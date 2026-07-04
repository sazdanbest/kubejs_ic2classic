// ============================================================================
//  IC2 × KubeJS  —  LOCAL TEST SUITE  4/4 : error resilience  [T-RESILIENCE]
// ----------------------------------------------------------------------------
//  A script that produces an IC2-INVALID recipe must NOT crash IC2's whole
//  recipe reload (which would silently drop every other addon recipe).
//  The addon isolates each op: this line should log
//     "[kubejs_ic2classic] mixingFurnace: applied +1/-0 op(s), 1 rejected by IC2"
//  plus an ERROR line "a recipe op was rejected by IC2 and skipped",
//  and suites 1–3 must still apply fully and the world must still load.
//
//  To DISABLE this negative test, delete this file.
// ============================================================================

IC2Events.recipes(event => {
    // Mixing furnace needs EXACTLY 2 inputs — giving 1 is invalid on purpose.
    event.mixingFurnace.add('ic2:ingot_bronze', 'ic2:dust_bronze')
    console.info('[IC2-TEST] ===== 4/4 resilience: queued 1 intentionally-invalid recipe =====')
})
