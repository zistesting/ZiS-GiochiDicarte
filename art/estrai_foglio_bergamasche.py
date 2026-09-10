"""Estrae le 40 carte bergamasche dal foglio 4x10.

Diverso dagli altri estrattori: qui le carte stanno tutte su un foglio unico, quattro
righe da dieci, e il foglio e' una scansione, quindi le carte non sono allineate al pixel.

Il riquadro di ogni carta si trova cella per cella cercando le righe e le colonne in cui
almeno il 40% dei pixel e' scuro, cioe' il bordo stampato. Dove il bordo del vicino sconfina
nella cella la misura viene fuori sbagliata, e si riconosce da sola: le quaranta carte sono
tutte uguali, quindi una larghezza o un'altezza che si discosta piu' del 5% dalla mediana
viene riportata alla mediana, ancorandola al lato piu' lontano dal confine della cella,
che e' quello sicuramente non tagliato.

Le bergamasche sono piu' strette delle altre: il riquadro stampato ha proporzione 1,98
contro l'1,78 dei due mazzi gia' nell'app. Non si stira per pareggiare, si aggiunge margine
bianco: e' la forma vera di quelle carte.
"""
from PIL import Image
import numpy as np, os, statistics
from extract import whiten, fit_ratio

SORGENTE = '/mnt/user-data/uploads/Carte_bergamasche.jpg'
# righe del foglio, dall'alto: un seme per riga, in ogni riga A,2,3,4,5,6,7,Fante,Cavallo,Re
SEMI_PER_RIGA = [3, 2, 1, 0]        # bastoni, spade, coppe, denari
VALORI = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

def clusters(idx, minlen=3):
    out=[]; s=p=None
    for x in idx:
        if s is None: s=p=x; continue
        if x-p>1: out.append((s,p)); s=x
        p=x
    if s is not None: out.append((s,p))
    return [c for c in out if c[1]-c[0]+1 >= minlen]

def riquadri(g):
    H, W = g.shape
    grezzi = []
    for r in range(4):
        y0, y1 = int(r*H/4), int((r+1)*H/4)
        for c in range(10):
            x0, x1 = int(c*W/10), int((c+1)*W/10)
            sub = g[y0:y1, x0:x1]; h, w = sub.shape
            m = sub < 200
            cc = clusters(np.where(m.sum(0) > 0.4*h)[0])
            rr = clusters(np.where(m.sum(1) > 0.4*w)[0])
            grezzi.append([x0+cc[0][0], x0+cc[-1][1], y0+rr[0][0], y0+rr[-1][1], x0, x1, y0, y1])

    mw = statistics.median(b[1]-b[0] for b in grezzi)
    mh = statistics.median(b[3]-b[2] for b in grezzi)
    for b in grezzi:
        L, R, T, B, x0, x1, y0, y1 = b
        if abs((R-L) - mw) > 0.05*mw:
            if (L-x0) >= (x1-1-R): R = int(L+mw)
            else:                  L = int(R-mw)
            b[0], b[1] = L, R
        if abs((B-T) - mh) > 0.05*mh:
            if (T-y0) >= (y1-1-B): B = int(T+mh)
            else:                  T = int(B-mh)
            b[2], b[3] = T, B
    return grezzi

def main(outdir, target=(448, 819)):
    os.makedirs(outdir, exist_ok=True)
    im = Image.open(SORGENTE).convert('RGB')
    g = np.asarray(im).astype(int).mean(2)
    box = riquadri(g)
    for i, (L, R, T, B, *_ ) in enumerate(box):
        seme = SEMI_PER_RIGA[i // 10]
        valore = VALORI[i % 10]
        crop = Image.fromarray(whiten(np.asarray(im.crop((L, T, R+1, B+1)))))
        pad = int(round(0.03*crop.width))       # stesso margine degli altri mazzi
        framed = Image.new('RGB', (crop.width+2*pad, crop.height+2*pad), (255,255,255))
        framed.paste(crop, (pad, pad))
        out = fit_ratio(framed).resize(target, Image.LANCZOS)
        z = np.asarray(out).astype(np.float32); gz = z.mean(2, keepdims=True)
        t = np.clip((gz - 249.0)/5.0, 0, 1)
        out = Image.fromarray(np.clip(z*(1-t) + 255.0*t, 0, 255).astype(np.uint8))
        out.save(f'{outdir}/berg_{seme}_{valore}.png')
    print(f'40 carte estratte, riquadro mediano '
          f'{int(statistics.median(b[1]-b[0] for b in box))}x{int(statistics.median(b[3]-b[2] for b in box))}')

if __name__ == '__main__':
    main('/home/claude/berg_png')
