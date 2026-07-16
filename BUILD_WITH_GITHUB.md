# APK bouwen via GitHub Actions

1. Maak een nieuwe GitHub-repository.
2. Upload de volledige inhoud van deze projectmap.
3. Open in GitHub het tabblad **Actions**.
4. Kies **Build debug APK**.
5. Klik **Run workflow**.
6. Na een geslaagde build staat onderaan de workflow-run het artifact `RaspiCarDashboard-v1-debug`.
7. Download en pak het artifact uit; daarin staat `app-debug.apk`.

De workflow draait ook automatisch na een push naar `main` of `master`.
