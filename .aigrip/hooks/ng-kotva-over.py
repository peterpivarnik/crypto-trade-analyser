#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Overenie potvrdzovacieho znaku kotvy zo stopy sedenia (M1 v STAVBA 4.27; kus FS, 23. 9. 2026).

Kotva zavolá tento skript, keď NG_KOTVA_OVER=1 (zapnuté 23. 9., PS 4.27 V1). Pustí potvrdenie iba vtedy, keď
  · príkaz nie je z podagenta (M5),
  · audit kotvy sa dá prečítať a je v ňom zastavenie toho istého kanála v tomto sedení (inak „nie“ — fail-safe;
    u kolegu audit PS čitateľný nie je, preto kotva píše do jeho vlastného ~/.aigrip-ng/kotva-audit.jsonl — KOTVA-4),
  · veta v dodatku „# potvrdené: <veta>“ je v správe, ktorú človek napísal v TEJ ISTEJ stope sedenia PO poslednom
    zastavení toho kanála — celá správa, alebo jej celá veta či úsek medzi interpunkciou; úlomok slova ani kus
    odmietnutia („rob to“ z „nerob to“) nestačí (KOTVA-5),
  · správa je od človeka: nie zhrnutie po zhustení kontextu, nie text, ktorý vložil nástroj (isMeta, isCompactSummary,
    pokyn časovača, výsledok nástroja) (KOTVA-5); platí aj správa, ktorú človek napísal, kým partner pracoval (príloha
    queued_command — človeka v nej určí ng-akter.py; KQM, 13. 10.),
  · a tá správa nie je staršia než 30 minút (M3),
  · a do pol hodiny pred ňou ani po nej do sedenia nepísala skúška stroja (NVY, 12. 10.: test mobilného rozhrania písal
    11. 10. do živých sedení PS značku a 10–14 s po nej „ok“ — slovo, ktorým človek bežne potvrdzuje, a stopa ho od
    človeka neodlíši). Platí aj pre skoršiu vetu pri nasadení (4.35 a). Skúšku v sedení pozná ng-akter.py
    (skuska_v_sedeni); kde ho niet vedľa tohto skriptu ani na spoločnom mieste stroja, pravidlo mlčí (ako pred 12. 10.).
Výstup: prvý riadok „ok“ alebo „nie“, druhý riadok dôvod pre partnera. Akákoľvek chyba = „nie“ (fail-safe: kotva
zastaví, partner sa opýta znova).

  ng-kotva-over.py <stopa.jsonl> <veta> <kanál> <podagent 0|1> [audit.jsonl] [príznaky rozboru]
Výnimka 4.35 a (skoršia veta človeka pri nasadení do ostrej prevádzky) platí len pre kladný pokyn a len keď v príkaze
nie je iný neobnoviteľný kanál (29. 9.; nasadenie_zadane nižšie).
"""
import sys, os, io, re, json, datetime, unicodedata

OKNO_S = 30 * 60
# 1. 10.: zastavenie platí 8 h — staré zastavenie v tom istom sedení (24. 9.) odomklo 30. 9. vetu „ano“, ktorá nebola
# odpoveďou naň (8 pustení v okne účtov, 7 v okne bezpečnosti 29. 9.); skutočné odpovede prišli najneskôr do 5 h
ZAST_PLATI_S = 8 * 3600
# NVY (12. 10.): odpoveď do pol hodiny pred skúšobnou správou stroja alebo po nej potvrdením nie je (pri_skuske). Test
# píše značku a hneď po nej „ok“; okno je na obe strany, lebo poradie správ určuje test, nie stráž. 0 = pravidlo vypnuté.
OKNO_SKUSKA_S = 30 * 60
AUDIT_PS = '/home/pstrocka/projects/aigrip/.aigrip/ng/kotva-audit.jsonl'
# texty, ktoré nenapísal človek (pokyny časovača, nástroja, zhrnutia)
NIE_CLOVEK = ('Stop hook feedback', 'Pokračuj v stavbe NG', 'Stavba NG beží kontinuálne', '[Request interrupted',
              'This session is being continued', 'Base directory for this skill', 'Continue from where you left off',
              '[Image', 'Caveat:')
HRANICA = set('.,;:!?—–-()[]"\'„“”‚‘’…/¶')   # ¶ = koniec riadku v správe človeka (1. 10.)
# 29. 9. (PS 4.35 a): pred nasadením do ostrej prevádzky sa partner pýta, „ak som mu predtým už nepovedal, že to má sám
# nasadiť“ — pri tomto kanáli platí aj skoršia veta človeka v tomto sedení (nie len odpoveď po zastavení), ak hovorí
# o nasadení; do 12 h.
# 29. 9. večer (druhé kolo overenia): výnimka platí len pre názov kanála z rozboru. Starší názov („… (gh workflow run)“,
# verzie do 10.12) dnes dáva už len núdzové pravidlo kotvy, keď rozbor zlyhá — a to nevidí iný kanál v tom istom príkaze
# (nasadenie + mail dostane len názov nasadenia), takže by výnimka pustila aj mail. Pri núdzovom pravidle sa partner
# opýta (zastavenie a odpoveď po ňom). Merač (ng-kotva-overenie.py) posudzuje staré pustenia podľa oboch názvov.
KANAL_OSTRA = ('nasadenie do ostrej prevádzky',)
KANAL_OSTRA_VSETKY = KANAL_OSTRA + ('nasadenie na ostrú stránku (gh workflow run)',)
NASAD_SLOVO = re.compile(r'nasad|nasaď|deploy|produkc|ostr[úáaé]|\bprod\b|release|vydaj|pusti (to )?von', re.I)
OKNO_OSTRA_S = 12 * 3600
# 29. 9. (nález nezávislého overenia): výnimka brala hocijaký úsek hocijakej správy, keď slovo o nasadení bolo niekde
# v CELEJ správe — prešiel aj zákaz („Nenasadzuj to na produkciu, najprv to otestuj.“: „nasad“ je v „nenasadzuj“, dodatok
# „najprv to otestuj“) a odpoveď na inú otázku („4.46 Upozornenia do Teams — nasadiť novú verziu … (A)“, dodatok „(a)“).
# 29. 9. večer (druhé kolo overenia): ani „celá veta so slovom nasadenia, bez záporu a otáznika“ nestačilo — prešlo
# 9 z 19 viet, všetky na stranu pustenia: oznam („Včera sme nasadili novú verziu.“, „Nasadenie na produkciu robí Juraj.“),
# podstatné meno („Pozri deployment logy.“, „Release notes sú hotové.“), iné sloveso („Priprav nasadenie na produkciu.“),
# pokyn na náhľad použitý pre ostrú („Nasaď to na staging.“) a odklad („Nasaď to na produkciu v piatok.“); odvolanie
# „Nie.“, „Ešte nie.“, „Zruš to, nechaj to tak.“ pokyn nezrušilo. Skoršia veta preto platí len v tvare KLADNÉHO POKYNU
# alebo výslovného dovolenia (zoznam tvarov nižšie, nie slovo kdekoľvek): rozkaz nasaď/nasaďte/deployni/releasni,
# pusti/daj/hoď to na produkciu (na ostrú, von), spusti/urob nasadenie, „nasadiť môžeš sám“, „schvaľujem nasadenie“,
# alebo neurčitok na začiatku vety („Áno, nasadiť na produkciu.“). Veta nesmie byť otázka, podmienka (ak, keď, až …),
# odklad (potom, zajtra, v piatok, o 17:00 …), oznam o minulosti (nasadil, deployed …) ani cudzia reč (povedal, chce …);
# cieľ náhľadu (staging, preview, test …) ju vyradí, ak veta sama nemenuje ostrú. Po pokyne ho zruší každá neskoršia správa
# človeka so záporom, odkladom alebo zrušením (nie, ešte nie, zruš, nechaj, počkaj, stoj …). Čo zoznam nepozná, neprejde —
# zlyhanie je vždy na strane otázky (partner sa opýta znova), nie pustenia.
NASAD_POKYN = re.compile(r'nasa[dď]|deploy|releas|vyda[jť]|pus[tť]\w* (to )?von', re.I)   # len hrubý predvýber
ZAPOR = re.compile(r"\bne(?:nasa|deploy|releas|vyd|púšť|pust|spúšť|spust|rob|chc|smie|smi|mus|treba|posiel|pos[ie]la|zverej)\w*"
                   r"|\bnie\b|\bnikdy\b|\bzákaz\w*|\bzakaz\w*|\bpočk\w*|\bpock\w*|\bneskôr\b|\bneskor\b|\bešte\s+nie\b"
                   r"|\bstoj\b|\bzastav\w*|\bstop\b|\bdon'?t\b|\bdon t\b|\bdo not\b|\bnot\b|\bnever\b|\bwait\b|\blater\b"
                   r"|\bhold\b|\bnajprv\b|\bnajskôr\b|\bnajskor\b|\baž\s+(?:keď|po)\b|\baz\s+(?:ked|po)\b", re.I)
KONIEC_VETY = set('.!?…;¶')
# neskoršia správa, ktorá nasadenie odkladá alebo zastavuje (s záporom): stoj, počkaj, nerob, najprv …
ODKLAD = re.compile(r'\b(?:stoj|stop|zastav\w*|počk\w*|pock\w*|nerob\w*|najprv|najskôr|najskor|až\s+(?:keď|po)|az\s+(?:ked|po))\b',
                    re.I)
# --- tvary kladného pokynu (text bez diakritiky, malé písmená) ---
_R = lambda x: re.compile(x)
POZDRAV = _R(r'\b(?:dobre|dobry|dobru|pekne|pekny)\s+(?:rano|vecer|noc|den|popoludnie)\b')
CIEL_OSTRA = _R(r'\b(?:na|do)\s+(?:ostr\w*|produkc\w*|prod|production|live|prevadzk\w*|zivy|ziva|zive)\b'
                r'|\bnaostro\b|\bproduction\b|\bprod\b|\bprodukc\w*|\bostr(?:u|a|ej|eho|y)\b')
# cieľ, ktorý nie je ostrá prevádzka: náhľad, staging, testovacie či vývojové prostredie, slot
CIEL_INY = _R(r'\b(?:staging|stage|stg|preview|nahlad\w*|uat|sandbox|canary|testovac\w*|localhost|slot\w*)\b'
              r'|\b(?:na|do|v|vo|pre)\s+(?:test\w*|dev\w*|develop\w*|qa|beta|lokal\w*|local\w*|vyvoj\w*)\b')
PODST_NASAD = _R(r'\b(?:nasadeni\w*|nasadzovani\w*|deploy|deployment|release|rollout|vydani\w*)\b')
# smer „kam“ (na produkciu, do ostrej) — nie miesto „kde“ („stav na produkcii“): pri všeobecnom slovese len smer
CIEL_SMER = _R(r'\bna\s+(?:ostru|produkciu|prod|production|live|zivu|zivo)\b|\bdo\s+(?:ostrej|produkcie|prod|production'
               r'|prevadzky|zivej)\b|\bnaostro\b')
IMPERATIV = _R(r'\b(?:nasad|nasadte|nasadzuj|nasadzujte|deployni|deploynite|deployuj|deployujte|releasni|releasnite'
               r'|releasuj|releasujte)\b')
# rozkaz so všeobecným slovesom — len s cieľom ostrá (daj to na produkciu) alebo s podstatným menom nasadenia
IMP_CIEL = _R(r'\b(?:pusti|pustite|daj|dajte|hod|hodte|posun|posunte|dostan|dostante)\b(?!\s+(?:mi|nam|vediet)\b)')
# sloveso hneď s podstatným menom nasadenia (spusti nasadenie, urob deploy) — nie „rob to … pred každou iteráciou
# nasadzovania vyhodnoť“ (skutočná veta PS z prehrávky 29. 9., pokyn na inú prácu)
IMP_PODST = _R(r'\b(?:spusti|spustite|urob|urobte|sprav|spravte|rob|robte|dokonci|dokoncite)\s+(?:(?:to|ten|tu|nove|novy|nova'
               r'|novu|ostre|ostry|ostru|cele|celu)\s+)?(?:nasadeni\w*|deploy\w*|release|rollout|vydani\w*)\b'
               r'|\bpokracuj(?:te)?\s+(?:v|s|so|na)\s+(?:nasadzovani\w*|nasadeni\w*|deploy\w*)\b')
IMP_VYDAJ = _R(r'\b(?:vydaj|vydajte)\b')
VON = _R(r'\b(?:von|ven)\b')
INFINITIV = _R(r'\b(?:nasadit|nasadzovat|deploynut|deployovat|releasnut)\b')
INF_CIEL = _R(r'\b(?:pustit|dat|hodit|posunut|spustit|urobit|spravit|vydat)\b')
MODAL = _R(r'\b(?:mozes|mozte|mozete|smies|smiete|mas|mate|pokojne|klidne|kludne)\b')
SCHVAL = _R(r'\b(?:schvalujem|suhlasim|povolujem|odsuhlasujem|dovolujem|potvrdzujem)\b'
            r'|\b(?:mas|mate)\s+(?:(?:moju|moje|moj)\s+)?(?:zelenu|ok|suhlas|povolenie|schvalenie)\b')
PREDPONA = _R(r'^(?:(?:ano|jo|hej|ok|okej|oki|okay|dobre|jasne|fajn|super|vyborne|suhlasim|tak|teda|takze|prosim|please'
              r'|yes|yep|sure|pokojne|klidne|kludne|len|iba|hned|teraz|ihned|uz|aj|tiez|a|go|beriem|potvrdzujem'
              r'|schvalujem|v poriadku|go ahead|go ahead and)\b[\s,;:!—–-]*)*')
ANGL = _R(r'^(?:deploy|ship|release)(?:\s+(?:it|this|that|now|everything|all|the|to)\b|\s*[!.]?$)')
PODMIENKA = _R(r'\b(?:ak|akby|keby|ked|ci|pokial|pokym|akonahle|lenco|len co|hned ako|potom ako|po tom ako|po tom co'
               r'|v pripade|az|if|when|once|unless|until|after|whether|as soon as|provided)\b')
ODKLAD_VETA = _R(r'\b(?:potom|po|then|later|neskor\w*|nabuduce|chvil\w*|onedlho|coskoro|soon)\b')
ODKLAD_SPRAVA = _R(r'\b(?:zajtra|pozajtra|vecer\w*|rano|rana|poobede|popoludni\w*|napoludnie|noc|cez noc|vikend\w*|pondel\w*'
                   r'|utor\w*|stred[uy]|stvrt\w*|piat\w*|sobot\w*|nedel[ua]|buduc\w*|polnoc\w*|hodin\w*|tomorrow|tonight'
                   r'|monday|tuesday|wednesday|thursday|friday|saturday|sunday|next|weekend|koncom|zaciatkom|do konca)\b'
                   r'|\bo\s+\d|\b\d{1,2}[:.]\d{2}\b|\b\d{1,2}\.\s?\d{1,2}\.')
MINULOST = _R(r'\b(?:nasadil\w*|nasaden[aeyo]|nasadeny\w*|nasadzoval\w*|deploynul\w*|deployoval\w*|releasol\w*|vydal\w*'
              r'|pustil\w*|spustil\w*|deployed|released|shipped|failed|succeeded|done|finished|completed|is|was|were|has'
              r'|have|had|will|would|scheduled|planned|running|started|pending|padol|padlo|zlyhal\w*|bezi|prebieha)\b')
CUDZIA_REC = _R(r'\b(?:povedal\w*|napisal\w*|pisal\w*|hovori\w*|tvrdi\w*|ziada\w*|chce\w*|chcel\w*|navrh\w*|pyta\w*'
                r'|said|says|wrote|asked|wants)\b')
# neurčitok na začiatku vety platí len bez iného konateľa („Nasadiť novú verziu musí Juraj.“)
INY_KONATEL = _R(r'\b(?:musi|musia|ma|maju|moze|mozu|smie|treba|bude|budu|by|robi|urobi|spravi|zvladne|zariadi|riesi|je|su'
                 r'|bol\w*|potrebuje|vie|chysta|planuje)\b')
# neskoršia správa, ktorá pokyn ruší: zápor, odklad alebo zrušenie („Nie.“, „Ešte nie.“, „Zruš to, nechaj to tak.“)
ZRUS = _R(r'\b(?:zrus\w*|nechaj\w*|nechajte|odloz\w*|storno\w*|stornuj\w*|zadrz\w*|brzd\w*|cancel\w*|abort\w*|revert\w*'
          r'|stoj\w*|stop\w*|pockaj\w*|zastav\w*|nerob\w*|nenasad\w*|nedeploy\w*|nepust\w*|nespust\w*|zatial|este nie'
          r'|nie|nee|no way|hold|wait|later|neskor\w*|zajtra|potom|najprv|najskor'
          # 27. 10. (kus JZ0-kotva, čeština): odvolanie sa rozpoznáva podľa významu, nielen slovenských slov. České „ne“ (samo, nie
          # „nech/nenasazuj“, tie chytá ZAPOR_SIROKO), „zatím“, „pozdrž/pozastav“, „radši“. Rozšírenie je na bezpečnú stranu:
          # falošné odvolanie = partner sa opýta znova, nezachytené = starý pokyn platí.
          r'|ne|nene|zatim|pozdrz\w*|pozastav\w*|pridrz\w*|radsi|odlozit|zrusit)\b')

def norm(s):
    return re.sub(r'\s+', ' ', (s or '').strip().strip('"\'„“”').lower())


def cas(ts):
    return datetime.datetime.fromisoformat(ts.replace('Z', '+00:00')).timestamp()


def predvoleny_audit():
    """Rovnaké pravidlo ako kotva: audit PS, keď je zapisovateľný, inak vlastný v domove."""
    if os.access(AUDIT_PS, os.W_OK) or (not os.path.exists(AUDIT_PS) and os.access(os.path.dirname(AUDIT_PS), os.W_OK)):
        return AUDIT_PS
    return os.path.join(os.environ.get('HOME', ''), '.aigrip-ng', 'kotva-audit.jsonl')


# B15 (2. 10.): jeden určovateľ „kto hovorí“ (ng-hovoriaci.py) — len VEDĽA tohto skriptu (aigrip .aigrip/scripts a jeho
# zrkadlo /opt/aigrip-ng/scripts, kam ide celý priečinok naraz); balík háčikov v projektoch ho nenesie, tam je overenie
# presne ako doteraz. Žiadna premenná prostredia ani iné miesto. Chyba alebo iná zmluva = overenie ako doteraz.
try:
    import importlib.util as _ilu
    _s = _ilu.spec_from_file_location('ng_hovoriaci_kotva', os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ng-hovoriaci.py'))
    _HV = _ilu.module_from_spec(_s)
    _s.loader.exec_module(_HV)
    _HV = _HV if getattr(_HV, 'ZNACKA', '') == 'hovoriaci 1' and hasattr(_HV, 'kotva_text') else None
except BaseException:
    _HV = None


def _text(o, c, forma):
    """B15: forma(c) — celá správa presne ako doteraz; s určovateľom navyše .kladny = text len na kladnú zhodu (vložený text
    zvonku \\x00, správa nie od človeka '\\x00'; ng-hovoriaci.kotva_text). Chýbajúci určovateľ = ako pred B15; jeho CHYBA
    ide von a overenie skončí na „nie“ (1. 11. 2026)."""
    if _HV is not None:
        # 1. 11. 2026: tu bol široký except s `pass` — a bol to presne ten druh ticha, pre ktorý sa zábrana TVÁRI,
        # že stráži. Keď určovateľ spadne (napríklad na chýbajúci import v ňom), pôvodné ticho vrátilo celú správu
        # bez .kladny, takže sprísnenie (vložený text zvonku, správa nie od človeka) zmizlo a overenie bolo
        # MIERNEJŠIE než s určovateľom. Chyba preto ide von a dopadne na koncovú poistku tohto súboru: „nie“ —
        # tak, ako hovorí jeho vlastná zmluva („Akákoľvek chyba = nie“). Chýbajúci určovateľ (_HV is None) je iná
        # vec: tam pravidlo mlčí ako pred B15 a správa sa posúdi po starom.
        return _HV.kotva_text(o, c, forma)
    return forma(c)


# NVY (12. 10.): skúšku stroja v živom sedení pozná jediný určovateľ aktéra ng-akter.py (skuska_v_sedeni — správa, ktorá sa
# končí žiadosťou zopakovať značku, aj napísaná, kým partner pracoval); tu sa jej tvar neopakuje. Berie sa kópia, ktorú už načítal
# ng-hovoriaci.py, inak súbor VEDĽA tohto skriptu, inak spoločné miesto stroja /opt/aigrip-ng/scripts (balík háčikov
# v projektoch ng-akter.py nenesie; spoločné miesto patrí PS a kolega doň nezapíše). Určovateľ tu vie potvrdenie len
# ODMIETNUŤ, nikdy pustiť. Keď sa nenájde nikde (stroj bez spoločného miesta), pravidlo mlčí — overenie ako pred 12. 10.
_AK = getattr(_HV, 'AK', None) if _HV is not None else None
if not hasattr(_AK, 'skuska_v_sedeni'):
    _AK = None
    for _p in (os.path.dirname(os.path.abspath(__file__)), '/opt/aigrip-ng/scripts'):
        try:
            import importlib.util as _ilu2
            _s2 = _ilu2.spec_from_file_location('ng_akter_kotva', os.path.join(_p, 'ng-akter.py'))
            _m2 = _ilu2.module_from_spec(_s2)
            _s2.loader.exec_module(_m2)
            if hasattr(_m2, 'skuska_v_sedeni'):
                _AK = _m2
                break
        except BaseException:
            continue


def _je_skuska(o):
    """Riadok stopy je skúšobná správa stroja napísaná do živého sedenia (ng-akter). Chýbajúci určovateľ = nie
    (pravidlo mlčí ako pred 12. 10.); jeho CHYBA ide von a overenie skončí na „nie“ (1. 11. 2026)."""
    if _AK is None:
        return False
    # 1. 11. 2026: tu bol široký except s `return False`, čo znamenalo „nie je to skúška stroja“ — pravidlo NVY
    # o tej správe nevedelo a „ok“ od testu mohlo prejsť ako slovo človeka. Stráž, ktorá sa nemá na čom
    # rozhodnúť, musí zastaviť: chyba ide von a koncová poistka súboru dá „nie“.
    return bool(_AK.skuska_v_sedeni(o))


def pri_skuske(t, skusky, okno=None):
    """Čas skúšobnej správy stroja, ktorá je najviac `okno` sekúnd pred správou z času t alebo po nej; inak None (NVY).
    Okno 0 pravidlo vypína."""
    okno = OKNO_SKUSKA_S if okno is None else okno
    if okno <= 0:
        return None
    blizke = [s for s in skusky if abs(t - s) <= okno]
    return max(blizke) if blizke else None


IDE_BLOK = ('<ide_opened_file>', '<ide_selection>')
IDE_RE = re.compile(r'^(?:\s*<(ide_opened_file|ide_selection)>.*?</\1>)+\s*', re.S)


def spravy_cloveka(stopa):
    return spravy_a_skusky(stopa)[0]


def skusky_stroja(stopa):
    """Časy skúšobných správ stroja v stope sedenia (NVY) — číta ich aj meranie ng-kotva-overenie.py."""
    return spravy_a_skusky(stopa)[1]


def _vo_fronte(o):
    """KQM (13. 10.): riadok stopy je správa, ktorú človek napísal, kým partner pracoval (príloha queued_command). Či je
    v prílohe hlas človeka, určuje len ng-akter.py (clovek_vo_fronte: režim „prompt“, pôvod „human“, nie skúška stroja,
    nie podagent); kde určovateľ nie je, príloha sa neberie (ako pred 13. 10.). Jeho chyba ide von (1. 11. 2026)."""
    if _AK is None or o.get('type') != 'attachment':
        return False
    # 1. 11. 2026: bez širokého except — chyba určovateľa dopadne na koncovú poistku súboru („nie“). Ticho tu síce
    # viedlo na bezpečnú stranu (príloha sa nebrala a stráž zastavila), ale pohltilo by aj chybu v ňom.
    return bool(_AK.clovek_vo_fronte(o))


def spravy_a_skusky(stopa):
    """Jedno čítanie stopy → (správy človeka [(čas, text)] ako doteraz, časy skúšobných správ stroja).
    KQM (13. 10.): aj správa napísaná počas práce partnera — Claude Code ju nezapíše ako správu používateľa, ale ako prílohu
    queued_command (za 14 dní do 13. 10. 325 takých správ, 3× človek odpovedal na zastavenie a kotva odpoveď nevidela:
    am 1. 10. 11:18, account-cp 8. 10. 11:31, Erika 12. 10. 11:32 — všetky 1–3 h PO zastavení, takže ako bežná správa by
    platili). Čas prílohy je čas, keď ju človek odoslal (304 z 325 má čas starší než riadok stopy pred ňou — zapíše sa až
    pri doručení), takže pravidlo 4.27 „až po zastavení“ platí aj pre ňu bez zmeny: napísaná pred zastavením neplatí."""
    von, skusky = [], []
    with io.open(stopa, encoding='utf-8', errors='replace') as f:
        for l in f:
            if '"user"' not in l and 'queued_command' not in l:
                continue
            # 1. 11.: zúžené na pokazený JSON (nedopísaný riadok stopy). Vynechaný riadok je bezpečná strana
            # (stráž potom nemá čím potvrdiť), ale chyba v kóde sa sem schovať nesmie.
            try:
                o = json.loads(l)
            except ValueError:
                continue
            if not isinstance(o, dict):
                continue                     # riadok nie je objekt: nie je to správa, nie je čím potvrdiť
            if _je_skuska(o):
                # 1. 11.: tu bol široký except s `pass`. Skúšobnú správu stroja sme ROZPOZNALI a nedal sa prečítať
                # len jej čas — ticho znamenalo, že pravidlo NVY o nej nevie, a „ok“ od testu by prešlo ako slovo
                # človeka. Chyba preto ide von: stráž bez údaja zastaví (koncová poistka dá „nie“), nepustí.
                skusky.append(cas(o['timestamp']))
            if _vo_fronte(o):
                c = (o.get('attachment') or {}).get('prompt')
            else:
                if o.get('type') != 'user' or o.get('isSidechain') or o.get('isMeta') or o.get('isCompactSummary') \
                        or o.get('isVisibleInTranscriptOnly') or 'toolUseResult' in o or o.get('sourceToolUseID'):
                    continue
                if o.get('promptSource') == 'system':
                    continue
                orig = o.get('origin')
                if isinstance(orig, dict) and orig.get('kind') not in (None, 'human'):
                    continue
                c = (o.get('message') or {}).get('content')
            if isinstance(c, list):
                if any(isinstance(b, dict) and b.get('type') == 'tool_result' for b in c):
                    continue
                # 1. 10.: kontext z VS Code (otvorený súbor, výber) je samostatný blok pred textom človeka — správa sa preto
                # celá vynechávala (začínala „<“) a odpoveď človeka v nej strážca nevidel; text editora nie sú slová človeka
                c = '\n'.join(b.get('text', '') for b in c if isinstance(b, dict) and b.get('type') == 'text'
                              and not b.get('text', '').lstrip().startswith(IDE_BLOK))
            if isinstance(c, str):
                c = IDE_RE.sub('', c)
            if not isinstance(c, str) or not c.strip() or c.lstrip().startswith('<'):
                continue
            if c.lstrip().startswith(NIE_CLOVEK):
                continue
            # 1. 11.: zúžené na nečitateľný čas — taká správa sa nedá zaradiť „po zastavení“, preto sa nepočíta
            # (bezpečná strana: stráž potom nemá čím potvrdiť). Chyba v kóde sa sem schovať nesmie.
            try:
                t = cas(o['timestamp'])
            except (KeyError, ValueError, TypeError):
                continue
            von.append((t, _text(o, c, norm)))   # B15: obsah ako doteraz, .kladny na kladnú zhodu (_text)
            # 1. 10. (Erika, Google Ads): koniec riadku je hranica vety — „ano zapnime aj google“ na prvom riadku a pod tým
            # ďalšia prosba; norm() riadky zlial a prvý riadok strážca neuznal ako celú vetu. Druhá podoba správy nesie
            # konce riadkov ako ¶ (hranica vety); prvá ostáva, keby partner citoval celú správu na jednom riadku
            if '\n' in c.strip():
                von.append((t, _text(o, c, lambda x: norm(re.sub(r'[ \t]*\n\s*', ' ¶ ', x.strip())))))
    return von, skusky


def zhoda(v, txt):
    """Veta v je celá správa txt, alebo jej úsek ohraničený začiatkom/koncom alebo interpunkciou (nie kus slova či vety)."""
    if not v:
        return False
    if hasattr(txt, 'kladny') and ('\x00' in v or not zhoda(v, txt.kladny)):   # B15: aj vo vlastných slovách človeka
        return False
    if v == txt:
        return True
    k = txt.find(v)
    while k >= 0:
        pred = txt[:k].rstrip()
        po = txt[k + len(v):].lstrip()
        if (not pred or pred[-1] in HRANICA or v[0] in HRANICA) and (not po or po[0] in HRANICA or v[-1] in HRANICA):
            return True
        k = txt.find(v, k + 1)
    return False


def cela_veta(v, txt):
    """Úseky správy txt, kde je veta v celou vetou (ohraničená začiatkom/koncom správy alebo koncom vety .!?…;) →
    zoznam textov, ktoré v správe za ňou nasledujú (prázdny = nie je celou vetou)."""
    if hasattr(txt, 'kladny') and (not v or '\x00' in v or not cela_veta(v, txt.kladny)):   # B15 (ako zhoda)
        return []
    von = []
    k = txt.find(v) if v else -1
    while k >= 0:
        pred = txt[:k].rstrip()
        po = txt[k + len(v):].lstrip()
        if (not pred or pred[-1] in KONIEC_VETY) and (not po or po[0] in KONIEC_VETY or v[-1] in KONIEC_VETY):
            von.append(po)
        k = txt.find(v, k + 1)
    return von


def bez_diakritiky(s):
    return ''.join(ch for ch in unicodedata.normalize('NFD', s or '') if unicodedata.category(ch) != 'Mn').lower()


# 29. 9. neskoro večer (tretie kolo, nález overenia: 12 viet so zápornom, otázkou či iným cieľom prešlo cez voľné tvary
# MODAL/SCHVAL/INFINITIV): výnimka je teraz ÚZKA. Platí len veta, ktorá sa ZAČÍNA rozkazom na nasadenie (po „áno, ok…“)
# a MENUJE ostrú prevádzku (na produkciu, na ostrú, do ostrej, naostro), alebo výslovné poverenie „nasadiť na ostrú môžeš
# sám“. V CELEJ správe nesmie byť žiadne slovo na ne- (nedá, nejde, nemôžeme …), nie, otázka, podmienka, odklad, iný cieľ
# (náhľad, demo, preprod …), minulosť ani cudzia reč. Jediná povolená „podmienka“ je poradie, ktoré PS 29. 9. menoval:
# „keď to (na)implementuješ / dokončíš / opravíš / otestuješ, nasaď to na produkciu“. Každá neskoršia správa so slovom
# na ne-, nie alebo zrušením pokyn zruší. Čo sem nepatrí, neprejde — partner sa opýta.
ZAPOR_SIROKO = re.compile(r'\bne[a-z]{2,}|\bnie\b|\bnikdy\b|\bzakaz\w*|\bno\b|\bnot\b|\bnever\b|\bdon t\b|\bdont\b')
SEKV = re.compile(r'\b(?:ked|az|potom ako|po tom ako|len co|hned ako)\s+(?:to\s+|ho\s+|ju\s+)?(?:na|do|s|z|u)?'
                  r'(?:implementujes|implementujete|dokoncis|dokoncite|spravis|spravite|opravis|opravite|dorobis|dorobite'
                  r'|otestujes|otestujete|pretestujes|budes\s+(?:mat\s+)?hotov\w*|bude\s+hotov\w*)\b[\s,;:—–-]*')
INY_CIEL_SIROKO = re.compile(r'\b(?:staging|stage|stg|preview|nahlad\w*|uat|sandbox|canary|test\w*|demo\w*|preprod\w*|pre-prod\w*'
                             r'|predprod\w*|dev\w*|develop\w*|qa|beta|lokal\w*|local\w*|vyvoj\w*|slot\w*|wiki|dokument\w*)\b')
ZACIATOK_POKYNU = re.compile(r'^(?:nasad|nasadte|deployni|deploynite|releasni|releasnite|pusti|pustite|daj|dajte|hod|hodte'
                             r'|posun|posunte)\b')
POVERENIE = re.compile(r'^nasadit\b.*\b(?:mozes|mozete|smies|smiete)\b.*\b(?:sam|sama|sami)\b'
                       r'|^(?:mozes|mozete|smies|smiete)\b.*\b(?:sam|sama|sami)\b.*\bnasadit\b')


# 28. 10.: české „zapomeň na to“ — rozšírenie je na bezpečnú stranu (odvolanie navyše = partner sa opýta znova)
ODVOLAJ = re.compile(r'\b(?:zabudn\w*|zapome\w*|ani\s+nahodou|rozmysl\w*|radsej\s+(?:nie|nic|to)|scratch|forget|never\s*mind|cancel'
                    r'|vrat\w*\s+to|odvol\w*|inak|prepac\w*)\b')


def tvar_pokynu(a):
    """Veta a (bez diakritiky, bez zdvorilej predpony a bez poradia „keď to dokončíš“) je rozkaz alebo poverenie nasadiť
    a menuje ostrú prevádzku (29. 9., tretie kolo)."""
    zac = SEKV.sub('', PREDPONA.sub('', a).lstrip(' ,;:!-')).lstrip(' ,;:!-')
    if not CIEL_SMER.search(zac):
        return False
    return bool(ZACIATOK_POKYNU.match(zac) or POVERENIE.match(zac))


def kladny_pokyn(v, txt):
    """Veta v zo správy txt je jednoznačný pokyn nasadiť do ostrej prevádzky (29. 9., tretie kolo — podmienky vyššie).
    Pochybnosť = False (partner sa opýta)."""
    if not v or '?' in txt:
        return False
    if not any(not po.startswith('?') for po in cela_veta(v, txt)):
        return False
    a = POZDRAV.sub(' ', bez_diakritiky(v))
    s = POZDRAV.sub(' ', bez_diakritiky(txt))
    s_bez = SEKV.sub(' ', s)
    if ZAPOR_SIROKO.search(s) or ZAPOR.search(txt):
        return False
    if (PODMIENKA.search(s_bez) or ODKLAD_VETA.search(s_bez) or ODKLAD_SPRAVA.search(s) or MINULOST.search(s)
            or CUDZIA_REC.search(s) or INY_CIEL_SIROKO.search(s)):
        return False
    return tvar_pokynu(a)


def odvolanie(txt):
    """Neskoršia správa človeka, ktorá pokyn ruší: slovo na ne-, nie, zrušenie, odklad kdekoľvek v nej (29. 9.). Aj správa
    o inom so záporom pokyn zruší — partner sa potom opýta znova (bezpečná strana)."""
    s = bez_diakritiky(txt)
    return bool(ZAPOR.search(txt) or ZRUS.search(s) or ZAPOR_SIROKO.search(s) or ODVOLAJ.search(s))


def nasadenie_zadane(v, spravy, teraz, okno=OKNO_OSTRA_S):
    """Skorší kladný pokyn človeka na nasadenie (4.35 a) → (čas, text) alebo None. spravy = [(čas, text)] v poradí stopy.
    Pokyn neplatí, keď ho človek potom odvolal. Merač (ng-kotva-overenie.py) ho môže volať, aby pustenie podľa
    výnimky nehlásil ako vážny nález."""
    v0, v = v, norm(v).rstrip()
    najdeny = None
    for t, txt in spravy:
        if t > teraz:
            continue
        if teraz - t <= okno and zhoda(v, txt) and kladny_pokyn(v, txt):
            najdeny = (t, txt)
        elif najdeny is not None and t >= najdeny[0] and odvolanie(txt):
            najdeny = None
    # B15: s určovateľom (text s .kladny) platí pokyn len vtedy, keď by platil aj po starom na celých správach — len
    # prísnejšie aj v stope, ktorá nie je zoradená podľa času
    if najdeny is not None and any(hasattr(x, 'kladny') for _, x in spravy) \
            and nasadenie_zadane(v0, [(t, str(x)) for t, x in spravy], teraz, okno) is None:
        return None
    return najdeny


def main():
    try:
        stopa, veta, kanal, pod = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
        audit = sys.argv[5] if len(sys.argv) > 5 and sys.argv[5] else predvoleny_audit()
    except Exception:
        print('nie\nkotva nedostala, čo overiť'); return
    if pod == '1':
        print('nie\npríkaz je z podagenta — podagent nepotvrdzuje nič; neobnoviteľnú akciu nechaj hlavnému oknu'); return
    v = norm(veta).rstrip()
    if len(v) < 2 or not stopa or not os.path.exists(stopa):
        print('nie\nstopu sedenia alebo vetu človeka nevidím'); return
    sid = os.path.basename(stopa)[:-6] if stopa.endswith('.jsonl') else os.path.basename(stopa)
    # audit: čítať musí ísť, inak sa „až po zastavení“ nedá overiť (KOTVA-4); riadok sa číta ako JSON, nie podľa bajtov
    # (červený test zapisoval „zastavené“ ako é a pravidlo „až po zastavení“ ostalo neoverené — KOTVA-6)
    try:
        with io.open(audit, encoding='utf-8', errors='replace') as f:
            riadky = f.read().splitlines()
    except Exception:
        print('nie\naudit kotvy sa nedá prečítať, a tak nevidno, kedy kotva zastavila — kotva zastavuje (fail-safe)'); return
    # 29. 9.: výnimka len pre samotné nasadenie — keď rozbor našiel v príkaze aj iný neobnoviteľný kanál (mail, push
    # --force, zmazanie repozitára …), kanál nesie iný text („… a zároveň …“) alebo príznak viac-kanalov a platí bežné
    # pravidlo (zastavenie a odpoveď po ňom); predtým `nasadenie; graph_mail.py send …  # potvrdené: (a)` poslal mail
    priznaky = sys.argv[6].split() if len(sys.argv) > 6 else []
    _stopa = []

    def citana():     # stopa sa číta raz a až vtedy, keď ju treba
        if not _stopa:
            _stopa.append(spravy_a_skusky(stopa))
        return _stopa[0]
    if kanal in KANAL_OSTRA and 'viac-kanalov' not in priznaky:
        spravy, skusky = citana()
        zadane = nasadenie_zadane(v, spravy, datetime.datetime.now().timestamp())
        # NVY: skoršia veta napísaná pri skúšobnej správe stroja výnimku neotvára — platí bežné pravidlo nižšie
        if zadane and pri_skuske(zadane[0], skusky) is None:
            print('ok\nčlovek v tomto sedení nasadenie sám zadal (4.35) — jeho skoršia veta platí'); return
    posl_zast = 0.0
    for l in riadky:
        if 'vysledok' not in l:
            continue
        # 1. 11.: zúžené na pokazený JSON. Nerozumený riadok auditu sa neráta za zastavenie, a bez zastavenia
        # stráž potvrdenie nepustí — bezpečná strana; chyba v kóde tu mlčať nesmie.
        try:
            d = json.loads(l)
        except ValueError:
            continue
        if d.get('vysledok') != 'zastavené' or d.get('kanal') != kanal:
            continue
        # zastavenie podagenta neodomyká potvrdenie hlavnému oknu: podagent má v audite stopu rodičovského sedenia,
        # takže by jeho zastavenie platilo za „otázku“ hlavného okna (nález nezávislého overenia 24. 9.)
        if str(d.get('podagent', 0)) in ('1', 'true', 'True'):
            continue
        # 29. 9. (vážny nález 13:58, fb52ba56): riadok bez stopy (staré zastavenia do 22. 9. — 196 pošty, 445 sudo) platil
        # ako zastavenie v KAŽDOM sedení, takže „až po zastavení“ neplatilo — stačila veta človeka do 30 min. Zastavenie
        # sa uzná len z tej istej stopy sedenia.
        if (d.get('stopa') or '') != sid[:40]:
            continue
        # 1. 11.: zúžené na nečitateľný čas zastavenia. Nezapočítané zastavenie znamená „kotva v tomto sedení
        # nezastavila“, teda stráž potvrdenie nepustí — bezpečná strana; chyba v kóde sa sem schovať nesmie.
        try:
            posl_zast = max(posl_zast, datetime.datetime.fromisoformat(d['ts']).timestamp())
        except (KeyError, ValueError, TypeError):
            continue
    if not posl_zast:
        print('nie\nkotva v tomto sedení tento kanál nezastavila — spusti príkaz bez dodatku, nech ho zastaví, a potom sa opýtaj človeka'); return
    teraz = datetime.datetime.now().timestamp()
    spravy, skusky = citana()
    pri = None
    for t, txt in reversed(spravy):
        if t > posl_zast and t - posl_zast <= ZAST_PLATI_S and teraz - t <= OKNO_S and zhoda(v, txt):
            if pri_skuske(t, skusky) is None:
                print('ok\nodpoveď človeka nájdená v tomto sedení'); return
            pri = t
    if pri is not None:
        # NVY (12. 10.): odpoveď je v stope, ale do sedenia v tom čase písala skúška stroja — jej „ok“ stopa od človeka neodlíši
        posl = max(skusky)
        print('nie\ndo tohto sedenia okolo tej odpovede písala skúška stroja (test, ktorý píše kanálom človeka; naposledy o '
              f'{datetime.datetime.fromtimestamp(posl):%H:%M}) a jej správy sa od slov človeka nedajú odlíšiť, preto táto odpoveď '
              'neplatí — povedz to človeku jednou vetou a opýtaj sa ho znova; prejde odpoveď, ktorú napíše po '
              f'{datetime.datetime.fromtimestamp(posl + OKNO_SKUSKA_S):%H:%M} (pol hodiny po poslednej skúšobnej správe), '
              'keď test do sedenia nepíše'); return
    if teraz - posl_zast > ZAST_PLATI_S:
        print('nie\nposledné zastavenie tohto kanála v sedení je staršie než 8 h — spusti príkaz bez dodatku, nech ho zastaví '
              'znova, a opýtaj sa človeka'); return
    if any(zhoda(v, txt) for _, txt in spravy):
        print('nie\ntieto slová človek povedal, ale nie ako odpoveď na toto zastavenie (skôr, alebo pred viac než 30 min) — opýtaj sa znova')
    else:
        print('nie\nvetu v dodatku človek v tomto sedení nenapísal (celú jeho vetu, nie jej kúsok) — opýtaj sa ho a do dodatku daj jeho slová doslova')


if __name__ == '__main__':
    try:
        main()
    except Exception as e:
        # 1. 11. 2026: dôvod sa menuje typom chyby a miestom — nie jej textom, ten môže nesť vetu človeka alebo
        # celý príkaz. Bez toho partner len dookola prosí človeka o to isté a nikto nevie, že stráž drží pokazený
        # pomocník (stráž je pritom len na strane zastavenia, takže nič neprejde).
        _t = e.__traceback__
        while _t is not None and _t.tb_next is not None:
            _t = _t.tb_next
        _kde = f' ({_t.tb_frame.f_code.co_name}, riadok {_t.tb_lineno})' if _t is not None else ''
        print(f'nie\noverenie zlyhalo — kotva zastavuje (fail-safe): {type(e).__name__}{_kde}; '
              'pomocník stráže nefunguje — povedz to správcovi platformy')
