#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Stráž auditov — audity sa smú len dopĺňať (B17, 2. 10. 2026; časť 3, FD-6 K-4 „audit potvrdené tichom je
append-only“, K-6 „append-only ako blok“; posúdenie časti 3, C2).

Do 2. 10. stráž chránila len zápisník a návrhy (ng-zapisnik-guard.sh r. 12, 35) a audity výslovne vynechávala.
20. 9. sa z auditu tichých potvrdení stratili 3 riadky, ktoré už mali verdikt; 30. 9. 23:30 partner sám vymazal
5 skúšobných riadkov zo záznamu kotvy — mal na to dôvod, ale kód tomu nebránil. Prehrávka 7 dní (2. 10.): zo
38 929 príkazov PS aj kolegov by stráž zastavila 7, všetky partnerove úpravy či mazania auditov (21., 23., 29., 30. 9.).

Volá ho ng-straz-beh.sh (ng_audit_straz) z oboch stráží zápisníka, len keď vstup menuje audit alebo príkaz maže či
presúva v priečinku stroja alebo projektu. Číta jeden JSON vstupu háčika zo stdin; keď ide o zápis, zmazanie,
presun či prepis auditu, vypíše rozhodnutie deny (JSON pre Claude Code), inak nič. Chyba = nič (pustiť), rovnako
ako stráž zápisníka.

Audit = súbor v priečinku stroja projektu (<domov>/projects/<projekt>/.aigrip/ng/), v ~/.aigrip-ng/ alebo v kópii
stôp kolegov (/var/lib/aig-ng/stopy/<kolega>/) s menom audit-*.jsonl · *-verdikty.jsonl · kotva-audit.jsonl ·
audit-dennik.md · audit-historia.md · audit-historia-stav.json · zapisnik-guard.log · hook-stopy.jsonl ·
beh-strazcov.jsonl. Kópia v /tmp či v skúšobnom priečinku audit nie je. Pracovné súbory auditu (audit-vzorka.txt,
audit-posudok.log, zámky) chránené nie sú — prepisuje ich beh, nie sú to dôkazy.

Čo je zápis (Bash): presmerovanie > >> a tee do auditu, sed/perl/awk na mieste, dd of=, editor, cp/mv/install/ln/
rsync/scp na audit (aj kópia priečinka, ktorá by audit prepísala), mv auditu preč, rm/unlink/shred/truncate auditu
(aj cez * a rm -r priečinka, v ktorom audit je), find … -delete či -exec rm nad priečinkom s auditom (podľa -name),
rsync --delete (aj --del) do neho, git clean -x / git stash -a v repozitári s auditom, git checkout/restore/rm/mv auditu,
Python so zápisom do auditu alebo jeho zmazaním, a to isté vnútri bash -c / sh -c / eval. Od 2. 10. večer (overovateľ
B17, P4: 12 doslovných obchádzok) aj: zložené zátvorky (mv audit{,.bak}, rm ng/{audit,x}), echo/printf/ls/find … |
xargs rm|mv (aj -I{}), gzip/bzip2/xz a spol. bez -k (pôvodný súbor zmizne), sort -o do auditu, tar -x s menovaným
auditom, perl/node/ruby/php -e so zmazaním, presunom či zápisom auditu, find -exec sh -c 'rm …', premenná cyklu
for … in <audit>, príkaz za do/then/else. Nepokrýva (vidí to len kontrola straty — a to len riadky, ktoré videla v
predošlom hodinovom behu): skript v súbore, cestu skladanú v kóde či z výstupu iného príkazu (cat zoznam | xargs rm,
for f in $(…)), rozbalenie celého archívu (tar -x, unzip) do priečinka s auditom, git reset --hard / checkout -- . a
zápis mimo nástrojov; úplný zoznam známych medzier je v README B17 (Riziká) — nie je to zoznam všetkého, čo stráž nevidí.
Zmazanie či presun CELÉHO projektu (rm -r, mv koreňa projektu) stráž pustí, keď je každý jeho audit celý v gite AJ na
spoločnom mieste (sledovaný, bez neuložených riadkov a v tej istej podobe na vetve, ktorú klon sleduje) — riadky ostanú
v spoločnom repozitári. Kotva sa na zmazanie projektu nepýta (overovateľ B17, N3: predtým stačil lokálny commit, teda
aj jediná kópia v repozitári bez spoločného miesta). git rm --cached sledovaného auditu je zákaz: súbor ostane len
u teba, po odoslaní ho pull zmaže v ostatných klonoch (N8). Od 3. 10. (N5) aj: mv/cp -t a --target-directory, pushd,
env -C, tar --remove-files, zip -m, gzip a spol. -r priečinka, rmSync/rmtree/remove_tree/FileUtils priečinka v kóde
-e a cesta cez symbolický odkaz. Nový audit, ktorý ešte neexistuje, smie vzniknúť prázdny (: > audit pri zakladaní
projektu, N6); s obsahom je to pripísanie mimo jedinej cesty. Príkazy rozkladá ten istý
určovateľ ako pri pravidle 2 a pri zápisníku (blok ng-zapis-v-projekte z ng-kontext-straz.sh) — úvodzovky, telá
heredoc a > v kóde zápisom nie sú. Čítanie (cat, grep, tail, jq, cp auditu do /tmp) prejde. Zmazanie súboru, ktorý
v čase príkazu ešte neexistuje (vznikne až tým istým príkazom, napr. kópia z iného projektu), dôkazom nie je a prejde.
Výnimka ako pri zápisníku: keď jediná cesta zápisu tichých potvrdení (ng-audit.py) z izby nie je dostupná, holé
pripísanie (>>, tee -a) do audit-ticho.jsonl prejde.

Skúšobné premenné (len pre vstup bez hook_event_name — červený test; živý vstup od Claude Code ich ignoruje, aby sa
stráž nedala vypnúť cez „env“ v nastaveniach): NG_AUDIT_PREDPONA (koreň pieskoviska pred /home… a /var/lib…),
NG_AUDIT_ZAPISOVAC (cesta, ktorej čitateľnosť znamená „jediná cesta zápisu je dostupná“), NG_AUDIT_BLOK (určovateľ).
"""
import os, re, sys, json, glob, fnmatch

TU = os.path.dirname(os.path.abspath(__file__))
MENO = re.compile(r'^(?:audit-[^/]*\.jsonl|[^/]*-verdikty\.jsonl|kotva-audit\.jsonl|audit-dennik\.md|audit-historia\.md|'
                  r'audit-historia-stav\.json|zapisnik-guard\.log|hook-stopy\.jsonl|beh-strazcov\.jsonl)$')
ZAPIS_AUDITU = ('/opt/aigrip-ng/scripts/ng-audit.py', '/home/pstrocka/projects/aigrip/.aigrip/scripts/ng-audit.py')
LOG = os.environ.get('NG_ZAPISNIK_LOG', '/home/pstrocka/projects/aigrip/.aigrip/ng/zapisnik-guard.log')
KOREN = re.compile('^$'); ZAPISOVAC = ZAPIS_AUDITU; BLOKY = []; PROJEKT = re.compile('^$')
XARGS_ARG = {'-I', '-n', '-P', '-L', '-s', '-d', '-E', '-a', '--arg-file', '--delimiter', '--max-args', '--max-procs',
             '--max-lines', '--max-chars'}
PREDPONY = ('xargs', 'do', 'then', 'else', 'elif', '{', '!')         # slovo pred príkazom (cyklus, podmienka, xargs)
BALIC = ('gzip', 'gunzip', 'bzip2', 'bunzip2', 'xz', 'unxz', 'lzma', 'lzip', 'pigz', 'pbzip2', 'zstd', 'lz4', 'compress')
# interpreter -e (perl, node, ruby, php …): voľby, za ktorými ide kód; volania, ktoré audit zmažú, presunú či prepíšu,
# pripíšu doň, alebo ho otvoria s režimom (cieľ = reťazcový literál priamo v argumentoch volania, nie vo vnorenom volaní)
KOD_VOLBY = {'perl': ('-e', '-E'), 'node': ('-e', '--eval', '-p', '--print'), 'nodejs': ('-e', '--eval', '-p', '--print'),
             'deno': ('eval',), 'bun': ('-e', '--eval'), 'ruby': ('-e',), 'php': ('-r',)}
KOD_MAZ = re.compile(r'(?<![\w$])(unlink|unlinkSync|rm|rmSync|rm_f|rm_rf|rm_r|rmdir|rmdirSync|rmtree|remove_tree|rename|renameSync|mv|truncate|'
                     r'truncateSync|ftruncate|writeFile|writeFileSync|write|file_put_contents|copyFile|copyFileSync|copy|cp|'
                     r'createWriteStream|delete|appendFile|appendFileSync|open|openSync|fopen)(?![\w$])')
KOD_PRIECINOK = ('rm', 'rmSync', 'rm_rf', 'rm_r', 'rmtree', 'remove_tree', 'rename', 'renameSync', 'mv')   # zdroj = celý priečinok


def nastav(v):
    """Koreň chránených miest a zdroje — skúšobné premenné len pre vstup bez hook_event_name."""
    global KOREN, ZAPISOVAC, BLOKY, PROJEKT
    ziv = bool(v.get('hook_event_name'))
    pred = '' if ziv else os.environ.get('NG_AUDIT_PREDPONA', '').rstrip('/')
    KOREN = re.compile('^' + re.escape(pred) + r'(?:/home/[^/]+/projects/[^/]+/\.aigrip/ng|/home/[^/]+/\.aigrip-ng|'
                       r'/var/lib/aig-ng/stopy/[^/]+)/(?P<meno>[^/]+)$')
    PROJEKT = re.compile('^' + re.escape(pred) + r'/home/[^/]+/projects/[^/]+$')
    ZAPISOVAC = (os.environ['NG_AUDIT_ZAPISOVAC'],) if (not ziv and os.environ.get('NG_AUDIT_ZAPISOVAC')) else ZAPIS_AUDITU
    BLOKY = ([os.environ['NG_AUDIT_BLOK']] if (not ziv and os.environ.get('NG_AUDIT_BLOK')) else []) + [
        os.path.join(TU, 'ng-kontext-straz.sh'), os.path.normpath(os.path.join(TU, '..', 'hooks', 'ng-kontext-straz.sh')),
        '/opt/aigrip-ng/hooks/ng-kontext-straz.sh']


def chraneny(a, cez_odkaz=True):
    m = KOREN.match(a or '')
    if m:
        return bool(MENO.match(m.group('meno')))
    # cesta cez symbolický odkaz (/tmp/odkaz/kotva-audit.jsonl, /tmp/s → audit): rozhoduje skutočné miesto súboru
    # (overovateľ B17, N5) — kópia v /tmp audit ostáva neauditom; v zoznamoch zo glob (pod) len pri mene auditu
    if not a or not cez_odkaz or not os.path.lexists(a):
        return False
    r = os.path.realpath(a)
    m = KOREN.match(r) if r != a else None
    return bool(m) and bool(MENO.match(m.group('meno')))


def pod(x):
    """Audity, ktoré ležia v priečinku x alebo pod ním — len na známych miestach (bez prechádzania celého stromu)."""
    if not x or not os.path.isdir(x):
        return []
    x = x.rstrip('/') or '/'
    von = []
    for p in ('*', 'ng/*', '.aigrip/ng/*', '*/.aigrip/ng/*', '.aigrip-ng/*', 'projects/*/.aigrip/ng/*', '*/*', 'stopy/*/*'):
        for a in glob.glob(os.path.join(x, p)):
            if chraneny(a, bool(MENO.match(os.path.basename(a)))) and os.path.isfile(a) and a not in von:
                von.append(a)
    return von


def _git(q, *a):
    """Návratový kód gitu v priečinku auditu q (len čítanie — bez obnovy indexu, súbežná synchronizácia), chyba = 128."""
    import subprocess                                    # len pri zmazaní celého projektu a git rm --cached (~5 ms)
    try:
        return subprocess.run(['git', '--no-optional-locks', '-C', os.path.dirname(q)] + list(a) + ['--', os.path.basename(q)],
                              capture_output=True, timeout=2).returncode
    except Exception:
        return 128


def sledovany(q):
    return _git(q, 'ls-files', '--error-unmatch') == 0


def v_gite_cely(q):
    """Audit je celý v histórii projektu aj na spoločnom mieste: git ho sleduje, nemá neuložené riadky a vetva, ktorú klon
    sleduje (@{upstream}), ho má v tej istej podobe — zmazaním celého projektu sa nestratí. Overovateľ B17 (N3): predtým
    stačil lokálny commit; v repozitári bez spoločného miesta (alebo s neodoslaným commitom) by zmazanie projektu zmazalo
    jedinú kópiu. Projekt bez vetvy na spoločnom mieste = nie (git skončí chybou)."""
    return sledovany(q) and _git(q, 'diff', '--quiet', 'HEAD') == 0 and _git(q, 'diff', '--quiet', '@{upstream}') == 0


def v_projekte(p):
    """Audity v priečinku p, ktoré by zmazanie či presun p naozaj stratilo. Celý projekt (koreň): len tie, ktoré nie sú
    celé v gite (overovateľ B17, P7: rm -rf ~/projects/<projekt> so sledovaným, aj prázdnym auditom bol falošný zákaz)."""
    q = pod(p)
    return [x for x in q if not v_gite_cely(x)] if PROJEKT.match(p.rstrip('/')) else q


def zatvorky(x, h=0):
    """Zložené zátvorky ako v shelli: a{,.bak} → a a.bak; ng/{a,b} → ng/a ng/b. Len slovo, ktoré vyzerá ako cesta
    (bez medzier, úvodzoviek, zátvoriek kódu a „=“), nie ${…} a nie {} z find/xargs."""
    m = re.search(r'\{([^{}]*,[^{}]*)\}', x)
    if not m or h > 4 or len(x) > 300 or re.search(r'[\s"\'()=;]', x) or x[:m.start()].endswith('$') \
            or not re.search(r'[/.]', x):
        return [x]
    return [y for c in m.group(1).split(',') for y in zatvorky(x[:m.start()] + c + x[m.end():], h + 1)][:64]


def literaly_volania(kod, i):
    """Reťazcové literály v argumentoch volania od pozície i (hneď za menom funkcie) — len na prvej úrovni, nie vo
    vnorených volaniach: fs.writeFileSync('/tmp/x', fs.readFileSync('audit')) → ['/tmp/x']. Bez zátvoriek (perl
    unlink "a", "b") po koniec príkazu."""
    n = len(kod)
    while i < n and kod[i] in ' \t':
        i += 1
    if i < n and kod[i] == '(':
        i += 1
    elif i < n and kod[i] not in '"\'':
        return []                                       # nie je to volanie s argumentom (napr. slovo v texte)
    hl, von = 0, []
    while i < n:
        ch = kod[i]
        if ch in '"\'':
            k = kod.find(ch, i + 1)
            if k < 0:
                break
            if hl == 0:
                von.append(kod[i + 1:k])
            i = k + 1; continue
        if ch in '([{':
            hl += 1
        elif ch in ')]}':
            if hl == 0:
                break
            hl -= 1
        elif ch in ';\n' and hl == 0:
            break
        i += 1
    return von


def argumenty(kod, i, koniec=False):
    """Argumenty volania od pozície i (hneď za „(“) ako text — so vnorenými zátvorkami a reťazcami; None = nedočítané.
    koniec=True → (argumenty, pozícia zatvárajúcej zátvorky)."""
    von, hl, j, z, n = [], 0, i, i, len(kod)
    while j < n:
        ch = kod[j]
        if ch in '"\'':
            k = kod.find(ch, j + 1)
            if k < 0:
                return None
            j = k + 1; continue
        if ch in '([{':
            hl += 1
        elif ch in ')]}':
            if hl == 0:
                von.append(kod[z:j].strip())
                return ([x for x in von if x], j) if koniec else [x for x in von if x]
            hl -= 1
        elif ch == ',' and hl == 0:
            von.append(kod[z:j].strip()); z = j + 1
        j += 1
    return None


def _najvyssie(e, znak):
    """Pozície znaku na najvyššej úrovni výrazu (mimo zátvoriek a reťazcov)."""
    von, hl, j, n = [], 0, 0, len(e)
    while j < n:
        ch = e[j]
        if ch in '"\'':
            k = e.find(ch, j + 1)
            if k < 0:
                return None
            j = k + 1; continue
        if ch in '([{':
            hl += 1
        elif ch in ')]}':
            hl -= 1
        elif ch == znak and hl == 0:
            von.append(j)
        j += 1
    return von


def py_cesta(e, kod, dom, h=0):
    """Cesta z výrazu Pythonu: literál, premenná s priradením, a + b, os.path.join(…), os.path.expanduser('~…'). Čo sa
    takto rozriešiť nedá (výpočet, premenná cyklu, f-reťazec), je None — rozhoduje potom len to, čo vidí určovateľ."""
    e = (e or '').strip()
    if h > 5 or not e:
        return None
    m = re.fullmatch(r"""[rbRB]?(['"])([^'"\\]*)\1""", e)
    if m:
        return m.group(2)
    if re.fullmatch(r'[A-Za-z_]\w*', e):
        a = re.findall(r'(?m)(?:^|;)[ \t]*' + e + r'[ \t]*=(?!=)[ \t]*([^\n;]+)', kod)
        return py_cesta(a[-1], kod, dom, h + 1) if a else None
    plus = _najvyssie(e, '+')
    if plus:
        casti = [e[i + 1:j] for i, j in zip([-1] + plus, plus + [len(e)])]
        r = [py_cesta(x, kod, dom, h + 1) for x in casti]
        return ''.join(r) if all(x is not None for x in r) else None
    m = re.match(r'os\.path\.(join|expanduser)\s*\(', e)
    if m:
        r = argumenty(e, m.end(), koniec=True)
        if r is None or not r[0] or r[1] != len(e) - 1:     # celý výraz je to jedno volanie
            return None
        r = [py_cesta(x, kod, dom, h + 1) for x in r[0]]
        if any(x is None for x in r):
            return None
        if m.group(1) == 'expanduser':
            return dom + r[0][1:] if r[0].startswith('~') else r[0]
        return os.path.join(*r)
    return None


def nacitaj_blok():
    Z, K = '# --- ng-zapis-v-projekte:zaciatok ---', '# --- ng-zapis-v-projekte:koniec ---'
    for b in BLOKY:
        try:
            t = open(b, encoding='utf-8').read()
        except OSError:
            continue
        if Z in t and K in t:
            G = {}
            exec(compile(t[t.index(Z):t.index(K)], b, 'exec'), G)
            return G
    return None


def zapisovac_dostupny():
    return any(os.access(p, os.R_OK) for p in ZAPISOVAC)


def stroj_skripty():
    return '/opt/aigrip-ng/scripts' if os.path.isdir('/opt/aigrip-ng/scripts') else '/home/pstrocka/projects/aigrip/.aigrip/scripts'


def vysvetli(meno):
    S = stroj_skripty()
    if meno == 'audit-ticho.jsonl':
        return (f'Audit tichých potvrdení sa smie len dopĺňať a píše ho jedine ng-audit.py: python3 {S}/ng-audit.py zapis '
                '"<čo si bral ako potvrdené tichom>" "<prečo je to malá vec>" "<projekt>".')
    if meno == 'audit-verdikty.jsonl':
        return 'Verdikty zrkadla zapisuje len nezávislý beh (ng-audit.py verdikt) a smú sa len dopĺňať.'
    if meno.endswith('-verdikty.jsonl'):
        return 'Verdikty nezávislého overenia zapisuje len overovací beh a smú sa len dopĺňať.'
    if meno == 'kotva-audit.jsonl':
        return 'Záznam kotvy píše len kotva sama a smie sa len dopĺňať.'
    if meno == 'audit-prisiel.jsonl':
        return 'Pamäť príchodu riadkov auditu dopĺňa len ng-audit.py — podľa nej zrkadlo vie, odkedy riadok čaká.'
    if meno == 'audit-dennik.md':
        return 'Denník tichých potvrdení dopĺňa len ranný beh (ng-audit.py dennik) — čo doň raz prišlo, nezmizne.'
    if meno in ('audit-historia.md', 'audit-historia-stav.json'):
        return 'Históriu auditov dopĺňa len ng-audit-historia.py — podľa nej sa kontroluje, či z auditov niečo nezmizlo.'
    if meno == 'zapisnik-guard.log':
        return 'Záznam zásahov stráže zápisníka píše len stráž sama a smie sa len dopĺňať.'
    if meno in ('hook-stopy.jsonl', 'beh-strazcov.jsonl'):
        return 'Stopu behu háčikov píšu len háčiky (orezáva ju stroj) — je to dôkaz, že stráže naozaj bežali.'
    return f'Audit {meno} sa smie len dopĺňať a píše ho len stroj.'


def odmietni(nastroj, a, co):
    meno = os.path.basename(a)
    r = (f'{vysvetli(meno)} Tento príkaz by ho {co} ({a}). Riadok auditu sa nemaže ani neprepisuje — ani skúšobný či '
         'chybný; ak skresľuje meranie, oprav meranie, nie audit. Čítať audit môžeš (cat, grep, tail, jq, kópia do /tmp).'
         + (' Ak zakladáš projekt z kópie iného, audit doň nekopíruj (tar --exclude, cp bez neho) — nemaž ho až potom.'
            if co.startswith(('zmazal', 'presunul')) else '')
         + ' (Stráž auditov, časť 3: audity sa smú len dopĺňať.)')
    try:
        import datetime
        with open(LOG, 'a', encoding='utf-8') as f:
            f.write(f'{datetime.datetime.now():%Y-%m-%dT%H:%M:%S}\t{nastroj}\taudit: {co} {a}\n')
    except OSError:
        pass
    sys.stdout.write(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': 'deny',
                                                         'permissionDecisionReason': r}}, ensure_ascii=False))
    sys.exit(0)


class Posudok:
    """Rozbor príkazu Bash nad určovateľom zápisu (ten istý blok ako pravidlo 2 a stráž zápisníka)."""

    def __init__(self, G, root, dom):
        self.G, self.root, self.dom, self.env = G, root, dom, {}
        self.OBAL = G.get('_OBAL') or {}
        self.cmd = ''
        self.zoznamy = {}                 # premenná cyklu for → slová zoznamu
        self.vystup = []                  # čo vypísal predošlý príkaz (echo/printf/ls/find) — vstup pre xargs
        self.xargs = None                 # None = bez xargs; inak náhradný reťazec (-I) alebo ''
        self.zasobnik = []                # pushd/popd (overovateľ B17, N5)
        self.chdir = None                 # env -C <priečinok> — platí len pre ten jeden príkaz
        self.kod_akt = ''                 # posudzovaný kód Pythonu (mená auditov, ktoré menuje)

    def rozvin(self, x):
        x = re.sub(r'\$\{?([A-Za-z_]\w*)\}?', lambda m: self.env.get(m.group(1), m.group(0)), x)
        for k, h in (('CLAUDE_PROJECT_DIR', self.root), ('HOME', self.dom)):
            x = x.replace('${' + k + '}', h).replace('$' + k, h)
        return x

    def cesty(self, p, cwd, h=0):
        """Absolútne cesty slova príkazu (s rozvinutým ~ a $PWD; s * ? [ ako zoznam zhôd; premenná cyklu for = každé
        slovo zoznamu); neistá cesta -> []."""
        if not p:
            return []
        p = self.rozvin(str(p).strip())
        m = re.search(r'\$\{?([A-Za-z_]\w*)\}?', p)
        if m and m.group(1) in self.zoznamy and h < 3:
            return [q for v in self.zoznamy[m.group(1)][:50] for q in self.cesty(p[:m.start()] + v + p[m.end():], cwd, h + 1)]
        p = p.replace('${PWD}', cwd if cwd != '?' else '$PWD').replace('$PWD', cwd if cwd != '?' else '$PWD')
        if p == '~' or p.startswith('~/'):
            p = self.dom + p[1:]
        if '$' in p or '`' in p or p.startswith('/dev/'):
            return []
        if not p.startswith('/') and cwd == '?':
            return []
        a = os.path.normpath(p if p.startswith('/') else os.path.join(cwd or self.root, p))
        if any(c in a for c in '*?['):
            return sorted(glob.glob(a))
        return [a]

    def jadro(self, w):
        self.xargs = self.chdir = None
        while w and (re.match(r'^\w+=', w[0]) or w[0] in self.OBAL or w[0] in PREDPONY):
            if w[0] == 'env':                         # env -C priečinok príkaz — príkaz beží v inom priečinku (N5)
                w = w[1:]
                while w and ((w[0].startswith('-') and w[0] != '-') or re.match(r'^\w+=', w[0])):
                    x = w[0]
                    if x in ('-C', '--chdir', '-u', '--unset', '-S', '--split-string') and len(w) > 1:
                        if x in ('-C', '--chdir'):
                            self.chdir = w[1]
                        w = w[2:]; continue
                    if x.startswith('--chdir='):
                        self.chdir = x.split('=', 1)[1]
                    elif re.match(r'^-[iv0]*C.', x):
                        self.chdir = x[x.index('C') + 1:]
                    w = w[1:]
                continue
            if w[0] == 'xargs':
                self.xargs = ''; w = w[1:]
                while w and w[0].startswith('-') and w[0] != '-':
                    if w[0] == '-I' and len(w) > 1:
                        self.xargs = w[1]
                    elif w[0].startswith('-I') and len(w[0]) > 2:
                        self.xargs = w[0][2:]
                    elif w[0].startswith(('-i', '--replace')):
                        self.xargs = w[0].split('=', 1)[1] if '=' in w[0] else (w[0][2:] if w[0].startswith('-i') and len(w[0]) > 2 else '{}')
                    w = w[2:] if w[0] in XARGS_ARG else w[1:]
                continue
            o = self.OBAL.get(w[0], set()); tm = w[0] == 'timeout'; w = w[1:]
            while w and w[0].startswith('-') and w[0] != '-':
                w = w[2:] if w[0] in o else w[1:]
            if tm and w:
                w = w[1:]
        return w

    def zo_vstupu(self, w, rek):
        """xargs: položky predošlého výstupu doplnené do príkazu (namiesto -I reťazca, inak na koniec)."""
        vs = []
        for v in self.vystup:
            if isinstance(v, tuple):                          # find bez -delete: čo by vypísal (podľa -name/-path)
                _, fa, kor = v
                vs += ([kor] if chraneny(kor) else []) + [q for q in pod(kor) if self.find_ohrozene(fa, kor, q, rek)]
            else:
                vs.append(v)
        if not vs:
            return w
        if self.xargs and any(self.xargs in x for x in w[1:]):
            return [w[0]] + [y for x in w[1:] for y in ([x.replace(self.xargs, v) for v in vs] if self.xargs in x else [x])]
        return w + vs

    def vypis(self, c0, a, arg, cwd):
        """Čo príkaz vypíše na výstup (pre xargs za ním): cesty auditov a priečinkov z echo/printf/ls, find bez -delete."""
        if c0 in ('echo', 'printf'):
            return [p for x in arg for p in self.cesty(x, cwd) if chraneny(p) or os.path.isdir(p)]
        if c0 == 'ls':
            if not arg:                                       # ls v priečinku príkazu (po cd $X neznámom: nič)
                return [] if cwd == '?' else [q for q in glob.glob(os.path.join(cwd or self.root, '*')) if chraneny(q)]
            return [p for x in arg for p in self.cesty(x, cwd) if chraneny(p)]
        if c0 == 'find':
            return [('find', a, p) for x in (self.korene_find(a) or ['.']) for p in self.cesty(x, cwd)]
        return []

    @staticmethod
    def korene_find(a):
        k = []
        for x in a:
            if x.startswith('-') or x in ('(', '!', ')'):
                break
            k.append(x)
        return k

    def kod_e(self, c0, a, cwd):
        """perl/node/ruby/php -e: zmazanie, presun či prepis auditu literálom cesty v tom istom volaní (overovateľ B17, P4).
        Čítanie (readFileSync, open na čítanie, print) prejde, aj keď audit menuje."""
        for i, x in enumerate(a[:-1]):
            if not (x in KOD_VOLBY[c0] or (c0 == 'perl' and re.match(r'^-[a-zA-Z]*[eE]$', x))):
                continue
            kod = a[i + 1]
            for m in KOD_MAZ.finditer(kod):
                f, lit = m.group(1), literaly_volania(kod, m.end())
                if f in ('write', 'delete') and not kod[:m.start()].endswith('File.'):
                    continue                                  # File.write / File.delete (ruby), nie stdout.write či map.delete
                ciele, zapis = [], False
                if f in ('open', 'openSync', 'fopen'):
                    # režim: perl predpona (>x, >>x, +<x; trojargumentový '>' a cesta za ním) alebo režim C ('w', 'ab', 'r+')
                    mod = ciel = None
                    for k, x in enumerate(lit):
                        r = re.match(r'^\s*(\+?>>?|\+<)\s*(.*)$', x, re.S)
                        if r:
                            mod, ciel = r.group(1), r.group(2) or (lit[k + 1] if k + 1 < len(lit) else ''); break
                        if re.match(r'^[rwax]\+?[bt]?\+?$', x):
                            mod, ciel = x, next((y for y in lit if y is not x), ''); break
                    if not mod or re.match(r'^r[bt]?$', mod):
                        continue                              # čítanie
                    zapis = '>>' in mod or mod.startswith('a')
                    ciele = [ciel]
                elif f in ('appendFile', 'appendFileSync'):
                    ciele, zapis = lit[:1], True
                elif f in ('copyFile', 'copyFileSync', 'copy', 'cp'):
                    ciele = lit[1:2]                           # prepíše sa cieľ kópie
                elif f in ('write', 'writeFile', 'writeFileSync', 'file_put_contents', 'createWriteStream', 'truncate',
                           'truncateSync', 'ftruncate'):
                    ciele = lit[:1]
                else:                                          # unlink, rm…, rename/mv (zdroj zmizne, cieľ sa prepíše)
                    ciele = lit[:2] if f in ('rename', 'renameSync', 'mv') else lit
                for k, c in enumerate(ciele):
                    for q in self.cesty(c, cwd):
                        if chraneny(q):
                            if zapis:
                                self.pripisal(q)                 # pripísanie mimo jedinej cesty (ako >>)
                            elif os.path.lexists(q):             # audit, ktorý ešte nie je, sa stratiť nemôže
                                odmietni('Bash', q, f'zmazal, presunul alebo prepísal ({c0} -e)')
                        elif f in KOD_PRIECINOK and (k == 0 or f not in ('rename', 'renameSync', 'mv')) and os.path.isdir(q):
                            # celý priečinok (rmSync(…, {recursive}), rmtree, rename priečinka) — audity v ňom (N5)
                            for x in v_projekte(q):
                                odmietni('Bash', x, f'zmazal alebo presunul (priečinok {q}, {c0} -e)')

    def kam(self, t, cwd):
        """Priečinok po cd/pushd/env -C t (neistý = '?')."""
        t = self.rozvin(self.dom + t[1:] if t.startswith('~') else t)
        return '?' if ('$' in t or '`' in t) else os.path.normpath(t) if t.startswith('/') else \
            '?' if cwd == '?' else os.path.normpath(os.path.join(cwd or self.root, t))

    @staticmethod
    def prazdny(w):
        """Príkaz bez výstupu (alebo s prázdnym riadkom): „> a“, „: > a“, „true > a“, „echo > a“, „printf '' > a“ — nový audit
        tak vznikne prázdny (overovateľ B17, N6: „: > .aigrip/ng/audit-ticho.jsonl“ pri zakladaní projektu am 21. 9.)."""
        if not w:
            return True
        if w[0] in (':', 'true') and len(w) == 1:
            return True
        return w[0] in ('echo', 'printf') and all(x in ('', '-n', '-e', '-ne', '-en', '-E') for x in w[1:])

    @staticmethod
    def ciel_kopie(c0, a):
        """(cieľ, zdroje) príkazu mv/cp/install/ln/rsync/scp; (None, []) = bez cieľa. Cieľový priečinok voľbou (-t X, -tX,
        -vt X, --target-directory[=]X) pri mv, cp, install a ln — overovateľ B17 (N5): „mv -t/tmp audit“ a
        „mv --target-directory=/tmp audit“ prešli; pri rsync a scp -t cieľ nie je."""
        td, slova, i = None, [], 0
        while i < len(a):
            x = a[i]
            if c0 in ('mv', 'cp', 'install', 'ln'):
                if x == '--target-directory' and i + 1 < len(a):
                    td = a[i + 1]; i += 2; continue
                if x.startswith('--target-directory='):
                    td = x.split('=', 1)[1]; i += 1; continue
                m = re.match(r'^-([A-Za-z]*)t(.*)$', x) if not x.startswith('--') else None
                if m and 'S' not in m.group(1):
                    if m.group(2):
                        td = m.group(2); i += 1; continue
                    if i + 1 < len(a):
                        td = a[i + 1]; i += 2; continue
            if not x.startswith('-'):
                slova.append(x)
            i += 1
        if td is not None:
            return td, slova
        return (slova[-1], slova[:-1]) if len(slova) >= 2 else (None, [])

    def pripisanie(self, slovo):
        b = re.escape(os.path.basename(str(slovo).rstrip('/')))
        return bool(re.search(r'>>\s*["\']?[^\s;&|"\'<>]*' + b + r'(?![\w.-])', self.cmd)) and \
            not re.search(r'(?<![>&\d])>\|?\s*["\']?[^\s;&|"\'<>]*' + b + r'(?![\w.-])', re.sub(r'>>', '', self.cmd))

    def pripisal(self, a, nastroj='Bash'):
        if os.path.basename(a) == 'audit-ticho.jsonl' and not zapisovac_dostupny():
            return                               # jediná cesta zápisu z izby nedostupná — holé pripísanie prejde
        odmietni(nastroj, a, 'pripísal mimo jedinej cesty zápisu')

    @staticmethod
    def find_ohrozene(a, koren, q, rek_vstup=None):
        """Zasiahne find (… -delete / -exec rm, alebo výstup pre xargs) audit q pod koreňom? Podľa -name/-iname/-path;
        -regex = áno. rek_vstup = spotrebiteľ výstupu maže rekurzívne (xargs rm -r)."""
        mena, cesty_v, typ_d, rek = [], [], False, bool(rek_vstup)
        for i, x in enumerate(a):
            if x in ('-name', '-iname') and i + 1 < len(a):
                mena.append((a[i + 1], x == '-iname'))
            elif x in ('-path', '-wholename', '-ipath') and i + 1 < len(a):
                cesty_v.append(a[i + 1])
            elif x in ('-regex', '-iregex'):
                return True
            elif x == '-type' and i + 1 < len(a) and a[i + 1] == 'd':
                typ_d = True
            elif x in ('-exec', '-execdir') and i + 2 < len(a) and os.path.basename(a[i + 1]) == 'rm' \
                    and any(y.startswith('-') and 'r' in y.lower() for y in a[i + 2:i + 5]):
                rek = True
            elif x in ('-exec', '-execdir') and i + 3 < len(a) and os.path.basename(a[i + 1]) in ('sh', 'bash', 'dash', 'zsh') \
                    and a[i + 2] == '-c' and re.search(r'\brm\s+(?:-\S*[rR]|--recursive)', a[i + 3]):
                rek = True
        if not mena and not cesty_v:
            return not typ_d or rek
        rel = os.path.relpath(q, koren)
        casti = rel.split('/')

        def zhoda(meno):
            return any(fnmatch.fnmatchcase(meno.lower() if ci else meno, vz.lower() if ci else vz) for vz, ci in mena)
        if not typ_d and zhoda(casti[-1]):
            return True
        if rek and any(zhoda(c) for c in casti[:-1]):
            return True
        return any(fnmatch.fnmatchcase(os.path.join(koren, rel), vz) or fnmatch.fnmatchcase('./' + rel, vz) for vz in cesty_v)

    def posud(self, cmd, cwd, hlbka=0):
        G = self.G
        self.cmd = cmd
        tel, tela = G['_telá'](cmd)
        spat = None                                       # priečinok pred env -C (platí len pre ten jeden príkaz)
        for w, r in G['_prikazy'](tel):
            if spat is not None:
                cwd, spat = spat, None
            w = [y for x in w for y in zatvorky(self.rozvin(x))]
            vystup, self.vystup = self.vystup, []
            for x in r:                                   # presmerovanie > >> do auditu
                for a in self.cesty(x, cwd):
                    if chraneny(a):
                        if not os.path.lexists(a) and self.prazdny(w):
                            continue                      # nový prázdny audit — nič sa nestratí ani nepripíše (N6)
                        if self.pripisanie(x) or not os.path.lexists(a):
                            self.pripisal(a)              # nový audit s obsahom = pripísanie mimo jedinej cesty
                        else:
                            odmietni('Bash', a, 'prepísal (presmerovanie >)')
            if w and w[0] in ('export', 'local', 'declare', 'readonly'):
                w = w[1:]
            if w and all(re.match(r'^[A-Za-z_]\w*=', x) for x in w):
                for x in w:
                    k, _, h = x.partition('=')
                    self.env[k] = self.dom + h[1:] if h.startswith('~') else h
                continue
            if w and w[0] == 'for' and len(w) > 2 and w[2] == 'in':
                self.zoznamy[w[1]] = w[3:]                     # for f in <audit> …; do rm "$f" — premenná = slová zoznamu
                continue
            w = self.jadro(w)
            if self.chdir is not None:                    # env -C priečinok príkaz (overovateľ B17, N5)
                spat, cwd = cwd, self.kam(self.chdir, cwd)
            if not w:
                continue
            if self.xargs is not None:
                self.vystup = vystup
                w = self.zo_vstupu(w, os.path.basename(w[0]) == 'rm' and any(
                    x.startswith('-') and not x.startswith('--') and 'r' in x.lower() or x == '--recursive' for x in w[1:]))
                self.vystup = []
            c0 = os.path.basename(w[0]); a = w[1:]
            arg = [x for x in a if not x.startswith('-')]
            fl = ''.join(x[1:] for x in a if x.startswith('-') and not x.startswith('--'))
            if c0 in ('cd', 'pushd'):                      # pushd ako cd, so zásobníkom pre popd (overovateľ B17, N5)
                if c0 == 'pushd':
                    self.zasobnik.append(cwd)
                cwd = self.kam(arg[0], cwd) if arg and not re.match(r'^[+-]\d+$', arg[0]) else \
                    self.dom if (c0 == 'cd' and not a) else '?'
            elif c0 == 'popd':
                cwd = self.zasobnik.pop() if self.zasobnik else '?'
            elif c0 in ('bash', 'sh', 'zsh', 'dash') and '-c' in a and a.index('-c') + 1 < len(a) and hlbka < 3:
                self.posud(a[a.index('-c') + 1], cwd, hlbka + 1)      # bash -c '…' — to isté vnútri
                self.cmd = cmd
            elif c0 == 'eval' and a and hlbka < 3:
                self.posud(' '.join(a), cwd, hlbka + 1)               # eval 'rm …' — to isté ako bash -c
                self.cmd = cmd
            elif c0 in ('rm', 'unlink', 'shred', 'truncate', 'srm', 'wipe'):
                rek = c0 == 'rm' and ('r' in fl.lower() or '--recursive' in a)
                for x in arg:
                    for p in self.cesty(x, cwd):
                        if chraneny(p) and os.path.lexists(p):
                            odmietni('Bash', p, 'skrátil' if c0 == 'truncate' else 'zmazal')
                        # rm -r odkazu bez lomky na konci zmaže len odkaz; s lomkou obsah priečinka, kam vedie (N5)
                        if rek and not (os.path.islink(p) and not x.endswith('/')):
                            for q in v_projekte(p):
                                odmietni('Bash', q, f'zmazal (rm -r {p})')
            elif c0 in ('mv', 'cp', 'install', 'ln', 'rsync', 'scp'):
                d, zdroje = self.ciel_kopie(c0, a)
                if d is None:
                    continue
                if c0 in ('rsync', 'scp') and re.match(r'^[\w.@-]+:', d):
                    continue                              # cieľ na inom stroji — tu nič neprepíše
                for dc in self.cesty(d, cwd):
                    if chraneny(dc):
                        odmietni('Bash', dc, 'prepísal')
                    if not os.path.isdir(dc):
                        continue
                    for s in zdroje:
                        for sp in self.cesty(s, cwd) or [s]:
                            # rsync zdroj/ kopíruje obsah, nie priečinok; cp -r zdroj cieľ/ → cieľ/zdroj
                            ciel = dc if (c0 == 'rsync' and s.endswith('/')) else os.path.join(dc, os.path.basename(sp.rstrip('/')))
                            if chraneny(ciel):
                                odmietni('Bash', ciel, 'prepísal')
                            if os.path.isdir(sp):           # kópia priečinka prepíše audit, ktorý má rovnaké meno aj v zdroji
                                for q in pod(ciel):
                                    if os.path.exists(os.path.join(sp, os.path.relpath(q, ciel))):
                                        odmietni('Bash', q, f'prepísal (kópia priečinka {sp})')
                    # --del je skratka pre --delete-during (overovateľ obnovy B17 ov5, 3. 10.: prešla)
                    if c0 == 'rsync' and any(x == '--del' or x.startswith('--delete') for x in a):
                        for q in pod(dc):
                            odmietni('Bash', q, 'zmazal (rsync --delete)')
                if (c0 == 'ln' and 's' not in fl and '--symbolic' not in a) or (c0 == 'cp' and ('l' in fl or '--link' in a)):
                    # pevný odkaz na audit = druhé meno toho istého súboru — cez neho by sa audit dal prepísať či skrátiť
                    for s in zdroje:
                        for p in self.cesty(s, cwd):
                            if chraneny(p) and os.path.lexists(p):
                                odmietni('Bash', p, 'spojil pevným odkazom (cez druhé meno by sa dal prepísať)')
                if c0 == 'mv' or (c0 == 'rsync' and '--remove-source-files' in a):
                    for s in zdroje:
                        for p in self.cesty(s, cwd):
                            if chraneny(p) and os.path.lexists(p):
                                odmietni('Bash', p, 'presunul preč')
                            for q in v_projekte(p):
                                odmietni('Bash', q, f'presunul preč (spolu s priečinkom {p})')
            elif c0 == 'tee':
                for x in arg:
                    for p in self.cesty(x, cwd):
                        if chraneny(p):
                            if 'a' in fl or '--append' in a:
                                self.pripisal(p)
                            else:
                                odmietni('Bash', p, 'prepísal (tee)')
            elif c0 in ('sed', 'perl', 'awk', 'gawk'):
                na_mieste = (c0 == 'sed' and ('i' in fl or any(x.startswith('--in-place') for x in a))) or \
                            (c0 == 'perl' and 'i' in fl) or (c0 in ('awk', 'gawk') and 'inplace' in a)
                if na_mieste:
                    for x in arg:
                        for p in self.cesty(x, cwd):
                            if chraneny(p):
                                odmietni('Bash', p, f'upravil ({c0} na mieste)')
                if c0 == 'perl':
                    self.kod_e(c0, a, cwd)
            elif c0 in KOD_VOLBY:
                self.kod_e(c0, a, cwd)
            elif c0 in BALIC:
                # gzip a spol. nahradia súbor zbaleným (pôvodný zmizne) — okrem -k/-c/-t/-l; zstd a lz4 len s --rm;
                # -r priečinka zbalí každý súbor v ňom (overovateľ B17, N5: gzip -r .aigrip/ng prešlo) — nie pri rozbaľovaní
                necha = (c0 in ('zstd', 'lz4') and '--rm' not in a) or any(ch in fl for ch in 'kctl') or \
                    any(x in ('--keep', '--stdout', '--to-stdout', '--test', '--list') for x in a)
                rek_b = ('r' in fl or '--recursive' in a) and not (c0 in ('gunzip', 'bunzip2', 'unxz') or 'd' in fl
                                                                  or '--decompress' in a or '--uncompress' in a)
                if not necha:
                    for x in arg:
                        for p in self.cesty(x, cwd):
                            if chraneny(p) and os.path.lexists(p):
                                odmietni('Bash', p, f'zbalil ({c0} — pôvodný súbor zmizne)')
                            if rek_b and os.path.isdir(p):
                                for q in pod(p):
                                    odmietni('Bash', q, f'zbalil ({c0} -r {p} — pôvodný súbor zmizne)')
            elif c0 == 'zip' and ('m' in fl or '--move' in a):
                # zip -m presunie súbory do archívu — pôvodné zmiznú (overovateľ B17, N5); prvé slovo je archív
                for x in arg[1:]:
                    for p in self.cesty(x, cwd):
                        if chraneny(p) and os.path.lexists(p):
                            odmietni('Bash', p, 'presunul do archívu (zip -m)')
                        if 'r' in fl or '--recurse-paths' in a:
                            for q in v_projekte(p):
                                odmietni('Bash', q, f'presunul do archívu (zip -m {p})')
            elif c0 == 'sort':
                for i, x in enumerate(a):
                    o = a[i + 1] if x in ('-o', '--output') and i + 1 < len(a) else x[2:] if x.startswith('-o') and len(x) > 2 \
                        else x.split('=', 1)[1] if x.startswith('--output=') else None
                    for p in self.cesty(o, cwd) if o else []:
                        if chraneny(p):
                            odmietni('Bash', p, 'prepísal (sort -o)')
            elif c0 in ('tar', 'bsdtar'):
                # tar -x s menovaným auditom (tar -xf záloha.tar .aigrip/ng/kotva-audit.jsonl); celý archív bez mien nie
                stary_tvar = bool(a) and re.match(r'^[A-Za-z]+$', a[0] or '') is not None
                vol = ([a[0]] if stary_tvar else []) + [x for x in a if x.startswith('-') and not x.startswith('--')]
                rozbal = any('x' in v for v in vol) or '--extract' in a or '--get' in a
                if rozbal or '--remove-files' in a:
                    ciel, clenovia, i = cwd, [], 1 if stary_tvar else 0
                    cak = [ch for v in vol for ch in v.lstrip('-') if ch in 'fC']
                    while i < len(a):
                        x = a[i]
                        if x.startswith('--'):
                            k, _, h = x.partition('=')
                            if k in ('--file', '--directory') and not h and i + 1 < len(a):
                                h = a[i + 1]; i += 1
                            if k == '--directory':
                                ciel = (self.cesty(h, cwd) or [cwd])[0]
                        elif x.startswith('-'):
                            pass
                        elif cak:
                            if cak.pop(0) == 'C':
                                ciel = (self.cesty(x, cwd) or [cwd])[0]
                        else:
                            clenovia.append(x)
                        i += 1
                    for x in clenovia:
                        for p in self.cesty(x if x.startswith('/') else os.path.join(ciel, x), cwd):
                            if chraneny(p) and os.path.lexists(p):
                                odmietni('Bash', p, 'prepísal (tar -x)' if rozbal else 'zmazal (tar --remove-files)')
                            if not rozbal:              # tar --remove-files zmaže zdroje po zabalení (overovateľ B17, N5)
                                for q in v_projekte(p):
                                    odmietni('Bash', q, f'zmazal (tar --remove-files {p})')
            elif c0 in ('ex', 'ed', 'vi', 'vim', 'nvim', 'nano', 'emacs', 'pico', 'joe'):
                for x in arg:
                    for p in self.cesty(x, cwd):
                        if chraneny(p):
                            odmietni('Bash', p, f'upravil (editor {c0})')
            elif c0 == 'dd':
                for x in a:
                    if x.startswith('of='):
                        for p in self.cesty(x[3:], cwd):
                            if chraneny(p):
                                odmietni('Bash', p, 'prepísal (dd)')
            elif c0 == 'find':
                def exec_maze(i):
                    c = os.path.basename(a[i + 1])
                    return c in ('rm', 'shred', 'truncate', 'unlink', 'mv') or (
                        c in ('sh', 'bash', 'dash', 'zsh') and i + 3 < len(a) and a[i + 2] == '-c'
                        and re.search(r'(?:^|[\s;&|(])(?:rm|unlink|shred|truncate|mv)\s', a[i + 3]) is not None)
                if '-delete' in a or any(a[i] in ('-exec', '-execdir', '-ok') and i + 1 < len(a) and exec_maze(i)
                                         for i in range(len(a))):
                    for x in self.korene_find(a) or ['.']:
                        for p in self.cesty(x, cwd):
                            if chraneny(p):
                                odmietni('Bash', p, 'zmazal (find)')
                            for q in pod(p):
                                if self.find_ohrozene(a, p, q):
                                    odmietni('Bash', q, f'zmazal (find {p} … -delete)')
            elif c0 == 'git':
                g = a; d = cwd
                while g and g[0].startswith('-'):
                    if g[0] == '-C' and len(g) > 1:
                        d = os.path.normpath(os.path.join(d if d != '?' else self.root, self.rozvin(g[1])))
                        g = g[2:]
                    else:
                        g = g[1:]
                if not g or d == '?':
                    continue
                sub, ga = g[0], g[1:]
                gfl = ''.join(x[1:] for x in ga if x.startswith('-') and not x.startswith('--'))
                gcesty = ga[ga.index('--') + 1:] if '--' in ga else [x for x in ga if not x.startswith('-')]
                if sub == 'clean' and 'x' in gfl.lower() and 'n' not in gfl and '--dry-run' not in ga:
                    # git clean -x/-X maže aj súbory mimo gitu (audity v aigrip sú mimo gitu) — od priečinka príkazu nadol
                    for x in gcesty or ['.']:
                        for p in self.cesty(x, d):
                            for q in ([p] if chraneny(p) else []) + pod(p):
                                odmietni('Bash', q, 'zmazal (git clean -x maže aj súbory mimo gitu)')
                elif sub == 'stash' and ('a' in gfl or '--all' in ga):
                    k = d
                    while k not in ('', '/') and not os.path.exists(os.path.join(k, '.git')):
                        k = os.path.dirname(k)
                    for q in pod(k if k not in ('', '/') else d):
                        odmietni('Bash', q, 'odložil preč (git stash --all — zo súborov zmizne)')
                elif sub in ('checkout', 'restore', 'rm', 'mv'):
                    # len výslovne menovaný audit (aj cez *); „git checkout -- .“ audit nemenuje — prípadnú stratu
                    # nepotvrdených riadkov uvidí kontrola straty (ng-audit-historia.py)
                    if sub == 'rm' and '--cached' in ga:
                        # súbor ostane len u teba — po commite a odoslaní ho pull zmaže v ostatných klonoch (overovateľ B17, N8;
                        # v kole 2 to prechádzalo ako „súbor ostáva“); audit, ktorý git nesleduje, sa tým nemení — prejde
                        for x in gcesty:
                            for p in self.cesty(x, d):
                                for q in ([p] if chraneny(p) else []) + (pod(p) if 'r' in gfl else []):
                                    if sledovany(q):
                                        odmietni('Bash', q, 'vybral z gitu (git rm --cached) — po odoslaní by ho pull '
                                                            'zmazal v ostatných klonoch')
                        continue
                    for x in gcesty:
                        for p in self.cesty(x, d):
                            if chraneny(p) and os.path.lexists(p):
                                odmietni('Bash', p, f'vrátil či zmazal (git {sub})')
                            if sub == 'rm' and 'r' in gfl:
                                for q in pod(p):
                                    odmietni('Bash', q, f'zmazal (git rm -r {p})')
            elif re.match(r'python3?(\.\d+)?$', c0):
                zasah = []

                def moj_v(p, cwd_, root_):
                    for q in self.cesty(p, cwd_ or cwd):
                        if chraneny(q):
                            zasah.append(q); return True
                        # priečinok s auditom: zásah len pri audite, ktorý kód menuje (os.path.join(NG, 'kotva-audit.jsonl')),
                        # nie pri každom zápise do priečinka — atomický zápis stroj.json či settings.json cez os.replace dával
                        # falošný zákaz (14 dní PS: 4×, keď ho od 3. 10. predsito pustí do rozhodovača)
                        pq = [x for x in pod(q) if os.path.basename(x) in self.kod_akt]
                        if pq:
                            zasah.extend(pq); return True
                    return False
                G['_v'] = moj_v
                G['_PYC'] = list(G.get('_PYC_POVODNE', [])) + [
                    re.compile(r"""shutil\.rmtree\(\s*([^,()]+?)\s*[,)]"""),
                    re.compile(r"""os\.truncate\(\s*([^,()]+?)\s*,"""),
                    re.compile(r"""Path\(\s*([^()]+?)\s*\)\.(?:unlink|rename|replace|rmdir)\("""),
                    re.compile(r"""(?:os\.replace|os\.rename|shutil\.move)\(\s*([^,()]+?)\s*,""")]
                kod = []
                if '-c' in a and a.index('-c') + 1 < len(a):
                    kod.append(a[a.index('-c') + 1])
                if not arg or arg[0] == '-':
                    kod += [b for ln, b in tela if re.search(r'python3?\b', ln)]
                for k in kod:
                    self.kod_akt = k
                    try:
                        if G['_py'](k, cwd, self.root):
                            odmietni('Bash', zasah[0] if zasah else '(cesta v kóde)', 'zapísal alebo zmazal (Python)')
                        # shutil.rmtree/move, os.rename/renames/replace s cestou rozriešenou aj cez os.path.join a premenné
                        # (overovateľ B17, N5): zdroj = audit alebo celý priečinok s auditom, cieľ presunu = audit
                        for m in re.finditer(r'(?:shutil\.(?:rmtree|move)|os\.(?:rename|renames|replace))\s*\(', k):
                            ar = argumenty(k, m.end()) or []
                            for i, x in enumerate(ar[:1 if 'rmtree' in m.group(0) else 2]):
                                for q in self.cesty(py_cesta(x, k, self.dom) or '', cwd):
                                    if chraneny(q) and (i or os.path.lexists(q)):
                                        odmietni('Bash', q, 'prepísal (Python)' if i else 'zmazal alebo presunul (Python)')
                                    if not i and os.path.isdir(q):
                                        for y in v_projekte(q):
                                            odmietni('Bash', y, f'zmazal alebo presunul (priečinok {q} v Pythone)')
                    except SystemExit:
                        raise
                    except Exception:
                        pass
            if c0 in ('echo', 'printf', 'ls', 'find') and '-delete' not in a:
                self.vystup = self.vypis(c0, a, arg, cwd)       # … | xargs rm — položky pre ďalší príkaz


def main():
    try:
        v = json.loads(sys.stdin.buffer.read().decode('utf-8', 'replace') or '{}')
    except Exception:
        return
    if not isinstance(v, dict):
        return
    nastav(v)
    nm = v.get('tool_name') or ''
    ti = v.get('tool_input') if isinstance(v.get('tool_input'), dict) else {}
    root = (os.environ.get('CLAUDE_PROJECT_DIR') or v.get('cwd') or os.getcwd()).rstrip('/') or '/'
    if nm in ('Write', 'Edit', 'MultiEdit', 'NotebookEdit'):
        p = str(ti.get('file_path') or ti.get('notebook_path') or '')
        a = os.path.normpath(p) if p.startswith('/') else os.path.normpath(os.path.join(v.get('cwd') or root, p)) if p else ''
        if chraneny(a):
            odmietni(nm, a, 'prepísal' if nm == 'Write' else 'upravil')
        return
    if nm != 'Bash':
        return
    cmd = str(ti.get('command') or '')
    if not cmd:
        return
    G = nacitaj_blok()
    if G is None:
        return                                   # určovateľ nie je nikde = pustiť (ako stráž zápisníka)
    G['_PYC_POVODNE'] = list(G.get('_PYC', []))
    Posudok(G, root, os.path.expanduser('~')).posud(cmd, v.get('cwd') or root)


if __name__ == '__main__':
    try:
        main()
    except SystemExit:
        raise
    except Exception:
        pass                                   # chyba stráže = pustiť
