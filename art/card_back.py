"""
Dorsi delle carte ZiS, disegnati in vettoriale.

Un solo disegno, tre palette: il dorso blu/argento per le illustrazioni ZiS e due
versioni in bianco/nero/grigio per il mazzo tradizionale, una scura e una chiara.
Nascono a 448x819, la proporzione esatta delle carte, quindi non subiscono
ridimensionamenti. Simmetrici rispetto a entrambi gli assi: identici anche capovolti,
come deve essere il dorso di una carta da gioco.
"""
import math

W, H = 448, 819

# Ruoli dei colori. Cambiando la palette cambia il dorso, il disegno resta lo stesso.
#   bg_outer  fondo della cornice esterna        line       linee principali dell'ornato
#   bg_field  fondo del campo                    line_soft  linee secondarie
#   lattice   trama diagonale sotto al campo     accent     bottoni e raggi
#   ring      cerchi intrecciati del campo       accent_hi  punte piu' chiare
PALETTES = {
    # blu notte e argento, presi da res/values/colors.xml
    "zis": dict(bg_outer="#0C1626", bg_field="#14294A", lattice="#23477E", ring="#3E6BA6",
                accent="#A9D0EC", accent_hi="#D7EAF6", line="#C8D0DC", line_soft="#AEB9C6"),
    # grigi scuri: sta sul velluto come quello blu
    "grey": dict(bg_outer="#0E0E0E", bg_field="#1E1E1E", lattice="#343434", ring="#6E6E6E",
                 accent="#C9C9C9", accent_hi="#EDEDED", line="#D6D6D6", line_soft="#A6A6A6"),
    # la stessa incisione rovesciata: ornato scuro su fondo chiaro
    "grey_light": dict(bg_outer="#E8E8E8", bg_field="#FBFBFB", lattice="#D2D2D2", ring="#9C9C9C",
                       accent="#3C3C3C", accent_hi="#1A1A1A", line="#2B2B2B", line_soft="#5E5E5E"),
}

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


def reticolo(p):
    """Campo: cerchi intrecciati su reticolo quadrato. Si sovrappongono e formano
       i quadrilobi tipici dei dorsi. Sotto, una trama diagonale piu' tenue."""
    s = 46.0
    r = s * 0.707
    out = ['<g clip-path="url(#campo)">']

    out.append(f'<g stroke="{p["lattice"]}" stroke-width="0.9" fill="none" opacity="0.9">')
    for k in range(-40, 60):
        c = k * 16
        out.append(f'<path d="M {FX0-40} {FY0 + c} L {FX1+40} {FY0 + c - (FX1-FX0)-80}"/>')
        out.append(f'<path d="M {FX0-40} {FY0 + c} L {FX1+40} {FY0 + c + (FX1-FX0)+80}"/>')
    out.append('</g>')

    cx0, cy0 = W / 2, H / 2
    ni = int((FX1 - FX0) / s) + 3
    nj = int((FY1 - FY0) / s) + 3
    nodi = [(cx0 + i * s, cy0 + j * s)
            for i in range(-ni, ni + 1) for j in range(-nj, nj + 1)]

    out.append(f'<g fill="none" stroke="{p["ring"]}" stroke-width="2.1" opacity="0.80">')
    for x, y in nodi:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.2f}"/>')
    out.append('</g>')
    out.append(f'<g fill="none" stroke="{p["line_soft"]}" stroke-width="0.9" opacity="0.70">')
    for x, y in nodi:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r:.2f}"/>')
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{r*0.34:.2f}"/>')
    out.append('</g>')
    out.append(f'<g fill="{p["accent"]}">')
    for x, y in nodi:
        out.append(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="2.9"/>')
    out.append('</g>')
    out.append(f'<g fill="{p["line"]}">')
    for x, y in nodi:
        mx, my = x + s / 2, y + s / 2
        stella = []
        for k in range(8):
            a = math.pi / 4 * k
            rr = 7.5 if k % 2 == 0 else 2.8
            stella.append((mx + math.cos(a) * rr, my + math.sin(a) * rr))
        out.append(f'<polygon points="{pts(stella)}"/>')
    out.append('</g>')
    out.append('</g>')
    return "\n".join(out)


def rosetta(p, cx, cy, r, petali=8, sw=1.5):
    # alone: stacca la rosetta dal reticolo del campo
    out = [f'<circle cx="{cx}" cy="{cy}" r="{r*1.22:.2f}" fill="{p["bg_outer"]}" opacity="0.85"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{r:.2f}" fill="{p["bg_outer"]}" stroke="{p["line"]}" stroke-width="{sw+0.6}"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{r*0.86:.2f}" fill="none" stroke="{p["line_soft"]}" stroke-width="0.9"/>']
    for k in range(petali):
        a = 2 * math.pi * k / petali
        px, py = cx + math.cos(a) * r * 0.55, cy + math.sin(a) * r * 0.55
        out.append(f'<ellipse cx="{px:.2f}" cy="{py:.2f}" rx="{r*0.30:.2f}" ry="{r*0.15:.2f}" '
                   f'transform="rotate({math.degrees(a):.1f} {px:.2f} {py:.2f})" '
                   f'fill="{p["ring"]}" stroke="{p["line"]}" stroke-width="0.9"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{r*0.19:.2f}" fill="{p["accent_hi"]}"/>')
    return "\n".join(out)


def medaglione(p, cx, cy, R):
    out = [f'<circle cx="{cx}" cy="{cy}" r="{R+16:.2f}" fill="{p["bg_outer"]}" opacity="0.9"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{R+10:.2f}" fill="{p["bg_field"]}" stroke="{p["line"]}" stroke-width="1.3"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{R:.2f}" fill="{p["bg_outer"]}" stroke="{p["line"]}" stroke-width="2.6"/>',
           f'<circle cx="{cx}" cy="{cy}" r="{R*0.93:.2f}" fill="none" stroke="{p["line_soft"]}" stroke-width="1.0"/>']
    n = 40
    for k in range(n):
        a = 2 * math.pi * k / n
        out.append(f'<circle cx="{cx + math.cos(a)*R*0.85:.2f}" cy="{cy + math.sin(a)*R*0.85:.2f}" '
                   f'r="{R*0.032:.2f}" fill="{p["line"]}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.76:.2f}" fill="{p["bg_field"]}" stroke="{p["line_soft"]}" stroke-width="1.1"/>')
    for k in range(32):
        a = 2 * math.pi * k / 32
        r1, r2 = R * 0.42, R * 0.74
        w = 0.032 if k % 2 == 0 else 0.017
        raggio = [(cx + math.cos(a - w) * r1, cy + math.sin(a - w) * r1),
                  (cx + math.cos(a) * r2, cy + math.sin(a) * r2),
                  (cx + math.cos(a + w) * r1, cy + math.sin(a + w) * r1)]
        out.append(f'<polygon points="{pts(raggio)}" fill="{p["accent"] if k % 2 == 0 else p["ring"]}"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.42:.2f}" fill="{p["bg_outer"]}" stroke="{p["line"]}" stroke-width="1.9"/>')
    for k in range(4):
        a = math.pi / 2 * k
        petalo = [(cx + math.cos(a) * R * 0.36, cy + math.sin(a) * R * 0.36),
                  (cx + math.cos(a + math.pi/4) * R * 0.14, cy + math.sin(a + math.pi/4) * R * 0.14),
                  (cx + math.cos(a + math.pi/2) * R * 0.36, cy + math.sin(a + math.pi/2) * R * 0.36),
                  (cx, cy)]
        out.append(f'<polygon points="{pts(petalo)}" fill="{p["accent"] if k % 2 == 0 else p["accent_hi"]}" '
                   f'stroke="{p["line"]}" stroke-width="0.8"/>')
    out.append(f'<circle cx="{cx}" cy="{cy}" r="{R*0.08:.2f}" fill="{p["line"]}"/>')
    return "\n".join(out)


def build(palette="zis"):
    p = PALETTES[palette]
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
         '<defs><clipPath id="campo">'
         f'<rect x="{FX0}" y="{FY0}" width="{FX1-FX0}" height="{FY1-FY0}"/></clipPath></defs>']

    s.append(f'<rect width="{W}" height="{H}" fill="{p["bg_outer"]}"/>')
    s.append(f'<rect x="6" y="6" width="{W-12}" height="{H-12}" fill="{p["bg_field"]}" '
             f'stroke="{p["line"]}" stroke-width="2.4"/>')
    s.append(f'<rect x="12" y="12" width="{W-24}" height="{H-24}" fill="none" '
             f'stroke="{p["line_soft"]}" stroke-width="0.9"/>')
    s.append(scallops(18, 18, W - 18, H - 18, 10, p["line_soft"], 1.2))
    s.append(f'<rect x="{FX0-4}" y="{FY0-4}" width="{FX1-FX0+8}" height="{FY1-FY0+8}" '
             f'fill="none" stroke="{p["line"]}" stroke-width="1.8"/>')

    s.append(reticolo(p))

    s.append(f'<rect x="{FX0}" y="{FY0}" width="{FX1-FX0}" height="{FY1-FY0}" '
             f'fill="none" stroke="{p["ring"]}" stroke-width="1.0"/>')

    for cx, cy in ((FX0 + 40, FY0 + 40), (FX1 - 40, FY0 + 40),
                   (FX0 + 40, FY1 - 40), (FX1 - 40, FY1 - 40)):
        s.append(rosetta(p, cx, cy, 28))
    for cy in (H / 2 - 190, H / 2 + 190):
        s.append(rosetta(p, W / 2, cy, 22, petali=6))
    for cx in (FX0 + 26, FX1 - 26):
        s.append(rosetta(p, cx, H / 2, 20, petali=6))

    s.append(medaglione(p, W / 2, H / 2, 94))
    s.append('</svg>')
    return "\n".join(s)


if __name__ == "__main__":
    for nome in PALETTES:
        open(f"card_back_{nome}.svg", "w").write(build(nome))
        print(f"card_back_{nome}.svg scritto")
