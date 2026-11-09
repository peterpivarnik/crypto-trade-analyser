#!/usr/bin/env bash
# Zápisník a návrhy sú append-only a majú jediného pisateľa (K-6, K-4; FD-9 prvok 1; oponentúra FD-9 #20).
# PreToolUse: Edit / Write / MultiEdit na NG-zaznamy.md alebo NAVRHY.md / navrhy-*.md = deny.
# Zápis ide výhradne cez `python3 .aigrip/scripts/ng-zapis.py` (Bash), ktorý riadok pridá a nič neprepíše.
# `deny` platí vo všetkých režimoch nástroja vrátane bypass (na rozdiel od `ask`) — preto je to zároveň
# prvý test primitíva, na ktorom má stáť prvok 7 (kotvy ako kód).
# Pohľady stroja (25. 9. 2026): partner Petra Tótha 21. 9. 11:56 a 12:12 prepísal v cp-controlling pohľad „kde stojíme“
# rukou (Python write_text), aby doň dostal vetu „kde som“. Pohľad kreslí len stroj (časť 2: renderuje sa, neudržiava).
# Edit / Write / MultiEdit na .aigrip/ng/<pohľad> = deny s odkazom na to, odkiaľ sa pohľad kreslí. Pohľady = súbory, ktoré
# celé prepisuje len kresliaci skript: KDE-STOJIME.md (ng-kdestojime.py), MAPA.md (ng-mapa.py) v každom projekte; v aigrip
# STAV.md (ng-stav.py), celok.md, postup.md, etapy.md, forecast.md, dekompozicia.md, odislo-von.md (ng-<meno>.py)
# a namietky.md — čitateľný výpis námietok nezávislých behov pre človeka (ng-p5-straz.py vypis-namietok, kus P6V,
# 11. 10. 2026): dôkaz o oponentúre si nesmie napísať ten, koho posudzuje (to isté ako namietky.jsonl vyššie).
# Nie je pohľad: kde-som.json (píše ng-kdesom.py), fronta.md (plán ťahu, píše partner), verzia.txt, audit-*.
# Zoznam je ten istý ako v ng-zapisnik-bash.sh (POHLADY). Kresliace skripty nič z toho neblokuje — píšu mimo nástrojov.
# Nové súbory starých druhov (25. 9. 2026): pilotný text hovorí „staré súbory (INTENT.md, map/, D/L/Q/J) číta ako podklad;
# nové takéto súbory nevytvára“ — meranie P-nic-ine ho videlo porušené. Edit / Write / MultiEdit, ktorý by ZALOŽIL nový
# <projekt>/map/decisions/D-*.md, map/learnings/L-*.md, map/questions/Q-*.md, map/journal/J-*.md alebo <projekt>/INTENT.md
# v projekte nového spôsobu (má NG-zaznamy.md), = deny s vetou, kam taká veta patrí (pomocný J-….md.tmp záznam nie je).
# Úprava existujúceho starého súboru sem nepatrí (od 9. 10. ju stráži časť „prepis“ nižšie). Kópia v /tmp alebo v skúšobnom priečinku bez NG-zaznamy.md
# prejde. Platí aj v aigrip (jeho text hovorí „žiadne D-XXX“; od prechodu 19. 9. tam nový starý súbor nevznikol).
# Ten istý cieľ stráži ng-zapisnik-bash.sh pre terminál.
# Bounded: číta jeden JSON zo stdin, nič nepočíta, nič nesieťuje (L-231).
set -u
# P6O (17. 10. 2026): časový limit aj bez GNU coreutils — na Macu PS príkaz `timeout` nie je (K2, 3. 10.; Homebrew ho dáva
# ako `gtimeout`). Volanie stráže položiek časti 4 tam vracalo 127, takže od P6K (11. 10.) zastavilo KAŽDÚ úpravu STAVBA.md
# ako „nedobehlo“ a položka prišla inou cestou bez posúdenia: 4.112, 4.118, 4.119, 4.121 a 4.122 sú všetky z Macu bez
# odseku o námietkach (pravidlo 6: 7 z 31). Ten istý strážca ako v ng-kotva.sh: timeout → gtimeout → strážca v pythone.
s_limitom() {  # s_limitom <sekundy> <príkaz> [argumenty …]
  if command -v timeout >/dev/null 2>&1; then timeout "$@"
  elif command -v gtimeout >/dev/null 2>&1; then gtimeout "$@"
  else python3 -c 'import os, signal, subprocess, sys
try:
    p = subprocess.Popen(sys.argv[2:], start_new_session=True)
except OSError:
    sys.exit(127)
try:
    r = p.wait(timeout=float(sys.argv[1]))
except subprocess.TimeoutExpired:
    try:
        os.killpg(p.pid, signal.SIGKILL)
    except OSError:
        pass
    p.wait()
    sys.exit(124)
sys.exit(r if r >= 0 else 128 - r)' "$@"
  fi
}
IN=$(cat 2>/dev/null || true)
for _b17 in "${BASH_SOURCE[0]%/*}/ng-straz-beh.sh" "${BASH_SOURCE[0]%/*}/../scripts/ng-straz-beh.sh" /opt/aigrip-ng/scripts/ng-straz-beh.sh; do [ -f "$_b17" ] && { . "$_b17" && ng_beh_stopa ng-zapisnik-guard && ng_audit_straz; break; }; done 2>/dev/null   # B17 (časť 3): stopa behu + audity len na dopĺňanie — ng-straz-beh.sh
TOOL=$(printf '%s' "$IN" | jq -r '.tool_name // empty' 2>/dev/null)
case "$TOOL" in Edit|Write|MultiEdit) ;; *) exit 0 ;; esac
F=$(printf '%s' "$IN" | jq -r '.tool_input.file_path // empty' 2>/dev/null)
# Nález 22. 9.: v izbe kolegu (iný domov na serveri) je stroj v /home/pstrocka nedostupný — stráž by zápis zakázala
# a jediná cesta zápisu by neexistovala = zápisník by sa nedal písať vôbec. Preto: keď ng-zapis.py nie je čitateľný,
# stráž zápis PUSTÍ a povie to (append-only sa tam nevymáha, kým stroj nebude dostupný zo spoločného miesta).
# EF (22. 9.): stroj beží zo spoločného miesta servera; keď chýba, z domova PS (len v jeho izbe)
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
ZAPIS="${NG_ZAPIS_PATH:-$STROJ/scripts/ng-zapis.py}"
LOG="${NG_ZAPISNIK_LOG:-/home/pstrocka/projects/aigrip/.aigrip/ng/zapisnik-guard.log}"
case "$F" in
  # P6K (11. 10. 2026): vzor bol `*/aigrip/STAVBA.md` a bash rozlišuje veľkosť písmen — klon PS na Macu je
  # `~/CLAUDE/AIGrip`, takže sa tu stráž položiek časti 4 vôbec neozvala a každá úprava prešla bez posúdenia
  # (nález 10. 10., AIG-P261010-4b04ac). Teraz sem ide KAŽDÝ súbor STAVBA.md a o tom, či je to dokument stavby
  # tohto projektu, rozhoduje určovateľ (ng-p5-straz.py `_je_stavba`), nie meno priečinka.
  */STAVBA.md)
    # P5S (3. 10.): pravidlo 5 pri vzniku položky „čaká na teba“ — nová (alebo pokazená) položka časti 4 bez dvoch možností
    # s cenou = deny (ng-p5-straz.py, ten istý určovateľ ako meranie J-5). Skript vedľa háčika (v aigrip je to živá kópia),
    # inak zo stroja; chyba, chýbajúci skript alebo viac než 5 s = pustiť.
    P5="${BASH_SOURCE[0]%/*}/../scripts/ng-p5-straz.py"; [ -f "$P5" ] || P5="$STROJ/scripts/ng-p5-straz.py"
    if [ -f "$P5" ]; then
      o=$(printf '%s' "$IN" | s_limitom 8 python3 "$P5" hook 2>/dev/null); P5RC=$?
      case "$o" in
        '{'*)
          # P6S (10. 10.): tá istá stráž zastaví aj novú položku bez odseku o námietkach nezávislého behu (pravidlo 6)
          # P6S/P6M/C4P (10. 10.): jeden dôvod môže niesť pravidlo 5, pravidlo 6 aj číslo bez pôvodu — log povie, čo zastavilo
          PKT=""; case "$o" in *'NG, pravidlo 5 ('*) PKT="pravidlo 5" ;; esac
          case "$o" in *'NG, pravidlo 6 ('*) PKT="${PKT:+$PKT a }pravidlo 6" ;; esac
          case "$o" in *'NG, čísla v položke ('*) PKT="${PKT:+$PKT a }čísla bez pôvodu" ;; esac
          PKT="${PKT:-pravidlo 5}"
          printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "$PKT $F" >> "$LOG" 2>/dev/null || true
          ( . "$STROJ/scripts/ng-hook-stopa.sh" ng-p5-straz-zasah ) >/dev/null 2>&1 || true
          printf '%s\n' "$o"; exit 0 ;;
      esac
    # P6K (11. 10. 2026): keď určovateľ NEDOBEHOL (pád, strop času), úprava dokumentu stavby neprejde ticho — ticho by
    # znamenalo, že položka posúdenie prešla. To je ten istý tvar „zlyhanie = ticho = prejde“, ktorý 10. 10. pustil
    # štyri úpravy časti 4 bez posúdenia. Ostatné súbory a ostatné chyby stráže sa ďalej púšťajú (háčik nesmie
    # zastaviť prácu pre vlastnú poruchu) — toto platí len pre dokument stavby.
      if [ "${P5RC:-0}" != 0 ]; then
        printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "pravidlo 6 nedobehlo (kód $P5RC) $F" >> "$LOG" 2>/dev/null || true
        jq -n --arg r "NG, pravidlo 6 (kus P6M): stráž položiek časti 4 pri tejto úprave NEDOBEHLA (kód $P5RC) — nemám čím posúdiť, či položka prešla nezávislou námietkou, a ticho by znamenalo, že prešla. Skús to znova; ak to padá, oprav stráž alebo urob úpravu tam, kde mechanizmus beží." \
          '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
        exit 0
      fi
    fi
    ;;
  */.aigrip/ng/stroj-nalezy.jsonl)
    # DN6 (10. 10. 2026): denník nálezov časti 6 je append-only v tom istom režime ako zápisník. Časť 6 práve preto
    # stratila dôkaz „kontrola už niečo našla“, že jej nálezy nemali kam — a keby si ten denník mohol partner prepísať
    # rukou, bol by dôkaz o jeho vlastnej kontrole znova jeho vetou. Píše ho jedine ng-stroj.py (kontrola / vypadky).
    printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "append-only $F" >> "$LOG" 2>/dev/null || true
    jq -n --arg r "Denník nálezov časti 6 (stroj-nalezy.jsonl) je append-only a píše ho jedine stroj: nález doň pripíše \`python3 $STROJ/scripts/ng-stroj.py kontrola\` (register kusov) alebo \`python3 $STROJ/scripts/ng-stroj.py vypadky\` (hlásenie výpadkov), každý ako jeden riadok s dátumom a s vlastnou vetou, čo sa našlo. Rukou ani nástrojom na úpravu súborov do neho zápis nejde (kus DN6): dôkaz, že kontrola niečo našla, si nesmie napísať ten, koho kontroluje — to je tá istá diera, ktorú 10. 10. našiel nezávislý audit biasu pri vykreslenom texte stavu." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
    exit 0
    ;;
  */.aigrip/ng/namietky.jsonl|*/.aigrip/ng/cisla-casti4.jsonl|*/.aigrip/ng/oponent.log)
    # P6M (10. 10. 2026): záznam námietky a prekreslené čísla sú append-only v tom istom režime ako zápisník — keby ich
    # partner mohol prepísať, mohol by si námietku, ktorá ho posudzuje, napísať sám (to je presne diera, ktorú našiel
    # nezávislý audit biasu 10. 10.). Píše ich jedine mechanizmus: ng-p5-straz.py oponent (námietky) a hodinový beh stavu
    # (prekreslené čísla). Deny platí vo všetkých režimoch nástroja vrátane bypass (na rozdiel od ask).
    printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "append-only $F" >> "$LOG" 2>/dev/null || true
    jq -n --arg r "Záznamy námietok (namietky.jsonl), prekreslených čísel (cisla-casti4.jsonl) a log oponentúry sú append-only a píše ich jedine mechanizmus — námietku spustíš príkazom \`python3 $STROJ/scripts/ng-p5-straz.py oponent 4.NN\` (model, počet kôl a znenie zadania určuje mechanizmus, nie ty), čísla prekresľuje hodinový beh stavu. Rukou ani nástrojom na úpravu súborov do nich zápis nejde (kus P6M): oponentúru nesmie nastavovať ten, koho posudzuje. Námietku smieš odmietnuť — odrážkou v odseku položky s dôvodom." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
    exit 0
    ;;
  */NG-zaznamy.md|*/NAVRHY.md|*/navrhy-*.md)
    if [ ! -r "$ZAPIS" ]; then
      jq -n --arg r "Stroj NG (jediná cesta zápisu) nie je z tejto izby dostupný — zápis do zápisníka rukou je povolený. Píš len na koniec súboru, jeden riadok = jeden záznam v tvare hlavičky (append-only). Toto je nález, nie norma: stroj má byť dostupný zo spoločného miesta." \
         '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"allow",permissionDecisionReason:$r}}'
      exit 0
    fi
    # stopa, aby sa dalo doložiť, že stráž naozaj zasiahla (K-8 signál zásahu)
    printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "$F" >> "$LOG" 2>/dev/null || true
    jq -n --arg r "Zápisník a návrhy sú append-only a píše ich jedine ng-zapis.py: python3 $ZAPIS <projekt> <typ> <pôvod> <kto> \"<veta>\" \"<kde platí>\" (potvrdenie návrhu: --potvrd <projekt> <kľúč>; záznam, ktorý už neplatí: --zrus <projekt> <kľúč> <pôvod> <kto> \"<prečo>\"; zmena platného záznamu: --nahrad <projekt> <kľúč> <pôvod> <kto> \"<nová veta>\" --dovod \"<prečo>\"). Priamy zápis do $F je zakázaný (K-6)." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
    ;;
esac
# Nový súbor starého druhu v projekte nového spôsobu (25. 9.): zakladá sa len to, čo ešte neexistuje; úprava prejde.
FN="$F"; case "$FN" in *//*|*/./*|*/../*|*/.|*/..) FN=$(realpath -m -s -- "$FN" 2>/dev/null || printf '%s' "$F") ;; esac   # aj map//journal (overenie 25. 9.)
if [[ "$FN" =~ ^(/.+)/map/(decisions/D|learnings/L|questions/Q|journal/J)-[^/]*\.md$ ]] || [[ "$FN" =~ ^(/.+)/INTENT\.md$ ]]; then
  KOR="${BASH_REMATCH[1]}"
  if [ -f "$KOR/NG-zaznamy.md" ] && [ ! -e "$FN" ] && [ ! -L "$FN" ]; then
    printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "nový starý záznam $FN" >> "$LOG" 2>/dev/null || true
    jq -n --arg r "V novom spôsobe sa nové rozhodnutia, poučenia, otázky ani denník nezakladajú ako samostatné súbory — zapíš jednu vetu cez ng-zapis.py (typ rozhodnutie · poznatok · zamer); staré súbory ostávajú ako podklad." \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
    exit 0
  fi
fi
# Prepis existujúceho starého súboru (9. 10., kus SZ): meranie P-nemaze ho rátalo, stráž ho púšťala — 9. 10. v aspecta-finops
# partner prepísal D-303, L-004 a Q-009. Rozhoduje ng-stare-straz.py (ten istý určovateľ ako meranie; výnimka: človek súbor
# v poslednej správe sám pomenoval). Skript vedľa háčika, v aigrip zo .aigrip/scripts, inak zo stroja; chyba alebo 5 s = pustiť.
case "$F" in
  */map/*|*/archive/v1/*|*[/-][DLQJ]-[0-9][0-9][0-9]*)
    SZ="${BASH_SOURCE[0]%/*}/ng-stare-straz.py"; [ -f "$SZ" ] || SZ="${BASH_SOURCE[0]%/*}/../scripts/ng-stare-straz.py"; [ -f "$SZ" ] || SZ="$STROJ/scripts/ng-stare-straz.py"
    if [ -f "$SZ" ]; then
      o=$(printf '%s' "$IN" | s_limitom 5 python3 "$SZ" hook 2>/dev/null) || o=""
      case "$o" in
        '{'*)
          printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "prepis starého $F" >> "$LOG" 2>/dev/null || true
          ( . "$STROJ/scripts/ng-hook-stopa.sh" ng-stare-straz-zasah ) >/dev/null 2>&1 || true
          printf '%s\n' "$o"; exit 0 ;;
      esac
    fi
    ;;
esac
# Pohľad = <domov>/projects/<projekt>/.aigrip/ng/<meno>; kópia s tým istým menom v /tmp či v skúšobnom priečinku pohľad nie je.
if [[ "$F" =~ ^/home/[^/]+/projects/[^/]+/\.aigrip/ng/(KDE-STOJIME|MAPA|STAV|celok|postup|etapy|forecast|dekompozicia|odislo-von|namietky)\.md$ ]]; then
    B=$(basename "$F")
    case "$B" in
      KDE-STOJIME.md) R="Pohľad „kde stojíme“ kreslí stroj z vety „kde som“ a zápisníka — ak ho treba zmeniť, zapíš vetu cez ng-kdesom.py alebo záznam cez ng-zapis.py; pohľad sa prekreslí sám." ;;
      MAPA.md) R="Mapu práce kreslí stroj zo zápisníka — ak ju treba zmeniť, zapíš záznam cez ng-zapis.py; mapa sa prekreslí sama." ;;
      odislo-von.md) R="Pohľad „čo odišlo von“ kreslí stroj zo záznamov a zo stôp odoslania — ak v ňom niečo chýba, zapíš záznam cez ng-zapis.py; pohľad sa prekreslí sám." ;;
      namietky.md) R="Čitateľný výpis námietok nezávislých behov kreslí stroj zo záznamov behov (ng-p5-straz.py vypis-namietok, inak hodinový beh stavu) — námietku do neho nepíš rukou a nemaž ju z neho: dôkaz o oponentúre si nesmie napísať ten, koho posudzuje (kus P6V). Námietku smieš odmietnuť — odrážkou v odseku položky s dôvodom." ;;
      *) K="${B%.md}"; R="Pohľad $B kreslí stroj z meraní a zápisníkov — ak je v ňom niečo zle, oprav v aigrip skript, ktorý ho kreslí (.aigrip/scripts/ng-${K,,}.py), alebo zapíš záznam cez ng-zapis.py; pohľad sa prekreslí sám." ;;
    esac
    printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "$TOOL" "$F" >> "$LOG" 2>/dev/null || true
    jq -n --arg r "$R (Skripty stroja sú v $STROJ/scripts.)" \
      '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
fi
exit 0
