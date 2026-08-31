# JARVIS — offline personal AI for Android

Ek personal assistant jo **poori tarah aapke phone ke andar** chalta hai.
Koi API key nahi, koi cloud nahi, koi subscription nahi, koi rate limit nahi.
Internet band ho tab bhi kaam karta hai.

> **Status:** Phase 0 — app build hoti hai aur chalti hai. Dimaag aur awaaz aage aa rahe hain.

---

## Yeh kya karega

| | |
|---|---|
| 🧠 **Dimaag** | On-device LLM (llama.cpp + GGUF). Model phone ke andar, offline. |
| 🎙️ **Kaan** | "Jarvis" wake word + offline speech-to-text (Vosk). |
| 🗣️ **Zubaan** | Android ka built-in TTS — offline bolta hai. |
| ✋ **Haath** | Call, SMS, koi bhi app kholna, torch, volume, alarm, battery. |
| 👁️ **Aankhein** | Location, calendar, notifications padhna. |

Baat-cheet **Hinglish** mein — jaise aap normally baat karte hain.

---

## Kis phone ke liye bana hai

Primary target: **realme 15 (RMX5106)** — Dimensity 7300+, 12 GB RAM, Android 16.

Chalega kisi bhi phone par jo:
- **Android 8.0+** (API 26), **arm64** ho
- **6 GB+ RAM** (8 GB+ behtar — bade model ke liye)
- **~3 GB free storage** model file ke liye

App khud RAM detect karke bata degi ke aapka phone kis tier mein hai.

---

## APK kaise milegi

Play Store par yeh app nahi jayegi (Google `SEND_SMS` / `CALL_PHONE` jaisi permissions
aise apps ko allow nahi karta). Sideload karna hoga — personal use ke liye bilkul theek hai.

1. Is repo ke **Actions** tab par jayein
2. Sabse upar wali successful **Build APK** run kholein
3. Neeche **Artifacts** se `REMOVED-OLD-KEYSTORE-PASSWORD-apk` download karein
4. ZIP se APK nikaal kar phone mein install karein
   *(Settings mein "Install unknown apps" allow karna padega)*

---

## Khud build karna ho to

```bash
git clone --recurse-submodules https://github.com/Mubashirsafeer7/jarvis-android
cd jarvis-android
./gradlew assembleDebug
```

APK yahan milegi: `app/build/outputs/apk/debug/`

Chahiye: JDK 17, Android SDK (compileSdk 36), aur Phase 1 ke baad NDK r27+.

---

## Roadmap

- [x] **Phase 0** — project scaffold, CI se APK build, device detect screen
- [ ] **Phase 1** — llama.cpp + GGUF model, offline text chat
- [ ] **Phase 2** — Vosk STT + TTS + "Jarvis" wake word + background service
- [ ] **Phase 3** — call / SMS / app kholna / device control
- [ ] **Phase 4** — location, calendar, notifications
- [ ] **Phase 5** — settings, polish

---

## Do baatein jo pehle jaan lein

**realme / ColorOS background ko maar deta hai.** Wake-word service zinda rehne ke liye app ko
manually whitelist karna padega — Settings → Battery → Allow background activity, aur
Auto-launch on. App onboarding mein aapko seedha wahan le jayegi. Yeh sabse aam wajah hoti hai
ke aisi app "kaam karna band kar deti hai".

**Model file repo mein nahi hai.** GGUF files 1–5 GB ki hoti hain. App pehli baar chalne par
download karegi (ek baar internet chahiye), ya aap khud koi bhi GGUF import kar sakte hain.

---

## License

Personal project. Apni marzi se use karein.
