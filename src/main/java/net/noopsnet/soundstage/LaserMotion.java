// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Fractional, timestamped laser tracking with immediate discontinuities. */
public final class LaserMotion {
    private static final double TAU_NANOS = 35_000_000.0;
    private static final long RESET_GAP_NANOS = 250_000_000L;
    private static final double SNAP_DEGREES = 18.0, ABRUPT_DEGREES = 6.0;

    public record Direction(double x, double y, double z) {}

    public record Aim(double panDegrees, double tiltDegrees) {
        public Direction direction(double facingDegrees) {
            double yaw = Math.toRadians(facingDegrees + panDegrees),
                    pitch = Math.toRadians(tiltDegrees);
            return new Direction(
                    -Math.sin(yaw) * Math.cos(pitch),
                    -Math.sin(pitch),
                    Math.cos(yaw) * Math.cos(pitch));
        }
    }

    private boolean initialized, wasVisible;
    private long lastSample, lastTime;
    private double pan, tilt, targetPan, targetTilt, rawPan, rawTilt, velocityPan, velocityTilt;

    public static Aim raw(int pan, int tilt) {
        return new Aim(
                (Math.clamp(pan, 0, 255) - 127.5) / 127.5 * 180,
                (Math.clamp(tilt, 0, 255) - 127.5) / 127.5 * 90);
    }

    private static double wrap(double degrees) {
        return degrees - 360.0 * Math.floor((degrees + 180.0) / 360.0);
    }

    private static double distance(double panA, double tiltA, double panB, double tiltB) {
        double a = Math.toRadians(tiltA), b = Math.toRadians(tiltB);
        double dot =
                Math.sin(a) * Math.sin(b)
                        + Math.cos(a) * Math.cos(b) * Math.cos(Math.toRadians(wrap(panB - panA)));
        return Math.toDegrees(Math.acos(Math.clamp(dot, -1.0, 1.0)));
    }

    private void advanceTo(long time) {
        double alpha = -Math.expm1(-Math.max(0.0, (double) (time - lastTime)) / TAU_NANOS);
        pan += (targetPan - pan) * alpha;
        tilt += (targetTilt - tilt) * alpha;
        lastTime = time;
    }

    private Aim reset(Aim aim, long sample, long now, boolean visible) {
        pan = targetPan = rawPan = aim.panDegrees;
        tilt = targetTilt = rawTilt = aim.tiltDegrees;
        velocityPan = velocityTilt = 0;
        lastSample = sample;
        lastTime = now;
        initialized = true;
        wasVisible = visible;
        return aim;
    }

    /** Call once per render frame; sampleNanos identifies the decoded universe. */
    public Aim advance(
            int panByte,
            int tiltByte,
            long sampleNanos,
            long now,
            boolean enabled,
            boolean visible) {
        Aim aim = raw(panByte, tiltByte);
        if (!initialized
                || !enabled
                || !visible
                || !wasVisible
                || now < lastTime
                || sampleNanos < lastSample
                || now - lastTime > RESET_GAP_NANOS
                || sampleNanos - lastSample > RESET_GAP_NANOS)
            return reset(aim, sampleNanos, now, visible);
        if (sampleNanos > lastSample) {
            // Preserve the old target until the new sample actually arrived.
            advanceTo(Math.clamp(sampleNanos, lastTime, now));
            double interval = (sampleNanos - lastSample) / 1e9;
            double change = distance(rawPan, rawTilt, aim.panDegrees, aim.tiltDegrees);
            double expected =
                    distance(
                            rawPan,
                            rawTilt,
                            rawPan + velocityPan * interval,
                            rawTilt + velocityTilt * interval);
            double predictionError =
                    distance(
                            rawPan + velocityPan * interval,
                            rawTilt + velocityTilt * interval,
                            aim.panDegrees,
                            aim.tiltDegrees);
            boolean snap =
                    change >= SNAP_DEGREES
                            || change >= ABRUPT_DEGREES
                                    && predictionError >= ABRUPT_DEGREES
                                    && change > 3 * expected + 1.5;
            velocityPan = wrap(aim.panDegrees - rawPan) / interval;
            velocityTilt = (aim.tiltDegrees - rawTilt) / interval;
            rawPan = aim.panDegrees;
            rawTilt = aim.tiltDegrees;
            lastSample = sampleNanos;
            targetPan = pan + wrap(rawPan - pan);
            targetTilt = rawTilt;
            if (snap) {
                pan = targetPan;
                tilt = targetTilt;
            }
        }
        // Hold the latest target during a decoder pause: never extrapolate.
        advanceTo(now);
        wasVisible = visible;
        return new Aim(pan, tilt);
    }
}
