package com.sazdan.kubejsic2classic.event;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * The {@code IC2Events} event group, exposed to server scripts once
 * {@link #GROUP}{@code .register()} runs. KubeJS binds any registered group as a global
 * by its name, so scripts can call {@code IC2Events.recipes(event => { ... })}.
 */
public interface IC2Events {

    EventGroup GROUP = EventGroup.of("IC2Events");

    /** Fired on every server (re)load — where machine recipes are added/removed. */
    EventHandler RECIPES = GROUP.server("recipes", () -> IC2RecipesEventJS.class);
}
