// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Dependency-free regression runner; does not claim capture or playback verification. */
public final class GridProbe {
    private static void check(boolean ok) { if (!ok) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        int[] cue=new int[512];cue[0]=255;cue[1]=255;
        var frameHold=new LightingFrame(0,cue);
        check(frameHold.effects(500_000_000L)==cue);
        check(frameHold.effects(1_500_000_000L)==null); // stale lasers/fog stop
        check(frameHold.rgb(1_500_000_000L)==cue); // RGB must not flash saved warm light
        check(frameHold.rgb(3_000_000_001L)==null); // configured fallback still works
        check(new LightingFrame(1_600_000_000L,new int[512]).rgb(1_600_000_000L)[0]==0); // blackout immediate
        var pcm=new PcmQueue(48000*4*4);
        // 1.5 seconds of 48 kHz AAC callback blocks: the old 32-chunk queue lost most of these.
        for(int i=0;i<72;i++)check(pcm.offer(new PcmQueue.Packet(new byte[1024*4],i*1024_000_000L/48000,7))==0);
        check(pcm.bytes()==72*4096);
        for(int i=0;i<72;i++){var packet=pcm.poll(1);check(packet.generation()==7&&packet.pts()==i*1024_000_000L/48000&&packet.samples().length==4096);}
        check(pcm.bytes()==0&&pcm.poll(1)==null);
        var bounded=new PcmQueue(12);check(bounded.offer(new PcmQueue.Packet(new byte[8],1,0))==0);check(bounded.offer(new PcmQueue.Packet(new byte[8],2,0))==1);check(bounded.bytes()==8&&bounded.poll(1).pts()==2);bounded.clear();check(bounded.bytes()==0);

        var slow=new LightSmoother();check(slow.advance(0,0,500)==0);
        int previous=0;for(int i=0;i<180;i++){int current=slow.advance(0x050505,1000.0/60,500);check(current>=previous);previous=current;}check(previous==0x050505);
        var lowFps=new LightSmoother();var highFps=new LightSmoother();lowFps.advance(0,0,500);highFps.advance(0,0,500);
        int low=0,high=0;for(int i=0;i<30;i++)low=lowFps.advance(0xffa040,1000.0/30,500);for(int i=0;i<144;i++)high=highFps.advance(0xffa040,1000.0/144,500);check(low==high);
        check(slow.advance(0x123456,0,0)==0x123456);check(slow.advance(0x123456,1000,500)==0x123456);
        var tracks=java.util.List.of(new PlaylistTrack("a","https://example.test/a",2),new PlaylistTrack("b","https://example.test/b",3));
        check(PlaylistTimeline.resolve(tracks,0,2500,false).equals(new PlaylistTimeline.Position(1,500,false)));
        check(PlaylistTimeline.resolve(tracks,1,3500,true).equals(new PlaylistTimeline.Position(0,500,false)));
        check(PlaylistTimeline.resolve(tracks,0,10500,true).equals(new PlaylistTimeline.Position(0,500,false)));
        check(PlaylistTimeline.resolve(tracks,0,5000,false).ended());
        check(PlaylistTimeline.resolve(java.util.List.of(),0,0,true).ended());
        check(Arrays.equals(AudioSpatializer.gains(1,-1),new double[]{1,0}));
        check(Arrays.equals(AudioSpatializer.gains(1,1),new double[]{0,1}));
        byte[] tone=new byte[4800*4];
        for(int i=0;i<4800;i++){short v=(short)(16000*Math.sin(2*Math.PI*8000*i/48000));for(int c=0;c<2;c++){tone[i*4+c*2]=(byte)v;tone[i*4+c*2+1]=(byte)(v>>8);}}
        byte[] clear=tone.clone(),wall=tone.clone(),muted=tone.clone(),left=tone.clone();
        new AudioSpatializer().process(clear,1,0,0);new AudioSpatializer().process(wall,1,0,1);new AudioSpatializer().process(muted,0,0,0);new AudioSpatializer().process(left,1,-1,0);
        double clearPower=0,wallPower=0;for(int i=0;i<tone.length;i+=4){int a=(short)((clear[i]&255)|(clear[i+1]<<8)),b=(short)((wall[i]&255)|(wall[i+1]<<8));clearPower+=(double)a*a;wallPower+=(double)b*b;check(left[i+2]==0&&left[i+3]==0);}
        check(wallPower<clearPower*.01);check(Arrays.equals(muted,new byte[muted.length]));
        var hosts=java.util.List.of("relay.example.com","127.0.0.1");
        check(StreamPolicy.allowed("https://relay.example.com/show/index.m3u8",hosts));
        check(!StreamPolicy.allowed("https://relay.example.com.evil.test/show",hosts));
        check(!StreamPolicy.allowed("https://user:secret@relay.example.com/show",hosts));
        check(!StreamPolicy.allowed("file:///etc/passwd",hosts));
        check(!StreamPolicy.allowed("https://other.example.com/show",hosts));
        check(StreamPolicy.gain(0,16)==1 && StreamPolicy.gain(16,16)==0 && StreamPolicy.gain(8,16)==.5);

        check(Arrays.equals(VrslGrid.center(0, 1920, 1080, VrslGrid.Layout.HORIZONTAL_TOP), new int[]{8, 8}));
        check(Arrays.equals(VrslGrid.center(12, 1920, 1080, VrslGrid.Layout.HORIZONTAL_TOP), new int[]{8, 200}));
        check(Arrays.equals(VrslGrid.center(13, 1920, 1080, VrslGrid.Layout.HORIZONTAL_TOP), new int[]{24, 8}));
        check(Arrays.equals(VrslGrid.center(512, 1920, 1080, VrslGrid.Layout.HORIZONTAL_TOP), new int[]{648, 8}));
        check(Arrays.equals(VrslGrid.center(0, 1920, 1080, VrslGrid.Layout.VERTICAL_RIGHT), new int[]{1720, 1072}));
        ByteBuffer frame = ByteBuffer.allocate(1920 * 1080 * 4);
        // Independent asymmetric fixture: cell 0 at top-left, green only.
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            frame.put((y * 1920 + x) * 4 + 1, (byte) 128);
        check(VrslGrid.read(frame, 1920, 1080, 0, VrslGrid.Layout.HORIZONTAL_TOP, VrslGrid.Transfer.IDENTITY) == 128);
        check(VrslGrid.read(frame, 1920, 1080, 1, VrslGrid.Layout.HORIZONTAL_TOP, VrslGrid.Transfer.IDENTITY) == 0);
        check(VrslGrid.read(frame, 1920, 1080, 0, VrslGrid.Layout.HORIZONTAL_TOP, VrslGrid.Transfer.LINEAR_TO_SRGB) == 188);
        try { VrslGrid.center(1536, 1920, 1080, VrslGrid.Layout.HORIZONTAL_TOP); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
        try { VrslGrid.read(ByteBuffer.allocate(1), 1920, 1080, 0, VrslGrid.Layout.HORIZONTAL_TOP, VrslGrid.Transfer.IDENTITY); throw new AssertionError(); }
        catch (IllegalArgumentException expected) { }
        System.out.println("GridProbe: all geometry, transfer, isolation and bounds checks passed");
    }
}
