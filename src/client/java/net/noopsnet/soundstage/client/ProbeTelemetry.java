// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded asynchronous diagnostic logging; never blocks the native display callback. */
final class ProbeTelemetry implements AutoCloseable {
    private final ArrayBlockingQueue<ProbePlayer.Sample> queue = new ArrayBlockingQueue<>(2048);
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean closed;
    private volatile String error;
    private final Thread writer;
    private final long origin = System.nanoTime();
    private final Path path;

    ProbeTelemetry(Path directory) throws IOException {
        Files.createDirectories(directory);
        path = Files.createTempFile(directory, "soundstage-", ".csv");
        BufferedWriter out = Files.newBufferedWriter(path);
        out.write("# session_utc=" + Instant.now() + "; clock=VLC_status_not_audio_PTS\n");
        out.write("sequence,callback_elapsed_ns,vlc_media_ms,intensity,red,green,blue\n");
        out.flush();
        writer =
                new Thread(
                        () -> {
                            try (out) {
                                while (!closed || !queue.isEmpty()) {
                                    ProbePlayer.Sample s = queue.poll(250, TimeUnit.MILLISECONDS);
                                    if (s != null)
                                        out.write(
                                                s.sequence()
                                                        + ","
                                                        + (s.observedNanos() - origin)
                                                        + ","
                                                        + s.mediaMillis()
                                                        + ","
                                                        + s.intensity()
                                                        + ","
                                                        + s.red()
                                                        + ","
                                                        + s.green()
                                                        + ","
                                                        + s.blue()
                                                        + "\n");
                                    else out.flush();
                                }
                                out.write("# dropped_samples=" + dropped.get() + "\n");
                            } catch (IOException | InterruptedException ex) {
                                error = ex.getClass().getSimpleName();
                            }
                        },
                        "soundstage-telemetry");
        writer.setDaemon(true);
        writer.start();
    }

    void record(ProbePlayer.Sample sample) {
        if (!closed && !queue.offer(sample)) dropped.incrementAndGet();
    }

    String status() {
        return error == null
                ? path.getFileName() + " dropped=" + dropped.get()
                : "CSV error: " + error;
    }

    @Override
    public void close() {
        closed = true;
    }
}
