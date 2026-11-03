package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class SettlementExchangeService {
    private SettlementExchangeService() { }
    public enum Action { VIEW, PROPOSE, QUOTE, COUNTER, ACCEPT, DECLINE, CANCEL, RETRY }
    public record Result(String message, SettlementFoodRequest request) { }
    static boolean represents(ServerPlayer player, SettlementRecord town) {
        return player.isAlive() && !player.isSpectator() && VillageDiplomacyService.represents(player, town)
                && town.permits(player.getUUID(), SettlementPermission.TREASURY) && town.permits(player.getUUID(), SettlementPermission.TRADE);
    }
    public static Result act(ServerPlayer player, VillageAllegianceId village, VillageAllegianceId target, UUID id,
                             long revision, Action action, SettlementExchangeTerms terms) {
        var root = player.serverLevel(); var data = SettlementSavedData.get(root); var town = data.get(village).orElse(null);
        if (!SettlementAidService.enabled(root) || !represents(player, town)) return new Result("forbidden", null);
        var level = SettlementAidService.dimension(player.server, town);
        if (level == null) return new Result("settlement_unavailable", null);
        if (action == Action.PROPOSE || action == Action.QUOTE) {
            if (id == null || data.aid().request(id) != null) return new Result("stale", null);
            var other = data.get(target).orElse(null);
            if (terms == null || !SettlementAidService.available(level, town, other)
                    || VillageDiplomacySavedData.get(level).contact(village, target) == null
                    || !VillageDiplomacyService.visible(player, village).containsKey(target)) return new Result("settlement_unavailable", null);
            if (action == Action.QUOTE) {
                if (other.autonomous() == null) return new Result("quote_requires_npc", null);
                terms = SettlementTradeEvaluation.quote(level, town, other, terms, false);
                if (terms == null) return new Result("unpriced_item", null);
            }
            var result = create(level, town, other, terms, player.getUUID(), action == Action.QUOTE, id);
            return result;
        }
        var request = data.aid().request(id);
        if (request == null || request.trade == null || !village.equals(request.donor()) && !village.equals(request.requester()))
            return new Result("forbidden", null);
        var trade = request.trade;
        if (request.revision() != revision) return new Result("stale", request);
        boolean turn = !trade.proposer.equals(village);
        if (action == Action.CANCEL) {
            if (request.journey != null || trade.state != SettlementNegotiatedTrade.State.OFFERED && trade.state != SettlementNegotiatedTrade.State.ACCEPTED)
                return new Result("locked", request);
            if (trade.first == null) {
                trade.state = SettlementNegotiatedTrade.State.CANCELLED; request.state = SettlementFoodRequest.State.CANCELLED;
            } else { trade.state = SettlementNegotiatedTrade.State.CANCELLING; request.state = SettlementFoodRequest.State.CANCELLING; }
        } else if (action == Action.RETRY) {
            if (request.journey == null) { request.blocker = ""; changed(level, request); return new Result("applied", request); }
            return new Result(SettlementAidService.retry(player, id, revision) == SettlementAidService.Result.APPLIED ? "applied" : "unavailable", request);
        } else {
            if (!turn || trade.state != SettlementNegotiatedTrade.State.OFFERED) return new Result("stale", request);
            if (action == Action.DECLINE) {
                trade.state = SettlementNegotiatedTrade.State.DECLINED; request.state = SettlementFoodRequest.State.DECLINED;
            } else if (action == Action.COUNTER) {
                if (terms == null || trade.countersRemaining() == 0) return new Result("counter_limit", request);
                var problem = validate(level, request, terms);
                if (!problem.isEmpty()) return new Result(problem, request);
                trade.counter(terms, village); authorize(trade, request, village, player.getUUID());
                SettlementAidService.releaseRoutes(level, request);
                request.selectedRoute = null; request.participants.clear(); request.preparation = null;
                trade.transport = SettlementTradeEvaluation.evaluate(level, data.get(request.donor()).orElseThrow(),
                        data.get(request.requester()).orElseThrow(), terms, !village.equals(request.donor())).transport();
                trade.courier = null; trade.explanation = "counter_received";
            } else if (action == Action.ACCEPT) {
                authorize(trade, request, village, player.getUUID());
                String problem = accept(level, request);
                if (!problem.isEmpty()) { trade.explanation = problem; changed(level, request); return new Result(problem, request); }
            } else return new Result("unavailable", request);
        }
        changed(level, request); return new Result("applied", request);
    }
    static Result create(ServerLevel level, SettlementRecord first, SettlementRecord second, SettlementExchangeTerms terms, UUID authority, boolean quote) {
        return create(level, first, second, terms, authority, quote, UUID.randomUUID());
    }
    private static Result create(ServerLevel level, SettlementRecord first, SettlementRecord second, SettlementExchangeTerms terms, UUID authority, boolean quote, UUID id) {
        var data = SettlementSavedData.get(level); long now = level.getServer().overworld().getGameTime();
        if (data.aid().requests.values().stream().anyMatch(request -> request.trade != null && !request.terminal()
                && (request.trade.state != SettlementNegotiatedTrade.State.FAILED || request.trade.unresolved())
                && (request.donor().equals(first.id()) && request.requester().equals(second.id())
                || request.donor().equals(second.id()) && request.requester().equals(first.id()))))
            return new Result("outstanding", null);
        var request = new SettlementFoodRequest(id, second.id(), first.id(), 1, now, now, now + 168000, authority);
        request.trade = new SettlementNegotiatedTrade(terms, quote ? second.id() : first.id());
        request.state = SettlementFoodRequest.State.AWAITING_ACCEPTANCE;
        String problem = validate(level, request, terms);
        if (!problem.isEmpty()) return new Result(problem, null);
        if (!data.aid().canRequest()) return new Result("limit_reached", null);
        if (quote && second.autonomous() != null) request.trade.secondAuthority = second.leader();
        else if (!quote) request.trade.firstAuthority = authority;
        request.trade.transport = SettlementTradeEvaluation.evaluate(level, first, second, terms, false).transport();
        request.trade.explanation = quote ? "quote_received" : "offer_received";
        data.aid().requests.put(request.id(), request); changed(level, request); notify(level, request);
        return new Result("applied", request);
    }
    static String validate(ServerLevel level, SettlementFoodRequest request, SettlementExchangeTerms terms) {
        var data = SettlementSavedData.get(level); var first = data.get(request.donor()).orElse(null); var second = data.get(request.requester()).orElse(null);
        if (!SettlementAidService.available(level, first, second)) return "settlement_unavailable";
        var evaluation = SettlementTradeEvaluation.evaluate(level, first, second, terms, false);
        if (!evaluation.problem().isEmpty()) return evaluation.problem();
        for (boolean side : new boolean[]{true, false}) {
            var plan = SettlementExchangeStock.plan(level, side ? first : second, side ? terms.first() : terms.second());
            if (!plan.problem().isEmpty()) return plan.problem();
        }
        return "";
    }
    static boolean authority(SettlementRecord town, UUID authority) {
        return town != null && authority != null && (town.autonomous() != null && authority.equals(town.leader())
                || town.permits(authority, SettlementPermission.DIPLOMACY) && town.permits(authority, SettlementPermission.TREASURY) && town.permits(authority, SettlementPermission.TRADE));
    }
    static void authorize(SettlementNegotiatedTrade trade, SettlementFoodRequest request, VillageAllegianceId town, UUID authority) {
        if (request.donor().equals(town)) trade.firstAuthority = authority; else trade.secondAuthority = authority;
    }
    static String accept(ServerLevel level, SettlementFoodRequest request) {
        var trade = request.trade; var data = SettlementSavedData.get(level);
        if (trade.state != SettlementNegotiatedTrade.State.OFFERED || trade.first != null) return "stale";
        var first = data.get(request.donor()).orElse(null); var second = data.get(request.requester()).orElse(null);
        if (!authority(first, trade.firstAuthority) || !authority(second, trade.secondAuthority)) return "approval_unavailable";
        if (request.selectedRoute != null) {
            var route = data.aid().routes.get(request.selectedRoute);
            if (route == null || route.revision != trade.routeRevision) return "custom_route_unavailable";
        }
        String problem = validate(level, request, trade.terms); if (!problem.isEmpty()) return problem;
        var firstPlan = SettlementExchangeStock.plan(level, first, trade.terms.first());
        var secondPlan = SettlementExchangeStock.plan(level, second, trade.terms.second());
        if (!firstPlan.problem().isEmpty()) return firstPlan.problem();
        if (!secondPlan.problem().isEmpty()) return secondPlan.problem();
        SettlementExchangeStock.debit(first, trade.terms.first(), firstPlan);
        SettlementExchangeStock.debit(second, trade.terms.second(), secondPlan);
        trade.first = new SettlementNegotiatedTrade.Cargo(trade.terms.first()); trade.second = new SettlementNegotiatedTrade.Cargo(trade.terms.second());
        trade.state = SettlementNegotiatedTrade.State.ACCEPTED; trade.explanation = "reserved";
        request.state = SettlementFoodRequest.State.PREPARING; changed(level, request); return "";
    }
    static void changed(ServerLevel level, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level); data.aid().changed(request); data.setDirty();
        if (request.journey != null) request.journey.revision = request.revision();
    }
    static void step(ServerLevel level, SettlementFoodRequest request, long now) {
        var data = SettlementSavedData.get(level); var trade = request.trade;
        if (request.journey != null && request.journey.presence == SettlementCourierJourney.Presence.DEAD) {
            SettlementExchangeTravel.refundReserved(level, request); return;
        }
        if (trade.state == SettlementNegotiatedTrade.State.CANCELLING) {
            if (SettlementExchangeTravel.refundReserved(level, request)) {
                trade.state = SettlementNegotiatedTrade.State.CANCELLED; request.state = SettlementFoodRequest.State.CANCELLED;
                SettlementAidService.releaseRoutes(level, request); changed(level, request);
            }
            return;
        }
        if (trade.state == SettlementNegotiatedTrade.State.OFFERED && now >= request.expires()) {
            trade.state = SettlementNegotiatedTrade.State.CANCELLED; request.state = SettlementFoodRequest.State.EXPIRED;
            changed(level, request); return;
        }
        if (trade.state == SettlementNegotiatedTrade.State.ACCEPTED && request.journey == null) {
            SettlementExchangeTravel.prepare(level, request, now); return;
        }
        if (trade.state != SettlementNegotiatedTrade.State.OFFERED) return;
        var receiverId = trade.proposer.equals(request.donor()) ? request.requester() : request.donor();
        var receiver = data.get(receiverId).orElse(null);
        if (receiver == null || receiver.autonomous() == null) return;
        var first = data.get(request.donor()).orElse(null); var second = data.get(request.requester()).orElse(null);
        if (!SettlementAidService.available(level, first, second)) return;
        var evaluation = SettlementTradeEvaluation.evaluate(level, first, second, trade.terms, receiverId.equals(request.donor()));
        trade.transport = evaluation.transport();
        if (evaluation.acceptable()) {
            authorize(trade, request, receiverId, receiver.leader());
            String problem = accept(level, request);
            if (!problem.isEmpty()) {
                trade.state = SettlementNegotiatedTrade.State.DECLINED; request.state = SettlementFoodRequest.State.DECLINED;
                trade.explanation = problem;
            }
        } else if (trade.countersRemaining() > 0 && evaluation.problem().isEmpty()) {
            var terms = SettlementTradeEvaluation.quote(level, first, second, trade.terms, receiverId.equals(request.donor()));
            String problem = terms == null ? "unpriced_item" : validate(level, request, terms);
            if (problem.isEmpty() && trade.counter(terms, receiverId)) {
                authorize(trade, request, receiverId, receiver.leader()); trade.explanation = "value_too_low";
            } else { trade.state = SettlementNegotiatedTrade.State.DECLINED; request.state = SettlementFoodRequest.State.DECLINED; trade.explanation = problem; }
        } else {
            trade.state = SettlementNegotiatedTrade.State.DECLINED; request.state = SettlementFoodRequest.State.DECLINED;
            trade.explanation = evaluation.problem().isEmpty() ? "value_too_low" : evaluation.problem();
        }
        changed(level, request); notify(level, request);
    }
    static void observe(ServerLevel level, SettlementRecord town, long now) {
        var data = SettlementSavedData.get(level);
        var clock = data.aid().town(town);
        if (now < clock.nextTrade) return;
        clock.nextTrade = now + 72000; data.aid().changed(); data.setDirty();
        if (town.autonomous() == null || data.aid().requests.values().stream().anyMatch(request -> request.trade != null
                && (request.donor().equals(town.id()) || request.requester().equals(town.id())) && now - request.created() < 72000)) return;
        var needs = SettlementExchangeStock.demands(level, town);
        for (var demand : needs.demands().stream().limit(8).toList()) {
            if (demand.missing() <= 0) continue;
            for (var sample : demand.requirement().alternatives().stream().limit(4).toList()) {
                if (com.jvn.villagerretaliation.sell.SellPriceResources.definition(level.getServer(), sample).isEmpty()) continue;
                int count = (int) Math.min(sample.getMaxStackSize(), Math.max(1, demand.missing() / Math.max(1, demand.requirement().value(sample))));
                for (var id : VillageDiplomacySavedData.get(level).contacts(town.id()).keySet().stream().limit(8).toList()) {
                    var other = data.get(id).orElse(null);
                    if (other == null || other.autonomous() != null || !SettlementAidService.available(level, town, other)) continue;
                    var terms = new SettlementExchangeTerms(new SettlementExchangeTerms.Bundle(List.of(), com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO),
                            new SettlementExchangeTerms.Bundle(List.of(sample.copyWithCount(count)), com.jvn.villagerretaliation.sell.CurrencyAmount.ZERO), true);
                    terms = SettlementTradeEvaluation.quote(level, town, other, terms, false);
                    if (terms != null && create(level, town, other, terms, town.leader(), false).request() != null) return;
                }
            }
        }
    }
    static void notify(ServerLevel level, SettlementFoodRequest request) {
        var data = SettlementSavedData.get(level);
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            var first = data.get(request.donor()).orElse(null); var second = data.get(request.requester()).orElse(null);
            if (VillageDiplomacyService.represents(player, first) || VillageDiplomacyService.represents(player, second))
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.exchange.notification"), false);
        }
    }
}
