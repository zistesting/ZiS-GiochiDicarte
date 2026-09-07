"""Quattro fogli di riepilogo, uno per seme: le dieci carte piu' il dorso.

Le carte NON passano dai 448x819 del mazzo dell'app: si ripartono dalle immagini originali
a 576x1024 e ci si ferma alla misura nativa, cioe' quella che il ritaglio produce senza mai
ridimensionare (608x1112, cornice piu' margine piu' il riempimento che porta alla
proporzione). Ingrandire oltre non aggiungerebbe dettaglio, aggiungerebbe solo peso.

Il dorso e' l'unico che non ha un limite: e' vettoriale, quindi si rigenera dall'SVG
direttamente alla misura del foglio.

A 300 dpi una carta del foglio misura 5,1 x 9,4 cm, cioe' quasi esattamente una carta vera.
"""
from PIL import Image
import numpy as np, cairosvg, io, os
from extract import whiten, fit_ratio
from extract_singole import frame_box

CARD_W, CARD_H = 608, 1112          # misura nativa, nessun ingrandimento
GAP    = 40
MARGIN = 56
SEMI = [(0, 'denari', 'd'), (1, 'coppe', 'c'), (2, 'spade', 's'), (3, 'bastoni', 'b')]

def carta_nativa(src):
    im = Image.open(src).convert('RGB')
    g = np.asarray(im).astype(int).mean(2)
    L, R, T, B = frame_box(g)
    crop = Image.fromarray(whiten(np.asarray(im.crop((L, T, R+1, B+1)))))
    pad = int(round(0.03*crop.width))
    framed = Image.new('RGB', (crop.width+2*pad, crop.height+2*pad), (255,255,255))
    framed.paste(crop, (pad, pad))
    out = fit_ratio(framed)
    if out.size != (CARD_W, CARD_H):
        out = out.resize((CARD_W, CARD_H), Image.LANCZOS)
    # ultimo ritocco: il fondo torna a bianco pieno
    z = np.asarray(out).astype(np.float32); gz = z.mean(2, keepdims=True)
    t = np.clip((gz - 249.0)/5.0, 0, 1)
    return Image.fromarray(np.clip(z*(1-t) + 255.0*t, 0, 255).astype(np.uint8))

def dorso():
    png = cairosvg.svg2png(url='/home/claude/work/art/card_back_zis.svg',
                           output_width=CARD_W, output_height=CARD_H)
    return Image.open(io.BytesIO(png)).convert('RGB')

def foglio(lettera, dest):
    carte = [carta_nativa(f'/mnt/user-data/uploads/{lettera}{v}.jpg') for v in range(1, 11)]
    carte.append(dorso())                      # undicesima: il dorso

    cols, righe = 6, [carte[:6], carte[6:]]    # 6 sopra, 5 sotto centrate
    W = MARGIN*2 + cols*CARD_W + (cols-1)*GAP
    H = MARGIN*2 + 2*CARD_H + GAP
    sheet = Image.new('RGB', (W, H), (255, 255, 255))
    for r, riga in enumerate(righe):
        larghezza = len(riga)*CARD_W + (len(riga)-1)*GAP
        x0 = (W - larghezza)//2
        y = MARGIN + r*(CARD_H + GAP)
        for i, c in enumerate(riga):
            sheet.paste(c, (x0 + i*(CARD_W+GAP), y))
    sheet.save(dest, dpi=(300, 300), optimize=True)
    return sheet.size, os.path.getsize(dest)

if __name__ == '__main__':
    os.makedirs('/mnt/user-data/outputs', exist_ok=True)
    for s, nome, lettera in SEMI:
        dest = f'/mnt/user-data/outputs/foglio_{nome}.png'
        size, peso = foglio(lettera, dest)
        print(f'{nome:<8} {size[0]}x{size[1]} px  ({size[0]/300*2.54:.1f} x {size[1]/300*2.54:.1f} cm a 300 dpi)  {peso/1024/1024:.2f} MB')
