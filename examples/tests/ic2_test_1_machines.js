// ============================================================================
//  IC2 × KubeJS  —  LOCAL TEST SUITE  1/3 : plain machine recipes
// ----------------------------------------------------------------------------
//  Every recipe below uses DISTINCTIVE outputs so you can find it in JEI by
//  searching the result item, and CREATIVE-EASY vanilla inputs so you can run
//  the machine in a creative world quickly.
//  Each line is tagged  [T-xxx]  — matches the local test checklist.
//  Watch the log for:  "[IC2-TEST] machines: <machine> applied +N/-M".
// ============================================================================

IC2Events.recipes(event => {
    console.info('[IC2-TEST] ===== 1/3 machines: begin =====')

    // ---- MACERATOR : add / explicit-id / chance / range / xp / removals -----
    // [T-MAC-1] simplest add, auto id, count-aware output (2 diamonds).
    event.macerator.add(Item.of('minecraft:diamond', 2), 'minecraft:bookshelf')
    // [T-MAC-2] explicit id + IC2 output item, 4 in one stack.
    event.macerator.add('ic2test:mac_bricks', Item.of('ic2:dust_iron', 4), 'minecraft:bricks')
    // [T-MAC-3] chance output: 50% emerald from mossy cobblestone.
    event.macerator.addChance('minecraft:emerald', 0.5, 'minecraft:mossy_cobblestone')
    // [T-MAC-4] range output: 2..5 gold nuggets from clay.
    event.macerator.addRange('minecraft:gold_nugget', 2, 5, 'minecraft:clay')
    // [T-MAC-5] xp output: quartz from diorite, 1.0 xp per op.
    event.macerator.addXp('minecraft:quartz', 1.0, 'minecraft:diorite')
    // [T-MAC-REM] removal of a REAL IC2 default (cobblestone -> sand). Verify in
    //             JEI that macerating cobblestone no longer yields sand.
    event.macerator.removeByOutput('minecraft:sand')

    // ---- EXTRACTOR : count-aware input + removeByInput ----------------------
    // [T-EXT-1] needs 4 redstone in ONE slot -> a redstone block (honest count).
    event.extractor.add('minecraft:redstone_block', Item.of('minecraft:redstone', 4))
    // [T-EXT-2] tag input.
    event.extractor.add('minecraft:slime_ball', '#forge:slimeballs')

    // ---- COMPRESSOR --------------------------------------------------------
    // [T-CMP-1] distinctive: netherite block from a single sponge (creative-easy).
    event.compressor.add('minecraft:netherite_block', 'minecraft:sponge')

    // ---- SAWMILL -----------------------------------------------------------
    // [T-SAW-1] range 3..6 sticks from a crafting table.
    event.sawmill.addRange('minecraft:stick', 3, 6, 'minecraft:crafting_table')

    // ---- RECYCLER (IRecyclerRecipeList extends IMachineRecipeList) ----------
    // [T-RCY-1] chance drop: 12.5% glowstone dust from rotten flesh.
    //   IC2 already recycles rotten flesh -> fertilizer, and the FIRST matching recipe wins, so the
    //   default would shadow ours. To truly REPLACE, remove the old one for this input first.
    event.recycler.removeByInput('minecraft:rotten_flesh')
    event.recycler.addChance('minecraft:glowstone_dust', 0.125, 'minecraft:rotten_flesh')

    // ---- MASS FABRICATOR ---------------------------------------------------
    // [T-MFB-1] amplifier-style entry (item accelerates matter production).
    event.massFabricator.add('minecraft:cobblestone', 'minecraft:diamond')

    // NOTE: IC2's electric FURNACE / BLAST FURNACE / SMOKER are read-only mirrors of vanilla
    // cooking recipes (CookingMachineRecipeList rebuilds from the vanilla RecipeManager each reload),
    // so there is intentionally no event.furnace / event.blastFurnace / event.smoker. Author those in
    // suite 3 via event.crafting.smelting(out, in, 'furnace' | 'blast_furnace' | 'smoker').

    // ---- MIXING FURNACE ----------------------------------------------------
    // [T-MIX-1] MultiMachineRecipeList: needs EXACTLY 2 inputs (maxInputs=2, exact=true).
    //           Alloy bronze dust from copper + tin dust.
    event.mixingFurnace.add('ic2:dust_bronze', 'ic2:dust_copper', 'ic2:dust_tin')

    console.info('[IC2-TEST] ===== 1/3 machines: queued (applied after IC2 loads) =====')
})
