package com.sazdan.kubejsic2classic.recipe.special;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IFusionRecipeList;
import net.minecraft.world.item.Item;

/**
 * {@code event.fusion} — fusion reactor fuels (not item→item recipes).
 * <pre>
 *   // item, fuelValue (EU), productionRate, consumed?
 *   event.fusion.addFuel('ic2:deuterium_cell', 40000, 1.0, true)
 *   event.fusion.removeFuel('ic2:tritium_cell')
 * </pre>
 */
public class FusionWrapper extends RecordingWrapper<IFusionRecipeList> {

    public FusionWrapper() {
        super("fusion");
    }

    public void addFuel(Object item, int fuelValue, double productionRate, boolean consumed) {
        Item fuel = IC2Conversions.toItemStack(item).getItem();
        float rate = (float) productionRate;
        addOp(reg -> reg.addFuel(fuel, fuelValue, rate, consumed));
    }

    public void removeFuel(Object item) {
        Item fuel = IC2Conversions.toItemStack(item).getItem();
        removeOp(reg -> reg.removeFuel(fuel));
    }
}
