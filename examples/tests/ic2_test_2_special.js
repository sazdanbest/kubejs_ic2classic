// ============================================================================
//  IC2 × KubeJS  —  LOCAL TEST SUITE  2/3 : special registries + fluids
// ----------------------------------------------------------------------------
//  These registries have bespoke APIs (electrolyzer, scrapbox, fusion, uu,
//  canner, fluid fuels, refinery, rare earth, potion brewing).
//  Most are only fully verifiable in the machine GUI / JEI; the log line
//  "[IC2-TEST] special: <name> applied +N/-M" confirms they were accepted.
// ============================================================================

IC2Events.recipes(event => {
    console.info('[IC2-TEST] ===== 2/3 special: begin =====')

    // ---- ELECTROLYZER : charge / discharge / dual --------------------------
    // signature: (input, output, energyEU)
    // [T-ELE-1] charge
    event.electrolyzer.addCharge('minecraft:apple', 'minecraft:golden_apple', 12000)
    // [T-ELE-2] discharge
    event.electrolyzer.addDischarge('minecraft:golden_apple', 'minecraft:apple', 6000)
    // [T-ELE-3] dual
    event.electrolyzer.addDual('minecraft:magma_cream', 'minecraft:blaze_powder', 8000)

    // ---- SCRAP BOX : weighted loot -----------------------------------------
    // [T-SCR-1] The second arg is a WEIGHT within the drop pool, not a probability. Default IC2
    //           drops have small weights, so use a big number to actually see diamonds drop.
    event.scrapbox.addDrop('minecraft:diamond', 1000)

    // ---- FUSION : fuel(item, fuelValueEU, productionRate, consumed) ---------
    // [T-FUS-1]
    event.fusion.addFuel('minecraft:blaze_rod', 40000, 1.0, true)

    // ---- UU-MATTER : entry(item, uuNeeded) ---------------------------------
    // [T-UUM-1] craft a diamond from 166000 UU.
    event.uuMatter.addEntry('minecraft:diamond', 166000)

    // ---- CANNER : fuel value / multiplier / fillable -----------------------
    // [T-CAN-1] fuel value
    event.canner.addFuel('minecraft:coal', 8)
    // [T-CAN-2] fuel multiplier
    event.canner.addFuelMultiplier('minecraft:lava_bucket', 2.0)
    // [T-CAN-3] fillable: fill empty cell (+ water bucket) -> water cell.
    event.canner.addFillable('ic2:cell_empty', 'minecraft:water_bucket', 'ic2:cell_water')

    // ---- FLUID FUELS : fuel(fluid, ticksPerBucket, euPerTick) ---------------
    // [T-FLF-1] lava as a fluid generator fuel.
    event.fluidFuels.addFuel('minecraft:lava', 1000, 4)

    // ---- REFINERY : fluent builder -----------------------------------------
    // [T-REF-1] two-fluid + catalyst -> fluid + item output.
    event.refining.add('ic2test:water_to_lava')
        .firstFluid('minecraft:water', 1000)
        .catalyst('minecraft:redstone')
        .fluidOutput('minecraft:lava', 100)
        .itemOutput('minecraft:blaze_powder')
        .build()

    // ---- RARE EARTH : output pool + weighted inputs ------------------------
    // [T-RAR-1] output
    event.rareEarth.addOutput('minecraft:emerald')
    // [T-RAR-2] weighted input
    event.rareEarth.addInput(1.0, 'minecraft:prismarine_shard')

    // ---- POTION BREWING : ingredient -> effect -----------------------------
    // [T-POT-1] single effect
    event.potionBrewing.addBrew('minecraft:golden_carrot', 'minecraft:night_vision')
    // [T-POT-2] two effects
    event.potionBrewing.addBrew('minecraft:pufferfish', 'minecraft:water_breathing', 'minecraft:poison')

    console.info('[IC2-TEST] ===== 2/3 special: queued (applied after IC2 loads) =====')
})
