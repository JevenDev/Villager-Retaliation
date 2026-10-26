package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticTravelMapData;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementCourierMapRequest;
import com.jvn.villagerretaliation.network.SettlementCourierMapPayload;
import com.jvn.villagerretaliation.network.SettlementCourierMapPayload.Stage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementCourierMapNetworking {
    private SettlementCourierMapNetworking() { }
    public static void handle(ServerPlayer player, SettlementCourierMapRequest request) {
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || !ServerboundRequestLimiter.tryAcquire(player, SettlementCourierMapRequest.TYPE.id(), 10)) return;
        PacketDistributor.sendToPlayer(player, snapshot(player, request));
    }
    public static SettlementCourierMapPayload snapshot(ServerPlayer player, SettlementCourierMapRequest query) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var registry = VillageAllegianceRegistrySavedData.get(level);
        var perspective = query.perspective() == null ? null : new VillageAllegianceId(query.perspective());
        var visible = VillageDiplomacyService.visible(player, perspective);
        var town = query.village() == null ? null : data.get(new VillageAllegianceId(query.village())).orElse(null);
        if (town != null && (!town.territory().active() || !visible.containsKey(town.id()) || !town.dimension().equals(level.dimension().location()))) town = null;
        var contacts = town == null ? java.util.Map.<VillageAllegianceId, com.jvn.villagerretaliation.diplomacy.VillageContact>of()
                : VillageDiplomacyService.visibleRelations(player, perspective, town.id());
        var markers = new ArrayList<SettlementCourierMapPayload.Courier>();
        SettlementFoodRequest selected = null;
        for (var request : data.aid().requests.values()) {
            var journey = request.journey; var donor = data.get(request.donor()).orElse(null); var recipient = data.get(request.requester()).orElse(null);
            var other = town == donor ? recipient : town == recipient ? donor : null;
            boolean permitted = town != null && other != null && contacts.containsKey(other.id()) && !request.terminal()
                    && (journey != null || request.state() == SettlementFoodRequest.State.PREPARING
                    || town == donor && request.state() == SettlementFoodRequest.State.OFFER_PREPARING);
            var entity = journey == null ? null : level.getEntity(journey.resident);
            boolean observed = entity instanceof net.minecraft.world.entity.npc.Villager villager && villager.isAlive()
                    && !villager.isRemoved() && player.distanceToSqr(villager) <= 64 * 64 && player.hasLineOfSight(villager);
            if (permitted) {
                var name = registry.canonicalRecord(other.id()).map(value -> value.displayName()).orElse("");
                var position = journey != null ? observed ? entity.blockPosition() : journey.position : town.center();
                var stage = journey == null ? Stage.PREPARING : stage(request);
                markers.add(new SettlementCourierMapPayload.Courier(request.id(), name.length() > 128 ? name.substring(0, 128) : name,
                        position, stage, observed, true, request.blocker()));
                if (request.id().equals(query.journey())) selected = request;
            } else if (observed) markers.add(new SettlementCourierMapPayload.Courier(request.id(), "", entity.blockPosition(), Stage.ENCOUNTER, true, false, ""));
        }
        markers.sort(Comparator.comparing(SettlementCourierMapPayload.Courier::id));
        int page = Math.min(query.page(), Math.max(0, (markers.size() - 1) / 8));
        var slice = markers.subList(page * 8, Math.min(markers.size(), page * 8 + 8));
        boolean terrainAllowed = town != null && town.permits(player.getUUID(), SettlementPermission.VIEW);
        var terrain = terrainAllowed ? DiplomaticTravelMapData.get(level).page(town.id(), query.epoch(), query.after(), level.dimension().location())
                : new DiplomaticTravelMapData.Page(0, 0, false, List.of());
        var cells = terrain.cells().stream().map(cell -> new SettlementCourierMapPayload.Cell(cell.position(), cell.color())).toList();
        List<BlockPos> route = List.of(); int key = 0, offset = 0; boolean moreRoute = false;
        if (selected != null && selected.journey != null) {
            var steps = selected.journey.route().steps(); key = steps.hashCode(); offset = Math.min(query.routeOffset(), steps.size());
            moreRoute = offset + 256 < steps.size(); route = steps.subList(offset, Math.min(offset + 256, steps.size())).stream().map(step -> step.position()).toList();
        }
        return new SettlementCourierMapPayload(query.requestId(), query.village(), page, page * 8 + 8 < markers.size(), terrainAllowed,
                terrain.epoch(), terrain.next(), terrain.more(), cells, slice, selected == null ? null : selected.id(), key, offset, moreRoute, route);
    }
    private static Stage stage(SettlementFoodRequest request) {
        var journey = request.journey;
        if (journey.repair != null) return Stage.PREPARING;
        if (!request.blocker().isEmpty() || journey.presence == SettlementCourierJourney.Presence.DEAD || journey.presence == SettlementCourierJourney.Presence.STRANDED) return Stage.BLOCKED;
        boolean delivery = request.offer.arrangement() == SettlementFoodOffer.Arrangement.DONOR_DELIVERY;
        return switch (journey.leg) {
            case APPROACH -> Stage.OUTBOUND;
            case OUTBOUND -> delivery ? Stage.DELIVERY : Stage.COLLECTION;
            case HOMEBOUND -> delivery ? Stage.RETURNING : Stage.DELIVERY;
            case RETURN_APPROACH -> Stage.RETURNING;
            case FINISHED -> Stage.COMPLETE;
        };
    }
}
