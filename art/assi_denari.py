"""Porta i quattro assi di denari personalizzati nel formato del mazzo.

Gli assi di denari dei quattro mazzi italiani non vengono dai fogli di scansione come le
altre trentanove carte: sono disegni a parte, col marchio ZiS e la scritta, e stanno in
art/assi/ a 448x819, cioe' gia' nel formato dell'app.

Questo script esiste per due motivi, nessuno dei due estetico. Il primo e' che le carte si
ritagliano dai fogli con estrai_foglio_bergamasche.py e compagnia: il giorno che uno di quei
fogli si rigenera, l'asso tornerebbe quello di serie e nessuno se ne accorgerebbe. Il
secondo e' la conversione: i PNG hanno gli angoli trasparenti, e appiattirli sul nero
invece che sul bianco farebbe comparire quattro spicchi scuri agli angoli della carta.

    python3 assi_denari.py            controlla e dice cosa farebbe
    python3 assi_denari.py --scrivi   sovrascrive le quattro carte
"""
import os
import sys

from PIL import Image

QUI = os.path.dirname(os.path.abspath(__file__))
ASSI = os.path.join(QUI, "assi")
NODPI = os.path.join(QUI, "..", "app", "src", "main", "res", "drawable-nodpi")

CARD_W, CARD_H = 448, 819

# sorgente in art/assi/ -> carta nel mazzo
MAPPA = {
    "p_d1.png": "trad_0_1.webp",   # piacentine
    "b_d1.png": "berg_0_1.webp",   # bergamasche
    "n_d1.png": "nap_0_1.webp",    # napoletane
    "z_d1.png": "card_0_1.webp",   # mazzo ZiS
}


def converti(sorgente):
    """Il PNG nel formato del mazzo: 448x819, RGB, angoli trasparenti appiattiti sul bianco."""
    im = Image.open(sorgente).convert("RGBA")
    if im.size != (CARD_W, CARD_H):
        im = im.resize((CARD_W, CARD_H), Image.LANCZOS)
    fondo = Image.new("RGBA", im.size, (255, 255, 255, 255))
    fondo.alpha_composite(im)
    return fondo.convert("RGB")


def main():
    scrivi = "--scrivi" in sys.argv
    for src, dst in MAPPA.items():
        ps, pd = os.path.join(ASSI, src), os.path.join(NODPI, dst)
        if not os.path.exists(ps):
            raise SystemExit(f"manca {ps}")
        im = converti(ps)
        if scrivi:
            im.save(pd, "WEBP", quality=92, method=6)
            print(f"  {src} -> {dst}  {os.path.getsize(pd) // 1024} KB")
        else:
            print(f"  {src} -> {dst}  ({im.size[0]}x{im.size[1]}, non scritto)")
    if not scrivi:
        print("\nrilancia con --scrivi per sovrascrivere le carte")


if __name__ == "__main__":
    main()
