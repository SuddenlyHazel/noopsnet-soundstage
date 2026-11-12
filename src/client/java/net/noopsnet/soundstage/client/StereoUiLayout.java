// SPDX-License-Identifier: MPL-2.0
package net.noopsnet.soundstage.client;

/** Shared geometry and palette for the native screen and offline layout evidence. */
public record StereoUiLayout(
        int width,
        int height,
        int padding,
        int gap,
        int nowHeight,
        int listHeight,
        int rowHeight,
        int playWidth,
        int sliderWidth) {
    public static final int PANEL = 0xff151b24, INSET = 0xff10171e;
    public static final int TEXT = 0xffedf4f3, MUTED = 0xffafc0c7, ACCENT = 0xff65e4dc;
    public static final int BUTTON = 0xff283642, HOVER = 0xff3c5260, SELECTED = 0xff18454c;

    public static StereoUiLayout fit(int screenWidth, int screenHeight) {
        int width = Math.min(480, screenWidth - 16), height = Math.min(340, screenHeight - 16);
        boolean compact = height < 260;
        int padding = compact ? 8 : 14, gap = compact ? 4 : 8;
        int now = compact ? 22 : 30;
        int list = height - padding * 2 - gap * 5 - 20 - now - 20 - 12 - 20;
        int play = compact ? 70 : 90;
        return new StereoUiLayout(
                width,
                height,
                padding,
                gap,
                now,
                list,
                compact ? 22 : 26,
                play,
                width - padding * 2 - gap - play);
    }
}
