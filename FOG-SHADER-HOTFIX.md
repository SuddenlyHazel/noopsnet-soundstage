# Fog and corner-lighting fix — community alpha.35

Includes Soundstage 0.3.0-alpha.4, Afterlight 0.5.3 and Afterlight/Photon 0.4.4.
The new Afterlight mod and shader must be installed together. Minecraft server
registries and protocol are unchanged; the existing server remains compatible.

## Evidence and changes

The player confirmed colored dust worked but fog did not. In the actual pinned
Iris NeoForge 1.8.14-beta.1 JAR, `fantastic.MixinParticleEngine` contains a hook for
selecting `PARTICLES_TRANS`, but `mixins.iris.fantastic.json` does not register it.
The active generic particle override instead uses `PARTICLES`. Vanilla cloud
particles use the translucent pass; dust uses the opaque pass. The new optional
Afterlight mixin selects Iris's translucent shader for NeoForge's translucent-only
particle predicate, preserving opaque, shadow and shader-disabled behavior.
It is disabled entirely without Iris. Binary ABI tests check the actual engine
call site, missing upstream registration, selector and callback descriptor.
An [upstream report](https://github.com/IrisShaders/Iris/issues/2499) also describes
invisible particles on this platform, but the installed binary is our evidence.

The two supplied screenshots showed warm bands following wall/ceiling corners.
Astra reproduced the defect using an actual CPU-generated lighting volume and
GLSL on the local RTX 4080: ordinary trilinear filtering mixed air light with
opaque zero cells, reducing replacement coverage and exposing vanilla warm light.
Opaque cells now use an alpha -1 marker; the shader excludes those cells from its
interpolation. Dark air remains included, preserving reach limits and wall
blocking. Normal-less particle passes no longer use an invalid TBN normal for
volume sampling.

The GPU corner fixture's blackout leak changes from
`[0.186095, 0.093047, 0.046524]` to `[0, 0, 0]`. Green radiance variation along the
corner falls from `1.140351` to less than `0.000001`. Closed walls, cross-fades,
invalid particle normals and prior temporal regression checks pass. All 17 Java
tests, 24 desktop shader stages, and the targeted Mac high+DH compile check pass.
This is an offscreen rendering reproduction, not a gameplay screenshot/test.

## Performance effects

The particle machine emits up to eight particles per tick instead of two, mixing
colored firework sparks with fine dust. Explicit velocities produce longer arcs
and lifetimes are capped below three seconds. Firework trails remain disabled;
all initial emission shares the existing 32-particles-per-tick global budget.
Output/RGB channel assignments are unchanged. First successful emissions are
logged once per linked effect session for diagnosis.

The live demo now keeps a dim neutral fill during its fog and particle sections.
Its encoded H.264 grid passes all 512 channels across 60 sampled frames with a
maximum error of 2. The demo broadcast was restarted; the Minecraft server was not.

Player validation of fog visibility, fountain appearance and corner lighting is
still required. Prior emission-position and short-frame-hold changes alone did
not fix the underlying rendering issues; this release addresses those paths.
