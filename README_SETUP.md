# Starry Nights setup

## Required dependencies

- JDK 21. This computer already has it at `C:\Users\darsh\.jdks\temurin-21\jdk-21.0.12+8`; use that path as `JAVA_HOME` for this project.
- Android SDK Platform 35, Build Tools 35.0.0, and Android command-line tools. They are installed project-locally at `F:\StarryNights\.android-sdk`.
- Node.js 20+ and npm. Node 24/npm 11 are already available.
- A Neon PostgreSQL project and its `DATABASE_URL` are required for consent, character, event, and snapshot persistence.

## 1. Create the Neon database

1. Create a Neon project and copy its pooled connection string.
2. Copy `.env.example` to `backend/.env` and set `DATABASE_URL`. Do not commit this file.
3. From `F:\StarryNights\backend`, run `npm run db:setup`. It applies [`database/schema.sql`](database/schema.sql) and [`database/seed.sql`](database/seed.sql) safely with idempotent statements. You may instead run those files in the Neon SQL Editor.

Example `backend/.env`:

```dotenv
DATABASE_URL=postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require
PORT=3000
ALLOWED_ORIGIN=http://localhost
```

## 2. Start the backend

```powershell
Set-Location F:\StarryNights\backend
npm install
npm run build
npm run dev
```

Check `http://localhost:3000/health`. It must return `{"status":"ok"}` before using cloud save.

## 3. Android SDK and build

The configured project-local SDK is `F:\StarryNights\.android-sdk`. If rebuilding on another machine, install Android command-line tools and then:

```powershell
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

Create `F:\StarryNights\local.properties`:

```properties
sdk.dir=F\:\\StarryNights\\.android-sdk
```

Set the terminal JDK only for the current PowerShell session:

```powershell
$env:JAVA_HOME = 'C:\Users\darsh\.jdks\temurin-21\jdk-21.0.12+8'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

With the Gradle wrapper installed, build the debug APK:

```powershell
Set-Location F:\StarryNights
.\gradlew.bat :android-app:testDebugUnitTest
.\gradlew.bat :android-app:assembleDebug
```

The APK output will be `F:\StarryNights\android-app\build\outputs\apk\debug\android-app-debug.apk`.

## 4. API address

The debug build uses `http://10.0.2.2:3000/`, which maps the Android emulator to the computer running the API.

For a physical phone, change the debug `API_BASE_URL` field in [`android-app/build.gradle.kts`](android-app/build.gradle.kts) to your computer's LAN HTTPS URL, rebuild, and ensure the phone and computer are on the same trusted network. Do not expose a development HTTP API publicly.

## 5. Install the APK

```powershell
adb install -r F:\StarryNights\android-app\build\outputs\apk\debug\android-app-debug.apk
```

Alternatively, transfer the APK to a trusted test device and install it after enabling installation from that file source.

## 6. Inspect the stored trial data in Neon

Open the Neon SQL Editor and run, for example:

```sql
SELECT * FROM user_consents ORDER BY consented_at DESC;
SELECT id, name, age, personality, hidden_traits FROM characters ORDER BY updated_at DESC;
SELECT id, status, current_state, updated_at FROM game_sessions ORDER BY updated_at DESC;
SELECT event_type, payload, created_at FROM game_events ORDER BY created_at DESC;
SELECT * FROM relationship_states ORDER BY updated_at DESC;
SELECT * FROM session_snapshots ORDER BY created_at DESC;
```

## Privacy and trial limits

- The app sends a hash of Android's installation identifier to bootstrap an anonymous private-trial user; the raw identifier is not stored by the API.
- Sensitive game data is stored only through the API/Neon, not in Room, SharedPreferences, DataStore, SQLite, or Android files.
- Consent and age confirmation are server-stored before a cloud game session can begin.
- Treat this as a private trial, not a production-authentication or privacy-policy implementation.

## Vercel deployment readiness

The backend is structured for a Vercel serverless deployment: authoritative state lives in Neon, `DATABASE_URL`, `PORT`, and `ALLOWED_ORIGIN` are environment configuration, and `backend/api/index.ts` exports the Express application without opening a local listener on Vercel. [`backend/vercel.json`](backend/vercel.json) rewrites requests to that handler.

To deploy later, select `F:\StarryNights\backend` as the Vercel project root and configure at least `DATABASE_URL` and a restrictive production `ALLOWED_ORIGIN`. Use the deployed HTTPS URL as the Android release `API_BASE_URL`. No Vercel deployment has been performed by this project setup.

