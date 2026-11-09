#!/usr/bin/env bash
# Spolocna stopa pre NG hooky (kus AV, 20. 9. 2026).
#
# Prazdny hook som uz chytil. Ale hook, ktory sa nespusti alebo spadne, je stale
# tichy -- jeho navratovy kod nikto nepozera. Kazdy NG hook preto pri behu
# pripise riadok; z toho sa da povedat, ci naozaj bezal.
#
# Pouzitie na zaciatku hooku:  . ~/projects/aigrip/.aigrip/scripts/ng-hook-stopa.sh <nazov>
ng_hook_stopa() {
  local nazov="${1:-?}" proj f
  proj=$(basename "${CLAUDE_PROJECT_DIR:-$PWD}")
  if [ -n "${NG_HOOK_STOPY:-}" ]; then
    f="$NG_HOOK_STOPY"   # skúška (pieskovisko): nikdy nie skutočná stopa PS ani kolegu
  else
    f="/home/pstrocka/projects/aigrip/.aigrip/ng/hook-stopy.jsonl"
    # z izby kolegu súbor PS nie je zapisovateľný (do 22. 9. bola stopa kolegov slepá) → vlastný domov; správca ju zbiera
    if ! [ -w "$f" ]; then mkdir -p "$HOME/.aigrip-ng" 2>/dev/null; f="$HOME/.aigrip-ng/hook-stopy.jsonl"; fi
  fi
  # B2 (2. 10.): verzia stroja v klone, kde háčik beží (prvý riadok .aigrip/ng/verzia.txt), a značka zastavenej
  # synchronizácie — z kópie stôp tak stroj vidí, akú verziu kolega naozaj má, bez čítania jeho priečinka (K-11
  # „nasadené = bežiace“, ng-rozvoz-kolegovia.py). Len vstavané príkazy shellu (háčik pri odoslaní správy má na všetko 2 s).
  # Prázdna verzia = v klone verzia.txt nie je; „?“ = je, ale nedá sa prečítať (nie bežný súbor, bez práv, prázdny alebo
  # neplatný prvý riadok); stopa bez poľa „verzia“ je zo skriptu pred 2. 10. Číta sa len bežný súbor a najviac 64 znakov —
  # rúra (FIFO) by háčik zastavila do jeho časového limitu.
  local pd="${CLAUDE_PROJECT_DIR:-$PWD}" ver="" blok="" vf
  vf="$pd/.aigrip/ng/verzia.txt"
  if [ -f "$vf" ] && [ -r "$vf" ]; then
    { IFS= read -r -n 64 ver < "$vf"; } 2>/dev/null || true   # aj pod set -e (súbor bez konca riadku = 1)
    ver=${ver//[^0-9A-Za-z._-]/}; ver=${ver:0:24}; ver=${ver:-?}
  elif [ -e "$vf" ] || [ -L "$vf" ]; then
    ver="?"
  fi
  if [ -f "$pd/.aigrip/.git-sync-blocked" ]; then
    { IFS= read -r -n 120 blok < "$pd/.aigrip/.git-sync-blocked"; } 2>/dev/null || true
    blok=${blok//[^0-9A-Za-z:._ -]/}; blok=${blok:0:60}; blok=${blok:-?}
  fi
  # 20. 10. 2026: `date -Is` pozná len GNU date. Na Macu PS zlyhá a stopa sa zapíše s PRÁZDNYM časom — 1 542 takých
  # riadkov tam ležalo od začiatku a nedalo sa z nich nič zistiť (kontrola háčikov číta okno podľa času).
  local ts; ts=$(date -Is 2>/dev/null) || ts=''
  [ -n "$ts" ] || ts=$(date +%Y-%m-%dT%H:%M:%S%z 2>/dev/null)
  printf '{"ts":"%s","projekt":"%s","hook":"%s","verzia":"%s"%s}\n' \
    "$ts" "$proj" "$nazov" "$ver" "${blok:+,\"blok\":\"$blok\"}" >> "$f" 2>/dev/null || true
  # 30. 9.: strážca otázok pridáva riadok pri každom konci ťahu — hranica 4000/3000, aby kontrola háčikov (okno 2 dni)
  # nevidela orezané štarty sedení ako chýbajúcu stopu (u PS až ~960 riadkov za deň)
  local n; n=$(wc -l < "$f" 2>/dev/null || echo 0)
  # orez len pod zámkom a cez vlastný dočasný súbor: dva háčiky tej istej udalosti (SessionStart; od strážcu otázok aj Stop)
  # prekročia hranicu naraz a spoločné "$f.tmp" súbor stopy vyprázdnilo (skúška 30. 9.: 5 z 200 súbežných orezov = 0 riadkov)
  if [ "$n" -gt 4000 ] && command -v flock >/dev/null 2>&1; then
    ( flock -n 9 || exit 0
      n=$(wc -l < "$f" 2>/dev/null || echo 0)
      [ "$n" -gt 4000 ] && tail -n 3000 "$f" > "$f.tmp.$$" && mv "$f.tmp.$$" "$f"
      rm -f "$f.tmp.$$" ) 9>>"$f.zamok" 2>/dev/null || true
  fi
}
ng_hook_stopa "${1:-?}"
