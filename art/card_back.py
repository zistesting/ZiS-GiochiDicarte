"""
Dorso delle carte ZiS, disegnato in vettoriale nella palette dell'app.
Nasce a 448x819, la proporzione esatta delle carte: nessun ridimensionamento e nessuna
deformazione. Simmetrico rispetto a entrambi gli assi, quindi identico anche capovolto,
come deve essere il dorso di una carta da gioco.
"""
import math

W, H = 448, 819

# palette da res/values/colors.xml
NAVY_DEEP = "#0C1626"
NAVY = "#14294A"
NAVY_MID = "#23477E"
STEEL = "#3E6BA6"
CELESTE = "#A9D0EC"
CELESTE_LIGHT = "#D7EAF6"
SILVER = "#C8D0DC"
SILVER_DARK = "#AEB9C6"

FX0, FY0, FX1, FY1 = 34, 34, W - 34, H - 34      # campo interno, dentro le cornici


def pts(p):
    return " ".join(f"{x:.2f},{y:.2f}" for x, y in p)


def scallops(x0, y0, x1, y1, r, col, sw):
    """Fascia di archi lungo il perimetro: i quattro lati, angoli inclusi."""
    out = []
    for ax, ay, bx, by in ((x0, y0, x1, y0), (x1, y1, x0, y1),
                           (x0, y1, x0, y0), (x1, y0, x1, y1)):
        L = math.hypot(bx - ax, by - ay)
        n = max(1, round(L / (2 * r)))
        step = L / n
        ux, uy = (bx - ax) / L, (by - ay) / L
        for i in range(n):
            sx, sy = ax + ux * step * i, ay + uy * step * i
            ex, ey = ax + ux * step * (i + 1), ay + uy * step * (i + 1)
            out.append(f'<path d="M {sx:.2f} {sy:.2f} A {step/2:.2f} {step/2:.2f} 0 0 1 '
                       f'{ex:.2f} {ey:.2f}" fill="none" stroke="{col}" stroke-width="{sw}"/>')
    return "\n".join(out)


def reticolo():
    """Campo: cerchi intrecciati su reticolo quadrato. Si sovrappongono e formano
       i quadrilobi tipici dei dorsi delle carte. Sotto, una trama diagonale piu' scura."""
    s = 46.0
    r = s * 0.707
    out = ['<g clip-path="url(#campo)">']

    out.append(f'<g stroke="{NAVY_MID}" stroke-width="0.9" fill="none" opacity="0.9">')
    for k in range(-40, 60):
        c = k * 16
        out.append(f'<path d="M {FX0-40} {FY0 + c} L {FX1+40} {FY0 + c - (FX1-FX0)-80}"/>')
        out.append(f'<path d="M {FX0-40} {FY0 + c} L {FX1+40} {FY0 + c + (FX1-FX0)+80}"/>')
    out.append('</g>')

    cx0, cy0 = W / 2, H / 2
    ni = int((FX1 - FX0) / s) + 3
    nj = int((FY1 - FY0) / s) + 3
    nodi = []
    for i in range(-ni, ni + 1):
        for j in range(-nj, nj + 1):
            nodi.append((cx0 + i * s, cy0 + j * s))

    out.append(f'<g fill="none" stroke="{STEEL}" stroke-width="2.1" opacity="0.80">')
    for x, y in nodi:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.2f}"/>')
    out.append('</g>')
    out.append(f'<g fill="none" stroke="{SILVER_DARK}" stroke-width="0.9" opacity="0.70">')
    for x, y in nodi:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.2f}"/>')
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r*0.34:.2f}"/>')
    out.append('</g>')
    # bottone su ogni nodo e stellina al centro di ogni maglia
    out.append(f'<g fill="{CELESTE}">')
    for x, y in nodi:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.9"/>')
    out.append('</g>')
    out.append(f'<g fill="{SILVER}">')
    for x, y in nodi:
        mx, my = x + s / 2, y + s / 2
        p = []
        for k in range(8):
            a = math.pi / 4 * k
            rr = 7.5 if k % 2 == 0 else 2.8
            p.append((mx + math.cos(a) * rr, my + math.sin(a) * rr))
        out.append(f'<polygon points="{pts(p)}"/>')
    out.append('</g>')
    out.append('</g>')
    return "\n".join(out)


def rosetta(cx, cy, r, petali=8, sw=1.5):
    # alone scuro: stacca la rosetta dal reticolo del campo
    out = [f'<circle cx="{cx}" cy="{cy}" r="{r*1.22:.2f}" fill="{NAVY_DEEP}" opacity="0.85"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{r:.2f}" fill="{NAVY_DEEP}" stroke="{SILVER}" stroke-width="{sw+0.6}"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{r*0.86:.2f}" fill="none" stroke="{SILVER_DARK}" stroke-width="0.9"/>']
    for k in range(petali):
        a = 2 * math.pi * k / petali
        px, py = cx + math.cos(a) * r * 0.55, cy + math.sin(a) * r * 0.55
        out.append(f'<ellipse cx="{px:.2f}" cy="{py:.2f}" rx="{r*0.30:.2f}" ry="{r*0.15:.2f}" '
                   f'transform="rotate({math.degrees(a):.1f} {px:.2f} {py:.2f})" '
                   f'fill="{STEEL}" stroke="{SILVER}" stroke-width="0.9"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{r*0.19:.2f}" fill="{CELESTE_LIGHT}"/>')
    return "\n".join(out)


def medaglione(cx, cy, R):
    out = [f'<circle cx="{cx}" cy="{cy}" r="{R+16:.2f}" fill="{NAVY_DEEP}" opacity="0.9"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{R+10:.2f}" fill="{NAVY}" stroke="{SILVER}" stroke-width="1.3"/>']
    out.append(scallops(cx - R - 6, cy - R - 6, cx + R + 6, cy + R + 6, 9, SILVER_DARK, 1.0)
               .replace('<path', '<path opacity="0"'))   # segnaposto, non usato
    out.pop()
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R:.2f}" fill="{NAVY_DEEP}" stroke="{SILVER}" stroke-width="2.6"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.93:.2f}" fill="none" stroke="{SILVER_DARK}" stroke-width="1.0"/>')
    n = 40
    for k in range(n):
        a = 2 * math.pi * k / n
        out.append(f'<circle cx="{cx + math.cos(a)*R*0.85:.2f}" cy="{cy + math.sin(a)*R*0.85:.2f}" '
                   f'r="{R*0.032:.2f}" fill="{SILVER}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.76:.2f}" fill="{NAVY}" stroke="{SILVER_DARK}" stroke-width="1.1"/>')
    for k in range(32):
        a = 2 * math.pi * k / 32
        r1, r2 = R * 0.42, R * 0.74
        w = 0.032 if k % 2 == 0 else 0.017
        p = [(cx + math.cos(a - w) * r1, cy + math.sin(a - w) * r1),
             (cx + math.cos(a) * r2, cy + math.sin(a) * r2),
             (cx + math.cos(a + w) * r1, cy + math.sin(a + w) * r1)]
        out.append(f'<polygon points="{pts(p)}" fill="{CELESTE if k % 2 == 0 else STEEL}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.42:.2f}" fill="{NAVY_DEEP}" stroke="{SILVER}" stroke-width="1.9"/>')
    for k in range(4):
        a = math.pi / 2 * k
        p = [(cx + math.cos(a) * R * 0.36, cy + math.sin(a) * R * 0.36),
             (cx + math.cos(a + math.pi / 4) * R * 0.14, cy + math.sin(a + math.pi / 4) * R * 0.14),
             (cx + math.cos(a + math.pi / 2) * R * 0.36, cy + math.sin(a + math.pi / 2) * R * 0.36),
             (cx, cy)]
        out.append(f'<polygon points="{pts(p)}" fill="{CELESTE if k % 2 == 0 else CELESTE_LIGHT}" '
                   f'stroke="{SILVER}" stroke-width="0.8"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.08:.2f}" fill="{SILVER}"/>')
    return "\n".join(out)


def build():
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
         '<defs><clipPath id="campo">'
         f'<rect x="{FX0}" y="{FY0}" width="{FX1-FX0}" height="{FY1-FY0}"/></clipPath></defs>']

    s.append(f'<rect width="{W}" height="{H}" fill="{NAVY_DEEP}"/>')
    s.append(f'<rect x="6" y="6" width="{W-12}" height="{H-12}" fill="{NAVY}" '
             f'stroke="{SILVER}" stroke-width="2.4"/>')
    s.append(f'<rect x="12" y="12" width="{W-24}" height="{H-24}" fill="none" '
             f'stroke="{SILVER_DARK}" stroke-width="0.9"/>')
    s.append(scallops(18, 18, W - 18, H - 18, 10, SILVER_DARK, 1.2))
    s.append(f'<rect x="{FX0-4}" y="{FY0-4}" width="{FX1-FX0+8}" height="{FY1-FY0+8}" '
             f'fill="none" stroke="{SILVER}" stroke-width="1.8"/>')

    s.append(reticolo())

    s.append(f'<rect x="{FX0}" y="{FY0}" width="{FX1-FX0}" height="{FY1-FY0}" '
             f'fill="none" stroke="{STEEL}" stroke-width="1.0"/>')

    for cx, cy in ((FX0 + 40, FY0 + 40), (FX1 - 40, FY0 + 40),
                   (FX0 + 40, FY1 - 40), (FX1 - 40, FY1 - 40)):
        s.append(rosetta(cx, cy, 28))
    for cy in (H / 2 - 190, H / 2 + 190):
        s.append(rosetta(W / 2, cy, 22, petali=6))
    for cx in (FX0 + 26, FX1 - 26):
        s.append(rosetta(cx, H / 2, 20, petali=6))

    s.append(medaglione(W / 2, H / 2, 94))
    s.append('</svg>')
    return "\n".join(s)


if __name__ == "__main__":
    open("dorso.svg", "w").write(build())
    print("dorso.svg scritto")
