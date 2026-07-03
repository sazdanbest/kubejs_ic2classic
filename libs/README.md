# libs/

Local binary dependencies used by `build.gradle`. Git-ignored (large / not ours to
redistribute).

**Since Phase 2 (2026-07-03) almost everything comes from Maven** — the only jar you must
place here is IC2Classic (it is not published to any Maven). The reference jars can stay for
convenience but are **not** on the build classpath anymore.

| File | modId | Role |
|---|---|---|
| `IC2Classic-1.19.2-2.1.3.3.jar` | `ic2` | **REQUIRED locally.** Resolved via the `flatDir { dir 'libs' }` repo using *module notation* `fg.deobf("ic2:IC2Classic:1.19.2-2.1.3.3")` (NOT `files(...)` — that would skip deobf). Loaded at both compile and runtime. |
| `kubejs-thermal-1902.1.9-build.16.jar` | — | Reference only (plugin structure). Not on the build classpath. |
| `kubejs-forge-*.jar`, `rhino-forge-*.jar`, `jei-*.jar` | — | Legacy local copies; **no longer used** — now pulled from Maven (see below). Safe to delete. |

## Maven-sourced deps (see `build.gradle` / `gradle.properties`)

| modId | Coordinate | Repo |
|---|---|---|
| `kubejs` / `rhino` | `dev.latvian.mods:kubejs-forge` / `rhino-forge` | `maven.saps.dev/releases` |
| `architectury` | `dev.architectury:architectury-forge:6.6.92` | `maven.architectury.dev` |
| `jei` | `mezz.jei:jei-1.19.2-forge:11.8.1.1034` | `maven.blamejared.com` |
| `carbonconfig` | `curse.maven:carbon-config-898104:7842759` (v1.19.2-2.0.0) | `cursemaven.com` — **mandatory dep of IC2** |

## Running

Dev test = **`./gradlew runClient`** (JDK 17). Mods load from the classpath via `fg.deobf`, so
**`run/mods/` is not needed**. Note: `runServer` (dedicated) does not boot in the dev userdev
environment (IC2/KubeJS dist-specific crashes at CONSTRUCT — not our bug); test a dedicated
server later with a real production jar set.
