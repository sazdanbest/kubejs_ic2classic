package com.sazdan.kubejsic2classic.recipe;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

import com.sazdan.kubejsic2classic.KubeJSIC2Classic;
import com.sazdan.kubejsic2classic.event.IC2Events;
import com.sazdan.kubejsic2classic.event.IC2RecipesEventJS;

import dev.latvian.mods.kubejs.script.ScriptType;
import ic2.api.recipes.RecipeRegistry;
import ic2.api.recipes.registries.IListenableRegistry;
import ic2.api.recipes.registries.IRefiningRecipeList;
import ic2.api.util.SidedObject;
import ic2.core.IC2;
import ic2.core.platform.recipes.misc.GlobalRecipes;

/**
 * Bridges the KubeJS {@code IC2Events.recipes} event to IC2's own reload lifecycle.
 *
 * <p>KubeJS posts the event <em>before</em> IC2 rebuilds its recipe lists, so we cannot apply
 * there. Instead we:
 * <ol>
 *   <li>on every server (re)load, {@link #collect()} runs the scripts to <em>record</em> the
 *       desired operations into a fresh {@link IC2RecipesEventJS};</li>
 *   <li>a one-time {@link IListenableRegistry} listener on each IC2 registry replays the latest
 *       recorded operations right after IC2 finishes (re)loading it.</li>
 * </ol>
 * Because IC2 clears and rebuilds each list per reload, and every recorded add removes its id
 * first, this stays duplicate-free across {@code /reload}.
 */
public final class IC2RecipeApplier {

    private static volatile IC2RecipesEventJS pending;
    private static boolean listenersRegistered = false;

    private IC2RecipeApplier() {}

    /** Called from {@code onServerReload}: re-run scripts to capture the latest operations. */
    public static void collect() {
        IC2RecipesEventJS event = new IC2RecipesEventJS();
        if (IC2Events.RECIPES.hasListeners()) {
            IC2Events.RECIPES.post(ScriptType.SERVER, event);
        }
        pending = event;
        ensureListeners();
    }

    private static synchronized void ensureListeners() {
        if (listenersRegistered) {
            return;
        }
        listenersRegistered = true;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

        // Plain machines (incl. recycler): wrappers keyed by name in the event.
        for (Machines.Def def : Machines.ALL) {
            String name = def.name();
            registerBoth(def.sided(), seen,
                    list -> apply(name, list, ev -> ev.machine(name)));
        }

        // Registries with bespoke APIs, matched to their event field.
        registerBoth(RecipeRegistry.ELECTROLYZER, seen,
                list -> apply("electrolyzer", list, ev -> ev.electrolyzer));
        registerBoth(RecipeRegistry.SCRAP_BOX, seen,
                list -> apply("scrapbox", list, ev -> ev.scrapbox));
        registerBoth(RecipeRegistry.FUSION_REACTOR, seen,
                list -> apply("fusion", list, ev -> ev.fusion));
        registerBoth(RecipeRegistry.UU_SHAPES, seen,
                list -> apply("uuMatter", list, ev -> ev.uuMatter));
        registerBoth(RecipeRegistry.CANNER, seen,
                list -> apply("canner", list, ev -> ev.canner));
        registerBoth(RecipeRegistry.FLUID_FUELS, seen,
                list -> apply("fluidFuels", list, ev -> ev.fluidFuels));
        registerBoth(RecipeRegistry.RARE_EARTH, seen,
                list -> apply("rareEarth", list, ev -> ev.rareEarth));
        registerBoth(RecipeRegistry.POTION_BREWING, seen,
                list -> apply("potionBrewing", list, ev -> ev.potionBrewing));

        // Count-aware workbench crafts — single (non-sided) registry, fires listeners on reload().
        registerListenerDedup(RecipeRegistry.CRAFTING, seen,
                list -> apply("crafting", list, ev -> ev.crafting));

        // Note: IC2's electric furnace / blast furnace / smoker are NOT scriptable here. They are
        // CookingMachineRecipeList instances whose reload() rebuilds the list from the vanilla
        // RecipeManager (smelting/blasting/smoking) and never fires our listeners — they are read-only
        // mirrors of vanilla cooking recipes. To add to them, add a vanilla cooking recipe, e.g.
        // event.crafting.smelting(out, in, 'furnace' | 'blast_furnace' | 'smoker').

        // Refining has an API interface but no RecipeRegistry field — reach it via IC2 internals.
        registerRefining(seen);

        KubeJSIC2Classic.LOGGER.info("[{}] registered IC2 reload listeners ({} machines + 8 special + crafting + refining)",
                KubeJSIC2Classic.MOD_ID, Machines.ALL.size());
    }

    /** Register {@code onReload} on both sides of a registry, de-duplicating shared instances. */
    private static <X extends IListenableRegistry<X>> void registerBoth(
            SidedObject<? extends X> sided, Set<Object> seen, java.util.function.Consumer<X> onReload) {
        for (boolean simulating : new boolean[] {true, false}) {
            registerListenerDedup(sided.get(simulating), seen, onReload);
        }
    }

    private static <X extends IListenableRegistry<X>> void registerListenerDedup(
            X list, Set<Object> seen, java.util.function.Consumer<X> onReload) {
        if (list == null || !seen.add(list)) {
            return;
        }
        list.registerListener(onReload);
    }

    /** Refining: not on {@link RecipeRegistry}, so obtained from IC2's internal gateway (guarded). */
    private static void registerRefining(Set<Object> seen) {
        try {
            for (boolean simulating : new boolean[] {true, false}) {
                GlobalRecipes global = IC2.RECIPES.get(simulating);
                if (global == null) {
                    continue;
                }
                IRefiningRecipeList refining = global.refining;
                registerListenerDedup(refining, seen, list -> apply("refining", list, ev -> ev.refining));
            }
        } catch (Throwable t) {
            KubeJSIC2Classic.LOGGER.warn("[{}] refining registry unavailable (IC2 internals changed?) — "
                    + "event.refining will be ignored", KubeJSIC2Classic.MOD_ID, t);
        }
    }

    /** Apply the latest recorded ops for one registry onto its freshly (re)loaded instance. */
    private static <X> void apply(String name, X list, Function<IC2RecipesEventJS, ? extends RecordingWrapper<X>> accessor) {
        IC2RecipesEventJS event = pending;
        if (event == null) {
            return;
        }
        RecordingWrapper<X> wrapper = accessor.apply(event);
        if (wrapper == null || wrapper.isEmpty()) {
            return;
        }
        wrapper.applyTo(list);
        if (wrapper.failureCount() > 0) {
            KubeJSIC2Classic.LOGGER.warn("[{}] {}: applied +{}/-{} op(s), {} rejected by IC2",
                    KubeJSIC2Classic.MOD_ID, name, wrapper.addCount(), wrapper.removeCount(),
                    wrapper.failureCount());
        } else {
            KubeJSIC2Classic.LOGGER.info("[{}] {}: applied +{}/-{} op(s)",
                    KubeJSIC2Classic.MOD_ID, name, wrapper.addCount(), wrapper.removeCount());
        }
    }
}
