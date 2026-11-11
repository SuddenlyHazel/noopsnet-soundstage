// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.nio.ByteBuffer;
import java.util.concurrent.Semaphore;
import net.noopsnet.soundstage.VrslGrid;
import org.watermedia.api.player.videolan.BasePlayer;
import org.watermedia.videolan4j.player.base.MediaPlayer;
import org.watermedia.videolan4j.player.embedded.videosurface.callback.*;
import org.watermedia.videolan4j.tools.Chroma;

/** Samples the VLC display callback; clock values are observations, not audio PTS. */
final class ProbePlayer extends BasePlayer implements RenderCallback, BufferFormatCallback, BufferCleanupCallback {
    record Sample(long sequence, long observedNanos, long mediaMillis, int intensity, int red, int green, int blue) {}
    private volatile Sample latest;
    private volatile boolean closed;
    private volatile int width, height;
    private volatile long mediaTime;
    private volatile long callbacks;
    void pollClock() { mediaTime = getTime(); }
    String diagnostics() { return getStateName() + " " + width + "x" + height + " callbacks=" + callbacks; }
    private long sequence;
    private final VrslGrid.Layout layout;
    private final VrslGrid.Transfer transfer;
    private final ProbeTelemetry telemetry;
    private final Semaphore frameLock = new Semaphore(1);

    ProbePlayer(VrslGrid.Layout layout, VrslGrid.Transfer transfer, ProbeTelemetry telemetry) {
        this.layout = layout;
        this.transfer = transfer;
        this.telemetry = telemetry;
        init(null, this, this, this);
        if (raw() != null) raw().mediaPlayer().videoSurface().getVideoSurface().setSemaphore(frameLock);
    }
    @Override public BufferFormat getBufferFormat(int width, int height) {
        this.width = width; this.height = height;
        System.getLogger(ProbePlayer.class.getName()).log(System.Logger.Level.INFO,
            "Soundstage native video buffer: " + width + "x" + height + " RV32");
        return new BufferFormat(Chroma.RV32, width, height);
    }
    @Override public void allocatedBuffers(ByteBuffer[] buffers) {}
    @Override public void cleanupBuffers(ByteBuffer[] buffers) { latest = null; }
    @Override public void display(MediaPlayer player, ByteBuffer[] buffers, BufferFormat format) {
        callbacks++;
        if (closed || buffers.length == 0) return;
        // VLC supplied 1090 rows for the tested 1080p OBS stream. Top cells
        // retain their coordinates; this tolerance is only for this probe.
        if (width != 1920 || (height < 1080 || height > 1120)) { latest = null; return; }
        if (!frameLock.tryAcquire()) return;
        try {
            ByteBuffer frame = buffers[0].duplicate();
            frame.clear();
            int[] values = new int[4];
            for (int i = 0; i < values.length; i++) values[i] = VrslGrid.read(frame, width, height, i, layout, transfer);
            Sample sample = new Sample(++sequence, System.nanoTime(), mediaTime, values[0], values[1], values[2], values[3]);
            latest = sample;
            telemetry.record(sample);
        } finally { frameLock.release(); }
    }
    Sample sample() { return latest; }
    @Override public void release() { closed = true; latest = null; telemetry.close(); super.release(); }
}
