package com.jvn.villagerretaliation.settlement;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;

class SettlementWork implements Runnable {
    private final Deque<Runnable> jobs = new ArrayDeque<>();

    SettlementWork(Collection<Runnable> jobs) {
        this.jobs.addAll(jobs);
    }

    static <T> Runnable each(Collection<T> values, Consumer<T> action) {
        return new Each<>(values.iterator(), action);
    }

    static Runnable repeat(java.util.function.BooleanSupplier step) {
        return new Repeat(step);
    }

    static Runnable guard(Runnable work, java.util.function.BooleanSupplier valid) {
        return new SettlementWork(java.util.List.of(work)) {
            @Override
            public void run() {
                if (valid.getAsBoolean()) super.run();
                else cancel();
            }
        };
    }

    static Runnable lazy(java.util.function.Supplier<Runnable> supplier) {
        var work = new SettlementWork(java.util.List.of());
        work.jobs.add(() -> work.jobs.addLast(supplier.get()));
        return work;
    }

    static boolean complete(Runnable work) {
        return work instanceof SettlementWork sequence ? sequence.jobs.isEmpty()
                : work instanceof Repeat repeat ? repeat.complete
                : !(work instanceof Each<?> each) || !each.values.hasNext();
    }

    void cancel() {
        this.jobs.clear();
    }

    @Override
    public void run() {
        if (this.jobs.isEmpty()) return;
        var job = this.jobs.getFirst();
        job.run();
        if (complete(job)) this.jobs.removeFirst();
    }

    private static final class Repeat implements Runnable {
        private final java.util.function.BooleanSupplier step;
        private boolean complete;

        Repeat(java.util.function.BooleanSupplier step) {
            this.step = step;
        }

        @Override
        public void run() {
            if (!this.complete) this.complete = this.step.getAsBoolean();
        }
    }

    private record Each<T>(Iterator<T> values, Consumer<T> action) implements Runnable {
        @Override
        public void run() {
            if (this.values.hasNext()) this.action.accept(this.values.next());
        }
    }
}
