#!/usr/bin/env bash
# Kus 3 (24. 9. 2026): správa človeka stratená po limite v tom istom sedení.
# Pri odoslaní správy (UserPromptSubmit): keď je tesne pred ňou v stope predošlá správa človeka bez odpovede
# (po nej len chyba limitu alebo spojenia, alebo nič), pridá partnerovi jednu vetu, aby ju vybavil spolu s novou.
# Po limite človek často hneď napíše znova v tom istom sedení bez obnovy — štart sedenia (ng-vypadok.sh) vtedy nebeží.
# Rozbor robí ng-bez-odpovede.py (režim odoslanie) — ten istý ako pri štarte sedenia. Nič nezapisuje (ani stopu
# háčika), pri chybe mlčí s kódom 0, beží najviac 1 s. U kolegu zo spoločného miesta, bez prístupu do domova PS.
# Kus J3S (3. 10. 2026): súbežne strážca pravidla 3 — keď je správa tretie či ďalšie vrátenie tej istej veci, pripomenie
# partnerovi zamknuté znenie (ng-tretie-kolo.py straz, ~0,6 s, najviac 1,5 s — obaja sa zmestia do limitu háčika 2 s).
# Strážca pripíše riadok do .aigrip/ng/tretie-kolo-straz.jsonl v projekte; keď ho zastaví časový limit, riadok napíše
# tento háčik (vysledok „časový limit“), aby výpadok nebol tichý.
set -u
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
# v aigrip: správu, ktorú už vybavil náhradný beh budiča, nehlásiť (stav budiča je len tu; u kolegov nie je)
BS="${CLAUDE_PROJECT_DIR:-}/.aigrip/ng/budic-stav.json"; [ -r "$BS" ] && export NG_BUDIC_STAV="$BS"
[ -t 0 ] && exit 0
VSTUP=$(cat)
# strážca beží súbežne cez rúru (bez dočasného súboru); jeho výstup sa vypíše až po vete o predošlej správe
S3=
# staršia kópia stroja strážcu nemá — bez tejto kontroly by „straz“ spustil celé meranie (28 s) a čakal na limit
if grep -q 'def _straz_hacik' "$STROJ/scripts/ng-tretie-kolo.py" 2>/dev/null; then
  exec 3< <(printf '%s' "$VSTUP" | timeout 1.5 python3 "$STROJ/scripts/ng-tretie-kolo.py" straz 2>/dev/null
            if [ "${PIPESTATUS[1]}" = 124 ] && [ -w "${CLAUDE_PROJECT_DIR:-/nonexistent}/.aigrip/ng" ]; then
              printf '{"ts":"%s","vysledok":"časový limit","ms":1500}\n' "$(date -Is)" \
                >> "$CLAUDE_PROJECT_DIR/.aigrip/ng/tretie-kolo-straz.jsonl" 2>/dev/null
            fi) && S3=1
fi
printf '%s' "$VSTUP" | timeout 1 python3 "$STROJ/scripts/ng-bez-odpovede.py" odoslanie 2>/dev/null || true
[ -n "$S3" ] && { cat <&3; exec 3<&-; }
exit 0
