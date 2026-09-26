package com.jvn.villagerretaliation.settlement;

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
    private static final String POSITION = "SettlementMiningPosition";
    private static final String DEPOSIT = "SettlementMiningDepositPending";
    private static final int MAX_ACTIONS = 32;

    private SettlementBackgroundMining() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
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
            work(level, worker, mine, owner, context, depots, new SettlementWorkBudget(worker, elapsed, multiplier));
        } finally {
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory()) || !before.equals(state)) worker.changed();
            worker.inventorySnapshot(snapshot);
            SettlementMineService.refresh(level, mine);
        }
    }

    private static void work(ServerLevel level, SettlementWorkerState worker, SettlementMine mine, Villager owner,
                             HiredWorkContext context, List<SettlementDepot> depots, SettlementWorkBudget budget) {
        for (int action = 0; action < MAX_ACTIONS; action++) {
            var state = context.state();
            BlockPos origin = state.contains(POSITION) ? BlockPos.of(state.getLong(POSITION)) : worker.position();
            var routes = SettlementMiningRoutes.reachable(mine, origin);
            if (routes.isEmpty() && !origin.equals(worker.position())) routes = SettlementMiningRoutes.reachable(mine, worker.position());
            if (routes.isEmpty()) {
                worker.status("no_safe_mining_route");
                return;
            }
            var target = target(worker, mine, owner, context, routes);
            if (target == null) return;
            var block = mine.getBlockState(target.position());
            var tool = context.inventory().equipBestTool(
                    stack -> MiningBlockRules.isUsableMiningTool(HiredMiningMode.EXPOSED_ORES, stack, block),
                    stack -> MiningBlockRules.destroySpeed(stack, block));
            if (tool.isEmpty()) {
                SettlementBackgroundTools.fetchTool(level, worker, context.inventory(), depots,
                        stack -> MiningBlockRules.isUsableMiningTool(HiredMiningMode.EXPOSED_ORES, stack, block),
                        stack -> MiningBlockRules.destroySpeed(stack, block), "pickaxe", budget);
                return;
            }
            int progress = state.contains("ActiveWorkBlockPos") && state.getLong("ActiveWorkBlockPos") == target.position().asLong()
                    ? context.progressTicks() : 0;
            double cost = 16.0D * target.steps()
                    + Math.max(1, MiningBlockRules.breakTicks(mine, target.position(), tool, context.skillWorkSpeedPercent()) - progress);
            if (budget.available() < cost) {
                budget.spend(cost);
                worker.status(target.steps() > 0 ? "approaching_ore" : "mining");
                return;
            }
            owner.setPos(target.stance().getBottomCenter());
            var drops = Block.getDrops(block, level, target.position(), null, owner, tool);
            var updated = SettlementBackgroundTools.items(context.inventory());
            if (!SettlementProductionInventory.insertOutputs(worker, updated, drops)) {
                worker.status("output_full");
                return;
            }
            if (!mine.change(Map.of(target.position(), block.getFluidState().createLegacyBlock()))) {
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
            MiningWorkerState.mined(level, context, target.position());
            BlockPos stance = target.stance();
            if (!com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isSafePathPosition(mine, mine::observed, stance)
                    && com.jvn.villagerretaliation.interaction.work.mining.MiningSafety.isSafePathPosition(mine, mine::observed, stance.below())) {
                stance = stance.below();
            }
            state.putLong(POSITION, stance.asLong());
            state.putBoolean(DEPOSIT, true);
            worker.practice(level, HiredWorkPractice.mining(mine, target.position(), block));
            worker.status("mined_ore");
        }
    }

    private static SettlementMiningRoutes.Target target(SettlementWorkerState worker, SettlementMine mine, Villager owner,
                                                         HiredWorkContext context, Map<BlockPos, Integer> routes) {
        HiredWorkPlan.retainMatching(context, position -> mine.contains(position)
                && MiningBlockRules.isMineableOre(mine, mine::observed, position), 20);
        for (var position : HiredWorkPlan.targets(context)) {
            var target = SettlementMiningRoutes.target(mine, owner, worker.position(), position, routes);
            if (target != null) return target;
        }
        var scan = HiredWorkAreaScan.collect(context, "SettlementMiningScanCursor", 4096,
                position -> mine.observed(position) && MiningBlockRules.isMineableOre(mine, mine::observed, position));
        var reachable = new LinkedHashMap<BlockPos, SettlementMiningRoutes.Target>();
        for (var position : scan.candidates()) {
            var target = SettlementMiningRoutes.target(mine, owner, worker.position(), position, routes);
            if (target != null) reachable.put(position, target);
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
