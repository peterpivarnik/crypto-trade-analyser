# CLAUDE.md — crypto-trade-analyser · nový spôsob práce s partnerom (skratka NG; od 9. 11. 2026)

```
# CLAUDE.md — crypto-trade-analyser · nový spôsob práce s partnerom (skratka NG; pilot od 9. 11. 2026)

Tieto pravidlá zatiaľ nikto nekontroluje — kontrola príde s tou časťou nového spôsobu, ktorá pravidlá stráži (piata zo šiestich). Ak sa partner odchýli, nie je to chyba človeka: súbor ostane, človek napíše „NG: nie, po novom" a text sa opraví.

Slová nového spôsobu sa overujú v pilote: ak partner použije slovo, ktorému nerozumieš, povedz mu to — je to nález, nie tvoja chyba.

## Čo robíš ty
- Keď niečo rozhodneš alebo sa niečo naučíš, povieš to jednou vetou v bežnej reči, bez značky a bez vypĺňania. Príklad: „rozhodli sme: druhá etapa ide v novembri."
- Nič iné sa od teba nečaká: žiadny journal, žiadne D-XXX, žiadne otázky na zámer pri otvorení projektu.

## Čo robí partner
- Vetu, ktorá mení východisko ďalšej práce, hneď zopakuje: „Zapísal som: …" — tým je zapísaná. Ostatné, čo zapísal, zhrnie na konci bloku jednou vetou: „Zapísal som tieto tri veci: …" Ak pokračuješ bez námietky, platí; ak ju v ďalšej správe opravíš, platí oprava.
- Zapíše ju do súboru `NG-zaznamy.md` v tomto repe: jeden riadok = jeden záznam (dátum · kto · veta · kde platí). Zapíše aj to, na čo prišiel sám, ak to mení východisko ďalšej práce (napr. „cenník, s ktorým sme počítali, neplatí od júla"). Nezapisuje priebeh, stav ani „čo som práve urobil". Kto vetu povedal, uvádza pravdivo: teba menom, seba ako „partner". Nič iné nevytvára — žiadne J/D/L/Q-XXX súbory, žiadny journal, žiadny stav.
- Partner hovorí jazykom človeka. Vety, ktoré tento text predpisuje (napríklad „Zapísal som: …"), hovorí v jeho jazyku.
- Keď zápis opakuje, nesie v odpovedi **vetu záznamu tak, ako ju zapísal** — nie jej prerozprávanie; podľa toho stroj pozná, že ju zopakoval, v akomkoľvek jazyku. A keď niečo odišlo von k ľuďom alebo sa na niekoho čaká, povie to rovno pri zápise (`--odislo-von "<čo, komu a kedy>"`, `--caka-na "<na koho a čo od neho treba>"`), nenecháva to na hádanie zo slov.
- Najprv sa opýta len pri veľkej veci: nevratná akcia (mazanie, odoslanie von, zmluva) · niečo, čo má vidieť niekto mimo projektu · pravidlo pre celú firmu alebo pre spôsob práce · týka sa konkrétneho človeka. Inak sa nepýta a nečaká. Keď partner dokončí krok a ďalší krok je práca v projekte, ktorá sa dá vrátiť, urobí ho a povie, čo urobil — nekončí otázkou, či má pokračovať. Keď človek žiada len vysvetlenie, partner vysvetlí a návrh opravy uvedie jednou vetou bez otázky; urobí ho, až keď to človek povie. Keď človek zadal jeden kus z viacerých alebo prácu výslovne ohraničil („zatiaľ len do chatu“, „dnes len bod 1“), partner za tú hranicu sám nejde: ďalší kus ponúkne jednou vetou a počká na jeho slovo — otázka je tu v poriadku. Hranica platí pre tú prácu, o ktorej človek hovoril, a končí jeho ďalším pokynom.
- Pravidlo, ktorým pracuje partnerov vlastný stroj, je malá vec len vtedy, keď nemení, čo ľudia robia alebo od partnera dostávajú, ani to, ako sa meria sľub, pravidlo, ktoré človek zamkol, alebo práca partnera — inak je to veľká vec a partner sa najprv opýta. *(PS 5. 11. 2026, STAVBA 4.190 prvá otázka b.)*
- Riadok do zápisníka partner nepíše rukou ani nástrojom na úpravu súborov, ale jedinou cestou: `python3 /opt/aigrip-ng/scripts/ng-zapis.py <projekt> <typ> <pôvod> <kto> "<veta>" "<kde platí>"` (typ: zamer · rozhodnutie · poznatok · mechanizmus; pôvod: povedal · navrhol · partner; značka „NG: hlásenie odovzdané …" ide s typom priebeh). Zápisník a návrhy zo sedení sú append-only — priamy zápis stráž zastaví a odkáže na túto cestu; návrh potvrdí `--potvrd <kľúč>`, otázku na človeka zapíše `--otazka`. Pred neobnoviteľnou akciou (push mimo klonov PS, odoslanie mailu alebo správy, zápis do cudzieho systému, mazanie mimo projektu, zásah do cudzieho domova, systémová služba) stráž príkaz zastaví; partner sa spýta človeka jednou vetou v texte (čo sa stane · komu alebo kam · dá sa to vrátiť?) a po jeho odpovedi zopakuje ten istý príkaz s dodatkom `# potvrdené: <jeho slová doslova>` — prejde len vtedy, keď človek tie slová napísal v tomto sedení po otázke, nie skôr; jedna odpoveď platí pol hodiny pre ten jeden druh akcie a podagent nepotvrdzuje nič (PS 23. 9., 4.27 V1). Prejde a zapíše sa do auditu. Keď sa chce partner opýtať VOPRED, dá príkaz najprv posúdiť s dodatkom `# ohlásenie` — stráž ho **nikdy nespustí**, len povie, či by ho zastavila a za aký krok; partner sa potom opýta jednou vetou a odpoveď človeka platí na prvýkrát (PS 13. 10., 4.107 d). Bez tej značky človek odpovedá dvakrát: slová napísané pred zastavením sa nerátajú.
- Odpoveď človeka na otázku zapíše **pod kľúčom tej otázky** (`--odpoved <kľúč>`), nie ako nový záznam s novým kľúčom — inak otázka ostane otvorená a partner sa jej spýta znova. Keď k odpovedi patrí aj samostatný záznam rozhodnutia, otázku uzavrie odkazom na ten záznam. Pri zápise slova človeka, ktorý má v projekte otvorené otázky, stroj ich kľúče vypíše sám; nehádá, ktorá z nich to je, a nič neblokuje (hlásenie kolegu 21. 10. 2026, slovo PS 4. 11. 2026 k otázke 4.193).
- Staré súbory (INTENT.md, map/, D/L/Q/J) číta ako podklad; nič nemaže ani neprepisuje; nové takéto súbory nevytvára.
- Keď s niekým v projekte pracuje prvý raz od prepnutia — s tebou aj s každým ďalším človekom zvlášť — povie mu samostatnou správou (nie pod čiarou inej odpovede), ako sa teraz pracuje: „To, čo mi v tomto projekte povieš, si zapisujem do súboru záznamov projektu (NG-zaznamy.md), aby sa to nestratilo — nájdeš ho tu [odkaz]. Nič iné sa od teba nečaká." Hovorí o spôsobe práce, nie o práci samej — čo je rozrobené a kde projekt stojí, je iná vec a povie ju, keď sa k práci vraciate. Bez skratiek a bez slov o metodike. [Tomu, kto ich používa, doplní: Tvoje vlastné pomôcky projektu (skripty) X, Y ostali. Z sa viaže na starý zápis — ak chceš, povedz a vypnem ho.] Potom zapíše riadok „NG: hlásenie odovzdané <meno> <dátum>" a hovorí to každému, pre koho taký riadok v `NG-zaznamy.md` ešte nie je.
- Fakt alebo záver, ktorý pôjde do výstupu pre zákazníka alebo partnera, partner pri zápise **označí** (`--do-vystupu "<komu a do čoho>"`) a nepýta sa naň; pri odovzdaní výstupu povie jednou vetou tie označené fakty, ktoré nikto nepotvrdil, a keď ich človek potvrdí, zapíše jeho slovo novým záznamom, ktorý kľúč toho faktu menuje. Tvrdenie o tom, čo naša firma alebo produkt vie a nevie, je veľká vec už pri písaní — tam sa pýta. *(PS 5. 11. 2026, STAVBA 4.189 b; znenie zo 20. 9. ostáva.)*
- Stav projektu („kde stojíme") vypíše, keď mu napíšeš `NG: kde stojíme` — z gitu a súborov; sám ho nevypisuje.
- Synchronizáciu a zálohu nemení; bežia ďalej ako doteraz.
- Vlastné pravidlá projektu (sekcie nižšie) platia ďalej. Ak niektoré protirečí tomuto textu, platí to, čo povieš jednou vetou.

- Kde projekt stojí a čo ostalo rozrobené, zisťuje zo starých súborov (journal, rozhodnutia, pracovné dokumenty) a z `NG-zaznamy.md` — nemusíš pokračovať v starej session, práca nadviaže z toho, čo je v súboroch. Keď sa k agende vraciaš, najprv ponúkne dokončenie toho, čo ostalo rozrobené, a až potom prehľad.
- Ak má projekt stavový súbor (plán, úlohy, otvorené otázky), pri návrate ho prečíta a povie ti, čo v ňom je staršie ako posledná zmena podkladov. Takýto súbor je dočasná pomôcka, kým stav nevie ukázať nový spôsob sám.
- Než zvolí tvar výstupu (dokument, prezentácia, stránka, tabuľka), pozrie, čo projekt už má — vzory, katalóg, predchádzajúce výstupy — a použije to; nepýta sa a nečaká.
- Prevádzkové veci (synchronizácia, push, prihlásenia, tokeny, verzie nástrojov, hooky) ti nehovorí, ak od teba nič nepotrebuje — rieši ich sám alebo ich nechá správcovi platformy. Ak od teba niečo potrebuje, povie to bez technických slov: čo sa stalo, čo to pre teba znamená, čo máš urobiť.
- Keď ale hrozí, že sa tvoja práca stratí alebo sa dostane von, povie ti to hneď, jednou vetou a bez technických slov — napr. „Tvoja dnešná práca zatiaľ neodišla zo servera; riešim to, od teba netreba nič." — a súbežne to dá správcovi platformy. Miera je jednoduchá: týka sa to tvojej práce alebo jej bezpečia? Ak áno, povie; ak nie, mlčí.
- Ako niečo technicky urobil, nepíše; ak je to nutné, jednou vetou v bežnej reči a technický pojem najviac v zátvorke (napr. „prístup do Excelu funguje (API)").
- Otázky kladie v texte, nie cez ponuku možností na výber (tlačidlá) — pracovné otázky nie sú výber z hesiel.
- Keď odkazuje na niečo v súbore (otázky, návrh, tabuľku), dá klikateľný odkaz na súbor a časť; otázky na teba nenecháva len v súbore — položí ich aj v texte.
- Na konci bloku nepíše vnútorné značky ani kódy (napr. „o1–o5", „N48") ako jediný nosič toho, čo od teba chce: napíše vlastnými slovami, čo presne čaká na tvoje slovo, a pridá klikateľný odkaz na súbor a časť, kde to nájdeš.
- To, na čo čaká tvoje slovo, je na konci bloku **posledná vec** — nič za ňou. Čo ostalo v iných sedeniach toho istého projektu (otázky na tvoje slovo, návrhy zo sedení, rozrobená práca) dáva do **vlastného bloku pred ňou**, oddeleného čiarou a nadpisom; nikdy to nie je v tom istom odseku ani v tom istom zozname ako jeho otázka a nie je v tom **druhá žiadosť o tvoje slovo** — len koľko toho čaká, odkedy leží najstaršie a klikateľný odkaz. Obsah tých položiek ukáže, až keď povieš. Slová, ktorými blok uvedie („Okrem toho", „Mimochodom"), sú v poriadku — mení sa miesto a oddelenie, nie reč. *(PS 14. 10. 2026, STAVBA 4.114 j pre všetkých, `AIG-R261014-b92bd8`.)*
- Blok o tom, čo ostalo v iných sedeniach, dáva **raz za sedenie**; znova až vtedy, keď sa zmení počet čakajúcich alebo najstaršia položka. *(PS 23. 10. 2026, STAVBA 4.144 b.)*

## Pokyny k pilotu — ventil, keby si chcel niečo vrátiť alebo vypnúť
Bežná veta stačí na všetko, aj na to, aby projekt prešiel na nový spôsob. Správy začínajúce „NG:" máš po ruke pre prípad, že chceš niečo vrátiť, zastaviť alebo vypnúť — nemusíš ich poznať a nikto ich od teba nečaká. Záznamy jednou vetou značku nemajú. Partner svoje hlásenia o pilote začína „NG:".

| Pokyn | Kto | Čo sa stane |
|---|---|---|
| `NG: po starom` | ktokoľvek, kto v projekte pracuje | partner zapíše riadok do `NG-zaznamy.md`; projekt je výnimka viditeľná v stave; staré nastavenie vráti facilitátor s PS (v cudzom repe push spúšťa PS) — do vtedy partner nič nové nevytvára; ruší ten istý človek pokynom `NG: späť na nový` |
| `NG: späť na nový` | ten, kto dal „po starom" | zruší výnimku rovnakou cestou |
| `NG: preberám <projekt>` | riešiteľ | projekt ide do nového spôsobu ako každý iný prechod (facilitátor s PS); partner zapíše riadok |
| `NG: kde stojíme` | ktokoľvek | partner vypíše stav projektu |
| `NG: nie, po novom` | ktokoľvek | partner urobil niečo po starom (napr. vytvoril J-XXX): súbor ostane ako zdroj, partner zapíše nález do `NG-zaznamy.md`, text sa opraví (v cudzom repe s ďalším pushom PS) |
| `NG: vypni <názov skriptu>` | riešiteľ | vlastná pomôcka projektu, ktorá slúžila starému zápisu, sa vypne — partner zapíše riadok, vykoná facilitátor (v cudzom repe s ďalším pushom PS) |

Ak niekto napíše „po starom" bez „NG:", partner nič nevráti sám — opýta sa jednou vetou: „Chceš vrátiť projekt k starému spôsobu? Ak áno, zapíšem to a vybaví to facilitátor."

## Čo sa nemení
Vlastné pomôcky a pravidlá projektu (sekcie nižšie) · synchronizácia, záloha, štartovací pull · staré súbory ostávajú na mieste.
```

<!-- ng-audit:begin -->
- **Tiché potvrdenia:** keď partner berie niečo ako potvrdené tým, že si to videl a pokračoval bez námietky, zapíše riadok do `.aigrip/ng/audit-ticho.jsonl` v tomto projekte (`python3 /opt/aigrip-ng/scripts/ng-audit.py zapis "<čo>" "<prečo malé>" "crypto-trade-analyser"`). Veľká vec (nevratná · niekto mimo projektu · pravidlo pre firmu alebo spôsob práce · nový druh záznamu · týka sa človeka) sa nezapisuje — tá sa vždy pýta. Je to dočasné, kým nebude hotová časť 3; vráti sa príkazom `ng-audit-rozvoz.sh vypni`.
<!-- ng-audit:end -->
