package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementWallMaterials {
    public static final int LOW_TOWER_STONE = 16;
    public static final int TALL_TOWER_STONE = 24;
    public static final int STONE_PER_LENGTH = 4;
    public static final int TIMBER_STONE_PER_LENGTH = 6;
    public static final double TIMBER_LOGS_PER_LENGTH = 0.5;
    public static final int PALISADE_POST_LOGS = 2;
    public static final int PLANK_POST_LOGS = 1;
    public static final double PALISADE_LOGS_PER_LENGTH = 0.5;
    public static final double PLANK_LOGS_PER_LENGTH = 0.25;
    public record Cost(int stone, int logs) {
        public static final Cost ZERO = new Cost(0, 0);
    }
    private record Amount(double stone, double logs) {
        // round the complete bill once and absorb floating point noise at whole-item boundaries
        Cost rounded() { return new Cost((int) Math.ceil(Math.max(0, stone) - 1e-9), (int) Math.ceil(Math.max(0, logs) - 1e-9)); }
    }
    private record Debit(SettlementDepot depot, int slot, int count) {}
    public record Payment(Cost cost, Cost supplied, String blocker, Runnable debit) {
        public boolean ready() { return blocker.isEmpty() && cost.equals(supplied); }
        public String message() {
            return "Storage: " + (cost.stone() > 0 || cost.logs() == 0 ? "Stone " + supplied.stone() + "/" + cost.stone() + " (stone/cobble)" : "")
                    + (cost.logs() > 0 ? (cost.stone() > 0 ? ", " : "") + "Logs " + supplied.logs() + "/" + cost.logs() + " (any)" : "")
                    + (ready() ? "" : ". " + (blocker.isEmpty() ? "Not enough available materials" : blocker));
        }
        public boolean pay() {
            if (!ready()) return false;
            debit.run();
            return true;
        }
    }
    private SettlementWallMaterials() {}

    public static Cost cost(SettlementWall wall, SettlementWallTerrain.Preview preview, SettlementWallsSavedData.Entry upgrade) {
        var target = new LinkedHashMap<BlockPos, BlockState>();
        var credit = new LinkedHashMap<BlockPos, BlockState>();
        for (var cell : preview.cells()) {
            if (!cell.target().isAir() && (cell.kind() == SettlementWallTerrain.Kind.PLACE
                    || upgrade != null && cell.kind() == SettlementWallTerrain.Kind.EXISTING)) target.put(cell.position(), cell.target());
            if (upgrade != null && cell.before() != null && cell.before().equals(upgrade.blocks().get(cell.position())))
                credit.put(cell.position(), cell.before());
        }
        var next = amount(wall, target);
        var previous = upgrade == null ? new Amount(0, 0) : amount(upgrade.wall(), credit);
        return new Amount(next.stone() - previous.stone(), next.logs() - previous.logs()).rounded();
    }

    static Cost upgradeCost(Map<SettlementWallsSavedData.Entry, SettlementWall> pieces, SettlementWallTerrain.Preview preview) {
        double stone = 0, logs = 0;
        for (var entry : pieces.entrySet()) {
            var target = new LinkedHashMap<BlockPos, BlockState>();
            for (var cell : SettlementWallUpgrade.pieceCells(entry.getValue(), preview)) {
                if (!cell.target().isAir() && (cell.kind() == SettlementWallTerrain.Kind.PLACE
                        || cell.kind() == SettlementWallTerrain.Kind.EXISTING)) target.put(cell.position(), cell.target());
            }
            var credit = new LinkedHashMap<BlockPos, BlockState>();
            for (var cell : preview.cells()) if (cell.before() != null && cell.before().equals(entry.getKey().blocks().get(cell.position())))
                credit.put(cell.position(), cell.before());
            var next = amount(entry.getValue(), target);
            var previous = amount(entry.getKey().wall(), credit);
            stone += next.stone() - previous.stone();
            logs += next.logs() - previous.logs();
        }
        return new Amount(stone, logs).rounded();
    }

    private static Amount amount(SettlementWall wall, Map<BlockPos, BlockState> blocks) {
        var remaining = new HashMap<>(blocks);
        double stone = 0, logs = 0;
        var pieces = wall.part() == SettlementWall.Part.COMPLETE ? SettlementWallGeometry.pieces(wall) : List.of(wall);
        for (var piece : pieces) {
            var plan = SettlementWallGeometry.build(piece);
            var positions = new java.util.HashSet<BlockPos>();
            plan.cells().stream().filter(cell -> !cell.state().isAir()).forEach(cell -> positions.add(cell.position()));
            var floors = new HashMap<BlockPos, Integer>();
            plan.foundations().forEach(pos -> floors.merge(pos.atY(0), pos.getY(), Math::min));
            int count = 0;
            var iterator = remaining.keySet().iterator();
            while (iterator.hasNext()) {
                var pos = iterator.next();
                if (positions.contains(pos) || pos.getY() < floors.getOrDefault(pos.atY(0), Integer.MIN_VALUE)) {
                    count++;
                    iterator.remove();
                }
            }
            if (count == 0 || positions.isEmpty()) continue;
            // the normal piece has a fixed price, clipped terrain and extra foundations scale its size
            double scale = count / (double) positions.size();
            if (piece.part() == SettlementWall.Part.TOWER) {
                switch (piece.towerAt(piece.nodes().getFirst())) {
                    case "stone_tower_low" -> stone += scale * LOW_TOWER_STONE;
                    case "stone_tower_tall" -> stone += scale * TALL_TOWER_STONE;
                    case "palisade_post" -> logs += scale * PALISADE_POST_LOGS;
                    case "plank_post" -> logs += scale * PLANK_POST_LOGS;
                    default -> throw new IllegalArgumentException("Unknown wall post material");
                }
            } else {
                var start = piece.nodes().getFirst();
                var end = piece.nodes().getLast();
                int length = Math.max(Math.abs(start.getX() - end.getX()), Math.abs(start.getZ() - end.getZ())) + 1
                        - (piece.towerRadius(start) + 1) - (piece.towerRadius(end) + 1);
                double sizedLength = scale * Math.max(0, length);
                switch (piece.type()) {
                    case LOW_STONE, TALL_STONE -> stone += sizedLength * STONE_PER_LENGTH;
                    case TIMBER -> {
                        stone += sizedLength * TIMBER_STONE_PER_LENGTH;
                        logs += sizedLength * TIMBER_LOGS_PER_LENGTH;
                    }
                    case PALISADE -> logs += sizedLength * PALISADE_LOGS_PER_LENGTH;
                    case PLANK -> logs += sizedLength * PLANK_LOGS_PER_LENGTH;
                }
            }
        }
        return new Amount(stone, logs);
    }

    public static boolean stone(ItemStack stack) { return stack.is(Items.STONE) || stack.is(Items.COBBLESTONE); }
    public static boolean logs(ItemStack stack) { return stack.is(ItemTags.LOGS); }

    public static Payment prepare(ServerLevel level, SettlementRecord town, UUID consumer, Cost cost) {
        if (town == null || SettlementSavedData.get(level).readOnly() || !town.territory().active())
            return new Payment(cost, Cost.ZERO, "Settlement storage is unavailable", () -> {});
        var debits = new ArrayList<Debit>();
        int stone = 0, logs = 0;
        String blocker = "";
        for (var depot : SettlementStock.supplies(town, HiredVillagerRole.BUILDER)) {
            if (!depot.rulesObserved() || level.hasChunkAt(depot.position()) && !SettlementStorageService.permitted(level, town,
                    HiredVillagerRole.BUILDER, new SettlementStorageLinks.Link(depot.position(), SettlementStorageLinks.Purpose.SUPPLY, 0))) continue;
            var promised = new ArrayList<ItemStack>();
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                var stack = depot.getItem(slot);
                boolean stoneItem = stone(stack);
                if (!stoneItem && !logs(stack)) continue;
                int need = stoneItem ? cost.stone() - stone : cost.logs() - logs;
                if (need <= 0) continue;
                int already = promised.stream().filter(value -> ItemStack.isSameItemSameComponents(value, stack)).mapToInt(ItemStack::getCount).sum();
                var allocation = SettlementAllocationService.inspect(level, town, null, consumer, depot.position(), stack);
                if (allocation.blocker().equals("allocation_pending")) blocker = "Storage allocation is updating. Confirm to check again";
                int allowance = AssignedStorageService.observedTransferAllowance(level, depot.position(), depot, depot.observedRules(),
                        stack, VillagerFilterPolicy.TransferOperation.PROVIDE, promised);
                int claims = AssignedStorageService.reservedItemCount(level, depot.position(), null, VillagerFilterPolicy.TransferOperation.PROVIDE, stack);
                int available = depot.count(value -> ItemStack.isSameItemSameComponents(value, stack)) - already - claims;
                int count = Math.min(need, Math.min(stack.getCount(), Math.min(available, Math.min(allowance, allocation.allowance() - already))));
                if (count <= 0) continue;
                promised.add(stack.copyWithCount(count));
                debits.add(new Debit(depot, slot, count));
                if (stoneItem) stone += count;
                else logs += count;
            }
        }
        var supplied = new Cost(stone, logs);
        return new Payment(cost, supplied, supplied.equals(cost) ? "" : blocker, () -> {
            for (var debit : debits) debit.depot().removeItem(debit.slot(), debit.count());
        });
    }
}
