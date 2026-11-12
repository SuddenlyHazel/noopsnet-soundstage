# NoopsNet Soundstage

Placeable spatial audio systems, shared playlists and streamed stage lighting for NeoForge.

Official NoopsNet source: https://noops.world/

## Build

Use JDK 21. The Gradle wrapper downloads pinned Minecraft/loader development dependencies.

```sh
./gradlew -p . build --console=plain
```


## Licensing and provenance

Original code is MPL-2.0; see [LICENSE](LICENSE). Artwork and third-party components are covered separately in [NOTICE.md](NOTICE.md). [Published artifact provenance](PROVENANCE.json) identifies the original pack binaries covered by our permission grant. Historical binaries may retain an older All Rights Reserved metadata label; the explicit grant in NOTICE.md also applies to those identified versions.

This repository starts with a reviewed source snapshot. It intentionally contains no private server history, player data, accounts or deployment configuration. The source snapshot differs from the original binary build in license metadata and standalone build wiring; its build is not claimed byte-identical.

## Play and perform

[Player setup and music credit](https://noops.world/soundstage.html).

Use the sound desk tabs for Audio, Playlist, Lighting and Patches. A linking tool connects the desk to speakers, stage lights and Afterlight fixtures. Live shows use OBS + HNode with an ordinary RTMP/MediaMTX relay; set a trusted public HTTP(S) playback host in the server config. Playlists need direct seekable audio URLs and accurate lengths in seconds.

This is an alpha; live multiplayer testing and cross-platform playback remain hands-on follow-up.

## Venue release 0.3.0-alpha.1

See [venue setup, fixture channels and QLC+ export](VENUES.md).

## Version 0.3.0-alpha.5

Fixtures now support [individual manual controls](MANUAL-FIXTURES.md), including standalone fog machines. QLC+ venue exports include a measured stage layout and fixture groups, and laser smoothing can be adjusted per listener. The paired Afterlight 0.5.4 and Photon shader provide source-first volumetric haze.
