package com.sazdan.kubejsic2classic.recipe.special;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.registries.ICannerRecipeRegistry;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.canner} — canner fuel values and fillable containers.
 * <pre>
 *   event.canner.addFuel('minecraft:coal', 8)             // solid canning fuel value
 *   event.canner.addFuelMultiplier('ic2:tin_can', 1.5)
 *   event.canner.addFillable('ic2:empty_cell', 'minecraft:lava_bucket', 'ic2:lava_cell')
 *   event.canner.removeFuel('minecraft:coal')
 *   event.canner.removeFillable('ic2:empty_cell')
 * </pre>
 * (Repairable registration needs an {@code IRepairable} item and is not scriptable.)
 */
public class CannerWrapper extends RecordingWrapper<ICannerRecipeRegistry> {

    public CannerWrapper() {
        super("canner");
    }

    /** Register a flat canning fuel value for an item. */
    public void addFuel(Object item, int value) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        addOp(reg -> reg.registerFuelValue(stack, value));
    }

    /** Register a canning fuel multiplier for an item. */
    public void addFuelMultiplier(Object item, double multiplier) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        float m = (float) multiplier;
        addOp(reg -> reg.registerFuelMultiplier(stack, m));
    }

    /** Remove a fuel value/multiplier for an item. */
    public void removeFuel(Object item) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        removeOp(reg -> reg.removeValue(stack));
    }

    /** Register a fillable container: {@code container + input → result}. */
    public void addFillable(Object container, Object input, Object result) {
        ItemStack containerStack = IC2Conversions.toItemStack(container);
        IInput in = IC2Conversions.toInput(input);
        ItemStack resultStack = IC2Conversions.toItemStack(result);
        addOp(reg -> reg.registerFillable(containerStack, in, resultStack));
    }

    /** Remove all fillable recipes for a container. */
    public void removeFillable(Object container) {
        ItemStack containerStack = IC2Conversions.toItemStack(container);
        removeOp(reg -> reg.removeFillable(containerStack));
    }
}
