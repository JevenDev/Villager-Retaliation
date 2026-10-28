package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementManagementActionPayload;
import com.jvn.villagerretaliation.network.SettlementManagementActionResultPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Entry;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.network.VillageOverviewPayload.Presence;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.util.VillagerProfessionUtil;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import com.jvn.villagerretaliation.villager.VillagerRecoveryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementManagementService {
    private SettlementManagementService() {
    }

    public static void open(ServerPlayer player, VillageAllegianceId town) {
        request(player, new SettlementManagementRequestPayload(town.value(), Page.SUMMARY, 0, "", 0, true));
    }

    public static void request(ServerPlayer player, SettlementManagementRequestPayload request) {
        if (!player.isAlive() || player.isSpectator()
                || !ServerboundRequestLimiter.tryAcquire(player, SettlementManagementRequestPayload.TYPE.id(), 5L)) {
            return;
        }
        SettlementSavedData.get(player.serverLevel()).simulation.requests.submit(player, request,
                snapshot -> PacketDistributor.sendToPlayer(player, snapshot));
    }

    public static void action(ServerPlayer player, SettlementManagementActionPayload payload) {
        String failure = ServerboundRequestLimiter.tryAcquire(player, SettlementManagementActionPayload.TYPE.id(), 5L)
                ? executeFailure(player, payload) : "busy";
        PacketDistributor.sendToPlayer(player, new SettlementManagementActionResultPayload(
                payload.town(), failure.isEmpty(), payload.request(), failure));
    }

    static boolean execute(ServerPlayer player, SettlementManagementActionPayload payload) {
        return executeFailure(player, payload).isEmpty();
    }

    static String executeFailure(ServerPlayer player, SettlementManagementActionPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) return "player_unavailable";
        var data = SettlementSavedData.get(player.serverLevel());
        if (data.readOnly()) return "recovery_read_only";
        var town = data.get(new VillageAllegianceId(payload.town())).orElse(null);
        if (town == null || !town.id().value().equals(payload.town())) return "town_missing";
        if (!town.dimension().equals(player.serverLevel().dimension().location())) return "wrong_dimension";
        String failure = SettlementManagementValidation.permission(player, town, payload.command());
        if (!failure.isEmpty()) return failure;
        SettlementSimulationService.managed(player, town);
        failure = SettlementManagementValidation.selection(player, town, payload.command());
        if (!failure.isEmpty()) return failure;
        return apply(player, town, payload.command());
    }

    private static String apply(ServerPlayer player, SettlementRecord town, SettlementManagementActionPayload.Command command) {
        ServerLevel level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var id = town.id();
        return switch (command) {
            case SettlementManagementActionPayload.SaveStorageFilter filter -> SettlementStorageService.saveFilter(player, town, filter);
            case SettlementManagementActionPayload.StorageFilter filter -> SettlementStorageService.configureFilter(player, town, filter);
            case SettlementManagementActionPayload.SaveStorage storage -> SettlementStorageService.configure(player, town, storage);
            case SettlementManagementActionPayload.SaveRoute route -> SettlementGuardService.configure(player, town, route);
            case SettlementManagementActionPayload.ClaimPlot plot -> SettlementPlotService.claim(
                    player, town, plot.x(), plot.z(), plot.free(), plot.revision(), plot.price());
            case SettlementManagementActionPayload.CommissionBuilding build -> SettlementConstructionService.commission(player, town,
                    build.request(), build.structure(), build.anchor(), build.rotation(), build.builder());
            case SettlementManagementActionPayload.Home home -> result(home.assign()
                    ? SettlementHousingService.assign(player, town, home.resident(), home.bed())
                    : SettlementHousingService.release(player, town, home.resident(), home.bed()), command);
            case SettlementManagementActionPayload.Management management -> result(town.setManualManagement(player.getUUID(), management.manual()), command);
            case SettlementManagementActionPayload.Advance ignored -> result(SettlementProgressionService.advance(player, id), command);
            case SettlementManagementActionPayload.View ignored -> result(SettlementViewService.open(player, id), command);
            case SettlementManagementActionPayload.DraftBlueprint draft -> result(SettlementBlueprintService.draft(player, town, draft.structure()), command);
            case SettlementManagementActionPayload.PlaceBlueprint place -> result(SettlementBlueprintService.place(player, town, place.blueprint(), place.anchor(), place.rotation()), command);
            case SettlementManagementActionPayload.StartProject start -> result(SettlementConstructionService.start(player, town, start.resident(), start.blueprint()), command);
            case SettlementManagementActionPayload.ReorderProject project -> result(SettlementConstructionService.reorder(player, town,
                    project.project(), project.before(), project.revision()), command);
            case SettlementManagementActionPayload.ConfigureProject project -> result(SettlementConstructionService.configureCrew(player, town,
                    project.project(), project.revision(), project.priority(), project.assistants()), command);
            case SettlementManagementActionPayload.CancelProject cancel -> result(SettlementConstructionService.cancel(player, town, cancel.project()), command);
            case SettlementManagementActionPayload.Brew brew -> result(SettlementBrewingService.configure(player, town, brew.resident(), brew.area(), brew.order()), command);
            case SettlementManagementActionPayload.SaveEquipment save -> result(save.configuration() == null
                    ? town.removeEquipmentPlan(player.getUUID(), save.area(), save.revision())
                    : town.setEquipmentPlan(player.getUUID(), save.area(), save.configuration(), save.revision()), command);
            case SettlementManagementActionPayload.SaveWorksite save -> result(SettlementWorksiteService.configure(
                    player, town, save.area(), save.plan(), save.adopt()), command);
            case SettlementManagementActionPayload.ResetWorker reset -> result(SettlementWorksiteService.reset(
                    player, town, reset.resident(), reset.area(), reset.mask()), command);
            case SettlementManagementActionPayload.FillStaffing fill -> result(SettlementWorksiteService.fill(player, town, fill.area()), command);
            case SettlementManagementActionPayload.SaveWorker save -> result(SettlementWorkerConfigurationService.configure(
                    player, town, save.resident(), save.area(), save.settings()), command);
            case SettlementManagementActionPayload.WorkerEnabled enabled -> result(SettlementWorkerConfigurationService.setEnabled(
                    player, town, enabled.resident(), enabled.area(), enabled.enabled()), command);
            case SettlementManagementActionPayload.Guard guard -> result(SettlementGuardService.command(player, town, guard.resident(), guard.order()), command);
            case SettlementManagementActionPayload.Assign assign -> result(SettlementWorkforceService.assignResident(level, player, id, assign.resident(), assign.area()), command);
            case SettlementManagementActionPayload.AutomaticStaffing automatic -> result(SettlementWorkforceService.automaticStaffing(player, town, automatic.resident()), command);
            case SettlementManagementActionPayload.Release release -> result(SettlementWorkforceService.release(level, player, id, release.resident()), command);
            case SettlementManagementActionPayload.Applicant applicant -> result(applicant.approve()
                    ? SettlementPopulationService.approve(player, id, applicant.id()) : SettlementPopulationService.decline(player, id, applicant.id()), command);
            case SettlementManagementActionPayload.SaveArea save -> result(SettlementBuildingService.register(player, id, save.area()), command);
            case SettlementManagementActionPayload.RemoveArea remove ->
                    result(data.removeArea(VillageAllegianceRegistrySavedData.get(level), id, player.getUUID(), remove.id()), command);
            case SettlementManagementActionPayload.ConfigureDepot depot ->
                    result(town.configureDepot(player.getUUID(), depot.id(), depot.purpose(), depot.priority()), command);
            case SettlementManagementActionPayload.StockTarget target ->
                    result(town.setDepotStockTarget(player.getUUID(), target.depot(), player.getMainHandItem(), target.count()), command);
            case SettlementManagementActionPayload.Treasury treasury -> result(treasury.deposit()
                    ? SettlementTradeService.depositCurrency(player, id, treasury.amount())
                    : SettlementTradeService.withdrawCurrency(player, id, treasury.amount()), command);
            case SettlementManagementActionPayload.Budget budget -> result(SettlementTradeService.configureBudget(player, id, budget.reserve(), budget.daily()), command);
            case SettlementManagementActionPayload.SaveOrder save -> result(SettlementTradeService.putOrder(player, id, save.order()), command);
            case SettlementManagementActionPayload.RemoveOrder remove -> result(SettlementTradeService.removeOrder(player, id, remove.id()), command);
            case SettlementManagementActionPayload.Permissions permissions ->
                    result(town.setPermissions(player.getUUID(), permissions.player(), permissions.grants()), command);
            case SettlementManagementActionPayload.Transfer transfer -> result(SettlementOwnershipService.offer(player, town, transfer.successor()), command);
            case SettlementManagementActionPayload.Inventory inventory -> result(level.getEntity(inventory.resident()) instanceof Villager villager
                    && SettlementWorkforceService.duty(villager).filter(duty -> duty.town().id().equals(id)).isPresent()
                    && SettlementWorkforceService.canManageEquipment(level, villager, player)
                    && com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.openJobInventory(player, villager), command);
        };
    }

    private static String result(boolean applied, SettlementManagementActionPayload.Command command) {
        return applied ? "" : SettlementManagementValidation.rejected(command);
    }

    static SettlementManagementPayload snapshot(ServerPlayer player, SettlementManagementRequestPayload request) {
        return snapshot(player, request, null);
    }

    static SettlementManagementPayload snapshot(ServerPlayer player, SettlementManagementRequestPayload request, SettlementDemandService.Snapshot plannedSupply) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var registry = VillageAllegianceRegistrySavedData.get(level);
        SettlementRecord town = request.town() == null ? null : data.get(new VillageAllegianceId(request.town())).orElse(null);
        if (request.page() != Page.TOWNS && (town == null || !town.dimension().equals(level.dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.VIEW))) {
            return null;
        }
        List<Entry> entries = new ArrayList<>();
        SettlementManagementPayload.Header header = null;
        if (request.page() == Page.TOWNS) {
            for (SettlementRecord candidate : data.settlements()) {
                if (candidate.dimension().equals(level.dimension().location()) && candidate.permits(player.getUUID(), SettlementPermission.VIEW)) {
                    entries.add(new SettlementManagementPayload.Town(candidate.id().value(), name(registry, candidate),
                            candidate.stage(), registry.residentRoster(candidate.id(), level.getGameTime()).size(), candidate.center()));
                }
            }
        } else {
            var assessment = plannedSupply == null ? overviewHeader(player, town) : overviewHeader(player, town, plannedSupply);
            header = assessment.header();
            var issues = assessment.issues();
            switch (request.page()) {
                case STORAGE -> {
                    var storage = SettlementStorageService.snapshot(player, town, request.query());
                    if (storage != null) entries.add(storage);
                }
                case DEMAND -> entries.addAll(assessment.demands());
                case SUPPLIES -> entries.addAll(SettlementSupplyService.supplies(player, town, assessment.supply().stock()));
                case SUPPLY_LINKS -> entries.addAll(SettlementSupplyService.links(player, town, request.query(), assessment.supply()));
                case ISSUES -> entries.addAll(issues);
                case RESIDENTS -> residents(level, registry, town, entries);
                case AREAS -> areas(town, entries);
                case STATIONS -> stations(town, request.query(), entries);
                case BEDS -> beds(level, town, entries);
                case HISTORY -> town.history().reversed().forEach(event -> entries.add(new SettlementManagementPayload.History(
                        event.id(), event.subject(), event.gameTime(), event.type())));
                case DEPOTS -> depots(town, entries);
                case PROJECTS -> projects(level, town, entries);
                case APPLICANTS -> {
                    for (var applicant : town.applicants().values()) {
                        ResourceLocation profession = ResourceLocation.tryParse(applicant.entity().getCompound("VillagerData").getString("profession"));
                        entries.add(new SettlementManagementPayload.Applicant(applicant.id(), display(applicant.name()),
                                profession == null ? ResourceLocation.withDefaultNamespace("none") : profession,
                                applicant.approvedBy().isPresent()));
                    }
                }
                case TRADE -> orders(town, entries);
                case DELEGATES -> town.delegates().forEach((id, delegated) -> entries.add(
                        new SettlementManagementPayload.Delegate(id, playerName(player, id), delegated)));
                case SUMMARY, TOWNS, RELATIONS -> {
                }
            }
        }
        String needle = request.page() == Page.STATIONS || request.page() == Page.STORAGE || request.page() == Page.SUPPLY_LINKS ? "" : request.query().strip().toLowerCase(Locale.ROOT);
        var matching = entries.stream().filter(entry -> searchable(entry).toLowerCase(Locale.ROOT).contains(needle));
        Comparator<Entry> order = Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id);
        if (request.page() == Page.SUPPLIES) order = Comparator.<Entry>comparingInt(entry -> {
            var supply = (SettlementManagementPayload.Supply) entry;
            return supply.shortage() > 0 ? 0 : supply.shortage() < 0 ? 1 : 2;
        }).thenComparing(entry -> ((SettlementManagementPayload.Supply) entry).storageName()).thenComparing(order);
        if (request.page() == Page.PROJECTS) order = Comparator.<Entry>comparingInt(entry -> switch (((SettlementManagementPayload.Project) entry).status()) {
            case ACTIVE -> 0;
            case PENDING -> 1;
            default -> 2;
        }); // Stable sort preserves the authoritative pending queue order.
        var filtered = (request.page() == Page.HISTORY ? matching : matching.sorted(order)).toList();
        int size = SettlementManagementRequestPayload.PAGE_SIZE;
        int index = Math.min(request.index(), Math.max(0, (filtered.size() - 1) / size));
        int start = index * size;
        return new SettlementManagementPayload(level.dimension().location(), header, request.page(), index, filtered.size(),
                request.query(), request.requestId(), request.openScreen(), filtered.subList(start, Math.min(filtered.size(), start + size)));
    }

    record Assessment(SettlementManagementPayload.Header header, List<SettlementManagementPayload.Issue> issues,
                      SettlementDemandService.Snapshot supply, List<SettlementManagementPayload.Demand> demands) { }

    static Assessment overviewHeader(ServerPlayer player, SettlementRecord town) {
        return overviewHeader(player, town, SettlementDemandService.inspect(player.serverLevel(), town, player));
    }

    static Assessment overviewHeader(ServerPlayer player, SettlementRecord town, SettlementDemandService.Snapshot supply) {
        var level = player.serverLevel();
        var registry = VillageAllegianceRegistrySavedData.get(level);
        var status = SettlementProgressionService.assess(level, town);
        var demands = SettlementDemandViewService.demands(level, town, supply);
        var issues = SettlementIssueService.issues(level, town, status, demands);
        Set<SettlementPermission> permissions = EnumSet.noneOf(SettlementPermission.class);
        for (SettlementPermission permission : SettlementPermission.values()) {
            if (town.permits(player.getUUID(), permission)) {
                permissions.add(permission);
            }
        }
        var header = new SettlementManagementPayload.Header(town.id().value(), name(registry, town), town.center(), town.leader(),
                permissions, town.stage(), town.radius(), town.stage().projects(), town.stage().depots(),
                SettlementTradeService.dailySupply(town.stage()), SettlementTradeService.dailyDemand(town.stage()),
                status.population(), status.housing(), status.foodDays(), town.support().shortageTicks(),
                status.sustainedTicks(), status.requiredTicks(), status.ready(), status.missing(), town.economy().balance(),
                town.economy().reserve(), town.economy().dailyBudget(), town.economy().spent(), (int) issues.stream().filter(SettlementManagementPayload.Issue::needsAttention).count(), town.manualManagement(), SettlementPlotService.totals(town),
                SettlementPopulationService.growthTarget(level, town), SettlementPopulationRequirements.effective(),
                SettlementPopulationRequirements.adjusted(), SettlementPopulationRequirements.problem());
        return new Assessment(header, issues, supply, demands);
    }

    static java.util.Map<Page, List<Entry>> overview(ServerPlayer player, SettlementRecord town) {
        var result = new java.util.EnumMap<Page, List<Entry>>(Page.class);
        var residents = new ArrayList<Entry>();
        residents(player.serverLevel(), VillageAllegianceRegistrySavedData.get(player.serverLevel()), town, residents, false);
        var areas = new ArrayList<Entry>();
        areas(town, areas);
        var projects = new ArrayList<Entry>();
        projects(player.serverLevel(), town, projects);
        result.put(Page.RESIDENTS, residents);
        result.put(Page.AREAS, areas);
        result.put(Page.PROJECTS, projects);
        var depots = new ArrayList<Entry>();
        depots(town, depots);
        result.put(Page.DEPOTS, depots);
        var orders = new ArrayList<Entry>();
        orders(town, orders);
        result.put(Page.TRADE, orders);
        var beds = new ArrayList<Entry>();
        beds(player.serverLevel(), town, beds, false);
        result.put(Page.BEDS, beds);
        return result;
    }

    private static void orders(SettlementRecord town, List<Entry> entries) {
        for (var order : town.economy().orders().values()) {
            var result = town.economy().lastResults().get(order.id());
            entries.add(new SettlementManagementPayload.Order(order.id(), display(order.sample().getHoverName().getString()),
                    order.direction(), order.item(), order.stockTarget(), order.unitPriceLimit(), order.dailyBudget(),
                    order.enabled(), result == null ? "NONE" : result.status().name()));
        }
    }

    private static List<HiredVillagerRole> settlementRoles(ServerLevel level, Villager villager, SettlementResidentState care) {
        var roles = new ArrayList<HiredVillagerRole>(villager == null ? care == null ? List.of() : care.availableRoles(level)
                : SettlementWorkforceService.availableRoles(level, villager));
        if ((villager != null && !villager.isBaby() || care != null && care.availableForWork()) && !roles.contains(HiredVillagerRole.BUILDER)) {
            roles.add(HiredVillagerRole.BUILDER);
        }
        return List.copyOf(roles);
    }

    private static void projects(ServerLevel level, SettlementRecord town, List<Entry> entries) {
        java.util.stream.Stream.concat(town.projects().values().stream(), town.projectHistory().values().stream()).forEach(project -> entries.add(new SettlementManagementPayload.Project(
                        project.id(), display(project.label()), project.area(), project.builder(), project.origin(), project.min(), project.max(), project.status(),
                        project.placed(), project.total(), project.blocker().substring(0, Math.min(128, project.blocker().length())),
                        project.materials().substring(0, Math.min(512, project.materials().length())), project.phase(),
                        project.missingMaterials().substring(0, Math.min(512, project.missingMaterials().length())),
                        project.priority(), project.pending() ? town.constructionQueueRevision() : project.crewRevision(), project.workplace() != null || town.worksitePlans().containsKey(project.area()),
                        constructionName(level, project.builder()), project.assistants().stream()
                                .map(id -> new SettlementManagementPayload.CrewMember(id, constructionName(level, id))).toList())));
    }

    private static String constructionName(ServerLevel level, UUID id) {
        if (id.equals(SettlementProject.AUTOMATIC_BUILDER)) return "Next available builder";
        return display(level.getEntity(id) instanceof Villager member ? VillagerPresetNameRegistry.resolveDisplayName(member).getString()
                : VillagerSocialGraphSavedData.get(level).knownName(id).orElse("Unknown builder"));
    }

    private static void residents(ServerLevel level, VillageAllegianceRegistrySavedData registry,
                                  SettlementRecord town, List<Entry> entries) {
        residents(level, registry, town, entries, true);
    }

    private static void residents(ServerLevel level, VillageAllegianceRegistrySavedData registry,
                                  SettlementRecord town, List<Entry> entries, boolean detailed) {
        var social = VillagerSocialGraphSavedData.get(level);
        for (var member : registry.residentRoster(town.id(), level.getGameTime())) {
            var entity = level.getEntity(member.id());
            Villager villager = entity instanceof Villager loaded && loaded.isAlive() ? loaded : null;
            var care = town.residents().get(member.id());
            var worker = town.workers().get(member.id());
            ResourceLocation profession = villager == null
                    ? social.knownProfession(member.id()).flatMap(VillagerProfessionUtil::parse)
                            .map(VillagerProfessionUtil::id).orElse(ResourceLocation.withDefaultNamespace("none"))
                    : VillagerProfessionUtil.id(villager.getVillagerData().getProfession());
            BlockPos position = villager != null ? villager.blockPosition() : care == null ? town.center() : care.position();
            int radius = town.radius() + 16;
            Presence presence = villager == null ? Presence.UNLOADED
                    : Math.abs((long) position.getX() - town.center().getX()) <= radius
                        && Math.abs((long) position.getZ() - town.center().getZ()) <= radius ? Presence.HERE : Presence.AWAY;
            boolean personal = villager != null && SettlementWorkforceService.hasPersonalCommitment(villager);
            var work = personal ? new SettlementWorkStatus("personal_commitment", "Personal workforce commitment")
                    : residentWork(level, town, member.id(), villager, worker);
            String temporary = personal || !town.duties().containsKey(member.id()) ? ""
                    : com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService.summary(villager != null
                    ? com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager)
                    : worker == null ? new net.minecraft.nbt.CompoundTag() : worker.settings());
            var guardCommand = town.temporaryGuardOrders().get(member.id());
            if (!personal && guardCommand != null) temporary = "Temporary " + guardCommand.mode().name().toLowerCase(Locale.ROOT)
                    + " / Return to assignment resumes the saved route";
            String diagnostic = temporary.isEmpty() ? work.diagnostic() : temporary + (work.diagnostic().isEmpty() ? "" : " | " + work.diagnostic());
            diagnostic = diagnostic.substring(0, Math.min(180, diagnostic.length()));
            var settings = personal ? null : SettlementWorkerConfigurationService.snapshot(level, town, member.id());
            entries.add(new SettlementManagementPayload.Resident(member.id(),
                    display(villager == null ? social.knownName(member.id()).orElse("") : VillagerPresetNameRegistry.resolveDisplayName(villager).getString()),
                    profession, villager == null ? !member.adult() : villager.isBaby(), presence, position, town.duties().get(member.id()),
                    !detailed ? -1 : villager != null ? VillagerRecoveryService.captureRecoveryState(villager).food() : care == null ? -1 : care.food(),
                    !detailed ? -1.0F : villager != null ? villager.getHealth() : care == null ? -1.0F : care.health(),
                    work.status(),
                    settlementRoles(level, villager, care),
                    town.guardOrders().get(member.id()), detailed ? settings : null,
                    detailed && !personal ? SettlementBrewingService.snapshot(level, town, member.id()) : null, diagnostic, town.manuallyStaffed(member.id()),
                    !personal && town.worksitePlans().containsKey(town.duties().get(member.id())),
                    settings == null ? 0 : Integer.bitCount(settings.overrides()), temporary, town.workerPoolFor(member.id())));
        }
    }

    static SettlementWorkStatus residentWork(ServerLevel level, SettlementRecord town, UUID id,
                                             Villager villager, SettlementWorkerState worker) {
        if (town.arrivals().containsKey(id)) return new SettlementWorkStatus("awaiting_arrival", "");
        var area = town.workplace(town.duties().get(id));
        if (area == null) return new SettlementWorkStatus("unassigned", "");
        var settings = town.effectiveWorkerSettings(id);
        if (settings != null && !settings.enabled()) return new SettlementWorkStatus("paused", "");
        if (!area.enabled()) return new SettlementWorkStatus("area_paused", "");
        if (worker != null && (!worker.area().equals(area.id()) || worker.role() != area.type().role())) worker = null;
        if (villager == null || !level.isPositionEntityTicking(villager.blockPosition()) || worker != null && worker.unsafe()) {
            String status = worker == null ? "awaiting_worker_observation" : worker.status();
            String diagnostic = worker == null ? "" : worker.diagnostic();
            return new SettlementWorkStatus(status, diagnostic.isBlank() ? SettlementWorkerDiagnostics.describe(status) : diagnostic);
        }
        return SettlementWorkStatus.observe(level, villager);
    }

    private static void beds(ServerLevel level, SettlementRecord town, List<Entry> entries) {
        beds(level, town, entries, true);
    }

    private static void beds(ServerLevel level, SettlementRecord town, List<Entry> entries, boolean refresh) {
        if (refresh) town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.HOME)
                .forEach(area -> SettlementBuildingService.refresh(level, town, area));
        SettlementHousingService.update(level, town);
        var seen = new java.util.HashSet<BlockPos>();
        for (var area : town.areas().values()) {
            var survey = town.surveys().get(area.id());
            if (!area.enabled() || area.type() != SettlementAreaType.HOME || survey == null) continue;
            for (var bed : survey.beds()) {
                if (!seen.add(bed)) continue;
                var resident = SettlementHousingService.owner(town, bed);
                String name = resident == null ? "" : VillagerSocialGraphSavedData.get(level).knownName(resident).orElse("");
                var id = UUID.nameUUIDFromBytes((town.id().value() + ":" + bed.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                entries.add(new SettlementManagementPayload.Bed(id, display(area.name()), bed, resident, display(name),
                        resident == null && Boolean.TRUE.equals(SettlementHousingService.availability(town, bed))
                                && SettlementHousingService.canUse(level, bed, null)));
            }
        }
    }

    private static void stations(SettlementRecord town, String areaId, List<Entry> entries) {
        var area = town.areas().values().stream().filter(candidate -> candidate.id().toString().equals(areaId)).findFirst().orElse(null);
        var survey = area == null ? null : town.surveys().get(area.id());
        if (survey == null) return;
        survey.fixtures().forEach((position, block) -> {
            if (area.contains(position)) entries.add(new SettlementManagementPayload.Station(
                    UUID.nameUUIDFromBytes((areaId + ":" + position.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    block.toString(), position));
        });
    }

    private static void areas(SettlementRecord town, List<Entry> entries) {
        for (var area : town.areas().values()) {
            var survey = town.surveys().get(area.id());
            entries.add(new SettlementManagementPayload.Area(area.id(), display(area.name()), area.type(), area.min(), area.max(),
                    area.priority(), area.enabled(), survey == null ? 0 : survey.beds().size(),
                    survey == null ? 0 : survey.workstations().size(),
                    (int) town.duties().values().stream().filter(area.id()::equals).count(), town.worksitePlans().get(area.id()), town.equipmentPlans().get(area.id()), area.crops()));
        }
    }

    private static void depots(SettlementRecord town, List<Entry> entries) {
        for (var depot : town.depots().values()) {
            var area = town.areas().get(depot.area());
            int slots = 0;
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                if (!depot.getItem(slot).isEmpty()) {
                    slots++;
                }
            }
            entries.add(new SettlementManagementPayload.Depot(depot.id(), display(area == null ? "" : area.name()), depot.area(),
                    depot.position(), depot.purpose(), depot.priority(), slots, depot.count(stack -> !stack.isEmpty()),
                    depot.stockTargets().stream().map(target ->
                            new SettlementManagementPayload.StockTarget(display(target.sample().getHoverName().getString()), target.count())).toList()));
        }
    }

    private static String searchable(Entry entry) {
        return entry.name() + " " + entry.id() + switch (entry) {
            case SettlementManagementPayload.Issue issue -> " " + issue.kind().name() + " " + issue.cause() + " "
                    + issue.detail() + " " + issue.targetId() + " " + issue.resident() + " " + issue.area();
            case SettlementManagementPayload.Demand demand -> " " + demand.consumer() + " " + demand.requirement() + " " + demand.cause();
            case SettlementManagementPayload.Supply supply -> " " + supply.storageName() + " " + supply.position().toShortString();
            case SettlementManagementPayload.Resident resident -> " " + resident.profession().getPath();
            case SettlementManagementPayload.Bed bed -> " " + bed.position().toShortString() + " " + bed.residentName() + " " + bed.resident();
            case SettlementManagementPayload.Area area -> " " + area.kind().name();
            case SettlementManagementPayload.History history -> " " + history.kind().name();
            case SettlementManagementPayload.Depot depot -> " " + depot.purpose().name();
            case SettlementManagementPayload.Applicant applicant -> " " + applicant.profession().getPath();
            case SettlementManagementPayload.Order order -> " " + order.item().getPath() + " " + order.direction().name();
            default -> "";
        };
    }

    private static String name(VillageAllegianceRegistrySavedData registry, SettlementRecord town) {
        return display(registry.record(town.id()).map(VillageAllegianceRegistrySavedData.AllegianceRecord::displayName).orElse(""));
    }

    private static String playerName(ServerPlayer viewer, UUID id) {
        var player = viewer.getServer().getPlayerList().getPlayer(id);
        if (player != null) {
            return display(player.getGameProfile().getName());
        }
        var cache = viewer.getServer().getProfileCache();
        return display(cache == null ? id.toString() : cache.get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString()));
    }

    private static String display(String value) {
        if (value.length() <= 128) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(127)) ? 127 : 128;
        return value.substring(0, end);
    }
}
