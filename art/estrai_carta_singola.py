"""Estrae una carta da un'immagine che ne contiene una sola.

Diverso da estrai_carte_zis.py, che lavora sui fogli da cinque: qui non c'e' niente da
separare, ma la cornice arriva quasi a filo dell'immagine e in fondo ad alcuni file c'e'
una riga scura isolata (un residuo di compressione) che, se presa per il bordo, sposta il
ritaglio di qualche pixel.
"""
from PIL import Image
import numpy as np, os, re, glob
from extract import whiten, fit_ratio

def clusters(idx, minlen=3):
    """Raggruppa indici consecutivi e tiene solo i gruppi spessi almeno minlen.

    E' questo a scartare la riga scura in fondo: il bordo della carta e' spesso 5 pixel,
    quel residuo e' spesso 1. Contarne i pixel scuri non basterebbe, perche' ne ha quasi
    quanti il bordo; a distinguerli e' lo spessore.
    """
    out=[]; s=p=None
    for x in idx:
        if s is None: s=p=x; continue
        if x-p>1: out.append((s,p)); s=x
        p=x
    if s is not None: out.append((s,p))
    return [c for c in out if c[1]-c[0]+1 >= minlen]

def frame_box(g):
    H,W = g.shape
    m = g < 238
    cols = clusters(np.where(m.sum(0) > 0.4*H)[0])
    rows = clusters(np.where(m.sum(1) > 0.4*W)[0])
    return cols[0][0], cols[-1][1], rows[0][0], rows[-1][1]

def convert(src, dst, target=(448,819)):
    im = Image.open(src).convert('RGB')
    g = np.asarray(im).astype(int).mean(2)
    L,R,T,B = frame_box(g)
    crop = im.crop((L, T, R+1, B+1))
    crop = Image.fromarray(whiten(np.asarray(crop)))
    pad = int(round(0.03*crop.width))     # stesso margine delle altre carte
    framed = Image.new('RGB', (crop.width+2*pad, crop.height+2*pad), (255,255,255))
    framed.paste(crop, (pad, pad))
    out = fit_ratio(framed).resize(target, Image.LANCZOS)
    z = np.asarray(out).astype(np.float32); gz = z.mean(2, keepdims=True)
    tz = np.clip((gz - 249.0)/5.0, 0, 1)
    Image.fromarray(np.clip(z*(1-tz) + 255.0*tz, 0, 255).astype(np.uint8)).save(dst)
    return (L,R,T,B), round((B-T+1)/(R-L+1),3)

if __name__ == '__main__':
    SUIT = {'b':3, 'c':1}          # b = bastoni, c = coppe
    os.makedirs('/home/claude/cards_png', exist_ok=True)
    for f in sorted(glob.glob('/mnt/user-data/uploads/[bc]*.jpg')):
        n = os.path.basename(f)[:-4]
        letter, value = n[0], int(n[1:])
        name = f'card_{SUIT[letter]}_{value}'
        box, r = convert(f, f'/home/claude/cards_png/{name}.png')
        print(f'{n:>4} -> {name:<11} cornice {box}  ratio {r}')
