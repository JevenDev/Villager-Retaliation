package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

final class SettlementLifecyclePlan {
    private SettlementLifecyclePlan() {
    }

    static CompoundTag release(ServerLevel level, SettlementRecord town, BlockPos position) {
        long plot = new ChunkPos(position).toLong();
        if (!town.territory().plots().contains(plot)) throw new IllegalArgumentException("The town does not own this plot.");
        if (new ChunkPos(town.center()).toLong() == plot) throw new IllegalArgumentException("Relocate the management center before releasing its plot.");
        if (occupied(town, plot)) throw new IllegalArgumentException("This plot still contains settlement facilities, residents or saved world work. Remove its facilities and settle that work first.");
        var remaining = new HashSet<>(town.territory().plots());
        remaining.remove(plot);
        if (!connected(remaining)) throw new IllegalArgumentException("Releasing this plot would disconnect the remaining territory.");
        var tag = town.save(level.registryAccess());
        tag.getCompound("Territory").putLongArray("Plots", remaining.stream().sorted().mapToLong(Long::longValue).toArray());
        tag.getCompound("Territory").putLong("Revision", Math.addExact(town.territory().revision(), 1L));
        return validated(level, tag);
    }

    static CompoundTag relocate(ServerLevel level, SettlementRecord town, BlockPos center) {
        if (center.equals(town.center())) throw new IllegalArgumentException("The management center is already here.");
        if (level.isOutsideBuildHeight(center) || !level.getWorldBorder().isWithinBounds(center)) throw new IllegalArgumentException("The new center is outside the world border or build height.");
        var tag = town.save(level.registryAccess());
        if (!town.contains(center)) {
            if (!emptyForRelocation(level, town)) throw new IllegalArgumentException("A populated town can move its management center only inside its claims. To move claims elsewhere, first empty its facilities, residents and saved work.");
            int dx = new ChunkPos(center).x - new ChunkPos(town.center()).x;
            int dz = new ChunkPos(center).z - new ChunkPos(town.center()).z;
            var data = SettlementSavedData.get(level);
            var registry = VillageAllegianceRegistrySavedData.get(level);
            var moved = new HashSet<Long>();
            for (long plot : town.territory().plots()) {
                int x = Math.addExact(ChunkPos.getX(plot), dx), z = Math.addExact(ChunkPos.getZ(plot), dz);
                if (!SettlementTerritory.validChunk(x, z) || !level.getWorldBorder().isWithinBounds(new ChunkPos(x, z))
                        || data.reserved(registry, town, x, z)) throw new IllegalArgumentException("Relocated territory conflicts with another claim or the world border.");
                moved.add(ChunkPos.asLong(x, z));
            }
            tag.getCompound("Territory").putLongArray("Plots", moved.stream().sorted().mapToLong(Long::longValue).toArray());
        }
        tag.putLong("Center", center.asLong());
        tag.getCompound("Territory").putLong("Revision", Math.addExact(town.territory().revision(), 1L));
        tag.remove("LeadershipOffer");
        return validated(level, tag);
    }

    static CompoundTag merge(ServerLevel level, SettlementRecord source, SettlementRecord target) {
        if (source == target || !source.dimension().equals(target.dimension())) throw new IllegalArgumentException("Merge requires two different towns in the same dimension.");
        var owned = new HashSet<>(target.territory().plots());
        for (long plot : source.territory().plots()) if (!owned.add(plot)) throw new IllegalArgumentException("The towns have conflicting plot ownership.");
        if (!connected(owned)) throw new IllegalArgumentException("Claim a connecting border before merging these towns.");
        var from = source.save(level.registryAccess());
        var into = target.save(level.registryAccess());
        into.putLong("Revision", Math.max(source.revision(), target.revision()));
        for (String key : from.getAllKeys()) {
            if (!(from.get(key) instanceof ListTag list) || Set.of("Delegates", "SupportServices").contains(key)) continue;
            var combined = ((ListTag) into.get(key)).copy();
            for (Tag raw : list) {
                var entry = raw.copy();
                if (key.equals("AnimalBirths") && !((CompoundTag) entry).hasUUID("ReceiptTown")) ((CompoundTag) entry).putUUID("ReceiptTown", source.id().value());
                if (key.equals("Projects") || key.equals("ProjectHistory")) ((CompoundTag) entry).getCompound("Task").putUUID("SettlementTown", target.id().value());
                if (key.equals("Arrivals") && !((CompoundTag) entry).contains("Origin")) ((CompoundTag) entry).putLong("Origin", source.center().asLong());
                if (key.equals("Navigation") && combined.contains(entry)) continue;
                combined.add(entry);
            }
            into.put(key, combined);
        }
        for (Tag raw : into.getList("WorkerPools", Tag.TAG_COMPOUND)) {
            var pool = (CompoundTag) raw;
            if (pool.getString("State").equals(SettlementWorkerPool.State.ACTIVE.name())
                    && !target.permits(pool.getUUID("Authority"), SettlementPermission.STAFF)) {
                pool.putString("State", SettlementWorkerPool.State.SUSPENDED.name());
                pool.putString("Blocker", "pool_permission_changed");
                pool.putLong("Revision", Math.addExact(into.getLong("Revision"), 1L));
            }
        }
        long[] oldRetired = from.getLongArray("RetiredProjects"), newRetired = into.getLongArray("RetiredProjects");
        long[] retired = java.util.Arrays.copyOf(newRetired, newRetired.length + oldRetired.length);
        System.arraycopy(oldRetired, 0, retired, newRetired.length, oldRetired.length);
        into.putLongArray("RetiredProjects", retired);
        var stage = source.stage().ordinal() > target.stage().ordinal() ? source.stage() : target.stage();
        into.putString("Stage", stage.name());
        into.putInt("ExpansionRadius", Math.max(from.getInt("ExpansionRadius"), into.getInt("ExpansionRadius")));
        into.put("Support", new SettlementSupportState().save());
        into.remove("LeadershipOffer");
        into.putLong("NextApplicantAt", Math.max(from.getLong("NextApplicantAt"), into.getLong("NextApplicantAt")));
        into.putLong("NextDepartureAt", Math.max(from.getLong("NextDepartureAt"), into.getLong("NextDepartureAt")));
        var territory = into.getCompound("Territory");
        var oldTerritory = from.getCompound("Territory");
        int capacity = Math.max(territory.getInt("EarnedCapacity"), oldTerritory.getInt("EarnedCapacity"));
        territory.putInt("EarnedCapacity", capacity);
        territory.putInt("GrandfatheredCapacity", Math.max(0, owned.size() - capacity));
        territory.putInt("FreeClaims", Math.addExact(territory.getInt("FreeClaims"), oldTerritory.getInt("FreeClaims")));
        territory.putInt("PaidPurchases", Math.addExact(territory.getInt("PaidPurchases"), oldTerritory.getInt("PaidPurchases")));
        territory.putInt("Rewards", territory.getInt("Rewards") | oldTerritory.getInt("Rewards"));
        territory.putLong("Revision", Math.addExact(Math.max(source.territory().revision(), target.territory().revision()), 1L));
        territory.putLongArray("Plots", owned.stream().sorted().mapToLong(Long::longValue).toArray());
        var economy = into.getCompound("Economy");
        var oldEconomy = from.getCompound("Economy");
        for (String key : Set.of("Balance", "PendingDeposit", "Reserve", "DailyBudget", "Spent")) {
            economy.put(key, CurrencyAmount.load(economy.getCompound(key)).add(CurrencyAmount.load(oldEconomy.getCompound(key))).save());
        }
        var bankStocks = economy.getCompound("BankStorage").getList("Stocks", Tag.TAG_COMPOUND);
        oldEconomy.getCompound("BankStorage").getList("Stocks", Tag.TAG_COMPOUND).forEach(stock -> bankStocks.add(stock.copy()));
        var bankTag = new CompoundTag(); bankTag.put("Stocks", bankStocks); economy.put("BankStorage", bankTag);
        var orders = economy.getList("Orders", Tag.TAG_COMPOUND);
        oldEconomy.getList("Orders", Tag.TAG_COMPOUND).forEach(order -> orders.add(order.copy()));
        var policySpending = economy.getList("PolicySpending", Tag.TAG_COMPOUND);
        oldEconomy.getList("PolicySpending", Tag.TAG_COMPOUND).forEach(account -> policySpending.add(account.copy()));
        var markets = economy.getList("Markets", Tag.TAG_COMPOUND);
        for (Tag raw : oldEconomy.getList("Markets", Tag.TAG_COMPOUND)) {
            var previous = (CompoundTag) raw;
            var existing = markets.stream().map(value -> (CompoundTag) value)
                    .filter(value -> value.getString("Group").equals(previous.getString("Group"))).findFirst().orElse(null);
            if (existing == null) markets.add(previous.copy());
            else for (String key : Set.of("Bought", "Sold")) existing.putInt(key, Math.addExact(existing.getInt(key), previous.getInt(key)));
        }
        economy.putLong("Period", Math.max(economy.getLong("Period"), oldEconomy.getLong("Period")));
        economy.putLong("LastTradeAt", Math.max(economy.getLong("LastTradeAt"), oldEconomy.getLong("LastTradeAt")));
        return validated(level, into);
    }

    private static CompoundTag validated(ServerLevel level, CompoundTag tag) {
        tag.putLong("Revision", Math.addExact(tag.getLong("Revision"), 1L));
        SettlementRecord.load(tag, level.registryAccess(), () -> {});
        return tag;
    }

    static boolean emptyForRelocation(ServerLevel level, SettlementRecord town) {
        var tag = town.save(level.registryAccess());
        return town.economy().bank.stocks.isEmpty() && tag.getAllKeys().stream().filter(key -> tag.get(key) instanceof ListTag)
                .filter(key -> !Set.of("Delegates", "History", "ProjectHistory", "SupportServices", "ManualDuties", "ManualDutyRevisions").contains(key))
                .allMatch(key -> ((ListTag) tag.get(key)).isEmpty());
    }

    private static boolean occupied(SettlementRecord town, long plot) {
        var chunk = new ChunkPos(plot);
        var min = new BlockPos(chunk.getMinBlockX(), -30_000_000, chunk.getMinBlockZ());
        var max = new BlockPos(chunk.getMaxBlockX(), 30_000_000, chunk.getMaxBlockZ());
        if (town.economy().bank.stocks.values().stream().anyMatch(stock -> new ChunkPos(stock.position()).toLong() == plot)) return true;
        if (town.areas().values().stream().anyMatch(area -> overlaps(min, max, area.min(), area.max()))
                || town.projects().values().stream().anyMatch(project -> overlaps(min, max, project.min(), project.max())
                        || project.workplace() != null && overlaps(min, max, project.workplace().min(), project.workplace().max())
                        || project.site() != null && overlaps(min, max, project.site().area().min(), project.site().area().max()))) return true;
        if (town.residents().values().stream().anyMatch(resident -> new ChunkPos(resident.position()).toLong() == plot)
                || town.workers().values().stream().anyMatch(worker -> new ChunkPos(worker.position()).toLong() == plot)
                || town.homes().values().stream().anyMatch(home -> home.bed() != null && new ChunkPos(home.bed()).toLong() == plot)) return true;
        return !town.arrivals().isEmpty() || !town.departures().isEmpty() || !town.itemDrops().isEmpty()
                || !town.groundItems().isEmpty() || !town.groundExperience().isEmpty() || !town.projectiles().isEmpty()
                || !town.entityDeaths().isEmpty() || !town.animalBirths().isEmpty() || !town.homeReleases().isEmpty()
                || town.fields().keySet().stream().anyMatch(pos -> new ChunkPos(pos).toLong() == plot)
                || town.furnaces().keySet().stream().anyMatch(pos -> new ChunkPos(pos).toLong() == plot)
                || town.brewingStands().keySet().stream().anyMatch(pos -> new ChunkPos(pos).toLong() == plot)
                || town.forests().values().stream().anyMatch(SettlementForest::pending)
                || town.mines().values().stream().anyMatch(SettlementMine::pending)
                || town.livestock().values().stream().anyMatch(state -> new ChunkPos(state.position()).toLong() == plot)
                || town.huntingTargets().values().stream().anyMatch(state -> new ChunkPos(state.position()).toLong() == plot);
    }

    private static boolean overlaps(BlockPos min, BlockPos max, BlockPos otherMin, BlockPos otherMax) {
        return min.getX() <= otherMax.getX() && max.getX() >= otherMin.getX() && min.getZ() <= otherMax.getZ() && max.getZ() >= otherMin.getZ();
    }

    private static boolean connected(Set<Long> plots) {
        if (plots.isEmpty()) return false;
        var remaining = new HashSet<>(plots);
        var queue = new ArrayDeque<Long>();
        queue.add(remaining.iterator().next());
        remaining.remove(queue.peek());
        while (!queue.isEmpty()) {
            long current = queue.remove();
            int x = ChunkPos.getX(current), z = ChunkPos.getZ(current);
            for (long neighbor : new long[] {ChunkPos.asLong(x - 1, z), ChunkPos.asLong(x + 1, z), ChunkPos.asLong(x, z - 1), ChunkPos.asLong(x, z + 1)}) {
                if (remaining.remove(neighbor)) queue.add(neighbor);
            }
        }
        return remaining.isEmpty();
    }
}
