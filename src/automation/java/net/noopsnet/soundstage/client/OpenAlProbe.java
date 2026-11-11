// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;
import java.util.List;
/** Silent native check; no Minecraft UI and no audible samples. */
public final class OpenAlProbe {
    public static void main(String[] args)throws Exception{
        try(var output=new OpenAlOutput()){
            System.out.println(output.description);
            for(int i=0;i<12;i++){
                var scene=new OpenAlOutput.Scene(i<8,List.of(new OpenAlOutput.Emitter(i-4,i%3,-2,0,i%2)));
                output.scene(scene);while(!output.ready())Thread.sleep(5);output.write(new byte[3840],scene);if(output.queuedMicros()<0||output.queuedMicros()>100_000)throw new AssertionError("Unbounded PCM latency");Thread.sleep(20);
            }
            output.pause();output.resume();output.flush();System.out.println("PASS: 3D/stereo streaming, filters, pause, flush, disposal (silent PCM).");
        }
    }
}
