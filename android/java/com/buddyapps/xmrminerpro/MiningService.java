package com.buddyapps.xmrminerpro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/** Visible foreground service: mining never runs without a persistent notification and Stop action. */
public class MiningService extends Service {
    public static final String ACTION_START = "com.buddyapps.xmrminerpro.START";
    public static final String ACTION_STOP = "com.buddyapps.xmrminerpro.STOP";

    private static final String CHANNEL = "xmrminerpro-mining";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "XMRMinerPro";

    private XmrigController controller;
    private Settings settings;
    private TelemetryReporter reporter;
    private final Handler handler = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    private volatile int batteryPercent = 100;
    private volatile boolean charging = true;
    private volatile String stopReason = "";

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) batteryPercent = level * 100 / scale;
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            charging = plugged != 0;
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        settings = new Settings(this);
        controller = new XmrigController(this);
        createChannel();
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        startForeground(NOTIFICATION_ID, buildNotification("Starting…"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopMining("Stopped by you");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (controller.isRunning()) return START_STICKY;

        settings.engineError("");
        settings.log("");
        settings.stats("Starting engine…");
        if (!MoneroAddress.isValid(settings.wallet())) {
            settings.engineError("Enter a valid public Monero address before starting");
            stopMining("Configuration error");
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            controller.start();
            stopReason = "";
            if (settings.telemetryEnabled()) {
                reporter = new TelemetryReporter(settings);
                reporter.update(controller.sample(), batteryPercent, charging);
                reporter.start();
            } else {
                settings.managementStatus("Dashboard: not configured");
            }
            handler.post(ticker);
            updateNotification("Connecting…");
        } catch (Exception e) {
            settings.engineError("Engine failed to start: " + e.getMessage());
            Log.e(TAG, "engine start failed", e);
            stopMining("Engine failed to start");
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void refresh() {
        String s = controller.stats();
        settings.stats(s);
        settings.engineError(controller.lastError());
        settings.log(controller.log());
        if (reporter != null) reporter.update(controller.sample(), batteryPercent, charging);
        String first = s.split("\\n")[0];
        updateNotification(first);

        if (!controller.isRunning()) {
            stopMining("Miner process exited");
            stopSelf();
            return;
        }
        if (settings.chargingOnly() && !charging) {
            stopMining("Charging-only mode: unplugged");
            stopSelf();
            return;
        }
        if (batteryPercent < 20 && !charging) {
            stopMining("Battery below 20%");
            stopSelf();
            return;
        }
        int thermal = thermalStatus();
        if (thermal >= 2) {
            stopMining("Device too hot (thermal status " + thermal + ")");
            stopSelf();
        }
    }

    /** PowerManager thermal status: 0 = none, 1 = light, 2 = moderate, 3 = severe, 4 = critical. */
    private int thermalStatus() {
        if (Build.VERSION.SDK_INT < 29) return 0;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.getCurrentThermalStatus();
        } catch (Throwable t) {
            return 0;
        }
    }

    private void stopMining(String reason) {
        handler.removeCallbacks(ticker);
        if (reporter != null) {
            reporter.stop();
            reporter = null;
        }
        if (controller != null) controller.stop();
        stopReason = reason;
        settings.stats("Stopped: " + reason);
        settings.engineError("");
        updateNotification("Stopped: " + reason);
    }

    static String lastStopReason() { return ""; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Mining status",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Persistent status and Stop control while mining");
            nm.createNotificationChannel(ch);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Intent stop = new Intent(this, MiningService.class);
        stop.setAction(ACTION_STOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getService(this, 2, stop, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL);
        else b = new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_upload)
         .setContentTitle("XMR Miner Pro")
         .setContentText(text)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         .setContentIntent(PendingIntent.getActivity(this, 1,
                 new Intent(this, MainActivity.class), flags))
         .addAction(android.R.drawable.ic_delete, "Stop mining", pi);
        return b.build();
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        if (reporter != null) {
            reporter.stop();
            reporter = null;
        }
        if (controller != null) controller.stop();
        try { unregisterReceiver(batteryReceiver); } catch (IllegalArgumentException ignored) {}
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
