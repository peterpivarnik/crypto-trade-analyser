#!/usr/bin/env bash
# Časť 6 — výpadková veta v ostatných projektoch (kus O, 20. 9. 2026).
# Hook nič nepočíta: číta jeden súbor, ktorý vykresľuje časť 2 v aigrip.
# Keď všetko beží, súbor je prázdny a hook mlčí — človek nemá počuť, že je ticho.
# B11 (po slove PS, STAVBA časť 4): keď súbor s vetami chýba alebo zostarol, hook nevie, či z toho, čo sa človeka týka,
# niečo stojí — stojí doručenie viet (vykreslenie v aigrip, kópia na spoločné miesto alebo prístup k nemu). Prehľad
# človeka to nezastaví: pri návrate si ho vykresľuje sám zo zápisníka (ng-kdesom-pripomienka.sh), veta 4 by tu bola
# nepravdivá. Ticho kontroly kontrol ide správcovi (R51): hook mlčí a zanechá stopu, ktorú kontrola hookov
# (ng-hooky-ludia.py) ukáže správcovi po ľuďoch. Do vtedy tu bola neschválená veta „neviem teraz povedať, či beží“ bez
# pokynu partnerovi — v 22 sedeniach kolegov (20.–29. 9.) ju nepovedal ani raz.
set -u
# EF (22. 9.): stroj beží zo spoločného miesta servera; keď chýba, z domova PS (len v jeho izbe)
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
# 26. 10. 2026: keď zapisovač stopy nie je ani na spoločnom mieste, ani v klone aigrip (to sú obe serverové
# cesty), skúsi sa kópia VEDĽA tohto háčika — tú rozváža balík (balik.hooky). Bez nej háčik na Macbooku PS
# v každom projekte okrem aigrip ticho nezapísal, že bežal, a kontrola háčikov to hlásila ako nález (sľub 2.22).
. "$STROJ/scripts/ng-hook-stopa.sh" ng-vypadok 2>/dev/null || . "${0%/*}/ng-hook-stopa.sh" ng-vypadok 2>/dev/null || true
# Kus A (24. 9.): správa človeka, ktorá ostala bez odpovede, lebo sedenie prerušil limit alebo obnova okna —
# pri štarte, obnove aj zhustení sedenia ju povie ako prvú vec (ng-bez-odpovede.py; pri chybe mlčí, nič nezapisuje)
VSTUP=""; [ -t 0 ] || VSTUP=$(timeout 2 cat 2>/dev/null || true)
[ -n "$VSTUP" ] && printf '%s' "$VSTUP" | timeout 3 python3 "$STROJ/scripts/ng-bez-odpovede.py" 2>/dev/null || true
# výpadková veta: z domova PS, keď je čitateľná, inak z kópie na spoločnom mieste (obnovuje sa každých 5 min)
V=/home/pstrocka/projects/aigrip/.aigrip/ng/vypadok.txt; [ -r "$V" ] || V="$STROJ/ng/vypadok.txt"
# 6. 11. 2026: tretia cesta — klon aigrip na Macu PS. Obe cesty vyššie sú serverové, takže na Macu háčik
# v žiadnom projekte vetu nemal odkiaľ prečítať a zanechával stopu „chýba súbor" (v bpw 24× za týždeň).
# Súbor tam prináša vonkajšia sonda (každých 5 min, kým sa server ozýva); keď sa neozve, súbor zostarne
# a háčik to povie stopou — nie vetou, o ktorej nevie, či platí. Inde je riadok bez účinku (cesta nie je).
[ -r "$V" ] || V="${NG_VYPADOK_MAC:-$HOME/CLAUDE/AIGrip/.aigrip/ng/vypadok.txt}"
_bez_suboru() {   # $1 = stopa pre správcu; len keď sa dá (bez spoločného miesta nie je čím ju zapísať — to ukáže kontrola hookov)
  declare -F ng_hook_stopa >/dev/null 2>&1 && ng_hook_stopa "$1" 2>/dev/null
  exit 0
}
[ -f "$V" ] || _bez_suboru ng-vypadok-chyba-subor
# Ak sa samotný súbor prestal obnovovať, nevie sa, čo platí — to je výpadok doručenia viet (stopa správcovi, nie veta).
# 6. 11. 2026: `stat -c` je GNU — na Macu PS tento riadok padol („stat: illegal option -- c") a háčik skončil
# kódom 1 s chybou na výstupe, nie vetou. Prvý pokus sa pýta po GNU, druhý po BSD; keď čas zmeny nepovie ani
# jeden, háčik nehádá (bez času sa nedá povedať, či veta platí) — zanechá stopu a mlčí.
MT=$(stat -c %Y "$V" 2>/dev/null || stat -f %m "$V" 2>/dev/null || true)
case "$MT" in ''|*[!0-9]*) _bez_suboru ng-vypadok-neznamy-cas;; esac
AGE=$(( $(date +%s) - MT ))
[ "$AGE" -gt 9000 ] && _bez_suboru ng-vypadok-stary-subor
[ -s "$V" ] && cat "$V"
exit 0
