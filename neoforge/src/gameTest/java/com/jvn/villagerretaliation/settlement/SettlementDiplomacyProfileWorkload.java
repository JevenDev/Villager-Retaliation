package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.route.AidRouteFixture;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SettlementDiplomacyProfileWorkload {
    private final ServerLevel level;
    private final List<SettlementRecord> towns;
    private final List<UUID> routes = new ArrayList<>();
    private final List<UUID> shipments = new ArrayList<>();
    SettlementDiplomacyProfileWorkload(ServerLevel level, List<SettlementRecord> towns) {
        this.level = level; this.towns = towns;
    }
    void start() {
        for (int index = 0; index < 8; index++) create(index % 2, 4 - index % 2, index / 2 + 2);
        for (int index = 0; index < 24; index++) {
            int source = index + 5;
            int target = source + 20 < towns.size() ? source + 20 : source - 20;
            create(source, target, 2);
        }
        for (int index = 0; index < 16; index++) {
            var from = access(towns.get(index + 5)); var to = access(towns.get(index + 25));
            AidRouteFixture.flat(level, from, to);
            var route = UUID.randomUUID(); routes.add(route); DiplomaticRouteService.prepare(level, route, from, to, false);
        }
    }
    private static SettlementDepot depot(SettlementRecord town) {
        return town.depots().values().stream().filter(value -> value.purpose() == SettlementDepotPurpose.FOOD).findFirst().orElseThrow();
    }
    private static BlockPos access(SettlementRecord town) { return depot(town).position().north(); }
    private void create(int source, int destination, int ordinal) {
        var donor = towns.get(source); var recipient = towns.get(destination); var data = SettlementSavedData.get(level);
        var id = new UUID(0xB014L + source, 2L * (ordinal + 1)); var resident = donor.residents().get(id);
        if (resident == null || !data.aid().courierAvailable(id)) throw new IllegalStateException("Profile courier is unavailable");
        var entity = level.getEntity(id); var context = entity instanceof Villager villager ? villager : resident.context(level);
        var from = access(donor); var to = access(recipient);
        if (entity instanceof Villager) context.moveTo(from.getX() - 5 + ordinal + .5, from.getY(), from.getZ() + .5);
        var approach = SettlementCourierGameTests.route(context.blockPosition(), from);
        var corner = new BlockPos(to.getX() - 1, from.getY(), from.getZ());
        var steps = new ArrayList<>(SettlementCourierGameTests.route(from, corner).steps());
        var tail = SettlementCourierGameTests.route(corner, to.west()).steps();
        steps.addAll(tail.subList(1, tail.size()));
        steps.add(new com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute.Step(to, com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute.Mode.WALK));
        var crossing = new com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute(steps);
        AidRouteFixture.flat(level, context.blockPosition(), to);
        long now = level.getGameTime();
        var request = new SettlementFoodRequest(UUID.randomUUID(), recipient.id(), donor.id(), 100, now, now + 20, now + 120000, recipient.leader());
        request.donorAuthority = donor.leader(); request.maximumItems = 20;
        var cargo = new ArrayList<ItemStack>(); int remaining = 20;
        while (remaining > 0) {
            var item = depot(donor).withdraw(stack -> stack.is(Items.BREAD), remaining);
            if (item.isEmpty()) throw new IllegalStateException("Profile donor has insufficient stock");
            remaining -= item.getCount(); cargo.add(item);
        }
        request.offer = new SettlementFoodOffer(SettlementFoodOffer.Arrangement.DONOR_DELIVERY, id,
                cargo.stream().map(item -> new SettlementFoodOffer.Parcel(depot(donor).id(), item)).toList());
        request.preparation = new SettlementAidPreparation(depot(donor).id(), depot(recipient).id(), from, to, false,
                new SettlementAidPreparation.Candidate(id, context.blockPosition()), null);
        request.state = SettlementFoodRequest.State.TRAVELLING; data.aid().requests.put(request.id(), request);
        long incarnation = resident.courierIncarnation() + 1;
        context.getPersistentData().putUUID(SettlementCourierService.JOURNEY, request.id());
        context.getPersistentData().putLong(SettlementCourierService.INCARNATION, incarnation);
        var journey = new SettlementCourierJourney(request.id(), id, donor.id(), level.dimension().location(), approach, crossing,
                context.saveWithoutId(new CompoundTag()), incarnation, ItemStack.EMPTY, now);
        resident.courier(request.id(), incarnation, true); donor.changed(); data.aid().journey(request, journey); SettlementCourierService.changed(level, request);
        VillageDiplomacyService.receiveCorrespondence(level, donor.id(), recipient.id());
        VillageDiplomacyService.receiveCorrespondence(level, recipient.id(), donor.id());
        shipments.add(request.id());
    }
    void generation() {
        routes.forEach(id -> DiplomaticRouteService.forget(level, id)); routes.clear();
        var start = new BlockPos(26000000, -60, 26000000);
        for (int index = 0; index < 8; index++) {
            var id = UUID.randomUUID(); routes.add(id);
            DiplomaticRouteService.prepare(level, id, start.offset(index * 8, 0, 0), start.offset(64 + index * 8, 0, 0), false);
        }
    }
    Snapshot snapshot() {
        var data = SettlementSavedData.get(level); int physical = 0, active = 0, blocked = 0; long progress = 0, delivered = 0;
        long completed = 0;
        for (var id : shipments) {
            var request = data.aid().request(id); var journey = request.journey;
            if (journey.ownsResident()) active++;
            if (journey.ownsResident() && level.getEntity(journey.resident) instanceof Villager) physical++;
            if (request.state() == SettlementFoodRequest.State.COMPLETED) completed++;
            if (!request.blocker().isEmpty()) blocked++;
            progress += journey.step + journey.leg.ordinal() * 10000L;
            delivered += request.deliveredNutrition();
        }
        return new Snapshot(active, physical, blocked, completed, delivered, progress);
    }
    void cancelRoutes() { routes.forEach(id -> DiplomaticRouteService.cancel(level, id)); }
    record Snapshot(int active, int physical, int blocked, long completed, long delivered, long progress) { }
}
