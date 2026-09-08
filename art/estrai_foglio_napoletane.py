"""Estrae le 40 carte napoletane dal foglio 4x10.

Qui non funziona niente di quello che funzionava per gli altri mazzi. Le napoletane non hanno
cornice stampata: sono carte bianche su fondo bianco, e per giunta si sovrappongono fra loro,
quindi non c'e' nemmeno il corridoio di sfondo che separava le bergamasche. Cercare "le righe
in cui almeno il 40% dei pixel e' scuro" trova il disegno, non il bordo.

L'unica traccia del bordo e' una linea grigia sottilissima, uno o due pixel, dove una carta
copre quella accanto. E' debole (valori fra 240 e 252 dove le carte si sfiorano, molto piu'
scura dove si accavallano) e si interrompe agli angoli arrotondati, quindi presa da sola non
basta a delimitare una carta.

Le carte pero' sono disposte con passo regolare. Invece di inseguire ogni bordo, si stima UNA
griglia: si prova ogni passo e ogni fase plausibile e si tiene la combinazione su cui cade il
segnale piu' forte. Cosi' i bordi deboli non vanno persi, perche' a decidere e' la somma di
tutti e undici, non il singolo. Il risultato e' passo 352,8 orizzontale e 600,8 verticale, che
cade sui bordi osservati entro pochi pixel.

Il ritaglio viene poi rientrato di qualche pixel per lasciare fuori la linea della carta
vicina. Si perde qualche pixel di carta bianca ai bordi, che non contiene disegno.
"""
from PIL import Image
import numpy as np, os
from extract import whiten, fit_ratio

SORGENTE = '/mnt/user-data/uploads/napoletane.jpg'
SEMI_PER_RIGA = [0, 1, 3, 2]      # denari, coppe, bastoni, spade
INSET = 8        # pixel lasciati fuori dal ritaglio per non prendere il bordo del vicino
MARGINE = 0.035  # fascia esterna del ritaglio riportata a bianco pieno

def fit(strength, n, span):
    """Passo e fase della griglia che raccolgono piu' segnale di bordo."""
    best = None
    for p in np.arange(span/n*0.95, span/n*1.05, 0.05):
        for ph in np.arange(-p, 0.001, 1.0):
            xs = [int(round(ph + k*p)) for k in range(n+1)]
            sc = sum(strength[max(0, min(len(strength)-1, x-1)):
                              max(1, min(len(strength), x+2))].max() for x in xs)
            if best is None or sc > best[0]: best = (sc, p, ph)
    return best[1], best[2]

def griglia(g):
    H, W = g.shape
    faint = (g > 60) & (g < 253)
    # il massimo fra le bande, non la somma: una banda in cui il bordo e' quasi invisibile
    # non deve cancellare il contributo delle altre tre
    colstr = np.zeros(W)
    for r in range(4):
        y0, y1 = int(r*H/4), int((r+1)*H/4)
        colstr = np.maximum(colstr, faint[y0:y1].sum(0)/(y1-y0))
    rowstr = np.zeros(H)
    for c in range(10):
        x0, x1 = int(c*W/10), int((c+1)*W/10)
        rowstr = np.maximum(rowstr, faint[:, x0:x1].sum(1)/(x1-x0))
    px, phx = fit(colstr, 10, W)
    py, phy = fit(rowstr, 4, H)
    xs = [int(round(phx + k*px)) for k in range(11)]
    ys = [int(round(phy + k*py)) for k in range(5)]
    return xs, ys

def pulisci_bordo(im):
    """Riporta a bianco pieno la fascia esterna del ritaglio.

    Rientrare di otto pixel non basta: dove due carte si accavallano davvero, la linea della
    vicina entra piu' dentro, e restava una riga scura lungo un lato o un archetto negli
    angoli. Su quaranta carte solo otto uscivano pulite.

    Cancellare quella fascia e' sicuro perche' le napoletane hanno un margine bianco largo e
    il disegno non ci arriva mai: il 3,5% della larghezza sono circa dodici pixel sul ritaglio
    e sedici sull'immagine finale, mentre la figura piu' sporgente si ferma molto piu' dentro.
    Verificato su tutte e quaranta prima di fissare il valore.
    """
    a = np.asarray(im).copy()
    h, w, _ = a.shape
    b = max(1, int(round(MARGINE*w)))
    a[:b] = 255; a[-b:] = 255; a[:, :b] = 255; a[:, -b:] = 255
    return Image.fromarray(a)

def main(outdir, target=(448, 819)):
    os.makedirs(outdir, exist_ok=True)
    im = Image.open(SORGENTE).convert('RGB')
    W, H = im.size
    g = np.asarray(im).astype(int).mean(2)
    xs, ys = griglia(g)
    for r in range(4):
        for c in range(10):
            L = max(0, xs[c] + INSET);      R = min(W-1, xs[c+1] - INSET)
            T = max(0, ys[r] + INSET);      B = min(H-1, ys[r+1] - INSET)
            crop = Image.fromarray(whiten(np.asarray(im.crop((L, T, R+1, B+1)))))
            crop = pulisci_bordo(crop)
            pad = int(round(0.03*crop.width))
            fr = Image.new('RGB', (crop.width+2*pad, crop.height+2*pad), (255,255,255))
            fr.paste(crop, (pad, pad))
            out = fit_ratio(fr).resize(target, Image.LANCZOS)
            z = np.asarray(out).astype(np.float32); gz = z.mean(2, keepdims=True)
            t = np.clip((gz - 249.0)/5.0, 0, 1)
            out = Image.fromarray(np.clip(z*(1-t) + 255.0*t, 0, 255).astype(np.uint8))
            out.save(f'{outdir}/nap_{SEMI_PER_RIGA[r]}_{c+1}.png')
    print(f'40 carte, cella {xs[1]-xs[0]}x{ys[1]-ys[0]}, ritaglio {xs[1]-xs[0]-2*INSET}x{ys[1]-ys[0]-2*INSET}')

if __name__ == '__main__':
    main('/home/claude/nap_png')
