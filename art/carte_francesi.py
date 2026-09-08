"""Generatore vettoriale del mazzo francese: 52 carte + dorso, in SVG.

Perche' vettoriale e non fogli da ritagliare, come per i quattro mazzi italiani: i semi
francesi sono quattro forme geometriche e i numerali sono griglie di simboli. Si disegnano
esattamente, alla misura che serve, senza scansioni da cercare e senza questioni di licenza.

Proporzione 1,4 (500x700) e non 1,829 come le carte italiane: le carte francesi sono piu'
tozze, e nel Klondike, dove le colonne si sfogliano a ventaglio, ogni centimetro di altezza
sprecato e' una carta in meno che ci sta nello schermo.

INDICI AGLI ANGOLI, grandi. E' la scelta piu' importante del mazzo e non c'entra l'estetica:
in una colonna del Klondike di ogni carta si vede solo la fascia superiore, alta un quarto.
Il disegno al centro non lo guarda nessuno; quello che si legge e' l'angolo.
"""
import math

W, H = 500, 700
ROSSO, NERO = "#C8102E", "#1A1A1A"
ORO, BLU, CARNE = "#D9A93C", "#26467A", "#F0D2B4"

# cuori, quadri, fiori, picche: l'ordine dei semi e' quello dei mazzi italiani gia' nell'app
SEMI = [("cuori", ROSSO), ("quadri", ROSSO), ("fiori", NERO), ("picche", NERO)]
SEMI_IDX = {n: i for i, (n, _) in enumerate(SEMI)}
VALORI = ["A","2","3","4","5","6","7","8","9","10","J","Q","K"]

def seme_path(i, cx, cy, s):
    """Il simbolo del seme, centrato in (cx,cy) e alto s."""
    k = s/2
    if i == 0:   # cuori
        return (f'<path d="M {cx} {cy+k} C {cx-1.5*k} {cy-0.1*k} {cx-k} {cy-1.3*k} {cx} {cy-0.45*k} '
                f'C {cx+k} {cy-1.3*k} {cx+1.5*k} {cy-0.1*k} {cx} {cy+k} Z"/>')
    if i == 1:   # quadri
        return f'<path d="M {cx} {cy-k} L {cx+0.72*k} {cy} L {cx} {cy+k} L {cx-0.72*k} {cy} Z"/>'
    if i == 2:   # fiori
        r = 0.42*k
        return (f'<g><circle cx="{cx}" cy="{cy-0.45*k}" r="{r}"/>'
                f'<circle cx="{cx-0.55*k}" cy="{cy+0.25*k}" r="{r}"/>'
                f'<circle cx="{cx+0.55*k}" cy="{cy+0.25*k}" r="{r}"/>'
                f'<path d="M {cx-0.28*k} {cy+k} C {cx-0.05*k} {cy+0.4*k} {cx-0.05*k} {cy+0.3*k} {cx-0.06*k} {cy} '
                f'L {cx+0.06*k} {cy} C {cx+0.05*k} {cy+0.3*k} {cx+0.05*k} {cy+0.4*k} {cx+0.28*k} {cy+k} Z"/></g>')
    # picche
    return (f'<g><path d="M {cx} {cy-k} C {cx+1.5*k} {cy+0.15*k} {cx+0.95*k} {cy+0.62*k} {cx} {cy+0.28*k} '
            f'C {cx-0.95*k} {cy+0.62*k} {cx-1.5*k} {cy+0.15*k} {cx} {cy-k} Z"/>'
            f'<path d="M {cx-0.3*k} {cy+k} C {cx-0.05*k} {cy+0.5*k} {cx-0.05*k} {cy+0.4*k} {cx-0.06*k} {cy+0.12*k} '
            f'L {cx+0.06*k} {cy+0.12*k} C {cx+0.05*k} {cy+0.4*k} {cx+0.05*k} {cy+0.5*k} {cx+0.3*k} {cy+k} Z"/></g>')

# posizioni dei simboli al centro, in frazioni del riquadro interno, per ogni valore
COL_SX, COL_CX, COL_DX = 0.28, 0.5, 0.72
Y = [0.16, 0.305, 0.5, 0.695, 0.84]
DISPOSIZIONI = {
    "A":  [(COL_CX, 0.5)],
    "2":  [(COL_CX, Y[0]), (COL_CX, Y[4])],
    "3":  [(COL_CX, Y[0]), (COL_CX, Y[2]), (COL_CX, Y[4])],
    "4":  [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_SX, Y[4]), (COL_DX, Y[4])],
    "5":  [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_CX, Y[2]), (COL_SX, Y[4]), (COL_DX, Y[4])],
    "6":  [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_SX, Y[2]), (COL_DX, Y[2]), (COL_SX, Y[4]), (COL_DX, Y[4])],
    "7":  [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_CX, 0.245), (COL_SX, Y[2]), (COL_DX, Y[2]),
           (COL_SX, Y[4]), (COL_DX, Y[4])],
    "8":  [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_CX, 0.245), (COL_SX, Y[2]), (COL_DX, Y[2]),
           (COL_CX, 0.755), (COL_SX, Y[4]), (COL_DX, Y[4])],
    "9":  [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_SX, 0.38), (COL_DX, 0.38), (COL_CX, Y[2]),
           (COL_SX, 0.62), (COL_DX, 0.62), (COL_SX, Y[4]), (COL_DX, Y[4])],
    "10": [(COL_SX, Y[0]), (COL_DX, Y[0]), (COL_CX, 0.245), (COL_SX, 0.38), (COL_DX, 0.38),
           (COL_SX, 0.62), (COL_DX, 0.62), (COL_CX, 0.755), (COL_SX, Y[4]), (COL_DX, Y[4])],
}

def figura(seme, colore, valore, x0, y0, w, h):
    """Le tre figure, a due teste come nei mazzi veri.

    Simmetria centrale: si disegna la meta' superiore e la si ribalta. Oltre a dimezzare il
    lavoro e' quello che rende la carta leggibile in qualunque verso, il che nel Klondike
    serve, perche' le carte in tavola non si girano mai.

    Il disegno e' costruito con le stesse forme piatte del mazzo ZiS: sagome piene, contorno
    scuro sottile, tre colori. Nessuna sfumatura: a 60 pixel di larghezza, che e' quanto e'
    larga una carta in una colonna del Klondike, le sfumature diventano fango.
    """
    cx = x0 + w/2
    yb = y0 + h*0.5
    acc  = {"J": BLU, "Q": colore, "K": ORO}[valore]
    fond = {"J": colore, "Q": BLU, "K": colore}[valore]

    def meta(flip):
        t = f'<g transform="rotate(180 {cx} {yb})">' if flip else '<g>'
        s = [t]
        # mantello: spalle larghe che scendono fino alla linea di mezzo
        s.append(f'<path d="M {cx-w*0.34} {yb} L {cx-w*0.30} {y0+h*0.335} '
                 f'Q {cx-w*0.24} {y0+h*0.255} {cx-w*0.13} {y0+h*0.245} '
                 f'L {cx+w*0.13} {y0+h*0.245} Q {cx+w*0.24} {y0+h*0.255} {cx+w*0.30} {y0+h*0.335} '
                 f'L {cx+w*0.34} {yb} Z" fill="{fond}" stroke="{NERO}" stroke-width="2.5"/>')
        # veste centrale, colore di contrasto
        s.append(f'<path d="M {cx-w*0.115} {yb} L {cx-w*0.115} {y0+h*0.29} '
                 f'Q {cx} {y0+h*0.245} {cx+w*0.115} {y0+h*0.29} L {cx+w*0.115} {yb} Z" '
                 f'fill="{acc}" stroke="{NERO}" stroke-width="2"/>')
        # collo e collare
        s.append(f'<rect x="{cx-w*0.045}" y="{y0+h*0.215}" width="{w*0.09}" height="{h*0.045}" fill="{CARNE}"/>')
        s.append(f'<path d="M {cx-w*0.135} {y0+h*0.255} Q {cx} {y0+h*0.215} {cx+w*0.135} {y0+h*0.255} '
                 f'Q {cx} {y0+h*0.295} {cx-w*0.135} {y0+h*0.255} Z" fill="#F2F2F2" stroke="{NERO}" stroke-width="2"/>')
        # capelli dietro la testa
        s.append(f'<ellipse cx="{cx}" cy="{y0+h*0.155}" rx="{w*0.135}" ry="{h*0.088}" '
                 f'fill="{"#6B4A2A" if valore != "K" else "#8A8A8A"}" stroke="{NERO}" stroke-width="2"/>')
        # viso
        s.append(f'<ellipse cx="{cx}" cy="{y0+h*0.163}" rx="{w*0.098}" ry="{h*0.070}" '
                 f'fill="{CARNE}" stroke="{NERO}" stroke-width="2"/>')
        s.append(f'<circle cx="{cx-w*0.037}" cy="{y0+h*0.160}" r="3.2" fill="{NERO}"/>')
        s.append(f'<circle cx="{cx+w*0.037}" cy="{y0+h*0.160}" r="3.2" fill="{NERO}"/>')
        s.append(f'<path d="M {cx-w*0.030} {y0+h*0.196} Q {cx} {y0+h*0.208} {cx+w*0.030} {y0+h*0.196}" '
                 f'fill="none" stroke="{NERO}" stroke-width="2"/>')
        if valore == "K":   # barba
            s.append(f'<path d="M {cx-w*0.075} {y0+h*0.185} Q {cx} {y0+h*0.255} {cx+w*0.075} {y0+h*0.185} '
                     f'Q {cx} {y0+h*0.215} {cx-w*0.075} {y0+h*0.185} Z" fill="#8A8A8A" stroke="{NERO}" stroke-width="2"/>')
        # copricapo
        if valore == "K":
            s.append(f'<path d="M {cx-w*0.155} {y0+h*0.105} L {cx-w*0.155} {y0+h*0.040} '
                     f'L {cx-w*0.078} {y0+h*0.088} L {cx} {y0+h*0.026} L {cx+w*0.078} {y0+h*0.088} '
                     f'L {cx+w*0.155} {y0+h*0.040} L {cx+w*0.155} {y0+h*0.105} Z" '
                     f'fill="{ORO}" stroke="{NERO}" stroke-width="2.5"/>')
        elif valore == "Q":
            s.append(f'<path d="M {cx-w*0.150} {y0+h*0.100} Q {cx} {y0+h*0.028} {cx+w*0.150} {y0+h*0.100} '
                     f'L {cx+w*0.120} {y0+h*0.118} Q {cx} {y0+h*0.070} {cx-w*0.120} {y0+h*0.118} Z" '
                     f'fill="{ORO}" stroke="{NERO}" stroke-width="2.5"/>')
        else:
            s.append(f'<path d="M {cx-w*0.165} {y0+h*0.118} Q {cx-w*0.06} {y0+h*0.020} {cx+w*0.120} {y0+h*0.070} '
                     f'Q {cx+w*0.150} {y0+h*0.090} {cx+w*0.105} {y0+h*0.130} Z" '
                     f'fill="{BLU}" stroke="{NERO}" stroke-width="2.5"/>')
            s.append(f'<path d="M {cx+w*0.115} {y0+h*0.060} Q {cx+w*0.26} {y0+h*0.020} {cx+w*0.30} {y0+h*0.075}" '
                     f'fill="none" stroke="{ROSSO}" stroke-width="5" stroke-linecap="round"/>')
        # oggetto: parte dalla spalla, non fluttua
        if valore == "K":     # spada
            s.append(f'<path d="M {cx+w*0.245} {yb} L {cx+w*0.245} {y0+h*0.150} L {cx+w*0.275} {y0+h*0.115} '
                     f'L {cx+w*0.305} {y0+h*0.150} L {cx+w*0.305} {yb} Z" fill="#C9CDD2" stroke="{NERO}" stroke-width="2"/>')
            s.append(f'<rect x="{cx+w*0.205}" y="{y0+h*0.300}" width="{w*0.14}" height="{h*0.028}" '
                     f'fill="{ORO}" stroke="{NERO}" stroke-width="2"/>')
        elif valore == "Q":   # fiore
            s.append(f'<path d="M {cx+w*0.275} {yb} L {cx+w*0.275} {y0+h*0.190}" stroke="#3E7D3A" stroke-width="5"/>')
            s.append(f'<g fill="{colore}" stroke="{NERO}" stroke-width="2">'
                     + seme_path(SEMI_IDX[seme[0]], cx+w*0.275, y0+h*0.150, h*0.085) + '</g>')
        else:                 # alabarda
            s.append(f'<path d="M {cx+w*0.270} {yb} L {cx+w*0.270} {y0+h*0.145}" stroke="#7A5230" stroke-width="6"/>')
            s.append(f'<path d="M {cx+w*0.270} {y0+h*0.100} L {cx+w*0.330} {y0+h*0.160} '
                     f'L {cx+w*0.270} {y0+h*0.185} L {cx+w*0.210} {y0+h*0.160} Z" '
                     f'fill="#C9CDD2" stroke="{NERO}" stroke-width="2"/>')
        s.append('</g>')
        return "".join(s)

    return meta(False) + meta(True) + \
           f'<line x1="{x0+w*0.06}" y1="{yb}" x2="{x0+w*0.94}" y2="{yb}" stroke="{NERO}" stroke-width="2.5"/>'

def carta(i_seme, valore):
    nome, colore = SEMI[i_seme]
    m = 22                                    # margine bianco
    bx, by, bw, bh = m, m, W-2*m, H-2*m       # riquadro interno
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
           f'<rect width="{W}" height="{H}" rx="{W*0.06}" fill="#FFFFFF"/>',
           f'<rect x="3" y="3" width="{W-6}" height="{H-6}" rx="{W*0.055}" fill="none" '
           f'stroke="#9C9C9C" stroke-width="3"/>']

    # --- indici agli angoli: la parte che conta davvero ---
    ih = H*0.085
    for ruota in (False, True):
        g = f'<g transform="rotate(180 {W/2} {H/2})">' if ruota else '<g>'
        out.append(g)
        out.append(f'<text x="{m+16}" y="{m+ih*0.80}" font-family="DejaVu Sans,Arial,sans-serif" '
                   f'font-size="{ih}" font-weight="bold" fill="{colore}" text-anchor="middle">{valore}</text>')
        out.append(f'<g fill="{colore}">{seme_path(i_seme, m+16, m+ih*1.28, ih*0.62)}</g>')
        out.append('</g>')

    if valore in DISPOSIZIONI:
        px, py, pw, ph = bx+bw*0.16, by+bh*0.06, bw*0.68, bh*0.88
        s = bh*0.135
        out.append(f'<g fill="{colore}">')
        for fx, fy in DISPOSIZIONI[valore]:
            cx, cy = px+pw*fx, py+ph*fy
            grande = (valore == "A")
            out.append(f'<g transform="rotate({180 if fy>0.55 and not grande else 0} {cx} {cy})">'
                       + seme_path(i_seme, cx, cy, s*(2.6 if grande else 1.0)) + '</g>')
        out.append('</g>')
    else:
        out.append(figura(SEMI[i_seme], colore, valore, bx+bw*0.13, by+bh*0.05, bw*0.74, bh*0.90))
    out.append('</svg>')
    return "".join(out)


# ---------------------------------------------------------------------------
# Generazione dei 53 file.
#
# Nomi: fr_<seme>_<valore>, semi 0 cuori, 1 quadri, 2 fiori, 3 picche,
# valori 1..13 dove 11 = Fante, 12 = Donna, 13 = Re. Piu' fr_back.
#
# Misura 600x840, cioe' proporzione 1,4: le carte francesi sono piu' tozze delle italiane,
# e nel Klondike, dove le colonne si sfogliano a ventaglio, ogni pixel di altezza sprecato
# e' una carta in meno che entra nello schermo. Non e' un capriccio: e' la proporzione vera
# di quelle carte.
#
# 600 di larghezza e' abbondante. Nel Klondike una carta sta sui 100 pixel, ma CardView non
# ingrandisce mai una bitmap oltre la sua misura, e la stessa immagine deve servire anche
# quando la carta viene mostrata grande. Tanto vale, visto che il disegno e' vettoriale e non
# costa niente generarlo alla misura che si vuole.
# ---------------------------------------------------------------------------

OUT_W, OUT_H = 600, 840

def genera(outdir):
    import os, io, cairosvg
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    tot = 0
    for s in range(4):
        for i, v in enumerate(VALORI):
            png = cairosvg.svg2png(bytestring=carta(s, v).encode(),
                                   output_width=OUT_W, output_height=OUT_H)
            im = Image.open(io.BytesIO(png)).convert('RGB')
            f = f'{outdir}/fr_{s}_{i+1}.webp'
            im.save(f, 'WEBP', quality=92, method=6)
            tot += os.path.getsize(f)
    return tot

def genera_dorso(outdir, card_back_py):
    """Il dorso riusa il generatore gia' nel progetto, con una palette in piu'.

    Le misure sono variabili di modulo, quindi si sovrascrivono prima di costruire: cosi'
    il dorso francese esce a 600x840 come le carte, senza duplicare il disegno.
    """
    import importlib.util, io, os, cairosvg
    from PIL import Image
    spec = importlib.util.spec_from_file_location('card_back', card_back_py)
    cb = importlib.util.module_from_spec(spec); spec.loader.exec_module(cb)
    cb.W, cb.H = OUT_W, OUT_H
    # anche il riquadro del campo deriva da W e H: ricalcolarlo con le stesse formule del
    # modulo, altrimenti resterebbe quello della carta italiana e il disegno uscirebbe storto
    cb.FX0, cb.FY0, cb.FX1, cb.FY1 = 34, 34, cb.W-34, cb.H-34
    cb.PALETTES['fr'] = dict(bg_outer="#3A0E12", bg_field="#5E161C", lattice="#8A2A30",
                             ring="#1F3C6B", accent="#D9A93C", accent_hi="#F2DFA4",
                             line="#EBD9A8", line_soft="#B08A3C")
    svg = cb.build('fr')
    png = cairosvg.svg2png(bytestring=svg.encode(), output_width=OUT_W, output_height=OUT_H)
    f = f'{outdir}/fr_back.webp'
    Image.open(io.BytesIO(png)).convert('RGB').save(f, 'WEBP', quality=92, method=6)
    return os.path.getsize(f)
