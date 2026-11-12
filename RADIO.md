# Community radio

The Stereo opens a dedicated **owo-ui radio screen**. Select a named station to
start it immediately, with the menu staying open. Playback status, Play/Stop
(or Retry/Unmute when appropriate) and a shared Stereo volume slider remain
visible. The selected station is highlighted in a scrollable list. **Settings**
opens the advanced editor; **Your audio** opens personal listener preferences.

The Sound Desk retains its full venue editor and **Radio** tab with **Tune in**.
Both paths start live audio without show lighting. Range, spatial audio, wall
muffling, existing speaker links and saved playlists are retained. No separate
radio block or OBS session is required. All station streams are shared live
broadcasts; Play resumes the current broadcast rather than seeking backwards.

Server owners configure the station catalog in `config/noopsnet_soundstage-server.toml`
(or the world's `serverconfig` override when present):

```toml
allowedStreamHosts = ["noops.world"]
radioStations = ["Cozy Autumn | https://noops.world/soundstage/soundstage/radio-cozy-autumn.m3u8"]
```

The SERVER config is synchronized by NeoForge when players join. Reconnect after
catalog edits. Both a station URL and any URLs it redirects to must pass the
existing exact-host media policy. Invalid or unapproved stations are omitted;
the picker offers up to 32 distinct stream URLs. Empty catalogs show an
explanation and leave the normal direct-stream controls available. A station
name is at most 64 characters. Never place publishing credentials in a URL.

## Host an unattended audio station

`deploy/noopsnet-soundstage-radio@.service` loops an AAC/M4A source in real time
and writes a sliding live HLS playlist. Different receivers join the current
broadcast window; normal playback buffering can cause small differences between
listeners. Selecting a station does not seek to the beginning of the source.
The station produces no video or lighting cues and needs no MediaMTX restart.

1. Convert an authorized audio source to AAC stereo at 48 kHz in an M4A container.
   Keep original downloads and converted media outside Git and modpack archives.
2. Create the `noopsnet-radio` system user, and install the source read-only at
   `/opt/noopsnet-soundstage/radio/cozy-autumn.m4a`.
3. Install the service template, reload systemd and enable
   `noopsnet-soundstage-radio@cozy-autumn.service`.
4. Include `deploy/nginx-radio.conf` in the existing pinned HTTPS origin server
   block **before** the general Soundstage HLS proxy. Run `nginx -t` before reload.
   The website's existing fixed-path HLS proxy already accepts these filenames.
5. Add the station's public URL to the catalog and verify the live playlist and
   audio decode through `https://noops.world/`. Then test in game manually.

Use lowercase letters, numbers and hyphens for station IDs. A source
`ambient.m4a`, service instance `ambient` and URL ending `radio-ambient.m3u8`
create a separate station. Streams have a short sliding window and old segments
are deleted. Restart numbering is based on Unix time to avoid reusing cached
segment names. The service is CPU/memory bounded, reads media outside home
folders and writes only its systemd-managed cache directory.

To stop a station, disable its specific service and remove its catalog entry.
This does not stop other stations, the demo, the performer relay or Minecraft.
Do not resume world generation or paste venue changes while deploying radio.

## First source

“Cozy Autumn” uses the user-supplied local file
`cozy autumn music 🍂 lofi beats to study _ relax to [_jH2oTNRe7g].webm`.
Its source ID is `_jH2oTNRe7g`; no artist or license has been inferred from its
filename. Original video and account information are not distributed in the pack.

## Station catalog

The thirteen user-supplied mixes are listed in
[`deploy/radio-stations.json`](deploy/radio-stations.json). Each has its own
looping service and playback URL. Source IDs document which local download was
used; they do not assert an artist credit or license. The catalog is configured
on Devo while the radio UI is being player-tested. Rejoin Devo after catalog
updates so NeoForge sends the latest station list; the installed radio candidate
does not need rebuilding for new stations.

| Station | Service instance |
| --- | --- |
| Cozy Autumn | `cozy-autumn` |
| City Pop | `city-pop` |
| Synthwave Cyberpunk | `synthwave` |
| Liquid Drum & Bass | `liquid-dnb` |
| Spy Jazz & Noir | `spy-jazz` |
| Vintage Jazz & Swing | `vintage-jazz` |
| Hyperpop | `hyperpop` |
| Witch House | `witch-house` |
| Kawaii Future Bass | `kawaii-future-bass` |
| Redstone FM (chiptune) | `redstone-fm` |
| Tokyo Night (chill phonk) | `tokyo-night` |
| Slowburn (wave / phonk) | `slowburn` |
| Melodic Drum & Bass | `melodic-dnb` |

### Measured server cost

A 30-second sample on 2026-11-12, after all nine stations started and uploads
finished, measured **0.95% of one CPU core** (0.03% of the 32-thread host) and
**261 MiB of combined service memory**, including reclaimable file cache.
Audio sources occupy **1.39 GiB**; the sliding HLS cache was **4.2 MiB**.
One complete segment from each station averaged **0.217 Mbit/s per listener**,
excluding HTTP/TLS overhead. Listener traffic grows with listening clients;
this is not a large-audience capacity benchmark or an in-game tick-time test.
Both Minecraft services stayed running. See the exact
[resource sample](evidence/radio-stations-impact.json) and
[stream checks](evidence/radio-stations.json).

## Stereo UI implementation and verification

The client uses the pack's existing `owo-lib` NeoForge build
`0.12.15.6-beta.1+1.21`; Soundstage now declares it as a client dependency.
The screen follows Soundstage's dark panel/cyan palette and Minecraft text.
Volume changes are throttled to at most five packets per second while dragging
and flushed on release/close. The server's existing host, ownership, proximity
and claim checks still validate every save. Radio changes do not spam the
settings-saved action bar. The screen closes if its source block is replaced or
the player leaves editing range. Advanced settings preserve a return path.

`stereoUiProbe` checks controls, status translation and usable geometry at five
GUI sizes. `tools/render_stereo_ui.py --client-jar PATH` creates **offline layout
previews**, using `build/reports/stereo-ui-layout.json` and the local Minecraft
glyph atlas. They are not gameplay screenshots or a capture of native widgets.
No game client is launched for verification; final widget interaction and
appearance are validated by the player in Devo.
