package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementAidPayload;
import com.jvn.villagerretaliation.network.SettlementAidRequestPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementAidNetworking {
    private SettlementAidNetworking() { }
    public static void handle(ServerPlayer player, SettlementAidRequestPayload request) {
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || !ServerboundRequestLimiter.tryAcquire(player, request.action() == SettlementAidRequestPayload.Action.VIEW
                        ? SettlementAidRequestPayload.TYPE.id() : VillagerRetaliation.id("settlement_aid_action"), 5L)) return;
        var village = new VillageAllegianceId(request.village()); var policy = request.policy();
        var result = switch (request.action()) {
            case VIEW -> SettlementAidService.Result.APPLIED;
            case REQUEST -> SettlementAidService.request(player, village, new VillageAllegianceId(request.target()), request.nutrition(), request.revision());
            case APPROVE -> SettlementAidService.approve(player, request.request(), request.revision(), policy.maximumItems());
            case ACCEPT -> SettlementAidService.accept(player, request.request(), request.revision());
            case DECLINE -> SettlementAidService.decline(player, request.request(), request.revision());
            case CANCEL -> SettlementAidService.cancel(player, request.request(), request.revision());
            case RETRY -> SettlementAidService.retry(player, request.request(), request.revision());
            case POLICY -> SettlementAidService.policy(player, village, request.revision(), policy.automatic(), policy.reserveDays(),
                    policy.maximumItems(), policy.minimumStanding(), policy.intervalTicks());
        };
        PacketDistributor.sendToPlayer(player, snapshot(player, request, result));
    }

    public static SettlementAidPayload snapshot(ServerPlayer player, SettlementAidRequestPayload request, SettlementAidService.Result result) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var aid = data.aid();
        var village = new VillageAllegianceId(request.village()); var town = data.get(village).orElse(null);
        long time = player.server.overworld().getGameTime();
        if (town == null || !town.permits(player.getUUID(), SettlementPermission.VIEW))
            return new SettlementAidPayload(request.requestId(), request.village(), "", 0,
                    new SettlementAidRequestPayload.Policy(false, 3, 128, 0, 72000), false, 0, 0, time, 0,
                    SettlementAidService.Result.FORBIDDEN, "", List.of());
        var visible = VillageDiplomacyService.visible(player, town.id()); var registry = VillageAllegianceRegistrySavedData.get(level);
        var rows = new ArrayList<SettlementAidPayload.Entry>();
        for (var value : aid.requests.values()) {
            boolean donating = data.get(value.donor()).orElse(null) == town;
            if (!donating && data.get(value.requester()).orElse(null) != town || donating && value.state() == SettlementFoodRequest.State.REQUEST_SENT) continue;
            var other = registry.canonicalRecord(donating ? value.requester() : value.donor()).orElse(null);
            if (other == null || !visible.containsKey(other.id())) continue;
            var state = visibleState(data, town, value);
            boolean concealed = state != value.state();
            boolean showOffer = value.offer != null && state != SettlementFoodRequest.State.REQUEST_SENT;
            rows.add(new SettlementAidPayload.Entry(value.id(), other.id().value(), trim(other.displayName()), donating, value.revision(), state,
                    value.nutrition(), showOffer ? value.offer.nutrition() : 0, showOffer ? value.deliveredNutrition() : 0,
                    showOffer ? value.offer.arrangement() : null, value.created(), concealed ? 0 : value.arrives(), concealed ? "" : value.blocker()));
        }
        rows.sort(Comparator.comparingLong(SettlementAidPayload.Entry::date).reversed().thenComparing(SettlementAidPayload.Entry::id));
        int page = Math.min(request.page(), Math.max(0, (rows.size() - 1) / SettlementAidPayload.PAGE_SIZE));
        var policy = aid.policy(town);
        String blocker = data.readOnly() ? "read_only" : !SettlementAidService.enabled(level) ? "disabled"
                : policy.automatic() && !town.permits(policy.authority(), SettlementPermission.DIPLOMACY) ? "approval_unavailable" : "";
        return new SettlementAidPayload(request.requestId(), request.village(), trim(registry.canonicalRecord(town.id()).map(record -> record.displayName()).orElse("")),
                aid.policyRevision(town), new SettlementAidRequestPayload.Policy(policy.automatic(), policy.reserveDays(), policy.maximumItems(), policy.minimumStanding(), policy.intervalTicks()),
                VillageDiplomacyService.represents(player, town), page, rows.size(), time, SettlementAidService.need(level, town), result, blocker,
                rows.subList(page * SettlementAidPayload.PAGE_SIZE, Math.min(rows.size(), (page + 1) * SettlementAidPayload.PAGE_SIZE)));
    }

    private static SettlementFoodRequest.State visibleState(SettlementSavedData data, SettlementRecord town, SettlementFoodRequest request) {
        boolean donating = data.get(request.donor()).orElse(null) == town;
        return switch (request.state()) {
            case AWAITING_OFFER, OFFER_PREPARING, OFFER_SENT -> donating ? request.state() : SettlementFoodRequest.State.REQUEST_SENT;
            case ACCEPTANCE_SENT -> donating ? SettlementFoodRequest.State.AWAITING_ACCEPTANCE : request.state();
            case DECLINE_SENT, CANCELLING -> data.get(request.messageSender).orElse(null) == town ? request.state()
                    : donating ? SettlementFoodRequest.State.AWAITING_ACCEPTANCE : SettlementFoodRequest.State.REQUEST_SENT;
            default -> request.state();
        };
    }
    private static String trim(String value) { return value.length() > 64 ? value.substring(0, 64) : value; }
}
