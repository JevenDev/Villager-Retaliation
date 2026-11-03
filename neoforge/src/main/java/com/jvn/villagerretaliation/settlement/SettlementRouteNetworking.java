package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.network.SettlementRouteRequest;
import com.jvn.villagerretaliation.network.SettlementRoutesPayload;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementRouteNetworking {
    private SettlementRouteNetworking() { }
    public static void handle(ServerPlayer player, SettlementRouteRequest query) {
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || !com.jvn.villagerretaliation.network.ServerboundRequestLimiter.tryAcquire(player, SettlementRouteRequest.TYPE.id(), 5)) return;
        if (query.action() == SettlementRouteRequest.Action.CLOSE) { SettlementRouteService.closePreview(player); return; }
        var data = SettlementSavedData.get(player.serverLevel()); var route = data.aid().routes.get(query.route());
        var response = new CompoundTag(); String message = "";
        if (query.action() != SettlementRouteRequest.Action.VIEW && !SettlementAidService.enabled(player.serverLevel())) message = "Settlement travel is unavailable";
        else switch (query.action()) {
            case VIEW, CLOSE -> { }
            case PREVIEW -> {
                var preview = SettlementRouteService.preview(player, query.route(), new VillageAllegianceId(query.origin()), new VillageAllegianceId(query.destination()), query.points());
                message = preview.problem();
                if (preview.path() != null) {
                    response.putLongArray("Preview", preview.path().steps().stream().mapToLong(step -> step.position().asLong()).toArray());
                    response.putDouble("Seconds", (preview.path().steps().size() - 1) * .4);
                    int obstructed = 0, unloaded = 0;
                    for (var step : preview.path().steps()) {
                        var ground = step.position().below();
                        if (!player.serverLevel().hasChunkAt(ground)) unloaded++;
                        else if (!com.jvn.villagerretaliation.diplomacy.route.RouteGroundService.customGroundProblem(player.serverLevel(), ground, new VillageAllegianceId(query.origin())).isEmpty()
                                && !player.serverLevel().getBlockState(ground).is(net.minecraft.world.level.block.Blocks.COBBLESTONE)) obstructed++;
                    }
                    message = "Crossing estimate: " + Math.round(response.getDouble("Seconds")) + "s. Development: " + obstructed + " protected or unsuitable cells, " + unloaded + " await loaded terrain. Save to establish the route";
                }
            }
            case SAVE -> message = SettlementRouteService.save(player, query.route(), new VillageAllegianceId(query.origin()), new VillageAllegianceId(query.destination()), query.name(), query.revision(), query.points());
            case REMOVE -> message = SettlementRouteService.remove(player, route, query.revision());
            case BUILD -> message = route != null && route.revision == query.revision() ? SettlementRouteService.construct(player, route, query.section()) : "Route changed, refresh first";
            case RECALL -> message = SettlementCaravanService.recall(player, query.journey(), query.revision());
            case CONFIGURE -> message = SettlementCaravanService.configure(player, query.journey(), query.revision(), query.route(), query.members());
        }
        response.putString("Message", message);
        snapshot(player, query, response);
        PacketDistributor.sendToPlayer(player, new SettlementRoutesPayload(query.sequence(), response));
    }
    static void snapshot(ServerPlayer player, SettlementRouteRequest query, CompoundTag response) {
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var registry = VillageAllegianceRegistrySavedData.get(level);
        var origin = query.origin() == null ? null : new VillageAllegianceId(query.origin());
        var visible = VillageDiplomacyService.visible(player, origin);
        var towns = new ListTag();
        for (var town : data.settlements()) {
            if (!town.territory().active() || !town.dimension().equals(level.dimension().location())
                    || !visible.containsKey(town.id()) && !town.permits(player.getUUID(), SettlementPermission.VIEW) || towns.size() >= 128) continue;
            var row = new CompoundTag(); row.putUUID("Id", town.id().value()); row.putString("Name", registry.canonicalRecord(town.id()).map(record -> record.displayName()).orElse("Settlement"));
            row.putBoolean("Manage", town.permits(player.getUUID(), SettlementPermission.DIPLOMACY)); row.putLong("Position", town.center().asLong()); towns.add(row);
        }
        response.put("Towns", towns);
        var routes = new ListTag();
        for (var route : data.aid().routes.values()) {
            if (!SettlementRouteService.manages(player, route)) continue;
            var row = new CompoundTag(); row.putUUID("Id", route.id); row.putUUID("Origin", route.origin.value()); row.putUUID("Destination", route.destination.value());
            row.putString("Name", route.name); row.putLong("Revision", route.revision); routes.add(row);
            if (!route.id.equals(query.route())) continue;
            SettlementRouteService.refresh(level, route);
            response.putLongArray("Path", route.path.steps().stream().mapToLong(step -> step.position().asLong()).toArray());
            response.putLongArray("Points", route.points.stream().mapToLong(net.minecraft.core.BlockPos::asLong).toArray());
            response.putDouble("Seconds", route.estimatedTicks() / 20);
            var sections = new ListTag();
            for (int i = 0; i < route.sections(); i++) {
                var section = new CompoundTag(); section.putDouble("Trail", route.trail(i)); section.putDouble("Built", route.construction(i));
                section.putString("Status", route.blockers[i]); sections.add(section);
            }
            response.put("Sections", sections);
        }
        response.put("Routes", routes);
        var request = query.journey() == null ? null : data.aid().request(query.journey());
        if (request == null || request.purpose != SettlementFoodRequest.Purpose.SUPPLY || request.offer == null) return;
        var home = data.get(request.offer.arrangement() == SettlementFoodOffer.Arrangement.DONOR_DELIVERY ? request.donor() : request.requester()).orElse(null);
        if (home == null || !home.permits(player.getUUID(), SettlementPermission.VIEW)) return;
        response.putLong("JourneyRevision", request.revision()); response.putUUID("Home", home.id().value());
        response.putUUID("Other", (home.id().equals(request.donor()) ? request.requester() : request.donor()).value());
        if (request.selectedRoute != null) response.putUUID("SelectedRoute", request.selectedRoute);
        response.putString("JourneyStatus", request.state() + (request.blocker().isEmpty() ? "" : ": " + request.blocker()));
        response.putString("Cargo", request.offer.remaining().stream().map(parcel -> parcel.item().getCount() + " " + parcel.item().getHoverName().getString()).collect(java.util.stream.Collectors.joining(", ")));
        response.putBoolean("Departed", request.journey != null);
        var members = new ListTag();
        for (var resident : home.residents().values()) {
            if (members.size() >= 128) break;
            boolean leader = resident.id().equals(request.offer.courier());
            if (!leader && !request.participants.containsKey(resident.id()) && !SettlementAidService.eligible(level, home, resident, request.id(), true)) continue;
            var context = resident.context(level); if (context == null) continue;
            var row = new CompoundTag(); row.putUUID("Id", resident.id()); row.putString("Name", context.getName().getString());
            row.putBoolean("Leader", leader); row.putBoolean("Selected", request.participants.containsKey(resident.id()));
            row.putBoolean("Guard", request.participants.getOrDefault(resident.id(), false)); row.putBoolean("Armed", SettlementCaravanService.guard(context));
            var mission = data.aid().courier(resident.id());
            if (mission != null && mission.journey != null) row.putString("Status", mission.journey.leg + " " + mission.journey.presence + " " + mission.blocker());
            members.add(row);
        }
        response.put("Members", members);
    }
}
