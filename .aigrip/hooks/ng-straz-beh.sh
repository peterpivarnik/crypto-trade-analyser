#!/usr/bin/env bash
# Stráže časti 3 v háčikoch (B17, 2. 10. 2026) — knižnica, ktorú si háčik načíta jedným riadkom hneď po prečítaní vstupu
# do premennej IN (kotva, stráž zápisníka pre úpravu súborov aj pre príkazový riadok):
#   ng_beh_stopa <meno>   stopa behu — dôkaz, že stráž v tom sedení naozaj bežala (u každého človeka, v každom projekte)
#   ng_audit_straz        audity sa smú len dopĺňať — zápis, zmazanie, presun či prepis auditu = zákaz (ako pri zápisníku)
#
# Prečo stopa behu (posúdenie časti 3, B5): kotva ani stráž zápisníka nezapisovali, že bežali; kontrolovalo sa len, že sú
# zapísané v nastaveniach projektu. Háčik vypnutý v osobných nastaveniach (disableAllHooks), sedenie spustené v podpriečinku
# projektu bez jeho nastavení alebo háčik, ktorý padne skôr, než rozhodne, nikto nevidel — tichá stráž vyzerá ako zapnutá.
# Ako: pri skončení háčika (EXIT trap — aj pri chybe; nie pri zabití signálom KILL, to kontrola uvidí ako chýbajúci beh)
# sa k sedeniu pripočíta jeden beh (jeden bajt do počítadla ~/.aigrip-ng/beh/<sedenie>.<háčik>, pod zámkom — súbežné
# príkazy toho istého sedenia sa nepomýlia). Riadok do stopy sa zapíše len pri prvom behu v sedení, pri každom skončení
# s chybou a pri 2., 4., 8., 16. … behu: počet behov v sedení je tak známy s presnosťou na polovicu pri najviac
# log2(n)+1 riadkoch. Kontrola (ng-beh-strazcov.py) ho porovná s počtom príkazov v stope sedenia.
# Kam: u PS do .aigrip/ng/beh-strazcov.jsonl v aigrip — nie do hook-stopy.jsonl: tisíce príkazov denne by ju orezali
# a kontrola háčikov (okno 2 dni) by prestala vidieť štarty sedení. U kolegu do jeho ~/.aigrip-ng/hook-stopy.jsonl, ktorú
# správca už zbiera do /var/lib/aig-ng/stopy/<kolega>/ (bez zmeny zberu; u kolegu sú to desiatky riadkov denne a orezáva
# ju ng-hook-stopa.sh ako doteraz).
# Skúšobný vstup bez session_id v tvare UUID (červený test kotvy, prehrávka) nič nezapíše. NG_BEH_STOPY (cieľ riadkov)
# a NG_BEH_DIR (počítadlá) sú na pieskovisko: keby ich niekto nastavil v ostrom behu, stopa zmizne a kontrola to ohlási
# ako nález — nevypína sa tým nič ticho.
# Bounded: bez siete, bez Pythonu; na beh jeden zámok (flock), jedno čítanie a jeden zápis malého súboru. Chyba knižnice
# háčik nezhodí a nezmení jeho výstup ani návratový kód (trap nič nevypisuje a nevolá exit).

_NG_BEH_SID_RE='"session_id"[[:space:]]*:[[:space:]]*"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"'

ng_beh_stopa() {
  _NG_BEH_MENO=${1:-?}
  trap '_ng_beh_koniec "$?"' EXIT
}

_ng_beh_koniec() {
  local rc=${1:-0} h=${IN:-} sid="" d f n=0 prvy=0 ok=0 kam kto proj t x=""
  # sedenie z hlavičky vstupu (Claude Code dáva session_id ako prvé pole, pred tool_input)
  h=${h:0:800}
  [[ $h =~ $_NG_BEH_SID_RE ]] && sid=${BASH_REMATCH[1]}
  [ -n "$sid" ] || return 0
  d=${NG_BEH_DIR:-${HOME:-/tmp}/.aigrip-ng/beh}
  f="$d/$sid.${_NG_BEH_MENO:-?}"
  [ -d "$d" ] || mkdir -p "$d" 2>/dev/null || return 0
  # počet behov = veľkosť počítadla; pripísanie a prečítanie pod zámkom, aby dva súbežné behy nevideli to isté číslo
  # (presmerovanie na zloženom príkaze: keď sa počítadlo nedá otvoriť, príkaz zlyhá, shell neskončí, stopa sa nezapíše)
  {
    command -v flock >/dev/null 2>&1 && flock -x -w 2 9
    printf 1 >&9 && ok=1
    IFS= read -r -N 1000000 x < "$f"
  } 2>/dev/null 9>>"$f"
  [ "$ok" = 1 ] || return 0
  n=${#x}
  [ "$n" -gt 0 ] 2>/dev/null || n=1
  [ "$n" = 1 ] && prvy=1
  if [ "$prvy" = 1 ] || [ "$rc" != 0 ] || [ $(( n & (n - 1) )) = 0 ]; then
    kam=${NG_BEH_STOPY:-/home/pstrocka/projects/aigrip/.aigrip/ng/beh-strazcov.jsonl}
    if [ -z "${NG_BEH_STOPY:-}" ] && ! { [ -w "$kam" ] || { [ ! -e "$kam" ] && [ -w "${kam%/*}" ]; }; }; then
      kam="${HOME:-/tmp}/.aigrip-ng/hook-stopy.jsonl"     # kolega: súbor, ktorý správca zbiera
    fi
    proj=${CLAUDE_PROJECT_DIR:-$PWD}; proj=${proj%/}; proj=${proj##*/}; proj=${proj//[\"\\]/_}
    kto=${USER:-${HOME##*/}}; kto=${kto//[\"\\]/_}
    printf -v t '%(%Y-%m-%dT%H:%M:%S%z)T' -1; t="${t:0:22}:${t:22}"
    { printf '{"ts":"%s","projekt":"%s","hook":"%s-beh","sid":"%s","n":%d,"rc":%d,"kto":"%s"}\n' \
        "$t" "$proj" "${_NG_BEH_MENO:-?}" "$sid" "$n" "$rc" "$kto" >> "$kam"; } 2>/dev/null
    if [ "$prvy" = 1 ]; then
      # upratanie: počítadlá sedení nečinných 8 dní — dlhšie než okno kontroly (7 dní), aby pokračujúce sedenie po pauze
      # počítalo ďalej, nie znova od 1 (pri 3 dňoch to dávalo falošné „vynechala“ — overovateľ B17, P1; kontrola aj tak
      # sčíta úseky, keby počítadlo predsa zmizlo); súbor PS nad ~6 MB sa oreže na posledných 30 000 riadkov pod zámkom
      find "$d" -maxdepth 1 -type f -mmin +11520 -delete 2>/dev/null
      if [ -z "${NG_BEH_STOPY:-}" ] && [ "$kam" != "${HOME:-/tmp}/.aigrip-ng/hook-stopy.jsonl" ] && command -v flock >/dev/null 2>&1; then
        x=$(stat -c %s -- "$kam" 2>/dev/null || echo 0)
        if [ "$x" -gt 6000000 ] 2>/dev/null; then
          ( flock -n 9 || exit 0
            tail -n 30000 "$kam" > "$kam.tmp.$$" && mv "$kam.tmp.$$" "$kam"
            rm -f "$kam.tmp.$$" ) 9>>"$kam.zamok" 2>/dev/null
        fi
      fi
    fi
  fi
  return 0
}

# Audity len na dopĺňanie (posúdenie časti 3, C2): stráž zápisníka audity výslovne vynechávala; 20. 9. sa z auditu
# tichých potvrdení stratili 3 riadky s verdiktom, 30. 9. partner sám vymazal 5 riadkov zo záznamu kotvy a kód tomu
# nebránil. Rozhoduje ng-audit-straz.py (vedľa tejto knižnice, inak spoločné miesto stroja). Python sa spustí, keď vstup
# menuje audit, a pri každom rm/unlink/shred/truncate/rsync/mv, cp -r, find -delete a git clean/stash — vstup háčika nesie
# aj cwd, takže podmienka „v priečinku projektu“ platí takmer vždy (14 dní PS aj kolegov: 2,8 % príkazov, +~23 ms);
# od 3. 10. (overovateľ B17, N5) aj pri mazaní či presune priečinka v kóde (rmtree, rmSync, remove_tree, FileUtils, rename…),
# tar --remove-files, zip -m a gzip/pigz/zstd/lz4/compress -r; ostatné príkazy nestoja nič. Chyba alebo chýbajúci rozhodovač = pustiť (ako stráž zápisníka); zákaz vypíše rozhodnutie
# a háčik skončí.
_NG_AUD_MENA='audit-[A-Za-z0-9_.-]*\.(jsonl|md|json)|kotva-audit\.jsonl|-verdikty\.jsonl|zapisnik-guard\.log|hook-stopy\.jsonl|beh-strazcov\.jsonl'
_NG_AUD_BASH='"tool_name"[[:space:]]*:[[:space:]]*"Bash"'
_NG_AUD_MAZ='(^|[^A-Za-z0-9_.-])(rm|unlink|shred|truncate|rsync|mv)[[:space:]]'
_NG_AUD_KDE='\.aigrip|aig-ng|projects|[~]|HOME'
_NG_AUD_FIND='(^|[^A-Za-z0-9_.-])find[[:space:]]'
_NG_AUD_FDEL='-delete|-exec[[:space:]]+(rm|shred|truncate)'
_NG_AUD_GIT='(^|[^A-Za-z0-9_.-])git[[:space:]].*(clean|stash)'
_NG_AUD_CPR='(^|[^A-Za-z0-9_.-])cp[[:space:]]([^;&|]*[[:space:]])?(-[A-Za-z]*[rRa][A-Za-z]*|--recursive|--archive)([[:space:]]|$)'
_NG_AUD_KOD='rmtree|remove_tree|rm_rf|rmSync|rmdirSync|FileUtils|shutil\.move|os\.(rename|replace|removedirs)|(^|[^A-Za-z0-9_])rename(Sync)?[[:space:]]*[(\"'"'"']|--remove-files'
_NG_AUD_BAL='(^|[^A-Za-z0-9_.-])(gzip|pigz|zstd|lz4|compress)[[:space:]]([^;&|]*[[:space:]])?(-[A-Za-z]*r[A-Za-z]*|--recursive)([[:space:]]|$)|(^|[^A-Za-z0-9_.-])zip[[:space:]]([^;&|]*[[:space:]])?(-[A-Za-z]*m[A-Za-z]*|--move)([[:space:]]|$)'

ng_audit_straz() {
  local p=0 py="" out vst=${IN:-}
  [[ $vst =~ $_NG_AUD_MENA ]] && p=1
  if [ "$p" = 0 ] && [[ $vst =~ $_NG_AUD_BASH ]]; then
    [[ $vst =~ $_NG_AUD_MAZ ]] && [[ $vst =~ $_NG_AUD_KDE ]] && p=1
    [[ $vst =~ $_NG_AUD_FIND ]] && [[ $vst =~ $_NG_AUD_FDEL ]] && p=1
    [[ $vst =~ $_NG_AUD_GIT ]] && p=1
    [[ $vst =~ $_NG_AUD_CPR ]] && p=1      # cp -r zálohy cez priečinok s auditom (meno auditu v príkaze nie je)
    [[ $vst =~ $_NG_AUD_KOD ]] && p=1      # zmazanie či presun priečinka v kóde, tar --remove-files (N5)
    [[ $vst =~ $_NG_AUD_BAL ]] && p=1      # gzip -r priečinka, zip -m (N5)
  fi
  [ "$p" = 1 ] || return 0
  for py in "${BASH_SOURCE[0]%/*}/ng-audit-straz.py" /opt/aigrip-ng/scripts/ng-audit-straz.py; do [ -f "$py" ] && break; done
  [ -f "$py" ] || return 0
  out=$(printf '%s' "$vst" | timeout 5 python3 -S "$py" 2>/dev/null) || return 0
  [ -n "$out" ] || return 0
  printf '%s\n' "$out"
  exit 0
}
