// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Offline movement regression; no Minecraft client, renderer or media player. */
public final class LaserMotionProbe {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void close(double a, double b, String message) {
        check(Math.abs(a - b) < 1e-9, message + ": " + a + " vs " + b);
    }

    public static void run() {
        var tracking = new LaserMotion();
        var first = tracking.advance(127, 127, 0, 0, true, true);
        tracking.advance(128, 127, 33_000_000, 33_000_000, true, true);
        var between = tracking.advance(128, 127, 33_000_000, 45_000_000, true, true);
        check(
                between.panDegrees() > first.panDegrees()
                        && between.panDegrees() < LaserMotion.raw(128, 127).panDegrees(),
                "Tracking must retain fractional steps");
        var jump = tracking.advance(200, 170, 66_000_000, 66_000_000, true, true);
        close(jump.panDegrees(), LaserMotion.raw(200, 170).panDegrees(), "Large pan cue must snap");
        close(
                jump.tiltDegrees(),
                LaserMotion.raw(200, 170).tiltDegrees(),
                "Large tilt cue must snap");

        var cut = new LaserMotion();
        cut.advance(127, 127, 0, 0, true, true);
        cut.advance(127, 127, 33_000_000, 33_000_000, true, true);
        close(
                cut.advance(133, 127, 66_000_000, 66_000_000, true, true).panDegrees(),
                LaserMotion.raw(133, 127).panDegrees(),
                "Abrupt small cut after a hold must snap");
        var wrap = new LaserMotion();
        wrap.advance(254, 127, 0, 0, true, true);
        wrap.advance(0, 127, 33_000_000, 33_000_000, true, true);
        double seam = wrap.advance(0, 127, 33_000_000, 50_000_000, true, true).panDegrees();
        check(seam > 178 && seam < 180.01, "Pan wrap must use the short arc");
        var raw = tracking.advance(15, 250, 100_000_000, 100_000_000, false, true);
        check(raw.equals(LaserMotion.raw(15, 250)), "Raw mode must preserve both channels");
        var dark = tracking.advance(245, 15, 133_000_000, 133_000_000, true, false);
        check(dark.equals(LaserMotion.raw(245, 15)), "Dark positioning must be immediate");
        var relit = tracking.advance(100, 200, 166_000_000, 166_000_000, true, true);
        check(
                relit.equals(LaserMotion.raw(100, 200)),
                "Relighting must not sweep from an old position");
        var resumed = tracking.advance(101, 200, 1_000_000_000, 1_000_000_000, true, true);
        check(
                resumed.equals(LaserMotion.raw(101, 200)),
                "Decoder recovery must reset stale movement");

        Double expected = null;
        for (int fps : new int[] {30, 60, 144}) {
            var smooth = new LaserMotion();
            smooth.advance(127, 127, 0, 0, true, true);
            smooth.advance(129, 127, 1_000_000, 1_000_000, true, true);
            LaserMotion.Aim value = null;
            for (int i = 1; i <= fps; i++)
                value =
                        smooth.advance(
                                129,
                                127,
                                1_000_000,
                                1_000_000 + Math.round(i * 1e9 / fps),
                                true,
                                true);
            if (expected == null) expected = value.panDegrees();
            else close(value.panDegrees(), expected, "Frame rate changed tracking");
        }
        var held = tracking.advance(101, 200, 1_000_000_000, 1_020_000_000, true, true);
        var later = tracking.advance(101, 200, 1_000_000_000, 1_200_000_000, true, true);
        close(held.panDegrees(), later.panDegrees(), "Held decoder input must not extrapolate");
        for (double facing : new double[] {0, 90, 180, 270}) {
            var d = LaserMotion.raw(127, 180).direction(facing);
            close(
                    d.x() * d.x() + d.y() * d.y() + d.z() * d.z(),
                    1,
                    "Beam direction must stay normalized");
            check(d.y() < 0, "Positive pitch must point down");
        }
    }

    /** CSV rows: seconds followed by eight groups of pan, tilt, visible, facing, x, y, z. */
    private static void replay(Path file) throws Exception {
        var rows = new ArrayList<double[]>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] parts = line.split(",");
            double[] row = new double[parts.length];
            for (int i = 0; i < parts.length; i++) row[i] = Double.parseDouble(parts[i]);
            rows.add(row);
        }
        var filters = new LaserMotion[8];
        for (int i = 0; i < 8; i++) filters[i] = new LaserMotion();
        int sample = 0, visibleFrames = 0, floorChecks = 0, trackingFrames = 0, preservedCuts = 0;
        double rawSquared = 0, smoothSquared = 0, maxRawStep = 0, maxSmoothStep = 0;
        var previousRaw = new LaserMotion.Direction[8];
        var previousSmooth = new LaserMotion.Direction[8];
        long[] cutGuard = new long[8];
        boolean[] wasVisible = new boolean[8];
        int frames = (int) Math.floor(rows.getLast()[0] * 144);
        for (int frame = 0; frame <= frames; frame++) {
            double time = frame / 144.0;
            while (sample + 1 < rows.size() && rows.get(sample + 1)[0] <= time + 1e-9) sample++;
            double[] row = rows.get(sample);
            long now = Math.round(time * 1e9), stamp = Math.round(row[0] * 1e9);
            for (int i = 0; i < 8; i++) {
                int a = 1 + i * 7;
                boolean visible = row[a + 2] > 0;
                var aim =
                        filters[i].advance(
                                (int) row[a], (int) row[a + 1], stamp, now, true, visible);
                var target = LaserMotion.raw((int) row[a], (int) row[a + 1]);
                var rawDirection = target.direction(row[a + 3]);
                var d = aim.direction(row[a + 3]);
                if (visible && wasVisible[i]) {
                    double rawStep = angle(rawDirection, previousRaw[i]),
                            smoothStep = angle(d, previousSmooth[i]);
                    maxRawStep = Math.max(maxRawStep, rawStep);
                    maxSmoothStep = Math.max(maxSmoothStep, smoothStep);
                    if (rawStep >= 18) {
                        check(angle(d, rawDirection) < 1e-5, "Authored jump was softened");
                        preservedCuts++;
                    }
                    if (rawStep > 8) cutGuard[i] = now + 150_000_000L;
                    if (now > cutGuard[i]) {
                        rawSquared += rawStep * rawStep;
                        smoothSquared += smoothStep * smoothStep;
                        trackingFrames++;
                    }
                }
                previousRaw[i] = rawDirection;
                previousSmooth[i] = d;
                wasVisible[i] = visible;
                if (!visible) continue;
                visibleFrames++;
                double distance = (64 - (row[a + 5] + .65)) / d.y();
                double x = row[a + 4] + .5 + distance * d.x(),
                        z = row[a + 6] + .5 + distance * d.z();
                check(d.y() < 0 && distance - .6 <= 32, "Smoothed beam fails floor/range");
                check(x >= 52 && x <= 76 && z >= -5 && z <= 14, "Smoothed beam left dance floor");
                floorChecks++;
            }
        }
        double rawRms = Math.sqrt(rawSquared / trackingFrames),
                smoothRms = Math.sqrt(smoothSquared / trackingFrames);
        check(
                smoothRms < rawRms * .90,
                "Tracking did not reduce per-frame angular steps: " + smoothRms + " vs " + rawRms);
        System.out.printf(
                java.util.Locale.ROOT,
                "{\"passed\":true,\"render_fps\":144,\"frames\":%d,\"visible_laser_frames\":%d,"
                    + "\"floor_checks\":%d,\"preserved_large_cuts\":%d,"
                    + "\"raw_tracking_rms_degrees\":%.9f,\"smoothed_tracking_rms_degrees\":%.9f,"
                    + "\"max_raw_step_degrees\":%.9f,\"max_smoothed_step_degrees\":%.9f}%n",
                frames + 1,
                visibleFrames,
                floorChecks,
                preservedCuts,
                rawRms,
                smoothRms,
                maxRawStep,
                maxSmoothStep);
    }

    private static double angle(LaserMotion.Direction a, LaserMotion.Direction b) {
        return Math.toDegrees(
                Math.acos(Math.clamp(a.x() * b.x() + a.y() * b.y() + a.z() * b.z(), -1.0, 1.0)));
    }

    public static void main(String[] args) throws Exception {
        run();
        if (args.length > 0) replay(Path.of(args[0]));
        else
            System.out.println(
                    "Laser movement probe passed: tracking, cuts, seam, raw, blackout, recovery and"
                            + " frame rates.");
    }
}
