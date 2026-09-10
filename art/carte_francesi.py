"""Generatore del mazzo francese: 52 carte + dorso, in SVG.

Cornice, indici e simboli dei semi sono vettoriali: i semi francesi sono quattro forme
geometriche, si disegnano esattamente alla misura che serve, senza fogli da ritagliare come
per i quattro mazzi italiani.

Le tre figure invece no: sono tre immagini piatte in art/figure/, ricolorate per seme (vedi
CROMIA piu' sotto). Il disegno di una figura di carte non si cava da qualche curva di Bezier,
e il tentativo vettoriale precedente si vedeva: pupazzi geometrici a due teste, larghi meno
di un dito.

Proporzione 1,4 (500x700) e non 1,829 come le carte italiane: le carte francesi sono piu'
tozze, e nel Klondike, dove le colonne si sfogliano a ventaglio, ogni centimetro di altezza
sprecato e' una carta in meno che ci sta nello schermo.

UN INDICE SOLO, in alto a sinistra, e grande. E' la scelta piu' importante del mazzo e non
c'entra l'estetica: in una colonna del Klondike di ogni carta si vede solo la fascia
superiore, alta un quarto. Il disegno al centro non lo guarda nessuno; quello che si legge
e' l'angolo - e sempre lo stesso angolo, perche' qui le carte non si girano mai.
"""
import base64
import io
import os

W, H = 500, 700
ROSSO, NERO = "#C8102E", "#1A1A1A"

# ---------------------------------------------------------------------------
# LE TRE FIGURE
#
# J, Q e K non sono piu' disegnate qui: vengono da tre immagini piatte in
# art/figure/ (fante.png, donna.png, re.png). Sono normalizzate a QUATTRO soli
# colori e con lo sfondo trasparente, e questo e' il punto: la ricolorazione per
# seme e' una sostituzione esatta di quattro valori RGB, non un filtro. Niente
# tinte intermedie da indovinare, nessun alone.
#
# I file sono a bordi netti e a risoluzione quadrupla rispetto all'originale:
# l'antialiasing non ci sta dentro, se lo prendono quando la figura viene
# rimpicciolita nella carta. Fatto al contrario - bordi morbidi nel file e
# rimpicciolimento dopo - la ricolorazione per corrispondenza esatta non
# funzionerebbe piu', perche' i pixel di bordo non sono nessuno dei quattro.
#
# CROMIA PER SEME. Il vincolo non e' estetico: nel Klondike le colonne si
# costruiscono a COLORI ALTERNATI, quindi rosso e nero devono restare
# inconfondibili. Percio' i due semi rossi hanno due rossi (carminio e
# vermiglio) e i due neri due scuri freddi (verde e ardesia): si distinguono
# fra loro senza che nessuno dei quattro possa essere preso per l'altro colore.
# Il tratto resta quasi nero in tutti e quattro, e il chiaro resta bianco: sono
# il contorno e il viso, e cambiarli farebbe perdere leggibilita' al disegno.
# ---------------------------------------------------------------------------

FIG_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "figure")
FIG_FILE = {"J": "fante.png", "Q": "donna.png", "K": "re.png"}

# i quattro colori con cui i file sono salvati
FIG_RIF = {"chiaro": (255, 255, 255), "manto": (140, 45, 30),
           "accento": (175, 117, 57), "tratto": (12, 12, 12)}

CROMIA = [
    dict(manto="#A8231C", accento="#D6A23C", tratto="#141414", chiaro="#FFFFFF"),  # 0 cuori
    dict(manto="#BF5320", accento="#E7C15C", tratto="#141414", chiaro="#FFFFFF"),  # 1 quadri
    dict(manto="#2E4A3E", accento="#A9C0B2", tratto="#0B100D", chiaro="#FFFFFF"),  # 2 fiori
    dict(manto="#33404F", accento="#B5C1CE", tratto="#0A0D12", chiaro="#FFFFFF"),  # 3 picche
]

# ---------------------------------------------------------------------------
# L'INDICE, e perche' e' UNO SOLO
#
# Le carte vere hanno l'indice a due angoli opposti perche' una carta in mano la
# puoi tenere in due versi. Nel Klondike non succede mai: le carte stanno in tavola
# sempre nello stesso verso, e questo mazzo lo usa solo lui.
#
# E non e' che il secondo indice si veda poco: non si vede MAI. In colonna ogni
# carta e' coperta DAL BASSO da quella sotto, quindi si vede la fascia in alto. Nel
# ventaglio degli scarti le carte vecchie sono coperte A DESTRA da quella in cima,
# quindi si vede ancora l'angolo in alto a sinistra. Nelle fondazioni si vede solo
# la carta in cima. L'unico caso in cui l'indice in basso a destra e' visibile e'
# l'ultima carta di una colonna, dove sopra c'e' gia' quello giusto.
#
# Toglierlo libera un terzo dell'altezza: il pannello centrale passa dal 30% al 64%.
#
# ALLINEATO SULL'INCHIOSTRO, non sulla linea di base. Si misura l'inchiostro di ogni
# valore (vedi ink) e si sceglie il font-size perche' il CORPO venga uguale per
# tutti; da li' esce anche una sola linea di base, e il simbolo del seme si piazza
# dopo la larghezza MISURATA del valore. Risolve due difetti che il mazzo aveva:
#
#  - sul 10 il simbolo del seme finiva sopra allo zero. cairosvg ignora textLength,
#    quindi il valore usciva alla sua larghezza naturale (321 px invece dei 212
#    chiesti) e il simbolo, piazzato dove textLength diceva, gli andava addosso;
#  - in colonna il J si leggeva come una I, perche' il gancio scendeva fino a 0,360
#    dell'altezza mentre la fascia visibile si fermava a 0,300. Alla Q accadeva lo
#    stesso con la coda.
#
# Adesso l'inchiostro di QUALUNQUE valore sta dentro INK_TOP..INK_BOT, e lo sfalso
# delle carte scoperte in colonna (SFALSO_SCOPERTA in KlondikeActivity) e' 0,32,
# cioe' piu' di INK_BOT: l'indice si vede sempre intero.
# ---------------------------------------------------------------------------

INK_TOP, INK_BOT = 0.030, 0.310   # fascia in cui sta l'inchiostro, per qualunque valore
SEME_H = 0.225                    # altezza del simbolo del seme
GAP = 0.045                       # spazio fra valore e seme, in frazioni dell'altezza
X0 = 0.056                        # dove comincia l'inchiostro del valore, in larghezza

# Il pannello centrale. Comincia allo stesso punto per ogni valore, perche' l'indice
# finisce sempre a INK_BOT: non serve una misura per carta come quando gli indici
# erano due e ognuno finiva a modo suo.
PAN_Y0, PAN_Y1 = 0.325, 0.965

PIP = 0.185        # altezza di un simbolo della griglia, in frazioni del pannello

# Le Y di DISPOSIZIONI coprono 0,16-0,84 del riquadro di allora: qui si riportano nel
# pannello. Non con un fattore di allargamento a occhio, ma mandando i simboli estremi a
# toccare esattamente i bordi: cosi' NIENTE sporge sopra PAN_Y0.
#
# Non e' un cavillo. Con un fattore a occhio il primo simbolo finiva quindici pixel sopra
# il pannello, cioe' dentro la fascia che in colonna resta visibile: sotto l'indice di
# ogni numerale si vedevano due puntine scure, le cime dei due simboli di sopra.
def _y_pip(fy):
    return PIP/2 + (fy - 0.16) / (0.84 - 0.16) * (1 - PIP)
FONT = "DejaVu Sans,Arial,sans-serif"

_ink = {}

def ink(valore):
    """Inchiostro del valore a font-size 100: (sinistra, larghezza, sopra la base, totale).

    Si rasterizza e si misura invece di leggere le metriche del font. Non e' pigrizia:
    le metriche richiederebbero di sapere quale font e' installato sulla macchina che
    genera i file, e il font-family qui e' un elenco con dei ripieghi. Misurando il
    risultato vero il conto e' esatto con qualunque font ci sia.
    """
    if valore in _ink:
        return _ink[valore]
    import cairosvg
    import numpy as np
    from PIL import Image
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="800" height="500">'
           f'<rect width="800" height="500" fill="#fff"/>'
           f'<text x="80" y="360" font-family="{FONT}" font-size="100" '
           f'font-weight="bold" fill="#000">{valore}</text></svg>')
    a = np.asarray(Image.open(io.BytesIO(cairosvg.svg2png(
        bytestring=svg.encode(), output_width=800, output_height=500))).convert('L'))
    ys, xs = np.where(a < 200)
    _ink[valore] = (float(xs.min() - 80), float(xs.max() - xs.min() + 1),
                    float(360 - ys.min()), float(ys.max() - ys.min() + 1))
    return _ink[valore]

_larg_seme = {}

def larg_seme(i):
    """Larghezza del simbolo del seme in frazioni della sua altezza, misurata.

    Serve a sapere quanto spazio prenotare a destra dell'indice. Misurata e non
    scritta a mano perche' dipende da seme_path: il quadri e' largo il 72%
    dell'altezza, gli altri tre stanno sopra il 94%.
    """
    if i in _larg_seme:
        return _larg_seme[i]
    import cairosvg
    import numpy as np
    from PIL import Image
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400">'
           f'<rect width="400" height="400" fill="#fff"/><g fill="#000">'
           + seme_path(i, 200, 200, 100) + '</g></svg>')
    a = np.asarray(Image.open(io.BytesIO(cairosvg.svg2png(
        bytestring=svg.encode(), output_width=400, output_height=400))).convert('L'))
    xs = np.where((a < 200).any(axis=0))[0]
    _larg_seme[i] = (xs.max() - xs.min() + 1) / 100.0
    return _larg_seme[i]

def corpo():
    """Altezza del corpo del valore, uguale per tutti i valori.

    La fissa il valore che ha piu' roba sotto la linea di base, cioe' il J col suo
    gancio: dandogli tutto lo spazio INK_TOP..INK_BOT, il suo corpo viene 1/1,27 di
    quello spazio, e tutti gli altri si adeguano a quella misura. Cosi' l'occhio
    legge indici della stessa grandezza e nessun glifo sborda dalla fascia.
    """
    return (INK_BOT - INK_TOP) * H / max(a / s for _, _, s, a in (ink(v) for v in VALORI))

_fig_cache = {}

def figura_png(valore, i_seme):
    """La figura del valore indicato, ricolorata per il seme: (base64, larghezza, altezza)."""
    chiave = (valore, i_seme)
    if chiave in _fig_cache:
        return _fig_cache[chiave]
    from PIL import Image
    import numpy as np
    im = Image.open(os.path.join(FIG_DIR, FIG_FILE[valore])).convert("RGBA")
    a = np.asarray(im).copy()
    rgb = a[:, :, :3]
    tinte = CROMIA[i_seme]
    for nome, sorgente in FIG_RIF.items():
        d = tinte[nome].lstrip("#")
        dest = (int(d[0:2], 16), int(d[2:4], 16), int(d[4:6], 16))
        sel = (rgb[:, :, 0] == sorgente[0]) & (rgb[:, :, 1] == sorgente[1]) & (rgb[:, :, 2] == sorgente[2])
        a[sel, 0], a[sel, 1], a[sel, 2] = dest
    buf = io.BytesIO()
    Image.fromarray(a, "RGBA").save(buf, "PNG", optimize=True)
    v = (base64.b64encode(buf.getvalue()).decode("ascii"), im.width, im.height)
    _fig_cache[chiave] = v
    return v

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

def carta(i_seme, valore):
    nome, colore = SEMI[i_seme]
    m = 22                                    # margine bianco
    bx, bw = m, W - 2*m                       # riquadro interno, in larghezza
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" '
           f'width="{W}" height="{H}" viewBox="0 0 {W} {H}">',
           f'<rect width="{W}" height="{H}" rx="{W*0.06}" fill="#FFFFFF"/>',
           f'<rect x="3" y="3" width="{W-6}" height="{H-6}" rx="{W*0.055}" fill="none" '
           f'stroke="#9C9C9C" stroke-width="3"/>']

    # --- L'INDICE: uno solo, in alto a sinistra, allineato sull'inchiostro ---
    #
    # Valore e seme stanno AFFIANCATI, non incolonnati: nella fascia alta lo spazio
    # scarseggia in verticale e abbonda in orizzontale, e mettendoli uno sopra l'altro si
    # finiva per rimpicciolire entrambi.
    B = corpo()
    sin, larg, sopra, _ = ink(valore)
    fs = B / sopra * 100.0
    sw = H * SEME_H * larg_seme(i_seme)                  # larghezza del simbolo del seme
    x0 = W * X0
    largo_max = (W - m) - x0 - H * GAP - sw
    # Rete di sicurezza: col font previsto il 10, che e' il valore piu' largo, entra di
    # una decina di pixel. Con un font piu' largo non entrerebbe, e allora l'unico a
    # stringere e' lui - meglio un 10 un filo piu' piccolo che un 10 col seme addosso.
    if larg * fs / 100.0 > largo_max:
        fs = 100.0 * largo_max / larg
    out.append(f'<text x="{x0 - sin*fs/100.0}" y="{INK_TOP*H + B}" font-family="{FONT}" '
               f'font-size="{fs}" font-weight="bold" fill="{colore}">{valore}</text>')
    sx = x0 + larg * fs / 100.0 + H * GAP
    out.append(f'<g fill="{colore}">'
               + seme_path(i_seme, sx + sw/2, INK_TOP*H + B*0.52, H*SEME_H) + '</g>')

    py0, py1 = H * PAN_Y0, H * PAN_Y1
    ph = py1 - py0

    if valore == "A":
        # L'Asso fa storia a se': un simbolo solo, grande, al centro del pannello. Con la
        # misura degli altri numerali diventava un puntino in mezzo al vuoto.
        out.append(f'<g fill="{colore}">'
                   + seme_path(i_seme, bx + bw/2, py0 + ph/2, ph*0.42) + '</g>')
    elif valore in DISPOSIZIONI:
        # LA GRIGLIA TRADIZIONALE, non un simbolo unico al centro.
        #
        # Il ragionamento che aveva portato al simbolo unico era che dieci quadri
        # affiancati su una carta larga 140 pixel diventano una macchia da contare, e a
        # quella misura non si contano. E' vero, ma risponde a una domanda che nessuno
        # pone: il valore lo dice l'indice, e in colonna il centro della carta non si vede
        # affatto, tranne che sull'ultima carta di ogni colonna. Quindi la scelta non e'
        # fra due leggibilita', e' fra due decorazioni - e fra le due vince quella che fa
        # sembrare una carta da gioco una carta da gioco.
        #
        # Le posizioni sono quelle di DISPOSIZIONI, che era gia' scritta qui e di cui
        # prima si usavano SOLO le chiavi, come test per distinguere numerali e figure.
        pip = ph * PIP
        for fx, fy in DISPOSIZIONI[valore]:
            cx, cy = bx + bw*fx, py0 + ph*_y_pip(fy)
            # meta' bassa capovolta, come nei mazzi veri. Con un indice solo la carta non
            # e' piu' simmetrica, quindi e' decorazione: ma e' quella che la fa leggere
            # come una carta invece che come una griglia di icone.
            g = (f'<g fill="{colore}" transform="rotate(180 {cx} {cy})">'
                 if fy > 0.5 else f'<g fill="{colore}">')
            out.append(g + seme_path(i_seme, cx, cy, pip) + '</g>')
    else:
        # La figura riempie il pannello. Entra nell'SVG come PNG in base64, quindi il file
        # resta uno solo e cairosvg non ha percorsi da risolvere. Comanda la larghezza:
        # con la proporzione di queste tre immagini l'altezza avanza sempre. Appoggiata in
        # basso, perche' e' un mezzo busto e la testa deve finire sotto l'indice.
        b64, iw, ih = figura_png(valore, i_seme)
        fw = bw * 0.94
        fh = fw * ih / iw
        if fh > ph:
            fh = ph
            fw = fh * iw / ih
        out.append(f'<image x="{(W-fw)/2}" y="{py1-fh}" width="{fw}" height="{fh}" '
                   f'xlink:href="data:image/png;base64,{b64}"/>')

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
