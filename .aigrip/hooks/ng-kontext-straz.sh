#!/usr/bin/env bash
# PreToolUse hook balíka NG — pravidlo 2 „vyhľadaj kontext pred konaním“ (zamknuté 21. 9.; kus FX, 23. 9.; oprava J2O, 24. 9.).
# Znenie: „Než partner v projekte prvý raz niečo zapíše alebo zmení, prečíta si, čo o veci už platí: zápisník projektu
# a pohľad ‚kde stojíme‘.“ Pri PRVOM zápise alebo zmene v projekte v sedení, keď partner ešte nečítal zápisník ani pohľad,
# zápis RAZ zastaví s prosbou prečítať. Potom v sedení mlčí (značka v /tmp).
# 24. 9. (J2O): do vtedy kontroloval z Bash len ng-zapis.py — zápisy cez cat >, python, sed -i, cp či curl -o prešli
# (7 z 11 skutočných prípadov za 18.–22. 9.), zastavil aj ng-zapis.py --help a za čítanie bral hocijakú zmienku zápisníka.
# Teraz zápis aj čítanie určuje ten istý blok ako meranie J-2 (ng-klauzuly.py ho číta odtiaľto).
# Nezastaví: zápis do /tmp a pomocného priečinka sedenia, pamäť ani cudzie repo, podagenta, beh bez človeka (claude -p);
# chyba hooku = pustiť.
set -u
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
# Vstup hooku ide Pythonu cez stdin, nie ako argument: príkaz nad 128 kB (veľký cat > dokument) by inak spadol a prešiel.
IFS= read -r -d '' KOD <<'PY' || true
# --- ng-zapis-v-projekte:zaciatok ---
import os, re
# Jediný určovateľ „zápis alebo zmena v projekte“ a „čítanie zápisníka alebo pohľadu“ pre pravidlo 2 (kus J2O, 24. 9.):
# používa ho strážca (tento súbor) aj meranie J-2 (ng-klauzuly.py si tento blok načíta odtiaľto), aby rátali to isté.
# Zápis = Write/Edit do projektu, presmerovanie > >> tee, sed -i, cp/mv/rm, curl -o, git commit, ng-zapis.py pre tento
# projekt, Python so zápisom do cesty v projekte. Nie je zápis: /tmp a pomocný priečinok sedenia, pamäť, cudzie repo,
# znak > v kóde, regexe, úvodzovkách či v tele heredoc, v (( … )) a [[ … ]], ng-zapis.py --help, rsync/scp na iný stroj.
# Čítanie (oprava skeptika 24. 9.) = čítací príkaz (Read, Grep, cat, head, tail, grep, sed bez -i, less, awk, git show/diff/log…)
# nad zápisníkom alebo pohľadom TOHTO projektu, alebo výpis pohľadu ng-kdestojime.py pre tento projekt. Nie je čítanie:
# ls, wc, stat, echo s menom súboru, git add, zápis >> či tee do zápisníka, zápisník iného projektu.
KONTEXT_RE = re.compile(r'NG-zaznamy\.md|KDE-STOJIME\.md|STAVBA\.md|NG-POSTUP-A-STAV\.md|NAVRHY\.md|kandidati-')
_HD = re.compile(r"<<-?\s*(['\"]?)([A-Za-z_]\w*)\1")
_STR = re.compile(r"""[rbfRBF]{0,2}(['"])((?:\\.|(?!\1).)*)\1""")
_PYSLOVA = {'os', 'path', 'join', 'Path', 'pathlib', 'str', 'open', 'io', 'expanduser', 'abspath', 'realpath', 'normpath',
            'dirname', 'resolve', 'encoding', 'newline', 'mode', 'errors', 'with_suffix', 'parent', 'self'}
_PYC = [re.compile(r"""open\(\s*([^,()]+?)\s*,\s*(?:mode\s*=\s*)?['"][rbt+]*[wax]"""),
        re.compile(r"""([\w.]+)\.(?:write_text|write_bytes)\("""),
        re.compile(r"""Path\(\s*([^()]+?)\s*\)\.(?:write_text|write_bytes|open\(\s*['"][wax])"""),
        re.compile(r"""([\w.]+)\.open\(\s*['"][wax]"""),
        re.compile(r"""\.(?:to_csv|to_excel|save)\(\s*([^,()]+?)\s*[,)]"""),
        re.compile(r"""(?:os\.replace|os\.rename|shutil\.copy\w*|shutil\.move)\(\s*[^,]+,\s*([^,()]+?)\s*\)"""),
        re.compile(r"""(?:os\.remove|os\.unlink)\(\s*([^,()]+?)\s*\)""")]


def _telá(cmd):
    """Príkaz bez tiel heredoc + zoznam (riadok s <<, telo)."""
    L = cmd.split('\n'); von = []; tela = []; i = 0
    while i < len(L):
        ln = L[i]; von.append(ln); i += 1
        for m in _HD.finditer(ln):
            d = m.group(2); b = []
            while i < len(L) and L[i].strip() != d:
                b.append(L[i]); i += 1
            i += 1; tela.append((ln, '\n'.join(b)))
    return '\n'.join(von), tela


def _prikazy(c):
    """Shell na jednoduché príkazy mimo úvodzoviek: [(slová, ciele presmerovania >)]; # komentár sa zahodí."""
    P = []; w = []; r = []; t = None; i = 0; n = len(c); cak = None
    def slovo(x):
        nonlocal cak
        if cak is not None: r.append((cak, x)); cak = None
        else: w.append(x)
    def koniec():
        nonlocal w, r
        if w or r: P.append((w, r))
        w = []; r = []
    while i < n:
        ch = c[i]
        if t is None and (c.startswith('((', i) or (c.startswith('[[', i) and (i + 2 == n or c[i + 2] in ' \t'))):
            k = c.find('))' if ch == '(' else ']]', i + 2)          # aritmetika a test: > je porovnanie, nie zápis
            i = n if k < 0 else k + 2; continue
        if ch in ' \t' or (ch == '\\' and i + 1 < n and c[i + 1] == '\n'):
            if t is not None: slovo(t); t = None
            i += 1 if ch != '\\' else 2; continue
        if ch in ';|&\n()' and not (ch == '&' and i + 1 < n and c[i + 1] == '>'):
            if t is not None: slovo(t); t = None
            koniec(); i += 1; continue
        if ch == '#' and t is None:
            while i < n and c[i] != '\n': i += 1
            continue
        if ch in '<>' or ch == '&':
            if t is not None and t.isdigit(): t = None
            elif t is not None: slovo(t); t = None
            j = i + 1 if ch != '&' else i + 2
            if ch == '<' and j < n and c[j] == '<':
                j += 1
                if j < n and c[j] == '<': j += 1
                cak = '<<'
            elif ch == '<': cak = '<' if not (j < n and c[j] == '(') else None
            else:
                if j < n and c[j] in '>|': j += 1
                if j < n and c[j] == '&': j += 1; cak = '>&'
                elif j < n and c[j] == '(': cak = None
                else: cak = '>'
            i = j; continue
        if ch in '\'"':
            k = i + 1; s = ''
            while k < n and c[k] != ch:
                if ch == '"' and c[k] == '\\' and k + 1 < n: s += c[k + 1]; k += 2; continue
                s += c[k]; k += 1
            t = (t or '') + s; i = k + 1; continue
        if ch == '\\' and i + 1 < n:
            t = (t or '') + c[i + 1]; i += 2; continue
        if ch == '$' and i + 1 < n and c[i + 1] == '(':
            d = 0; k = i + 1
            while k < n:
                if c[k] == '(': d += 1
                elif c[k] == ')':
                    d -= 1
                    if d == 0: break
                k += 1
            t = (t or '') + c[i:k + 1]; i = k + 1; continue
        t = (t or '') + ch; i += 1
    if t is not None: slovo(t)
    koniec()
    return [(w, [x for o, x in r if o == '>']) for w, r in P]


_TECH = re.compile(r'/\.aigrip/\.[^/]+$|/\.git(/|$)|/\.playwright-mcp(/|$)')
_CITAJ = {'cat', 'head', 'tail', 'grep', 'egrep', 'fgrep', 'rg', 'less', 'more', 'sed', 'awk', 'gawk', 'nl', 'tac', 'bat',
          'batcat', 'cut', 'diff', 'column', 'glow', 'view', 'sort', 'uniq'}
_OBAL = {'sudo': {'-u', '-g', '-C', '-D', '-p', '-r', '-t', '-U', '-T', '-h'}, 'env': {'-u', '-C', '-S'}, 'nice': {'-n'},
         'ionice': {'-c', '-n', '-p'}, 'stdbuf': {'-i', '-o', '-e'}, 'timeout': {'-s', '-k', '--signal', '--kill-after'},
         'command': set(), 'exec': {'-a'}, 'time': set(), 'nohup': set()}


_KONTEXT_MENO = re.compile(r'(?:NG-zaznamy|KDE-STOJIME|STAVBA|NG-POSTUP-A-STAV|NAVRHY)\.md|kandidati-[^/]*')


def _kontext(p, cwd, root):
    """p je zápisník alebo pohľad (alebo iný súbor zo zoznamu KONTEXT_RE) TOHTO projektu — celé meno súboru, nie zmienka v texte."""
    return bool(p) and bool(_KONTEXT_MENO.fullmatch(os.path.basename(p.rstrip('/')))) and _v(p, cwd, root)


def _v(p, cwd, root):
    """Leží cesta p v projekte root? Technické značky (.aigrip/.git-sync-*, .session-start-time), .git a .playwright-mcp nie
    sú obsah projektu. Nerozriešená premenná alebo neznámy priečinok (cwd '?', po cd $X) -> nie."""
    if not p: return False
    dom = '/'.join(root.split('/')[:3])
    p = p.replace('${HOME}', dom).replace('$HOME', dom)
    if p == '~' or p.startswith('~/'): p = dom + p[1:]
    if '$' in p or '`' in p or '{' in p or p.startswith('/dev/'): return False
    if not p.startswith('/') and cwd == '?': return False
    a = os.path.normpath(p if p.startswith('/') else os.path.join(cwd or root, p))
    return (a == root or a.startswith(root + '/')) and not _TECH.search(a)


def _py(code, cwd, root):
    """Python kód zapisuje do projektu: cieľ zápisovej operácie (literál priamo, cez priradenie alebo f-reťazec)
    sa rozrieši na cestu v projekte. Cieľ, ktorý sa rozriešiť nedá (sys.argv, výpočet), zápis nie je."""
    ciele = [m.group(1).strip() for R in _PYC for m in R.finditer(code)]
    def prvy(e, h=0):
        """Prvý kus cesty výrazu: literál alebo hodnota premennej (do hĺbky 3), inak None."""
        for m in re.finditer(r"""(?P<s>[rbfRBF]{0,2}(['"])(?:\\.|(?!\2).)*\2)|(?P<i>[A-Za-z_]\w*)""", e):
            if m.group('s'):
                s = _STR.fullmatch(m.group('s')).group(2)
                k = re.match(r'\{([A-Za-z_]\w*)\}', s)
                if k:
                    z = prvy(k.group(1), h + 1) if h < 3 else None
                    return None if z is None else z + s[k.end():]
                return s
            i = m.group('i')
            if i in _PYSLOVA: continue
            if h >= 3: return None
            a = re.findall(rf'^\s*{i}\s*=\s*(.+)$', code, re.M)
            return prvy(a[-1].strip(), h + 1) if a else None
        return None
    for c in ciele:
        s = prvy(c)
        if s and '\n' not in s and len(s) < 300 and not s.startswith(('http', '%')) and _v(s, cwd, root): return True
    return False


def konanie(nm, vst, cwd, root, proj=None):
    """(čítal, zápis) pre jedno volanie nástroja: čítal = pred prípadným zápisom v tom istom volaní čítal zápisník alebo
    pohľad; zápis = popis prvého zápisu alebo zmeny v projekte root, inak ''."""
    proj = proj or os.path.basename(root)
    if nm in ('Write', 'Edit', 'MultiEdit', 'NotebookEdit'):
        p = str(vst.get('file_path') or vst.get('notebook_path') or '')
        return False, (f'{nm} {p}' if _v(p, cwd, root) else '')
    if nm == 'Read':
        return _kontext(str(vst.get('file_path') or ''), cwd, root), ''
    if nm == 'Grep':
        p = str(vst.get('path') or ''); g = str(vst.get('glob') or '')
        return _kontext(p, cwd, root) or (bool(KONTEXT_RE.search(g)) and _v(p or root, cwd, root)), ''
    if nm != 'Bash': return False, ''
    cmd, tela = _telá(str(vst.get('command', '')))
    cital = False; env = {}; dom = '/'.join(root.split('/')[:3])
    def rozvin(x): return re.sub(r'\$\{?([A-Za-z_]\w*)\}?', lambda m: env.get(m.group(1), m.group(0)), x)
    for w, r in _prikazy(cmd):
        w = [rozvin(x) for x in w]
        for x in r:
            if _v(rozvin(x), cwd, root): return cital, f'> {rozvin(x)}'
        if w and w[0] in ('export', 'local', 'declare', 'readonly'): w = w[1:]
        if w and all(re.match(r'^[A-Za-z_]\w*=', x) for x in w):          # S=/tmp/…; P=~/projects/x — premenné pre ďalšie príkazy
            for x in w: k, _, h = x.partition('='); env[k] = rozvin(dom + h[1:] if h.startswith('~') else h)
            continue
        while w and (re.match(r'^\w+=', w[0]) or w[0] in _OBAL):
            o = _OBAL.get(w[0], set()); tm = w[0] == 'timeout'; w = w[1:]
            while w and w[0].startswith('-') and w[0] != '-':
                w = w[2:] if w[0] in o else w[1:]
            if tm and w: w = w[1:]                                   # trvanie timeoutu
        if not w: continue
        c0 = os.path.basename(w[0]); a = w[1:]
        arg = [x for x in a if not x.startswith('-')]
        if c0 == 'cd':
            t = arg[0] if arg else dom
            t = dom + t[1:] if t.startswith('~') else t
            cwd = '?' if ('$' in t or '`' in t) else os.path.normpath(t) if t.startswith('/') else '?' if cwd == '?' else os.path.normpath(os.path.join(cwd or root, t))
            continue
        z = a if c0.endswith('ng-zapis.py') else (a[1:] if re.match(r'python3?(\.\d+)?$', c0) and a and a[0].endswith('ng-zapis.py') else None)
        if z is not None:
            if z and z[0] not in ('--help', '-h') and '--help' not in z:
                pr = z[1] if z[0].startswith('--') and len(z) > 1 else z[0]
                if os.path.basename(os.path.normpath(pr)) == proj: return cital, 'ng-zapis.py'
            continue
        if c0 in _CITAJ and not (c0 == 'sed' and any(re.match(r'^-[a-zA-Z]*i', x) or x.startswith('--in-place') for x in a)):
            if any(_kontext(rozvin(x), cwd, root) for x in a if not x.startswith('-')): cital = True
        if c0 == 'git' and a and a[0] in ('show', 'diff', 'log', 'blame'):
            if any(_kontext(x.split(':', 1)[1], root, root) if ':' in x else _kontext(x, cwd, root) for x in a[1:] if not x.startswith('-')): cital = True
        if c0.endswith('ng-kdestojime.py') or (re.match(r'python3?(\.\d+)?$', c0) and a and a[0].endswith('ng-kdestojime.py')):
            if proj in a: cital = True
        if c0 == 'sed' and any(re.match(r'^-[a-zA-Z]*i', x) or x.startswith('--in-place') for x in a):
            f = []; skr = any(x in ('-e', '-f') or x.startswith(('--expression', '--file')) for x in a); k = 0
            while k < len(a):
                x = a[k]
                if x in ('-e', '-f'): k += 2; continue
                if x.startswith('-'): k += 1; continue
                if not skr: skr = True; k += 1; continue
                f.append(x); k += 1
            if any(_v(x, cwd, root) for x in f): return cital, 'sed -i'
        if c0 == 'tee' and any(_v(x, cwd, root) for x in arg): return cital, 'tee'
        if c0 in ('cp', 'mv', 'install', 'ln', 'rsync', 'scp') and len(arg) >= 2:
            d = a[a.index('-t') + 1] if '-t' in a and a.index('-t') + 1 < len(a) else arg[-1]
            if not re.match(r'^[\w.@-]+:', d) and _v(d, cwd, root): return cital, f'{c0} → {d}'
        if c0 in ('rm', 'unlink', 'rmdir') and any(_v(x, cwd, root) for x in arg): return cital, c0
        if c0 in ('curl', 'wget'):
            for k, x in enumerate(a):
                if x in (('-o', '--output') if c0 == 'curl' else ('-O', '--output-document')) and k + 1 < len(a) and _v(a[k + 1], cwd, root): return cital, f'{c0} -o'
        if c0 == 'git':
            g = a; d = cwd
            if len(g) > 1 and g[0] == '-C': d = g[1]; g = g[2:]
            if g and g[0] in ('commit', 'mv', 'rm', 'restore', 'apply', 'revert', 'am', 'cherry-pick') and _v(d or root, cwd, root): return cital, f'git {g[0]}'
        if re.match(r'python3?(\.\d+)?$', c0):
            if (not arg or arg[0] == '-') and any(_kontext(m.group(2), cwd, root) for ln, b in tela if re.search(r'python3?\b', ln)
                                                  for m in _STR.finditer(b) if len(m.group(2)) < 300): cital = True
            if '-c' in a and a.index('-c') + 1 < len(a) and _py(a[a.index('-c') + 1], cwd, root): return cital, 'python -c'
            if (not arg or arg[0] == '-') and any(_py(b, cwd, root) for ln, b in tela if re.search(r'python3?\b', ln)): return cital, 'python (heredoc)'
    return cital, ''
# --- ng-zapis-v-projekte:koniec ---
import sys, json
try: v = json.loads(sys.stdin.read() or '{}')
except Exception: sys.exit(0)
PD = sys.argv[1].rstrip('/')
nm = v.get('tool_name', ''); ti = v.get('tool_input') or {}
if nm not in ('Bash', 'Edit', 'Write', 'MultiEdit', 'NotebookEdit'): sys.exit(0)
sid = v.get('session_id') or ''; stopa = v.get('transcript_path') or ''
if not sid or not stopa or '/subagents/' in stopa or v.get('agent_id') or not os.path.exists(stopa): sys.exit(0)
if os.environ.get('CLAUDE_CODE_ENTRYPOINT', '').startswith('sdk'): sys.exit(0)        # beh bez človeka (claude -p)
if not os.path.exists(os.path.join(PD, 'NG-zaznamy.md')): sys.exit(0)
znacka = f'/tmp/ng-kontext-{os.getuid()}-{sid}'
if os.path.exists(znacka): sys.exit(0)
cital, zapis = konanie(nm, ti, v.get('cwd') or PD, PD)
if not zapis: sys.exit(0)                  # čítanie, /tmp, scratchpad, pamäť, cudzie repo, > v kóde — nie je zápis v projekte
citane = cital; bez_cloveka = False; povod = set()
if not citane:
    with open(stopa, errors='ignore') as f:
        for l in f:
            if re.search(r'"entrypoint":\s*"sdk', l): bez_cloveka = True; break          # claude -p, aj keď premenná chýba
            if '"turnOrigin"' in l: povod.update(re.findall(r'"turnOrigin":\s*"(\w+)"', l))
            if '"tool_use"' not in l or not (KONTEXT_RE.search(l) or 'ng-kdestojime' in l): continue
            try: o = json.loads(l)
            except Exception: continue
            for b in (o.get('message') or {}).get('content') or []:
                if isinstance(b, dict) and b.get('type') == 'tool_use' and konanie(b.get('name', ''), b.get('input') or {}, o.get('cwd') or PD, PD)[0]:
                    citane = True; break
            if citane: break
# claude -p spustený z Bash sedenia vo VS Code dedí CLAUDE_CODE_ENTRYPOINT=claude-vscode aj entrypoint v stope (skúška
# návratu 20. 9.) — pozná sa podľa toho, že žiadne zadanie v stope neprišlo od človeka (turnOrigin len „sdk“)
if 'sdk' in povod and 'human' not in povod: bez_cloveka = True
open(znacka, 'w').close()                 # raz za sedenie — či už čítal, alebo ho teraz zastavíme
if citane or bez_cloveka: sys.exit(0)
import subprocess; subprocess.run(['bash','-c',f'. {os.environ.get("STROJ_NG","")}/scripts/ng-hook-stopa.sh ng-kontext-straz-zasah'], env=dict(os.environ, CLAUDE_PROJECT_DIR=PD), capture_output=True, timeout=5)
dovod = ('NG, pravidlo 2: v tomto sedení je to prvý zápis v projekte a zápisník ani pohľad „kde stojíme“ si ešte nečítal. '
         f'Najprv si prečítaj {PD}/NG-zaznamy.md (aspoň riadky k tejto veci) a {PD}/.aigrip/ng/KDE-STOJIME.md, potom zápis zopakuj — '
         'druhý pokus už prejde. Človekovi o tom nepíš.')
print(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': 'deny', 'permissionDecisionReason': dovod}}, ensure_ascii=False))
PY
STROJ_NG="$STROJ" python3 -c "$KOD" "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || exit 0
exit 0
