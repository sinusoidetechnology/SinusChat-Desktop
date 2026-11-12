# SinusChat-Desktop

SinusChat na komputer z Windowsem i Linuksem. Nie ma osobnego konta: łączy się z SinusChat na Twoim telefonie w tej samej sieci Wi-Fi (albo przez hotspot telefonu), a wiadomości, zdjęcia i połączenia przechodzą przez telefon i są szyfrowane end-to-end tak samo jak w aplikacji. Strona: [sinusoide.pl](https://sinusoide.pl).

- czaty, grupy, zdjęcia, filmy, pliki i wiadomości głosowe z telefonu,
- rozmowy głosowe i wideo z komputera,
- parowanie kodem QR, połączenie szyfrowane kluczami urządzeń,
- PIN, blokada po czasie, po zminimalizowaniu, po uśpieniu komputera i po odłączeniu telefonu,
- blokada, gdy odejdziesz od komputera z telefonem (Bluetooth, Windows),
- powiadomienia, praca w tle i autostart,
- własny instalator, automatyczne aktualizacje podpisane kluczem Ed25519,
- wersja przenośna bez instalacji i jednorazowa sesja, która po zamknięciu usuwa wszystkie dane,
- 6 języków: polski, angielski, czeski, ukraiński, niemiecki i chiński tradycyjny.

## Pobieranie

Najnowsza wersja: [sinusoide.pl/pobierz.html](https://sinusoide.pl/pobierz.html#komputer) albo zakładka [Releases](https://github.com/sinusoidetechnology/SinusChat-Desktop/releases).

| Plik | Dla kogo |
|---|---|
| `SinusChat-Desktop-windows-x64-setup.exe` | Windows 10 i 11 (64-bit), instalacja dla bieżącego użytkownika |
| `SinusChat-Desktop-windows-x64-portable.zip` | Windows bez instalacji, dane w folderze `data` obok programu |
| `SinusChat-Desktop-windows-x64-portable-session.zip` | Windows, jednorazowa sesja usuwana po zamknięciu okna |
| `SinusChat-Desktop-linux-x64.deb` | Ubuntu, Debian |
| `SinusChat-Desktop-linux-x64.rpm` | Fedora, openSUSE |

Potrzebny jest telefon z najnowszą wersją [SinusChat](https://github.com/sinusoidetechnology/SinusChat).

## Budowanie

Potrzebne: JDK 21 oraz kod [SinusChat](https://github.com/sinusoidetechnology/SinusChat) obok tego repozytorium, bo moduł `link` (protokół połączenia z telefonem) jest wspólny:

```
Documents/
├── AndroidStudioProjects/SC      ← repozytorium SinusChat
└── sinuschatwindowsapp           ← to repozytorium
```

```bash
./gradlew :desktop:run
```

Pakiety:

```bash
./gradlew :desktop:packageSetup            # instalator Windows (wymaga Inno Setup 6)
./gradlew :desktop:packagePortable          # wersja przenośna
./gradlew :desktop:packagePortableSession   # jednorazowa sesja
./gradlew :desktop:packageDeb :desktop:packageRpm   # Linux
```

Pakiety buduje się na systemie, dla którego są przeznaczone. Pomocnik Bluetooth dla Windows (`desktop/ble/SinusBle.cs`) kompiluje się sam wbudowanym w Windows kompilatorem .NET.

## Wydania i aktualizacje

Wydanie tworzy automatycznie GitHub Actions (`.github/workflows/desktop-release.yml`) po zmianie `appVersion` w `desktop/build.gradle.kts` albo po ręcznym uruchomieniu. Buduje instalatory dla Windows i Linuksa, podpisuje plik `desktop-latest.json` kluczem z sekretu `SINUSCHAT_UPDATE_KEY` i publikuje wszystko w Releases. Zainstalowana aplikacja sprawdza ten plik, weryfikuje podpis i sumy kontrolne, pobiera aktualizację w tle i instaluje ją przy ponownym uruchomieniu.

Klucz prywatny do podpisywania nigdy nie trafia do repozytorium.

## Licencja

AGPL-3.0 z warunkami dodatkowymi z sekcji 7 (nazwy i znaki, oznaczenie zmienionej wersji, autorstwo, poparcie) — pełny tekst w [LICENSE](LICENSE), warunki w [NOTICE.md](NOTICE.md). Zmieniona wersja musi mieć własną nazwę, ikonę, identyfikator i klucz podpisywania aktualizacji.

## Bezpieczeństwo

Błędy bezpieczeństwa zgłaszaj prywatnie, według [SECURITY.md](SECURITY.md).

## English

SinusChat-Desktop is SinusChat for Windows and Linux computers. It has no separate account: it connects to SinusChat on your phone over the same Wi-Fi network (or the phone's hotspot), and messages, photos and calls go through the phone with the same end-to-end encryption. It offers PIN and proximity lock, background mode, its own installer with signed automatic updates, a portable version and a one-time session that deletes all data on close. Build it with JDK 21 next to a checkout of [SinusChat](https://github.com/sinusoidetechnology/SinusChat) (shared `link` module) using `./gradlew :desktop:run`. Releases are built by GitHub Actions. Licensed under AGPL-3.0 with additional terms, see [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md). Report security issues privately as described in [SECURITY.md](SECURITY.md).
