package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceApi;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.combat.downed.VillagerDownedService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureCatalog;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.item.ConstructionBlueprintItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import com.jvn.villagerretaliation.interaction.VillagerWalletService;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class SettlementConstructionService {
    private SettlementConstructionService() {
    }

    public static boolean start(ServerPlayer player, SettlementRecord town, UUID resident, UUID blueprintId) {
        if (!canBuild(player, town) || town.stage().projects() == 0 || resident == null) return false;
        var level = player.serverLevel();
        var builder = resolveBuilder(level, town, resident);
        if (!eligible(level, town, builder)) return false;
        var area = town.workplace(town.duties().get(resident));
        if (area == null || area.type() != SettlementAreaType.CONSTRUCTION) return false;
        SettlementConstructionSiteService.update(level, town);
        ItemStack blueprint = heldBlueprint(player, blueprintId);
        var preview = ConstructionBlueprintItem.previewData(blueprint).orElse(null);
        if (preview == null || preview.started() || preview.expired() || preview.completed()
                || !preview.dimension().equals(level.dimension())
                || SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.knowsProject(blueprintId))) {
            return false;
        }
        var entry = BuilderStructureCatalog.byId(level.getServer(), preview.structureId()).orElse(null);
        var plan = entry == null ? null : BuilderStructureScanner.scan(level, entry, preview.rotation()).orElse(null);
        if (plan == null) {
            return false;
        }
        var project = SettlementProject.create(town, area.id(), resident, plan, preview.origin(), blueprintId, level.getGameTime());
        if (!project.within(area) || level.isOutsideBuildHeight(project.min()) || level.isOutsideBuildHeight(project.max())
                || !level.getWorldBorder().isWithinBounds(project.min()) || !level.getWorldBorder().isWithinBounds(project.max())
                || !BuilderSitePlanner.validateSite(level, player, builder,
                        HiredWorkArea.fromBounds(area.min(), area.max(), true), plan, preview.origin()).valid()) {
            return false;
        }
        project.reservePayment(plan.price());
        SettlementBackgroundService.onActive(builder);
        var duty = SettlementWorkforceService.duty(builder).orElse(null);
        if (duty == null || duty.town() != town || !duty.area().id().equals(area.id())) return false;
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
        town.recordEvent(level.getGameTime(), SettlementEvent.Type.PROJECT_STARTED, project.label());
        HiredWorkSession.invalidate(builder);
        SettlementBackgroundService.onUnloaded(builder);
        var care = town.residents().get(resident);
        if (level.getEntity(resident) != builder && care != null) {
            care.remember(builder);
            town.changed();
        }
        return true;
    }

    public static String commission(ServerPlayer player, SettlementRecord town, UUID request, ResourceLocation structure,
                                    BlockPos anchor, Rotation rotation, UUID resident) {
        if (!canBuild(player, town)) return "permission";
        var existing = town.project(request);
        if (existing != null) {
            var accepted = scan(player.serverLevel(), structure, rotation);
            return accepted != null && existing.builder().equals(resident) && existing.structure().equals(structure)
                    && existing.rotation() == rotation && existing.origin().equals(origin(accepted, anchor)) ? "" : "duplicate";
        }
        var level = player.serverLevel();
        if (SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.knowsProject(request))) return "duplicate";
        var plan = scan(level, structure, rotation);
        if (plan == null) return "prefab";
        var origin = origin(plan, anchor);
        String failure = placementFailure(player, town, plan, origin);
        if (!failure.isEmpty()) return failure;
        var builder = resolveBuilder(level, town, resident);
        if (!eligible(level, town, builder)) return "builder";
        var previous = town.workers().get(resident);
        if (previous != null) previous.restore(builder);
        reconcile(builder);
        if (BuilderTaskState.hasTask(HiredWorkStateStore.state(builder))) return "builder";
        var area = workplace(level, town, plan, origin, request);
        if (area == null || !town.owns(area)) return "bounds";
        var project = SettlementProject.planned(town, area, resident, plan, origin, request, level.getGameTime());
        if (town.economy().constructionFunds().compareTo(CurrencyAmount.of(plan.price(), 1)) < 0) return "funds";
        if (!town.startProject(player.getUUID(), project)) return "capacity";
        SettlementWorkforceService.reconcile(level, builder);
        var state = HiredWorkStateStore.state(builder);
        HiredWorkStateStore.pauseWork(level, builder, HiredVillagerRole.BUILDER, "interaction.work.builder.started", Map.of());
        project.restore(state);
        state.remove("NextWorkGameTime");
        state.remove("ProgressTicks");
        SettlementConstructionSiteService.observeArea(level, town, area, null);
        town.recordEvent(level.getGameTime(), SettlementEvent.Type.PROJECT_STARTED, project.label());
        HiredWorkSession.invalidate(builder);
        SettlementBackgroundService.onUnloaded(builder);
        var care = town.residents().get(resident);
        if (care != null) care.remember(builder);
        return "";
    }

    static BuilderStructureScanner.StructurePlan scan(ServerLevel level, ResourceLocation structure, Rotation rotation) {
        var entry = BuilderStructureCatalog.byId(level.getServer(), structure).orElse(null);
        return entry == null ? null : BuilderStructureScanner.scan(level, entry, rotation).orElse(null);
    }

    static BlockPos origin(BuilderStructureScanner.StructurePlan plan, BlockPos anchor) {
        return anchor.offset(-plan.localCenter().getX(), -plan.localMin().getY(), -plan.localCenter().getZ());
    }

    static boolean eligible(ServerLevel level, SettlementRecord town, Villager builder) {
        if (builder == null || !builder.isAlive() || builder.isBaby() || VillagerDownedService.isDowned(builder)
                || Math.abs(builder.getX() - town.center().getX()) > town.radius() + 16
                || Math.abs(builder.getZ() - town.center().getZ()) > town.radius() + 16
                || SettlementWorkforceService.hasPersonalCommitment(builder)
                || !VillageAllegianceApi.canonicalPrimary(level, builder).filter(town.id()::equals).isPresent()
                || !HiredVillagerRoles.availableRoles(level, builder).contains(HiredVillagerRole.BUILDER)
                || town.activeProject(builder.getUUID()) != null || town.departures().containsKey(builder.getUUID())) return false;
        var area = town.workplace(town.duties().get(builder.getUUID()));
        var settings = town.effectiveWorkerSettings(builder.getUUID());
        return area == null || area.type() == SettlementAreaType.CONSTRUCTION && area.enabled() && (settings == null || settings.options().get(SettlementWorkerSetting.ENABLED) == 1);
    }

    static String placementFailure(ServerPlayer player, SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin) {
        var level = player.serverLevel();
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        if (town.stage().projects() == 0 || town.projects().values().stream().filter(SettlementProject::active).count() >= town.stage().projects()) return "capacity";
        if (!town.contains(min, max)
                || level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) return "bounds";
        var footprint = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max.offset(1, 1, 1)));
        if (SettlementSavedData.get(level).settlements().stream().filter(other -> other.dimension().equals(town.dimension()))
                .flatMap(other -> other.projects().values().stream().filter(other::owns)).anyMatch(project -> project.reservesFootprint()
                && new AABB(Vec3.atLowerCornerOf(project.min()), Vec3.atLowerCornerOf(project.max().offset(1, 1, 1)))
                        .intersects(footprint))) return "overlap";
        if (town.areas().values().stream().anyMatch(area -> area.type().changesTerrain() && area.type() != SettlementAreaType.CONSTRUCTION
                && new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1)))
                        .intersects(footprint))) return "overlap";
        if (SettlementSavedData.get(level).settlements().stream()
                .filter(other -> !other.id().equals(town.id()) && other.dimension().equals(town.dimension()))
                .anyMatch(other -> other.areas().values().stream().filter(other::owns).anyMatch(area ->
                        footprint.intersects(new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1))))))) return "overlap";
        var area = workplace(level, town, plan, origin, UUID.randomUUID());
        if (area == null || !town.owns(area)) return "bounds";
        var check = BuilderSitePlanner.validateSite(level, player, null, HiredWorkArea.fromBounds(area.min(), area.max(), true), plan, origin);
        return check.valid() ? "" : "placement";
    }

    private static SettlementArea workplace(ServerLevel level, SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin, UUID id) {
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        var border = level.getWorldBorder();
        var lower = new BlockPos(Math.max(min.getX() - 4, (int) Math.ceil(Math.max(town.center().getX() - town.radius(), border.getMinX()))),
                Math.max(min.getY() - 2, level.getMinBuildHeight()),
                Math.max(min.getZ() - 4, (int) Math.ceil(Math.max(town.center().getZ() - town.radius(), border.getMinZ()))));
        var upper = new BlockPos(Math.min(max.getX() + 4, (int) Math.floor(Math.min(town.center().getX() + town.radius(), border.getMaxX() - 1))),
                Math.min(max.getY() + 2, level.getMaxBuildHeight() - 1),
                Math.min(max.getZ() + 4, (int) Math.floor(Math.min(town.center().getZ() + town.radius(), border.getMaxZ() - 1))));
        try {
            var name = plan.entry().menuLabel();
            return new SettlementArea(id, SettlementAreaType.CONSTRUCTION, name.substring(0, Math.min(name.length(), SettlementArea.MAX_NAME_LENGTH)), lower, upper, 5, true);
        } catch (IllegalArgumentException invalidBounds) {
            return null;
        }
    }

    public static void placed(Villager builder, CompoundTag state, BlockPos position, net.minecraft.world.level.block.state.BlockState before) {
        var town = town(builder, state);
        var project = project(builder, state);
        if (town == null || project == null || !project.active()) return;
        var level = (ServerLevel) builder.level();
        var after = level.getBlockState(position);
        if (before == after) return;
        project.observeSite(town.workplace(project.area()));
        var site = project.site();
        var origin = SettlementTerrainSection.sectionOrigin(position);
        var min = new BlockPos(Math.max(origin.getX(), site.min().getX()), Math.max(origin.getY(), site.min().getY()), Math.max(origin.getZ(), site.min().getZ()));
        var max = new BlockPos(Math.min(origin.getX() + 15, site.max().getX()), Math.min(origin.getY() + 15, site.max().getY()), Math.min(origin.getZ() + 15, site.max().getZ()));
        site.record(SettlementTerrainSection.capture("ConstructionSurvey", project.id(), min, max, level.getChunkAt(position)));
        var entity = level.getBlockEntity(position);
        var placement = new SettlementConstructionPlacement(level, List.of(new SettlementConstructionPlacement.Part(position, before, after,
                entity == null ? null : entity.saveWithoutMetadata(level.registryAccess()))));
        placement.acknowledgeNative(level, project.id());
        site.nativePlacement(placement);
        worked(town, project, builder);
        town.changed();
    }

    public static void worked(Villager builder, CompoundTag state) {
        var town = town(builder, state);
        var project = project(builder, state);
        if (town != null && project != null && project.active()) worked(town, project, builder);
    }

    static void worked(SettlementRecord town, SettlementProject project, Villager builder) {
        if (project.earnPayment()) town.changed();
        synchronizePayments(town, builder);
    }

    static void synchronizePayments(SettlementRecord town, Villager builder) {
        var care = town.residents().get(builder.getUUID());
        if (care != null) care.restoreWallet(builder);
        var receipts = builder.getPersistentData().getCompound("SettlementConstructionPayments");
        boolean changed = false;
        for (var project : town.projects().values()) {
            if (!project.builder().equals(builder.getUUID()) || !project.paymentEarned() || project.fee() == 0) continue;
            if (receipts.getBoolean(project.id().toString())) {
                if (project.acknowledgePayment()) changed = true;
                continue;
            }
            VillagerWalletService.addCurrency(builder, project.fee());
            project.acknowledgePayment();
            receipts.putBoolean(project.id().toString(), true);
            changed = true;
        }
        if (changed) {
            builder.getPersistentData().put("SettlementConstructionPayments", receipts);
            var worker = town.workers().get(builder.getUUID());
            if (worker != null) worker.rememberPayments(builder);
            if (care != null) care.remember(builder);
            town.changed();
        }
    }

    static Villager resolveBuilder(ServerLevel level, SettlementRecord town, UUID resident) {
        if (town.departures().containsKey(resident)
                || !VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id())
                        .map(record -> record.residents().containsKey(resident)).orElse(false)) return null;
        for (var dimension : level.getServer().getAllLevels()) {
            var entity = dimension.getEntity(resident);
            if (entity != null) return entity instanceof Villager villager && villager.level() == level ? villager : null;
        }
        var care = town.residents().get(resident);
        return care != null && care.availableForWork() ? care.context(level) : null;
    }

    public static boolean cancel(ServerPlayer player, SettlementRecord town, UUID projectId) {
        var project = town.projects().get(projectId);
        if (!canBuild(player, town) || project == null || !project.active()) {
            return false;
        }
        town.finishProject(projectId, SettlementProject.Status.CANCELLED);
        town.recordEvent(player.serverLevel().getGameTime(), SettlementEvent.Type.PROJECT_CANCELLED, project.label());
        if (player.serverLevel().getEntity(project.builder()) instanceof Villager builder) {
            reconcile(builder);
            SettlementWorkforceService.reconcile(player.serverLevel(), builder);
        }
        var worker = town.workers().get(project.builder());
        if (worker != null && projectId.equals(BuilderTaskState.jobId(worker.settings()).orElse(null))) {
            BuilderTaskState.clearTask(worker.settings());
        }
        ConstructionBlueprintItem.expireMatchingBlueprints(player.serverLevel(), projectId);
        return true;
    }

    public static PendingClearance pendingClearance(Villager builder) {
        if (!(builder.level() instanceof ServerLevel level) || SettlementWorkforceService.hasPersonalCommitment(builder)) return null;
        var town = SettlementWorkforceService.home(level, builder).orElse(null);
        if (town == null) return null;
        var positions = new ArrayList<BlockPos>();
        var body = builder.getBoundingBox().inflate(-0.02D);
        SettlementProject blocked = null;
        for (var project : town.projects().values()) {
            if (project.active() || !project.builder().equals(builder.getUUID()) || project.site() == null || !project.site().pending()) continue;
            for (var placement : project.site().placements().values()) {
                for (var part : placement.parts()) {
                    var pos = part.position();
                    var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk == null || placement.applied(chunk, project.id(), pos)
                            || part.after().getCollisionShape(chunk, pos).isEmpty()) continue;
                    positions.add(pos);
                    if (blocked == null && body.intersects(new AABB(pos))) blocked = project;
                }
            }
        }
        return blocked == null ? null : new PendingClearance(blocked.site().area(), blocked.origin(), blocked.label(), List.copyOf(positions));
    }

    public record PendingClearance(SettlementArea area, BlockPos center, String label, List<BlockPos> positions) {
    }

    public static boolean prepare(Villager builder, CompoundTag state) {
        var project = project(builder, state);
        if (project != null && !town(builder, state).owns(project)) return false;
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
        return project.active() && duty != null && duty.area().enabled() && duty.town().owns(project)
                && project.builder().equals(builder.getUUID())
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
        if (town != null && project != null && project.active() && project.builder().equals(builder.getUUID())) {
            observe(builder, state);
            town.finishProject(project.id(), status);
            synchronizePayments(town, builder);
            town.recordEvent(builder.level().getGameTime(), status == SettlementProject.Status.COMPLETED
                    ? SettlementEvent.Type.PROJECT_COMPLETED : SettlementEvent.Type.PROJECT_CANCELLED, project.label());
        }
    }

    public static void reconcile(Villager builder) {
        var state = HiredWorkStateStore.state(builder);
        SettlementWorkforceService.home((ServerLevel) builder.level(), builder).ifPresent(town -> synchronizePayments(town, builder));
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

    static boolean canBuild(ServerPlayer player, SettlementRecord town) {
        return player != null && town != null && player.isAlive() && !player.isSpectator()
                && town.dimension().equals(player.level().dimension().location())
                && town.permits(player.getUUID(), SettlementPermission.BUILD);
    }
}
