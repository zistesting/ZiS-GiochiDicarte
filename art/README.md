# Sorgenti grafiche

`card_back.py` genera `card_back.svg`, il dorso delle carte. E' disegnato in vettoriale
direttamente a 448x819, la proporzione delle carte, quindi non subisce ridimensionamenti.
Colori presi da `app/src/main/res/values/colors.xml`.

Per rigenerarlo dopo una modifica:

```bash
pip install cairosvg pillow
python3 card_back.py
python3 - <<'PY'
import cairosvg
from PIL import Image
cairosvg.svg2png(url="card_back.svg", write_to="/tmp/big.png",
                 output_width=896, output_height=1638)
Image.open("/tmp/big.png").convert("RGB").resize((448, 819), Image.LANCZOS) \
     .quantize(256).save("../app/src/main/res/drawable-nodpi/card_back.png", optimize=True)
PY
```

Questa cartella sta fuori da `app/`, quindi non entra nella compilazione ne' nell'APK.
