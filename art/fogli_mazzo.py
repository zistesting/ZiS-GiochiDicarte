"""Un foglio per mazzo: tutte le carte piu' il dorso, alla risoluzione nativa.

Le carte si prendono dai file gia' pronti in drawable-nodpi, cioe' esattamente quelli che
finiscono nell'app: il foglio mostra il mazzo come lo vede il giocatore, non le scansioni di
partenza. Non si ripartono dai fogli originali come fa fogli_seme.py: li' aveva senso perche'
un seme solo lasciava spazio per la misura nativa, qui quaranta carte affiancate a
risoluzione di scansione sarebbero un'immagine da centoventi milioni di pixel per un
dettaglio che nessuno guarderebbe.

I quattro mazzi italiani stanno in quattro righe da dieci, una riga per seme, nell'ordine
denari, coppe, spade, bastoni e da Asso a Re. Il francese in quattro righe da tredici, un
seme per riga, da Asso a Re. Il dorso va da solo in una riga in fondo, centrato: infilarlo
in coda a un seme lo farebbe passare per una carta di quel seme.

    python3 fogli_mazzo.py                 tutti e cinque
    python3 fogli_mazzo.py zis piacentine  solo quelli indicati
"""
import os
import sys

from PIL import Image

QUI = os.path.dirname(os.path.abspath(__file__))
NODPI = os.path.join(QUI, "..", "app", "src", "main", "res", "drawable-nodpi")
DEST = "/mnt/user-data/outputs"

GAP, MARGIN = 32, 56

# nome -> (prefisso dei file, quanti valori per seme)
MAZZI = {
    "zis":         ("card", 10),
    "piacentine":  ("trad", 10),
    "bergamasche": ("berg", 10),
    "napoletane":  ("nap", 10),
    "francese":    ("fr", 13),
}


def carta(prefisso, nome):
    f = os.path.join(NODPI, f"{prefisso}_{nome}.webp")
    if not os.path.exists(f):
        raise FileNotFoundError(f)
    return Image.open(f).convert("RGB")


def foglio(nome_mazzo, dest):
    prefisso, valori = MAZZI[nome_mazzo]
    righe = [[carta(prefisso, f"{s}_{v}") for v in range(1, valori + 1)] for s in range(4)]
    righe.append([carta(prefisso, "back")])

    # la misura la dettano le carte, non una costante: le italiane sono 448x819, le
    # francesi 600x840, e il foglio si adatta invece di ridimensionare le carte
    cw, ch = righe[0][0].size
    for riga in righe:
        for c in riga:
            if c.size != (cw, ch):
                raise SystemExit(f"{nome_mazzo}: carte di misura diversa nello stesso mazzo")

    W = MARGIN * 2 + valori * cw + (valori - 1) * GAP
    H = MARGIN * 2 + len(righe) * ch + (len(righe) - 1) * GAP
    sheet = Image.new("RGB", (W, H), (255, 255, 255))
    for r, riga in enumerate(righe):
        larghezza = len(riga) * cw + (len(riga) - 1) * GAP
        x0 = (W - larghezza) // 2                       # il dorso, da solo, resta centrato
        y = MARGIN + r * (ch + GAP)
        for i, c in enumerate(riga):
            sheet.paste(c, (x0 + i * (cw + GAP), y))
    sheet.save(dest, dpi=(300, 300), optimize=True)
    return sheet.size, os.path.getsize(dest), len(righe[0]) * 4 + 1


if __name__ == "__main__":
    quali = sys.argv[1:] or list(MAZZI)
    for nome in quali:
        if nome not in MAZZI:
            raise SystemExit(f"mazzo sconosciuto: {nome}")
        d = os.path.join(DEST, f"mazzo_{nome}.png")
        (w, h), peso, n = foglio(nome, d)
        print(f"{nome:<12} {n:2} carte  {w}x{h} px  "
              f"({w/300*2.54:5.1f} x {h/300*2.54:5.1f} cm a 300 dpi)  {peso/1024/1024:5.1f} MB")
