// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

/** Real VLC/HLS callback pacing through the production output worker, silently. */
public final class AudioStreamProbe {
    public static void main(String[] args)throws Exception{
        org.watermedia.WaterMedia.prepare(org.watermedia.loaders.ILoader.DEFAULT);
        var factory=new org.watermedia.videolan4j.factory.MediaPlayerFactory("--no-video","--no-video-title-show");
        var player=factory.mediaPlayers().newMediaPlayer();var audio=new SpatialAudio();
        try{
            audio.scene(new OpenAlOutput.Scene(true,java.util.List.of(new OpenAlOutput.Emitter(0,0,-2,0,0))));
            player.audio().callback("S16N",48000,2,audio);
            if(!player.media().play(java.net.URI.create(args.length==0?"https://noops.world/soundstage/demo/index.m3u8":args[0])))throw new AssertionError("Stream rejected");
            for(int i=0;i<12;i++){Thread.sleep(1000);System.out.println("STREAM "+i+" backend="+audio.backend+" received="+audio.receivedBytes+" written="+audio.writtenBytes+" overflow="+audio.droppedPackets+" late="+audio.latePackets);}
            if(audio.writtenBytes<192000*7||audio.droppedPackets!=0||audio.latePackets!=0)throw new AssertionError("Audio lost or output stalled");
            System.out.println("PASS: live music PCM flows continuously without overflow or late drops (muted output).");
        }finally{player.controls().stop();player.release();audio.close();factory.release();}
    }
}
