import math, os
from PIL import Image, ImageDraw, ImageFilter

OUT = "/scratch/work/app/res"
S = 1024  # supersample canvas


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gradient_bg(size, deep=(9, 11, 30), mid=(27, 16, 70)):
    """Diagonal deep-navy -> indigo gradient with soft aurora blobs."""
    img = Image.new("RGB", (size, size), deep)
    d = ImageDraw.Draw(img)
    for y in range(size):
        for_x = y / max(1, size - 1)
        d.line([(0, y), (size, y)], fill=lerp(deep, mid, for_x ** 0.85))
    # aurora blobs
    blobs = [
        (0.18, 0.16, 0.62, (106, 75, 255), 210),
        (0.86, 0.30, 0.55, (255, 75, 149), 190),
        (0.30, 0.92, 0.60, (16, 191, 255), 170),
        (0.82, 0.86, 0.48, (255, 159, 69), 130),
    ]
    glow = Image.new("RGB", (size, size), (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for cx, cy, r, col, a in blobs:
        R = int(r * size)
        gd.ellipse([int(cx * size) - R, int(cy * size) - R,
                    int(cx * size) + R, int(cy * size) + R],
                   fill=tuple(int(c * a / 255) for c in col))
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.09))
    img = Image.blend(img, Image.blend(img, glow, 0.0), 0.0)
    # screen-blend the glow on top
    base = img.load()
    gl = glow.load()
    for y in range(size):
        for x in range(size):
            r, g, b = base[x, y]
            gr, gg, gb = gl[x, y]
            base[x, y] = (min(255, r + gr), min(255, g + gg), min(255, b + gb))
    return img


def bell(size, color=(255, 255, 255)):
    """White alarm-bell silhouette on transparent canvas, drawn large."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    u = size / 512.0  # design units are 512

    def P(x, y):
        return (x * u, y * u)

    c = color + (255,)
    # dome
    d.ellipse([P(146, 112), P(366, 332)], fill=c)
    # body
    d.rectangle([P(146, 222), P(366, 306)], fill=c)
    # flare
    d.polygon([P(150, 300), P(362, 300), P(414, 384), P(98, 384)], fill=c)
    # rounded rim
    d.ellipse([P(96, 344), P(416, 396)], fill=c)
    # clapper
    d.ellipse([P(226, 388), P(286, 448)], fill=c)
    # top nub
    d.ellipse([P(238, 88), P(274, 124)], fill=c)
    d.rectangle([P(240, 104), P(272, 128)], fill=c)
    return img


def shade(img):
    """Give the bell a soft top-down white->ice gradient and a glass highlight."""
    w, h = img.size
    grad = Image.new("L", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        grad.putpixel((0, y), int(255 - 60 * t))
    grad = grad.resize((w, h))
    solid = Image.new("RGBA", (w, h), (255, 255, 255, 255))
    ice = Image.new("RGBA", (w, h), (206, 219, 255, 255))
    body = Image.composite(solid, ice, grad)
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(body, (0, 0), img)
    return out


def rounded_mask(size, radius_ratio=0.225):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1],
                                        radius=int(size * radius_ratio), fill=255)
    return m


def circle_mask(size):
    m = Image.new("L", (size, size), 0)
    ImageDraw.Draw(m).ellipse([0, 0, size - 1, size - 1], fill=255)
    return m


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", optimize=True)


def main():
    bg_big = gradient_bg(S)
    bell_big = shade(bell(S))

    # ---- adaptive background: full-bleed gradient (108dp canvas) ----
    # ---- adaptive foreground: bell inside the 66dp safe zone ----
    fg_scale = 0.50  # bell occupies ~46% of the 108 canvas
    fg_big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    bw = int(S * fg_scale)
    b = bell_big.resize((bw, bw), Image.LANCZOS)
    fg_big.paste(b, ((S - bw) // 2, (S - bw) // 2), b)

    dens = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, k in dens.items():
        # legacy square icon 48dp
        px = int(48 * k)
        comp = bg_big.resize((px, px), Image.LANCZOS).convert("RGBA")
        bs = int(px * 0.66)
        bb = bell_big.resize((bs, bs), Image.LANCZOS)
        comp.paste(bb, ((px - bs) // 2, (px - bs) // 2), bb)
        comp.putalpha(rounded_mask(px))
        save(comp, f"{OUT}/mipmap-{name}/ic_launcher.png")

        # round icon
        comp2 = bg_big.resize((px, px), Image.LANCZOS).convert("RGBA")
        comp2.paste(bb, ((px - bs) // 2, (px - bs) // 2), bb)
        comp2.putalpha(circle_mask(px))
        save(comp2, f"{OUT}/mipmap-{name}/ic_launcher_round.png")

        # adaptive layers 108dp
        apx = int(108 * k)
        save(bg_big.resize((apx, apx), Image.LANCZOS).convert("RGBA"),
             f"{OUT}/mipmap-{name}/ic_launcher_bg.png")
        save(fg_big.resize((apx, apx), Image.LANCZOS),
             f"{OUT}/mipmap-{name}/ic_launcher_fg.png")

    # notification / small icon (white silhouette, 24dp)
    for name, k in dens.items():
        px = int(24 * k)
        nb = bell_big.resize((px, px), Image.LANCZOS)
        mono = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        white = Image.new("RGBA", (px, px), (255, 255, 255, 255))
        mono.paste(white, (0, 0), nb)
        save(mono, f"{OUT}/drawable-{name}/ic_stat_alarm.png")

    print("icons written")


main()
