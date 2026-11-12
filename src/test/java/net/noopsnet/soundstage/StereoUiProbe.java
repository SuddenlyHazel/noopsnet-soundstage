// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import net.noopsnet.soundstage.client.StereoControls;
import net.noopsnet.soundstage.client.StereoUiLayout;

import java.util.List;

/** Offline controls and readable geometry; never launches a Minecraft client. */
public final class StereoUiProbe {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws java.io.IOException {
        var tracks = List.of(new PlaylistTrack("Saved playlist", "https://noops.world/song", 60));
        var settings =
                new StageNetwork.Settings(
                        "https://noops.world/custom",
                        60,
                        15,
                        true,
                        true,
                        21,
                        true,
                        true,
                        true,
                        true,
                        0,
                        tracks,
                        2,
                        0x123456,
                        "My venue");
        var changed = StereoControls.audio(settings, 101, false);
        require(changed.volume() == 100 && !changed.enabled(), "Clamp and stop audio");
        require(
                changed.playlist().equals(tracks)
                        && changed.playlistMode()
                        && changed.lighting()
                        && changed.address() == 21
                        && changed.range() == 15
                        && changed.group().equals("My venue"),
                "Simple controls preserve advanced settings");
        require(StereoControls.audio(settings, -10, true).volume() == 0, "Clamp minimum volume");
        var radio = new RadioStation("Radio", "https://noops.world/live");
        var tuned = StereoControls.tune(settings, radio);
        require(
                tuned.enabled()
                        && !tuned.playlistMode()
                        && !tuned.lighting()
                        && tuned.url().equals(radio.url())
                        && tuned.playlist().equals(tracks),
                "Tune live audio safely");
        require(StereoControls.status(false, false, "PLAYING").equals("Stopped"), "Respect power");
        require(
                StereoControls.status(true, true, "PLAYING").equals("Muted for you"),
                "Explain local mute");
        require(
                StereoControls.status(true, false, "ERROR / VLC").startsWith("Station unavailable"),
                "Error recovery");
        require(
                StereoControls.status(true, false, "Outside playback range")
                        .equals("Outside listening range"),
                "Explain distance");
        require(
                StereoControls.status(true, false, "PLAYING / OpenAL").equals("Playing live"),
                "Hide backend jargon");
        require(
                StereoControls.status(true, false, "Reconnecting").equals("Reconnecting..."),
                "Show reconnect state");
        require(
                net.noopsnet.soundstage.client.FogDensityPreference.load(100, 1) == 100,
                "Existing defaults receive the stronger baseline");
        require(
                net.noopsnet.soundstage.client.FogDensityPreference.load(200, 1) == 133,
                "Preserve deliberate legacy high-density appearance");
        require(
                net.noopsnet.soundstage.client.FogDensityPreference.load(150, 1) == 100,
                "Legacy 150 percent is the new normal");
        require(
                net.noopsnet.soundstage.client.FogDensityPreference.load(300, 2) == 300,
                "Do not migrate new preferences again");
        require(
                net.noopsnet.soundstage.client.FogDensityPreference.load(999, 2) == 300
                        && net.noopsnet.soundstage.client.FogDensityPreference.load(-10, 2) == 0,
                "Bound the expanded personal range");
        var evidence = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (int[] screen :
                new int[][] {{320, 180}, {426, 240}, {640, 360}, {960, 540}, {1280, 720}}) {
            var layout = StereoUiLayout.fit(screen[0], screen[1]);
            require(
                    layout.width() <= screen[0] - 16 && layout.height() <= screen[1] - 16,
                    "Screen bounds");
            require(
                    layout.listHeight() >= layout.rowHeight(),
                    "At least one accessible row at largest GUI scale");
            require(
                    layout.playWidth() + layout.gap() + layout.sliderWidth()
                            == layout.width() - 2 * layout.padding(),
                    "Transport row fits");
            evidence.add(
                    java.util.Map.of(
                            "screenWidth",
                            screen[0],
                            "screenHeight",
                            screen[1],
                            "layout",
                            layout,
                            "panelColor",
                            StereoUiLayout.PANEL,
                            "insetColor",
                            StereoUiLayout.INSET,
                            "textColor",
                            StereoUiLayout.TEXT,
                            "mutedColor",
                            StereoUiLayout.MUTED,
                            "accentColor",
                            StereoUiLayout.ACCENT));
        }
        var target = java.nio.file.Path.of("build/reports/stereo-ui-layout.json");
        java.nio.file.Files.createDirectories(target.getParent());
        java.nio.file.Files.writeString(
                target,
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence));
        System.out.println("Stereo controls, status and five GUI-scale layouts passed");
    }
}
