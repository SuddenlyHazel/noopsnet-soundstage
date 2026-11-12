// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.automation;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Wall-clock camera trajectories for the isolated lab, never shipped in mods. */
final class CinematicLabMotion {
    private record Pose(double time, double x, double y, double z, float yaw, float pitch) {}

    private final Path lab;
    private final List<Pose> poses = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private final String name;
    private final double duration;
    private final int captureFps;
    private final long startNanos = System.nanoTime();
    private int imageIndex;
    private double time;
    private Pose current;
    private boolean done;

    CinematicLabMotion(Path lab, JsonObject request) throws Exception {
        this.lab = lab;
        name = request.get("name").getAsString();
        if (!name.matches("[a-zA-Z0-9_.-]{1,70}"))
            throw new IllegalArgumentException("Unsafe motion name");
        if (Files.exists(lab.resolve(name + "-motion.csv")))
            throw new IllegalArgumentException("Motion output already exists");
        captureFps = request.has("capture_fps") ? request.get("capture_fps").getAsInt() : 0;
        if (captureFps < 0 || captureFps > 12)
            throw new IllegalArgumentException("Capture rate must be 0..12");
        for (var element : request.getAsJsonArray("keyframes")) {
            var p = element.getAsJsonObject();
            double t = number(p, "seconds"),
                    x = number(p, "x"),
                    y = number(p, "y"),
                    z = number(p, "z");
            float yaw = (float) number(p, "yaw"), pitch = (float) number(p, "pitch");
            if (Math.abs(x) > 1000000
                    || Math.abs(z) > 1000000
                    || y < -60
                    || y > 350
                    || Math.abs(pitch) > 90)
                throw new IllegalArgumentException("Motion pose out of bounds");
            if (poses.isEmpty() ? t != 0 : t <= poses.getLast().time)
                throw new IllegalArgumentException(
                        "Motion times must start at zero and strictly increase");
            poses.add(new Pose(t, x, y, z, yaw, pitch));
        }
        if (poses.size() < 2 || poses.size() > 64)
            throw new IllegalArgumentException("Motion needs 2..64 poses");
        duration = poses.getLast().time;
        if (duration < 1 || duration > 30)
            throw new IllegalArgumentException("Motion duration must be 1..30 seconds");
        rows.add("elapsed_seconds,x,y,z,yaw,pitch,screenshot_index");
        Files.writeString(lab.resolve(name + "-request.json"), request.toString());
    }

    private static double number(JsonObject object, String key) {
        double value = object.get(key).getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite motion value");
        return value;
    }

    private static double interpolate(double a, double b, double t) {
        return a + (b - a) * t;
    }

    void beforeFrame(Minecraft mc) {
        if (done) return;
        if (mc.player == null || !mc.player.isSpectator())
            throw new IllegalStateException("Lab motion requires spectator mode");
        time = Math.min(duration, (System.nanoTime() - startNanos) / 1e9);
        int segment = 0;
        while (segment + 2 < poses.size() && poses.get(segment + 1).time < time) segment++;
        Pose a = poses.get(segment), b = poses.get(segment + 1);
        double t = (time - a.time) / (b.time - a.time);
        current =
                new Pose(
                        time,
                        interpolate(a.x, b.x, t),
                        interpolate(a.y, b.y, t),
                        interpolate(a.z, b.z, t),
                        (float) (a.yaw + Math.IEEEremainder(b.yaw - a.yaw, 360.) * t),
                        (float) interpolate(a.pitch, b.pitch, t));
        var player = mc.player;
        player.setPos(current.x, current.y, current.z);
        player.xo = player.xOld = current.x;
        player.yo = player.yOld = current.y;
        player.zo = player.zOld = current.z;
        player.setYRot(current.yaw);
        player.yRotO = current.yaw;
        player.setXRot(current.pitch);
        player.xRotO = current.pitch;
        player.setDeltaMovement(0, 0, 0);
    }

    void afterFrame(Minecraft mc) throws Exception {
        if (done || current == null) return;
        int captured = -1;
        if (captureFps > 0 && (time >= imageIndex / (double) captureFps || time >= duration)) {
            captured = imageIndex++;
            Screenshot.grab(
                    mc.gameDirectory,
                    name + String.format("-%04d.png", captured),
                    mc.getMainRenderTarget(),
                    message -> {});
        }
        rows.add(
                time
                        + ","
                        + current.x
                        + ","
                        + current.y
                        + ","
                        + current.z
                        + ","
                        + current.yaw
                        + ","
                        + current.pitch
                        + ","
                        + captured);
        if (time >= duration) {
            Files.write(lab.resolve(name + "-motion.csv"), rows);
            done = true;
        }
    }

    boolean done() {
        return done;
    }

    String name() {
        return name;
    }
}
