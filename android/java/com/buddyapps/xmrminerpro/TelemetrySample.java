package com.buddyapps.xmrminerpro;

/** Immutable snapshot of one real XMRig /1/summary response. -1 means "not reported". */
final class TelemetrySample {
    final double hashrate;
    final double hashrateAvg;
    final long accepted;
    final long rejected;
    final long uptime;
    final String pool;
    final boolean connected;
    final boolean fresh;

    TelemetrySample(double hashrate, double hashrateAvg, long accepted, long rejected,
                    long uptime, String pool, boolean connected, boolean fresh) {
        this.hashrate = hashrate;
        this.hashrateAvg = hashrateAvg;
        this.accepted = accepted;
        this.rejected = rejected;
        this.uptime = uptime;
        this.pool = pool;
        this.connected = connected;
        this.fresh = fresh;
    }
}
