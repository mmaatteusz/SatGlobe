# SatGlobe

Natywna aplikacja na Androida pokazująca na żywo satelity widziane przez
odbiornik GNSS telefonu. Satelity GPS, Galileo, GLONASS, BeiDou, QZSS, SBAS i
NavIC są rysowane jako kolorowe punkty oraz promienie nad interaktywnym globem
3D.

## Co działa

- rzeczywista lista satelitów raportowana przez Androida;
- azymut, elewacja, C/N0 i informacja, czy satelita jest używany w ustalaniu
  pozycji;
- liczba widocznych i używanych satelitów, średnia siła sygnału oraz dokładność
  pozycji;
- glob OpenGL ES 2.0 obracany palcem i skalowany gestem;
- wyróżnienie wybranego satelity;
- działanie offline bez uprawnienia do Internetu;
- Android 7.0 i nowszy (API 24+).

## Ważne ograniczenie

GnssStatus podaje kierunek satelity względem telefonu, ale nie udostępnia
pełnej bieżącej pozycji orbitalnej ECEF. SatGlobe zachowuje prawdziwy azymut i
elewację, natomiast umieszcza marker na skompresowanej, umownej wysokości nad
Ziemią. Dzięki temu glob jest czytelny i aplikacja nie udaje dokładności, której
sam telefon nie dostarcza.

## Budowanie APK

Najprościej uruchomić workflow **Build Android APK** w zakładce Actions.
Workflow testuje projekt, uruchamia lint, buduje debug APK i publikuje artefakt
SatGlobe-APK. Ten APK jest podpisany automatycznym certyfikatem debug i nadaje
się do bezpośredniej instalacji testowej.

Lokalnie:

1. Otwórz katalog w Android Studio.
2. Użyj JDK 17 oraz Android SDK 35.
3. Wybierz wariant debug.
4. Uruchom aplikację na prawdziwym telefonie; emulator zwykle nie dostarcza
   realnego statusu satelitów.

Do publikacji w Google Play trzeba zbudować wariant release i podpisać go
prywatnym kluczem, którego nie wolno dodawać do repozytorium.

## Obsługa

1. Dotknij **Uruchom skan GNSS**.
2. Zezwól na dokładną lokalizację podczas używania aplikacji.
3. Wyjdź na zewnątrz lub stań przy oknie, jeśli odbiornik nie widzi satelitów.
4. Obracaj glob jednym palcem, przybliżaj dwoma; podwójne dotknięcie resetuje
   widok.
5. Dotknij kafelka satelity, aby wyróżnić go na globie.

## Struktura

- GnssController — nasłuch GnssStatus i lokalizacji;
- GlobeRenderer — glob, tekstura, linie widoczności i markery OpenGL;
- SatelliteMath — transformacja azymut/elewacja do układu Ziemi;
- tools/generate_earth_texture.py — reprodukowalna tekstura z Natural Earth.

Licencja kodu: MIT. Dane Natural Earth: domena publiczna.
