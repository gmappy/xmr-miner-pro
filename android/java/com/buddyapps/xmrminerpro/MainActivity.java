package com.buddyapps.xmrminerpro;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Beginner-facing dashboard. All numbers are read from XMRig's API via the service. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF0E1116;
    private static final int PANEL = 0xFF171D24;
    private static final int GREEN = 0xFF35D07F;
    private static final int TEXT = 0xFFE8F0E8;
    private static final int MUTED = 0xFF9AA7B2;

    private Settings settings;
    private EditText walletField;
    private TextView walletHint;
    private EditText poolField;
    private EditText workerField;
    private EditText threadsField;
    private EditText tempField;
    private CheckBox chargingOnly;
    private TextView statsView;
    private TextView errorView;
    private TextView logView;
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            statsView.setText(settings.stats());
            errorView.setText(settings.engineError());
            String log = settings.log();
            logView.setText(log.length() > 2500 ? log.substring(log.length() - 2500) : log);
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = new Settings(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column(this);
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);

        root.addView(title("XMR Miner Pro", 24, GREEN));
        root.addView(note("Genuine Monero (RandomX) mining via XMRig. Only your public wallet address is used.\n" +
                "Mining is visible, uses CPU and battery, and can be stopped at any time."));
        root.addView(space(10));

        root.addView(section("Wallet & pool"));
        walletField = field("Public XMR wallet address");
        walletField.setText(settings.wallet());
        root.addView(label("Public Monero address"));
        root.addView(walletField);
        walletHint = note("");
        root.addView(walletHint);
        walletField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                walletHint.setText(MoneroAddress.describe(s.toString()));
            }
        });
        walletHint.setText(MoneroAddress.describe(settings.wallet()));

        root.addView(label("Pool host:port"));
        poolField = field("pool.example.com:3333");
        poolField.setText(settings.pool());
        root.addView(poolField);
        root.addView(note("Use a genuine Monero pool. Check its fee, minimum payout and payout schedule on its own site."));

        root.addView(label("Worker name"));
        workerField = field("android");
        workerField.setText(settings.worker());
        root.addView(workerField);

        root.addView(section("Performance & safety"));
        root.addView(label("CPU threads (start low: 1–2)"));
        threadsField = field("1");
        threadsField.setText(String.valueOf(settings.threads()));
        root.addView(threadsField);

        root.addView(label("Temperature limit (°C, where supported)"));
        tempField = field("65");
        tempField.setText(String.valueOf(settings.tempLimit()));
        root.addView(tempField);

        chargingOnly = new CheckBox(this);
        chargingOnly.setText("Mine only while charging");
        chargingOnly.setTextColor(TEXT);
        chargingOnly.setChecked(settings.chargingOnly());
        root.addView(chargingOnly);
        root.addView(note("Mining stops automatically if the device gets hot, the battery drops below 20% " +
                "(when unplugged), or you press Stop."));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(button("Start mining", GREEN, new Runnable() {
            @Override public void run() { startMining(); }
        }));
        buttons.addView(button("Stop mining", 0xFF5A6672, new Runnable() {
            @Override public void run() { stopMining(); }
        }));
        root.addView(buttons);

        errorView = note("");
        errorView.setTextColor(0xFFFF8A80);
        root.addView(errorView);

        root.addView(section("Live statistics (from XMRig)"));
        statsView = new TextView(this);
        statsView.setTypeface(Typeface.MONOSPACE);
        statsView.setTextSize(13);
        statsView.setTextColor(TEXT);
        statsView.setBackgroundColor(PANEL);
        statsView.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(statsView);

        root.addView(section("Miner log"));
        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextColor(MUTED);
        logView.setBackgroundColor(PANEL);
        logView.setPadding(dp(12), dp(12), dp(12), dp(12));
        root.addView(logView);

        root.addView(note("Estimated rewards are not shown as balances. Confirm pool balance and wallet payouts " +
                "on the pool's website and in your own wallet."));

        requestNotificationsIfNeeded();
        handler.post(refresh);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(refresh);
        super.onDestroy();
    }

    private void startMining() {
        String wallet = walletField.getText().toString().trim();
        String pool = poolField.getText().toString().trim();
        if (!MoneroAddress.isValid(wallet)) {
            Toast.makeText(this, "Enter a valid public Monero address", Toast.LENGTH_LONG).show();
            return;
        }
        if (!pool.matches("[A-Za-z0-9._-]+:[0-9]{2,5}")) {
            Toast.makeText(this, "Pool must be host:port", Toast.LENGTH_LONG).show();
            return;
        }
        int threads;
        int temp;
        try {
            threads = Integer.parseInt(threadsField.getText().toString().trim());
            temp = Integer.parseInt(tempField.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Threads and temperature must be numbers", Toast.LENGTH_LONG).show();
            return;
        }
        if (threads < 1) threads = 1;
        if (temp < 45 || temp > 100) {
            Toast.makeText(this, "Temperature limit must be between 45 and 100", Toast.LENGTH_LONG).show();
            return;
        }

        settings.wallet(wallet);
        settings.pool(pool);
        settings.worker(workerField.getText().toString().trim().isEmpty()
                ? "android" : workerField.getText().toString().trim());
        settings.threads(threads);
        settings.tempLimit(temp);
        settings.chargingOnly(chargingOnly.isChecked());

        Intent i = new Intent(this, MiningService.class);
        i.setAction(MiningService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
    }

    private void stopMining() {
        Intent i = new Intent(this, MiningService.class);
        i.setAction(MiningService.ACTION_STOP);
        startService(i);
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    private TextView title(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView section(String text) {
        TextView t = title(text, 16, GREEN);
        t.setPadding(0, dp(18), 0, dp(6));
        return t;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(MUTED);
        t.setTextSize(13);
        t.setPadding(0, dp(6), 0, dp(2));
        return t;
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(MUTED);
        t.setTextSize(12);
        t.setPadding(0, dp(6), 0, dp(4));
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF6B7783);
        e.setTextColor(TEXT);
        e.setBackgroundColor(PANEL);
        e.setSingleLine(true);
        e.setPadding(dp(10), dp(10), dp(10), dp(10));
        return e;
    }

    private Button button(String text, int color, final Runnable action) {
        Button b = new Button(this);
        b.setText(text);
        b.setBackgroundColor(color);
        b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, dp(12), dp(8), 0);
        b.setLayoutParams(p);
        b.setOnClickListener(new android.view.View.OnClickListener() {
            @Override public void onClick(android.view.View v) { action.run(); }
        });
        return b;
    }

    private View space(int d) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(d)));
        return v;
    }

    private static LinearLayout column(Activity a) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.TOP);
        return l;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
