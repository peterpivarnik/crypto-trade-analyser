#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Stop hook — strážca zbytočných otázok (pravidlo 4 „otázky úmerné veci“; PS 30. 9., STAVBA 4.51 a). Verzia 7.

Keď ťah partnera končí ZBYTOČNOU otázkou na človeka — ponuka vratnej práce v projekte (A) alebo voľba tvaru výstupu
(S) — háčik to ZAPÍŠE. Nič nezastavuje: zastavovanie sa 10. 10. 2026 zrušilo (PS, STAVBA 4.87 d,
AIG-R261010-55cec9) a s ním aj režim, prepínač v manifeste (kľúč otazky_straz) a kritérium bezpečnosti. Háčik teda
vždy skončí kódom 0 bez výstupu a jeho jediný výsledok je riadok v stope háčikov.

NÁLEZ (pole „ista“ true) = zbytočný koniec, ktorý je ISTÝ (ng-otazky.py p['ista'] — po overení 2. 10. ponuka rozboru
po čisto neutrálnej správe človeka) a v tomto ťahu ešte nezapísaný; to je skutočná práca strážcu a to isté ráta
týždenné použitie (ng-pouzitie.py, riadok otazky-straz). Zbytočný koniec, ktorý istý nie je, dostane riadok
s pochybnosťou a nálezom nie je. Meranie pravidla 4 (J-4) ráta každú zbytočnú otázku bez ohľadu na to — to sa
nemení. Čo je zbytočná otázka, rozhoduje jediný modul ng-otazky.py (rozhodnutie_strazcu; posudok ráta meranie
pravidla 4); háčik nemá vlastné pravidlá, len zapíše stav sedenia a riadok. Návrat k agende (prvý ťah sedenia,
prestávka ≥ 3 h) sa neobmedzuje: ponuku dokončiť rozrobené predpisuje pilotný text.
Spúšťa ho obal ng-otazky-straz.sh (rozvoz registruje háčiky konca ťahu ako  bash "<háčik>"; súbor .py spustený cez
bash by skončil kódom 2 — a kód 2 pri Stop ťah vracia).

Riadky (bez obsahu správy okrem úryvku otázky ≤ 160 znakov) idú do tej istej stopy háčikov ako ng-hook-stopa.sh
(aigrip .aigrip/ng/hook-stopy.jsonl, ak je zapisovateľná, inak ~/.aigrip-ng/hook-stopy.jsonl — odtiaľ ich správca
zbiera); skracovanie stopy robí ng-hook-stopa.sh, tento háčik ju len dopĺňa. Obal píše riadok „bežal“ pod menom
ng-otazky-straz; zásahy majú meno ng-otazky-straz-zasah:
  {"ts","projekt","hook":"ng-otazky-straz-zasah","sid","trieda","pravidlo","otazka","akcia","ista"[,"len_rep"]
   [,"pochybnost"]}
  (kompaktný JSON ako riadky „bežal“). ista = toto je NÁLEZ strážcu (istý zbytočný koniec, prvý raz v tomto ťahu);
  False pri bez-tahu, pochybnosti, druhom náleze toho istého ťahu (uz-raz), rep-len-zapis, úniku, pokračovaní.
  pochybnost (verzia 5) = druh pochybnosti, pre ktorú zbytočný koniec nie je istý („správa“, „človek“, „meno“, „slovo“,
  „obsah“, „ľudia mimo“ …) — bez slov zo správy partnera aj človeka.
  akcia: zapis (koniec je zbytočný; či je to nález, hovorí ista) ·
         unik (úniková veta, uznaná) · pokracovanie (pokračovanie po konci, ktorý už háčik videl, stop_hook_active) ·
         chyba (raz denne na projekt; bez obsahu správy)
Pre skúšky: NG_HOOK_STOPY = cesta k stope (pieskovisko), NG_STROJ = koreň stroja,
NG_OTAZKY_ZNACKY = priečinok značiek ťahu a sedenia (inak /tmp).

Bez riadku (a vždy kód 0, bez výstupu): podagent, beh bez človeka (claude -p, sdk; ťah začatý strojom), správa bez
otázky na konci (rýchla cesta — nič nečíta), legitímna otázka, ponuka pri návrate k agende, akákoľvek chyba.
"""
import json
import os
import sys

HAK = 'ng-otazky-straz-zasah'
API_MODULU = 2                 # zmluva s ng-otazky.py (API_STRAZCU), nie verzia pravidiel; 2 = bez zastavovania (4.87 d)
AIGRIP = '/home/pstrocka/projects/aigrip/.aigrip'
AIGRIP_STOPY = AIGRIP + '/ng/hook-stopy.jsonl'


def _stroj():
    s = os.environ.get('NG_STROJ') or '/opt/aigrip-ng'
    return s if os.path.isdir(os.path.join(s, 'scripts')) else AIGRIP


def _projekt(vstup=None):
    cwd = (vstup or {}).get('cwd') if isinstance(vstup, dict) else None
    return os.path.basename(str(os.environ.get('CLAUDE_PROJECT_DIR') or cwd or os.getcwd()).rstrip('/'))[:80]


def _modul():
    """ng-otazky.py vedľa háčika (verzia z rozvozu), inak v scripts/ vedľa, inak na spoločnom mieste stroja — pokazenú
    alebo inú verziu preskočí a skúsi ďalšiu. Vráti (modul, chyba)."""
    import importlib.util
    tu = os.path.dirname(os.path.abspath(__file__))
    chyba = 'modul nenajdeny'
    for c in (os.path.join(tu, 'ng-otazky.py'), os.path.join(tu, '..', 'scripts', 'ng-otazky.py'),
              os.path.join(_stroj(), 'scripts', 'ng-otazky.py')):
        if not os.path.isfile(c):
            continue
        try:
            spec = importlib.util.spec_from_file_location('ng_otazky', c)
            m = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(m)
        except BaseException as e:            # pokazená kópia → ďalšia, nie ticho
            chyba = 'modul pokazeny: ' + type(e).__name__
            continue
        if getattr(m, 'API_STRAZCU', None) == API_MODULU and hasattr(m, 'rozhodnutie_strazcu'):
            return m, ''
        chyba = 'modul inej verzie'
    return None, chyba


def _stopa_hakov():
    f = os.environ.get('NG_HOOK_STOPY')
    if f:
        return f
    if os.access(AIGRIP_STOPY, os.W_OK):
        return AIGRIP_STOPY
    d = os.path.join(os.path.expanduser('~'), '.aigrip-ng')
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, 'hook-stopy.jsonl')


def zapis(zaznam):
    """Jeden riadok do stopy háčikov (formát ng-hook-stopa.sh + polia strážcu). Skracovanie robí ng-hook-stopa.sh."""
    import datetime
    try:
        riadok = dict(ts=datetime.datetime.now().astimezone().isoformat(timespec='seconds'), **zaznam)
        with open(_stopa_hakov(), 'a', encoding='utf-8') as h:
            h.write(json.dumps(riadok, ensure_ascii=False, separators=(',', ':')) + '\n')
    except Exception:
        pass


def _znacky():
    return os.environ.get('NG_OTAZKY_ZNACKY') or '/tmp'


def chyba(co, vstup=None):
    """Háčik, ktorý spadne alebo nenájde modul, to raz denne na projekt zapíše (bez obsahu správy) — inak by vyzeral
    ako „nič na zápis“."""
    import datetime
    try:
        projekt = _projekt(vstup)
        zn = os.path.join(_znacky(), f"ng-otazky-straz-{os.getuid()}-chyba-{projekt[:40]}-{datetime.date.today():%Y%m%d}")
        if os.path.exists(zn):
            return
        open(zn, 'w').close()
        zapis(dict(projekt=projekt, hook=HAK, akcia='chyba', chyba=str(co)[:80]))
    except BaseException:
        pass


def posud(vstup, M):
    """Rozhodnutie háčika: None (pustiť bez riadku) alebo dict z M.rozhodnutie_strazcu (akcia, ista, cesta, p, …)
    + projekt. Rozhoduje modul; háčik len zapíše stav sedenia. Oddelené od main() pre skúšky."""
    r = M.rozhodnutie_strazcu(vstup) if isinstance(vstup, dict) else None
    if not r:
        return None
    r['projekt'] = _projekt(vstup)
    stav, zmena = r['stav'], False
    if r['novy_unik']:
        stav['uniky'].append(r['kluc'])
        zmena = True
    if r['ista']:                       # nález zapísaný — druhý koniec toho istého ťahu už nálezom nie je (uz-raz)
        stav['najdene'] = (stav['najdene'] + [r['kluc']])[-200:]
        zmena = True
    if zmena:
        M.uloz_stav_strazcu(r['subor'], stav)
    r['nad_limit_unikov'] = r['nad_limit']
    return r


def main():
    try:
        vstup = json.loads(sys.stdin.buffer.read().decode('utf-8-sig', errors='replace') or '{}')
    except Exception:
        return
    if not isinstance(vstup, dict):
        return
    M, ch = _modul()
    if M is None:
        chyba(ch, vstup)
        return
    r = posud(vstup, M)
    if not r:
        return
    p = r['p']
    trieda = p['trieda']
    pravidlo = next(((o['pravidlo'] if o['trieda'] in ('A', 'S') else 'rep:' + o['pravidlo'])
                     for o in p['otazky'] if o['trieda'] in ('A', 'S') or o['rep']), '')
    if r.get('nad_limit_unikov'):
        pravidlo += ' · unik-nad-limit'
    riadok = dict(projekt=r['projekt'], hook=HAK, sid=r['sid'], trieda=trieda, pravidlo=pravidlo[:120],
                  otazka=p['uryvok'][:160], akcia=r['akcia'], ista=bool(r['ista']))
    if p.get('len_rep'):
        riadok['len_rep'] = True                      # zbytočná len pre opakovanie legitímnej otázky — nie je to nález
    if r.get('pochybnost'):
        riadok['pochybnost'] = str(r['pochybnost'])[:80]   # verzia 5: zbytočná, ale nie istá — nie je to nález
    zapis(riadok)


if __name__ == '__main__':
    try:
        main()
    except BaseException as e:
        chyba(type(e).__name__)
    sys.exit(0)
