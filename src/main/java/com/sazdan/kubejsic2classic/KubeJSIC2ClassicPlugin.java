package com.sazdan.kubejsic2classic;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;

/**
 * KubeJS plugin for IC2Classic. Registered via {@code kubejs.plugins.txt}.
 *
 * <p>Phase 3 skeleton: only exposes a smoke-test global so we can confirm the plugin loads
 * ({@code console.log(IC2Recipes)} in a script). The real machine-recipe and count-craft
 * bindings land in later phases — see CLAUDE.md.
 */
public class KubeJSIC2ClassicPlugin extends KubeJSPlugin {

    @Override
    public void registerBindings(BindingsEvent event) {
        // Smoke-test binding. Replaced by the real IC2Recipes / IC2Crafting objects in Phase 4/5.
        event.add("IC2Recipes", "kubejs_ic2classic: plugin loaded (" + event.getType() + ")");
    }
}
