"""One 512-channel universe; fixture starts are 1,5,11,13,17+4*pixel."""
import colorsys
import math

DURATION = 60
FPS = 30
PIXELS = 8


def channels(seconds):
    t = seconds % DURATION
    v = [0] * 512
    # Park laser axes at center even during blackout; its dimmer remains zero.
    v[8:10] = [128, 128]
    if t >= 58:
        return v
    phase = int(t // 10)
    local = t % 10

    def rgb(address, intensity, hue):
        color = colorsys.hsv_to_rgb(hue % 1, 0.85, 1)
        v[address - 1:address + 3] = [round(intensity), *(round(c * 255) for c in color)]

    if phase == 0:
        # Three obvious primary tests, then a white reference.
        color = ([255, 0, 0], [0, 255, 0], [0, 0, 255], [255, 255, 255])[min(3, int(local / 2.5))]
        for address in [1, *(17 + i * 4 for i in range(PIXELS))]:
            v[address - 1:address + 3] = [180, *color]
    elif phase == 1:
        rgb(5, 180, local / 10)
        v[8] = round(128 + 42 * math.sin(2 * math.pi * local / 10))
        v[9] = round(112 + 18 * math.sin(2 * math.pi * local / 5))
    elif phase == 2:
        # Distinct on/off burst, followed by narrow-to-wide output.
        v[10] = 180 if local < 3 or 5 <= local < 9 else 0
        v[11] = round(255 * local / 10)
    elif phase == 3:
        rgb(13, 180 if int(local) % 2 == 0 else 0, local / 10)
    elif phase == 4:
        # Positive-X chase: exactly one addressable block at a time.
        pixel = int(local * 2) % PIXELS
        rgb(17 + pixel * 4, 220, pixel / PIXELS)
    else:
        rgb(1, 150, local / 12)
        rgb(5, 150, local / 12 + .33)
        v[8] = round(128 + 32 * math.sin(2 * math.pi * local / 8))
        v[9] = round(112 + 12 * math.cos(2 * math.pi * local / 8))
        v[10:12] = [55, 180]
        rgb(13, 80, local / 12 + .66)
        for i in range(PIXELS):
            rgb(17 + i * 4, 130, i / PIXELS + local / 8)
    return v


def grid(seconds):
    """Top-down grayscale 40x13; each sample scales to a VRSL 16x16 cell."""
    image = bytearray(40 * 13)
    for channel, value in enumerate(channels(seconds)):
        x, y = channel // 13, channel % 13
        image[y * 40 + x] = value
    return bytes(image)
