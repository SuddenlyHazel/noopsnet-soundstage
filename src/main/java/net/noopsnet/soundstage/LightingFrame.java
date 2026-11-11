// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage;

/** Immutable published decoder snapshot; a short RGB hold masks decoder buffer changes. */
public record LightingFrame(long nanos,int[] channels) {
    public int[] effects(long now){return now-nanos<=1_000_000_000L?channels:null;}
    public int[] rgb(long now){return now-nanos<=3_000_000_000L?channels:null;}
}
