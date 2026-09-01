# JARVIS — offline personal AI for Android

Ek personal assistant jo **poori tarah aapke phone ke andar** chalta hai.
Koi API key nahi, koi cloud nahi, koi subscription nahi, koi rate limit nahi.
Internet band ho tab bhi kaam karta hai.

> **Status:** Phase 2a — offline LLM + awaaz. Bol kar poochein, sun kar jawab milta hai.
> Wake word ("Jarvis" bol kar jagana) abhi baaqi hai.

---

## Yeh kya karega

| | |
|---|---|
| 🧠 **Dimaag** | On-device LLM (llama.cpp + GGUF). Model phone ke andar, offline. |
| 🎙️ **Kaan** | Offline speech-to-text — Android ka on-device recognizer. |
| 🗣️ **Zubaan** | Android ka built-in TTS — offline bolta hai. |
| ✋ **Haath** | Call, SMS, koi bhi app kholna, torch, volume, alarm, battery. |
| 👁️ **Aankhein** | Location, calendar, notifications padhna. |

Baat-cheet **Hinglish** mein — jaise aap normally baat karte hain.

---

## Kis phone ke liye bana hai

Primary target: **realme 15 (RMX5106)** — Dimensity 7300+, 12 GB RAM, Android 16.

Chalega kisi bhi phone par jo:
- **Android 11+** (API 30), **arm64** ho
- **6 GB+ RAM** (8 GB+ behtar — bade model ke liye)
- **~3 GB free storage** model file ke liye

App khud RAM detect karke bata degi ke aapka phone kis tier mein hai.

---

## APK kaise milegi

Play Store par yeh app nahi jayegi (Google `SEND_SMS` / `CALL_PHONE` jaisi permissions
aise apps ko allow nahi karta). Sideload karna hoga — personal use ke liye bilkul theek hai.

1. Is repo ke **Actions** tab par jayein
2. Sabse upar wali successful **Build APK** run kholein
3. Neeche **Artifacts** se `jarvis-apk` download karein
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

Chahiye: JDK 17, Android SDK (compileSdk 36), NDK 29 (llama.cpp compile karne ke liye).
Pehli build mein llama.cpp compile hota hai — 15-25 minute lag sakte hain.

---

## Pehli baar chalane par

1. App kholein → model list dikhegi, aapke phone ki RAM ke hisaab se
2. **Get** dabayein — model download hoga (background mein, app band kar sakte hain)
3. Download ke baad model khud load ho jayega
4. Ab **airplane mode on karke** baat karein — proof ke sab kuch offline hai

**Model download na ho to?** Link badal sakte hain. Koi bhi GGUF file phone par download
karein (HuggingFace se) aur app mein **"GGUF file chunein"** se import kar lein — yeh rasta
hamesha kaam karta hai.

**Speed** button dabakar apne phone ka asli tokens/sec dekh sakte hain.

## App update karna

Ab har build **ek hi key** se sign hoti hai, to nayi APK seedha purani ke upar install ho jati
hai — uninstall karne ki zaroorat nahi, aur model bhi wahin rehta hai.

1. **Actions** → sabse upar wali green run → **Artifacts** → `jarvis-apk`
2. ZIP se APK nikaal kar install karein — bas.

### Model ka backup

Model app ke apne folder mein rehta hai, jise Android **uninstall par delete** kar deta hai (aur
Android 11+ par koi file manager us folder tak pahunch bhi nahi sakta). Isliye model ke saamne
**Save** button hai — woh usay `Downloads/Jarvis/` mein copy kar deta hai.

Kabhi app uninstall karni pade, to pehle **Save** dabayein; nayi install ke baad
**"GGUF file chunein"** se wahi file import kar lein — 2 GB dobara download nahi karna padega.

## Awaaz se baat karna

Chat screen par **🎤** dabayein → boliye → Jarvis sun kar jawab dega, aur bol kar bhi sunayega.

Bolte waqt poori screen par **arc reactor** aata hai. Woh nakli animation nahi — rings aapke
microphone ke asli level par khulti aur simatti hain, aur Jarvis ke sochne aur bolne par alag
harkat karti hain. Tap karke band kar sakte hain.

- Pehli baar microphone ki ijazat maangi jayegi
- **Awaaz on/off** button se jawab bolna band kar sakte hain
- Sab kuch phone ke andar — recognizer aur TTS dono offline chalte hain

Agar "recognizer ne internet maanga" jaisa message aaye, to phone ki speech
services mein offline language pack install nahi hai: Settings → System →
Languages & input → On-device speech recognition → English (India) add karein.

---

## Roadmap

- [x] **Phase 0** — project scaffold, CI se APK build, device detect screen
- [x] **Phase 1** — llama.cpp + GGUF model, offline text chat
- [x] **Phase 2a** — offline STT + TTS, mic button se baat
- [ ] **Phase 2b** — "Jarvis" wake word + background service
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
