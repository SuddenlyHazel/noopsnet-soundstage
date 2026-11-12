// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Personal controls: apply immediately and persist without editing a venue. */
final class ListenerSettingsScreen extends Screen {
    private final Screen parent;

    ListenerSettingsScreen(Screen parent) {
        super(Component.literal("Soundstage settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 155, y = Math.max(48, height / 2 - 77);
        slider(
                x,
                y,
                "Volume",
                SoundstageClient.personalVolume,
                100,
                "%",
                v -> SoundstageClient.personalVolume = v,
                "Your listening volume for all performances.");
        toggle(
                x + 160,
                y,
                "Mute",
                () -> SoundstageClient.mute,
                v -> SoundstageClient.mute = v,
                "Mute all Soundstage audio for you.");
        slider(
                x,
                y + 24,
                "Brightness",
                SoundstageClient.brightness,
                100,
                "%",
                v -> SoundstageClient.brightness = v,
                "Limit the brightness of performance lights and effects.");
        toggle(
                x + 160,
                y + 24,
                "Show effects",
                () -> SoundstageClient.lights,
                SoundstageClient::lightingPreference,
                "Enable performance-controlled lights, fog, lasers and particles.");
        slider(
                x,
                y + 48,
                "RGB smoothing",
                SoundstageClient.smoothingMillis,
                2000,
                " ms",
                v -> SoundstageClient.smoothingMillis = v,
                "Soften stage-light and LED color transitions. Higher values respond more slowly.");
        slider(
                x + 160,
                y + 48,
                "Particles",
                SoundstageClient.particleDensity,
                100,
                "%",
                v -> SoundstageClient.particleDensity = v,
                "Reduce particles from performance machines. Also respects Minecraft's particle"
                        + " setting.");
        toggle(
                x,
                y + 72,
                "Fog / mist",
                () -> SoundstageClient.showFog,
                SoundstageClient::fogPreference,
                "Hide fog and volumetric beams. Spotlights still illuminate surfaces.");
        toggle(
                x + 160,
                y + 72,
                "Lasers",
                () -> SoundstageClient.showLasers,
                v -> SoundstageClient.showLasers = v,
                "Show laser beams from performances.");
        slider(
                x,
                y + 96,
                "Fog density",
                SoundstageClient.fogDensity,
                200,
                "%",
                v -> SoundstageClient.fogDensity = v,
                "Control haze independently of light brightness. 100% is ordinary stage haze.");
        addRenderableWidget(
                Button.builder(
                                Component.literal("Lower effects"),
                                b -> {
                                    SoundstageClient.brightness = 50;
                                    SoundstageClient.particleDensity = 25;
                                    SoundstageClient.smoothingMillis = 800;
                                    SoundstageClient.fogPreference(false);
                                    rebuildWidgets();
                                })
                        .bounds(x + 160, y + 96, 150, 20)
                        .tooltip(
                                Tooltip.create(
                                        Component.literal(
                                                "50% brightness, 25% particles, smoother RGB lights"
                                                        + " and no fog.")))
                        .build());
        toggle(
                x,
                y + 120,
                "Laser smoothing",
                () -> SoundstageClient.laserSmoothing,
                SoundstageClient::laserSmoothingPreference,
                "Smooth small tracking steps. Large or abrupt pan/tilt changes snap immediately;"
                        + " colors and blackouts keep their cue timing.");
        addRenderableWidget(
                Button.builder(
                                Component.literal("Reset defaults"),
                                b -> {
                                    SoundstageClient.personalVolume = 100;
                                    SoundstageClient.mute = false;
                                    SoundstageClient.brightness = 100;
                                    SoundstageClient.particleDensity = 100;
                                    SoundstageClient.fogDensity = 100;
                                    SoundstageClient.smoothingMillis = 0;
                                    SoundstageClient.showLasers = true;
                                    SoundstageClient.lightingPreference(true);
                                    SoundstageClient.fogPreference(true);
                                    SoundstageClient.laserSmoothingPreference(true);
                                    rebuildWidgets();
                                })
                        .bounds(x, y + 148, 150, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(x + 160, y + 148, 150, 20)
                        .build());
    }

    private void toggle(
            int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set, String hint) {
        addRenderableWidget(
                Button.builder(
                                text(label, get.getAsBoolean()),
                                b -> {
                                    set.accept(!get.getAsBoolean());
                                    b.setMessage(text(label, get.getAsBoolean()));
                                })
                        .bounds(x, y, 150, 20)
                        .tooltip(Tooltip.create(Component.literal(hint)))
                        .build());
    }

    private static Component text(String label, boolean value) {
        return Component.literal(label + ": " + (value ? "ON" : "OFF"));
    }

    private void slider(
            int x,
            int y,
            String label,
            int initial,
            int maximum,
            String unit,
            IntConsumer change,
            String hint) {
        var slider =
                new AbstractSliderButton(
                        x, y, 150, 20, Component.empty(), initial / (double) maximum) {
                    @Override
                    protected void updateMessage() {
                        setMessage(
                                Component.literal(
                                        label + ": " + Math.round(value * maximum) + unit));
                    }

                    @Override
                    protected void applyValue() {
                        change.accept((int) Math.round(value * maximum));
                    }
                };
        slider.setMessage(Component.literal(label + ": " + initial + unit));
        slider.setTooltip(Tooltip.create(Component.literal(hint)));
        addRenderableWidget(slider);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g, mouseX, mouseY, delta);
        super.render(g, mouseX, mouseY, delta);
        int y = Math.max(48, height / 2 - 77);
        g.drawCenteredString(font, title, width / 2, y - 32, 0xFFFFFF);
        g.drawCenteredString(
                font, "Your preferences apply to every venue.", width / 2, y - 18, 0xBBBBBB);
    }

    @Override
    public void removed() {
        SoundstageClient.savePrefs();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
