"""Un foglio per mazzo: le quaranta carte piu' il dorso.

Le quaranta stanno in quattro righe da dieci, una riga per seme, nell'ordine denari, coppe,
spade, bastoni e da Asso a Re. Il dorso e' la quarantunesima e va da solo in una quinta riga,
centrato: dividerlo fra i semi lo confonderebbe con le carte.

Le carte si prendono dai file gia' pronti a 448x819, cioe' esattamente quelli che finiscono
nell'app. Non si ripartono dai fogli originali come per i fogli per seme: li' aveva senso
perche' un seme solo lasciava spazio per la misura nativa, qui quaranta carte affiancate
sarebbero un'immagine da centoventi milioni di pixel per un dettaglio che a schermo nessuno
vedrebbe mai. A 300 dpi la carta di questo foglio misura 3,8 x 6,9 cm.
"""
from PIL import Image
import os

CARD_W, CARD_H = 448, 819
GAP, MARGIN = 32, 56
SEMI = [0, 1, 2, 3]                       # denari, coppe, spade, bastoni

MAZZI = {
    'zis':         ('card', '/home/claude/newdeck'),
    'bergamasche': ('berg', '/home/claude/bergdeck'),
    'napoletane':  ('nap',  '/home/claude/napdeck'),
    'piacentine':  ('trad', '/home/claude/proj/p2/ZiS-GiochiDiCarte-part2/app/src/main/res/drawable-nodpi'),
}

# Il dorso ZiS non e' stato rigenerato con le quaranta carte: sta ancora solo nel pacchetto
# delle immagini, quindi si cerca in tutte e due le cartelle.
RISERVA = '/home/claude/proj/p2/ZiS-GiochiDiCarte-part2/app/src/main/res/drawable-nodpi'

def carta(cartella, prefisso, nome):
    for base in (cartella, RISERVA):
      for est in ('.webp', '.png'):
        f = f'{base}/{prefisso}_{nome}{est}'
        if os.path.exists(f):
            im = Image.open(f).convert('RGB')
            return im if im.size == (CARD_W, CARD_H) else im.resize((CARD_W, CARD_H), Image.LANCZOS)
    raise FileNotFoundError(f'{prefisso}_{nome}')

def foglio(nome_mazzo, dest):
    prefisso, cartella = MAZZI[nome_mazzo]
    righe = [[carta(cartella, prefisso, f'{s}_{v}') for v in range(1, 11)] for s in SEMI]
    righe.append([carta(cartella, prefisso, 'back')])          # il dorso, da solo

    W = MARGIN*2 + 10*CARD_W + 9*GAP
    H = MARGIN*2 + len(righe)*CARD_H + (len(righe)-1)*GAP
    sheet = Image.new('RGB', (W, H), (255, 255, 255))
    for r, riga in enumerate(righe):
        larghezza = len(riga)*CARD_W + (len(riga)-1)*GAP
        x0 = (W - larghezza)//2                                 # il dorso resta centrato
        y = MARGIN + r*(CARD_H + GAP)
        for i, c in enumerate(riga):
            sheet.paste(c, (x0 + i*(CARD_W+GAP), y))
    sheet.save(dest, dpi=(300, 300), optimize=True)
    return sheet.size, os.path.getsize(dest)

if __name__ == '__main__':
    for nome in ('zis', 'piacentine', 'bergamasche', 'napoletane'):
        dest = f'/mnt/user-data/outputs/mazzo_{nome}.png'
        (w, h), peso = foglio(nome, dest)
        print(f'{nome:<12} {w}x{h} px  ({w/300*2.54:.1f} x {h/300*2.54:.1f} cm a 300 dpi)  {peso/1024/1024:.1f} MB')
