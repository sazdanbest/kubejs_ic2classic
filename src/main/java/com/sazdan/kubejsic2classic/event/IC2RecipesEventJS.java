package com.sazdan.kubejsic2classic.event;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sazdan.kubejsic2classic.recipe.CraftingWrapper;
import com.sazdan.kubejsic2classic.recipe.MachineRecipeWrapper;
import com.sazdan.kubejsic2classic.recipe.Machines;
import com.sazdan.kubejsic2classic.recipe.Substitution;
import com.sazdan.kubejsic2classic.recipe.special.CannerWrapper;
import com.sazdan.kubejsic2classic.recipe.special.ElectrolyzerWrapper;
import com.sazdan.kubejsic2classic.recipe.special.FluidFuelsWrapper;
import com.sazdan.kubejsic2classic.recipe.special.FusionWrapper;
import com.sazdan.kubejsic2classic.recipe.special.PotionBrewWrapper;
import com.sazdan.kubejsic2classic.recipe.special.RareEarthWrapper;
import com.sazdan.kubejsic2classic.recipe.special.RefiningWrapper;
import com.sazdan.kubejsic2classic.recipe.special.ScrapboxWrapper;
import com.sazdan.kubejsic2classic.recipe.special.UuMatterWrapper;

import dev.latvian.mods.kubejs.event.EventJS;

/**
 * The object passed to {@code IC2Events.recipes(event => { ... })}.
 *
 * <p>Fields are handles to IC2's recipe registries, e.g.
 * {@code event.macerator.add('ic2:iron_dust', 'minecraft:iron_ore')} or
 * {@code event.electrolyzer.addCharge(input, output, energy)}. Calls only <em>record</em>
 * operations; they are replayed onto the real IC2 lists from IC2's reload listener (see
 * {@code IC2RecipeApplier}). A fresh instance is created every server (re)load.
 */
public class IC2RecipesEventJS extends EventJS {

    // Plain IMachineRecipeList machines (incl. recycler) — see Machines.ALL.
    private final Map<String, MachineRecipeWrapper> byName = new LinkedHashMap<>();

    public final MachineRecipeWrapper macerator = reg("macerator");
    public final MachineRecipeWrapper extractor = reg("extractor");
    public final MachineRecipeWrapper compressor = reg("compressor");
    public final MachineRecipeWrapper sawmill = reg("sawmill");
    public final MachineRecipeWrapper massFabricator = reg("massFabricator");
    public final MachineRecipeWrapper mixingFurnace = reg("mixingFurnace");
    public final MachineRecipeWrapper recycler = reg("recycler");
    // No furnace/blastFurnace/smoker: those IC2 machines mirror vanilla cooking recipes (read-only).
    // Use event.crafting.smelting(out, in, 'furnace' | 'blast_furnace' | 'smoker') instead.

    // Registries with bespoke APIs.
    public final ElectrolyzerWrapper electrolyzer = new ElectrolyzerWrapper();
    public final ScrapboxWrapper scrapbox = new ScrapboxWrapper();
    public final FusionWrapper fusion = new FusionWrapper();
    public final UuMatterWrapper uuMatter = new UuMatterWrapper();
    public final CannerWrapper canner = new CannerWrapper();
    public final FluidFuelsWrapper fluidFuels = new FluidFuelsWrapper();
    public final RefiningWrapper refining = new RefiningWrapper();
    public final RareEarthWrapper rareEarth = new RareEarthWrapper();
    public final PotionBrewWrapper potionBrewing = new PotionBrewWrapper();

    // Count-aware workbench crafts + cooking/stonecutter/smithing (RecipeRegistry.CRAFTING).
    public final CraftingWrapper crafting = new CraftingWrapper();

    private MachineRecipeWrapper reg(String name) {
        MachineRecipeWrapper wrapper = new MachineRecipeWrapper(name);
        byName.put(name, wrapper);
        return wrapper;
    }

    /** Look up the wrapper backing a given {@link Machines.Def} name. */
    public MachineRecipeWrapper machine(String name) {
        return byName.get(name);
    }

    /**
     * One-line "swap this item/fluid for that one everywhere it's used in production" —
     * covers the 7 plain machines (macerator/extractor/compressor/sawmill/massFabricator/
     * mixingFurnace/recycler), the Refinery ({@code refining}: main/second fluid, catalyst,
     * item/fluid output) and generator fuel values ({@code fluidFuels}). Every other input,
     * output, chance, random range and NBT on each matched recipe stays exactly as it was.
     *
     * <p>Not covered (call these registries' own methods instead): electrolyzer, scrapbox,
     * fusion, uuMatter, canner, rareEarth, potionBrewing, crafting — each has a bespoke recipe
     * shape that doesn't fit this generic engine. Tag-based inputs (e.g. {@code #forge:ores/iron})
     * are never rewritten by design (see {@code Substitution}).
     */
    public void substitute(Object from, Object to) {
        Substitution.Target t = Substitution.resolve(from, to);
        for (MachineRecipeWrapper w : byName.values()) {
            w.substitute(t);
        }
        refining.substitute(t);
        fluidFuels.substitute(t);
    }
}
