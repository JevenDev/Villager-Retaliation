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
import net.minecraft.core.Direction;
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
        project.queue(player.getUUID());
        if (!town.queueProject(player.getUUID(), project)) return false;
        ConstructionBlueprintItem.updatePlacement(blueprint, level, plan, preview.origin(), preview.rotation());
        ConstructionBlueprintItem.markStarted(blueprint, builder, 0, level.getGameTime());
        tickQueue(level, town);
        return true;
    }

    public static String commission(ServerPlayer player, SettlementRecord town, UUID request, ResourceLocation structure,
                                    BlockPos anchor, Rotation rotation, UUID resident) {
        if (!canBuild(player, town)) return "permission";
        var existing = town.project(request);
        if (existing != null) {
            var accepted = scan(player.serverLevel(), structure, rotation);
            return accepted != null && existing.requestedBuilder().equals(resident) && existing.structure().equals(structure)
                    && existing.rotation() == rotation && existing.origin().equals(origin(accepted, anchor)) ? "" : "duplicate";
        }
        var level = player.serverLevel();
        if (SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.knowsProject(request))) return "duplicate";
        var plan = scan(level, structure, rotation);
        if (plan == null) return "prefab";
        var origin = origin(plan, anchor);
        String failure = placementFailure(player, town, plan, origin);
        if (!failure.isEmpty()) return failure;
        if (resident == null) return "builder";
        if (!resident.equals(SettlementProject.AUTOMATIC_BUILDER)
                && !VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id())
                        .map(record -> record.residents().containsKey(resident)).orElse(false)) return "builder";
        var area = workplace(level, town, plan, origin, request);
        if (area == null || !town.owns(area)) return "bounds";
        var project = SettlementProject.planned(town, area, resident, plan, origin, request, level.getGameTime());
        project.queue(player.getUUID());
        if (!town.queueProject(player.getUUID(), project)) return "capacity";
        tickQueue(level, town);
        return "";
    }

    static BuilderStructureScanner.StructurePlan scan(ServerLevel level, ResourceLocation structure, Rotation rotation) {
        var entry = BuilderStructureCatalog.byId(level.getServer(), structure).orElse(null);
        return entry == null ? null : BuilderStructureScanner.scan(level, entry, rotation).orElse(null);
    }

    /** Bounded, ordered dispatch. Waiting never edits the world or consumes partial resources. */
    public static void tickQueue(ServerLevel level, SettlementRecord town) {
        if (SettlementSavedData.get(level).readOnly() || !town.dimension().equals(level.dimension().location())) return;
        if(town.autonomous()!=null && !AutonomousVillageService.loaded(level,town)) return;
        for (var project : town.projects().values()) if (!project.unfinished()) SettlementConstructionMaterials.refund(level, town, project);
        int available = town.stage().projects() - (int) town.projects().values().stream().filter(SettlementProject::active).count();
        if (available <= 0) return;
        for (var project : List.copyOf(town.projects().values())) {
            if (!project.pending()) continue;
            String blocker = "";
            if (!town.permits(project.authority(), SettlementPermission.BUILD)) blocker = "permission";
            var builder = project.builder().equals(SettlementProject.AUTOMATIC_BUILDER)
                    ? town.duties().keySet().stream().map(id -> resolveBuilder(level, town, id))
                            .filter(candidate -> eligible(level, town, candidate)).findFirst().orElse(null)
                    : resolveBuilder(level, town, project.builder());
            if (blocker.isEmpty() && !eligible(level, town, builder)) blocker = "builder";
            if (blocker.isEmpty() && project.workplace() == null && !project.area().equals(town.duties().get(builder.getUUID()))) blocker = "builder";
            if (blocker.isEmpty()) {
                var previous = town.workers().get(builder.getUUID());
                if (previous != null) previous.restore(builder);
                reconcile(builder);
                if (BuilderTaskState.hasTask(HiredWorkStateStore.state(builder))) blocker = "builder";
            }
            var plan = scan(level, project.structure(), project.rotation());
            if (blocker.isEmpty() && (plan == null || plan.blocks().size() != project.total()
                    || !plan.worldMin(project.origin()).equals(project.min()) || !plan.worldMax(project.origin()).equals(project.max()))) blocker = "prefab";
            if (blocker.isEmpty() && !project.matchesPlan(plan)) blocker = "template_changed";
            if (blocker.isEmpty() && !town.owns(project)) blocker = "bounds";
            if (blocker.isEmpty() && protectedSite(level, town, project)) blocker = "blocked_existing";
            if (blocker.isEmpty()) {
                var site = BuilderSitePlanner.validateStartedSite(level, builder, plan, project.origin());
                if (!site.valid()) blocker = site.statusKey().replace("interaction.work.builder.", "");
            }
            if (blocker.isEmpty() && town.economy().constructionFunds().compareTo(CurrencyAmount.of(project.fee(), 1)) < 0) blocker = "funds";
            // This task marker delegates presentation to the escrow's current detailed blocker.
            // It also lets a later builder/funds/site wait supersede a stale material diagnosis.
            if (blocker.isEmpty() && !SettlementConstructionMaterials.reserve(level, town, project, plan, builder)) blocker = "materials";
            if (!blocker.isEmpty()) {
                if (project.waitFor(blocker)) town.changed();
                // The queue is strict: later requests cannot consume supplies ahead of its head.
                break;
            }
            if (!town.activateProject(project, builder.getUUID())) {
                SettlementConstructionMaterials.refund(level, town, project);
                if (project.waitFor("funds")) town.changed();
                break;
            }
            SettlementBackgroundService.onActive(builder);
            SettlementWorkforceService.reconcile(level, builder);
            var state = HiredWorkStateStore.state(builder);
            HiredWorkStateStore.pauseWork(level, builder, HiredVillagerRole.BUILDER, "interaction.work.builder.started", Map.of());
            project.restore(state);
            state.remove("NextWorkGameTime");
            state.remove("ProgressTicks");
            SettlementConstructionSiteService.observeArea(level, town, town.workplace(project.area()), null);
            town.recordEvent(level.getGameTime(), SettlementEvent.Type.PROJECT_STARTED, project.label());
            HiredWorkSession.invalidate(builder);
            SettlementBackgroundService.onUnloaded(builder);
            var care = town.residents().get(builder.getUUID());
            if (care != null) care.remember(builder);
            if (--available <= 0) break;
        }
    }

    private static boolean protectedSite(ServerLevel level, SettlementRecord town, SettlementProject project) {
        if(project.repairOf()!=null) return !AutonomousVillageMaintenance.safe(level,town,project);
        var footprint = new AABB(Vec3.atLowerCornerOf(project.min()), Vec3.atLowerCornerOf(project.max().offset(1, 1, 1)));
        if (town.areas().values().stream().anyMatch(area -> area.type() != SettlementAreaType.CONSTRUCTION
                && footprint.intersects(new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1)))))) return true;
        for (var pos : BlockPos.betweenClosed(project.min(), project.max())) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) != null) return true;
        }
        return false;
    }

    public static boolean reorder(ServerPlayer player, SettlementRecord town, UUID project, UUID before, long revision) {
        return canBuild(player, town) && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && town.reorderProject(player.getUUID(), project, before, revision);
    }

    static BlockPos origin(BuilderStructureScanner.StructurePlan plan, BlockPos anchor) {
        return anchor.offset(-plan.localCenter().getX(), -plan.localMin().getY(), -plan.localCenter().getZ());
    }

    static boolean eligible(ServerLevel level, SettlementRecord town, Villager builder) {
        if (town.autonomous()!=null && (builder==null || level.getEntity(builder.getUUID())!=builder || !level.isPositionEntityTicking(builder.blockPosition()))) return false;
        if (builder == null || !builder.isAlive() || builder.isBaby() || VillagerDownedService.isDowned(builder)
                || Math.abs(builder.getX() - town.center().getX()) > town.radius() + 16
                || Math.abs(builder.getZ() - town.center().getZ()) > town.radius() + 16
                || SettlementWorkforceService.hasPersonalCommitment(builder)
                || !VillageAllegianceApi.canonicalPrimary(level, builder).filter(town.id()::equals).isPresent()
                || town.activeProject(builder.getUUID()) != null || town.departures().containsKey(builder.getUUID())) return false;
        var area = town.workplace(town.duties().get(builder.getUUID()));
        var settings = town.effectiveWorkerSettings(builder.getUUID());
        return area != null && area.type() == SettlementAreaType.CONSTRUCTION && area.enabled() && town.owns(area)
                && (settings == null || settings.options().get(SettlementWorkerSetting.ENABLED) == 1);
    }

    static String placementFailure(ServerPlayer player, SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin) {
        return placementFailure(player.serverLevel(),player,town,plan,origin);
    }
    static String placementFailure(ServerLevel level, ServerPlayer player, SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin) {
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        if (town.stage().projects() == 0) return "stage_capacity";
        if (town.projects().values().stream().filter(SettlementProject::pending).count() >= SettlementRecord.CONSTRUCTION_QUEUE_LIMIT) return "queue_full";
        if (!town.contains(min, max)
                || level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) return "bounds";
        var footprint = new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max.offset(1, 1, 1)));
        if (SettlementSavedData.get(level).settlements().stream().filter(other -> other.dimension().equals(town.dimension()))
                .flatMap(other -> java.util.stream.Stream.concat(other.projects().values().stream(), other.projectHistory().values().stream()).filter(other::owns)).anyMatch(project -> (project.reservesFootprint() || project.status() == SettlementProject.Status.COMPLETED)
                && new AABB(Vec3.atLowerCornerOf(project.min()), Vec3.atLowerCornerOf(project.max().offset(1, 1, 1)))
                        .intersects(footprint))) return "overlap";
        if (town.areas().values().stream().anyMatch(area -> area.type() != SettlementAreaType.CONSTRUCTION
                && new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1)))
                        .intersects(footprint))) return "overlap";
        if (SettlementSavedData.get(level).settlements().stream()
                .filter(other -> !other.id().equals(town.id()) && other.dimension().equals(town.dimension()))
                .anyMatch(other -> other.areas().values().stream().filter(other::owns).anyMatch(area ->
                        footprint.intersects(new AABB(Vec3.atLowerCornerOf(area.min()), Vec3.atLowerCornerOf(area.max().offset(1, 1, 1))))))) return "overlap";
        var area = workplace(level, town, plan, origin, UUID.randomUUID());
        if (area == null || !town.owns(area)) return "bounds";
        var check = BuilderSitePlanner.validateSite(level, player, null, HiredWorkArea.fromBounds(area.min(), area.max(), true), plan, origin);
        return check.valid() ? "" : check.statusKey().replace("interaction.work.builder.", "");
    }

    static SettlementArea workplace(ServerLevel level, SettlementRecord town, BuilderStructureScanner.StructurePlan plan, BlockPos origin, UUID id) {
        var min = plan.worldMin(origin);
        var max = plan.worldMax(origin);
        var border = level.getWorldBorder();
        if (!town.contains(min, max) || level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !border.isWithinBounds(min) || !border.isWithinBounds(max)) return null;
        var lower = min;
        var upper = max;
        for (var direction : Direction.values()) {
            int margin = direction.getAxis() == Direction.Axis.Y ? 2 : 4;
            for (int step = 0; step < margin; step++) {
                boolean negative = direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE;
                var expandedMin = negative ? lower.relative(direction) : lower;
                var expandedMax = negative ? upper : upper.relative(direction);
                if (!town.contains(expandedMin, expandedMax)
                        || level.isOutsideBuildHeight(expandedMin) || level.isOutsideBuildHeight(expandedMax)
                        || !border.isWithinBounds(expandedMin) || !border.isWithinBounds(expandedMax)) break;
                lower = expandedMin;
                upper = expandedMax;
            }
        }
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
        var lead = project.builder().equals(builder.getUUID()) ? builder
                : resolveBuilder((ServerLevel) builder.level(), town, project.builder());
        if (lead != null) synchronizePayments(town, lead);
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

    public static boolean configureCrew(ServerPlayer player, SettlementRecord town, UUID projectId, long revision,
                                        int priority, List<UUID> assistants) {
        if (!canBuild(player, town) || !town.permits(player.getUUID(), SettlementPermission.STAFF)
                || SettlementSavedData.get(player.serverLevel()).readOnly()
                || SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) != town
                || assistants == null || assistants.size() >= SettlementConstructionCrew.MAX_MEMBERS) return false;
        var project = town.projects().get(projectId);
        if (project == null || !project.active() || project.crewRevision() != revision
                || project.workplace() == null && !town.worksitePlans().containsKey(project.area())) return false;
        for (var id : assistants) {
            if (id == null) return false;
            var home = town.workplace(town.duties().get(id));
            var settings = town.effectiveWorkerSettings(id);
            var builder = resolveBuilder(player.serverLevel(), town, id);
            if (home == null || home.type() != SettlementAreaType.CONSTRUCTION || !home.enabled()
                    || !town.worksitePlans().containsKey(home.id()) || builder == null || !builder.isAlive() || builder.isBaby()
                    || VillagerDownedService.isDowned(builder) || SettlementWorkforceService.hasPersonalCommitment(builder)
                    || settings != null && settings.options().get(SettlementWorkerSetting.ENABLED) != 1) return false;
        }
        if (!project.configureCrew(revision, priority, assistants)) return false;
        town.changed();
        return true;
    }

    public static boolean cancel(ServerPlayer player, SettlementRecord town, UUID projectId) {
        var project = town.projects().get(projectId);
        if (!canBuild(player, town) || project == null || !project.unfinished()) {
            return false;
        }
        town.finishProject(projectId, SettlementProject.Status.CANCELLED);
        SettlementConstructionMaterials.refund(player.serverLevel(), town, project);
        town.recordEvent(player.serverLevel().getGameTime(), SettlementEvent.Type.PROJECT_CANCELLED, project.label());
        var members = new ArrayList<>(project.assistants());
        members.add(project.builder());
        for (var member : members) {
            if (player.serverLevel().getEntity(member) instanceof Villager builder) {
                SettlementWorkforceService.reconcile(player.serverLevel(), builder);
                reconcile(builder);
            }
            var worker = town.workers().get(member);
            if (worker != null && projectId.equals(BuilderTaskState.jobId(worker.settings()).orElse(null))) {
                BuilderTaskState.clearTask(worker.settings());
                worker.changed();
            }
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
                && project.includes(builder.getUUID()) && duty.town().assignedProject(builder.getUUID()) == project
                && builder.level().getWorldBorder().isWithinBounds(project.min()) && builder.level().getWorldBorder().isWithinBounds(project.max())
                && project.town().equals(duty.town().id().value()) && duty.area().type() == SettlementAreaType.CONSTRUCTION
                && project.within(duty.town().workplace(project.area())) && project.structure().equals(plan.entry().id()) && project.rotation() == plan.rotation()
                && project.matchesPlan(plan)
                && project.origin().equals(BuilderTaskState.origin(state))
                && project.min().equals(project.origin().offset(plan.localMin())) && project.max().equals(project.origin().offset(plan.localMax()))
                && project.total() == plan.blocks().size() && !SettlementWorkforceService.hasPersonalCommitment(builder);
    }

    public static boolean templateMatches(Villager builder, CompoundTag state, BuilderStructureScanner.StructurePlan plan) {
        var project = project(builder, state);
        return project == null || project.matchesPlan(plan);
    }

    public static void observe(Villager builder, CompoundTag state) {
        var project = project(builder, state);
        if (project == null || !project.active() || !project.includes(builder.getUUID())) return;
        var town = town(builder, state);
        if (project.crew() != null) {
            var plan = scan((ServerLevel) builder.level(), project.structure(), project.rotation());
            if (plan != null && plan.blocks().size() == project.total() && project.matchesPlan(plan) && (project.site() == null || !project.site().pending())) {
                SettlementConstructionCoordination.refresh(town, project, plan, (ServerLevel) builder.level());
            }
            if (!BuilderTaskState.blockedReason(state).isEmpty()) project.crew().release(builder.getUUID());
        }
        if (project.observe(state)) town.changed();
    }

    public static void finish(Villager builder, CompoundTag state, SettlementProject.Status status) {
        var town = town(builder, state);
        var project = project(builder, state);
        release(builder, state);
        if (town != null && project != null && project.active() && project.includes(builder.getUUID())
                && (status == SettlementProject.Status.CANCELLED ? project.builder().equals(builder.getUUID())
                        : project.crew() == null || project.placed() == project.total())) {
            observe(builder, state);
            if (status == SettlementProject.Status.COMPLETED) SettlementConstructionMaterials.complete((ServerLevel) builder.level(), town, project);
            town.finishProject(project.id(), status);
            if (status == SettlementProject.Status.COMPLETED)
                com.jvn.villagerretaliation.progress.ProgressHistoryService.contribution((ServerLevel) builder.level(),
                        project.authority(), town.id(), "construction", project.structure().toString(), 1);
            SettlementConstructionMaterials.refund((ServerLevel) builder.level(), town, project);
            synchronizePayments(town, builder);
            town.recordEvent(builder.level().getGameTime(), status == SettlementProject.Status.COMPLETED
                    ? SettlementEvent.Type.PROJECT_COMPLETED : SettlementEvent.Type.PROJECT_CANCELLED, project.label());
        }
    }

    public static void reconcile(Villager builder) {
        var level = (ServerLevel) builder.level();
        var state = HiredWorkStateStore.state(builder);
        var home = SettlementWorkforceService.home(level, builder).orElse(null);
        if (home != null) synchronizePayments(home, builder);
        var current = project(builder, state);
        var duty = SettlementWorkforceService.duty(builder).orElse(null);
        var active = home == null || duty == null || SettlementWorkforceService.hasPersonalCommitment(builder)
                ? null : home.assignedProject(builder.getUUID());
        if (state.getCompound(BuilderTaskState.TASK_TAG).hasUUID("SettlementTown") && (current == null || current != active)) {
            if (current != null && current.active() && current.builder().equals(builder.getUUID())
                    && (duty == null || !current.area().equals(duty.area().id())
                            || !current.town().equals(duty.town().id().value()) || SettlementWorkforceService.hasPersonalCommitment(builder))) {
                if (current.waitFor("builder")) town(builder, state).changed();
            }
            release(builder, state);
            HiredWorkStateStore.pauseWork(level, builder, HiredVillagerRole.BUILDER, "interaction.work.status.paused", Map.of());
            BuilderTaskState.clearTask(state);
            state.remove("SettlementConstructionPosition");
            HiredWorkSession.invalidate(builder);
        }
        if (active != null && (!BuilderTaskState.hasTask(state) || active.id().equals(BuilderTaskState.jobId(state).orElse(null)))
                && (!BuilderTaskState.hasTask(state) || active.crew() == null
                        && !state.getCompound(BuilderTaskState.TASK_TAG).equals(active.state().getCompound(BuilderTaskState.TASK_TAG)))) {
            active.restore(state);
            HiredWorkSession.invalidate(builder);
        }
    }

    public static HiredWorkArea workArea(Villager builder, CompoundTag state) {
        var duty = SettlementWorkforceService.duty(builder).orElse(null);
        var project = project(builder, state);
        var area = duty == null ? null : project != null && project.active() && project.includes(builder.getUUID())
                ? duty.town().workplace(project.area()) : duty.area();
        return area == null ? null : HiredWorkArea.fromBounds(area.min(), area.max(), true);
    }

    public static com.jvn.villagerretaliation.interaction.HiredJobSite jobSite(Villager builder, CompoundTag state,
                                                                            com.jvn.villagerretaliation.interaction.HiredJobSite fallback) {
        var area = workArea(builder, state);
        return area == null ? fallback : com.jvn.villagerretaliation.interaction.HiredJobSite.fromWorkArea(area);
    }

    public static boolean cooperative(Villager builder, CompoundTag state) {
        var project = project(builder, state);
        return project != null && project.active() && project.crew() != null && project.includes(builder.getUUID());
    }

    public static int selectPlacement(Villager builder, CompoundTag state, BuilderStructureScanner.StructurePlan plan, int fallback) {
        var project = project(builder, state);
        if (project == null || project.crew() == null) return fallback;
        if (!allows(builder, state, plan)) return -1;
        int index = SettlementConstructionCoordination.select(town(builder, state), project, builder.getUUID(), plan,
                (ServerLevel) builder.level(), builder.level().getGameTime(), com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(builder));
        if (index >= 0) BuilderTaskState.setPlacedIndex(state, index);
        return index;
    }

    public static boolean claimClearing(Villager builder, CompoundTag state,
                                        com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker.PlacementGroup group) {
        var project = project(builder, state);
        return project == null || project.crew() == null || project.crew().acquire(builder.getUUID(), -1,
                SettlementConstructionCoordination.positions(group), builder.level().getGameTime());
    }

    public static void release(Villager builder, CompoundTag state) {
        var project = project(builder, state);
        if (project != null && project.crew() != null) project.crew().release(builder.getUUID());
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
                && !SettlementSavedData.get(player.serverLevel()).readOnly()
                && SettlementSavedData.get(player.serverLevel()).get(town.id()).orElse(null) == town
                && town.permits(player.getUUID(), SettlementPermission.BUILD);
    }
}
