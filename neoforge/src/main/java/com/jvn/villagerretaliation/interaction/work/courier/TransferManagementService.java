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
            var order = HiredTransferQueue.Order.create(append.source(), append.destination(), append.sample(), append.amount());
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
            var positions = AssignedStorageService.assignedStorage(level, villager).stream()
                    .filter(record -> record.dimension().equals(level.dimension())).map(record -> record.pos()).distinct().limit(128).toList();
            for (var position : positions) {
                var endpoint = new HiredTransferQueue.Endpoint(position, null);
                boolean source = HiredTransferService.personalEndpoint(level, villager, endpoint, true);
                boolean destination = HiredTransferService.personalEndpoint(level, villager, endpoint, false);
                if (!source && !destination) continue;
                boolean loaded = level.hasChunkAt(position);
                storages.add(new TransferSnapshotPayload.Storage(endpoint, position.toShortString(), source, destination, loaded));
                if (source && loaded && level.getBlockEntity(position) instanceof Container container) samples(products, endpoint, container);
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
            for (var depot : town.depots().values()) {
                if (storages.size() >= 128) break;
                var depotArea = town.areas().get(depot.area());
                if (depotArea == null || !depotArea.enabled()) continue;
                boolean source = links.permitsSupply(depot.id());
                boolean destination = links.permitsOutput(depot.id());
                if (!source && !destination) continue;
                var endpoint = new HiredTransferQueue.Endpoint(depot.position(), depot.id());
                storages.add(new TransferSnapshotPayload.Storage(endpoint, ProductionManagementService.title(depotArea.name() + " " + depot.position().toShortString()),
                        source, destination, depot.rulesObserved()));
                if (source && depot.rulesObserved()) samples(products, endpoint, depot);
            }
        }
        return new TransferSnapshotPayload(level.dimension().location(), target, request.request(), applied,
                ProductionManagementService.title(title), editable, blocker, feedback,
                queue == null ? null : queue.save(level.registryAccess()), storages, products);
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
