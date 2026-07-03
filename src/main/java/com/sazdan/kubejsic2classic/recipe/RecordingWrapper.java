package com.sazdan.kubejsic2classic.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

    /** Replay all recorded operations onto a freshly (re)loaded IC2 registry. */
    public void applyTo(X target) {
        for (Consumer<X> op : ops) {
            op.accept(target);
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

    public String name() {
        return name;
    }
}
