package com.sazdan.kubejsic2classic.recipe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sazdan.kubejsic2classic.KubeJSIC2Classic;

import ic2.api.recipes.ingridients.generators.IOutputGenerator;
import ic2.api.recipes.ingridients.generators.ItemWithNBTGenerator;
import ic2.api.recipes.ingridients.inputs.FluidInput;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.ingridients.inputs.ItemInput;
import ic2.api.recipes.ingridients.inputs.ItemStackInput;
import ic2.api.recipes.ingridients.inputs.ItemTagInput;
import ic2.api.recipes.ingridients.recipes.ChanceRecipeOutput;
import ic2.api.recipes.ingridients.recipes.IFluidRecipeOutput;
import ic2.api.recipes.ingridients.recipes.IRecipeOutput;
import ic2.api.recipes.ingridients.recipes.RangeFluidOutput;
import ic2.api.recipes.ingridients.recipes.RangeRecipeOutput;
import ic2.api.recipes.ingridients.recipes.SimpleFluidOutput;
import ic2.api.recipes.ingridients.recipes.SimpleRecipeOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Generic "swap this item/fluid for that one everywhere it appears, keep everything else"
 * engine shared by every registry's {@code substitute(from, to)} and {@code setRange(...)}.
 *
 * <p>Reconstructs inputs/outputs through IC2's public ingredient API ({@code getComponents()},
 * {@code getAllOutputs()}, {@code getAllFluidOutputs()}, {@code getMetadata()},
 * {@code getExperience()}, {@code ChanceRecipeOutput.getChance()}) wherever possible — no
 * guessing at field layouts. The one exception: {@link RangeRecipeOutput} and
 * {@link RangeFluidOutput} don't expose their min/max through any getter, so those two (only)
 * are read via reflection on their package-private fields, guarded and cached. If a future IC2
 * version renames those fields, the substitution of that one recipe is skipped and logged — it
 * does not crash the reload (same accepted-risk shape as {@link RefiningWrapper}'s dependency on
 * IC2 internals).
 *
 * <p>Tag-based inputs ({@link ItemTagInput}, e.g. {@code #forge:ores/iron}) are never rewritten:
 * a tag represents a whole category, and narrowing it to one item would silently change what the
 * recipe accepts. Recipe kinds this file doesn't know how to rebuild (a custom
 * {@link IRecipeOutput} subtype) are left untouched with a warning — never partially rewritten.
 */
public final class Substitution {

    private Substitution() {}

    /** What to swap. Exactly one of (fromItem, fromFluid) is set unless the id is registered as both. */
    public record Target(Item fromItem, Item toItem, Fluid fromFluid, Fluid toFluid) {}

    public static Target resolve(Object from, Object to) {
        Item fromItem = IC2Conversions.resolveItem(from);
        Item toItem = fromItem != null ? IC2Conversions.resolveItem(to) : null;
        if (fromItem != null && toItem == null) {
            throw new IllegalArgumentException("[substitute] replacement is not a registered item: " + to);
        }
        Fluid fromFluid = IC2Conversions.resolveFluid(from);
        Fluid toFluid = fromFluid != null ? IC2Conversions.resolveFluid(to) : null;
        if (fromFluid != null && toFluid == null) {
            throw new IllegalArgumentException("[substitute] replacement is not a registered fluid: " + to);
        }
        if (fromItem == null && fromFluid == null) {
            throw new IllegalArgumentException("[substitute] source is neither a registered item nor a fluid: " + from);
        }
        return new Target(fromItem, toItem, fromFluid, toFluid);
    }

    // ------------------------------------------------------------- inputs

    /** Rebuild one input if it exactly represents {@code from}; otherwise return it unchanged. */
    public static IInput remapInput(IInput in, Target t) {
        if (in == null || in instanceof ItemTagInput) {
            return in;
        }
        if (t.fromFluid() != null && in instanceof FluidInput fi) {
            Fluid f = getField(fi, "fluid");
            if (f == t.fromFluid()) {
                int amount = getIntField(fi, "fluidAmount");
                int size = getIntField(fi, "size");
                return new FluidInput(t.toFluid(), amount, size);
            }
            return in;
        }
        // Only the two public single-item IInput shapes are rebuilt — an opaque/internal IInput
        // (e.g. IC2's AlcoholInput/SubItemInput) may have matching semantics we can't introspect,
        // so it's left untouched rather than guessed at.
        if (t.fromItem() != null && (in instanceof ItemInput || in instanceof ItemStackInput)) {
            List<ItemStack> comps = in.getComponents();
            if (comps.size() == 1 && comps.get(0).getItem() == t.fromItem()) {
                if (in instanceof ItemInput) {
                    // ItemInput.matches() ignores NBT — keep that (NBT-agnostic) shape, just retarget the item.
                    return new ItemInput(t.toItem(), in.getInputSize());
                }
                // ItemStackInput.matches() requires exact NBT — preserve that, with NBT carried over.
                return new ItemStackInput(swapItem(comps.get(0), t.toItem()), in.getInputSize());
            }
        }
        return in;
    }

    public static IInput[] remapInputs(IInput[] inputs, Target t) {
        IInput[] out = new IInput[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            out[i] = remapInput(inputs[i], t);
        }
        return out;
    }

    public static boolean inputsChanged(IInput[] original, IInput[] remapped) {
        for (int i = 0; i < original.length; i++) {
            if (original[i] != remapped[i]) {
                return true;
            }
        }
        return false;
    }

    public static FluidStack remapFluid(FluidStack fs, Target t) {
        if (fs == null || fs.isEmpty() || t.fromFluid() == null || fs.getFluid() != t.fromFluid()) {
            return fs;
        }
        FluidStack copy = new FluidStack(t.toFluid(), fs.getAmount());
        if (fs.hasTag()) {
            copy.setTag(fs.getTag().copy());
        }
        return copy;
    }

    // ------------------------------------------------------------ outputs

    /**
     * Rebuild an output preserving its concrete subtype (Simple/Chance/Range, fluid or not),
     * its xp/nbt, and — for Range outputs — its min/max. Returns the same instance if {@code t}
     * doesn't match anything in it.
     */
    public static IRecipeOutput remapOutput(IRecipeOutput out, Target t) {
        boolean[] changed = {false};
        List<IOutputGenerator> items = remapItemOutputs(out.getAllOutputs(), t, changed);
        List<FluidStack> fluids = null;
        if (out instanceof IFluidRecipeOutput fro) {
            fluids = new ArrayList<>();
            for (FluidStack fs : fro.getAllFluidOutputs()) {
                FluidStack mapped = remapFluid(fs, t);
                if (mapped != fs) {
                    changed[0] = true;
                }
                fluids.add(mapped);
            }
        }
        if (!changed[0]) {
            return out;
        }

        CompoundTag nbt = out.getMetadata();
        float xp = out.getExperience();

        if (out instanceof RangeFluidOutput rfo) {
            int minV = getIntField(rfo, "minValue");
            int maxV = getIntField(rfo, "maxValue");
            int minF = getIntField(rfo, "minFluid");
            int maxF = getIntField(rfo, "maxFluid");
            IOutputGenerator itemGen = items.isEmpty() ? null : items.get(0);
            FluidStack fluidOut = fluids != null && !fluids.isEmpty() ? fluids.get(0) : FluidStack.EMPTY;
            if (itemGen != null && !fluidOut.isEmpty()) {
                return new RangeFluidOutput(itemGen, minV, maxV, fluidOut, minF, maxF, nbt);
            } else if (itemGen != null) {
                return new RangeFluidOutput(itemGen, minV, maxV, nbt);
            } else {
                return new RangeFluidOutput(fluidOut, minF, maxF, nbt);
            }
        }
        if (out instanceof RangeRecipeOutput rro) {
            int minV = getIntField(rro, "minValue");
            int maxV = getIntField(rro, "maxValue");
            IOutputGenerator itemGen = items.isEmpty() ? null : items.get(0);
            return new RangeRecipeOutput(itemGen, nbt, minV, maxV);
        }
        if (out instanceof ChanceRecipeOutput cro) {
            return new ChanceRecipeOutput(items, xp, nbt, cro.getChance());
        }
        if (out instanceof SimpleFluidOutput) {
            return new SimpleFluidOutput(items, fluids, nbt, xp);
        }
        if (out instanceof SimpleRecipeOutput) {
            return new SimpleRecipeOutput(items, nbt, xp);
        }
        KubeJSIC2Classic.LOGGER.warn(
                "[{}] substitute: don't know how to rebuild output type {}, recipe left unchanged",
                KubeJSIC2Classic.MOD_ID, out.getClass().getName());
        return out;
    }

    private static List<IOutputGenerator> remapItemOutputs(List<ItemStack> outputs, Target t, boolean[] changed) {
        List<IOutputGenerator> gens = new ArrayList<>(outputs.size());
        for (ItemStack s : outputs) {
            if (t.fromItem() != null && s.getItem() == t.fromItem()) {
                ItemStack swapped = swapItem(s, t.toItem());
                gens.add(new ItemWithNBTGenerator(swapped, swapped.getCount()));
                changed[0] = true;
            } else {
                gens.add(new ItemWithNBTGenerator(s.copy(), s.getCount()));
            }
        }
        return gens;
    }

    private static ItemStack swapItem(ItemStack orig, Item to) {
        ItemStack copy = new ItemStack(to, orig.getCount());
        if (orig.hasTag()) {
            copy.setTag(orig.getTag().copy());
        }
        return copy;
    }

    // ------------------------------------------------------- range readback
    // getAllOutputs()/getAllFluidOutputs() only ever return ONE representative stack, not the
    // true bounds — callers that need to preserve an existing range untouched (e.g. RefiningWrapper
    // merging item-range + fluid-range set in separate builder calls) must read it back here
    // instead of reconstructing min/max from that representative stack's count/amount.

    /** True [min,max] of an already-built {@link RangeRecipeOutput}'s item count. */
    public static int[] readRange(RangeRecipeOutput r) {
        return new int[] {getIntField(r, "minValue"), getIntField(r, "maxValue")};
    }

    /** True [min,max] of an already-built {@link RangeFluidOutput}'s item-count half. */
    public static int[] readItemRange(RangeFluidOutput r) {
        return new int[] {getIntField(r, "minValue"), getIntField(r, "maxValue")};
    }

    /** True [min,max] of an already-built {@link RangeFluidOutput}'s fluid-amount half. */
    public static int[] readFluidRange(RangeFluidOutput r) {
        return new int[] {getIntField(r, "minFluid"), getIntField(r, "maxFluid")};
    }

    // ---------------------------------------------------------- reflection
    // Only reached for RangeRecipeOutput / RangeFluidOutput / FluidInput, whose min/max/fluid
    // fields have no public getter in IC2's API. See class javadoc for the fallback contract.

    private static final Map<String, Field> FIELD_CACHE = new HashMap<>();

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name) {
        String key = target.getClass().getName() + "#" + name;
        try {
            Field f = FIELD_CACHE.computeIfAbsent(key, k -> {
                try {
                    Field field = target.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
            return (T) f.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("[substitute] IC2 internals changed shape for " + key, e);
        }
    }

    private static int getIntField(Object target, String name) {
        Number n = getField(target, name);
        return n.intValue();
    }
}
