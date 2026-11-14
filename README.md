# Tablet Wake

Tablet Wake è una piccola app Android pensata per i tablet dedicati a Home Assistant in modalità chiosco. Fa una sola cosa e la fa localmente: riaccende lo schermo quando rileva una presenza, lasciando launcher, dashboard, blocco schermo, luminosità e navigazione alle app e alle impostazioni già presenti sul dispositivo.

## Perché usarla

Un pannello Home Assistant a parete non dovrebbe restare acceso inutilmente, né dipendere per forza da un sensore esterno. Tablet Wake sfrutta l’hardware già integrato nel tablet:

- **Fotocamera frontale:** riconosce variazioni della scena a distanza. I fotogrammi sono elaborati in memoria, non vengono registrati né trasmessi.
- **Sensore di prossimità:** riaccende il display quando una mano si avvicina, con consumi inferiori se il dispositivo dispone di un sensore adatto.
- **Pausa dopo spegnimento:** ignora temporaneamente il rilevamento dopo lo spegnimento, così il display non si riaccende immediatamente quando viene spento volontariamente.
- **Persistenza:** ricorda se il rilevamento era attivo e lo ripristina dopo il riavvio quando Android lo consente.
- **Aggiornamenti automatici:** controlla ogni 24 ore il repository ufficiale, scarica l’APK più recente e demanda ad Android la conferma finale d’installazione.

## Comportamento dopo il riavvio

- Con il **sensore di prossimità**, Tablet Wake prova a ripristinare automaticamente il servizio.
- Con la **fotocamera**, Android impedisce a un’app in background di aprirla silenziosamente dopo il boot. Tablet Wake mostra quindi una notifica **Riattiva**: basta un solo tocco. Anche aprire l’app riavvia il monitoraggio memorizzato.

Se le notifiche sono disabilitate, dopo il riavvio è necessario aprire manualmente l’app. Su alcuni tablet conviene inoltre escludere Tablet Wake dalle ottimizzazioni aggressive della batteria.

## Timeout dello schermo

Il timeout resta gestito da Android perché è un’impostazione globale del dispositivo. L’app ne mostra il valore corrente e apre direttamente la relativa pagina di sistema, senza richiedere il permesso invasivo per modificare le impostazioni globali.

## Aggiornamenti

L’app legge [`version.json`](version.json) dal branch `main`. Quando `versionCode` è più recente:

1. scarica l’asset `TabletWake.apk` della release indicata;
2. verifica l’hash SHA-256 dichiarato;
3. mostra una notifica per avviare l’installer Android.

Android richiede sempre la conferma dell’utente per installare un APK, salvo configurazioni amministrate come *device owner* o app di sistema. Alla prima installazione può inoltre essere necessario consentire a Tablet Wake l’installazione da questa origine.

## Requisiti e prova

- Android 8.0 o successivo;
- fotocamera frontale oppure sensore di prossimità;
- alimentazione continua consigliata per la modalità fotocamera, idealmente con protezione intelligente della batteria.

Procedura consigliata:

1. installare l’APK dalla sezione **Releases**;
2. scegliere la modalità compatibile con il tablet;
3. concedere fotocamera e notifiche quando richiesto;
4. premere **Avvia rilevamento**;
5. impostare la pausa dopo spegnimento, inizialmente 30 secondi;
6. controllare le impostazioni di risparmio batteria e del timeout schermo.

## Privacy e sicurezza

- Nessun account e nessun servizio cloud per il rilevamento.
- Nessuna immagine salvata o inviata in rete.
- Accesso Internet usato esclusivamente per il manifest e l’APK degli aggiornamenti dal repository `brn78/TabletWake`.
- Verifica SHA-256 dell’APK prima di proporre l’installazione.
- Il risveglio non rimuove il blocco schermo e non modifica la configurazione chiosco.

## Build

Il progetto usa Android Gradle Plugin 9.3, Gradle 9.5, JDK 17, `compileSdk 36` e CameraX 1.6.1.

```powershell
./gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Per distribuire aggiornamenti installabili sopra una versione esistente, tutte le release devono essere firmate con la stessa chiave privata. La chiave non deve essere aggiunta al repository: resta in `.signing/` (esclusa da git), da conservare anche in una copia di sicurezza fuori dal PC.

## Pubblicare una release

Le release sono compilate e firmate da GitHub Actions ([`release.yml`](.github/workflows/release.yml)).

1. Solo la prima volta, o quando cambia la chiave, caricare la chiave nei segreti del repository:

   ```powershell
   powershell -ExecutionPolicy Bypass -File tools\set-signing-secrets.ps1
   ```

2. Aggiornare `versionCode` e `versionName` in `app/build.gradle.kts` e aggiungere la sezione della versione in `CHANGELOG.md`.
3. Inviare su `main` e creare il tag della versione, per esempio `git tag v1.1.2` seguito da `git push origin v1.1.2`.
4. Il workflow esegue test e lint, firma l’APK, ne verifica la firma, calcola lo SHA-256 e pubblica la release con le note prese dal `CHANGELOG.md`.
5. Aggiornare `version.json` con il blocco riportato nel riepilogo del workflow: da quel momento i tablet ricevono l’aggiornamento.

## Diritti

Copyright © 2026 Bruno Leonardi — tutti i diritti riservati.

Contatto: [b.leonardi78@gmail.com](mailto:b.leonardi78@gmail.com)
