package com.sazdan.kubejsic2classic.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;

import dev.latvian.mods.kubejs.fluid.FluidStackJS;
import dev.latvian.mods.kubejs.item.ItemStackJS;
import ic2.api.recipes.ingridients.generators.IOutputGenerator;
import ic2.api.recipes.ingridients.generators.ItemWithNBTGenerator;
import ic2.api.recipes.ingridients.inputs.FluidInput;
import ic2.api.recipes.ingridients.inputs.IInput;
import ic2.api.recipes.ingridients.inputs.ItemInput;
import ic2.api.recipes.ingridients.inputs.ItemStackInput;
import ic2.api.recipes.ingridients.inputs.ItemTagInput;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bridges loose JavaScript values into IC2's recipe ingredient model, keeping the item
 * <em>count</em> that KubeJS/Rhino would otherwise drop when going through a vanilla
 * {@link net.minecraft.world.item.crafting.Ingredient}.
 *
 * <p>Accepted input/output forms from scripts:
 * <ul>
 *   <li>an already-built {@link IInput} / {@link IOutputGenerator} (passed through);</li>
 *   <li>a vanilla {@link ItemStack} or anything {@link ItemStackJS#of(Object)} understands
 *       (e.g. {@code 'ic2:iron_dust'}, {@code Item.of('minecraft:diamond', 2)}) — count preserved;</li>
 *   <li>a String tag {@code '#forge:dusts/iron'} (count via the {@code "Nx "} prefix);</li>
 *   <li>a String with a leading count, KubeJS-style: {@code '4x minecraft:iron_ingot'}.</li>
 * </ul>
 */
public final class IC2Conversions {

    /** Namespace used for auto-generated recipe ids. */
    public static final String NAMESPACE = "kubejs_ic2classic";

    /** Matches an optional KubeJS-style {@code "Nx "} count prefix. */
    private static final Pattern COUNT_PREFIX = Pattern.compile("^\\s*(\\d+)\\s*x\\s*(.+)$", Pattern.CASE_INSENSITIVE);

    private IC2Conversions() {}

    // ------------------------------------------------------------------ inputs

    /** Convert a single JS value into a count-aware {@link IInput}. */
    public static IInput toInput(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("IC2 recipe input cannot be null");
        }
        if (o instanceof IInput input) {
            return input;
        }
        if (o instanceof CharSequence cs) {
            return stringToInput(cs.toString());
        }
        // ItemStack, Item, ItemLike, ingredient-json etc. -> resolve to a stack (count kept).
        ItemStack stack = ItemStackJS.of(o);
        return stackToInput(stack);
    }

    /**
     * Convert a JS value into a form IC2's {@code AdvRecipeRegistry.createFrom} accepts for
     * <em>cooking / stonecutter / smithing</em> inputs: an {@link ItemStack}, {@link ItemLike},
     * item {@link TagKey} or vanilla {@link Ingredient}. These recipes round-trip through the
     * vanilla {@link net.minecraft.world.item.crafting.RecipeManager} as a standard
     * {@code Ingredient}, so an IC2 {@link IInput} (count-aware) is <em>not</em> valid here — its
     * JSON can't be read back by the vanilla-style serializers. Count is irrelevant for these
     * single-ingredient recipe types anyway.
     */
    public static Object toVanillaInput(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("IC2 recipe input cannot be null");
        }
        if (o instanceof Ingredient || o instanceof ItemStack || o instanceof TagKey || o instanceof ItemLike) {
            return o;
        }
        if (o instanceof IInput input) {
            List<ItemStack> comps = input.getComponents();
            if (!comps.isEmpty()) {
                return comps.get(0);
            }
            throw new IllegalArgumentException("IC2 recipe input resolved to nothing: " + o);
        }
        if (o instanceof CharSequence cs) {
            String s = cs.toString().trim();
            Matcher m = COUNT_PREFIX.matcher(s);
            if (m.matches()) {
                s = m.group(2).trim();
            }
            if (s.startsWith("#")) {
                ResourceLocation id = parseId(s.substring(1));
                return TagKey.create(Registry.ITEM_REGISTRY, id);
            }
            return ItemStackJS.of(s);
        }
        return ItemStackJS.of(o);
    }

    /** Convert a whole argument list into an {@link IInput} array. */
    public static IInput[] toInputs(Object[] args) {
        IInput[] out = new IInput[args.length];
        for (int i = 0; i < args.length; i++) {
            out[i] = toInput(args[i]);
        }
        return out;
    }

    private static IInput stringToInput(String raw) {
        String s = raw.trim();
        int count = 1;
        Matcher m = COUNT_PREFIX.matcher(s);
        if (m.matches()) {
            count = Integer.parseInt(m.group(1));
            s = m.group(2).trim();
        }
        if (s.startsWith("#")) {
            ResourceLocation id = parseId(s.substring(1));
            TagKey<Item> tag = TagKey.create(Registry.ITEM_REGISTRY, id);
            return new ItemTagInput(tag, count);
        }
        ItemStack stack = ItemStackJS.of(s);
        if (m.matches()) {
            stack.setCount(count);
        }
        return stackToInput(stack);
    }

    private static IInput stackToInput(ItemStack stack) {
        int size = Math.max(1, stack.getCount());
        // NBT-exact matching only when the stack actually carries NBT; otherwise match by item.
        if (stack.hasTag()) {
            return new ItemStackInput(stack);
        }
        return new ItemInput(stack.getItem(), size);
    }

    // ----------------------------------------------------------------- outputs

    /**
     * Build the list of output generators for a recipe output value. Accepts a single item form
     * or a JS array / {@link List} of item forms (for multi-output machines).
     */
    public static List<IOutputGenerator> toGenerators(Object o) {
        List<IOutputGenerator> list = new ArrayList<>();
        collectGenerators(o, list);
        if (list.isEmpty()) {
            throw new IllegalArgumentException("IC2 recipe output resolved to nothing: " + o);
        }
        return list;
    }

    /** Single-generator output (for range recipes, which only accept one generator). */
    public static IOutputGenerator toGenerator(Object o) {
        return toGenerators(o).get(0);
    }

    private static void collectGenerators(Object o, List<IOutputGenerator> out) {
        if (o == null) {
            return;
        }
        if (o instanceof IOutputGenerator gen) {
            out.add(gen);
            return;
        }
        if (o instanceof Iterable<?> it) {
            for (Object e : it) {
                collectGenerators(e, out);
            }
            return;
        }
        if (o instanceof Object[] arr) {
            for (Object e : arr) {
                collectGenerators(e, out);
            }
            return;
        }
        ItemStack stack = toItemStack(o);
        if (!stack.isEmpty()) {
            // ItemWithNBTGenerator keeps both NBT and the stack count.
            out.add(new ItemWithNBTGenerator(stack, stack.getCount()));
        }
    }

    /** Resolve any JS value into an {@link ItemStack} (supports the {@code "Nx "} prefix on strings). */
    public static ItemStack toItemStack(Object o) {
        if (o instanceof CharSequence cs) {
            String s = cs.toString().trim();
            Matcher m = COUNT_PREFIX.matcher(s);
            if (m.matches()) {
                ItemStack stack = ItemStackJS.of(m.group(2).trim());
                stack.setCount(Integer.parseInt(m.group(1)));
                return stack;
            }
        }
        return ItemStackJS.of(o);
    }

    // ----------------------------------------------------------------- fluids

    /**
     * Resolve any JS value into a Forge {@link FluidStack}. Accepts a {@link FluidStack}, a
     * KubeJS {@code Fluid.of('id', mb)} value, or a fluid id string (amount defaults to 1000 mB).
     */
    public static FluidStack toFluidStack(Object o) {
        if (o instanceof FluidStack fs) {
            return fs;
        }
        FluidStackJS js = FluidStackJS.of(o);
        Fluid fluid = js.getFluid();
        int amount = (int) js.getAmount();
        if (amount <= 0) {
            amount = 1000;
        }
        CompoundTag nbt = js.getNbt();
        return nbt != null && !nbt.isEmpty() ? new FluidStack(fluid, amount, nbt) : new FluidStack(fluid, amount);
    }

    /** Resolve a JS value into the raw {@link Fluid} (ignoring amount). */
    public static Fluid toFluid(Object o) {
        return toFluidStack(o).getFluid();
    }

    /** Wrap a JS fluid value as a count-aware IC2 {@link FluidInput}. */
    public static FluidInput toFluidInput(Object o) {
        return new FluidInput(toFluidStack(o));
    }

    /** Registry id of a fluid. */
    public static ResourceLocation fluidId(Fluid fluid) {
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
        return id != null ? id : new ResourceLocation("minecraft", "empty");
    }

    // ----------------------------------------------------------------- effects

    /** Resolve a JS value (id string or MobEffect) into a {@link MobEffect}. */
    public static MobEffect toMobEffect(Object o) {
        if (o instanceof MobEffect effect) {
            return effect;
        }
        ResourceLocation id = parseId(o);
        MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
        if (effect == null) {
            throw new IllegalArgumentException("Unknown mob effect: " + id);
        }
        return effect;
    }

    // --------------------------------------------------------------- numbers

    public static float toFloat(Object o) {
        return o instanceof Number n ? n.floatValue() : Float.parseFloat(String.valueOf(o).trim());
    }

    public static int toInt(Object o) {
        return o instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(o).trim());
    }

    // ------------------------------------------------------------------ ids

    /** Build a deterministic, unique auto-id {@code kubejs_ic2classic:<machine>/<out>_from_<in>}. */
    public static ResourceLocation autoId(String machine, ItemStack out, ItemStack in, Map<String, Integer> counter) {
        StringBuilder path = new StringBuilder(machine).append('/');
        path.append(out.isEmpty() ? "result" : itemId(out.getItem()).getPath());
        if (in != null && !in.isEmpty()) {
            path.append("_from_").append(itemId(in.getItem()).getPath());
        }
        String base = path.toString().toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        int n = counter.merge(base, 1, Integer::sum);
        return new ResourceLocation(NAMESPACE, n == 1 ? base : base + "_" + (n - 1));
    }

    /** Parse a {@code "namespace:path"} (or bare {@code "path"}) string into a {@link ResourceLocation}. */
    public static ResourceLocation parseId(Object o) {
        String s = String.valueOf(o).trim();
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource location: " + s);
        }
        return id;
    }

    /** Registry id of an item. */
    public static ResourceLocation itemId(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id != null ? id : new ResourceLocation("minecraft", "air");
    }

    /**
     * Heuristic: is this leading argument meant to be a recipe id rather than an item/output?
     * True when it is a namespaced string ({@code has ':'}), not a tag ({@code '#...'}), and does
     * not resolve to a registered item.
     */
    public static boolean looksLikeId(Object o) {
        if (!(o instanceof CharSequence cs)) {
            return false;
        }
        String s = cs.toString().trim();
        if (s.isEmpty() || s.startsWith("#") || s.indexOf(':') < 0) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) {
            return false;
        }
        return !ForgeRegistries.ITEMS.containsKey(id);
    }
}
