// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Require sustained drift before disrupting a playing VOD with a seek. */
public final class PlaybackSynchronizer {
    private long started, lastCheck, lastSeek, driftSince;
    private int driftSign;

    public void reset(long now) {
        started = lastCheck = now;
        lastSeek = now;
        driftSince = 0;
        driftSign = 0;
    }

    public boolean correctionNeeded(long now, long desiredMillis, long mediaMillis) {
        boolean gap = now - lastCheck > 1_500_000_000L;
        lastCheck = now;
        long drift = desiredMillis - mediaMillis;
        if (gap || now - started < 5_000_000_000L || mediaMillis <= 0 || Math.abs(drift) <= 1500) {
            driftSign = 0;
            return false;
        }
        int sign = Long.signum(drift);
        if (sign != driftSign) {
            driftSign = sign;
            driftSince = now;
            return false;
        }
        if (now - driftSince < 2_000_000_000L || now - lastSeek < 15_000_000_000L) return false;
        lastSeek = now;
        driftSign = 0;
        return true;
    }
}
