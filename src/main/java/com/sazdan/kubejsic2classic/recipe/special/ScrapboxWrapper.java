package com.sazdan.kubejsic2classic.recipe.special;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IScrapBoxRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.scrapbox} — the loot pool a scrap box rolls into.
 * <pre>
 *   event.scrapbox.addDrop('minecraft:diamond', 0.001)   // item, chance (weight)
 *   event.scrapbox.removeDrop('minecraft:dirt')
 * </pre>
 */
public class ScrapboxWrapper extends RecordingWrapper<IScrapBoxRegistry> {

    public ScrapboxWrapper() {
        super("scrapbox");
    }

    /** Add a possible scrap-box drop with the given chance/weight. */
    public void addDrop(Object item, double chance) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        float c = (float) chance;
        addOp(reg -> reg.addDrop(stack, c));
    }

    /** Remove all drops of the given item. */
    public void removeDrop(Object item) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        removeOp(reg -> reg.removeDrops(stack));
    }
}
