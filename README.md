# KubeJS IC2Classic

A **KubeJS addon for IC2Classic** (Minecraft 1.19.2 / Forge) that puts the whole IC2 recipe
system under script control — machine recipes, special registries and workbench crafting — with
working JEI integration.

- **modId:** `kubejs_ic2classic`
- **Minecraft:** 1.19.2 (Forge 43.x)
- **Requires:** IC2Classic + KubeJS
- **License:** MIT

---

## Why this mod exists

IC2Classic keeps its recipes in its own registries (`RecipeRegistry` / `IAdvancedCraftingManager`),
**not** in the vanilla `RecipeManager`. For modpack authors that means:

1. KubeJS `event.recipes.*` cannot see or edit **any** IC2 machine recipe — macerator, compressor,
   electrolyzer, recycling pool, fusion fuels, UU-matter values, refinery and so on. There is no way
   to add or remove them from scripts.
2. IC2 workbench crafts are *count-aware*: a single crafting slot may require a whole stack (`IInput`
   with a real count). Vanilla KubeJS `event.shaped` collapses every ingredient count to 1, so such
   crafts cannot be authored at all — and JEI's transfer button ("+") only moves one item per slot
   even when the recipe needs more.

This addon solves both: everything IC2 can register becomes scriptable, and count-aware crafts are
authored with honest per-slot counts that JEI displays and transfers correctly.

---

## What it does

### 1. Machine recipes — full `add` / `remove` / `replace`

`macerator`, `extractor`, `compressor`, `sawmill`, `recycler`, `massFabricator`, `mixingFurnace`
(exactly 2 inputs) and `molecularTransformer` (when *Advanced Solars* is installed — soft
dependency, guarded at runtime).

Each machine exposes:

```js
event.macerator.add(output, input)                       // plain recipe
event.macerator.add('my:id', output, input)              // with explicit recipe id
event.macerator.addXp(output, xp, input)                 // with experience
event.macerator.addChance(output, chance, input)         // probabilistic output (0.0–1.0)
event.macerator.addRange(output, min, max, input)        // random output count
event.macerator.remove('ic2:macerator/iron_ore')         // by exact id
event.macerator.removeByOutput('minecraft:sand')         // by output item
event.macerator.removeByInput('minecraft:cobblestone')   // by input item
```

Replacing a recipe = `removeByInput` / `removeByOutput` / `remove` + `add` in the same event.

### 2. Special IC2 registries

`electrolyzer` (charge / discharge / dual), `scrapbox` (weighted drops), `fusion` (fusion fuels),
`uuMatter` (UU-matter values), `canner` (fuel values, multipliers, fillables), `fluidFuels` (liquid
generator fuels), `refining` (refinery recipes through a fluent builder: two fluids + optional
catalyst → fluid and/or item), `rareEarth` (centrifuge outputs / weighted inputs) and
`potionBrewing` (IC2 potion brewing).

```js
event.electrolyzer.addCharge('minecraft:apple', 'minecraft:golden_apple', 12000)
event.scrapbox.addDrop('minecraft:diamond', 1000)
event.fluidFuels.addFuel('minecraft:lava', 1000, 4)

event.refining.add('kubejs:water_to_lava')
    .firstFluid('minecraft:water', 1000)
    .catalyst('minecraft:redstone')
    .fluidOutput('minecraft:lava', 100)
    .build()
```

### 3. Workbench crafting with real per-slot counts

```js
// Each 'A' slot really requires 2 advanced alloy ingots -> yields 4 plates.
event.crafting.shaped('kubejs:plate_grid', Item.of('ic2:plate_advanced_alloy', 4),
    ['AAA', 'AAA', 'AAA'],
    { A: Item.of('ic2:ingot_advanced_alloy', 2) })

event.crafting.shapeless(Item.of('minecraft:diamond', 2), ['minecraft:diamond_block'])
```

Shaped, shapeless, smelting (furnace / blast furnace / smoker / campfire), stonecutter and
smithing recipes can be added — and all of them removed by id.

### 4. Global editing of already registered recipes

```js
// Swap an item/fluid everywhere it appears (including IC2's own default recipes):
// macerator, extractor, compressor, sawmill, massFabricator, mixingFurnace, recycler,
// refining and fluidFuels. Everything else on the recipe (other inputs, output type,
// range, XP, NBT) is preserved.
event.substitute('ic2:bio_fuel', 'industrialforegoing:biofuel')

// Retune the random output spread of an existing recipe, without recreating it:
event.macerator.setRange('ic2:macerator/some_recipe', 2, 5)
event.refining.setFluidRange('kubejs:refining/my_recipe', 200, 800)
```

### 5. JEI fixes

Count-aware crafts stay in the vanilla *Crafting* category, but JEI now shows the real **×N** in
the ingredient slots, and the transfer button fills the grid with **N per slot** instead of one.
Non-IC2 vanilla recipes are untouched and transfer exactly as before.

### 6. Reload-safe by design

- **Deferred apply** — scripts only *record* operations; they are applied from an IC2 listener
  *after* IC2 loads its datapack recipes, so a KubeJS reload never wipes them.
- **Idempotent** — every `add` removes its own id first, so `/reload` does not duplicate entries.
- **Fault isolation** — a single invalid recipe is rejected and logged
  (`a recipe op was rejected by IC2 and skipped`), the rest still apply and the server stays up.

---

## Installation

Put these in `mods/` — the addon is required on **both** client and server:

| Mod | Version |
|---|---|
| Forge | 1.19.2 (43.x) |
| IC2Classic | `1.19.2-2.1.3.3` |
| CarbonConfig | `1.19.2-2.0.0` (hard dependency of IC2) |
| KubeJS | `1902.6.2` |
| Rhino | `1902.2.3` |
| Architectury API | `6.6.92` |
| **kubejs_ic2classic** | this mod |
| JEI | `11.8.1.1034` — optional, **client only** |

Scripts go into `kubejs/server_scripts/`; they are applied on world load and on every `/reload`.

---

## Quick start

```js
// kubejs/server_scripts/my_ic2.js
IC2Events.recipes(event => {
    // Macerate cobblestone into gravel
    event.macerator.add('minecraft:gravel', 'minecraft:cobblestone')

    // Count-aware craft: 2 advanced alloy ingots per slot -> 4 plates
    event.crafting.shaped('kubejs:plate_grid', Item.of('ic2:plate_advanced_alloy', 4),
        ['AAA', 'AAA', 'AAA'],
        { A: Item.of('ic2:ingot_advanced_alloy', 2) })

    // 25% chance output
    event.compressor.addChance('minecraft:flint', 0.25, 'minecraft:gravel')
})
```

Everything is authored inside `IC2Events.recipes(event => { … })` — there are no separate globals,
which is exactly what makes recipes re-apply correctly on `/reload`.

**Input formats:** `'minecraft:diamond'` = 1 item · `Item.of('minecraft:diamond', 4)` = 4 items in
one slot · `'#forge:ores/gold'` = tag · `'4x minecraft:redstone'` = 4 items · `Fluid.of('minecraft:lava', 500)`
or `'minecraft:lava'` (= 1000 mB) for fluids. NBT is supported via `Item.of(...).withNBT(...)`.
A recipe id may be passed as an optional **first** argument; it is required for a targeted
`remove(...)`.

> ⚠️ Workbench recipe ids must live in a **loaded mod namespace** (`kubejs:` or your own mod id) —
> IC2 silently drops crafts with unknown namespaces. Machine recipes are unaffected.

---

## Known limitations

- **IC2 electric furnace / blast furnace / smoker are read-only.** They mirror vanilla cooking
  recipes, so there is no `event.furnace`; author them with
  `event.crafting.smelting(out, in, 'furnace' | 'blast_furnace' | 'smoker')` and IC2 picks them up
  automatically.
- `substitute()` covers the machine and fuel registries only; registries with their own recipe shape
  (electrolyzer, scrapbox, fusion, uuMatter, canner, rareEarth, potionBrewing, crafting) are edited
  through their own methods. Tag-based ingredients are never rewritten.
- Machines, wires, blocks and items are out of scope — this is a recipe/scripting addon.

---

## Building from source

Requires **JDK 17** (Forge 1.19.2 / ForgeGradle 6 do not run on newer JDKs).

```bash
# IC2Classic is not published to any Maven repo, so place it in libs/ first:
#   libs/IC2Classic-1.19.2-2.1.3.3.jar
# Optional soft dependency: libs/advancedsolars-2.1.2.jar
# Everything else (KubeJS, Rhino, Architectury, JEI, CarbonConfig) resolves from Maven.

JAVA_HOME="/path/to/jdk-17" ./gradlew build      # -> build/libs/kubejs_ic2classic-1.19.2-<version>.jar
JAVA_HOME="/path/to/jdk-17" ./gradlew runClient  # dev launch
```

`run/` and `build/` are git-ignored, and `libs/*.jar` are not redistributed with the repository.

---

## Documentation

- [`INSTRUCTIONS.md`](INSTRUCTIONS.md) — full user guide (Russian): every method, format and
  pitfall, plus a troubleshooting table.
- [`examples/server_scripts/`](examples/server_scripts) — example script covering the whole API.
- [`examples/tests/`](examples/tests) — per-machine / per-registry test sets.
- [`CLAUDE.md`](CLAUDE.md) — implementation notes for contributors.

---

## License

MIT — see [LICENSE](LICENSE).
