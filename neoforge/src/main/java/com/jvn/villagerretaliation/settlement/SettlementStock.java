package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

public final class SettlementStock {
    private SettlementStock() {
    }

    public static List<SettlementDepot> supplies(SettlementRecord town) {
        return supplies(town, null);
    }

    public static List<SettlementDepot> supplies(SettlementRecord town, HiredVillagerRole role) {
        var catalog = catalog(town);
        return SettlementDepotPurpose.BUILDER_ONLY.permitsRole(role) ? catalog.supplies : catalog.sharedSupplies;
    }

    public static List<SettlementDepot> outputs(SettlementRecord town) {
        return outputs(town, null);
    }

    public static List<SettlementDepot> outputs(SettlementRecord town, HiredVillagerRole role) {
        var catalog = catalog(town);
        return SettlementDepotPurpose.BUILDER_ONLY.permitsRole(role) ? catalog.outputs : catalog.sharedOutputs;
    }

    public static List<SettlementDepot> depots(SettlementRecord town) {
        return depots(town, null);
    }

    public static List<SettlementDepot> depots(SettlementRecord town, HiredVillagerRole role) {
        var catalog = catalog(town);
        return SettlementDepotPurpose.BUILDER_ONLY.permitsRole(role) ? catalog.depots : catalog.sharedDepots;
    }

    static List<SettlementDepot> registered(SettlementRecord town, HiredVillagerRole role) {
        return town.depots().values().stream().filter(depot -> {
            var area = town.areas().get(depot.area());
            return town.territory().active() && town.owns(area) && area.enabled() && depot.purpose().permitsRole(role);
        }).sorted(Comparator.comparingInt(SettlementDepot::priority).reversed().thenComparing(SettlementDepot::id)).toList();
    }

    private static Catalog catalog(SettlementRecord town) {
        if (town.stockCatalog == null || !town.stockCatalog.matches(town)) town.stockCatalog = new Catalog(town);
        return town.stockCatalog;
    }

    static final class Catalog {
        final List<Entry> entries;
        final List<SettlementDepot> depots;
        final List<SettlementDepot> supplies;
        final List<SettlementDepot> outputs;
        final List<SettlementDepot> sharedDepots;
        final List<SettlementDepot> sharedSupplies;
        final List<SettlementDepot> sharedOutputs;

        Catalog(SettlementRecord town) {
            this.entries = town.depots().values().stream().map(depot -> new Entry(depot, depot.priority(), depot.purpose(), active(town, depot))).toList();
            this.depots = this.entries.stream().filter(Entry::active).map(Entry::depot)
                    .sorted(Comparator.comparingInt(SettlementDepot::priority).reversed().thenComparing(SettlementDepot::id)).toList();
            this.supplies = this.depots.stream().filter(depot -> depot.purpose().providesSupplies()).toList();
            this.outputs = this.depots.stream().filter(depot -> depot.purpose().acceptsOutputs()).toList();
            this.sharedDepots = this.depots.stream().filter(depot -> depot.purpose().permitsRole(null)).toList();
            this.sharedSupplies = this.supplies.stream().filter(depot -> depot.purpose().permitsRole(null)).toList();
            this.sharedOutputs = this.outputs.stream().filter(depot -> depot.purpose().permitsRole(null)).toList();
        }

        boolean matches(SettlementRecord town) {
            if (this.entries.size() != town.depots().size()) return false;
            for (var entry : this.entries) {
                var depot = town.depots().get(entry.depot.id());
                if (depot != entry.depot || depot.priority() != entry.priority || depot.purpose() != entry.purpose
                        || active(town, depot) != entry.active) return false;
            }
            return true;
        }

        private static boolean active(SettlementRecord town, SettlementDepot depot) {
            if(town.autonomous()!=null) {
                var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                var level=server==null?null:server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,town.dimension()));
                if(level==null || !level.hasChunkAt(depot.position())) return false;
            }
            var area = town.areas().get(depot.area());
            return town.owns(area) && area.enabled();
        }
    }

    private record Entry(SettlementDepot depot, int priority, SettlementDepotPurpose purpose, boolean active) { }

    public static int count(SettlementRecord town, Predicate<ItemStack> predicate) {
        return count(supplies(town), predicate);
    }

    public static int count(List<SettlementDepot> depots, Predicate<ItemStack> predicate) {
        return depots.stream().mapToInt(depot -> depot.count(predicate)).sum();
    }

    static ItemStack take(net.minecraft.server.level.ServerLevel level, SettlementRecord town, java.util.UUID consumer,
                          Predicate<ItemStack> predicate, int maximum) {
        for (var depot : supplies(town)) for (int slot = 0; slot < depot.getContainerSize(); slot++) {
            var sample = depot.getItem(slot);
            if (sample.isEmpty() || !predicate.test(sample)) continue;
            int count = Math.min(maximum, SettlementAllocationService.inspect(level, town, null, consumer, depot.position(), sample).allowance());
            count = Math.min(count, com.jvn.villagerretaliation.inventory.AssignedStorageService.observedTransferAllowance(
                    level, depot.position(), depot, depot.observedRules(), sample,
                    com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.PROVIDE));
            if (count > 0) return depot.removeItem(slot, count);
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack take(SettlementRecord town, Predicate<ItemStack> predicate, int maximum) {
        return take(supplies(town), predicate, maximum);
    }

    public static ItemStack take(List<SettlementDepot> depots, Predicate<ItemStack> predicate, int maximum) {
        for (SettlementDepot depot : depots) {
            ItemStack taken = depot.withdraw(predicate, maximum);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack deposit(SettlementRecord town, ItemStack stack) {
        return deposit(outputs(town), stack);
    }

    public static ItemStack deposit(List<SettlementDepot> depots, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (SettlementDepot depot : depots) {
            remaining = depot.deposit(remaining);
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    public static int capacity(SettlementRecord town, ItemStack stack) {
        return capacity(outputs(town), stack);
    }

    public static int capacity(List<SettlementDepot> depots, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        int capacity = 0;
        for (SettlementDepot depot : depots) {
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                if (!depot.canPlaceItem(slot, stack)) {
                    continue;
                }
                ItemStack existing = depot.getItem(slot);
                if (existing.isEmpty()) {
                    capacity += Math.min(depot.getMaxStackSize(), stack.getMaxStackSize());
                } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                    capacity += Math.max(0, Math.min(depot.getMaxStackSize(), stack.getMaxStackSize()) - existing.getCount());
                }
            }
        }
        return capacity;
    }
}
