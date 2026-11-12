// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTEfx.*;
import static org.lwjgl.openal.EXTThreadLocalContext.*;

import org.lwjgl.openal.*;

import java.nio.*;
import java.util.*;

/** Dedicated worker-thread context, never changes Minecraft's process context. */
final class OpenAlOutput implements AutoCloseable {
    record Emitter(float x, float y, float z, float gain, float obstruction) {}

    record Scene(boolean spatial, List<Emitter> emitters) {
        Scene {
            emitters = List.copyOf(emitters);
        }
    }

    private long device, context;
    private final int[] sources = new int[8], filters = new int[8];
    private final List<Set<Integer>> buffers = new ArrayList<>();
    private final Map<Integer, Integer> frameCounts = new HashMap<>();
    private boolean efx;
    private Boolean spatial;
    private boolean started;
    long underruns;
    static final long RESERVE_MICROS = 300_000;
    String description;

    OpenAlOutput() {
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == 0) throw new IllegalStateException("No OpenAL device");
            var caps = ALC.createCapabilities(device);
            if (!caps.ALC_EXT_thread_local_context)
                throw new IllegalStateException("OpenAL thread contexts unavailable");
            context =
                    alcCreateContext(
                            device,
                            caps.ALC_SOFT_HRTF
                                    ? new int[] {SOFTHRTF.ALC_HRTF_SOFT, ALC_TRUE, 0}
                                    : null);
            if (context == 0) context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0 || !alcSetThreadContext(context))
                throw new IllegalStateException("No OpenAL thread context");
            AL.createCapabilities(caps);
            efx = caps.ALC_EXT_EFX;
            alDistanceModel(AL_NONE);
            alDopplerFactor(0);
            alListener3f(AL_POSITION, 0, 0, 0);
            alListenerfv(AL_ORIENTATION, new float[] {0, 0, -1, 0, 1, 0});
            for (int i = 0; i < 8; i++) {
                sources[i] = alGenSources();
                buffers.add(new HashSet<>());
                alSourcei(sources[i], AL_SOURCE_RELATIVE, AL_TRUE);
                if (efx) {
                    filters[i] = alGenFilters();
                    alFilteri(filters[i], AL_FILTER_TYPE, AL_FILTER_LOWPASS);
                }
            }
            description =
                    "OpenAL 3D"
                            + (caps.ALC_SOFT_HRTF
                                            && alcGetInteger(device, SOFTHRTF.ALC_HRTF_SOFT)
                                                    == ALC_TRUE
                                    ? " / HRTF"
                                    : " / speaker panning");
            check();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    private static void check() {
        int error = alGetError();
        if (error != AL_NO_ERROR) throw new IllegalStateException("OpenAL error " + error);
    }

    void scene(Scene scene) {
        if (spatial != null && spatial != scene.spatial) flush();
        spatial = scene.spatial;
        for (int i = 0; i < 8; i++) {
            var e = i < scene.emitters.size() ? scene.emitters.get(i) : new Emitter(0, 0, -1, 0, 0);
            alSource3f(sources[i], AL_POSITION, e.x, e.y, e.z);
            alSourcef(sources[i], AL_GAIN, e.gain * (1 - .65f * e.obstruction));
            if (efx) {
                alFilterf(filters[i], AL_LOWPASS_GAIN, 1);
                alFilterf(filters[i], AL_LOWPASS_GAINHF, 1 - .97f * e.obstruction);
                alSourcei(sources[i], AL_DIRECT_FILTER, filters[i]);
            }
        }
    }

    private void reclaim() {
        for (int i = 0; i < 8; i++)
            while (alGetSourcei(sources[i], AL_BUFFERS_PROCESSED) > 0) {
                int b = alSourceUnqueueBuffers(sources[i]);
                alDeleteBuffers(b);
                buffers.get(i).remove(b);
                if (i == 0) frameCounts.remove(b);
            }
    }

    long queuedMicros() {
        reclaim();
        return Math.max(
                        0,
                        frameCounts.values().stream().mapToLong(Integer::longValue).sum()
                                - alGetSourcei(sources[0], AL11.AL_SAMPLE_OFFSET))
                * 1_000_000L
                / 48000;
    }

    // Budget time, not callback count: AAC blocks are only ~21ms. Three blocks
    // left the native mixer empty during ordinary chunk-building / GC stalls.
    // PCM is still started at its VLC PTS; this reserve adds no presentation delay.
    boolean ready() {
        return queuedMicros() < RESERVE_MICROS && alGetSourcei(sources[0], AL_BUFFERS_QUEUED) < 128;
    }

    void write(byte[] pcm, Scene scene) {
        scene(scene);
        reclaim();
        int count = scene.spatial ? 8 : 1;
        var data =
                org.lwjgl.BufferUtils.createByteBuffer(scene.spatial ? pcm.length / 2 : pcm.length)
                        .order(ByteOrder.nativeOrder());
        if (scene.spatial)
            for (int i = 0; i + 3 < pcm.length; i += 4) {
                int l = (short) ((pcm[i] & 255) | (pcm[i + 1] << 8)),
                        r = (short) ((pcm[i + 2] & 255) | (pcm[i + 3] << 8));
                data.putShort((short) ((l + r) / 2));
            }
        else data.put(pcm);
        data.flip();
        for (int i = 0; i < count; i++) {
            int b = alGenBuffers();
            buffers.get(i).add(b);
            if (i == 0) frameCounts.put(b, pcm.length / 4);
            alBufferData(b, scene.spatial ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16, data, 48000);
            alSourceQueueBuffers(sources[i], b);
        }
        // Start all emitter queues together after an underrun; shared PCM clock.
        if (alGetSourcei(sources[0], AL_SOURCE_STATE) != AL_PLAYING) {
            if (started) underruns++;
            if (scene.spatial) alSourcePlayv(sources);
            else alSourcePlay(sources[0]);
            started = true;
        }
        check();
    }

    void pause() {
        for (int source : sources) alSourcePause(source);
    }

    void resume() {
        if (spatial != null && spatial) alSourcePlayv(sources);
        else alSourcePlay(sources[0]);
    }

    void flush() {
        for (int i = 0; i < buffers.size(); i++) {
            alSourceStop(sources[i]);
            alSourcei(sources[i], AL_BUFFER, 0);
            for (int b : buffers.get(i)) alDeleteBuffers(b);
            buffers.get(i).clear();
        }
        frameCounts.clear();
        started = false;
    }

    @Override
    public void close() {
        if (context != 0) {
            if (alcGetThreadContext() == context) {
                for (int i = 0; i < buffers.size(); i++) {
                    alSourceStop(sources[i]);
                    alDeleteSources(sources[i]);
                    for (int b : buffers.get(i)) alDeleteBuffers(b);
                    if (filters[i] != 0) alDeleteFilters(filters[i]);
                }
                AL.setCurrentThread(null);
                alcSetThreadContext(0);
            }
            alcDestroyContext(context);
            context = 0;
        }
        if (device != 0) {
            alcCloseDevice(device);
            device = 0;
        }
    }
}
