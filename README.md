# Starry Nights

Private Android trial for a fictional-adult social-simulation game. It uses a Kotlin/Jetpack Compose APK, an Express/TypeScript API, and PostgreSQL (Neon-compatible). The APK never receives a database URL or database credentials.

## Playable trial flow

1. Adults-only consent is saved through the API.
2. Create a fictional adult (enforced age 18+) or select a demo adult.
3. Start **Blind Date**, select one of nine locations, and meet them without knowing all their traits.
4. Use timing-based actions such as eye contact, smile, approach, tease, flirt, compliment, step back, and end date.
5. Observe reactions, discover traits, and develop Heat, attraction, trust, comfort, curiosity, and tension.
6. When all compatibility conditions pass, the player may explicitly choose **Continue Private Moment**.
7. The optional private route is abstract and non-explicit: compatible style cards plus Continue, Slow Down, Change Direction, and Stop Scene controls. It never activates automatically.

The first build deliberately uses silhouettes/icons and cinematic text rather than explicit imagery or real-world sexual instructions.

## Structure

- `android-app/` — Kotlin, Jetpack Compose, MVVM, Navigation Compose, StateFlow, Retrofit, kotlinx.serialization.
- `backend/` — Express, TypeScript, Zod, pg, dotenv. All database access is parameterized.
- `database/` — Neon/PostgreSQL schema and content seed.

## Current content

- Blind Date is the major playable scenario, with nine starting locations and three demo fictional adults.
- Four seeded scenarios: Blind Date, Midnight Circle, Masquerade Signals, and After Dark Lounge.
- 28 traits, 20 actions, 50 dialogue templates, 10 director cards, and 8 abstract intimacy-style cards.
- Directional relationship schema, append-only event history, snapshots, and cloud-save endpoints.

See [README_SETUP.md](README_SETUP.md) for setup, Neon inspection, emulator/phone networking, and APK production.

