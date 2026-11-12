// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/** CLI used by codec integration checks to exercise the shipped grid reader. */
public final class DecodeFrame {
    public static void main(String[] args) throws Exception {
        ByteBuffer frame = ByteBuffer.wrap(Files.readAllBytes(Path.of(args[0])));
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < 512; i++) {
            if (i > 0) result.append(',');
            result.append(
                    VrslGrid.read(
                            frame,
                            1920,
                            1080,
                            i,
                            VrslGrid.Layout.HORIZONTAL_TOP,
                            VrslGrid.Transfer.IDENTITY));
        }
        System.out.println(result.append(']'));
    }
}
