#!/usr/bin/env bash
# PreToolUse hook balíka NG — poistka pri práci iného človeka (PS 29. 9., 4.39 b).
# Keď sa partner v sedení PRVÝ RAZ chystá zmeniť, presunúť alebo zmazať súbor, ktorý vytvoril alebo naposledy menil
# iný človek, stroj ho raz pristaví: „Toto je práca X — povedz človeku jednou vetou, čo mu to zmení, zapíš to a pokračuj.“
# Pri ďalšej zmene práce toho istého autora v tom istom sedení mlčí (partner sa nepýta a nečaká — okrem spájania dvoch
# verzií, pri ktorom by vypadol text iného človeka: to nerozhodne sám). Autora oznámenie dostane pri najbližšom otvorení
# projektu, v ktorom pracuje (ng-kdesom-pripomienka.sh), nie kdekoľvek inde.
# Autor = autor commitu v gite (aj „auto: dirty-guard“ — je to práca človeka), bez commitov stroja a hromadných
# správcovských zmien (NG:, prevádzka:, sync:, chore(aigrip), framework:, setup: AIGrip onboarding, Merge, uloženie pred
# prechodom) a bez robotov (…[bot], Claude). Nestráži: /tmp, pamäť, súbory stroja (.aigrip/, .claude/, NG-zaznamy.md),
# súbory mimo gitu, cudzie repo, podagenta, beh bez človeka (claude -p, aj spustený z VS Code). Chyba hooku = pustiť.
# 29. 9. (oprava po revízii): Bash sa číta ako shell — okrem rm/mv/git rm/git mv aj sed -i, perl -i, presmerovanie > >>,
# tee, cp/mv/ln/install na existujúci súbor, truncate, shred, unlink, dd of=, git checkout/restore, find -delete/-exec,
# … | xargs; cd, (…), git -C, env -C a obaly sudo/env/timeout/nohup/nice/bash -c. Priečinok a vzor sa rozvinú na súbory
# a každý súbor sa posúdi zvlášť (tvorca = kto ho pridal, aj cez premenovanie, posledný = najnovší commit človeka).
# Celé to má časový rozpočet ~1,3 s (hook má v nastaveniach 3 s): keď nestihne, pustí a autora si NEoznačí za videného.
set -u
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
IFS= read -r -d '' KOD <<'PY' || true
import os, re, sys, json, time, glob, mmap, fnmatch, threading, subprocess, unicodedata
from collections import namedtuple
T0 = time.monotonic()
# 29. 9.: v aigrip (~18 tis. commitov) stál jeden `git log -- súbor` ~0,25 s a hook pri 20 cieľoch bežal 5,6 s — Claude Code
# ho po 3 s zabil a príkaz prešiel bez kontroly. Rozpočet drží celý beh pod ~1,5 s; čo nestihne, pustí.
try:
    ROZPOCET = float(os.environ.get('NG_CUDZIA_ROZPOCET') or 1.3)
except ValueError:
    ROZPOCET = 1.3
PD = os.path.realpath(sys.argv[1]); STROJ = sys.argv[2]
try:
    v = json.load(sys.stdin)
except Exception:
    sys.exit(0)
if os.environ.get('CLAUDE_CODE_ENTRYPOINT', '').startswith('sdk') or v.get('agent_id'):
    sys.exit(0)
sid = v.get('session_id') or ''
stopa = v.get('transcript_path') or ''
if not sid or '/subagents/' in stopa:
    sys.exit(0)
nm = v.get('tool_name') or ''; ti = v.get('tool_input') or {}
HOME = os.path.expanduser('~')
# 29. 9.: framework:/setup: AIGrip onboarding sú hromadné zmeny správcu v cudzom repe a „uložené pred prechodom na NG“
# je správcovské uloženie — inak by kolega dostal „práca Petra Stročku“ za .gitignore, ktorý Peter len nasadil.
STROJ_SPRAVA = re.compile(r'^(NG[: ]|prevádzka:|users\.tsv:|sync:|chore\(aigrip\)|framework:|setup: AIGrip onboarding|Merge |merge[: ]|data:)'
                          r'|pred prechodom na (nový spôsob|NG)')
VYNECHAT = re.compile(r'(^|/)(\.aigrip|\.claude|\.git)(/|$)|^NG-zaznamy\.md$|^CLAUDE\.md$')
GLOB = re.compile(r'[*?[]')

class Cas(Exception):
    pass

def zostava():
    return ROZPOCET - (time.monotonic() - T0)

# ---------------------------------------------------------------- čítanie príkazu ako shell
# 29. 9.: predtým stačilo, že rm/mv nebol prvé slovo (sudo rm, /bin/rm, (rm …)), že bol za `cd` alebo v `git -C`, alebo že
# súbor menil sed -i / > / cp — a poistka mlčala. Slovo je zoznam častí: ('s', text, v_úvodzovkách) · ('v', premenná, …) ·
# ('x', …) = $(…), `…`, $1 — neznáme, také slovo sa nehádže. Heredoc je dáta (neberie sa), <<< nie je heredoc.
class Slovo(list):
    pass

def tokeny(s, i=0, do_zatvorky=False):
    out, caka, hlbka = [], [], 0
    stav = {'w': None}
    n = len(s)
    def pridaj(typ, text, q):
        w = stav['w']
        if w is None:
            w = stav['w'] = Slovo()
        if typ == 's' and w and w[-1][0] == 's' and w[-1][2] == q:
            w[-1] = ('s', w[-1][1] + text, q)
        else:
            w.append((typ, text, q))
    def koniec():
        if stav['w'] is not None:
            out.append(stav['w']); stav['w'] = None
    while i < n:
        ch = s[i]
        if ch in ' \t':
            koniec(); i += 1; continue
        if ch == '\\':
            if i + 1 < n and s[i + 1] == '\n':
                i += 2; continue
            if i + 1 < n:
                pridaj('s', s[i + 1], True)
            i += 2; continue
        if ch == '\n':
            koniec(); out.append('\n'); i += 1
            for d, tab in caka:                     # telo heredocu sú dáta, nie príkazy
                while i < n:
                    j = s.find('\n', i); j = n if j < 0 else j
                    l = s[i:j]; i = j + 1
                    if (l.lstrip('\t') if tab else l) == d:
                        break
            caka = []
            continue
        if ch == '#' and stav['w'] is None:
            j = s.find('\n', i); i = n if j < 0 else j; continue
        if ch == "'":
            j = s.find("'", i + 1); j = n if j < 0 else j
            pridaj('s', s[i + 1:j], True); i = j + 1; continue
        if ch == '"':
            i = uvodzovky(s, i + 1, pridaj); continue
        if ch == '$':
            i = dolar(s, i, pridaj, False); continue
        if ch == '`':
            j = i + 1
            while j < n and s[j] != '`':
                j += 2 if s[j] == '\\' else 1
            pridaj('x', '', False); i = j + 1; continue
        if ch in '&|;()<>':
            w = stav['w']
            if ch in '<>' and w is not None and len(w) == 1 and w[0][0] == 's' and not w[0][2] and w[0][1].isdigit():
                stav['w'] = None                    # 2> — číslo je deskriptor, nie slovo
            if ch in '<>' and s.startswith('(', i + 1):
                _, i = tokeny(s, i + 2, True); pridaj('x', '', False); continue      # <(…) >(…)
            koniec()
            if do_zatvorky and ch == ')' and hlbka == 0:
                return out, i + 1
            for op in ('&>>', '<<<', '<<-', '&&', '&>', '||', '|&', ';;', '<<', '<>', '<&', '>>', '>|', '>&', '&', '|', ';', '(', ')', '<', '>'):
                if s.startswith(op, i):
                    break
            i += len(op)
            if op in ('<<', '<<-'):
                while i < n and s[i] in ' \t':
                    i += 1
                d, i = oddelovac(s, i)
                caka.append((d, op == '<<-')); out.append('<<'); continue
            if op == '(':
                hlbka += 1
            elif op == ')':
                hlbka -= 1
            out.append({'|&': '|', ';;': ';'}.get(op, op)); continue
        pridaj('s', ch, False); i += 1
    koniec()
    return out, i

def uvodzovky(s, i, pridaj):
    n, buf = len(s), ''
    while i < n and s[i] != '"':
        c = s[i]
        if c == '\\' and i + 1 < n and s[i + 1] in '\\"$`\n':
            if s[i + 1] != '\n':
                buf += s[i + 1]
            i += 2; continue
        if c in '$`':
            if buf:
                pridaj('s', buf, True); buf = ''
            if c == '`':
                j = i + 1
                while j < n and s[j] != '`':
                    j += 2 if s[j] == '\\' else 1
                pridaj('x', '', True); i = j + 1; continue
            i = dolar(s, i, pridaj, True); continue
        buf += c; i += 1
    pridaj('s', buf, True)                          # aj prázdne "" je slovo
    return i + 1

MENO = re.compile(r'[A-Za-z_]\w*')
def dolar(s, i, pridaj, q):
    n = len(s)
    if s.startswith('$((', i):
        h, j = 0, i + 1
        while j < n:
            if s[j] == '(':
                h += 1
            elif s[j] == ')':
                h -= 1
                if h == 0:
                    break
            j += 1
        pridaj('x', '', q); return j + 1
    if s.startswith('$(', i):
        _, j = tokeny(s, i + 2, True); pridaj('x', '', q); return j
    if s.startswith('${', i):
        j = s.find('}', i); j = n if j < 0 else j
        m = s[i + 2:j]
        pridaj('v', m, q) if MENO.fullmatch(m) else pridaj('x', '', q)
        return j + 1
    if not q and s.startswith("$'", i):
        j = i + 2
        while j < n and s[j] != "'":
            j += 2 if s[j] == '\\' else 1
        pridaj('s', s[i + 2:j], True); return j + 1
    m = MENO.match(s, i + 1)
    if m:
        pridaj('v', m.group(), q); return m.end()
    if i + 1 < n and s[i + 1] in '@*#?$!-0123456789':
        pridaj('x', '', q); return i + 2
    pridaj('s', '$', q); return i + 1

def oddelovac(s, i):
    d, n = '', len(s)
    while i < n and s[i] not in ' \t\n;&|<>()':
        c = s[i]
        if c in '\'"':
            j = s.find(c, i + 1); j = n if j < 0 else j
            d += s[i + 1:j]; i = j + 1
        elif c == '\\' and i + 1 < n:
            d += s[i + 1]; i += 2
        else:
            d += c; i += 1
    return d, i

Arg = namedtuple('Arg', 'text gl')                   # gl = vzor, ktorý shell nerozvinul (v úvodzovkách / bez zhody)

def zlozene(x):
    m = re.search(r'\{([^{}]*,[^{}]*)\}', x)
    if not m:
        return [x]
    von = []
    for alt in m.group(1).split(','):
        von += zlozene(x[:m.start()] + alt + x[m.end():])
    return von[:500]

def rozvin(sl, env):
    kusy = [('', '', False)]
    for k, (t, x, q) in enumerate(sl):
        if t == 'x':
            return None
        if t == 'v':
            if x == 'PWD':
                hodn = [env['cwd']] if env['cwd'] else None
            elif x in env['vars']:
                hodn = env['vars'][x]
            elif x == 'HOME':
                hodn = [HOME]
            elif x == 'CLAUDE_PROJECT_DIR':
                hodn = [PD]
            else:
                hodn = None
            if not hodn:
                return None
            alt = [(h, glob.escape(h), False) for h in hodn]
        elif q:
            alt = [(x, glob.escape(x), False)]
        else:
            if k == 0 and (x == '~' or x.startswith('~/')):
                x = HOME + x[1:]
            alt = [(a, a, bool(GLOB.search(a))) for a in zlozene(x)]
        kusy = [(a[0] + b[0], a[1] + b[1], a[2] or b[2]) for a in kusy for b in alt][:500]
    return [(t, p if g else None) for t, p, g in kusy]

def slova(sl_list, env, globy=True):
    von = []
    for sl in sl_list:
        r = rozvin(sl, env)
        if r is None:
            von.append(Arg(None, False)); continue
        for text, vzor in r:
            if globy and vzor is not None and (os.path.isabs(vzor) or env['cwd']):
                zhody = sorted(glob.glob(vzor if os.path.isabs(vzor) else os.path.join(glob.escape(env['cwd']), vzor)))[:3000]
                if zhody:
                    von += [Arg(z, False) for z in zhody]; continue
            von.append(Arg(text, bool(GLOB.search(text))))
    return von

def text0(sl):
    return sl[0][1] if len(sl) == 1 and sl[0][0] == 's' and not sl[0][2] else None

def cesta(text, cwd):
    if text is None:
        return None
    if os.path.isabs(text):
        return os.path.normpath(text)
    return os.path.normpath(os.path.join(cwd, text)) if cwd else None

def volby(a, kratke='', dlhe=()):
    """Voľby ako GNU: vráti (množina volieb, hodnoty volieb, argumenty, koľko argumentov bolo pred `--`)."""
    op, hodn, ar, pred, konec, i = set(), {}, [], None, False, 0
    while i < len(a):
        x = a[i].text
        if x is None or konec or not x.startswith('-') or x == '-':
            ar.append(a[i]); i += 1; continue
        if x == '--':
            konec = True; pred = len(ar); i += 1; continue
        if x.startswith('--'):
            k, rov, h = x[2:].partition('=')
            if not rov and k in dlhe and i + 1 < len(a):
                i += 1; h = a[i].text
            op.add(k); hodn[k] = h; i += 1; continue
        for j in range(1, len(x)):
            c = x[j]; op.add(c)
            if c in kratke:
                h = x[j + 1:]
                if not h and i + 1 < len(a):
                    i += 1; h = a[i].text
                hodn[c] = h
                break
        i += 1
    return op, hodn, ar, pred

KLUCOVE = {'!', '{', '}', 'then', 'else', 'elif', 'do', 'done', 'fi', 'if', 'while', 'until', 'esac', 'time', 'coproc'}
SHELLY = ('bash', 'sh', 'zsh', 'dash', 'ksh')
MENIACE = {'rm', 'unlink', 'tee', 'truncate', 'shred', 'dd', 'sed', 'perl', 'awk', 'gawk', 'cp', 'mv', 'ln', 'install', 'git',
           'find', 'xargs', 'eval', *SHELLY}

def obaly(a, env):
    """sudo/env/timeout/nohup/… pred príkazom — len sa rozpoznajú a zložia (nič sa nespúšťa)."""
    while a and a[0].text is not None:
        c = os.path.basename(a[0].text); j = 1
        if c in ('sudo', 'doas'):
            while j < len(a) and a[j].text and (a[j].text.startswith('-') or re.match(r'[A-Za-z_]\w*=', a[j].text)):
                x = a[j].text
                if x == '--':
                    j += 1; break
                if x in ('-u', '-g', '-h', '-p', '-C', '-r', '-t', '-U', '-T', '-D', '--user', '--group', '--host', '--prompt',
                         '--role', '--type', '--other-user', '--command-timeout', '--close-from', '--chdir'):
                    if x in ('-D', '--chdir') and j + 1 < len(a):
                        env = dict(env, cwd=cesta(a[j + 1].text, env['cwd']))
                    j += 2
                else:
                    j += 1
        elif c == 'env':
            while j < len(a) and a[j].text is not None:
                x = a[j].text
                if x in ('-u', '--unset'):
                    j += 2
                elif x in ('-C', '--chdir'):
                    env = dict(env, cwd=cesta(a[j + 1].text, env['cwd']) if j + 1 < len(a) else None); j += 2
                elif x.startswith('--chdir='):
                    env = dict(env, cwd=cesta(x[8:], env['cwd'])); j += 1
                elif x in ('-S', '--split-string') or x.startswith('--split-string='):
                    return [], env
                elif (x.startswith('-') and x != '-') or re.match(r'[A-Za-z_]\w*=', x):
                    j += 1
                else:
                    break
        elif c == 'timeout':
            while j < len(a) and a[j].text and a[j].text.startswith('-'):
                j += 2 if a[j].text in ('-s', '-k', '--signal', '--kill-after') else 1
            j += 1                                  # trvanie
        elif c in ('nohup', 'exec', 'builtin', 'setsid', 'time', 'command', 'nice', 'ionice', 'stdbuf', 'unbuffer', 'chronic'):
            while j < len(a) and a[j].text and a[j].text.startswith('-'):
                x = a[j].text
                if c == 'command' and x in ('-v', '-V'):
                    return [], env
                vezme = {'nice': ('-n', '--adjustment'), 'ionice': ('-c', '-n', '-p', '-P', '-u', '--class', '--classdata'),
                         'stdbuf': ('-i', '-o', '-e'), 'exec': ('-a',)}.get(c, ())
                j += 2 if x in vezme else 1
        else:
            break
        a = a[j:]
    return a, env

def sed_subory(a):
    """sed: zoznam súborov, ak ide o úpravu na mieste (-i, -Ei, --in-place), inak []."""
    miesto, skript, subory, konec, i = False, False, [], False, 0
    while i < len(a):
        x = a[i].text
        if x is None or konec or not x.startswith('-') or x == '-':
            subory.append(a[i])
        elif x == '--':
            konec = True
        elif x.startswith('--'):
            if x.startswith('--in-place'):
                miesto = True
            elif x in ('--expression', '--file'):
                skript = True; i += 1
            elif x == '--line-length':
                i += 1
            elif x.startswith(('--expression=', '--file=')):
                skript = True
        else:
            for j in range(1, len(x)):
                c = x[j]
                if c == 'i':
                    miesto = True; break            # zvyšok je prípona zálohy
                if c in 'efl':
                    if c in 'ef':
                        skript = True
                    if j == len(x) - 1:
                        i += 1
                    break
        i += 1
    if not miesto:
        return []
    return subory if skript else subory[1:]

def perl_subory(a):
    miesto, skript, subory, i = False, False, [], 0
    while i < len(a):
        x = a[i].text
        if x is None or not x.startswith('-') or x == '-':
            subory.append(a[i])
        elif x == '--':
            subory += a[i + 1:]; break
        else:
            for j in range(1, len(x)):
                c = x[j]
                if c == 'i':
                    miesto = True; break
                if c in 'eE':
                    skript = True
                    if j == len(x) - 1:
                        i += 1
                    break
                if c in 'IMmFl0Cxd':
                    if c == 'I' and j == len(x) - 1:
                        i += 1
                    break
        i += 1
    if not miesto:
        return []
    return subory if skript else subory[1:]

def menit_exec(w):
    """Príkaz za find -exec / xargs: mení súbory, ktoré dostane?"""
    if not w or w[0].text is None:
        return False
    c = os.path.basename(w[0].text)
    return (c in ('rm', 'unlink', 'shred', 'truncate', 'mv') or (c == 'sed' and bool(sed_subory(w[1:] + [Arg('_', False)])))
            or (c == 'perl' and bool(perl_subory(w[1:] + [Arg('_', False)]))))

def find_vyber(a):
    """find: (štartové cesty, filter mien), alebo None, ak find nič nemaže ani nemení."""
    ar = a[1:]; i = 0
    while i < len(ar) and ar[i].text and (ar[i].text in ('-H', '-L', '-P') or re.match(r'-O\d', ar[i].text) or ar[i].text == '-D'):
        i += 2 if ar[i].text == '-D' else 1
    starty = []
    while i < len(ar) and (ar[i].text is None or not (ar[i].text.startswith('-') or ar[i].text in ('(', '!', ')', ','))):
        starty.append(ar[i]); i += 1
    vyraz = ar[i:]; t = [x.text for x in vyraz]
    meni = '-delete' in t
    for k, x in enumerate(t):
        if x in ('-exec', '-execdir', '-ok', '-okdir'):
            sub = []
            for y in vyraz[k + 1:]:
                if y.text in (';', '+'):
                    break
                if y.text != '{}':
                    sub.append(y)
            meni = meni or menit_exec(sub)
    mena = tuple((t[k + 1], x == '-iname') for k, x in enumerate(t) if x in ('-name', '-iname') and k + 1 < len(t) and t[k + 1])
    return (starty or [Arg('.', False)], mena) if meni else None

def zdroj_rury(pred):
    """Čo posiela do xargs predchádzajúci príkaz rúry: (cesty, filter mien, git pathspec?, env) alebo None."""
    if not pred:
        return None
    sl, env = pred
    a, env = obaly(slova(sl, env), env)
    if not a or a[0].text is None:
        return None
    c = os.path.basename(a[0].text)
    if c == 'find':
        ar = a[1:]; i = 0
        while i < len(ar) and ar[i].text in ('-H', '-L', '-P'):
            i += 1
        st = []
        while i < len(ar) and (ar[i].text is None or not (ar[i].text.startswith('-') or ar[i].text in ('(', '!'))):
            st.append(ar[i]); i += 1
        t = [x.text for x in ar[i:]]
        mena = tuple((t[k + 1], x == '-iname') for k, x in enumerate(t) if x in ('-name', '-iname') and k + 1 < len(t) and t[k + 1])
        return st or [Arg('.', False)], mena, False, env
    if c in ('ls', 'echo'):
        return [x for x in a[1:] if x.text and not x.text.startswith('-')], (), False, env
    if c == 'git' and len(a) > 1 and a[1].text == 'ls-files':
        ar = volby(a[2:], 'xX', ('exclude', 'exclude-from'))[2]
        return ar or [Arg('.', False)], (), True, env
    if c in ('grep', 'egrep', 'rg'):
        op, h, ar, _ = volby(a[1:], 'efmABCD', ('regexp', 'file', 'max-count', 'include', 'exclude', 'exclude-dir'))
        if not (op & {'l', 'L', 'files-with-matches', 'files-without-match'}) and c != 'rg':
            return None
        ar = ar if ('e' in h or 'f' in h or 'regexp' in h) else ar[1:]
        return ar or [Arg('.', False)], (), False, env
    return None

def kopia(a, druh):
    """cp/mv/ln/install: (ciele — čo sa prepíše, a pri mv aj čo sa presunie)."""
    kr = {'install': 'tSmog'}.get(druh, 'tS')
    op, h, ar, _ = volby(a[1:], kr, ('target-directory', 'suffix', 'mode', 'owner', 'group'))
    von = []
    if druh == 'mv':
        von += [x.text for x in ar if x.text is not None]              # presunie sa preč
        if not (op & {'t', 'target-directory'}):
            von = von[:-1] if ar and ar[-1].text is not None else von
    if druh == 'ln' and not (op & {'f', 'force'}):
        return von
    if (druh == 'install' and op & {'d', 'directory'}) or op & {'n', 'no-clobber'} or h.get('update') == 'none':
        return von
    return von, ar, op, h

def ciele_kopie(a, druh, cwd):
    r = kopia(a, druh)
    if isinstance(r, list):
        return [(cesta(x, cwd), False) for x in r]
    von, ar, op, h = r
    von = [(cesta(x, cwd), False) for x in von]
    tdir = h.get('t') or h.get('target-directory')
    if tdir is not None:
        d = cesta(tdir, cwd); zdroje = ar
        pary = [(s, os.path.join(d, os.path.basename(s.text.rstrip('/'))) if d and s.text else None) for s in zdroje]
    else:
        if len(ar) < 2 or ar[-1].text is None:
            return von
        d = cesta(ar[-1].text, cwd); zdroje = ar[:-1]
        if d and os.path.isdir(d) and not (op & {'T', 'no-target-directory'}):
            pary = [(s, os.path.join(d, os.path.basename(s.text.rstrip('/'))) if s.text else None) for s in zdroje]
        else:
            pary = [(s, d) for s in zdroje]
    rekurz = druh == 'cp' and op & {'r', 'R', 'a', 'recursive', 'archive'}
    for s, d in pary:
        if not d:
            continue
        sp = cesta(s.text, cwd)
        if sp and os.path.isdir(sp):
            if not rekurz:
                continue
            k = 0
            for kor, _, subs in os.walk(sp):     # cp -r prepíše len súbory, ktoré majú v cieli rovnaké meno
                for f in subs:
                    von.append((os.path.join(d, os.path.relpath(os.path.join(kor, f), sp)), False)); k += 1
                if k > 3000:
                    break
            continue
        von.append((d, False))
    return von

def git_ciele(a, env, ciele):
    cwd, i = env['cwd'], 1
    while i < len(a) and a[i].text and a[i].text.startswith('-'):
        x = a[i].text
        if x == '-C':
            cwd = cesta(a[i + 1].text, cwd) if i + 1 < len(a) else None; i += 2
        elif x in ('-c', '--git-dir', '--work-tree', '--namespace', '--exec-path', '--super-prefix', '--config-env'):
            i += 2
        else:
            i += 1
    if i >= len(a) or a[i].text is None:
        return
    sub, rest = a[i].text, a[i + 1:]
    def daj(ar, zmeny=None):
        for x in ar:
            if x.text is None or x.text.startswith(':'):
                continue
            p = cesta(x.text, cwd)
            if not p:
                continue
            if x.gl:                                 # git si vzor rozvinie sám
                ciele.append((p, True, zmeny, ()))
            elif zmeny is not None and os.path.isdir(p):
                ciele.append((p, False, zmeny, ()))
            else:
                ciele.append((p, False, None, ()))
    if sub == 'rm':
        daj(volby(rest, '', ('pathspec-from-file',))[2])
    elif sub == 'mv':
        op, h, ar, _ = volby(rest, '', ())
        if op & {'n', 'dry-run'} or len(ar) < 2:
            return
        daj(ar[:-1])
        if op & {'f', 'force'} and ar[-1].text is not None:
            d = cesta(ar[-1].text, cwd)
            if d and os.path.isdir(d):
                daj([Arg(os.path.join(d, os.path.basename((s.text or '').rstrip('/'))), False) for s in ar[:-1] if s.text])
            else:
                daj(ar[-1:])
    elif sub in ('checkout', 'restore'):
        op, h, ar, pred = volby(rest, 'bBsU' if sub == 'checkout' else 's', ('orphan', 'pathspec-from-file', 'source', 'conflict'))
        if sub == 'checkout' and op & {'b', 'B', 'orphan', 'detach'}:
            return
        if sub == 'restore' and op & {'S', 'staged'} and not op & {'W', 'worktree'}:
            return                                   # len index — súbor v pracovnom strome sa nemení
        src = h.get('s') or h.get('source') or ''
        if sub == 'checkout':
            if pred is not None:
                src, ar = (ar[0].text or '') if pred else '', ar[pred:]
            else:                                    # bez `--`: cesty sú tie argumenty, ktoré existujú
                if ar and ar[0].text and not os.path.exists(cesta(ar[0].text, cwd) or '') and not ar[0].gl:
                    src, ar = ar[0].text, ar[1:]
                ar = [x for x in ar if x.gl or (x.text and os.path.exists(cesta(x.text, cwd) or ''))]
        # 29. 9.: priečinok/vzor pri checkout/restore mení len súbory, ktoré sa líšia — inak `git checkout -- .` pristaví
        # za každý súbor v repe, hoci nič nezmení; menovaný súbor sa posudzuje vždy
        daj(ar, zmeny=src if not src.startswith('-') else '')

def jeden(sl, presm, env, ciele, pred, hlbka):
    cwd = env['cwd']
    for op, w in presm:                              # > >> >| &> &>> <> >& zapisujú do súboru
        for x in slova([w], env):
            if x.text is None or (op == '>&' and (x.text.isdigit() or x.text == '-')):
                continue
            p = cesta(x.text, cwd)
            if p:
                ciele.append((p, False, None, ()))
    sl = list(sl)
    while sl and text0(sl[0]) in KLUCOVE:
        sl.pop(0)
    if len(sl) >= 3 and text0(sl[0]) == 'for' and text0(sl[2]) == 'in':
        hodn = slova(sl[3:], env)
        env['vars'][text0(sl[1]) or ''] = None if any(x.text is None for x in hodn) else [x.text for x in hodn]
        return None
    prir = []
    while sl and sl[0] and sl[0][0][0] == 's' and not sl[0][0][2] and re.match(r'[A-Za-z_]\w*=', sl[0][0][1]):
        prir.append(sl.pop(0))
    if not sl:
        for w in prir:
            k, _, zv = w[0][1].partition('=')
            r = rozvin(Slovo([('s', zv, False)] + list(w[1:])), env)
            env['vars'][k] = [r[0][0]] if r else None
        return None
    a = slova(sl, env, False)
    if a and a[0].text in ('cd', 'pushd', 'popd'):
        c, ar = a[0].text, [x for x in a[1:] if not (x.text and x.text.startswith('-') and x.text != '-')]
        if c == 'popd':
            env['cwd'] = env['zasobnik'].pop() if env['zasobnik'] else None
        else:
            if c == 'pushd':
                env['zasobnik'].append(env['cwd'])
            if not ar:
                env['cwd'] = HOME
            elif ar[0].text in (None, '-'):
                env['cwd'] = None                    # nevieme kam — relatívne cesty ďalej neriešime
            else:
                p = cesta(ar[0].text, cwd)
                env['cwd'] = os.path.realpath(p) if p else None
        return sl
    b = obaly(a, env)[0]
    if not b or b[0].text is None or os.path.basename(b[0].text) not in MENIACE:
        return sl                                    # ls, cat, grep … — globy netreba rozvíjať
    a, env2 = obaly(slova(sl, env), env)
    if not a or a[0].text is None:
        return sl
    cwd = env2['cwd']
    c = os.path.basename(a[0].text)
    def daj(texty):
        for x in texty:
            p = cesta(x.text if isinstance(x, Arg) else x, cwd)
            if p:
                ciele.append((p, False, None, ()))
    if c == 'rm':
        op, h, ar, _ = volby(a[1:])
        rek = op & {'r', 'R', 'recursive'}
        daj([x for x in ar if x.text and (rek or not os.path.isdir(cesta(x.text, cwd) or ''))])
    elif c in ('unlink', 'tee'):
        daj(volby(a[1:])[2])
    elif c == 'truncate':
        daj(volby(a[1:], 'sr', ('size', 'reference'))[2])
    elif c == 'shred':
        daj(volby(a[1:], 'ns', ('iterations', 'size', 'random-source'))[2])
    elif c == 'dd':
        daj([x.text[3:] for x in a[1:] if x.text and x.text.startswith('of=')])
    elif c == 'sed':
        daj(sed_subory(a[1:]))
    elif c == 'perl':
        daj(perl_subory(a[1:]))
    elif c in ('awk', 'gawk'):
        t = [x.text for x in a[1:]]
        if any(t[k] in ('-i', '--include') and k + 1 < len(t) and t[k + 1] == 'inplace' for k in range(len(t))) or '-iinplace' in t:
            op, h, ar, _ = volby(a[1:], 'ivfF', ('include', 'assign', 'file', 'field-separator'))
            daj(ar if ('f' in h or 'file' in h) else ar[1:])
    elif c in ('cp', 'mv', 'ln', 'install'):
        ciele += [(p, False, None, ()) for p, _ in ciele_kopie(a, c, cwd) if p]
    elif c == 'git':
        git_ciele(a, env2, ciele)
    elif c == 'find':
        r = find_vyber(a)
        if r:
            for x in r[0]:
                p = cesta(x.text, cwd)
                if p:
                    ciele.append((p, False, None, r[1]))
    elif c == 'xargs':
        i = 1
        while i < len(a) and a[i].text and a[i].text.startswith('-'):
            x = a[i].text
            i += 2 if x in ('-a', '-d', '-E', '-I', '-L', '-n', '-P', '-s', '--arg-file', '--delimiter', '--eof', '--replace',
                            '--max-lines', '--max-args', '--max-procs', '--max-chars', '--process-slot-var') else 1
        z = zdroj_rury(pred)
        if z and menit_exec([x for x in a[i:] if x.text not in ('{}',)]):
            cesty, mena, spec, penv = z
            for x in cesty:
                p = cesta(x.text, penv['cwd'])
                if p:
                    ciele.append((p, spec and x.gl, None, mena))
    elif c in SHELLY and hlbka < 3:
        for k in range(1, len(a)):
            x = a[k].text
            if x is None or not x.startswith(('-', '+')):
                break
            if 'c' in x[1:] and not x.startswith('--') and k + 1 < len(a) and a[k + 1].text is not None:
                prejdi(tokeny(a[k + 1].text)[0], dict(env2, vars=dict(env2['vars']), zasobnik=[]), ciele, hlbka + 1)
                break
    elif c == 'eval' and hlbka < 3:
        if all(x.text is not None for x in a[1:]):
            prejdi(tokeny(' '.join(x.text for x in a[1:]))[0], env, ciele, hlbka + 1)
    return sl

def prejdi(tok, env, ciele, hlbka=0):
    w, r, i, pred, spoj, zas = [], [], 0, None, None, []
    while i <= len(tok):
        t = tok[i] if i < len(tok) else '\n'
        if isinstance(t, Slovo):
            w.append(t); i += 1; continue
        if t in ('>', '>>', '>|', '&>', '&>>', '<>', '>&'):
            if i + 1 < len(tok) and isinstance(tok[i + 1], Slovo):
                r.append((t, tok[i + 1])); i += 2
            else:
                i += 1
            continue
        if t in ('<', '<&', '<<<'):
            i += 2 if i + 1 < len(tok) and isinstance(tok[i + 1], Slovo) else 1
            continue
        if t == '<<':
            i += 1; continue
        a = None
        if w or r:
            a = jeden(w, r, env, ciele, pred if spoj == '|' else None, hlbka)
        pred = (a, dict(env)) if a else None
        spoj, w, r = t, [], []
        if t == '(':
            zas.append((env['cwd'], dict(env['vars'])))
        elif t == ')' and zas:
            env['cwd'], env['vars'] = zas.pop()
        i += 1

def ciele_nastroja():
    if nm in ('Edit', 'Write', 'MultiEdit', 'NotebookEdit'):
        p = ti.get('file_path') or ti.get('notebook_path') or ''
        return [(p, False, None, ())] if p and os.path.isabs(p) else []
    if nm != 'Bash':
        return []
    c = ti.get('command') or ''
    env = {'cwd': os.path.realpath(v.get('cwd') or PD), 'vars': {}, 'zasobnik': []}
    ciele = []
    prejdi(tokeny(c)[0], env, ciele)
    return ciele

# ---------------------------------------------------------------- kto je kto
def n(x):
    x = ''.join(ch for ch in unicodedata.normalize('NFD', (x or '').lower()) if unicodedata.category(ch) != 'Mn')
    return re.sub(r'[\s._-]+', ' ', x).strip()

# 29. 9.: pstr13 je súkromný GitHub účet PS (squash-merge v bpw) — bez toho mu hook v jeho vlastnom projekte hlásil
# „vytvoril(a) pstr13“. Ďalšie aliasy sem (účet → účet v ng/ludia.tsv); riadok „… privát“ v ludia.tsv je ten istý človek.
ALIAS = {'pstr13': 'pstrocka'}
UCTY = {}
try:
    for l in open(os.path.join(STROJ, 'ng', 'ludia.tsv'), encoding='utf-8'):
        u, _, m = l.strip().partition('|')
        if u and m:
            UCTY[u.lower()] = re.sub(r' privat$', '', n(m))
except Exception:
    pass

def login(mail):
    lp, _, dom = (mail or '').lower().partition('@')
    return lp.split('+')[-1] if dom.endswith('users.noreply.github.com') else lp

def kluc(meno, mail):
    for x in (login(mail), (meno or '').lower()):
        x = ALIAS.get(x, x)
        if x in UCTY:
            return UCTY[x]
    return n(meno)

def robot(meno, mail):
    m, e = (meno or '').lower(), (mail or '').lower()
    return m.endswith('[bot]') or '[bot]@' in e or n(meno) in ('claude', 'github', 'github actions', 'dependabot', 'copilot')

def stroj(meno, mail, sprava):
    return bool(STROJ_SPRAVA.search(sprava)) or robot(meno, mail)

# ---------------------------------------------------------------- git
GENV = dict(os.environ, GIT_OPTIONAL_LOCKS='0', GIT_TERMINAL_PROMPT='0', LC_ALL='C')
ROOT = PD

def git(*a, vstup=None):
    t = zostava()
    if t <= 0.03:
        raise Cas()
    try:
        return subprocess.run(['git', '-C', ROOT, *a], input=vstup, capture_output=True, timeout=t, env=GENV).stdout
    except subprocess.TimeoutExpired:
        raise Cas()

def prud(args, spracuj, vstup=None):
    """Číta výstup gitu po kúskoch (oddelené \\0); spracuj() vráti True = stačí. Po rozpočte proces zabije. True = dobehol."""
    t = zostava()
    if t <= 0.03:
        return False
    p = subprocess.Popen(['git', '-C', ROOT, *args], stdin=subprocess.PIPE if vstup else subprocess.DEVNULL,
                         stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=GENV)
    tm = threading.Timer(t, p.kill); tm.start()
    zv, cely = b'', False
    try:
        if vstup:
            p.stdin.write(vstup); p.stdin.close()
        while True:
            k = p.stdout.read1(65536)
            if not k:
                if zv:
                    spracuj([zv])
                cely = p.wait(timeout=max(zostava(), 0.01)) == 0 and zostava() > 0     # zabitý alebo chyba = nedobehol
                break
            casti = (zv + k).split(b'\0'); zv = casti.pop()
            if spracuj(casti):
                cely = True
                break
    except Exception:
        cely = False
    finally:
        tm.cancel()
        try:
            p.kill(); p.wait(timeout=0.2)
        except Exception:
            pass
    return cely

FMT = '--format=%x1e%H%x1f%ct%x1f%an%x1f%ae%x1f%ad%x1f%s'

def prechod(stavy, specy, starty):
    """Jeden priechod históriou (od najnovšieho): pre každý súbor prvý commit človeka (posledný, kto menil) a commit,
    ktorý súbor na tejto ceste pridal (vznik). Skončí, keď má vznik každý súbor."""
    podla = {}
    for st in stavy:
        st['v_prechode'] = False
        podla.setdefault(st['p'], []).append(st)
    s = {'h': None, 'st': None, 'ost': len(stavy)}
    def spracuj(casti):
        for t in casti:
            t = t.decode('utf-8', 'surrogateescape')
            if s['st'] is not None:
                stat, h = s['st'], s['h']; s['st'] = None
                for st in podla.get(t, ()) if h else ():
                    if st['v_prechode'] or (st['hore'] is not None and (h['ct'] > st['hore'] or h['c'] == st['od'])):
                        continue
                    if stat == 'A':
                        st['vznik'] = h; st['v_prechode'] = True; s['ost'] -= 1
                    elif stat in 'MT' and h['clovek'] and not st['posl_hotovo']:
                        st['posl'] = h; st['posl_hotovo'] = True
                continue
            if t.startswith('\x1e'):
                f = t[1:].split('\x1f', 5)
                s['h'] = {'c': f[0], 'ct': int(f[1]), 'a': (f[2], f[3]), 'd': f[4], 'clovek': not stroj(f[2], f[3], f[5])} if len(f) == 6 else None
                continue
            t = t.lstrip('\n')
            if t:
                s['st'] = t[0]
        return s['ost'] <= 0
    return prud(['log', '--no-renames', '--name-status', '-z', FMT, '--date=format:%-d. %-m.', *starty, '--', *specy], spracuj)

def presuny(stavy, je_ja):
    """Bol vznik súboru v skutočnosti premenovaním? (git diff-tree -M na commite vzniku.) Najprv commity cudzích ľudí —
    tie môžu pristaviť; kým to nie je overené, cudzí „tvorca“ sa nehlási (inak by presun kolegom dal jemu PS-ovu prácu)."""
    com = {}
    for st in stavy:
        if st.get('vznik') and 'presun' not in st:
            com.setdefault(st['vznik']['c'], []).append(st)
    if not com:
        return
    def pr(c):
        h = com[c][0]['vznik']
        return 0 if h['clovek'] and not je_ja(*h['a']) else (1 if not h['clovek'] else 2)
    poradie = sorted(com, key=pr)[:1500]
    s = {'c': None, 'kus': [], 'mapa': {}, 'videl': set()}
    def uzavri_commit():
        if s['c'] in com:
            s['videl'].add(s['c'])
            for st in com[s['c']]:
                st['presun'] = s['mapa'].get(st['p'])
        s['mapa'] = {}
    def spracuj(casti):
        for t in casti:
            t = t.decode('utf-8', 'surrogateescape')
            if s['kus']:
                s['kus'].append(t)
                if s['kus'][0][0] in 'RC' and len(s['kus']) < 3:
                    continue
                if s['kus'][0][0] == 'R':
                    s['mapa'][s['kus'][2]] = (s['kus'][1], int(s['kus'][0][1:] or 0))
                s['kus'] = []
                continue
            if re.fullmatch(r'[0-9a-f]{40}', t):
                uzavri_commit(); s['c'] = t
            elif t:
                s['kus'] = [t]
        return False
    # 29. 9.: --root — prvý commit repa (aigrip: migrácia K1, 239 súborov) diff-tree inak vynechá a jeho súbory by
    # ostali neuzavreté; commit, ku ktorému po celom behu neprišiel žiadny výstup, nemá čo premenovať
    if prud(['diff-tree', '--stdin', '--root', '-r', '-M', '-z', '--name-status'], spracuj, vstup=('\n'.join(poradie) + '\n').encode()):
        uzavri_commit()
        for c in poradie:
            if c not in s['videl']:
                for st in com[c]:
                    st['presun'] = None

def uzavri(stavy):
    dalsie = []
    for st in stavy:
        vz = st.get('vznik')
        if not vz or 'presun' not in st:
            continue                                  # neoverené — neuzavreté, nehlási sa
        pr = st.pop('presun'); st['vznik'] = None
        if pr is None:
            if vz['clovek']:
                st['tvorca'] = vz
                if not st['posl_hotovo']:
                    st['posl'] = vz
            st['posl_hotovo'] = st['tvorca_hotovo'] = True
        else:                                         # premenované: tvorca je, kto pridal pôvodný súbor (--follow)
            stara, skore = pr
            if vz['clovek'] and skore < 100 and not st['posl_hotovo']:
                st['posl'] = vz; st['posl_hotovo'] = True
            st['p'], st['hore'], st['od'] = stara, vz['ct'], vz['c']
            st['kolo'] += 1
            if st['kolo'] <= 4:
                dalsie.append(st)
    return dalsie

def bez_cloveka():
    """claude -p spustený z Bash sedenia vo VS Code dedí CLAUDE_CODE_ENTRYPOINT=claude-vscode — pozná sa podľa stopy:
    žiadne zadanie neprišlo od človeka (turnOrigin len „sdk“), ako v ng-kontext-straz.sh (skúška návratu 20. 9.)."""
    try:
        with open(stopa, 'rb') as f:
            mm = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)
    except Exception:
        return False
    try:
        if re.search(rb'"entrypoint":\s*"sdk', mm):
            return True
        return bool(re.search(rb'"turnOrigin":\s*"sdk"', mm)) and not re.search(rb'"turnOrigin":\s*"human"', mm)
    finally:
        mm.close()

def hlavne():
    global ROOT
    surove = ciele_nastroja()
    if not surove:
        return
    # 1. ciele v projekte → pathspecy (priečinok a vzor sa rozvinú na súbory)
    ciele = []
    for p, spec, zmeny, filtr in surove:
        if spec:
            hlava, zv = p, ''
            while GLOB.search(hlava):
                hlava, k = os.path.split(hlava); zv = os.path.join(k, zv) if zv else k
            p = os.path.join(os.path.realpath(hlava), zv)
        else:
            p = os.path.realpath(p)
        if p == PD or p.startswith(PD + os.sep):
            ciele.append((p, spec, zmeny, filtr))
    if not ciele:
        return
    ROOT = os.path.realpath(git('rev-parse', '--show-toplevel').decode('utf-8', 'surrogateescape').strip() or PD)
    if not (PD == ROOT or PD.startswith(ROOT + os.sep)):
        return
    skup = {}
    for p, spec, zmeny, filtr in ciele:
        rel = os.path.relpath(p, ROOT)
        skup.setdefault((zmeny, filtr), []).append(rel if spec else ':(literal)' + rel)
    subory, specy = [], []
    for (zmeny, filtr), sp in skup.items():
        if zmeny is None:
            out = git('ls-files', '-z', '--', *sp)
        else:
            out = git('diff', '--name-only', '-z', *([zmeny] if zmeny else []), '--', *sp)
        tu = []
        zac = [x[10:] if x.startswith(':(literal)') else x for x in sp]
        def meno_sedi(r):
            # find -name: zhoda mena súboru alebo priečinka pod štartom (find -name old -exec rm -rf {} zmaže celý priečinok)
            for z in zac:
                if z in ('.', ''):
                    rel = r
                elif r == z:
                    rel = os.path.basename(r)
                elif r.startswith(z + '/'):
                    rel = r[len(z) + 1:]
                else:
                    continue
                return any(fnmatch.fnmatchcase(k.lower() if ic else k, pat.lower() if ic else pat)
                           for k in rel.split('/') for pat, ic in filtr)
            return False
        for r in out.decode('utf-8', 'surrogateescape').split('\0'):
            if not r:
                continue
            ab = os.path.join(ROOT, r)
            if not ab.startswith(PD + os.sep):
                continue
            relpd = os.path.relpath(ab, PD)
            if VYNECHAT.search(relpd) or (filtr and not meno_sedi(r)):
                continue
            tu.append((r, relpd))
        subory += tu
        # priechod históriou dostane priečinok, nie tisíc ciest (tisíce pathspecov git prechádza pomaly); pri filtri/zmenách
        # s pár súbormi len tie súbory
        specy += sp if (zmeny is None and not filtr) or len(tu) > 200 else [':(literal)' + r for r, _ in tu]
    videl = set()
    subory = [x for x in subory if not (x[0] in videl or videl.add(x[0]))]
    if not subory:
        return
    # 2. kto som ja
    cfg = git('config', '--get-regexp', r'^user\.(name|email)$').decode('utf-8', 'replace')
    meno = re.findall(r'^user\.name (.*)$', cfg, re.M); mail = re.findall(r'^user\.email (.*)$', cfg, re.M)
    meno, mail = (meno[-1] if meno else ''), (mail[-1] if mail else '')     # platí posledná hodnota (ako git config --get)
    ja_u = os.environ.get('USER', '').lower()
    ja = {kluc(meno, mail), n(meno), n(login(mail)), n(ja_u), UCTY.get(ja_u, '')}
    ja |= {UCTY.get(al, '') for al, u in ALIAS.items() if u == ja_u}
    ja.discard('')
    def je_ja(m, e):
        return kluc(m, e) in ja or n(m) in ja or n(login(e)) in ja
    # 3. história — jeden priechod pre všetky súbory, potom overenie premenovaní a pokračovanie po starej ceste
    stavy = [{'p': r, 'rel': rp, 'hore': None, 'od': None, 'posl': None, 'posl_hotovo': False, 'tvorca': None,
              'tvorca_hotovo': False, 'kolo': 0, 'vznik': None} for r, rp in subory]
    kolo, starty = stavy, []
    while kolo:
        prechod(kolo, specy, starty)
        presuny(kolo, je_ja)
        kolo = uzavri(kolo)
        if zostava() <= 0.05:
            break
        specy = sorted({':(literal)' + st['p'] for st in kolo})
        starty = sorted({st['od'] + '^' for st in kolo})
    if os.environ.get('NG_CUDZIA_LADENIE'):          # len na skúšku — wrapper stderr zahadzuje
        print(json.dumps({'subory': len(stavy), 'posl': sum(st['posl_hotovo'] for st in stavy),
                          'tvorca': sum(st['tvorca_hotovo'] for st in stavy), 'cas': round(time.monotonic() - T0, 3)}), file=sys.stderr)
    # 4. čo je cudzie (len overené fakty — nedokončené sa nehlási)
    najdene = {}
    for st in stavy:
        for h, co, hotovo in ((st['posl'], 'naposledy menil(a)', st['posl_hotovo']), (st['tvorca'], 'vytvoril(a)', st['tvorca_hotovo'])):
            if h is st['tvorca'] and co != 'vytvoril(a)':
                continue                              # odvtedy ho nikto nemenil — povie sa „vytvoril(a)“
            if hotovo and h and not je_ja(*h['a']):
                najdene.setdefault(kluc(*h['a']), (h['a'][0], st['rel'], h['d'], co))
    if not najdene:
        return
    st_p = os.path.join(HOME, '.aigrip-ng', 'cudzia-praca.json')
    try:
        stav = json.load(open(st_p, encoding='utf-8'))
    except Exception:
        stav = {}
    teraz = time.time()
    stav = {k: w for k, w in stav.items() if isinstance(w, dict) and any(teraz - t < 2 * 86400 for t in w.values())}
    videne = stav.setdefault(sid, {})
    nove = {k: x for k, x in najdene.items() if k not in videne and n(x[0]) not in videne}
    if not nove or bez_cloveka():
        return
    for k in nove:
        videne[k] = teraz
    # 29. 9.: pamäť sa najprv zapíše vedľa a na miesto sa presunie až PO vypísaní zastavenia — keď hook zabijú medzi tým,
    # autor ostane nevidený a zastavenie príde znova (predtým sa zapísala skôr a zabitý beh autora „videl“ bez zastavenia)
    try:
        os.makedirs(os.path.dirname(st_p), exist_ok=True)
        tmp = f'{st_p}.tmp{os.getpid()}'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(stav, f)
    except Exception:
        return                                        # bez pamäte by pristavoval dokola — radšej pustiť
    kto = '; '.join(f'{rel} {co} {a} ({dat})' for a, rel, dat, co in nove.values())
    mena = ', '.join(x[0] for x in nove.values())
    dovod = (f'NG, poistka 4.39: toto je práca iného človeka — {kto}. Skôr než to zmeníš, presunieš alebo zmažeš: povedz človeku, '
             f's ktorým pracuješ, jednou vetou, čo to {mena} zmení, a zapíš to do zápisníka '
             f'(python3 {STROJ}/scripts/ng-zapis.py {os.path.basename(PD)} poznatok partner partner "<čo sa zmenilo v práci koho a prečo>" "<kde platí>"). '
             'Potom príkaz zopakuj — pri ďalšej zmene práce toho istého človeka v tomto sedení ťa už nepristavím. Nepýtaj sa a nečakaj; '
             'len keby pri spájaní dvoch verzií mal vypadnúť text iného človeka, nerozhoduj sám a opýtaj sa. Autor sa to dozvie sám, '
             'keď najbližšie otvorí tento projekt.')
    sys.stdout.write(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': 'deny',
                                                        'permissionDecisionReason': dovod}}, ensure_ascii=False) + '\n')
    sys.stdout.flush()
    try:
        os.replace(tmp, st_p)
    except Exception:
        pass
    try:                                              # stopa beží odpojená — nečaká sa na ňu
        subprocess.Popen(['bash', '-c', f'. {STROJ}/scripts/ng-hook-stopa.sh ng-cudzia-praca-zasah'], env=dict(os.environ, CLAUDE_PROJECT_DIR=PD),
                         stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
    except Exception:
        pass

try:
    hlavne()
except Exception:
    pass
PY
python3 -c "$KOD" "${CLAUDE_PROJECT_DIR:-$PWD}" "$STROJ" 2>/dev/null || exit 0
exit 0
