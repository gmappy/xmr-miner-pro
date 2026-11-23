package com.buddyapps.xmrminerpro;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Sends real mining statistics to the optional management dashboard.
 * No wallet address, key or seed phrase is ever included.
 */
final class TelemetryReporter {
    private static final String TAG = "XMRMinerPro";
    private static final long INTERVAL_MS = 10000;

    private final Settings settings;
    private final String endpoint;
    private final String token;
    private volatile boolean running;
    private Thread thread;

    private volatile TelemetrySample sample;
    private volatile int batteryPct = -1;
    private volatile boolean charging;
    private volatile String stopStatus = "";

    TelemetryReporter(Settings s) {
        settings = s;
        String base = s.managementUrl().trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        endpoint = base + "/api/devices/" + s.deviceId().trim() + "/telemetry";
        token = s.deviceToken().trim();
    }

    void update(TelemetrySample s, int battery, boolean isCharging) {
        sample = s;
        batteryPct = battery;
        charging = isCharging;
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() {
                while (running) {
                    send(stopStatus);
                    try { Thread.sleep(INTERVAL_MS); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
            }
        }, "xmrminerpro-telemetry");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops the periodic loop, then makes one best-effort final "stopped" report. */
    void stop() {
        running = false;
        stopStatus = "stopped";
        if (thread != null) thread.interrupt();
        new Thread(new Runnable() {
            @Override public void run() { send("stopped"); }
        }, "xmrminerpro-telemetry-final").start();
    }

    private void send(String overrideStatus) {
        TelemetrySample s = sample;
        String status = overrideStatus != null && !overrideStatus.isEmpty() ? overrideStatus
                : (s == null ? "starting" : (!s.fresh ? "connecting" : (s.connected ? "mining" : "connecting")));
        double rate = s != null && s.fresh ? Math.max(0, s.hashrate) : 0;
        double avg = s != null && s.fresh ? Math.max(0, s.hashrateAvg) : 0;
        long accepted = s != null && s.fresh ? Math.max(0, s.accepted) : 0;
        long rejected = s != null && s.fresh ? Math.max(0, s.rejected) : 0;
        long uptime = s != null && s.fresh ? Math.max(0, s.uptime) : 0;
        String pool = s == null ? "" : (s.pool == null ? "" : s.pool);
        Float temp = cpuTemp();

        StringBuilder body = new StringBuilder("{");
        body.append("\"hashrate\":").append(rate).append(',');
        body.append("\"hashrate_avg\":").append(avg).append(',');
        body.append("\"accepted\":").append(accepted).append(',');
        body.append("\"rejected\":").append(rejected).append(',');
        body.append("\"uptime_s\":").append(uptime).append(',');
        body.append("\"cpu_percent\":-1,");
        if (temp != null) body.append("\"temp_c\":").append(temp).append(',');
        if (batteryPct >= 0) body.append("\"battery_pct\":").append(batteryPct).append(',');
        body.append("\"charging\":").append(charging).append(',');
        body.append("\"pool\":").append(quote(pool)).append(',');
        body.append("\"platform\":").append(quote(platform())).append(',');
        body.append("\"status\":").append(quote(status));
        body.append('}');

        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(5000);
            c.setReadTimeout(5000);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setDoOutput(true);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            OutputStream os = c.getOutputStream();
            os.write(bytes);
            os.close();
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                settings.managementStatus("Dashboard: reported " +
                        new SimpleDateFormat("HH:mm:ss", Locale.US).format(new java.util.Date()));
            } else {
                settings.managementStatus("Dashboard: HTTP " + code);
            }
            try { c.getInputStream().close(); } catch (Exception ignored) {}
        } catch (Exception e) {
            Log.w(TAG, "telemetry report failed: " + e.getMessage());
            settings.managementStatus("Dashboard: " + e.getMessage());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String platform() {
        return "android";
    }

    /** Reads a real thermal zone in Celsius when the device exposes one; null otherwise. */
    private static Float cpuTemp() {
        try {
            File[] zones = new File("/sys/class/thermal").listFiles();
            if (zones == null) return null;
            for (File z : zones) {
                if (!z.getName().startsWith("thermal_zone")) continue;
                File f = new File(z, "temp");
                if (!f.exists()) continue;
                BufferedReader r = new BufferedReader(new FileReader(f));
                String line = r.readLine();
                r.close();
                if (line == null) continue;
                float millis = Float.parseFloat(line.trim());
                float c = millis > 1000 ? millis / 1000f : millis;
                if (c > 0 && c < 120) return c;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(' ');
            else b.append(ch);
        }
        return b.append('"').toString();
    }
}
