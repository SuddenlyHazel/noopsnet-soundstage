// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Shared channel contract used by patching, rendering and external fixture export. */
public enum FixtureProfile {
    RGB("RGB Light", "Dimmer", "Red", "Green", "Blue"),
    LASER("RGB Laser", "Dimmer", "Red", "Green", "Blue", "Pan", "Tilt"),
    FOG("Fog Machine", "Output", "Spread"),
    PARTICLE("Particle Machine", "Output", "Red", "Green", "Blue");
    public final String title;
    public final String[] channels;

    FixtureProfile(String title, String... channels) {
        this.title = title;
        this.channels = channels;
    }

    public int footprint() {
        return channels.length;
    }

    public boolean fits(int address) {
        return address >= 1 && address + footprint() - 1 <= 512;
    }

    public static FixtureProfile at(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof StageBlock b)
            return switch (b.kind) {
                case LASER -> LASER;
                case FOG -> FOG;
                case PARTICLE -> PARTICLE;
                default -> RGB;
            };
        return RGB;
    }

    public static int color(int[] v, int a) {
        int i = a - 1;
        return (v[i + 1] * v[i] / 255) << 16 | (v[i + 2] * v[i] / 255) << 8 | v[i + 3] * v[i] / 255;
    }
}
