// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.noopsnet.soundstage.FixtureProfile;
import net.noopsnet.soundstage.LightingFrame;
import net.noopsnet.soundstage.ManualFixtureSettings;

/** Reuses a manual universe and sample timestamp until a fixture's inputs change. */
public final class ManualFixtureInputs {
    private ManualFixtureSettings settings;
    private FixtureProfile profile;
    private int address;
    private LightingFrame frame;

    public static int percentToChannel(int value) {
        if (value < 0 || value > 100)
            throw new IllegalArgumentException("Percent must be 0 to 100");
        return (int) Math.round(value * 255.0 / 100.0);
    }

    public static int channelToPercent(int value) {
        if (value < 0 || value > 255)
            throw new IllegalArgumentException("Channel must be 0 to 255");
        return (int) Math.round(value * 100.0 / 255.0);
    }

    public static int degreesToChannel(int degrees, int halfRange) {
        if ((halfRange != 90 && halfRange != 180) || degrees < -halfRange || degrees > halfRange)
            throw new IllegalArgumentException("Angle outside fixture range");
        return (int) Math.round((degrees + halfRange) * 255.0 / (2.0 * halfRange));
    }

    public static int channelToDegrees(int value, int halfRange) {
        if ((halfRange != 90 && halfRange != 180) || value < 0 || value > 255)
            throw new IllegalArgumentException("Invalid angle channel");
        return (int) Math.round((value - 127.5) * halfRange / 127.5);
    }

    public static int parseColor(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (!text.matches("[0-9A-Fa-f]{6}"))
            throw new IllegalArgumentException("Color: use six RGB hex digits.");
        return Integer.parseInt(text, 16);
    }

    public LightingFrame frame(ManualFixtureSettings value, FixtureProfile kind, int at, long now) {
        if (value == null || !value.valid() || !value.override() || kind == null || !kind.fits(at))
            throw new IllegalArgumentException("Invalid manual fixture input");
        if (value.equals(settings) && kind == profile && at == address) return frame;
        int[] channels = new int[512];
        int index = at - 1;
        channels[index] = value.enabled() ? value.output() : 0;
        if (kind == FixtureProfile.FOG) {
            channels[index + 1] = value.spread();
        } else {
            channels[index + 1] = value.red();
            channels[index + 2] = value.green();
            channels[index + 3] = value.blue();
            if (kind == FixtureProfile.LASER) {
                channels[index + 4] = value.pan();
                channels[index + 5] = value.tilt();
            }
        }
        settings = value;
        profile = kind;
        address = at;
        frame = new LightingFrame(now, channels);
        return frame;
    }
}
