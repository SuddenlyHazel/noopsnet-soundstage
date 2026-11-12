# Manual fixture controls

Right-click a Soundstage stage light, laser, fog machine or particle machine to
open its own controls. A fixture does not need a sound desk or an active show.
The linking tool retains its existing behavior, and stereos, desks and speakers
keep their existing controls.

Choose an output level and use **Power** to set on or off. Lights, lasers and
particle machines accept a six-digit RGB color. Lasers also accept pan and tilt
in degrees; fog machines accept a spread percentage. Stage lights can adjust
their existing physical up/down aim. Select **Use manual** to save the values
and take control of that fixture. A manual fixture set to off stays off even if
its linked show sends a cue. **Return to show** gives control back to the linked
show; an unlinked fixture then has no show source. The saved manual values are
kept for the next time manual control is selected.

Old fixtures default to show control, so upgrading does not turn them on or
alter a venue's existing patch. The server stores manual values on the fixture
and synchronizes them to nearby clients. Local listener settings, including
lighting, brightness, fog, lasers and particle density, still apply to what
each person sees. Manual output is not a networked 20-tick DMX stream; clients
reuse a cached channel snapshot until settings or the fixture address changes.

The fixture owner, its authorized operators and server operators can edit it
when they can build at the block. A trusted operator of a linked desk may also
edit its fixture when the linked source is loaded, the patch still matches,
and both locations permit the action. The server checks proximity and claim
permissions again when a setting is saved. If the fixture changes or is replaced
while its screen is open, reopen it before saving. Version 5 of the Soundstage
payload protocol requires the matching client and server builds.

To give someone access to a standalone fixture, look at it and use
`/soundstage trust <player>`. Its owner can remove that access with
`/soundstage untrust <player>`. The same commands continue to manage trusted
operators when aimed at a desk or stereo.

The standalone `manualFixtureProbe` checks settings, persistence encoding,
payload round trips, valid ranges, channel layouts, blackout and cache lifetime
without opening Minecraft. Actual client UI, multiplayer claim integrations and
in-game visual behavior require player verification.
