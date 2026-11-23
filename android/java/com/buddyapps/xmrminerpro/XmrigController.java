package com.buddyapps.xmrminerpro;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Owns the XMRig process. Every statistic shown in the UI comes from its local HTTP API. */
final class XmrigController {
    private static final String ENGINE = "xmrig";
    private static final String API_HOST = "127.0.0.1";
    private static final int API_PORT = 33445;

    private final Context app;
    private final Settings settings;
    private Process process;
    private Thread reader;
    private Thread poller;
    private volatile boolean running;
    private String token = "";
    private String stats = "Miner not running";
    private String lastError = "";
    private final StringBuilder log = new StringBuilder();

    XmrigController(Context c) {
        app = c.getApplicationContext();
        settings = new Settings(app);
    }

    boolean isRunning() { return running && process != null; }
    String stats() { return stats; }
    String lastError() { return lastError; }
    String log() { return log.toString(); }

    boolean supportedAbi() {
        if (Build.SUPPORTED_ABIS.length == 0) return false;
        return "arm64-v8a".equals(Build.SUPPORTED_ABIS[0]);
    }

    synchronized void start() throws IOException {
        if (isRunning()) return;
        lastError = "";
        if (!supportedAbi()) {
            throw new IOException("This build requires an ARM64 (arm64-v8a) device");
        }

        File engine = ensureEngine();
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        token = hex(raw);

        List<String> cmd = new ArrayList<>();
        cmd.add(engine.getAbsolutePath());
        cmd.add("--algo=rx/0");
        cmd.add("--url=" + settings.pool().trim());
        cmd.add("--user=" + settings.wallet().trim() + "." + settings.worker().trim());
        cmd.add("--pass=x");
        cmd.add("--threads=" + Settings.safeThreads(settings.threads()));
        cmd.add("--randomx-mode=light");
        cmd.add("--randomx-init=1");
        cmd.add("--no-huge-pages");
        cmd.add("--http-enabled");
        cmd.add("--http-host=" + API_HOST);
        cmd.add("--http-port=" + API_PORT);
        cmd.add("--http-access-token=" + token);
        cmd.add("--api-worker-id=" + settings.worker().trim());
        cmd.add("--log-file=" + new File(app.getFilesDir(), "xmrig.log").getAbsolutePath());
        cmd.add("--print-time=10");
        cmd.add("--no-color");

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(app.getFilesDir());
        pb.redirectErrorStream(true);
        process = pb.start();
        running = true;
        stats = "Connecting to miner…";
        appendLog("$ " + String.join(" ", cmd).replace(token, "***") + "\n");

        reader = new Thread(new Runnable() {
            @Override public void run() {
                BufferedReader r = null;
                try {
                    r = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = r.readLine()) != null) appendLog(line + "\n");
                } catch (IOException ignored) {
                } finally {
                    if (r != null) try { r.close(); } catch (IOException ignored) {}
                    running = false;
                }
            }
        }, "xmrig-output");
        reader.setDaemon(true);
        reader.start();

        poller = new Thread(new Runnable() {
            @Override public void run() {
                while (running && !Thread.currentThread().isInterrupted()) {
                    String s = readStats();
                    if (s != null) stats = s;
                    try { Thread.sleep(2000); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "xmrig-stats");
        poller.setDaemon(true);
        poller.start();
    }

    synchronized void stop() {
        running = false;
        if (poller != null) poller.interrupt();
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
        stats = "Mining stopped";
    }

    private File ensureEngine() throws IOException {
        File f = new File(app.getFilesDir(), ENGINE);
        long expected = -1;
        try {
            android.content.res.AssetFileDescriptor afd = app.getAssets().openFd(ENGINE);
            expected = afd.getLength();
            afd.close();
        } catch (IOException ignored) {
            expected = -1;
        }
        if (f.exists() && (expected < 0 || f.length() == expected)) {
            if (!f.canExecute() && !f.setExecutable(true, false)) {
                throw new IOException("Cannot mark engine executable");
            }
            return f;
        }

        InputStream in = app.getAssets().open(ENGINE);
        FileOutputStream out = new FileOutputStream(f, false);
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        if (!f.setExecutable(true, false)) throw new IOException("Cannot mark engine executable");
        return f;
    }

    private String readStats() {
        HttpURLConnection c = null;
        try {
            URL u = new URL("http://" + API_HOST + ":" + API_PORT + "/1/summary");
            c = (HttpURLConnection) u.openConnection();
            c.setConnectTimeout(1500);
            c.setReadTimeout(1500);
            c.setRequestProperty("Authorization", "Bearer " + token);
            c.setRequestMethod("GET");
            if (c.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return format(sb.toString());
        } catch (Exception e) {
            lastError = "API: " + e.getMessage();
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Formats the real /1/summary payload. Missing fields are shown as '?', never invented. */
    private String format(String json) {
        String hashrate = object(json, "hashrate");
        String results = object(json, "results");
        String conn = object(json, "connection");

        double current = firstNumber(hashrate, "total", 0);
        double avg = firstNumber(hashrate, "total", 2);
        long good = (long) number(results, "shares_good", -1);
        long totalShares = (long) number(results, "shares_total", -1);
        long hashes = (long) number(results, "hashes_total", -1);
        String pool = stringValue(conn, "pool", "?");
        long uptime = (long) number(conn, "uptime", -1);
        long ping = (long) number(conn, "ping", -1);
        long failures = (long) number(conn, "failures", -1);
        String algo = stringValue(json, "algo", "?");
        String version = stringValue(json, "version", "?");

        StringBuilder b = new StringBuilder();
        b.append("Status: ").append(poolOkay(conn) ? "connected" : "connecting / offline").append('\n');
        b.append("Miner: XMRig ").append(version).append(" · ").append(algo).append('\n');
        b.append("Current: ").append(fmtRate(current)).append("   Average: ").append(fmtRate(avg)).append('\n');
        b.append("Accepted shares: ").append(good < 0 ? "?" : good)
         .append("   Rejected: ").append((good < 0 || totalShares < 0) ? "?" : (totalShares - good)).append('\n');
        b.append("Total hashes: ").append(hashes < 0 ? "?" : hashes).append('\n');
        b.append("Pool: ").append(pool).append('\n');
        b.append("Pool uptime: ").append(uptime < 0 ? "?" : uptime + "s")
         .append("   Ping: ").append(ping < 0 ? "?" : ping + "ms")
         .append("   Failures: ").append(failures < 0 ? "?" : failures).append('\n');
        b.append("Pool balance / wallet payouts: check the pool and your own wallet.");
        return b.toString();
    }

    private static boolean poolOkay(String conn) {
        String status = stringValue(conn, "status", "");
        if ("connected".equals(status)) return true;
        return number(conn, "uptime", 0) > 0;
    }

    private static String fmtRate(double v) {
        if (v < 0) return "? H/s";
        return String.format(Locale.US, "%.1f H/s", v);
    }

    private void appendLog(String s) {
        synchronized (log) {
            log.append(s);
            if (log.length() > 12000) log.delete(0, log.length() - 12000);
        }
        settings.log(log.toString());
    }

    private static String hex(byte[] b) {
        char[] out = new char[b.length * 2];
        String d = "0123456789abcdef";
        for (int i = 0; i < b.length; i++) {
            out[i * 2] = d.charAt((b[i] >> 4) & 0xf);
            out[i * 2 + 1] = d.charAt(b[i] & 0xf);
        }
        return new String(out);
    }

    private static String object(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int s = json.indexOf('{', i);
        if (s < 0) return "";
        int depth = 0;
        boolean inString = false;
        for (int j = s; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (ch == '"' && (j == 0 || json.charAt(j - 1) != '\\')) inString = !inString;
            if (inString) continue;
            if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return json.substring(s, j + 1);
        }
        return "";
    }

    private static double number(String json, String key, double fallback) {
        if (json == null) return fallback;
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return fallback;
        int colon = json.indexOf(':', i + key.length() + 2);
        if (colon < 0) return fallback;
        int j = colon + 1;
        while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;
        int start = j;
        if (j < json.length() && (json.charAt(j) == '-' || json.charAt(j) == '+')) j++;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) ||
               json.charAt(j) == '.' || json.charAt(j) == 'e' || json.charAt(j) == 'E' ||
               json.charAt(j) == '-' || json.charAt(j) == '+')) j++;
        try { return Double.parseDouble(json.substring(start, j)); }
        catch (RuntimeException e) { return fallback; }
    }

    private static double firstNumber(String json, String key, int index) {
        if (json == null) return -1;
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return -1;
        int arr = json.indexOf('[', i);
        if (arr < 0) return -1;
        int j = arr + 1;
        int seen = 0;
        while (j < json.length() && json.charAt(j) != ']') {
            char ch = json.charAt(j);
            if (ch == '-' || ch == '.' || Character.isDigit(ch)) {
                int start = j;
                while (j < json.length() && (Character.isDigit(json.charAt(j)) ||
                       json.charAt(j) == '.' || json.charAt(j) == '-' || json.charAt(j) == 'e' ||
                       json.charAt(j) == 'E' || json.charAt(j) == '+')) j++;
                if (seen == index) {
                    try { return Double.parseDouble(json.substring(start, j)); }
                    catch (RuntimeException e) { return -1; }
                }
                seen++;
            } else j++;
        }
        return -1;
    }

    private static String stringValue(String json, String key, String fallback) {
        if (json == null) return fallback;
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return fallback;
        int colon = json.indexOf(':', i + key.length() + 2);
        if (colon < 0) return fallback;
        int q = json.indexOf('"', colon + 1);
        if (q < 0) return fallback;
        int end = json.indexOf('"', q + 1);
        if (end < 0) return fallback;
        return json.substring(q + 1, end);
    }
}
