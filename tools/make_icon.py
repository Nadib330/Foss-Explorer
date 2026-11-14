#!/usr/bin/env python3
"""Makes the app icon and the in-app logo from art/Explorer_Logo5.jpg.

    python3 tools/make_icon.py

The picture is a disc of three cream segments with an olive compass in the
middle. Android wants an icon in two layers that it can cut to any outline,
so the script measures the picture and writes it as vector drawables:

  res/drawable/ic_launcher_background.xml  the three segments, continued to the
                                            edges of the layer
  res/drawable/ic_launcher_foreground.xml  the compass: ring, face and needle
  res/drawable/icon_monochrome.xml         ring and needle alone, for themed icons

With --check it also draws the measured shapes and reports how far they are
from the picture.

Own code of Foss Explorer, GPL-3.0. Needs Pillow.
"""
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
SOURCE = os.path.join(ROOT, 'art', 'Explorer_Logo5.jpg')
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')


def hexcolor(p):
    return '#%02X%02X%02X' % tuple(p[:3])


def measure(im):
    """Centre, radii, colours and the four corners of the needle, in pixels of the picture."""
    w, h = im.size
    cx, cy = w / 2.0, h / 2.0
    px = im.load()
    dark = lambda p: p[0] < 140
    row = [x for x in range(w) if dark(px[x, int(cy) - 16])]
    outer = (row[-1] - row[0] + 1) / 2.0
    gaps = [(row[i], row[i + 1]) for i in range(len(row) - 1) if row[i + 1] - row[i] > 5]
    inner = cx - gaps[0][0] - 0.5
    needle = [(x, y) for y in range(h) for x in range(w)
              if dark(px[x, y]) and math.hypot(x - cx, y - cy) < inner - 12]
    corners = [max(needle, key=lambda p: p[0] - p[1]), max(needle, key=lambda p: p[0] + p[1]),
               min(needle, key=lambda p: p[0] - p[1]), min(needle, key=lambda p: p[0] + p[1])]
    return {
        'centre': (cx, cy), 'disc': w / 2.0, 'outer': outer, 'inner': inner, 'needle': corners,
        'top': px[int(cx), int(h * 0.09)], 'left': px[int(w * 0.16), int(h * 0.69)],
        'right': px[int(w * 0.81), int(h * 0.56)], 'ink': px[int(cx - (outer + inner) / 2), int(cy)],
    }


def sectors(c, r, far):
    """The top and the right segment as polygons; the third is what they leave. As in the picture, the
    borders are the three lines that touch the ring at the top, the lower right and the lower left."""
    cx, cy = c
    p_top = (cx, cy - r)
    p_right = (cx + r * math.cos(math.radians(30)), cy + r * 0.5)
    p_left = (cx - r * math.cos(math.radians(30)), cy + r * 0.5)
    far1 = (cx + far, cy - r)                                        # from the top, to the right
    t = far
    far2 = (p_right[0] - 0.5 * t, p_right[1] + 0.8660254 * t)        # from the lower right, down and left
    far3 = (p_left[0] - 0.5 * t, p_left[1] - 0.8660254 * t)          # from the lower left, up and left
    top = [c, p_left, far3, (cx - far, cy - far), (cx + far, cy - far), far1, p_top]
    right = [c, p_top, far1, (cx + far, cy + far), (cx - far, cy + far), far2, p_right]
    return top, right


def path(points):
    return 'M' + ' L'.join('%.3f,%.3f' % p for p in points) + ' Z'


def circle(cx, cy, r):
    return 'M%.3f,%.3f m-%.3f,0 a%.3f,%.3f 0 1,0 %.3f,0 a%.3f,%.3f 0 1,0 -%.3f,0 Z' % (cx, cy, r, r, r, 2 * r, r, r, 2 * r)


def vector(size, viewport, body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<!-- %s\n     Written by tools/make_icon.py from art/Explorer_Logo5.jpg. Do not edit by hand. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="%ddp"\n    android:height="%ddp"\n    android:viewportWidth="%d"\n    android:viewportHeight="%d">\n%s</vector>\n'
            % (comment, size, size, viewport, viewport, body))


def fill(color, data):
    return '    <path\n        android:fillColor="%s"\n        android:pathData="%s" />\n' % (color, data)


def stroke(color, width, data):
    return ('    <path\n        android:pathData="%s"\n        android:strokeColor="%s"\n        android:strokeWidth="%.3f" />\n'
            % (data, color, width))


def shapes(m, centre, disc_radius):
    """The measured shapes for a drawing whose disc has [disc_radius] around [centre]."""
    s = disc_radius / m['disc']
    cx, cy = centre
    outer, inner = m['outer'] * s, m['inner'] * s
    needle = [(cx + (x + 0.5 - m['centre'][0]) * s, cy + (y + 0.5 - m['centre'][1]) * s) for x, y in m['needle']]
    return outer, inner, needle


def write(name, text):
    with open(os.path.join(RES, 'drawable', name), 'w', encoding='utf-8') as f:
        f.write(text)


def check(im, m):
    """Draws the measured shapes at the size of the picture and compares."""
    w, h = im.size
    big = Image.new('RGB', (w, h), m['left'][:3])
    d = ImageDraw.Draw(big)
    outer, inner, needle = shapes(m, m['centre'], m['disc'])
    top, right = sectors(m['centre'], outer, w * 2)
    d.polygon(top, fill=m['top'][:3])
    d.polygon(right, fill=m['right'][:3])
    cx, cy = m['centre']
    d.ellipse((cx - outer, cy - outer, cx + outer, cy + outer), fill=m['ink'][:3])
    d.ellipse((cx - inner, cy - inner, cx + inner, cy + inner), fill=m['top'][:3])
    d.polygon(needle, fill=m['ink'][:3])
    mask = Image.new('L', (w, h), 0)
    ImageDraw.Draw(mask).ellipse((2, 2, w - 3, h - 3), fill=255)
    diff = ImageChops.difference(big, im).convert('L')
    far = sum(1 for a, b in zip(diff.tobytes(), mask.tobytes()) if b and a > 40)
    inside = sum(1 for b in mask.tobytes() if b)
    print('pixels of the disc that differ visibly: %d of %d (%.2f %%)' % (far, inside, 100.0 * far / inside))
    return big


def main():
    im = Image.open(SOURCE).convert('RGB')
    m = measure(im)
    top_c, left_c, right_c, ink = (hexcolor(m[k]) for k in ('top', 'left', 'right', 'ink'))

    # Launcher icon: 108 units, of which a circle of 72 is certain to be seen. The disc fills that circle.
    c = (54.0, 54.0)
    outer, inner, needle = shapes(m, c, 36.0)
    top, right = sectors(c, outer, 90.0)
    write('ic_launcher_background.xml', vector(108, 108,
          fill(left_c, 'M0,0h108v108h-108z') + fill(top_c, path(top)) + fill(right_c, path(right)),
          'App icon, back layer: the three segments of the logo, continued to the edges.'))
    ring = stroke(ink, outer - inner, circle(c[0], c[1], (outer + inner) / 2))
    write('ic_launcher_foreground.xml', vector(108, 108,
          fill(top_c, circle(c[0], c[1], (outer + inner) / 2)) + ring + fill(ink, path(needle)),
          'App icon, front layer: the compass of the logo.'))
    write('icon_monochrome.xml', vector(108, 108,
          stroke('#FF000000', outer - inner, circle(c[0], c[1], (outer + inner) / 2)) + fill('#FF000000', path(needle)),
          'App icon for themed icons: ring and needle alone.'))

    print('colours: top %s, left %s, right %s, compass %s' % (top_c, left_c, right_c, ink))
    print('ring: outer %.1f, inner %.1f of disc %.1f px; needle %s' % (m['outer'], m['inner'], m['disc'], m['needle']))
    if '--check' in sys.argv:
        drawn = check(im, m)
        if len(sys.argv) > 2 and not sys.argv[-1].startswith('--'):
            drawn.save(sys.argv[-1])


if __name__ == '__main__':
    main()
