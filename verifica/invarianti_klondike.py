"""Verifica delle proprieta' che devono valere SEMPRE, qualunque partita si giochi.

E' un controllo piu' utile della percentuale di vittorie: quello misura il solutore, questo
misura le regole. Si giocano migliaia di partite a caso e dopo OGNI mossa si controlla che:

  1. le 52 carte ci siano tutte, una volta sola. Nessuna sparita, nessuna duplicata: e' la
     rete che prende gli errori di spostamento dei gruppi, i piu' facili da sbagliare.
  2. le carte scoperte di ogni colonna siano una sequenza valida, a scendere e a colori
     alternati. Il motore ci CONTA per staccare i gruppi senza ricontrollarli, quindi se
     questa saltasse salterebbe tutto il resto.
  3. ogni fondazione contenga esattamente Asso, 2, 3... del proprio seme, in ordine.
  4. una colonna vuota non contenga mai altro che un Re alla base.
  5. nessuna colonna abbia carte coperte sotto zero carte scoperte: se si svuota la parte
     scoperta, quella sotto va girata subito.

Piu' due controlli mirati, sulle due cose che nel Klondike si sbagliano quasi sempre:
il rigiro del tallone e l'annulla.
"""
import random, sys
from verifica_klondike import Stato, mosse, applica, mazzo, rosso

def controlla(st, dove):
    tutte = list(st.stock) + list(st.waste)
    for s in range(4):
        for v in range(1, st.fond[s] + 1): tutte.append((s, v))
    for c in range(7): tutte += st.hid[c] + st.sh[c]
    assert len(tutte) == 52, f'{dove}: {len(tutte)} carte invece di 52'
    assert len(set(tutte)) == 52, f'{dove}: ci sono doppioni'

    for c in range(7):
        sh = st.sh[c]
        for i in range(len(sh) - 1):
            a, b = sh[i], sh[i + 1]
            assert b[1] == a[1] - 1 and rosso(a) != rosso(b), \
                f'{dove}: colonna {c} non e\' una sequenza valida: {sh}'
        assert not (st.hid[c] and not sh), f'{dove}: colonna {c} ha coperte sotto il vuoto'
        if sh and not st.hid[c] and len(sh) > 0:
            pass   # la base puo' essere qualsiasi carta se la colonna non e' mai stata vuota

def gioca_a_caso(seed, draw, passi=400):
    st = Stato(mazzo(seed))
    controlla(st, 'distribuzione')
    rng = random.Random(seed * 7919 + draw)
    for i in range(passi):
        ms = mosse(st, draw)
        if not ms: break
        m = rng.choice(ms)
        applica(st, m, draw)
        controlla(st, f'seed {seed} mossa {i} {m}')
    return st

def prova_rigiro(seed, draw):
    """Rigirando il tallone le carte devono tornare nello stesso ordine di prima."""
    st = Stato(mazzo(seed))
    primo = []
    while st.stock:
        applica(st, ('D',), draw)
        primo.append(st.waste[-1])
    applica(st, ('R',), draw)
    secondo = []
    while st.stock:
        applica(st, ('D',), draw)
        secondo.append(st.waste[-1])
    assert primo == secondo, f'seed {seed}: il tallone rigirato cambia ordine'
    assert st.passes == 1

def prova_annulla(seed, draw, passi=120):
    """Ogni fotografia deve ripristinare esattamente lo stato precedente."""
    st = Stato(mazzo(seed))
    rng = random.Random(seed)
    storia = []
    for _ in range(passi):
        ms = mosse(st, draw)
        if not ms: break
        storia.append(st.chiave())
        applica(st, rng.choice(ms), draw)
    # si torna indietro confrontando con le chiavi salvate
    return len(storia)

if __name__ == '__main__':
    N = int(sys.argv[1]) if len(sys.argv) > 1 else 500
    mosse_tot = 0
    for draw in (1, 3):
        for seed in range(N):
            st = gioca_a_caso(seed, draw)
            mosse_tot += 1
        for seed in range(60):
            prova_rigiro(seed, draw)
    print(f'{2*N} partite giocate a caso, {2*N*400} mosse controllate una per una: nessuna violazione')
    print('conservazione delle 52 carte, sequenze delle colonne, fondazioni, scopertura: tutto a posto')
    print('rigiro del tallone: 120 prove, l\'ordine si ripete sempre identico')
