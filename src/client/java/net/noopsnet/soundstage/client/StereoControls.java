// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.noopsnet.soundstage.RadioStation;
import net.noopsnet.soundstage.StageNetwork;

/** Radio edits preserve the venue's playlists, links and non-audio settings. */
public final class StereoControls {
    private StereoControls() {}

    public static StageNetwork.Settings tune(StageNetwork.Settings old, RadioStation station) {
        return station.tune(old);
    }

    public static StageNetwork.Settings audio(
            StageNetwork.Settings s, int volume, boolean enabled) {
        return new StageNetwork.Settings(
                s.url(),
                Math.clamp(volume, 0, 100),
                s.range(),
                enabled,
                s.lighting(),
                s.address(),
                s.playlistMode(),
                s.loopPlaylist(),
                s.spatial(),
                s.walls(),
                s.trackIndex(),
                s.playlist(),
                s.fallbackMode(),
                s.fallbackColor(),
                s.group());
    }

    public static String status(boolean enabled, boolean muted, String raw) {
        if (!enabled) return "Stopped";
        String value = raw.toLowerCase(java.util.Locale.ROOT);
        if (value.contains("unavailable") || value.contains("unable") || value.contains("error"))
            return "Station unavailable - Retry";
        if (muted) return "Muted for you";
        if (value.contains("outside")) return "Outside listening range";
        if (value.contains("reconnect")) return "Reconnecting...";
        if (value.contains("play")) return "Playing live";
        if (value.contains("pause")) return "Paused";
        return "Connecting...";
    }
}
