# libs/

Local binary dependencies used by `build.gradle` (`fg.deobf(files('libs/...'))`).
These jars are **git-ignored** (large / not ours to redistribute). Populate this folder
before building:

| File | modId | Notes |
|---|---|---|
| `IC2Classic-1.19.2-2.1.3.3.jar` | `ic2` | `compileOnly` — only `ic2.api.*` is needed to compile; the real jar goes in `run/mods/` at runtime. |
| `kubejs-forge-1902.6.2-build.73.jar` | `kubejs` | `implementation`. |
| `rhino-forge-1902.2.3-build.284.jar` | `rhino` | `implementation` (KubeJS scripting engine). |
| `jei-1.19.2-forge-11.8.1.1034.jar` | `jei` | `compileOnly` + `runtimeOnly`. |
| `kubejs-thermal-1902.1.9-build.16.jar` | — | Reference only (plugin structure). Not on the build classpath. |
| **Architectury API `>= 6.5.82` (1.19.2 Forge)** | `architectury` | **NOT YET ADDED.** Required by KubeJS at runtime. Add the jar here and enable the `implementation files(...)` line in `build.gradle`, or resolve via CurseMaven. |

## run/mods/

For `runClient` / `runServer` with a real IC2, copy the actual (non-dev) jars into
`run/mods/`: IC2Classic + KubeJS + Rhino + Architectury + JEI.
