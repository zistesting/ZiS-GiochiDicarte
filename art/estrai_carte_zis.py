from PIL import Image
import numpy as np, os, statistics

SRC = '/mnt/user-data/uploads'
SUITS = {'denari':0, 'coppe':1, 'spade':2, 'bastoni':3}
# ogni foglio: A = 5 4 3 2 Asso, B = Re Cavallo Fante 7 6
ORDER = {'A':[5,4,3,2,1], 'B':[10,9,8,7,6]}

def card_boxes(g):
    H, W = g.shape
    strong_col = (g < 238).sum(0) > 0.5*H
    idx = np.where(strong_col)[0]
    L0, R0 = int(idx[0]), int(idx[-1])
    span = R0 - L0 + 1
    slot = span / 5.0
    boxes = []
    for i in range(5):
        a = int(round(L0 + i*slot)); b = int(round(L0 + (i+1)*slot))
        sub = g[:, a:b]; h, w = sub.shape
        m = sub < 238
        cc = m.sum(0); rr = m.sum(1)
        ci = np.where(cc > 0.5*h)[0]
        ri = np.where(rr > 0.5*w)[0]
        L = a + int(ci[0]);  R = a + int(ci[-1])
        T = int(ri[0]);      B = int(ri[-1])
        boxes.append([L, R, T, B, a, b])
    # larghezza mediana: le carte sono tutte uguali, quindi una misura fuori scala
    # e' un bordo tagliato dal confine dello slot, non una carta diversa
    med = statistics.median(bx[1]-bx[0] for bx in boxes)
    for bx in boxes:
        L, R, T, B, a, b = bx
        if abs((R-L) - med) > 0.05*med:
            # tengo il lato piu' lontano dal confine dello slot, cioe' quello non tagliato
            if (L - a) >= (b-1 - R): R = int(L + med)
            else:                    L = int(R - med)
            bx[0], bx[1] = L, R
    return boxes

def whiten(arr):
    """Porta il fondo della carta a bianco pieno (255,255,255) senza toccare il disegno.

    Il punto delicato e' capire QUAL E' il bianco. Nei fogli lo sfondo attorno alle carte
    e' gia' 255, ma la faccia della carta no: in coppe_B, denari_B e spade_B e' un grigio
    chiaro attorno a 238. Prendere il bianco dallo sfondo del foglio, come farebbe un
    percentile sull'immagine intera, lascerebbe quel grigio dov'e'.

    Quindi il bianco si stima dalla MODA dei pixel chiari del ritaglio, cioe' dal valore
    che ricorre di piu' sopra 200: e' la faccia della carta, che occupa piu' area di
    qualunque altra tinta chiara. Da li' due passaggi:
      1) bilanciamento: ogni canale diviso per il suo valore sulla carta, cosi' la carta
         finisce esattamente a 255. Sui toni scuri l'effetto e' minimo, il tratto non cambia.
      2) appiattimento morbido: quello che a quel punto e' gia' quasi bianco va a bianco
         pieno, con una rampa e non con una soglia secca, altrimenti i contorni morbidi del
         disegno prenderebbero un alone. La rampa parte da 244: le lame d'argento delle
         spade e le cornici grigie stanno molto sotto, quindi restano intatte.
    """
    a = arr.astype(np.float32)
    g = a.mean(2)
    bright = g > 200
    if bright.sum() > 50:
        hist = np.bincount(g[bright].astype(int), minlength=256)
        peak = int(np.argmax(hist))
        band = bright & (np.abs(g - peak) <= 3)
        paper = np.array([np.median(a[..., c][band]) for c in range(3)], dtype=np.float32)
    else:
        paper = np.array([255.0, 255.0, 255.0], dtype=np.float32)
    paper = np.maximum(paper, 200.0)
    a = np.clip(a * (255.0/paper), 0, 255)
    g = a.mean(2, keepdims=True)
    t = np.clip((g - 244.0)/8.0, 0, 1)      # 244 -> invariato, 252 -> bianco pieno
    a = a*(1-t) + 255.0*t
    return np.clip(a, 0, 255).astype(np.uint8)

def fit_ratio(im, ratio=819/448.0):
    """Porta l'immagine alla proporzione voluta AGGIUNGENDO bianco, mai stirando."""
    w, h = im.size
    if h/w < ratio:  nw, nh = w, int(round(w*ratio))
    else:            nw, nh = int(round(h/ratio)), h
    out = Image.new('RGB', (max(nw,w), max(nh,h)), (255,255,255))
    out.paste(im, ((out.width-w)//2, (out.height-h)//2))
    return out

def main(outdir, target=(224,410)):
    os.makedirs(outdir, exist_ok=True)
    report=[]
    for suit, s in sorted(SUITS.items(), key=lambda kv: kv[1]):
        for sheet in ('A','B'):
            f = f'{SRC}/piacentine_Armanino_material_{suit}_{sheet}.jpg'
            im = Image.open(f).convert('RGB')
            g = np.asarray(im).astype(int).mean(2)
            boxes = card_boxes(g)
            for k, bx in enumerate(boxes):
                L, R, T, B = bx[:4]
                crop = im.crop((L, T, R+1, B+1))
                crop = Image.fromarray(whiten(np.asarray(crop)))
                # margine bianco uniforme: CardView ritaglia la carta con angoli arrotondati
                # (raggio 10% della larghezza), e senza questo margine la cornice disegnata,
                # che sta esattamente sul bordo, verrebbe tagliata negli angoli.
                pad = int(round(0.03*crop.width))
                framed = Image.new('RGB', (crop.width+2*pad, crop.height+2*pad), (255,255,255))
                framed.paste(crop, (pad, pad))
                crop = fit_ratio(framed)
                crop = crop.resize(target, Image.LANCZOS)
                # Il ridimensionamento LANCZOS lascia il fondo a 253-254 invece che a 255
                # (e' il normale sovraelongamento del filtro accanto ai bordi netti). Un
                # ultimo ritocco molto stretto lo riporta a bianco pieno senza toccare
                # nient'altro: parte da 249, mentre il grigio piu' chiaro del disegno,
                # le lame delle spade, sta sotto 235.
                z = np.asarray(crop).astype(np.float32)
                gz = z.mean(2, keepdims=True)
                tz = np.clip((gz - 249.0)/5.0, 0, 1)
                crop = Image.fromarray(np.clip(z*(1-tz) + 255.0*tz, 0, 255).astype(np.uint8))
                v = ORDER[sheet][k]
                name = f'card_{s}_{v}'
                crop.save(f'{outdir}/{name}.png')
                report.append((name, suit, v, R-L+1, B-T+1, round((B-T+1)/(R-L+1),3)))
    return report

if __name__ == '__main__':
    rep = main('/home/claude/cards_png')
    for r in rep: print(r)
