package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

final class SettlementExchangeTravel {
    private SettlementExchangeTravel() { }
    static void prepare(ServerLevel level, SettlementFoodRequest request, long now) {
        var data = SettlementSavedData.get(level); var trade = request.trade;
        var first = data.get(request.donor()).orElse(null); var second = data.get(request.requester()).orElse(null);
        if (!SettlementAidService.available(level, first, second)) { block(level, request, "settlement_unavailable"); return; }
        if (!SettlementExchangeService.authority(first, trade.firstAuthority) || !SettlementExchangeService.authority(second, trade.secondAuthority)) {
            block(level, request, "approval_unavailable"); return;
        }
        if (request.selectedRoute != null && (data.aid().routes.get(request.selectedRoute) == null
                || data.aid().routes.get(request.selectedRoute).revision != trade.routeRevision)) { block(level, request, "custom_route_unavailable"); return; }
        if (request.preparation == null) {
            var route = request.selectedRoute == null ? null : data.aid().routes.get(request.selectedRoute);
            var from = SettlementAidStock.depots(first, true).stream().filter(SettlementDepot::rulesObserved)
                    .filter(depot -> route == null || depot.position().distSqr((route.origin.equals(first.id()) ? route.path.steps().getFirst() : route.path.steps().getLast()).position()) <= 12).findFirst().orElse(null);
            var to = SettlementAidStock.depots(second, true).stream().filter(SettlementDepot::rulesObserved)
                    .filter(depot -> route == null || depot.position().distSqr((route.origin.equals(second.id()) ? route.path.steps().getFirst() : route.path.steps().getLast()).position()) <= 12).findFirst().orElse(null);
            if (from == null || to == null) { block(level, request, "depot_unavailable"); return; }
            var home = trade.terms.firstSends() ? first : second; var depot = trade.terms.firstSends() ? from : to;
            var courier = home.residents().values().stream().filter(resident -> !request.participants.containsKey(resident.id())
                    && SettlementAidService.eligible(level, home, resident, request.id())).findFirst().orElse(null);
            if (courier == null) { block(level, request, "courier_unavailable"); return; }
            var candidate = new SettlementAidPreparation.Candidate(courier.id(), courier.position());
            candidate.access = depot.position(); candidate.approach = UUID.randomUUID(); trade.courier = courier.id();
            request.preparation = new SettlementAidPreparation(from.id(), to.id(), from.position(), to.position(),
                    !SettlementAidService.boat(level, home, depot).isEmpty(), trade.terms.firstSends() ? candidate : null, trade.terms.firstSends() ? null : candidate);
            SettlementExchangeService.changed(level, request);
        }
        if (request.selectedRoute == null) {
            var plan = request.preparation;
            DiplomaticRouteService.prepare(level, plan.crossing, plan.from, plan.to, plan.boatAllowed);
            if (DiplomaticRouteService.ready(level, plan.crossing) == null) { block(level, request, "preparing_route"); return; }
        }
        SettlementCourierService.begin(level, request, now);
    }
    static void block(ServerLevel level, SettlementFoodRequest request, String reason) {
        SettlementAidService.transition(SettlementSavedData.get(level), request, request.state(), reason);
    }
    static boolean arrived(ServerLevel level, SettlementFoodRequest request) {
        var trade = request.trade; var journey = request.journey;
        var data = SettlementSavedData.get(level);
        boolean firstSends = trade.terms.firstSends();
        var outgoing = firstSends ? trade.first : trade.second; var incoming = firstSends ? trade.second : trade.first;
        var home = data.get(firstSends ? request.donor() : request.requester()).orElse(null);
        var away = data.get(firstSends ? request.requester() : request.donor()).orElse(null);
        if (home == null || away == null || !home.territory().active() || !away.territory().active()) {
            SettlementCourierService.block(level, request, "settlement_unavailable"); return true;
        }
        if (journey.leg == SettlementCourierJourney.Leg.APPROACH && outgoing.custody == SettlementNegotiatedTrade.Custody.RESERVED) {
            outgoing.custody = SettlementNegotiatedTrade.Custody.ONBOARD; SettlementExchangeService.changed(level, request); return true;
        }
        if (journey.leg == SettlementCourierJourney.Leg.OUTBOUND && !request.recalled) {
            var depot = firstSends ? request.preparation.recipientDepot : request.preparation.donorDepot;
            if (outgoing.custody == SettlementNegotiatedTrade.Custody.ONBOARD) {
                if (!atDepot(away, depot, journey)) { SettlementCourierService.block(level, request, "depot_unavailable"); return true; }
                SettlementExchangeStock.unload(level, away, outgoing, depot, false);
                SettlementExchangeService.changed(level, request);
                if (!outgoing.empty()) SettlementCourierService.block(level, request, "destination_storage_full");
                return true;
            }
            if (incoming.custody == SettlementNegotiatedTrade.Custody.RESERVED) {
                incoming.custody = SettlementNegotiatedTrade.Custody.ONBOARD; SettlementExchangeService.changed(level, request); return true;
            }
        }
        if (journey.leg == SettlementCourierJourney.Leg.HOMEBOUND) {
            var depot = firstSends ? request.preparation.donorDepot : request.preparation.recipientDepot;
            if (!atDepot(home, depot, journey)) { SettlementCourierService.block(level, request, "depot_unavailable"); return true; }
            if (!outgoing.empty()) {
                SettlementExchangeStock.unload(level, home, outgoing, depot, true); SettlementExchangeService.changed(level, request);
                if (!outgoing.empty()) SettlementCourierService.block(level, request, "refund_storage_full");
                return true;
            }
            if (incoming.custody == SettlementNegotiatedTrade.Custody.RESERVED && request.recalled) {
                if (!refundReserved(level, request)) return true;
            } else if (!incoming.empty()) {
                SettlementExchangeStock.unload(level, home, incoming, depot, false); SettlementExchangeService.changed(level, request);
                if (!incoming.empty()) SettlementCourierService.block(level, request, "destination_storage_full");
                return true;
            }
        }
        if (journey.finished() && trade.state == SettlementNegotiatedTrade.State.ACCEPTED) {
            if (trade.unresolved()) { SettlementCourierService.block(level, request, "unresolved_cargo"); return true; }
            boolean returned = !trade.first.returned.isEmpty() || !trade.second.returned.isEmpty()
                    || !trade.first.returnedFunds.isZero() || !trade.second.returnedFunds.isZero();
            trade.state = trade.loss() ? SettlementNegotiatedTrade.State.PARTIAL
                    : returned ? SettlementNegotiatedTrade.State.CANCELLED : SettlementNegotiatedTrade.State.COMPLETED;
            trade.explanation = trade.loss() ? "loss_recorded" : returned ? "returned" : "completed";
            SettlementExchangeService.changed(level, request);
        }
        return false;
    }
    private static boolean atDepot(SettlementRecord town, UUID depot, SettlementCourierJourney journey) {
        return town.depots().containsKey(depot) && town.depots().get(depot).position().distSqr(journey.position) <= 12;
    }
    static boolean refundReserved(ServerLevel level, SettlementFoodRequest request) {
        var trade = request.trade; var data = SettlementSavedData.get(level); boolean complete = true; String problem = "refund_storage_full";
        for (boolean side : new boolean[]{true, false}) {
            var cargo = side ? trade.first : trade.second;
            if (cargo == null || cargo.custody != SettlementNegotiatedTrade.Custody.RESERVED || cargo.empty()) continue;
            var town = data.get(side ? request.donor() : request.requester()).orElse(null);
            if (town == null || !town.territory().active()) { complete = false; problem = "settlement_unavailable"; continue; }
            SettlementExchangeStock.unload(level, town, cargo, null, true); complete &= cargo.empty();
            SettlementExchangeService.changed(level, request);
        }
        if (!complete) block(level, request, problem); return complete;
    }
    static void lose(ServerLevel level, SettlementFoodRequest request, SettlementEncounterState encounter, boolean death) {
        var cargo = request.trade.onboard();
        if (cargo != null) {
            var survivors = new ArrayList<ItemStack>();
            var random = new java.util.Random(request.id().getMostSignificantBits() ^ (encounter == null ? 0 : encounter.id.getLeastSignificantBits()));
            for (var item : cargo.remaining) {
                int count = death ? item.getCount() : SettlementEncounterRecovery.protectedItem(item) ? 0 : (int) Math.floor(item.getCount() * encounter.definition.cargoLoss());
                if (count > 0) {
                    var lost = item.copyWithCount(count); cargo.lost.add(lost);
                    if (death) {
                        if (encounter == null) request.journey.recovery.add(new SettlementCourierRecovery.Drop(UUID.randomUUID(), lost));
                        else if (SettlementEncounterRecovery.recover(lost, encounter.definition.recovery(), random))
                            SettlementEncounterRecovery.remains(request, encounter).items.add(lost);
                    }
                }
                if (count < item.getCount()) survivors.add(item.copyWithCount(item.getCount() - count));
            }
            cargo.remaining.clear(); cargo.remaining.addAll(survivors);
            if (death) { cargo.lostFunds = cargo.lostFunds.add(cargo.funds); cargo.funds = CurrencyAmount.ZERO; cargo.custody = SettlementNegotiatedTrade.Custody.CLOSED; }
        }
        if (request.trade.loss()) request.trade.explanation = "loss_recorded";
        if (death) { request.trade.state = SettlementNegotiatedTrade.State.FAILED; request.trade.explanation = "loss_recorded"; }
        SettlementExchangeService.changed(level, request);
    }
}
