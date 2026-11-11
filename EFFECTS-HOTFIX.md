# Effects hotfix — 0.3.0-alpha.3 / community alpha.34

The player reported missing fog, a black laser, striped fixture faces and brief
warm lighting flashes with Afterlight/Photon enabled. This is a client update;
protocol 4 and the alpha.32 server remain compatible.

## Changes and evidence

- Laser and stage lenses now use Minecraft's emissive-eyes material, an explicit
  white texture and flat LabPBR maps, and complete UV/overlay/light/normal vertex
  data. Photon's lightning pass samples a texture and special-cases lightning
  colors, making the old position/color-only geometry unsuitable. Beam winding
  faces outward, halo opacity is fixed above Photon's cutoff, and dimming changes
  RGB. Blackout skips laser geometry.
- Fog uses larger vanilla cloud particles with a four-second lifetime, emitted
  through the front rather than inside the housing/top edge. This supports low
  ceilings like the player's installation. The 32-particles-per-tick global cap,
  48-block range and reduced/minimal particle preferences remain enforced. This
  replaces the suspected emission/visibility failure; its appearance still
  needs the player's in-game check.
- All four fixture models had overlapping panel/lens north faces and overlapping
  housing/rim tops. The old fog model reproduces both coplanar intersections.
  Separate rim pieces and an inset lens remove them. The live stage lens matches
  the inset dimensions.
- Decoder buffer cleanup no longer immediately discards the copied DMX array.
  RGB holds its last frame for up to three seconds during interruptions, then
  uses the chosen fallback. Lasers/fog/particles still stop after one second of
  stale data; a received blackout and stopping the desk act immediately. Each
  rendered venue reads one shared snapshot. This prevents one known route to a
  transient saved warm color; it does not prove all reported warm flashes fixed.
- `/soundstagestatus` reports particle preference, incoming fixture channels and
  particle creation counts, without URLs or account data.

## Verification

`gradlew build effectsGeometryProbe` passes. The standalone geometry probe uses
Minecraft's actual vertex buffer builder and validates RGB, full-bright lightmap,
normals, outward faces and halo/core alpha for horizontal, vertical and diagonal
beams. GridProbe covers the stale-data grace period, outage and immediate blackout.
`python3 tools/check_models.py` rejects the previous model and passes all four
corrected fixtures. All 18 existing Afterlight shader validation cases compile.
No Minecraft client was launched or controlled. Player visual validation remains
pending. The audio queue fix from alpha.33 is unchanged.
