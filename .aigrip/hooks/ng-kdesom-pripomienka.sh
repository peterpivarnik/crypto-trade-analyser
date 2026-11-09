#!/usr/bin/env bash
# Pripomienka partnerovi pri štarte sedenia (nie správa pre človeka, R104):
#  1) veta „kde som" (4.9) — keď chýba alebo zostarla, partner ju má prepisovať počas práce (vždy);
#  2) pri NÁVRATE (človek v tomto projekte nepísal ≥ 2 h — FD-9b K-10 v2, nález #18: nie pri každom štarte, R42)
#     otázky na človeka (záznamy v stave návrh s adresátom) a návrhy zo sedení, o ktorých nerozhodol — partner ich PONÚKNE.
#  3) pri NÁVRATE aj začiatok pohľadu „kde stojíme" (K3, 25. 9.): do vtedy ho pilotný text ani háčik partnerovi nespomenuli
#     a pri návrate k práci ho otvoril 1 z 15 sedení kolegov (rozbor stôp 18.–24. 9.). Krátko a ohraničene (≤ 8 riadkov,
#     ≤ 1 800 znakov): veta „kde som" z kde-som.json (čerstvá, u kolegu sa pohľad v jeho klone neprekresľuje), potom
#     najnovšie riadky sekcií pohľadu; pohľad, ktorý nevidí najnovší záznam zápisníka, povie svoj dátum a ten záznam
#     (dátum v pohľade je čas poslednej zmeny obsahu, nie vykreslenia — tichý projekt tak nie je „starý"). Nie v aigrip
#     (tam ukazuje stav STAV.md a STAVBA.md). Nič nezapisuje; pri chybe mlčí.
#  4) pri NÁVRATE aj „odišlo?“ (kus OVN, 4. 10.): koncepty mailov z pohľadu („Toto na odoslanie ešte čaká“), o ktorých
#     zápisník nevie, či odišli — partner sa človeka jednou vetou spýta a odpoveď zapíše. Čo a kedy sa pýtať, rozhoduje
#     ng-odislo-otazka.py na spoločnom mieste stroja (najviac 3, tú istú vec raz za 3 dni; pamäť v $HOME/.aigrip-ng).
#     Skúška návratu 4. 10.: v 8 zo 17 projektov sa nevedelo, či pripravené odišlo — strážca na konci ťahu sa pýta len
#     na prípravu v bežiacom sedení, staršia ostala bez otázky navždy. Od OVS (4. 10.) aj vety zápisníka, ktoré sľubujú
#     odovzdanie bez zápisu o ňom („Peter dá Marekovi zálohu“, „Jozef rozdistribuuje interný mail“) — tie súbor nemajú.
#  Návratová časť (2, 3, 4) mlčí v behu bez človeka (claude -p): nikto sa nevracia a automat by dostal pokyn ponúkať dokončenie.
#  Mlčí aj pri zhustení kontextu (SessionStart source=compact, 29. 9.) — to nie je návrat a nesmie ho ani zapísať ako vložený.
#  UserPromptSubmit (29. 9.): tá istá návratová časť aj pri PRVEJ správe po ≥ 2 h v okne, ktoré ostalo otvorené. Za týždeň
#     22.–29. 9. bolo z 37 návratov kolegov 12 v otvorenom okne — štart sedenia vtedy nebeží a výťah nedostali nikdy.
#     Rýchlo (každá správa): čas poslednej vety človeka v projekte drží $HOME/.aigrip-ng/navrat.json, stopy sa prečítajú
#     len raz, keď záznam chýba. Ten istý návrat sa nevloží dvakrát: keď ho už vložil štart sedenia (alebo skoršia správa),
#     sedenie je v „vlozene“ s časom po poslednej vete človeka. V aigrip mlčí (návrat tam hlási ng-stavba-guard.sh).
#  Platí pre sedenia PS (stopy iných ľudí stroj nevidí — K-10 pre iných adresátov je nevymáhané, FD-9b v2).
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
. "$STROJ/scripts/ng-hook-stopa.sh" ng-kdesom-pripomienka 2>/dev/null || . "${0%/*}/ng-hook-stopa.sh" ng-kdesom-pripomienka 2>/dev/null || true
P=$(basename "${CLAUDE_PROJECT_DIR:-$PWD}")
PD="${CLAUDE_PROJECT_DIR:-$PWD}"
T0=$(date +%s%N)   # UserPromptSubmit má na celý háčik 2 s — čerstvý pohľad pri návrate sa vykreslí len v zvyšku času
INPUT=""; [ -t 0 ] || INPUT=$(timeout 1 cat 2>/dev/null || true)
UDALOST=$(printf '%s' "$INPUT" | jq -r '.hook_event_name // empty' 2>/dev/null)
SID=$(printf '%s' "$INPUT" | jq -r '.session_id // empty' 2>/dev/null)
ZDROJ=$(printf '%s' "$INPUT" | jq -r '.source // empty' 2>/dev/null)   # SessionStart: startup · resume · clear · compact
NS="$HOME/.aigrip-ng/navrat.json"
# zapíše, že tomuto sedeniu bol návrat vložený (aby ho pri odoslaní správy nevložil druhýkrát); chyba = nič
_vlozene() {
  [ -n "$SID" ] || return 0
  timeout 1 python3 - "$NS" "$SID" <<'PY' 2>/dev/null || true
import sys,os,json,time
ns,sid=sys.argv[1],sys.argv[2]
try: st=json.load(open(ns,encoding='utf-8'))
except Exception: st={}
if not isinstance(st,dict): st={}
vl=st.get('vlozene') if isinstance(st.get('vlozene'),dict) else {}
now=time.time(); vl={k:v for k,v in vl.items() if isinstance(v,(int,float)) and now-v<3*86400}; vl[sid]=now; st['vlozene']=vl
os.makedirs(os.path.dirname(ns),exist_ok=True); tmp=f'{ns}.tmp{os.getpid()}'
with open(tmp,'w',encoding='utf-8') as f: json.dump(st,f)
os.replace(tmp,ns)
PY
}
F="$PD/.aigrip/ng/kde-som.json"
S="$STROJ/scripts/ng-kdesom.py"
if [ "$UDALOST" = "UserPromptSubmit" ]; then
  :   # veta „kde som“ sa pripomína pri štarte a na konci ťahu (ng-kdesom-koniec), nie pri každej správe
elif [ ! -f "$F" ]; then
  echo "NG: v tomto projekte nie je veta „kde som\" — pri práci ju prepisuj:"
  echo "    python3 $S $P \"<čo práve robím>\" \"<čo je ďalšie>\""
else
  VEK=$(( ( $(date +%s) - $(stat -c %Y "$F" 2>/dev/null || stat -f %m "$F" 2>/dev/null || echo 0) ) / 3600 ))
  if [ "$VEK" -gt 24 ]; then
    echo "NG: veta „kde som\" je stará $VEK h — pri práci ju prepíš:"
    echo "    python3 $S $P \"<čo práve robím>\" \"<čo je ďalšie>\""
  fi
fi
# beh bez človeka (claude -p): otázky ani pohľad mu nepodsúvať (ako ng-bez-odpovede.py a ng-kontext-straz.sh)
case "${CLAUDE_CODE_ENTRYPOINT:-}" in sdk*) exit 0 ;; esac
# kto tu sedí (29. 9., nález Mariána Lacenu v aspecta-finops): partner bral meno človeka z e-mailu v kontexte prostredia —
# to je účet Claude, na ktorom okno beží (spoločné účty sa striedajú), nie človek; 13 rozhodnutí tak dostalo cudzie meno.
# Pri štarte okna jedna veta: človek = vlastník domova (unixový účet → ng/ludia.tsv, inak git user.name). Chyba = ticho.
if [ "$UDALOST" != "UserPromptSubmit" ]; then
  timeout 1 python3 - "$STROJ" <<'PY' 2>/dev/null || true
import sys,os,io,json,pwd,subprocess,unicodedata
STROJ=sys.argv[1]
def n(x): return ''.join(c for c in unicodedata.normalize('NFD',(x or '').lower()) if unicodedata.category(c)!='Mn')
ja=pwd.getpwuid(os.getuid()).pw_name; meno=''
try:
    for l in io.open(os.path.join(STROJ,'ng','ludia.tsv'),encoding='utf-8'):
        u,_,m=l.strip().partition('|')
        if u==ja and m.strip(): meno=m.strip()
except Exception: pass
if not meno:
    try: meno=subprocess.run(['git','config','--global','user.name'],capture_output=True,text=True,timeout=0.5).stdout.strip()
    except Exception: pass
if not meno: sys.exit(0)
try: mail=(json.load(open(os.path.expanduser('~/.claude.json'),encoding='utf-8')).get('oauthAccount') or {}).get('emailAddress') or ''
except Exception: mail=''
veta=f'NG: s tebou v tomto okne pracuje {meno} (účet {ja}) — toto meno uvádzaj v zápisníku aj v hlásení o novom spôsobe práce.'
if mail and n(meno.split()[-1]) not in n(mail.split('@')[0]):
    veta+=f' E-mail {mail} v kontexte prostredia patrí účtu Claude, na ktorom okno práve beží (spoločné účty sa striedajú), nie človeku — meno podľa neho neurčuj.'
print(veta)
PY
fi
# hlásenie o novom spôsobe (29. 9.): pravidlo stálo len na texte v CLAUDE.md; za 7 dní ho v projekte, kde pracovali,
# nedostali Ján Malý (mpai, cp-ai-digi) a PS (am, marketing). Pri štarte okna: ak pre sediaceho človeka (unixový účet →
# meno z ng/ludia.tsv) v zápisníku nie je platná značka „hlásenie odovzdané“, partner dostane jednu vetu. Chyba = ticho.
# Pravidlo je JEDEN blok (medzi značkami nižšie): meranie P-hlásenie (ng-klauzuly.py ma_hlasenie) si ho načíta odtiaľto, aby
# pripomienka a meranie rátali to isté — do 29. 9. mali každé vlastné a pre Dominiku v aspecta-finops sa rozchádzali.
IFS= read -r -d '' HLASENIE <<'PY' || true
# --- ng-hlasenie:zaciatok ---
import os, io, re, unicodedata, datetime, hashlib
# Jediný určovateľ „má človek v zápisníku platnú značku ‚NG: hlásenie odovzdané‘“ (29. 9.) — pripomienka pri štarte okna
# (ng-kdesom-pripomienka.sh) aj meranie P-hlásenie (ng-klauzuly.py ma_hlasenie). Nič nezapisuje.
def _hl_n(x): return ''.join(c for c in unicodedata.normalize('NFD', (x or '').lower()) if unicodedata.category(c) != 'Mn').strip()
def _hl_meno(x): return ' '.join(_hl_n(x).replace('(', ' ').replace(')', ' ').split())
HL_ZNACKA = 'ng: hlasenie odovzdane'
# výslovná skratka: „PS“ je v celom rámci Peter Strocka (iniciály PS majú v tíme šiesti — samé by neplatili nikomu)
HL_ALIASY = {'peter strocka': {'ps'}}
HL_ZAPOR = re.compile(r'\bomyl|\bnie je\b|\bneodovzdan|\bnedostal|\bzrusen|\bneplat')
HL_KLUC = re.compile(r'\b[a-z]{2,6}-[a-z]\d{6}-[0-9a-f]{4,16}\b')   # trvalý kľúč záznamu (AIG-X260919-a276; od B6 6+ znakov) po _hl_n

def _hl_kluc_uzkeho(zapisnik, b):
    """Kľúč úzkeho riadku bez kľúča (dátum | kto | veta | kde) — ten istý, aký mu dáva čítanie zápisníka (ng-zapisnik.py
    kluc_odvodeny, B6): zrušenie značky (ng-zapis.py --zrus) ho nesie, hoci v súbore nestojí (bpw: HEAD a Mac majú značky
    v úzkych riadkoch). None = riadok nemá dátum, z ktorého by sa kľúč dal odvodiť."""
    m = re.match(r'^\s*(\d{4})-(\d{2})-(\d{2})\s*$', b[0]) or re.match(r'^\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})\s*$', b[0])
    if not m or len(b) < 3: return None
    y, mo, d = (m[1], m[2], m[3]) if len(m[1]) == 4 else (m[3], m[2], m[1])
    try: dd = f'{datetime.date(int(y), int(mo), int(d)):%y%m%d}'
    except ValueError: return None
    p = os.path.basename(os.path.dirname(os.path.abspath(zapisnik)))
    prj = ''.join(c for c in unicodedata.normalize('NFD', p) if unicodedata.category(c) != 'Mn').upper().replace('-', '')[:3]
    return _hl_n(f'{prj}-X{dd}-' + hashlib.sha256(f'{p}|{dd}|{b[2]}'.encode('utf-8')).hexdigest()[:6])

def hl_ludia(cesta):
    """[(účet, meno)] z ng/ludia.tsv; chýbajúci súbor = prázdny tím."""
    try: return [(u.strip(), m.strip()) for u, _, m in (l.rstrip('\n').partition('|') for l in io.open(cesta, encoding='utf-8')) if u.strip()]
    except FileNotFoundError: return []

def hl_znaky(ucet, meno, ludia):
    """Slová, ktorými značka môže menovať tohto človeka: jeho účty (všetky účty s tým istým menom), výslovná skratka, krstné
    meno, priezvisko a iniciály — každé z posledných troch len vtedy, keď ho v tíme (ng/ludia.tsv) nemá nikto iný. 29. 9.:
    iniciály „PS“ majú Strocka, Sedlák, Senko, Štefka, Sído a Šuba — značka „hlásenie odovzdané PS“ (AEM, AIB, bpw) umlčala
    piatich, ktorí hlásenie nedostali; priezvisko „Senko“ majú Peter aj Tibor — vtedy platí len celé meno. Meranie pozná
    človeka po mene (git autor, stĺpec kto), pripomienka po účte — obe cesty dajú tie isté slová."""
    if not ucet and meno and ' ' not in meno.strip():
        ucet = next((u for u, _ in ludia if u == meno.strip()), '')           # „meno“ je v skutočnosti účet (git autor pstr13)
        if ucet: meno = ''
    if not meno: meno = next((m for u, m in ludia if u == ucet), '')
    nm = _hl_meno(meno); c = nm.split()
    krs, pri, ini = {}, {}, {}                     # RÔZNI ľudia (dva účty Pokoraczkiho sú jeden človek; AI účty nie sú ľudia)
    for _, m in ludia:
        x = _hl_meno(m)
        if not x or x.startswith('ai account'): continue
        w = x.split(); krs.setdefault(w[0], set()).add(x)
        if len(w) >= 2: pri.setdefault(w[-1], set()).add(x); ini.setdefault(w[0][0] + w[-1][0], set()).add(x)
    def sam(d, k): return not (d.get(k, set()) - {nm})
    z = {_hl_n(u) for u in {ucet} | {u for u, m in ludia if nm and _hl_meno(m) == nm} if u} | HL_ALIASY.get(nm, set())
    if nm.startswith('ai account'): c = []         # AI účet nie je človek: len účet (nie „ai“ ani iniciály „aj“ zo slova „aj“)
    if c and sam(krs, c[0]): z.add(c[0])
    if len(c) >= 2:
        z.add(c[-1] if sam(pri, c[-1]) else c[0] + ' ' + c[-1])
        if sam(ini, c[0][0] + c[-1][0]): z.add(c[0][0] + c[-1][0])
        z.add(' '.join(c))             # celé meno: v opravnej vete aj v inom páde („pre Jána Malého“ — samé „mal…“ by bolo „malý“)
    z.discard('')
    return z

def _hl_vzor(z, pady):
    # krátke slovo („ps“, iniciály, účet „rh“) len samostatne: 29. 9. v aigrip „ps“ z názvu projektu „am-ps“ urobil z vety
    # „v am-ps … nie je záznam o hlásení“ opravu značky PS a pripomienka chodila pri každom štarte okna
    if len(z) <= 3: return r'(?<![\w/-])' + re.escape(z) + r'(?![\w/-])'
    # v opravnej vete aj iný pád (Dominika → Dominiku, Marián → Mariánovi); krátke slovo („ján“, „malý“) len v celom mene
    w = z.split()
    if pady and (len(w) > 1 or len(z) > 4):
        return r'\b' + r'\s+'.join(re.escape(x[:-2] if len(x) > 4 else x[:-1] if len(x) == 4 else x) + r'\w{0,5}' for x in w) + r'\b'
    return r'\b' + r'\s+'.join(re.escape(x) for x in w) + r'\b'

def _hl_den(x):
    m = re.match(r'\s*(\d{4})-(\d{1,2})-(\d{1,2})', x) or re.match(r'\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})', x)
    if not m: return datetime.date.min
    y, mo, d = (m[1], m[2], m[3]) if len(m[1]) == 4 else (m[3], m[2], m[1])
    try: return datetime.date(int(y), int(mo), int(d))
    except ValueError: return datetime.date.min

def hl_ma_znacku(zapisnik, znaky):
    """True = v zápisníku je značka „NG: hlásenie odovzdané“ pre človeka so slovami `znaky`, ktorú neskorší riadok neopravil.
    Značka = bunka, ktorá ZAČÍNA „NG: hlásenie odovzdané“, a jedno zo slov je v nej celé (nie záporná veta „pre Dominiku …
    hlásenie odovzdané nie je“, nie stĺpec mena „X: hlásenie odovzdané …“, nie stĺpec dopadu „malý“ za Jána Malého).
    Zápisník je append-only, omyl sa opravuje neskorším riadkom, ktorý značku VÝSLOVNE ruší: (a) ten istý kľúč so stavom
    zrušené/nahradené (posledný riadok kľúča platí), (b) veta s kľúčom značky a záporom (omyl, neplatí, zrušené, nie je),
    (c) časť vety (medzi — alebo ;) so slovami „hlásenie odovzdané“, záporom a menom tohto človeka (aspecta-finops
    ASP-P260929-cb77: „pre Dominiku Kulošťákovú hlásenie odovzdané nie je“). Zmienka o hlásení bez slov značky oprava nie je:
    zápisník aigrip je denník o hláseniach vo všetkých projektoch („Peter Káčerík v AIB nedostal vetu … v am-ps … nie je
    záznam o hlásení“) — 29. 9. taká veta rušila platnú značku PS AIG-X260919-a276."""
    vz = [re.compile(_hl_vzor(z, False)) for z in znaky]; vp = [re.compile(_hl_vzor(z, True)) for z in znaky]
    znacky, opravy, zrus_kluc, stav = [], [], set(), {}
    for i, l in enumerate(io.open(zapisnik, encoding='utf-8', errors='replace')):
        if not l.startswith('|'): continue
        b = [x.strip() for x in l.strip().strip('|').split('|')]
        k = _hl_n(b[0]).strip('`') if b[0].startswith('`') else None
        if k and len(b) >= 8: stav[k] = _hl_n(b[7])
        t = _hl_n(l)
        if 'hlasen' not in t and not (HL_ZAPOR.search(t) and len(HL_KLUC.findall(t)) > (1 if k else 0)): continue
        d = _hl_den(b[2] if k and len(b) > 2 else b[0])
        for x in map(_hl_n, b[1:] if k else b):
            if x.startswith(HL_ZNACKA):
                if any(r.search(x[len(HL_ZNACKA):]) for r in vz): znacky.append((d, i, k or _hl_kluc_uzkeho(zapisnik, b)))
                continue
            for p in re.split(r'\s[—–]\s|;', x):
                if not HL_ZAPOR.search(p): continue
                zrus_kluc.update(HL_KLUC.findall(p))
                if 'hlasenie odovzdan' in p and any(r.search(p) for r in vp): opravy.append((d, i))
    return any(stav.get(k) not in ('zrusene', 'nahradene') and k not in zrus_kluc and not any(o > (d, i) for o in opravy)
               for d, i, k in znacky)
# --- ng-hlasenie:koniec ---
import sys
STROJ, PD, P = sys.argv[1:4]
ja = os.environ.get('USER') or os.environ.get('LOGNAME') or ''
if not ja: sys.exit(0)
ludia = hl_ludia(os.path.join(STROJ, 'ng', 'ludia.tsv'))
if hl_ma_znacku(os.path.join(PD, 'NG-zaznamy.md'), hl_znaky(ja, '', ludia)): sys.exit(0)
kto = next((m for u, m in ludia if u == ja), '') or ja
print(f'NG: pre človeka, ktorý tu sedí ({kto}), v zápisníku tohto projektu ešte nie je značka „hlásenie odovzdané“ — '
      f'na začiatku mu samostatnou správou povedz, ako sa teraz pracuje (pilotný text v CLAUDE.md, odsek „Keď s niekým '
      f'v projekte pracuje prvý raz“), a potom zapíš: python3 {STROJ}/scripts/ng-zapis.py {P} priebeh partner partner '
      f'"NG: hlásenie odovzdané {kto} {datetime.date.today():%Y-%m-%d}" "projekt"')
PY
if [ "$UDALOST" != "UserPromptSubmit" ] && [ -f "$PD/NG-zaznamy.md" ]; then
  timeout 1 python3 -c "$HLASENIE" "$STROJ" "$PD" "$P" 2>/dev/null || true
fi
# oznámenie autorovi (PS 29. 9., 4.39 b): keď človek otvorí projekt, v ktorom za posledných 30 dní sám pracoval, partner
# mu jednou správou povie, čo iní od jeho poslednej práce tu zmenili v jeho súboroch (vytvoril ich alebo ich posledný menil
# pred zmenou) — len v tomto projekte, nie kdekoľvek (PS: „aby ho to nevyrušilo od jeho inej práce“), najviac raz denne.
# Kolegom až po tom, čo PS uvidí prvý skutočný text (OZNAMENIA_KOLEGOM=1 v ďalšej verzii); PS hneď.
OZNAMENIA_KOLEGOM=1   # PS 29. 9. 17:27, 4.49 (a)
if [ "$UDALOST" != "UserPromptSubmit" ] && { [ "${USER:-}" = "pstrocka" ] || [ "$OZNAMENIA_KOLEGOM" = 1 ]; }; then
  timeout 2 python3 - "$STROJ" "$PD" <<'PY' 2>/dev/null || true
import sys,os,re,json,time,subprocess,unicodedata,datetime
STROJ,PD=sys.argv[1:3]
T0=time.time()
def n(x): return ''.join(c for c in unicodedata.normalize('NFD',(x or '').lower()) if unicodedata.category(c)!='Mn').strip()
# core.quotePath=false (29. 9.): bez neho git vypíše cestu s diakritikou v úvodzovkách ("work/…/Dot\303\241cie.docx") — taká cesta
# ako pathspec nič nenájde, súbor vypadol bez slova a zabral miesto v strope (PM-PT 73 takých súborov, mpai 64, aspecta-finops 49)
def git(*a):
    try: return subprocess.run(['git','-C',PD,'-c','core.quotePath=false',*a],capture_output=True,text=True,timeout=0.8).stdout
    except Exception: return ''
def prud(a,do):
    """riadky výstupu gitu (od najnovšieho), kým neuplynie čas `do` — čiastočný výsledok (najnovšie zmeny) je lepší než žiadny"""
    try: pp=subprocess.Popen(['git','-C',PD,'-c','core.quotePath=false',*a],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,errors='replace')
    except Exception: return
    try:
        for l in pp.stdout:
            yield l.rstrip('\n')
            if time.time()>do: break
    finally:
        pp.kill(); pp.wait()
# správcovské a strojové commity nie sú práca človeka. 29. 9. doplnené: „merge: zlúčenie origin/main pred prechodom na NG“ (ADM),
# „PS: necommitnuta praca z klonu … pred prechodom na NG“ (cp-mis), uloženia pred zlúčením (bpw), zakladanie projektov
# a onboarding, hromadné rozvozy pomôcok (framework: … v 47 projektoch). Záchrana synchronizácie (sync-zachrana) sem NEpatrí:
# ukladá neodoslanú prácu človeka pod jeho menom (account-jlr 29. 9.: objednávky Šimona Hivka) — jej strojové súbory
# odfiltruje NIE_PRACA nižšie a zmenu cudzieho súboru v nej treba autorovi oznámiť.
STROJ_SPRAVA=re.compile(r'^(NG[: ]|prevádzka:|users\.tsv:|sync:|chore\(aigrip\)|Merge |merge[ :(]|data:|setup: AIGrip onboarding|framework: (create|propagate) |PS: necommit)'
                        r'|pred prechodom na (nový spôsob|NG)|pred zlúčením|pred zlucenim|založenie projektu')
# súbory, ktorých zmena nie je práca človeka: stroj (.aigrip — aj pohľady KDE-STOJIME.md a MAPA.md, ktoré prekresľuje domov PS
# v každom klone), .claude, .gitignore/.gitattributes, zápisník, CLAUDE.md, návrhy zo sedení, automatické úvodné stránky sedení
# a denníky, ktoré záchrana obnovila z gitu
NIE_PRACA=re.compile(r'(^|/)(\.aigrip|\.claude)(/|$)|(^|/)\.git(ignore|attributes)?(/|$)|^NG-zaznamy\.md$|^CLAUDE\.md$|(^|/)NAVRHY[^/]*\.md$'
                     r'|(^|/)map/journal/[^/]*(session-stub|recovered-from-git)')
# strojové výstupy, ktoré projekt sám označil v .gitattributes (29. 9.): merge=union (denníky a telemetria, ktoré sa len
# dopĺňajú) alebo linguist-generated (vygenerované). Commit ich nerozozná: výsledky nočných behov bpw
# (work/dokumentacia/E2E-HLASENIA.jsonl, NOCNE-BEHY.jsonl, E2E-HLASENIE-*.md …) odchádzajú v „auto: dirty-guard save before
# sync“ pod menom PS ako každá jeho práca a názov súboru nič všeobecné nenesie — kým ich bpw v .gitattributes neoznačí,
# rátajú sa ako jeho posledná práca (dnes bez dopadu: v bpw za 30 dní iný človek necommitol).
def _vzor_attr(p):
    kotva = '/' in p.rstrip('/'); p = p.lstrip('/'); r = ''; i = 0
    while i < len(p):
        if p.startswith('**/', i): r += '(?:.*/)?'; i += 3
        elif p.startswith('**', i): r += '.*'; i += 2
        elif p[i] == '*': r += '[^/]*'; i += 1
        elif p[i] == '?': r += '[^/]'; i += 1
        else: r += re.escape(p[i]); i += 1
    return ('^' if kotva else '(^|/)') + r + '$'
try:
    _av = [_vzor_attr(w[0]) for w in (l.split() for l in open(os.path.join(PD, '.gitattributes'), encoding='utf-8', errors='replace'))
           if len(w) > 1 and not w[0].startswith('#') and not w[0].endswith('/')
           and any(a in ('merge=union', 'linguist-generated', 'linguist-generated=true') for a in w[1:])]
    if _av: NIE_PRACA = re.compile('|'.join([NIE_PRACA.pattern] + _av))
except Exception: pass
ja={n(git('config','user.name').strip()), n(git('config','user.email').strip().split('@')[0]), n(os.environ.get('USER',''))}
try:
    for l in open(os.path.join(STROJ,'ng','ludia.tsv'),encoding='utf-8'):
        u,_,m=l.strip().partition('|')
        if u==os.environ.get('USER'): ja.add(n(m))
except Exception: pass
ja.discard('')
if os.environ.get('NG_OZNAMENIE_SKUSKA_JA'): ja={n(x) for x in os.environ['NG_OZNAMENIE_SKUSKA_JA'].split('|') if x}   # len skúška/ukážka pre PS
def je_ja(meno,mail): return n(meno) in ja or n(mail.split('@')[0]) in ja
ns=os.path.expanduser('~/.aigrip-ng/oznamenia.json')
try: st=json.load(open(ns,encoding='utf-8'))
except Exception: st={}
dnes=f'{datetime.date.today():%Y-%m-%d}'
if st.get(PD)==dnes: sys.exit(0)
# moja posledná práca v projekte za 30 dní — aj automatické uloženie (je to práca človeka), ale len commit, ktorý zmenil aspoň
# jeden súbor práce (29. 9.): dirty-guard ukladá pod menom PS aj pohľady, ktoré jeho domov prekreslil. V PM-PT bol jedinou
# „prácou“ PS za 30 dní prekreslený KDE-STOJIME.md; v marketingu sa jeho posledná práca posunula z 28. 9. 14:33 na 29. 9. 8:22
# a čo iní zmenili medzi tým, by sa nikdy neoznámilo. Súbory len pre moje kandidátske commity, od najnovšieho.
moje=[c for c in (l.split('\t',5) for l in git('log','--since=30.days','--format=%H\t%an\t%ae\t%at\t%ct\t%s','-n','3000').split('\n'))
      if len(c)==6 and je_ja(c[1],c[2]) and not STROJ_SPRAVA.search(c[5])][:400]
cas={c[0]:(int(c[3]),int(c[4])) for c in moje}
t_ja=0; h=None
for l in (prud(['log','--no-walk=unsorted','--format=@@%H','--name-only','--no-renames',*cas],T0+0.4) if cas else ()):
    if l.startswith('@@'):
        h=l[2:]
        if t_ja and cas.get(h,(0,0))[1]<t_ja: break      # ďalšie sú staršie než nájdená práca (čas autora ≤ čas commitu)
    elif l and h in cas and not NIE_PRACA.search(l): t_ja=max(t_ja,cas[h][0]); h=None
if not t_ja: sys.exit(0)          # v projekte za 30 dní nepracoval — oznámenie by ho rušilo
# čo iní zmenili po mojej práci. Súbor, ktorý po nej len vznikol, môj byť nemôže, a strop platí až po overení vlastníctva
# (29. 9.): hromadné importy pošty pridajú desiatky súborov za pár hodín, strop 60 sa zaplnil skôr, než sa vlastníctvo overilo,
# a v AIB tak zmizlo, že Peter Káčerík 17. 9. odstránil štyri súbory PS. Rovnako ich odrezal limit 200 commitov.
kand={}; najstarsia={}; kto=None
for l in prud(['log',f'--since=@{t_ja+1}','--format=@@%an\t%ae\t%ad\t%s','--date=format:%-d. %-m.','--name-status','--no-renames','-n','3000'],T0+0.7):
    if l.startswith('@@'):
        c=l[2:].split('\t',3); kto=None
        if len(c)>=4 and not STROJ_SPRAVA.search(c[3]) and not je_ja(c[0],c[1]): kto=(c[0],c[2],c[3])
        continue
    if not kto or '\t' not in l: continue
    st_,f=l.split('\t',1)
    if NIE_PRACA.search(f): continue
    najstarsia[f]=st_[:1]            # história ide od najnovšieho — posledný zápis je najstaršia zmena po mojej práci
    if f not in kand: kand[f]=(kto[0],kto[1],'odstránil(a) z projektu' if st_.startswith('D') else 'zmenil(a)',kto[2])
kand={f:x for f,x in kand.items() if najstarsia.get(f)!='A'}
# kto súbor menil posledný PRED mojou poslednou prácou — jeden prechod históriou pre všetky súbory naraz (cesty doslovne:
# „[“ alebo „*“ v názve súboru nie je vzor)
pred={}; zoznam=list(kand)[:600]; a=None
for l in (prud(['--literal-pathspecs','log',f'--before=@{t_ja+1}','--format=@@%an\t%ae','--name-only','--no-renames','--',*zoznam],T0+1.1) if zoznam else ()):
    if l.startswith('@@'): a=l[2:].split('\t'); continue
    if l and a and l in kand and l not in pred:
        pred[l]=a
        if len(pred)>=len(zoznam): break
zmeny=dict([(f,x) for f,x in kand.items() if f in pred and len(pred[f])==2 and je_ja(*pred[f])][:60])
if not zmeny: sys.exit(0)
st[PD]=dnes
try:
    os.makedirs(os.path.dirname(ns),exist_ok=True); tmp=f'{ns}.tmp{os.getpid()}'
    json.dump(st,open(tmp,'w',encoding='utf-8')); os.replace(tmp,ns)
except Exception: sys.exit(0)
pod={}
for f,(a,d,co,s) in zmeny.items(): pod.setdefault((a,co),[]).append((f,d,s))
riadky=[]
for (a,co),fs in pod.items():
    dni=sorted({d for _,d,_ in fs}); pop=[s for _,_,s in fs if not s.startswith('auto:')][:1]
    _k=len(fs); _s='tvoj súbor' if _k==1 else ('tvoje súbory' if _k<5 else 'tvojich súborov')
    riadky.append(f'   · {a} {co} {_k} {_s} ({", ".join(dni)}): {", ".join(os.path.basename(f) for f,_,_ in fs[:4])}{" …" if len(fs)>4 else ""}'
                  + (f' — popis zmeny: „{pop[0][:100]}“' if pop else ''))
print(f'NG, oznámenie autorovi (4.39): od tvojej poslednej práce v tomto projekte ({datetime.datetime.fromtimestamp(t_ja):%-d. %-m. %H:%M}) iní zmenili súbory, ktoré si vytvoril alebo posledný menil:')
print('\n'.join(riadky[:6]))
print('   Povedz to človeku na začiatku jednou správou — kto, čo, kedy a prečo (dôvod nájdeš v zápisníku alebo v popise zmeny) — a ponúkni, že mu to otvoríš. Nepýtaj sa na nič iné; dnes sa to v tomto projekte už nezopakuje.')
PY
fi
if [ "$UDALOST" = "UserPromptSubmit" ]; then
  [ "$P" = "aigrip" ] && exit 0
  PR=$(printf '%s' "$INPUT" | jq -r '.prompt // empty' 2>/dev/null | head -c 300)
  NAVRAT=$(timeout 1.5 python3 - "$STROJ" "$PD" "$SID" "$PR" "$NS" <<'PY' 2>/dev/null
import sys,os,glob,json,time,datetime,importlib
STROJ,PD,SID,PR,NS=sys.argv[1:6]
sys.path.insert(0,os.path.join(STROJ,'scripts')); A=importlib.import_module('ng-akter')
t=PR.strip()
if not t or t.startswith(A.NASTROJ_ZACIATOK) or A.NASTROJ_RE.search(t): print('nie'); sys.exit(0)   # nevložil to človek
now=time.time()
try: st=json.load(open(NS,encoding='utf-8'))
except Exception: st={}
if not isinstance(st,dict): st={}
posl=st.get('posledna') if isinstance(st.get('posledna'),dict) else {}
vl=st.get('vlozene') if isinstance(st.get('vlozene'),dict) else {}
prev=posl.get(PD)
if not isinstance(prev,(int,float)):
    # prvý raz v tomto projekte: posledná veta človeka zo stôp (bez práve odoslanej — tá môže byť v stope už zapísaná)
    prev=0.0; do=now+0.8; TR=os.path.expanduser('~/.claude/projects'); d=os.path.join(TR,'-'+PD.strip('/').replace('/','-'))
    for f in glob.glob(d+'/*.jsonl'):
        if now-os.path.getmtime(f)>3*86400: continue
        with open(f,'rb') as h:
            h.seek(0,2); n=h.tell(); h.seek(max(0,n-3000000)); data=h.read().decode('utf-8','ignore')
        for l in data.split('\n'):
            if time.time()>do: prev=now; break          # nestihlo sa — radšej nič nevložiť
            if '"type":"user"' not in l: continue
            try: o=json.loads(l)
            except Exception: continue
            if A.akter(o)!='clovek': continue
            try: ts=datetime.datetime.fromisoformat(o.get('timestamp','').replace('Z','+00:00')).timestamp()
            except Exception: continue
            if ts<now-90 and ts>prev: prev=ts
posl[PD]=now
navrat = (now-prev)>=2*3600 and not (isinstance(vl.get(SID),(int,float)) and vl[SID]>=prev)
vl={k:v for k,v in vl.items() if isinstance(v,(int,float)) and now-v<3*86400}
if navrat and SID: vl[SID]=now
st['posledna']=posl; st['vlozene']=vl
try:
    os.makedirs(os.path.dirname(NS),exist_ok=True); tmp=f'{NS}.tmp{os.getpid()}'
    with open(tmp,'w',encoding='utf-8') as f: json.dump(st,f)
    os.replace(tmp,NS)
except Exception: pass
print('ano' if navrat else 'nie')
PY
)
  [ "${NAVRAT:-nie}" = "ano" ] || exit 0
else
# zhustenie kontextu (29. 9.): SessionStart beží aj pri kompaktovaní uprostred dlhého behu bez človeka. To nie je návrat —
# automat by dostal „človek sa vracia, ponúkni mu…“ a zápis do „vlozene“ by potom umlčal skutočný návrat pri jeho prvej
# správe v tom istom okne (vlozene[SID] novšie než jeho posledná veta). Pri compact sa návratová časť nevkladá ani nezapisuje.
[ "$ZDROJ" = "compact" ] && exit 0
# návrat = posledná veta človeka v stopách tohto projektu je staršia než 2 h (alebo žiadna)
NAVRAT=$(python3 - "$P" "$STROJ" "${CLAUDE_PROJECT_DIR:-$PWD}" <<'PY' 2>/dev/null
import sys,os,glob,json,time
P=sys.argv[1]; STROJ=sys.argv[2]; PD=sys.argv[3]; TR=os.path.expanduser('~/.claude/projects')
sys.path.insert(0,os.path.join(STROJ,'scripts')); import importlib; A=importlib.import_module('ng-akter')
posl=0.0
for d in glob.glob(os.path.join(TR,'-'+PD.strip('/').replace('/','-')+'*')):
    for f in glob.glob(d+'/*.jsonl'):
        if time.time()-os.path.getmtime(f)>3*86400: continue
        for l in open(f,errors='ignore'):
            if '"type":"user"' not in line if False else '"type":"user"' not in l: continue
            try: o=json.loads(l)
            except Exception: continue
            if A.akter(o)!='clovek': continue
            ts=o.get('timestamp','')
            try:
                import datetime
                t=datetime.datetime.fromisoformat(ts.replace('Z','+00:00')).timestamp()
                if t>posl: posl=t
            except Exception: pass
# veta poslaná v okne, ktoré ostalo otvorené, sa zapísala aj do navrat.json (29. 9.) — platí novšia z oboch
try:
    v=json.load(open(os.path.expanduser('~/.aigrip-ng/navrat.json'),encoding='utf-8')).get('posledna',{}).get(PD)
    if isinstance(v,(int,float)) and v>posl: posl=v
except Exception: pass
print('ano' if (time.time()-posl)>=2*3600 else 'nie')
PY
)
[ "${NAVRAT:-ano}" = "ano" ] || exit 0
_vlozene
fi
NG_T0="$T0" NG_UDALOST="$UDALOST" python3 - "$P" "$STROJ" "${CLAUDE_PROJECT_DIR:-$PWD}" <<'PY' 2>/dev/null
import sys,io,os
P=sys.argv[1]; STROJ=sys.argv[2]; PD=sys.argv[3]
sys.path.insert(0,os.path.join(STROJ,'scripts')); import importlib
_NA_ODOSLANIE = []   # OVN (4. 10.): „Toto na odoslanie ešte čaká“ z pohľadu pre otázku „odišlo?“ na konci
# K3 (25. 9.): začiatok pohľadu „kde stojíme" pri návrate — vlastný blok, jeho chyba nezastaví otázky nižšie
def _pohlad():
    import re, json, datetime
    kp = os.path.join(PD, '.aigrip', 'ng', 'KDE-STOJIME.md')
    if P == 'aigrip': return
    # B11 (po slove PS, STAVBA časť 4): bez súboru sa pohľad skúsi vykresliť naživo; keď nejde ani to, partner povie
    # výpadkovú vetu 4 (nižšie) — do vtedy sa pohľad pri návrate potichu vynechal
    t = io.open(kp, encoding='utf-8', errors='replace').read(200000) if os.path.isfile(kp) else None
    bez_suboru = t is None
    # 29. 9.: pohľad vykreslený teraz z pracovnej kópie (nič nezapíše) — súbor v klone kolegu sa neprekresľuje, pri 4 zo 6
    # návratov nemal jeho najnovšie záznamy. Zlyhanie alebo prekročený čas = ostáva súbor s poznámkou o novšom zázname.
    cerstvy = None
    try:
        import subprocess, time
        limit = 4.0
        if os.environ.get('NG_UDALOST') == 'UserPromptSubmit':   # po vykreslení musí ostať čas na otázky nižšie
            try: limit = 1.2 - (time.time_ns() - int(os.environ['NG_T0'])) / 1e9
            except Exception: limit = 0.4
        if limit < 0.25: raise TimeoutError
        _pd = os.path.normpath(PD); _vk = os.path.join(STROJ, 'scripts', 'ng-kdestojime.py')
        # vykresľovač, ktorý režim --na-vystup ešte nepozná (zrkadlo stroja mešká za rozvozom, návrat verzie), by pohľad
        # zapísal do pracovnej kópie kolegu — vtedy sa vôbec nespustí
        if "'--na-vystup'" not in io.open(_vk, encoding='utf-8', errors='replace').read(): raise RuntimeError
        r = subprocess.run(['python3', _vk, os.path.basename(_pd), '--na-vystup'],
                           env=dict(os.environ, NG_ROOT=os.path.dirname(_pd)), capture_output=True, text=True, timeout=limit)
        # len výstup s pečiatkou režimu --na-vystup: vykresľovač, ktorý ho ešte nepozná (zrkadlo stroja mešká za rozvozom),
        # by pohľad zapísal do pracovnej kópie — jeho výstup sa neberie a ostáva súbor
        if (r.returncode == 0 and r.stdout.startswith(f'# Kde stojíme — {os.path.basename(_pd)}\n')
                and 'Vykreslené pri návrate.*' in r.stdout[:600]): cerstvy = r.stdout
    except Exception:
        cerstvy = None
    if cerstvy is None and t is None:
        # B11 (po slove PS): prehľad sa nedá ukázať ani naživo, ani zo súboru — jediné miesto, kde je výpadková veta 4
        # (R51/R116) u kolegu pravdivá. Rovnaký pokyn partnerovi ako vety o výpadkoch (ng-vypadkove-vety.py; doručenie meria
        # kontrola hookov) a stopa ng-kdesom-bez-pohladu, ktorú kontrola hookov ukáže správcovi po ľuďoch.
        print('NG: povedz človeku na začiatku odpovede doslova túto vetu a nič technické k nej nepridávaj; ak si ju v tomto sedení už povedal, neopakuj:')
        print('   Prehľad práce a to, čo na teba čaká, teraz neviem ukázať. Pracujeme ďalej, zapisujem ako doteraz. Od teba nič netreba.')
        try:
            import subprocess
            subprocess.run(['bash', os.path.join(STROJ, 'scripts', 'ng-hook-stopa.sh'), 'ng-kdesom-bez-pohladu'],
                           stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=1)
        except Exception: pass
        return
    if t is None:   # bez súboru je pohľad celý zo zápisníka — hlavička nesmie tvrdiť, že dokumenty na odoslanie sú zo súboru
        t = ''; cerstvy = cerstvy.replace('<!--ODVODENE-NECITATELNE-->', '')
    now = datetime.datetime.now()
    def cist(s, n=170):
        s = re.sub(r'\*\*|`', '', s).replace('\n', ' ').strip()
        return s if len(s) <= n else s[:n - 1].rstrip() + '…'
    def vek(d):
        h = int((now - d).total_seconds() // 3600)
        return f'pred {h} h' if h < 48 else f'pred {h // 24} dňami'
    # sekcia -> [(podsekcia, riadok)]; podsekcia rozlíši v „Čo odišlo von" to, čo odišlo (zo záznamov), od toho,
    # čo na odoslanie ešte čaká, a od odvodeného z commitov (u kolegu sú to zmeny stroja — vynechávajú sa)
    def rozober(t):
        sek = {}; cur = None; sub = ''; prerus = []
        for l in t.split('\n'):
            if l.startswith('## '): cur = l[3:].strip(); sek[cur] = []; sub = ''; continue
            s = l.strip()
            # hlavná veta je tučná (**…**), vynechá sa len kurzíva „*(zapísané pred N h)*" a náhradná veta pri chýbajúcej vete
            # „kde som" (tú už hlási časť 1 tohto háčika — dvakrát by to isté len zaberalo miesto)
            if (cur == 'Kde sa práca prerušila' and s and not (s.startswith('*') and not s.startswith('**'))
                    and not s.startswith('Veta „kde som" tu nie je')): prerus.append(s)
            if s.startswith('**Toto na odoslanie'): sub = 'caka'
            elif s.startswith('*Zo záznamov'): sub = ''
            elif s.startswith('*Odvodené: zmeny'): sub = 'commity'
            if cur and s.startswith('- '):
                b = s[2:].strip()
                if b in ('žiadne', '(nič, čo by sa dalo rozpoznať)') or b.startswith('… a ďalších'): continue
                sek[cur].append((sub, b))
        m = re.search(r'naposledy zmenil (\d{1,2})\. (\d{1,2})\. (\d{4}) (\d{1,2}):(\d{2})', t)
        return sek, prerus, (datetime.datetime(int(m[3]), int(m[2]), int(m[1]), int(m[4]), int(m[5])) if m else None)
    sek, prerus, pd_cas = rozober(t)
    pd_suboru = pd_cas; odv_zo_suboru = False
    if cerstvy:
        sek_c, prerus, pd_cas = rozober(cerstvy)
        if '<!--ODVODENE-NECITATELNE-->' in cerstvy:
            odv_zo_suboru = True
            # u kolegu: zo záznamov čerstvé, odvodené zo stavu stroja (odoslané a čakajúce dokumenty, adresáti) zo súboru
            zaz = re.compile(r'^\*\*(?:\d{4}-\d{2}-\d{2}|\d{1,2}\. ?\d{1,2}\. ?\d{4})\*\* — ')
            von, caka = 'Čo odišlo von k ľuďom', 'Na koho sa čaká'
            sek_c[von] = [x for x in sek.get(von, []) if x[0] != ''] + [x for x in sek_c.get(von, []) if x[0] == '']
            sek_c[caka] = [x for x in sek.get(caka, []) if not zaz.match(x[1])] + [x for x in sek_c.get(caka, []) if zaz.match(x[1])]
        sek = sek_c
    riadky = []
    # veta „kde som": zo súboru (čerstvá), inak z pohľadu
    ks = None
    try:
        ks = json.load(io.open(os.path.join(PD, '.aigrip', 'ng', 'kde-som.json'), encoding='utf-8'))
        try: ks = importlib.import_module('ng-kdesom').normalizuj(ks)
        except Exception: pass
        ks_cas = datetime.datetime.fromisoformat(ks['ts'])
        if ks_cas.tzinfo: ks_cas = ks_cas.astimezone().replace(tzinfo=None)   # ručne zapísaná veta s pásmom by inak spadla na starší pohľad
        if (ks.get('robim') or '').strip():
            riadky.append(f'Kde sa práca prerušila ({vek(ks_cas)}): {cist(ks["robim"], 200)} — ďalej: {cist(ks.get("dalsie") or "—", 160)}')
    except Exception:
        ks = None
    if not riadky and prerus:
        riadky.append('Kde sa práca prerušila: ' + cist(' — '.join(prerus[:2]), 300))
    # zápisník raz: najnovší vecný záznam (pre poznámku o zastaranom pohľade) a otvorené otázky (tie hlási časť 2 s adresátom —
    # v „Čaká sa" by boli dvakrát a otázku pre PS by partner kolegu ponúkol kolegovi ako jeho)
    def _den(x):
        try:
            m2 = re.match(r'\s*(\d{4})-(\d{1,2})-(\d{1,2})', x)
            if m2: return datetime.date(int(m2[1]), int(m2[2]), int(m2[3]))
            m2 = re.match(r'\s*(\d{1,2})\.\s*(\d{1,2})\.\s*(\d{4})', x)
            return datetime.date(int(m2[3]), int(m2[2]), int(m2[1])) if m2 else None
        except ValueError:
            return None
    def _kluc(x): return re.sub(r'\W+', '', cist(x, 400).lower())
    def _datum_v(x):
        # dátum záznamu je na začiatku riadku („**2026-09-29** — …“); riadky odvodené zo súborov (adresát, cesta) dátum
        # záznamu nemajú — idú pred záznamy; nemožný dátum (preklep) = bez dátumu, nie pád celého výťahu
        m2 = re.match(r'\*\*(?:(\d{4})-(\d{2})-(\d{2})|(\d{1,2})\. ?(\d{1,2})\. ?(\d{4}))\*\*', x)
        if not m2: return datetime.date.min
        try:
            return datetime.date(int(m2[1]), int(m2[2]), int(m2[3])) if m2[1] else datetime.date(int(m2[6]), int(m2[5]), int(m2[4]))
        except ValueError:
            return datetime.date.min
    posl = None; posl_d = None; stav_k = {}; roz_z = {}
    try:
        for i, l in enumerate(io.open(os.path.join(PD, 'NG-zaznamy.md'), encoding='utf-8', errors='replace')):
            if not l.startswith('| `'): continue
            c = [x.strip() for x in l.strip().strip('|').split('|')]
            if len(c) >= 14 and c[1] == 'rozhodnutie': stav_k[c[0]] = c
            if len(c) >= 7 and c[1] == 'rozhodnutie': roz_z[c[0]] = (i, c)
            if len(c) >= 7 and not c[1].startswith(('priebeh', 'znack')) and not c[5].startswith('NG:'):
                d = _den(c[2])
                if d and (posl_d is None or d >= posl_d): posl, posl_d = c, d
    except Exception: pass
    otazky = [_kluc(c[5])[:50] for c in stav_k.values() if c[7] == 'návrh' and _kluc(c[5])]
    # 29. 9. (kontrola): pohľad zoskupuje rozhodnutia podľa „kde platí“ a nesie aj otázky v stave „návrh“ — výber z pohľadu
    # ukazoval otázku pre iného človeka ako rozhodnutie a najnovšie rozhodnutie dňa vynechal. Zo zápisníka: posledný riadok
    # kľúča (odpoveď na otázku je nový riadok s tým istým kľúčom), len platné, najnovšie podľa (dátum, poradie v súbore).
    roz_pl = sorted([(i, c) for i, c in roz_z.values() if (c[7] if len(c) > 7 else '') in ('', 'platí') and c[5]],
                    key=lambda ic: (_den(ic[1][2]) or datetime.date.min, ic[0]))
    rozh = [f'{c[2]} · ' + (c[3] if c[4] == 'povedal' else f'{c[3]} ({c[4]})') + f': {c[5]}' for _, c in roz_pl[-2:]]
    def _nie_otazka(b): return 'otázka zo zápisníka' not in b and not any(q in _kluc(b) for q in otazky)
    # z ostatných sekcií najnovšie riadky (zoznamy zo záznamov sú chronologické = najnovšie na konci). „Posledná vecná
    # práca" (git log) sa neberie: partner ju vidí sám a u kolegu v nej sú zmeny stroja, nie jeho práca.
    # zápisník sa zlučuje „zober oba“ (merge=union), poradie riadkov nie je poradie času — najnovšie sa berú podľa dátumu
    von_z = sorted([b for s, b in sek.get('Čo odišlo von k ľuďom', []) if s == ''], key=_datum_v)
    von_c = [b for s, b in sek.get('Čo odišlo von k ľuďom', []) if s == 'caka']
    _NA_ODOSLANIE[:] = von_c
    for znacka, zoznam in (('Čaká sa', sorted([b for _, b in sek.get('Na koho sa čaká', []) if _nie_otazka(b)], key=_datum_v)[-2:]),
                           ('Odišlo von', von_z[-1:]), ('Na odoslanie ešte čaká', [] if von_z else von_c[-1:]),
                           ('Rozhodnutie', rozh if roz_z else [b for _, b in sek.get('Posledné rozhodnutia — podľa nich sa koná', [])][-2:]),
                           ('Zámer', [b for _, b in sek.get('Zámery', [])][-1:])):
        for x in zoznam[::-1]:
            x = re.sub(r'^(\S+ — )?Odišlo von:\s*', r'\1', x)                          # „Odišlo von: …" v samotnej vete
            x = re.sub(r'^[^·]{1,90}· (?=(\d{4}-\d{2}-\d{2}|\d{1,2}\. \d{1,2}\. \d{4}) · )', '', x, count=1)   # meno záznamu pred dátumom
            riadky.append(f'{znacka}: {cist(x, 150)}')
    if not riadky: return
    if cerstvy and odv_zo_suboru:
        zdroj = (', záznamy vykreslené teraz zo zápisníka; dokumenty na odoslanie a ich adresáti zo súboru'
                 + (f' z {pd_suboru:%d. %m. %H:%M}' if pd_suboru else ''))
    elif cerstvy:
        zdroj = ', vykreslený teraz zo zápisníka'
    else:
        zdroj = f', obsah z {pd_cas:%d. %m. %H:%M}' if pd_cas else ''
    hl = ('NG: človek sa k projektu vracia po ≥ 2 h — začiatok pohľadu „kde stojíme" (.aigrip/ng/KDE-STOJIME.md' + zdroj
          + '). Podklad pre teba, nie správa pre človeka: '
          'ak nadväzuje na rozrobenú prácu, ponúkni najprv jej dokončenie; celý pohľad je v súbore'
          + (' (môže byť starší — čo platí teraz, rozhoduje zápisník).' if cerstvy else '.'))
    if bez_suboru:   # B11 (po slove PS): súbor s pohľadom v projekte nie je — hlavička naň neodkazuje
        hl = ('NG: človek sa k projektu vracia po ≥ 2 h — začiatok pohľadu „kde stojíme" (vykreslený teraz zo zápisníka; '
              'súbor .aigrip/ng/KDE-STOJIME.md v projekte nie je). Podklad pre teba, nie správa pre človeka: '
              'ak nadväzuje na rozrobenú prácu, ponúkni najprv jej dokončenie.')
    # u kolegu sa pohľad v jeho klone neprekresľuje (vykresľuje ho len stroj v domove PS). Dátum v pohľade je čas poslednej
    # ZMENY obsahu, nie vykreslenia, takže vek sám nič nehovorí (tichý projekt má starý dátum a pohľad je presný). Zastaraný je
    # pohľad, keď zápisník má záznam z neskoršieho dňa — vtedy povie ten záznam (najnovší podľa dátumu: merge=union mieša poradie).
    star = ''
    if not cerstvy and posl and (pd_cas is None or posl_d > pd_cas.date()):
        star = ((f'   (pohľad je z {pd_cas:%d. %m.}' if pd_cas else '   (pohľad nemá dátum')
                + f'; zápisník má novší záznam z {posl[2]}: {cist(posl[5], 150)} — čo platí teraz, rozhoduje zápisník a veta „kde som".)')
    # ohraničenie: spolu ≤ 1 800 znakov a ≤ 8 riadkov obsahu; hlavička a poznámka o starom pohľade sa nikdy neodrežú
    von = [hl]; zvysok = 1800 - len(hl) - len(star) - 2
    for r in riadky[:8]:
        r = '   · ' + r
        if len(r) + 1 > zvysok: break
        von.append(r); zvysok -= len(r) + 1
    if star: von.append(star)
    print('\n'.join(von))
try: _pohlad()
except Exception: pass
Z=importlib.import_module('ng-zapis')
# otázky: rozhodnutia v stave návrh (posledný stav per kľúč), adresát ktokoľvek (v sedení PS = PS, kým K-10 pre iných nie je)
posl={}
zp=os.path.join(PD,'NG-zaznamy.md')
if os.path.exists(zp):
    for l in io.open(zp,encoding='utf-8'):
        if not l.startswith('| `'): continue
        c=[x.strip() for x in l.strip().strip('|').split('|')]
        if len(c)>=14 and c[1]=='rozhodnutie': posl[c[0]]=c
ot=[c for c in posl.values() if c[7]=='návrh']
# K-10 (21. 9.): otázka s adresátom PS sa v projekte kolegu nesmie ponúknuť kolegovi ako jeho — kto sedí, hovorí unixový účet
ja=os.environ.get('USER') or os.environ.get('LOGNAME') or ''
je_ps=(ja=='pstrocka')
def pre_ps(c): return (c[8] or '').strip().lower() in ('ps','peter strocka','peter stročka','pstrocka','')
# GS (24. 9.): doručenie otázky adresátovi, ktorý nie je PS — adresát sa porovná s menom sediaceho (ng/ludia.tsv: účet|meno);
# do vtedy dostal kolega ako „svoju“ každú otázku, ktorá nebola pre PS, aj otázku pre niekoho tretieho
import unicodedata as _ud
def _n(x): return ''.join(ch for ch in _ud.normalize('NFD',(x or '').lower()) if _ud.category(ch)!='Mn').strip()
_meno=''
try:
    for _l in io.open(os.path.join(STROJ,'ng','ludia.tsv'),encoding='utf-8'):
        _u,_, _m=_l.strip().partition('|')
        if _u==ja: _meno=_m
except Exception: pass
# B10 (2. 10.): ten istý určovateľ adresáta ako meranie doručenia (ng-dorucenie.adresat — bez diakritiky, spojovník = medzera,
# preklep vnútri priezviska, nie ženský tvar mena, „Peter Stročka“ = PS, „Peter“ = ten Peter, ktorý v projekte pracuje). Do vtedy
# mal adresát zapísaný s preklepom v priezvisku alebo so spojovníkom v dvojitom priezvisku vlastnú otázku medzi cudzími.
# Kto v projekte pracuje, vie stroj zo stôp len v sedení PS (stopy kolegov číta len on); u kolegu je to on sám. Bez modulu = staré porovnanie.
try:
    _D=importlib.import_module('ng-dorucenie'); _LU=_D.ludia(os.path.join(STROJ,'ng','ludia.tsv'))
    try: _V=sorted({_D.ST.kto(_d) for _d in _D.ST.stopy(P) if os.path.isdir(_d)}|{ja})
    except Exception: _V=[ja]
    # D4 (10. 10.): stopy ostatných ľudí číta len PS, takže v sedení kolegu bolo _V=[ja] — holé krstné meno („Peter“) sa
    # vyriešilo vždy na sediaceho a otázka pre iného Petra by mu prišla ako jeho. Zoznam ľudí projektu (účty) vykresľuje
    # ng-dorucenie.py vedľa ludia.tsv a spoločné miesto ho nesie rovnako; chýbajúci alebo nečitateľný zoznam = ako doteraz.
    try: _V=sorted(set(_V)|set(_D.ludia_projektu(P,os.path.join(STROJ,'ng',_D.PROJEKTY_LUDIA))))
    except Exception: pass
except Exception: _D=None
def pre_mna(c):
    if _D is not None and _LU:
        try: return ja in _D.adresat(c[8] or 'PS',_LU,_V)[0]
        except Exception: pass
    if je_ps: return pre_ps(c)
    a=_n(c[8])
    if not a or pre_ps(c): return False
    return a==_n(ja) or (bool(_meno) and all(t in _n(_meno).split() for t in a.split()))
moje=[c for c in ot if pre_mna(c)]; cudzie=[c for c in ot if not pre_mna(c)]
# B1 (2. 10.): veľké veci prvé — otázka z nálezu kontroly tichých potvrdení (dôverový incident, R70 (4)) bola u Mariána
# Lacenu dvanásta z dvanástich a do ukážky by sa nedostala nikdy; poradie inak ostáva poradím v zápisníku
# D2 (10. 10.): v rámci toho najprv otázky PO TERMÍNE a potom NAJSTARŠIE — do vtedy bolo poradie za „veľkou vecou“ poradím
# riadkov v zápisníku, ktoré pri zlučovaní „zober oba“ (merge=union) nie je ani poradím dní: v aspecta-finops ležala
# 10. 10. jediná otázka po termíne dvanásta zo šestnástich a v account-cp otázky zo 4. – 5. 10. pod novšími.
# Termín je 10. stĺpec (c[9]), dátum vzniku 3. (c[2]); nečitateľný dátum nie je „po termíne“ a ide za čitateľné.
import datetime as _dtm, re as _re
_DNES=_dtm.date.today()
def _dat(x):
    if _D is not None:
        try: return _D._den(x)
        except Exception: pass
    _m=_re.match(r'\s*(\d{4})-(\d{1,2})-(\d{1,2})',x or '')
    try: return _dtm.date(int(_m[1]),int(_m[2]),int(_m[3])) if _m else None
    except ValueError: return None
def _poradie(c):
    _t=_dat(c[9] if len(c)>9 else '')
    return (_n(c[11] if len(c)>11 else '')!='velky', not (_t is not None and _t<_DNES), _dat(c[2]) or _dtm.date.max)
moje.sort(key=_poradie)
# D1 (10. 10.): strop výpisu vlastných otázok. Zoznam otázok nikdy nemal znakový rozpočet (1 800 znakov a 8 riadkov platí
# len pre výňatok „kde stojíme“ nad ním) — strop bol jediná hranica a 10. 10. skrýval 32 otázok v piatich projektoch
# (aigrip 11 z 19, account-cp 10 z 18, aspecta-finops 8 z 16, produkt-dailychecks 2 z 10, cp-controlling 1 z 9), medzi
# nimi dve po termíne. 40 = dvojnásobok najdlhšej dnešnej fronty (19), takže sa neskryje nič ani pri dvojnásobnej záťaži;
# výpis má potom najviac ~3,5 kB, menej než ostatok háčika. Keď strop aj tak zaberie, povie sa to ako NÁLEZ (do 2. 10.
# sa skrývalo potichu a nikto to nevidel) a čo sa odreže, je po oprave poradia to najnovšie a nie po termíne.
STROP_OTAZOK=40
n=0; p=Z.navrhy_cesta(P)
if os.path.exists(p):
    for l in io.open(p,encoding='utf-8'):
        if l.startswith('| `') and '| návrh |' in l: n+=1
if moje or n:
    print(f"NG: človek sa vracia po ≥ 2 h — v pohľade „kde stojíme\" tohto projektu čaká {len(moje)} otázok na jeho slovo a {n} návrhov zo sedení; ponúkni mu ich v prvej odpovedi jednou vetou (odpoveď zapíšeš cez ng-zapis.py --odpoved / --potvrd; „zvyšok zahodiť\" ukončí návrhy).")
    # B10 (2. 10.): do vtedy najviac 3 — ďalšie sa neukázali nikdy (meranie doručenia to 2. 10. našlo v troch projektoch);
    # D1 (10. 10.): 8 → STROP_OTAZOK (najdlhšia fronta dňa bola 19 a 32 otázok sa skrývalo)
    for c in moje[:STROP_OTAZOK]: print(f"   otázka pre {c[8] or '—'}: {c[5][:110 if len(moje) <= 3 else 80]}")
    if len(moje) > STROP_OTAZOK: print(f"   … a ďalších {len(moje) - STROP_OTAZOK} sa NEUKÁZALO — strop háčika je {STROP_OTAZOK} a fronta je dlhšia; je to nález stroja, povedz to človeku a znenie nájde v pohľade „kde stojíme“ tohto projektu")
if cudzie:
    print(f"NG: v zápisníku je aj {len(cudzie)} otázok pre iných ľudí (adresát je uvedený) — tomuto človeku ich neponúkaj ako jeho; ak je adresátom on, povie to sám.")
    for c in cudzie[:3]: print(f"   otázka pre {c[8] or '—'}: {c[5][:90]}")
# B6 (2. 10.): rozpor — neskorší záznam hovorí, že platné slovo tohto človeka neplatí (produkt-dailychecks PRO-R261002-d5d6);
# zrušiť ho smie len jeho slovo, preto sa pripomenie len jemu (ng-zapisnik.py; jeho chyba otázky vyššie nezastaví)
try:
    def _moj(kto):
        k=_n(kto)
        if je_ps: return k in ('ps','peter strocka','pstrocka')
        return bool(k) and (k==_n(ja) or (bool(_meno) and all(t in _n(_meno).split() for t in k.split())))
    _ZM=importlib.import_module('ng-zapisnik')   # pôvod a autor z riadku obsahu (znova otvorený záznam má v z['a'] otázku nálezu)
    _zz=[(z,_ZM._obsah(z)) for z in _ZM.citaj(zp,P)[0] if z.get('sporne') and z['stav'] not in ('zrušené','nahradené')]
    _sp=[z for z,o in _zz if o['povod']!='partnerov poznatok' and _moj(o['kto'])]
    if _sp:
        print(f"NG: v zápisníku je {len(_sp)}× rozpor — neskorší záznam hovorí, že slovo tohto človeka neplatí, no zrušené nie je; spýtaj sa ho jednou vetou, či platí, a podľa odpovede zapíš ng-zapis.py --zrus <projekt> <kľúč> povedal \"<jeho meno>\" \"<jeho veta>\" alebo --nahrad <projekt> <kľúč> povedal \"<jeho meno>\" \"<nová veta>\" --dovod \"<prečo>\".")
        for z in _sp[:3]: print(f"   {z['kluc']}: {_ZM._obsah(z)['veta'][:90]} — podľa {z['sporne'][0]} neplatí")
    # vlastný poznatok partnera, o ktorom neskorší záznam hovorí, že neplatí (aigrip AIG-P260926-a06b): opraví ho partner sám,
    # človeka sa nepýta — do vtedy nemal spúšťač, pohľad ho človeku zámerne neukazuje (overovateľ B6, 2. 10.)
    _spp=[z for z,o in _zz if o['povod']=='partnerov poznatok' and not z.get('znovu')]
    if _spp:
        print(f"NG: {len(_spp)}× vlastný poznatok partnera, o ktorom neskorší záznam hovorí, že neplatí, no zrušený nie je — oprav ho sám, človeka sa nepýtaj: ng-zapis.py --nahrad {P} <kľúč> partner partner \"<veta, ktorá platí>\" --dovod \"<prečo>\" (alebo --zrus {P} <kľúč> partner partner \"<prečo>\").")
        for z in _spp[:3]: print(f"   {z['kluc']}: {_ZM._obsah(z)['veta'][:90]} — podľa {z['sporne'][0]} neplatí")
except Exception: pass
# OVN (4. 10.): „odišlo?“ — koncepty mailov z pohľadu, o ktorých zápisník nevie, či odišli; rozhoduje ng-odislo-otazka.py
# (spoločné miesto stroja, zmluva ZNACKA). Pri odoslaní správy len v zvyšku času (háčik má na všetko 2 s); chyba = ticho.
# OVS (4. 10.): aj bez konceptov — modul pridá sľuby odovzdania zo zápisníka (záloha, interný mail, podklady bez súboru)
try:
    _ok = True
    if _ok and os.environ.get('NG_UDALOST') == 'UserPromptSubmit':
        import time as _tm
        try: _ok = (_tm.time_ns() - int(os.environ['NG_T0'])) / 1e9 < 1.5
        except Exception: _ok = False
    if _ok:
        _OO = importlib.import_module('ng-odislo-otazka')
        if getattr(_OO, 'ZNACKA', '') == 'odislo-otazka 1':
            _t = _OO.pri_navrate(P, PD, STROJ, list(_NA_ODOSLANIE))
            if _t: print(_t)
except Exception: pass
PY
exit 0
