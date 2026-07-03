package com.sazdan.kubejsic2classic.recipe;

import java.util.List;

import ic2.api.recipes.RecipeRegistry;
import ic2.api.recipes.registries.IMachineRecipeList;
import ic2.api.util.SidedObject;

/**
 * The plain {@link IMachineRecipeList} machines IC2 exposes via {@link RecipeRegistry}, paired with
 * the script-facing accessor name used on the {@code IC2Events.recipes} event.
 *
 * <p>Machines with bespoke APIs (electrolyzer, canner, recycler, fusion, scrap box, UU, refining)
 * are handled separately and are not listed here.
 */
public final class Machines {

    public record Def(String name, SidedObject<? extends IMachineRecipeList> sided) {}

    public static final List<Def> ALL = List.of(
            new Def("macerator", RecipeRegistry.MACERATOR),
            new Def("extractor", RecipeRegistry.EXTRACTOR),
            new Def("compressor", RecipeRegistry.COMPRESSOR),
            new Def("sawmill", RecipeRegistry.SAWMILL),
            new Def("massFabricator", RecipeRegistry.MASS_FABRICATOR),
            new Def("furnace", RecipeRegistry.FURNACE),
            new Def("blastFurnace", RecipeRegistry.BLAST_FURNACE),
            new Def("smoker", RecipeRegistry.SMOKER),
            new Def("mixingFurnace", RecipeRegistry.MIXING_FURNACE),
            // IRecyclerRecipeList extends IMachineRecipeList, so it gets add/remove/chance/range too.
            new Def("recycler", RecipeRegistry.RECYCLER));

    private Machines() {}
}
