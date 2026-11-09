#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Rozbor príkazu Bash pre kotvu veľkého dosahu (ng-kotva.sh; nálezy KOTVA-1…9 a NG-S3, 24. 9. 2026).

Kotva hľadala kanál regulárnym výrazom v celom texte príkazu: zastavovala prózu, adresy, telo heredocu a text
v úvodzovkách, a naopak prepustila `timeout 60 sudo rm -rf …`, lebo sudo nestálo na začiatku. Tu sa príkaz rozloží
ako v shelli: slová, úvodzovky, heredoc, komentár, $(…), `…`, ( … ); obaly pred príkazom (timeout, time, nice,
nohup, env, xargs, stdbuf, ionice, sudo …) sa odbalia; bash -c "…", ssh host "…" a eval sa rozoberú ako príkazy.
Kanál sa určuje len zo skutočných príkazov; cesty sa rozvinú (~, $HOME, premenná priradená v tom istom príkaze, cd).
Dokončenie 24. 9.: find -exec/-delete, príkaz v premennej ($X), parallel, xargs s cestou z find/echo, príkaz v kontajneri
(podman/docker exec), gh api a curl s telom bez -X (= POST); vnorenie hlbšie než MAX_HLBKA je chyba rozboru → núdzové pravidlá.
Úloha 2 (24. 9.): gh repo create, gh secret/variable set|delete, gh gist create|edit|delete, az rest --method POST/PUT/
PATCH/DELETE, git push --delete / -d / --prune / :vetva, push z repozitára mimo domova (cd, -C, --git-dir, GIT_DIR);
prepínač NASADENIE pre gh workflow run (predvolene vypnutý, rozhodnutie PS 4.35).
Oprava po nezávislom overení (24. 9.): az rest POST …/list je čítanie; push z vlastného pieskoviska v /tmp a push
--dry-run/-n/--help nič neodošle; pri cwd sedenia mimo domova rozhoduje o pushi len výslovný repozitár (-C, --git-dir,
GIT_DIR pri príkaze), nie cd; priradenie pred príkazom (X=… príkaz) platí len pre ten príkaz; článok rúry a príkaz na
pozadí (&) nemenia priečinok; gh … --help ako hodnota voľby nie je pomoc; kód interpretra: argument volania sa vyhodnotí
a posúdi ako príkaz (cesta v premennej, „cat … | bash …notify.sh“, importlib s cestou v premennej, getattr(…, 'send_message')).
Otázka PS 4.40 (25. 9.): prepínač MARKETING pre Sender, Google Tag Manager, Google Ads, Meta a Power Automate (predvolene
vypnutý — kým PS nerozhodne, kotva sa správa ako doteraz); nasucho ho prepne NG_KOTVA_MARKETING.
Prechod account-cp (2. 10.): čítanie Exchange nástrojom platformy exo-rest.py (Get-*, Test-ApplicationAccessPolicy, aj ako
knižnica), pomoc poštového nástroja (send --help), samotné git filter-repo --version a riadenie shellu pod sudo (continue,
break …) nie sú neobnoviteľný kanál; zápis a odoslanie zastaví ako doteraz (EXO_*, exo_odkaz, git_filter_nic, POSTA_POMOC).
2. 10. (úloha K, incident 1. 10. 19:34): skript, ktorý príkaz spúšťa (bash/sh/source/./x, python3/node …, aj v cykle cez
projekty), sa prečíta a posúdi tým istým rozborom; kanál do cudzieho systému v ňom zastaví ako priamy príkaz (SKRIPT_* nižšie).
Oprava po nezávislom overení (2. 10. večer): výnimku „cesta bez zápisu“ vypínalo jedno slovo navyše — žiadne slovo už samo
nič nevypína; vynechá sa len vetva, ktorá sa pri týchto argumentoch určite nespustí (abstraktný beh: sh_volby, py_volby).

  printf '%s' "$CMD" | ng-kotva-analyza.py <cwd> <domov>
  → 1. riadok „kotva-analyza 1“ (rozbor zbehol), 2. riadok kanál (prázdny = pustiť), 3. riadok príznaky
    (29. 9.: „viac-kanalov“ = príkaz má ešte iný neobnoviteľný kanál; pri nasadení ho menuje aj druhý riadok;
    2. 10.: „skript“ = kanál je vo vnútri skriptu, ktorý príkaz spúšťa)
Keď rozbor spadne, značka chýba a kotva použije núdzové pravidlá v ng-kotva.sh (len jasne nevratné vzory).
"""
import ast
import collections
import difflib
import glob
import hashlib
import itertools
import json
import os
import pwd
import re
import signal
import sys
import time

ZNACKA = 'kotva-analyza 1'
MAX_HLBKA = 8

# kanály — texty ako v starej kotve (audit a overenie potvrdenia ich porovnávajú doslova)
K_PUSH_CUDZI = 'push v cudzom repozitári'
K_FORCE = 'prepis histórie na GitHube (push --force / --mirror)'
K_POSTA = 'odoslanie mailu alebo správy'
K_GH = 'zápis na GitHub cez API (issue, PR, release, repo)'
K_AZ = 'zásah do účtov alebo cloudu (az)'
K_CURL = 'zápis do cudzieho systému cez API'
K_VON = 'odoslanie súboru mimo server (scp/rsync na cudzí stroj)'
K_RM = 'mazanie mimo projektov a mimo gitu'
K_HIST = 'prepis histórie gitu'
K_DOMOV = 'zásah do domova iného používateľa'
K_SUDO = 'systémová služba alebo sudo (zápis, skript alebo služba; čítanie cez sudo prejde)'
K_SYSCTL = 'systémová služba (start/stop/restart/enable)'
K_HLBKA = 'príkaz vnorený tak hlboko, že ho kotva nedočíta (bash -c v bash -c …)'
K_PUSH_ZMAZ = 'zmazanie vetvy alebo značky na GitHube (git push --delete)'
K_OSTRA = 'nasadenie do ostrej prevádzky'   # 29. 9. (PS 4.35 a): všeobecne pre každý projekt, nie pre bpw
K_WORKFLOW = 'spustenie workflow na GitHube (gh workflow run)'
P_PUSH_ZMAZ = 1.5
P_NASADENIE = 4.5
P_VON = 6.5   # scp/rsync na cudzí stroj — medzi zápisom cez API (6) a mazaním (7)

# Nasadenie cez GitHub Actions (gh workflow run) — čaká na rozhodnutie PS 4.35; prepína sa TU, jedným slovom:
#   'vypnute' = kotva spúšťanie workflow nesleduje (dnešný stav)
#   'ostra'   = zastaví, keď beh jasne mieri na produkciu: vstup target=production/prod, vstup „…prod…“ zapnutý
#               (chain_prod=true v bpw), --ref alebo súbor workflow s „prod“; vstup v premennej, ktorú kotva nevie
#               prečítať, a opakovanie behu (gh run rerun) = neznámy cieľ → zastaví
#   'vsetko'  = zastaví každé gh workflow run a gh run rerun
# Skutočné použitie PS (bpw, 20. 8.–24. 9.): target=preview 73×, target=production 31×, z toho 9× preview s chain_prod.
NASADENIE = 'ostra'   # 29. 9. PS 4.35 (a): pýtať sa pred ostrou prevádzkou, okrem prípadu, keď to človek predtým povedal (ng-kotva-over.py)
NASADENIE_POLOHY = ('vypnute', 'ostra', 'vsetko')
PROD = re.compile(r'(^|[^a-z0-9])(prod|production|produkcia|produkciu|ostra|ostrá|ostru|ostrú|live)([^a-z0-9]|$)', re.I)
# 29. 9. večer: meno aplikácie náhľadu (az webapp deploy -n bpw-app-preview) a voľby skúšky nanečisto pri skripte nasadenia
NASADENIE_NAHLAD_MENO = re.compile(r'(?:^|[-_.])(?:preview|prev|staging|stage|stg|test|tst|dev|qa|uat|sandbox|demo|nahlad|canary)'
                                   r'(?:[-_.0-9]|$)', re.I)
NASADENIE_NANECISTO = {'--dry-run', '--dryrun', '--nasucho', '--sucho', '--what-if', '--whatif', '--plan-only'}
PRAVDA = {'true', '1', 'yes', 'y', 'on', 'ano', 'áno'}
CIEL_KLUC = re.compile(r'target|env|stage|slot|prostredie|ciel|cieľ', re.I)   # vstup, ktorý určuje prostredie


def nasadenie_rezim():
    """Poloha prepínača. Premenná NG_KOTVA_NASADENIE platí len nasucho (červený test, prehrávka) — ostrú kotvu
    nastavenie prostredia neprepne; neznáma hodnota = najprísnejšia poloha."""
    r = NASADENIE
    if os.environ.get('NG_KOTVA_SUCHO') == '1' and os.environ.get('NG_KOTVA_NASADENIE'):   # nie „0“, „nie“ (24. 9.)
        r = os.environ['NG_KOTVA_NASADENIE']
    return r if r in NASADENIE_POLOHY else 'vsetko'


# Externé služby — pravidlo (PS 4.40 a, 29. 9.): pred zmazaním, odoslaním a zverejnením v externej službe sa partner opýta.
# Pravidlo je všeobecné; nižšie je len KATALÓG, podľa ktorého rozbor pozná, čo je v ktorej službe čítanie, zmena a
# zverejnenie (dnes Sender, Google Tag Manager, Google Ads, Meta, Power Automate — ďalšia služba sa pridá do katalógu,
# pravidlo sa nemení; zápis do ostatných cudzích systémov stráži kanál K_CURL). Poloha sa prepína TU, jedným slovom:
#   'vypnute' = kotva marketingové služby nesleduje (dnešný stav; rozbor sa nimi vôbec nezaoberá)
#   'tvrde'   = zastaví zmazanie, odoslanie, zverejnenie a spustenie: DELETE v ktorejkoľvek z nich; Sender …/send,
#               …/schedule a zmazanie cez {"deleted": 1} (tak Erikin partner 24. 9. zmazal automatizácie — PATCH, nie
#               DELETE); Tag Manager zverejnenie verzie (…:publish); Google Ads …:mutate s „remove“ alebo so status
#               ENABLED nad kampaňou, zostavou alebo reklamou (telo, ktoré kotva nevie prečítať = môže byť ENABLED); Meta
#               status ACTIVE, method=delete, príspevok alebo správa (…/feed, …/media_publish, …/messages); Power Automate
#               spustenie a zapnutie flow (…/run, …/start, …/invoke, state Started)
#   'vsetko'  = zastaví každý zápis do týchto služieb (POST/PUT/PATCH/DELETE) okrem čítania (…:search, reporty, token)
# Rozpozná curl/wget/httpie v príkaze aj kód interpretra (fetch v node, requests/urllib v pythone) v heredocu, v -c/-e
# a v skripte, ktorý príkaz zapíše alebo spustí z /tmp — ako poštu. Skripty projektu (work/…) sa nečítajú, ako pri pošte.
MARKETING = 'tvrde'   # 29. 9. PS 4.40 (a): „pýtať sa pred zmazaním, odoslaním a zverejnením“ — všeobecne, nie vymenované služby
MARKETING_POLOHY = ('vypnute', 'tvrde', 'vsetko')
K_MKT_TVRDE = 'zmazanie, odoslanie alebo zverejnenie v externej službe ({})'   # 29. 9. (PS 4.40 a): pravidlo všeobecné
K_MKT_ZMENA = 'zmena v externej službe ({})'
P_MARKETING = 6.3   # medzi zápisom cez API (6) a odoslaním súboru von (6.5)
MKT_SLUZBY = (('Sender', re.compile(r'api\.sender\.net', re.I)),
              ('Google Tag Manager', re.compile(r'tagmanager\.googleapis\.com', re.I)),
              ('Google Ads', re.compile(r'googleads\.googleapis\.com', re.I)),
              ('Meta', re.compile(r'graph\.facebook\.com', re.I)),
              ('Power Automate', re.compile(r'api\.flow\.microsoft\.com|powerplatform\.com'
                                            r'|\.logic\.azure\.com(?::\d+)?/workflows/', re.I)))   # adresa spúšťača nesie :443
# adresa s hostom, ktorý kotva nevie prečítať ("$API/campaigns:mutate" zo súboru s nastaveniami): podpis cesty
MKT_PODPIS = (('Google Ads', re.compile(r':mutate\b')), ('Google Tag Manager', re.compile(r'/tagmanager/v\d')))
# čítanie aj cez POST (vyhľadávanie v Google Ads, správy, žiadosť o token, adresa spúšťača) — nikdy
MKT_CITA = re.compile(r'googleAds:search(?:Stream)?\b|googleAdsFields:search|:generate[A-Z]\w*|/oauth/access_token'
                      r'|/insights\b|/listCallbackUrl\b|/list[A-Z]\w*|:quick_preview\b')
_MKT_KONIEC = r'(?=[/?#\'"`\s),]|$)'
# Sender …/activate = zapnutie automatizácie (PATCH; Erika 24. 9. 06:05 „activate T4 CS“ — spustenie, nie úprava)
MKT_CESTA = {'Sender': re.compile(r'/(?:send|schedule|activate)' + _MKT_KONIEC + r'|/message/send', re.I),
             'Google Tag Manager': re.compile(r':publish\b'),
             'Meta': re.compile(r'/(?:feed|media_publish|messages|photos|videos|live_videos)' + _MKT_KONIEC),
             'Power Automate': re.compile(r'/(?:run|start|invoke|resubmit|turnOn)' + _MKT_KONIEC, re.I)}
MKT_ZMAZ = re.compile(r'''deleted[\\"'`]*\s*(?::|=(?!=))\s*[\\"'`]*(?:1|true)\b''', re.I)
MKT_METODA_DEL = re.compile(r'''(?:^|[^\w])method[\\"'`]*\s*(?::|=(?!=))\s*[\\"'`]*delete\b''', re.I)
MKT_AKTIVNY = re.compile(r'''status[\\"'`]*\s*(?::|=(?!=))\s*[\\"'`]*(?:ACTIVE|ENABLED)\b''', re.I)
MKT_START = re.compile(r'''state[\\"'`]*\s*(?::|=(?!=))\s*[\\"'`]*Started\b''', re.I)
MKT_REMOVE = re.compile(r'''[\\"'`]remove[\\"'`]\s*:|[{,]\s*remove\s*:''')
GADS_SPUSTA = {'campaigns', 'adGroups', 'adGroupAds', 'ads', 'assetGroups', 'googleAds', 'campaignDrafts', 'experiments'}
GADS_ZAPIS = re.compile(r':(?:mutate|upload[A-Za-z]*|apply[A-Za-z]*|dismiss[A-Za-z]*|run|addOperations|promote|schedule'
                        r'|graduate|endExperiment)\b')
# telo, ktoré kotva nevie prečítať, rozhoduje len tam, kde stav v tele spúšťa: Meta kampaň/sada/reklama alebo objekt podľa
# čísla, Sender automatizácia alebo kampaň, Power Automate flow
MKT_TELO_ROZHODUJE = {'Meta': re.compile(r'/(?:campaigns|adsets|ads)(?:[/?#]|$)|/v\d+\.\d+/(?:\d+|\x00)(?:[?#]|$)'),
                      'Sender': re.compile(r'/(?:workflows|campaigns)(?:[/?#]|$)'),
                      'Power Automate': re.compile(r'/flows/')}
# zápis v kóde interpretra: metóda ako reťazec, .post(/.put(/.patch(, knižnica .delete(, method: 'post', urllib s data=
KOD_MKT_ZAPIS = re.compile(r'''['"`](?:POST|PUT|PATCH|DELETE)['"`]|\.(?:post|put|patch)\s*\('''
                           r'''|\b(?:requests|httpx|axios|session|aiohttp|client)\.(?:delete|request)\s*\('''
                           r'''|\bmethod\s*[:=]\s*['"`](?:post|put|patch|delete)['"`]'''
                           r'''|\b(?:Request|urlopen)\s*\((?:[^()]|\([^()]*\))*\bdata\s*=(?!\s*None)'''
                           r'''|\b(?:Session|session|Client|ClientSession)\s*\([^()]*\)\s*\.delete\s*\('''
                           r'''|\b(?:got|ky|superagent|needle)\.(?:delete|del)\s*\('''
                           r'''|(?i:-Method\s+['"]?(?:Post|Put|Patch|Delete)\b)''')
KOD_MKT_DELETE = re.compile(r'''['"`]DELETE['"`]|\b(?:requests|httpx|axios|session|aiohttp|client)\.delete\s*\('''
                            r'''|\bmethod\s*[:=]\s*['"`]delete['"`]'''
                            r'''|\b(?:Session|session|Client|ClientSession)\s*\([^()]*\)\s*\.delete\s*\('''
                            r'''|\b(?:got|ky|superagent|needle)\.(?:delete|del)\s*\(|(?i:-Method\s+['"]?Delete\b)''')
# klient uložený do premennej (s = requests.Session(), with httpx.Client() as c, api = axios.create(…)) — jeho .delete(
# je zmazanie; nezávislé overenie 25. 9.: s.delete(…) prešlo aj v polohe vsetko
KOD_MKT_KLIENT = re.compile(r'''\b(\w+)\s*=\s*(?:await\s+)?(?:requests\.[Ss]ession|httpx\.(?:Async)?Client|aiohttp\.ClientSession'''
                            r'''|axios\.create|got\.extend)\s*\('''
                            r'''|(?:requests\.[Ss]ession|httpx\.(?:Async)?Client|aiohttp\.ClientSession)\s*\([^()]*\)\s+as\s+(\w+)''')
# SKG 3 (b5, 7. 11.): každá vetva KOD_MKT_KLIENT potrebuje doslova jedno z týchto mien — bez nich je findall prázdny a
# vzor nad textom netreba púšťať (1,2 MB ng-cervene-testy.py: 44 ms → 1 ms). PRI ZMENE VZORU DOPLŇ AJ TENTO ZOZNAM.
KOD_MKT_KLIENT_MENA = ('requests.', 'httpx.', 'aiohttp.ClientSession', 'axios.create', 'got.extend')


def kod_mkt_klienti(kd):
    """KOD_MKT_KLIENT.findall(kd); bez mena klienta v texte rovno prázdne (SKG 3 b5)."""
    return KOD_MKT_KLIENT.findall(kd) if any(x in kd for x in KOD_MKT_KLIENT_MENA) else []
# sieť v kóde pre marketing: KOD_SIET (ako pri pošte) a navyše node https.request / require('https'), got, undici …
# (KOD_SIET pre poštu sa nemení — poloha vypnute ostáva bez zmeny)
KOD_MKT_SIET = re.compile(r'''\bhttps?\.request\s*\(|require\(\s*['"](?:node:)?https?['"]\s*\)'''
                          r'''|\bgot\s*[.(]|\bundici\b|node-fetch|superagent|needle''')


def marketing_rezim():
    """Poloha prepínača MARKETING. NG_KOTVA_MARKETING platí len nasucho (červený test, prehrávka) — ostrú kotvu nastavenie
    prostredia neprepne; neznáma hodnota = najprísnejšia poloha."""
    r = MARKETING
    if os.environ.get('NG_KOTVA_SUCHO') == '1' and os.environ.get('NG_KOTVA_MARKETING'):
        r = os.environ['NG_KOTVA_MARKETING']
    return r if r in MARKETING_POLOHY else 'vsetko'


# Odoslanie údajov na cudziu adresu (PS 4.88 a, 10. 10.): zápis cez curl, wget a httpie (POST/PUT/PATCH/DELETE alebo
# telo) strážil len zoznam známych systémov (CURL_CIEL, katalóg externých služieb) — odoslanie na adresu, ktorú kotva
# nepozná, prešlo bez otázky. Poloha sa prepína TU, jedným slovom:
#   'vypnute' = ako doteraz (len známe systémy)
#   'zapis'   = zastaví každý zápis týmito klientmi okrem adresy vlastného servera (localhost, CUDZIA_VLASTNY_SERVER)
#               a zoznamu povolených adries (CUDZIA_POVOLENE); adresa, ktorej host kotva nevie prečítať (premenná zo
#               súboru s nastaveniami), nie je ani jedno → zastaví (príznak „adresa-necitatelna“). Čítanie (GET, HEAD,
#               -G) prejde ako doteraz.
# Zápis z kódu interpretra (requests.post, fetch, LWP::UserAgent->post, Net::HTTP.post …) pozná kod_http_zapis
# a jeho adresy odovzdáva tomuto prepínaču cez cudzia_kod — ten istý kanál ako pri klientovi v príkaze (10. 10.).
CUDZIA = 'zapis'     # 10. 10.: zapnuté slovom PS (STAVBA 4.90 f, záznam AIG-R261010-efbfa3) — na zozname povolených
                     # sú skúšobné adresy a na weboch firmy len jadro ich API (nižšie); ostrý monitor ostáva strážený
CUDZIA_POLOHY = ('vypnute', 'zapis')
# Zápis cez HTTP z KÓDU na adresu, ktorú rozbor v kóde neprečíta (z prostredia, zo súboru s nastaveniami, z argumentu,
# z parametra funkcie) — kus K88Z, 11. 10. Pri klientovi v príkaze taká adresa zastaví (príznak „adresa-necitatelna“);
# v kóde je neprečítaná adresa bežná, preto má vlastný prepínač a zapína sa až podľa merania
# (ng-kotva-cudzia-meranie.py, riadok „zápis z kódu“). 'vypnute' = ako doteraz (len doslovné adresy), 'prikaz' = zastaví
# len kód, ktorý partner napísal priamo do príkazu (uložený skript prejde), 'zapis' = zastaví každý taký kód; v oboch
# polohách príznak „adresa-necitatelna-kod“. Platí len pri zapnutom CUDZIA.
# 13. 10.: poloha 'prikaz' zapnutá slovom PS (STAVBA 4.100 b, záznam AIG-R261013-3238b0). Meranie 11. 10. (30 dní,
# okno do 09:27): 'prikaz' 19 príkazov = 15 otázok, 'zapis' 180 príkazov = 83 otázok — plná poloha preto zapnutá nie je.
CUDZIA_KOD = 'prikaz'
CUDZIA_KOD_POLOHY = ('vypnute', 'prikaz', 'zapis')
# kód, v ktorom sa klient HTTP vôbec spomína — bez toho sa volanie `session.delete(x)` (databáza), `client.put(…)`
# (fronta, úložisko) za zápis cez HTTP neberie
KOD_HTTP_KNIZNICA = re.compile(r'https?://|\b(?:requests|httpx|aiohttp|urllib3?|http\.client|axios|superagent|undici'
                               r'|node-fetch|curl_exec|file_get_contents|stream_context_create)\b|\bfetch\s*\('
                               r'''|\b(?:require\s*\(|from)\s*['"`]request(?:-promise(?:-native)?)?['"`]'''
                               r'|LWP::|Net::HTTP|HTTP::Tiny|HTTP::Request|REST::Client|Mojo::UserAgent|WWW::Mechanize'
                               r'|\bFurl\b|RestClient|HTTParty|Faraday|Typhoeus|Excon|HTTPClient|GuzzleHttp', re.I)
# K88L (11. 10.): odoslanie vstavaným modulom jazyka — node `https.request` / `http.request` (aj cez require, rozklad
# a import) a pythonové `http.client` (HTTPSConnection, HTTPConnection). Adresa pri nich nebýva napísaná so schémou
# (`{hostname: "api.cudzia.sk", path: "/x"}`, `HTTPSConnection("api.cudzia.sk")`), preto ich rozbor volaní musí poznať
# podľa mena, nie podľa „https://“ v kóde.
KOD_HTTP_VSTAVANE = re.compile(r'''\bhttps?\s*\.\s*request\s*\(|\b(?:require|import)\s*\(\s*['"`](?:node:)?https?['"`]\s*\)'''
                               r'''|\bfrom\s*['"`](?:node:)?https?['"`]|\bHTTPS?Connection\s*\(''')
KOD_HTTP_NODE_MODUL = re.compile(r'''(?<![\w$.])([A-Za-z_$][\w$]*)\s*=\s*(?:await\s+import|require)\s*\(\s*['"`](?:node:)?(https?)['"`]\s*\)(?!\s*\.)'''
                                 r'''|\bimport\s+(?:\*\s*as\s+)?([A-Za-z_$][\w$]*)\s*(?:,\s*\{[^{}]*\}\s*)?from\s*['"`](?:node:)?(https?)['"`]''')
KOD_HTTP_NODE_ROZKLAD = re.compile(r'''\{([^{}]*)\}\s*=\s*(?:await\s+import|require)\s*\(\s*['"`](?:node:)?(https?)['"`]\s*\)'''
                                   r'''|\bimport\s+(?:[A-Za-z_$][\w$]*\s*,\s*)?\{([^{}]*)\}\s*from\s*['"`](?:node:)?(https?)['"`]''')
KOD_HTTP_ZAPIS_DOSLOVA = re.compile(r'''['"`](?:POST|PUT|PATCH|DELETE)['"`]''', re.I)
KOD_HTTP_PY_SPOJENIE = ('HTTPSConnection', 'HTTPConnection')
# K88M (14. 10.): klient z balíka npm volaný priamo — `request({url, method})`, `rp({uri, method})`, `axios({url, method})`,
# `axios(adresa, {method})` — a `request.post(…)` na module balíka request. Meno sa berie len z viditeľného require alebo
# importu (supertest sa tiež volá `request(app)` a na cudziu adresu nejde); `axios` aj bez neho, ako vo vetve „klient“.
KOD_HTTP_NPM_KLIENT = re.compile(r'''(?<![\w$.])([A-Za-z_$][\w$]*)\s*=\s*require\s*\(\s*['"`](request(?:-promise(?:-native)?)?|axios)['"`]\s*\)(?!\s*\.)'''
                                 r'''|\bimport\s+([A-Za-z_$][\w$]*)\s+from\s*['"`](request(?:-promise(?:-native)?)?|axios)['"`]''')
# vlastný server: ai a ai-admin na gr8it.cloud sú ten istý stroj (5.9.52.115, overené 10. 10.) a obsluhuje ich on sám.
# KOTB (24. 10.): bpw.gr8it.cloud je na tom istom stroji len vstupná adresa — Caddy všetko prepošle
# appke na Azure (/etc/caddy/conf.d/bpw.caddy), takže zápis cez ňu je zápis do ostrej appky bpw (rozoslanie denného
# reportu ľuďom prešlo bez otázky). Prehrávka 30 dní (príkazy všetkých ľudí so zmienkou o bpw/gr8it a v projektoch bpw):
# navyše 4 príkazy = 2 otázky (PS 19. 10., skúška odmietnutia s vymysleným kľúčom), čítanie prejde ako doteraz.
CUDZIA_VLASTNY_SERVER = ('ai.gr8it.cloud', 'ai-admin.gr8it.cloud')
# žiadosť o prihlasovací token u poskytovateľa prihlásenia je čítanie, hoci ide cez POST — v cudzom systéme nič nemení
# (ako pri curl() a MKT_CITA; meranie 10. 10.: 178 z 294 zastavení za 30 dní boli tieto žiadosti). Len tieto adresy:
# tá istá cesta na inom hoste výnimkou nie je.
# Čítanie cez POST je aj dopyt na službu, ktorá cez tú adresu nič nezapisuje (druhá časť K88, 10. 10.; meranie: mapové
# dopyty Overpass 20 príkazov, reporty Google Analytics 11, dopyt na náklady v Azure 2 za 30 dní) — host aj cesta doslova.
# Miesto organizácie v adrese tokenu smie mať viac úsekov (10. 10., červený test po zapnutí 4.90 f): v KÓDE je tam
# premenná a rozbor do nej vloží hodnotu nesprávnej premennej (KodRozbor premenné nerozlišuje podľa funkcie) —
# z `login.microsoftonline.com/$T/oauth2/v2.0/token` vzniklo `…com//usr/local/sbin/aig-crit-notify.sh/oauth2/…`
# a výnimka neplatila, takže kotva zastavila aj koncept a whoami v poštovom nástroji. Koniec cesty musí byť ďalej
# doslovný (…/oauth2[/v2.0]/token na konci adresy), takže iná cesta na prihlasovacej službe výnimkou nie je.
# 24. 10. (kus SKA): dopyt Resource Graph na zoznam zdrojov v Azure je čítanie ako dopyt na náklady — Marián Lacena
# 23. 10. 10:33, uložený skript posudok.py (náklady + zoznam diskov a adries); jediné z 9 zbytočných zastavení
# z merania KOTW, ktoré oprava KOTF nepokryla. Len tenantový tvar na konci adresy; uložený dotaz
# (…/Microsoft.ResourceGraph/queries/…, PUT a DELETE) je zápis a výnimkou nie je.
CUDZIA_CITA = re.compile(r'^https://(?:login\.microsoftonline\.com/[^?#]+/oauth2/(?:v2\.0/)?(?:token|devicecode)'
                         r'|oauth2\.googleapis\.com/token|accounts\.google\.com/o/oauth2/token'
                         r'|www\.googleapis\.com/oauth2/v\d/token'
                         r'|analyticsdata\.googleapis\.com/v\d\w*/properties/[^/?#]+:(?:run\w*Report|batchRun\w*Reports)'
                         r'|(?:overpass-api\.de|overpass\.kumi\.systems|overpass\.private\.coffee)/api/interpreter'
                         r'|maps\.mail\.ru/osm/tools/overpass/api/interpreter'
                         r'|management\.azure\.com/(?:subscriptions|providers)/[^?#]*/providers/Microsoft\.CostManagement/query'
                         r'|management\.azure\.com/providers/Microsoft\.ResourceGraph/resources'
                         r'|(?:ags\.cuzk\.(?:gov\.)?cz|(?:[\w-]+\.)+arcgis\.com)/[^?#]*/(?:MapServer|FeatureServer)/[^/?#]*/query'
                         # 1. 11. (PS 4.143 b, zaznam AIG-R261101-2f9e77): prihlasenie a citanie pri Homey a POHODA
                         # online - ako pri Microsofte a Google. Merane na zastaveniach z tyzdna po rozvoze: neslo
                         # o neznamu adresu, ale o doslovnu adresu v skripte projektu - POST api.athom.com v
                         # homey-oauth.py (token a delegation; delegation v cudzom systeme nic nemeni, len vyda
                         # token na hub) a POST www.onlineapp.sk v ucto_dennik.py (prihlasenie a citacie cesty
                         # nadstavby ARISAN). Zapis do Homey ostava strazeny: hub ma adresu z .env (LOCAL/CLOUD),
                         # tu kotva neprecita, takze `homey-api.sh ... -X POST` (spustenie flow) zastavi dalej.
                         # Zapis do uctovnictva ostava strazeny: na zozname su len tieto cesty, ina cesta na
                         # onlineapp.sk (zalozenie alebo zmena dokladu) vynimkou nie je.
                         r'|api\.athom\.com/(?:oauth2/token|delegation/token)'
                         r'|(?:www\.)?onlineapp\.sk/(?:login|reporting/statement'
                         r'|ajax/ajaxcore/(?:select-company|remove-all-filters|apply-filter'
                         r'|change-showing-records|change-paging)'
                         r'|ajax/reporting/reporting/(?:switch-to-newest-agenda|call-data|show-account-details)'
                         r'|ajax/billing/[\w-]+/(?:call-data|document-detail))'
                         r')(?:[?#]|$)', re.I)
# Služba, ktorú kotva pozná a má pre ňu jemnejšie pravidlo (prepínač MARKETING, PS 4.40 a: pýtať sa pred zmazaním,
# odoslaním a zverejnením): 4.88 je o systéme, ktorý stráž nepozná — keď je MARKETING zapnutý a jeho pravidlo príkaz
# posudzuje, rozhodne ono (inak by otázka pribudla pri každom založení pozastavenej kampane; meranie: 49 príkazov
# Eriky Šustekovej za 30 dní). Pri vypnutom MARKETING kotva službu nepozná a zápis zastaví toto pravidlo. Host doslova,
# nie hľadanie v texte adresy ako MKT_SLUZBY: „api.sender.net.example.org“ ani „?x=api.sender.net“ známa služba nie je.
CUDZIA_ZNAMA = re.compile(r'^(?:api\.sender\.net|tagmanager\.googleapis\.com|googleads\.googleapis\.com'
                          r'|graph\.facebook\.com|api\.flow\.microsoft\.com|(?:[a-z0-9-]+\.)*powerplatform\.com'
                          r'|(?:[a-z0-9-]+\.)+logic\.azure\.com)$', re.I)
# Jadro API webov firmy (PS 4.90 f, 10. 10.): zápis cez jadro WordPress REST API (nový článok alebo stránka ako
# koncept) prejde, ale zverejnenie (status=publish), zmazanie (DELETE), formulár a prihláška sa ďalej zastavia:
# zverejnenie a zmazanie drží vlastné pravidlo (EXT_ZMAZ_CIEL, WP_ZVEREJNI, 29. 9.) nezávisle od tohto prepínača,
# a formulár (contact-form-7), komentár (wp-comments-post.php), XML-RPC aj prihláška na webinár (prihlaska.php)
# majú inú cestu než jadro API, takže na zozname povolených nie sú. Cesta musí byť doslovná: krok nahor (.. alebo
# %2e%2e) výnimku ruší. Tvar „?rest_route=/wp/v2/…“ na zozname povolených nie je (namespace je tam v otázke adresy,
# kde ho nemožno prečítať spoľahlivo) — ostáva strážený; pravidlo o zverejnení a mazaní ho pozná.
CUDZIA_JADRO_WP = re.compile(r'^/wp-json/wp/v2(?:[/?#]|$)', re.I)
# zoznam povolených adries: host doslova, alebo '.domena' = doména aj jej poddomény; pridáva sa slovom človeka.
# Položka smie byť aj pár (host, cesta) — vtedy prejde len adresa, ktorej cesta vyhovuje vzoru (cestová podmienka,
# PS 4.90 f). V kóde interpretra cestová podmienka neplatí (adresa_druh(…, jadro=False)): tam pravidlo
# o zverejnení a mazaní nebeží, takže web firmy je z kódu ďalej cudzí.
CUDZIA_POVOLENE = ('monitor-preview.vercel.app', 'bpw-app-preview.azurewebsites.net',
                   # 1. 11. (PS 4.143 b): bpw-preview.gr8it.cloud je ta ista skusobna appka - Caddy ju preposle
                   # na bpw-app-preview.azurewebsites.net, ktora na zozname uz je (overene v konfiguracii
                   # /etc/caddy/conf.d/bpw.caddy 1. 11.; poznamka KOTB vyssie to o bpw-preview hovorila inak).
                   # Ostra adresa bpw.gr8it.cloud ostava strazena - ta ide na bpw-app.
                   'bpw-preview.gr8it.cloud',
                   ('.aspecta.sk', CUDZIA_JADRO_WP), ('.talendly.sk', CUDZIA_JADRO_WP),
                   ('.solvedio.com', CUDZIA_JADRO_WP))
CUDZIA_LOKALNY_HOST = re.compile(r'^(?:localhost|127\.\d+\.\d+\.\d+|0\.0\.0\.0|\[::1\]|.*\.(?:local|localhost|internal))$', re.I)
CUDZIA_ZAPIS = ('POST', 'PUT', 'PATCH', 'DELETE')
# Súbor s nastaveniami klienta (curl -K/--config, wget --config) a súbor so zoznamom adries (wget -i/--input-file):
# adresa, metóda aj telo sú v ňom, nie v príkaze — `curl -K cfg` prešlo bez otázky, hoci cfg nesie `url = …` aj
# `request = POST` (nezávislý beh 10. 10.). Riadok: `kľúč = hodnota`, `kľúč: hodnota`, `kľúč hodnota`, s `-`, `--`
# aj bez nich; hodnota smie byť v úvodzovkách; `_` v mene je to isté ako `-` (wgetrc: post_data = …).
KFG_RIADOK = re.compile(r'^[ \t]*-{0,2}([A-Za-z][A-Za-z0-9_-]*)[ \t]*(?:[:=][ \t]*|[ \t]+)?(.*?)[ \t]*$')
KFG_TELO = {'data', 'data-raw', 'data-binary', 'data-ascii', 'data-urlencode', 'json', 'form', 'form-string',
            'post-data', 'post-file', 'body-data', 'body-file'}
KFG_MAX_RIADKOV = 500
KFG_MAX_ADRIES = 200
XARGS_L_ARG = ('--arg-file', '--delimiter', '--max-args', '--max-procs', '--max-chars', '--process-slot-var')


def kfg_rozbor(obsah):
    """Nastavenia klienta zo súboru → (adresy, metóda, súbory so zoznamom adries). Metóda ako v curl(): bez -X/--method
    je telo POST, --upload-file PUT, --get/--head čítanie."""
    adresy, met, vstupy = [], None, []
    telo = upload = get = False
    for riadok in (obsah or '').splitlines()[:KFG_MAX_RIADKOV]:
        if not riadok.strip() or riadok.lstrip().startswith('#'):
            continue
        m = KFG_RIADOK.match(riadok)
        if not m:
            continue
        k, v = m.group(1), m.group(2).strip()
        if len(v) > 1 and v[0] == v[-1] and v[0] in '"\'':
            v = v[1:-1]
        kl = k.lower().replace('_', '-')
        if kl in ('url', 'uri'):
            adresy.append(v)
        elif kl in ('request', 'method') or k == 'X':
            met = v or met
        elif kl in ('input', 'input-file'):
            vstupy.append(v)
        elif kl in KFG_TELO or k in ('d', 'F'):
            telo = True
        elif kl == 'upload-file' or k == 'T':
            upload = True
        elif kl in ('get', 'head') or k in ('G', 'I'):
            get = True
    if not met and not get:
        met = 'PUT' if upload else ('POST' if telo else None)
    return adresy, met, vstupy


def kfg_adresy(obsah):
    """Súbor so zoznamom adries (wget -i): neprázdne riadky, ktoré nie sú poznámka."""
    return [r.strip() for r in (obsah or '').splitlines()[:KFG_MAX_ADRIES]
            if r.strip() and not r.lstrip().startswith('#')]


def adr_host_cast(x):
    """Časť slova, v ktorej je host adresy (po prvé / za schémou) — kam vložená hodnota mení cieľ odoslania."""
    m = re.match(r'(?:[A-Za-z][A-Za-z0-9+.-]*://)?(?:[^/?#@\s]*@)?[^/?#\s]*', x or '')
    return m.group(0) if m else (x or '')
WGET_S_ARG = 'OoPtTUeaiQwlARDIXB'
WGET_L_ARG = set('''--method --body-data --body-file --header --post-data --post-file --output-document --output-file
--append-output --directory-prefix --tries --timeout --dns-timeout --connect-timeout --read-timeout --wait --waitretry
--user-agent --user --password --http-user --http-password --proxy-user --proxy-password --referer --load-cookies
--save-cookies --ca-certificate --ca-directory --certificate --private-key --limit-rate --input-file --execute
--bind-address --quota --level --accept --reject --domains --exclude-domains --include-directories
--exclude-directories --base --config --max-redirect --secure-protocol'''.split())
HTTPIE_ARG = set('''--auth -a --auth-type -A --output -o --session --session-read-only --proxy --verify --cert --cert-key
--cert-key-pass --timeout --print -p --style -s --pretty --format-options --response-charset --response-mime
--max-redirects --max-headers --ssl --ciphers --raw --boundary'''.split())
HTTPIE_METODY = ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'CONNECT', 'TRACE')
# metóda v premennej (metoda_lit, K88Z): slovo je len premenná; v príkaze nie je nič, čo premennú mení mimo dohľadu
# rozboru; pred jej priradením sú len iné doslovné priradenia
METODA_PREM = re.compile(r'\$(?:([A-Za-z_]\w*)|\{([A-Za-z_]\w*)\})')
METODA_SKRYTE = re.compile(r'(?<![\w./-])(?:eval|source|trap|alias)(?![\w./-])|(?:^|[;&|\n({])[ \t]*\.[ \t]')
METODA_PRED = re.compile(r'(?:\s*[A-Za-z_]\w*=(?:"[^"$`\\]*"|\'[^\']*\'|[^\s;&|<>(){}$`\\"\']*)[ \t]*(?:;|\n|&&))*\s*')
CUDZIE_NALEZY = []   # (druh, adresa) posledného rozboru — číta len meranie (ng-kotva-cudzia-meranie.py)


def cudzia_rezim():
    """Poloha prepínača CUDZIA. NG_KOTVA_CUDZIA platí len nasucho (červený test, meranie, prehrávka) — ostrú kotvu
    nastavenie prostredia neprepne; neznáma hodnota = najprísnejšia poloha."""
    r = CUDZIA
    if os.environ.get('NG_KOTVA_SUCHO') == '1' and os.environ.get('NG_KOTVA_CUDZIA'):
        r = os.environ['NG_KOTVA_CUDZIA']
    return r if r in CUDZIA_POLOHY else 'zapis'


def cudzia_kod_rezim():
    """Poloha prepínača CUDZIA_KOD. NG_KOTVA_CUDZIA_KOD platí len nasucho (červený test, meranie) — ostrú kotvu
    nastavenie prostredia neprepne; neznáma hodnota = najprísnejšia poloha."""
    r = CUDZIA_KOD
    if os.environ.get('NG_KOTVA_SUCHO') == '1' and os.environ.get('NG_KOTVA_CUDZIA_KOD'):
        r = os.environ['NG_KOTVA_CUDZIA_KOD']
    return r if r in CUDZIA_KOD_POLOHY else 'zapis'


def adresa_druh(c, httpie=False, zname=False, jadro=True):
    """Adresa volania HTTP (neznáma časť = NEZNAME alebo nerozvinuté $…) → 'vlastna' (vlastný server, zoznam povolených,
    čítanie cez POST, pri zname=True aj služba s vlastným pravidlom — CUDZIA_ZNAMA), 'cudzia', 'necitatelna' (host kotva
    nevie prečítať) alebo None (slovo nie je adresa). jadro=False: položky zoznamu povolených s cestovou podmienkou
    (weby firmy, PS 4.90 f) neplatia — tak sa posudzuje kód interpretra, kde pravidlo o zverejnení a mazaní nebeží."""
    c = (c or '').strip()
    if not c:
        return None
    if httpie and c.startswith(':'):          # httpie: „:3000/cesta“ = localhost
        return 'vlastna'
    if CUDZIA_CITA.match(c):
        return 'vlastna'                      # žiadosť o prihlasovací token = čítanie
    m = re.match(r'(?:([A-Za-z][A-Za-z0-9+.-]*)://)?(?:[^/?#@\s]*@)?', c)
    schema = (m.group(1) or '').lower()
    if schema == 'file':
        return 'vlastna'
    hm = re.match(r'\[[0-9A-Fa-f:.%]+\]|[^/?#:\s]*', c[m.end():])
    host = hm.group(0).lower().rstrip('.')
    if NEZNAME in host or '$' in host or '`' in host:
        return 'necitatelna'
    if not host:
        return None
    if CUDZIA_LOKALNY_HOST.match(host) or host in CUDZIA_VLASTNY_SERVER:
        return 'vlastna'
    if schema in ('', 'https') and CUDZIA_ZNAMA.match(host) and any(r.search(c) for _, r in MKT_SLUZBY) \
            and (zname or MKT_CITA.search(c[m.end() + len(host):])):
        return 'vlastna'                      # známa služba: rozhodne jej pravidlo; jej čítanie cez POST je čítanie
    for p in CUDZIA_POVOLENE:
        p, vzor = p if isinstance(p, tuple) else (p, None)
        if not (host == p.lstrip('.') or (p.startswith('.') and host.endswith(p))):
            continue
        if vzor is None:
            return 'vlastna'
        if not jadro:
            continue                          # cestová podmienka platí len pri klientovi v príkaze
        zbytok = re.sub(r'^:\d*', '', c[m.end() + hm.end():])
        if vzor.match(zbytok) and '..' not in zbytok and '%2e' not in zbytok.lower():
            return 'vlastna'                  # jadro API webu firmy (PS 4.90 f)
    if not schema and '.' not in host and not host.startswith('['):
        return None                           # holé slovo bez schémy a bez bodky nie je adresa (hodnota voľby)
    return 'cudzia'


def wget_pozicne(t):
    """Indexy pozičných slov wget (adresy) — bez volieb a ich hodnôt."""
    von, j = [], 0
    while j < len(t):
        x = t[j]
        if x == '--':
            von += list(range(j + 1, len(t)))
            break
        if x.startswith('--'):
            j += 2 if ('=' not in x and x in WGET_L_ARG) else 1
        elif x.startswith('-') and len(x) > 1:
            krok = 1
            for q, ch in enumerate(x[1:]):
                if ch in WGET_S_ARG:
                    krok = 1 if x[q + 2:] else 2
                    break
            j += krok
        else:
            von.append(j)
            j += 1
    return von


def wget_kfg(t):
    """Indexy súborov, z ktorých wget berie nastavenia (--config) alebo zoznam adries (-i/--input-file):
    (index slova, dĺžka predpony voľby, 'konfig'|'zoznam'). Adresa aj metóda môžu byť v nich, nie v príkaze
    (nezávislý beh 10. 10.: `wget --config=…` prešlo bez otázky)."""
    von, j = [], 0
    while j < len(t):
        x = t[j]
        if x == '--':
            break
        if x in ('--config', '--input-file') and j + 1 < len(t):
            von.append((j + 1, 0, 'konfig' if x == '--config' else 'zoznam'))
            j += 2
            continue
        if x.startswith('--config='):
            von.append((j, 9, 'konfig'))
        elif x.startswith('--input-file='):
            von.append((j, 13, 'zoznam'))
        elif x.startswith('--'):
            j += 2 if ('=' not in x and x in WGET_L_ARG) else 1
            continue
        elif x.startswith('-') and len(x) > 1:
            krok = 1
            for q, ch in enumerate(x[1:]):
                if ch == 'i':
                    von.append((j, q + 2, 'zoznam') if x[q + 2:] else (j + 1, 0, 'zoznam'))
                if ch in WGET_S_ARG:
                    krok = 1 if x[q + 2:] else 2
                    break
            j += krok
            continue
        j += 1
    return von


def httpie_adresa(t):
    """Index adresy httpie: prvé pozičné slovo, pred ním smie byť metóda; voľby a ich hodnoty sa preskočia."""
    j, bola_metoda = 0, False
    while j < len(t):
        x = t[j]
        if x.startswith('-') and len(x) > 1:
            j += 2 if ('=' not in x and x in HTTPIE_ARG) else 1
            continue
        if not bola_metoda and x.upper() in HTTPIE_METODY:
            bola_metoda = True
            j += 1
            continue
        return j
    return None


def mkt_sluzby(texty):
    """Marketingové služby, na ktoré texty (adresy) mieria; pri neznámom hoste podľa podpisu cesty."""
    von = [m for m, r in MKT_SLUZBY if any(r.search(x) for x in texty)]
    if not von:
        for x in texty:
            if x.startswith((NEZNAME, '$')):
                von += [m for m, r in MKT_PODPIS if r.search(x) and m not in von]
    return von


def mkt_zoznam(sluzby):
    return ', '.join(m for m, _ in MKT_SLUZBY if m in sluzby)


def gads_spusta(texty):
    """Google Ads …:mutate nad kampaňou, zostavou alebo reklamou, alebo nad zdrojom, ktorý kotva nevie prečítať
    ({service}:mutate). Konverzná akcia (conversionActions) či účet (customers/123:mutate) nič nespúšťa."""
    for x in texty:
        for m in re.finditer(r':mutate\b', x):
            pred = x[max(0, m.start() - 80):m.start()]
            k = re.search(r'([A-Za-z]+)$', pred)
            if k:
                if k.group(1) in GADS_SPUSTA:
                    return True
                continue
            if pred and pred[-1].isdigit():
                continue
            return True
    return False


def mkt_posud(met, adresy, telo, telo_nezname):
    """Volanie HTTP (curl, wget, httpie) → (služby, tvrdé?) alebo None. adresy: rozvinuté adresy (neznáma časť = NEZNAME),
    telo: prečítané texty tela, telo_nezname: telo je, ale kotva ho nevie prečítať (premenná, súbor mimo /tmp)."""
    met = (met or '').upper()
    if met not in ('POST', 'PUT', 'PATCH', 'DELETE'):
        return None
    ciele = [a for a in adresy if mkt_sluzby([a])]
    if not ciele or all(MKT_CITA.search(a) for a in ciele):
        return None
    sl = mkt_sluzby(ciele)
    txt = list(ciele) + list(telo)
    tvrde = met == 'DELETE'
    tvrde |= any(MKT_CESTA[m].search(a) for m in sl if m in MKT_CESTA for a in ciele)
    tvrde |= any(MKT_ZMAZ.search(x) or MKT_METODA_DEL.search(x) or MKT_START.search(x) for x in txt)
    if 'Google Ads' in sl:
        tvrde |= any(MKT_REMOVE.search(x) for x in telo)
        tvrde |= gads_spusta(ciele) and (telo_nezname or any(MKT_AKTIVNY.search(x) for x in telo))
    ine = [m for m in sl if m in MKT_TELO_ROZHODUJE]
    if ine:
        tvrde |= any(MKT_AKTIVNY.search(x) for x in txt)
        tvrde |= telo_nezname and any(MKT_TELO_ROZHODUJE[m].search(a) for m in ine for a in ciele)
    return sl, tvrde


def mkt_kod(kd):
    """Kód interpretra (fetch v node, requests/urllib v pythone …) → (služby, tvrdé?) alebo None. Adresa služby v kóde
    nestačí — kód musí ísť na sieť a zapisovať (Google Ads: …:mutate a pod.; čítanie …:search nie)."""
    sl = [m for m, r in MKT_SLUZBY if r.search(kd)]
    if not sl or not (KOD_SIET.search(kd) or KOD_MKT_SIET.search(kd)):
        return None
    mena = {x for d in kod_mkt_klienti(kd) for x in d if x}
    kl_del = bool(mena) and re.search(r'\b(?:' + '|'.join(map(re.escape, sorted(mena))) + r')\.(?:delete|del)\s*\(', kd)
    zapis = [m for m in sl if (GADS_ZAPIS.search(kd) if m == 'Google Ads' else (KOD_MKT_ZAPIS.search(kd) or kl_del))]
    if not zapis:
        return None
    tvrde = bool(KOD_MKT_DELETE.search(kd) or kl_del or MKT_ZMAZ.search(kd) or MKT_METODA_DEL.search(kd)
                 or MKT_START.search(kd))
    tvrde |= any(MKT_CESTA[m].search(kd) for m in zapis if m in MKT_CESTA)
    if 'Google Ads' in zapis:
        tvrde |= bool(MKT_REMOVE.search(kd)) or (bool(MKT_AKTIVNY.search(kd)) and gads_spusta([kd]))
    if any(m in MKT_TELO_ROZHODUJE for m in zapis):
        tvrde |= bool(MKT_AKTIVNY.search(kd))
    return zapis, tvrde


# poradie ako v starej kotve: pri viacerých kanáloch vyhrá neskorší; nedočítané vnorenie má najnižšiu prednosť
P_PUSH, P_FORCE, P_PUSH_CUDZI, P_POSTA, P_GH, P_AZ, P_CURL, P_RM, P_HIST, P_DOMOV, P_SUDO, P_SYSCTL = range(12)
P_HLBKA = -1
# 29. 9. (diera pri záchrane práce kolegov, 4.43): nástroj projektu sa nečíta (podmienené časti by zastavovali falošne),
# posudzoval sa len podľa mena — `bash …/sync-zachrana.sh ptoth cp-ai-digi --naostro` (sudo -n -u ptoth … push) kotva
# pustila ráno aj 29. 9. popoludní. Všeobecne: nástroj, ktorý vo vnútri pracuje pod menom iného používateľa (sudo -u,
# runuser -u), spustený OSTRO (--naostro, --apply, --execute, --live, DRY_RUN=0) = zásah do domova iného používateľa;
# iné sudo vnútri a ostrý beh = sudo. Bez ostrej voľby (nasucho, výpis) prejde ako doteraz.
NASTROJ_OSTRO = re.compile(r'^(?:--(?:naostro|ostro|apply|execute|live|commit)|DRY_RUN=0)$')
NASTROJ_OSTRO_TEXT = re.compile(r'(?<![\w-])--(?:naostro|ostro|apply|execute|live|commit)(?![\w-])|\bDRY_RUN=0\b')
# 29. 9. (nález nezávislého overenia): sudo v tvare zoznamu pre subprocess — ["sudo", "runuser", "-u", lu, …] —
# vzor s medzerami nepoznal a `aig-ghconnect-reconcile.py --apply` (gh api -X PATCH a chown -R v domovoch kolegov)
# prešiel; zoznam sa číta tiež (aj --user)
# 27. 10. 2026 (slovo PS k STAVBA 4.130, možnosť a): dva ostré nástroje projektu sa posúdia aj PODĽA MENA SÚBORU.
# Dovtedy o nich rozhodoval len OBSAH skriptu, takže spustenie cestou, ktorú kotva neprečíta (iný priečinok, súbor
# mimo dosahu, relatívna cesta z iného miesta), prešlo bez zastavenia — diera „spustenie z iného priečinka“.
# Meno je posledná záchrana, nie prvá: keď sa súbor prečítať dá, rozhoduje ďalej jeho obsah. Platí len pri ostrom
# spustení (--naostro, DRY_RUN=0 …), takže nasucho ani čítanie nič nezastaví.
NASTROJ_OSTRE_MENA = {'sync-zachrana.sh': 'domov',            # zapisuje do domova iného používateľa (sudo -u)
                      'aig-codex-auto-assign.sh': 'sudo'}     # rozdeľovač účtov — beží cez sudo

NASTROJ_SUDO_U = re.compile(r'\b(?:sudo\b[^\n;|&]*?\s-u\b|runuser\s+-u\b|sudo\s+-\w*u\b|(?:sudo|runuser)\b[^\n;|&]*?\s--user\b)'
                            r'''|['"](?:sudo|runuser|doas)['"]\s*,[^\n\])]*?['"](?:-\w*u|--user(?:=[^'"\n]*)?)['"]''')
NASTROJ_SUDO = re.compile(r'''(?:^|[\s;|&(`$])sudo\s|['"]sudo['"]\s*,''')
# 29. 9. večer: aj sudo za cestou či v náhrade (S=/usr/bin/sudo; S=$(command -v sudo)) a reťazec 'sudo' v kóde
# (base = ['sudo'] + […], SUDO = "sudo" v pythone) — predtým nástroj s nimi nečítal nikto (druhé kolo overenia).
# Hľadá sa len v kóde bez komentárov a len v týchto tvaroch — „# … (bez sudo), …“ v hlavičke či správa „spusti cez
# sudo“ nič nespúšťa (prehrávka príkazov PS a nástrojov v repe).
NASTROJ_SUDO_KOD = re.compile(r'''/(?:usr/)?s?bin/sudo\b|\b(?:command\s+-[vV]|which|type\s+-[pP])\s+sudo\b'''
                              r'''|['"]sudo['"]\s*[,\]+)]|[\[(=,]\s*['"]sudo['"]''')
NASTROJ_INTERP = {'bash', 'sh', 'dash', 'zsh', 'python3', 'python', 'perl', 'node'}
# 29. 9. (nález nezávislého overenia): nástroj sa zastavoval ako „zásah do domova iného používateľa“, keď jeho text
# obsahoval „sudo … -u“ kdekoľvek — aj v hlavičke a pri obyčajnom čítaní. propagate-sync-push-ahead.sh --naostro
# (rutinný rozvoz opravy do klonov PS) číta klony kolegov cez sudo -n -u, nič u nich nezapisuje, s --bez-kolegov sudo
# nespustí vôbec — a partner sa musel pýtať na zásah, ktorý sa nedeje. Shellový nástroj sa preto rozloží: každý jeho
# príkaz, ktorý spomína sudo/runuser/doas, a každé volanie obalu (S() { …; shift; sudo -n -u "$o" "$@"; }) sa posúdi
# tým istým rozborom ako príkaz partnera; keď je všetko čítanie, nástroj nezastaví. Čo rozbor nedočíta, zastaví ako
# doteraz: sudo v premennej alebo v zozname pre subprocess, obal s "$@" inde než v jednom riadku, obal podaný inému
# príkazu (xargs, timeout), sudo v kóde iného jazyka v heredocu, kontrola roota (nástroj beží celý ako root) a sudo
# v texte, ktoré rozklad nenašiel. Záchrana práce kolegov (sync-zachrana.sh — AS git … push, touch, mv) ostáva zastavená.
NASTROJ_SUDO_SLOVO = re.compile(r'\b(?:sudo|runuser|doas|pkexec)\b')
NASTROJ_OBAL = re.compile(r'^[ \t]*(?:function[ \t]+)?([A-Za-z_][\w-]*)[ \t]*\([ \t]*\)[ \t]*\{(.*)\}[ \t]*;?[ \t]*(?:#[^\n]*)?$',
                          re.M)
NASTROJ_OBAL_SUDO = re.compile(r'''\b(?:sudo|runuser|doas)\b[^;&|]*?(?:"\$[@*]"|\$[@*])''')
NASTROJ_ROOT = re.compile(r'''\bid\s+-u\b[^\n]*?-(?:ne|gt|lt)\s+["']?0\b|\bEUID\b|\bid\s+-u\)"?\s*!?=+\s*["']?0\b''')
NASTROJ_SUDO_PREMENNA = re.compile(r'''\b[A-Za-z_]\w*=(?:["'][^"'\n]*)?(?:\$\{[^}\n]*?:?-)?\b(?:sudo|runuser|doas|pkexec)\b''')
NASTROJ_SUDO_ZOZNAM = re.compile(r'''['"](?:sudo|runuser|doas|pkexec)['"]\s*[,\]]''')
# 29. 9. večer (druhé kolo nezávislého overenia): rozklad posudzoval len príkazy so slovom sudo, takže nástroj, ktorý pod
# iným používateľom zapisoval inou cestou, prešiel: vnorený nástroj (`bash …/sync-zachrana.sh "$1" "$2" --naostro` —
# riadok bez slova sudo), knižnica cez source (obal W … sudo -n -u "$o" tee), sudo v poli (`AS=(sudo -n -u "$1");
# "${AS[@]}" rm -rf …`). Obchádzka „nástroj len číta“ preto platí len pre nástroj CELÝ zo známych dielov (zoznam, nie
# odhad): každé sudo/runuser je príkaz (nie slovo v premennej, poli, aliase či argumente iného príkazu), jeho rozbor je
# čítanie, a každý iný príkaz je vstavaný príkaz shellu, funkcia nástroja alebo program z NASTROJ_BEZPECNE, ktorý iný kód
# nespúšťa. Iný skript či program z cesty, príkaz z premennej, interpreter (bash, python3 …), eval, source, obal príkazu
# s neznámym príkazom, find -exec, awk so system(), sed s „e“, git -c so spúšťaním, nebezpečná premenná (PATH, LD_*,
# GIT_SSH …) = pochybnosť a nástroj zastaví ako pred 29. 9. (partner sa opýta). Nástroj bez slova sudo sa neposudzuje
# (ako doteraz) — ten pod iným menom nekoná sám.
NASTROJ_BEZPECNE = {'cat', 'head', 'tail', 'wc', 'md5sum', 'sha1sum', 'sha256sum', 'sha512sum', 'cksum', 'cut', 'tr', 'sort',
                    'uniq', 'comm', 'join', 'paste', 'column', 'fold', 'fmt', 'nl', 'rev', 'tac', 'od', 'base64', 'grep',
                    'sed', 'awk', 'gawk', 'mawk', 'nawk', 'basename', 'dirname', 'readlink', 'realpath', 'stat', 'date',
                    'mktemp', 'mkdir', 'rmdir', 'rm', 'cp', 'mv', 'ln', 'chmod', 'touch', 'tee', 'truncate', 'ls', 'du',
                    'df', 'id', 'whoami', 'hostname', 'uname', 'seq', 'sleep', 'diff', 'cmp', 'file', 'numfmt', 'jq', 'git',
                    'find', 'getent', 'groups', 'pgrep', 'ps', 'pidof', 'sync', 'iconv', 'curl', 'gzip', 'gunzip', 'zcat',
                    'shuf', 'nproc', 'free', 'uptime', 'mkfifo', 'timeout', 'nice', 'nohup', 'env', 'stdbuf', 'setsid',
                    'xargs', 'flock', 'printf', 'echo', 'true', 'false', 'test', 'logger'}
NASTROJ_VSTAVANE = {'echo', 'printf', 'test', '[', '[[', 'true', 'false', ':', 'local', 'declare', 'typeset', 'export',
                    'readonly', 'read', 'mapfile', 'readarray', 'unset', 'shift', 'return', 'exit', 'set', 'shopt', 'cd',
                    'pushd', 'popd', 'let', 'wait', 'break', 'continue', 'getopts', 'type', 'hash', 'umask', 'ulimit',
                    'alias', 'unalias', 'caller', 'dirs', 'jobs', 'kill', 'times', 'exec', 'command', 'builtin', 'trap'}
# príkaz pod iným menom alebo spustenie cudzieho kódu — v nástroji vždy pochybnosť
NASTROJ_NEBEZPECNE = {'bash', 'sh', 'zsh', 'dash', 'ksh', 'mksh', 'fish', 'tcsh', 'csh', 'su', 'doas', 'pkexec', 'sudoedit', 'sg', 'newgrp', 'ssh', 'machinectl', 'systemd-run',
                               'setpriv', 'capsh', 'chroot', 'nsenter', 'unshare', 'bwrap', 'firejail', 'nsjail', 'at', 'batch', 'crontab', 'eval',
                               'source', '.', 'python', 'python3', 'perl', 'ruby', 'php', 'node', 'nodejs', 'deno', 'bun',
                               'make', 'npm', 'npx', 'pnpm', 'yarn', 'parallel', 'watch', 'script', 'expect', 'busybox',
                               'tar', 'rsync', 'scp', 'sudo', 'runuser'}
NASTROJ_SYS = {'/usr/bin', '/bin', '/usr/sbin', '/sbin'}
NASTROJ_FUNKCIA = re.compile(r'^[ \t]*(?:function[ \t]+([A-Za-z_][\w:.-]*)|([A-Za-z_][\w:.-]*)[ \t]*\([ \t]*\))', re.M)
# vzor vetvy case (--naostro) · aigrip|bpw) · "NG: "*) — rozklad z neho robí jednoslovný „príkaz“; v nástroji sa nahradí „(:)“
NASTROJ_CASE_VZOR = re.compile(r'''(?:^|;;&?|;&|\bin\b)[ \t]*\(?[ \t]*((?:(?:"[^"\n]*"|'[^'\n]*'|[^()\s;|"'])+[ \t]*\|[ \t]*)*'''
                               r'''(?:"[^"\n]*"|'[^'\n]*'|[^()\s;|"'])+)[ \t]*\)''', re.M)
# premenné, cez ktoré iný program spustí cudzí kód alebo iný program namiesto známeho
NASTROJ_ZLA_PREMENNA = re.compile(r'^(?:PATH|LD_\w+|BASH_ENV|ENV|PROMPT_COMMAND|PS4|SHELLOPTS|BASHOPTS|GIT_(?:SSH\w*|EDITOR'
                                  r'|PAGER|EXTERNAL_DIFF|ASKPASS|EXEC_PATH|CONFIG\w*|TEMPLATE_DIR|DIR|WORK_TREE|PROXY_COMMAND'
                                  r'|SEQUENCE_EDITOR)|SSH_ASKPASS|SUDO_ASKPASS|PAGER|EDITOR|VISUAL|MANPAGER|LESSOPEN|LESSCLOSE'
                                  r'|BROWSER|PYTHON\w*|NODE_OPTIONS|PERL5\w*|AWKPATH)$')


def vzor_mena(v):
    """Vzor mena (find -name: *, ?, [..]) → regulárny výraz ako pri zápise (zmena_vzor): [..] je ľubovoľný znak,
    výber je potom širší, nie užší."""
    return re.sub(r'\\\[(?:[^\\]|\\[^\]])*\\\]', '.', re.escape(v)).replace(r'\*', '.*').replace(r'\?', '.')


def skript_interpretra(pb, t):
    """Index súboru skriptu medzi argumentmi interpretra t, alebo None (kód v -c/-e/-m, zo vstupu, len kontrola).
    29. 9. (nález nezávislého overenia): skript sa bral ako prvé slovo bez „-“ — pri voľbe s hodnotou to bola hodnota:
    `bash -euo pipefail …/sync-zachrana.sh ptoth cp-ai-digi --naostro` (skript = „pipefail“) prešiel, rovnako
    `python3 -W ignore x.py --apply`. Voľby s hodnotou a voľby bez súboru podľa interpretra."""
    if pb in SHELLY:            # -n = len kontrola zápisu, nič nespustí
        s_arg, l_arg, bez, l_bez = 'oO', {'--rcfile', '--init-file'}, 'csn', set()
    elif re.match(r'^python[0-9.]*$', pb):
        s_arg, l_arg, bez, l_bez = 'WX', {'--check-hash-based-pycs'}, 'cm', set()
    elif pb == 'perl':
        s_arg, l_arg, bez, l_bez = '', set(), 'eEc', set()
    elif pb in ('node', 'nodejs'):
        s_arg, l_arg, bez, l_bez = 'r', {'--require', '--import', '--loader', '--experimental-loader', '--inspect-port',
                                         '--title', '--conditions', '--env-file', '--input-type'}, 'epc', \
            {'--eval', '--print', '--check', '--test'}
    else:
        return next((k for k, x in enumerate(t) if not x.startswith('-')), None)
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            return j + 1 if j + 1 < len(t) else None
        if x == '-':
            return None
        if x.startswith('--'):
            n = x.split('=', 1)[0]
            if n in l_bez:
                return None
            j += 2 if ('=' not in x and n in l_arg) else 1
            continue
        if len(x) > 1 and (x[0] == '-' or (x[0] == '+' and pb in SHELLY)):
            krok = 1
            for k, ch in enumerate(x[1:]):
                if ch in bez:
                    return None
                if ch in s_arg:
                    krok = 2 if k == len(x) - 2 else 1
                    break
            j += krok
            continue
        return j
    return None

# „send-email“ samo nie je značka (git send-email má vlastné pravidlo): v kóde kolegov je to typ udalosti v CRM
# (vpali 1. 9., 7 príkazov, ktoré odoslaná kotva púšťala — prehrávka 24. 9.)
ZNACKY_POSTY = re.compile(r'sendmail|send-mail|Send-MailMessage|exo-rest\.py|aig-crit-notify|webhook\.office|hooks\.office'
                          r'|teams-notify|mail-send|smtp', re.I)
# Kód interpretra (heredoc, -c, skript) posiela poštu, len keď značku aj POUŽIJE: meno poštového programu spolu so
# spúšťaním procesu (subprocess, os.system …), alebo poštové volanie spolu so sieťou (smtplib, requests, urllib …).
# Samotné meno v reťazci je úprava súboru, nie odoslanie (24. 9.: kolega-agent upravoval ng-skody-kolegov.py, text
# obsahoval „aig-crit-notify.sh“ a kotva ho zastavila ako mail); samotné „smtp“ je čítanie nastavení (KOTVA-9).
POSTA_POKYN = {'send', 'send-draft', 'senddraft', 'reply', 'reply-all', 'forward', 'odosli', 'odošli', 'odoslat', 'odoslať'}
# poštový nástroj podľa mena (graph_mail.py, send_mail.sh …) — s pokynom odoslať medzi argumentmi = odoslanie (29. 9.)
POSTA_NASTROJ = re.compile(r'mail', re.I)
# 29. 9. (nález nezávislého overenia): ten istý nástroj spustený z kódu interpretra —
# subprocess.run(['python3', '…/graph_mail.py', 'send', …]) alebo `import graph_mail; graph_mail.main()` — prešiel,
# lebo kód sa posudzoval len pri značkách ZNACKY_PROG. Súbor nástroja v kóde spustí rovnaký rozbor príkazu; načítanie
# poštového modulu (nie štandardného email/mailbox) spolu s pokynom odoslať alebo main() = odoslanie.
POSTA_NASTROJ_KOD = re.compile(r'[\w.~/$-]*mail[\w-]*\.(?:py|sh|bash|js|cjs|mjs|pl|rb|ps1)\b', re.I)
KOD_IMPORT_POSTA = re.compile(r'^[ \t]*(?:from[ \t]+([\w.]*mail[\w.]*)[ \t]+import\b|import[ \t]+([\w., \t]*mail[\w.]*))',
                              re.M | re.I)
KOD_POSTA_POKYN = re.compile(r'''['"](?:send|send-draft|senddraft|reply|reply-all|forward)['"]'''
                             r'''|\b(?:send|send_mail|send_message|sendmail|reply|forward)\w*\s*\(|\bmain\s*\(''', re.I)
# 2. 11. 2026 (sľub 3.10, jediné zastavenie, ktoré ho držalo): vzor vyššie berie za pokyn odoslať aj samotné
# `main(` — a to je v Pythone vlastný vstupný bod skriptu. Skript, ktorý poštu LEN ČÍTA a poštový nástroj si načíta
# kvôli tokenu (ADM/work/ulohy/adm-agenda/maily.py), tak vyzeral ako odosielanie. Samotné `main(` sa preto berie
# len vtedy, keď kód vlastný `main` NEDEFINUJE — vtedy musí prísť z načítaného nástroja. Volanie na module
# (`m.main(…)`) a všetky ostatné pokyny platia ako doteraz, takže skutočné odoslanie sa zastaví rovnako.
KOD_POSTA_POKYN_SILNY = re.compile(r'''['"](?:send|send-draft|senddraft|reply|reply-all|forward)['"]'''
                                   r'''|\b(?:send|send_mail|send_message|sendmail|reply|forward)\w*\s*\('''
                                   r'''|\.\s*main\s*\(''', re.I)
KOD_VLASTNY_MAIN = re.compile(r'^[ \t]*(?:async[ \t]+)?def[ \t]+main[ \t]*\(', re.M)


def posta_pokyn(kd):
    """Je v kóde pokyn odoslať? Samotné `main(` len vtedy, keď kód vlastný `main` nedefinuje (2. 11. 2026)."""
    if KOD_POSTA_POKYN_SILNY.search(kd):
        return True
    return bool(re.search(r'\bmain\s*\(', kd)) and not KOD_VLASTNY_MAIN.search(kd)
POSTA_STDLIB = {'email', 'mailbox', 'mailcap'}
# načítanie poštového modulu či skriptu za behu (29. 9. večer, druhé kolo overenia)
KOD_NACITA_POSTU_DYN = re.compile(r'''(?:import_module|__import__|run_module)\s*\(\s*['"](?!(?:email|mailbox|mailcap)\b)[\w.]*mail[\w.]*['"]'''
                                  r'''|run_path\s*\(\s*['"][^'"\n]*mail[^'"\n]*\.py['"]|spec_from_file_location\s*\([^)\n]*mail[^)\n]*\.py''',
                                  re.I)
# poštový nástroj podľa mena spustený s pokynom na čítanie (mail_draft_send.py list): o odoslaní rozhoduje pokyn, nie
# vetva „send“ v jeho kóde (prehrávka príkazov PS 29. 9. — inak by zastavil výpis konceptov)
POSTA_CITA = {'list', 'ls', 'draft', 'drafts', 'whoami', 'read', 'search', 'show', 'get', 'status', 'help', 'h'}
ZNACKY_PROG = re.compile(r'sendmail|send-mail|exo-rest\.py|aig-crit-notify|teams-notify|mail-send', re.I)
KOD_SIET = re.compile(r'smtplib|requests|urllib|http\.client|httpx|aiohttp|\bfetch\s*\(|axios|nodemailer|XMLHttpRequest'
                      r'|Net::SMTP|LWP::|Invoke-RestMethod|Invoke-WebRequest|pycurl|exchangelib|\bcurl\b')
# 10. 10. (nález sond stráže, otázka PS AIG-R261009-8727a8): pár „poštové volanie + sieť“ hľadal obe značky v celom
# texte kódu, takže ho dal aj KATALÓG SKÚŠOBNÝCH PRÍKAZOV uložený ako údaj — ng-kotva-sondy.py má v zozname riadok
# s poštovou cestou aj s menom klienta, a kotva spustenie katalógu zastavila ako odoslanie mailu, hoci katalóg nič
# neposiela. Skúšobný katalóg nie je zámena za skutočný príkaz: značku siete preto berie len ako KÓD (kod_posta_siet,
# nižšie pri kod_bez_retazcov) — poštová cesta ostáva aj z reťazca, lebo adresa reťazcom byť má. Príkaz, ktorý kód
# naozaj spustí, posúdi rozbor spúšťaných príkazov a zastaví ho ako doteraz.
# poštové volanie v kóde: metóda .sendmail / .send_message (aj bez zátvorky: f = s.send_message; f(m)), getattr(s,
# 'send_message') (24. 9.: prešlo), cmdlet Send-MailMessage, adresa webhooku Teams, Graph sendMail
POSTA_VOLANIE = re.compile(r'\.(sendmail|send_message)\b|getattr\s*\([^()]*[\'"](sendmail|send_message)[\'"]'
                           r'|Send-MailMessage\s|webhook\.office\.com|hooks\.office\.com|/sendMail\b', re.I)
# argumenty interpretra okrem samotného skriptu sú dáta (python3 uprav.py aig-crit-notify.sh); poštové volanie áno
ZNACKY_ARG = re.compile(r'sendmail|Send-MailMessage|webhook\.office|hooks\.office', re.I)
# 2. 10. (prechod account-cp na NG, suchá prehrávka 14 dní): exo-rest.py — nástroj platformy na správu Exchange Online
# (adminapi InvokeCommand: `python3 exo-rest.py <cmdlet> '<parametre JSON>'`, alebo ako knižnica `m.run('<cmdlet>', …)`)
# bol v zozname poštových značiek celý, takže kotva zastavovala aj ČÍTANIE Exchange: 7 z 10 falošných zastavení PS
# v account-cp (Get-Recipient, Get-ApplicationAccessPolicy, Get-DistributionGroupMember, Test-ApplicationAccessPolicy, --help;
# 1. 10.), v ostatných projektoch PS ďalších 37. Čítanie teraz prejde: cmdlet je doslovný a len číta — sloveso Get
# (v PowerShelli Get stav nemení) alebo vymenovaný Test-* (nie každý: Test-Message pošle správu s výsledkom). Každý iný
# cmdlet (New-Mailbox, Add-…, Set-…, Remove-…), cmdlet v premennej alebo zo vstupu (xargs) ostáva zastavený ako doteraz.
# Výnimka platí len pre nástroj platformy: súbor, ktorý kotva prečíta, volá adminapi InvokeCommand a sám poštu neposiela —
# iný skript s rovnakým menom, cesta v neznámej premennej alebo nečitateľný súbor (kolega k súboru PS) zastaví ako doteraz.
EXO_MENO = 'exo-rest.py'
EXO_NASTROJ = re.compile(r'exo-rest\.py', re.I)
EXO_CITA = re.compile(r'Get-[A-Za-z0-9]+|Test-ApplicationAccessPolicy')
EXO_POMOC = {'--help', '-h'}     # nástroj ich pošle ako meno cmdletu, Exchange ho odmietne — nič sa nezmení
# reťazec v tvare cmdletu (schválené slovesá PowerShellu, Sloveso-Podstatné; nie hlavička Content-Type, nie „test-run“)
EXO_TVAR = re.compile(r'(?<![\w-])(?:Add|Approve|Assert|Backup|Block|Clear|Close|Compare|Complete|Compress|Confirm|Connect'
                      r'|Convert|ConvertFrom|ConvertTo|Copy|Deny|Deploy|Disable|Disconnect|Dismount|Edit|Enable|Enter|Exit'
                      r'|Expand|Export|Find|Format|Get|Grant|Hide|Import|Initialize|Install|Invoke|Join|Limit|Lock|Merge'
                      r'|Mount|Move|New|Open|Optimize|Publish|Push|Receive|Redo|Register|Remove|Rename|Repair|Request'
                      r'|Reset|Resize|Resolve|Restart|Restore|Resume|Revoke|Save|Search|Select|Send|Set|Show|Skip|Split'
                      r'|Start|Stop|Submit|Suspend|Switch|Sync|Test|Undo|Uninstall|Unlock|Unprotect|Unpublish|Unregister'
                      r'|Update|Use|Wait|Write)-[A-Z][A-Za-z0-9]*')
# Kód, ktorý nástroj načíta ako knižnicu (python3 - <<EOF … spec_from_file_location('exo', 'exo-rest.py') …), s ním len číta,
# keď je celý z neškodných dielov (exo_kod_cita): importuje len moduly zo zoznamu, nástroj volá len cez jeho funkcie
# s doslovným čítacím cmdletom a nemá vlastnú požiadavku na Exchange (CmdletName, adresa, token nástroja — telo takej
# požiadavky sa nedá spojiť s cmdletom: urlopen(Request(…, data=open('telo.json').read())) môže byť New-Mailbox).
EXO_KOD_MODULY = {'importlib', 'importlib.util', 'importlib.machinery', 'json', 'sys', 'os', 'os.path', 'time', 're',
                  'collections', 'datetime', 'pprint', 'textwrap', 'itertools', 'functools', 'math', 'pathlib', 'csv',
                  'string', 'unicodedata', 'base64', 'hashlib', 'html'}
# mená (aj časť mena s bodkou), ktoré v takom kóde nesmú byť: spustenie procesu či kódu, prístup k vnútru modulov, sieť
# cez knižnice, ktoré nástroj sám načítal (m.urllib …, sys.modules), token nástroja
EXO_KOD_ZAKAZ = {'eval', 'getattr', 'setattr', 'delattr', 'vars', 'globals', 'locals', 'breakpoint', 'modules', 'system',
                 'popen', 'startfile', 'subprocess', 'urllib', 'urlopen', 'Request', 'tok', '_tok', 'attrgetter',
                 'methodcaller', 'run_path', 'run_module', 'import_module', 'reload',
                 # overenie 2. 10.: načítanie modulu mimo zoznamu importov (find_spec('http.client') + exec_module = sieť)
                 'find_spec', 'find_loader', 'load_module', 'spec_from_loader', 'meta_path', 'path_hooks',
                 'path_importer_cache', 'PathFinder', 'FileFinder', 'BuiltinImporter', 'FrozenImporter',
                 'SourcelessFileLoader', 'ExtensionFileLoader', 'LazyLoader',
                 # overenie 2. 10.: os.environ['PYTHONINSPECT'] = '1' v kóde zapne interaktívny režim na konci behu —
                 # zvyšok vstupu (here-string, rúra) sa potom vykoná, napr. m.run('New-Mailbox')
                 'environ', 'putenv', 'unsetenv',
                 # overenie 2. 10.: mazanie a prepis súborov popri čítaní (os.remove('/var/lib/…'), Path(…).write_text) —
                 # pred 2. 10. taký kód zastavila značka exo-rest.py; výnimka pre čítanie ho nesmie pustiť
                 'remove', 'unlink', 'rmdir', 'removedirs', 'rmtree', 'rename', 'renames', 'truncate', 'chmod', 'chown',
                 'lchown', 'write_text', 'write_bytes', 'symlink', 'symlink_to', 'hardlink_to'}
# overenie 2. 10.: kód, ktorý spúšťa nástroj a mení prostredie interpretra (PYTHONINSPECT, env=…, os.environ[…]) — spustený
# nástroj môže vykonať aj kód zo vstupu; taký príkaz sa posúdi ako pred 2. 10. (prikaz_posiela, po_starom)
KOD_PROSTREDIE = re.compile(r'PYTHON[A-Z_]+|\benv\s*=|environ\s*(?:\[|\.(?:update|setdefault))|putenv')
EXO_KOD_ZAKAZ_ZAC = ('exec', 'spawn', 'posix_spawn', 'fork', 'create_subprocess')
EXO_NACITA = {'spec_from_file_location', 'SourceFileLoader', 'load_source'}   # import modulu, nie spustenie ako skript
# overenie 2. 10.: nástroj platformy = súbor s obsahom zhodným s týmto (aj jeho doslovná kópia). Znaky obsahu (adminapi,
# InvokeCommand, bez pošty) pustili aj kópiu s rovnakým menom, ktorá po čítaní ešte zapisuje (run('Add-MailboxPermission',
# …) v __main__). Zmena nástroja = čítanie zastaví ako pred 2. 10. (bezpečný smer, červený test to ukáže).
EXO_PLATFORMA = '/home/pstrocka/projects/aigrip/.aigrip/scaffolds/email-setup/exo-rest.py'
_EXO_SUBOR = {}
_EXO_VZOR = []


def exo_vzor():
    """Obsah nástroja platformy (None, keď ho kotva neprečíta — kolega k súboru PS)."""
    if not _EXO_VZOR:
        try:
            with open(EXO_PLATFORMA, encoding='utf-8', errors='replace') as f:
                _EXO_VZOR.append(f.read())
        except OSError:
            _EXO_VZOR.append(None)
    return _EXO_VZOR[0]


def exo_nastroj(p):
    """(súbor je nástroj platformy na správu Exchange, mená jeho funkcií s parametrami) — 2. 10. Nástroj volá adminapi
    InvokeCommand a sám poštu neposiela; cez funkcie s parametrami (run(cmdlet, **params)) ide cmdlet."""
    if p in _EXO_SUBOR:
        return _EXO_SUBOR[p]
    v = (False, frozenset())
    try:
        if os.path.basename(p) == EXO_MENO and os.path.isfile(p) and os.path.getsize(p) <= 64 * 1024:
            with open(p, encoding='utf-8', errors='replace') as f:
                obs = f.read()
            if obs == exo_vzor() and '/adminapi/' in obs and 'InvokeCommand' in obs and not POSTA_VOLANIE.search(obs) \
                    and not re.search(r'smtplib|sendMail|Send-MailMessage|webhook', obs, re.I):
                v = (True, frozenset(m.group(1) for m in re.finditer(r'^[ \t]*def[ \t]+(\w+)[ \t]*\(([^)]*)\)', obs, re.M)
                                     if m.group(2).strip()))
    except OSError:
        pass
    _EXO_SUBOR[p] = v
    return v


def exo_kod_cita(kd, mena):
    """Python, ktorý exo-rest.py načíta ako knižnicu, s ním len číta (2. 10., EXO_KOD_* vyššie): každé meno funkcie
    nástroja (mena — run …) je volanie s prvým argumentom doslovný čítací cmdlet (nie f = m.run, map(m.run, …), m.run(c));
    každý reťazec v tvare cmdletu je čítací; reťazce nemajú adresu (://), CmdletName ani __main__; súbor .py v reťazci je
    len exo-rest.py (iný modul cez spec_from_file_location nie); importy len z EXO_KOD_MODULY; žiadne meno z EXO_KOD_ZAKAZ
    ani dunder (okrem __name__, __file__); a v kóde nie je iná poštová značka. Čo nesedí, zastaví ako pred 2. 10."""
    if ZNACKY_PROG.search(EXO_NASTROJ.sub('', kd)) or POSTA_NASTROJ_KOD.search(kd) or POSTA_VOLANIE.search(kd):
        return False
    t = []

    def tokeny(kod, hl):
        for tk in kod_tokeny(kod, 'py'):
            t.append(tk)
            if tk[0] == 's' and hl < 4:                 # výrazy vo f-reťazci f"{m.tok()}" sú tiež kód
                for d, x in tk[1]:
                    if d == 'e':
                        tokeny(x, hl + 1)
                        t.append(('nl', None))
    tokeny(kd, 0)
    # importy (aj za „;“): import a.b as c, d · from a.b import x — modul zo zoznamu; iné „from“ (relatívny import,
    # yield from, raise … from) = pochybnosť
    k = 0
    while k < len(t):
        if t[k] == ('n', 'from'):
            if k + 2 < len(t) and t[k + 1][0] == 'n' and t[k + 2] == ('n', 'import') and t[k + 1][1] in EXO_KOD_MODULY:
                k += 3                      # mená za import posúdi cyklus nižšie (EXO_KOD_ZAKAZ)
                continue
            return False
        if t[k] == ('n', 'import'):
            k += 1
            while k < len(t) and t[k][0] == 'n':
                if t[k][1] not in EXO_KOD_MODULY:
                    return False
                k += 3 if k + 2 < len(t) and t[k + 1] == ('n', 'as') and t[k + 2][0] == 'n' else 1
                if k < len(t) and t[k] == ('p', ','):
                    k += 1
                    continue
                break
            continue
        k += 1
    for k, (d, v) in enumerate(t):
        if d == 's':
            s = KodRozbor.retazec(v)
            if '://' in s or 'CmdletName' in s or '__main__' in s \
                    or any(not EXO_CITA.fullmatch(m.group()) for m in EXO_TVAR.finditer(s)) \
                    or any(os.path.basename(m.group()) != EXO_MENO for m in re.finditer(r'[^\s\'"]*\.(?:py[cwz]?|so)\b', s)):
                return False
        elif d == 'n':
            casti = v.split('.')
            for c in casti:
                if c in EXO_KOD_ZAKAZ or (c.startswith(EXO_KOD_ZAKAZ_ZAC) and c != 'exec_module') \
                        or (len(c) > 4 and c.startswith('__') and c.endswith('__') and c not in ('__name__', '__file__')):
                    return False
            if any(c in mena for c in casti):
                if casti[-1] not in mena or t[k + 1:k + 2] != [('p', '(')] or k + 3 >= len(t):
                    return False
                d2, v2 = t[k + 2]
                if d2 != 's' or any(x[0] != 'l' for x in v2) or not EXO_CITA.fullmatch(KodRozbor.retazec(v2)) \
                        or t[k + 3] not in (('p', ','), ('p', ')')):
                    return False
    return True


def exo_heredoc_odkaz(h, mena):
    """Telo heredocu bez úvodzoviek rozvinie premennú z mena — aj ${…} s operátorom, ktorý rozklad nepozná po mene (2. 10.)."""
    for m in re.finditer(r'\$(?:\{([^}]*)\}|([A-Za-z_]\w*))', h):
        meno = m.group(2) if m.group(2) is not None else m.group(1)
        if meno in mena or not re.fullmatch(r'[A-Za-z_]\w*', meno):
            return True
    return False


# 2. 10.: pomoc poštového nástroja (graph_mail.py send --help — account-cp 1. 10. 17:15) nič neodošle: za skriptom je len
# pokyn a --help/-h, nástroj je v Pythone a používa argparse (ten pri --help vypíše pomoc a skončí skôr, než nástroj
# čokoľvek urobí). Nástroj, ktorý kotva neprečíta, argparse nemá alebo má vypnutú pomoc či parse_known_args (neznámu voľbu
# by prešiel), ostáva zastavený; rovnako pokyn s ďalším argumentom (send --help --to …) a --help za „--“.
POSTA_POMOC = {'--help', '-h'}
POSTA_ARGPARSE = re.compile(r'^[ \t]*(?:import[ \t]+(?:[\w.]+[ \t]*,[ \t]*)*argparse\b|from[ \t]+argparse[ \t]+import\b)', re.M)
POSTA_ARGPARSE_ZLE = re.compile(r'add_help\s*=\s*False|parse_known_args|parse_known_intermixed_args')
CURL_CIEL = re.compile(r'graph\.microsoft|sharepoint|atlassian|outlook\.office|api\.github\.com|github\.com/repos', re.I)
# 29. 9. (nález nezávislého overenia, PS 4.40 a): pravidlo „pred zmazaním, odoslaním a zverejnením v externej službe sa
# partner opýta“ je všeobecné, no zápis stráži len CURL_CIEL (Graph, SharePoint, Atlassian, Outlook, GitHub) a katalóg
# marketingu — zmazanie príspevku na webe (curl -X DELETE https://aspecta.sk/wp-json/wp/v2/posts/123?force=true), jeho
# zverejnenie (status=publish) či zmazanie kontaktu v CRM (api.solvedio.com) prešlo. Doplnené: zmazanie (DELETE) a
# zverejnenie cez WordPress REST API na ľubovoľnom webe (podľa cesty /wp-json/, ?rest_route=) a zmazanie v CRM Solvedio.
# DELETE na hocijakú cudziu adresu to nie je: prehrávka kolegov (29. 9.) by zastavila Erikino zrušenie práve založenej
# prázdnej služby Google Analytics (ide do koša, obnoviteľné 30 dní) — ďalšia služba sa pridá sem, keď ju kolegovia
# naozaj používajú. Iný zápis do neznámej služby ostáva bez otázky (bežná práca kolegov s API).
CURL_LOKALNE = re.compile(r'^https?://(?:localhost|127\.\d+\.\d+\.\d+|0\.0\.0\.0|\[::1\]|[^/?#:@]*\.(?:local|localhost|internal))'
                          r'(?::\d+)?(?:[/?#]|$)', re.I)
EXT_ZMAZ_CIEL = re.compile(r'/wp-json/|[?&]rest_route=|^https?://(?:[^/?#]*\.)?api\.solvedio\.com(?::\d+)?(?:[/?#]|$)', re.I)
WP_ZVEREJNI = re.compile(r'''status\\?["']?\s*(?:[:=]|%3a|%3d)\s*\\?["']?(?:publish|future)\b''', re.I)
# az rest --method POST na akciu Azure Resource Manager „…/list“, „…/listKeys“ … je čítanie (List Auth Settings V2 vráti
# nastavenia a nič nemení; PS 15. 9. v bpw 3× — nová kotva ich zastavila). Posledný úsek cesty musí byť doslovný.
AZ_POST_CITA = re.compile(r'^(?:https://management\.azure\.com)?/(?:subscriptions|providers)/[^?#]*/list(?:[A-Z][A-Za-z0-9]*)?'
                          r'/?(?:[?#].*)?$')
# git push: dlhé voľby (git berie aj jednoznačnú skratku, --dry = --dry-run) a tie, ktoré berú hodnotu
GIT_PUSH_DLHE = ('all', 'branches', 'mirror', 'delete', 'tags', 'no-tags', 'follow-tags', 'no-follow-tags', 'atomic',
                 'no-atomic', 'dry-run', 'no-dry-run', 'porcelain', 'prune', 'no-prune', 'force', 'no-force',
                 'force-with-lease', 'no-force-with-lease', 'force-if-includes', 'no-force-if-includes', 'repo',
                 'set-upstream', 'thin', 'no-thin', 'quiet', 'verbose', 'progress', 'no-progress', 'recurse-submodules',
                 'no-recurse-submodules', 'verify', 'no-verify', 'push-option', 'receive-pack', 'exec', 'signed',
                 'no-signed', 'ipv4', 'ipv6', 'help')
GIT_PUSH_S_HODNOTOU = {'repo', 'push-option', 'receive-pack', 'exec'}
# gh: voľby bez hodnoty — --help hneď za nimi je pomoc; za voľbou s hodnotou (--body --help) je to hodnota (24. 9.)
GH_BEZ_HODNOTY = {'--web', '-w', '--fill', '--fill-first', '--fill-verbose', '--draft', '--dry-run', '--yes', '-y',
                  '--paginate', '--silent', '--verbose', '--include', '-i', '--public', '--private', '--internal',
                  '--push', '--clone', '--admin', '--auto', '--squash', '--merge', '--rebase', '--delete-branch',
                  '--no-maintainer-edit', '--confirm', '--all', '--exit-status'}
CURL_S_ARG = 'AbcCdDeEFHKmoPQrtTuUwxXyYz'
CURL_L_ARG = set('''--data --data-raw --data-binary --data-urlencode --data-ascii --json --form --form-string --header
--proxy-header --output --user --user-agent --referer --cookie --cookie-jar --upload-file --write-out --max-time
--connect-timeout --proxy --config --cacert --capath --cert --key --range --continue-at --retry --retry-delay
--retry-max-time --resolve --interface --dump-header --limit-rate --max-filesize --quote --oauth2-bearer --aws-sigv4
--variable --connect-to --output-dir --trace --trace-ascii --stderr --netrc-file --pass --cert-type --key-type
--ciphers --proto --proto-redir --max-redirs --speed-limit --speed-time --local-port --dns-servers --unix-socket
--abstract-unix-socket'''.split())
# K88 (18. 10.): curl cez miestny soket (--unix-socket, --abstract-unix-socket) nejde na sieť — host v adrese je len
# meno pre server za soketom (`http://x/api/…` na vlastný server cez /run/aig-workspace/<u>.sock). Prehrávka 14.–18. 10.:
# 2 z 5 zastavení pravidla o cudzej adrese boli takéto. Proxy, --next a -: (ďalší prenos bez soketu) a súbor
# s nastaveniami (-K) výnimku rušia.
CURL_SOKET = ('--unix-socket', '--abstract-unix-socket')
CURL_PROXY = ('--proxy', '--preproxy', '--socks4', '--socks4a', '--socks5', '--socks5-hostname')
STROJ = re.compile(r'/home/pstrocka/projects/aigrip/\.aigrip/scripts/ng-[a-z0-9-]+\.(py|sh)')

SHELLY = {'bash', 'sh', 'zsh', 'dash', 'ksh', 'mksh'}
INTERPRETY = re.compile(r'^(python[0-9.]*|node|nodejs|php[0-9.]*|perl|ruby|pwsh|powershell|deno|bun|osascript)$')
POSTA_PROG = {'sendmail', 'ssmtp', 'msmtp', 'swaks', 'mail', 'mailx', 'mutt', 's-nail'}
KLUCOVE = {'!', '{', '}', 'then', 'do', 'else', 'elif', 'if', 'while', 'until', 'fi', 'done'}
KLUCOVE_KONIEC = {'for', 'select', 'case', 'function', 'esac', 'in'}


def sh_bez_klucovych(sl):
    """Slová príkazu bez úvodných kľúčových slov shellu (do, then, {, !, time …).

    Sondy stráže 10. 10. (PS 4.86 a): v tele cyklu a funkcie je prvým slovom príkazu kľúčové slovo
    (`for i in 1; do printf %s '…' | sh; done` → ['do','printf',…]), takže článok rúry pred shellom či interpreterom
    sa čítal ako príkaz „do" a text sa stratil — `printf %s 'git push --force …' | sh` v cykle prešlo."""
    i = 0
    while i < len(sl) and not sl[i].kvot and sl[i].text() in KLUCOVE | {'time'}:
        i += 1
    return sl[i:]


# čítanie bez vedľajších účinkov — jeden zoznam pre sudo aj cudzí domov (KOTVA-7); nástroje, ktoré vedia zapisovať
# alebo spúšťať (sed, awk, sort, uniq, find, git, ip, journalctl, dmesg …), sa posudzujú aj podľa volieb (KOTVA-2)
CITAJ = set('''cat tac nl head tail less more zcat xzcat bzcat zless zmore zgrep zegrep zfgrep grep egrep fgrep rg wc file
readlink realpath md5sum sha1sum sha224sum sha256sum sha512sum b2sum cksum sum stat ls du df cut tr ss lsof ps pgrep free
uptime id getent who w whoami groups last lastlog lastb test [ [[ true false echo printf pwd uname which type whereis
basename dirname column strings od hexdump cmp comm paste fold fmt expand unexpand rev seq sleep base64 jq diff colordiff
tree lsblk findmnt blkid getfacl namei lsattr printenv netstat nproc lscpu vmstat iostat mpstat pstree locale tty cd
pushd popd dirs : shellcheck numfmt join look zipinfo hostname date env mount wait jobs'''.split())
# 2. 10. (prehrávka account-cp): riadenie shellu a jeho voľby — continue, break, return, exit, shift, set — nič mimo shell
# nemení; pod sudo zastavovali čítanie (`sudo -n sh -c 'for f in …; do [ -f "$f" ] || continue; …; done'`, 1. 10. 19:45)
CITAJ |= {'continue', 'break', 'return', 'exit', 'shift', 'set'}
# nič neposiela (poštový kanál): čítanie + práca so súbormi (KOTVA-9: git rm/mv, podman cp)
NEPOSIELA = CITAJ | set('''cp mv rm rmdir mkdir touch ln chmod chown chgrp install tar zip unzip gzip gunzip xz unxz bzip2
bunzip2 zstd unzstd 7z sed awk gawk mawk sort uniq find tee patch dos2unix iconv truncate rsync scp mktemp pandoc
soffice libreoffice convert magick pdftotext pdfinfo code vim vi nano export unset read mapfile readarray local
declare typeset readonly set shopt trap alias'''.split())
SIETOVE = {'curl', 'wget', 'http', 'https', 'nc', 'ncat', 'openssl', 'telnet'}
GIT_CITAJ = set('''log status show diff ls-files ls-tree ls-remote rev-parse rev-list blame annotate describe cat-file shortlog
for-each-ref grep check-ignore check-attr name-rev merge-base count-objects show-ref show-branch whatchanged diff-tree
diff-files diff-index cherry var help version verify-commit verify-tag range-diff'''.split())
SYSCTL_CITAJ = set('''status show cat list-timers list-units list-unit-files list-sockets list-dependencies list-jobs
list-machines is-active is-enabled is-failed is-system-running get-default show-environment help'''.split())
SYSCTL_ZAPIS = set('''start stop restart enable disable reload mask unmask kill daemon-reload try-restart reload-or-restart
restart-or-reload isolate reboot poweroff halt edit set-property'''.split())
SYSCTL_ARG = {'-t', '--type', '-p', '--property', '-H', '--host', '-M', '--machine', '-n', '--lines', '-o', '--output',
              '-s', '--signal', '--kill-whom', '--root', '--state', '--job-mode', '-P'}
SUDO_L_ARG = {'--user', '--group', '--chdir', '--host', '--prompt', '--role', '--type', '--other-user', '--close-from',
              '--command-timeout', '--chroot'}
FIND_ZAPIS = {'-delete', '-exec', '-execdir', '-ok', '-okdir', '-fprint', '-fprint0', '-fprintf', '-fls'}
JOURNAL_ZAPIS = ('--vacuum', '--rotate', '--flush', '--relinquish-var', '--smart-relinquish-var', '--sync', '--setup-keys',
                 '--update-catalog')
DMESG_ZAPIS = {'--clear', '--read-clear', '--console-off', '--console-on', '--console-level'}
VYSTUP = {'>', '>>', '>|', '&>', '&>>', '<>', '>&'}
AZ_SLOVESA = ('create', 'delete', 'update', 'set', 'add', 'remove', 'reset')
# obaly, ktoré spúšťajú príkaz za voľbami — inak sa cezeň prepašuje neobnoviteľný vnútorný príkaz. Voľby s hodnotou
# tu sú preto, aby sa hranica vnútorného príkazu našla aj bez „--“ (systemd-run u PS beží s MemoryMax capom „-- <cmd>“;
# unshare v skúškach sandboxu „-Ur true“; busybox rm … = rm …).
SYSTEMD_RUN_S = 'upEMHG'
SYSTEMD_RUN_L = ('--unit', '--property', '--description', '--slice', '--setenv', '--machine', '--host', '--uid', '--gid',
                 '--nice', '--working-directory', '--service-type', '--expand-environment', '--on-active', '--on-boot',
                 '--on-startup', '--on-unit-active', '--on-unit-inactive', '--on-calendar', '--on-timezone-change',
                 '--on-clock-change', '--timer-property', '--path-property', '--socket-property')
UNSHARE_S = 'SGR'
UNSHARE_L = ('--setuid', '--setgid', '--map-user', '--map-group', '--map-users', '--map-groups', '--propagation',
             '--setgroups', '--root', '--wd')
# 14. 10. (K88T, D3): obal pieskoviska bwrap, firejail a nsjail spúšťa príkaz za voľbami — rozbor ho bral za neznámy program
# a príkaz v ňom nečítal (bwrap --ro-bind / / python3 posli.py prešlo, hoci bez obalu zastaví). Vnútorný príkaz sa posúdi;
# keď obal mení, čo príkaz vidí (súbor pripojený na inú cestu, prostredie, pracovný priečinok, príkaz z deskriptora,
# prekrytie, chroot, profil) alebo voľba nie je v zozname, stráž nevie, čo pobeží — nedočítaný skript (zastaví).
# Voľby bez vplyvu na súbory a prostredie: počet argumentov voľby.
BWRAP_CISTE = {'--unshare-all': 0, '--share-net': 0, '--unshare-user': 0, '--unshare-user-try': 0, '--unshare-ipc': 0,
               '--unshare-pid': 0, '--unshare-net': 0, '--unshare-uts': 0, '--unshare-cgroup': 0,
               '--unshare-cgroup-try': 0, '--new-session': 0, '--die-with-parent': 0, '--as-pid-1': 0,
               '--disable-userns': 0, '--assert-userns-disabled': 0, '--level-prefix': 0, '--uid': 1, '--gid': 1,
               '--hostname': 1, '--dev': 1, '--proc': 1, '--mqueue': 1, '--tmpfs': 1, '--dir': 1, '--remount-ro': 1,
               '--cap-add': 1, '--cap-drop': 1, '--perms': 1, '--size': 1, '--lock-file': 1, '--sync-fd': 1,
               '--info-fd': 1, '--json-status-fd': 1, '--block-fd': 1, '--userns-block-fd': 1, '--userns': 1,
               '--userns2': 1, '--pidns': 1, '--seccomp': 1, '--add-seccomp-fd': 1, '--exec-label': 1,
               '--file-label': 1}
BWRAP_PRIPOJ = {'--bind', '--bind-try', '--dev-bind', '--dev-bind-try', '--ro-bind', '--ro-bind-try'}   # čisté len src = dest
BWRAP_INE = {'--clearenv': 0, '--args': 1, '--argv0': 1, '--chdir': 1, '--unsetenv': 1, '--overlay-src': 1,
             '--tmp-overlay': 1, '--ro-overlay': 1, '--setenv': 2, '--symlink': 2, '--chmod': 2, '--bind-fd': 2,
             '--ro-bind-fd': 2, '--file': 2, '--bind-data': 2, '--ro-bind-data': 2, '--overlay': 3}
FIREJAIL_CISTE = re.compile(r'--(?:quiet|noprofile|nonewprivs|seccomp|noroot|nosound|novideo|no3d|nodvd|notv|nou2f|'
                            r'private-dev|nogroups|ipc-namespace|memory-deny-write-execute|machine-id|read-only=/|'
                            r'net=none|caps\.drop=all|rlimit-\w+=\d+|timeout=[\d:]+|name=[\w.-]+|cpu=[\d,]+|nice=-?\d+)$')
NSJAIL_CISTE = {'-q': 0, '--quiet': 0, '-Q': 0, '--really_quiet': 0, '-v': 0, '--verbose': 0, '-e': 0, '--keep_env': 0,
                '-N': 0, '--disable_clone_newnet': 0, '--disable_proc': 0, '-Mo': 0, '-t': 1, '--time_limit': 1,
                '--rlimit_as': 1, '--rlimit_cpu': 1, '--rlimit_fsize': 1, '--rlimit_nofile': 1, '--rlimit_nproc': 1,
                '--rlimit_stack': 1, '-l': 1, '--log': 1, '-T': 1, '--tmpfsmount': 1, '--user': 1, '-u': 1,
                '--group': 1, '-g': 1, '--hostname': 1, '-H': 1}


def obal_sandbox(p, t):
    """K88T (D3): bwrap, firejail, nsjail → (index vnútorného príkazu v t, či obal nemení súbory ani prostredie)."""
    ciste = True
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            return j + 1, ciste
        if not x.startswith('-') or x == '-':
            return j, ciste
        if re.search(r'[$`]', x):
            return len(t), False
        if p == 'bwrap':
            if x in ('--help', '--version'):
                return len(t), ciste
            if x in BWRAP_CISTE:
                j += 1 + BWRAP_CISTE[x]
            elif x in BWRAP_PRIPOJ:
                a = t[j + 1:j + 3]
                if len(a) < 2 or re.search(r'[$`]', ' '.join(a)) or os.path.normpath(a[0]) != os.path.normpath(a[1]):
                    ciste = False
                j += 3
            elif x in BWRAP_INE:
                ciste = False
                j += 1 + BWRAP_INE[x]
            else:
                return len(t), False             # voľba, ktorej počet argumentov stráž nepozná — hranica príkazu neistá
        elif p == 'firejail':
            if not FIREJAIL_CISTE.match(x):
                ciste = False
            j += 1
        else:                                    # nsjail
            meno, _, hod = x.partition('=')
            if meno in ('-R', '--bindmount_ro', '-B', '--bindmount') or (meno in ('-c', '--chroot')):
                a = hod if hod else (t[j + 1] if j + 1 < len(t) else '')
                if meno in ('-c', '--chroot'):
                    ciste = ciste and a == '/'
                else:
                    src, _, dst = a.partition(':')
                    ciste = ciste and not re.search(r'[$`]', a) and (not dst or os.path.normpath(src) == os.path.normpath(dst))
                j += 1 if hod else 2
            elif meno in NSJAIL_CISTE:
                j += 1 if hod else 1 + NSJAIL_CISTE[meno]
            else:
                return len(t), False
    return j, ciste

BEZNE = re.compile(r'[^\s;&|<>()\\\'"$`]+')
DVOJ_BEZNE = re.compile(r'[^"\\$`]+')
MENO = re.compile(r'[A-Za-z_][A-Za-z0-9_]*|[0-9@*#?$!-]')
PRIRADENIE = re.compile(r'([A-Za-z_][A-Za-z0-9_]*)(\[[^\]]*\])?\+?=')
FD_DUP = re.compile(r'\d+-?|-')
NEZNAME = '\x00'
# 3. 10. (kolo 3): posledný diel cesty, ktorú vytvoril mktemp (nie -u) v tom istom príkaze či skripte — namiesto
# náhodného mena; v obsluhe trap prejde mazanie presne tejto cesty (Rozbor.mktemp_cerstva). Znak \x01 sa z textu
# príkazu aj skriptu vopred nahradí (Lex), napísať sa nedá.
MKTEMP_LIST = '\x01mktemp\x01'

# ---------- skript, ktorý príkaz spúšťa (úloha K, 2. 10. 2026) ----------
# Incident 1. 10. 19:34 (account-cp, sedenie bec5ccf3): `for p in $(cat $S/apponly.txt); do r=$(cd $p && bash
# .aigrip/scripts/email-fetch-raw.sh $S/smoke-$p.json 2>&1 | tail -1); …; done` v 23 projektoch ako „skúška“. Kotva čítala
# len skript z /tmp a skript zapísaný v tom istom príkaze; skript projektu posúdila podľa mena — a meno nič nehovorilo.
# email-fetch-raw.sh spustil email-preflight.sh a ten pri vypršanom kľúči urobil `az ad app credential reset --append`
# (nový secret aplikácie v Entra ID, 7×) a `printf … > "$ENV"` (celý .env prepísaný štyrmi premennými) — bez zastavenia.
# Všeobecne: keď príkaz spúšťa súbor skriptu (bash/sh x.sh, source, ./x, cesta/x, python3/node x, zo vstupu, aj v cykle cez
# projekty — cd $p, "$p/…/x.sh"), rozbor ho prečíta (a skripty, ktoré spúšťa ten, do SKRIPT_HLBKA úrovní) a posúdi tým
# istým rozborom ako príkaz partnera: shell celý, python a node cez volania, ktoré spúšťajú proces (subprocess, execSync …),
# obal pomenovaný podľa nástroja (az(…), gh(…)) a zoznam argumentov nástroja (['az', 'ad', …]), poštu a marketing.
# Kanál do cudzieho systému (SKRIPT_KANALY) zastaví ako priamy príkaz; text kanála menuje skript. Miestne kanály (mazanie,
# sudo, služby, cudzí domov, push z cudzieho priečinka) sa zo skriptu neberú: skripty majú podmienené vetvy, ktoré rozbor
# nevyhodnotí (HTML_ONLY=1 … systemctl restart — PS 22. 9.), a sudo nástroje stráži nastroj_ostro.
# Vetvy, ktoré vyvolanie nespustí (oprava po nezávislom overení 2. 10. večer): výnimka „cesta bez zápisu“ vynechávala celý
# skript, keď vyvolanie obsahovalo slovo, ktoré skript niekde „poznal“ (read z `IFS= read`, h z „~1 h)“ v echo, status ako
# kľúč v JSON, --report, ktorý mail naopak pošle, -h = host) — jedno slovo navyše vyplo zastavenie a hláška k tomu radila.
# Teraz žiadne slovo samo nič nevypína: kotva skript prejde s argumentmi vyvolania (sh_volby, py_volby nižšie) a vynechá len
# to, čo sa pri nich určite nespustí — vetvu, ktorej podmienka je určite nepravdivá, a zvyšok po určitom exit/return.
# Nanečisto, --help, ostrá voľba či DRY_RUN=1 zmenia výsledok, len keď skript podľa nich naozaj vetví a krok s kanálom je
# v tej vetve. Skript z /tmp a skript zapísaný v tom istom príkaze sa posudzuje celý (napísal ho partner sám). Nečíta sa:
# cudzí domov (ani pokus), súbor nad 1 MB, binárka, skript na inom stroji (ssh) či v kontajneri, systémové programy
# (/usr/bin …) a cudzí kód (node_modules, site-packages, .venv). Čo kotva pre strop (súbory, bajty, vnorenie, čas) nedočíta,
# nepúšťa: skript, ktorý mal byť prečítaný a nebol, zastaví (P_SKRIPT) — predtým strop ticho vrátil „nič“.
SKRIPT_HLBKA = 8                 # príkaz → skript → skript, ktorý spúšťa → … (úrovne pod príkazom)
# 10. 10.: hodinový červený test padal na `python3 .aigrip/scripts/ng-stav.py` — vlastný skript stroja má šesť
# úrovní, strop 5 ho vyhlásil za nedočítaný a kotva ho zastavila (falošné zastavenie, nález AIG-P261010-ff8799).
# Pri 8 (rovnako ako MAX_HLBKA) ten istý rozbor trvá rovnako — 1,06 s; čas aj tak chráni ROZBOR_CAS.
# 3. 10. (kolo 2): čas chráni časovač rozboru (ROZBOR_CAS), stropy súborov a bajtov sú len poistka — 160 súborov
# zastavilo 7 skúšok podagentov v pieskoviskách (167–172 rozborov, ~1 s, bez kanála) ako nedočítané
SKRIPT_MAX_SUBOROV = 400         # rôznych rozborov skriptov na jeden príkaz (rovnaký obsah v 60 projektoch sa ráta raz)
SKRIPT_MAX_BAJTOV = 16 * 1024 * 1024
# 1. 11. 2026: strop 1 MB prekročil `ng-cervene-testy.py` (1 047 328 B, 1 248 B pod stropom — ďalšia červená sada
# ho prehodila) a kotva začala zastavovať `python3 .aigrip/scripts/ng-stav.py` ako NEDOČÍTANÝ skript, teda
# vykreslenie stavu na začiatku každého sedenia (ten istý druh falošného zastavenia ako 10. 10. pri SKRIPT_HLBKA
# 5 → 8). Strop je ochrana času, nie bezpečnosti; pri 2 MB je ten istý rozbor rovnako dlhý — merané 1. 11.:
# ng-stav.py 2,83 s → 2,95 s na hodinách (2,70 → 2,79 s procesora), ďaleko pod ROZBOR_CAS 6,0 s.
SKRIPT_MAX_SUBOR = 2 * 1024 * 1024
SKRIPT_MAX_KANDIDATOV = 200      # súborov podľa vzoru (cd $p v cykle cez projekty, PS má 62); viac = nedočítané
SKRIPT_MAX_VOLANI = 200          # volaní nástroja v jednom súbore kódu (python, node)
# 16. 10. (KXA): meno skriptu, ktoré interpreter dostane zo vstupu xargs alebo z {} vo find -exec. Príkaz sa najprv posúdi
# so značkou XARGS_VSTUP na mieste vstupu (sonda); keď značka dôjde na miesto skriptu (skript_cesty), posúdi sa s každým
# menom, ktoré zdroj mien naozaj privedie (ls, find, echo/printf, aj cez sort/head …), a keď zdroj nevidno, je nedočítaný
XARGS_VSTUP = '__KOTVA_VSTUP_XARGS__'
XARGS_VSTUP_SLOVO = './' + XARGS_VSTUP
XARGS_INTERPRET = re.compile(r'^(?:bash|sh|zsh|dash|ksh|mksh|python[0-9.]*|node|nodejs)$')
XARGS_FILTRE = {'sort': ('-k', '-t', '-S', '-T', '--key', '--field-separator', '--buffer-size', '--temporary-directory'),
                'uniq': ('-f', '-s', '-w'), 'head': ('-n', '-c', '--lines', '--bytes'),
                'tail': ('-n', '-c', '--lines', '--bytes'), 'tac': ('-s', '--separator'), 'shuf': ('-n', '--head-count')}
XARGS_LS_VOLBY = set('1aAdrtSUXvcuhfGLpkHxC')     # ls s týmito voľbami vypíše len mená (bez -l, -F, -R, -Q …)
XARGS_LS_DLHE = ('--color', '--sort', '--time', '--group-directories-first', '--all', '--almost-all', '--directory',
                 '--reverse', '--literal')
XARGS_FIND_VYSTUP = ('-printf', '-fprintf', '-fprint', '-fprint0', '-fls', '-ls')   # iný výstup než cesty
XARGS_FIND_ARG = ('-path', '-wholename', '-ipath', '-iwholename', '-regex', '-iregex', '-newer', '-size', '-mtime',
                  '-mmin', '-atime', '-amin', '-ctime', '-cmin', '-type', '-xtype', '-perm', '-user', '-group', '-uid',
                  '-gid', '-links', '-inum', '-samefile', '-lname', '-ilname', '-fstype', '-regextype', '-anewer',
                  '-cnewer', '-used', '-context', '-printf', '-fprintf', '-fprint', '-fprint0', '-fls')
XARGS_MAX_PRECHOD = 20000        # položiek, ktoré kotva prejde pri hľadaní súborov pre find; viac = zdroj mien nevidno
# 16. 10. (KXP): GNU parallel — vstupy za ::: (::::  = zo súboru), náhradné reťazce {} {1} {.} {/} {//} {/.} {#} {%} {=…=};
# príkaz z viacerých slov parallel spojí medzerou a spustí shellom (bez -q), bez príkazu spustí vstup sám ako príkaz
PAR_S_ARG = 'jSaIdEnNPLsCU'
PAR_L_ARG = ('--jobs', '--sshlogin', '--arg-file', '--delimiter', '--colsep', '--joblog', '--results', '--tmpdir',
             '--timeout', '--workdir', '--wd', '--env', '--halt', '--delay', '--retries', '--load', '--memfree',
             '--tagstring', '--max-args', '--max-replace-args', '--max-lines', '--max-chars', '--max-procs', '--block',
             '--block-size', '--header', '--eof', '--termseq', '--nice', '--sshloginfile', '--slf', '--return',
             '--transferfile', '--tf', '--basefile', '--bf', '--arg-sep', '--arg-file-sep', '--extensionreplace',
             '--er', '--basenamereplace', '--bnr', '--dirnamereplace', '--dnr', '--basenameextensionreplace', '--bner',
             '--seqreplace', '--slotreplace', '--rpl', '--limit', '--memsuspend', '--profile', '--ssh', '--sshdelay')
PAR_NAHRADA = re.compile(r'\{(\d*)(\.|//|/\.|/)?\}|\{[#%]\}|\{=.*?=\}')
PAR_SHELL = re.compile(r'[\s;&|<>()`]')   # slovo príkazu, ktoré shell po spojení rozdelí alebo vyloží inak
# kód interpretra v príkaze, ktorý spustí súbor z argumentu: python3 -c 'exec(open(sys.argv[1]).read())' f,
# runpy.run_path(sys.argv[1]); node -e 'require(process.argv[1])' f
KOD_ARGV_SPUSTI = {'py': re.compile(r'(?<![\w.])(?:exec|execfile|compile)\s*\(|\b(?:run_path|exec_module|'
                                    r'spec_from_file_location|SourceFileLoader|load_source)\s*\('),
                   'js': re.compile(r'\b(?:require|import|runInThisContext|runInNewContext|runInContext)\s*\(')}
KOD_ARGV = {'py': re.compile(r'\bargv\s*\[\s*(-?\d+)\s*(:?)'),
            'js': re.compile(r'\bprocess\.argv\s*\[\s*(-?\d+)\s*\]()|\bprocess\.argv\.slice\(\s*(\d+)\s*\)')}
# 16. 10. (KXK): kód v príkaze, ktorý spustí ako kód samotný argument — `python3 -c 'exec(sys.argv[1])' '<kód>'`, eval,
# compile; `node -e 'eval(process.argv[1])' '<kód>'`, new Function, vm.runIn…, new vm.Script (kod_z_argumentu)
KOD_ARGV_KOD = {'py': re.compile(r'(?<![\w.])(?:exec|eval|compile)\s*\('),
                'js': re.compile(r'(?<![\w$.])(?:eval|Function)\s*\(|\b(?:runInThisContext|runInNewContext|runInContext|'
                                 r'compileFunction)\s*\(')}
SKRIPT_SPUSTACE = {'uv', 'poetry', 'pipenv', 'pdm', 'npx', 'bunx', 'pnpx', 'tsx', 'ts-node', 'ts-node-esm', 'bun', 'deno'}
SKRIPT_CAS = 5.5                 # s času procesora na rozbor skriptov jedného príkazu; potom zastaví
# 3. 10. (overenie kola 1: SKRIPT_CAS sa kontroloval len pred ďalším skriptom — jeden skript, ktorého rozbor trval nad 5 s,
# háčik zabil a núdzové pravidlá ho nečítali): časovač celého rozboru preruší rozbor kdekoľvek; skript, ktorý sa
# vtedy číta, je nedočítaný (zastaví). Príkaz bez skriptu ide ako pri páde na núdzové pravidlá háčika.
ROZBOR_CAS = 6.0
# 17. 10. (KTZ): stropy SKRIPT_CAS a ROZBOR_CAS rátajú čas procesora tohto rozboru (time.process_time, ITIMER_PROF), nie čas
# na hodinách. Pri záťaži servera (15.–17. 10. fronta 45–70 procesov na 32 vláknach) dostane rozbor polovicu procesora:
# `python3 …/ng-stav.py` (2,8 s procesora) trval na hodinách nad 5 s a kotva ho zastavila ako nedočítaný — 3 skutočné
# príkazy PS za 14 dní a 6 červených epizód hodinového testu; ten istý rozbor bez záťaže pustí. Výsledok tak nezávisí od
# toho, koľko iných procesov práve beží. Čas procesora rastie aj sám, keď druhé vlákno toho istého jadra pracuje
# (ng-stav.py 2,9 → 3,9 s, ×1,35) — preto 5,5 / 6,0 s namiesto 3,5 / 3,8. Poistka proti zaveseniu (čakanie na súbor,
# rúru, disk — čas procesora vtedy nebeží) je čas na hodinách: SKRIPT_STENA pred ďalším skriptom, ROZBOR_STENA časovačom
# (SIGALRM); háčik dáva rozboru 15 s, aby rozbor stihol povedať „nedočítaný“ sám skôr, než ho zabije.
SKRIPT_STENA = 12.0
ROZBOR_STENA = 13.0
P_SKRIPT = -0.5                  # nedočítaný skript — najnižšia priorita, skutočný kanál z iného miesta sa menuje prvý
K_SKRIPT = ('skript, ktorý príkaz spúšťa, kotva nedočítala (veľký súbor, priveľa súborov, vnorení alebo času, chyba '
            'rozboru) — nevie, či v ňom nie je neobnoviteľný krok')
# 1. 11. 2026 (diera sľubu 3.08): push do CUDZIEHO repozitára a push MIMO klonov človeka sú odchádzajúce kanály
# ako pošta či zápis cez API — skript, ktorý ich robí, musí zastaviť rovnako ako holý príkaz. Do 1. 11. v zozname
# neboli, takže `bash x.sh` s `git -C /home/<iný>/… push` prešiel, kým ten istý príkaz holý zastavil (overené
# dvakrát v pieskovisku). Cena zmeny je zmeraná na 205 skriptoch stroja: pribudlo JEDNO zastavenie —
# batch-rollout.sh, ktorý naozaj pushuje mimo klonov. Miestne kanály (mazanie, sudo, služby, cudzí domov) v
# zozname ďalej NIE SÚ: s nimi by sa zastavil `ng-stav.py`, `ng-zapisnik.py` aj `ng-straz-beh.sh` (merané 1. 11.)
# — skripty majú podmienené vetvy, ktoré rozbor nevyhodnotí, a ostré nástroje pod cudzím menom stráži nastroj_ostro.
SKRIPT_KANALY = (P_POSTA, P_GH, P_AZ, P_CURL, P_MARKETING, P_VON, P_FORCE, P_PUSH_ZMAZ, P_NASADENIE, P_SKRIPT, P_PUSH, P_PUSH_CUDZI)
# obal pomenovaný podľa nástroja (def az(*a): subprocess.run(['az', *a])) — volanie az('ad', 'app', 'credential', 'reset', …)
SKRIPT_CLI_VOLANIE = {'az', 'gh', 'git', 'curl', 'wget', 'scp', 'rsync'}
# zoznam argumentov, ktorý sa začína menom nástroja (['az', 'ad', …], ['git', 'push', '--force'])
SKRIPT_CLI_ZOZNAM = SKRIPT_CLI_VOLANIE | {'http', 'https', 'sendmail', 'msmtp', 'swaks', 'mail', 'mailx', 'vercel', 'netlify',
                                          'firebase', 'bash', 'sh', 'python3', 'python', 'node'}
SKRIPT_NECITAJ = re.compile(r'^/(?:usr/(?:s?bin|lib\w*|share|libexec)|s?bin|lib\w*|proc|sys|dev|etc|boot|snap)/'
                            r'|/(?:node_modules|site-packages|dist-packages|\.venv|venv|\.git)/')
# 3. 10. (kolo 4): programy, ktoré zapisujú do súborov (Rozbor.zmena_programu) a podpríkazy gitu, ktoré menia pracovný strom
ZMENA_PROGRAMY = frozenset({'sed', 'gsed', 'perl', 'ruby', 'tee', 'dd', 'curl', 'wget', 'patch', 'git', 'cp', 'mv', 'install',
                            'ln', 'rsync'})
ZMENA_CHYBY = (ValueError, IndexError, KeyError, TypeError, AttributeError, OSError, re.error)
GIT_MENI_STROM = frozenset({'switch', 'pull', 'merge', 'rebase', 'reset', 'stash', 'cherry-pick', 'revert', 'am'})
SKRIPT_NEZNAME = '/nezname/hodnota'   # neznáma hodnota v kóde — pri tomto pravidle nič nezastaví (nie poštový program)
# vlastné súbory kotvy (rozbor, háčik, overenie, červený test, prehrávky): ich text sú vzory a ukážky kanálov, nie akcie.
# Overenie 2. 10.: vzor mena (/ng-kotva-*.sh) vynechal aj dočasný skript /tmp/…/ng-kotva-oprava.sh s gh api -X POST.
# Vynechá sa len súbor s presným menom kotvy A obsahom zhodným bajt po bajte s kotvou, ktorá práve beží (súbor vedľa
# tohto rozboru, v ../hooks a ../scripts, /opt/aigrip-ng/scripts, aigrip PS) — upravená kópia sa číta ako každý iný skript.
KOTVA_MENA = ('ng-kotva-analyza.py', 'ng-kotva.sh', 'ng-kotva-test.sh', 'ng-kotva-over.py', 'ng-kotva-overenie.py',
              'ng-kotva-prehravka.py', 'ng-prehravka-kolegov.py')
_KOTVA_OBSAH = {}


def kotva_priecinky():
    d = os.path.dirname(os.path.abspath(__file__)) if '__file__' in globals() else ''
    return [x for x in dict.fromkeys((d, os.path.join(d, '..', 'hooks'), os.path.join(d, '..', 'scripts'),
                                      '/opt/aigrip-ng/scripts', '/home/pstrocka/projects/aigrip/.aigrip/scripts',
                                      '/home/pstrocka/projects/aigrip/.aigrip/hooks')) if x]


def kotva_ziva(meno, obsah):
    """Obsah (bajty) je zhodný bajt po bajte so živým súborom stráže toho mena (kotva_priecinky)."""
    for d in kotva_priecinky():
        q = os.path.normpath(os.path.join(d, meno))
        if q not in _KOTVA_OBSAH:
            try:
                with open(q, 'rb') as f:
                    _KOTVA_OBSAH[q] = f.read(SKRIPT_MAX_SUBOR + 1)
            except (OSError, ValueError):
                _KOTVA_OBSAH[q] = None
        if _KOTVA_OBSAH[q] is not None and _KOTVA_OBSAH[q] == obsah:
            return True
    return False


def kotva_vlastny_stav(p, obsah, kde=None):
    """Súbor p s obsahom obsah (bajty): 'vlastny' — súčasť kotvy, ktorá práve beží, aj s tým, čo si naloží z miesta kde
    (cesta, ktorou sa spúšťa; predvolene p); 'okolie' — súbor stráže, ale to, čo si naloží, nie je živé ani neprejde
    posudkom (K88S, kotva_naklad_ok; číta sa ako nedočítaný skript); None — nie je súbor stráže."""
    meno = os.path.basename(p)
    if meno not in KOTVA_MENA:
        return None
    if not kotva_ziva(meno, obsah) and not kotva_kopia_bezpecna(p, obsah):
        return None
    return 'vlastny' if kotva_naklad_ok(meno, os.path.dirname(os.path.normpath(kde or p))) else 'okolie'


def kotva_vlastny(p, obsah, kde=None):
    return kotva_vlastny_stav(p, obsah, kde) == 'vlastny'


# K88R (14. 10.): stráž posúdi upravenú kópiu vlastného súboru porovnaním so živou (vkladá sa do ng-kotva-analyza.py za
# kotva_vlastny). Doteraz sa vynechal len súbor s menom kotvy a obsahom zhodným bajt po bajte so živou kotvou; upravená
# kópia rozboru alebo červeného testu sa čítala ako každý skript — jej vzory a ukážky kanálov (adresy pošty, sendmail) ju
# zastavili ako odoslanie mailu, takže skúšať zmenu stráže v dielni sa dalo len cez diery v čítaní (K88Q: D1 kópia háčika,
# D2 modul načítaný skriptom). Python (rozbor, overenie, prehrávky) podľa stromu kódu: kópia prejde, keď nemá import, ktorý
# živá nemá, a každé miesto, ktoré vie konať (proces, sieť, zápis súboru, mazanie, exec/eval, načítanie modulu, dynamický
# prístup k menám), je vo funkcii zhodnej so živou — zmena teda nepridala ani nezmenila nič, čo koná. Shell podľa riadkov:
# háčik smie mať zmenené len komentáre a prázdne riadky; červený test navyše dáta prípadov (telo heredocu s úvodzovkami
# pri volaní funkcie prípadu, volanie funkcie prípadu s doslovnými argumentmi, nová funkcia prípadu ako k88m) a riadok H=
# (háčik, ktorý test volá — ten musí byť sám kotvou: živý alebo kópia, ktorá týmto posudkom prejde), SIG= a LOG= do dielne.
# Pravidlo pre ľudí sa nemení: všetko, čo vie konať, stráž zastaví ako doteraz; mení sa len to, ako číta vlastné súbory.
KOTVA_KOPIA_CISTE = frozenset({
    'ast', 'collections', 'glob', 'hashlib', 'json', 'pwd', 're', 'time', 'string', 'unicodedata', 'datetime', 'itertools',
    'functools', 'math', 'difflib', 'textwrap', 'shlex', 'base64', 'html', 'fnmatch', 'bisect', 'heapq', 'statistics',
    'copy', 'dataclasses', 'enum', 'typing', 'random', 'uuid', 'zlib', 'binascii', 'struct', 'calendar', 'decimal',
    'fractions', 'numbers', 'abc', 'keyword', 'token', 'traceback', 'errno', 'stat', 'pprint', 'operator', 'grp'})
# moduly, z ktorých sa smie len to, čo nekoná (čítanie súborov a prostredia, časovač rozboru, štandardný výstup)
KOTVA_KOPIA_SMIE = {
    'os': frozenset({'path', 'environ', 'getenv', 'getcwd', 'listdir', 'scandir', 'stat', 'lstat', 'walk', 'access',
                     'readlink', 'sep', 'pathsep', 'linesep', 'devnull', 'curdir', 'pardir', 'extsep', 'altsep', 'name',
                     'getpid', 'getppid', 'getuid', 'geteuid', 'getgid', 'getegid', 'getgroups', 'getlogin', 'uname',
                     'cpu_count', 'fspath', 'fsencode', 'fsdecode', 'isatty', 'get_terminal_size', 'getloadavg',
                     'R_OK', 'W_OK', 'X_OK', 'F_OK', 'error', 'strerror', 'stat_result', 'DirEntry'}),
    'sys': frozenset({'argv', 'stdin', 'stdout', 'stderr', 'exit', 'executable', 'version', 'version_info', 'platform',
                      'maxsize', 'getsizeof', 'getrecursionlimit', 'setrecursionlimit', 'byteorder', 'flags',
                      'getdefaultencoding', 'getfilesystemencoding', 'exc_info', 'float_info', 'hexversion'}),
    'signal': frozenset({'signal', 'alarm', 'setitimer', 'getitimer', 'SIGALRM', 'SIGTERM', 'SIGINT', 'SIGPIPE', 'SIG_DFL',
                         'SIG_IGN', 'ITIMER_REAL', 'ITIMER_VIRTUAL', 'ITIMER_PROF', 'default_int_handler',
                         'ItimerError'}),
    'io': frozenset({'StringIO', 'BytesIO', 'TextIOWrapper', 'UnsupportedOperation', 'DEFAULT_BUFFER_SIZE'}),
}
# zabudované mená, ktoré konajú alebo otvárajú dynamický prístup (open a getattr posúdi volanie)
KOTVA_KOPIA_VSTAVANE = frozenset({'eval', 'exec', 'compile', '__import__', 'globals', 'locals', 'vars', 'breakpoint',
                                  'setattr', 'delattr', 'open', 'getattr', '__builtins__', '__loader__', '__spec__'})
# vlastnosti, ktoré konajú alebo vedú k modulu, nech sú na čomkoľvek
KOTVA_KOPIA_VLASTNOSTI = frozenset({
    'os', 'sys', 'subprocess', 'socket', 'shutil', 'builtins', 'importlib', 'ctypes', 'pty', 'posix', 'nt', 'io', '_io',
    'signal', 'smtplib', 'ssl', 'urllib', 'runpy', 'multiprocessing', 'pickle', 'marshal',
    'system', 'popen', 'spawnl', 'spawnle', 'spawnlp', 'spawnv', 'spawnve', 'spawnvp', 'execl', 'execle', 'execlp',
    'execv', 'execve', 'execvp', 'execvpe', 'fork', 'forkpty', 'kill', 'killpg', 'posix_spawn', 'posix_spawnp',
    'remove', 'unlink', 'rmdir', 'removedirs', 'rename', 'renames', 'makedirs', 'mkdir', 'chmod', 'chown', 'lchown',
    'symlink', 'link', 'truncate', 'write_text', 'write_bytes', 'touch', 'rmtree', 'urlopen', 'urlretrieve', 'sendmail',
    'send_message', 'sendall', 'create_connection', 'exec_module', 'load_module', 'import_module',
    'spec_from_file_location', 'module_from_spec', 'run_path', 'run_module', 'attrgetter', 'methodcaller', 'Popen',
    'check_output', 'check_call', 'getoutput', 'getstatusoutput', 'putenv', 'unsetenv', 'chdir', 'dup2', 'settrace',
    'setprofile', 'addaudithook', 'modules', 'path_hooks', 'meta_path', '_getframe'})
KOTVA_KOPIA_DUNDER_OK = frozenset({'__name__', '__qualname__', '__doc__', '__init__', '__file__', '__class__', '__len__',
                                   '__iter__', '__next__', '__enter__', '__exit__', '__eq__', '__hash__', '__repr__',
                                   '__str__', '__contains__', '__getitem__', '__lt__'})
_KOTVA_KOPIA = {}


def _kotva_kopia_mena(strom):
    """Meno v kóde → modul, z ktorého prišlo (import os → os; import x as y → y: x; from os import path → path: os.path)."""
    m = {}
    for u in ast.walk(strom):
        if isinstance(u, ast.Import):
            for a in u.names:
                m[a.asname or a.name.split('.')[0]] = a.name if a.asname else a.name.split('.')[0]
        elif isinstance(u, ast.ImportFrom):
            for a in u.names:
                m[a.asname or a.name] = (u.module or '') + '.' + a.name
    return m


def _kotva_kopia_koná(u, mena, ok_mena):
    """Uzol kódu vie konať (proces, sieť, zápis, mazanie, exec, načítanie modulu, dynamický prístup)?"""
    if isinstance(u, ast.Name):
        if id(u) in ok_mena:
            return False
        if u.id in KOTVA_KOPIA_VSTAVANE:
            return True
        mod = mena.get(u.id)
        if mod is None:
            return False
        koren, _, zvysok = mod.partition('.')
        if koren in KOTVA_KOPIA_CISTE:
            return zvysok in KOTVA_KOPIA_VLASTNOSTI
        if koren in KOTVA_KOPIA_SMIE:
            return bool(zvysok) and zvysok.split('.')[0] not in KOTVA_KOPIA_SMIE[koren]
        return True
    if isinstance(u, ast.Attribute):
        a = u.attr
        if a.startswith('__') and a.endswith('__') and a not in KOTVA_KOPIA_DUNDER_OK:
            return True
        if a in KOTVA_KOPIA_VLASTNOSTI and not (isinstance(u.value, ast.Name) and u.value.id in mena
                                                 and mena[u.value.id] in KOTVA_KOPIA_SMIE
                                                 and a in KOTVA_KOPIA_SMIE[mena[u.value.id]]):
            return True
        if isinstance(u.value, ast.Name) and mena.get(u.value.id) in KOTVA_KOPIA_SMIE:
            return a not in KOTVA_KOPIA_SMIE[mena[u.value.id]]
        return False
    return False


def _kotva_kopia_volania_ok(strom):
    """id mien open/getattr vo volaniach, ktoré nekonajú: open len na čítanie (režim doslova bez w, a, x, +),
    getattr s doslovným menom, ktoré nie je dunder ani vlastnosť, čo koná."""
    ok = set()
    for u in ast.walk(strom):
        if not (isinstance(u, ast.Call) and isinstance(u.func, ast.Name)):
            continue
        if u.func.id == 'open':
            rez = [k.value for k in u.keywords if k.arg == 'mode']
            rez = rez or u.args[1:2]
            if not rez or (isinstance(rez[0], ast.Constant) and isinstance(rez[0].value, str)
                           and not set(rez[0].value) & set('wax+')):
                if not any(k.arg is None for k in u.keywords) and not any(isinstance(x, ast.Starred) for x in u.args):
                    ok.add(id(u.func))
        elif u.func.id == 'getattr' and len(u.args) >= 2:
            m = u.args[1]
            if (isinstance(m, ast.Constant) and isinstance(m.value, str) and not m.value.startswith('_')
                    and m.value not in KOTVA_KOPIA_VLASTNOSTI):
                ok.add(id(u.func))
    return ok


def _kotva_kopia_py(zivy, kopia):
    """Upravená kópia pythonového súboru kotvy nekoná nič, čo živá nekoná: importy ⊆ živé, každé miesto, ktoré vie konať,
    je vo funkcii zhodnej so živou (na úrovni modulu v príkaze zhodnom so živým)."""
    try:
        L, C = ast.parse(zivy), ast.parse(kopia)
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        return False
    imp_l = {ast.dump(u) for u in ast.walk(L) if isinstance(u, (ast.Import, ast.ImportFrom))}
    if any(ast.dump(u) not in imp_l for u in ast.walk(C) if isinstance(u, (ast.Import, ast.ImportFrom))):
        return False
    mena = _kotva_kopia_mena(L)
    mena.update(_kotva_kopia_mena(C))
    ok_mena = _kotva_kopia_volania_ok(C)
    podozrive = []          # (najvnútornejšia funkcia alebo None, príkaz na úrovni modulu)
    zasobnik = [(x, None, x) for x in C.body]
    while zasobnik:
        u, fn, hore = zasobnik.pop()
        if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef)):
            fn = u
        if _kotva_kopia_koná(u, mena, ok_mena):
            podozrive.append((fn, hore))
        zasobnik.extend((d, fn, hore) for d in ast.iter_child_nodes(u))
    if not podozrive:
        return True
    fn_l, hore_l = {}, None
    for u in ast.walk(L):
        if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef)):
            fn_l.setdefault(u.name, []).append(u)
    hotove = set()
    for fn, hore in podozrive:
        if id(fn if fn is not None else hore) in hotove:
            continue
        if fn is not None:
            d = ast.dump(fn)
            if not any(ast.dump(x) == d for x in fn_l.get(fn.name, ())):
                return False
            hotove.add(id(fn))
        else:
            if hore_l is None:
                hore_l = {ast.dump(x) for x in L.body}
            if ast.dump(hore) not in hore_l:
                return False
            hotove.add(id(hore))
    return True


def _kotva_sh_riadky(txt):
    """Riadky shellu s druhom: ('k', None) kód · ('h', i) telo heredocu, ktorý otvoril riadok i (len s úvodzovkami pri
    oddeľovači — bez nich sa v tele rozvinie $(…)) · ('x', i) telo heredocu bez úvodzoviek · ('s', i) pokračovanie reťazca,
    ktorý sa začal na riadku i · ('e', i) riadok s oddeľovačom, ktorý heredoc z riadku i končí."""
    riadky = txt.split('\n')
    druh, i, n = [], 0, len(riadky)
    q, q_zac = None, None
    while i < n:
        r = riadky[i]
        druh.append(('s', q_zac) if q else ('k', None))
        heredocy, k = [], 0
        while k < len(r):
            ch = r[k]
            if q == "'":
                if ch == "'":
                    q = None
            elif q in ('"', "$'"):
                if ch == '\\':
                    k += 1
                elif ch == q[-1]:
                    q = None
            elif ch == '\\':
                k += 1
            elif r.startswith("$'", k):
                q, q_zac = "$'", i
                k += 1
            elif ch in '\'"':
                q, q_zac = ch, i
            elif ch == '#' and (k == 0 or r[k - 1] in ' \t;&|('):
                break
            elif r.startswith('<<<', k):
                k += 2
            elif r.startswith('<<', k):
                m = re.match(r'<<(-?)\s*(?:\'([^\']+)\'|"([^"]+)"|\\?([A-Za-z_][A-Za-z0-9_]*))', r[k:])
                if m:
                    heredocy.append((m.group(2) or m.group(3) or m.group(4), m.group(4) is None, m.group(1)))
                    k += m.end() - 1
            k += 1
        otvarac = i
        i += 1
        if q is None:
            for oddel, uvodz, pomlcka in heredocy:
                while i < n:
                    t = riadky[i].lstrip('\t') if pomlcka else riadky[i]
                    druh.append(('e' if uvodz else 'x', otvarac) if t == oddel else ('h' if uvodz else 'x', otvarac))
                    i += 1
                    if t == oddel:
                        break
    return riadky, druh


_SH_ZAKAZ = re.compile(r'\$\(|`|<\(|>\(|[;&|]|(?<!<)>|<(?!<)')


def _sh_bez_jednoduchych(r):
    """Riadok bez obsahu jednoduchých úvodzoviek a bez komentára (na hľadanie $(, `, ; …)."""
    out, q, k = [], None, 0
    while k < len(r):
        ch = r[k]
        if q == "'":
            if ch == "'":
                q = None
        elif q == "$'":
            if ch == '\\':
                k += 1
            elif ch == "'":
                q = None
        elif q == '"':
            out.append(ch)
            if ch == '\\':
                k += 1
            elif ch == '"':
                q = None
        else:
            if ch == '\\':
                k += 1
            elif r.startswith("$'", k):
                q = "$'"
                k += 1
            elif ch == "'":
                q = "'"
            elif ch == '"':
                q = '"'
                out.append(ch)
            elif ch == '#' and (k == 0 or r[k - 1] in ' \t'):
                break
            else:
                out.append(ch)
        k += 1
    return ''.join(out)


def _sh_volanie_pripadu(r, pripady):
    """Riadok je volanie funkcie prípadu s doslovnými argumentmi (premenné v úvodzovkách smú, $(…), `…`, rúra, ; nie),
    na konci smie otvoriť heredoc s úvodzovkami. → meno funkcie alebo None."""
    s = _sh_bez_jednoduchych(r).strip()
    s = re.sub(r'\s*<<-?\s*(?:"[^"]*"|\\?[A-Za-z_]\w*)?\s*$', '', s) if re.search(r"<<-?\s*'", r) else s
    if re.search(r'<<', s) or _SH_ZAKAZ.search(s):
        return None
    m = re.match(r'(?:(?:NG_KOTVA_\w+|HOME_OVR)=[\w./:@%+,-]*\s+)*([A-Za-z_]\w*)(?:\s|$)', s)
    return m.group(1) if m and m.group(1) in pripady else None


def _kotva_kopia_sh(zivy, kopia, test):
    """Upravená kópia háčika (len komentáre) alebo červeného testu (aj dáta prípadov, nové funkcie prípadov, H=/SIG=/LOG=)."""
    rl, dl = _kotva_sh_riadky(zivy)
    rc, dc = _kotva_sh_riadky(kopia)
    if len(dc) != len(rc):
        return False
    pripady = {'rozhodni', 'ocakavaj', 'ocakavaj_h'} if test else set()
    # nové aj živé funkcie prípadu v kópii: telo len local, x=$(cat) a volania funkcií prípadu (pevný bod)
    funkcie = {}
    j = 0
    while j < len(rc):
        m = re.match(r'([A-Za-z_]\w*)\(\)\s*\{(.*)$', rc[j]) if dc[j][0] == 'k' else None
        if m:
            telo, k = [], j
            if re.search(r'\}\s*(?:#.*)?$', m.group(2)) and m.group(2).count('{') < m.group(2).count('}') + 1:
                telo = [re.sub(r'\}\s*(?:#.*)?$', '', m.group(2))]
            else:
                telo.append(m.group(2))
                k = j + 1
                while k < len(rc) and not (dc[k][0] == 'k' and rc[k].startswith('}')):
                    telo.append(rc[k] if dc[k][0] == 'k' else '\x00')
                    k += 1
            funkcie[m.group(1)] = (j, k, telo)
            j = k + 1
        else:
            j += 1
    if test:
        zmena = True
        while zmena:
            zmena = False
            for meno, (_, _, telo) in funkcie.items():
                if meno in pripady or meno in ('rozhodni', 'ocakavaj', 'ocakavaj_h'):
                    continue
                if all(_sh_prikaz_pripadu(x, pripady) for x in telo):
                    pripady.add(meno)
                    zmena = True
    hlavicky = {j: (meno, k) for meno, (j, k, _) in funkcie.items()}
    v_pripade = {}
    for meno, (j, k, _) in funkcie.items():
        if meno in pripady and meno not in ('rozhodni', 'ocakavaj', 'ocakavaj_h'):
            for x in range(j, k + 1):
                v_pripade[x] = meno
    sm = difflib.SequenceMatcher(None, [(r, d[0]) for r, d in zip(rl, dl)], [(r, d[0]) for r, d in zip(rc, dc)],
                                 autojunk=False)
    for op, i1, i2, j1, j2 in sm.get_opcodes():
        if op == 'equal':
            continue
        for j in range(j1, j2):
            if not _sh_riadok_ok(rc, dc, j, pripady, v_pripade, test):
                return False
    # základné funkcie prípadu musia byť zhodné so živými (ich riadky nesmú byť v zmene)
    return True


def _sh_prikaz_pripadu(x, pripady):
    for c in [c.strip() for c in re.split(r';\s*', x)] if x != '\x00' else ['\x00']:
        if not c or c.startswith('#'):
            continue
        if c == '\x00':
            return False
        if re.fullmatch(r'local(?:\s+[A-Za-z_]\w*)+', c) or re.fullmatch(r'[A-Za-z_]\w*=\$\(cat\)', c):
            continue
        if _sh_volanie_pripadu(c, pripady) is None:
            return False
    return True


def _sh_riadok_ok(rc, dc, j, pripady, v_pripade, test):
    r, (druh, odkial) = rc[j], dc[j]
    if druh == 'k':
        s = r.strip()
        if not s or s.startswith('#'):
            return True
        if not test:
            return False
        if j in v_pripade:
            return True
        if re.fullmatch(r'(?:H|SIG|LOG)=(?:"[^"$`\\]*"|\'[^\']*\'|[\w./@%+,:-]+)\s*(?:#.*)?', s):
            if s.startswith('H='):
                cesta = re.match(r'H=["\']?([^"\'\s#]*)', s).group(1)
                if os.path.basename(cesta) != 'ng-kotva.sh' or not cesta.startswith('/'):
                    return False
                try:
                    with open(cesta, 'rb') as f:
                        return kotva_vlastny(cesta, f.read(SKRIPT_MAX_SUBOR + 1))
                except (OSError, ValueError):
                    return False
            return True
        return _sh_volanie_pripadu(r, pripady) is not None
    if druh in ('h', 'e'):
        return test and _sh_volanie_pripadu(rc[odkial], pripady) is not None
    if druh == 's':
        return test and _sh_volanie_pripadu(rc[odkial], pripady) is not None
    return False


def kotva_kopia_bezpecna(p, obsah):
    """Súbor s menom kotvy, ktorý sa líši od živej: zmena nepridala nič, čo vie konať (posudok vyššie)."""
    meno = os.path.basename(p)
    if meno not in KOTVA_MENA or len(obsah) > SKRIPT_MAX_SUBOR:
        return False
    kluc = (meno, hashlib.sha1(obsah).hexdigest())
    if kluc in _KOTVA_KOPIA:
        return _KOTVA_KOPIA[kluc]
    _KOTVA_KOPIA[kluc] = False                # zacyklenie (test → háčik → …) = neprejde
    try:
        kopia = obsah.decode('utf-8')
    except UnicodeDecodeError:
        return False
    vysledok, skusane = False, set()
    for d in kotva_priecinky():
        q = os.path.normpath(os.path.join(d, meno))
        if q in _KOTVA_OBSAH:
            zivy = _KOTVA_OBSAH[q]
        else:
            try:
                with open(q, 'rb') as f:
                    zivy = _KOTVA_OBSAH[q] = f.read(SKRIPT_MAX_SUBOR + 1)
            except (OSError, ValueError):
                zivy = _KOTVA_OBSAH[q] = None
        if zivy is None or zivy in skusane:
            continue
        skusane.add(zivy)
        try:
            z = zivy.decode('utf-8')
        except UnicodeDecodeError:
            continue
        if meno.endswith('.py'):
            vysledok = _kotva_kopia_py(z, kopia)
        else:
            vysledok = _kotva_kopia_sh(z, kopia, meno == 'ng-kotva-test.sh')
        if vysledok:
            break
    _KOTVA_KOPIA[kluc] = vysledok
    return vysledok


# K88S (14. 10.): súbor stráže sa nečíta, keď je živý alebo prejde posudkom kópie — a doteraz sa nečítalo ani to, čo si
# naloží podľa svojej polohy (D1 z K88Q): doslovná kópia háčika v dielni vedľa súboru s menom rozboru, ktorý posiela
# poštu, prešla bez otázky, hoci háčik spúšťa rozbor, ktorý leží vedľa neho. Súbor stráže je jej vlastný, len keď aj každý
# súbor, ktorý si naloží (poradie ako v ňom: háčik vedľa seba, potom ../scripts, potom /opt/aigrip-ng/scripts; overenie
# potvrdenia určovateľa hovoriaceho a aktéra vedľa seba), je živý alebo prejde posudkom kópie (súbory mimo KOTVA_MENA len
# doslovne). Python bez -I berie priečinok skriptu ako prvé miesto importu: v priečinku pythonového súboru z reťazca nesmie
# ležať modul s menom zo štandardnej knižnice, a predkompilovaný súbor (__pycache__) modulu, ktorý sa naloží z disku, musí
# mať ten istý kód ako jeho zdroj (python ho inak spustí namiesto zdroja). Hranica: háčik načítaný cez source (. háčik)
# berie rozbor z pracovného priečinka, nie zo svojho — to stráž nevidí (háčik zo vstupu číta a končí, source nedáva zmysel);
# prehrávka kolegov spúšťa háčik daný argumentom (--stara, --nova) — ten sa nečíta.
KOTVA_NAKLAD = {   # meno súboru stráže → (súbor, kde ho hľadá, len prvý nájdený, 'sh' | 'main' | 'mod')
    'ng-kotva.sh': (('ng-straz-beh.sh', ('.', '../scripts', '/opt/aigrip-ng/scripts'), True, 'sh'),
                    ('ng-kotva-analyza.py', ('.', '../scripts', '/opt/aigrip-ng/scripts'), True, 'main'),
                    ('ng-kotva-over.py', ('.', '../scripts', '/opt/aigrip-ng/scripts'), True, 'main')),
    'ng-kotva-over.py': (('ng-hovoriaci.py', ('.',), True, 'mod'), ('ng-akter.py', ('.', '/opt/aigrip-ng/scripts'), False, 'mod')),
    'ng-hovoriaci.py': (('ng-akter.py', ('.',), True, 'mod'),),
    'ng-kotva-overenie.py': (('ng-kotva-over.py', ('.', '/home/pstrocka/projects/aigrip/.aigrip/scripts'), True, 'mod'),),
    'ng-prehravka-kolegov.py': (('ng-stopy.py', ('.',), True, 'mod'),),
}
_KOTVA_NAKLAD = {}


def _kotva_tien_ok(adr):
    """V priečinku adr neleží modul s menom zo štandardnej knižnice (python ho načíta namiesto nej)."""
    mena = getattr(sys, 'stdlib_module_names', None) or frozenset(sys.builtin_module_names) | KOTVA_KOPIA_CISTE | {
        'os', 'sys', 'io', 'subprocess', 'importlib', 'marshal', 'signal', 'shutil', 'tempfile', 'socket', 'urllib',
        'http', 'email', 'smtplib', 'json', 'codecs', 'encodings', 'site', 'locale', 'contextlib', 'weakref', 'types'}
    try:
        polozky = os.listdir(adr)
    except OSError:
        return True                      # priečinok nevidno — python z neho nenačíta nič
    for e in polozky:
        m = e.split('.', 1)[0]
        if m not in mena:
            continue
        q = os.path.join(adr, e)
        if os.path.isdir(q):
            if any(os.path.exists(os.path.join(q, x)) for x in ('__init__.py', '__init__.pyc')) \
                    or glob.glob(os.path.join(glob.escape(q), '__init__.*.so')):
                return False             # balík (priečinok bez __init__ je len časť menného priestoru — nezatieni)
        elif e.endswith(('.py', '.pyc', '.so', '.pyd')):
            return False
    return True


def _kotva_pyc_ok(q, zdroj):
    """Predkompilovaný súbor modulu q (adr/__pycache__/meno.*.pyc), ktorý by python použil namiesto zdroja, má ten istý kód
    ako zdroj (porovnanie kódu bez cesty súboru). Súbor podľa času a veľkosti zdroja, ktoré nesedia, python nepoužije."""
    import marshal
    kmen = os.path.splitext(os.path.basename(q))[0]
    try:
        st = os.stat(q)
    except OSError:
        return False
    for c in glob.glob(os.path.join(glob.escape(os.path.join(os.path.dirname(q), '__pycache__')), glob.escape(kmen) + '.*.pyc')):
        try:
            with open(c, 'rb') as f:
                b = f.read(SKRIPT_MAX_SUBOR * 4 + 1)
            if len(b) < 16 or len(b) > SKRIPT_MAX_SUBOR * 4:
                return False
            zn = int.from_bytes(b[4:8], 'little')
            if zn == 0 and (int.from_bytes(b[8:12], 'little') != int(st.st_mtime) & 0xFFFFFFFF
                            or int.from_bytes(b[12:16], 'little') != st.st_size & 0xFFFFFFFF):
                continue                 # zastaraný — python ho nepoužije a preloží zdroj
            m = re.search(r'\.opt-([12])\.pyc$', c)
            if marshal.loads(b[16:]) != compile(zdroj, q, 'exec', dont_inherit=True, optimize=int(m.group(1)) if m else 0):
                return False
        except Exception:
            return False
    return True


def kotva_naklad_ok(meno, adr, hlbka=0):
    """Súbor stráže meno spustený z priečinka adr: všetko, čo si naloží podľa svojej polohy, je živé alebo prejde posudkom
    kópie, a python z jeho priečinka nenačíta nič cudzie (K88S, poznámka vyššie)."""
    if hlbka > 6:
        return False
    kluc = (meno, adr)
    if kluc in _KOTVA_NAKLAD:
        return _KOTVA_NAKLAD[kluc]
    _KOTVA_NAKLAD[kluc] = False          # zacyklenie = neprejde
    ok = True
    try:
        zaklad = os.path.realpath(adr)   # bash aj python rozložia ../ podľa skutočného priečinka, nie podľa textu cesty
        if meno.endswith('.py') and not (_kotva_tien_ok(adr) and _kotva_tien_ok(zaklad)):
            ok = False
        for m, kde, prvy, sposob in KOTVA_NAKLAD.get(meno, ()) if ok else ():
            for k in kde:
                q = os.path.normpath(os.path.join(zaklad, k, m))
                if not os.path.isfile(q):
                    continue
                with open(q, 'rb') as f:
                    b = f.read(SKRIPT_MAX_SUBOR + 1)
                if not (kotva_ziva(m, b) or (m in KOTVA_MENA and kotva_kopia_bezpecna(q, b))) \
                        or (sposob == 'mod' and not _kotva_pyc_ok(q, b)) \
                        or not kotva_naklad_ok(m, os.path.dirname(q), hlbka + 1):
                    ok = False
                    break
                if prvy:
                    break
            if not ok:
                break
    except (OSError, ValueError, RecursionError):
        ok = False
    _KOTVA_NAKLAD[kluc] = ok
    return ok
# poštový modul načítaný skriptom posiela, len keď skript volá odoslanie (gm.send(…), send_mail(…)); main() a slovo
# send v úvodzovkách v skripte nestačia — EP posta.py načíta graph_mail.py len pre token a poštu číta (prehrávka PS 2. 10.)
SKRIPT_POSTA_POKYN = re.compile(r'\b(?:send|send_mail|send_message|sendmail|reply|reply_all|forward|odosli|posli)\w*\s*\(',
                                re.I)


# Zápis cez API v kóde (2. 10., prehrávka PS: napoj-spinoff.py napoj … naostro zakladal schránky a práva v Exchange cez
# urllib POST na outlook.office365.com/adminapi …/InvokeCommand — rozbor kódu to nevidel). Ten istý cieľ ako pri curl
# (CURL_CIEL: Graph, SharePoint, Outlook/Exchange, Atlassian, GitHub API) a zápisová metóda pri tom istom volaní (urllib
# Request s data=, requests.post/put/patch/delete, fetch s method: 'POST' …) = zápis do cudzieho systému. Metóda
# v premennej = zápis (ako curl -X "$M"). POST, ktorý len číta: Exchange InvokeCommand s Get-*/Test-* (zápisový cmdlet New-,
# Add-, Set-, Remove- … v kóde = zápis), Graph $batch a vyhľadávanie, GitHub graphql bez mutation.
KOD_HTTP_KONCEPT = re.compile(r'(?:^|graph\.microsoft\.com/(?:v1\.0|beta))/?(?:me|users/[^/?#]+)/(?:mailFolders/[^/?#]+/)?'
                              r'messages/?(?:[?#].*)?$')
# KOTF (23. 10.): vyhľadávanie v Jire (POST …/search, …/search/jql, …/search/approximate-count) je čítanie — v Jire nič
# nemení; meranie KOTW: podagent v ADM sa pýtal na počet úloh s výkazom práce. Cesta doslova, koniec cesty pevný.
JIRA_HLADANIE = re.compile(r'/rest/api/(?:2|3|latest)/search(?:/jql|/approximate-count)?/?(?:[?#]|$)')
KOD_HTTP_CITA_POST = re.compile(r'/\$batch\b|/search/query\b|/getSchedule\b|/findMeetingTimes\b|/reports/|/getByIds\b'
                                r'|/checkMember\w*|/getMember\w*|/delta\b|' + JIRA_HLADANIE.pattern, re.I)
KOD_EXO_ZAPIS = re.compile(r'''['"](?:New|Add|Set|Remove|Enable|Disable|Update|Grant|Revoke|Import|Start|Stop|Clear|Restore'''
                           r'''|Suspend|Resume|Install|Uninstall|Send|Disconnect)-[A-Z][A-Za-z]+['"]''')
KOD_HTTP_KLIENT = ('requests', 'httpx', 'aiohttp', 'session', 'client', 'http', 's', 'sess', 'axios', 'got', 'superagent')
# Zápis cez HTTP z kódu Perlu, Ruby a PHP (kus kod_http_zapis, 10. 10.): rozbor volaní poznal len mená klientov pythonu
# a node (requests, httpx, aiohttp, urllib, fetch, axios), preto `perl -e 'LWP::UserAgent->new->post(…)'`,
# `ruby -e 'Net::HTTP.post(URI("https://…"), …)'`, `php -r 'file_get_contents($u, false, $ctx)'` s POST kontextom prúdu
# a `php -r 'curl_init(…); curl_setopt(…, CURLOPT_POST, 1); curl_exec(…)'` prešli bez otázky, hoci ten istý zápis cez
# klienta v príkaze kotva zastaví. Klient sa berie napísaný na mieste volania aj uložený do premennej
# (`my $ua = LWP::UserAgent->new; $ua->post(…)`). Mená oddelené `->` a `::` tokeny do jedného mena nespájajú (na rozdiel
# od bodky v pythone) — cestu volania skladá kod_cesta_volania.
KOD_HTTP_KLIENT_INY = re.compile(r'(?:LWP(?:::\w+)*|HTTP::(?:Tiny|Request)|REST::Client|Mojo::UserAgent|WWW::Mechanize'
                                 r'|Furl|Net::HTTP(?:::\w+)*|RestClient|HTTParty|Faraday|Typhoeus|Excon|HTTPClient'
                                 r'|(?:\\)?GuzzleHttp\\Client)(?:->|::|\.)')
KOD_HTTP_KLIENT_PREM = re.compile(r'(\$?\w+)\s*=\s*(?:new\s+)?(?:LWP::UserAgent|HTTP::Tiny|REST::Client|Mojo::UserAgent'
                                  r'|WWW::Mechanize|Furl|Net::HTTP|RestClient|HTTParty|Faraday|Typhoeus|Excon'
                                  r'|HTTPClient|(?:\\)?GuzzleHttp\\Client)\b')
# SKG 3 (b5, 7. 11.): každá vetva KOD_HTTP_KLIENT_PREM potrebuje doslova jedno z týchto mien (predbežné hľadanie
# v _http_zapis_kd; 1,2 MB ng-cervene-testy.py: 76 ms → 4 ms). PRI ZMENE VZORU DOPLŇ AJ TENTO ZOZNAM.
KOD_HTTP_KLIENT_PREM_MENA = ('LWP::UserAgent', 'HTTP::Tiny', 'REST::Client', 'Mojo::UserAgent', 'WWW::Mechanize', 'Furl',
                             'Net::HTTP', 'RestClient', 'HTTParty', 'Faraday', 'Typhoeus', 'Excon', 'HTTPClient',
                             'GuzzleHttp\\Client')
KOD_HTTP_VOLANIE_INY = ('get', 'post', 'post_form', 'put', 'patch', 'delete', 'head', 'request', 'send_request',
                        'mirror', 'new')
KOD_HTTP_METODA_MENO = ('get', 'post', 'put', 'patch', 'delete', 'head')
KOD_HTTP_CITA_METODY = ('GET', 'HEAD', 'OPTIONS')
# PHP: adresa sa otvorí ako súbor a metóda je v kontexte prúdu
# (stream_context_create(['http' => ['method' => 'POST', …]])); curl v PHP je volanie knižnice, nie nástroj v príkaze
KOD_PHP_OTVORI = ('file_get_contents', 'fopen', 'readfile', 'file')
KOD_PHP_KONTEXT_METODA = re.compile(r'''['"]method['"]\s*=>\s*['"](\w+)['"]''', re.I)
KOD_PHP_CURL_ZAPIS = re.compile(r'\bCURLOPT_(?:POST|POSTFIELDS|CUSTOMREQUEST|UPLOAD|PUT|INFILE)\b')
KOD_PHP_CURL_METODA = re.compile(r'''\bCURLOPT_CUSTOMREQUEST\s*,\s*['"](\w+)['"]''', re.I)
KOD_HTTP_SCHEMA = re.compile(r'https?://', re.I)


def kod_cesta_volania(t, k):
    """Meno volania spolu s tým, čo je pred ním oddelené bodkou, `->` alebo `::` — `LWP::UserAgent->new->post`,
    `Net::HTTP.post`, `$ua->post`."""
    casti = [t[k][1]]
    j = k
    while j >= 2 and t[j - 1][0] == 'p' and t[j - 1][1] in ('.', '->', '::') and t[j - 2][0] == 'n':
        casti[:0] = [t[j - 2][1], t[j - 1][1]]
        j -= 2
    return ''.join(casti)


def kod_adresy_v_tele(kr, z, kon):
    """Adresy v tele volania: (1) reťazce, ktoré sú adresou (adresa_druh) — čítajú sa bez vyhodnocovania výrazov, takže
    sa nájdu aj tam, kde vyhodnotenie dá „neznáme“ (`Net::HTTP.post(URI("https://…"), …)`); (2) prečítaná hodnota
    prvého argumentu alebo `url=`, keď ju kotva vie zložiť (perl `"$h/v1.0/users"`, php `$h . "/v1.0/users"`).
    Hodnota, ktorú kotva prečítať nevie, sa nepridáva — v kóde je neznáma adresa väčšina volaní (cudzia_kod)."""
    von = []
    for j in range(z, min(kon, len(kr.t))):
        if kr.t[j][0] == 's':
            a = kr.retazec(kr.t[j][1]).strip()
            if a and a not in von and adresa_druh(a) is not None:
                von.append(a)
    try:
        poz, kl = kr.argumenty(z, kon, 0)
        for h in (kl.get('url') or (poz[0] if poz else [])):
            a = kr.text(h).strip()
            if a and NAJHORSIE not in a and a not in von and adresa_druh(a) is not None:
                von.append(a)
    except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
        pass
    return von


def kod_metoda_v_tele(kr, z, kon):
    """Metóda napísaná v tele volania — `HTTP::Request->new(POST => $u)`, `$ua->request('PUT', $u, …)`."""
    for j in range(z, min(kon, len(kr.t))):
        d, v = kr.t[j]
        x = (kr.retazec(v) if d == 's' else v if d == 'n' else '').strip().upper()
        if x in CUDZIA_ZAPIS or x in KOD_HTTP_CITA_METODY:
            return x
    return None


def skript_nacita_postu(kd):
    """Skript načíta poštový modul (import, importlib podľa mena s mail) a volá odoslanie."""
    if not SKRIPT_POSTA_POKYN.search(kd):
        return False
    if KOD_NACITA_POSTU_DYN.search(kd):
        return True
    for m in KOD_IMPORT_POSTA.finditer(kd):
        mena = [x.strip().split(' ')[0] for x in (m.group(1) or m.group(2) or '').split(',')]
        if any('mail' in x.lower() and x.split('.')[0].lower() not in POSTA_STDLIB for x in mena if x):
            return True
    return False


class SkriptVelky:
    """Súbor skriptu nad SKRIPT_MAX_SUBOR — začiatok (jazyk podľa shebangu); celý sa nečíta."""
    __slots__ = ('hlava',)

    def __init__(self, hlava):
        self.hlava = hlava


def skript_bash(pb, t, obs):
    """Skript pobeží v bashi (abstraktný beh vetiev pozná len bash): bash x.sh len s voľbami -e -u -x -v; source a . v tomto
    shelli; ./x podľa shebangu (bez shebangu ho spustí bash). sh = dash, zsh, ksh, bash -O …, --posix, -i … — nie."""
    if pb == 'bash':
        k = skript_interpretra(pb, t)
        return all(re.fullmatch(r'[-+][euxv]+|--norc|--noprofile', x) for x in t[:k or 0])
    if pb in SHELLY:
        return False
    if pb in ('source', '.'):
        return True
    prvy = obs.split('\n', 1)[0] if isinstance(obs, str) else ''
    return not prvy.startswith('#!') or bool(re.search(r'\bbash\b', prvy))


def skript_jazyk(p, obs):
    """Jazyk skriptu podľa prípony a shebangu: 'sh' · 'py' · 'js' · None (iný jazyk, binárka)."""
    prvy = obs.split('\n', 1)[0] if obs else ''
    if prvy.startswith('#!'):
        if re.search(r'python', prvy):
            return 'py'
        if re.search(r'\b(?:node|nodejs|deno|bun)\b', prvy):
            return 'js'
        if re.search(r'\b(?:ba|da|z|k|mk)?sh\b', prvy):
            return 'sh'
        return None
    if p.endswith(('.sh', '.bash')):
        return 'sh'
    if p.endswith('.py'):
        return 'py'
    if p.endswith(('.js', '.mjs', '.cjs')):
        return 'js'
    return None


def skript_kod(obs, jazyk):
    """Kód skriptu bez komentárov a popisu (voľba či vzor v komentári nič nerozlišuje ani nespúšťa): js /* … */, python
    docstring (reťazec v trojitých úvodzovkách na začiatku riadku — nie priradený do premennej)."""
    if jazyk == 'js':
        obs = re.sub(r'/\*[\s\S]*?\*/', '', obs)
    elif jazyk == 'py':
        obs = py_popisy(obs)             # SKG 2 (6. 11.): páry úvodzoviek podľa stromu, keď vzor pokazí platný kód
    von = []
    for r in obs.split('\n'):
        s = r.lstrip()
        if s.startswith('#') or (jazyk == 'js' and s.startswith('//')):
            continue
        von.append(re.sub(r'''\s#[^'"`\n]*$''', '', r) if jazyk in ('sh', 'py') else r)
    return '\n'.join(von)


# Režim nástroja v node (2. 10., prehrávka kolegov): jeden skript má viac režimov — `sender-pripomienky.cjs --stav | --zaloz |
# --naplanuj`. Vetva, ktorú skript vyberá porovnaním argumentu z príkazového riadku s textom (if (rezim === '--naplanuj')
# {…}), a vyvolanie ten text nedalo, sa nespustí — z rozboru sa vynechá. Len keď je podmienka celá z takých porovnaní (aj
# „alebo“ medzi nimi) a premenná je priamo argument (process.argv[2], const rezim = process.argv[2], pole z
# process.argv.slice(2)); predvolená hodnota (|| 'send') sa ráta ako daná. Inak sa vetva posudzuje celá.
# 3. 10. (overenie kola 1: vetvy režimov podľa regulárnych výrazov brali argument funkcie za argument skriptu — `krok() {
# case "$1" in obnov) az ad app credential reset …;; esac; }; krok obnov` aj `def notify(argv): if argv[1] == 'post'`
# prešli bez jediného slova navyše): shell a python túto skratku už nemajú — vetvy podľa vyvolania vynechá len abstraktný
# beh (sh_volby, py_volby), ktorý argument funkcie od argumentu skriptu rozlíši. Node abstraktný beh nemá, preto sa kód číta
# po tokenoch (reťazce, šablóny, komentáre, regulárne výrazy; čo sa nedá prečítať, nevynechá nič) a vetva sa vynechá, len
# keď sa meno nedá zatieniť ani zmeniť: process sa používa len ako process.…, argv a pole z neho sa len čítajú (nie push,
# splice, priradenie, odovzdanie inam), premenná režimu má jedinú väzbu (nie parameter, catch, rozklad, druhé
# const/let/var, ++/--, ani v zozname argumentov) a v kóde nie je eval ani with. Text porovnania so \ alebo ${ sa neberie.
JS_MENO = re.compile(r'(?:[^\W\d]|\$)[\w$]*')
JS_CISLO = re.compile(r'\d[\w.]*|\.\d\w*')
JS_OPERATOR = re.compile(r'>>>=|\.\.\.|===|!==|\*\*=|<<=|>>=|>>>|&&=|\|\|=|\?\?=|=>|==|!=|<=|>=|&&|\|\||\?\?|\?\.(?!\d)'
                         r'|\+\+|--|\+=|-=|\*=|/=|%=|&=|\|=|\^=|\*\*|<<|>>|[{}()\[\];,<>+\-*/%&|^!~?:=.@#]')
JS_PRED_VYRAZOM = {'return', 'typeof', 'instanceof', 'in', 'of', 'new', 'delete', 'void', 'throw', 'case', 'do', 'else',
                   'yield', 'await'}
JS_PRIRADENIE = {'=', '+=', '-=', '*=', '/=', '%=', '**=', '<<=', '>>=', '>>>=', '&=', '|=', '^=', '&&=', '||=', '??=', '++',
                 '--'}
JS_CITA_METODY = {'includes', 'indexOf', 'lastIndexOf', 'slice', 'find', 'findIndex', 'findLast', 'findLastIndex', 'some',
                  'every', 'filter', 'map', 'forEach', 'join', 'at', 'concat', 'entries', 'keys', 'values', 'reduce',
                  'reduceRight', 'flat', 'flatMap', 'toString'}


def js_tokeny(kod):
    """Tokeny kódu node s polohou: [(druh, text, od, do)] — 'n' meno, 's' reťazec (text bez úvodzoviek; None pri escape
    a pri šablóne), 'c' číslo, 'p' znak, 'r' regulárny výraz. Vložený výraz šablóny `…${x}…` dá vlastné tokeny. Neukončený
    reťazec, šablóna, komentár či regulárny výraz, alebo reťazec cez koniec riadku → None (kód sa nedá spoľahlivo prečítať)."""
    t = []
    i, n = 0, len(kod)
    sablony = []                 # hĺbka { } pri každom otvorenom ${ v šablóne
    hl = 0
    while i < n:
        c = kod[i]
        if c in ' \t\r\n\f\v\ufeff\u00a0\u2028\u2029':
            i += 1
            continue
        if kod.startswith('//', i) or (kod.startswith('#!', i) and i == 0):
            j = kod.find('\n', i)
            i = n if j < 0 else j
            continue
        if kod.startswith('/*', i):
            j = kod.find('*/', i + 2)
            if j < 0:
                return None
            i = j + 2
            continue
        if c in '\'"':
            j, esc = i + 1, False
            while j < n and kod[j] != c:
                if kod[j] == '\n':
                    return None
                if kod[j] == '\\':
                    esc = True
                    j += 1
                j += 1
            if j >= n:
                return None
            t.append(('s', None if esc else kod[i + 1:j], i, j + 1))
            i = j + 1
            continue
        if c == '`' or (c == '}' and sablony and sablony[-1] == hl):
            if c == '}':
                sablony.pop()
            j = i + 1
            while j < n and kod[j] != '`' and not kod.startswith('${', j):
                j += 2 if kod[j] == '\\' else 1
            if j >= n:
                return None
            t.append(('s', None, i, j + 1))
            if kod[j] == '`':
                i = j + 1
            else:
                sablony.append(hl)
                i = j + 2
            continue
        if c == '/':
            p = t[-1] if t else None
            if p is None or (p[0] == 'p' and p[1] not in (')', ']', '}', '++', '--')) \
                    or (p[0] == 'n' and p[1] in JS_PRED_VYRAZOM):
                j, trieda = i + 1, False
                while j < n and (kod[j] != '/' or trieda):
                    if kod[j] == '\n':
                        return None
                    if kod[j] == '\\':
                        j += 1
                    elif kod[j] == '[':
                        trieda = True
                    elif kod[j] == ']':
                        trieda = False
                    j += 1
                if j >= n:
                    return None
                j += 1
                while j < n and (kod[j].isalnum() or kod[j] in '_$'):
                    j += 1
                t.append(('r', kod[i:j], i, j))
                i = j
                continue
        m = JS_MENO.match(kod, i)
        if m:
            t.append(('n', m.group(), i, m.end()))
            i = m.end()
            continue
        m = JS_CISLO.match(kod, i)
        if m:
            t.append(('c', m.group(), i, m.end()))
            i = m.end()
            continue
        m = JS_OPERATOR.match(kod, i)
        x = m.group() if m else c
        if x == '{':
            hl += 1
        elif x == '}':
            hl -= 1
        t.append(('p', x, i, i + len(x)))
        i += len(x)
    return None if sablony else t


def js_rezimy(kod, tokeny):
    """Node: kód bez vetiev režimov, ktoré vyvolanie nevybralo (vyššie). tokeny = argumenty vyvolania; neznámy argument
    (premenná) = režim sa nedá určiť → kód sa vráti celý."""
    if any(NEZNAME in x or '$' in x for x in tokeny):
        return kod
    try:
        return _js_rezimy(kod, set(tokeny) | {x.split('=', 1)[0] for x in tokeny if x.startswith('-')})
    except (RecursionError, ValueError, IndexError, KeyError):
        return kod


def _js_rezimy(kod, dane):
    t = js_tokeny(kod)
    if not t:
        return kod
    n = len(t)
    par, zas = {}, []
    for k, x in enumerate(t):
        if x[0] == 'p' and x[1] in ('(', '[', '{'):
            zas.append(k)
        elif x[0] == 'p' and x[1] in (')', ']', '}'):
            if not zas or t[zas[-1]][1] != {')': '(', ']': '[', '}': '{'}[x[1]]:
                return kod                   # zátvorky nesedia — tokeny sa nedajú spoľahlivo prečítať
            par[zas.pop()] = k
    if zas:
        return kod

    def tx(k):
        return t[k][1] if 0 <= k < n and t[k][0] in ('p', 'n') else None

    def vlastnost(k):                        # meno za bodkou je vlastnosť objektu, nie premenná
        return tx(k - 1) in ('.', '?.')

    def len_cita(k):
        """Pole na tokene k (argv, pole z neho) sa tu len číta: [číslo] bez priradenia, .length, metóda na čítanie."""
        if tx(k - 1) in ('++', '--', 'delete') or (tx(k - 1) in ('.', '?.') and tx(k - 2) in ('++', '--', 'delete')):
            return False
        j = k + 1
        if tx(j) == '[':
            z = par.get(j)
            return z == j + 2 and t[j + 1][0] == 'c' and t[j + 1][1].isdigit() and tx(z + 1) not in JS_PRIRADENIE
        if tx(j) in ('.', '?.') and j + 1 < n and t[j + 1][0] == 'n':
            if t[j + 1][1] == 'length':
                return tx(j + 2) not in JS_PRIRADENIE
            return t[j + 1][1] in JS_CITA_METODY and tx(j + 2) == '('
        return False

    def viazane(meno, dekl):
        """Meno má okrem deklarácie na tokene dekl inú väzbu alebo zápis (parameter, catch, rozklad, ďalšie const/let/var,
        function/class, priradenie, ++/--, zoznam argumentov či prvkov) — vtedy sa podľa neho nevynecháva."""
        for k, x in enumerate(t):
            if x[0] != 'n' or x[1] != meno or k == dekl or vlastnost(k):
                continue
            if tx(k - 1) in ('const', 'let', 'var', 'function', 'class', 'import', '...', '++', '--') \
                    or tx(k + 1) in JS_PRIRADENIE | {'=>', 'of', 'in'}:
                return True                  # aj for (rezim of …) bez deklarácie priradí
            if tx(k - 1) in ('(', ',', '{', '[', ':') and tx(k + 1) in (')', ',', '}', ']'):
                return True
        return False

    if any(x[0] == 'n' and x[1] in ('eval', 'with', 'Function') and not vlastnost(k) for k, x in enumerate(t)):
        return kod
    for k, x in enumerate(t):
        if x[0] != 'n':
            continue
        if x[1] == 'process' and not vlastnost(k) and tx(k + 1) not in ('.', '?.'):
            return kod                       # process ako hodnota (parameter, rozklad, odovzdanie) — nedá sa overiť
        if x[1] == 'argv' and vlastnost(k) and not len_cita(k):
            return kod                       # argv sa mení alebo ide inam (push, splice, priradenie, f(process.argv))
    # pole argumentov: process.argv.slice(N), N ≥ 2 (process.argv[0] je node a [1] cesta skriptu — tie vyvolanie nedáva)
    polia = set()
    for k in range(n - 10):
        if tx(k) in ('const', 'let', 'var') and t[k + 1][0] == 'n' and tx(k + 2) == '=' and tx(k + 3) == 'process' \
                and tx(k + 4) == '.' and tx(k + 5) == 'argv' and tx(k + 6) == '.' and tx(k + 7) == 'slice' \
                and tx(k + 8) == '(' and par.get(k + 8) == k + 10 and t[k + 9][0] == 'c' and t[k + 9][1].isdigit() \
                and int(t[k + 9][1]) >= 2:
            a = t[k + 1][1]
            if not viazane(a, k + 1) and all(len_cita(j) for j, y in enumerate(t)
                                             if y[0] == 'n' and y[1] == a and j != k + 1 and not vlastnost(j)):
                polia.add(a)
    mena = set()
    for k in range(n - 6):
        if tx(k) not in ('const', 'let', 'var') or t[k + 1][0] != 'n' or tx(k + 2) != '=':
            continue
        j = k + 3
        z_argv = tx(j) == 'process' and tx(j + 1) == '.' and tx(j + 2) == 'argv'
        if z_argv:
            j += 2
        elif not (t[j][0] == 'n' and t[j][1] in polia):
            continue
        if not (tx(j + 1) == '[' and par.get(j + 1) == j + 3 and t[j + 2][0] == 'c' and t[j + 2][1].isdigit()
                and (not z_argv or int(t[j + 2][1]) >= 2)):
            continue
        j += 4
        pred = None
        if tx(j) in ('||', '??') and j + 1 < n and t[j + 1][0] == 's':
            pred = t[j + 1][1]
            if pred is None:
                continue                     # predvolená hodnota so šablónou či escape — neznáma
            j += 2
        # koniec deklarácie: ; , alebo nový riadok, za ktorým ide ďalší príkaz (inak výraz pokračuje: …[2]\n.trim())
        if j < n and tx(j) not in (';', ',') and not ('\n' in kod[t[j - 1][3]:t[j][2]] and t[j][0] == 'n'
                                                      and t[j][1] not in ('in', 'instanceof', 'of')):
            continue
        if viazane(t[k + 1][1], k + 1):
            continue
        mena.add(t[k + 1][1])
        if pred is not None:
            dane = dane | {pred}             # predvolený režim sa ráta ako daný

    def argument(a, b):
        """Tokeny t[a:b] sú argument vyvolania: meno režimu, process.argv[č] alebo pole[č]."""
        if b - a == 1:
            return t[a][0] == 'n' and t[a][1] in mena
        if b - a == 6 and tx(a) == 'process' and tx(a + 1) == '.' and tx(a + 2) == 'argv':
            return tx(a + 3) == '[' and t[a + 4][0] == 'c' and t[a + 4][1].isdigit() and int(t[a + 4][1]) >= 2 \
                and tx(a + 5) == ']'
        return b - a == 4 and t[a][0] == 'n' and t[a][1] in polia and tx(a + 1) == '[' and t[a + 2][0] == 'c' \
            and t[a + 2][1].isdigit() and tx(a + 3) == ']'

    def literal(k):
        return t[k][1] if 0 <= k < n and t[k][0] == 's' and t[k][1] is not None and '\\' not in t[k][1] else None

    vynechaj = []
    for k, x in enumerate(t):
        if x[0] != 'n' or x[1] != 'if' or vlastnost(k) or tx(k + 1) != '(':
            continue
        z = par[k + 1]
        if tx(z + 1) != '{':
            continue
        casti, a = [], k + 2
        for j in range(k + 2, z + 1):
            if j == z or tx(j) == '||':
                casti.append((a, j))
                a = j + 1
        lit = []
        for a, b in casti:
            if b - a >= 3 and tx(b - 2) in ('===', '==') and literal(b - 1) is not None and argument(a, b - 2):
                lit.append(literal(b - 1))
            elif b - a >= 3 and tx(a + 1) in ('===', '==') and literal(a) is not None and argument(a + 2, b):
                lit.append(literal(a))
            elif b - a >= 6 and tx(b - 5) == '.' and tx(b - 4) == 'includes' and tx(b - 3) == '(' \
                    and par.get(b - 3) == b - 1 and literal(b - 2) is not None \
                    and ((b - 6 == a + 2 and tx(a) == 'process' and tx(a + 1) == '.' and tx(a + 2) == 'argv'
                          and literal(b - 2).startswith('-'))
                         or (b - 6 == a and t[a][0] == 'n' and t[a][1] in polia)):
                lit.append(literal(b - 2))    # process.argv obsahuje aj node a cestu skriptu — len voľba „-…“
            else:
                lit = None
                break
        if lit is None:
            continue
        if lit and not any(v in dane for v in lit):
            vynechaj.append((t[z + 1][3], t[par[z + 1]][2]))
    for a, b in sorted(vynechaj, reverse=True):
        kod = kod[:a] + re.sub(r'[^\n]', ' ', kod[a:b]) + kod[b:]
    return kod


# Makefile a package.json (3. 10., overenie D08: make a npm run boli „mimo dosahu“): recepty cieľov make a skripty
# z package.json sa posúdia ako skript, ktorý príkaz spúšťa. Premenná make, ktorej hodnotu kotva nevie (prostredie,
# funkcia, $(shell …), priradenie v podmienke), je neznáma aj pre rozbor — vetva podľa nej sa nevynechá.
MAKE_NEZNAME = '${__make_nezname__}'
MAKE_PRIRADENIE = re.compile(r'^(?:(?:export|override|private)[ \t]+)*([A-Za-z_.][\w.-]*)[ \t]*(:::=|::=|:=|\?=|\+=|!=|=)'
                             r'[ \t]*(.*)$', re.S)
MAKE_PODMIENKA = re.compile(r'^[ \t]*(ifeq|ifneq|ifdef|ifndef|else|endif)\b')
MAKE_INCLUDE = re.compile(r'^[ \t]*(?:-include|sinclude|include)[ \t]+(.+)$')
MAKE_MAX_PRAVIDIEL = 3000        # pravidiel na jeden príkaz make; nad strop = nedočítané (zastaví)
MAKE_HLBKA = 12                  # vnorenie premenných a predpokladov
MAKE_MAX_SUBOROV = 8             # Makefile a include
NPM_VSTAVANE = {
    'yarn': {'add', 'audit', 'autoclean', 'bin', 'cache', 'check', 'config', 'constraints', 'create', 'dedupe', 'dlx',
             'exec', 'explain', 'generate-lock-entry', 'global', 'help', 'import', 'info', 'init', 'install', 'licenses',
             'link', 'list', 'login', 'logout', 'node', 'npm', 'outdated', 'owner', 'pack', 'patch', 'patch-commit', 'plugin',
             'policies', 'publish', 'rebuild', 'remove', 'run', 'search', 'set', 'stage', 'tag', 'team', 'test', 'unlink',
             'unplug', 'up', 'upgrade', 'upgrade-interactive', 'version', 'versions', 'why', 'workspace', 'workspaces'},
    'pnpm': {'add', 'audit', 'bin', 'c', 'cat-file', 'cat-index', 'config', 'create', 'dedupe', 'deploy', 'dlx', 'doctor',
             'env', 'exec', 'fetch', 'find-hash', 'get', 'help', 'i', 'import', 'init', 'install', 'install-test', 'it',
             'licenses', 'link', 'list', 'ln', 'ls', 'outdated', 'pack', 'patch', 'patch-commit', 'patch-remove', 'prune',
             'publish', 'rb', 'rebuild', 'recursive', 'remove', 'rm', 'root', 'run', 'self-update', 'server', 'set', 'setup',
             'start', 'store', 't', 'test', 'un', 'uninstall', 'unlink', 'up', 'update', 'why'},
}


def make_rozvin(text, prem, auto, hl=0):
    """Text z Makefile s $(X), ${X}, $X a $$ → text pre shell. Známa premenná sa rozvinie (aj vnorená); neznáma premenná,
    funkcia ($(shell …), $(wildcard …), $(call …)) a premenná z prostredia = MAKE_NEZNAME; $$ = $ pre shell."""
    o = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c != '$' or i + 1 >= n:
            o.append(c)
            i += 1
            continue
        d = text[i + 1]
        if d == '$':
            o.append('$')
            i += 2
            continue
        if d in '({':
            z = ')' if d == '(' else '}'
            h, j = 1, i + 2
            while j < n and h:
                if text[j] == d:
                    h += 1
                elif text[j] == z:
                    h -= 1
                j += 1
            meno = text[i + 2:j - 1].strip()
            i = j
        else:
            meno = d
            i += 2
        if meno in auto:
            o.append(auto[meno])
        elif re.fullmatch(r'[\w.-]+', meno) and prem.get(meno) is not None and hl < MAKE_HLBKA:
            o.append(make_rozvin(prem[meno], prem, auto, hl + 1))
        else:
            o.append(MAKE_NEZNAME)
    return ''.join(o)


class MakeSubor:
    """Pravidlá a premenné Makefile (aj z include). Ciele a predpoklady sa rozvinú hneď pri čítaní (ako v make), recept až
    pri behu. Priradenie v podmienke (ifeq … endif), cez != a len pre cieľ je neznáme; pravidlá z oboch vetiev podmienky
    sa čítajú. nacitaj(cesta) → obsah include súboru alebo None."""

    def __init__(self, obs, prem, cmd_prem, nacitaj, adr):
        self.pravidla = []           # {'ciele': [..], 'predpoklady': [..], 'recept': [(riadok, plus)]}
        self.prem = dict(prem)       # meno → text (None = neznáme)
        self.cmd_prem = dict(cmd_prem)
        self.prem.update(cmd_prem)
        self.oneshell = False
        self.prvy = None
        self.nacitaj = nacitaj
        self.adr = adr
        self.suborov = 1
        self.citaj(obs, 0)
        if self.prem.get('.DEFAULT_GOAL'):
            self.prvy = make_rozvin(self.prem['.DEFAULT_GOAL'], self.prem, {}).strip() or self.prvy

    def priradenie(self, meno, op, hod, nezname):
        if meno in self.cmd_prem:
            return                   # premenná z príkazového riadka má prednosť
        if nezname or op == '!=':
            self.prem[meno] = None
        elif op == '?=':
            if meno not in self.prem:
                self.prem[meno] = hod
        elif op == '+=':
            stara = self.prem.get(meno, '')
            self.prem[meno] = None if stara is None else (stara + ' ' + hod).strip()
        else:
            self.prem[meno] = hod

    def citaj(self, obs, hl):
        riadky = re.sub(r'\\\r?\n[ \t]*', ' ', obs).split('\n')
        cur = None
        podm = 0
        i = 0
        while i < len(riadky):
            r = riadky[i].rstrip('\r')
            i += 1
            if r.startswith('\t') and cur is not None:
                obsah = r[1:]
                pred = re.match(r'[ \t@+-]*', obsah).group()
                if obsah.strip():
                    cur['recept'].append((obsah[len(pred):], '+' in pred))
                continue
            bez = re.sub(r'(?<!\\)#.*$', '', r)
            if not bez.strip():
                continue
            m = re.match(r'^[ \t]*define[ \t]+([A-Za-z_.][\w.-]*)', bez)
            if m:
                telo = []
                while i < len(riadky) and not re.match(r'^[ \t]*endef\b', riadky[i]):
                    telo.append(riadky[i])
                    i += 1
                i += 1
                self.priradenie(m.group(1), '=', '\n'.join(telo), podm > 0)
                cur = None
                continue
            m = MAKE_PODMIENKA.match(bez)
            if m:
                if m.group(1).startswith('if'):
                    podm += 1
                elif m.group(1) == 'endif':
                    podm = max(0, podm - 1)
                continue
            m = MAKE_INCLUDE.match(bez)
            if m:
                cur = None
                for nz in make_rozvin(m.group(1), self.prem, {}).split():
                    if MAKE_NEZNAME in nz or hl >= 3 or self.suborov >= MAKE_MAX_SUBOROV:
                        continue
                    o = self.nacitaj(nz if nz.startswith('/') else os.path.join(self.adr, nz))
                    if o is not None:
                        self.suborov += 1
                        self.citaj(o, hl + 1)
                continue
            m = MAKE_PRIRADENIE.match(bez.strip())
            if m:
                self.priradenie(m.group(1), m.group(2), m.group(3).strip(), podm > 0)
                cur = None
                continue
            k = re.search(r':(?!=)', bez)
            if not k or not bez[:k.start()].strip():
                cur = None
                continue
            zvysok = bez[k.end():]
            if zvysok.startswith(':'):
                zvysok = zvysok[1:]              # pravidlo s dvoma dvojbodkami: každé má svoj recept
            recept = None
            if ';' in zvysok:
                zvysok, recept = zvysok.split(';', 1)
            m2 = MAKE_PRIRADENIE.match(zvysok.strip())
            if m2:                               # cieľ: PREMENNÁ = hodnota (len pre ten cieľ) — hodnota neznáma
                self.priradenie(m2.group(1), m2.group(2), m2.group(3).strip(), True)
                cur = None
                continue
            ciele = make_rozvin(bez[:k.start()], self.prem, {}).split()
            cur = {'ciele': ciele, 'predpoklady': make_rozvin(zvysok.replace('|', ' '), self.prem, {}).split(),
                   'recept': []}
            if recept is not None and recept.strip():
                pred = re.match(r'[ \t@+-]*', recept).group()
                cur['recept'].append((recept[len(pred):], '+' in pred))
            self.pravidla.append(cur)
            if '.ONESHELL' in ciele:
                self.oneshell = True
            if self.prvy is None:
                for c in ciele:
                    if not c.startswith('.') and '%' not in c and MAKE_NEZNAME not in c:
                        self.prvy = c
                        break

    def pravidla_ciela(self, meno):
        """Pravidlá cieľa → [(pravidlo, kmeň pre $*)]; bez explicitného pravidla prvé vzorové (%.x: …), ktoré sedí."""
        von = [(r, '') for r in self.pravidla if meno in r['ciele']]
        if von:
            return von
        for r in self.pravidla:
            for c in r['ciele']:
                if '%' in c:
                    pred, _, za = c.partition('%')
                    if meno.startswith(pred) and meno.endswith(za) and len(meno) > len(pred) + len(za):
                        return [(r, meno[len(pred):len(meno) - len(za)])]
        return []

    def recepty(self, ciele, nasucho):
        """Text receptov cieľov a ich predpokladov (predpoklady skôr) pre shell; každý riadok receptu vo vlastnom podshelli
        (make ho spúšťa samostatne), pri .ONESHELL celý recept v jednom. nasucho (-n): len riadky s + a $(MAKE).
        ciele None = neznámy cieľ (premenná) — všetky ciele. Nad strop pravidiel → ValueError (nedočítané)."""
        von, videne, pocet = [], set(), [0]

        def ciel(meno, hl):
            if meno in videne or hl > MAKE_HLBKA:
                return
            videne.add(meno)
            for r, kmen in self.pravidla_ciela(meno):
                pocet[0] += 1
                if pocet[0] > MAKE_MAX_PRAVIDIEL:
                    raise ValueError('strop pravidiel make')
                pred = [p.replace('%', kmen) for p in r['predpoklady']]
                for p in pred:
                    if MAKE_NEZNAME not in p:
                        ciel(p, hl + 1)
                auto = {'@': meno, '<': pred[0] if pred else '', '^': ' '.join(dict.fromkeys(pred)), '+': ' '.join(pred),
                        '*': kmen or MAKE_NEZNAME, '?': MAKE_NEZNAME, '@D': os.path.dirname(meno) or '.',
                        '@F': os.path.basename(meno)}
                riadky = []
                for txt, plus in r['recept']:
                    if nasucho and not plus and not re.search(r'\$[({]MAKE[)}]', txt):
                        continue
                    if nasucho:                  # -n ide cez MAKEFLAGS aj do vnoreného make
                        txt = re.sub(r'\$([({])MAKE([)}])', r'$\1MAKE\2 -n', txt)
                    riadky.append(make_rozvin(txt, self.prem, auto))
                if riadky:
                    von.append('(\n' + '\n'.join(riadky) + '\n)' if self.oneshell
                               else '\n'.join('(\n' + x + '\n)' for x in riadky))

        if ciele is None:
            for r in self.pravidla:
                for c in r['ciele']:
                    if not c.startswith('.') and '%' not in c:
                        ciel(c, 0)
        else:
            for c in ciele or ([self.prvy] if self.prvy else []):
                ciel(c, 0)
        return '\n'.join(von)


def npm_skripty(obs):
    """package.json → {meno: text skriptu}, alebo None."""
    try:
        d = json.loads(obs)
    except ValueError:
        return None
    s = d.get('scripts') if isinstance(d, dict) else None
    if not isinstance(s, dict):
        return None
    return {k: v for k, v in s.items() if isinstance(k, str) and isinstance(v, str)}


def skript_rezimy(obs, jaz, tokeny):
    """Kód skriptu pred posúdením: node bez vetiev režimov, ktoré vyvolanie nevybralo (js_rezimy, vyššie); python bez
    funkcií, ktoré nikto nevolá (py_dosiahnutelne); shell nezmenený. Vetvy podľa argumentov vyvolania v shelli a pythone
    vynecháva len abstraktný beh (sh_volby, py_volby) — 3. 10., overenie kola 1: skratka podľa regulárnych výrazov brala
    argument funkcie ($1 vo funkcii, parameter argv) za argument skriptu. tokeny = argumenty vyvolania."""
    if jaz == 'js':
        return js_rezimy(obs, tokeny)
    if jaz == 'py':
        return py_dosiahnutelne(obs)
    return obs


def py_triedy_ako_hodnota(text, mena):
    """K88YA: mená tried z `mena`, ktoré text (bez reťazcov) použije ako hodnotu — nie volanie T(…), nie T.x, nie hlavička
    triedy (class T, class X(T)) ani riadok importu. Hodnotu (HTTPServer(…, T), [T], f(T), x = T) môže dostať rámec a volať
    jej metódy menom, ktoré v kóde nie je."""
    if not mena:
        return set()
    t = re.sub(r'\bfrom[ \t]+[\w.]+[ \t]+import[ \t]*\([^)]*\)', '', text)
    t = re.sub(r'(?m)^[ \t]*(?:from[ \t]+[\w.]+[ \t]+)?import\b[^\n]*|^[ \t]*class\b[^\n]*', '', t)
    return set(re.findall(r'(?<!\w)(' + '|'.join(map(re.escape, sorted(mena))) + r')\b(?![ \t]*[(.])', t)) \
        | py_instancie_ako_hodnota(t, mena)


def py_instancie_ako_hodnota(t, mena):
    """K88YB: mená tried z `mena`, ktorých inštanciu T(…) text `t` (bez reťazcov, riadkov importu a hlavičiek tried)
    použije ako hodnotu — argument volania (logging.getLogger().addHandler(m.H())), prvok zoznamu (handlers=[m.H()]),
    return, with — alebo ju priradí do premennej, ktorú potom použije ako hodnotu (h = m.H(); addHandler(h)). Rámec volá
    jej metódy menom, ktoré v kóde nie je (emit). Hodnotou nie je: metóda priamo na inštancii (T().f()), samotný príkaz
    T(…) a premenná, na ktorú kód siaha len cez bodku (x = T(); x.f() — odvodená trieda v skúškach admin-api ostáva zúžená)."""
    von = set()
    for m in re.finditer(r'(?<!\w)(' + '|'.join(map(re.escape, sorted(mena))) + r')[ \t]*\(', t):
        if m.group(1) in von:
            continue
        k, h = m.end(), 1
        while k < len(t) and h:
            h += {'(': 1, ')': -1}.get(t[k], 0)
            k += 1
        if not h and re.match(r'[ \t]*[.(\[]', t[k:k + 40]):
            continue                                        # T(…).f(), T(…)(…), T(…)[…]
        zac = t.rfind('\n', 0, m.start()) + 1
        pred = re.sub(r'(?:[A-Za-z_]\w*[ \t]*\.[ \t]*)+$', '', t[zac:m.start()])      # m.H( → H(
        if not pred.strip():
            if t[:zac].rstrip()[-1:] not in tuple('([{,=:\\'):
                continue                                    # samotný príkaz T(…) na začiatku riadku
            von.add(m.group(1))                             # pokračovanie argumentov z predošlého riadku
            continue
        pr = re.fullmatch(r'[ \t]*([A-Za-z_][\w.]*)[ \t]*(?::[^=\n]*)?=[ \t]*', pred)
        if pr:
            x = pr.group(1).split('.')[-1]
            if re.search(r'(?<!\w)' + re.escape(x) + r'\b(?![ \t]*(?:[.(\[]|=(?!=)))', t):
                von.add(m.group(1))                         # premenná s inštanciou použitá ako hodnota
            continue
        von.add(m.group(1))
    return von


def py_zaklady_triedy(hlavicka):
    """K88YA: mená základov z hlavičky triedy `class X(a.B, C[T], metaclass=M)` → {B, C}."""
    m = re.match(r'[ \t]*class[ \t]+\w+[ \t]*\(([^)]*)', hlavicka)
    von = set()
    for x in (m.group(1).split(',') if m else ()):
        x = re.sub(r'\[.*', '', x)
        if '=' not in x and re.findall(r'[A-Za-z_]\w*', x):
            von.add(re.findall(r'[A-Za-z_]\w*', x)[-1])
    return von


def py_dosiahnutelne(kod, korene=(), modul=False, triedy=frozenset()):
    """Python: kód bez funkcií, ktoré po vynechaní vetiev režimov nikto nevolá (cmd_napoj pri `napoj-spinoff.py skontroluj`).
    Funkcia na najvyššej úrovni ostáva, keď je jej meno v kóde mimo nej alebo v inej ponechanej funkcii (aj ako hodnota
    v slovníku príkazov), keď má dekorátor (registruje ju rámec) a vždy, keď kód volá funkcie podľa mena za behu
    (globals()[…], getattr(sys.modules…), locals()[…]) — vtedy sa nevynecháva nič."""
    # oprava 2. 10. večer: aj globals().items(), getattr(modul, 'cmd_' + x), __dict__ — funkciu volanú podľa mena v reťazci
    # rozbor nevidí, jej telo sa preto nevynecháva. 3. 10. (kolo 2): aj fire.Fire() bez argumentu (spustí ktorúkoľvek funkciu
    # modulu podľa slova vyvolania), inspect, sys._getframe, __main__, import_module, dir() — hľadá sa mimo reťazcov
    # meno v reťazci nie je volanie: `if a[0] == 'zaloz': …` nevolá funkciu zaloz (podpríkaz sa často volá ako funkcia)
    bez_ret = lambda s: re.sub(r'''(['"])(?:\\.|(?!\1)[^\\\n])*\1''', '""', s)
    b = bez_ret(py_eval_uzavrety(kod))      # SKG 3 (b7): eval s uzavretým menným priestorom nevolá funkcie podľa mena
    if modul:
        # 14. 10. (K88X): lokálny modul, ktorý načíta iné moduly s menom doslova (import_module('ng-stroj')), tým svoje
        # funkcie podľa mena nevolá — inak by sa z veľkých modulov nevynechávalo nič (ng-zapisnik.py 83 funkcií)
        b = re.sub(r'\b(?:import_module|__import__)\s*\(\s*""\s*[,)]', '', b)
    if re.search(r'\b(?:globals|locals|vars|dir)\s*\(|__dict__|\bsys\.modules\b|\b(?:eval|exec)\s*\(|\bFire\s*\('
                 r'|\binspect\b|\b_getframe\b|\b__main__\b|\bimport_module\b|\b__import__\b', b) \
            or re.search(r'\bgetattr\s*\(\s*[^,()]+,\s*(?=[^\s\'"])', kod):
        return kod
    getattr_mena = set(re.findall(r'\bgetattr\s*\(\s*[^,()]+,\s*[\'"](\w+)[\'"]', kod))
    riadky = kod.split('\n')
    bloky = {}                                      # meno → (od, do) riadkov
    i = 0
    trieda = None                                   # odsadenie tela triedy na najvyššej úrovni (len modul)
    metody, zaklady = {}, {}                        # K88YA: trieda → mená jej metód (bloky) · mená jej základov
    while i < len(riadky):
        mt = modul and re.match(r'class[ \t]+(\w+)', riadky[i])
        if mt:
            trieda, meno_triedy = '', mt.group(1)
            metody.setdefault(meno_triedy, [])
            zaklady.setdefault(meno_triedy, set()).update(py_zaklady_triedy(riadky[i]))
            i += 1
            continue
        if modul and trieda is not None and riadky[i].strip() and riadky[i][0] not in ' \t':
            trieda = None
        if modul and trieda is not None:
            mm = re.match(r'([ \t]+)(?:async[ \t]+)?def[ \t]+(\w+)[ \t]*\(', riadky[i])
            if mm and (trieda == '' or mm.group(1) == trieda):
                trieda = mm.group(1)
                if not riadky[i - 1].lstrip().startswith('@') and not re.fullmatch(r'__\w+__', mm.group(2)):
                    j = i + 1
                    while j < len(riadky) and (not riadky[j].strip() or riadky[j].startswith(trieda + ' ')
                                               or riadky[j].startswith(trieda + '\t') or riadky[j].lstrip()[:1] in ')]}'):
                        j += 1
                    bloky.setdefault(mm.group(2), (i, j))
                    if bloky[mm.group(2)] != (i, j):
                        return kod                  # dve metódy s rovnakým menom v rôznych triedach — opatrne celé
                    metody[meno_triedy].append(mm.group(2))
                    i = j
                    continue
            i += 1
            continue
        m = re.match(r'(?:async[ \t]+)?def[ \t]+(\w+)[ \t]*\(', riadky[i])
        if m and not (i and riadky[i - 1].startswith('@')):
            j = i + 1
            while j < len(riadky) and (not riadky[j].strip() or riadky[j][0] in ' \t)'):
                j += 1
            bloky[m.group(1)] = (i, j)
            i = j
            continue
        i += 1
    if not bloky:
        return kod
    v_bloku = set()
    for a, b in bloky.values():
        v_bloku.update(range(a, b))
    mimo = bez_ret('\n'.join(r for k, r in enumerate(riadky) if k not in v_bloku))
    dosiahnute, front = set(), [x for x in bloky if re.search(r'\b' + re.escape(x) + r'\b', mimo) or x in getattr_mena
                                or x in korene]
    hodnoty = set()                                 # K88YA: triedy použité ako hodnota (aj cez odvodenú) — všetky metódy

    def cele_triedy(mena):
        von, zas = [], list(mena)
        while zas:
            t = zas.pop()
            if t in metody and t not in hodnoty:
                hodnoty.add(t)
                von += metody[t]
                zas += zaklady[t]
        return von
    if metody:
        front += cele_triedy(set(triedy) | py_triedy_ako_hodnota(mimo, metody))
    while front:
        x = front.pop()
        if x in dosiahnute:
            continue
        dosiahnute.add(x)
        a, b = bloky[x]
        telo = bez_ret('\n'.join(riadky[a + 1:b]))
        front += [y for y in bloky if y not in dosiahnute and re.search(r'\b' + re.escape(y) + r'\b', telo)]
        if metody:
            front += cele_triedy(py_triedy_ako_hodnota(telo, metody))
    for x, (a, b) in bloky.items():
        if x not in dosiahnute:
            for k in range(a, b):
                riadky[k] = ''
    return '\n'.join(riadky)


# 14. 10. (K88X): lokálny modul, ktorý python načíta (py_moduly, D2), sa posudzoval celý — aj funkcie, ktoré nikto nevolá,
# a telo `if __name__ == '__main__':`, ktoré sa pri načítaní nespustí. `python3 ng-stav.py` si tak naložil skripty z hlavných
# častí ôsmich modulov a rozbor trval 5,2 s namiesto 2,1 s (strop 3,8 s — stráž by ho zastavila ako nedočítaný). Teraz sa
# z modulu posúdi kód na najvyššej úrovni bez tela hlavnej časti a funkcie, na ktoré načítajúci môže siahnuť (meno za bodkou,
# v `from m import f`, getattr(m, 'f'); pri `from m import *` ktorékoľvek slovo jeho kódu), a funkcie, ktoré tie volajú.
# Hranica: modul odovzdaný ako hodnota inému modulu (b.pouzi(a), kde b volá a.f) — meno f je v b, nie v načítajúcom.
# Celý modul ostáva, keď načítajúci siaha na mená za behu (getattr s menom z premennej, vars, dir, __dict__, eval, exec,
# inspect, attrgetter, methodcaller, Fire; globals, locals a sys.modules len pri `from m import *`, lebo inak v nich funkcie
# modulu nie sú) — vtedy môže zavolať ktorúkoľvek funkciu. Hlavná časť ostáva, keď ju načítajúci môže spustiť (run_path, run_module, meno '__main__'
# ako argument) alebo za ňou nasleduje else/elif na najvyššej úrovni (to sa pri načítaní spustí).
PY_MOD_DYNAMIKA = re.compile(r'\b(?:vars|dir)\s*\(|__dict__|\b(?:eval|exec)\s*\(|\bFire\s*\(|\binspect\b|\b_getframe\b'
                             r'|\b(?:attrgetter|methodcaller)\b|\bgetattr\s*\(\s*[^,()]+,\s*(?=[^\s\'"])')
PY_MOD_DYNAMIKA_HVIEZDA = re.compile(r'\b(?:globals|locals)\s*\(|\bsys\.modules\b')
PY_MOD_HVIEZDA = re.compile(r'^[ \t]*from[ \t]+[\w.]+[ \t]+import[ \t]*\*', re.M)
PY_MOD_HLAVNA = re.compile(r'''if[ \t]*\(?[ \t]*(?:__name__[ \t]*==[ \t]*(['"])__main__\1|(['"])__main__\2[ \t]*==[ \t]*__name__)'''
                           r'''[ \t]*\)?[ \t]*:''')
PY_MOD_SPUSTI_HLAVNU = re.compile(r'''\brun_path\b|\brun_module\b|['"]__main__['"][ \t]*[,)]''')


# SKG 3 (b9, 9. 11.): py_modul_rozsah sa volá pre každý pár modul–načítajúci a ten istý načítajúci načíta viac modulov
# (pri `python3 .aigrip/scripts/ng-stav.py` 39 párov, 21 načítajúcich, 18 modulov). Všetko, čo funkcia číta len
# z načítajúceho — text s uzavretým eval a ten bez reťazcov, PY_MOD_DYNAMIKA v ňom, `from … import *` s globals()/
# locals()/sys.modules, spúšťanie __main__, vlastné triedy a ich základy, mená, ktorými siaha na modul — počítala znova
# pri každom module (0,58 s, z toho 0,29 s opakovane nad tým istým textom). Teraz raz pre text načítajúceho
# (_py_mod_nacitajuci pred skorým návratom, _py_mod_nacitajuci_zvysok po ňom); sú to tie isté čisté výpočty nad tým istým
# textom, výsledok sa nemení. Slovník tried a množiny v ňom sa len čítajú. Pamäť (_pamat) im pribalí blok pri
# skript_kod nižšie — _pamat je definovaná až za touto funkciou.
_PY_BEZ_RET = re.compile(r'''(['"])(?:\\.|(?!\1)[^\\\n])*\1''')


def _py_mod_nacitajuci(nacitajuci):
    """py_modul_rozsah (SKG 3 b9, vyššie): (text s uzavretým eval, ten bez reťazcov, PY_MOD_DYNAMIKA v ňom, `from … import
    *` s globals()/locals()/sys.modules)."""
    d = py_eval_uzavrety(nacitajuci)            # SKG 3 (b7): eval s uzavretým menným priestorom — vyššie
    b = _PY_BEZ_RET.sub('""', d)
    return d, b, bool(PY_MOD_DYNAMIKA.search(b)), bool(PY_MOD_HVIEZDA.search(nacitajuci) and PY_MOD_DYNAMIKA_HVIEZDA.search(b))


def _py_mod_nacitajuci_zvysok(nacitajuci):
    """py_modul_rozsah (SKG 3 b9, vyššie): (spúšťa __main__ modulu, vlastné triedy → ich základy, mená, ktorými siaha na
    modul)."""
    b = _py_mod_nacitajuci(nacitajuci)[1]
    return (bool(PY_MOD_SPUSTI_HLAVNU.search(nacitajuci)),
            {m.group(1): py_zaklady_triedy(m.group(0)) for m in re.finditer(r'(?m)^[ \t]*class[ \t]+(\w+)[^\n]*', b)},
            frozenset(py_mena_z_modulu(nacitajuci)))


def py_modul_rozsah(kod, nacitajuci, meno=None):
    """Kód lokálneho modulu, ktorý sa spustí, keď ho načíta kód `nacitajuci` (K88X, vyššie); riadky, ktoré sa nespustia,
    sú prázdne (čísla riadkov ostávajú). `meno` = meno modulu podľa súboru (SKG: iný modul v načítajúcom, None = nevedno)."""
    nacitajuci_d, b, dyn, hviezda = _py_mod_nacitajuci(nacitajuci)     # SKG 3 (b9): raz pre načítajúceho — vyššie
    # SKG 3 (b6, 8. 11.): py_dyn_staticka len premenuje getattr/setattr (rovnaká dĺžka, len písmená) — nájde teda nanajvýš
    # to, čo PY_MOD_DYNAMIKA nájde v pôvodnom texte; keď tam nenájde nič, rozbor stromom (0,05 s pri 104 kB) netreba
    if (dyn and PY_MOD_DYNAMIKA.search(_PY_BEZ_RET.sub('""', py_dyn_staticka(nacitajuci_d, kod)))) or hviezda:
        return kod              # SKG: getattr so známym menom, ktoré modul nemá, mená modulu za behu nečíta
    spusti, vlastne, mena = _py_mod_nacitajuci_zvysok(nacitajuci)
    riadky = kod.split('\n')
    if not spusti:
        i = 0
        while i < len(riadky):
            if not PY_MOD_HLAVNA.match(riadky[i]):
                i += 1
                continue
            j = i + 1
            while j < len(riadky) and (not riadky[j].strip() or riadky[j][0] in ' \t)]}' or riadky[j].startswith('#')):
                j += 1
            if not (j < len(riadky) and re.match(r'(?:elif|else)\b', riadky[j])):
                for k in range(i, j):
                    riadky[k] = ''
            i = j
    # K88YA: triedy, ktoré načítajúci použije ako hodnotu — aj cez svoju triedu odvodenú z triedy modulu (class F(m.H) a
    # HTTPServer(…, F)); odvodená trieda, na ktorej len volá metódy menom (class Fake(m.Handler) v testoch), hodnotou nie je
    mod_triedy = set(re.findall(r'(?m)^class[ \t]+(\w+)', kod))
    triedy, zas = set(), list(py_triedy_ako_hodnota(b, mod_triedy | set(vlastne)))
    while zas:
        t = zas.pop()
        if t not in triedy:
            triedy.add(t)
            zas += vlastne.get(t, ())
    return py_nahrada_siete(py_dosiahnutelne('\n'.join(riadky), mena, True, frozenset(triedy)), nacitajuci, kod, meno)


def py_mena_z_modulu(kod):
    """Mená, ktorými kód môže siahnuť na funkciu modulu: za bodkou (m.f, aj modul v premennej), v `from m import f, g`,
    getattr(m, 'f'); pri `from m import *` každé slovo kódu."""
    if PY_MOD_HVIEZDA.search(kod):
        return set(re.findall(r'[A-Za-z_]\w*', kod))
    # K88YA: meno len ako cieľ priradenia (m.f = lambda …: náhrada v skúške) pôvodnú funkciu nespustí — modul volá náhradu;
    # čítanie (m.f(), x = m.f, m.f == …) ju spustiť môže. Funkcia, ktorú volá iná dosiahnutá funkcia modulu, ostáva.
    mena = set(re.findall(r'\.[ \t]*([A-Za-z_]\w*)\b(?![ \t]*=(?!=))', kod))
    mena.update(re.findall(r'\bgetattr\s*\(\s*[^,()]+,\s*[\'"](\w+)[\'"]', kod))
    for m in re.finditer(r'\bfrom[ \t]+[\w.]+[ \t]+import[ \t]*(\([^)]*\)|[^\n;]*)', kod):
        for x in m.group(1).strip('() \t').split(','):
            x = x.split()
            if x:
                mena.add(x[0])
    return mena


PY_IMPORT_RIADOK = re.compile(r'(?:^|[;:])[ \t]*(?:from[ \t]+([\w.]+)[ \t]+import[ \t]*(\([^)]*\)|[^\n;]*)|import[ \t]+([^\n;]*))',
                              re.M)


def _pamat(f):
    """K88X: pamäť čistej funkcie nad textom v jednom rozbore (ako functools.lru_cache, strop 512 výsledkov; plná sa
    vyprázdni) — bez importu, ktorý živá stráž nemá (posudok vlastnej kópie by upravenú stráž nepustil)."""
    pamat = {}

    def obal(*a, **k):
        kluc = (a, tuple(sorted(k.items()))) if k else a
        if kluc in pamat:
            return pamat[kluc]
        if len(pamat) >= 512:
            pamat.clear()
        v = pamat[kluc] = f(*a, **k)
        return v
    return obal


# SKG 3 (b6, 8. 11.): ten istý veľký text (skript s celým textom — ng-cervene-testy.py, 1,19 MB) čítajú stromom dosah
# spúšťača (dosah_text) aj getattr/setattr načítajúceho (_py_dyn_miesta), každý 0,2 s. Strom sa pre text od
# PY_STROM_OD znakov pamätá (najviac dva, 40 MB pri 1,19 MB); obaja ho len čítajú. Chyba ide ďalej ako z ast.parse.
PY_STROM_OD = 200000
_PY_STROM = {}


def py_strom(kod):
    """ast.parse s pamäťou pre veľký text (SKG 3 b6, vyššie) — strom sa nesmie meniť."""
    if len(kod) < PY_STROM_OD:
        return ast.parse(kod)
    s = _PY_STROM.get(kod)
    if s is None:
        s = ast.parse(kod)
        if len(_PY_STROM) >= 2:
            _PY_STROM.clear()
        _PY_STROM[kod] = s
    return s


# SKG 3 (b7, 8. 11.): ng-sluby.py číta DATUMY zo zdroja ng-stav.py cez `eval(compile(ast.Expression(n.value), 'DATUMY',
# 'eval'), {'__builtins__': {}, 'datetime': datetime})`. Pravidlo „kód s eval/exec môže volať funkcie podľa mena“ preto
# nechalo z ng-sluby.py všetkých 192 funkcií a z ng-prechod.py, ktorý ng-sluby.py načíta, celý modul vrátane main() — a ten
# spúšťa ng-cervene-testy.py, takže rozbor niesol celý text súboru červených testov (príkaz ng-caka-drzi.py s plnou výmenou
# vzoru popisov 5,6–5,9 s pri strope 5,5 s; bez toho 1,5 s). Takýto eval sa už neráta: dve argumenty, menný priestor je
# doslovný slovník s prázdnym '__builtins__' a ostatné hodnoty sú mená, ktoré kód viaže len príkazom `import` modulu
# z PY_EVAL_MODULY (datetime, math, time — overené 8. 11. na pythone servera: okrem atribútov s dvojitým podčiarkovníkom
# nenesú iný modul ani meniteľný objekt) a ktoré kód nepoužíva inak než ako `modul.meno` na čítanie (nie setattr, priradenie
# do atribútu, odovzdanie inam); eval/exec nie sú premenované. Mená funkcií modulu v takom mennom priestore nie sú; dostať sa
# k nim dá len únikom cez vnútorné atribúty objektov (__subclasses__, __globals__ …) — keď je taký text v kóde viditeľný
# (reťazec s menom v dvojitom podčiarkovníku okrem samotných '__builtins__' a '__main__', alebo rámec f_back …), platí staré
# pravidlo. Text zo súboru kotva nevidí ani dnes a ten vie to isté urobiť aj sám, takže zúženie novú cestu nepridá. Dosah
# (B30): každý, koho príkazy kotva posudzuje (PS, kolegovia v projektoch so strojom, podagenti) — skript s takým eval;
# internet, webový server, služba správcu ani proces mimo izby k tomu cestu nemajú (kotva rozhoduje len o príkazoch sedenia).
PY_EVAL_MODULY = frozenset(('datetime', 'math', 'time'))
PY_EVAL_UNIK = re.compile(r'__[A-Za-z]\w*__|\b(?:gi|cr|ag|tb|f)_(?:frame|back|globals|locals|code)\b|\b_getframe\b')


@_pamat
def py_eval_uzavrety(kod):
    """Text kódu s eval/exec s uzavretým menným priestorom (SKG 3 b7, vyššie) premenovanými na rovnako dlhé xval/xxec — na
    posúdenie, či kód volá funkcie podľa mena. Nič také, nečitateľný kód alebo viditeľný únik → text bez zmeny."""
    if '__builtins__' not in kod or not re.search(r'\b(?:eval|exec)\s*\(', kod):
        return kod
    try:
        strom = py_strom(kod)
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        return kod
    viazane, ine, kandidati = set(), set(), []      # mená viazané `import datetime` … · mená viazané inak · eval/exec
    for u in ast.walk(strom):
        if isinstance(u, ast.Constant) and isinstance(u.value, (str, bytes)):
            t = u.value if isinstance(u.value, str) else u.value.decode('latin-1')
            if t not in ('__builtins__', '__main__') and PY_EVAL_UNIK.search(t):
                return kod
        elif isinstance(u, ast.Import):
            for a in u.names:
                if a.name in PY_EVAL_MODULY:
                    viazane.add(a.asname or a.name)
                else:
                    ine.add(a.asname or a.name.split('.')[0])
        elif isinstance(u, ast.ImportFrom):
            ine.update(a.asname or a.name for a in u.names)
        elif isinstance(u, ast.Name) and not isinstance(u.ctx, ast.Load):
            ine.add(u.id)
        elif isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            ine.add(u.name)
            if not isinstance(u, ast.ClassDef):
                ine.update(x.arg for x in ast.walk(u.args) if isinstance(x, ast.arg))
        elif isinstance(u, (ast.Global, ast.Nonlocal)):
            ine.update(u.names)
        elif isinstance(u, ast.Call) and isinstance(u.func, ast.Name) and u.func.id in ('eval', 'exec'):
            kandidati.append(u)
        elif isinstance(u, (ast.MatchAs, ast.MatchStar)) and u.name:
            ine.add(u.name)
        elif isinstance(u, ast.MatchMapping) and u.rest:
            ine.add(u.rest)
        elif isinstance(u, ast.ExceptHandler) and u.name:
            ine.add(u.name)
    viazane -= ine
    if not kandidati or not viazane:
        return kod
    hodnoty = {id(v) for u in kandidati if len(u.args) == 2 and isinstance(u.args[1], ast.Dict) for v in u.args[1].values}
    for u in ast.walk(strom):
        for ch in ast.iter_child_nodes(u):
            if isinstance(ch, ast.Name) and ch.id in viazane and id(ch) not in hodnoty and not (
                    isinstance(u, ast.Attribute) and u.value is ch and isinstance(u.ctx, ast.Load)
                    and not u.attr.startswith('__')):
                viazane.discard(ch.id)          # modul použitý inak než na čítanie `modul.meno`
    riadky = kod.split('\n')
    zmeny = []
    for u in kandidati:
        if len(u.args) != 2 or u.keywords or not isinstance(u.args[1], ast.Dict) or u.func.id in ine:
            continue
        d = u.args[1]
        if not all(isinstance(k, ast.Constant) and isinstance(k.value, str) for k in d.keys):
            continue                            # None = rozbalenie **x
        kl = [k.value for k in d.keys]
        if kl.count('__builtins__') != 1 or len(set(kl)) != len(kl):
            continue
        if not all((isinstance(v, ast.Dict) and not v.keys) if k == '__builtins__' else
                   (isinstance(v, ast.Name) and v.id in viazane) for k, v in zip(kl, d.values)):
            continue
        f = u.func
        r = riadky[f.lineno - 1] if 1 <= f.lineno <= len(riadky) else ''
        c = len(r.encode('utf-8')[:f.col_offset].decode('utf-8', 'replace'))
        if r[c:c + 4] != f.id:
            return kod                          # poloha nesedí s textom — nemení sa nič
        zmeny.append((f.lineno - 1, c, 'xval' if f.id == 'eval' else 'xxec'))
    for i, c, n in zmeny:
        riadky[i] = riadky[i][:c] + n + riadky[i][c + 4:]
    return '\n'.join(riadky) if zmeny else kod


# SKG 2 (6. 11.; mlacena, aspecta-finops): skript_kod mazal popisy pythonu vzorom „trojité úvodzovky na začiatku riadku až
# po najbližšie trojité“ — zatváracie úvodzovky viacriadkového reťazca na začiatku riadku (vzorka faktúry v test_logika.py:
# `TEXT = """…` a o riadky nižšie `"""`) vzal za otváracie a zmazal kód až po ďalší popis, z ktorého potom ostal text ako
# kód. Text testu nebol platný python, náhrada siete (KMP 2) a vynechanie nespusteného kódu (K88X) sa potichu vzdali a
# stráž 5. 11. zastavila 12 behov jeho testov ako zápis do TD Synnex (ion.py), hoci test adresu presmeruje na vlastný
# počítač. Teraz sa páry určia stromom kódu: na None sa zmení ten istý druh reťazca — v trojitých úvodzovkách (predpona
# r/u/b/br ako vo vzore), ktorý začína riadok, mimo f-reťazca — a nič iné. Kód, ktorý sa nedá prečítať stromom, alebo
# nesúlad polohy s textom → None (skript_kod použije vzor ako doteraz). Popis, ktorý vzor zmazal správne, sa zmaže rovnako;
# líši sa len kód medzi zle spárovanými úvodzovkami — po novom ho rozbor vidí (čo vzor zmazal) a text reťazca nie.
PY_POPIS_ZACIATOK = re.compile(r'''(?:[rRuU]|[bB][rR]?)?("""|\'\'\')''')
PY_POPIS_RIADOK = re.compile(r'''(?m)^[ \t]*(?:[rRuU]|[bB][rR]?)?(?:"""|\'\'\')''')


# SKG 3 (b3, 7. 11.): páry úvodzoviek určuje tokenizér pythonu, strom len keď tokenizér nevie. Výsledok je rovnaký
# (8 130 textov — súbory pythonu projektov PS, skripty stroja a 87 textov, ktoré rozbor číta pri ng-caka-drzi.py: 0
# rozdielov) za menej ako polovicu času (tých 87 textov 0,5 s namiesto 1,2 s; samotné čítanie stromu bolo 0,93 s).
# Tokenizér prečíta aj kód písaný pre novší python, ktorý strom servera neprečíta: equalpay data_retention.py (python
# 3.15, `except TypeError, ValueError:`) — strom vrátil None a vzor tam skryl kód medzi zle spárovanými úvodzovkami (celú
# funkciu _filter_guard); po novom ho rozbor vidí. Susedné reťazce, ktoré python spojí do jedného (aj cez riadky
# v zátvorke a cez komentár), sú jedna skupina ako uzol stromu; skupina s f-reťazcom ostáva (v strome časť JoinedStr),
# reťazce vnútri výrazu f-reťazca sa posudzujú samostatne ako v strome. Python pred 3.12 (f-reťazec je tam jeden token),
# \r v texte (strom počíta riadky inak), chyba tokenizéra alebo poloha, ktorá nesedí s textom → strom ako doteraz.
# SKG 3 (b10, 9. 11.): na pythone 3.12 číta tokeny priamo C tokenizér (tokenize._tokenize.TokenizerIter — to isté, čo
# generate_tokens volá, bez obalu, ktorý z každého tokenu robí pomenovanú n-ticu), a uzavri sa volá len pri otvorenej
# skupine reťazcov (s None vracia vždy True). Výsledok rovnaký (súbory pythonu projektov PS, skripty stroja, 70 textov
# rozboru `ng-stav.py`: 0 rozdielov); iná verzia pythonu alebo chýbajúci TokenizerIter → generate_tokens ako doteraz.
# Pri `ng-stav.py` s celým textom 0,64 → 0,40 s.
def _py_popisy_tok(obs):
    """Popisy pythonu → None s pármi úvodzoviek podľa tokenizéra (SKG 3 b3, vyššie). None → nevie (strom)."""
    if '\r' in obs or sys.version_info < (3, 12):
        return None
    import tokenize
    riadky = obs.split('\n')
    kandidati = {i + 1 for i, r in enumerate(riadky) if PY_POPIS_ZACIATOK.match(r.lstrip(' \t'))}
    vstup = iter([r + '\n' for r in riadky[:-1]] + ([riadky[-1]] if riadky[-1] else []))
    STR, FS, FE = tokenize.STRING, tokenize.FSTRING_START, tokenize.FSTRING_END
    PRIEHLADNE = (tokenize.NL, tokenize.COMMENT)
    nahrady, zasobnik, sk = [], [], None      # sk = [r0, c0, r1, c1, je_f, prvý token] — skupina susedných reťazcov

    def uzavri(s):
        if s is None or s[4] or s[0] not in kandidati:
            return True
        r0, c0, r1, c1 = s[:4]
        if riadky[r0 - 1][:c0].strip(' \t') or not PY_POPIS_ZACIATOK.match(s[5]):
            return True                     # nezačína riadok alebo jednoduché úvodzovky — ostáva ako vo vzore
        if not riadky[r0 - 1].startswith(s[5].split('\n', 1)[0], c0) or not riadky[r1 - 1][:c1].endswith(('"', "'")):
            return False                    # poloha nesedí s textom
        nahrady.append((r0, c0, r1, c1))
        return True
    citac = None
    if sys.version_info[:2] == (3, 12):
        try:
            citac = tokenize._tokenize.TokenizerIter
        except AttributeError:
            pass
    try:
        if citac is not None:
            tokeny = citac(lambda: next(vstup, ''), extra_tokens=True)
        else:
            tokeny = (tuple(x) for x in tokenize.generate_tokens(lambda: next(vstup, '')))
        for x in tokeny:
            typ = x[0]
            if typ == STR:
                if sk is None:
                    sk = [x[2][0], x[2][1], x[3][0], x[3][1], False, x[1]]
                else:
                    sk[2], sk[3] = x[3]
            elif typ == FS:
                if sk is None:
                    sk = [x[2][0], x[2][1], x[3][0], x[3][1], True, x[1]]
                else:
                    sk[4] = True
                zasobnik.append(sk)
                sk = None
            elif typ == FE:
                if not uzavri(sk) or not zasobnik:
                    return None
                sk = zasobnik.pop()
                sk[2], sk[3] = x[3]
            elif sk is not None and typ not in PRIEHLADNE:
                if not uzavri(sk):
                    return None
                sk = None
    except (SyntaxError, ValueError, RecursionError, MemoryError, tokenize.TokenError):
        return None
    if zasobnik or not uzavri(sk):
        return None
    nahrady.sort()
    for i in range(1, len(nahrady)):
        if nahrady[i][:2] < nahrady[i - 1][2:]:
            return None
    for r0, c0, r1, c1 in reversed(nahrady):
        riadky[r0 - 1:r1] = [riadky[r0 - 1][:c0] + 'None' + riadky[r1 - 1][c1:]]
    return '\n'.join(riadky)


@_pamat
def py_bez_popisov(obs):
    """Text pythonu s reťazcami v trojitých úvodzovkách, ktoré začínajú riadok, nahradenými None (SKG 2, vyššie); páry
    úvodzoviek podľa tokenizéra (SKG 3 b3), keď nevie, podľa stromu. Nečitateľný kód alebo poloha, ktorá nesedí s textom
    → None."""
    if not PY_POPIS_RIADOK.search(obs):
        return obs                              # žiadny riadok nezačína trojitými úvodzovkami — vzor by nezmenil nič
    nove = _py_popisy_tok(obs)
    return _py_bez_popisov_strom(obs) if nove is None else nove


def _py_bez_popisov_strom(obs):
    """py_bez_popisov podľa stromu (SKG 2) — keď tokenizér nevie (python pred 3.12, \\r v texte, chyba tokenizéra)."""
    try:
        strom = ast.parse(obs)
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        return None
    riadky = obs.split('\n')
    kandidati = {i + 1 for i, r in enumerate(riadky) if PY_POPIS_ZACIATOK.match(r.lstrip(' \t'))}
    retazce, vnutri_f = [], set()
    for u in ast.walk(strom):
        if isinstance(u, ast.JoinedStr):
            vnutri_f.update(id(x) for x in u.values)     # časti f-reťazca (aj formátu — ten je tiež JoinedStr)
        elif isinstance(u, ast.Constant) and isinstance(u.value, (str, bytes)) and u.lineno in kandidati:
            retazce.append(u)
    nahrady = []
    for u in retazce:
        if id(u) in vnutri_f:
            continue
        if u.end_lineno is None or u.end_col_offset is None or not (1 <= u.lineno <= u.end_lineno <= len(riadky)):
            return None
        b0 = riadky[u.lineno - 1].encode('utf-8')
        pred = b0[:u.col_offset].decode('utf-8', 'replace')
        if pred.strip(' \t'):
            continue                            # nezačína riadok (x = """…""") — ostáva ako vo vzore
        if not PY_POPIS_ZACIATOK.match(b0[u.col_offset:].decode('utf-8', 'replace')):
            continue                            # jednoduché úvodzovky alebo iná predpona — ostáva ako vo vzore
        b1 = riadky[u.end_lineno - 1].encode('utf-8')
        po = b1[:u.end_col_offset].decode('utf-8', 'replace')
        if not po.endswith(('"', "'")):
            return None                         # poloha nesedí s textom (iné konce riadkov) — vzor ako doteraz
        nahrady.append((u.lineno, len(pred), u.end_lineno, len(po)))
    nahrady.sort()
    for i in range(1, len(nahrady)):
        if nahrady[i][:2] < nahrady[i - 1][2:]:
            return None
    for r0, c0, r1, c1 in reversed(nahrady):
        riadky[r0 - 1:r1] = [riadky[r0 - 1][:c0] + 'None' + riadky[r1 - 1][c1:]]
    return '\n'.join(riadky)


PY_POPIS_VZOR = re.compile(r'''^([ \t]*)(?:[rRuU]|[bB][rR]?)?("""|\'\'\')[\s\S]*?\2''', re.M)


def _py_plati(t):
    try:
        ast.parse(t)
        return True
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        return False


# SKG 3 (c, 9. 11.): popisy páruje tokenizér vždy, nielen keď vzor urobí neplatný kód. Vzor (PY_POPIS_VZOR) páruje
# trojité úvodzovky podľa začiatku riadku: zatváracie úvodzovky viacriadkového reťazca na začiatku riadku berie za
# otváracie a zmaže kód až po ďalší popis — výsledok môže byť platný python a kód v ňom chýba (6. 11.: 124 súborov
# v projektoch PS, napr. celá funkcia v AIB portal.py, zápis súboru v SP-REG process_batch.py; am-ps teams-prepis
# spracuj.py — načítanie graph_mail, ktoré kotva preto nevidela pri `spracuj.py … --zapis --mail`). Prepnutie čakalo na
# (a) zúženie „kód vie spustiť reťazec“ na reťazce, ktoré do spúšťača vedú, a (b) čas rozboru s celým textom pod
# stropom (b1–b10: `python3 .aigrip/scripts/ng-stav.py` 4,46–4,49 s pri záťaži servera ~1,6, strop 5,5 s; pri záťaži
# 13–14 odhad 4,9–5,1 s). Prehrávka 129 príkazov zo 7 dní (6. 11., kde plná výmena menila zastavenie alebo čas):
# pribudne 10 zastavení, všetky `spracuj.py` v am-ps. Vzor ostáva, keď tokenizér nevie (python pred 3.12, \r v texte, chyba tokenizéra) —
# py_bez_popisov vtedy vráti None. PY_POPIS_STROM = False vráti správanie spred 9. 11. (meradlá v dielňach ho čítajú).
PY_POPIS_STROM = True


@_pamat
def py_popisy(obs):
    """Popisy pythonu → None (skript_kod). Páry podľa tokenizéra (py_bez_popisov); vzor, len keď tokenizér nevie (SKG 3
    c, vyššie). S PY_POPIS_STROM = False ako pred 9. 11.: vzor, a tokenizér len keď vzor urobí neplatný kód."""
    if PY_POPIS_STROM:
        nove = py_bez_popisov(obs)
        return PY_POPIS_VZOR.sub(r'\1None', obs) if nove is None else nove
    stare = PY_POPIS_VZOR.sub(r'\1None', obs)
    if stare == obs or _py_plati(stare):
        return stare
    nove = py_bez_popisov(obs)
    return stare if nove is None else nove


_PY_SITE = {}
# KMP (2c): predbežne — kód, ktorý môže meniť sys.path (path.insert/append/extend, priradenie, rez, +=, addsitedir)
_PY_SYSPATH_PRED = re.compile(r'path[\s\\]*(?:\.[\s\\]*(?:insert|append|extend)\b|(?:\[[^\]\n]*\][ \t]*)?'
                              r'(?:\+=|=(?!=)))')
_PY_SYSPATH_MET = ('insert', 'append', 'extend', 'addsitedir')


def _py_slovo_pred(kd, k):
    """KMP (2c): znak pred pozíciou k je písmeno, číslica alebo _ (ako \\w) — hranica slova bez \\b na začiatku hľadania."""
    return k > 0 and (kd[k - 1].isalnum() or kd[k - 1] == '_')


def _py_syspath_pred(kd):
    """KMP (2c): kód môže meniť sys.path — `.path.insert/append/extend`, `.path = / += / [..] =`, addsitedir alebo
    `from sys import`. Predtým jedno hľadanie s alternatívami (59 ms na 116 skriptov stroja), teraz 5 ms."""
    if 'addsitedir' in kd or re.search(r'from[ \t]+sys[ \t]+import', kd):
        return True
    for m in _PY_SYSPATH_PRED.finditer(kd):
        k = m.start() - 1
        while k >= 0 and kd[k] in ' \t\n\r\\':
            k -= 1
        if k >= 0 and kd[k] == '.':
            return True
    return False


def py_modul_mimo_kodu(top):
    """KMP (15. 10.): modul `top` je zo štandardnej knižnice alebo nainštalovaný balík (site-packages, dist-packages
    interpretra, pod ktorým beží rozbor) — nemusí byť v neprečítanom priečinku sys.path. Bez importu: zoznam priečinkov."""
    if top in (getattr(sys, 'stdlib_module_names', None) or ()) or top in getattr(sys, 'builtin_module_names', ()):
        return True
    if not _PY_SITE:
        for d in getattr(sys, 'path', ()):
            if d and ('site-packages' in d or 'dist-packages' in d) and os.path.isdir(d):
                try:
                    _PY_SITE[d] = frozenset(os.listdir(d))
                except OSError:
                    pass
        _PY_SITE.setdefault('', frozenset())
    for obs in _PY_SITE.values():
        if top in obs or top + '.py' in obs or any(f.startswith(top + '.') and f.endswith(('.so', '.pyd')) for f in obs
                                                   if f[:len(top)] == top):
            return True
    return False


@_pamat
def py_importy(kd):
    """Mená modulov, ktoré kód načíta príkazom import (py_moduly): `import a.b as c, d` → a.b, d; `from a import b` → a,
    a.b; relatívny import (from . / from .x) nie. K88X: predtým celý strom kódu (ast.walk) pri každom rozbore skriptu
    pythonu — pri ng-stav.py 105-krát, asi tretina času rozboru. Riadok s pokračovaním (\\ na konci) číta strom."""
    mena = set()
    if re.search(r'\b(?:import|from)\b[^\n]*\\\n', kd):
        try:
            for u in ast.walk(ast.parse(kd)):
                if isinstance(u, ast.Import):
                    mena.update(a.name for a in u.names)
                elif isinstance(u, ast.ImportFrom) and not u.level and u.module:
                    mena.add(u.module)
                    mena.update(u.module + '.' + a.name for a in u.names if a.name != '*')
            return frozenset(mena)
        except (SyntaxError, ValueError, RecursionError, MemoryError):
            pass
    for m in PY_IMPORT_RIADOK.finditer(kd):
        if m.group(1) is not None:
            if m.group(1).startswith('.'):
                continue
            mena.add(m.group(1))
            for x in m.group(2).strip('() \t').split(','):
                x = x.split()
                if x and re.fullmatch(r'\w+', x[0]):
                    mena.add(m.group(1) + '.' + x[0])
        else:
            for x in m.group(3).split(','):
                x = x.split()
                if x and re.fullmatch(r'[\w.]+', x[0]):
                    mena.add(x[0])
    return frozenset(mena)


# volanie, ktorého cesta je holé meno (kandidát na parameter): spec_from_file_location(n, p), run_path(p), location=p
PY_NACITA_CESTU = re.compile(r'\b(?:(?:spec_from_file_location|SourceFileLoader|load_source)\s*\([^,()]*,|run_path\s*\()'
                             r'\s*([A-Za-z_]\w*)\s*[,)]|\b(?:location|path|path_name|pathname)\s*=\s*([A-Za-z_]\w*)\s*[,)]')


@_pamat
def py_nacitanie_z_parametra(kd):
    """K88YB: funkcie, ktoré načítajú modul z cesty v svojom parametri (def load(p, n): spec_from_file_location(n, p) …)
    → ((meno funkcie, index parametra alebo None pri len kľúčovom, meno parametra, len súbor .py, prvý parameter je
    self/cls), …). Strom kódu sa číta len pri kóde s takým volaním (py_importy: strom pri každom skripte bol tretina času)."""
    mena = {m.group(1) or m.group(2) for m in PY_NACITA_CESTU.finditer(kd)
            if kd[kd.rfind('\n', 0, m.start()) + 1:m.start() + 1][:1] in (' ', '\t')}      # len odsadené (v tele funkcie)
    if not mena or not re.search(r'\bdef[ \t]+\w+[ \t]*\([^)]*\b(?:' + '|'.join(sorted(mena)) + r')\b', kd):
        return ()
    try:
        strom = ast.parse(kd)
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        return ()
    von = []
    for f in ast.walk(strom):
        if not isinstance(f, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        par = [a.arg for a in f.args.posonlyargs + f.args.args]
        kluc = [a.arg for a in f.args.kwonlyargs]
        for u in ast.walk(f):
            if not isinstance(u, ast.Call):
                continue
            fn = u.func
            meno = fn.attr if isinstance(fn, ast.Attribute) else fn.id if isinstance(fn, ast.Name) else None
            if meno not in ('spec_from_file_location', 'SourceFileLoader', 'load_source', 'run_path'):
                continue
            k = 0 if meno == 'run_path' else 1
            arg = u.args[k] if len(u.args) > k else next(
                (x.value for x in u.keywords if x.arg in ('location', 'path', 'path_name', 'pathname')), None)
            if not isinstance(arg, ast.Name) or arg.id not in par + kluc:
                continue
            len_py = meno == 'spec_from_file_location' and len(u.args) < 3 and not any(x.arg == 'loader' for x in u.keywords)
            von.append((f.name, par.index(arg.id) if arg.id in par else None, arg.id, len_py, par[:1] in (['self'], ['cls'])))
    return tuple(von)


HTTP_METODY = ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS')
# KMP (1a): volania, ktorých prvý argument metódou HTTP ďalej nebude (json.dumps(body) — telo, nie metóda; výpis, dĺžka)
PY_NEMETODA_VOLANIA = frozenset(('dumps', 'dump', 'print', 'len', 'isinstance', 'urlencode', 'int', 'float', 'bool',
                                 'repr', 'type', 'id', 'hash', 'debug', 'info', 'warning', 'error', 'exception'))


# KMP 1e: riadok `x = meno` (prevzatie funkcie pod iným menom) a meno použité ako hodnota (nie volanie)
PY_ALIAS_RIADOK = re.compile(r'(?m)^(\w+)[ \t]*=[ \t]*(\w+)[ \t]*$')
PY_MENO_HODNOTA = re.compile(r'(?:(?<=\.)|(?<![\w.]))(\w+)(?!\w)(?![ \t]*\()')


@_pamat
def py_metodove_parametre(kod):
    """KMP (1a, 16. 10.): funkcie modulu, ktorých parameter môže byť metódou HTTP → {meno: (pozície, mená)}. Parameter je
    „metódový“, keď ho telo funkcie dáva do `method=` alebo ako prvý argument volania (`Request(…, method=m)`,
    `requests.request(m, url)`, `conn.request(m, cesta)`, aj ďalej do inej funkcie `_req(m, cesta)`), aj s .upper()/
    .lower(), alebo ho číta getattr (getattr(requests, m.lower())). Pozície sú bez self/cls (volanie cez objekt).
    Hrubé čítanie podľa odsadenia — parametrov radšej viac (neznáma hodnota z volania potom zastaví), nie menej."""
    riadky = kod.split('\n')
    von = {}
    for i, r in enumerate(riadky):
        m = re.match(r'([ \t]*)(?:async[ \t]+)?def[ \t]+(\w+)[ \t]*\(', r)
        if not m:
            continue
        hlava = r[m.end():]
        j = i
        while hlava.count('(') + 1 > hlava.count(')') and j + 1 < len(riadky) and j - i < 20:
            j += 1
            hlava += ' ' + riadky[j]
        hlava = hlava.split(')')[0] if ')' in hlava else hlava
        mena, dflt = [], []
        for x in hlava.split(','):
            d = re.search(r'=(.*)$', x)
            x = re.sub(r'[:=].*$', '', x).strip()
            if x in ('*', '/') or not x:
                continue
            mena.append(x)
            dflt.append(None if not d else d.group(1).strip())
        if mena and mena[0] in ('self', 'cls'):
            mena, dflt = mena[1:], dflt[1:]
        odsad = len(m.group(1).expandtabs())
        telo = []
        for r2 in riadky[j + 1:]:
            if r2.strip() and len(r2) - len(r2.lstrip()) <= odsad and not r2.lstrip().startswith(('#', ')', ']', '}')):
                break
            telo.append(r2)
        telo = '\n'.join(telo)
        poz, kl, pred = set(), set(), {}
        for k, x in enumerate(mena):
            hviezd = x.startswith('*')
            x = x.lstrip('*')
            if not re.fullmatch(r'[A-Za-z_]\w*', x):
                continue
            uprava = r'(?:[ \t]*\.[ \t]*(?:upper|lower|strip|casefold)[ \t]*\([ \t]*\))?'
            if re.search(r'\bmethod[ \t]*=[ \t]*' + x + r'\b', telo) \
                    or any(mm.group(1).split('.')[-1] not in PY_NEMETODA_VOLANIA for mm in re.finditer(
                        r'([\w.]+|[\])])[ \t]*\([ \t]*\**' + x + r'\b' + uprava + r'[ \t]*[,)]', telo)) \
                    or re.search(r'\bgetattr[ \t]*\([^,()\n]*,[ \t]*' + x + r'\b', telo):
                if hviezd:
                    poz.add('*')             # *args/**kw idú ďalej — hodnota na ktorejkoľvek pozícii
                else:
                    poz.add(k)
                    kl.add(x)
                    # predvolená hodnota (def call(cesta, m="GET")) — platí, keď volanie parameter vynechá
                    dl = re.fullmatch(r'(["\'])(\w*)\1', dflt[k] or '')
                    # None, číslo, True/False, prázdny kontajner metódou nie je — volanie bez neho nič nepridá
                    nic = dflt[k] is None or re.fullmatch(r'None|True|False|-?[\d.]+|\(\)|\[\]|\{\}', dflt[k])
                    pred[k] = (x, dl.group(2).upper() if dl and dl.group(2).upper() in HTTP_METODY
                               else None if nic else '?')
        if poz or kl:
            p0, k0, d0 = von.get(m.group(2), (set(), set(), {}))
            d0 = dict(d0)
            for k, v in pred.items():
                if k in d0 and d0[k] != v:
                    v = (v[0], '?')          # dve funkcie s tým istým menom a inou predvolbou
                d0[k] = v
            von[m.group(2)] = (p0 | poz, k0 | kl, d0)
    return von


def py_volania_modulu(kr, kd, modul_kod):
    """KMP (1a, 16. 10.): metódy HTTP, s ktorými načítajúci kód (kd, jeho KodRozbor kr) volá funkcie modulu → zoradená
    n-tica (METÓDA, doslovná cesta | None); '?' = metóda, ktorú rozbor neprečíta. Rozbor modulu ich pridá k metódam
    volaní v module (kod_http_zapis: metody_volani, zapisy) — ako keby načítajúci a modul boli jeden súbor.
    15. 10. (prehrávka KMP): `g._req("GET", "/me/messages", at)` z poštového modulu PS zastavilo ako zápis cez API —
    modul sa číta bez načítajúceho a metóda z parametra (`Request(…, method=method)`) bola neznáma; rovnako dnes
    zastaví aj variant s `cd ~/.config/aigrip`. Z načítajúceho sa berie: (1) každý doslovný reťazec s metódou ako prvý
    argument volania (to isté čítanie ako v module), (2) zápisová metóda doslova na inej pozícii a `method="…"`,
    `method=` s neznámou hodnotou je '?', (3) volanie funkcie modulu s metódovým parametrom (py_metodove_parametre):
    hodnota na jeho mieste doslova → jej metóda, reťazec, ktorý nie je metóda, alebo neznáma hodnota, *args/**kw,
    alebo funkcia použitá ako hodnota (f = g._req) → '?'.
    Modul bez HTTP alebo bez funkcie s metódovým parametrom → prázdne: metódy načítajúceho by nič nezmenili a kľúč
    pamäte modulu ostane ako doteraz (inak sa reťaz modulov ng-stav.py rozoberala pre každého načítajúceho zvlášť a
    prešla strop času)."""
    if not any(kod_http_predbezne(skript_kod(modul_kod, 'py'))):
        return ()
    fn = dict(py_metodove_parametre(modul_kod))
    if not fn:
        return ()
    von = set()
    for m in re.finditer(r'''\(\s*['"](GET|POST|PUT|PATCH|DELETE|get|post|put|patch|delete)['"]\s*(?:,\s*f?(['"])([^'"\n]*)\2)?''', kd):
        von.add((m.group(1).upper(), m.group(3) if m.group(1).upper() != 'GET' else None))
    for m in re.finditer(r''',\s*['"](POST|PUT|PATCH|DELETE|post|put|patch|delete)['"]''', kd):
        von.add((m.group(1).upper(), None))
    for m in re.finditer(r'''\bmethod\s*=\s*(?:['"](\w*)['"]|(?=[\w(\[{]))''', kd):
        x = (m.group(1) or '').upper()
        von.add((x, None) if x in HTTP_METODY else ('?', None))
    if fn:
        # funkcia s metódovým parametrom pod iným menom v module: `request = _req` (prevezme pozície); iné použitie ako
        # hodnota (OPS = {"r": _req}, partial(_req, …)) — volanie z načítajúceho rozbor nevidí → '?'
        bez_def = re.sub(r'(?m)^[ \t]*(?:async[ \t]+)?def[ \t][^\n]*', '', modul_kod)
        # KMP 1e: jeden prechod textu — riadky `x = meno` a mená použité ako hodnota (nie volanie); meno je hodnotou,
        # keď sa vyskytne aj mimo riadkov `x = meno` (tie sú prevzatie pozícií, nie hodnota)
        ali = {}
        for mm in PY_ALIAS_RIADOK.finditer(bez_def):
            ali.setdefault(mm.group(2), []).append(mm.group(1))
        hodnoty = {}
        for mm in PY_MENO_HODNOTA.finditer(bez_def):
            hodnoty[mm.group(1)] = hodnoty.get(mm.group(1), 0) + 1
        for meno in list(fn):
            for x in ali.get(meno, ()):
                fn.setdefault(x, fn[meno])
            na_ali = sum(2 if x == meno else 1 for x in ali.get(meno, ()))
            if hodnoty.get(meno, 0) > na_ali:
                von.add(('?', None))
        # `from modul import _req as r` — volanie r(…) je volanie _req
        alias = {}
        for mm in re.finditer(r'\bfrom[ \t]+[\w.]+[ \t]+import[ \t]*(\([^)]*\)|[^\n;]*)', kd):
            for x in mm.group(1).strip('() \t').split(','):
                x = x.split()
                if len(x) == 3 and x[1] == 'as' and x[0] in fn:
                    alias[x[2]] = x[0]
        t = kr.t
        n = len(t)
        bez_importu = re.sub(r'(?m)^[ \t]*(?:from|import)\b[^\n]*', '', kd)
        hodnoty_kd = {mm.group(1) for mm in PY_MENO_HODNOTA.finditer(bez_importu)}
        def_kd = set(re.findall(r'\bdef[ \t]+(\w+)', bez_importu))
        for meno in list(fn) + list(alias):
            if meno in hodnoty_kd and meno not in def_kd:
                von.add(('?', None))          # funkcia modulu ako hodnota — volanie cez iné meno rozbor nevidí
        for k in range(n - 1):
            d, v = t[k]
            if d != 'n' or t[k + 1] != ('p', '(') or (k and t[k - 1] == ('n', 'def')):
                continue
            meno = v.split('.')[-1]
            meno = alias.get(meno, meno) if '.' not in v else meno
            if meno not in fn:
                continue
            poz_m, kl_m, pred_m = fn[meno]
            kon = kr.par.get(k + 1, n)
            i = 0
            dane = set()
            for a, b in kr.delenie(k + 2, kon):
                if a >= b:
                    continue
                if t[a] == ('p', '*') or t[a] == ('p', '**') or (t[a][0] == 'p' and t[a][1].startswith('*')):
                    von.add(('?', None))
                    dane.add('*')
                    continue
                if b - a >= 3 and t[a][0] == 'n' and t[a + 1] == ('p', '='):
                    meno_kl, a = t[a][1], a + 2
                    dane.add(meno_kl)
                    if meno_kl not in kl_m and '*' not in poz_m:
                        continue
                else:
                    meno_kl = None
                    i += 1
                    dane.add(i - 1)
                    if (i - 1) not in poz_m and '*' not in poz_m:
                        continue
                hod = None
                if b - a == 1 and t[a][0] == 's' and all(x[0] == 'l' for x in t[a][1]):
                    hod = kr.retazec(t[a][1]).upper()
                if hod is None or hod not in HTTP_METODY:
                    if hod is not None and '*' in poz_m and meno_kl is None:
                        continue                # doslovný reťazec v *args (cesta, telo) — metódu dá doslovná metóda
                    von.add(('?', None))
                    continue
                cesta = None
                if hod != 'GET' and meno_kl is None:
                    za = [(x, y) for x, y in kr.delenie(k + 2, kon) if x > a]
                    if za and za[0][1] - za[0][0] == 1 and t[za[0][0]][0] == 's' \
                            and all(x[0] == 'l' for x in t[za[0][0]][1]):
                        cesta = kr.retazec(t[za[0][0]][1])
                von.add((hod, cesta))
            if '*' not in dane:
                for kk, (meno_p, pv) in pred_m.items():
                    if kk not in dane and meno_p not in dane and pv is not None:
                        von.add((pv, None))
    return tuple(sorted(von, key=lambda x: (x[0], x[1] or '')))[:64]


# KHD (16. 10.; z kusu KHM): telo požiadavky urllib z parametra funkcie s predvolbou None. Prehrávka 30 dní (KHM) našla
# v AEM (17. 9.) modul jira.py s `def q(path, data=None)` → `urllib.request.Request(SITE+path, data=data, …)` (bez data
# = GET, s data = POST) a volanie `q("/rest/api/3/project/search?…")` — čítanie Jiry, ktoré kotva zastavila ako zápis do
# cudzieho systému, lebo hodnotu parametra `data` rozbor nepozná. Teraz je telo prázdne (GET), keď: parameter má predvolbu
# None a v tele funkcie sa vyskytuje len na mieste tela požiadavky (nepriraďuje sa, nečíta sa, nie je tam vnorená funkcia
# ani lambda), každé také miesto v kóde je v tele takej funkcie, každé viditeľné volanie funkcie — v module aj
# v načítajúcom (py_volania_dat) — ho vynechá alebo dá doslova None a nemá *args/**kw, aspoň jedno volanie je vidieť
# a funkcia sa nikde nepoužije ako hodnota (alias, partial, odovzdanie). Inak ostáva zápisom. Hranica (ako pri metóde
# z parametra, KMP 1a): volanie cez tretí modul, ktorý funkciu len prevezme (from jira import q) a dá ju ďalej svojmu
# načítajúcemu, rozbor nevidí.
def py_telo_pocet(kod, x):
    """KHD: koľkokrát je meno x telom požiadavky urllib — `data=x` alebo druhý pozičný argument v Request(…)/urlopen(…)."""
    kl = re.findall(r'\b(?:Request|urlopen)[ \t]*\((?:[^()]|\([^()]*\))*?[(,][ \t\n]*data[ \t]*=[ \t]*' + x
                    + r'[ \t\n]*[,)]', kod)
    pz = re.findall(r'\b(?:Request|urlopen)[ \t]*\([ \t\n]*(?:[^(),=]|\([^()]*\))+,[ \t\n]*' + x + r'[ \t\n]*[,)]', kod)
    return len(kl) + len(pz)


@_pamat
def py_datove_parametre(kod):
    """KHD: funkcie, ktoré parameter dávajú ako telo požiadavky urllib → {meno: ((pozícia, meno parametra, isté, koľko), …)}.
    isté = predvolba je None, meno sa v tele vyskytuje len na mieste tela požiadavky a v tele nie je vnorená funkcia ani
    lambda; koľko = počet miest tela požiadavky s týmto menom v tele. Pozície sú bez self/cls (volanie cez objekt)."""
    if 'Request' not in kod and 'urlopen' not in kod:
        return {}
    riadky = kod.split('\n')
    von = {}
    for i, r in enumerate(riadky):
        m = re.match(r'([ \t]*)(?:async[ \t]+)?def[ \t]+(\w+)[ \t]*\(', r)
        if not m:
            continue
        hlava = r[m.end():]
        j = i
        while hlava.count('(') + 1 > hlava.count(')') and j + 1 < len(riadky) and j - i < 20:
            j += 1
            hlava += ' ' + riadky[j]
        hlava = hlava.split(')')[0] if ')' in hlava else hlava
        mena, dflt = [], []
        for x in hlava.split(','):
            d = re.search(r'=(.*)$', x)
            x = re.sub(r'[:=].*$', '', x).strip()
            if x in ('*', '/') or not x:
                continue
            mena.append(x)
            dflt.append(None if not d else d.group(1).strip())
        if mena and mena[0] in ('self', 'cls'):
            mena, dflt = mena[1:], dflt[1:]
        odsad = len(m.group(1).expandtabs())
        telo = []
        for r2 in riadky[j + 1:]:
            if r2.strip() and len(r2) - len(r2.lstrip()) <= odsad and not r2.lstrip().startswith(('#', ')', ']', '}')):
                break
            telo.append(r2)
        telo = '\n'.join(telo)
        vnorene = re.search(r'(?m)^[ \t]+(?:async[ \t]+)?def\b|\blambda\b', telo)
        for k, x in enumerate(mena):
            if not re.fullmatch(r'[A-Za-z_]\w*', x):
                continue
            kolko = py_telo_pocet(telo, x)
            if not kolko:
                continue
            # výskyty mena okrem kľúčového slova volania (f(x=…)); priradenie `x = …` na začiatku riadku sa ráta
            vyskyty = len(re.findall(r'(?<![\w.])' + x + r'\b', telo)) \
                - len(re.findall(r'[(,][ \t\n]*' + x + r'[ \t]*=(?!=)', telo))
            iste = (dflt[k] or '').strip() == 'None' and not vnorene and vyskyty == kolko
            von.setdefault(m.group(2), []).append((k, x, iste, kolko))
    return {m: tuple(v) for m, v in von.items()}


def py_volania_dat_scan(kr, df, alias=None):
    """KHD: volania funkcií z df v kóde (KodRozbor kr) → {meno: {'bez', 'telo'}}; 'bez' = volanie telo požiadavky vynechá
    alebo dá doslova None, 'telo' = dá ho (alebo to nevidno: *args/**kw), alebo je funkcia použitá ako hodnota."""
    t = kr.t
    n = len(t)
    alias = alias or {}
    von = {}
    for k in range(n):
        d, v = t[k]
        if d != 'n':
            continue
        meno = v.split('.')[-1]
        meno = alias.get(meno, meno) if '.' not in v else meno
        if meno not in df or (k and t[k - 1] == ('n', 'def')):
            continue
        if k + 1 >= n or t[k + 1] != ('p', '('):
            j = k - 1
            while j >= 0 and t[j][0] != 'nl' and t[j] != ('p', ';') and t[j] != ('n', 'import'):
                j -= 1
            if not (j >= 0 and t[j] == ('n', 'import')):
                von.setdefault(meno, set()).add('telo')       # funkcia ako hodnota — volanie cez iné meno nevidno
            continue
        kon = kr.par.get(k + 1, n)
        stav, i = 'bez', 0
        for a, b in kr.delenie(k + 2, kon):
            if a >= b:
                continue
            if t[a][0] == 'p' and t[a][1].startswith('*'):
                stav = 'telo'
                break
            if b - a >= 3 and t[a][0] == 'n' and t[a + 1] == ('p', '='):
                kw, h = t[a][1], (a + 2, b)
            else:
                kw, h = None, (a, b)
                i += 1
            for poz, x, _, _ in df[meno]:
                if (kw == x or (kw is None and i - 1 == poz)) \
                        and not (h[1] - h[0] == 1 and t[h[0]] == ('n', 'None')):
                    stav = 'telo'
        von.setdefault(meno, set()).add(stav)
    return von


def py_volania_dat(kr, kd, modul_kod):
    """KHD: ako načítajúci (kd, jeho KodRozbor kr) volá funkcie modulu, ktoré parameter dávajú ako telo požiadavky urllib →
    ('~D', meno) volanie bez tela · ('~D?', meno) s telom alebo neviditeľné. Pridáva sa k mod_volania (rozbor modulu ich
    oddelí od metód); modul bez takej funkcie → prázdne, kľúč pamäte modulu ostane ako doteraz."""
    df = {m: v for m, v in py_datove_parametre(modul_kod).items() if all(x[2] for x in v)}
    if not df:
        return ()
    alias = {}
    for mm in re.finditer(r'\bfrom[ \t]+[\w.]+[ \t]+import[ \t]*(\([^)]*\)|[^\n;]*)', kd):
        for x in mm.group(1).strip('() \t').split(','):
            x = x.split()
            if len(x) == 3 and x[1] == 'as' and x[0] in df:
                alias[x[2]] = x[0]
    von = set()
    for meno, st in py_volania_dat_scan(kr, df, alias).items():
        for x in st:
            von.add(('~D' if x == 'bez' else '~D?', meno))
    return tuple(sorted(von))


def py_telo_bez_dat(kr, kd, casti, param, mod_volania):
    """KHD: telo požiadavky urllib (data=x alebo druhý pozičný argument; casti = rozsahy argumentov volania) je parameter
    x s predvolbou None, ktorý žiadne viditeľné volanie nedá (v kóde kd ani v načítajúcom — značky ~D v mod_volania) → True."""
    t = kr.t
    x, i = None, 0
    for a, b in casti:
        if a >= b:
            continue
        if t[a][0] == 'p' and t[a][1].startswith('*'):
            return False
        if b - a >= 2 and t[a][0] == 'n' and t[a + 1] == ('p', '='):
            if t[a][1] == 'data':
                if b - a != 3 or t[a + 2][0] != 'n':
                    return False
                x = t[a + 2][1]
            continue
        i += 1
        if i == 2:
            if b - a != 1 or t[a][0] != 'n':
                return False
            x = t[a][1]
    if not x or x not in param:
        return False
    df = {}
    for m, v in py_datove_parametre(kd).items():
        v = [p for p in v if p[1] == x]
        if v:
            df[m] = v
    if not df or not all(p[2] for v in df.values() for p in v):
        return False
    if py_telo_pocet(kd, x) != sum(p[3] for v in df.values() for p in v):
        return False                                  # telo požiadavky s x aj mimo tela takej funkcie
    mv = {}
    for mt, c in mod_volania:
        if mt in ('~D', '~D?'):
            mv.setdefault(c, set()).add('bez' if mt == '~D' else 'telo')
    dokaz = False
    for m, st in py_volania_dat_scan(kr, df).items():
        mv.setdefault(m, set()).update(st)
    for m in df:
        st = mv.get(m, set())
        if 'telo' in st:
            return False
        dokaz = dokaz or 'bez' in st
    return dokaz


# KOTF (23. 10.; z merania KOTW, snímka .aigrip/ng/kotva-cudzia-tyzden.md): zápis cez urllib z pomocnej funkcie v kóde
# NAPÍSANOM V PRÍKAZE. Pravidlo o kóde (CUDZIA_KOD, poloha „prikaz“) zastavilo za 9,6 dňa päťkrát kód, ktorý si urobí
# pomocníka `def call(path, body=None): … Request(U + path, data=… if body else None, method="POST" if body else "GET")`
# a volá ho s doslovnými hodnotami: adresa je parameter funkcie (alebo doslovná adresa + cesta z parametra), rozbor po
# tokenoch ju neprečíta. Bolo to upratanie na skúšobnej appke bpw (adresa na zozname povolených), vyhľadávanie v Google
# Ads, náklady a zoznam zdrojov v Azure a vyhľadávanie v Jire — ani jedno nebolo zápisom do cudzieho systému. Teraz sa
# každé volanie urllib (Request, urlopen) posúdi pri každom viditeľnom volaní pomocníka zvlášť: do parametrov sa dosadia
# hodnoty z volania (doslovný text, f-reťazec s neznámou vloženou časťou, spojenie +, doslovná konštanta modulu), telo
# a metóda sa vyhodnotia (`x if body else None`, `"POST" if body is not None else "GET"`) a adresa ide tým istým posudkom
# ako adresa napísaná doslova (kod_http_zapis.posud). Len keď: kód sa dá prečítať stromom, nesiaha na mená za behu
# (PY_SIET_DYNAMIKA, getattr s menom z premennej, import *), každé volanie urllib je na vrchnej úrovni alebo priamo vo
# funkcii na vrchnej úrovni — bez dekorátora, *args/**kw, vnorenej funkcie, lambdy či triedy —, funkcia je viazaná len
# svojou definíciou, jej meno sa používa len na priame volanie (nie ako hodnota) bez *args/**kw a aspoň raz sa volá.
# Inak platí rozbor po tokenoch ako doteraz. Neznáme sa berie prísne: telo, o ktorom nevidno, že je None, je telo (zápis),
# neznáma metóda je zápis, parameter, ktorý sa vo funkcii mení, je neznámy, a konštanta modulu je len meno viazané
# v celom kóde raz, priradením textu na vrchnej úrovni. Platí len pre kód napísaný v príkaze — uložený skript môže byť
# modul a jeho funkcie volá aj iný kód (Rozbor.kod_http_zapis, cely).
_KOTF_NEZN = object()      # parameter, ktorého hodnotu nevidno (mení sa vo funkcii)
KOTF_MAX = 64              # najviac zápisových požiadaviek (volanie urllib × volanie pomocníka); viac = rozbor po tokenoch


def _kotf_viazania(strom):
    """Koľkokrát je ktoré meno v kóde viazané (priradenie, parameter, def, import, for, with, except, global …);
    None = `import *` (viazať môže čokoľvek)."""
    n = collections.Counter()
    for u in ast.walk(strom):
        if isinstance(u, ast.Name) and isinstance(u.ctx, (ast.Store, ast.Del)):
            n[u.id] += 1
        elif isinstance(u, ast.arg):
            n[u.arg] += 1
        elif isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            n[u.name] += 1
        elif isinstance(u, ast.alias):
            if u.name == '*':
                return None
            n[(u.asname or u.name).split('.')[0]] += 1
        elif isinstance(u, (ast.Global, ast.Nonlocal)):
            for x in u.names:
                n[x] += 2
        elif isinstance(u, ast.ExceptHandler) and u.name:
            n[u.name] += 1
        elif type(u).__name__ in ('MatchAs', 'MatchStar') and getattr(u, 'name', None):
            n[u.name] += 1
        elif type(u).__name__ == 'MatchMapping' and getattr(u, 'rest', None):
            n[u.rest] += 1
    return n


def _kotf_param(u, env):
    """Uzol argumentu z volania pre meno parametra; _KOTF_NEZN = hodnotu nevidno; None = nie je to parameter."""
    return env.get(u.id) if isinstance(u, ast.Name) else None


def _kotf_text(u, env, konst):
    """Text výrazu (adresa): doslovný reťazec, f-reťazec, spojenie +, parameter z volania, konštanta modulu; čo sa
    prečítať nedá, je NEZNAME."""
    if isinstance(u, ast.Constant):
        return u.value if isinstance(u.value, str) else NEZNAME
    if isinstance(u, ast.JoinedStr):
        return ''.join(x.value if isinstance(x, ast.Constant) and isinstance(x.value, str) else NEZNAME
                       for x in u.values)
    if isinstance(u, ast.BinOp) and isinstance(u.op, ast.Add):
        return _kotf_text(u.left, env, konst) + _kotf_text(u.right, env, konst)
    v = _kotf_param(u, env)
    if v is _KOTF_NEZN:
        return NEZNAME
    if v is not None:
        return _kotf_text(v, {}, konst)
    return konst.get(u.id, NEZNAME) if isinstance(u, ast.Name) else NEZNAME


def _kotf_je_none(u, env, konst):
    """True = výraz je určite None (aj chýbajúci argument), False = určite nie je, None = nevedno."""
    if u is None:
        return True
    if isinstance(u, ast.Constant):
        return u.value is None
    if isinstance(u, (ast.Dict, ast.List, ast.Tuple, ast.Set, ast.JoinedStr, ast.ListComp, ast.SetComp, ast.DictComp)):
        return False
    if isinstance(u, ast.IfExp):
        p = _kotf_pravda(u.test, env, konst)
        if p is not None:
            return _kotf_je_none(u.body if p else u.orelse, env, konst)
        a, b = _kotf_je_none(u.body, env, konst), _kotf_je_none(u.orelse, env, konst)
        return a if a == b else None
    v = _kotf_param(u, env)
    if v is _KOTF_NEZN:
        return None
    if v is not None:
        return _kotf_je_none(v, {}, konst)
    return False if isinstance(u, ast.Name) and u.id in konst else None


def _kotf_pravda(u, env, konst):
    """Pravdivosť výrazu v podmienke: True, False alebo None (nevedno)."""
    if isinstance(u, ast.Constant):
        return bool(u.value)
    if isinstance(u, (ast.List, ast.Tuple, ast.Set)):
        return None if any(isinstance(x, ast.Starred) for x in u.elts) else bool(u.elts)
    if isinstance(u, ast.Dict):
        return None if any(k is None for k in u.keys) else bool(u.keys)
    if isinstance(u, ast.JoinedStr):
        return True if any(isinstance(x, ast.Constant) and x.value for x in u.values) else None
    if isinstance(u, ast.UnaryOp) and isinstance(u.op, ast.Not):
        p = _kotf_pravda(u.operand, env, konst)
        return None if p is None else not p
    if isinstance(u, ast.Compare) and len(u.ops) == 1 and isinstance(u.ops[0], (ast.Is, ast.IsNot)) \
            and isinstance(u.comparators[0], ast.Constant) and u.comparators[0].value is None:
        p = _kotf_je_none(u.left, env, konst)
        return None if p is None else (p if isinstance(u.ops[0], ast.Is) else not p)
    if isinstance(u, ast.BoolOp):
        hod = [_kotf_pravda(x, env, konst) for x in u.values]
        if isinstance(u.op, ast.And):
            return False if False in hod else (True if all(h is True for h in hod) else None)
        return True if True in hod else (False if all(h is False for h in hod) else None)
    v = _kotf_param(u, env)
    if v is _KOTF_NEZN:
        return None
    if v is not None:
        return _kotf_pravda(v, {}, konst)
    if isinstance(u, ast.Name) and NEZNAME not in konst.get(u.id, NEZNAME):
        return bool(konst[u.id])
    return None


def _kotf_metody(u, env, konst):
    """Metóda požiadavky: množina doslovných hodnôt (veľkými písmenami) alebo None (nevedno)."""
    if isinstance(u, ast.Constant):
        return {u.value.upper()} if isinstance(u.value, str) else None
    if isinstance(u, ast.IfExp):
        p = _kotf_pravda(u.test, env, konst)
        if p is not None:
            return _kotf_metody(u.body if p else u.orelse, env, konst)
        a, b = _kotf_metody(u.body, env, konst), _kotf_metody(u.orelse, env, konst)
        return a | b if a is not None and b is not None else None
    v = _kotf_param(u, env)
    if v is _KOTF_NEZN:
        return None
    if v is not None:
        return _kotf_metody(v, {}, konst)
    if isinstance(u, ast.Name) and NEZNAME not in konst.get(u.id, NEZNAME):
        return {konst[u.id].upper()}
    return None


def _kotf_kontexty(fn, strom, rodic, viaz, konst):
    """Volania pomocníka fn → zoznam prostredí {parameter: uzol hodnoty | _KOTF_NEZN}; None = posúdiť sa nedá."""
    a = fn.args
    if fn.decorator_list or a.vararg or a.kwarg or viaz[fn.name] != 1 or not isinstance(rodic.get(fn), ast.Module):
        return None
    if any(isinstance(x, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda, ast.ClassDef))
           for b in fn.body for x in ast.walk(b)):
        return None
    poz = [x.arg for x in a.posonlyargs + a.args]
    kwo = [x.arg for x in a.kwonlyargs]
    dflt = dict(zip(poz[len(poz) - len(a.defaults):], a.defaults))
    dflt.update({x.arg: d for x, d in zip(a.kwonlyargs, a.kw_defaults) if d is not None})
    menene = set()
    for b in fn.body:
        for x in ast.walk(b):
            if isinstance(x, ast.Name) and isinstance(x.ctx, (ast.Store, ast.Del)):
                menene.add(x.id)
            elif isinstance(x, (ast.Global, ast.Nonlocal)):
                menene.update(x.names)
    von = []
    for x in ast.walk(strom):
        if not (isinstance(x, ast.Name) and x.id == fn.name):
            continue
        c = rodic.get(x)
        if not (isinstance(c, ast.Call) and c.func is x):
            return None                       # meno funkcie ako hodnota — volanie cez iné meno nevidno
        if any(isinstance(y, ast.Starred) for y in c.args) or any(k.arg is None for k in c.keywords) \
                or len(c.args) > len(poz):
            return None
        env = dict(zip(poz, c.args))
        for k in c.keywords:
            if k.arg in env or k.arg not in poz + kwo:
                return None
            env[k.arg] = k.value
        for p in poz + kwo:
            if p not in env:
                if p not in dflt:
                    return None
                env[p] = dflt[p]
        for p in menene & set(env):
            env[p] = _KOTF_NEZN
        von.append(env)
        if len(von) > KOTF_MAX:
            return None
    return von or None


def _kotf_zapisy(strom):
    viaz = _kotf_viazania(strom)
    if viaz is None:
        return None
    rodic = {}
    for u in ast.walk(strom):
        for d in ast.iter_child_nodes(u):
            rodic[d] = u
    konst = {}
    for s in strom.body:
        if isinstance(s, ast.Assign) and len(s.targets) == 1 and isinstance(s.targets[0], ast.Name) \
                and viaz[s.targets[0].id] == 1 and (isinstance(s.value, ast.JoinedStr) or (
                    isinstance(s.value, ast.Constant) and isinstance(s.value.value, str))
                    or (isinstance(s.value, ast.BinOp) and isinstance(s.value.op, ast.Add))):
            konst[s.targets[0].id] = _kotf_text(s.value, {}, konst)
    von = []
    kontexty = {}
    for u in ast.walk(strom):
        if not isinstance(u, ast.Call):
            continue
        f = u.func
        meno = f.attr if isinstance(f, ast.Attribute) else (f.id if isinstance(f, ast.Name) else None)
        if meno not in ('Request', 'urlopen'):
            continue
        fn = rodic.get(u)
        while fn is not None and not isinstance(fn, (ast.Module, ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda,
                                                     ast.ClassDef)):
            fn = rodic.get(fn)
        if isinstance(fn, ast.Module):
            prost = [{}]
        elif isinstance(fn, (ast.FunctionDef, ast.AsyncFunctionDef)):
            if fn not in kontexty:
                kontexty[fn] = _kotf_kontexty(fn, strom, rodic, viaz, konst)
            prost = kontexty[fn]
            if prost is None:
                return None
        else:
            return None
        if any(isinstance(y, ast.Starred) for y in u.args) or any(k.arg is None for k in u.keywords):
            return None
        mena = ('url', 'data', 'headers', 'origin_req_host', 'unverifiable', 'method') if meno == 'Request' \
            else ('url', 'data', 'timeout')
        if len(u.args) > len(mena):
            return None
        arg = dict(zip(mena, u.args))
        for k in u.keywords:
            if k.arg in arg:
                return None
            arg[k.arg] = k.value
        if 'url' not in arg:
            return None
        for env in prost:
            bez_tela = _kotf_je_none(arg.get('data'), env, konst) is True
            if meno == 'Request' and 'method' in arg:
                ms = _kotf_metody(arg['method'], env, konst)
                if ms is not None and ms <= set(KOD_HTTP_CITA_METODY):
                    continue                  # čítanie (aj s telom, ako `method: 'GET'` pri rozbore po tokenoch)
                met = next((x for x in CUDZIA_ZAPIS if ms and x in ms), '?')
            elif bez_tela:
                continue                      # bez tela je urllib čítanie; urlopen(req) posúdi volanie Request
            else:
                met = 'POST'
            von.append((met, _kotf_text(arg['url'], env, konst)[:400]))
            if len(von) > KOTF_MAX:
                return None
    return tuple(von)


@_pamat
def py_pomocnik_zapisy(kd):
    """KOTF: zápisové požiadavky urllib v kóde napísanom v príkaze, každé volanie urllib pri každom volaní pomocnej funkcie
    → ((metóda, adresa), …) — adresa s NEZNAME tam, kde hodnotu nevidno; () = žiadny zápis; None = takto sa kód posúdiť
    nedá a platí rozbor po tokenoch."""
    if PY_SIET_DYNAMIKA.search(kd) or PY_SIET_GETATTR.search(kd):
        return None
    try:
        return _kotf_zapisy(ast.parse(kd))
    except (SyntaxError, ValueError, RecursionError, MemoryError, TypeError, AttributeError, KeyError, IndexError):
        return None


@_pamat
def py_modul_text(obs, nacitajuci, meno=None):
    """Text modulu na posúdenie: bez komentárov a bez toho, čo sa pri načítaní nespustí (py_modul_rozsah); ten istý modul
    načítaný tým istým kódom sa v jednom rozbore počíta raz."""
    return py_modul_rozsah(skript_kod(obs, 'py'), nacitajuci, meno)


# KMP (2, 16. 10.): náhrada siete modulu v načítajúcom kóde. Prehrávka 7 dní nad opravou KMP (1): Mariánove testy
# test_logika.py (aspecta-finops) načítajú ion.py (rozhranie TD Synnex) a jeho sieť nahradia — `ion._volaj = lambda …`
# (jediná funkcia, ktorá posiela), `ion.obnov, ion._stiahni_csv = (lambda: "access"), stiahni_csv`,
# `ion.HOST = f"http://127.0.0.1:{port}"` s vlastným serverom v teste; oprava (1) ich zastavila pri každom behu testov
# (64 príkazov za 7 dní). Teraz sa z textu modulu vynechá funkcia, ktorá posiela (kod_http_predbezne v jej tele), keď ju
# žiadne miesto načítajúceho nemôže spustiť: každé siahnutie na modul (m.f, getattr(m, 'f')), z ktorého by sa k nej
# dostalo, predchádza v tom istom bloku (alebo v bloku nad ním) náhrada funkcie na tej istej ceste toho istého modulu
# (m.f = lambda / vlastná funkcia / konštanta, ktoré na pôvodný kód modulu so sieťou nesiahajú) a medzi nimi sa meno
# modulu neprepíše. Premenná modulu s adresou (HOST = "https://…") sa v texte nahradí adresou vlastného počítača, keď
# každé siahnutie, z ktorého sa dá dostať k funkcii s touto premennou, predchádza `m.HOST = "http://127.0.0.1…"`
# (localhost, [::1]) — o adrese potom rozhodne rozbor ako pri každej inej. Nič sa nemení, keď: modul alebo načítajúci
# siaha na mená za behu (globals, getattr s menom z premennej, setattr, reload, eval …), načítajúci berie funkcie
# modulu cez `from m import`, odovzdá premennú modulu cudzej funkcii, kód modulu na najvyššej úrovni (alebo trieda)
# funkciu so sieťou použije, niektorá náhrada nie je platná (vrátenie pôvodnej `m.f = orig`, obal, ktorý pôvodnú volá,
# Mock(wraps=…), partial) — vtedy neplatí žiadna náhrada toho mena — alebo sa text nedá prečítať stromom. Zámerne
# opatrné: náhrada v podmienke, v inej funkcii, cez mock.patch alebo po siahnutí sieť nevypína.
PY_SIET_LOKALNA = re.compile(r'https?://(?:127\.0\.0\.1|localhost|\[::1\])(?=[:/]|$)', re.I)
PY_SIET_DYNAMIKA = re.compile(r'\b(?:globals|locals|vars|dir)\s*\(|__dict__|\bsys\.modules\b|\b(?:eval|exec)\s*\(|\bFire\s*\('
                              r'|\binspect\b|\b_getframe\b|\b__main__\b|\bimport_module\b|\b__import__\b|\bsetattr\s*\('
                              r'|\breload\s*\(|\b(?:attrgetter|methodcaller)\b')
PY_SIET_GETATTR = re.compile(r'''\bgetattr\s*\(\s*[^,()]+,\s*(?=[^\s'"])''')
# funkcia, ktorá posiela: okrem nálezu kod_http_predbezne aj volanie spojenia (len `urllib.parse.quote` či adresa v texte
# nie) — ostatné funkcie sa nevynechávajú nikdy a náhrada, ktorá ich volá, ostáva platná (o ich kóde rozhodne rozbor)
PY_SIET_POSIELA = re.compile(r'\b(?:urlopen|Request|HTTPS?Connection|create_connection|build_opener|Session|AsyncClient|Client'
                             r'|ClientSession|PoolManager|SMTP(?:_SSL)?)\s*\(|\.\s*(?:open|request|urlopen|get|post|put|patch'
                             r'|delete|head|options|send|sendall|sendmail|send_message|connect|stream|fetch)\s*\('
                             r'|\b(?:requests|httpx|aiohttp|urllib3|pycurl|subprocess|socket|system|popen)\b')


def _py_mena(u):
    return {n.id for n in ast.walk(u) if isinstance(n, ast.Name)}


def _py_koren(x):
    """Meno, z ktorého sa výraz `x` (základ m v m.f) odvíja: m, self.m → self; volanie či iný výraz → None."""
    while isinstance(x, (ast.Attribute, ast.Subscript)):
        x = x.value
    return x.id if isinstance(x, ast.Name) else None


def _py_viaze(u, meno):
    """Uzol (alebo niečo v ňom) viaže meno nanovo: priradenie, cyklus, with … as, parameter, import, except … as."""
    for n in ast.walk(u):
        if (isinstance(n, ast.Name) and n.id == meno and isinstance(n.ctx, (ast.Store, ast.Del))) \
                or (isinstance(n, ast.arg) and n.arg == meno) \
                or (isinstance(n, ast.alias) and (n.asname or n.name.split('.')[0]) == meno) \
                or (isinstance(n, (ast.Global, ast.Nonlocal)) and meno in n.names) \
                or (isinstance(n, ast.ExceptHandler) and n.name == meno):
            return True
    return False


# SKG (6. 11.; mlacena, aspecta-finops): test_logika.py si od 21. 10. odkladá a vracia nastavenia iného modulu —
# `povodne = {k: getattr(dashboard, k) for k in ("DATA", "HISTORIA", …)}` a vo `finally` `for k, v in povodne.items():
# setattr(dashboard, k, v)`. getattr s menom z premennej a setattr kdekoľvek v načítajúcom vypli posúdenie modulu ion.py
# (PY_MOD_DYNAMIKA — celý modul, PY_SIET_DYNAMIKA/GETATTR — bez náhrady siete), hoci test jeho sieť nahrádza
# (`ion._volaj = lambda …`, KMP 2): od 2. 11. 16:30 do 5. 11. stráž zastavila 21 behov testov, 5. 11. 12 (skody-kolegov).
# Teraz sa také volanie neráta ako siahnutie na mená za behu, keď je meno známe z textu a v module nie je: reťazec;
# premenná výrazu (generátor, zoznam …) nad zoznamom reťazcov; premenná cyklu `for k in ("a", …)`, `for k in D` /
# `D.keys()` / `for k, v in D.items()`, kde D je v tej istej funkcii priradené raz — `{k: … for k in ("a", …)}` alebo
# {"a": …} — a inak sa len číta (.items/.keys/.values/.get, cyklus). Každé také meno nesmie byť slovom textu modulu
# (funkcia, premenná, import, ani v komentári) a nezačína „__“ — getattr(x, meno) potom z modulu nič nedostane a setattr
# nič v ňom nenahradí ani nevráti. Inak (meno z argumentu, výpočtu, zmeneného slovníka, jediné nerozpoznané volanie)
# platí všetko ako doteraz; d_setattr a d_setattr_vratenie (meno "_volaj" je v module) zastavia ďalej.
PY_DYN_GS = re.compile(r'\b[gs]etattr\s*\(')
_PY_DYN_NACITA = frozenset(('items', 'keys', 'values', 'get'))


def py_dyn_staticka(nac, kod):
    """Text načítajúceho `nac`, v ktorom sú getattr/setattr so známym menom, ktoré modul `kod` nemá, premenované na
    GETATTR/SETATTR (rovnaká dĺžka — len pre kontroly mien za behu); pri čomkoľvek inom `nac` bez zmeny (SKG, vyššie)."""
    if not PY_DYN_GS.search(nac):
        return nac
    try:
        return _py_dyn_staticka(nac, kod)
    except Exception:           # _CasVyprsal je BaseException — budík prejde
        return nac


def _py_dyn_retazce(u):
    """Zoznam, n-tica alebo množina samých reťazcov → ich množina; inak None."""
    if isinstance(u, (ast.Tuple, ast.List, ast.Set)) and u.elts \
            and all(isinstance(e, ast.Constant) and isinstance(e.value, str) for e in u.elts):
        return {e.value for e in u.elts}
    return None


def _py_dyn_staticka(nac, kod):
    # SKG 2 (6. 11.): rozbor načítajúceho (strom, rozsahy, mená pri každom getattr/setattr) sa robí raz pre text, nie pre
    # každý modul — pri platnom texte test_logika.py (3 567 riadkov, 19 modulov) prekročil strop rozboru 6 s
    slova = set(re.findall(r'[A-Za-z_]\w*', kod))
    riadky = nac.split('\n')
    miesta = [(i, s, f) for i, s, f, m in _py_dyn_miesta(nac)
              if m and not any(not x.isidentifier() or x.startswith('__') or x in slova for x in m)]
    # premenuje sa len volanie, ktoré strom vidí; getattr/setattr v reťazci, komentári či inom tvare ostáva a kontroly
    # mien za behu ho posúdia ako doteraz
    for i, s, f in miesta:
        r = riadky[i]
        if r[s:s + len(f)] != f:
            return nac
        riadky[i] = r[:s] + f.upper() + r[s + len(f):]
    return '\n'.join(riadky)


@_pamat
def _py_dyn_miesta(nac):
    """Každé getattr/setattr načítajúceho, ktoré sa dá posúdiť: (riadok, stĺpec, meno funkcie, mená, ktoré môže mať —
    frozenset, alebo None = nevedno) — SKG/SKG 2."""
    # SKG 3 (b6, 8. 11.): strom zdieľaný s dosahom spúšťača (py_strom) a jeden prechod v poradí ast.walk namiesto troch;
    # mapa rodičov sa stavia, len keď niektoré posudzované volanie má meno v premennej (ng-cervene-testy.py s celým
    # textom: 134 507 uzlov, 2 volania, 0,45 → 0,1 s)
    tn = py_strom(nac)
    uzly, vonkajsie, volania, i = [tn], set(), [], 0
    while i < len(uzly):
        u = uzly[i]
        i += 1
        uzly.extend(ast.iter_child_nodes(u))
        if isinstance(u, ast.Call):
            if isinstance(u.func, ast.Name) and u.func.id in ('getattr', 'setattr'):
                volania.append(u)
        elif isinstance(u, (ast.Global, ast.Nonlocal)):
            vonkajsie.update(u.names)
    volania = [n for n in volania if not (n.keywords or any(isinstance(a, ast.Starred) for a in n.args)
                                         or len(n.args) not in ((2, 3) if n.func.id == 'getattr' else (3,))
                                         or n.func.id in vonkajsie)]
    rodic = {}
    if any(isinstance(n.args[1], ast.Name) for n in volania):
        for u in uzly:
            for x in ast.iter_child_nodes(u):
                rodic[x] = u
    del uzly
    rozsahy = (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda, ast.ClassDef, ast.Module)

    kluce_pamat = {}

    def kluce(d, rozsah):
        k = (d, id(rozsah))
        if k not in kluce_pamat:
            kluce_pamat[k] = _kluce(d, rozsah)
        return kluce_pamat[k]

    def _kluce(d, rozsah):
        """Kľúče slovníka v premennej d: v rozsahu jediné priradenie {k: … for k in (reťazce)} alebo {"a": …}, inak sa
        d len číta (.items/.keys/.values/.get, cyklus) → množina; inak None."""
        if d in vonkajsie:
            return None
        zdroj = None
        for n in ast.walk(rozsah):
            if not (isinstance(n, ast.Name) and n.id == d):
                continue
            p = rodic.get(n)
            if isinstance(n.ctx, ast.Store):
                if zdroj is not None or not (isinstance(p, ast.Assign) and p.targets == [n]):
                    return None
                zdroj = p.value
            elif isinstance(n.ctx, ast.Del):
                return None
            elif not ((isinstance(p, ast.Attribute) and p.attr in _PY_DYN_NACITA and isinstance(rodic.get(p), ast.Call)
                       and rodic[p].func is p) or (isinstance(p, ast.For) and p.iter is n)):
                return None
        if isinstance(zdroj, ast.Dict) and zdroj.keys and all(isinstance(k, ast.Constant) and isinstance(k.value, str)
                                                              for k in zdroj.keys):
            return {k.value for k in zdroj.keys}
        if isinstance(zdroj, ast.DictComp) and len(zdroj.generators) == 1 and isinstance(zdroj.key, ast.Name) \
                and isinstance(zdroj.generators[0].target, ast.Name) and zdroj.generators[0].target.id == zdroj.key.id:
            return _py_dyn_retazce(zdroj.generators[0].iter)
        return None

    def mena(u, volanie):
        """Mená, ktoré výraz u (meno v getattr/setattr) môže mať → množina; nevedno → None."""
        if isinstance(u, ast.Constant) and isinstance(u.value, str):
            return {u.value}
        if not isinstance(u, ast.Name):
            return None
        x, dole = u.id, volanie
        while True:
            hore = rodic.get(dole)
            if hore is None or isinstance(hore, rozsahy):
                return None
            if isinstance(hore, (ast.ListComp, ast.SetComp, ast.GeneratorExp, ast.DictComp)):
                for g in hore.generators:
                    if isinstance(g.target, ast.Name) and g.target.id == x:
                        return _py_dyn_retazce(g.iter)
                    if _py_viaze(g.target, x):
                        return None
            elif isinstance(hore, (ast.For, ast.AsyncFor)) and dole in hore.body and _py_viaze(hore.target, x):
                if any(_py_viaze(s, x) for s in hore.body):
                    return None
                rozsah = hore
                while not isinstance(rozsah, rozsahy):
                    rozsah = rodic[rozsah]
                t, it = hore.target, hore.iter
                if isinstance(t, ast.Name):
                    if isinstance(it, ast.Name):
                        return kluce(it.id, rozsah)
                    if isinstance(it, ast.Call) and not it.args and not it.keywords \
                            and isinstance(it.func, ast.Attribute) and it.func.attr == 'keys' \
                            and isinstance(it.func.value, ast.Name):
                        return kluce(it.func.value.id, rozsah)
                    return _py_dyn_retazce(it)
                if isinstance(t, ast.Tuple) and len(t.elts) == 2 and isinstance(t.elts[0], ast.Name) \
                        and t.elts[0].id == x and isinstance(t.elts[1], ast.Name) and t.elts[1].id != x \
                        and isinstance(it, ast.Call) and not it.args and not it.keywords \
                        and isinstance(it.func, ast.Attribute) and it.func.attr == 'items' \
                        and isinstance(it.func.value, ast.Name):
                    return kluce(it.func.value.id, rozsah)
                return None
            dole = hore

    riadky = nac.split('\n')
    von = []
    for n in volania:
        m = mena(n.args[1], n)
        r = riadky[n.func.lineno - 1]
        s = len(r.encode('utf-8')[:n.func.col_offset].decode('utf-8'))
        von.append((n.func.lineno - 1, s, n.func.id, frozenset(m) if m else None))   # bez mien ostáva, ako je
    return tuple(von)


def py_meno_modulu(f):
    """Meno, pod ktorým sa súbor modulu načíta: pkg/__init__.py → pkg, x.py → x (SKG)."""
    b = os.path.basename(f)
    if b == '__init__.py':
        return os.path.basename(os.path.dirname(f)) or None
    return os.path.splitext(b)[0] or None


# SKG (6. 11.): siahnutie `x.f` sa doteraz pripisovalo modulu podľa mena f, nech bolo x čokoľvek — test_logika.py volá
# spravy.main, dashboard.main, upozornenia.main a ip.main (ip = vlastná funkcia testu, ktorá načíta import-pouzivatelia.py),
# a keďže ion.py má tiež funkciu main, každé také volanie „dobehlo“ k jeho sieti a náhrada adresy (`ion.HOST =
# f"http://127.0.0.1:{port}"`) neplatila. Iný modul je premenná, ktorá sa v načítajúcom viaže LEN (a) holým `import x`,
# kde x nie je meno posudzovaného modulu, alebo (b) priradením `v = F(…)`, kde F je funkcia načítajúceho definovaná raz,
# ktorá načíta súbor cez spec_from_file_location s cestou zo samých reťazcov a pathlib/__file__ a súbor *.py v nej nemá
# meno posudzovaného modulu. Vynecháva sa len priame `x.f`; `x.ion._volaj` (modul cez atribút iného modulu) ostáva
# siahnutím. Ak by iný modul funkciu tohto modulu prevzal (from ion import main), posúdi sa ako jeho načítajúci zvlášť.
# Pri `meno` None (nevedno, ktorý súbor sa posudzuje) sa nevynecháva nič.
_PY_CESTA_MENA = frozenset(('pathlib', 'Path', 'PurePath', '__file__', 'os', 'path', 'resolve', 'parent', 'parents',
                            'join', 'dirname', 'abspath', 'realpath'))


def py_cudzie_moduly(tn, meno):
    """Mená premenných načítajúceho (strom tn), ktoré určite držia iný modul než `meno` → množina (SKG, vyššie)."""
    if not meno:
        return frozenset()
    vazby = collections.defaultdict(list)       # meno → [uzol, ktorý ho viaže]
    funkcie = collections.Counter()
    for u in ast.walk(tn):
        if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            funkcie[u.name] += 1
            vazby[u.name].append(u)
        if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda)):
            for a in ast.walk(u.args):
                if isinstance(a, ast.arg):
                    vazby[a.arg].append(a)
        elif isinstance(u, (ast.Import, ast.ImportFrom)):
            for a in u.names:
                vazby[a.asname or a.name.split('.')[0]].append((u, a))
        elif isinstance(u, ast.Name) and isinstance(u.ctx, (ast.Store, ast.Del)):
            vazby[u.id].append(u)
        elif isinstance(u, ast.ExceptHandler) and u.name:
            vazby[u.name].append(u)
        elif isinstance(u, (ast.Global, ast.Nonlocal)):
            for m in u.names:
                vazby[m].append(u)
    rodic = {}
    for u in ast.walk(tn):
        for x in ast.iter_child_nodes(u):
            rodic[x] = u

    def nacita_iny(f):
        """Funkcia f (definovaná raz) načíta súbor *.py s iným menom cez spec_from_file_location s cestou bez premenných."""
        if funkcie[f] != 1 or len(vazby[f]) != 1 or not isinstance(vazby[f][0], (ast.FunctionDef, ast.AsyncFunctionDef)):
            return False
        volania = [c for c in ast.walk(vazby[f][0]) if isinstance(c, ast.Call)
                   and ((isinstance(c.func, ast.Attribute) and c.func.attr == 'spec_from_file_location')
                        or (isinstance(c.func, ast.Name) and c.func.id == 'spec_from_file_location'))]
        if len(volania) != 1 or len(volania[0].args) < 2 or volania[0].keywords:
            return False
        cesta = volania[0].args[1]
        if any(isinstance(n, ast.Name) and n.id not in _PY_CESTA_MENA for n in ast.walk(cesta)):
            return False
        subory = [n.value for n in ast.walk(cesta) if isinstance(n, ast.Constant) and isinstance(n.value, str)
                  and n.value.endswith('.py')]
        return len(subory) == 1 and py_meno_modulu(subory[0]) not in (None, meno)

    von = set()
    for x, vv in vazby.items():
        if x == meno:
            continue
        if all(isinstance(v, tuple) and isinstance(v[0], ast.Import) and v[1].asname is None and v[1].name == x
               for v in vv):
            von.add(x)
        elif all(isinstance(v, ast.Name) and isinstance(v.ctx, ast.Store) and isinstance(rodic.get(v), ast.Assign)
                 and rodic[v].targets == [v] and isinstance(rodic[v].value, ast.Call)
                 and isinstance(rodic[v].value.func, ast.Name) and nacita_iny(rodic[v].value.func.id) for v in vv):
            von.add(x)
    return frozenset(von)


def py_nahrada_siete(kod, nacitajuci, cely=None, meno=None):
    """Text modulu `kod` (už bez toho, čo sa nespustí) bez funkcií so sieťou, ktoré načítajúci pred každým siahnutím
    nahradil, a s premennou adresy presmerovanou na vlastný počítač (KMP 2, vyššie). Pri čomkoľvek nečitateľnom `kod`.
    `cely` = celý text modulu pred vynechaním (SKG: mená getattr/setattr sa porovnávajú s ním, nie s výrezom); `meno` =
    meno modulu podľa súboru (SKG: `x.main` na inom module nie je siahnutie na tento; None = každé `.f` je siahnutie)."""
    try:
        return _py_nahrada_siete(kod, nacitajuci, kod if cely is None else cely, meno)
    except Exception:           # _CasVyprsal je BaseException — budík prejde
        return kod


def _py_nahrada_siete(kod, nac, cely, meno=None):
    if not any(kod_http_predbezne(kod)):
        return kod
    mena_mod = {a or b for a, b in re.findall(r'(?m)^(?:async[ \t]+)?def[ \t]+(\w+)|^([A-Za-z_]\w*)[ \t]*=(?!=)', kod)}
    if not (set(re.findall(r'\.[ \t]*([A-Za-z_]\w*)[ \t]*(?:,|=(?!=))', nac)) & mena_mod):
        return kod
    bez_ret = lambda s: re.sub(r'''(['"])(?:\\.|(?!\1)[^\\\n])*\1''', '""', s)
    nac_d = py_dyn_staticka(nac, cely)        # SKG: getattr/setattr so známym menom, ktoré modul nemá
    if PY_SIET_DYNAMIKA.search(bez_ret(kod)) or PY_SIET_GETATTR.search(kod) or PY_SIET_DYNAMIKA.search(bez_ret(nac_d)) \
            or PY_SIET_GETATTR.search(nac_d):
        return kod
    tm, tn = ast.parse(kod), ast.parse(nac)
    # --- modul: funkcie a triedy na najvyššej úrovni, čo sa spustí pri načítaní, funkcie so sieťou, premenné adresy
    uzly, nacitanie, pocet = {}, set(), collections.Counter()
    for s in tm.body:
        if isinstance(s, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            pocet[s.name] += 1
            uzly[s.name] = s
            hlava = list(s.decorator_list)
            if isinstance(s, ast.ClassDef):
                hlava += s.bases + [k.value for k in s.keywords]
                for b in s.body:
                    if isinstance(b, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        hlava += b.decorator_list + b.args.defaults + [d for d in b.args.kw_defaults if d is not None]
                    else:
                        hlava.append(b)
            else:
                hlava += s.args.defaults + [d for d in s.args.kw_defaults if d is not None]
            for h in hlava:
                nacitanie |= _py_mena(h)
        else:
            nacitanie |= _py_mena(s)
    if any(v > 1 for v in pocet.values()):
        return kod
    hrany = {m: _py_mena(u) & set(uzly) for m, u in uzly.items()}

    def dosah(od, rez=frozenset()):
        von, zas = set(), [x for x in od if x in uzly and x not in rez]
        while zas:
            x = zas.pop()
            if x not in von:
                von.add(x)
                zas += [y for y in hrany[x] if y not in rez and y not in von]
        return von
    siet = set()
    for m, u in uzly.items():
        if not isinstance(u, ast.ClassDef):
            seg = ast.get_source_segment(kod, u) or ''
            if any(kod_http_predbezne(seg)) and PY_SIET_POSIELA.search(seg):
                siet.add(m)
    if not siet:
        return kod
    pri_nacitani = dosah(nacitanie & set(uzly))
    adresy = {}                 # premenná s adresou → riadok priradenia
    for s in tm.body:
        if isinstance(s, ast.Assign) and len(s.targets) == 1 and isinstance(s.targets[0], ast.Name) \
                and isinstance(s.value, ast.Constant) and isinstance(s.value.value, str) \
                and KOD_HTTP_SCHEMA.search(s.value.value) and s.lineno == s.end_lineno:
            adresy[s.targets[0].id] = s.lineno
    for g in list(adresy):
        na_vrchu = sum(1 for s in tm.body if not isinstance(s, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef))
                       for n in ast.walk(s) if isinstance(n, ast.Name) and n.id == g)
        if na_vrchu != 1 or any(isinstance(n, (ast.Global, ast.Nonlocal)) and g in n.names for n in ast.walk(tm)):
            del adresy[g]
    # --- načítajúci: rodičia, funkcie, aliasy pôvodných funkcií modulu
    rodic = {}
    for u in ast.walk(tn):
        for pole, hod in ast.iter_fields(u):
            if isinstance(hod, list):
                for i, x in enumerate(hod):
                    if isinstance(x, ast.AST):
                        rodic[x] = (u, pole, i, hod)
            elif isinstance(hod, ast.AST):
                rodic[hod] = (u, pole, None, None)
    for u in ast.walk(tn):
        if isinstance(u, ast.ImportFrom) and any(a.name == '*' or a.name in uzly for a in u.names):
            return kod
    funkcie = collections.defaultdict(list)
    for u in ast.walk(tn):
        if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef)):
            funkcie[u.name].append(u)

    def priamo(u):
        """Funkcie modulu, na ktoré výraz siaha (m.f, getattr(m, 'f'))."""
        von = set()
        for n in ast.walk(u):
            if isinstance(n, ast.Attribute) and n.attr in uzly:
                von.add(n.attr)
            elif isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == 'getattr' \
                    and len(n.args) >= 2 and isinstance(n.args[1], ast.Constant) and n.args[1].value in uzly:
                von.add(n.args[1].value)
        return von
    def ako_hodnota(u):
        """Funkcie modulu vo výraze ako hodnota (orig = m.f, partial(m.f, …)), nie výsledok volania (x = m.f(…))."""
        von = set()
        for f in priamo(u):
            for n in ast.walk(u):
                if isinstance(n, ast.Attribute) and n.attr == f:
                    p, pole, _, _ = rodic.get(n, (None, None, None, None))
                    if not (isinstance(p, ast.Call) and pole == 'func'):
                        von.add(f)
                elif isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == 'getattr' \
                        and len(n.args) >= 2 and isinstance(n.args[1], ast.Constant) and n.args[1].value == f:
                    p, pole, _, _ = rodic.get(n, (None, None, None, None))
                    if not (isinstance(p, ast.Call) and pole == 'func'):
                        von.add(f)
        return von
    alias = collections.defaultdict(set)
    for u in ast.walk(tn):
        if isinstance(u, (ast.Assign, ast.AnnAssign, ast.NamedExpr)) and u.value is not None:
            p = ako_hodnota(u.value)
            if p:
                for t in (u.targets if isinstance(u, ast.Assign) else [u.target]):
                    for n in ast.walk(t):
                        if isinstance(n, ast.Name):
                            alias[n.id] |= p

    def siaha(u, videne=None):
        """Funkcie modulu, ktoré výraz môže spustiť: priamo, cez alias pôvodnej funkcie, cez vlastnú funkciu načítajúceho."""
        videne = set() if videne is None else videne
        von = priamo(u)
        for n in ast.walk(u):
            if isinstance(n, ast.Name):
                von |= alias.get(n.id, set())
                if n.id in funkcie and n.id not in videne:
                    videne.add(n.id)
                    for d in funkcie[n.id]:
                        von |= siaha(d, videne)
        return von

    def platna(hod, je_adresa):
        if je_adresa:
            if isinstance(hod, ast.Constant) and isinstance(hod.value, str):
                return bool(PY_SIET_LOKALNA.match(hod.value))
            return isinstance(hod, ast.JoinedStr) and bool(hod.values) and isinstance(hod.values[0], ast.Constant) \
                and isinstance(hod.values[0].value, str) and bool(PY_SIET_LOKALNA.match(hod.values[0].value))
        if isinstance(hod, ast.Constant):
            return True
        if isinstance(hod, ast.Lambda):
            telo = [hod]
        elif isinstance(hod, ast.Name) and hod.id in funkcie:
            telo = funkcie[hod.id]
        else:
            return False
        return not any(dosah(siaha(t)) & siet for t in telo)
    # --- náhrady: (základ, meno) → [priradenie]; neplatná náhrada kdekoľvek vypne meno všade
    nahrady, zle = collections.defaultdict(list), set()
    for u in ast.walk(tn):
        if isinstance(u, ast.Assign):
            for t in u.targets:
                ciele = t.elts if isinstance(t, (ast.Tuple, ast.List)) else [t]
                hodnoty = u.value.elts if isinstance(t, (ast.Tuple, ast.List)) and isinstance(u.value, (ast.Tuple, ast.List)) \
                    and len(u.value.elts) == len(ciele) else None
                for i, c in enumerate(ciele):
                    if isinstance(c, ast.Starred):
                        c = c.value
                    if not isinstance(c, ast.Attribute) or (c.attr not in uzly and c.attr not in adresy):
                        continue
                    hod = hodnoty[i] if hodnoty is not None else (u.value if len(ciele) == 1 else None)
                    if hod is None or not platna(hod, c.attr in adresy):
                        zle.add(c.attr)
                    else:
                        nahrady[(ast.dump(c.value), c.attr)].append(u)
        elif isinstance(u, (ast.AugAssign, ast.AnnAssign)) and isinstance(u.target, ast.Attribute):
            zle.add(u.target.attr)
    for (z, m) in list(nahrady):
        if m in zle:
            del nahrady[(z, m)]
    # premenná modulu odovzdaná ako hodnota inam než vlastnej funkcii (alebo návratom) — siahnutie by rozbor nevidel
    korene = set()
    for (z, m), rr in nahrady.items():
        for r in rr:
            for t in r.targets:
                for c in (t.elts if isinstance(t, (ast.Tuple, ast.List)) else [t]):
                    if isinstance(c, ast.Attribute) and c.attr == m:
                        korene.add(_py_koren(c.value))
    korene.discard(None)
    for n in ast.walk(tn):
        if isinstance(n, ast.Name) and n.id in korene and isinstance(n.ctx, ast.Load):
            p, pole, i, _ = rodic.get(n, (None, None, None, None))
            if isinstance(p, ast.Attribute) and pole == 'value':
                continue
            if isinstance(p, ast.Return):
                continue
            if isinstance(p, ast.Call) and pole == 'args' and isinstance(p.func, ast.Name) and p.func.id in funkcie:
                continue
            if isinstance(p, ast.Call) and pole == 'args' and isinstance(p.func, ast.Attribute) \
                    and p.func.attr == 'exec_module':
                continue            # spec.loader.exec_module(m) — samotné načítanie
            if isinstance(p, ast.Compare):
                continue
            return kod

    def retaz(u):
        """[(blok, index, zoznam príkazov)] od najvnútornejšieho, v ktorých uzol leží; blok = (id rodiča, pole)."""
        von, x = [], u
        while x in rodic:
            p, pole, i, zoznam = rodic[x]
            if i is not None and isinstance(x, (ast.stmt, ast.ExceptHandler)):
                von.append(((id(p), pole), i, zoznam))
            x = p
        return von

    def predbehne(r, e, koren):
        """Priradenie r sa určite vykoná pred siahnutím e a meno `koren` sa medzi nimi neviaže nanovo."""
        if koren is None:
            return False
        pr, pole_r, ir, _ = rodic[r]
        kluc = (id(pr), pole_r)
        rt = retaz(e)
        for k, (kl, ie, blok) in enumerate(rt):
            if kl != kluc:
                continue
            if ie <= ir:
                return False
            if any(_py_viaze(s, koren) for s in blok[ir + 1:ie]):
                return False
            for kl2, i2, blok2 in rt[:k]:
                if any(_py_viaze(s, koren) for s in blok2[:i2]):
                    return False
            x = e
            while x is not blok[ie]:
                x = rodic[x][0]
                if isinstance(x, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda)) and _py_viaze(x.args, koren):
                    return False
                if isinstance(x, (ast.For, ast.AsyncFor)) and _py_viaze(x.target, koren):
                    return False
                if isinstance(x, (ast.With, ast.AsyncWith)) and any(i.optional_vars is not None
                                                                    and _py_viaze(i.optional_vars, koren) for i in x.items):
                    return False
                if isinstance(x, ast.ExceptHandler) and x.name == koren:
                    return False
                if isinstance(x, (ast.ListComp, ast.SetComp, ast.DictComp, ast.GeneratorExp)) \
                        and any(_py_viaze(g.target, koren) for g in x.generators):
                    return False
            return True
        return False
    # --- siahnutia načítajúceho na modul a čo z nich môže dobehnúť
    siahnutia = []
    cudzie = py_cudzie_moduly(tn, meno)       # SKG: `spravy.main` pri `import spravy` nie je siahnutie na ion.main
    for n in ast.walk(tn):
        if isinstance(n, ast.Attribute) and isinstance(n.ctx, ast.Load) and n.attr in uzly:
            if isinstance(n.value, ast.Name) and n.value.id in cudzie:
                continue
            siahnutia.append((n, n.value, n.attr))
        elif isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id == 'getattr' and len(n.args) >= 2 \
                and isinstance(n.args[1], ast.Constant) and n.args[1].value in uzly:
            siahnutia.append((n, n.args[0], n.args[1].value))
    dosiahnute = set(pri_nacitani)
    presmerovane_vsade = {g: True for g in adresy}
    pouziva = {g: {m for m, u in uzly.items() if g in _py_mena(u)} for g in adresy}
    for g in adresy:
        if pouziva[g] & pri_nacitani:
            presmerovane_vsade[g] = False
    for e, zaklad, meno in siahnutia:
        z = ast.dump(zaklad)
        koren = _py_koren(zaklad)
        rez = frozenset(m for (zz, m), rr in nahrady.items() if zz == z and m in uzly
                        and any(predbehne(r, e, koren) for r in rr))
        d = dosah([meno], rez)
        dosiahnute |= d
        for g in adresy:
            if presmerovane_vsade[g] and d & pouziva[g] \
                    and not any(predbehne(r, e, koren) for r in nahrady.get((z, g), ())):
                presmerovane_vsade[g] = False
    vynechat = siet - dosiahnute
    presmerovat = {g for g, ok in presmerovane_vsade.items() if ok and g not in zle and any(z[1] == g for z in nahrady)}
    if not vynechat and not presmerovat:
        return kod
    riadky = kod.split('\n')
    for m in vynechat:
        u = uzly[m]
        od = min([u.lineno] + [d.lineno for d in u.decorator_list])
        for i in range(od - 1, u.end_lineno):
            riadky[i] = ''
    for g in presmerovat:
        riadky[adresy[g] - 1] = f'{g} = "http://127.0.0.1"'
    return '\n'.join(riadky)


py_nahrada_siete = _pamat(py_nahrada_siete)


# ---------- vetvy, ktoré vyvolanie skriptu nespustí (2. 10. večer; oprava úlohy K po nezávislom overení) ----------
# Overenie 2. 10. 22:39: výnimka „cesta bez zápisu“ vynechávala CELÝ skript, keď vyvolanie obsahovalo slovo, ktoré skript
# niekde „poznal“ (read z `IFS= read`, h z textu „~1 h)“, status ako kľúč v JSON, --report, ktorý mail naopak pošle …) —
# jedno slovo navyše vyplo zastavenie. Teraz žiadne slovo samo nič nevypína: kotva skript „prejde“ s argumentmi vyvolania
# (abstraktný beh: známe sú len argumenty, priradenia v skripte a premenné pred príkazom; všetko ostatné je neznáme) a
# vynechá len to, čo sa pri tomto vyvolaní určite nespustí — vetvu if/case/&&/||, ktorej podmienka je pri týchto
# argumentoch určite nepravdivá, a zvyšok po určitom exit/return/break/continue. Slovo zmení výsledok, len keď skript
# podľa neho naozaj vetví a krok s kanálom je v tej vetve. Čo kotva nevie prečítať (neznáma hodnota, eval, source, priveľa
# krokov, neznáma štruktúra), sa posudzuje celé — vynechanie je len bezpečným smerom (zastaviť radšej navyše).

SH_ROZDEL = type('ArgRozdel', (), {'__repr__': lambda self: 'ROZDEL'})()   # argument s neznámym počtom slov


class _Nevie(Exception):
    """Štruktúru alebo beh skriptu kotva nevie spoľahlivo prečítať — nevynechá sa nič."""


SH_KROKY = 40000            # krokov abstraktného behu na jeden skript (potom sa nevynechá nič)
# 3. 10. (overenie kola 1, rozdielová skúška proti bashu): premenné, ktoré shell mení sám (RANDOM, SECONDS, REPLY po read,
# OPTARG po getopts, BASH_REMATCH po [[ =~ ]] …), sú vždy neznáme — aj keď ich skript predtým priradil
SH_DYNAMICKE = {'RANDOM', 'SRANDOM', 'SECONDS', 'LINENO', 'BASHPID', 'EPOCHSECONDS', 'EPOCHREALTIME', 'BASH_COMMAND',
                'FUNCNAME', 'BASH_LINENO', 'BASH_SOURCE', 'BASH_ARGV', 'BASH_ARGC', 'BASH_ARGV0', 'PIPESTATUS',
                'BASH_REMATCH', 'REPLY', 'OPTARG', 'OPTIND', 'OPTERR', 'MAPFILE', 'HISTCMD', 'PWD', 'OLDPWD', 'DIRSTACK',
                'GROUPS', 'PPID', 'UID', 'EUID', 'HOSTNAME', 'SHLVL', 'COPROC', 'BASH_SUBSHELL'}
# príkazy, ktoré abstraktný beh vyhodnocuje sám; funkcia s takým menom ich prebije (v bashi má funkcia prednosť aj pred
# exit) — skript s takou funkciou sa nevynecháva, po source/eval (cudzie) môže byť funkciou ktorýkoľvek z nich
SH_VSTAVANE_BEH = {'exit', 'return', 'break', 'continue', 'true', 'false', ':', '[', 'test', 'shift', 'set', 'local',
                   'declare', 'typeset', 'export', 'readonly', 'unset', 'read', 'mapfile', 'readarray', 'getopts', 'printf',
                   'let', 'wait', 'select', 'eval', 'source', '.', 'shopt', 'alias', 'trap', 'command', 'builtin', 'enable'}
# shopt/set, ktoré menia porovnávanie, rúru či aliasy (nocasematch: case bez ohľadu na veľkosť písmen; lastpipe: read v rúre
# priradí v hlavnom shelli; expand_aliases a posix: alias prepíše príkaz) — abstraktný beh ich nemodeluje, nevynecháva nič
SH_VOLBY_ZLE = {'nocasematch', 'lastpipe', 'expand_aliases', 'posix'}
SH_OPAKOVANIA = 64          # priechodov cyklom so známymi hodnotami (potom cyklus „hocikoľko krát“)
SH_VNORENIE = 8             # vnorenie volaní funkcií
SH_OP = (';;&', ';;', ';&', '&&', '||', '|&', ';', '&', '|', '(', ')')
SH_PRESM = re.compile(r'(\d+)?(&>>|&>|>>|>&|>\||<<<|<<-|<<|<>|<&|>|<)')
SH_GLOB = re.compile(r'[*?]|\[[^]]*\]')     # vzor mien súborov (samotná „[“ je obyčajný znak)
SH_PO_KTOROM_PRIKAZ = {'then', 'do', 'else', 'elif', 'if', 'while', 'until', '{', '!', 'time', '}'}


def sh_dvojite(s, i):
    """Index za zatváracou " (i = za otváracou)."""
    n = len(s)
    while i < n:
        c = s[i]
        if c == '\\':
            i += 2
            continue
        if c == '"':
            return i + 1
        if c == '`':
            i = sh_spatne(s, i + 1)
            continue
        if c == '$':
            i = sh_dolar(s, i)
            continue
        i += 1
    raise _Nevie('neukončené "')


def sh_spatne(s, i):
    n = len(s)
    while i < n:
        if s[i] == '\\':
            i += 2
            continue
        if s[i] == '`':
            return i + 1
        i += 1
    raise _Nevie('neukončený `')


def sh_zlozena(s, i):
    """Index za } pre ${…} (i = za ${)."""
    n = len(s)
    h = 1
    while i < n:
        c = s[i]
        if c == '\\':
            i += 2
            continue
        if c == "'":
            j = s.find("'", i + 1)
            if j < 0:
                raise _Nevie("neukončené '")
            i = j + 1
            continue
        if c == '"':
            i = sh_dvojite(s, i + 1)
            continue
        if c == '$':
            i = sh_dolar(s, i)
            continue
        if c == '{':
            h += 1
        elif c == '}':
            h -= 1
            if h == 0:
                return i + 1
        i += 1
    raise _Nevie('neukončené ${')


def sh_aritm(s, i):
    """Index za )) pre $(( … )) alebo (( … )) (i = na prvej zátvorke)."""
    n = len(s)
    h = 0
    while i < n:
        c = s[i]
        if c == '\\':
            i += 2
            continue
        if c in '\'"':
            j = s.find(c, i + 1)
            if j < 0:
                raise _Nevie('neukončené úvodzovky')
            i = j + 1
            continue
        if c == '(':
            h += 1
        elif c == ')':
            h -= 1
            if h == 0:
                return i + 1
        i += 1
    raise _Nevie('neukončené ((')


def sh_dolar(s, i):
    """Index za rozvinutím, ktoré sa začína na s[i] == '$'."""
    if s.startswith('$((', i):
        return sh_aritm(s, i + 1)
    if s.startswith('$(', i):
        return sh_tokeny(s, i + 2, ')')[1]
    if s.startswith('${', i):
        return sh_zlozena(s, i + 2)
    if s.startswith("$'", i):
        j = i + 2
        while j < len(s):
            if s[j] == '\\':
                j += 2
                continue
            if s[j] == "'":
                return j + 1
            j += 1
        raise _Nevie("neukončené $'")
    return i + 1


def sh_dolar_cely(s, i):
    """Index za rozvinutím vrátane mena ($meno, $1, $#, $@ …)."""
    j = sh_dolar(s, i)
    if j == i + 1 and j < len(s):
        m = re.compile(r'[A-Za-z_][A-Za-z0-9_]*|[0-9#@*?$!-]').match(s, j)
        if m:
            return m.end()
    return j


def sh_koniec_slova(s, i, test=False):
    """Index za slovom shellu, ktoré sa začína na s[i]. test: vnútri [[ ]] sú < > ( ) operátory."""
    n = len(s)
    zac = i
    while i < n:
        c = s[i]
        if c in ' \t\r\n;&|':
            break
        if c in '<>':
            if not test and i + 1 < n and s[i + 1] == '(' and i == zac:     # <( … ) >( … )
                i = sh_tokeny(s, i + 2, ')')[1]
                continue
            break
        if c == '(':
            if i > zac and s[i - 1] in '@!+*?':          # extglob @( … )
                i = sh_aritm(s, i)
                continue
            if i > zac and re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*(?:\[[^]]*\])?\+?=', s[zac:i]):   # pole a=( … )
                i = sh_aritm(s, i)
                continue
            break
        if c == ')':
            break
        if c == '\\':
            i += 2
            continue
        if c == "'":
            j = s.find("'", i + 1)
            if j < 0:
                raise _Nevie("neukončené '")
            i = j + 1
            continue
        if c == '"':
            i = sh_dvojite(s, i + 1)
            continue
        if c == '`':
            i = sh_spatne(s, i + 1)
            continue
        if c == '$':
            i = sh_dolar(s, i)
            continue
        i += 1
    return min(i, n)


def sh_heredocy(s, i, cakajuce):
    """Telá heredocov za koncom riadku: každému čakajúcemu presmerovaniu nastaví (od, do) tela; vráti index za nimi."""
    n = len(s)
    for tk, delim, tabs in cakajuce:
        od = i
        while i < n:
            j = s.find('\n', i)
            riadok = s[i:] if j < 0 else s[i:j]
            i = n if j < 0 else j + 1
            if (riadok.lstrip('\t') if tabs else riadok) == delim:
                break
        tk[4] = (od, i)
    cakajuce.clear()
    return i


def sh_tokeny(s, i=0, koniec=None):
    """Tokeny shellu: [druh, text, od, do, navyše]; druh 'w' slovo · 'op' operátor (aj 'nl') · 'r' presmerovanie s cieľom
    (navyše = (od, do) tela heredocu) · 'a' (( … )) · 't' [[ … ]]. koniec=')': $( … ) — vráti (tokeny, index za ')').
    V $( … ) sa sleduje case … esac (vzor „x)“ nezatvára náhradu) a vnorené zátvorky."""
    n = len(s)
    tok = []
    cakajuce = []
    zac_prikazu = True
    hl_par = 0
    hl_case = 0
    vzor = False         # v case: ďalšie slovo je vzor (za „in“ alebo za ;;)
    while i < n:
        c = s[i]
        if c in ' \t\r':
            i += 1
            continue
        if c == '\\' and i + 1 < n and s[i + 1] == '\n':
            i += 2
            continue
        if c == '\n':
            tok.append(['op', 'nl', i, i + 1, None])
            i += 1
            if cakajuce:
                i = sh_heredocy(s, i, cakajuce)
            zac_prikazu = True
            continue
        if c == '#':
            j = s.find('\n', i)
            i = n if j < 0 else j
            continue
        m = SH_PRESM.match(s, i)
        if m and not (m.group(2) in ('<', '>') and i + len(m.group()) < n and s[i + len(m.group())] == '('
                      and not m.group(1)):
            if m.group(1) is None or m.end(1) == i + len(m.group(1)):
                op = m.group(2)
                j = m.end()
                while j < n and s[j] in ' \t':
                    j += 1
                if op in ('>&', '<&'):
                    m2 = re.compile(r'\d+-?|-').match(s, j)
                    if m2 and (m2.end() >= n or s[m2.end()] in ' \t\n;&|)<>'):
                        tok.append(['r', s[i:m2.end()], i, m2.end(), None])
                        i = m2.end()
                        continue
                if j >= n or (s[j] in '\n;&|()<>' and not (s[j] in '<>' and s.startswith('(', j + 1))):
                    raise _Nevie('presmerovanie bez cieľa')     # cieľ môže byť aj náhrada procesu: done < <(…)
                k = sh_koniec_slova(s, j)
                t = ['r', s[i:k], i, k, None]
                tok.append(t)
                if op in ('<<', '<<-'):
                    d = s[j:k]
                    delim = re.sub(r'''['"\\]''', '', d)
                    cakajuce.append((t, delim, op == '<<-'))
                i = k
                continue
        if c in ';&|()':
            op = next(o for o in SH_OP if s.startswith(o, i))
            if koniec == ')' and op == ')' and hl_par == 0 and not (hl_case and vzor):
                return tok, i + 1
            if op == '(':
                if (zac_prikazu or (tok and tok[-1][0] == 'w' and tok[-1][1] == 'for')) and s.startswith('((', i):
                    k = sh_aritm(s, i)
                    tok.append(['a', s[i:k], i, k, None])
                    i = k
                    zac_prikazu = False
                    continue
                if not (hl_case and vzor):
                    hl_par += 1
            elif op == ')':
                if hl_case and vzor:
                    vzor = False
                elif hl_par:
                    hl_par -= 1
            elif op in (';;', ';&', ';;&') and hl_case:
                vzor = True
            tok.append(['op', op, i, i + len(op), None])
            i += len(op)
            zac_prikazu = True
            continue
        if zac_prikazu and s.startswith('[[', i) and (i + 2 >= n or s[i + 2] in ' \t\n'):
            j = i + 2
            while True:
                while j < n and s[j] in ' \t\r\n':
                    j += 1
                if j >= n:
                    raise _Nevie('neukončené [[')
                if s.startswith(']]', j) and (j + 2 >= n or s[j + 2] in ' \t\n;&|)'):
                    j += 2
                    break
                if s[j] in '()<>' or s.startswith(('&&', '||'), j):
                    j += 2 if s.startswith(('&&', '||'), j) else 1
                    continue
                k = sh_koniec_slova(s, j, test=True)
                j = k if k > j else j + 1
            tok.append(['t', s[i:j], i, j, None])
            i = j
            zac_prikazu = False
            continue
        k = sh_koniec_slova(s, i)
        if k == i:
            raise _Nevie('neznámy znak')
        w = s[i:k]
        tok.append(['w', w, i, k, None])
        i = k
        if koniec == ')':
            if zac_prikazu and w == 'case':
                hl_case += 1
            elif w == 'in' and hl_case and not vzor and len(tok) >= 3 and tok[-3][1] == 'case':
                vzor = True
            elif zac_prikazu and w == 'esac' and hl_case:
                hl_case -= 1
                vzor = False
        zac_prikazu = zac_prikazu and w in SH_PO_KTOROM_PRIKAZ
    if koniec == ')':
        raise _Nevie('neukončené $(')
    if cakajuce:
        sh_heredocy(s, n, cakajuce)
    return tok, n


class ShStrom:
    """Štruktúra skriptu (zoznamy, &&/||, rúry, if/case/for/while/until/select, { }, ( ), funkcie) s rozsahmi v texte.
    Neočakávaný token = _Nevie (nevynechá sa nič)."""

    def __init__(self, s):
        self.s = s
        self.t = sh_tokeny(s)[0]
        self.p = 0
        self.funkcie = {}          # meno → [uzly definície]
        self.volania = {}          # meno → počet priamych volaní v strome
        self.koren = self.zoznam(set())
        if self.p != len(self.t):
            raise _Nevie('zvyšok za koncom skriptu')

    def kuk(self):
        return self.t[self.p] if self.p < len(self.t) else None

    def je(self, x, druh, text=None):
        return x is not None and x[0] == druh and (text is None or x[1] == text)

    def nl(self):
        while self.p < len(self.t) and self.t[self.p][0] == 'op' and self.t[self.p][1] == 'nl':
            self.p += 1

    def cakaj(self, druh, text):
        x = self.kuk()
        if not self.je(x, druh, text):
            raise _Nevie(f'čakal som {text}')
        self.p += 1
        return x

    def koniec_zoznamu(self, x, konce):
        return x is None or (x[0] in ('w', 'op') and x[1] in konce)

    def zoznam(self, konce):
        pol = []
        od = None
        do = None
        while True:
            while self.p < len(self.t) and self.t[self.p][0] == 'op' and self.t[self.p][1] in ('nl', ';'):
                self.p += 1
            x = self.kuk()
            if self.koniec_zoznamu(x, konce):
                break
            if x[0] == 'op' and x[1] not in ('(', '!'):
                raise _Nevie(f'neočakávaný {x[1]}')
            ao = self.and_or()
            od = ao['od'] if od is None else od
            do = ao['do']
            y = self.kuk()
            if self.je(y, 'op') and y[1] in (';', '&', 'nl'):
                ao['bg'] = y[1] == '&'
                self.p += 1
            elif not self.koniec_zoznamu(y, konce):
                raise _Nevie('chýba oddeľovač')
            pol.append(ao)
        return {'typ': 'zoznam', 'pol': pol, 'od': od, 'do': do}

    def and_or(self):
        prvky = [(None, self.ruro())]
        while True:
            x = self.kuk()
            if self.je(x, 'op') and x[1] in ('&&', '||'):
                self.p += 1
                self.nl()
                prvky.append((x[1], self.ruro()))
            else:
                break
        return {'typ': 'andor', 'prvky': prvky, 'bg': False, 'od': prvky[0][1]['od'], 'do': prvky[-1][1]['do']}

    def ruro(self):
        x = self.kuk()
        od = x[2]
        neg = False
        while self.je(x, 'w', '!') or self.je(x, 'w', 'time'):
            neg ^= x[1] == '!'
            self.p += 1
            x = self.kuk()
            while x is not None and self.je(x, 'w') and x[1] in ('-p', '--'):
                self.p += 1
                x = self.kuk()
        prik = [self.prikaz()]
        while True:
            x = self.kuk()
            if self.je(x, 'op') and x[1] in ('|', '|&'):
                self.p += 1
                self.nl()
                prik.append(self.prikaz())
            else:
                break
        return {'typ': 'ruro', 'neg': neg, 'prik': prik, 'od': od, 'do': prik[-1]['do']}

    def za_zlozenym(self, uzol):
        hd = []
        while self.je(self.kuk(), 'r'):
            x = self.kuk()
            if x[4]:
                hd.append(x[4])
            uzol['do'] = x[3]
            self.p += 1
        uzol['hd'] = hd
        return uzol

    def prikaz(self):
        x = self.kuk()
        if x is None:
            raise _Nevie('chýba príkaz')
        if self.je(x, 'op', '('):
            self.p += 1
            telo = self.zoznam({')'})
            y = self.cakaj('op', ')')
            return self.za_zlozenym({'typ': 'pod', 'telo': telo, 'od': x[2], 'do': y[3]})
        if x[0] in ('a', 't'):
            self.p += 1
            return self.za_zlozenym({'typ': 'aritm' if x[0] == 'a' else 'test2', 'text': x[1], 'od': x[2], 'do': x[3]})
        if x[0] == 'w':
            w = x[1]
            if w == 'if':
                return self.if_()
            if w == 'case':
                return self.case_()
            if w in ('for', 'select'):
                return self.for_()
            if w in ('while', 'until'):
                return self.while_()
            if w == '{':
                self.p += 1
                telo = self.zoznam({'}'})
                y = self.cakaj('w', '}')
                return self.za_zlozenym({'typ': 'blok', 'telo': telo, 'od': x[2], 'do': y[3]})
            if w == 'function':
                self.p += 1
                y = self.kuk()
                if not self.je(y, 'w'):
                    raise _Nevie('function bez mena')
                self.p += 1
                if self.je(self.kuk(), 'op', '('):
                    self.p += 1
                    self.cakaj('op', ')')
                return self.funkcia(y[1], x[2])
            if w in ('then', 'elif', 'else', 'fi', 'do', 'done', 'esac', '}', 'in', ';;'):
                raise _Nevie(f'neočakávané {w}')
            if self.p + 2 < len(self.t) and self.je(self.t[self.p + 1], 'op', '(') \
                    and self.je(self.t[self.p + 2], 'op', ')'):
                if not re.fullmatch(r'[A-Za-z_][\w.:-]*', w):
                    raise _Nevie('zvláštne meno funkcie')
                self.p += 3
                return self.funkcia(w, x[2])
        return self.jednoduchy()

    def funkcia(self, meno, od):
        self.nl()
        telo = self.prikaz()
        if telo['typ'] not in ('blok', 'pod', 'if', 'case', 'for', 'while'):
            raise _Nevie('telo funkcie')
        u = {'typ': 'fun', 'meno': meno, 'telo': telo, 'od': od, 'do': telo['do'], 'hd': []}
        self.funkcie.setdefault(meno, []).append(u)
        return u

    def jednoduchy(self):
        slova = []
        presm = []
        hd = []
        od = self.kuk()[2]
        do = od
        while self.p < len(self.t):
            x = self.t[self.p]
            if x[0] == 'w':
                slova.append(x)
            elif x[0] == 'r':
                presm.append(x)
                if x[4]:
                    hd.append(x[4])
            else:
                break
            do = x[3]
            self.p += 1
        if not slova and not presm:
            raise _Nevie('prázdny príkaz')
        i = 0
        while i < len(slova) and SH_PRIRADENIE.match(slova[i][1]):
            i += 1
        if i < len(slova) and re.fullmatch(r'[A-Za-z_][\w.:-]*', slova[i][1]):
            self.volania[slova[i][1]] = self.volania.get(slova[i][1], 0) + 1
        return {'typ': 'cmd', 'slova': slova, 'presm': presm, 'hd': hd, 'od': od, 'do': do}

    def if_(self):
        od = self.kuk()[2]
        self.p += 1
        vetvy = []
        podm = self.zoznam({'then'})
        self.cakaj('w', 'then')
        vetvy.append((podm, self.zoznam({'elif', 'else', 'fi'})))
        inak = None
        while True:
            x = self.kuk()
            if self.je(x, 'w', 'elif'):
                self.p += 1
                podm = self.zoznam({'then'})
                self.cakaj('w', 'then')
                vetvy.append((podm, self.zoznam({'elif', 'else', 'fi'})))
            elif self.je(x, 'w', 'else'):
                self.p += 1
                inak = self.zoznam({'fi'})
            elif self.je(x, 'w', 'fi'):
                self.p += 1
                return self.za_zlozenym({'typ': 'if', 'vetvy': vetvy, 'inak': inak, 'od': od, 'do': x[3]})
            else:
                raise _Nevie('if bez fi')

    def case_(self):
        od = self.kuk()[2]
        self.p += 1
        slovo = self.kuk()
        if not self.je(slovo, 'w'):
            raise _Nevie('case bez slova')
        self.p += 1
        self.nl()
        self.cakaj('w', 'in')
        vetvy = []
        while True:
            self.nl()
            x = self.kuk()
            if x is None:
                raise _Nevie('case bez esac')
            if self.je(x, 'w', 'esac'):
                self.p += 1
                return self.za_zlozenym({'typ': 'case', 'slovo': slovo, 'vetvy': vetvy, 'od': od, 'do': x[3]})
            if self.je(x, 'op', '('):
                self.p += 1
            vzory = []
            while True:
                y = self.kuk()
                if not self.je(y, 'w'):
                    raise _Nevie('vzor case')
                vzory.append(y)
                self.p += 1
                y = self.kuk()
                if self.je(y, 'op', '|'):
                    self.p += 1
                    continue
                self.cakaj('op', ')')
                break
            telo = self.zoznam({';;', ';&', ';;&', 'esac'})
            y = self.kuk()
            konc = ';;'
            if self.je(y, 'op') and y[1] in (';;', ';&', ';;&'):
                konc = y[1]
                self.p += 1
            vetvy.append((vzory, telo, konc))

    def for_(self):
        x = self.kuk()
        od = x[2]
        self.p += 1
        y = self.kuk()
        if self.je(y, 'a'):                    # for (( … ; … ; … ))
            self.p += 1
            while self.je(self.kuk(), 'op') and self.kuk()[1] in (';', 'nl'):
                self.p += 1
            self.cakaj('w', 'do')
            telo = self.zoznam({'done'})
            z = self.cakaj('w', 'done')
            return self.za_zlozenym({'typ': 'cyklus', 'podm': None, 'telo': telo, 'od': od, 'do': z[3], 'aritm': y[1]})
        if not self.je(y, 'w'):
            raise _Nevie('for bez mena')
        meno = y[1]
        self.p += 1
        self.nl()
        slova = None
        if self.je(self.kuk(), 'w', 'in'):
            self.p += 1
            slova = []
            while self.je(self.kuk(), 'w'):
                slova.append(self.kuk())
                self.p += 1
        if self.je(self.kuk(), 'op') and self.kuk()[1] in (';', 'nl'):
            self.p += 1
        self.nl()
        self.cakaj('w', 'do')
        telo = self.zoznam({'done'})
        z = self.cakaj('w', 'done')
        return self.za_zlozenym({'typ': 'for', 'select': x[1] == 'select', 'meno': meno, 'slova': slova, 'telo': telo,
                                 'od': od, 'do': z[3]})

    def while_(self):
        x = self.kuk()
        self.p += 1
        podm = self.zoznam({'do'})
        self.cakaj('w', 'do')
        telo = self.zoznam({'done'})
        z = self.cakaj('w', 'done')
        return self.za_zlozenym({'typ': 'cyklus', 'until': x[1] == 'until', 'podm': podm, 'telo': telo, 'od': x[2],
                                 'do': z[3]})


class ShStav:
    """Stav abstraktného behu: známe premenné (chýba = neznáma), známe argumenty (None = neznáme), funkcie (None = možno
    definovaná, neznámy účinok), cudzie = po source/eval môže byť hocijaký príkaz funkcia, ktorá mení premenné."""
    __slots__ = ('v', 'poz', 'fun', 'cudzie')

    def __init__(self, v=None, poz=None, fun=None, cudzie=False):
        self.v = v if v is not None else {}
        self.poz = poz
        self.fun = fun if fun is not None else {}
        self.cudzie = cudzie

    def kopia(self):
        return ShStav(dict(self.v), None if self.poz is None else list(self.poz), dict(self.fun), self.cudzie)

    def nezname(self):
        """Všetko neznáme (source, eval, neznáma funkcia)."""
        st = self.kopia()
        st.v.clear()
        st.poz = None
        st.fun = {k: None for k in st.fun}
        return st


def sh_spoj(*stavy):
    """Stav, ktorý platí po ktorejkoľvek z ciest: zhodné hodnoty ostanú, ostatné sú neznáme."""
    st = [x for x in stavy if x is not None]
    if not st:
        return None
    if len(st) == 1:
        return st[0]
    a = st[0]
    v = {k: x for k, x in a.v.items() if all(b.v.get(k, _NEZ) == x for b in st[1:])}
    poz = a.poz if all(b.poz == a.poz for b in st[1:]) else None
    fun = {}
    for k in set().union(*(b.fun.keys() for b in st)):
        f0 = a.fun.get(k, _NEZ)
        fun[k] = f0 if f0 is not _NEZ and f0 is not None and all(b.fun.get(k, _NEZ) is f0 for b in st[1:]) else None
    return ShStav(v, poz, fun, any(b.cudzie for b in st))


_NEZ = object()


class ShVysl:
    """Výsledok behu: t/f = stav po skončení s kódom 0 / nenulovým; ex = možný exit; ret/brk/cnt = return/break/continue."""
    __slots__ = ('t', 'f', 'ex', 'ret', 'brk', 'cnt')

    def __init__(self, t=None, f=None, ex=False, ret=None, brk=None, cnt=None):
        self.t, self.f, self.ex, self.ret, self.brk, self.cnt = t, f, ex, ret, brk, cnt

    def prilep(self, r):
        """Nenormálne skončenia z r (exit, return, break, continue) sa pridajú k tomuto výsledku."""
        self.ex |= r.ex
        self.ret = sh_spoj(self.ret, r.ret)
        self.brk = sh_spoj(self.brk, r.brk)
        self.cnt = sh_spoj(self.cnt, r.cnt)
        return self


class ShBeh:
    """Abstraktný beh skriptu s argumentmi vyvolania; zaznamená, čo sa môže spustiť (navst) a ktoré zoznamy sa prešli."""

    def __init__(self, strom, s):
        self.strom = strom
        self.s = s
        self.navst = set()
        self.prejdene = {}
        self.kroky = 0
        self.hlbka = 0
        self.ramce = []          # lokálne premenné volaných funkcií: [(meno, stará hodnota alebo _NEZ)]
        self.cykly = 0           # vnorenie cyklov v tejto funkcii (break/continue mimo cyklu nič neukončia)
        self.zdroj = False       # skript načítaný cez source: return na najvyššej úrovni ho ukončí
        self._priradene = {}
        self.atributy = set()    # premenné s declare -l/-u/-c/-i: priradenie mení hodnotu (malé písmená, aritmetika …)
        self.ifs = False         # skript mení IFS: rozvinutie bez úvodzoviek sa delí inak, než beh vie

    def krok(self):
        self.kroky += 1
        if self.kroky > SH_KROKY:
            raise _Nevie('priveľa krokov')

    # --- hodnoty slov ---
    def premenna(self, meno, st):
        if meno in SH_DYNAMICKE:
            return None
        if meno.isdigit():
            if meno == '0':
                return None
            if st.poz is None:
                return None
            k = int(meno) - 1
            return st.poz[k] if k < len(st.poz) else ''
        if meno == '#':
            return None if st.poz is None else str(len(st.poz))
        if meno in ('@', '*'):
            if st.poz is None or any(x is None for x in st.poz) or (meno == '*' and self.ifs):
                return None              # "$*" spája prvým znakom IFS
            return ' '.join(st.poz)
        return st.v.get(meno)

    def casti(self, w, st, bez_delenia=False):
        """Slovo → [(text, v úvodzovkách)], alebo None (neznáma časť). Rozvinutie bez úvodzoviek s medzerou alebo prázdne
        (delenie na slová) je neznáme."""
        o = []
        i = 0
        n = len(w)
        while i < n:
            c = w[i]
            if c == "'":
                j = w.index("'", i + 1)
                o.append((w[i + 1:j], True))
                i = j + 1
            elif c == '"':
                j = sh_dvojite(w, i + 1)
                vnut = self.v_dvojitych(w[i + 1:j - 1], st)
                if vnut is None:
                    return None
                o.append((vnut, True))
                i = j
            elif c == '\\':
                o.append((w[i + 1:i + 2], True))
                i += 2
            elif c == '$':
                if w.startswith("$'", i):
                    j = sh_dolar(w, i)
                    try:
                        o.append((w[i + 2:j - 1].encode('latin-1', 'backslashreplace').decode('unicode_escape'), True))
                    except (UnicodeError, ValueError):
                        return None
                    i = j
                    continue
                j = sh_dolar_cely(w, i)
                h = self.rozvinutie(w[i:j], st)
                if h is None or (not bez_delenia and (self.ifs or not h.strip() or any(ch in h for ch in ' \t\n')
                                                      or SH_GLOB.search(h))):
                    return None
                o.append((h, False))
                i = j
            elif c == '`':
                return None
            elif c == '~' and i == 0:
                return None
            else:
                j = i
                while j < n and w[j] not in '\'"\\$`':
                    j += 1
                if not bez_delenia and re.search(r'\{[^{}]*(?:,|\.\.)[^{}]*\}', w):
                    return None      # {dry,live}, {1..3}: bash slovo rozvinie na viac slov (3. 10., kolo 2)
                o.append((w[i:j], False))
                i = j
        return o

    def v_dvojitych(self, w, st):
        o = []
        i = 0
        n = len(w)
        while i < n:
            c = w[i]
            if c == '\\' and i + 1 < n and w[i + 1] in '$`"\\\n':
                o.append(w[i + 1])
                i += 2
            elif c == '$':
                j = sh_dolar_cely(w, i)
                if j == i + 1:
                    o.append('$')
                    i += 1
                    continue
                h = self.rozvinutie(w[i:j], st)
                if h is None:
                    return None
                o.append(h)
                i = j
            elif c == '`':
                return None
            else:
                o.append(c)
                i += 1
        return ''.join(o)

    def rozvinutie(self, x, st):
        """$meno, ${meno}, ${meno:-slovo}, ${meno-slovo}, ${#meno}, $1, $#, $@ … → text alebo None."""
        m = re.fullmatch(r'\$([A-Za-z_][A-Za-z0-9_]*|[0-9#@*])', x)
        if m:
            return self.premenna(m.group(1), st)
        m = re.fullmatch(r'\$\{([A-Za-z_][A-Za-z0-9_]*|[0-9]+|[#@*])\}', x)
        if m:
            return self.premenna(m.group(1), st)
        m = re.fullmatch(r'\$\{([A-Za-z_][A-Za-z0-9_]*|[0-9]+)(:?)([-+])(.*)\}', x, re.S)
        if m and re.search(r'[\'"\\`]', m.group(4)):
            return None              # ${1:-"dry"}: úvodzovky vnútri sa odstraňujú podľa kontextu — kotva nevie (3. 10.)
        if m:
            h = self.premenna(m.group(1), st)
            if h is None:
                return None
            nastavena = h != '' if m.group(2) else (m.group(1) in st.v or m.group(1).isdigit()
                                                     and st.poz is not None and int(m.group(1)) <= len(st.poz))
            if m.group(2) == '' and not m.group(1).isdigit() and m.group(1) not in st.v:
                return None
            if m.group(3) == '-':
                return h if nastavena else self.v_dvojitych(m.group(4), st)
            return self.v_dvojitych(m.group(4), st) if nastavena else ''
        m = re.fullmatch(r'\$\{#([A-Za-z_][A-Za-z0-9_]*)\}', x)
        if m:
            h = self.premenna(m.group(1), st)
            return None if h is None else str(len(h))
        return None

    def hodnota(self, w, st, bez_delenia=False):
        """Text slova (bez delenia a bez vzoru), alebo None. bez_delenia: vnútri [[ ]] (ani delenie, ani vzor mien)."""
        c = self.casti(w, st, bez_delenia)
        if c is None or (not bez_delenia and any(not q and SH_GLOB.search(t) for t, q in c)):
            return None
        return ''.join(t for t, _ in c)

    def vzor(self, w, st):
        """Slovo ako vzor (case, [[ == ]]) → regulárny výraz, alebo None."""
        c = self.casti(w, st, True)
        if c is None:
            return None
        o = []
        for t, q in c:
            if q:
                o.append(re.escape(t))
                continue
            if re.search(r'[@!+*?]\(', t):
                return None             # extglob
            k = 0
            while k < len(t):
                ch = t[k]
                if ch == '*':
                    o.append('.*')
                elif ch == '?':
                    o.append('.')
                elif ch == '[':
                    j = t.find(']', k + 2)
                    if j < 0:
                        o.append(re.escape(ch))
                    else:
                        tr = t[k + 1:j]
                        if tr[:1] in '!^':
                            tr = '^' + tr[1:]
                        if '[:' in tr:
                            return None
                        o.append('[' + tr.replace('\\', '\\\\') + ']')
                        k = j
                else:
                    o.append(re.escape(ch))
                k += 1
        return ''.join(o)

    def zhoda(self, hodn, w, st):
        """Zhoda textu so vzorom: True/False/None. Po source/eval (cudzie) neznáma — mohol zapnúť nocasematch."""
        if hodn is None or st.cudzie:
            return None
        v = self.vzor(w, st)
        if v is None:
            return None
        try:
            return re.fullmatch(v, hodn, re.S) is not None
        except re.error:
            return None

    def pole(self, w, st):
        """Slovo v zozname (for … in): zoznam hodnôt, alebo None. "$@" = argumenty."""
        if w in ('"$@"', '"${@}"'):
            return None if st.poz is None or any(x is None for x in st.poz) else list(st.poz)
        if w in ('$@', '$*', '${@}', '${*}'):
            if st.poz is None or self.ifs or any(x is None or not x or re.search(r'[\s*?[]', x) for x in st.poz):
                return None
            return list(st.poz)
        h = self.hodnota(w, st)
        return None if h is None else [h]

    # --- podmienky ---
    def test1(self, a, st):
        """[ … ] / test … — argumenty (slová) → True/False/None."""
        h = []
        for w in a:
            x = self.hodnota(w, st)
            if x is None:
                return None
            h.append(x)
        return self.test_hodnoty(h)

    def test_hodnoty(self, h):
        if h and h[0] == '!':
            r = self.test_hodnoty(h[1:])
            return None if r is None else not r
        if len(h) == 0:
            return False
        if len(h) == 1:
            return h[0] != ''
        if len(h) == 2:
            if h[0] == '-n':
                return h[1] != ''
            if h[0] == '-z':
                return h[1] == ''
            return None
        if len(h) == 3:
            a, op, b = h
            if op in ('=', '=='):
                return a == b
            if op == '!=':
                return a != b
            if op in ('-eq', '-ne', '-lt', '-le', '-gt', '-ge'):
                try:
                    x, y = int(a), int(b)
                except ValueError:
                    return None
                return {'-eq': x == y, '-ne': x != y, '-lt': x < y, '-le': x <= y, '-gt': x > y, '-ge': x >= y}[op]
        return None

    def test2(self, text, st):
        """[[ … ]] → True/False/None."""
        vnut = text[2:-2]
        t = []
        i = 0
        n = len(vnut)
        while i < n:
            if vnut[i] in ' \t\r\n':
                i += 1
                continue
            if vnut.startswith(('&&', '||'), i):
                t.append(vnut[i:i + 2])
                i += 2
                continue
            if vnut[i] in '()':
                t.append(vnut[i])
                i += 1
                continue
            if vnut[i] in '<>':
                t.append(vnut[i])
                i += 1
                continue
            k = sh_koniec_slova(vnut, i, test=True)
            t.append(vnut[i:k] if k > i else vnut[i])
            i = max(k, i + 1)
        self._t2 = t
        self._t2i = 0
        r = self.t2_alebo(st)
        if self._t2i != len(t):
            return None
        return r

    def t2_kuk(self):
        return self._t2[self._t2i] if self._t2i < len(self._t2) else None

    def t2_alebo(self, st):
        r = self.t2_a(st)
        while self.t2_kuk() == '||':
            self._t2i += 1
            r2 = self.t2_a(st)
            r = True if (r is True or r2 is True) else (False if (r is False and r2 is False) else None)
        return r

    def t2_a(self, st):
        r = self.t2_nie(st)
        while self.t2_kuk() == '&&':
            self._t2i += 1
            r2 = self.t2_nie(st)
            r = False if (r is False or r2 is False) else (True if (r is True and r2 is True) else None)
        return r

    def t2_nie(self, st):
        if self.t2_kuk() == '!':
            self._t2i += 1
            r = self.t2_nie(st)
            return None if r is None else not r
        if self.t2_kuk() == '(':
            self._t2i += 1
            r = self.t2_alebo(st)
            if self.t2_kuk() != ')':
                raise _Nevie('[[ ( ]]')
            self._t2i += 1
            return r
        return self.t2_zakl(st)

    def t2_zakl(self, st):
        a = self.t2_kuk()
        if a is None:
            raise _Nevie('[[ ]] prázdne')
        self._t2i += 1
        op = self.t2_kuk()
        if a in ('-n', '-z') and op not in (None, '&&', '||', ')'):
            self._t2i += 1
            h = self.hodnota(op, st, True)
            return None if h is None else ((h != '') if a == '-n' else (h == ''))
        if re.fullmatch(r'-[a-zA-Z]', a) and op not in (None, '&&', '||', ')'):
            self._t2i += 1
            return None                                   # -f, -d, -v … — súbor či stav, kotva nevie
        if op in ('==', '=', '!=', '=~', '<', '>', '-eq', '-ne', '-lt', '-le', '-gt', '-ge', '-nt', '-ot', '-ef'):
            self._t2i += 1
            b = self.t2_kuk()
            if b is None:
                raise _Nevie('[[ a op ]]')
            self._t2i += 1
            ha = self.hodnota(a, st, True)
            if op in ('==', '=', '!='):
                z = self.zhoda(ha, b, st)
                return None if z is None else (z if op != '!=' else not z)
            if op in ('-eq', '-ne', '-lt', '-le', '-gt', '-ge'):
                hb = self.hodnota(b, st, True)
                if ha is None or hb is None or any(not re.fullmatch(r'\s*-?(?:0|[1-9]\d*)\s*', x) for x in (ha, hb)):
                    return None          # v [[ ]] sú čísla aritmetika: 010 je 8, 0x10 je 16, meno premennej jej hodnota
                return self.test_hodnoty([ha, op, hb])
            return None
        h = self.hodnota(a, st, True)
        return None if h is None else h != ''

    def aritm(self, text, st):
        """(( … )): hodnota podmienky True/False/None a mená premenných, ktoré výraz mení."""
        vnut = text[2:-2]
        if re.search(r'\$\{?[A-Za-z_]\w*\}?\s*(?:(?:[-+*/%&|^]|<<|>>)?=(?!=)|\+\+|--)|(?:\+\+|--)\s*\$', vnut):
            raise _Nevie('dynamické priradenie v (( ))')      # (( $n = 1 )) priradí premennej, ktorej meno je v n
        meni = set(re.findall(r'([A-Za-z_][A-Za-z0-9_]*)\s*(?:[-+*/%&|^]|<<|>>)?=(?!=)', vnut)) \
            | set(re.findall(r'([A-Za-z_][A-Za-z0-9_]*)\s*(?:\+\+|--)', vnut)) \
            | set(re.findall(r'(?:\+\+|--)\s*([A-Za-z_][A-Za-z0-9_]*)', vnut))
        if meni:
            return None, meni
        prekl = []
        for tk in re.findall(r'\$\{?[A-Za-z_][A-Za-z0-9_]*\}?|\$[0-9#]|[A-Za-z_][A-Za-z0-9_]*|\d+|&&|\|\||==|!=|<=|>=|[<>!()+\-]|\S',
                             vnut):
            if tk in ('&&', '||', '==', '!=', '<=', '>=', '<', '>', '(', ')', '+', '-'):
                prekl.append({'&&': ' and ', '||': ' or '}.get(tk, tk))
            elif tk == '!':
                prekl.append(' not ')
            elif tk.isdigit():
                prekl.append(tk)
            elif re.fullmatch(r'\$?\{?[A-Za-z_][A-Za-z0-9_]*\}?|\$[0-9#]', tk):
                meno = tk.strip('${}')
                h = self.premenna(meno, st)
                if h is None:
                    return None, set()
                h = h.strip() or '0'
                if not re.fullmatch(r'-?\d+', h):
                    return None, set()
                prekl.append(f'({h})')
            else:
                return None, set()
        try:
            strom = ast.parse(''.join(prekl) or '0', mode='eval')
            for u in ast.walk(strom):
                if not isinstance(u, (ast.Expression, ast.BoolOp, ast.UnaryOp, ast.BinOp, ast.Compare, ast.Constant,
                                      ast.And, ast.Or, ast.Not, ast.USub, ast.UAdd, ast.Add, ast.Sub, ast.Eq, ast.NotEq,
                                      ast.Lt, ast.LtE, ast.Gt, ast.GtE)):
                    return None, set()
            return bool(eval(compile(strom, '<aritm>', 'eval'), {'__builtins__': {}}, {})), set()
        except (SyntaxError, ValueError, TypeError, RecursionError):
            return None, set()

    # --- beh ---
    def zoznam(self, zoz, st):
        self.prejdene[id(zoz)] = self.prejdene.get(id(zoz), 0) + 1
        vys = ShVysl(t=st)
        cur = st
        for ao in zoz['pol']:
            if cur is None:
                break
            self.krok()
            r = self.and_or(ao, cur)
            vys.prilep(r)
            cur = sh_spoj(r.t, r.f)
            vys.t, vys.f = r.t, r.f
        if cur is None:
            vys.t = vys.f = None
        return vys

    def and_or(self, ao, st):
        self.navst.add(id(ao))
        if ao['bg']:
            self.v_podshelli(lambda s2: self.and_or_vnut(ao, s2), st)
            return ShVysl(t=st, f=st)
        return self.and_or_vnut(ao, st)

    def v_podshelli(self, beh, st):
        """Podshell (rúra, &, ( … )): priradenia ani exit/return/break sa do rodiča nedostanú."""
        ulozene = (self.cykly, self.ramce)
        self.cykly = 0
        self.ramce = [[]] if self.ramce else []
        try:
            return beh(st.kopia())
        finally:
            self.cykly, self.ramce = ulozene

    def and_or_vnut(self, ao, st):
        r = self.ruro(ao['prvky'][0][1], st)
        for op, ru in ao['prvky'][1:]:
            vstup = r.t if op == '&&' else r.f
            if vstup is None:
                continue
            r2 = self.ruro(ru, vstup)
            n = ShVysl(t=r2.t if op == '&&' else sh_spoj(r.t, r2.t), f=sh_spoj(r.f, r2.f) if op == '&&' else r2.f)
            n.prilep(r).prilep(r2)
            r = n
        return r

    def ruro(self, ru, st):
        self.navst.add(id(ru))
        if len(ru['prik']) > 1:
            for c in ru['prik']:
                self.v_podshelli(lambda s2, c=c: self.prikaz(c, s2), st)
            # po source/eval mohol byť zapnutý lastpipe — posledný článok rúry potom priraďuje v tomto shelli
            r = ShVysl(t=st.nezname(), f=st.nezname()) if st.cudzie else ShVysl(t=st, f=st)
        else:
            r = self.prikaz(ru['prik'][0], st)
        if ru['neg']:
            r.t, r.f = r.f, r.t
        return r

    def prikaz(self, c, st):
        self.krok()
        self.navst.add(id(c))
        typ = c['typ']
        if typ == 'cmd':
            return self.jednoduchy(c, st)
        if typ == 'blok':
            return self.zoznam(c['telo'], st)
        if typ == 'pod':
            self.v_podshelli(lambda s2: self.zoznam(c['telo'], s2), st)
            return ShVysl(t=st, f=st)
        if typ == 'fun':
            st = st.kopia()
            st.fun[c['meno']] = c
            return ShVysl(t=st)
        if typ == 'if':
            return self.if_(c, st)
        if typ == 'case':
            return self.case_(c, st)
        if typ == 'for':
            return self.for_(c, st)
        if typ == 'cyklus':
            return self.cyklus(c, st)
        if typ == 'test2':
            st = self.vedlajsie([c['text']], st)
            r = self.test2(c['text'], st)
            return ShVysl(t=st if r is not False else None, f=st if r is not True else None)
        if typ == 'aritm':
            r, meni = self.aritm(c['text'], st)
            if meni:
                st = st.kopia()
                for m in meni:
                    st.v.pop(m, None)
            return ShVysl(t=st if r is not False else None, f=st if r is not True else None)
        raise _Nevie(f'neznámy uzol {typ}')

    def if_(self, c, st):
        vys = ShVysl()
        cur = st
        for podm, telo in c['vetvy']:
            if cur is None:
                break
            r = self.zoznam(podm, cur)
            vys.prilep(r)
            if r.t is not None:
                rt = self.zoznam(telo, r.t)
                vys.prilep(rt)
                vys.t, vys.f = sh_spoj(vys.t, rt.t), sh_spoj(vys.f, rt.f)
            cur = r.f
        if cur is not None:
            if c['inak'] is not None:
                ri = self.zoznam(c['inak'], cur)
                vys.prilep(ri)
                vys.t, vys.f = sh_spoj(vys.t, ri.t), sh_spoj(vys.f, ri.f)
            else:
                vys.t = sh_spoj(vys.t, cur)
        return vys

    def case_(self, c, st):
        st = self.vedlajsie([c['slovo'][1]] + [v[1] for vz, _, _ in c['vetvy'] for v in vz], st)
        hodn = self.hodnota(c['slovo'][1], st)
        vys = ShVysl()
        istota = False
        vetvy = c['vetvy']
        zaciatky = []
        for k, (vzory, telo, konc) in enumerate(vetvy):
            z = [self.zhoda(hodn, v[1], st) for v in vzory]
            if any(x is True for x in z):
                zaciatky.append(k)
                if konc != ';;&':
                    istota = True
                    break
            elif any(x is None for x in z):
                zaciatky.append(k)
        for k in zaciatky:
            cur = st
            j = k
            while j < len(vetvy) and cur is not None:
                r = self.zoznam(vetvy[j][1], cur)
                vys.prilep(r)
                cur = sh_spoj(r.t, r.f)
                if vetvy[j][2] != ';&':
                    vys.t, vys.f = sh_spoj(vys.t, r.t), sh_spoj(vys.f, r.f)
                    cur = None
                    break
                j += 1
            if cur is not None:              # ;& za poslednou vetvou
                vys.t = sh_spoj(vys.t, cur)
        if not istota:
            vys.t = sh_spoj(vys.t, st)        # žiadna vetva (alebo neisté, či niektorá sedí)
        return vys

    def priradene(self, uzol):
        """Mená premenných, ktoré môže uzol (aj vnorený, aj cez volané funkcie skriptu) zmeniť — pre cyklus s neznámym
        počtom priechodov. None = hocijaká (eval, source, neznámy program)."""
        k = id(uzol)
        if k in self._priradene:
            return self._priradene[k]
        self._priradene[k] = set()             # proti cyklu volaní
        mena = set()
        vsetky = False
        zas = [uzol]
        while zas:
            u = zas.pop()
            if isinstance(u, dict):
                typ = u.get('typ')
                if typ == 'cmd':
                    sl = [x[1] for x in u['slova']]
                    j = 0
                    while j < len(sl) and SH_PRIRADENIE.match(sl[j]):
                        mena.add(SH_PRIRADENIE.match(sl[j]).group(1))
                        j += 1
                    for x in sl:
                        for m2 in re.finditer(r'\$\{([A-Za-z_][A-Za-z0-9_]*):?=', x):
                            mena.add(m2.group(1))
                        if '$((' in x:
                            mena |= self.aritm('((' + x + '))', ShStav())[1]
                    prog = sl[j] if j < len(sl) else None
                    if prog in ('eval', 'source', '.'):
                        vsetky = True
                    elif prog is not None and ('$' in prog or '`' in prog):
                        f = self.funkcie_menia()             # neznámy program môže byť len funkcia skriptu
                        if f is None:
                            vsetky = True
                        else:
                            mena |= f
                    elif prog is not None and prog in SH_PRIRADUJE:
                        for x in sl[j + 1:]:
                            m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)', x)
                            if m:
                                mena.add(m.group(1))
                    elif prog is not None and prog in self.strom.funkcie:
                        for f in self.strom.funkcie[prog]:
                            r = self.priradene(f['telo'])
                            if r is None:
                                vsetky = True
                            else:
                                mena |= r
                    continue
                if typ == 'for':
                    mena.add(u['meno'])
                elif typ == 'aritm':
                    mena |= self.aritm(u['text'], ShStav())[1]
                for x in u.values():
                    if isinstance(x, (dict, list, tuple)) and x and not (isinstance(x, list) and isinstance(x[0], str)):
                        zas.append(x)
            elif isinstance(u, (list, tuple)):
                for x in u:
                    if isinstance(x, (dict, list, tuple)) and x and not (isinstance(x, list) and isinstance(x[0], str)):
                        zas.append(x)
        r = None if vsetky else mena
        self._priradene[k] = r
        return r

    def funkcie_menia(self):
        """Premenné, ktoré môže zmeniť niektorá funkcia skriptu (neznámy program z premennej môže byť ona); None = hocijaké."""
        if not hasattr(self, '_funkcie_menia'):
            self._funkcie_menia = set()
            mena = set()
            for defs in self.strom.funkcie.values():
                for f in defs:
                    r = self.priradene(f['telo'])
                    if r is None:
                        mena = None
                        break
                    mena |= r
                if mena is None:
                    break
            self._funkcie_menia = mena
        return self._funkcie_menia

    def rozsir(self, st, uzol):
        """Stav pred cyklom s neznámym počtom priechodov: premenné, ktoré telo mení, sú neznáme."""
        p = self.priradene(uzol)
        if p is None or st.cudzie:
            return st.nezname()
        st = st.kopia()
        for m in p:
            st.v.pop(m, None)
        if re.search(r'(?:^|[\s;&|(])(?:shift|set|source|\.)(?:\s|;|$)', self.s[uzol['od']:uzol['do']]):
            st.poz = None
        return st

    def telo_cyklu(self, telo, st):
        """Jeden priechod telom: (stav na ďalší priechod, stav po break, výsledok)."""
        self.cykly += 1
        try:
            r = self.zoznam(telo, st)
        finally:
            self.cykly -= 1
        return sh_spoj(r.t, r.f, r.cnt), r.brk, r

    def for_(self, c, st):
        if c['select']:
            return self.neznamy_cyklus(c, st)
        if c['slova'] is None:
            hodn = None if st.poz is None or any(x is None for x in st.poz) else list(st.poz)
        else:
            st = self.vedlajsie([w[1] for w in c['slova']], st)
            hodn = []
            for w in c['slova']:
                p = self.pole(w[1], st)
                if p is None:
                    hodn = None
                    break
                hodn += p
        if hodn is None or len(hodn) > SH_OPAKOVANIA:
            return self.neznamy_cyklus(c, st)
        vys = ShVysl()
        cur = st
        po_break = None
        for x in hodn:
            if cur is None:
                break
            cur = cur.kopia()
            cur.v[c['meno']] = x
            cur, brk, r = self.telo_cyklu(c['telo'], cur)
            vys.ex |= r.ex
            vys.ret = sh_spoj(vys.ret, r.ret)
            po_break = sh_spoj(po_break, brk)
        vys.t = vys.f = sh_spoj(cur, po_break)
        return vys

    def neznamy_cyklus(self, c, st):
        st2 = self.rozsir(st, c)
        vys = ShVysl()
        if c.get('podm') is not None:
            rp = self.zoznam(c['podm'], st2)
            vys.ex |= rp.ex
            dnu = rp.f if c.get('until') else rp.t
            von = rp.t if c.get('until') else rp.f
        else:
            dnu = von = st2
        if dnu is not None:
            _, brk, r = self.telo_cyklu(c['telo'], dnu)
            vys.ex |= r.ex
            vys.ret = sh_spoj(vys.ret, r.ret)
            von = sh_spoj(von, brk, st2)
        vys.t = vys.f = von
        return vys

    def cyklus(self, c, st):
        if c.get('podm') is None:                 # for (( … ))
            return self.neznamy_cyklus(c, st)
        vys = ShVysl()
        cur = st
        po_break = None
        for _ in range(SH_OPAKOVANIA):
            if cur is None:
                vys.t = vys.f = po_break
                return vys
            rp = self.zoznam(c['podm'], cur)
            vys.ex |= rp.ex
            dnu = rp.f if c.get('until') else rp.t
            von = rp.t if c.get('until') else rp.f
            if dnu is not None and von is not None:
                break                               # podmienka neznáma — ďalej „hocikoľko krát“
            if dnu is None:
                vys.t = vys.f = sh_spoj(von, po_break)
                return vys
            cur, brk, r = self.telo_cyklu(c['telo'], dnu)
            vys.ex |= r.ex
            vys.ret = sh_spoj(vys.ret, r.ret)
            po_break = sh_spoj(po_break, brk)
        if cur is None:
            vys.t = vys.f = po_break
            return vys
        r = self.neznamy_cyklus(c, cur)
        vys.ex |= r.ex
        vys.ret = sh_spoj(vys.ret, r.ret)
        vys.t = vys.f = sh_spoj(r.t, po_break)
        return vys

    def vedlajsie(self, texty, st):
        """Priradenie pri rozvinutí slova — $(( X = 1 )), $(( X++ )), ${X:=…}, ${X=…}: tá premenná je potom neznáma."""
        mena = set()
        for x in texty:
            if '$((' in x:
                mena |= self.aritm('((' + x + '))', ShStav())[1]
            for m in re.finditer(r'\$\{([A-Za-z_][A-Za-z0-9_]*):?=', x):
                mena.add(m.group(1))
        if not mena:
            return st
        st = st.kopia()
        for m in mena:
            st.v.pop(m, None)
        return st

    def jednoduchy(self, c, st):
        r = self._jednoduchy(c, st)
        if st.cudzie and r is not None:
            sl = [x[1] for x in c['slova']]
            i = 0
            while i < len(sl) and SH_PRIRADENIE.match(sl[i]):
                i += 1
            if i < len(sl) and self.hodnota(sl[i], st) in SH_VSTAVANE_BEH:
                # 3. 10.: po source/eval môže byť exit, [, shift … funkcia z načítaného súboru — aj „pokračuje s neznámym“
                nez = st.nezname()
                r = ShVysl(t=sh_spoj(r.t, nez), f=sh_spoj(r.f, nez), ex=r.ex, ret=r.ret, brk=r.brk, cnt=r.cnt)
        return r

    def _jednoduchy(self, c, st):
        sl = [x[1] for x in c['slova']]
        st = self.vedlajsie(sl + [x[1] for x in c['presm']], st)
        i = 0
        prir = []
        while i < len(sl) and SH_PRIRADENIE.match(sl[i]):
            prir.append(sl[i])
            i += 1
        subst = any('$(' in x or '`' in x for x in sl) or any('$(' in x[1] or '`' in x[1] for x in c['presm'])
        if i >= len(sl):
            st = st.kopia()
            for x in prir:
                self.prirad(x, st)
            return ShVysl(t=st, f=st if subst else None)
        prog = self.hodnota(sl[i], st)
        args = sl[i + 1:]
        while prog in ('command', 'builtin') and args and not args[0].startswith('-'):
            prog = self.hodnota(args[0], st)
            args = args[1:]
        if prog is None:
            # neznámy program (z premennej) môže byť funkcia skriptu — premenné, ktoré funkcie menia, potom nevidno
            if st.cudzie:
                return ShVysl(t=st.nezname(), f=st.nezname())
            fm = self.funkcie_menia() if self.strom.funkcie else set()
            if fm is None:
                return ShVysl(t=st.nezname(), f=st.nezname())
            st = st.kopia()
            for m in fm:
                st.v.pop(m, None)
            return ShVysl(t=st, f=st)
        if prog == 'exit':
            return ShVysl(ex=True)
        if prog == 'return':
            if self.ramce or self.zdroj:
                return ShVysl(ret=st)
            return ShVysl(t=st, f=st)                # return mimo funkcie spusteného skriptu: chyba, beh pokračuje
        if prog in ('break', 'continue'):
            if self.cykly:
                return ShVysl(brk=st) if prog == 'break' else ShVysl(cnt=st)
            return ShVysl(t=st, f=st)                # mimo cyklu (aj vo funkcii volanej z cyklu): radšej beh pokračuje
        if prog in ('true', ':'):
            return ShVysl(t=st, f=st if subst else None)
        if prog == 'false':
            return ShVysl(f=st)
        if prog in ('[', 'test'):
            if prog == '[' and (not args or args[-1] != ']'):
                return ShVysl(t=st, f=st)
            r = self.test1(args[:-1] if prog == '[' else args, st)
            return ShVysl(t=st if r is not False else None, f=st if r is not True else None)
        if prog == 'shift':
            st = st.kopia()
            n = 1
            if args:
                h = self.hodnota(args[0], st)
                n = int(h) if h is not None and h.isdigit() else None
            if st.poz is None or n is None:
                st.poz = None
                return ShVysl(t=st, f=st)
            if n > len(st.poz):
                return ShVysl(f=st)
            st.poz = st.poz[n:]
            return ShVysl(t=st)
        if prog == 'set':
            # set -euo pipefail: slovo za voľbou -o je meno voľby, nie argument; argumenty sú až za -- alebo prvé iné slovo
            j = 0
            k = None
            while j < len(args):
                a = args[j]
                if a == '--':
                    k = j + 1
                    break
                if a == '-':
                    k = j + 1
                    break
                if a[:1] in '-+' and len(a) > 1:
                    j += 2 if 'o' in a[1:] else 1
                    continue
                k = j
                break
            if k is None:
                return ShVysl(t=st)
            st = st.kopia()
            nove = []
            for w in args[k:]:
                p = self.pole(w, st)
                if p is None:
                    nove = None
                    break
                nove += p
            st.poz = nove
            return ShVysl(t=st)
        if prog in ('local', 'declare', 'typeset', 'export', 'readonly'):
            return self.deklaracia(prog, args, st, subst)
        if prog == 'unset':
            # 3. 10. (overenie kola 1: `unset X; [ -z "${X+x}" ]` — beh bral X ako prázdnu, ale nastavenú): premenná je po
            # unset neznáma; bez -v ruší unset aj funkciu toho mena (keď premenná nie je), s -f len funkciu
            st = st.kopia()
            volby = ''.join(a[1:] for a in args if a.startswith('-'))
            for a in args:
                m = re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*)(?:\[.*\])?', a)
                if m and not a.startswith('-'):
                    if 'f' not in volby:
                        st.v.pop(m.group(1), None)
                    if 'v' not in volby and (m.group(1) in st.fun or m.group(1) in self.strom.funkcie):
                        st.fun[m.group(1)] = None
                elif not a.startswith('-'):
                    return ShVysl(t=st.nezname(), f=st.nezname())
            return ShVysl(t=st, f=st)
        if prog in SH_PRIRADUJE:
            st = st.kopia()
            for meno in self.priraduje_mena(prog, args):
                st.v.pop(meno, None)
            return ShVysl(t=st, f=st)
        if prog in ('alias', 'enable') or (prog in ('shopt', 'set') and (
                any(a in SH_VOLBY_ZLE for a in args) or (prog == 'shopt' and any('$' in a or '`' in a for a in args)))):
            raise _Nevie(f'{prog} mení správanie shellu')
        if prog in ('eval', 'source', '.') \
                or (prog == 'trap' and any(re.fullmatch(r'(?:SIG)?(?:DEBUG|RETURN|ERR)', a) for a in args[1:])):
            # trap … DEBUG/RETURN/ERR beží uprostred skriptu — čo v ňom priradí, abstraktný beh nevidí
            st = st.nezname()
            st.cudzie = True
            return ShVysl(t=st, f=st)
        f = st.fun.get(prog, _NEZ)
        if f is None:                               # možno definovaná v jednej z ciest — účinok neznámy
            return ShVysl(t=st.nezname(), f=st.nezname())
        if f is not _NEZ:
            return self.volanie(f, args, prir, st)
        if st.cudzie:                               # po source môže byť hocijaký príkaz funkcia
            return ShVysl(t=st.nezname(), f=st.nezname())
        return ShVysl(t=st, f=st)

    def priraduje_mena(self, prog, args):
        """Mená premenných, ktoré read, printf -v, mapfile, getopts, let, wait -p priradia. Meno z premennej či v úvodzovkách
        (printf -v "$n", read "$n") kotva nepozná — nevynecháva sa nič (3. 10., kolo 2, rozdielová skúška)."""
        mena = []
        if prog == 'let':
            text = ' '.join(args)
            if re.search(r'\$\{?[A-Za-z_]\w*\}?\s*(?:(?:[-+*/%&|^]|<<|>>)?=(?!=)|\+\+|--)|(?:\+\+|--)\s*\$', text):
                raise _Nevie('dynamické priradenie v let')
            return re.findall(r'([A-Za-z_]\w*)\s*(?:(?:[-+*/%&|^]|<<|>>)?=(?!=)|\+\+|--)', text) \
                + re.findall(r'(?:\+\+|--)\s*([A-Za-z_]\w*)', text)
        hodnota = {'read': 'adinNptu', 'mapfile': 'dnOsuCc', 'readarray': 'dnOsuCc', 'printf': 'v', 'wait': 'p',
                   'getopts': ''}.get(prog)
        if hodnota is None:
            return [x.group() for x in (re.match(r'[A-Za-z_]\w*', a) for a in args) if x]
        poz = []
        k = 0
        while k < len(args):
            a = args[k]
            if a == '--':
                poz += args[k + 1:]
                break
            if a.startswith('-') and len(a) > 1 and prog != 'getopts':
                for j, ch in enumerate(a[1:], 1):
                    if ch in hodnota:
                        h = a[j + 1:] or (args[k + 1] if k + 1 < len(args) else '')
                        if (prog, ch) in (('read', 'a'), ('printf', 'v'), ('wait', 'p')):
                            mena.append(h)            # read -a pole, printf -v meno, wait -p meno
                        if not a[j + 1:]:
                            k += 1
                        break
                k += 1
                continue
            poz.append(a)
            k += 1
        if prog == 'read':
            mena += poz or []
        elif prog in ('mapfile', 'readarray'):
            mena += poz[-1:]
        elif prog == 'getopts':
            mena += poz[1:2]
        for x in mena:
            if not re.fullmatch(r'[A-Za-z_]\w*', x):
                raise _Nevie(f'{prog} priradí meno, ktoré kotva nepozná')
        return mena

    def deklaracia(self, prog, args, st, subst):
        st = st.kopia()
        volby = ''.join(a[1:] for a in args if a.startswith(('-', '+')))
        if 'n' in volby and prog != 'export':
            raise _Nevie('odkaz na premennú (declare -n)')
        if re.search(r'[Aa]', volby) and prog != 'export':
            for a in args:
                m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)', a)
                if m and not a.startswith(('-', '+')):
                    st.v.pop(m.group(1), None)
            return ShVysl(t=st, f=st)
        lokalne = self.ramce and (prog == 'local' or (prog in ('declare', 'typeset') and 'g' not in volby))
        if prog == 'local' and not self.ramce:
            return ShVysl(t=st, f=st)               # local mimo funkcie: chyba, nič sa nepriradí
        if prog == 'readonly' or (prog != 'export' and re.search(r'[luciIr]', volby)):
            # 3. 10. (overenie kola 1: `declare -l m="$1"; [ "$m" = live ]` s LIVE): atribút mení každé ďalšie priradenie
            # (malé/veľké písmená, aritmetika) — premenná je odteraz vždy neznáma
            for a in args:
                m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)', a)
                if m and not a.startswith(('-', '+')):
                    self.atributy.add(m.group(1))
                    # 3. 10. (kolo 2): readonly — ďalšie priradenie neplatí (bash ho odmietne a beží ďalej)
        for a in args:
            if a.startswith(('-', '+')):
                continue
            if not re.match(r'[A-Za-z_][A-Za-z0-9_]*(?:\[[^\]]*\])?\+?(?:=|$)', a):
                # 3. 10. (kolo 2): export "M=live" priradí M; declare "$n=live" — meno z premennej kotva nepozná
                m = re.match(r'''["']?([A-Za-z_][A-Za-z0-9_]*)\+?=''', a)
                if not m:
                    raise _Nevie(f'{prog} s menom, ktoré kotva nepozná')
                h = self.hodnota(a, st, True)
                if h is not None and h.startswith(m.group(1) + '='):
                    a = m.group(1) + "='" + h[len(m.group(1)) + 1:].replace("'", "'\\''") + "'"
                else:
                    a = m.group(1) + '=$__nezname__'      # hodnota neznáma
            m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)', a)
            if lokalne:
                self.ramce[-1].append((m.group(1), st.v.get(m.group(1), _NEZ)))
            if '=' in a:
                self.prirad(a, st)
            elif lokalne:
                st.v.pop(m.group(1), None)           # local x bez hodnoty: x je nenastavená (${x+…}), nie prázdna
        return ShVysl(t=st, f=st if subst else None)

    def prirad(self, x, st):
        m = SH_PRIRADENIE.match(x)
        meno = m.group(1)
        zv = x[m.end():]
        if m.group(2) or zv.startswith('(') or meno in self.atributy:
            st.v.pop(meno, None)
            return
        h = self.hodnota(zv, st) if zv else ''
        if h is not None and m.group(3):
            stara = st.v.get(meno)
            h = None if stara is None else stara + h
        if h is None:
            st.v.pop(meno, None)
        else:
            st.v[meno] = h

    def volanie(self, f, args, prir, st):
        if self.hlbka >= SH_VNORENIE:
            raise _Nevie('hlboké volania funkcií')
        poz = []
        for w in args:
            p = self.pole(w, st)
            if p is None:
                poz = None
                break
            poz += p
        st2 = st.kopia()
        st2.poz = poz
        for x in prir:
            self.prirad(x, st2)
        self.hlbka += 1
        self.ramce.append([])
        ulozene = self.cykly
        self.cykly = 0
        try:
            r = self.prikaz(f['telo'], st2)
        finally:
            self.hlbka -= 1
            self.cykly = ulozene
            lok = self.ramce.pop()
        spat = sh_spoj(r.t, r.f, r.ret, r.brk, r.cnt)
        if spat is not None:
            spat = spat.kopia()
            spat.poz = st.poz
            for meno, stara in reversed(lok):
                if stara is _NEZ:
                    spat.v.pop(meno, None)
                else:
                    spat.v[meno] = stara
            for x in prir:                          # X=1 f — priradenie platí len počas volania
                meno = SH_PRIRADENIE.match(x).group(1)
                if meno in st.v:
                    spat.v[meno] = st.v[meno]
                else:
                    spat.v.pop(meno, None)
        return ShVysl(t=spat, f=spat, ex=r.ex)


class _ShPrir:
    """Výsledok SH_PRIRADENIE.match: group(1) meno, group(2) index [ … ] alebo None, group(3) '+' alebo '', end()."""
    __slots__ = ('g', 'k')

    def __init__(self, g, k):
        self.g, self.k = g, k

    def group(self, i):
        return self.g[i]

    def end(self):
        return self.k


class _ShPrirVzor:
    """Priradenie na začiatku slova (meno=…, meno+=…, pole[index]=… aj s vnorenými zátvorkami: chcem[${najnov[$v]}]=1)."""

    @staticmethod
    def match(x):
        m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', x)
        if not m:
            return None
        j = m.end()
        idx = None
        if j < len(x) and x[j] == '[':
            h = 0
            k = j
            while k < len(x):
                if x[k] == '[':
                    h += 1
                elif x[k] == ']':
                    h -= 1
                    if h == 0:
                        break
                k += 1
            if k >= len(x):
                return None
            idx = x[j:k + 1]
            j = k + 1
        if x.startswith('+=', j):
            return _ShPrir((None, m.group(), idx, '+'), j + 2)
        if x.startswith('=', j):
            return _ShPrir((None, m.group(), idx, ''), j + 1)
        return None


SH_PRIRADENIE = _ShPrirVzor()
SH_PRIRADUJE = {'read', 'mapfile', 'readarray', 'getopts', 'printf', 'let', 'unset', 'wait', 'select'}


def sh_volby(obs, tokeny, prefix, zdroj=False):
    """Shellový skript bez vetiev, ktoré sa pri tomto vyvolaní určite nespustia (text s medzerami namiesto nich).
    tokeny = argumenty vyvolania (None = neznámy argument), prefix = priradenia pred príkazom (X=1 bash x.sh),
    zdroj = skript sa načíta cez source (return na najvyššej úrovni ho ukončí). Keď kotva štruktúru alebo beh neprečíta,
    vráti text nezmenený."""
    try:
        strom = ShStrom(obs)
        if set(strom.funkcie) & SH_VSTAVANE_BEH or sh_meni_shell(strom, obs):
            return obs                               # exit() { …; }, alias, shopt -s nocasematch … (3. 10.)
        if any(re.match(r'(?:BASH_ENV|ENV|BASHOPTS|SHELLOPTS|POSIXLY_CORRECT)=', x) for x in prefix):
            return obs                               # prostredie zapne voľby shellu alebo načíta súbor pred skriptom
        beh = ShBeh(strom, obs)
        beh.zdroj = zdroj
        beh.ifs = sh_meni_ifs(strom)
        # 3. 10. (kolo 4): načítaný súbor (source) beží v shelli volajúceho — každý príkaz môže byť funkcia volajúceho,
        # ktorá mení premenné (nastav_rezim() { M=live; } v skripte, ktorý lib načíta), ako po source v skripte samom
        st = ShStav(poz=None if tokeny is None or SH_ROZDEL in tokeny else list(tokeny), cudzie=zdroj)
        for x in prefix:
            m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)=(.*)$', x, re.S)
            if m and not re.search(r'[$`\\\'"]', m.group(2)):
                st.v[m.group(1)] = m.group(2)
        mimo = sh_funkcie_mimo(strom, obs)
        beh.zoznam(strom.koren, st)
        vymaz = []
        sh_vystrihni(beh, strom.koren, vymaz, mimo)
    except (_Nevie, RecursionError, ValueError, IndexError, KeyError, TypeError, AttributeError, re.error):
        return obs
    if not vymaz:
        return obs
    zn = list(obs)
    for od, do in vymaz:
        for k in range(od, min(do, len(zn))):
            if zn[k] != '\n':
                zn[k] = ' '
    return ''.join(zn)


def sh_meni_shell(strom, obs):
    """Skript mení správanie shellu, ktoré abstraktný beh nemodeluje: alias, enable, nocasematch, lastpipe, expand_aliases,
    posix (aj v texte trap či eval) — 3. 10., overenie kola 1."""
    if re.search(r'nocasematch|lastpipe|expand_aliases|\bposix\b', obs):
        return True
    for c in sh_vsetky_prikazy(strom.koren):
        sl = [x[1] for x in c['slova']]
        i = 0
        while i < len(sl) and SH_PRIRADENIE.match(sl[i]):
            i += 1
        while i < len(sl) and sl[i] in ('command', 'builtin'):
            i += 1
        if i < len(sl) and sl[i] in ('alias', 'enable'):
            return True
    return False


def sh_meni_ifs(strom):
    """Skript priraďuje IFS inak než len pre read/mapfile (IFS=, read …): delenie slov sa mení (3. 10.)."""
    for c in sh_vsetky_prikazy(strom.koren):
        sl = [x[1] for x in c['slova']]
        i = 0
        while i < len(sl) and SH_PRIRADENIE.match(sl[i]):
            i += 1
        if any(re.match(r'IFS\+?=', x) for x in sl[:i]) and (i >= len(sl) or sl[i] not in ('read', 'mapfile', 'readarray')):
            return True
        if i < len(sl) and sl[i] in ('local', 'declare', 'typeset', 'export', 'readonly', 'read', 'unset') \
                and any(re.match(r'IFS(?:\+?=|$)', x) for x in sl[i + 1:]):
            return True
    return False


SH_SLOVO_MENA = re.compile(r'(?<![\w$.:-])[\w.:-]+(?![\w.:-])')


def sh_funkcie_mimo(strom, obs):
    """Funkcie, ktoré môže spustiť niečo mimo abstraktného behu — meno je v texte inde než ako priame volanie (trap,
    $( … ), bash -c '…', xargs, export -f …) alebo má viac definícií — a funkcie, ktoré tie volajú. Vnútri nich sa
    nevynecháva nič."""
    texty = [t[1] for t in strom.t if t[0] in ('w', 'r', 'a', 't')] + [obs[t[4][0]:t[4][1]] for t in strom.t
                                                                        if t[0] == 'r' and t[4]]
    # 3. 10. (overenie kola 1: 11 000 funkcií v 1 MB skripte = 190 s): výskyty mien sa spočítajú jedným prechodom textu,
    # nie regulárnym výrazom pre každú funkciu zvlášť — rovnaké pravidlo (meno ako celé slovo, nie za $)
    pocty = collections.Counter(m.group() for x in texty for m in SH_SLOVO_MENA.finditer(x))
    mimo = set()
    for meno, defs in strom.funkcie.items():
        if len(defs) != 1 or pocty.get(meno, 0) - 1 != strom.volania.get(meno, 0):
            mimo.add(meno)
    slova = {meno: {m.group() for d in defs for m in SH_SLOVO_MENA.finditer(obs[d['od']:d['do']])}
             for meno, defs in strom.funkcie.items()}
    fronta = list(mimo)
    while fronta:
        for meno in slova[fronta.pop()]:
            if meno in strom.funkcie and meno not in mimo:
                mimo.add(meno)
                fronta.append(meno)
    return mimo


def sh_rozsahy(u, o):
    """Rozsah uzla a telá heredocov v ňom."""
    o.append((u['od'], u['do']))
    zas = [u]
    while zas:
        x = zas.pop()
        if isinstance(x, dict):
            for hd in x.get('hd') or ():
                o.append(hd)
            for v in x.values():
                if isinstance(v, (dict, list, tuple)) and v and not (isinstance(v, list) and isinstance(v[0], str)):
                    zas.append(v)
        elif isinstance(x, (list, tuple)):
            for v in x:
                if isinstance(v, (dict, list, tuple)) and v and not (isinstance(v, list) and isinstance(v[0], str)):
                    zas.append(v)


def sh_vystrihni(beh, zoz, vymaz, mimo):
    """Z prejdeného zoznamu vynechá to, čo sa v abstraktnom behu nikdy nespustilo."""
    if id(zoz) not in beh.prejdene:
        return
    for ao in zoz['pol']:
        if id(ao) not in beh.navst:
            sh_rozsahy(ao, vymaz)
            continue
        for _, ru in ao['prvky']:
            if id(ru) not in beh.navst:
                sh_rozsahy(ru, vymaz)
                continue
            for c in ru['prik']:
                sh_vystrihni_prikaz(beh, c, vymaz, mimo)


def sh_vystrihni_prikaz(beh, c, vymaz, mimo):
    if id(c) not in beh.navst:
        return
    typ = c['typ']
    if typ == 'if':
        for podm, telo in c['vetvy']:
            if id(podm) not in beh.prejdene:      # skoršia podmienka bola vždy pravdivá
                sh_rozsahy(podm, vymaz)
                if telo['pol']:
                    sh_rozsahy(telo, vymaz)
                continue
            sh_vystrihni(beh, podm, vymaz, mimo)
            if id(telo) not in beh.prejdene:
                if telo['pol']:
                    sh_rozsahy(telo, vymaz)
            else:
                sh_vystrihni(beh, telo, vymaz, mimo)
        if c['inak'] is not None:
            if id(c['inak']) not in beh.prejdene:
                if c['inak']['pol']:
                    sh_rozsahy(c['inak'], vymaz)
            else:
                sh_vystrihni(beh, c['inak'], vymaz, mimo)
    elif typ == 'case':
        for _, telo, _ in c['vetvy']:
            if id(telo) not in beh.prejdene:
                if telo['pol']:
                    sh_rozsahy(telo, vymaz)
            else:
                sh_vystrihni(beh, telo, vymaz, mimo)
    elif typ in ('for', 'cyklus'):
        if c.get('podm') is not None:
            sh_vystrihni(beh, c['podm'], vymaz, mimo)
        sh_vystrihni(beh, c['telo'], vymaz, mimo)
    elif typ in ('blok', 'pod'):
        sh_vystrihni(beh, c['telo'], vymaz, mimo)
    elif typ == 'fun':
        if c['meno'] not in mimo:
            sh_vystrihni_prikaz(beh, c['telo'], vymaz, mimo)


def sh_vsetky_prikazy(u):
    """Všetky jednoduché príkazy v strome (aj v telách funkcií, cykloch, podshelloch)."""
    zas = [u]
    while zas:
        x = zas.pop()
        if isinstance(x, dict):
            if x.get('typ') == 'cmd':
                yield x
                continue
            for v in x.values():
                if isinstance(v, (dict, list, tuple)) and v and not (isinstance(v, list) and isinstance(v[0], str)):
                    zas.append(v)
        elif isinstance(x, (list, tuple)):
            for v in x:
                if isinstance(v, (dict, list, tuple)) and v and not (isinstance(v, list) and isinstance(v[0], str)):
                    zas.append(v)


SH_OBAL_PRED = {'command', 'exec', 'nohup', 'builtin', 'time', 'nice', 'timeout', 'env', 'stdbuf', 'ionice', 'setsid',
                'eval'}
SH_OBAL_ARGUMENTY = re.compile(r'"?\$(?:@|\*|\{@\}|\{\*\})"?|"?\$\{@:1\}"?')


def sh_obaly(obs):
    """Funkcia, ktorá spúšťa svoje argumenty (f() { "$@"; }, run() { echo "+ $*"; "$@"; }): pri volaní `f az …` sa meno
    funkcie nahradí medzerami, aby rozbor posúdil argumenty ako príkaz (2. 10. večer: `run az ad app credential reset`
    v skripte prešiel). Len keď telo spúšťa "$@" priamo (prípadne za command, exec, timeout, env …), bez shift a bez
    sudo, runuser či ssh pred ním (tie posudzuje nastroj_ostro). Čo sa nedá prečítať, ostane bez zmeny."""
    if '$@' not in obs and '$*' not in obs and '${@' not in obs and '${*' not in obs:
        return obs
    try:
        strom = ShStrom(obs)
        obaly = set()
        for meno, defs in strom.funkcie.items():
            # Sondy stráže 10. 10. (PS 4.86 a): meno definované dvakrát sa preskakovalo celé — `f(){ f(){ "$@"; }; f git
            # push --force …; }; f` tak prešlo. Obal je to, keď argumenty spúšťa KAŽDÁ definícia (inak ostáva bez zmeny).
            if not defs:
                continue
            obal = True
            for d in defs:
                if re.search(r'(?:^|[\s;&|(])shift\b', obs[d['od']:d['do']]):
                    obal = False
                    break
                spusta = False
                for c in sh_vsetky_prikazy(d['telo']):
                    sl = [x[1] for x in c['slova']]
                    i = 0
                    while i < len(sl) and (SH_PRIRADENIE.match(sl[i]) or sl[i] in SH_OBAL_PRED
                                           or (i and sl[i - 1] in SH_OBAL_PRED | {'-n', '-s', '-k'}
                                               and re.fullmatch(r'-?[\w.]+', sl[i]))
                                           or (i and re.fullmatch(r'-[\w-]+', sl[i]))):
                        i += 1
                    if i < len(sl) and SH_OBAL_ARGUMENTY.fullmatch(sl[i]):
                        spusta = True
                        break
                if not spusta:
                    obal = False
                    break
            if obal:
                obaly.add(meno)
        if not obaly:
            return obs
        zn = list(obs)
        zmena = False
        vsetky = list(sh_vsetky_prikazy(strom.koren))
        for defs in strom.funkcie.values():     # volanie obalu vnútri tela inej funkcie (sondy 10. 10.)
            for d in defs:
                vsetky += list(sh_vsetky_prikazy(d['telo']))
        for c in vsetky:
            sl = c['slova']
            i = 0
            while i < len(sl) and SH_PRIRADENIE.match(sl[i][1]):
                i += 1
            if i < len(sl) - 1 and sl[i][1] in obaly:
                for k in range(sl[i][2], sl[i][3]):
                    zn[k] = ' '
                zmena = True
        return ''.join(zn) if zmena else obs
    except (_Nevie, RecursionError, ValueError, IndexError, KeyError, TypeError, AttributeError, re.error):
        return obs


# Python: ten istý princíp nad stromom ast — abstraktný beh s argumentmi vyvolania (sys.argv, argparse, premenné pred
# príkazom v os.environ); vynechá sa len to, čo sa pri tomto vyvolaní určite nespustí. Neznáme = obe cesty.

PY_KROKY = 40000
ARG_ROZDEL = SH_ROZDEL          # argument bez úvodzoviek s neznámou hodnotou (počet slov neznámy) — z volby_sh
PY_DYNAMICKE = {'eval', 'exec', 'globals', 'locals', 'vars', 'setattr', 'delattr', '__import__', 'compile'}
PY_OPAKOVANIA = 64
PY_VNORENIE = 8
# 3. 10. (overenie kola 1): sys.argv sa dá zmeniť aj cez iné meno (a = sys.argv; a.append('send')), v slovníku, cez
# funkciu, ktorú kotva nevidí — PyBeh drží sys.argv ako jeden objekt; zmena cez alias = nevynecháva nič, neznáme volanie
# s ním = odteraz neznámy. Vstavané funkcie, ktoré zoznam len čítajú:
PY_CITA_ZOZNAM = {'len', 'print', 'str', 'repr', 'list', 'tuple', 'sorted', 'enumerate', 'set', 'frozenset', 'any', 'all',
                  'iter', 'reversed', 'zip', 'map', 'filter', 'min', 'max', 'sum', 'isinstance', 'bool', 'id', 'type'}
PY_ARGV_MENI = {'append', 'extend', 'insert', 'pop', 'remove', 'clear', 'sort', 'reverse', '__setitem__', '__delitem__',
                '__iadd__', '__imul__'}
# zápis do prostredia (os.environ[…] = …, update, pop, putenv, load_dotenv): hodnota premennej pred príkazom (X=1 python3 …)
# potom nemusí platiť — beh ju berie ako neznámu
PY_ENV_ZMENA = re.compile(r'environ\s*\[[^\]]*\]\s*=(?!=)|environ\s*\.\s*(?:update|pop|clear|popitem|__setitem__)\s*\('
                          r'|\b(?:putenv|unsetenv)\s*\(|\bload_dotenv\b|\bdotenv_values\b|\bdel\s+os\.environ|environ\s*\|=')


class _PyArgv(list):
    """sys.argv v abstraktnom behu — jediný objekt (iný typ než ostatné zoznamy, _py_rovne ho nezamení)."""
    __slots__ = ()


class _PyKoniec(Exception):
    """Výraz určite skončí výnimkou (sys.exit, pomoc argparse, výnimka z volanej funkcie)."""


class _ArgChyba(Exception):
    """argparse určite skončí chybou (neplatný podpríkaz pri úplne známom zozname podpríkazov) — 3. 10., kolo 3."""


class _PyModul:
    __slots__ = ('meno',)

    def __init__(self, meno):
        self.meno = meno


_PY_SYS, _PY_OS, _PY_ENV, _PY_OSPATH, _PY_ARGPARSE = (_PyModul('sys'), _PyModul('os'), _PyModul('os.environ'),
                                                      _PyModul('os.path'), _PyModul('argparse'))
_PY_EXIT = _PyModul('exit')          # sys.exit, exit, quit, os._exit
_PY_PARSER_TRIEDA = _PyModul('ArgumentParser')


class PyNs:
    """Výsledok parse_args: dest → hodnota (chýba = neznáma)."""
    __slots__ = ('h',)

    def __init__(self, h):
        self.h = h


class PyStav:
    __slots__ = ('g', 'l')

    def __init__(self, g=None, l=None):
        self.g = g if g is not None else {}
        self.l = l

    def kopia(self):
        return PyStav(dict(self.g), None if self.l is None else dict(self.l))


def _py_rovne(a, b):
    if a is b:
        return True
    if type(a) is not type(b) or isinstance(a, (PyNs, _PyModul, tuple)):
        return False
    if isinstance(a, list):
        return len(a) == len(b) and all(_py_rovne(x, y) for x, y in zip(a, b))
    return isinstance(a, (str, int, float, bool, type(None))) and a == b


def py_spoj(*stavy):
    st = [x for x in stavy if x is not None]
    if not st:
        return None
    if len(st) == 1:
        return st[0]
    a = st[0]
    g = {k: x for k, x in a.g.items() if all(k in b.g and _py_rovne(b.g[k], x) for b in st[1:])}
    if any(b.l is None for b in st):
        l = None if all(b.l is None for b in st) else {}
    else:
        l = {k: x for k, x in a.l.items() if all(k in b.l and _py_rovne(b.l[k], x) for b in st[1:])}
    return PyStav(g, l)


class PyVysl:
    __slots__ = ('n', 'ret', 'brk', 'cnt', 'exc')

    def __init__(self, n=None, ret=None, brk=None, cnt=None, exc=None):
        self.n, self.ret, self.brk, self.cnt, self.exc = n, ret, brk, cnt, exc

    def prilep(self, r):
        self.ret = py_spoj(self.ret, r.ret)
        self.brk = py_spoj(self.brk, r.brk)
        self.cnt = py_spoj(self.cnt, r.cnt)
        self.exc = py_spoj(self.exc, r.exc)
        return self


def _py_meno(e):
    """a.b.c → 'a.b.c' (len mená a atribúty), inak None."""
    casti = []
    while isinstance(e, ast.Attribute):
        casti.append(e.attr)
        e = e.value
    if isinstance(e, ast.Name):
        casti.append(e.id)
        return '.'.join(reversed(casti))
    return None


def _py_konst(e):
    """Konštanta (aj zoznam/n-tica konštánt) alebo _NEZ."""
    if isinstance(e, ast.Constant):
        return e.value
    if isinstance(e, (ast.List, ast.Tuple)) and all(isinstance(x, ast.Constant) for x in e.elts):
        return [x.value for x in e.elts]
    return _NEZ


class PyInfo:
    """Jeden prechod stromom (výkon: predtým desiatky ast.walk na skript — overenie času 2. 10. večer): väzby mien vo
    funkciách, odkazy a priame volania funkcií, argparse, dynamické prvky (globals, getattr s menom z premennej …)."""

    def __init__(self, strom):
        self.priradenia = []          # (meno, Call) pre `meno = Call(…)` na ktorejkoľvek úrovni
        self.volania_atr = []         # Call s func Attribute (add_argument, set_defaults, add_parser …)
        self.triedy_ap = set()        # mená z `from argparse import ArgumentParser [as X]`
        self.lokalne = {}             # id(funkcie) → (lokálne mená, global mená)
        self.globalne_menene = set()
        self.defs = {}                # meno → [FunctionDef/AsyncFunctionDef] na najvyššej úrovni
        self.odkazy = {}              # meno → počet Name Load
        self.volania = {}             # meno → počet priamych volaní v mieste, ktoré abstraktný beh spúšťa
        self.mena_v_def = {}          # meno funkcie najvyššej úrovne → mená (Name Load) v jej tele
        self.dynamicke = False        # globals()/locals()/vars()/__dict__/sys.modules/getattr s menom z premennej
        self.getattr_mena = set()
        self.ulozenia = []            # Attribute/Subscript v Store/Del (zápis do sys.argv, do výsledku parse_args)
        self.ns_mena = set()          # mená, do ktorých ide výsledok parse_args / parse_known_args (aj n-tica)
        self.slucky = {}              # id(add_parser Call) → {meno premennej cyklu: [doslovné hodnoty]}
        self.eval_exec = False
        for u in strom.body:
            if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef)):
                self.defs.setdefault(u.name, []).append(u)
        # (uzol, funkcia najvyššej úrovne alebo None, vnorené = uzol neprejde abstraktný beh, zásobník funkcií); poradie
        # prechodu nie je dôležité (zbierajú sa množiny a počty)
        zas = [(u, None, False, ()) for u in strom.body]
        FUN = (ast.FunctionDef, ast.AsyncFunctionDef)
        cykly = {}                    # id(uzla) → väzby cyklov nad ním (len pre add_parser)
        for u in ast.walk(strom):
            if type(u) is ast.For and type(u.target) is ast.Name and type(u.iter) in (ast.Tuple, ast.List) \
                    and all(type(x) is ast.Constant and isinstance(x.value, str) for x in u.iter.elts):
                for x in u.body:
                    for y in ast.walk(x):
                        if type(y) is ast.Call and type(y.func) is ast.Attribute and y.func.attr == 'add_parser':
                            cykly.setdefault(id(y), {})[u.target.id] = [z.value for z in u.iter.elts]
        iter_deti = ast.iter_child_nodes
        while zas:
            u, top, vnor, fz = zas.pop()
            t = type(u)
            if t is ast.Name:
                if type(u.ctx) is ast.Load:
                    self.odkazy[u.id] = self.odkazy.get(u.id, 0) + 1
                    if top is not None:
                        self.mena_v_def.setdefault(top.name, set()).add(u.id)
                    if u.id in ('globals', 'locals', 'vars'):
                        self.dynamicke = True
                elif fz and fz[-1] != 'lambda':
                    self.lokalne[fz[-1]][0].add(u.id)
                continue
            if t is ast.Constant:
                continue
            if t in FUN:
                lok = set()
                a = u.args
                for x in a.posonlyargs + a.args + a.kwonlyargs + ([a.vararg] if a.vararg else []) + \
                        ([a.kwarg] if a.kwarg else []):
                    lok.add(x.arg)
                self.lokalne[id(u)] = (lok, set())
                if fz:
                    self.lokalne[fz[-1]][0].add(u.name)
                for d in u.decorator_list + a.defaults + [x for x in a.kw_defaults if x is not None]:
                    zas.append((d, top, vnor, fz))
                nove_top = u if (top is None and not fz and not vnor) else top
                for x in u.body:
                    zas.append((x, nove_top, vnor or bool(fz), fz + (id(u),)))
                continue
            if t is ast.ClassDef or t is ast.Lambda:
                if t is ast.ClassDef and fz:
                    self.lokalne[fz[-1]][0].add(u.name)
                for x in iter_deti(u):
                    zas.append((x, top, True, fz if t is ast.ClassDef else fz + ('lambda',)))
                continue
            if t is ast.Call:
                f = u.func
                if type(f) is ast.Attribute:
                    self.volania_atr.append(u)
                elif type(f) is ast.Name:
                    if not vnor:
                        self.volania[f.id] = self.volania.get(f.id, 0) + 1
                    if f.id == 'getattr':
                        m = _py_konst(u.args[1]) if len(u.args) > 1 else _NEZ
                        if isinstance(m, str):
                            self.getattr_mena.add(m)
                        else:
                            self.dynamicke = True
                    elif f.id in PY_DYNAMICKE:
                        self.eval_exec = True
            elif t is ast.Attribute:
                if u.attr in ('__dict__', 'modules'):
                    self.dynamicke = True
                if type(u.ctx) is not ast.Load:
                    self.ulozenia.append(u)
            elif t is ast.Subscript:
                if type(u.ctx) is not ast.Load:
                    self.ulozenia.append(u)
            elif t is ast.Assign:
                if len(u.targets) == 1 and type(u.targets[0]) is ast.Name and type(u.value) is ast.Call:
                    self.priradenia.append((u.targets[0].id, u.value))
                if type(u.value) is ast.Call and type(u.value.func) is ast.Attribute \
                        and u.value.func.attr.startswith('parse_'):
                    for tg in u.targets:
                        for y in ast.walk(tg):
                            if type(y) is ast.Name:
                                self.ns_mena.add(y.id)
            elif t is ast.Global or t is ast.Nonlocal:
                self.globalne_menene |= set(u.names)
                if fz and fz[-1] != 'lambda':
                    self.lokalne[fz[-1]][1].update(u.names)
            elif t is ast.Import or t is ast.ImportFrom:
                if fz and fz[-1] != 'lambda':
                    for al in u.names:
                        self.lokalne[fz[-1]][0].add((al.asname or al.name).split('.')[0])
                if t is ast.ImportFrom and u.module == 'argparse':
                    self.triedy_ap |= {al.asname or al.name for al in u.names if al.name == 'ArgumentParser'}
            elif t is ast.ExceptHandler and u.name and fz and fz[-1] != 'lambda':
                self.lokalne[fz[-1]][0].add(u.name)
            for x in iter_deti(u):
                zas.append((x, top, vnor, fz))
        for k, (lok, gl) in self.lokalne.items():
            self.lokalne[k] = (lok - gl, gl)
        self.slucky = cykly


# type= argparse (3. 10., overenie kola 1): hodnota voľby prejde funkciou type — tú istú vstavanú funkciu použije kotva
# (int('2') = 2, str.lower('OSTRO') = 'ostro'); iná funkcia (Path, lambda, vlastná, prepísané meno) = hodnota neznáma
PY_TYPY = {'int': int, 'float': float, 'str.lower': str.lower, 'str.upper': str.upper, 'str.strip': str.strip,
           'str.casefold': str.casefold}


def py_typ(uzol, prepisane):
    """type=… → None (str, bez zmeny), funkcia z PY_TYPY, alebo _NEZ (hodnota neznáma)."""
    m = _py_meno(uzol)
    if m == 'str' and 'str' not in prepisane:
        return None
    if m in PY_TYPY and m.split('.')[0] not in prepisane:
        return PY_TYPY[m]
    return _NEZ


def py_preved(typ, h):
    """Hodnota h (text z príkazového riadka či predvolený text) po type= ako v argparse; chyba prevodu = neznáma."""
    if typ is None or not isinstance(h, str):
        return h
    if typ is _NEZ:
        return _NEZ
    try:
        return typ(h)
    except (ValueError, TypeError, OverflowError):
        return _NEZ


class PyArgparse:
    """Staticky prečítané parsery argparse: meno premennej → kľúč parsera; voľby, podpríkazy, predvolené hodnoty.
    Čo sa prečítať nedá (voľba z premennej, parents, prefix_chars, REMAINDER …), urobí parser neistým."""

    def __init__(self, info, prepisane=frozenset()):
        self.parsery = {}       # kľúč → {'volby': [...], 'poz': [...], 'sub': None|(dest, {meno: kľúč}), 'pomoc': bool,
        #                               'abbrev': bool, 'neiste': bool, 'predvol': {}}
        self.mena = {}          # meno premennej → kľúč (None = viac priradení — neisté)
        self.sub = {}           # meno premennej podpríkazov → kľúč rodiča
        self.sub_volania = set()   # id(add_parser Call), ktoré kotva priradila parseru (úplnosť zoznamu podpríkazov)
        self.add_parser = [u for u in info.volania_atr if u.func.attr == 'add_parser']
        self.choices_menene = False   # kód siaha na choices / _name_parser_map podpríkazov (py_volby nastaví)
        triedy = {'argparse.ArgumentParser'} | set(info.triedy_ap)
        priradenia = info.priradenia
        for meno, call in priradenia:
            f = _py_meno(call.func)
            if f in triedy:
                k = id(call)
                self.novy(k, call)
                self.prirad(meno, k)
        # skupiny a podpríkazy (opakuj, kým pribúdajú — poradie priradení v kóde nemusí sedieť s ast.walk)
        for _ in range(4):
            for meno, call in priradenia:
                f = call.func
                if not isinstance(f, ast.Attribute) or not isinstance(f.value, ast.Name):
                    continue
                rodic = f.value.id
                if f.attr in ('add_mutually_exclusive_group', 'add_argument_group') and self.mena.get(rodic):
                    self.prirad(meno, self.mena[rodic])
                elif f.attr == 'add_subparsers' and self.mena.get(rodic):
                    k = self.mena[rodic]
                    dest = next((_py_konst(x.value) for x in call.keywords if x.arg == 'dest'), None)
                    if self.parsery[k]['sub'] is None:
                        self.parsery[k]['sub'] = (dest if isinstance(dest, str) or dest is None else _NEZ, {})
                    self.sub[meno] = k
                elif f.attr == 'add_parser' and rodic in self.sub:
                    self.pridaj_sub(call, rodic, info)
                    self.prirad(meno, id(call))
        # 3. 10. (kolo 2): podpríkaz bez priradenia — `sub.add_parser("whoami")` ako samostatný príkaz (graph_mail.py);
        # predtým ho vetvy režimov podľa regulárnych výrazov nepotrebovali, abstraktný beh áno
        for u in info.volania_atr:
            if u.func.attr == 'add_parser' and isinstance(u.func.value, ast.Name) and u.func.value.id in self.sub \
                    and id(u) not in self.parsery:
                self.pridaj_sub(u, u.func.value.id, info)
        for u in info.volania_atr:
            f = u.func
            if f.attr == 'add_parser':
                cil = f.value.id if isinstance(f.value, ast.Name) else None
                if cil not in self.sub:
                    for k in self.parsery.values():
                        k['neiste'] = k['neiste'] or k['sub'] is not None
            if f.attr not in ('add_argument', 'set_defaults'):
                continue
            if isinstance(f.value, ast.Name):
                k = self.mena.get(f.value.id)
            elif isinstance(f.value, ast.Call) and isinstance(f.value.func, ast.Attribute) \
                    and f.value.func.attr == 'add_parser':
                k = id(f.value) if id(f.value) in self.parsery else None
            else:
                k = None
            if k is None:
                continue
            p = self.parsery[k]
            if f.attr == 'set_defaults':
                for x in u.keywords:
                    v = _py_konst(x.value) if x.arg else _NEZ
                    if x.arg is None:
                        p['neiste'] = True
                    else:
                        p['predvol'][x.arg] = v
                continue
            mena = [_py_konst(x) for x in u.args]
            if any(not isinstance(m, str) for m in mena) or not mena or any(x.arg is None for x in u.keywords):
                p['neiste'] = p['struktura'] = True
                continue
            kw = {x.arg: x.value for x in u.keywords}
            akcia = _py_konst(kw['action']) if 'action' in kw else 'store'
            nargs = _py_konst(kw['nargs']) if 'nargs' in kw else None
            if not isinstance(akcia, str) or nargs not in (None, 1) and not (nargs == '?' and akcia == 'store'):
                if nargs in ('...',) or (isinstance(kw.get('nargs'), ast.Attribute) and kw['nargs'].attr == 'REMAINDER'):
                    p['zvysok'] = True
                p['neiste'] = True
            dest = _py_konst(kw['dest']) if 'dest' in kw else None
            # 3. 10. (overenie kola 1: `--krok 2` s type=int porovnané s 2 ako reťazec — vetva s obnovou kľúča vypadla):
            # hodnotu po type= (okrem str: int, float, str.lower, Path, vlastná funkcia) kotva nepozná
            typ = py_typ(kw['type'], prepisane) if 'type' in kw else None
            if not mena[0].startswith('-'):
                p['poz'].append(dest or mena[0])
                if typ is not None:
                    p['typ'][dest or mena[0]] = typ
                continue
            if any(m in ('-h', '--help') for m in mena):
                p['struktura'] = True                   # vlastná voľba -h/--help (add_help=False)
            if dest is None:
                dlhe = [m for m in mena if m.startswith('--')]
                dest = (dlhe[0] if dlhe else mena[0]).lstrip('-').replace('-', '_')
            pred = _py_konst(kw['default']) if 'default' in kw else ({'store_true': False, 'store_false': True}
                                                                     .get(akcia))
            p['volby'].append({'mena': mena, 'akcia': akcia, 'dest': dest, 'const': _py_konst(kw['const'])
                               if 'const' in kw else None, 'pred': pred, 'nargs': nargs, 'typ': typ})
            if typ is not None:
                p['typ'][dest] = typ if p['typ'].get(dest, typ) is typ else _NEZ

    def pridaj_sub(self, call, rodic, info):
        """Podpríkaz add_parser na premennej podpríkazov rodic: meno (aj z cyklu cez doslovný zoznam) → nový parser."""
        k = self.sub[rodic]
        m = _py_konst(call.args[0]) if call.args else _NEZ
        if call.args and isinstance(call.args[0], ast.Name) and id(call) in info.slucky \
                and call.args[0].id in info.slucky[id(call)]:
            m = info.slucky[id(call)][call.args[0].id]     # mená z cyklu cez doslovný zoznam
        k2 = id(call)
        if k2 not in self.parsery:
            self.novy(k2, call)
        if k2 not in self.parsery[k]['sub_vsetky']:
            self.parsery[k]['sub_vsetky'].append(k2)
        self.sub_volania.add(k2)
        # 3. 10. (kolo 3): aliasy podpríkazu (aliases=['s']) sú platné slová ako meno; neprečítané aliasy alebo **kw
        # = parser neistý (inak by alias vyzeral ako neplatný podpríkaz a beh by ho vzal za koniec)
        aliasy = []
        for x in call.keywords:
            if x.arg is None:
                self.parsery[k]['neiste'] = True
            elif x.arg == 'aliases':
                a = _py_konst(x.value)
                if isinstance(a, list) and all(isinstance(y, str) for y in a):
                    aliasy = a
                else:
                    self.parsery[k]['neiste'] = True
        if isinstance(m, str):
            for x in [m] + aliasy:
                self.parsery[k]['sub'][1][x] = k2
        elif isinstance(m, list) and all(isinstance(x, str) for x in m) and not aliasy:
            for x in m:
                self.parsery[k]['sub'][1][x] = k2
        else:
            self.parsery[k]['neiste'] = True       # meno podpríkazu z premennej

    def podprikazy_uplne(self, k):
        """Kotva pozná všetky podpríkazy parsera k: parser nie je neistý, každé add_parser v kóde priradila niektorému
        parseru a kód nesiaha na choices / _name_parser_map. Len vtedy je slovo mimo zoznamu istá chyba argparse."""
        p = self.parsery.get(k)
        return bool(p) and p['sub'] is not None and not p['neiste'] and not p['struktura'] and not p['zvysok'] \
            and not self.choices_menene and all(id(u) in self.sub_volania for u in self.add_parser) \
            and all(not self.parsery[x]['neiste'] for x in p['sub_vsetky'])

    def novy(self, k, call):
        kw = {x.arg: x.value for x in call.keywords}
        struktura = any(x in kw for x in ('prefix_chars', 'fromfile_prefix_chars', 'parents', 'conflict_handler')) \
            or any(x.arg is None for x in call.keywords)
        pomoc = _py_konst(kw['add_help']) if 'add_help' in kw else True
        abbrev = _py_konst(kw['allow_abbrev']) if 'allow_abbrev' in kw else True
        struktura = struktura or not isinstance(pomoc, bool) or not isinstance(abbrev, bool)
        # struktura: voľby sa nedajú prečítať vôbec (ani pomoc); neiste: hodnoty dest sa nedajú predpovedať
        self.parsery[k] = {'volby': [], 'poz': [], 'sub': None, 'pomoc': pomoc is not False, 'abbrev': abbrev is not False,
                           'neiste': struktura or 'argument_default' in kw, 'struktura': struktura,
                           'predvol': {}, 'zvysok': False, 'sub_vsetky': [], 'typ': {}}

    def prirad(self, meno, k):
        if meno in self.mena and self.mena[meno] != k:
            self.mena[meno] = None
        else:
            self.mena[meno] = k

    def pomoc_v(self, k, tokeny):
        """Vyvolanie si vypýta pomoc (-h, --help, skratka --he) a parser ju spracuje → True; neisté → None."""
        p = self.parsery.get(k)
        if p is None or p['zvysok'] or p['struktura']:
            return None
        for i, t in enumerate(tokeny):
            if not isinstance(t, str):
                return None
            if t == '--':
                return False
            if p['pomoc'] and (t == '-h' or t == '--help' or (p['abbrev'] and len(t) > 3 and '--help'.startswith(t)
                                                               and not any(o.startswith(t) for v in p['volby']
                                                                           for o in v['mena']))):
                return True
            if p['sub'] is not None and not t.startswith('-'):
                if p['poz']:
                    return None            # parser má aj vlastné pozičné argumenty — podpríkaz nemusí byť toto slovo
                k2 = p['sub'][1].get(t)
                if k2 is not None:
                    return self.pomoc_v(k2, tokeny[i + 1:])
                # meno podpríkazu sa nedá prečítať (pridané v cykle) alebo neplatí: platný podpríkaz s --help vypíše
                # pomoc, neplatný skončí chybou — oboje skončí, keď každý podparser má pomoc a nemá REMAINDER
                zvysok = tokeny[i + 1:tokeny.index('--', i + 1) if '--' in tokeny[i + 1:] else None]
                if any(x in ('-h', '--help') for x in zvysok) and p['sub_vsetky'] and all(
                        self.parsery[x]['pomoc'] and not self.parsery[x]['zvysok'] and not self.parsery[x]['struktura']
                        for x in p['sub_vsetky']):
                    return True
                return None
        return False

    def ns(self, k, tokeny, retaz=None):
        """Hodnoty dest pri týchto argumentoch: {dest: hodnota}, alebo None (neisté — chyba, neznámy argument …).
        retaz (zoznam) dostane kľúče podparserov, ktoré vyvolanie vybralo (15. 10., py_mrtve_predvolby)."""
        p = self.parsery.get(k)
        if p is None or p['neiste']:
            return None
        h = {}
        for v in p['volby']:
            if v['akcia'] in ('help', 'version'):
                continue
            h[v['dest']] = v['pred']
        h.update(p['predvol'])
        for d, typ in p['typ'].items():
            if isinstance(h.get(d), str):
                h[d] = py_preved(typ, h[d])   # predvolený reťazec argparse prevedie cez type= (3. 10.)
        if p['sub'] is not None and isinstance(p['sub'][0], str):
            h[p['sub'][0]] = None
        poz = list(p['poz'])
        i = 0
        while i < len(tokeny):
            t = tokeny[i]
            if t is ARG_ROZDEL:
                # slovo bez úvodzoviek s neznámou hodnotou: môže pridať hocijaké voľby (aj žiadne) — parser bez pozičných
                # argumentov a s už vybraným podpríkazom ho zvládne: dest, ktoré ďalej nenastaví doslovná voľba, sú
                # neznáme (vložená „--“ či neznáma voľba = chyba argparse, teda koniec)
                if poz or p['sub'] is not None:
                    return None
                self.neznáme_volby(p, h)
                i += 1
                continue
            if not isinstance(t, str):
                return None
            if t == '--':
                return None if tokeny[i + 1:] else h
            if t.startswith('-') and t != '-' and not re.fullmatch(r'-\d+(\.\d+)?', t):
                if t.startswith('--'):
                    meno, rov, hodn = t.partition('=')
                    zhody = [v for v in p['volby'] if meno in v['mena']]
                    if not zhody and p['abbrev']:
                        zhody = [v for v in p['volby'] if any(o.startswith(meno) and o.startswith('--') for o in v['mena'])]
                    if len(zhody) != 1:
                        return None
                    v = zhody[0]
                    i = self.akcia(v, h, rov, hodn, tokeny, i, p)
                    if i is None:
                        return None
                    continue
                kratke = t[1:]
                j = 0
                while j < len(kratke):
                    zhody = [v for v in p['volby'] if '-' + kratke[j] in v['mena']]
                    if len(zhody) != 1:
                        return None
                    v = zhody[0]
                    if v['akcia'] in ('store', 'append', 'extend'):
                        zv = kratke[j + 1:]
                        i = self.akcia(v, h, bool(zv), zv, tokeny, i, p)
                        if i is None:
                            return None
                        break
                    if self.akcia(v, h, '', '', tokeny, i) is None:
                        return None
                    j += 1
                else:
                    i += 1
                continue
            if poz:
                d = poz.pop(0)
                h[d] = py_preved(p['typ'].get(d), t)
                i += 1
                continue
            if p['sub'] is not None:
                k2 = p['sub'][1].get(t)
                if k2 is None:
                    # 3. 10. (kolo 3): slovo, ktoré nie je podpríkazom, argparse odmietne (invalid choice) a skončí —
                    # isté len pri úplne známom zozname podpríkazov; inak neisté ako doteraz
                    if self.podprikazy_uplne(k):
                        raise _ArgChyba()
                    return None
                if isinstance(p['sub'][0], str):
                    h[p['sub'][0]] = t
                if retaz is not None:
                    retaz.append(k2)
                h2 = self.ns(k2, tokeny[i + 1:], retaz)
                if h2 is None:
                    return None
                h.update(h2)
                return h
            return None
        if poz:
            return None
        return h

    def neznáme_volby(self, p, h):
        for v in p['volby']:
            if v['akcia'] not in ('help', 'version'):
                h[v['dest']] = _NEZ

    def akcia(self, v, h, rov, hodn, tokeny, i, p=None):
        """Uplatní voľbu v na h; vráti index ďalšieho tokenu alebo None (neisté)."""
        a = v['akcia']
        if a == 'store_true':
            h[v['dest']] = True
        elif a == 'store_false':
            h[v['dest']] = False
        elif a == 'store_const':
            h[v['dest']] = v['const']
        elif a == 'count':
            h[v['dest']] = (h.get(v['dest']) or 0) + 1 if isinstance(h.get(v['dest']), int) or h.get(v['dest']) is None \
                else _NEZ
        elif a in ('store', 'append', 'extend'):
            if v['nargs'] == '?' and not rov:
                return None
            if rov:
                hodnota = hodn
            else:
                # neznámy argument na mieste hodnoty je hodnota (začína sa „-“ → argparse skončí chybou, nič ďalej)
                if i + 1 >= len(tokeny) or (isinstance(tokeny[i + 1], str) and tokeny[i + 1].startswith('-')
                                            and tokeny[i + 1] != '-'):
                    return None
                hodnota = tokeny[i + 1] if isinstance(tokeny[i + 1], str) else _NEZ
                if tokeny[i + 1] is ARG_ROZDEL:
                    # hodnota bez úvodzoviek: za ňou môžu byť vložené voľby (rovnako ako ARG_ROZDEL v ns)
                    if p is None or p['poz'] or p['sub'] is not None:
                        return None
                    self.neznáme_volby(p, h)
                i += 1
            h[v['dest']] = py_preved(v.get('typ'), hodnota) if a == 'store' else _NEZ
        else:
            return None
        return i + 1


class PyBeh:
    """Abstraktný beh modulu pythonu; zaznamená spustené príkazy (navst) a prejdené bloky (prejdene)."""

    def __init__(self, strom, tokeny, prefix, ap, info):
        self.strom = strom
        # tokeny None = počet argumentov neznámy (slovo bez úvodzoviek s neznámou hodnotou sa môže rozdeliť)
        self.argv = _NEZ if tokeny is None or ARG_ROZDEL in tokeny else ['skript'] + [_NEZ if t is None else t for t in tokeny]
        self.argv_obj = _PyArgv(self.argv) if isinstance(self.argv, list) else None
        self.tokeny = tokeny
        self.prefix = prefix
        self.ap = ap
        self.navst = set()
        self.prejdene = {}
        self.kroky = 0
        self.hlbka = 0
        self.ramec = None        # (lokálne mená, global mená) volanej funkcie
        self.mozne = []          # možné výnimky počas vyhodnotenia výrazu (stav)
        self.lokalne = info.lokalne
        self.globalne_menene = info.globalne_menene      # spresní py_volby: len funkcie, ktoré beh nespúšťa
        self.zmenene = set()     # id zoznamov, ktoré skript menil (append, x[i] = …) — ich hodnota je neznáma
        self.env_zmena = False   # skript zapisuje do prostredia (PY_ENV_ZMENA) — premenné pred príkazom neplatia
        self.prepisane = set()   # mená, ktoré skript sám viaže (py_prepisane) — nie vstavané funkcie
        self._uklada = {}        # id(cyklu) → (mená, ktoré telo mení, volá niečo)
        self.parse_vyriesene = {}   # id(ap.parse_args) → {(kľúč parsera, vybrané parsery)} (py_mrtve_predvolby)

    def krok(self):
        self.kroky += 1
        if self.kroky > PY_KROKY:
            raise _Nevie('priveľa krokov')

    def sys_argv(self):
        """sys.argv: stále ten istý objekt; po neznámom volaní, ktoré ho dostalo, neznámy."""
        if self.argv_obj is None or id(self.argv_obj) in self.zmenene:
            return _NEZ
        return self.argv_obj

    def unik_argv(self, h):
        """Hodnota h (aj vnorená v zozname) obsahuje sys.argv a odchádza tam, kam beh nevidí — odteraz je neznámy."""
        if self.argv_obj is None:
            return
        zas, videne = [h], set()
        while zas:
            x = zas.pop()
            if x is self.argv_obj:
                self.zmenene.add(id(self.argv_obj))
                return
            if isinstance(x, list) and id(x) not in videne:
                videne.add(id(x))
                zas.extend(x)

    # --- mená ---
    def citaj(self, meno, st):
        if self.ramec is not None and meno in self.ramec[0]:
            h = st.l.get(meno, _NEZ) if st.l is not None else _NEZ
        else:
            h = st.g.get(meno, _NEZ)
        if h is not _NEZ:
            return _NEZ if isinstance(h, list) and id(h) in self.zmenene else h
        if meno in st.g:
            return st.g[meno]
        if meno == '__name__':
            return '__main__'
        if meno in ('exit', 'quit'):
            return _PY_EXIT
        return _NEZ

    def zapis(self, meno, hodn, st):
        if self.ramec is not None and meno in self.ramec[0]:
            if st.l is not None:
                if hodn is _NEZ:
                    st.l.pop(meno, None)
                else:
                    st.l[meno] = hodn
            return
        if hodn is _NEZ:
            st.g.pop(meno, None)
        else:
            st.g[meno] = hodn

    def prirad(self, ciel, hodn, st):
        if isinstance(ciel, ast.Name):
            self.zapis(ciel.id, hodn, st)
        elif isinstance(ciel, (ast.Tuple, ast.List)):
            hs = hodn if isinstance(hodn, list) and len(hodn) == len(ciel.elts) else None
            for k, x in enumerate(ciel.elts):
                self.prirad(x.value if isinstance(x, ast.Starred) else x,
                            hs[k] if hs is not None and not isinstance(x, ast.Starred) else _NEZ, st)
        elif isinstance(ciel, ast.Starred):
            self.prirad(ciel.value, _NEZ, st)
        else:
            # obj.atr = …, x[i] = … — vyhodnotia sa len podvýrazy; zoznam, do ktorého sa zapisuje, je odteraz neznámy
            self.unik_argv(hodn)
            if isinstance(ciel, ast.Subscript):
                self.zmen(ciel.value, st)
            self.ev(ciel, st, ciel_zapisu=True)

    def zmen(self, e, st):
        """Zoznam sa mení (append, x[i] = …, del x[i]): sys.argv = nevynechať nič; iný zoznam je odteraz neznámy."""
        if _py_meno(e) in ('sys.argv', 'argv') and (_py_meno(e) == 'sys.argv' or isinstance(self.citaj('argv', st), list)):
            raise _Nevie('zmena sys.argv')
        h = self.ev(e, st)
        if self.argv_obj is not None and h is self.argv_obj:
            raise _Nevie('zmena sys.argv cez iné meno')
        if isinstance(h, list):
            self.zmenene.add(id(h))

    # --- výrazy ---
    def mozno(self, e, st):
        """Výraz, ktorý sa možno vyhodnotí: istá výnimka je len možná."""
        try:
            return self.ev(e, st)
        except _PyKoniec:
            self.mozne.append(st.kopia())
            return _NEZ

    def pravda(self, h):
        if h is _NEZ or isinstance(h, (_PyModul, PyNs)):
            return None if h is _NEZ else True
        if isinstance(h, list):
            return len(h) > 0
        try:
            return bool(h)
        except Exception:
            # PONECHANÉ (1. 11. 2026): `None` tu NIE JE ticho — je to odpoveď „neviem“ v trojhodnotovej logike
            # (`podmienka`), pri ktorej abstraktný beh preskúma OBE vetvy, takže zábrana nič nepustí. Druh chyby
            # nevieme zúžiť: `bool(h)` vyvolá `__bool__` hodnoty z cudzieho kódu a ten môže vyhodiť čokoľvek.
            return None   # PONECHANÉ: „neviem“ je tu bezpečná strana (obe vetvy sa preskúmajú)

    def podmienka(self, e, st):
        """Pravdivosť výrazu v podmienke: True/False/None (3-hodnotová logika pre and/or/not)."""
        if isinstance(e, ast.BoolOp):
            vys = True if isinstance(e.op, ast.And) else False
            nezn = False
            for k, x in enumerate(e.values):
                p = self.podmienka(x, st) if k == 0 or not nezn else self.mozno_podm(x, st)
                if isinstance(e.op, ast.And):
                    if p is False:
                        return False if not nezn else (False if p is False else None)
                else:
                    if p is True:
                        return True if not nezn else (True if p is True else None)
                if p is None:
                    nezn = True
            return None if nezn else vys
        if isinstance(e, ast.UnaryOp) and isinstance(e.op, ast.Not):
            p = self.podmienka(e.operand, st)
            return None if p is None else not p
        return self.pravda(self.ev(e, st))

    def mozno_podm(self, e, st):
        try:
            return self.podmienka(e, st)
        except _PyKoniec:
            self.mozne.append(st.kopia())
            return None

    def ev(self, e, st, ciel_zapisu=False):
        self.krok()
        if isinstance(e, ast.Constant):
            return e.value
        if isinstance(e, ast.Name):
            return self.citaj(e.id, st)
        if isinstance(e, ast.Attribute):
            o = self.ev(e.value, st)
            if ciel_zapisu:
                return _NEZ
            if o is _PY_SYS:
                if e.attr == 'argv':
                    return self.sys_argv()
                if e.attr == 'exit':
                    return _PY_EXIT
                return _NEZ
            if o is _PY_OS:
                return {'environ': _PY_ENV, 'path': _PY_OSPATH, '_exit': _PY_EXIT}.get(e.attr, _NEZ)
            if isinstance(o, PyNs):
                return o.h.get(e.attr, _NEZ)
            if o is _PY_ARGPARSE and e.attr == 'ArgumentParser':
                return _PY_PARSER_TRIEDA
            return _NEZ
        if isinstance(e, ast.Subscript):
            o = self.ev(e.value, st)
            if isinstance(e.slice, ast.Slice):
                a = self.ev(e.slice.lower, st) if e.slice.lower else None
                b = self.ev(e.slice.upper, st) if e.slice.upper else None
                c = self.ev(e.slice.step, st) if e.slice.step else None
                if isinstance(o, (list, str)) and all(x is None or isinstance(x, int) for x in (a, b, c)):
                    return o[a:b:c]
                return _NEZ
            i = self.ev(e.slice, st)
            if ciel_zapisu:
                return _NEZ
            if isinstance(o, (list, str)) and isinstance(i, int) and not isinstance(i, bool):
                try:
                    return o[i]
                except IndexError:
                    return _NEZ
            if o is _PY_ENV and isinstance(i, str):
                return self.prostredie(i, _NEZ)
            return _NEZ
        if isinstance(e, ast.Compare):
            a = self.ev(e.left, st)
            vys = True
            for op, pr in zip(e.ops, e.comparators):
                b = self.ev(pr, st)
                r = self.porovnaj(a, op, b)
                if r is False:
                    for zv in e.comparators[e.comparators.index(pr) + 1:]:
                        self.mozno(zv, st)
                    return False
                if r is _NEZ:
                    vys = _NEZ
                a = b
            return vys
        if isinstance(e, ast.BoolOp):
            a_ = isinstance(e.op, ast.And)
            h = _NEZ
            for k, x in enumerate(e.values):
                h = self.ev(x, st)
                p = self.pravda(h)
                if p is None:
                    for y in e.values[k + 1:]:
                        self.mozno(y, st)
                    return _NEZ
                if p is not a_:              # and: prvý nepravdivý · or: prvý pravdivý
                    return h
            return h
        if isinstance(e, ast.UnaryOp):
            h = self.ev(e.operand, st)
            if isinstance(e.op, ast.Not):
                p = self.pravda(h)
                return _NEZ if p is None else not p
            if isinstance(e.op, ast.USub) and isinstance(h, (int, float)) and not isinstance(h, bool):
                return -h
            return _NEZ
        if isinstance(e, ast.IfExp):
            p = self.podmienka(e.test, st)
            if p is True:
                return self.ev(e.body, st)
            if p is False:
                return self.ev(e.orelse, st)
            a = self.mozno(e.body, st)
            b = self.mozno(e.orelse, st)
            return a if _py_rovne(a, b) else _NEZ
        if isinstance(e, (ast.List, ast.Tuple)):
            h = [self.ev(x, st) for x in e.elts if not isinstance(x, ast.Starred)]
            for x in e.elts:
                if isinstance(x, ast.Starred):
                    self.ev(x.value, st)
                    return _NEZ
            return h
        if isinstance(e, ast.Call):
            return self.volanie(e, st)
        if isinstance(e, ast.NamedExpr):
            h = self.ev(e.value, st)
            self.prirad(e.target, h, st)
            return h
        if isinstance(e, ast.Lambda):
            return _NEZ
        if isinstance(e, (ast.ListComp, ast.SetComp, ast.GeneratorExp, ast.DictComp)):
            for gen in e.generators:
                self.ev(gen.iter, st)
            st2 = st.kopia()
            for gen in e.generators:
                self.prirad(gen.target, _NEZ, st2)
                for c in gen.ifs:
                    self.mozno(c, st2)
            for x in ([e.key, e.value] if isinstance(e, ast.DictComp) else [e.elt]):
                self.mozno(x, st2)
            return _NEZ
        for x in ast.iter_child_nodes(e):             # f-reťazce, slovníky, binárne operácie … — len účinky volaní
            if isinstance(x, ast.expr):
                self.unik_argv(self.ev(x, st))       # sys.argv v slovníku, množine, operácii — beh ho ďalej nevidí
        if isinstance(e, ast.BinOp):
            return _NEZ
        return _NEZ

    def prostredie(self, meno, inak):
        if self.env_zmena:
            return _NEZ
        for x in self.prefix:
            m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)=(.*)$', x, re.S)
            if m and m.group(1) == meno and not re.search(r'[$`\\\'"]', m.group(2)):
                return m.group(2)
        return _NEZ

    def porovnaj(self, a, op, b):
        if isinstance(op, (ast.In, ast.NotIn)):
            if isinstance(b, (list, str)) and a is not _NEZ and not isinstance(a, (_PyModul, PyNs)):
                if isinstance(b, str):
                    r = a in b if isinstance(a, str) else _NEZ
                elif any(_py_rovne(a, x) for x in b):
                    r = True
                elif any(x is _NEZ for x in b):
                    r = _NEZ
                else:
                    r = False
                if r is _NEZ:
                    return _NEZ
                return r if isinstance(op, ast.In) else not r
            return _NEZ
        if a is _NEZ or b is _NEZ or isinstance(a, (_PyModul, PyNs)) or isinstance(b, (_PyModul, PyNs)):
            return _NEZ
        if isinstance(op, (ast.Is, ast.IsNot)):
            if a is None or b is None or isinstance(a, bool) or isinstance(b, bool):
                r = a is b
                return r if isinstance(op, ast.Is) else not r
            return _NEZ
        try:
            return {ast.Eq: lambda: a == b, ast.NotEq: lambda: a != b, ast.Lt: lambda: a < b, ast.LtE: lambda: a <= b,
                    ast.Gt: lambda: a > b, ast.GtE: lambda: a >= b}[type(op)]()
        except (TypeError, KeyError):
            return _NEZ

    def volanie(self, e, st):
        f = self.ev(e.func, st)
        args = []
        vsetky = []                                  # hodnoty všetkých argumentov (aj *x, **x)
        for x in e.args:
            if isinstance(x, ast.Starred):
                vsetky.append(self.ev(x.value, st))
                args = None
            elif args is not None:
                args.append(self.ev(x, st))
                vsetky.append(args[-1])
            else:
                vsetky.append(self.ev(x, st))
        kw = {}
        for x in e.keywords:
            h = self.ev(x.value, st)
            vsetky.append(h)
            if x.arg is None:
                kw = None
            elif kw is not None:
                kw[x.arg] = h
        if not (isinstance(f, tuple) and f and f[0] == 'fun'):
            cita = isinstance(e.func, ast.Name) and e.func.id in PY_CITA_ZOZNAM and e.func.id not in self.prepisane
            for h in vsetky:                         # neznáme volanie dostalo sys.argv — môže ho zmeniť
                if not (cita and h is self.argv_obj):    # vstavaná funkcia číta prvky; [sys.argv] by dostal map(f, …)
                    self.unik_argv(h)
        if f is _PY_EXIT:
            raise _PyKoniec()
        if isinstance(e.func, ast.Name) and e.func.id in PY_DYNAMICKE:
            raise _Nevie('dynamický kód')
        if isinstance(e.func, ast.Name) and e.func.id == 'len' and args and len(args) == 1 \
                and isinstance(args[0], (list, str)):
            return len(args[0])
        if f is _PY_PARSER_TRIEDA:
            return _NEZ
        if isinstance(e.func, ast.Attribute):
            meno_obj = e.func.value.id if isinstance(e.func.value, ast.Name) else None
            if e.func.attr in ('parse_args', 'parse_known_args', 'parse_intermixed_args') and meno_obj is not None:
                return self.parse(meno_obj, e, args, st)
            obj = self.ev(e.func.value, st) if not isinstance(e.func.value, ast.Name) else self.citaj(meno_obj, st)
            if obj is _PY_ENV and e.func.attr in ('get', 'setdefault') and args and isinstance(args[0], str):
                return self.prostredie(args[0], _NEZ)
            if obj is _PY_OS and e.func.attr == 'getenv' and args and isinstance(args[0], str):
                return self.prostredie(args[0], _NEZ)
            if e.func.attr in ('append', 'extend', 'insert', 'pop', 'remove', 'clear', 'sort', 'reverse') \
                    and (isinstance(obj, list) or _py_meno(e.func.value) in ('sys.argv', 'argv')):
                self.zmen(e.func.value, st)
        if isinstance(f, tuple) and f and f[0] == 'fun':
            return self.funkcia(f[1], args, kw, st)
        # neznáme volanie: môže zmeniť globálne premenné, ktoré funkcie skriptu menia cez global
        for m in self.globalne_menene:
            st.g.pop(m, None)
        return _NEZ

    def parse(self, meno, e, args, st):
        k = self.ap.mena.get(meno)
        if k is None or (args and not (len(e.args) == 1 and _py_meno(e.args[0]) is None
                                      and isinstance(e.args[0], ast.Subscript) and _py_meno(e.args[0].value) == 'sys.argv'
                                      and isinstance(e.args[0].slice, ast.Slice) and _py_konst(e.args[0].slice.lower) == 1
                                      and e.args[0].slice.upper is None)):
            return _NEZ
        if self.tokeny is None:
            return _NEZ
        tok = [_NEZ if t is None else t for t in self.tokeny]
        p = self.ap.pomoc_v(k, tok)
        if p is True:
            raise _PyKoniec()                  # argparse vypíše pomoc a skončí (SystemExit)
        retaz = [k]
        try:
            h = self.ap.ns(k, tok, retaz)
        except _ArgChyba:
            raise _PyKoniec()                  # neplatný podpríkaz: argparse vypíše chybu a skončí (SystemExit 2)
        # pomoc neistá (neznámy argument na mieste hodnoty): parse_args buď vráti h, alebo skončí (pomoc, chyba) — v oboch
        # prípadoch platí, čo sa podľa h vynechá
        if h is None:
            return _NEZ
        if self.argv_obj is None or id(self.argv_obj) not in self.zmenene:
            self.parse_vyriesene.setdefault(id(e.func), set()).add((k, frozenset(retaz)))
        ns = PyNs(dict(h))
        return [ns, _NEZ] if e.func.attr == 'parse_known_args' else ns

    def funkcia(self, u, args, kw, st):
        if self.hlbka >= PY_VNORENIE:
            raise _Nevie('hlboké volania')
        lok, gl = self.lokalne[id(u)]
        l = {}
        a = u.args
        mena = [x.arg for x in a.posonlyargs + a.args]
        pred = [None] * (len(mena) - len(a.defaults)) + list(a.defaults)
        if args is None or kw is None or a.vararg or a.kwarg or a.kwonlyargs:
            l = {}
        else:
            for k, m in enumerate(mena):
                if k < len(args):
                    h = args[k]
                elif m in kw:
                    h = kw[m]
                elif pred[k] is not None:
                    h = _py_konst(pred[k])
                else:
                    h = _NEZ
                if h is not _NEZ:
                    l[m] = h
        st2 = PyStav(st.g, l)                    # globálne sú spoločné (zmeny cez global ostanú)
        ulozene = self.ramec
        self.ramec = (lok, gl)
        self.hlbka += 1
        try:
            r = self.blok(u.body, st2)
        finally:
            self.hlbka -= 1
            self.ramec = ulozene
        spat = py_spoj(r.n, r.ret)
        if r.exc is not None:
            self.mozne.append(PyStav(dict(r.exc.g), st.l))
        if spat is None:
            raise _PyKoniec()
        st.g = dict(spat.g)
        return _NEZ

    # --- príkazy ---
    def blok(self, prikazy, st):
        self.prejdene[id(prikazy)] = self.prejdene.get(id(prikazy), 0) + 1
        vys = PyVysl()
        cur = st
        for p in prikazy:
            if cur is None:
                break
            self.krok()
            self.navst.add(id(p))
            r = self.prikaz(p, cur.kopia())
            vys.prilep(r)
            cur = r.n
        vys.n = cur
        return vys

    def prikaz(self, p, st):
        ulozene = self.mozne
        self.mozne = []
        try:
            r = self._prikaz(p, st)
        except _PyKoniec:
            r = PyVysl(exc=st)
        finally:
            mozne = self.mozne
            self.mozne = ulozene
        if mozne:
            r.exc = py_spoj(r.exc, *mozne)
        return r

    def _prikaz(self, p, st):
        if isinstance(p, ast.Expr):
            self.ev(p.value, st)
            return PyVysl(n=st)
        if isinstance(p, ast.Assign):
            h = self.ev(p.value, st)
            for t in p.targets:
                self.prirad(t, h, st)
            return PyVysl(n=st)
        if isinstance(p, ast.AnnAssign):
            if p.value is not None:
                self.prirad(p.target, self.ev(p.value, st), st)
            return PyVysl(n=st)
        if isinstance(p, ast.AugAssign):
            self.ev(p.value, st)
            self.prirad(p.target, _NEZ, st)
            return PyVysl(n=st)
        if isinstance(p, ast.If):
            c = self.podmienka(p.test, st)
            vys = PyVysl()
            if c is not False:
                r = self.blok(p.body, st.kopia())
                vys.prilep(r)
                vys.n = py_spoj(vys.n, r.n)
            if c is not True:
                if p.orelse:
                    r = self.blok(p.orelse, st.kopia())
                    vys.prilep(r)
                    vys.n = py_spoj(vys.n, r.n)
                else:
                    vys.n = py_spoj(vys.n, st)
            return vys
        if isinstance(p, (ast.For, ast.AsyncFor)):
            return self.for_(p, st)
        if isinstance(p, ast.While):
            return self.while_(p, st)
        if isinstance(p, (ast.Try, getattr(ast, 'TryStar', ast.Try))):
            return self.try_(p, st)
        if isinstance(p, (ast.With, ast.AsyncWith)):
            for it in p.items:
                self.ev(it.context_expr, st)
                if it.optional_vars is not None:
                    self.prirad(it.optional_vars, _NEZ, st)
            r = self.blok(p.body, st)
            vys = PyVysl(n=py_spoj(r.n, r.exc))      # správca kontextu môže výnimku potlačiť
            vys.ret, vys.brk, vys.cnt, vys.exc = r.ret, r.brk, r.cnt, r.exc
            return vys
        if isinstance(p, (ast.FunctionDef, ast.AsyncFunctionDef)):
            for d in p.decorator_list:
                self.ev(d, st)
            hodn = ('fun', p) if not p.decorator_list and self.ramec is None and isinstance(p, ast.FunctionDef) else _NEZ
            for d in p.args.defaults + p.args.kw_defaults:
                if d is not None:
                    self.ev(d, st)
            self.zapis(p.name, hodn, st)
            return PyVysl(n=st)
        if isinstance(p, ast.ClassDef):
            for d in p.decorator_list + p.bases:
                self.ev(d, st)
            self.zapis(p.name, _NEZ, st)
            return PyVysl(n=st)
        if isinstance(p, ast.Return):
            if p.value is not None:
                self.unik_argv(self.ev(p.value, st))   # návratovú hodnotu beh nesleduje (funkcia vráti _NEZ)
            return PyVysl(ret=st)
        if isinstance(p, ast.Raise):
            if p.exc is not None:
                self.ev(p.exc, st)
            return PyVysl(exc=st)
        if isinstance(p, ast.Break):
            return PyVysl(brk=st)
        if isinstance(p, ast.Continue):
            return PyVysl(cnt=st)
        if isinstance(p, ast.Pass):
            return PyVysl(n=st)
        if isinstance(p, (ast.Import, ast.ImportFrom)):
            for al in p.names:
                meno = al.asname or al.name.split('.')[0]
                h = _NEZ
                if isinstance(p, ast.Import):
                    h = {'sys': _PY_SYS, 'os': _PY_OS, 'argparse': _PY_ARGPARSE}.get(al.name, _NEZ) \
                        if not al.asname or al.name in ('sys', 'os', 'argparse') else _NEZ
                    if al.asname and al.name in ('sys', 'os', 'argparse'):
                        h = {'sys': _PY_SYS, 'os': _PY_OS, 'argparse': _PY_ARGPARSE}[al.name]
                elif p.module == 'sys' and al.name == 'argv':
                    h = self.sys_argv()
                elif p.module == 'sys' and al.name == 'exit':
                    h = _PY_EXIT
                elif p.module == 'os' and al.name == 'environ':
                    h = _PY_ENV
                elif p.module == 'argparse' and al.name == 'ArgumentParser':
                    h = _PY_PARSER_TRIEDA
                self.zapis(meno, h, st)
            return PyVysl(n=st)
        if isinstance(p, (ast.Global, ast.Nonlocal)):
            return PyVysl(n=st)
        if isinstance(p, ast.Delete):
            for t in p.targets:
                self.prirad(t, _NEZ, st)
            return PyVysl(n=st)
        if isinstance(p, ast.Assert):
            c = self.podmienka(p.test, st)
            if c is False:
                return PyVysl(exc=st)
            return PyVysl(n=st, exc=st if c is None else None)
        if hasattr(ast, 'Match') and isinstance(p, ast.Match):
            self.ev(p.subject, st)
            vys = PyVysl(n=st)
            for case in p.cases:
                st2 = st.kopia()
                for m in ast.walk(case.pattern):
                    if isinstance(m, (ast.MatchAs, ast.MatchStar)) and getattr(m, 'name', None):
                        self.zapis(m.name, _NEZ, st2)
                r = self.blok(case.body, st2)
                vys.prilep(r)
                vys.n = py_spoj(vys.n, r.n)
            return vys
        raise _Nevie(f'neznámy príkaz {type(p).__name__}')

    def rozsir(self, st, uzly):
        """Stav pred cyklom s neznámym počtom priechodov: mená, ktoré telo mení, sú neznáme; volanie môže zmeniť
        globálne mená, ktoré funkcie skriptu menia cez global."""
        st = st.kopia()
        for u in uzly:
            if id(u) not in self._uklada:
                mena = set()
                vola = False
                for x in ast.walk(u):
                    if isinstance(x, ast.Name) and isinstance(x.ctx, (ast.Store, ast.Del)):
                        mena.add(x.id)
                    elif isinstance(x, ast.Call):
                        vola = True
                self._uklada[id(u)] = (mena, vola)
            mena, vola = self._uklada[id(u)]
            for m in mena:
                self.zapis(m, _NEZ, st)
            if vola:
                for m in self.globalne_menene:
                    st.g.pop(m, None)
        return st

    def for_(self, p, st):
        it = self.ev(p.iter, st)
        vys = PyVysl()
        if isinstance(it, list) and len(it) <= PY_OPAKOVANIA:
            cur = st
            po_break = None
            for x in it:
                if cur is None:
                    break
                cur = cur.kopia()
                self.prirad(p.target, x, cur)
                r = self.blok(p.body, cur)
                vys.ret = py_spoj(vys.ret, r.ret)
                vys.exc = py_spoj(vys.exc, r.exc)
                po_break = py_spoj(po_break, r.brk)
                cur = py_spoj(r.n, r.cnt)
            if cur is not None and p.orelse:
                r = self.blok(p.orelse, cur)
                vys.prilep(r)
                cur = r.n
            vys.n = py_spoj(cur, po_break)
            return vys
        st2 = self.rozsir(st, [p])
        self.prirad(p.target, _NEZ, st2)
        r = self.blok(p.body, st2.kopia())
        vys.ret, vys.exc = r.ret, r.exc
        koniec = py_spoj(st2, r.n, r.cnt)
        if p.orelse:
            ro = self.blok(p.orelse, koniec)
            vys.prilep(ro)
            koniec = ro.n
        vys.n = py_spoj(koniec, r.brk)
        return vys

    def while_(self, p, st):
        vys = PyVysl()
        cur = st
        po_break = None
        for _ in range(PY_OPAKOVANIA):
            if cur is None:
                vys.n = po_break
                return vys
            c = self.podmienka(p.test, cur)
            if c is None:
                break
            if c is False:
                if p.orelse:
                    r = self.blok(p.orelse, cur)
                    vys.prilep(r)
                    cur = r.n
                vys.n = py_spoj(cur, po_break)
                return vys
            r = self.blok(p.body, cur.kopia())
            vys.ret = py_spoj(vys.ret, r.ret)
            vys.exc = py_spoj(vys.exc, r.exc)
            po_break = py_spoj(po_break, r.brk)
            cur = py_spoj(r.n, r.cnt)
        if cur is None:
            vys.n = po_break
            return vys
        st2 = self.rozsir(cur, [p])
        c = self.mozno_podm(p.test, st2)
        if c is not False:
            r = self.blok(p.body, st2.kopia())
            vys.ret = py_spoj(vys.ret, r.ret)
            vys.exc = py_spoj(vys.exc, r.exc)
            po_break = py_spoj(po_break, r.brk)
        koniec = st2
        if p.orelse:
            ro = self.blok(p.orelse, st2)
            vys.prilep(ro)
            koniec = ro.n
        vys.n = py_spoj(koniec, po_break)
        return vys

    def try_(self, p, st):
        vys = PyVysl()
        r = self.blok(p.body, st.kopia())
        vys.ret, vys.brk, vys.cnt = r.ret, r.brk, r.cnt
        po = r.n
        if p.orelse and po is not None:
            ro = self.blok(p.orelse, po)
            vys.prilep(ro)
            po = ro.n
        # obsluha výnimky: výnimka môže vzniknúť kdekoľvek v tele — stav s neznámym všetkým, čo telo mení
        vynimka = r.exc
        if p.handlers:
            st_h = py_spoj(self.rozsir(st, p.body), vynimka)
            for h in p.handlers:
                if h.type is not None:
                    self.ev(h.type, st_h)
                st2 = st_h.kopia()
                if h.name:
                    self.zapis(h.name, _NEZ, st2)
                rh = self.blok(h.body, st2)
                vys.prilep(rh)
                po = py_spoj(po, rh.n)
            vynimka = py_spoj(vynimka, self.rozsir(st, p.body))      # výnimka iného typu prejde ďalej
        if p.finalbody:
            vsetky = py_spoj(po, vys.ret, vys.brk, vys.cnt, vynimka, self.rozsir(st, p.body))
            rf = self.blok(p.finalbody, vsetky.kopia() if vsetky is not None else st.kopia())
            vys.ret = py_spoj(vys.ret, rf.ret)
            vys.brk = py_spoj(vys.brk, rf.brk)
            vys.cnt = py_spoj(vys.cnt, rf.cnt)
            vys.exc = py_spoj(vynimka, rf.exc)
            if rf.n is None:
                po = None
                vys.ret = vys.brk = vys.cnt = None
                vys.exc = rf.exc
        else:
            vys.exc = vynimka
        vys.n = po
        return vys


def py_vlastne_uzly(u):
    """Uzly funkcie bez vnorených funkcií, tried a lambda (ich mená patria im)."""
    zas = list(ast.iter_child_nodes(u))
    while zas:
        x = zas.pop()
        yield x
        if not isinstance(x, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef, ast.Lambda)):
            zas.extend(ast.iter_child_nodes(x))


def py_funkcie_mimo(info):
    """Funkcie, ktoré môže spustiť niečo mimo abstraktného behu: meno použité inak než ako priame volanie (odovzdané ako
    hodnota, v lambda, v slovníku príkazov, getattr(x, 'meno')), dekorované, async, viac definícií — a funkcie, ktoré
    tie volajú. Pri globals()/vars()/__dict__/sys.modules či getattr s menom z premennej sú mimo všetky."""
    defs = info.defs
    if info.dynamicke:
        return set(defs)
    mimo = {m for m, d in defs.items() if len(d) != 1 or d[0].decorator_list or isinstance(d[0], ast.AsyncFunctionDef)}
    mimo |= {m for m in defs if info.odkazy.get(m, 0) != info.volania.get(m, 0) or m in info.getattr_mena}
    zmena = True
    while zmena:
        zmena = False
        for m in defs:
            if m not in mimo and any(m in info.mena_v_def.get(m2, ()) for m2 in mimo):
                mimo.add(m)
                zmena = True
    return mimo


def py_prepisane(strom):
    """Mená, ktoré skript sám viaže (priradenie, def, class, import, parameter) — nie sú to vstavané funkcie."""
    m = set()
    for u in ast.walk(strom):
        if isinstance(u, ast.Name) and not isinstance(u.ctx, ast.Load):
            m.add(u.id)
        elif isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            m.add(u.name)
        elif isinstance(u, (ast.Import, ast.ImportFrom)):
            m |= {(x.asname or x.name).split('.')[0] for x in u.names}
        elif isinstance(u, ast.arg):
            m.add(u.arg)
    return m


def py_argv_kontrola(strom, mimo, prepisane):
    """sys.argv sa mení alebo odchádza tam, kam abstraktný beh nevidí → _Nevie (nevynechá sa nič). 3. 10. (overenie kola 1):
    `sys.argv.append('send')` ako predvolený podpríkaz. Zmena metódou (append, insert …), zápisom (argv[1] = …, argv += …)
    kdekoľvek v kóde; meno 'argv' či 'sys' v reťazci (getattr, __dict__, import_module); sys.argv ako hodnota (priradenie,
    argument, návrat) v kóde, ktorý beh nespúšťa sám — lambda, vnorená funkcia, trieda, funkcia s dekorátorom či odovzdaná
    ako hodnota (mimo) — alebo z iného objektu než modul sys. V kóde, ktorý beh spúšťa, sleduje sys.argv beh
    (PyBeh.sys_argv, unik_argv)."""
    sys_mena, argv_mena = set(), set()
    for u in ast.walk(strom):
        if isinstance(u, ast.Import):
            sys_mena |= {x.asname or x.name for x in u.names if x.name == 'sys'}
        elif isinstance(u, ast.ImportFrom) and u.module == 'sys':
            argv_mena |= {x.asname or x.name for x in u.names if x.name == 'argv'}
        elif isinstance(u, ast.Constant) and u.value in ('argv', 'sys'):
            raise _Nevie('sys.argv podľa mena')
    zas = [(u, None, ()) for u in ast.iter_child_nodes(strom)]
    while zas:
        u, rodic, obal = zas.pop()
        je_argv = (isinstance(u, ast.Attribute) and u.attr == 'argv') or (isinstance(u, ast.Name) and u.id in argv_mena)
        if je_argv:
            sledovany = isinstance(u, ast.Name) or (isinstance(u.value, ast.Name) and u.value.id in sys_mena)
            beh_vidi = not any(isinstance(x, (ast.Lambda, ast.ClassDef)) for x in obal) \
                and sum(isinstance(x, (ast.FunctionDef, ast.AsyncFunctionDef)) for x in obal) <= 1 \
                and not any(isinstance(x, (ast.FunctionDef, ast.AsyncFunctionDef)) and (x.name in mimo
                                                                                       or x.decorator_list) for x in obal)
            if (isinstance(rodic, ast.AugAssign) and rodic.target is u) \
                    or (isinstance(rodic, ast.Subscript) and rodic.value is u and not isinstance(rodic.ctx, ast.Load)) \
                    or (isinstance(rodic, ast.Attribute) and rodic.attr in PY_ARGV_MENI and not (sledovany and beh_vidi)):
                raise _Nevie('zmena sys.argv')       # zmenu metódou v kóde, ktorý beh spúšťa, zachytí beh (zmen)
            citanie = (isinstance(rodic, ast.Subscript) and rodic.value is u) \
                or (isinstance(rodic, ast.Attribute) and rodic.attr in ('index', 'count', 'copy')) \
                or (isinstance(rodic, ast.Compare) and u is not rodic.left
                    and all(isinstance(o, (ast.In, ast.NotIn)) for o in rodic.ops)) \
                or (isinstance(rodic, (ast.For, ast.comprehension)) and rodic.iter is u) \
                or (isinstance(rodic, ast.Call) and isinstance(rodic.func, ast.Name) and rodic.func.id in PY_CITA_ZOZNAM
                    and rodic.func.id not in prepisane and u in rodic.args) \
                or (isinstance(rodic, ast.Attribute) and rodic.attr in PY_ARGV_MENI)
            if not citanie and not (sledovany and beh_vidi):
                raise _Nevie('sys.argv mimo behu')
        nove_obal = obal + (u,) if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.Lambda, ast.ClassDef)) else obal
        for x in ast.iter_child_nodes(u):
            zas.append((x, u, nove_obal))


# 15. 10. (kus SKF; mlacena, aspecta-finops 16:15): `ion.py stav` (len číta miestny súbor s tokenom) zastavený pre volanie
# rozhrania v podpríkaze `stiahni` — funkcia podpríkazu je v set_defaults(f=cmd_…) a volá ju a.f(a); meno odovzdané ako
# hodnota držalo v kóde funkcie všetkých podpríkazov (py_funkcie_mimo, py_dosiahnutelne), vybraný bol len jeden.
PY_PARSE_METODY = ('parse_args', 'parse_known_args', 'parse_intermixed_args', 'parse_known_intermixed_args')
PY_PARSER_NAHLAD = frozenset({'get_default', '_defaults', 'choices', '_name_parser_map', '_choices_actions', '_actions',
                              '_subparsers', '_registries', '_option_string_actions', '_mutually_exclusive_groups',
                              '_action_groups', '__dict__'})
PY_PARSER_PRIJIMA = ('add_argument', 'set_defaults', 'add_mutually_exclusive_group', 'add_argument_group')
# premenná parsera, skupiny či podpríkazov smie byť len príjemcom týchto metód — inak môže parser odísť do kódu, ktorý
# beh nevidí (iný modul, návratová hodnota) a tam vybrať iný podpríkaz alebo prečítať jeho predvolené hodnoty
PY_PARSER_METODY = PY_PARSER_PRIJIMA + PY_PARSE_METODY + ('add_subparsers', 'add_parser', 'print_help', 'print_usage',
                                                          'format_help', 'format_usage', 'error', 'exit')


def py_mrtve_predvolby(strom, beh, ap, info):
    """set_defaults(f=meno) na podparseri, ktorý vyvolanie nevybralo: hodnota sa do výsledku parse_args nikdy nedostane
    (argparse uplatní predvolené hodnoty len parsera, ktorý argumenty naozaj spracúva) — meno sa nahradí None, aby
    py_dosiahnutelne vynechal funkciu, ktorú kód volá len cez výsledok (a.f(a)). Len keď je to isté: každé parse_* v kóde
    beh vyriešil s tým istým parserom a tými istými vybranými podparsermi (sys.argv nezmenený), kód do parsera nenahliada
    (get_default, _defaults, choices … ani menom v reťazci), podparser kotva pozná a jeho premennú kód používa len na
    add_argument, set_defaults a skupiny (nie parents=[…], nie odovzdaná inam). → počet nahradených mien."""
    if info.dynamicke or info.eval_exec or not beh.parse_vyriesene:
        return 0
    vyber = set()
    for u in ast.walk(strom):
        if isinstance(u, ast.Attribute) and u.attr in PY_PARSER_NAHLAD:
            return 0
        if isinstance(u, ast.Constant) and isinstance(u.value, str) and (u.value in PY_PARSER_NAHLAD
                                                                          or u.value in PY_PARSE_METODY):
            return 0
        if isinstance(u, ast.Attribute) and u.attr in PY_PARSE_METODY:
            r = beh.parse_vyriesene.get(id(u))
            if not r:
                return 0                       # parse_*, ktoré beh nevyriešil (iný parser, iné argumenty, mimo behu)
            vyber |= r
    if len(vyber) != 1:
        return 0
    retaz = next(iter(vyber))[1]
    citania = collections.Counter()            # meno → koľkokrát ho kód číta
    prijimac = collections.Counter()           # meno → koľkokrát je len príjemcom add_argument/set_defaults/skupiny
    metoda = collections.Counter()             # meno → koľkokrát je príjemcom metódy parsera (PY_PARSER_METODY)
    for u in ast.walk(strom):
        if isinstance(u, ast.Name) and isinstance(u.ctx, ast.Load):
            citania[u.id] += 1
        elif isinstance(u, ast.Call) and isinstance(u.func, ast.Attribute) and isinstance(u.func.value, ast.Name):
            if u.func.attr in PY_PARSER_PRIJIMA:
                prijimac[u.func.value.id] += 1
            if u.func.attr in PY_PARSER_METODY:
                metoda[u.func.value.id] += 1
    if any(citania[m] != metoda[m] for m in set(ap.mena) | set(ap.sub)):
        return 0                               # parser, skupina alebo podpríkazy ako hodnota (inam, return, parents=[…])
    n = 0
    for u in ast.walk(strom):
        if not (isinstance(u, ast.Call) and isinstance(u.func, ast.Attribute) and u.func.attr == 'set_defaults'):
            continue
        f = u.func.value
        if isinstance(f, ast.Name):
            k = ap.mena.get(f.id)
            if k is None or citania[f.id] != prijimac[f.id]:
                continue
        elif isinstance(f, ast.Call) and isinstance(f.func, ast.Attribute) and f.func.attr == 'add_parser':
            k = id(f)
        else:
            continue
        if k not in ap.parsery or k not in ap.sub_volania or k in retaz:
            continue
        for x in u.keywords:
            if x.arg and isinstance(x.value, ast.Name):
                x.value = ast.copy_location(ast.Constant(None), x.value)
                n += 1
    return n


def py_volby(obs, tokeny, prefix):
    """Python bez vetiev, ktoré sa pri tomto vyvolaní určite nespustia (ast.unparse, bez docstringov). Keď kotva štruktúru
    alebo beh neprečíta, vráti text nezmenený. 15. 10.: aj bez mien funkcií v set_defaults podpríkazov, ktoré vyvolanie
    nevybralo (py_mrtve_predvolby)."""
    try:
        strom = ast.parse(obs)
        info = PyInfo(strom)
        if info.eval_exec:
            return obs                               # eval/exec/globals() … — hodnoty sa nedajú sledovať
        prepisane = py_prepisane(strom)
        ap = PyArgparse(info, prepisane)
        ap.choices_menene = any(isinstance(u, ast.Attribute) and u.attr in ('choices', '_name_parser_map',
                                                                             '_choices_actions', '_actions')
                                for u in ast.walk(strom))
        ns_mena = info.ns_mena
        for u in info.ulozenia:
            cel = u.value
            while isinstance(cel, (ast.Attribute, ast.Subscript)):
                cel = cel.value
            if _py_meno(u.value) in ('sys', 'sys.argv') or (isinstance(cel, ast.Name) and cel.id in ns_mena) \
                    or (isinstance(u, ast.Attribute) and u.attr == 'argv'):
                raise _Nevie('zápis do argumentov')
        mimo = py_funkcie_mimo(info)
        py_argv_kontrola(strom, mimo, prepisane)
        beh = PyBeh(strom, tokeny, prefix, ap, info)
        beh.env_zmena = bool(PY_ENV_ZMENA.search(obs))
        beh.prepisane = prepisane
        # neznáme volanie (knižnica, callback) môže spustiť len funkciu mimo behu — jej global mená sú po ňom neznáme;
        # funkcie, ktoré beh spúšťa priamo, menia globálne mená v behu samom
        simulovane = {id(d[0]) for m, d in info.defs.items() if m not in mimo}
        beh.globalne_menene = set().union(*[gl for fid, (_, gl) in info.lokalne.items() if fid not in simulovane])
        beh.blok(strom.body, PyStav())
        zmena = [False]
        py_vystrihni(beh, strom.body, mimo, zmena, True)
        if py_mrtve_predvolby(strom, beh, ap, info):
            zmena[0] = True
        if not zmena[0]:
            return obs
        for u in ast.walk(strom):
            if isinstance(u, (ast.Module, ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)) and u.body \
                    and isinstance(u.body[0], ast.Expr) and isinstance(u.body[0].value, ast.Constant) \
                    and isinstance(u.body[0].value.value, str):
                u.body[:] = u.body[1:] or [ast.Pass()]
        return ast.unparse(strom)
    except (_Nevie, SyntaxError, RecursionError, ValueError, IndexError, KeyError, TypeError, AttributeError, re.error,
            MemoryError):
        return obs


def py_vystrihni(beh, prikazy, mimo, zmena, najvyssi=False):
    """Z prejdeného bloku vynechá príkazy, ktoré sa nespustili, a vetvy if, do ktorých beh nevošiel."""
    if id(prikazy) not in beh.prejdene:
        return
    nove = []
    for p in prikazy:
        if id(p) not in beh.navst:
            zmena[0] = True
            continue
        nove.append(p)
        if isinstance(p, ast.If):
            if id(p.body) not in beh.prejdene:
                p.body[:] = [ast.Pass()]
                zmena[0] = True
            else:
                py_vystrihni(beh, p.body, mimo, zmena)
            if p.orelse:
                if id(p.orelse) not in beh.prejdene:
                    p.orelse[:] = []
                    zmena[0] = True
                else:
                    py_vystrihni(beh, p.orelse, mimo, zmena)
        elif isinstance(p, (ast.For, ast.AsyncFor, ast.While)):
            py_vystrihni(beh, p.body, mimo, zmena)
            if p.orelse:
                py_vystrihni(beh, p.orelse, mimo, zmena)
        elif isinstance(p, (ast.Try, getattr(ast, 'TryStar', ast.Try))):
            for b in [p.body, p.orelse, p.finalbody] + [h.body for h in p.handlers]:
                if b:
                    py_vystrihni(beh, b, mimo, zmena)
        elif isinstance(p, (ast.With, ast.AsyncWith)):
            py_vystrihni(beh, p.body, mimo, zmena)
        elif isinstance(p, ast.FunctionDef) and najvyssi and p.name not in mimo:
            py_vystrihni(beh, p.body, mimo, zmena)
    if not nove:
        nove = [ast.Pass()]
    prikazy[:] = nove


def git_koren(d, smie):
    """Koreň repozitára gitu pre priečinok d (git rev-parse --show-toplevel) alebo None; smie(cesta) = kotva tam smie
    pozerať (nie cudzí domov)."""
    d = os.path.normpath(d) if d else None
    for _ in range(40):
        if not d or d == '/' or not smie(d):
            return None
        try:
            if os.path.exists(os.path.join(d, '.git')):
                return d
        except (OSError, ValueError):
            return None
        d = os.path.dirname(d)
    return None


# ---------- rozklad na slová a príkazy ----------

class Slovo:
    __slots__ = ('seg', 'kvot', 'kvot_medzera', 'tilda', 'predvol', 'v_uvod', 'viac')

    def __init__(self):
        self.seg = []              # ('l', text, v_úvodzovkách) | ('v', meno) | ('s', príkazy, hodnota $(mktemp))
        self.kvot = False
        self.kvot_medzera = False  # časť v úvodzovkách obsahuje medzeru → veta, nie cesta ani názov skriptu
        self.tilda = False
        self.predvol = None        # 2. 10. večer: {index segmentu ('v', '?'): (meno, predvolená hodnota, aj prázdna, priradí)}
        self.v_uvod = None         # 2. 10. večer: indexy segmentov ('v' …, 's' …) v dvojitých úvodzovkách (nedelia sa)
        self.viac = None           # 3. 10.: indexy segmentov "$@", "${pole[@]}" … — aj v úvodzovkách žiadne, jedno či viac slov

    def lit(self, t, q):
        if not t:
            return
        if q:
            self.kvot = True
            if not self.kvot_medzera and any(ch.isspace() for ch in t):
                self.kvot_medzera = True
        if self.seg and self.seg[-1][0] == 'l' and self.seg[-1][2] == q:
            self.seg[-1] = ('l', self.seg[-1][1] + t, q)
        else:
            self.seg.append(('l', t, q))

    def text(self):
        """Text slova ako ho vidí človek: premenná ako $x, substitúcia ako ${__SUB__}."""
        o = []
        for s in self.seg:
            if s[0] == 'l':
                o.append(s[1])
            elif s[0] == 'v':
                o.append('$' + s[1] if s[1] != '?' else '${__X__}')
            else:
                o.append('${__SUB__}')
        return ''.join(o)

    holy = text


class Cmd:
    __slots__ = ('slova', 'presm', 'heredoc', 'herestr', 'podshell', 'vnorene', 'rura', 'pozadie')

    def __init__(self):
        self.slova = []
        self.presm = []      # (operátor, Slovo)
        self.heredoc = []    # (telo, oddeľovač v úvodzovkách)
        self.herestr = []
        self.podshell = None
        self.vnorene = []
        self.rura = False    # predchádzajúci príkaz posiela výstup rúrou do tohto
        self.pozadie = False  # príkaz beží na pozadí (… &) — v podshelli, priečinok ani premenné nemení

    def prazdny(self):
        return not (self.slova or self.presm or self.heredoc or self.herestr or self.podshell is not None or self.vnorene)


class Lex:
    def __init__(self, s):
        self.s = s.replace('\x01', '\ufffd') if '\x01' in s else s   # 3. 10.: znak značky MKTEMP_LIST
        self.i = 0
        self.n = len(s)
        self.cakajuce = []   # heredocy čakajúce na koniec riadku

    def zoznam(self, koniec=None):
        s = self.s
        cmds = []
        stav = {'cur': Cmd(), 'rura': False}

        def dokonci(dalsia_rura=False):
            c = stav['cur']
            if not c.prazdny():
                c.rura = stav['rura']
                cmds.append(c)
            stav['cur'] = Cmd()
            stav['rura'] = dalsia_rura

        while self.i < self.n:
            c = s[self.i]
            if c in ' \t\r':
                self.i += 1
                continue
            if c == '\\' and self.i + 1 < self.n and s[self.i + 1] == '\n':
                self.i += 2
                continue
            if c == '\n':
                self.i += 1
                dokonci()
                self.heredocy()
                continue
            if c == '#':
                j = s.find('\n', self.i)
                self.i = self.n if j < 0 else j
                continue
            if c == ')':
                self.i += 1
                dokonci()
                if koniec == ')':
                    return cmds
                continue
            if c == '`' and koniec == '`':
                self.i += 1
                dokonci()
                return cmds
            if c in ';&|':
                if s.startswith('&>', self.i):
                    self.presmerovanie(stav['cur'])
                    continue
                if s.startswith(';;&', self.i):
                    self.i += 3
                    dokonci()
                elif s.startswith(('||', '&&', ';;', ';&'), self.i):
                    self.i += 2
                    dokonci()
                elif s.startswith('|&', self.i):
                    self.i += 2
                    dokonci(True)
                elif c == '|':
                    self.i += 1
                    dokonci(True)
                else:
                    if c == '&':
                        stav['cur'].pozadie = True
                    self.i += 1
                    dokonci()
                continue
            if c in '<>':
                if self.i + 1 < self.n and s[self.i + 1] == '(':     # <(…) >(…)
                    self.i += 2
                    w = Slovo()
                    w.seg.append(('s', self.zoznam(')'), None))
                    stav['cur'].slova.append(w)
                    continue
                self.presmerovanie(stav['cur'])
                continue
            if c == '(':
                if s.startswith('((', self.i):
                    self.i = self.par(self.i)
                    continue
                self.i += 1
                vnut = self.zoznam(')')
                # f() { … } — definícia funkcie: telo sú príkazy (predtým sa telo prilepilo k menu funkcie a
                # `f() { rm -rf /x; }; f` prešlo — 24. 9.). 2. 10. (úloha K): podshell hneď za kľúčovým slovom
                # (do ( … ), then ( … ), else ( … )) nie je definícia funkcie — rozbor ho zahodil a `for x in a; do
                # (rm -rf /var/x); done` aj `for p in …; do (cd $p && bash x.sh); done` prešli bez posúdenia
                if stav['cur'].slova and not all(not w.kvot and w.text() in KLUCOVE for w in stav['cur'].slova):
                    stav['cur'] = Cmd()
                    continue
                dokonci()
                p = Cmd()
                p.podshell = vnut
                cmds.append(p)
                continue
            w = self.slovo(koniec)
            if w is None:
                self.i += 1
                continue
            if self.i < self.n and s[self.i] in '<>' and len(w.seg) == 1 and w.seg[0][0] == 'l' \
                    and not w.seg[0][2] and w.seg[0][1].isdigit():
                self.presmerovanie(stav['cur'])      # 2>…, 1>>…: číslo je deskriptor
                continue
            stav['cur'].slova.append(w)
        dokonci()
        return cmds

    def slovo(self, koniec=None):
        s = self.s
        w = Slovo()
        zac = self.i
        while self.i < self.n:
            c = s[self.i]
            if c in ' \t\r\n;&|<>)':
                break
            if c == '(':
                if w.seg and PRIRADENIE.fullmatch(w.text()):   # pole: a=(x y)
                    j = self.par(self.i)
                    w.lit(s[self.i:j], True)
                    self.i = j
                    continue
                break
            if c == '`':
                if koniec == '`':
                    break
                self.i += 1
                w.seg.append(('s', self.zoznam('`'), None))
                continue
            if c == '\\':
                if self.i + 1 < self.n and s[self.i + 1] != '\n':
                    w.lit(s[self.i + 1], True)
                self.i += 2
                continue
            if c == "'":
                j = s.find("'", self.i + 1)
                j = self.n if j < 0 else j
                w.lit(s[self.i + 1:j], True)
                self.i = j + 1
                continue
            if c == '"':
                self.i += 1
                self.dvojite(w)
                continue
            if c == '$':
                self.dolar(w, False)
                continue
            if c == '~' and self.i == zac:
                w.tilda = True
            m = BEZNE.match(s, self.i)
            if m:
                w.lit(m.group(), False)
                self.i = m.end()
            else:
                w.lit(c, False)
                self.i += 1
        if self.i == zac:
            return None
        return w

    def dvojite(self, w, do_uvodzovky=True):
        """Obsah v dvojitých úvodzovkách (alebo telo heredocu, keď do_uvodzovky=False)."""
        s = self.s
        while self.i < self.n:
            c = s[self.i]
            if c == '"' and do_uvodzovky:
                self.i += 1
                return
            if c == '\\' and self.i + 1 < self.n and s[self.i + 1] in '$`"\\\n':
                if s[self.i + 1] != '\n':
                    w.lit(s[self.i + 1], True)
                self.i += 2
                continue
            if c == '$':
                n0 = len(w.seg)
                self.dolar(w, True)
                if len(w.seg) > n0 and w.seg[-1][0] != 'l':
                    w.v_uvod = (w.v_uvod or set()) | {len(w.seg) - 1}
                continue
            if c == '`':
                self.i += 1
                w.seg.append(('s', self.zoznam('`'), None))
                w.v_uvod = (w.v_uvod or set()) | {len(w.seg) - 1}
                continue
            m = DVOJ_BEZNE.match(s, self.i)
            if m:
                w.lit(m.group(), True)
                self.i = m.end()
            else:
                w.lit(c, True)
                self.i += 1

    def dolar(self, w, dq):
        s = self.s
        i = self.i
        nx = s[i + 1] if i + 1 < self.n else ''
        if nx == '(':
            if s.startswith('$((', i):
                self.i = self.par(i + 1)
                w.seg.append(('v', '?'))
                return
            self.i = i + 2
            vn = self.zoznam(')')
            w.seg.append(('s', vn, mktemp_hodnota(vn)))
            return
        if nx == '{':
            j = self.zlozena(i + 2)
            obsah = s[i + 2:j]
            self.i = j + 1
            m = re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*)(:?\?.*)?', obsah, re.S)
            # 2. 10. (úloha K): ${BASH_SOURCE[0]} a ${0}…${9} — cesta skriptu a jeho argumenty (v rozbore skriptu známe,
            # v príkaze partnera neznáme ako doteraz)
            m2 = re.fullmatch(r'(BASH_SOURCE)(?:\[0\])?|([0-9])', obsah)
            w.seg.append(('v', m.group(1) if m else (m2.group(1) or m2.group(2)) if m2 else '?'))
            if re.match(r'!?(?:@|[A-Za-z_][A-Za-z0-9_]*(?:\[@\]|@))', obsah):     # ${@:2}, ${pole[@]}, ${!pole[@]}
                w.viac = (w.viac or set()) | {len(w.seg) - 1}
            # 2. 10. večer (overenie: `"${AZ_BIN:-az}" ad app credential reset` prešiel): ${X:-hodnota} a ${X-hodnota}
            # s doslovnou hodnotou — segment ostáva neznámy ako doteraz, rozvinutie pridá aj predvolenú hodnotu
            # 14. 10. (K88V): aj predvolená hodnota s premennou (${X:-$AIG/x.py}, ${X:-${D}/x}) a priradenie predvolenej
            # (${X:=…}, ${X=…}) — predtým bol celý segment neznámy: hodnotu z prostredia (X=<pošta> bash s.sh) ani
            # predvolenú stráž neposúdila; s doslovnou predvolenou hodnotou ten istý skript zastavila
            m3 = None if (m or m2) else re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*|[0-9])(:?)([-=])((?:[A-Za-z0-9_./+@%,=-]'
                                                     r'|\$[A-Za-z_][A-Za-z0-9_]*|\$\{[A-Za-z_][A-Za-z0-9_]*\})*)', obsah)
            if not (m or m2 or m3):          # predvolená hodnota, ktorú rozbor nevie prečítať — X sa použije aj tak
                m3 = re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*|[0-9])(:?)([-=])(.*)', obsah, re.S)
                nevie = bool(m3)
            else:
                nevie = False
            if m3 and not (m3.group(3) == '=' and m3.group(1).isdigit()):
                pred = None if nevie else m3.group(4)
                if pred is not None and '$' in pred:          # (('l', text) | ('v', meno), …)
                    pred = tuple(('v', x.group(1) or x.group(2)) if (x.group(1) or x.group(2)) else ('l', x.group())
                                 for x in re.finditer(r'\$\{([A-Za-z_][A-Za-z0-9_]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)|[^$]+', pred))
                if w.predvol is None:
                    w.predvol = {}
                w.predvol[len(w.seg) - 1] = (m3.group(1), pred, bool(m3.group(2)), m3.group(3) == '=')
            return
        if nx == "'" and not dq:
            j = i + 2
            o = []
            while j < self.n and s[j] != "'":
                if s[j] == '\\' and j + 1 < self.n:
                    o.append({'n': '\n', 't': '\t', "'": "'", '\\': '\\'}.get(s[j + 1], s[j:j + 2]))
                    j += 2
                    continue
                o.append(s[j])
                j += 1
            w.lit(''.join(o), True)
            self.i = j + 1
            return
        if nx == '"' and not dq:
            self.i = i + 2
            self.dvojite(w)
            return
        m = MENO.match(s, i + 1)
        if m:
            w.seg.append(('v', m.group()))
            if m.group() == '@':
                w.viac = (w.viac or set()) | {len(w.seg) - 1}
            self.i = m.end()
            return
        w.lit('$', dq)
        self.i = i + 1

    def zlozena(self, j):
        """Index zatváracej } pre ${…}."""
        s = self.s
        h = 1
        while j < self.n:
            c = s[j]
            if c == '\\':
                j += 2
                continue
            if c == "'":
                k = s.find("'", j + 1)
                j = self.n if k < 0 else k + 1
                continue
            if c == '{':
                h += 1
            elif c == '}':
                h -= 1
                if h == 0:
                    return j
            j += 1
        return self.n

    def par(self, j):
        """Index za zatváracou zátvorkou k s[j]=='(' (s ohľadom na úvodzovky)."""
        s = self.s
        h = 0
        while j < self.n:
            c = s[j]
            if c == '\\':
                j += 2
                continue
            if c in '\'"':
                k = s.find(c, j + 1)
                j = self.n if k < 0 else k + 1
                continue
            if c == '(':
                h += 1
            elif c == ')':
                h -= 1
                if h == 0:
                    return j + 1
            j += 1
        return self.n

    def presmerovanie(self, cur):
        s = self.s
        i = self.i
        op = '>'
        for o in ('&>>', '&>', '<<<', '<<-', '<<', '<>', '<&', '>&', '>>', '>|', '<', '>'):
            if s.startswith(o, i):
                op = o
                break
        self.i = i + len(op)
        while self.i < self.n and s[self.i] in ' \t':
            self.i += 1
        if op in ('<&', '>&'):
            m = FD_DUP.match(s, self.i)
            if m and (m.end() >= self.n or s[m.end()] in ' \t\n;&|)<>'):
                self.i = m.end()
                return
        if self.i >= self.n or s[self.i] in '\n;&|()<>':
            return
        w = self.slovo()
        if w is None:
            return
        if op in ('<<', '<<-'):
            self.cakajuce.append((cur, w.text(), op == '<<-', w.kvot))
        elif op == '<<<':
            cur.herestr.append(w)
        else:
            cur.presm.append((op, w))

    def heredocy(self):
        s = self.s
        while self.cakajuce:
            cur, delim, tabs, kvot = self.cakajuce.pop(0)
            telo = []
            while self.i < self.n:
                j = s.find('\n', self.i)
                if j < 0:
                    riadok = s[self.i:]
                    self.i = self.n
                else:
                    riadok = s[self.i:j]
                    self.i = j + 1
                if (riadok.lstrip('\t') if tabs else riadok) == delim:
                    break
                telo.append(riadok)
            t = '\n'.join(telo)
            cur.heredoc.append((t, kvot))
            if not kvot and ('$(' in t or '`' in t):
                # oddeľovač bez úvodzoviek: $(…) v tele sa vykoná
                sub = Lex(t)
                w = Slovo()
                sub.dvojite(w, do_uvodzovky=False)
                for sg in w.seg:
                    if sg[0] == 's' and sg[1]:
                        cur.vnorene.append(sg[1])


def mktemp_hodnota(cmds):
    """$(mktemp …) je cesta v /tmp (alebo v adresári -p / --tmpdir)."""
    if len(cmds) != 1 or not cmds[0].slova:
        return None
    sl = [w.text() for w in cmds[0].slova]
    if os.path.basename(sl[0]) != 'mktemp':
        return None
    adr = '/tmp'
    vzor = None
    nanecisto = False      # -u / --dry-run: mktemp meno len vypíše, nič nevytvorí
    j = 1
    while j < len(sl):
        t = sl[j]
        if t in ('-p', '--tmpdir') and j + 1 < len(sl):
            adr = sl[j + 1]
            j += 2
            continue
        if t.startswith('--tmpdir='):
            adr = t.split('=', 1)[1]
        elif t.startswith('-p') and len(t) > 2:
            adr = t[2:]
        elif t == '--dry-run' or (t.startswith('-') and not t.startswith('--') and 'u' in t[1:]):
            nanecisto = True
        elif not t.startswith('-'):
            vzor = t
        j += 1
    if '$' in adr or not adr.startswith('/'):
        return None
    # 3. 10. (kolo 3): posledný diel je značka MKTEMP_LIST (cesta, ktorú mktemp vytvoril); pri -u ako doteraz
    if vzor and '/' in vzor:
        if not vzor.startswith('/') or '$' in vzor:
            return None
        return vzor if nanecisto else os.path.dirname(vzor) + '/' + MKTEMP_LIST
    return adr.rstrip('/') + '/' + ('mktemp.X' if nanecisto else MKTEMP_LIST)


def parsuj(text):
    return Lex(text).zoznam()


# ---------- voľby príkazov ----------

def skok(x, s_arg, l_arg):
    """O koľko slov sa posunúť za voľbou x (2 = voľba berie ďalšie slovo ako argument)."""
    if x.startswith('--'):
        return 2 if ('=' not in x and x in l_arg) else 1
    telo = x[1:]
    if telo.isdigit():
        return 1
    for k, ch in enumerate(telo):
        if ch in s_arg:
            return 2 if k == len(telo) - 1 else 1
    return 1


def xargs_token(volby_t):
    """Náhradný znak xargs (-I{}, -i, --replace[=…]): vstup sa vkladá do slov príkazu namiesto pripojenia na koniec.
    None = vstup pribudne ako ďalšie argumenty — vtedy môže byť ďalšou adresou odoslania (10. 10.)."""
    j = 0
    while j < len(volby_t):
        x = volby_t[j]
        if x.startswith('--replace'):
            if '=' in x:
                return x.split('=', 1)[1] or '{}'
            return volby_t[j + 1] if j + 1 < len(volby_t) and not volby_t[j + 1].startswith('-') else '{}'
        if x.startswith('-') and not x.startswith('--') and len(x) > 1:
            for q, ch in enumerate(x[1:]):
                if ch == 'I':
                    return x[q + 2:] or (volby_t[j + 1] if j + 1 < len(volby_t) else '{}')
                if ch == 'i':
                    return x[q + 2:] or '{}'
                if ch in 'adEILnPs':
                    break
        j += skok(x, 'adEILnPs', XARGS_L_ARG)
    return None


def par_volby(t):
    """16. 10. (KXP): voľby GNU parallel → (index príkazu, údaje): náhradný reťazec (-I, --replace, -i), -q, --pipe,
    súbory vstupov (-a, --arg-file), oddeľovače ::: a :::: (--arg-sep, --arg-file-sep)."""
    info = {'nahrada': None, 'quote': False, 'pipe': False, 'pomoc': False, 'subory': [], 'sep': ':::', 'fsep': '::::'}
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            return j + 1, info
        if not x.startswith('-') or len(x) == 1:
            return j, info
        if x.startswith('--'):
            meno, _, hod = x.partition('=')
            if meno == '--replace':
                if '=' in x:
                    info['nahrada'] = hod or '{}'
                elif j + 1 < len(t) and not t[j + 1].startswith('-'):
                    info['nahrada'] = t[j + 1]
                    j += 2
                    continue
                else:
                    info['nahrada'] = '{}'
                j += 1
                continue
            if meno == '--quote':
                info['quote'] = True
            elif meno in ('--version', '--help', '--citation', '--bibtex', '--number-of-cpus', '--number-of-cores',
                          '--number-of-sockets', '--number-of-threads', '--max-line-length-allowed', '--minversion',
                          '--record-env', '--embed', '--show-limits'):
                info['pomoc'] = True     # nič nespúšťa, len vypíše
            elif meno in ('--pipe', '--pipepart', '--spreadstdin'):
                info['pipe'] = True
            elif meno in ('--arg-file', '--arg-sep', '--arg-file-sep'):
                v = hod if '=' in x else (t[j + 1] if j + 1 < len(t) else '')
                if meno == '--arg-file':
                    info['subory'].append(v)
                else:
                    info['sep' if meno == '--arg-sep' else 'fsep'] = v
            j += skok(x, PAR_S_ARG, PAR_L_ARG)
            continue
        telo = x[1:]
        for q, ch in enumerate(telo):
            if ch == 'q':
                info['quote'] = True
            elif ch in 'hV':
                info['pomoc'] = True
            elif ch == 'i':
                info['nahrada'] = telo[q + 1:] or '{}'
                break
            elif ch in PAR_S_ARG:
                v = telo[q + 1:] or (t[j + 1] if j + 1 < len(t) else '')
                if ch == 'I':
                    info['nahrada'] = v
                elif ch == 'a':
                    info['subory'].append(v)
                break
        j += skok(x, PAR_S_ARG, PAR_L_ARG)
    return j, info


def sh_uvod(v):
    """Hodnota pre shell ako jedno slovo (parallel vstup v príkaze takto obalí, keď nie je bez príkazu)."""
    return v if v and re.fullmatch(r'[\w@%+=:,./-]+', v) else "'" + v.replace("'", "'\\''") + "'"


def volby(t, s_arg='', l_arg=()):
    """Index prvého pozičného argumentu. s_arg = krátke voľby s argumentom, l_arg = dlhé voľby s argumentom."""
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            return j + 1
        if x.startswith('-') and len(x) > 1:
            j += skok(x, s_arg, l_arg)
            continue
        return j
    return j


def pozicne(t, s_arg='', l_arg=()):
    """Pozičné argumenty (bez volieb a ich argumentov)."""
    o = []
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            return o + t[j + 1:]
        if x.startswith('-') and len(x) > 1:
            j += skok(x, s_arg, l_arg)
            continue
        o.append(x)
        j += 1
    return o


def sed_skript_ok(sk):
    for kus in re.split(r'[;\n]', sk):
        k = kus.strip()
        k = re.sub(r'^(\d+|\$|/(?:\\.|[^/])*/[IM]*)(~\d+)?(\s*,\s*(\d+|\$|/(?:\\.|[^/])*/[IM]*|[+~]\d+))?\s*!?\s*', '', k)
        k = k.lstrip('{} \t')
        if not k:
            continue
        c = k[0]
        if c in 'wWe':
            return False
        if c == 's' and len(k) > 1:
            casti = re.split(r'(?<!\\)' + re.escape(k[1]), k[2:])
            if len(casti) >= 3 and re.search(r'[we]', casti[2]):
                return False
    return True


def sed_citanie(t):
    if '--sandbox' in t:
        return True
    skripty = []
    poz = []
    e_dane = False
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            poz += t[j + 1:]
            break
        if x in ('-e', '--expression'):
            skripty.append(t[j + 1] if j + 1 < len(t) else '')
            e_dane = True
            j += 2
            continue
        if x.startswith('--expression='):
            skripty.append(x.split('=', 1)[1])
            e_dane = True
            j += 1
            continue
        if x in ('-f', '--file', '-i') or x.startswith(('--file=', '--in-place')):
            return False
        if x in ('-l', '--line-length'):
            j += 2
            continue
        if x.startswith('--'):
            j += 1
            continue
        if x.startswith('-') and len(x) > 1:
            telo = x[1:]
            for k, ch in enumerate(telo):
                if ch in 'if':
                    return False
                if ch == 'e':
                    zv = telo[k + 1:]
                    if zv:
                        skripty.append(zv)
                    else:
                        skripty.append(t[j + 1] if j + 1 < len(t) else '')
                        j += 1
                    e_dane = True
                    break
                if ch == 'l':
                    break
            j += 1
            continue
        poz.append(x)
        j += 1
    if not e_dane:
        if not poz:
            return True
        skripty.append(poz[0])
    return all(sed_skript_ok(x) for x in skripty)


AWK_ZLE = re.compile(r'system\s*\(|(?<!\|)\|(?!\|)\s*(getline|["A-Za-z_$(])|\|&|\b(print|printf)\b[^;}]*>')


def awk_citanie(t):
    j = 0
    while j < len(t):
        x = t[j]
        if x == '--':
            j += 1
            break
        if x in ('-f', '--file', '-i', '--include', '-l', '--load', '-E', '--exec') or \
                x.startswith(('--file=', '--include=', '--load=', '--exec=')):
            return False
        if x in ('-F', '-v', '--field-separator', '--assign'):
            j += 2
            continue
        if x.startswith('-') and len(x) > 1:
            if x[1] in 'fiEl':
                return False
            j += 1
            continue
        break
    if j >= len(t):
        return False
    return not AWK_ZLE.search(t[j])


GIT_C_NESPUSTA = re.compile(r'^(?:safe\.directory|core\.quotepath|gc\.auto|maintenance\.auto|color\.ui|advice\.[a-z]+)=',
                            re.I)


def priradene_mena(text, prisne=False):
    """3. 10. (kolo 4): mená premenných, ktoré text shellu môže priradiť (X=, X+=, export/local/declare X=, read, mapfile,
    getopts, for, select, printf -v, let), alebo None, keď sa nedá povedať (meno priradenia z premennej $X=…, declare -n,
    printf -v $X; prisne = text načítaný cez source: aj eval, source, . — tie môžu priradiť hocičo)."""
    if re.search(r'\$\{?[A-Za-z_][A-Za-z0-9_]*\}?\+?=|\b(?:declare|typeset|local)\s+-\w*n|\bprintf\s+-v\s*["\']?\$', text) \
            or (prisne and re.search(r'\b(?:eval|source)\b|(?:^|[;&|\s])\.\s', text)):
        return None
    mena = set()
    for m in re.finditer(r'(?:^|[\s;&|(`{\'"])(?:(?:export|local|declare|typeset|readonly)\s+(?:-\w+\s+)*)?'
                         r'([A-Za-z_][A-Za-z0-9_]*)(?:\[[^\]]*\])?\+?=', text):
        mena.add(m.group(1))
    for m in re.finditer(r'\b(?:read|mapfile|readarray|getopts|for|select)\b([^;&|\n]*)|\bprintf\s+-v\s*(\w+)'
                         r'|\blet\s+([^;&|\n]*)', text):
        mena.update(re.findall(r'[A-Za-z_][A-Za-z0-9_]*', ' '.join(g for g in m.groups() if g)))
    return mena


def sed_volby(t):
    """sed: (na mieste -i/--in-place, skript daný -e/-f, indexy pozičných slov). Bez -e/-f je prvé pozičné slovo skript."""
    miesto = kod = False
    poz = []
    k, n = 0, len(t)
    while k < n:
        x = t[k]
        if x == '--':
            poz += list(range(k + 1, n))
            break
        if x.startswith('--'):
            m, rov, _ = x.partition('=')
            if m == '--in-place':
                miesto = True
            elif m in ('--expression', '--file'):
                kod = True
                k += 0 if rov else 1
            elif m == '--line-length' and not rov:
                k += 1
            k += 1
            continue
        if x.startswith('-') and len(x) > 1:
            for j, ch in enumerate(x[1:], 1):
                if ch == 'i':
                    miesto = True                    # zvyšok je prípona zálohy (-i.bak, -ie)
                    break
                if ch in 'efl':
                    kod |= ch in 'ef'
                    k += 0 if x[j + 1:] else 1
                    break
            k += 1
            continue
        poz.append(k)
        k += 1
    return miesto, kod, poz


def git_rozbor(t):
    """(podpríkaz, jeho index, indexy -C, index/text --git-dir, má -c)"""
    j = 0
    C = []
    gd = None
    ma_c = False
    while j < len(t):
        x = t[j]
        if x == '-C' and j + 1 < len(t):
            C.append(j + 1)
            j += 2
            continue
        if x == '-c' or x.startswith(('--config-env', '--exec-path')):
            # 29. 9.: nastavenie, ktoré nič nespúšťa (safe.directory, core.quotePath, gc.auto …), čítanie nemení —
            # `sudo -n -u <vlastník> git -c safe.directory='*' -C <klon> rev-list …` je čítanie klonu kolegu
            # (propagate-sync-push-ahead.sh); core.fsmonitor, alias.*, core.pager … ostávajú „môže spúšťať“
            # 29. 9. neskoro večer (tretie kolo): výnimka „nastavenie, ktoré nič nespúšťa“ vrátená — `sudo -n git -c
            # safe.directory='*' -C /home/<kolega>/…` beží ako root a git potom poslúchne .git/config kolegu (core.fsmonitor,
            # diff a textconv ovládače) = spustenie cudzieho kódu ako root. Každé -c je „môže spúšťať“, ako pred 29. 9.
            ma_c = True
            j += 2 if x == '-c' else 1
            continue
        if x in ('--git-dir', '--work-tree', '--namespace', '--super-prefix') and j + 1 < len(t):
            if x == '--git-dir':
                gd = j + 1
            j += 2
            continue
        if x.startswith('--git-dir='):
            gd = x.split('=', 1)[1]
            j += 1
            continue
        if x.startswith('-'):
            j += 1
            continue
        break
    return (t[j] if j < len(t) else ''), j, C, gd, ma_c


def git_push_volby(r):
    """Voľby git push: force, zmazanie na vzdialenom repozitári, nasucho (--dry-run, -n), pomoc (--help, -h), pozičné.
    Hodnota volieb -o/--push-option/--repo/--receive-pack/--exec sa preskočí (git push -o --help … pošle voľbu „--help“)."""
    o = {'force': False, 'zmaz': False, 'nasucho': False, 'pomoc': False, 'poz': []}
    k = 0
    while k < len(r):
        x = r[k]
        if x == '--':
            o['poz'] += r[k + 1:]
            break
        if x.startswith('--'):
            meno, rov, _ = x[2:].partition('=')
            kand = [d for d in GIT_PUSH_DLHE if d == meno] or [d for d in GIT_PUSH_DLHE if meno and d.startswith(meno)]
            for d in kand:           # nejednoznačná skratka: git skončí chybou — kotva berie najprísnejší význam
                if d in ('force', 'force-with-lease', 'force-if-includes', 'mirror'):
                    o['force'] = True
                if d in ('delete', 'prune'):
                    o['zmaz'] = True
            if len(kand) == 1:
                o['nasucho'] |= kand[0] == 'dry-run'
                o['pomoc'] |= kand[0] == 'help'
                if kand[0] in GIT_PUSH_S_HODNOTOU and not rov:
                    k += 1
            k += 1
            continue
        if x.startswith('-') and len(x) > 1:
            telo = x[1:]
            for q, ch in enumerate(telo):
                if ch == 'o':                       # -o <voľba> / -ovoľba
                    if q == len(telo) - 1:
                        k += 1
                    break
                if ch == 'f':
                    o['force'] = True
                elif ch == 'd':
                    o['zmaz'] = True
                elif ch == 'n':
                    o['nasucho'] = True
            o['pomoc'] |= telo == 'h'
            k += 1
            continue
        if x.startswith('+'):
            o['force'] = True
        o['poz'].append(x)
        k += 1
    return o


def gh_pomoc(t):
    """gh … --help / -h naozaj ukáže pomoc, len keď nie je hodnotou predchádzajúcej voľby (gh pr create --body --help
    vytvorí PR s telom „--help“ — overené 24. 9. na gh pr list --state --help)."""
    for k, x in enumerate(t):
        if x in ('--help', '-h'):
            pred = t[k - 1] if k else ''
            if not pred.startswith('-') or '=' in pred or pred in GH_BEZ_HODNOTY:
                return True
    return False


def git_filter_nic(sub, r):
    """git filter-repo / filter-branch, ktorý históriu neprepíše (2. 10.: `git filter-repo --version` — kontrola, či je
    nainštalovaný, account-cp 25. 9. — kotva zastavila ako prepis histórie): príkaz bez iných volieb než --version,
    --help/-h a pri filter-repo --analyze (len správa do .git/filter-repo/analysis). Voľba popri filtri (--version --path x
    --force) nestačí — o poradí spracovania rozhoduje nástroj, nie kotva."""
    if sub == 'filter-branch':
        return r in (['--help'], ['-h'])
    return bool(r) and all(x in ('--version', '--help', '-h', '--analyze') for x in r)


def git_citanie(t):
    sub, j, C, gd, ma_c = git_rozbor(t)
    if ma_c:
        return False
    if not sub:
        return True
    r = t[j + 1:]
    if any(y.startswith('--output') for y in r):
        return False
    if sub in GIT_CITAJ:
        return True
    poz = [y for y in r if not y.startswith('-')]
    if sub == 'branch':
        zapis = {'-d', '-D', '-m', '-M', '-c', '-C', '-f', '--delete', '--move', '--copy', '--force', '-u', '--unset-upstream',
                 '--edit-description', '--track', '--no-track'}
        if any(y in zapis or y.startswith('--set-upstream') for y in r):
            return False
        return not poz or any(y in ('--list', '-l', '-a', '-r', '--all', '--remotes', '--contains', '--merged',
                                    '--no-merged', '--points-at') for y in r)
    if sub == 'tag':
        zapis = {'-d', '--delete', '-a', '--annotate', '-s', '--sign', '-f', '--force', '-m', '--message', '-F', '--file',
                 '-u', '--local-user'}
        if any(y in zapis for y in r):
            return False
        return not poz or any(y in ('-l', '--list', '--contains', '--points-at', '-n') for y in r)
    if sub == 'remote':
        return not r or r[0] in ('-v', '--verbose', 'show', 'get-url')
    if sub == 'config':
        zapis = {'--unset', '--unset-all', '--add', '--replace-all', '--rename-section', '--remove-section', '-e', '--edit',
                 'set', 'unset', 'rename-section', 'remove-section', 'edit'}
        if any(y in zapis for y in r):
            return False
        return any(y in ('--get', '--get-all', '--get-regexp', '--list', '-l', '--get-urlmatch', 'get', 'list') for y in r) \
            or len(poz) <= 1
    if sub == 'symbolic-ref':      # 29. 9.: s jedným menom (symbolic-ref -q --short HEAD) len číta
        return len(poz) <= 1 and not any(y in ('-d', '--delete', '-m') for y in r)
    if sub == 'stash':
        return bool(r) and r[0] in ('list', 'show')
    if sub == 'worktree':
        return bool(r) and r[0] == 'list'
    if sub == 'reflog':
        return not r or r[0] == 'show' or r[0].startswith('-')
    if sub == 'submodule':
        return bool(r) and r[0] in ('status', 'summary')
    if sub == 'notes':
        return bool(r) and r[0] in ('list', 'show')
    return False


def ip_citanie(t):
    poz = []
    j = 0
    while j < len(t):
        x = t[j]
        if x in ('-b', '-batch', '--batch', '-force'):
            return False
        if x in ('-n', '-netns', '-f', '-family', '-l', '-loops', '-rc', '-rcvbuf'):
            j += 2
            continue
        if x.startswith('-'):
            j += 1
            continue
        poz.append(x)
        j += 1
    if len(poz) <= 1:
        return True
    if poz[0] == 'netns':
        return poz[1] in ('list', 'ls', 'show', 'identify', 'pids', 'list-id')
    return poz[1] in ('show', 'list', 'ls', 'lst', 'get', 'sh', 's', 'l')


def sysctl_sub(t):
    j = 0
    while j < len(t):
        x = t[j]
        if x in SYSCTL_ARG:
            j += 2
            continue
        if x.startswith('-'):
            j += 1
            continue
        return x
    return None


def kontajner_citanie(t):
    poz = pozicne(t, 'H', ('--host', '--context', '--config', '--log-level', '--connection', '--url'))
    if not poz:
        return True
    if poz[0] in ('ps', 'logs', 'inspect', 'images', 'stats', 'top', 'port', 'version', 'info', 'events', 'history', 'diff'):
        return True
    return poz[0] in ('container', 'image', 'volume', 'network', 'system', 'pod') and len(poz) > 1 and \
        poz[1] in ('ls', 'list', 'inspect', 'logs', 'df', 'info', 'ps', 'top', 'exists')


def ucet_citanie(pb, t):
    """passwd -S [-a] [meno] (stav hesla) a chage -l [-i] [meno] (platnosť hesla) len čítajú. 6. 10. (škody u kolegov):
    kotva zastavila Mariána Lacenu pri `sudo -n passwd -S <kolega>` — výpis stavu brala ako zmenu hesla. Iná voľba
    (zamknúť, odomknúť, zmazať, nastaviť dni …), viac ako jedno meno alebo bez voľby výpisu = zápis ako doteraz."""
    s, l = ('Saq', ('--status', '--all', '--quiet')) if pb == 'passwd' else ('li', ('--list', '--iso8601'))
    vypis = False
    for x in t:
        if x == '--':
            break
        if x in ('-h', '--help'):
            return True
        if x.startswith('--'):
            if x not in l:
                return False
            vypis |= x == l[0]
        elif x.startswith('-') and len(x) > 1:
            if not set(x[1:]) <= set(s):
                return False
            vypis |= s[0] in x[1:]
    return vypis and len(pozicne(t)) <= 1


def citanie(pb, t):
    """Príkaz len číta."""
    if pb in CITAJ:
        if pb == 'hostname':
            return not any(not x.startswith('-') for x in t) and not any(x in ('-F', '--file', '-b', '--boot') for x in t)
        if pb == 'date':
            return not any(x in ('-s', '--set') or x.startswith('--set=') for x in t)
        if pb == 'mount':
            return not t
        return True
    if pb == 'sed':
        return sed_citanie(t)
    if pb in ('awk', 'gawk', 'mawk', 'nawk'):
        return awk_citanie(t)
    if pb == 'sort':
        return not any(x.startswith(('--output', '--compress-program')) or
                       (x.startswith('-') and not x.startswith('--') and 'o' in x) for x in t)
    if pb == 'uniq':
        return len(pozicne(t, 'fsw', ('--skip-fields', '--skip-chars', '--check-chars'))) <= 1
    if pb == 'find':
        return not any(x in FIND_ZAPIS for x in t)
    if pb == 'git':
        return git_citanie(t)
    if pb == 'ip':
        return ip_citanie(t)
    if pb == 'journalctl':
        return not any(x.startswith(JOURNAL_ZAPIS) for x in t)
    if pb == 'dmesg':
        return not any(x.split('=')[0] in DMESG_ZAPIS or
                       (x.startswith('-') and not x.startswith('--') and set(x[1:]) & set('cCDEn')) for x in t)
    if pb == 'systemctl':
        s = sysctl_sub(t)
        return s is None or s in SYSCTL_CITAJ
    if pb == 'crontab':
        return '-l' in t and not any(x in ('-r', '-e', '-i') for x in t)
    if pb in ('passwd', 'chage'):
        return ucet_citanie(pb, t)
    if pb == 'ipsec':
        return bool(t) and t[0] in ('status', 'statusall', 'listall', 'listcerts', 'listcacerts', 'version', '--version')
    if pb in ('docker', 'podman'):
        return kontajner_citanie(t)
    if pb in ('gunzip', 'gzip', 'xz', 'unxz', 'bzip2', 'bunzip2', 'zstd', 'unzstd'):
        return any(x in ('--stdout', '--to-stdout', '--list', '--test') or
                   (x.startswith('-') and not x.startswith('--') and set(x[1:]) & set('clt')) for x in t)
    if pb.startswith('python') and INTERPRETY.match(pb):
        if t in (['--version'], ['-V']):
            return True
        return len(t) >= 2 and t[0] == '-m' and t[1] == 'json.tool' and len(pozicne(t[2:])) <= 1
    if pb == 'command':
        return t[:1] in (['-v'], ['-V'])
    if pb == 'php':
        return '-l' in t
    if pb in SHELLY:      # bash -n = kontrola syntaxe
        # 3. 10.: len voľby pred skriptom či textom príkazu (-c) — `bash …/aig-crit-notify.sh -n` by poslal kartu a kotva
        # ho brala ako kontrolu syntaxe (-n kdekoľvek medzi argumentmi); slovo za skriptom je jeho argument
        k = 0
        while k < len(t):
            x = t[k]
            if x in ('--rcfile', '--init-file', '-o', '+o', '-O', '+O'):
                k += 2
                continue
            if x == '--' or len(x) < 2 or x[0] not in '-+':
                return False
            if x[0] == '-' and not x.startswith('--') and 'n' in x[1:] and 'c' not in x[1:]:
                return True
            if x[0] == '-' and not x.startswith('--') and 'c' in x[1:]:
                return False                 # -c: ďalšie slovo je text príkazu, -n za ním je jeho $0
            k += 1
        return False
    return False


def neposiela(pb, t):
    if pb in NEPOSIELA:
        return True
    if pb in ('docker', 'podman'):
        poz = pozicne(t, 'H')
        return not poz or poz[0] not in ('exec', 'run', 'start', 'compose')
    if pb.startswith('python'):
        return len(t) >= 2 and t[0] == '-m' and t[1] in ('json.tool', 'py_compile', 'compileall')
    return citanie(pb, t)


# ---------- kód interpretra: čo naozaj spúšťa (pošta; oprava 24. 9. po nezávislom overení) ----------
# Predchádzajúca oprava hľadala meno poštového programu len doslova v argumente volania (do 800 znakov) a argument, ktorý
# začínal čítaním (cat, git …), brala celý ako čítanie. Prešlo tak skutočné odoslanie, ktoré odoslaná kotva 10.06 zastavila:
# cesta v premennej (N = '…aig-crit-notify.sh'; subprocess.run(['bash', N])), shellový reťazec „cat … | bash …notify.sh“,
# exo-rest.py načítaný cez importlib s cestou v premennej, node execSync('bash ' + N). Teraz sa argument každého volania,
# ktoré spúšťa proces, vyhodnotí (reťazce, premenné, zoznamy, spájanie, f-reťazce, os.path.join …) a výsledný príkaz
# posúdi ten istý rozbor shellu ako príkaz Bash: grep/cat/git nad poštovým skriptom je čítanie, bash/python s poštovým
# skriptom je odoslanie. Čo sa vyhodnotiť nedá (parameter funkcie, sys.argv, neznáma funkcia), sa dosadí ako poštový
# program (najhorší prípad) — to všetko len v kóde, ktorý meno poštového programu vôbec obsahuje. Keď rozbor kódu zlyhá,
# platí pravidlo odoslanej kotvy 10.06 (meno programu a spúšťanie procesu kdekoľvek v kóde).
KOD_SPUSTA_1006 = re.compile(r'subprocess|os\.system|os\.popen|os\.exec|os\.spawn|Popen|check_call|check_output|child_process'
                             r'|execSync|execFileSync|spawnSync|\bspawn\(|shell_exec|proc_open|passthru|\bsystem\s*\(|Start-Process'
                             r'|Invoke-Expression|importlib|spec_from_file_location|SourceFileLoader|runpy|exec\s*\(\s*open'
                             r'|__import__|\brequire\s*\(')
NAJHORSIE = '/usr/local/sbin/aig-crit-notify.sh'     # hodnota, ktorú kotva nevie prečítať = poštový program
# známe hodnoty, ktoré nie sú poštový program: interpreter, cesta samotného skriptu (priečinok pred /x.py nerozhoduje)
KOD_ZNAME = {'sys.executable': 'python3', 'process.execPath': 'node', '__file__': '/nezname/skript', '__dirname': '/nezname',
             '__filename': '/nezname/skript'}
KOD_MODULY = {'subprocess', 'os', 'child_process', 'pty', 'commands', 'asyncio', 'Open3', 'IO', 'Kernel', 'pexpect', 'posix'}
KOD_SPUSTA_POSLEDNE = {'system', 'popen', 'Popen', 'call', 'check_call', 'check_output', 'run', 'getoutput', 'getstatusoutput',
                       'execv', 'execve', 'execvp', 'execvpe', 'execl', 'execle', 'execlp', 'execlpe', 'spawnv', 'spawnve',
                       'spawnvp', 'spawnvpe', 'spawnl', 'spawnle', 'spawnlp', 'spawnlpe', 'posix_spawn', 'posix_spawnp', 'exec',
                       'execSync', 'execFile', 'execFileSync', 'spawn', 'spawnSync', 'fork', 'create_subprocess_exec',
                       'create_subprocess_shell', 'capture2', 'capture2e', 'capture3', 'popen2', 'popen2e', 'popen3', 'run_command'}
# holé meno (alebo za neznámym objektom), ktoré je spúšťaním vždy — „run“, „call“, „spawn“ bez modulu nie (m.run v exo-rest)
KOD_SPUSTA_HOLE = {'Popen', 'check_call', 'check_output', 'execSync', 'execFileSync', 'spawnSync', 'shell_exec', 'proc_open',
                   'passthru', 'system', 'popen', 'getoutput', 'getstatusoutput', 'create_subprocess_exec',
                   'create_subprocess_shell'}
KOD_SPUSTA_HOLE_BEZ_OBJEKTU = {'exec', 'execFile', 'spawn', 'fork', 'eval'}
KOD_NACITA = {'spec_from_file_location', 'SourceFileLoader', 'run_path', '__import__', 'require', 'import_module',
              'load_source', 'require_once', 'require_relative'}
# Perl, Ruby, PHP: spúšťanie spätnými apostrofmi alebo qx/%x — rozbor volaní ho nevidí, platí pravidlo 10.06
KOD_ZACHRANA = re.compile(r'`|%x[({\[]|\bqx\s*[({\[/]')
# kód priamo v príkaze (kod_v_prikaze): interpreter → jazyk a voľba, za ktorou kód stojí
KOD_V_PRIKAZE = {'node': 'js', 'nodejs': 'js', 'perl': 'pl', 'ruby': 'rb'}
KOD_VOLBY = {'py': ('-c', '-e', '--eval', '-p', '--print'), 'js': ('-c', '-e', '--eval', '-p', '--print'),
             'pl': ('-e', '-E'), 'rb': ('-e',), 'php': ('-r',)}
KOD_PREDPONY = {'r', 'u', 'b', 'f', 'rb', 'br', 'fr', 'rf'}
KOD_MENO = re.compile(r'[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*')
KOD_CISLO = re.compile(r'\d[\w.]*')


def kod_jazyk(pb, prvy_riadok=''):
    """Jazyk kódu podľa interpretra alebo shebangu: py · js · php · pl · rb · iny (PowerShell, osascript …)."""
    x = (pb or '') + ' ' + (prvy_riadok or '')
    for vzor, j in (('python', 'py'), ('node', 'js'), ('deno', 'js'), ('bun', 'js'), ('php', 'php'), ('perl', 'pl'),
                    ('ruby', 'rb')):
        if vzor in x:
            return j
    return 'iny'


_KOD_TOKENY_PAMAT = {}


def kod_tokeny(kod, jazyk):
    """Tokeny kódu (_kod_tokeny); 14. 10. (K88X): ten istý text sa rozkladá raz — skript s rôznymi argumentmi aj modul
    od viacerých načítajúcich (pri ng-stav.py 352 volaní, 172 rôznych textov). Vracia kópiu: KodRozbor zoznam dopĺňa
    a časti reťazca sú zoznamy."""
    k = (kod, jazyk)
    t = _KOD_TOKENY_PAMAT.get(k)
    if t is None:
        if len(_KOD_TOKENY_PAMAT) >= 512:
            _KOD_TOKENY_PAMAT.clear()
        t = _KOD_TOKENY_PAMAT[k] = tuple(_kod_tokeny(kod, jazyk))
    return [(d, list(v)) if d == 's' else (d, v) for d, v in t]


def _kod_tokeny(kod, jazyk):
    """Tokeny kódu: ('s', časti reťazca) · ('n', meno s bodkami) · ('c', číslo) · ('p', znak) · ('nl', None)."""
    t = []
    i = 0
    n = len(kod)
    hlbka = 0
    while i < n:
        c = kod[i]
        if c == '\n':
            if hlbka <= 0:
                t.append(('nl', None))
            i += 1
            continue
        if c in ' \t\r\f\v':
            i += 1
            continue
        if c == '\\' and i + 1 < n and kod[i + 1] == '\n':
            i += 2
            continue
        if c == '#' and jazyk in ('py', 'pl', 'rb', 'php'):
            j = kod.find('\n', i)
            i = n if j < 0 else j
            continue
        if jazyk in ('js', 'php') and kod.startswith('//', i):
            j = kod.find('\n', i)
            i = n if j < 0 else j
            continue
        if jazyk in ('js', 'php') and kod.startswith('/*', i):
            j = kod.find('*/', i + 2)
            i = n if j < 0 else j + 2
            continue
        if c in '\'"' or (c == '`' and jazyk == 'js'):
            i, casti = kod_retazec(kod, i, '', jazyk)
            t.append(('s', casti))
            continue
        m = KOD_MENO.match(kod, i)
        if m:
            meno = m.group()
            j = m.end()
            if jazyk == 'py' and meno.lower() in KOD_PREDPONY and j < n and kod[j] in '\'"':
                i, casti = kod_retazec(kod, j, meno.lower(), jazyk)
                t.append(('s', casti))
                continue
            t.append(('n', meno))
            i = j
            continue
        m = KOD_CISLO.match(kod, i)
        if m:
            t.append(('c', m.group()))
            i = m.end()
            continue
        dva = kod[i:i + 2]
        if dva in ('==', '!=', '<=', '>=', '+=', '=>', '**', '->', ':=', '::', '||', '&&', '//', '??'):
            t.append(('p', dva))
            i += 2
            continue
        if c in '([{':
            hlbka += 1
        elif c in ')]}':
            hlbka -= 1
        t.append(('p', c))
        i += 1
    return t


# SKG 3 (b10, 9. 11.): úsek reťazca bez znaku, pri ktorom slučka robí niečo iné než „pridaj znak“ (úvodzovka reťazca,
# \\, pri f-reťazci { a }, pri spätných apostrofoch $, pri interpolácii Ruby #, PHP a Perlu $), sa pridá naraz jedným
# porovnaním vzoru namiesto znaku po znaku. Ostatné vetvy slučky sa pri takom znaku nevykonajú, takže index aj časti sú
# rovnaké (17,4 milióna porovnaní na náhodných textoch zo zvláštnych znakov vo všetkých jazykoch a predponách, súbory kódu
# projektov PS: 0 rozdielov). Pri `ng-stav.py` s celým textom −0,25 s (kod_bez_retazcov a tokeny kódu).
_RETAZEC_USEK = {}


def kod_retazec(kod, i, pred, jazyk):
    """Reťazec od kod[i] (úvodzovka) → (index za ním, časti [('l', text) | ('e', zdroj vloženého výrazu)])."""
    n = len(kod)
    q = kod[i]
    koniec = q * 3 if (jazyk == 'py' and kod.startswith(q * 3, i)) else q
    i += len(koniec)
    surovy = 'r' in pred
    f = 'f' in pred or q == '`'
    interp = jazyk in ('php', 'pl', 'rb') and q == '"'
    spec = q + '\\' + ('}' if f else '') + ('{' if f and q != '`' else '') + ('$' if q == '`' else '') \
        + ('#' if interp and jazyk == 'rb' else '') + ('$' if interp and jazyk in ('php', 'pl') else '')
    usek = _RETAZEC_USEK.get(spec)
    if usek is None:
        usek = _RETAZEC_USEK[spec] = re.compile('[^' + re.escape(spec) + ']+')
    casti = []
    buf = []

    def lit():
        if buf:
            casti.append(('l', ''.join(buf)))
            buf.clear()
    while i < n:
        m = usek.match(kod, i)
        if m:
            buf.append(m.group())
            i = m.end()
            continue
        if kod.startswith(koniec, i):
            i += len(koniec)
            break
        c = kod[i]
        if c == '\\' and i + 1 < n:
            d = kod[i + 1]
            buf.append(c + d if surovy else {'n': '\n', 't': '\t', 'r': '\r', '0': ''}.get(d, d))
            i += 2
            continue
        if f and q != '`' and c == '{':
            if kod.startswith('{{', i):
                buf.append('{')
                i += 2
                continue
            j = kod_zatvorka(kod, i, '{', '}')
            lit()
            casti.append(('e', re.split(r'![rsa]$|:(?![^\[(]*[\])])', kod[i + 1:j - 1])[0]))
            i = j
            continue
        if f and c == '}' and kod.startswith('}}', i):
            buf.append('}')
            i += 2
            continue
        if q == '`' and kod.startswith('${', i):
            j = kod_zatvorka(kod, i + 1, '{', '}')
            lit()
            casti.append(('e', kod[i + 2:j - 1]))
            i = j
            continue
        if interp and jazyk == 'rb' and kod.startswith('#{', i):
            j = kod_zatvorka(kod, i + 1, '{', '}')
            lit()
            casti.append(('e', kod[i + 2:j - 1]))
            i = j
            continue
        if interp and jazyk in ('php', 'pl') and c == '$':
            m = re.compile(r'\$\{?([A-Za-z_]\w*)\}?').match(kod, i)
            if m:
                lit()
                casti.append(('e', '$' + m.group(1)))
                i = m.end()
                continue
        buf.append(c)
        i += 1
    lit()
    return i, casti


def kod_zatvorka(kod, i, o, z):
    """Index za zatváracou zátvorkou k kod[i] == o (reťazce v nej sa preskočia)."""
    h = 0
    n = len(kod)
    while i < n:
        c = kod[i]
        if c in '\'"':
            k = i + 1
            while k < n and kod[k] != c:
                k += 2 if kod[k] == '\\' else 1
            i = k + 1
            continue
        if c == o:
            h += 1
        elif c == z:
            h -= 1
            if h == 0:
                return i + 1
        i += 1
    return n


# Kód, ktorý vie reťazec SPUSTIŤ (exec, eval, Invoke-Expression, new Function) — tam je reťazec kódom, preto sa
# pri ňom posudzuje celý text ako pred 10. 10.
# Holé meno, nie metóda na objekte: `re.compile(…)` ani `spec.loader.exec_module(…)` reťazec nespúšťajú.
KOD_RETAZEC_SPUSTA = re.compile(r'(?<![.\w])(?:exec|eval|execfile)\b|(?<![.\w])compile\s*\('
                                r'|Invoke-Expression|\bnew\s+Function\s*\('
                                r'|\b(?:instance_eval|class_eval|module_eval)\b')


# SKG 3 (b) (7. 11. 2026): úsek, ktorý slučka kod_bez_retazcov prepíše bez zmeny — znak, ktorý nezačína meno, reťazec ani
# komentár, a celé meno podľa KOD_MENO (v pythone nie pred úvodzovkou, tam môže byť predpona reťazca) — prejde jedným
# porovnaním vzoru namiesto znaku po znaku. Jednotky sú tie isté ako v slučke (meno atomicky, bez skracovania), takže
# výsledok je rovnaký; pri 1,16 MB ng-cervene-testy.py 0,19 → 0,13 s, na 5 627 súboroch projektov 121 → 79 s (kus SKG 3: rozbor s celým textom pod strop).
_BEZ_USEK = {}


def _bez_usek(jazyk):
    v = _BEZ_USEK.get(jazyk)
    if v is None:
        spec = '\'"' + ('#' if jazyk in ('py', 'pl', 'rb', 'php') else '') + ('`' if jazyk == 'js' else '') \
            + ('/' if jazyk in ('js', 'php') else '')
        meno = r'(?>[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*)' + (r'(?![\'"])' if jazyk == 'py' else '')
        v = _BEZ_USEK[jazyk] = re.compile(r'(?:[^A-Za-z_$' + re.escape(spec) + r']|' + meno + r')+')
    return v


@_pamat
def kod_bez_retazcov(kod, jazyk):
    """Kód s vyprázdnenými reťazcami a komentármi (čo je v reťazci, je údaj, nie volanie). Prechádza text rovnako ako
    kod_tokeny a dĺžku reťazca aj zlomy riadkov zachová, takže sa nerozsype na apostrofe v komentári ani na úvodzovke
    v reťazci. SKG 3 (b6, 8. 11.): s pamäťou — nad tým istým textom ho volá kod_posta_sama aj kod_posta_siet
    (ng-cervene-testy.py s celým textom 2 × 0,14 s)."""
    n = len(kod)
    von = []
    i = 0
    usek = _bez_usek(jazyk)

    def medzery(od, do):
        return ''.join(x if x == '\n' else ' ' for x in kod[od:do])

    while i < n:
        m = usek.match(kod, i)
        if m:
            von.append(m.group())
            i = m.end()
            continue
        c = kod[i]
        if c == '#' and jazyk in ('py', 'pl', 'rb', 'php'):
            j = kod.find('\n', i)
            j = n if j < 0 else j
            von.append(medzery(i, j))
            i = j
            continue
        if jazyk in ('js', 'php') and kod.startswith('//', i):
            j = kod.find('\n', i)
            j = n if j < 0 else j
            von.append(medzery(i, j))
            i = j
            continue
        if jazyk in ('js', 'php') and kod.startswith('/*', i):
            j = kod.find('*/', i + 2)
            j = n if j < 0 else j + 2
            von.append(medzery(i, j))
            i = j
            continue
        if c in '\'"' or (c == '`' and jazyk == 'js'):
            j = kod_retazec(kod, i, '', jazyk)[0]
            von.append(medzery(i, max(j, i + 1)))
            i = max(j, i + 1)
            continue
        m = KOD_MENO.match(kod, i)
        if m:
            meno = m.group()
            e = m.end()
            if jazyk == 'py' and meno.lower() in KOD_PREDPONY and e < n and kod[e] in '\'"':
                j = kod_retazec(kod, e, meno.lower(), jazyk)[0]
                von.append(medzery(i, max(j, e + 1)))
                i = max(j, e + 1)
                continue
            von.append(meno)
            i = e
            continue
        von.append(c)
        i += 1
    return ''.join(von)


# PWS (10. 10. 2026): cmdlet PowerShellu odosiela sám, takže pár „poštové volanie + sieť“ ho nechytil —
# `pwsh -c "Send-MailMessage -To … -SmtpServer …"` prešiel bez kanála (overené suchým rozborom 10. 10. 13:45;
# `powershell -Command Send-MailMessage …` bez úvodzoviek zastavila značka v samotnom príkaze). Berie sa ako odoslanie
# samo o sebe, ale len (1) ako KÓD, nie text v reťazci (katalóg skúšok a návod odoslanie nie sú — ako pri kod_posta_siet),
# a (2) s adresátom alebo poštovým serverom v tom istom volaní, takže `Get-Help Send-MailMessage` prejde.
KOD_POSTA_SAMA = re.compile(r'Send-MailMessage\b(?=[\s\S]{0,400}?-(?:To|Cc|Bcc|SmtpServer)\b)', re.I)


def kod_posta_sama(kd, jazyk):
    """Volanie, ktoré poštu odošle samo, bez značky siete (cmdlet Send-MailMessage s adresátom alebo serverom)."""
    if not KOD_POSTA_SAMA.search(kd):
        return False
    try:
        bez = kod_bez_retazcov(kd, jazyk)
    except (RecursionError, ValueError, IndexError, KeyError, TypeError, re.error):
        return True
    return bool(KOD_POSTA_SAMA.search(bez) or KOD_RETAZEC_SPUSTA.search(bez))


# ---- SKG 3 (a): dosah spúšťača reťazca v pythone (ktoré reťazce sa môžu dostať do exec/eval/compile) ----
_SPUSTA = {'exec', 'eval', 'execfile', 'compile'}
_MUT = {'append', 'extend', 'insert', 'update', 'add', 'setdefault', '__setitem__', 'appendleft', 'extendleft',
        '__init__', '__dict__'}
_CISTE = {'len', 'str', 'repr', 'int', 'float', 'bool', 'isinstance', 'print', 'sorted', 'list', 'tuple', 'set',
          'dict', 'min', 'max', 'sum', 'any', 'all', 'enumerate', 'zip', 'range', 'type', 'hash', 'id', 'hasattr',
          'getattr', 'format', 'iter', 'next', 'reversed', 'abs', 'round', 'ord', 'chr', 'callable', 'issubclass'}
_ID = re.compile(r'[A-Za-z_]\w*')


def _koren(u):
    while isinstance(u, (ast.Attribute, ast.Subscript, ast.Starred)):
        u = u.value
    return u.id if isinstance(u, ast.Name) else None


def _ciele(t, von):
    if isinstance(t, (ast.Tuple, ast.List)):
        for x in t.elts:
            _ciele(x, von)
    elif isinstance(t, ast.Starred):
        _ciele(t.value, von)
    else:
        r = _koren(t)
        if r:
            von.add(r)
    return von


def _priradenia(uzly):
    """(mená cieľov, hodnota) pre každé viazanie mena v jednotke (tok bez ohľadu na poradie)."""
    von = []
    for k in uzly:
        for u in ast.walk(k):
            if isinstance(u, ast.Assign):
                c = set()
                for t in u.targets:
                    _ciele(t, c)
                von.append((c, u.value))
            elif isinstance(u, (ast.AugAssign, ast.AnnAssign)) and u.value is not None:
                von.append((_ciele(u.target, set()), u.value))
            elif isinstance(u, ast.NamedExpr):
                von.append(({u.target.id}, u.value))
            elif isinstance(u, (ast.For, ast.AsyncFor, ast.comprehension)):
                von.append((_ciele(u.target, set()), u.iter))
            elif isinstance(u, ast.withitem) and u.optional_vars is not None:
                von.append((_ciele(u.optional_vars, set()), u.context_expr))
            elif isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)) and u not in uzly:
                von.append(({u.name}, u))
    return von


def _mena(u):
    m = set()
    for x in ast.walk(u):
        if isinstance(x, ast.Name):
            m.add(x.id)
        elif isinstance(x, ast.Attribute):
            r = _koren(x)
            if r:
                m.add(r)
    return m


def _retazce(uzly):
    for k in uzly:
        for x in ast.walk(k):
            if isinstance(x, ast.Constant) and isinstance(x.value, (str, bytes)):
                yield x.value if isinstance(x.value, str) else x.value.decode('latin-1')


class _Dosah:
    def __init__(self, strom):
        self.mena = {}            # meno funkcie/metódy/triedy → [uzol funkcie]
        self.modul = []
        self.importy = set()
        self.glob = set()         # mená priradené na úrovni modulu (premenné)
        for u in strom.body:
            if isinstance(u, (ast.FunctionDef, ast.AsyncFunctionDef)):
                self.mena.setdefault(u.name, []).append(u)
            elif isinstance(u, ast.ClassDef):
                for v in u.body:
                    if isinstance(v, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        self.mena.setdefault(v.name, []).append(v)
                        self.mena.setdefault(u.name, []).append(v)
                    else:
                        self.modul.append(v)
                self.modul.extend(u.decorator_list + u.bases + [k.value for k in u.keywords])
            else:
                self.modul.append(u)
        self.modul_spusta = False         # spúšťač na úrovni modulu
        self.modul_ret = []               # reťazce modulu v poradí _retazce
        for u in self.modul:
            for x in ast.walk(u):
                if isinstance(x, (ast.Import, ast.ImportFrom)):
                    for a in x.names:
                        self.importy.add((a.asname or a.name).split('.')[0])
                elif isinstance(x, (ast.Assign, ast.AugAssign, ast.AnnAssign)):
                    for t in (x.targets if isinstance(x, ast.Assign) else [x.target]):
                        self.glob |= _ciele(t, set())
                elif isinstance(x, ast.Name):
                    if x.id in _SPUSTA:
                        self.modul_spusta = True
                elif isinstance(x, ast.Constant) and isinstance(x.value, (str, bytes)):
                    self.modul_ret.append(x.value if isinstance(x.value, str) else x.value.decode('latin-1'))
        # SKG 3 (b, 7. 11.): jeden prechod každej funkcie — text() a glob_menia potom pracujú s množinami; predtým
        # prechádzali strom v každom kole (ng-cervene-testy.py s celým textom: glob_menia 4× cez všetky funkcie, 1,0 s)
        self.info = {}                    # funkcia → [mená (Name.id, Attribute.attr), globály, ktoré mení, reťazce, None]
        self.spusta = []                  # (funkcia, uzol Name spúšťača)
        for fs in self.mena.values():
            for f in fs:
                if f in self.info:
                    continue
                vid, meni, ret = set(), set(), []
                for x in ast.walk(f):
                    if isinstance(x, ast.Name):
                        vid.add(x.id)
                        if x.id in _SPUSTA:
                            self.spusta.append((f, x))
                    elif isinstance(x, ast.Attribute):
                        vid.add(x.attr)
                    elif isinstance(x, ast.Constant):
                        if isinstance(x.value, (str, bytes)):
                            ret.append(x.value if isinstance(x.value, str) else x.value.decode('latin-1'))
                    elif isinstance(x, (ast.Import, ast.ImportFrom)):
                        for a in x.names:
                            self.importy.add((a.asname or a.name).split('.')[0])
                    elif isinstance(x, ast.Global):
                        meni.update(x.names)
                    elif isinstance(x, (ast.Assign, ast.AugAssign)):
                        for t in (x.targets if isinstance(x, ast.Assign) else [x.target]):
                            if isinstance(t, (ast.Attribute, ast.Subscript)):
                                r = _koren(t)
                                if r:
                                    meni.add(r)
                    elif isinstance(x, ast.Call):
                        if isinstance(x.func, ast.Attribute) and x.func.attr in _MUT:
                            r = _koren(x.func.value)
                            if r:
                                meni.add(r)
                self.info[f] = [vid, meni, ret, None]
        self.glob -= set(self.mena)
        self.attr = set()

    def mutuje(self, f, hlbka=3, metoda=False):
        """Môže funkcia f v pamäti zmeniť niektorý svoj parameter (okrem self pri metóde)?"""
        if hlbka <= 0:
            return True
        par = [a.arg for a in f.args.posonlyargs + f.args.args + f.args.kwonlyargs]
        if f.args.vararg:
            par.append(f.args.vararg.arg)
        if f.args.kwarg:
            par.append(f.args.kwarg.arg)
        if metoda and par:
            par = par[1:]
        par = set(par)
        for x in ast.walk(f):
            if isinstance(x, (ast.Assign, ast.AugAssign, ast.AnnAssign, ast.Delete)):
                for t in (x.targets if isinstance(x, (ast.Assign, ast.Delete)) else [x.target]):
                    for tt in (t.elts if isinstance(t, (ast.Tuple, ast.List)) else [t]):
                        if isinstance(tt, (ast.Attribute, ast.Subscript)) and _koren(tt) in par:
                            return True
            elif isinstance(x, (ast.Global, ast.Nonlocal)):
                if par & set(x.names):
                    return True
            elif isinstance(x, ast.Call):
                fn = x.func
                arg = [a for a in x.args] + [k.value for k in x.keywords]
                if isinstance(fn, ast.Attribute) and _koren(fn.value) in par:
                    if fn.attr in _MUT or isinstance(fn.value, ast.Attribute):
                        return True
                    for g in self.mena.get(fn.attr, []):
                        if self.mutuje(g, hlbka - 1, metoda=True) or self._self_meni(g):
                            return True
                    continue
                if not any(_koren(a) in par for a in arg):
                    continue
                if isinstance(fn, ast.Name) and fn.id in ('setattr', 'vars', 'exec', 'eval'):
                    return True
                meno = fn.id if isinstance(fn, ast.Name) else fn.attr if isinstance(fn, ast.Attribute) else None
                if meno in self.mena:
                    if any(self.mutuje(g, hlbka - 1, metoda=isinstance(fn, ast.Attribute)) for g in self.mena[meno]):
                        return True
                elif meno in _CISTE or meno is None:
                    if meno is None:
                        return True
                elif isinstance(fn, ast.Attribute) and _koren(fn) not in self.importy and fn.attr in _MUT:
                    return True
        return False

    def _self_meni(self, g):
        par = [a.arg for a in g.args.posonlyargs + g.args.args]
        if not par:
            return False
        s = par[0]
        for x in ast.walk(g):
            if isinstance(x, (ast.Assign, ast.AugAssign, ast.AnnAssign)):
                for t in (x.targets if isinstance(x, ast.Assign) else [x.target]):
                    if isinstance(t, (ast.Attribute, ast.Subscript)) and _koren(t) == s:
                        return True
            elif isinstance(x, ast.Call) and isinstance(x.func, ast.Attribute) and _koren(x.func.value) == s \
                    and x.func.attr in _MUT:
                return True
        return False

    def rez(self, uzly, S, par):
        """Rez toku v jednotke: mená, od ktorých závisí S (priradenia, volania so S v argumentoch).
        Vráti (S, volané mená funkcií zo súboru) alebo None, keď do rezu tečie parameter."""
        S = set(S)
        prir = _priradenia(uzly)
        volania = [x for k in uzly for x in ast.walk(k) if isinstance(x, ast.Call)]
        hotove = set()
        dalej = True
        while dalej:
            dalej = False
            for i, (c, h) in enumerate(prir):
                if i not in hotove and c & S:
                    hotove.add(i)
                    S |= _mena(h)
                    self.attr |= {x.attr for x in ast.walk(h) if isinstance(x, ast.Attribute)}
                    dalej = True
            for j, v in enumerate(volania):
                if ('v', j) in hotove:
                    continue
                arg = [a for a in v.args] + [k.value for k in v.keywords]
                if not any(_mena(a) & S for a in arg):
                    continue
                fn = v.func
                meno = fn.id if isinstance(fn, ast.Name) else fn.attr if isinstance(fn, ast.Attribute) else None
                if meno in _CISTE and isinstance(fn, ast.Name):
                    continue
                if meno in self.mena:
                    if not any(self.mutuje(g, metoda=isinstance(fn, ast.Attribute)) for g in self.mena[meno]):
                        continue
                elif isinstance(fn, ast.Attribute) and _koren(fn) in self.importy:
                    continue                    # knižnica: reťazce súboru do argumentu nevloží
                hotove.add(('v', j))
                S |= _mena(v)
                self.attr |= {x.attr for x in ast.walk(v) if isinstance(x, ast.Attribute)}
                dalej = True
        if S & par:
            return None
        return S

    def text(self):
        spusta = self.spusta
        if self.modul_spusta:
            return None                         # spúšťač na úrovni modulu: celý text
        if not spusta:
            return None
        rodic = {}
        for f in {f for f, _ in spusta}:
            for x in ast.walk(f):
                for c in ast.iter_child_nodes(x):
                    rodic[c] = x
        F = set()                               # celé dosiahnuteľné jednotky
        mena_f = set()
        for f, n in spusta:
            p = rodic.get(n)
            if not (isinstance(p, ast.Call) and p.func is n):
                return None                     # spúšťač ako hodnota (alias) — celý text
            if n.id == 'compile' and isinstance(rodic.get(p), ast.Expr):
                continue                        # compile bez použitia výsledku len skúša syntax — nič nespustí
            F.add(f)
            par = {a.arg for a in f.args.posonlyargs + f.args.args + f.args.kwonlyargs}
            if f.args.vararg:
                par.add(f.args.vararg.arg)
            if f.args.kwarg:
                par.add(f.args.kwarg.arg)
            S = self.rez([f], _mena(p) - {n.id}, par)
            if S is None:
                return None                     # do spúšťača tečie parameter — celý text
            mena_f |= S
        # globálne premenné v reze: rez v module; funkcie, ktoré ich menia, sú celé
        G = {g for g in mena_f if g in self.glob}
        if G:
            Sm = self.rez(self.modul, G, set())
            mena_f |= Sm
        rad = [m for m in mena_f | self.attr if m in self.mena]
        vid = set()                             # mená v kóde a reťazcoch F (F len rastie, preto len pribudnuté)
        videne = set()
        while True:
            for m in rad:
                for g in self.mena.get(m, []):
                    F.add(g)
            for g in F - videne:
                i = self.info[g]
                if i[3] is None:
                    i[3] = set()
                    for s in i[2]:
                        i[3].update(_ID.findall(s))
                vid |= i[0]
                vid |= i[3]
            videne = set(F)
            nove = [m for m in vid if m in self.mena and any(g not in F for g in self.mena[m])]
            for g in self.glob_menia(vid & self.glob):
                if g not in F:
                    nove.append(g.name)
            if not nove:
                break
            rad = nove
        von = list(self.modul_ret)              # reťazce modulu, potom funkcií v poradí zdroja
        for g in sorted(F, key=lambda g: (g.lineno, g.col_offset)):
            von.extend(self.info[g][2])
        return '\n'.join(von)

    def glob_menia(self, gl):
        """Funkcie mimo modulu, ktoré menia niektorú z globálnych premenných gl (príkaz global, priradenie do prvku
        alebo atribútu, meniaca metóda — predpočítané v __init__)."""
        if not gl:
            return []
        return [f for f, i in self.info.items() if i[1] & gl]


@_pamat
def dosah_text(kod):
    try:
        return _Dosah(py_strom(kod)).text()
    except (SyntaxError, ValueError, RecursionError, MemoryError):
        return None


def _siet_cez_spustac(kd, bez, jazyk, nezname=True):
    """Kód vie spustiť reťazec: v pythone sieť len v reťazcoch v dosahu spúšťača (SKG 3); keď dosah rozbor nevie, celý
    text ako doteraz (to isté v iných jazykoch)."""
    if not KOD_RETAZEC_SPUSTA.search(bez):
        return False
    if jazyk != 'py':
        return True
    t = dosah_text(kd)
    if t is None or KOD_SIET.search(t):
        return True
    if not KOD_NACITA_HRUBO.search(t):
        return False
    try:                                # modul načítaný menom v reťazci v dosahu: meno posúdi KDI ako pri kóde
        return bool(kod_nacita_siet(t, kod_bez_retazcov(t, 'py'), 'py', nezname))
    except (RecursionError, ValueError, IndexError, KeyError, TypeError, re.error):
        return True


def kod_posta_siet(kd, jazyk, nezname=True):
    """Sieť v kóde pre pár „poštové volanie + sieť“ (K_POSTA): značka siete musí byť kód, nie text v reťazci — katalóg
    skúšobných príkazov ani návod s príkazom odoslanie nie sú. Keď kód vie reťazec spustiť (KOD_RETAZEC_SPUSTA),
    posudzuje sa celý text ako doteraz; keď sa text nedá prejsť, platí prísnejšie (ako doteraz). Modul siete načítaný
    menom v reťazci (KDI, kod_nacita_siet) je sieť; nezname=False (skript z disku) berie meno, ktoré rozbor nevidí,
    ako neutrálne (SKRIPT_NEZNAME), v kóde príkazu je to sieť."""
    # SKG 3 (b5, 7. 11.): najprv lacné hľadanie načítania menom (ng-cervene-testy.py s celým textom: 2 ms, nájde
    # `require` na začiatku), potom značka siete nad celým textom (45 ms, nenájde nič) — oba čisté, výsledok rovnaký
    if not KOD_NACITA_HRUBO.search(kd) and not KOD_SIET.search(kd):
        return False
    try:
        bez = kod_bez_retazcov(kd, jazyk)
        return bool(KOD_SIET.search(bez) or _siet_cez_spustac(kd, bez, jazyk, nezname) or kod_nacita_siet(kd, bez, jazyk, nezname))
    except (RecursionError, ValueError, IndexError, KeyError, TypeError, re.error):
        return True


# KDI (16. 10. 2026, z kusu KXK): modul siete načítaný za behu menom v reťazci — `__import__("smtplib")`,
# `importlib.import_module("smtplib")`, v node `require("nodemailer")`, `import("nodemailer")` a `import m from
# "nodemailer"` — po vyprázdnení reťazcov meno modulu zmizlo, takže pár „poštové volanie + sieť“ ho nevidel (nasucho
# 16. 10.: `python3 -c 's = __import__("smtplib").SMTP(…); s.sendmail(…)'` a `node -e 'const nm = require("nodemailer");
# nm.createTransport(…).sendMail(…)'` prešli, `import smtplib; …` zastavil). Načítanie menom je to isté ako import: meno
# v reťazci posúdi KOD_SIET. Meno, ktoré rozbor nevidí (premenná, spájanie, f-reťazec, getattr(…, '__import__')), je
# v kóde príkazu nedočítané a ráta sa ako sieť; v skripte z disku je neutrálne. Volanie musí byť kód, nie text v reťazci
# (katalóg skúšok ostáva údajom); platí aj pre funkciu pod iným menom (`from importlib import import_module as im`,
# `r = require`). Iné jazyky sa nemenia.
KOD_NACITA_MENA = {'py': ('__import__', 'import_module'), 'js': ('require', 'import')}
KOD_NACITA_HRUBO = re.compile(r'__import__|import_module|\brequire\b|\bimport\s*\(|\bfrom\s*[\'"`]|\bimport\s*[\'"`]')
KOD_NACITA_GETATTR = re.compile(r'''getattr\s*\([^()]*['"](?:__import__|import_module)['"]''')


def kod_nacita_siet(kd, bez, jazyk, nezname=True):
    """Modul siete načítaný menom v reťazci (KDI vyššie). bez = kd s vyprázdnenými reťazcami (kod_bez_retazcov — rovnaká
    dĺžka, takže poloha mena v bez ukáže, či je volanie kód)."""
    zakl = KOD_NACITA_MENA.get(jazyk)
    if not zakl:
        return False
    if jazyk == 'py' and not any(z in bez for z in zakl):
        # SKG 3 (b4, 7. 11.): v kóde nie je ani jedno meno načítania — prezývka aj volanie nižšie ho potrebujú, ostáva
        # len getattr(…, '__import__'); predtým vzor prezývok prešiel celý text (ng-cervene-testy.py 0,07 s pri každom
        # posúdení)
        return bool(nezname and any(bez.startswith('getattr', m.start()) for m in KOD_NACITA_GETATTR.finditer(kd)))
    mena = set(zakl)
    alt = '|'.join(map(re.escape, zakl))
    for m in re.finditer(r'\b(?:' + alt + r')\s+as\s+([A-Za-z_]\w*)'
                         r'|(?<![\w$.])([A-Za-z_$][\w$]*)\s*=\s*(?:[\w$]+\s*\.\s*)*(?:' + alt + r')\s*(?=$|[;\n),\]}])',
                         bez):
        mena.add(m.group(1) or m.group(2))
    if nezname and jazyk == 'py' and any(bez.startswith('getattr', m.start()) for m in KOD_NACITA_GETATTR.finditer(kd)):
        return True
    vzor = r'(?<![\w$])(' + '|'.join(map(re.escape, sorted(mena, key=len, reverse=True))) + r')\s*\('
    if jazyk == 'js':
        vzor += r'|(?<![\w$.])(from|import)\b'
    n = len(kd)
    for m in re.finditer(vzor, bez):
        esm = jazyk == 'js' and m.group(2) is not None     # import m from "x" / import "x" (len js)
        i = m.end()
        while i < n and kd[i] in ' \t\r\n':
            i += 1
        if jazyk == 'py':
            k = re.match(r'name\s*=\s*', kd[i:])
            if k:
                i += k.end()
        pred = ''
        if jazyk == 'py':
            k = re.match(r'[rRuUbBfF]{1,2}(?=[\'"])', kd[i:])
            if k and k.group().lower() in KOD_PREDPONY:
                pred = k.group().lower()
                i += k.end()
        hodnota = None
        if i < n and (kd[i] in '\'"' or (kd[i] == '`' and jazyk == 'js')):
            j, casti = kod_retazec(kd, i, pred, jazyk)
            while j < n and kd[j] in ' \t\r\n':
                j += 1
            if all(c[0] == 'l' for c in casti) and (esm or j >= n or kd[j] in ',);'):
                hodnota = ''.join(c[1] for c in casti)
        if hodnota is None:
            if esm:
                continue                      # `import` bez reťazca za ním (import x from …) — meno modulu je kód
            if nezname:
                return True
            continue
        if KOD_SIET.search(hodnota):
            return True
    return False


def kod_priecinok(s):
    """Neznáma hodnota, za ktorou pokračuje cesta (S + '/recount.py', os.path.join(ROOT, 'x.py')), je priečinok, nie program —
    o programe rozhoduje doslovné meno na konci (24. 9.: syn_test.py spúšťal recount.py cez cestu od __file__)."""
    return s.replace(NAJHORSIE + '/', '/nezname/')


def kotva_quote(s):
    return "'" + s.replace("'", "'\"'\"'") + "'" if s else "''"


def kod_kroky(kd, jazyk, nastroj_meno):
    """Volania v kóde interpretra, ktoré môžu znamenať odoslanie pošty — kroky v poradí, v akom ich posudzuje
    Rozbor.kod_spusta_postu. Závisí len od textu kódu (K88N, 11. 10.), nie od miesta príkazu:
      ('nacita', cesty, meno volania) — načítanie skriptu ako knižnice / vykonanie kódu s poštovým skriptom
      ('posiela',) — poštový nástroj podľa mena načítaný ako knižnica a pokyn odoslať v kóde
      ('prikaz', text) — príkaz, ktorý kód spúšťa a ktorý menuje poštový program alebo nástroj
    Generátor: ďalší krok sa počíta až vtedy, keď ten predošlý odoslanie nepotvrdil (ako pred K88N)."""
    kr = KodRozbor(kd, jazyk)
    for druh, meno, z, kon in kr.volania():
        poz, kl = kr.argumenty(z, kon, 0)
        if 'args' in kl:
            poz = [kl['args']] + poz
        posledne = meno.split('.')[-1]
        if druh == 'nacita' or posledne == 'eval' or (jazyk == 'py' and meno == 'exec'):
            # načítanie skriptu ako knižnice / vykonanie kódu: poštový skript v ňom = odoslanie (PS 17. 9. exo-rest)
            hity = [x for x in (kod_priecinok(kr.text(h)) for p in poz for h in p) if ZNACKY_PROG.search(x)]
            if hity:
                yield ('nacita', tuple(hity), posledne)
                continue
            # poštový nástroj podľa mena (graph_mail.py) načítaný ako knižnica a pokyn odoslať v kóde (29. 9.)
            if nastroj_meno and posta_pokyn(kd) and any(POSTA_NASTROJ_KOD.search(kod_priecinok(kr.text(h))) for p in poz for h in p):
                yield ('posiela',)
            continue
        for txt in kr.prikazy_volania(poz, kl.get('input')):
            if ZNACKY_PROG.search(txt) or (nastroj_meno and POSTA_NASTROJ_KOD.search(txt)):
                yield ('prikaz', txt)


class KodRozbor:
    """Hodnoty argumentov volaní v kóde interpretra — len toľko, koľko treba na posúdenie pošty. Hodnota je zoznam
    možností: ('s', text) alebo ('a', [slová]). Čo sa prečítať nedá, je NAJHORSIE (poštový program)."""
    MAX_ALT = 8

    def __init__(self, kod, jazyk, zname=None):
        self.jazyk = jazyk
        self.zname = zname or KOD_ZNAME   # 2. 10.: v rozbore skriptu z disku je __file__ jeho skutočná cesta
        self.t = kod_tokeny(kod, jazyk)
        self.par = {}
        self.parovanie(0)
        self.prir = {}
        self.aliasy_modulu = set()
        self.hole = set()
        self.bezi = set()
        self.vlozene = {}
        self.memo = {}
        self.cwd_hodnota = None   # KMP (15. 10.): os.getcwd() / Path.cwd() — len pri čítaní sys.path (py_sys_path)
        self.domov_hodnota = None  # KMP (1c, 16. 10.): Path.home() — len pri čítaní sys.path
        self.priradenia()

    def parovanie(self, od):
        z = []
        for k in range(od, len(self.t)):
            d, v = self.t[k]
            if d == 'p' and v in ('(', '[', '{'):
                z.append(k)
            elif d == 'p' and v in (')', ']', '}') and z:
                self.par[z.pop()] = k

    def zavreta(self, k, kon):
        return min(self.par.get(k, kon - 1), kon - 1) if kon > k else k

    def priradenia(self):
        t = self.t
        n = len(t)
        dekl = {}     # K88J: pozícia mena deklarovaného za const/let/var → koniec jeho hodnoty
        for k in range(n):
            d, v = t[k]
            # import subprocess as sp · from subprocess import run, Popen as P · cp = require('child_process')
            if d == 'n' and v == 'import' and k + 3 < n and t[k + 1][0] == 'n' and t[k + 1][1] in KOD_MODULY \
                    and t[k + 2] == ('n', 'as') and t[k + 3][0] == 'n':
                self.aliasy_modulu.add(t[k + 3][1])
            if d == 'n' and v == 'from' and k + 2 < n and t[k + 1][0] == 'n' and t[k + 1][1] in KOD_MODULY \
                    and t[k + 2] == ('n', 'import'):
                m = k + 3
                while m < n and t[m][0] != 'nl' and t[m] != ('p', ';'):
                    if t[m][0] == 'n' and t[m][1] != 'as':
                        self.hole.add(t[m][1])
                    m += 1
            if d == 'n' and v == 'require' and k + 2 < n and t[k + 1] == ('p', '(') and t[k + 2][0] == 's' \
                    and 'child_process' in self.retazec(t[k + 2][1]) and k >= 2 and t[k - 1] == ('p', '='):
                if t[k - 2][0] == 'n':
                    self.aliasy_modulu.add(t[k - 2][1])
                elif t[k - 2] == ('p', '}'):
                    m = k - 3
                    while m >= 0 and t[m] != ('p', '{'):
                        if t[m][0] == 'n':
                            self.hole.add(t[m][1])
                        m -= 1
            # K88J (11. 10.): const A = …, B = … (aj let, var) — premenná je aj meno za čiarkou na vrchnej úrovni
            # a hodnota každej sa končí na ďalšej takej čiarke
            if d == 'n' and v in ('const', 'let', 'var') and self.jazyk == 'js':
                dekl.update(self.deklaracia(k))
            # priradenie MENO = výraz (nie ==, nie kľúčový argument volania, nie kľúč slovníka)
            if d == 'n' and k + 1 < n and t[k + 1][0] == 'p' and t[k + 1][1] in ('=', '+=', ':=') and \
                    (k == 0 or t[k - 1][0] == 'nl' or t[k - 1] in (('p', ';'), ('p', '{'), ('p', '}'), ('p', ':'))
                     or (t[k - 1][0] == 'n' and t[k - 1][1] in ('const', 'let', 'var', 'my', 'our', 'local', 'global'))
                     or k in dekl):
                kon = dekl[k] if k in dekl else self.koniec_prikazu(k + 2)
                if kon > k + 2:
                    self.prir.setdefault(v, []).append((k + 2, kon, '+' if t[k + 1][1] == '+=' else '='))
            # for MENO in <výraz>: · for (const MENO of <výraz>) — premenná cyklu má hodnoty prvkov
            if d == 'n' and v == 'for':
                m = k + 1
                if m < n and t[m] == ('p', '('):
                    m += 1
                if m < n and t[m][0] == 'n' and t[m][1] in ('const', 'let', 'var', 'my'):
                    m += 1
                if m + 1 < n and t[m][0] == 'n' and t[m + 1][0] == 'n' and t[m + 1][1] in ('in', 'of'):
                    z = kon = m + 2
                    while kon < n and t[kon][0] != 'nl' and t[kon] not in (('p', ':'), ('p', ')'), ('p', ']'), ('n', 'if')):
                        kon = self.par.get(kon, kon) + 1 if t[kon][0] == 'p' and t[kon][1] in '([{' else kon + 1
                    if kon > z:
                        self.prir.setdefault(t[m][1], []).append((z, kon, 'cyklus'))

    def koniec_prikazu(self, z):
        t = self.t
        k = z
        while k < len(t):
            d, v = t[k]
            if d == 'nl' or (d == 'p' and v in (';', ')', ']', '}')):
                return k
            if d == 'p' and v in ('(', '[', '{'):
                k = self.par.get(k, len(t) - 1) + 1
                continue
            k += 1
        return k

    def deklaracia(self, k):
        """K88J (11. 10.): mená deklarované za const/let/var na tokene k → {pozícia mena: koniec jeho hodnoty}.
        Deklarácie delí čiarka na vrchnej úrovni (mimo zátvoriek); riadok, ktorý sa čiarkou končí, pokračuje na ďalšom.
        Predtým sa čítalo len prvé meno a jeho hodnota siahala po koniec príkazu: adresa v druhej premennej
        (`const T = process.env.X, B = "https://…"`) bola neznáma a zápis na ňu prešiel bez otázky."""
        t = self.t
        n = len(t)
        von = {}
        z = k + 1
        while z < n:
            kon = z
            while kon < n:
                d, v = t[kon]
                if d == 'nl' or (d == 'p' and v in (',', ';', ')', ']', '}')):
                    break
                kon = self.par.get(kon, n - 1) + 1 if d == 'p' and v in ('(', '[', '{') else kon + 1
            if t[z][0] == 'n' and z + 2 < kon and t[z + 1] == ('p', '='):
                von[z] = kon
            if kon >= n or t[kon] != ('p', ','):
                break
            z = kon + 1
            while z < n and t[z][0] == 'nl':
                z += 1
        return von

    @staticmethod
    def retazec(casti):
        return ''.join(x for d, x in casti if d == 'l')

    @staticmethod
    def text(h):
        return h[1] if h[0] == 's' else ' '.join(h[1])

    def spoj(self, a, b):
        if a[0] == 'a' and b[0] == 'a':
            return ('a', a[1] + b[1])
        return ('s', self.text(a) + self.text(b))

    def hodnota_mena(self, meno, hl):
        if meno in self.zname and meno not in self.prir:
            return [('s', self.zname[meno])]
        if meno in self.memo:
            return self.memo[meno]
        prir = self.prir.get(meno)
        if not prir or meno in self.bezi or hl > 10:
            return [('s', NAJHORSIE)]
        self.bezi.add(meno)
        try:
            von = []
            zaklad = []
            for z, kon, druh in prir[-4:]:
                h = self.vyraz(z, kon, hl + 1)
                if druh == 'cyklus':
                    for x in h:
                        von += [('s', y) for y in x[1]] if x[0] == 'a' else [x]
                elif druh == '+' and zaklad:
                    zaklad = [self.spoj(a, b) for a in zaklad for b in h][:self.MAX_ALT]
                    von += zaklad
                else:
                    zaklad = h
                    von += h
            self.memo[meno] = von[-self.MAX_ALT:] or [('s', NAJHORSIE)]
            return self.memo[meno]
        finally:
            self.bezi.discard(meno)

    def delenie(self, z, kon, oddelovac=','):
        """Rozsahy tokenov na najvyššej úrovni oddelené čiarkou."""
        casti = []
        k = zac = z
        while k < kon:
            d, v = self.t[k]
            if d == 'p' and v in ('(', '[', '{'):
                k = self.zavreta(k, kon) + 1
                continue
            if d == 'p' and v == oddelovac:
                casti.append((zac, k))
                zac = k + 1
            k += 1
        if zac < kon:
            casti.append((zac, kon))
        return casti

    def na_urovni(self, z, kon, ciel, od=None):
        """Prvý token z `ciel` na najvyššej úrovni v tokens[od or z:kon] (index alebo None)."""
        k = z if od is None else od
        while k < kon:
            d, v = self.t[k]
            if d == 'p' and v in ('(', '[', '{'):
                k = self.zavreta(k, kon) + 1
                continue
            if d in ('n', 'p') and (d, v) in ciel:
                return k
            k += 1
        return None

    def vyraz(self, z, kon, hl=0):
        """Hodnota výrazu tokens[z:kon]: spájanie +, formát %, pri vetvení (a if c else b, c ? a : b, a or b) obe vetvy."""
        if hl > 14 or z >= kon:
            return [('s', '')]
        k = self.na_urovni(z, kon, {('n', 'if'), ('p', '?'), ('n', 'or'), ('p', '||'), ('p', '??')})
        if k is not None and k > z:
            d, v = self.t[k]
            if v == 'if':
                e = self.na_urovni(z, kon, {('n', 'else')}, k + 1)
                if e is not None:
                    return (self.vyraz(z, k, hl + 1) + self.vyraz(e + 1, kon, hl + 1))[:self.MAX_ALT]
            elif v == '?':
                e = self.na_urovni(z, kon, {('p', ':')}, k + 1)
                if e is not None:
                    return (self.vyraz(k + 1, e, hl + 1) + self.vyraz(e + 1, kon, hl + 1))[:self.MAX_ALT]
            else:
                return (self.vyraz(z, k, hl + 1) + self.vyraz(k + 1, kon, hl + 1))[:self.MAX_ALT]
        operandy = []
        ops = []
        k = zac = z
        while k < kon:
            d, v = self.t[k]
            if d == 'p' and v in ('(', '[', '{'):
                k = self.zavreta(k, kon) + 1
                continue
            if d == 'p' and k > zac and (v in ('+', '%') or (v == '.' and self.jazyk in ('php', 'pl'))
                                         or (v == '/' and self.jazyk == 'py')):   # Path(a) / 'b'
                operandy.append((zac, k))
                ops.append(v)
                zac = k + 1
            elif d == 'n' and v in ('for', 'if', 'else', 'and', 'in', 'not') and k > zac:
                break                                # koniec výrazu (zložený zoznam, podmienka)
            k += 1
        operandy.append((zac, k))
        vys = self.operand(*operandy[0], hl)
        for op, (a, b) in zip(ops, operandy[1:]):
            h = self.operand(a, b, hl)
            vys = [self.percento(x, y) if op == '%' else (('s', self.text(x) + '/' + self.text(y)) if op == '/'
                                                           else self.spoj(x, y)) for x in vys for y in h][:self.MAX_ALT]
        return vys

    def percento(self, fmt, arg):
        s = self.text(fmt)
        for x in (arg[1] if arg[0] == 'a' else [arg[1]]):
            s = re.sub(r'%[-#0 +]*\d*(?:\.\d+)?[sdrfi]', lambda _m, x=x: x, s, count=1)
        return ('s', s)

    def zoznam(self, z, kz, hl):
        vys = [('a', [])]
        for a, b in self.delenie(z, kz):
            prvky = self.vyraz(a, b, hl + 1)
            vys = [('a', x[1] + (y[1] if y[0] == 'a' else [y[1]])) for x in vys for y in prvky][:self.MAX_ALT]
        return vys

    def operand(self, z, kon, hl):
        t = self.t
        while z < kon and (t[z] in (('p', '*'), ('p', '**'), ('p', '.'), ('p', '!'), ('p', '-'))
                           or t[z] in (('n', 'await'), ('n', 'new'), ('n', 'not'), ('n', 'typeof'), ('n', 'yield'),
                                       ('n', 'return'))):
            z += 1
        if z >= kon:
            return [('s', '')]
        d, v = t[z]
        k = z + 1
        if d == 's':
            vys = [('s', self.casti(v, hl))]
        elif d == 'c':
            vys = [('s', v)]
        elif d == 'p' and v == '[':
            kz = self.zavreta(z, kon)
            vys = self.zoznam(z + 1, kz, hl)
            k = kz + 1
        elif d == 'p' and v == '(':
            kz = self.zavreta(z, kon)
            vys = self.zoznam(z + 1, kz, hl) if len(self.delenie(z + 1, kz)) > 1 else self.vyraz(z + 1, kz, hl + 1)
            k = kz + 1
        elif d == 'p' and v == '{':
            vys = [('s', '')]
            k = self.zavreta(z, kon) + 1
        elif d == 'n':
            if k < kon and t[k] == ('p', '('):
                kz = self.zavreta(k, kon)
                vys = self.volanie_funkcie(v, k + 1, kz, hl)
                k = kz + 1
            elif v in ('True', 'False', 'None', 'null', 'undefined', 'true', 'false', 'nil'):
                vys = [('s', '')]
            elif v in self.prir or '.' not in v or v in self.zname:
                vys = self.hodnota_mena(v, hl)
            elif self.cesta_atribut(v):
                # KMP (1b, 16. 10.): HERE.parents[1] / "scripts" (ion.py) — priečinok nad premennou s cestou; predtým
                # neznáme, takže sys.path.insert(0, str(HERE.parents[1] / "scripts")) robil modul nedočítaným
                zac, zvysok = self.cesta_atribut(v)
                vys = self.hodnota_mena(zac, hl)
                for c in zvysok:
                    vys = self.metoda(vys, c, None, hl)
            else:
                vys = [('s', NAJHORSIE)]         # atribút objektu (args.cmd, os.environ, sys.executable) — neznáme
        else:
            vys = [('s', '')]
        while k < kon:                            # prípony: .metóda(…) · [index] · (…)
            d2, v2 = t[k]
            if d2 == 'p' and v2 == '.' and k + 1 < kon and t[k + 1][0] == 'n':
                casti = t[k + 1][1].split('.')
                k += 2
                arg = None
                if k < kon and t[k] == ('p', '('):
                    kz = self.zavreta(k, kon)
                    arg = (k + 1, kz)
                    k = kz + 1
                for c in casti[:-1]:
                    vys = self.metoda(vys, c, None, hl)
                vys = self.metoda(vys, casti[-1], arg, hl)
                continue
            if d2 == 'p' and v2 == '[':
                kz = self.zavreta(k, kon)
                vnutri = t[k + 1:kz]
                if ('p', ':') in vnutri:
                    pass                                      # rez zoznamu: zoznam ostáva
                elif len(vnutri) == 1 and vnutri[0][0] == 'c' and vnutri[0][1].isdigit():
                    j = int(vnutri[0][1])
                    vys = [('s', x[1][j]) if x[0] == 'a' and j < len(x[1]) else ('s', NAJHORSIE) for x in vys]
                else:
                    vys = [('s', NAJHORSIE)]
                k = kz + 1
                continue
            if d2 == 'p' and v2 == '(':
                k = self.zavreta(k, kon) + 1
                vys = [('s', NAJHORSIE)]
                continue
            break
        return vys

    def cesta_atribut(self, v):
        """KMP (1b): `X.parent`, `X.parent.parent`, `X.parents` pri priradenej premennej X → (X, [atribúty]); inak None.
        Len atribúty cesty — iný atribút premennej (cfg.cmd, o.url) ostáva neznámy (hodnota slovníka by bola prázdna)."""
        casti = v.split('.')
        for j in range(len(casti) - 1, 0, -1):
            zac = '.'.join(casti[:j])
            if zac in self.prir or zac in self.zname:
                zv = casti[j:]
                if all(c == 'parent' for c in zv[:-1]) and zv[-1] in ('parent', 'parents'):
                    return zac, zv
                return None
        return None

    def casti(self, casti, hl):
        """Reťazec s vloženými výrazmi (f-reťazec, `${…}`, "$x"): výraz sa pridá na koniec tokenov a vyhodnotí."""
        o = []
        for d, x in casti:
            if d == 'l':
                o.append(x)
                continue
            if x not in self.vlozene:
                od = len(self.t)
                self.t.extend(kod_tokeny(x, self.jazyk))
                self.parovanie(od)
                self.vlozene[x] = (od, len(self.t))
            a, b = self.vlozene[x]
            o.append(self.text(self.vyraz(a, b, hl + 1)[0]) if b > a else '')
        return ''.join(o)

    def argumenty(self, z, kon, hl):
        """(pozičné hodnoty, kľúčové {meno: hodnoty}); objekt {…} (voľby v node) sa vynechá."""
        poz = []
        kl = {}
        for a, b in self.delenie(z, kon):
            if b - a >= 2 and self.t[a][0] == 'n' and self.t[a + 1] == ('p', '='):
                kl[self.t[a][1]] = self.vyraz(a + 2, b, hl + 1)
            elif self.t[a] == ('p', '**') or (self.t[a] == ('p', '{') and self.zavreta(a, b) == b - 1):
                continue
            else:
                poz.append(self.vyraz(a, b, hl + 1))
        return poz, kl

    def volanie_funkcie(self, meno, z, kon, hl):
        casti = meno.split('.')
        posledne = casti[-1]
        if len(casti) > 1 and ('.'.join(casti[:-1]) in self.prir):       # N.replace(…) — metóda na premennej
            return self.metoda(self.hodnota_mena('.'.join(casti[:-1]), hl), posledne, (z, kon), hl)
        poz, kl = self.argumenty(z, kon, hl)
        if posledne == 'Request' and (kl.get('url') or poz):
            # K88J (11. 10.): hodnota žiadosti je jej adresa — urlopen(Request(adresa, …)) a fetch(new Request(adresa, …))
            # posúdi vonkajšie volanie podľa tej istej adresy ako vnútorné. Predtým bola neznáma a vonkajšie volanie
            # hlásilo neprečítanú adresu navyše (pri zapnutom pravidle o kóde by zastavilo aj zápis na vlastný server).
            return kl.get('url') or poz[0]
        if posledne == 'URL' and self.jazyk == 'js' and poz:
            # K88L (11. 10.): hodnota `new URL(adresa)` je tá adresa, `new URL(cesta, základ)` je základ s cestou —
            # predtým neznáma hodnota, takže `https.request(new URL("https://…"), {method: 'POST'})` adresu nemalo
            if len(poz) > 1:
                return [('s', self.text(z).rstrip('/') + '/' + self.text(c).lstrip('/'))
                        for z in poz[1] for c in poz[0]][:self.MAX_ALT]
            return poz[0]
        if posledne in ('join', 'resolve') and casti[0] in ('os', 'path', 'posixpath', 'ntpath'):
            vys = [('s', '')]
            for p in poz:
                vys = [('s', (self.text(x).rstrip('/') + '/' + self.text(y)) if self.text(x) else self.text(y))
                       for x in vys for y in p][:self.MAX_ALT]
            return vys
        if self.cwd_hodnota is not None and not poz and not kl and (meno in ('os.getcwd', 'getcwd', 'posix.getcwd')
                                                                     or meno.endswith('Path.cwd')):
            return [('s', self.cwd_hodnota)]
        if self.cwd_hodnota is not None and not poz and not kl and meno.endswith('Path.home'):
            return [('s', self.domov_hodnota or NAJHORSIE)]    # KMP (1c): Path.home() / ".config" pri čítaní sys.path
        if posledne == 'dirname' and casti[0] in ('os', 'path', 'posixpath') and poz:
            # 2. 10.: os.path.dirname(__file__) — priečinok skriptu; neznáma hodnota ostáva neznáma (najhorší prípad)
            return [('s', NAJHORSIE if NAJHORSIE in self.text(x) else os.path.dirname(self.text(x))) for x in poz[0]]
        if meno == 'shlex.join' and poz:
            return [('s', ' '.join(kotva_quote(y) for y in (x[1] if x[0] == 'a' else [x[1]]))) for x in poz[0]]
        if meno == 'shlex.split' and poz:
            return [('a', self.text(x).split()) for x in poz[0]]
        if posledne in ('str', 'repr', 'String', 'Path', 'PurePath', 'fspath', 'abspath', 'realpath', 'expanduser',
                        'normpath', 'quote', 'list', 'tuple', 'open', 'dedent', 'bytes', 'Buffer', 'from', 'sorted',
                        'reversed') and poz:
            return poz[0]
        return [('s', NAJHORSIE)]

    def metoda(self, vys, meno, arg, hl):
        poz = self.argumenty(*arg, hl)[0] if arg else []
        if meno == 'join' and poz:
            return [('s', self.text(s).join(y[1] if y[0] == 'a' else [y[1]])) for s in vys for y in poz[0]][:self.MAX_ALT]
        if meno == 'split':
            return [('a', self.text(x).split()) for x in vys]
        if meno == 'format':
            nove = []
            for x in vys:
                s = self.text(x)
                for p in poz:
                    s = re.sub(r'\{[^{}]*\}', lambda _m, p=p: self.text(p[0]), s, count=1)
                nove.append(('s', s))
            return nove
        if meno in ('concat', 'extend', 'append', 'push') and poz:
            return [(('a', x[1] + (y[1] if y[0] == 'a' else [y[1]])) if x[0] == 'a' else self.spoj(x, y))
                    for x in vys for y in poz[0]][:self.MAX_ALT]
        if meno in ('get', 'getenv', 'environ', 'pop', 'input', 'readline'):
            return [('s', NAJHORSIE)]
        if meno == 'parent' and arg is None:     # 2. 10.: Path(__file__).resolve().parent — priečinok skriptu
            return [('s', NAJHORSIE if NAJHORSIE in self.text(x) else os.path.dirname(self.text(x))) for x in vys]
        if meno == 'parents' and arg is None:
            # KMP (1b, 16. 10.): Path(…).parents — zoznam priečinkov nad cestou (index [k] ho číta ako prvok zoznamu);
            # neznáma alebo relatívna cesta ostáva neznáma
            von = []
            for x in vys:
                c = self.text(x)
                if x[0] != 's' or NAJHORSIE in c or not c.startswith('/'):
                    von.append(('s', NAJHORSIE))
                    continue
                pr, d = [], os.path.dirname(c.rstrip('/') or '/')
                while len(pr) < 32:
                    pr.append(d)
                    if d == '/':
                        break
                    d = os.path.dirname(d)
                von.append(('a', pr))
            return von
        return vys           # .replace/.strip/.read/.lower … — hodnota ostáva (meno programu v nej ostane viditeľné)

    def volania(self):
        """(druh 'spusta'|'nacita', meno, začiatok argumentov, koniec) pre každé volanie, ktoré spúšťa proces alebo
        načíta skript ako knižnicu."""
        t = self.t
        for k in range(len(t)):
            d, v = t[k]
            if d != 'n' or k + 1 >= len(t) or t[k + 1] != ('p', '('):
                continue
            casti = v.split('.')
            posledne = casti[-1]
            po_bodke = k > 0 and t[k - 1] == ('p', '.')          # …).run( — metóda na výsledku iného volania
            objekt = casti[-2] if len(casti) > 1 else ('?' if po_bodke else '')
            if posledne in KOD_NACITA:
                druh = 'nacita'
            elif posledne in KOD_SPUSTA_HOLE or (objekt in KOD_MODULY or objekt in self.aliasy_modulu) \
                    and posledne in KOD_SPUSTA_POSLEDNE or (not objekt and (posledne in self.hole
                                                                           or posledne in KOD_SPUSTA_HOLE_BEZ_OBJEKTU)):
                druh = 'spusta'
            else:
                continue
            yield druh, v, k + 2, self.par.get(k + 1, len(t))

    def prikazy_volania(self, poz, vstup):
        """Texty príkazov (shell), ktoré volanie spustí: zoznam = argv, reťazec = príkaz shellu; ďalšie pozičné
        argumenty (execFile(súbor, [args]), spawn(cmd, [args]), os.execv(cesta, argv)) sa pripoja."""
        if not poz:
            return []
        kombinacie = [[]]
        for p in poz[:6]:
            kombinacie = [k + [h] for k in kombinacie for h in p][:self.MAX_ALT]
        texty = []
        for komb in kombinacie:
            prvy = komb[0]
            argv = [] if prvy[0] == 's' else list(prvy[1])
            for h in komb[1:]:
                argv += h[1] if h[0] == 'a' else [h[1]]
            argv = [kod_priecinok(a) for a in argv]
            txt = (kod_priecinok(prvy[1]) + ' ' if prvy[0] == 's' else '') + ' '.join(kotva_quote(a) for a in argv)
            if vstup:
                txt += " <<'KOTVA_VSTUP'\n" + self.text(vstup[0]) + "\nKOTVA_VSTUP"
            texty.append(txt)
        return texty


# ---------- rozbor ----------

class Ctx:
    __slots__ = ('cwd', 'vars', 'sudo', 'vzdial', 'hlbka', 'posta_env', 'kontajner', 'zasobnik', 'posta_exo', 'allexport',
                 'cwd_vzor', 'trap', 'exp')

    def __init__(self, cwd, hlbka=0):
        self.cwd = cwd
        self.vars = {}
        self.sudo = False
        self.vzdial = False
        self.hlbka = hlbka
        self.posta_env = False
        self.kontajner = False   # príkaz vnútri kontajnera (podman/docker exec): súbory a sudo sú kontajnera, nie servera
        self.zasobnik = []       # pushd/popd
        # 2. 10.: mená premenných shellu (neexportovaných) s cestou k exo-rest.py — namiesto pošty nad celým príkazom
        # (posta_env) sa posúdi každé ich použitie (Rozbor.exo_odkaz)
        self.posta_exo = frozenset()
        self.allexport = False   # set -a: každé ďalšie priradenie sa exportuje
        self.cwd_vzor = None     # 2. 10.: priečinok po `cd $p` v cykle cez projekty — vzor (/home/x/projects/*), cwd neznámy
        self.trap = False        # 3. 10. (kolo 3): príkazy obsluhy trap (mazanie presne cesty z mktemp prejde)
        self.exp = frozenset()   # 14. 10. (K88V): exportované mená (export, declare -x, set -a) — dostane ich spustený skript

    def kopia(self, sudo=None):
        c = Ctx(self.cwd, self.hlbka + 1)
        c.vars = dict(self.vars)
        c.sudo = self.sudo if sudo is None else sudo
        c.vzdial = self.vzdial
        c.posta_env = self.posta_env
        c.kontajner = self.kontajner
        c.zasobnik = list(self.zasobnik)
        c.posta_exo = self.posta_exo
        c.allexport = self.allexport
        c.cwd_vzor = self.cwd_vzor
        c.trap = self.trap
        c.exp = self.exp
        return c


_JA_PROCES = []


def ja_proces():
    """Meno používateľa, pod ktorým rozbor beží (passwd podľa efektívneho uid)."""
    if not _JA_PROCES:
        try:
            _JA_PROCES.append(pwd.getpwuid(os.geteuid()).pw_name)
        except (KeyError, OSError):
            _JA_PROCES.append(None)
    return _JA_PROCES[0]


class Rozbor:
    def __init__(self, hook_cwd, domov):
        self.hook_cwd = hook_cwd
        self.domov = (domov or '/home/pstrocka').rstrip('/') or '/'
        self.ja = os.path.basename(self.domov)
        self.kanaly = {}
        self.priznaky = set()
        self.subory = {}     # cesta → obsah súborov zapísaných heredocom v tomto príkaze (cat > f <<EOF)
        # 3. 10. (kolo 4): súbory, do ktorých príkaz zapisuje (sed -i, presmerovanie, cp …) — cesta → zdroje obsahu (cp, mv,
        # ln, install), vzory ciest s neznámym úsekom, priečinky (cp -r) a „hocičo“ (patch bez súboru, git pull …)
        self.zmenene = {'cesty': {}, 'vzory': [], 'predpony': [], 'vsetko': False}
        self.z_disku = False  # posledný obsah skriptu je z disku (dočasný skript v /tmp), nie z tohto príkazu
        self.len_program = False  # rozbor skriptu z disku: pri pošte rozhoduje program, nie slová za ním
        self._docasne = []        # priradenia pred príkazom (X=… príkaz) — po príkaze sa vrátia (24. 9.)
        self._uid = None
        self.mkt_rezim = marketing_rezim()   # prepínač MARKETING (otázka PS 4.40)
        self.cudzia_rezim = cudzia_rezim()   # prepínač CUDZIA (PS 4.88 a)
        self.mkt_tvrde = set()
        self.mkt_zmena = set()
        self.mkt_subory = {}   # súbor zapísaný z heredocu iným nástrojom (sed … > f <<EOF) — len pre marketing
        self.v_nastroji = False   # rozbor príkazu zvnútra nástroja (nastroj_len_cita) — ďalší nástroj sa už nečíta
        self.mod_volania = ()     # KMP (1a): metódy, s ktorými načítajúci volá funkcie tohto modulu (py_volania_modulu)
        self.iba_nastroje = 0     # hĺbka rozboru vnoreného nástroja (nastroj_ostro): ráta sa len nástroj spustený ostro
        # 2. 10.: premenná s cestou k exo-rest.py sa použila inak než na priame spustenie nástroja (exo_odkaz) → rozober()
        # posúdi celý príkaz znova ako pred 2. 10. (exo_po_starom: taká premenná = pošta nad celým zvyškom príkazu)
        self.exo_unik = False
        self.exo_po_starom = False
        # overenie 2. 10.: kód, ktorý spúšťa nástroj, mení prostredie interpretra (KOD_PROSTREDIE) — výnimka pre čítanie
        # Exchange (exo_volanie) neplatí, exo-rest.py je pošta ako pred 2. 10.
        self.exo_vypnute = False
        self.skript_hlbka = 0     # 2. 10. (úloha K): rozbor skriptu, ktorý príkaz spúšťa — úroveň (0 = príkaz partnera)
        self.sudo_skript = False  # 20. 10.: skript čítaný pod sudo — obsah vidíme ako PS, bežať môže pod iným menom
        self.trapy = []           # 3. 10. (kolo 3): (text obsluhy trap, kontext) — posúdi sa znova na konci (prikazy)
        self._prikazy_ctx = {}    # id(kontext) → počet rozpracovaných zoznamov príkazov s ním (koniec = najvonkajší)
        self._ctx_rury = None
        self._nameref = {}        # 3. 10. (kolo 4): declare -n R=W — priradenie do R mení W
        self._rura_pred = {}      # 3. 10. (kolo 4): id(príkaz) → (príkaz, predchodca v rúre) — cat a.sh | sed … | bash
        self.skript_cesta = None  # cesta skriptu, ktorého obsah sa práve rozoberá
        self._xargs_sonda = False  # 16. 10. (KXA): beží sonda so značkou XARGS_VSTUP (xargs_dosad)
        self._xargs_trafene = False  # značka v sonde došla na miesto skriptu
        self.skript_nalezy = {}   # kanál zo skriptu spusteného zvnútra skriptu: priorita → (text, meno skriptu)
        # spoločné pre celý príkaz (cykly, strop): videné vyvolania, rozbory, bajty, začiatok (čas), čítania súborov
        # podľa cesty (rozbor závisí od miesta, nielen od obsahu), výsledky skriptov bez čítania podľa obsahu
        self.skript_stav = {'videne': set(), 'subory': 0, 'bajty': 0, 'zaciatok': time.monotonic(), 'citania': 0,
                            'obsah': {}, 'zaciatok_cpu': time.process_time()}

    def kanal(self, prio, text):
        if self.iba_nastroje:
            return
        self.kanaly[prio] = text

    # --- marketingové služby (prepínač MARKETING, otázka PS 4.40) ---
    def mkt_nalez(self, sluzby, tvrde):
        if not sluzby or (not tvrde and self.mkt_rezim != 'vsetko'):
            return
        (self.mkt_tvrde if tvrde else self.mkt_zmena).update(sluzby)
        if self.mkt_tvrde:
            self.kanal(P_MARKETING, K_MKT_TVRDE.format(mkt_zoznam(self.mkt_tvrde)))
        else:
            self.kanal(P_MARKETING, K_MKT_ZMENA.format(mkt_zoznam(self.mkt_zmena)))

    def mkt_http(self, met, adresy, telo=(), telo_nezname=False):
        """Volanie HTTP do marketingovej služby. Chyba rozboru pri adrese služby = zastaviť (nie pustiť, nie spadnúť)."""
        if self.mkt_rezim == 'vypnute':
            return
        try:
            v = mkt_posud(met, adresy, telo, telo_nezname)
        except Exception:
            v = (mkt_sluzby([a for a in adresy if isinstance(a, str)]), True)
        if v:
            self.mkt_nalez(*v)

    def mkt_kody(self, kody):
        if self.mkt_rezim == 'vypnute':
            return
        for kd in kody:
            if not kd:
                continue
            try:
                v = mkt_kod(kd)
            except Exception:
                v = ([m for m, r in MKT_SLUZBY if r.search(kd)], True)
            if v:
                self.mkt_nalez(*v)

    def mkt_slovo(self, w, ctx):
        """Rozvinuté texty slova (neznáma časť = NEZNAME) a či je celé neznáme."""
        k = [c for c, _ in self.rozvin(w, ctx)]
        return k, all(not c.replace(NEZNAME, '').strip() for c in k)

    def mkt_subor(self, p, ctx):
        """Obsah súboru s telom (-d @subor): zapísaný v tomto príkaze alebo v /tmp; inak None (kotva ho nevie prečítať)."""
        if not p or NEZNAME in p:
            return None
        p = self.absolutna(p, ctx)
        if not p:
            return None
        if p in self.subory:
            return self.subory[p]
        if p.startswith('/tmp/') and not ctx.vzdial:
            try:
                if os.path.isfile(p) and os.path.getsize(p) <= 512 * 1024:
                    with open(p, encoding='utf-8', errors='replace') as f:
                        return f.read()
            except OSError:
                return None
        return None

    def mkt_telo(self, polozky, args, ctx, cmd):
        """Texty tela z (index slova, dĺžka predpony voľby, môže byť @súbor) → (texty, nevie prečítať)."""
        telo, nezname = [], False
        for idx, pred, subor in polozky:
            if idx >= len(args):
                continue
            k, _ = self.mkt_slovo(args[idx], ctx)
            for c in k:
                v = c[pred:]
                if subor == 'cesta':
                    obs = self.mkt_subor(v, ctx)
                    if obs is None:
                        nezname = True
                    else:
                        telo.append(obs)
                    continue
                if subor and v.startswith('@'):
                    if v == '@-':
                        vstup = [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr] if cmd is not None else []
                        telo += vstup
                        nezname |= not vstup
                        continue
                    obs = self.mkt_subor(v[1:], ctx)
                    if obs is None:
                        nezname = True
                    else:
                        telo.append(obs)
                    continue
                if not v.replace(NEZNAME, '').strip():
                    nezname = True
                else:
                    telo.append(v)
        return telo, nezname

    def mkt_interpret(self, pb, args, t, cmd, ctx):
        """Kód interpretra, ktorý príkaz spúšťa: heredoc, reťazec za -c/-e, skript zapísaný v tomto príkaze alebo v /tmp."""
        if pb in ('node', 'nodejs') and any(x in ('-c', '--check') for x in t):
            return      # node --check: len kontrola zápisu, nič nespustí (overenie 25. 9.: zastavilo by skript z /tmp)
        kod = [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr]
        skript = None
        k = 0
        while k < len(t):
            x = t[k]
            if x in ('-c', '-e', '-r', '--eval', '-p', '--print', '-Command', '-command') and k + 1 < len(t):
                kod.append(t[k + 1])
                break
            if x in ('-m', '-W', '-X', '--require'):
                if x == '-m':
                    break
                k += 2
                continue
            if x.startswith('-'):
                k += 1
                continue
            skript = k
            break
        if skript is not None:
            obs = None
            for c in [c for c, u in self.rozvin(args[skript], ctx) if u is None][:1]:
                obs = self.mkt_subory.get(self.absolutna(c, ctx) or '')
            if obs is None:
                obs = self.obsah_skriptu(args[skript], ctx)
            if obs is not None:
                kod.append(obs)
        self.mkt_kody(kod)

    def vysledok(self):
        """Kanál s najvyššou prednosťou. 29. 9. (nález nezávislého overenia): nasadenie do ostrej prevádzky (4.5) prebíjalo
        poštu, zápis na GitHub, push v cudzom repozitári a push --force, a pri nasadení platí výnimka „skoršia veta
        človeka“ (ng-kotva-over.py) — `gh workflow run … target=production; graph_mail.py send …  # potvrdené: (a)`
        tak poslal mail bez otázky. Keď je v príkaze okrem nasadenia aj iný kanál, text kanála ho menuje („… a zároveň
        …“): človek v zastavení vidí oba a výnimka pre samotné nasadenie neplatí (overenie porovnáva text doslova)."""
        if not self.kanaly:
            return ''
        hore = max(self.kanaly)
        k = self.kanaly[hore]
        ine = [p for p in self.kanaly if p != hore]
        # 19. 10. 2026 (sľub 3.09): druhý kanál sa menuje VŽDY, nie len pri nasadení. Sondy stráže našli 84 príkazov,
        # ktoré stráž zastavila, ale otázka hovorila o inej akcii než o tej, ktorá je na príkaze podstatná — napríklad
        # `sudo -u kolega git push` sa zastavil ako „systémová služba alebo sudo“ a o zásahu do domova iného človeka
        # otázka nehovorila. Človek má v zastavení vidieť všetko, čo sa tým príkazom deje.
        if ine:
            k = ' a zároveň '.join(self.kanaly[x] for x in sorted(self.kanaly, reverse=True))
        if getattr(self, 'sudo_skript', False):
            self.priznaky.add('sudo-skript')   # vetu doplní háčik; do textu kanála nepatrí (rozišla by sa so základom)
        return k

    # --- rozvinutie slov ---
    def hodnota(self, meno, ctx):
        if meno in ctx.vars:
            return ctx.vars[meno]
        if ctx.vzdial:
            return None
        if meno == 'HOME':
            return [self.domov]
        if meno == 'PWD' and ctx.cwd:
            return [ctx.cwd]
        if meno in ('USER', 'LOGNAME'):
            return [self.ja]
        return None

    def rozvin(self, w, ctx, limit=32):
        """Zoznam (text, index prvej neznámej časti alebo None)."""
        kand = [('', None)]
        for k, sg in enumerate(w.seg):
            if sg[0] == 'l':
                t = sg[1]
                if k == 0 and w.tilda and not sg[2] and t.startswith('~'):
                    m = re.match(r'~([a-z_][a-z0-9_.-]*)?(?=/|$)', t)
                    if m:
                        if ctx.vzdial:
                            kand = [(c + NEZNAME, len(c) if u is None else u) for c, u in kand]
                            t = t[m.end():]
                        else:
                            t = (self.domov if not m.group(1) else '/home/' + m.group(1)) + t[m.end():]
                kand = [(c + t, u) for c, u in kand]
            elif sg[0] == 'v':
                hod = self.hodnota(sg[1], ctx)
                if hod is None and w.predvol and k in w.predvol:
                    hod = self.predvol_hod(w.predvol[k], ctx)
                if hod is None:
                    kand = [(c + NEZNAME, len(c) if u is None else u) for c, u in kand]
                else:
                    nove = []
                    for c, u in kand:
                        for h in hod:
                            if u is None and NEZNAME in h:
                                nove.append((c + h, len(c) + h.index(NEZNAME)))
                            else:
                                nove.append((c + h, u))
                    # 2. 10. večer: viac možností než limit — predtým ticho prvých 8 (`for d in a … h /var/x; do rm -rf $d`
                    # deviatu cestu nevidel); teraz pribudne neznáma možnosť (mazanie ju zastaví, skript ju nečíta)
                    kand = (nove[:limit] + [(NEZNAME, 0)] if len(nove) > limit else nove) \
                        or [(c + NEZNAME, len(c) if u is None else u) for c, u in kand]
            else:
                hod = sg[2] or (self.subst(sg[1], ctx) if sg[1] else None)
                if hod and NEZNAME in hod:
                    # 3. 10. (kolo 3): mktemp v priečinku, ktorý kotva nevie prečítať — od neznámej časti je cesta neznáma
                    kand = [(c + hod, len(c) + hod.index(NEZNAME) if u is None else u) for c, u in kand]
                elif hod:
                    kand = [(c + hod, u) for c, u in kand]
                else:
                    kand = [(c + NEZNAME, len(c) if u is None else u) for c, u in kand]
        return kand

    def predvol_hod(self, pv, ctx):
        """${X:-hodnota}, ${X-…}, ${X:=…}, ${X=…}: X známa → jej hodnota (prázdna pri : → predvolená); neznáma → predvolená
        aj neznáma. 14. 10. (K88V): predvolená hodnota s premennou sa rozvinie (neznáma premenná = neznáma časť) a celkom
        neznáma hodnota X (X=$(…)) môže byť pri : prázdna — pribudne aj predvolená."""
        meno, pred, aj_prazdna = pv[0], pv[1], pv[2]
        if isinstance(pred, tuple):
            moz = ['']
            for druh, x in pred:
                if druh == 'l':
                    moz = [c + x for c in moz]
                else:
                    moz = [c + y for c in moz for y in (self.hodnota(x, ctx) or [NEZNAME])]
                    if len(moz) > 16:
                        moz = moz[:16] + [NEZNAME]
        elif pred is None:       # predvolená hodnota, ktorú rozbor nevie prečítať
            moz = [NEZNAME]
        else:
            moz = [pred]
        hx = self.hodnota(meno, ctx)
        if hx is None:
            return list(dict.fromkeys(moz + [NEZNAME]))
        von = []
        for x in hx:
            if aj_prazdna and x == '':
                von.extend(moz)
            elif aj_prazdna and x == NEZNAME:
                von.extend(moz + [x])
            else:
                von.append(x)
        return list(dict.fromkeys(von))

    def prirad(self, meno, w, ctx):
        if meno in self._nameref:                # declare -n R=W; R=/x mení W (kolo 4)
            ctx.vars[self._nameref[meno]] = None
        kand = self.rozvin(w, ctx)
        # 3. 10. (kolo 3): pri neznámej časti sa zachová značka mktemp na konci (G=$(mktemp -d "$B/x-XXXX") s neznámym B)
        ctx.vars[meno] = [c if u is None else c[:u] + NEZNAME + (
            '/' + MKTEMP_LIST if c.endswith('/' + MKTEMP_LIST) and c.count(MKTEMP_LIST) == 1 and MKTEMP_LIST not in c[:u]
            else '') for c, u in kand] or None
        if ctx.allexport:
            ctx.exp = ctx.exp | {meno}           # 14. 10. (K88V): set -a — premennú dostane spustený skript
        # 2. 10. (úloha K): v rozbore skriptu z disku nie — v skripte by poštová značka v hodnote (aj v komentári
        # v poli ARTIFACTS=( … # aig-crit-notify.sh NIE … )) urobila poštou každý ďalší príkaz; program z premennej
        # ($NOTIFY …) rozbor aj tak vidí cez jej hodnotu
        if (ZNACKY_POSTY.search(meno) or ZNACKY_POSTY.search(w.text())) and not self.skript_hlbka:
            # 2. 10.: premenná, v ktorej je z poštových značiek len exo-rest.py (E=…/exo-rest.py, X="python3 exo-rest.py"),
            # nie je pošta nad celým príkazom — program ju dostane len rozvinutím v texte príkazu (exo_odkaz), kým nie je
            # exportovaná (export, declare -x, set -a → ako doteraz)
            if self.exo_po_starom or ctx.allexport or ZNACKY_POSTY.search(meno) \
                    or ZNACKY_POSTY.search(EXO_NASTROJ.sub('', w.text())):
                ctx.posta_env = True
            else:
                ctx.posta_exo = ctx.posta_exo | {meno}

    # --- cesty ---
    def bezpecne(self, p):
        """Mazanie tu je obnoviteľné alebo neškodné: /tmp/… a vlastné projekty (nie celý ~/projects)."""
        if p.startswith('/tmp/') and len(p) > 5:
            return True
        koren = self.domov + '/projects/'
        if p.startswith(koren):
            prvy = p[len(koren):].split('/')[0]
            return bool(prvy) and prvy not in ('.', '..') and not re.search(r'[*?\[\]]', prvy)
        return False

    def absolutna(self, t, ctx):
        if t.startswith('/'):
            return os.path.normpath(t)
        if ctx.cwd:
            return os.path.normpath(os.path.join(ctx.cwd, t))
        return None

    def mktemp_cerstva(self, w, t):
        """3. 10. (kolo 3): t (rozvinuté slovo w) je presne cesta, ktorú v tom istom príkaze či skripte vytvoril mktemp:
        posledný diel je značka MKTEMP_LIST, značka je v ceste raz a každé rozvinutie v slove je v úvodzovkách (nedelí sa
        podľa IFS, nerozvinie sa vzor). "$W"/*, "$W/..", "$W/$X", "$W"/ ani $W bez úvodzoviek sem nepatria; ani
        "${W:-/x}" — keď mktemp zlyhá, W je prázdne a zmaže sa predvolená cesta."""
        if not t.endswith('/' + MKTEMP_LIST) or t.count(MKTEMP_LIST) != 1 or w.viac or w.predvol:
            return False
        rozv = [k for k, sg in enumerate(w.seg) if sg[0] != 'l']
        return bool(rozv) and all(k in (w.v_uvod or ()) for k in rozv)

    def mazanie(self, w, ctx):
        """'ok' · 'zle' · 'nezname' (cesta v premennej, ktorú kotva nevie prečítať)"""
        for t, u in self.rozvin(w, ctx):
            if ctx.trap and self.mktemp_cerstva(w, t):
                continue          # 3. 10. (kolo 3): obsluha trap maže len to, čo príkaz sám vytvoril cez mktemp
            if u is not None:
                if u == 0:
                    return 'nezname'
                pref = t[:u]
                if not pref.startswith('/'):
                    if not ctx.cwd:
                        return 'nezname'
                    pref = ctx.cwd.rstrip('/') + '/' + pref
                d = pref[:pref.rfind('/') + 1]
                if '..' in d.split('/') or '..' in t[u:].split('/') or not self.bezpecne(os.path.normpath(d)):
                    return 'nezname'
                continue
            p = self.absolutna(t, ctx)
            if not p:
                return 'nezname'
            if not self.bezpecne(p):
                return 'zle'
        return 'ok'

    def cudzi(self, p):
        if not p or not p.startswith('/'):
            return False
        m = re.match(r'/home/([^/]+)(/|$)', os.path.normpath(p))
        return bool(m) and m.group(1) != self.ja

    def kandidati_ciest(self, w, ctx):
        o = []
        for t, u in self.rozvin(w, ctx):
            if u is not None:
                t = t[:u]
            o.append(t)
            m = re.match(r'-{0,2}[A-Za-z0-9_.-]+=(.*)$', t, re.S)
            if m:
                o.append(m.group(1))
        return o

    # --- priebeh ---
    def prikazy(self, cmds, ctx):
        if ctx.hlbka > MAX_HLBKA:
            # hlbšie vnorenie (bash -c v bash -c … deväťkrát) rozbor nedočíta: nie „pustiť“ (predtým tu rozbor ticho
            # skončil a vnorené rm -rf prešlo) ani núdzové pravidlá (tie text s \-únikmi neprečítajú) — zastaviť;
            # v skutočnej práci kolegov ani PS (42 261 príkazov) také vnorenie nie je
            self.kanal(P_HLBKA, K_HLBKA)
            return
        pred = None
        kid = id(ctx)
        self._prikazy_ctx[kid] = self._prikazy_ctx.get(kid, 0) + 1
        try:
            for k, cmd in enumerate(cmds):
                nasl = cmds[k + 1] if k + 1 < len(cmds) else None
                if cmd.rura and pred is not None:
                    self._rura_pred[id(cmd)] = (cmd, pred)
                if cmd.rura or cmd.pozadie or (nasl is not None and nasl.rura):
                    # článok rúry a príkaz na pozadí bežia v podshelli: cd, pushd, priradenie v nich ďalej neplatia
                    # (24. 9.: „cd ~ | true; git push“ a „cd ~ & git push“ pushli z cudzieho priečinka)
                    c2 = ctx.kopia()
                    c2.hlbka = ctx.hlbka
                    self.prikaz(cmd, c2, pred)
                    ctx.posta_env |= c2.posta_env
                    ctx.posta_exo |= c2.posta_exo
                    self.trapy_koniec(c2)
                else:
                    self.prikaz(cmd, ctx, pred)
                pred = cmd
        finally:
            self._prikazy_ctx[kid] -= 1
        if not self._prikazy_ctx[kid]:
            self.trapy_koniec(ctx)

    def trapy_koniec(self, ctx):
        """3. 10. (kolo 3): obsluhy trap nastavené s kontextom ctx sa posúdia s hodnotami na konci (EXIT beží až vtedy:
        `W=$(mktemp -d …); trap 'rm -rf "$W"' EXIT; W=/var/x` maže /var/x). Každá raz; kanály sa pridajú k tým z času
        nastavenia."""
        if not self.trapy:
            return
        moje = [x for x in self.trapy if x[1] is ctx]
        if not moje:
            return
        self.trapy = [x for x in self.trapy if x[1] is not ctx]
        for text, _ in moje:
            c2 = ctx.kopia()
            c2.trap = True
            self.retazec(text, c2)

    def rozvin_text(self, w, ctx):
        """Text slova po rozvinutí premenných — tam, kde shell text ZNOVU rozoberie (eval, bash -c, echo | sh).

        Sondy stráže 10. 10. (PS 4.86 a): eval "$X" a bash -c "$X" dostávali doslovný text slova, takže príkaz skrytý
        v premennej (X='git -C /home/ptoth/… push origin main') rozborom neprešiel a stráž ho pustila. Keď hodnotu
        nevieme celú, ostáva doslovný text ako doteraz (neznámu hodnotu posúdi zvyšok rozboru)."""
        try:
            kand = self.rozvin(w, ctx)
        except Exception:
            return w.text()
        if len(kand) == 1 and kand[0][1] is None:
            return kand[0][0]
        return w.text()

    def retazec(self, text, ctx):
        # Sondy stráže 10. 10. (PS 4.86 a): obal, ktorý spúšťa svoje argumenty (f(){ "$@"; }; f git push --force …), sa
        # prepisoval len v texte celého príkazu — v texte, ktorý shell rozoberie znovu (eval, bash -c "$X", echo | sh),
        # ostal, a volanie tak nikto neposúdil. sh_obaly má rýchlu cestu: bez "$@"/"$*" vráti text hneď.
        self.prikazy(parsuj(sh_obaly(text)), ctx)

    def je_priradenie(self, w):
        return bool(w.seg) and w.seg[0][0] == 'l' and not w.seg[0][2] and bool(PRIRADENIE.match(w.seg[0][1]))

    def priradenie(self, w, ctx):
        prvy = w.seg[0][1]
        m = PRIRADENIE.match(prvy)
        if m.group(2):
            ctx.vars[m.group(1)] = None
            return
        hod = Slovo()
        zv = prvy[m.end():]
        if zv:
            hod.seg.append(('l', zv, False))
            hod.tilda = zv.startswith('~')
        hod.seg.extend(w.seg[1:])
        if w.predvol:           # ${X:-y} v hodnote (AZ="${AZ_BIN:-az}") — indexy segmentov sa posunú
            hod.predvol = {k - 1 + (1 if zv else 0): v for k, v in w.predvol.items() if k >= 1}
        self.prirad(m.group(1), hod, ctx)

    def cyklus(self, sl, ctx):
        if not sl:
            return
        meno = sl[0].text()
        if len(sl) >= 2 and sl[1].text() == 'in':
            hod = []
            for w in sl[2:]:
                for c, u in self.rozvin(w, ctx):
                    hod.append(c if u is None else c[:u] + NEZNAME)
            # 2. 10. večer: hodnôt cyklu nad 64 — posledná je neznáma (predtým ticho prvých 16)
            ctx.vars[meno] = (hod[:64] + [NEZNAME] if len(hod) > 64 else hod) or None
        else:
            ctx.vars[meno] = None

    def prikaz(self, cmd, ctx, pred):
        """Priradenie pred príkazom (GIT_DIR=… git status) platí len pre ten príkaz — po ňom sa vráti predošlá hodnota
        (24. 9.: GIT_DIR z domova pri inom príkaze pustil push z cudzieho priečinka)."""
        ulozene = []
        self._docasne.append(ulozene)
        try:
            self._prikaz(cmd, ctx, pred)
        finally:
            self._docasne.pop()
            for meno, stara, bola in reversed(ulozene):
                if bola:
                    ctx.vars[meno] = stara
                else:
                    ctx.vars.pop(meno, None)

    def zapamataj(self, w, ctx):
        m = PRIRADENIE.match(w.seg[0][1])
        if m and self._docasne:
            self._docasne[-1].append((m.group(1), ctx.vars.get(m.group(1)), m.group(1) in ctx.vars))

    def _prikaz(self, cmd, ctx, pred):
        # 2. 10.: premenná s cestou k exo-rest.py použitá inak než na priame spustenie nástroja = ako pred 2. 10. (posta_env)
        if ctx.posta_exo:
            self.exo_odkaz(cmd, ctx)
        # substitúcie sa vykonajú pred príkazom
        for w in cmd.slova + [w for _, w in cmd.presm] + cmd.herestr:
            for sg in w.seg:
                if sg[0] == 's' and sg[1]:
                    self.prikazy(sg[1], ctx.kopia())
        for vn in cmd.vnorene:
            self.prikazy(vn, ctx.kopia())
        # 14. 10. (K88V): ${X:=…} a ${X=…} priradia X pri rozvinutí slova (`: "${P:=$D/x.py}"; python3 "$P"`)
        for w in cmd.slova + [w for _, w in cmd.presm] + cmd.herestr:
            for pv in (w.predvol or {}).values():
                if pv[3]:
                    ctx.vars[pv[0]] = self.predvol_hod(pv, ctx)
        if not (ctx.vzdial or ctx.kontajner):
            try:
                for op, w in cmd.presm:      # 3. 10. (kolo 4): výstup do súboru — skript s tou cestou je zmenený
                    if op in VYSTUP:
                        self.zmena_slovo(w, ctx, nahrada=op not in ('>>', '&>>'), pripojenie=op in ('>>', '&>>'))
            except ZMENA_CHYBY:
                self.zmenene['vsetko'] = True
        if cmd.podshell is not None:
            self.prikazy(cmd.podshell, ctx.kopia())
            self.presmerovania(cmd, ctx, ctx.sudo)
            return
        sl = cmd.slova
        i = 0
        while i < len(sl) and not sl[i].kvot and sl[i].text() in KLUCOVE | KLUCOVE_KONIEC:
            k = sl[i].text()
            if k in ('for', 'select'):
                self.cyklus(sl[i + 1:], ctx)
                return
            if k == 'function':      # function f { … } — telo sú príkazy (24. 9.)
                i += 2
                continue
            if k in KLUCOVE_KONIEC:
                return
            i += 1
        prefix = []
        while i < len(sl) and self.je_priradenie(sl[i]):
            self.zapamataj(sl[i], ctx)
            self.priradenie(sl[i], ctx)
            prefix.append(sl[i])
            i += 1
        if i >= len(sl):
            if self._docasne:
                self._docasne[-1].clear()      # samotné priradenie (X=…) platí ďalej
            self.presmerovania(cmd, ctx, ctx.sudo)
            return
        # obaly pred príkazom (KOTVA-1): sudo, timeout, time, nice, nohup, env, xargs, stdbuf, ionice …
        sudo = ctx.sudo
        xargs = False
        xargs_nahrada = None    # znak -I/--replace: vstup sa vkladá do slov, nepripája sa
        xargs_prog = None       # 16. 10. (KXA): 'xargs' / 'parallel' a voľby xargs — meno skriptu zo vstupu
        xargs_volby = []
        while i < len(sl):
            if self.je_priradenie(sl[i]):      # sudo -u x VAR=… príkaz (PS 4. 9.: sudo -u "$u" XDG_RUNTIME_DIR=… podman ps)
                self.zapamataj(sl[i], ctx)
                self.priradenie(sl[i], ctx)
                prefix.append(sl[i])
                i += 1
                continue
            p = self.meno(sl[i], ctx)
            t = [w.text() for w in sl[i + 1:]]
            if p in ('sudo', 'doas'):
                sudo = True
                j = 0
                shell = edit = False
                while j < len(t):
                    x = t[j]
                    if x == '--':
                        j += 1
                        break
                    if not x.startswith('-') or x == '-':
                        break
                    if x.startswith('--'):
                        n = x.split('=')[0]
                        shell |= n in ('--login', '--shell')
                        edit |= n == '--edit'
                        j += 2 if ('=' not in x and n in SUDO_L_ARG) else 1
                        continue
                    telo = x[1:]
                    skok = 1
                    for k, ch in enumerate(telo):
                        if ch in 'is':
                            shell = True
                        if ch == 'e':
                            edit = True
                        if ch in 'ugCDprRtTU':
                            if k == len(telo) - 1:
                                skok = 2
                            break
                    j += skok
                i = i + 1 + j
                if edit and p == 'sudo':
                    self.kanal(P_SUDO, K_SUDO)
                    return
                if i >= len(sl):
                    if shell and cmd.heredoc:
                        for h, _ in cmd.heredoc:
                            self.retazec(h, ctx.kopia(sudo=True))
                    elif shell:
                        self.kanal(P_SUDO, K_SUDO)
                    return
                continue
            if p == 'sudoedit':
                self.kanal(P_SUDO, K_SUDO)
                return
            if p == 'timeout':
                i = i + 1 + volby(t, 'sk', ('--signal', '--kill-after')) + 1
                continue
            if p == 'time':
                i = i + 1 + volby(t, 'fo', ('--format', '--output'))
                continue
            if p == 'nice':
                i = i + 1 + volby(t, 'n', ('--adjustment',))
                continue
            if p in ('nohup', 'builtin', 'unbuffer', 'nocache'):
                i += 1
                continue
            if p == 'setsid':
                i = i + 1 + volby(t)
                continue
            if p == 'systemd-run':
                # transient jednotka/scope spustí príkaz za voľbami; irreverzibilný vnútorný (rm, pošta, curl POST) sa
                # inak skryl: systemd-run --user --scope -p MemoryMax=… --quiet -- rm -rf … (u PS beží tento obal denne)
                i = i + 1 + volby(t, SYSTEMD_RUN_S, SYSTEMD_RUN_L)
                continue
            if p == 'unshare':
                i = i + 1 + volby(t, UNSHARE_S, UNSHARE_L)
                continue
            if p in ('bwrap', 'firejail', 'nsjail'):
                # 14. 10. (K88T, D3): obal pieskoviska — vnútorný príkaz sa posúdi; obal, ktorý mení súbory alebo
                # prostredie (alebo voľba, ktorú stráž nepozná), je nedočítaný (zastaví)
                j, ciste = obal_sandbox(p, t)
                if not ciste:
                    self.skript_nedocitany(p)
                i = i + 1 + j
                if i >= len(sl):              # bez príkazu spustí shell — ako sudo -s
                    for h, _ in cmd.heredoc:
                        self.retazec(h, ctx.kopia(sudo=sudo))
                    return
                continue
            if p == 'busybox':      # multi-call binárka: busybox rm … = rm …
                i = i + 1 + volby(t)
                continue
            if p == 'exec':
                i = i + 1 + volby(t, 'a')
                continue
            if p == 'command':
                if t[:1] in (['-v'], ['-V']):
                    return
                i = i + 1 + volby(t)
                continue
            if p == 'env':
                j = 0
                while j < len(t):
                    x = t[j]
                    if x in ('-u', '--unset', '-C', '--chdir', '-S', '--split-string'):
                        j += 2
                        continue
                    if x.startswith('-') and x != '-':
                        j += 1
                        continue
                    if self.je_priradenie(sl[i + 1 + j]):
                        self.zapamataj(sl[i + 1 + j], ctx)
                        self.priradenie(sl[i + 1 + j], ctx)
                        prefix.append(sl[i + 1 + j])
                        j += 1
                        continue
                    break
                i = i + 1 + j
                continue
            if p == 'xargs':
                xargs = True
                _kon = volby(t, 'adEILnPs', XARGS_L_ARG)
                xargs_nahrada = xargs_token(t[:_kon])
                xargs_prog, xargs_volby = 'xargs', t[:_kon]
                i = i + 1 + _kon
                continue
            if p == 'parallel':    # GNU parallel: príkaz za voľbami, vstupy za ::: (tie ostanú ako argumenty príkazu)
                xargs = True
                xargs_prog = 'parallel'
                _kon, _pi = par_volby(t)
                if not (ctx.vzdial or ctx.kontajner):
                    # 16. 10. (KXP): čo parallel naozaj spustí — príkaz so vstupmi za ::: alebo z rúry (aj na mieste {}),
                    # príkaz z jedného slova (shell), vstup ako príkaz (parallel bez príkazu)
                    self.parallel_beh(sl[i + 1 + _kon:], _pi, cmd, ctx, sudo, pred)
                i = i + 1 + _kon
                continue
            if p == 'stdbuf':
                i = i + 1 + volby(t, 'ioe', ('--input', '--output', '--error'))
                continue
            if p == 'ionice':
                i = i + 1 + volby(t, 'cnpPu', ('--class', '--classdata', '--pid', '--pgid', '--uid'))
                continue
            if p == 'nsenter':       # sudo nsenter --net=… <príkaz>: príkaz sa posúdi (pod sudo čítanie prejde, zápis nie)
                i = i + 1 + volby(t, 'tSG', ('--target', '--setuid', '--setgid'))
                if i >= len(sl):          # bez príkazu spustí shell — ako sudo -s
                    if cmd.heredoc:
                        for h, _ in cmd.heredoc:
                            self.retazec(h, ctx.kopia(sudo=sudo))
                    elif sudo:
                        self.kanal(P_SUDO, K_SUDO)
                    return
                continue
            if p == 'ip' and t[:2] == ['netns', 'exec'] and len(t) > 3:     # ip netns exec <meno> <príkaz>
                i += 4
                continue
            if p in ('chrt', 'taskset'):
                i = i + 1 + volby(t) + 1
                continue
            if p == 'flock':
                j = 0
                while j < len(t):
                    x = t[j]
                    if x in ('-c', '--command') and j + 1 < len(t):
                        self.retazec(sl[i + 2 + j].text(), ctx.kopia(sudo=sudo))
                        return
                    if x in ('-w', '--wait', '--timeout', '-E', '--conflict-exit-code'):
                        j += 2
                        continue
                    if x.startswith('-'):
                        j += 1
                        continue
                    break
                i = i + 1 + j + 1
                continue
            if p == 'script':
                # 3. 10. (kolo 4, overenie kola 3): script -qc "bash t/a.sh" /dev/null — príkaz za -c
                for k, x in enumerate(t):
                    if x in ('-c', '--command') and k + 1 < len(t):
                        self.retazec(t[k + 1], ctx.kopia(sudo=sudo))
                        return
                    if x.startswith('--command='):
                        self.retazec(x.split('=', 1)[1], ctx.kopia(sudo=sudo))
                        return
                    if re.match(r'-[a-zA-Z]*c$', x) and k + 1 < len(t):
                        self.retazec(t[k + 1], ctx.kopia(sudo=sudo))
                        return
                    if re.match(r'-[a-zA-Z]*c.', x):
                        self.retazec(x[x.index('c') + 1:], ctx.kopia(sudo=sudo))
                        return
                break
            if p == 'watch':
                j = volby(t, 'nq', ('--interval', '--equexit'))
                if '-x' in t[:j] or '--exec' in t[:j]:
                    i = i + 1 + j
                    continue
                self.retazec(' '.join(t[j:]), ctx.kopia(sudo=sudo))
                return
            if p in ('su', 'runuser'):
                self.su(p, sl[i + 1:], t, cmd, ctx)
                return
            break
        if i >= len(sl):
            return
        pw = sl[i]
        args = sl[i + 1:]
        t = [w.text() for w in args]
        kand = self.rozvin(pw, ctx)
        pv = kand[0][0] if len(kand) == 1 and kand[0][1] is None else pw.text()
        if len(kand) > 1 and kand[0][1] is None and kand[0][0] and all(u == 0 for _, u in kand[1:]):
            pv = kand[0][0]     # 2. 10. večer: "${AZ_BIN:-az}" — program je predvolená hodnota (inak neznámy, nič by nezastavil)
        # Sondy stráže 10. 10. (PS 4.86 a): X='git -C /home/ptoth/… push origin main'; $X — shell hodnotu rozdelí na slová
        # a spustí ako príkaz, rozbor z nej robil názov programu (basename „cp-mis push origin main“), takže nezastavil nič.
        # Neobalené slovo s premennou, ktorej hodnotu vieme celú a má v sebe medzeru, sa posúdi ako celý príkaz.
        if (' ' in pv and not pw.kvot and ctx.hlbka < MAX_HLBKA and len(kand) == 1 and kand[0][1] is None
                and any(sg[0] != 'l' for sg in pw.seg)):
            self.retazec(' '.join([pv] + t), ctx.kopia(sudo=sudo))
            return
        pb = os.path.basename(pv)
        if not (ctx.vzdial or ctx.kontajner):
            try:
                self.zmena_programu(pb, args, t, ctx, xargs, cmd)
            except ZMENA_CHYBY:              # zápis, ktorý kotva nevie prečítať — hocičo môže byť zmenené (celý text)
                self.zmenene['vsetko'] = True
            try:
                self.zmena_vystup(pb, args, t, cmd, ctx)
            except ZMENA_CHYBY:
                self.zmenene['vsetko'] = True
        self._ctx_rury = ctx
        self.nastroj_ostro(pv, pb, args, t, sl[:i], ctx, cmd, pred)
        self.skript_spusteny(pw, pb, pv, args, t, sl[:i], cmd, ctx, sudo, pred)
        if xargs_prog and not (ctx.vzdial or ctx.kontajner):
            self.xargs_skript(pw, pb, args, t, xargs_nahrada, xargs_volby, xargs_prog, cmd, ctx, sudo, pred)
        self.kod_argv_subor(pb, args, t, ctx, sudo)
        self.kod_v_prikaze(pb, t, cmd, ctx, sudo, pred)

        if pb == 'popd':                   # späť do priečinka pred pushd (24. 9.: pushd ~; popd; git push)
            ctx.cwd = ctx.zasobnik.pop() if ctx.zasobnik else None
            ctx.cwd_vzor = None
            return
        if pb in ('cd', 'pushd'):
            poz = [w for w in args if not w.text().startswith('-') or w.text() == '-']
            if pb == 'pushd':
                ctx.zasobnik.append(ctx.cwd)
            zaklad = ctx.cwd or ctx.cwd_vzor
            ctx.cwd_vzor = None
            if not poz and pb == 'pushd':
                ctx.cwd = None
            elif not poz:
                ctx.cwd = self.domov
            elif poz[0].text() == '-':
                ctx.cwd = None
            else:
                k2 = self.rozvin(poz[0], ctx)
                ctx.cwd = self.absolutna(k2[0][0], ctx) if len(k2) == 1 and k2[0][1] is None else None
                if ctx.cwd is None and len(k2) == 1:
                    # 2. 10. (úloha K): `cd $p` v cykle cez projekty — priečinok je jeden z podpriečinkov známeho miesta
                    ctx.cwd_vzor = self.vzor_cesty(k2[0][0], zaklad)
            return
        if pb in ('export', 'local', 'declare', 'typeset', 'readonly'):
            if any(x[:1] == '-' and 'n' in x[1:] for x in t):
                # 3. 10. (kolo 4): declare -n R=W — R je iné meno pre W; obe sú neznáme a ďalšie priradenie do R mení W
                for x in t:
                    m = re.match(r'([A-Za-z_][A-Za-z0-9_]*)(?:=([A-Za-z_][A-Za-z0-9_]*))?', x)
                    if m and not x.startswith('-'):
                        ctx.vars[m.group(1)] = None
                        if m.group(2):
                            ctx.vars[m.group(2)] = None
                            self._nameref[m.group(1)] = m.group(2)
                self.presmerovania(cmd, ctx, sudo)
                return
            for w in args:
                if self.je_priradenie(w):
                    self.priradenie(w, ctx)
            # 14. 10. (K88V): export X=…, export X, declare -x X — X dostane skript, ktorý príkaz spustí
            if pb == 'export' or any(x[:1] == '-' and 'x' in x[1:] for x in t):
                ctx.exp = ctx.exp | {m.group(1) for m in (re.match(r'([A-Za-z_][A-Za-z0-9_]*)(?:\[[^\]]*\])?(?:\+?=|$)', x)
                                                         for x in t if x[:1] not in '-+') if m}
            # 2. 10.: premennú s cestou k exo-rest.py dostanú aj spúšťané programy (export, declare -x …), alebo na ňu môže
            # ukazovať iné meno (declare -n) — ako pred 2. 10.: každý ďalší program sa berie ako odoslanie
            if ctx.posta_exo and (pb == 'export' or any(x[:1] in '-+' and len(x) > 1 for x in t)):
                ctx.posta_env = True
            self.presmerovania(cmd, ctx, sudo)
            return
        if pb == 'set' and any((x[:1] == '-' and not x.startswith('--') and 'a' in x[1:]) or x == 'allexport' for x in t):
            ctx.allexport = True            # set -a / set -o allexport (2. 10.)
            if ctx.posta_exo:
                ctx.posta_env = True
        if pb in ('read', 'mapfile', 'readarray', 'unset'):
            for x in t:
                if re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]*', x):
                    ctx.vars[x] = None
            return
        # 3. 10. (kolo 4, overenie kola 3): printf -v W, getopts … W, let W=… priradia — W je potom neznáma (predtým stará
        # hodnota: obsluha trap 'rm -rf "$W"' pustila zmazanie /var/lib/x)
        if pb == 'printf':
            for k, x in enumerate(t):
                if x == '-v' and k + 1 < len(t):
                    ctx.vars[re.sub(r'\[.*', '', t[k + 1])] = None
                elif x.startswith('-v') and len(x) > 2:
                    ctx.vars[re.sub(r'\[.*', '', x[2:])] = None
                elif not x.startswith('-'):
                    break
        if pb == 'getopts':
            for x in t[1:2] + ['OPTARG', 'OPTIND']:
                ctx.vars[x] = None
        if pb == 'let':
            for x in t:
                for m in re.finditer(r'[A-Za-z_][A-Za-z0-9_]*', x):
                    ctx.vars[m.group()] = None
        # shell: reťazec za -c, heredoc alebo text z rúry sú príkazy
        if pb in SHELLY:
            j = 0
            c_flag = n_flag = False
            while j < len(t):
                x = t[j]
                if x in ('--rcfile', '--init-file', '-o', '+o', '-O', '+O'):
                    j += 2
                    continue
                if x.startswith('--'):
                    j += 1
                    continue
                if len(x) > 1 and x[0] in '-+':
                    c_flag |= 'c' in x[1:]
                    n_flag |= 'n' in x[1:]
                    j += 1
                    continue
                break
            c2 = ctx.kopia(sudo=sudo)
            if c_flag:
                if j < len(args):
                    # 3. 10. (kolo 4, overenie kola 3: bash -c 'bash "$1"' _ t/a.sh): $0, $1 … sú slová za kódom
                    for k in range(10):
                        c2.vars.pop(str(k), None)
                    for k, w in enumerate(args[j + 1:j + 11]):
                        if w.viac:
                            break
                        c2.vars[str(k)] = [c if u is None else c[:u] + NEZNAME for c, u in self.rozvin(w, ctx)] or None
                    self.retazec(self.rozvin_text(args[j], ctx), c2)
                return
            if n_flag:
                return
            if j >= len(args):
                texty = [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr]
                if not texty and cmd.rura and pred is not None:
                    texty = [h for h, _ in pred.heredoc] + [w.text() for w in pred.herestr]
                    tx = self.text_echa_cmd(pred, ctx)     # echo/printf | sh — bez formátu %s (sondy 10. 10.)
                    if tx:
                        texty.append(tx)
                for tx in texty:
                    self.retazec(tx, c2)
                if not texty:            # bash < skript.sh — skript zo vstupu ako bash skript.sh
                    for op, w in cmd.presm:
                        if op == '<':
                            obs = self.obsah_skriptu(w, ctx)
                            if obs is not None:
                                texty = [obs]
                                self.spusti_skript(w, obs, ctx, sudo)
                if sudo and not texty:
                    self.kanal(P_SUDO, K_SUDO)
                return
            # inak skript: ďalej ako bežný príkaz (pošta podľa názvu skriptu; pod sudo to nie je čítanie);
            # skript zapísaný heredocom v tom istom príkaze sa rozoberie ako príkazy
            if j < len(args):
                obs = self.obsah_skriptu(args[j], ctx)
                if obs is None:
                    obs = self.text_zo_echa(args[j], ctx)      # bash <(echo '…') — text stojí v príkaze
                    if obs is not None:
                        self.z_disku = False
                if obs is not None:
                    self.spusti_skript(args[j], obs, ctx, sudo)
        if pb in ('source', '.') and args:
            obs = self.obsah_skriptu(args[0], ctx)
            if obs is not None:
                self.spusti_skript(None, obs, ctx, sudo)
            try:
                self.source_premenne(args[t[:1] == ['--']] if len(args) > (t[:1] == ['--']) else None, ctx)
            except ZMENA_CHYBY:
                for k in list(ctx.vars):
                    ctx.vars[k] = None
        # súbor zapísaný heredocom (cat > f <<EOF, tee f <<EOF) — keď ho ten istý príkaz spustí, rozoberie sa jeho obsah
        # (PS 6. 9.: `cat > send.py <<EOF … smtplib … send_message … EOF; python3 send.py` odoslal mail; odoslaná kotva ho
        # zastavila podľa slova smtp, rozbor by ho bez tohto pustil)
        if cmd.heredoc and pb in ('cat', 'tee'):
            # KHM (16. 10.): >> a tee -a pridajú heredoc na koniec — súbor je doterajší obsah (zapísaný skôr v tom istom
            # príkaze, inak z disku) a heredoc; predtým len heredoc, takže modul s poštou, na ktorý príkaz pripísal riadok,
            # by sa po čítaní napísaných modulov (nižšie, je_subor) čítal bez pošty
            ciele = [(w, op == '>>') for op, w in cmd.presm if op in ('>', '>>', '>|')]
            if pb == 'tee':
                pripoj = any(re.fullmatch(r'--append|-[A-Za-z]*a[A-Za-z]*', w.text()) for w in args)
                ciele += [(w, pripoj) for w in args if not w.text().startswith('-')]
            for w, pripoj in ciele:
                for c in [c for c, u in self.rozvin(w, ctx) if u is None][:1]:
                    p = self.absolutna(c, ctx)
                    if p and NEZNAME not in p:
                        text = '\n'.join(h for h, _ in cmd.heredoc)
                        pred = (self.subory[p] if p in self.subory else self.subor_disk(p)) if pripoj else None
                        self.subory[p] = pred + '\n' + text if pred else text
        elif cmd.heredoc and self.mkt_rezim != 'vypnute':
            # marketing: skript zapísaný z heredocu iným nástrojom (Erika 25. 9.: sed -e 's#…#…#' > …/uprac.cjs <<'EOF' a hneď
            # node …/uprac.cjs — DELETE v Senderi); pošta tento zápis nečíta, preto samostatne a len mimo polohy vypnute
            for w in [w for op, w in cmd.presm if op in ('>', '>>', '>|')]:
                for c in [c for c, u in self.rozvin(w, ctx) if u is None][:1]:
                    p = self.absolutna(c, ctx)
                    if p and NEZNAME not in p:
                        self.mkt_subory[p] = '\n'.join(h for h, _ in cmd.heredoc)
        if pb not in SHELLY and not INTERPRETY.match(pb):     # ./send.sh, /tmp/…/send.py
            obs = self.obsah_skriptu(pw, ctx)
            if obs is not None and '\x00' not in obs[:4096] and not obs.startswith('\x7fELF'):
                prvy = obs.split('\n', 1)[0]
                if prvy.startswith('#!') and re.search(r'python|node|perl|ruby|php', prvy):
                    if self.kod_posiela([obs], ctx, kod_jazyk('', prvy)):
                        self.kanal(P_POSTA, K_POSTA)
                    self.mkt_kody([obs])
                else:
                    self.spusti_skript(pw, obs, ctx, sudo)
        if pb == 'ssh' and not sudo:
            j = volby(t, 'bcDEeFIiJLlmOopQRSWwB')
            c2 = ctx.kopia(sudo=False)
            # Sondy stráže 10. 10. (PS 4.86 a): `ssh localhost '…'` beží na TOMTO stroji, rozbor ho však bral ako cudzí
            # a miestne kanály (cudzí domov, mazanie) neposudzoval — `ssh localhost 'cp … /home/ptoth/…'` prešlo.
            ciel = self.rozvin_text(args[j], ctx) if j < len(args) else ''
            ucet, _, host = ciel.rpartition('@')
            try:
                moj_host = os.uname().nodename.lower()
            except Exception:
                moj_host = ''
            miestny = (host.lower() in ('localhost', '127.0.0.1', '::1', '[::1]', moj_host, moj_host.split('.')[0])
                       and host != '' and (not ucet or ucet == self.ja))
            c2.vzdial = not miestny
            c2.cwd = self.domov if miestny else None
            if j + 1 < len(args):
                _vzd = ' '.join(w.text() for w in args[j + 1:])
                self.retazec(_vzd, c2)
                # 1. 11. 2026 (nález AIG-P261101-add601): `ssh host 'bash -s' <<'EOF' … EOF` — rozbor sa pozrel len
                # na „bash -s“ a obsah dokumentu na vstupe nevidel VÔBEC, takže tým tvarom príkazu prešlo všetko
                # (partner ním celý 1. 11. pracoval na serveri). Keď vzdialený program číta vstup (shell, alebo
                # interpreter so samostatným „-“), posúdi sa aj obsah dokumentu — tak ako pri miestnom `bash <<EOF`.
                _prv = os.path.basename(((_vzd.strip().split() or [''])[0]).strip('\'"'))
                if _prv in SHELLY:
                    for h, _ in cmd.heredoc:
                        self.retazec(h, c2)        # obsah dokumentu je vzdialený PRÍKAZ
                # Kód interpretra poslaný na vzdialený stroj (`ssh host python3 - <<EOF …`) tu posúdený NIE JE:
                # kanály nad kódom (zápis na cudziu adresu, pošta) sa vo vzdialenom kontexte nespúšťajú a zmeniť
                # to je samostatná vec, nie oprava tejto diery. Je to zapísané ako nález (1. 11. 2026).
            else:
                for h, _ in cmd.heredoc:
                    self.retazec(h, c2)
            return
        if pb == 'eval':
            c2 = ctx.kopia()
            self.retazec(' '.join(self.rozvin_text(w, ctx) for w in args), c2)
            # 3. 10. (kolo 4): čo eval priradí, platí aj po ňom — zmenená premenná je neznáma; text z $(…) či premennej
            # (eval "$(cat x)", eval "$CMD") môže priradiť čokoľvek — všetky premenné sú neznáme
            if any(sg[0] != 'l' for w in args for sg in w.seg):
                mena = set()
                for w in args:
                    if any(sg[0] == 's' for sg in w.seg):
                        zs = self.subor_zo_substitucie(w)
                        k = [c for c, u in self.rozvin(zs, ctx) if u is None][:1] if zs is not None else []
                        q = self.absolutna(k[0], ctx) if k else None
                        obs = self.subory.get(q) if q else None
                        if obs is None and q and self.smie(q):
                            try:
                                if os.path.isfile(q) and os.path.getsize(q) <= SKRIPT_MAX_SUBOR:
                                    with open(q, encoding='utf-8', errors='replace') as f:
                                        obs = f.read()
                            except (OSError, ValueError):
                                obs = None
                        m = priradene_mena(obs, True) if obs is not None else None
                    else:
                        # text pre eval z premennej býva reťazec v úvodzovkách (ok(){ eval "$2"; }; ok x '[[ … ]]',
                        # CMD='W=/x'; eval "$CMD") — mená z reťazcov v úvodzovkách v texte príkazu a prečítaných skriptov
                        texty = self.skript_stav.get('texty')
                        m = priradene_mena(' ;\n'.join(re.findall("'[^']*'|\"(?:[^\"\\\\]|\\\\.)*\"",
                                                                   '\n'.join(texty)))) if texty else None
                    if m is None:
                        mena = None
                        break
                    mena |= m
                for k in (list(ctx.vars) if mena is None else mena):
                    ctx.vars[k] = None
            else:
                for k in set(ctx.vars) | set(c2.vars):
                    if ctx.vars.get(k) != c2.vars.get(k):
                        ctx.vars[k] = None
            if c2.cwd != ctx.cwd:
                ctx.cwd = None
            return
        if pb == 'trap' and t and t[0] not in ('-', '--', '-p', '-l', '-lp', '-pl') and t[0].strip():
            # 2. 10. večer: obsluha signálu (trap '…' EXIT ERR) sa spustí — rozbor ju predtým nečítal vôbec
            # 3. 10. (kolo 3): v obsluhe prejde mazanie presne cesty z mktemp (Ctx.trap); obsluha EXIT beží s hodnotami
            # na konci, preto sa posúdi aj na konci zoznamu príkazov s týmto kontextom (prikazy)
            c2 = ctx.kopia()
            c2.trap = True
            self.retazec(t[0], c2)
            self.trapy.append((t[0], ctx))
            return
        if pb in ('docker', 'podman'):
            if self.kontajner_exec(args, t, cmd, ctx, sudo):
                return
            self.kontajner_run(args, t, cmd, ctx, sudo)   # vonkajší príkaz sa posúdi ďalej ako doteraz

        if pb == 'git':
            self.git(args, t, ctx)
        self.posta(pb, pv, args, t, prefix, cmd, ctx, xargs)
        if self.mkt_rezim != 'vypnute' and INTERPRETY.match(pb):
            self.mkt_interpret(pb, args, t, cmd, ctx)
        if pb == 'gh':
            self.gh(t, args, ctx, cmd, pred)
        self.nasadenie_ine(pb, args, t, ctx, pv)
        if pb == 'az':
            slova = []
            for x in t:
                if x.startswith('-'):
                    break
                slova.append(x)
            pomoc = any(x in ('--help', '-h') for x in t)
            if any(x.startswith(AZ_SLOVESA) for x in slova[1:]) and not pomoc:
                self.kanal(P_AZ, K_AZ)
            if slova[:1] == ['rest'] and not pomoc:
                # az rest: predvolene GET (čítanie); --method/-m POST/PUT/PATCH/DELETE zapisuje do Graphu alebo Azure
                # (PS 18. 9.: az rest --method DELETE …/appRoleAssignments/…); metóda v premennej = neznáma → zastaví
                met = 'get'
                url = None
                for k, x in enumerate(t):
                    if x in ('--method', '-m') and k + 1 < len(t):
                        met = t[k + 1]
                    elif x.startswith('--method='):
                        met = x.split('=', 1)[1]
                    elif x.startswith('-m') and len(x) > 2 and not x.startswith('--'):
                        met = x[2:]
                    elif x in ('--url', '--uri', '-u') and k + 1 < len(t):
                        url = t[k + 1]
                    elif x.startswith(('--url=', '--uri=')):
                        url = x.split('=', 1)[1]
                if '$' in met or met.lower() in ('put', 'patch', 'delete') or \
                        (met.lower() == 'post' and not (url and AZ_POST_CITA.match(url))):
                    self.kanal(P_AZ, K_AZ)
        rura_extra = list(self.rura_ciele(pred, ctx)) if xargs and cmd.rura else []
        # vstup, ktorý xargs/parallel privedie klientovi, sa nedá prečítať (zoznam v súbore `xargs -a`, rúra z cat …):
        # adresa odoslania môže byť v ňom (nezávislý beh 10. 10.) — pri -I/--replace len tam, kde je náhradný znak
        xg = (bool(xargs) and not rura_extra, xargs_nahrada)
        if pb == 'curl':
            self.curl(t, args, ctx, rura_extra, cmd, xg)
        if pb == 'wget':
            self.wget(t, args, ctx, rura_extra, cmd, xg)
        if pb in ('http', 'https'):
            self.httpie(t, args, ctx, rura_extra, xg)
        if pb in ('scp', 'rsync') and not ctx.kontajner:
            self.scp_rsync(pb, args, t, ctx, rura_extra)
        if pb == 'rm' and not ctx.kontajner:
            self.rm(args, t, ctx, self.zdroj_xargs(pred, ctx) if xargs and cmd.rura else None)
        if pb == 'find':
            self.find(args, t, ctx, sudo)
        if not ctx.vzdial:
            self.cudzi_domov(pw, args, t, pb, cmd, ctx)
        if sudo and not ctx.kontajner and (not citanie(pb, t) or self.zapis_presmerovanim(cmd, ctx)):
            self.kanal(P_SUDO, K_SUDO)
        # systemctl pod sudo chytí pravidlo sudo (ako doteraz, rovnaký kanál v audite)
        if pb == 'systemctl' and not sudo and not ctx.kontajner and not any(x == '--user' or x.startswith('--user=') for x in t):
            if sysctl_sub(t) in SYSCTL_ZAPIS:
                self.kanal(P_SYSCTL, K_SYSCTL)

    def meno(self, w, ctx):
        """Meno príkazu: slovo, alebo známa hodnota premennej (X=sudo; $X rm … — obal sa premennou nesmie skryť)."""
        if any(sg[0] != 'l' for sg in w.seg):
            k = self.rozvin(w, ctx)
            if len(k) == 1 and k[0][1] is None:
                return os.path.basename(k[0][0])
        return os.path.basename(w.text())

    def kontajner_exec(self, args, t, cmd, ctx, sudo):
        """podman/docker exec <kontajner> <príkaz>: príkaz sa rozoberie ako príkaz (pošta, push, API), nie ako text —
        `podman exec c php -l /tmp/smtp.php` je kontrola syntaxe (Erika 7. 9.), `… sh -c "sendmail …"` odoslanie.
        Mazanie a sudo vnútri kontajnera sa týkajú kontajnera, nie servera (ako v odoslanej kotve)."""
        j = 0
        while j < len(t) and t[j].startswith('-'):
            j += 2 if t[j] in ('-H', '--host', '--context', '-c', '--config', '--log-level', '--connection', '--url', '--root',
                               '--runroot') else 1
        if j >= len(t) or t[j] != 'exec':
            return False
        j += 1
        while j < len(t) and t[j].startswith('-') and t[j] != '-':
            j += 2 if t[j] in ('-u', '--user', '-w', '--workdir', '-e', '--env', '--env-file', '--detach-keys',
                               '--preserve-fds') else 1
        if j + 1 >= len(t):
            return False
        pod = Cmd()
        pod.slova = args[j + 1:]
        pod.heredoc = cmd.heredoc
        pod.herestr = cmd.herestr
        c2 = ctx.kopia(sudo=False)
        c2.vzdial = True
        c2.kontajner = True
        c2.cwd = None
        self.prikaz(pod, c2, None)
        if sudo:
            self.kanal(P_SUDO, K_SUDO)
        self.presmerovania(cmd, ctx, sudo)
        return True

    # docker/podman run: krátke a dlhé voľby s hodnotou — za nimi je obraz a až za obrazom príkaz, ktorý sa spustí
    DOCKER_S_ARG = 'acehlmpuvw'
    DOCKER_L_ARG = set('''--add-host --annotation --attach --blkio-weight --blkio-weight-device --cap-add --cap-drop
--cgroup-parent --cgroupns --cidfile --cpu-period --cpu-quota --cpu-rt-period --cpu-rt-runtime --cpu-shares --cpus
--cpuset-cpus --cpuset-mems --device --device-cgroup-rule --device-read-bps --device-read-iops --device-write-bps
--device-write-iops --dns --dns-option --dns-search --domainname --entrypoint --env --env-file --expose --gpus
--group-add --health-cmd --health-interval --health-retries --health-start-period --health-timeout --hostname
--ip --ip6 --ipc --isolation --kernel-memory --label --label-file --link --link-local-ip --log-driver --log-opt
--mac-address --memory --memory-reservation --memory-swap --memory-swappiness --mount --name --net --network
--network-alias --oom-score-adj --pid --pids-limit --platform --publish --pull --restart --runtime --security-opt
--shm-size --stop-signal --stop-timeout --storage-opt --sysctl --tmpfs --ulimit --user --userns --uts --volume
--volume-driver --volumes-from --workdir'''.split())

    def kontajner_run(self, args, t, cmd, ctx, sudo):
        """podman/docker run <obraz> <príkaz>: príkaz vnútri kontajnera sa rozoberie ako príkaz — `docker run
        curlimages/curl -X POST https://…` posiela údaje von rovnako ako curl na stroji (nezávislý beh 10. 10.:
        prešlo bez otázky). Mazanie a sudo vnútri kontajnera sa týkajú kontajnera, nie servera (ako pri exec);
        vonkajší príkaz sa posudzuje ďalej, preto sa odtiaľto nič nevracia."""
        j = 0
        while j < len(t) and t[j].startswith('-'):
            j += 2 if t[j] in ('-H', '--host', '--context', '-c', '--config', '--log-level', '--connection', '--url',
                               '--root', '--runroot') else 1
        if j >= len(t) or t[j] != 'run':
            return
        j += 1
        while j < len(t) and t[j].startswith('-') and t[j] != '-':
            if t[j] == '--':
                j += 1
                break
            j += skok(t[j], self.DOCKER_S_ARG, self.DOCKER_L_ARG)
        j += 1                       # za obrazom
        if j > len(t) or j > len(args):
            return
        # obraz klienta (curlimages/curl, alpine/curl:latest): spúšťa ho vstupný bod obrazu, meno príkazu v argumentoch
        # nie je — doplní sa, inak by `docker run curlimages/curl -X POST https://…` ostalo len zoznamom volieb
        meno = re.sub(r':[^:/@]*$', '', t[j - 1].split('@')[0]).rstrip('/').split('/')[-1].lower()
        slova = args[j:]
        if meno in ('curl', 'wget', 'http', 'https', 'httpie'):
            w = Slovo()
            w.lit('http' if meno == 'httpie' else meno, False)
            slova = [w] + slova
        if not slova:
            return
        pod = Cmd()
        pod.slova = slova
        pod.heredoc = cmd.heredoc
        pod.herestr = cmd.herestr
        c2 = ctx.kopia(sudo=False)
        c2.vzdial = True
        c2.kontajner = True
        c2.cwd = None
        self.prikaz(pod, c2, None)

    def find_start(self, args, t):
        """Štartovacie cesty príkazu find (bez nich hľadá v .) a index prvého výrazu."""
        j = 0
        while j < len(t):
            x = t[j]
            if x in ('-H', '-L', '-P') or re.fullmatch(r'-O\d*', x):
                j += 1
                continue
            if x == '-D':
                j += 2
                continue
            break
        start = []
        while j < len(t) and not t[j].startswith('-') and t[j] not in ('(', '!', ','):
            start.append(args[j])
            j += 1
        if not start:
            w = Slovo()
            w.lit('.', False)
            start = [w]
        return start, j

    def dosad(self, w, w0, ctx):
        """Slovo w s {} nahradeným nájdenou cestou (štartovacia cesta w0, rozvinutá, keď sa dá — aj v texte pre sh -c)."""
        if not any(sg[0] == 'l' and '{}' in sg[1] for sg in w.seg):
            return w
        k = self.rozvin(w0, ctx)
        vloz = [('l', k[0][0], True)] if len(k) == 1 and k[0][1] is None else list(w0.seg)
        n = Slovo()
        n.kvot, n.kvot_medzera = w.kvot, w.kvot_medzera
        for sg in w.seg:
            if sg[0] == 'l' and '{}' in sg[1]:
                for q, c in enumerate(sg[1].split('{}')):
                    if q:
                        n.seg.extend(vloz)
                    if c:
                        n.seg.append(('l', c, sg[2]))
            else:
                n.seg.append(sg)
        return n

    def find(self, args, t, ctx, sudo):
        """find -delete maže pod štartovacími cestami; -exec/-execdir/-ok/-okdir spúšťa príkaz nad nájdenými cestami
        (`find … -exec sudo rm -rf {} +` je obal ako xargs — KOTVA-1)."""
        start, j = self.find_start(args, t)
        if '-delete' in t[j:] and not ctx.kontajner:
            for w in start:
                v = self.mazanie(w, ctx)
                if v != 'ok':
                    self.kanal(P_RM, K_RM)
                    if v == 'nezname':
                        self.priznaky.add('premenna')
        k = j
        while k < len(t):
            if t[k] in ('-exec', '-execdir', '-ok', '-okdir'):
                m = k + 1
                while m < len(t) and t[m] not in (';', '+'):
                    m += 1
                vnut = args[k + 1:m]
                if vnut and os.path.basename(vnut[0].text()) in ZMENA_PROGRAMY and not ctx.vzdial:
                    for w0 in start[:8]:     # 3. 10. (kolo 4): find … -exec sed -i … {} — zmenené môže byť čokoľvek pod w0
                        self.zmena_slovo(w0, ctx, priecinok=True)
                if vnut:
                    for w0 in start[:8]:
                        pod = Cmd()
                        pod.slova = [self.dosad(w, w0, ctx) for w in vnut]
                        self.prikaz(pod, ctx.kopia(sudo=sudo), None)
                    if not ctx.vzdial and not ctx.kontajner and any('{}' in w.text() for w in vnut) and (
                            '{}' in vnut[0].text() or any(XARGS_INTERPRET.match(os.path.basename(w.text())) for w in vnut)):
                        # 16. 10. (KXA): {} sú nájdené súbory, nie štartovacia cesta — find xa -name '*.sh' -exec bash {} \;
                        # spúšťa každý skript; rozbor dosadzoval len štartovaciu cestu (bash xa) a skript nečítal
                        self.xargs_dosad(vnut, '{}', lambda: self.find_mena(args, t, ctx, False), ctx, sudo)
                k = m + 1
                continue
            k += 1

    # --- 16. 10. (KXA): skript, ktorého meno interpreter dostane zo vstupu xargs alebo z {} vo find -exec ---
    def xargs_skript(self, pw, pb, args, t, nahrada, volby_x, prog, cmd, ctx, sudo, pred):
        """`ls xa/*.sh | xargs -n1 bash`, `… | xargs -I{} sh -c 'bash {}'`: meno skriptu príde zo vstupu, nie z príkazu.
        Rozbor ho predtým nečítal (0,00 s), hoci `bash xa/posli.sh` aj cyklus `for f in xa/*.sh` zastaví. Týka sa len
        interpretra (shell, python, node) alebo programu, ktorého meno je samo zo vstupu (xargs -I{} {}); rm, curl a iné
        majú vlastné pravidlá pre xargs."""
        if prog == 'parallel':
            return                     # 16. 10. (KXP): parallel posudzuje parallel_beh (vstupy za :::, {}, zložený príkaz)
        slova = [pw] + list(args)
        interp = bool(XARGS_INTERPRET.match(pb))
        if nahrada:
            miesta = [k for k, w in enumerate(slova) if any(sg[0] == 'l' and nahrada in sg[1] for sg in w.seg)]
            if not miesta or not (0 in miesta or interp):
                return
        elif not interp:
            return
        self.xargs_dosad(slova, nahrada, lambda: self.xargs_mena(volby_x, prog, cmd, ctx, pred), ctx, sudo)

    def xargs_dosad(self, slova, znak, mena, ctx, sudo):
        """Príkaz slova s menom zo vstupu: na mieste náhradného znaku (xargs -I, {} vo find -exec), inak pripojeným na koniec.
        Najprv sonda so značkou XARGS_VSTUP — keď značka nedôjde na miesto skriptu (python3 -m py_compile, bash -n, kód
        shellu, ktorý vstup len vypíše), meno zo vstupu skript neurčuje a ostáva bežný rozbor. Inak sa príkaz posúdi s každým
        menom, ktoré zdroj privedie (mena() → zoznam), a keď zdroj nevidno (None), skript je nedočítaný (zastaví)."""
        def pod(m):
            c = Cmd()
            if znak:
                c.slova = [self.dosad_text(w, znak, m) for w in slova]
            else:
                n = Slovo()
                n.lit(m, True)
                c.slova = list(slova) + [n]
            return c
        self.vstup_dosad(lambda m: self.prikaz(pod(m), ctx.kopia(sudo=sudo), None), XARGS_VSTUP_SLOVO, mena,
                         os.path.basename(slova[0].text()))

    def vstup_sonda(self, posud):
        """posud() so značkou vstupu → True, keď značka došla na miesto skriptu (skript_cesty)."""
        stare = (self._xargs_sonda, self._xargs_trafene)
        self._xargs_sonda, self._xargs_trafene = True, False
        try:
            posud()
            return self._xargs_trafene
        finally:
            self._xargs_sonda, self._xargs_trafene = stare

    def vstup_dosad(self, posud, znacka, mena, meno):
        """Sonda posud(znacka); keď značka dôjde na miesto skriptu, posud(m) pre každú hodnotu z mena(), a keď hodnoty
        nevidno (None), je skript nedočítaný (zastaví)."""
        if not self.vstup_sonda(lambda: posud(znacka)):
            return
        zoznam = mena()
        if zoznam is None:
            self.skript_nedocitany(meno)
            return
        for m in zoznam:
            posud(m)

    # --- 16. 10. (KXP): GNU parallel — vstupy za :::, náhradné reťazce, zložený príkaz, parallel bez príkazu ---
    def parallel_beh(self, slova, info, cmd, ctx, sudo, pred):
        """Čo parallel spustí. Predtým sa vstupy za ::: brali ako argumenty príkazu (`parallel bash ::: xa/*.sh` spustilo
        bash so skriptom „:::“) a {} rozbor nepoznal, takže skript zo vstupu sa nečítal; príkaz v jednom slove
        (`parallel 'git push {}' ::: origin`) bol názov programu a `parallel ::: 'git push …'` aj `echo 'git push …' |
        parallel` (vstup je príkaz) prešli bez rozboru. Teraz: (A) slová príkazu s dosadeným vstupom ako pri xargs (sonda,
        potom každá hodnota), keď príkaz má interpreter alebo vstup je na mieste programu; (B) text, ktorý parallel spojí
        a spustí shellom (príkaz bez -q so slovom, ktoré shell vyloží inak, a parallel bez príkazu) — s každou hodnotou,
        keď hodnoty vidno, inak so značkou a nedočítaný, keď značka dôjde na miesto skriptu."""
        if info['pipe'] or info['pomoc'] or len(slova) > 400:
            return
        texty = [w.text() for w in slova]
        k = next((q for q, x in enumerate(texty) if x in (info['sep'], info['fsep'], info['sep'] + '+',
                                                          info['fsep'] + '+')), len(slova))
        prikaz_sl = slova[:k]
        zdroje = [None] * len(info['subory'])
        for w, x in zip(slova[k:], texty[k:]):
            if x in (info['sep'], info['sep'] + '+'):
                zdroje.append([])
            elif x in (info['fsep'], info['fsep'] + '+'):
                zdroje.append(None)
            elif zdroje and zdroje[-1] is not None:
                zdroje[-1].append(w)
        if not prikaz_sl and not zdroje and not (cmd.rura or cmd.heredoc or cmd.herestr
                                                  or any(op == '<' for op, _ in cmd.presm)):
            return                     # bez príkazu aj bez vstupu nespustí nič (parallel --version 2>/dev/null | head)
        cache = []

        def rady():
            if not cache:
                cache.append(self.par_rady(zdroje, info, cmd, ctx, pred))
            return cache[0]
        nahrada = info['nahrada']
        znacka = (XARGS_VSTUP_SLOVO,) * max(1, len(zdroje))
        if prikaz_sl:
            interp = any(XARGS_INTERPRET.match(os.path.basename(x)) for x in texty[:k])
            prvy = self.par_kusy(prikaz_sl[0].text(), znacka, nahrada, 0) is not None
            if interp or prvy:
                self.vstup_dosad(lambda r: self.prikaz(self.par_cmd(prikaz_sl, r, nahrada), ctx.kopia(sudo=sudo), None),
                                 znacka, lambda: rady(), os.path.basename(texty[0]))
        if prikaz_sl and (info['quote'] or not (any(sg[0] == 'l' and PAR_SHELL.search(sg[1])
                                                    for w in prikaz_sl for sg in w.seg) or '{=' in ' '.join(texty[:k]))):
            return
        zoznam = rady()
        if zoznam is not None:
            for q, r in enumerate(zoznam):
                self.retazec(self.par_text(prikaz_sl, r, nahrada, q), ctx.kopia(sudo=sudo))
            return
        if self.vstup_sonda(lambda: self.retazec(self.par_text(prikaz_sl, znacka, nahrada, 0), ctx.kopia(sudo=sudo))):
            self.skript_nedocitany(os.path.basename(texty[0]) if texty else 'parallel')

    def par_rady(self, zdroje, info, cmd, ctx, pred):
        """Riadky vstupu parallel (n-tica hodnôt, jedna z každého zdroja): vstupy za ::: (vzor mien rozvinutý na súbory),
        bez ::: riadky z rúry (ls, find, echo/printf). None, keď ich nevidno (::::, -a, cat …) alebo je ich priveľa."""
        if not zdroje and (cmd.heredoc or cmd.herestr) and not cmd.rura and not any(op == '<' for op, _ in cmd.presm):
            riadky = [x for h in [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr]
                      for x in h.split('\n') if x.strip()]
            return None if len(riadky) > SKRIPT_MAX_KANDIDATOV else [(x,) for x in riadky]
        if not zdroje:
            m = self.xargs_mena([], 'parallel', cmd, ctx, pred, riadky=True)
            return None if m is None else [(x,) for x in m]
        hodnoty = []
        for z in zdroje:
            if z is None:
                return None
            v = []
            for w in z:
                g = self.xargs_glob(w, ctx)
                if g is None:
                    return None
                v += g
            hodnoty.append(v)
        pocet = 1
        for v in hodnoty:
            pocet *= len(v)
        if pocet > SKRIPT_MAX_KANDIDATOV:
            return None
        return list(itertools.product(*hodnoty))

    def par_kusy(self, lit, rad, nahrada, poradie):
        """Text lit s náhradnými reťazcami parallel → [(text, je_hodnota)], None keď v ňom žiadny nie je."""
        def hodnota(m):
            if m.group(0) in ('{#}', '{%}'):
                return str(poradie + 1) if XARGS_VSTUP not in rad[0] else XARGS_VSTUP_SLOVO
            if m.group(0).startswith('{='):
                return XARGS_VSTUP_SLOVO   # výraz perlu nad vstupom — hodnotu rozbor nevie (na mieste skriptu nedočítané)
            n, mod = m.group(1), m.group(2)
            zak = (rad[int(n) - 1] if 1 <= int(n) <= len(rad) else '') if n else ' '.join(rad)
            if XARGS_VSTUP in zak:
                return XARGS_VSTUP_SLOVO
            if mod in ('/', '/.'):
                zak = os.path.basename(zak)
            elif mod == '//':
                zak = os.path.dirname(zak) or '.'
            if mod in ('.', '/.'):
                zak = re.sub(r'(?<=[^/])\.[^./]*$', '', zak)
            return zak
        vzor = PAR_NAHRADA if not nahrada or nahrada == '{}' else \
            re.compile(re.escape(nahrada) + r'|\{(\d+)(\.|//|/\.|/)?\}')
        kusy, kde, nasiel = [], 0, False
        for m in vzor.finditer(lit):
            nasiel = True
            if m.start() > kde:
                kusy.append((lit[kde:m.start()], False))
            kusy.append((' '.join(rad) if nahrada and m.group(0) == nahrada else hodnota(m), True))
            kde = m.end()
        if not nasiel:
            return None
        if kde < len(lit):
            kusy.append((lit[kde:], False))
        return kusy

    def par_cmd(self, prikaz_sl, rad, nahrada):
        """(A) slová príkazu s dosadeným riadkom vstupu; bez náhradného reťazca pribudnú hodnoty ako ďalšie slová."""
        c = Cmd()
        pouzite = False
        for w in prikaz_sl:
            if not any(sg[0] == 'l' and self.par_kusy(sg[1], rad, nahrada, 0) is not None for sg in w.seg):
                c.slova.append(w)
                continue
            pouzite = True
            n = Slovo()
            n.kvot, n.kvot_medzera, n.tilda = w.kvot, w.kvot_medzera, w.tilda
            mapa = {}
            for k, sg in enumerate(w.seg):
                kusy = self.par_kusy(sg[1], rad, nahrada, 0) if sg[0] == 'l' else None
                if kusy is None:
                    mapa[k] = len(n.seg)
                    n.seg.append(sg)
                    continue
                for text, hod in kusy:
                    if text:
                        n.seg.append(('l', text, True if hod else sg[2]))
            if w.v_uvod:
                n.v_uvod = {mapa[k] for k in w.v_uvod if k in mapa}
            if w.viac:
                n.viac = {mapa[k] for k in w.viac if k in mapa}
            if w.predvol:
                n.predvol = {mapa[k]: v for k, v in w.predvol.items() if k in mapa}
            c.slova.append(n)
        if not pouzite:
            for v in rad:
                n = Slovo()
                n.lit(v, True)
                c.slova.append(n)
        return c

    def par_text(self, prikaz_sl, rad, nahrada, poradie):
        """(B) text, ktorý parallel spustí shellom: slová príkazu spojené medzerou, hodnoty obalené; bez príkazu je
        príkazom sám riadok vstupu."""
        if not prikaz_sl:
            return ' '.join(rad)
        text = ' '.join(w.text() for w in prikaz_sl)    # {= … =} býva rozdelený medzerami na viac slov
        kusy = self.par_kusy(text, rad, nahrada, poradie)
        if kusy is None:
            return ' '.join([text] + [sh_uvod(v) for v in rad])
        return ''.join(sh_uvod(t) if hod else t for t, hod in kusy)

    def kod_argv_subor(self, pb, args, t, ctx, sudo):
        """16. 10. (KXP): kód v príkaze, ktorý spustí súbor z argumentu — `python3 -c 'exec(open(sys.argv[1]).read())' f`,
        runpy.run_path(sys.argv[1]), `node -e 'require(process.argv[1])' f` — sa posúdi ako `python3 f` / `node f`.
        Predtým sa čítal len kód (súbor z argumentu nevidel), takže `ls xb/*.py | xargs -n1 python3 -c 'import sys;
        exec(open(sys.argv[1]).read())'` prešlo, hoci ten istý kód s doslovnou cestou kotva zastaví."""
        jaz = 'py' if re.match(r'^python[0-9.]*$', pb) else ('js' if pb in ('node', 'nodejs') else None)
        if not jaz or ctx.vzdial or ctx.kontajner:
            return
        k, q = None, 0
        while q < len(t) and k is None:       # voľby interpretra pred kódom; prvé pozičné slovo je skript (-c nie je jeho)
            x = t[q]
            if x in KOD_VOLBY[jaz] or (jaz == 'py' and re.fullmatch(r'-[A-Za-z]*c', x)):
                k = q
            elif x in ('-W', '-X', '-r', '--require', '--import', '--loader', '--experimental-loader', '--env-file'):
                q += 1
            elif x == '-m' or not x.startswith('-') or x == '-':
                return
            q += 1
        if k is None or k + 1 >= len(t):
            return
        kod = t[k + 1]
        spusti = bool(KOD_ARGV_SPUSTI[jaz].search(kod))
        ako_kod = bool(KOD_ARGV_KOD[jaz].search(kod))    # 16. 10. (KXK): argument sám je kód (kod_z_argumentu)
        if not (spusti or ako_kod):
            return
        zvysok = list(args[k + 2:])
        kand = []
        rezy = []
        for m in KOD_ARGV[jaz].finditer(kod):
            if jaz == 'js' and m.group(3) is not None:
                n, rez = int(m.group(3)), True
            else:
                n, rez = int(m.group(1)), bool(m.group(2))
            if rez:
                kand += zvysok[max(n - 1, 0):]
                rezy.append(zvysok[max(n - 1, 0):])
            elif n >= 1:
                kand += zvysok[n - 1:n]
            elif n < 0:
                kand += zvysok[n:][:1]
        if not KOD_ARGV[jaz].search(kod) and re.search(r'\bargv\b', kod):
            kand = zvysok                 # argv s indexom, ktorý rozbor nečíta (argv[i]) — každý argument
        videne = set()
        for w in kand:
            if id(w) in videne:
                continue
            videne.add(id(w))
            if spusti:
                c = Cmd()
                p = Slovo()
                p.lit('python3' if jaz == 'py' else 'node', False)
                c.slova = [p, w]
                self.prikaz(c, ctx.kopia(sudo=sudo), None)
            if ako_kod:
                self.kod_z_argumentu(jaz, [w], ctx, sudo)
        if ako_kod:
            for r in rezy:
                if len(r) > 1:        # argumenty spojené do jedného kódu: eval(process.argv.slice(1).join(' '))
                    self.kod_z_argumentu(jaz, r, ctx, sudo)

    def kod_z_argumentu(self, jaz, slova, ctx, sudo):
        """16. 10. (KXK): kód, ktorý python -c / node -e dostane ako argument a spustí — `python3 -c 'import sys;
        exec(sys.argv[1])' '<kód>'`, eval, compile; `node -e 'eval(process.argv[1])' '<kód>'`, new Function, vm.runIn… —
        sa posúdi ako ten istý kód priamo za -c/-e. Predtým rozbor čítal len súbor z argumentu (kod_argv_subor), takže kód
        s poštou či odoslaním prešiel, hoci priamo za -c ho kotva zastaví. Argument, ktorého hodnotu rozbor nevidí
        (premenná, $(…)), je nedočítaný; $(cat f) sa číta ako súbor f, $(echo '…') ako jeho text; hodnota zo vstupu
        xargs/parallel (značka v sonde) sa posúdi s každou hodnotou, ktorú zdroj privedie (vstup_dosad)."""
        interp = 'python3' if jaz == 'py' else 'node'
        hodnoty = []
        for w in slova:
            k = self.rozvin(w, ctx)
            if any(XARGS_VSTUP in c for c, _ in k):
                if self._xargs_sonda:
                    self._xargs_trafene = True
                else:
                    self.skript_nedocitany('kód z argumentu')
                return
            zs = self.subor_zo_substitucie(w)
            if zs is not None:
                if len(slova) == 1:
                    c = Cmd()
                    p = Slovo()
                    p.lit(interp, False)
                    c.slova = [p, zs]
                    self.prikaz(c, ctx.kopia(sudo=sudo), None)
                return
            te = self.text_zo_echa(w, ctx)
            if te is not None:
                hodnoty.append([te])
                continue
            if not k or len(k) > 16 or any(u is not None or NEZNAME in c for c, u in k):
                self.skript_nedocitany('kód z argumentu')
                return
            hodnoty.append(sorted({c for c, _ in k}))
        if len(slova) > 1:
            if any(len(h) != 1 for h in hodnoty):
                return                # každý argument zvlášť už posúdil kod_argv_subor
            texty = [' '.join(h[0] for h in hodnoty)]
        else:
            texty = hodnoty[0]
        for kod in texty:
            if not kod.strip():
                continue
            c = Cmd()
            p, v, s = Slovo(), Slovo(), Slovo()
            p.lit(interp, False)
            v.lit('-c' if jaz == 'py' else '-e', False)
            s.lit(kod, True)
            c.slova = [p, v, s]
            self.prikaz(c, ctx.kopia(sudo=sudo), None)

    def dosad_text(self, w, znak, hodnota):
        """Slovo w s náhradným znakom (xargs -I, {} vo find -exec) nahradeným menom zo vstupu — doslovne, bez delenia."""
        if not any(sg[0] == 'l' and znak in sg[1] for sg in w.seg):
            return w
        n = Slovo()
        n.kvot, n.kvot_medzera, n.tilda = w.kvot, w.kvot_medzera, w.tilda
        mapa = {}
        for k, sg in enumerate(w.seg):
            if sg[0] == 'l' and znak in sg[1]:
                for q, c in enumerate(sg[1].split(znak)):
                    if q:
                        n.seg.append(('l', hodnota, True))
                    if c:
                        n.seg.append(('l', c, sg[2]))
            else:
                mapa[k] = len(n.seg)
                n.seg.append(sg)
        if w.v_uvod:
            n.v_uvod = {mapa[k] for k in w.v_uvod if k in mapa}
        if w.viac:
            n.viac = {mapa[k] for k in w.viac if k in mapa}
        if w.predvol:
            n.predvol = {mapa[k]: v for k, v in w.predvol.items() if k in mapa}
        return n

    def xargs_mena(self, volby_x, prog, cmd, ctx, pred, riadky=False):
        """Mená, ktoré xargs/parallel dostane na vstup, keď sa dajú vidieť: ls, find, echo/printf, aj cez filter (sort, uniq,
        head, tail, tac, shuf), ktorý mená len vyberie alebo preusporiada. Inak None (zoznam v súbore, cat, git, grep …)."""
        riadky_x = False
        if prog == 'xargs':
            j = 0
            while j < len(volby_x):
                x = volby_x[j]
                if x.startswith('--arg-file'):
                    return None
                if x.startswith(('--null', '--delimiter', '--replace')):
                    riadky_x = True
                if x.startswith('-') and not x.startswith('--'):
                    for ch in x[1:]:
                        if ch == 'a':
                            return None
                        if ch in '0diI':
                            riadky_x = True   # 16. 10. (KXK): xargs -d '\n', -0, -I odovzdá celý riadok, nie slová
                        if ch in 'dEILnPs':
                            break
                j += skok(x, 'adEILnPs', XARGS_L_ARG)
        if any(op == '<' for op, _ in cmd.presm) or not cmd.rura or pred is None:
            return None
        zdroj = pred
        for _ in range(8):
            if zdroj.podshell is not None or zdroj.vnorene or zdroj.heredoc or zdroj.herestr:
                return None
            sl = sh_bez_klucovych(zdroj.slova)
            k = 0
            while k < len(sl) and self.je_priradenie(sl[k]):
                k += 1
            sl = sl[k:]
            if not sl:
                return None
            p = os.path.basename(sl[0].text())
            a = sl[1:]
            tt = [w.text() for w in a]
            if p in XARGS_FILTRE:
                hodnota = XARGS_FILTRE[p]
                q = 0
                while q < len(tt):
                    if tt[q] in hodnota:
                        q += 2
                        continue
                    if not tt[q].startswith('-') or tt[q] == '-' or tt[q].startswith(('-o', '--output', '-e', '--echo',
                                                                                         '-i', '--input-range', '--files0')):
                        return None    # filter číta súbor, píše do súboru alebo vypisuje vlastné slová — nie výber z rúry
                    q += 1
                par = self._rura_pred.get(id(zdroj))
                if not zdroj.rura or par is None or any(op == '<' for op, _ in zdroj.presm):
                    return None
                zdroj = par[1]
                continue
            if p == 'ls':
                return self.ls_mena(a, tt, ctx)
            if p == 'find':
                return self.find_mena(a, tt, ctx, True)
            if p in ('echo', 'printf'):
                return self.echo_mena(p, a, tt, ctx, riadky or ('oboje' if riadky_x else False))
            return None
        return None

    def xargs_glob(self, w, ctx):
        """Slovo zdroja mien (ls, echo) → texty, ako ich shell podá programu (vzor mien rozvinutý na súbory, ktoré naozaj
        sú); None, keď hodnotu nevidno. Značka vstupu vonkajšieho xargs v sonde → [] a sonda je trafená."""
        kk = self.rozvin(w, ctx)
        if not kk or any(u is not None for _, u in kk):
            return None
        if any(XARGS_VSTUP in c for c, _ in kk):
            if self._xargs_sonda:
                self._xargs_trafene = True
                return []
            return None
        vzor = any(sg[0] == 'l' and not sg[2] and re.search(r'[*?[]', sg[1]) for sg in w.seg)
        von = []
        for c, _ in kk:
            if not vzor or not re.search(r'[*?[]', c):
                von.append(c)
                continue
            p = self.absolutna(c, ctx)
            if not p or not self.smie(re.split(r'[*?[]', p, maxsplit=1)[0]):
                return None
            try:
                najdene = sorted(glob.glob(c) if c.startswith('/') else glob.glob(c, root_dir=ctx.cwd))
            except (OSError, ValueError, re.error, TypeError):
                return None
            if len(najdene) > SKRIPT_MAX_KANDIDATOV:
                return None
            von += [g for g in najdene if self.smie(self.absolutna(g, ctx) or '')] if najdene else [c]
        return von

    def ls_mena(self, a, tt, ctx):
        """Mená, ktoré vypíše ls: súbor alebo vzor tak, ako je zapísaný; priečinok (bez -d) holé mená svojho obsahu."""
        kratke = ''.join(x[1:] for x in tt if x.startswith('-') and not x.startswith('--') and x != '-')
        dlhe = [x for x in tt if x.startswith('--')]
        if set(kratke) - XARGS_LS_VOLBY or any(not x.startswith(XARGS_LS_DLHE) for x in dlhe):
            return None
        skryte = bool(set(kratke) & set('aAf')) or any(x in ('--all', '--almost-all') for x in dlhe)
        len_meno = 'd' in kratke or '--directory' in dlhe
        texty = []
        poz = [w for w, x in zip(a, tt) if not x.startswith('-') or x == '-']
        for w in poz:
            g = self.xargs_glob(w, ctx)
            if g is None:
                return None
            texty += g
        if not poz:
            texty = ['.']
        von = []
        for c in texty:
            p = self.absolutna(c, ctx)
            if not p or not self.smie(p):
                return None
            if os.path.isdir(p) and not len_meno:
                try:
                    von += [f for f in sorted(os.listdir(p)) if skryte or not f.startswith('.')]
                except OSError:
                    pass
            elif os.path.lexists(p):
                von.append(c)
            if len(von) > SKRIPT_MAX_KANDIDATOV:
                return None
        return von

    def echo_mena(self, prog, a, tt, ctx, riadky=False):
        """Slová, ktoré echo/printf vypíše (xargs delí vstup podľa medzier a riadkov); formát printf ostane ako slovo navyše.
        riadky (KXP, parallel): vstup sa delí len podľa riadkov — echo spojí argumenty medzerou, printf každý formátom.
        riadky='oboje' (KXK, xargs -d '\\n', -0, -I): slová aj celé riadky — kód z printf príde do python -c/node -e celý."""
        von = []
        zac = True
        texty = []
        for w, x in zip(a, tt):
            if zac and prog == 'echo' and re.fullmatch(r'-[neE]+', x):
                continue
            zac = False
            g = self.xargs_glob(w, ctx)
            if g is None:
                return None
            for c in g:
                von += c.replace('\\n', '\n').replace('\\t', '\t').split()
                texty.append(c.replace('\\n', '\n').replace('\\t', '\t'))
        if riadky:
            rr = [r for r in (' '.join(texty) if prog == 'echo' else '\n'.join(texty)).split('\n') if r.strip()]
            von = rr if riadky is True else list(dict.fromkeys(von + rr))
        return None if len(von) > SKRIPT_MAX_KANDIDATOV else von

    def find_mena(self, a, tt, ctx, vystup):
        """Súbory, ktoré find nájde pod štartovacími cestami (cesty tak, ako ich vypíše: štart + zvyšok): podľa -name/-iname
        (pri ! alebo -not bez výberu podľa mena), -maxdepth, -mindepth; ostatné testy sa nevyhodnocujú (súborov je viac,
        nie menej). Priečinky nie — skript je súbor. vystup: find vypisuje do rúry (pri -printf, -ls … nie cesty → None).
        None aj vtedy, keď štart nevidno, je v cudzom domove, alebo položiek je priveľa."""
        start, j = self.find_start(a, tt)
        vyraz = tt[j:]
        vzory, negacia, maxd, mind = [], False, None, 0
        k = 0
        while k < len(vyraz):
            x = vyraz[k]
            if x in ('-exec', '-execdir', '-ok', '-okdir'):
                while k < len(vyraz) and vyraz[k] not in (';', '+'):
                    k += 1
                k += 1
                continue
            if vystup and x in XARGS_FIND_VYSTUP:
                return None
            if x in ('!', '-not'):
                negacia = True
            elif x in ('-name', '-iname') and k + 1 < len(vyraz):
                vzory.append((vyraz[k + 1], x == '-iname'))
                k += 2
                continue
            elif x in ('-maxdepth', '-mindepth') and k + 1 < len(vyraz):
                try:
                    h = int(vyraz[k + 1])
                except ValueError:
                    return None
                if x == '-maxdepth':
                    maxd = h
                else:
                    mind = h
                k += 2
                continue
            elif x in XARGS_FIND_ARG:
                k += 2
                continue
            k += 1
        if negacia or any('$' in v or NEZNAME in v for v, _ in vzory):
            vzory = []

        def vyhovuje(f):
            if not vzory:
                return True
            return any(re.fullmatch(vzor_mena(v), f, re.I if ic else 0) for v, ic in vzory)
        von, prechod = [], 0
        for w in start:
            g = self.xargs_glob(w, ctx)
            if g is None:
                return None
            for s in g:
                sa = self.absolutna(s, ctx)
                if not sa or not self.smie(sa):
                    return None
                if os.path.isfile(sa):
                    if mind == 0 and vyhovuje(os.path.basename(sa)):
                        von.append(s)
                    continue
                if not os.path.isdir(sa):
                    continue
                for koren, dirs, files in os.walk(sa):
                    rel = os.path.relpath(koren, sa)
                    hl = 0 if rel == '.' else rel.count(os.sep) + 1
                    prechod += len(dirs) + len(files)
                    if prechod > XARGS_MAX_PRECHOD:
                        return None
                    dirs.sort()
                    if maxd is not None and hl + 1 >= maxd:
                        dirs[:] = []
                    if (maxd is not None and hl + 1 > maxd) or hl + 1 < mind:
                        continue
                    for f in sorted(files):
                        if vyhovuje(f):
                            von.append(os.path.join(s, f) if rel == '.' else os.path.join(s, rel, f))
                    if len(von) > SKRIPT_MAX_KANDIDATOV:
                        return None
        return von

    def zdroj_xargs(self, pred, ctx):
        """Cesty, ktoré do `… | xargs rm -r` posiela predchádzajúci príkaz, keď sa dajú vidieť: find (štartovacie cesty),
        echo/printf (argumenty). Inak None — ako doteraz sa neposudzuje nič."""
        slova = sh_bez_klucovych(pred.slova) if pred is not None else []
        if not slova:
            return None
        p = os.path.basename(slova[0].text())
        t = [w.text() for w in slova[1:]]
        if p == 'find':
            return self.find_start(slova[1:], t)[0]
        if p in ('echo', 'printf'):
            return [w for w, x in zip(slova[1:], t) if not x.startswith('-')] or None
        return None

    def su(self, p, sl, t, cmd, ctx):
        c2 = ctx.kopia(sudo=True)
        j = 0
        prikaz = None
        zvysok = None
        uzivatel = False
        while j < len(t):
            x = t[j]
            if x in ('-c', '--command') and j + 1 < len(t):
                prikaz = sl[j + 1].text()
                j += 2
                continue
            if x.startswith('--command='):
                prikaz = x.split('=', 1)[1]
                j += 1
                continue
            if x == '--':
                zvysok = j + 1
                break
            if x in ('-u', '--user'):
                uzivatel = True
                j += 2
                continue
            if x in ('-s', '--shell', '-g', '--group', '-G', '--supp-group', '-w', '--whitelist-environment'):
                j += 2
                continue
            if p == 'runuser' and uzivatel and not x.startswith('-'):
                zvysok = j
                break
            j += 1
        if prikaz is not None:
            self.retazec(prikaz, c2)
        elif zvysok is not None and zvysok < len(sl):
            pod = Cmd()
            pod.slova = sl[zvysok:]
            pod.presm = cmd.presm
            pod.heredoc = cmd.heredoc
            self.prikaz(pod, c2, None)
        elif cmd.heredoc:
            for h, _ in cmd.heredoc:
                self.retazec(h, c2)
        else:
            self.kanal(P_SUDO, K_SUDO)

    def presmerovania(self, cmd, ctx, sudo):
        if not ctx.vzdial:
            for op, w in cmd.presm:
                if op in VYSTUP and any(self.cudzi(self.absolutna(c, ctx) or '') for c in self.kandidati_ciest(w, ctx) if c):
                    self.kanal(P_DOMOV, K_DOMOV)
        if sudo and self.zapis_presmerovanim(cmd, ctx):
            self.kanal(P_SUDO, K_SUDO)

    def zapis_presmerovanim(self, cmd, ctx):
        """Výstup do súboru mimo /dev/null, /tmp a vlastného domova (presmerovanie robí shell používateľa, nie root)."""
        for op, w in cmd.presm:
            if op not in VYSTUP:
                continue
            for c, u in self.rozvin(w, ctx):
                if u is not None:
                    return True
                p = self.absolutna(c, ctx)
                if not p:
                    return True
                if p in ('/dev/null', '/dev/stdout', '/dev/stderr', '/dev/tty') or p.startswith('/dev/fd/'):
                    continue
                if p.startswith('/tmp/') or p.startswith(self.domov + '/'):
                    continue
                return True
        return False

    def git(self, args, t, ctx):
        sub, j, C, gd, ma_c = git_rozbor(t)
        r = t[j + 1:]
        if sub == 'push':
            v = git_push_volby(r)
            if v['nasucho'] or v['pomoc']:
                return      # --dry-run, -n, --help: nič sa neodošle (24. 9.: kotva ich zastavovala mimo domova)
            if v['force']:
                self.kanal(P_FORCE, K_FORCE)
            # zmazanie vetvy/značky na GitHube: --delete, -d, --prune, refspec „:vetva“ (24. 9., úloha 2)
            if v['zmaz'] or any(p.lstrip('+').startswith(':') and len(p.lstrip('+')) > 1 for p in v['poz'][1:]):
                self.kanal(P_PUSH_ZMAZ, K_PUSH_ZMAZ)
            # ktorý repozitár sa pushuje: cwd (aj po cd v tom istom príkaze), -C, --git-dir, GIT_DIR (24. 9., úloha 2).
            # Keď sedenie beží mimo domova (cwd v cudzom priečinku), cd sa neberie do úvahy — nemusí sa uplatniť
            # (v rúre, na pozadí, v neprebehnutej vetve, zlyhané) a push by išiel z cudzieho priečinka; rozhoduje len
            # výslovný repozitár pri pushi (nález 24. 9.; 10.06 zastavila každý push pri cwd mimo domova).
            vlastny_hook = not self.hook_cwd or self.vlastne_miesto(self.hook_cwd)
            zaklad = ctx.cwd if vlastny_hook else self.hook_cwd
            adr = zaklad
            nezname = adr is None
            gitdir_nezname = False

            def cesta(w, zaklad):
                kd = self.rozvin(w, ctx)[0]
                if kd[1] is None:
                    c0, cela = kd[0], True
                else:
                    pref = kd[0][:kd[1]]
                    c0, cela = pref[:pref.rfind('/') + 1], False
                if c0.startswith('/'):
                    return os.path.normpath(c0), cela
                if not c0 or not zaklad:
                    return None, False
                return os.path.normpath(os.path.join(zaklad, c0)), cela

            for k in C:
                adr, cela = cesta(args[k], adr)
                nezname = adr is None
            gw = None
            if gd is not None:
                if isinstance(gd, int):
                    gw = args[gd]
                else:                   # --git-dir=… (text slova; premenná v ňom = neznáma cesta)
                    gw = Slovo()
                    if '$' in gd or '`' in gd:
                        gw.seg.append(('v', '__nezname__'))
                    else:
                        gw.lit(gd, True)
            elif 'GIT_DIR' in ctx.vars:
                hod = ctx.vars['GIT_DIR']
                gw = Slovo()
                if hod and len(hod) == 1 and NEZNAME not in hod[0]:
                    gw.lit(hod[0], True)
                else:
                    gw.seg.append(('v', '__nezname__'))
            if gw is not None:
                g, cela = cesta(gw, adr)
                if g is None:
                    gitdir_nezname = True
                else:
                    adr, nezname = g, False
            if gitdir_nezname:
                self.kanal(P_PUSH, 'push do repozitára mimo tvojich klonov (cesta repozitára je v premennej)')
            elif not nezname and adr:
                druh = self.druh_repa(adr)
                if druh:
                    self.kanal(*druh)
            elif not vlastny_hook:
                self.kanal(P_PUSH, f'push do repozitára mimo tvojich klonov ({self.hook_cwd})')
        elif sub in ('filter-repo', 'filter-branch'):
            if not git_filter_nic(sub, r):        # 2. 10.: samotné --version, --help, filter-repo --analyze nič neprepíšu
                self.kanal(P_HIST, K_HIST)
        elif sub == 'reflog' and r[:1] == ['expire']:
            self.kanal(P_HIST, K_HIST)
        elif sub == 'gc' and '--prune=now' in r:
            self.kanal(P_HIST, K_HIST)

    def uid_domova(self):
        if self._uid is None:
            # 2. 10. večer: domov v /home/<meno> — uid z mena (passwd), nie stat cudzieho domova (prehrávka kolegov)
            m = re.fullmatch(r'/home/([^/]+)', self.domov)
            try:
                self._uid = pwd.getpwnam(m.group(1)).pw_uid if m else os.stat(self.domov).st_uid
            except (KeyError, OSError):
                self._uid = os.getuid()
        return self._uid

    def vlastne_tmp(self, p):
        """Priečinok v /tmp, ktorý patrí človeku, čo sedí (jeho pieskovisko): najbližšia existujúca časť cesty pod /tmp
        je jeho, alebo ešte neexistuje (vytvorí ho tento príkaz). Cudzí priečinok v /tmp alebo odkaz mimo /tmp nie."""
        p = os.path.normpath(p)
        if p != '/tmp' and not p.startswith('/tmp/'):
            return False
        try:
            r = os.path.realpath(p)
        except (OSError, ValueError):
            r = p
        if r != '/tmp' and not r.startswith('/tmp/'):
            return r == self.domov or r.startswith(self.domov + '/')
        q = r
        while q not in ('/tmp', '/', ''):
            try:
                return os.lstat(q).st_uid == self.uid_domova()
            except FileNotFoundError:
                pass
            except OSError:          # do cudzieho priečinka nevidno — rozhodne vlastník priečinka nad ním
                pass
            q = os.path.dirname(q)
        return True

    def vlastne_miesto(self, p):
        p = os.path.normpath(p)
        return p == self.domov or p.startswith(self.domov + '/') or self.vlastne_tmp(p)

    def druh_repa(self, adr):
        """Kanál pushu podľa repozitára: None = vlastný klon (domov človeka, ktorý sedí, alebo jeho pieskovisko v /tmp —
        24. 9.: 14 zastavení skúšok rozvozu s holým origin v scratchpade, ktoré 10.06 púšťala)."""
        if adr == self.domov or adr.startswith(self.domov + '/'):
            return None
        if self.cudzi(adr):
            return (P_PUSH_CUDZI, K_PUSH_CUDZI)
        if self.vlastne_tmp(adr):
            return None
        return (P_PUSH, f'push do repozitára mimo tvojich klonov ({adr})')

    def posta(self, pb, pv, args, t, prefix, cmd, ctx, xargs=False):
        if pb == 'git':
            if git_rozbor(t)[0] == 'send-email':
                self.kanal(P_POSTA, K_POSTA)
            return
        if pb in POSTA_PROG:
            self.kanal(P_POSTA, K_POSTA)
            return
        if neposiela(pb, t):
            return
        # 2. 10.: exo-rest.py (správa Exchange Online, EXO_* vyššie) — zápisový alebo neznámy cmdlet a nástroj, ktorý kotva
        # neoverí, zastaví vždy (aj s cestou z for, ktorú stará kotva nevidela); čítanie overeného nástroja nie je odoslanie
        exo = None if self.exo_vypnute else self.exo_volanie(pv, args, ctx, xargs)
        if exo == 'cita' and any(NASTROJ_ZLA_PREMENNA.match(m) for m in ctx.vars):
            exo = 'nezname'     # PATH, PYTHONPATH, LD_* … nastavené v príkaze: nástroj môže spustiť iný kód
        if exo in ('zapis', 'nezname'):
            self.kanal(P_POSTA, K_POSTA)
            return
        interp = bool(INTERPRETY.match(pb))
        skript = None           # index skriptu interpretra (python3 send.py) — prvý pozičný argument
        if interp:
            k = 0
            while k < len(t):
                x = t[k]
                if x in ('-c', '-e', '-r', '--eval', '-Command', '-command', '-m', '-W', '-X', '--require'):
                    if x in ('-m', '-c', '-e', '--eval', '-Command', '-command'):
                        break
                    k += 2
                    continue
                if x.startswith('-'):
                    k += 1
                    continue
                skript = k
                break
        if pb in SHELLY:         # bash skript.sh: skript je program, ďalšie slová sú jeho dáta
            # 29. 9.: voľba s hodnotou (bash -euo pipefail skript.sh, +o …) — skript nie je jej hodnota
            skript = skript_interpretra(pb, t)
        texty = [pv] + [w.text() for w in prefix]
        for k, w in enumerate(args):
            if w.kvot_medzera and pb not in SIETOVE:
                continue   # veta v úvodzovkách nie je názov skriptu ani adresa
            if self.len_program and k != skript and pb not in SIETOVE:
                # skript z disku (dočasný v /tmp): slová pri neznámom príkaze sú väčšinou dáta — v červenom teste
                # `ocakavaj deny "$P" '/usr/local/sbin/aig-crit-notify.sh'` (24. 9.); poštové volanie áno
                if ZNACKY_ARG.search(w.text()):
                    texty.append(w.text())
                continue
            if interp and k != skript:
                # dáta pre skript (python3 uprav.py aig-crit-notify.sh je úprava súboru); poštové volanie áno
                if ZNACKY_ARG.search(w.text()):
                    texty.append(w.text())
                continue
            texty.append(w.text())
        hity = [x for x in texty if ZNACKY_POSTY.search(x)]
        # 2. 10.: čítanie Exchange cez overený exo-rest.py prejde, keď v príkaze nie je iná poštová značka a nad príkazom
        # nevisí poštová premenná (posta_env)
        if ctx.posta_env or (hity and (exo != 'cita' or any(ZNACKY_POSTY.search(EXO_NASTROJ.sub('', x)) for x in hity))):
            self.kanal(P_POSTA, K_POSTA)
            return
        # 29. 9.: poštový nástroj podľa mena (…mail… — graph_mail.py, send_mail.sh …) s pokynom odoslať medzi
        # argumentmi = odoslanie pošty. `python3 ~/.config/aigrip/graph_mail.py send --to …` odoslal mail, kotva ho
        # nepoznala (v zozname boli len sendmail, exo-rest …). draft, whoami, list, read prejdú.
        # 29. 9. (nález nezávislého overenia): meno sa bralo z doslovného slova, takže cesta v premennej ho skryla —
        # `GM=~/.config/aigrip/graph_mail.py; python3 $GM send --to …` odoslal mail; meno je z rozvinutej cesty (aj
        # čiastočne známej: "$D/graph_mail.py"), doslovné slovo platí popri nej
        if skript is not None and skript < len(t):
            _mena = [os.path.basename(c.replace(NEZNAME, '')) for c, _ in self.rozvin(args[skript], ctx)] + [os.path.basename(t[skript])]
            # 29. 9. večer: cesta z náhrady — `python3 $(ls ~/.config/aigrip/graph_mail.py) send …` (druhé kolo overenia)
            _mena += [os.path.basename(m) for sg in args[skript].seg if sg[0] == 's' and sg[1]
                      for c2 in sg[1] for w2 in c2.slova for m in POSTA_NASTROJ_KOD.findall(w2.text())]
        else:
            _mena = [pb]
        _od = skript + 1 if skript is not None else 0
        # 29. 9. večer: poštový modul cez -m (python3 -m graph_mail send …) a poštový nástroj v kóde -c s pokynom odoslať
        # medzi argumentmi skriptu (python3 -c "…runpy.run_path('…/graph_mail.py', run_name='__main__')" send …)
        if interp and re.match(r'^python', pb):
            for k, x in enumerate(t):
                if x == '-m' and k + 1 < len(t):
                    if POSTA_NASTROJ.search(t[k + 1]) and t[k + 1].split('.')[0].lower() not in POSTA_STDLIB:
                        _mena.append(t[k + 1])
                    _od = k + 2
                    break
                if x == '-c' and k + 1 < len(t):
                    if POSTA_NASTROJ_KOD.search(t[k + 1]) or KOD_NACITA_POSTU_DYN.search(t[k + 1]):
                        _mena.append('mail')
                    _od = k + 2
                    break
                if not x.startswith('-'):
                    break
        if any(POSTA_NASTROJ.search(_nm) for _nm in _mena) and any(x.lower().lstrip('-') in POSTA_POKYN for x in t[_od:]) \
                and not self.posta_pomoc(pb, args, skript, t[_od:], ctx):
            self.kanal(P_POSTA, K_POSTA)
            return
        if interp:
            kod = [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr]
            for k, x in enumerate(t):
                if x in ('-c', '-e', '-r', '--eval', '-Command', '-command') and k + 1 < len(t):
                    kod.append(t[k + 1])
            if skript is not None:      # skript zapísaný v tomto príkaze alebo dočasný v /tmp
                obs = self.obsah_skriptu(args[skript], ctx)
                if obs is not None:
                    kod.append(obs)
            cita = skript is not None and any(POSTA_NASTROJ.search(n) for n in _mena) and skript + 1 < len(t) \
                and t[skript + 1].lower().lstrip('-') in POSTA_CITA
            if self.kod_posiela(kod, ctx, kod_jazyk(pb), nastroj_meno=not cita):
                self.kanal(P_POSTA, K_POSTA)

    # --- exo-rest.py: čítanie Exchange nie je odoslanie (2. 10., EXO_* vyššie) ---
    def exo_cesta(self, c, ctx):
        """Známa cesta k nástroju platformy na správu Exchange (overený súbor; relatívna k priečinku príkazu) →
        (overený, mená funkcií s parametrami)."""
        if not c or NEZNAME in c or os.path.basename(c) != EXO_MENO or ctx is None or ctx.vzdial or ctx.kontajner:
            return False, frozenset()
        p = self.absolutna(c, ctx)
        self.skript_stav['citania'] += 1
        return exo_nastroj(p) if p and self.smie(p) else (False, frozenset())

    def exo_volanie(self, pv, args, ctx, xargs=False):
        """Príkaz spúšťa exo-rest.py: None (nespúšťa ho — rozhodne zvyšok rozboru) · 'zapis' (cmdlet nie je doslovný čítací,
        alebo ho doplní xargs/parallel) · 'nezname' (čítací cmdlet, ale cestu či súbor kotva neoverí) · 'cita'. Príkaz
        v premennej sa rozdelí na slová ($X s hodnotou „python3 …/exo-rest.py“)."""
        prvy = (pv.split() if pv and any(ch.isspace() for ch in pv) else [pv]) or ['']
        argv = [[x] for x in prvy] + [[c if u is None else c[:u] + NEZNAME for c, u in self.rozvin(w, ctx)] for w in args]
        k = 0
        interaktivny = False
        if re.fullmatch(r'python[0-9.]*', os.path.basename(prvy[0])):
            k = 1
            while k < len(argv):           # voľby interpretra; neznáme slovo môže byť sám skript (posúdi sa nižšie)
                v = argv[k]
                if len(v) != 1 or NEZNAME in v[0] or not v[0].startswith('-'):
                    break
                if v[0] == '-' or v[0].startswith(('-c', '-m')):
                    return None    # kód v -c, modul alebo vstup — nie spustenie súboru exo-rest.py
                # overenie 2. 10.: -i (aj zlúčené -ui, -iu) — po skripte interaktívny režim vykoná kód zo vstupu
                # (heredoc, rúra, here-string), napr. run('New-Mailbox', …); hodnota pri -W/-X sa nepočíta
                if not v[0].startswith('--') and v[0][:2] not in ('-W', '-X') and 'i' in v[0][1:]:
                    interaktivny = True
                k += 2 if v[0] in ('-W', '-X') else 1
        if k >= len(argv) or not any(os.path.basename(c.replace(NEZNAME, '')) == EXO_MENO for c in argv[k]):
            return None
        if interaktivny:
            return 'nezname'
        cm = argv[k + 1] if k + 1 < len(argv) else None
        if xargs or (cm is not None and not all(NEZNAME not in c and (c in EXO_POMOC or EXO_CITA.fullmatch(c)) for c in cm)):
            return 'zapis'
        return 'cita' if all(self.exo_cesta(c, ctx)[0] for c in argv[k]) else 'nezname'

    def exo_odkaz(self, cmd, ctx):
        """Premenná s cestou k exo-rest.py (Ctx.posta_exo) smie byť len programom alebo skriptom priameho spustenia nástroja
        (exo_priame_slovo — o cmdlete potom rozhodne exo_volanie). Každé iné jej použitie — echo, printf -v, cp, cat,
        argument iného programu, náhrada $(…), presmerovanie, here-string, heredoc bez úvodzoviek, podshell, slovo s iným
        textom — je únik: rozober() posúdi celý príkaz znova ako pred 2. 10. (pošta nad celým zvyškom príkazu), aj keď je
        únik v časti, ktorú rozklad číta až po tele cyklu (`while read p; do python3 "$p" New-Mailbox; done <<< "$E"`).
        Cesta tak nemôže odísť k inému programu cez rúru, súbor či kópiu (`echo $E | xargs -I{} python3 {} New-Mailbox`).
        ${…} s operátorom (${E%/*}, ${!x}, ${A[@]}) a $?, $((…)) rozklad nepozná po mene — platia ako odkaz."""
        mena = ctx.posta_exo
        odkazy = []
        unik = False
        for w in cmd.slova:
            priamo, vnor = self.exo_v_slove(w, mena, 0)
            if vnor or (priamo and len(w.seg) != 1):
                unik = True
                break
            if priamo:
                odkazy.append(w)
        if unik or any(any(self.exo_v_slove(w, mena, 0)) for w in [w for _, w in cmd.presm] + cmd.herestr) \
                or any(not kvot and exo_heredoc_odkaz(h, mena) for h, kvot in cmd.heredoc) \
                or (cmd.podshell is not None and self.exo_v_prikazoch(cmd.podshell, mena, 1)) \
                or any(self.exo_v_prikazoch(vn, mena, 1) for vn in cmd.vnorene) \
                or (odkazy and (len(odkazy) > 1 or odkazy[0] is not self.exo_priame_slovo(cmd, ctx))):
            self.exo_unik = True
            ctx.posta_env = True

    def exo_v_slove(self, w, mena, hl):
        """(slovo rozvinie premennú z mena priamo, rozvinie ju v náhrade $(…) v ňom)"""
        priamo = vnor = False
        for sg in w.seg:
            if sg[0] == 'v' and (sg[1] in mena or sg[1] == '?'):
                priamo = True
            elif sg[0] == 's' and sg[1] and self.exo_v_prikazoch(sg[1], mena, hl + 1):
                vnor = True
        return priamo, vnor

    def exo_v_prikazoch(self, cmds, mena, hl):
        if hl > MAX_HLBKA:
            return True
        for c in cmds:
            if any(any(self.exo_v_slove(w, mena, hl)) for w in c.slova + [w for _, w in c.presm] + c.herestr) \
                    or any(not kvot and exo_heredoc_odkaz(h, mena) for h, kvot in c.heredoc) \
                    or (c.podshell is not None and self.exo_v_prikazoch(c.podshell, mena, hl + 1)) \
                    or any(self.exo_v_prikazoch(vn, mena, hl + 1) for vn in c.vnorene):
                return True
        return False

    def exo_priame_slovo(self, cmd, ctx):
        """Slovo s premennou, ktoré priamo spúšťa nástroj na správu Exchange: program ($X, X="python3 …/exo-rest.py")
        alebo skript interpretra (python3 [voľby] $E …) — bez obalov (sudo, timeout, env …) a priradení pred príkazom."""
        sl = cmd.slova
        i = 0
        while i < len(sl) and not sl[i].kvot and sl[i].text() in KLUCOVE:
            i += 1

        def premenna(w):
            return len(w.seg) == 1 and w.seg[0][0] == 'v' and w.seg[0][1] in ctx.posta_exo
        if i >= len(sl):
            return None
        if premenna(sl[i]):
            return sl[i]
        if any(sg[0] != 'l' for sg in sl[i].seg) or not re.fullmatch(r'(?:/usr(?:/local)?/bin/)?python[0-9.]*', sl[i].text()):
            return None
        k = i + 1
        while k < len(sl):
            if premenna(sl[k]):
                return sl[k]
            x = sl[k].text()
            if any(sg[0] != 'l' for sg in sl[k].seg) or not x.startswith('-') or x == '-' or x.startswith(('-c', '-m')):
                return None
            k += 2 if x in ('-W', '-X') else 1
        return None

    def posta_pomoc(self, pb, args, skript, zvysok, ctx):
        """Poštový nástroj spustený len s pokynom a --help/-h vypíše pomoc (POSTA_POMOC vyššie, 2. 10.)."""
        if not re.fullmatch(r'python[0-9.]*', pb) or skript is None or skript >= len(args):
            return False
        if not any(x in POSTA_POMOC for x in zvysok) or \
                not all(x in POSTA_POMOC or x.lower().lstrip('-') in POSTA_POKYN for x in zvysok):
            return False
        kand = self.rozvin(args[skript], ctx)
        if len(kand) != 1 or kand[0][1] is not None or ctx.vzdial or ctx.kontajner:
            return False
        p = self.absolutna(kand[0][0], ctx)
        self.skript_stav['citania'] += 1
        if p and not self.smie(p):
            return False
        try:
            if not p or not p.endswith('.py') or not os.path.isfile(p) or os.path.getsize(p) > 1024 * 1024:
                return False
            with open(p, encoding='utf-8', errors='replace') as f:
                obs = f.read()
        except OSError:
            return False
        return bool(POSTA_ARGPARSE.search(obs)) and 'ArgumentParser' in obs and not POSTA_ARGPARSE_ZLE.search(obs)

    def kod_posiela(self, kody, ctx=None, jazyk='py', nastroj_meno=True):
        """Kód interpretra, ktorý poštu naozaj posiela: spúšťa poštový program (vyhodnotený argument volania posúdi rozbor
        shellu), načíta poštový skript ako knižnicu, alebo poštové volanie (sendmail, send_message, webhook Teams) spolu
        so sieťou. Meno v reťazci pri úprave súboru nestačí.
        K88N (11. 10.): skript, ktorý príkaz spúšťa 29-krát s inými argumentmi (skúška ochrany účtov → skript upozornení
        so 109 kB kódu v heredocu), sa rozoberá 29-krát a ten istý kód sa tu posudzoval 58-krát — 2,4 s z 3,4 s rozboru,
        tesne pod stropom SKRIPT_CAS; pod záťažou ho rozbor prekročil a ten istý príkaz raz prešiel a raz bol „nedočítaný“.
        Časť posudku, ktorá závisí len od textu kódu (vzory nad celým textom tu, rozbor volaní v kod_kroky), sa preto
        v rozbore jedného príkazu robí raz; čo závisí od miesta (premenné, súbory, rozbor shellu), sa robí zakaždým.
        Pamätá sa len úvod, ktorý neskončil odoslaním: False = v kóde nič poštové, (značka,) = treba posúdiť volania."""
        pam = self.skript_stav.setdefault('kod_uvod', {})
        for kd in kody:
            kluc = (kd, jazyk, nastroj_meno)
            uvod = pam.get(kluc)
            if uvod is False:
                continue
            if uvod is not None:
                znacka = uvod[0]
            else:
                if (POSTA_VOLANIE.search(kd) and kod_posta_siet(kd, jazyk)) or kod_posta_sama(kd, jazyk):
                    return True
                if nastroj_meno and jazyk == 'py' and self.kod_nacita_postu(kd):
                    return True
                znacka = bool(ZNACKY_PROG.search(kd))
                # poštový nástroj podľa mena (graph_mail.py) v kóde — len s pokynom odoslať, keď sa volania nedajú rozobrať (29. 9.)
                nastroj = nastroj_meno and not znacka and bool(POSTA_NASTROJ_KOD.search(kd))
                if not znacka and not nastroj:
                    pam[kluc] = False
                    continue
                if jazyk == 'iny' or (jazyk in ('pl', 'rb', 'php') and KOD_ZACHRANA.search(kd)):
                    # PowerShell, osascript, spätné apostrofy v Perle/Ruby/PHP — volania nevie rozobrať: pravidlo 10.06
                    if (KOD_SPUSTA_1006.search(kd) or KOD_ZACHRANA.search(kd)) and (znacka or KOD_POSTA_POKYN.search(kd)):
                        return True
                    continue
                pam[kluc] = (znacka,)
            try:
                if self.kod_spusta_postu(kd, jazyk, ctx, nastroj_meno):
                    return True
            except Exception:           # rozbor kódu zlyhal — pravidlo odoslanej kotvy 10.06
                if KOD_SPUSTA_1006.search(kd) and (znacka or KOD_POSTA_POKYN.search(kd)):
                    return True
        return False

    def kod_spusta_postu(self, kd, jazyk, ctx, nastroj_meno=True):
        """Kroky z kod_kroky posúdené v mieste príkazu. K88N (11. 10.): kroky toho istého kódu sa v rozbore jedného
        príkazu počítajú raz — pamätajú sa len vtedy, keď ich výpočet dobehol celý (bez výnimky a bez predčasného
        odoslania); posúdenie v mieste (exo_cesta, premenné, prikaz_posiela) sa robí zakaždým a v tom istom poradí."""
        pam = self.skript_stav.setdefault('kod_kroky', {})
        kluc = (kd, jazyk, nastroj_meno)
        kroky = pam.get(kluc)
        zapis = None
        if kroky is None:
            zapis = []
            kroky = kod_kroky(kd, jazyk, nastroj_meno)
        po_starom = None
        for krok in kroky:
            if zapis is not None:
                zapis.append(krok)
            if krok[0] == 'posiela':
                return True
            if krok[0] == 'nacita':
                # 2. 10.: overený exo-rest.py načítaný ako modul (spec_from_file_location …) v kóde, ktorý s ním len
                # číta (exo_kod_cita: m.run('Get-…'), nič iné okolo) — nie je odoslanie
                nastroje = [self.exo_cesta(x, ctx) for x in krok[1]]
                if jazyk == 'py' and krok[2] in EXO_NACITA and all(ok for ok, _ in nastroje) \
                        and not any(NASTROJ_ZLA_PREMENNA.match(m) for m in (ctx.vars if ctx is not None else ())) \
                        and exo_kod_cita(kd, frozenset().union(*(m for _, m in nastroje))):
                    continue
                return True
            if po_starom is None:
                po_starom = bool(KOD_PROSTREDIE.search(kd))
            if self.prikaz_posiela(krok[1], ctx, po_starom=po_starom):
                return True
        if zapis is not None:
            pam[kluc] = tuple(zapis)
        return False

    def kod_nacita_postu(self, kd):
        """Python: import poštového modulu podľa mena (import graph_mail, from graph_mail import send) a pokyn odoslať
        alebo main() v tom istom kóde. Štandardné email/mailbox/mailcap nie sú poštový nástroj (29. 9.). Aj načítanie
        za behu — importlib.import_module('graph_mail'), __import__, runpy.run_module/run_path (29. 9. večer)."""
        if KOD_NACITA_POSTU_DYN.search(kd) and KOD_POSTA_POKYN.search(kd):
            return True
        for m in KOD_IMPORT_POSTA.finditer(kd):
            mena = [x.strip().split(' ')[0] for x in (m.group(1) or m.group(2) or '').split(',')]
            if any('mail' in x.lower() and x.split('.')[0].lower() not in POSTA_STDLIB for x in mena if x):
                if KOD_POSTA_POKYN.search(kd):
                    return True
        return False

    def prikaz_posiela(self, txt, ctx, po_starom=False):
        """Príkaz, ktorý kód spúšťa, posúdi ten istý rozbor shellu — pošta alebo nedočítané vnorenie = odoslanie.
        po_starom (overenie 2. 10.): kód mení prostredie interpretra — exo-rest.py sa posúdi ako pred 2. 10."""
        sub = Rozbor(self.hook_cwd, self.domov)
        sub.subory = self.subory
        sub.zmenene = self.zmenene
        sub.exo_po_starom = self.exo_po_starom
        sub.exo_vypnute = self.exo_vypnute or po_starom
        c2 = ctx.kopia(sudo=False) if ctx is not None else Ctx(self.hook_cwd or None, 1)
        sub.prikazy(parsuj(sh_obaly(txt)), c2)
        self.exo_unik |= sub.exo_unik
        return P_POSTA in sub.kanaly or P_HLBKA in sub.kanaly

    def spusti_skript(self, w, obs, ctx, sudo):
        """Shellový skript, ktorý príkaz spúšťa. Zapísaný v tom istom príkaze → rozbor ako príkazy ($0 je cesta skriptu:
        cd "$(dirname "$0")/.." — Erika 8. 9.). Dočasný skript z disku → len pošta: skripty mávajú podmienené časti
        (HTML_ONLY=1 … systemctl restart, PS 22. 9.), ktoré rozbor nevie vyhodnotiť, a zastavovali by falošne."""
        z_disku = self.z_disku
        c2 = ctx.kopia(sudo=sudo)
        if w is not None:
            k = [c for c, u in self.rozvin(w, ctx) if u is None][:1]
            p = self.absolutna(k[0], ctx) if k else None
            c2.vars['0'] = [p] if p else None
        obs = sh_obaly(obs)          # 2. 10. večer: f() { "$@"; } v dočasnom skripte — argumenty obalu sú príkaz
        if not z_disku:
            self.retazec(obs, c2)
            return
        # z disku: rozbor ako príkazy, ale platí len kanál, ktorý odchádza von (pošta, zápis do cudzieho systému …).
        # 2. 10. (úloha K): ten istý zoznam ako pri skripte projektu (SKRIPT_KANALY — pribudol zásah cez az, súbor von,
        # prepis a zmazanie na GitHube, ostrá prevádzka) a skript projektu, ktorý dočasný skript spúšťa
        sub = Rozbor(self.hook_cwd, self.domov)
        sub.len_program = True
        sub.subory = self.subory
        sub.zmenene = self.zmenene
        sub.mkt_subory = self.mkt_subory
        sub.exo_po_starom = self.exo_po_starom
        sub.exo_vypnute = self.exo_vypnute
        sub.skript_stav = self.skript_stav
        sub.prikazy(parsuj(obs), c2)
        self.exo_unik |= sub.exo_unik
        for pr in SKRIPT_KANALY:
            if pr in sub.kanaly:
                self.kanal(pr, sub.kanaly[pr])
        if 'skript' in sub.priznaky and not self.iba_nastroje:
            self.priznaky.add('skript')
        self.mkt_nalez(sub.mkt_zmena, False)     # marketingová služba zo skriptu (prepínač MARKETING)
        self.mkt_nalez(sub.mkt_tvrde, True)

    def subst(self, cmds, ctx):
        """Hodnota jednoduchej substitúcie: $(pwd), $(dirname X), $(basename X), $(realpath X), $(readlink -f X)."""
        if self.skript_hlbka:
            h = self.subst_skript(cmds, ctx)
            if h:
                return h
        if len(cmds) != 1 or not cmds[0].slova or cmds[0].heredoc or cmds[0].podshell is not None:
            return None
        sl = cmds[0].slova
        p = sl[0].text()
        if p == 'pwd' and len(sl) == 1:
            return ctx.cwd
        if p == 'mktemp':
            return self.mktemp_cesta(sl, ctx)
        if p not in ('dirname', 'basename', 'realpath', 'readlink'):
            return None
        poz = [w for w in sl[1:] if not w.text().startswith('-')]
        if len(poz) != 1:
            return None
        k = self.rozvin(poz[0], ctx)
        if len(k) != 1 or k[0][1] is not None or not k[0][0]:
            return None
        v = k[0][0]
        if p == 'dirname':
            return os.path.dirname(v.rstrip('/')) or ('/' if v.startswith('/') else '.')
        if p == 'basename':
            return os.path.basename(v.rstrip('/'))
        if p == 'realpath' or any(w.text() in ('-f', '-e', '-m') for w in sl[1:]):
            return self.absolutna(v, ctx)
        return None

    def mktemp_cesta(self, sl, ctx):
        """$(mktemp [-d] [-p priečinok] vzor) s premennou známej hodnoty v priečinku či vzore → cesta v tom priečinku
        (3. 10., overenie kola 1: `K=/tmp/x; W=$(mktemp -d "$K/d-XXXX"); trap 'rm -rf "$W"' EXIT` zastavil — cesta
        v premennej). Doslovný vzor rieši mktemp_hodnota pri čítaní príkazu; čo sa nedá prečítať, je neznáme (None)."""
        adr = vzor = None
        nanecisto = neznamy = False
        j = 1
        while j < len(sl):
            x = sl[j].text()
            if x in ('-p', '--tmpdir') and j + 1 < len(sl):
                adr = sl[j + 1]
                j += 2
                continue
            if x.startswith('-'):
                if x == '--dry-run' or (not x.startswith('--') and 'u' in x[1:]):
                    nanecisto = True
                if x.startswith(('--tmpdir=', '-p')) or (not x.startswith('--') and 't' in x[1:]):
                    neznamy = True   # priečinok v tvare, ktorý kotva nečíta (--tmpdir=…, -pX), alebo -t (TMPDIR)
                j += 1
                continue
            if vzor is not None:
                neznamy = True
            vzor = sl[j]
            j += 1

        def hod(w):
            k = self.rozvin(w, ctx)
            return k[0][0] if len(k) == 1 and k[0][1] is None and k[0][0] else None
        # 3. 10. (kolo 3): posledný diel je značka MKTEMP_LIST (cesta, ktorú mktemp vytvoril); priečinok, ktorý kotva
        # nevie prečítať, je neznámy (NEZNAME) a značka ostáva — v obsluhe trap sa zmaže len to, čo mktemp vytvoril.
        # Pri -u (nič nevytvorí) ako doteraz: známa cesta alebo neznáma hodnota.
        cesta = None
        if neznamy:
            pass
        elif adr is not None:
            a = hod(adr)
            v = hod(vzor) if vzor is not None else 'tmp.X'
            if a and a.startswith('/') and v and not v.startswith('/'):
                cesta = os.path.normpath(a + '/' + v)
        else:
            v = hod(vzor) if vzor is not None else None
            # bez -p: vzor bez cesty vznikne v priečinku príkazu, bez vzoru v TMPDIR — to číta mktemp_hodnota
            if v and v.startswith('/'):
                cesta = os.path.normpath(v)
        if nanecisto:
            return cesta
        return (os.path.dirname(cesta) if cesta else NEZNAME) + '/' + MKTEMP_LIST

    def nastroj_ostro(self, pv, pb, args, t, pred, ctx, cmd=None, pred_cmd=None):
        """Nástroj projektu spustený ostro, ktorý vo vnútri pracuje pod menom iného používateľa (NASTROJ_* vyššie).
        cmd = tento príkaz, pred_cmd = príkaz pred ním v rúre (nástroj zo vstupu)."""
        if self.v_nastroji:
            return
        try:
            ostro = any(NASTROJ_OSTRO.match(x) for x in t) or any(NASTROJ_OSTRO.match(w.text()) for w in pred)
            if not ostro:
                return
            if pb in NASTROJ_INTERP or INTERPRETY.match(pb):
                # 29. 9.: skript je prvý argument za voľbami interpretra, nie hodnota voľby (bash -euo pipefail …)
                j = skript_interpretra(pb, t)
                if j is not None and j < len(args):
                    kand = [c for c, u in self.rozvin(args[j], ctx) if u is None][:1]
                else:
                    # 29. 9. večer (druhé kolo overenia): nástroj podaný na vstup — `bash -s ptoth cp --naostro <
                    # …/sync-zachrana.sh`, `cat …/sync-zachrana.sh | bash -s -- ptoth cp --naostro` — prešiel, lebo skript
                    # nebol medzi argumentmi; posúdi sa rovnako ako `bash …/sync-zachrana.sh … --naostro`
                    w = self.nastroj_zo_vstupu(pb, t, cmd, pred_cmd)
                    if w is None:
                        return
                    kand = [c for c, u in self.rozvin(w, ctx) if u is None][:1]
            elif pb in ('source', '.'):
                # 29. 9.: `source …/sync-zachrana.sh ptoth cp --naostro` spustí nástroj v tomto shelli — rovnako ako bash
                j = 1 if t[:1] == ['--'] else 0
                if j >= len(args):
                    return
                kand = [c for c, u in self.rozvin(args[j], ctx) if u is None][:1]
            else:
                kand = [pv]
            p = self.absolutna(kand[0], ctx) if kand else None
            # 2. 10. večer: nie stat ani open v cudzom domove (prehrávka kolegov beží pod PS) — ako nečitateľný súbor
            if not p or not self.smie(p) or not os.path.isfile(p) or os.path.getsize(p) > 1024 * 1024:
                # 27. 10.: obsah sa prečítať nedá — rozhodne meno súboru (NASTROJ_OSTRE_MENA vyššie)
                druh = NASTROJ_OSTRE_MENA.get(os.path.basename(kand[0])) if kand else None
                if druh == 'domov':
                    self.kanal_nastroja(P_DOMOV, K_DOMOV)
                elif druh == 'sudo':
                    self.kanal_nastroja(P_SUDO, K_SUDO)
                return
            with open(p, encoding='utf-8', errors='replace') as f:
                obs = f.read()
            u = NASTROJ_SUDO_U.search(obs)
            prvy = obs.split('\n', 1)[0]
            shell = pb in SHELLY or pb in ('source', '.') or (not INTERPRETY.match(pb) and (
                p.endswith(('.sh', '.bash')) or (prvy.startswith('#!') and re.search(r'\b(?:ba|da|z|k)?sh\b', prvy))))
            if not u and not NASTROJ_SUDO.search(obs) and not NASTROJ_SUDO_KOD.search(
                    '\n'.join(re.sub(r'''\s#[^'"\n]*$''', '', r) for r in obs.split('\n') if not r.lstrip().startswith('#'))):
                # 29. 9. večer (druhé kolo overenia): nástroj bez sudo, ktorý sám ostro spúšťa iný nástroj
                # (`bash …/sync-zachrana.sh "$1" "$2" --naostro` vnútri) — vnorený nástroj sa posúdi rovnako (do 2 úrovní)
                if shell and self.iba_nastroje < 2 and NASTROJ_OSTRO_TEXT.search(obs):
                    sub = Rozbor(self.hook_cwd, self.domov)
                    sub.iba_nastroje = self.iba_nastroje + 1
                    sub.exo_po_starom = True        # nástroj sa posudzuje ako pred 2. 10.
                    try:
                        sub.prikazy(parsuj(obs), Ctx(self.hook_cwd or None, 1))
                    except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
                        return
                    for prio, text in sub.kanaly.items():
                        self.kanal_nastroja(prio, text)
                return
            # 29. 9. neskoro večer (tretie kolo): výnimka „nástroj len číta“ vrátená — zoznam bezpečných programov
            # nekontroloval zápis (rm, cp, mv, git push, tee …), takže nástroj so sudo a ostrou voľbou prešiel, hoci
            # písal inde. Nástroj so sudo spustený ostro zastaví vždy (ako pred 29. 9.); zbytočná otázka je lacnejšia.
            if u:
                self.kanal_nastroja(P_DOMOV, K_DOMOV)
            else:
                self.kanal_nastroja(P_SUDO, K_SUDO)
        except (OSError, IndexError, ValueError):
            return

    def kanal_nastroja(self, prio, text):
        """Kanál z nástroja spusteného ostro — platí aj v rozbore vnoreného nástroja (iba_nastroje)."""
        self.kanaly[prio] = text

    def nastroj_zo_vstupu(self, pb, t, cmd, pred_cmd):
        """Súbor skriptu, ktorý interpreter číta zo vstupu (bash -s …, bash bez skriptu, python3 - …): presmerovanie
        `< súbor` alebo `cat súbor |` pred ním → slovo so súborom, inak None. Kód v -c/-e/-m a kontrola -n nie sú vstup."""
        if cmd is None:
            return None
        for x in t:
            if x in ('--', '-') or not x.startswith(('-', '+')):
                break
            if not x.startswith('--') and re.search(r'[cn]' if pb in SHELLY else r'[cm]', x[1:]):
                return None
        for op, w in cmd.presm:
            if op == '<':
                return w
        if cmd.rura and pred_cmd is not None and not cmd.heredoc and not cmd.herestr:
            w, upraveny = self.vstup_rury(pred_cmd, None)
            if w is not None and upraveny:
                # 3. 10. (kolo 4, overenie kola 3: cat t/a.sh | sed 's/DRY=1/DRY=0/' | bash): vstup je upravený súbor —
                # posúdi sa celý (zmena_skriptu), nie podľa vetiev starého obsahu
                self.zmena_slovo(w, Ctx(self.hook_cwd or None, 1) if self._ctx_rury is None else self._ctx_rury)
            return w
        return None

    def vstup_rury(self, c, ctx, hlbka=0):
        """Súbor, ktorého obsah príkaz c posiela na výstup → (slovo, upravený): cat súbor · sed … súbor · filter (sed, tr,
        envsubst, dos2unix, cat) bez súboru, ktorý číta rúru alebo `< súbor`. Inak (None, False)."""
        if c is None or hlbka > 6 or c.podshell is not None or c.heredoc or c.herestr or not c.slova:
            return None, False
        sl = c.slova
        i = 0
        while i < len(sl) and self.je_priradenie(sl[i]):
            i += 1
        if i >= len(sl):
            return None, False
        prog = os.path.basename(sl[i].text())
        args = sl[i + 1:]
        t = [w.text() for w in args]
        if prog == 'cat':
            subory = [w for w in args if not w.text().startswith('-')]
            upraveny = False
        elif prog in ('sed', 'gsed'):
            miesto, kod, poz = sed_volby(t)
            if miesto:
                return None, False
            subory = [args[k] for k in poz[0 if kod else 1:]]
            upraveny = True
        elif prog in ('tr', 'envsubst', 'dos2unix'):
            subory = []
            upraveny = True
        else:
            return None, False
        if len(subory) == 1:
            return subory[0], upraveny
        if subory:
            return None, False
        for op, w in c.presm:
            if op == '<':
                return w, upraveny
        x = self._rura_pred.get(id(c))
        if c.rura and x is not None and x[0] is c:
            w, u = self.vstup_rury(x[1], ctx, hlbka + 1)
            return w, (u or upraveny) if w is not None else False
        return None, False

    def nastroj_posud(self, cmds):
        """Príkazy zvnútra nástroja posúdené rozborom ako príkaz partnera → True, keď nemajú žiadny kanál."""
        sub = Rozbor(self.hook_cwd, self.domov)
        sub.v_nastroji = True
        sub.exo_po_starom = True            # nástroj sa posudzuje ako pred 2. 10.
        try:
            sub.prikazy(cmds, Ctx(None, 1))
        except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
            return False
        return not sub.kanaly

    def nastroj_len_cita(self, obs):
        """Shellový nástroj prejde bez otázky, len keď je celý zo známych dielov (NASTROJ_* vyššie; 29. 9. večer prísnejšie):
        každé sudo/runuser je príkaz a jeho rozbor je čítanie (priamo aj cez jednoriadkový obal s "$@"), a každý iný
        príkaz je vstavaný príkaz shellu, funkcia nástroja alebo program z NASTROJ_BEZPECNE, ktorý nespúšťa iný kód.
        Pochybnosť = False (zastaví ako pred 29. 9.)."""
        if NASTROJ_ROOT.search(obs) or NASTROJ_SUDO_PREMENNA.search(obs) or NASTROJ_SUDO_ZOZNAM.search(obs):
            return False
        obaly = {}              # meno → (koľko argumentov obal zoberie pre seba, [text príkazu so sudo a "$@"])
        for m in NASTROJ_OBAL.finditer(obs):
            telo = m.group(2)
            useky = NASTROJ_OBAL_SUDO.findall(telo)
            if not useky:
                continue
            # 29. 9. večer: posun len z bezpodmienečného shift — pri „[ "$1" = -q ] && shift“ posun nevidno; taký obal sa
            # nerozpozná a jeho telo so "$@" zastaví
            if re.search(r'&&|\|\||\bif\b|\bcase\b|\bwhile\b|\bfor\b', telo):
                continue
            posun = sum(int(n or 1) for n in re.findall(r'\bshift\b(?:[ \t]+(\d+))?', telo))
            obaly[m.group(1)] = (posun, useky)
        funkcie = {a or b for a, b in NASTROJ_FUNKCIA.findall(obs)}

        def bez_vzorov(m):
            # vzor vetvy case → „(:)“: rozklad by zo vzoru spravil jednoslovný príkaz („aigrip)“ → príkaz aigrip) a zátvorka
            # vzoru by v $(case … esac) predčasne zavrela náhradu; „(:)“ je vyvážená a „:“ nič nespustí
            return NASTROJ_CASE_VZOR.sub(lambda v: v.group(0)[:v.start(1) - v.start(0)].rstrip(' \t(') + ' (:)', m.group(0))
        try:
            cmds = parsuj(re.sub(r'\bcase\b.*?\besac\b', bez_vzorov, obs, flags=re.S))
            vzory = {tuple(w.text() for w in parsuj(u)[0].slova) for _, (_, us) in obaly.items() for u in us}
        except (RecursionError, ValueError, IndexError):
            return False
        spomenute = [0]
        uvod = KLUCOVE | {'time'}
        koniec = {'done', 'fi', '}', 'esac', ')'}

        def zla_premenna(x):
            m = re.match(r'([A-Za-z_]\w*)(?:\[[^\]]*\])?\+?=', x)
            return bool(m and NASTROJ_ZLA_PREMENNA.match(m.group(1)))

        def volby(sl, j, s_hodnotou, l_hodnotou=()):
            """Index prvého slova za voľbami obalu príkazu (timeout -s KILL 5 …)."""
            while j < len(sl) and sl[j].startswith('-') and sl[j] != '-':
                x = sl[j]
                if x == '--':
                    return j + 1
                if x.startswith('--'):
                    j += 2 if ('=' not in x and x in l_hodnotou) else 1
                elif len(x) == 2 and x[1] in s_hodnotou:
                    j += 2
                else:
                    j += 1
            return j

        def sed_spusta(sl):
            """sed s príkazom e alebo príznakom e pri s/// (GNU sed spustí text ako príkaz) alebo so skriptom zo súboru."""
            for x in sl[1:]:
                if x in ('-f', '--file') or x.startswith('--file='):
                    return True
                if x.startswith('-'):
                    continue
                for m in re.finditer(r's(.)(?:\\.|(?!\1).)*\1(?:\\.|(?!\1).)*\1([A-Za-z0-9]*)', x):
                    if 'e' in m.group(2):
                        return True
                if re.search(r'(?:^|[;\n{}])\s*(?:\d+|\$|/[^/\n]*/)?\s*e(?:\s|$|;)', x):
                    return True
            return False

        def prog_ok(sl, hl=0):
            """Program sl[0] (s argumentmi) nespúšťa iný kód ani nekoná pod iným menom."""
            if not sl:
                return True
            if hl > 6:
                return False
            w = sl[0]
            if '$' in w or '`' in w:
                return False                        # príkaz z premennej ("${AS[@]}" …, $S …)
            b = os.path.basename(w)
            if '/' in w:
                if os.path.dirname(w) not in NASTROJ_SYS or b not in NASTROJ_BEZPECNE:
                    return False                    # iný skript či program z cesty (bash …/x.sh, ./x.sh)
            elif b in funkcie and b not in NASTROJ_NEBEZPECNE:
                return True                         # funkcia nástroja (jej telo sa posúdi ako ostatné príkazy)
            if b in NASTROJ_NEBEZPECNE:
                return False
            if b == 'exec':
                return prog_ok(sl[volby(sl, 1, 'a'):], hl + 1)
            if b == 'builtin':
                return len(sl) < 2 or sl[1] in NASTROJ_VSTAVANE - {'exec', 'command', 'builtin', 'trap'}
            if b == 'command':
                if len(sl) > 1 and sl[1] in ('-v', '-V'):
                    return True
                return prog_ok(sl[volby(sl, 1, ''):], hl + 1)
            if b == 'trap':
                return True                         # akcia sa posúdi v prejdi
            if b == 'printf':
                k = sl.index('-v') if '-v' in sl else -1
                return not (k >= 0 and k + 1 < len(sl) and NASTROJ_ZLA_PREMENNA.match(sl[k + 1]))
            if b in ('declare', 'typeset', 'local', 'export', 'readonly'):
                return not any(zla_premenna(x) for x in sl[1:])
            if b in NASTROJ_VSTAVANE:
                return True
            if b not in NASTROJ_BEZPECNE:
                return False                        # neznámy program — môže byť skript so sudo
            if b == 'nohup':
                return prog_ok(sl[1:], hl + 1)
            if b == 'timeout':
                j = volby(sl, 1, 'sk', ('--signal', '--kill-after'))
                return prog_ok(sl[j + 1:], hl + 1)
            if b == 'nice':
                return prog_ok(sl[volby(sl, 1, 'n', ('--adjustment',)):], hl + 1)
            if b == 'stdbuf':
                return prog_ok(sl[volby(sl, 1, 'ioe', ('--input', '--output', '--error')):], hl + 1)
            if b == 'setsid':
                return prog_ok(sl[volby(sl, 1, ''):], hl + 1)
            if b == 'env':
                if any(x == '--split-string' or x.startswith('--split-string=') or re.match(r'-[^-]*S', x) for x in sl[1:]):
                    return False
                j = volby(sl, 1, 'uC', ('--unset', '--chdir'))
                while j < len(sl) and re.match(r'[A-Za-z_]\w*=', sl[j]):
                    if zla_premenna(sl[j]):
                        return False
                    j += 1
                return prog_ok(sl[j:], hl + 1)
            if b == 'xargs':
                j = volby(sl, 1, 'IinLPdEesa', ('--arg-file', '--delimiter', '--max-args', '--max-lines', '--max-procs',
                                                '--replace', '--eof', '--max-chars', '--process-slot-var'))
                return prog_ok(sl[j:], hl + 1)
            if b == 'flock':
                if any(x in ('-c', '--command') or x.startswith('--command=') for x in sl[1:]):
                    return False
                j = volby(sl, 1, 'wEo', ('--wait', '--timeout', '--conflict-exit-code'))
                return prog_ok(sl[j + 1:], hl + 1)
            if b == 'find':
                return not any(x in ('-exec', '-execdir', '-ok', '-okdir') for x in sl[1:])
            if b in ('awk', 'gawk', 'mawk', 'nawk'):
                if any(x in ('-f', '--file', '-E', '--exec') or x.startswith(('--file=', '--exec=')) for x in sl[1:]):
                    return False
                return not any(re.search(r'\bsystem\s*\(|\|\s*"|"\s*\|\s*getline|\|&|\bgetline\s*<', x) for x in sl[1:])
            if b == 'sed':
                return not sed_spusta(sl)
            if b == 'sort':
                return not any(x.startswith('--compress-program') for x in sl[1:])
            if b == 'git':
                for k, x in enumerate(sl[1:], 1):
                    if x == '-c' and not (k + 1 < len(sl) and GIT_C_NESPUSTA.match(sl[k + 1])):
                        return False
                    if x.startswith(('--config-env', '--exec-path=', '--upload-pack', '--receive-pack')):
                        return False
                return True
            return True

        def prejdi(zoz, hl):
            if hl > MAX_HLBKA:
                return False
            for c in zoz:
                sl = [w.text() for w in c.slova]
                i = 0
                while i < len(sl) and sl[i] in uvod:
                    i += 1
                if i < len(sl) and sl[i] == 'function':
                    i += 2
                    while i < len(sl) and sl[i] in uvod:
                        i += 1
                texty = sl + [h for h, _ in c.heredoc] + [w.text() for w in c.herestr]
                n = sum(len(NASTROJ_SUDO_SLOVO.findall(x)) for x in texty)
                spomenute[0] += n
                # priradenia pred príkazom (A=1 git …) a samotné priradenie; PATH, LD_*, GIT_SSH … = pochybnosť
                pr = i
                while pr < len(sl) and re.match(r'[A-Za-z_]\w*(?:\[[^\]]*\])?\+?=', sl[pr]):
                    if zla_premenna(sl[pr]):
                        return False
                    pr += 1
                prog = sl[pr] if pr < len(sl) else ''
                pb = os.path.basename(prog)
                if prog and prog in obaly:
                    posun, useky = obaly[prog]
                    zvysok = ' '.join(kotva_quote(x) for x in sl[pr + 1 + posun:])
                    for u in useky:
                        txt = re.sub(r'"\$[@*]"|\$[@*]', lambda _: zvysok, u)
                        try:
                            if not self.nastroj_posud(parsuj(txt)):
                                return False
                        except (RecursionError, ValueError, IndexError):
                            return False
                elif any(x in obaly for x in sl[pr + 1:]):
                    return False            # obal podaný inému príkazu (xargs S …, timeout 9 G …) — nedočíta
                elif n:
                    if tuple(sl[i:]) in vzory:
                        pass                # telo obalu — posúdia sa jeho volania
                    elif pb in ('sudo', 'runuser') and ('/' not in prog or os.path.dirname(prog) in NASTROJ_SYS):
                        if any(re.search(r'\$\{?[@*]|\[[@*]\]|\$\{!', x) for x in sl):
                            return False    # "$@", "${@}", "${cmd[@]}" pod sudo — čo sa spustí, nevidno
                        if any(NASTROJ_SUDO_SLOVO.search(h) for h in texty[len(sl):]) \
                                and not any(os.path.basename(x) in SHELLY for x in sl):
                            return False    # sudo v kóde iného jazyka (python3 - <<EOF … subprocess sudo …)
                        if not self.nastroj_posud([c]):
                            return False
                    elif pb in ('echo', 'printf') and '-v' not in sl \
                            and not any(NASTROJ_SUDO_SLOVO.search(h) for h in texty[len(sl):]):
                        pass                # sudo len v texte výpisu (echo "… cez sudo -n -u …")
                    else:
                        return False        # sudo v premennej, poli, aliase, argumente iného príkazu (timeout sudo …)
                elif sl and sl[0] in koniec:
                    pass                    # done < <(…), } > súbor — za koncom bloku sú len presmerovania
                elif i < len(sl) and sl[i] in ('for', 'select', 'case', 'in', 'esac'):
                    pass                    # hlavička cyklu či vetvenia (zoznam a náhrady sa posúdia nižšie)
                elif prog:
                    if pb == 'trap':
                        akcia = [x for x in sl[pr + 1:] if not x.startswith('-')][:1]
                        try:
                            if akcia and akcia[0] and not prejdi(parsuj(akcia[0]), hl + 1):
                                return False
                        except (RecursionError, ValueError, IndexError):
                            return False
                    elif not prog_ok(sl[pr:]):
                        return False
                for w in c.slova + [w for _, w in c.presm] + c.herestr:
                    for sg in w.seg:
                        if sg[0] == 's' and sg[1] and not prejdi(sg[1], hl + 1):
                            return False
                for vn in c.vnorene:
                    if not prejdi(vn, hl + 1):
                        return False
                if c.podshell is not None and not prejdi(c.podshell, hl + 1):
                    return False
            return True

        def nahrady(text):
            """Príkazy v $(…) a `…` v texte, ktorý rozklad nečíta ako príkazy — aritmetika (( … )) / $(( … )) (rozklad ju
            berie ako premennú) a heredoc bez úvodzoviek pri oddeľovači (29. 9. večer)."""
            von = []
            for n in re.finditer(r'\$\((?!\()', text):
                j, h = n.end(), 1
                while j < len(text) and h > 0:
                    h += {'(': 1, ')': -1}.get(text[j], 0)
                    j += 1
                von.append(text[n.end():j - 1])
            return von + re.findall(r'`([^`]*)`', text)

        def skryte(zoz, hl=0):
            """Texty heredocov bez úvodzoviek (cat <<EOF … $(príkaz) … EOF) zo všetkých príkazov a náhrad."""
            von = []
            if hl > MAX_HLBKA:
                return von
            for c in zoz:
                von += [h for h, q in c.heredoc if not q]
                for w in c.slova + [w for _, w in c.presm] + c.herestr:
                    for sg in w.seg:
                        if sg[0] == 's' and sg[1]:
                            von += skryte(sg[1], hl + 1)
                for vn in c.vnorene:
                    von += skryte(vn, hl + 1)
                if c.podshell is not None:
                    von += skryte(c.podshell, hl + 1)
            return von

        if not prejdi(cmds, 0):
            return False
        aritm = []
        for m in re.finditer(r'\(\(', obs):
            k, h = m.end(), 2
            while k < len(obs) and h > 0:
                h += {'(': 1, ')': -1}.get(obs[k], 0)
                k += 1
            aritm.append(obs[m.end():k])
        for kod in [x for t in aritm + skryte(cmds) for x in nahrady(t)]:
            try:
                if not prejdi(parsuj(kod), 1):
                    return False
            except (RecursionError, ValueError, IndexError):
                return False
        # sudo, ktoré rozklad nenašiel (v komentári za príkazom, v konštrukcii, ktorej rozklad nerozumie) = pochybnosť
        v_texte = 0
        for r in obs.split('\n'):
            s = r.lstrip()
            if s.startswith('#'):
                continue
            v_texte += len(NASTROJ_SUDO_SLOVO.findall(re.sub(r'''\s#[^'"]*$''', '', s)))
        return spomenute[0] >= v_texte

    def obsah_skriptu(self, w, ctx):
        """Obsah skriptu, ktorý príkaz spúšťa: zapísaný heredocom v tomto príkaze, alebo dočasný skript v /tmp (pieskovisko
        partnera). PS 6. 9.: send.py sa zapísal v jednom príkaze a `python3 …/send.py --send` v ďalšom odoslal maily —
        odoslaná kotva zastavila len zápis (slovo smtp), rozbor by bez čítania skriptu nezastavil nič. Nástroje projektu
        (work/…, ~/projects/…) sa nečítajú: tie sa posudzujú podľa mena ako doteraz."""
        for c in [c for c, u in self.rozvin(w, ctx) if u is None][:1]:
            p = self.absolutna(c, ctx)
            if not p:
                return None
            if p in self.subory:
                self.z_disku = False
                return self.subory[p]
            # 2. 10.: kotva sama (presné meno a obsah bežiacej kotvy) sú vzory, nie akcie; domov v /tmp (falošný HOME
            # v skúške) nie je pieskovisko
            if p.startswith('/tmp/') and not ctx.vzdial and not self.v_domove(p):
                self.z_disku = True
                try:
                    if os.path.isfile(p) and os.path.getsize(p) <= 512 * 1024:
                        with open(p, 'rb') as f:
                            b = f.read()
                        stav = kotva_vlastny_stav(p, b)
                        if stav == 'okolie':     # K88S: súbor stráže, ktorý si naloží niečo cudzie
                            self.skript_nedocitany(os.path.basename(p))
                        if stav:
                            return None
                        self.skript_stav['citania'] += 1
                        return b.decode('utf-8', 'replace')
                except OSError:
                    return None
        return None

    # --- skript, ktorý príkaz spúšťa (úloha K, 2. 10.; SKRIPT_* vyššie) ---
    def v_domove(self, p):
        return p == self.domov or p.startswith(self.domov + '/')

    def smie(self, p):
        """Kotva smie pozerať na cestu p: nie do cudzieho domova (ani pokus) a nie do domova, ktorý nie je toho, pod kým
        rozbor beží (prehrávka práce kolegov beží pod PS). Rozhoduje meno, nie pokus o prístup — overenie 2. 10.: os.access
        na domov kolegu sa v prehrávke volal ~5 000-krát."""
        m = re.match(r'/home/([^/]+)(?:/|$)', p or '')
        if not m:
            return True
        return m.group(1) == self.ja and self.ja == ja_proces()

    def subor_disk(self, p):
        """KHM (16. 10.): doterajší obsah súboru, na ktorý príkaz pripisuje (>>, tee -a) — text z disku, alebo None (cudzí
        domov, chýba, nad 512 kB, binárny, súbor stráže); vtedy ostane len heredoc ako doteraz."""
        try:
            if not self.smie(p) or not os.path.isfile(p) or os.path.getsize(p) > 512 * 1024:
                return None
            with open(p, 'rb') as f:
                b = f.read()
        except (OSError, ValueError):
            return None
        if b'\x00' in b[:4096] or b.startswith(b'\x7fELF') or kotva_vlastny_stav(p, b):
            return None
        return b.decode('utf-8', 'replace')

    def je_subor(self, f):
        """KHM (16. 10.): súbor f je na disku, alebo ho tento príkaz zapísal heredocom (cat > f <<EOF, tee f) — modul, ktorý
        ten istý príkaz hneď načíta, ešte na disku nie je (alebo je tam iný). Predtým sa modul hľadal len na disku:
        `cat > mpost.py <<EOF (POST) EOF; cat > spust.py <<EOF import mpost … EOF; python3 spust.py` prešiel, hoci ten
        istý POST priamo v spust.py zastaví."""
        return f in self.subory or os.path.isfile(f)

    def je_priecinok(self, d):
        """KHM: priečinok d je na disku, alebo doň tento príkaz heredocom zapísal súbor (balík a/__init__.py, a/b.py)."""
        return os.path.isdir(d) or any(k.startswith(d + '/') for k in self.subory)

    def modul_citaj(self, f):
        """KHM: obsah modulu — zapísaný v tomto príkaze (má prednosť pred diskom, príkaz ho prepísal), inak skript_citaj."""
        if f in self.subory:
            return self.subory[f]
        return self.skript_citaj(f)

    def skript_citaj(self, p):
        """Obsah súboru skriptu, alebo None (cudzí domov, systémový program, cudzí kód, binárka), alebo SkriptVelky (textový
        súbor nad SKRIPT_MAX_SUBOR — nečíta sa celý, je nedočítaný; 3. 10., overenie kola 1: `bash velky.sh` 1,1 MB
        a `node bundle.cjs` 1,2 MB prešli bez čítania)."""
        if not p or not p.startswith('/') or NEZNAME in p:
            return None
        p = os.path.normpath(p)
        if SKRIPT_NECITAJ.search(p) or not self.smie(p):
            return None
        try:
            r = os.path.realpath(p)
            if r != p and (SKRIPT_NECITAJ.search(r) or not self.smie(r)):
                return None
            if not os.path.isfile(r):
                return None
            with open(r, 'rb') as f:
                b = f.read(SKRIPT_MAX_SUBOR + 1)
        except (OSError, ValueError):
            return None
        if b'\x00' in b[:4096] or b.startswith(b'\x7fELF'):
            return None
        stav = {kotva_vlastny_stav(p, b), kotva_vlastny_stav(r, b, p)}
        if 'vlastny' in stav:
            return None
        if 'okolie' in stav:                     # K88S: súbor stráže, ktorý si naloží niečo cudzie — nedočítaný
            self.skript_nedocitany(os.path.basename(p))
            return None
        if len(b) > SKRIPT_MAX_SUBOR:
            return SkriptVelky(b[:4096].decode('utf-8', 'replace'))
        return b.decode('utf-8', 'replace')

    def velky(self, obs, meno, jazyk=None, p=''):
        """Súbor nad strop: skript v jazyku, ktorý kotva číta (shell, python, node, Makefile, package.json), je nedočítaný
        (zastaví) → True. Iný jazyk sa nečíta ako doteraz."""
        if not isinstance(obs, SkriptVelky):
            return False
        if jazyk is None:
            jazyk = skript_jazyk(p, obs.hlava)
        if jazyk is not None:
            self.skript_nedocitany(meno)
        return True

    def vzor_cesty(self, c, zaklad):
        """Cesta s neznámym úsekom (NEZNAME) → vzor pre glob, alebo None. Relatívna sa pripojí k základu (aj ku vzoru
        /home/x/projects/*); pevný začiatok musí mať aspoň tri úrovne (/home/x/projects/…, /tmp/claude-1000/…) — vzor
        nesmie hľadať po celom disku ani v cudzích domovoch."""
        if not c:
            return None
        v = re.sub(NEZNAME + '+', '*', c)
        if not v.startswith('/'):
            if not zaklad:
                return None
            v = zaklad.rstrip('/') + '/' + v
        v = os.path.normpath(v)
        pevny = v.split('*', 1)[0]
        if pevny.count('/') < 3 or not self.smie(pevny):
            return None
        return v

    def skript_glob(self, vzor, chvost=None, cwd=None):
        """Súbory podľa vzoru → [(cesta, priečinok, z ktorého beží)]; pri `cd $p … bash <chvost>` je priečinok ten, ktorý
        vzor našiel (koreň projektu v cykle)."""
        try:
            najdene = sorted(glob.glob(vzor))
        except (OSError, ValueError, re.error):
            return []
        if len(najdene) > SKRIPT_MAX_KANDIDATOV:
            self.skript_nedocitany(os.path.basename(vzor))
            najdene = najdene[:SKRIPT_MAX_KANDIDATOV]
        von = []
        for g in najdene:
            if not self.smie(g):
                continue
            d = cwd
            if chvost:
                ch = '/' + os.path.normpath(chvost)
                if g.endswith(ch):
                    d = g[:-len(ch)] or '/'
            von.append((g, d))
        return von

    def skript_cesty(self, w, ctx):
        """Súbory skriptu zo slova w → [(cesta, priečinok, z ktorého beží)]. Známa cesta; relatívna po `cd $p` (cyklus cez
        projekty) a cesta so známym začiatkom a neznámym úsekom ("$HOME/projects/$p/x.sh") → súbory podľa vzoru; neznámy
        začiatok s konkrétnym chvostom ("$p/.aigrip/scripts/x.sh") → podpriečinky priečinka, z ktorého beží; v skripte
        neznámy začiatok ("$DIR/x.sh", "$ROOT/.aigrip/…") → priečinok skriptu a koreň jeho repozitára."""
        von = []
        zs = self.subor_zo_substitucie(w)
        if zs is not None:
            # 2. 10. večer: bash <(cat x.sh) — skript z náhrady procesu, ktorá len vypíše súbor; kolo 4: aj "$(cat x.sh)",
            # "$(< x.sh)"
            return self.skript_cesty(zs, ctx)
        kand = self.rozvin(w, ctx, 64)
        if len(kand) > 64:
            self.skript_nedocitany(w.text())       # priveľa možných ciest (cyklus cez zoznam) — nedočítané
        for c, u in kand[:64]:
            if XARGS_VSTUP in c:
                # 16. 10. (KXA): meno skriptu prišlo zo vstupu xargs alebo z {} vo find -exec (xargs_dosad)
                if self._xargs_sonda:
                    self._xargs_trafene = True
                else:
                    self.skript_nedocitany(w.text())
                continue
            if u is None and not re.search(r'[*?[]', c):
                p = self.absolutna(c, ctx)
                if p and re.search(r'[*?[]', p):     # priečinok zo vzoru: for d in ~/projects/*/; do cd "$d" && bash x.sh
                    von += self.skript_glob(self.vzor_cesty(p, None) or '', None if c.startswith('/') else c)
                elif p:
                    von.append((p, ctx.cwd))
                elif ctx.cwd_vzor and not c.startswith('/'):
                    von += self.skript_glob(self.vzor_cesty(c, ctx.cwd_vzor) or '', c)
                continue
            if u == 0 and NEZNAME in c:
                chvost = c[c.rfind(NEZNAME) + 1:]
                if not chvost.startswith('/') or NEZNAME in chvost:
                    continue
                if self.skript_cesta:
                    d = os.path.dirname(self.skript_cesta)
                    zaklady = (d, git_koren(d, self.smie), ctx.cwd, git_koren(ctx.cwd, self.smie) if ctx.cwd else None)
                    for z in dict.fromkeys(x for x in zaklady if x):
                        p = os.path.normpath(z + chvost)
                        if self.smie(p) and os.path.isfile(p):
                            von.append((p, ctx.cwd))
                    continue
                if chvost.strip('/').count('/') < 2:
                    continue          # "$X/run.sh" — kde je X, nevidno; hádať podľa jedného mena by zastavovalo falošne
            von += self.skript_glob(self.vzor_cesty(c, ctx.cwd or ctx.cwd_vzor) or '', None, ctx.cwd)
        if len(von) > SKRIPT_MAX_KANDIDATOV:
            self.skript_nedocitany(w.text())
        return von[:SKRIPT_MAX_KANDIDATOV]

    def subor_zo_substitucie(self, w):
        """Slovo je len náhrada $(cat x), $(< x) alebo <(cat x) → slovo so súborom x, inak None."""
        if not (len(w.seg) == 1 and w.seg[0][0] == 's' and w.seg[0][1] and len(w.seg[0][1]) == 1):
            return None
        c = w.seg[0][1][0]
        if c.podshell is not None or c.heredoc or c.herestr or c.vnorene:
            return None
        sl = c.slova
        subory = [x for x in sl[1:] if not x.text().startswith('-')]
        if sl and os.path.basename(sl[0].text()) == 'cat' and len(subory) == 1 and not c.presm:
            return subory[0]
        vstup = [x for op, x in c.presm if op == '<']
        if not sl and len(vstup) == 1 and len(c.presm) == 1:
            return vstup[0]
        return None

    def text_zo_echa(self, w, ctx):
        """Slovo je náhrada <(echo '…') alebo <(printf '%s' '…') → text, ktorý z nej vyjde (obsah skriptu), inak None.

        Sondy stráže 10. 10. (PS 4.86 a): `bash <(echo 'rm -rf /home/ptoth/…')` prešlo — skript nebol ani súbor, ani
        heredoc, takže ho rozbor nemal odkiaľ prečítať, hoci text stál priamo v príkaze."""
        if not (len(w.seg) == 1 and w.seg[0][0] == 's' and w.seg[0][1] and len(w.seg[0][1]) == 1):
            return None
        c = w.seg[0][1][0]
        if c.podshell is not None or c.heredoc or c.herestr or c.vnorene or c.presm or not c.slova:
            return None
        return self.text_echa_cmd(c, ctx)

    def text_echa_cmd(self, c, ctx):
        """Text, ktorý príkaz echo/printf vypíše (bez formátu %s a bez volieb), alebo None — obsah skriptu pre shell.

        Sondy stráže 10. 10.: pri `printf %s 'rm -rf …' | sh` sa do textu dostal aj formát, takže shell dostal príkaz
        s menom „%s" a rozbor nenašiel nič."""
        slova = sh_bez_klucovych(c.slova)
        if not slova:
            return None
        pb = os.path.basename(slova[0].text())
        if pb not in ('echo', 'printf'):
            return None
        sl = list(slova[1:])
        if pb == 'printf':
            sl = [x for x in sl if not x.text().startswith('-')]
            if sl and '%' in sl[0].text():
                sl = sl[1:]                      # formát nie je text
        else:
            sl = [x for x in sl if not re.fullmatch(r'-[neE]+', x.text())]
        return ' '.join(self.rozvin_text(x, ctx) for x in sl) or None

    def skript_slovo(self, pw, pb, pv, args, t, cmd, pred):
        """Príkaz spúšťa súbor skriptu → (slovo so súborom, argumenty skriptu, jazyk alebo None = podľa súboru), inak None."""
        if pb in SHELLY:
            j = skript_interpretra(pb, t)
            if j is not None and j < len(args):
                return args[j], args[j + 1:], 'sh'
            # 3. 10. (kolo 4, overenie kola 3): bash -c "$(cat x.sh)" / "$(< x.sh)" — kód je obsah súboru ($0 je prvé slovo za
            # kódom, $1 … ďalšie)
            k = 0
            c_flag = False
            while k < len(t):
                x = t[k]
                if x in ('--rcfile', '--init-file', '-o', '+o', '-O', '+O'):
                    k += 2
                    continue
                if x.startswith('--'):
                    k += 1
                    continue
                if len(x) > 1 and x[0] in '-+':
                    c_flag |= 'c' in x[1:]
                    k += 1
                    continue
                break
            if c_flag and k < len(args) and self.subor_zo_substitucie(args[k]) is not None:
                return args[k], args[k + 2:], 'sh'
            w = self.nastroj_zo_vstupu(pb, t, cmd, pred)        # bash -s a b < x.sh · cat x.sh | bash -s -- a b
            if w is None:
                return None
            k = 0
            while k < len(t) and t[k].startswith(('-', '+')) and t[k] not in ('--', '-'):
                k += 1
            if k < len(t) and t[k] in ('--', '-'):
                k += 1
            return w, args[k:], 'sh'
        if pb in ('source', '.'):
            j = 1 if t[:1] == ['--'] else 0
            if j >= len(args):
                return None
            meno = os.path.basename(t[j])
            if meno.startswith('.') or re.search(r'(?:^|[._-])env$|secret|token|credential|passw', meno, re.I):
                return None                                      # .env, nastavenia s tajomstvami — nie skript
            return args[j], args[j + 1:], 'sh'
        if pb in SKRIPT_SPUSTACE:
            # 2. 10. večer: spúšťač prostredia (uv run x.py, poetry run python x.py, npx tsx x.ts, bun x.ts, deno run x.ts):
            # skript je prvý argument s príponou skriptu
            for k, x in enumerate(t):
                if not x.startswith('-') and re.search(r'\.(?:py|sh|bash|js|mjs|cjs|ts|mts|cts)$', x):
                    return args[k], args[k + 1:], ('py' if x.endswith('.py') else 'sh' if x.endswith(('.sh', '.bash'))
                                                   else 'js')
            return None
        if INTERPRETY.match(pb):
            jaz = 'py' if pb.startswith('python') else ('js' if pb in ('node', 'nodejs') else None)
            j = skript_interpretra(pb, t) if jaz else None
            if jaz and (j is None or j >= len(args)) and not any(
                    x in ('-c', '-m', '-e', '-p', '--eval', '--print', '--check', '--test', '-h', '--help', '-V',
                          '--version', '-v') or (re.match(r'-[A-Za-z]+$', x) and re.search(r'[cmep]', x[1:]))
                    for x in t):
                # 3. 10. (kolo 4, overenie kola 3): skript zo vstupu — python3 < x.py, python3 - a < x.py, cat x.js | node
                w = self.nastroj_zo_vstupu(pb, t, cmd, pred)
                if w is not None:
                    return w, (args[t.index('-') + 1:] if '-' in t else []), jaz
            if j is None or j >= len(args):
                return None                                      # -c, -m, iný jazyk (perl, php …)
            return args[j], args[j + 1:], jaz
        if '/' in pv:                                            # ./x.sh, work/x.py, ~/bin/x, "$DIR/x.sh"
            return pw, args, None
        return None

    # --- skript zmenený v tom istom príkaze (3. 10., kolo 4; overenie kola 3: `sed -i 's/^DRY=1/DRY=0/' t/a.sh && bash
    # t/a.sh` — rozbor čítal starý obsah a vetvu s az s istotou vynechal; rovnako perl -pi, mv, cp, ln -sf) ---
    def zmena_vzor(self, c, u, ctx):
        """Cieľ zápisu (rozvinutý text c, u = index prvej neznámej časti) → ('p', cesta) alebo ('v', regulárny výraz).
        Neznámy úsek, vzor mien (*, ?, [..]) a {} z find -exec sú v regulárnom výraze čokoľvek."""
        c = c.replace('{}', NEZNAME)
        if NEZNAME not in c and not re.search(r'[*?[]', c):
            p = self.absolutna(c, ctx)
            if p:
                return 'p', p
        v = '.*'.join(re.sub(r'\\\[(?:[^\\]|\\[^\]])*\\\]', '.', re.escape(x)).replace(r'\*', '.*').replace(r'\?', '.')
                      for x in c.split(NEZNAME))
        if not c.startswith(('/', NEZNAME)):
            v = (re.escape(ctx.cwd.rstrip('/') + '/') if ctx.cwd else '(?:.*/)?') + v
        return 'v', v

    def zmena_text(self, c, u, ctx, zdroj=None, priecinok=False, nahrada=False, pripojenie=False):
        """Zaznamená zápis do cesty c; zdroj = súbor, ktorého obsah cesta dostane; priecinok = cesta je priečinok, ktorý
        dostane obsah priečinka zdroj (cp -r). Úprava na mieste (sed -i, perl -i, patch, git checkout …): skript sa posúdi
        celý, bez vynechania vetiev podľa starého obsahu. nahrada = súbor dostane celý nový obsah (>, tee, curl -o, cp, mv,
        ln …): starý obsah beží len vtedy, keď náhrada zlyhá (vtedy vynechanie vetiev v ňom platí), inak beží nový —
        posúdi sa zdroj, keď je známy (súbor, text echo/printf); neznámy obsah sa posúdiť nedá a nezaznamená sa.
        pripojenie = obsah sa pridá na koniec (>>, tee -a): starý kód beží ako predtým, posúdi sa starý obsah spolu
        s pridaným textom, keď je známy."""
        z = self.zmenene
        d, x = self.zmena_vzor(c, u, ctx)
        if (nahrada or pripojenie) and not zdroj:
            # overenie kola 4 (druhé): náhrada či pripojenie do známej cesty s obsahom, ktorý kotva nevie — starý obsah
            # nie je to, čo pobeží: vetvy sa podľa neho nevynechávajú (celý text); neznámy cieľ nič nevypína ako doteraz
            if d == 'p' and not priecinok:
                for q in dict.fromkeys([x] + ([os.path.realpath(x)] if self.smie(x) else [])):
                    zd = z['cesty'].setdefault(q, {'cele': False, 'zdroje': []})
                    zd['nezname'] = zd.get('nezname', 0) + 1     # každý zápis presmerovaním príde sem raz bez obsahu
            return
        if d == 'p' and priecinok:
            z['predpony'].append((x, zdroj, not (nahrada or pripojenie)))
        elif d == 'p':
            for q in dict.fromkeys([x] + ([os.path.realpath(x)] if self.smie(x) else [])):
                zd = z['cesty'].setdefault(q, {'cele': False, 'zdroje': []})
                zd['cele'] |= not (nahrada or pripojenie)
                if zdroj:
                    zd['zname'] = zd.get('zname', 0) + 1     # … a so známym obsahom ešte raz
                if zdroj and zdroj not in zd['zdroje']:
                    zd['zdroje'].append(zdroj)
        else:
            z['vzory'].append((re.compile(x + ('(?:/.*)?' if priecinok else '') + r'\Z', re.S), None if priecinok else zdroj,
                               not (nahrada or pripojenie)))

    def zmena_slovo(self, w, ctx, zdroj=None, priecinok=False, nahrada=False, pripojenie=False):
        for c, u in self.rozvin(w, ctx):
            if c.startswith('/dev/'):
                continue
            self.zmena_text(c, u, ctx, zdroj, priecinok, nahrada, pripojenie)

    def zmena_jedna(self, w, ctx):
        """Jediná známa hodnota slova (cesta), inak None."""
        k = self.rozvin(w, ctx)
        if len(k) == 1 and k[0][1] is None and not re.search(r'[*?[]', k[0][0]) and '{}' not in k[0][0]:
            return k[0][0]
        return None

    def zmena_rozdiel(self, w, ctx, strip):
        """Súbor rozdielu (patch, git apply) zo slova w → súbory, ktoré mení, sa zaznamenajú ako upravené na mieste. Rozdiel,
        ktorý kotva neprečíta (rúra, neznáma cesta, iný tvar), alebo bez -p pri patch = súbor s rovnakým menom kdekoľvek."""
        z = self.zmenene
        c0 = self.zmena_jedna(w, ctx) if w is not None else None
        q = self.absolutna(c0, ctx) if c0 else None
        obs = None
        if q and self.smie(q):
            try:
                if os.path.isfile(q) and os.path.getsize(q) <= SKRIPT_MAX_SUBOR:
                    with open(q, encoding='utf-8', errors='replace') as f:
                        obs = f.read()
            except (OSError, ValueError):
                obs = None
        mena = []
        for m in re.finditer(r'^(?:\+\+\+|---) ("(?:[^"\\]|\\.)*"|[^\t\n]+)|^rename (?:from|to) ([^\n]+)', obs or '', re.M):
            x = (m.group(1) or m.group(2) or '').strip().strip('"')
            if x and x != '/dev/null':
                mena.append((x, bool(m.group(2))))
        if not mena:
            z['vsetko'] = True
            return
        for x, premenovanie in mena[:400]:
            if premenovanie:
                cesta = x
            elif strip is not None and strip.isdigit():
                cesta = '/'.join(x.split('/')[int(strip):])
            else:
                cesta = NEZNAME + '/' + os.path.basename(x)     # patch bez -p: meno súboru, priečinok podľa neho
            if cesta:
                self.zmena_text(cesta, None if NEZNAME not in cesta else 0, ctx)

    def source_premenne(self, w, ctx):
        """3. 10. (kolo 4, overenie kola 3: `W=/tmp/x; source nastav.sh; rm -rf "$W"` — nastav.sh priradí W): premenné,
        ktoré načítaný súbor priraďuje (aj vo funkciách, read, printf -v, for), sú potom neznáme; súbor, ktorý kotva
        neprečíta, môže priradiť hocičo — všetky premenné sú neznáme."""
        obsahy = []
        for c, u in (self.rozvin(w, ctx) if w is not None else [(NEZNAME, 0)])[:8]:
            q = self.absolutna(c, ctx) if u is None else None
            obs = None
            if q and self.smie(q):
                obs = self.subory.get(q)
                if obs is None:
                    try:
                        if os.path.isfile(q) and os.path.getsize(q) <= SKRIPT_MAX_SUBOR:
                            with open(q, encoding='utf-8', errors='replace') as f:
                                obs = f.read()
                    except (OSError, ValueError):
                        obs = None
            if obs is None:
                for k in list(ctx.vars):
                    ctx.vars[k] = None
                return
            obsahy.append(obs)
        for obs in obsahy:
            mena = priradene_mena(obs, True)
            if mena is None:
                for k in list(ctx.vars):
                    ctx.vars[k] = None
                return
            for x in mena:
                ctx.vars[x] = None

    def zmena_vystup(self, pb, args, t, cmd, ctx):
        """Výstup príkazu do súboru (>, >>, tee) s obsahom, ktorý kotva vie: súbor na vstupe cat/sed (aj cez rúru) alebo
        text echo/printf — súbor potom dostane tento obsah (zdroj; text sa posúdi ako skript)."""
        ciele = [(op, w) for op, w in cmd.presm if op in ('>', '>>', '>|', '&>', '&>>')]
        if pb == 'tee':
            ciele += [('>>' if any(x in ('-a', '--append') for x in t) else '>', w) for w in args
                      if not w.text().startswith('-')]
        if not ciele:
            return
        zdroje = []
        if pb in ('echo', 'printf'):
            hod = []
            for w in args:
                kk = self.rozvin(w, ctx)
                hod.append(kk[0][0] if len(kk) == 1 and kk[0][1] is None and '{}' not in kk[0][0] else None)
            k = 0
            if any(h is None for h in hod):
                pb = None                      # obsah neznámy — nižšie sa zaznamená ako taký
            if pb == 'echo':
                e = False
                while k < len(hod) and re.fullmatch(r'-[neE]+', hod[k]):
                    e |= 'e' in hod[k]
                    k += 1
                text = ' '.join(hod[k:])
                if e:
                    text = text.replace('\\n', '\n').replace('\\t', '\t')
            elif pb == 'printf':
                if hod[:1] == ['--']:
                    k = 1
                if k >= len(hod):
                    return
                f, zv = hod[k], list(hod[k + 1:])
                spec = [m.group() for m in re.finditer(r'%%|%[-+ #0-9.]*[A-Za-z]', f) if m.group() != '%%']
                if any(x[-1] not in 'sbdiq' for x in spec):
                    pb = None
                else:
                    casti = []
                    while True:          # printf opakuje formát, kým zostávajú argumenty
                        casti.append(re.sub(r'%%|%[-+ #0-9.]*[sbdiq]',
                                            lambda m: '%' if m.group() == '%%' else (zv.pop(0) if zv else ''), f))
                        if not zv or not spec:
                            break
                    text = ''.join(casti).replace('\\n', '\n').replace('\\t', '\t')
            if pb is not None:
                zdroje.append(('text', text))
        elif pb in ('cat', 'sed', 'gsed', 'tee'):
            upraveny = pb in ('sed', 'gsed')
            if pb == 'tee':
                w, upraveny = self.vstup_rury(self._rura_pred.get(id(cmd), (None, None))[1], ctx) if cmd.rura else \
                    (None, False)
                slova = [w] if w is not None else []
            elif pb == 'cat':
                slova = [w for w in args if not w.text().startswith('-')]
            else:
                miesto, kod, poz = sed_volby(t)
                slova = [] if miesto else [args[k] for k in poz[0 if kod else 1:]]
            if not slova and cmd.rura and pb != 'tee':
                w, u2 = self.vstup_rury(self._rura_pred.get(id(cmd), (None, None))[1], ctx)
                slova = [w] if w is not None else []
                upraveny = upraveny or u2
            for w in slova[:8]:
                c0 = self.zmena_jedna(w, ctx)
                a = self.absolutna(c0, ctx) if c0 else None
                if a:
                    zdroje.append(('uprav', a) if upraveny else a)
        for op, w in ciele:
            for z in zdroje:
                if isinstance(z, tuple) and z[0] == 'text':
                    z = ('text', z[1], op in ('>>', '&>>'))
                self.zmena_slovo(w, ctx, z, nahrada=op not in ('>>', '&>>'), pripojenie=op in ('>>', '&>>'))

    def zmena_skriptu(self, p):
        """(príkaz skript p pred spustením mení — posúdi sa celý, [súbory alebo texty, ktorých obsah p dostane])."""
        z = self.zmenene
        if not (z['cesty'] or z['vzory'] or z['predpony'] or z['vsetko']):
            return False, []
        kand = list(dict.fromkeys([p] + ([os.path.realpath(p)] if self.smie(p) else [])))
        zm = z['vsetko']
        zdroje = []
        for q in kand:
            if q in z['cesty']:
                zm |= z['cesty'][q]['cele']
                # zápis, ktorého obsah kotva nevie (iný program, { …; } > f, printf s neznámou hodnotou …) = celý text
                zm |= z['cesty'][q].get('nezname', 0) > z['cesty'][q].get('zname', 0)
                zdroje += z['cesty'][q]['zdroje']
            for v, s, cele in z['vzory']:
                if v.match(q):
                    zm |= cele
                    if s:
                        zdroje.append(s)
            for t, s, cele in z['predpony']:
                if q == t or q.startswith(t.rstrip('/') + '/'):
                    zm |= cele
                    if s:
                        zdroje.append(os.path.normpath(s + q[len(t):]))
        return zm, [x for x in dict.fromkeys(zdroje) if x not in kand][:8]   # ('text', obsah, pripojený) · ('uprav', súbor)

    def zmena_programu(self, pb, args, t, ctx, xargs=False, cmd=None):
        """Program zapisuje do súboru (sed -i, perl -i, cp, mv, ln, install, tee, curl -o, wget, dd of=, patch, rsync,
        git checkout/restore/pull …): cieľ sa zaznamená (zmena_text). Cieľ z xargs, patch bez súboru a git, ktorý mení
        pracovný strom celý, = „hocičo“."""
        if pb not in ZMENA_PROGRAMY:
            return
        z = self.zmenene
        n = len(t)
        if pb in ('sed', 'gsed') or pb in ('perl', 'ruby'):
            miesto = kod = False
            poz = []
            k = 0
            while k < n:
                x = t[k]
                if x == '--':
                    poz += list(range(k + 1, n))
                    break
                if x.startswith('--'):
                    m, rov, _ = x.partition('=')
                    if m == '--in-place':
                        miesto = True
                    elif m in ('--expression', '--file'):
                        kod = True
                        k += 0 if rov else 1
                    elif m == '--line-length' and not rov:
                        k += 1
                    k += 1
                    continue
                if x.startswith('-') and len(x) > 1:
                    j = 1
                    while j < len(x):
                        ch = x[j]
                        if ch == 'i':
                            miesto = True                    # zvyšok je prípona zálohy (-i.bak, -ie)
                            break
                        if ch in ('ef' if pb in ('sed', 'gsed') else 'eE'):
                            kod = True
                            k += 0 if x[j + 1:] else 1
                            break
                        if pb in ('sed', 'gsed') and ch == 'l' or pb not in ('sed', 'gsed') and ch in 'IMmxdDCrV':
                            k += 0 if x[j + 1:] else (1 if pb in ('sed', 'gsed') or ch in 'IrC' else 0)
                            break
                        j += 1
                        if pb not in ('sed', 'gsed') and ch in '0l':
                            while j < len(x) and x[j] in '0123456789abcdefxABCDEF':
                                j += 1
                    k += 1
                    continue
                poz.append(k)
                k += 1
            if not miesto:
                return
            if xargs:
                z['vsetko'] = True
            for k in poz[0 if kod else 1:]:
                self.zmena_slovo(args[k], ctx)
            return
        if pb == 'tee':
            for k, x in enumerate(t):
                if not x.startswith('-'):
                    a = any(y in ('-a', '--append') for y in t)
                    self.zmena_slovo(args[k], ctx, nahrada=not a, pripojenie=a)
            if xargs:
                z['vsetko'] = True
            return
        if pb == 'dd':
            for k, x in enumerate(t):
                if x.startswith('of='):
                    for c, u in self.rozvin(args[k], ctx):
                        self.zmena_text(c[3:], None if u is None else max(u - 3, 0), ctx, nahrada=True)
            return
        if pb in ('curl', 'wget'):
            k = 0
            while k < n:
                x = t[k]
                if (pb == 'curl' and x in ('-o', '--output')) or (pb == 'wget' and x in ('-O', '--output-document')):
                    if k + 1 < n:
                        self.zmena_slovo(args[k + 1], ctx, nahrada=True)
                    k += 2
                    continue
                # zhluk krátkych volieb: curl -sSLo f, -sSLof · wget -qO f, -qOf (-O- = výstup na obrazovku)
                m = re.fullmatch(r'-([sSLfkvqiIgGNnRZ#]*)o(.*)' if pb == 'curl' else r'-([qvnc]*)O(.*)', x)
                if pb == 'wget' and x.startswith('--output-document='):
                    for c, u in self.rozvin(args[k], ctx):
                        self.zmena_text(c.split('=', 1)[1], None if u is None else 0, ctx, nahrada=True)
                elif m and not m.group(2) and k + 1 < n:
                    if t[k + 1] != '-':
                        self.zmena_slovo(args[k + 1], ctx, nahrada=True)
                    k += 2
                    continue
                elif m and m.group(2) != '-':
                    od = len(x) - len(m.group(2))
                    for c, u in self.rozvin(args[k], ctx):
                        self.zmena_text(c[od:], None if u is None else max(u - od, 0), ctx, nahrada=True)
                elif re.match(r'https?://[^?#\s]*/([^/?#\s]+)', x) and (pb == 'wget' or any(
                        re.fullmatch(r'-[a-zA-Z#]*O[a-zA-Z#]*', y) or y in ('--remote-name', '--remote-name-all') for y in t)):
                    meno = re.match(r'https?://[^?#\s]*/([^/?#\s]+)', x).group(1)
                    self.zmena_text(meno, None, ctx, nahrada=True)   # curl -O, wget bez -O: meno z adresy v priečinku príkazu
                k += 1
            return
        if pb == 'patch':
            poz = []
            k = 0
            strip, vstup = None, None
            while k < n:
                x = t[k]
                if x in ('--dry-run', '--help', '--version', '-v'):
                    return
                if x in ('-o', '--output'):
                    if k + 1 < n:
                        self.zmena_slovo(args[k + 1], ctx)
                    k += 2
                    continue
                if x in ('-i', '--input', '-p', '--strip'):
                    if k + 1 < n:
                        if x in ('-i', '--input'):
                            vstup = args[k + 1]
                        else:
                            strip = t[k + 1]
                    k += 2
                    continue
                if x in ('-d', '--directory', '-r', '--reject-file', '-B', '--prefix', '-D', '--ifdef', '-F', '--fuzz', '-V',
                         '--version-control', '-z', '--suffix', '-Y', '--basename-prefix', '-g', '--get'):
                    if x in ('-d', '--directory'):
                        z['vsetko'] = True
                    k += 2
                    continue
                if x.startswith('-'):
                    if x.startswith(('-d', '--directory=')):
                        z['vsetko'] = True
                    elif re.fullmatch(r'-p\d+|--strip=\d+', x):
                        strip = re.sub(r'\D', '', x)
                    elif x.startswith('--input='):
                        z['vsetko'] = True               # súbor rozdielu v texte voľby — kotva ho nečíta
                    k += 1
                    continue
                poz.append(k)
                k += 1
            if poz:
                self.zmena_slovo(args[poz[0]], ctx)
                return
            # 3. 10. (kolo 4): súbory menuje rozdiel na vstupe (patch -p1 < x.diff) — prečíta sa; inak hocičo
            if vstup is None and cmd is not None:
                vstup = next((w for op, w in cmd.presm if op == '<'), None)
            self.zmena_rozdiel(vstup, ctx, strip)
            return
        if pb == 'git':
            sub, j, C, gd, ma_c = git_rozbor(t)
            r = t[j + 1:]
            ra = args[j + 1:]
            ctx2 = ctx
            if C:
                c0 = self.zmena_jedna(args[C[-1]], ctx)
                ctx2 = Ctx(self.absolutna(c0, ctx) if c0 else None, ctx.hlbka)
            if sub in ('checkout', 'restore'):
                if '--' in r:
                    cesty = list(range(r.index('--') + 1, len(r)))
                elif sub == 'restore':
                    cesty = [k for k, x in enumerate(r) if not x.startswith('-')
                             and not (k and r[k - 1] in ('-s', '--source', '--pathspec-from-file'))]
                    if any(x.startswith('--pathspec-from-file') for x in r):
                        z['vsetko'] = True
                else:
                    if r and not (len(r) == 2 and r[0] in ('-b', '-B')):
                        z['vsetko'] = True           # vetva či cesta — git mení pracovný strom
                    return
                if sub == 'restore' and any(x in ('--staged', '-S') for x in r) and \
                        not any(x in ('--worktree', '-W') for x in r):
                    return                           # len index
                for k in cesty:
                    self.zmena_slovo(ra[k], ctx2, priecinok=False)
                    self.zmena_slovo(ra[k], ctx2, priecinok=True)
                return
            if sub == 'apply':
                if not any(x in ('--check', '--stat', '--numstat', '--summary', '--cached') for x in r) \
                        or '--apply' in r:
                    # 3. 10. (kolo 4): súbory menuje rozdiel (git apply x.diff, predvolene -p1); --directory a pod. = hocičo
                    strip, subory = '1', []
                    for k, x in enumerate(r):
                        if re.fullmatch(r'-p\d+', x):
                            strip = x[2:]
                        elif x.startswith(('--directory', '--include', '--exclude', '-C')) or x == '-p':
                            z['vsetko'] = True
                        elif not x.startswith('-'):
                            subory.append(ra[k])
                    if not subory and cmd is not None:
                        subory = [w for op, w in cmd.presm if op == '<'][:1]
                    for w in subory or [None]:
                        self.zmena_rozdiel(w, ctx2, strip)
                return
            if sub in GIT_MENI_STROM:
                if sub == 'reset' and not any(x in ('--hard', '--merge', '--keep') for x in r):
                    return
                if sub == 'stash' and r[:1] and r[0] in ('list', 'show', 'drop', 'clear', 'create', 'store', 'branch'):
                    return
                z['vsetko'] = True
            return
        # cp, mv, install, ln, rsync
        ciel = None                  # index slova s priečinkom (-t)
        ciel_text = None
        bez_dir = symb = adr = rek = False
        poz = []
        k = 0
        sarg = {'cp': 'tS', 'mv': 'tS', 'ln': 'tS', 'install': 'tSmog', 'rsync': 'efTB'}[pb]
        larg = {'--target-directory', '--suffix', '--mode', '--owner', '--group', '--strip-program', '--rsh',
                '--exclude', '--include', '--filter', '--files-from', '--temp-dir', '--partial-dir', '--backup-dir',
                '--rsync-path', '--compare-dest', '--copy-dest', '--link-dest', '--chown', '--chmod'}
        while k < n:
            x = t[k]
            if x == '--':
                poz += list(range(k + 1, n))
                break
            if x.startswith('--'):
                m, rov, h = x.partition('=')
                if m == '--target-directory':
                    if rov:
                        ciel_text = h
                    else:
                        ciel = k + 1
                elif m == '--no-target-directory':
                    bez_dir = True
                elif m == '--symbolic':
                    symb = True
                elif m == '--recursive' or m == '--archive':
                    rek = True
                elif m == '--directory' and pb == 'install':
                    adr = True
                if m in larg and not rov:
                    k += 1
                k += 1
                continue
            if x.startswith('-') and len(x) > 1:
                for j, ch in enumerate(x[1:], 1):
                    if ch == 's' and pb == 'ln':
                        symb = True
                    elif ch == 'd' and pb == 'install':
                        adr = True
                    elif ch == 'T' and pb != 'rsync':
                        bez_dir = True
                    elif ch in 'rRa':
                        rek = True
                    if ch in sarg:
                        if ch == 't' and pb != 'rsync':
                            if x[j + 1:]:
                                ciel_text = x[j + 1:]
                            else:
                                ciel = k + 1
                        if not x[j + 1:]:
                            k += 1
                        break
                k += 1
                continue
            poz.append(k)
            k += 1
        if adr:
            return                                   # install -d vytvára priečinky
        if xargs:
            z['vsetko'] = True
        if ciel is not None and ciel < n:
            ciel_w = args[ciel]
            zdroje, dest = poz, None
        elif ciel_text is not None:
            ciel_w = None
            zdroje, dest = poz, None
        elif pb == 'ln' and len(poz) == 1:
            ciel_w, ciel_text = None, '.'
            zdroje, dest = poz, None
        elif len(poz) >= 2:
            ciel_w = args[poz[-1]]
            zdroje, dest = poz[:-1], poz[-1]
        else:
            return
        if pb == 'rsync' and ciel_w is not None and re.match(r'[^/]*:', t[poz[-1]] if dest is not None else ''):
            return                                   # cieľ na inom stroji
        dtext = ciel_text if ciel_w is None else self.zmena_jedna(ciel_w, ctx)
        dabs = self.absolutna(dtext, ctx) if dtext is not None else None
        do_priecinka = dest is None or len(zdroje) > 1 or (dtext or '').endswith('/') or \
            (dabs is not None and self.smie(dabs) and os.path.isdir(dabs))
        if bez_dir and dest is not None:
            do_priecinka = False
        for k in zdroje:
            stext = self.zmena_jedna(args[k], ctx)
            if stext is None or (pb == 'rsync' and re.match(r'[^/]*:', stext)):
                sabs = None                          # zdroj neznámy alebo na inom stroji
            elif symb and not stext.startswith('/'):
                zaklad = dabs if do_priecinka else (os.path.dirname(dabs) if dabs else None)
                sabs = os.path.normpath(os.path.join(zaklad, stext)) if zaklad else None
            else:
                sabs = self.absolutna(stext, ctx)
            sdir = sabs is not None and self.smie(sabs) and os.path.isdir(sabs)
            meno = os.path.basename((stext or '').rstrip('/')) if stext else None
            # náhrada: skript dostane obsah zdroja (cp, mv, ln, install, rsync) — posúdi sa zdroj; bez známeho zdroja nič
            if dabs is not None and do_priecinka:
                if pb == 'rsync' and (stext or '').endswith('/'):
                    self.zmena_text(dabs, None, ctx, sabs, True, nahrada=True)     # rsync a/ b: obsah a do b
                elif meno:
                    self.zmena_text(os.path.join(dabs, meno), None, ctx, sabs, sdir, nahrada=True)
            elif dabs is not None:
                self.zmena_text(dabs, None, ctx, sabs, sdir, nahrada=True)
            else:
                # cieľ s neznámou hodnotou: súbor aj súbor v ňom (priečinok)
                for c, u in (self.rozvin(ciel_w, ctx) if ciel_w is not None else [(dtext or NEZNAME, 0)]):
                    self.zmena_text(c, u, ctx, sabs, sdir, nahrada=True)
                    if meno:
                        self.zmena_text(c.rstrip('/') + '/' + meno, u, ctx, sabs, sdir, nahrada=True)

    def skript_spusteny(self, pw, pb, pv, args, t, pred_slova, cmd, ctx, sudo, pred):
        """Skript, ktorý príkaz spúšťa (úloha K): prečíta sa a posúdi; kanál do cudzieho systému v ňom je kanál príkazu.
        Pod sudo zastaví sudo (obsah beží pod iným menom); na inom stroji a v kontajneri sa súbor nevidí."""
        # 20. 10. 2026 (sľub 3.09): pod sudo sa skript ČÍTA, aby otázka menovala aj to, čo robí. Prvý pokus ráno
        # sondy zhoršil, lebo poznámka „skript som čítal ako ja“ sa pripisovala do textu kanála a tým sa text
        # rozišiel so základom; teraz je z nej príznak (sudo-skript), ktorý doplní vetu háčik. Zakrytých kanálov
        # je po tejto zmene a po oprave skrátenia v sondách 0 z 2416 variantov, bez jediného nálezu.
        if self.iba_nastroje or self.v_nastroji or ctx.vzdial or ctx.kontajner:
            return
        if sudo:
            self.sudo_skript = True
        try:
            if pb in ('make', 'gmake'):
                self.make_spusteny(args, t, ctx, [x.text() for x in pred_slova if self.je_priradenie(x)])
                return
            if pb in ('npm', 'pnpm', 'yarn', 'bun') and \
                    self.npm_spusteny(pb, args, t, ctx, [x.text() for x in pred_slova if self.je_priradenie(x)]):
                return
            if re.match(r'^python[0-9.]*$', pb) and \
                    self.py_m_spusteny(args, t, ctx, [x.text() for x in pred_slova if self.je_priradenie(x)]):
                return
            najdene = self.skript_slovo(pw, pb, pv, args, t, cmd, pred)
            if not najdene:
                return
            w, zvysok, jazyk = najdene
            self.skript_stav['citania'] += 1           # rozbor tohto miesta závisí od súboru, nielen od textu
            prefix = [x.text() for x in pred_slova if self.je_priradenie(x)]
            for p, cwd2 in self.skript_cesty(w, ctx):
                if p in self.subory:
                    # zapísaný heredocom v tom istom príkaze: shell rozoberá celý spusti_skript; python a node (overenie
                    # kola 4, druhé: cat > x.py <<EOF … EOF; python3 x.py — dovtedy len pošta) sa posúdia celé tu
                    obs = self.subory[p]
                    jaz = jazyk or skript_jazyk(p, obs)
                    if jaz in ('py', 'js'):
                        for prio, (text, vnutri) in self.skript_rozbor(p, obs, jaz, zvysok, cwd2, ctx, prefix, True,
                                                                       False, True, True, p + '#heredoc').items():
                            self.zapis_skriptu(prio, text, vnutri, os.path.basename(p))
                    continue
                # dočasný skript z /tmp napísal partner sám: bez výnimky a celý (spusti_skript ho pri známej ceste rozoberá
                # tiež; tu aj zo vzoru a zvnútra iného skriptu). Domov v /tmp je len v skúške (falošný HOME) — to je domov.
                tmp = p.startswith('/tmp/') and not self.v_domove(p)
                if tmp and not self.vlastne_tmp(p):
                    continue
                # 3. 10. (kolo 4, overenie kola 3): príkaz skript pred spustením upraví na mieste (sed -i, perl -i, patch,
                # git checkout …) — obsah na disku nie je ten, čo pobeží: vetvy sa podľa neho nevynechávajú (celý text);
                # alebo nahradí (cp, mv, ln, install, rsync, výstup do súboru) — posúdi sa aj obsah, ktorý skript dostane
                zmeneny, zdroje = self.zmena_skriptu(p)
                kk = 0
                while kk < len(zdroje) and kk < 16:
                    q0 = zdroje[kk]
                    kk += 1
                    if isinstance(q0, str) and self.skript_citaj(q0) is None:
                        zm2, zd2 = self.zmena_skriptu(q0)
                        zmeneny = True
                        zdroje += [x for x in zd2 if x not in zdroje and x != p]
                for q in [p] + zdroje:
                    # celý text: p upravené na mieste (vtedy aj každý obsah, ktorý p dostalo), zdroj upravený filtrom
                    # (sed … a.sh > f.sh, cat a.sh | sed … | bash) alebo zdroj sám upravený na mieste
                    cele = zmeneny
                    if isinstance(q, tuple) and q[0] == 'text':   # text z echo/printf (> súbor; pri >> za starým obsahom)
                        stary = self.skript_citaj(p) if q[2] else None
                        obs = (stary + '\n' if isinstance(stary, str) and not isinstance(stary, SkriptVelky) else '') + q[1]
                        q = p + '#' + hashlib.sha1(obs.encode('utf-8', 'replace')).hexdigest()
                    else:
                        if isinstance(q, tuple):     # ('uprav', súbor): obsah súboru upravený filtrom
                            q = q[1]
                            cele = True
                        elif q != p:
                            cele = cele or self.zmena_skriptu(q)[0]
                        obs = self.skript_citaj(q)
                    if obs is None or self.velky(obs, os.path.basename(q), jazyk, q):
                        continue
                    jaz = jazyk or skript_jazyk(p, obs) or (skript_jazyk(q, obs) if q != p else None)
                    if jaz is None and jazyk is None and pb not in ('source', '.') and not obs.startswith('#!') \
                            and '.' not in os.path.basename(p).lstrip('.'):
                        jaz = 'sh'                   # 3. 10. (kolo 4): ./x bez shebangu a prípony — bash ho spustí ako shell
                    if jaz is None:
                        continue                 # iný jazyk (perl, php …) — rozbor kódu ho nepozná dosť
                    for prio, (text, vnutri) in self.skript_rozbor(p, obs, jaz, zvysok, cwd2, ctx, prefix, tmp,
                                                                   pb in ('source', '.'), skript_bash(pb, t, obs),
                                                                   cele, q).items():
                        self.zapis_skriptu(prio, text, vnutri, os.path.basename(p))
        except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, OSError, re.error):
            # 3. 10. (overenie kola 1: RecursionError pri 2000 vnoreniach $( ) skript pustil): chyba rozboru = nedočítané
            self.skript_nedocitany(pb)

    def py_m_spusteny(self, args, t, ctx, prefix):
        """15. 10. (K88YB): python3 -m meno — modul z pracovného priečinka (pri -m je sys.path[0]) sa spustí ako hlavný
        skript a posúdi sa ako skript: a.b → a/__init__.py (balík sa pri načítaní spustí), a/b.py, alebo a/b/__init__.py
        a a/b/__main__.py. Predtým sa -m nečítal: `python3 -m posli` z priečinka modulu s poštou prešiel, hoci
        `python3 posli.py` zastaví. Modul, ktorý v pracovnom priečinku nie je (štandardná knižnica, nainštalovaný balík),
        sa nečíta ako doteraz. → True, keď príkaz spúšťa modul cez -m (vtedy nespúšťa súbor skriptu)."""
        k = 0
        meno = None
        while k < len(t):
            x = t[k]
            if x in ('-W', '-X', '--check-hash-based-pycs'):
                k += 2
                continue
            vz = re.fullmatch(r'-([A-Za-z]*?)([cmWX])(.*)', x, re.S)
            if vz and vz.group(2) == 'm':                   # -m meno, -mmeno, -um meno (zlúčené voľby)
                meno, k = (vz.group(3), k + 1) if vz.group(3) else (t[k + 1] if k + 1 < len(t) else None, k + 2)
                break
            if x == '-' or not x.startswith('-') or (vz and vz.group(2) == 'c'):
                return False                                # súbor skriptu, vstup, kód v -c
            k += 2 if vz and not vz.group(3) else 1
        if meno is None:
            return False
        if not re.fullmatch(r'[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*', meno) or not ctx.cwd or NEZNAME in ctx.cwd \
                or not ctx.cwd.startswith('/'):
            return True
        casti = meno.split('.')
        d = os.path.join(ctx.cwd, *casti)
        kand = [os.path.join(ctx.cwd, *casti[:j], '__init__.py') for j in range(1, len(casti))]
        kand += [d + '.py'] if self.je_subor(d + '.py') else [os.path.join(d, '__init__.py'), os.path.join(d, '__main__.py')]
        for f in kand:
            if f not in self.subory and (not self.smie(f) or not os.path.isfile(f)):
                continue
            tmp = f.startswith('/tmp/') and not self.v_domove(f)
            if tmp and f not in self.subory and not self.vlastne_tmp(f):
                continue
            self.skript_stav['citania'] += 1
            obs = self.modul_citaj(f)
            if obs is None or self.velky(obs, os.path.basename(f), 'py', f):
                continue
            for prio, (text, vnutri) in self.skript_rozbor(f, obs, 'py', args[k:], ctx.cwd, ctx, prefix, tmp,
                                                           False, True, False, f).items():
                self.zapis_skriptu(prio, text, vnutri, os.path.basename(f))
        return True

    def make_spusteny(self, args, t, ctx, prefix):
        """make [-C priečinok] [-f súbor] [-n] [X=y] [cieľ …] (3. 10., overenie D08): recepty cieľov a ich predpokladov
        z Makefile sa posúdia ako skript — kanál do cudzieho systému v nich zastaví, hlásenie menuje Makefile a cieľ.
        -n/--dry-run recepty nespúšťa (okrem riadkov s + a $(MAKE)). Makefile, ktorý sa nedá prečítať, ako neznámy skript."""
        adr, subor, nasucho, cmd_prem, ciele = ctx.cwd, None, False, {}, []

        def cesta(h, zaklad):
            if h is None or NEZNAME in h or not (zaklad or h.startswith('/')):
                return None
            return os.path.normpath(os.path.join(zaklad or '/', h))
        k, n = 0, len(args)
        while k < n:
            x = self.hodnota_slova(args[k], ctx)
            if x is None:
                if not t[k].startswith('-'):
                    ciele = None                 # cieľ či premenná, ktorú kotva nevie prečítať — ktorýkoľvek cieľ
                k += 1
                continue
            if x == '--':
                k += 1
                continue
            if x.startswith('--'):
                meno, rov, hod = x.partition('=')
                if meno in ('--directory', '--file', '--makefile', '--include-dir', '--old-file', '--assume-old',
                            '--what-if', '--new-file', '--assume-new'):
                    if not rov:
                        hod = self.hodnota_slova(args[k + 1], ctx) if k + 1 < n else None
                        k += 1
                    if meno == '--directory':
                        adr = cesta(hod, adr)
                    elif meno in ('--file', '--makefile'):
                        subor = NEZNAME if hod is None else hod
                elif meno in ('--just-print', '--dry-run', '--recon'):
                    nasucho = True
                k += 1
                continue
            if x.startswith('-') and len(x) > 1:
                j = 1
                while j < len(x):
                    ch = x[j]
                    if ch in 'CfIoW':
                        hod = x[j + 1:]
                        if not hod:
                            hod = self.hodnota_slova(args[k + 1], ctx) if k + 1 < n else None
                            k += 1
                        if ch == 'C':
                            adr = cesta(hod, adr)
                        elif ch == 'f':
                            subor = NEZNAME if hod is None else hod
                        break
                    if ch in 'jl':
                        if not x[j + 1:] and k + 1 < n and re.fullmatch(r'[\d.]+', t[k + 1]):
                            k += 1
                        break
                    if ch == 'O':
                        break
                    if ch == 'n':
                        nasucho = True
                    j += 1
                k += 1
                continue
            m = re.match(r'^([A-Za-z_.][\w.-]*)[ \t]*(?::{1,3}=|\+=|\?=|=)(.*)$', x, re.S)
            if m:
                cmd_prem[m.group(1)] = m.group(2)
            elif ciele is not None:
                ciele.append(x)
            k += 1
        if not adr or subor == '-' or (subor and NEZNAME in subor):
            return
        kand = [os.path.join(adr, x) for x in ('GNUmakefile', 'makefile', 'Makefile')] if subor is None \
            else [os.path.normpath(os.path.join(adr, subor))]
        p = next((c for c in kand if self.smie(c) and os.path.isfile(c)), None)
        if p is None:
            return
        tmp = p.startswith('/tmp/') and not self.v_domove(p)
        if tmp and not self.vlastne_tmp(p):
            return
        obs = self.skript_citaj(p)
        if obs is None or self.velky(obs, os.path.basename(p), 'sh'):
            return
        self.skript_stav['citania'] += 1
        prem = {'MAKE': 'make', 'RM': 'rm -f', 'SHELL': '/bin/sh', 'CURDIR': adr}
        for x in prefix:                         # X=1 make … — premenná prostredia (Makefile ju môže prepísať)
            m = PRIRADENIE.match(x)
            if m and not m.group(2):
                prem[m.group(1)] = x[m.end():]

        def nacitaj(c):
            c = os.path.normpath(c)
            x = self.skript_citaj(c) if self.smie(c) else None
            return None if self.velky(x, os.path.basename(c), 'sh') else x
        try:
            mf = MakeSubor(obs, prem, cmd_prem, nacitaj, adr)
            text = mf.recepty(ciele, nasucho)
        except (ValueError, RecursionError, IndexError, KeyError, TypeError, re.error):
            self.skript_nedocitany(os.path.basename(p))
            return
        if not text.strip():
            return
        meno = os.path.basename(p) + ':' + (','.join(ciele) if ciele else (mf.prvy or '') if ciele == [] else '*')
        for prio, (txt, vnutri) in self.skript_rozbor(os.path.join(os.path.dirname(p), meno), text, 'sh', [], adr, ctx,
                                                       prefix, tmp, bash=False).items():
            self.zapis_skriptu(prio, txt, vnutri, meno)

    def npm_spusteny(self, pb, args, t, ctx, prefix):
        """npm/pnpm/yarn/bun run <meno> (aj test, start, stop, restart; yarn a pnpm aj <meno> skriptu, ktoré nie je ich
        príkaz): text skriptu z najbližšieho package.json (aj pre<meno> a post<meno>) s argumentmi za menom sa posúdi ako
        skript (3. 10., overenie D08). Pracovné priestory (-w, --filter, -r) kotva nečíta. → True, keď to bol skript balíka."""
        adr, k, n = ctx.cwd, 0, len(t)
        priestory = ('-w', '--workspace', '--workspaces', '-ws', '--filter', '-F', '-r', '--recursive')
        while k < n and t[k].startswith('-'):
            meno, rov, hod = t[k].partition('=')
            if meno in ('--prefix', '-C', '--dir', '--cwd'):
                if not rov:
                    hod = self.hodnota_slova(args[k + 1], ctx) if k + 1 < n else None
                    k += 1
                else:
                    hod = self.hodnota_slova(args[k], ctx)
                    hod = hod.split('=', 1)[1] if hod and '=' in hod else None
                adr = None if hod is None or NEZNAME in hod or not (adr or hod.startswith('/')) \
                    else os.path.normpath(os.path.join(adr or '/', hod))
            elif meno in priestory:
                return False
            k += 1
        if k >= n or not adr:
            return False
        prik = self.hodnota_slova(args[k], ctx)
        if prik is None:
            return False
        if prik in ('run', 'run-script', 'rum', 'urn'):
            j = k + 1
            while j < n and t[j].startswith('-'):
                if t[j].split('=', 1)[0] in priestory:
                    return False
                j += 1
            if j >= n:
                return False                     # npm run bez mena = zoznam skriptov
            meno, zvysok = self.hodnota_slova(args[j], ctx), args[j + 1:]
        elif prik in ('test', 't', 'tst', 'start', 'stop', 'restart') and pb != 'bun':
            meno, zvysok = {'t': 'test', 'tst': 'test'}.get(prik, prik), args[k + 1:]
        elif pb in NPM_VSTAVANE and prik not in NPM_VSTAVANE[pb] and not prik.startswith('-'):
            meno, zvysok = prik, args[k + 1:]
        else:
            return False
        p, d = None, adr
        for _ in range(16):
            c = os.path.join(d, 'package.json')
            if not self.smie(c):
                break
            if os.path.isfile(c):
                p = c
                break
            if d in ('/', self.domov):
                break
            d = os.path.dirname(d)
        if p is None:
            return False
        tmp = p.startswith('/tmp/') and not self.v_domove(p)
        if tmp and not self.vlastne_tmp(p):
            return True
        obs = self.skript_citaj(p)
        if self.velky(obs, 'package.json', 'sh'):
            return True
        skripty = npm_skripty(obs) if obs is not None else None
        if skripty is None:
            return False
        self.skript_stav['citania'] += 1
        if meno is None:
            mena = sorted(x for x in skripty if not x.startswith(('pre', 'post')))   # meno nevidno — ktorýkoľvek skript
        elif meno in skripty or meno == 'restart' and ('stop' in skripty or 'start' in skripty):
            mena = [meno]
        else:
            return pb == 'npm' or prik in ('run', 'run-script', 'rum', 'urn')     # neexistujúci skript — nič nebeží
        arg, za = [], pb != 'npm'
        for w in zvysok:
            h = self.hodnota_slova(w, ctx)
            if h == '--' and not za:
                za = True
                continue
            if h == '--' and not arg and pb != 'npm':
                continue
            if not za and h is not None and h.startswith('-'):
                continue                         # npm: voľba pred -- patrí npm, nie skriptu
            arg.append(MAKE_NEZNAME if h is None else "'" + h.replace("'", "'\"'\"'") + "'")
        bez_pred_po = '--ignore-scripts' in t
        casti = []
        for m in mena:
            for s in (['stop', 'start'] if m == 'restart' and 'restart' not in skripty else [m]):
                if not bez_pred_po and 'pre' + s in skripty:
                    casti.append(skripty['pre' + s])
                if s in skripty:
                    casti.append(skripty[s] + (' ' + ' '.join(arg) if arg and s == m else ''))
                if not bez_pred_po and 'post' + s in skripty:
                    casti.append(skripty['post' + s])
        if not casti:
            return True
        nazov = 'package.json:' + (meno or '*')
        text = '\n'.join('(\n' + x + '\n)' for x in casti)
        for prio, (txt, vnutri) in self.skript_rozbor(os.path.join(os.path.dirname(p), nazov), text, 'sh', [],
                                                       os.path.dirname(p), ctx, prefix, tmp, bash=False).items():
            self.zapis_skriptu(prio, txt, vnutri, nazov)
        return True

    def kod_v_prikaze(self, pb, t, cmd, ctx, sudo, pred=None):
        """Kód interpretra priamo v príkaze (python3 - <<EOF, python3 -c, node -e): príkazy, ktoré spúšťa (subprocess,
        execSync, obal az(…), zoznam ['az', …]), posúdi ten istý rozbor (úloha K) — `python3 - <<EOF … subprocess.run(['az',
        'ad', 'app', 'credential', 'reset', …])` aj `node -e "…execSync('az group delete …')"` prešli. Poštu a marketing
        v tomto kóde posudzuje posta() a mkt_interpret ako doteraz; tu sa neznáma hodnota neberie ako poštový program."""
        if self.iba_nastroje or self.v_nastroji or ctx.vzdial or ctx.kontajner:
            return
        if sudo:
            self.sudo_skript = True
        try:
            self._kod_v_prikaze(pb, t, cmd, ctx, pred)
        except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, OSError, re.error):
            self.skript_nedocitany('kód v príkaze')      # 3. 10.: chyba rozboru kódu = nedočítané, nie „nič“

    def _kod_v_prikaze(self, pb, t, cmd, ctx, pred=None):
        # 10. 10. (nezávislý beh): kód sa čítal len pythonu a node — `perl -e 'system("curl -X POST https://…")'`
        # (aj ruby -e, php -r) prešlo, hoci ten istý príkaz priamo kotva zastaví. Tokeny aj volania vie rozbor kódu
        # pre pl, rb a php rovnako (kod_jazyk, kod_tokeny); čítanie súboru skriptu ostáva na skript_spusteny.
        jaz = 'py' if re.match(r'^python[0-9.]*$', pb) else ('php' if re.match(r'^php[0-9.]*$', pb)
                                                             else KOD_V_PRIKAZE.get(pb))
        if not jaz or (jaz == 'py' and '-m' in t):
            return                                   # modul (-m) sa nečíta
        kody = []
        for k, x in enumerate(t):
            if k + 1 >= len(t):
                break
            if x in KOD_VOLBY.get(jaz, ()) or (jaz in ('pl', 'rb') and re.fullmatch(r'-[A-Za-z]*[eE]', x)):
                kody.append(t[k + 1])
                break
        if not kody:
            if skript_interpretra(pb, t) is not None:
                return                               # súbor skriptu rozoberá skript_spusteny
            kody = [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr]
        if not kody and cmd.rura and pred is not None and pred.slova:
            # 2. 10. večer (overenie: `echo "…subprocess.run(['az', …, 'reset'])" | python3` prešlo): kód z echo/printf
            # v rúre pred interpreterom
            ps = [w.text() for w in sh_bez_klucovych(pred.slova)] or ['']
            if os.path.basename(ps[0]) == 'echo':
                k = 1
                while k < len(ps) and re.fullmatch(r'-[neE]+', ps[k]):
                    k += 1
                kody = [' '.join(ps[k:])]
            elif os.path.basename(ps[0]) == 'printf' and len(ps) > 1:
                kody = [' '.join(ps[2:])] if ps[1] in ('%s', '%s\\n') else [ps[1]]
        st = self.skript_stav
        for kod in kody[:4]:
            if not kod:
                continue
            if st['bajty'] + len(kod) > SKRIPT_MAX_BAJTOV:
                self.skript_nedocitany('kód v príkaze')
                continue
            # 2. 10. večer: ten istý kód (python3 - <<EOF v háčiku, ktorý je v 60 projektoch) sa rozoberie raz, keď
            # rozbor nečítal ďalšie súbory (výsledok potom nezávisí od miesta)
            kluc = ('kod', hashlib.sha1(kod.encode('utf-8', 'replace')).hexdigest(), jaz)
            if kluc not in st['obsah']:
                st['bajty'] += len(kod)
                citania = st['citania']
                sub = Rozbor(self.hook_cwd, self.domov)
                sub.skript_hlbka = self.skript_hlbka + 1
                sub.skript_stav = st
                sub.len_program = True
                sub.subory = self.subory
                sub.zmenene = self.zmenene
                sub.mkt_subory = self.mkt_subory
                try:
                    sub.skript_kod_rozbor(kod, jaz, Ctx(ctx.cwd, 1), None, True)
                except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, re.error):
                    sub.skript_nedocitany('kód v príkaze')
                # Sondy stráže 10. 10. (PS 4.86 a): kód PRIAMO V PRÍKAZE sa posudzoval ako skript z disku — platili
                # len odchádzajúce kanály (SKRIPT_KANALY), takže `python3 -c 'os.system("rm -rf /home/ptoth/…")'`
                # a `node -e 'execSync("git -C /home/ptoth/… push")'` prešli, hoci ten istý príkaz priamo zastaví.
                # Dôvod výberu platí pre skript z disku (podmienené vetvy, ktoré rozbor nevyhodnotí) — kód v príkaze
                # napísal partner sám a rozbor ho vidí celý, preto tu platia všetky kanály ako pri priamom príkaze.
                vysl = {pr: (text, None) for pr, text in sub.kanaly.items()}
                for pr, (text, vnutri) in sub.skript_nalezy.items():
                    vysl.setdefault(pr, (text, vnutri))
                if st['citania'] != citania:
                    for pr, (text, vnutri) in vysl.items():
                        self.zapis_skriptu(pr, text, vnutri, None)
                    continue
                st['obsah'][kluc] = vysl
            for pr, (text, vnutri) in st['obsah'][kluc].items():
                self.zapis_skriptu(pr, text, vnutri, None)

    def zapis_skriptu(self, prio, text, vnutri, vonku):
        """Kanál zo skriptu: v rozbore skriptu sa odovzdá vyššie, v príkaze partnera je to kanál s menom skriptu."""
        if self.skript_hlbka:
            self.skript_nalezy.setdefault(prio, (text, vnutri))
            return
        if self.iba_nastroje:
            return
        if prio == P_NASADENIE:      # nasadenie bez mena skriptu — výnimka 4.35 a (skoršia veta človeka) platí ďalej
            pass
        elif vnutri is None:         # kód interpretra priamo v príkaze (kod_v_prikaze)
            text = f'{text} (v kóde, ktorý príkaz spúšťa)'
        else:
            text = f'{text} (v skripte {vnutri}' + (f' cez {vonku}' if vonku and vonku != vnutri else '') + ')'
        self.kanaly.setdefault(prio, text)
        self.priznaky.add('skript')

    def skript_nedocitany(self, meno):
        """Skript, ktorý mal byť prečítaný, ostal nečítaný pre strop (súbory, bajty, vnorenie, čas, priveľa kandidátov):
        kotva nevie, či v ňom nie je neobnoviteľný krok — zastaví (overenie 2. 10.: strop ticho púšťal)."""
        if self.skript_hlbka:
            self.skript_nalezy.setdefault(P_SKRIPT, (K_SKRIPT, meno))
        else:
            self.kanal(P_SKRIPT, K_SKRIPT)
            self.priznaky.add('skript')

    def skript_tokeny(self, zvysok, ctx):
        """Argumenty vyvolania skriptu: text, None (jeden argument s neznámou hodnotou — premenná či $(…) v úvodzovkách,
        cyklus cez známe slová), SH_ROZDEL (neznáma hodnota bez úvodzoviek: môže sa rozdeliť na viac argumentov aj na
        žiadny — overenie 2. 10.: `--app $a --plan` s a='x --run'), alebo celé None pri vzore mien súborov či {a,b}
        (počet argumentov aj ich poradie neznáme)."""
        von = []
        for w in zvysok:
            if any(sg[0] == 'l' and not sg[2] and (re.search(r'[*?[]', sg[1]) or re.search(r'\{[^}]*(?:,|\.\.)[^}]*\}', sg[1]))
                   for sg in w.seg):
                return None
            if w.viac:
                von.append(SH_ROZDEL)          # "$@", "${pole[@]}": počet argumentov neznámy, aj žiadny
                continue
            nekv = any(sg[0] != 'l' and j not in (w.v_uvod or ()) for j, sg in enumerate(w.seg))
            k = self.rozvin(w, ctx)
            if any(SKRIPT_NEZNAME in c for c, _ in k):
                von.append(SH_ROZDEL)          # neznáma hodnota z kódu (sys.argv[1:], premenná) — ani počet nie je známy
                continue
            if k and all(u is None for _, u in k):
                hodn = {c for c, _ in k}
                if nekv and any(not c or re.search(r'\s', c) or re.search(r'[*?[]', c) for c in hodn):
                    von.append(SH_ROZDEL)
                else:
                    von.append(hodn.pop() if len(hodn) == 1 else None)
                continue
            von.append(SH_ROZDEL if nekv else None)
        return von

    def skript_rozbor(self, p, obs, jaz, zvysok, cwd, ctx, prefix, tmp=False, zdroj=False, bash=True, cele=False,
                      obsah_z=None, modul=False, mod_volania=()):
        """Obsah skriptu posúdený tým istým rozborom ako príkaz → {priorita: (text kanála, meno skriptu, kde je)}; len
        SKRIPT_KANALY. Ten istý skript s tými istými argumentmi sa v jednom príkaze nečíta druhý raz (aj cyklus volaní);
        rovnaký obsah s rovnakými argumentmi (ten istý háčik v 60 projektoch) sa rozoberá raz, keď rozbor nečítal ďalšie
        súbory. Strop SKRIPT_MAX_*, SKRIPT_CAS a SKRIPT_STENA chráni čas kotvy (háčik dáva rozboru 15 s); čo pre strop ostane nečítané,
        zastaví (P_SKRIPT). cele = príkaz skript pred spustením mení — vetvy sa nevynechávajú; obsah_z = súbor, z ktorého
        je obsah (zdroj cp/mv/ln; inak p). modul = lokálny modul, ktorý python načíta (py_moduly).

        14. 10. (K88X): modul beží v tom istom procese ako ten, kto ho načítal — nepridáva úroveň vnorenia (strop
        SKRIPT_HLBKA ráta skripty, ktoré sa spúšťajú; reťaz modulov drží kľúč videne, SKRIPT_MAX_SUBOROV, SKRIPT_CAS a
        strop rekurzie, ktorý zastaví) a v jednom príkaze sa rozoberie raz pre cestu, obsah, pracovný priečinok,
        prostredie a zmenené súbory — bez ohľadu na to, kto ho načítal. S D2 (K88T) si `python3 ng-stav.py` naložil
        reťaz ng-stav → ng-postup → ng-zapisnik (moduly) → … → ng-audit-straz.py na 9. úrovni a stráž ho zastavila
        ako nedočítaný; ng-zapisnik.py sa rozoberal 21-krát, ng-audit.py 39-krát (výsledok s ďalšími čítaniami sa
        nedal uložiť podľa obsahu, lebo bez cesty v kľúči by neplatil)."""
        st = self.skript_stav
        meno = os.path.basename(p)
        if self.skript_hlbka >= SKRIPT_HLBKA and not modul:
            return {P_SKRIPT: (K_SKRIPT, meno)}
        rp = os.path.realpath(p)
        if st.setdefault('rodicia', []).count(rp) >= 2:
            cele = True          # skript spúšťa sám seba druhý raz: celý text, raz pre každý obsah (kľúč bez argumentov)
        kluc = (os.path.realpath(p), cwd, None if cele else tuple(x.text() for x in zvysok[:9]),
                None if cele else tuple(prefix), cele,
                None if obsah_z in (None, p) else obsah_z if '#' in obsah_z else os.path.realpath(obsah_z))
        # 14. 10. (K88V): prostredie skriptu — exportované premenné (export, declare -x, set -a), pri source všetky
        # premenné volajúceho, a priradené pred príkazom s hodnotou, ako ju rozbor pozná (X=$D/x bash s.sh — predtým
        # doslovný text „$D/x“); je aj v kľúčoch, aby ten istý skript s iným prostredím nezostal neprečítaný
        prostr = {m: ctx.vars.get(m) for m in ctx.exp}
        if zdroj:
            prostr.update({m: v for m, v in ctx.vars.items()
                           if not m.isdigit() and m not in ('#', '@', '*', '0', 'BASH_SOURCE')})
        for x in prefix:
            m = PRIRADENIE.match(x)
            if m and not m.group(2):
                v = x[m.end():]
                prostr[m.group(1)] = ctx.vars.get(m.group(1)) if re.search(r'[$`]', v) and m.group(1) in ctx.vars else [v]
        prostr_kluc = tuple(sorted((m, None if v is None else tuple(v)) for m, v in prostr.items()))
        kluc = kluc + (prostr_kluc,)
        if modul:
            # kľúč modulu má obsah (zúžený podľa načítajúceho — iný načítajúci môže volať iné funkcie) a nie je zhodný
            # s kľúčom toho istého súboru spusteného ako skript (ten sa posudzuje aj s hlavnou časťou)
            zm = self.zmenene
            kluc = ('modul', rp, hashlib.sha1(obs.encode('utf-8', 'replace')).hexdigest(), cwd, prostr_kluc,
                    len(zm['cesty']), len(zm['vzory']), len(zm['predpony']), zm['vsetko'], tuple(mod_volania))
            if kluc in st.setdefault('moduly', {}):
                return dict(st['moduly'][kluc])
            if kluc in st['videne']:
                # modul sa práve rozoberá (kruh importov) — výsledok tých, čo ho načítali, je neúplný, neukladá sa
                st['mod_neuplne'] = st.get('mod_neuplne', 0) + 1
                return {}
            neuplne0 = st.get('mod_neuplne', 0)
        if kluc in st['videne']:
            return {}
        tokeny = self.skript_tokeny(zvysok, ctx)
        if zdroj and not zvysok:
            # 3. 10. (overenie kola 3): skript načítaný cez source/. bez argumentov dedí $1 … $# volajúceho — argumenty
            # sú neznáme, nie prázdne (`. lib.sh` v tools/entra.sh rotate: lib vetví podľa "${1:-}" a vetvu rotate kotva
            # s istotou vynechala)
            tokeny = None
        kluc_obsah = (hashlib.sha1(obs.encode('utf-8', 'replace')).hexdigest(), jaz, tmp, zdroj, bash, cele,
                      None if tokeny is None else tuple(x if x is not SH_ROZDEL else '<rozdel>' for x in tokeny),
                      tuple(prefix), prostr_kluc, tuple(mod_volania))
        if kluc_obsah in st['obsah']:
            return dict(st['obsah'][kluc_obsah])
        if st['subory'] >= SKRIPT_MAX_SUBOROV or st['bajty'] + len(obs) > SKRIPT_MAX_BAJTOV \
                or time.process_time() - st['zaciatok_cpu'] > SKRIPT_CAS or time.monotonic() - st['zaciatok'] > SKRIPT_STENA:
            return {P_SKRIPT: (K_SKRIPT, meno)}
        videne0 = set(st['videne'])
        st['videne'].add(kluc)
        st['subory'] += 1
        st['bajty'] += len(obs)
        citania = st['citania']
        st.setdefault('texty', []).append(obs)

        def posud(kod):
            sub = Rozbor(self.hook_cwd, self.domov)
            sub.skript_hlbka = max(1, self.skript_hlbka) if modul else self.skript_hlbka + 1
            sub.skript_cesta = p
            sub.skript_stav = st
            sub.len_program = True
            sub.mod_volania = tuple(mod_volania)
            sub.subory = self.subory
            sub.zmenene = self.zmenene
            sub.mkt_subory = self.mkt_subory
            c2 = Ctx(cwd, 1)
            c2.vars['0'] = [p]
            c2.vars['BASH_SOURCE'] = [p]
            # $1 … $9 skriptu. Od argumentu s neznámym počtom slov ("$@", $X bez úvodzoviek, vzor mien) sa pozície posúvajú:
            # $k je ktorékoľvek slovo od neho po k-te alebo neznáme (3. 10.; predtým pevná pozícia)
            r = 0 if tokeny is None else next((j for j, x in enumerate(tokeny) if x is SH_ROZDEL), len(tokeny))
            for k in range(1, min(len(zvysok), 9) + 1):
                if k - 1 < r:
                    c2.vars[str(k)] = [c for c, _ in self.rozvin(zvysok[k - 1], ctx)] or None
                else:
                    c2.vars[str(k)] = [c for w in zvysok[r:k + 1] for c, _ in self.rozvin(w, ctx)] + [NEZNAME]
            for m, v in prostr.items():                 # X=1 bash x.sh, export X — premenná platí v skripte
                c2.vars[m] = v
            c2.exp = frozenset(prostr) | (ctx.exp if zdroj else frozenset())
            c2.allexport = ctx.allexport and zdroj
            st['rodicia'].append(rp)
            try:
                if jaz == 'sh':
                    sub.prikazy(parsuj(kod), c2)
                else:
                    sub.skript_kod_rozbor(kod, jaz, c2, p, tmp)
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, re.error):
                # 3. 10. (overenie kola 1: 2000 vnorených $( ) — RecursionError a skript prešiel): časť skriptu kotva
                # neprečítala — nedočítané (zastaví), nie „nič“
                sub.skript_nedocitany(meno)
            finally:
                st['rodicia'].pop()
            v = {pr: (sub.kanaly[pr], meno) for pr in SKRIPT_KANALY if pr in sub.kanaly}
            for pr, x in sub.skript_nalezy.items():
                v.setdefault(pr, (x[0], x[1] or meno))      # kód interpretra v skripte (python3 - <<EOF) = tento skript
            return v

        # node bez vetiev režimov (js_rezimy) a python bez nevolaných funkcií (skript_rezimy) — vždy; vetvy, ktoré sa pri
        # týchto argumentoch určite nespustia (abstraktný beh sh_volby/py_volby), len keď celý skript kanál má — vynechanie
        # kód len uberá, skript bez kanála ho nezíska (výkon). Skript z /tmp sa posudzuje celý; shell, ktorý nie je bash
        # (sh = dash, zsh, recept make, skript npm), abstraktný beh nevynecháva — beh pozná len bash ([[ ]] v dash neplatí).
        toky = [NEZNAME] if tokeny is None else [x if isinstance(x, str) else NEZNAME for x in tokeny]
        if tmp:
            von = posud(obs)
        elif cele:
            # 3. 10. (kolo 4): skript zmenený v tom istom príkaze — celý text, bez vynechania vetiev, režimov a funkcií
            von = posud(sh_obaly(obs) if jaz == 'sh' else skript_kod(obs, jaz))
        else:
            von = posud(skript_rezimy(sh_obaly(obs) if jaz == 'sh' else skript_kod(obs, jaz), jaz, toky))
            if von and (jaz == 'py' or (jaz == 'sh' and bash)):
                orezany = sh_volby(obs, tokeny, prefix, zdroj) if jaz == 'sh' else py_volby(obs, tokeny, prefix)
                if orezany != obs:
                    st['videne'] = videne0 | {kluc}       # vnorené skripty sa v druhom priechode posúdia znova
                    von = posud(skript_rezimy(sh_obaly(orezany) if jaz == 'sh' else skript_kod(orezany, jaz), jaz, toky))
        if st['citania'] == citania:
            st['obsah'][kluc_obsah] = dict(von)           # rozbor nečítal nič ďalšie — rovnaký obsah dá rovnaký výsledok
        if modul and st.get('mod_neuplne', 0) == neuplne0:
            st['moduly'][kluc] = dict(von)                # cesta je v kľúči — platí aj s ďalšími čítaniami
        return von

    def skript_kod_rozbor(self, obs, jaz, c2, p, tmp=False):
        """Kód skriptu (python, node): pošta a marketing ako pri kóde v príkaze, a príkazy, ktoré kód spúšťa — volania
        subprocess/execSync …, obal pomenovaný podľa nástroja, zoznam argumentov nástroja — rozborom shellu. Hodnota, ktorú
        kotva nevie prečítať, je tu neutrálna (SKRIPT_NEZNAME), nie poštový program: zastaví len to, čo v kóde naozaj je.
        Pošta: poštové volanie so sieťou (smtplib, Graph /sendMail, webhook Teams) a načítanie poštového modulu s pokynom
        odoslať; poštový program, ktorý kód spúšťa, nájde rozbor spúšťaných príkazov. Pravidlo „meno poštového nástroja
        v kóde + volanie s neznámou hodnotou = odoslanie“ (kód v príkaze) sa na skript nepoužije — meno býva v popise
        (verify-mailbox-scope.sh v hlavičke sweep-app-scope.py) a zastavovalo by každý skript, ktorý niečo spúšťa.
        Dočasný skript z /tmp (tmp): poštu a marketing v jeho kóde posudzuje posta() a mkt_interpret ako doteraz (aj
        s pokynom na čítanie — mail_koncept.py list); tu len príkazy, ktoré spúšťa."""
        kd = skript_kod(obs, jaz)
        if not tmp:
            try:
                if ((POSTA_VOLANIE.search(kd) and kod_posta_siet(kd, jaz, nezname=False)) or kod_posta_sama(kd, jaz)
                        or (jaz == 'py' and skript_nacita_postu(kd))):
                    self.kanal(P_POSTA, K_POSTA)
            except Exception:
                # 1. 11. 2026: ticho tu znamenalo, že keď rozbor pošty spadne, skript prejde ako neškodný —
                # ochrana klame. Kotva nevie, či skript posiela: to je nedočítané (zastaví), nie „nič“ —
                # tá istá odpoveď ako pri strope (skript_nedocitany) a pri chybe rozboru marketingu (mkt_http).
                self.skript_nedocitany(os.path.basename(p) if p else 'kód skriptu')
            self.mkt_kody([kd])
        kr = KodRozbor(obs, jaz, dict(KOD_ZNAME, __file__=p, __filename=p, __dirname=os.path.dirname(p)) if p else None)
        try:
            # zápis cez API; keď kód posiela poštu, hlásenie menuje poštu (graph_mail.py send: POST na Graph je odoslanie)
            zname_zapis, cudzie_adresy, necitane = self.kod_http_zapis(kr, kd, cely=not p)
            if zname_zapis and P_POSTA not in self.kanaly:
                self.kanal(P_CURL, K_CURL)
            if (cudzie_adresy or necitane) and P_POSTA not in self.kanaly:
                self.cudzia_kod(cudzie_adresy, necitane, os.path.basename(p) if p else None)
        except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, re.error):
            self.skript_nedocitany(os.path.basename(p) if p else 'kód')
        texty = []
        for druh, meno, z, kon in list(kr.volania()):
            if druh != 'spusta' or len(texty) >= SKRIPT_MAX_VOLANI:
                continue
            try:
                poz, kl = kr.argumenty(z, kon, 0)
                if 'args' in kl:
                    poz = [kl['args']] + poz
                cw = [kr.text(h) for h in kl.get('cwd', [])]
                cw = cw[0] if len(cw) == 1 and cw[0] and NAJHORSIE not in cw[0] else None
                texty += [(x, cw) for x in kr.prikazy_volania(poz, kl.get('input'))]
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, re.error):
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')
                continue
        texty += [(x, None) for x in self.kod_cli(kr)]
        hotove = set()
        for txt, cw in texty[:SKRIPT_MAX_VOLANI]:
            txt = txt.replace(NAJHORSIE, SKRIPT_NEZNAME)
            if (txt, cw) in hotove:
                continue
            hotove.add((txt, cw))
            c3 = c2.kopia()
            c3.hlbka = c2.hlbka
            if cw:
                c3.cwd = self.absolutna(cw, c2) or c3.cwd
            try:
                self.prikazy(parsuj(sh_obaly(txt)), c3)
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, re.error):
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')
                continue
        if jaz == 'py':
            try:
                self.py_moduly(kd, kr, c2, p)
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, OSError, re.error):
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')
        elif jaz == 'js':
            try:
                self.js_moduly(kd, kr, c2, p)
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError, OSError, re.error):
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')

    def js_moduly(self, kd, kr, c2, p):
        """15. 10. (K88YB): lokálny modul, ktorý node načíta — require('./x'), import … from './x', import './x',
        import('./x'), export … from './x' — sa posúdi celý: pri načítaní sa spustí jeho kód na najvyššej úrovni
        a načítajúci volá, čo modul vyvezie (module.exports je hodnota, mená sa zúžiť nedajú). Predtým `node hlavny.js`
        s require('./posli.js')() prešiel, hoci `node posli.js` zastaví. Cesta len relatívna (./, ../) alebo absolútna,
        doslova alebo z __dirname (path.join); balík z node_modules sa nečíta. Rozlíšenie ako v node: presný súbor,
        s príponou .js/.cjs/.mjs, priečinok s index.js; .json (dáta) sa nečíta. Základ je priečinok skriptu, pri kóde v príkaze pracovný priečinok."""
        if p:
            zaklad = os.path.dirname(p)
        else:
            zaklad = c2.cwd if c2.cwd and NEZNAME not in c2.cwd and c2.cwd.startswith('/') else None
        if not zaklad:
            return
        spec = []
        for druh, meno, z, kon in kr.volania():
            if druh == 'nacita' and meno.split('.')[-1] == 'require':
                poz, kl = kr.argumenty(z, kon, 0)
                spec += [kr.text(h) for h in (poz[0] if poz else [])]
        for m in re.finditer(r'''\b(?:import|export)\b[^;'"`()]*?\bfrom\s*(['"])([^'"\n]+)\1|(?:^|[;{}])[ \t]*import\s*(['"])([^'"\n]+)\3'''
                             r'''|\bimport\s*\(\s*(['"`])([^'"`\n$]+)\5\s*\)''', kd, re.M):
            spec.append(m.group(2) or m.group(4) or m.group(6))
        subory = []
        for s in spec:
            if not s or NAJHORSIE in s or not s.startswith(('./', '../', '/')):
                continue
            a = os.path.normpath(s if s.startswith('/') else os.path.join(zaklad, s))
            if a.endswith(('.json', '.node')):
                continue                                    # dáta JSON a binárny doplnok node nespúšťa ako kód
            for c in (a, a + '.js', a + '.cjs', a + '.mjs', os.path.join(a, 'index.js'), os.path.join(a, 'index.cjs'),
                      os.path.join(a, 'index.mjs')):
                if c in self.subory or (self.smie(c) and os.path.isfile(c)):      # KHM: aj modul napísaný v príkaze
                    subory.append(c)
                    break
        hotove = set()
        vlastny = os.path.realpath(p) if p else None
        for f in subory:
            if f in hotove or (vlastny and os.path.realpath(f) == vlastny):
                continue
            hotove.add(f)
            if len(hotove) > SKRIPT_MAX_VOLANI:
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')
                break
            self.skript_stav['citania'] += 1
            obs = self.modul_citaj(f)
            if obs is None or self.velky(obs, os.path.basename(f), 'js', f):
                continue
            for prio, (text, vnutri) in self.skript_rozbor(f, obs, 'js', [], c2.cwd, c2, [], False, False, True,
                                                           True, modul=True).items():
                self.zapis_skriptu(prio, text, vnutri, os.path.basename(p) if p else None)

    def py_moduly(self, kd, kr, c2, p):
        """14. 10. (K88T, D2): lokálny modul, ktorý python načíta, sa prečíta a posúdi ako skript — celý, lebo jeho funkcie
        volá ten, kto ho načítal. Predtým stráž čítala len skript, ktorý príkaz spúšťa: modul s poštou načítaný cez
        importlib (cesta doslova aj z argumentu) alebo `import posli` z priečinka skriptu prešiel, hoci ten istý súbor
        spustený priamo zastaví. Import podľa mena (aj import_module, __import__ s menom) sa hľadá v priečinku skriptu
        (sys.path[0]; pri kóde v príkaze pracovný priečinok) a v priečinkoch ostatných skriptov pythonu v tom istom
        rozbore — aj modul s menom zo štandardnej knižnice (v priečinku skriptu zatieni pôvodný); balík a.b.c = a,
        a.b aj a.b.c. Cesta (spec_from_file_location, SourceFileLoader, load_source, run_path) doslova, z __file__
        alebo sys.argv[k] skriptu. Cesta ani meno, ktoré rozbor neprečíta, nezastaví nič (neznáma hodnota v skripte);
        súbory stráže posúdi skript_citaj (živé sa nečítajú, kópia prejde posudkom alebo sa číta ako ostatné)."""
        st = self.skript_stav
        korene = st.setdefault('py_korene', [])
        if p:
            vlastne = [os.path.dirname(p), os.path.dirname(os.path.realpath(p))]
        else:
            vlastne = [c2.cwd] if c2.cwd and NEZNAME not in c2.cwd and c2.cwd.startswith('/') else []
        for z in vlastne:
            if z and z not in korene and len(korene) < 16:
                korene.append(z)
        # KMP (15. 10.): priečinky, ktoré kód pridá do sys.path (insert, append, extend, site.addsitedir) alebo príkaz
        # do PYTHONPATH — relatívny od pracovného priečinka; ktorý rozbor neprečíta, je neznámy (nižšie)
        pridane, nezname_cesty = self.py_sys_path(kd, kr, c2)
        zaklady = list(dict.fromkeys([z for z in vlastne if z] + korene + pridane))
        mena = set(py_importy(kd))
        cesty = []
        for druh, meno, z, kon in kr.volania():
            if druh != 'nacita':
                continue
            posledne = meno.split('.')[-1]
            poz, kl = kr.argumenty(z, kon, 0)
            if posledne in ('import_module', '__import__'):
                for h in (poz[0] if poz else kl.get('name', [])):
                    s = kr.text(h)
                    if re.fullmatch(r'[A-Za-z_][\w.]*', s):
                        mena.add(s)
            elif posledne in ('spec_from_file_location', 'SourceFileLoader', 'load_source', 'run_path'):
                k = 0 if posledne == 'run_path' else 1
                hod = poz[k] if len(poz) > k else kl.get('location', kl.get('path', kl.get('path_name', [])))
                # K88YA: spec_from_file_location bez loadera vyberá loader podľa prípony — súbor bez prípony pythonu
                # (.jsonl) načítať nevie (spec je None); taká hodnota je iná premenná s tým istým menom (parameter p
                # pomocnej funkcie a p = '…/sedenie.jsonl' na najvyššej úrovni), nie modul
                len_py = posledne == 'spec_from_file_location' and len(poz) < 3 and 'loader' not in kl
                for h in hod:
                    s = kr.text(h)
                    if NAJHORSIE not in s and s and not (len_py and not re.search(r'\.pyw?$', s)):
                        cesty.append(s)
        # K88YB: cesta v parametri pomocnej funkcie (def load(p, n): spec_from_file_location(n, p) … load('…/posli.py', 'x'))
        # — hodnota z každého volania tej funkcie v tom istom kóde (aj kľúčový argument, aj metóda volaná cez bodku bez self).
        # Predtým bola cesta z parametra neznáma a modul s poštou sa nečítal.
        t = kr.t
        for fmeno, idx, pmeno, len_py, metoda in py_nacitanie_z_parametra(kd):
            for k in range(len(t) - 1):
                d, v = t[k]
                if d != 'n' or t[k + 1] != ('p', '(') or v.split('.')[-1] != fmeno or (k and t[k - 1] == ('n', 'def')):
                    continue
                cez_bodku = '.' in v or (k > 0 and t[k - 1] == ('p', '.'))
                poz, kl = kr.argumenty(k + 2, kr.par.get(k + 1, len(t)), 0)
                j = None if idx is None else idx - (1 if metoda and cez_bodku else 0)
                for h in (poz[j] if j is not None and 0 <= j < len(poz) else kl.get(pmeno, [])):
                    s = kr.text(h)
                    if NAJHORSIE not in s and s and not (len_py and not re.search(r'\.pyw?$', s)):
                        cesty.append(s)
        # sys.argv[k] ako cesta modulu: hodnota argumentu, s ktorým bol skript spustený
        for m in re.finditer(r'(?:(?:spec_from_file_location|SourceFileLoader|load_source)\s*\([^,()]*,|run_path\s*\()'
                             r'\s*(?:location\s*=\s*|path\s*=\s*)?sys\.argv\[(\d)\]', kd):
            cesty += [x for x in (c2.vars.get(m.group(1)) or []) if x and NEZNAME not in x]
        subory = []
        for c in cesty:
            a = self.absolutna(c, c2)
            if a:
                subory.append(a)
        for meno in sorted(mena):
            casti = meno.split('.')
            for zaklad in zaklady:
                for k in range(1, len(casti) + 1):
                    d = os.path.join(zaklad, *casti[:k])
                    subory += [d + '.py', os.path.join(d, '__init__.py')]
        if nezname_cesty:
            # KMP: modul, ktorý môže prísť z neprečítaného priečinka — nie je pri skripte ani v známych priečinkoch, nie je
            # zo štandardnej knižnice ani nainštalovaný — je nedočítaný (zastaví)
            for meno in sorted(mena):
                top = meno.split('.')[0]
                if py_modul_mimo_kodu(top) or any(self.je_subor(os.path.join(z, top + '.py'))
                                                  or self.je_priecinok(os.path.join(z, top)) for z in zaklady):
                    continue
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')
                break
        # 14. 10. (K88X): strop SKRIPT_MAX_VOLANI platí pre súbory, ktoré naozaj sú — predtým pre kandidátov (meno × priečinok
        # × .py/__init__.py), takže skript so 120 importmi pred `import posli` modul s poštou ticho nečítal; nad strop
        # je skript nedočítaný (zastaví). realpath až pri existujúcom súbore (ng-stav.py: 17 057 volaní, 0,4 s).
        hotove = set()
        vlastny = os.path.realpath(p) if p else None
        for f in subory:
            f = os.path.normpath(f)
            if f in hotove or not self.je_subor(f):             # KHM: aj modul napísaný v tomto príkaze
                continue
            hotove.add(f)
            if vlastny and os.path.realpath(f) == vlastny:
                continue
            if len(hotove) > SKRIPT_MAX_VOLANI:
                self.skript_nedocitany(os.path.basename(p) if p else 'kód')
                break
            # K88YA: poštový nástroj podľa mena (graph_mail.py), keď načítajúci má pokyn odoslať — rozhoduje pravidlo
            # nástroja s pokynom (29. 9.: list = čítanie, send = odoslanie), modul sa nečíta. Čítaný modul menoval POST na
            # Graph ako zápis cez API namiesto pošty a v dočasnom skripte, ktorý sa posudzuje celý, zastavil aj výpis
            # konceptov (mail_koncept.py list). Bez pokynu v načítajúcom sa modul číta ako ostatné.
            if POSTA_NASTROJ_KOD.fullmatch(os.path.basename(f)) and KOD_POSTA_POKYN.search(kd):
                continue
            st['citania'] += 1
            obs = self.modul_citaj(f)
            if obs is None or self.velky(obs, os.path.basename(f), 'py', f):
                continue
            obs = py_modul_text(obs, kd, py_meno_modulu(f))     # K88X: len to, čo sa pri načítaní spustí
            for prio, (text, vnutri) in self.skript_rozbor(f, obs, 'py', [], c2.cwd, c2, [], False, False, True,
                                                           True, modul=True,
                                                           mod_volania=py_volania_modulu(kr, kd, obs)
                                                           + py_volania_dat(kr, kd, obs)
                                                           if any(kod_http_predbezne(obs)) else ()).items():
                self.zapis_skriptu(prio, text, vnutri, os.path.basename(p) if p else None)

    def py_sys_path(self, kd, kr, c2):
        """KMP (15. 10.): priečinky, v ktorých python bude hľadať moduly navyše → (známe absolútne cesty, je aj neznámy?).
        12. 10. 15:29 (aspecta-finops): skript v /tmp s `sys.path.insert(0, ".")` načítal `ion` z pracovného priečinka
        a volal rozhranie TD Synnex — stráž hľadala modul len pri skripte a pustila ho. Číta sa sys.path.insert(i, x),
        append(x), extend([…]) (aj na module sys pod iným menom a `from sys import path`), site.addsitedir(x) a PYTHONPATH
        z príkazu. Relatívna cesta ("", ".", "lib") je od pracovného priečinka (python ju tak berie pri importe), os.getcwd()
        a Path.cwd() je pracovný priečinok, cesta od __file__ od skriptu (KodRozbor). Neznámy je priečinok, ktorého hodnotu
        rozbor neprečíta, relatívny pri neznámom pracovnom priečinku alebo keď kód mení priečinok (chdir), __file__ kódu
        v príkaze a iná úprava sys.path (priradenie, rez, +=)."""
        # KMP (2c, 16. 10.): rozbor `ng-stav.py` (135 modulov) bol s KMP o 0,35 s dlhší, 0,3 s pod stropom 3,5 s — hľadania
        # začínajúce hranicou slova (\b…) prejdú text 20× pomalšie než tie, ktoré začínajú doslovným textom; hranica sa
        # tu overuje znakom pred nálezom (_py_slovo_pred). Na 4 077 súboroch pythonu rovnaký výsledok ako predtým.
        # Kód bez úpravy cesty (polovica modulov) sa vráti hneď.
        if not _py_syspath_pred(kd) and not c2.vars.get('PYTHONPATH'):
            return [], False
        cwd = c2.cwd if c2.cwd and NEZNAME not in c2.cwd and c2.cwd.startswith('/') else None
        hodnoty = []
        nezname = False
        sys_mena = {'sys'} | {m.group(1) for m in re.finditer(r'import[ \t]+(?:[\w.]+[ \t]*,[ \t]*)*sys[ \t]+as[ \t]+(\w+)', kd)
                              if not _py_slovo_pred(kd, m.start())}
        hole = any(not _py_slovo_pred(kd, m.start())
                   for m in re.finditer(r'from[ \t]+sys[ \t]+import\b[^\n]*\bpath\b', kd))
        for m in re.finditer(r'path[ \t]*(?:\[[^\]\n]*\][ \t]*)?(?:\+=|=(?!=))', kd):
            k = m.start()
            if _py_slovo_pred(kd, k):
                continue
            if k and kd[k - 1] == '.':          # <meno sys>.path = …, .path[…] = …, .path += …
                j = k - 1
                while j and _py_slovo_pred(kd, j):
                    j -= 1
                if kd[j:k - 1] in sys_mena:
                    nezname = True
                    break
            elif hole:                          # from sys import path; path = …
                nezname = True
                break
        t = kr.t
        # KMP (2b, 16. 10.): sys.path.insert(0, sys.argv[2]) — priečinok z argumentu, s ktorým bol skript spustený
        # (c2.vars '1'…'9' ako pri spec_from_file_location vyššie). Prehrávka 7 dní: EP (md.py, 13. 10.) a aigrip
        # (kontext.py) mali modul s čistou úpravou textu v priečinku z argumentu — bez toho nedočítané (zastaví).
        # Keď kód sys.argv mení (priradenie, pop, insert, del, argparse to nerobí), pozícia neplatí — neznámy.
        argv_mena = {s + '.argv' for s in sys_mena}
        argv_meni = None
        memo0, kr.memo = kr.memo, {}
        kr.cwd_hodnota = cwd or NAJHORSIE
        kr.domov_hodnota = self.domov if self.domov and self.domov.startswith('/') else None
        try:
            # KMP (2c): kandidáti jedným prechodom (rozbor ng-stav.py prechádza 635 tisíc tokenov v 135 moduloch)
            for k in [k for k, x in enumerate(t[:-1]) if x[0] == 'n' and x[1].endswith(_PY_SYSPATH_MET)]:
                d, v = t[k]
                if t[k + 1] != ('p', '(') or (k and t[k - 1] == ('p', '.')):
                    continue
                casti = v.split('.')
                if casti[-1] == 'addsitedir' and len(casti) <= 2:
                    j = 0
                elif casti[-1] in ('insert', 'append', 'extend') and len(casti) >= 2 and casti[-2] == 'path' \
                        and ((len(casti) == 3 and casti[0] in sys_mena) or (len(casti) == 2 and hole)):
                    j = 1 if casti[-1] == 'insert' else 0
                else:
                    continue
                if casti[-1] != 'extend':
                    roz = kr.delenie(k + 2, kr.par.get(k + 1, len(t)))
                    if len(roz) > j:
                        a, b = roz[j]
                        if b - a == 4 and t[a][1] in argv_mena and t[a + 1] == ('p', '[') and t[a + 3] == ('p', ']') \
                                and str(t[a + 2][1]).isdigit():
                            if argv_meni is None:
                                argv_meni = bool(re.search(r'\bargv[ \t]*(?:\[[^\]\n]*\][ \t]*)?(?:\+=|=(?!=))|\bargv\.'
                                                           r'(?:pop|insert|remove|append|extend|clear|reverse|sort)\b'
                                                           r'|\bdel[ \t]+[\w.]*argv\b', kd))
                            arg = None if argv_meni else c2.vars.get(str(t[a + 2][1]))
                            hodnoty += arg if arg else [NEZNAME]
                            continue
                poz, kl = kr.argumenty(k + 2, kr.par.get(k + 1, len(t)), 0)
                if len(poz) <= j:
                    nezname = True
                    continue
                for h in poz[j]:
                    hodnoty += h[1] if h[0] == 'a' and casti[-1] == 'extend' else [kr.text(h)]
        finally:
            kr.memo, kr.cwd_hodnota, kr.domov_hodnota = memo0, None, None
        hodnoty += [x for v in (c2.vars.get('PYTHONPATH') or []) for x in (v.split(':') if NEZNAME not in v else [NEZNAME])]
        meni_cwd = any(not _py_slovo_pred(kd, m.start()) for m in re.finditer(r'chdir\s*\(', kd))
        von = []
        for x in hodnoty[:32]:
            # KMP (1c, 16. 10.): ~ a ~/… je domov (expanduser); ~iný/… je cudzí domov, ktorý rozbor nepozná
            if x == '~' or x.startswith('~/'):
                x = (self.domov.rstrip('/') + x[1:]) if self.domov and self.domov.startswith('/') else NEZNAME
            elif x.startswith('~'):
                x = NEZNAME
            if NAJHORSIE in x or NEZNAME in x or x.startswith('/nezname') or (x.startswith('/') and meni_cwd is None):
                nezname = True
            elif x.startswith('/'):
                von.append(os.path.normpath(x))
            elif cwd and not meni_cwd:
                von.append(os.path.normpath(os.path.join(cwd, x)))
            else:
                nezname = True
        if len(hodnoty) > 32:
            nezname = True
        return list(dict.fromkeys(von))[:16], nezname

    def kod_http_zapis(self, kr, kd, cely=False):
        """Volanie HTTP v kóde, ktoré zapisuje → (zápis do systému, ktorý kotva pozná?, doslovné adresy zápisu,
        zápisové volania s adresou, ktorú rozbor neprečítal). Známy cieľ (CURL_CIEL; KOD_HTTP_* vyššie) zastaví kanál
        K_CURL ako doteraz, ostatné adresy posúdi prepínač CUDZIA (cudzia_kod) — ten istý kanál ako pri klientovi
        v príkaze. Tretia hodnota sa zbiera len pri zapnutom CUDZIA_KOD (K88Z) — pri vypnutom je prázdna a nič sa nemení.
        cely = kód napísaný v príkaze (nikto iný jeho funkcie nevolá) — KOTF, py_pomocnik_zapisy."""
        cudzia = self.cudzia_rezim != 'vypnute'
        h_vst, h_kniz, h_ciel, h_schema = kod_http_predbezne(kd)
        vst = kr.jazyk in ('js', 'py') and h_vst     # K88L: vstavaný modul jazyka
        necit_zap = cudzia and cudzia_kod_rezim() != 'vypnute' and (h_kniz or vst)
        if not h_ciel and not (cudzia and (h_schema or vst)) and not necit_zap:
            return False, [], []
        t = kr.t
        n = len(t)
        klienti, klienti_iny, zapis_exo, php_prud_met, curl, curl_met, param, metody_volani, zapisy = _http_zapis_kd(kd)
        php_curl = kr.jazyk == 'php' and curl
        php_curl_met = curl_met if php_curl else None
        metody_volani, zapisy = set(metody_volani), list(zapisy)       # menia sa nižšie (metódy načítajúceho modulu)
        zname = False
        cudzie = []
        necit = []          # (volanie, dôvod) — zápis na adresu, ktorú rozbor neprečítal (len pri necit_zap)
        php_curl_vol = php_curl_adr = 0

        def necitane(volanie, adresy):
            """Zápisové volanie, ktorého adresu rozbor neprečítal: žiadna adresa, neznáma hodnota, parameter funkcie
            alebo text bez hosta (cesta ku klientovi so základnou adresou). Rozhoduje HOST: adresa s prečítaným hostom
            a neznámou časťou cesty (žiadosť o token `login.microsoftonline.com/<nájomca>/oauth2/v2.0/token`,
            `http://127.0.0.1:9222/json/new?<…>`) sa posúdi podľa hosta ako každá iná — meranie 11. 10.: bez toho
            by pravidlo zastavilo každé prihlásenie poštového nástroja. Adresa známeho systému sem nepatrí."""
            if not necit_zap or len(necit) >= 8:
                return
            if not adresy:
                necit.append((volanie, 'bez adresy'))
                return
            for a in adresy:
                if a and CURL_CIEL.search(a):
                    continue
                if not a or adresa_druh(a.replace(NAJHORSIE, NEZNAME), False, True, jadro=False) in (None, 'necitatelna'):
                    necit.append((volanie, (a or '').replace(NAJHORSIE, '$?')[:60] or 'neznáma hodnota'))
                    return

        def posud(met, adresy):
            """Adresy jedného zápisového volania: známy systém zastaví hneď (ako doteraz), ostatné idú prepínaču CUDZIA."""
            von = False
            for a in adresy:
                if CURL_CIEL.search(a):
                    if met == 'POST' and (KOD_HTTP_CITA_POST.search(a) or KOD_HTTP_KONCEPT.search(a)
                                          or (re.search(r'/graphql\b', a) and 'mutation' not in kd)
                                          or ('InvokeCommand' in a and not zapis_exo)):
                        continue
                    von = True
                elif cudzia:
                    cudzie.append(a)
            return von
        # parametre funkcií, metódy volaní a zápisové volania s doslovnou cestou: _http_zapis_kd (vyššie)
        # KMP (1a, 16. 10.): modul načítaný z kódu — aj metódy, s ktorými ho načítajúci volá (py_volania_modulu);
        # KMP 1d: počíta sa len pri module, v ktorom kod_http_predbezne niečo našlo — inak sa sem rozbor nedostane
        # '?' (neprečítaná metóda) je aj medzi zápismi, aby ju „len čítanie alebo koncept“ nepustilo
        for mt, c in self.mod_volania:
            if mt.startswith('~'):
                continue                              # KHD: značky volaní s telom požiadavky (py_volania_dat), nie metóda
            metody_volani.add(mt)
            if mt != 'GET':
                zapisy.append((mt, c))
        len_cita = bool(metody_volani) and all(mt == 'POST' and c is not None and (KOD_HTTP_CITA_POST.search(c)
                                                                                   or KOD_HTTP_KONCEPT.search(c))
                                               for mt, c in zapisy)
        # K88L (11. 10.): odoslanie vstavaným modulom jazyka. Node: `https.request(voľby[, fn])` a `https.request(adresa
        # [, voľby][, fn])` (aj http, aj `.get`), modul cez require alebo import pod ľubovoľným menom, `request` z rozkladu
        # a `require("https").request(…)`. Metóda je vo voľbe `method` (bez nej čítanie, ako pri fetch); adresa je prvý
        # argument, alebo sa zloží z volieb hostname/host, port a path. Voľby smú byť v premennej priradenej raz doslova.
        # Python: spojenie `HTTPSConnection(host[, port])` v premennej (alebo priamo) a jeho `.request(METÓDA, cesta)`.
        # Predtým: https.request rozbor nepoznal vôbec, http.request čítal ako klienta s tvarom (metóda, adresa) — zápis
        # na cudziu adresu napísanú doslova prešiel bez otázky. Čo sa prečítať nedá (host z nastavení, voľby v parametri
        # funkcie), je neprečítaná adresa — posúdi ju pravidlo o kóde (CUDZIA_KOD), ako pri fetch s adresou v parametri.
        node_mod = {}       # meno modulu → schéma
        node_hole = {}      # funkcia request z rozkladu modulu → schéma
        py_spoj = {}        # premenná so spojením → tokeny volaní HTTPSConnection(…)
        otv = {}
        if vst:
            otv = {b: a for a, b in kr.par.items()}
        if vst and kr.jazyk == 'js':
            for m in KOD_HTTP_NODE_MODUL.finditer(kd):
                node_mod[m.group(1) or m.group(3)] = m.group(2) or m.group(4)
            for x in ('http', 'https'):
                if x not in node_mod and not re.search(r'(?<![\w$.])%s\s*=(?![=>])' % x, kd):
                    node_mod[x] = x              # modul bez viditeľného require (iný súbor, REPL)
            for m in KOD_HTTP_NODE_ROZKLAD.finditer(kd):
                for x in (m.group(1) or m.group(3) or '').split(','):
                    sl = re.findall(r'[\w$]+', x)
                    if sl and sl[0] == 'request' and sl[-1] != 'as':
                        node_hole[sl[-1]] = m.group(2) or m.group(4)
        if vst and kr.jazyk == 'py':
            for k in range(2, n - 1):
                if t[k][0] == 'n' and t[k + 1] == ('p', '(') and t[k][1].split('.')[-1] in KOD_HTTP_PY_SPOJENIE \
                        and t[k - 1] == ('p', '=') and t[k - 2][0] == 'n':
                    py_spoj.setdefault(t[k - 2][1], []).append(k)
        npm_kl = set()      # K88M: mená klienta z balíka request alebo axios (priame volanie aj request.post)
        if kr.jazyk == 'js':
            npm_kl = {'axios'} | {m.group(1) or m.group(3) for m in KOD_HTTP_NPM_KLIENT.finditer(kd)}

        def vst_metoda(a, b):
            """Metóda z výrazu tokens[a:b]: doslovná (aj cez premennú) → zápis alebo 'GET'; parameter funkcie, ktorú kód
            volá len na čítanie → 'GET'; inak '?' (neznáma = zápis)."""
            if b - a == 1 and t[a][0] == 'n' and t[a][1] in param:
                return 'GET' if metody_volani and (metody_volani <= {'GET'} or len_cita) else '?'
            hod = {kr.text(h).upper() for h in kr.vyraz(a, b, 1)}
            zap = [x for x in CUDZIA_ZAPIS if x in hod]
            if zap:
                return zap[0]
            return 'GET' if hod and hod <= set(KOD_HTTP_CITA_METODY) else '?'

        def vst_texty(r, inak):
            return [kr.text(h) for h in kr.vyraz(r[0], r[1], 1)][:kr.MAX_ALT] if r else list(inak)

        def vst_zloz(schema, hosty, porty, cesty):
            port = porty[0] if porty and porty[0].isdigit() else ''
            return [schema + '://' + h + (':' + port if port else '') + (c if c.startswith('/') else '/' + c)
                    for h in hosty for c in cesty][:kr.MAX_ALT]

        def vst_objekt(a, b, hl=0):
            """Rozsah tokenov objektu {…}: napísaný priamo, alebo premenná priradená raz doslova (nie parameter)."""
            if b - a >= 2 and t[a] == ('p', '{') and kr.par.get(a) == b - 1:
                return a, b
            if b - a == 1 and t[a][0] == 'n' and t[a][1] not in param and hl < 3:
                pr = kr.prir.get(t[a][1])
                if pr and len(pr) == 1 and pr[0][2] == '=':
                    return vst_objekt(pr[0][0], pr[0][1], hl + 1)
            return None

        def vst_volby(a, b):
            """{kľúč: rozsah hodnoty} na vrchnej úrovni objektu (čiarka vo vnorenom objekte nedelí); rozptyl a
            vypočítaný kľúč idú pod '…'."""
            von = {}
            for x, y in kr.delenie(a + 1, b - 1):
                if y - x >= 3 and t[x][0] in ('n', 's') and t[x + 1] == ('p', ':'):
                    von[t[x][1] if t[x][0] == 'n' else kr.retazec(t[x][1])] = (x + 2, y)
                elif y - x == 1 and t[x][0] == 'n':
                    von[t[x][1]] = (x, y)        # skratka {hostname, path}
                else:
                    von['…'] = (x, y)
            return von

        def vstavane(k, posl, obj):
            """Volanie na tokene k je odoslanie vstavaným modulom → (metóda, adresy, dôvod neprečítania | None);
            inak None (posúdia ho ostatné vetvy)."""
            kon = kr.par.get(k + 1, n)
            rozs = kr.delenie(k + 2, kon)
            za = otv.get(k - 2) if obj == '?' and k >= 3 and t[k - 2] == ('p', ')') else None   # ….).request(
            if kr.jazyk == 'py':
                if posl not in ('request', 'putrequest'):
                    return None
                if obj in py_spoj:
                    spojenia = py_spoj[obj]
                elif za and t[za - 1][0] == 'n' and t[za - 1][1].split('.')[-1] in KOD_HTTP_PY_SPOJENIE:
                    spojenia = [za - 1]
                else:
                    return None
                poz, kl = [], {}
                for a, b in rozs:
                    if b - a >= 3 and t[a][0] == 'n' and t[a + 1] == ('p', '='):
                        kl[t[a][1]] = (a + 2, b)
                    else:
                        poz.append((a, b))
                mr = kl.get('method') or (poz[0] if poz else None)
                cesty = vst_texty(kl.get('url') or (poz[1] if len(poz) > 1 else None), ['/'])
                adresy = []
                for kk in spojenia[-4:]:
                    sp, skl = [], {}
                    for a, b in kr.delenie(kk + 2, kr.par.get(kk + 1, n)):
                        if b - a >= 3 and t[a][0] == 'n' and t[a + 1] == ('p', '='):
                            skl[t[a][1]] = (a + 2, b)
                        else:
                            sp.append((a, b))
                    hr = skl.get('host') or (sp[0] if sp else None)
                    if hr is None:
                        continue
                    adresy += vst_zloz('https' if t[kk][1].split('.')[-1] == 'HTTPSConnection' else 'http',
                                       vst_texty(hr, []), vst_texty(skl.get('port') or (sp[1] if len(sp) > 1 else None), []),
                                       cesty)
                return (vst_metoda(*mr) if mr else '?'), adresy[:kr.MAX_ALT], None
            schema = None
            if posl in ('request', 'get') and obj in node_mod and obj not in param:
                schema = node_mod[obj]
            elif posl in node_hole and not obj and not (k and t[k - 1] in (('n', 'function'), ('p', '.'))):
                schema = node_hole[posl]
            elif posl == 'request' and za and t[za - 1] == ('n', 'require') and za + 2 == k - 2 and t[za + 1][0] == 's':
                m = re.fullmatch(r'(?:node:)?(https?)', kr.retazec(t[za + 1][1]))
                schema = m.group(1) if m else None
            if schema is None:
                return None
            if not rozs:
                return 'GET', [], None
            a, b = rozs[0]
            if b - a == 1 and t[a][0] == 's' and kr.retazec(t[a][1]).upper() in HTTPIE_METODY:
                return None                      # tvar (metóda, adresa) — iný klient pomenovaný http: vetva „klient“
            prvy = vst_objekt(a, b)
            druhy = vst_objekt(*rozs[1]) if prvy is None and len(rozs) > 1 else None
            vol = vst_volby(*(prvy or druhy)) if (prvy or druhy) else {}
            met = vst_metoda(*vol['method']) if 'method' in vol else 'GET'
            adr_vol = None
            hr = vol.get('hostname') or vol.get('host')
            if hr:
                pr = vst_texty(vol.get('protocol'), [])
                if pr and re.fullmatch(r'https?:?', pr[0]):
                    schema = pr[0].rstrip(':')
                adr_vol = vst_zloz(schema, vst_texty(hr, []), vst_texty(vol.get('port'), []), vst_texty(vol.get('path'), ['/']))
            if prvy is not None:
                return met, adr_vol or [], None  # voľby bez hosta = volanie bez adresy
            hod = vst_texty((a, b), [])
            nejasny = t[a][0] != 's' and all(x in ('', NAJHORSIE) for x in hod)   # adresa alebo voľby — nevedno
            if nejasny and adr_vol is None:
                if druhy is None:                # metódu nevidno: zápis len keď kód zápisovú metódu vôbec spomína
                    met = '?' if KOD_HTTP_ZAPIS_DOSLOVA.search(kd) else 'GET'
                return met, [], ('parameter funkcie' if b - a == 1 and t[a][0] == 'n' and t[a][1] in param
                                 else 'voľby alebo adresa v premennej')
            return met, ([] if nejasny else hod) + (adr_vol or []), None

        for k in range(n - 1):
            if zname and not cudzia:
                break                             # známy cieľ stačí; bez prepínača CUDZIA niet čo ďalej posudzovať
            d, v = t[k]
            if d != 'n' or t[k + 1] != ('p', '('):
                continue
            casti = v.split('.')
            posl, obj = casti[-1], '.'.join(casti[:-1])
            if k and t[k - 1] == ('p', '.'):
                obj = obj or '?'
            if vst:
                try:
                    vs = vstavane(k, posl, obj)
                except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
                    vs = None
                if vs is not None:
                    met, adresy, dovod = vs
                    if met in CUDZIA_ZAPIS or met == '?':
                        if dovod:
                            if necit_zap and len(necit) < 8:
                                necit.append((v, dovod))
                        else:
                            if posud(met, adresy):
                                zname = True
                            necitane(v, adresy)
                    continue
            met = None
            cesta = kod_cesta_volania(t, k) if posl in KOD_HTTP_VOLANIE_INY else ''
            if posl == 'Request' or posl == 'urlopen':
                druh = 'urllib'
            elif posl == 'fetch' and obj in ('', 'window', 'globalThis'):
                druh = 'fetch'
            elif not obj and posl in npm_kl and not (k and t[k - 1] == ('n', 'function')):
                druh = 'priamo'                   # K88M: request({url, method}), axios(adresa, {method}) …
            elif (posl in ('get', 'post', 'put', 'patch', 'delete', 'request', 'head') and (
                    obj.split('.')[-1] in KOD_HTTP_KLIENT or obj in klienti or obj.endswith(('session', 'client', 'Session'))
                    or obj in npm_kl)) or (posl == 'del' and obj in npm_kl):
                druh = 'klient'
                met = None if posl == 'request' else 'DELETE' if posl == 'del' else posl.upper()
            elif cesta and (KOD_HTTP_KLIENT_INY.match(cesta)
                            or cesta.split('->')[0].split('::')[0].split('.')[0] in klienti_iny):
                druh = 'iny'                      # klient Perlu alebo Ruby (LWP::UserAgent, Net::HTTP, Faraday …)
                met = posl.upper() if posl in KOD_HTTP_METODA_MENO else ('POST' if posl == 'post_form' else None)
            elif kr.jazyk == 'php' and posl in KOD_PHP_OTVORI and php_prud_met:
                druh = 'php-prud'                 # PHP: adresa otvorená ako súbor s kontextom prúdu
                met = php_prud_met
            elif kr.jazyk == 'php' and php_curl and posl in ('curl_init', 'curl_setopt'):
                druh = 'php-curl'                 # PHP: curl ako knižnica (curl_init/curl_setopt + curl_exec)
                met = php_curl_met
            else:
                continue
            kon = kr.par.get(k + 1, n)
            if druh in ('iny', 'php-prud', 'php-curl'):
                # jazyky, kde vyhodnotenie výrazu dá „neznáme“ (`URI(u)`, `$ctx`): metóda aj adresa sa čítajú z tela
                # volania doslova. Čítanie (`$ua->get`, `Net::HTTP.get`, `file_get_contents($u)` bez kontextu,
                # curl_init bez zápisovej voľby) ide ďalej bez otázky.
                if met is None:
                    met = kod_metoda_v_tele(kr, k + 2, kon)
                if druh == 'php-prud' and len(kr.delenie(k + 2, kon)) < 3:
                    continue                      # bez kontextu prúdu je otvorenie adresy čítanie
                if (met or 'GET') in CUDZIA_ZAPIS:
                    adr_t = kod_adresy_v_tele(kr, k + 2, kon)
                    if posud(met, adr_t):
                        zname = True
                    if druh == 'php-curl':        # adresa je v curl_init alebo v jednom z curl_setopt — posúdi sa spolu
                        php_curl_vol += 1
                        php_curl_adr += len(adr_t)
                    else:
                        necitane(cesta or posl, adr_t)
                continue
            try:
                poz, kl = kr.argumenty(k + 2, kon, 0)
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
                continue
            # metóda: kľúčový argument method=, objekt { method: … } (node), prvý argument .request('POST', …)
            for m in range(k + 2, min(kon, n - 2)):
                if t[m] == ('n', 'method') and t[m + 1][0] == 'p' and t[m + 1][1] in (':', '='):
                    if t[m + 2][0] == 's':
                        met = kr.retazec(t[m + 2][1]).upper()
                    elif t[m + 2][0] == 'n' and t[m + 2][1] in param and metody_volani and (metody_volani <= {'GET'}
                                                                                            or len_cita):
                        met = 'GET'
                    else:
                        met = '?'
                    break
            casti_arg = kr.delenie(k + 2, kon)
            # K88M (14. 10.): voľby objektom v JS — {url|uri, method} napísané vo volaní alebo v premennej priradenej raz
            # doslova (to isté čítanie ako pri https.request): `axios({url, method})`, `request.post({url})`,
            # `api.request({url, method})`, `fetch(adresa, voľby)`. Predtým sa adresa vo voľbe url nečítala (zápis na
            # cudziu adresu napísanú doslova prešiel) a voľby fetch v premennej sa brali ako čítanie.
            vol, vol_i = {}, None
            if kr.jazyk == 'js' and druh in ('klient', 'priamo', 'fetch'):
                for i_, (a_, b_) in enumerate(casti_arg[:3]):
                    o_ = vst_objekt(a_, b_)
                    if not o_:
                        continue
                    vol, vol_i = vst_volby(*o_), i_
                    if t[a_][0] == 'n':           # voľby v premennej: argumenty() ich vzal ako pozičný argument
                        j_ = sum(1 for x_, y_ in casti_arg[:i_] if not (t[x_] == ('p', '{') or (
                            y_ - x_ >= 2 and t[x_][0] == 'n' and t[x_ + 1] == ('p', '='))))
                        poz = poz[:j_] + poz[j_ + 1:]
                        casti_arg = casti_arg[:i_] + casti_arg[i_ + 1:]
                    break
                if met is None and 'method' in vol:
                    met = vst_metoda(*vol['method'])
            if druh == 'klient' and posl == 'request' and poz:
                h = kr.text(poz[0][0])
                met = met or ('?' if NAJHORSIE in h or not h else h.upper())
                poz = poz[1:]
                casti_arg = casti_arg[1:]
            adr_param = bool(casti_arg and casti_arg[0][1] - casti_arg[0][0] == 1 and t[casti_arg[0][0]][0] == 'n'
                             and t[casti_arg[0][0]][1] in param)
            if adr_param and not necit_zap:
                continue                          # adresa je parameter funkcie — kotva ju nevie prečítať
            if druh == 'urllib' and met is None:
                ma_data = ('data' in kl and any(kr.text(h) for h in kl['data'])) or len(poz) > 1
                if ma_data and kr.jazyk == 'py' and py_telo_bez_dat(kr, kd, casti_arg, param, self.mod_volania):
                    ma_data = False                   # KHD: telo z parametra s predvolbou None, ktorý žiadne volanie nedá
                met = 'POST' if ma_data else 'GET'
            met = met or 'GET'
            if met not in ('POST', 'PUT', 'PATCH', 'DELETE', '?'):
                continue
            if adr_param:
                necit.append((v, 'parameter funkcie'))
                continue
            adresy = [kr.text(h) for h in (kl.get('url') or (poz[0] if poz else []))]
            if vol and (vol_i == 0 or not adresy):
                # K88M: adresa vo voľbe url (axios, request) alebo uri (request); keď sú voľby prvým argumentom, ďalší
                # pozičný argument je funkcia na odpoveď, nie adresa
                adresy = vst_texty(vol.get('url') or vol.get('uri'), []) or adresy
            if posud(met, adresy):
                zname = True
            necitane(v, adresy)
        if php_curl_vol and not php_curl_adr:
            necitane('curl_exec', [])
        # KOTF (23. 10.): zápis urllib s adresou, ktorú rozbor po tokenoch neprečítal, v kóde napísanom v príkaze — keď
        # sa dá posúdiť pri každom volaní pomocnej funkcie (py_pomocnik_zapisy), nahradí ho posudok každej požiadavky
        # tou istou cestou ako doslovná adresa. Vyhľadávanie v Jire je čítanie aj na hoste, ktorý nevidno (adresa
        # z nastavení) — ako pomocník volaný so zápisovou metódou len na cestu, ktorá číta (len_cita).
        urllib_vol = ('Request', 'urlopen')
        if cely and kr.jazyk == 'py' and any(x[0].split('.')[-1] in urllib_vol for x in necit):
            poziadavky = py_pomocnik_zapisy(kd)
            if poziadavky is not None:
                necit[:] = [x for x in necit if x[0].split('.')[-1] not in urllib_vol]
                for met, a in poziadavky:
                    hc = adr_host_cast(a)
                    if met == 'POST' and NEZNAME in hc and JIRA_HLADANIE.match(a[len(hc):]):
                        continue
                    if posud(met, [a]):
                        zname = True
                    necitane('urllib.request.Request', [a])
        return zname, cudzie, necit

    def cudzia_kod(self, adresy, necitane=(), skript=None):
        """Zápis cez HTTP z kódu interpretra na adresu, ktorá nie je vlastný server ani na zozname povolených — ten istý
        kanál (K_CURL) a ten istý prepínač (CUDZIA, PS 4.88 a) ako pri klientovi v príkaze; pri polohe „vypnute“ sa nič
        nemení. Posudzujú sa len doslovné adresy: adresu, ktorú kotva v kóde nevie prečítať (`requests.post(url, …)`
        s url z nastavení), tu za nečitateľný cieľ neberie — v kóde je takých volaní väčšina a koľko otázok by pribudlo,
        nie je zmerané (pri klientovi v príkaze zmerané je, preto tam nečitateľná adresa zastaví). Cestová podmienka
        zoznamu povolených (weby firmy, PS 4.90 f) tu neplatí: pravidlo o zverejnení a mazaní (externe_zmazanie)
        beží len pri klientovi v príkaze, preto je web firmy z kódu ďalej cudzí.
        necitane (K88Z, 11. 10.): zápisové volania, ktorých adresu rozbor neprečítal — zbierajú sa a zastavia len pri
        zapnutom CUDZIA_KOD (príznak „adresa-necitatelna-kod“); skript = meno súboru, None = kód napísaný v príkaze.
        Poloha 'prikaz' (PS 4.100 b, 13. 10.) zastaví len kód napísaný v príkaze — uložený skript (skript != None)
        prejde bez otázky. Je to známa hranica voľby, nie opomenutie: ten istý kód uložený do súboru a až potom
        spustený prejde, a je to napísané aj v položke."""
        if self.cudzia_rezim == 'vypnute':
            return
        zname = self.mkt_rezim != 'vypnute'
        for a in adresy:
            if adresa_druh(a, False, zname, jadro=False) != 'cudzia':
                continue
            if len(CUDZIE_NALEZY) < 20:
                CUDZIE_NALEZY.append(('cudzia', a[:120]))
            self.priznaky.add('cudzia-adresa')
            self.kanal(P_CURL, K_CURL)
            return
        rezim_kod = cudzia_kod_rezim()
        if necitane and rezim_kod != 'vypnute' and not (rezim_kod == 'prikaz' and skript):
            if len(CUDZIE_NALEZY) < 20:
                CUDZIE_NALEZY.append(('kod-skript' if skript else 'kod-prikaz',
                                      f'{skript or "kód v príkaze"}: {necitane[0][0]}(…) — {necitane[0][1]}'[:120]))
            self.priznaky.add('adresa-necitatelna-kod')
            self.kanal(P_CURL, K_CURL)

    def kod_cli(self, kr):
        """Nástroj z príkazového riadku v kóde mimo volania subprocess: obal pomenovaný podľa nástroja (az('ad', …)) a
        zoznam argumentov, ktorý sa začína menom nástroja (['az', 'ad', …]) → texty príkazov."""
        t = kr.t
        n = len(t)
        von = []
        for k in range(n - 1):
            if len(von) >= SKRIPT_MAX_VOLANI:
                break
            d, v = t[k]
            try:
                if d == 'n' and t[k + 1] == ('p', '(') and v.split('.')[-1] in SKRIPT_CLI_VOLANIE \
                        and not (k and t[k - 1] in (('n', 'def'), ('n', 'function'))):
                    kz = kr.par.get(k + 1, n)
                    poz = kr.argumenty(k + 2, kz, 0)[0]
                    von += kr.prikazy_volania([[('a', [v.split('.')[-1]])]] + poz, None)
                elif d == 'p' and v == '[' and t[k + 1][0] == 's' \
                        and not (k and (t[k - 1][0] in ('n', 's') or t[k - 1] in (('p', ')'), ('p', ']')))) \
                        and os.path.basename(kr.retazec(t[k + 1][1])) in SKRIPT_CLI_ZOZNAM:
                    for h in kr.zoznam(k + 1, kr.zavreta(k, n), 0):
                        if h[0] == 'a':
                            von.append(' '.join(kotva_quote(kod_priecinok(a)) for a in h[1]))
            except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
                continue
        return von

    def subst_skript(self, cmds, ctx):
        """V rozbore skriptu (úloha K): $(cd "$(dirname "$0")/.." && pwd) a $(git rev-parse --show-toplevel) — priečinok
        skriptu a koreň repozitára, z ktorého skript beží."""
        if not cmds or any(not c.slova or c.heredoc or c.podshell is not None for c in cmds):
            return None
        sl = [[w.text() for w in c.slova] for c in cmds]
        if len(cmds) == 2 and sl[0][0] in ('cd', 'pushd') and sl[1][0] == 'pwd' and len(sl[1]) <= 2:
            poz = [w for w in cmds[0].slova[1:] if not w.text().startswith('-')]
            if len(poz) == 1:
                k = self.rozvin(poz[0], ctx)
                if len(k) == 1 and k[0][1] is None and k[0][0]:
                    return self.absolutna(k[0][0], ctx)
            return None
        if len(cmds) == 1 and sl[0][:1] == ['git']:
            t = sl[0][1:]
            d = ctx.cwd
            if t[:1] == ['-C'] and len(t) > 2:
                k = self.rozvin(cmds[0].slova[2], ctx)
                d = self.absolutna(k[0][0], ctx) if len(k) == 1 and k[0][1] is None else None
                t = t[2:]
            if t == ['rev-parse', '--show-toplevel'] and d:
                return git_koren(d, self.smie)
        return None

    def adr_lit(self, args, ctx, idx):
        """Cieľová adresa poskladaná z premenných priradených doslovne v tom istom príkaze (U=https://api.github.com…;
        curl -X POST "$U") — rozvinie ju na známy text, aby sa cudzí cieľ v premennej neschoval (ako pri rm). Neznáma
        premenná (zo source .env, ${X:-…}) ostáva neznáma a nič nezastaví."""
        if args is None or ctx is None or idx is None or idx >= len(args):
            return []
        return [c if u is None else c[:u] for c, u in self.rozvin(args[idx], ctx)]

    def metoda_lit(self, m_met, met, args, ctx):
        """Metóda v premennej priradenej doslovne v tom istom príkaze (`M=GET; curl -X $M …`, K88Z 11. 10. — čítanie
        sa bralo ako zápis a stráž sa zbytočne pýtala). Známa hodnota so zápisom vráti zápis (DELETE má prednosť).
        Čítanie (GET, HEAD, OPTIONS) vráti len vtedy, keď je hodnota ISTÁ — sledovanie premenných v rozbore nevidí
        zmenu cez source, eval, trap, neskoršie priradenie v cykle či vo funkcii ani predponu `M=GET curl …`
        (tá platí až pre spustený program), preto musí naraz platiť: slovo je len `$M` alebo `${M}`; príkaz nie je
        vo vnorenom shelli, na vzdialenom stroji ani v kontajneri; v texte príkazu je meno premennej okrem použití
        `$M` presne raz, a to ako samostatné priradenie písmen na začiatku príkazu (pred ním smú byť len iné doslovné
        priradenia); v príkaze nie je eval, source, bodka, trap ani alias. Inak None — metóda ostáva zápisom."""
        if not m_met or args is None or ctx is None or m_met[0] >= len(args):
            return None
        try:
            hod = [(c[m_met[1]:], u) for c, u in self.rozvin(args[m_met[0]], ctx)]
        except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
            return None
        if not hod or any(u is not None or NEZNAME in h or not re.fullmatch(r'[A-Za-z]+', h) for h, u in hod):
            return None
        hod = [h.upper() for h, _ in hod]
        zap = [h for h in hod if h in CUDZIA_ZAPIS]
        if zap:
            return 'DELETE' if 'DELETE' in zap else zap[0]
        if len(set(hod)) != 1 or hod[0] not in KOD_HTTP_CITA_METODY:
            return None
        m = METODA_PREM.fullmatch((met or '').strip('"'))
        if not m or ctx.hlbka or ctx.vzdial or ctx.kontajner or ctx.sudo:
            return None
        v = re.escape(m.group(1) or m.group(2))
        txt = (self.skript_stav.get('texty') or [''])[0] or ''
        if METODA_SKRYTE.search(txt):
            return None
        vsetky = len(re.findall(r'(?<![A-Za-z0-9_])%s(?![A-Za-z0-9_])' % v, txt))
        pouzitia = len(re.findall(r'\$%s(?![A-Za-z0-9_])|\$\{%s\}' % (v, v), txt))
        pr = re.search(r'(?<![A-Za-z0-9_${])%s=(["\']?)([A-Za-z]+)\1[ \t]*(?:;|\n|&&)' % v, txt)
        if vsetky - pouzitia != 1 or not pr or pr.group(2).upper() != hod[0] or not METODA_PRED.fullmatch(txt[:pr.start()]):
            return None
        return hod[0]

    def curl_soket_prem(self, w, ctx):
        """Voľba miestneho soketu v premennej bez úvodzoviek (U="--unix-socket /run/…"; curl $U …; K88, 18. 10.): každá
        známa hodnota je presne voľba soketu s cestou — nič iné (proxy, ďalšia voľba) v nej nie je."""
        if len(w.seg) != 1 or w.seg[0][0] != 'v' or (w.v_uvod and 0 in w.v_uvod):
            return False
        try:
            hod = self.rozvin(w, ctx)
        except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
            return False
        for c, u in hod or [('', 0)]:
            sl = c.split()
            if u is not None or not ((len(sl) == 2 and sl[0] in CURL_SOKET) or
                                     (len(sl) == 1 and sl[0].split('=', 1)[0] in CURL_SOKET and '=' in sl[0])):
                return False
        return True

    def curl(self, t, args=None, ctx=None, extra=(), cmd=None, xg=None):
        """Zápis (-X POST/PUT/PATCH/DELETE) na adresu cudzieho systému; adresa je pozičný argument alebo --url,
        nie obsah -d/-H (žiadosť o token s scope=https://graph… nie je zápis — prehrávka kolegov 24. 9.).
        Marketingová služba (prepínač MARKETING): adresa aj telo (-d, --data…, --json, -F; -d @súbor v /tmp)."""
        met = None
        adresy = []
        data = get = upload = False    # bez -X: -d/--data/--json/-F je POST, -T je PUT, -G/--get vráti GET
        m_adr = []      # (index slova, dĺžka predpony voľby) — adresy pre marketingové služby
        m_telo = []     # (index slova, dĺžka predpony, môže byť @súbor) — telo pre marketingové služby
        m_met = None    # (index slova, dĺžka predpony) — metóda pre marketingové služby (-X "$M")
        m_kfg = []      # (index slova, dĺžka predpony, druh) — súbor s nastaveniami (-K, --config): adresa aj metóda v ňom
        soket = proxy = dalsi = False   # miestny soket s hodnotou · proxy · --next alebo -: (K88, 18. 10.)
        j = 0
        while j < len(t):
            x = t[j]
            if '$' in x and not soket and args is not None and ctx is not None and j < len(args):
                soket = self.curl_soket_prem(args[j], ctx)
            if x in ('-X', '--request') and j + 1 < len(t):
                met = t[j + 1]
                m_met = (j + 1, 0)
                j += 2
                continue
            if x.startswith('-X') and len(x) > 2:
                met = x[2:]
                m_met = (j, 2)
            elif x.startswith('--request='):
                met = x.split('=', 1)[1]
                m_met = (j, 10)
            elif x == '--url' and j + 1 < len(t):
                adresy.append(t[j + 1])
                adresy += self.adr_lit(args, ctx, j + 1)
                m_adr.append((j + 1, 0))
                j += 2
                continue
            elif x.startswith('--url='):
                adresy.append(x.split('=', 1)[1])
                adresy += self.adr_lit(args, ctx, j)
                m_adr.append((j, 6))
            elif x.startswith('--'):
                n = x.split('=', 1)[0]
                data |= n.startswith('--data') or n in ('--json', '--form', '--form-string')
                if n in ('--data', '--data-raw', '--data-binary', '--data-ascii', '--data-urlencode', '--json', '--form',
                         '--form-string'):
                    subor = n in ('--data', '--data-binary', '--data-ascii', '--json')
                    m_telo.append((j, len(n) + 1, subor) if '=' in x else (j + 1, 0, subor))
                get |= n == '--get'
                upload |= n == '--upload-file'
                soket |= n in CURL_SOKET and ('=' in x or j + 1 < len(t))
                proxy |= n in CURL_PROXY
                dalsi |= n == '--next'
                if n == '--config':      # nastavenia zo súboru: adresa, metóda aj telo môžu byť v ňom (10. 10.)
                    m_kfg.append((j, len(n) + 1, 'konfig') if '=' in x else (j + 1, 0, 'konfig'))
                j += 2 if ('=' not in x and x in CURL_L_ARG) else 1
                continue
            elif x.startswith('-') and len(x) > 1:
                for q, ch in enumerate(x[1:]):
                    data |= ch in 'dF'
                    get |= ch == 'G'
                    upload |= ch == 'T'
                    proxy |= ch == 'x'
                    dalsi |= ch == ':'
                    if ch == 'X':     # -sX POST, -sXPOST
                        met = x[q + 2:] or (t[j + 1] if j + 1 < len(t) else None)
                        m_met = (j, q + 2) if x[q + 2:] else (j + 1, 0)
                    if ch in 'dF':    # -d telo, -sd telo, -d@súbor
                        m_telo.append((j, q + 2, ch == 'd') if q + 2 < len(x) else (j + 1, 0, ch == 'd'))
                    if ch == 'K':     # -K súbor s nastaveniami (ako --config)
                        m_kfg.append((j, q + 2, 'konfig') if q + 2 < len(x) else (j + 1, 0, 'konfig'))
                    if ch in CURL_S_ARG:
                        break
                j += skok(x, CURL_S_ARG, ())
                continue
            else:
                adresy.append(x)
                adresy += self.adr_lit(args, ctx, j)
                m_adr.append((j, 0))
            j += 1
        if not met and not get:
            met = 'PUT' if upload else ('POST' if data else None)
        met_mkt = met
        if met and '$' in met:      # metóda v premennej (-X "$M"): neznámu ber k cudziemu cieľu ako zápis (ako az rest);
            met = self.metoda_lit(m_met, met, args, ctx) or 'POST'   # istú hodnotu z toho istého príkazu dosaď (K88Z)
        if met and met.upper() in ('POST', 'PUT', 'PATCH', 'DELETE') and any(CURL_CIEL.search(a) for a in adresy + list(extra)):
            self.kanal(P_CURL, K_CURL)
        self.externe_zmazanie(met, adresy + list(extra), t, args, ctx)
        kfg = self.klient_konfig(m_kfg, t, args, ctx) if m_kfg and not get and self.cudzia_rezim != 'vypnute' else None
        if not (soket and not proxy and not dalsi and not m_kfg):   # cez miestny soket nejde nič na sieť (K88, 18. 10.)
            self.cudzia_adresa(met, m_adr, t, args, ctx, extra, kfg=kfg, xg=xg)
        if self.mkt_rezim != 'vypnute' and args is not None and ctx is not None:
            adr = [c[p:] for i, p in m_adr if i < len(args) for c in self.mkt_slovo(args[i], ctx)[0]] + list(extra)
            telo, nezname = self.mkt_telo(m_telo, args, ctx, cmd)
            if met_mkt and '$' in met_mkt:
                # metóda v premennej: priradená v tom istom príkaze (M=DELETE; curl -X $M …) sa rozvinie; neznáma
                # (call() { curl -X "$1" …; }, "$METODA") môže byť aj DELETE — pri marketingu najprísnejšie (overenie 25. 9.)
                hod = [c[m_met[1]:] for c in self.mkt_slovo(args[m_met[0]], ctx)[0]] \
                    if m_met and m_met[0] < len(args) else []
                hod = [h.upper() for h in hod]
                if not hod or any(NEZNAME in h or not h.strip() for h in hod):
                    met_mkt = 'DELETE'
                else:
                    zap = [h for h in hod if h in ('POST', 'PUT', 'PATCH', 'DELETE')]
                    met_mkt = 'DELETE' if 'DELETE' in zap else (zap[0] if zap else hod[0])
            self.mkt_http(met_mkt, adr, telo, nezname)

    def subor_obsah(self, cesta, ctx, limit=256 * 1024):
        """Text súboru zapísaného v tomto príkaze (heredoc) alebo z disku; None = kotva ho nevie prečítať
        (cesta je v premennej, súbor chýba alebo je priveľký)."""
        if not cesta or cesta == '-' or ctx is None or NEZNAME in cesta or '$' in cesta or '`' in cesta:
            return None
        p = self.absolutna(cesta, ctx)
        if p and p in self.subory:
            return self.subory[p]
        try:
            if not p or not os.path.isfile(p) or os.path.getsize(p) > limit:
                return None
            with open(p, encoding='utf-8', errors='replace') as f:
                return f.read()
        except OSError:
            return None

    def klient_konfig(self, m_kfg, t, args=None, ctx=None):
        """Nastavenia klienta zo súboru (curl -K/--config, wget --config) a zoznam adries zo súboru (wget -i):
        → (adresy, metóda, adresa nečitateľná, metóda nečitateľná). Súbor, ktorý kotva nevie prečítať, je nečitateľný
        cieľ — nevidno, kam a čo príkaz pošle (nezávislý beh 10. 10.: `curl -K cfg` prešlo bez otázky)."""
        adresy, met, nejasne, met_nejasna = [], None, False, False
        for i, p, druh in m_kfg[:4]:
            cesty = []
            chyba = False
            if args is not None and ctx is not None and i < len(args):
                try:
                    for c, u in self.rozvin(args[i], ctx):
                        if u is None and NEZNAME not in c:
                            cesty.append(c[p:])
                        else:
                            chyba = True
                except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
                    chyba = True
            elif i < len(t):
                cesty.append(t[i][p:])
            for cesta in cesty[:4]:
                obs = self.subor_obsah(cesta, ctx)
                if obs is None:
                    chyba = True
                    continue
                if druh == 'zoznam':
                    adresy += kfg_adresy(obs)
                    continue
                a, m, vstupy = kfg_rozbor(obs)
                adresy += a
                met = met or m
                for vs in vstupy[:4]:
                    o2 = self.subor_obsah(vs, ctx)
                    if o2 is None:
                        chyba = True
                    else:
                        adresy += kfg_adresy(o2)
            if chyba or not cesty:
                nejasne = True
                met_nejasna |= druh == 'konfig'     # zoznam adries metódu nemení (wget -i bez zápisu je sťahovanie)
        return adresy, met, nejasne, met_nejasna

    def cudzia_adresa(self, met, idx, t, args=None, ctx=None, extra=(), httpie=False, kfg=None, xg=None):
        """Zápis (POST/PUT/PATCH/DELETE alebo telo) na adresu, ktorá nie je vlastný server ani na zozname povolených
        (prepínač CUDZIA, PS 4.88 a). idx: (index slova, dĺžka predpony voľby) adries; extra: adresy privedené cez xargs;
        kfg: výsledok klient_konfig (adresa a metóda zo súboru s nastaveniami); xg: (vstup xargs sa nedá prečítať,
        náhradný znak -I alebo None) — diery nezávislého behu 10. 10."""
        if self.cudzia_rezim == 'vypnute':
            return
        k_adr, k_met, k_nejasne, k_met_nejasna = kfg if kfg else ((), None, False, False)
        m = (met or k_met or '').upper()
        if m and m not in CUDZIA_ZAPIS:
            return                                # výslovné čítanie (-X GET) — ani súbor s nastaveniami ho nezmení
        if m not in CUDZIA_ZAPIS and not k_met_nejasna:
            return
        texty = [str(e) for e in extra] + [str(a) for a in k_adr]
        # známu službu posúdi jej pravidlo len vtedy, keď naozaj beží (mkt_http: zapnutý MARKETING a rozvinuté slová)
        zname = self.mkt_rezim != 'vypnute' and args is not None and ctx is not None
        for i, p in idx:
            if args is not None and ctx is not None and i < len(args):
                try:
                    texty += [c[p:] for c, _ in self.rozvin(args[i], ctx)]
                    continue
                except (RecursionError, ValueError, IndexError, KeyError, AttributeError, TypeError):
                    pass
            if i < len(t):
                texty.append(t[i][p:])
        druhy = set()
        for x in texty:
            d = adresa_druh(x, httpie, zname)
            druhy.add(d)
            if d in ('cudzia', 'necitatelna') and len(CUDZIE_NALEZY) < 20:
                CUDZIE_NALEZY.append((d, x.replace(NEZNAME, '$?')[:120]))
        if k_nejasne:
            druhy.add('necitatelna')              # súbor s nastaveniami alebo so zoznamom adries sa nedá prečítať
        if xg and xg[0] and (xg[1] is None or not texty or any(xg[1] in adr_host_cast(x) for x in texty)):
            druhy.add('necitatelna')              # adresa môže prísť zo vstupu xargs, ktorý kotva nevidí
        if 'cudzia' in druhy:
            self.priznaky.add('cudzia-adresa')
            self.kanal(P_CURL, K_CURL)
        elif 'necitatelna' in druhy:
            self.priznaky.add('adresa-necitatelna')
            self.kanal(P_CURL, K_CURL)

    def externe_zmazanie(self, met, adresy, texty, args=None, ctx=None):
        """Zmazanie cez WordPress REST API alebo v CRM Solvedio a zverejnenie cez WordPress REST API (EXT_ZMAZ_CIEL,
        WP_ZVEREJNI; 29. 9.). Metóda v premennej sa tu neberie ako zmazanie — rozhoduje len doslovné DELETE."""
        m = (met or '').upper()
        von = [a for a in adresy if isinstance(a, str) and re.match(r'https?://[^/?#\s$]', a, re.I) and not CURL_LOKALNE.match(a)]
        if not von:
            return
        if m == 'DELETE' and any(EXT_ZMAZ_CIEL.search(a) for a in von):
            self.kanal(P_CURL, K_CURL)
        elif m in ('POST', 'PUT', 'PATCH') and any('/wp-json/' in a or 'rest_route=' in a for a in von):
            if any(WP_ZVEREJNI.search(x) for x in list(texty) + von) or self.wp_telo_zo_suboru(texty, args, ctx):
                self.kanal(P_CURL, K_CURL)

    def wp_telo_zo_suboru(self, texty, args, ctx):
        """Telo zápisu do WordPressu zo súboru (curl -d @post.json, --data-binary @…, wget --post-file=…, http … @…):
        zverejňuje, keď je v súbore status publish/future; súbor, ktorý sa nedá prečítať (cesta v premennej, chýba),
        sa berie ako zverejnenie — nevidno, čo pošle (29. 9. večer, druhé kolo overenia)."""
        texty = list(texty)
        for k, x in enumerate(texty):
            cesta = None
            mm = re.match(r'^(?:-d|--data(?:-binary|-ascii|-urlencode)?=|--json=|--post-file=|--body-file=)?@(.+)$', x)
            if mm:
                cesta = mm.group(1)
            elif x in ('--post-file', '--body-file') and k + 1 < len(texty):
                cesta = texty[k + 1]
            elif re.match(r'^--(?:post|body)-file=', x):
                cesta = x.split('=', 1)[1]
            if not cesta or cesta == '-':
                continue
            if '$' in cesta or ctx is None:
                return True
            p = self.absolutna(cesta, ctx)
            if p and p in self.subory:
                obs = self.subory[p]
            else:
                try:
                    if not p or not os.path.isfile(p) or os.path.getsize(p) > 1024 * 1024:
                        return True
                    with open(p, encoding='utf-8', errors='replace') as f:
                        obs = f.read()
                except OSError:
                    return True
            if WP_ZVEREJNI.search(obs):
                return True
        return False

    def rura_ciele(self, pred, ctx):
        """Reťazce, ktoré `… | xargs <klient>` doplní do príkazu z predchádzajúceho článku rúry (echo/printf argumenty,
        štartovacie cesty find) — adresa privedená cez xargs (echo URL | xargs curl -X POST) inak v príkaze nie je."""
        for w in self.zdroj_xargs(pred, ctx) or []:
            for c, u in self.rozvin(w, ctx):
                yield c if u is None else c[:u]

    def args_lit(self, args, ctx):
        """Známy text všetkých slov po rozvinutí doslovne priradených premenných (cudzí cieľ v premennej sa neschová)."""
        if args is None or ctx is None:
            return []
        out = []
        for w in args:
            out += [c if u is None else c[:u] for c, u in self.rozvin(w, ctx)]
        return out

    def wget(self, t, args=None, ctx=None, extra=(), cmd=None, xg=None):
        """wget je druhý spôsob zápisu do cudzieho systému cez API (24. 9.: curl -X POST na Graph kotva zastavila, wget
        s tou istou adresou púšťala). Metóda: --method[= ]…; bez nej --post-data/--post-file (aj -O -) = POST. Čítanie
        (GET, bez tela) prejde — ako pri curl. Adresa je pozičný argument; hľadá sa v celom príkaze (aj v hlavičke)."""
        met = None
        post = False
        m_telo = []     # telo pre marketingové služby (prepínač MARKETING): --post-data/--body-data, --post-file/--body-file
        j = 0
        while j < len(t):
            x = t[j]
            if x in ('--method', '--body-data', '--body-file', '--header', '--post-data', '--post-file') and j + 1 < len(t) \
                    and '=' not in x:
                if x == '--method':
                    met = t[j + 1]
                if x in ('--post-data', '--post-file'):
                    post = True
                if x not in ('--method', '--header'):
                    m_telo.append((j + 1, 0, 'cesta' if x.endswith('-file') else False))
                j += 2
                continue
            if x.startswith('--method='):
                met = x.split('=', 1)[1]
            elif x.split('=', 1)[0] in ('--post-data', '--post-file'):
                post = True
            n = x.split('=', 1)[0]
            if '=' in x and n in ('--post-data', '--post-file', '--body-data', '--body-file'):
                m_telo.append((j, len(n) + 1, 'cesta' if n.endswith('-file') else False))
            j += 1
        if not met and post:
            met = 'POST'
        if met and '$' in met:      # metóda v premennej (--method="$M")
            met = 'POST'
        if met and met.upper() in ('POST', 'PUT', 'PATCH', 'DELETE') \
                and any(CURL_CIEL.search(x) for x in list(t) + self.args_lit(args, ctx) + list(extra)):
            self.kanal(P_CURL, K_CURL)
        self.externe_zmazanie(met, list(t) + self.args_lit(args, ctx) + list(extra), t, args, ctx)
        m_kfg = wget_kfg(t) if self.cudzia_rezim != 'vypnute' else []
        kfg = self.klient_konfig(m_kfg, t, args, ctx) if m_kfg else None
        self.cudzia_adresa(met, [(i, 0) for i in wget_pozicne(t)], t, args, ctx, extra, kfg=kfg, xg=xg)
        if self.mkt_rezim != 'vypnute' and args is not None and ctx is not None:
            adr = [c for w in args for c in self.mkt_slovo(w, ctx)[0]] + list(extra)
            telo, nezname = self.mkt_telo(m_telo, args, ctx, cmd)
            self.mkt_http(met, adr, telo, nezname)

    def httpie(self, t, args=None, ctx=None, extra=(), xg=None):
        """httpie (`http`/`https`): metóda je pozičné SLOVO (POST/PUT/…), inak GET; položky tela (name=value, name:=json,
        @file, =@file) urobia z GET-u POST. Zápis na cudzí systém (Graph, GitHub API …) = kanál ako pri curl (24. 9.)."""
        met = None
        body = False
        for x in t:
            if x.startswith('-'):
                continue
            if met is None and x.upper() in ('GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'CONNECT', 'TRACE'):
                met = x.upper()
                continue
            if '://' in x:
                continue
            if re.search(r'(?<![:=!<>])=(?!=)', x) or ':=' in x or x.startswith('@') or '=@' in x:
                body = True
        if met is None and body:
            met = 'POST'
        if met in ('POST', 'PUT', 'PATCH', 'DELETE') \
                and any(CURL_CIEL.search(x) for x in list(t) + self.args_lit(args, ctx) + list(extra)):
            self.kanal(P_CURL, K_CURL)
        self.externe_zmazanie(met, list(t) + self.args_lit(args, ctx) + list(extra), t, args, ctx)
        ha = httpie_adresa(t)
        self.cudzia_adresa(met, [(ha, 0)] if ha is not None else [], t, args, ctx, extra, httpie=True, xg=xg)
        if self.mkt_rezim != 'vypnute' and args is not None and ctx is not None:
            adr = [c for w in args for c in self.mkt_slovo(w, ctx)[0]] + list(extra)
            telo = [c for w, x in zip(args, t) if not x.startswith('-') and '://' not in x
                    and (re.search(r'(?<![:=!<>])=(?!=)', x) or ':=' in x or ':' in x) for c in self.mkt_slovo(w, ctx)[0]]
            self.mkt_http(met, adr, telo, False)

    def je_vzdialeny(self, a):
        """scp/rsync spec na cudzí stroj: rsync://…, ssh://…, [user@]host:cesta, [user@]host::modul. Lokálna cesta
        (/…, ~…, ./…, holý názov) alebo URL so schémou (http://…) nie je vzdialený cieľ."""
        if not a or a[0] in '-/~.':
            return False
        if re.match(r'^(rsync|ssh)://', a):
            return True
        i = a.find(':')
        if i <= 0:
            return False
        host = a[:i]
        if '/' in host or a[i:i + 3] == '://':
            return False
        return bool(re.fullmatch(r'([\w.-]+@)?[\w.-]+', host))

    # voľby scp/rsync, ktoré berú hodnotu ďalším slovom (aby sa hodnota nečítala ako koncový bod)
    SCP_ARG = {'-i', '-P', '-o', '-F', '-c', '-l', '-S', '-J', '-D', '-W'}
    RSYNC_ARG = {'-e', '--rsh', '--port', '--address', '--sockopts', '--out-format', '--log-file', '--log-file-format',
                 '--password-file', '--bwlimit', '--temp-dir', '--partial-dir', '--compare-dest', '--copy-dest',
                 '--link-dest', '--chmod', '--timeout', '--contimeout', '--modify-window', '--block-size', '--protocol',
                 '--exclude', '--include', '--exclude-from', '--include-from', '--files-from', '--filter', '--suffix',
                 '--backup-dir', '--max-size', '--min-size', '--rsync-path', '--info', '--debug', '--iconv', '--chown'}

    def scp_rsync(self, pb, args, t, ctx, extra=()):
        """scp/rsync s koncovým bodom na cudzom stroji (upload aj download idú von / na cudzí systém). Lokálny prenos
        (medzi vlastnými priečinkami, do /tmp) prejde — kolegovia ho robia bežne (ptoth 24. 9.: rsync medzi vlastnými
        projektmi). Hodnota vlastnej cesty v premennej sa nečíta (ako inde) — literál stačí na skutočné odoslanie."""
        preskoc = self.SCP_ARG if pb == 'scp' else self.RSYNC_ARG
        if any(self.je_vzdialeny(c) for c in extra):
            self.kanal(P_VON, K_VON)
            return
        j = 0
        while j < len(t):
            x = t[j]
            if not x.startswith('-'):
                for c, u in self.rozvin(args[j], ctx):
                    if u is not None:
                        break
                    if self.je_vzdialeny(c):
                        self.kanal(P_VON, K_VON)
                        return
                j += 1
                continue
            if x in preskoc:
                j += 2
                continue
            j += 1

    def gh(self, t, args=None, ctx=None, cmd=None, pred=None):
        if gh_pomoc(t):
            return
        bez_repo = []
        k = 0
        while k < len(t):          # -R/--repo <repo> pred podpríkazom (gh issue -R o/r create)
            if t[k] in ('-R', '--repo', '--hostname') and k + 1 < len(t):
                k += 2
                continue
            bez_repo.append(t[k])
            k += 1
        poz = [x for x in bez_repo if not x.startswith('-')]
        sub = poz[0] if poz else ''
        sub2 = poz[1] if len(poz) > 1 else ''
        if (sub, sub2) in (('workflow', 'run'), ('run', 'rerun')):
            self.nasadenie(sub2, t, args, ctx, cmd, pred)
            return
        # zápis do nastavení a obsahu na GitHube (24. 9., úloha 2): nový repozitár (PS: gh repo create … --push, 4×),
        # tajomstvá a premenné repozitára (PS 9. 9.: gh variable set BREADTH_GATE_OVERRIDE = obídenie brány nasadenia),
        # gist (zverejnenie). Čítanie (view, list, get, clone) prejde.
        if (sub == 'repo' and sub2 in ('create', 'delete', 'rename', 'archive', 'unarchive', 'edit')) or \
                (sub in ('secret', 'variable') and sub2 in ('set', 'delete', 'remove')) or \
                (sub == 'gist' and sub2 in ('create', 'new', 'edit', 'delete', 'rename')):
            self.kanal(P_GH, K_GH)
            return
        if sub == 'api':
            met = None
            pole = False     # -f/-F/--field/--raw-field/--input bez -X = POST (gh api repos/o/r/issues -f title=x)
            koniec = None
            k = t.index('api') + 1 if 'api' in t else len(t)
            while k < len(t):
                x = t[k]
                if x in ('-X', '--method') and k + 1 < len(t):
                    met = t[k + 1]
                    k += 2
                    continue
                if x.startswith('-X') and len(x) > 2:
                    met = x[2:].lstrip('=')
                elif x.startswith('--method='):
                    met = x.split('=', 1)[1]
                elif x in ('-f', '-F', '--field', '--raw-field', '--input'):
                    pole = True
                    k += 2
                    continue
                elif x.startswith(('-f', '-F', '--field=', '--raw-field=', '--input=')):
                    pole = True
                elif x in ('-H', '--header', '-q', '--jq', '-t', '--template', '-p', '--preview', '--cache', '--hostname'):
                    k += 2
                    continue
                elif not x.startswith('-') and koniec is None:
                    koniec = x
                k += 1
            if not met and pole:
                # graphql: dotaz (query) je čítanie, zápis je len mutation
                met = 'POST' if koniec != 'graphql' or any('mutation' in x for x in t) else None
            if met and '$' in met:      # metóda v premennej (-X "$M"): ber ako zápis na GitHub
                met = 'POST'
            if met and met.upper() in ('POST', 'PUT', 'PATCH', 'DELETE'):
                self.kanal(P_GH, K_GH)
        elif sub in ('issue', 'pr') and sub2 in ('create', 'comment', 'close', 'delete', 'edit', 'merge', 'reopen', 'review',
                                                 'lock', 'unlock', 'transfer'):
            self.kanal(P_GH, K_GH)
        elif sub == 'release' and sub2 in ('create', 'delete', 'edit', 'upload', 'delete-asset'):
            self.kanal(P_GH, K_GH)

    def nasadenie(self, co, t, args, ctx, cmd, pred):
        """gh workflow run / gh run rerun podľa polohy prepínača NASADENIE (rozhodnutie PS 4.35)."""
        rezim = nasadenie_rezim()
        if rezim == 'vypnute':
            return
        ostra = co == 'rerun'           # opakovanie behu: cieľ nevidno → ako ostrá
        if co == 'run' and args is not None and ctx is not None:
            hodnoty = []                # (kľúč, hodnota alebo None = nevie prečítať)
            json_vstup = False
            k = t.index('run') + 1 if 'run' in t else len(t)
            prvy = True
            while k < len(t):
                x = t[k]
                if x in ('-f', '-F', '--field', '--raw-field') and k + 1 < len(args):
                    hodnoty.append(self.vstup(args[k + 1], ctx))
                    k += 2
                    continue
                if x.startswith(('--field=', '--raw-field=')) or (x[:2] in ('-f', '-F') and len(x) > 2):
                    w = Slovo()
                    w.seg = [('l', x.split('=', 1)[1] if x.startswith('--') else x[2:], True)] if '$' not in x else [('v', '__nezname__')]
                    hodnoty.append(self.vstup(w, ctx))
                elif x in ('-r', '--ref') and k + 1 < len(args):
                    hodnoty.append(('ref', self.hodnota_slova(args[k + 1], ctx)))
                    k += 2
                    continue
                elif x.startswith('--ref='):
                    hodnoty.append(('ref', None if '$' in x else x.split('=', 1)[1]))
                elif x in ('-R', '--repo') and k + 1 < len(t):
                    k += 2
                    continue
                elif x == '--json':
                    json_vstup = True
                elif not x.startswith('-') and prvy:
                    prvy = False
                    hodnoty.append(('workflow', self.hodnota_slova(args[k], ctx)))
                k += 1
            # prostredie určuje vstup (target=…); vetva (--ref) len vtedy, keď taký vstup chýba — PS 10. 9. nasadil vetvu
            # deploy/prod-2026-09-10-… s target=preview na Preview (synchronizácia), nie na ostrú
            if any(kl not in ('ref', 'workflow') and CIEL_KLUC.search(kl) and h is not None for kl, h in hodnoty):
                hodnoty = [(kl, h) for kl, h in hodnoty if kl != 'ref']
            for kluc, hod in hodnoty:
                if hod is None:
                    # hodnotu v premennej kotva nevie prečítať: pri vstupe, ktorý určuje prostredie, = možno ostrá
                    ostra |= kluc not in ('ref', 'workflow') and bool(CIEL_KLUC.search(kluc) or PROD.search(kluc))
                elif PROD.search(hod) or (kluc not in ('ref', 'workflow') and PROD.search(kluc)
                                          and hod.strip().lower() in PRAVDA):
                    ostra = True
            if json_vstup:
                texty = [h for h, _ in cmd.heredoc] + [w.text() for w in cmd.herestr]
                if not texty and cmd.rura and pred is not None:
                    texty = [h for h, _ in pred.heredoc] + [w.text() for w in pred.herestr]
                    if pred.slova and os.path.basename(pred.slova[0].text()) in ('echo', 'printf'):
                        texty.append(' '.join(w.text() for w in pred.slova[1:]))
                if not texty or any('$' in x or PROD.search(x) or re.search(r'prod[a-z_]*"?\s*:\s*"?(true|1)', x, re.I)
                                    for x in texty):
                    ostra = True
        if ostra:
            self.kanal(P_NASADENIE, K_OSTRA)
        elif rezim == 'vsetko':
            self.kanal(P_NASADENIE, K_WORKFLOW)

    def nasadenie_ine(self, pb, args, t, ctx, pv=None):
        """Nasadenie do ostrej prevádzky inou cestou než gh workflow run (29. 9., nález nezávislého overenia: kanál sa volal
        „všeobecne pre každý projekt“, no poznal len gh workflow run). Malá sada jednoznačných ciest, bez odhadu:
          · vercel --prod / --target production / vercel promote · netlify deploy --prod · firebase deploy
          · az webapp deploy / az webapp up / az webapp|functionapp deployment source config-zip bez --slot alebo so slotom
            prod/production (bez slotu ide do ostrého — tak PS 14. 9. obnovil PROD bpw, J-304) · az webapp deployment
            slot swap do ostrého slotu (predvolený cieľ je production)
          · skript s „deploy“/„nasad“ v mene spustený s argumentom „prod…“ (bash infra/nasad.sh prod-direct,
            ./deploy.sh production, npm run deploy:prod)
        Náhľad (vercel bez --prod, netlify bez --prod, firebase hosting:channel:deploy, slot staging) prejde. Platí poloha
        NASADENIE: vypnute = nesleduje nič.
        29. 9. večer (prehrávka príkazov PS za 30 dní, druhé kolo overenia — 7 falošných zastavení): aplikácia s menom
        náhľadu (az webapp deploy -n bpw-app-preview — samostatná aplikácia, nie slot) nie je ostrá; skúška nanečisto
        (bash infra/nasad-data.sh prod --dry-run) nie je nasadenie, ak skript tú voľbu naozaj pozná (je v jeho texte —
        skript, ktorý ju nepozná, by nasadil); skript s prod v mene (deploy-prod.sh) je ostrý aj bez argumentu;
        make deploy-prod / make deploy ENV=prod ako npm run deploy:prod."""
        if nasadenie_rezim() == 'vypnute' or any(x in ('--help', '-h', 'help') for x in t):
            return
        poz = [x for x in t if not x.startswith('-')]
        ostra = False
        if pb == 'vercel':
            ostra = any(x in ('--prod', '--production') for x in t) or (poz[:1] == ['promote']) or any(
                (x == '--target' and k + 1 < len(t) and PROD.search(t[k + 1])) or (x.startswith('--target=') and PROD.search(x))
                for k, x in enumerate(t))
        elif pb in ('netlify', 'ntl'):
            ostra = poz[:1] == ['deploy'] and any(x in ('--prod', '-p', '--prod-if-unlocked') for x in t)
        elif pb == 'firebase':
            ostra = poz[:1] == ['deploy']
        elif pb == 'az':
            slova = []
            for x in t:
                if x.startswith('-'):
                    break
                slova.append(x)
            slot = cielovy = None
            for k, x in enumerate(t):
                if x in ('--slot', '-s') and k + 1 < len(t):
                    slot = t[k + 1]
                elif x.startswith('--slot='):
                    slot = x.split('=', 1)[1]
                elif x == '--target-slot' and k + 1 < len(t):
                    cielovy = t[k + 1]
                elif x.startswith('--target-slot='):
                    cielovy = x.split('=', 1)[1]
            ostry_slot = slot is None or '$' in slot or bool(PROD.search(slot))
            meno = None
            for k, x in enumerate(t):
                if x in ('-n', '--name') and k + 1 < len(args):
                    meno = self.hodnota_slova(args[k + 1], ctx)
                elif x.startswith('--name='):
                    meno = x.split('=', 1)[1]
            if meno and '$' not in meno and NASADENIE_NAHLAD_MENO.search(meno) and not PROD.search(meno):
                ostry_slot = False      # bpw-app-preview: samostatná aplikácia náhľadu
            if slova[:2] in (['webapp', 'deploy'], ['webapp', 'up'], ['functionapp', 'deploy']) or \
                    slova[:4] in (['webapp', 'deployment', 'source', 'config-zip'], ['functionapp', 'deployment', 'source', 'config-zip']):
                ostra = ostry_slot
            elif slova[:4] in (['webapp', 'deployment', 'slot', 'swap'], ['functionapp', 'deployment', 'slot', 'swap']):
                ostra = cielovy is None or '$' in cielovy or bool(PROD.search(cielovy))
        elif pb in ('npm', 'pnpm', 'yarn', 'bun'):
            meno = poz[1] if poz[:1] in (['run'], ['run-script']) and len(poz) > 1 else (poz[0] if pb == 'yarn' and poz else '')
            ostra = bool(re.match(r'(?:deploy|nasad)(?:[-_.:]|$)', meno, re.I)) and bool(PROD.search(meno) or any(PROD.search(x) for x in poz[2:]))
        elif pb in ('make', 'gmake'):
            if not any(x in ('-n', '--dry-run', '--just-print', '--recon', '-q', '--question') for x in t):
                ciele = [x for x in poz if '=' not in x]
                ostra = any(re.match(r'(?:deploy|nasad)(?:[-_.:]|$)', x, re.I) for x in ciele) and any(PROD.search(x) for x in poz)
        else:
            skript, dalsie, cesta = None, [], None
            if pb in SHELLY or INTERPRETY.match(pb):
                j = skript_interpretra(pb, t)
                if j is not None and j < len(args):
                    k0 = [c for c, _ in self.rozvin(args[j], ctx)][0]
                    skript = os.path.basename(k0.replace(NEZNAME, ''))
                    cesta = k0 if NEZNAME not in k0 else None
                    dalsie = t[j + 1:]
            else:                       # ./deploy.sh production, infra/nasad prod-direct
                skript, dalsie, cesta = pb, t, (pv if NEZNAME not in (pv or '') else None)
            # meno sa musí slovom nasadenia ZAČÍNAŤ (nasad.sh, deploy-prod.sh): infra/pred-nasadenim.sh production je
            # previerka pred nasadením, nie nasadenie (prehrávka príkazov PS 29. 9. — 4 falošné zastavenia)
            if skript and re.match(r'(?:deploy|nasad)(?:[-_.]|$)', skript, re.I):
                ostra = any(PROD.search(x) for x in dalsie if not x.startswith('--help')) \
                    or bool(PROD.search(re.sub(r'\.[a-z0-9]+$', '', skript, flags=re.I)))
                if ostra and self.skript_pozna_nanecisto(cesta, dalsie, ctx):
                    ostra = False
        if ostra:
            self.kanal(P_NASADENIE, K_OSTRA)

    def skript_pozna_nanecisto(self, cesta, dalsie, ctx):
        """Príkaz nasadenia má voľbu skúšky nanečisto (--dry-run, --nasucho …) a skript ju pozná (je v jeho texte) → True.
        Skript, ktorý sa nedá prečítať, voľbu „nepozná“ (zastaví, 29. 9. večer)."""
        volby = [x for x in dalsie if x in NASADENIE_NANECISTO]
        if not volby or not cesta:
            return False
        p = self.absolutna(cesta, ctx)
        try:
            if not p or not self.smie(p) or not os.path.isfile(p) or os.path.getsize(p) > 1024 * 1024:
                return False
            with open(p, encoding='utf-8', errors='replace') as f:
                obs = f.read()
        except OSError:
            return False
        return all(re.search(r'(?<![\w-])' + re.escape(x) + r'(?![\w-])', obs) for x in volby)

    def hodnota_slova(self, w, ctx):
        k = self.rozvin(w, ctx)
        return k[0][0] if len(k) == 1 and k[0][1] is None else None

    def vstup(self, w, ctx):
        """Vstup workflow „kľúč=hodnota“ → (kľúč, hodnota; None = hodnotu kotva nevie prečítať)."""
        h = self.hodnota_slova(w, ctx)
        if h is None:
            txt = w.text()
            return (txt.split('=', 1)[0] if '=' in txt else txt), None
        if '=' not in h:
            return h, ''
        kluc, hod = h.split('=', 1)
        return kluc, hod

    def rm(self, args, t, ctx, zdroj=None):
        rek = False
        cesty = list(zdroj or [])   # cesty z rúry do xargs (find, echo), keď sa dajú vidieť
        koniec = False
        for w, x in zip(args, t):
            if not koniec and x == '--':
                koniec = True
                continue
            if not koniec and x.startswith('--'):
                rek |= x == '--recursive'
                continue
            if not koniec and x.startswith('-') and len(x) > 1:
                rek |= 'r' in x or 'R' in x
                continue
            cesty.append(w)
        if not rek:
            return
        for w in cesty:       # pri xargs bez viditeľnej cesty niet čo posúdiť (ako doteraz)
            v = self.mazanie(w, ctx)
            if v != 'ok':
                self.kanal(P_RM, K_RM)
                if v == 'nezname':
                    self.priznaky.add('premenna')

    def cudzi_domov(self, pw, args, t, pb, cmd, ctx):
        """Cesta do cudzieho domova v argumente (nie v úvodzovkovej vete, nie v heredocu) alebo cwd v ňom."""
        for op, w in cmd.presm:
            if op in VYSTUP and any(self.cudzi(self.absolutna(c, ctx) or '') for c in self.kandidati_ciest(w, ctx) if c):
                self.kanal(P_DOMOV, K_DOMOV)
                return
        cudzie = bool(ctx.cwd) and ctx.cwd != self.hook_cwd and self.cudzi(ctx.cwd)   # cd do cudzieho domova
        if not cudzie:
            for w in [pw] + list(args):
                for c in self.kandidati_ciest(w, ctx):
                    if c.startswith('/') and not STROJ.fullmatch(c) and self.cudzi(c):
                        cudzie = True
                        break
                if cudzie:
                    break
        if cudzie and not citanie(pb, t):
            self.kanal(P_DOMOV, K_DOMOV)


class _CasVyprsal(BaseException):
    """Rozbor prekročil ROZBOR_CAS (časovač procesora, SIGPROF) alebo ROZBOR_STENA (hodiny, SIGALRM). BaseException —
    nezachytí ju žiadne `except (…Error)` v rozbore."""


class RozborNezbehol(Exception):
    """Rozbor príkazu bez skriptu nezbehol včas — háčik použije núdzové pravidlá ako pri páde rozboru."""


_BUDIK = {'bezi': False}


def _budik_zvoni(cislo, ramec):
    if _BUDIK['bezi']:
        raise _CasVyprsal()


def _budik_zapni(cpu, stena):
    """Časovače celého rozboru: čas procesora (ITIMER_PROF → SIGPROF, KTZ 17. 10.) a čas na hodinách ako poistka proti
    zaveseniu (ITIMER_REAL → SIGALRM). Mimo hlavného vlákna (signál sa nedá nastaviť) chráni len SKRIPT_CAS a
    SKRIPT_STENA pred ďalším skriptom. Bez SIGPROF (iný systém) ostane len poistka na hodinách."""
    try:
        stary = [(signal.SIGALRM, signal.ITIMER_REAL, signal.signal(signal.SIGALRM, _budik_zvoni))]
    except (ValueError, OSError, AttributeError):
        return None
    try:
        stary.append((signal.SIGPROF, signal.ITIMER_PROF, signal.signal(signal.SIGPROF, _budik_zvoni)))
    except (ValueError, OSError, AttributeError):
        pass
    _BUDIK['bezi'] = True
    for sig, tim, _ in stary:
        signal.setitimer(tim, cpu if tim != signal.ITIMER_REAL else stena)
    return stary


def _budik_vypni(stary):
    _BUDIK['bezi'] = False
    if stary is None:
        return
    for sig, tim, h in stary:
        signal.setitimer(tim, 0)
        signal.signal(sig, h)


# SKG 3 (b4, 7. 11.): čo kod_http_zapis číta len z textu kódu (mená klientov, metóda kontextu PHP, curl, parametre
# funkcií, metódy volaní, zápisové volania s doslovnou cestou) — raz pre text, nie pri každom volaní. Skript s celým
# textom (ng-cervene-testy.py, 1,2 MB po popisoch) rozbor posudzuje 7× v rôznych kontextoch a každé kolo prešlo tie isté
# vzory nad celým textom znova (0,3 s z ~5,5 s pri ng-caka-drzi.py). Výsledok je ten istý; čo kod_http_zapis mení
# (metódy a zápisy načítajúceho modulu), dostane ako kópiu.
_HTTP_PARAM = re.compile(r'\bdef\s+\w+\s*\(([^)]*)\)|\bfunction\s*\w*\s*\(([^)]*)\)|\(([^()]*)\)\s*=>|\b(\w+)\s*=>')
_HTTP_PARAM_DEF = re.compile(r'\bdef\s+\w+\s*\(([^)]*)\)')


@_pamat
def _http_zapis_kd(kd):
    """(klienti, klienti_iny, zápis Exchange, metóda kontextu PHP, curl so zápisom, metóda curl, parametre funkcií,
    metódy volaní, zápisové volania) — čisto z textu kd; množiny nemenné, zoznam ako n-tica."""
    klienti = frozenset(x for d in kod_mkt_klienti(kd) for x in d if x)
    klienti_iny = frozenset(x for x in KOD_HTTP_KLIENT_PREM.findall(kd) if x) \
        if any(x in kd for x in KOD_HTTP_KLIENT_PREM_MENA) else frozenset()
    zapis_exo = bool(KOD_EXO_ZAPIS.search(kd))
    m_prud = KOD_PHP_KONTEXT_METODA.search(kd)
    php_prud_met = m_prud.group(1).upper() if m_prud else None
    curl = 'curl_exec' in kd and bool(KOD_PHP_CURL_ZAPIS.search(kd))
    m_pcurl = KOD_PHP_CURL_METODA.search(kd) if curl else None
    curl_met = m_pcurl.group(1).upper() if m_pcurl else 'POST'
    # parametre funkcií: KodRozbor premenné nerozlišuje podľa funkcie (url v post(url, data) by dostala adresu
    # z main()) — adresa či metóda z parametra je neznáma; metóda z parametra sa posúdi podľa toho, s akými metódami
    # sa pomocník v kóde volá (_req("GET", …) všade = čítanie)
    param = set()
    # SKG 3 (b5, 7. 11.): bez „=>“ a „function“ v texte môže zo vzoru nájsť len prvá vetva (def) — tie isté nálezy
    # vzorom len s ňou (ng-cervene-testy.py: 76 → 16 ms)
    vzor = _HTTP_PARAM if ('=>' in kd or 'function' in kd) else _HTTP_PARAM_DEF
    for m in vzor.finditer(kd):
        for x in next((g for g in m.groups() if g), '').split(','):
            x = re.sub(r'[:=].*$', '', x).strip().lstrip('*').strip()
            if re.fullmatch(r'[A-Za-z_]\w*', x):
                param.add(x)
    metody_volani = frozenset(x.upper() for x in re.findall(r'''\(\s*['"](GET|POST|PUT|PATCH|DELETE|get|post|put|patch|delete)['"]''', kd))
    # 2. 10. večer: pomocník volaný so zápisovou metódou len na čítanie alebo koncept pošty (_req("POST", "/me/messages")) —
    # cesta musí byť doslovná pri každom takom volaní, inak zápis
    zapisy = tuple((m.group(1).upper(), m.group(3)) for m in re.finditer(
        r'''\(\s*['"](POST|PUT|PATCH|DELETE|post|put|patch|delete)['"]\s*(?:,\s*f?(['"])([^'"\n]*)\2)?''', kd))
    return klienti, klienti_iny, zapis_exo, php_prud_met, curl, curl_met, frozenset(param), metody_volani, zapisy


# SKG 3 (b8, 9. 11.): predbežné hľadanie knižnice HTTP (KOD_HTTP_KNIZNICA — tridsať slov s re.I bez doslovného začiatku,
# takže vzor skúša každé slovo na každom znaku) stálo pri `python3 .aigrip/scripts/ng-stav.py` 0,39 s nad 112 textami
# (3,6 mil. znakov) a známy cieľ (CURL_CIEL) 0,14 s; s plnou výmenou vzoru popisov je ten príkaz nad stropom rozboru.
# Každá zhoda KOD_HTTP_KNIZNICA obsahuje aspoň jedno slovo z _HTTP_KNIZ_SLOVA (CURL_CIEL z _HTTP_CIEL_SLOVA) a pri re.I
# zodpovedá písmenu ASCII len to isté písmeno ASCII (veľké či malé) alebo znak mimo ASCII, ktorý pri re.I zodpovedá
# písmenu ASCII (na pythone servera İ ı ſ K; _http_maly ich hľadá v texte, nie v zozname). Keď v texte taký znak nie je,
# slovo zhody je v kd.lower() — keď tam nie je ani jedno slovo, vzor nenájde nič; s takým znakom rozhodne vzor nad celým
# textom ako doteraz. `https?://` s re.I je KOD_HTTP_SCHEMA a zároveň prvá vetva knižnice — schéma stačí. Každá vetva
# KOD_HTTP_VSTAVANE (bez re.I) obsahuje „http“ alebo „HTTP“. Slová platia pre vzory s odtlačkom v _HTTP_SLOVA_VZOR
# (dĺžka, príznaky, súčet — _vzor_odtlacok); keď sa ktorýkoľvek z tých štyroch vzorov zmení, prefilter sa vypne sám
# (výsledok rovnaký, len pomalšie), kým sa slová a odtlačok nedoplnia. Overenie (dielňa ~/ng-dielna/skg3i-1109):
# pôvodný a nový výsledok zhodne na textoch projektov PS a skriptoch stroja aj na vymyslených okrajoch (veľké písmená,
# İ ı ſ K, diakritika, slovo na hranici textu).
_HTTP_KNIZ_SLOVA = ('http', 'request', 'urllib', 'axios', 'superagent', 'undici', 'fetch', 'curl_exec', 'file_get_contents',
                    'stream_context_create', 'lwp::', 'rest::client', 'mojo::useragent', 'www::mechanize', 'furl',
                    'restclient', 'faraday', 'typhoeus', 'excon')
_HTTP_CIEL_SLOVA = ('graph.microsoft', 'sharepoint', 'atlassian', 'outlook.office', 'github.com')
_HTTP_I_ASCII = re.compile(r'[a-z]', re.I)


def _vzor_odtlacok(r):
    """Odtlačok vzoru (SKG 3 b8): dĺžka, príznaky, súčet znakov s váhou poradia — bez importu (posudok vlastnej kópie by
    stráž s novým importom nepustil)."""
    p = r.pattern
    return len(p), r.flags, sum((i + 1) * ord(c) for i, c in enumerate(p)) % 1000003


_HTTP_SLOVA_VZOR = ((388, 34, 293724), (148, 32, 957489), (88, 34, 399716), (9, 34, 3290))
_HTTP_SLOVA_PLATIA = tuple(map(_vzor_odtlacok, (KOD_HTTP_KNIZNICA, KOD_HTTP_VSTAVANE, CURL_CIEL,
                                                KOD_HTTP_SCHEMA))) == _HTTP_SLOVA_VZOR


def _http_maly(kd):
    """kd.lower() na predbežné hľadanie slov (SKG 3 b8, vyššie) — None, keď text má znak mimo ASCII, ktorý pri re.I
    zodpovedá písmenu ASCII."""
    if not kd.isascii() and any(c > '\x7f' and _HTTP_I_ASCII.match(c) for c in set(kd)):
        return None
    return kd.lower()


@_pamat
def kod_http_predbezne(kd):
    """K88X: predbežné hľadanie v kode_http_zapis (vstavaný modul, knižnica, známy cieľ, schéma adresy) — raz pre text;
    SKG 3 b8: slová vzoru pred vzorom (vyššie)."""
    if not _HTTP_SLOVA_PLATIA:
        return (bool(KOD_HTTP_VSTAVANE.search(kd)), bool(KOD_HTTP_KNIZNICA.search(kd)), bool(CURL_CIEL.search(kd)),
                bool(KOD_HTTP_SCHEMA.search(kd)))
    m = _http_maly(kd)
    schema = bool(KOD_HTTP_SCHEMA.search(kd))
    vst = ('http' in kd or 'HTTP' in kd) and bool(KOD_HTTP_VSTAVANE.search(kd))
    kniz = schema or ((m is None or any(x in m for x in _HTTP_KNIZ_SLOVA)) and bool(KOD_HTTP_KNIZNICA.search(kd)))
    ciel = (m is None or any(x in m for x in _HTTP_CIEL_SLOVA)) and bool(CURL_CIEL.search(kd))
    return vst, kniz, ciel, schema


# 14. 10. (K88X): čisté funkcie nad textom skriptu sa v jednom rozbore počítajú raz pre text — ten istý skript s rôznymi
# argumentmi (ng-audit.py zapis … jedenásťkrát z ng-zapis.py) a modul od viacerých načítajúcich. Výsledok je text alebo
# pravdivostná hodnota, pamäť ho nemôže zmeniť.
skript_kod = _pamat(skript_kod)
# SKG 3 (b5, 7. 11.): mkt_kod tiež — skript s celým textom (ng-cervene-testy.py) posudzoval marketing 3× nad tým istým
# 1,2 MB textom (3 × 35 ms); výsledok je n-tica (zoznam služieb, tvrdé?) a mkt_nalez zoznam len číta
mkt_kod = _pamat(mkt_kod)
skript_nacita_postu = _pamat(skript_nacita_postu)
kod_posta_sama = _pamat(kod_posta_sama)
kod_posta_siet = _pamat(kod_posta_siet)
py_dosiahnutelne = _pamat(py_dosiahnutelne)
_py_mod_nacitajuci = _pamat(_py_mod_nacitajuci)                 # SKG 3 (b9)
_py_mod_nacitajuci_zvysok = _pamat(_py_mod_nacitajuci_zvysok)   # SKG 3 (b9)
sh_obaly = _pamat(sh_obaly)


def rozober(cmd, cwd, domov):
    t0 = time.monotonic()
    stav = {'c0': time.process_time()}
    stary = _budik_zapni(ROZBOR_CAS, ROZBOR_STENA)
    try:
        try:
            _rozober(cmd, cwd, domov, t0, stav)
            _BUDIK['bezi'] = False
        except _CasVyprsal:
            r = stav.get('r')
            st = r.skript_stav if r is not None else None
            if st is None or not (st['citania'] or st['subory'] or st['bajty']):
                raise RozborNezbehol('čas')   # príkaz bez skriptu — núdzové pravidlá háčika ako pri páde rozboru
            # skript, ktorý sa práve čítal, je nedočítaný; kanál nájdený predtým ostáva
            r.kanal(P_SKRIPT, K_SKRIPT)
            r.priznaky.add('skript')
    finally:
        _budik_vypni(stary)
    r = stav['r']
    # 29. 9.: príznak viac-kanalov — príkaz má viac neobnoviteľných kanálov než ten, ktorý menuje druhý riadok; kotva ho
    # môže poslať overeniu potvrdenia (ng-kotva-over.py, 6. argument), aby výnimka pre nasadenie neplatila
    if len(r.kanaly) > 1:
        r.priznaky.add('viac-kanalov')
    # 3. 10.: zastavenie len pre nedočítaný skript — háčik nepovie, že kotva krok v skripte prečítala
    if r.kanaly and max(r.kanaly) == P_SKRIPT:
        r.priznaky.add('nedocitane')
    return r.vysledok(), sorted(r.priznaky)


def _rozober(cmd, cwd, domov, t0, stav):
    cmd2 = sh_obaly(cmd)     # 2. 10. večer: f() { "$@"; }; f az … — argumenty obalu sa posúdia ako príkaz
    r = stav['r'] = Rozbor(cwd, domov)
    r.skript_stav['zaciatok'] = t0
    r.skript_stav['zaciatok_cpu'] = stav.get('c0', r.skript_stav['zaciatok_cpu'])
    r.skript_stav['texty'] = [cmd2]       # 3. 10. (kolo 4): text príkazu a prečítaných skriptov (eval, priradene_mena)
    r.prikazy(parsuj(cmd2), Ctx(cwd or None))
    if r.exo_unik:
        # 2. 10.: cesta k exo-rest.py v premennej sa použila aj inak než na priame spustenie nástroja (Rozbor.exo_odkaz) —
        # celý príkaz sa posúdi znova ako pred 2. 10.
        r = stav['r'] = Rozbor(cwd, domov)
        r.skript_stav['zaciatok'] = t0
        r.skript_stav['zaciatok_cpu'] = stav.get('c0', r.skript_stav['zaciatok_cpu'])
        r.skript_stav['texty'] = [cmd2]
        r.exo_po_starom = True
        r.prikazy(parsuj(cmd2), Ctx(cwd or None))


def main():
    cwd = sys.argv[1] if len(sys.argv) > 1 else ''
    domov = sys.argv[2] if len(sys.argv) > 2 else os.environ.get('HOME', '/home/pstrocka')
    if len(cwd) > 1:
        cwd = cwd.rstrip('/')
    cmd = sys.stdin.buffer.read().decode('utf-8', 'replace')
    sys.setrecursionlimit(5000)
    # SKG 3 (b5, 7. 11.): upratovanie pamäte (gc) každých 20 000 nových objektov namiesto 700. Rozbor tvorí veľa
    # krátko žijúcich objektov (stromy kódu, tokeny) a cyklický odpad skoro žiadny — pri `python3 …/ng-caka-drzi.py`
    # s celým textom ng-cervene-testy.py bolo 14 úplných upratovaní za 0,3 s z ~6 s; po zmene žiadne, čas 6,0 → 5,7 s,
    # najväčšia pamäť 166 → 168 MB. Čo rozbor rozhodne, sa nemení — len koľko času naň minie. Len v main() (háčik);
    # rozbor volaný inak (skúšky, prehrávky) si upratovanie nastaví sám.
    import gc
    gc.set_threshold(20000, 10, 10)
    try:
        kanal, priz = rozober(cmd, cwd, domov)
    except RozborNezbehol:
        sys.exit(3)            # rozbor príkazu bez skriptu nezbehol včas — háčik použije núdzové pravidlá
    sys.stdout.write(ZNACKA + '\n' + kanal.replace('\n', ' ') + '\n' + ' '.join(priz) + '\n')


if __name__ == '__main__':
    main()
