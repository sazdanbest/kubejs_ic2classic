// ============================================================================
//  IC2 × KubeJS  —  LOCAL TEST SUITE  3/3 : count-aware workbench crafts
// ----------------------------------------------------------------------------
//  The headline feature: N items in a SINGLE crafting slot (honest, via IC2
//  IInput) + the JEI "+" transfer that moves N per slot instead of 1.
//  Log to watch:  "[IC2-TEST] crafting applied +N/-M"  and  "NO_PARSE_ERRORS".
//
//  IMPORTANT — recipe id NAMESPACE: IC2 feeds these crafts to the vanilla
//  RecipeManager via a virtual resource pack that only serves namespaces equal
//  to a LOADED mod id (plus ic2 / minecraft). So a crafting id must use a real
//  mod namespace — 'kubejs:' (KubeJS is loaded) or your own mod — NOT a made-up
//  one like 'ic2test:', which IC2 silently drops. Machine recipes (suites 1-2)
//  are NOT affected; they bypass the RecipeManager.
// ============================================================================

IC2Events.recipes(event => {
    console.info('[IC2-TEST] ===== 3/3 crafting: begin =====')

    // ---- HEADLINE JEI COUNT TEST -------------------------------------------
    // [T-CFT-1] Full 3x3, every slot needs 2 Advanced Alloy -> 4 Advanced Alloy Plates.
    //           In JEI, search "Advanced Alloy Plate": every input slot must show ×2.
    //           This is the recipe to use for the JEI "+" transfer button test.
    event.crafting.shaped('kubejs:plate_grid', Item.of('ic2:plate_advanced_alloy', 4),
        ['AAA', 'AAA', 'AAA'],
        { A: Item.of('ic2:ingot_advanced_alloy', 2) })

    // [T-CFT-2] Single row, each slot needs 4 Advanced Alloy -> Refined Iron (doc example).
    event.crafting.shaped('kubejs:refined_row', 'ic2:ingot_refined_iron',
        ['PPP'],
        { P: Item.of('ic2:ingot_advanced_alloy', 4) })

    // ---- SHAPELESS : count on output and on input --------------------------
    // [T-CFT-3] count OUTPUT: 2 diamonds from a diamond block.
    event.crafting.shapeless(Item.of('minecraft:diamond', 2), ['minecraft:diamond_block'])
    // [T-CFT-4] count INPUT: 4 sticks in one slot -> a blaze rod (honest count-in).
    event.crafting.shapeless('minecraft:blaze_rod', Item.of('minecraft:stick', 4))

    // ---- SMELTING : all four cooking types ---------------------------------
    // NOTE: these inject vanilla cooking recipes, so they ALSO show up in IC2's electric
    //       furnace / blast furnace / smoker (which mirror the vanilla RecipeManager).
    // [T-SML-1] furnace (default)
    event.crafting.smelting('kubejs:smelt_furnace', 'minecraft:emerald', 'minecraft:sand')
    // [T-SML-2] blast furnace
    event.crafting.smelting('kubejs:smelt_blast', 'ic2:ingot_refined_iron', 'minecraft:iron_ingot', 'blast_furnace')
    // [T-SML-3] smoker
    event.crafting.smelting('kubejs:smelt_smoker', 'minecraft:golden_carrot', 'minecraft:carrot', 'smoker')
    // [T-SML-4] campfire, with xp + cook time
    event.crafting.smelting('kubejs:smelt_campfire', 'minecraft:cooked_cod', 'minecraft:cod', 'campfire', 0.5, 200)

    // ---- STONECUTTER -------------------------------------------------------
    // [T-STC-1]
    event.crafting.stonecutter('kubejs:cut_bricks', Item.of('minecraft:stone_bricks', 4), 'minecraft:stone')

    // ---- SMITHING ----------------------------------------------------------
    // [T-SMI-1] base + addition -> output
    event.crafting.smithing('kubejs:smith_hoe', 'minecraft:netherite_hoe', 'minecraft:diamond_hoe', 'minecraft:netherite_ingot')

    // ---- REMOVAL (optional) ------------------------------------------------
    // Re-declaring an id above already tests idempotency across /reload.
    // Example of removing an IC2 default craft by id (uncomment to try):
    //   event.crafting.remove('ic2:some_recipe_id')

    console.info('[IC2-TEST] ===== 3/3 crafting: queued (applied after IC2 loads) =====')
})
