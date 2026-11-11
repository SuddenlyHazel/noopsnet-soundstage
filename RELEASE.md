# Soundstage 0.3.0-alpha.4

Community alpha.35 includes Afterlight 0.5.3 with shader 0.4.4. Install those two
together: the lighting-volume format changed. The optional Iris compatibility
hook routes translucent fog through the correct shader, and corner sampling no
longer exposes phantom warm blocklight. Normal-less particles receive valid RGB
lighting. The particle machine now emits a longer, denser colored spark fountain;
the server demo includes dim white fill during fog/particle sections. Client-only
update, compatible with the current server. Restart Minecraft.

# Soundstage 0.3.0-alpha.3

Community alpha.34 corrects laser/stage-lens rendering with Photon, removes
overlapping fixture faces, and emits denser fog from the front nozzle so low
ceilings do not hide it. RGB cues survive short decoder interruptions without
briefly restoring saved warm light. `/soundstagestatus` reports live fixture
values and particle creation counts. Compatible with the alpha.32 server;
restart the client. In-game appearance still needs player validation.

# Soundstage 0.3.0-alpha.2

Community alpha.33 fixes severe audio queue loss with real VLC/HLS streams.
OpenAL/HRTF and all venue features remain enabled. Compatible with the alpha.32
server; restart the client. [Cause and regression evidence](AUDIO-HOTFIX.md).

# Soundstage 0.3.0-alpha.1

Community alpha.32 adds performance-controlled RGB lasers, fog and colored-particle
machines; OpenAL 3D/HRTF audio; automatic mixed-fixture patching; named fixtures;
QLC+ workspace/fixture export; and explicit addressable Afterlight assemblies.
See [VENUES.md](VENUES.md) for setup, channel maps, limits and validation scope.

The matching server and client are required. Existing worlds, saved desks,
playlists, groups and links remain compatible; update the client and restart.
Afterlight 0.5.2 and the 0.4.3 shader remain included, retaining the previous
[lighting hardening](LIGHTING-HARDENING.md).
