// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;

import java.util.*;
import java.util.function.Consumer;

public final class StageNetwork {
    public static Consumer<Open> openClient = p -> {};
    public static Consumer<OpenFixture> openFixtureClient = p -> {};

    static ManualFixtureSettings readManual(RegistryFriendlyByteBuf b) {
        return new ManualFixtureSettings(
                b.readBoolean(),
                b.readBoolean(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt(),
                b.readVarInt());
    }

    static void writeManual(RegistryFriendlyByteBuf b, ManualFixtureSettings value) {
        b.writeBoolean(value.override());
        b.writeBoolean(value.enabled());
        b.writeVarInt(value.output());
        b.writeVarInt(value.red());
        b.writeVarInt(value.green());
        b.writeVarInt(value.blue());
        b.writeVarInt(value.pan());
        b.writeVarInt(value.tilt());
        b.writeVarInt(value.spread());
    }

    public record OpenFixture(
            BlockPos pos,
            UUID fixtureId,
            int revision,
            FixtureProfile profile,
            ManualFixtureSettings settings,
            float fixtureTilt)
            implements CustomPacketPayload {
        static final Type<OpenFixture> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Soundstage.ID, "open_fixture"));
        static final StreamCodec<RegistryFriendlyByteBuf, OpenFixture> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBlockPos(p.pos);
                            b.writeUUID(p.fixtureId);
                            b.writeVarInt(p.revision);
                            b.writeEnum(p.profile);
                            writeManual(b, p.settings);
                            b.writeFloat(p.fixtureTilt);
                        },
                        b ->
                                new OpenFixture(
                                        b.readBlockPos(),
                                        b.readUUID(),
                                        b.readVarInt(),
                                        b.readEnum(FixtureProfile.class),
                                        readManual(b),
                                        b.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SaveFixture(
            BlockPos pos,
            UUID fixtureId,
            int revision,
            ManualFixtureSettings settings,
            float fixtureTilt)
            implements CustomPacketPayload {
        static final Type<SaveFixture> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Soundstage.ID, "save_fixture"));
        static final StreamCodec<RegistryFriendlyByteBuf, SaveFixture> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBlockPos(p.pos);
                            b.writeUUID(p.fixtureId);
                            b.writeVarInt(p.revision);
                            writeManual(b, p.settings);
                            b.writeFloat(p.fixtureTilt);
                        },
                        b ->
                                new SaveFixture(
                                        b.readBlockPos(),
                                        b.readUUID(),
                                        b.readVarInt(),
                                        readManual(b),
                                        b.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Settings(
            String url,
            int volume,
            int range,
            boolean enabled,
            boolean lighting,
            int address,
            boolean playlistMode,
            boolean loopPlaylist,
            boolean spatial,
            boolean walls,
            int trackIndex,
            List<PlaylistTrack> playlist,
            int fallbackMode,
            int fallbackColor,
            String group) {
        public Settings {
            playlist = List.copyOf(playlist);
        }

        public Settings(
                String url, int volume, int range, boolean enabled, boolean lighting, int address) {
            this(
                    url, volume, range, enabled, lighting, address, false, true, true, true, 0,
                    List.of(), 0, 0xffbb77, "Main");
        }

        static Settings read(RegistryFriendlyByteBuf b) {
            String url = b.readUtf(2048);
            int volume = b.readVarInt(), range = b.readVarInt();
            boolean enabled = b.readBoolean(), lighting = b.readBoolean();
            int address = b.readVarInt();
            boolean mode = b.readBoolean(),
                    loop = b.readBoolean(),
                    spatial = b.readBoolean(),
                    walls = b.readBoolean();
            int index = b.readVarInt(), count = b.readVarInt();
            if (count < 0 || count > 32) throw new IllegalArgumentException("Playlist too large");
            var tracks = new ArrayList<PlaylistTrack>();
            for (int i = 0; i < count; i++)
                tracks.add(new PlaylistTrack(b.readUtf(80), b.readUtf(2048), b.readVarInt()));
            return new Settings(
                    url,
                    volume,
                    range,
                    enabled,
                    lighting,
                    address,
                    mode,
                    loop,
                    spatial,
                    walls,
                    index,
                    tracks,
                    b.readVarInt(),
                    b.readInt(),
                    b.readUtf(32));
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeUtf(url, 2048);
            b.writeVarInt(volume);
            b.writeVarInt(range);
            b.writeBoolean(enabled);
            b.writeBoolean(lighting);
            b.writeVarInt(address);
            b.writeBoolean(playlistMode);
            b.writeBoolean(loopPlaylist);
            b.writeBoolean(spatial);
            b.writeBoolean(walls);
            b.writeVarInt(trackIndex);
            b.writeVarInt(playlist.size());
            for (var t : playlist) {
                b.writeUtf(t.title(), 80);
                b.writeUtf(t.url(), 2048);
                b.writeVarInt(t.seconds());
            }
            b.writeVarInt(fallbackMode);
            b.writeInt(fallbackColor);
            b.writeUtf(group, 32);
        }

        boolean valid() {
            return volume >= 0
                    && volume <= 100
                    && range >= 1
                    && range <= 32
                    && address >= 1
                    && address <= 509
                    && playlist.size() <= 32
                    && trackIndex >= 0
                    && trackIndex < Math.max(1, playlist.size())
                    && fallbackMode >= 0
                    && fallbackMode <= 2
                    && fallbackColor >= 0
                    && fallbackColor <= 0xffffff
                    && group != null
                    && !group.isBlank()
                    && group.length() <= 32;
        }
    }

    public record Patch(
            BlockPos pos, int address, String group, String name, FixtureProfile profile) {
        public Patch(BlockPos pos, int address, String group) {
            this(pos, address, group, "", FixtureProfile.RGB);
        }

        static Patch read(RegistryFriendlyByteBuf b) {
            return new Patch(
                    b.readBlockPos(),
                    b.readVarInt(),
                    b.readUtf(32),
                    b.readUtf(48),
                    b.readEnum(FixtureProfile.class));
        }

        void write(RegistryFriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeVarInt(address);
            b.writeUtf(group, 32);
            b.writeUtf(name, 48);
            b.writeEnum(profile);
        }
    }

    public record Open(BlockPos pos, Settings settings, List<Patch> patches)
            implements CustomPacketPayload {
        public Open(BlockPos pos, Settings settings) {
            this(pos, settings, List.of());
        }

        static final Type<Open> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Soundstage.ID, "open"));
        static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBlockPos(p.pos);
                            p.settings.write(b);
                            b.writeVarInt(p.patches.size());
                            p.patches.forEach(x -> x.write(b));
                        },
                        b -> {
                            var pos = b.readBlockPos();
                            var s = Settings.read(b);
                            int count = b.readVarInt();
                            if (count < 0 || count > 64)
                                throw new IllegalArgumentException("Too many patches");
                            var patches = new ArrayList<Patch>();
                            for (int i = 0; i < count; i++) patches.add(Patch.read(b));
                            return new Open(pos, s, patches);
                        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Save(BlockPos pos, Settings settings) implements CustomPacketPayload {
        static final Type<Save> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Soundstage.ID, "save"));
        static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBlockPos(p.pos);
                            p.settings.write(b);
                        },
                        b -> new Save(b.readBlockPos(), Settings.read(b)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record EditPatch(BlockPos desk, Patch patch, boolean wholeGroup, boolean remove)
            implements CustomPacketPayload {
        static final Type<EditPatch> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Soundstage.ID, "patch"));
        static final StreamCodec<RegistryFriendlyByteBuf, EditPatch> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBlockPos(p.desk);
                            p.patch.write(b);
                            b.writeBoolean(p.wholeGroup);
                            b.writeBoolean(p.remove);
                        },
                        b ->
                                new EditPatch(
                                        b.readBlockPos(),
                                        Patch.read(b),
                                        b.readBoolean(),
                                        b.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record Action(BlockPos pos, String action) implements CustomPacketPayload {
        static final Type<Action> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Soundstage.ID, "action"));
        static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.of(
                        (b, p) -> {
                            b.writeBlockPos(p.pos);
                            b.writeUtf(p.action, 16);
                        },
                        b -> new Action(b.readBlockPos(), b.readUtf(16)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static Open snapshot(StageEntity d) {
        return new Open(
                d.getBlockPos(),
                d.settings(),
                d.lights.entrySet().stream()
                        .map(
                                e ->
                                        new Patch(
                                                e.getKey(),
                                                e.getValue(),
                                                d.groups.getOrDefault(e.getKey(), "Main"),
                                                d.names.getOrDefault(e.getKey(), ""),
                                                FixtureProfile.at(d.getLevel(), e.getKey())))
                        .toList());
    }

    public static OpenFixture fixtureSnapshot(StageEntity fixture) {
        return new OpenFixture(
                fixture.getBlockPos(),
                fixture.id,
                fixture.revision,
                FixtureProfile.at(fixture.getLevel(), fixture.getBlockPos()),
                fixture.manualSettings(),
                StageLightPhysics.clampTilt(fixture.tiltDegrees));
    }

    public static boolean claim(Player p, BlockPos pos) {
        var event =
                CommonHooks.onRightClickBlock(
                        p,
                        InteractionHand.MAIN_HAND,
                        pos,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        return !event.isCanceled() && event.getUseBlock() != TriState.FALSE;
    }

    public static boolean access(Player p, BlockPos pos) {
        return p.isAlive()
                && !p.isSpectator()
                && p.mayBuild()
                && p.level().hasChunkAt(pos)
                && p.distanceToSqr(Vec3.atCenterOf(pos)) <= 64
                && claim(p, pos);
    }

    private static StageEntity editable(Player p, BlockPos pos) {
        return access(p, pos)
                        && p.level().getBlockEntity(pos) instanceof StageEntity d
                        && d.isSource()
                        && d.canEdit(p)
                ? d
                : null;
    }

    /** Check proximity and both claims before allowing a linked desk operator. */
    public static StageEntity editableFixture(Player p, BlockPos pos) {
        if (!access(p, pos)
                || !(p.level().getBlockEntity(pos) instanceof StageEntity fixture)
                || !fixture.isFixture()) return null;
        if (fixture.canEdit(p)) return fixture;
        if (fixture.desk == null || fixture.deskId == null || !p.level().hasChunkAt(fixture.desk))
            return null;
        if (!(p.level().getBlockEntity(fixture.desk) instanceof StageEntity desk)
                || !desk.isSource()
                || !fixture.deskId.equals(desk.id)
                || !desk.lights.containsKey(pos)
                || !desk.canEdit(p)
                || !claim(p, fixture.desk)) return null;
        return fixture;
    }

    static boolean validFixtureSave(SaveFixture p) {
        return p != null
                && p.pos != null
                && p.fixtureId != null
                && p.settings != null
                && p.settings.valid()
                && Float.isFinite(p.fixtureTilt)
                && p.fixtureTilt >= -80
                && p.fixtureTilt <= 80;
    }

    private static void refresh(Player p, StageEntity d) {
        if (p instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, snapshot(d));
    }

    public static void register(RegisterPayloadHandlersEvent e) {
        e.registrar("5")
                .executesOn(HandlerThread.MAIN)
                .playToClient(Open.TYPE, Open.CODEC, (p, c) -> openClient.accept(p))
                .playToClient(
                        OpenFixture.TYPE, OpenFixture.CODEC, (p, c) -> openFixtureClient.accept(p))
                .playToServer(
                        SaveFixture.TYPE,
                        SaveFixture.CODEC,
                        (p, c) -> {
                            if (!validFixtureSave(p)) return;
                            var player = c.player();
                            var fixture = editableFixture(player, p.pos);
                            if (fixture == null || !fixture.id.equals(p.fixtureId)) return;
                            if (fixture.revision != p.revision) {
                                player.displayClientMessage(
                                        Component.literal("Fixture changed; reopen its controls."),
                                        true);
                                return;
                            }
                            fixture.applyManual(p.settings, p.fixtureTilt);
                            player.displayClientMessage(
                                    Component.literal(
                                            p.settings.override()
                                                    ? "Fixture manual settings saved."
                                                    : "Fixture returned to show control."),
                                    true);
                        })
                .playToServer(
                        Save.TYPE,
                        Save.CODEC,
                        (p, c) -> {
                            var player = c.player();
                            var s = p.settings;
                            var desk = editable(player, p.pos);
                            if (!s.valid() || desk == null) return;
                            if (!StreamPolicy.allowed(s.url, Soundstage.HOSTS.get())
                                    || s.playlist.stream()
                                            .anyMatch(
                                                    t ->
                                                            !StreamPolicy.allowed(
                                                                    t.url(),
                                                                    Soundstage.HOSTS.get()))) {
                                player.displayClientMessage(
                                        Component.literal(
                                                "Stream host is not allowed. Ask a server operator"
                                                        + " to add the relay hostname."),
                                        false);
                                return;
                            }
                            desk.apply(s);
                            if (desk.getBlockState().getBlock() instanceof StageBlock block
                                    && block.kind != StageBlock.Kind.STEREO)
                                player.displayClientMessage(
                                        Component.literal("Soundstage settings saved."), true);
                        })
                .playToServer(
                        EditPatch.TYPE,
                        EditPatch.CODEC,
                        (p, c) -> {
                            var player = c.player();
                            var d = editable(player, p.desk);
                            var edit = p.patch;
                            if (d == null
                                    || !d.lights.containsKey(edit.pos)
                                    || edit.address < 1
                                    || edit.address > 509
                                    || edit.group.isBlank()
                                    || edit.group.length() > 32) return;
                            String oldGroup = d.groups.getOrDefault(edit.pos, "Main");
                            var targets =
                                    d.lights.keySet().stream()
                                            .filter(
                                                    pos ->
                                                            pos.equals(edit.pos)
                                                                    || (p.wholeGroup
                                                                            && d.groups
                                                                                    .getOrDefault(
                                                                                            pos,
                                                                                            "Main")
                                                                                    .equals(
                                                                                            oldGroup)))
                                            .toList();
                            if (targets.stream()
                                    .anyMatch(
                                            pos ->
                                                    !player.level().hasChunkAt(pos)
                                                            || !claim(player, pos)
                                                            || !FixtureProfile.at(
                                                                            player.level(), pos)
                                                                    .fits(edit.address))) return;
                            for (var pos : targets) {
                                if (p.remove) {
                                    d.lights.remove(pos);
                                    d.groups.remove(pos);
                                    d.names.remove(pos);
                                    if (player.level().getBlockEntity(pos)
                                                    instanceof StageEntity fixture
                                            && d.id.equals(fixture.deskId)) {
                                        fixture.desk = null;
                                        fixture.deskId = null;
                                        fixture.changed();
                                    }
                                } else {
                                    d.lights.put(pos, edit.address);
                                    d.groups.put(pos, edit.group);
                                    if (!p.wholeGroup) d.names.put(pos, edit.name);
                                }
                            }
                            d.changed();
                            refresh(player, d);
                        })
                .playToServer(
                        Action.TYPE,
                        Action.CODEC,
                        (p, c) -> {
                            var player = c.player();
                            var d = editable(player, p.pos);
                            if (d == null) return;
                            if (p.action.equals("demo")) {
                                String url = Soundstage.DEMO_URL.get();
                                if (url.isBlank()
                                        || !StreamPolicy.allowed(url, Soundstage.HOSTS.get())) {
                                    player.displayClientMessage(
                                            Component.literal("The server demo is not configured."),
                                            false);
                                    return;
                                }
                                d.url = url;
                                d.playlistMode = false;
                                d.enabled = true;
                                d.lighting = true;
                            } else if (!d.playlist.isEmpty()
                                    && (p.action.equals("next") || p.action.equals("previous"))) {
                                d.trackIndex =
                                        Math.floorMod(
                                                d.trackIndex + (p.action.equals("next") ? 1 : -1),
                                                d.playlist.size());
                                d.trackStartedTick = player.level().getGameTime();
                                d.pausedTrackMillis = 0;
                            } else if (VenueTools.action(player, d, p.action)) {
                                d.changed();
                                refresh(player, d);
                                return;
                            } else return;
                            d.changed();
                            refresh(player, d);
                        });
    }
}
