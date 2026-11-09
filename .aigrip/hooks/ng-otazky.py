#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Otázky na človeka na konci ťahu — jediné miesto pravidla 4 „otázky úmerné veci“ (PS 30. 9., STAVBA 4.51 a). Verzia 6.

Pravidlo 4 (jadro.json): partner sa pýta len na to, čo rozhoduje len človek — verdikt, nevratná akcia, veľká vec;
ostatné rozhodne sám a zapíše. Od 4.51 sa rátajú len ZBYTOČNÉ otázky:
  A    ponuka alebo žiadosť o povolenie na vratnú prácu v projekte („Mám ich opraviť?“, „Chceš, aby som to doplnil?“,
       „Mám pokračovať krokom N?“) — LEN keď sloveso ponuky je na zozname vratnej práce (_VRATNE) a okolie otázky
       nenesie nič vonkajšie ani nevratné
  S    voľba tvaru výstupu (text do mailu / Word / PDF), keď nič neoznačuje rozhodnutie ani obsah, ktorý ide von
  rep  otázka, o ktorej partner sám píše, že ju opakuje („stále čakám“, „ostáva otázka z minulej správy“), v sedení
       ju už položil a človek sa jej medzitým nedotkol
Legitímne a nikdy sa neobmedzujú:
  B    veľká vec (odoslanie von, živý alebo cudzí systém, zmazanie, peniaze a zmluvy, nasadenie, merge, niekto mimo
       projektu, konkrétny človek, pravidlo pre firmu alebo spôsob práce, bezpečnosť, cudzí projekt alebo súbor;
       od verzie 5 aj výstup pre ľudí mimo projektu — stretnutie, prezentácia, klient, vedenie, verejný web, ponuka)
  C    verdikt človeka (voľba cesty, súhlas, obsah, formulácia; aj keď partner sám napíše, že rozhoduje človek;
       od verzie 5 aj tempo, ktoré si človek v tomto ťahu určil sám, a ponuka počas jeho kontroly textu)
  D    fakt, ktorý vie len človek, alebo spresnenie nejasného pokynu
  R    ponuka pri návrate, ktorú predpisuje pilotný text
  X    otázka pre tretiu stranu (citát, návrh mailu, dotazník, otázky na stretnutie)
  O    iná otázka — aj ponuka so slovesom mimo zoznamu („Mám to spustiť?“) a otázka v angličtine
  M    zmiešaný blok: zbytočná + legitímna časť — nezasahuje sa
ZÁSADA: pri pochybnosti je otázka legitímna. Verzia 1 mala predvolené „Mám + sloveso = ponuka“ a veľké veci chytala
zoznamom slov; nezávislá prehrávka 20. 8. – 30. 9. našla medzi 183 zásahmi 25 legitímnych otázok (6 veľkých vecí).
Verzia 2 to obracia: zbytočná je len ponuka, ktorej sloveso je na zozname vratnej práce (_VRATNE), a len keď
  · otázka, veta pred ňou a úvod zoznamu nenesú nič vonkajšie ani nevratné (aj podstatné meno deja, adresát v datíve,
    „pre X“, pomenovaný človek alebo firma, mail, odpoveď, telefonát, iný projekt, záznam starého druhu J/D/L-XXX,
    CLAUDE.md, pravidlo pre všetkých) ani výslovné rozhodnutie človeka („tvoje rozhodnutie“, „kým sa rozhodneš“,
    „Rozhodnutie 1 … platí (a)?“, „ZNENIE NA VERDIKT“), zmenu čísla „z X na Y“ či pomenovanú možnosť („cestu 3“);
  · celá aktuálna časť správy (po nadpis) nenesie výslovný krok von (zápis do databázy, nasadenie, zverejnenie, push,
    merge do main, živý web) ani výslovné rozhodnutie a nad otázkou nie je návrh listu či mailu (oslovenie, podpis);
  · pri odkazovacej otázke („Mám to takto urobiť?“, „Mám?“, „Mám tie texty prepísať?“) ani jej blízke okolie (odsek,
    blok, odsek nad ňou, nadpis) nenesie žiadny z uvedených znakov ani citované znenie.
Dve odložené vzorky (polovica sedení 20. 8. – 30. 9. a 1. 7. – 19. 8.) ukázali, že verzie pred týmito pravidlami by
zastavili aj legitímne otázky — overenie a čísla sú v test/ (vsetko.sh). Každá nová vzorka ukázala nový druh falošného
zásahu, preto verzia 3 pridáva ďalšie znaky „pri pochybnosti legitímna“ z vymyslených sond (nie ladené na stopách):
  · návrat k agende — prvý ťah sedenia alebo správa po prestávke ≥ NAVRAT_PAUZA_H hodín: ponuka je ponuka pri návrate (R);
  · cudzie služby menom (Asana, HubSpot, Mailchimp, DNS, e-shop…), doklad na platbu ako predmet („pripraviť faktúru“,
    „spraviť prevod“), záznam o konkrétnom človeku (dochádzka, mzda, CV, hodnotenie), zdieľané a celofiremné miesto,
    obsah pre verejnosť a úrad, výslovný príjemca vo vedľajšej vete („ktorý už klient dostal“), inštitúcia;
  · verdikt inými slovami („na tvojom posúdení“, „potrebujem tvoj názor“, „nechám na teba“, „Rozhodni: …?“, „mení to
    záver / hlavné číslo“) a návrat inými slovami („včera sme skončili“, „vitaj späť“).

Jednotka je KONCOVÝ BLOK správy partnera: posledný odsek končiaci otáznikom, s úvodom zoznamu, voľným zoznamom
a s odsekmi priamo nad ním, ktoré tiež nesú otázku alebo žiadosť o verdikt. Blok je zbytočný, len keď je zbytočná
KAŽDÁ otázka v ňom a nie je v ňom žiadosť o verdikt.

Knižnica bez vedľajších účinkov pri importe (háčik, ng-kdesom-koniec.sh aj meranie ju načítajú cez importlib):
  posudok(text, historia=None, navrat=False, clovek=None, skor=None) -> dict(trieda, zbytocna, zbytocna_bez_unik, unik,
                                        len_rep, vyslovne, ista, pochybnost, otazky, uryvok)
  texty_cloveka_tahu(udalosti, i)    -> správy človeka v ťahu (začiatok ťahu + správy napísané počas práce) — verzia 5
  texty_cloveka_skor(cesta) / texty_cloveka_skor_ud(udalosti, i) -> skoršie správy človeka v sedení (strážca) — verzia 5
  udalosti_z_riadkov(riadky)         -> udalosti sedenia (človek vrátane správ napísaných počas práce, text, nástroj, háčik)
  historia(udalosti, po=None)        -> skoršie koncové otázky sedenia a čo človek napísal po nich (pre „rep“)
  posudok_konca(vstup)               -> to isté rozhodnutie pre strážcu aj ng-kdesom-koniec.sh zo vstupu Stop háčika
  posudok_v_stope(udalosti, i)       -> posudok konca ťahu v meraní a skúškach (história, návrat k agende)
  zaciatok_tahu(cesta)               -> posledná správa človeka v stope (čítaním odzadu, aj za hranicou 2 MB) + návrat
  rozhodnutie_strazcu(vstup)         -> čo strážca s koncom ťahu urobí (akcia, ista, pochybnost) — BEZ zápisu
  sedenie(udalosti, od_ts, do_ts)    -> meranie jedného sedenia
  meraj(dni=7, ...) / pravidlo4(...) -> pravidlo 4 za okno: zbytočné otázky na sedenie s človekom, po ľuďoch
  riadok_j4(r, nazov, tlacidla)      -> riadok J-4 pre ng-klauzuly.py
  pokyn_od() / riadok_pokyn(r, mena)  -> odkedy je spresnený pokyn (4.94 e) v projektoch a koľko zbytočných otázok
                                        prišlo po žiadosti o vysvetlenie (4.94 d) — jedno znenie pre stav aj server
  python3 ng-otazky.py < text        -> posudok jednej správy (skúška)
  python3 ng-otazky.py meraj [dni]   -> meranie za posledných N dní (predvolene 7)
Overenie: test/vsetko.sh (153 označených otázok, odložená vzorka 20. 8. – 30. 9., syntetické sady revízie, háčik).
Verzia 4 (30. 9., po dvoch revíziách verzie 3): opravy falošných zásahov F1–F7 (návrat v odseku nad otázkou a ďalšie
tvary návratu, opakovanie pri návrate a „tvoje slovo“, odklad a opytovacie slovo ako dotknutie sa otázky, písmeno
možnosti ako označenie, spresnenie za pomlčkou a v odseku nad otázkou, tabuľka medzi otázkou a ponukou), kontext VS Code
pred správou človeka (_text_cloveka), meranie prežije zmiznutý súbor stopy, úniková veta v úvodzovkách alebo v citáte
sa neráta, „learning/decision“ ako záznam starého druhu (B) a jedno rozhodnutie strážcu pre háčik aj ng-kdesom-koniec.
Verzia 5 (2. 10., väčšinový posudok 18 prípadov zo 7 dní: 14 zbytočných, 4 oprávnené alebo sporné — človek si sám určil
tempo po jednom obrázku pre stretnutie mimo projektu, rozbor pre klienta s nepotvrdeným rozsahom, odkaz do textu pre
verejný web, ktorý človek práve kontroloval; syntetika recenzenta: 48 zo 143 oprávnených by strážca zastavil):
  · nové znaky legitímnej otázky pre meranie aj strážcu — tempo človeka (_tempo_cloveka: v správach človeka v tomto ťahu
    zadal jeden kus z viacerých „priprav prvý obrázok“, obmedzil prácu „zatiaľ len“, „zatiaľ nemeň súbor“, ohlásil vlastnú
    kontrolu „najprv skontrolujem“, „keď to schválim“), ponuka počas kontroly človeka („…, kým kontroluješ texty?“),
    výstup pre ľudí mimo projektu (_B_VON5), ďalšie cudzie systémy a záznamy (_B_SYSTEM5, _B_SYSTEM5_MENO) a obsahový
    verdikt v tvare ponuky (_C_OBSAH5: zmena odhadu, záveru, cieľa, rozsahu; „namiesto“, „hoci“, „aby vyzeral …“);
  · DVE ÚROVNE: meranie (J-4) ráta každú zbytočnú otázku (zbytocna), strážca zapíše NÁLEZ len pri ISTEJ (ista,
    _istota). Pri pochybnosti zapíše jej druh (pole „pochybnost“) a nálezom to nie je; meranie otázku ráta ďalej.
    Čísla a prehrávka sú v test/ (vsetko.sh, t_v5.py).
  · Oprava po overení 2. 10. večer (tri nezávislé overenia a nové sondy): strážca bral každú neznámu formuláciu človeka
    ako neutrálnu („poďme pomaly – prvý krok a stop“, „teraz to čítam, nechaj to tak“), zápor pri „naraz“ či „a ďalej“
    obracal zmysel na povolenie a nevidel tempo ani publikum zo skoršieho ťahu. Istá je preto len otázka s KLADNÝM
    DÔKAZOM na všetkých stranách: ponúka sa ROZBOR (len čítanie, končí sa voľbou človeka — vzor „ďalší krok“), správa
    človeka v ťahu je ČISTO NEUTRÁLNA (_neutralna_sprava: súhlas, voľba, pokyn na vratnú prácu, vec v projekte), v jeho
    skorších správach v sedení nie je výslovné čakanie či dávkovanie (_SKOR_CAKAJ) a správa partnera nespomína nikoho
    mimo projektu ani podľa role („pre daňového poradcu“, _pre_rola). Ponuku úpravy, tvorby či ďalšieho kusu strážca
    nevracia nikdy. Meranie sa mení len opravou záporu v _tempo_cloveka (_pokracuj_povolene).
  · Oprava 3. 10. (odolnosť podľa overenia zmluvy 2. 10.): text človeka sa číta najviac v rozsahu CLOVEK_ZNAKOV
    (začiatok a koniec) a raz za posudok, nie pri každej ponuke (dlhšiu správu strážca za neutrálnu nepovažuje); strážca
    reže koniec správy partnera ako koncovy_blok() — bez rozbitého bloku kódu (_koniec_spravy); k ťahu patria všetky
    správy človeka napísané počas práce, nielen posledná (texty_cloveka_tahu, zaciatok_tahu()['fronta_texty']).
Verzia 6 (4. 10., kus HM — jediný falošný zásah verzie 5 v odloženej vzorke 1. 7. – 30. 9.: „Mám body 1 a 3
opraviť?“ pod celým znením zadania, ktoré malo odísť klientovi ako príloha mailu, a štyrmi očíslovanými nálezmi k nemu;
bod 3 menil podmienku, ktorú klient mal od 16. 9.): ponuka zapracovať VYBRANÉ očíslované nálezy nad znením je verdikt
(C:vyber-nalezov-nad-znenim, _vyber_nad_znenim) — partner by sám rozhodol, čo sa v texte človeka zmení a čo ostane.
Platí, keď najbližší očíslovaný zoznam nad otázkou má aspoň dve čísla, otázka menuje len niektoré z nich („body
1 a 3“, „bod 2“, „opraviť 1 a 3“; rozsah „1–4“ sa rozvinie) a časť nesie citované znenie alebo slovo „znenie / text
mailu, zadania …“. Ponuka zapracovať všetky nálezy ostáva ponukou (A). Strážca sa nemení (tento koniec nevracal ani
verzia 5 — nie je ponuka rozboru); mení sa meranie (J-4 ho už neráta). Zmluva s háčikmi (API_STRAZCU) sa nemení.
Verzia 7 (10. 10., PS STAVBA 4.87 d, AIG-R261010-55cec9): ZASTAVOVANIE STRÁŽCU SA RUŠÍ. Strážca už ťah nikdy nevráti —
len zapíše riadok do stopy háčikov; režim, jeho prepínač v manifeste (kľúč otazky_straz) ani kritérium bezpečnosti
neexistujú. Pravidlá triedenia a meranie pravidla 4 sa nemenia (ZNACKA ostáva). Zmluva s háčikmi sa mení
(API_STRAZCU 1 → 2): rozhodnutie_strazcu() nemá parameter režimu, vo výsledku nie je „blok“ ani „by_zastavil“ (namiesto
neho „ista“ = nález strážcu) a strazca_zastavi() ani rezim_strazcu() už nie sú.
Oprava 10. 10. (kus P4A): „to/ich/ho/ju tak + neurčitok“ je odkazovacia otázka (_TAKTO) — „Mám to tak upraviť
v podrobnom pláne aj v súhrne?“ pod zoznamom ôsmich zmien plánu kampane (EP 8. 10.) mala 10 slov a „tak“ nie je
„takto“, takže okolie sa nečítalo a verdikt k obsahu sa rátal ako zbytočný. Oprava len pridáva čítanie okolia: zo
všetkých 1 477 koncov s otázkou od 1. 6. mení dva, oba k legitímnej otázke (test/t_v6.py (3)); strážca sa nemení.
"""
import functools
import re
import sys
import unicodedata

ZNACKA = 'otazky 6'
# Zmluva s háčikom strážcu: rozhodnutie_strazcu() / stav_strazcu(). Háčik kontroluje TOTO číslo, nie ZNACKA — nová
# verzia pravidiel (ZNACKA) tak nevypne súhru ticho; zvýšiť len pri zmene zmluvy. 2 od 10. 10. 2026: zastavovanie
# zrušené (STAVBA 4.87 d) — parameter režimu, „blok“, „by_zastavil“, strazca_zastavi() a rezim_strazcu() zmizli.
API_STRAZCU = 2
LEGIT = ('B', 'R', 'X', 'D', 'C', 'O')
ZBYTOCNE = ('A', 'S', 'rep')
PRAH_PODOBNOSTI = 0.6          # prekryv kmeňov otázky so skoršou otázkou, od ktorého je to opakovanie
CAST_ODSEKOV = 12              # najviac odsekov nad koncovým blokom v „časti“ (inak po nadpis) pri odkazovacej otázke
CAST_ZNAKOV = 6000             # a najviac znakov (rýchlosť)
ROZSIRENIE_ODSEKOV = 8         # najviac odsekov, o ktoré sa blok rozšíri nahor (otázky a žiadosti o verdikt priamo nad ním)
MAX_ZNAKOV = 30000             # posudzuje sa len koniec správy (správa s 1 MB textu by háčik zdržala sekundy)
UNIKOV_NA_SEDENIE = 2          # toľkokrát za sedenie platí úniková veta; potom sa otázka ráta znova ako zbytočná
MAX_OTAZOK = 25                # blok s viac otázkami je dávka (iné porušenie pravidla 4), nie ponuka — legitímny, bez rozboru


# ---------------------------------------------------------------------------------------------- normalizácia textu
def bez_diakritiky(s):
    return ''.join(c for c in unicodedata.normalize('NFKD', s or '') if not unicodedata.combining(c))


def norm(s):
    """Malé písmená bez diakritiky, jednotné úvodzovky a pomlčky — regulárne výrazy sa píšu bez diakritiky."""
    s = bez_diakritiky(s).lower()
    s = re.sub(r'[„“”«»‚‘’]', '"', s)
    return s.replace('–', '-').replace('—', '-')


def cisty(text):
    """Text bez kódu, odkazov a formátovania. Kód v riadku sa ponecháva (nesie slová ako „stash“), ale bez otáznika."""
    t = text or ''
    t = re.sub(r'```.*?```', ' ', t, flags=re.S)
    t = re.sub(r'`([^`\n]*)`', lambda m: ' ' + m.group(1).replace('?', ' ') + ' ', t)
    t = re.sub(r'!\[[^\]]*\]\([^)]*\)', ' ', t)
    t = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', t)
    t = re.sub(r'<[^>\n]{1,80}>', ' ', t)
    t = t.replace('**', '').replace('__', '')
    t = re.sub(r'(?<![\w*])\*(?!\s)([^*\n]{1,200}?)(?<!\s)\*(?![\w*])', r'\1', t)
    t = re.sub(r'(?<![\w_])_(?!\s)([^_\n]{1,200}?)(?<!\s)_(?![\w_])', r'\1', t)
    return re.sub(r'[ \t]{2,}', ' ', t)


_ZOZNAM = re.compile(r'^(\s*)([-*•·]|\d{1,2}[.)]|[a-hA-H][.)])\s+')
_KONIEC_OTAZKY = re.compile(r'\?[*_)\]\s]*$')
_ODDELOVAC = re.compile(r'^\s*(-{3,}|\*{3,}|_{3,})\s*$')


def _je_tabulka(r):
    return r.lstrip().startswith('|')


def _je_citat(r):
    return r.lstrip().startswith('>')


def konci_otazkou(text):
    """Posledný neprázdny riadok správy (mimo kódu) končí otáznikom — tá istá jednotka, akú rátal J-4 v ng-klauzuly.py."""
    t = text or ''
    if len(t) > MAX_ZNAKOV:                             # stačí koniec správy
        t = t[-MAX_ZNAKOV:]
        if t.count('```') % 2:                          # rez padol do bloku kódu — jeho začiatok je mimo výrezu
            t = '```' + t
    t = re.sub(r'```.*?```', ' ', t, flags=re.S)
    riadky = [r for r in t.split('\n') if r.strip()]
    return bool(riadky) and bool(_KONIEC_OTAZKY.search(riadky[-1].rstrip()))


# ---------------------------------------------------------------------------------------------- koncový blok a otázky
_SKRATKY = re.compile(r'\b(Dr|Ing|Mgr|Bc|napr|tzn|resp|príp|atď|tj|č|čl|ods|str|kap|obr|tab|mil|mld|tis|cca|max|min|'
                      r'hod|sv|vs|pozn|tel|zák|p|ul|ml|st|ut|št|pi|so|ne|po)\.(?=\s)', re.I)
_BODKA = '․'


def _vety(s):
    """Vety úseku — bodka za skratkou alebo v dátume („30. 9.“) vetu nekončí."""
    t = _SKRATKY.sub(lambda m: m.group(0)[:-1] + _BODKA, s)
    t = re.sub(r'(\d)\.(?=\s*\d)', '\\1' + _BODKA, t)
    t = re.sub(r'(\d)\.(\s+)(?=[a-záäčďéíĺľňóôŕšťúýž])', '\\1' + _BODKA + '\\2', t)
    casti = re.split(r'(?<=[.!?…])["“”»)\]*_]*\s+(?=[„"*(\[«]*[A-ZÁÄČĎÉÍĽĹŇÓÔŔŠŤÚÝŽ0-9])', t)
    return [c.replace(_BODKA, '.').strip() for c in casti if c.strip()]


def _odseky(t):
    return [p for p in re.split(r'\n\s*\n', t) if p.strip() and not _ODDELOVAC.match(p)]


def _hlavicka(p):
    """Odsek, ktorý je len nadpisom („## Na rozhodnutie“, „Ako overím“ tučným) — hranica časti správy."""
    p = p.strip()
    if p.startswith('#'):
        return True
    return len(p) <= 90 and '\n' not in p and not p.endswith(('.', '?', '!', ':')) and len(p.split()) <= 10


# žiadosť o verdikt rozkazom („Pripomienkuj stratégiu.“) — legitímna časť bloku
_ROZKAZ_VERDIKT = re.compile(r'^(pripomienkuj|potvrd|rozhodni|vyber si|vyber|schval|odsuhlas|posud)\b')
# Partner sám píše, že vec rozhoduje človek → verdikt (C). Zámerne tu NIE JE úniková veta „O tomto rozhoduje človek,
# lebo …“ (tú ráta a obmedzuje strážca — unik()) ani bežné „čakám na tvoje slovo“ / „treba tvoje slovo“ (partneri to
# píšu pri každej otázke; v označených dátach stojí pri ponukách #102, #13).
_C_VYSLOVNE = re.compile(
    r'\b(tvoje rozhodnuti\w*|rozhodnutie pre (teba|vas)|rozhodnut (to |o tom )?(mas|musis) ty|'
    r'bez tvoj(ho|ej) (odklepnutia|odklepu|suhlasu|slova|ok|potvrdenia|rozhodnutia|schvalenia|vedomia)|'
    r'len s tvojim (ok|suhlasom|slovom|potvrdenim|odklepom)|(az )?po tvojom (odklepnuti|odklepe|suhlase)|'
    r'potrebujem (od teba )?(len )?(tvoj|tvoje|tvoju|jedno|jeden) (verdikt|suhlas|rozhodnutie|odklepnutie|odklep)|'
    r'(tvoj|tvojho|tvojim|tvojom) (verdikt|suhlas)\w*|verdikt\w*|znenie na verdikt|'
    r'(tvoje|tvojho|tvojom|tvojim) vyslovn\w+ (slov|suhlas|ano|potvrden|pokyn)\w*|vyslovn\w+ (suhlas|ano)|'
    r'rozhodnutie (je )?na teba|rozhodnutie je na tebe|(to je|je to) (prave )?(to |tvoje |tvoj )?rozhodnut\w*|'
    r'(je|ostava|nechavam|necham) (to )?na tebe|(kym|az|ked) sa (ne)?rozhodnes|'
    r'(ty )?mi (medzitym |potom |zatial |este )?(povies|napises|odpovies|potvrdis|das vediet|posles)|'
    # verzia 3 (sondy 30. 9.): to isté inými slovami — posúdenie, úsudok, názor, „nechám na teba“, obsahové rozhodnutie
    r'(je to |to je )?na tvojom (posudeni|uvazeni|rozhodnuti|vkuse)|'
    r'(vec|otazka|zalezitost) tvojho (usudku|posudenia|rozhodnutia|vkusu|nazoru)|'
    r'posudit (to )?(musis|mas|treba) ty|(musis|mas) (to )?posudit( ty)?|'
    r'potrebujem (od teba )?(tvoj |tvoje |tvoju )?(nazor|potvrdenie|usudok|posudenie|stanovisko)|'
    r'(nechavam|necham|nechavame) (to |tu vec |rozhodnutie |volbu )?na teba|bez teba (to )?(nechcem|nebudem|nerobim)|'
    r'(nechcem|nebudem) (to )?(robit|menit|urobit|spravit|rozhodovat|rozhodnut) bez teba|'
    r'(obsahov|strategick)\w* (rozhodnuti|otazk|vec|volb)\w*)\b')
# návrh, ktorý mení záver, odporúčanie alebo hlavné číslo („Mení to hlavné číslo, ktoré…“) — obsah → verdikt (verzia 3)
_C_MENI_OBSAH = re.compile(r'\bmen(i|ia|ilo|ili|ime)\s+(to\s+|sa\s+)?(\w+\s+){0,2}(zaver\w*|odporucani\w*|hlavn\w* cisl\w*|'
                           r'headline\w*|vysledn\w*|ocenen\w*|celkov\w* (hodnot|sum|cisl)\w*|rozpoct\w*)')
# číslované rozhodnutie alebo otázka v riadenom rozhodovaní („Rozhodnutie 1 … platí (a)?“, „Otázka 4 stojí: …“) — len
# pri samotnej otázke, vo vete pred ňou a v úvode zoznamu (nie „… ako otázku č. 5 v prehľade“ o odsek vyššie)
_C_CISLOVANE = re.compile(r'\brozhodnuti[ea] (c\. ?|cislo )?\d+|(^|[.:!?]\s+)otazka (c\. ?|cislo )?\d+\b|\botazka \d+ z \d+|'
                          r'\bplati \([a-z]')


# návrh zmeniť číslo („opraviť dolnú hranicu z 50 na 95 Kč/m²“) — obsah, nie úprava textu → verdikt
_C_ZMENA_CISLA = re.compile(r'\bz \d[\d ,.]*(\s*[a-z%]{1,5}\.?(/[a-z0-9²]+)?)?\s+na \d')
# ponuka zapracovať pomenovanú možnosť („zapracovať cestu 3“, „Odporúčam variant B. Mám ho pripraviť?“) — voľba medzi
# možnosťami patrí človeku
_C_CESTA = re.compile(r'\b(cest(a|u|y|ou|e)|variant(a|u|y|om|e)?|moznost(i|ou)?) ([a-h]|\d{1,2})\b'
                      # P4V (1. 11. 2026): možnosť pomenovaná NÁZVOM v úvodzovkách, nie písmenom či číslom — „Mám
                      # dlaždicu upraviť podľa variantu „Celá dlaždica v cenách AWS“?“ (aspecta-finops 30. 10.) pod
                      # dvoma variantmi s cenou a s odporúčaním. Pravidlo 5 od partnera žiada práve tento tvar, takže
                      # otázka, ktorý z nich urobiť, je voľba človeka (C) — nie ponuka vratnej práce (A).
                      r'|\b(cest\w*|variant\w*|moznost\w*|alternativ\w*) "[^"\n]{3,}"')
# P4H (1. 11. 2026): v tej istej otázke stojí vedľa ponuky aj žiadosť o verdikt o HODNOTE („Mám ju upraviť podľa
# bodov 1 až 4 a je hranica 50 USD pre „pokryté“ v poriadku?“, aspecta-finops 30. 10.). Jednotka je koncový blok a ten
# zbytočný NIE JE, keď je v ňom žiadosť o verdikt (zásada verzie 2) — tu ju nesie druhá polovica tej istej vety, takže
# ju delenie na vety nevidelo. Platí len vtedy, keď otázka sama nesie hodnotu: číslo alebo vec v úvodzovkách; holé
# „je to v poriadku?“ ostáva ponukou.
_C_HODNOTA_OK = re.compile(r'\b(je|su|sedi|plati|vyhovuje)\b[^?]{0,90}?\b(v poriadku|ok|spravn\w*|vhodn\w*)\b')
_C_HODNOTA_VEC = re.compile(r'\d|"[^"\n]{3,}"')
# citované znenie nad odkazovacou otázkou („Do statusu by som to prepísal: „…“ Mám to opraviť?“, citát v bloku „>“) —
# otázka sa pýta na znenie → verdikt
_C_ZNENIE = re.compile(r'(^|\n)\s*>|[:\-]\s*"[^"]{40,}"')
# VÝBER Z ČÍSLOVANÝCH NÁLEZOV NAD ZNENÍM (verzia 6; odložená vzorka 4. 10.: „Mám body 1 a 3 opraviť?“ pod celým znením
# zadania do prílohy mailu pre klienta a štyrmi očíslovanými nálezmi k nemu) — partner vyberá, ktoré nálezy do znenia
# zapracuje a ktoré nechá; čo sa v texte človeka zmení a čo ostane, je verdikt (C), hoci „opraviť“ je vratná práca.
# Odkaz na čísla: „body 1 a 3“, „bod 2“, „nálezy 1, 3 a 4“, „1. a 3.“ (aspoň dve čísla bez podstatného mena).
_VYBER_MENO = re.compile(r'\b(bod\w*|polozk\w*|nalez\w*|pripomienk\w*|vec\w*|chyb\w*|zmen\w*|odrazk\w*)\s+(c\.\s*)?'
                         r'(\d{1,2}\.?(\s*(,|a|aj|az|-|\+)\s*\d{1,2}\.?)*)(?!\s*(%|x|kc|eur|gb|tb|mb|dni|dna|hod|min|ks|mil))')
_VYBER_HOLE = re.compile(r'(?<![\w.,])(\d{1,2}\.?(\s*(,|a|aj)\s*\d{1,2}\.?)+)(?![\w,]|\.\d)')
_CISLO_ZOZNAMU = re.compile(r'^\s*(\d{1,2})[.)]\s+\S', re.M)
_ZNENIE_SLOVO = re.compile(r'\b(znen(ie|ia|i|im)|aktualn\w* text\w*|text\w* (mailu|zadania|zmluvy|ponuky|prilohy|listu))\b')


def _odsek_caka_na_cloveka(p):
    """Odsek nad koncovým blokom nesie otázku (riadok končí otáznikom, nie v citáte) alebo žiadosť o verdikt."""
    for r in p.split('\n'):
        if not r.strip() or _je_tabulka(r) or _je_citat(r):
            continue
        if _KONIEC_OTAZKY.search(r.rstrip()):
            return True
    if _C_VYSLOVNE.search(norm(p)):
        return True
    for r in p.split('\n'):
        s = _ZOZNAM.sub('', r).strip()
        for v in _vety(s):
            if _ROZKAZ_VERDIKT.match(norm(v).lstrip('*_ ')):
                return True
    return False


def koncovy_blok(text):
    """Koncový blok správy (dict) alebo None, keď správa nekončí otázkou.
    odseky  základ (posledný odsek s úvodom a voľným zoznamom) a nad ním odseky s otázkou alebo žiadosťou o verdikt
    zaklad  index prvého odseku základu v „odseky“
    cast    odseky aktuálnej časti správy nad blokom (po nadpis) — pre krátku odkazovaciu otázku („Mám to urobiť?“)"""
    if not konci_otazkou(text):
        return None
    t = text
    if len(t) > MAX_ZNAKOV:
        rez = t.find('\n\n', len(t) - MAX_ZNAKOV)
        t = t[rez + 2:] if rez >= 0 else t[-MAX_ZNAKOV:]
        if t.count('```') % 2:
            t = '```' + t
    t = cisty(t)
    ods = _odseky(t)
    if not ods:
        return None
    blok = [ods[-1]]
    i = len(ods) - 1
    # voľný zoznam („1. …\n\n2. …?“) a úvod zoznamu („Na rozhodnutie:“, „## Na rozhodnutie“) patria k bloku
    if _ZOZNAM.match(ods[-1]):
        while i - 1 >= 0 and _ZOZNAM.match(ods[i - 1]) and not ods[i - 1].lstrip().startswith('#'):
            i -= 1
            blok.insert(0, ods[i])
        if i - 1 >= 0 and (ods[i - 1].rstrip().endswith(':') or ods[i - 1].lstrip().startswith('#')) \
                and len(ods[i - 1]) < 400:
            i -= 1
            blok.insert(0, ods[i])
    elif i - 1 >= 0 and ods[i - 1].rstrip().endswith(':') and len(ods[i - 1]) < 600 and len(ods[-1]) < 400:
        i -= 1
        blok.insert(0, ods[i])
    # rozšírenie nahor: odseky priamo nad základom, ktoré tiež čakajú na človeka (otázka, žiadosť o verdikt) — inak by sa
    # zmiešaný koniec („… rozhodnúť to máš ty.\n\nMám vygenerovať dokumenty nanovo?“) posúdil ako čistá ponuka
    nad = []
    j = i - 1
    while j >= 0 and len(nad) < ROZSIRENIE_ODSEKOV:
        p = ods[j]
        if p.lstrip().startswith('#'):
            break
        if _odsek_caka_na_cloveka(p):
            nad.insert(0, p)
            j -= 1
            continue
        # F7 (revízia 2): tabuľka medzi otázkou na človeka a koncovou ponukou („Ktorú verziu vyberáš?“ → tabuľka →
        # „Mám pripraviť aj tabuľku pre verziu B?“) — tabuľka je obsah otázky nad ňou, rozšírenie ide ďalej
        riadky_p = [r for r in p.split('\n') if r.strip()]
        if riadky_p and all(_je_tabulka(r) for r in riadky_p) and j - 1 >= 0 and _odsek_caka_na_cloveka(ods[j - 1]) \
                and len(nad) + 2 <= ROZSIRENIE_ODSEKOV:
            nad[0:0] = [ods[j - 1], p]
            j -= 2
            continue
        # zoznam možností pod otázkou („Beží to na AWS, alebo na Azure?“ → „- Ak AWS: …“ „- Ak Azure: …“) patrí k nej
        if _ZOZNAM.match(p) and j - 1 >= 0 and not ods[j - 1].lstrip().startswith('#') \
                and _odsek_caka_na_cloveka(ods[j - 1]) and len(nad) + 2 <= ROZSIRENIE_ODSEKOV:
            nad[0:0] = [ods[j - 1], p]
            j -= 2
            continue
        if nad and p.rstrip().endswith(':') and len(p) < 400:      # úvod zoznamu otázok nad blokom
            nad.insert(0, p)
            j -= 1
        break
    cast = []
    nadpis = ''
    while j >= 0 and len(cast) < CAST_ODSEKOV and sum(len(c) for c in cast) < CAST_ZNAKOV:
        if _hlavicka(ods[j]):
            nadpis = ods[j]                             # nadpis aktuálnej časti („## Ako to zaznie v maile“)
            break
        cast.insert(0, ods[j])
        j -= 1
    return dict(odseky=nad + blok, zaklad=len(nad), cast=cast, nadpis=nadpis, surovy=(text or '')[-3000:])


# zložená otázka „…, a má X …?“ — dve otázky v jednej vete („mám tie názvy opraviť, a má import na Preview zostať?“)
_ZLOZENA_SLOVO = (r'má|majú|mám|máme|môžem|môžeme|chceš|chcete|je|sú|bude|budú|či|kto|čo|kedy|kde|ako|prečo|ktor\w+|koľko')
_ZLOZENA = re.compile(r',\s+a\s+(?=(?:\S+\s+)?(?:' + _ZLOZENA_SLOVO + r')\b)|'
                      r'\s[—–]\s+(?=(?:\S+\s+)?(?:máš|máte|vieš|viete|poznáš|pamätáš|potrebuješ|bude|budú|platí|je|sú|'
                      r'robil\w*|kto|čo|kedy|kde|kam|koľko|ktor\w+|prečo|či)\b)', re.I)
# oslovenie na začiatku odseku = návrh listu alebo správy pre niekoho iného; otázky v ňom nie sú otázky na človeka
_OSLOVENIE = re.compile(r'^[\s>*_"]*(dobry den|dobry vecer|dobre rano|vazen\w+|mil[ya] |ahoj\b|cau\b|zdravim\b|'
                        r'dear\b|hello\b|hi\b)[^.?!\n]{0,40}[,!]')
# vykanie alebo tykanie s veľkým písmenom uprostred vety („Môžem Vám …“) — sloh listu
_LISTOVY_SLOH = re.compile(r'(?<=[a-záäčďéíĺľňóôŕšťúýž,;] )(Vám|Vás|Váš\w*|Vaš\w+|Vami|Tebe|Ťa|Tvoj\w*)\b')


def _pocet_viet(odsek):
    return len(_vety(' '.join(_ZOZNAM.sub('', r).strip() for r in odsek.split('\n') if r.strip() and not _je_tabulka(r))))


def otazky_bloku(blok):
    """Otázky koncového bloku: [dict(veta, jednotka, predosla, nasl, uvod, polozka, citat, list, odsek, nad, sam)] —
    položka zoznamu je samostatná jednotka (s pokračovacími riadkami), úvod zoznamu je riadok končiaci dvojbodkou nad ňou.
    nad = otázka z rozšírenia nad základom · sam = otázka je v odseku jediná veta · list = otázka v návrhu listu."""
    jednotky = []          # [text, uvod, cislo polozky, citat, index odseku]
    uvod = ''
    list_od = None         # index odseku, od ktorého ide návrh listu (oslovenie)
    okolie = (blok.get('cast') or [])[-5:]
    if any(_OSLOVENIE.match(norm(p)) for p in okolie):
        list_od = 0
    for k, ods in enumerate(blok['odseky']):
        if list_od is None and _OSLOVENIE.match(norm(ods)):
            list_od = k
        prvy = next((r for r in ods.split('\n') if r.strip()), '')
        if not _ZOZNAM.match(prvy) and not prvy.rstrip().endswith(':'):
            uvod = ''                                  # nový obyčajný odsek — úvod zoznamu nad ním sa naň nevzťahuje
        akt = None
        for r in ods.split('\n'):
            if not r.strip() or _je_tabulka(r):
                continue
            m = _ZOZNAM.match(r)
            if m:
                if akt:
                    jednotky.append(akt)
                c = re.match(r'\s*(\d{1,2})[.)]', r)
                akt = [r[m.end():].strip(), uvod, int(c.group(1)) if c else None, _je_citat(r), k]
            elif akt and r.startswith((' ', '\t')) and not akt[0].rstrip().endswith('?'):
                akt[0] += ' ' + r.strip()
            else:
                if akt:
                    jednotky.append(akt)
                    akt = None
                s = r.strip().lstrip('#').strip()
                if s.endswith(':'):
                    uvod = s
                jednotky.append([s, '' if s.endswith(':') else uvod, None, _je_citat(r), k])
        if akt:
            jednotky.append(akt)
    von = []
    zaklad = blok.get('zaklad', 0)
    sam = {}
    for text, uv, cislo, citat, k in jednotky:
        vety = _vety(text)
        for j, v in enumerate(vety):
            if not _KONIEC_OTAZKY.search(v):
                continue
            casti = [c.strip() for c in _ZLOZENA.split(v) if c and c.strip()] if _ZLOZENA.search(v) else [v]
            if len(casti) < 2:
                casti = [v]
            odsek = blok['odseky'][k]
            if k not in sam:
                sam[k] = _pocet_viet(odsek) <= 1
            for cast in casti:
                veta = cast.strip(' *_')
                if not veta.endswith('?'):
                    veta += '?'
                von.append(dict(veta=veta, jednotka=text, predosla=vety[j - 1] if j else '',
                                nasl=vety[j + 1] if j + 1 < len(vety) else '', uvod=uv, polozka=cislo, citat=citat,
                                list=(list_od is not None and k >= list_od) or bool(_LISTOVY_SLOH.search(v)),
                                odsek=odsek, nad=k < zaklad, zlozena=len(casti) > 1, sam=sam[k]))
    return von


# ---------------------------------------------------------------------------------------------- znaky tried
# Všetky výrazy bežia nad norm() textom (malé písmená, bez diakritiky), okrem _B_MENO, _B_IDE_K, _B_KOMU
# a _B_CUDZI_SUBOR (pôvodný text).
# B — veľká vec. Konanie (odoslanie, zápis do systému, zmazanie, platba) aj podstatné meno deja („nasadenie“,
# „odoslanie“, „úhrada“) a predmet, ktorý z otázky robí veľkú vec (zmluva, objednávka, CLAUDE.md, cudzí projekt).
# „Google Workspace“ ako dodávateľ vo faktúrach nie je zápis do Google; „pred odoslaním“ je čas, nie odoslanie.
_B_VON = re.compile(          # beží nad bez_diakritiky(pôvodný) s re.I — veľké písmeno uprostred vety je názov („Poslat CFO“)
    r'\b(ne)?(poslat|poslal[aiy]?|poslem|posleme|posli|poslite|posiela\w*|odoslat|odoslem|odosleme|odosli|odoslal\w*|'
    r'odosiela\w*|zaslat|zaslem|zasleme|zasiela\w*|rozoslat|rozoslem|rozosiela\w*|rozposlat|rozposlem|rozposiela\w*|'
    r'preposlat|preposlem|preposiela\w*|dorucit|dorucim|dorucime|odovzdat|odovzdam|odovzdame|'
    r'odide|odidu|odisl\w*|zverejnit|zverejnim|zverejnime|zverejnovat|uverejnit|uverejnim|uverejnime|publikovat|'
    r'publikujem\w*|nasadit|nasadim|nasadime|nasadzova\w*|deploynut|mergnut|mergnem|mergneme)\b|'
    r'\b(pojde|pojdu|ide|idu|ist|odide|isiel|isla)\s+von\b|'
    r'\bna (zivy |zivom )?web(e|u)?\b|\bna (oboch |tych )?weboch\b|\bzivom webe\b|\bdo produkci\w*|'
    r'\b(ide|pojde|nejde),? alebo (nejde|nepojde|ide)\b|\bschrank\w*\b', re.I)
# čas, nie konanie: „pred odoslaním“, „po zverejnení“ — dej urobí človek inokedy, partner ponúka prácu pred ním
_CAS_DEJA = re.compile(r'\b(pred|po) (\w+ ){0,2}(odoslani\w*|zverejneni\w*|nasadeni\w*|publikovani\w*)\b')
_B_VON_DEJ = re.compile(
    r'\b(odoslan(ie|ia|iu|im|i)|rozoslan\w*|rozposlan\w*|rozposielan\w*|zaslan(ie|ia|im)|zverejnen(ie|ia|iu|im|i)|'
    r'uverejnen\w*|publikovan(ie|ia|im)|nasaden(ie|ia|iu|im|i)|nasadzovan\w*|deploy\w*|release\w*|merg\w*|'
    r'zlucit (do|s)|zlucim (do|s)|do main(u)?|na main(e)?|produkcn\w*|na prod\w*|prod|naostro|'
    r'ostr(a|u|y|ej|eho|om|e) (prevadzk|rezim|migraci|verzi|beh|spusten)\w*|pozvank\w*|newsletter\w*|'
    r'odovzdan\w*|zadavac\w* dokumentaci\w*|verejn\w* obstaravan\w*|tender\w*|sutaz\w*|'
    # verzia 3: obsah pre verejnosť alebo úrad (tlačová správa, sociálne siete, podanie, žaloba, daňové priznanie)
    r'landing\w*|microsite\w*|webstrank\w*|webov\w* strank\w*|'
    r'tlacov\w* sprav\w*|tlacovk\w*|press release|socialn\w* siet\w*|podani\w* (na sud|sudu|na urad|zaloby|odvolani|'
    r'namietk)\w*|zalob\w*|danov\w* priznani\w*|ziadost\w* o (dotaci|grant|prispevok|licenci|povoleni)\w*)\b|'
    # verzia 3: výslovný príjemca vo vedľajšej vete („dokument, ktorý už klient dostal“, „číslo, ktoré uvidí CFO“,
    # „verzia, ktorú sme poslali“) — obsah už je alebo bude u niekoho mimo projektu
    r'\bktor(y|a|e|u|eho|ych|ou|om|i)\s+((uz|dnes|zajtra|potom|mu|jej|im)\s+){0,2}((?!si\b|sme\b|ste\b|som\b)\w+\s+)?'
    r'(dostal\w*|dostane|dostanu|uvidi|uvidia|videl[aoi]?|odide|odidu|odisl\w*)\b|'
    r'\bktor(y|a|e|u|eho|ych|ou|om|i)\s+((uz|dnes|zajtra|potom|sme|ste|mu|jej|im)\s+){0,3}(poslal\w*|odoslal\w*|'
    r'posielas|posielate|posielame|posleme|posles|poslete)\b')
_B_SYSTEM = re.compile(
    r'\b(v|vo|na|do|z|zo|cez)\s+(google\w*|meta|facebook\w*|github\w*|gitlab\w*|jir[aeyou]\w*|wordpress\w*|sharepoint\w*|'
    r'teams\w*|outlook\w*|gmail\w*|linkedin\w*|confluence\w*|salesforce\w*|dns|crm\w*|erp\w*|azure\w*|slack\w*|'
    r'kalendar\w*)\b|'
    r'\bgithub\w*\b|\b(ucet|uctu|ucte|uctom|ucty|uctoch|uctov)\b|\bkampan\w*\b|\bkanal(a|u|e|om|y|ov|och|mi)?\b|'
    r'\breklam(a|y|e|u|ou|ach|ami|nych|ny|ne|nom)?\b|'
    r'\bserver\w*\b|\bdo (cudzieho|externeho|ineho) systemu\b|\bcudz\w* system\w*\b|'
    r'\b(nainstal\w*|instal\w*|odinstal\w*|sudo|systemov\w* sluzb\w*|push\w*|pushn\w*|commit\w*|'
    r'(zapis\w*|zmen\w*|uprav\w*|nahra\w*) (\w+ ){0,2}(do|v) databaz\w*|'
    r'zapnut|zapnem|zapneme|vypnut|vypnem|vypneme|restart\w*|ticket\w*|tiket\w*|issue|predplatn\w*|licenci\w*|'
    r'hook\w*|cron\w*|migraci\w*)\b|'
    # verzia 3 (sondy 30. 9., nie ladené na stopách): bežné cudzie služby menom aj bez predložky — zápis do nich je vždy
    # mimo projektu; DNS, e-shop a „ostrý“ systém sú živé; presun alebo prenos dát
    r'\b(asan(a|y|e|u|ou)|trell(o|a|e|u|om)|notion\w*|hubspot\w*|pipedrive\w*|mailchimp\w*|mailerlite\w*|ecomail\w*|'
    r'smartemailing\w*|sender\w*|brevo\w*|sendgrid\w*|clickup\w*|airtable\w*|basecamp\w*|zendesk\w*|freshdesk\w*|'
    r'intercom\w*|shoptet\w*|shopify\w*|woocommerce\w*|webflow\w*|wix\w*|cloudflare\w*|stripe\w*|paypal\w*|'
    r'money s3|helios\w*|clockify\w*|toggl\w*|calendly\w*|dropbox\w*|onedrive\w*|google drive\w*|icloud\w*|'
    r'canv(a|y|e|u|ou)|figm(a|y|e|u|ou)|zapier\w*|make\.com|n8n|power automate|power bi|tableau\w*|looker\w*|'
    r'bigquery\w*|supabase\w*|firebase\w*|vercel\w*|netlify\w*|heroku\w*|hetzner\w*|websupport\w*|wedos\w*|'
    r'godaddy\w*|dns|search console|tag manager|google analytics)\b|'
    r'\b(v|vo|na|do|z|zo)\s+(\w+\s+)?e-?shop\w*|\bostr\w* (web|system|server|prostredi|databaz)\w*|'
    r'\b(v|vo|do|z|zo)\s+uctovnictv\w*|'
    r'\b(presun|prenos)\w* (\w+ )?dat\b')
_B_ZMAZ = re.compile(
    r'\b(zmaz\w*|vymaz\w*|odmaz\w*|premaz\w*|zahod\w*|vyhod\w*|odstran\w*|zrus\w*|stash\w*|reset\w*|nevratn\w*|natrvalo|'
    r'necommit\w*|neulozen\w*|odlozen\w* (uprav|zmen)\w*|neda sa (to )?vratit|odvolat sa neda|nedaju sa vratit|'
    r'prepis\w* (povodn|existujuc|tvoj|rucn)\w*|povodn\w* subor\w*|ktore si (urobil|upravil|zmenil|napisal)\w*|'
    r'zaloh\w*|'
    # verzia 3: prepis histórie verzií
    r'histori\w* (gitu|repozitar\w*|commit\w*|vetv\w*)|git\w* histori\w*|rebase\w*|force[- ]?push\w*|amend\w*)\b')
_B_PENIAZE = re.compile(      # platba a záväzok ako konanie alebo predmet — „zaplatené 209 997 €“ v dátach nie je platba
    r'\b(kupit|kupim|kupime|kup(u|a|ou)?|nakupit|nakupim|nakup(u|om|e|y)?|zaplatit|zaplatim|zaplatime|zaplat|platb\w*|'
    r'platc\w*|platit|platim|platime|uhradit|uhradim|uhradime|uhrad(a|y|u|e|ou)|minat|minie|minu|minaju|'
    r'peniaz\w* (idu|pojdu|odidu)|stoj\w* peniaze|previest peniaze|prevod\w* penaz\w*|objednat|objednam|objednavk\w*|'
    r'rozpoc\w*|budget\w*|zmluv(a|y|e|u|ou|am|ach|ami)?|podpis(at|em|eme|u|om|y|e)?|podpisov\w*|vypoved\w*|'
    # verzia 3: doklad, ktorý sa vystavuje alebo platí, ako predmet ponuky („pripraviť faktúru“, „spraviť prevod“) — nie
    # údaj o ňom v analýze („doplniť sumy dvoch faktúr v registri“ ostáva vratná práca)
    r'dobropis\w*|upomienk\w*|platobn\w* prikaz\w*|bankov\w* prevod\w*|prevod\w* (penazi|peniazov|na ucet|platby)|'
    r'obchodn\w* ponuk\w*|ponuk\w* (pre|klient|zakaznik)\w*|'
    r'(pripravit|pripravim|pripravime|vygenerovat|vygenerujem|vystavit|vystavim|spravit|spravim|urobit|urobim|'
    r'zadat|zadam|zauctovat)\s+(\w+\s+){0,2}(faktur\w*|cenov\w* ponuk\w*|prevod(y|u)?|zalohov\w* faktur\w*))\b')
_B_CLOVEK = re.compile(
    r'\b(kolegov\w*|kolegom|kolegovi|kolegyn\w*|kolegami|ludia mimo|ludi mimo|ludom mimo|'
    r'mimo (projekt|firm|tim|organizac)\w*|konkretn\w* (clovek\w*|cloveka|osob\w*)|menovit\w*|'
    r'osobn\w* (udaj\w*|profil\w*)|stretnut\w* so?|'
    r'(dat|dam|dame|dali) (mu|jej|im) (to )?vediet|(mu|jej|im) (to )?(napisat|poslat|odpovedat|ukazat|povedat|dat vediet)|'
    r'(napisat|odpovedat|ukazat|povedat|preposlat) (mu|jej|im)|pozvanie|pozvat|spristupnit|pridat (ho|ju|ich) do|'
    r'zavolat|zavolam|telefonovat|'
    r'klientovi|klientom|klientke|klientkam|dodavatelovi|dodavatelom|zakaznikovi|zakaznikom|odberatelovi|odberatelom|'
    r'vedeniu|predstavenstvu|riaditelovi|nadriaden(emu|ej)|uradu|na urad\w*|von z (firm|projekt|tim|organizac)\w*|'
    r'\w{2,}-?ovi|'                                  # datív životného podstatného mena: klientovi, PM-ovi, znalcovi
    r'pre (klient\w*|zakaznik\w*|dodavatel\w*|odberatel\w*|sefa|sefov\w*|nastupc\w*|veduc\w*|riaditel\w*|'
    r'nadriaden\w*|kolegu|kolegov|kolegyn\w*|uctovnic\w*|uctovnik\w*|pravnik\w*|advokat\w*|auditor\w*|znal(ca|cov)|'
    r'ministerstv\w*|ministr\w*|urad\w*|vedeni\w*|predstavenstv\w*|investor\w*|bank(u|y)|verejnost\w*|medi(a|i)|'
    r'novinar\w*|partner(a|ov)|pm-?ov\w*|projektov\w* manazer\w*|panel\w*|komisi\w*|porot\w*|hodnotitel\w*)|'
    # verzia 3: záznam o konkrétnom človeku (dochádzka, mzda, hodnotenie, CV, pohovor) a kolega v inom páde
    r'kolega|kolegu|zamestnan\w*|dochadzk\w*|absenci\w*|mzd(a|y|u|ou|ach|ami|ov\w*)|vyplat(a|y|u|ou|ne|nu|nej)|'
    r'klienta|klientov|zakaznika|zakaznikov|dodavatela|dodavatelov|odberatela|odberatelov|'
    r'vlad(a|y|e|u|ou)|ministerstv\w*|parlament\w*|sud(u|e|om)?|'
    r'osobn\w* spis\w*|pracovn\w* pomer\w*|dovolenk\w*|zivotopis\w*|cv|pohovor\w*|'
    r'hodnoteni\w* (zamestnanc|kolegu|kolegyne|pracovnik|cloveka|ludi)\w*|posud\w* na (kolegu|cloveka|zamestnanc)\w*)\b')
# „pre“ + meno alebo skratka inštitúcie s veľkým písmenom uprostred vety („pre Helu“, „pre SOFTEC“, „pre CFO“) — PÔVODNÝ
# text; nie označenie položky s číslicou („pre VZ1“, „pre K-14“)
_B_PRE = re.compile(r'(?<=[a-záäčďéíĺľňóôŕšťúýž,;:] )pre\s+(?:pána\s+|pani\s+)?[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ](?![\wáäčďéíĺľňóôŕšťúýž]*[\d‑-])')
# pomenovaný človek alebo inštitúcia, ktorá s výsledkom niečo urobí („Levenec si vyberie“, „Aleva dostane“, „ČP potvrdí“) —
# PÔVODNÝ text; výsledok je pre niekoho mimo projektu
_B_OSOBA_KONA = re.compile(r'(?<=[a-záäčďéíĺľňóôŕšťúýž,;:] )[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][\wáäčďéíĺľňóôŕšťúýž]{1,}\s+'
                           r'(?:(?:si|sa|to|ho|ju|ich|mu|nám|vám|potom|už|ešte|len|iba|sám|sama)\s+){0,2}'
                           r'(vyberie|vyberú|rozhodne|rozhodnú|schváli|schvália|potvrdí|potvrdia|dostane|dostanú|uvidí|uvidia|'
                           r'pošle|pošlú|odpovie|odpovedia|prečíta|prečítajú|podpíše|podpíšu|pozrie|skontroluje|posúdi|'
                           r'zaplatí|prijme|prijmú|odmietne|odmietnu|pripomienkuje|odsúhlasí|chce|chcú|čaká|čakajú|'
                           r'mohol|mohla|mohli|môže|môžu|potrebuje|potrebujú)\b')
_B_MENA = re.compile(r'\b[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}\s?/\s?[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}\b|'
                     r'\b[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}ov(á|ej|ú|ou)\b|'
                     r'\b(pán|pána|pánovi|pánom|pani|pán doktor|kolega|kolegyňa|kolegu|kolegyni)\s+[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ]')
# možné meno človeka alebo firmy v otázke, vete pred ňou alebo úvode zoznamu: slovo s veľkým začiatočným písmenom
# uprostred vety (nie skratka, nie za predložkou miesta či nástroja „v Exceli“, „do Poľska“, „cez GitHub“) — pomenovaný
# človek alebo firma robí z ponuky vec človeka (pri pochybnosti legitímna); PÔVODNÝ text
_VELKE_SLOVO = re.compile(r'(?<=[a-záäčďéíĺľňóôŕšťúýž,;)] )[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}\b')
_PREDLOZKY_MIESTA = {'v', 'vo', 'na', 'do', 'z', 'zo', 'cez', 'k', 'ku', 's', 'so', 'pri', 'po', 'od', 'o', 'za', 'pred',
                     'nad', 'pod', 'medzi', 'podla', 'podľa', 'ako'}      # „ako Word“ = tvar, nie meno


def _meno_mozne(povodny):
    """Prvé slovo, ktoré môže byť menom človeka alebo firmy, alebo ''."""
    for m in _VELKE_SLOVO.finditer(povodny or ''):
        pred = povodny[:m.start()].rstrip().split()
        if pred and pred[-1].lower().strip(',;(') in _PREDLOZKY_MIESTA:
            continue
        return m.group(0)
    return ''


# komunikácia s niekým ako predmet ponuky: mail, odpoveď, reakcia, stanovisko, pozvánka, list, správa pre …
_B_KOMUNIKACIA = re.compile(
    r'\b((e-)?mail\w*|reakci\w*|vyjadreni\w*|stanovisk\w*|pozvank\w*|'
    r'(pripravit|pripravim|napisat|napisem|spisat|spisem|dopisat|upravit|preformulovat|poslat|draft)\w* (\w+ ){0,2}odpoved\w*|'
    r'draft\w* odpoved\w*|odpoved\w* (\w+ ){0,2}(pripravit|napisat|upravit|preformulovat|poslat)|telefon\w*|prijemc\w*|adresat\w*|'
    r'sprav(u|a|y) (pre|na|do) \w+|list(u)? (pre|na) \w+)\b')
_B_PRAVIDLO = re.compile(
    r'\b(sposob\w* prace|pre vsetk\w* projekt\w*|pre (celu|celej) firm\w*|celofirem\w*|pre vsetkych(?= (kolegov|ludi|pouzivatel|riesitel|clenov|zamestnanc)|\s*[?.,;:)]|\s*$)|pre cely tim|'
    r'pre celu organizaciu|claude\.md|settings\.json|firemn\w* (standard|pravidl|smernic)\w*|standard(u|om|e|y)?|'
    r'smernic\w*|pravidl\w* (pre|stroja|firmy|timu|prace|spravania)|(do|medzi) pravid(iel|la)|ako pravidlo|'
    r'hesl[oaeu]\w*|token\w*|pristup\w*|opravnen\w*|prihlasen\w*|prihlasit|bezpecnost\w*|'
    r'citliv\w* (udaj|informac|vec|dat|tem|otazk|pre|voci)\w*|je (to )?(politicky )?citliv\w*|politick(y|a|e|eho|ej|ych|ym|ou|ymi|u)\b|'
    # verzia 3: prístupové údaje a právne texty, ktoré vidí verejnosť
    r'api kluc\w*|kluc\w* (k |do |pre )?api|credential\w*|secret\w*|certifikat\w*|'
    r'obchodn\w* podmienk\w*|vseobecn\w* obchodn\w*|zasad\w* (ochrany|spracovania|cookies)|gdpr|cookie\w*|'
    r'firewall\w*|vpn|'
    r'journal\w*|zurnal\w*|[jdlqn]-(xxx|\d{2,4}))\b|(?<![\w/])/(decision|learning|journal)\b|(?<![\w.])\.env\b|'
    # verzia 4: záznam starého druhu aj bez lomky („zapísať ako learning + decision“, odložená vzorka jnadhajsky 27. 7.)
    r'\b(ako|do|nov\w*|dalsi\w*) (learning|decision|lesson)s?\b|\b(learning|decision)s? (\+|a|aj) (learning|decision)\b')
# cudzí projekt, spoločné miesto (šablóny, nástroje pre všetky projekty)
_B_CUDZIE = re.compile(
    r'\b(v|vo|do|z|zo) (inom|druhom|cudzom|vedlajsom|ineho|druheho|cudzieho|vedlajsieho|tvojho|tvojom|jej|jeho|ich) '
    r'projekt\w*|\bprojekt(u|e) [a-z0-9]+-[a-z0-9-]+|\bmimo (tohto )?projektu\b|'
    r'\bspolocn\w* (sablon|miest|nastroj|skript|repozitar|kniznic|priecink|zdroj|konfigur)\w*|'
    r'\bzdielan\w* (sablon|priecink|repozitar|disk|subor)\w*|'
    r'\b(v|vo|do|z|zo) (hlavnom |hlavneho |inom |inej |druhom )?(repe|repa|repozitari|repozitara|projekte|projektu) [a-z0-9]|'
    r'\b(v|vo|do|z|zo) (?!e-)[a-z][a-z0-9]+-[a-z0-9][a-z0-9-]*\b(?![.\w/-])|'
    # verzia 3: zdieľané a celofiremné miesto (vidia ho iní ľudia)
    r'\bzdielan\w* (tabul|dokument|priecin|disk|subor|kalendar|priestor|sablon|repozitar|slozk|zlozk|excel|sheet|'
    r'drive|zoznam)\w*|\bfiremn\w* (wiki|intranet|register|sablon|disk|system|tabul|kalendar|priecin|web)\w*|'
    r'\bintranet\w*|\bwiki\b')
# súbor iného človeka: privlastňovacie prídavné meno od mena („do Jánovho konceptu“, „Petrovu verziu“) — PÔVODNÝ text
_B_CUDZI_SUBOR = re.compile(r'(?<=[a-záäčďéíĺľňóôŕšťúýž,;:]\s)[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}'
                            r'(ovho|ovmu|ovom|ovým|ovu|ovej|ových|ovými|inho|inmu|inom|iným|inu|inej|iných|inými)\b')
# ŽIVÝ SYSTÉM — úzka sada pre krátku odkazovaciu otázku („Mám to takto nastaviť?“): návrh nad ňou mení niečo, čo už
# beží mimo projektu alebo stojí peniaze (reklama, web, účet v systéme, odoslanie v 1. osobe, zmazanie, inštalácia).
# Téma analýzy („zmluva“, „faktúra“, „Google Workspace“) ani plán človeka („stačí ju poslať“) sem nepatrí.
_B_ZIVY = re.compile(
    r'\b(kampan\w*|reklam(a|y|e|u|ou|ach|ami|ny|ne|nom|nych)?|na (zivy |zivom )?web(e|u)?|na weboch|zivom webe|'
    r'zverejn(it|im|ime)|publikovat|nasad(it|im|ime)|nasaden(ie|ia|im)|deploy\w*|do produkci\w*|produkcn\w*|'
    r'v (ucte|uctoch|uctu)|'
    r'(v|vo|na|do)\s+(google\w*|meta|facebook\w*|github\w*|gitlab\w*|jir[aeyou]\w*|wordpress\w*|sharepoint\w*|dns)|'
    r'(poslem|posleme|odoslem|odosleme)(?!\s+(ti|tebe|vam))|odide|odidu|pojde von|ide von|'
    r'minat|minie|minu|minaju|peniaze (idu|pojdu)|kupit|kupim|kupime|zaplatit|zaplatim|objednat|objednam|'
    r'zmazem|zmazeme|vymazem|zmazat|vymazat|stash\w*|nevratn\w*|natrvalo|neda sa (to )?vratit|odvolat sa neda|'
    r'nainstalovat|nainstalujem|nainstalujeme|sudo|platc\w*|schrank\w*|push\w*|pushn\w*|'
    r'zapnut|zapnem|zapneme|vypnut|vypnem|vypneme|databaz\w*|search console|analytics|tag manager|gtm|wordpress\w*|'
    r'wp-admin|cms)\b')
# meno človeka v datíve uprostred vety („dať Erike“, „odpoveď Jozefovi“, „Má ísť Alevovi“) — na PÔVODNOM texte
_B_MENO = re.compile(r'(?<=[a-záäčďéíĺľňóôŕšťúýž,;:]\s)[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}(ovi|ke)\b')
# „má ísť ČP“, „pôjde ČP“, „išiel k ČP“ — zásielka ide k pomenovanému adresátovi (pôvodný text, veľké písmeno)
_B_IDE_K = re.compile(r'\b(ísť|pôjde|pôjdu|ide|idú|odíde|odídu|išiel|išla|išlo)\s+(?:(?:k|ku|do)\s+)?[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ]')
# komunikácia s pomenovaným človekom: sloveso alebo podstatné meno komunikácie a do dvoch slov meno s veľkým písmenom
# („napísať Jane“, „odpoveď Jozefovi“, „mail Alene“) — ženské mená v datíve (-e) sa inak nedajú odlíšiť od lokálu
_B_KOMU = re.compile(r'\b(napísať|napíšem|napíšeme|poslať|pošlem|pošleme|odpovedať|odpoviem|odpovieme|odpoveď|povedať|'
                     r'poviem|dať vedieť|dám vedieť|zavolať|zavolám|ukázať|ukážem|preposlať|prepošlem|pripomenúť|'
                     r'odovzdať|odovzdám|sprístupniť|pozvať|ozvať|ozvem|mail|mailom|správu|správa)\s+'
                     r'(?:[a-záäčďéíĺľňóôŕšťúýž]+\s+){0,2}[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ][a-záäčďéíĺľňóôŕšťúýž]{2,}')
# Verzia 5 (väčšinový posudok 18 prípadov 2. 10. a syntetika recenzenta) — veľká vec aj bez slovesa odoslania:
# výstup pre ľudí mimo projektu (pilotný text: „niečo, čo má vidieť niekto mimo projektu“) — podklad na stretnutie,
# prezentácia, publikum menom roly, verejný web, ponuka, súťaž; a príjemca, ktorý výsledok uvidí alebo dostane
_B_VON5 = re.compile(
    r'\b(na|pred|po|pri|do|pocas|k|ku)\s+(\w+\s+)?(stretnut\w*|meeting\w*|porad(a|y|e|u|ou|ach)|workshop\w*|'
    r'prezentaci\w*|schodz\w*|rokovani\w*|webinar\w*|konferenci\w*)\b|'
    r'\b(prezentova\w*|odprezentova\w*|powerpoint\w*|pptx?|slajd\w*|prezentaci\w*|case stud\w*|tlacov\w*)\b|'
    r'\bpre (cfo|ceo|cio|cto|coo|board\w*|manazment\w*|manazer\w*|vedenie|predstavenstv\w*|investor\w*|sponzor\w*|'
    r'zadavatel\w*|objednavatel\w*|ludi mimo|verejnost\w*|media|uradnik\w*|tim (klienta|zakaznika))\b|'
    r'\bpre (tvojho |tvoju |tvojich |nasho |nasu |nasich |jeho |jej )?(sefa|sefov\w*|sefk\w*|veduc\w*|klient\w*|'
    r'zakaznik\w*|dlznik\w*|veritel\w*|najomc\w*|clen\w* (predstavenstv|vybor|rady|komisi)\w*|studentov|ziakov|'
    r'rodicov|ucastnikov|clenov|obyvatelov|obcanov|pacientov|partnerov|akcionarov|investorov|dodavatelov|odberatelov|'
    r'uradnikov|novinarov|poslucacov|citatelov|navstevnikov)\b|'
    r'\b(pojde|pojdu|ide|idu|odide|odidu|dostane|dostanu|uvidi|uvidia)\s+(\w+\s+)?(bank|mest|urad|klient|zakaznik|'
    r'investor|akcionar|partner|veduc|sef|ministerstv|obc|verejnost)\w*|'
    r'\b(rozdam\w*|rozdame|rozdas|rozdaju|veltrh\w*|casopis\w*|novin(y|ach|am)|obal\w* (produkt|vyrobk|tovar)\w*|'
    r'akcionar\w*)\b|'
    r'\b(verejn\w* (web\w*|strank\w*|profil\w*)|cenov\w* ponuk\w*|do ponuky|do tendr\w*|do sutaz\w*|do obstaravani\w*)\b|'
    r'\b(klient\w*|zakaznik\w*|cfo|ceo|vedenie|manazment|board|investor\w*|kolegovia|verejnost)\s+(\w+\s+)?'
    r'(uvidi|uvidia|dostane|dostanu|precita|precitaju|pouzije|pouziju|ukaze|ukazu|dostal|videl|nasiel)\b')
# cudzie systémy a záznamy, ktoré verzia 4 nepoznala (syntetika recenzenta 30. 9.: 28 z 51 veľkých vecí prešlo ako
# ponuka) — verejné registre, účtovné a pokladničné systémy, hosting a pošta domény, živá aplikácia či modul, ktorý
# používajú iní, spoločný kalendár, inzerát, import kontaktov, osobné údaje (norm text)
_B_SYSTEM5 = re.compile(
    r'\b(katastr\w*|kataster|(obchodn|zivnostensk|centraln|statn)\w* regist\w*|sap|ekas\w*|e-kas\w*|dmarc|dkim|spf|'
    r'mx zaznam\w*|htaccess|hosting\w*|pokladn\w*|pokladnicn\w*|administraci\w*|slovensko\.sk|portal\w*|newsletter\w*|'
    r'heurek\w*|mailing\w*|master (vetv|branch)\w*|master\b|hlavn\w* vetv\w*|vetv\w* (main|master)|'
    r'(pouziva|pouzivaju) (\w+ )?(tim\w*|oddeleni\w*|kolegovia|zakaznici|klienti|ludia|pouzivatelia|ostatni|ini|firma|vsetci)|'
    r'(tim\w*|oddeleni\w*|kolegovia|zakaznici|klienti|ludia|pouzivatelia) (uz |aj |teraz )?(pouziva|pouzivaju)|'
    r'(ma|maju) (teraz |prave |uz )?otvoren\w* (\w+ )?(pravnik|klient|kolega|kolegyna|uctovnik|uctovnicka|sef)\w*|'
    r'spolocn\w* (kalendar|tabul|dokument|disk|priecin|konfigur)\w*|inzerat\w*|import\w* (\w+ ){0,2}kontakt\w*|'
    r'prezenck\w*|rodn\w* cisl\w*|refund\w*|storn\w*|(vratit|vratim|vratenie) (\w+ )?(penazi|peniaze)|'
    r'(pre|na|v|vo|do) domen\w*|registraci\w* (firmy|spolocnosti|na portal\w*)|prepusten\w*|brigadnik\w*|'
    r'pravnik\w*|advokat\w*|notar\w*|auditor\w*|uctovnik\w*|uctovnick\w*|veduc\w*|'
    r'(ostatn|vsetk|inym|inych|dalsi|dalsim)\w* projekt\w*|podpisuj\w*|podpise|klauzul\w*|sankci\w*|'
    r'(intern|firemn)\w* manual\w*|pipedriv\w*|airtabl\w*|cloudflar\w*|strip(e|u|om)|onedriv\w*|google driv\w*|'
    r'supabas\w*|firebas\w*|brev(o|a|u|e|om)|triedn\w* knih\w*|zakovsk\w* knizk\w*|klasifikac\w*|'
    r'(subor|dokument|tabulk|priecin|kalendar)\w* (je |su |bol |bola )?(zdielan|spolocn)\w*|'
    r'(zdielan|spolocn)\w* (\w+ )?(harmonogram|plan|tabul|dokument|subor|priecin|kalendar|disk|sablon|zoznam|evidenci)\w*|'
    r'ktor\w* (\w+ )?(pouziva|pouzivaju|vyplna|vyplnaju|edituj\w*|upravuj\w*|spravuj\w*) (\w+ ){0,2}(vsetk|cel|ostatn|'
    r'pobock|oddeleni|tim|kolegovia|ludia|firm)\w*|evidenci\w* (majetku|zamestnanc|dochadzk|ludi|clenov)\w*|'
    r'plat(y|ov|och|mi)|osobn\w* (priecin|subor|dokument|disk|mail|udaj|spis)\w*|'
    r'prepis\w* (\w+ )?(star|predosl)\w* (verzi|subor|dokument)\w*|histori\w* (zmien|verzi\w*)|(v|do|z) git(e|u)?\b|'
    r'vyprazdn\w*|pre (cele|celu|celeho|celej|vsetk\w*) (oddeleni|firm|tim|organizaci|pobock|zamestnanc|kolegov)\w*)\b')
# cudzí systém menom s veľkým písmenom (PÔVODNÝ text) — slová, ktoré malým písmenom znamenajú niečo iné („pohoda“,
# „profesia“)
_B_SYSTEM5_MENO = re.compile(r'\b(Pohod[aeyuo]u?|Profesi[aiu]|Edupage|eKas\w*|Money S3|Omega|Kros|Heurek\w*|SAP|Sharepoint|'
                             r'Jir[aeyu]|Asan[aeyu]|Notion|Trell[oa]|Shoptet|Shopify|WordPress|Wordpress|Mailchimp)\b')
# obsahový verdikt v tvare ponuky (verzia 5; syntetika recenzenta: 19 z 30 verdiktov prešlo ako ponuka) — zmena odhadu,
# záveru, cieľa, rozsahu, hodnotenia rizika, smeru; odklon od pokynu („hoci si chcel“, „namiesto“)
_C_OBSAH5 = re.compile(
    r'\b(odhad\w*|zaver\w*|ciel(a|e|om|u|ov|i)?|cielov\w*|rozsah\w*|predpoklad\w*|strategi\w*|swot|interpretaci\w*|'
    r'vyklad\w* zadani\w*|posud(ok|ku|kom|ky|kov)|nazor\w*|odporucan\w*|hodnoteni\w* rizik\w*|zvysuj\w* rizik\w*|'
    r'priorit\w*|navratnost\w*|tvrdil\w*|tvrdi|opak|neoplat\w*|expanzi\w*|marz\w*|'
    r'(v|na) smer\w*,? ktor\w*|hlavn\w* (cast|kapitol|myslienk|zaver|argument|cisl)\w*|vypust\w*|'
    r'vynech\w* (cel\w* )?(kapitol|cast|sekci)\w*|namiesto|hoci|napriek|aj ked|uzs\w*|sirs\w*|v prospech)\b|'
    r'\baby (\w+ ){0,2}(vyzeral|vyznel|posobil|vyzerali|vyzneli|posobili)\w*|'
    r'\b(z|zo) (vysok|nizk|stredn|kladn|zaporn|pozitivn|negativn)\w* na\b')
# len pre strážcu (meranie by stratilo bežné ponuky „Mám opraviť sumu 120 € v tabuľke?“): suma, percento, cena,
# spôsob „tak, aby / tak, že“ — pochybnosť, nie verdikt (norm)
_POCHYB_OBSAH = re.compile(r'\b\d+([,.]\d+)?\s*(%|percent\w*|€|eur\w*|kc|czk|mil\w*\.?|tis\w*\.?|mld\w*\.?|kus\w*)(?!\w)|'
                           r'\b(tak|tym sposobom),? (aby|ze)\b|\bcen(a|u|y|e|ou|ami|ach)\b|\bcennik\w*')
# to isté v okolí odkazovacej otázky („Navrhujem zmeniť predpoklad: … Mám to takto prepísať?“) — len zmena obsahového
# údaja, nie samotné slovo („Cena je malá“ v odseku nad ponukou je bežná veta)
_C_OBSAH5_OKOLIE = re.compile(
    r'\b(zmen|uprav|prepis|zniz|zvys|posun|prestav|obrat|zrus)\w* (\w+ ){0,3}(odhad|zaver|ciel|rozsah|predpoklad|odporucan|'
    r'hodnoteni|cen[auy]\b|rozpoc|priorit|strategi|argument)\w*|\bnavratnost\w*|\btvrdil\w* opak|\bneoplat\w*|'
    r'\brozpad\w* (sa )?(\w+ )?argument\w*|\binterpretaci\w* zadani\w*|'
    r'\b(zisk|cisl|vysled|naklad|trzb|marz|hodnot|navratnost|uspor)\w* (\w+ ){0,2}(nizs|vyss|mens|vacs|polovicn|'
    r'dvojnasob|tretinov)\w*|'
    r'\bo (tretinu|polovicu|stvrtinu|\d+ ?%) (nizs|vyss|mens|vacs)\w*')
# rozbor (čítanie a rozpis bez zmeny): rozanalyzovať, začať analýzou, overiť, pozrieť, porovnať — výsledok sa aj tak končí
# verdiktom človeka; bez slovesa úpravy v tej istej otázke
# JZ5 (28. 10. 2026): kontrola je čítanie bez zmeny rovnako ako overenie — bez nej by „Mám to ešte overiť?“ bola
# ponuka rozboru a „Mám ešte skontrolovať súčty?“ nie, hoci je to ten istý úkon (meranie parity jazykov J.01).
_ROZBOR = re.compile(r'\b(rozanalyzova\w*|analyz\w*|rozbor\w*|preskuma\w*|preveri\w*|overi\w*|overit|pozriet|pozriem|'
                     r'pozrieme|porovna\w*|skontrolova\w*|prekontrolova\w*|zkontrolova\w*)\b')
_UPRAVA = re.compile(r'\b(doplni|dopisa|oprav|uprav|zapracova|prepisa|prestav|preusporiad|zjednoti|preloz|zapis|premietn|'
                     r'vloz|dokonci|vyries|priprav|sprav|urob|spisa|navrhn|vygener)\w*')


def _je_rozbor(n):
    return bool(_ROZBOR.search(n)) and not _UPRAVA.search(n)


# ČLOVEK URČIL TEMPO (verzia 5; P16, P17: „priprav prvý obrázok“, „priprav 2. obrázok“ — prestávky medzi kusmi použil
# na kontrolu): v správach človeka v tomto ťahu (norm) zadal jeden kus z viacerých (sloveso tvorby + poradie), obmedzil
# prácu („zatiaľ len“, „zatiaľ nemeň súbor“, „po jednom“) alebo ohlásil, že najprv sám skontroluje. „Implementuj krok 4“
# (výkon zvolenej cesty) tempo neurčuje — nasledujúci rozbor sa aj tak končí verdiktom.
_TEMPO_SLOVESO = r'(priprav|sprav|urob|vytvor|napis|nakresli|vygeneruj|spracuj|navrhni|dokonci|prepis|zacni|rob)\w*'
_TEMPO_PORADOVE = r'(prv|druh|tret|stvrt|piat|siest|siedm|osm|deviat|desiat)\w*'
_TEMPO_KUS = (r'(obrazok|obrazk\w*|krok\w*|kapitol\w*|cast\w*|bod\w*|slajd\w*|snimk\w*|sekci\w*|odsek\w*|stran\w*|'
              r'tabulk\w*|graf\w*|diagram\w*|schem\w*|uloh\w*|polozk\w*|subor\w*|dokument\w*|text\w*|verzi\w*|'
              r'navrh\w*|ukazk\w*|vystup\w*|prilo\w*|oblast\w*|etap\w*|faz\w*|dav\w*|mail\w*|list\w*)')
_TEMPO_PORADIE = re.compile(r'\b' + _TEMPO_SLOVESO + r'\s+(\w+\s+){0,2}?(' + _TEMPO_PORADOVE + r'|\d{1,2}\.?)\s+(\w+\s+)?'
                            + _TEMPO_KUS + r'\b|\b' + _TEMPO_SLOVESO + r'\s+(\w+\s+){0,2}?' + _TEMPO_KUS
                            + r'\s+(c\.\s*)?\d{1,2}[a-z]?\b')
_TEMPO_OBMEDZ = re.compile(
    r'\b(zatial|najprv|najskor|teraz|tentoraz)\s+(len|iba|nic|nerob\w*|nemen\w*|nepis\w*|nezapisuj\w*|neuprav\w*|'
    r'nepokracuj\w*|neprepisuj\w*)\b|\b(len|iba)\s+(prv\w*|jed(en|nu|no|neho|nom|nej)|tento|tuto|toto)\b|'
    r'\bpo jednom\b|\bjed(en|nu|no) po (druhom|druhej)\b|\b(a|potom|tam|tu)\s+(pockaj|zastav sa|stoj)\b|'
    r'\bnepokracuj\w*|\b(nemen|neupravuj|nezapisuj|neprepisuj|nesahaj|nedavaj|nevkladaj|nezapracuj|nepridavaj|'
    r'neposielaj|neodosielaj|nezverejnuj|nedoplnaj|neopravuj|nepis|neimplementuj)(te|me)?\b|'
    r'\b(chcem|potrebujem)\s+(\w+\s+){0,2}(vidiet|skontrolovat|prejst|precitat|pozriet)\b|'
    r'\blen\s+(mi\s+)?(povedz|navrhni|vysvetli|popis|zhrn|ukaz|odporuc)\w*|'
    r'\b(zatial|nateraz|najprv|najskor)\s+(len|iba|nic|nie|staci)\b|\bstaci\s+(to|zatial|nateraz)\b|'
    r'\b(len|iba)\s+(\w+\s+)?(uvod\w*|osnov\w*|kapitol\w*\s+\d+|cast\w*\s+\d+|krok\w*\s+\d+|bod\w*\s+\d+)|'
    r'\b(zvysok|ostatne|ostatnu|dalsie|dalsiu)\s+(\w+\s+)?(neskor|potom|inokedy|zajtra)\b|'
    r'\b(len|iba)\s+(do|v|na)\s+chat\w*\b')
# P4Z (24. 10. 2026, náhradný beh): veta, ktorú PS vybral v 4.103 a, menuje za výslovnú hranicu „zatiaľ len do chatu“ —
# vzor ju poznal len so „zatiaľ“. „Len do chatu, neimplementuj“ a „Výsledok iba do chatu“ (23. 10.) hranicou neboli
# a ponuka ďalšieho kusu po nich sa rátala ako zbytočná; v týždni merania po doručení pokynu to jedného človeka
# posunulo nad cieľ (0 → 1 z 9). Pribudli „len / iba do | v | na chat…“ a „neimplementuj“. Prehrávka 30 dní do 24. 10.:
# zmení sa jediná otázka (tá z 23. 10.), 50 → 49 zbytočných. Vrátenie = odobrať tie dva vzory.
# PS 4.103 a (13. 10. 2026): päť vzorov o vlastnej kontrole človeka („najprv si to pozriem“, „skontrolujem to“ …)
# za mesiac nezabralo ani raz — boli mŕtve a text pokynu o nich nehovoril. Trieda „kontrola“ ostáva: dáva ju
# _TEMPO_PODMIENKA („keď schválim“, „až potvrdím“), ktorá zabrala. Vrátenie = vrátiť vzory z histórie gitu.

# človek dovolil pokračovať — tempo neurčil („… a pokračuj“, „všetky“, „naraz“, „bez pýtania“)
_TEMPO_POKRACUJ = re.compile(r'\b(pokracuj\w*|a dalej|(sprav|urob|priprav|spracuj|dokonci|vygeneruj|napis)\w* (\w+ ){0,2}'
                             r'(vsetk\w*|cel\w*)|vsetk\w* naraz|aj ostatn\w*|rovno aj|potom aj|naraz|bez pytania|nepytaj)\b')
# otázka pri ponuke, ktorú partner robí POČAS kontroly človeka („Mám ten odkaz doplniť, kým kontroluješ texty?“, P09):
# človek by mohol schváliť iné znenie, než videl
_KONTROLUJE = re.compile(r'\b(kym|pokial|zatial co|medzitym co|ked)\s+(si\s+)?(\w+\s+){0,2}(kontrolujes|prezeras|citas|'
                         r'pozeras|prechadzas|schvalujes|posudzujes|pripomienkujes|testujes|overujes|preverujes|revidujes|'
                         r'skontrolujes|pozries|precitas|prejdes|prezries|posudis|schvalis|odsuhlasis)\b|'
                         r'\bpocas (tvojej |tvojho )?(kontroly|citania|revizie|schvalovania|posudzovania)\b')


# správa človeka v tomto ťahu výslovne posiela výsledok von alebo ho určuje publiku (verzia 5; norm) — len začiatok
# správy (VON_CLOVEK_ZNAKOV): pokyn býva hore, vložený text nižšie (plán kampane, prepis) nehovorí o tejto ponuke;
# zmienka o stretnutí („na stretnutí s klientom dopresníme“) nestačí — tú berie len strážca ako pochybnosť
VON_CLOVEK_ZNAKOV = 500
_VON_CLOVEK = re.compile(r'\b((ide|pojde|odide|posielam|posielame|posli|poslime) (to |ho |ju |ich )?von|'
                         r'(posli|preposli|odosli) (to |ho |ju |ich )?[a-z]+(ovi|ke|e)\b|'
                         r'pre (riaditel\w*|sef\w*|klient\w*|zakaznik\w*|vedeni\w*|cfo|ceo|board\w*|predstavenstv\w*|'
                         r'investor\w*|akcionar\w*|urad\w*|bank\w*|verejnost\w*))\b')

# text človeka sa číta najviac v rozsahu CLOVEK_ZNAKOV — začiatok (CLOVEK_HLAVA) a koniec; rez pred norm (oprava 3. 10.
# po overení zmluvy 2. 10.: vložený dokument s 1–5 MB sa normalizoval a prehľadával pri každej ponuke bloku, pri 25
# ponukách 2,6–10 s a strážca ticho narazil na svoj limit). Pokyn aj tempo bývajú na začiatku alebo na konci správy;
# dlhšiu správu strážca za neutrálnu nikdy nepovažuje (_istota), rez preto istú otázku pridať nemôže.
CLOVEK_ZNAKOV = 30000
CLOVEK_HLAVA = 20000


def _orez_cloveka(s):
    """Text človeka orezaný na CLOVEK_ZNAKOV: začiatok CLOVEK_HLAVA znakov a koniec; kratší ostáva celý."""
    s = s or ''
    if len(s) <= CLOVEK_ZNAKOV:
        return s
    return s[:CLOVEK_HLAVA] + '\n' + s[-(CLOVEK_ZNAKOV - CLOVEK_HLAVA):]


@functools.lru_cache(maxsize=2)
def _von_cloveka(clovek):
    """Správa človeka výslovne posiela výsledok von alebo ho určuje publiku (_VON_CLOVEK, len začiatok správy) — raz
    za posudok, nie pri každej ponuke (oprava 3. 10.)."""
    return bool(clovek and _VON_CLOVEK.search(norm(_orez_cloveka(clovek))[:VON_CLOVEK_ZNAKOV]))


# ČLOVEK ŽIADAL LEN VYSVETLENIE ALEBO SA PÝTAL (11. 10., PS STAVBA 4.94 d, AIG-R261011-2e5805). Ponuka ROZBORU po
# takej správe je vec človeka (C): rozbor nič nemení a končí sa jeho voľbou, takže otázkou pred ním nestráca nič —
# a rátať ju ako zbytočnú by partnera tlačilo urobiť rozbor, o ktorý nikto nežiadal. Ponuka ÚPRAVY, tvorby či ďalšieho
# kusu po tej istej správe ostáva zbytočná (A): práve ju zakazuje klásť ako otázku druhá veta spresneného pokynu
# z 11. 10., takže meranie ju musí vidieť — inak by sa nedalo zistiť, či pokyn zabral.
# Určovateľ je zrkadlom _je_rozbor: tam „rozbor a nie úprava“, tu „žiadosť o vysvetlenie a nie pokyn na prácu“, oba
# s tým istým zoznamom slovies úpravy (_UPRAVA) — jeden zoznam, nie dva. „Vysvetli B8 a potom to oprav“ je preto pokyn,
# nie žiadosť o vysvetlenie. Známy šum _UPRAVA (slová ako „správne“) robí určovateľ UŽŠÍM, teda prísnejším k partnerovi.
# Zmerané na 30 dňoch do 11. 10. (44 zbytočných otázok; podklad d-podklad.py je len na serveri, obsahuje vety kolegov):
# po žiadosti o vysvetlenie 5, po otázke 6, po pokyne, súhlase alebo fakte 33 — a ponukou ROZBORU nebola z tých 11 ANI
# JEDNA (všetkých 13 ponúk rozboru prišlo po obyčajnom pokyne a medzi nimi je všetkých 6 nálezov strážcu). Pravidlo
# teda na nameranom okne nezmení číslo ani jednému človeku — nikto nedostane cieľ zadarmo (PS 4.98) — a mení len to,
# čo partnera tlačí robiť.
_VYSVETLI = re.compile(r'\b(vysvetli\w*|vysvetlit|nerozumiem|nechapem|nepochopil\w*|objasni\w*|rozved\w*|'
                       r'co (to )?znamena|co tym myslis|ako to mysl\w*|co je to\b|preco\b)')


@functools.lru_cache(maxsize=2)
def _vysvetlenie_cloveka(clovek):
    """Človek v tomto ťahu žiadal len vysvetlenie alebo sa pýtal: 'vysvetlenie' | 'otazka' | ''.
    Žiadosť o vysvetlenie (_VYSVETLI) alebo otázka (otáznik v správe) a ŽIADNE sloveso úpravy či tvorby (_UPRAVA) —
    inak je to pokyn na prácu a ponuka po ňom sa ráta ďalej. Raz za posudok (cache) a len v rozsahu CLOVEK_ZNAKOV
    ako _tempo_cloveka."""
    t = _orez_cloveka(clovek)
    n = norm(t)
    if not n.strip() or _UPRAVA.search(n):
        return ''
    if _VYSVETLI.search(n):
        return 'vysvetlenie'
    return 'otazka' if '?' in t else ''


# pokračovanie podmienené kontrolou človeka („keď ho schválim, pokračuj“) — nie je to povolenie pokračovať
_TEMPO_PODMIENKA = re.compile(r'\b(ked|az|ak|potom co)\s+(\w+\s+){0,2}(schvalim|odsuhlasim|potvrdim|skontrolujem|pozriem|'
                              r'precitam|prejdem|poviem|dam vediet)\b')


# zápor pri povolení pokračovať („nerob to naraz“, „a ďalej nie“, „nie rovno aj druhú“) — povolenie to nie je (oprava
# po overení 2. 10.: výraz „naraz“ či „a ďalej“ sa bral ako povolenie aj so záporom a prebil obmedzenie za ním)
_ZAPOR_PRED = re.compile(r'\b(ne[a-z]+|nie|ani)\s+((to|ho|ju|ich|vsetko|vsetky|cele|celu|cely|hned|rovno|uz|este)\s+){0,2}$')
_ZAPOR_PO = re.compile(r'^\s*,?\s*((zatial|uz|este|teraz|vobec|nateraz)\s+)?(nie|nic|ani|ne[a-z]+)\b')


def _pokracuj_povolene(n):
    """Človek dovolil pokračovať: výraz z _TEMPO_POKRACUJ bez záporu tesne pred ním ani za ním (norm text)."""
    for m in _TEMPO_POKRACUJ.finditer(n):
        if _ZAPOR_PRED.search(n[max(0, m.start() - 40):m.start()]) or _ZAPOR_PO.match(n[m.end():m.end() + 30]):
            continue
        return True
    return False


# FALOŠNÉ ZÁSAHY VÝNIMKY O HRANICI PRÁCE (12. 10., kus P4F; sľub v STAVBA 4.103 — opraviť ich pri každej voľbe PS okrem
# (c), AIG-P261011-f31cd3). Z desiatich prečítaných prípadov za 30 dní do 11. 10. bol vzor pritiahnutý v piatich a jeden
# z nich zmenil počet (zbytočná otázka sa neprávom nerátala). Päť druhov, každý s vlastnou brzdou; samotné vzory sa
# nemenia — mŕtve vzory a „postupne“ ako spôsob práce sú vec voľby PS v 4.103:
#   · vložený obsah (<pasted_content>) a krátky citát v úvodzovkách (1–3 slová) sú zmienka, nie pokyn — pravidlá o kóde,
#     ktoré človek dal posúdiť („Nepridávaj zbytočné abstrakcie…“), slovo, o ktorom hovorí (posun "postupne");
#   · „zatiaľ / najprv nič | nie“ ako opis stavu: hneď za ním „je / sú / bol“ alebo prvé záporné sloveso do čiarky je
#     v minulom čase či opisuje stav („sa zatiaľ nič … neaplikovalo“) — hranicou ostáva „zatiaľ nič nerob“, „zatiaľ nič.“;
#   · rečové „počkaj“ = „zadrž, nerozumiem“: do konca vety otáznik alebo žiadosť o vysvetlenie („počkaj, prečo pk?“) —
#     „počkaj, ešte to čítam“ hranicou ostáva;
#   · „len / iba jeden | prvý | tento“ ako opis dát („je tam iba jeden riadok“, „iba jedného pošleme“): hranicou je len so
#     slovesom tvorby či úpravy do štyroch slov alebo s pomenovaným kusom hneď za ním („sprav len prvý“, „len prvý obrázok“).
# Pri pochybnosti ostáva výnimka ako doteraz (brzda vyradí zásah len pri jasnom znaku opisu či zmienky).
_VLOZENE = re.compile(r'<pasted_content\b[^>]*>.*?(?:</pasted_content[^>]*>|\Z)', re.S)
_CITAT = re.compile(r'"[^"\s]+(?:[ \t]+[^"\s]+){0,2}"')
_POPIS_PO = re.compile(r'\s*(je|su|bol\w*|bud\w*)\b')
_PRVE_ZAPORNE = re.compile(r'\bne[a-z]{2,}\b')
_ZAPOR_POPIS = re.compile(r'ne[a-z]+(l|la|lo|li)$|(nema(m|me|s|te|ju)?|nevie(m|me|s|te)?|neplati|nefunguj\w*|'
                          r'neexistuj\w*|nejde|neide|nesedi)$')
_LEN_JEDEN = re.compile(r'(len|iba)\s+(prv\w*|jed(en|nu|no|neho|nom|nej)|tento|tuto|toto)$')
_PRACA = re.compile(r'\b' + _TEMPO_SLOVESO + r'|' + _UPRAVA.pattern)
_KUS_ZA = re.compile(r'\s*(\w+\s+)?' + _TEMPO_KUS + r'\b')


def _bez_zmienok(n):
    """norm text bez vloženého obsahu (<pasted_content>) a krátkych citátov v úvodzovkách — zmienka nie je hranica práce."""
    return _CITAT.sub(' ', _VLOZENE.sub(' ', n))


def _hranica_obmedz(n):
    """Človek prácu ohraničil: prvý zásah _TEMPO_OBMEDZ, ktorý nie je opisom stavu, rečovým „počkaj“ ani opisom dát."""
    for m in _TEMPO_OBMEDZ.finditer(n):
        t = m.group(0)
        if re.search(r'\b(nic|nie)$', t):
            po = re.split(r'[.,;:!?\n]', n[m.end():m.end() + 160], 1)[0]
            z = _PRVE_ZAPORNE.search(po)
            if _POPIS_PO.match(po) or (z and _ZAPOR_POPIS.match(z.group(0))):
                continue
        if 'pockaj' in t:
            veta = re.split(r'[.!\n]', n[m.end():m.end() + 120], 1)[0]
            if '?' in veta or _VYSVETLI.search(veta):
                continue
        if _LEN_JEDEN.match(t):
            pred = re.split(r'[.;:!?\n]', n[max(0, m.start() - 80):m.start()])[-1].split()[-4:]
            za = re.split(r'[.;:!?\n]', n[m.end():m.end() + 80], 1)[0]
            if not (_PRACA.search(' '.join(pred + za.split()[:4])) or _KUS_ZA.match(za)):
                continue
        if re.search(r'\b(len|iba)$', t) and re.match(r'\s*\(?\d', n[m.end():]) \
                and not _KUS_ZA.match(re.sub(r'^\s*\(?\d+\.?\)?', '', n[m.end():m.end() + 60])):
            continue   # „(teraz len 1)“ — počet v opise dát, nie kus práce („zatiaľ len 1. kapitolu“ hranicou ostáva)
        return True
    return False


@functools.lru_cache(maxsize=2)
def _tempo_cloveka(clovek):
    """Človek si v správach tohto ťahu sám určil tempo (verzia 5): 'poradie' | 'obmedzenie' | 'kontrola' | ''.
    Raz za posudok (cache) a len v rozsahu CLOVEK_ZNAKOV (oprava 3. 10.); vložený obsah a citát sa nečítajú (P4F)."""
    n = _bez_zmienok(norm(_orez_cloveka(clovek)))
    if not n.strip():
        return ''
    if _TEMPO_PODMIENKA.search(n):
        return 'kontrola'
    if _pokracuj_povolene(n):
        return ''
    if _TEMPO_PORADIE.search(n):
        return 'poradie'
    if _hranica_obmedz(n):
        return 'obmedzenie'
    return ''

# R — ponuka pri návrate, ktorú predpisuje pilotný text
_R = re.compile(r'\b(rozroben\w*|rozpracovan\w*|nedokoncen\w*|nedopisan\w*|naposledy (sme|ostal\w*|zostal\w*)|minule sme|'
                r'minule (ostal|zostal)\w*|prerusil\w*|prerusen\w*|od posledneho razu|ked sa vracias|pri navrate|'
                r'navrh\w* (zo sedeni|zaznamov|z predchadzajucich sedeni)|nepotvrden\w* navrh\w*|na tvoje potvrdenie|'
                r'(cakaju|caka) (este |stale |uz |aj )?(na )?tvoje slovo|na tvoje slovo (este |stale |uz )?(cakaju|caka)|'
                # revízia 2 (šošovka falošné zásahy): ďalšie tvary návratu
                r'posledne sme|vitam (ta |vas )?(spat|naspat)|zo vcera|nedokoncil\w*|nedotiahl\w*|rozpisan\w*|nedoroben\w*|'
                r'kym si bol\w* prec|(si|ste) (spat|naspat)|vraciame sa|vracias sa|vratil\w* si sa|'
                # verzia 4 (zvyšok revízie 2: 2 zo 14 ponúk pri návrate): „ostal otvorený návrh“, „ráno sme to nestihli“
                r'(ostal\w*|zostal\w*) otvoren\w*|otvoren\w* (ostal\w*|zostal\w*)|nestihl\w*|'
                r'starsie otazk\w*|otazk\w* z predchadzajucich sedeni|z (minul|predosl)(eho|ych) sedeni\w*|'
                r'pri (poslednom|minulom) sedeni|'
                # verzia 3 (sondy 30. 9.): návrat inými slovami
                r'vitaj (spat|naspat)|ked sme (skoncili|prestali|prerusili)|kde sme (skoncili|prestali|prerusili)|'
                r'vcera sme|z (vcerajsk|minul\w* tyzdn|piatk|pondelk|predosl\w* dn)\w*|pred prestavkou|'
                r'posledn\w* (session|sedeni\w*) (skoncil|prerusil|ostal|zostal)\w*|z minula|'
                r'otvoren\w* (otazk\w*|vec\w*) z (minula|minuleho|predosl\w*|vcera|vcerajsk\w*))\b')
# X — otázka pre tretiu stranu („Pre ČP je otázka jednoduchá: …?“, „Otázky na workshop:“, „Do mailu by som napísal:“)
_X = re.compile(r'\botazk\w*\b[^:?]{0,40}\b(pre|na|do)\s+(?!teba|tvoje|tvoj|ta\b)\S+[^:?]{0,40}:|'
                r'\b(pre|na)\s+(?!teba|tvoje|tvoj)\S+\s+(je|bude|by bola)\b[^:?]{0,40}\botazk\w*[^:?]{0,30}:')
_X_NAVRH = re.compile(r'\b(do (mailu|e-mailu|emailu|spravy|dotaznika|formulara|chatbota|pozvanky|listu)|dotaznik\w*|'
                      r'predmet (mailu|e-mailu|emailu|spravy)|'
                      r'(navrh|text|znenie|koncept|draft) (mailu|e-mailu|emailu|spravy|odpovede|otazok|dotaznika|pozvanky|listu)|'
                      r'uvitac\w+ vet\w*|navrhujem (tieto |taketo |nasledujuce )?otazky|'
                      # verzia 4 (revízia 2, tretia strana): anketa, replika/scenár chatbota, cvičenie, popis PR a nepriama reč
                      # „X sa pýta …:“ — len v úvode s dvojbodkou (úvod zoznamu, veta pred otázkou končiaca „:“, časť pred „:“)
                      r'anket\w*|replik\w*|scenar\w*|cvicen\w*|chatbot\w*|(popis\w*|text\w*) (do |k |pre )?(pr|pull request\w*)|'
                      r'do (pr|pull request\w*) popis\w*|sa (\w+ ){0,4}pyta)\b')

# D — fakt, ktorý vie len človek; spresnenie nejasného pokynu; úkon človeka
_D_OPYT = re.compile(r'^(kto|koho|komu|kym|kedy|kde|kam|odkial|kolko|preco|aky|aka|ake|akeho|akej|akych|akym|akou|'
                     r'co|coho|com|cim|ako|ci|ktor\w*)\b')
_DRUHA_OSOBA = re.compile(
    r'\b(vyberas|vyberies|zvolis|volis|suhlasis|potvrdis|pozries|pozrite|vybavis|vybavite|rozhodnes|rozhodujes|myslis|'
    r'myslite|vies|viete|mas|mate|posles|poslete|odosles|odpovies|napises|das|dodas|preferujes|preferujete|budes|'
    r'budete|pamatas|planujes|chystas|poznas|potrebujes|potrebujete|zistujes|zistujete|vratis|vratite|pouzivas|'
    r'pouzivate|robite|robis|mozes|mozete|chystate|prides|pridete|posudis|pripomienkujes|zvladnes|spravis|urobis|'
    r'prejdes|prejdete|skontrolujes|overis|dostanes|beries|berete|nechas|nechate|ostavas|pridas|pouzijes|'
    r'pouzijete|zistis|zistite)\b|'
    r'\b\w+(l|la|li)\s+(si|ste)\b|\b(si|ste)\s+\w+(l|la|li)\b|\b(bol|bola|boli) (si|ste)\b')
_UKON_CLOVEKA = re.compile(r'\b(odkliknes|odkliknete|odklikol|odklikla|budes pri tom|budete pri tom|prihlasis sa|'
                           r'prihlasite sa|kliknes|kliknete|nahras|nahrate|dodas|dodate|posles mi|poslete mi)\b')
_OBJASNI = re.compile(r'\b(myslel si|myslela si|myslis|myslite|mal si na mysli|mala si na mysli|chcem sa uistit|'
                      r'neviem,? ci (to )?(chces|chcete|potrebujes|potrebujete|myslis|myslite)|'
                      r'nie je (mi )?jasne|nie som si ist\w*|nerozumiem|predpokladam, ze si|ak si myslel|'
                      r'tvoj\w* (odpoved|pokyn|sprav|slov)\w* (\w+ ){0,2}vyzera\w*|vyzera to, ze si)\b')

# C — verdikt
_C = re.compile(r'\b(vyberas|vyberies|zvolis|volis|vyberate|zvolite|rozhodnes|rozhodujes|suhlasis|suhlasite|potvrdis|'
                r'potvrdite|schvalujes|schvalis|ideme (cestou|variantom|moznostou))\b|'
                r'\bktor(u|a|y|e|ou|ym|ych) (cest\w*|moznost\w*|variant\w*|verzi\w*|z (nich|tychto|moznosti|ciest|variant\w*))')
_VOLBA = re.compile(r'(,|\s)\s*alebo\s+\S|^alebo\b|\sci\s+(na|ako|vo|v|do|len|iba)\b')
_OTVORENE = re.compile(r'\b(nieco|nejak\w*|cokolvek|niektor\w*)\b')
_CHCES = re.compile(r'\b(chces|chcete)\b')
_CHCES_ABY = re.compile(r'\b(chces|chcete),?\s+(?:(?:aby|nech)\s+(?:som|sme)|abych|abychom)\b')   # JZ4: české „abych · abychom“

# S — len voľba TVARU výstupu; formulácia, názov či veta v dokumente je obsah → verdikt (C)
_S_TVAR = re.compile(r'\b(ako text\w*|do mailu|vo word\w*|ako word\w*|ako dokument\w*|v pdf|ako pdf|do pdf|v exceli|'
                     r'ako excel\w*|ako xlsx|ako csv|ako svg|ako html|ako obrazok|na zdielanie|ako odkaz|ako tabul\w*|'
                     r'ako zoznam|v markdown\w*)\b')
# čo z voľby tvaru robí rozhodnutie alebo obsah, ktorý ide von (číslované rozhodnutie, zmluva, ponuka, klient,
# business case, znenie v úvodzovkách predložené na verdikt)
_NIE_S = re.compile(r'\b(zmluv\w*|ponuk\w*|klient\w*|zakaznik\w*|business case\w*|biznis case\w*|bc|rozhodnuti\w*|'
                    r'verdikt\w*|znen\w*|formulaci\w*)\b|"')

# A — ponuka / žiadosť o povolenie na vratnú prácu.
# ZOZNAM VRATNEJ PRÁCE: slovesá zo skutočných ponúk partnera na koncoch ťahov 1. 7. – 30. 9. 2026 (stopy PS a kolegov,
# 1 084 koncov s otázkou k 30. 9. ráno). Pri každom: počet ponúk s týmto slovesom · rozdelenie v ručne označenej sade 153 otázok
# (23.–30. 9.) · prečo je práca vratná. Na zozname je sloveso, ktorého bežný predmet je obsah v projekte (text, analýza,
# tabuľka, súbor, ktorý partner sám vedie) alebo čítanie bez zmeny; účinok sa vráti vrátením súboru. Legitímne použitia
# toho istého slovesa (napr. „pripraviť odpoveď Levenecovi“) rieši okolie (veľká vec, adresát, výslovné rozhodnutie).
# NIE je na ňom sloveso, ktorého bežný predmet býva vonku alebo sa nedá vrátiť — spustiť, pustiť, nastaviť, založiť,
# vytvoriť v systéme, zmeniť, vrátiť, presunúť, odstrániť, vyradiť, zrušiť, potvrdiť, locknúť, prepnúť, použiť, uviesť,
# poslať, napísať komu, prejsť na …, odpovedať, kontaktovať — taká ponuka je O (legitímna). Bez dôkazu v dátach vypadli
# analyzovať a zanalyzovať (0 ponúk), rozpísať a spočítať (po 1 ponuke, bez štítku A).
# sloveso: (1. os. j. č., 1. os. mn. č., príčastie minulé v mužskom rode)
_VRATNE = {
    # tvorba obsahu v projekte
    'pripravit': ('pripravim', 'pripravime', 'pripravil'),        # 65 · A10 M4 C2 B1 D1 · podklad, návrh, verzia v projekte
    'spravit': ('spravim', 'spravime', 'spravil'),                # 62 · A1 · „Mám to spraviť?“ — predmet je návrh nad otázkou
    'urobit': ('urobim', 'urobime', 'urobil'),                    # 19 · A4 B3 M2 · ako spraviť; B rieši okolie
    'spisat': ('spisem', 'spiseme', 'spisal'),                    # 4 · — · text do súboru v projekte
    'navrhnut': ('navrhnem', 'navrhneme', 'navrhol'),             # 5 · A2 · návrh je text, nie rozhodnutie
    'vygenerovat': ('vygenerujem', 'vygenerujeme', 'vygeneroval'),  # 10 · A4 · výstup zo zdroja v projekte, dá sa znova
    # úprava obsahu v projekte
    'doplnit': ('doplnim', 'doplnime', 'doplnil'),                # 41 · A10 C1 D1 · doplnenie textu alebo tabuľky
    'dopisat': ('dopisem', 'dopiseme', 'dopisal'),                # 9 · M1 · ako doplniť
    'opravit': ('opravim', 'opravime', 'opravil'),                # 21 · A5 B2 C2 · oprava chyby v súbore
    'upravit': ('upravim', 'upravime', 'upravil'),                # 6 · A2 M1 · úprava súboru
    'zapracovat': ('zapracujem', 'zapracujeme', 'zapracoval'),    # 13 · A1 M1 · premietnutie dohodnutého do dokumentu
    'prepisat': ('prepisem', 'prepiseme', 'prepisal'),            # 13 · A1 · prepis textu (nie „prepísať pôvodný súbor“ — B)
    'prestavat': ('prestaviam', 'prestaviame', 'prestaval'),      # 3 · — · zmena štruktúry dokumentu
    'preusporiadat': ('preusporiadam', 'preusporiadame', 'preusporiadal'),  # 1 · A1 · poradie častí dokumentu
    'zjednotit': ('zjednotim', 'zjednotime', 'zjednotil'),        # 3 · A2 · zosúladenie čísel alebo textu
    'prelozit': ('prelozim', 'prelozime', 'prelozil'),            # 2 · A2 · preklad súboru
    'zapisat': ('zapisem', 'zapiseme', 'zapisal'),                # 24 · A2 M2 · zápis do súboru projektu (J/D/L-XXX — B)
    'premietnut': ('premietnem', 'premietneme', 'premietol'),     # 2 · A1 · prenos dohodnutého do dokumentu
    'vlozit': ('vlozim', 'vlozime', 'vlozil'),                    # 6 · A1 D1 M1 · vloženie do súboru
    'dokoncit': ('dokoncim', 'dokoncime', 'dokoncil'),            # 4 · — · dokončenie rozrobenej práce (návrat — R)
    'vyriesit': ('vyriesim', 'vyriesime', 'vyriesil'),            # 6 · A2 · vyriešenie nezhody v súbore
    # čítanie a rozbor bez zmeny
    'rozanalyzovat': ('rozanalyzujem', 'rozanalyzujeme', 'rozanalyzoval'),  # 9 · A9 · rozbor ďalšieho kroku
    'porovnat': ('porovnam', 'porovname', 'porovnal'),            # 2 · — · porovnanie bez zmeny
    'overit': ('overim', 'overime', 'overil'),                    # 9 · — · overenie v zdroji bez zmeny
    'pozriet': ('pozriem', 'pozrieme', 'pozrel'),                 # 13 · — · pozrieť sa = čítanie
    # JZ5 (28. 10. 2026, z merania parity jazykov J.01): kontrola po dokončenej práci chýbala — „Tabuľka je hotová.
    # Mám ešte skontrolovať súčty?“ vychádzala ako iná otázka (O) v slovenčine aj v češtine, hoci je to ten istý
    # krok navyše ako „Mám to ešte overiť?“. Kontrola je čítanie bez zmeny, rovnako ako overiť/pozrieť/porovnať.
    'skontrolovat': ('skontrolujem', 'skontrolujeme', 'skontroloval'),      # 0 ponúk 1. 7. – 30. 9. · doplnené z J.01 · kontrola bez zmeny
    'prekontrolovat': ('prekontrolujem', 'prekontrolujeme', 'prekontroloval'),  # ten istý tvar s predponou pre-
    'preverit': ('preverim', 'preverime', 'preveril'),            # 0 ponúk · doplnené z J.01 · preverenie v zdroji bez zmeny
    # postup práce
    'zacat': ('zacnem', 'zacneme', 'zacal'),                      # 21 · A6 M1 · začať ďalší krok práce v projekte
    'pokracovat': ('pokracujem', 'pokracujeme', 'pokracoval'),    # 21 · A1 R1 · pokračovať v práci (návrat — R)
    'postupovat': ('postupujem', 'postupujeme', 'postupoval'),    # 1 · A1 · „Mám takto postupovať?“
}
_VRATNE_INF = set(_VRATNE)
_VRATNE_1SG = set(v[0] for v in _VRATNE.values())
_VRATNE_1PL = set(v[1] for v in _VRATNE.values())
_VRATNE_PRIC = set()
for _v in _VRATNE.values():
    _k = _v[2]
    if _k.endswith('ol'):                               # navrhol → navrhla, navrhli
        _VRATNE_PRIC.update((_k, _k[:-2] + 'la', _k[:-2] + 'li'))
    elif _k.endswith('el'):                             # pozrel → pozrela, pozreli
        _VRATNE_PRIC.update((_k, _k + 'a', _k + 'i'))
    else:
        _VRATNE_PRIC.update((_k, _k + 'a', _k + 'i'))
# slová na -l/-la/-li, ktoré nie sú príčastie slovesa ponuky (v „Chceš, aby som …“)
_NIE_PRIC = {'mail', 'email', 'model', 'detail', 'podla', 'vela', 'cela', 'cele', 'mala', 'mali', 'bola', 'boli', 'bol',
             'mohol', 'mohla', 'mohli', 'skola', 'sila', 'chvila', 'zial', 'portal', 'kanal', 'material', 'dali'}

_PONUKA_HLAVA = re.compile(r'(^|[;:,]\s*|\s)(mam|mame|mozem|mozeme)\b(?!\s+((tu|teraz)\s+)?(nameran\w*|otazk\w*|'
                           r'problem\w*|hotov\w*|pripraven\w*|od teba)\b)')
# slová končiace na „ť“, ktoré nie sú infinitív (číslovky, podstatné mená, príslovky)
_NIE_INF = {'desat', 'devat', 'pat', 'sest', 'dvadsat', 'tridsat', 'styridsat', 'opat', 'spat', 'naspat', 'host', 'siet',
            'pamat', 'napat', 'sucast', 'cast', 'vlast', 'oblast', 'zast', 'zet', 'lakt', 'plet', 'smrt', 'nit', 'chut',
            'pecat', 'trat', 'obet', 'labut', 'kost', 'mast', 'bolest', 'cest', 'povest', 'zvlast', 'stvrt', 'hrst',
            'zmat', 'plt', 'ucast', 'past', 'celust', 'strast', 'zavist', 'nenavist', 'plst', 'triest'}
_ANAFORA = re.compile(r'\b(to|takto|tak|ho|ju|ich|tie|tieto|tuto|tu|ten|tento|toto|tym|nimi|nich|tom|nej|nom|tymi|tymito|'
                      r'tych|tychto|tej|tejto|tomto|takyto|taku|taketo|tomu|tomuto|toho|tohto|nu|na to|do toho|do nich|do nej)\b')
_PORADIE = re.compile(r'\bktor\w*\s+(z (nich|toho|tychto)\s+)?(mam|mame)\s+(zacat|pokracovat)\b')
_PODMIENKA = re.compile(r'^(a\s+)?(ak|ked|kym|az|pokial|len co)\b[^,?]{0,160},\s*')
# podmienka, ktorej podmetom je človek („Ak ju chceš ukázať pani X, …“) — jeho úkon, nie konanie partnera
_PODMIENKA_CLOVEK = re.compile(r'^(a\s+)?(ak|ked|kym|pokial)\s+(\w+\s+){0,2}(chces|chcete|potrebujes|potrebujete|budes|'
                               r'budete|planujes|planujete|pojdes|pojdete|mas|mate|posles|poslete)\b[^,?]{0,160},\s*')
_ALEBO_POCKAT = re.compile(r',?\s*alebo\s+(to\s+)?(zatial\s+)?(pockat|pockam|nechat (to )?tak|nerobit(\s+nic)?|'
                           r'zatial nie)\s*\??$')
# úniková veta: „O tomto rozhoduje človek, lebo <dôvod aspoň troma slovami>“ (aj „rozhoduješ ty, lebo …“)
_UNIK = re.compile(r'\b(rozhoduje(s)?\s+(o (tom|tomto|tejto veci|nich)\s+)?(clovek|ty)|(clovek|ty)\s+rozhodujes?|'
                   r'o (tomto|tom|tejto veci|nich) rozhoduje(s)?\s+(clovek|ty))\b[^.?!\n]{0,120}\b(lebo|pretoze|kedze)\s+'
                   r'\S+\s+\S+\s+\S+')
# angličtina: otázka bez slovenskej diakritiky s anglickými funkčnými slovami
_EN = set('the is are was were does did you your we our should shall would could can will want what which who when where '
          'why how of for this that these those it and or with from have has had be been not any all if then there here '
          'please let us go ahead'.split())
_SK = set('a aj je su na v vo do z zo s so sa si som sme ste mi ma mam mame mas to tu ten ta tie ci ako ale alebo pre pri '
          'po od k ku o ze by uz este nie ano co kto kde kedy preco on my'.split())


def _anglicky(veta):
    v = (veta or '').lower()
    if re.search(r'[áäčďéíĺľňóôŕšťúýž]', v):
        return False
    w = re.findall(r"[a-z']+", v)
    if len(w) < 3:
        return False
    en = sum(1 for x in w if x in _EN)
    sk = sum(1 for x in w if x in _SK)
    return en >= 2 and en > sk and en / len(w) >= 0.2


# JZ4 (27. 10. 2026, J.03): české infinitívy končia na „t“ (pokračovat), nie na „ť“ — podľa „ť“ bola čeština (a slovenčina písaná bez
# diakritiky) pre toto meranie slepá: každá česká ponuka vyšla ako „iná otázka“ (O) a zbytočné otázky v českom sedení sa
# nikdy nenašli. Bez diakritiky sa za infinitív nesmie brať každé slovo na „t“ („What“, „Audit“), preto sa berú len slovesá zo
# zoznamu vratnej práce (_VRATNE) — české tvary, ktoré sa od slovenských líšia, sú v _CS_INF a prevedú sa na slovenský kľúč.
_CS_INF = {'udelat': 'urobit', 'prepsat': 'prepisat', 'vyresit': 'vyriesit', 'zacit': 'zacat', 'podivat': 'pozriet',
           'navrhnout': 'navrhnut', 'premietnout': 'premietnut', 'preusporadat': 'preusporiadat', 'prestavet': 'prestavat',
           'dopsat': 'dopisat', 'sepsat': 'spisat',
           # JZ5 (28. 10.): české tvary slovies doplnených z merania parity jazykov J.01
           'zkontrolovat': 'skontrolovat', 'proverit': 'preverit'}
_VRATNE_INF.update(_CS_INF)       # české tvary patria k vratnej práci rovnako ako slovenské (vlastný tvar, nie preklad: ponuka ich hľadá vo vete)
# JZ5 (28. 10.): české príčastia slovies vratnej práce, ktoré sa od slovenských líšia písmenom, nie prekladom
# („Chceš, abych to ještě zkontroloval?“). Tvary, ktoré sú po odstránení diakritiky rovnaké (překontroloval →
# prekontroloval), tu nie sú — tie už v _VRATNE_PRIC sú.
for _cp in ('zkontroloval', 'proveril'):
    _VRATNE_PRIC.update((_cp, _cp + 'a', _cp + 'i'))


def _infinitivy(povodny):
    """Slovenské infinitívy vety podľa PÔVODNÉHO textu (končia na „ť“), mimo vedľajších viet s „aby“ — tie opisujú účel
    („…, aby si ju mohol ukázať“), nie ponúkanú prácu. Bez diakritiky by sa za infinitív bralo „What“, „Formát“, „Audit“.
    Vráti zoznam v poradí (tvary bez diakritiky)."""
    t = unicodedata.normalize('NFC', povodny or '').lower()
    t = re.sub(r'\baby\b[^,;?]*', ' ', t)
    von = []
    for w in re.findall(r'\b\w+ť\b', t):
        n = norm(w)
        if n in _NIE_INF or n.endswith(('ost', 'nast', 'dsat')):
            continue
        von.append(n)
    for w in re.findall(r'\b\w+t\b', t):         # JZ4: česky (a bez diakritiky) — len slovesá vratnej práce
        n = norm(w)
        if n in _VRATNE_INF and n not in von:
            von.append(n)
        elif n in _CS_INF and n not in von:
            von.append(n)
    return von


def _bez_podmienky_cloveka(povodny):
    """Veta bez úvodnej podmienky, ktorej podmetom je človek. norm() zachováva dĺžku textu, preto sa rez prenesie."""
    n = norm(povodny)
    m = _PODMIENKA_CLOVEK.match(n)
    if not m or len(n) != len(povodny):
        return povodny
    return povodny[m.end():]


_SPOJKY = re.compile(r'^((a|aj|teda|este|tak|no|potom|najprv)\s+){1,3}')


def _hlava(n):
    """Otázka bez úvodu („A pri doklade X: …“, „Týždeň: …“, „1. Ktorá cesta?“ → „ktora cesta“, „Ak chceš, pripravím
    …?“ → „pripravim …“)."""
    s = re.sub(r'^[\s\-*•·>]*(\d{1,2}[.)]\s*)?', '', n)
    s = re.sub(r'^(a\s+)?ak (chces|chcete|suhlasis|suhlasite)\s*,\s*', '', s)
    s = _SPOJKY.sub('', s)
    if ':' in s[:60] and not s.startswith('http'):
        pred, po = s.split(':', 1)
        if len(pred.split()) <= 6 and po.strip() and '"' not in pred:
            s = _SPOJKY.sub('', po.strip())
    return s


# „to/ich/ho/ju tak + neurčitok“ („Mám to tak upraviť v podrobnom pláne aj v súhrne?“ pod zoznamom ôsmich zmien plánu,
# EP 8. 10., kus P4A) — „tak“ samo je bežná spojka („tak, aby“, „a tak“), preto len za zámenom a pred neurčitkom
_TAKTO = re.compile(r'\b(takto|tak,? ako|v tejto podobe|v tomto tvare|v tomto rozsahu|na tomto zaklade|tie|tieto|tych|'
                    r'tychto|tymi|tymito|tu|tuto|ten|tento|toto|tomto|tejto|toho|tohto|z toho|z tohto|'
                    r'(to|ich|ho|ju) tak \w+t)\b')


def _anaforicka(h):
    """Odkazovacia otázka — predmet ponuky nie je v nej, ale nad ňou („Mám to urobiť?“, „Mám?“, „Spraviť?“,
    „Doplním to takto do summary a idem na plný text?“)."""
    slova = h.split()
    return len(slova) <= 3 or (len(slova) <= 8 and bool(_ANAFORA.search(h))) or (len(slova) <= 14 and bool(_TAKTO.search(h)))


def _ponuka(h, uvod, q):
    """Je hlava otázky ponukou partnera na vlastný krok? Vráti (pravidlo, slovesá mimo zoznamu vratnej práce) alebo None.
    Prázdny zoznam slovies mimo = ponuka vratnej práce (A, ak je okolie čisté); neprázdny = ponuka inej veci (O)."""
    veta = q['veta']
    inf = _infinitivy(veta)
    mimo = [w for w in inf if w not in _VRATNE_INF]
    if 'pustit' in mimo and re.search(r'\bsa\b', h):       # „pustiť sa do“ = začať pracovať; „pustiť“ = spustiť
        mimo = [w for w in mimo if w != 'pustit']
    if _PORADIE.search(h):
        return 'A:poradie', []
    m = re.search(r'\b(chces|chcete),?\s+(?:(?:aby|nech)\s+(?:som|sme)|abych|abychom)\b(.*)', h)   # JZ4: české „abych · abychom“
    if m:
        pric = [w for w in re.findall(r'\b\w{4,}\b', m.group(2))
                if re.fullmatch(r'\w+(l|la|li)', w) and w not in _NIE_PRIC]
        if not pric:
            return None
        zle = [w for w in pric if w not in _VRATNE_PRIC]
        if 'pustil' in zle and re.search(r'\bsa\b', m.group(2)):
            zle = [w for w in zle if w != 'pustil']
        return 'A:chces-aby', zle
    if re.match(r'^(mam|mozem|mame|mozeme)(\s+(teda|to|tak))?\s*\?', h):
        # „Mám?“ — čo sa ponúka, je vo vete pred otázkou („…, pripravím ti ju ako .xlsx. Mám?“)
        pred_p = _bez_podmienky_cloveka(q.get('predosla') or '')
        slova = set(re.findall(r'\b\w+\b', norm(pred_p)))
        inf_p = _infinitivy(pred_p)
        ma = bool(slova & (_VRATNE_1SG | _VRATNE_1PL)) or any(w in _VRATNE_INF for w in inf_p)
        zle = [w for w in inf_p if w not in _VRATNE_INF]
        return 'A:mam?', zle if ma else (zle or ['?'])
    m = _PONUKA_HLAVA.search(h)
    if m:
        zvysok = set(re.findall(r'\b\w+\b', h[m.end():]))
        hlavne = [w for w in inf if w in zvysok]
        if hlavne:
            return 'A:mam-inf', [w for w in hlavne if w in mimo]
    prve = (re.findall(r'\b\w+\b', h) or [''])[0]
    if prve in _VRATNE_1PL:
        return 'A:navrh-my', mimo
    if prve in _VRATNE_1SG:
        return 'A:navrh-ja', mimo
    if inf and prve == inf[0] and (uvod or len(h) < 200):
        return 'A:infinitiv', mimo
    return None


def _von(povodny):
    """Odoslanie von v pôvodnom texte: sloveso s veľkým písmenom uprostred vety je názov („Poslat CFO“), „pošlem ti“
    je správa v rozhovore, nie odoslanie von."""
    t = bez_diakritiky(povodny)
    for m in _B_VON.finditer(t):
        w = m.group(0)
        if w[:1].isupper():
            pred = t[:m.start()].rstrip(' „"*_([')
            if pred and pred[-1] not in '.!?:;\n':
                continue
        if re.match(r'\s+(ti|tebe|vam)\b', t[m.end():m.end() + 8], re.I) or \
                re.search(r'\b(ti|tebe|vam)\s+$', t[max(0, m.start() - 8):m.start()], re.I):
            continue                                    # „pošlem ti“, „mám ti poslať“ = v rozhovore
        return True
    return False


def _velka_vec(n, povodny=''):
    """Znak veľkej veci v úseku: n = norm() text, povodny = ten istý úsek s diakritikou a veľkými písmenami."""
    if povodny and _von(povodny):
        return 'B:von'
    if _B_VON_DEJ.search(_CAS_DEJA.sub(' ', n)):
        return 'B:von-dej'
    for nazov, rx in (('B:system', _B_SYSTEM), ('B:zmazanie', _B_ZMAZ), ('B:peniaze', _B_PENIAZE),
                      ('B:clovek', _B_CLOVEK), ('B:pravidlo', _B_PRAVIDLO), ('B:cudzie', _B_CUDZIE)):
        if rx.search(n):
            return nazov
    if _B_KOMUNIKACIA.search(n):
        return 'B:komunikacia'
    if _B_VON5.search(n):                               # verzia 5: výstup pre ľudí mimo projektu
        return 'B:von-ludia'
    if _B_SYSTEM5.search(n):                            # verzia 5: cudzí systém, register, záznam o človeku
        return 'B:system5'
    if povodny:
        if _B_SYSTEM5_MENO.search(povodny):
            return 'B:system-meno'
        if _B_MENO.search(povodny) or _B_IDE_K.search(povodny) or _B_KOMU.search(povodny) or _B_PRE.search(povodny):
            return 'B:meno'
        if _B_OSOBA_KONA.search(povodny) or _B_MENA.search(povodny):
            return 'B:osoba'
        if _meno_mozne(povodny):
            return 'B:meno-mozne'
        if _B_CUDZI_SUBOR.search(povodny):
            return 'B:cudzi-subor'
    return ''


def trieda_otazky(q, blok, vyslovne=True, clovek=''):
    """(trieda, pravidlo) jednej otázky koncového bloku. Legitímne znaky majú prednosť pred ponukou — pri pochybnosti
    je otázka legitímna (O). vyslovne=False: bez znaku „partner sám píše, že rozhoduje človek“ (na meranie, koľko ponúk
    tento znak kryje). clovek = správy človeka v tomto ťahu (verzia 5: tempo, ktoré si človek určil sám)."""
    veta = q['veta']
    n = norm(veta)
    h = _hlava(n)
    uvod = norm(q.get('uvod') or '')
    jednotka = norm(q.get('jednotka') or '')
    predosla = norm(q.get('predosla') or '')
    if _anglicky(veta):
        return 'O', 'O:anglicky'
    # X — otázka nie je pre človeka v sedení
    if q.get('citat'):
        return 'X', 'X:citat'
    if q.get('list'):
        return 'X', 'X:navrh-listu'
    if _X_NAVRH.search(uvod) or (predosla.rstrip().endswith(':') and _X_NAVRH.search(predosla)) \
            or (':' in n and _X_NAVRH.search(n.rsplit(':', 1)[0])):
        return 'X', 'X:navrh-textu'
    if _X.search(uvod + ' ') or _X.search(predosla + ' ' + n):
        return 'X', 'X:tretia-strana'
    # R — ponuka pri návrate
    nad_r = [norm(o) for o in (blok.get('odseky') or [])]
    if q.get('sam') and blok.get('cast'):
        nad_r.append(norm(blok['cast'][-1]))
    if _R.search(jednotka) or _R.search(uvod) or _R.search(norm(q.get('odsek') or '')) or any(_R.search(x) for x in nad_r):
        return 'R', 'R:navrat'
    # C — partner sám píše, že rozhoduje človek, alebo ide o číslované rozhodnutie
    if vyslovne:
        okolie = ' '.join((n, predosla, uvod))
        if _C_VYSLOVNE.search(okolie) or _C_CISLOVANE.search(okolie) or _C_CISLOVANE.search(jednotka):
            return 'C', 'C:vyslovne'
    if _C_ZMENA_CISLA.search(' '.join((n, predosla))):
        return 'C', 'C:zmena-cisla'
    if _C_MENI_OBSAH.search(' '.join((n, predosla, uvod))):
        return 'C', 'C:meni-obsah'
    if _C_HODNOTA_OK.search(n) and _C_HODNOTA_VEC.search(n):     # P4H: verdikt o hodnote v tej istej otázke
        return 'C', 'C:hodnota-na-verdikt'
    # verzia 5: ponuka počas kontroly človeka („…, kým kontroluješ texty?“) a obsahový verdikt v tvare ponuky
    if _KONTROLUJE.search(' '.join((n, predosla))):
        return 'C', 'C:kontrola-cloveka'
    if _C_OBSAH5.search(n):
        return 'C', 'C:obsah'
    if _ROZKAZ_VERDIKT.match(re.sub(r'^[\s\-*•·>]*(\d{1,2}[.)]\s*)?', '', n)) or _ROZKAZ_VERDIKT.match(predosla.lstrip('*_ ')):
        return 'C', 'C:rozkaz-verdikt'
    if _C_CESTA.search(' '.join((n, predosla, uvod))) and (_PONUKA_HLAVA.search(h) or _CHCES_ABY.search(h)
                                                          or re.match(r'^\w+(ime|eme|ame|im|em|am|ujem|ujeme)\b', h)):
        return 'C', 'C:volba-cesty'
    # B — veľká vec: otázka bez vedľajšej vety podmienky („Ak pôjde von sám, mám …?“ — von ide človek, partner len
    # upravuje), predošlá veta v tej istej jednotke a úvod zoznamu; pri krátkej odkazovacej otázke („Mám to takto
    # urobiť?“) celá aktuálna časť správy — „to“ je návrh nad ňou.
    hp = _PODMIENKA.sub('', h)
    hv = hp if hp != h and _PONUKA_HLAVA.search(hp) else h
    pred_p = _bez_podmienky_cloveka(q.get('predosla') or '')
    kont_n = ' '.join((hv, norm(pred_p), uvod))
    kont_p = ' '.join((veta if hv == h else veta.split(',', 1)[-1], pred_p, q.get('uvod') or ''))
    if re.search(r'\bv citate\b|\bcitat\b', n):          # zmena v citáte = slová človeka
        return 'C', 'C:citat'
    b = _velka_vec(kont_n, kont_p)
    if b:
        return 'B', b
    anaf = _anaforicka(h) and not _VOLBA.search(h)
    if anaf:
        cast = '\n'.join(blok.get('cast') or [])
        m = _B_ZIVY.search(norm(cast) + ' ' + jednotka)
        if m:
            return 'B', 'B:zivy(cast):' + m.group(0)
    if _OBJASNI.search(' '.join((n, predosla, jednotka) + ((norm(blok['cast'][-1]),) if q.get('sam') and blok.get('cast') else ()))):
        return 'D', 'D:spresnenie'
    posl_cast = norm((blok.get('cast') or [''])[-1])
    if _UKON_CLOVEKA.search(' '.join((n, predosla, uvod, posl_cast))):
        return 'D', 'D:ukon-cloveka'
    if _PORADIE.search(h):
        return _vratna(q, blok, anaf, 'A:poradie', vyslovne, clovek)
    if _D_OPYT.match(h):
        return ('C', 'C:ktory') if h.startswith('ktor') else ('D', 'D:opytovacie')
    if _C.search(h):
        return 'C', 'C:verdikt'
    if _DRUHA_OSOBA.search(h) and not re.match(r'^(mam|mozem|mame|mozeme)\b', h):
        return 'D', 'D:druha-osoba'
    if _PONUKA_HLAVA.search(h) and _ALEBO_POCKAT.search(h):
        p = _ponuka(_ALEBO_POCKAT.sub('', h), uvod, dict(q, veta=re.sub(r',?\s*alebo\s.*$', '', veta)))
        if p and not p[1]:
            return _vratna(q, blok, anaf, 'A:alebo-pockat', vyslovne, clovek)
        return 'C', 'C:volba'
    if _VOLBA.search(h) or norm(q.get('nasl') or '').startswith('alebo'):
        if _OTVORENE.search(h):
            return 'C', 'C:otvorena-volba'
        if _S_TVAR.search(h) and not _NIE_S.search(' '.join((n, predosla, uvod))) \
                and not _C_CISLOVANE.search(' '.join((n, predosla, uvod))):
            return 'S', 'S:volba-tvaru'
        return 'C', 'C:volba'
    if _CHCES.search(h) and not _CHCES_ABY.search(h):
        if _S_TVAR.search(h) and not _OTVORENE.search(h) and not _NIE_S.search(' '.join((n, predosla, uvod))):
            return 'S', 'S:chces-tvar'
        return 'C', 'C:chces'
    p = _ponuka(h, uvod, q)
    if p:
        pravidlo, mimo = p
        if mimo:
            return 'O', 'O:ponuka-ine-sloveso(' + ','.join(mimo[:3]) + ')'
        return _vratna(q, blok, anaf, pravidlo, vyslovne, clovek)
    return 'O', 'O:ina'


# úvod zoznamu, ktorý je sám ponukou („Mám teda:“, „Môžem:“) — otázka na konci zoznamu je len jeho posledná položka,
# ponúka sa celý zoznam
_UVOD_PONUKA = re.compile(r'\b(mam|mame|mozem|mozeme|chces,? aby som)\b[^:]{0,60}:\s*$')


_PODPIS = re.compile(r'\b(s pozdravom|s pozdravem|best regards|kind regards|predmet:|subject:|dobry den|dobre rano|'
                     r'vazen\w+ (pan|pani|klient|zakaznik)\w*)|(^|\n)\s*(ahoj|cau|dear|hello|hi)\b[^\n]{0,40},\s*$', re.M)


# Znaky pre CELÚ časť správy nad ponukou (nie len blízke okolie): len výslovné konanie, ktoré partner navrhuje alebo
# opisuje ako krok (neminulý tvar) — zápis do databázy, nasadenie, zverejnenie, push, merge do hlavnej vetvy, živý web
# alebo produkcia. Téma analýzy („reklama ide mimo nákladových súčtov“, „e-shopy posielajú balíky“) ani správa o tom, čo
# sa už stalo („odstránil som“, „odišla k …“), sem nepatrí — tie odsek nad ponukou nesie bežne.
_B_SILNE_CAST = re.compile(
    r'\b((zapis\w*|zapisat|zapisem|nahra\w*) (\w+ ){0,2}do databaz\w*|na (zivy|zivom|zive|produkcn\w*|ostry|ostrom) '
    r'(web\w*|server\w*|prostredi\w*|system\w*)|do produkci\w*|nasad(it|im|ime|zujem|enie|enia|eniu)|'
    r'zverejn(it|im|ime|enie|enia)|uverejn(it|im|ime|enie)|publikovat|pushn(ut|em|eme)|push(u|om)?|'
    r'merg\w* do main\w*|do main(u)?|spust(it|im|ime) (\w+ ){0,2}(kampan|reklam|rozosiel|newsletter)\w*)\b')


def _text_useku(u):
    """Text odseku pre hľadanie znakov — aj riadky tabuľky (bunky), bez podmienky, ktorej podmetom je človek."""
    riadky = []
    for r in u.split('\n'):
        r = r.strip()
        if not r:
            continue
        if _je_tabulka(r):
            if re.fullmatch(r'[|:\- ]+', r):
                continue
            r = ' · '.join(c.strip() for c in r.strip('|').split('|')) + '.'
        riadky.append(r)
    return ' '.join(_bez_podmienky_cloveka(v) for v in _vety(' '.join(riadky)))


def _vyber_nad_znenim(q, blok):
    """Verzia 6: ponuka zapracovať VYBRANÉ očíslované nálezy („Mám body 1 a 3 opraviť?“), keď najbližší očíslovaný
    zoznam nad otázkou má aspoň dve čísla, otázka menuje len niektoré z nich (výber, nie všetky) a aktuálna časť
    nesie znenie — citovaný text („>“, dlhá citácia za dvojbodkou) alebo slovo „znenie / text mailu, zadania …“.
    Vráti vybrané čísla ('1,3') alebo ''."""
    h = norm(q['veta'])
    m = _VYBER_MENO.search(h)
    sel = m.group(3) if m else ''
    if not sel:
        m = _VYBER_HOLE.search(h)
        sel = m.group(1) if m else ''
    if not sel:
        return ''
    vybrane = set()
    for a, b in re.findall(r'(\d{1,2})\.?(?:\s*(?:-|az)\s*(\d{1,2}))?', sel):     # „1 až 4“, „1-4“ = rozsah
        vybrane |= set(range(int(a), int(b) + 1)) if b and int(a) < int(b) <= int(a) + 20 else {int(a)}
    nad = list(blok.get('cast') or []) + [o for o in (blok.get('odseky') or []) if o != q.get('odsek')]
    # čísla NAJBLIŽŠIEHO očíslovaného zoznamu nad otázkou (zdola nahor: odseky bez čísel pred zoznamom sa preskočia,
    # odrážky a odsadené riadky ho nepreruší, prvý iný odsek nad ním ho končí) — „body 1–4“ pod zoznamom 1–4 nie je
    # výber, hoci vyššie v správe je iný zoznam 1–6 (označená otázka ptoth 25. 9., štítok A)
    cisla = set()
    for u in reversed(nad):
        c = {int(x) for x in _CISLO_ZOZNAMU.findall(u)}
        if c:
            cisla |= c
        elif cisla and not re.match(r'^(\s+|\s*[-*•·]\s)', u):
            break
    if len(cisla) < 2 or not vybrane or not vybrane < cisla:
        return ''
    if not any(_C_ZNENIE.search(norm(u)) or _ZNENIE_SLOVO.search(norm(u)) for u in nad + [blok.get('nadpis') or '']):
        return ''
    return ','.join(str(x) for x in sorted(vybrane))


def _vratna(q, blok, anaf, pravidlo, vyslovne=True, clovek=''):
    """Ponuka so slovesom vratnej práce: A, len keď okolie nenesie nič, čo z nej robí vec človeka.
    0) Verzia 5: človek si v tomto ťahu sám určil tempo (_tempo_cloveka: zadal jeden kus z viacerých, obmedzil prácu,
       ohlásil vlastnú kontrolu) — ponuka ďalšieho kroku rešpektuje jeho kontrolu medzi kusmi (C).
    1) Celá aktuálna časť správy (odsek otázky, blok, odseky nad ním po nadpis, nadpis): výslovné rozhodnutie človeka,
       výslovný krok von alebo do živého systému (_B_SILNE_CAST) a návrh listu či mailu (oslovenie, podpis — aj v bloku
       kódu nad otázkou).
    2) Odkazovacia otázka („Mám to takto urobiť?“, „Mám?“, „Mám tie texty prepísať?“) alebo ponukový úvod zoznamu —
       predmet ponuky je nad otázkou: jej odsek, blok, odsek nad ňou (keď stojí sama) a nadpis — všetky znaky veľkej
       veci, zmena čísla, pomenovaná možnosť, citované znenie.
    Odložené vzorky 20. 8. – 30. 9. a 1. 7. – 19. 8. ukázali, že kontext len otázky a vety pred ňou nestačí (commit,
    žurnál, zápis do databázy živého webu, iný projekt, návrh mailu nad otázkou)."""
    if _PODPIS.search(norm(blok.get('surovy') or '')):
        return 'C', 'C:navrh-textu-pre-niekoho(okolie)'
    t = _tempo_cloveka(clovek or '')
    if t:
        return 'C', 'C:tempo-cloveka(' + t + ')'
    if _von_cloveka(clovek or ''):
        return 'B', 'B:von-podla-cloveka'
    # 0b) PS STAVBA 4.94 d (11. 10.): ponuka ROZBORU po správe, v ktorej človek žiadal len vysvetlenie alebo sa pýtal,
    # je jeho vec — rozbor nič nemení a končí sa jeho voľbou. Ponuka úpravy či tvorby po tej istej správe ostáva A.
    vy = _vysvetlenie_cloveka(clovek or '') if _je_rozbor(_hlava(norm(q['veta']))) else ''
    if vy:
        return 'C', 'C:rozbor-po-vysvetleni(' + vy + ')'
    v = _vyber_nad_znenim(q, blok)
    if v:
        return 'C', 'C:vyber-nalezov-nad-znenim(' + v + ')'
    blizke = [q.get('odsek') or '']
    blizke += [o for o in (blok.get('odseky') or []) if o != q.get('odsek')]
    if q.get('sam') and blok.get('cast'):
        blizke.append(blok['cast'][-1])
    if blok.get('nadpis'):
        blizke.append(blok['nadpis'])
    vzdialene = [c for c in (blok.get('cast') or []) if c not in blizke]
    for u in blizke + vzdialene:
        nn = norm(_text_useku(u))
        if vyslovne and _C_VYSLOVNE.search(nn):
            return 'C', 'C:vyslovne(cast)'
        m = _B_SILNE_CAST.search(nn)
        if m:
            return 'B', 'B:krok-von(cast):' + m.group(0)[:30]
    if anaf or _UVOD_PONUKA.search(norm(q.get('uvod') or '')):
        for u in blizke:
            p = _text_useku(u)
            nn = norm(p)
            if _C_ZMENA_CISLA.search(nn):
                return 'C', 'C:zmena-cisla(okolie)'
            if _C_MENI_OBSAH.search(nn):
                return 'C', 'C:meni-obsah(okolie)'
            if _C_CESTA.search(nn):
                return 'C', 'C:volba-cesty(okolie)'
            if _C_OBSAH5_OKOLIE.search(nn) and not _je_rozbor(norm(q['veta'])):
                return 'C', 'C:obsah(okolie)'
            b = _velka_vec(nn, p)
            if b:
                return 'B', b + '(okolie)'
        for u in blizke + list((blok.get('cast') or [])[-3:]):
            if _C_ZNENIE.search(norm(u)):
                return 'C', 'C:znenie(okolie)'
    return 'A', pravidlo


def _ziadosti_bloku(blok):
    """Vety bloku, ktoré žiadajú verdikt človeka bez otáznika: rozkaz („Pripomienkuj stratégiu.“) alebo výslovné
    „rozhodnúť to máš ty“ / „potrebujem tvoj súhlas“ v odsekoch nad základom — legitímna časť bloku."""
    von = []
    for k, ods in enumerate(blok['odseky']):
        for r in ods.split('\n'):
            if _je_tabulka(r):
                continue
            s = _ZOZNAM.sub('', r).strip()
            for v in _vety(s):
                if v.rstrip(' *_').endswith('?'):
                    continue
                nv = norm(v).lstrip('*_ ')
                if _ROZKAZ_VERDIKT.match(nv):
                    von.append(('rozkaz', v))
                elif k < blok.get('zaklad', 0) and _C_VYSLOVNE.search(nv):
                    von.append(('vyslovne', v))
    return von


# ---------------------------------------------------------------------------------------------- opakovanie (rep)
_STOP = set(('mam mame mozem mozeme chces chcete este teraz tieto tento tato toto tych tymto ktore ktora ktory ktoru '
             'alebo otazka otazku otazky odpoved odpovede cakam stale ostava ostavaju zostava otvorena otvorene '
             'predchadzajucej predchadzajucich minulej spravy spravu slovo tvoje tvoju tvoj rozhodnutie rozhodnutia '
             'pripominam potrebujem plati teda takto potom najprv vsetky vsetko nieco preto lebo pretoze '
             'bude budu bolo boli bola jeho ich nich nimi ktorym ktorych ktorou ktorej kedze '
             'len iba tiez dnes zajtra vecer rano treba mozno nove novy nova stare stary sprav urob '
             'tak'.split()))
# partner sám píše, že otázku opakuje — bez toho sa opakovanie neráta (otázka s rovnakou šablónou o ĎALŠEJ veci —
# „Ktorú možnosť vyberáš?“ pri otázke 2 z 8 — nie je opakovanie)
_OPAKUJE = re.compile(
    r'\b(stale|este|nadalej|znova|opat)\b[^.?!]{0,60}\b(cakam|caka|cakaju|plati|ostava|ostavaju|zostava|zostavaju|'
    r'otvoren\w+|visi|stoji|rozbit\w+|bez odpovede|nezodpovedan\w+)\b|'
    r'\b(ostava|ostavaju|zostava|zostavaju)\b[^.?!]{0,60}\b(otazk\w*|rozhodn\w*|odpoved\w*|vec\w*|drobnost\w*|odkaz\w*)\b|'
    r'\botvoren\w+ (je|su|ostava|ostavaju)\b|\b(odkaz|otazka|vec|rozhodnutie)\b[^.?!]{0,60}\b(ostava|zostava)\b|'
    r'\bz (predchadzajuc|minul|predosl)\w+ sprav\w*|\bcakam na (tvoju )?odpoved\w*|\bpripominam\b|\bspred chvile\b')


def kmene(s):
    """Kmene nosných slov (prvých 5 znakov bez diakritiky; čísla celé) — na porovnanie otázok a odpovedí."""
    von = set()
    for w in re.findall(r'[0-9]+[a-z0-9-]*|[a-z]{4,}', norm(s)):
        if w in _STOP:
            continue
        von.add(w if w[0].isdigit() else w[:5])
    return von


def _podobnost(a, b):
    if not a or not b:
        return 0.0, 0
    spol = len(a & b)
    return spol / min(len(a), len(b)), spol


_ODPOVED_ZACIATOK = re.compile(r'^\W*(ano|nie|ok|okej|jasne|suhlasim|suhlas|mozes|nechaj|dobre|super|presne|spravne|jj|'
                               r'yes|no|volim|cesta|moznost|variant)\b')
# rozkaz ako „áno“ na ponuku („urob“, „oprav to“) — len krátky; dlhší rozkaz je nový pokyn („pridaj ten rozcestník pod …“)
_ODPOVED_ROZKAZ = re.compile(r'^\W*(urob|sprav|oprav|pokracuj|daj|vyrad|zahod|zmaz|posli|dopln|pridaj|priprav|implementuj|'
                             r'vykonaj|analyzuj|zapis|pouzi|vyber)\w*\W*(\w+\W*){0,2}$')
# voľba z ponúknutých možností je odpoveď, aj dlhá: „(a)“, „a)“, „(a+) daj“, „B, ale …“, „B“, „2“, „druhú“, „béčko“,
# „potvrdzujem“, „beriem“, „podľa odporúčania“
_ODPOVED_VOLBA = re.compile(
    r'^\W*(\([a-h][+\'"′″]{0,2}\)|[a-h][+]?\)|[a-h]\+?\s*[,.;:-]|[a-h]\+?\s*$|\d{1,2}\s*$|'
    r'(moznost\w*|cest\w+|variant\w*|verzi\w+|bod\w*)\s+\S+|(to |tu |ten |ta )?(prv|druh|tret|stvrt|piat)\w{1,3}\b|'
    r'(acko|becko|cecko|decko)\b|potvrdzujem|beriem|berem|podla (tvojho )?odporucania|odporucan\w+|'
    r'(tak )?ako navrhujes|plati\b)')
# voľba kdekoľvek v dlhšej správe („… a k otázke 1 beriem (a)“)
_ODPOVED_VOLBA_KDEKOLVEK = re.compile(r'\(\s*[a-h][+\'"′″]{0,2}\s*\)|\b(beriem|berem|volim|potvrdzujem|suhlasim)\b|'
                                      r'\b(acko|becko|cecko|decko)\b|'
                                      r'\b(moznost|variant|cest)\w* [a-h1-9]\b')
_CISLA_ODPOVEDE = re.compile(r'(?:^|\n)\s*(\d{1,2})\s*(?:[.):,-]|\s(?=ano|nie|ok|urob|oprav|nechaj|vyrad|zatial))')
# protiotázka alebo žiadosť o vysvetlenie = človek sa otázky dotkol („vysvetli prepínač“, „tri???“, „nedá sa to inak?“)
_PROTIOTAZKA = re.compile(r'\b(pockaj\w*|pockame|neskor|zajtra|najprv|rozdiel\w*)\b|'
                          r'^\W*((hm+|a|no|a teda)\W+)?(aky|aka|ake|kolko|kto|kedy|kde|co|ako|ktor\w*)\b|'
                          r'\?|^\W*(vysvetli|popis|ukaz|preco|ako to|co znamena|co je|nerozumiem|neviem|nechapem|'
                          r'daj (mi )?priklad|priklad)\b')


def odpovedal(otazka, cislo, posledna_v_bloku, clovek_text):
    """Človek na otázku odpovedal alebo sa jej dotkol (citát, číslo položky, voľba, áno/nie, spoločné slová, protiotázka).
    Konzervatívne: pri pochybnosti „áno, dotkol sa“ — opakovanie sa potom nepočíta."""
    h = norm(clovek_text)
    q = norm(otazka['veta'])
    for k in range(0, max(1, len(q) - 24), 8):          # citát otázky („Mám ho poslať? Ano“)
        kus = q[k:k + 25]
        if len(kus) >= 25 and kus in h:
            return True
    if _ODPOVED_VOLBA.search(h.strip()) or _ODPOVED_ZACIATOK.search(h) or _ODPOVED_ROZKAZ.search(h.strip()) \
            or _ODPOVED_VOLBA_KDEKOLVEK.search(h):
        return True
    if _PROTIOTAZKA.search(h.strip()):
        return True
    cisla = set(int(x) for x in _CISLA_ODPOVEDE.findall(h)) | set(int(x) for x in re.findall(r'\bbod\w*\s+(\d{1,2})', h))
    if cislo is not None and (cislo in cisla or re.search(r'\b(vsetk\w*|oba|obe|obidva)\b', h)):
        return True
    ko = kmene(otazka['veta'] + ' ' + otazka.get('predosla', ''))
    kh = kmene(clovek_text)
    _, spol = _podobnost(ko, kh)
    if kh and len(kh) <= 4 and kh <= ko:
        return True                                     # krátka odpoveď slovami z otázky („skriptom“)
    return spol >= 2 or (spol >= 1 and len(ko) <= 3)


def _oznacenia(s):
    """Označenia veci v otázke: „(c″)“, „E4“, „K-13“, čísla. Dve otázky s rovnakou šablónou, ale iným označením
    („Prijímaš E4 …?“ → „Prijímaš A (31, 32) …?“, „Platí (c′)?“ → „Platí (c″)?“) nie sú opakovanie."""
    n = norm(s).replace('‑', '-').replace('‐', '-')
    return set(re.findall(r'\([a-z][^)\s]{0,3}\)|\b[a-z]{1,3}-?\d+[a-z]?\b|\b\d+\b', n)) | \
        set('volba-' + m for m in re.findall(r'\b(?:variant|moznost|cest|verzi)\w*\s+([a-h])\b', n))


def _opakovanie(q, historia):
    """Skoršia koncová otázka sedenia, ktorú táto otázka opakuje a ktorej sa človek nedotkol; inak None.
    Podmienky (všetky): partner sám píše, že otázku opakuje · v sedení je skoršia koncová otázka s prekryvom slov ·
    obe nesú rovnaké označenia (aj vo vete pred otázkou) · každé nosné slovo skoršej otázky je aj v novej (inak je to
    iná vec pod rovnakou šablónou) · človek medzitým písal · žiadna jeho správa sa otázky nedotkla."""
    if not historia:
        return None
    if not _OPAKUJE.search(norm(' '.join((q.get('odsek') or q.get('jednotka') or q.get('predosla') or '', q['veta'],
                                          q.get('uvod') or '')))):
        return None
    kq = kmene(q['veta'] + ' ' + q.get('predosla', ''))
    if len(kq) < 2:
        return None
    for zaznam in reversed(historia):
        for p in zaznam['otazky']:
            if p['trieda'] in ('B', 'R', 'X'):
                continue
            kp = kmene(p['veta'] + ' ' + p.get('predosla', ''))
            sim, spol = _podobnost(kq, kp)
            if sim >= PRAH_PODOBNOSTI and spol >= 2:
                oq = _oznacenia(q['veta'] + ' ' + (q.get('predosla') or ''))
                op = _oznacenia(p['veta'] + ' ' + (p.get('predosla') or ''))
                if oq and op and not (oq <= op or op <= oq):
                    continue                            # iná vec pod rovnakou šablónou otázky („Otázka 2“ → „Otázka 3“)
                if kmene(p['veta']) - kmene(q['veta'] + ' ' + (q.get('predosla') or '')):
                    continue                            # iná vec tými istými slovami („… pre faktúry?“ → „… pre dobropisy?“)
                ludia = zaznam['odpovede']
                if not ludia:
                    return None
                if any(odpovedal(p, p.get('polozka'), p is zaznam['otazky'][-1], t) for t in ludia):
                    return None
                return dict(veta=p['veta'][:160], ts=zaznam.get('ts'))
    return None


def moze_opakovat(text):
    """Rýchla otázka pre háčik: treba čítať históriu sedenia? Len keď partner v koncovom bloku píše, že niečo opakuje."""
    blok = koncovy_blok(text)
    if not blok:
        return False
    return bool(_OPAKUJE.search(norm('\n'.join(blok['odseky']))))


# ---------------------------------------------------------------------------------------------- posudok správy
_UVODZOVKY_UNIK = re.compile(r'"[^"\n]{0,400}"')


def unik(text):
    """Úniková veta partnera kdekoľvek v správe: „O tomto rozhoduje človek, lebo …“ (aj „rozhoduješ ty, lebo …“) s dôvodom.
    Verzia 4: veta v úvodzovkách („Podľa pravidla píšem vetu „O tomto rozhoduje človek, lebo …“ len pri veľkej veci.“) ani
    v citáte (riadok „>“) nie je partnerova úniková veta — neráta sa."""
    t = norm(cisty((text or '')[-MAX_ZNAKOV:]))
    t = '\n'.join(r for r in t.split('\n') if not r.lstrip().startswith('>'))
    return bool(_UNIK.search(_UVODZOVKY_UNIK.sub(' ', t)))


# ---------------------------------------------------------------------------------------------- verzia 5: istá zbytočná
# DVE ÚROVNE (verzia 5, 2. 10.). MERANIE (J-4) ráta každú zbytočnú otázku (zbytocna — pravidlá vyššie). STRÁŽCA ťah
# vráti partnerovi len pri ISTEJ zbytočnej otázke (ista): podmnožina, v ktorej niet ani slabého znaku veci človeka.
# Prečo: väčšinový posudok 18 prípadov našiel 4 oprávnené otázky, ktoré meranie zarátalo (tempo určené človekom,
# výstup pre ľudí mimo projektu, kontrola textu človekom práve teraz), a syntetika recenzenta 48 zo 143 oprávnených
# (cudzie systémy menom, obsahové verdikty). Slovník takéto prípady nikdy nevyčerpá — preto zastavenie stojí na KLADNOM
# dôkaze, že ide o prácu v projekte, a nie len na tom, že sa nenašiel znak veľkej veci. Istá je otázka, keď:
#   · každá otázka bloku je ponuka vratnej práce (A; nie voľba tvaru, nie opakovanie);
#   · v správach človeka v tomto ťahu nie je publikum ani krok von (stretnutie, prezentácia, klient, vedenie,
#     zverejnenie, odoslanie) a v celej správe partnera nie je rozpracovaná komunikácia von (neodoslaný návrh mailu či
#     odpovede, „pošli ju“, „pred odoslaním“) ani podklad na stretnutie či prezentáciu;
#   · v otázke ani vo vete, na ktorú odkazuje, nie je slovo s veľkým písmenom (meno, firma, skratka, cudzí systém) a v jej
#     blízkom okolí (otázka, veta pred ňou, úvod zoznamu, veta, na ktorú odkazuje) nie sú ľudia, publikum ani systémy
#     mimo projektu (_POCHYB_OKOLIE — širšie ako znaky veľkej veci pre meranie);
#   · ponúka sa ROZBOR (rozanalyzovať, začať analýzou, overiť, pozrieť, porovnať — výsledok sa aj tak končí verdiktom
#     človeka), alebo iná práca, ktorej otázka (a pri odkazovacej otázke aj veta, na ktorú odkazuje) nesie len slová
#     zo zoznamu vecí v projekte (_ISTE_SLOVO: analýza, krok, kapitola, tabuľka, preklep, chyba, skúšobná sada, schéma,
#     súbor …), pomocné slová a sloveso ponuky — každé iné obsahové slovo, suma, percento, cena či znak obsahového
#     verdiktu („namiesto“, „hoci“, „tak, aby“; _C_OBSAH5, _POCHYB_OBSAH) je pochybnosť.
# Pri pochybnosti to strážca nezapíše ako nález, len s dôvodom (pole „pochybnost“); meranie otázku ráta ďalej.
_ISTE_SLOVO = re.compile(
    r'(analyz\w*|rozbor\w*|krok\w*|kapitol\w*|cast(i|ou|ami|ach)?|sekci\w*|oddiel\w*|odsek\w*|bod(y|u|om|ov|e|och|mi)?|'
    r'polozk\w*|tabul\w*|stlp\w*|riad(ok|ky|ku|kom|koch|kov|kami)|bunk\w*|subor\w*|priecin\w*|dokument\w*|'
    r'text(y|u|om|e|och|ov|ami)?|nadpis\w*|uvod\w*|prehlad\w*|suhrn\w*|zhrnut\w*|osnov\w*|schem\w*|'
    r'graf(y|u|om|e|ov|och|mi)?|diagram\w*|obrazok|obrazk\w*|legend\w*|popis\w*|poznamk\w*|komentar\w*|odkaz\w*|'
    r'preklep\w*|chyb\w*|oprav(a|y|u|ou|e|ami|ach)|test(y|u|om|e|ov|och|mi)?|sad(a|y|u|e|ou)|skusobn\w*|skript\w*|'
    r'program\w*|kod(u|e|om)?|funkci\w*|modul\w*|regist\w*|metodik\w*|vysledok|vysledk\w*|porovnan\w*|zoznam\w*|'
    r'vzork\w*|sum(a|y|u|ou|e|ami|ach)?|sucet|suct\w*|cisl(o|a|u|om|ach|ami)|cisel|prilo\w*|verzi\w*|navrh\w*|'
    r'koncept\w*|podklad\w*|readme|zaznam\w*|zapisnik\w*|vystup\w*|variant\w*|hodnoteni\w*|citlivostn\w*|'
    r'dat(a|ach|ami)?|udaj\w*|report\w*|doklad\w*|faktur\w*|naklad\w*|mesiac\w*|zdroj\w*|sablon\w*|format\w*|'
    r'nazov|nazv\w*|premenn\w*|datum\w*|citan\w*|'
    r'svg|pdf|csv|xlsx?|html|json|md|png|docx|markdown|ng-zaznamy)')
_ISTE_POMOCNE = set((
    'mam mame mozem mozeme chces chcete aby som sme sa si nech ti tebe mi '
    'to ho ju ich im mu jej jeho nich nej nom nimi nu tie tieto ten tento ta tato tu tuto toto tych tychto tymi tymito tom '
    'tomto tejto tej tomu tomuto toho tohto takto tak takyto taku taketo takeho takej teda '
    'a aj i ako do z zo v vo na o od po pri s so k ku za pre cez medzi podla bez okrem nad pod pred az '
    'rovno rovnako teraz este tiez hned potom najprv najskor dalej znova opat nanovo uz len iba priamo sucasne zaroven '
    'spolu naraz jeden jedna jedno jednu jedneho jednom jednej dva dve dvoch tri troch styri styroch pat oba obe obidva '
    'obidve sam sama same sami je su bol bola bolo boli bude budu ma maju mat byt pustit zacat zacnem zacneme '
    'pada padne padol padla zlyha zlyhal zlyhala zlyhava nesedi nesedia sedi chybaju ci '
    'nasiel nasla nasli zistil zistila zistili ukazal ukazala ukazalo ukazali '
    'pokracovat pokracujem pokracujeme').split())
# tvary pomocných slov (celý tvar, nie predpona — „nas…“ by pustilo „nastavenie“, „nov…“ „novelu“, „hotov…“ „hotovosť“)
_ISTE_POMOCNE_RE = re.compile(
    r'(vsetk|ostatn|zvysn|chybajuc|dals|prv|druh|tret|stvrt|piat|siest|siedm|osm|deviat|desiat|niektor|kazd|tvoj|moj|svoj|'
    r'kratsi|dlhsi|prehladnejs|citateln|strucn|jednoduchs|presnejs|rovnak|spravn|chybn|aktualn|nasledujuc)\w*|'
    r'cel(y|a|e|u|ej|om|eho|ych|ym|ymi)|celkom|celkovo|nov(y|a|e|u|ej|om|eho|ych|ym|ymi)|'
    r'nas(a|e|u|ho|ej|om|im|ich|imi)?|hotov(o|e|y|a|u|ej|om)')
# slová s veľkým písmenom, ktoré nie sú meno ani cudzí systém (tvar výstupu, súbor projektu)
_ISTE_VELKE = set('svg pdf csv xlsx xls html json md png docx readme excel exceli excelu word worde wordu markdown markdowne '
                  'ng'.split())
# celá správa partnera: rozpracovaná komunikácia von a podklad na stretnutie či prezentáciu (norm)
_POCHYB_SPRAVA = re.compile(
    r'\b(neodoslan\w*|neposlan\w*|pred (\w+ )?odoslan\w*|na odoslanie|(posli|odosli|preposli|posles|odosles)\s+(ju|ho|ich|to|'
    r'mu|jej|im)\b|odpovedat vsetkym|(navrh|koncept|draft)\w* (\w+ )?(mail|e-mail|odpoved|sprav|list|pozvank|ponuk)\w*|'
    r'(v|do) (gmail|outlook)\w*|(na|pred|po|pri|pocas|do) (\w+ )?(stretnut\w*|meeting\w*|porad(a|y|e|u|ou|ach)\b|'
    r'prezentaci\w*|schodz\w*|rokovani\w*|workshop\w*|webinar\w*)|prezentovan\w*|prezentova(t|l|la|li)\b|powerpoint\w*|'
    r'slajd\w*|kolegovia (sa )?(\w+ )?(spytaj|spytat|pytat|uvidia|dostanu|precitaju)\w*|pre (cfo|ceo|cio|klient\w*|'
    r'zakaznik\w*|vedenie|manazment|board|investor\w*|predstavenstv\w*|akcionar\w*|pravnik\w*|auditor\w*|urad\w*|'
    r'banku|mesto|ministerstv\w*|riaditel\w*|sefa|sefov\w*|komisi\w*|porot\w*|hodnotitel\w*|verejnost\w*|'
    r'odberatel\w*|partnerov)\b|(zverejn|uverejn|publikuj)\w*|'
    r'(ukazes|ukazete|ukazeme|ukazem|prezentujes|prezentujete|prezentujeme|prezentujem|predstavis|predstavite|'
    r'predstavime|predstavim|rozdame|rozdas|rozdam|posles|poslete|posleme|poslem|odosles|odosleme|odoslem)\b|'
    r'(investor|akcionar)\w*|(sutaz|tender|obstaravan|grant|dotaci|ministerstv|extern|klientsk|zakaznick|partnersk)\w*|'
    r'bank(a|e|u|ou)\b|urad(u|e|om|y|och|mi)?\b|odovzdava\w*|'
    r'verejn\w* (verzi|text|stran|web|cennik|ponuk|profil|prezentaci|dokument|vystup)\w*)')
# správy človeka v tomto ťahu: publikum alebo krok von (norm)
_POCHYB_CLOVEK = re.compile(
    r'\b(stretnut\w*|meeting\w*|porad(a|y|e|u|ou|ach)|prezent\w*|powerpoint\w*|slajd\w*|workshop\w*|webinar\w*|klient\w*|'
    r'zakaznik\w*|cfo|ceo|cio|vedeni(e|u|a)|board\w*|investor\w*|zverejn\w*|uverejn\w*|publikova\w*|odosl\w*|'
    r'posl(at|i|ime|em|eme)|preposl\w*|na web\w*|sef\w*|riaditel\w*|predstavenstv\w*|akcionar\w*|auditor\w*|'
    r'pravnik\w*|bank(a|e|u|ou)|urad\w*|(ide|pojde|odide|posielam|posielame) von)\b')
# správy človeka v tomto ťahu: človek ohlasuje vlastný úkon nad výsledkom („pozriem“, „schválim“, „ukáž mi“, „počkaj“) —
# slabší znak tempa než _tempo_cloveka (meranie); strážca pri ňom nezastaví (norm)
_POCHYB_TEMPO = re.compile(r'\b(schvalim|odsuhlasim|skontrolujem|pozriem|precitam|prejdem|prezriem|posudim|rozhodnem|'
                           r'poviem ti|dam ti vediet|ozvem sa|ukaz mi|posli mi|daj mi|cakaj|pockaj)\b')
# blízke okolie otázky: ľudia, publikum a systémy mimo projektu (širšie ako znaky veľkej veci pre meranie; norm)
_POCHYB_OKOLIE = re.compile(
    r'\b(klient\w*|zakaznik\w*|odberatel\w*|cfo|ceo|cio|cto|coo|vedeni(e|u|a|m)|manazment\w*|manazer\w*|board\w*|'
    r'predstavenstv\w*|investor\w*|sponzor\w*|pravnik\w*|advokat\w*|notar\w*|auditor\w*|znal(ec|ca|covi|com|ci|cov)|'
    r'uctovni(k|ka|cka|cke|cku|ckou|kovi|kom)|kolegov\w*|kolega|kolegu|kolegyn\w*|tim(u|e|om|y|ov)?|oddeleni\w*|'
    r'ludia|ludom|ludi|verejnost\w*|pouzivatel\w*|uzivatel\w*|zamestnan\w*|firm(a|y|e|u|ou)|stretnut\w*|meeting\w*|'
    r'porad(a|y|e|u|ou|ach)|prezent\w*|powerpoint\w*|slajd\w*|workshop\w*|webinar\w*|web(e|u|ov\w*|stran\w*)?|'
    r'mail\w*|e-mail\w*|odosl\w*|posl(at|em|eme|i|ite)|posiela\w*|zverejn\w*|uverejn\w*|publik\w*|ponuk(a|y|u|ou|e)|'
    r'tender\w*|sutaz\w*|obstaravan\w*|zmluv\w*|urad\w*|ministerstv\w*|osobn\w*|citliv(e|y|a|u|ej|ych|ym|ymi|ou)?)\b')


# veta správy, v ktorej niekto mimo projektu výsledok uvidí, dostane, použije alebo posúdi („Tento prehľad uvidí zajtra
# predstavenstvo.“, „Tabuľku si v piatok stiahne audítor.“) — sloveso príjmu a publikum v tej istej vete (norm)
_PRIJEM_SLOVESO = re.compile(r'\b(uvidi|uvidia|stiahne|stiahnu|dostane|dostanu|precita|precitaju|pojde|pojdu|odide|odidu|'
                             r'pouzije|pouziju|ukaze|ukazu|predlozi|predlozia|predlozime|odovzda|odovzdaju|odovzdame|odosle|'
                             r'poslu|posle|zverejni|zverejnia|prezentuje|prezentuju|schvali|schvalia|pripomienkuje|posudi|'
                             r'posudia|skontroluje|skontroluju|podpise|podpisu|dostal|dostali|videl|videli)\b')
_PRIJEM_KTO = re.compile(r'\b(klient\w*|zakaznik\w*|odberatel\w*|cfo|ceo|cio|vedeni\w*|manazment\w*|board\w*|'
                         r'predstavenstv\w*|investor\w*|akcionar\w*|sponzor\w*|pravnik\w*|auditor\w*|uctovni(k|ck)\w*|'
                         r'kolegovia|kolegov|tim\w*|oddeleni\w*|verejnost\w*|ludia|uzivatel\w*|pouzivatel\w*|urad\w*|'
                         r'bank(a|e|u|ou)|mest(o|u|a)|ministerstv\w*|riaditel\w*|sef\w*|komisi\w*|porot\w*|'
                         r'hodnotitel\w*|partneri|dodavatel\w*|ucastnik\w*|student\w*|ziac\w*|ziak\w*|rodic\w*|'
                         r'citatel\w*|navstevnik\w*|posluchac\w*|obyvatel\w*|obcan\w*|pacient\w*|clen\w*|pracovnik\w*|'
                         r'zamestnan\w*|vsetci|ini)\b')
# sloveso príjmu v 3. os. pl. a životné podstatné meno v pluráli („dostanú účastníci“, „uvidia rodičia“)
_PRIJEM_PL = r'(uvidia|stiahnu|dostanu|precitaju|pouziju|ukazu|schvalia|posudia|skontroluju|podpisu|dostali|videli|' \
             r'budu (mat|citat|pouzivat|pozerat))'
_ZIVE_PL = r'[a-z]{3,}(ci|ovia|ti|ri)'
_PRIJEM_PL_BLIZKO = re.compile(r'\b' + _PRIJEM_PL + r'(\s+\w+){0,2}?\s+(?P<a>' + _ZIVE_PL + r')\b|\b(?P<b>' + _ZIVE_PL
                               + r')(\s+\w+){0,2}?\s+' + _PRIJEM_PL + r'\b')


def _koniec_spravy(text):
    """Koniec správy partnera (MAX_ZNAKOV) pre strážcu tak ako v koncovy_blok(): rez na hranici odseku, a keď padne do
    bloku kódu, zvyšok kódu sa uzavrie. Inak by cisty() zahodil prózu medzi ním a ďalším blokom kódu a znak ako „pre
    klienta“ by sa nenašiel (overenie zmluvy 2. 10.: správa s 36 152 znakmi vyšla istá; oprava 3. 10.)."""
    t = text or ''
    if len(t) > MAX_ZNAKOV:
        rez = t.find('\n\n', len(t) - MAX_ZNAKOV)
        t = t[rez + 2:] if rez >= 0 else t[-MAX_ZNAKOV:]
        if t.count('```') % 2:
            t = '```' + t
    return t


def _veta_prijemcu(text):
    """Prvá veta správy (bez kódu), v ktorej výsledok uvidí, dostane či posúdi niekto mimo projektu; '' keď nie je."""
    for ods in _odseky(cisty(_koniec_spravy(text))):
        for v in _vety(' '.join(r.strip() for r in ods.split('\n'))):
            n = norm(v)
            if _PRIJEM_SLOVESO.search(n) and _PRIJEM_KTO.search(n):
                return n
            for m in _PRIJEM_PL_BLIZKO.finditer(n):
                kto = m.group('a') or m.group('b') or ''
                if kto and not _ISTE_SLOVO.fullmatch(kto) and kto not in _ISTE_POMOCNE:
                    return n
    return ''


def _tokeny(n):
    return re.findall(r'[a-z0-9]+(?:[._/-][a-z0-9]+)*', n)


def _iste_slovo(t):
    """Slovo istej ponuky: pomocné slovo, sloveso ponuky, číslo kroku, súbor v projekte alebo vec zo zoznamu _ISTE_SLOVO."""
    if t in _ISTE_POMOCNE or t in _VRATNE_INF or t in _VRATNE_1SG or t in _VRATNE_1PL or t in _VRATNE_PRIC:
        return True
    if re.fullmatch(r'\d{1,3}[a-z]?|[a-h]', t) or _ISTE_POMOCNE_RE.fullmatch(t):
        return True
    if re.search(r'\.[a-z0-9]{1,5}$|/', t):            # súbor alebo cesta v projekte („naklady.md“, „tech/data“)
        return True
    return bool(_ISTE_SLOVO.fullmatch(t))


_KONIEC_VETY = re.compile(r'[.!?:]["“”»)\]*_]*$')


def _velke_slovo5(povodny):
    """Prvé slovo s veľkým písmenom uprostred vety (meno, firma, skratka, cudzí systém) okrem tvaru výstupu, súboru
    a označenia položky („B“, „3b“, „B6“, „O-24“); '' keď nie je. PÔVODNÝ text."""
    zac = True
    for w in cisty(povodny or '').split():
        holy = w.strip('„“"\'*_()[]{}:;,.!?…»«')
        prvy, zac = zac, bool(_KONIEC_VETY.search(w))
        if not holy or prvy or not holy[:1].isupper():
            continue
        if re.fullmatch(r'[A-ZÁČĎÉÍĽŇÓÔŔŠŤÚÝŽ]|[A-Z]{1,2}-?\d+[a-z]?|\d+[A-Za-z]', holy):
            continue
        if re.search(r'[./_]', holy) or norm(holy) in _ISTE_VELKE:
            continue
        return holy
    return ''


def _referent(q, blok):
    """Veta, na ktorú odkazovacia otázka odkazuje: veta pred ňou (bez únikovej vety „O tomto rozhoduje človek, lebo …“
    — vtedy posledná iná veta pred otázkou v tej istej jednotke), inak posledná veta odseku nad blokom; '' keď nie je."""
    pred = q.get('predosla') or ''
    if pred and not _UNIK.search(norm(pred)):
        return pred
    vety = _vety(q.get('jednotka') or '')
    nv = norm(q['veta']).rstrip('?').strip()[:40]
    k = next((i for i, v in enumerate(vety) if nv and nv in norm(v)), 0)
    pred = [v for v in vety[:k] if not _UNIK.search(norm(v))]
    if pred:
        return pred[-1]
    if blok.get('cast'):
        vety = [v for v in _vety(_text_useku(blok['cast'][-1])) if not _UNIK.search(norm(v))]
        if vety:
            return vety[-1]
    return ''


def _pochybnost_otazky(q, blok):
    """Pochybnosť pri jednej ponuke vratnej práce (verzia 5) — '' keď je istá."""
    veta = q['veta']
    n = norm(veta)
    h = _hlava(n)
    if _UVOD_PONUKA.search(norm(q.get('uvod') or '')):
        return 'zoznam ponúk'
    if re.search(r'(^|[\s(`"„])[~/]|\.\./', veta):
        return 'cesta mimo projektu'
    rozbor = _je_rozbor(h)
    odkaz = (_anaforicka(h) and not _VOLBA.search(h)) or bool(re.match(r'^(mam|mozem|mame|mozeme)(\s+(teda|to|tak))?\s*\?', h))
    ref = _referent(q, blok) if odkaz and not rozbor else ''
    if odkaz and not rozbor and not ref:
        return 'odkaz bez predmetu'
    w = _velke_slovo5(veta) or _velke_slovo5(ref)
    if w:
        return 'meno: ' + w
    m = _POCHYB_OKOLIE.search(' '.join((n, norm(q.get('predosla') or ''), norm(q.get('uvod') or ''), norm(ref))))
    if m:
        return 'ľudia mimo: ' + m.group(0)
    m = _C_OBSAH5.search(' '.join((n, norm(ref)))) or _POCHYB_OBSAH.search(' '.join((n, norm(ref))))
    if m:
        return 'obsah: ' + m.group(0)
    # rozbor: len slová otázky (opis kroku, na ktorý odkazuje, rozbor nemení); iná práca: otázka aj veta, na ktorú odkazuje
    for t in _tokeny(h) + ([] if rozbor else _tokeny(norm(ref))):
        if not _iste_slovo(t):
            return 'slovo: ' + t
    return ''


# ---------------------------------------------------------------------------------------------- verzia 5: kladný dôkaz aj pri človeku
# Oprava po overení 2. 10. večer. Overenie našlo falošné zastavenia, ktoré slovník nezachytí: strážca bral každú
# NEZNÁMU formuláciu človeka ako neutrálnu („poďme pomaly – prvý krok a stop“, „teraz to čítam, nechaj to tak“, „nerob to
# naraz, len prvú časť“) a nevidel tempo ani publikum zo skoršieho ťahu (ťahy „áno“ po „priprav prvý graf“). Každá nová
# dávka sond našla nové znenie. Strážca preto zastaví len tam, kde má KLADNÝ DÔKAZ na všetkých stranách:
#   · ponúka sa ROZBOR (rozanalyzovať, začať analýzou, overiť, pozrieť, porovnať — len čítanie, ktoré sa aj tak končí
#     voľbou človeka; vzor „Krok N je hotový … Ďalší je krok N+1 … Mám ho rozanalyzovať?“). Ponuku úpravy, tvorby či
#     ďalšieho kusu strážca nevracia: tam tempo, kontrola či publikum z tohto ani zo skoršieho ťahu menia zbytočnú
#     otázku na oprávnenú a slová na to sa nevyčerpajú. Meranie (J-4) ju ráta ďalej;
#   · správa človeka v ťahu je ČISTO NEUTRÁLNA — každé slovo je súhlas, voľba možnosti, pokyn na vratnú prácu, vec
#     v projekte alebo pomocné slovo („áno“, „súhlasím s možnosťou C“, „implementuj cestu B“, „oprav chybu“,
#     „--prijmi "…"“ s menovkou bez znaku obmedzenia); číslo len ako menovka možnosti či kroku za voľbou („cestu 2“,
#     „pre 3B“), nie kus za pokynom tvorby („sprav krok 1“, „uprav tabuľku 1“). Čokoľvek iné — aj obsah k zvolenej ceste —
#     je pochybnosť: obmedzenie sa dá povedať aj slovami, ktoré žiadny zoznam nepozná („tým je pre mňa etapa uzavretá“);
#   · v skorších správach človeka v sedení nie je výslovné čakanie či dávkovanie („vždy počkaj na mňa“, „po každej časti
#     sa zastav“, „po jednom“) — úzky zoznam, len doplnok: rozbor nič nemení a končí sa voľbou človeka.
# Strážca tak zastavuje zriedka a úzko (vzor „ďalší krok“ po čistom súhlase či voľbe); zvyšok ostáva v meraní.
_NEUTRAL_CLOVEK = set((
    # súhlas, pokračovanie
    'ano ok okej okay oki dobre dobra jasne super fajn vyborne perfektne presne spravne jj suhlasim suhlasime suhlas '
    'diky dakujem dakujeme vdaka prijimam prijmi akceptujem akceptuj beriem berem potvrdzujem schvalujem plati sedi '
    'poriadku mozes mozete podme pod chod pokracuj pokracujte pokracujme pokracovat dalej rovnako takisto '
    'dalsi dalsie dalsiu dalsim dalsom dalsieho dalsich '
    # pokyn na vratnú prácu v projekte, voľba možnosti
    'implementuj zapracuj uprav oprav sprav spravte urob urobte vykonaj aplikuj dopln dopis prepis vloz priprav vytvor '
    'napis pridaj zmen analyzuj zanalyzuj rozanalyzuj analizuj zanalizuj pozri over skontroluj porovnaj vyries dokonci '
    'pouzi zvol vyber volim vyberam zvolim zapis uloz vygeneruj '
    'cesta cestu cesty cestou moznost moznostou moznosti variant variantu varianta variantom alternativu alternativa '
    'riesenie riesenia upravy upravu upravou upravami '
    # pomocné slová — bez časových, obmedzujúcich, radových a „po“ („po častiach“)
    'to ho ju ich im mu jej tie tieto ten tento ta tato tu tuto toto tych tychto tymi tom tomto tej tejto toho tohto '
    'tomu takto tak teda a aj i ako do z zo v vo na o od pri s so k ku za pre cez podla mi si sa je su '
    'vsetko vsetky vsetkych vsetkom cele cely celu celej celom zaroven sucasne tiez nanovo znova opat hned rovno').split())
# veci v projekte (_ISTE_SLOVO), ktoré v správe človeka znamenajú ukážku či kus, nie celok („sprav vzorku“, „len časť“)
_NIE_NEUTRAL_CLOVEK = re.compile(r'vzork\w*|koncept\w*|cast(i|ou|ami|ach)?')
_MOZNOST_SLOVO = re.compile(r'(cest|moznost|variant|alternativ|riesen|verzi|navrh)\w*')
# za týmito slovami je číslo menovka voľby či kroku („pre 3B súhlasím“, „implementuj S2 a M1“), nie kus za pokynom tvorby
_ZA_MENOVKOU = set('pre a implementuj zapracuj aplikuj volim zvolim beriem berem vyberam zvol vyber'.split())
_PRIJATIE = re.compile(r'^-{0,2}(prijmi|prijimam|akceptuj|akceptujem|beriem|potvrdzujem|schvalujem)\b')


def _vzdialenost(a, b):
    """Levenshteinova vzdialenosť (krátke slová)."""
    p = list(range(len(b) + 1))
    for i, x in enumerate(a, 1):
        c = [i]
        for j, y in enumerate(b, 1):
            c.append(min(p[j] + 1, c[j - 1] + 1, p[j - 1] + (x != y)))
        p = c
    return p[-1]


def _slovo_implementuj(w):
    """„implementuj“ aj s preklepom („imlpementuj“, „implemetuj“ — tak ho ľudia píšu)."""
    return w.startswith('implement') or (8 <= len(w) <= 14 and w[:2] == 'im' and _vzdialenost(w, 'implementuj') <= 2)


# kus práce (norm)
_KUS5 = (r'(krok\w*|cast\w*|kapitol\w*|obrazok|obrazk\w*|bod\w*|sekci\w*|slajd\w*|snimk\w*|stran\w*|etap\w*|faz\w*|'
         r'dav\w*|tretin\w*|polovic\w*|stvrtin\w*|kus\w*|blok\w*|odsek\w*|hark\w*|tabul\w*|graf\w*|subor\w*|uloh\w*|'
         r'polozk\w*|zmen\w*|vet(a|u|y|ou)|riad(ok|ky|ku|kom|kov)|text\w*|verzi\w*|navrh\w*|analyz\w*|rozbor\w*)')
# znaky obmedzenia (norm) — rodiny slov; len na menovku v úvodzovkách pri prijatí výsledku: zastavenie, odklad a čas, zápor (aj záporný
# rozkaz a 1. osoba množného čísla „nerobíme“), ďalšie či zvyšné kusy, „len/zatiaľ/najprv“ s kusom, poradie kusu,
# dávkovanie, vlastná práca či kontrola človeka, iní ľudia, odovzdanie von
_OBMEDZ5 = re.compile(
    r'\b(stop\w*|zastav\w*|prestan\w*|dost|staci\w*|pauz\w*|prestavk\w*|prerus\w*|pockaj\w*|pockam\w*|pockat|'
    r'cakaj\w*|vyckaj\w*|odloz\w*|nenahli\w*|ponahlaj\w*|(s|u)?konc(i|ime|ite|it|me|ia)|koniec|brzd\w*|spomal\w*|pomaly|'
    r'postupn\w*|neskor\w*|zajtra|pozajtra|inokedy|nabuduce|buduc\w*|dnes\w*|pondel\w*|utorok|utorku|stred(a|u|e)|'
    r'stvrtok|stvrtku|piatok|piatku|sobot\w*|nedel(a|u|e)|rano|poobede|vecer\w*|pokoj|nateraz|momentalne)\b|'
    r'\b(nie|nic|nicoho|ani|nikam|nikdy)\b|\bne[a-z]*(j|b|d|n|s|z|c)\b|\bne[a-z]+(me|ime|eme|ame|ujme|ujeme)\b|'
    r'\b(dals\w*|zvys\w*|ostatn\w*|nasledujuc\w*|zostavajuc\w*)\s+(\w+\s+)?' + _KUS5 + r'|\bzvysok\w*|'
    r'\b(len|iba|jedine|vylucne|zatial|najprv|najskor|teraz)\s+(\w+\s+){0,2}?(' + _KUS5 +
    r'|prv\w*|jed(en|nu|no|neho|nom|nej)|tent\w*|tuto|toto|tu|to)\b|'
    r'\b(prv|druh|tret|stvrt|piat|siest|jedn|jeden|jedin)\w*\s+(\w+\s+)?' + _KUS5 + r'|'
    r'\bpo (jednom|jednej|castiach|kuskoch|krokoch|bodoch|kapitolach)\b|\b(?P<opak>[a-z]{3,})[a-z]* (za|po) (?P=opak)|'
    r'\b(osobne|sam|sama|sami)\b|\b(precitam|docitam|citam|studujem|listujem|pozeram|prezeram|prechadzam|kontrolujem|'
    r'skontrolujem|porovnavam|revidujem|pripomienkujem|mrknem|kuknem|pozriem|prejdem|prezriem|posudim|schvalim|'
    r'odsuhlasim|rozhodnem|premyslam|premyslim|zvazujem|uvazujem|rozmyslam|rozmyslim|ozvem|napisem|preberieme|'
    r'prejdeme|pozrieme|nechaj\w*|nechame|nechajme|nechme)\b|'
    r'\bna (kontrolu|schvalenie|odsuhlasenie|pozretie|ukazku|skusku|posudenie|pripomienkovanie)\b|\bnech vidim\b|'
    r'\b(koleg\w*|tim(u|e|om)?|spolu s)\b|'
    r'\b(pojde|pojdu|ide|idu|dostane|dostanu|uvidi|uvidia|ukazem|ukazeme|prezentuj\w*|predloz\w*|odovzd\w*|'
    r'posle\w*|posiela\w*|rozosl\w*|zverejn\w*|uverejn\w*)\b')


def _neutralna_sprava(n):
    """Správa človeka (norm) je čisto neutrálna: každé slovo je súhlas, voľba možnosti, pokyn na vratnú prácu, vec
    v projekte alebo pomocné slovo; číslo len samo („2“) alebo ako menovka za voľbou („cestu 2“, „pre 3B“). Prijatie
    výsledku s menovkou v úvodzovkách („--prijmi "krok 2 — súčty" --aj-nemenne“): menovka je text partnera — stačí, že
    nenesie znak obmedzenia (_OBMEDZ5); prepínače príkazu sa vynechajú."""
    if re.search(r'\b([a-z]{3,})[a-z]* (za|po) \1', n):          # „krok za krokom“, „bod po bode“ — dávkovanie
        return False
    s = n.strip()
    if _PRIJATIE.match(s) and '"' in s:
        if any(_OBMEDZ5.search(u) for u in re.findall(r'"([^"]*)"', s)):
            return False
        s = re.sub(r'"[^"]*"', ' ', s)
        s = re.sub(r'\s--[a-z0-9][a-z0-9-]*', ' ', s)
    t = _tokeny(s)
    if not t:
        return False
    for k, w in enumerate(t):
        if w in _NEUTRAL_CLOVEK or _slovo_implementuj(w) or re.fullmatch(r'[a-h]', w):
            continue
        if re.fullmatch(r'\d{1,2}[a-z]?|[a-z]\d{1,2}', w):
            if k == 0 or _MOZNOST_SLOVO.fullmatch(t[k - 1]) or t[k - 1] in _ZA_MENOVKOU or _slovo_implementuj(t[k - 1]):
                continue
            # „implementuj krok 4“ — výkon zvolenej cesty kroku, nie dávkovanie (väčšina 2. 10. pri P03); „sprav krok 4“ nie
            if k >= 2 and t[k - 1] in ('krok', 'kroku', 'krokom') and (_slovo_implementuj(t[k - 2]) or t[k - 2] in _ZA_MENOVKOU):
                continue
            return False
        if _ISTE_SLOVO.fullmatch(w) and not _NIE_NEUTRAL_CLOVEK.fullmatch(w):
            continue
        return False
    return True


# skoršie správy človeka v sedení: výslovné čakanie, zastavenie či dávkovanie (úzky zoznam; norm) — „krok za krokom,
# vždy najprv analyzuj“ sem nepatrí (to je spôsob práce, ktorý ponuku rozboru práve predpisuje)
_SKOR_CAKAJ = re.compile(
    r'\b(pockaj\w*|cakaj\w*|vyckaj\w*|nepokracuj\w*|nechod\w*|zastav sa|zastav to|stoj|stop|'
    r'po (kazdej|kazdom) (\w+ )?(sa )?(zastav|pockaj|stoj|cakaj)\w*|(zastav|pockaj|stoj|cakaj)\w* (sa )?po (kazdej|kazdom)|'
    r'bez (mojho|mojej) (suhlasu|ok|pokynu|slova|odsuhlasenia|kontroly)|(vzdy|zakazdym) (mi )?(najprv |najskor )?'
    r'(ukaz|daj|posli|predloz)|po (jednom|jednej|castiach|kuskoch)|postupne|pomaly|jeden po druhom|jednu po druhej)\b')


# výsledok „pre“ človeka v role, ktorú zoznamy nepoznajú („Podklad pre daňového poradcu je hotový.“, „pre konateľa“,
# „pre notárku“, „pre znalca“) — osoba podľa prípony podstatného mena, nie vec v projekte (oprava po overení, sonda
# BV08: zoznam rolí sa nevyčerpá); celá správa partnera (norm)
_PRE_ROLA = re.compile(r'\bpre\s+(\w+\s+)?(?P<rola>[a-z]{3,}(ca|cu|covi|cov|covia|tel|tela|telovi|telov|telia|telku|telky|'
                       r'nik|nika|nikovi|nikov|nicku|nicky|nikom|ar|ara|arovi|arov|arku|arky|or|ora|orovi|orov|orku|orky|'
                       r'er|era|erovi|erov|ista|istu|istov|isti|kynu|kyne|kyna|ovia|ec|ov)|'
                       r'(poistovn|nemocnic|zastupitelstv|inspektorat|druzstv|nadaci|komor)[a-z]*|obec|obce|obci|skol(u|y|e))\b')
_NIE_ROLA = set('stlpec koniec zaciatok ukazovatel ukazovatela ukazovatelov menovatel citatel delitel slovnik slovnika '
                'dennik dennika dennikov zbornik vestnik casnik filter filtra parameter parametra parametrov meter server '
                'servera folder register registra vektor faktor faktora sektor sektora motor monitor procesor editor koridor '
                'konektor kurzor generator generatora kalkulator indikator indikatora operator validator simulator kalendar '
                'kalendara formular formulara inventar inventara honorar glosar scenar scenara riadkov stlpcov krokov bodov '
                'dokladov suborov mesiacov nakladov udajov zaznamov uctov prikazov typov druhov okruhov obdobi'.split())


def _pre_rola(n):
    """Prvá zmienka „pre <osoba v role>“ v texte (norm) alebo ''."""
    for m in _PRE_ROLA.finditer(n):
        w = m.group('rola')
        if w in _NIE_ROLA or _ISTE_SLOVO.fullmatch(w) or w in _ISTE_POMOCNE or _ISTE_POMOCNE_RE.fullmatch(w):
            continue
        return m.group(0)
    return ''


SKOR_SPRAV = 12                # skoršie správy človeka, ktoré strážca prečíta (pred začiatkom ťahu)
SKOR_HODIN = 12                # a najviac toľko hodín pred začiatkom ťahu


def _skor_z_cloveka(ludia):
    """ludia = udalosti „clovek“ od najnovšej po najstaršiu. Text skorších správ človeka v sedení: pred začiatkom ťahu
    (posledná správa mimo práce partnera), najviac SKOR_SPRAV správ a SKOR_HODIN hodín; správy napísané počas práce
    partnera medzi začiatkom ťahu a poslednou správou sa pridajú tiež."""
    zaklad = None
    von = []
    for k, e in enumerate(ludia):
        if zaklad is None:
            if not e.get('fronta'):
                zaklad = e
            elif k > 0:
                von.append(_orez_cloveka(e.get('text')))
            continue
        a, b = _ts_sek(e.get('ts')), _ts_sek(zaklad.get('ts'))
        if a and b and b - a > SKOR_HODIN * 3600:
            break
        von.append(_orez_cloveka(e.get('text')))
        if len(von) >= SKOR_SPRAV:
            break
    return '\n'.join(reversed(von))


def texty_cloveka_skor(cesta, max_bajtov=16_000_000):
    """Skoršie správy človeka v sedení zo stopy (háčik) — čítaním odzadu; to isté ako texty_cloveka_skor_ud()."""
    try:
        f = open(cesta, 'rb')
    except OSError:
        return ''
    ludia = []
    with f:
        for l in _riadky_odzadu(f, max_bajtov):
            if l is None:
                break
            if not l or len(l) > 5_000_000 or (b'"user"' not in l and b'"queued_command"' not in l):
                continue
            ludia += [e for e in reversed(udalosti_z_riadkov([l])) if e['k'] == 'clovek']
            if len(ludia) > SKOR_SPRAV + 20:
                break
    return _skor_z_cloveka(ludia)


def texty_cloveka_skor_ud(udalosti, i):
    """Skoršie správy človeka v sedení pred koncom ťahu udalosti[i] (meranie, skúšky)."""
    ludia = []
    for j in range(i - 1, -1, -1):
        if udalosti[j]['k'] == 'clovek':
            ludia.append(udalosti[j])
            if len(ludia) > SKOR_SPRAV + 20:
                break
    return _skor_z_cloveka(ludia)


def _istota(von, qs, blok, text, clovek='', skor=None):
    """Je zbytočný blok ISTO zbytočný — je to nález strážcu? (True, '') alebo (False, dôvod pochybnosti).
    clovek=None: posudok len textu (skúšky bez správy človeka) — skúšky správy človeka sa vynechajú; prázdna správa
    človeka je pochybnosť."""
    for o in von:
        if o['trieda'] != 'A' or o['rep']:
            return False, 'nie ponuka vratnej práce (' + ('opakovanie' if o['rep'] else o['trieda']) + ')'
    if not all(_je_rozbor(_hlava(norm(q['veta']))) for q in qs):
        return False, 'nie je ponuka rozboru'
    if clovek is not None:
        if len(clovek) > CLOVEK_ZNAKOV:                 # vložený dokument nie je čistý súhlas — bez normalizácie MB textu
            return False, 'človek: dlhá správa'
        n = norm(clovek)
        if not n.strip():
            return False, 'človek: bez správy'
        if not _neutralna_sprava(n):
            return False, 'človek: nie je len súhlas, voľba či pokyn'
        m = _SKOR_CAKAJ.search(norm(skor or ''))
        if m:
            return False, 'človek skôr: ' + m.group(0)
    nt = norm(cisty(_koniec_spravy(text)))
    m = _POCHYB_SPRAVA.search(nt)
    if m:
        return False, 'správa: ' + m.group(0)
    r = _pre_rola(nt)
    if r:
        return False, 'správa: ' + r
    v = _veta_prijemcu(text)
    if v:
        return False, 'príjemca: ' + v[:60]
    if any(_odsek_caka_na_cloveka(c) for c in blok.get('cast') or []):
        return False, 'iná otázka na človeka v časti nad blokom'
    for q in qs:
        d = _pochybnost_otazky(q, blok)
        if d:
            return False, d
    return True, ''


def posudok(text, historia=None, navrat=False, clovek=None, skor=None):
    """Posudok správy partnera. None, keď správa nekončí otázkou; inak dict:
    navrat=True: ťah je návratom k agende (prvý ťah človeka v stope alebo správa po prestávke NAVRAT_PAUZA_H hodín —
    navrat_v_udalostiach(), v háčiku zaciatok_tahu()['navrat']) — ponuka vratnej práce je vtedy ponuka pri návrate,
    ktorú pilotný text predpisuje (R), nie zbytočná otázka (verzia 3; pri pochybnosti legitímna)
    clovek: správy človeka v tomto ťahu (texty_cloveka_tahu(), v háčiku z zaciatok_tahu()) — tempo, ktoré si človek
    určil sám, a publikum mimo projektu (verzia 5); bez neho (None) sa posudzuje len text — strážca bez správy
    človeka ponuku za istú nepovažuje (prázdna správa = pochybnosť), skúšky textu ju môžu vynechať
    skor: skoršie správy človeka v sedení (texty_cloveka_skor(), texty_cloveka_skor_ud()) — výslovné čakanie či
    dávkovanie z predošlých ťahov (_SKOR_CAKAJ; verzia 5, oprava po overení); len pre nález strážcu (ista)
    trieda             A|S|rep (zbytočná) alebo B|R|X|D|C|O|M (legitímna)
    zbytocna           blok je zbytočný (každá otázka v ňom je A, S alebo opakovanie, žiadna žiadosť o verdikt) a nie je
                       v ňom úniková veta
    zbytocna_bez_unik  to isté bez ohľadu na únikovú vetu · unik — správa nesie únikovú vetu
    len_rep            blok je zbytočný LEN kvôli opakovaniu otázky, ktorá sama nie je A ani S (strážca ju len zapíše)
    vyslovne           blok by bol zbytočný, keby partner nenapísal, že rozhoduje človek („tvoje rozhodnutie“) — na meranie
    ista               verzia 5: blok je ISTO zbytočný — strážca ho zapíše ako nález (podmnožina zbytocna_bez_unik; _istota)
    pochybnost         prečo zbytočný blok nie je istý ('' pri istom a pri legitímnom)
    vysvetlenie        človek v tomto ťahu žiadal len vysvetlenie ('vysvetlenie') alebo sa pýtal ('otazka'), inak ''
                       (_vysvetlenie_cloveka; 4.94 d) — meranie podľa neho delí zbytočné otázky
    otazky             [dict(veta, trieda, pravidlo, rep, polozka, predosla)] · uryvok — prvá zbytočná otázka (≤ 160 znakov)"""
    blok = koncovy_blok(text)
    if not blok:
        return None
    qs = otazky_bloku(blok)
    if not qs:
        return None
    if len(qs) > MAX_OTAZOK:                            # dávka otázok: nie je to ponuka; rozbor by trval sekundy
        return dict(trieda='O', zbytocna=False, zbytocna_bez_unik=False, unik=False, len_rep=False, vyslovne=False,
                    ista=False, pochybnost='', otazky=[dict(veta=qs[-1]['veta'], trieda='O', pravidlo='O:davka', rep=None, polozka=qs[-1]['polozka'],
                                 predosla=qs[-1]['predosla'])], rozkazy=[], rep=None,
                    uryvok=re.sub(r'\s+', ' ', qs[-1]['veta'])[:160])
    von = []
    for q in qs:
        tr, pr = trieda_otazky(q, blok, clovek=clovek or '')
        if navrat and tr == 'A':
            tr, pr = 'R', 'R:navrat-kontext(' + pr + ')'
        rep = _opakovanie(q, historia) if tr not in ('B', 'R', 'X') and historia and not navrat else None
        o = dict(veta=q['veta'], trieda=tr, pravidlo=pr, rep=rep, polozka=q['polozka'], predosla=q['predosla'])
        if pr.startswith('C:vyslovne'):
            bv = trieda_otazky(q, blok, vyslovne=False, clovek=clovek or '')[0]
            o['bez_vyslovne'] = 'R' if navrat and bv == 'A' else bv
        von.append(o)
    ziadosti = _ziadosti_bloku(blok)
    rozkazy = [v for d, v in ziadosti if d == 'rozkaz']

    def _z(o, bez_vyslovne=False):
        tr = o.get('bez_vyslovne', o['trieda']) if bez_vyslovne else o['trieda']
        return tr in ('A', 'S') or bool(o['rep'])

    zbyt = [o for o in von if _z(o)]
    legit = [o for o in von if not _z(o)]
    if legit or ziadosti:
        hl = min((o['trieda'] for o in legit), key='BRXDCO'.index) if legit else 'C'
        trieda = 'M' if zbyt else hl
        zbytocna = False
    else:
        if any(o['trieda'] == 'A' for o in zbyt):
            trieda = 'A'
        elif any(o['trieda'] == 'S' for o in zbyt):
            trieda = 'S'
        else:
            trieda = 'rep'
        zbytocna = True
    len_rep = zbytocna and not all(o['trieda'] in ('A', 'S') for o in von)
    # koľko ponúk kryje znak „partner sám píše, že rozhoduje človek“: blok by bol zbytočný bez neho
    vyslovne = (not zbytocna) and not rozkazy and all(_z(o, True) for o in von) and \
        (any('bez_vyslovne' in o for o in von) or len(ziadosti) > len(rozkazy))
    je_unik = unik(text)
    ista, pochybnost = _istota(von, qs, blok, text, clovek, skor) if zbytocna else (False, '')
    prva = next((o for o in von if _z(o)), None) or next((o for o in von if 'bez_vyslovne' in o), None)
    return dict(trieda=trieda, zbytocna=zbytocna and not je_unik, zbytocna_bez_unik=zbytocna, unik=je_unik,
                len_rep=len_rep, vyslovne=vyslovne, ista=ista, pochybnost=pochybnost, otazky=von,
                vysvetlenie=_vysvetlenie_cloveka(clovek or ''),
                rozkazy=[v for _d, v in ziadosti],
                rep=next((o['rep'] for o in von if o['rep']), None),
                uryvok=re.sub(r'\s+', ' ', (prva['veta'] if prva else von[-1]['veta']))[:160])


# ---------------------------------------------------------------------------------------------- stopa sedenia
def _akter():
    """Jediný určovateľ hlasu (ng-akter.py) alebo None, keď sa nenačíta (sľub 1.16)."""
    try:
        import importlib, os, sys as _s
        tu = os.path.dirname(os.path.abspath(__file__))
        if tu not in _s.path:
            _s.path.insert(0, tu)
        return importlib.import_module('ng-akter')
    except BaseException:
        return None


def _zacatky_nastroja():
    """Začiatky textov od nástroja — od určovateľa; zoznam nižšie je len náhrada, keď sa modul nenačíta."""
    _AK = _akter()
    if _AK is not None:
        try:
            return _AK.zacatky_nastroja()
        except Exception:
            pass
    return ('<task-notification>', '<system-reminder>', '<command-name>', '<command-message>', '<local-command',
            '<user-prompt-submit-hook>', 'Caveat: The messages below', 'This session is being continued',
            '<ide_selection>', '<ide_opened_file>', '<bash-input>', '<bash-stdout>', '<bash-stderr>')


NASTROJ_ZACIATOK = _zacatky_nastroja()
_NASTROJ_RE = re.compile(r'^\s*(<task-notification>|<system-reminder>|<command-name>|<local-command|'
                         r'Caveat: The messages below|This session is being continued)')
_EVIDENCIA = re.compile(r'ng-kdesom\.py|ng-zapis\.py|ng-audit\.py|NG-zaznamy\.md')


def _text_bloku(c):
    if isinstance(c, str):
        return c
    if isinstance(c, list):
        return '\n'.join(b.get('text', '') for b in c if isinstance(b, dict) and b.get('type') == 'text')
    return ''


_IDE_KONTEXT = ('<ide_opened_file>', '<ide_selection>')


def _text_cloveka(c):
    """Text správy človeka bez kontextu, ktorý pred ňu vkladá VS Code (otvorený súbor, výber) ako samostatný blok.
    Bez toho sa správa „<ide_opened_file>…“ + „Ahoj, ideme ďalej.“ brala celá ako výstup nástroja: návrat k agende po
    prestávke sa nerozpoznal (ponuka pri návrate → A), kľúč ťahu bol z predošlého ťahu a meranie zlúčilo dva ťahy."""
    if isinstance(c, list):
        return '\n'.join(b.get('text', '') for b in c if isinstance(b, dict) and b.get('type') == 'text'
                         and not (b.get('text') or '').lstrip().startswith(_IDE_KONTEXT))
    return _text_bloku(c)


def udalosti_z_riadkov(riadky):
    """Udalosti sedenia z riadkov stopy (jsonl) v poradí: dict(k=clovek|text|nastroj|hak|prerusenie, ts, …).
    Hlas človeka ako ng-akter.py (človek ≠ výsledok nástroja, háčik, pripomienka, príkaz, podagent) a navyše správa,
    ktorú človek napísal POČAS práce partnera: tú Claude Code nezapíše ako riadok „user“, ale ako prílohu
    queued_command (30. 9.: 12–42 % správ človeka podľa osoby) — bez nej by „človek neodpovedal“ neplatilo.
    Riadky s výsledkami nástrojov sa neparsujú — stopa má desiatky MB a háčik musí byť rýchly."""
    import json
    von = []
    for l in riadky:
        # rýchly filter bez JSON (aj bez dekódovania): Claude Code píše kompaktne ("type":"user"), s medzerou tiež prejde
        if isinstance(l, bytes):
            if b'"assistant"' not in l and b'"user"' not in l and b'"queued_command"' not in l:
                continue
            if b'"tool_result"' in l and b'Stop hook feedback' not in l[:3000]:
                continue
            l = l.decode('utf-8', errors='ignore')
        else:
            if '"assistant"' not in l and '"user"' not in l and '"queued_command"' not in l:
                continue
            if '"tool_result"' in l and 'Stop hook feedback' not in l[:3000]:
                continue
        try:
            o = json.loads(l)
        except Exception:
            continue
        if not isinstance(o, dict) or o.get('isSidechain'):
            continue
        typ = o.get('type')
        ts = o.get('timestamp') or ''
        if typ == 'attachment':
            a = o.get('attachment')
            if isinstance(a, dict) and a.get('type') == 'queued_command' and a.get('commandMode') == 'prompt' \
                    and not a.get('isMeta'):
                org = a.get('origin')
                kind = org.get('kind') if isinstance(org, dict) else org
                t = _text_cloveka(a.get('prompt')).strip()
                # 19. 10. (sľub 1.16): či je v prílohe hlas človeka, určuje jediný určovateľ; filtre nižšie sú náhrada
                _AK = _akter()
                if _AK is not None:
                    try:
                        tf = _AK.clovek_vo_fronte(o)
                        kind = 'human' if tf else 'nie-clovek'
                        t = tf.strip() if tf else ''
                    except Exception:
                        pass
                if kind == 'human' and t and not t.startswith(NASTROJ_ZACIATOK) and not _NASTROJ_RE.search(t):
                    von.append(dict(k='clovek', ts=ts or a.get('timestamp') or '', text=t, origin='human',
                                    ep=o.get('entrypoint') or '', fronta=True))
            continue
        c = (o.get('message') or {}).get('content') if isinstance(o.get('message'), dict) else None
        if typ == 'assistant':
            if not isinstance(c, list):
                continue
            for b in c:
                if not isinstance(b, dict):
                    continue
                if b.get('type') == 'text' and (b.get('text') or '').strip():
                    von.append(dict(k='text', ts=ts, text=b['text'], ep=o.get('entrypoint') or ''))
                elif b.get('type') == 'tool_use':
                    inp = b.get('input') or {}
                    cmd = str(inp.get('command', '')) if isinstance(inp, dict) else ''
                    ciel = str(inp.get('file_path', '')) if isinstance(inp, dict) else ''
                    von.append(dict(k='nastroj', ts=ts, meno=b.get('name', ''),
                                    evidencia=bool(_EVIDENCIA.search(cmd[:3000] + ' ' + ciel))
                                    or b.get('name') in ('TodoWrite', 'TaskCreate', 'TaskUpdate')))
        elif typ == 'user':
            if isinstance(c, list) and any(isinstance(b, dict) and b.get('type') == 'tool_result' for b in c):
                continue
            t = _text_cloveka(c).strip()
            if not t:
                continue
            if t.startswith('Stop hook feedback'):       # spätná väzba háčika má isMeta — rozpoznať ju skôr
                von.append(dict(k='hak', ts=ts, text=t[:600], otazky='NG, otázky:' in t))
            elif o.get('isMeta'):
                continue
            elif t.startswith('[Request interrupted'):
                von.append(dict(k='prerusenie', ts=ts))
            elif t.startswith(NASTROJ_ZACIATOK) or _NASTROJ_RE.search(t):
                continue
            else:
                # 19. 10. (sľub 1.16): posledné slovo má určovateľ — pozná aj beh stroja (claude -p), podagenta,
                # skúšobnú správu v živom sedení a súhrn po zhutnení, ktoré vlastný filter nepoznal.
                _AK = _akter()
                if _AK is not None:
                    try:
                        if _AK.akter(o) != 'clovek':
                            continue
                    except Exception:
                        pass
                von.append(dict(k='clovek', ts=ts, text=t, origin=o.get('turnOrigin'), ep=o.get('entrypoint') or ''))
    return von


def citaj_koniec(cesta, bajtov=2_000_000):
    """Riadky z konca stopy (prvý, možno neúplný riadok sa zahodí)."""
    with open(cesta, 'rb') as f:
        f.seek(0, 2)
        od = max(0, f.tell() - bajtov)
        f.seek(od)
        data = f.read()
    riadky = data.split(b'\n')
    return riadky[1:] if od else riadky


def udalosti_zo_stopy(cesta, bajtov=2_000_000):
    return udalosti_z_riadkov(citaj_koniec(cesta, bajtov))


NAVRAT_PAUZA_H = 3             # správa človeka po takejto prestávke je návrat k agende (ponuka pri návrate = R)


def _ts_sek(ts):
    import datetime
    try:
        return datetime.datetime.fromisoformat(str(ts).replace('Z', '+00:00')).timestamp()
    except Exception:
        return None


def _riadky_odzadu(f, max_bajtov=64_000_000, kus=1 << 20):
    """Riadky otvoreného súboru (bajty, bez konca riadku) od konca k začiatku; prečíta najviac max_bajtov. Riadok dlhší
    než kus sa poskladá z viacerých kusov; neúplný prvý riadok okna (limit) sa nevráti. Na začiatku súboru vráti None."""
    f.seek(0, 2)
    poz = f.tell()
    zaciatok = b''                # začiatok riadku z neskoršieho kusu — jeho zvyšok je v kuse pred ním
    precitane = 0
    while poz > 0 and precitane < max_bajtov:
        od = max(0, poz - kus)
        f.seek(od)
        data = f.read(poz - od)
        precitane += len(data)
        poz = od
        casti = (data + zaciatok).split(b'\n')
        if od > 0:
            zaciatok = casti[0]
            casti = casti[1:]
            if len(zaciatok) > 16_000_000:           # extrémne dlhý riadok — ďalej sa nečíta
                return
        else:
            zaciatok = b''
        for l in reversed(casti):
            yield l
    if poz == 0:
        yield None                                      # začiatok súboru je prečítaný


def _ts_riadku(l):
    if len(l) < 3_000_000:
        try:
            import json
            t = json.loads(l).get('timestamp')
            if t:
                return t
        except Exception:
            pass
    m = list(re.finditer(rb'"timestamp":"([^"]{10,40})"', l))
    return m[-1].group(1).decode('ascii', 'ignore') if m else None


def zaciatok_tahu(cesta, max_bajtov=64_000_000, kus=1 << 20, navrat_bajtov=8_000_000):
    """Posledná správa človeka v stope (udalosť „clovek“: ts, origin, ep) — čítaním ODZADU po kusoch, aj za hranicou
    2 MB (dlhý ťah s veľkými výstupmi nástrojov). Skončí pri prvom náleze; None, keď v posledných max_bajtov nie je.
    Navyše (verzia 3), vždy podľa poslednej správy človeka MIMO práce partnera (správa napísaná počas práce — fronta —
    ťah nezačína): prvy — pred ňou nie je v stope žiadny text partnera (prvý ťah sedenia); pauza_h — hodiny od
    poslednej činnosti partnera pred ňou (text alebo nástroj; riadky, ktoré Claude Code zapíše pri obnovení sedenia, sa
    nerátajú); navrat — prvý ťah alebo prestávka aspoň NAVRAT_PAUZA_H hodín. Rovnako to počíta navrat_v_udalostiach()
    pre meranie."""
    try:
        f = open(cesta, 'rb')
    except OSError:
        return None
    with f:
        riadky = _riadky_odzadu(f, max_bajtov, kus)
        clovek = None
        for l in riadky:
            if not l or len(l) > 5_000_000 or (b'"user"' not in l and b'"queued_command"' not in l):
                continue
            for e in udalosti_z_riadkov([l]):
                if e['k'] == 'clovek':
                    clovek = e
                    break
            if clovek:
                break
        if not clovek:
            return None
        zaklad = clovek                                 # návrat sa posudzuje podľa poslednej správy mimo práce partnera
        if clovek.get('fronta'):
            zaklad = None
            stred = []                                  # správy napísané počas práce medzi začiatkom ťahu a poslednou
            for l in riadky:
                if l is None:
                    break
                if not l or len(l) > 5_000_000 or (b'"user"' not in l and b'"queued_command"' not in l):
                    continue
                ud_l = udalosti_z_riadkov([l])
                zaklad = next((e for e in ud_l if e['k'] == 'clovek' and not e.get('fronta')), None)
                if zaklad:
                    break
                stred += [e.get('text') or '' for e in reversed(ud_l) if e['k'] == 'clovek' and e.get('fronta')]
            if zaklad is None:
                clovek.update(prvy=False, pauza_h=None, navrat=False)
                return clovek
            clovek['zaklad_text'] = zaklad.get('text') or ''   # verzia 5: tempo človeka zo začiatku ťahu aj z fronty
            clovek['fronta_texty'] = stred[::-1]        # oprava 3. 10.: aj správy z fronty pred poslednou, v poradí
        pred_ts = None
        text_pred = False
        zaciatok_suboru = False
        prec = 0
        for l in riadky:
            if l is None:
                zaciatok_suboru = True
                break
            prec += len(l) + 1
            if prec > navrat_bajtov:
                break
            if b'"type":"assistant"' not in l or b'"isSidechain":true' in l:
                continue                                # prestávka sa meria od poslednej činnosti partnera, nie od
            if pred_ts is None:                         # riadkov, ktoré Claude Code zapíše pri obnovení sedenia
                pred_ts = _ts_riadku(l)
            if b'"type":"text"' in l:
                text_pred = True
            if pred_ts is not None and text_pred:
                break
        if not text_pred and not zaciatok_suboru:
            text_pred = True                            # nedočítané do začiatku — nevieme, radšej „nie prvý ťah“
    a, b = _ts_sek(pred_ts), _ts_sek(zaklad.get('ts'))
    clovek['prvy'] = not text_pred
    clovek['pauza_h'] = round((b - a) / 3600, 2) if a and b else None
    clovek['navrat'] = clovek['prvy'] or bool(a and b and b - a >= NAVRAT_PAUZA_H * 3600)
    return clovek


def navrat_v_udalostiach(udalosti, i):
    """Je koniec ťahu udalosti[i] v návrate k agende? To isté ako zaciatok_tahu()['navrat']: posledná správa človeka pred
    ním mimo práce partnera (správa napísaná počas práce — fronta — sa preskočí) je prvá v stope (pred ňou žiadny text
    partnera) alebo prišla po prestávke aspoň NAVRAT_PAUZA_H hodín."""
    j = i - 1
    while j >= 0 and (udalosti[j]['k'] != 'clovek' or udalosti[j].get('fronta')):
        j -= 1
    if j < 0:
        return False
    prvy_text = next((k for k, u in enumerate(udalosti) if u['k'] == 'text'), None)
    if prvy_text is None or prvy_text > j:
        return True
    p = j - 1                                           # posledná činnosť partnera pred správou (text alebo nástroj)
    while p >= 0 and udalosti[p]['k'] not in ('text', 'nastroj'):
        p -= 1
    a, b = (_ts_sek(udalosti[p].get('ts')) if p >= 0 else None), _ts_sek(udalosti[j].get('ts'))
    return bool(a and b and b - a >= NAVRAT_PAUZA_H * 3600)


def texty_cloveka_tahu(udalosti, i):
    """Správy človeka, ktoré patria k ťahu končiacemu udalosti[i] (verzia 5 — tempo človeka a publikum): posledná správa
    mimo práce partnera (začiatok ťahu) a za ňou VŠETKY správy napísané počas práce, v poradí (oprava 3. 10.: predtým len
    posledná — „zatiaľ len prvý report“ a po ňom „ďakujem“ vyšlo ako bez tempa). To isté vidí háčik cez zaciatok_tahu()
    (zaklad_text + fronta_texty + text)."""
    fronta = []
    for j in range(i - 1, -1, -1):
        u = udalosti[j]
        if u['k'] != 'clovek':
            continue
        if not u.get('fronta'):
            return '\n'.join(x for x in [u.get('text') or ''] + [f.get('text') or '' for f in reversed(fronta)] if x)
        fronta.append(u)
    return ''


def _texty_zaciatku(zac):
    """Správy človeka v ťahu z výsledku zaciatok_tahu() — to isté ako texty_cloveka_tahu() v meraní."""
    if not zac:
        return ''
    if 'zaklad_text' not in zac and zac.get('fronta'):
        return ''                                       # začiatok ťahu mimo fronty sa nenašiel — ako v meraní
    return '\n'.join(x for x in [zac.get('zaklad_text') or ''] + list(zac.get('fronta_texty') or []) + [zac.get('text') or '']
                     if x)


def posudok_konca(vstup):
    """Posudok konca ťahu zo vstupu Stop háčika — JEDNO rozhodnutie pre strážcu otázok aj pre ng-kdesom-koniec.sh:
    text (last_assistant_message, inak posledný text zo stopy), história sedenia, keď partner píše, že otázku opakuje,
    beh bez človeka (podagent, sdk, ťah začatý strojom) a návrat k agende (zaciatok_tahu()). Vráti dict(p, zac, text),
    alebo None, keď niet čo posúdiť (bez otázky na konci, beh stroja, podagent) alebo koniec nie je zbytočný ani bez
    únikovej vety. zac = posledná správa človeka (ts = kľúč ťahu) alebo None, keď sa v stope nenašla."""
    import os
    if not isinstance(vstup, dict) or vstup.get('agent_id'):
        return None
    if os.environ.get('CLAUDE_CODE_ENTRYPOINT', '').startswith('sdk'):
        return None
    stopa = vstup.get('transcript_path')
    stopa = stopa if isinstance(stopa, str) else ''
    if '/subagents/' in stopa:
        return None
    ma_stopu = bool(stopa) and os.path.isfile(stopa)
    text = vstup.get('last_assistant_message')
    if not isinstance(text, str):
        if not ma_stopu:
            return None
        text = next((e['text'] for e in reversed(udalosti_zo_stopy(stopa, 2_000_000)) if e['k'] == 'text'), '')
    if not text or not konci_otazkou(text):
        return None                                     # rýchla cesta: bez otázky na konci nič nečíta
    p = posudok(text)
    if not p:
        return None
    hist = None
    if moze_opakovat(text) and ma_stopu:                # história len keď partner píše, že otázku opakuje
        ud = udalosti_zo_stopy(stopa, 2_000_000)
        idx = next((i for i in range(len(ud) - 1, -1, -1) if ud[i]['k'] == 'text' and ud[i]['text'] == text), len(ud))
        hist = historia(ud, po=idx)
        p = posudok(text, hist) or p
    if not p['zbytocna_bez_unik']:
        return None
    zac = zaciatok_tahu(stopa) if ma_stopu else None    # posledná správa človeka čítaním odzadu (aj za hranicou 2 MB)
    if zac and (zac.get('origin') == 'sdk' or str(zac.get('ep') or '').startswith('sdk')):
        return None
    # návrat k agende: ponuka pri návrate je predpísaná (R); verzia 5: tempo a publikum zo správ človeka v tomto ťahu
    # a v skorších správach sedenia (texty_cloveka_skor — číta sa len pri zbytočnom konci)
    skor = texty_cloveka_skor(stopa) if (ma_stopu and zac) else ''
    p = posudok(text, hist, navrat=bool(zac and zac.get('navrat')), clovek=_texty_zaciatku(zac), skor=skor) or p
    if not p['zbytocna_bez_unik']:
        return None
    return dict(p=p, zac=zac, text=text)


def posudok_v_stope(udalosti, i):
    """Posudok konca ťahu udalosti[i] tak, ako ho vidí háčik a meranie: s históriou sedenia (opakovanie), návratom
    a správami človeka v ťahu aj skôr v sedení (verzia 5)."""
    return posudok(udalosti[i]['text'], historia(udalosti, po=i), navrat=navrat_v_udalostiach(udalosti, i),
                   clovek=texty_cloveka_tahu(udalosti, i), skor=texty_cloveka_skor_ud(udalosti, i))


# ---------------------------------------------------------------------------------------------- rozhodnutie strážcu
# Rozhodnutie háčika strážcu (ng-otazky-straz.py). Zastavovanie zrušené 10. 10. 2026 (PS, STAVBA 4.87 d) — strážca ťah
# nevracia, len zapisuje; súhra s ng-kdesom-koniec.sh (bod 4, strazca_zastavi) tým odpadla a je odobraná. Stav sedenia
# (uznané úniky, nálezy ťahov) je súbor v $NG_OTAZKY_ZNACKY (inak /tmp); píše ho LEN háčik strážcu.


def _znacky_strazcu():
    import os
    return os.environ.get('NG_OTAZKY_ZNACKY') or '/tmp'


def _sha12(s):
    import hashlib
    return hashlib.sha1((s or '').encode('utf-8', 'ignore')).hexdigest()[:12]


def stav_strazcu(sid):
    """Stav strážcu pre sedenie: (súbor, dict(uniky, najdene)).
    uniky = kľúče ťahov s uznanou únikovou vetou · najdene = kľúče ťahov, v ktorých strážca už nález zapísal (pole
    „ista“ druhý raz v tom istom ťahu = False, akcia „uz-raz“). Staré kľúče (zastavene, by_zastavene) sa od 10. 10.
    nečítajú — zastavovanie je zrušené a stav je len značka sedenia v /tmp."""
    import os
    import json
    f = os.path.join(_znacky_strazcu(), f'ng-otazky-straz-{os.getuid()}-{sid or "bez-sid"}.json')
    d = {}
    try:
        with open(f, encoding='utf-8') as h:
            d = json.load(h)
    except Exception:
        d = {}
    if not isinstance(d, dict):
        d = {}
    return f, dict(uniky=list(d.get('uniky') or []), najdene=list(d.get('najdene') or []))


def uloz_stav_strazcu(f, d):
    """Zápis stavu cez dočasný súbor a premenovanie (súbežný čitateľ nikdy nevidí polovičný súbor). Chyba = ticho."""
    import os
    import json
    try:
        t = f + f'.{os.getpid()}.novy'
        with open(t, 'w', encoding='utf-8') as h:
            json.dump(d, h)
        os.replace(t, f)
    except Exception:
        pass


def rozhodnutie_strazcu(vstup, k=None):
    """Čo strážca urobí s koncom ťahu — BEZ zápisu (stav len číta). None = pustiť bez riadku; inak dict:
      akcia        zapis (koniec je zbytočný; či je to nález, hovorí „ista“) · unik · pokracovanie
      ista         koniec je ISTO zbytočný a v tomto ťahu ešte nezapísaný = NÁLEZ strážcu, jeho skutočná práca (False
                   pri bez-tahu, pochybnosti, druhom náleze toho istého ťahu, len-opakovaní, úniku, pokračovaní)
      cesta        prečo nález je alebo nie je: najdena · uz-raz · rep-len-zapis · bez-tahu · pochybnost
      pochybnost   verzia 5: zbytočný koniec nie je istý (p['ista'] False) — nálezom to nie je, háčik zapíše druh
                   pochybnosti (bez slov zo správy: „správa“, „človek“, „meno“, „slovo“, „obsah“, „ľudia mimo“ …)
      nad_limit    správa nesie únikovú vetu, ale limit UNIKOV_NA_SEDENIE je v sedení vyčerpaný
      novy_unik    úniková veta sa uznáva prvý raz v tomto ťahu (háčik ju pripíše do stavu)
      p, zac, sid, tah, kluc, sprava (hash textu), subor, stav
    Kľúč ťahu = čas poslednej správy človeka + sedenie; keď sa začiatok ťahu nenašiel, kľúč je hash správy (inak by mali
    všetky ťahy bez začiatku jeden kľúč a limit únikov by neplatil). k = hotový posudok_konca(vstup), ak ho volajúci má.
    Zastavovanie zrušené 10. 10. 2026 (PS, STAVBA 4.87 d): žiadny režim, žiadny blok — strážca vždy len zapisuje."""
    if not isinstance(vstup, dict):
        return None
    if k is None:
        k = posudok_konca(vstup)
    if not k:
        return None
    p, zac = k['p'], k['zac']
    sid = str(vstup.get('session_id') or '')[:8] if isinstance(vstup.get('session_id'), (str, int)) else ''
    tah = (zac or {}).get('ts') or ''
    sprava = _sha12(k.get('text') or '')
    kluc = _sha12(tah + '|' + sid) if tah else _sha12('sprava:' + sprava + '|' + sid)
    f, stav = stav_strazcu(sid)
    r = dict(p=p, zac=zac, sid=sid, tah=tah, kluc=kluc, sprava=sprava, subor=f, stav=stav, cesta='',
             ista=False, nad_limit=False, novy_unik=False, pochybnost='')
    if vstup.get('stop_hook_active') is True:
        r['akcia'] = 'pokracovanie'                   # pokračovanie po zásahu háčika — nikdy nezastaviť
        return r
    if p['unik']:
        if kluc in stav['uniky'] or len(stav['uniky']) < UNIKOV_NA_SEDENIE:
            r['akcia'] = 'unik'
            r['novy_unik'] = kluc not in stav['uniky']
            return r
        r['nad_limit'] = True                         # tretí a ďalší únik v sedení sa neberie
    uz = stav['najdene']
    if p['len_rep']:
        cesta = 'rep-len-zapis'
    elif not tah:
        cesta = 'bez-tahu'                            # nevieme, ktorý ťah — nálezom to nie je
    elif not p.get('ista'):
        cesta = 'pochybnost'                          # verzia 5: zbytočná, ale nie istá — meranie ju ráta, nález nie
        # do stopy háčikov len druh pochybnosti (pred dvojbodkou), nie slovo zo správy partnera ani človeka
        r['pochybnost'] = (p.get('pochybnost') or 'nie je istá').split(':')[0].strip()
    elif kluc in uz:
        cesta = 'uz-raz'
    else:
        cesta = 'najdena'
    r['cesta'] = cesta
    r['ista'] = cesta == 'najdena'
    r['akcia'] = 'zapis'
    return r


def zastavenia(udalosti):
    """Konce ťahov: [(index textu partnera, hak_po)] — po texte prišiel človek, spätná väzba háčika alebo koniec stopy.
    hak_po = háčik ťah zastavil a partner pokračoval."""
    von = []
    n = len(udalosti)
    for i, e in enumerate(udalosti):
        if e['k'] != 'text':
            continue
        nasl = udalosti[i + 1] if i + 1 < n else None
        if nasl is None or nasl['k'] in ('clovek', 'hak'):
            von.append((i, bool(nasl and nasl['k'] == 'hak')))
    return von


def historia(udalosti, po=None):
    """Skoršie koncové otázky sedenia (na koncoch ťahov) a čo človek napísal po nich — pre rozpoznanie opakovania.
    po = index udalosti, pred ktorou sa história končí (aktuálna správa sa nezapočíta). Posledných 12 blokov s otázkou
    (rozoberajú sa len tie — dlhé sedenie by inak stálo kvadratický čas)."""
    hist = []
    hranica = len(udalosti) if po is None else po
    konce = [i for i, _ in zastavenia(udalosti) if i < hranica and konci_otazkou(udalosti[i]['text'])][-12:]
    for i in konce:
        e = udalosti[i]
        blok = koncovy_blok(e['text'])
        if not blok:
            continue
        qs = otazky_bloku(blok)
        if not qs or len(qs) > MAX_OTAZOK:
            continue
        nv = navrat_v_udalostiach(udalosti, i)
        qs = [dict(veta=q['veta'], predosla=q['predosla'], polozka=q['polozka'],
                   trieda=('R' if nv and t == 'A' else t)) for q in qs for t in [trieda_otazky(q, blok)[0]]]
        odp = [u['text'] for u in udalosti[i + 1:hranica] if u['k'] == 'clovek']
        hist.append(dict(ts=e.get('ts'), otazky=qs, odpovede=odp, index=i))
    return hist


# ---------------------------------------------------------------------------------------------- meranie sedenia
def strojovy_beh(udalosti):
    """Sedenie bez človeka (claude -p, sdk) — pravidlo J-2 v ng-klauzuly.py: entrypoint sdk*, alebo ťahy
    začaté len strojom (turnOrigin „sdk“ bez „human“), alebo žiadna správa človeka."""
    ludia = [u for u in udalosti if u['k'] == 'clovek']
    if not ludia:
        return True
    if any((u.get('ep') or '').startswith('sdk') for u in udalosti):
        return True
    orig = set(u.get('origin') for u in ludia if u.get('origin'))
    return 'sdk' in orig and 'human' not in orig


def sedenie(udalosti, od_ts=None, do_ts=None, videne=None):
    """Meranie jedného sedenia: zbytočné otázky, na ktorých ŤAH SKONČIL (čo ostalo človeku).
    Ťah = od správy človeka po ďalšiu. Keď háčik ťah zastavil a partner pokračoval, otázka z prvého zastavenia platí
    ďalej len vtedy, keď pokračovanie bolo iba evidencia (veta „kde som“, zápis, echo) — keď partner medzitým pracoval,
    ponuku urobil. Keď ťah zastavil STRÁŽCA OTÁZOK (spätná väzba „NG, otázky:“) a ťah potom skončil bez otázky, otázka
    odpadla vždy — aj keď ponúknutá práca bola len zápis do zápisníka. od_ts/do_ts: ISO čas UTC ako v stope — ráta sa
    ťah, ktorého koniec padne do okna. videne: množina už zarátaných koncov (čas + text) — kópia toho istého sedenia
    v dvoch súboroch stopy sa ráta raz (ako J-2). Úniková veta platí prvé dva razy za sedenie (UNIKOV_NA_SEDENIE).
    Vráti dict(tahov, otazok, zbytocnych, unikov, len_rep, vyslovne, istych, po_vysvetleni, zoznam=[…],
    zastaveni=[…]); istych = z zbytočných tie, ktoré by strážca v režime zastav vrátil (verzia 5, p['ista']);
    po_vysvetleni = z zbytočných tie, pred ktorými človek žiadal len vysvetlenie alebo sa pýtal (4.94 d/e)."""
    konce = set(i for i, _ in zastavenia(udalosti))
    vysl = dict(tahov=0, otazok=0, zbytocnych=0, unikov=0, len_rep=0, vyslovne=0, istych=0, po_vysvetleni=0,
                zoznam=[], zastaveni=[])
    tah = []
    tahy = []
    for i, e in enumerate(udalosti):
        if e['k'] == 'clovek':
            if tah:
                tahy.append(tah)
            tah = []
            continue
        tah.append(i)
    if tah:
        tahy.append(tah)
    uniky = 0
    for t in tahy:
        pending = None
        prac = False
        koniec_ts = None
        prvy = True
        for i in t:
            e = udalosti[i]
            if e['k'] == 'nastroj' and not e.get('evidencia'):
                prac = True
            if e['k'] == 'hak' and e.get('otazky'):
                prac = True                # strážca otázok zasiahol: koniec bez otázky = otázka odpadla
            if i not in konce:
                continue
            koniec_ts = e.get('ts')
            p = posudok_v_stope(udalosti, i) if konci_otazkou(e['text']) else None
            if p:
                pending = dict(p=p, ts=e.get('ts'))
                vysl['zastaveni'].append(dict(ts=e.get('ts'), trieda=p['trieda'], zbytocna=p['zbytocna'],
                                              uryvok=p['uryvok'], stop_hook_active=not prvy))
            elif prac:
                pending = None
            prac = False
            prvy = False
        if koniec_ts is None:
            continue
        p = pending['p'] if pending else None
        je_unik = bool(p and p['unik'] and p['zbytocna_bez_unik'])
        if je_unik:
            uniky += 1                     # poradie únikov v sedení sa ráta aj mimo okna (strop platí na sedenie)
        if (od_ts and koniec_ts < od_ts) or (do_ts and koniec_ts > do_ts):
            continue
        if videne is not None:
            kluc = (koniec_ts, udalosti[t[-1]].get('text', '')[:200])
            if kluc in videne:
                continue
            videne.add(kluc)
        vysl['tahov'] += 1
        if not p:
            continue
        vysl['otazok'] += 1
        if p['vyslovne']:
            vysl['vyslovne'] += 1
        zb = p['zbytocna']
        if je_unik:
            if uniky <= UNIKOV_NA_SEDENIE:
                vysl['unikov'] += 1        # zbytočná otázka so zdôvodnením „rozhoduje človek, lebo …“ — ráta sa zvlášť
            else:
                zb = True                  # tretí a ďalší únik v sedení sa už neberie
        if zb:
            vysl['zbytocnych'] += 1
            if p.get('vysvetlenie'):
                vysl['po_vysvetleni'] += 1      # 4.94 d/e: zbytočné otázky po žiadosti o vysvetlenie sa vidia zvlášť
            if p['len_rep']:
                vysl['len_rep'] += 1
            if p.get('ista'):
                vysl['istych'] += 1
            vysl['zoznam'].append(dict(ts=pending['ts'], trieda=p['trieda'], uryvok=p['uryvok'],
                                       len_rep=p['len_rep'], ista=bool(p.get('ista')), pochybnost=p.get('pochybnost') or '',
                                       vysvetlenie=p.get('vysvetlenie') or ''))
    return vysl


# SPRESNENÝ POKYN (PS STAVBA 4.94 e, 11. 10. 2026, AIG-R261011-2e5805): dve vety pribudli k vete „Inak sa nepýta
# a nečaká.“ v pokynoch projektu. Týždeň po ich doručení má ukázať, či zbytočných otázok ubudlo — a aby to nebol týždeň
# na cieli zadarmo (PS 4.98), deň začiatku sa NEZAPISUJE RUKOU: číta sa z gitu projektu ako prvý commit, ktorý druhú
# vetu pokynu do CLAUDE.md pridal. Kým pokyn v projektoch nie je, meranie to povie a týždeň nezačal.
POKYN_ZNAK = 'návrh opravy uvedie jednou vetou bez otázky'
# základ na porovnanie „pred pokynom“ (meranie 11. 10. 2026 na 30 dňoch: zbytočných otázok spolu, z nich po žiadosti
# o vysvetlenie alebo po otázke) — podklad d-podklad.py je len na serveri, lebo obsahuje vety kolegov
# 12. 10. (kus P4F): zbytočných 45, nie 44 — oprava falošných zásahov výnimky o hranici práce vrátila do počtu jednu
# otázku odpustenú neprávom; to isté okno (291 sedení, 625 koncov ťahu s otázkou) starým kódom 44, novým 45. Základ sa
# ráta tou istou metódou ako týždeň po pokyne, inak by porovnanie nieslo zmenu metódy. Druhé číslo je z pomocného
# rozboru (STAVBA 4.103, námietka „9 a 11“) a oprava ho nemení — pridaná otázka nebola po žiadosti o vysvetlenie.
POKYN_PRED = (45, 11, '30 dní do 11. 10. 2026')


def pokyn_od(projekty=None, home='/home/pstrocka/projects'):
    """dict(od, vsade_od, projektov, z): odkedy je spresnený pokyn (POKYN_ZNAK) v CLAUDE.md projektov so zápisníkom.
    od = najstarší deň (ISO), v ktorý ho tam niektorý commit pridal, alebo None — pokyn ešte nikde nie je, teda týždeň
    merania nezačal. Git sa pýta len pri projekte, ktorý vetu dnes naozaj má (kým nie je rozvezená, nestojí nič).
    vsade_od = deň, keď pokyn dorazil do POSLEDNÉHO projektu (ISO), len keď je vo všetkých, inak None. P4R (13. 10.):
    týždeň merania začína až ním — cp-ai-workspace dostal celý nový text pri prechode 13. 10. a meranie od prvého
    projektu by rátalo „1. deň zo 7“, hoci pokyn bol v 1 z 36 projektov a ľudia nad cieľom pracujú inde."""
    import os
    import glob
    import subprocess
    projs = projekty or sorted(f.split('/')[-2] for f in glob.glob(os.path.join(home, '*', 'NG-zaznamy.md'))
                               if f.split('/')[-2] != 'bpw-nocny')
    dni = []
    ma = 0
    for p in projs:
        try:
            if POKYN_ZNAK not in open(os.path.join(home, p, 'CLAUDE.md'), encoding='utf-8', errors='ignore').read():
                continue
        except OSError:
            continue
        ma += 1
        try:
            out = subprocess.run(['git', '-C', os.path.join(home, p), 'log', '--reverse', '--format=%cs',
                                  '-S', POKYN_ZNAK, '--', 'CLAUDE.md'], capture_output=True, text=True,
                                 timeout=20).stdout.split('\n')
            d = next((x.strip() for x in out if x.strip()), '')
            if d:
                dni.append(d)
        except Exception:
            pass
    return dict(od=min(dni) if dni else None,
                vsade_od=max(dni) if dni and ma == len(projs) and len(dni) == ma else None, projektov=ma, z=len(projs))


def riadok_pokyn(r, mena=False):
    """Časť riadka J-4 o spresnenom pokyne (4.94 e) a o zbytočných otázkach po žiadosti o vysvetlenie (4.94 d).
    JEDNO miesto pre riadok s menami (ng-otazky.py meraj) aj pre riadok v stave bez mien (ng-klauzuly.p4_riadok) —
    v stave sa mená kolegov neuvádzajú, preto tam ostáva len počet ľudí."""
    import datetime
    s = r.get('spolu') or {}
    zb = int(s.get('zbytocnych') or 0)
    pv = int(s.get('po_vysvetleni') or 0)
    ludia = sorted(((kto, int(d.get('po_vysvetleni') or 0)) for kto, d in (r.get('ludia') or {}).items()
                    if int(d.get('po_vysvetleni') or 0)), key=lambda x: (-x[1], x[0]))
    kde = ''
    if ludia:
        kde = ((' (' + ' · '.join(f'{k} {n}' for k, n in ludia) + ')') if mena else
               (' u 1 človeka' if len(ludia) == 1 else f' u {len(ludia)} ľudí'))
    von = (f'po žiadosti o vysvetlenie alebo otázke {pv} z {zb}{kde}'
           f' — pred spresnením pokynu {POKYN_PRED[1]} zo {POKYN_PRED[0]} za {POKYN_PRED[2]}')
    pk = r.get('pokyn')
    if not isinstance(pk, dict):
        return von + ' · odkedy je spresnený pokyn v projektoch, sa nedalo prečítať'
    if not pk.get('od'):
        return von + (f" · spresnený pokyn (4.94 e) ešte nie je ani v jednom z {pk.get('z', 0)} projektov"
                      f' — týždeň merania nezačal')
    def _den(iso):
        try:
            d = datetime.date.fromisoformat(iso)
            return f'{d.day}. {d.month}.', (datetime.date.today() - d).days + 1
        except Exception:
            return iso, 0
    # P4R (13. 10.): kým pokyn nie je vo všetkých projektoch, týždeň merania nezačal — ráta sa od posledného doručenia
    if not pk.get('vsade_od') or int(pk.get('projektov', 0)) < int(pk.get('z', 0)):
        return von + (f" · spresnený pokyn (4.94 e) je zatiaľ v {pk.get('projektov', 0)} z {pk.get('z', 0)} projektov"
                      f" (prvý od {_den(pk['od'])[0]}) — týždeň merania začne, keď bude vo všetkých")
    kedy, dnov = _den(pk['vsade_od'])
    return von + (f" · spresnený pokyn (4.94 e) je vo všetkých {pk.get('z', 0)} projektoch od {kedy},"
                  f' meria sa {dnov}. deň zo 7')


def meraj(dni=7, teraz=None, projekty=None, ludia=None):
    """Pravidlo 4 za posledných N dní: zbytočné otázky na sedenie s človekom, po ľuďoch. Výber sedení ako jadro()
    v ng-klauzuly.py: projekty so zápisníkom (bez bpw-nocny), stopy PS aj kolegov (ng-stopy), len po prechode projektu
    do NG, bez strojových behov, aspoň jedna správa človeka v okne. Vráti dict(od, do, ludia, spolu, sedenia)."""
    import os
    import glob
    import datetime
    import importlib
    import subprocess
    for c in (os.path.dirname(os.path.abspath(__file__)), '/opt/aigrip-ng/scripts',
              '/home/pstrocka/projects/aigrip/.aigrip/scripts'):
        if c not in sys.path:
            sys.path.append(c)
    ST = importlib.import_module('ng-stopy')
    HOME = '/home/pstrocka/projects'
    teraz = teraz or datetime.datetime.now().astimezone()
    if teraz.tzinfo is None:
        teraz = teraz.astimezone()
    od = teraz - datetime.timedelta(days=dni)

    def z(d):
        return d.astimezone(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%S')

    def prechod(proj):
        try:
            out = subprocess.run(['git', '-C', os.path.join(HOME, proj), 'log', '--diff-filter=A', '--format=%cI', '-1',
                                  '--', 'NG-zaznamy.md'], capture_output=True, text=True, timeout=10).stdout.strip()
            if out:
                return z(datetime.datetime.fromisoformat(out))
        except Exception:
            pass
        return '2026-09-15T00:00:00'

    projs = projekty or sorted(f.split('/')[-2] for f in glob.glob(os.path.join(HOME, '*', 'NG-zaznamy.md'))
                               if f.split('/')[-2] != 'bpw-nocny')
    sedenia = []
    videne = set()
    for proj in projs:
        od_p = max(z(od), prechod(proj))
        for td in ST.stopy(proj):
            kto = ST.kto(td)
            if ludia and kto not in ludia:
                continue
            for f in glob.glob(td + '/*.jsonl'):
                try:                                    # súbor zmizol alebo sa nedá čítať medzi glob a open (zrkadlo
                    if os.path.getmtime(f) < od.timestamp():   # kolegov sa obnovuje, sedenie zmazané) — preskočiť ho,
                        continue                        # nie zhodiť celé meranie J-4 na záložný riadok starej metódy
                    with open(f, 'rb') as h:
                        ud = udalosti_z_riadkov(h)
                except OSError:
                    continue
                if strojovy_beh(ud):
                    continue
                if not any(u['k'] == 'clovek' and od_p <= (u.get('ts') or '') <= z(teraz) for u in ud):
                    continue
                v = sedenie(ud, od_ts=od_p, do_ts=z(teraz), videne=videne)
                if not v['tahov']:
                    continue
                sedenia.append(dict(kto=kto, projekt=proj, sid=os.path.basename(f)[:8], tahov=v['tahov'],
                                    otazok=v['otazok'], zbytocnych=v['zbytocnych'], unikov=v['unikov'],
                                    len_rep=v['len_rep'], vyslovne=v['vyslovne'], istych=v['istych'],
                                    po_vysvetleni=v['po_vysvetleni'], zoznam=v['zoznam']))
    polia = ('tahov', 'otazok', 'zbytocnych', 'unikov', 'len_rep', 'vyslovne', 'istych', 'po_vysvetleni')
    sumar = {}
    for s in sedenia:
        d = sumar.setdefault(s['kto'], dict(sedeni=0, max_sedenie=0, **{p: 0 for p in polia}))
        d['sedeni'] += 1
        for p in polia:
            d[p] += s[p]
        d['max_sedenie'] = max(d['max_sedenie'], s['zbytocnych'])
    for d in sumar.values():
        d['na_sedenie'] = round(d['zbytocnych'] / d['sedeni'], 2) if d['sedeni'] else 0.0
    spolu = dict(sedeni=len(sedenia), **{p: sum(s[p] for s in sedenia) for p in polia})
    spolu['na_sedenie'] = round(spolu['zbytocnych'] / spolu['sedeni'], 2) if spolu['sedeni'] else 0.0
    triedy = {}
    for s in sedenia:
        for x in s['zoznam']:
            triedy[x['trieda']] = triedy.get(x['trieda'], 0) + 1
    spolu['triedy'] = triedy
    return dict(od=od.isoformat(timespec='minutes'), do=teraz.isoformat(timespec='minutes'), ludia=sumar,
                spolu=spolu, sedenia=sedenia, pokyn=pokyn_od(projs, HOME))


def pravidlo4(dni=7, teraz=None):
    """Meranie pre riadok J-4 v ng-klauzuly.py: to isté čo meraj(), s oknom N dní do teraz (naivný miestny čas stačí)."""
    return meraj(dni=dni, teraz=teraz)


def riadok_j4(r, nazov='otázky úmerné veci', tlacidla=0, vsetkych=None):
    """Riadok J-4 pre ng-klauzuly.py (bez chvosta „— zamknuté …“, ten dopĺňa zamok(4, r['spolu']['na_sedenie'])).
    Tvar „(X na sedenie“ číta história zamknutých pravidiel (jadro-historia.jsonl) — nemeniť.
    r = pravidlo4(); tlacidla = otázky cez ponuku možností (z jadro()); vsetkych = všetky otázky na konci textu (starý J-4)."""
    s = r['spolu']
    t = s.get('triedy', {})
    casti = ', '.join(f'{m} {t[k]}' for k, m in (('A', 'ponuka vratnej práce'), ('S', 'voľba tvaru'), ('rep', 'opakovanie'))
                      if t.get(k)) or 'žiadna'
    ludia = ' · '.join(f"{kto} {d['zbytocnych']}/{d['sedeni']}" + (f" (únik {d['unikov']}×)" if d['unikov'] else '')
                       for kto, d in sorted(r['ludia'].items(), key=lambda x: (-x[1]['zbytocnych'], x[0])))
    return (f"J-4 {nazov}: {s['zbytocnych']} zbytočných otázok na človeka v {s['sedeni']} sedeniach s človekom "
            f"({s['na_sedenie']:.2f} na sedenie; {casti}; ťahov {s['tahov']}, z nich skončilo otázkou {s['otazok']}"
            + (f', všetkých otázok v texte {vsetkych}' if vsetkych is not None else '') + ')'
            f" · úniková veta „rozhoduje človek, lebo“ {s['unikov']}× · „tvoje rozhodnutie“ pri ponuke {s['vyslovne']}×"
            f" · po ľuďoch (zbytočné/sedenia): {ludia or '—'}"
            f" · {riadok_pokyn(r, mena=True)} · {tlacidla} cez tlačidlá (má byť 0)")


if __name__ == '__main__':
    if len(sys.argv) > 1 and sys.argv[1] == 'meraj':
        r = meraj(int(sys.argv[2]) if len(sys.argv) > 2 else 7)
        print(riadok_j4(r))
        for kto, d in sorted(r['ludia'].items()):
            print(f"  {kto:12} {d['zbytocnych']:3} v {d['sedeni']:3} sedeniach = {d['na_sedenie']:.2f} na sedenie "
                  f"(max {d['max_sedenie']} v jednom; únik {d['unikov']}, len opakovanie {d['len_rep']}, "
                  f"isté (nález strážcu) {d['istych']})")
    else:
        p = posudok(sys.stdin.read())
        print(ZNACKA)
        if not p:
            print('správa nekončí otázkou')
        else:
            print(f"trieda bloku: {p['trieda']} · zbytočná: {p['zbytocna']}" + (' · úniková veta' if p['unik'] else '')
                  + (f" · istá (nález strážcu): {p['ista']}" + (f" — pochybnosť: {p['pochybnost']}" if p['pochybnost'] else '')
                     if p['zbytocna_bez_unik'] else ''))
            for o in p['otazky']:
                print(f"  [{o['trieda']}{' rep' if o['rep'] else ''}] {o['pravidlo']}: {o['veta'][:140]}")
