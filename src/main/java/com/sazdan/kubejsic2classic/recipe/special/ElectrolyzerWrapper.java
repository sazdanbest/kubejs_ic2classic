package com.sazdan.kubejsic2classic.recipe.special;

import java.util.HashMap;
import java.util.Map;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IElectrolyzerRecipeList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.electrolyzer} — IC2 electrolyzer recipes.
 *
 * <p>A recipe is {@code input + energy → output} in one of three modes:
 * <b>charge</b> (fills a cell), <b>discharge</b> (empties a cell) or <b>dual</b> (both).
 * <pre>
 *   event.electrolyzer.addCharge('ic2:water_cell', 'ic2:empty_cell', 12000)
 *   event.electrolyzer.addDischarge('id', input, output, energy)
 * </pre>
 */
public class ElectrolyzerWrapper extends RecordingWrapper<IElectrolyzerRecipeList> {

    private enum Mode { CHARGE, DISCHARGE, DUAL }

    private final Map<String, Integer> idCounter = new HashMap<>();

    public ElectrolyzerWrapper() {
        super("electrolyzer");
    }

    /** {@code addCharge(input, output, energy)} or {@code addCharge(id, input, output, energy)}. */
    public ResourceLocation addCharge(Object... args) {
        return add(Mode.CHARGE, args);
    }

    public ResourceLocation addDischarge(Object... args) {
        return add(Mode.DISCHARGE, args);
    }

    public ResourceLocation addDual(Object... args) {
        return add(Mode.DUAL, args);
    }

    private ResourceLocation add(Mode mode, Object[] args) {
        int i = 0;
        ResourceLocation id = null;
        if (args.length == 4 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            i = 1;
        }
        if (args.length < i + 3) {
            throw new IllegalArgumentException("[electrolyzer] expected (input, output, energy)");
        }
        ItemStack input = IC2Conversions.toItemStack(args[i]);
        ItemStack output = IC2Conversions.toItemStack(args[i + 1]);
        int energy = IC2Conversions.toInt(args[i + 2]);
        ResourceLocation rl = id != null ? id : IC2Conversions.autoId("electrolyzer", output, input, idCounter);
        addOp(list -> {
            list.removeRecipe(rl);
            switch (mode) {
                case CHARGE -> list.addChargeRecipe(rl, input, output, energy);
                case DISCHARGE -> list.addDischargeRecipe(rl, input, output, energy);
                case DUAL -> list.addDualRecipe(rl, input, output, energy);
            }
        });
        return rl;
    }

    /** Remove one recipe by its id. */
    public void remove(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(list -> list.removeRecipe(rl));
    }

    /** Remove the recipe(s) matching the given input item ({@code charge}=true to match charge side). */
    public void removeByInput(Object input, boolean charge) {
        ItemStack probe = IC2Conversions.toItemStack(input);
        removeOp(list -> list.removeRecipe(probe, charge));
    }
}
