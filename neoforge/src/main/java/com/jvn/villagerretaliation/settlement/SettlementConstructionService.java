package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementConstructionService {
    private SettlementConstructionService() {
    }

    public static boolean start(ServerPlayer player, SettlementRecord town, UUID resident, UUID blueprintId) {
        if (!canBuild(player, town) || town.stage().projects() == 0
                || !(player.serverLevel().getEntity(resident) instanceof Villager builder)
                || !builder.isAlive() || builder.isBaby() || SettlementWorkforceService.hasPersonalCommitment(builder)) {
            return false;
        }
        var duty = SettlementWorkforceService.duty(builder).orElse(null);
        if (duty == null || duty.town() != town || duty.area().type() != SettlementAreaType.CONSTRUCTION) {
            return false;
        }
        var level = player.serverLevel();
        SettlementConstructionSiteService.update(level, town);
        ItemStack blueprint = heldBlueprint(player, blueprintId);
        var preview = ConstructionBlueprintItem.previewData(blueprint).orElse(null);
        if (preview == null || preview.started() || preview.expired() || preview.completed()
                || !preview.dimension().equals(level.dimension())
                || SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.projects().containsKey(blueprintId))) {
            return false;
        }
        var entry = BuilderStructureCatalog.byId(level.getServer(), preview.structureId()).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(level, entry, preview.rotation()).orElse(null);
        if (plan == null) {
            return false;
        }
        var project = SettlementProject.create(town, duty.area().id(), resident, plan, preview.origin(), blueprintId, level.getGameTime());
        if (!project.within(duty.area()) || level.isOutsideBuildHeight(project.min()) || level.isOutsideBuildHeight(project.max())
                || !level.getWorldBorder().isWithinBounds(project.min()) || !level.getWorldBorder().isWithinBounds(project.max())
                || !BuilderSitePlanner.validateSite(level, player, builder,
                        HiredWorkArea.fromBounds(duty.area().min(), duty.area().max(), true), plan, preview.origin()).valid()) {
            return false;
        }
        SettlementBackgroundService.onActive(builder);
        var state = HiredWorkStateStore.state(builder);
        if (BuilderTaskState.hasTask(state) || !town.startProject(player.getUUID(), project)) {
            return false;
        }
        HiredWorkStateStore.pauseWork(level, builder, HiredVillagerRole.BUILDER, "interaction.work.builder.started", Map.of());
        project.restore(state);
        SettlementConstructionSiteService.observeArea(level, town, duty.area(), null);
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        ConstructionBlueprintItem.updatePlacement(blueprint, level, plan, preview.origin(), preview.rotation());
        ConstructionBlueprintItem.markStarted(blueprint, builder, 0, level.getGameTime());
        HiredWorkSession.invalidate(builder);
        SettlementBackgroundService.onUnloaded(builder);
        return true;
    }

    public static boolean cancel(ServerPlayer player, SettlementRecord town, UUID projectId) {
        var project = town.projects().get(projectId);
        if (!canBuild(player, town) || project == null || !project.active()) {
            return false;
        }
        town.finishProject(projectId, SettlementProject.Status.CANCELLED);
        if (player.serverLevel().getEntity(project.builder()) instanceof Villager builder) {
            reconcile(builder);
        }
        var worker = town.workers().get(project.builder());
        if (worker != null && projectId.equals(BuilderTaskState.jobId(worker.settings()).orElse(null))) {
            BuilderTaskState.clearTask(worker.settings());
        }
        ConstructionBlueprintItem.expireMatchingBlueprints(player.serverLevel(), projectId);
        return true;
    }

    public static boolean prepare(Villager builder, CompoundTag state) {
        var project = project(builder, state);
        if (project == null || project.site() == null || !project.site().pending()) return true;
        SettlementConstructionSiteService.refresh((ServerLevel) builder.level(), project.site());
        town(builder, state).changed();
        return !project.site().pending();
    }

    public static boolean allows(Villager builder, CompoundTag state, BuilderStructureScanner.StructurePlan plan) {
        var duty = SettlementWorkforceService.duty(builder).orElse(null);
        var project = project(builder, state);
        if (project == null) {
            return duty == null && !state.getCompound(BuilderTaskState.TASK_TAG).hasUUID("SettlementTown");
        }
        return project.active() && duty != null && project.builder().equals(builder.getUUID())
                && builder.level().getWorldBorder().isWithinBounds(project.min()) && builder.level().getWorldBorder().isWithinBounds(project.max())
                && project.town().equals(duty.town().id().value()) && project.area().equals(duty.area().id())
                && project.within(duty.area()) && project.structure().equals(plan.entry().id()) && project.rotation() == plan.rotation()
                && project.origin().equals(BuilderTaskState.origin(state))
                && project.min().equals(project.origin().offset(plan.localMin())) && project.max().equals(project.origin().offset(plan.localMax()))
                && project.total() == plan.blocks().size() && !SettlementWorkforceService.hasPersonalCommitment(builder);
    }

    public static void observe(Villager builder, CompoundTag state) {
        var project = project(builder, state);
        if (project != null && project.builder().equals(builder.getUUID()) && project.observe(state)) {
            town(builder, state).changed();
        }
    }

    public static void finish(Villager builder, CompoundTag state, SettlementProject.Status status) {
        var town = town(builder, state);
        var project = project(builder, state);
        if (town != null && project != null && project.builder().equals(builder.getUUID())) {
            observe(builder, state);
            town.finishProject(project.id(), status);
        }
    }

    public static void reconcile(Villager builder) {
        var state = HiredWorkStateStore.state(builder);
        var project = project(builder, state);
        if (state.getCompound(BuilderTaskState.TASK_TAG).hasUUID("SettlementTown")
                && (project == null || !project.active() || !project.builder().equals(builder.getUUID())
                        || SettlementWorkforceService.duty(builder).filter(duty ->
                                duty.town().id().value().equals(project.town()) && duty.area().id().equals(project.area())).isEmpty()
                        || SettlementWorkforceService.hasPersonalCommitment(builder))) {
            finish(builder, state, SettlementProject.Status.CANCELLED);
            HiredWorkStateStore.pauseWork((ServerLevel) builder.level(), builder, HiredVillagerRole.BUILDER,
                    "interaction.work.status.paused", Map.of());
            BuilderTaskState.clearTask(state);
            HiredWorkSession.invalidate(builder);
        }
        if (!SettlementWorkforceService.hasPersonalCommitment(builder)) {
            SettlementWorkforceService.duty(builder).filter(duty -> duty.area().type() == SettlementAreaType.CONSTRUCTION).ifPresent(duty -> {
                var active = duty.town().activeProject(builder.getUUID());
                if (active != null && active.area().equals(duty.area().id())
                        && (!BuilderTaskState.hasTask(state) || active.id().equals(BuilderTaskState.jobId(state).orElse(null)))
                        && !state.getCompound(BuilderTaskState.TASK_TAG).equals(active.state().getCompound(BuilderTaskState.TASK_TAG))) {
                    active.restore(state);
                    HiredWorkSession.invalidate(builder);
                }
            });
        }
    }

    private static SettlementRecord town(Villager builder, CompoundTag state) {
        var task = state.getCompound(BuilderTaskState.TASK_TAG);
        return builder.level() instanceof ServerLevel level && task.hasUUID("SettlementTown")
                ? SettlementSavedData.get(level).get(new VillageAllegianceId(task.getUUID("SettlementTown"))).orElse(null) : null;
    }

    private static SettlementProject project(Villager builder, CompoundTag state) {
        var town = town(builder, state);
        return town == null ? null : BuilderTaskState.jobId(state).map(town.projects()::get).orElse(null);
    }

    private static ItemStack heldBlueprint(ServerPlayer player, UUID id) {
        for (ItemStack stack : player.getHandSlots()) {
            if (ConstructionBlueprintItem.previewData(stack).filter(preview -> preview.jobId().equals(id)).isPresent()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean canBuild(ServerPlayer player, SettlementRecord town) {
        return player != null && town != null && player.isAlive() && !player.isSpectator()
                && town.dimension().equals(player.level().dimension().location())
                && town.permits(player.getUUID(), SettlementPermission.BUILD);
    }
}
