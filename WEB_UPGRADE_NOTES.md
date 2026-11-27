# Web version upgrade (web_preview/)

Drop-in replacement: `web_preview/index.html` + `manifest.json` (same two files as before).
`start_tablet_server.bat` works unchanged. The native Kotlin app was **not** modified.

Your existing notes are migrated automatically on first launch (old localStorage data is left in place as a safety copy).

Notes are stored in IndexedDB for the page address you open (e.g. http://10.223.63.183:8080).
If your laptop's IP changes, the tablet sees a new empty origin — use Menu → Settings → "Back up everything"
before that, then "Restore from file" on the new address.
