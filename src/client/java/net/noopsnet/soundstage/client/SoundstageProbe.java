// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.net.URI;
import java.io.IOException;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.noopsnet.soundstage.VrslGrid;
import org.watermedia.api.player.PlayerAPI;

@Mod(value = "noopsnet_soundstage", dist = Dist.CLIENT)
public final class SoundstageProbe {
    private static ProbePlayer player;
    private static ProbeTelemetry telemetry;
    private static Vec3 fixture;
    public SoundstageProbe() {
        NeoForge.EVENT_BUS.addListener(SoundstageProbe::commands);
        NeoForge.EVENT_BUS.addListener(SoundstageProbe::render);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> { if (player != null) player.pollClock(); });
        NeoForge.EVENT_BUS.addListener(SoundstageProbe::renderWorld);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> stop());
    }
    private static void stop() { if (player != null) { player.release(); player = null; } fixture = null; }
    private static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("soundstageprobe")
            .then(Commands.literal("stop").executes(c -> { stop(); return 1; }))
            .then(Commands.literal("start").then(Commands.argument("url", StringArgumentType.string()).executes(c -> {
                URI uri;
                try { uri = URI.create(StringArgumentType.getString(c, "url")); }
                catch (IllegalArgumentException ex) { c.getSource().sendFailure(Component.literal("Invalid media URI")); return 0; }
                if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getUserInfo() != null) {
                    c.getSource().sendFailure(Component.literal("Prototype accepts local http://127.0.0.1 URLs only")); return 0;
                }
                if (!PlayerAPI.isReady()) { c.getSource().sendFailure(Component.literal("WATERMeDIA VLC backend is not ready")); return 0; }
                stop();
                try { telemetry = new ProbeTelemetry(Minecraft.getInstance().gameDirectory.toPath().resolve("logs/soundstage")); }
                catch (IOException ex) { c.getSource().sendFailure(Component.literal("Cannot open probe CSV")); return 0; }
                player = new ProbePlayer(VrslGrid.Layout.HORIZONTAL_TOP, VrslGrid.Transfer.IDENTITY, telemetry);
                var viewer = Minecraft.getInstance().player;
                if (viewer != null) fixture = viewer.getEyePosition().add(viewer.getLookAngle().scale(3));
                player.setVolume(30);
                player.start(uri);
                c.getSource().sendSuccess(() -> Component.literal("Probe started: 1080p monochrome, raw pixel transfer; /soundstageprobe stop to release"), false);
                return 1;
            }))));
    }
    private static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || player == null || fixture == null) return;
        ProbePlayer.Sample s = player.sample();
        if (s == null) return;
        boolean stale = System.nanoTime() - s.observedNanos() > 1_000_000_000L;
        float gain = s.intensity() / 255f;
        Vec3 p = fixture.subtract(event.getCamera().getPosition());
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        DebugRenderer.renderFilledBox(event.getPoseStack(), buffers,
            new AABB(p.x - 0.25, p.y - 0.25, p.z - 0.25, p.x + 0.25, p.y + 0.25, p.z + 0.25),
            stale ? .1f : s.red() / 255f * gain, stale ? .1f : s.green() / 255f * gain,
            stale ? .1f : s.blue() / 255f * gain, 1f);
        buffers.endBatch(RenderType.debugFilledBox());
    }
    private static void render(RenderGuiEvent.Post event) {
        if (player == null) return;
        ProbePlayer.Sample s = player.sample();
        var gui = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        if (s == null) { gui.drawString(font, "Soundstage: " + player.diagnostics(), 8, 8, 0xffffff); return; }
        boolean stale = System.nanoTime() - s.observedNanos() > 1_000_000_000L;
        int r = s.red() * s.intensity() / 255, g = s.green() * s.intensity() / 255, b = s.blue() * s.intensity() / 255;
        gui.fill(8, 24, 72, 88, stale ? 0xff202020 : 0xff000000 | r << 16 | g << 8 | b);
        gui.drawString(font, "Soundstage frame " + s.sequence() + " clock " + s.mediaMillis() + " ms" + (stale ? " STALE" : ""), 8, 8, 0xffffff);
        gui.drawString(font, "DMX " + s.intensity() + "," + s.red() + "," + s.green() + "," + s.blue(), 8, 92, 0xffffff);
        gui.drawString(font, telemetry.status(), 8, 104, 0xffffff);
    }
}
