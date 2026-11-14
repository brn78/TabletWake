# Cronologia versioni

## 1.1.2 — 14 novembre 2026

- Aggiornamenti: un download interrotto non blocca più i controlli successivi, gli APK scaricati in precedenza vengono eliminati e la verifica dell’APK avviene fuori dal thread principale.
- L’hash SHA-256 del manifest viene accettato solo se è esadecimale e di 64 caratteri.
- Fotocamera: analisi dei fotogrammi più robusta con dimensioni non valide o indici fuori dal buffer, senza allocare memoria a ogni fotogramma.
- Servizio: avvio in primo piano corretto anche senza permesso fotocamera, ciclo di vita senza transizioni ripetute e risveglio protetto dalle chiamate concorrenti.
- Interfaccia: il pulsante degli aggiornamenti resta disattivato durante il controllo e il pulsante di avvio si disattiva se il tablet non ha né fotocamera frontale né sensore di prossimità.
- Testi di notifiche ed errori spostati nelle risorse dell’app.
- Nuovi test per il rilevatore di movimento.

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
