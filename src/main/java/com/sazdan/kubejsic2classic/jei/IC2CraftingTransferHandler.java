package com.sazdan.kubejsic2classic.jei;

import com.sazdan.kubejsic2classic.net.FillCraftingGridPacket;
import com.sazdan.kubejsic2classic.net.IC2Net;
import ic2.core.platform.recipes.crafting.RecipeIC2Base;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.Optional;

/**
 * Count-aware recipe transfer for the vanilla 3×3 {@link CraftingMenu}.
 *
 * <p>Registered for {@code (CraftingMenu, minecraft:crafting)}, so it receives every
 * crafting recipe. IC2 count recipes ({@link RecipeIC2Base}) are transferred via our own
 * {@link FillCraftingGridPacket} (moving {@code getInputSize()} items per slot); every other
 * recipe is delegated to JEI's default crafting transfer handler, so vanilla behaviour is
 * untouched.
 */
public class IC2CraftingTransferHandler implements IRecipeTransferHandler<CraftingMenu, CraftingRecipe> {
    private final IRecipeTransferHandler<CraftingMenu, CraftingRecipe> delegate;

    public IC2CraftingTransferHandler(IRecipeTransferHandler<CraftingMenu, CraftingRecipe> delegate) {
        this.delegate = delegate;
    }

    @Override
    public Class<? extends CraftingMenu> getContainerClass() {
        return CraftingMenu.class;
    }

    @Override
    public Optional<MenuType<CraftingMenu>> getMenuType() {
        return Optional.of(MenuType.CRAFTING);
    }

    @Override
    public RecipeType<CraftingRecipe> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Override
    public IRecipeTransferError transferRecipe(CraftingMenu container, CraftingRecipe recipe,
                                               IRecipeSlotsView recipeSlots, Player player,
                                               boolean maxTransfer, boolean doTransfer) {
        if (!(recipe instanceof RecipeIC2Base)) {
            return delegate.transferRecipe(container, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
        if (doTransfer) {
            IC2Net.CHANNEL.sendToServer(new FillCraftingGridPacket(recipe.getId(), maxTransfer));
        }
        return null; // no error
    }
}
