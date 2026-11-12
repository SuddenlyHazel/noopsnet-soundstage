// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.function.Consumer;

public final class StageEntity extends BlockEntity {
    public static Consumer<StageEntity> clientLoaded = e -> {};
    public UUID id = UUID.randomUUID(), owner;
    public final Set<UUID> operators = new HashSet<>();
    public String url = "";
    public int volume = 70, range = 16, address = 1, revision;

    /** Optional per-fixture pitch; positive degrees aim down. Legacy fixtures stay horizontal. */
    public float tiltDegrees;

    public boolean enabled, lighting = true;
    public final Set<BlockPos> speakers = new LinkedHashSet<>();
    public final Map<BlockPos, Integer> lights = new LinkedHashMap<>();
    public final java.util.List<PlaylistTrack> playlist = new java.util.ArrayList<>();
    public boolean playlistMode, loopPlaylist = true, spatial = true, walls = true;
    public int trackIndex, fallbackMode, fallbackColor = 0xffbb77;
    public long trackStartedTick, pausedTrackMillis;
    public String group = "Main";
    public final Map<BlockPos, String> names = new LinkedHashMap<>();
    public final Map<BlockPos, String> groups = new LinkedHashMap<>();
    public BlockPos desk;
    public UUID deskId;
    private ManualFixtureSettings manualSettings = ManualFixtureSettings.defaults();

    public StageEntity(BlockPos pos, BlockState state) {
        super(Soundstage.ENTITY.get(), pos, state);
    }

    public boolean isSource() {
        return getBlockState().getBlock() instanceof StageBlock b
                && (b.kind == StageBlock.Kind.STEREO || b.kind == StageBlock.Kind.DESK);
    }

    public boolean isFixture() {
        if (!(getBlockState().getBlock() instanceof StageBlock b)) return false;
        return b.kind == StageBlock.Kind.LIGHT
                || b.kind == StageBlock.Kind.LASER
                || b.kind == StageBlock.Kind.FOG
                || b.kind == StageBlock.Kind.PARTICLE;
    }

    public ManualFixtureSettings manualSettings() {
        return manualSettings;
    }

    public void applyManual(ManualFixtureSettings settings, float fixtureTilt) {
        if (!isFixture()
                || settings == null
                || !settings.valid()
                || !Float.isFinite(fixtureTilt)
                || fixtureTilt < -80
                || fixtureTilt > 80)
            throw new IllegalArgumentException("Invalid manual fixture settings");
        manualSettings = settings;
        if (((StageBlock) getBlockState().getBlock()).kind == StageBlock.Kind.LIGHT)
            tiltDegrees = fixtureTilt;
        changed();
    }

    static CompoundTag saveManual(ManualFixtureSettings value) {
        var tag = new CompoundTag();
        tag.putBoolean("override", value.override());
        tag.putBoolean("enabled", value.enabled());
        tag.putInt("output", value.output());
        tag.putInt("red", value.red());
        tag.putInt("green", value.green());
        tag.putInt("blue", value.blue());
        tag.putInt("pan", value.pan());
        tag.putInt("tilt", value.tilt());
        tag.putInt("spread", value.spread());
        return tag;
    }

    static ManualFixtureSettings loadManual(CompoundTag tag) {
        var defaults = ManualFixtureSettings.defaults();
        return new ManualFixtureSettings(
                tag.getBoolean("override"),
                tag.contains("enabled") ? tag.getBoolean("enabled") : defaults.enabled(),
                manualChannel(tag, "output", defaults.output()),
                manualChannel(tag, "red", defaults.red()),
                manualChannel(tag, "green", defaults.green()),
                manualChannel(tag, "blue", defaults.blue()),
                manualChannel(tag, "pan", defaults.pan()),
                manualChannel(tag, "tilt", defaults.tilt()),
                manualChannel(tag, "spread", defaults.spread()));
    }

    private static int manualChannel(CompoundTag tag, String name, int fallback) {
        return tag.contains(name, Tag.TAG_ANY_NUMERIC)
                ? Math.clamp(tag.getInt(name), 0, 255)
                : fallback;
    }

    public boolean canEdit(Player p) {
        return !p.isSpectator()
                && p.mayBuild()
                && (p.hasPermissions(2)
                        || p.getUUID().equals(owner)
                        || operators.contains(p.getUUID()));
    }

    public boolean ownedBy(Player p) {
        return p.hasPermissions(2) || p.getUUID().equals(owner);
    }

    public void changed() {
        revision++;
        setChanged();
        if (level != null)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public String effectiveUrl() {
        return playlistMode && !playlist.isEmpty()
                ? playlist.get(Math.clamp(trackIndex, 0, playlist.size() - 1)).url()
                : playlistMode ? "" : url;
    }

    public long desiredMillis() {
        return enabled && level != null
                ? Math.max(0, (level.getGameTime() - trackStartedTick) * 50)
                : pausedTrackMillis;
    }

    public void advance() {
        if (!enabled || !playlistMode || playlist.isEmpty() || level == null) return;
        var next = PlaylistTimeline.resolve(playlist, trackIndex, desiredMillis(), loopPlaylist);
        if (next.ended()) {
            enabled = false;
            pausedTrackMillis = 0;
            changed();
        } else if (next.index() != trackIndex || desiredMillis() - next.millis() >= 1000) {
            trackIndex = next.index();
            trackStartedTick = level.getGameTime() - next.millis() / 50;
            changed();
        }
    }

    public StageNetwork.Settings settings() {
        return new StageNetwork.Settings(
                url,
                volume,
                range,
                enabled,
                lighting,
                address,
                playlistMode,
                loopPlaylist,
                spatial,
                walls,
                trackIndex,
                List.copyOf(playlist),
                fallbackMode,
                fallbackColor,
                group);
    }

    public void apply(StageNetwork.Settings s) {
        boolean reset =
                s.playlistMode() != playlistMode
                        || s.trackIndex() != trackIndex
                        || !playlist.equals(s.playlist());
        long elapsed = reset ? 0 : desiredMillis();
        url = s.url().trim();
        volume = s.volume();
        range = s.range();
        lighting = s.lighting();
        address = s.address();
        playlistMode = s.playlistMode();
        loopPlaylist = s.loopPlaylist();
        spatial = s.spatial();
        walls = s.walls();
        trackIndex = s.trackIndex();
        playlist.clear();
        playlist.addAll(s.playlist());
        fallbackMode = s.fallbackMode();
        fallbackColor = s.fallbackColor();
        group = s.group();
        enabled = s.enabled() && !effectiveUrl().isBlank();
        pausedTrackMillis = elapsed;
        if (level != null) trackStartedTick = level.getGameTime() - elapsed / 50;
        changed();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) clientLoaded.accept(this);
    }

    @Override
    protected void saveAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.saveAdditional(t, r);
        t.putUUID("venue_id", id);
        if (owner != null) t.putUUID("owner", owner);
        t.putString("url", url);
        t.putInt("volume", volume);
        t.putInt("range", range);
        t.putInt("address", address);
        t.putBoolean("enabled", enabled);
        t.putBoolean("lighting", lighting);
        t.putInt("revision", revision);
        t.putFloat("tiltDegrees", StageLightPhysics.clampTilt(tiltDegrees));
        t.put("manualFixture", saveManual(manualSettings));
        t.putBoolean("playlistMode", playlistMode);
        t.putBoolean("loopPlaylist", loopPlaylist);
        t.putBoolean("spatial", spatial);
        t.putBoolean("walls", walls);
        t.putInt("trackIndex", trackIndex);
        t.putLong("trackStartedTick", trackStartedTick);
        t.putLong("pausedTrackMillis", pausedTrackMillis);
        t.putInt("fallbackMode", fallbackMode);
        t.putInt("fallbackColor", fallbackColor);
        t.putString("group", group);
        var tracks = new ListTag();
        for (var track : playlist) {
            var item = new CompoundTag();
            item.putString("title", track.title());
            item.putString("url", track.url());
            item.putInt("seconds", track.seconds());
            tracks.add(item);
        }
        t.put("playlist", tracks);
        t.putLongArray("speakers", speakers.stream().mapToLong(BlockPos::asLong).toArray());
        var list = new ListTag();
        lights.forEach(
                (p, a) -> {
                    var l = new CompoundTag();
                    l.putLong("pos", p.asLong());
                    l.putInt("address", a);
                    l.putString("group", groups.getOrDefault(p, "Main"));
                    l.putString("name", names.getOrDefault(p, ""));
                    list.add(l);
                });
        t.put("lights", list);
        var ops = new ListTag();
        operators.stream().limit(32).forEach(u -> ops.add(StringTag.valueOf(u.toString())));
        t.put("operators", ops);
        if (desk != null && deskId != null) {
            t.putLong("desk", desk.asLong());
            t.putUUID("deskId", deskId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag t, HolderLookup.Provider r) {
        super.loadAdditional(t, r);
        if (t.hasUUID("venue_id")) id = t.getUUID("venue_id");
        owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
        url = t.getString("url");
        if (url.length() > 2048) url = "";
        volume = Math.clamp(t.getInt("volume"), 0, 100);
        range = Math.clamp(t.getInt("range"), 1, 32);
        address = Math.clamp(t.getInt("address"), 1, 509);
        tiltDegrees = StageLightPhysics.clampTilt(t.getFloat("tiltDegrees"));
        manualSettings =
                t.contains("manualFixture", Tag.TAG_COMPOUND)
                        ? loadManual(t.getCompound("manualFixture"))
                        : ManualFixtureSettings.defaults();
        enabled = t.getBoolean("enabled");
        lighting = t.getBoolean("lighting");
        revision = t.getInt("revision");
        playlistMode = t.getBoolean("playlistMode");
        loopPlaylist = !t.contains("loopPlaylist") || t.getBoolean("loopPlaylist");
        spatial = !t.contains("spatial") || t.getBoolean("spatial");
        walls = !t.contains("walls") || t.getBoolean("walls");
        playlist.clear();
        var tracks = t.getList("playlist", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(32, tracks.size()); i++) {
            var track = tracks.getCompound(i);
            try {
                playlist.add(
                        new PlaylistTrack(
                                track.getString("title"),
                                track.getString("url"),
                                track.getInt("seconds")));
            } catch (IllegalArgumentException ignored) {
            }
        }
        trackIndex = Math.clamp(t.getInt("trackIndex"), 0, Math.max(0, playlist.size() - 1));
        trackStartedTick = t.getLong("trackStartedTick");
        pausedTrackMillis = Math.max(0, t.getLong("pausedTrackMillis"));
        fallbackMode = Math.clamp(t.getInt("fallbackMode"), 0, 2);
        fallbackColor =
                t.contains("fallbackColor") ? t.getInt("fallbackColor") & 0xffffff : 0xffbb77;
        group = t.contains("group") ? t.getString("group") : "Main";
        if (group.length() > 32) group = "Main";
        speakers.clear();
        for (long pos : t.getLongArray("speakers")) {
            if (speakers.size() >= 32) break;
            speakers.add(BlockPos.of(pos));
        }
        lights.clear();
        groups.clear();
        names.clear();
        var ls = t.getList("lights", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(64, ls.size()); i++) {
            var l = ls.getCompound(i);
            lights.put(BlockPos.of(l.getLong("pos")), Math.clamp(l.getInt("address"), 1, 509));
            String name = l.getString("name");
            if (!name.isBlank() && name.length() <= 48)
                names.put(BlockPos.of(l.getLong("pos")), name);
            String g = l.getString("group");
            groups.put(BlockPos.of(l.getLong("pos")), g.isBlank() || g.length() > 32 ? "Main" : g);
        }
        operators.clear();
        var ops = t.getList("operators", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(32, ops.size()); i++)
            try {
                operators.add(UUID.fromString(ops.getString(i)));
            } catch (IllegalArgumentException ignored) {
            }
        desk = t.contains("desk") ? BlockPos.of(t.getLong("desk")) : null;
        deskId = t.hasUUID("deskId") ? t.getUUID("deskId") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider r) {
        return saveWithoutMetadata(r);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
