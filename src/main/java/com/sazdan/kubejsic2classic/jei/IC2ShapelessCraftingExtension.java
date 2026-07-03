package com.sazdan.kubejsic2classic.jei;

import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.core.platform.recipes.crafting.ShapelessIC2Recipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws an IC2 {@link ShapelessIC2Recipe} inside JEI's vanilla Crafting category with the
 * correct per-slot counts, sourced from {@link IInput#getComponents()}. See
 * {@link IC2ShapedCraftingExtension} for why this is needed.
 */
public class IC2ShapelessCraftingExtension implements ICraftingCategoryExtension {
    private final ShapelessIC2Recipe recipe;

    public IC2ShapelessCraftingExtension(ShapelessIC2Recipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper gridHelper, IFocusGroup focuses) {
        List<List<ItemStack>> inputs = new ArrayList<>(recipe.inputs.size());
        for (IInput in : recipe.inputs) {
            inputs.add(in == null ? List.of() : List.copyOf(in.getComponents()));
        }
        // width/height 0 -> JEI treats it as a shapeless grid and lays inputs out itself.
        gridHelper.createAndSetInputs(builder, inputs, 0, 0);
        gridHelper.createAndSetOutputs(builder, List.of(recipe.getResultItem()));
    }

    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getId();
    }
}
