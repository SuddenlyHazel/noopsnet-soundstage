// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.noopsnet.soundstage.FixtureProfile;
import net.noopsnet.soundstage.ManualFixtureSettings;
import net.noopsnet.soundstage.StageNetwork;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Player-operated editor for one fixture; it never changes the linked desk patch. */
final class FixtureControlScreen extends Screen {
    private record Label(int x, int y, String text) {}

    private final StageNetwork.OpenFixture initial;
    private final Map<String, EditBox> fields = new HashMap<>();
    private final Map<String, String> draft = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();
    private boolean enabled;
    private String error = "";
    private int x, y;

    FixtureControlScreen(StageNetwork.OpenFixture initial) {
        super(Component.literal(initial.profile().title));
        this.initial = initial;
        this.enabled = initial.settings().enabled();
    }

    private void field(String key, String label, String value, int dx, int dy, int w, int limit) {
        var input = new EditBox(font, x + dx, y + dy, w, 20, Component.literal(label));
        input.setMaxLength(limit);
        input.setValue(draft.getOrDefault(key, value));
        draft.put(key, input.getValue());
        input.setResponder(text -> draft.put(key, text));
        fields.put(key, input);
        labels.add(new Label(dx, dy - 11, label));
        addRenderableWidget(input);
    }

    private int number(String key, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(fields.get(key).getValue().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + ": enter a whole number.");
        }
        if (value < min || value > max)
            throw new IllegalArgumentException(key + ": use " + min + " to " + max + ".");
        return value;
    }

    private int color() {
        return ManualFixtureInputs.parseColor(fields.get("Color").getValue());
    }

    private float fixtureTilt() {
        if (!fields.containsKey("Fixture tilt")) return initial.fixtureTilt();
        float value;
        try {
            value = Float.parseFloat(fields.get("Fixture tilt").getValue().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Fixture tilt: enter degrees.");
        }
        if (!Float.isFinite(value) || value < -80 || value > 80)
            throw new IllegalArgumentException("Fixture tilt: use -80 to 80 degrees.");
        return value;
    }

    private void applyManual() {
        try {
            var previous = initial.settings();
            int rgb = initial.profile() == FixtureProfile.FOG ? 0 : color();
            int pan =
                    initial.profile() == FixtureProfile.LASER
                            ? ManualFixtureInputs.degreesToChannel(number("Pan", -180, 180), 180)
                            : previous.pan();
            int tilt =
                    initial.profile() == FixtureProfile.LASER
                            ? ManualFixtureInputs.degreesToChannel(number("Tilt", -90, 90), 90)
                            : previous.tilt();
            int spread =
                    initial.profile() == FixtureProfile.FOG
                            ? ManualFixtureInputs.percentToChannel(number("Spread", 0, 100))
                            : previous.spread();
            var settings =
                    new ManualFixtureSettings(
                            true,
                            enabled,
                            ManualFixtureInputs.percentToChannel(number("Output", 0, 100)),
                            initial.profile() == FixtureProfile.FOG
                                    ? previous.red()
                                    : (rgb >> 16) & 255,
                            initial.profile() == FixtureProfile.FOG
                                    ? previous.green()
                                    : (rgb >> 8) & 255,
                            initial.profile() == FixtureProfile.FOG ? previous.blue() : rgb & 255,
                            pan,
                            tilt,
                            spread);
            PacketDistributor.sendToServer(
                    new StageNetwork.SaveFixture(
                            initial.pos(),
                            initial.fixtureId(),
                            initial.revision(),
                            settings,
                            fixtureTilt()));
            onClose();
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
        }
    }

    @Override
    protected void init() {
        x = width / 2 - 150;
        y = height / 2 - 106;
        fields.clear();
        labels.clear();
        var settings = initial.settings();
        field(
                "Output",
                "Output %",
                Integer.toString(ManualFixtureInputs.channelToPercent(settings.output())),
                0,
                42,
                125,
                3);
        var power =
                Button.builder(
                                Component.literal(enabled ? "Power: on" : "Power: off"),
                                b -> {
                                    enabled = !enabled;
                                    b.setMessage(
                                            Component.literal(
                                                    enabled ? "Power: on" : "Power: off"));
                                })
                        .bounds(x + 160, y + 42, 140, 20)
                        .build();
        addRenderableWidget(power);
        if (initial.profile() == FixtureProfile.FOG) {
            field(
                    "Spread",
                    "Fog spread %",
                    Integer.toString(ManualFixtureInputs.channelToPercent(settings.spread())),
                    0,
                    82,
                    140,
                    3);
        } else {
            int rgb = settings.red() << 16 | settings.green() << 8 | settings.blue();
            field(
                    "Color",
                    "RGB color (hex)",
                    String.format(Locale.ROOT, "%06X", rgb),
                    0,
                    82,
                    140,
                    7);
        }
        if (initial.profile() == FixtureProfile.LASER) {
            field(
                    "Pan",
                    "Pan (-180 to 180 deg)",
                    Integer.toString(ManualFixtureInputs.channelToDegrees(settings.pan(), 180)),
                    0,
                    122,
                    140,
                    4);
            field(
                    "Tilt",
                    "Tilt (-90 to 90 deg)",
                    Integer.toString(ManualFixtureInputs.channelToDegrees(settings.tilt(), 90)),
                    160,
                    122,
                    140,
                    3);
        } else if (initial.profile() == FixtureProfile.RGB) {
            field(
                    "Fixture tilt",
                    "Aim down/up (-80 to 80 deg)",
                    Float.toString(initial.fixtureTilt()),
                    0,
                    122,
                    140,
                    8);
        }
        addRenderableWidget(
                Button.builder(Component.literal("Use manual"), b -> applyManual())
                        .bounds(x, y + 166, 94, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Return to show"),
                                b -> {
                                    PacketDistributor.sendToServer(
                                            new StageNetwork.SaveFixture(
                                                    initial.pos(),
                                                    initial.fixtureId(),
                                                    initial.revision(),
                                                    initial.settings().withOverride(false),
                                                    initial.fixtureTilt()));
                                    onClose();
                                })
                        .bounds(x + 100, y + 166, 110, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> onClose())
                        .bounds(x + 216, y + 166, 84, 20)
                        .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, y + 5, 0xffffff);
        graphics.drawString(
                font,
                initial.settings().override() ? "Current control: manual" : "Current control: show",
                x,
                y + 20,
                0xb0b0b0,
                false);
        for (var label : labels)
            graphics.drawString(font, label.text(), x + label.x(), y + label.y(), 0xffffff, false);
        if (!error.isEmpty()) graphics.drawString(font, error, x, y + 194, 0xff8888, false);
    }
}
