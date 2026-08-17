# Cronologia versioni

## 1.1.1 — 17 agosto 2026

- Corretto il crash all’avvio riscontrato su alcuni firmware Android.
- Pianificazione degli aggiornamenti spostata fuori dal percorso critico di apertura.
- Eventuali rifiuti del `JobScheduler` non chiudono più l’app: resta disponibile il controllo manuale.
- Minificazione e riduzione risorse temporaneamente disattivate per la massima compatibilità OEM.

## 1.1.0 — 16 agosto 2026

- Ripristino persistente del rilevamento dopo il riavvio.
- Riavvio automatico in modalità prossimità e notifica di riattivazione per la fotocamera.
- Pausa configurabile da 0 a 120 secondi dopo lo spegnimento dello schermo.
- Visualizzazione del timeout Android e collegamento diretto alle impostazioni schermo.
- Controllo giornaliero, download automatico e verifica SHA-256 degli aggiornamenti.
- Collegamento al repository pubblico e contatto email dell’autore.
- APK di release firmato con una chiave dedicata.

## 1.0.0 — versione di prova

- Rilevamento locale tramite fotocamera frontale o sensore di prossimità.
- Risveglio dello schermo senza gestione della modalità chiosco.
- Regolazione della sensibilità e servizio in primo piano.
