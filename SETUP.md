# MLQ Solo for Android · One-Time Setup

About 30 minutes, once. After this the phone app keeps itself current: every MLQ Solo update you publish from the PC reaches the phones a minute later. A new app build is only needed if the app shell itself changes (rare).

Everything is in the **MLQ Solo Phone Kit** folder. Do the steps in order.

---

## 1 · Publish the new MLQ Solo on the PC

1. PC app → Settings → Updates → **Install & Publish Update** → pick the new **MLQ-Solo.zip**.
2. Leave MLQ Solo open on the PC. About a minute after it restarts, it copies the phone version of the app to the cloud (after step 2 is done).

## 2 · Supabase (the MLQ Solo project, `fxqtrzzlplnkoxcpanqc`)

Open supabase.com → the **MLQ Solo** project (not MLQ Work).

1. **SQL Editor** → New query → paste **1 Phone Storage.sql** → Run. It should say *MLQ Solo phone storage ready*.
2. **Edge Functions → Secrets** → add two secrets:
   - `CRON_SECRET`: the text in **cron-secret.txt**
   - `FCM_SERVICE_ACCOUNT`: the Firebase service-account JSON from step 3 (all of it). Add this one after step 3.
3. **Edge Functions → Deploy a new function → Via Editor**, twice:
   - Name **solo-push** → replace the code with **2 solo-push.ts** → Deploy. Then open its settings and turn **Verify JWT off**.
   - Name **solo-nudge** → replace the code with **3 solo-nudge.ts** → Deploy. Turn **Verify JWT off** too.
4. **SQL Editor** → New query → paste **4 Reminders Schedule.sql** → Run. This makes the cloud check every 10 minutes and send reminders (Telegram and phone) when his PC is off or asleep.

## 3 · Firebase (phone notifications)

You can reuse the **MLQ Work** Firebase project.

1. console.firebase.google.com → the MLQ Work project → **Project Overview → Add app → Android**:
   - Package name: `gg.maxlevel.mlqsolo` (exactly)
   - Nickname: MLQ Solo
   - Register app → **Download google-services.json** → keep it → Next to the end (skip the SDK steps).
2. Gear icon → **Project settings → Service accounts → Generate new private key**. A JSON file downloads. Treat it like a password.
   (If you still have the one you made for MLQ Work, that same file works.)
3. Back in Supabase (step 2.2): add the secret `FCM_SERVICE_ACCOUNT` with **all** of that JSON.

## 4 · GitHub (builds the app)

1. github.com/new → name `mlq-solo-android` → **Public** → Create.
   Public lets the phones download new versions without a GitHub login. Nothing secret is in the code: the key and the Firebase file go into hidden Secrets below.
2. On the new repo: **uploading an existing file** → unzip **mlq-solo-android.zip**, open the folder, select **everything inside it** (including the `.github` folder) and drag it in → **Commit changes**.
3. Check the repo shows a `.github` folder. If not: Add file → Create new file → name it `.github/workflows/android.yml` → paste that file's contents → Commit.
4. Repo → **Settings → Secrets and variables → Actions → New repository secret**, three times:

   | Name | Value |
   |---|---|
   | `SOLO_KEYSTORE_B64` | everything in `keystore-base64.txt` (in **MLQ Solo Android Signing Key.zip**) |
   | `SOLO_KEYSTORE_PASSWORD` | the text in `password.txt` (same zip) |
   | `GOOGLE_SERVICES_JSON` | everything in **google-services.json** (Firebase step 1) |

5. Repo → **Actions** → enable workflows if asked → **Build Android APK** → **Run workflow**. About 8–10 minutes. A green tick means done. A red cross: open it, copy the red error lines and send them to Claude.
6. Repo → **Releases** → the newest one has **MLQ-Solo.apk**.

Keep **MLQ Solo Android Signing Key.zip** somewhere private. Every future build must use it, or phones can't update the app.

## 5 · Tell MLQ Solo where the app lives

PC app → Settings → Updates → **Android App** → type `your-github-name/mlq-solo-android` → press Enter.
**Copy APK Link** gives a link that always points to the newest build.

## 6 · Install on each phone (Hemank's and yours)

1. Open the APK link on the phone → download → open the file → allow installs from the browser → **Install**.
2. Open **MLQ Solo**. The **Set Up MLQ Solo On This Phone** screen appears:
   - **Whose Phone Is This?** Hemank or Udbhav
   - **Sign In** with the MLQ Solo account (the email and password in the PC app's config.js)
   - **Notifications → Turn On**
3. **Xiaomi / Redmi / POCO / Realme / OnePlus / Vivo / Oppo:** long-press the MLQ Solo icon → App info → **Battery → No restrictions** (and **Autostart: On** where it exists). Without this, reminders can arrive late.
4. Optional: Android Settings → Apps → MLQ Solo → Notifications → **Urgent Reminders** → allow it to override Do Not Disturb if you want urgent ones to ring even then.

## 7 · Test

- Phone → Settings → This Phone → **Test Urgent Buzz**. Lock the phone first: it should ring like an alarm and keep ringing until you open it (or 10 minutes pass).
- Send a chat message from the PC while the phone app is closed: the phone gets it with a **Reply** button.
- On his PC, step away for 2+ minutes while a reminder is due: his phone buzzes.
- Turn his PC off: within 10–20 minutes reminders arrive on his phone and in Telegram (the cloud takes over).

## How notifications work

| Who | Gets | How it sounds |
|---|---|---|
| Hemank | Everything his PC would show, when he's been away from the PC 2+ minutes or the PC is off | Urgent (not started, overdue commissions): alarm sound, repeats until opened, can show over the lock screen. Reminders: strong buzz. Updates: normal |
| Hemank | Messages from Udbhav (unless MLQ Solo is open in front of him) | Message buzz, Reply button |
| Udbhav | Partner alerts (hasn't started, overdue, day won, deliveries, payments, day off, games played) | Reminder buzz; they still go to the family Telegram group too |
| Udbhav | Messages from Hemank | Message buzz, Reply button |

Buttons on notifications: **Start Timer** (start reminders), **Mark Done** (habit reminders), **Open**, **Reply**. Between the 1 AM wrap-up and the 7 AM start nothing nags; anything low-key arrives silently.

## Later: a new app build

Only when Claude says the app shell changed: upload the changed files to the repo (or re-upload the zip's contents). The build runs by itself and phones show **Update MLQ Solo** with a one-tap download.
