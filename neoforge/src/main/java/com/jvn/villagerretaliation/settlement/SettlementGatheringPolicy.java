package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementGatheringPolicy {
    private SettlementGatheringPolicy() { }

    public record Decision(boolean managed, boolean allowed, String blocker) { }

    public static Decision inspect(ServerLevel level, Villager villager) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.town().managedGathering(duty.area().id()).isEmpty()) return new Decision(false, true, "");
        if (duty.area().type() == SettlementAreaType.HUNTING_GROUNDS && HiredWorkStateStore.state(villager).getString("WorkerTaskState").equals("collecting_output"))
            return new Decision(!duty.town().managedGathering(duty.area().id()).isEmpty(), true, "");
        var inventory = HiredJobInventory.getJobInventory(villager);
        return inspect(level, duty.town(), duty.area().id(), villager.getUUID(), HiredWorkStateStore.state(villager),
                inventory.outputSlots().stream().map(inventory::getItem).toList());
    }

    static boolean hold(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        if (town.managedGathering(worker.area()).isEmpty() || worker.huntingEngaged()) return false;
        var items = worker.items(level.registryAccess());
        var carried = worker.outputSlots().stream().map(items::get).toList();
        var decision = inspect(level, town, worker.area(), worker.id(), worker.settings(), carried);
        if (decision.allowed()) return false;
        if (carried.stream().anyMatch(stack -> !stack.isEmpty())) {
            if (worker.settings().contains("AutoDepositOutputs") && !worker.settings().getBoolean("AutoDepositOutputs"))
                worker.status("policy_deposit_disabled");
            else if (!SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier,
                    SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList(), false))
                worker.status("storage_full");
        } else worker.status(decision.blocker());
        return true;
    }

    static boolean action(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, HiredJobInventory inventory) {
        if (town.managedGathering(worker.area()).isEmpty()) return true;
        var decision = inspect(level, town, worker.area(), worker.id(), worker.settings(),
                inventory.outputSlots().stream().map(inventory::getItem).toList());
        if (!decision.allowed()) worker.status(decision.blocker());
        return decision.allowed();
    }

    static Decision inspect(ServerLevel level, SettlementRecord town, UUID area, UUID worker, CompoundTag state, List<ItemStack> carried) {
        var entries = town.managedGathering(area);
        if (entries.isEmpty() || state.contains(com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue.STATE_TAG))
            return new Decision(false, true, "");
        if (busy(state)) return new Decision(true, true, "");
        var ordered = entries.stream().sorted(Comparator.comparingInt((SettlementRecord.ManagedGathering entry) ->
                SettlementPolicyService.priority(town, entry.policy())).reversed().thenComparing(entry -> entry.policy().id())
                .thenComparing(entry -> entry.step().id())).toList();
        boolean carrying = carried.stream().anyMatch(stack -> !stack.isEmpty());
        String blocker = "policy_quota_met";
        for (var entry : ordered) {
            var policy = entry.policy();
            var binding = entry.binding();
            if (!town.territory().active() || policy.state() != SettlementSupplyPolicy.State.ACTIVE || policy.blocker().equals("policy_rechecking") || !SettlementPolicyExecution.authorized(town, policy)) {
                blocker = "policy_suspended";
                continue;
            }
            if (binding == null || binding.state() != SettlementSupplyPolicy.BindingState.ACTIVE || binding.target() == 0 || binding.gathered() >= binding.ceiling()
                    || !policy.configuration().steps().contains(binding.step())) continue;
            var step = entry.step();
            var facts = SettlementSupplyStock.observe(level, town, step.destination(), step.sample(), null);
            if (!facts.known()) {
                blocker = "output_storage_unavailable";
                continue;
            }
            long previous = facts.contributors().stream().filter(contribution -> worker.equals(contribution.worker()) && contribution.kind().equals("carried"))
                    .mapToLong(SettlementSupplyStock.Contribution::quantity).sum();
            long current = step.destination().equals(HiredProductionStock.destination(state))
                    ? carried.stream().filter(stack -> HiredProductionStock.matches(step.sample(), stack)).mapToLong(ItemStack::getCount).sum() : 0;
            if (facts.stored() + facts.incoming() - previous + current >= binding.target()) continue;
            if (!SettlementSupplyStock.permitsOutput(level, town, area, step.destination())) {
                blocker = "policy_destination_unlinked";
                continue;
            }
            if (!HiredProductionStock.prepareDestination(state, carrying, step.destination())) {
                blocker = "awaiting_output_delivery";
                continue;
            }
            if (SettlementPolicyValidation.capacity(level, town, step.destination(), step.sample()) <= 0) {
                blocker = "output_full";
                continue;
            }
            return new Decision(true, true, "");
        }
        return new Decision(true, false, blocker);
    }

    public static void received(Villager villager, ItemStack sample, int amount) {
        if (amount <= 0 || !(villager.level() instanceof ServerLevel level) || level.getEntity(villager.getUUID()) != villager) return;
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty != null) received(duty.town(), duty.area().id(), HiredWorkStateStore.state(villager), sample, amount);
    }

    static List<ItemStack> before(ServerLevel level, SettlementRecord town, SettlementWorkerState worker) {
        if (town.managedGathering(worker.area()).isEmpty()) return null;
        var items = worker.items(level.registryAccess());
        return worker.outputSlots().stream().map(items::get).map(ItemStack::copy).toList();
    }

    static void received(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, List<ItemStack> before) {
        if (before == null) return;
        var previous = new java.util.ArrayList<>(before.stream().map(ItemStack::copy).toList());
        var items = worker.items(level.registryAccess());
        for (int slot : worker.outputSlots()) {
            var sample = items.get(slot);
            int added = sample.getCount();
            for (var old : previous) if (HiredProductionStock.matches(sample, old)) {
                int used = Math.min(added, old.getCount());
                added -= used;
                old.shrink(used);
            }
            if (added > 0) received(town, worker.area(), worker.settings(), sample, added);
        }
    }

    static void received(SettlementRecord town, UUID area, CompoundTag state, ItemStack sample, int amount) {
        if (amount <= 0) return;
        var destination = HiredProductionStock.destination(state);
        var entries = town.managedGathering(area).stream().filter(entry -> entry.binding() != null
                && entry.step().destination().equals(destination) && HiredProductionStock.matches(entry.step().sample(), sample))
                .sorted(Comparator.comparingInt((SettlementRecord.ManagedGathering entry) -> SettlementPolicyService.priority(town, entry.policy()))
                        .reversed().thenComparing(entry -> entry.policy().id()).thenComparing(entry -> entry.step().id())).toList();
        for (int index = 0; index < entries.size() && amount > 0; index++) {
            var entry = entries.get(index);
            var binding = entry.binding();
            int credited = index == entries.size() - 1 ? amount : (int) Math.min(amount, Math.max(0, binding.ceiling() - binding.gathered()));
            if (credited > 0) entry.policy().binding(binding.gathered(credited));
            amount -= credited;
        }
    }

    static boolean busy(CompoundTag state) {
        return state.getInt("ProgressTicks") > 0 || state.contains("FishingActiveHookId", Tag.TAG_INT)
                || state.contains("SettlementFishingCast", Tag.TAG_COMPOUND) || SettlementBackgroundFishing.hasPendingCatch(state)
                || state.getBoolean("FishingCatchCompleted") || state.hasUUID("HuntingActiveTarget")
                || com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.pending(state);
    }
}
