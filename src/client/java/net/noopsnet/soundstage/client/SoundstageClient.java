// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.noopsnet.soundstage.*;

import org.watermedia.api.player.PlayerAPI;

import java.net.URI;
import java.nio.file.*;
import java.util.*;

@Mod(value = Soundstage.ID, dist = Dist.CLIENT)
public final class SoundstageClient {
    private static final Set<StageEntity> LOADED = new HashSet<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<BlockPos, Integer> COLORS = new HashMap<>();
    private static final Map<BlockPos, StageLightPhysics.Radiance> STAGE_RADIANCE = new HashMap<>();
    private static final Map<BlockPos, StageLightPhysics.Smoother> STAGE_SMOOTHED = new HashMap<>();

    private record LaserTracking(
            UUID venue,
            VenuePlayer player,
            long startTick,
            int address,
            int facing,
            LaserMotion motion) {}

    private static final Map<BlockPos, LaserTracking> LASER_TRACKING = new HashMap<>();
    private static final Map<BlockPos, ManualFixtureInputs> MANUAL_INPUTS = new HashMap<>();
    private static final Map<BlockPos, net.minecraft.world.level.block.entity.BlockEntity>
            OVERRIDDEN = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static int tick;
    static int personalVolume = 100,
            smoothingMillis,
            brightness = 100,
            particleDensity = 100,
            fogDensity = 100;
    private static final Map<BlockPos, LightSmoother> SMOOTHED = new HashMap<>();
    private static long lastLightFrame;
    private static List<StageEntity> active = List.of();
    private static long lastAfterlight;
    static boolean mute, lights = true, showFog = true, showLasers = true, laserSmoothing = true;
    private static final Path PREFS =
            net.neoforged.fml.loading.FMLPaths.CONFIGDIR
                    .get()
                    .resolve("soundstage-listener.properties");

    private static final class Session {
        VenuePlayer player;
        String url;
        String playbackKey;
        boolean lighting;
        long started;
        long retry;
        int failures;
        String status = "Starting";
        final PlaybackSynchronizer sync = new PlaybackSynchronizer();

        void close() {
            if (player != null) {
                try {
                    player.release();
                } catch (RuntimeException ignored) {
                }
                player = null;
            }
        }
    }

    public SoundstageClient(IEventBus bus, net.neoforged.fml.ModContainer container) {
        loadPrefs();
        StageEntity.clientLoaded = LOADED::add;
        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (net.neoforged.neoforge.client.gui.IConfigScreenFactory)
                        (mod, parent) -> new ListenerSettingsScreen(parent));
        NeoForge.EVENT_BUS.addListener(
                (ScreenEvent.Init.Post e) -> {
                    if (e.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)
                        e.addListener(
                                net.minecraft.client.gui.components.Button.builder(
                                                Component.literal("Soundstage settings"),
                                                b ->
                                                        Minecraft.getInstance()
                                                                .setScreen(
                                                                        new ListenerSettingsScreen(
                                                                                e.getScreen())))
                                        .bounds(e.getScreen().width - 130, 8, 122, 20)
                                        .build());
                });
        StageNetwork.openClient =
                p -> {
                    var mc = Minecraft.getInstance();
                    if (mc.screen instanceof StereoScreen stereo) {
                        stereo.update(p);
                    } else if (mc.screen instanceof DeskScreen old) {
                        mc.setScreen(new DeskScreen(p, old));
                    } else if (mc.level != null
                            && mc.level.getBlockState(p.pos()).getBlock()
                                    instanceof StageBlock block
                            && block.kind == StageBlock.Kind.STEREO) {
                        mc.setScreen(new StereoScreen(p));
                    } else mc.setScreen(new DeskScreen(p));
                };
        StageNetwork.openFixtureClient =
                p -> Minecraft.getInstance().setScreen(new FixtureControlScreen(p));
        NeoForge.EVENT_BUS.addListener(SoundstageClient::tick);
        NeoForge.EVENT_BUS.addListener(SoundstageClient::render);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
        NeoForge.EVENT_BUS.addListener(SoundstageClient::registerCommands);
    }

    private static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher()
                .register(
                        Commands.literal("soundstagesmoothing")
                                .then(
                                        Commands.argument(
                                                        "milliseconds",
                                                        IntegerArgumentType.integer(0, 2000))
                                                .executes(
                                                        c -> {
                                                            smoothingMillis =
                                                                    IntegerArgumentType.getInteger(
                                                                            c, "milliseconds");
                                                            savePrefs();
                                                            return 1;
                                                        })));
        event.getDispatcher()
                .register(
                        Commands.literal("soundstagebrightness")
                                .then(
                                        Commands.argument(
                                                        "percent",
                                                        IntegerArgumentType.integer(0, 100))
                                                .executes(
                                                        c -> {
                                                            brightness =
                                                                    IntegerArgumentType.getInteger(
                                                                            c, "percent");
                                                            savePrefs();
                                                            return 1;
                                                        })));
        event.getDispatcher()
                .register(
                        Commands.literal("soundstagevolume")
                                .then(
                                        Commands.argument(
                                                        "percent",
                                                        IntegerArgumentType.integer(0, 100))
                                                .executes(
                                                        c -> {
                                                            personalVolume =
                                                                    IntegerArgumentType.getInteger(
                                                                            c, "percent");
                                                            savePrefs();
                                                            return 1;
                                                        })));
        event.getDispatcher()
                .register(
                        Commands.literal("soundstagemute")
                                .executes(
                                        c -> {
                                            mute = !mute;
                                            savePrefs();
                                            c.getSource()
                                                    .sendSuccess(
                                                            () ->
                                                                    Component.literal(
                                                                            mute
                                                                                    ? "Soundstage"
                                                                                          + " muted"
                                                                                    : "Soundstage"
                                                                                          + " unmuted"),
                                                            false);
                                            return 1;
                                        }));
        event.getDispatcher()
                .register(
                        Commands.literal("soundstagelights")
                                .executes(
                                        c -> {
                                            lights = !lights;
                                            savePrefs();
                                            restoreLights();
                                            c.getSource()
                                                    .sendSuccess(
                                                            () ->
                                                                    Component.literal(
                                                                            lights
                                                                                    ? "Soundstage"
                                                                                          + " lighting"
                                                                                          + " enabled"
                                                                                    : "Soundstage"
                                                                                          + " lighting"
                                                                                          + " disabled"),
                                                            false);
                                            return 1;
                                        }));
        event.getDispatcher()
                .register(
                        Commands.literal("soundstagestatus")
                                .executes(c -> reportStatus(c.getSource())));
    }

    private static int reportStatus(CommandSourceStack source) {
        var mc = Minecraft.getInstance();
        source.sendSuccess(
                () ->
                        Component.literal(
                                "Lighting "
                                        + lights
                                        + ", brightness "
                                        + brightness
                                        + "%, particles "
                                        + mc.options.particles().get()
                                        + ", mist "
                                        + (AtmosphereBridge.volumetric
                                                ? "volumetric"
                                                : "particle fallback")),
                false);
        for (var desk : active) {
            source.sendSuccess(
                    () ->
                            Component.literal(
                                    "Desk "
                                            + desk.getBlockPos().toShortString()
                                            + ": "
                                            + status(desk.getBlockPos())),
                    false);
            for (var pos : desk.lights.keySet())
                if (FixtureProfile.at(level, pos) != FixtureProfile.RGB)
                    source.sendSuccess(
                            () ->
                                    Component.literal(
                                            pos.toShortString()
                                                    + " "
                                                    + PerformanceEffects.status(pos)),
                            false);
        }
        return 1;
    }

    private static void loadPrefs() {
        try (var in = Files.newInputStream(PREFS)) {
            var p = new Properties();
            p.load(in);
            personalVolume = percent(p, "volume");
            mute = Boolean.parseBoolean(p.getProperty("mute", "false"));
            lights = Boolean.parseBoolean(p.getProperty("lights", "true"));
            smoothingMillis =
                    Math.clamp(Integer.parseInt(p.getProperty("smoothingMillis", "0")), 0, 2000);
            brightness = percent(p, "brightness");
            particleDensity = percent(p, "particleDensity");
            fogDensity =
                    FogDensityPreference.load(
                            Integer.parseInt(p.getProperty("fogDensity", "100")),
                            Integer.parseInt(p.getProperty("fogDensityScaleVersion", "1")));
            showFog = Boolean.parseBoolean(p.getProperty("fog", "true"));
            showLasers = Boolean.parseBoolean(p.getProperty("lasers", "true"));
            laserSmoothing = Boolean.parseBoolean(p.getProperty("laserSmoothing", "true"));
        } catch (Exception ignored) {
        }
    }

    private static int percent(Properties p, String key) {
        return Math.clamp(Integer.parseInt(p.getProperty(key, "100")), 0, 100);
    }

    static void savePrefs() {
        var p = new Properties();
        p.setProperty("volume", "" + personalVolume);
        p.setProperty("mute", "" + mute);
        p.setProperty("lights", "" + lights);
        p.setProperty("smoothingMillis", "" + smoothingMillis);
        p.setProperty("brightness", "" + brightness);
        p.setProperty("particleDensity", "" + particleDensity);
        p.setProperty("fogDensity", "" + fogDensity);
        p.setProperty("fogDensityScaleVersion", "" + FogDensityPreference.SCALE_VERSION);
        p.setProperty("fog", "" + showFog);
        p.setProperty("lasers", "" + showLasers);
        p.setProperty("laserSmoothing", "" + laserSmoothing);
        try {
            Files.createDirectories(PREFS.getParent());
            try (var out = Files.newOutputStream(PREFS)) {
                p.store(out, "Soundstage listener settings");
            }
        } catch (java.io.IOException ignored) {
        }
    }

    static void lightingPreference(boolean enabled) {
        lights = enabled;
        if (!enabled) restoreLights();
    }

    static void fogPreference(boolean enabled) {
        showFog = enabled;
    }

    static void laserSmoothingPreference(boolean enabled) {
        laserSmoothing = enabled;
        LASER_TRACKING.clear();
    }

    private static void clear() {
        SESSIONS.values().forEach(Session::close);
        SESSIONS.clear();
        active = List.of();
        restoreLights();
        LOADED.clear();
        level = null;
    }

    private static double distance(StageEntity d, Vec3 listener) {
        double best = Vec3.atCenterOf(d.getBlockPos()).distanceTo(listener);
        for (var p : d.speakers)
            if (level.hasChunkAt(p)
                    && level.getBlockEntity(p) instanceof StageEntity s
                    && d.id.equals(s.deskId))
                best = Math.min(best, Vec3.atCenterOf(p).distanceTo(listener));
        // Keep the show active near linked fixtures even outside audible speaker coverage.
        return best;
    }

    private static double proximity(StageEntity d, Vec3 listener) {
        double best = distance(d, listener);
        if (lights && d.lighting)
            for (var p : d.lights.keySet())
                best = Math.min(best, Vec3.atCenterOf(p).distanceTo(listener));
        return best;
    }

    private static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != mc.level) {
            var pending = LOADED.stream().filter(e -> e.getLevel() == mc.level).toList();
            clear();
            level = mc.level;
            LOADED.addAll(pending);
        }
        if (level == null || mc.player == null) return;
        LOADED.removeIf(e -> e.isRemoved() || e.getLevel() != level);
        PerformanceEffects.particles();
        for (var d : active) {
            var session = SESSIONS.get(d.id);
            if (session != null && session.player != null)
                session.player.scene(audioScene(d, mc.player.getEyePosition()));
        }
        if (++tick % 5 != 0) return;
        var listener = mc.player.getEyePosition();
        long now = System.nanoTime();
        var selected =
                LOADED.stream()
                        .filter(
                                e ->
                                        e.isSource()
                                                && e.enabled
                                                && !e.effectiveUrl().isBlank()
                                                && proximity(e, listener) < e.range + 4)
                        .sorted(
                                Comparator.<StageEntity>comparingDouble(e -> proximity(e, listener))
                                        .thenComparingLong(e -> e.getBlockPos().asLong()))
                        .limit(4)
                        .toList();
        var wanted = new HashSet<UUID>();
        selected.forEach(d -> wanted.add(d.id));
        SESSIONS.entrySet()
                .removeIf(
                        e -> {
                            if (wanted.contains(e.getKey())) return false;
                            e.getValue().close();
                            return true;
                        });
        for (var d : selected) {
            var s = SESSIONS.computeIfAbsent(d.id, k -> new Session());
            String key = d.effectiveUrl() + "|" + (d.playlistMode ? d.trackStartedTick : 0);
            if (!key.equals(s.playbackKey) || d.lighting != s.lighting) {
                s.close();
                s.url = d.effectiveUrl();
                s.playbackKey = key;
                s.lighting = d.lighting;
                s.retry = 0;
                s.failures = 0;
            }
            if (s.player == null && now >= s.retry) {
                if (!PlayerAPI.isReady()) {
                    s.status = "VLC backend unavailable";
                    continue;
                }
                try {
                    s.player = new VenuePlayer(d.lighting);
                    s.player.setVolume(0);
                    s.player.start(
                            URI.create(s.url),
                            d.playlistMode
                                    ? new String[] {":start-time=" + (d.desiredMillis() / 1000.0)}
                                    : new String[0]);
                    s.started = now;
                    s.sync.reset(now);
                    s.status = "Connecting";
                } catch (RuntimeException ex) {
                    s.close();
                    s.retry = now + 10_000_000_000L;
                    s.status = "Unable to open stream";
                }
            }
            if (s.player != null) {
                s.status = s.player.getStateName();
                int v =
                        mute
                                ? 0
                                : (int)
                                        Math.round(
                                                d.volume
                                                        * personalVolume
                                                        / 100.0
                                                        * StreamPolicy.gain(
                                                                distance(d, listener), d.range));
                var acoustic = acoustics(d, listener, mc.player.getYRot());
                s.player.spatial(v / 100.0, d.spatial ? acoustic[0] : 0, d.walls ? acoustic[1] : 0);
                s.player.scene(audioScene(d, listener));
                if (d.playlistMode && s.player.isPlaying()) {
                    long desired = d.desiredMillis(), observed = s.player.getTime();
                    if (s.sync.correctionNeeded(now, desired, observed)) {
                        System.getLogger(SoundstageClient.class.getName())
                                .log(
                                        System.Logger.Level.INFO,
                                        "Soundstage correcting sustained playlist drift: "
                                                + (desired - observed)
                                                + "ms");
                        s.player.seekTo(desired);
                    }
                }
                String state = s.status.toLowerCase(Locale.ROOT);
                if (now - s.started > 15_000_000_000L
                        && (state.contains("error")
                                || state.contains("ended")
                                || state.contains("stopped")
                                || state.contains("nothing"))) {
                    s.close();
                    s.failures = Math.min(5, s.failures + 1);
                    s.retry = now + Math.min(30, 2L << s.failures) * 1_000_000_000L;
                    s.status = "Reconnecting";
                }
            }
        }
        active = selected;
    }

    private static OpenAlOutput.Scene audioScene(StageEntity d, Vec3 listener) {
        var mc = Minecraft.getInstance();
        double master = mute ? 0 : d.volume * personalVolume / 10000.0;
        var points =
                d.speakers.stream()
                        .filter(
                                p ->
                                        level.hasChunkAt(p)
                                                && level.getBlockEntity(p) instanceof StageEntity e
                                                && d.id.equals(e.deskId))
                        .sorted(
                                Comparator.comparingDouble(
                                        p -> Vec3.atCenterOf(p).distanceToSqr(listener)))
                        .limit(8)
                        .toList();
        if (points.isEmpty()) points = List.of(d.getBlockPos());
        var forward = mc.player.getLookAngle();
        double yaw = Math.toRadians(mc.player.getYRot());
        var right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        var up = right.cross(forward).normalize();
        var emitters = new ArrayList<OpenAlOutput.Emitter>();
        double total = 0;
        for (var p : points) {
            double g = StreamPolicy.gain(Vec3.atCenterOf(p).distanceTo(listener), d.range);
            total += g;
        }
        for (var p : points) {
            var v = Vec3.atCenterOf(p).subtract(listener);
            double g = StreamPolicy.gain(v.length(), d.range);
            float blocked = 0;
            if (d.walls) {
                int hits = 0;
                for (double offset : new double[] {-.25, 0, .25}) {
                    var hit =
                            level.clip(
                                    new net.minecraft.world.level.ClipContext(
                                            listener,
                                            Vec3.atCenterOf(p).add(offset, .15, 0),
                                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                            net.minecraft.world.level.ClipContext.Fluid.NONE,
                                            mc.player));
                    if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(p))
                        hits++;
                }
                blocked = hits / 3f;
            }
            emitters.add(
                    new OpenAlOutput.Emitter(
                            (float) v.dot(right),
                            (float) v.dot(up),
                            (float) -v.dot(forward),
                            (float) (master * g / Math.max(1, total)),
                            blocked));
        }
        if (!d.spatial) {
            var a = acoustics(d, listener, mc.player.getYRot());
            return new OpenAlOutput.Scene(
                    false,
                    List.of(
                            new OpenAlOutput.Emitter(
                                    0,
                                    0,
                                    -1,
                                    (float)
                                            (master
                                                    * StreamPolicy.gain(
                                                            distance(d, listener), d.range)),
                                    d.walls ? (float) a[1] : 0)));
        }
        return new OpenAlOutput.Scene(true, emitters);
    }

    private static double[] acoustics(StageEntity d, Vec3 listener, float yaw) {
        var points = new ArrayList<BlockPos>();
        points.add(d.getBlockPos());
        for (var p : d.speakers)
            if (level.hasChunkAt(p)
                    && level.getBlockEntity(p) instanceof StageEntity e
                    && d.id.equals(e.deskId)) points.add(p);
        double radians = Math.toRadians(yaw),
                rightX = -Math.cos(radians),
                rightZ = -Math.sin(radians),
                weight = 0,
                pan = 0,
                blocked = 0;
        for (var p : points) {
            var vector = Vec3.atCenterOf(p).subtract(listener);
            double gain = StreamPolicy.gain(vector.length(), d.range);
            if (gain <= 0) continue;
            double w = gain * gain;
            weight += w;
            double horizontal = Math.hypot(vector.x, vector.z);
            if (horizontal > .01) pan += w * (vector.x * rightX + vector.z * rightZ) / horizontal;
            if (d.walls) {
                var hit =
                        level.clip(
                                new net.minecraft.world.level.ClipContext(
                                        listener,
                                        Vec3.atCenterOf(p),
                                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                                        net.minecraft.world.level.ClipContext.Fluid.NONE,
                                        Minecraft.getInstance().player));
                if (hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(p))
                    blocked += w;
            }
        }
        return weight == 0 ? new double[] {0, 0} : new double[] {pan / weight, blocked / weight};
    }

    private static void updateLights(List<StageEntity> selected) {
        COLORS.clear();
        STAGE_RADIANCE.clear();
        PerformanceEffects.cues.clear();
        var applied = new HashSet<BlockPos>();
        var manualPositions = new HashSet<BlockPos>();
        long now = System.nanoTime();
        double dt = lastLightFrame == 0 ? 16 : (now - lastLightFrame) / 1_000_000.0;
        lastLightFrame = now;
        boolean updateAfterlight = System.nanoTime() - lastAfterlight > 100_000_000L;
        if (updateAfterlight) lastAfterlight = System.nanoTime();
        if (lights)
            for (var fixture :
                    LOADED.stream()
                            .filter(
                                    e ->
                                            !e.isRemoved()
                                                    && e.getLevel() == level
                                                    && e.isFixture()
                                                    && e.manualSettings().override()
                                                    && level.hasChunkAt(e.getBlockPos())
                                                    && e.getBlockPos()
                                                                    .distToCenterSqr(
                                                                            Minecraft.getInstance()
                                                                                    .player
                                                                                    .position())
                                                            <= 48 * 48)
                            .sorted(
                                    Comparator.comparingDouble(
                                            e ->
                                                    e.getBlockPos()
                                                            .distToCenterSqr(
                                                                    Minecraft.getInstance()
                                                                            .player
                                                                            .position())))
                            .limit(64)
                            .toList()) {
                var pos = fixture.getBlockPos();
                var profile = FixtureProfile.at(level, pos);
                int address = fixture.address;
                if (fixture.desk != null
                        && level.hasChunkAt(fixture.desk)
                        && level.getBlockEntity(fixture.desk) instanceof StageEntity desk
                        && desk.id.equals(fixture.deskId))
                    address = desk.lights.getOrDefault(pos, address);
                if (!profile.fits(address)) continue;
                var settings = fixture.manualSettings();
                var frame =
                        MANUAL_INPUTS
                                .computeIfAbsent(pos, p -> new ManualFixtureInputs())
                                .frame(settings, profile, address, now);
                var values = frame.channels();
                manualPositions.add(pos);
                applied.add(pos);
                if (profile == FixtureProfile.RGB) {
                    boolean off = values[address - 1] == 0;
                    if (off) {
                        SMOOTHED.remove(pos);
                        STAGE_SMOOTHED.remove(pos);
                    }
                    int color = FixtureProfile.color(values, address);
                    int blended =
                            off
                                    ? 0
                                    : SMOOTHED.computeIfAbsent(pos, p -> new LightSmoother())
                                            .advance(color, dt, smoothingMillis);
                    color = 0;
                    for (int shift : new int[] {16, 8, 0})
                        color |= (((blended >> shift) & 255) * brightness / 100) << shift;
                    COLORS.put(pos, color);
                    var target = StageLightPhysics.fromDmx(values, address);
                    var radiance =
                            off
                                    ? StageLightPhysics.Radiance.BLACK
                                    : STAGE_SMOOTHED
                                            .computeIfAbsent(
                                                    pos, p -> new StageLightPhysics.Smoother())
                                            .advance(target, dt, smoothingMillis);
                    STAGE_RADIANCE.put(pos, radiance.scale(brightness / 100.0));
                } else {
                    var aim =
                            profile == FixtureProfile.LASER
                                    ? laserAim(
                                            pos, fixture.id, null, 0, address, frame, values, now)
                                    : null;
                    PerformanceEffects.cues.put(
                            pos, new PerformanceEffects.Cue(profile, values, address, aim));
                }
            }
        if (lights)
            for (var d : selected) {
                var s = SESSIONS.get(d.id);
                if (!d.lighting) continue;
                // Every fixture in this render frame reads the same published universe.
                var frame =
                        s != null && s.player != null && d.enabled
                                ? s.player.lightingFrame()
                                : null;
                var values = frame == null ? null : frame.effects(now);
                var rgbValues = frame == null ? null : frame.rgb(now);
                for (var patch : d.lights.entrySet()) {
                    var pos = patch.getKey();
                    if (!level.hasChunkAt(pos) || applied.contains(pos)) continue;
                    if (level.getBlockEntity(pos) instanceof StageEntity fixture
                            && fixture.manualSettings().override()) continue;
                    var profile = FixtureProfile.at(level, pos);
                    if (profile != FixtureProfile.RGB) {
                        if (!(level.getBlockEntity(pos) instanceof StageEntity equipment)
                                || !d.id.equals(equipment.deskId)) continue;
                        if (values != null && profile.fits(patch.getValue())) {
                            LaserMotion.Direction aim = null;
                            if (profile == FixtureProfile.LASER)
                                aim =
                                        laserAim(
                                                pos,
                                                d.id,
                                                s.player,
                                                d.trackStartedTick,
                                                patch.getValue(),
                                                frame,
                                                values,
                                                now);
                            PerformanceEffects.cues.put(
                                    pos,
                                    new PerformanceEffects.Cue(
                                            profile, values, patch.getValue(), aim));
                            applied.add(pos);
                        }
                        continue;
                    }
                    int color =
                            rgbValues != null
                                    ? FixtureProfile.color(rgbValues, patch.getValue())
                                    : -1;
                    if (color < 0)
                        color =
                                d.fallbackMode == 1
                                        ? 0
                                        : d.fallbackMode == 2 ? d.fallbackColor : -1;
                    if (color < 0) {
                        SMOOTHED.remove(pos);
                        continue;
                    }
                    int blended =
                            SMOOTHED.computeIfAbsent(pos, p -> new LightSmoother())
                                    .advance(color, dt, smoothingMillis);
                    color = 0;
                    for (int shift : new int[] {16, 8, 0})
                        color |= (((blended >> shift) & 255) * brightness / 100) << shift;
                    var entity = level.getBlockEntity(pos);
                    if (entity instanceof StageEntity equipment) {
                        if (!d.id.equals(equipment.deskId)) continue;
                        COLORS.put(pos, color);
                        applied.add(pos);
                        // DMX dimmer and listener brightness scale LINEAR energy.
                        // Decoding the already dimmed display RGB would raise the
                        // dimmer to gamma as well, suppressing quiet cues severely.
                        var target =
                                rgbValues != null
                                        ? StageLightPhysics.fromDmx(rgbValues, patch.getValue())
                                        : StageLightPhysics.fromDisplayRgb(
                                                d.fallbackMode == 2 ? d.fallbackColor : 0);
                        var radiance =
                                STAGE_SMOOTHED
                                        .computeIfAbsent(pos, p -> new StageLightPhysics.Smoother())
                                        .advance(target, dt, smoothingMillis)
                                        .scale(brightness / 100.0);
                        STAGE_RADIANCE.put(pos, radiance);
                    } else if (entity != null) {
                        if (updateAfterlight && applyAfterlight(entity, color))
                            OVERRIDDEN.put(pos, entity);
                        if (OVERRIDDEN.get(pos) == entity) applied.add(pos);
                    }
                }
            }
        SMOOTHED.keySet().retainAll(applied);
        STAGE_SMOOTHED.keySet().retainAll(STAGE_RADIANCE.keySet());
        MANUAL_INPUTS.keySet().retainAll(manualPositions);
        LASER_TRACKING
                .keySet()
                .removeIf(
                        pos -> {
                            var cue = PerformanceEffects.cues.get(pos);
                            return cue == null || cue.profile() != FixtureProfile.LASER;
                        });
        OVERRIDDEN
                .entrySet()
                .removeIf(
                        e -> {
                            if (applied.contains(e.getKey())
                                    && level.getBlockEntity(e.getKey()) == e.getValue())
                                return false;
                            applyAfterlight(e.getValue(), null);
                            return true;
                        });
    }

    private static LaserMotion.Direction laserAim(
            BlockPos pos,
            UUID venue,
            VenuePlayer player,
            long startTick,
            int address,
            LightingFrame frame,
            int[] values,
            long now) {
        int i = address - 1;
        var direction = level.getBlockState(pos).getValue(StageBlock.FACING);
        int facing = direction.get2DDataValue();
        var tracking = LASER_TRACKING.get(pos);
        if (tracking == null
                || !tracking.venue.equals(venue)
                || tracking.player != player
                || tracking.startTick != startTick
                || tracking.address != address
                || tracking.facing != facing) {
            tracking =
                    new LaserTracking(venue, player, startTick, address, facing, new LaserMotion());
            LASER_TRACKING.put(pos, tracking);
        }
        boolean visible =
                showLasers
                        && brightness > 0
                        && values[i] > 0
                        && (values[i + 1] | values[i + 2] | values[i + 3]) != 0;
        return tracking.motion
                .advance(values[i + 4], values[i + 5], frame.nanos(), now, laserSmoothing, visible)
                .direction(direction.toYRot());
    }

    private static final Map<Class<?>, Optional<java.lang.reflect.Method>> HOOKS = new HashMap<>();

    private static boolean applyAfterlight(
            net.minecraft.world.level.block.entity.BlockEntity e, Integer color) {
        var method =
                HOOKS.computeIfAbsent(
                        e.getClass(),
                        c -> {
                            try {
                                return Optional.of(c.getMethod("setLiveColor", Integer.class));
                            } catch (NoSuchMethodException ex) {
                                return Optional.empty();
                            }
                        });
        if (method.isEmpty()) return false;
        try {
            method.get().invoke(e, color);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static void restoreLights() {
        OVERRIDDEN.values().forEach(e -> applyAfterlight(e, null));
        OVERRIDDEN.clear();
        COLORS.clear();
        SMOOTHED.clear();
        STAGE_RADIANCE.clear();
        STAGE_SMOOTHED.clear();
        LASER_TRACKING.clear();
        MANUAL_INPUTS.clear();
        PerformanceEffects.cues.clear();
        AtmosphereBridge.clear();
    }

    static void retry(BlockPos p) {
        if (level == null || !(level.getBlockEntity(p) instanceof StageEntity d)) return;
        var session = SESSIONS.get(d.id);
        if (session == null) return;
        session.close();
        session.retry = 0;
        session.failures = 0;
        session.status = "Connecting";
    }

    static String status(BlockPos p) {
        if (level == null || !(level.getBlockEntity(p) instanceof StageEntity d))
            return "Unavailable";
        var s = SESSIONS.get(d.id);
        return s == null
                ? (d.enabled ? "Outside playback range" : "Stopped")
                : s.status + " / " + (s.player == null ? "" : s.player.audioBackend());
    }

    private static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var mc = Minecraft.getInstance();
        if (level == null || mc.player == null) return;
        var fixtures =
                LOADED.stream()
                        .filter(
                                d ->
                                        d.isSource()
                                                && proximity(d, mc.player.getEyePosition())
                                                        < d.range + 4)
                        .sorted(
                                Comparator.<StageEntity>comparingDouble(
                                                d -> proximity(d, mc.player.getEyePosition()))
                                        .thenComparingLong(d -> d.getBlockPos().asLong()))
                        .limit(4)
                        .toList();
        updateLights(fixtures);
        AtmosphereBridge.update(STAGE_RADIANCE);
        PerformanceEffects.render(e);
        if (COLORS.isEmpty()) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        COLORS.forEach(
                (pos, rgb) -> {
                    var p = Vec3.atLowerCornerOf(pos).subtract(e.getCamera().getPosition());
                    var facing = level.getBlockState(pos).getValue(StageBlock.FACING);
                    AABB lens =
                            switch (facing) {
                                case SOUTH ->
                                        new AABB(
                                                p.x + .1875,
                                                p.y + .3125,
                                                p.z + .984,
                                                p.x + .8125,
                                                p.y + .8125,
                                                p.z + .986);
                                case EAST ->
                                        new AABB(
                                                p.x + .984,
                                                p.y + .3125,
                                                p.z + .1875,
                                                p.x + .986,
                                                p.y + .8125,
                                                p.z + .8125);
                                case WEST ->
                                        new AABB(
                                                p.x + .014,
                                                p.y + .3125,
                                                p.z + .1875,
                                                p.x + .016,
                                                p.y + .8125,
                                                p.z + .8125);
                                default ->
                                        new AABB(
                                                p.x + .1875,
                                                p.y + .3125,
                                                p.z + .014,
                                                p.x + .8125,
                                                p.y + .8125,
                                                p.z + .016);
                            };
                    EmissiveGeometry.box(
                            buffers.getBuffer(EmissiveGeometry.type()),
                            e.getPoseStack().last(),
                            lens,
                            rgb);
                });
        buffers.endBatch(EmissiveGeometry.type());
    }
}
