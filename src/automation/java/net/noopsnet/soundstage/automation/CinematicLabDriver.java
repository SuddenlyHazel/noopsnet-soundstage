// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.automation;

import com.google.gson.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Disposable offline development lab only; excluded from every distributed JAR. */
@Mod(value = "noopsnet_soundstage", dist = Dist.CLIENT)
public final class CinematicLabDriver {
    private static final String WORLD = "CinematicLab";
    private static final String NATURAL_WORLD = "CinematicLabNatural";
    private static final Set<String> WORLDS = Set.of(WORLD, NATURAL_WORLD);
    private Path lab;
    private int ticks;
    private String error = "", lastId = "", capture;
    private String openingWorld = "";
    private long frame, previousFrame, captureUntil;
    private String captureName;
    private JsonObject lightingCounters;
    private String lightingCountersRequest = "";
    private final ArrayList<String> frameRows = new ArrayList<>();
    private CinematicLabMotion motion;
    private StatePublisher publisher;

    public CinematicLabDriver() {
        var mc = Minecraft.getInstance();
        Path game = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        // Neither a Prism path nor an arbitrary existing world can enable this.
        if (!game.endsWith(Path.of("noopsnet-soundstage/run/cinematic-lab"))) return;
        if (!Files.isRegularFile(game.resolve("cinematic-lab.marker"))) return;
        lab = game.resolve("lab");
        try {
            Files.createDirectories(lab);
        } catch (java.io.IOException e) {
            System.getLogger(CinematicLabDriver.class.getName())
                    .log(System.Logger.Level.ERROR, "Cannot prepare disposable cinematic lab", e);
            return;
        }
        publisher = new StatePublisher(lab);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
        NeoForge.EVENT_BUS.addListener(this::beforeFrame);
    }

    private void localWorld(Minecraft mc) {
        if (!mc.hasSingleplayerServer() || mc.player == null)
            throw new IllegalStateException("Lab singleplayer world required");
        Path actual =
                mc.getSingleplayerServer()
                        .getWorldPath(LevelResource.ROOT)
                        .toAbsolutePath()
                        .normalize();
        Path saves = mc.gameDirectory.toPath().resolve("saves").toAbsolutePath().normalize();
        if (!actual.getParent().equals(saves)
                || !WORLDS.contains(actual.getFileName().toString())
                || Files.isSymbolicLink(actual))
            throw new IllegalStateException(
                    "Refusing a world outside the two disposable Cinematic lab saves");
    }

    private static String safeName(String s) {
        if (!s.matches("[a-zA-Z0-9_.-]{1,100}"))
            throw new IllegalArgumentException("Unsafe artifact name");
        return s;
    }

    private void tick(ClientTickEvent.Post event) {
        if (++ticks % 5 != 0) return;
        var mc = Minecraft.getInstance();
        var profiler = mc.getProfiler();
        profiler.push("cinematic_lab_control");
        try {
            String publicationError = publisher.takeFailure();
            if (publicationError != null) {
                error = publicationError;
                publisher.publishError(error);
            }
            mc.options.pauseOnLostFocus = false;
            Path request = lab.resolve("request.json");
            if (Files.exists(request)) {
                var r = JsonParser.parseString(Files.readString(request)).getAsJsonObject();
                Files.delete(request);
                error = "";
                lastId = r.has("id") ? r.get("id").getAsString() : "";
                switch (r.get("action").getAsString()) {
                    case "create_flat", "create_natural" -> {
                        boolean flat = r.get("action").getAsString().equals("create_flat");
                        String world = flat ? WORLD : NATURAL_WORLD;
                        if (mc.level != null
                                || Files.exists(
                                        mc.gameDirectory.toPath().resolve("saves").resolve(world)))
                            throw new IllegalStateException("World already exists");
                        var rules = new GameRules();
                        var settings =
                                new LevelSettings(
                                        world,
                                        GameType.CREATIVE,
                                        false,
                                        Difficulty.PEACEFUL,
                                        true,
                                        rules,
                                        WorldDataConfiguration.DEFAULT);
                        mc.createWorldOpenFlows()
                                .createFreshLevel(
                                        world,
                                        settings,
                                        new WorldOptions(20261112L, !flat, false),
                                        registries ->
                                                registries
                                                        .registryOrThrow(Registries.WORLD_PRESET)
                                                        .getHolderOrThrow(
                                                                flat
                                                                        ? WorldPresets.FLAT
                                                                        : WorldPresets.NORMAL)
                                                        .value()
                                                        .createWorldDimensions(),
                                        new TitleScreen());
                    }
                    case "open" -> {
                        if (mc.level != null)
                            throw new IllegalStateException("Close the active lab world first");
                        String world = r.has("world") ? r.get("world").getAsString() : WORLD;
                        if (!WORLDS.contains(world))
                            throw new IllegalArgumentException("Unknown lab world");
                        Path save = mc.gameDirectory.toPath().resolve("saves").resolve(world);
                        if (!Files.isDirectory(save) || Files.isSymbolicLink(save))
                            throw new IllegalStateException("Expected a real disposable lab save");
                        openingWorld = world;
                        mc.createWorldOpenFlows().openWorld(world, () -> {});
                    }
                    case "confirm_backup" -> {
                        String world = r.get("world").getAsString();
                        if (!WORLDS.contains(world)
                                || !world.equals(openingWorld)
                                || mc.level != null
                                || !(mc.screen
                                        instanceof
                                        net.minecraft.client.gui.screens.BackupConfirmScreen))
                            throw new IllegalStateException(
                                    "Expected the requested disposable world's backup prompt");
                        // Select only vanilla's backup-and-proceed action. Never
                        // skip the backup or generalize this to arbitrary UI.
                        var buttons =
                                mc.screen.children().stream()
                                        .filter(
                                                net.minecraft.client.gui.components.Button.class
                                                        ::isInstance)
                                        .map(net.minecraft.client.gui.components.Button.class::cast)
                                        .filter(
                                                button ->
                                                        button.getMessage().getContents()
                                                                        instanceof
                                                                        net.minecraft.network.chat
                                                                                        .contents
                                                                                        .TranslatableContents
                                                                                text
                                                                && text.getKey()
                                                                        .equals(
                                                                                "selectWorld.backupJoinConfirmButton"))
                                        .toList();
                        if (buttons.size() != 1)
                            throw new IllegalStateException("Unknown backup prompt controls");
                        buttons.getFirst().onPress();
                    }
                    case "registry_counts" -> {
                        // Read-only diagnostic, usable before integrated-server
                        // startup. Count existing state lists without expanding
                        // their property transition tables.
                        record Count(String id, int states) {}
                        var counts = new ArrayList<Count>();
                        var namespaces = new TreeMap<String, Long>();
                        long total = 0;
                        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                            var id =
                                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(
                                            block);
                            int size = block.getStateDefinition().getPossibleStates().size();
                            counts.add(new Count(id.toString(), size));
                            total += size;
                            namespaces.merge(id.getNamespace(), (long) size, Long::sum);
                        }
                        counts.sort(
                                Comparator.comparingInt(Count::states)
                                        .reversed()
                                        .thenComparing(Count::id));
                        var result = new JsonObject();
                        result.addProperty("request_id", lastId);
                        result.addProperty("blocks", counts.size());
                        result.addProperty("possible_states", total);
                        var top = new JsonArray();
                        for (var count : counts.subList(0, Math.min(30, counts.size()))) {
                            var row = new JsonObject();
                            row.addProperty("block", count.id());
                            row.addProperty("states", count.states());
                            top.add(row);
                        }
                        result.add("top_blocks", top);
                        var byNamespace = new JsonObject();
                        namespaces.forEach(byNamespace::addProperty);
                        result.add("states_by_namespace", byNamespace);
                        Files.writeString(lab.resolve("registry-counts.json"), result.toString());
                    }
                    case "close" -> {
                        localWorld(mc);
                        motion = null;
                        // Vanilla's pause-menu path disconnects the level first;
                        // Minecraft.disconnect then waits for the integrated
                        // server to save/stop. Omitting this step waits forever.
                        mc.level.disconnect();
                        mc.disconnect(new TitleScreen());
                    }
                    case "commands" -> {
                        localWorld(mc);
                        var server = mc.getSingleplayerServer();
                        var commands = new ArrayList<String>();
                        for (var c : r.getAsJsonArray("commands")) commands.add(c.getAsString());
                        server.execute(
                                () -> {
                                    var source =
                                            server.createCommandSourceStack()
                                                    .withSuppressedOutput();
                                    for (var c : commands)
                                        server.getCommands().performPrefixedCommand(source, c);
                                });
                    }
                    case "capture" -> {
                        localWorld(mc);
                        capture = safeName(r.get("name").getAsString()) + ".png";
                    }
                    case "motion" -> {
                        localWorld(mc);
                        if (motion != null && !motion.done())
                            throw new IllegalStateException("Motion is still active");
                        motion = new CinematicLabMotion(lab, r);
                    }
                    case "terrain" -> {
                        localWorld(mc);
                        var server = mc.getSingleplayerServer();
                        var points = r.getAsJsonArray("points").deepCopy();
                        if (points.size() > 64)
                            throw new IllegalArgumentException("Too many terrain samples");
                        String id = lastId;
                        server.execute(
                                () -> {
                                    try {
                                        var result = new JsonObject();
                                        result.addProperty("request_id", id);
                                        var samples = new JsonArray();
                                        var level = server.overworld();
                                        for (var point : points) {
                                            var p = point.getAsJsonArray();
                                            int x = p.get(0).getAsInt(), z = p.get(1).getAsInt();
                                            if (Math.abs((long) x) > 1000000
                                                    || Math.abs((long) z) > 1000000)
                                                throw new IllegalArgumentException(
                                                        "Terrain coordinate out of bounds");
                                            int y =
                                                    level.getHeight(
                                                            Heightmap.Types
                                                                    .MOTION_BLOCKING_NO_LEAVES,
                                                            x,
                                                            z);
                                            var at = new BlockPos(x, y - 1, z);
                                            var sample = new JsonObject();
                                            sample.addProperty("x", x);
                                            sample.addProperty("y", y);
                                            sample.addProperty("z", z);
                                            sample.addProperty(
                                                    "biome",
                                                    level.getBiome(at)
                                                            .unwrapKey()
                                                            .orElseThrow()
                                                            .location()
                                                            .toString());
                                            sample.addProperty(
                                                    "block", level.getBlockState(at).toString());
                                            sample.addProperty(
                                                    "fluid",
                                                    level.getFluidState(at).isEmpty()
                                                            ? "none"
                                                            : level.getFluidState(at)
                                                                    .getType()
                                                                    .toString());
                                            samples.add(sample);
                                        }
                                        result.add("samples", samples);
                                        Files.writeString(
                                                lab.resolve("terrain.json"), result.toString());
                                    } catch (Exception e) {
                                        try {
                                            Files.writeString(
                                                    lab.resolve("terrain-error.txt"), e.toString());
                                        } catch (Exception ignored) {
                                        }
                                    }
                                });
                    }
                    case "metrics" -> {
                        localWorld(mc);
                        if (!mc.debugClientMetricsStart(message -> {}))
                            throw new IllegalStateException("Metrics capture could not start");
                    }
                    case "options" -> {
                        int fps = r.has("fps") ? r.get("fps").getAsInt() : 60;
                        if (fps < 30 || fps > 260)
                            throw new IllegalArgumentException("Invalid lab FPS cap");
                        mc.options.framerateLimit().set(fps);
                        mc.options.enableVsync().set(false);
                        mc.options.bobView().set(false);
                        mc.options.hideGui = true;
                        mc.options.save();
                    }
                    case "resolution" -> {
                        int w = r.get("width").getAsInt(), h = r.get("height").getAsInt();
                        if (w < 640 || w > 3840 || h < 360 || h > 2160)
                            throw new IllegalArgumentException("Invalid lab resolution");
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), w, h);
                    }
                    case "frames" -> {
                        localWorld(mc);
                        int seconds = r.get("seconds").getAsInt();
                        if (seconds < 1 || seconds > 120)
                            throw new IllegalArgumentException("Capture must be 1..120 seconds");
                        captureName = safeName(r.get("name").getAsString());
                        frameRows.clear();
                        frameRows.add("frame,wall_interval_ns,minecraft_frame_ns");
                        previousFrame = 0;
                        captureUntil = System.nanoTime() + seconds * 1_000_000_000L;
                    }
                    case "shader" -> {
                        String name = safeName(r.get("name").getAsString());
                        if (!Files.isRegularFile(
                                mc.gameDirectory.toPath().resolve("shaderpacks").resolve(name)))
                            throw new IllegalArgumentException("Missing lab shader");
                        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                        Object config = iris.getMethod("getIrisConfig").invoke(null);
                        config.getClass()
                                .getMethod("setShaderPackName", String.class)
                                .invoke(config, name);
                        config.getClass()
                                .getMethod("setShadersEnabled", boolean.class)
                                .invoke(config, true);
                        config.getClass().getMethod("save").invoke(config);
                        iris.getMethod("reload").invoke(null);
                        var failure = (Optional<?>) iris.getMethod("getStoredError").invoke(null);
                        if (failure.isPresent())
                            throw new IllegalStateException(
                                    "Iris rejected lab shader: " + failure.get());
                    }
                    case "profile" -> selectProfile(safeName(r.get("name").getAsString()));
                    case "lighting_counters" -> {
                        localWorld(mc);
                        Class<?> snapshots =
                                Class.forName("net.noopsnet.neon.client.ClientLightingSnapshots");
                        var counters = (Map<?, ?>) snapshots.getMethod("diagnostics").invoke(null);
                        var result = new JsonObject();
                        for (var entry : counters.entrySet()) {
                            Object stats = entry.getValue();
                            var values = new JsonObject();
                            for (String key : List.of("requests", "hits", "rebuilds", "revision"))
                                values.addProperty(
                                        key,
                                        (Number) stats.getClass().getMethod(key).invoke(stats));
                            values.addProperty(
                                    "reusable",
                                    (Boolean) stats.getClass().getMethod("reusable").invoke(stats));
                            result.add(entry.getKey().toString(), values);
                        }
                        lightingCounters = result;
                        lightingCountersRequest = lastId;
                    }
                    case "resume" -> {
                        localWorld(mc);
                        mc.setScreen(null);
                        mc.options.hideGui = true;
                        mc.mouseHandler.releaseMouse();
                    }
                    case "quit" -> mc.stop();
                    default -> throw new IllegalArgumentException("Unknown lab action");
                }
            }
            var state = new JsonObject();
            state.addProperty("request_id", lastId);
            state.addProperty("error", error);
            state.addProperty("opening_world", openingWorld);
            state.addProperty(
                    "screen", mc.screen == null ? "none" : mc.screen.getClass().getName());
            state.addProperty("local_world", mc.hasSingleplayerServer());
            state.addProperty("frame", frame);
            state.addProperty("recording", captureUntil > 0);
            state.addProperty("width", mc.getWindow().getWidth());
            state.addProperty("height", mc.getWindow().getHeight());
            state.addProperty("motion_active", motion != null && !motion.done());
            state.addProperty("motion_name", motion == null ? "" : motion.name());
            state.addProperty("fps_cap", mc.options.framerateLimit().get());
            state.addProperty("vsync", mc.options.enableVsync().get());
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            state.addProperty("shader", (String) iris.getMethod("getCurrentPackName").invoke(null));
            var pack = (Optional<?>) iris.getMethod("getCurrentPack").invoke(null);
            state.addProperty(
                    "shader_profile",
                    pack.isEmpty()
                            ? ""
                            : (String)
                                    pack.get()
                                            .getClass()
                                            .getMethod("getProfileInfo")
                                            .invoke(pack.get()));
            var failure = (Optional<?>) iris.getMethod("getStoredError").invoke(null);
            state.addProperty("shader_error", failure.isEmpty() ? "" : failure.get().toString());
            if (lightingCounters != null) {
                state.add("lighting_counters", lightingCounters);
                state.addProperty("lighting_counters_request_id", lightingCountersRequest);
            }
            if (mc.player != null) {
                state.addProperty("x", mc.player.getX());
                state.addProperty("y", mc.player.getY());
                state.addProperty("z", mc.player.getZ());
                state.addProperty("yaw", mc.player.getYRot());
                state.addProperty("pitch", mc.player.getXRot());
                state.addProperty("dimension", mc.player.level().dimension().location().toString());
                state.addProperty("spectator", mc.player.isSpectator());
            }
            if (mc.level != null) {
                state.addProperty("world_time", mc.level.getDayTime());
                state.addProperty("rain", mc.level.getRainLevel(1f));
            }
            if (mc.hasSingleplayerServer())
                state.addProperty(
                        "world",
                        mc.getSingleplayerServer()
                                .getWorldPath(LevelResource.ROOT)
                                .toAbsolutePath()
                                .normalize()
                                .getFileName()
                                .toString());
            // All game reads and serialization stay on this thread. The worker
            // receives only immutable text and never touches Minecraft objects.
            publisher.publish(state.toString());
        } catch (Exception e) {
            error = e.toString();
            publisher.publishError(error);
        } finally {
            profiler.pop();
        }
    }

    /** Select through pinned Iris's own profile/default queue, then verify every value. */
    private static void selectProfile(String name) throws Exception {
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
        Object pack = ((Optional<?>) iris.getMethod("getCurrentPack").invoke(null)).orElseThrow();
        Object menu = pack.getClass().getMethod("getMenuContainer").invoke(pack);
        Object profiles = menu.getClass().getMethod("getProfiles").invoke(menu);
        var available = new HashMap<String, Object>();
        java.util.function.BiConsumer<String, Object> collect = available::put;
        profiles.getClass()
                .getMethod("forEach", java.util.function.BiConsumer.class)
                .invoke(profiles, collect);
        Object profile = available.get(name);
        if (profile == null)
            throw new IllegalArgumentException(
                    "Unknown profile: " + name + "; available " + available.keySet());
        Class<?> profileType = Class.forName("net.irisshaders.iris.shaderpack.option.Profile");
        var requested = (Map<?, ?>) profileType.getField("optionValues").get(profile);
        iris.getMethod("queueDefaultShaderPackOptionValues").invoke(null);
        iris.getMethod("queueShaderPackOptionsFromProfile", profileType).invoke(null, profile);
        iris.getMethod("reload").invoke(null);
        var failure = (Optional<?>) iris.getMethod("getStoredError").invoke(null);
        if (failure.isPresent())
            throw new IllegalStateException("Iris rejected lab profile: " + failure.get());
        pack = ((Optional<?>) iris.getMethod("getCurrentPack").invoke(null)).orElseThrow();
        Object options = pack.getClass().getMethod("getShaderPackOptions").invoke(pack);
        Object values = options.getClass().getMethod("getOptionValues").invoke(options);
        Class<?> valuesType =
                Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues");
        Object optionSet = options.getClass().getMethod("getOptionSet").invoke(options);
        Class<?> setType = Class.forName("net.irisshaders.iris.shaderpack.option.OptionSet");
        var booleans = (Map<?, ?>) setType.getMethod("getBooleanOptions").invoke(optionSet);
        for (var entry : requested.entrySet()) {
            String key = entry.getKey().toString(), expected = entry.getValue().toString();
            Object actual =
                    valuesType
                            .getMethod(
                                    booleans.containsKey(key)
                                            ? "getBooleanValueOrDefault"
                                            : "getStringValueOrDefault",
                                    String.class)
                            .invoke(values, key);
            if (!expected.equals(String.valueOf(actual)))
                throw new IllegalStateException(
                        "Profile option mismatch: "
                                + key
                                + " expected "
                                + expected
                                + " got "
                                + actual);
        }
        if (!(Boolean)
                profileType
                        .getMethod("matches", setType, valuesType)
                        .invoke(profile, optionSet, values))
            throw new IllegalStateException("Reloaded options do not match profile " + name);
    }

    /** One latest-value slot: slow storage cannot accumulate a stale-state queue. */
    static final class StatePublisher {
        private final Path directory;
        private final AtomicReference<String> state = new AtomicReference<>();
        private final AtomicReference<String> error = new AtomicReference<>();
        private final AtomicReference<String> failure = new AtomicReference<>();
        private final AtomicBoolean scheduled = new AtomicBoolean();
        private final java.util.concurrent.ExecutorService io =
                Executors.newSingleThreadExecutor(
                        task -> {
                            var thread = new Thread(task, "Cinematic lab state I/O");
                            thread.setDaemon(true);
                            return thread;
                        });

        StatePublisher(Path directory) {
            this.directory = directory;
        }

        void publish(String serialized) {
            state.set(Objects.requireNonNull(serialized));
            schedule();
        }

        void publishError(String message) {
            error.set(Objects.requireNonNull(message));
            schedule();
        }

        String takeFailure() {
            return failure.getAndSet(null);
        }

        private void schedule() {
            if (scheduled.compareAndSet(false, true)) io.execute(this::drain);
        }

        private void replace(String name, String contents) throws java.io.IOException {
            Path target = directory.resolve(name), temporary = directory.resolve(name + ".pending");
            Files.writeString(temporary, contents);
            // Readers see either the prior complete snapshot or the new one;
            // request IDs and error text are never exposed as a partial write.
            Files.move(
                    temporary,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }

        private void attempt(String name, String contents) {
            try {
                replace(name, contents);
            } catch (java.io.IOException | SecurityException e) {
                failure.set(e.toString());
            }
        }

        private void drain() {
            try {
                while (true) {
                    String nextError = error.getAndSet(null), nextState = state.getAndSet(null);
                    if (nextError == null && nextState == null) return;
                    if (nextError != null) attempt("error.txt", nextError);
                    if (nextState != null) attempt("state.json", nextState);
                }
            } finally {
                scheduled.set(false);
                // A producer can publish between the final empty read and the
                // flag reset. Rechecking closes that lost-wakeup race.
                if (state.get() != null || error.get() != null) schedule();
            }
        }
    }

    private void beforeFrame(RenderFrameEvent.Pre event) {
        try {
            if (motion != null && !motion.done()) motion.beforeFrame(Minecraft.getInstance());
        } catch (Exception e) {
            error = e.toString();
            motion = null;
        }
    }

    private void frame(RenderFrameEvent.Post event) {
        var mc = Minecraft.getInstance();
        frame++;
        long now = System.nanoTime();
        try {
            if (motion != null && !motion.done()) motion.afterFrame(mc);
            if (captureUntil > 0) {
                if (previousFrame != 0)
                    frameRows.add(frame + "," + (now - previousFrame) + "," + mc.getFrameTimeNs());
                previousFrame = now;
                if (now >= captureUntil) {
                    Files.write(lab.resolve(captureName + ".csv"), frameRows);
                    captureUntil = 0;
                }
            }
            if (capture != null) {
                String name = capture;
                capture = null;
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
            }
        } catch (Exception e) {
            error = e.toString();
            captureUntil = 0;
        }
    }
}
