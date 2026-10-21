# Maja: Łąkowa Przygoda 🐝

Platformówka w stylu Super Mario Bros z bohaterami „Pszczółki Mai”.
**Jedno źródło gry: `web/index.html`.** Aplikacja Android to cienka powłoka (WebView),
która pakuje ten sam plik do APK – zmieniasz grę w jednym miejscu i aktualizują się obie wersje.

```
web/index.html                 <- CAŁA gra (logika, grafika, dźwięk, sterowanie)
app/                           <- powłoka Androida (WebView) + klucz podpisu
  src/main/java/.../MainActivity.kt
.github/workflows/build.yml    <- budowanie APK + publikacja w Releases
.github/workflows/pages.yml    <- publikacja web/ na GitHub Pages
```

## Jak działa aktualizacja

Każdy push do `main`:

1. **Build APK** buduje aplikację, wkładając do niej aktualny `web/index.html`, i wrzuca `maja-1.N.apk`
   do zakładki **Releases** (N = numer builda). Nowy APK instaluje się *na wierzch* starego,
   bo ma wyższy `versionCode` i ten sam klucz podpisu (`app/maja-debug.keystore`).
2. **Deploy web** (tylko gdy zmieniło się coś w `web/`) publikuje stronę na GitHub Pages.

## Pierwsze uruchomienie

1. Wgraj całą zawartość tego folderu do repozytorium (razem z `.github/`).
2. **Settings → Pages → Source: GitHub Actions** (raz) – dla wersji WWW.
3. **Actions** – po kilku minutach w **Releases** pojawi się APK; strona będzie pod
   `https://TWOJ_LOGIN.github.io/NAZWA_REPO/`.
4. Na telefonie otwórz Releases, pobierz APK i zainstaluj. Kolejne wersje: pobierz nowszy APK i instaluj
   bez odinstalowywania.

## Styl graficzny

Grafika jest wzorowana na kreskówce z lat 70./80.: postacie mają ciemny kontur i proporcje z serialu
(Maja ze złotą kręconą grzywką, Gucio – trutek (samiec pszczoły) z brązową czupryną i czerwoną muszką, Alek z opuszczonymi powiekami, chrząszcz z czerwonym szalikiem),
a świat jest „malowany gwaszem” (koniczyny, jaskry, tekstury ziemi). Wszystko rysowane jest w kodzie – w `web/index.html`,
sekcja `STYL`. Kafelki i warstwy tła są rysowane raz do tekstur (`mkTex`), a potem tylko wklejane, żeby gra była płynna.
Na wolnym telefonie gra sama obniża rozdzielczość, a potem włącza tryb lekki (bez dalszych warstw tła).

## Sterowanie dotykowe

Na telefonie w poziomie gra jest w środku, a po bokach są **marginesy ze sterowaniem** (palce nie zasłaniają gry):

| Układ | Lewy margines | Prawy margines |
|---|---|---|
| **Podzielony** (domyślny) | ◀ na górze, **SKOK** pod spodem | ▶ na górze, **BIEG / STRZAŁ** pod spodem |
| **Klasyczny** | ◀ ▶ obok siebie | **BIEG / STRZAŁ** na górze, **SKOK** pod spodem |

Przycisk ⇄ (w prawym górnym rogu) przełącza układ i zapamiętuje wybór. Obok są: pauza, dźwięk, pełny ekran.
Przyciski to duże, stykające się strefy – można przesuwać palec z ◀ na ▶ bez odrywania.

Płynność: rozdzielczość rysowania jest ograniczona, a gdy telefon nie nadąża, gra sama ją obniża.

## Rozwój lokalny

Otwórz `web/index.html` w przeglądarce – to cała gra. Klawisze: ← → / A D ruch, Spacja / Z / ↑ skok,
Shift / X bieg (z kwiatem strzał), P pauza, M dźwięk, F pełny ekran.

## Uwagi

* Czcionka Fredoka jest pobierana z Google Fonts (APK ma na to uprawnienie INTERNET).
  Bez internetu gra używa czcionki systemowej – wszystko poza tym działa offline.
* Klucz `app/maja-debug.keystore` jest w repo celowo (prosty podpis hobbystyczny, żeby aktualizacje działały).
  Do publikacji w sklepie użyj własnego, tajnego klucza.
* GitHub Pages w darmowym planie wymaga publicznego repo. Postacie (Maja, Gucio, Alek, Tekla) są chronione
  prawem autorskim – to gra fanowska; nie publikuj jej w Google Play ani komercyjnie.
