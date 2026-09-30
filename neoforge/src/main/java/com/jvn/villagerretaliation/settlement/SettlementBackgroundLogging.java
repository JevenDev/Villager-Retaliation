package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan;
import com.jvn.villagerretaliation.interaction.work.HiredWorkContext;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters;
import com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingOptions;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry;
import com.jvn.villagerretaliation.interaction.work.logging.LoggingWorker;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import com.jvn.villagerretaliation.mixin.BushBlockAccessor;
import com.jvn.villagerretaliation.skill.HiredWorkPractice;
import com.jvn.villagerretaliation.util.VillagerEquipmentDurability;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

final class SettlementBackgroundLogging {
    private static final String APPROACHED = "SettlementLoggingApproached";
    private static final String SCAN_CURSOR = "SettlementLoggingScanCursor";
    private static final String DEPOSIT = "LoggingTreeDepositPending";
    private static final int MAX_ACTIONS = 64;

    private SettlementBackgroundLogging() {
    }

    static void tick(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, int elapsed, double multiplier) {
        var area = town.areas().get(worker.area());
        var forest = town.forests().get(worker.area());
        if (forest == null || area == null || !forest.matches(area)) {
            worker.status("no_observed_forest");
            return;
        }
        if (forest.conflict()) {
            worker.status("forest_conflict");
            return;
        }
        var depots = SettlementStock.depots(town).stream().filter(SettlementDepot::rulesObserved).toList();
        var state = worker.settings();
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
        var context = context(inventory, worker, area);
        try {
            LoggingHarvestPlan.clearCompletedDeposit(context);
            state.remove(DEPOSIT);
            work(level, town, worker, forest, owner, context, depots, new SettlementWorkBudget(worker, elapsed, multiplier));
        } finally {
            var snapshot = inventory.captureSnapshot();
            if (!snapshot.equals(worker.inventory()) || !before.equals(state)) worker.changed();
            worker.inventorySnapshot(snapshot);
            SettlementForestService.applyLoaded(level, forest);
        }
    }

    static HiredWorkContext context(HiredJobInventory inventory, SettlementWorkerState worker, SettlementArea area) {
        return new HiredWorkContext(inventory, worker.settings(), area.center(), area.min(), area.max(), 64, 32, true,
                worker.aptitude(), HiredVillagerRoles.roleActionSpeedPercent(HiredVillagerRole.LOGGING, worker.aptitude()),
                100, 1, false, false);
    }

    private static void work(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementForest forest, Villager owner,
                             HiredWorkContext context, List<SettlementDepot> depots, SettlementWorkBudget budget) {
        var plan = LoggingHarvestPlan.read(context);
        if (plan == null) {
            context.state().remove(APPROACHED);
            var filters = HiredLoggingFilters.selectedFilterIds(context.state());
            var trees = LoggingTreeGeometry.discovery(forest, forest::observed, filters);
            var candidates = HiredWorkAreaScan.collect(context, SCAN_CURSOR, 4096,
                    position -> forest.observed(position) && forest.getBlockState(position).is(BlockTags.LOGS)
                            && HiredLoggingFilters.matches(forest.getBlockState(position), filters));
            var roots = trees.distinctRoots(candidates.candidates()).stream()
                    .filter(root -> com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeClaims.available(level, owner,
                            LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, root, filters)))
                    .sorted(Comparator.comparingDouble(position -> position.distSqr(worker.position()))).toList();
            if (roots.isEmpty()) {
                if (bonemeal(level, town, worker, forest, context, depots, budget)) return;
                worker.status(candidates.completedFullPass() ? "waiting_for_trees" : "searching_forest");
                return;
            }
            BlockPos root = roots.getFirst();
            var logs = LoggingTreeGeometry.connectedTreeLogs(forest, forest::observed, root, filters);
            var leaves = LoggingTreeGeometry.naturalTreeLeaves(forest, forest::observed, logs);
            var sapling = HiredLoggingOptions.plantSaplings(context.state())
                    ? LoggingTreeGeometry.saplingForTree(forest, forest::observed, logs) : ItemStack.EMPTY;
            int minY = logs.stream().mapToInt(BlockPos::getY).min().orElse(root.getY());
            var planting = sapling.isEmpty() ? List.<BlockPos>of() : logs.stream().filter(position -> position.getY() == minY
                    && forest.observed(position.below()) && LoggingTreeGeometry.isNaturalTreeBase(forest.getBlockState(position.below()))).toList();
            LoggingHarvestPlan.begin(context, root, null, logs, leaves, planting, sapling,
                    HiredLoggingOptions.stripLogs(context.state()), LoggingTreeGeometry.logFamilyKey(forest.getBlockState(root)));
            plan = LoggingHarvestPlan.read(context);
        }
        if (plan == null) return;
        if (!com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeClaims.reserve(level, owner,
                java.util.Arrays.stream(plan.logs()).mapToObj(BlockPos::of).toList())) {
            worker.status("tree_claimed");
            return;
        }
        if (!context.state().getBoolean(APPROACHED)) {
            if (!budget.spend(40.0D + 16.0D * Math.sqrt(worker.position().distSqr(plan.origin())))) {
                worker.status("approaching_tree");
                return;
            }
            context.state().putBoolean(APPROACHED, true);
        }
        owner.setPos(plan.origin().getBottomCenter());
        for (int action = 0; action < MAX_ACTIONS; action++) {
            plan = LoggingHarvestPlan.read(context);
            if (plan == null) return;
            if (plan.hasLogs()) {
                var position = BlockPos.of(plan.logs()[0]);
                if (!forest.observed(position)) {
                    worker.status("no_observed_forest");
                    return;
                }
                var block = forest.getBlockState(position);
                if (!block.is(BlockTags.LOGS) || !plan.logFamily().isBlank()
                        && !LoggingTreeGeometry.logFamilyKey(block).equals(plan.logFamily())) {
                    LoggingHarvestPlan.removeLog(context, position);
                    continue;
                }
                if (plan.logFamily().isBlank()) LoggingHarvestPlan.rememberLogFamily(context, LoggingTreeGeometry.logFamilyKey(block));
                var axe = context.inventory().equipBestTool(stack -> stack.is(ItemTags.AXES), stack -> destroySpeed(level, stack, block));
                if (axe.isEmpty()) {
                    SettlementBackgroundTools.fetchTool(level, town, worker, context.inventory(), depots, stack -> stack.is(ItemTags.AXES),
                            stack -> destroySpeed(level, stack, block), "axe", budget);
                    return;
                }
                BlockState stripped = plan.stripLogs() ? AxeItem.getAxeStrippingState(block) : null;
                var harvest = stripped == null ? block : stripped;
                double cost = blockCost(level, forest, position, block, axe, context.skillWorkSpeedPercent());
                if (!harvest(level, worker, forest, owner, context, position, harvest, axe, cost, stripped != null, "cutting_tree", budget)) return;
                LoggingHarvestPlan.removeLog(context, position);
                LoggingHarvestPlan.incrementLogsCut(context);
            } else if (plan.hasLeaves()) {
                var position = BlockPos.of(plan.leaves()[0]);
                if (!forest.observed(position)) {
                    worker.status("no_observed_forest");
                    return;
                }
                var block = forest.getBlockState(position);
                if (!LoggingTreeGeometry.isNaturalLeaf(block)) {
                    LoggingHarvestPlan.removeLeaf(context, position);
                    continue;
                }
                var tool = HiredLoggingOptions.harvestLeaves(context.state())
                        ? context.inventory().equipBestTool(LoggingWorker::isLeafHarvestTool, LoggingWorker::leafToolScore) : ItemStack.EMPTY;
                if (HiredLoggingOptions.harvestLeaves(context.state()) && tool.isEmpty()
                        && SettlementBackgroundTools.fetchTool(level, town, worker, context.inventory(), depots, LoggingWorker::isLeafHarvestTool,
                                LoggingWorker::leafToolScore, "leaf_tool", budget)) return;
                if (!harvest(level, worker, forest, owner, context, position, block, tool, 10.0D, false, "clearing_tree_leaves", budget)) return;
                LoggingHarvestPlan.removeLeaf(context, position);
            } else if (!plan.sapling().isEmpty() && plan.saplings().length > 0) {
                var position = BlockPos.of(plan.saplings()[0]);
                if (!forest.observed(position) || !forest.observed(position.below())) {
                    worker.status("no_observed_forest");
                    return;
                }
                if (!(plan.sapling().getItem() instanceof BlockItem item) || !canPlant(forest, position, item.getBlock().defaultBlockState())) {
                    LoggingHarvestPlan.removeSapling(context, position);
                    continue;
                }
                if (!plant(level, town, worker, forest, context, depots, position, plan.sapling(), item.getBlock().defaultBlockState(), budget)) return;
                LoggingHarvestPlan.removeSapling(context, position);
            } else {
                int cut = plan.logsCut();
                LoggingHarvestPlan.clear(context);
                com.jvn.villagerretaliation.interaction.work.HiredPathMemory.releaseAll(owner);
                context.state().remove(APPROACHED);
                context.state().putBoolean(DEPOSIT, true);
                if (cut > 0) worker.practice(level, HiredWorkPractice.logging(cut));
                context.inventory().equipBestTool(stack -> stack.is(ItemTags.AXES), stack -> stack.getDestroySpeed(Blocks.OAK_LOG.defaultBlockState()));
                worker.status(cut > 0 ? "tree_harvested" : "waiting_for_trees");
                return;
            }
        }
    }

    private static boolean bonemeal(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementForest forest,
                                    HiredWorkContext context, List<SettlementDepot> depots, SettlementWorkBudget budget) {
        if (!HiredLoggingOptions.bonemealSaplings(context.state())) return false;
        var updated = SettlementBackgroundTools.items(context.inventory());
        var supplies = SettlementProductionInventory.supplies(level, town, worker, updated, depots);
        int selected = -1;
        for (int index = 0; index < supplies.size(); index++) {
            if (supplies.get(index).available().is(net.minecraft.world.item.Items.BONE_MEAL) && SettlementBackgroundTools.allowed(level, supplies, index)) {
                selected = index;
                break;
            }
        }
        if (selected < 0) return false;
        String targetKey = "SettlementLoggingSapling";
        BlockPos position = context.state().contains(targetKey) ? BlockPos.of(context.state().getLong(targetKey)) : null;
        if (position == null || !forest.observed(position) || !SettlementTreeGrowth.sapling(forest.getBlockState(position))) {
            var candidates = HiredWorkAreaScan.collect(context, "SettlementLoggingSaplingCursor", 4096,
                    pos -> forest.observed(pos) && SettlementTreeGrowth.sapling(forest.getBlockState(pos)));
            position = candidates.candidates().stream().min(Comparator.comparingDouble(pos -> pos.distSqr(worker.position()))).orElse(null);
            if (position == null) return false;
            context.state().putLong(targetKey, position.asLong());
        }
        var source = supplies.get(selected);
        double cost = 40.0D + 16.0D * Math.sqrt(position.distSqr(worker.position()))
                + (source.depot() == null ? 0.0D : 16.0D * Math.sqrt(position.distSqr(source.depot().position())));
        if (budget.available() < cost) {
            budget.spend(cost);
            worker.status("fetching_bonemeal");
            return true;
        }
        var plan = SettlementTreeGrowth.bonemeal(level, forest, position, level.random);
        context.state().remove(targetKey);
        worker.status(plan.status());
        if (!plan.accepted()) return true;
        if (!plan.commit()) {
            worker.status("forest_conflict");
            return true;
        }
        budget.spend(cost);
        if (source.depot() == null) updated.get(source.slot()).shrink(1);
        else source.depot().removeItem(source.slot(), 1);
        SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
        return true;
    }

    private static boolean harvest(ServerLevel level, SettlementWorkerState worker, SettlementForest forest, Villager owner,
                                   HiredWorkContext context, BlockPos position, BlockState harvest, ItemStack tool,
                                   double cost, boolean stripped, String status, SettlementWorkBudget budget) {
        if (budget.available() < cost) {
            budget.spend(cost);
            worker.status(status);
            return false;
        }
        if (forest.getBlockState(position).hasBlockEntity()) {
            worker.status("forest_conflict");
            return false;
        }
        var drops = Block.getDrops(harvest, level, position, null, owner, tool);
        var updated = SettlementBackgroundTools.items(context.inventory());
        if (!SettlementProductionInventory.insertOutputs(worker, updated, drops)) {
            worker.status("output_full");
            return false;
        }
        if (!forest.change(Map.of(position, Blocks.AIR.defaultBlockState()))) {
            worker.status("forest_conflict");
            return false;
        }
        budget.spend(cost);
        SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
        if (!tool.isEmpty()) {
            var equipped = context.inventory().getItem(HiredJobInventory.MAINHAND_SLOT);
            if (!VillagerEquipmentDurability.mineBlock(equipped, level, harvest, position, owner)) {
                VillagerEquipmentDurability.hurtTool(equipped, owner, EquipmentSlot.MAINHAND);
            }
            if (stripped) VillagerEquipmentDurability.hurtTool(equipped, owner, EquipmentSlot.MAINHAND);
            context.inventory().syncMainHandEquipment();
        }
        worker.status(status);
        return true;
    }

    private static boolean plant(ServerLevel level, SettlementRecord town, SettlementWorkerState worker, SettlementForest forest,
                                 HiredWorkContext context, List<SettlementDepot> depots, BlockPos position, ItemStack sapling, BlockState planted, SettlementWorkBudget budget) {
        var updated = SettlementBackgroundTools.items(context.inventory());
        for (int slot : worker.outputSlots()) {
            if (updated.get(slot).is(sapling.getItem())) {
                if (!budget.spend(20.0D)) {
                    worker.status("planting_sapling");
                    return false;
                }
                if (!forest.change(Map.of(position, planted))) return false;
                updated.get(slot).shrink(1);
                SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
                worker.status("planted_sapling");
                return true;
            }
        }
        var supplies = SettlementProductionInventory.supplies(level, town, worker, updated, depots);
        for (int index = 0; index < supplies.size(); index++) {
            var source = supplies.get(index);
            if (!source.available().is(sapling.getItem()) || !SettlementBackgroundTools.allowed(level, supplies, index)) continue;
            double cost = 20.0D + (source.depot() == null ? 0.0D : 16.0D * Math.sqrt(position.distSqr(source.depot().position())));
            if (!budget.spend(cost)) {
                worker.status("fetching_sapling");
                return false;
            }
            if (!forest.change(Map.of(position, planted))) return false;
            if (source.depot() == null) updated.get(source.slot()).shrink(1);
            else source.depot().removeItem(source.slot(), 1);
            SettlementBackgroundTools.replaceItems(level, context.inventory(), updated);
            worker.status("planted_sapling");
            return true;
        }
        LoggingHarvestPlan.clearSaplings(context);
        worker.status("missing_sapling");
        return true;
    }

    static boolean canPlant(SettlementForest forest, BlockPos position, BlockState sapling) {
        if (!forest.observed(position) || !forest.observed(position.below()) || !forest.getBlockState(position).canBeReplaced()
                || !(sapling.getBlock() instanceof BushBlock bush)) return false;
        var soil = forest.getBlockState(position.below());
        var decision = soil.canSustainPlant(forest, position.below(), Direction.UP, sapling);
        return decision.isDefault() ? ((BushBlockAccessor) bush).villagerretaliation$mayPlaceOn(soil, forest, position.below()) : decision.isTrue();
    }

    private static double blockCost(ServerLevel level, SettlementForest forest, BlockPos position, BlockState state,
                                    ItemStack tool, int speedPercent) {
        float hardness = Math.max(0.2F, state.getDestroySpeed(forest, position));
        int ticks = Math.clamp(Math.round(hardness * 6.0F / Math.max(0.1F, destroySpeed(level, tool, state))), 1, 24);
        return HiredVillagerRoles.scaledDurationTicks(ticks * 10, speedPercent);
    }

    private static float destroySpeed(ServerLevel level, ItemStack tool, BlockState state) {
        float speed = tool.getDestroySpeed(state);
        int efficiency = EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY), tool);
        return efficiency > 0 && speed > 1.0F ? speed + efficiency * efficiency + 1 : speed;
    }
}
