// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.noopsnet.soundstage.client.ManualFixtureInputs;

import java.util.UUID;

/** Offline state, codec, input mapping and caching checks; never launches a game. */
public final class ManualFixtureProbe {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    public static void main(String[] args) {
        var defaults = ManualFixtureSettings.defaults();
        require(
                defaults.valid() && !defaults.override(),
                "Legacy fixtures must stay show-controlled");
        var manual = new ManualFixtureSettings(true, true, 128, 32, 96, 255, 200, 12, 77);
        require(manual.valid(), "Manual settings are valid");
        require(
                !new ManualFixtureSettings(true, true, -1, 32, 96, 255, 200, 12, 77).valid(),
                "Reject negative output");
        require(
                !new ManualFixtureSettings(true, true, 128, 32, 256, 255, 200, 12, 77).valid(),
                "Reject excessive RGB");
        require(
                !new ManualFixtureSettings(true, true, 128, 32, 96, 255, 200, 12, 999).valid(),
                "Reject excessive spread");
        require(
                !manual.withOverride(false).override()
                        && manual.withOverride(false).output() == 128,
                "Return to show preserves manual settings");
        require(
                !manual.withEnabled(false).enabled() && manual.withEnabled(false).override(),
                "Manual off remains an override");

        CompoundTag tag = StageEntity.saveManual(manual);
        require(
                StageEntity.loadManual(tag).equals(manual),
                "Persistent manual settings round trip");
        require(
                StageEntity.loadManual(new CompoundTag()).equals(defaults),
                "Missing stored settings use safe defaults");
        tag.putInt("red", -20);
        tag.putInt("green", 9999);
        var bounded = StageEntity.loadManual(tag);
        require(
                bounded.valid() && bounded.red() == 0 && bounded.green() == 255,
                "Malformed saved values are bounded");

        var id = UUID.fromString("c0de0000-0000-0000-0000-000000000001");
        var pos = new BlockPos(15, 64, -19);
        var open = new StageNetwork.OpenFixture(pos, id, 42, FixtureProfile.LASER, manual, 12.5f);
        var save = new StageNetwork.SaveFixture(pos, id, 42, manual, -17.25f);
        require(StageNetwork.validFixtureSave(save), "Well-formed fixture save is accepted");
        require(
                !StageNetwork.validFixtureSave(
                        new StageNetwork.SaveFixture(pos, id, 42, manual, Float.NaN)),
                "Non-finite fixture tilt is rejected");
        require(
                !StageNetwork.validFixtureSave(
                        new StageNetwork.SaveFixture(pos, id, 42, manual, 81)),
                "Out-of-range fixture tilt is rejected");
        require(
                !StageNetwork.validFixtureSave(
                        new StageNetwork.SaveFixture(
                                pos,
                                id,
                                42,
                                new ManualFixtureSettings(true, true, 999, 0, 0, 0, 0, 0, 0),
                                0)),
                "Out-of-range network channels are rejected");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            StageNetwork.OpenFixture.CODEC.encode(buffer, open);
            require(
                    StageNetwork.OpenFixture.CODEC.decode(buffer).equals(open),
                    "Fixture-open payload round trip");
            buffer.clear();
            StageNetwork.SaveFixture.CODEC.encode(buffer, save);
            require(
                    StageNetwork.SaveFixture.CODEC.decode(buffer).equals(save),
                    "Fixture-save payload round trip");
        } finally {
            buffer.release();
        }

        require(
                ManualFixtureInputs.percentToChannel(0) == 0
                        && ManualFixtureInputs.percentToChannel(100) == 255,
                "Percent controls cover channel endpoints");
        require(
                ManualFixtureInputs.channelToPercent(128) == 50,
                "Channel control reports 50 percent");
        require(
                ManualFixtureInputs.degreesToChannel(-180, 180) == 0
                        && ManualFixtureInputs.degreesToChannel(180, 180) == 255
                        && ManualFixtureInputs.degreesToChannel(-90, 90) == 0
                        && ManualFixtureInputs.degreesToChannel(90, 90) == 255,
                "Laser angles cover their ranges");
        require(ManualFixtureInputs.parseColor("#12aBcD") == 0x12abcd, "Six-digit RGB color");
        rejects(() -> ManualFixtureInputs.parseColor("FFF"), "Reject short color");
        rejects(() -> ManualFixtureInputs.percentToChannel(101), "Reject invalid percent");
        rejects(() -> ManualFixtureInputs.degreesToChannel(181, 180), "Reject invalid laser angle");

        var cache = new ManualFixtureInputs();
        var rgb = cache.frame(manual, FixtureProfile.RGB, 509, 100);
        require(
                rgb.channels().length == 512
                        && rgb.channels()[508] == 128
                        && rgb.channels()[509] == 32
                        && rgb.channels()[510] == 96
                        && rgb.channels()[511] == 255,
                "RGB channels use the actual 509 address");
        require(
                rgb == cache.frame(manual, FixtureProfile.RGB, 509, 5000),
                "Frames and samples are cached");
        require(rgb.nanos() == 100 && rgb.channels()[508] == 128, "Manual data does not expire");
        var shifted = cache.frame(manual, FixtureProfile.RGB, 2, 6000);
        require(
                shifted != rgb
                        && shifted.nanos() == 6000
                        && shifted.channels()[0] == 0
                        && shifted.channels()[1] == 128
                        && shifted.channels()[508] == 0,
                "Address change refreshes cache without stale channels");
        var laser = cache.frame(manual, FixtureProfile.LASER, 507, 7000);
        require(
                laser.channels()[506] == 128
                        && laser.channels()[510] == 200
                        && laser.channels()[511] == 12,
                "Laser pan and tilt use the correct footprint");
        var fog = cache.frame(manual, FixtureProfile.FOG, 511, 8000);
        require(
                fog.channels()[510] == 128 && fog.channels()[511] == 77,
                "Standalone fog output and spread use the correct footprint");
        var particle = cache.frame(manual, FixtureProfile.PARTICLE, 509, 9000);
        require(
                particle.channels()[508] == 128 && particle.channels()[511] == 255,
                "Particle output and color use the correct footprint");
        var off = cache.frame(manual.withEnabled(false), FixtureProfile.RGB, 1, 10000);
        require(
                off.channels()[0] == 0 && off.channels()[1] == 32,
                "Manual off blackouts the fixture while preserving its settings");
        rejects(
                () -> cache.frame(manual.withOverride(false), FixtureProfile.RGB, 1, 1),
                "Reject nonmanual frame");
        rejects(
                () -> cache.frame(manual, FixtureProfile.LASER, 508, 1),
                "Reject fixture footprint overflow");
        System.out.println(
                "Manual fixture state, persistence, codecs, bounds, DMX footprints and cache probes"
                        + " passed.");
    }
}
