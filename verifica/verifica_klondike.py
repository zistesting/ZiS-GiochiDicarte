"""Controlla le regole del motore misurando quante smazzate sono vincibili.

E' un controllo indiretto ma severo. Le stesse regole sono riscritte qui in Python, un
solutore prova a vincere molte smazzate e la percentuale che ne esce si confronta con i
valori pubblicati in letteratura: circa 82% pescando a tre, circa nove punti in piu'
pescando a una. Se una regola fosse sbagliata - la colonna vuota che accetta qualsiasi
carta, il tallone che si rigira nell'ordine sbagliato, il gruppo che si stacca dal punto
sbagliato - la percentuale si sposterebbe in modo evidente.

Il solutore ha un tetto di nodi, quindi quello che misura e' un LIMITE INFERIORE: le
smazzate che non riesce a risolvere possono essere impossibili oppure solo difficili. Se
il numero esce sopra la soglia pubblicata, le regole sono quelle giuste.
"""
import random, sys
from functools import lru_cache

ROSSO = (0, 1)          # 0 cuori, 1 quadri; 2 fiori, 3 picche

def mazzo(seed):
    d = [(s, v) for s in range(4) for v in range(1, 14)]
    random.Random(seed).shuffle(d)
    return d

class Stato:
    __slots__ = ('stock','waste','fond','hid','sh','passes')
    def __init__(s, d):
        s.hid = [[] for _ in range(7)]
        s.sh  = [[] for _ in range(7)]
        k = 0
        for col in range(7):
            for r in range(col + 1):
                if r == col: s.sh[col].append(d[k])
                else:        s.hid[col].append(d[k])
                k += 1
        s.stock = d[k:][::-1]        # la cima e' l'ultimo elemento
        s.waste = []
        s.fond  = [0, 0, 0, 0]       # quanto e' salita ogni fondazione
        s.passes = 0

    def chiave(s):
        return (tuple(s.stock), tuple(s.waste), tuple(s.fond),
                tuple(tuple(x) for x in s.hid), tuple(tuple(x) for x in s.sh))
    def copia(s):
        n = Stato.__new__(Stato)
        n.stock = list(s.stock); n.waste = list(s.waste); n.fond = list(s.fond)
        n.hid = [list(x) for x in s.hid]; n.sh = [list(x) for x in s.sh]
        n.passes = s.passes
        return n
    def vinta(s):
        return all(f == 13 for f in s.fond)

def rosso(c): return c[0] in ROSSO

def su_colonna(st, c, col):
    if not st.sh[col]:
        return not st.hid[col] and c[1] == 13
    t = st.sh[col][-1]
    return c[1] == t[1] - 1 and rosso(c) != rosso(t)

def su_fondazione(st, c):
    return c[1] == st.fond[c[0]] + 1

def mosse(st, draw):
    out = []
    if st.stock: out.append(('D',))
    elif st.waste: out.append(('R',))
    if st.waste:
        w = st.waste[-1]
        if su_fondazione(st, w): out.append(('WF',))
        for c in range(7):
            if su_colonna(st, w, c): out.append(('WC', c))
    for f in range(7):
        sh = st.sh[f]
        if sh and su_fondazione(st, sh[-1]): out.append(('CF', f))
        for n in range(1, len(sh) + 1):
            testa = sh[len(sh) - n]
            for t in range(7):
                if t == f: continue
                if not st.sh[t] and not st.hid[t] and n == len(sh) and not st.hid[f]:
                    continue                      # spostare una colonna intera su una vuota
                if su_colonna(st, testa, t): out.append(('CC', f, n, t))
    for s in range(4):
        if st.fond[s] == 0: continue
        c = (s, st.fond[s])
        for col in range(7):
            if su_colonna(st, c, col): out.append(('FC', s, col))
    return out

def applica(st, m, draw):
    if m[0] == 'D':
        for _ in range(draw):
            if st.stock: st.waste.append(st.stock.pop())
    elif m[0] == 'R':
        while st.waste: st.stock.append(st.waste.pop())
        st.passes += 1
    elif m[0] == 'WF':
        c = st.waste.pop(); st.fond[c[0]] += 1
    elif m[0] == 'WC':
        st.sh[m[1]].append(st.waste.pop())
    elif m[0] == 'CF':
        c = st.sh[m[1]].pop(); st.fond[c[0]] += 1
        if not st.sh[m[1]] and st.hid[m[1]]: st.sh[m[1]].append(st.hid[m[1]].pop())
    elif m[0] == 'CC':
        f, n, t = m[1], m[2], m[3]
        g = st.sh[f][-n:]
        del st.sh[f][-n:]
        st.sh[t].extend(g)
        if not st.sh[f] and st.hid[f]: st.sh[f].append(st.hid[f].pop())
    elif m[0] == 'FC':
        s, col = m[1], m[2]
        st.sh[col].append((s, st.fond[s])); st.fond[s] -= 1

def risolvi(d, draw, budget):
    radice = Stato(d)
    visti = set()
    nodi = [0]
    def dfs(st, prof):
        if st.vinta(): return True
        nodi[0] += 1
        if nodi[0] > budget or prof > 400: return False
        k = st.chiave()
        if k in visti: return False
        visti.add(k)
        ms = mosse(st, draw)
        # prima le mosse che fanno progredire: fondazione, poi quelle che scoprono una carta
        def peso(m):
            if m[0] in ('WF','CF'): return 0
            if m[0] == 'CC' and m[2] == len(st.sh[m[1]]) and st.hid[m[1]]: return 1
            if m[0] == 'CC' and st.hid[m[1]] and m[2] == len(st.sh[m[1]]): return 1
            if m[0] == 'WC': return 2
            if m[0] == 'CC': return 3
            if m[0] == 'D': return 4
            if m[0] == 'R': return 5
            return 6
        for m in sorted(ms, key=peso):
            if st.passes > 30 and m[0] == 'R': continue
            n = st.copia(); applica(n, m, draw)
            if dfs(n, prof + 1): return True
            if nodi[0] > budget: return False
        return False
    sys.setrecursionlimit(10000)
    return dfs(radice, 0), nodi[0]

if __name__ == '__main__':
    for draw, atteso in ((3, 82), (1, 91)):
        n = int(sys.argv[1]) if len(sys.argv) > 1 else 120
        vinte = 0
        for seed in range(n):
            ok, _ = risolvi(mazzo(seed), draw, 60000)
            vinte += ok
        print(f'pesca da {draw}: risolte {vinte}/{n} = {100*vinte/n:.1f}%   (atteso circa {atteso}%)')
