package com.sazdan.kubejsic2classic.jei;

import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.core.platform.recipes.crafting.ShapedIC2Recipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws an IC2 {@link ShapedIC2Recipe} inside JEI's vanilla Crafting category with the
 * correct per-slot counts. The vanilla layer ({@code getIngredients()}) collapses IC2's
 * {@link IInput} count to 1; here we fill slots straight from {@link IInput#getComponents()},
 * which preserves the count, so JEI renders e.g. "×4" in the slot.
 */
public class IC2ShapedCraftingExtension implements ICraftingCategoryExtension {
    private final ShapedIC2Recipe recipe;

    public IC2ShapedCraftingExtension(ShapedIC2Recipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper gridHelper, IFocusGroup focuses) {
        int width = recipe.width;
        int height = recipe.height;
        List<List<ItemStack>> inputs = new ArrayList<>(width * height);
        for (int i = 0; i < width * height; i++) {
            IInput in = recipe.inputs[i];
            inputs.add(in == null ? List.of() : List.copyOf(in.getComponents()));
        }
        gridHelper.createAndSetInputs(builder, inputs, width, height);
        gridHelper.createAndSetOutputs(builder, List.of(recipe.getResultItem()));
    }

    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getId();
    }

    @Override
    public int getWidth() {
        return recipe.width;
    }

    @Override
    public int getHeight() {
        return recipe.height;
    }
}
