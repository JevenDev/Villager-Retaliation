package com.jvn.villagerretaliation.interaction;

import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
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
        var state = HiredWorkStateStore.state(villager);
        var home = HiredWorkArea.fromState(state, villager.blockPosition());
        if (task != null && (level.isOutsideBuildHeight(task.min()) || level.isOutsideBuildHeight(task.max())
                || !level.getWorldBorder().isWithinBounds(task.min()) || !level.getWorldBorder().isWithinBounds(task.max())
                || authority.civic() && !duty.town().contains(task.min(), task.max())
                || !authority.civic() && (!home.usable() || !home.contains(task.min()) || !home.contains(task.max())))) {
            return authority.civic() ? "Task leaves settlement or world bounds" : "Task leaves the worker's assigned bounds";
        }
        var previous = queue(state);
        if (append && state.getBoolean(INVALID)) return "Saved task queue needs cancellation";
        if (task != null && previous != null) {
            var repeated = previous.tasks().stream().filter(existing -> existing.id().equals(task.id())).findFirst().orElse(null);
            if (repeated != null) return repeated.equals(task) ? "" : "Task identifier is already in use";
        }
        if (append && previous != null && previous.tasks().size() >= HiredTemporaryQueue.MAX_TASKS) return "Temporary queue is full";
        if (append && previous != null && !valid(level, villager, authority, previous)) return "Assignment changed since the queue was created";
        HiredTemporaryQueue next = task == null ? null : append && previous != null ? previous.append(task)
                : new HiredTemporaryQueue(authority.civic() ? duty.town().id().value() : player.getUUID(), player.getUUID(),
                        authority.civic() ? duty.area().id() : null, authority.role(), home.min(), home.max(), List.of(task));
        if (!append || previous == null || task == null) reset(level, villager, authority.role());
        write(state, next);
        if ((!append || previous == null) && task != null && task.kind() == HiredTemporaryTask.Kind.FISH
                && (state.getBoolean("FishingCatchCompleted") || com.jvn.villagerretaliation.settlement.SettlementBackgroundFishing.hasPendingCatch(state))) {
            state.putBoolean(PREVIOUS_CATCH, true);
        }
        HiredWorkSession.invalidate(villager);
        return "";
    }

    public static void validate(ServerLevel level, Villager villager, VillagerWorkAuthority authority) {
        var state = HiredWorkStateStore.state(villager);
        if (!state.contains(HiredTemporaryQueue.STATE_TAG)) return;
        var queue = queue(state);
        if (queue != null && !valid(level, villager, authority, queue)) {
            reset(level, villager, queue.role());
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
                    && queue.tasks().stream().allMatch(task -> duty.town().contains(task.min(), task.max()));
        }
        var home = HiredWorkArea.fromState(HiredWorkStateStore.state(villager), villager.blockPosition());
        return queue.worksite() == null && authority.owner().filter(queue.owner()::equals).isPresent()
                && queue.owner().equals(queue.issuer()) && home.usable()
                && home.min().equals(queue.homeMin()) && home.max().equals(queue.homeMax())
                && queue.tasks().stream().allMatch(task -> home.contains(task.min()) && home.contains(task.max()));
    }

    public static boolean ready(ServerLevel level, HiredWorkSession session) {
        var queue = queue(session.state());
        String blocker = session.state().getBoolean(INVALID) ? "invalid" : null;
        if (queue != null) {
            var task = queue.current();
            for (int x = task.min().getX() >> 4; x <= task.max().getX() >> 4 && blocker == null; x++) {
                for (int z = task.min().getZ() >> 4; z <= task.max().getZ() >> 4; z++) {
                    if (!level.hasChunk(x, z)) {
                        blocker = "unloaded";
                        break;
                    }
                }
            }
        }
        if (blocker == null) return true;
        HiredWorkerBrain.setFailure(session.context(), "temporary_task_" + blocker, 0L);
        HiredWorkerBrain.setState(session.context(), HiredWorkerTaskState.AWAITING_INSTRUCTION);
        HiredWorkStateStore.setStatus(session.state(), "interaction.work.temporary." + blocker);
        return false;
    }

    public static void finishExcavation(ServerLevel level, com.jvn.villagerretaliation.interaction.work.HiredWorkContext context) {
        if (!active(context.state(), HiredTemporaryTask.Kind.EXCAVATE)) return;
        boolean excavation = HiredMiningMode.fromState(context.state()).excavatesArea();
        var scan = com.jvn.villagerretaliation.interaction.work.HiredWorkAreaScan.collect(context, MINING_SCAN, 768,
                pos -> excavation
                        ? com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isMineableExcavationBlock(level, context, pos)
                        : com.jvn.villagerretaliation.interaction.work.mining.MiningBlockRules.isMineableOre(level, pos));
        if (!scan.candidates().isEmpty()) context.state().remove(MINING_SCAN);
        else if (scan.completedFullPass()) context.state().putBoolean(FINISHED, true);
    }

    public static void finishPatch(CompoundTag state) {
        if (active(state, HiredTemporaryTask.Kind.FARM_PATCH)) state.putBoolean(FINISHED, true);
    }

    public static void caught(CompoundTag state) {
        if (!active(state, HiredTemporaryTask.Kind.FISH)) return;
        if (state.getBoolean(PREVIOUS_CATCH)) state.remove(PREVIOUS_CATCH);
        else state.putBoolean(FINISHED, true);
    }

    public static void completed(ServerLevel level, Villager villager, HiredWorkSession session, WorkResult result) {
        var queue = queue(session.state());
        if (queue == null) return;
        if (!session.state().getBoolean(FINISHED)) return;
        var next = queue.advance();
        if (next == null || !Objects.equals(next.current().id(), queue.current().id())) reset(level, villager, session.role());
        write(session.state(), next);
        HiredWorkSession.invalidate(villager);
    }

    private static void reset(ServerLevel level, Villager villager, HiredVillagerRole role) {
        var session = HiredWorkSession.create(level, villager, role);
        if (session.worker() != null) session.worker().stop(level, villager, session.context());
        com.jvn.villagerretaliation.villager.VillagerTaskNavigationUtil.stopHiredNavigation(villager);
        session.state().remove(FINISHED);
        session.state().remove("NextWorkGameTime");
        session.state().remove(MINING_SCAN);
    }

    private static void write(CompoundTag state, HiredTemporaryQueue queue) {
        if (queue == null) {
            state.remove(HiredTemporaryQueue.STATE_TAG);
            state.remove(PREVIOUS_CATCH);
        } else queue.store(state);
        state.remove(INVALID);
        state.remove(FINISHED);
    }

    public static void copyState(CompoundTag source, CompoundTag destination) {
        for (String key : List.of(HiredTemporaryQueue.STATE_TAG, FINISHED, PREVIOUS_CATCH, INVALID, MINING_SCAN)) {
            destination.remove(key);
            if (source.contains(key)) destination.put(key, source.get(key).copy());
        }
    }

    public static String summary(CompoundTag state) {
        var queue = queue(state);
        if (state.getBoolean(INVALID)) return "Invalid saved temporary queue";
        if (queue == null) return "";
        return queue.current().kind().label() + (queue.current().kind() == HiredTemporaryTask.Kind.FISH
                ? " (" + queue.current().remaining() + " catches left)" : "") + " | " + queue.tasks().size() + " queued";
    }
}
