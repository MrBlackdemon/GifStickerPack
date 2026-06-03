# GIF Sticker Pack per WhatsApp 📱

App Android in **Kotlin** che converte le tue GIF in uno Sticker Pack installabile su WhatsApp.

---

## 📋 Requisiti

- Android Studio Hedgehog (2023.1.1) o successivo
- Android SDK 34
- JDK 17
- Dispositivo/emulatore Android 5.0+
- WhatsApp installato sul dispositivo

---

## 🚀 Come aprire il progetto

1. Apri **Android Studio**
2. `File → Open` → seleziona la cartella `GifStickerPack`
3. Attendi il sync di Gradle (scaricherà le dipendenze)
4. Aggiungi l'icona launcher (vedi sotto)
5. Premi **Run ▶**

---

## 🖼️ Icona launcher (OBBLIGATORIA prima del build)

Devi aggiungere un'immagine `ic_launcher.png` in:
- `app/src/main/res/mipmap-hdpi/ic_launcher.png` (72x72 px)
- `app/src/main/res/mipmap-hdpi/ic_launcher_round.png` (72x72 px)

In Android Studio: click destro su `res → New → Image Asset` e genera l'icona automaticamente.

---

## 📦 Dipendenze usate

| Libreria | Scopo |
|---|---|
| `android-gif-drawable` | Decodifica e preview GIF |
| `Glide` | Caricamento GIF nella RecyclerView |
| `Material Components` | UI moderna stile WhatsApp |
| `Coroutines` | Operazioni async (conversione) |
| `Gson` | Serializzazione pack in JSON |

---

## ⚙️ Come funziona

```
[Seleziona Cartella] 
       ↓
[Scansiona GIF] → mostra griglia preview
       ↓
[Seleziona GIF da includere]
       ↓
[Inserisci nome pack e autore]
       ↓
[Conversione GIF → WebP] (async, con progress)
       ↓
[StickerPackManager salva in files/sticker_packs/]
       ↓
[WhatsAppSender invia Intent ad WhatsApp]
       ↓
[WhatsApp chiede conferma all'utente]
       ↓
✅ Sticker Pack installato!
```

---

## 📐 Vincoli WhatsApp Sticker

| Regola | Valore |
|---|---|
| Sticker per pack | min 3, max 30 |
| Dimensione massima sticker | 500 KB |
| Formato | WebP (animato o statico) |
| Risoluzione sticker | 512x512 px |
| Tray image (icona pack) | 96x96 px WebP |

---

## 🔌 Architettura

```
MainActivity
├── GifAdapter          — RecyclerView con preview GIF
├── GifConverter        — Converte GIF → WebP
├── StickerPackManager  — Salva/legge pack su disco
├── StickerContentProvider — Espone i file a WhatsApp
└── WhatsAppSender      — Invia l'Intent di installazione
```

---

## ⚠️ Note importanti

1. **ContentProvider**: WhatsApp legge i file sticker tramite il `StickerContentProvider` — deve avere authority `<packageId>.stickercontentprovider`

2. **Animated sticker**: WhatsApp supporta sticker animati (WebP animato) dall'app v2.19.71+. Su dispositivi Android < 9 viene usata una conversione GIF→WebP statico come fallback.

3. **Permessi**: Su Android 13+ vengono richiesti `READ_MEDIA_IMAGES` e `READ_MEDIA_VIDEO`. Su versioni precedenti `READ_EXTERNAL_STORAGE`.

4. **Dimensioni**: Se una GIF supera 500KB dopo la conversione, viene loggato un warning. WhatsApp rifiuterà sticker troppo grandi.

---

## 🔧 Personalizzazioni possibili

- Aggiungere emoji personalizzate per ogni sticker
- Supporto multi-pack (lista dei pack creati)
- Compressione aggressiva per GIF grandi
- Supporto WebP animato nativo (Android 9+) con `ImageDecoder`
