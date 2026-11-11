# Audio buffering hotfix — Soundstage 0.3.0-alpha.2 / community alpha.33

The alpha.32 OpenAL adapter split VLC callbacks into short packets but allowed only
32 queued packets. The public HLS demo delivers PCM roughly 1.2 seconds ahead of
its presentation clock; that queue held only about a third of a second. The worker
waited for presentation while incoming audio continuously evicted pending data.

Reproduced in a separate silent native probe inside the same Prism Flatpak:

| 12-second live-demo run | Before | After |
| --- | ---: | ---: |
| PCM bytes received | 2,437,120 | 2,437,120 |
| PCM bytes submitted to output | 49,920 | 2,211,840 |
| Dropped packets | 1,144 | 0 |
| Late packets | 0 | 0 |

The remaining received bytes after the fixed run are normal future-dated PCM,
not lost data. Output advances at approximately 192,000 bytes per second (48 kHz,
16-bit stereo). OpenAL/HRTF remains enabled.

The fix retains original decoder callback blocks and bounds pending PCM by four
seconds of sample data (768,000 bytes), independent of packet size. It still
presents audio on the VLC clock; four seconds is a memory limit, not added delay.
Pause, flush, generation checks and overload recovery remain bounded.

The pure regression covers a 1.5-second decoder burst, packet ordering and exact
PTS preservation, plus bounded overload eviction and clearing. `AudioStreamProbe`
exercises the production callback and output worker with real HLS music but muted
output, and fails if packets are dropped, late, or output stalls. The prior probe
only exercised native output initialization and therefore missed this bug.

This is a client-side fix compatible with the alpha.32 server. Restart Minecraft
to load it. In-game listening confirmation remains the user's follow-up.
