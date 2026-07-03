package com.sazdan.kubejsic2classic.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.kubejs.util.MapJS;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.registries.IAdvancedCraftingManager;
import ic2.api.recipes.registries.IAdvancedCraftingManager.SmeltingType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * {@code event.crafting} — count-aware IC2 workbench crafts and cooking/stonecutter/smithing,
 * backed by {@link IAdvancedCraftingManager} ({@code RecipeRegistry.CRAFTING}).
 *
 * <p>Why this exists: KubeJS' native {@code event.shaped}/{@code event.shapeless} route through a
 * vanilla {@link net.minecraft.world.item.crafting.Ingredient}, which has no count, so
 * {@code Item.of('x', 4)} collapses to 1 in a slot. Going through IC2's {@link IInput} keeps the
 * count honest, so a slot really demands N items. Every ingredient value is turned into an
 * {@link IInput} via {@link IC2Conversions#toInput}; IC2's {@code createInputFrom} passes it
 * straight through.
 *
 * <pre>
 *   event.crafting.shaped('ic2:advanced_machine', ['PPP','PMP','PPP'], {
 *       P: Item.of('ic2:advanced_alloy', 4),   // 4 in one slot — honest, via IInput
 *       M: 'ic2:machine'
 *   })
 *   event.crafting.shapeless(Item.of('minecraft:diamond', 2), ['#forge:gems/diamond', 'ic2:machine'])
 *   event.crafting.smelting('ic2:refined_iron', 'ic2:iron_ore', 'blast_furnace')
 *   event.crafting.stonecutter('ic2:machine', 'iron_block')
 *   event.crafting.smithing('ic2:nano_chestplate', 'ic2:carbon_chestplate', 'ic2:iridium')
 *   event.crafting.remove('ic2:some_recipe_id')
 * </pre>
 *
 * <p>Application is deferred and idempotent across {@code /reload} — see {@link RecordingWrapper}
 * and {@link IC2RecipeApplier}; every {@code add} removes its id first.
 */
public class CraftingWrapper extends RecordingWrapper<IAdvancedCraftingManager> {

    /** Per-apply counter so several recipes with the same output get distinct auto-ids. */
    private final Map<String, Integer> idCounter = new HashMap<>();

    public CraftingWrapper() {
        super("crafting");
    }

    // ---------------------------------------------------------------- shaped

    /**
     * {@code shaped(output, pattern[], keys{})} or {@code shaped(id, output, pattern[], keys{})}.
     * {@code pattern} is up to 3 rows of chars; {@code keys} maps each char to an item/tag/count.
     */
    public ResourceLocation shaped(Object... args) {
        int idx = 0;
        ResourceLocation id = null;
        if (args.length >= 4 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            idx = 1;
        }
        if (args.length < idx + 3) {
            throw new IllegalArgumentException("[crafting] shaped needs output, pattern and keys");
        }
        ItemStack output = IC2Conversions.toItemStack(args[idx++]);
        List<String> rows = toPattern(args[idx++]);
        Map<?, ?> keys = toKeyMap(args[idx]);
        Object[] shapeArgs = buildShapedArgs(rows, keys);

        ResourceLocation rl = id != null ? id : autoId("shaped", output);
        addOp(mgr -> {
            mgr.removeCraftingRecipe(rl);
            mgr.addShapedRecipe(rl, output, shapeArgs);
        });
        return rl;
    }

    private static Object[] buildShapedArgs(List<String> rows, Map<?, ?> keys) {
        List<Object> out = new ArrayList<>(rows);
        for (Map.Entry<?, ?> e : keys.entrySet()) {
            String k = String.valueOf(e.getKey());
            if (k.isEmpty() || " ".equals(k)) {
                continue; // spaces are empty slots, never mapped
            }
            out.add(Character.valueOf(k.charAt(0)));
            out.add(IC2Conversions.toInput(e.getValue()));
        }
        return out.toArray();
    }

    // -------------------------------------------------------------- shapeless

    /**
     * {@code shapeless(output, [inputs...])} / {@code shapeless(output, in1, in2, ...)} or with a
     * leading explicit id.
     */
    public ResourceLocation shapeless(Object... args) {
        int idx = 0;
        ResourceLocation id = null;
        if (args.length >= 3 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            idx = 1;
        }
        if (args.length < idx + 2) {
            throw new IllegalArgumentException("[crafting] shapeless needs an output and at least one input");
        }
        ItemStack output = IC2Conversions.toItemStack(args[idx++]);
        IInput[] inputs = IC2Conversions.toInputs(flatten(Arrays.copyOfRange(args, idx, args.length)));

        ResourceLocation rl = id != null ? id : autoId("shapeless", output);
        addOp(mgr -> {
            mgr.removeCraftingRecipe(rl);
            mgr.addShapelessRecipe(rl, output, (Object[]) inputs);
        });
        return rl;
    }

    // --------------------------------------------------------------- cooking

    /**
     * {@code smelting(output, input [, type ...] [, xp] [, cookTime])}. Types are any of
     * {@code furnace / blast_furnace / smoker / campfire} (default {@code furnace}); several may be
     * given to register the recipe for all of them at once.
     */
    public ResourceLocation smelting(Object... args) {
        int idx = 0;
        ResourceLocation id = null;
        if (args.length >= 3 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            idx = 1;
        }
        if (args.length < idx + 2) {
            throw new IllegalArgumentException("[crafting] smelting needs an output and an input");
        }
        ItemStack output = IC2Conversions.toItemStack(args[idx++]);
        // Cooking uses a vanilla Ingredient (round-trips through RecipeManager), not an IC2 IInput.
        Object input = IC2Conversions.toVanillaInput(args[idx++]);

        List<SmeltingType> types = new ArrayList<>();
        List<Float> numbers = new ArrayList<>();
        for (; idx < args.length; idx++) {
            Object a = args[idx];
            if (a instanceof Number n) {
                numbers.add(n.floatValue());
            } else {
                types.add(smeltingType(a));
            }
        }
        SmeltingType[] typeArr = types.isEmpty()
                ? new SmeltingType[] {SmeltingType.FURNACE}
                : types.toArray(new SmeltingType[0]);
        float xp = numbers.isEmpty() ? 0f : numbers.get(0);
        Float time = numbers.size() >= 2 ? numbers.get(1) : null;

        ResourceLocation rl = id != null ? id : autoId("smelting", output);
        addOp(mgr -> {
            mgr.removeSmeltingRecipe(rl, typeArr);
            if (time != null) {
                mgr.addSmeltingRecipe(rl, output, input, xp, time, typeArr);
            } else if (!numbers.isEmpty()) {
                mgr.addSmeltingRecipe(rl, output, input, xp, typeArr);
            } else {
                mgr.addSmeltingRecipe(rl, output, input, typeArr);
            }
        });
        return rl;
    }

    // ------------------------------------------------------------ stonecutter

    /** {@code stonecutter(output, input)} or with a leading explicit id. */
    public ResourceLocation stonecutter(Object... args) {
        int idx = 0;
        ResourceLocation id = null;
        if (args.length >= 3 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            idx = 1;
        }
        if (args.length < idx + 2) {
            throw new IllegalArgumentException("[crafting] stonecutter needs an output and an input");
        }
        ItemStack output = IC2Conversions.toItemStack(args[idx++]);
        Object input = IC2Conversions.toVanillaInput(args[idx]);

        ResourceLocation rl = id != null ? id : autoId("stonecutter", output);
        addOp(mgr -> {
            mgr.removeStoneCutterRecipe(rl);
            mgr.addStoneCutterRecipe(rl, output, input);
        });
        return rl;
    }

    // --------------------------------------------------------------- smithing

    /** {@code smithing(output, base, addition)} or with a leading explicit id. */
    public ResourceLocation smithing(Object... args) {
        int idx = 0;
        ResourceLocation id = null;
        if (args.length >= 4 && IC2Conversions.looksLikeId(args[0])) {
            id = IC2Conversions.parseId(args[0]);
            idx = 1;
        }
        if (args.length < idx + 3) {
            throw new IllegalArgumentException("[crafting] smithing needs an output, a base and an addition");
        }
        ItemStack output = IC2Conversions.toItemStack(args[idx++]);
        Object base = IC2Conversions.toVanillaInput(args[idx++]);
        Object addition = IC2Conversions.toVanillaInput(args[idx]);

        ResourceLocation rl = id != null ? id : autoId("smithing", output);
        addOp(mgr -> {
            mgr.removeSmithingRecipe(rl);
            mgr.addSmithingRecipe(rl, base, addition, output);
        });
        return rl;
    }

    // ----------------------------------------------------------------- remove

    /** Remove one shaped/shapeless IC2 crafting recipe by id. */
    public void remove(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(mgr -> mgr.removeCraftingRecipe(rl));
    }

    /** Remove an IC2 smelting recipe by id; pass the same type(s) it was registered with. */
    public void removeSmelting(Object id, Object... typeNames) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        SmeltingType[] types = typeNames.length == 0
                ? new SmeltingType[] {SmeltingType.FURNACE}
                : Arrays.stream(typeNames).map(CraftingWrapper::smeltingType).toArray(SmeltingType[]::new);
        removeOp(mgr -> mgr.removeSmeltingRecipe(rl, types));
    }

    /** Remove an IC2 stonecutter recipe by id. */
    public void removeStonecutter(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(mgr -> mgr.removeStoneCutterRecipe(rl));
    }

    /** Remove an IC2 smithing recipe by id. */
    public void removeSmithing(Object id) {
        ResourceLocation rl = IC2Conversions.parseId(id);
        removeOp(mgr -> mgr.removeSmithingRecipe(rl));
    }

    // ----------------------------------------------------------------- helpers

    private static List<String> toPattern(Object o) {
        List<String> rows = new ArrayList<>();
        if (o instanceof CharSequence cs) {
            rows.add(cs.toString());
            return rows;
        }
        List<?> list = ListJS.of(o);
        if (list == null) {
            throw new IllegalArgumentException("[crafting] shaped pattern must be an array of strings, got: " + o);
        }
        for (Object row : list) {
            rows.add(String.valueOf(row));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("[crafting] shaped pattern is empty");
        }
        return rows;
    }

    private static Map<?, ?> toKeyMap(Object o) {
        Map<?, ?> map = MapJS.of(o);
        if (map == null) {
            throw new IllegalArgumentException("[crafting] shaped keys must be an object, got: " + o);
        }
        return map;
    }

    /** If a single {@code List}/array was passed, expand it into the argument array. */
    private static Object[] flatten(Object[] args) {
        if (args.length == 1) {
            List<?> list = ListJS.of(args[0]);
            if (list != null) {
                return list.toArray();
            }
        }
        return args;
    }

    private static SmeltingType smeltingType(Object o) {
        if (o instanceof SmeltingType t) {
            return t;
        }
        String s = String.valueOf(o).trim().toUpperCase().replace(" ", "_");
        switch (s) {
            case "FURNACE":
                return SmeltingType.FURNACE;
            case "SMOKER":
                return SmeltingType.SMOKER;
            case "BLAST":
            case "BLASTFURNACE":
            case "BLAST_FURNACE":
                return SmeltingType.BLASTFURNACE;
            case "CAMPFIRE":
                return SmeltingType.CAMPFIRE;
            default:
                throw new IllegalArgumentException("[crafting] unknown smelting type: " + o
                        + " (use furnace / blast_furnace / smoker / campfire)");
        }
    }

    private ResourceLocation autoId(String kind, ItemStack output) {
        String out = output.isEmpty() ? "result" : IC2Conversions.itemId(output.getItem()).getPath();
        String base = ("crafting/" + kind + "/" + out).toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        int n = idCounter.merge(base, 1, Integer::sum);
        return new ResourceLocation(IC2Conversions.NAMESPACE, n == 1 ? base : base + "_" + (n - 1));
    }
}
