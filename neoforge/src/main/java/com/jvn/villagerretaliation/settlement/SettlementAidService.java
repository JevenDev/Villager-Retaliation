package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyApi;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyCheckpoint;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.VillageRelation;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import com.jvn.villagerretaliation.diplomacy.route.RoutePreparation;
import com.jvn.villagerretaliation.diplomacy.route.RouteSavedData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementAidService {
    private static final Map<MinecraftServer, Schedule> SCHEDULES = new WeakHashMap<>();
    private SettlementAidService() { }
    public enum Result { APPLIED, FORBIDDEN, STALE, SUSPENDED, UNKNOWN_VILLAGE, UNAVAILABLE, COOLDOWN, OUTSTANDING, NO_SHORTAGE, LIMIT_REACHED }

    public static Result policy(ServerPlayer player, VillageAllegianceId village, long expected, boolean automatic,
                                int reserveDays, int maximumItems, int minimumStanding, int intervalTicks) {
        var data = SettlementSavedData.get(player.serverLevel()); var town = data.get(village).orElse(null);
        if (!VillageDiplomacyService.represents(player, town)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (data.aid().policyRevision(town) != expected) return Result.STALE;
        if (reserveDays < 1 || reserveDays > 30 || maximumItems < 1 || maximumItems > 1728 || minimumStanding < 0
                || minimumStanding > 100 || intervalTicks < 24000 || intervalTicks > 2400000) return Result.UNAVAILABLE;
        var state = data.aid().town(town);
        state.policy = new SettlementAidPolicy(automatic, player.getUUID(), reserveDays, maximumItems, minimumStanding, intervalTicks);
        data.aid().changed(); state.policyRevision = data.aid().revision(); data.setDirty();
        return Result.APPLIED;
    }

    public static Result request(ServerPlayer player, VillageAllegianceId requester, VillageAllegianceId donor, long nutrition, long expected) {
        var data = SettlementSavedData.get(player.serverLevel()); var town = data.get(requester).orElse(null);
        if (!VillageDiplomacyService.represents(player, town)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (data.aid().policyRevision(town) != expected) return Result.STALE;
        return create(player.serverLevel(), town, data.get(donor).orElse(null), nutrition, player.getUUID(), now(player.server));
    }

    public static Result approve(ServerPlayer player, UUID id, long expected, int maximumItems) {
        var data = SettlementSavedData.get(player.serverLevel()); var request = data.aid().request(id);
        var town = request == null ? null : data.get(request.donor()).orElse(null);
        if (!VillageDiplomacyService.represents(player, town)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (request.revision() != expected) return Result.STALE;
        if (request.state() != SettlementFoodRequest.State.AWAITING_OFFER || maximumItems < 1 || maximumItems > 1728) return Result.UNAVAILABLE;
        request.donorAuthority = player.getUUID(); request.maximumItems = Math.min(maximumItems, data.aid().policy(town).maximumItems());
        transition(data, request, SettlementFoodRequest.State.OFFER_PREPARING, ""); return Result.APPLIED;
    }

    public static Result accept(ServerPlayer player, UUID id, long expected) {
        var data = SettlementSavedData.get(player.serverLevel()); var request = data.aid().request(id);
        var town = request == null ? null : data.get(request.requester()).orElse(null);
        if (!VillageDiplomacyService.represents(player, town)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (request.revision() != expected) return Result.STALE;
        if (request.state() != SettlementFoodRequest.State.AWAITING_ACCEPTANCE) return Result.UNAVAILABLE;
        return accept(player.serverLevel(), request) ? Result.APPLIED : Result.UNAVAILABLE;
    }

    public static Result decline(ServerPlayer player, UUID id, long expected) {
        var data = SettlementSavedData.get(player.serverLevel()); var request = data.aid().request(id);
        if (request == null) return Result.FORBIDDEN;
        boolean donorTurn = request.state() == SettlementFoodRequest.State.AWAITING_OFFER || request.state() == SettlementFoodRequest.State.OFFER_PREPARING;
        boolean requesterTurn = request.state() == SettlementFoodRequest.State.AWAITING_ACCEPTANCE;
        var town = data.get(donorTurn ? request.donor() : request.requester()).orElse(null);
        if ((!donorTurn && !requesterTurn) || !VillageDiplomacyService.represents(player, town)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (request.revision() != expected) return Result.STALE;
        request.messageSender = data.get(request.donor()).orElse(null) == town ? request.donor() : request.requester();
        request.arrives = now(player.server) + delay(player.serverLevel(), request); releaseRoutes(player.serverLevel(), request);
        transition(data, request, SettlementFoodRequest.State.DECLINE_SENT, ""); return Result.APPLIED;
    }

    public static Result cancel(ServerPlayer player, UUID id, long expected) {
        var data = SettlementSavedData.get(player.serverLevel()); var request = data.aid().request(id);
        if (request == null) return Result.FORBIDDEN;
        var donor = data.get(request.donor()).orElse(null); var recipient = data.get(request.requester()).orElse(null);
        var sender = VillageDiplomacyService.represents(player, recipient) ? recipient : donor;
        if (!VillageDiplomacyService.represents(player, sender)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (request.revision() != expected) return Result.STALE;
        if (!beforeDeparture(request) || request.state() == SettlementFoodRequest.State.CANCELLING) return Result.UNAVAILABLE;
        request.messageSender = sender == donor ? request.donor() : request.requester(); request.arrives = now(player.server) + delay(player.serverLevel(), request);
        releaseRoutes(player.serverLevel(), request); transition(data, request, SettlementFoodRequest.State.CANCELLING, "");
        return Result.APPLIED;
    }

    public static Result retry(ServerPlayer player, UUID id, long expected) {
        var data = SettlementSavedData.get(player.serverLevel()); var request = data.aid().request(id);
        var town = request == null ? null : data.get(request.donor()).orElse(null);
        if (!VillageDiplomacyService.represents(player, town)) return Result.FORBIDDEN;
        if (!enabled(player.serverLevel())) return Result.SUSPENDED;
        if (request.revision() != expected) return Result.STALE;
        if (request.state() != SettlementFoodRequest.State.OFFER_PREPARING || request.preparation == null) return Result.UNAVAILABLE;
        var level = dimension(player.server, town); if (level == null) return Result.UNAVAILABLE;
        boolean retried = DiplomaticRouteService.retry(level, request.preparation.crossing);
        for (var candidate : candidates(request.preparation)) if (candidate.approach != null) retried |= DiplomaticRouteService.retry(level, candidate.approach);
        if (!retried) return Result.UNAVAILABLE;
        transition(data, request, request.state(), ""); return Result.APPLIED;
    }

    static Result create(ServerLevel level, SettlementRecord requester, SettlementRecord donor, long nutrition, UUID authority, long now) {
        var data = SettlementSavedData.get(level); var aid = data.aid();
        if (donor == null || requester.id().equals(donor.id()) || VillageDiplomacySavedData.get(level).contact(requester.id(), donor.id()) == null)
            return Result.UNKNOWN_VILLAGE;
        if (!available(level, requester, donor) || nutrition <= 0 || nutrition > 10000000) return Result.UNAVAILABLE;
        if (aid.requests.values().stream().anyMatch(request -> !request.terminal() && data.get(request.requester()).orElse(null) == requester
                && data.get(request.donor()).orElse(null) == donor)) return Result.OUTSTANDING;
        var state = aid.town(requester);
        if (now < state.nextRequest) return Result.COOLDOWN;
        long need = need(level, requester); if (need <= 0) return Result.NO_SHORTAGE;
        if (!aid.canRequest()) return Result.LIMIT_REACHED;
        var rules = VillageDiplomacyRules.get(level.getServer());
        var request = new SettlementFoodRequest(UUID.randomUUID(), requester.id(), donor.id(), Math.min(need, nutrition), now,
                now + delay(level, requester, donor), now + rules.requestLifetime(), authority);
        aid.requests.put(request.id(), request); aid.changed(request);
        state.nextRequest = now + rules.aidCooldown(); data.setDirty(); return Result.APPLIED;
    }

    static long need(ServerLevel level, SettlementRecord town) {
        var aid = SettlementSavedData.get(level).aid();
        long incoming = aid.requests.values().stream().filter(request -> SettlementSavedData.get(level).get(request.requester()).orElse(null) == town)
                .mapToLong(SettlementFoodRequest::incomingNutrition).sum();
        return Math.max(0, SettlementAidStock.reserve(level, town, aid.policy(town)) - SettlementAidStock.nutrition(town) - incoming);
    }
    private static boolean approvalAvailable(ServerLevel level, SettlementRecord donor, SettlementRecord recipient, SettlementFoodRequest request) {
        if (!donor.permits(request.donorAuthority, SettlementPermission.DIPLOMACY) || !available(level, donor, recipient)) return false;
        if (!request.automaticApproval) return true;
        var aid = SettlementSavedData.get(level).aid(); var policy = aid.policy(donor);
        return policy.automatic() && policy.authority().equals(request.donorAuthority)
                && VillageDiplomacySavedData.get(level).standing(donor.id(), recipient.id()) >= policy.minimumStanding()
                && now(level.getServer()) >= aid.town(donor).nextAid;
    }
    static boolean enabled(ServerLevel level) {
        var data = SettlementSavedData.get(level); var rules = VillageDiplomacyRules.get(level.getServer());
        return level.getServer().isSameThread() && !data.readOnly() && data.aid().writable() && rules.enabled()
                && VillageDiplomacyRules.problem().isEmpty() && !VillageDiplomacySavedData.get(level).readOnly();
    }
    static boolean available(ServerLevel level, SettlementRecord first, SettlementRecord second) {
        var registry = VillageAllegianceRegistrySavedData.get(level);
        return first != null && second != null && !first.id().equals(second.id()) && first.territory().active() && second.territory().active()
                && first.dimension().equals(second.dimension()) && registry.canonicalRecord(first.id()).filter(record -> !record.archived()).isPresent()
                && registry.canonicalRecord(second.id()).filter(record -> !record.archived()).isPresent()
                && first.center().distSqr(second.center()) <= (long) VillageDiplomacyRules.get(level.getServer()).shipmentRange() * VillageDiplomacyRules.get(level.getServer()).shipmentRange()
                && VillageDiplomacySavedData.get(level).standing(first.id(), second.id()) >= 0
                && VillageDiplomacyApi.query(level, first.id(), second.id()).relation() != VillageRelation.HOSTILE;
    }
    static ServerLevel dimension(MinecraftServer server, SettlementRecord town) {
        return town == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, town.dimension()));
    }
    private static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static long delay(ServerLevel level, SettlementRecord first, SettlementRecord second) {
        var rules = VillageDiplomacyRules.get(level.getServer());
        return Math.max(rules.letterMinimum(), (long) Math.ceil(Math.sqrt(first.center().distSqr(second.center()))) * rules.letterTicksPerBlock());
    }
    private static long delay(ServerLevel level, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level); var first = data.get(request.requester()).orElse(null); var second = data.get(request.donor()).orElse(null);
        return first == null || second == null ? VillageDiplomacyRules.get(level.getServer()).letterMinimum() : delay(level, first, second);
    }
    static void transition(SettlementSavedData data, SettlementFoodRequest request, SettlementFoodRequest.State state, String blocker) {
        if (request.state == state && request.blocker.equals(blocker)) return;
        request.state = state; request.blocker = blocker; data.aid().changed(request); data.setDirty();
    }
    private static boolean accept(ServerLevel level, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level);
        if (request.offer == null || request.preparation == null || !available(level, data.get(request.requester()).orElse(null), data.get(request.donor()).orElse(null))) return false;
        request.messageSender = request.requester();
        request.arrives = now(level.getServer()) + delay(level, request);
        transition(data, request, SettlementFoodRequest.State.ACCEPTANCE_SENT, ""); return true;
    }

    static SettlementResidentState courier(ServerLevel level, SettlementRecord town, BlockPos depot, UUID request) {
        return town.residents().values().stream().filter(resident -> eligible(level, town, resident, request))
                .min(Comparator.comparingDouble(resident -> resident.position().distSqr(depot))).orElse(null);
    }
    static boolean eligible(ServerLevel level, SettlementRecord town, SettlementResidentState resident, UUID request) {
        if (resident == null || !VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id())
                .map(record -> record.residents().containsKey(resident.id())).orElse(false) || !resident.eligibleDiplomaticCourier() || town.leader().equals(resident.id())
                || town.autonomous() != null && resident.id().equals(town.autonomous().ruler()) || town.duties().containsKey(resident.id())
                || town.workerPoolFor(resident.id()) != null || town.workers().containsKey(resident.id()) || town.activeProject(resident.id()) != null
                || town.arrivals().containsKey(resident.id()) || town.departures().containsKey(resident.id())) return false;
        for (var other : SettlementSavedData.get(level).aid().requests.values()) {
            if (other.id().equals(request) || other.terminal()) continue;
            if (other.offer != null) { if (other.offer.courier().equals(resident.id())) return false; continue; }
            if (other.preparation != null && (other.preparation.delivery != null && other.preparation.delivery.resident.equals(resident.id())
                    || other.preparation.collection != null && other.preparation.collection.resident.equals(resident.id()))) return false;
        }
        var live = level.getEntity(resident.id());
        return live == null || live instanceof Villager villager
                && com.jvn.villagerretaliation.allegiance.VillageAllegianceApi.canonicalPrimary(level, villager).filter(town.id()::equals).isPresent()
                && villager.isAlive() && !villager.isBaby() && !villager.isPassenger()
                && villager.getTradingPlayer() == null && villager.getTarget() == null && villager.getLastHurtByMob() == null
                && !villager.isOnFire() && !villager.isInWaterOrBubble() && !SettlementWorkforceService.hasPersonalCommitment(villager);
    }
    static ItemStack boat(ServerLevel level, SettlementRecord town, SettlementDepot depot) {
        if (depot == null || !depot.rulesObserved() || !SettlementAidStock.depots(town, false).contains(depot)) return ItemStack.EMPTY;
        var snapshot = new SettlementSupplyStock.Snapshot(level, town);
        for (int slot = 0; slot < depot.getContainerSize(); slot++) {
            var item = depot.getItem(slot); if (!(item.getItem() instanceof BoatItem)) continue;
            var facts = snapshot.observe(depot.position(), item, null);
            if (facts.known() && facts.available() > Math.max(0, depot.stockTarget(item)) && SettlementLogisticsService.framedAllowance(level, depot, item,
                    VillagerFilterPolicy.TransferOperation.PROVIDE, true) > 0) return item.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }
    private static List<SettlementAidPreparation.Candidate> candidates(SettlementAidPreparation preparation) {
        var result = new ArrayList<SettlementAidPreparation.Candidate>(2);
        if (preparation.delivery != null) result.add(preparation.delivery);
        if (preparation.collection != null) result.add(preparation.collection); return result;
    }
    static void releaseRoutes(ServerLevel source, SettlementFoodRequest request) {
        if (request.preparation == null) return;
        var town = SettlementSavedData.get(source).get(request.donor()).orElse(null); var level = dimension(source.getServer(), town);
        if (level == null) return;
        DiplomaticRouteService.forget(level, request.preparation.crossing);
        for (var candidate : candidates(request.preparation)) if (candidate.approach != null) DiplomaticRouteService.forget(level, candidate.approach);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var level = event.getServer().overworld(); if (!enabled(level)) return;
        SCHEDULES.computeIfAbsent(event.getServer(), ignored -> new Schedule()).tick(event.getServer());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SCHEDULES.remove(event.getServer()); }

    static final class Schedule {
        private List<UUID> requests = List.of();
        private List<VillageAllegianceId> towns = List.of();
        private int requestCursor, townCursor;
        private long refresh;
        private Runnable planning;
        private UUID planningRequest;

        void tick(MinecraftServer server) {
            var level = server.overworld(); var data = SettlementSavedData.get(level); long now = now(server);
            if (this.planning != null) {
                this.planning.run();
                if (SettlementWork.complete(this.planning)) { this.planning = null; this.planningRequest = null; }
            }
            if (now >= this.refresh) {
                this.requests = data.aid().requests.values().stream().filter(request -> !request.terminal()).map(SettlementFoodRequest::id).toList();
                this.towns = data.settlements().stream().filter(town -> town.territory().active() && town.autonomous() != null).map(SettlementRecord::id).toList();
                this.refresh = now + 200;
            }
            if (now % 5 == 0 && !this.requests.isEmpty()) {
                var request = data.aid().request(this.requests.get(Math.floorMod(this.requestCursor++, this.requests.size())));
                if (request != null && !request.terminal() && !request.id().equals(this.planningRequest)) step(level, request, now);
            }
            if (now % 20 == 0) {
                receipt(level);
                if (!this.towns.isEmpty()) {
                    var town = data.get(this.towns.get(Math.floorMod(this.townCursor++, this.towns.size()))).orElse(null);
                    if (town != null) observe(level, town, now);
                }
            }
        }

        void observe(ServerLevel root, SettlementRecord town, long now) {
            var level = dimension(root.getServer(), town); if (level == null) return;
            var data = SettlementSavedData.get(root); var rules = VillageDiplomacyRules.get(root.getServer());
            var state = data.aid().town(town);
            if (now - state.lastObserved < rules.cadence()) return;
            long daily = (long) VillageAllegianceRegistrySavedData.get(level).residentCount(town.id(), level.getGameTime()) * SettlementFoodService.dailyNutrition();
            boolean shortage = daily > 0 && SettlementAidStock.nutrition(town) < daily && need(level, town) > 0;
            state.lastObserved = now;
            if (!shortage) state.shortageSince = -1;
            else if (state.shortageSince < 0) state.shortageSince = now;
            data.aid().changed(); data.setDirty();
            if (!shortage || now - state.shortageSince < rules.shortageDuration() || now < state.nextRequest) return;
            var contacts = VillageDiplomacySavedData.get(level).contacts(town.id()).keySet().stream()
                    .map(id -> data.get(id).orElse(null)).filter(other -> available(level, town, other))
                    .sorted(Comparator.<SettlementRecord>comparingInt(other -> -VillageDiplomacySavedData.get(level).standing(town.id(), other.id()))
                            .thenComparingDouble(other -> other.center().distSqr(town.center()))).toList();
            for (var donor : contacts) if (create(level, town, donor, need(level, town), town.leader(), now) == Result.APPLIED) break;
        }

        void step(ServerLevel root, SettlementFoodRequest request, long now) {
            var data = SettlementSavedData.get(root); var donor = data.get(request.donor()).orElse(null); var recipient = data.get(request.requester()).orElse(null);
            var level = dimension(root.getServer(), donor);
            if (level == null || recipient == null || !donor.territory().active() || !recipient.territory().active() || donor.id().equals(recipient.id())) {
                transition(data, request, request.state(), "settlement_unavailable"); return;
            }
            if (now >= request.expires() && beforeDeparture(request)) {
                if (request.offer == null || request.offer.empty() || SettlementAidStock.refund(level, donor, request)) {
                    releaseRoutes(level, request); transition(data, request, SettlementFoodRequest.State.EXPIRED, "");
                } else transition(data, request, request.state(), "refund_storage_full");
                return;
            }
            switch (request.state()) {
                case REQUEST_SENT -> {
                    if (now >= request.arrives() && VillageDiplomacyService.receiveCorrespondence(level, recipient.id(), donor.id()))
                        transition(data, request, SettlementFoodRequest.State.AWAITING_OFFER, "");
                }
                case AWAITING_OFFER -> {
                    var policy = data.aid().policy(donor); var state = data.aid().town(donor);
                    if (!policy.automatic() || !donor.permits(policy.authority(), SettlementPermission.DIPLOMACY) || now < state.nextAid) return;
                    int standing = VillageDiplomacySavedData.get(level).standing(donor.id(), recipient.id());
                    if (!available(level, donor, recipient) || standing < policy.minimumStanding()) return;
                    request.donorAuthority = policy.authority(); request.automaticApproval = true;
                    request.maximumItems = Math.max(1, policy.maximumItems() * (50 + Math.max(0, standing) / 2) / 100);
                    transition(data, request, SettlementFoodRequest.State.OFFER_PREPARING, "");
                }
                case OFFER_PREPARING -> prepare(level, donor, recipient, request);
                case OFFER_SENT -> {
                    if (now >= request.arrives() && SettlementAidCheckpoint.durable(level, request.revision())
                            && VillageDiplomacyService.receiveCorrespondence(level, donor.id(), recipient.id()))
                        transition(data, request, SettlementFoodRequest.State.AWAITING_ACCEPTANCE, "");
                }
                case AWAITING_ACCEPTANCE -> {
                    if (recipient.autonomous() != null) accept(level, request);
                }
                case ACCEPTANCE_SENT -> {
                    if (now >= request.arrives() && VillageDiplomacyService.receiveCorrespondence(level, recipient.id(), donor.id()))
                        transition(data, request, SettlementFoodRequest.State.PREPARING, "");
                }
                case DECLINE_SENT, CANCELLING -> {
                    if (now < request.arrives()) return;
                    if (request.offer == null || request.offer.empty() || SettlementAidStock.refund(level, donor, request))
                        transition(data, request, request.state() == SettlementFoodRequest.State.CANCELLING ? SettlementFoodRequest.State.CANCELLED : SettlementFoodRequest.State.DECLINED, "");
                    else transition(data, request, request.state(), "refund_storage_full");
                }
                default -> { }
            }
        }

        void prepare(ServerLevel level, SettlementRecord donor, SettlementRecord recipient, SettlementFoodRequest request) {
            var data = SettlementSavedData.get(level);
            if (!approvalAvailable(level, donor, recipient, request)) {
                transition(data, request, request.state(), "approval_unavailable"); return;
            }
            if (request.preparation == null) {
                long active = data.aid().requests.values().stream().filter(value -> !value.terminal() && value.preparation != null).count();
                if (active >= VillageDiplomacyRules.get(level.getServer()).activeJourneys()) {
                    transition(data, request, request.state(), "courier_queue"); return;
                }
                quote(level, donor, request, null, quote -> initialize(level, donor, recipient, request, quote)); return;
            }
            var plan = request.preparation;
            var source = donor.depots().get(plan.donorDepot); var target = recipient.depots().get(plan.recipientDepot);
            if (source == null || target == null || !SettlementAidStock.depots(donor, false).contains(source)
                    || !SettlementAidStock.depots(recipient, true).contains(target)) {
                transition(data, request, request.state(), "depot_unavailable"); return;
            }
            if (RouteSavedData.get(level).readOnly()) { transition(data, request, request.state(), "route_data_read_only"); return; }
            try {
                var crossing = DiplomaticRouteService.prepareAccess(level, plan.crossing, plan.from, plan.to, plan.boatAllowed, 2, 2);
                var route = DiplomaticRouteService.ready(level, plan.crossing);
                if (route == null) { transition(data, request, request.state(), crossing.blocker().isEmpty() ? "preparing_route" : crossing.blocker()); return; }
                int delivery = approach(level, donor, request, plan.delivery, route.steps().getFirst().position(), source, route.needsBoat());
                int collection = approach(level, recipient, request, plan.collection, route.steps().getLast().position(), target, route.needsBoat());
                if (delivery == -1 || collection == -1) { transition(data, request, request.state(), "preparing_route"); return; }
                if (delivery == Integer.MAX_VALUE && collection == Integer.MAX_VALUE) {
                    String blocker = plan.delivery != null ? plan.delivery.blocker : plan.collection.blocker;
                    transition(data, request, request.state(), blocker.isEmpty() ? "courier_unavailable" : blocker); return;
                }
                var arrangement = delivery <= collection ? SettlementFoodOffer.Arrangement.DONOR_DELIVERY : SettlementFoodOffer.Arrangement.REQUESTER_COLLECTION;
                quote(level, donor, request, source.id(), quote -> offer(level, donor, request, arrangement, quote));
            } catch (IllegalStateException failure) {
                if (!"route_queue_full".equals(failure.getMessage()) && !"route_data_read_only".equals(failure.getMessage())) throw failure;
                transition(data, request, request.state(), failure.getMessage());
            }
        }

        void quote(ServerLevel level, SettlementRecord donor, SettlementFoodRequest request, UUID depot, java.util.function.Consumer<SettlementAidStock.Quote> complete) {
            if (this.planning != null) return;
            long requestRevision = request.revision(); var valid = SettlementAllocationService.aidGuard(level, donor);
            this.planningRequest = request.id();
            this.planning = SettlementDemandService.planAid(level, donor, snapshot -> {
                var data = SettlementSavedData.get(level);
                if (data.readOnly() || request.revision() != requestRevision || request.state() != SettlementFoodRequest.State.OFFER_PREPARING) return;
                if (!valid.getAsBoolean()) { transition(data, request, request.state(), "stock_changed"); return; }
                complete.accept(SettlementAidStock.quote(level, donor, data.aid().policy(donor), snapshot, donor.revision(),
                        request.nutrition(), request.maximumItems, depot));
            });
        }

        void initialize(ServerLevel level, SettlementRecord donor, SettlementRecord recipient, SettlementFoodRequest request, SettlementAidStock.Quote quote) {
            var data = SettlementSavedData.get(level);
            if (!quote.blocker().isEmpty()) { transition(data, request, request.state(), quote.blocker()); return; }
            var source = donor.depots().get(quote.parcels().getFirst().depot());
            var target = SettlementAidStock.depots(recipient, true).stream().filter(SettlementDepot::rulesObserved)
                    .min(Comparator.comparingDouble(depot -> depot.position().distSqr(source.position()))).orElse(null);
            if (target == null) { transition(data, request, request.state(), "depot_unavailable"); return; }
            var delivery = courier(level, donor, source.position(), request.id()); var collection = courier(level, recipient, target.position(), request.id());
            if (delivery == null && collection == null) { transition(data, request, request.state(), "courier_unavailable"); return; }
            request.preparation = new SettlementAidPreparation(source.id(), target.id(), source.position(), target.position(),
                    delivery != null && !boat(level, donor, source).isEmpty() || collection != null && !boat(level, recipient, target).isEmpty(),
                    delivery == null ? null : new SettlementAidPreparation.Candidate(delivery.id(), delivery.position()),
                    collection == null ? null : new SettlementAidPreparation.Candidate(collection.id(), collection.position()));
            data.aid().changed(request); data.setDirty();
        }

        int approach(ServerLevel level, SettlementRecord home, SettlementFoodRequest request, SettlementAidPreparation.Candidate candidate,
                     BlockPos access, SettlementDepot depot, boolean requiresBoat) {
            if (candidate == null) return Integer.MAX_VALUE;
            var data = SettlementSavedData.get(level); var resident = home.residents().get(candidate.resident);
            String blocker = !eligible(level, home, resident, request.id()) ? "courier_unavailable"
                    : requiresBoat && boat(level, home, depot).isEmpty() ? "boat_unavailable" : "";
            if (!blocker.isEmpty()) {
                if (!candidate.blocker.equals(blocker)) { candidate.blocker = blocker; data.aid().changed(request); data.setDirty(); }
                return Integer.MAX_VALUE;
            }
            if (candidate.approach == null || !access.equals(candidate.access)) {
                if (candidate.approach != null) DiplomaticRouteService.forget(level, candidate.approach);
                candidate.approach = UUID.randomUUID(); candidate.access = access; data.aid().changed(request); data.setDirty();
            }
            var preparation = DiplomaticRouteService.prepare(level, candidate.approach, candidate.origin, access, false);
            var route = DiplomaticRouteService.ready(level, candidate.approach);
            if (route != null) return route.travelCost();
            if (!candidate.blocker.equals(preparation.blocker())) {
                candidate.blocker = preparation.blocker(); data.aid().changed(request); data.setDirty();
            }
            return preparation.state() == RoutePreparation.State.BLOCKED || preparation.state() == RoutePreparation.State.CANCELLED ? Integer.MAX_VALUE : -1;
        }

        void offer(ServerLevel level, SettlementRecord donor, SettlementFoodRequest request, SettlementFoodOffer.Arrangement arrangement, SettlementAidStock.Quote quote) {
            var data = SettlementSavedData.get(level); var plan = request.preparation; var candidate = plan.candidate(arrangement);
            var home = arrangement == SettlementFoodOffer.Arrangement.DONOR_DELIVERY ? donor : data.get(request.requester()).orElse(null);
            if (home == null || !approvalAvailable(level, donor, data.get(request.requester()).orElse(null), request) || !eligible(level, home, home.residents().get(candidate.resident), request.id())) return;
            if (!quote.blocker().isEmpty()) { transition(data, request, request.state(), quote.blocker()); return; }
            if (!SettlementAidStock.reserve(level, donor, request, quote, arrangement, candidate.resident)) {
                transition(data, request, request.state(), "stock_changed"); return;
            }
            request.messageSender = request.donor();
            request.arrives = now(level.getServer()) + delay(level, request);
            data.aid().town(donor).nextAid = now(level.getServer()) + data.aid().policy(donor).intervalTicks();
            transition(data, request, SettlementFoodRequest.State.OFFER_SENT, "");
        }
    }

    private static boolean beforeDeparture(SettlementFoodRequest request) {
        return switch (request.state()) {
            case REQUEST_SENT, AWAITING_OFFER, OFFER_PREPARING, OFFER_SENT, AWAITING_ACCEPTANCE, ACCEPTANCE_SENT, DECLINE_SENT, CANCELLING, PREPARING -> true;
            default -> false;
        };
    }
    static void receipt(ServerLevel level) {
        var data = SettlementSavedData.get(level); var aid = data.aid();
        if (aid.receipts.isEmpty() || !SettlementAidCheckpoint.durable(level, aid.revision())) return;
        var receipt = aid.receipts.firstEntry().getValue(); var registry = VillageAllegianceRegistrySavedData.get(level);
        var donor = registry.canonicalRecord(receipt.donor()).orElse(null); var recipient = registry.canonicalRecord(receipt.recipient()).orElse(null);
        if (donor == null || recipient == null || donor.archived() || recipient.archived() || donor.id().equals(recipient.id())) return;
        var diplomacy = VillageDiplomacySavedData.get(level);
        if (!diplomacy.received(donor.id(), recipient.id(), aid.stream(), receipt.sequence())
                && !VillageDiplomacyService.standingEvent(level, donor.id(), recipient.id(), aid.stream(), receipt.sequence(), receipt.standing(), "food_aid")) return;
        if (!VillageDiplomacyCheckpoint.durable(level, diplomacy.revision())) return;
        aid.receipts.remove(receipt.sequence()); aid.changed(); data.setDirty();
    }
}
