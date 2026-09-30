package com.jvn.villagerretaliation.interaction.work.courier;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock;
import com.jvn.villagerretaliation.interaction.work.production.ProductionManagementService;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.TransferRequestPayload;
import com.jvn.villagerretaliation.network.TransferSnapshotPayload;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.jvn.villagerretaliation.settlement.SettlementTransferService;
import com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TransferManagementService {
    private TransferManagementService() {
    }

    public static void handle(ServerPlayer player, TransferRequestPayload request) {
        boolean action = !(request.command() instanceof TransferRequestPayload.View);
        var key = action ? TransferRequestPayload.TYPE.id() : com.jvn.villagerretaliation.VillagerRetaliation.id("transfer_view");
        if (!ServerboundRequestLimiter.tryAcquire(player, key, 3L)) {
            PacketDistributor.sendToPlayer(player, unavailable(player, request, "Please wait before changing transfers again"));
            return;
        }
        String feedback = action ? apply(player, request) : "";
        PacketDistributor.sendToPlayer(player, snapshot(player, request, action && feedback.isEmpty(), feedback));
    }

    public static String apply(ServerPlayer player, TransferRequestPayload request) {
        if (player == null || !player.isAlive() || player.isSpectator()) return "Courier permission changed";
        if (request.command() instanceof TransferRequestPayload.View) return "";
        var level = player.serverLevel();
        var target = request.target();
        var villager = target.town() == null && level.getEntity(target.subject()) instanceof Villager loaded ? loaded : null;
        var town = target.town() == null ? null : SettlementSavedData.get(level).get(new VillageAllegianceId(target.town())).orElse(null);
        if (target.town() == null ? !HiredTransferService.owns(player, villager)
                : town == null || !town.permits(player.getUUID(), SettlementPermission.VIEW) || !SettlementTransferService.permits(player, town, target.subject())) {
            return "Courier permission or assignment changed";
        }
        if (request.command() instanceof TransferRequestPayload.Append append) {
            var order = HiredTransferQueue.Order.create(append.source(), append.destination(), append.sample(), append.amount(), append.mode());
            return villager != null ? HiredTransferService.appendPersonal(player, villager, request.revision(), order)
                    : SettlementTransferService.append(player, town, target.subject(), request.revision(), order);
        }
        var edit = (TransferRequestPayload.Edit) request.command();
        return villager != null ? HiredTransferService.editPersonal(player, villager, request.revision(), edit.order(), edit.edit(), edit.index())
                : SettlementTransferService.edit(player, town, target.subject(), request.revision(), edit.order(), edit.edit(), edit.index());
    }

    public static TransferSnapshotPayload snapshot(ServerPlayer player, TransferRequestPayload request, boolean applied, String feedback) {
        if (!player.isAlive() || player.isSpectator()) return unavailable(player, request, "Courier permission changed");
        var level = player.serverLevel();
        var target = request.target();
        var storages = new ArrayList<TransferSnapshotPayload.Storage>();
        var products = new ArrayList<TransferSnapshotPayload.Product>();
        var stocks = new ArrayList<TransferSnapshotPayload.Stock>();
        HiredTransferQueue queue;
        String title;
        String blocker;
        boolean editable;
        if (target.town() == null) {
            var villager = level.getEntity(target.subject()) instanceof Villager loaded ? loaded : null;
            if (!HiredTransferService.owns(player, villager)) return unavailable(player, request, "Personal courier is unavailable");
            var access = HiredTransferService.access(level, villager);
            queue = access.queue();
            blocker = access.blocker();
            title = villager.getName().getString();
            editable = blocker.isEmpty();
            if (queue != null) for (var order : queue.orders()) {
                var facts = HiredTransferService.facts(level, villager, order);
                stocks.add(new TransferSnapshotPayload.Stock(order.id(), facts.stored(), facts.incoming()));
            }
            var positions = AssignedStorageService.assignedStorage(level, villager).stream()
                    .filter(record -> record.dimension().equals(level.dimension())).map(record -> record.pos()).distinct().limit(128).toList();
            for (var position : positions) {
                var endpoint = new HiredTransferQueue.Endpoint(position, null);
                boolean source = HiredTransferService.personalEndpoint(level, villager, endpoint, true);
                boolean destination = HiredTransferService.personalEndpoint(level, villager, endpoint, false);
                if (!source && !destination) continue;
                boolean loaded = level.hasChunkAt(position);
                storages.add(new TransferSnapshotPayload.Storage(endpoint, position.toShortString(), source, destination, loaded));
                var contents = AssignedStorageService.observedContents(level, position, player);
                if (source && contents != null) samples(products, endpoint, new net.minecraft.world.SimpleContainer(contents.toArray(net.minecraft.world.item.ItemStack[]::new)));
            }
        } else {
            var data = SettlementSavedData.get(level);
            var town = data.get(new VillageAllegianceId(target.town())).orElse(null);
            var area = town == null ? null : town.workplace(target.subject());
            if (town == null || area == null || area.type() != SettlementAreaType.DEPOT
                    || !town.dimension().equals(level.dimension().location()) || !town.permits(player.getUUID(), SettlementPermission.VIEW)) {
                return unavailable(player, request, "Courier worksite is unavailable");
            }
            queue = town.transferQueues().get(area.id());
            var plan = town.worksitePlans().get(area.id());
            var links = plan == null ? SettlementWorksiteLinks.AUTOMATIC : plan.links();
            title = area.name();
            blocker = data.readOnly() ? "Settlement data requires recovery"
                    : plan == null ? "Adopt worksite settings before adding shared transfers" : "";
            editable = blocker.isEmpty() && SettlementTransferService.permits(player, town, area.id());
            var configured = plan == null ? com.jvn.villagerretaliation.settlement.SettlementStorageLinks.AUTOMATIC : plan.settings().storage();
            var positions = new java.util.LinkedHashSet<net.minecraft.core.BlockPos>();
            if (configured.automatic()) town.depots().values().forEach(depot -> positions.add(depot.position()));
            else configured.entries().forEach(link -> positions.add(com.jvn.villagerretaliation.settlement.SettlementSupplyStock.positions(level, link.position()).getFirst()));
            for (var position : positions) {
                if (storages.size() >= 128) break;
                var depot = town.depots().values().stream().filter(value -> value.position().equals(position)).findFirst().orElse(null);
                var endpoint = new HiredTransferQueue.Endpoint(position, depot == null ? null : depot.id());
                boolean source = configured.automatic() ? depot != null && links.permitsSupply(depot.id())
                        : configured.entries().stream().anyMatch(link -> link.purpose().supplies()
                                && com.jvn.villagerretaliation.settlement.SettlementSupplyStock.sameStorage(level, position, link.position()));
                boolean destination = configured.automatic() ? depot != null && links.permitsOutput(depot.id())
                        : configured.entries().stream().anyMatch(link -> link.purpose().outputs()
                                && com.jvn.villagerretaliation.settlement.SettlementSupplyStock.sameStorage(level, position, link.position()));
                if (!source && !destination) continue;
                var contents = com.jvn.villagerretaliation.settlement.SettlementSupplyStock.contents(level, town, position);
                if (AssignedStorageService.containerLoaded(level, position)
                        && !com.jvn.villagerretaliation.inventory.PartyContainerLootService.isAvailable(level, position, player)) contents = null;
                storages.add(new TransferSnapshotPayload.Storage(endpoint, position.toShortString(), source, destination, contents != null));
                if (source && contents != null) samples(products, endpoint, new net.minecraft.world.SimpleContainer(contents.toArray(net.minecraft.world.item.ItemStack[]::new)));
            }
            var supply = new com.jvn.villagerretaliation.settlement.SettlementSupplyStock.Snapshot(level, town);
            if (queue != null) for (var order : queue.orders()) {
                var facts = supply.observe(order.destination().position(), order.sample(), null);
                boolean visible = !AssignedStorageService.containerLoaded(level, order.destination().position())
                        || com.jvn.villagerretaliation.inventory.PartyContainerLootService.isAvailable(level, order.destination().position(), player);
                stocks.add(new TransferSnapshotPayload.Stock(order.id(), visible ? facts.stored() : -1, visible ? facts.incoming() : -1));
            }
        }
        return new TransferSnapshotPayload(level.dimension().location(), target, request.request(), applied,
                ProductionManagementService.title(title), editable, blocker, feedback,
                queue == null ? null : queue.save(level.registryAccess()), storages, products, stocks);
    }

    private static void samples(List<TransferSnapshotPayload.Product> products, HiredTransferQueue.Endpoint endpoint, Container container) {
        for (int slot = 0; slot < container.getContainerSize() && products.size() < 256; slot++) {
            var sample = container.getItem(slot);
            if (sample.isEmpty() || products.stream().anyMatch(product -> product.source().equals(endpoint) && HiredProductionStock.matches(product.sample(), sample))) continue;
            products.add(new TransferSnapshotPayload.Product(endpoint, sample));
        }
    }

    public static TransferSnapshotPayload unavailable(ServerPlayer player, TransferRequestPayload request, String feedback) {
        return new TransferSnapshotPayload(player.serverLevel().dimension().location(), request.target(), request.request(), false,
                "", false, feedback, feedback, null, List.of(), List.of());
    }
}
