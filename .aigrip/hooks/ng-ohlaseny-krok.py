#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Ohlásený krok na konci ťahu — jediné miesto pravidla 1a (plynulosť; 24. 9. 2026).

Partner skončí ťah vetou „Pokračujem ďalej …“ / „Teraz prejdem na …“, hoci ho nič neblokuje, a človek musí napísať
„pokračuj“. Za 7 dní to Peter Tóth v cp-controlling urobil 15× („pokracuj automaticky, ked nepotrebujes z mojej strany
zasah“, 21. 9. 18:30); v aigrip to bolo 6 z 9 prípadov, keď PS písal „pokračuj“ alebo „beží?“ bez prekážky.

Pravidlo číta len text poslednej správy partnera — žiadny stav, nič sa neukladá okrem riadku o zásahu. Používa ho háčik
balíka na konci ťahu (ng-kdesom-koniec.sh — u každého človeka v každom projekte v novom spôsobe; do projektu ho nosí
rozvoz vedľa háčika, ako rozbor kotvy, takže kolega beží presne overenú verziu) aj strážca konca ťahu v aigrip
(ng-koniec-tahu.py) — jedny regulárne výrazy, žiadne dve kópie.

  posudok(text)        → (veta, None) keď treba zasiahnuť, inak (None, dôvod pustenia) — dôvod je pre prehrávku
  ohlaseny_krok(text)  → veta posledného odseku, v ktorej partner ohlásil VLASTNÝ ďalší krok, ktorý nič neblokuje; alebo None
  plynulost(vstup, projekt_dir, projekt) → text zásahu pre háčik na konci ťahu, alebo None; zásah zapíše do
                         ~/.aigrip-ng/plynulost.jsonl toho, kto pracuje (u kolegu nie do domova PS)
  otazka_na_cloveka(text), _cisty(text), ROZKAZ — používa aj ng-prepocet.py (cez ng-koniec-tahu.py)
  python3 ng-ohlaseny-krok.py < text  → vypíše posudok (skúška)

Pustí vždy: ťah, ktorý háčik už raz zastavil (najviac jeden zásah za ťah) · otázku na človeka (otáznik) · žiadosť
o úkon človeka · krok podmienený človekom, behom v pozadí, časom alebo udalosťou („keď dobehne“, „po tvojom slove“,
„zajtra“, „po stretnutí“) · návrh („by som“, „navrhujem“, „odporúčam“) · odsek, v ktorom partner sám napísal, že jeho
krok čaká („Keď skončí, zapíšem všetko naraz“) · zhrnutie bez ohlásenia · beh bez človeka (claude -p, sdk) · podagenta
· akúkoľvek chybu. Podmienka, ktorú spĺňa partner sám („keď dokončím túto sériu“, „keď na ňu prídem“), krok nekryje
(21. 9. 18:51 u P. Tótha).
Overenie 24. 9. (nezávislá prehrávka 14 dní, falošné zastavenia): pustí aj krok zoradený za prácu v pozadí (vstup
háčika background_tasks: „…, potom overím“, „Potom …“, „keď budem mať HTML“ — Claude Code partnera po dobehnutí zobudí
sám), krok po žiadosti o úkon človeka v jednom z dvoch predošlých odsekov („Nahraj sken …“ → „Keď budem mať oboje, …“),
„po potvrdení“, „hneď po tom, ako odsúhlasíš“, „až rozhodnutím“, „čaká sa na“, „ČAKÁM na … — začnem po nich“,
„nabudúce / v ďalšom sedení“ a zoznam možností. Prípady P. Tótha 17.–21. 9. zastaví ďalej.

Prehrávka 24. 9. na skutočných koncoch ťahov 17.–24. 9. (1058: PS 422 vo všetkých projektoch, kolegovia 636): zastavil
by 38 — 22 u P. Tótha v cp-controlling (8 z 9 prípadov, keď musel písať „pokračuj“; nie 17. 9. 20:51 — zhrnutie bez
ohlásenia), 16 u PS v aigrip; po 22 z 38 človek naozaj napísal „pokračuj“ alebo „beží?“. Pustil 308 koncov s otázkou,
83 so žiadosťou o úkon človeka, 93 s krokom podmieneným človekom, behom alebo časom a 507 zhrnutí bez ohlásenia.
"""
import re
import sys

ZNACKA = 'ohlaseny-krok 1'

# Partner ohlasuje VLASTNÝ ďalší krok (1. osoba jednotného čísla, prítomný/budúci čas).
OHLAS = re.compile(
    r'\b(pokračujem|staviam ďalej|idem (na|do|ďalej|teraz|to|ich|ho|ju|robiť|opraviť|pozrieť|spraviť|dokončiť|overiť|stavať|'
    r'zapísať|pripraviť|skúsiť|prejsť|pridať|doplniť|spustiť|rovno)|pôjdem (ďalej|na|do)|'
    r'prejdem|prechádzam (na|k|do)|dorobím|dokončím|spravím|urobím|opravím|pozriem|overím|preverím|doplním|'
    r'nastavím|zapíšem|spustím|pustím sa|púšťam sa|pridám|zaradím|postavím|navrhnem|prepíšem|upravím|vyriešim|zistím|'
    r'doladím|vytvorím|napíšem|prečítam|rozoberiem|skúsim|pripravím|spracujem|dopíšem|prepočítam|zapracujem|otestujem|'
    r'vyskúšam|vygenerujem|spíšem|pregenerujem|premenujem|zosúladím|zapojím|beriem si|si beriem|vezmem si|'
    r'beriem (ďalej|teraz|rovno|ďalš\w+)|začnem|dám (pravidlu|do|tomu|to)|budem (pokračovať|stavať|robiť|opravovať)|'
    r'beriem do ďalšieho ťahu|v ďalšom ťahu|ozvem sa|'
    # 27. 10. (kus JZ0c, čeština): české tvary 1. osoby — čeština má okrem -ím/-ám aj -u a -i (jdu, začnu,
    # napíšu, pokračuji, zpracuji), ktoré slovenský zoznam nepozná. Bez nich stroj po česky ohlásený krok
    # takmer nevidel (21 % toho, čo po slovensky) a človek musel častejšie písať „pokračuj“.
    r'pokračuj[iu]|pokračovat budu|jdu (na|do|dál|teď|to|je|ho|ji|dělat|opravit|podívat|udělat|dokončit|ověřit|'
    r'stavět|zapsat|připravit|zkusit|projít|přidat|doplnit|spustit|rovnou)|půjdu (dál|na|do)|'
    r'přejdu|přecházím (na|k|do)|udělám|opravím|podívám se|ověřím|prověřím|nastavím|zapíšu|zapisuji|spustím|'
    r'pustím se|pouštím se|přidám|zařadím|postavím|navrhnu|přepíšu|upravím|vyřeším|zjistím|doladím|vytvořím|'
    r'napíšu|přečtu|rozeberu|zkusím|připravím|zpracuji|zpracuju|dopíšu|přepočítám|zapracuji|zapracuju|otestuji|'
    r'otestuju|vyzkouším|vygeneruji|vygeneruju|sepíšu|přegeneruji|přejmenuji|sladím|zapojím|beru si|vezmu si|'
    r'beru (dál|teď|rovnou|další)|začnu|dám (pravidlu|do|tomu|to)|budu (pokračovat|stavět|dělat|opravovat)|'
    r'v dalším tahu|ozvu se)\b', re.I)
# Časť vety naviazaná na čakanie (beh, udalosť, iný deň, budík) — to nie je parkovanie práce.
CAKA = re.compile(
    r'(zobudím sa|budík|ďalšia kontrola|\b(pri|po) (ďalšom |najbližšom )?(prebudení|zobudení)\b|\b(sa prebudím|prebudím sa|prebudí ma|zobudí ma)\b|čakám|počkám|dobehn|\bbeh(y|ov|u)?\b|nočn|task-notification|'
    r'notifikáci|časovač|ešte (beží|bežia|pracuje|pracujú|dobieha|počíta)|\b(čaká|čakajú)( ešte)? (na|nahrat\w*|\w+nie)\b|po (jeho|jej|ich) (dokončení|skončení|dobehnutí)|'
    r'\bpo (stretnut|porad|schôdz|meeting|hovore|telefonát)\w*|zajtra|ráno|v pondelok|v utorok|v stredu|vo štvrtok|v piatok|'
    r'po víkende|po týždni|budúci týždeň|\b\d{1,2}\. ?\d{1,2}\.|'
    # overenie 24. 9. (falošné zastavenia): čaká sa na iného („Čaká sa na Janu. Potom …“, „ešte to musí schváliť Peter,
    # potom …“), plán na ďalšie sedenie („nabudúce“, „v ďalšom sedení“) a čas („po obede“)
    r'čaká sa na|čakáme na|\bmusí (ešte )?(schváliť|potvrdiť|odsúhlasiť|odpovedať|poslať|dodať|rozhodnúť|podpísať)|'
    r'nabudúce|\b(v|na|pri) (ďalšom|budúcom|novom) sedení|\b(v|pri) (ďalšej|budúcej|novej) (session|relácii)|'
    r'po obede|poobede|popoludní|'
    # 27. 10. (kus JZ0c, čeština): to isté čakanie po česky. Bez toho stroj hlásil ohlásený krok aj tam, kde sa
    # zjavne čaká — doložené vetou „Po schůzce v úterý doplním poznámky.“, ktorú slovenský náprotivok nezasiahol.
    r'probudím se|budík|další kontrola|\b(při|po) (dalším |nejbližším )?(probuzení)\b|čekám|počkám|doběhn|'
    r'noční|ještě (běží|běžel|pracuje|pracují|dobíhá|počítá)|po (jeho|jejím|jejich) (dokončení|skončení)|'
    r'\bpo (schůz|porad|setkán|meeting|hovoru|telefonát)\w*|zítra|ráno|v pondělí|v úterý|ve středu|ve čtvrtek|'
    r'v pátek|o víkendu|příští týden|čeká se na|čekáme na|'
    r'\bmusí (ještě )?(schválit|potvrdit|odsouhlasit|odpovědět|poslat|dodat|rozhodnout|podepsat)|'
    r'příště|\b(v|na|při) (dalším|příštím|novém) sezení|po obědě|odpoledne)', re.I)
# Závislosť na človeku kdekoľvek vo vete („po tvojom slove“, „od teba“, „čakám na tvoje …“) a návrh („by som“,
# „navrhujem“, „odporúčam“) — krok čaká na slovo človeka, platí pre celú vetu.
ZAVISLOST = re.compile(
    r'(\bby som\b|\bnavrhujem\b|\bodporúčam\b|po tvoj(om|ej)|po odpoved|'
    r'tvoj(e|ho|om|ím|ej|ou|u)? (slov|súhlas|potvrden|rozhodnut|odpove|pokyn|verdikt|akt)|'
    r'stačí (slovo|veta|jedno|jedna|napísať)|\b(povedz|povieš|poviem|daj|dáš) (mi )?slovo\b|na tvoj pokyn|'
    r'od teba(?!,? (nič|sa nič|netreba|nepotrebujem|nečakám|teraz nič))|'
    r'tvoju (vetu|odpoveď)|čakám (ešte )?na tv|'
    r'\bpo (\w+ )?(potvrdení|schválení|odsúhlasení|odpovedi|rozhodnutí|vyjadrení|pripomienkach|súhlase|spätnej väzbe)\b|'
    r'podľa toho,? (čo|ako|či|kedy|ktor\w+) (\w+ ){0,2}(povie|povieš|rozhodne|rozhodneš|odpovie|odpovieš|potvrdí|potvrdíš|'
    r'pošle|pošleš|vyberie|vyberieš|schváli|schváliš|zvolí|zvolíš|dodá|dodáš)\b|'
    # 27. 10. (kus JZ0c, čeština): to isté po česky — krok, ktorý čaká na slovo človeka, nie je ohlásený krok.
    r'\bnavrhuji\b|\bdoporučuji\b|po tvém|po tvé|tvé (slovo|potvrzení|rozhodnutí|vyjádření)|'
    r'stačí (slovo|věta|jedno|jedna|napsat)|\b(řekni|řekneš|dej|dáš) (mi )?slovo\b|na tvůj pokyn|'
    r'od tebe(?!,? (nic|se nic|netřeba|nepotřebuji|nečekám))|čekám (ještě )?na tv)', re.I)
# Podmienka (vedľajšia veta „keď / ak / až / kým …“) až po čiarku, bodkočiarku alebo koniec.
PODM = re.compile(r'\b(keď|ak|až|kým|keby|len čo|hneď,? ako|po tom,? čo|po tom,? ako|potom,? ako|akonáhle)\b([^,;]*)', re.I)
# Vedľajšia veta účelu („…, aby som ti stav vypísal, keď sa vrátiš“) opisuje krok, nepodmieňuje ho.
ABY = re.compile(r',?\s\baby\b.*$', re.I | re.S)
# Podmienka, ktorú spĺňa partner sám: sloveso v 1. osobe („keď dokončím“, „až prejdem“, „keď budem mať“).
PRVA_OSOBA = re.compile(r'\b(?!(?:nám|vám|sám|ním|zem|tem)\b)\w{2,}(?:ím|ám|em)\b', re.I)
# … a nie je to čakanie na iného (druhá osoba, výsledok behu, odpoveď).
CUDZIE = re.compile(r'(\b\w+(íš|áš|eš)\b|\bti\b|\bťa\b|\bteba\b|tvoj|dostanem|uvidím|príde|odpovie|dobehn|čakám|počkám|'
                    r'bude\b|budú\b|potvrd|schvál|súhlas|rozhodnut|odpoveď|odpoved|\bchceš\b)', re.I)
# Odsek, v ktorom partner žiada človeka o úkon (rozkazovací spôsob) — čaká sa oprávnene.
ROZKAZ = re.compile(
    r'\b(prihlás sa|prihlás|napíš|pošli mi|pošli|potvrď|daj (mi )?vedieť|daj mi|pozri sa|pozri|skús|otvor|klikni|odklikni|'
    r'preklikni|preklikaj|pretukni|pretukaj|vyber si|rozhodni|odpíš|povedz|schváľ|nainštaluj|skopíruj|vlož|zadaj|'
    r'odsúhlas|prečítaj si|skontroluj|nahraj|stiahni|vyplň|ozvi sa|pripomeň|'
    r'napíšte|pošlite|dajte (mi )?vedieť|skúste|otvorte|kliknite|potvrďte|prihláste sa|pozrite|'
    # 27. 10. (kus JZ0c, čeština): to isté po česky — keď partner žiada o úkon, čaká sa oprávnene.
    r'přihlas se|napiš|pošli mi|podívej se|podívej|zkus|otevři|klikni|vyber si|rozhodni|odpověz|řekni|schval|'
    r'nainstaluj|zkopíruj|vlož|zadej|odsouhlas|přečti si|zkontroluj|nahraj|stáhni|vyplň|ozvi se|připomeň|'
    r'napište|pošlete|dejte (mi )?vědět|zkuste|otevřete|klikněte|potvrďte|přihlaste se|podívejte)\b', re.I)
# Veta, ktorá sa odvoláva na predošlú („Potom …“, „Po nej …“) — podmienku treba hľadať v predošlej vete.
NAVAZUJE = re.compile(r'^(potom|po (nej|ňom|tom|nich)|následne|hneď potom|až potom)\b', re.I)
CAKA_NA_CLOVEKA = re.compile(r'(čaká na tvoj|čakám|na teba čaká|čaká na teba)', re.I)
BEZI = re.compile(r'(dobehn|\bbeh(y|ov|u)?\b|notifikáci|časovač|\b(beží|bežia|dobieha|dobiehajú|skončí)\b|čakám|počkám)', re.I)
# Budenie („pri ďalšom prebudení“ — 20. 9. 07:39 v aigrip: „ja sa prebudím, keď dobehne … Pri ďalšom prebudení kandidátov
# pozriem“, beh ešte bežal) je čakanie ako „zobudím sa“; starý strážca ho pustil, spoločný modul ho bez toho zastavil.
BUDIK = re.compile(r'(budík|zobudím sa|ďalšia kontrola|\b(pri|po) (ďalšom |najbližšom )?(prebudení|zobudení)\b|'
                   r'\b(sa prebudím|prebudím sa|prebudí ma|zobudí ma)\b)', re.I)
# Krok výslovne nezávislý od toho, na čo sa čaká („medzitým“, „sám“, „kde ma netreba“).
NEZAVISLE = re.compile(r'\b(medzitým|zatiaľ|popri tom|sám|sama|bez teba|bez tvojej|kde ma netreba|kde ťa netreba|'
                       r'kde ťa nepotrebujem|kde ma nič neblokuje)\b', re.I)
# Odsek, v ktorom partner výslovne povie, že od človeka nič netreba — krok nečaká, aj keď inde spomenie jeho odpoveď.
NIC_NETREBA = re.compile(r'(od teba,? (teraz )?(nič|sa nič) (netreba|nepotrebujem|nečakám|nie je treba)|nič od teba (netreba|nepotrebujem|nečakám))', re.I)
# Žiadosť o úkon človeka v jednom z dvoch predošlých odsekov („## Nahraj podpísaný sken“, „`BVS.pdf` daj do …“,
# „Otvor noVNC … napíš „hotovo““) — posledný odsek opisuje, čo partner urobí, keď človek skončí („Keď budem mať oboje,
# rozdelím …“; 13. 9. ps-domov, 14. 9. aigrip). Krok výslovne nezávislý („medzitým“, „sám“) alebo „teraz“ zastaví ďalej.
ZIADOST_PRED = re.compile(r'\b(prihlás sa|prihlás|napíš|pošli|potvrď|daj (mi )?vedieť|daj mi|daj (ho|ju|ich|to|sem|do|na)|'
                          r'otvor|klikni|odklikni|preklikni|preklikaj|pretukni|pretukaj|vyber si|rozhodni|odpíš|povedz|'
                          r'schváľ|nainštaluj|skopíruj|vlož|zadaj|odsúhlas|skontroluj|nahraj|stiahni|vyplň|vyrieš|'
                          r'napíšte|pošlite|dajte (mi )?vedieť|otvorte|kliknite|potvrďte|prihláste sa)\b', re.I)   # nie „pozri“, „skús“ — odporúčanie, nie úkon
TERAZ = re.compile(r'\bteraz\b', re.I)
# Zoznam možností pre človeka („Možnosti: - A: pokračujem Alzou - B: prejdem na Hornbach“) — výber, nie ohlásený krok.
MOZNOSTI = re.compile(r'\b(možnos\w*|variant\w*|alternatív\w*)\b', re.I)
# Beh v pozadí (vstup háčika background_tasks): krok zoradený za niečo („…, potom overím“, „po ňom“, „Potom …“,
# „keď budem mať HTML“) alebo odsek, ktorý opisuje bežiaci beh („nasadzujem“, „beží testovací plán“), čaká na ten beh —
# Claude Code partnera po jeho dobehnutí zobudí sám, človek nemusí písať „pokračuj“ (10.–16. 9. bpw, AIB, monitor;
# 23. 9. 15:06 cp-controlling). Beh ohlásený ako súbežný („medzitým beží …“) krok nekryje.
PORADIE = re.compile(r'(^|,\s*|\ba\s+)(potom|následne)\b|\bpo (ňom|nej|nich|dobehnutí|skončení|dokončení|nasadení)\b', re.I)
BEH_OPIS = re.compile(r'\b(nasadzuj\w*|sledujem|idú (za sebou )?nasaden\w*|dobieha\w*|beží|bežia|dobehne|dobehnú|'
                      r'pracuje|pracujú)\b', re.I)
SUBEZNE = re.compile(r'\b(medzitým|popri tom)\b', re.I)
AK = re.compile(r'\b(ak|keby|pokiaľ)\b', re.I)
# Skratky a dátumy, za ktorými bodka vetu nekončí („Dr. Max“, „napr. …“).
SKRATKY = re.compile(r'\b(Dr|Ing|Mgr|Bc|napr|tzn|resp|príp|atď|tj|t\. ?j|č|čl|ods|str|kap|obr|tab|mil|mld|tis|cca|max|min|'
                     r'hod|sv|vs|pozn|tel|zák|a\. ?s|s\. ?r\. ?o)\.(?=\s)', re.I)
_BODKA = '\u2024'
# Citát („prehľad „čaká na teba““, názov časti, slová človeka) sa neposudzuje ako reč partnera o sebe.
CITAT = re.compile(r'„[^“"\n]{0,200}[“"]|"[^"\n]{0,200}"|“[^”\n]{0,200}”|»[^«\n]{0,200}«')


def _cisty(text):
    t = re.sub(r'```.*?```', ' ', text or '', flags=re.S)
    t = re.sub(r'`[^`]*`', ' ', t)
    t = re.sub(r'\[([^\]]*)\]\([^)]*\)', r'\1', t)
    return t.replace('**', '').replace('__', '')


def otazka_na_cloveka(text):
    """Partner v poslednej správe položil otázku (veta končiaca „?“; kód a odkazy sa nerátajú)."""
    return bool(re.search(r'\?[\s»“")\]*_]*($|\n|\s)', _cisty(text)))


def _podm_cudzia(cast):
    """Časť vety má podmienku, ktorú nespĺňa partner sám („keď dobehne“, „ak chceš“, „až potvrdíš“)."""
    for m in PODM.finditer(ABY.sub('', cast)):
        podm = m.group(2)
        if not (PRVA_OSOBA.search(podm) and not CUDZIE.search(podm) and not CAKA.search(podm)):
            return True
    return False


def _podmienene(cast):
    """Časť vety je podmienená niekým alebo niečím iným než partnerom (človek, beh, udalosť)."""
    return bool(ZAVISLOST.search(cast)) or _podm_cudzia(cast)


def _vety(odsek):
    """[(veta, je_polozka_zoznamu)] — bodka za skratkou alebo v dátume vetu nekončí."""
    t = SKRATKY.sub(lambda m: m.group(0)[:-1] + _BODKA, odsek)
    von = []
    for riadok in t.split('\n'):
        polozka = bool(re.match(r'\s*([-*•·]|\d+[.)])\s', riadok))
        for v in re.split(r'(?<=[.!…])\s+(?=[A-ZÁÄČĎÉÍĽĹŇÓÔŔŠŤÚÝŽ„"*(\[-])', riadok):
            v = v.strip(' -*·•>').replace(_BODKA, '.')
            if v:
                von.append((v, polozka))
    return von


def _casti(v):
    """[(časť, krok, podmienená)] — časti vety oddelené pomlčkou alebo bodkočiarkou; podmienka, ktorú nespĺňa partner,
    platí aj pre časti za ňou („Ak to potvrdíš, prepíšem plán — doplním …“), nie pred ňou („Pokračujem; ozvem sa, ak …“)."""
    von = []; podm = False
    for cast in re.split(r'\s[—–]\s|;\s', v):
        podm = podm or _podm_cudzia(cast)
        m = OHLAS.search(cast)
        krok = m.group(0).lower() if m else ''
        von.append((cast, krok, podm))
    return von


def _bez_citatov(v):
    return CITAT.sub(' … ', v)


def _plan_caka(vety):
    """Partner v odseku sám napísal, že jeho krok čaká na človeka alebo beh („Keď skončí, zapíšem všetko naraz“)."""
    for v, _ in vety:
        v = _bez_citatov(v)
        zav = bool(ZAVISLOST.search(ABY.sub('', v)))
        for cast, krok, podm in _casti(v):
            if krok and krok != 'ozvem sa' and (podm or zav or BEZI.search(cast)):
                return True
    return False


def _ziadost(odseky):
    """V odsekoch je žiadosť o úkon človeka — nie v citáte („pravidlo „pozri sa, či …““) a nie pre prípad („Ak sa to
    zopakuje, napíš čas“)."""
    for v, _ in _vety('\n'.join(odseky)):
        v = _bez_citatov(v)
        m = ZIADOST_PRED.search(v)
        if m and not AK.search(v[:m.start()]):
            return True
    return False


def posudok(text, pozadie=False):
    """(veta, None) — partner ohlásil vlastný krok, ktorý nič neblokuje; (None, dôvod) — pustiť.
    pozadie=True: v sedení beží práca v pozadí (vstup háčika background_tasks) — krok zoradený za ňu čaká oprávnene."""
    if not text or not text.strip():
        return None, 'bez textu'
    if otazka_na_cloveka(text):
        return None, 'otázka'
    ods = [p.strip() for p in _cisty(text).split('\n\n') if re.search(r'\w', p)]
    if not ods:
        return None, 'bez textu'
    posl = ods[-1]
    if ROZKAZ.search(posl):
        return None, 'žiadosť o úkon človeka'
    uvod = ods[-2] if len(ods) > 1 else ''
    if (MOZNOSTI.search(posl) or (uvod.rstrip().endswith(':') and MOZNOSTI.search(uvod))) and (
            len(re.findall(r'(?m)^\s*([-*•·]|\d+[.)]|[A-D][).:])\s', posl)) >= 2
            or len(re.findall(r'(?:^|\s)\(?[A-D]\)\s', posl)) >= 2):
        return None, 'zoznam možností'
    ziadost_pred = _ziadost(ods[-3:-1])
    beh_opis = pozadie and any(BEH_OPIS.search(v) and not SUBEZNE.search(v) for v, _ in _vety(posl))
    vety = _vety(posl)
    pred = _vety('\n'.join(ods[-3:-1])) if len(ods) > 1 else []     # dva predošlé odseky — pre „Potom …“ na začiatku
    plan_caka = _plan_caka(pred[-4:] + vety) and not NIC_NETREBA.search(posl)
    if pozadie and not plan_caka:        # „Ozvem sa, keď bude plán hotový.“ pri behu v pozadí — odsek čaká na ten beh
        plan_caka = any(krok == 'ozvem sa' and podm for v, _ in vety for _c, krok, podm in _casti(_bez_citatov(v))) \
            and not NIC_NETREBA.search(posl)
    dovod = 'bez ohlásenia'
    dedi = False                         # podmienka vety končiacej dvojbodkou platí aj pre položky zoznamu pod ňou
    for i, (povodna, polozka) in enumerate(vety):
        if dedi and not polozka:
            dedi = False
        v = _bez_citatov(povodna)
        predosla = _bez_citatov(vety[i - 1][0] if i > 0 else (pred[-1][0] if pred else ''))
        podm_vety = _podmienene(v) or bool(CAKA.search(v) and not BUDIK.search(v))
        if v.endswith(':'):
            dedi = podm_vety or bool(BEZI.search(v))
        if not OHLAS.search(v):
            continue
        if dedi and polozka:
            dovod = 'podmienené'
            continue
        # predošlá veta o čakaní kryje „Potom …“ — okrem budíka („Nastavil som budík na 08:10. Potom sa pozriem …“ je parkovanie)
        if NAVAZUJE.search(v) and predosla and (_podmienene(predosla)
                                                or (CAKA.search(predosla) and not BUDIK.search(predosla))
                                                or CAKA_NA_CLOVEKA.search(predosla)
                                                or re.search(r'\b(beží|bežia|dobieha|dobiehajú)\b', predosla)):
            dovod = 'podmienené'
            continue
        if ZAVISLOST.search(ABY.sub('', v)):   # „Urobím to naraz — potrebujem od teba len jedno rozhodnutie“
            dovod = 'podmienené'
            continue
        # posudzuje sa po častiach vety: „Pokračujem ďalej; ozvem sa, keď budem mať dávku“ je ohlásená práca,
        # „Nastavil som budík — pokračujem kusom BK“ tiež; podmienka platí aj pre časti za ňou („Ak potvrdíš, prepíšem
        # plán — doplním …“)
        casti = _casti(v)
        for j, (cast, krok, podm) in enumerate(casti):
            if not krok:
                continue
            if CAKA.search(cast):
                dovod = 'čaká na beh alebo čas'
                continue
            if podm:
                dovod = 'podmienené'
                continue
            # čakanie v predošlej časti vety platí aj pre krok za ňou („⏸ ČAKÁM na I-203 … — stavať začnem po nich“)
            if any((CAKA.search(c) and not BUDIK.search(c)) or BEZI.search(c) for c, _, _ in casti[:j]) \
                    and not NEZAVISLE.search(cast):
                dovod = 'čaká na beh alebo čas'
                continue
            if krok == 'ozvem sa' and not PODM.search(cast):
                continue                 # „Ozvem sa.“ samo nie je krok; „ozvem sa, keď ich prejdem“ je
            # „Časovač beží o 09:11; potom overím …“ — časť nadväzuje na čakanie v predošlej časti (budík sám nie je čakanie)
            if j > 0 and NAVAZUJE.search(cast.strip()) and BEZI.search(casti[j - 1][0]):
                dovod = 'podmienené'
                continue
            if plan_caka and not NEZAVISLE.search(v):
                dovod = 'podmienené (plán čaká)'
                continue
            if ziadost_pred and not NEZAVISLE.search(v) and not TERAZ.search(v):
                dovod = 'čaká na úkon človeka'
                continue
            if pozadie and not NEZAVISLE.search(v) and (PORADIE.search(cast) or PODM.search(cast)
                                                        or NAVAZUJE.search(v) or beh_opis):
                dovod = 'čaká na beh v pozadí'
                continue
            return povodna[:220], None
    return None, dovod


def ohlaseny_krok(text, pozadie=False):
    """Veta posledného odseku, v ktorej partner ohlásil vlastný ďalší krok, ktorý nič neblokuje — alebo None."""
    return posudok(text, pozadie)[0]


def beh_v_pozadi(vstup):
    """V sedení beží práca v pozadí (vstup háčika Stop: background_tasks — príkaz, podagent, workflow), ktorá partnera
    po dobehnutí zobudí sama. Stráženie (monitor) sa neráta — beží aj celé hodiny."""
    try:
        return any(isinstance(t, dict) and 'monitor' not in str(t.get('type', '')).lower()
                   and str(t.get('status', '')).lower() not in ('completed', 'failed', 'killed', 'stopped', 'cancelled')
                   for t in (vstup.get('background_tasks') or []))
    except Exception:
        return False


def veta_zasahu(veta):
    """Text pre partnera (nie pre človeka) — bez technických slov; predpona „NG, plynulosť:“ ho označí pre meranie
    (ng-skody-kolegov.py ho ráta ako zásah strážcu plynulosti)."""
    return ('NG, plynulosť: Ohlásil si ďalší krok („' + veta.strip() + '“) — urob ho teraz, alebo napíš, čo ťa blokuje. '
            'Ak čakáš na človeka, na beh, ktorý ešte nedobehol, alebo na čas, povedz to jednou vetou a ťah skonči; '
            'v tomto ťahu ťa druhýkrát nezastavím. Človekovi o tomto zásahu nepíš.')


def posledna_sprava(stopa, bajtov=524288):
    """Text poslednej správy partnera z konca stopy (len keď ho háčik nedostal na vstupe) a to, odkiaľ sedenie beží.
    Číta len koniec súboru — stopa má aj desiatky MB."""
    import json
    with open(stopa, 'rb') as f:
        f.seek(0, 2)
        od = max(0, f.tell() - bajtov)
        f.seek(od)
        riadky = f.read().decode('utf-8', errors='ignore').split('\n')
    for l in reversed(riadky[1:] if od else riadky):
        if '"assistant"' not in l:
            continue
        try:
            o = json.loads(l)
        except Exception:
            continue
        if o.get('type') != 'assistant' or o.get('isSidechain'):
            continue
        c = (o.get('message') or {}).get('content')
        tx = c if isinstance(c, str) else '\n'.join(b.get('text', '') for b in (c or [])
                                                     if isinstance(b, dict) and b.get('type') == 'text')
        if tx.strip():
            return tx, str(o.get('entrypoint') or '')
    return '', ''


def tah_stroja(stopa, bajtov=524288):
    """Ťah začal beh bez človeka. claude -p spustený zo sedenia vo VS Code zdedí CLAUDE_CODE_ENTRYPOINT=claude-vscode
    (Claude Code prepíše na sdk-cli len hodnotu „cli“) a jeho stopa má entrypoint claude-vscode — rozlíši ho len
    turnOrigin „sdk“ pri správe, ktorá ťah začala (ng-stav.py; 20. 9. „Vraciaš sa k projektu …“ v aigrip). Číta len
    koniec stopy a len vtedy, keď by sa zasahovalo; chyba = False."""
    import json
    try:
        with open(stopa, 'rb') as f:
            f.seek(0, 2)
            od = max(0, f.tell() - bajtov)
            f.seek(od)
            riadky = f.read().decode('utf-8', errors='ignore').split('\n')
        for l in reversed(riadky[1:] if od else riadky):
            if '"user"' not in l:
                continue
            try:
                o = json.loads(l)
            except Exception:
                continue
            if o.get('type') != 'user' or o.get('isSidechain') or o.get('isMeta'):
                continue
            c = (o.get('message') or {}).get('content')
            if isinstance(c, list) and any(isinstance(b, dict) and b.get('type') == 'tool_result' for b in c):
                continue
            return o.get('turnOrigin') == 'sdk' or str(o.get('entrypoint') or '').startswith('sdk')
    except Exception:
        pass
    return False


def plynulost(vstup, projekt_dir='', projekt=''):
    """Rozhodnutie háčika na konci ťahu (ng-kdesom-koniec.sh): text pre partnera, alebo None (pustiť).
    Pustí vždy: ťah už raz zastavil háčik (stop_hook_active — najviac jeden zásah za ťah, žiadna slučka) · beh bez
    človeka (claude -p, sdk) · podagent · aigrip u PS (tam to isté pravidlo robí strážca konca ťahu ng-koniec-tahu.py,
    ktorý volá posudok() z tohto modulu) · akúkoľvek chybu (volajúci ju zhltne)."""
    import os
    if not isinstance(vstup, dict) or vstup.get('stop_hook_active') or vstup.get('agent_id'):
        return None
    if os.environ.get('CLAUDE_CODE_ENTRYPOINT', '').startswith('sdk'):
        return None
    stopa = str(vstup.get('transcript_path') or '')
    if '/subagents/' in stopa:
        return None
    if projekt_dir and os.path.exists(os.path.join(projekt_dir, '.aigrip', 'hooks', 'ng-fronta-koniec.sh')):
        import pwd
        if pwd.getpwuid(os.getuid()).pw_name == 'pstrocka':
            return None
    text = vstup.get('last_assistant_message')
    if not isinstance(text, str):
        if not stopa or not os.path.exists(stopa):
            return None
        text, vstup_od = posledna_sprava(stopa)
        if vstup_od.startswith('sdk'):
            return None
    veta = ohlaseny_krok(text, beh_v_pozadi(vstup))
    if not veta:
        return None
    if stopa and tah_stroja(stopa):      # claude -p spustený zo sedenia vo VS Code (premenná zdedená, pozri tah_stroja)
        return None
    _zapis(dict(projekt=projekt or os.path.basename(projekt_dir.rstrip('/')),
                sid=str(vstup.get('session_id') or '')[:8], pravidlo='1a', veta=veta[:160]))
    return veta_zasahu(veta)


def _zapis(zaznam):
    """Zásah sa zapíše do domova toho, kto pracuje (~/.aigrip-ng/plynulost.jsonl) — u kolegu nie do domova PS;
    správca ho zbiera spolu so stopami (ng-stopy-kolegov.sh). Zápis nikdy nezastaví háčik."""
    import os, json, datetime
    try:
        d = os.path.join(os.path.expanduser('~'), '.aigrip-ng')
        os.makedirs(d, exist_ok=True)
        f = os.path.join(d, 'plynulost.jsonl')
        with open(f, 'a', encoding='utf-8') as h:
            h.write(json.dumps(dict(ts=datetime.datetime.now().astimezone().isoformat(timespec='seconds'), **zaznam),
                               ensure_ascii=False) + '\n')
        if os.path.getsize(f) > 1_000_000:            # drží sa posledných ~1500 zásahov
            riadky = open(f, encoding='utf-8', errors='ignore').read().split('\n')[-1500:]
            open(f + '.tmp', 'w', encoding='utf-8').write('\n'.join(riadky))
            os.replace(f + '.tmp', f)
    except Exception:
        pass


if __name__ == '__main__':
    v, d = posudok(sys.stdin.read())
    print(ZNACKA)
    print(('ZASTAVIŤ: ' + v) if v else ('pustiť: ' + d))
