package com.jvn.villagerretaliation.settlement;

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
        var snapshot = snapshot(player, request);
        if (snapshot == null) {
            snapshot = snapshot(player, new SettlementManagementRequestPayload(null, Page.TOWNS, 0, "", request.requestId(), request.openScreen()));
        }
        PacketDistributor.sendToPlayer(player, snapshot);
    }

    public static void action(ServerPlayer player, SettlementManagementActionPayload payload) {
        boolean applied = ServerboundRequestLimiter.tryAcquire(player, SettlementManagementActionPayload.TYPE.id(), 5L)
                && execute(player, payload);
        PacketDistributor.sendToPlayer(player, new SettlementManagementActionResultPayload(payload.town(), applied));
    }

    static boolean execute(ServerPlayer player, SettlementManagementActionPayload payload) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        var data = SettlementSavedData.get(level);
        var id = new VillageAllegianceId(payload.town());
        var town = data.get(id).orElse(null);
        if (town == null || !town.dimension().equals(level.dimension().location())
                || !town.permits(player.getUUID(), SettlementPermission.VIEW)) {
            return false;
        }
        return switch (payload.command()) {
            case SettlementManagementActionPayload.Advance ignored -> SettlementProgressionService.advance(player, id);
            case SettlementManagementActionPayload.View ignored -> SettlementViewService.open(player, id);
            case SettlementManagementActionPayload.DraftBlueprint draft -> SettlementBlueprintService.draft(player, town, draft.structure());
            case SettlementManagementActionPayload.PlaceBlueprint place -> SettlementBlueprintService.place(player, town, place.blueprint(), place.anchor(), place.rotation());
            case SettlementManagementActionPayload.StartProject start -> SettlementConstructionService.start(player, town, start.resident(), start.blueprint());
            case SettlementManagementActionPayload.CancelProject cancel -> SettlementConstructionService.cancel(player, town, cancel.project());
            case SettlementManagementActionPayload.Brew brew -> SettlementBrewingService.configure(player, town, brew.resident(), brew.area(), brew.order());
            case SettlementManagementActionPayload.SaveWorker save -> SettlementWorkerConfigurationService.configure(
                    player, town, save.resident(), save.area(), save.settings());
            case SettlementManagementActionPayload.Guard guard -> SettlementGuardService.command(player, town, guard.resident(), guard.order());
            case SettlementManagementActionPayload.Assign assign -> level.getEntity(assign.resident()) instanceof Villager villager
                    && SettlementWorkforceService.assign(level, player, id, villager, assign.area());
            case SettlementManagementActionPayload.Release release -> SettlementWorkforceService.release(level, player, id, release.resident());
            case SettlementManagementActionPayload.Applicant applicant -> applicant.approve()
                    ? SettlementPopulationService.approve(player, id, applicant.id()) : SettlementPopulationService.decline(player, id, applicant.id());
            case SettlementManagementActionPayload.SaveArea save -> SettlementBuildingService.register(player, id, save.area());
            case SettlementManagementActionPayload.RemoveArea remove ->
                    data.removeArea(VillageAllegianceRegistrySavedData.get(level), id, player.getUUID(), remove.id());
            case SettlementManagementActionPayload.ConfigureDepot depot ->
                    town.configureDepot(player.getUUID(), depot.id(), depot.purpose(), depot.priority());
            case SettlementManagementActionPayload.StockTarget target ->
                    town.setDepotStockTarget(player.getUUID(), target.depot(), player.getMainHandItem(), target.count());
            case SettlementManagementActionPayload.Treasury treasury -> treasury.deposit()
                    ? SettlementTradeService.depositCurrency(player, id, treasury.amount())
                    : SettlementTradeService.withdrawCurrency(player, id, treasury.amount());
            case SettlementManagementActionPayload.Budget budget -> SettlementTradeService.configureBudget(player, id, budget.reserve(), budget.daily());
            case SettlementManagementActionPayload.SaveOrder save -> SettlementTradeService.putOrder(player, id, save.order());
            case SettlementManagementActionPayload.RemoveOrder remove -> SettlementTradeService.removeOrder(player, id, remove.id());
            case SettlementManagementActionPayload.Permissions permissions ->
                    town.setPermissions(player.getUUID(), permissions.player(), permissions.grants());
            case SettlementManagementActionPayload.Transfer transfer ->
                    town.transferLeadership(player.getUUID(), transfer.successor());
            case SettlementManagementActionPayload.Inventory inventory -> level.getEntity(inventory.resident()) instanceof Villager villager
                    && SettlementWorkforceService.duty(villager).filter(duty -> duty.town().id().equals(id)).isPresent()
                    && SettlementWorkforceService.canManageEquipment(level, villager, player)
                    && com.jvn.villagerretaliation.inventory.VillagerInventoryAccess.openJobInventory(player, villager);
        };
    }

    static SettlementManagementPayload snapshot(ServerPlayer player, SettlementManagementRequestPayload request) {
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
            var status = SettlementProgressionService.assess(level, town);
            Set<SettlementPermission> permissions = EnumSet.noneOf(SettlementPermission.class);
            for (SettlementPermission permission : SettlementPermission.values()) {
                if (town.permits(player.getUUID(), permission)) {
                    permissions.add(permission);
                }
            }
            header = new SettlementManagementPayload.Header(town.id().value(), name(registry, town), town.center(), town.leader(),
                    permissions, town.stage(), status.population(), status.housing(), status.foodDays(), town.support().shortageTicks(),
                    status.sustainedTicks(), status.requiredTicks(), status.ready(), status.missing(), town.economy().balance(),
                    town.economy().reserve(), town.economy().dailyBudget(), town.economy().spent());
            switch (request.page()) {
                case RESIDENTS -> residents(level, registry, town, entries);
                case AREAS -> areas(town, entries);
                case DEPOTS -> depots(town, entries);
                case PROJECTS -> town.projects().values().forEach(project -> entries.add(new SettlementManagementPayload.Project(
                        project.id(), display(project.label()), project.area(), project.builder(), project.origin(), project.status(),
                        project.placed(), project.total(), project.blocker().substring(0, Math.min(128, project.blocker().length())),
                        project.materials().substring(0, Math.min(512, project.materials().length())), project.phase(),
                        project.missingMaterials().substring(0, Math.min(512, project.missingMaterials().length())))));
                case APPLICANTS -> {
                    for (var applicant : town.applicants().values()) {
                        ResourceLocation profession = ResourceLocation.tryParse(applicant.entity().getCompound("VillagerData").getString("profession"));
                        entries.add(new SettlementManagementPayload.Applicant(applicant.id(), display(applicant.name()),
                                profession == null ? ResourceLocation.withDefaultNamespace("none") : profession,
                                applicant.approvedBy().isPresent()));
                    }
                }
                case TRADE -> {
                    for (var order : town.economy().orders().values()) {
                        var result = town.economy().lastResults().get(order.id());
                        entries.add(new SettlementManagementPayload.Order(order.id(), display(order.sample().getHoverName().getString()),
                                order.direction(), order.item(), order.stockTarget(), order.unitPriceLimit(), order.dailyBudget(),
                                order.enabled(), result == null ? "NONE" : result.status().name()));
                    }
                }
                case DELEGATES -> town.delegates().forEach((id, delegated) -> entries.add(
                        new SettlementManagementPayload.Delegate(id, playerName(player, id), delegated)));
                case SUMMARY, TOWNS -> {
                }
            }
        }
        String needle = request.query().strip().toLowerCase(Locale.ROOT);
        var filtered = entries.stream().filter(entry -> searchable(entry).toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::id)).toList();
        int size = SettlementManagementRequestPayload.PAGE_SIZE;
        int index = Math.min(request.index(), Math.max(0, (filtered.size() - 1) / size));
        int start = index * size;
        return new SettlementManagementPayload(level.dimension().location(), header, request.page(), index, filtered.size(),
                request.query(), request.requestId(), request.openScreen(), filtered.subList(start, Math.min(filtered.size(), start + size)));
    }

    private static void residents(ServerLevel level, VillageAllegianceRegistrySavedData registry,
                                  SettlementRecord town, List<Entry> entries) {
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
            int radius = town.stage().radius() + 16;
            Presence presence = villager == null ? Presence.UNLOADED
                    : Math.abs((long) position.getX() - town.center().getX()) <= radius
                        && Math.abs((long) position.getZ() - town.center().getZ()) <= radius ? Presence.HERE : Presence.AWAY;
            entries.add(new SettlementManagementPayload.Resident(member.id(),
                    display(villager == null ? social.knownName(member.id()).orElse("") : VillagerPresetNameRegistry.resolveDisplayName(villager).getString()),
                    profession, villager == null ? !member.adult() : villager.isBaby(), presence, position, town.duties().get(member.id()),
                    villager != null ? VillagerRecoveryService.captureRecoveryState(villager).food() : care == null ? -1 : care.food(),
                    villager != null ? villager.getHealth() : care == null ? -1.0F : care.health(),
                    town.arrivals().containsKey(member.id()) ? "awaiting_arrival"
                            : !town.duties().containsKey(member.id()) ? "unassigned"
                            : town.workerSettings().containsKey(member.id()) && !town.workerSettings().get(member.id()).enabled() ? "paused"
                            : worker == null ? "active" : worker.status(),
                    villager == null || villager.isBaby() ? List.of() : HiredVillagerRoles.availableRoles(level, villager),
                    town.guardOrders().get(member.id()), SettlementWorkerConfigurationService.snapshot(level, town, member.id()),
                    SettlementBrewingService.snapshot(level, town, member.id())));
        }
    }

    private static void areas(SettlementRecord town, List<Entry> entries) {
        for (var area : town.areas().values()) {
            var survey = town.surveys().get(area.id());
            entries.add(new SettlementManagementPayload.Area(area.id(), display(area.name()), area.type(), area.min(), area.max(),
                    area.priority(), area.enabled(), survey == null ? 0 : survey.beds().size(),
                    survey == null ? 0 : survey.workstations().size(),
                    (int) town.duties().values().stream().filter(area.id()::equals).count()));
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
            case SettlementManagementPayload.Resident resident -> " " + resident.profession().getPath();
            case SettlementManagementPayload.Area area -> " " + area.kind().name();
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
