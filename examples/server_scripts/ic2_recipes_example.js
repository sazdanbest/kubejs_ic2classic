// IC2Classic × KubeJS — machine recipe examples (Phase 4).
//
// Put this in  kubejs/server_scripts/  . It runs on world load and on every /reload.
// `IC2Events` is provided by the kubejs_ic2classic addon.

IC2Events.recipes(event => {
    // 1) Simplest form — auto-generated id. Macerate 1 cobblestone into 1 gravel.
    event.macerator.add('minecraft:gravel', 'minecraft:cobblestone')

    // 2) Explicit id + count-aware output. `Item.of(id, n)` keeps the count IC2 needs.
    event.macerator.add('kubejs:gold_from_ore', Item.of('minecraft:gold_nugget', 8), '#forge:ores/gold')

    // 3) Chance output: 25% chance to get flint when compressing gravel.
    event.compressor.addChance('minecraft:flint', 0.25, 'minecraft:gravel')

    // 4) Range output: 2..4 sticks per plank in the sawmill.
    event.sawmill.addRange('minecraft:stick', 2, 4, 'minecraft:oak_planks')

    // 5) Count-aware input: this extractor recipe needs 4 redstone in one slot.
    event.extractor.add('minecraft:redstone_block', Item.of('minecraft:redstone', 4))

    // Removal helpers (also deferred; run against the freshly-loaded IC2 list):
    //   event.macerator.remove('ic2:macerator/iron_ore')   // by exact recipe id
    event.macerator.removeByOutput('minecraft:sand')        // by output item

    // 6) Recycler is a plain machine too (IRecyclerRecipeList extends IMachineRecipeList).
    event.recycler.addChance('minecraft:gunpowder', 0.125, 'minecraft:cobblestone')

    // --- Registries with bespoke APIs ---

    // Electrolyzer: input + energy(EU) -> output, in charge/discharge/dual mode.
    event.electrolyzer.addCharge('minecraft:apple', 'minecraft:golden_apple', 12000)

    // Scrap box loot pool: item + weight.
    event.scrapbox.addDrop('minecraft:diamond', 0.001)

    // Fusion fuel: item, fuelValue(EU), productionRate, consumed?
    event.fusion.addFuel('minecraft:blaze_rod', 40000, 1.0, true)

    // UU-Matter entry: item craftable from N UU.
    event.uuMatter.addEntry('minecraft:diamond', 166000)

    // Canner fuel value.
    event.canner.addFuel('minecraft:coal', 8)

    // --- Fluids ---

    // Fluid generator fuel: fluid, ticksPerBucket, euPerTick.
    event.fluidFuels.addFuel('minecraft:lava', 1000, 4)

    // Refinery: two fluid inputs (+ optional item catalyst) -> fluid/item output.
    event.refining.add('kubejs:water_to_lava')
        .firstFluid('minecraft:water', 1000)
        .catalyst('minecraft:redstone')
        .fluidOutput('minecraft:lava', 100)
        .itemOutput('minecraft:blaze_powder')
        .build()

    // Rare-earth centrifuge: output pool + weighted inputs.
    event.rareEarth.addOutput('minecraft:emerald')
    event.rareEarth.addInput(1.0, 'minecraft:prismarine_shard')

    // IC2 potion brewing: ingredient -> effect.
    event.potionBrewing.addBrew('minecraft:golden_carrot', 'minecraft:night_vision')

    // --- Count-aware workbench crafts (Phase 5) ---
    // These go through IC2's IInput, so counts in a slot are HONEST: unlike native
    // event.shaped, `Item.of(id, 4)` really demands 4 items in that slot.

    // Shaped: each 'P' slot honestly needs 4 Mixed Metal Ingots -> a row of Refined Iron.
    event.crafting.shaped('ic2:ingot_refined_iron', ['PPP'], {
        P: Item.of('ic2:ingot_advanced_alloy', 4),
    })

    // Shapeless: 2 diamonds from a diamond block (inputs as an array or as varargs).
    event.crafting.shapeless(Item.of('minecraft:diamond', 2), ['minecraft:diamond_block'])

    // Cooking: default furnace, or name blast_furnace / smoker / campfire; optional xp, time.
    event.crafting.smelting('ic2:ingot_refined_iron', 'minecraft:iron_ingot', 'blast_furnace')

    // Stonecutter and smithing (vanilla-style inputs; count is irrelevant for these).
    event.crafting.stonecutter('minecraft:stone_bricks', 'minecraft:stone')
    event.crafting.smithing('minecraft:netherite_hoe', 'minecraft:diamond_hoe', 'minecraft:netherite_ingot')

    // Removal by id (also: removeSmelting/removeStonecutter/removeSmithing).
    //   event.crafting.remove('ic2:im_machine')

    console.info('[IC2 example] queued IC2 machine + special + fluid + crafting recipes (applied after IC2 loads)')
})
