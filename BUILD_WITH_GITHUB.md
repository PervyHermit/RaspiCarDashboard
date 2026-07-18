# V2 APK bouwen via GitHub Actions

1. Kopieer de volledige inhoud van deze V2-projectmap over je lokale `RaspiCarDashboard` repository.
2. Controleer dat `.github`, `app` en `docs` nog aanwezig zijn.
3. Open GitHub Desktop.
4. Commit bijvoorbeeld met: `RaspiCar Dashboard V2`.
5. Klik **Push origin**.
6. Open de repository op GitHub en ga naar **Actions**.
7. Open de nieuwe groene workflow-run **Build debug APK**.
8. Download onder **Artifacts**: `RaspiCarDashboard-v2-debug`.
9. Pak het artifact uit; daarin staat `app-debug.apk`.

De workflow draait automatisch bij iedere push naar `main` of `master`.

## Let op bij installeren over V1

GitHub-hosted runners kunnen voor debug-builds een nieuwe debug signing key gebruiken. Android kan daarom melden dat V2 niet over V1 kan worden geïnstalleerd. In dat geval moet V1 eerst worden verwijderd. Daardoor worden de zes ingestelde app-slots en andere lokale voorkeuren gewist.

Voor latere versies is een vaste persoonlijke signing key via GitHub Secrets aan te raden, zodat updates zonder verwijderen kunnen worden geïnstalleerd.
