# SMS Backup USB

Offline Android-app som exporterar alla SMS till en användarvald mapp eller USB-enhet via Android Storage Access Framework.

## Exportformat
- JSON: komplett maskinläsbar backup
- HTML: lättläst kopia som kan öppnas i webbläsare

## Integritet
Appen deklarerar ingen INTERNET-behörighet. Export sker endast efter uttryckligt knapptryck.

## Android-begränsning
READ_SMS är en hard-restricted Android-behörighet på moderna Android-versioner. Om vanlig installation inte kan ge SMS-behörigheten måste installatören tillåta restricted permissions, till exempel vid ADB-installation med `--allow-restricted-permissions`.
