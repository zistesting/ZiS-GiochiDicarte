# ZiS – Giochi di Carte

Scopa, Briscola e Tresette contro il Banco, piu' il solitario Klondike. Progetto Android
nativo (Kotlin + View Binding).

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
   sotto quella di navigazione. `SystemBars.kt` chiama `setDecorFitsSystemWindows(false)` e poi
   applica i margini giusti al contenuto con `setOnApplyWindowInsetsListener`, tenendo lo sfondo
   fino ai bordi. La chiamata esplicita non è ridondante: senza, sotto API 35 la decor view
   consumava gli inset da sola e il listener riceveva zero, quindi il risultato era corretto ma
   per un comportamento implicito della piattaforma, già cambiato una volta. Sostituisce
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
- Valutare `minifyEnabled true` + `shrinkResources true` per ridurre il pacchetto. Ora che le
  carte sono riferimenti diretti a `R.drawable` lo shrinker le vede usate da solo: il
  `res/raw/keep.xml` che le proteggeva è stato tolto. Va comunque provato su un dispositivo
  prima di pubblicare.

---

## Note tecniche

**Mazzo.** Quattro mazzi da 40 carte piu' dorso in `res/drawable-nodpi/`, tutti a **448x819**,
in **WebP**.

Le carte ZiS (`card_*`) sono state rifatte da quaranta immagini singole a 576x1024. Il
ritaglio e' automatico (`art/estrai_carta_singola.py`): la cornice si trova cercando le
colonne e le righe in cui almeno il 40% dei pixel e' scuro, cioe' le linee piene del bordo.

Un dettaglio che sembra un cavillo e non lo e': in fondo a ogni file c'e' una riga scura
isolata, un residuo di compressione, che ha quasi tanti pixel scuri quanti il bordo vero.
Contarli non basta a distinguerli; a separarli e' lo spessore, perche' il bordo della carta
e' alto 5 pixel e quel residuo 1. Per questo i gruppi di righe si scartano sotto i 3 pixel di
spessore invece che sotto una soglia di pixel scuri.

Ogni carta viene poi portata a 448x819 **aggiungendo margine bianco**, mai stirando, piu' un
margine fisso del 3% su tutti i lati: senza, la cornice disegnata, che sta esattamente sul
bordo, verrebbe tagliata dagli angoli arrotondati con cui `CardView` ritaglia la carta.

**Fondo bianco delle carte ZiS.** Il punto delicato e' capire qual e' il bianco. Il bianco si
stima dalla **moda dei pixel chiari del ritaglio**, cioe' dal valore che ricorre di piu' sopra
200: e' la faccia della carta, che occupa piu' area di qualunque altra tinta chiara. Da li'
un bilanciamento per canale piu' un appiattimento a rampa e non a soglia, per non lasciare
aloni sui contorni morbidi. La rampa parte da 244, mentre il grigio piu' chiaro del disegno,
le lame d'argento delle spade, sta sotto 235, quindi non viene toccato. Verificato su tutte e
quaranta: il fondo e' 255 pieno, angoli esterni compresi.

Il **dorso** `card_back` non viene dalle immagini delle carte: resta quello disegnato in vettoriale in `art/`.

**Mazzo napoletano.** Le carte `nap_*` vengono da un foglio 4x10 (una riga per seme: denari,
coppe, bastoni, spade). Qui non funzionava niente di quello che funzionava per gli altri
mazzi: le napoletane **non hanno cornice stampata**, sono carte bianche su fondo bianco, e per
giunta si sovrappongono fra loro, quindi manca anche il corridoio di sfondo che separava le
bergamasche. Cercare le righe in cui almeno il 40% dei pixel è scuro trova il disegno, non il
bordo.

L'unica traccia del bordo è una linea grigia di uno o due pixel dove una carta copre quella
accanto, debole e interrotta agli angoli arrotondati: presa da sola non basta. Le carte però
sono disposte con passo regolare, quindi invece di inseguire ogni bordo si stima **una
griglia**: si prova ogni passo e ogni fase plausibile e si tiene la combinazione su cui cade
il segnale più forte. Così i bordi deboli non si perdono, perché a decidere è la somma di
tutti e undici e non il singolo. Il passo trovato è 352,8 in orizzontale e 600,8 in verticale,
e cade sui bordi osservati entro pochi pixel.

Il ritaglio viene poi rientrato di otto pixel e la fascia esterna del 3,5% riportata a bianco
pieno. Rientrare non bastava: dove due carte si accavallano davvero la linea della vicina
entra più dentro, e su quaranta carte solo otto uscivano pulite. Cancellare quella fascia è
sicuro perché il disegno non ci arriva mai — la figura più sporgente si ferma a 27 pixel dal
bordo contro i 16 cancellati, verificato su tutte e quaranta.

Il dorso è la quarta palette di `art/card_back.py`, verde e giallo come gli inchiostri del
mazzo.

**Mazzo bergamasco.** Le carte `berg_*` vengono da un unico foglio 4x10 (una riga per seme,
in ogni riga A, 2-7, Fante, Cavallo, Re) trovato su Wikimedia Commons. Il ritaglio è in
`art/estrai_foglio_bergamasche.py` e usa lo stesso criterio degli altri: cella per cella si
cercano le righe e le colonne in cui almeno il 40% dei pixel è scuro, cioè il bordo stampato,
e dove il bordo del vicino sconfina la misura si riconosce da sola perché si discosta dalla
mediana delle quaranta, che sono tutte uguali.

Due cose da sapere su questo mazzo.

La prima: le bergamasche sono **più strette** delle altre. Il riquadro stampato ha proporzione
1,98 contro l'1,78 degli altri due mazzi, quindi dopo il riempimento a 448x819 la carta occupa
l'87% della larghezza invece del 94%. Non è un errore ed è voluto: la forma è quella vera, e
stirarla per pareggiare sarebbe stato peggio. I mazzi non si mescolano mai fra loro, quindi la
differenza non si vede in partita.

La seconda: l'**asso di denari era vuoto**. In questo tipo di mazzo è la carta su cui i
fabbricanti mettono il proprio marchio, e nella copia usata il marchio era stato tolto: restava
l'anello concentrico senza niente al centro, l'unica carta del mazzo che non diceva nulla.
Adesso ci sta il marchio ZiS — vedi *Gli assi di denari* più sotto.

Il dorso non viene dal foglio, che ne è privo: è lo stesso disegno vettoriale degli altri due,
con una terza palette (`berg` in `art/card_back.py`) presa dai tre inchiostri del mazzo, blu,
rosso e oro.

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
> domani qualcuno la chiede. Lo stesso vale per il mazzo ZiS: il modello e' il disegno
> tradizionale italiano, non il mazzo di un singolo produttore, e i fogli Armanino di inizio
> Novecento in `art/` lo documentano — hanno le stesse composizioni (aquila sull'asso di
> denari, putto sull'asso di spade, stemma sul 4 di denari) un secolo prima.

**I dorsi** sono disegnati in vettoriale: nascono gia' a 448x819, quindi non subiscono nessun
ridimensionamento. Sono gli unici due file salvati in WebP **senza perdita**: su un disegno a
tinte piatte il lossless pesa meno del lossy (74 KB contro 143 KB per `card_back`). Un solo disegno con una palette per mazzo: blu/argento per il mazzo ZiS, grigi scuri per quello
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

**Quattro mazzi.** I quattro mazzi sono quattro tabelle di riferimenti a `R.drawable` in `Decks.kt`,
indicizzate per seme e valore. L'impostazione **Mazzo** sceglie quale tabella usare.
Aggiungerne un quarto vuol dire una tabella in piu', un valore in `Prefs`, un radio nelle
impostazioni e una stringa: il resto del codice non sa quanti mazzi esistono.

Prima le carte si caricavano per nome con `Resources.getIdentifier("card_0_1", ...)`. Quella
funzione e' deprecata dall'API 29, fa un lookup per stringa **a ogni disegno** e soprattutto
rende le immagini invisibili allo shrinker delle risorse. Con i riferimenti diretti il costo a
runtime e' un accesso a un array, e se una carta manca il progetto **non compila**, invece di
mostrare un rettangolo bianco a partita iniziata.

Il rovescio della medaglia e' che il ripiego "immagine mancante -> carta ZiS corrispondente"
non esiste piu', e con esso e' sparito l'avviso "mazzo non installato" nelle impostazioni:
adesso tutti i file di tutti e tre i mazzi devono esserci al momento della compilazione. E'
uno scambio conveniente, perche' un errore di compilazione si vede subito mentre una carta
bianca si scopriva a partita in corso.

Cambiando mazzo la cache delle bitmap si svuota, altrimenti resterebbero a schermo le carte
del mazzo precedente.

**Cache bitmap.** `CardView` usa una `LruCache` limitata a 1/8 della heap, con chiave
`risorsa + larghezza`, cosi' misure diverse convivono invece di buttarsi a vicenda.
`ZisApp` la svuota quando il sistema segnala poca memoria.

**Riuso delle viste.** `render()` non ricostruisce piu' il tavolo da zero: aggiunge o toglie
solo le `CardView` che servono e aggiorna quelle che restano. Prima ogni chiamata buttava e
riallocava fino a venticinque viste (nel Tresette), piu' volte per ogni presa. L'unica
attenzione e' che le animazioni lasciano stato sulla vista, `visibility` a `INVISIBLE`,
`translation`, `alpha`, `scale`: prima lo azzerava la ricostruzione, ora lo azzera `resetCard`.

**Accessibilita'.** Ogni `CardView` espone una `contentDescription`: il nome italiano della
carta se e' scoperta, "Carta coperta" se non lo e', e nel Tresette l'aggiunta "non giocabile"
sulle carte spente dall'obbligo di seme. Senza quest'ultima chi usa TalkBack non avrebbe modo
di sapere che quella carta e' fuori gioco, perche' l'unico indizio era l'opacita' al 35%.

**Riepiloghi uniformi.** I tre giochi dicono le stesse cose con le stesse parole. La
terminologia e' una sola: una distribuzione e' una **mano**, la serie fino al bersaglio e' la
**partita**. Prima ognuno diceva la sua, e in Briscola "partita" indicava addirittura la
singola mano mentre la serie si chiamava "incontro": l'opposto della Scopa. Anche
l'impostazione di Briscola diceva "Partite da vincere" intendendo mani.

L'impaginazione del riepilogo e' la stessa per tutti: esito della mano, riga della partita, e
la riga del vincitore solo quando la partita e' finita. La Scopa la usa gia' cosi', con in
piu' la tabella dei quattro punti (che resta com'era); Briscola e Tresette la adottano tramite
`dialog_result.xml`, che e' la stessa struttura senza tabella. Prima quei due mettevano tutto
nel messaggio dell'`AlertDialog`, quindi le singole righe non si potevano colorare.

Anche la schermata di gioco ora si legge allo stesso modo nei tre: riga della partita in alto,
poi la situazione della mano per ciascuno. La Scopa ha guadagnato la riga della partita, che
prima era mescolata dentro il punteggio di ciascun giocatore.

**Colore per esito.** Le righe di esito sono **oro quando sei in vantaggio tu**, celeste negli
altri casi: vale per la riga della mano, per quella della partita e per quella del vincitore,
in tutti e tre i giochi. La regola sta in `Outcome.kt` e non dentro le activity apposta,
perche' deve restare identica: e' quella che fa capire come sta andando prima ancora di
leggere i numeri, e basta che due schermate la applichino in modo diverso perche' l'indizio
smetta di funzionare. La riga del totale nella tabella della Scopa resta oro per entrambe le
colonne, perche' li' e' un'intestazione e non un esito.

**Statistiche.** Il pulsante in fondo alla schermata iniziale apre una tabella con, per ogni
gioco, le partite concluse, quelle vinte dal Banco e quelle vinte dall'utente, piu' la riga
dei totali. Le colonne le tiene una `TableLayout` con gli stessi stili del riepilogo di fine
mano della Scopa, cosi' le due tabelle dell'app si somigliano invece di essere ognuna a modo
suo. Righe e colonne sono pero' scambiate rispetto a quella: qui le righe sono i giochi,
perche' i loro nomi sono parole lunghe e stanno meglio nella colonna larga di sinistra,
lasciando alle tre colonne strette i soli numeri.

Si contano **solo le vittorie**, da una parte e dall'altra: le partite giocate sono la loro
somma. Un terzo contatore separato potrebbe divergere dagli altri due, e non ci sarebbe modo
di sapere quale dei tre ha ragione. Le partite abbandonate a meta' non si contano, perche'
non hanno un esito; la tabella lo dice quando e' vuota, invece di lasciar credere che il
conteggio sia rotto.

**I quattro giochi stanno su due colonne**, e sotto c'e' Esci. Prima erano cinque pulsanti in
fila: 5 x 56dp piu' quattro spazi da 12dp fanno 328dp di altezza, che su un telefono da 640dp,
con logo, titolo e riga in fondo, non lasciava respiro a niente. Su due colonne diventano 192dp.
Le misure stanno in `dimen.xml` e non nel layout, perche' le usano sei pulsanti su tre righe: coi
numeri ripetuti sei volte, cambiarne uno e dimenticarne un altro sfasa la griglia.

I quattro pulsanti in fondo alla schermata iniziale sono scesi da 60 a 52dp con margini piu'
stretti: a 60dp quattro pulsanti facevano 384dp e su un telefono da 360dp non ci stavano.
Adesso sono 304dp in tutto.

**Pulsante info.** Ogni schermata di gioco ha in alto a sinistra un pulsante che apre le
regole del gioco in corso; la schermata iniziale ne ha uno che apre la presentazione dell'app
e la parte sulla privacy. La finestra e' costruita in `InfoDialog.kt` con un `ScrollView`
esplicito e non con il messaggio dell'`AlertDialog`: quel messaggio scorre da solo su alcune
versioni di Android e su altre no, e i testi qui sono lunghi, quindi su meta' dei telefoni
l'ultimo paragrafo sarebbe finito tagliato.

**Privacy.** Il testo informativo dice che l'app non chiede nessun permesso, e la cosa e'
verificabile: nel manifest non c'e' un solo `uses-permission`. Niente internet, niente
rubrica, niente posizione, niente fotocamera, niente file. L'unica cosa salvata sono le
preferenze di gioco, in `SharedPreferences`, sul telefono. L'unico collegamento verso
l'esterno e' il logo ZiS della schermata iniziale, che apre `zis.it` nel browser di sistema
con un intent: e' il browser a connettersi, non l'app.

**Pausa responsabile.** Disattivarla non chiede piu' una password. Era una barriera che non
proteggeva nessuno, perche' e' lo stesso utente a decidere per se stesso, e si limitava a
mettere un ostacolo in mezzo. L'interruttore ora si muove liberamente nei due sensi.

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

**Tempi di gioco.** Tutti in `Timing.kt`, uno solo per i tre giochi contro il Banco. Con il **gioco
automatico** attivo valgono zero: niente animazioni, niente attese, niente cartelli SCOPA, e
la partita scorre alla massima velocità. Le mosse passano comunque dall'`Handler` una alla
volta, quindi non si annidano sullo stack e l'app resta reattiva.

**Riepilogo di fine mano (Scopa).** E' una `TableLayout` (`res/layout/dialog_score.xml`)
passata al dialogo con `setView`, non un testo. Prima le colonne erano tenute in riga con gli
spazi e un carattere a larghezza fissa: 27 caratteri che sui telefoni stretti andavano a capo
e mandavano tutto fuori squadra. Con la tabella le colonne le tiene il layout e si usa il
carattere normale dell'app.

## Gli assi di denari

I quattro assi di denari non vengono dai fogli di scansione come le altre trentanove carte di
ogni mazzo: sono immagini a parte, col marchio ZiS e la scritta, e stanno in `art/assi/` già
nel formato 448x819. Le porta nel mazzo `art/assi_denari.py`.

Uno script e non un ritaglio fatto a mano, per due motivi che non hanno a che vedere con
l'estetica. Il primo: le carte si ritagliano dai fogli con `estrai_foglio_bergamasche.py` e
compagnia, e il giorno che uno di quei fogli si rigenera l'asso tornerebbe quello di serie
senza che nessuno se ne accorga. Il secondo: i PNG hanno gli angoli trasparenti, e appiattirli
sul nero invece che sul bianco farebbe comparire quattro spicchi scuri agli angoli della carta.

`art/fogli_mazzo.py` produce un foglio per mazzo con tutte le carte più il dorso, alla
risoluzione nativa: quattro righe, una per seme, più il dorso da solo e centrato in fondo.
Vale per tutti e cinque i mazzi, francese compreso, e la misura della carta la dettano le
carte stesse — le italiane sono 448x819, le francesi 600x840. A 300 dpi un foglio italiano
misura 41 x 37 cm.

## Klondike

Il primo gioco dell'app senza avversario, e questo cambia piu' cose di quante sembri. Non c'e'
nessuna euristica, nessuna ricerca, nessun budget di nodi: meta' del lavoro fatto per gli
altri tre non si applica. In compenso servono due cose che gli altri non hanno mai avuto:
l'**annulla**, perche' un solitario senza annulla e' una punizione, e il **movimento di
gruppo**, perche' si spostano sequenze e non una carta per volta.

**Le colonne.** Ogni colonna e' divisa in due liste, coperte e scoperte, invece di una lista
piu' un contatore di quante sono girate. Evita l'errore piu' facile del gioco: sbagliare
l'indice che separa le due parti e spostare una carta che il giocatore non ha ancora visto.

Le carte scoperte formano **sempre** una sequenza valida a scendere e a colori alternati. Non
e' una speranza, e' una conseguenza: una carta ci arriva solo se ci sta, e quando se ne scopre
una nuova quella diventa l'unica scoperta. Per questo un gruppo da spostare e' semplicemente
una parte finale delle scoperte, senza doverla ricontrollare.

**L'annulla tiene una fotografia per mossa**, non la mossa da rifare al contrario. Rifare al
contrario costa meno memoria ma ha una trappola: una mossa che scopre una carta non e'
reversibile da sola, perche' rimettendo giu' la carta bisogna anche ricordarsi di ricoprire
quella sotto. Con le fotografie il problema non esiste, e cento mosse di storia stanno in
pochi chilobyte. La storia non viene salvata chiudendo l'app: si riprende la partita dov'era,
ma non si torna indietro a prima della chiusura.

**Pescare a una o a tre non e' un dettaglio estetico.** Pescando a tre, due terzi del tallone
non passano mai in cima nel giro in corso, e le smazzate impossibili passano da circa una su
undici a circa una su cinque. Sta nel costruttore proprio perche' e' il parametro che decide
quanto e' duro il gioco.

**Come e' stato verificato.** Il motore e' in Kotlin e qui non si esegue, quindi le stesse
regole sono state riscritte in Python (`verifica/`) e messe alla prova in due modi. Mille
partite a caso con un controllo dopo **ogni mossa** su conservazione delle 52 carte, validita'
delle sequenze, ordine delle fondazioni e scopertura: 400.000 mosse, nessuna violazione. E un
solutore che misura quante smazzate sono vincibili, per confrontarlo con la letteratura: sale
da 52% a 68% a 76% alzando il budget di nodi, quindi il limite e' il solutore e non le regole,
e il divario fra pesca da uno e da tre e' dello stesso ordine dei nove punti pubblicati.

**Lo schermo.** Le carte non stanno nel layout: c'e' un contenitore solo e dentro le
posiziona il codice, alla coordinata calcolata. E' l'opposto degli altri tre giochi, dove le
mani sono `LinearLayout` e le carte figli in fila, ed e' una scelta obbligata: qui le carte si
sovrappongono a ventaglio con scarti diversi fra coperte e scoperte, e quello scarto va
compresso quando la colonna si allunga. Un `LinearLayout` non sa fare niente di tutto questo.

La misura della carta e' il **minimo fra due vincoli**, come fa `CardSize` per gli altri tre
giochi. In larghezza sette colonne devono stare affiancate, e su un telefono e' questo che
comanda: viene 140 px per 196. In altezza devono starci la fila alta piu' una colonna di
riferimento — sei coperte e sette scoperte, in tutto 4,76 altezze di carta, un numero che viene
dagli sfalsamenti veri e non da una stima. Il secondo vincolo serve dove il primo da' un
risultato assurdo: in orizzontale su un dispositivo grande la larghezza disponibile e' quasi il
doppio, e guardando solo quella le carte venivano tanto alte che alle colonne non restava
spazio. Quando comanda l'altezza le sette colonne non riempiono piu' la larghezza e il blocco
si centra.

La proporzione e' 1,4 e non l'1,829 delle carte italiane: e' il motivo per cui la misura non
passa da `CardSize`, che quella proporzione ce l'ha cablata.

**La geometria si ricalcola quando il contenitore cambia misura**, con un listener sul layout
piu' `onConfigurationChanged`. Non e' un dettaglio: le carte non stanno nel layout, le
posiziona il codice su queste misure, e calcolarle una volta sola alla creazione voleva dire
che dopo una rotazione o l'apertura dello schermo diviso restavano alle coordinate della
finestra precedente.

**Ricomincia** rimette la stessa smazzata dall'inizio, e sta fra Annulla e Rigioca. Non è una
comodità: una smazzata su undici è impossibile, il giocatore non vede le ventuno carte coperte
e l'annulla si ferma a cento mosse, quindi una scelta irreversibile presa presto butta via la
partita senza rimedio. Con Ricomincia si riprova.

Non conta niente in statistica, ed è la differenza con Rigioca: quello cambia smazzata, cioè
rinuncia, e conta come partita persa. Il mazzo come è stato distribuito viene salvato con la
partita, se no dopo una ripresa non ci sarebbe più niente da ricominciare; non sta dentro
`save()` del motore, perché quella la usa anche la pila dell'annulla e cinquantadue carte per
ognuna delle cento fotografie sarebbero quindici chilobyte per un dato che non cambia mai.

Tre pulsanti dove prima ce n'erano due lasciano circa 106dp per pulsante su uno schermo da
360dp, e il cartiglio se ne prende 24 di padding interno: "Ricomincia" a 15sp non ci starebbe.
Da qui `autoSizeTextType`, che rimpicciolisce il testo fino a 11sp quanto basta a starci
invece di mandarlo a capo. Copre anche il carattere di sistema ingrandito.

**Il gioco automatico** delle impostazioni ora vale anche qui: prima lo leggevano solo i tre
giochi contro il Banco e nel Klondike accenderlo non faceva niente. Non e' un risolutore e non
prova a esserlo - serve a provare in fretta che animazioni, annulla, vittoria e statistiche
funzionino - ma vince il 32% delle smazzate pescando una carta alla volta e il 6,6% pescando a
tre, misurato su 1500 partite col motore in `verifica/`. La regola che vale piu' di tutte le
altre insieme e' svuotare una colonna spostandone tutte le scoperte su un'altra: da sola porta
le vittorie dal 9% al 32%, perche' una colonna vuota e' l'unico posto dove puo' andare un Re.
Il rischio di un giocatore automatico non e' giocare male, e' girare in tondo: percio' fra
colonne si muove solo quando la mossa scopre una carta coperta o svuota una colonna, cioe' solo
quando non si puo' disfare, e non riporta mai giu' una carta dalla fondazione.

**Rigioca chiede sempre conferma**, tranne a partita finita, e la domanda dice se la partita in
corso contera' come persa: sopra le cinque mosse si', sotto no. Una conferma che non informa
chiede solo di ripetere il tocco.

Le coperte si sfalsano del 16% dell'altezza, le scoperte del **32%**: di una coperta basta
vedere che c'e', di una scoperta bisogna leggere l'angolo. Il 32 non e' arrotondato a occhio,
lo fissa l'indice: l'inchiostro dell'indice arriva a 0,310 dell'altezza della carta, e con lo
sfalso al 30% restava tagliato il gancio del J, che senza il gancio si legge come una I. Se la colonna sfora, i due scarti si
comprimono insieme. Verificato simulando il tavolo con le carte vere: una colonna da diciannove
carte ci sta ancora senza comprimere niente.

**Le tre figure** non sono disegnate in vettoriale: vengono da tre immagini piatte in
`art/figure/`, normalizzate a quattro soli colori con lo sfondo trasparente e ricolorate per
seme. Cosi' la ricolorazione e' una sostituzione esatta di quattro valori RGB e non un filtro:
niente tinte intermedie da indovinare, nessun alone. La tabella sta in `CROMIA`, in cima a
`art/carte_francesi.py`.

Cuori carminio e oro, quadri vermiglio e ambra, fiori verde scuro e argento verde, picche
ardesia e acciaio. Il vincolo non e' estetico: le colonne si costruiscono a **colori
alternati**, quindi rosso e nero devono restare inconfondibili, e per questo i due semi rossi
hanno due rossi e i due neri due scuri freddi. Tratto quasi nero e bianco del viso restano
uguali in tutti e quattro, se no il disegno perde leggibilita'.

La figura riempie il pannello centrale, che con un indice solo va da 0,325 a 0,965
dell'altezza: comanda la larghezza, perche' con la proporzione di queste tre immagini
l'altezza avanza sempre. E' appoggiata in basso, perche' e' un mezzo busto e la testa deve
finire subito sotto l'indice.

**Un indice solo, in alto a sinistra, e grande.** Non e' una scelta estetica. In colonna di
ogni carta si vede solo la fascia in alto, e su un telefono quella fascia e' larga una
quarantina di punti: se il valore non si legge li', non si legge da nessuna parte, perche' il
disegno al centro resta coperto.

Il secondo indice, quello in basso a destra, e' stato tolto. Sulle carte vere c'e' perche' una
carta in mano la puoi tenere in due versi; qui le carte stanno in tavola sempre nello stesso
verso, e questo mazzo lo usa solo il Klondike. E non e' che si vedesse poco: non si vedeva
**mai**. In colonna ogni carta e' coperta dal basso da quella sotto, quindi si vede la fascia
in alto; nel ventaglio degli scarti le carte vecchie sono coperte a destra da quella in cima,
quindi si vede ancora l'angolo in alto a sinistra; nelle fondazioni si vede solo la carta in
cima. L'unico caso in cui era visibile e' l'ultima carta di una colonna, dove sopra c'e' gia'
quello giusto. Costava un terzo dell'altezza: togliendolo, il pannello centrale passa dal 30%
al 64%, e la figura raddoppia in entrambi i versi.

**L'indice e' allineato sull'inchiostro, non sulla linea di base.** Si rasterizza ogni valore e
si misura (`ink` in `art/carte_francesi.py`), poi si scegle il font-size perche' il *corpo*
venga uguale per tutti; da li' esce anche una sola linea di base, e il simbolo del seme si
piazza dopo la larghezza **misurata** del valore. Si misura invece di leggere le metriche del
font perche' le metriche richiederebbero di sapere quale font e' installato sulla macchina che
genera i file, e il `font-family` e' un elenco con dei ripieghi.

Serve a risolvere due difetti che il mazzo aveva e che non erano dettagli:

- **sul 10 il simbolo del seme finiva sopra allo zero.** cairosvg ignora `textLength`, quindi
  il valore usciva alla sua larghezza naturale - 321 px invece dei 212 chiesti - e il simbolo,
  piazzato dove `textLength` diceva, gli andava addosso;
- **in colonna il J si leggeva come una I**, perche' il gancio scendeva fino a 0,360
  dell'altezza mentre la fascia visibile si fermava a 0,300. Alla Q capitava lo stesso con la
  coda.

Adesso l'inchiostro di qualunque valore sta dentro 0,030-0,310, verificato su tutte e 52, e lo
sfalso delle scoperte e' 0,32: l'indice si vede sempre intero.

**La griglia tradizionale di simboli** ha ripreso il posto del simbolo unico grande. I simboli
sono alti il 21% del pannello, e il tetto non e' l'altezza ma la larghezza: le tre colonne hanno
i centri a cento pixel l'una dall'altra e picche, cuori e fiori sono larghi quasi quanto sono
alti, quindi oltre quella misura si toccherebbero. Il
ragionamento che aveva portato al simbolo unico era che dieci quadri affiancati su una carta
larga 140 pixel diventano una macchia da contare, e a quella misura non si contano: e' vero, ma
risponde a una domanda che nessuno pone. Il valore lo dice l'indice, e in colonna il centro
della carta **non si vede affatto**, tranne che sull'ultima carta di ogni colonna. Quindi la
scelta non e' fra due leggibilita', e' fra due decorazioni, e fra le due vince quella che fa
sembrare una carta da gioco una carta da gioco.

Le posizioni sono quelle di `DISPOSIZIONI`, che era gia' scritta nel generatore e di cui prima
si usavano **solo le chiavi**, come test per distinguere numerali e figure: le coordinate erano
dati morti. Le Y coprono 0,16-0,84 del riquadro di allora e qui si riportano nel pannello
mandando i simboli estremi a toccarne esattamente i bordi, non con un fattore a occhio: con un
fattore il primo simbolo finiva quindici pixel sopra il pannello, cioe' dentro la fascia che in
colonna resta visibile, e sotto l'indice di ogni numerale comparivano due puntine scure. La
meta' bassa dei simboli e' capovolta come nei mazzi veri: con un indice solo e' decorazione,
ma e' quella che la fa leggere come una carta invece che come una griglia di icone. L'Asso fa
storia a se', un simbolo solo e grande al centro, perche' con la misura degli altri diventava
un puntino in mezzo al vuoto.

**Il ventaglio degli scarti e' ancorato alla carta IN CIMA**, non alla prima. Le piu' vecchie
si dispongono a scalare verso sinistra. La carta in cima e' l'unica giocabile, e ancorando la
prima si spostava ogni volta che il numero di carte visibili passava da tre a due a una:
ancorando quella in cima, sta sempre nello stesso punto e la si prende senza guardare.

**Le carte scivolano** dalla posizione vecchia a quella nuova. L'animazione non sa quale mossa
e' stata fatta: prima di muovere si fotografa dove si trovava ogni carta, dopo il disegno si
confrontano le posizioni e si anima tutto quello che si e' spostato. Cosi' funziona da sola
anche per le mosse che spostano un gruppo, o per il rigiro del tallone che ne sposta
ventiquattro insieme, senza un caso per ogni tipo di mossa. E la vista e' gia' al posto giusto
prima di partire: si riporta indietro e la si lascia tornare, quindi l'animazione non puo'
finire in un punto sbagliato, e se viene interrotta la carta al massimo salta in posizione.

**translationZ e non bringToFront.** La prima versione dell'animazione portava avanti la carta
in movimento con `bringToFront`, che riordina i figli del contenitore. Ma l'ordine dei figli e'
esattamente cio' che impila i ventagli: animando piu' carte insieme - un gruppo, o le
ventiquattro del rigiro del tallone - l'ordine in cui venivano portate avanti era quello
casuale della mappa, e dopo qualche mossa il ventaglio degli scarti si ritrovava impilato al
contrario. Si vedeva sfalsato, e soprattutto una carta vecchia, che non ha il tocco, finiva
sopra a quella in cima, che ce l'ha: sembrava che le carte non si potessero piu' prendere,
mentre il tocco arrivava alla carta sbagliata. `translationZ` alza la carta solo per il
disegno, senza toccare l'ordine dei figli.

**Un difetto di larghezza che sembrava un difetto di altezza.** Nella prima versione la scritta
con le mosse divideva la riga con i pulsanti: su un telefono stretto i pulsanti si prendevano
quasi tutta la larghezza, alla scritta ne restava una ventina di punti e andava a capo una
lettera per riga. La barra diventava alta mezzo schermo, il tavolo prende l'altezza che avanza,
e le colonne si schiacciavano fino a mostrare una carta sola. Chi guardava vedeva le colonne
collassate e cercava il difetto nel calcolo dei ventagli, che era giusto. Adesso la scritta sta
su una riga sua con `maxLines="1"` e i pulsanti sono in fondo.

**Si gioca a tocchi**, non trascinando. Tocchi una carta e va dove ha senso: prima la
fondazione, poi una colonna, preferendo quella che non consuma uno spazio vuoto. Su un telefono
e' anche piu' preciso del trascinamento, perche' una carta larga quaranta punti si prende male
con un dito. Il prezzo e' che quando una carta potrebbe andare in due colonne diverse la scelta
la fa il gioco: l'annulla e' li' apposta.

**Le viste si riusano e non si rimuovono, si nascondono.** Toglierle per posizione sarebbe
sbagliato: l'ordine dei figli viene rimescolato da `bringToFront`, che e' quello che impila i
ventagli nel verso giusto, e si finirebbe per rimuovere una carta ancora in uso.

**Una trappola evitata.** Il motore non usa `removeLast()` sulle liste. Kotlin ha da sempre
quell'estensione, ma Java 21 ha aggiunto un metodo omonimo a `java.util.List`, arrivato su
Android solo con l'API 35: compilando contro l'SDK 36 il compilatore risolve sul metodo Java,
il codice compila senza un fiato e poi si pianta con `NoSuchMethodError` su qualunque telefono
sotto Android 15 — che con `minSdk 24` vuol dire quasi tutti. Gli altri tre giochi non ne
soffrono perche' usano `ArrayDeque`, che ha metodi propri con quel nome.

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

Quanto restano scoperte le carte pescate si regola dalle impostazioni: 1, 2 o 3 secondi,
2 di partenza. Quanto tempo serva per leggere una carta dipende da chi gioca, e siccome da
li' passa meta' dell'informazione della partita non e' un dettaglio estetico.

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

Sempre nel Tresette, `keepValue` misura quanto costa separarsi da una carta: i punti che
perdi più il valore di controllo, perché una carta che nessuno può più battere è una presa
sicura più avanti. Il peso (`KEEP_WEIGHT`) è **1,3** e vale sia in apertura sia in risposta:
la stessa carta deve valere lo stesso, se no il Banco la giudica in due modi diversi a seconda
di chi apre.

Il numero è tarato, non scelto a occhio. Sotto 1,0 il Banco esce di 3 anche quando quel seme
non gli frutta niente: il terzo che incassa calando il 3 compensa quasi per intero il costo di
separarsene, e a 0,9 la differenza era di un decimo di punto a favore del 3. A 1,3 esce invece
con la carta bassa, ma continua a uscire di 3 quando in quel seme l'avversario ha solo l'asso
e deve calarlo — lì il margine resta ampio, perché non è una regola «non uscire mai di 3» ma
«non uscirne quando non frutta». Verso l'alto il limite è circa 2,8: oltre, il Banco terrebbe
il 3 anche quando c'è un asso da catturare, che è il difetto opposto.

In **Scopa**, a mazzo finito restano al massimo sei giocate e si cerca la migliore con un
minimax e taglio alfa-beta (`solve` in `ScopaGame.kt`). Cosi' il Banco sa se sta regalando una
scopa, sa che conviene fare l'ultima presa (chi la fa si porta via il tavolo) e chiude bene
primiera e denari. Sulla **primiera** vale la regola tradizionale: chi non ha preso nemmeno una
carta di un seme non concorre, e se non concorre nessuno dei due il punto non viene assegnato,
come in caso di parita'. L'alfa-beta non e' un lusso: senza, l'albero e' cento volte piu' grande.
La ricerca costa 0,04 ms in media, quindi non si sente. C'e' comunque un tetto di 60.000 nodi
che fa ricadere sull'euristica, ma su ventimila ricerche il massimo osservato e' stato 221.

Le prese possibili per una carta (`capturesOn`) non si enumerano piu' provando tutte le `2^n`
maschere di bit del tavolo: e' una ricorsione su somma che scarta le carte gia' piu' grandi del
bersaglio e, tenendo le altre ordinate, si ferma appena la piu' piccola disponibile sfora. Il
costo passa da esponenziale nel numero di carte in tavola a esponenziale nel bersaglio, che
nella scopa non supera mai 10. Non era un dettaglio: il tetto di nodi conta i nodi, **non** il
lavoro fatto dentro ognuno, e `capturesFor` viene chiamata anche al tocco dell'utente, cioe'
sul thread della UI. Su un tavolo da sedici carte senza prese singole sono due ordini di
grandezza; le combinazioni restituite sono le stesse, verificate su 20.000 tavoli casuali.

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

**La briscola sta coricata sotto il mazzo.** Il riquadro che la contiene ha le misure
scambiate (largo quanto e' alta una carta, alto quanto e' larga) e la carta dentro e' ruotata
di 90 gradi. Due dettagli non ovvi. Primo: e' centrata sul mazzo e non affiancata, perche'
coricata e' larga quasi due carte e messa di fianco arriverebbe sotto le carte della presa;
centrata invece sporge di mezza carta per lato, e la meta' di sinistra sparisce fuori schermo
insieme al mazzo. Verificato dal telefono piccolo al tablet: fra la punta della briscola e la
prima carta della presa restano sempre almeno 26dp. Secondo: la rotazione e' **oraria**, cosi'
la meta' che resta in vista e' quella alta della carta; girandola dall'altra parte si vedrebbe
la meta' bassa, cioe' il cartiglio vuoto, e la briscola sarebbe piu' difficile da riconoscere
con un'occhiata. Serve anche `clipChildren = false` sul riquadro e sulla riga, altrimenti la
carta coricata, che esce dai loro confini, verrebbe tagliata.

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

**Dialoghi.** Riepilogo, scelta della presa e avviso della pausa sono tenuti in un campo e chiusi in `onDestroy()`. I dialoghi creati con `AlertDialog.Builder` non si
chiudono da soli: se il sistema distruggeva l'activity a dialogo aperto restavano appesi sia la
finestra (`WindowLeaked`) sia, nel caso della pausa, il `CountDownTimer`, che continuava a
scrivere su un pulsante ormai morto tenendo in vita l'intera activity.

**Pulsante Esci.** Chiude l'app per davvero: `finishAndRemoveTask` invece di
`finishAffinity`, così sparisce anche la scheda dalle app recenti, e poi `exitProcess` per
terminare il processo.

Prima di tutto questo le preferenze vengono forzate su disco con `Prefs.flush` e
`SavedGame.flush`. Tutti i salvataggi dell'app usano `apply()`, che aggiorna la memoria e
rimanda il file a dopo: nel funzionamento normale Android completa quelle scritture quando
l'ultima activity si ferma, ma chiudendo il processo a mano non le aspetta più nessuno, e si
perderebbero l'ultima impostazione toccata, l'ultima statistica e la partita salvata.

Su `exitProcess` va detto che Android non ne ha bisogno: dopo `finishAndRemoveTask` il
processo resta in giro «vuoto», non consuma nulla di utile ed è il primo che il sistema butta
via quando serve memoria. Ucciderlo a mano è sconsigliato in generale, perché un'app con
servizi o lavori in sospeso può restarci male. Questa non ne ha nessuno — niente servizi,
niente permessi, niente rete — e dopo il flush non ci sono scritture in sospeso, quindi qui è
sicuro. **Se un domani l'app dovesse acquisire un servizio o un WorkManager, quella riga va
tolta per prima.**

**Partita ripresa.** La mano in corso si salva nelle preferenze in `onStop` e si rilegge in
`onCreate`: chiudendo l'app a metà partita, riaprendola si riprende da dove eri. Non è
`onSaveInstanceState`, che copre solo il caso «il sistema ha ucciso il processo mentre l'app
era in secondo piano» e sparisce appena l'app viene tolta dalle recenti o il telefono viene
riavviato.

Il salvataggio si cancella in `onDestroy`, ma **solo se** l'activity sta davvero finendo, cioè
se sei uscito col pulsante Menu o col tasto indietro: uscire è una scelta, e chi esce non si
aspetta di ritrovarsi la stessa mano. Chiudere l'app non passa da `onDestroy`, e lì la partita
resta.

Il formato (`SavedGame.kt`) è volutamente elementare: sezioni separate da `|`, numeri separati
da `,`, ogni carta è un numero da 0 a 39. Niente JSON e niente serializzazione automatica, così
non servono plugin né dipendenze in più. Un numero di versione in testa fa buttare i
salvataggi vecchi dopo un aggiornamento che cambi i campi, invece di leggerli storti.

Il ripristino riusa `recover()`, che già guardava lo stato reale della partita per ripartire
dopo un `onStop`: una mossa interrotta a metà si perde e si rifà, esattamente come già
succedeva tornando dall'app in background. L'unico dato che il motore non sa ricostruire da
solo è **se i punti della mano sono già stati assegnati**: a mano finita lo stato è identico
prima e dopo, quindi senza un flag apposta riprendere una partita chiusa col riepilogo aperto
rifarebbe i conti, raddoppiando i punti dell'incontro e la vittoria nelle statistiche. Per
questo l'assegnazione dei punti e la finestra di riepilogo sono due funzioni separate, e il
ripristino chiama solo la seconda.

**Memoria.** I livelli di `onTrimMemory` non stanno su un'unica scala di gravità, e per giunta
non arrivano più tutti. Da **API 34** il sistema non notifica più le app di `RUNNING_MODERATE`
(5), `RUNNING_LOW` (10), `RUNNING_CRITICAL` (15), `MODERATE` (60) e `COMPLETE` (80): sono
deprecati con la nota «Apps are not notified of this level since API level 34». Anche
`onLowMemory()` non viene più chiamata. Su un telefono moderno arrivano quindi solo
`UI_HIDDEN` (20) e `BACKGROUND` (40), cioè i due casi in cui l'app non è più visibile e la
cache si svuota del tutto.

Il ramo che dimezza la cache resta perché il `minSdk` è 24: su Android 13 e precedenti quelle
segnalazioni arrivano ancora ed è lì che serve. Non serve invece rimpiazzarle leggendo
`ActivityManager.getMyMemoryState()`: esiste dall'API 16, quindi non costerebbe compatibilità,
ma su Android 14+ la `LruCache` si autoregola già (è tarata su 1/8 della heap) e interrogare
lo stato della memoria a ogni fotogramma costerebbe più di quanto farebbe risparmiare.

**Watchdog.** Se una mossa si blocca, dopo 4 secondi il gioco riparte da solo guardando lo
stato reale della partita. Resta fermo mentre è aperto il dialogo della pausa, altrimenti
cambierebbe lo stato dietro alla finestra.

**Pausa fra gli incontri.** Attiva di serie in **tutti e tre** i giochi contro il Banco: a fine
incontro compare l'avviso sul gioco responsabile e bisogna aspettare un minuto. Si disattiva
dalle impostazioni, senza password (vedi "Pausa responsabile" qui sopra), e la disattivazione
scade dopo un'ora. Non c'è più il pulsante "Nuovo incontro" a metà incontro in Briscola, che
era l'unico modo di ricominciare saltando l'attesa.

## Cosa manca ancora

- Nessun suono, nessuna modalità a 4 giocatori.
