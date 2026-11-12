// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.util.List;

/** Offline catalog validation and tuning transitions. Does not start Minecraft. */
public final class RadioStationProbe {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String url = "https://noops.world/soundstage/soundstage/radio-cozy-autumn.m3u8";
        var station = RadioStation.parse(" Cozy Autumn | " + url + " ");
        require(station.name().equals("Cozy Autumn") && station.url().equals(url), "Trim entries");
        for (String entry :
                List.of(
                        "Missing separator",
                        " | " + url,
                        "Empty | ",
                        "Local | file:///tmp/music",
                        "Secret | https://user:password@noops.world/music",
                        "Fragment | " + url + "#key",
                        "Broken\nName | " + url,
                        "a".repeat(65) + " | " + url)) {
            require(!RadioStation.validEntry(entry), "Reject invalid station: " + entry);
        }
        var choices =
                RadioStation.available(
                        List.of(
                                "broken",
                                "Cozy | " + url,
                                "Duplicate | " + url,
                                "Unapproved | https://evil.test/radio"),
                        List.of("noops.world"));
        require(
                choices.size() == 1 && choices.getFirst().url().equals(url),
                "Filter catalog safely");
        var many =
                java.util.stream.IntStream.range(0, 40)
                        .mapToObj(i -> "Station " + i + " | https://noops.world/" + i)
                        .toList();
        require(RadioStation.available(many, List.of("noops.world")).size() == 32, "Bound catalog");
        var tracks = List.of(new PlaylistTrack("Old playlist", "https://noops.world/old", 60));
        var old =
                new StageNetwork.Settings(
                        "https://noops.world/old",
                        43,
                        17,
                        false,
                        true,
                        25,
                        true,
                        true,
                        true,
                        true,
                        0,
                        tracks,
                        2,
                        0xff00aa,
                        "Dance floor");
        var tuned = station.tune(old);
        require(
                tuned.valid()
                        && tuned.enabled()
                        && !tuned.playlistMode()
                        && !tuned.lighting()
                        && tuned.url().equals(url),
                "Tune live audio without interpreting music as DMX");
        require(
                tuned.volume() == 43
                        && tuned.range() == 17
                        && tuned.spatial()
                        && tuned.walls()
                        && tuned.playlist().equals(tracks)
                        && tuned.address() == 25
                        && tuned.fallbackColor() == old.fallbackColor()
                        && tuned.group().equals(old.group()),
                "Keep personal venue settings and playlist");
        System.out.println("Radio station catalog and tuning checks passed");
    }
}
