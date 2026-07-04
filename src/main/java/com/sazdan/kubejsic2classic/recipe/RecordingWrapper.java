package com.sazdan.kubejsic2classic.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.sazdan.kubejsic2classic.KubeJSIC2Classic;

/**
 * Base for script-facing recipe handles that <em>record</em> operations at
 * {@code IC2Events.recipes} time and replay them onto the real IC2 registry once IC2 has
 * (re)built it. See {@link IC2RecipeApplier} for why application is deferred.
 *
 * @param <X> the IC2 registry type this wrapper edits (must be {@code IListenableRegistry<X>})
 */
public abstract class RecordingWrapper<X> {

    protected final String name;
    protected final List<Consumer<X>> ops = new ArrayList<>();
    private int adds;
    private int removes;
    private int failures;

    protected RecordingWrapper(String name) {
        this.name = name;
    }

    /** Record an "add" operation. */
    protected void addOp(Consumer<X> op) {
        ops.add(op);
        adds++;
    }

    /** Record a "remove" operation. */
    protected void removeOp(Consumer<X> op) {
        ops.add(op);
        removes++;
    }

    /**
     * Replay all recorded operations onto a freshly (re)loaded IC2 registry.
     *
     * <p>Each op is isolated: if a single recipe is rejected by IC2 (e.g. an invalid
     * mixing-furnace recipe throws {@code "Recipe Is Invalid"}), we log it and carry on
     * instead of letting the exception bubble up and crash IC2's <em>entire</em> recipe
     * reload — which would silently drop every other addon recipe too.
     */
    public void applyTo(X target) {
        failures = 0;
        for (Consumer<X> op : ops) {
            try {
                op.accept(target);
            } catch (Throwable t) {
                failures++;
                KubeJSIC2Classic.LOGGER.error(
                        "[{}] {}: a recipe op was rejected by IC2 and skipped — {}",
                        KubeJSIC2Classic.MOD_ID, name, t.toString());
            }
        }
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    public int addCount() {
        return adds;
    }

    public int removeCount() {
        return removes;
    }

    /** How many ops IC2 rejected on the last {@link #applyTo}. */
    public int failureCount() {
        return failures;
    }

    public String name() {
        return name;
    }
}
