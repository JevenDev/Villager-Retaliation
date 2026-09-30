package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.HiredSupplyCrafting;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionQueue;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

final class SettlementPolicyValidation {
    private SettlementPolicyValidation() { }

    static String configuration(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy) {
        String target = target(level, town, policy);
        if (!target.isEmpty()) return target;
        var graph = SettlementPolicyGraph.inspect(policy.configuration());
        if (!graph.valid()) return graph.blocker();
        var production = new java.util.HashMap<UUID, Integer>();
        var delivery = new java.util.HashMap<UUID, Integer>();
        int bindings = policy.bindings().size();
        for (var step : policy.configuration().steps()) {
            if (SettlementPolicyOrders.current(policy, step) != null) continue;
            bindings++;
            if (step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) production.merge(step.area(), 1, Integer::sum);
            else if (step.kind() == SettlementSupplyPolicy.StepKind.DELIVERY) delivery.merge(step.area(), 1, Integer::sum);
        }
        if (bindings > SettlementSupplyPolicy.MAX_BINDINGS) return "policy_binding_limit";
        for (var entry : production.entrySet()) {
            var queue = town.productionQueues().get(entry.getKey());
            if (entry.getValue() + (queue == null ? 0 : queue.orders().size()) > HiredProductionQueue.MAX_ORDERS) return "policy_queue_full";
        }
        for (var entry : delivery.entrySet()) {
            var queue = town.transferQueues().get(entry.getKey());
            if (entry.getValue() + (queue == null ? 0 : queue.orders().size())
                    > com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.MAX_ORDERS) return "policy_queue_full";
        }
        return "";
    }

    static String target(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy) {
        var target = policy.configuration().target();
        if (target.kind() == SettlementSupplyPolicy.TargetKind.STOCK) {
            boolean linked = town.depots().values().stream().anyMatch(depot -> SettlementSupplyStock.sameStorage(level, depot.position(), target.storage()))
                    || town.worksitePlans().keySet().stream().anyMatch(area -> SettlementSupplyStock.storages(level, town, area, true).stream()
                            .anyMatch(position -> SettlementSupplyStock.sameStorage(level, position, target.storage())));
            if (!linked) return "policy_destination_unlinked";
            return SettlementSupplyStock.contents(level, town, target.storage()) == null ? "storage_unavailable" : "";
        }
        if (target.kind() == SettlementSupplyPolicy.TargetKind.FOOD)
            return target.consumer().equals(town.id().value()) ? "" : "policy_consumer_missing";
        if (town.managedSupplyOrder(target.consumer()) != null) return "policy_consumer_managed";
        var project = town.projects().get(target.consumer());
        if (project != null) return project.status() == SettlementProject.Status.CANCELLED ? "policy_consumer_missing" : "";
        for (var queue : town.productionQueues().values()) {
            var order = queue.order(target.consumer());
            if (order != null) return order.cancelled() ? "policy_consumer_missing" : order.paused() ? "policy_consumer_paused" : "";
        }
        return "policy_consumer_missing";
    }

    static String step(ServerLevel level, SettlementRecord town, SettlementSupplyPolicy policy, SettlementSupplyPolicy.Step step) {
        var area = town.workplace(step.area());
        var settings = town.worksitePlans().get(step.area());
        if (area == null || !town.owns(area) || !area.enabled() || settings == null || !settings.settings().enabled())
            return "policy_worksite_unavailable";
        var output = SettlementPolicyPlanner.output(level, step);
        if (output.isEmpty()) return "recipe_unavailable";
        if (!SettlementSupplyStock.permitsOutput(level, town, step.area(), step.destination())) return "policy_destination_unlinked";
        if (step.kind() == SettlementSupplyPolicy.StepKind.DELIVERY) {
            if (area.type() != SettlementAreaType.DEPOT
                    || !SettlementTransferService.endpoints(level, town, area.id(), SettlementPolicyOrders.delivery(level, town, step, 1), null))
                return "transfer_endpoint_unavailable";
            var queue = town.transferQueues().get(area.id());
            if (SettlementPolicyOrders.current(policy, step) == null && queue != null
                    && queue.orders().size() >= com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.MAX_ORDERS)
                return "policy_queue_full";
        } else if (step.kind() == SettlementSupplyPolicy.StepKind.PRODUCTION) {
            if (!step.recipe().supports(level, area.type().role())) return "recipe_unavailable";
            var queue = town.productionQueues().get(area.id());
            if (SettlementPolicyOrders.current(policy, step) == null && queue != null && queue.orders().size() >= HiredProductionQueue.MAX_ORDERS)
                return "policy_queue_full";
            if (queue == null && area.type() == SettlementAreaType.BREWERY) {
                for (var entry : town.duties().entrySet()) {
                    if (!entry.getValue().equals(area.id())) continue;
                    var worker = town.workers().get(entry.getKey());
                    var state = level.getEntity(entry.getKey()) instanceof net.minecraft.world.entity.npc.Villager villager
                            ? com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager) : worker == null ? null : worker.settings();
                    if (state != null && com.jvn.villagerretaliation.interaction.work.brewing.BrewingWorker.hasOrder(state))
                        return "policy_legacy_production";
                }
            }
            return station(level, town, area, step);
        } else {
            if (!List.of(SettlementAreaType.FARM, SettlementAreaType.FISHERY, SettlementAreaType.HUNTING_GROUNDS,
                    SettlementAreaType.FORESTRY, SettlementAreaType.MINE, SettlementAreaType.ANIMAL_PEN).contains(area.type()))
                return "policy_gather_unavailable";
            if (settings.settings().options().getOrDefault(SettlementWorkerSetting.AUTO_DEPOSIT, 0) == 0) return "policy_deposit_disabled";
            if (policy.configuration().steps().stream().anyMatch(candidate -> candidate.kind() == SettlementSupplyPolicy.StepKind.GATHER
                    && candidate.area().equals(step.area()) && !candidate.destination().equals(step.destination())))
                return "policy_gather_destination_conflict";
            for (var other : town.supplyPolicies().values()) {
                if (other.id().equals(policy.id()) || other.state() == SettlementSupplyPolicy.State.REMOVING) continue;
                if (other.configuration().steps().stream().anyMatch(candidate -> candidate.kind() == SettlementSupplyPolicy.StepKind.GATHER
                        && candidate.area().equals(step.area()) && !candidate.destination().equals(step.destination())))
                    return "policy_gather_destination_conflict";
            }
        }
        return "";
    }

    static int capacity(ServerLevel level, SettlementRecord town, BlockPos position, ItemStack sample) {
        int loaded = AssignedStorageService.unreservedOutputCapacity(level, position, sample);
        if (loaded >= 0) return loaded;
        var depot = town.depots().values().stream().filter(value -> value.position().equals(position)).findFirst().orElse(null);
        if (depot == null || !depot.rulesObserved() || !depot.purpose().accepts(sample)) return -1;
        return Math.min(SettlementStock.capacity(List.of(depot), sample), AssignedStorageService.observedTransferAllowance(
                level, position, depot, depot.observedRules(), sample, VillagerFilterPolicy.TransferOperation.RECEIVE));
    }

    private static String station(ServerLevel level, SettlementRecord town, SettlementArea area, SettlementSupplyPolicy.Step step) {
        var links = SettlementWorksiteService.links(town, area.id());
        var construction = new SettlementConstructionMachines(level, town, null);
        if (step.recipe().recipe() == null)
            return town.brewingStands().values().stream().anyMatch(machine -> machine.area().equals(area.id())
                    && links.permitsStation(machine.position()) && SettlementBrewingStandService.available(level, machine, construction))
                    ? "" : "no_brewing_stand";
        var holder = level.getRecipeManager().byKey(step.recipe().recipe()).orElse(null);
        if (holder == null) return "recipe_unavailable";
        if (holder.value() instanceof CraftingRecipe recipe) {
            if (area.type().role() == HiredVillagerRole.COOK && !HiredSupplyCrafting.requiresCraftingTable(recipe)) return "";
            var survey = town.surveys().get(area.id());
            var blocks = new SettlementBuildingSurvey.ObservedBlocks(level, null, town);
            return survey != null && survey.fixtures().entrySet().stream().anyMatch(entry ->
                    entry.getValue().equals(ResourceLocation.withDefaultNamespace("crafting_table")) && links.permitsStation(entry.getKey())
                            && SettlementBuildingSurvey.fixtureAvailable(blocks, entry.getKey(), entry.getValue()))
                    ? "" : "no_crafting_table";
        }
        var input = holder.value().getIngredients().getFirst().getItems();
        return town.furnaces().values().stream().anyMatch(machine -> SettlementFurnaceService.serves(area, machine)
                && links.permitsStation(machine.position()) && SettlementFurnaceService.available(level, machine, construction)
                && java.util.Arrays.stream(input).anyMatch(sample -> machine.recipe(level, sample)
                        .filter(recipe -> recipe.id().equals(step.recipe().recipe())).isPresent())) ? "" : "no_furnace";
    }
}
