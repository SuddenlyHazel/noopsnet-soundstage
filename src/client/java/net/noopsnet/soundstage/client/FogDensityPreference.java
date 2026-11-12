// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

/** Keep deliberate legacy preferences visually consistent when the baseline increases. */
public final class FogDensityPreference {
    public static final int MAX_PERCENT = 300, SCALE_VERSION = 2;

    private FogDensityPreference() {}

    public static int load(int stored, int scaleVersion) {
        if (scaleVersion >= SCALE_VERSION) return Math.clamp(stored, 0, MAX_PERCENT);
        int legacy = Math.clamp(stored, 0, 200);
        // The requested new baseline applies to the previous default too.
        return legacy == 100 ? 100 : (int) Math.round(legacy / 1.5);
    }
}
