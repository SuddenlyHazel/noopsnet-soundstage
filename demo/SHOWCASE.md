# Server demo: one-minute venue test

The existing **Play server demo** button now plays a dedicated mixed-fixture test.
The music is still the credited “Dream Culture” recording. No modpack update is
needed beyond alpha.32. The broadcast is continuous, so joining starts somewhere
within the loop, not necessarily at the first section.

## Build this exact test patch

Use a **fresh desk** so other linked lights do not change the automatic addresses.
Place the following fixtures in a straight line along **increasing X** (east),
all at the **same Y and Z**. Leave open space in front of the laser and face it
into that space when placing it. The desk and speakers may sit elsewhere.

| X offset | Fixture | Starting address |
| --- | --- | --- |
| 0 | Stage Light | 1 |
| 1 | RGB Laser | 5 |
| 2 | Fog Machine | 11 |
| 3 | Particle Machine | 13 |
| 4–11 | Eight Afterlight blocks, in order | 17, 21, 25, 29, 33, 37, 41, 45 |

Select the desk with the Linking Tool and link all twelve fixtures. Open
**Desk → Venue → Auto patch ALL**. The addresses should match this table.
Alternatively, set these addresses individually in **Patches**; placement then
need not follow the row. Do not link extra lights into this test desk.

Link one or two speakers, turn **3D audio** on, and choose **Play server demo**.
Place an additional **unlinked** Afterlight block nearby as a static-light control.
For the first check, use `/soundstagebrightness 100`, `/soundstagesmoothing 0`,
and ensure local lights and audio are enabled. Existing smoothing can otherwise
soften the strip chase and blackouts.

## What should happen

| Loop time | Expected result |
| --- | --- |
| 0–10 s | Stage Light and all eight pixels show red, green, blue, then white |
| 10–20 s | Laser changes color and sweeps horizontally and vertically; other effects off |
| 20–30 s | Fog outputs two bursts with increasing spread; particles already emitted linger |
| 30–40 s | Colored particles pulse on alternating seconds |
| 40–50 s | One Afterlight pixel at a time chases toward increasing X |
| 50–58 s | All fixture types run together with a moving pixel gradient |
| 58–60 s | All controlled light/output channels are zero; existing particles fade naturally |

The unlinked Afterlight block should keep its saved appearance throughout. The
music stays continuous through every section, including blackout. Walk around a
speaker, turn your head, change height, and put a wall between you and the speaker
to check spatial audio. **3D audio off** restores the original stereo presentation.
A wall placed in the laser path should stop the beam. Disable desk lighting to
stop the effects and restore linked Afterlight blocks to their saved appearance.

## Export this test venue

**Desk → Venue → Export QLC+ show patch** writes a timestamped directory under
`minecraft/soundstage-exports/`. Install its four `.qxf` files in QLC+'s user fixture
library, restart QLC+, and open `venue.qxw`. The named fixtures and addresses are
already assigned. `patch.csv` records groups and world coordinates; `README.txt`
covers Art-Net → HNode → OBS routing.

This export is your venue patch, not the server's generated demo animation. Author
Scenes/Chasers/Shows in QLC+, or use its channel faders to test individual fixtures.
Fixture definitions pass QLC+'s XML schema; the GUI import/programming workflow
still needs acceptance testing.
