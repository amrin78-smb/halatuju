# Changelog

## 0.1.0-m1 — Milestone 1 (2026-11-05)
* Multi-module project: `:core` (pure Kotlin engine), `:app` (simulator), `:diagnostic` (inspector).
* Mock location via LocationManager test providers (gps + network), plus optional Play services FLP mock mode.
* Deterministic great-circle walking engine: presets 2/4/5/6 km/h, custom 1–8 km/h, intervals 500 ms/1/2/5 s.
* Foreground service (type `location`) with Pause/Resume/Stop notification actions; no auto-restart.
* Compose Material 3 dark UI with location, route, control, statistics and diagnostics sections.
* DataStore persistence of settings, saved routes and last-run summary; delete-all option.
* GPS Location Inspector: independent FLP + LocationManager receiver that reports Android's mock flag.
