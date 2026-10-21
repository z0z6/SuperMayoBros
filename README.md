# Maja: Łąkowa Przygoda 🐝

Platformówka na Androida w stylu Super Mario Bros – bohaterowie z kreskówki „Pszczółka Maja”.
Cała grafika i dźwięki są generowane w kodzie (zero plików graficznych/audio), więc projekt jest samowystarczalny.

## Jak zbudować APK (GitHub Actions)

1. Utwórz nowe repozytorium na GitHubie i wgraj do niego **zawartość tego folderu** (cały, razem z ukrytym `.github/`).
   ```
   git init && git add . && git commit -m "Maja game"
   git branch -M main
   git remote add origin https://github.com/TWOJ_LOGIN/maja-game.git
   git push -u origin main
   ```
2. Wejdź w zakładkę **Actions** → workflow **Build APK** (uruchomi się sam po pushu; możesz też kliknąć *Run workflow*).
3. Po ok. 3–5 minutach otwórz zakończony przebieg i pobierz artefakt **maja-debug-apk** (ZIP z `app-debug.apk`).
4. Skopiuj APK na telefon i zainstaluj (włącz „instalowanie z nieznanych źródeł”).

Gradle jest instalowany przez akcję `gradle/actions/setup-gradle`, więc nie musisz commitować `gradlew` ani `gradle-wrapper.jar`.
APK debugowy jest podpisany kluczem debug – nadaje się do instalacji na własnym telefonie.

## Sterowanie (ekran dotykowy, tryb poziomy)

| Przycisk | Akcja |
|---|---|
| ◀ ▶ (lewy dół) | ruch |
| SKOK (prawy dół) | skok – im dłużej trzymasz, tym wyżej (jak w Mario) |
| BIEG (obok SKOKU) | bieg; z kwiatem – strzał kulką pyłku |
| ‖ / 🔊 (prawy górny róg) | pauza / dźwięk |

## Bohaterowie

* **Maja** – pszczółka; zwinna, szybko zmienia kierunek.
* **Gucio** (odpowiednik Luigiego) – konik polny; skacze wyżej, ale się ślizga.

## Odpowiedniki z Mario

| Mario | Maja |
|---|---|
| Grzyb | słoik miodu → Maja rośnie (może rozbijać cegły) |
| Kwiat ognia | kwiat → strzelanie kulkami pyłku |
| Zielony grzyb 1UP | biedronka |
| Gwiazdka | świetlik (nieśmiertelność) |
| Monety | kropelki miodu |
| Rury | łodygi |
| Flaga i zamek | kwiat-meta i ul |
| Goomba | chrząszcz |
| Koopa | ślimak (skorupa do kopania) |
| Latający Koopa | szerszeń |
| Piranha | pająk Tekla na nitce |
| Bowser | Tekla – boss (3 skoki na głowę) |
| NPC | Alek (truteń) – przyjazny, podpowiada |

## Poziomy

1. **Słoneczna Łąka** – klasyczny świat 1-1 z kwiatami i wzgórzami
2. **Pod Korzeniami** – podziemia, pająki spuszczające się z sufitu
3. **Korony Drzew** – skakanie po liściach nad przepaścią, mnóstwo szerszeni
4. **Gniazdo Szerszeni** – plastry miodu i walka z Teklą

## Fizyka

Stały krok 60 FPS, wartości zbliżone do SMB: przyspieszenie i tarcie, poślizg przy zawracaniu,
prędkość chodu/biegu, wysokość skoku zależna od prędkości i od czasu trzymania przycisku,
mocniejsza grawitacja przy opadaniu, odbijanie się od wrogów, uderzanie głową w bloki.
Parametry znajdziesz w `Game.kt` w funkcji `updatePlayer()`.

## Struktura

```
app/src/main/java/pl/maja/game/
  Level.kt       – kafelki, definicje 4 poziomów (łatwo dodać kolejne)
  Game.kt        – fizyka, wrogowie, power-upy, logika
  GameView.kt    – rysowanie (grafika w kodzie), dotyk, pętla gry
  Sfx.kt         – generowane efekty dźwiękowe
  MainActivity.kt
```

Nowy poziom: dodaj funkcję w `Levels` (wzoruj się na `level1()`) i zwiększ `Levels.COUNT`.

## Uwaga prawna

Pszczółka Maja, Gucio, Alek i Tekla to postacie objęte prawami autorskimi. Projekt to gra fanowska
do użytku prywatnego – nie publikuj jej w Google Play ani nie sprzedawaj bez zgody właścicieli praw.
