#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Stráž prepisu starých záznamov (kus SZ, 9. 10. 2026) — jedno miesto pre strážcu aj meranie P-nemaze.

Prečo: pilotný text hovorí „staré súbory (INTENT.md, map/, D/L/Q/J) číta ako podklad; nič nemaže ani neprepisuje“.
Meranie (ng-klauzuly.py, P-nemaze) úpravu existujúceho starého súboru rátalo od 29. 9., stráž zápisníka
(ng-zapisnik-guard.sh) ju však zámerne púšťala — od 25. 9. (kus HW) zastavovala len ZALOŽENIE nového D/L/Q/J.
9. 10. 10:34 v aspecta-finops tak partner na pokyn „oprav na správne znenie“ prepísal D-303 (stav LOCKED → NAHRADENÉ),
L-004 a dvakrát Q-009, hoci novú platnosť už mal zapísanú vetou v zápisníku.

Čo robí: Edit / Write / MultiEdit, ktorý mení EXISTUJÚCI starý súbor (cesta podľa toho istého určovateľa ako meranie:
map/decisions|learnings|questions|journal, archive/v1, D/L/Q/J-NNN; mimo pieskoviska) v projekte nového spôsobu
(projekt súboru alebo projekt sedenia má NG-zaznamy.md) = deny s vetou, kam patrí nová platnosť (ng-zapis.py).
Výnimka (pilotný text: „ak niektoré protirečí tomuto textu, platí to, čo povieš jednou vetou“): človek ten súbor
v správe, na ktorú partner práve odpovedá, sám pomenoval — číslom (D-303) alebo menom súboru. Vtedy prejde a meranie
ho ako zásah neráta. KQS (13. 10.): správou, na ktorú partner odpovedá, je posledná bežná správa človeka a s ňou aj správy,
ktoré človek napísal po nej, kým partner pracoval (príloha queued_command, človeka v nej určí ng-akter.py). Nový súbor starého druhu sem nepatrí (zastaví ho stráž zápisníka, kus HW). Chyba = pustiť.

  ng-stare-straz.py hook     ← JSON PreToolUse zo stdin; vypíše deny JSON alebo nič
  ng-stare-straz.py skuska      ← červená skúška určovateľa v pieskovisku; rc 0 = všetko sedí
  ng-stare-straz.py skuska-hak  ← celý háčik ng-zapisnik-guard.sh v pieskovisku (zapojenie, stopa, záznam)
"""
import os, io, re, sys, json, tempfile, shutil

# Ten istý určovateľ ako meranie do 9. 10. (ng-klauzuly.py STARY_RE, PIESKOVISKO_RE) — meranie ho odteraz berie odtiaľto.
STARY_RE = re.compile(r'/(?:map/(?:decisions|learnings|questions|journal)|archive/v1)/|[/-](?:D|L|Q|J)-\d{3}')
PIESKOVISKO_RE = re.compile(r'^(?:/tmp|/var/tmp|/dev/shm)(?:/|$)|/scratchpad(?:/|$)|pieskovisk')
ID_RE = re.compile(r'(?:^|[/-])([DLQJ])-(\d{3,})')
NIE_CLOVEK = ('Stop hook feedback', 'Pokračuj v stavbe NG', 'Stavba NG beží kontinuálne', '[Request interrupted',
              'This session is being continued', 'Base directory for this skill', 'Continue from where you left off',
              '[Image', 'Caveat:', 'Náhradný beh stavby NG')   # ako ng-kotva-over.py; + zadanie náhradného behu (budič)
IDE_BLOK = ('<ide_opened_file>', '<ide_selection>')
IDE_RE = re.compile(r'^(?:\s*<(ide_opened_file|ide_selection)>.*?</\1>)+\s*', re.S)


def stary(fp):
    """Cesta je starý súbor mimo pieskoviska (rovnako ako meranie: podľa cesty, nie podľa textu úpravy)."""
    fp = str(fp or '')
    return bool(STARY_RE.search(fp)) and not PIESKOVISKO_RE.search(fp)


def sprava_cloveka(o):
    """Text správy, ktorú napísal človek (záznam stopy ako dict), inak None.

    19. 10. (sľub 1.16, FD-9b „druhý filter hlasu kdekoľvek okrem ng-akter.py“): kto hovorí, určuje JEDEN
    určovateľ (ng-akter). Dovtedy mala táto stráž vlastný filter a od určovateľa sa líšila v oboch smeroch —
    nepoznala beh stroja (claude -p) ani skúšobnú správu v živom sedení a naopak zahadzovala každý text, ktorý
    začínal „<“. Filtre nižšie ostávajú ako náhrada pre prípad, že sa určovateľ v klone kolegu nenačíta; stráž
    vtedy pracuje ako pred 19. 10., nikdy nie prísnejšie."""
    if _AK is not None:
        try:
            if _AK.akter(o) != 'clovek':
                return None
            t = _AK.text(o)
            return t if t.strip() else None
        except Exception:
            pass
    if not isinstance(o, dict) or o.get('type') != 'user' or o.get('isSidechain') or o.get('isMeta') \
            or o.get('isCompactSummary') or o.get('isVisibleInTranscriptOnly') or 'toolUseResult' in o \
            or o.get('sourceToolUseID') or o.get('promptSource') == 'system':
        return None
    orig = o.get('origin')
    if isinstance(orig, dict) and orig.get('kind') not in (None, 'human'):
        return None
    c = (o.get('message') or {}).get('content')
    if isinstance(c, list):
        if any(isinstance(b, dict) and b.get('type') == 'tool_result' for b in c):
            return None
        c = '\n'.join(b.get('text', '') for b in c if isinstance(b, dict) and b.get('type') == 'text'
                      and not b.get('text', '').lstrip().startswith(IDE_BLOK))
    if isinstance(c, str):
        c = IDE_RE.sub('', c)
    if not isinstance(c, str) or not c.strip() or c.lstrip().startswith('<') or c.lstrip().startswith(NIE_CLOVEK):
        return None
    return c


# KQS (13. 10.): správu, ktorú človek napíše, kým partner pracuje, Claude Code nezapíše ako správu používateľa, ale ako
# prílohu queued_command — stráž ju dovtedy nevidela, a tak by zastavila úpravu, ktorú človek práve v nej pomenoval (kotva
# mala tú istú slepotu, kus KQM). Či je v prílohe hlas človeka, určuje len ng-akter.py (clovek_vo_fronte: režim „prompt“,
# pôvod „human“, nie skúška stroja, nie podagent). Súbor VEDĽA tohto skriptu, inak spoločné miesto stroja
# /opt/aigrip-ng/scripts; kde sa nenájde, príloha sa neberie (ako pred 13. 10.). Výnimku vie len rozšíriť, nikdy zastaviť.
# Za 14 dní do 13. 10.: 83 úprav starých súborov, 4 so správou vo fronte, žiadna v nej súbor nepomenovala, stráž od 9. 10.
# nezastavila nič — oprava je lacnejšia než spúšťač, ktorý by na tú chvíľu čakal (ten istý kód).
_AK = None
for _p in (os.path.dirname(os.path.abspath(__file__)), '/opt/aigrip-ng/scripts'):
    try:
        import importlib.util as _ilu
        _s = _ilu.spec_from_file_location('ng_akter_stare', os.path.join(_p, 'ng-akter.py'))
        _m = _ilu.module_from_spec(_s)
        _s.loader.exec_module(_m)
        if hasattr(_m, 'clovek_vo_fronte'):
            _AK = _m
            break
    except BaseException:
        continue


def sprava_vo_fronte(o):
    """Text správy, ktorú človek napísal počas práce partnera (príloha queued_command), inak None. Chyba = None."""
    if _AK is None or not isinstance(o, dict) or o.get('type') != 'attachment':
        return None
    try:
        t = _AK.clovek_vo_fronte(o)
    except Exception:
        return None
    if not t or not t.strip() or t.lstrip().startswith(NIE_CLOVEK):
        return None
    return t


def menoval(text, fp):
    """Človek v texte pomenoval súbor fp: číslom (D-303, aj D303) alebo menom súboru bez prípony. KQS: text môže byť aj
    zoznam správ, na ktoré partner odpovedá (spravy_v_tahu) — stačí jedna z nich."""
    if isinstance(text, (list, tuple)):
        return any(menoval(t, fp) for t in text)
    if not text:
        return False
    t = text.lower()
    base = os.path.basename(str(fp))
    stem = base[:-3] if base.lower().endswith('.md') else base
    m = ID_RE.search('/' + base)
    if m and re.search(r'(?<![a-z0-9])' + m.group(1).lower() + r'-?' + m.group(2) + r'(?!\d)', t):
        return True
    return len(stem) >= 4 and stem.lower() in t


def posledna_sprava_cloveka(stopa):
    """Posledná správa človeka v stope sedenia (tá, na ktorú partner práve odpovedá); None, keď sa nedá prečítať."""
    posl = None
    try:
        with io.open(stopa, encoding='utf-8', errors='replace') as f:
            for l in f:
                if '"user"' not in l or 'tool_use_id' in l:
                    continue
                try:
                    s = sprava_cloveka(json.loads(l))
                except Exception:
                    continue
                if s is not None:
                    posl = s
    except Exception:
        return None
    return posl


def spravy_v_tahu(stopa):
    """KQS: správy človeka, na ktoré partner práve odpovedá — posledná bežná správa a po nej správy napísané počas práce
    partnera (v poradí, v akom ich partner dostal); [] bez správy, None, keď sa stopa nedá prečítať."""
    von = []
    try:
        with io.open(stopa, encoding='utf-8', errors='replace') as f:
            for l in f:
                if 'queued_command' in l:
                    try:
                        q = sprava_vo_fronte(json.loads(l))
                    except Exception:
                        q = None
                    if q is not None:
                        von.append(q)
                    continue
                if '"user"' not in l or 'tool_use_id' in l:
                    continue
                try:
                    s = sprava_cloveka(json.loads(l))
                except Exception:
                    continue
                if s is not None:
                    von = [s]
    except Exception:
        return None
    return von


def _ng_projekt(d):
    """Najbližší priečinok smerom hore s NG-zaznamy.md (projekt nového spôsobu), inak None."""
    d = os.path.abspath(d or '/')
    while True:
        if os.path.isfile(os.path.join(d, 'NG-zaznamy.md')):
            return d
        hore = os.path.dirname(d)
        if hore == d:
            return None
        d = hore


def posud(inp):
    """(fp, dôvod) pri zastavení, inak None. inp = JSON PreToolUse."""
    if inp.get('tool_name') not in ('Edit', 'Write', 'MultiEdit'):
        return None
    fp = str((inp.get('tool_input') or {}).get('file_path') or '')
    if not fp.startswith('/'):
        return None
    fp = os.path.normpath(fp)
    if not stary(fp) or not os.path.isfile(fp):     # nový súbor starého druhu stráži kus HW
        return None
    proj = _ng_projekt(os.path.dirname(fp)) or _ng_projekt(os.environ.get('CLAUDE_PROJECT_DIR') or inp.get('cwd') or '')
    if not proj:
        return None
    if menoval(spravy_v_tahu(inp.get('transcript_path') or ''), fp):
        return None
    base = os.path.basename(fp)
    m = ID_RE.search('/' + base)
    meno = f'{m.group(1)}-{m.group(2)}' if m else base
    return fp, (f'Starý záznam {meno} je v novom spôsobe len podklad — neprepisuje sa ani nemaže, ani inou cestou '
                f'(príkazom, skriptom). Ak už neplatí alebo sa mení, zapíš novú platnosť jednou vetou cez '
                f'ng-zapis.py (typ rozhodnutie alebo poznatok, s tým, čo nahrádza) — novší záznam v zápisníku platí '
                f'pred starým súborom; človeku to povedz vetou „Zapísal som: …“. Nepýtaj sa ho na to. Prejde to len '
                f'vtedy, keď človek v správe, na ktorú odpovedáš, sám žiada zmenu tohto súboru a pomenuje ho '
                f'(napr. „{meno}“).')


def hook():
    try:
        inp = json.loads(sys.stdin.read() or '{}')
        r = posud(inp)
    except Exception:
        return 0
    if r:
        print(json.dumps({'hookSpecificOutput': {'hookEventName': 'PreToolUse', 'permissionDecision': 'deny',
                                                 'permissionDecisionReason': r[1]}}, ensure_ascii=False))
    return 0


def _mimo_tmp():
    """Priečinok skúšky mimo /tmp: pieskovisko (/tmp, scratchpad) určovateľ zámerne neráta, takže skúška v ňom by nevidela
    nič. Skutočný domov používateľa (nie $HOME — súhrnné červené testy ho presmerujú do /tmp), ~/.cache; po skúške sa zmaže."""
    import pwd
    c = os.path.join(pwd.getpwuid(os.getuid()).pw_dir, '.cache')
    os.makedirs(c, exist_ok=True)
    return c


def skuska():
    sb = tempfile.mkdtemp(prefix='ng-stare-straz-', dir=_mimo_tmp())
    try:
        ng = os.path.join(sb, 'home', 'x', 'projects', 'ngp')     # projekt nového spôsobu
        st = os.path.join(sb, 'home', 'x', 'projects', 'stary')   # projekt po starom
        for d in (ng, st):
            os.makedirs(os.path.join(d, 'map', 'decisions'))
            os.makedirs(os.path.join(d, 'archive', 'v1'))
            os.makedirs(os.path.join(d, 'work'))
        io.open(os.path.join(ng, 'NG-zaznamy.md'), 'w').write('| hlavička |\n')
        for d in (ng, st):
            for f in ('map/decisions/D-303_2026-09-01_register.md', 'archive/v1/stary-prikaz.md', 'work/agenda.md'):
                io.open(os.path.join(d, f), 'w').write('x\n')
        D = os.path.join(ng, 'map/decisions/D-303_2026-09-01_register.md')

        def stopa(*spravy):
            p = os.path.join(sb, f'stopa{len(os.listdir(sb))}.jsonl')
            with io.open(p, 'w', encoding='utf-8') as f:
                for s in spravy:
                    f.write(json.dumps(s if isinstance(s, dict) else
                                       {'type': 'user', 'message': {'role': 'user', 'content': s}}, ensure_ascii=False) + '\n')
            return p

        def vstup(fp, tr, tool='Edit', cwd=None):
            return {'tool_name': tool, 'tool_input': {'file_path': fp, 'old_string': 'x', 'new_string': 'y'},
                    'transcript_path': tr, 'cwd': cwd or ng}
        bez = stopa('oprav na správne znenie, aby z toho nevychádzalo iné vyhodnotenie')
        tool_res = {'type': 'user', 'message': {'content': [{'type': 'tool_result', 'tool_use_id': 't1', 'content': 'D-303'}]},
                    'toolUseResult': {}}

        def fronta(t, **k):   # KQS: správa napísaná počas práce partnera (tvar zo stopy 5. 10.)
            a = {'type': 'queued_command', 'prompt': [{'type': 'text', 'text': t}], 'commandMode': 'prompt',
                 'origin': {'kind': 'human'}, 'humanTurn': True}
            a.update(k.pop('a', {}))
            return dict({'type': 'attachment', 'isSidechain': False, 'attachment': a, 'timestamp': '2026-10-13T10:00:00Z'}, **k)
        P = [  # (popis, vstup, má zastaviť)
            ('úprava D-303 v projekte NG bez slova človeka (9. 10. aspecta-finops)', vstup(D, bez), True),
            ('prepis (Write) existujúceho D-303', vstup(D, bez, 'Write'), True),
            ('MultiEdit existujúceho D-303', vstup(D, bez, 'MultiEdit'), True),
            ('človek povedal „oprav aj D-303“', vstup(D, stopa('oprav aj D-303')), False),
            ('človek povedal „schválenie D303“ (bez pomlčky)', vstup(D, stopa('schválenie D303')), False),
            ('človek pomenoval súbor menom', vstup(D, stopa('uprav D-303_2026-09-01_register')), False),
            ('človek pomenoval D-3030, nie D-303', vstup(D, stopa('pozri D-3030')), True),
            ('D-303 len vo výsledku nástroja, nie od človeka', vstup(D, stopa('oprav to', tool_res)), True),
            ('D-303 len v pripomienke háčika', vstup(D, stopa('oprav to', 'Stop hook feedback: D-303 …')), True),
            ('D-303 v staršej správe, posledná ho nemenuje', vstup(D, stopa('oprav D-303', 'pokračuj')), True),
            ('archív starých príkazov v projekte NG', vstup(os.path.join(ng, 'archive/v1/stary-prikaz.md'), bez), True),
            ('archív — človek ho pomenoval', vstup(os.path.join(ng, 'archive/v1/stary-prikaz.md'), stopa('uprav stary-prikaz')), False),
            ('pracovný dokument v projekte NG', vstup(os.path.join(ng, 'work/agenda.md'), bez), False),
            ('starý súbor v projekte po starom, sedenie tiež', vstup(os.path.join(st, 'map/decisions/D-303_2026-09-01_register.md'), bez, cwd=st), False),
            ('starý súbor v projekte po starom, sedenie v projekte NG', vstup(os.path.join(st, 'map/decisions/D-303_2026-09-01_register.md'), bez, cwd=ng), True),
            ('nový D-304 (stráži kus HW, nie táto)', vstup(os.path.join(ng, 'map/decisions/D-304_novy.md'), bez, 'Write'), False),
            ('kópia v pieskovisku', vstup(os.path.join(sb, 'scratchpad', 'map/decisions/D-303_x.md'), bez), False),
            ('relatívna cesta (nástroj ju nedá) = pustiť', vstup('map/decisions/D-303_2026-09-01_register.md', bez), False),
            ('čítanie (Read) sa nestráži', vstup(D, bez, 'Read'), False),
            ('stopa sa nedá prečítať = bez slova človeka', vstup(D, os.path.join(sb, 'nie-je.jsonl')), True),
            # KQS (13. 10.): správa, ktorú človek napísal, kým partner pracoval (príloha queued_command)
            ('KQS: človek pomenoval D-303 v správe napísanej počas práce partnera', vstup(D, stopa('oprav to', fronta('áno, oprav aj D-303'))), False),
            ('KQS: D-303 v bežnej správe, po nej vo fronte iná prosba', vstup(D, stopa('oprav D-303', fronta('a pridaj aj dátum'))), False),
            ('KQS: D-303 vo fronte pred poslednou bežnou správou', vstup(D, stopa(fronta('oprav D-303'), 'pokračuj')), True),
            ('KQS: D-303 vo fronte z iného sedenia', vstup(D, stopa('oprav to', fronta('oprav D-303', a={'origin': {'kind': 'peer'}}))), True),
            ('KQS: D-303 vo fronte podagenta', vstup(D, stopa('oprav to', fronta('oprav D-303', isSidechain=True))), True),
            ('KQS: D-303 v hlásení úlohy na pozadí vo fronte', vstup(D, stopa('oprav to', fronta('<task-notification>D-303 hotové</task-notification>'))), True),
            ('KQS: D-303 vo fronte ako zadanie náhradného behu', vstup(D, stopa('oprav to', fronta('Náhradný beh stavby NG: oprav D-303'))), True),
            ('KQS: D-303 vo fronte v príkaze /… (nie režim prompt)', vstup(D, stopa('oprav to', fronta('oprav D-303', a={'commandMode': 'bash'}))), True),
        ]
        os.makedirs(os.path.join(sb, 'scratchpad', 'map', 'decisions'))
        io.open(os.path.join(sb, 'scratchpad', 'map/decisions/D-303_x.md'), 'w').write('x\n')
        P.append(('kópia v /tmp sa neráta (skúšobný priečinok)', {'tool_name': 'Edit', 'tool_input': {
            'file_path': '/tmp/ng-x/map/decisions/D-303_x.md'}, 'transcript_path': bez, 'cwd': ng}, False))
        zle = 0
        stare_env = os.environ.pop('CLAUDE_PROJECT_DIR', None)
        try:
            for popis, inp, ma in P:
                je = bool(posud(inp))
                ok = je == ma
                zle += not ok
                print(('✓' if ok else '✗') + f' {popis}: {"zastaví" if je else "pustí"}')
        finally:
            if stare_env is not None:
                os.environ['CLAUDE_PROJECT_DIR'] = stare_env
        print(f'{len(P) - zle}/{len(P)}')
        return 1 if zle else 0
    finally:
        shutil.rmtree(sb, ignore_errors=True)


def skuska_hak():
    """Celý háčik ng-zapisnik-guard.sh (nie len tento skript): zapojenie, poradie vetiev, stopa a záznam zásahu."""
    import subprocess
    tu = os.path.dirname(os.path.abspath(__file__))
    hak = next((h for h in (os.path.join(tu, 'ng-zapisnik-guard.sh'), os.path.join(tu, '..', 'hooks', 'ng-zapisnik-guard.sh'))
                if os.path.isfile(h)), None)
    if not hak:
        print('háčik ng-zapisnik-guard.sh sa nenašiel')
        return 1
    sb = tempfile.mkdtemp(prefix='ng-stare-hak-', dir=_mimo_tmp())
    try:
        ng = os.path.join(sb, 'projects', 'ngp')
        os.makedirs(os.path.join(ng, 'map', 'decisions'))
        os.makedirs(os.path.join(ng, 'work'))
        for f in ('NG-zaznamy.md', 'map/decisions/D-303_2026-09-01_register.md', 'work/agenda.md'):
            io.open(os.path.join(ng, f), 'w').write('x\n')
        tr = {}
        for k, s in (('bez', 'oprav na správne znenie'), ('so', 'áno, oprav aj D-303')):
            tr[k] = os.path.join(sb, k + '.jsonl')
            io.open(tr[k], 'w', encoding='utf-8').write(json.dumps({'type': 'user', 'message': {'role': 'user', 'content': s}},
                                                                   ensure_ascii=False) + '\n')
        tr['fronta'] = os.path.join(sb, 'fronta.jsonl')   # KQS: slovo človeka v správe napísanej počas práce partnera
        io.open(tr['fronta'], 'w', encoding='utf-8').write(
            json.dumps({'type': 'user', 'message': {'role': 'user', 'content': 'oprav na správne znenie'}}, ensure_ascii=False) + '\n'
            + json.dumps({'type': 'attachment', 'attachment': {'type': 'queued_command', 'prompt': [{'type': 'text', 'text':
                          'áno, oprav aj D-303'}], 'commandMode': 'prompt', 'origin': {'kind': 'human'}}}, ensure_ascii=False) + '\n')
        log, stopy = os.path.join(sb, 'guard.log'), os.path.join(sb, 'stopy.jsonl')
        env = dict(os.environ, NG_ZAPISNIK_LOG=log, NG_HOOK_STOPY=stopy, CLAUDE_PROJECT_DIR=ng)
        D = os.path.join(ng, 'map/decisions/D-303_2026-09-01_register.md')

        def beh(fp, t, tool='Edit'):
            inp = {'tool_name': tool, 'tool_input': {'file_path': fp, 'old_string': 'x', 'new_string': 'y'},
                   'transcript_path': tr[t], 'cwd': ng}
            o = subprocess.run(['bash', hak], input=json.dumps(inp), capture_output=True, text=True, env=env, timeout=20).stdout
            try:
                return json.loads(o)['hookSpecificOutput']['permissionDecision']
            except Exception:
                return 'pustí' if not o.strip() else 'iné: ' + o[:80]
        P = [('úprava D-303 bez slova človeka', beh(D, 'bez'), 'deny'),
             ('prepis D-303 (Write) bez slova človeka', beh(D, 'bez', 'Write'), 'deny'),
             ('úprava D-303, človek ho pomenoval', beh(D, 'so'), 'pustí'),
             ('úprava D-303, človek ho pomenoval počas práce partnera (KQS)', beh(D, 'fronta'), 'pustí'),
             ('pracovný dokument', beh(os.path.join(ng, 'work/agenda.md'), 'bez'), 'pustí'),
             ('nový D-304 (vetva HW ostáva)', beh(os.path.join(ng, 'map/decisions/D-304_novy.md'), 'bez', 'Write'), 'deny'),
             ('zápisník rukou (vetva K-6 ostáva)', beh(os.path.join(ng, 'NG-zaznamy.md'), 'bez'), 'deny')]
        zle = 0
        for popis, je, ma in P:
            zle += je != ma
            print(('✓' if je == ma else '✗') + f' {popis}: {je}')
        zaznam = io.open(log, encoding='utf-8').read() if os.path.exists(log) else ''
        stopa = io.open(stopy, encoding='utf-8').read() if os.path.exists(stopy) else ''
        # stopa zásahu: len keď ng-hook-stopa.sh stroja, ktorý háčik použije, berie NG_HOOK_STOPY (súhrnné červené testy
        # ho nahrádzajú náhradou, ktorá píše do svojho pieskoviska — tam sa stopa nedá prečítať odtiaľto)
        stroj = os.environ.get('NG_STROJ') or '/opt/aigrip-ng'
        stroj = stroj if os.path.isdir(os.path.join(stroj, 'scripts')) else '/home/pstrocka/projects/aigrip/.aigrip'
        try:
            hs = 'NG_HOOK_STOPY' in io.open(os.path.join(stroj, 'scripts', 'ng-hook-stopa.sh'), encoding='utf-8').read()
        except Exception:
            hs = False
        for popis, ok in (('záznam zásahu v logu stráže (2 prepisy)', zaznam.count('prepis starého') == 2),
                          ('stopa zásahu ng-stare-straz-zasah' + ('' if hs else ' (stroj háčikov ju píše inam — nekontrolujem)'),
                           stopa.count('ng-stare-straz-zasah') == 2 if hs else True)):
            zle += not ok
            print(('✓' if ok else '✗') + f' {popis}')
        print(f'{len(P) + 2 - zle}/{len(P) + 2}')
        return 1 if zle else 0
    finally:
        shutil.rmtree(sb, ignore_errors=True)


if __name__ == '__main__':
    a = sys.argv[1] if len(sys.argv) > 1 else ''
    sys.exit(hook() if a == 'hook' else skuska() if a == 'skuska' else skuska_hak() if a == 'skuska-hak'
             else (print(__doc__) or 2))
