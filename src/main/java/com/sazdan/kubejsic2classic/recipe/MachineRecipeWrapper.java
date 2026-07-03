package com.sazdan.kubejsic2classic.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import ic2.api.recipes.ingridients.generators.IOutputGenerator;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.ingridients.recipes.ChanceRecipeOutput;
import ic2.api.recipes.ingridients.recipes.IRecipeOutput;
import ic2.api.recipes.ingridients.recipes.RangeRecipeOutput;
import ic2.api.recipes.ingridients.recipes.SimpleRecipeOutput;
import ic2.api.recipes.registries.IMachineRecipeList;
import ic2.api.recipes.registries.IMachineRecipeList.RecipeEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Script-facing handle for one IC2 machine recipe list (macerator, extractor, recycler, …),
 * exposed as {@code event.<machine>}.
 *
 * <p>Covers every plain {@link IMachineRecipeList}: single/multi output, chance, range and XP.
 * All {@code add*} calls are idempotent (they remove their id first), so replays on {@code /reload}
 * never duplicate. Application is deferred — see {@link RecordingWrapper} / {@link IC2RecipeApplier}.
 */
public class MachineRecipeWrapper extends RecordingWrapper<IMachineRecipeList> {

    /** Per-apply counter so several recipes with the same output get distinct ids. */
    private final Map<String, Integer> idCounter = new HashMap<>();

    public MachineRecipeWrapper(String machine) {
        super(machine);
    }

    // ------------------------------------------------------------------ add

    /** {@code add(output, ...inputs)} or {@code add(id, output, ...inputs)}. */
    public ResourceLocation add(Object... args) {
        Parsed p = parse(args, 0);
        return record(p.id, new SimpleRecipeOutput(gens(p.output)), p.inputs);
    }

    /** {@code addXp(output, xp, ...inputs)} or with a leading id. */
    public ResourceLocation addXp(Object... args) {
        Parsed p = parse(args, 1);
        float xp = num(p.middle[0]);
        return record(p.id, new SimpleRecipeOutput(gens(p.output), xp), p.inputs);
    }

    /** {@code addChance(output, chance, ...inputs)} — {@code chance} in [0,1]. */
    public ResourceLocation addChance(Object... args) {
        Parsed p = parse(args, 1);
        float chance = num(p.middle[0]);
        return record(p.id, new ChanceRecipeOutput(gens(p.output), chance, 0f), p.inputs);
    }

    /** {@code addRange(output, min, max, ...inputs)} — random output count in [min,max]. */
    public ResourceLocation addRange(Object... args) {
        Parsed p = parse(args, 2);
        int min = (int) num(p.middle[0]);
        int max = (int) num(p.middle[1]);
        IOutputGenerator gen = IC2Conversions.toGenerator(p.output);
        return record(p.id, new RangeRecipeOutput(gen, min, max), p.inputs);
    }

    private ResourceLocation record(ResourceLocation id, IRecipeOutput output, IInput[] inputs) {
        if (inputs.length == 0) {
            throw new IllegalArgumentException("[" + name + "] recipe needs at least one input");
        }
        ResourceLocation rl = id != null ? id : autoId(output, inputs);
        addOp(list -> {
            list.removeRecipe(rl); // idempotent: overwrite any previous same-id recipe
            list.addRecipe(rl, output, inputs);
        });
        return rl;
    }

    // --------------------------------------------------------------- remove

    /** Remove one recipe by its id (e.g. {@code 'ic2:macerator/iron_ore'}). */
    public void remove(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(list -> list.removeRecipe(rl));
    }

    /** Remove every recipe whose output contains the given item. */
    public void removeByOutput(Object output) {
        ItemStack target = IC2Conversions.toItemStack(output);
        removeOp(list -> removeMatching(list, entry -> {
            for (ItemStack out : entry.getOutput().getAllOutputs()) {
                if (ItemStack.isSame(out, target)) {
                    return true;
                }
            }
            return false;
        }));
    }

    /** Remove every recipe that accepts the given item as an input. */
    public void removeByInput(Object input) {
        ItemStack probe = IC2Conversions.toItemStack(input);
        removeOp(list -> removeMatching(list, entry -> {
            for (IInput in : entry.getInputs()) {
                if (in.matches(probe)) {
                    return true;
                }
            }
            return false;
        }));
    }

    private static void removeMatching(IMachineRecipeList list, Predicate<RecipeEntry> test) {
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (RecipeEntry entry : list.getAllEntries()) {
            if (test.test(entry)) {
                toRemove.add(entry.getLocation());
            }
        }
        for (ResourceLocation rl : toRemove) {
            list.removeRecipe(rl);
        }
    }

    // --------------------------------------------------------------- helpers

    private static List<IOutputGenerator> gens(Object output) {
        return IC2Conversions.toGenerators(output);
    }

    private static float num(Object o) {
        if (o instanceof Number n) {
            return n.floatValue();
        }
        return Float.parseFloat(String.valueOf(o));
    }

    /** Build a deterministic, unique id from the recipe's output (and first input). */
    private ResourceLocation autoId(IRecipeOutput output, IInput[] inputs) {
        String outPath = "result";
        List<ItemStack> outs = output.getAllOutputs();
        if (!outs.isEmpty()) {
            outPath = IC2Conversions.itemId(outs.get(0).getItem()).getPath();
        }
        StringBuilder path = new StringBuilder(name).append('/').append(outPath);
        if (inputs.length > 0 && !inputs[0].getComponents().isEmpty()) {
            path.append("_from_").append(IC2Conversions.itemId(inputs[0].getComponents().get(0).getItem()).getPath());
        }
        String base = sanitize(path.toString());
        int n = idCounter.merge(base, 1, Integer::sum);
        String finalPath = n == 1 ? base : base + "_" + (n - 1);
        return new ResourceLocation(IC2Conversions.NAMESPACE, finalPath);
    }

    private static String sanitize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    /**
     * Split a script call into: optional leading id, output, {@code fixedMiddle} positional args
     * (xp / chance / min,max), then the variadic inputs.
     */
    private static Parsed parse(Object[] args, int fixedMiddle) {
        int idx = 0;
        ResourceLocation id = null;
        if (args.length >= 2 + fixedMiddle && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            idx = 1;
        }
        if (args.length < idx + 1 + fixedMiddle) {
            throw new IllegalArgumentException("Not enough arguments for IC2 recipe: got " + args.length);
        }
        Object output = args[idx++];
        Object[] middle = Arrays.copyOfRange(args, idx, idx + fixedMiddle);
        idx += fixedMiddle;
        IInput[] inputs = IC2Conversions.toInputs(Arrays.copyOfRange(args, idx, args.length));
        return new Parsed(id, output, middle, inputs);
    }

    private record Parsed(ResourceLocation id, Object output, Object[] middle, IInput[] inputs) {}
}
