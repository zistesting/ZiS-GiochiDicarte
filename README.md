# ZiS – Giochi di Carte

Scopa e Briscola contro il Banco. Progetto Android nativo (Kotlin + View Binding).

- `minSdk 24` · `targetSdk 34` · `compileSdk 34`
- AGP 8.5.2 · Gradle 8.7 · Kotlin 1.9.24 · JDK 17

---

## Compilare

**Su GitHub:** il workflow `.github/workflows/build-apk.yml` parte a ogni push.
L'APK si scarica da **Actions → ultimo run → Artifacts → ZiS-GiochiDiCarte**.

**In locale:**

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

---

## Firma

Il keystore **non sta più nel repository** e le password non sono più scritte in
`build.gradle`. Senza configurazione il progetto compila lo stesso, firmato con la
chiave di debug standard di Android.

### In locale

Metti `scopa.keystore` in `app/` e crea `keystore.properties` nella cartella
principale (è già nel `.gitignore`):

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

> **Importante.** La vecchia chiave va considerata compromessa: password e file
> erano nel repository. Se il repo è pubblico, chiunque poteva firmare un APK che
> Android riconosce come il tuo. Il file va tolto anche dalla cronologia di git:
>
> ```bash
> git rm --cached app/scopa.keystore
> git commit -m "Rimuove il keystore dal repository"
> # per ripulire anche la history servono git-filter-repo o BFG Repo-Cleaner
> ```
>
> Se hai già distribuito APK firmati con quella chiave, continua a usarla (tramite
> i secret) finché non passi a Play App Signing: cambiare firma obbliga chi ha già
> l'app a disinstallarla prima di aggiornare.

---

## "Package conflict" quando installi un aggiornamento

Android accetta un aggiornamento solo se e' firmato con la **stessa chiave** della versione
gia' installata. Senza i secret configurati, ogni build su GitHub Actions genera una chiave
di debug nuova, quindi la firma cambia a ogni run e l'installazione viene rifiutata.

La soluzione e' configurare i quattro secret (vedi sopra). Una volta fatto, tutti gli APK
usciranno firmati con `scopa.keystore` e gli aggiornamenti si installeranno sopra il
precedente senza disinstallare.

Per convertire il keystore in base64:

```bash
base64 -w0 app/scopa.keystore          # Linux
base64 -i app/scopa.keystore | tr -d '\n'   # macOS
```

Su Windows, in PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\scopa.keystore")) | Set-Clipboard
```

Attenzione: l'APK gia' installato ora sul telefono e' firmato con una chiave di debug
usa-e-getta, quindi **quella disinstallazione la devi fare una volta sola**. Da li' in poi,
con i secret attivi, non servira' piu'.

## Migrazione a targetSdk 36 (necessaria per il Play Store)

Dal **31 agosto 2026** Google Play accetta nuove app e aggiornamenti solo con
`targetSdk 36` o superiore. Non è una modifica di una riga sola, quindi è tenuta
fuori da questa versione: il progetto compila e funziona così com'è.

Quando la affronti:

1. `app/build.gradle` → `compileSdk 36`, `targetSdk 36`
2. `build.gradle` → AGP almeno `8.9.1` (compileSdk 36 non è supportato dall'8.5.2)
3. `gradle/wrapper/gradle-wrapper.properties` → Gradle compatibile con l'AGP scelto
4. Da API 35 l'**edge-to-edge è obbligatorio**: le schermate vanno sotto status bar
   e navigation bar. I layout hanno già `android:fitsSystemWindows="true"` sul root,
   che copre questi casi semplici; controlla comunque ogni schermata su un
   dispositivo reale.
5. Per pubblicare serve un **AAB**, non un APK: `./gradlew bundleRelease`.

---

## Note tecniche

**Mazzo.** 40 carte + dorso, tutte 320x585 px, palette a 256 colori (l'errore
rispetto all'originale a 24 bit e' 1.5/255, invisibile su disegni a colori piatti,
ma il file pesa il 59% in meno). Le sorgenti originali sono 560x1024: se un domani
servisse piu' nitidezza sui tablet basta rigenerarle a 400 px di larghezza.
Il **dorso** e' un adattamento dell'immagine vecchia (era 220x339, proporzione
diversa): cornice e medaglione sono stati mantenuti in scala e sono state stirate
solo le due fasce di filigrana. Andrebbe ridisegnato a 560x1024 come le altre.

**Immagini delle carte.** Stanno in `res/drawable-nodpi/`, non in `res/drawable/`.
La cartella senza qualificatore vale mdpi, quindi Android ingrandirebbe ogni carta
alla densità dello schermo: su un telefono xxhdpi una carta da 220×410 px diventa
660×1230, cioè 3,3 MB in RAM invece di 360 KB. Con 41 carte in cache si arrivava a
oltre 130 MB e l'app veniva uccisa per OutOfMemory. **Non spostare questi file in
`drawable/`.**

**Cache bitmap.** `CardView` usa una `LruCache` limitata a 1/8 della heap, con
`inScaled = false`. `ZisApp` la svuota quando il sistema segnala poca memoria.

**Callback differiti.** Tutti i passaggi di turno sono `postDelayed` su un unico
`Handler`, ripuliti in `onDestroy()`. Senza questo, uscire dall'app mentre gioca il
Banco faceva partire il dialogo di fine mano su un'activity distrutta
(`BadTokenException`).

**Risorse caricate per nome.** Le carte vengono risolte a runtime con
`getIdentifier("card_X_Y")`. `res/raw/keep.xml` le protegge dallo shrinker nel caso
attivassi `shrinkResources`.

## Cosa manca ancora

- **Salvataggio dello stato**: se Android uccide il processo, la partita in corso è
  persa. Serve `onSaveInstanceState` (o un salvataggio in `SharedPreferences`).
- Nessun suono, nessuna statistica, nessuna modalità a 4 giocatori.
