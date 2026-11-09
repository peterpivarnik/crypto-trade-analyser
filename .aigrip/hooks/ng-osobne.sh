#!/usr/bin/env bash
# Osobné záznamy putujú s človekom (R81, K-11; B18, 2. 10. 2026) — pri štarte sedenia dostane partner osobné záznamy
# človeka, ktorý tu sedí, z jeho OSTATNÝCH projektov: len jeho (unixový účet) a len publikum „osobné“; záznamy
# organizácie nikdy. Logika je v ng-osobne.py hook (ohraničené ≤ 8 riadkov, ≤ 2 000 znakov). Nič nezapisuje do projektu;
# mlčí pri chybe, pri zhustení kontextu (compact), v behu bez človeka (claude -p) a keď človek osobné záznamy nemá.
set -u
# stroj beží zo spoločného miesta servera; keď chýba, z domova PS (len v jeho izbe) — ako ostatné háčiky balíka
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
. "$STROJ/scripts/ng-hook-stopa.sh" ng-osobne 2>/dev/null || true
case "${CLAUDE_CODE_ENTRYPOINT:-}" in sdk*) exit 0 ;; esac
INPUT=""; [ -t 0 ] || INPUT=$(timeout 1 cat 2>/dev/null || true)
printf '%s' "$INPUT" | timeout 3 python3 "$STROJ/scripts/ng-osobne.py" hook "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null || true
exit 0
