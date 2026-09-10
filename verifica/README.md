# Verifica del motore del Klondike

Il motore sta in `app/src/main/java/com/zis/scopa/KlondikeGame.kt` ed e' scritto in Kotlin,
che qui non si puo' eseguire. Queste tre cose riscrivono le stesse regole in Python e le
mettono alla prova. Non entrano nell'app: stanno fuori da `app/`.

```bash
pip install --break-system-packages numpy   # non serve nemmeno quello, e' solo Python
python3 invarianti_klondike.py 500
python3 solutore_klondike.py 60 200000
```

## `invarianti_klondike.py` — il controllo che conta

Gioca mille partite a caso e dopo **ogni singola mossa** verifica cinque cose che devono
valere sempre:

1. le 52 carte ci sono tutte, una volta sola (nessuna sparita fra tallone, scarti,
   fondazioni, coperte e scoperte);
2. le carte scoperte di ogni colonna sono una sequenza valida a scendere e a colori
   alternati — il motore ci **conta** per staccare i gruppi senza ricontrollarli;
3. le fondazioni contengono Asso, 2, 3... del proprio seme in ordine;
4. nessuna colonna ha carte coperte sotto zero scoperte;
5. rigirando il tallone le carte tornano nello stesso ordine di prima.

Ultimo esito: **400.000 mosse controllate una per una, nessuna violazione.**

## `solutore_klondike.py` — il controllo sulle regole

Gli invarianti dicono che il codice e' coerente con se stesso, non che le regole siano
quelle del Klondike vero. Per quello si misura quante smazzate sono vincibili e si confronta
con la letteratura: circa **82%** pescando a tre, circa nove punti in piu' pescando a una.

Il solutore ha un tetto di nodi, quindi da' un **limite inferiore**: le smazzate che non
risolve possono essere impossibili oppure solo difficili per lui.

| budget di nodi | pesca da 1, stessi 25 semi |
|---|---|
| 50.000 | 52% |
| 200.000 | 68% |
| 800.000 | 76% |

Il numero sale col budget e non si ferma: e' il **solutore** a essere il limite, non le
regole. E il divario fra pesca da uno e da tre (68% contro 55% a parita' di budget) e' dello
stesso ordine dei nove punti misurati in letteratura.

Se una regola fosse sbagliata — la colonna vuota che accetta qualsiasi carta, il tallone che
si rigira nell'ordine sbagliato, il gruppo che si stacca dal punto sbagliato — la percentuale
si sposterebbe in modo evidente e non salirebbe cosi' col budget.
