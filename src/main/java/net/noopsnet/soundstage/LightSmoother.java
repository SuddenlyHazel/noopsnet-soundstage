// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Retains fractional color changes; quantization happens only at presentation. */
public final class LightSmoother {
    private final double[] channels = new double[3];
    private boolean initialized;

    public int advance(int target, double elapsedMillis, int smoothingMillis) {
        double alpha =
                !initialized || smoothingMillis <= 0
                        ? 1
                        : -Math.expm1(-Math.max(0, elapsedMillis) / smoothingMillis);
        int result = 0;
        for (int i = 0; i < 3; i++) {
            int shift = (2 - i) * 8, value = (target >> shift) & 255;
            channels[i] += (value - channels[i]) * alpha;
            result |= ((int) Math.clamp(Math.round(channels[i]), 0, 255)) << shift;
        }
        initialized = true;
        return result;
    }
}
