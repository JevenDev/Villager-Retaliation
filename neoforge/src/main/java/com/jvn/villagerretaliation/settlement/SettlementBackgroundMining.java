package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
import com.jvn.villagerretaliation.interaction.HiredMiningMode;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.HiredWorkPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules;
import com.jvn.villagerretaliation.interaction.work.mining.MiningVeinPlan;
import com.jvn.villagerretaliation.interaction.work.mining.MiningWorkerState;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;

final class SettlementBackgroundMining {
    static final String POSITION = "SettlementMiningPosition";
    private static final String DEPOSIT = "SettlementMiningDepositPending";
    private static final int MAX_ACTIONS = 32;

    private SettlementBackgroundMining() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        if (SettlementGatheringPolicy.hold(level, town, worker, elapsed, multiplier)) return;
        var before = SettlementGatheringPolicy.before(level, town, worker);
        try {
            workTick(level, town, worker, elapsed, multiplier);
        } finally {
            SettlementGatheringPolicy.received(level, town, worker, before);
        }
    }

    private static void workTick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var area = town.areas().get(worker.area());
        var mine = town.mines().get(worker.area());
        if (mine == null || area == null || !mine.matches(area)) {
            worker.status("no_observed_mine");
            return;
        }
        if (mine.conflict()) {
            worker.status("mine_conflict");
            return;
        }
        var state = worker.settings();
        if (HiredMiningMode.fromState(state).excavatesArea() && !mine.fullyObserved(level.getMinBuildHeight(), level.getMaxBuildHeight())) {
            worker.status("excavation_needs_observation");
            return;
        }
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        boolean autoDeposit = !state.contains("AutoDepositOutputs") || state.getBoolean("AutoDepositOutputs");
        if (autoDeposit && (state.getBoolean(DEPOSIT) || worker.status().equals("output_full"))
                && SettlementBackgroundHauling.depositCarried(level, town, worker, elapsed, multiplier, depots, false)) return;
        var owner = worker.context(level, town);
        if (owner == null) {
            worker.status("awaiting_worker_observation");
            return;
        }
        var before = state.copy();
        var inventory = HiredJobInventory.getJobInventory(owner);
        var context = new HiredWorkContext(inventory, state, area.center(), area.min(), area.max(), 64, 32, true,
                worker.aptitude(), HiredVillagerRoles.roleActionSpeedPercent(HiredVillagerRole.MINING, worker.aptitude()),
                100, 1, false, false);
        try {
            MiningWorkerState.prepareBackground(context);
            state.remove(DEPOSIT);
            var budget = new SettlementWorkBudget(worker, elapsed, multiplier);
            if (enter(worker, mine, area, budget)) work(level, town, worker, mine, owner, context, depots, budget);
        } catch (SettlementMiningView.Unavailable unavailable) {
            worker.status("excavation_needs_observation");
        } finally {
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory()) || !before.equals(state)) worker.changed();
            worker.inventorySnapshot(snapshot);
            SettlementMinePhysics.advance(level, mine);
            SettlementMineService.refresh(level, mine);
        }
    }

    private static boolean enter(SettlementWorkerState worker, SettlementMine mine, SettlementArea area, SettlementWorkBudget budget) {
        var state = worker.settings();
        var entry = worker.miningEntry(mine, area);
        if (state.contains(POSITION) && SettlementMiningRoutes.safe(mine, BlockPos.of(state.getLong(POSITION)))) return true;
        if (SettlementMiningRoutes.safe(mine, worker.position())) {
            state.remove(POSITION);
            return true;
        }
        if (entry == null) {
            worker.status("no_safe_mining_route");
            return false;
        }
        if (!budget.spend(16.0D * Math.sqrt(worker.position().distSqr(entry)))) {
            worker.status("returning_to_mine");
            return false;
        }
        state.putLong(POSITION, entry.asLong());
        return true;
    }

    private static void work(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementMine mine, Villager owner,
                             HiredWorkContext context, List<SettlementDepot> depots, SettlementWorkBudget budget) {
        var routeBudget = new SettlementMiningRoutes.Budget();
        var mode = HiredMiningMode.fromState(context.state());
        var view = new SettlementMiningView(level, mine);
        for (int action = 0; action < MAX_ACTIONS; action++) {
            if (!SettlementGatheringPolicy.action(level, town, worker, context.inventory())) return;
            SettlementMinePhysics.advance(level, mine);
            var state = context.state();
            BlockPos origin = state.contains(POSITION) ? BlockPos.of(state.getLong(POSITION)) : worker.position();
            if (!SettlementMiningRoutes.safe(mine, origin)) origin = worker.position();
            if (!SettlementMiningRoutes.safe(mine, origin)) {
                worker.status("no_safe_mining_route");
                return;
            }
            if (mode.usesExcavationShaft()) {
                var hazard = SettlementMiningHazards.tick(level, town, worker, mine, view, owner, context, origin, depots, budget, routeBudget);
                if (hazard == SettlementMiningHazards.Result.PROGRESSED) continue;
                if (hazard == SettlementMiningHazards.Result.WAITING) return;
            }
            Integer layer = mode.usesExcavationShaft() ? MiningBlockRules.currentExcavationLayer(mine, mine::observed, context) : null;
            if (mode.usesExcavationShaft()) {
                var support = SettlementMiningSupports.tick(level, town, worker, mine, view, owner, context, origin, layer, depots, budget, routeBudget);
                if (support == SettlementMiningSupports.Result.PROGRESSED) continue;
                if (support == SettlementMiningSupports.Result.WAITING) return;
            }
            boolean floorDeferred = false;
            if (mode.excavatesHorizontally() && com.jvn.villagerretaliation.interaction.work.mining.MiningHorizontalOptions.patchFloor(state)) {
                var floor = SettlementMiningFloor.tick(level, town, worker, mine, owner, context, origin, depots, budget, routeBudget);
                if (floor == SettlementMiningFloor.Result.PROGRESSED) continue;
                if (floor == SettlementMiningFloor.Result.WAITING) return;
                floorDeferred = floor == SettlementMiningFloor.Result.DEFERRED;
            }
            var routes = SettlementMiningRoutes.forWorker(worker, mine, origin);
            var target = mode.excavatesHorizontally()
                    ? SettlementHorizontalExcavation.target(worker, mine, owner, origin, context, routes, routeBudget)
                    : mode.usesExcavationShaft()
                    ? SettlementVerticalExcavation.target(worker, mine, view, owner, origin, context, layer, routes, routeBudget)
                    : target(worker, mine, owner, context, routes, routeBudget);
            if (target == null) {
                if (floorDeferred && worker.status().equals("excavation_complete")) worker.status("floor_unreachable");
                return;
            }
            var block = mine.getBlockState(target.position());
            var equipment = HiredEquipmentNeed.mining(mode, block);
            equipment.remember(state);
            var tool = SettlementBackgroundTools.equipTool(worker, context.inventory(),
                    equipment,
                    stack -> MiningBlockRules.destroySpeed(stack, block));
            if (tool.isEmpty()) {
                SettlementBackgroundTools.fetchTool(level, town, worker, context.inventory(), depots,
                        equipment,
                        stack -> MiningBlockRules.destroySpeed(stack, block), MiningBlockRules.requiredMiningToolLabel(mode, block), budget);
                return;
            }
            int progress = state.contains("ActiveWorkBlockPos") && state.getLong("ActiveWorkBlockPos") == target.position().asLong()
                    ? context.progressTicks() : 0;
            double cost = 16.0D * target.steps()
                    + Math.max(1, MiningBlockRules.breakTicks(mine, target.position(), tool, context.skillWorkSpeedPercent()) - progress);
            if (!ready(worker, target, budget, cost, mode.excavatesArea() ? "approaching_excavation" : "approaching_ore", "mining")) return;
            owner.setPos(target.stance().getBottomCenter());
            var drops = Block.getDrops(block, level, target.position(), null, owner, tool);
            var updated = SettlementBackgroundTools.items(context.inventory());
            if (!SettlementProductionInventory.insertOutputs(worker, updated, drops)) {
                worker.status("output_full");
                return;
            }
            var section = mine.sections().get(SettlementTerrainSection.sectionOrigin(target.position()));
            if (!section.extractOre(level, target.position(), tool)
                    && !mine.change(Map.of(target.position(), block.getFluidState().createLegacyBlock()))) {
                worker.status("mine_conflict");
                return;
            }
            budget.spend(cost);
            SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
            var equipped = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
            if (!VillagerEquipmentDurability.mineBlock(equipped, level, block, target.position(), owner)) {
                VillagerEquipmentDurability.hurtTool(equipped, owner, EquipmentSlot.MAINHAND);
            }
            context.inventory().syncMainHandEquipment();
            if (mine.getBlockState(target.position()).getBlock() == block.getBlock()) context.setProgressTicks(0);
            else MiningWorkerState.mined(level, context, target.position());
            BlockPos stance = target.stance();
            if (!com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isSafePathPosition(mine, mine::observed, stance)
                    && com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isSafePathPosition(mine, mine::observed, stance.below())) {
                stance = stance.below();
            }
            state.putLong(POSITION, stance.asLong());
            state.putBoolean(DEPOSIT, true);
            worker.practice(level, HiredWorkPractice.mining(mine, target.position(), block));
            worker.status(mode.excavatesArea() ? "excavated_block" : "mined_ore");
        }
    }

    static boolean ready(SettlementWorkerState worker, SettlementMiningRoutes.Target target, SettlementWorkBudget budget,
                         double cost, String approaching, String working) {
        if (budget.available() >= cost) return true;
        int steps = Math.min(target.steps(), (int) (budget.available() / 16.0D));
        if (steps > 0) {
            budget.spend(16.0D * steps);
            worker.settings().putLong(POSITION, target.path().get(steps).asLong());
        } else {
            budget.spend(target.steps() > 0 ? 16.0D : cost);
        }
        worker.status(target.steps() > steps ? approaching : working);
        return false;
    }

    private static SettlementMiningRoutes.Target target(SettlementWorkerState worker, SettlementMine mine, Villager owner,
                                                         HiredWorkContext context, SettlementMiningRoutes routes, SettlementMiningRoutes.Budget budget) {
        HiredWorkPlan.retainMatching(context, position -> mine.contains(position)
                && MiningBlockRules.isMineableOre(mine, mine::observed, position), 20);
        for (var position : HiredWorkPlan.targets(context)) {
            var target = routes.target(owner, position, budget);
            if (target != null) return target;
            if (routes.pending(position)) {
                worker.status("searching_mining_route");
                return null;
            }
        }
        var scan = HiredWorkAreaScan.collect(context, "SettlementMiningScanCursor", 4096,
                position -> mine.observed(position) && MiningBlockRules.isMineableOre(mine, mine::observed, position));
        var reachable = new LinkedHashMap<BlockPos, SettlementMiningRoutes.Target>();
        for (var position : scan.candidates()) {
            var target = routes.target(owner, position, budget);
            if (target != null) reachable.put(position, target);
            if (routes.pending(position)) {
                if (reachable.isEmpty()) {
                    HiredWorkPlan.replaceWithObjective(context, "ore", position, List.of(position), 20);
                    worker.status("searching_mining_route");
                    return null;
                }
                break;
            }
        }
        var vein = MiningVeinPlan.best(mine, worker.position(), List.copyOf(reachable.keySet()), 20);
        HiredWorkPlan.replaceWithObjective(context, vein.size() > 1 ? "vein" : "ore",
                vein.isEmpty() ? null : vein.getFirst(), vein, 20);
        if (vein.isEmpty()) {
            worker.status(!scan.completedFullPass() ? "searching_mine" : scan.candidates().isEmpty() ? "waiting_for_ore" : "no_safe_mining_route");
            return null;
        }
        return reachable.get(vein.getFirst());
    }
}
