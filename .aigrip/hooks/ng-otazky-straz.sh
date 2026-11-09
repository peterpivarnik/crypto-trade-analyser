#!/usr/bin/env bash
# Stop hook balíka NG — obal strážcu zbytočných otázok (pravidlo 4; STAVBA 4.51 a).
#
# Zastavovanie strážcu je od 10. 10. 2026 zrušené (PS, STAVBA 4.87 d): háčik len zapisuje riadok do stopy háčikov,
# nikdy nič nevypíše na stdout a vždy skončí kódom 0.
#
# Prečo obal: rozvoz registruje háčiky konca ťahu ako  bash "$CLAUDE_PROJECT_DIR/.aigrip/hooks/<meno>"  (ng-rozvoz.py
# registruj_stop). Súbor .py spustený cez bash skončí kódom 2 („syntax error near unexpected token“) — a kód 2 pri Stop
# znamená „zastav ťah a vráť chybu partnerovi“, pri každom konci ťahu (slučka). Do balíka (balik.stop) preto patrí TENTO
# súbor; ng-otazky-straz.py a ng-otazky.py idú vedľa neho (balik.hooky), ako ng-ohlaseny-krok.py vedľa ng-kdesom-koniec.sh.
#
# Riadok „bežal“ píše ng-hook-stopa.sh pod menom ng-otazky-straz (zásahy majú meno ng-otazky-straz-zasah) — háčik, ktorý
# sa nespustí, sa tak dá odlíšiť od háčika, ktorý nemá čo zachytiť. Adresát zásahu je partner, nie človek.
# Akákoľvek chyba = ticho pustiť ďalej, vždy kód 0; háčik na stdout nič nepíše. Python beží najviac
# NG_OTAZKY_TIMEOUT sekúnd (predvolene 10; bežne ~40 ms): zaseknutý háčik by Claude Code hlásil ako „did not respond“ —
# to ng-navrat-pri-skode.py ráta ako chybu háčika a vrátil by celú verziu stroja.
# Verzia 4: PYTHONDONTWRITEBYTECODE — import modulu by inak nechal v .aigrip/hooks/__pycache__ 128 kB .pyc, ktorý
# synchronizácia (git add -A) v repe bez __pycache__ v .gitignore pošle kolegom. Pri skúške (NG_HOOK_STOPY nastavené)
# sa riadok „bežal“ nepíše — tak ani pieskovisko bez náhrady ng-hook-stopa.sh nezapíše do skutočnej stopy háčikov.
set -u
export PYTHONDONTWRITEBYTECODE=1
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
# 26. 10. 2026: keď zapisovač stopy nie je ani na spoločnom mieste, ani v klone aigrip (to sú obe serverové
# cesty), skúsi sa kópia VEDĽA tohto háčika — tú rozváža balík (balik.hooky). Bez nej háčik na Macbooku PS
# v každom projekte okrem aigrip ticho nezapísal, že bežal, a kontrola háčikov to hlásila ako nález (sľub 2.22).
[ -n "${NG_HOOK_STOPY:-}" ] || . "$STROJ/scripts/ng-hook-stopa.sh" ng-otazky-straz 2>/dev/null || . "${0%/*}/ng-hook-stopa.sh" ng-otazky-straz 2>/dev/null || true
case "$0" in */*) TU="${0%/*}";; *) TU=.;; esac
PY="$TU/ng-otazky-straz.py"; [ -f "$PY" ] || PY="$TU/../scripts/ng-otazky-straz.py"; [ -f "$PY" ] || PY="$STROJ/scripts/ng-otazky-straz.py"
[ -f "$PY" ] || exit 0
command -v python3 >/dev/null 2>&1 || exit 0
if command -v timeout >/dev/null 2>&1; then
  timeout "${NG_OTAZKY_TIMEOUT:-10}" python3 "$PY" 2>/dev/null || true
else
  python3 "$PY" 2>/dev/null || true
fi
exit 0
