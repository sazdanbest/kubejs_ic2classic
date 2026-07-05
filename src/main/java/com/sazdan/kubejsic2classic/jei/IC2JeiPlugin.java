package com.sazdan.kubejsic2classic.jei;

import com.sazdan.kubejsic2classic.KubeJSIC2Classic;
import ic2.core.platform.recipes.crafting.ShapedIC2Recipe;
import ic2.core.platform.recipes.crafting.ShapelessIC2Recipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;

/**
 * JEI integration for IC2 count-aware workbench crafts (Phase 6).
 *
 * <ul>
 *   <li>Extends the vanilla Crafting category so IC2 shaped/shapeless recipes render their
 *       real per-slot counts (from {@code IInput.getComponents()}).</li>
 *   <li>Installs a count-aware transfer handler for the vanilla {@link CraftingMenu} that
 *       moves N items per slot for IC2 recipes and delegates everything else to JEI's default.</li>
 * </ul>
 *
 * <p>JEI only loads {@code @JeiPlugin} classes when JEI is present, so this stays inert when
 * the (optional) JEI dependency is absent.
 */
@JeiPlugin
public class IC2JeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation(KubeJSIC2Classic.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration reg) {
        reg.getCraftingCategory().addCategoryExtension(ShapedIC2Recipe.class, IC2ShapedCraftingExtension::new);
        reg.getCraftingCategory().addCategoryExtension(ShapelessIC2Recipe.class, IC2ShapelessCraftingExtension::new);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration reg) {
        IRecipeTransferHandlerHelper helper = reg.getTransferHelper();
        // JEI's default crafting transfer (moves 1 per slot) — used for all non-IC2 recipes.
        IRecipeTransferInfo<CraftingMenu, CraftingRecipe> defaultInfo = helper.createBasicRecipeTransferInfo(
                CraftingMenu.class, MenuType.CRAFTING, RecipeTypes.CRAFTING, 1, 9, 10, 36);
        IRecipeTransferHandler<CraftingMenu, CraftingRecipe> defaultHandler =
                helper.createUnregisteredRecipeTransferHandler(defaultInfo);

        reg.addRecipeTransferHandler(new IC2CraftingTransferHandler(defaultHandler, helper), RecipeTypes.CRAFTING);
    }
}
