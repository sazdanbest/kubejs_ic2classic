package com.sazdan.kubejsic2classic.recipe.special;

import java.util.ArrayList;
import java.util.List;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IUUMatterRegistry;
import ic2.api.recipes.registries.IUUMatterRegistry.UUMatterEntry;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.uuMatter} — items craftable from UU-Matter and their UU cost.
 * <pre>
 *   event.uuMatter.addEntry('minecraft:diamond', 166000)   // item, UU needed
 *   event.uuMatter.removeByOutput('minecraft:iron_ingot')
 * </pre>
 * (Shaped UU patterns via {@code UUMatterBuilder} are not exposed yet.)
 */
public class UuMatterWrapper extends RecordingWrapper<IUUMatterRegistry> {

    public UuMatterWrapper() {
        super("uuMatter");
    }

    /** Register an item as craftable from the given amount of UU-Matter. */
    public void addEntry(Object item, int uuNeeded) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        addOp(reg -> reg.registerUUEntry(stack, uuNeeded));
    }

    /** Remove every UU entry that outputs the given item. */
    public void removeByOutput(Object item) {
        ItemStack target = IC2Conversions.toItemStack(item);
        removeOp(reg -> {
            List<UUMatterEntry> toRemove = new ArrayList<>();
            for (UUMatterEntry entry : reg.getEntries()) {
                if (ItemStack.isSame(entry.getStack(), target)) {
                    toRemove.add(entry);
                }
            }
            for (UUMatterEntry entry : toRemove) {
                reg.removeUUEntry(entry);
            }
        });
    }
}
