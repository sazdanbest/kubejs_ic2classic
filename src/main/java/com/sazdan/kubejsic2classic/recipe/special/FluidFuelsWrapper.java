package com.sazdan.kubejsic2classic.recipe.special;

import com.sazdan.kubejsic2classic.recipe.IC2Conversions;
import com.sazdan.kubejsic2classic.recipe.RecordingWrapper;
import com.sazdan.kubejsic2classic.recipe.Substitution;

import ic2.api.recipes.registries.IFluidFuelRegistry;
import net.minecraft.world.level.material.Fluid;

/**
 * {@code event.fluidFuels} — fuels burnable in IC2's fluid generators (semifluid/etc.).
 * <pre>
 *   // fluid, ticksPerBucket (burn time), euPerTick (power)
 *   event.fluidFuels.addFuel('ic2:biogas', 5000, 8)
 *   event.fluidFuels.removeFuel('ic2:biomass')
 * </pre>
 */
public class FluidFuelsWrapper extends RecordingWrapper<IFluidFuelRegistry> {

    public FluidFuelsWrapper() {
        super("fluidFuels");
    }

    public void addFuel(Object fluid, int ticksPerBucket, int euPerTick) {
        Fluid f = IC2Conversions.toFluid(fluid);
        addOp(reg -> reg.addFuel(f, ticksPerBucket, euPerTick));
    }

    public void removeFuel(Object fluid) {
        Fluid f = IC2Conversions.toFluid(fluid);
        removeOp(reg -> reg.removeFuel(f));
    }

    /** Re-register the fuel value already set for {@code from} under {@code to} instead. */
    public void substitute(Object from, Object to) {
        substitute(Substitution.resolve(from, to));
    }

    /** Same as {@link #substitute(Object, Object)}, for callers that already resolved a {@link Substitution.Target}. */
    public void substitute(Substitution.Target t) {
        if (t.fromFluid() == null) {
            return; // fuel values are keyed by fluid only — nothing here for an item-only swap
        }
        Fluid f = t.fromFluid();
        Fluid to = t.toFluid();
        addOp(reg -> {
            IFluidFuelRegistry.FuelEntry e = reg.getFuel(f);
            if (e != null) {
                reg.removeFuel(f);
                reg.addFuel(to, e.getTicksPerBucket(), e.getEuPerTick());
            }
        });
    }
}
