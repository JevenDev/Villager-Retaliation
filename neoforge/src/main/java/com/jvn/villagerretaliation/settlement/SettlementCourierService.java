package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = "villagerretaliation")
public final class SettlementCourierService {
    static final String JOURNEY = "VillagerRetaliationDiplomaticJourney", INCARNATION = "VillagerRetaliationDiplomaticIncarnation";
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private static final Map<Villager, SettlementFoodRequest> WAITING = new WeakHashMap<>();
    private SettlementCourierService() { }
    static Runtime runtime(MinecraftServer server) { return RUNTIMES.computeIfAbsent(server, ignored -> new Runtime()); }
    static final class Runtime {
        final Map<UUID, CompletableFuture<Void>> checkpoints = new java.util.HashMap<>();
        final Map<UUID, Long> retryAt = new java.util.HashMap<>();
        boolean stopping;
        long navigationTick = Long.MIN_VALUE;
        int courierCursor;
        int nextCourierStart(int count) {
            if (count == 0) return 0;
            int start = Math.floorMod(this.courierCursor, count);
            this.courierCursor = (start + 1) % count;
            return start;
        }
    }
    static Entity existing(MinecraftServer server, UUID id) {
        for (var level : server.getAllLevels()) { var entity = level.getEntity(id); if (entity != null) return entity; }
        return null;
    }
    static SettlementFoodRequest request(Villager villager) {
        return villager.level() instanceof ServerLevel level ? SettlementSavedData.get(level).aid().courier(villager.getUUID()) : null;
    }
    public static boolean owns(Villager villager) {
        var request = request(villager); return request != null && request.journey != null && request.journey.ownsResident();
    }
    public static boolean controls(Villager villager) {
        var waiting = WAITING.get(villager);
        if (waiting != null && waiting.state() == SettlementFoodRequest.State.PREPARING && !danger(villager)) return true;
        return owns(villager) && !danger(villager);
    }
    static boolean danger(Villager villager) {
        return villager.isDeadOrDying() || com.jvn.villagerretaliation.combat.downed.VillagerDownedService.isDowned(villager)
                || villager.getTarget() != null || villager.getLastHurtByMob() != null || villager.isOnFire() || villager.isInLava()
                || com.jvn.villagerretaliation.villager.VillagerRetaliationVillagerBrainUtil.hasVanillaFleeState((ServerLevel) villager.level(), villager.getBrain());
    }
    static void changed(ServerLevel level, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level); data.aid().changed(request);
        request.journey.revision = request.revision(); data.setDirty();
    }
    static void block(ServerLevel level, SettlementFoodRequest request, String reason) {
        var journey = request.journey;
        if (journey != null && !journey.blocker.equals(reason)) { journey.blocker = reason; changed(level, request); }
        SettlementAidService.transition(SettlementSavedData.get(level), request, request.state(), reason);
    }
    static void begin(ServerLevel level, SettlementFoodRequest request, long now) {
        var data = SettlementSavedData.get(level); var offer = request.offer; var plan = request.preparation;
        if (request.journey != null || offer == null || plan == null || offer.custody() != SettlementFoodOffer.Custody.RESERVED) return;
        if (data.aid().couriers.values().stream().map(data.aid()::request).filter(value -> value != null && value.journey != null && value.journey.ownsResident()).count() >= VillageDiplomacyRules.get(level.getServer()).activeJourneys()) {
            SettlementAidService.transition(data, request, request.state(), "journey_queue_full"); return;
        }
        boolean delivery = offer.arrangement() == SettlementFoodOffer.Arrangement.DONOR_DELIVERY;
        var home = data.get(delivery ? request.donor() : request.requester()).orElse(null);
        var donor = data.get(request.donor()).orElse(null); var recipient = data.get(request.requester()).orElse(null);
        var resident = home == null ? null : home.residents().get(offer.courier());
        if (!SettlementAidService.available(level, donor, recipient) || !donor.permits(request.donorAuthority, SettlementPermission.DIPLOMACY)
                || !recipient.permits(request.authority(), SettlementPermission.DIPLOMACY)
                || !SettlementAidService.eligible(level, home, resident, request.id())) {
            SettlementAidService.transition(data, request, request.state(), "courier_unavailable"); return;
        }
        var live = existing(level.getServer(), resident.id());
        if (live != null && physicalCount(data) >= VillageDiplomacyRules.get(level.getServer()).physicalCouriers()) {
            SettlementAidService.transition(data, request, request.state(), "physical_courier_queue"); return;
        }
        if (SettlementAidStock.nutrition(donor) < SettlementAidStock.reserve(level, donor, data.aid().policy(donor))) {
            SettlementAidService.transition(data, request, request.state(), "protected_food_reserve"); return;
        }
        var context = live instanceof Villager villager ? villager : resident.context(level);
        if (context == null || context.level() != level) { SettlementAidService.transition(data, request, request.state(), "resident_context_unavailable"); return; }
        if (live instanceof Villager villager) { WAITING.put(villager, request); villager.getNavigation().stop(); }
        var candidate = plan.candidate(offer.arrangement());
        if (candidate == null || candidate.access == null || candidate.approach == null) {
            SettlementAidService.transition(data, request, request.state(), "courier_plan_unavailable"); return;
        }
        if (!candidate.origin.equals(context.blockPosition())) {
            if (candidate.approach != null) DiplomaticRouteService.forget(level, candidate.approach);
            candidate.origin = context.blockPosition().immutable(); candidate.approach = UUID.randomUUID();
            data.aid().changed(request); data.setDirty();
        }
        try {
            var prepared = DiplomaticRouteService.prepare(level, candidate.approach, candidate.origin, candidate.access, false);
            var approach = DiplomaticRouteService.ready(level, candidate.approach);
            var crossing = DiplomaticRouteService.ready(level, plan.crossing);
            if (approach == null || crossing == null) {
                SettlementAidService.transition(data, request, request.state(), prepared.blocker().isEmpty() ? "preparing_route" : prepared.blocker()); return;
            }
            if (!delivery) crossing = crossing.reversed();
            var depot = home.depots().get(delivery ? plan.donorDepot : plan.recipientDepot);
            var boat = crossing.needsBoat() ? SettlementAidService.boat(level, home, depot) : ItemStack.EMPTY;
            if (crossing.needsBoat() && boat.isEmpty()) { SettlementAidService.transition(data, request, request.state(), "boat_unavailable"); return; }
            if (resident.courierIncarnation() >= Long.MAX_VALUE - 1) { SettlementAidService.transition(data, request, request.state(), "resident_incarnation_exhausted"); return; }
            long incarnation = resident.courierIncarnation() + 1;
            context.getPersistentData().putUUID(JOURNEY, request.id()); context.getPersistentData().putLong(INCARNATION, incarnation);
            var journey = new SettlementCourierJourney(request.id(), resident.id(), delivery ? request.donor() : request.requester(), level.dimension().location(), approach, crossing,
                    context.saveWithoutId(new CompoundTag()), incarnation, boat, now);
            if (!boat.isEmpty()) depot.withdraw(item -> ItemStack.isSameItemSameComponents(item, boat), 1);
            resident.courier(request.id(), incarnation, true); home.changed(); data.aid().journey(request, journey);
            SettlementAidService.transition(data, request, SettlementFoodRequest.State.TRAVELLING, "");
            changed(level, request); WAITING.remove(context);
        } catch (IllegalStateException failure) {
            if (!"route_queue_full".equals(failure.getMessage()) && !"route_data_read_only".equals(failure.getMessage())) throw failure;
            SettlementAidService.transition(data, request, request.state(), failure.getMessage());
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        long started = com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics.start();
        try { tickServer(event.getServer()); }
        finally { com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics.finish(event.getServer(),
                com.jvn.villagerretaliation.diplomacy.VillageDiplomacyMetrics.Part.COURIERS, started); }
    }
    private static void tickServer(MinecraftServer server) {
        var root = server.overworld(); if (!SettlementAidService.enabled(root)) return;
        var runtime = RUNTIMES.computeIfAbsent(server, ignored -> new Runtime()); if (runtime.stopping) return;
        var data = SettlementSavedData.get(root); long now = root.getGameTime();
        var couriers = List.copyOf(data.aid().couriers.values());
        int start = runtime.nextCourierStart(couriers.size());
        for (int offset = 0; offset < couriers.size(); offset++) {
            var id = couriers.get((start + offset) % couriers.size());
            var request = data.aid().request(id); if (request == null || request.journey == null) continue;
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, request.journey.dimension));
            if (level == null) { block(root, request, "settlement_unavailable"); continue; }
            step(level, request, now, runtime);
            if (now % 20 == 0) {
                SettlementCourierInterference.update(level, request);
                if (request.journey.released() && request.journey.interference.values().stream().allMatch(incident -> incident.applied))
                    data.aid().couriers.remove(request.journey.resident);
            }
        }
    }
    static void step(ServerLevel level, SettlementFoodRequest request, long now, Runtime runtime) {
        var journey = request.journey; var entity = existing(level.getServer(), journey.resident());
        var live = entity instanceof Villager villager && entity.level() == level && !entity.isRemoved() ? villager : null;
        if (entity != null && live == null) { block(level, request, "courier_wrong_dimension"); return; }
        if (journey.presence == SettlementCourierJourney.Presence.DEAD) {
            SettlementCourierRecovery.update(level, request, now, runtime); SettlementCourierRecovery.unlock(level, request); return;
        }
        if (journey.released()) return;
        if (journey.repair != null) {
            if (live != null && danger(live)) { block(level, request, "courier_in_danger"); return; }
            if (now % 20 == 0) repairRoute(level, request, live);
            return;
        }
        if (journey.presence == SettlementCourierJourney.Presence.CLAIMING) {
            if (!SettlementAidCheckpoint.durable(level, journey.revision)) return;
            journey.presence = live == null ? SettlementCourierJourney.Presence.STORED : SettlementCourierJourney.Presence.PHYSICAL;
            changed(level, request); return;
        }
        if (journey.presence == SettlementCourierJourney.Presence.RETIRING) {
            if (!SettlementAidCheckpoint.durable(level, journey.revision)) return;
            if (live != null && (nearby(level, live.blockPosition(), 96) || danger(live) || journey.obstructed)) {
                live.getPersistentData().putLong(INCARNATION, journey.incarnation);
                if (live.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat && boat.getUUID().equals(journey.boatId))
                    boat.getPersistentData().putLong(INCARNATION, journey.boatIncarnation);
                capture(journey, live); journey.presence = SettlementCourierJourney.Presence.PHYSICAL; changed(level, request); return;
            }
            if (live != null) retire(level, journey, live);
            journey.presence = SettlementCourierJourney.Presence.STORED; journey.lastTick = now; changed(level, request); return;
        }
        if (live != null) {
            if (live.getPersistentData().getLong(INCARNATION) != journey.incarnation) { block(level, request, "courier_identity_mismatch"); return; }
            if (journey.presence == SettlementCourierJourney.Presence.MATERIALIZING) {
                if (!nativeDurable(level, journey, live, runtime, now)) return;
                journey.presence = SettlementCourierJourney.Presence.PHYSICAL; changed(level, request);
            }
            if (journey.presence == SettlementCourierJourney.Presence.STRANDED) {
                journey.presence = SettlementCourierJourney.Presence.PHYSICAL; changed(level, request);
            }
            physical(level, request, live, now); return;
        }
        if (journey.presence == SettlementCourierJourney.Presence.PHYSICAL) {
            block(level, request, "awaiting_native_courier"); return;
        }
        if (nearby(level, journey.position, 80)) {
            materialize(level, request, now); return;
        }
        if (journey.presence == SettlementCourierJourney.Presence.MATERIALIZING) {
            journey.presence = SettlementCourierJourney.Presence.STORED; journey.lastTick = now; changed(level, request); return;
        }
        if (journey.presence == SettlementCourierJourney.Presence.STRANDED) return;
        if (journey.blocker.equals("physical_courier_queue") || journey.blocker.equals("waiting_for_ticking_terrain")) block(level, request, "");
        if (journey.atEnd()) { arrived(level, request, null, now); return; }
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LOST) { block(level, request, "boat_lost"); return; }
        var next = journey.route().steps().get(journey.step + 1);
        var observation = com.jvn.villagerretaliation.diplomacy.route.RouteSavedData.get(level).observation(level.dimension().location(), next.position().getX(), next.position().getZ());
        if (observation == null) { block(level, request, "terrain_unobserved"); return; }
        if (observation.height(next.position().getX(), next.position().getZ()) != next.position().getY()
                || observation.kind(next.position().getX(), next.position().getZ()) != (next.mode() == DiplomaticRoute.Mode.SAIL || next.mode() == DiplomaticRoute.Mode.LAUNCH
                ? com.jvn.villagerretaliation.diplomacy.route.RouteTerrain.WATER : com.jvn.villagerretaliation.diplomacy.route.RouteTerrain.WALK)) {
            block(level, request, "terrain_changed"); return;
        }
        if (journey.blocker.equals("terrain_changed") || journey.blocker.equals("terrain_unobserved")) block(level, request, "");
        if (next.mode() == DiplomaticRoute.Mode.LAUNCH && journey.boatIncarnation == Long.MAX_VALUE) {
            block(level, request, "boat_identity_exhausted"); return;
        }
        int before = journey.step; journey.advance(now);
        if (journey.step != before) { travelled(level, request); changed(level, request); }
    }
    static void travelled(ServerLevel level, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level); var map = com.jvn.villagerretaliation.diplomacy.route.DiplomaticTravelMapData.get(level);
        var donor = data.get(request.donor()).orElse(null); var recipient = data.get(request.requester()).orElse(null);
        if (donor != null) map.observe(level, donor.id(), request.journey.position);
        if (recipient != null) map.observe(level, recipient.id(), request.journey.position);
    }
    static boolean retry(ServerLevel level, SettlementFoodRequest request) {
        var journey = request.journey;
        if (journey == null || !journey.ownsResident() || journey.repairs >= VillageDiplomacyRules.get(level.getServer()).routeRetries()
                || journey.presence != SettlementCourierJourney.Presence.PHYSICAL && journey.presence != SettlementCourierJourney.Presence.STORED
                || journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED || journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHING
                || journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKING || journey.boatCustody == SettlementCourierJourney.BoatCustody.LOST) return false;
        var entity = existing(level.getServer(), journey.resident);
        if (entity instanceof Villager courier) {
            if (danger(courier) || courier.level() != level) return false;
            capture(journey, courier); journey.position = courier.blockPosition().immutable(); courier.getNavigation().stop();
        }
        if (journey.repair != null) DiplomaticRouteService.forget(level, journey.repair);
        journey.repair = UUID.randomUUID(); journey.repairOrigin = journey.position; journey.repairs++; journey.obstructed = true;
        block(level, request, "preparing_route"); changed(level, request); return true;
    }
    private static void repairRoute(ServerLevel level, SettlementFoodRequest request, Villager courier) {
        var journey = request.journey;
        if (courier != null && !courier.blockPosition().equals(journey.repairOrigin)) { block(level, request, "courier_moved"); return; }
        try {
            var preparation = DiplomaticRouteService.prepare(level, journey.repair, journey.repairOrigin, journey.route().steps().getLast().position(),
                    journey.boatCustody == SettlementCourierJourney.BoatCustody.PACKED);
            var path = DiplomaticRouteService.ready(level, journey.repair);
            if (path == null) { block(level, request, preparation.blocker().isEmpty() ? "preparing_route" : preparation.blocker()); return; }
            journey.detour = path; journey.step = 0; journey.position = path.steps().getFirst().position();
            journey.obstructed = false; journey.lastTick = level.getServer().overworld().getGameTime(); journey.lastProgress = journey.lastTick;
            DiplomaticRouteService.forget(level, journey.repair); journey.repair = null; journey.repairOrigin = null;
            block(level, request, ""); changed(level, request);
        } catch (IllegalStateException failure) {
            if (!"route_queue_full".equals(failure.getMessage()) && !"route_data_read_only".equals(failure.getMessage())) throw failure;
            block(level, request, failure.getMessage());
        }
    }
    static boolean nearby(ServerLevel level, BlockPos position, int radius) {
        for (var player : level.players()) if (!player.isSpectator() && player.distanceToSqr(Vec3.atCenterOf(position)) <= (double) radius * radius) return true;
        return false;
    }
    static boolean nativeDurable(ServerLevel level, SettlementCourierJourney journey, Entity entity, Runtime runtime, long now) {
        var pending = runtime.checkpoints.get(entity.getUUID());
        if (pending != null) {
            if (!pending.isDone()) return false;
            runtime.checkpoints.remove(entity.getUUID());
            try { pending.join(); return true; }
            catch (CompletionException failure) {
                LogUtils.getLogger().error("Courier entity checkpoint failed, ownership remains reserved", failure.getCause());
                runtime.retryAt.put(entity.getUUID(), now + 200);
            }
        }
        if (now < runtime.retryAt.getOrDefault(entity.getUUID(), 0L)) return false;
        runtime.checkpoints.put(entity.getUUID(), SettlementCourierCheckpoint.persist(level, entity)); return false;
    }
    private static long physicalCount(SettlementSavedData data) {
        return data.aid().couriers.values().stream().map(data.aid()::request).filter(value -> value != null && value.journey != null)
                .filter(value -> value.journey.presence == SettlementCourierJourney.Presence.PHYSICAL
                        || value.journey.presence == SettlementCourierJourney.Presence.MATERIALIZING
                        || value.journey.presence == SettlementCourierJourney.Presence.RETIRING).count();
    }
    static void materialize(ServerLevel level, SettlementFoodRequest request, long now) {
        var journey = request.journey; var data = SettlementSavedData.get(level);
        long physical = physicalCount(data);
        if (journey.presence != SettlementCourierJourney.Presence.MATERIALIZING) {
            if (physical >= VillageDiplomacyRules.get(level.getServer()).physicalCouriers()) { block(level, request, "physical_courier_queue"); return; }
            if (!level.isPositionEntityTicking(journey.position) || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(journey.position).toLong())) {
                block(level, request, "waiting_for_ticking_terrain"); return;
            }
            journey.presence = SettlementCourierJourney.Presence.MATERIALIZING; changed(level, request); return;
        }
        if (!SettlementAidCheckpoint.durable(level, journey.revision) || !level.isPositionEntityTicking(journey.position)) return;
        var courier = net.minecraft.world.entity.EntityType.VILLAGER.create(level); if (courier == null) return;
        if (journey.finished()) {
            var home = data.get(journey.home).orElse(null); var resident = home == null ? null : home.residents().get(journey.resident);
            if (resident == null || resident.context(level) == null) { block(level, request, "resident_context_unavailable"); return; }
            courier = resident.context(level);
        } else courier.load(journey.residentContext.copy());
        courier.moveTo(journey.position.getX() + 0.5, journey.position.getY(), journey.position.getZ() + 0.5, courier.getYRot(), courier.getXRot());
        courier.getPersistentData().putUUID(JOURNEY, journey.id); courier.getPersistentData().putLong(INCARNATION, journey.incarnation);
        if (!level.noCollision(courier) || !SettlementCourierBoat.materialize(level, journey, courier)) {
            block(level, request, "courier_landing_obstructed"); return;
        }
        if (!level.addFreshEntity(courier)) { courier.stopRiding(); block(level, request, "courier_spawn_rejected"); return; }
        journey.lastTick = now; block(level, request, "");
    }
    static boolean navigationDue(ServerLevel level, SettlementCourierJourney journey, long now) {
        var runtime = runtime(level.getServer());
        if (now < journey.nextNavigation || runtime.navigationTick == now) return false;
        runtime.navigationTick = now; journey.nextNavigation = now + (journey.obstructed ? 40 : 10);
        return true;
    }
    static void physical(ServerLevel level, SettlementFoodRequest request, Villager courier, long now) {
        var journey = request.journey;
        if (!courier.isAlive()) return;
        if (danger(courier)) { journey.obstructed = true; block(level, request, "courier_in_danger"); return; }
        if (journey.blocker.equals("courier_in_danger")) { journey.obstructed = false; block(level, request, ""); }
        if (journey.finished()) { arrived(level, request, courier, now); return; }
        if (journey.atEnd()) { arrived(level, request, courier, now); return; }
        var target = journey.route().steps().get(journey.step + 1);
        if (!level.isPositionEntityTicking(target.position())) {
            if (!nearby(level, courier.blockPosition(), 96) && !journey.obstructed) beginRetirement(level, request, courier, now);
            else block(level, request, "waiting_for_ticking_terrain");
            return;
        }
        if (target.mode() != DiplomaticRoute.Mode.WALK || journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED) {
            SettlementCourierBoat.navigate(level, request, courier, target, now); return;
        }
        double distance = courier.position().distanceToSqr(Vec3.atBottomCenterOf(target.position()));
        if (distance <= 0.64) {
            journey.step++; journey.position = target.position(); journey.lastProgress = now; journey.obstructed = false;
            block(level, request, ""); capture(journey, courier); travelled(level, request); changed(level, request);
        } else if (navigationDue(level, journey, now)) {
            if (!courier.getNavigation().moveTo(courier.getNavigation().createPath(target.position(), 0), 0.5)
                    || now - journey.lastProgress > 200) {
                journey.obstructed = true; block(level, request, "courier_path_obstructed");
            }
        }
        if (!nearby(level, courier.blockPosition(), 96) && !journey.obstructed && distance <= 0.64) beginRetirement(level, request, courier, now);
    }
    static void capture(SettlementCourierJourney journey, Villager courier) {
        journey.residentContext = courier.saveWithoutId(new CompoundTag());
        if (courier.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat && boat.getUUID().equals(journey.boatId))
            journey.boatContext = boat.saveWithoutId(new CompoundTag());
    }
    static void beginRetirement(ServerLevel level, SettlementFoodRequest request, Villager courier, long now) {
        var journey = request.journey; if (danger(courier) || journey.obstructed) return;
        if (journey.incarnation >= Long.MAX_VALUE - 1) { block(level, request, "resident_incarnation_exhausted"); return; }
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED && journey.boatIncarnation == Long.MAX_VALUE) {
            block((ServerLevel) courier.level(), request, "boat_identity_exhausted"); return;
        }
        journey.incarnation++;
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED) journey.boatIncarnation++;
        capture(journey, courier); courier.getNavigation().stop(); journey.presence = SettlementCourierJourney.Presence.RETIRING;
        journey.lastTick = now; changed(level, request);
    }
    static void retire(ServerLevel level, SettlementCourierJourney journey, Villager courier) {
        var boat = courier.getVehicle(); courier.stopRiding();
        courier.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        if (boat != null && boat.getUUID().equals(journey.boatId) && boat.getPassengers().isEmpty()) boat.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
    }
    static void arrived(ServerLevel level, SettlementFoodRequest request, Villager courier, long now) {
        SettlementCourierTransfers.arrived(level, request, courier, now);
    }
    public static void unloading(Villager courier) {
        var request = request(courier); if (request == null || request.journey == null || courier.isDeadOrDying()) return;
        var journey = request.journey; if (journey.presence == SettlementCourierJourney.Presence.RETIRING || journey.released()) return;
        if (journey.incarnation >= Long.MAX_VALUE - 1) { block((ServerLevel) courier.level(), request, "resident_incarnation_exhausted"); return; }
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED && journey.boatIncarnation == Long.MAX_VALUE) {
            block((ServerLevel) courier.level(), request, "boat_identity_exhausted"); return;
        }
        journey.incarnation++;
        if (journey.boatCustody == SettlementCourierJourney.BoatCustody.LAUNCHED) journey.boatIncarnation++;
        capture(journey, courier); journey.position = courier.blockPosition().immutable();
        journey.obstructed |= danger(courier);
        journey.presence = journey.obstructed ? SettlementCourierJourney.Presence.STRANDED : SettlementCourierJourney.Presence.RETIRING;
        changed((ServerLevel) courier.level(), request);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Villager courier)) return;
        var data = SettlementSavedData.get(level); if (data.readOnly()) return;
        var request = data.aid().courier(courier.getUUID()); var journey = request == null ? null : request.journey;
        boolean valid = true;
        if (journey != null && !journey.released()) {
            valid = courier.getPersistentData().hasUUID(JOURNEY) && journey.id.equals(courier.getPersistentData().getUUID(JOURNEY))
                    && courier.getPersistentData().getLong(INCARNATION) == journey.incarnation
                    && (journey.presence == SettlementCourierJourney.Presence.PHYSICAL || journey.presence == SettlementCourierJourney.Presence.MATERIALIZING
                    || journey.presence == SettlementCourierJourney.Presence.STRANDED);
        } else {
            var town = SettlementWorkforceService.home(level, courier).orElse(null);
            var resident = town == null ? null : town.residents().get(courier.getUUID());
            if (resident != null && resident.courierJourney() != null)
                valid = courier.getPersistentData().getLong(INCARNATION) >= resident.courierIncarnation();
        }
        if (!valid) { event.setCanceled(true); courier.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK); }
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof Villager courier && owns(courier)
                && courier.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK) unloading(courier);
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        var runtime = RUNTIMES.computeIfAbsent(event.getServer(), ignored -> new Runtime()); runtime.stopping = true;
        for (var request : SettlementSavedData.get(event.getServer().overworld()).aid().requests()) {
            if (request.journey == null || request.journey.released()) continue;
            var live = existing(event.getServer(), request.journey.resident());
            if (live instanceof Villager courier) unloading(courier);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        RUNTIMES.remove(event.getServer()); WAITING.entrySet().removeIf(entry -> entry.getKey().getServer() == event.getServer());
    }
}
