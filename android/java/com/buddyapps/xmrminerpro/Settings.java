package com.buddyapps.xmrminerpro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Local, app-private configuration. Only public wallet data is ever stored. */
final class Settings {
    private static final String PREF = "xmrminerpro";

    private static final String K_WALLET = "wallet";
    private static final String K_POOL = "pool";
    private static final String K_WORKER = "worker";
    private static final String K_THREADS = "threads";
    private static final String K_TEMP = "temp_limit";
    private static final String K_CHARGING = "charging_only";
    private static final String K_STATS = "stats_text";
    private static final String K_LOG = "log_text";
    private static final String K_ERROR = "engine_error";

    private final SharedPreferences p;

    Settings(Context c) {
        p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    String wallet()       { return p.getString(K_WALLET, ""); }
    String pool()          { return p.getString(K_POOL, "pool.supportxmr.com:3333"); }
    String worker()        { return p.getString(K_WORKER, "android"); }
    int threads()          { return p.getInt(K_THREADS, 1); }
    int tempLimit()        { return p.getInt(K_TEMP, 65); }
    boolean chargingOnly() { return p.getBoolean(K_CHARGING, false); }

    void wallet(String v)       { put(K_WALLET, v); }
    void pool(String v)          { put(K_POOL, v); }
    void worker(String v)        { put(K_WORKER, v); }
    void threads(int v)          { p.edit().putInt(K_THREADS, v).apply(); }
    void tempLimit(int v)        { p.edit().putInt(K_TEMP, v).apply(); }
    void chargingOnly(boolean v) { p.edit().putBoolean(K_CHARGING, v).apply(); }

    String stats() { return p.getString(K_STATS, "Not mining"); }
    void stats(String v) { put(K_STATS, v); }

    String log() { return p.getString(K_LOG, ""); }
    void log(String v) { put(K_LOG, v); }

    String engineError() { return p.getString(K_ERROR, ""); }
    void engineError(String v) { put(K_ERROR, v); }

    private void put(String key, String value) {
        p.edit().putString(key, value == null ? "" : value).apply();
    }

    static int safeThreads(int requested) {
        int cores = Runtime.getRuntime().availableProcessors();
        int max = Math.max(1, cores - 1);
        if (requested < 1) return 1;
        return Math.min(requested, max);
    }

    static boolean supportsThermalStatus() {
        return Build.VERSION.SDK_INT >= 29;
    }
}
