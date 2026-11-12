// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.nio.ByteBuffer;

/**
 * Monochrome VRSL grid reader. Frame input is tightly packed, top-down four-byte pixels with green
 * at byte 1.
 */
public final class VrslGrid {
    public enum Layout {
        HORIZONTAL_TOP,
        HORIZONTAL_BOTTOM,
        VERTICAL_LEFT,
        VERTICAL_RIGHT
    }

    public enum Transfer {
        IDENTITY,
        LINEAR_TO_SRGB
    }

    private VrslGrid() {}

    /** Channel is zero-based across universes; returned coordinates are top-down. */
    public static int[] center(int channel, int width, int height, Layout layout) {
        if (channel < 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid grid dimensions/channel");
        int universe = channel / 512;
        int local = channel % 512;
        long along = (long) universe * 640 + (local / 13) * 16;
        long across = (local % 13) * 16L;
        // HNode TextureWriter.PixelToIndex flips serializer Y before Unity stores
        // the pixels. Thus serializer coordinates already match top-down video.
        long x, y;
        switch (layout) {
            case HORIZONTAL_TOP -> {
                x = along;
                y = across;
            }
            case HORIZONTAL_BOTTOM -> {
                x = along;
                y = height - 208 + across;
            }
            case VERTICAL_LEFT -> {
                x = across;
                y = height - along - 16;
            }
            case VERTICAL_RIGHT -> {
                x = width - 208 + across;
                y = height - along - 16;
            }
            default -> throw new IllegalArgumentException("Unsupported layout");
        }
        if (x < 0 || y < 0 || x + 16 > width || y + 16 > height)
            throw new IllegalArgumentException("Channel cell outside frame");
        return new int[] {(int) x + 8, (int) y + 8};
    }

    /** Average the center 4x4 green samples, avoiding compressed cell borders. */
    public static int read(
            ByteBuffer rgba, int width, int height, int channel, Layout layout, Transfer transfer) {
        long bytes = (long) width * height * 4;
        if (width <= 0 || height <= 0 || bytes > Integer.MAX_VALUE || rgba.remaining() < bytes)
            throw new IllegalArgumentException("Invalid or truncated RGBA frame");
        int[] p = center(channel, width, height, layout);
        int total = 0;
        for (int y = p[1] - 2; y < p[1] + 2; y++)
            for (int x = p[0] - 2; x < p[0] + 2; x++)
                total += Byte.toUnsignedInt(rgba.get(rgba.position() + (y * width + x) * 4 + 1));
        double v = total / (16.0 * 255);
        if (transfer == Transfer.LINEAR_TO_SRGB)
            v = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0, Math.min(1, v)) * 255);
    }
}
