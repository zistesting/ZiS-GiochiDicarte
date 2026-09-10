# Regole per R8 - e il motivo per cui non ce n'e' bisogno di nessuna.
#
# minifyEnabled e shrinkResources sono accesi (vedi build.gradle) e questo file resta vuoto
# di proposito. R8 rompe le cose quando il codice raggiunge classi o membri per vie che
# l'analisi statica non vede. Qui non ce ne sono, e si puo' verificare una per una:
#
#  - NIENTE RIFLESSIONE. Zero Class.forName, zero java.lang.reflect, zero newInstance.
#
#  - NIENTE Resources.getIdentifier. Le carte si caricavano per nome, ora sono riferimenti
#    diretti a R.drawable in Decks.kt. E' il cambiamento che rende sicuro anche
#    shrinkResources: lo shrinker vede quali immagini sono usate, e non serve piu' il
#    res/raw/keep.xml che ci vorrebbe altrimenti.
#
#  - NIENTE SERIALIZZAZIONE AUTOMATICA. Nessun Gson o Moshi, nessun Serializable, nessun
#    Parcelable. I salvataggi passano da SavedGame, che scrive e rilegge un formato di testo
#    fatto a mano, campo per campo: nessun nome di classe o di campo finisce nei dati, quindi
#    l'offuscamento non li puo' rompere.
#
#  - LE ACTIVITY le tiene il manifest. AAPT2 genera da solo le regole per le classi
#    dichiarate li', e lo stesso vale per qualunque classe nominata in un layout.
#
#  - IL VIEW BINDING e' codice generato e chiamato direttamente (ActivityGameBinding.inflate
#    e compagnia), quindi R8 lo vede senza aiuto.
#
#  - CardView NON viene mai gonfiata da XML: la costruisce sempre il codice. Se un giorno
#    finisse in un layout servirebbe il costruttore (Context, AttributeSet), ma anche allora
#    la regola la genererebbe AAPT2, perche' la classe comparirebbe nel layout.
#
#  - LE LIBRERIE si portano dietro le proprie regole dentro gli AAR (consumer rules):
#    appcompat, material e constraintlayout non hanno bisogno di niente da qui.
#
# SE UN GIORNO SI ROMPE. Il sintomo tipico e' un ClassNotFoundException o un
# NoSuchMethodException che si vede SOLO nel build di release. In quel caso il primo
# sospetto non e' questo file: e' la modalita' aggressiva di R8, vedi gradle.properties.
# Le regole vanno scritte qui solo se si aggiunge una delle cose elencate sopra.
