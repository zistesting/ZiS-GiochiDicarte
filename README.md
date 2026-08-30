# ZiS – Giochi di Carte

Scopa e Briscola contro il Banco. Progetto Android nativo (Kotlin + View Binding).

- `minSdk 24` · `targetSdk 36` · `compileSdk 36`
- AGP 8.9.1 · Gradle 8.11.1 · Kotlin 2.1.0 · JDK 17

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

1. `compileSdk 36`, `targetSdk 36`, AGP 8.9.1 (l'8.5.2 non supporta l'API 36), Gradle 8.11.1,
   Kotlin 2.1.0.
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
PNG a 256 colori. Le carte ZiS (`card_*`) vengono da originali a 560x1024, errore massimo
2,7/255. Le tradizionali (`trad_*`) sono scansioni di stampa: prima di ridurle e' stata tolta
la retinatura di quadricromia con filtro mediano piu' bilaterale, poi ognuna e' stata
ritagliata sul disegno e riportata alla proporzione delle carte aggiungendo bianco, non
stirando; errore massimo 4,3/255, piu' alto perche' il rumore di scansione si comprime peggio
del disegno a tinte piatte.

**I dorsi** sono disegnati in vettoriale: nascono gia' a 448x819, quindi non subiscono nessun
ridimensionamento. Un solo disegno con tre palette: blu/argento per il mazzo ZiS, grigi scuri per quello
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
dell'APK. Le immagini vanno salvate in PNG a **256 colori**: su disegni a tinte piatte l'errore
rispetto ai 24 bit è invisibile e il file pesa circa il 60% in meno.

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

**Apertura del Banco (Briscola).** Il Banco non ha piu' la regola secca "mai aprire di
briscola": con una mano tipo briscola 4 + briscola cavallo + un 3 lo obbligava a buttare il
carico, dieci punti regalati per non calare una briscola che non vale niente. Adesso confronta
il costo di ogni carta, cioe' i suoi punti piu' quanto varrebbe tenersela, e la briscola bassa
entra nel conto. Nelle mani normali il liscio resta comunque la scelta piu' economica.

**Callback differiti.** Tutti i passaggi di turno sono `postDelayed` su un unico `Handler`,
ripuliti in `onDestroy()`. Senza questo, uscire dall'app mentre gioca il Banco faceva partire
il dialogo di fine mano su un'activity distrutta (`BadTokenException`).

**Watchdog.** Se una mossa si blocca, dopo 4 secondi il gioco riparte da solo guardando lo
stato reale della partita. Resta fermo mentre è aperto il dialogo della pausa, altrimenti
cambierebbe lo stato dietro alla finestra.

**Pausa fra le partite.** Attiva di serie in **entrambi** i giochi: a fine partita compare
l'avviso sul gioco responsabile e bisogna aspettare un minuto. Si disattiva solo con la
password e la disattivazione scade dopo un'ora. Non c'è più il pulsante "Nuova partita" a
metà incontro in Briscola, che era l'unico modo di ricominciare saltando l'attesa.

## Cosa manca ancora

- **Salvataggio dello stato**: se Android uccide il processo, la partita in corso è persa.
  Serve `onSaveInstanceState` (o un salvataggio in `SharedPreferences`). La rotazione invece
  è già gestita.
- Nessun suono, nessuna statistica, nessuna modalità a 4 giocatori.
