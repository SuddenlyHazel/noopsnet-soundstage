// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.util.List;

/** Muted native regression: the mixer must keep playing during short Java stalls. */
public final class OpenAlJitterProbe {
    public static void main(String[] args) throws Exception {
        try (var output = new OpenAlOutput()) {
            var scene =
                    new OpenAlOutput.Scene(true, List.of(new OpenAlOutput.Emitter(0, 0, -2, 0, 0)));
            byte[] pcm = new byte[4096];
            for (int stall : new int[] {60, 100, 160, 200}) {
                while (output.ready()) output.write(pcm, scene);
                long before = output.queuedMicros();
                Thread.sleep(stall);
                long remaining = output.queuedMicros();
                System.out.println(
                        "STALL "
                                + stall
                                + "ms queued_before="
                                + before
                                + "us remaining="
                                + remaining
                                + "us");
                if (remaining < 20_000)
                    throw new AssertionError("Mixer starved during " + stall + "ms stall");
                if (before > 350_000) throw new AssertionError("Unbounded output queue");
            }
            output.flush();
            if (output.queuedMicros() != 0)
                throw new AssertionError("Seek did not clear queued PCM");
            System.out.println(
                    "PASS: mixer stays fed through 60–200ms worker stalls; seek flush clears it.");
        }
    }
}
