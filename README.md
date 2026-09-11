# Mat & Fika 1.0

Svensk offlineapp för Android, med egen appidentitet `se.steffy.matfika`.
16 originalrecept: 8 maträtter och 8 bakrecept. Sökning, kategorier,
favoriter, egna recept, portionsberäkning, inköpslista, veckomeny,
foto/PDF-import och säkerhetskopiering med inbäddade receptbilder.

Gränssnittet anpassar antal receptkolumner efter fönstrets bredd.
Långa textfält växer i sidans scrollvy. Osparad recepttext bevaras
vid återskapande av aktiviteten, till exempel när DeX-fönstret ändrar storlek.

## Bygg
Java 17, Gradle 8.9, Android SDK 35.
`gradle :app:assembleRelease` skapar en osignerad release-APK.
Signera med den privata Mat & Fika-nyckeln; nyckeln får inte läggas i git.

## Verifiering
Workflow bygger debug och release, kör Android lint och en instrumenterad
smoketest på Android 15/API 35 i mobil- och skrivbordsformat.
Testklassen finns bara i debugvarianten och ingår inte i release-APK.
Testet kontrollerar receptdata, kategorier, sökning, favoriter, skalning,
inköpslista, sparande av lång text och backupformat samt tar skärmbilder.
Samsung One UI och fysisk DeX behöver verifieras på användarens enhet.
