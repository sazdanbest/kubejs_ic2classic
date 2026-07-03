package com.sazdan.kubejsic2classic.recipe.special;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IPotionBrewRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;

/**
 * {@code event.potionBrewing} — IC2 potion-brewing: an ingredient item grants a {@link MobEffect}
 * (optionally with a second effect).
 * <pre>
 *   event.potionBrewing.addBrew('minecraft:golden_carrot', 'minecraft:night_vision')
 *   event.potionBrewing.addBrew('minecraft:sugar', 'minecraft:speed', 'minecraft:haste')
 *   event.potionBrewing.removeBrew('minecraft:sugar')
 * </pre>
 */
public class PotionBrewWrapper extends RecordingWrapper<IPotionBrewRegistry> {

    public PotionBrewWrapper() {
        super("potionBrewing");
    }

    /** Register an ingredient → effect brew. */
    public void addBrew(Object item, Object effect) {
        Item ingredient = IC2Conversions.toItemStack(item).getItem();
        MobEffect mob = IC2Conversions.toMobEffect(effect);
        addOp(reg -> reg.registerBrew(ingredient, mob));
    }

    /** Register an ingredient → two effects brew. */
    public void addBrew(Object item, Object effect, Object secondEffect) {
        Item ingredient = IC2Conversions.toItemStack(item).getItem();
        MobEffect first = IC2Conversions.toMobEffect(effect);
        MobEffect second = IC2Conversions.toMobEffect(secondEffect);
        addOp(reg -> reg.registerBrew(ingredient, first, second));
    }

    /** Remove the brew registered for an ingredient item. */
    public void removeBrew(Object item) {
        Item ingredient = IC2Conversions.toItemStack(item).getItem();
        removeOp(reg -> reg.removeBrew(ingredient));
    }
}
