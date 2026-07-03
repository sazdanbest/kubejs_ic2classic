package com.sazdan.kubejsic2classic.recipe.special;

import java.util.HashMap;
import java.util.Map;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;

import ic2.api.recipes.registries.IRareEarthRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.rareEarth} — the rare-earth centrifuge: input items (with a weight) roll one of the
 * registered outputs.
 * <pre>
 *   event.rareEarth.addOutput('ic2:tiny_iridium')          // add to the output pool
 *   event.rareEarth.addInput(1.0, 'minecraft:prismarine_shard')  // input + weight
 *   event.rareEarth.removeInput('minecraft:clay_ball')
 * </pre>
 */
public class RareEarthWrapper extends RecordingWrapper<IRareEarthRegistry> {

    private final Map<String, Integer> idCounter = new HashMap<>();

    public RareEarthWrapper() {
        super("rareEarth");
    }

    /** {@code addOutput(item)} or {@code addOutput(id, item)} — register an output in the pool. */
    public ResourceLocation addOutput(Object... args) {
        int i = 0;
        ResourceLocation id = null;
        if (args.length == 2 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            i = 1;
        }
        if (args.length < i + 1) {
            throw new IllegalArgumentException("[rareEarth] addOutput needs an item");
        }
        ItemStack output = IC2Conversions.toItemStack(args[i]);
        ResourceLocation rl = id != null ? id : autoId("out", output);
        addOp(reg -> reg.registerOutput(rl, output));
        return rl;
    }

    /** {@code addInput(weight, ...items)} or {@code addInput(id, weight, ...items)}. */
    public ResourceLocation addInput(Object... args) {
        int i = 0;
        ResourceLocation id = null;
        if (args.length >= 3 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            i = 1;
        }
        if (args.length < i + 2) {
            throw new IllegalArgumentException("[rareEarth] addInput needs (weight, item...)");
        }
        float weight = IC2Conversions.toFloat(args[i++]);
        ItemStack[] items = new ItemStack[args.length - i];
        for (int k = 0; k < items.length; k++) {
            items[k] = IC2Conversions.toItemStack(args[i + k]);
        }
        ResourceLocation rl = id != null ? id : autoId("in", items[0]);
        addOp(reg -> reg.registerInput(rl, weight, items));
        return rl;
    }

    /** Remove one entry by id. */
    public void remove(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(reg -> reg.remove(rl));
    }

    /** Remove the input entry matching the given item. */
    public void removeInput(Object item) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        removeOp(reg -> reg.removeInput(stack));
    }

    /** Remove an output from the pool. */
    public void removeOutput(Object item) {
        ItemStack stack = IC2Conversions.toItemStack(item);
        removeOp(reg -> reg.removeOutput(stack));
    }

    private ResourceLocation autoId(String kind, ItemStack stack) {
        String base = ("rare_earth/" + kind + "_" + IC2Conversions.itemId(stack.getItem()).getPath())
                .toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        int n = idCounter.merge(base, 1, Integer::sum);
        return new ResourceLocation(IC2Conversions.NAMESPACE, n == 1 ? base : base + "_" + (n - 1));
    }
}
