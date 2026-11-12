// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.noopsnet.soundstage.*;

import java.util.*;

/** Small tabbed editor; drafts survive tab changes and resize. */
final class DeskScreen extends Screen {
    private final StageNetwork.Open initial;
    private StageNetwork.Settings draft;
    private final List<PlaylistTrack> tracks;
    private final Map<String, EditBox> fields = new HashMap<>();
    private final List<String> labels = new ArrayList<>();
    private int tab, selected, patchIndex, x, y;
    private String error = "";

    DeskScreen(StageNetwork.Open p) {
        super(Component.literal("Soundstage"));
        initial = p;
        draft = p.settings();
        tracks = new ArrayList<>(draft.playlist());
    }

    DeskScreen(StageNetwork.Open p, DeskScreen old) {
        this(p);
        tab = old.tab;
        patchIndex = old.patchIndex;
    }

    private void button(String title, int dx, int dy, int w, Runnable action) {
        addRenderableWidget(
                Button.builder(
                                Component.literal(title),
                                b -> {
                                    try {
                                        action.run();
                                    } catch (IllegalArgumentException e) {
                                        error =
                                                e.getMessage() == null
                                                        ? "Check the entered values."
                                                        : e.getMessage();
                                    }
                                })
                        .bounds(x + dx, y + dy, w, 20)
                        .build());
    }

    private void field(String key, String label, String value, int dx, int dy, int w, int max) {
        var f = new EditBox(font, x + dx, y + dy, w, 20, Component.literal(label));
        f.setMaxLength(max);
        f.setValue(value);
        fields.put(key, f);
        addRenderableWidget(f);
        labels.add(dx + ":" + (dy - 11) + ":" + label);
    }

    private String value(String k) {
        return fields.get(k).getValue().trim();
    }

    private int number(String k, int min, int max) {
        int n;
        try {
            n = Integer.parseInt(value(k));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(k + ": enter a whole number.");
        }
        if (n < min || n > max)
            throw new IllegalArgumentException(k + ": use " + min + "–" + max + ".");
        return n;
    }

    private StageNetwork.Settings settings(
            String url,
            int volume,
            int range,
            boolean enabled,
            boolean lighting,
            int address,
            boolean mode,
            boolean loop,
            boolean spatial,
            boolean walls,
            int index,
            int fallback,
            int color,
            String group) {
        return new StageNetwork.Settings(
                url, volume, range, enabled, lighting, address, mode, loop, spatial, walls, index,
                tracks, fallback, color, group);
    }

    private void capture() {
        if (fields.isEmpty()) return;
        var s = draft;
        String url = s.url(), group = s.group();
        int v = s.volume(), r = s.range(), a = s.address(), color = s.fallbackColor();
        if (tab == 0) {
            url = value("URL");
            v = number("Volume", 0, 100);
            r = number("Range", 1, 32);
        }
        if (tab == 2) {
            a = number("Address", 1, 509);
            group = value("Group");
            if (group.isBlank()) throw new IllegalArgumentException("Give the group a name.");
            try {
                color = Integer.parseInt(value("Color").replace("#", ""), 16);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Use six hex digits for the fallback color.");
            }
            if (color < 0 || color > 0xffffff)
                throw new IllegalArgumentException("Use a color from 000000 to FFFFFF.");
        }
        draft =
                settings(
                        url,
                        v,
                        r,
                        s.enabled(),
                        s.lighting(),
                        a,
                        s.playlistMode(),
                        s.loopPlaylist(),
                        s.spatial(),
                        s.walls(),
                        Math.min(s.trackIndex(), Math.max(0, tracks.size() - 1)),
                        s.fallbackMode(),
                        color,
                        group);
    }

    private void toggle(int which) {
        capture();
        var s = draft;
        draft =
                settings(
                        s.url(),
                        s.volume(),
                        s.range(),
                        which == 0 ? !s.enabled() : s.enabled(),
                        which == 1 ? !s.lighting() : s.lighting(),
                        s.address(),
                        which == 2 ? !s.playlistMode() : s.playlistMode(),
                        which == 3 ? !s.loopPlaylist() : s.loopPlaylist(),
                        which == 4 ? !s.spatial() : s.spatial(),
                        which == 5 ? !s.walls() : s.walls(),
                        s.trackIndex(),
                        which == 6 ? (s.fallbackMode() + 1) % 3 : s.fallbackMode(),
                        s.fallbackColor(),
                        s.group());
        rebuild();
    }

    private void rebuild() {
        fields.clear();
        labels.clear();
        clearWidgets();
        init();
    }

    @Override
    protected void init() {
        x = width / 2 - 180;
        y = height / 2 - 112;
        fields.clear();
        labels.clear();
        var s = draft;
        String[] tabs = {"Audio", "Playlist", "Lighting", "Patches", "Venue"};
        for (int i = 0; i < 5; i++) {
            final int t = i;
            button(
                    tabs[i],
                    i * 73,
                    8,
                    69,
                    () -> {
                        capture();
                        tab = t;
                        rebuild();
                    });
        }
        if (tab == 0) {
            field("URL", "Live stream URL (approved host)", s.url(), 0, 48, 360, 2048);
            field("Volume", "Volume %", "" + s.volume(), 0, 86, 110, 3);
            field("Range", "Range (blocks)", "" + s.range(), 125, 86, 110, 2);
            button(s.enabled() ? "Playing" : "Stopped", 250, 86, 110, () -> toggle(0));
            button(
                    s.playlistMode() ? "Mode: playlist" : "Mode: live",
                    0,
                    115,
                    175,
                    () -> toggle(2));
            button(s.spatial() ? "3D audio: on" : "3D audio: off", 185, 115, 175, () -> toggle(4));
            button(
                    s.walls() ? "Wall muffling: on" : "Wall muffling: off",
                    0,
                    141,
                    175,
                    () -> toggle(5));
            button("Play server demo", 185, 141, 175, () -> action("demo"));
            labels.add("0:170:" + SoundstageClient.status(initial.pos()));
        } else if (tab == 1) {
            selected = Math.max(0, Math.min(selected, Math.max(0, tracks.size() - 1)));
            var t = tracks.isEmpty() ? new PlaylistTrack("", "", 180) : tracks.get(selected);
            button(
                    "<",
                    0,
                    36,
                    25,
                    () -> {
                        selected = Math.floorMod(selected - 1, Math.max(1, tracks.size()));
                        rebuild();
                    });
            button(
                    ">",
                    335,
                    36,
                    25,
                    () -> {
                        selected = (selected + 1) % Math.max(1, tracks.size());
                        rebuild();
                    });
            labels.add(
                    "35:42:Track "
                            + (tracks.isEmpty() ? 0 : selected + 1)
                            + " / "
                            + tracks.size()
                            + " — edit then Update / Add");
            field("Title", "Title", t.title(), 0, 75, 235, 80);
            field("Seconds", "Length (seconds)", "" + t.seconds(), 250, 75, 110, 5);
            field("Track URL", "Direct audio URL (seekable)", t.url(), 0, 112, 360, 2048);
            button(
                    "Update",
                    0,
                    139,
                    65,
                    () -> {
                        if (tracks.isEmpty())
                            throw new IllegalArgumentException(
                                    "Use Add to create the first track.");
                        tracks.set(selected, track());
                        capture();
                        rebuild();
                    });
            button(
                    "Add",
                    70,
                    139,
                    55,
                    () -> {
                        if (tracks.size() >= 32)
                            throw new IllegalArgumentException("Maximum 32 tracks.");
                        tracks.add(track());
                        selected = tracks.size() - 1;
                        capture();
                        rebuild();
                    });
            button(
                    "Remove",
                    130,
                    139,
                    65,
                    () -> {
                        if (!tracks.isEmpty()) tracks.remove(selected);
                        capture();
                        rebuild();
                    });
            button(
                    "Move up",
                    200,
                    139,
                    75,
                    () -> {
                        if (selected > 0) {
                            Collections.swap(tracks, selected, selected - 1);
                            selected--;
                        }
                        capture();
                        rebuild();
                    });
            button(s.loopPlaylist() ? "Loop: on" : "Loop: off", 280, 139, 80, () -> toggle(3));
            button(
                    "Play selected",
                    0,
                    166,
                    130,
                    () -> {
                        capture();
                        var d = draft;
                        draft =
                                settings(
                                        d.url(),
                                        d.volume(),
                                        d.range(),
                                        true,
                                        d.lighting(),
                                        d.address(),
                                        true,
                                        d.loopPlaylist(),
                                        d.spatial(),
                                        d.walls(),
                                        selected,
                                        d.fallbackMode(),
                                        d.fallbackColor(),
                                        d.group());
                        save();
                    });
            button("Previous", 140, 166, 105, () -> action("previous"));
            button("Next", 255, 166, 105, () -> action("next"));
        } else if (tab == 2) {
            field("Address", "Next dimmer address", "" + s.address(), 0, 51, 170, 3);
            field("Group", "New fixture group", s.group(), 185, 51, 175, 32);
            button(s.lighting() ? "Lighting: on" : "Lighting: off", 0, 82, 170, () -> toggle(1));
            button(
                    "No signal: " + new String[] {"restore", "black", "color"}[s.fallbackMode()],
                    185,
                    82,
                    175,
                    () -> toggle(6));
            field(
                    "Color",
                    "Fallback RGB hex",
                    String.format("%06X", s.fallbackColor()),
                    0,
                    124,
                    170,
                    6);
            button(
                    "Your audio / visual settings",
                    0,
                    155,
                    260,
                    () -> {
                        capture();
                        minecraft.setScreen(new ListenerSettingsScreen(this));
                    });
        } else if (tab == 4) {
            field("Venue group", "Assembly / group to arrange", s.group(), 0, 48, 175, 32);
            field("Venue address", "Starting DMX address", "" + s.address(), 185, 48, 175, 3);
            button("Auto patch ALL", 0, 77, 175, () -> venue("autopatch"));
            button("Collect connected blocks", 185, 77, 175, () -> venue("collect"));
            button("Pixels along X", 0, 104, 115, () -> venue("strip_x"));
            button("Pixels along Y", 122, 104, 115, () -> venue("strip_y"));
            button("Pixels along Z", 245, 104, 115, () -> venue("strip_z"));
            button("Reverse group order", 0, 131, 175, () -> venue("reverse"));
            button("Group shares one cue", 185, 131, 175, () -> venue("uniform"));
            button(
                    "Export QLC+ show patch",
                    0,
                    158,
                    360,
                    () -> {
                        try {
                            var dir =
                                    net.neoforged.fml.loading.FMLPaths.GAMEDIR
                                            .get()
                                            .resolve("soundstage-exports")
                                            .resolve("venue-" + System.currentTimeMillis());
                            ShowExport.write(dir, initial.patches());
                            if (minecraft.player != null)
                                minecraft.player.displayClientMessage(
                                        Component.literal("Show patch exported: " + dir), false);
                            error = "Exported to soundstage-exports in your game folder.";
                        } catch (java.io.IOException ex) {
                            throw new IllegalArgumentException("Export failed: " + ex.getMessage());
                        }
                    });
        } else {
            var patches = initial.patches();
            patchIndex = Math.min(patchIndex, Math.max(0, patches.size() - 1));
            button(
                    "Previous",
                    0,
                    38,
                    85,
                    () -> {
                        patchIndex = Math.floorMod(patchIndex - 1, Math.max(1, patches.size()));
                        rebuild();
                    });
            button(
                    "Next",
                    275,
                    38,
                    85,
                    () -> {
                        patchIndex = (patchIndex + 1) % Math.max(1, patches.size());
                        rebuild();
                    });
            labels.add(
                    "95:44:Fixture "
                            + (patches.isEmpty() ? 0 : patchIndex + 1)
                            + " / "
                            + patches.size());
            if (!patches.isEmpty()) {
                var p = patches.get(patchIndex);
                labels.add("0:70:" + p.profile().title + " @ " + p.pos().toShortString());
                field("Patch address", "Dimmer address", "" + p.address(), 0, 100, 170, 3);
                field("Patch group", "Group", p.group(), 185, 100, 175, 32);
                button("Apply fixture", 0, 131, 170, () -> patch(false, false));
                button("Apply entire group", 185, 131, 175, () -> patch(true, false));
                button("Unlink", 0, 159, 80, () -> patch(false, true));
                button("Identify", 90, 159, 80, () -> PerformanceEffects.identify(p.pos()));
                field("Name", "Fixture name", p.name(), 185, 159, 175, 48);
            } else labels.add("0:87:Use the linking tool to connect stage / Afterlight lights.");
        }
        button("Save", 0, 205, 175, this::save);
        button("Cancel", 185, 205, 175, this::onClose);
    }

    private void venue(String action) {
        var s = draft;
        String group = value("Venue group");
        if (group.isBlank()) throw new IllegalArgumentException("Choose a group name.");
        draft =
                settings(
                        s.url(),
                        s.volume(),
                        s.range(),
                        s.enabled(),
                        s.lighting(),
                        number("Venue address", 1, 509),
                        s.playlistMode(),
                        s.loopPlaylist(),
                        s.spatial(),
                        s.walls(),
                        s.trackIndex(),
                        s.fallbackMode(),
                        s.fallbackColor(),
                        group);
        action(action);
    }

    private PlaylistTrack track() {
        if (value("Title").isBlank() || value("Track URL").isBlank())
            throw new IllegalArgumentException("Enter a title and direct audio URL.");
        return new PlaylistTrack(value("Title"), value("Track URL"), number("Seconds", 1, 86400));
    }

    private void send() {
        capture();
        PacketDistributor.sendToServer(new StageNetwork.Save(initial.pos(), draft));
    }

    private void save() {
        send();
        onClose();
    }

    private void action(String action) {
        send();
        PacketDistributor.sendToServer(new StageNetwork.Action(initial.pos(), action));
    }

    private void patch(boolean group, boolean remove) {
        String name = value("Patch group");
        if (name.isBlank()) throw new IllegalArgumentException("Give the group a name.");
        var old = initial.patches().get(patchIndex);
        var p =
                new StageNetwork.Patch(
                        old.pos(),
                        number("Patch address", 1, 509),
                        name,
                        value("Name"),
                        old.profile());
        send();
        PacketDistributor.sendToServer(new StageNetwork.EditPatch(initial.pos(), p, group, remove));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g, mx, my, partial);
        g.fill(x - 10, y - 14, x + 370, y + 233, 0xee151b24);
        super.render(g, mx, my, partial);
        g.drawString(font, title, x, y - 7, 0x65e4dc);
        for (String line : labels) {
            var parts = line.split(":", 3);
            g.drawString(
                    font,
                    parts[2],
                    x + Integer.parseInt(parts[0]),
                    y + Integer.parseInt(parts[1]),
                    0xcccccc);
        }
        if (!error.isEmpty()) g.drawString(font, error, x, y + 191, 0xff7777);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
