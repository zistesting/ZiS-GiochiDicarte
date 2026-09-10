"""Dal logo in art/logo_app.png produce l'immagine dell'app e le icone del lanciatore.

Un file sorgente, sei destinazioni. Farlo a mano vuol dire ridimensionare sei volte e
ricordarsi le cinque densita': la volta che se ne sbaglia una, sui telefoni di quella
densita' resta l'icona vecchia e non lo si scopre.

    res/drawable-nodpi/app_logo.webp   500x500, usata in due posti:
                                       - il logo in cima alla schermata iniziale
                                       - il foreground dell'icona adattiva (Android 8+),
                                         rientrato di 18dp su 108 in ic_launcher.xml
    res/mipmap-{mdpi..xxxhdpi}/ic_launcher.png
                                       l'icona classica, quella che usano Android 7 e i
                                       lanciatori che non gestiscono le icone adattive.
                                       minSdk e' 24, cioe' Android 7: servono ancora.

L'ALFA SI CONSERVA, e non e' un dettaglio: il logo e' un quadrato con gli angoli
arrotondati e gli angoli sono trasparenti. Appiattendolo su un fondo comparirebbero quattro
spicchi, e nell'icona adattiva il fondo lo mette gia' il lanciatore.

    python3 icona_app.py            dice cosa farebbe
    python3 icona_app.py --scrivi   scrive i sei file
"""
import os
import sys

from PIL import Image

QUI = os.path.dirname(os.path.abspath(__file__))
SORGENTE = os.path.join(QUI, "logo_app.png")
RES = os.path.join(QUI, "..", "app", "src", "main", "res")

LOGO_LATO = 500
# le cinque densita' del lanciatore: 48dp moltiplicati per 1, 1.5, 2, 3 e 4
DENSITA = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    scrivi = "--scrivi" in sys.argv
    src = Image.open(SORGENTE).convert("RGBA")
    print(f"sorgente: {os.path.basename(SORGENTE)} {src.width}x{src.height}")

    lavori = [(os.path.join(RES, "drawable-nodpi", "app_logo.webp"), LOGO_LATO, "WEBP")]
    for d, lato in DENSITA.items():
        lavori.append((os.path.join(RES, f"mipmap-{d}", "ic_launcher.png"), lato, "PNG"))

    for dest, lato, fmt in lavori:
        im = src if src.size == (lato, lato) else src.resize((lato, lato), Image.LANCZOS)
        rel = os.path.relpath(dest, RES)
        if scrivi:
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            if fmt == "WEBP":
                im.save(dest, "WEBP", quality=92, method=6, exact=True)
            else:
                im.save(dest, "PNG", optimize=True)
            print(f"  {rel:42} {lato}x{lato}  {os.path.getsize(dest) // 1024} KB")
        else:
            print(f"  {rel:42} {lato}x{lato}  (non scritto)")

    if not scrivi:
        print("\nrilancia con --scrivi per sovrascrivere")


if __name__ == "__main__":
    main()
