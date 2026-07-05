package com.sazdan.kubejsic2classic.jei;

import com.sazdan.kubejsic2classic.net.FillCraftingGridPacket;
import com.sazdan.kubejsic2classic.net.IC2Net;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.core.platform.recipes.crafting.RecipeIC2Base;
import ic2.core.platform.recipes.crafting.ShapedIC2Recipe;
import ic2.core.platform.recipes.crafting.ShapelessIC2Recipe;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Count-aware recipe transfer for the vanilla 3×3 {@link CraftingMenu}.
 *
 * <p>Registered for {@code (CraftingMenu, minecraft:crafting)}, so it receives every
 * crafting recipe. IC2 count recipes ({@link RecipeIC2Base}) are transferred via our own
 * {@link FillCraftingGridPacket} (moving {@code getInputSize()} items per slot); every other
 * recipe is delegated to JEI's default crafting transfer handler, so vanilla behaviour is
 * untouched.
 *
 * <p>Before allowing a transfer we check the player's inventory (plus what already sits in
 * the grid) against the recipe's {@link IInput}s, count included, and report shortfalls via
 * {@link IRecipeTransferHandlerHelper#createUserErrorForMissingSlots} — JEI then disables the
 * "+" button and highlights the missing slots, matching its behaviour for vanilla recipes.
 */
public class IC2CraftingTransferHandler implements IRecipeTransferHandler<CraftingMenu, CraftingRecipe> {
    private final IRecipeTransferHandler<CraftingMenu, CraftingRecipe> delegate;
    private final IRecipeTransferHandlerHelper helper;

    public IC2CraftingTransferHandler(IRecipeTransferHandler<CraftingMenu, CraftingRecipe> delegate,
                                      IRecipeTransferHandlerHelper helper) {
        this.delegate = delegate;
        this.helper = helper;
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
        IRecipeTransferError missing = checkMissingInputs(container, recipe, recipeSlots, player);
        if (missing != null) {
            return missing;
        }
        if (doTransfer) {
            IC2Net.CHANNEL.sendToServer(new FillCraftingGridPacket(recipe.getId(), maxTransfer));
        }
        return null; // no error
    }

    /**
     * Simulates one transfer against the player's inventory + current grid contents (the
     * server returns the grid to the inventory before filling) and returns a user-facing
     * error highlighting the slots whose {@link IInput} cannot be satisfied, or {@code null}
     * if the recipe is fully transferable.
     */
    private IRecipeTransferError checkMissingInputs(CraftingMenu container, CraftingRecipe recipe,
                                                    IRecipeSlotsView recipeSlots, Player player) {
        IInput[] inputs;
        int width;
        int height;
        if (recipe instanceof ShapedIC2Recipe shaped) {
            inputs = shaped.inputs;
            width = shaped.width;
            height = shaped.height;
        } else if (recipe instanceof ShapelessIC2Recipe shapeless) {
            inputs = shapeless.inputs.toArray(new IInput[0]);
            width = height = getShapelessSize(inputs.length);
        } else {
            return null; // unknown IC2 recipe shape — let the server do its best-effort fill
        }

        // What the server-side fill can draw from: main inventory + whatever is in the grid.
        List<ItemStack> available = new ArrayList<>(player.getInventory().items.size() + 9);
        for (ItemStack stack : player.getInventory().items) {
            available.add(stack.copy());
        }
        for (int g = 0; g < 9; g++) {
            available.add(container.getSlot(1 + g).getItem().copy());
        }

        // Input slot views are the 9 grid slots in row-major order (see JEI CraftingGridHelper).
        List<IRecipeSlotView> slotViews = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        List<IRecipeSlotView> missingViews = new ArrayList<>();
        boolean anyMissing = false;
        for (int i = 0; i < inputs.length; i++) {
            IInput in = inputs[i];
            if (in == null || !canPull(available, in, in.getInputSize())) {
                if (in == null) {
                    continue;
                }
                anyMissing = true;
                int viewIndex = getCraftingIndex(i, width, height);
                if (viewIndex >= 0 && viewIndex < slotViews.size()) {
                    missingViews.add(slotViews.get(viewIndex));
                }
            }
        }
        if (!anyMissing) {
            return null;
        }
        Component message = Component.translatable("jei.tooltip.error.recipe.transfer.missing");
        return missingViews.isEmpty()
                ? helper.createUserErrorWithTooltip(message)
                : helper.createUserErrorForMissingSlots(message, missingViews);
    }

    /**
     * Mirrors {@link FillCraftingGridPacket}'s {@code pullMatching}: the first matching stack
     * fixes the item type for the slot (a crafting slot holds one type), then same-type stacks
     * are consumed from {@code available} until {@code need} is covered.
     */
    private static boolean canPull(List<ItemStack> available, IInput in, int need) {
        ItemStack chosen = ItemStack.EMPTY;
        for (ItemStack stack : available) {
            if (need <= 0) {
                break;
            }
            if (stack.isEmpty() || !in.matches(stack)) {
                continue;
            }
            if (chosen.isEmpty()) {
                chosen = stack.copy();
            } else if (!ItemStack.isSameItemSameTags(chosen, stack)) {
                continue;
            }
            int take = Math.min(stack.getCount(), need);
            stack.shrink(take);
            need -= take;
        }
        return need <= 0;
    }

    // Both methods replicate JEI's mezz.jei.library.gui.CraftingGridHelper, which decides
    // where our category extensions' inputs land in the 3×3 slot-view grid.

    private static int getShapelessSize(int total) {
        if (total > 4) {
            return 3;
        }
        return total > 1 ? 2 : 1;
    }

    private static int getCraftingIndex(int i, int width, int height) {
        if (width == 1) {
            return height == 1 ? 4 : i * 3 + 1;
        }
        if (height == 1) {
            return i + 3;
        }
        if (width == 2) {
            int index = i;
            if (i > 1) {
                index++;
                if (i > 3) {
                    index++;
                }
            }
            return index;
        }
        if (height == 2) {
            return i + 3;
        }
        return i;
    }
}
