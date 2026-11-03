package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.diplomacy.VillageDiplomacyService;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementExchangePayload;
import com.jvn.villagerretaliation.network.SettlementExchangeRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementExchangeNetworking {
    private SettlementExchangeNetworking() { }
    public static void handle(ServerPlayer player, SettlementExchangeRequest query) {
        if (!player.server.isSameThread() || !player.isAlive() || player.isSpectator()
                || !ServerboundRequestLimiter.tryAcquire(player, SettlementExchangeRequest.TYPE.id(), 5)) return;
        var village = new VillageAllegianceId(query.village());
        var result = query.action() == SettlementExchangeService.Action.VIEW ? new SettlementExchangeService.Result("", null)
                : SettlementExchangeService.act(player, village, query.target() == null ? null : new VillageAllegianceId(query.target()),
                query.request(), query.revision(), query.action(), query.terms());
        PacketDistributor.sendToPlayer(player, new SettlementExchangePayload(query.sequence(), snapshot(player, query, result)));
    }
    static CompoundTag snapshot(ServerPlayer player, SettlementExchangeRequest query, SettlementExchangeService.Result result) {
        var tag = new CompoundTag(); tag.putUUID("Village", query.village()); tag.putString("Message", result.message());
        var level = player.serverLevel(); var data = SettlementSavedData.get(level); var town = data.get(new VillageAllegianceId(query.village())).orElse(null);
        if (town == null || !town.permits(player.getUUID(), SettlementPermission.VIEW)) return tag;
        tag.putLong("Revision", data.aid().revision());
        tag.putBoolean("Authorized", SettlementAidService.enabled(level) && SettlementExchangeService.represents(player, town));
        var registry = VillageAllegianceRegistrySavedData.get(level); var contacts = new ListTag();
        var visible = VillageDiplomacyService.visible(player, town.id());
        for (var other : data.settlements()) {
            if (other.id().equals(town.id()) || !visible.containsKey(other.id()) || contacts.size() >= 128) continue;
            var entry = new CompoundTag(); entry.putUUID("Id", other.id().value());
            entry.putString("Name", registry.canonicalRecord(other.id()).map(value -> value.displayName()).orElse("Settlement")); contacts.add(entry);
        }
        tag.put("Contacts", contacts);
        var requests = data.aid().requests.values().stream().filter(request -> request.trade != null
                && (request.donor().equals(town.id()) || request.requester().equals(town.id())))
                .sorted(Comparator.comparingLong(SettlementFoodRequest::created).reversed()).toList();
        int page = Math.min(query.page(), Math.max(0, (requests.size() - 1) / 8));
        tag.putInt("Page", page); tag.putInt("Total", requests.size());
        if (result.request() != null && result.message().equals("applied")) tag.putUUID("Selected", result.request().id());
        var rows = new ListTag();
        for (var request : requests.subList(page * 8, Math.min(requests.size(), page * 8 + 8))) {
            var row = new CompoundTag(); var trade = request.trade;
            row.putUUID("Id", request.id()); row.putLong("Revision", request.revision());
            row.putUUID("First", request.donor().value()); row.putUUID("Second", request.requester().value());
            row.putString("FirstName", registry.canonicalRecord(request.donor()).map(value -> value.displayName()).orElse("Settlement"));
            row.putString("SecondName", registry.canonicalRecord(request.requester()).map(value -> value.displayName()).orElse("Settlement"));
            row.putString("State", trade.state.name()); row.putString("Journey", request.state().name());
            row.putString("Blocker", request.blocker()); row.putInt("Remaining", trade.countersRemaining());
            row.putBoolean("Turn", !trade.proposer.equals(town.id())); row.putBoolean("Departed", request.journey != null);
            row.put("Terms", trade.terms.save(level.registryAccess())); row.putString("Explanation", trade.explanation);
            row.putString("Transport", trade.transport.mixedFraction()); row.putInt("Guards", (int) request.participants.values().stream().filter(Boolean::booleanValue).count());
            if (request.selectedRoute != null) {
                var route = data.aid().routes.get(request.selectedRoute); row.putString("Route", route == null ? "Unavailable" : route.name);
            }
            if (trade.first != null) {
                row.put("FirstCargo", trade.first.save(level.registryAccess())); row.put("SecondCargo", trade.second.save(level.registryAccess()));
            }
            rows.add(row);
        }
        tag.put("Rows", rows);
        var other = query.target() == null ? null : data.get(new VillageAllegianceId(query.target())).orElse(null);
        tag.put("Stock", stock(player, town));
        if (other != null && visible.containsKey(other.id())) {
            tag.put("OtherStock", stock(player, other));
            var dimension = SettlementAidService.dimension(player.server, other);
            if (dimension != null) {
                var needs = new ListTag();
                for (var demand : SettlementExchangeStock.demands(dimension, other).demands()) {
                    if (needs.size() >= 8) break;
                    if (demand.missing() <= 0) continue;
                    var need = new CompoundTag(); need.putString("Name", demand.name()); need.putLong("Missing", demand.missing()); needs.add(need);
                }
                tag.put("Needs", needs);
            }
        }
        tag.putString("Funds", town.economy().constructionFunds().mixedFraction()); return tag;
    }
    private static ListTag stock(ServerPlayer player, SettlementRecord town) {
        var items = new ArrayList<ItemStack>();
        for (var depot : SettlementAidStock.depots(town, false)) for (int slot = 0; slot < depot.getContainerSize(); slot++) {
            var item = depot.getItem(slot);
            if (item.isEmpty() || com.jvn.villagerretaliation.interaction.VillagerCurrencyResources.isCurrency(player.server, item) || items.size() >= 256 || items.stream().anyMatch(value -> ItemStack.isSameItemSameComponents(value, item))) continue;
            items.add(item.copyWithCount(1));
        }
        var tag = new ListTag(); items.forEach(item -> tag.add(item.save(player.registryAccess()))); return tag;
    }
}
