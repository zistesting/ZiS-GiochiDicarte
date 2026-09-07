# Sorgenti grafiche

`card_back.py` genera i dorsi delle carte. Un solo disegno, tre palette:

| SVG | Usato come | Palette |
|---|---|---|
| `card_back_zis.svg` | `card_back.png` | blu notte e argento, i colori di `colors.xml` |
| `card_back_grey.svg` | `trad_back.png` | grigi scuri |
| `card_back_grey_light.svg` | alternativa | la stessa incisione in negativo, ornato scuro su fondo chiaro |

Sono disegnati direttamente a 448x819, la proporzione delle carte, quindi non subiscono
ridimensionamenti. Per cambiare colori basta modificare il dizionario `PALETTES` in cima
allo script: le forme non si toccano.

Per rigenerarli:

```bash
pip install cairosvg pillow
python3 card_back.py                    # scrive i tre SVG
python3 - <<'PY'
import cairosvg
from PIL import Image
for nome, dest in (("zis", "card_back"), ("grey", "trad_back")):
    cairosvg.svg2png(url=f"card_back_{nome}.svg", write_to="/tmp/big.png",
                     output_width=896, output_height=1638)
    Image.open("/tmp/big.png").convert("RGB").resize((448, 819), Image.LANCZOS) \
         .quantize(256).save(f"../app/src/main/res/drawable-nodpi/{dest}.png", optimize=True)
PY
```

## Carte

`estrai_carta_singola.py` ritaglia una carta da un'immagine che ne contiene una sola: trova
la cornice, porta il fondo a bianco pieno, aggiunge il margine e salva a 448x819. Fa le
quaranta carte del mazzo ZiS. Il perche' delle singole scelte e' nel README principale,
sezione Mazzo.

```bash
pip install pillow numpy
python3 estrai_carta_singola.py       # legge i file d/c/s/b*.jpg, scrive i PNG
```

`fogli_seme.py` compone i quattro fogli di riepilogo, uno per seme: le dieci carte piu' il
dorso, sei sopra e cinque sotto. Non parte dalle carte gia' pronte a 448x819: rifa' il
ritaglio dalle immagini originali e si ferma alla misura nativa, 608x1112, cioe' quella che
il ritaglio produce senza mai ridimensionare. Il dorso e' l'unico senza limite, perche' e'
vettoriale e si rigenera dall'SVG alla misura del foglio.

```bash
pip install pillow numpy cairosvg
python3 fogli_seme.py
```

Il foglio esce 3960x2376 px con i dpi impostati a 300: in stampa sono 33,5 x 20,1 cm e la
singola carta misura 5,1 x 9,4 cm, cioe' quasi esattamente una carta vera.

Questa cartella sta fuori da `app/`, quindi non entra nella compilazione ne' nell'APK.
