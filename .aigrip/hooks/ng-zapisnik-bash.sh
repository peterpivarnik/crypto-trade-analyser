#!/usr/bin/env bash
# Stráž zápisníka pre príkazový riadok (kus HH, 24. 9. 2026; nález NG-S7 nezávislého overenia).
# Stráž ng-zapisnik-guard.sh chráni zápisník len pred Edit/Write/MultiEdit. Partner Petra Tótha 20.–23. 9. pripísal do
# NG-zaznamy.md v cp-controlling 125-krát cez „cat >> NG-zaznamy.md“ a raz ho prepísal Pythonom — zápisník sa zmenil na
# prepis rozhovoru mimo jedinej cesty zápisu a pohľad „kde stojíme“ tie riadky nevidí.
# PreToolUse Bash: zápis do NG-zaznamy.md / NAVRHY.md / navrhy-*.md presmerovaním, tee, sed -i alebo Pythonom = deny
# s odkazom na jedinú cestu (ng-zapis.py). Čítanie (grep, cat bez presmerovania), git a samotné ng-zapis.py prejdú.
# Keď ng-zapis.py nie je čitateľný, pustí (rovnako ako ng-zapisnik-guard.sh). Bounded, bez siete.
#
# Pohľady stroja (25. 9. 2026): ten istý partner 21. 9. 11:56 a 12:12 prepísal pohľad .aigrip/ng/KDE-STOJIME.md Pythonom
# (write_text), aby doň dostal vetu „kde som“. Pohľad kreslí len stroj. Zápis do .aigrip/ng/<pohľad> presmerovaním, tee,
# sed -i, cp/mv/install/ln/rsync na ten súbor, curl -o alebo Pythonom (open/write_text/os.replace/shutil) = deny s vetou,
# odkiaľ sa pohľad kreslí. Pohľady (ten istý zoznam ako v ng-zapisnik-guard.sh): <domov>/projects/<projekt>/.aigrip/ng/
# KDE-STOJIME.md, MAPA.md v každom projekte; STAV.md, celok.md, postup.md, etapy.md, forecast.md, dekompozicia.md,
# odislo-von.md a namietky.md (čitateľný výpis námietok nezávislých behov, kus P6V) v aigrip. Kópia s tým istým menom v /tmp či v skúšobnom priečinku pohľad nie je. Čo je zápis, neurčuje táto
# stráž sama: načíta si blok „ng-zapis-v-projekte“ zo strážcu pravidla 2 (ng-kontext-straz.sh, ten istý balík) — rovnako
# ako meranie J-2 — a mení mu len cieľ (súbor pohľadu namiesto celého projektu). Úvodzovky, telá heredoc, > v kóde
# a porovnaniach preto zápisom nie sú. Kresliace skripty (ng-kdestojime.py, ng-stav.py … z aigrip aj /opt/aigrip-ng),
# čítanie, git a zmazanie pohľadu prejdú. Príkaz, ktorý žiadny pohľad nemenuje, sa do Pythonu vôbec nedostane.
#
# STAVBA.md z príkazového riadku (kus SB, 10. 10. 2026): stráž pravidla 5 a 6 pri vzniku položky „čaká na teba“ visí len
# na PreToolUse Edit|Write|MultiEdit — zápis do STAVBA.md príkazom nevidel nikto a docstring ng-p5-straz.py to priznával.
# Zápisník dostal takú stráž (kus HH) až po tom, čo sa doň 125× pripísalo cez „cat >> NG-zaznamy.md“; tu sa to nečaká.
# Tá istá trieda ochrany (deny platí aj v režime bypass), ten istý určovateľ zápisu v bashi. Zápis do <aigrip>/STAVBA.md
# presmerovaním, tee, sed/perl -i, patch, truncate, dd, cp/mv/install/rsync na jej meno alebo Pythonom (open(…,'w'/'a'),
# write_text, writelines, os.replace/os.rename, shutil.copy/move) = deny s vetou, kadiaľ úprava ide. Vykresľovač stavu
# (ng-stav.py a generátory jeho blokov) prejde — dopisuje strojové bloky, nie položky časti 4. Čítanie (cat, grep, sed bez
# -i, git, cp STAVBA.md inam) prejde. Chyba stráže tu NIE JE tichý priechod: keď sa odpoveď nedá postaviť (chýba jq),
# stráž zastaví príkaz kódom 2 a vetu povie na chybový výstup; keď sa nedá prečítať vstup, rozhodne sa aspoň o STAVBA.md.
#
# Zápis kódom so zloženou cestou (kus KSZ 11. 10. pri STAVBA.md, kus KSZ2 11. 10. pre ostatné chránené súbory): Python,
# ktorý cieľ skladá (premenná + meno, os.path.join, f-reťazec, Path / meno, parameter funkcie, dočasný súbor a premenovanie),
# vzory ani spoločný určovateľ nevidia. Rozbor kódu (ast) vyhodnotí cieľ každej zápisovej operácie celý a zastaví zápis
# do STAVBA.md, pohľadu stroja, zápisníka, návrhov, záznamu námietok aj denníka nálezov časti 6. Od kusu KSZ3 (11. 10.)
# aj kód v JavaScripte (node -e/-p, heredoc, skript), Perle a Ruby (-e, heredoc, skript, -i na mieste): malý rozbor výrazu
# nájde cieľ writeFileSync, renameSync, open s > alebo >>, File.write, FileUtils.mv … Cieľ z glob ostáva mimo.
#
# Skript v súbore, ktorého príkaz chránený súbor nemenuje (kus KSZ4, 11. 10.): `python3 zaplata.py` alebo `node x.js`,
# kde meno chráneného súboru stojí len v texte skriptu, stráž doteraz vôbec neposudzovala (brána pozerala len príkaz).
# Odteraz sa pri každom spustení skriptu mimo stroja interpretom prečíta text skriptu; keď chránený súbor menuje, posúdi
# ho ten istý rozbor kódu ako pri kóde v príkaze. Tým istým kusom: vzory zápisníka, záznamu námietok a denníka nálezov
# (>>, tee, sed -i) už nehľadajú v telách heredoc, ktoré nečíta shell — text pomocného skriptu či skúšobného prípadu
# zápisom nie je; telo pre Python naďalej posudzujú vzory open(…)/write_text aj rozbor kódu.
# Skúšobná kópia skriptu stroja (kus KSZ5, 19. 10.): kópia skriptu stroja v dielni (to isté meno, aj s príponou, a väčšina
# tých istých funkcií), ktorú príkaz spúšťa v skúšobnom režime, ktorý jej text pozná (test, skuska, --skuska, --na-vystup,
# --nasucho …), sa nečíta — ako skript stroja; všetkých päť zastavení „skript v súbore“ za mesiac bolo práve také a falošné.
#
# Nové súbory starých druhov (25. 9. 2026): meranie P-nic-ine našlo za 7 dní nové D/L/Q/J v projektoch nového spôsobu.
# Príkaz, ktorý by založil nový map/decisions/D-*, map/learnings/L-*, map/questions/Q-*, map/journal/J-* alebo INTENT.md
# v projekte s NG-zaznamy.md, = deny s vetou, kam taká veta patrí (podrobne pri časti nižšie). Úprava existujúceho prejde.
# Chyba stráže = pustiť.
set -u
IN=$(cat 2>/dev/null || true)
for _b17 in "${BASH_SOURCE[0]%/*}/ng-straz-beh.sh" "${BASH_SOURCE[0]%/*}/../scripts/ng-straz-beh.sh" /opt/aigrip-ng/scripts/ng-straz-beh.sh; do [ -f "$_b17" ] && { . "$_b17" && ng_beh_stopa ng-zapisnik-bash && ng_audit_straz; break; }; done 2>/dev/null   # B17 (časť 3): stopa behu + audity len na dopĺňanie — ng-straz-beh.sh
TOOL=$(printf '%s' "$IN" | jq -r '.tool_name // empty' 2>/dev/null)
# SB (10. 10.): nečitateľný vstup (chýbajúci alebo zlyhaný jq) nesmie byť tichý priechod pre STAVBA.md — vtedy sa ide ďalej
# s nespracovaným vstupom príkazu nástroja Bash (JSON si rozoberie Python nižšie) a rozhodne sa len o nej. Ostatné
# stráže čítajú $CMD, ktorý ostane prázdny — sú bez zmeny.
if [ "$TOOL" != "Bash" ]; then
  { [ -z "$TOOL" ] && printf '%s' "$IN" | grep -q '"tool_name"[[:space:]]*:[[:space:]]*"Bash"' \
      && printf '%s' "$IN" | grep -q 'STAVBA\.md'; } || exit 0
fi
CMD=$(printf '%s' "$IN" | jq -r '.tool_input.command // empty' 2>/dev/null)
# SB (10. 10.): nečitateľný vstup (chýbajúci alebo zlyhaný jq) nesmie byť tichý priechod pre STAVBA.md — vtedy sa ide
# ďalej s nespracovaným vstupom a rozhodne sa len o nej. Ostatné stráže nižšie čítajú $CMD, takže sú bez zmeny.
if [ -z "$CMD" ]; then printf '%s' "$IN" | grep -q 'STAVBA\.md' || exit 0; fi
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
ZAPIS="${NG_ZAPIS_PATH:-$STROJ/scripts/ng-zapis.py}"
LOG="${NG_ZAPISNIK_LOG:-/home/pstrocka/projects/aigrip/.aigrip/ng/zapisnik-guard.log}"

# --- STAVBA.md: položky „čaká na teba“ vznikajú len nástrojom, ktorý stráž pravidla 5 a 6 vidí (kus SB, 10. 10.) ---
# Čo je zápis, neurčuje táto stráž sama: rozhoduje ten istý blok „ng-zapis-v-projekte“ ako pri pohľadoch (preto telá
# heredoc, úvodzovky a > v kóde ani v porovnaniach zápisom nie sú — grepová podoba stráže na nich mala falošný nález
# a zastavila písanie pomocného skriptu). Mení sa len cieľ: STAVBA.md projektu aigrip, rovnako ako ju pozná stráž pri
# Edit/Write (ng-p5-straz.py _je_stavba). Tu sa nastaví len príznak; Python nižšie beží raz pre všetky ciele.
CMDS="${CMD:-$IN}"
SS=0
printf '%s' "$CMDS" | grep -Eq "(^|[^A-Za-z0-9_.-])STAVBA\.md([^A-Za-z0-9_.-]|$)" \
  && ! printf '%s' "$CMDS" | grep -Eq 'ng-stav\.py|ng-postup\.py|ng-celok\.py|ng-mapa-cesty\.py' && SS=1

_sb_zastav() {
  # $1 = čo stráž videla. Deny platí aj v režime bypass (ako pri zápisníku); keď sa odpoveď nedá postaviť (chýba jq),
  # príkaz sa zastaví kódom 2 a veta ide na chybový výstup — chyba stráže tu NIE JE tichý priechod.
  printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "STAVBA.md príkazom ($1) · $(printf '%s' "$CMDS" | head -c 120 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
  RSB="STAVBA.md sa z príkazového riadku nezapisuje ($1). Položku „čaká na teba“ musí pri vzniku posúdiť stráž pravidla 5 (aspoň dve možnosti a pri KAŽDEJ cena alebo nevýhoda) a pravidla 6 (odsek, čo namietol nezávislý beh) — a tá vidí len nástroj Edit, Write a MultiEdit. Uprav dokument nimi; strojové bloky (celok, stav, čo nesedí, prvky, etapy, denník) si dopíše vykresľovač stavu sám (ng-stav.py). Čítanie dokumentu príkazom nič neblokuje."
  jq -n --arg r "$RSB" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}' 2>/dev/null && exit 0
  printf 'NG, stráž STAVBA.md: %s\n' "$RSB" >&2
  exit 2
}


# Vety zastavenia sú na jednom mieste: tú istú povie vzor v bashi aj rozbor kódu so zloženou cestou nižšie (kus KSZ2, 11. 10.).
R_ZAPISNIK="Zápisník a návrhy sú append-only a píše ich jedine ng-zapis.py — ani presmerovaním, tee, sed či Pythonom. Zapíš: python3 $ZAPIS <projekt> <typ> <pôvod> <kto> \"<veta>\" \"<kde platí>\" (typ: zamer · rozhodnutie · poznatok · mechanizmus; pôvod: povedal · navrhol · partner). Záznam, ktorý už neplatí: --zrus <projekt> <kľúč> <pôvod> <kto> \"<prečo>\"; zmena platného: --nahrad <projekt> <kľúč> <pôvod> <kto> \"<nová veta>\" --dovod \"<prečo>\". Priebeh a prepis rozhovoru sa nezapisujú."
R_NAMIETKY="Záznam námietky (namietky.jsonl), prekreslené čísla (cisla-casti4.jsonl) a log oponentúry sú append-only a píše ich jedine mechanizmus: \`python3 $STROJ/scripts/ng-p5-straz.py oponent 4.NN\` spustí beh, ktorého model, počet kôl a zadanie určuje mechanizmus, a záznam pripíše sám; čísla prekresľuje hodinový beh stavu. Oponentúru nesmie nastavovať ten, koho posudzuje (kus P6M) — námietku smieš odmietnuť odrážkou v odseku položky s dôvodom, nie ju prepísať."
R_DENNIK="Denník nálezov časti 6 (stroj-nalezy.jsonl) je append-only a píše ho jedine stroj: nález doň pripíše \`python3 $STROJ/scripts/ng-stroj.py kontrola\` (register kusov) alebo \`python3 $STROJ/scripts/ng-stroj.py vypadky\` (hlásenie výpadkov), každý ako jeden riadok s dátumom a s vlastnou vetou, čo sa našlo. Rukou ani nástrojom na úpravu súborov do neho zápis nejde (kus DN6): dôkaz, že kontrola niečo našla, si nesmie napísať ten, koho kontroluje — to je tá istá diera, ktorú 10. 10. našiel nezávislý audit biasu pri vykreslenom texte stavu."
_zz_zastav() { jq -n --arg r "$1" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'; exit 0; }
# KSZ4 (11. 10.): tri vzory nižšie hľadali v celom príkaze, teda aj v telách heredoc — 11. 10. zastavili písanie pomocného
# skriptu (python3 - <<'PY' s textom skúšobného prípadu ">>…/namietky.jsonl"), ktorý nič nezapisoval. Príkaz bez heredoc
# sa zastaví hneď ako doteraz; pri príkaze s heredoc sa vzor len poznačí (ZV) a potvrdí ho beh Pythonu nižšie nad
# príkazom bez tiel heredoc (telo, ktoré číta shell, ostáva; telo pre Python ostáva vzorom open(…)/write_text).
# Keď sa beh Pythonu nepodarí alebo určovateľ chýba, platí pôvodné rozhodnutie vzoru: zastaviť.
ZV=""
_zv_zastav() {
  case "$1" in
    z*) printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "$(printf '%s' "$CMD" | head -c 160 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
        _zz_zastav "$R_ZAPISNIK" ;;
    n*) printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "append-only $(printf '%s' "$CMD" | head -c 120 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
        _zz_zastav "$R_NAMIETKY" ;;
    *)  printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "append-only $(printf '%s' "$CMD" | head -c 120 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
        _zz_zastav "$R_DENNIK" ;;
  esac
}
_zv_vzor() { case "$CMD" in *'<<'*) ZV="$ZV$1" ;; *) _zv_zastav "$1" ;; esac; }
# KSZ8 (19. 10.): vzor sed -i hľadal meno v celom príkaze, aj vo výraze náhrady — 1. 10. zastavil u kolegu
# `sed -i 's/…(NG-zaznamy.md, 78 záznamov)/…/' iný-súbor`. Keď príkaz chránený súbor menuje len pri sed -i, poznačí sa
# (ZV) vždy, aj bez heredoc, a beh Pythonu nižšie posúdi súbory, ktoré sed mení (bez -e/-f všetky argumenty za výrazom,
# s -e/-f všetky). Vzor odteraz vidí -i aj za iným prepínačom (sed -E -i …), ktorý pôvodný vzor nevidel.

# --- zápisník a návrhy (vzory bez zmeny od 24. 9.) ---
if printf '%s' "$CMD" | grep -Eq 'NG-zaznamy\.md|NAVRHY\.md|navrhy-[^ ]*\.md' \
   && ! printf '%s' "$CMD" | grep -q 'ng-zapis\.py' && [ -r "$ZAPIS" ]; then
  S='(NG-zaznamy\.md|NAVRHY\.md|navrhy-[^ "'"'"']*\.md)'
  if printf '%s' "$CMD" | grep -Eq "(>>?|\btee( -a)?)[[:space:]]*[\"']?[^ ;|&\"']*${S}" \
     || printf '%s' "$CMD" | grep -Eq "open\([^)]*${S}[^)]*,[[:space:]]*[\"'][wa+]" \
     || { printf '%s' "$CMD" | grep -Eq "(write_text|writelines)\(" && printf '%s' "$CMD" | grep -Eq "Path\([^)]*${S}"; }; then
    _zv_vzor z
  elif printf '%s' "$CMD" | grep -Eq "sed[[:space:]]([^;|&]*[[:space:]])?-(-in-place|[a-zA-Z]*i)[^;|&]*${S}"; then
    ZV="${ZV}z"                        # KSZ8: sed -i posúdi Python podľa súborov, ktoré sed mení (nie podľa výrazu)
  fi
fi


# --- záznam námietky a prekreslené čísla (kus P6M, 10. 10. 2026) ---
# Ten istý cieľ ako pri zápisníku, a z toho istého dôvodu, pre ktorý vznikol kus HH: stráž nad Edit/Write nestačí, keď sa
# dá to isté urobiť príkazom (125 riadkov cez „cat >> NG-zaznamy.md“). Námietku, ktorá posudzuje partnera, nesmie partner
# napísať ani prepísať — ani presmerovaním, tee, sed -i či Pythonom. Jediná cesta je mechanizmus (ng-p5-straz.py).
if printf '%s' "$CMD" | grep -Eq 'namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log' \
   && ! printf '%s' "$CMD" | grep -q 'ng-p5-straz\.py'; then
  S2='(namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log)'
  if printf '%s' "$CMD" | grep -Eq "(>>?|\btee( -a)?)[[:space:]]*[\"']?[^ ;|&\"']*${S2}" \
     || printf '%s' "$CMD" | grep -Eq "open\([^)]*${S2}[^)]*,[[:space:]]*[\"'][wa+]" \
     || { printf '%s' "$CMD" | grep -Eq "(write_text|writelines)\(" && printf '%s' "$CMD" | grep -Eq "Path\([^)]*${S2}"; }; then
    _zv_vzor n
  elif printf '%s' "$CMD" | grep -Eq "sed[[:space:]]([^;|&]*[[:space:]])?-(-in-place|[a-zA-Z]*i)[^;|&]*${S2}"; then
    ZV="${ZV}n"                        # KSZ8: sed -i posúdi Python podľa súborov, ktoré sed mení (nie podľa výrazu)
  fi
fi


# --- denník nálezov časti 6 (kus DN6, 10. 10. 2026) ---
# Ten istý cieľ a ten istý dôvod ako pri zázname námietok: dôkaz, že kontrola časti 6 niečo našla, si nesmie napísať
# ten, koho kontroluje. Zápis do stroj-nalezy.jsonl presmerovaním, tee, sed -i alebo Pythonom = deny. Výnimka pre meno
# vlastného skriptu tu NIE JE úmyselne: „python3 ng-stroj.py kontrola; echo {} >> stroj-nalezy.jsonl“ by ňou prešlo,
# a stroj svoj denník píše zvnútra skriptu, nie príkazom — takú výnimku teda nepotrebuje. Čítanie prejde.
if printf '%s' "$CMD" | grep -q 'stroj-nalezy\.jsonl'; then
  S3='stroj-nalezy\.jsonl'
  if printf '%s' "$CMD" | grep -Eq "(>>?|\btee( -a)?)[[:space:]]*[\"']?[^ ;|&\"']*${S3}" \
     || printf '%s' "$CMD" | grep -Eq "(perl|ruby)[[:space:]]+-[A-Za-z]*i[^;|&]*${S3}" \
     || printf '%s' "$CMD" | grep -Eq "open\([^)]*${S3}[^)]*,[[:space:]]*[\"'][wa+]" \
     || { printf '%s' "$CMD" | grep -Eq "(write_text|writelines)\(" && printf '%s' "$CMD" | grep -Eq "Path\([^)]*${S3}"; }; then
    _zv_vzor d
  elif printf '%s' "$CMD" | grep -Eq "sed[[:space:]]([^;|&]*[[:space:]])?-(-in-place|[a-zA-Z]*i)[^;|&]*${S3}"; then
    ZV="${ZV}d"                        # KSZ8: sed -i posúdi Python podľa súborov, ktoré sed mení (nie podľa výrazu)
  fi
fi


# --- zápisník, návrhy, záznam námietok a denník nálezov: zápis kódom so zloženou cestou (kus KSZ2, 11. 10. 2026) ---
# Vzory vyššie vidia len meno súboru napísané priamo v open(…)/Path(…) alebo za presmerovaním. Pripísanie cez
# open(os.path.join(A, 'NG-zaznamy.md'), 'a'), cez premennú alebo cez dočasný súbor a premenovanie nimi prejde —
# to isté, čo kus KSZ zavrel pri STAVBA.md. Keď príkaz taký súbor menuje a spúšťa Python, posúdi ho rozbor kódu
# v spoločnom behu Pythonu nižšie (cieľ: len súbor v projekte, nie skúšobná kópia inde). Chyba stráže = pustiť.
ZZ=0
printf '%s' "$CMD" | grep -Eq 'NG-zaznamy\.md|NAVRHY\.md|navrhy-[^ ]*\.md|namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log|stroj-nalezy\.jsonl' \
  && printf '%s' "$CMD" | grep -Eq 'python|node|perl|ruby' && ZZ=1


# --- nové súbory starých druhov (25. 9.) a pohľady stroja (25. 9.) — jeden beh Pythonu pre všetky ---
# Nové súbory starých druhov: pilotný text hovorí, že staré súbory (INTENT.md, map/, D/L/Q/J) partner číta ako podklad a nové
# takéto súbory nevytvára; meranie P-nic-ine ho za 7 dní videlo porušené. Príkaz, ktorý by ZALOŽIL nový
# <projekt>/map/decisions/D-*.md, map/learnings/L-*.md, map/questions/Q-*.md, map/journal/J-*.md alebo <projekt>/INTENT.md
# (pomocný J-….md.tmp nie je záznam) v projekte nového spôsobu (má NG-zaznamy.md) — presmerovaním, tee, cp/install/ln/
# rsync, curl -o, Pythonom alebo touch — = deny s vetou, kam taká veta patrí. Zápis určuje ten istý blok ako pri
# pohľadoch, cieľ je „súbor starého druhu, ktorý ešte neexistuje“.
# Neráta sa: úprava existujúceho súboru (>> do neho, sed -i, Python), mv existujúceho starého súboru na nové meno toho istého
# druhu v tom istom priečinku (premenovanie, nie nový záznam), zmazanie, git, čítanie, kópia v /tmp či v skúšobnom priečinku
# bez NG-zaznamy.md. Platí aj v aigrip (jeho text hovorí „žiadne D-XXX“; od prechodu 19. 9. tam taký súbor nevznikol).
# Stub, ktorý zakladal starý háčik stub-promoter.sh sám (mimo nástrojov), stráž nevidí — ten vypína prechod projektu.
# Neistý cieľ (meno s $(date …), cesta skladaná v Pythone cez os.path.join, skript v súbore) sa nerozrieši a prejde.
# Python beží len vtedy, keď príkaz menuje taký priečinok alebo INTENT.md a niečo, čo zapisuje, alebo keď menuje pohľad;
# keď oboje, beží raz (jeden beh ~35 ms, dva by prekročili 50 ms). Chyba stráže = pustiť.
# určovateľ zápisu: najprv z toho istého balíka (vedľa), potom zo stroja — staršia kópia bez bloku sa preskočí
BLOK="$(dirname "${BASH_SOURCE[0]}")/ng-kontext-straz.sh:$STROJ/hooks/ng-kontext-straz.sh"
POHLADY='KDE-STOJIME|MAPA|STAV|celok|postup|etapy|forecast|dekompozicia|odislo-von|namietky'
NN=0; PP=0
# > /dev/null a 2>&1 nič nezakladajú — menej behov Pythonu (PCRE: presmerovanie, za ktorým nie je /dev/null ani &)
if printf '%s' "$CMD" | grep -Eq '(^|[^a-z])(decisions|learnings|questions|journal)([^a-z]|$)|INTENT\.md'; then
  printf '%s' "$CMD" | grep -Pq '>>?\s*(?!/dev/null|&)[^\s&|;>]|\b(tee|cp|mv|install|ln|rsync|scp|curl|wget|touch)\b|python'
  [ $? -ne 1 ] && NN=1                  # 0 = zhoda; 2 = grep bez PCRE -> radšej Python než nič
fi
printf '%s' "$CMD" | grep -Eq "(^|[^A-Za-z0-9_.-])(${POHLADY})\.md([^A-Za-z0-9_.-]|$)" && PP=1
# --- skript v súbore, ktorého príkaz chránený súbor nemenuje (kus KSZ4, 11. 10. 2026) ---
# Brána vyššie pozerá len text príkazu: `python3 zaplata.py`, kde STAVBA.md, zápisník či pohľad stojí len v skripte, sa do
# rozboru nedostal. Keď príkaz spúšťa interpretom skript, ktorý nie je skriptom stroja, brána si jeho text pozrie sama
# (grep, ~3 ms): beh Pythonu sa pustí, len keď skript chránený súbor menuje, alebo keď bash cestu skriptu nerozrieši
# (premenná, úvodzovky, relatívna cesta po cd) — vtedy ju rozrieši Python tým istým postupom ako rozbor, prečíta text
# a podľa mien v ňom zapne posudky nižšie. Skripty stroja sa nečítajú. Meranie 11. 10.: beh Pythonu pri KAŽDOM spustení
# skriptu mimo stroja (10,7 % príkazov) stál +52 ms (17 → 69 ms), preto prvé sito v bashi.
SK=0
if [ -n "$CMD" ]; then
  _skm="STAVBA\\.md|NG-zaznamy\\.md|NAVRHY\\.md|navrhy-[^ ]*\\.md|namietky\\.jsonl|cisla-casti4\\.jsonl|oponent\\.log|stroj-nalezy\\.jsonl|(${POHLADY})\\.md"
  _skc=""; [[ $IN =~ \"cwd\"[[:space:]]*:[[:space:]]*\"([^\"]*)\" ]] && _skc=${BASH_REMATCH[1]}
  _skr='(^|[^A-Za-z0-9_./-])(python3?(\.[0-9]+)?|node(js)?|perl|ruby)[[:space:]]+(-[^cem[:space:]][^[:space:]]*[[:space:]]+)*([^-[:space:]][^[:space:];|&<>()]*)'
  _skt=$CMD; _skn=0
  while [ "$_skn" -lt 12 ] && [[ $_skt =~ $_skr ]]; do
    _sks=${BASH_REMATCH[6]}; _skt=${_skt#*"${BASH_REMATCH[0]}"}; _skn=$((_skn + 1))
    case "$_sks" in *.aigrip/scripts/*|*.aigrip/hooks/*|*/opt/aigrip-ng/*) continue ;; esac
    case "$_sks" in \~/*) _sks="$HOME/${_sks#\~/}" ;; esac
    case "$_sks" in
      *[\$\`\'\"\\*?~]*) SK=1; break ;;               # cestu skriptu bash nerozrieši — rozhodne Python
      /*) ;;
      *) [ -n "$_skc" ] || { SK=1; break; }
         if [[ $CMD == *cd\ * || $CMD == *pushd\ * ]]; then     # relatívna cesta po cd: skúsi sa každý priečinok z príkazu
           _skd=$CMD; _ski=0
           while [ "$_ski" -lt 6 ] && [[ $_skd =~ (^|[^A-Za-z0-9_./-])(cd|pushd)[[:space:]]+([^[:space:]\;\|\&\<\>\(\)]+) ]]; do
             _ske=${BASH_REMATCH[3]}; _skd=${_skd#*"${BASH_REMATCH[0]}"}; _ski=$((_ski + 1))
             case "$_ske" in \~/*) _ske="$HOME/${_ske#\~/}" ;; \~) _ske=$HOME ;; esac
             case "$_ske" in
               *[\$\`\'\"\\*?~]*) SK=1; break 2 ;;         # priečinok bash nerozrieši — rozhodne Python
               /*) ;;
               *) [ "$_ski" -gt 1 ] && { SK=1; break 2; }; _ske="$_skc/$_ske" ;;
             esac
             [ -f "$_ske/$_sks" ] && grep -Eqs "$_skm" "$_ske/$_sks" && { SK=1; break 2; }
           done
         fi
         _sks="$_skc/$_sks" ;;
    esac
    [ -f "$_sks" ] || continue
    grep -Eqs "$_skm" "$_sks" && { SK=1; break; }
  done
fi
[ "$NN$PP$SS$ZZ$SK$ZV" = 00000 ] && exit 0
IFS= read -r -d '' KOD <<'PY' || true
import os, re, sys, json
v = json.loads(sys.stdin.buffer.read().decode('utf-8', 'replace') or '{}')
Z, K = '# --- ng-zapis-v-projekte:zaciatok ---', '# --- ng-zapis-v-projekte:koniec ---'
for blok in os.environ['NG_BLOK'].split(':'):
    try: t = open(blok, encoding='utf-8').read()
    except OSError: continue
    if Z in t and K in t: break
else: sys.exit(4 if os.environ.get('NG_ZV') else 0)   # určovateľ nie je nikde = pustiť; poznačený vzor (KSZ4) = zastaví bash
G = {}
exec(compile(t[t.index(Z):t.index(K)], blok, 'exec'), G)

# ---- rozbor kódu Pythonu, spoločný pre všetky chránené súbory (kus KSZ 11. 10. pri STAVBA.md, kus KSZ2 11. 10. pre ostatné) ----
# Spoločný určovateľ zápisu z výrazu zloženého z kusov (`AIG + '/STAVBA.md'`, os.path.join(A, '.aigrip/ng', 'STAV.md'),
# f'{A}/NG-zaznamy.md', Path(A) / meno, parameter funkcie, sys.argv) rozrieši len PRVÝ kus cesty — pre pravidlo 2 to stačí,
# pre jeden menovaný súbor nie. Tu sa kód rozoberie (ast), mená sa dosadia (bez ohľadu na rozsah platnosti — viac hodnôt
# = skúsia sa všetky) a cieľ každej zápisovej operácie sa vyhodnotí celý. Číta sa kód v príkaze (heredoc, -c), skript,
# ktorý ten istý príkaz najprv zapíše, aj skript v súbore, ktorý príkaz spúšťa (okrem skriptov stroja). Mení sa len cieľ
# (parameter ciel) — ako pri spoločnom určovateľovi. Cieľ, ktorý sa vyhodnotiť nedá, zápisom nie je: je to stráž proti
# prešľapu, nie proti úmyslu. Spoločný určovateľ sa nemení (meranie pravidla 2 sa nepohne).
def _ksz_kod(kod, argv, cwd, senv, ciel, root):
    """'' alebo popis zápisu do chráneného súboru v kóde Pythonu; čo je chránený súbor, určuje ciel(cesta, cwd, root)."""
    import ast
    T = ast.parse(kod)
    env = {}

    def orez(s): return {x for x in list(s)[:40] if isinstance(x, str) and len(x) <= 400}

    def meno(f):
        c = []
        while isinstance(f, ast.Attribute): c.append(f.attr); f = f.value
        c.append(f.id if isinstance(f, ast.Name) else '?')
        return '.'.join(reversed(c))

    def spoj(*mn):
        o = {''}
        for m in mn:
            if not m: return set()
            o = orez({a + b for a in o for b in m})
        return o

    def cesta(*mn):
        o = None
        for m in mn:
            if not m: return set()
            o = set(m) if o is None else orez({os.path.join(a, b) for a in o for b in m})
        return o or set()

    def val(n, h=0):
        if n is None or h > 14: return set()
        if isinstance(n, ast.Constant):
            return {n.value} if isinstance(n.value, str) else {n.value.decode('utf-8', 'replace')} if isinstance(n.value, bytes) else set()
        if isinstance(n, ast.Name): return set(env.get(n.id, ()))
        if isinstance(n, ast.JoinedStr):
            return spoj(*[val(x.value if isinstance(x, ast.FormattedValue) else x, h + 1) for x in n.values])
        if isinstance(n, ast.BinOp):
            l, r = val(n.left, h + 1), val(n.right, h + 1)
            if isinstance(n.op, ast.Add): return spoj(l, r)
            if isinstance(n.op, ast.Div): return cesta(l, r)
            if isinstance(n.op, ast.Mod):
                ar = [val(e, h + 1) for e in n.right.elts] if isinstance(n.right, ast.Tuple) else [r]
                o = set()
                for a in l:
                    ks = a.split('%s')
                    if len(ks) - 1 != len(ar) or not all(ar): continue
                    x = {ks[0]}
                    for k, m in zip(ks[1:], ar): x = orez({p + q + k for p in x for q in m})
                    o |= x
                return orez(o)
            return set()
        if isinstance(n, ast.IfExp): return orez(val(n.body, h + 1) | val(n.orelse, h + 1))
        if isinstance(n, ast.BoolOp): return orez(set().union(*[val(x, h + 1) for x in n.values]))
        if isinstance(n, ast.NamedExpr): return val(n.value, h + 1)
        if isinstance(n, ast.Subscript):
            m = meno(n.value); i = n.slice
            if m == 'sys.argv' and isinstance(i, ast.Constant) and isinstance(i.value, int):
                return {argv[i.value]} if -len(argv) <= i.value < len(argv) else set()
            if m == 'os.environ' and isinstance(i, ast.Constant) and isinstance(i.value, str): return {'$' + i.value}
            return set()
        if isinstance(n, ast.Attribute):
            if n.attr == 'parent': return orez({os.path.dirname(x.rstrip('/')) for x in val(n.value, h + 1)})
            if n.attr == 'name': return orez({os.path.basename(x.rstrip('/')) for x in val(n.value, h + 1)})
            return set()
        if isinstance(n, ast.Call):
            m = meno(n.func); k = m.rsplit('.', 1)[-1]
            A = [val(x, h + 1) for x in n.args if not isinstance(x, ast.Starred)]
            if m in ('os.path.join', 'path.join', 'posixpath.join', 'join'): return cesta(*A)
            if k in ('Path', 'PurePath', 'PosixPath', 'PurePosixPath'): return cesta(*A) if A else {'.'}
            if m in ('os.getcwd', 'Path.cwd', 'pathlib.Path.cwd'): return {'$PWD'}
            if m in ('Path.home', 'pathlib.Path.home'): return {'~'}
            if m in ('os.environ.get', 'os.getenv', 'environ.get', 'getenv') and A and A[0]:
                return orez({'$' + x for x in A[0]} | (A[1] if len(A) > 1 else set()))
            if k in ('expanduser', 'abspath', 'realpath', 'normpath', 'fspath', 'str', 'expandvars', 'fsdecode') and len(A) == 1:
                return A[0]
            if k == 'dirname' and len(A) == 1: return orez({os.path.dirname(x) for x in A[0]})
            if k == 'basename' and len(A) == 1: return orez({os.path.basename(x) for x in A[0]})
            if isinstance(n.func, ast.Attribute):
                z = val(n.func.value, h + 1)
                if not z: return set()
                if k in ('resolve', 'absolute', 'expanduser', 'as_posix', 'strip', 'rstrip', 'lstrip'): return z
                if k == 'joinpath': return cesta(z, *A)
                if k == 'with_name' and len(A) == 1: return orez({os.path.join(os.path.dirname(x), y) for x in z for y in A[0]})
                if k == 'with_suffix' and len(A) == 1: return orez({os.path.splitext(x)[0] + y for x in z for y in A[0]})
                if k == 'replace' and len(A) == 2 and all(A): return orez({x.replace(p, q) for x in z for p in A[0] for q in A[1]})
                if k == 'format' and all(A):
                    o = set()
                    for x in z:
                        ks = re.split(r'\{\d*\}', x)
                        if len(ks) - 1 != len(A): continue
                        y = {ks[0]}
                        for kk, mm in zip(ks[1:], A): y = orez({p + q + kk for p in y for q in mm})
                        o |= y
                    return orez(o)
        return set()

    # priradenia: meno = výraz, a, b = x, y, p += '…', cyklus cez vymenované hodnoty, parametre funkcií z volaní
    fn = {f.name: f for f in ast.walk(T) if isinstance(f, (ast.FunctionDef, ast.AsyncFunctionDef))}
    PR = []
    for n in ast.walk(T):
        if isinstance(n, ast.Assign): PR += [(t, n.value) for t in n.targets]
        elif isinstance(n, (ast.AnnAssign, ast.NamedExpr)) and n.value is not None: PR.append((n.target, n.value))
        elif isinstance(n, ast.AugAssign) and isinstance(n.op, (ast.Add, ast.Div)):
            PR.append((n.target, ast.BinOp(left=n.target, op=n.op, right=n.value)))
        elif isinstance(n, (ast.For, ast.AsyncFor, ast.comprehension)) and isinstance(n.iter, (ast.Tuple, ast.List, ast.Set)):
            PR += [(n.target, e) for e in n.iter.elts]
        elif isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)):
            ps = n.args.posonlyargs + n.args.args
            PR += [(ast.Name(id=p.arg), d) for p, d in zip(ps[len(ps) - len(n.args.defaults):], n.args.defaults)]
        elif isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id in fn:
            f = fn[n.func.id]
            PR += [(ast.Name(id=p.arg), x) for p, x in zip(f.args.posonlyargs + f.args.args, n.args)]
            PR += [(ast.Name(id=kw.arg), kw.value) for kw in n.keywords if kw.arg]

    def prirad(t, vn):
        if isinstance(t, ast.Name):
            nov = val(vn) - env.get(t.id, set())
            if nov: env[t.id] = orez(env.get(t.id, set()) | nov)
            return bool(nov)
        if isinstance(t, (ast.Tuple, ast.List)) and isinstance(vn, (ast.Tuple, ast.List)) and len(t.elts) == len(vn.elts):
            return any([prirad(a, b) for a, b in zip(t.elts, vn.elts)])
        return False
    for _ in range(6):
        if not any([prirad(t, vn) for t, vn in PR]): break

    def pise(md):
        """Režim otvorenia je zápisový; režim, ktorý sa nedá vyhodnotiť, radšej áno."""
        if md is None: return False
        x = val(md)
        return not x or any(set(y) & set('wax+') for y in x)

    ciele = []; shell = []; cwds = [cwd]
    for n in ast.walk(T):
        if not isinstance(n, ast.Call): continue
        m = meno(n.func); k = m.rsplit('.', 1)[-1]; a = n.args
        kw = {x.arg: x.value for x in n.keywords if x.arg}
        if m == 'os.chdir' and a: cwds += sorted(val(a[0]))
        elif m == 'os.open' and len(a) > 1:
            if re.search(r'O_(?:WRONLY|RDWR|CREAT|APPEND|TRUNC)', ast.dump(a[1])): ciele.append(val(a[0]))
        elif k == 'open' and (isinstance(n.func, ast.Name) or len(a) >= 2 or 'file' in kw):
            if pise(a[1] if len(a) > 1 else kw.get('mode')): ciele.append(val(a[0] if a else kw.get('file')))
        elif k == 'open' and isinstance(n.func, ast.Attribute):          # Path(…).open('w')
            if pise(a[0] if a else kw.get('mode')): ciele.append(val(n.func.value))
        elif k in ('write_text', 'write_bytes') and isinstance(n.func, ast.Attribute): ciele.append(val(n.func.value))
        elif m in ('os.replace', 'os.rename', 'os.renames', 'os.link', 'os.symlink', 'shutil.move', 'shutil.copy',
                   'shutil.copy2', 'shutil.copyfile') and (len(a) >= 2 or 'dst' in kw):
            d = val(a[1] if len(a) >= 2 else kw['dst']); s = val(a[0]) if a else set()
            ciele += [d, orez({os.path.join(x, os.path.basename(y)) for x in d for y in s})]   # cieľ = priečinok
        elif k in ('rename', 'replace') and isinstance(n.func, ast.Attribute) and len(a) == 1 and not kw \
                and not m.startswith(('os.', 'shutil.', 're.')):
            ciele.append(val(a[0]))                                        # Path(tmp).replace(cieľ)
        elif m == 'os.truncate' and a: ciele.append(val(a[0]))
        elif m in ('os.system', 'os.popen') and a: shell += sorted(val(a[0]))
        elif m.startswith('subprocess.') and a:
            if isinstance(a[0], (ast.List, ast.Tuple)):
                sl = [sorted(val(e)) for e in a[0].elts]
                if sl and all(sl): shell.append(' '.join("'" + x[0].replace("'", "'\\''") + "'" for x in sl))
            else: shell += sorted(val(a[0]))

    def rozvin(x): return re.sub(r'\$\{?([A-Za-z_]\w*)\}?', lambda mm: senv.get(mm.group(1), mm.group(0)), x)
    for c in ciele:
        for x in c:
            if any(ciel(rozvin(x), d if str(d).startswith('/') else cwd, root) for d in cwds):
                return 'python: zápis cez zloženú cestu (' + os.path.basename(x.rstrip('/'))[:60] + ')'
    for s in shell:
        for d in cwds:
            if G['konanie']('Bash', {'command': s}, d if str(d).startswith('/') else cwd, root, proj='\x00')[1]:
                return 'python: príkaz shellu so zápisom'
    return ''

def _ksz_hrubo(kod, meno):
    """Kód, ktorý sa nedá rozobrať (chyba syntaxe, pád rozboru): menuje chránený súbor (vzor meno) a obsahuje zápisovú operáciu."""
    if not re.search(r"os\.re(?:place|name)|shutil\.(?:copy\w*|move)|write_(?:text|bytes)|open\([^)\n]*['\"][rbt]*[wax+]", kod): return ''
    for m in G['_STR'].finditer(kod):
        if re.search(meno, m.group(2)):
            return 'python: kód sa nedá rozobrať, menuje ' + os.path.basename(m.group(2))[:60] + ' a zapisuje'
    return ''

# ---- iné interprety: node, perl, ruby (kus KSZ3, 11. 10.) ----
# Kód v JavaScripte, Perle a Ruby sa nerozoberá stromom ako Python, ale malým rozborom výrazu: reťazce (aj s vloženou
# premennou `${A}/x`, "$A/x", "#{A}/x"), mená z jednoduchých priradení (const/let/var/my/our A = …), spájanie (+, v Perle
# bodka), path.join/resolve, File.join/expand_path, domov a priečinok príkazu, argumenty príkazu. Cieľ každej zápisovej
# operácie (writeFileSync, appendFileSync, renameSync, copyFileSync, open s > alebo >>, File.write, FileUtils.mv …) sa
# vyhodnotí celý a posúdi ten istý cieľ ako pri Pythone; viac hodnôt mena = skúsia sa všetky. Príkaz shellu z kódu
# (system, execSync, `…`) posúdi spoločný určovateľ. Cieľ, ktorý sa vyhodnotiť nedá (meno bez priradenia, glob, cyklus,
# hodnota z výpočtu), zápisom nie je — ako pri Pythone; režim otvorenia, ktorý sa vyhodnotiť nedá, radšej áno.
_IZ_ZAP = {
    'node': re.compile(r'\b(?:writeFile(?:Sync)?|appendFile(?:Sync)?|createWriteStream|truncate(?:Sync)?|rename(?:Sync)?'
                       r'|copyFile(?:Sync)?|cpSync|link(?:Sync)?|symlink(?:Sync)?|openSync|\.open|\.cp)\s*\(|\b(?:execSync|exec)\s*\('),
    'perl': re.compile(r'\b(?:open|sysopen|rename|truncate|copy|move|system|exec|spew\w*|append\w*|edit\w*)\b|`|\bqx'),
    'ruby': re.compile(r'\b(?:File|IO|FileUtils|Pathname)\b|\bopen\s*\(|\b(?:system|exec)\b|`|%x'),
}


def _iz_retazec(s, i):
    """Koniec reťazca, ktorý začína na s[i] (úvodzovka), s ohľadom na \\ ."""
    q = s[i]; k = i + 1
    while k < len(s) and s[k] != q:
        k += 2 if s[k] == '\\' else 1
    return k + 1


def _iz_args(s, i, zatv, jaz, slova=True):
    """Argumenty volania od pozície i (za „(“, keď zatv; inak do konca príkazu, pri slova aj po or/and/||) ako texty;
    (argumenty, koniec)."""
    A = []; z = i; d = 0; k = i
    while k < len(s):
        ch = s[k]
        if ch in '\'"`': k = _iz_retazec(s, k); continue
        if jaz != 'node' and ch == '#' or jaz != 'perl' and s.startswith('//', k) and (k == 0 or s[k - 1] in ' \t\n;,('):
            while k < len(s) and s[k] != '\n': k += 1
            continue
        if ch in '([{': d += 1
        elif ch in ')]}':
            if d == 0:
                if zatv: A.append(s[z:k]); return [a.strip() for a in A if a.strip()], k + 1
                break
            d -= 1
        elif d == 0 and ch == ',': A.append(s[z:k]); z = k + 1
        elif d == 0 and not zatv and (ch in ';\n' or slova and re.match(r'\s+(?:or|and|unless|if|do)\b|\s*(?:\|\||&&)', s[k:k + 9])): break
        k += 1
    A.append(s[z:k])
    return [a.strip() for a in A if a.strip()], k


def _iz_casti(e, sep):
    """Rozdelí výraz na časti oddelené na najvyššej úrovni niektorým z oddeľovačov sep (mimo reťazcov a zátvoriek)."""
    o = []; z = 0; d = 0; k = 0
    while k < len(e):
        ch = e[k]
        if ch in '\'"`': k = _iz_retazec(e, k); continue
        if ch in '([{': d += 1
        elif ch in ')]}': d -= 1
        elif d == 0:
            m = next((x for x in sep if e.startswith(x, k)), None)
            if m and not (m == '.' and (e[k + 1:k + 2] in ('.', '=') or e[k - 1:k] == '.' or
                                        (k and e[k - 1].isdigit() and e[k + 1:k + 2].isdigit()))):
                o.append(e[z:k]); z = k + len(m); k = z; continue
        k += 1
    o.append(e[z:])
    return [x.strip() for x in o]


def _iny_kod(kod, jaz, argv, cwd, senv, ciel, root):
    """'' alebo popis zápisu do chráneného súboru v kóde JavaScriptu (node), Perlu alebo Ruby."""
    if not _IZ_ZAP[jaz].search(kod): return ''
    dom = '/'.join(root.split('/')[:3]); env = {}

    def orez(s): return {x for x in list(s)[:40] if isinstance(x, str) and len(x) <= 400}

    def spoj(*mn):
        o = {''}
        for m in mn:
            if not m: return set()
            o = orez({a + b for a in o for b in m})
        return o

    def cesta(*mn):
        o = None
        for m in mn:
            if not m: return set()
            o = set(m) if o is None else orez({os.path.join(a, b) for a in o for b in m})
        return o or set()

    def premenna(n): return set(env.get(n.lstrip('$'), ()))

    def zenv(n):
        if n == 'HOME': return {dom}
        if n == 'PWD': return {cwd}
        if n == 'CLAUDE_PROJECT_DIR': return {root}
        return {senv[n]} if n in senv else set()

    def vlozene(s, h):
        """Hodnoty reťazca s vloženými premennými."""
        if jaz == 'node': R = r'\$\{([^}]*)\}'
        elif jaz == 'ruby': R = r'#\{([^}]*)\}'
        else: R = r'\$\{(\w+)\}|\$ENV\{(\w+)\}|\$(\w+)'
        ks = []; z = 0
        for m in re.finditer(R, s):
            ks.append({s[z:m.start()]})
            if jaz == 'perl':
                ks.append(zenv(m.group(2)) if m.group(2) else premenna(m.group(1) or m.group(3)))
            else: ks.append(val(m.group(1), h + 1))
            z = m.end()
        ks.append({s[z:]})
        return spoj(*ks)

    def operand(e, h):
        e = e.strip()
        if not e: return set()
        if e[0] in '\'"`' and _iz_retazec(e, 0) == len(e):
            t = e[1:-1]
            if e[0] == "'" or (e[0] == '"' and jaz == 'node'): return {re.sub(r"\\(.)", r'\1', t)}
            if e[0] == '`' and jaz != 'node': return set()
            return vlozene(t, h)
        if e[0] == '(' and e.endswith(')'): return val(e[1:-1], h + 1)
        m = re.match(r'^\$ENV\{\s*[\'"]?(\w+)[\'"]?\s*\}$|^ENV(?:\[\s*[\'"](\w+)[\'"]\s*\]|\.fetch\(\s*[\'"](\w+)[\'"][^)]*\))$'
                     r'|^process\.env(?:\.(\w+)|\[\s*[\'"](\w+)[\'"]\s*\])$', e)
        if m: return zenv(next(x for x in m.groups() if x))
        m = re.match(r'^(?:process\.argv|\$ARGV|ARGV)\[\s*(\d+)\s*\]$', e)
        if m:
            i = int(m.group(1))                    # node: argv má na začiatku node (a skript alebo -), ako process.argv
            return {argv[i]} if 0 <= i < len(argv) else set()
        if re.match(r'^(?:(?:require\(\s*[\'"](?:node:)?os[\'"]\s*\)|os)\.homedir\(\s*\)|Dir\.home|File\.expand_path\(\s*[\'"]~[\'"]\s*\))$', e): return {dom}
        if re.match(r'^(?:process\.cwd\(\s*\)|Dir\.pwd|(?:Cwd::)?(?:get)?cwd\(?\s*\)?)$', e): return {cwd}
        m = re.match(r'^(.*?)\.(?:toString|to_s|trim|strip|chomp|normalize|to_path|to_str)\(?\s*\)?$', e, re.S)
        if m: return val(m.group(1), h + 1)
        m = re.match(r'^([\w$:.]+(?:->\w+)*)\s*\(', e, re.S)
        if m and e.endswith(')'):
            f = m.group(1); A, kon = _iz_args(e, m.end(), True, jaz)
            if kon == len(e):
                V = [val(x, h + 1) for x in A]
                k = re.split(r'[.:]+|->', f)[-1]
                if k in ('join', 'resolve', 'catfile', 'catdir'):
                    o = cesta(*V)
                    return o if k != 'resolve' else orez({x if x.startswith('/') else os.path.join(cwd, x) for x in o})
                if k == 'expand_path' and V:
                    o = orez({dom + x[1:] if x.startswith('~') else x for x in V[0]})
                    return cesta(V[1], o) if len(V) > 1 else orez({x if x.startswith('/') else os.path.join(cwd, x) for x in o})
                if k in ('dirname',) and len(V) == 1: return orez({os.path.dirname(x) for x in V[0]})
                if k in ('basename',) and V: return orez({os.path.basename(x) for x in V[0]})
                if k in ('Pathname', 'new', 'path', 'String', 'Path', 'abs_path', 'realpath', 'rel2abs') and len(V) >= 1: return V[0]
                return set()
        if re.match(r'^\$?\{?[A-Za-z_]\w*\}?$', e): return premenna(e.strip('${}'))
        return set()

    def val(e, h=0):
        if h > 12: return set()
        o = set()
        for c in _iz_casti(e, ['||', '//', '??', ' or ', '?', ':']) if not re.match(r'^[\w$]+::', e.strip()) else [e]:
            ks = _iz_casti(c, ['.'] if jaz == 'perl' else ['+'])
            o |= spoj(*[operand(x, h) for x in ks])
        return orez(o)

    # priradenia: const/let/var/my/our/local A = výraz, A += …, $A .= …
    PR = []
    for m in re.finditer(r'(?:(?<=[\s;{(])|^)(?:(?:const|let|var|my|our|local)\s+)?(\$?[A-Za-z_]\w*)\s*(\+|\.)?=(?![=~>])', kod):
        if m.group(0).lstrip().startswith(('if', 'while', 'return')): continue
        A, _k = _iz_args(kod, m.end(), False, jaz, False)
        if A: PR.append((m.group(1).lstrip('$'), m.group(2), A[0]))
    for _ in range(4):
        zmena = False
        for n, op, e in PR:
            x = val(e)
            if op: x = spoj(env.get(n, set()), x)
            nov = x - env.get(n, set())
            if nov: env[n] = orez(env.get(n, set()) | nov); zmena = True
        if not zmena: break

    def pise(r):
        x = val(r)
        return not x or any(set(y) & set('wa+') for y in x) or bool(re.search(r'O_(?:WRONLY|RDWR|CREAT|APPEND|TRUNC)|WRONLY|RDWR|CREAT|APPEND|TRUNC', r))

    ciele = []; shell = []
    if jaz == 'node':
        for m in _IZ_ZAP['node'].finditer(kod):
            A, _k = _iz_args(kod, m.end(), True, jaz)
            if not A: continue
            k = re.sub(r'\W', '', m.group(0))
            if k in ('execSync', 'exec'): shell += sorted(val(A[0])); continue
            if k in ('open', 'openSync'):
                if len(A) > 1 and pise(A[1]): ciele.append(val(A[0]))
                continue
            ciele.append(val(A[1] if re.match(r'(?:rename|copyFile|cp|link|symlink)', k) and len(A) > 1 else A[0]))
    elif jaz == 'perl':
        for m in re.finditer(r'(?<![\w$>:])(open|sysopen|rename|truncate|copy|move|File::Copy::copy|File::Copy::move|system|exec)\b\s*(\(?)', kod):
            A, _k = _iz_args(kod, m.end(), bool(m.group(2)), jaz)
            k = m.group(1).split('::')[-1]
            if k == 'open' and len(A) >= 3:
                md = val(A[1])
                if any(re.match(r'\s*(?:\+?>|\+<)', x) for x in md) or not md: ciele.append(val(A[2]))
            elif k == 'open' and len(A) == 2:
                ciele.append(orez({re.sub(r'^\s*(?:\+?>>?|\+<)\s*', '', x) for x in val(A[1]) if re.match(r'\s*(?:\+?>|\+<)', x)}))
            elif k == 'sysopen' and len(A) >= 3:
                if pise(A[2]): ciele.append(val(A[1]))
            elif k in ('rename', 'copy', 'move') and len(A) >= 2: ciele.append(val(A[1]))
            elif k == 'truncate' and A: ciele.append(val(A[0]))
            elif k in ('system', 'exec') and A:
                if len(A) == 1: shell += sorted(val(A[0]))
                else:
                    sl = [sorted(val(x)) for x in A]
                    if all(sl): shell.append(' '.join("'" + x[0].replace("'", "'\\''") + "'" for x in sl))
        for m in re.finditer(r'\bpath\s*\(', kod):                       # Path::Tiny: path(X)->spew / append / edit
            A, k = _iz_args(kod, m.end(), True, jaz)
            if A and re.match(r'\s*->\s*(?:spew|append|edit)', kod[k:]): ciele.append(val(A[0]))
        for m in re.finditer(r'`((?:\\.|[^`\\])*)`|\bqx\s*[({]([^)}]*)[)}]', kod):
            shell += sorted(vlozene(m.group(1) if m.group(1) is not None else m.group(2), 0))
    else:
        for m in re.finditer(r'(?<![\w.:])((?:File|IO)\.(?:write|binwrite|open|new|rename|link|symlink|truncate)'
                             r'|FileUtils\.(?:mv|move|cp|copy|copy_file|cp_r|install|ln|ln_s|ln_sf)|open|system|exec)\b\s*(\(?)', kod):
            A, k = _iz_args(kod, m.end(), bool(m.group(2)), jaz)
            f = m.group(1); kk = f.split('.')[-1]
            if not A: continue
            if kk in ('write', 'binwrite', 'truncate'): ciele.append(val(A[0]))
            elif kk in ('open', 'new'):
                if len(A) > 1 and (pise(A[1]) or any(re.match(r'mode:\s*[\'"][^\'"]*[wa+]', x) for x in A[1:])): ciele.append(val(A[0]))
            elif kk in ('system', 'exec'): shell += sorted(val(A[0])) if len(A) == 1 else []
            elif len(A) >= 2: ciele.append(val(A[1]))
        for m in re.finditer(r'\bPathname(?:\.new)?\s*\(', kod):
            A, k = _iz_args(kod, m.end(), True, jaz)
            if A and re.match(r'\.(?:write|binwrite)\b|\.open\(\s*[\'"][wa]', kod[k:]): ciele.append(val(A[0]))
        for m in re.finditer(r'`((?:\\.|[^`\\])*)`|%x[({]([^)}]*)[)}]', kod):
            shell += sorted(vlozene(m.group(1) if m.group(1) is not None else m.group(2), 0))

    def rozvin(x): return re.sub(r'\$\{?([A-Za-z_]\w*)\}?', lambda mm: senv.get(mm.group(1), mm.group(0)), x)
    for c in ciele:
        for x in c:
            x = dom + x[1:] if x.startswith('~') else x
            if ciel(rozvin(x), cwd, root): return jaz + ': zápis do ' + os.path.basename(x.rstrip('/'))[:60]
    for s in shell:
        if G['konanie']('Bash', {'command': s}, cwd, root, proj='\x00')[1]: return jaz + ': príkaz shellu so zápisom'
    return ''


def _iny_hrubo(kod, jaz, meno):
    """Kód, ktorý rozbor nezvládol: menuje chránený súbor (vzor meno) a obsahuje zápisovú operáciu."""
    if not _IZ_ZAP[jaz].search(kod): return ''
    for m in G['_STR'].finditer(kod):
        if re.search(meno, re.sub(r'^\s*(?:\+?>>?|\+<)\s*', '', m.group(2))):
            return jaz + ': kód sa nedá rozobrať, menuje ' + os.path.basename(m.group(2))[:60] + ' a zapisuje'
    return ''


def _iny_cli(jaz, a):
    """Z argumentov node/perl/ruby: (kódy v príkaze, skript v súbore alebo None, argumenty kódu, prepis na mieste -i)."""
    kody = []; i = 0; inpl = False
    while i < len(a):
        x = a[i]
        if jaz == 'node':
            if x in ('-e', '--eval', '-p', '--print') and i + 1 < len(a): kody.append(a[i + 1]); i += 2; break
            if x.startswith(('--eval=', '--print=')): kody.append(x.split('=', 1)[1]); i += 1; break
            if x in ('-r', '--require', '--import', '--loader', '--experimental-loader', '-C', '--conditions'): i += 2; continue
            if x == '-' or not x.startswith('-'): break
            i += 1; continue
        if x == '--': i += 1; break
        if x == '-' or not x.startswith('-') or len(x) == 1: break
        k = 1; dalsi = False
        while k < len(x):
            ch = x[k]
            if ch in 'eE' and (jaz == 'perl' or ch == 'e'):
                if k + 1 < len(x): kody.append(x[k + 1:])
                elif i + 1 < len(a): kody.append(a[i + 1]); dalsi = True
                break
            if ch == 'i': inpl = True; break
            if ch in 'l0':
                k += 1
                while k < len(x) and x[k] in '0123456789abcdefxABCDEF': k += 1
                continue
            if ch in ('MmIdDxCFV' if jaz == 'perl' else 'rICFWTxEK'):
                if k + 1 == len(x) and ch in 'IrCM' and i + 1 < len(a): dalsi = True
                break
            k += 1
        i += 2 if dalsi else 1
    if kody: return kody, None, a[i:], inpl
    if i < len(a) and a[i] != '-': return [], a[i], a[i + 1:], inpl
    return [], '-', a[i + 1:], inpl

# ---- skúšobná kópia skriptu stroja v skúšobnom režime (kus KSZ5, 19. 10. 2026) ----
# Prehrávka kusu KSZ4 (21 dní) a týždeň prevádzky po ňom: všetkých päť zastavení „skript v súbore“ bolo falošných a všetky
# boli kópiou skriptu stroja v dielni spustenou v skúšobnom režime (ng-dorucenie.py --skuska, ng-kdestojime-stary.py
# --na-vystup dvakrát, ng-stare-straz.py skuska, ng-sluby.py test). Rozbor režim behu nevidí: cieľ z predvolenej hodnoty
# (ROOT = os.environ.get('NG_ROOT') or '/home/…/projects') ani zápis vo funkcii, ktorú skúška púšťa len nad pieskoviskom,
# od skutočného zápisu nerozozná. Skript stroja v skúšobnom režime píše len do pieskoviska a na výstup (zmluva stroja, stráži
# ju jeho vlastná skúška), preto sa taká kópia nečíta rovnako ako skript stroja — len keď platí všetko: (1) príkaz ju spúšťa
# v skúšobnom režime, ktorý jej text sám pozná ('test', 'skuska…', '--skuska', '--test', '--na-vystup', '--nasucho',
# '--sucho', '--dry-run'); (2) jej meno je meno skriptu stroja, aj s príponou (ng-kdestojime-stary.py); (3) aspoň polovica
# jej funkcií je v tom skripte stroja a ona má aspoň polovicu jeho funkcií — pomocný skript s podobným menom kópiou nie je.
# Bez skúšobného režimu sa kópia posúdi ako doteraz. Návrh z kusu KSZ4 (nerátať predvolenú hodnotu, keď príkaz premennú
# sám nastaví) by z tých piatich nezachránil ani jedno — žiadny z príkazov premennú cieľa nenastavoval.
_KS_REZIM = re.compile(r'^(?:test|skuska(?:-[\w-]+)?|--skuska|--test|--na-vystup|--nasucho|--sucho|--dry-run)$')
_KS_DEF = re.compile(r'^[ \t]*(?:async[ \t]+)?def[ \t]+(\w+)', re.M)
def _kopia_v_skuske(s, t, argv):
    """Skript s (text t, argumenty argv so skriptom na začiatku) je skúšobná kópia skriptu stroja v skúšobnom režime."""
    rez = [x for x in argv[1:] if _KS_REZIM.match(x)]
    if not rez or not any("'" + x + "'" in t or '"' + x + '"' in t for x in rez): return False
    b = os.path.basename(s)
    if not b.endswith('.py'): return False
    b = b[:-3]; naj = None
    for d in dict.fromkeys(os.path.join(os.path.dirname(os.path.dirname(x)), 'scripts')
                           for x in os.environ.get('NG_BLOK', '').split(':') if x.startswith('/')):
        try: L = os.listdir(d)
        except OSError: continue
        for m in L:
            m = m[:-3] if m.endswith('.py') else ''
            if m and (b == m or (b.startswith(m) and b[len(m)] in '-_.')) and (naj is None or len(m) > len(naj[1])): naj = (d, m)
    if not naj: return False
    try: ts = open(os.path.join(naj[0], naj[1] + '.py'), encoding='utf-8', errors='replace').read()
    except OSError: return False
    a, m = set(_KS_DEF.findall(t)), set(_KS_DEF.findall(ts))
    sp = len(a & m)
    return bool(a and m) and sp * 2 >= len(a) and sp * 2 >= len(m)

def _ksz_prikaz(prikaz, cwd, root, ciel, meno, zber=None):
    """Prejde príkaz: každý beh Pythonu, node, perl a ruby s kódom v príkaze (heredoc, -c, -e) alebo so skriptom v súbore.
    So zoznamom zber sa nič neposudzuje — len sa doň uložia texty skriptov v súbore, ktoré príkaz spúšťa (kus KSZ4)."""
    G['_v'] = ciel                       # príkaz shellu spustený z kódu posúdi spoločný určovateľ s tým istým cieľom
    tel, tela = G['_telá'](prikaz)
    pyt = [b for ln, b in tela if re.search(r'python3?\b', ln)]
    senv = {}; dom = '/'.join(root.split('/')[:3]); OBAL = G.get('_OBAL') or {}
    def rozvin(x): return re.sub(r'\$\{?([A-Za-z_]\w*)\}?', lambda mm: senv.get(mm.group(1), mm.group(0)), x)
    for w, _r in G['_prikazy'](tel):
        w = [rozvin(x) for x in w]
        if w and w[0] in ('export', 'local', 'declare', 'readonly'): w = w[1:]
        if w and all(re.match(r'^[A-Za-z_]\w*=', x) for x in w):
            for x in w: nn, _, hh = x.partition('='); senv[nn] = rozvin(dom + hh[1:] if hh.startswith('~') else hh)
            continue
        while w and (re.match(r'^\w+=', w[0]) or w[0] in OBAL):
            if '=' in w[0]: nn, _, hh = w[0].partition('='); senv.setdefault(nn, rozvin(dom + hh[1:] if hh.startswith('~') else hh))
            o = OBAL.get(w[0], set()); tm = w[0] == 'timeout'; w = w[1:]
            while w and w[0].startswith('-') and w[0] != '-':
                w = w[2:] if w[0] in o else w[1:]
            if tm and w: w = w[1:]
        if not w: continue
        c0 = os.path.basename(w[0]); a = w[1:]
        if c0 == 'cd':
            t2 = next((x for x in a if not x.startswith('-')), dom)
            t2 = dom + t2[1:] if t2.startswith('~') else t2
            cwd = '?' if ('$' in t2 or '`' in t2) else os.path.normpath(t2) if t2.startswith('/') else '?' if cwd == '?' else os.path.normpath(os.path.join(cwd, t2))
            continue
        if re.match(r'(?:node(?:js)?|perl(?:5[\d.]*)?|ruby[\d.]*)$', c0):         # KSZ3: iné interprety
            jaz = 'node' if c0.startswith('node') else c0[:4]
            kody, skript, argv, inpl = _iny_cli(jaz, a)
            if inpl:                                                       # perl -i / ruby -i: prepis súborov na mieste
                for x in argv:
                    if ciel(rozvin(dom + x[1:] if x.startswith('~') else x), cwd, root): return jaz + ' -i: prepis ' + os.path.basename(x)[:60]
            vlastny = r'(?<![\w.-])' + re.escape(c0) + r'\b'
            if skript == '-': kody = [b for ln, b in tela if re.search(vlastny, ln)]
            elif skript:
                s = dom + skript[1:] if skript.startswith('~') else skript
                s = s if s.startswith('/') else None if (cwd == '?' or '$' in s) else os.path.join(cwd, s)
                kody = [b for ln, b in tela if os.path.basename(skript) in ln and not re.search(vlastny, ln)]
                if s and not re.search(r'/\.aigrip/(?:scripts|hooks)/|^/opt/aigrip-ng/', os.path.normpath(s)):
                    try:
                        if os.path.getsize(s) <= 400000:
                            kody.append(open(s, encoding='utf-8', errors='replace').read())
                            if zber is not None: zber.append(kody[-1])
                    except OSError: pass
                else: kody = []
            for kod in kody:
                if zber is not None: break
                av = ([c0] + ([skript] if skript else []) + argv) if jaz == 'node' else argv
                try: r = _iny_kod(kod, jaz, av, cwd, senv, ciel, root)
                except Exception: r = _iny_hrubo(kod, jaz, meno)
                if r: return r
            continue
        if not re.match(r'python3?(\.\d+)?$', c0): continue
        i = 0
        while i < len(a) and a[i].startswith('-') and a[i] not in ('-', '-c', '-m'):
            i += 2 if a[i] in ('-W', '-X') else 1
        if i < len(a) and a[i] == '-m': continue
        if i < len(a) and a[i] == '-c': kody = a[i + 1:i + 2]; argv = ['-c'] + a[i + 2:]
        elif i >= len(a) or a[i] == '-': kody = pyt; argv = ['-'] + a[i + 1:]
        else:
            argv = a[i:]; s = dom + a[i][1:] if a[i].startswith('~') else a[i]
            s = s if s.startswith('/') else None if (cwd == '?' or '$' in s) else os.path.join(cwd, s)
            kody = [b for ln, b in tela if os.path.basename(a[i]) in ln and not re.search(r'python3?\b', ln)]   # skript, ktorý ten istý príkaz najprv zapíše
            if s and not re.search(r'/\.aigrip/(?:scripts|hooks)/|^/opt/aigrip-ng/', os.path.normpath(s)):
                try:
                    if os.path.getsize(s) <= 400000:
                        _t = open(s, encoding='utf-8', errors='replace').read()
                        if not _kopia_v_skuske(s, _t, argv):        # KSZ5: skúšobná kópia skriptu stroja sa nečíta
                            kody.append(_t)
                            if zber is not None: zber.append(_t)
                except OSError: pass
            else: kody = []
        for kod in kody:
            if zber is not None: break
            try: r = _ksz_kod(kod, argv, cwd, senv, ciel, root)
            except (SyntaxError, ValueError, RecursionError, MemoryError, AttributeError, TypeError, KeyError, IndexError):
                r = _ksz_hrubo(kod, meno)
            if r: return r
    return ''

_CMD = str((v.get('tool_input') or {}).get('command') or '')
_SKZ = set()                             # posudky, ktoré zaplo len meno v texte skriptu v súbore (kus KSZ4)
def _skp(x): return 'skript v súbore, ktorý príkaz spúšťa — ' if x in _SKZ else ''
if os.environ.get('NG_ZV'):
    # ---- vzory zápisníka, záznamu námietok a denníka nálezov nad príkazom bez tiel heredoc (kus KSZ4, 11. 10.) ----
    # Bash vzor našiel v príkaze s heredoc. Tu sa tie isté vzory posúdia znova: vzory shellu (>>, tee, sed -i, perl -i)
    # nad príkazom bez tiel heredoc, ktoré číta Python, node, perl alebo ruby alebo ktoré príkaz zapisuje do skriptu
    # v tých jazykoch (cat > x.py <<E) — reťazec ">>…/namietky.jsonl" v takom kóde zápisom shellu nie je; kód posúdi
    # rozbor (v príkaze hneď, skript v súbore pri spustení). Ostatné telá (pre shell, do iného súboru) ostávajú ako
    # doteraz.
    # KSZ6 (19. 10.): vzory Pythonu (open(…,'a'), write_text) hľadali v celom príkaze — 19. 10. 11:34 zastavili písanie
    # skúšobného skriptu (cat > skus5.py <<'PYEOF' s textom skúšobného prípadu open(A + '/<zápisník>', 'a')), ktorý nič
    # nezapisoval. Telo, ktoré príkaz len zapisuje do skriptu v súbore (> x.py, tee x.py) a nečíta ho interpret, sa tu
    # nehľadá: spustí ho ten istý príkaz -> posúdi ho rozbor kódu nižšie (skript, ktorý príkaz najprv zapíše), spustí ho
    # neskorší príkaz -> brána skriptu v súbore (KSZ4). Telo, ktoré číta interpret (python3 - <<E), ostáva ako doteraz.
    _tel, _tela = G['_telá'](_CMD)
    _INT = re.compile(r'(?:^|[\s;|&(])(?:python3?(?:\.\d+)?|node(?:js)?|perl|ruby)\b[^|;&]*<<')
    _VYN = re.compile(_INT.pattern + r'|>[ \t]*["\']?[^\s;|&"\'<>]+\.(?:py|js|mjs|cjs|pl|rb)\b')
    _DOS = re.compile(r'(?:>[ \t]*|\btee(?:[ \t]+-a)?[ \t]+)["\']?[^\s;|&"\'<>]+\.(?:py|js|mjs|cjs|pl|rb)\b')
    # KSZ8 (19. 10.): telo, ktoré príkaz len zapisuje do dátového súboru (stránka, text, tabuľka) — 12. 10. zastavilo
    # u kolegu `cat hlavička.html - > týždeň.html <<'EOF'` s `<code>NG-zaznamy.md</code>` v texte stránky („>“ pred menom
    # vzal vzor za presmerovanie). Také telo je text, nie príkazy: nehľadá sa v ňom žiadny vzor. Len keď riadok s <<
    # má jediné telo, nemá rúru ani nič, čo text spúšťa (shell, ssh, eval, interpret, xargs, sudo …), jeho príkazy sú len cat/tee
    # (a cd, mkdir, echo, printf, priradenie premennej), ciele sú dátové súbory a ten istý príkaz žiadny z nich nespúšťa. Zápis do chráneného
    # súboru v riadku s << (cat >> NG-zaznamy.md <<E) ostáva zastavený — vidí ho riadok bez tiel.
    _DAT = re.compile(r'\.(?:html?|md|markdown|txt|csv|tsv|json|jsonl|ya?ml|xml|svg|css|ini|toml|cfg|conf|log|rst|tex|eml|ics|vcf)$', re.I)
    _BEH = re.compile(r'(?:^|[\s;|&(`])(?:(?:ba|da|z|k)?sh|ssh|eval|source|exec|xargs|parallel|sudo|su|env|at|batch|crontab|chmod'
                      r'|timeout|nohup|python3?(?:\.\d+)?|node(?:js)?|perl|ruby|\.)(?=[\s;|&)]|$)|(?:^|[;|&(`])\s*\./')
    _OK = {'cat', 'tee', 'cd', 'pushd', 'mkdir', 'echo', 'printf', 'true', ':'}

    def _do_dat(ln):
        if '|' in ln or ln.count('<<') != 1 or _BEH.search(ln): return False
        try: P = G['_prikazy'](ln)
        except Exception: return False
        ciele = []
        for w, r in P:
            w = w[next((i for i, x in enumerate(w) if not re.match(r'[A-Za-z_]\w*=', x)), len(w)):]   # X=… pred príkazom
            if not w:
                if r: return False
                continue                                          # samé priradenie (X=…) vstup nečíta
            if os.path.basename(w[0]) not in _OK: return False
            if w[0] in ('cat', 'tee'):
                ciele += r + ([x for x in w[1:] if not x.startswith('-')] if w[0] == 'tee' else [])
        ciele = [c for c in ciele if c != '/dev/null']
        if not ciele or not all(_DAT.search(c) for c in ciele): return False
        for s in re.split(r'\n|;|&&|\|\|', _tel):          # spustí ho ten istý príkaz (bash x.txt, cat x.md | sh, ./x)
            if s.strip() != ln.strip() and _BEH.search(s) and any(os.path.basename(c) in s for c in ciele): return False
        return True
    _dat = {i for i, (ln, b) in enumerate(_tela) if _do_dat(ln)}
    _sh = _tel + ''.join('\n' + b for i, (ln, b) in enumerate(_tela) if not _VYN.search(ln) and i not in _dat)
    _py = _tel + ''.join('\n' + b for i, (ln, b) in enumerate(_tela) if _INT.search(ln) or (not _DOS.search(ln) and i not in _dat))

    def _sed_i(sh, S):
        """sed -i mení súbor s menom S (kus KSZ8): výraz súborom nie je. Bez -e/-f je prvý argument výraz a súbory sú
        ostatné, s -e/-f sú súbormi všetky (BSD `sed -i '' výraz súbor`: prázdna prípona sa preskočí). Príkaz, ktorý sa
        rozobrať nedá, posúdi pôvodný vzor nad celým príkazom."""
        try: P = G['_prikazy'](sh)
        except Exception: return bool(re.search(r'sed[ \t\n]+(?:-[a-zA-Z]*i|--in-place)[^;|&]*' + S, sh))
        for w, _ in P:
            for j, x in enumerate(w):
                if os.path.basename(x) not in ('sed', 'gsed'): continue
                if {'xargs', 'parallel', 'find'} & {os.path.basename(y) for y in w[:j]}:   # súbory prídu odinakiaľ
                    if re.search(r'sed[ \t\n]+(?:-[a-zA-Z]*i|--in-place)[^;|&]*' + S, sh): return True
                    continue
                ina = vyraz = koniec = False; arg = []; k = j + 1
                while k < len(w):
                    a = w[k]; k += 1
                    if koniec or a == '-' or not a.startswith('-'): arg.append(a); continue
                    if a == '--': koniec = True; continue
                    if a.startswith('--'):
                        n, rov, _ = a.partition('=')
                        if len(n) >= 4 and '--in-place'.startswith(n): ina = True
                        elif (len(n) >= 3 and '--expression'.startswith(n)) or (len(n) >= 4 and '--file'.startswith(n)):
                            vyraz = True; k += 0 if rov else 1
                        elif len(n) >= 3 and '--line-length'.startswith(n): k += 0 if rov else 1
                        continue
                    for m, ch in enumerate(a[1:], 1):
                        if ch == 'i': ina = True; break                      # zvyšok je prípona zálohy
                        if ch in 'efl':
                            if ch != 'l': vyraz = True
                            if m == len(a) - 1: k += 1                     # argument je ďalšie slovo
                            break
                if not ina: continue
                if not vyraz and arg[:1] == ['']: arg = arg[1:]             # BSD: sed -i '' výraz súbor
                if any(re.search(S, f) for f in (arg if vyraz else arg[1:])): return True
        return False
    for _z, _S in (('z', r'(?:NG-zaznamy\.md|NAVRHY\.md|navrhy-[^ "\']*\.md)'),
                   ('n', r'(?:namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log)'), ('d', r'stroj-nalezy\.jsonl')):
        if _z not in os.environ['NG_ZV']: continue
        if (re.search(r'(?:>>?|\btee(?: -a)?)[ \t\n]*["\']?[^ ;|&"\']*' + _S, _sh)
                or _sed_i(_sh, _S)
                or (_z == 'd' and re.search(r'(?:perl|ruby)[ \t\n]+-[A-Za-z]*i[^;|&]*' + _S, _sh))
                or re.search(r'open\([^)]*' + _S + r'[^)]*,[ \t\n]*["\'][wa+]', _py)
                or (re.search(r'(?:write_text|writelines)\(', _py) and re.search(r'Path\([^)]*' + _S, _py))):
            sys.stdout.buffer.write(('V\t' + _z).encode('utf-8'))
            sys.exit(0)

if os.environ.get('NG_SK') == '1':
    # ---- skript v súbore, ktorého príkaz chránený súbor nemenuje (kus KSZ4, 11. 10.) ----
    # Texty skriptov mimo stroja, ktoré príkaz spúšťa interpretom; mená chránených súborov v nich zapnú posudky nižšie
    # (tie skript prečítajú znova a rozoberú). Chyba = nič nezapnúť (pustiť).
    _txt = []
    try: _ksz_prikaz(_CMD, v.get('cwd') or (sys.argv[1].rstrip('/') or '/'), sys.argv[1].rstrip('/') or '/', lambda *a: False, r'$^', zber=_txt)
    except Exception: _txt = []
    _T = '\n'.join(_txt)
    if _T:
        if re.search(r'(?<![\w.-])STAVBA\.md(?![\w.-])', _T) and not re.search(r'ng-stav\.py|ng-postup\.py|ng-celok\.py|ng-mapa-cesty\.py', _CMD):
            if os.environ.get('NG_SS') != '1': _SKZ.add('S')
            os.environ['NG_SS'] = '1'
        if re.search(r'NG-zaznamy\.md|NAVRHY\.md|navrhy-[^ ]*\.md|namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log|stroj-nalezy\.jsonl', _T):
            if os.environ.get('NG_ZZ') != '1': _SKZ.add('Z')
            os.environ['NG_ZZ'] = '1'
        if re.search(r'(?<![\w.-])(?:' + os.environ['NG_POHLADY'] + r')\.md(?![\w.-])', _T):
            if os.environ.get('NG_PP') != '1': _SKZ.add('P')
            os.environ['NG_PP'] = '1'

if os.environ.get('NG_SS') == '1':
    # ---- STAVBA.md projektu aigrip (kus SB, 10. 10.) ----
    def stavba(p, cwd, root):
        """Cieľ zápisu je STAVBA.md projektu aigrip — ten istý určovateľ cieľa ako stráž pri Edit/Write
        (ng-p5-straz.py _je_stavba): meno súboru a meno priečinka projektu. Cesta sa rozvinie rovnako ako pri
        pohľadoch (~, $HOME, $CLAUDE_PROJECT_DIR, $PWD) a doplní priečinkom, v ktorom príkaz beží; kópia v /tmp
        alebo v skúšobnom priečinku bez priečinka aigrip ňou nie je, nerozvinutá premenná -> nie."""
        if not p: return False
        p = str(p).strip()
        dom = '/'.join(root.split('/')[:3])
        for k, h in (('CLAUDE_PROJECT_DIR', root), ('PWD', cwd if cwd != '?' else '$PWD'), ('HOME', dom)):
            p = p.replace('${' + k + '}', h).replace('$' + k, h)
        if p == '~' or p.startswith('~/'): p = dom + p[1:]
        if '$' in p or '`' in p or '{' in p or p.startswith('/dev/'): return False
        if not p.startswith('/') and cwd == '?': return False
        a = os.path.normpath(p if p.startswith('/') else os.path.join(cwd or root, p))
        # P6K2 (1. 11. 2026): meno priečinka sa porovnáva BEZ OHĽADU na veľkosť písmen — tak, ako to robí
        # `_je_stavba` v ng-p5-straz.py, ktorý tento určovateľ podľa docstringu hore zrkadlí. Klon PS na Macu je
        # `~/CLAUDE/AIGrip`, takže príkazom z Macu prešli 26. a 27. 10. položky 4.158, 4.159, 4.160, 4.168 a 4.169
        # bez posúdenia pravidlom 5 a 6 a štyri z nich ostali bez odseku o námietkach (nález 1. 11. 2026). Je to tá
        # istá diera, ktorú kus P6K 11. 10. zavrel na strane Edit/Write a na strane príkazu ostala otvorená.
        return os.path.basename(a) == 'STAVBA.md' and os.path.basename(os.path.dirname(a)).lower() == 'aigrip'

    G['_v'] = stavba                     # určovateľ zápisu ostáva spoločný, mení sa len cieľ
    root = sys.argv[1].rstrip('/') or '/'
    cwd0 = v.get('cwd') or root
    _, zapis = G['konanie']('Bash', v.get('tool_input') or {}, cwd0, root, proj='\x00')
    # Dva prepisy, ktoré spoločný určovateľ nevidí (nález skúšky 10. 10.; platí aj pri zápisníku a pohľadoch — je to
    # zapísané ako nález, nie opravené tu, aby sa nepohlo meranie pravidla 2): `perl -i` (pozná len `sed -i`) a atomický
    # patcher, ktorý cieľ drží v premennej (`C = '…/STAVBA.md'; os.replace(tmp, C)`) — práve tak sa v tomto projekte píšu
    # väčšie úpravy, keď Edit zlyhá na živom súbore. Hľadá sa v príkaze BEZ TIEL HEREDOC, takže písanie pomocného
    # skriptu heredocom (ktorý meno dokumentu len obsahuje) sa nezastaví.
    if not zapis:
        _tel, _ = G['_telá'](str((v.get('tool_input') or {}).get('command') or ''))
        if re.search(r'(?:os\.replace|os\.rename|shutil\.(?:copy\w*|move))\(|\b(?:perl|ruby)\s+-[A-Za-z]*i\b'
                     r'|\btruncate\b|\bdd\b[^|;&]*\bof=', _tel):
            _kand = [x for _w, _r in G['_prikazy'](_tel) for x in _w] + [_tel]
            # cesta môže byť v úvodzovkách vnútri slova (`python3 -c "… C='…' …"`) — preto ešte jedna vrstva
            _kand += [m.group(2) for _s in list(_kand) for m in G['_STR'].finditer(_s)]
            _kand += [m.group(2) for _s in list(_kand) for m in G['_STR'].finditer(_s)]
            if any(stavba(x, cwd0, root) for x in _kand):
                zapis = 'prepis dokumentu (cieľ v premennej alebo perl -i)'
    # KSZ (11. 10.): zápis v kóde Pythonu, kde cieľ nie je jeden literál, ale cesta ZLOŽENÁ — `AIG + '/STAVBA.md'`,
    # os.path.join(AIG, 'STAVBA.md'), f'{AIG}/STAVBA.md', Path(AIG) / 'STAVBA.md', parameter funkcie, sys.argv. Spoločný
    # určovateľ z takého výrazu rozrieši len PRVÝ kus cesty (priečinok projektu): pre pravidlo 2 to stačí („zápis
    # v projekte“), pre jeden menovaný súbor nie — 11. 10. 05:13 tak náhradný beh vložil položku 4.100 cez dočasný súbor
    # a os.replace(AIG + '/STAVBA.md.tmp-…', AIG + '/STAVBA.md') a stráž mlčala. Tu sa kód rozoberie (ast), mená sa
    # dosadia (bez ohľadu na rozsah platnosti — viac hodnôt = skúsia sa všetky) a cieľ každej zápisovej operácie sa
    # vyhodnotí celý. Číta sa kód v príkaze (heredoc, -c) aj skript v súbore, ktorý príkaz spúšťa a ktorému dokument
    # menuje (python3 zaplata.py STAVBA.md) — okrem skriptov stroja. Cieľ, ktorý sa vyhodnotiť nedá, zápisom nie je
    # (čítanie dokumentu a zápis inam ostáva voľný). Rozbor sám je od kusu KSZ2 spoločný (hore), tu sa mu dá len cieľ.
    if not zapis:
        zapis = _ksz_prikaz(str((v.get('tool_input') or {}).get('command') or ''), cwd0, root, stavba, r'STAVBA\.md$')
    if zapis and zapis.split()[0] not in ('rm', 'unlink', 'rmdir'):     # zmazanie dokumentu je iná vec (git ho vráti)
        sys.stdout.buffer.write(('S\t' + _skp('S') + zapis.replace('\n', ' ')[:160]).encode('utf-8'))
        sys.exit(0)

if os.environ.get('NG_ZZ') == '1':
    # ---- zápisník, návrhy, záznam námietok a denník nálezov časti 6: zápis kódom so zloženou cestou (kus KSZ2, 11. 10.) ----
    # Vzory v bashi vidia len meno súboru napísané priamo v open(…) alebo Path(…) či za presmerovaním; pripísanie cez
    # open(os.path.join(A, 'NG-zaznamy.md'), 'a') alebo cez dočasný súbor a premenovanie nimi prešlo (suché otázky
    # 11. 10.). Cieľ je tu užší než vo vzoroch: len súbor v projekte (<domov>/projects/<projekt>/…) — skúšobná kópia
    # s tým istým menom v /tmp alebo v dielni chránený súbor nie je. Výnimka pre meno skriptu stroja v príkaze tu nie je:
    # skript stroja v súbore sa nečíta vôbec, kód v príkaze vedľa neho sa posúdi ako každý iný.
    ZZC = (('zapisnik', re.compile(r'^/home/[^/]+/projects/[^/]+/(?:NG-zaznamy\.md|(?:\.aigrip/ng/)?(?:NAVRHY|navrhy-[^/]*)\.md)$')),
           ('namietky', re.compile(r'^/home/[^/]+/projects/[^/]+/\.aigrip/ng/(?:namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log)$')),
           ('dennik', re.compile(r'^/home/[^/]+/projects/[^/]+/\.aigrip/ng/stroj-nalezy\.jsonl$')))
    ZZM = r'(?:NG-zaznamy\.md|NAVRHY\.md|navrhy-[^/]*\.md|namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log|stroj-nalezy\.jsonl)$'
    ZZD = {}

    def chraneny(p, cwd, root):
        """Cieľ zápisu je zápisník, návrhy, záznam námietok alebo denník nálezov v projekte; cesta sa rozvinie ako pri pohľadoch."""
        if not p: return False
        p = str(p).strip()
        dom = '/'.join(root.split('/')[:3])
        for k, h in (('CLAUDE_PROJECT_DIR', root), ('PWD', cwd if cwd != '?' else '$PWD'), ('HOME', dom)):
            p = p.replace('${' + k + '}', h).replace('$' + k, h)
        if p == '~' or p.startswith('~/'): p = dom + p[1:]
        if '$' in p or '`' in p or '{' in p or p.startswith('/dev/'): return False
        if not p.startswith('/') and cwd == '?': return False
        a = os.path.normpath(p if p.startswith('/') else os.path.join(cwd or root, p))
        for d, r in ZZC:
            if r.match(a): ZZD.setdefault('druh', d); return True
        return False

    root = sys.argv[1].rstrip('/') or '/'
    zapis = _ksz_prikaz(str((v.get('tool_input') or {}).get('command') or ''), v.get('cwd') or root, root, chraneny, ZZM)
    if zapis:
        d = ZZD.get('druh') or ('namietky' if re.search(r'namietky\.jsonl|cisla-casti4\.jsonl|oponent\.log', zapis)
                                else 'dennik' if 'stroj-nalezy' in zapis else 'zapisnik')
        sys.stdout.buffer.write(('Z\t' + d + '\t' + _skp('Z') + zapis.replace('\n', ' ')[:160]).encode('utf-8'))
        sys.exit(0)

if os.environ.get('NG_NN') == '1':
    # ---- nový súbor starého druhu ----
    STARY = re.compile(r'^(/.+)/map/(?:decisions/(D)|learnings/(L)|questions/(Q)|journal/(J))-[^/]*\.md$|^(/.+)/(INTENT)\.md$')
    root = sys.argv[1].rstrip('/') or '/'
    k = root.find('/projects/'); dom = root[:k] if k > 0 else '/'.join(root.split('/')[:3])
    cmd = str((v.get('tool_input') or {}).get('command') or '')

    def cesta(p, cwd):
        """Absolútna cesta (~, $HOME, $CLAUDE_PROJECT_DIR, $PWD rozvinuté, doplnená priečinkom príkazu); neistá -> None."""
        if not p: return None
        p = str(p).strip()
        for n, h in (('CLAUDE_PROJECT_DIR', root), ('PWD', cwd if cwd != '?' else '$PWD'), ('HOME', dom)):
            p = p.replace('${' + n + '}', h).replace('$' + n, h)
        if p == '~' or p.startswith('~/'): p = dom + p[1:]
        if any(c in p for c in '$`{*?') or p.startswith('/dev/'): return None
        if not p.startswith('/') and cwd == '?': return None
        return os.path.normpath(p if p.startswith('/') else os.path.join(cwd or root, p))

    def druh(a):
        m = STARY.match(a or '')
        return (next(x for x in m.groups()[1:5] + m.groups()[6:] if x), m.group(1) or m.group(6)) if m else (None, None)

    def novy(a):
        """a je súbor starého druhu, ktorý ešte neexistuje, v projekte nového spôsobu."""
        d, kor = druh(a)
        return bool(d) and os.path.isfile(os.path.join(kor, 'NG-zaznamy.md')) and not os.path.lexists(a)

    # cp/mv/install/ln/rsync/scp: cieľ môže byť priečinok (súbor dostane meno zdroja); mv starého súboru na meno toho istého
    # druhu v tom istom priečinku je premenovanie, nie nový záznam. Premenné, export a obaly (timeout 5, env X=1, nice, command …)
    # ako v určovateľovi (export X=…; timeout 5 cp … $X/). touch určovateľ nerieši (pre pravidlo 2 nie je zápisom obsahu); tu
    # zakladá súbor. (Oprava nezávislého overenia 25. 9.: obal, export a rsync do priečinka predtým prešli.)
    KOPIE = {}; env = {}; DOTYK = ''; OBAL = G.get('_OBAL') or {}
    def rozvin(x): return re.sub(r'\$\{?([A-Za-z_]\w*)\}?', lambda m: env.get(m.group(1), m.group(0)), x)

    def jadro(w):
        """Slová príkazu bez priradení pred ním a bez obalov s ich voľbami — rovnako ako v určovateľovi."""
        while w and (re.match(r'^\w+=', w[0]) or w[0] in OBAL):
            o = OBAL.get(w[0], set()); tm = w[0] == 'timeout'; w = w[1:]
            while w and w[0].startswith('-') and w[0] != '-':
                w = w[2:] if w[0] in o else w[1:]
            if tm and w: w = w[1:]
        return w

    tel, _ = G['_telá'](cmd)
    cwd0 = v.get('cwd') or root
    for w, _r in G['_prikazy'](tel):
        w = [rozvin(x) for x in w]
        if w and w[0] in ('export', 'local', 'declare', 'readonly'): w = w[1:]
        if w and all(re.match(r'^[A-Za-z_]\w*=', x) for x in w):
            for x in w: n, _, h = x.partition('='); env[n] = rozvin(dom + h[1:] if h.startswith('~') else h)
            continue
        w = jadro(w)
        if not w: continue
        c0 = os.path.basename(w[0]); a = w[1:]; arg = [x for x in a if not x.startswith('-')]
        if c0 == 'cd':
            t2 = arg[0] if arg else dom
            t2 = dom + t2[1:] if t2.startswith('~') else t2
            cwd0 = '?' if ('$' in t2 or '`' in t2) else os.path.normpath(t2) if t2.startswith('/') else '?' if cwd0 == '?' else os.path.normpath(os.path.join(cwd0, t2))
        elif c0 == 'touch':
            if not DOTYK and any(novy(cesta(x, cwd0)) for x in arg): DOTYK = 'touch'
        elif c0 in ('cp', 'mv', 'install', 'ln', 'rsync', 'scp') and len(arg) >= 2:
            d = a[a.index('-t') + 1] if '-t' in a and a.index('-t') + 1 < len(a) else arg[-1]
            KOPIE[d] = (c0, [x for x in arg if x != d])

    # Určovateľ vráti PRVÝ zápis v príkaze. Keby ním bolo rm/sed -i/git na súbor starého druhu, ktorý neexistuje
    # (rm -f X; cat > X), ďalšie príkazy by sa už nepozreli a založenie by prešlo (nález overenia 25. 9.). Preto pri
    # rm/unlink/rmdir/sed/git slová príkazu cieľom nie sú; ciele presmerovania (sed … > X) áno — určovateľ ich pri každom
    # príkaze skúša ako prvé, toľkokrát, koľko ich príkaz má.
    STAV = {'c0': '', 'nr': 0, 'n': 0}
    _pr = G['_prikazy']
    def prikazy(c):
        for w, r in _pr(c):
            j = list(w)
            if j and j[0] in ('export', 'local', 'declare', 'readonly'): j = j[1:]
            j = jadro(j)
            STAV.update(c0=os.path.basename(j[0]) if j else '', nr=len(r), n=0)
            yield w, r

    def ciel(p, cwd, _root):
        STAV['n'] += 1
        if STAV['n'] > STAV['nr'] and STAV['c0'] in ('rm', 'unlink', 'rmdir', 'sed', 'git'): return False
        a = cesta(p, cwd)
        if not a: return False
        if p in KOPIE:
            c0, zdroje = KOPIE[p]
            for s in zdroje:
                b = os.path.join(a, os.path.basename(s.rstrip('/'))) if os.path.isdir(a) else a
                if not novy(b): continue
                zs = cesta(s, cwd)
                if c0 == 'mv' and zs and druh(zs)[0] == druh(b)[0] and os.path.dirname(zs) == os.path.dirname(b): continue
                return True
            return False
        return novy(a)

    G['_v'] = ciel; G['_prikazy'] = prikazy     # určovateľ zápisu ostáva spoločný, mení sa len cieľ
    try: _, zapis = G['konanie']('Bash', v.get('tool_input') or {}, v.get('cwd') or root, root, proj='\x00')
    finally: G['_prikazy'] = _pr
    if zapis and zapis.split()[0] in ('rm', 'unlink', 'rmdir', 'sed', 'git'): zapis = ''   # zmazanie, úprava, git — nič nové
    zapis = zapis or DOTYK
    if zapis:
        sys.stdout.buffer.write(('N\t' + zapis.replace('\n', ' ')[:160]).encode('utf-8'))
        sys.exit(0)

if os.environ.get('NG_PP') == '1':
    # ---- pohľady stroja (bez zmeny od 25. 9.) ----
    MENA = os.environ['NG_POHLADY']
    POHLAD = re.compile(r'^/home/[^/]+/projects/[^/]+/\.aigrip/ng/(?:' + MENA + r')\.md$')

    def pohlad(p, cwd, root):
        """Cieľ zápisu je pohľad stroja: <domov>/projects/<projekt>/.aigrip/ng/<pohľad>. Rovnako ako pôvodný určovateľ:
        cesta sa rozvinie (~, $HOME, $CLAUDE_PROJECT_DIR, $PWD) a doplní priečinkom, v ktorom príkaz beží. Kópia v /tmp alebo
        v skúšobnom priečinku (…/sim/x/.aigrip/ng/…) pohľad nie je; nerozvinutá premenná alebo neznámy priečinok -> nie."""
        if not p: return False
        p = str(p).strip()
        dom = '/'.join(root.split('/')[:3])
        for k, h in (('CLAUDE_PROJECT_DIR', root), ('PWD', cwd if cwd != '?' else '$PWD'), ('HOME', dom)):
            p = p.replace('${' + k + '}', h).replace('$' + k, h)
        if p == '~' or p.startswith('~/'): p = dom + p[1:]
        if '$' in p or '`' in p or '{' in p or p.startswith('/dev/'): return False
        if not p.startswith('/') and cwd == '?': return False
        return bool(POHLAD.match(os.path.normpath(p if p.startswith('/') else os.path.join(cwd or root, p))))

    G['_v'] = pohlad                     # určovateľ zápisu ostáva spoločný, mení sa len cieľ
    root = sys.argv[1].rstrip('/') or '/'
    _, zapis = G['konanie']('Bash', v.get('tool_input') or {}, v.get('cwd') or root, root, proj='\x00')
    # KSZ2 (11. 10.): prepis pohľadu kódom so zloženou cestou (A + '/.aigrip/ng/KDE-STOJIME.md', os.path.join, dočasný súbor
    # a premenovanie) spoločný určovateľ nevidí — rozbor kódu ako pri dokumente stavby, cieľ = pohľad.
    if not zapis: zapis = _ksz_prikaz(str((v.get('tool_input') or {}).get('command') or ''), v.get('cwd') or root, root, pohlad, r'\.aigrip/ng/(?:' + MENA + r')\.md$')
    if zapis and zapis.split()[0] not in ('rm', 'unlink', 'rmdir'):     # zmazanie pohľadu nie je zápis; ďalší beh ho prekreslí
        R = r'(?<![\w.-])(' + MENA + r')\.md(?![\w.-])'                  # meno z cieľa zápisu, inak prvé v príkaze
        m = re.search(R, zapis) or re.search(R, v.get('tool_input', {}).get('command', ''))
        sys.stdout.buffer.write(('P\t' + (m.group(0) if m else 'KDE-STOJIME.md') + '\t' + _skp('P') + zapis.replace('\n', ' ')[:160]).encode('utf-8'))
PY
V=$(printf '%s' "$IN" | NG_BLOK="$BLOK" NG_POHLADY="$POHLADY" NG_NN="$NN" NG_PP="$PP" NG_SS="$SS" NG_ZZ="$ZZ" NG_SK="$SK" NG_ZV="$ZV" python3 -S -c "$KOD" "${CLAUDE_PROJECT_DIR:-$PWD}" 2>/dev/null) \
  || { RCV=$?; [ "$RCV" != 4 ] && [ "$SS" = 1 ] && _sb_zastav "určovateľ zápisu sa nepustil"; [ -n "$ZV" ] && _zv_zastav "$ZV"; exit 0; }
[ -n "$V" ] || exit 0
case "$V" in V$'\t'*) _zv_zastav "${V#V$'\t'}" ;; esac      # KSZ4: vzor potvrdený nad príkazom bez tiel heredoc
DOD=""                                              # KSZ4: zastavenie len z textu skriptu v súbore to povie
case "$V" in *"skript v súbore, ktorý príkaz spúšťa"*) DOD=" Stráž prečítala skript, ktorý príkaz spúšťa: podľa rozboru kódu vie zapísať do tohto súboru (režim behu — pieskovisko či výpis na výstup — z textu skriptu nevidí)." ;; esac
case "$V" in S$'\t'*) _sb_zastav "${V#S$'\t'}" ;; esac
if [ "${V%%$'\t'*}" = Z ]; then                     # KSZ2: zápis kódom do zápisníka, návrhov, záznamu námietok alebo denníka nálezov
  V=${V#Z$'\t'}; ZD=${V%%$'\t'*}
  [ "$ZD" = zapisnik ] && [ ! -r "$ZAPIS" ] && exit 0  # jediná cesta zápisu nie je čitateľná = pustiť (ako vzory vyššie)
  printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "append-only kódom (${V#*$'\t'}) · $(printf '%s' "$CMD" | head -c 120 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
  case "$ZD" in
    namietky) _zz_zastav "$R_NAMIETKY$DOD" ;;
    dennik) _zz_zastav "$R_DENNIK$DOD" ;;
    *) _zz_zastav "$R_ZAPISNIK$DOD" ;;
  esac
fi
if [ "${V%%$'\t'*}" = N ]; then
  printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "nový starý záznam (${V#N$'\t'}) · $(printf '%s' "$CMD" | head -c 120 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
  jq -n --arg r "V novom spôsobe sa nové rozhodnutia, poučenia, otázky ani denník nezakladajú ako samostatné súbory — zapíš jednu vetu cez ng-zapis.py (typ rozhodnutie · poznatok · zamer); staré súbory ostávajú ako podklad." \
     '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
  exit 0
fi
V=${V#P$'\t'}
B=${V%%$'\t'*}
case "$B" in
  KDE-STOJIME.md) R="Pohľad „kde stojíme“ kreslí stroj z vety „kde som“ a zápisníka — ak ho treba zmeniť, zapíš vetu cez ng-kdesom.py alebo záznam cez ng-zapis.py; pohľad sa prekreslí sám." ;;
  MAPA.md) R="Mapu práce kreslí stroj zo zápisníka — ak ju treba zmeniť, zapíš záznam cez ng-zapis.py; mapa sa prekreslí sama." ;;
  odislo-von.md) R="Pohľad „čo odišlo von“ kreslí stroj zo záznamov a zo stôp odoslania — ak v ňom niečo chýba, zapíš záznam cez ng-zapis.py; pohľad sa prekreslí sám." ;;
  namietky.md) R="Čitateľný výpis námietok nezávislých behov kreslí stroj zo záznamov behov (ng-p5-straz.py vypis-namietok, inak hodinový beh stavu) — námietku do neho nepíš rukou a nemaž ju z neho: dôkaz o oponentúre si nesmie napísať ten, koho posudzuje (kus P6V). Námietku smieš odmietnuť — odrážkou v odseku položky s dôvodom." ;;
  *) K="${B%.md}"; R="Pohľad $B kreslí stroj z meraní a zápisníkov — ak je v ňom niečo zle, oprav v aigrip skript, ktorý ho kreslí (.aigrip/scripts/ng-${K,,}.py), alebo zapíš záznam cez ng-zapis.py; pohľad sa prekreslí sám." ;;
esac
printf '%s\t%s\t%s\n' "$(date +%Y-%m-%dT%H:%M:%S)" "Bash" "pohľad ${V#*$'\t'} · $(printf '%s' "$CMD" | head -c 120 | tr '\n' ' ')" >> "$LOG" 2>/dev/null || true
jq -n --arg r "$R$DOD (Skripty stroja sú v $STROJ/scripts.)" \
   '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
