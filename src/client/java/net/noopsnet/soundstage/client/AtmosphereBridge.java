// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.noopsnet.soundstage.*;

import java.lang.reflect.Method;
import java.util.*;

/** Optional API: Soundstage still works without Afterlight/Iris or the paired pack. */
final class AtmosphereBridge {
    private static Method submit, reset, density;
    private static boolean lookedUp, failed;
    static boolean volumetric;

    private record Beam(Vec3 origin, Vec3 direction, double length) {}

    static void update(Map<BlockPos, StageLightPhysics.Radiance> stageRadiance) {
        if (!lookedUp) {
            lookedUp = true;
            try {
                submit =
                        Class.forName("net.noopsnet.neon.client.AfterlightAtmosphere")
                                .getMethod("submit", double[].class, double[].class);
                reset = submit.getDeclaringClass().getMethod("clear");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                submit = null;
                reset = null;
            }
        }
        if (submit == null || failed) {
            volumetric = false;
            return;
        }
        if (density == null)
            try {
                density = submit.getDeclaringClass().getMethod("setFogDensity", double.class);
            } catch (NoSuchMethodException ignored) {
            }
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        var fog = new ArrayList<Double>();
        var beams = new ArrayList<Double>();
        // Stable nearest-first caps avoid hash iteration choosing different machines.
        var cues =
                PerformanceEffects.cues.entrySet().stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        e -> e.getKey().distToCenterSqr(mc.player.position())))
                        .toList();
        for (var entry : cues) {
            var cue = entry.getValue();
            int i = cue.address() - 1;
            double power =
                    cue.values()[i]
                            / 255.0
                            * (cue.profile() == FixtureProfile.FOG
                                    ? 1.0
                                    : SoundstageClient.brightness / 100.0);
            if (power <= 0 || mc.player.distanceToSqr(Vec3.atCenterOf(entry.getKey())) > 48 * 48)
                continue;
            var state = mc.level.getBlockState(entry.getKey());
            if (!state.hasProperty(StageBlock.FACING)) continue;
            var facing = state.getValue(StageBlock.FACING);
            if (cue.profile() == FixtureProfile.FOG && fog.size() < 8 * 5) {
                var p =
                        Vec3.atCenterOf(entry.getKey())
                                .add(facing.getStepX() * .9, .1, facing.getStepZ() * .9);
                Collections.addAll(fog, p.x, p.y, p.z, power, cue.values()[i + 1] / 255.0);
            } else if (cue.profile() == FixtureProfile.LASER
                    && SoundstageClient.showLasers
                    && beams.size() < 8 * 16) {
                var direction = cue.laserDirection(facing.toYRot());
                append(
                        beams,
                        trace(entry.getKey(), direction, true),
                        .055,
                        .0012,
                        true,
                        linear(cue.values()[i + 1]) * power,
                        linear(cue.values()[i + 2]) * power,
                        linear(cue.values()[i + 3]) * power);
            }
        }
        for (var entry :
                stageRadiance.entrySet().stream()
                        .sorted(
                                Comparator.comparingDouble(
                                        e -> e.getKey().distToCenterSqr(mc.player.position())))
                        .toList()) {
            if (beams.size() >= 8 * 16) break;
            var radiance = entry.getValue();
            if (radiance.black()) continue;
            var state = mc.level.getBlockState(entry.getKey());
            if (!state.hasProperty(StageBlock.FACING)) continue;
            var facing = state.getValue(StageBlock.FACING);
            float tilt =
                    mc.level.getBlockEntity(entry.getKey()) instanceof StageEntity fixture
                            ? fixture.tiltDegrees
                            : 0;
            var aimed = StageLightPhysics.direction(facing.getStepX(), facing.getStepZ(), tilt);
            var direction = new Vec3(aimed[0], aimed[1], aimed[2]);
            // Keep the source at the physical front lens while tilting its axis.
            var origin =
                    Vec3.atCenterOf(entry.getKey())
                            .add(facing.getStepX() * .6, .15, facing.getStepZ() * .6);
            append(
                    beams,
                    new Beam(origin, direction, 24),
                    .18,
                    .22,
                    false,
                    radiance.red(),
                    radiance.green(),
                    radiance.blue());
        }
        try {
            if (density != null)
                density.invoke(
                        null, SoundstageClient.showFog ? SoundstageClient.fogDensity / 100.0 : 0.0);
            volumetric =
                    (boolean)
                            submit.invoke(
                                    null,
                                    fog.stream().mapToDouble(Double::doubleValue).toArray(),
                                    beams.stream().mapToDouble(Double::doubleValue).toArray());
        } catch (ReflectiveOperationException | LinkageError ex) {
            failed = true;
            volumetric = false;
            System.getLogger(AtmosphereBridge.class.getName())
                    .log(
                            System.Logger.Level.WARNING,
                            "Volumetric mist unavailable; using particle fallback",
                            ex);
        }
    }

    private static double linear(int value) {
        return Math.pow(value / 255.0, 2.2);
    }

    private static Beam trace(BlockPos fixture, Vec3 direction, boolean laser) {
        var mc = Minecraft.getInstance();
        var start = Vec3.atCenterOf(fixture).add(0, .15, 0).add(direction.scale(.6));
        var end = start.add(direction.scale(laser ? 32 : 24));
        // A blocker in the center must not truncate the entire spotlight:
        // shared per-sample visibility casts its actual voxel shadow instead.
        if (!laser) return new Beam(start, direction, 24);
        var hit =
                mc.level.clip(
                        new ClipContext(
                                start,
                                end,
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                mc.player) {
                            @Override
                            public net.minecraft.world.phys.shapes.VoxelShape getBlockShape(
                                    net.minecraft.world.level.block.state.BlockState block,
                                    net.minecraft.world.level.BlockGetter world,
                                    BlockPos pos) {
                                return pos.equals(fixture)
                                        ? net.minecraft.world.phys.shapes.Shapes.empty()
                                        : super.getBlockShape(block, world, pos);
                            }
                        });
        if (hit.getType() != HitResult.Type.MISS) end = hit.getLocation();
        return new Beam(start, direction, start.distanceTo(end));
    }

    private static void append(
            List<Double> out,
            Beam b,
            double radius,
            double slope,
            boolean laser,
            double red,
            double green,
            double blue) {
        Collections.addAll(
                out,
                b.origin.x,
                b.origin.y,
                b.origin.z,
                b.length,
                b.direction.x,
                b.direction.y,
                b.direction.z,
                radius,
                red,
                green,
                blue,
                slope,
                laser ? 1.0 : 0.0,
                0.0,
                0.0,
                0.0);
    }

    static void clear() {
        volumetric = false;
        if (submit != null && !failed)
            try {
                reset.invoke(null);
            } catch (ReflectiveOperationException ignored) {
            }
    }
}
