// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import net.noopsnet.soundstage.AudioSpatializer;

import org.watermedia.videolan4j.binding.lib.LibVlc;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.player.base.callback.DefaultAudioCallbackAdapter;

import java.util.List;
import java.util.concurrent.*;

import javax.sound.sampled.*;

/** VLC owns presentation time; OpenAL owns spatial rendering, with Java Sound fallback. */
final class SpatialAudio extends DefaultAudioCallbackAdapter implements AutoCloseable {
    // VLC commonly delivers more than a second ahead of its presentation clock.
    // Retain original callback blocks and budget four seconds of 48 kHz stereo PCM.
    private final net.noopsnet.soundstage.PcmQueue queue =
            new net.noopsnet.soundstage.PcmQueue(48000 * 4 * 4);
    volatile long receivedBytes, writtenBytes, droppedPackets, latePackets, underruns;
    private final Thread worker;
    private volatile boolean closed, paused;
    private volatile int generation;
    private volatile double gain, pan, occlusion;
    private volatile OpenAlOutput.Scene scene =
            new OpenAlOutput.Scene(false, List.of(new OpenAlOutput.Emitter(0, 0, -1, 0, 0)));
    volatile String backend = "Starting audio";

    SpatialAudio() {
        super(4);
        worker = new Thread(this::run, "Soundstage spatial PCM");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY + 1);
        worker.start();
    }

    void position(double volume, double balance, double obstruction) {
        gain = volume;
        pan = balance;
        occlusion = obstruction;
    }

    void scene(OpenAlOutput.Scene s) {
        scene = s;
    }

    @Override
    protected void onPlay(MediaPlayer player, byte[] samples, int count, long pts) {
        if (closed || samples.length > 192000) return;
        if (samples.length == 0 || samples.length % 4 != 0) return;
        receivedBytes += samples.length;
        droppedPackets +=
                queue.offer(new net.noopsnet.soundstage.PcmQueue.Packet(samples, pts, generation));
    }

    private void run() {
        OpenAlOutput al = null;
        SourceDataLine line = null;
        var dsp = new AudioSpatializer();
        int current = -1;
        boolean wasPaused = false;
        try {
            try {
                al = new OpenAlOutput();
                backend = al.description;
            } catch (RuntimeException | LinkageError ex) {
                backend = "Java Sound fallback";
                var format = new AudioFormat(48000, 16, 2, true, false);
                line = AudioSystem.getSourceDataLine(format);
                line.open(format, 57600);
                line.start();
                System.getLogger(getClass().getName())
                        .log(
                                System.Logger.Level.WARNING,
                                "OpenAL unavailable; using stereo fallback",
                                ex);
            }
            while (!closed) {
                if (current != generation) {
                    current = generation;
                    dsp.reset();
                    if (al != null) al.flush();
                    else line.flush();
                }
                if (al != null) al.scene(scene);
                if (paused != wasPaused) {
                    if (al != null) {
                        if (paused) al.pause();
                        else al.resume();
                    } else {
                        if (paused) line.stop();
                        else line.start();
                    }
                    wasPaused = paused;
                }
                if (paused) {
                    Thread.sleep(10);
                    continue;
                }
                var p = queue.poll(10);
                if (p == null) continue;
                while (!closed && p.generation() == generation && !paused) {
                    long queued =
                            al != null
                                    ? al.queuedMicros()
                                    : (line.getBufferSize() - line.available())
                                            * 1_000_000L
                                            / 192000;
                    long wait = p.pts() - LibVlc.libvlc_clock() - queued;
                    if (wait <= 1000 && (al == null || al.ready())) break;
                    Thread.sleep(2);
                }
                if (closed || p.generation() != generation || paused) continue;
                if (p.pts() > 0 && LibVlc.libvlc_clock() - p.pts() > 250_000) {
                    latePackets++;
                    if (al != null) al.flush();
                    else line.flush();
                    continue;
                }
                writtenBytes += p.samples().length;
                if (al != null) {
                    al.write(p.samples(), scene);
                    underruns = al.underruns;
                } else {
                    dsp.process(p.samples(), gain, pan, occlusion);
                    int offset = 0;
                    while (!closed && p.generation() == generation && offset < p.samples().length)
                        offset += line.write(p.samples(), offset, p.samples().length - offset);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError ex) {
            backend = "Audio output failed";
            System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, backend, ex);
        } finally {
            System.getLogger(getClass().getName())
                    .log(
                            System.Logger.Level.INFO,
                            "Soundstage audio finished: written="
                                    + writtenBytes
                                    + ", underruns="
                                    + underruns
                                    + ", late="
                                    + latePackets
                                    + ", overflow="
                                    + droppedPackets);
            if (al != null) al.close();
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    @Override
    public void pause(MediaPlayer p, long pts) {
        paused = true;
    }

    @Override
    public void resume(MediaPlayer p, long pts) {
        paused = false;
    }

    @Override
    public void flush(MediaPlayer p, long pts) {
        generation++;
        queue.clear();
    }

    @Override
    public void drain(MediaPlayer p) {}

    @Override
    public void setVolume(float volume, boolean mute) {}

    @Override
    public void close() {
        closed = true;
        generation++;
        queue.clear();
        worker.interrupt();
    }
}
