package com.jvn.villagerretaliation.quest.tutorial;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side finite-work ledger. A permit must be persisted before mutating the
 * world; a receipt is committed only after success. Unresolved permits retain
 * their budget across reload until the operation is reconciled, never retried as
 * new free work. This class deliberately has no inventory, navigation or UI API.
 */
public final class TutorialRun {
    public enum State { SETUP, ACTIVE, PAUSED, AWAITING_CHOICE, CONVERTED, STOPPED, ABANDONED }
    public enum GrantState { RESERVED, DELIVERED }

    public record Binding(UUID player, UUID mentor, UUID worker, UUID assignment, UUID task,
            UUID run, int generation, String role, String dimension) {
        public Binding {
            Objects.requireNonNull(player); Objects.requireNonNull(mentor); Objects.requireNonNull(worker);
            Objects.requireNonNull(assignment); Objects.requireNonNull(task); Objects.requireNonNull(run);
            if (generation < 0 || role == null || role.isBlank() || dimension == null || dimension.isBlank())
                throw new IllegalArgumentException("Incomplete tutorial binding");
        }
    }

    /** Targets are stable identities: dimension/position, entity UUID or an exact order/job ID. */
    public record Scope(Map<String, Set<String>> targets, Map<String, Integer> limits,
            Map<String, Integer> required, String output, boolean requireAllProductsDelivered) {
        public Scope(Map<String, Set<String>> targets, Map<String, Integer> limits, Map<String, Integer> required, String output) {
            this(targets, limits, required, output, false);
        }
        public Scope {
            var copy = new LinkedHashMap<String, Set<String>>();
            targets.forEach((key, value) -> copy.put(key, Set.copyOf(value)));
            targets = Map.copyOf(copy); limits = positive(limits); required = positive(required);
            output = output == null ? "" : output;
            if (limits.isEmpty() || required.isEmpty() || !limits.keySet().containsAll(required.keySet()))
                throw new IllegalArgumentException("A trial needs bounded actions and required outcomes");
            for (var entry : limits.entrySet()) {
                if (targets.getOrDefault(entry.getKey(), Set.of()).isEmpty()
                        || required.getOrDefault(entry.getKey(), 0) > entry.getValue())
                    throw new IllegalArgumentException("Missing scope or invalid budget: " + entry.getKey());
            }
        }
        private static Map<String, Integer> positive(Map<String, Integer> values) {
            if (values.values().stream().anyMatch(value -> value == null || value <= 0 || value > 65536))
                throw new IllegalArgumentException("Tutorial quantities must be positive and finite");
            return Map.copyOf(values);
        }
    }

    public record Permit(UUID operation, Binding binding, String action, String target, int units) { }
    public record Receipt(Permit permit, String item, int quantity, String destination, long gameTime, Map<String, Integer> products) {
        public Receipt(Permit permit, String item, int quantity, String destination, long gameTime) {
            this(permit, item, quantity, destination, gameTime,
                    item != null && !item.isBlank() && quantity > 0 ? Map.of(item, quantity) : Map.of());
        }
        public Receipt {
            Objects.requireNonNull(permit);
            item = item == null ? "" : item;
            destination = destination == null ? "" : destination;
            if (quantity < 0) throw new IllegalArgumentException("Negative receipt quantity");
            products = products == null ? (item.isBlank() || quantity == 0 ? Map.of() : Map.of(item, quantity)) : Map.copyOf(products);
            if (products.values().stream().anyMatch(value -> value == null || value <= 0))
                throw new IllegalArgumentException("Invalid product quantity");
        }
    }
    public record Quote(UUID id, UUID assignment, int generation, String role, String currency,
            int durationDays, int total, String terms) {
        public Quote {
            Objects.requireNonNull(id); Objects.requireNonNull(assignment);
            if (durationDays <= 0 || total < 0 || currency == null || currency.isBlank())
                throw new IllegalArgumentException("Invalid continuation quote");
            terms = Objects.requireNonNull(terms);
        }
        public boolean samePrice(Quote other) {
            return other != null && assignment.equals(other.assignment) && generation == other.generation
                    && role.equals(other.role) && currency.equals(other.currency)
                    && durationDays == other.durationDays && total == other.total && terms.equals(other.terms);
        }
    }
    public record Snapshot(UUID trial, String tutorial, Binding binding, State state, Scope scope,
            Map<UUID, Permit> pending, Map<UUID, Receipt> receipts, Map<String, GrantState> grants,
            Set<String> learned, boolean completed, UUID paymentReceipt, Quote quote) {
        public Snapshot {
            pending = Map.copyOf(pending); receipts = Map.copyOf(receipts); grants = Map.copyOf(grants);
            learned = Set.copyOf(learned);
        }
    }

    private final UUID trial;
    private final String tutorial;
    private Binding binding;
    private State state = State.SETUP;
    private Scope scope;
    private final Map<UUID, Permit> pending = new LinkedHashMap<>();
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();
    private final Map<String, GrantState> grants = new LinkedHashMap<>();
    private final Set<String> learned = new LinkedHashSet<>();
    private boolean completed;
    private UUID paymentReceipt;
    private Quote quote;

    public TutorialRun(UUID trial, String tutorial, Binding binding) {
        this.trial = Objects.requireNonNull(trial);
        this.tutorial = Objects.requireNonNull(tutorial);
        this.binding = Objects.requireNonNull(binding);
    }

    public static TutorialRun restore(Snapshot saved) {
        var result = new TutorialRun(saved.trial, saved.tutorial, saved.binding);
        result.state = saved.state; result.scope = saved.scope;
        result.pending.putAll(saved.pending); result.receipts.putAll(saved.receipts);
        result.grants.putAll(saved.grants); result.learned.addAll(saved.learned);
        result.completed = saved.completed; result.paymentReceipt = saved.paymentReceipt; result.quote = saved.quote;
        // Malformed or newer saves cannot accidentally turn into unlimited employment.
        if (result.state == State.ACTIVE && result.scope == null) result.state = State.PAUSED;
        return result;
    }

    public Snapshot snapshot() {
        return new Snapshot(trial, tutorial, binding, state, scope, pending, receipts, grants, learned,
                completed, paymentReceipt, quote);
    }
    public Binding binding() { return binding; }
    public State state() { return state; }
    public Scope scope() { return scope; }
    public boolean completed() { return completed; }
    public UUID trial() { return trial; }

    /** Scope is approved once. Resume and worker replacement cannot replenish it. */
    public boolean activate(Binding expected, Scope approved) {
        if (!binding.equals(expected) || completed || state != State.SETUP || scope != null) return false;
        scope = Objects.requireNonNull(approved); state = State.ACTIVE; return true;
    }
    public void pause() { if (state == State.ACTIVE) state = State.PAUSED; }
    public boolean resume(Binding expected, boolean readinessRevalidated) {
        if (!binding.equals(expected) || !readinessRevalidated || state != State.PAUSED || completed || scope == null)
            return false;
        state = State.ACTIVE; return true;
    }

    public Permit reserve(Binding expected, UUID operation, String action, String target, int units) {
        if (state != State.ACTIVE || !binding.equals(expected) || operation == null || units <= 0 || scope == null
                || pending.containsKey(operation) || receipts.containsKey(operation)
                || !scope.targets.getOrDefault(action, Set.of()).contains(target)) return null;
        if (Set.of("mine", "log", "till", "plant", "harvest", "replant", "shear", "kill", "build_complete", "leaf", "place", "ingredient", "load_bottle", "brew", "patrol").contains(action)
                && (pending.values().stream().anyMatch(p -> p.action.equals(action) && p.target.equals(target))
                || receipts.values().stream().anyMatch(r -> r.permit.action.equals(action) && r.permit.target.equals(target)))) return null;
        int used = committedUnits(action) + pending.values().stream().filter(p -> p.action.equals(action))
                .mapToInt(Permit::units).sum();
        if (units > scope.limits.getOrDefault(action, 0) - used) return null;
        var permit = new Permit(operation, binding, action, target, units);
        pending.put(operation, permit); return permit;
    }

    /** Only the holder of the exact permit can confirm an operation. Replays are idempotent. */
    public boolean commit(Receipt receipt) {
        Permit permit = receipt.permit;
        Permit reserved = pending.get(permit.operation);
        boolean partialDeposit = reserved != null && Set.of("deposit","pickup").contains(permit.action)
                && reserved.binding.equals(permit.binding) && reserved.action.equals(permit.action)
                && reserved.target.equals(permit.target) && permit.units > 0 && permit.units <= reserved.units;
        if (receipts.containsKey(permit.operation) || !binding.equals(permit.binding)
                || !permit.equals(reserved) && !partialDeposit || terminal()) return false;
        if (permit.action.equals("deposit")) {
            if (receipt.quantity <= 0 || receipt.quantity != permit.units || scope.output.isBlank()
                    || !scope.output.equals(receipt.destination)
                    || receipt.quantity > produced(receipt.item) - deposited(receipt.item)) return false;
        }
        pending.remove(permit.operation); receipts.put(permit.operation, receipt);
        boolean delivered = !scope.requireAllProductsDelivered || receipts.values().stream()
                .flatMap(r -> r.products.keySet().stream()).distinct().allMatch(item -> deposited(item) >= produced(item));
        if (delivered && scope.required.entrySet().stream().allMatch(e -> committedUnits(e.getKey()) >= e.getValue())) {
            completed = true;
            state = State.AWAITING_CHOICE;
            quote = null;
        }
        return true;
    }

    /** Release only a proved non-mutating failure; unknown outcomes stay reserved. */
    public boolean failed(Permit permit) {
        return permit != null && binding.equals(permit.binding) && pending.remove(permit.operation, permit);
    }
    public int committedUnits(String action) {
        return receipts.values().stream().filter(r -> r.permit.action.equals(action)).mapToInt(r -> r.permit.units).sum();
    }
    public int produced(String item) {
        return receipts.values().stream().filter(r -> !r.permit.action.equals("deposit"))
                .mapToInt(r -> r.products.getOrDefault(item, 0)).sum();
    }
    public int deposited(String item) {
        return receipts.values().stream().filter(r -> r.permit.action.equals("deposit") && r.item.equals(item))
                .mapToInt(Receipt::quantity).sum();
    }
    public boolean learn(String step) { return step != null && !step.isBlank() && learned.add(step); }
    public boolean reserveGrant(String key) {
        return !terminal() && !completed && key != null && !key.isBlank() && grants.putIfAbsent(key, GrantState.RESERVED) == null;
    }
    public boolean deliveredGrant(String key) { return grants.replace(key, GrantState.RESERVED, GrantState.DELIVERED); }

    public boolean replaceWorker(Binding expected, UUID mentor, UUID worker, UUID assignment, UUID task,
            String dimension, boolean eligibleAndRevalidated) {
        if (!binding.equals(expected) || !eligibleAndRevalidated || terminal() || completed || !pending.isEmpty()
                || (state != State.PAUSED && state != State.SETUP)) return false;
        binding = new Binding(binding.player, mentor, worker, assignment, task, binding.run,
                Math.addExact(binding.generation, 1), binding.role, dimension);
        if(binding.role.equals("nitwit")&&scope!=null){
            var targets=new LinkedHashMap<>(scope.targets);targets.put("report",Set.of(worker.toString()));
            scope=new Scope(targets,scope.limits,scope.required,scope.output,scope.requireAllProductsDelivered);
        }
        quote = null;
        // Targets remain the original remaining task. Changing the footprint needs separate review.
        return true;
    }
    public boolean offer(Quote next) {
        if (state != State.AWAITING_CHOICE || !completed || !binding.assignment.equals(next.assignment)
                || binding.generation != next.generation || !binding.role.equals(next.role)) return false;
        quote = next; return true;
    }
    public boolean accepts(UUID quoteId, Quote current) {
        return state == State.AWAITING_CHOICE && completed && paymentReceipt == null && quote != null
                && quote.id.equals(quoteId) && quote.samePrice(current);
    }
    /** Called after the ordinary payment/contract transaction commits, never before. */
    public boolean converted(UUID quoteId, Quote current, UUID receipt) {
        if (receipt == null || !accepts(quoteId, current)) return false;
        paymentReceipt = receipt; state = State.CONVERTED; quote = null; return true;
    }
    public boolean stop(Binding expected) {
        if (!binding.equals(expected) || terminal()) return false;
        if (!pending.isEmpty()) { pause(); return false; }
        state = completed ? State.STOPPED : State.ABANDONED; quote = null; return true;
    }
    public boolean reaccept(Binding expected) {
        if (!binding.equals(expected) || state != State.ABANDONED || completed || !pending.isEmpty()) return false;
        state = scope == null ? State.SETUP : State.PAUSED; return true;
    }
    private boolean terminal() { return state == State.CONVERTED || state == State.STOPPED || state == State.ABANDONED; }
}
