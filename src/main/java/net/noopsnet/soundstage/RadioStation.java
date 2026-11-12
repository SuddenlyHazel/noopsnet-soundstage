// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Named, operator-configured live audio sources; never contains publishing credentials. */
public record RadioStation(String name, String url) {
    public RadioStation {
        name = name.strip();
        url = url.strip();
        if (name.isEmpty()
                || name.length() > 64
                || name.chars().anyMatch(Character::isISOControl)
                || url.isEmpty()
                || !StreamPolicy.allowed(url, List.of(host(url)))) {
            throw new IllegalArgumentException("Use Station name | http(s)://approved-host/stream");
        }
    }

    private static String host(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public static RadioStation parse(String entry) {
        int split = entry.indexOf('|');
        if (split < 0) throw new IllegalArgumentException("Separate station name and URL with |");
        return new RadioStation(entry.substring(0, split), entry.substring(split + 1));
    }

    public static boolean validEntry(Object value) {
        if (!(value instanceof String entry)) return false;
        try {
            parse(entry);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static List<RadioStation> available(
            List<? extends String> entries, Collection<? extends String> approvedHosts) {
        var stations = new ArrayList<RadioStation>();
        for (String entry : entries) {
            try {
                var station = parse(entry);
                if (StreamPolicy.allowed(station.url, approvedHosts)
                        && stations.stream().noneMatch(s -> s.url.equals(station.url))) {
                    stations.add(station);
                    if (stations.size() == 32) break;
                }
            } catch (IllegalArgumentException ignored) {
                // A bad entry must not hide the remaining usable stations.
            }
        }
        return List.copyOf(stations);
    }

    public StageNetwork.Settings tune(StageNetwork.Settings settings) {
        return new StageNetwork.Settings(
                url,
                settings.volume(),
                settings.range(),
                true,
                false,
                settings.address(),
                false,
                settings.loopPlaylist(),
                settings.spatial(),
                settings.walls(),
                settings.trackIndex(),
                settings.playlist(),
                settings.fallbackMode(),
                settings.fallbackColor(),
                settings.group());
    }
}
