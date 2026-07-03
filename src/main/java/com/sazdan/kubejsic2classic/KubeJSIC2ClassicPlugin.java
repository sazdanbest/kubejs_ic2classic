package com.sazdan.kubejsic2classic;

import com.sazdan.kubejsic2classic.event.IC2Events;
import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.IC2RecipeApplier;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import ic2.api.recipes.ingridients.inputs.IInput;

/**
 * KubeJS plugin for IC2Classic. Registered via {@code kubejs.plugins.txt}.
 *
 * <p>Phase 4: exposes the {@code IC2Events.recipes} server event for adding/removing IC2
 * machine recipes. The event is re-posted on every server (re)load so scripts stay applied
 * after IC2 rebuilds its recipe lists from datapacks.
 */
public class KubeJSIC2ClassicPlugin extends KubeJSPlugin {

    @Override
    public void registerEvents() {
        // Registering the group makes KubeJS expose the global `IC2Events` binding automatically.
        IC2Events.GROUP.register();
    }

    @Override
    public void registerTypeWrappers(ScriptType type, TypeWrappers wrappers) {
        // Let scripts pass strings/items where the API asks for a count-aware IC2 input.
        // NOTE: don't register ResourceLocation here — KubeJS already provides that wrapper,
        // and re-registering it throws and aborts mod construction.
        wrappers.register(IInput.class, (cx, o) -> IC2Conversions.toInput(o));
    }

    @Override
    public void onServerReload() {
        // KubeJS fires this BEFORE IC2 rebuilds its recipe lists, so we only record the scripts'
        // operations here; IC2RecipeApplier replays them from IC2's own reload listener afterwards.
        IC2RecipeApplier.collect();
    }
}
