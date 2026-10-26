package com.jvn.villagerretaliation.settlement;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class SettlementCourierTransfers {
    private SettlementCourierTransfers() { }
    static void arrived(ServerLevel level, SettlementFoodRequest request, Villager courier, long now) {
        var data = SettlementSavedData.get(level); var journey = request.journey;
        var home = data.get(journey.home()).orElse(null);
        if (home == null || !home.territory().active()) { SettlementCourierService.block(level, request, "settlement_unavailable"); return; }
        if (!SettlementAidCheckpoint.durable(level, journey.revision)) return;
        if (courier != null && !journey.finished() && !SettlementCourierService.nativeDurable(level, journey, courier,
                SettlementCourierService.runtime(level.getServer()), now)) return;
        boolean delivery = request.offer.arrangement() == SettlementFoodOffer.Arrangement.DONOR_DELIVERY;
        boolean pickup = delivery && journey.leg == SettlementCourierJourney.Leg.APPROACH
                || !delivery && journey.leg == SettlementCourierJourney.Leg.OUTBOUND;
        boolean unload = delivery && journey.leg == SettlementCourierJourney.Leg.OUTBOUND
                || !delivery && journey.leg == SettlementCourierJourney.Leg.HOMEBOUND;
        if (pickup && request.offer.custody() == SettlementFoodOffer.Custody.RESERVED) {
            var donor = data.get(request.donor()).orElse(null);
            if (donor == null || !donor.territory().active() || donor.depots().get(request.preparation.donorDepot) == null
                    || donor.depots().get(request.preparation.donorDepot).position().distSqr(journey.position) > 12) {
                SettlementCourierService.block(level, request, "depot_unavailable"); return;
            }
            request.offer.custody = SettlementFoodOffer.Custody.COURIER; SettlementCourierService.changed(level, request); return;
        }
        if (unload && !request.offer.empty()) {
            var recipient = data.get(request.requester()).orElse(null);
            if (recipient == null || !recipient.territory().active()) { SettlementCourierService.block(level, request, "settlement_unavailable"); return; }
            var depot = recipient.depots().get(request.preparation.recipientDepot);
            if (depot == null || depot.position().distSqr(journey.position) > 12) { SettlementCourierService.block(level, request, "depot_unavailable"); return; }
            SettlementAidStock.deliver(level, recipient, request, request.preparation.recipientDepot);
            if (!request.offer.empty()) { SettlementCourierService.block(level, request, "destination_storage_full"); return; }
            SettlementCourierService.changed(level, request); return;
        }
        if (journey.leg == SettlementCourierJourney.Leg.HOMEBOUND
                && journey.boatCustody != SettlementCourierJourney.BoatCustody.NONE && journey.boatCustody != SettlementCourierJourney.BoatCustody.RETURNED) {
            if (journey.boatCustody != SettlementCourierJourney.BoatCustody.PACKED) { SettlementCourierService.block(level, request, "boat_lost"); return; }
            var depot = delivery ? request.preparation.donorDepot : request.preparation.recipientDepot;
            var destination = home.depots().get(depot);
            if (destination == null || destination.position().distSqr(journey.position) > 12) { SettlementCourierService.block(level, request, "depot_unavailable"); return; }
            if (!SettlementAidStock.depositAt(level, home, journey.boat, depot).isEmpty()) {
                SettlementCourierService.block(level, request, "boat_return_storage_full"); return;
            }
            journey.boatCustody = SettlementCourierJourney.BoatCustody.RETURNED; SettlementCourierService.changed(level, request); return;
        }
        if (journey.finished()) {
            var resident = home.residents().get(journey.resident);
            if (resident == null) { SettlementCourierService.block(level, request, "resident_context_unavailable"); return; }
            if (courier == null) {
                var context = resident.context(level); if (context == null) return;
                context.load(journey.residentContext.copy());
                context.moveTo(journey.position.getX() + 0.5, journey.position.getY(), journey.position.getZ() + 0.5, context.getYRot(), context.getXRot());
                context.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
                context.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation);
                resident.courier(journey.id, journey.incarnation, false); resident.observe(context, home, now); home.changed();
                journey.residentContext = context.saveWithoutId(new CompoundTag());
                if (request.state() != SettlementFoodRequest.State.COMPLETED) {
                    SettlementAidService.transition(data, request, SettlementFoodRequest.State.COMPLETED, "");
                    SettlementCourierService.changed(level, request); SettlementAidService.releaseRoutes(level, request);
                }
                return;
            }
            var runtime = SettlementCourierService.runtime(level.getServer());
            if (!SettlementCourierService.nativeDurable(level, journey, courier, runtime, now)) return;
            resident.courier(journey.id, journey.incarnation, false); resident.observe(courier, home, now); home.changed();
            journey.presence = SettlementCourierJourney.Presence.RELEASED;
            if (journey.interference.values().stream().allMatch(incident -> incident.applied)) data.aid().couriers.remove(journey.resident);
            SettlementCourierService.changed(level, request);
            SettlementAidService.transition(data, request, SettlementFoodRequest.State.COMPLETED, "");
            SettlementAidService.releaseRoutes(level, request); return;
        }
        journey.nextLeg(now); SettlementCourierService.block(level, request, ""); SettlementCourierService.changed(level, request);
        if (journey.leg == SettlementCourierJourney.Leg.HOMEBOUND || journey.leg == SettlementCourierJourney.Leg.RETURN_APPROACH)
            SettlementAidService.transition(data, request, SettlementFoodRequest.State.RETURNING, "");
    }
}
