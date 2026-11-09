#!/usr/bin/env bash
# PreToolUse (Read) — pri otvorení starého súboru (D/L/Q/J) povie partnerovi, ktoré záznamy ho citujú a kde hľadať ďalej
# (B16, 2. 10. 2026; R85, R87, R99; posúdenie plnej podoby, časť 2, Ch5). Logika je v ng-poruke-naraz.py.
# Rýchla cesta: čo nie je cesta do map/{decisions,learnings,questions,journal}/, skončí hneď bez pythonu (Read je
# najčastejší nástroj). Nič nezastaví, nikdy nevráti chybu: zaseknutý háčik u kolegu = návrat celej verzie stroja
# (ng-navrat-pri-skode.py), preto timeout 2 s a vždy kód 0. Pieskovisko: NG_STROJ (koreň stroja), NG_HOOK_STOPY (stopa).
set -u
export PYTHONDONTWRITEBYTECODE=1
VSTUP=""; [ -t 0 ] || VSTUP=$(timeout 1 cat 2>/dev/null || true)
case "$VSTUP" in
  *'/map/decisions/'*|*'/map/learnings/'*|*'/map/questions/'*|*'/map/journal/'*) ;;
  *) exit 0 ;;
esac
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
PY="$STROJ/scripts/ng-poruke-naraz.py"
[ -f "$PY" ] && command -v python3 >/dev/null 2>&1 || exit 0
printf '%s' "$VSTUP" | timeout 2 python3 "$PY" 2>/dev/null || true
exit 0
