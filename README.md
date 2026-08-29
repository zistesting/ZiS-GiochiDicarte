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
