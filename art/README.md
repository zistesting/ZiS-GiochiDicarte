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

Questa cartella sta fuori da `app/`, quindi non entra nella compilazione ne' nell'APK.
