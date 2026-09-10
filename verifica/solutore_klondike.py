"""Solutore, per misurare quante smazzate sono vincibili e confrontarlo con la letteratura.

Il primo tentativo, una ricerca in profondita' con memoria delle posizioni gia' viste,
risolveva il 36%: troppo poco per dire qualcosa, perche' misurava il solutore e non le regole.
Tre cambiamenti lo rendono utile.

1. GIOCO AUTOMATICO SICURO. Una carta che non potra' mai piu' servire a nessuno sale in
   fondazione senza che la ricerca ci pensi. La condizione classica: assi e due sempre; per
   il resto, una carta di valore v e' sicura se le due fondazioni del colore opposto sono
   arrivate almeno a v-1 e l'altra dello stesso colore almeno a v-2. Cosi' nessuna carta che
   servirebbe come appoggio viene sepolta, e l'albero si sgonfia.

2. NIENTE RITORNI DALLA FONDAZIONE. Riportare giu' una carta gia' salita e' legale e resta
   nel motore, perche' a volte serve davvero, ma nella ricerca moltiplica i rami. Le misure
   pubblicate dicono che toglierlo sposta la percentuale di circa quattro decimi di punto.

3. MOSSE INUTILI SCARTATE. Spostare un gruppo da una colonna all'altra senza scoprire niente
   e senza svuotare la colonna non cambia la posizione in modo utile.
"""
import sys
from verifica_klondike import Stato, mazzo, rosso, su_colonna, su_fondazione

def sicura(fond, c):
    s, v = c
    if v <= 2: return True
    opp = (2, 3) if s in (0, 1) else (0, 1)
    stesso = 1 - s if s in (0, 1) else 5 - s
    return fond[opp[0]] >= v - 1 and fond[opp[1]] >= v - 1 and fond[stesso] >= v - 2

def autogioco(st):
    mosso = True
    while mosso:
        mosso = False
        if st.waste:
            c = st.waste[-1]
            if su_fondazione(st, c) and sicura(st.fond, c):
                st.waste.pop(); st.fond[c[0]] += 1; mosso = True; continue
        for col in range(7):
            if not st.sh[col]: continue
            c = st.sh[col][-1]
            if su_fondazione(st, c) and sicura(st.fond, c):
                st.sh[col].pop(); st.fond[c[0]] += 1
                if not st.sh[col] and st.hid[col]: st.sh[col].append(st.hid[col].pop())
                mosso = True; break

def mosse_utili(st, draw):
    out = []
    if st.waste:
        w = st.waste[-1]
        if su_fondazione(st, w): out.append((0, ('WF',)))
        for c in range(7):
            if su_colonna(st, w, c): out.append((2, ('WC', c)))
    for f in range(7):
        sh = st.sh[f]
        if sh and su_fondazione(st, sh[-1]): out.append((0, ('CF', f)))
        for n in range(1, len(sh) + 1):
            testa = sh[len(sh) - n]
            tutto = (n == len(sh))
            scopre = tutto and bool(st.hid[f])
            svuota = tutto and not st.hid[f]
            for t in range(7):
                if t == f: continue
                if not st.sh[t] and not st.hid[t]:
                    if svuota: continue            # sposta la colonna intera da un vuoto all'altro
                    out.append((3, ('CC', f, n, t)))
                elif su_colonna(st, testa, t):
                    if not scopre and not svuota and n == len(sh) and not st.hid[f]:
                        continue
                    if not scopre and n < len(sh):
                        # spostare solo una parte serve quasi sempre a liberare la carta
                        # sotto: se non scopre niente, e' un giro a vuoto
                        continue
                    out.append((1 if scopre else 3, ('CC', f, n, t)))
    if st.stock: out.append((4, ('D',)))
    elif st.waste: out.append((5, ('R',)))
    out.sort(key=lambda x: x[0])
    return [m for _, m in out]

def risolvi(d, draw, budget):
    from verifica_klondike import applica
    radice = Stato(d); autogioco(radice)
    visti = set(); nodi = [0]
    sys.setrecursionlimit(20000)
    def dfs(st, prof):
        if st.vinta(): return True
        nodi[0] += 1
        if nodi[0] > budget or prof > 300: return False
        k = st.chiave()
        if k in visti: return False
        visti.add(k)
        for m in mosse_utili(st, draw):
            if m[0] == 'R' and st.passes > 12: continue
            n = st.copia(); applica(n, m, draw); autogioco(n)
            if dfs(n, prof + 1): return True
            if nodi[0] > budget: return False
        return False
    return dfs(radice, 0), nodi[0]

if __name__ == '__main__':
    n = int(sys.argv[1]); budget = int(sys.argv[2])
    for draw, atteso in ((1, 91), (3, 82)):
        vinte = 0
        for seed in range(n):
            ok, _ = risolvi(mazzo(seed), draw, budget)
            vinte += ok
        print(f'pesca da {draw}: risolte {vinte}/{n} = {100*vinte/n:.0f}%   (in letteratura circa {atteso}%)')
