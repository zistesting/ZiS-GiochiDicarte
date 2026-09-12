"""Dai due logo in art/ produce l'immagine dell'app e le icone del lanciatore.

DUE SORGENTI, SETTE DESTINAZIONI, e i due disegni non sono intercambiabili:

    logo_app.png     il marchio quadrato stondato, con la cornice lucida e l'ombra.
                     Angoli trasparenti.
    icona_app.png    lo stesso stemma dentro un cerchio, senza cornice e senza ombra.
                     Fuori dal cerchio e' trasparente.

    res/drawable-nodpi/app_logo.webp        500x500   da logo_app
                     il logo grande in cima alla schermata iniziale, dove la cornice
                     quadrata ci sta bene.

    res/drawable-nodpi/ic_launcher_fg.webp  500x500   da icona_app
                     il primo piano dell'icona adattiva (Android 8+), rientrato di 18dp
                     su 108 in mipmap-anydpi-v26/ic_launcher.xml.

    res/mipmap-{mdpi..xxxhdpi}/ic_launcher.png   48..192   da icona_app
                     l'icona classica, quella che usano Android 7 e i lanciatori che non
                     gestiscono le icone adattive. minSdk e' 24, cioe' Android 7: servono
                     ancora.

PERCHE' IL LANCIATORE PRENDE IL CERCHIO E NON IL QUADRATO. Le maschere del lanciatore -
tonda, squircle, quadrata stondata - le decide il telefono, non noi. Il marchio quadrato
dentro una maschera tonda si vede come un quadrato dentro un cerchio, con quattro spicchi
tagliati e la cornice lucida rosicchiata; il cerchio invece riempie qualunque maschera senza
giunture. E' la stessa ragione per cui il fondo dell'icona adattiva e' navy_logo, cioe' il
blu del cerchio, e non il navy scuro del tema.

Questa e' anche la riga che la versione precedente di questo script sbagliava: prendeva
logo_app per tutte e sei le destinazioni, quindi rilanciandolo si riportavano le cinque
icone classiche al quadrato. Il disegno tondo esisteva gia' nell'app, ma non passava piu' da
qui - e uno strumento che, rilanciato, disfa il lavoro e' peggio di nessuno strumento.

IL MARGINE DELL'ANELLO, che e' il numero da non perdere di vista se il disegno tondo si
ridisegna: l'anello chiaro sta a 27 pixel dal bordo su 250 di raggio, cioe' al 10,8%. E' il
margine che permette i 18dp di rientro in ic_launcher.xml - 18 su 108 mettono il disegno
esattamente a filo di maschera - senza che l'antialiasing della maschera si mangi l'anello.
Un disegno con l'anello piu' vicino al bordo vuole un rientro piu' grande, e il posto dove
cambiarlo e' quell'XML.

L'ALFA SI CONSERVA, e non e' un dettaglio: i due disegni hanno il fuori trasparente.
Appiattendoli su un fondo comparirebbero quattro spicchi negli angoli del logo e un quadrato
attorno al cerchio dell'icona, e nell'icona adattiva il fondo lo mette gia' il lanciatore.

SENZA PERDITA, e per l'icona si risparmia anche: il WebP senza perdita del cerchio sta in
21 KB contro i 26 del q92 che si usava prima, perche' il disegno e' fatto di campi di colore
piatti. Il logo, che ha la cornice lucida in sfumatura, senza perdita costa 39 KB contro 23,
cioe' 16 KB in piu' su quasi dieci megabyte di immagini: si pagano volentieri, perche' cosi'
il file nell'app e il file sorgente sono identici bit per bit e la prossima volta che si
confrontano non c'e' il rumore del codificatore da interpretare.

    python3 icona_app.py            dice cosa farebbe
    python3 icona_app.py --scrivi   scrive i sette file
"""
import os
import sys

from PIL import Image

QUI = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(QUI, "..", "app", "src", "main", "res")

LOGO = os.path.join(QUI, "logo_app.png")
ICONA = os.path.join(QUI, "icona_app.png")

LATO = 500
# le cinque densita' del lanciatore: 48dp moltiplicati per 1, 1.5, 2, 3 e 4
DENSITA = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def lavori():
    """(sorgente, destinazione, lato, formato) per ciascuno dei sette file."""
    yield LOGO, os.path.join(RES, "drawable-nodpi", "app_logo.webp"), LATO, "WEBP"
    yield ICONA, os.path.join(RES, "drawable-nodpi", "ic_launcher_fg.webp"), LATO, "WEBP"
    for d, lato in DENSITA.items():
        yield ICONA, os.path.join(RES, f"mipmap-{d}", "ic_launcher.png"), lato, "PNG"


def main():
    scrivi = "--scrivi" in sys.argv
    sorgenti = {}
    for p in (LOGO, ICONA):
        im = Image.open(p).convert("RGBA")
        if im.size != (LATO, LATO):
            sys.exit(f"{os.path.basename(p)} e' {im.width}x{im.height}: serve {LATO}x{LATO}")
        sorgenti[p] = im
        print(f"sorgente: {os.path.basename(p)} {im.width}x{im.height}")

    for src, dest, lato, fmt in lavori():
        im = sorgenti[src]
        if im.size != (lato, lato):
            im = im.resize((lato, lato), Image.LANCZOS)
        rel = os.path.relpath(dest, RES)
        da = os.path.basename(src)
        if not scrivi:
            print(f"  {rel:42} {lato:>4}  <- {da}  (non scritto)")
            continue
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        if fmt == "WEBP":
            im.save(dest, "WEBP", lossless=True, method=6, exact=True)
        else:
            im.save(dest, "PNG", optimize=True)
        kb = os.path.getsize(dest) / 1024
        print(f"  {rel:42} {lato:>4}  <- {da}  {kb:5.1f} KB")

    if not scrivi:
        print("\nrilancia con --scrivi per sovrascrivere")


if __name__ == "__main__":
    main()
