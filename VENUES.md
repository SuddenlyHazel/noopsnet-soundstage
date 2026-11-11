# Soundstage venues — 0.3.0-alpha.1 / community alpha.32

Install the matching client pack and restart Minecraft. This release adds blocks
and changes the editing protocol; alpha.31 clients must update to join.

## Build and patch

1. Place a Sound Desk, speakers, Stage Lights, RGB Lasers, Fog Machines or Particle
   Machines. All are in the Soundstage creative tab and have crafting recipes.
2. Select the desk with the Linking Tool, then click each fixture or speaker.
   Sneak-click equipment to unlink it. You must own equipment or be an operator,
   and have permission to use both the desk and fixture claims.
3. Open the desk's **Venue** tab and choose **Auto patch ALL**. This assigns
   non-overlapping addresses using each fixture's actual channel count. It
   deliberately replaces your old manual addresses; export again after changes.
4. In **Patches**, give fixtures recognizable names and groups. **Identify** marks
   the selected fixture with a sparkle for ten seconds. **Apply fixture** saves
   its name/address/group; **Apply entire group** sets one shared address and group.
5. Set the stream in **Audio**, enable lighting, and play your performance.
   The server music demo remains available; its original RGB cues are not a
   bespoke moving-laser show. Use QLC+ faders to check each new fixture channel.

Speakers need no DMX patch: linking is their complete setup. Up to 32 can be
linked; the closest eight loaded speakers contribute 3D sound. Without linked
speakers the desk is the emitter. Range and volume are desk controls.

## Deliberate Afterlight strips and panels

Link a starting Afterlight block and put it in a named group, e.g. `Balcony`.
In Venue, enter that same group and a free starting DMX address. **Collect
connected blocks** includes face-adjacent Afterlight blocks within 64 blocks of
this desk, without crossing other groups or denied claims. This immediately
patches the collected group; it does not modify block colors permanently.

**Pixels along X/Y/Z** assigns a separate dimmer+RGB slot to each member, sorted
along that axis within deterministic rows. X order is Y, then Z, then X; Y order
is X, Z, Y; Z order is Y, X, Z. This works for strips and row-major panels.
**Reverse group order** reverses the address sequence. **Group shares one cue**
sets all members to the starting address. Use separate groups for segments.
Only explicit collection/linking changes membership; later adjacent construction
is not automatically added. Unlink a member through Patches or the linking tool.

Patches are persistent. An operation that would exceed 512 channels, 64 fixtures,
or cross a denied claim changes nothing. Group arrangement rejects overlaps with
other groups. Automatic patching considers all fixtures and removes overlaps.

## Export to QLC+

Click **Export QLC+ show patch** in Venue. The client writes a timestamped folder
under `minecraft/soundstage-exports/` containing:

- `venue.qxw`: a QLC+ workspace with named fixtures and their DMX patch.
- Four `.qxf` fixture definitions for RGB lights, lasers, fog and particles.
- `patch.csv`: names, groups, addresses, channel counts and world coordinates.
- `README.txt`: import and signal-routing instructions.

Install the definitions through QLC+'s Fixture Definition Editor/user fixture
library, restart QLC+, then open the workspace. Program Scenes, Chasers, RGB
Matrices or Shows in QLC+. Set Universe 1's Art-Net output to HNode and match its
first universe. Capture the 1920×1080 horizontal-top monochrome grid in OBS with
your performance audio. Use the existing [relay instructions](https://noops.world/soundstage.html).
QLC+ uses zero-based addresses internally; the game and CSV show 1-based addresses.
Export never includes the stream URL, publishing credentials, or a recorded show.
Definitions are validated against QLC+'s upstream schema; opening/programming the
workspace in the QLC+ GUI remains an external-tool acceptance check.

## Fixture channels

| Fixture | Channels, starting at its assigned address |
| --- | --- |
| Stage Light / Afterlight pixel | dimmer, red, green, blue |
| RGB Laser | dimmer, red, green, blue, pan, tilt |
| Fog Machine | output, spread |
| Particle Machine | output, red, green, blue |

All channels use 0–255. Laser pan covers -180° to +180° relative to the placed
block's facing; tilt runs from up (0), through level (127/128), to down (255).
Beams stop at solid collisions and extend at most 32 blocks. Fog uses drifting
smoke particles; particles use colored dust. This is not volumetric haze or
vector-image laser projection. Existing shaders may alter the appearance, so
check the scene in game. Brightness and `/soundstagelights` apply locally; stale
control data stops machines and lasers instead of latching them on. Particles
already emitted fade naturally. Particle emission is capped at 32 per client tick.

## Spatial audio

The bundled LWJGL/OpenAL Soft stack renders independent positioned emitters using
one shared VLC decode and presentation clock. It requests HRTF when available,
uses listener orientation and height, and applies per-speaker obstruction using
three collision rays and OpenAL low-pass filtering. Speaker gains are normalized
so adding many speakers does not multiply the venue's loudness. Doppler is off.
There is no modeled room reverb or acoustic propagation delay.

Spatial mode downmixes the performance to mono for physical point-source speakers.
Turn **3D audio off** to retain the original stereo mix. If OpenAL initialization
is unavailable, Java Sound stereo panning/muffling is the fallback. The Audio tab
reports the active backend. Headphones are recommended for HRTF. No additional
spatial-audio installation is required; existing WATERMeDIA/VLC requirements stay.

## Verification

Automated checks cover native server linking, permissions, persistence, mixed
fixture footprints, overflow rollback, reverse ordering, network roundtrip and
XML escaping. QXF files pass QLC+'s published schema. A silent native OpenAL
probe passes on the host and inside the Prism Flatpak sandbox with HRTF enabled,
including streaming, filters, stereo switching, pause, flush and disposal.
Manual Minecraft appearance/listening and QLC+ GUI checks remain unverified.

Sources: [OpenAL Soft](https://openal-soft.org/),
[LWJGL context isolation](https://github.com/LWJGL/lwjgl3/blob/master/modules/lwjgl/openal/src/main/java/org/lwjgl/openal/AL.java),
[QLC+ fixture editor](https://docs.qlcplus.org/v5/fixture-definition-editor),
[QLC+ schema](https://github.com/mcallegari/qlcplus/blob/master/resources/schemas/fixture.xsd).
