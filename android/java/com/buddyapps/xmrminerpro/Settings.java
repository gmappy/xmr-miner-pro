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
    private static final String K_MGMT_URL = "mgmt_url";
    private static final String K_DEVICE_ID = "device_id";
    private static final String K_DEVICE_TOKEN = "device_token";
    private static final String K_MGMT_STATUS = "mgmt_status";

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

    String managementUrl() { return p.getString(K_MGMT_URL, ""); }
    String deviceId()       { return p.getString(K_DEVICE_ID, ""); }
    String deviceToken()    { return p.getString(K_DEVICE_TOKEN, ""); }
    void managementUrl(String v) { put(K_MGMT_URL, v); }
    void deviceId(String v)      { put(K_DEVICE_ID, v); }
    void deviceToken(String v)   { put(K_DEVICE_TOKEN, v); }

    String managementStatus() { return p.getString(K_MGMT_STATUS, ""); }
    void managementStatus(String v) { put(K_MGMT_STATUS, v); }

    boolean telemetryEnabled() {
        return validManagementUrl(managementUrl())
                && deviceId().matches("[a-f0-9]{16}")
                && deviceToken().length() >= 20;
    }

    static boolean validManagementUrl(String v) {
        if (v == null) return false;
        String s = v.trim();
        if (!s.startsWith("https://") && !s.startsWith("http://127.0.0.1:")
                && !s.startsWith("http://10.") && !s.startsWith("http://192.168.")
                && !s.startsWith("http://172.")) return false;
        try {
            java.net.URL u = new java.net.URL(s);
            int port = u.getPort();
            if (port == -1) port = "https".equals(u.getProtocol()) ? 443 : 80;
            if (port < 1 || port > 65535) return false;
            String host = u.getHost();
            return host != null && host.length() > 0 && !host.contains(" ");
        } catch (Exception e) { return false; }
    }

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
