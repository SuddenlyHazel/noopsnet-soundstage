// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Persisted per-fixture override. Channels use the same 0–255 scale as DMX. */
public record ManualFixtureSettings(
        boolean override,
        boolean enabled,
        int output,
        int red,
        int green,
        int blue,
        int pan,
        int tilt,
        int spread) {

    /** Legacy fixtures remain controlled by their existing show until explicitly changed. */
    public static ManualFixtureSettings defaults() {
        return new ManualFixtureSettings(false, true, 255, 255, 255, 255, 128, 128, 128);
    }

    public boolean valid() {
        return channel(output)
                && channel(red)
                && channel(green)
                && channel(blue)
                && channel(pan)
                && channel(tilt)
                && channel(spread);
    }

    public ManualFixtureSettings withOverride(boolean value) {
        return new ManualFixtureSettings(
                value, enabled, output, red, green, blue, pan, tilt, spread);
    }

    public ManualFixtureSettings withEnabled(boolean value) {
        return new ManualFixtureSettings(
                override, value, output, red, green, blue, pan, tilt, spread);
    }

    private static boolean channel(int value) {
        return value >= 0 && value <= 255;
    }
}
