// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.*;
import org.watermedia.videolan4j.player.base.callback.DefaultAudioCallbackAdapter;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.binding.lib.LibVlc;
import net.noopsnet.soundstage.AudioSpatializer;

/** VLC remains the decoder/clock; Java Sound presents a bounded queue of spatialized PCM. */
final class SpatialAudio extends DefaultAudioCallbackAdapter implements AutoCloseable {
    private record Packet(byte[] samples,long pts,int generation){}
    private final ArrayBlockingQueue<Packet> queue=new ArrayBlockingQueue<>(128);
    private final SourceDataLine line;
    private final AudioSpatializer dsp=new AudioSpatializer();
    private final Thread worker;
    private volatile boolean closed,paused;
    private volatile int generation;
    private volatile double gain,pan,occlusion;
    SpatialAudio() throws LineUnavailableException {
        super(4);
        var format=new AudioFormat(48000,16,2,true,false);
        line=AudioSystem.getSourceDataLine(format);line.open(format,8192);line.start();
        worker=new Thread(this::run,"Soundstage spatial PCM");worker.setDaemon(true);worker.start();
    }
    void position(double volume,double balance,double obstruction){gain=volume;pan=balance;occlusion=obstruction;}
    @Override protected void onPlay(MediaPlayer player,byte[] samples,int count,long pts){
        if(closed||samples.length>192000)return;
        var packet=new Packet(samples,pts,generation);
        if(!queue.offer(packet)){queue.poll();queue.offer(packet);}
    }
    private void run(){
        int dspGeneration=-1;
        try{
            while(!closed){
                var packet=queue.poll(100,TimeUnit.MILLISECONDS);if(packet==null)continue;
                while(!closed&&packet.generation==generation){
                    long queuedMicros=(line.getBufferSize()-line.available())*1_000_000L/192000;
                    long wait=packet.pts-LibVlc.libvlc_clock()-queuedMicros;
                    if(!paused&&wait<=1000)break;
                    Thread.sleep(paused?10:Math.min(10,Math.max(1,wait/1000)));
                }
                if(closed||packet.generation!=generation)continue;
                if(packet.pts>0&&LibVlc.libvlc_clock()-packet.pts>250_000){line.flush();continue;}
                if(dspGeneration!=packet.generation){dsp.reset();dspGeneration=packet.generation;}
                dsp.process(packet.samples,gain,pan,occlusion);
                int offset=0;while(!closed&&packet.generation==generation&&offset<packet.samples.length)offset+=line.write(packet.samples,offset,packet.samples.length-offset);
            }
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        catch(RuntimeException ex){System.getLogger(SpatialAudio.class.getName()).log(System.Logger.Level.WARNING,"Spatial audio output stopped",ex);}
        finally{line.stop();line.close();}
    }
    @Override public void pause(MediaPlayer p,long pts){paused=true;line.stop();}
    @Override public void resume(MediaPlayer p,long pts){paused=false;line.start();}
    @Override public void flush(MediaPlayer p,long pts){generation++;queue.clear();line.flush();}
    @Override public void drain(MediaPlayer p){/* Output worker drains already queued packets. */}
    @Override public void setVolume(float volume,boolean mute){/* Venue/listener controls are applied by position(). */}
    @Override public void close(){closed=true;generation++;queue.clear();line.stop();line.flush();line.close();worker.interrupt();}
}
