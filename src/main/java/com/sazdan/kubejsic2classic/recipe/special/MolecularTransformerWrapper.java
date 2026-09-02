package com.sazdan.kubejsic2classic.recipe.special;

import java.util.HashMap;
import java.util.Map;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IElectrolyzerRecipeList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.molecularTransformer} — Advanced Solars' Molecular Transformer recipes.
 *
 * <p>Advanced Solars registers its recipe list ({@code AdvancedSolarsRecipes.MOLECULAR_TRANSFORMER})
 * as an {@link IElectrolyzerRecipeList} — same interface as IC2's own {@code event.electrolyzer}
 * — but overrides {@code addDualRecipe}/{@code addDischargeRecipe} as a plain
 * {@code input + energy → output} transform rather than IC2's cell-fill/cell-empty semantics.
 * {@code addCharge} is NOT overridden by Advanced Solars and inherits the base electrolyzer
 * "fill a cell" behavior, which is likely not meaningful for a Molecular Transformer — prefer
 * {@code addDual}/{@code addDischarge}.
 * <pre>
 *   event.molecularTransformer.addDual('ic2:iron_dust', 'ic2:gold_dust', 12000)
 * </pre>
 *
 * <p>Only active when Advanced Solars is loaded — silently a no-op otherwise, see
 * {@code IC2RecipeApplier#registerMolecularTransformer}.
 */
public class MolecularTransformerWrapper extends RecordingWrapper<IElectrolyzerRecipeList> {

    private enum Mode { CHARGE, DISCHARGE, DUAL }

    private final Map<String, Integer> idCounter = new HashMap<>();

    public MolecularTransformerWrapper() {
        super("molecularTransformer");
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
            throw new IllegalArgumentException("[molecularTransformer] expected (input, output, energy)");
        }
        ItemStack input = IC2Conversions.toItemStack(args[i]);
        ItemStack output = IC2Conversions.toItemStack(args[i + 1]);
        int energy = IC2Conversions.toInt(args[i + 2]);
        ResourceLocation rl = id != null ? id : IC2Conversions.autoId("molecular_transformer", output, input, idCounter);
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
