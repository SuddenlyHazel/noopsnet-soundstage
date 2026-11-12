# Fog that reads as a stage effect

The Devo mist pass makes three things distinct: a dense forward nozzle plume,
a visible outward fill, and room haze that accumulates while a show runs.
It retains the bounded air-space grid rather than adding a fluid solver.

## Player-visible behavior

- The plume follows the machine's physical facing direction and widens away
  from the nozzle, with traveling puffs and a slight upward curve.
- The room-fill front travels about three blocks per second along connected
  air spaces. Walls still block it and doorways provide a route.
- Output deposits new haze into the previous density field. Continuous output
  visibly thickens the room over 30–60 seconds, and repeated bursts retain and
  add to earlier haze instead of restarting a fixed-density target.
- Density falls with distance, keeping the nozzle plume distinguishable from
  background haze. Moving world-space billows and finer wisps add visible motion.
- After emission stops, existing haze fades progressively, with roughly a
  six-second half-life. It does not disappear at the same instant as the cue.
- Output controls production and plume strength. Spread controls room reach
  (10–32 blocks along air paths) and the widening jet. Overlapping emitters add
  density, with a protective cap to keep the effect bounded.

This is a client-local visual effect. It is not persistent server weather and
has no wind, pressure, temperature or ventilation simulation. The shader still
uses the shared mean-density grid for stable lighting transmission; animated
wisps modulate the visible fog without adding flickering surface shadows.

## Integration and cost

Soundstage prefers the optional `submitDirected` bridge, passing nozzle XYZ,
output, spread and facing XZ. Afterlight retains the legacy five-value `submit`
entry point; Soundstage falls back to that API with older Afterlight versions.
Emitter clocks are keyed by nozzle position and survive input reordering.

The simulation remains a 64³ grid, capped at eight nearby fog sources and four
updates per second on one worker. No extra volumetric raymarch steps were added.
The visible fog shader adds one 3D noise lookup per sample. The single-emitter
preview scene measured about 2.3 ms per CPU update on the development machine;
that number is not a whole-game or cross-device benchmark.

## Verification

`RoomFogVolumeTest` checks directional plumes, accumulation after 5/30/60 seconds,
repeated bursts, visible front progression, solid walls/door paths, fading,
camera rebasing, invalid inputs and legacy/directed bridge ages.

Run `./gradlew fogPlumePreview` in the Afterlight project to export actual Java
fields at 0.25, 2, 8, 30 and 60 seconds. Then run Afterlight's
`tools/render_fog_plume.py` with the existing moderngl/NumPy/Pillow environment.
It renders those fields through the shipped fog shader on offscreen EGL at fixed
exposure. Its images are synthetic renders, never gameplay captures.
The existing `test_spotlight_transport_gpu.py` checks transport, occlusion,
source additivity and temporal stability independently.

## Manual Devo check

Use one machine with clear space in front of the nozzle. Set Output 70–100% and
Spread around 50–80%, turn it on and choose **Use manual**. Watch from the side
with a stage light shining through the fog. The first few seconds should show a
forward plume, then an expanding haze. Compare the room at roughly 10, 30 and
60 seconds, then turn the machine off and save the manual controls to check the
fade. Finally try a pulsed show to check cumulative haze between bursts.
The player performs this gameplay validation; development does not launch or
control their Minecraft client.

## Personal density scale

The default personal Fog density remains **100%**, now rendered at 1.5× the
previous baseline. The slider spans **0–300%**, corresponding to optical
multipliers **0–4.5×** the old baseline. CPU fog production, buildup timing,
spread and per-cell caps remain unchanged; the multiplier applies consistently
to visible fog and light transmission.

The old saved default 100 stays 100 and receives the stronger baseline. Other
legacy personal values are translated to retain their appearance (old 200
becomes 133; old 150 becomes 100). Saving writes density scale version 2 so
preferences do not migrate repeatedly. Reset returns to the new 100% standard.
