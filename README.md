# 💸 Budgetly — Smart Student Budget Tracker

> **PROG7313 · Portfolio of Evidence (Final) · Group ""**
> A modern, offline-first Android budgeting app built in Kotlin with a Cloud Firestore backend, analytical charting, dressed in a premium **Indigo & Rose** theme.

<p>
  <img alt="Platform" src="https://img.shields.io/badge/Platform-Android%2024%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Language" src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Database" src="https://img.shields.io/badge/Cloud-Firestore-FFCA28?logo=firebase&logoColor=black">
  <img alt="CI" src="https://img.shields.io/badge/CI-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white">
  <img alt="Theme" src="https://img.shields.io/badge/Theme-Indigo%20%26%20Rose-6366F1">
</p>

---

## 📌 Demonstration & Downloads

| Resource | Link |
| --- | --- |
| 🎬 **YouTube Demo (unlisted)** | `🔗 PLACEHOLDER — paste your unlisted YouTube link here` |
| 📦 **Production APK** | `🔗 PLACEHOLDER — app/build/outputs/apk/release/app-release-unsigned.apk` (also published as a build artifact on every CI run — see the **Actions** tab) |
| 💾 **Repository** | https://github.com/karabomoleko278-cpu/Budget-App |

---

## 1. Overview

Budgetly helps students take control of their money: register securely, capture income and
expenses (with optional **receipt photos**), set **minimum and maximum monthly spending goals**, and
instantly see — through charts and a colour-coded gauge — whether you are staying inside your budget
envelope. It is engineered to stay usable with **no connection** (local-first) while keeping data
backed up and portable in the cloud.

### Highlights mapped to the grading rubric

| # | Requirement | How Budgetly delivers it |
| --- | --- | --- |
| 1 | **Online database** | Cloud Firestore mirror of every entry/category/goal, with **offline persistence** so writes queue and flush automatically. |
| 2 | **Analytical graphing** | MPAndroidChart **bar chart** of *amount spent per category* over **Day / Week / Month**, with **Min & Max goal lines** on the same axis. |
| 3 | **Visual goal indicator** | Custom-drawn **circular gauge** (Canvas) that turns **Green / Amber / Red** based on where this month's spend sits between the Min and Max thresholds. |
| 4 | **UI/UX & branding** | Premium **Indigo & Rose** palette, 8dp spacing scale, typography scale, 48dp touch targets, full dark-mode parity. |
| 5 | **Stability** | DB / chart / network code wrapped in `try/catch` + `CoroutineExceptionHandler` with structured `Log.d`/`Log.e`. |
| 6 | **CI/CD** | `.github/workflows/build.yml` runs unit tests + builds the APK on every push/PR to `main`. |
| 7 | **Documentation** | This README. |

---

## 2. Architecture

Budgetly follows an **offline-first, layered (MVVM-influenced) architecture**.

```
┌──────────────────────────────────────────────────────────────┐
│                  UI LAYER (Activities + custom View)          │
│  Login · Register · Main(Dashboard) · AddEntry · Reports ·    │
│  GoalSettings · EntryList · Category · GoalGaugeView (Canvas) │
└───────────────▲───────────────────────────────▲──────────────┘
                │ observes Flow / LiveData       │ user actions
┌───────────────┴───────────────────────────────┴──────────────┐
│                  DOMAIN LAYER (pure, unit-tested)             │
│  GoalStatusCalculator · PeriodRange · CsvBuilder             │
└───────────────▲───────────────────────────────▲──────────────┘
┌───────────────┴───────────────────────────────┴──────────────┐
│                          DATA LAYER                           │
│  Room (single source of truth, offline cache)                 │
│  FirestoreSyncManager  ──►  Cloud Firestore (mirror)          │
│  SessionManager (biometric flag, last user, Min/Max goals)    │
└──────────────────────────────────────────────────────────────┘
```

**Key patterns:** Single Source of Truth (UI only observes Room; the network never blocks the UI),
repository/manager objects for infrastructure, pure domain functions for testable logic, and
ViewBinding throughout.

| Concern | Choice |
| --- | --- |
| Language / UI | Kotlin · Android Views · Material 3 |
| Local DB | Room (KSP) |
| Cloud DB | Cloud Firestore (+ offline persistence) |
| Charts | MPAndroidChart + custom `Canvas` gauge |
| Security | AndroidX Biometric |
| Async | Coroutines + `Flow` |
| Build | AGP 8.13 · Gradle 9.4 · JDK 17 |

---

## 3. Firebase Sync Strategy (Deep-Dive)

Budgetly uses an **offline-first mirror** rather than reading the UI directly from the cloud.

- **Write path** — each local insert (entry / category / per-category budget) is written to Room
  first, then mirrored to Firestore by `FirestoreSyncManager.pushXxx(...)` as a *fire-and-forget*
  call keyed by the Room id (`users/{userId}/entries/{entryId}`). While offline the Firestore SDK
  queues the write and flushes it automatically on reconnect.
- **Read path** — `MainActivity.syncFromCloud()` calls `pullAll(...)` on a background dispatcher and
  upserts cloud documents into Room; the dashboard, charts and gauge update reactively the instant a
  row lands.
- **Backup on login** — `LoginActivity` calls `backupAll(...)` to upload anything captured offline.
- **Offline persistence** is enabled once in `BudgetlyApp` via `PersistentCacheSettings`.

> 🔒 Passwords are **never** written to the cloud — only the username. Firebase init is wrapped in
> `try/catch`, so a missing/template `google-services.json` degrades gracefully to **local-only mode**.

---

## 4. Analytical Graphing & Goal Gauge (Deep-Dive)

**Reports** renders *amount spent per category* for a **Day / Week / Month** period
(`MaterialButtonToggleGroup`). `PeriodRange.startOf(period)` (pure, unit-tested) computes the window;
expenses are grouped into a `BarChart`; the user's **Minimum (green)** and **Maximum (red)** monthly
goals are drawn as dashed `LimitLine`s on the same axis. A pie chart shows the category split.

**`GoalGaugeView`** is a custom `Canvas` view that sweeps a 270° arc proportional to `spent / max` and
colours itself by zone via the pure `GoalStatusCalculator`:

## 5. UI/UX & Branding

- **Indigo & Rose** design system: Indigo `#6366F1` primary, Rose `#F43F5E` accent, Emerald success,
  Amber caution — defined in `colors.xml` with a full dark-mode override in `values-night/colors.xml`.
- **8dp spacing scale** and a **typography scale** (`dimens.xml` + `styles_vault.xml`).
- **Accessibility:** every interactive control uses the **48dp** minimum touch target.
- Consolidated **Goals & Settings** hub (goals, category budgets, security and export in one place)

---

## 6. Stability & 8. Continuous Integration

All database, chart-rendering and network calls are wrapped in `try/catch` and/or
`CoroutineExceptionHandler` with structured logging, so failures are logged, never fatal.

`.github/workflows/build.yml` runs on every **push / pull request to `main`**: sets up **JDK 17** and
the **Android SDK**, runs **unit tests** (`testDebugUnitTest`), **clean-builds** the debug & release
APKs, and uploads them as artifacts.

---

## 7. Build & Run

1. Open the project in **Android Studio** (JDK 17).
2. **Firebase:** a template `app/google-services.json` is committed so the project builds out of the
   box and runs in **local-only mode**. To enable live cloud sync, register an Android app with
   package **`com.iie.vaultquest`** in a Firestore project and replace the template file.
3. Run on a device/emulator (API 24+). Tests: `./gradlew testDebugUnitTest`.

> *Note:* the internal package id remains `com.iie.vaultquest` (changing it is unrelated to the app's
> display name and avoids a data-wipe); the app is branded **Budgetly** everywhere a user can see.

---


