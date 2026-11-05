# libs/

Local binary dependencies used by `build.gradle`. Git-ignored (large / not ours to redistribute).

Only two jars belong here — both are resolved through the `flatDir { dir 'libs' }` repo using
**module notation** (not `files(...)`, which would skip `fg.deobf`):

| File | modId | Role |
|---|---|---|
| `IC2Classic-1.19.2-2.1.3.3.jar` | `ic2` | **REQUIRED.** Not on any Maven. `fg.deobf("ic2:IC2Classic:1.19.2-2.1.3.3")`, on both compile and runtime classpath. |
| `advancedsolars-2.1.2.jar` | `advanced_solars` | **Optional soft dep.** `fg.deobf("trinsdar:advancedsolars:2.1.2")`, `compileOnly` + `runtimeOnly`. Lets `event.molecularTransformer` reference `AdvancedSolarsRecipes` at compile time; guarded by `ModList.isLoaded` + try/catch at runtime, so a pack without this addon is unaffected. |

## Removed legacy jars

`kubejs-forge-*.jar`, `rhino-forge-*.jar` and `jei-*.jar` are **no longer needed** — those mods are
pulled from Maven (see below). The `kubejs-thermal` reference jar (used only as a plugin-structure
example during development) was removed as well; it is not on the build classpath.

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

