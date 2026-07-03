package com.sazdan.kubejsic2classic.event;

import java.util.LinkedHashMap;
import java.util.Map;

import com.sazdan.kubejsic2classic.recipe.MachineRecipeWrapper;
import com.sazdan.kubejsic2classic.recipe.Machines;
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
    public final MachineRecipeWrapper furnace = reg("furnace");
    public final MachineRecipeWrapper blastFurnace = reg("blastFurnace");
    public final MachineRecipeWrapper smoker = reg("smoker");
    public final MachineRecipeWrapper mixingFurnace = reg("mixingFurnace");
    public final MachineRecipeWrapper recycler = reg("recycler");

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

    private MachineRecipeWrapper reg(String name) {
        MachineRecipeWrapper wrapper = new MachineRecipeWrapper(name);
        byName.put(name, wrapper);
        return wrapper;
    }

    /** Look up the wrapper backing a given {@link Machines.Def} name. */
    public MachineRecipeWrapper machine(String name) {
        return byName.get(name);
    }
}
