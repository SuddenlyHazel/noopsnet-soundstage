// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;
import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import net.noopsnet.soundstage.VrslGrid;
import org.watermedia.api.player.videolan.BasePlayer;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.player.embedded.videosurface.callback.*;
import org.watermedia.videolan4j.tools.Chroma;
/** One audio pipeline per venue, with optional 512-channel monochrome grid sampling. */
final class VenuePlayer extends BasePlayer implements RenderCallback,BufferFormatCallback,BufferCleanupCallback {
    private final Semaphore lock=new Semaphore(1);
    private final boolean lighting;
    private final SpatialAudio audio;
    private volatile boolean closed;
    private volatile Frame frame;
    record Frame(long nanos,int[] channels){}
    VenuePlayer(boolean lighting){
        this.lighting=lighting;init(null,this,this,this);
        SpatialAudio output=null;
        if(raw()!=null){
            raw().mediaPlayer().videoSurface().getVideoSurface().setSemaphore(lock);
            try{output=new SpatialAudio();raw().mediaPlayer().audio().callback("S16N",48000,2,output);}
            catch(IllegalArgumentException ex){System.getLogger(VenuePlayer.class.getName()).log(System.Logger.Level.WARNING,"Spatial PCM unavailable; using native stereo",ex);}
        }
        audio=output;
    }
    String audioBackend(){return audio==null?"Native stereo":audio.backend;}
    void scene(OpenAlOutput.Scene scene){if(audio!=null)audio.scene(scene);}
    boolean spatialReady(){return audio!=null;}
    void spatial(double gain,double pan,double occlusion){if(audio!=null)audio.position(gain,pan,occlusion);else super.setVolume((int)(gain*100*(1-.65*occlusion)));}
    @Override public void setVolume(int volume){if(audio!=null)audio.position(volume/100.0,0,0);else super.setVolume(volume);}
    @Override public BufferFormat getBufferFormat(int w,int h){return new BufferFormat(Chroma.RV32,w,h);}
    @Override public void allocatedBuffers(ByteBuffer[] buffers){}
    @Override public void cleanupBuffers(ByteBuffer[] buffers){frame=null;}
    @Override public void display(MediaPlayer p,ByteBuffer[] buffers,BufferFormat f){
        if(closed||!lighting||buffers.length==0||f.getWidth()!=1920||f.getHeight()<1080||f.getHeight()>1120)return;
        if(!lock.tryAcquire())return;
        try{var b=buffers[0].duplicate();b.clear();int[] values=new int[512];for(int i=0;i<512;i++)values[i]=VrslGrid.read(b,f.getWidth(),f.getHeight(),i,VrslGrid.Layout.HORIZONTAL_TOP,VrslGrid.Transfer.IDENTITY);frame=new Frame(System.nanoTime(),values);}
        finally{lock.release();}
    }
    int[] channels(){var f=frame;return f==null||System.nanoTime()-f.nanos>1_000_000_000L?null:f.channels;}
    int color(int address){var f=frame;if(f==null||System.nanoTime()-f.nanos>1_000_000_000L)return -1;int i=address-1;int[] v=f.channels;return (v[i+1]*v[i]/255)<<16|(v[i+2]*v[i]/255)<<8|v[i+3]*v[i]/255;}
    @Override public void release(){closed=true;frame=null;if(audio!=null)audio.close();super.release();}
}
