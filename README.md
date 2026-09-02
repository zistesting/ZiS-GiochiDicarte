# ZiS – Giochi di Carte

Scopa e Briscola contro il Banco. Progetto Android nativo (Kotlin + View Binding).

- `minSdk 24` · `targetSdk 36` · `compileSdk 36`
- AGP 8.13.2 · Gradle 8.13 · Kotlin 2.2.20 · JDK 17

---

## Compilare

**Su GitHub:** il workflow `.github/workflows/build-apk.yml` parte a ogni push e produce
sempre due artefatti, scaricabili da **Actions → ultimo run → Artifacts**:

| Artefatto | Cos'e' | A cosa serve |
|---|---|---|
| `ZiS-GiochiDiCarte-APK` | `app-release.apk` | si installa a mano sul telefono, per provare |
| `ZiS-GiochiDiCarte-AAB` | `app-release.aab` | si carica sul Play Console per pubblicare |

Entrambi escono dalla variante **release**, cioe' esattamente il codice che finisce agli
utenti. Senza i secret della firma vengono firmati con la chiave di debug: l'APK si installa
lo stesso, l'AAB invece il Play Console lo rifiuta.

**In locale:**

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab
```

---

## Firma

Il keystore **non sta nel repository** e le password non sono scritte in `build.gradle`.
Senza configurazione il progetto compila lo stesso, firmato con la chiave di debug standard
di Android. Un pacchetto firmato in quel modo va bene per provare l'app sul telefono, ma
**non è caricabile sul Play Store**.

### In locale

Metti `scopa.keystore` in `app/` e crea `keystore.properties` nella cartella principale
(è già nel `.gitignore`):

```properties
storeFile=scopa.keystore
storePassword=LA_TUA_PASSWORD
keyAlias=scopa
keyPassword=LA_TUA_PASSWORD
```

### Su GitHub Actions

In **Settings → Secrets and variables → Actions** crea:

| Secret | Valore |
|---|---|
| `KEYSTORE_BASE64` | output di `base64 -w0 app/scopa.keystore` |
| `KEYSTORE_PASSWORD` | password del keystore |
| `KEY_ALIAS` | `scopa` |
| `KEY_PASSWORD` | password della chiave |

> **Importante.** La vecchia chiave va considerata compromessa: password e file erano nel
> repository. Se il repo è pubblico, chiunque poteva firmare un APK che Android riconosce
> come il tuo. Il file va tolto anche dalla cronologia di git:
>
> ```bash
> git rm --cached app/scopa.keystore
> git commit -m "Rimuove il keystore dal repository"
> # per ripulire anche la history servono git-filter-repo o BFG Repo-Cleaner
> ```
>
> Per il Play Store la soluzione definitiva è **Play App Signing**: carichi una chiave di
> upload, Google conserva quella di firma vera e una chiave compromessa si può sostituire
> senza costringere nessuno a disinstallare l'app.

---

## "Package conflict" quando installi un aggiornamento

Android accetta un aggiornamento solo se è firmato con la **stessa chiave** della versione
già installata. Senza i secret configurati, ogni build su GitHub Actions genera una chiave di
debug nuova, quindi la firma cambia a ogni run e l'installazione viene rifiutata.

La soluzione è configurare i quattro secret (vedi sopra). Per convertire il keystore in base64:

```bash
base64 -w0 app/scopa.keystore               # Linux
base64 -i app/scopa.keystore | tr -d '\n'   # macOS
```

Su Windows, in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\scopa.keystore")) | Set-Clipboard
```

---

## Pubblicare sul Play Store

Il progetto è impostato per la scadenza del **31 agosto 2026**: da quella data Google Play
accetta nuove app e aggiornamenti solo con `targetSdk 36`. Chi arriva in ritardo può chiedere
una proroga dal Play Console fino al **1° novembre 2026**.

Cosa è già stato fatto:

1. `compileSdk 36`, `targetSdk 36`, AGP 8.13.2, Gradle 8.13, Kotlin 2.2.20.
   L'API massima supportata da AGP 8.9 è la 35: con `compileSdk 36` la build funzionava
   lo stesso ma fuori configurazione supportata, e a ogni run usciva l'avviso «We recommend
   using a newer Android Gradle plugin». AGP 8.13 arriva all'API 36.1 e pretende Gradle 8.13.
2. **Edge to edge.** Da API 36 non si può più rinunciare a disegnare sotto la barra di stato e
   sotto quella di navigazione. `SystemBars.kt` applica i margini giusti al contenuto con
   `setOnApplyWindowInsetsListener`, tenendo lo sfondo fino ai bordi. Sostituisce
   `android:fitsSystemWindows="true"`, che copriva solo i casi semplici.
3. **Schermi grandi.** Da API 36 i dispositivi con lato corto da 600dp in su ignorano
   `screenOrientation="portrait"`: l'app può ritrovarsi in orizzontale o in una finestra
   ridimensionabile. Le carte adesso si misurano su larghezza **e** altezza (`CardSize.kt`),
   così in orizzontale non diventano tanto alte da non starci; le due schermate di gioco
   dichiarano `configChanges`, quindi ruotando il tablet la partita in corso non si perde.
4. Il workflow produce **APK e AAB** a ogni push, entrambi in variante release.

Cosa resta da fare prima della pubblicazione:

- Provare su un tablet vero, in verticale e in orizzontale.
- Compilare la scheda del Play Console: privacy policy, questionario sui contenuti, fascia
  d'età, screenshot. L'app non raccoglie dati e non chiede permessi, quindi la dichiarazione
  "Sicurezza dei dati" è la più semplice possibile.
- Valutare `minifyEnabled true` per ridurre il pacchetto. Le carte sono caricate per nome con
  `getIdentifier`, quindi `res/raw/keep.xml` le protegge già dallo shrinker, ma va provato su
  un dispositivo prima di pubblicare.

---

## Note tecniche

**Mazzo.** Due mazzi da 40 carte piu' dorso in `res/drawable-nodpi/`, tutti a **448x819**,
in **WebP**. Le carte ZiS (`card_*`) vengono da originali a 560x1024, errore massimo
2,7/255.

**Mazzo tradizionale.** Le carte `trad_*` vengono da scansioni di quattro fogli d'epoca,
uno per seme, di un mazzo piacentino stampato da *Succ. Armanino - Roma* (il nome compare
sull'asso di denari). Hanno sostituito le scansioni precedenti, che erano coperte da
copyright. Ogni foglio contiene dieci carte su due file: `5 4 3 2 A` sopra, `Re Cavallo
Fante 7 6` sotto.

L'estrazione e' automatica e ripetibile. Il riquadro delle dieci carte si isola sfruttando il
fatto che la carta e' color crema mentre lo sfondo della scansione e' grigio neutro (`R - B`
sopra 10). Dentro ogni cella si cerca poi la cornice nera facendo scorrere un rettangolo di
misura fissa, 439x934 px, e tenendo la posizione in cui il suo perimetro raccoglie piu' pixel
neri: prendere semplicemente la riga piu' scura sbagliava sulle carte con disegni lunghi, per
esempio il 7 di bastoni.

Il rettangolo scorrevole pero' puo' agganciarsi alla giuntura fra due carte invece che alla
cornice, e su due carte su quaranta era successo (il Re di denari, fuori di 82 px, e il 6 di
spade, di 34). L'ultimo passaggio rimette quindi tutto in griglia: le dieci carte sono
incollate a contatto, quindi gli angoli in alto a sinistra hanno passo costante e una leggera
deriva, perche' il foglio e' scansionato appena storto. Il passo si stima con la mediana degli
otto scarti fra colonne vicine prese da **entrambe** le righe: e' la stessa larghezza fisica,
e stimarla su otto valori invece che su quattro la rende insensibile a una carta sbagliata.
Con una retta stimata per riga, invece, l'inclinazione assorbiva lo scarto del 6 di spade e
non lo segnalava. Chi resta fuori di piu' di 20 px viene riportato dove dice la griglia. Ogni carta viene poi ritagliata sulla cornice, filtrata con un
mediano 3x3 per togliere la retinatura di quadricromia, riportata alla proporzione delle carte
aggiungendo carta del colore giusto (non stirando) e ridotta a 448x819.

**Fondo bianco.** La carta d'epoca e' color crema e la scansione ha una dominante gialla. Il
fondo viene portato al bianco in due passaggi. Prima un bilanciamento: ogni canale e' diviso
per il valore che ha sulla carta, quindi la carta finisce esattamente a 255. Sui toni scuri
l'effetto e' quasi nullo (un nero a 20 sale a 22), percio' il tratto non cambia. Poi
l'appiattimento: quello che a quel punto e' gia' quasi bianco e quasi grigio viene portato a
bianco pieno, ma con una sfumatura invece che con una soglia secca, altrimenti i contorni
morbidi del disegno prenderebbero un alone. Verificato su tutte e quaranta: le strisce di
margine sono a 255 pieno.

Errore del WebP q90 su queste: 2,7/255 in media, piu' alto delle ZiS perche' la grana della
scansione si comprime peggio del disegno a tinte piatte.

Il dorso `trad_back` non viene dai fogli: resta quello disegnato in vettoriale in `art/`.

> Se pubblichi, conviene tenere nel repository anche la provenienza delle scansioni (da dove
> vengono e con che licenza). Non serve al codice, serve ad avere la risposta pronta se un
> domani qualcuno la chiede.

**I dorsi** sono disegnati in vettoriale: nascono gia' a 448x819, quindi non subiscono nessun
ridimensionamento. Sono gli unici due file salvati in WebP **senza perdita**: su un disegno a
tinte piatte il lossless pesa meno del lossy (74 KB contro 143 KB per `card_back`). Un solo disegno con tre palette: blu/argento per il mazzo ZiS, grigi scuri per quello
tradizionale, piu' una versione chiara di scorta. Il sorgente sta in `art/`, fuori da `app/`, e per
cambiare colori basta modificare il dizionario `PALETTES` in cima allo script.

La cartella `nodpi` serve perché Android
non deve riscalare i file in base alla densità dello schermo: alla riduzione ci pensa
`CardView`, che decodifica ogni immagine **alla larghezza a cui la carta viene davvero
disegnata**. Su un telefono xxhdpi una carta occupa circa 230 px e la bitmap viene creata a
256×468 (~470 KB) anche se il file sorgente è molto più grande. È questo che permette di usare
un unico set di immagini ad alta risoluzione sia sui telefoni sia sui tablet.
**Non spostare i file in `drawable/`**: lì Android li ingrandirebbe alla densità dello schermo,
sprecando memoria per niente. Vale anche per i loghi, che infatti stanno in `drawable-nodpi/`.

**Misura richiesta dalle immagini.** La carta più grande che l'app disegna è 150dp di larghezza:
da 230 px circa su un telefono comune fino a 450–500 px su un tablet ad alta densità. Un set
sorgente da **448×819** copre ogni caso senza ingrandimenti; oltre si guadagna solo peso
dell'APK.

**Formato.** Le carte sono in **WebP a qualità 90**, non più in PNG a 256 colori: 10,7 MB di
immagini sono diventati 5,1 MB, cioè metà del pacchetto. L'errore introdotto è 1,7/255 in media;
il picco sta su qualche centinaio di pixel per carta (lo 0,1%) lungo i contorni neri più netti
delle scansioni tradizionali, e non migliora alzando la qualità — a q96 il file cresce del 50%
e il picco scende da 92 a 84. A dimensione reale non si vede niente: il WebP anzi attenua il
dithering che la palette a 256 colori lasciava sulle sfumature. I quattro loghi hanno la
trasparenza e stanno a q95; i due dorsi sono senza perdita.

WebP è supportato da Android 4.0 in su, quindi non tocca il `minSdk 24`. I nomi delle risorse
non cambiano (`card_0_1`, non `card_0_1.png`), quindi non c'è una riga di codice da modificare:
cambia solo l'estensione del file.

**Due mazzi.** Le carte si caricano per nome, `<prefisso>_seme_valore`. Il prefisso lo decide
l'impostazione **Mazzo**: `card` per le illustrazioni ZiS, `trad` per le figure tradizionali.
Per aggiungere il secondo mazzo bastano 41 file in `res/drawable-nodpi/` chiamati
`trad_0_1.png` ... `trad_3_10.png` piu' `trad_back.png` (semi: 0 denari, 1 coppe, 2 spade,
3 bastoni). Nessuna riga di codice da toccare.

Finche' quei file non ci sono, ogni immagine mancante ripiega su quella ZiS corrispondente:
si puo' quindi pubblicare l'interruttore prima di avere il mazzo, e caricare le carte anche
poche per volta senza mai lasciare buchi bianchi sul tavolo. Le impostazioni avvisano quando
il mazzo tradizionale e' selezionato ma non installato.

Cambiando mazzo la cache delle bitmap si svuota, altrimenti resterebbero a schermo le carte
del mazzo precedente.

**Cache bitmap.** `CardView` usa una `LruCache` limitata a 1/8 della heap. Se la larghezza
richiesta cambia (rotazione, finestra ridimensionata) la cache si svuota e le carte vengono
ridecodificate alla nuova misura. `ZisApp` la svuota quando il sistema segnala poca memoria.

**versionCode.** È `1000 + numero della build di GitHub Actions`. Il numero di Actions da solo
è fragile: se il repository viene ricreato il contatore riparte da 1 e il Play Console rifiuta
l'upload, perché pretende un `versionCode` sempre crescente. La base davanti lascia margine; se
dovesse servire basta alzare `VERSION_CODE_BASE` in `app/build.gradle`.

**Mescolata.** Una sola funzione, `shuffledDeck()` in `Card.kt`, usata da Scopa e Briscola.
Usa `SecureRandom` e non `Random`: `java.util.Random` ha uno stato interno di 48 bit, cioè
281.000 miliardi di partenze possibili, mentre le disposizioni di 40 carte sono 40 fattoriale,
un numero con 48 cifre. Con `Random` la stragrande maggioranza delle mescolate non uscirebbe
mai. `SecureRandom` pesca entropia dal sistema operativo a ogni chiamata, senza stato limitato,
quindi ogni disposizione è realmente raggiungibile. `Collections.shuffle` è un Fisher-Yates,
cioè uniforme: nessuna posizione è favorita.

**Tempi di gioco.** Tutti in `Timing.kt`, uno solo per entrambi i giochi. Con il **gioco
automatico** attivo valgono zero: niente animazioni, niente attese, niente cartelli SCOPA, e
la partita scorre alla massima velocità. Le mosse passano comunque dall'`Handler` una alla
volta, quindi non si annidano sullo stack e l'app resta reattiva.

**Riepilogo di fine mano (Scopa).** E' una `TableLayout` (`res/layout/dialog_score.xml`)
passata al dialogo con `setView`, non un testo. Prima le colonne erano tenute in riga con gli
spazi e un carattere a larghezza fissa: 27 caratteri che sui telefoni stretti andavano a capo
e mandavano tutto fuori squadra. Con la tabella le colonne le tiene il layout e si usa il
carattere normale dell'app.

## Tresette

Terzo gioco: **tresette in due con il tallone**. Dieci carte a testa, venti nel tallone. Chi
risponde **deve** rispondere al seme, e puo' calare un altro seme solo se quel seme non ce
l'ha, ma in quel caso non prende. Prende chi ha calato la carta piu' alta del seme di
apertura. Poi si pesca, prima chi ha preso, e **la carta pescata si mostra all'avversario**.
Finito il tallone si giocano le ultime dieci prese senza pescare. Incontro a 21 o 31 punti,
scelta nelle impostazioni.

Ordine di presa: 3, 2, Asso, Re, Cavallo, Fante, 7, 6, 5, 4.

**I punti si contano in terzi**, non con i decimali: Asso 3 terzi, il 2, il 3, Fante, Cavallo
e Re un terzo, dal 4 al 7 niente, ultima presa 3 terzi. In tutto 35. A fine mano ciascuno
scarta il proprio resto. Da qui viene un fatto che semplifica il riepilogo: i due resti
sommano **sempre** a 2 terzi, perche' le carte da un terzo sono venti e i due conteggi sono
complementari modulo 3. Quindi **una mano vale sempre esattamente 11 punti**, mai 10, e il
pareggio a fine mano non esiste. Verificato su 30.000 mani: 11 punti tutte le volte.

**La mano da dieci carte** e' il problema vero su un telefono, e la soluzione e' arrivata al
secondo tentativo. Il primo era una fila sola di dieci carte sovrapposte: a misura piena
restava scoperto il 39% di ciascuna, rimpicciolendole il 57%, e in quella striscia verticale
un 3 e un 7 di denari si distinguevano a fatica.

La disposizione buona e' **due file da cinque**. Le carte non si sovrappongono affatto, quindi
si vedono intere, e per giunta vengono piu' grandi di prima: su un telefono tipico si passa da
57 a 66 dp. La misura (`CardSize.handWidth`) e' il piu' stretto fra tre limiti: cinque carte
affiancate devono stare nella riga tolti i bordi e gli spazi; due file non devono mangiarsi
piu' del 38% dell'altezza, altrimenti al tavolo non resta posto per le carte della presa; e
una carta in mano non ha senso che sia piu' grande di una in tavola. Verificato dal telefono
piccolo (360x560dp) al tablet, anche con le carte del Banco scoperte: il tavolo resta sempre
piu' capiente di quanto gli serve. Le file si tengono pari (dieci carte fanno 5 e 5, nove
fanno 5 e 4) e da cinque in giu' si passa a una fila sola, per non lasciare una riga vuota.

Il **ventaglio del Banco** resta invece una fila sola sovrapposta: sono dorsi tutti uguali,
sovrapporli non costa niente e lo spazio verticale risparmiato va al tavolo.

Restano gli altri due aiuti alla lettura: la mano si tiene **ordinata per seme** e, dentro il
seme, dalla carta piu' forte; e le carte non giocabili sono spente al 35% e non rispondono al
tocco, cosi' l'obbligo di rispondere al seme diventa anche una guida per trovare la carta.

**Le due misure insieme hanno costretto a rifare la cache di `CardView`.** Teneva una sola
larghezza in uno stato a parte e si svuotava tutta appena ne arrivava un'altra: andava bene
finche' a schermo c'era una misura sola, ma con le carte piccole in mano e quelle grandi in
tavola le due si sarebbero buttate a vicenda a ogni disegno, ridecodificando l'intero mazzo a
ogni fotogramma. Ora la larghezza fa parte della chiave.

**Finale a carte note.** Come negli altri due giochi, a tallone finito le carte mai viste
sono esattamente la mano dell'avversario (verificato 20.000 volte), quindi si calcola invece
di stimare. Qui pero' l'albero e' molto piu' grande che a Briscola, percio' la ricerca parte
solo da sette carte in giu'. La soglia e' misurata, non scelta a occhio:

| carte in mano | mediana | massimo |
|---|---|---|
| 6 | 0,45 ms | 12 ms |
| 7 | 3,1 ms | 52 ms |
| 8 | 18 ms | 125 ms |

A otto carte diventa troppo. A sette il costo cade comunque nella pausa di riflessione del
Banco, quando a schermo non si muove niente, quindi non produce scatti. Quanto rende, con i
due Banchi che si affrontano su 6.000 mani:

| | vince | saldo |
|---|---|---|
| euristica contro gioco casuale | 83,0% | +3,84 punti/mano |
| ricerca a 6 carte contro sola euristica | 63,4% | +0,99 |
| ricerca a 7 carte contro sola euristica | 65,8% | +1,20 |
| ricerca a 7 contro ricerca a 6 | 51,8% | +0,16 |

Sulla taratura dell'euristica va detta una cosa: ho provato cinque varianti dei parametri e
davano tutte lo stesso risultato. Il controllo base-contro-base fa 49%, cioe' erano
equivalenti, perche' due dei parametri non cambiavano mai la carta scelta. Restano quelli di
partenza, che risultano gia' vicini a un ottimo locale.

**Conteggio delle carte.** Il Banco tiene conto di quello che e' uscito. `unseenBy(p)` e'
la lista delle carte che il giocatore `p` non ha ancora visto: non in mano sua, non in tavola,
non nei mucchi delle prese. Finche' il mazzo non e' finito sono le carte del mazzo piu' quelle
dell'avversario; **quando il mazzo e' vuoto sono esattamente la mano dell'avversario**, e da
li' l'ultima mano si puo' giocare a carte note.

In **Briscola**, finito il mazzo restano al massimo tre prese: l'albero ha 36 foglie e si
esplora tutto (`solve` in `BriscolaGame.kt`), quindi il finale e' giocato alla perfezione. E'
li' che si decide la partita, perche' i carichi rimasti valgono da soli decine di punti.

Nel **Tresette** la carta pescata si mostra, quindi `seenInHandOf` tiene anche traccia di
quali carte il Banco ha visto entrare nella mano avversaria: informazione utile prima che il
tallone finisca, mentre dopo la mano avversaria si ricava comunque per differenza.

In **Scopa**, a mazzo finito restano al massimo sei giocate e si cerca la migliore con un
minimax e taglio alfa-beta (`solve` in `ScopaGame.kt`). Cosi' il Banco sa se sta regalando una
scopa, sa che conviene fare l'ultima presa (chi la fa si porta via il tavolo) e chiude bene
primiera e denari. L'alfa-beta non e' un lusso: senza, l'albero e' cento volte piu' grande.
La ricerca costa 0,04 ms in media, quindi non si sente. C'e' comunque un tetto di 60.000 nodi
che fa ricadere sull'euristica, ma su ventimila ricerche il massimo osservato e' stato 221.

Fuori dall'ultima mano il conteggio serve in Scopa a pesare il rischio di scopa: lasciare il
tavolo a un totale fra 1 e 10 e' pericoloso solo se una carta di quel valore puo' essere
ancora in mano all'avversario. Se sono gia' uscite tutte e quattro il rischio e' zero, e prima
il Banco lo evitava lo stesso.

Quanto rende, misurato facendo giocare il Banco nuovo contro quello vecchio, 20.000 partite
per gioco, con chi comincia che si alterna:

| | vince il nuovo | vince il vecchio | saldo medio |
|---|---|---|---|
| Scopa | 58,9% | 41,1% | +0,56 punti a mano |
| Briscola | 55,4% | 44,6% | +3,5 punti su 120 |

**Apertura del Banco (Briscola).** Il Banco non ha piu' la regola secca "mai aprire di
briscola": con una mano tipo briscola 4 + briscola cavallo + un 3 lo obbligava a buttare il
carico, dieci punti regalati per non calare una briscola che non vale niente. Adesso confronta
il costo di ogni carta, cioe' i suoi punti piu' quanto varrebbe tenersela, e la briscola bassa
entra nel conto. Nelle mani normali il liscio resta comunque la scelta piu' economica.

**Da quale carta parte la giocata del Banco.** Le carte del Banco sono coperte, ma le viste
sono create nell'ordine della mano, quindi la posizione di partenza dell'animazione si ricava
dall'indice della carta scelta. Prima partiva sempre quella all'estrema sinistra: se il Banco
aveva scelto un'altra carta, a sinistra ne spariva una e in mezzo al tavolo ne compariva
un'altra. In Briscola il salto delle carte appena pescate va ripetuto identico a `render()`,
altrimenti l'indice slitta di uno.

**Callback differiti.** Tutti i passaggi di turno sono `postDelayed` su un unico `Handler`,
ripuliti in `onDestroy()`. Senza questo, uscire dall'app mentre gioca il Banco faceva partire
il dialogo di fine mano su un'activity distrutta (`BadTokenException`).

**Gioco sospeso in background.** `onStop()` svuota l'`Handler` e l'overlay: finché la schermata
non è visibile la partita non va avanti da sola. Prima il Banco continuava a giocare mentre
l'utente era altrove e il riepilogo di fine mano si apriva su una schermata che nessuno stava
guardando; con il gioco automatico attivo l'app macinava partite intere in background. Al
ritorno, `onResume()` chiama `recover()`, cioè la stessa funzione del watchdog: la mossa
interrotta viene rifatta da capo guardando lo stato reale della partita.

**Dialoghi.** Riepilogo, scelta della presa, avviso della pausa e richiesta della password sono
tenuti in un campo e chiusi in `onDestroy()`. I dialoghi creati con `AlertDialog.Builder` non si
chiudono da soli: se il sistema distruggeva l'activity a dialogo aperto restavano appesi sia la
finestra (`WindowLeaked`) sia, nel caso della pausa, il `CountDownTimer`, che continuava a
scrivere su un pulsante ormai morto tenendo in vita l'intera activity.

**Memoria.** I livelli di `onTrimMemory` non stanno su un'unica scala di gravità, quindi sono
trattati in due casi distinti. `RUNNING_MODERATE`, `RUNNING_LOW` e `RUNNING_CRITICAL` valgono 5,
10 e 15: l'app è ancora in primo piano e la RAM sta finendo, e la cache si dimezza. Da
`UI_HIDDEN` (20) in su l'app non è più visibile e la cache si svuota del tutto. Con la vecchia
soglia unica `>= UI_HIDDEN` i primi tre non scattavano mai, proprio nei casi in cui liberare
memoria serve di più.

**Watchdog.** Se una mossa si blocca, dopo 4 secondi il gioco riparte da solo guardando lo
stato reale della partita. Resta fermo mentre è aperto il dialogo della pausa, altrimenti
cambierebbe lo stato dietro alla finestra.

**Pausa fra le partite.** Attiva di serie in **entrambi** i giochi: a fine partita compare
l'avviso sul gioco responsabile e bisogna aspettare un minuto. Si disattiva solo con la
password e la disattivazione scade dopo un'ora. Non c'è più il pulsante "Nuova partita" a
metà incontro in Briscola, che era l'unico modo di ricominciare saltando l'attesa.

## Cosa manca ancora

- **Salvataggio dello stato**: se Android uccide il processo, la partita in corso è persa.
  Serve `onSaveInstanceState` (o un salvataggio in `SharedPreferences`). I cambi di
  configurazione invece sono coperti: oltre alla rotazione, `configChanges` elenca ora anche
  `uiMode`, `fontScale`, `locale`, `layoutDirection` e `density`, così cambiare tema scuro,
  dimensione del carattere, lingua o aprire un pieghevole non fa più ripartire la mano da zero.
  Il prezzo è che le scritte già a schermo non si riscalano subito cambiando la dimensione del
  carattere: si aggiornano tornando al menu.
- Nessun suono, nessuna statistica, nessuna modalità a 4 giocatori.
