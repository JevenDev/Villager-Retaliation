package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.PartyContainerLootService;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Supply;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.SupplyLink;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.SupplyLinkKind;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

final class SettlementSupplyService {
    private SettlementSupplyService() { }

    static List<Supply> supplies(ServerPlayer player, SettlementRecord town) {
        return supplies(player, town, new SettlementSupplyStock.Snapshot(player.serverLevel(), town));
    }

    static List<Supply> supplies(ServerPlayer player, SettlementRecord town, SettlementSupplyStock.Snapshot stock) {
        var level = player.serverLevel();
        var stores = stores(player, town);
        var rows = new LinkedHashMap<Key, Integer>();
        for (var store : stores.values()) {
            if (store.contents() != null) for (var stack : store.contents()) add(rows, store.position(), stack, 0);
            if (store.depot() != null) for (var target : store.depot().stockTargets()) add(rows, store.position(), target.sample(), target.count());
        }
        for (var queue : town.productionQueues().values()) for (var order : queue.orders()) {
            if (order.storage() == null || !stores.containsKey(order.storage()) || order.cancelled() && queue.pending(order.id()) == 0) continue;
            add(rows, order.storage(), order.output(), order.mode() == HiredProductionOrder.Mode.MAINTAIN_STOCK && !order.cancelled() ? order.amount() : 0);
        }
        for (var queue : town.transferQueues().values()) for (var order : queue.orders()) {
            if ((!order.cancelled() && !order.complete() || order.pending() > 0) && stores.containsKey(order.destination().position())) {
                add(rows, order.destination().position(), order.sample(), order.mode() == com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Mode.MAINTAIN_STOCK ? order.amount() : 0);
            }
        }
        var commitments = com.jvn.villagerretaliation.interaction.work.production.HiredProductionCommitments.get(level);
        for (var store : stores.values()) {
            for (var position : SettlementSupplyStock.positions(level, store.position())) {
                commitments.refresh(level, position);
                for (var sample : commitments.samples(position)) add(rows, store.position(), sample, 0);
            }
        }
        for (var worker : town.workers().values()) {
            if (!town.duties().containsKey(worker.id())) continue;
            var entity = level.getEntity(worker.id());
            var state = entity instanceof Villager villager ? com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager) : worker.settings();
            var destination = HiredProductionStock.destination(state);
            if (destination == null || !stores.containsKey(destination)) continue;
            if (entity instanceof Villager villager) {
                var inventory = com.jvn.villagerretaliation.inventory.HiredJobInventory.getJobInventory(villager);
                for (int slot : inventory.outputSlots()) add(rows, destination, inventory.getItem(slot), 0);
            } else {
                var inventory = worker.items(level.registryAccess());
                for (int slot : worker.outputSlots()) add(rows, destination, inventory.get(slot), 0);
            }
        }
        for (var store : stores.values()) {
            if (store.contents() == null && rows.keySet().stream().noneMatch(key -> key.position().equals(store.position()))) {
                rows.put(new Key(store.position(), ItemStack.EMPTY), 0);
            }
        }
        var normalized = new LinkedHashMap<Key, Integer>();
        for (var entry : rows.entrySet()) {
            var position = SettlementSupplyStock.positions(level, entry.getKey().position()).getFirst();
            var original = stores.get(entry.getKey().position());
            stores.putIfAbsent(position, new Store(position, original.depot(), original.area(), original.name(), original.status(), original.contents()));
            normalized.merge(new Key(position, entry.getKey().item()), entry.getValue(), Math::max);
        }
        var result = new ArrayList<Supply>();
        for (var entry : normalized.entrySet()) {
            var key = entry.getKey();
            var store = stores.get(key.position());
            long stored = store.contents() == null ? -1L : store.contents().stream()
                    .filter(stack -> HiredProductionStock.matches(key.item(), stack)).mapToLong(ItemStack::getCount).sum();
            long incoming = key.item().isEmpty() || store.contents() == null ? -1L
                    : stock.observe(key.position(), key.item(), null).incoming();
            String status = store.status();
            if (incoming < 0 && stored >= 0) status = "incoming_unavailable";
            String itemName = key.item().isEmpty() ? store.name() : key.item().getHoverName().getString();
            result.add(new Supply(identity(level, town, key), bounded(itemName, 128), key.position(),
                    store.depot() == null ? null : store.depot().id(), store.area(), bounded(store.name(), 128),
                    key.item(), stored, key.item().isEmpty() || store.contents() == null ? -1 : stock.observe(key.position(), key.item(), null).outgoing(),
                    incoming, entry.getValue(), status));
        }
        return List.copyOf(result);
    }

    static List<SupplyLink> links(ServerPlayer player, SettlementRecord town, String query) {
        return links(player, town, query, SettlementDemandService.inspect(player.serverLevel(), town, player));
    }

    static List<SupplyLink> links(ServerPlayer player, SettlementRecord town, String query, SettlementDemandService.Snapshot snapshot) {
        var supply = supplies(player, town, snapshot.stock()).stream().filter(row -> row.id().toString().equals(query)).findFirst().orElse(null);
        if (supply == null) return List.of();
        var level = player.serverLevel();
        var links = new LinkedHashMap<UUID, SupplyLink>();
        var depot = supply.depot() == null ? null : town.depots().get(supply.depot());
        if (depot != null) link(links, supply, SupplyLinkKind.STORAGE_TARGET, supply.storageName(), depot.area(), depot.id(),
                depot.position(), 0, Math.max(0, depot.stockTarget(supply.item())), supply.status(), "");
        var producers = new java.util.LinkedHashSet<UUID>();
        for (var queue : town.productionQueues().values()) {
            var area = town.areas().get(queue.owner());
            if (area == null) continue;
            for (var order : queue.orders()) {
                if (!SettlementSupplyStock.sameStorage(level, supply.position(), order.storage()) || !HiredProductionStock.matches(supply.item(), order.output())) continue;
                long pending = queue.pending(order.id());
                if (order.cancelled() && pending == 0 || order.complete() && pending == 0) continue;
                String status = order.cancelled() ? "cancelled" : order.paused() || !enabled(town, area) ? "paused"
                        : order.blocker().isEmpty() ? "active" : "blocked";
                String detail = order.blocker();
                if (!SettlementSupplyStock.permitsOutput(level, town, area.id(), supply.position())) {
                    status = "output_unavailable";
                }
                link(links, supply, SupplyLinkKind.PRODUCTION, area.name(), area.id(), order.id(), area.center(), pending,
                        order.mode() == HiredProductionOrder.Mode.MAINTAIN_STOCK && !order.cancelled() ? order.amount() : 0,
                        status, detail);
                producers.add(area.id());
            }
        }
        for (var queue : town.transferQueues().values()) {
            var area = town.areas().get(queue.owner());
            if (area == null) continue;
            for (var order : queue.orders()) {
                if (!SettlementSupplyStock.sameStorage(level, supply.position(), order.destination().position()) || !order.matches(supply.item())
                        || order.cancelled() && order.pending() == 0 || order.complete()) continue;
                long pending = queue.allocations().stream().filter(allocation -> allocation.order().equals(order.id()))
                        .mapToLong(com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Allocation::amount).sum();
                String status = order.cancelled() ? "cancelled" : order.paused() || !enabled(town, area) ? "paused"
                        : order.blocker().isEmpty() ? "active" : "blocked";
                link(links, supply, SupplyLinkKind.TRANSFER, area.name(), area.id(), order.id(), area.center(), pending,
                        order.mode() == com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue.Mode.MAINTAIN_STOCK ? order.amount() : 0,
                        status, order.blocker());
                input(links, supply, town, area, order.source().position(), order.source().depot());
            }
        }
        for (var area : town.areas().values()) {
            if (area.type().role() == null) continue;
            if (outputs(level, town, area, supply) && !producers.contains(area.id())) {
                String diagnostic = "";
                for (var duty : town.duties().entrySet()) {
                    if (!duty.getValue().equals(area.id())) continue;
                    var entity = level.getEntity(duty.getKey());
                    var work = SettlementManagementService.residentWork(level, town, duty.getKey(),
                            entity instanceof Villager villager ? villager : null, town.workers().get(duty.getKey()));
                    if (SettlementIssueService.blockedWorker(work.status())) {
                        diagnostic = work.diagnostic().isBlank() ? SettlementWorkerDiagnostics.describe(work.status()) : work.diagnostic();
                        break;
                    }
                }
                link(links, supply, SupplyLinkKind.WORKSITE, area.name(), area.id(), null, area.center(), 0, 0,
                        !enabled(town, area) ? "paused" : diagnostic.isBlank() ? "linked" : "blocked", diagnostic);
            }
        }
        for (var id : producers) {
            var area = town.areas().get(id);
            var settings = town.worksitePlans().get(id);
            if (settings != null && !settings.settings().storage().automatic()) {
                for (var storage : settings.settings().storage().entries()) if (storage.purpose().supplies()) {
                    var source = town.depots().values().stream().filter(value -> value.position().equals(storage.position())).findFirst().orElse(null);
                    if (source != null && !source.purpose().permitsRole(area.type().role())) continue;
                    input(links, supply, town, area, storage.position(), source == null ? null : source.id());
                }
            } else {
                var configured = SettlementWorksiteService.links(town, id);
                for (var source : SettlementStock.supplies(town, area.type().role())) if (configured.permitsSupply(source.id())) {
                    input(links, supply, town, area, source.position(), source.id());
                }
            }
        }
        for (var project : town.projects().values()) {
            if (depot == null || !depot.purpose().providesSupplies() || !project.reservesFootprint()
                    || !SettlementWorksiteService.links(town, project.area()).permitsSupply(depot.id())) continue;
            if (project.blocker().isBlank() && project.missingMaterials().isBlank()) continue;
            String detail = project.blocker().isBlank() ? "" : SettlementWorkerDiagnostics.describe(
                    "project." + project.blocker().replace("interaction.work.builder.", ""));
            if (!project.missingMaterials().isBlank()) detail += (detail.isBlank() ? "" : " ") + project.missingMaterials();
            link(links, supply, SupplyLinkKind.PROJECT, project.label(), project.area(), project.id(), project.origin(),
                    0, 0, "blocked", detail);
        }
        for (var demand : SettlementDemandViewService.demands(level, town, snapshot, supply.item())) {
            boolean input = demand.sources().stream().anyMatch(position -> SettlementSupplyStock.sameStorage(level, position, supply.position()));
            boolean product = town.productionQueues().values().stream().flatMap(queue -> queue.orders().stream())
                    .anyMatch(order -> order.id().equals(demand.consumer()) && order.matches(supply.item()) && SettlementSupplyStock.sameStorage(level, order.storage(), supply.position()));
            if (!product && !input) continue;
            link(links, supply, SupplyLinkKind.CONSUMER, demand.name(), demand.area(), demand.id(), demand.focus(), 0, 0,
                    demand.missing() < 0 ? "unknown" : demand.missing() > 0 ? "blocked" : "covered",
                    bounded(demand.requirement() + ": required " + demand.required() + ", supplied " + demand.supplied() + ", outstanding "
                            + (demand.missing() < 0 ? "unknown" : demand.missing()) + ". " + demand.advice(), 512));
        }
        return List.copyOf(links.values());
    }

    private static void input(Map<UUID, SupplyLink> links, Supply supply, SettlementRecord town, SettlementArea area,
                              BlockPos position, UUID depotId) {
        if (!town.contains(position)) return;
        var depot = depotId == null ? null : town.depots().get(depotId);
        String name = depot == null ? position.toShortString() : storageName(town, depot);
        link(links, supply, SupplyLinkKind.INPUT_STORAGE, area.name() + ": " + name, area.id(), depotId, position, 0, 0, "linked", "");
    }

    private static boolean outputs(ServerLevel level, SettlementRecord town, SettlementArea area, Supply supply) {
        if (area.type() == SettlementAreaType.CONSTRUCTION || area.type() == SettlementAreaType.GUARD_POST) return false;
        var plan = town.worksitePlans().get(area.id());
        if (supply.depot() != null && !town.depots().get(supply.depot()).purpose().permitsRole(area.type().role())) return false;
        if (plan != null && !plan.settings().storage().automatic()) return plan.settings().storage().entries().stream()
                .anyMatch(link -> link.purpose().outputs() && SettlementSupplyStock.sameStorage(level, link.position(), supply.position()));
        return supply.depot() != null && SettlementWorksiteService.links(town, area.id()).permitsOutput(supply.depot())
                && town.depots().get(supply.depot()).purpose().acceptsOutputs();
    }

    private static boolean enabled(SettlementRecord town, SettlementArea area) {
        var plan = town.worksitePlans().get(area.id());
        return area.enabled() && (plan == null || plan.settings().enabled());
    }

    private static Map<BlockPos, Store> stores(ServerPlayer player, SettlementRecord town) {
        var level = player.serverLevel();
        var result = new LinkedHashMap<BlockPos, Store>();
        for (var depot : town.depots().values()) {
            var area = town.areas().get(depot.area());
            if (area == null || !town.owns(area)) continue;
            String status = !area.enabled() ? "disabled" : !depot.rulesObserved() ? "rules_unobserved" : "available";
            boolean accessible = !AssignedStorageService.containerLoaded(level, depot.position())
                    || PartyContainerLootService.isAvailable(level, depot.position(), player)
                    && PartyContainerLootService.isAvailable(level, depot.position(), SettlementStorageService.actor(level, town));
            var contents = accessible ? new ArrayList<ItemStack>() : null;
            if (contents != null) for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                if (!depot.getItem(slot).isEmpty()) contents.add(depot.getItem(slot).copy());
            }
            result.put(depot.position(), new Store(depot.position(), depot, depot.area(), storageName(town, depot),
                    accessible ? status : "inaccessible", contents));
        }
        for (var area : town.areas().values()) {
            if (area.type().role() == null) continue;
            var plan = town.worksitePlans().get(area.id());
            if (plan != null) addStores(player, town, area, plan.settings().storage(), result);
        }
        for (var duty : town.duties().entrySet()) {
            var area = town.areas().get(duty.getValue());
            if (area != null && area.type().role() != null) addStores(player, town, area, SettlementStorageService.effective(town, duty.getKey()), result);
        }
        for (var queue : town.productionQueues().values()) for (var order : queue.orders()) {
            missingStore(level, town, result, order.storage(), queue.owner());
        }
        for (var queue : town.transferQueues().values()) for (var order : queue.orders()) {
            missingStore(level, town, result, order.destination().position(), queue.owner());
        }
        return result;
    }

    private static void missingStore(ServerLevel level, SettlementRecord town, Map<BlockPos, Store> stores, BlockPos position, UUID area) {
        if (position == null || !town.contains(position) || stores.containsKey(position)) return;
        var alias = stores.values().stream().filter(store -> SettlementSupplyStock.sameStorage(level, store.position(), position)).findFirst().orElse(null);
        stores.put(position, alias == null ? new Store(position, null, area, position.toShortString(), "missing", null)
                : new Store(position, alias.depot(), alias.area(), alias.name(), alias.status(), alias.contents()));
    }

    private static void addStores(ServerPlayer player, SettlementRecord town, SettlementArea area, SettlementStorageLinks storage,
                                  Map<BlockPos, Store> stores) {
        if (storage.automatic()) return;
        var level = player.serverLevel();
        for (var link : storage.entries()) {
            var position = link.position();
            if (stores.containsKey(position) || !town.contains(position)) continue;
            if (AssignedStorageService.containerLoaded(level, position) && AssignedStorageService.containerPositions(level, position).stream()
                    .anyMatch(stores::containsKey)) continue;
            String status = AssignedStorageService.containerStatus(level, position, link.purpose().id(), player);
            boolean permitted = SettlementStorageService.permitted(level, town, area.type().role(), link);
            List<ItemStack> contents = permitted ? AssignedStorageService.observedContents(level, position, player) : null;
            if (contents == null && (status.equals("available") || status.equals("full"))) status = "inaccessible";
            stores.put(position, new Store(position, null, area.id(), position.toShortString(), status, contents));
        }
    }

    private static String storageName(SettlementRecord town, SettlementDepot depot) {
        var area = town.areas().get(depot.area());
        return (area == null ? "" : area.name() + " ") + depot.position().toShortString();
    }

    private static void add(Map<Key, Integer> rows, BlockPos position, ItemStack item, int target) {
        if (!item.isEmpty()) rows.merge(new Key(position, HiredProductionStock.product(item).copyWithCount(1)), target, Math::max);
    }

    private static UUID identity(ServerLevel level, SettlementRecord town, Key key) {
        return UUID.nameUUIDFromBytes((town.id() + ":" + key.position().asLong() + ":"
                + (key.item().isEmpty() ? "unknown" : key.item().save(level.registryAccess())))
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void link(Map<UUID, SupplyLink> links, Supply supply, SupplyLinkKind kind, String name, UUID area, UUID target,
                             BlockPos position, long incoming, int amount, String status, String detail) {
        var id = UUID.nameUUIDFromBytes((supply.id() + ":" + kind + ":" + area + ":" + target + ":" + position.asLong()).getBytes(StandardCharsets.UTF_8));
        links.put(id, new SupplyLink(id, bounded(name, 128), supply.id(), kind, area, target, position,
                incoming, amount, status, bounded(detail, 512)));
    }

    private static String bounded(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, Character.isHighSurrogate(text.charAt(limit - 1)) ? limit - 1 : limit);
    }

    private record Store(BlockPos position, SettlementDepot depot, UUID area, String name, String status, List<ItemStack> contents) { }

    private record Key(BlockPos position, ItemStack item) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && this.position.equals(key.position) && ItemStack.isSameItemSameComponents(this.item, key.item);
        }

        @Override
        public int hashCode() { return 31 * this.position.hashCode() + ItemStack.hashItemAndComponents(this.item); }
    }
}
