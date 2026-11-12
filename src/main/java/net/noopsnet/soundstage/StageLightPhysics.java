// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Stage transport uses linear light; 8-bit fixture display colors are separate. */
public final class StageLightPhysics {
    public record Radiance(double red, double green, double blue) {
        public static final Radiance BLACK = new Radiance(0, 0, 0);

        public Radiance scale(double value) {
            return new Radiance(red * value, green * value, blue * value);
        }

        public boolean black() {
            return red <= 0 && green <= 0 && blue <= 0;
        }
    }

    private static double linear(int channel) {
        return Math.pow(Math.clamp(channel, 0, 255) / 255.0, 2.2);
    }

    public static Radiance fromDmx(int[] channels, int address) {
        int i = address - 1;
        if (i < 0 || i + 3 >= channels.length)
            throw new IllegalArgumentException("Invalid RGB fixture address");
        double dimmer = Math.clamp(channels[i], 0, 255) / 255.0;
        return new Radiance(
                linear(channels[i + 1]) * dimmer,
                linear(channels[i + 2]) * dimmer,
                linear(channels[i + 3]) * dimmer);
    }

    public static Radiance fromDisplayRgb(int color) {
        return new Radiance(
                linear((color >> 16) & 255), linear((color >> 8) & 255), linear(color & 255));
    }

    public static float clampTilt(float degrees) {
        return Float.isFinite(degrees) ? Math.clamp(degrees, -80f, 80f) : 0f;
    }

    /** Horizontal facing X/Z, with positive pitch pointing below the horizon. */
    public static double[] direction(int facingX, int facingZ, float tiltDegrees) {
        if (Math.abs(facingX) + Math.abs(facingZ) != 1)
            throw new IllegalArgumentException("Horizontal cardinal facing required");
        double angle = Math.toRadians(clampTilt(tiltDegrees)), horizontal = Math.cos(angle);
        return new double[] {facingX * horizontal, -Math.sin(angle), facingZ * horizontal};
    }

    /** Preserve fractional linear energy and frame-rate-independent cue easing. */
    public static final class Smoother {
        private Radiance value = Radiance.BLACK;
        private boolean initialized;

        public Radiance advance(Radiance target, double elapsedMillis, int smoothingMillis) {
            double alpha =
                    !initialized || smoothingMillis <= 0
                            ? 1
                            : -Math.expm1(-Math.max(0, elapsedMillis) / smoothingMillis);
            value =
                    new Radiance(
                            value.red + (target.red - value.red) * alpha,
                            value.green + (target.green - value.green) * alpha,
                            value.blue + (target.blue - value.blue) * alpha);
            initialized = true;
            return value;
        }
    }

    private StageLightPhysics() {}
}
