package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRoute;
import com.jvn.villagerretaliation.diplomacy.route.DiplomaticRouteService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementCaravanService {
    private SettlementCaravanService() { }
    static boolean matches(SettlementRoute route, SettlementFoodRequest request) {
        return route.origin.equals(request.donor()) && route.destination.equals(request.requester())
                || route.destination.equals(request.donor()) && route.origin.equals(request.requester());
    }
    static boolean guard(Villager villager) {
        var item = villager.getMainHandItem().getItem();
        return item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.AxeItem
                || item instanceof net.minecraft.world.item.BowItem || item instanceof net.minecraft.world.item.CrossbowItem;
    }
    static String configure(ServerPlayer player, UUID id, long revision, UUID routeId, Map<UUID, Boolean> members) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var request = data.aid().request(id);
        if (request == null || request.purpose != SettlementFoodRequest.Purpose.SUPPLY || request.journey != null || request.offer == null
                || request.state() != SettlementFoodRequest.State.AWAITING_ACCEPTANCE && request.state() != SettlementFoodRequest.State.OFFER_SENT
                || request.revision() != revision || members.size() > 7 || data.readOnly()) return "Refresh the offer before configuring departure";
        var home = data.get(request.offer.arrangement() == SettlementFoodOffer.Arrangement.DONOR_DELIVERY ? request.donor() : request.requester()).orElse(null);
        if (home == null || !home.permits(player.getUUID(), SettlementPermission.DIPLOMACY) || !home.permits(player.getUUID(), SettlementPermission.STAFF)) return "Only the sending settlement can assign travelers";
        var route = routeId == null ? null : data.aid().routes.get(routeId);
        if (routeId != null && (route == null || !matches(route, request))) return "Choose a route between these settlements";
        for (var member : members.entrySet()) {
            var resident = home.residents().get(member.getKey());
            if (member.getKey().equals(request.offer.courier()) || !SettlementAidService.eligible(level, home, resident, request.id(), true)) return "A selected villager is unavailable";
            var context = resident.context(level);
            if (member.getValue() && (context == null || !guard(context))) return "Equip guards with a weapon before assigning them";
        }
        request.selectedRoute = routeId; request.participants.clear(); request.participants.putAll(members);
        data.aid().changed(request); data.setDirty(); return "Departure configured. Accept the food offer to send the journey";
    }
    static List<SettlementFoodRequest> prepare(ServerLevel level, SettlementFoodRequest leader, SettlementRecord home, DiplomaticRoute crossing, long now) {
        var data = SettlementSavedData.get(level); var result = new ArrayList<SettlementFoodRequest>(); var preparedJobs = new ArrayList<UUID>();
        if (leader.participants.isEmpty()) return result;
        if (crossing.needsBoat()) { SettlementAidService.transition(data, leader, leader.state(), "caravan_land_route_required"); return null; }
        long active = data.aid().requests.values().stream().filter(value -> value.journey != null && value.journey.ownsResident()).count();
        if (data.aid().requests.size() + leader.participants.size() > SettlementAidLedger.REQUEST_LIMIT
                || active + leader.participants.size() + 1 > com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules.get(level.getServer()).activeJourneys()) {
            SettlementAidService.transition(data, leader, leader.state(), "journey_queue_full"); return null;
        }
        for (var entry : leader.participants.entrySet()) {
            var resident = home.residents().get(entry.getKey()); var live = SettlementCourierService.existing(level.getServer(), entry.getKey());
            if (!SettlementAidService.eligible(level, home, resident, leader.id(), true) || resident.courierIncarnation() >= Long.MAX_VALUE - 1) {
                SettlementAidService.transition(data, leader, leader.state(), "caravan_member_unavailable"); return null;
            }
            var context = live instanceof Villager villager ? villager : resident.context(level);
            if (context == null || entry.getValue() && !guard(context)) { SettlementAidService.transition(data, leader, leader.state(), "caravan_member_unavailable"); return null; }
            var id = UUID.nameUUIDFromBytes((leader.id() + ":member:" + entry.getKey()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var approachId = UUID.nameUUIDFromBytes((id + ":" + context.blockPosition()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var preparation = DiplomaticRouteService.prepare(level, approachId, context.blockPosition(), crossing.steps().getFirst().position(), false);
            var approach = DiplomaticRouteService.ready(level, approachId);
            if (approach == null) { SettlementAidService.transition(data, leader, leader.state(), preparation.blocker().isEmpty() ? "preparing_caravan" : preparation.blocker()); return null; }
            var member = new SettlementFoodRequest(id, leader.requester(), leader.donor(), 1, leader.created(), leader.created(), leader.expires(), leader.authority());
            member.purpose = entry.getValue() ? SettlementFoodRequest.Purpose.GUARD : SettlementFoodRequest.Purpose.COMPANION;
            member.caravan = leader.id(); member.state = SettlementFoodRequest.State.TRAVELLING;
            member.journey = new SettlementCourierJourney(id, resident.id(), home.id(), level.dimension().location(), approach, crossing,
                    context.saveWithoutId(new CompoundTag()), resident.courierIncarnation() + 1, ItemStack.EMPTY, now);
            var route = leader.selectedRoute == null ? null : data.aid().routes.get(leader.selectedRoute);
            if (route != null) member.journey.select(route, !route.origin.equals(home.id()));
            result.add(member);
            preparedJobs.add(approachId);
        }
        preparedJobs.forEach(id -> DiplomaticRouteService.forget(level, id));
        return result;
    }
    static void claim(ServerLevel level, SettlementFoodRequest leader, SettlementRecord home, List<SettlementFoodRequest> members) {
        var data = SettlementSavedData.get(level);
        for (var member : members) {
            var journey = member.journey; member.journey = null;
            data.aid().requests.put(member.id(), member); data.aid().journey(member, journey);
            var resident = home.residents().get(journey.resident); resident.courier(member.id(), journey.incarnation, true);
            var live = SettlementCourierService.existing(level.getServer(), journey.resident);
            if (live instanceof Villager villager) {
                villager.getPersistentData().putUUID(SettlementCourierService.JOURNEY, journey.id);
                villager.getPersistentData().putLong(SettlementCourierService.INCARNATION, journey.incarnation);
                villager.getNavigation().stop(); SettlementCourierService.capture(journey, villager);
            }
            SettlementCourierService.changed(level, member);
        }
        home.changed(); data.setDirty();
    }
    static List<SettlementFoodRequest> group(ServerLevel level, SettlementFoodRequest member) {
        var aid = SettlementSavedData.get(level).aid(); UUID id = member.caravan == null ? member.id() : member.caravan;
        return aid.requests.values().stream().filter(request -> request.journey != null && (request.id().equals(id) || id.equals(request.caravan))).toList();
    }
    static String recall(ServerPlayer player, UUID id, long revision) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var request = data.aid().request(id);
        if (request == null || request.journey == null || request.revision() != revision || data.readOnly()) return "Journey changed, refresh first";
        var home = data.get(request.journey.home).orElse(null);
        if (home == null || !home.permits(player.getUUID(), SettlementPermission.DIPLOMACY)) return "Only the sending settlement can recall a caravan";
        var group = group(level, request);
        if (group.stream().noneMatch(member -> member.journey.ownsResident())) return "No travelers remain to recall";
        if (group.stream().anyMatch(member -> member.journey.ownsResident() && (member.journey.crossing.needsBoat()
                || member.journey.repairs >= com.jvn.villagerretaliation.diplomacy.VillageDiplomacyRules.get(level.getServer()).routeRetries())))
            return "Recall needs an available land return route";
        for (var member : group) {
            member.recalled = true; var journey = member.journey;
            if (journey.ownsResident() && journey.leg.ordinal() < SettlementCourierJourney.Leg.HOMEBOUND.ordinal()) {
                journey.leg = SettlementCourierJourney.Leg.HOMEBOUND; journey.step = 0; journey.detour = null;
                if (!SettlementCourierService.retry(level, member)) {
                    journey.obstructed = true; SettlementCourierService.block(level, member, "caravan_return_blocked");
                }
                member.state = SettlementFoodRequest.State.RETURNING;
            }
            SettlementCourierService.changed(level, member);
        }
        return "Survivors are returning. Cargo remains with its leader until safely unloaded";
    }
    static boolean ready(ServerLevel level, SettlementFoodRequest member) {
        if (member.recalled || member.caravan == null && member.participants.isEmpty()) return true;
        var journey = member.journey; String reason = "";
        var group = group(level, member);
        var leader = SettlementSavedData.get(level).aid().request(member.caravan == null ? member.id() : member.caravan);
        if (leader == null || group.size() != leader.participants.size() + 1) reason = "caravan_member_missing";
        for (var other : group) {
            if (other == member) continue; var companion = other.journey;
            if (companion.presence == SettlementCourierJourney.Presence.DEAD && !companion.remains.isEmpty()) continue;
            if (companion.presence == SettlementCourierJourney.Presence.DEAD || companion.presence == SettlementCourierJourney.Presence.STRANDED
                    || !companion.blocker.isEmpty() && !companion.blocker.startsWith("caravan_")) { reason = "caravan_member_blocked"; break; }
            if (companion.leg.ordinal() < journey.leg.ordinal() || (journey.leg == SettlementCourierJourney.Leg.OUTBOUND || journey.leg == SettlementCourierJourney.Leg.HOMEBOUND)
                    && companion.leg == journey.leg && companion.step + 4 < journey.step) reason = "caravan_waiting";
        }
        if (!reason.isEmpty()) {
            if (journey.blocker.isEmpty() || journey.blocker.startsWith("caravan_")) SettlementCourierService.block(level, member, reason);
            return false;
        }
        if (journey.blocker.startsWith("caravan_")) SettlementCourierService.block(level, member, "");
        return true;
    }
    static boolean readyForNextLeg(ServerLevel level, SettlementFoodRequest member) {
        if (member.recalled || member.caravan == null && member.participants.isEmpty()) return true;
        var journey = member.journey;
        for (var other : group(level, member)) {
            if (other == member) continue;
            if (other.journey.presence == SettlementCourierJourney.Presence.DEAD && !other.journey.remains.isEmpty()) continue;
            if (other.journey.presence == SettlementCourierJourney.Presence.DEAD || other.journey.presence == SettlementCourierJourney.Presence.STRANDED) {
                SettlementCourierService.block(level, member, "caravan_member_blocked"); return false;
            }
            if (other.journey.leg.ordinal() < journey.leg.ordinal() || other.journey.leg == journey.leg && !other.journey.atEnd()) {
                SettlementCourierService.block(level, member, "caravan_waiting"); return false;
            }
        }
        if (member.caravan != null && journey.leg == SettlementCourierJourney.Leg.OUTBOUND) {
            var leader = SettlementSavedData.get(level).aid().request(member.caravan);
            if (leader == null || leader.journey.leg.ordinal() <= journey.leg.ordinal()) { SettlementCourierService.block(level, member, "caravan_waiting"); return false; }
        }
        return true;
    }
}
