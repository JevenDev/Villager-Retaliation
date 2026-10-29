package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryEntities;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerBrain;
import com.jvn.villagerretaliation.interaction.work.HiredWorkerTaskState;
import com.jvn.villagerretaliation.interaction.work.WorkResult;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementWorkforceService;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

public final class HiredTemporaryWorkService {
    private static final String FINISHED = "TemporaryTaskFinished";
    private static final String INVALID = "TemporaryTaskInvalid";
    private static final String PREVIOUS_CATCH = "TemporaryPreviousCatch";
    private static final String MINING_SCAN = "TemporaryMiningCompletionScan";

    private HiredTemporaryWorkService() {
    }

    public static HiredTemporaryQueue queue(CompoundTag state) {
        try {
            HiredTemporaryQueue queue = HiredTemporaryQueue.load(state);
            HiredTemporaryQueue.pending(state);
            state.remove(INVALID);
            return queue;
        } catch (IllegalArgumentException exception) {
            state.putBoolean(INVALID, true);
            return null;
        }
    }

    public static boolean active(CompoundTag state, HiredTemporaryTask.Kind kind) {
        var queue = queue(state);
        return queue != null && queue.current().kind() == kind;
    }

    public static HiredJobSite jobSite(CompoundTag state, HiredVillagerRole role, HiredJobSite fallback) {
        var queue = queue(state);
        return queue != null && queue.role() == role ? HiredJobSite.fromWorkArea(queue.current().area()) : fallback;
    }

    public static String submit(ServerPlayer player, Villager villager, HiredTemporaryTask task, boolean append) {
        if (!(villager.level() instanceof ServerLevel level) || player.serverLevel() != level || !villager.isAlive() || villager.isBaby()) {
            return "Worker is unavailable";
        }
        var authority = VillagerWorkAuthority.resolve(level, villager);
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (!authority.active() || authority.civic() && (duty == null || !duty.town().permits(player.getUUID(), SettlementPermission.STAFF))
                || !authority.civic() && !authority.owner().filter(player.getUUID()::equals).isPresent()) {
            return "Worker permission changed";
        }
        if (task != null && task.kind().role() != authority.role()) return "Incompatible worker role";
        if (task != null && task.kind() == HiredTemporaryTask.Kind.ORE
                && !com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isMineableOre(level, task.target()))
            return "Select an exposed ore deposit";
        if (task != null && !task.material().isEmpty()) {
            String selector = task.material();
            var registry = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BLOCK);
            var id = net.minecraft.resources.ResourceLocation.parse(selector.startsWith("#") ? selector.substring(1) : selector);
            if (selector.startsWith("#") ? registry.getTag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, id)).isEmpty()
                    : !registry.containsKey(id)) return "Unknown mining material or block tag";
        }
        var state = HiredWorkStateStore.state(villager);
        var home = HiredWorkArea.fromState(state, villager.blockPosition());
        var current = queue(state);
        var previous = state.contains(HiredTemporaryQueue.PENDING_TAG) && !state.getBoolean(INVALID) ? HiredTemporaryQueue.pending(state) : current;
        if (append && state.getBoolean(INVALID)) return "Saved task queue needs cancellation";
        if (task != null && previous != null) {
            var taskId = task.id();
            var repeated = previous.tasks().stream().filter(existing -> existing.id().equals(taskId)).findFirst().orElse(null);
            if (repeated != null) return repeated.equals(task) || (task.kind() == HiredTemporaryTask.Kind.TREE || task.kind() == HiredTemporaryTask.Kind.ORE)
                    && repeated.kind() == task.kind() && Objects.equals(repeated.target(), task.target())
                    || task.kind().entityTarget() && repeated.kind() == task.kind() && Objects.equals(repeated.entity(), task.entity())
                    ? "" : "Task identifier is already in use";
        }
        if (task != null && task.kind().entityTarget()) {
            var target = level.getEntity(task.entity());
            if (!(target instanceof net.minecraft.world.entity.LivingEntity living)) return "Selected target is unavailable";
            if (!HiredTemporaryEntities.authorized(villager, living.blockPosition())) return "Selected target leaves work bounds";
            if (!HiredTemporaryEntities.eligible(villager, living, task)) return "Selected target is not eligible for this work";
            task = HiredTemporaryEntities.normalize(level, villager, task, living);
            if (!task.area().contains(living.blockPosition())) return "Selected target leaves task bounds";
        }
        if (task != null && task.kind() == HiredTemporaryTask.Kind.TREE) {
            if (!level.hasChunkAt(task.target()) || authority.civic() && !com.jvn.villagerretaliation.settlement.SettlementExpeditions.permits(level, duty.town(), task)
                    || !authority.civic() && (!home.usable() || !home.contains(task.target()))) return "Selected tree is outside available work bounds";
            task = treeTask(level, state, task, authority.civic() ? null : home);
            if (task == null) return "No matching natural tree at the selected block";
        }
        if (task != null && task.kind() == HiredTemporaryTask.Kind.ORE) {
            if (!authority.civic() && (!home.usable() || !home.contains(task.target()))) return "Selected ore leaves the worker's assigned bounds";
            var min = task.target().offset(-2, -1, -2);
            var max = task.target().offset(2, 2, 2);
            if (!authority.civic()) {
                min = HiredWorkArea.maxPos(min, home.min());
                max = HiredWorkArea.minPos(max, home.max());
            }
            task = new HiredTemporaryTask(task.id(), task.kind(), min, max, 1, task.target());
        }
        if (task != null && (level.isOutsideBuildHeight(task.min()) || level.isOutsideBuildHeight(task.max())
                || !level.getWorldBorder().isWithinBounds(task.min()) || !level.getWorldBorder().isWithinBounds(task.max())
                || authority.civic() && !com.jvn.villagerretaliation.settlement.SettlementExpeditions.permits(level, duty.town(), task)
                || !authority.civic() && (!home.usable() || !home.contains(task.min()) || !home.contains(task.max())))) {
            return authority.civic() ? "Task leaves expedition bounds or enters another settlement" : "Task leaves the worker's assigned bounds";
        }
        if (append && previous != null && previous.tasks().size() >= HiredTemporaryQueue.MAX_TASKS) return "Temporary queue is full";
        if (append && previous != null && !valid(level, villager, authority, previous)) return "Assignment changed since the queue was created";
        HiredTemporaryQueue next = task == null ? null : append && previous != null ? previous.append(task)
                : new HiredTemporaryQueue(authority.civic() ? duty.town().id().value() : player.getUUID(), player.getUUID(),
                        authority.civic() ? duty.area().id() : null, authority.role(), home.min(), home.max(), List.of(task));
        if (authority.role() == HiredVillagerRole.LOGGING && unfinishedTree(level, villager)) {
            if (append && next != null && current != null && !state.contains(HiredTemporaryQueue.PENDING_TAG)) next = next.advance();
            if (next != null && current != null && next.tasks().size() >= HiredTemporaryQueue.MAX_TASKS) return "Temporary queue is full";
            HiredTemporaryQueue.storePending(state, next);
            return "";
        }
        state.remove(HiredTemporaryQueue.PENDING_TAG);
        if (!append || previous == null || task == null) reset(level, villager, authority.role(), false);
        write(state, next);
        if ((!append || previous == null) && task != null && task.kind() == HiredTemporaryTask.Kind.FISH
                && (state.getBoolean("FishingCatchCompleted") || com.jvn.villagerretaliation.settlement.SettlementBackgroundFishing.hasPendingCatch(state))) {
            state.putBoolean(PREVIOUS_CATCH, true);
        }
        HiredWorkSession.invalidate(villager);
        return "";
    }

    private static HiredTemporaryTask treeTask(ServerLevel level, CompoundTag state, HiredTemporaryTask task, HiredWorkArea home) {
        var logs = com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.selectedTreeLogs(level,
                task.target(), com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters.selectedFilterIds(state));
        if (logs.isEmpty()) return null;
        var positions = new java.util.ArrayList<>(logs);
        positions.addAll(com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.naturalTreeLeaves(level, level::hasChunkAt, logs));
        var min = task.target();
        var max = task.target();
        for (var position : positions) {
            min = HiredWorkArea.minPos(min, position);
            max = HiredWorkArea.maxPos(max, position);
        }
        min = min.offset(-2, -1, -2);
        max = max.offset(2, 1, 2);
        if (home != null) {
            min = HiredWorkArea.maxPos(min, home.min());
            max = HiredWorkArea.minPos(max, home.max());
            if (positions.stream().anyMatch(position -> !home.contains(position))) return null;
        }
        try {
            return new HiredTemporaryTask(task.id(), task.kind(), min, max, 1, task.target());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static boolean unfinishedTree(ServerLevel level, Villager villager) {
        return com.jvn.villagerretaliation.interaction.work.logging.LoggingHarvestPlan.read(
                HiredWorkSession.create(level, villager, HiredVillagerRole.LOGGING).context()) != null;
    }

    public static void validate(ServerLevel level, Villager villager, VillagerWorkAuthority authority) {
        var state = HiredWorkStateStore.state(villager);
        if (!state.contains(HiredTemporaryQueue.STATE_TAG) && !state.contains(HiredTemporaryQueue.PENDING_TAG)) return;
        var queue = queue(state);
        var pending = state.getBoolean(INVALID) ? null : HiredTemporaryQueue.pending(state);
        if (queue != null && !valid(level, villager, authority, queue) || pending != null && !valid(level, villager, authority, pending)
                || state.contains(HiredTemporaryQueue.PENDING_TAG) && (!authority.active() || authority.role() != HiredVillagerRole.LOGGING)) {
            reset(level, villager, queue != null ? queue.role() : HiredVillagerRole.LOGGING, false);
            state.remove(HiredTemporaryQueue.PENDING_TAG);
            write(state, null);
        } else if (queue != null && queue.current().kind() == HiredTemporaryTask.Kind.FISH && state.getBoolean(FINISHED)) {
            completed(level, villager, HiredWorkSession.create(level, villager, queue.role()), WorkResult.idle(""));
        }
    }

    private static boolean valid(ServerLevel level, Villager villager, VillagerWorkAuthority authority, HiredTemporaryQueue queue) {
        if (!villager.isAlive() || !authority.active() || authority.role() != queue.role()) return false;
        if (authority.civic()) {
            var duty = SettlementWorkforceService.duty(villager).orElse(null);
            return duty != null && duty.town().id().value().equals(queue.owner()) && duty.area().id().equals(queue.worksite())
                    && duty.town().permits(queue.issuer(), SettlementPermission.STAFF)
                    && (duty.town().permits(queue.issuer(), SettlementPermission.BUILD)
                            || queue.tasks().stream().noneMatch(task -> task.kind() == HiredTemporaryTask.Kind.PREPARE_FARM))
                    && queue.tasks().stream().allMatch(task -> com.jvn.villagerretaliation.settlement.SettlementExpeditions.permits(level, duty.town(), task));
        }
        var home = HiredWorkArea.fromState(HiredWorkStateStore.state(villager), villager.blockPosition());
        return queue.worksite() == null && authority.owner().filter(queue.owner()::equals).isPresent()
                && queue.owner().equals(queue.issuer()) && home.usable()
                && home.min().equals(queue.homeMin()) && home.max().equals(queue.homeMax())
                && queue.tasks().stream().allMatch(task -> home.contains(task.min()) && home.contains(task.max()));
    }

    public static boolean ready(ServerLevel level, Villager villager, HiredWorkSession session) {
        var queue = queue(session.state());
        String blocker = session.state().getBoolean(INVALID) ? "invalid" : HiredTemporaryEntities.blocker(level, villager, session.state());
        if (queue != null) {
            var area = session.area();
            for (int x = area.min().getX() >> 4; x <= area.max().getX() >> 4 && blocker == null; x++) {
                for (int z = area.min().getZ() >> 4; z <= area.max().getZ() >> 4; z++) {
                    if (!level.hasChunk(x, z)) {
                        blocker = "unloaded";
                        break;
                    }
                }
            }
        }
        if (blocker == null) return true;
        if (session.worker() != null) session.worker().pause(level, villager, session.context());
        HiredWorkerBrain.setFailure(session.context(), "temporary_task_" + blocker, 0L);
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.AWAITING_INSTRUCTION);
        HiredWorkStateStore.setStatus(session.state(), "interaction.work.temporary." + blocker);
        return false;
    }

    public static void finishExcavation(ServerLevel level, com.jvn.villagerretaliation.interaction.work.HiredWorkContext context) {
        var queue = queue(context.state());
        if (queue == null || queue.role() != HiredVillagerRole.MINING) return;
        boolean excavation = HiredMiningMode.fromState(context.state()).excavatesArea();
        var scan = com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.findAny(context, MINING_SCAN, 768,
                pos -> com.jvn.villagerretaliation.interaction.work.mining.MiningTaskRules.permits(level, context, pos) && (excavation
                        ? com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isMineableExcavationBlock(level, context, pos)
                        : com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isMineableOre(level, pos)));
        if (!scan.candidates().isEmpty()) context.state().remove(MINING_SCAN);
        else if (scan.completedFullPass()) context.state().putBoolean(FINISHED, true);
    }

    public static void finishPatch(CompoundTag state) {
        if (active(state, HiredTemporaryTask.Kind.FARM_PATCH)) state.putBoolean(FINISHED, true);
    }

    public static void finishForest(ServerLevel level, com.jvn.villagerretaliation.interaction.work.HiredWorkContext context) {
        var queue = queue(context.state());
        if (queue == null || !queue.current().kind().forest()) return;
        var filters = com.jvn.villagerretaliation.interaction.work.logging.HiredLoggingFilters.selectedFilterIds(context.state());
        var trees = com.jvn.villagerretaliation.interaction.work.logging.LoggingTreeGeometry.discovery(level, level::hasChunkAt, filters);
        var scan = com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.findAny(context, "TemporaryForestCompletionScan", 768,
                pos -> level.getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)
                        && trees.isNaturalTree(pos));
        if (!scan.candidates().isEmpty()) context.state().remove("TemporaryForestCompletionScan");
        else if (scan.completedFullPass()) context.state().putBoolean(FINISHED, true);
    }

    public static void finishTree(CompoundTag state) {
        if (active(state, HiredTemporaryTask.Kind.TREE)) state.putBoolean(FINISHED, true);
    }

    public static void finishAction(CompoundTag state, HiredTemporaryTask.Kind kind) {
        if (active(state, kind)) state.putBoolean(FINISHED, true);
    }

    public static void caught(CompoundTag state) {
        if (!active(state, HiredTemporaryTask.Kind.FISH)) return;
        if (state.getBoolean(PREVIOUS_CATCH)) state.remove(PREVIOUS_CATCH);
        else state.putBoolean(FINISHED, true);
    }

    public static void completed(ServerLevel level, Villager villager, HiredWorkSession session, WorkResult result) {
        var queue = queue(session.state());
        if (session.state().contains(HiredTemporaryQueue.PENDING_TAG) && !session.state().getBoolean(INVALID)
                && !unfinishedTree(level, villager)) {
            var pending = HiredTemporaryQueue.pending(session.state());
            session.state().remove(HiredTemporaryQueue.PENDING_TAG);
            reset(level, villager, session.role(), true);
            write(session.state(), pending);
            HiredWorkSession.invalidate(villager);
            return;
        }
        if (queue == null) return;
        if (!session.state().getBoolean(FINISHED)) return;
        var next = queue.advance();
        if (next == null || !Objects.equals(next.current().id(), queue.current().id())) reset(level, villager, session.role(), true);
        write(session.state(), next);
        HiredWorkSession.invalidate(villager);
    }

    private static void reset(ServerLevel level, Villager villager, HiredVillagerRole role, boolean completed) {
        var session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) {
            if (completed) session.worker().complete(level, villager, session.context());
            else session.worker().cancel(level, villager, session.context());
        }
        com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        session.state().remove(FINISHED);
        session.state().remove("NextWorkGameTime");
        session.state().remove(MINING_SCAN);
        session.state().remove(com.jvn.villagerretaliation.settlement.SettlementFarmWork.TAG);
        session.state().remove("TemporaryForestCompletionScan");
        HiredTemporaryEntities.clear(session.state());
    }

    private static void write(CompoundTag state, HiredTemporaryQueue queue) {
        if (queue == null) {
            state.remove(HiredTemporaryQueue.STATE_TAG);
            state.remove(PREVIOUS_CATCH);
        } else queue.store(state);
        state.remove(INVALID);
        state.remove(FINISHED);
    }

    public static void clearOrders(CompoundTag state) {
        state.remove(HiredTemporaryQueue.PENDING_TAG);
        state.remove(com.jvn.villagerretaliation.settlement.SettlementFarmWork.TAG);
        state.remove(MINING_SCAN);
        HiredTemporaryEntities.clear(state);
        write(state, null);
    }

    public static void copyState(CompoundTag source, CompoundTag destination) {
        HiredTemporaryEntities.copy(source, destination);
        for (String key : List.of(HiredTemporaryQueue.STATE_TAG, HiredTemporaryQueue.PENDING_TAG, FINISHED, PREVIOUS_CATCH, INVALID, MINING_SCAN,
                com.jvn.villagerretaliation.settlement.SettlementFarmWork.TAG)) {
            destination.remove(key);
            if (source.contains(key)) destination.put(key, source.get(key).copy());
        }
    }

    public static String summary(CompoundTag state) {
        var queue = queue(state);
        if (state.getBoolean(INVALID)) return "Invalid saved temporary queue";
        if (state.contains(HiredTemporaryQueue.PENDING_TAG)) {
            var pending = HiredTemporaryQueue.pending(state);
            return pending == null ? "Cancel after current tree" : pending.tasks().size() + " queued after current tree";
        }
        if (queue == null) return "";
        return queue.current().kind().label() + (queue.current().kind() == HiredTemporaryTask.Kind.FISH
                ? " (" + queue.current().remaining() + " catches left)" : "") + " | " + queue.tasks().size() + " queued";
    }
}
