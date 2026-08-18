package com.sazdan.kubejsic2classic.recipe.special;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sazdan.kubejsic2classic.KubeJSIC2Classic;
import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;
import com.sazdan.kubejsic2classic.recipe.Substitution;

import ic2.api.recipes.ingridients.generators.IOutputGenerator;
import ic2.api.recipes.ingridients.generators.ItemWithNBTGenerator;
import ic2.api.recipes.ingridients.inputs.EmptyInput;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.ingridients.recipes.IFluidRecipeOutput;
import ic2.api.recipes.ingridients.recipes.RangeFluidOutput;
import ic2.api.recipes.ingridients.recipes.SimpleFluidOutput;
import ic2.api.recipes.registries.IRefiningRecipeList;
import ic2.api.recipes.registries.IRefiningRecipeList.FluidRecipe;
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
                        List<IOutputGenerator> itemOut, List<FluidStack> fluidOut, IFluidRecipeOutput rangeOutput) {
        if (first == null || first.isEmpty()) {
            throw new IllegalArgumentException("[refining] a recipe needs a firstFluid");
        }
        IFluidRecipeOutput output = rangeOutput != null ? rangeOutput : new SimpleFluidOutput(itemOut, fluidOut);
        if (rangeOutput == null && fluidOut.isEmpty() && itemOut.isEmpty()) {
            throw new IllegalArgumentException("[refining] a recipe needs at least one output");
        }
        ResourceLocation rl = id != null ? id : autoId(first, fluidOut);
        addOp(list -> {
            list.removeRecipe(rl, false); // idempotent
            list.addRecipe(rl, first, second, catalyst, output);
        });
    }

    // ----------------------------------------------------------- substitute

    /**
     * Swap one item/fluid for another across every refining recipe already registered —
     * IC2's own defaults included (main/second fluid, item catalyst, item/fluid output) —
     * keeping id and everything else (chance/range/XP/NBT) exactly as it was. See
     * {@link Substitution} for the exact rules (tag catalysts are never touched).
     */
    public void substitute(Object from, Object to) {
        substitute(Substitution.resolve(from, to));
    }

    /** Same as {@link #substitute(Object, Object)}, for callers that already resolved a {@link Substitution.Target}. */
    public void substitute(Substitution.Target t) {
        addOp(list -> {
            for (FluidRecipe fr : new ArrayList<>(list.getAllRecipes())) {
                try {
                    FluidStack newFirst = Substitution.remapFluid(fr.getFirstTank(), t);
                    FluidStack newSecond = Substitution.remapFluid(fr.getSecondTank(), t);
                    IInput newCatalyst = Substitution.remapInput(fr.getItemInput(), t);
                    var newOutput = (IFluidRecipeOutput) Substitution.remapOutput(fr.getOutput(), t);
                    boolean changed = newFirst != fr.getFirstTank() || newSecond != fr.getSecondTank()
                            || newCatalyst != fr.getItemInput() || newOutput != fr.getOutput();
                    if (changed) {
                        list.removeRecipe(fr.getLocation(), false);
                        list.addRecipe(fr.getLocation(), newFirst, newSecond, newCatalyst, newOutput);
                    }
                } catch (RuntimeException e) {
                    KubeJSIC2Classic.LOGGER.warn("[{}] refining: substitute skipped {} — {}",
                            KubeJSIC2Classic.MOD_ID, fr.getLocation(), e.toString());
                }
            }
        });
    }

    /** Set (or add) a random item-output range [min,max] on an existing recipe, by id. */
    public void setItemRange(Object id, int min, int max) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        addOp(list -> {
            FluidRecipe fr = list.getRecipe(rl);
            if (fr == null) {
                KubeJSIC2Classic.LOGGER.warn("[{}] refining: setItemRange — no such recipe {}", KubeJSIC2Classic.MOD_ID, rl);
                return;
            }
            List<ItemStack> outs = fr.getOutput().getAllOutputs();
            if (outs.isEmpty()) {
                throw new IllegalArgumentException("recipe " + rl + " has no item output to range");
            }
            IOutputGenerator gen = new ItemWithNBTGenerator(outs.get(0), outs.get(0).getCount());
            List<FluidStack> fluids = fr.getOutput().getAllFluidOutputs();
            IFluidRecipeOutput out;
            if (fluids.isEmpty()) {
                out = new RangeFluidOutput(gen, min, max, fr.getOutput().getMetadata());
            } else {
                // Preserve the fluid half's TRUE bounds if it was already ranged, instead of
                // collapsing it to a single fixed amount.
                int[] fRange = fr.getOutput() instanceof RangeFluidOutput rfo
                        ? Substitution.readFluidRange(rfo)
                        : new int[] {fluids.get(0).getAmount(), fluids.get(0).getAmount()};
                out = new RangeFluidOutput(gen, min, max, fluids.get(0), fRange[0], fRange[1], fr.getOutput().getMetadata());
            }
            list.removeRecipe(rl, false);
            list.addRecipe(rl, fr.getFirstTank(), fr.getSecondTank(), fr.getItemInput(), out);
        });
    }

    /** Set (or add) a random fluid-output range [min,max] on an existing recipe, by id. */
    public void setFluidRange(Object id, int min, int max) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        addOp(list -> {
            FluidRecipe fr = list.getRecipe(rl);
            if (fr == null) {
                KubeJSIC2Classic.LOGGER.warn("[{}] refining: setFluidRange — no such recipe {}", KubeJSIC2Classic.MOD_ID, rl);
                return;
            }
            List<FluidStack> fluids = fr.getOutput().getAllFluidOutputs();
            if (fluids.isEmpty()) {
                throw new IllegalArgumentException("recipe " + rl + " has no fluid output to range");
            }
            List<ItemStack> outs = fr.getOutput().getAllOutputs();
            IFluidRecipeOutput out;
            if (outs.isEmpty()) {
                out = new RangeFluidOutput(fluids.get(0), min, max, fr.getOutput().getMetadata());
            } else {
                // Preserve the item half's TRUE bounds if it was already ranged, instead of
                // collapsing it to a single fixed count.
                int[] iRange = fr.getOutput() instanceof RangeFluidOutput rfo
                        ? Substitution.readItemRange(rfo)
                        : new int[] {outs.get(0).getCount(), outs.get(0).getCount()};
                out = new RangeFluidOutput(new ItemWithNBTGenerator(outs.get(0), outs.get(0).getCount()), iRange[0], iRange[1], fluids.get(0), min, max, fr.getOutput().getMetadata());
            }
            list.removeRecipe(rl, false);
            list.addRecipe(rl, fr.getFirstTank(), fr.getSecondTank(), fr.getItemInput(), out);
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
        private IFluidRecipeOutput rangeOutput;

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

        /**
         * A random item-count output in [min,max] instead of a fixed one. Mutually exclusive
         * with {@link #itemOutput(Object)}; combine with {@link #fluidOutputRange} for a recipe
         * that randomizes both, or with {@link #fluidOutput} for a random item + fixed fluid.
         */
        public Builder itemOutputRange(Object item, int min, int max) {
            ItemStack stack = IC2Conversions.toItemStack(item);
            IOutputGenerator gen = new ItemWithNBTGenerator(stack, stack.getCount());
            this.rangeOutput = combineRange(gen, min, max, null, 0, 0);
            return this;
        }

        /**
         * A random fluid-amount output in [min,max] instead of a fixed one. Mutually exclusive
         * with {@link #fluidOutput(Object)}.
         */
        public Builder fluidOutputRange(Object fluid, int min, int max) {
            FluidStack stack = IC2Conversions.toFluidStack(fluid);
            this.rangeOutput = combineRange(null, 0, 0, stack, min, max);
            return this;
        }

        private IFluidRecipeOutput combineRange(IOutputGenerator itemGen, int itemMin, int itemMax,
                                                 FluidStack fluidStack, int fluidMin, int fluidMax) {
            // Merge with whichever half (item/fluid) was already set by the other *Range call.
            // getAllOutputs()/getAllFluidOutputs() only give ONE representative stack, not the
            // true min/max, so the preserved half's bounds must come from Substitution's reflection
            // readback — otherwise it silently collapses to a single fixed value (or to [0,0]).
            if (rangeOutput instanceof RangeFluidOutput existing) {
                List<ItemStack> outs = existing.getAllOutputs();
                if (itemGen == null && !outs.isEmpty()) {
                    int[] r = Substitution.readItemRange(existing);
                    itemGen = new ItemWithNBTGenerator(outs.get(0), outs.get(0).getCount());
                    itemMin = r[0];
                    itemMax = r[1];
                }
                List<FluidStack> fluids = existing.getAllFluidOutputs();
                if (fluidStack == null && !fluids.isEmpty()) {
                    int[] r = Substitution.readFluidRange(existing);
                    fluidStack = fluids.get(0);
                    fluidMin = r[0];
                    fluidMax = r[1];
                }
            }
            if (itemGen != null && fluidStack != null) {
                return new RangeFluidOutput(itemGen, itemMin, itemMax, fluidStack, fluidMin, fluidMax);
            } else if (itemGen != null) {
                return new RangeFluidOutput(itemGen, itemMin, itemMax);
            } else {
                return new RangeFluidOutput(fluidStack, fluidMin, fluidMax);
            }
        }

        /** Record the recipe (deferred; applied after IC2 loads). */
        public void build() {
            owner.record(id, first, second, catalyst, itemOut, fluidOut, rangeOutput);
        }
    }
}
