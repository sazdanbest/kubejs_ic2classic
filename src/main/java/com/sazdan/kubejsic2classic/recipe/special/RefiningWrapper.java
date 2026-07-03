package com.sazdan.kubejsic2classic.recipe.special;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.ingridients.generators.IOutputGenerator;
import ic2.api.recipes.ingridients.generators.ItemWithNBTGenerator;
import ic2.api.recipes.ingridients.inputs.EmptyInput;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.ingridients.recipes.SimpleFluidOutput;
import ic2.api.recipes.registries.IRefiningRecipeList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * {@code event.refining} — the Refinery (two fluid inputs + optional item catalyst → fluid/item
 * output). Uses a fluent builder because a recipe has many optional parts:
 * <pre>
 *   event.refining.add('mypack:refined_oil')
 *       .firstFluid('ic2:oil', 1000)
 *       .secondFluid('minecraft:water', 500)   // optional
 *       .catalyst('#forge:dusts/redstone')     // optional
 *       .fluidOutput('ic2:refined_fuel', 500)
 *       .itemOutput('ic2:tar')                 // optional, can repeat
 *       .build()
 *   event.refining.remove('ic2:refining/oil')
 * </pre>
 *
 * <p>Note: IC2Classic exposes {@link IRefiningRecipeList} as API but does not surface the list on
 * {@code RecipeRegistry}; {@code IC2RecipeApplier} reaches it through IC2's internal
 * {@code IC2.RECIPES} gateway (guarded), so this is the one wrapper that depends on IC2 internals.
 */
public class RefiningWrapper extends RecordingWrapper<IRefiningRecipeList> {

    private final Map<String, Integer> idCounter = new HashMap<>();

    public RefiningWrapper() {
        super("refining");
    }

    /** Start building a refining recipe; {@code id} optional. Call {@link Builder#build()} to record. */
    public Builder add(Object... args) {
        ResourceLocation id = args.length >= 1 && IC2Conversions.looksLikeId(args[0])
                ? IC2Conversions.parseId(args[0]) : null;
        return new Builder(this, id);
    }

    /** Remove one refining recipe by id. */
    public void remove(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(list -> list.removeRecipe(rl, false));
    }

    private void record(ResourceLocation id, FluidStack first, FluidStack second, IInput catalyst,
                        List<IOutputGenerator> itemOut, List<FluidStack> fluidOut) {
        if (first == null || first.isEmpty()) {
            throw new IllegalArgumentException("[refining] a recipe needs a firstFluid");
        }
        if (fluidOut.isEmpty() && itemOut.isEmpty()) {
            throw new IllegalArgumentException("[refining] a recipe needs at least one output");
        }
        ResourceLocation rl = id != null ? id : autoId(first, fluidOut);
        addOp(list -> {
            list.removeRecipe(rl, false); // idempotent
            list.addRecipe(rl, first, second, catalyst, new SimpleFluidOutput(itemOut, fluidOut));
        });
    }

    private ResourceLocation autoId(FluidStack first, List<FluidStack> fluidOut) {
        String in = IC2Conversions.fluidId(first.getFluid()).getPath();
        String out = fluidOut.isEmpty() ? "product" : IC2Conversions.fluidId(fluidOut.get(0).getFluid()).getPath();
        String base = ("refining/" + out + "_from_" + in).toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        int n = idCounter.merge(base, 1, Integer::sum);
        return new ResourceLocation(IC2Conversions.NAMESPACE, n == 1 ? base : base + "_" + (n - 1));
    }

    /** Fluent builder for a single refining recipe. */
    public static class Builder {
        private final RefiningWrapper owner;
        private final ResourceLocation id;
        private FluidStack first = FluidStack.EMPTY;
        private FluidStack second = FluidStack.EMPTY;
        private IInput catalyst = EmptyInput.INSTANCE;
        private final List<IOutputGenerator> itemOut = new ArrayList<>();
        private final List<FluidStack> fluidOut = new ArrayList<>();

        private Builder(RefiningWrapper owner, ResourceLocation id) {
            this.owner = owner;
            this.id = id;
        }

        public Builder firstFluid(Object fluid) {
            this.first = IC2Conversions.toFluidStack(fluid);
            return this;
        }

        public Builder firstFluid(Object fluid, int amount) {
            this.first = IC2Conversions.toFluidStack(fluid);
            this.first.setAmount(amount);
            return this;
        }

        public Builder secondFluid(Object fluid) {
            this.second = IC2Conversions.toFluidStack(fluid);
            return this;
        }

        public Builder secondFluid(Object fluid, int amount) {
            this.second = IC2Conversions.toFluidStack(fluid);
            this.second.setAmount(amount);
            return this;
        }

        public Builder catalyst(Object input) {
            this.catalyst = IC2Conversions.toInput(input);
            return this;
        }

        public Builder fluidOutput(Object fluid) {
            this.fluidOut.add(IC2Conversions.toFluidStack(fluid));
            return this;
        }

        public Builder fluidOutput(Object fluid, int amount) {
            FluidStack fs = IC2Conversions.toFluidStack(fluid);
            fs.setAmount(amount);
            this.fluidOut.add(fs);
            return this;
        }

        public Builder itemOutput(Object item) {
            ItemStack stack = IC2Conversions.toItemStack(item);
            this.itemOut.add(new ItemWithNBTGenerator(stack, stack.getCount()));
            return this;
        }

        /** Record the recipe (deferred; applied after IC2 loads). */
        public void build() {
            owner.record(id, first, second, catalyst, itemOut, fluidOut);
        }
    }
}
