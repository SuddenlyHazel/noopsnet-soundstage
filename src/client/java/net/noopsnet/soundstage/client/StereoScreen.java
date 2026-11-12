// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.noopsnet.soundstage.*;

import java.util.List;

/** A listening-first Stereo screen; full venue tools remain available under Settings. */
final class StereoScreen extends BaseOwoScreen<FlowLayout> {
    private final StageNetwork.Open initial;
    private StageNetwork.Settings draft;
    private final List<RadioStation> stations;
    private LabelComponent now, state;
    private ButtonComponent play;
    private int saveIn = -1, syncAfter, textWidth;
    private boolean syncingVolume;
    private io.wispforest.owo.ui.component.SliderComponent volumeControl;
    private final java.util.UUID sourceId;

    StereoScreen(StageNetwork.Open payload) {
        super(Component.literal("Stereo"));
        initial = payload;
        draft = payload.settings();
        var level = net.minecraft.client.Minecraft.getInstance().level;
        sourceId =
                level != null && level.getBlockEntity(payload.pos()) instanceof StageEntity source
                        ? source.id
                        : null;
        stations = RadioStation.available(Soundstage.RADIO_STATIONS.get(), Soundstage.HOSTS.get());
    }

    void update(StageNetwork.Open payload) {
        if (payload.pos().equals(initial.pos())) draft = payload.settings();
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    private LabelComponent label(String text, int color) {
        return Components.label(Component.literal(text).withColor(color & 0xffffff)).shadow(false);
    }

    // owo injects Component into vanilla widgets at runtime; NeoForge javac does not see that
    // interface.
    private io.wispforest.owo.ui.core.Component ui(
            net.minecraft.client.gui.components.AbstractWidget widget) {
        return (io.wispforest.owo.ui.core.Component) (Object) widget;
    }

    private ButtonComponent button(String text, int width, Runnable action) {
        var button = Components.button(Component.literal(text), b -> action.run());
        ui(button).sizing(Sizing.fixed(width), Sizing.fixed(20));
        button.renderer(
                ButtonComponent.Renderer.flat(
                        StereoUiLayout.BUTTON, StereoUiLayout.HOVER, 0xff1e2831));
        button.textShadow(false);
        return button;
    }

    @Override
    protected void build(FlowLayout root) {
        var size = StereoUiLayout.fit(width, height);
        root.surface(Surface.flat(0xb5091018));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        var panel =
                Containers.verticalFlow(Sizing.fixed(size.width()), Sizing.fixed(size.height()));
        panel.padding(Insets.of(size.padding()));
        panel.gap(size.gap());
        panel.surface(Surface.flat(StereoUiLayout.PANEL).and(Surface.outline(0xff3c5260)));
        int inner = size.width() - size.padding() * 2;
        textWidth = inner;

        var heading = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        var title = label("Stereo", StereoUiLayout.TEXT);
        title.text(Component.literal("Stereo").withStyle(ChatFormatting.BOLD).withColor(0xedf4f3));
        // This owo build wraps labels before fixed/fill widths are inflated. Content sizing
        // with an explicit maxWidth keeps the first layout from wrapping one glyph per line.
        title.maxWidth(inner - 54);
        title.sizing(Sizing.content(), Sizing.content());
        var titleSpace = Containers.verticalFlow(Sizing.fixed(inner - 54), Sizing.fixed(20));
        titleSpace.verticalAlignment(VerticalAlignment.CENTER);
        titleSpace.child(title);
        heading.child(titleSpace);
        heading.child(ui(button("Done", 54, this::onClose)));
        panel.child(heading);

        var playing = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(size.nowHeight()));
        playing.gap(4);
        now = label("Choose a station", StereoUiLayout.TEXT);
        now.maxWidth(inner);
        now.sizing(Sizing.content(), Sizing.fixed(10));
        state = label("", StereoUiLayout.ACCENT);
        state.maxWidth(inner);
        state.sizing(Sizing.content(), Sizing.fixed(10));
        playing.child(now).child(state);
        panel.child(playing);

        var controls = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        controls.gap(size.gap());
        play = button("Play", size.playWidth(), this::togglePlayback);
        controls.child(ui(play));
        var volume = Components.slider(Sizing.fixed(size.sliderWidth()));
        volumeControl = volume;
        volume.value(draft.volume() / 100.0);
        volume.message(s -> Component.literal("Stereo volume " + draft.volume() + "%"));
        ui(volume)
                .tooltip(
                        Component.literal(
                                "Stereo volume for everyone nearby. Your audio settings are"
                                        + " separate."));
        volume.onChanged()
                .subscribe(
                        value -> {
                            if (syncingVolume) return;
                            draft =
                                    StereoControls.audio(
                                            draft, (int) Math.round(value * 100), draft.enabled());
                            if (saveIn < 0) saveIn = 4;
                        });
        volume.slideEnd().subscribe(this::flush);
        controls.child(ui(volume));
        panel.child(controls);
        panel.child(label("Stations", StereoUiLayout.MUTED).verticalSizing(Sizing.fixed(12)));

        var list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(3);
        if (stations.isEmpty()) {
            var empty =
                    label(
                            "No stations configured. Open Settings to use a direct stream.",
                            StereoUiLayout.MUTED);
            empty.maxWidth(inner - 12);
            empty.horizontalSizing(Sizing.fill(100));
            list.child(empty);
        }
        for (RadioStation station : stations) {
            var choice =
                    button(
                            station.name(),
                            inner - 12,
                            () -> {
                                draft = StereoControls.tune(draft, station);
                                saveIn = 0;
                                flush();
                            });
            ui(choice).verticalSizing(Sizing.fixed(size.rowHeight()));
            ui(choice).tooltip(Component.literal(station.name() + " - select to listen live"));
            choice.renderer(
                    (context, b, delta) -> {
                        boolean selected =
                                !draft.playlistMode() && station.url().equals(draft.url());
                        int color =
                                b.isHoveredOrFocused()
                                        ? StereoUiLayout.HOVER
                                        : selected
                                                ? StereoUiLayout.SELECTED
                                                : StereoUiLayout.BUTTON;
                        context.fill(
                                b.getX(),
                                b.getY(),
                                b.getX() + b.getWidth(),
                                b.getY() + b.getHeight(),
                                color);
                        if (selected || b.isFocused())
                            context.renderOutline(
                                    b.getX(),
                                    b.getY(),
                                    b.getWidth(),
                                    b.getHeight(),
                                    StereoUiLayout.ACCENT);
                    });
            list.child(ui(choice));
        }
        var scroll =
                Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(size.listHeight()), list);
        scroll.surface(Surface.flat(StereoUiLayout.INSET));
        panel.child(scroll);

        var footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        footer.gap(size.gap());
        int first = (inner - size.gap()) / 2;
        footer.child(
                ui(
                        button(
                                "Settings",
                                first,
                                () -> {
                                    flush();
                                    minecraft.setScreen(
                                            DeskScreen.advanced(
                                                    new StageNetwork.Open(
                                                            initial.pos(),
                                                            draft,
                                                            initial.patches()),
                                                    this));
                                })));
        footer.child(
                ui(
                        button(
                                "Your audio",
                                inner - size.gap() - first,
                                () -> {
                                    flush();
                                    minecraft.setScreen(new ListenerSettingsScreen(this));
                                })));
        panel.child(footer);
        root.child(panel);
        refreshLabels();
    }

    private String stationName() {
        if (draft.playlistMode()) return "Playlist";
        return stations.stream()
                .filter(s -> s.url().equals(draft.url()))
                .map(RadioStation::name)
                .findFirst()
                .orElse(draft.url().isBlank() ? "Choose a station" : "Custom stream");
    }

    private void refreshLabels() {
        if (now == null) return;
        String name = stationName();
        if (font.width(name) > textWidth)
            name = font.plainSubstrByWidth(name, textWidth - 12) + "...";
        if (!now.text().getString().equals(name)) {
            now.tooltip(Component.literal(stationName()));
            now.text(Component.literal(name).withStyle(ChatFormatting.BOLD).withColor(0xedf4f3));
        }
        String status =
                StereoControls.status(
                        draft.enabled(),
                        SoundstageClient.mute,
                        SoundstageClient.status(initial.pos()));
        if (draft.enabled() && !SoundstageClient.mute && draft.volume() == 0)
            status = "Stereo volume is 0%";
        else if (draft.enabled() && !SoundstageClient.mute && SoundstageClient.personalVolume == 0)
            status = "Your listening volume is 0%";
        if (!state.text().getString().equals(status))
            state.text(
                    Component.literal(status)
                            .withColor(
                                    status.startsWith("Station unavailable")
                                            ? 0xffb0a3
                                            : 0x65e4dc));
        String action =
                status.startsWith("Station unavailable")
                        ? "Retry"
                        : SoundstageClient.mute && draft.enabled()
                                ? "Unmute"
                                : draft.enabled() ? "Stop" : "Play";
        if (!play.getMessage().getString().equals(action))
            play.setMessage(Component.literal(action));
        play.active(draft.playlistMode() ? !draft.playlist().isEmpty() : !draft.url().isBlank());
    }

    private void togglePlayback() {
        if (StereoControls.status(draft.enabled(), false, SoundstageClient.status(initial.pos()))
                .startsWith("Station unavailable")) {
            SoundstageClient.retry(initial.pos());
        } else if (SoundstageClient.mute && draft.enabled()) {
            SoundstageClient.mute = false;
            SoundstageClient.savePrefs();
        } else {
            draft = StereoControls.audio(draft, draft.volume(), !draft.enabled());
            saveIn = 0;
            flush();
        }
        refreshLabels();
    }

    private void flush() {
        if (saveIn < 0) return;
        if (minecraft.getConnection() != null)
            PacketDistributor.sendToServer(new StageNetwork.Save(initial.pos(), draft));
        syncAfter = 10;
        saveIn = -1;
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft.level == null
                || minecraft.player == null
                || !(minecraft.level.getBlockEntity(initial.pos()) instanceof StageEntity source)
                || !source.id.equals(sourceId)
                || minecraft.player.distanceToSqr(
                                net.minecraft.world.phys.Vec3.atCenterOf(initial.pos()))
                        > 64) {
            saveIn = -1;
            onClose();
            return;
        }
        if (saveIn > 0) saveIn--;
        if (saveIn == 0) flush();
        if (syncAfter > 0) syncAfter--;
        else if (saveIn < 0) {
            draft = source.settings();
            if (volumeControl != null
                    && Math.round(volumeControl.value() * 100) != draft.volume()) {
                syncingVolume = true;
                volumeControl.value(draft.volume() / 100.0);
                syncingVolume = false;
            }
        }
        refreshLabels();
    }

    @Override
    public void onClose() {
        flush();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
