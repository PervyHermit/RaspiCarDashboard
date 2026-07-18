# V1 naar V2 bijwerken

1. Sluit GitHub Desktop.
2. Pak `RaspiCarDashboard-V2-update-only.zip` uit.
3. Kopieer alle mappen en bestanden uit de zip naar je lokale repositorymap.
4. Kies **Bestanden in de bestemming vervangen**.
5. Open GitHub Desktop.
6. Controleer dat onder andere deze wijzigingen zichtbaar zijn:
   - `DashboardActivity.java`
   - `SettingsActivity.java`
   - `ExternalAppOverlayService.java` (nieuw)
   - `activity_dashboard.xml`
   - `activity_settings.xml`
   - `AndroidManifest.xml`
   - `app/build.gradle`
   - `.github/workflows/build-apk.yml`
7. Commit met bijvoorbeeld `RaspiCar Dashboard V2`.
8. Klik **Push origin**.
9. Download na de groene Action-run het artifact `RaspiCarDashboard-v2-debug`.

## VLC eenmalig instellen

Open VLC en zet de video-achtergrondmodus op Picture-in-Picture. De exacte vertaling kan per VLC-versie verschillen, maar de optie staat bij VLC-instellingen onder Video / Background-PiP mode.

Daarna:

1. Tik in RaspiCar op VLC.
2. Start een video.
3. Tik op de zwevende knop **VLC zwevend**.
4. VLC hoort als verplaatsbaar PiP-venster boven RaspiCar/Waze te blijven.

RaspiCar kan VLC niet rechtstreeks dwingen tot PiP; de overgang wordt door VLC zelf uitgevoerd.
