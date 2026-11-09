#!/usr/bin/env bash
# Novinky pre ľudí aj mimo servera (STAVBA 4.156 c; doplnok PS 25. 10. 2026: „dá sa to zobrazovať kolegom vo vscode
# aj keď pracujú na lokálnom počítači … aby sa o novinkách dozvedeli“).
# Na serveri ukazuje novinky pás „Čo je nové“ vo workspace a upozornenie vo VS Code (aigrip-token-notifier) — tu háčik
# mlčí. Na vlastnom počítači kolegu (klon projektu, VS Code alebo terminál) to rozšírenie nie je, preto ich povie
# partner: pri štarte sedenia dostane pustené novinky, ktoré na tomto počítači ešte nezazneli, a háčik ich hneď označí
# ako povedané ($HOME/.aigrip-ng/novinky-povedane, jedno id na riadok — každá zaznie na počítači raz, nie v každom
# projekte). Zoznam ide s balíkom stroja: ng-novinky.json vedľa tohto háčika je kópia .aigrip/ng/novinky.json z aigrip
# (rozvoz), takže nová novinka príde s najbližšou verziou stroja a pull-om kolegu. Novinka s len_server=true (vec,
# ktorá sa robí len na serveri) sa mimo servera nepovie.
# Jazyk: novinku partner povie v jazyku človeka (nie doslova; JZ5, 27. 10. 2026) — české pole nadpis_cs/text_cs v zozname, chýbajúce = slovensky s poznámkou.
# Mlčí pri chybe, pri zhustení kontextu (compact), v behu bez človeka (claude -p) a keď nie je čo povedať.
set -u
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
. "$STROJ/scripts/ng-hook-stopa.sh" ng-novinky 2>/dev/null || true
[ -r "${NG_NOVINKY_SERVER:-/opt/aigrip-ng/ng/novinky.json}" ] && exit 0   # na serveri ich ukážu workspace a VS Code
case "${CLAUDE_CODE_ENTRYPOINT:-}" in sdk*) exit 0 ;; esac
INPUT=""
if [ ! -t 0 ]; then
  # na Macu príkaz timeout nie je (K2, 3. 10.); Claude Code vstup háčika zatvára, takže cat skončí aj bez neho
  if command -v timeout >/dev/null 2>&1; then INPUT=$(timeout 1 cat 2>/dev/null); else INPUT=$(cat 2>/dev/null); fi
fi
case "$INPUT" in *'"source":"compact"'*|*'"source": "compact"'*) exit 0 ;; esac
case "$0" in */*) _H="${0%/*}";; *) _H=".";; esac
ZOZNAM="${NG_NOVINKY:-$_H/ng-novinky.json}"
[ -r "$ZOZNAM" ] || exit 0
PY=$(command -v python3 2>/dev/null || command -v python 2>/dev/null) || exit 0
"$PY" - "$ZOZNAM" "${HOME:-.}/.aigrip-ng/novinky-povedane" 2>/dev/null <<'PY' || true
import io, os, sys, json
zoznam, povedane = sys.argv[1], sys.argv[2]
d = json.load(io.open(zoznam, encoding='utf-8'))
if d.get('zapnute') is not True:
    sys.exit(0)
try:
    uz = set(l.strip() for l in io.open(povedane, encoding='utf-8'))
except OSError:
    uz = set()
nove = [n for n in d.get('novinky', []) if n.get('pustena') is True and not n.get('len_server')
        and n.get('id') and n['id'] not in uz and n.get('nadpis') and n.get('text')]
if not nove:
    sys.exit(0)
nove = sorted(nove, key=lambda n: n.get('datum', ''))[:5]   # najstaršia prvá, ako pás vo workspace
out = ['NOVINKY AIGRIP (pustil ich Peter Strocka pre ľudí, ktorí pracujú s AIGrip; STAVBA 4.156). Človek tu pracuje '
       'mimo servera, kde mu ich workspace ani VS Code samy neukážu. Na začiatku svojej najbližšej odpovede mu ich povedz '
       'samostatným blokom s nadpisom „Čo je nové v AIGrip“ (nadpis aj text v JAZYKU ČLOVEKA, nie vždy po slovensky) — '
       'každú jednou odrážkou, nadpis tučne a za ním text. Nie doslova, ale podľa významu: fakty, názvy tlačidiel a '
       'adresy ostávajú, bez skratiek a bez vlastných doplnkov. Jazyk človeka poznáš podľa toho, ako s tebou hovorí. '
       'Po slovensky použi slovenský text [SK]; po česky použi český text [CS] (je už preložený — povedz ho tak, ako je); '
       'v inom jazyku povedz význam slovenského textu v jeho jazyku. Ak pri novinke stojí „[CS] chýba“, povedz ju po slovensky '
       'a na konci odrážky pridaj v jazyku človeka poznámku „(zatím jen slovensky)“ / „(zatiaľ len po slovensky)“. '
       'Nič od neho nečakaj a pokračuj v tom, čo chce. Na tomto počítači sa už znova neukážu.']
for n in nove:
    sk = f'[SK] **{n["nadpis"].strip()}** — {n["text"].strip()}'
    if n.get('nadpis_cs') and n.get('text_cs'):
        cs = f'[CS] **{n["nadpis_cs"].strip()}** — {n["text_cs"].strip()}'
    else:
        cs = '[CS] chýba'
    out.append('• ' + sk + '\n  ' + cs)
os.makedirs(os.path.dirname(povedane), exist_ok=True)
with io.open(povedane, 'a', encoding='utf-8') as f:
    for n in nove:
        f.write(n['id'] + '\n')
sys.stdout.write('\n'.join(out) + '\n')
PY
exit 0
