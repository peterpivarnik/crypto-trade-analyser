#!/usr/bin/env bash
# Stop hook balíka NG — tri veci na konci ťahu; adresát je partner, nie človek (R104) — človek z toho nevidí nič.
#
# 1) Veta „kde som“ (pravidlo 9 „navigátor“, zamknuté 22. 9.; kus FP, 23. 9.). Do 23. 9. pripomínal vetu len hook pri
#    štarte sedenia: za 7 dní ju malo 11 z 20 projektov, v ktorých sa pracovalo, a 14 z 32 sedení skončilo bez záznamu
#    aj bez vety — z tých sa po prerušení nedá pokračovať. Keď ťah končí, sedenie už pracovalo (aspoň 5 použití nástrojov)
#    a veta je staršia než začiatok sedenia (alebo chýba), vráti partnerovi jednu prosbu ju prepísať. NAJVIAC RAZ ZA SEDENIE (od 9. 10. za úsek práce, 1c).
#    1b) Krátke sedenie (kus NVK, 8. 10.): menej než 5 nástrojov, ale meranie návratu ho ráta ako sedenie s prácou (správa
#    človeka dlhšia než 30 znakov — jeden určovateľ ng-navrat.py praca_cloveka) a v sedení nie je zápis do zápisníka ani
#    veta „kde som“ → tá istá jedna prosba, s dôvetkom, aby veta preniesla, čo z doterajšej platí. 8. 10. 6:10 v monitore:
#    zadanie auditu vložené do sedenia a po 19 s pozastavené („teraz nerob, dám ti vedieť“), jeden nástroj, nič zapísané —
#    jediné sedenie zo 111, z ktorého sa nedá pokračovať. Prehrávka 7 dní: krátkych sedení s prácou bolo 6, žiadne nemalo
#    zápis ani vetu v sedení (meranie päť z nich pustilo len pre záznam z toho dňa z iného sedenia) — prosba príde v 6:
#    monitor; dve sedenia Mariána Lacenu v aspecta-finops (zistenie, že vypršalo heslo aplikácie na sťahovanie faktúr,
#    a návrh, ako chytať nové náklady, ostali len v texte); bpw (pokračovanie podľa odovzdávky); aigrip (ťah prerušený
#    človekom); account-cp (otázka, kam nahrať podklady — jediná bez rozrobenej práce). Bez modulu merania na spoločnom
#    mieste (alebo pri inej zmluve) či bez rozpoznávača sa krátke sedenie nekontroluje.
#    1c) Okno obnovené po prestávke (kus PSE, 9. 10.): „sedenie“ v bodoch 1 a 1b je posledný úsek práce — nový začína
#    správa človeka po ≥ 2 h bez správy v stope (ng-navrat.py useky(), ten istý určovateľ ako meranie návratu). EP 5. 10.:
#    Elena Pitterová obnovila okno zo 4 dní skôr, partner jej stiahol na server druhý projekt a prihlásil ju ku GitHubu;
#    veta „kde som“ z 1. 10. v tom istom okne prosbu umlčala a z práce sa pri návrate nedalo pokračovať. Vety, zápisy,
#    nástroje a prosba spred prestávky novú prácu nekryjú; bod 5 číta celé sedenie ako doteraz. Bez funkcie na spoločnom
#    mieste háčik ráta celé sedenie ako do 9. 10.
#    Kde sa to rozhoduje (kus RKS, 15. 10.): body 1, 1b, 1c a 5 rozhoduje ng-kdesom-straz.py na spoločnom mieste stroja
#    (zmluva ZNACKA 'kdesom-straz 1') — opravy z 8.–11. 10. dostali projekty PS až rozvozom 14. 10. a 58 z 87 klonov
#    kolegov ich 15. 10. nemalo (account-cp 14. 10. 8:47 bez prosby, kus NVH); odteraz platí oprava do 5 minút všade.
#    Funkcia kde_som() nižšie je NÁHRADA so stavom z 15. 10. — beží, keď modul nevidno, má inú zmluvu alebo spadne;
#    opravy idú do modulu, nie sem. Stráž: sada straz-9 nad oboma cestami.
#
# 2) Ohlásený krok (pravidlo 1a „plynulosť“, 24. 9.): posledný odsek ohlasuje vlastný ďalší krok partnera, ktorý nie je
#    podmienený človekom, behom ani udalosťou, a ťah by sa skončil → raz zastaví: „Ohlásil si ďalší krok (…) — urob ho
#    teraz, alebo napíš, čo ťa blokuje.“ Za 7 dní musel Peter Tóth v cp-controlling 15× písať „pokračuj“ („pokracuj
#    automaticky, ked nepotrebujes z mojej strany zasah“, 21. 9. 18:30). Pravidlo je v ng-ohlaseny-krok.py, ktorý ide
#    v balíku vedľa tohto háčika (ten istý modul používa strážca konca ťahu v aigrip). NAJVIAC RAZ ZA ŤAH
#    (stop_hook_active); pustí otázku, žiadosť o úkon človeka, krok viazaný na beh, claude -p, podagenta.
#
# 3) Echo „Zapísal som“ (pravidlo zamknuté S2, cieľ 90 %; kus IE, 27. 9.): v tomto ťahu partner zapísal do zápisníka
#    a človeku to nezopakoval → raz vráti: „zopakuj jednou vetou Zapísal som: …“. Meranie P-echo bolo 86 % — po oprave
#    merania (ng-echo.py: len príkazy, ktoré zápisník naozaj píšu) 89 %; z 18 skupín bez echa 9× cp-controlling (20.–23. 9.,
#    zápis rukou), 6× aigrip (aj náhradné behy stavby 25. a 26. 9.). Čo je zápis, rozhoduje ng-echo.py na spoločnom mieste
#    stroja (ten istý modul číta meranie); keď ho nevidno, háčik echo nekontroluje. NAJVIAC RAZ ZA ŤAH (stop_hook_active).
#
# 4) ODOBRANÉ 10. 10. 2026 (PS, STAVBA 4.87 d): súhra so strážcom zbytočných otázok. Do vtedy sa bod „ťah končí
#    čakaním na človeka, ale v zápisníku to nie je“ vynechal, keď strážca koncovú otázku vrátil partnerovi — a to vedel
#    len režim „zastav“. Zastavovanie je zrušené (strážca už len zapisuje), takže vynechávať nie je čo: čakanie z koncovej
#    otázky sa posudzuje ako každé iné.
#
# 5) Čo odišlo von · na koho sa čaká · pripravené na odoslanie človekom (pilotný text; 2. 10. 2026, PS 20:22 „čo nám
#    chýba … hneď“). Do 2. 10. išli tieto prosby spolu s vetou „kde som“ NAJVIAC RAZ ZA SEDENIE — a tú jednu prosbu
#    takmer vždy minula veta „kde som“ v prvom ťahu; čakanie a príprava, ktoré prišli neskôr, pripomenuté neboli
#    (rozbor 20 chýb merania P-von/P-caka z 2. 10. 19:16: 7 skutočných — 5 pre jednu prosbu za sedenie, 2 pre oneskorený
#    rozvoz; 11 falošných zásahov; 2 sedenia pred prechodom projektu). Po novom: vlastná prosba „NG, von a čakanie:“
#    pri každej NOVEJ udalosti bez zápisu, najviac 3× na druh (von · čakanie · príprava) za sedenie, tú istú udalosť
#    nikdy dvakrát. Udalosti a zápisy rozpoznáva
#    ng-von-caka.py na spoločnom mieste stroja (zmluva ROZPOZNAVAC) — ten istý rozpoznávač ako meranie; háčik si už
#    nenesie vlastnú kópiu vzorov, zmena vzoru platí do 5 minút všade. Keď modul nevidno, háčik pripomína len vetu
#    „kde som“. Značka sedenia je JSON v $NG_KDESOM_ZNACKY (inak /tmp); stará prázdna značka = veta „kde som“ už bola.
#    Oprava po nezávislom overení (2. 10. večer): háčik pripomínal aj zapísané (otázku na Mariána v aspecta-finops
#    v 3. aj 5. ťahu) a tú istú vec dvakrát. Preto (a) rozpoznávač dostane zápisník projektu (NG-zaznamy.md): otvorená
#    otázka či záznam s tým istým obsahom kryje aj čakanie a odoslanie zo skoršieho sedenia; (b) čo partner v pokračovaní
#    ťahu po prosbe zopakuje, si háčik zapamätá bez prosby; (c) vec s tým istým obsahom ako už pripomenutá nepríde znova
#    (značka nesie aj krátke texty pripomenutých vecí); (d) prvý beh po starom háčiku (stará prázdna značka) zapamätá
#    bez prosby to, čo bolo otvorené do času starej prosby, a nečitateľná značka všetko otvorené — nič nepríde dvakrát.
#    Druhé overenie (3. 10.): značka v spoločnom /tmp niesla vety z rozhovorov a čítať ju mohol každý účet (open bez
#    práv = 0664). Odteraz nesie len odtlačok obsahu (kmene slov, mená, kódy — ng-von-caka.py odtlacok()), nie vety,
#    a vzniká s právami 0600 (mkstemp, nezávisí od umask). Partner, ktorý prosbu nechal tak, dostal v ďalšom ťahu
#    druhú: „Ešte čakám na tvoje slovo, či mám prezrieť tie tri akceptácie“ (PM-PT), „Starší koncept v Outlooku…“
#    (am-ps). Preto (e) „stále / ešte / ďalej čakám …“ je to isté čakanie, keď zdieľa obsah s vecou, ktorú háčik už
#    pripomenul či zapamätal (pri nadpise „**…:**“ aj so zoznamom pod ním; cesty k súborom sa nerátajú), alebo nenesie
#    nič nové (iná vec so „ešte“ príde — mpai 1aa8fa92); (f) ďalšia príprava na
#    odoslanie po prosbe o prípravu je tá istá príprava (meranie ju ráta raz za sedenie). Prosba o čakanie v texte
#    a o prípravu teraz tiež hovorí, že otázku treba po odpovedi uzavrieť (--odpoved). Celkom inými slovami bez „stále /
#    ešte“ môže tá istá vec prísť ešte raz — prosba to partnerovi hovorí, nesľubuje opak.
#
# 6) Zmienka o nezapísanej veci (R16 bod 4, R47; B15, 2. 10.): človek v tomto ťahu odkázal na dohodu, schválenie, poradu alebo
#    mail bez obsahu a v zápisníku k tomu nič nie je → raz poprosí partnera zapísať otázku do zoznamu (nie zastaviť prácu).
#    Rozpoznávač, hranice a meranie sú v ng-membrana.py na spoločnom mieste stroja; keď ho nevidno, háčik robí len body 1–5.
#
# 7) Klikateľný odkaz (pravidlo zamknuté S2, cieľ 90 %; 3. 10.): posledná správa ťahu spomína súbor, ktorý leží na tomto
#    počítači, a nemá naň odkaz [názov](cesta) → raz vráti: „pridaj krátku vetu s odkazom“. Meranie P-odkaz bolo 89 %
#    (661 zo 742); prehrávka 7 dní: zo 54 záverečných správ so súborom bez odkazu chytí 44 (zvyšok sú mená súborov zo
#    SharePointu, z citovaného mailu alebo z iného projektu), 526 správ s odkazom a 1041 bez súboru nechá ticho.
#    Čo je „spomína súbor bez odkazu“, rozhoduje ng-odkaz.py na spoločnom mieste stroja (ten istý modul číta meranie).
#    NAJVIAC RAZ ZA ŤAH (stop_hook_active), nie pre podagenta.
#
# 9) Dokument stavby pri požiadavke na vstup človeka (slovo PS 7. 11. 2026: „stavba musí byť vždy aktualizovaná,
#    keď sa rieši nejaká požiadavka na vstup odo mňa — zabezpeč to"): keď posledná správa ťahu žiada slovo človeka
#    k položke 4.NN, dokument ju musí nesť — a keď v odpovedi stojí odporúčanie, dokument musí odporúčať to isté.
#    7. 11. sa stalo oboje naopak: 4.226 sa v dokumente vykresľovala z riadku zápisníka s PRVÝM odporúčaním, ktoré
#    tri nezávislé behy medzitým zhodili, a 4.227 bola v dokumente len ako číslo v zozname. Určovateľ je
#    ng-caka-dokument.py na spoločnom mieste stroja; nič neprepisuje, opravu robí partner.
#
# 8) Otázka na rozhodnutie bez možností (pravidlo 5 „alternatívy pred výberom“, zamknuté 24. 9.; kus P5K, 4. 10.): ťah zapísal
#    do zápisníka otázku (ng-zapis.py --otazka), ktorú meranie J-5 ráta ako rozhodnutie, a ani jej veta, ani text partnera
#    v ťahu nemá dve možnosti s cenou či nevýhodou → raz vráti zamknuté znenie: „doplň do odpovede (a), (b) … s cenou“.
#    Za 7 dní do 4. 10.: 18 zo 40 takých otázok na ľudí (12 z 28 na kolegov). Určovateľ je ten istý ako meranie
#    (ng-p5-straz.py koniec_tahu → ng-pravidla.py p5_posud) na spoločnom mieste stroja; prehrávka 7 dní: zo zlých so
#    stopou pripomenie 9 z 9, pri dobrých mlčí 22 z 22. NAJVIAC RAZ ZA ŤAH — aj pri stop_hook_active (otázka zapísaná
#    až v pokračovaní ťahu sa posúdi), opakovaniu bráni spätná väzba v stope; nie pre podagenta ani zlyhaný zápis.
#
# Akákoľvek chyba = ticho pustiť ďalej. Háčik číta stopu raz (rozpoznávač); pravidlo 1a číta len text poslednej správy,
# ktorý dostane na vstupe.
set -u
STROJ="${NG_STROJ:-/opt/aigrip-ng}"; [ -d "$STROJ/scripts" ] || STROJ=/home/pstrocka/projects/aigrip/.aigrip
# 20. 10. 2026: tretia možnosť — koreň stroja podľa umiestnenia tohto háčika. Obe cesty vyššie sú serverové,
# takže mimo servera (Macbook PS) sa ng-hook-stopa.sh nenašiel a háčik nezanechal stopu: kontrola háčikov to
# hlásila ako nález a sľub 2.22 bol preto červený. Na serveri je riadok bez účinku (prvá cesta existuje).
[ -d "$STROJ/scripts" ] || { case "$0" in */*) _T="${0%/*}";; *) _T=".";; esac; [ -d "$_T/../scripts" ] && STROJ="$_T/.."; }
# KSMC (19. 10.): na inom počítači (Mac PS) nie je spoločné miesto ani domov PS na serveri — STROJ ukazoval do prázdna,
# moduly sa nenačítali a bežala náhrada bez úsekov práce: veta „kde som“ z 9:07 krytila v monitore aj prácu 16:10–20:10
# po 7 h prestávke v tom istom sedení (18. 10.; skúška návratu 19. 10. „kde sa prerušilo“ chýba) a prosba ukazovala na
# skript v ceste servera. Keď stroj na miestach servera nie je, vezme sa klon aigrip, v ktorom háčik sám leží, alebo
# klon aigrip vedľa projektu (~/CLAUDE/AIGrip) — len klon so skriptmi vety „kde som“ (iný projekt ich nemá).
if ! [ -d "$STROJ/scripts" ]; then
  case "$0" in */*) _h="${0%/*}";; *) _h=.;; esac
  for _s in "$_h/.." "${CLAUDE_PROJECT_DIR:-$PWD}/../AIGrip/.aigrip" "${CLAUDE_PROJECT_DIR:-$PWD}/../aigrip/.aigrip" \
            "$HOME/CLAUDE/AIGrip/.aigrip"; do
    if [ -f "$_s/scripts/ng-kdesom.py" ] && [ -f "$_s/scripts/ng-kdesom-straz.py" ]; then
      _s=$(cd "$_s" 2>/dev/null && pwd -P) && { STROJ="$_s"; break; }
    fi
  done
fi
. "$STROJ/scripts/ng-hook-stopa.sh" ng-kdesom-koniec 2>/dev/null || true
case "$0" in */*) TU="${0%/*}";; *) TU=.;; esac
MOD="$TU/ng-ohlaseny-krok.py"; [ -f "$MOD" ] || MOD="$TU/../scripts/ng-ohlaseny-krok.py"; [ -f "$MOD" ] || MOD="$STROJ/scripts/ng-ohlaseny-krok.py"   # vedľa háčika = verzia z rozvozu
IN=$(cat 2>/dev/null || true)
PD="${CLAUDE_PROJECT_DIR:-$PWD}"; P=$(basename "$PD")
python3 - "$IN" "$PD" "$P" "$STROJ" "$MOD" <<'PY' 2>/dev/null || exit 0
import sys, os, json, datetime, re, tempfile
sys.dont_write_bytecode = True     # moduly z .aigrip/hooks nenechajú __pycache__ v repe projektu (synchronizácia git add -A)
try:
    vstup = json.loads(sys.argv[1] or '{}')
except Exception:
    sys.exit(0)
if not isinstance(vstup, dict):
    sys.exit(0)
PD, P, STROJ, MOD = sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5]


def plynulost():
    """Pravidlo 1a z modulu vedľa háčika; chyba alebo iná verzia modulu = nič."""
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location('ng_ohlaseny_krok', MOD)
        m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        if getattr(m, 'ZNACKA', '') != 'ohlaseny-krok 1':
            return None
        return m.plynulost(vstup, PD, P)
    except BaseException:          # aj sys.exit v module — háčik nikdy nespadne kvôli pravidlu 1a
        return None


def _modul(meno, subor, zmluva):
    """Modul zo spoločného miesta stroja; chyba alebo iná zmluva = None."""
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location(meno, os.path.join(STROJ, 'scripts', subor))
        m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
        return m if zmluva(m) else None
    except BaseException:
        return None


def _len_kdesom(stopa):
    """Bez rozpoznávača: len to, čo potrebuje veta „kde som“ (začiatok, počet nástrojov, posledná veta)."""
    S = dict(zac=None, nastroje=0, ks_posl='', ks=[])
    with open(stopa, errors='ignore') as f:
        for l in f:
            if S['zac'] is None and '"timestamp"' in l:
                try:
                    S['zac'] = datetime.datetime.fromisoformat(json.loads(l)['timestamp'].replace('Z', '+00:00')).timestamp()
                except Exception:
                    pass
            if '"type":"tool_use"' in l:
                S['nastroje'] += l.count('"type":"tool_use"')
                if 'ng-kdesom.py' in l:
                    try:
                        for b in json.loads(l)['message']['content']:
                            cm = str((b.get('input') or {}).get('command', '')) if isinstance(b, dict) and b.get('type') == 'tool_use' else ''
                            if 'ng-kdesom.py' in cm:
                                S['ks_posl'] = cm.split('ng-kdesom.py', 1)[1]
                                S['ks'].append(dict(ts=_cas_riadku(l), text=S['ks_posl']))
                    except Exception:
                        pass
    return S


def _cas_riadku(l):
    try:
        return datetime.datetime.fromisoformat(json.loads(l)['timestamp'].replace('Z', '+00:00')).timestamp()
    except Exception:
        return None


def posledny_usek(stopa):
    """PSE (9. 10.): posledný úsek práce sedenia — okno obnovené po prestávke ≥ 2 h je nová práca a vetu „kde som“
    potrebuje znova (EP 5. 10.: veta z 1. 10. v tom istom okne umlčala prosbu pri práci o štyri dni neskôr). Určovateľ
    ng-navrat.py useky() na spoločnom mieste stroja (ten istý ako meranie návratu); chyba alebo iná zmluva = None
    (háčik ráta celé sedenie ako doteraz)."""
    NV = _modul('ng_navrat', 'ng-navrat.py', lambda m: getattr(m, 'ZNACKA', '') == 'navrat 1')
    if NV is None or not hasattr(NV, 'posledny_usek'):
        return None
    try:
        return NV.posledny_usek(stopa)
    except Exception:
        return None


def s_pracou(stopa, U=None):
    """1b: ráta meranie návratu toto sedenie ako sedenie s prácou? Určovateľ ng-navrat.py praca_cloveka na spoločnom
    mieste stroja; chyba alebo iná zmluva = False (krátke sedenie sa nekontroluje, ako do 8. 10.). PSE: pri známom
    poslednom úseku len jeho správy."""
    if U is not None:
        return bool(U.get('praca'))
    NV = _modul('ng_navrat', 'ng-navrat.py', lambda m: getattr(m, 'ZNACKA', '') == 'navrat 1')
    if NV is None:
        return False
    try:
        with open(stopa, errors='ignore') as f:
            for l in f:
                if '"user"' not in l:
                    continue
                try:
                    if NV.praca_cloveka(json.loads(l)):
                        return True
                except Exception:
                    continue
    except Exception:
        return False
    return False


def kde_som_spolocne():
    """RKS (15. 10.): body 1, 1b, 1c a 5 z ng-kdesom-straz.py na spoločnom mieste stroja. None = modul nevidno, má inú
    zmluvu, spadne alebo vráti niečo iné než dva zoznamy — vtedy rozhodne náhrada kde_som() nižšie."""
    m = _modul('ng_kdesom_straz', 'ng-kdesom-straz.py', lambda m: getattr(m, 'ZNACKA', '') == 'kdesom-straz 1')
    if m is None:
        return None
    try:
        ks, vc = m.koniec_tahu(vstup, PD, P, STROJ)
    except BaseException:          # RKS: pád modulu nestíši prosbu — rozhodne náhrada
        return None
    return (ks, vc) if isinstance(ks, list) and isinstance(vc, list) else None


def kde_som():
    """NÁHRADA (stav z 15. 10., ďalej sa neopravuje — opravy idú do ng-kdesom-straz.py). Pravidlo 9 (veta „kde som“, raz
    za sedenie) a bod 5: „čo odišlo von · na koho sa čaká · pripravené na odoslanie človekom“ — pri každej NOVEJ udalosti
    bez zápisu, najviac STROP_NA_DRUH× na druh za sedenie, tú istú udalosť nikdy dvakrát. Vráti (body vety „kde som“,
    body von a čakania)."""
    sid = vstup.get('session_id') or ''
    stopa = vstup.get('transcript_path') or ''
    if not sid or not stopa or not os.path.exists(stopa) or '/subagents/' in stopa:
        return [], []
    VC = _modul('ng_von_caka', 'ng-von-caka.py', lambda m: getattr(m, 'ROZPOZNAVAC', '') == 'von-caka 4')
    S = None
    if VC:
        try:
            with open(stopa, errors='ignore') as f:
                S = VC.prejdi_stopu(f)
        except Exception:
            S = None
    if S is None:
        VC = None
        S = _len_kdesom(stopa)
    if S.get('zac') is None:
        return [], []
    # PSE (9. 10.): veta „kde som“ (body 1 a 1b) sa posudzuje v poslednom úseku práce — po prestávke ≥ 2 h a správe človeka
    # je obnovené okno nová práca: vety, zápisy a nástroje spred prestávky ju nekryjú. Bod 5 (von, čakanie) číta celé
    # sedenie ako doteraz — zápis spred prestávky udalosť spred prestávky kryje.
    U = posledny_usek(stopa)
    po_prestavke = bool(U and U.get('useky', 1) > 1)
    if po_prestavke:
        v_useku = lambda x: x.get('ts') is None or x['ts'] >= U['zac']
        ks_u = [k for k in S.get('ks') or [] if v_useku(k)]
        K = dict(nastroje=U.get('nastroje', 0), zapisy=[z for z in S.get('zapisy') or [] if v_useku(z)], ks=ks_u,
                 ks_posl=ks_u[-1]['text'] if ks_u else '')
    else:
        K = dict(nastroje=S.get('nastroje', 0), zapisy=S.get('zapisy'), ks=S.get('ks'), ks_posl=S.get('ks_posl') or '')
    kratke = K['nastroje'] < 5
    if kratke and (VC is None or K['zapisy'] or K['ks'] or not s_pracou(stopa, U)):
        return [], []                  # 1b: krátke sedenie len s prácou podľa merania návratu a bez zápisu aj vety
    ZN = os.environ.get('NG_KDESOM_ZNACKY') or '/tmp'
    stara = os.path.join(ZN, f'ng-kdesom-koniec-{os.getuid()}-{sid}')   # do 2. 10.: prázdny súbor = jedna prosba za sedenie
    znacka = stara + '.json'
    # pamäť sedenia: odtlacky = len odtlačky obsahu (kmene slov, mená, kódy), nie vety — /tmp je spoločný (3. 10.);
    # siroke = odtlačky čakania pod nadpisom „**…:**“ aj so zoznamom (len pre „stále / ešte čakám“)
    stav = dict(ks=os.path.exists(stara), pocet={}, id=[], tahy={}, odtlacky={}, siroke={})
    prechod = None                     # 'stara' = prvý beh po starom háčiku v tomto sedení; 'poskodena' = značka sa nedá prečítať
    if os.path.exists(znacka):
        try:
            with open(znacka, encoding='utf-8') as h:
                d = json.load(h)
            if not isinstance(d, dict):
                raise ValueError('značka nie je slovník')
            stav['ks'] = stav['ks'] or bool(d.get('ks'))
            if isinstance(d.get('ks_zac'), (int, float)):
                stav['ks_zac'] = d['ks_zac']
            stav['pocet'] = {str(k): int(v) for k, v in (d.get('pocet') or {}).items()}
            stav['id'] = [str(x) for x in (d.get('id') or [])]
            stav['tahy'] = {str(k): [str(x) for x in v] for k, v in (d.get('tahy') or {}).items() if isinstance(v, list)}
            for kl in ('odtlacky', 'siroke'):
                stav[kl] = {str(k): [o for o in v if isinstance(o, list) and len(o) == 3]
                            for k, v in (d.get(kl) or {}).items() if isinstance(v, list)}
        except Exception:
            prechod = 'poskodena'; stav['ks'] = True
    elif os.path.exists(stara):
        prechod = 'stara'
    if po_prestavke and stav['ks']:
        # PSE: prosba o vetu „kde som“ z úseku pred prestávkou novú prácu nekryje; značka bez času úseku (do 9. 10.)
        # sa posúdi podľa času poslednej zmeny súboru
        kz = stav.get('ks_zac')
        if kz is None:
            try:
                kz = max(os.path.getmtime(x) for x in (znacka, stara) if os.path.exists(x))
            except ValueError:
                kz = None
        if kz is not None and kz < U['zac']:
            stav['ks'] = False

    def uloz():
        """Značka len pre tento účet: dočasný súbor s právami 0600 (mkstemp — O_EXCL, nezávisí od umask; pri umask 0002
        vznikalo 0664 a /tmp vidí každý účet), potom premenovanie. Chyba = výnimka; dočasný súbor neostane."""
        fd, t = tempfile.mkstemp(prefix=os.path.basename(znacka) + '.', dir=ZN)
        try:
            with os.fdopen(fd, 'w', encoding='utf-8') as h:
                json.dump(stav, h)
            os.replace(t, znacka)
        except BaseException:
            try:
                os.unlink(t)
            except OSError:
                pass
            raise
    # 1. 10.: vetu musí prepísať TOTO sedenie (tak to meria J-9). Čas súboru nestačil: v aigrip ho prepisuje iné okno
    # (stavba), u kolegu ho obnoví synchronizácia — sedenia účtov v aigrip a týždňové sedenie v mpai pripomienku nedostali
    ks_posl = K['ks_posl']
    chyba_ks = not stav['ks'] and not (ks_posl and re.search(r'["\']', ks_posl))
    nove = {}; zmena = False
    # text na porovnanie s pamäťou: bez ciest k súborom a cieľov odkazov (3. 10.: „work/output/…“ zhodovalo rôzne čakania)
    text_ud = lambda e: re.sub(r'\s+', ' ', VC.bez_ciest(str(e.get('kontext') or e.get('usek') or '')))[:300]
    text_si = lambda e: re.sub(r'\s+', ' ', VC.bez_ciest(str(e.get('siroky') or '')))[:400]

    def pridaj(kl, druh, oo):
        oo = stav[kl].get(druh, []) + oo
        stav[kl][druh] = [json.loads(x) for x in dict.fromkeys(json.dumps(o) for o in oo if any(o))][-30:]

    def zapamataj(druh, ee):                           # bez prosby: id a odtlačok obsahu, aby rovnaká vec neprišla ani pod iným id
        stav['id'] = list(dict.fromkeys(stav['id'] + [e['id'] for e in ee]))
        pridaj('odtlacky', druh, [VC.odtlacok(text_ud(e)) for e in ee if text_ud(e)])
        if any(text_si(e) for e in ee):
            pridaj('siroke', druh, [VC.odtlacok(text_si(e)) for e in ee if text_si(e)])
    if VC:
        posl = vstup.get('last_assistant_message')
        if not isinstance(posl, str) or not posl.strip():
            posl = None
        try:
            zaz = VC.zaznamy_zapisnika(os.path.join(PD, 'NG-zaznamy.md'))   # čakanie a odoslanie zapísané aj v skoršom sedení
        except Exception:
            zaz = []
        try:
            ot = VC.otvorene(S, posl, zaz)
        except Exception:
            ot = {}
        strop = getattr(VC, 'STROP_NA_DRUH', 3)
        tah = str(S.get('tah_id', {}).get(S.get('tah'), S.get('tah')))      # ťah = od poslednej správy človeka
        if prechod:
            # prvý beh po starom háčiku: čo bolo otvorené do jeho prosby (čas starej značky; koniec ťahu, ktorý vtedy už bežal),
            # pripomenul on — zapamätať bez prosby; pri nečitateľnej značke všetko otvorené (nevedno, čo už prišlo)
            hranica = None
            if prechod == 'stara':
                try:
                    hranica = os.path.getmtime(stara)
                except OSError:
                    hranica = None
            zac_tahu = (S.get('tah_ts') or {}).get(S.get('tah'))
            for druh in ('von', 'caka', 'prip'):
                ee = [e for e in ot.get(druh, []) if hranica is None
                      or ((zac_tahu if e.get('koniec') else e.get('ts')) or hranica + 1) <= hranica]
                if ee:
                    zapamataj(druh, ee); zmena = True
        for druh in ('von', 'caka', 'prip'):
            ot_d = [e for e in ot.get(druh, []) if e['id'] not in stav['id']]
            if not ot_d:
                continue
            if tah in stav['tahy'].get(druh, []):
                # v tomto ťahu už táto prosba prišla — čo partner v pokračovaní po nej zopakuje, nie je nová udalosť:
                # zapamätať, inak by prišla v ďalšom ťahu znova (overenie 2. 10., am-ps db246991)
                zapamataj(druh, ot_d); zmena = True
                continue
            odt = stav['odtlacky'].get(druh, [])
            # ten istý obsah ako vec, ktorú už pripomenul či zapamätal (iná správa, iné slová) — to isté sedenie: celé slová
            uz = [e for e in ot_d if any(VC.rovnake_odtlacok(text_ud(e), o) for o in odt)]
            if druh == 'caka' and any(i.startswith(('caka:', 'koniec:')) for i in stav['id']):
                # (e) „stále / ešte / ďalej čakám …“ po skoršom čakaní v sedení: to isté, keď s ním zdieľa nosné slovo, meno
                # či kód alebo nenesie nič nové („Ešte čakám na tvoje slovo, či mám prezrieť tie tri akceptácie.“ — PM-PT
                # 9a2b9dac); iná, dosiaľ nepripomenutá vec so „ešte“ príde („Jedna otázka ešte čaká na tvoje slovo.
                # Kapitola 3 navrhuje…“ — mpai 1aa8fa92, prvé pripomenuté čakanie bolo iné)
                odt_e = odt + stav['siroke'].get('caka', [])
                uz += [e for e in ot_d if e not in uz and e.get('pokracuje')
                       and VC.suvisi(e.get('kontext') or e.get('usek') or '', e.get('okolie') or '', odt_e) is not False]
            if druh == 'prip' and any(i.startswith('prip:') for i in stav['id']):
                # (f) ďalšia zmienka o pripravenom po prosbe o prípravu je tá istá príprava (meranie ju ráta raz za sedenie;
                # v prehrávke 4 z 4 opakovaní: „Starší koncept v Outlooku…“ po „koncept mailu je pripravený“ — am-ps db246991)
                uz = list(ot_d)
            if uz:
                zapamataj(druh, uz); zmena = True
            n = [e for e in ot_d if e not in uz]
            if n and stav['pocet'].get(druh, 0) < strop:
                nove[druh] = n
    if not chyba_ks and not nove:
        if zmena:
            try:
                uloz()
            except Exception:
                pass                                   # bez prosby nič nehrozí; nabudúce sa zapamätá znova
        return [], []
    if chyba_ks:
        stav['ks'] = True
        stav['ks_zac'] = U['zac'] if U else S['zac']   # PSE: úsek, ktorému prosba patrí
    for druh in nove:
        stav['pocet'][druh] = stav['pocet'].get(druh, 0) + 1
        stav['tahy'][druh] = stav['tahy'].get(druh, []) + [tah]
        zapamataj(druh, [e for e in ot.get(druh, []) if e['id'] not in stav['id']] + nove[druh])   # aj staršie otvorené: nikdy dvakrát
    uloz()                                             # bez zapísanej značky žiadna prosba (inak by sa opakovala donekonečna)
    ks_body = []
    if chyba_ks and kratke:
        ks_body.append('sedenie je krátke, ale človek v ňom zadal prácu a v zápisníku ani vo vete „kde som“ o nej nič nie je — keby sa '
                       'sedenie teraz zavrelo, nové sedenie nevie, čo bolo zadané (aj zadanie odložené na neskôr). Prepíš vetu „kde som“: '
                       'čo človek zadal a čo má nasledovať; čo z doterajšej vety ešte platí, prenes do nej: '
                       f'python3 {STROJ}/scripts/ng-kdesom.py {P} "<čo sa práve robilo>" "<čo je ďalšie>"')
    elif chyba_ks:
        ks_body.append('v tomto sedení si vetu „kde som“ ešte neprepísal (súbor mohlo medzitým zmeniť iné okno alebo synchronizácia) — prepíš ju: '
                       f'python3 {STROJ}/scripts/ng-kdesom.py {P} "<čo sa práve robilo>" "<čo je ďalšie>"')
    vc_body = []
    usek = lambda e: re.sub(r'\s+', ' ', str(e.get('usek') or ''))[:60]
    if 'von' in nove:
        vc_body.append(f'v sedení niečo odišlo von („{usek(nove["von"][-1])}“), ale v zápisníku to nie je — zapíš jednou vetou, čo, komu a kedy: '
                       f'python3 {STROJ}/scripts/ng-zapis.py {P} poznatok partner partner "<čo odišlo komu a kedy>" "<kde platí>"')
    # 3. 10.: uzavretie otázky pri každej prosbe, ktorá otázku zakladá — neuzavretá sa pri návrate ponúkne človeku znova
    # a umlčí nové čakanie s podobným obsahom (v zápisníkoch je otvorená asi polovica otázok)
    uzavri = f'python3 {STROJ}/scripts/ng-zapis.py --odpoved {P} <kľúč> <kto> "<odpoveď>"'
    if 'caka' in nove:
        kon = [e for e in nove['caka'] if e.get('koniec')]
        if kon:
            vc_body.append(f'ťah končí čakaním na človeka („{usek(kon[-1])}“), ale v zápisníku to nie je — pri návrate po prerušení '
                           'to bez zápisu nikto nenájde (veta „kde som“ nestačí). Zapíš otázku na toho, na koho sa čaká, a čo od neho treba: '
                           f'python3 {STROJ}/scripts/ng-zapis.py --otazka {P} "<adresát>" "<čo od neho treba>" "<kde platí>"; '
                           f'keď človek odpovie, otázku uzavri: {uzavri}')
        else:
            vc_body.append(f'v sedení sa na niekoho čaká („{usek(nove["caka"][-1])}“), ale v zápisníku to nie je — zapíš, na koho a čo od neho treba '
                           f'(otázka: python3 {STROJ}/scripts/ng-zapis.py --otazka {P} "<adresát>" "<čo od neho treba>" "<kde platí>"); '
                           f'keď sa dočkáš odpovede, otázku uzavri: {uzavri}')
    if 'prip' in nove:
        vc_body.append(f'v sedení si pripravil niečo, čo odošle človek sám („{usek(nove["prip"][-1])}“) — stroj jeho odoslanie '
                       'nevidí, tak ho zapíš ako otázku na toho, kto odosiela, aby sa pri návrate dalo zistiť, či odišlo: '
                       f'python3 {STROJ}/scripts/ng-zapis.py --otazka {P} "<kto odošle>" "Odišlo už <čo> <komu>?" "<kde platí>"; '
                       f'keď človek povie, že odišlo (alebo že nepôjde), otázku uzavri: {uzavri}')
    return ks_body, vc_body


def echo_zapis():
    """Pravidlo echo: zápisy do zápisníka v tomto ťahu (od poslednej správy človeka), po ktorých partner človeku
    zápis nezopakoval. Vráti text pre partnera alebo ''. Podagent, druhý zásah v tom istom ťahu, chyba = ''.
    JZ3 (27. 10. 2026): zopakovanie sa pozná podľa toho, že odpoveď NESIE VETU ZÁZNAMU (ng-echo.echo_v_texte) —
    slovenské „Zapísal som“ je len jedna z ciest. Do vtedy stráž po českom zopakovaní („Zapsal jsem: …“) pýtala
    zopakovanie znova a človek videl tú istú vetu dvakrát. Staršie ng-echo.py bez echo_v_texte = po starom."""
    if vstup.get('stop_hook_active') or vstup.get('agent_id'):
        return ''
    stopa = vstup.get('transcript_path') or ''
    if not stopa or not os.path.exists(stopa) or '/subagents/' in stopa:
        return ''
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location('ng_echo', os.path.join(STROJ, 'scripts', 'ng-echo.py'))
        EC = importlib.util.module_from_spec(spec); spec.loader.exec_module(EC)
        if getattr(EC, 'ZNACKA', '') != 'echo 1':
            return ''
    except BaseException:
        return ''
    zopakoval = getattr(EC, 'echo_v_texte', None) or (lambda t, v=(): bool(EC.ECHO_RE.search(t or '')))
    vety = []
    with open(stopa, errors='ignore') as f:
        for l in f:
            if '"type":"user"' in l and '"tool_result"' not in l:
                try:
                    o = json.loads(l)
                except Exception:
                    continue
                if o.get('type') == 'user' and not o.get('isMeta') and not o.get('isSidechain'):
                    vety = []                     # nový ťah človeka — rátajú sa len zápisy od neho
                continue
            # kým v ťahu nič nečaká na zopakovanie, stačí hrubé predsito; po zápise treba prejsť KAŽDÝ text partnera
            # (zopakovanie nemusí niesť slovo „Zapísal“ — nesie vetu záznamu, aj po česky)
            if '"type":"assistant"' not in l or not (vety or 'ng-zapis' in l or 'NG-zaznamy' in l or 'apísal som' in l):
                continue
            try:
                o = json.loads(l)
            except Exception:
                continue
            for b in ((o.get('message') or {}).get('content') or []):
                if not isinstance(b, dict):
                    continue
                if b.get('type') == 'tool_use':
                    vety += EC.zapisy(b.get('name', ''), b.get('input') or {})
                elif b.get('type') == 'text' and zopakoval(b.get('text', ''), vety):
                    vety = []
    posl = vstup.get('last_assistant_message')
    if isinstance(posl, str) and zopakoval(posl, vety):
        vety = []
    if not vety:
        return ''
    v = str(vety[0]).strip().strip('|').strip()[:90]
    kolko = 'jednu vetu' if len(vety) == 1 else f'{len(vety)} vety'
    return (f'NG, echo: v tomto ťahu si zapísal do zápisníka {kolko}' + (f' („{v}“)' if v else '') + ', ale človeku si to '
            'nezopakoval. Pridaj do odpovede jednu vetu vlastnými slovami: „Zapísal som: …“' +
            ('' if len(vety) == 1 else f' (pri viacerých „Zapísal som tieto {len(vety)} veci: …“)') +
            ' — bez kľúča záznamu a bez technických slov. Tá veta patrí človeku; nič iné o tejto pripomienke nepíš.')


def odkaz():
    """Pravidlo odkaz: posledná správa ťahu spomína miestny súbor bez klikateľného odkazu → text pre partnera, inak ''."""
    if vstup.get('stop_hook_active') or vstup.get('agent_id') or '/subagents/' in str(vstup.get('transcript_path') or ''):
        return ''
    posl = vstup.get('last_assistant_message')
    if not isinstance(posl, str) or not posl.strip():
        return ''
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location('ng_odkaz', os.path.join(STROJ, 'scripts', 'ng-odkaz.py'))
        OD = importlib.util.module_from_spec(spec); spec.loader.exec_module(OD)
        if getattr(OD, 'ZNACKA', '') != 'odkaz 1':
            return ''
        return OD.pripomienka(posl, PD, vstup.get('cwd'))
    except BaseException:
        return ''


def zmienka():
    """Bod 6 z ng-membrana.py (spoločné miesto stroja); chyba alebo iná zmluva modulu = nič."""
    try:
        import importlib.util
        spec = importlib.util.spec_from_file_location('ng_membrana', os.path.join(STROJ, 'scripts', 'ng-membrana.py'))
        m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
        return m.koniec_tahu(vstup, PD, P, STROJ) if getattr(m, 'ZNACKA', '') == 'membrana 1' else ''
    except BaseException:
        return ''


def caka_dokument():
    """Bod 9 z ng-caka-dokument.py (spoločné miesto stroja): keď ťah žiada slovo človeka k položke, dokument
    stavby musí nesť jej aktuálne znenie (slovo PS 7. 11. 2026). Chyba alebo iná zmluva modulu = nič."""
    m = _modul('ng_caka_dokument', 'ng-caka-dokument.py',
               lambda m: getattr(m, 'ZNACKA', '') == 'caka-dokument 1')
    try:
        return m.koniec_tahu(vstup) if m else ''
    except BaseException:
        return ''


def p5_otazka():
    """Bod 8 z ng-p5-straz.py (spoločné miesto stroja); chyba alebo iná zmluva modulu = nič."""
    m = _modul('ng_p5_straz', 'ng-p5-straz.py', lambda m: getattr(m, 'ZNACKA', '') == 'p5-straz 1')
    try:
        return m.koniec_tahu(vstup) if m else ''
    except BaseException:
        return ''


dovody = []
p1a = plynulost()
if p1a:
    dovody.append(p1a)
try:
    ec = echo_zapis()
except Exception:
    ec = ''
if ec:
    dovody.append(ec)
try:
    od = odkaz()
except Exception:
    od = ''
if od:
    dovody.append(od)
try:
    ks_body, vc_body = kde_som_spolocne() or kde_som()
except Exception:
    ks_body, vc_body = [], []
if ks_body:
    dovody.append('NG, pred koncom ťahu: ' + ' · '.join(ks_body) + '\nČlovekovi o tom nepíš. (Táto prosba príde najviac raz za sedenie.)')
if vc_body:
    dovody.append('NG, von a čakanie: ' + ' · '.join(vc_body) + '\nČlovekovi o tom nepíš. Ak v skutočnosti nič neodišlo a na nikoho '
                  'sa nečaká (citát, plán, podmienka), alebo to v zápisníku už je, nič nepíš. Tú istú udalosť ti znova nepripomeniem, '
                  'ani keď o nej povieš „stále / ešte čakám“; keď ju neskôr povieš celkom inými slovami, prosba môže prísť ešte raz — '
                  'vtedy tiež nič nepíš. Pri novej udalosti v sedení príde prosba znova (najviac 3× na druh).')
zm = zmienka()
if zm:
    dovody.append(zm)
p5 = p5_otazka()
if p5:
    dovody.append(p5)
cd = caka_dokument()
if cd:
    dovody.append(cd)
if dovody:
    print(json.dumps({'decision': 'block', 'reason': '\n'.join(dovody)}, ensure_ascii=False))
PY
exit 0
