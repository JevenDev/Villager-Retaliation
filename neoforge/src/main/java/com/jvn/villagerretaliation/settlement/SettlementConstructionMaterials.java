package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderTaskState;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderWorker;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Project-owned materials. A lot has exactly one owner throughout reservation, collection,
 * spending and refund; it is never also inserted into the worker's general inventory.
 * Depot contents and this ledger share one SettlementSavedData snapshot. Ordinary chest and
 * barrel transfers cross their independent chunk save using checked write-ahead receipts.
 */
public final class SettlementConstructionMaterials {
    private SettlementConstructionMaterials() { }

    public static boolean reserved(SettlementProject project) {
        return project != null && project.materialEscrow().getBoolean("Reserved");
    }

    public static boolean managed(Villager builder, CompoundTag state) {
        return reserved(project(builder, state));
    }

    public static String blocker(SettlementProject project) {
        return project.materialEscrow().getString("Blocker");
    }

    public static boolean reserve(ServerLevel level, SettlementRecord town, SettlementProject project,
                                  BuilderStructureScanner.StructurePlan plan, Villager builder) {
        var ledger = project.materialEscrow();
        if (reserved(project)) {
            if (!ledger.getBoolean("Returning")) {
                boolean ready = SettlementConstructionTransfers.advanceDebits(level, town, project, false);
                if (ready) ledger.remove("Blocker");
                return ready;
            }
            refund(level, town, project);
            if (refundPending(project)) return false;
            for (var key : List.copyOf(ledger.getAllKeys())) ledger.remove(key);
        }
        var storage = SettlementStorageService.effective(town, builder.getUUID());
        var duty = town.duties().get(builder.getUUID());
        var links = duty == null ? SettlementWorksiteLinks.AUTOMATIC : SettlementWorksiteService.links(town, duty, builder.getUUID());
        var sources = SettlementStock.supplies(town, HiredVillagerRole.BUILDER).stream()
                .filter(depot -> authorized(storage, links, depot, true))
                .filter(depot -> depot.rulesObserved() && BuilderWorker.builderMaterialStorageFilter(plan, project.origin()).test(depot.position()))
                .filter(depot -> accessible(level, town, depot, true)).toList();
        // Specific items precede broad tags so, for example, a logs row cannot steal the
        // only oak logs from an oak-specific row when birch is available for the tag.
        var requirements = BuilderConstructionCost.requirements(plan).stream()
                .sorted(java.util.Comparator.comparing(BuilderConstructionCost.Ingredient::tag)).toList();
        if (requirements.size() > BuilderConstructionCost.MAX_INGREDIENTS) {
            ledger.putString("Blocker", "material_quote_too_large");
            town.changed();
            return false;
        }
        var lots = new ListTag();
        var quote = new ListTag();
        Map<SettlementDepot, Map<Integer, Integer>> slots = new HashMap<>();
        Map<SettlementDepot, List<ItemStack>> promises = new HashMap<>();
        var ordinary = storage.entries().stream().filter(link -> link.purpose().supplies())
                .filter(link -> BuilderWorker.builderMaterialStorageFilter(plan, project.origin()).test(link.position()))
                .filter(link -> town.depots().values().stream().noneMatch(depot -> depot.position().equals(link.position())))
                .sorted(java.util.Comparator.comparingInt(SettlementStorageLinks.Link::priority)).toList();
        Map<BlockPos, Map<Integer, Integer>> ordinarySlots = new java.util.LinkedHashMap<>();
        Map<BlockPos, List<ItemStack>> ordinaryPromises = new HashMap<>();
        Map<BlockPos, net.minecraft.world.Container> ordinaryContainers = new HashMap<>();
        Map<BlockPos, UUID> ordinaryIds = new HashMap<>();
        for (int row = 0; row < requirements.size(); row++) {
            var requirement = requirements.get(row);
            int remaining = requirement.count();
            var line = new CompoundTag();
            line.putString("Id", requirement.id().toString());
            line.putBoolean("Tag", requirement.tag());
            line.putInt("Count", remaining);
            line.putInt("Spent", 0);
            line.putInt("Refunded", 0);
            quote.add(line);
            for (var depot : sources) {
                var reservedSlots = slots.computeIfAbsent(depot, ignored -> new HashMap<>());
                var promised = promises.computeIfAbsent(depot, ignored -> new ArrayList<>());
                for (int slot = 0; slot < depot.getContainerSize() && remaining > 0; slot++) {
                    var stack = depot.getItem(slot);
                    if (!requirement.matches(stack)) continue;
                    int already = promised.stream().filter(value -> ItemStack.isSameItemSameComponents(value, stack)).mapToInt(ItemStack::getCount).sum();
                    int allocation = SettlementAllocationService.inspect(level, town, builder.getUUID(), project.id(), depot.position(), stack).allowance();
                    int allowance = AssignedStorageService.observedTransferAllowance(level, depot.position(), depot, depot.observedRules(),
                            stack, VillagerFilterPolicy.TransferOperation.PROVIDE, promised);
                    int claims = AssignedStorageService.reservedItemCount(level, depot.position(), null,
                            VillagerFilterPolicy.TransferOperation.PROVIDE, stack);
                    int available = depot.count(value -> ItemStack.isSameItemSameComponents(value, stack)) - already - claims;
                    int count = Math.min(remaining, Math.min(stack.getCount() - reservedSlots.getOrDefault(slot, 0),
                            Math.min(available, Math.min(allowance, allocation - already))));
                    if (count <= 0) continue;
                    var lot = new CompoundTag();
                    lot.putInt("Row", row);
                    lot.putUUID("Source", depot.id());
                    lot.putLong("Position", depot.position().asLong());
                    lot.putInt("Slot", slot);
                    lot.put("Stack", stack.copyWithCount(count).save(level.registryAccess()));
                    lot.putInt("Remaining", count);
                    lot.putBoolean("Collected", false);
                    lots.add(lot);
                    reservedSlots.merge(slot, count, Integer::sum);
                    promised.add(stack.copyWithCount(count));
                    remaining -= count;
                }
            }
            for (var link : ordinary) {
                if (remaining <= 0) break;
                var container = SettlementConstructionTransfers.container(level, town, link);
                var rules = AssignedStorageService.observedContainerRules(level, link.position()).orElse(null);
                if (container == null || rules == null) continue;
                ordinaryContainers.put(link.position(), container);
                var reservedSlots = ordinarySlots.computeIfAbsent(link.position(), ignored -> new HashMap<>());
                var promised = ordinaryPromises.computeIfAbsent(link.position(), ignored -> new ArrayList<>());
                for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
                    var stack = container.getItem(slot);
                    if (!requirement.matches(stack)) continue;
                    int already = promised.stream().filter(value -> ItemStack.isSameItemSameComponents(value, stack)).mapToInt(ItemStack::getCount).sum();
                    int allocation = SettlementAllocationService.inspect(level, town, builder.getUUID(), project.id(), link.position(), stack).allowance();
                    int allowance = AssignedStorageService.observedTransferAllowance(level, link.position(), container, rules, stack,
                            VillagerFilterPolicy.TransferOperation.PROVIDE, promised);
                    int claims = AssignedStorageService.reservedItemCount(level, link.position(), null, VillagerFilterPolicy.TransferOperation.PROVIDE, stack);
                    int stored = 0;
                    for (int index = 0; index < container.getContainerSize(); index++) if (ItemStack.isSameItemSameComponents(container.getItem(index), stack)) stored += container.getItem(index).getCount();
                    int count = Math.min(remaining, Math.min(stack.getCount() - reservedSlots.getOrDefault(slot, 0),
                            Math.min(stored - claims - already, Math.min(allowance, allocation - already))));
                    if (count <= 0) continue;
                    var lot = new CompoundTag();
                    lot.putInt("Row", row);
                    lot.putBoolean("Ordinary", true);
                    lot.putUUID("Source", ordinaryIds.computeIfAbsent(link.position(), ignored -> UUID.randomUUID()));
                    lot.putLong("Position", link.position().asLong());
                    lot.putInt("Slot", slot);
                    lot.put("Stack", stack.copyWithCount(count).save(level.registryAccess()));
                    lot.putInt("Remaining", count);
                    lot.putBoolean("Collected", false);
                    lots.add(lot);
                    promised.add(stack.copyWithCount(count));
                    reservedSlots.merge(slot, count, Integer::sum);
                    remaining -= count;
                }
            }
            if (remaining > 0) {
                ledger.putString("Blocker", ordinary.stream().anyMatch(link -> SettlementConstructionTransfers.container(level, town, link) == null)
                        ? "requires_transactional_storage" : "missing_materials");
                ledger.putString("Missing", requirement.displayName() + " x" + remaining);
                town.changed();
                return false;
            }
        }
        // Planning above does not mutate inventories. The validated reservation commits on the server thread.
        for (var depot : slots.entrySet()) for (var slot : depot.getValue().entrySet()) depot.getKey().removeItem(slot.getKey(), slot.getValue());
        ledger.putInt("Version", 1);
        ledger.putBoolean("Reserved", true);
        ledger.putBoolean("Simplified", plan.entry().constructionCost().mode() != BuilderConstructionCost.Mode.EXACT);
        ledger.putInt("TotalUnits", plan.blocks().size());
        ledger.putInt("Units", 0);
        ledger.put("Quote", quote);
        ledger.put("Lots", lots);
        ledger.put("Paid", new ListTag());
        ledger.put("Storage", storage.save());
        ledger.put("Links", links.save());
        var debits = new ListTag();
        ordinarySlots.forEach((position, reservedSlots) -> {
            if (!reservedSlots.isEmpty()) debits.add(SettlementConstructionTransfers.debit(level, position,
                    ordinaryContainers.get(position), reservedSlots, ordinaryIds.get(position)));
        });
        ledger.put("Debits", debits);
        ledger.remove("Blocker");
        ledger.remove("Missing");
        town.changed();
        boolean ready = SettlementConstructionTransfers.advanceDebits(level, town, project, false);
        if (!ready && ledger.getString("Blocker").isEmpty()) ledger.putString("Blocker", "material_transfer_pending");
        return ready;
    }

    private static boolean authorized(SettlementStorageLinks storage, SettlementWorksiteLinks links, SettlementDepot depot, boolean supply) {
        if (storage.automatic()) return supply ? links.permitsSupply(depot.id()) : links.permitsOutput(depot.id());
        return storage.entries().stream().anyMatch(link -> link.position().equals(depot.position())
                && (supply ? link.purpose().supplies() : link.purpose().outputs()));
    }

    private static boolean accessible(ServerLevel level, SettlementRecord town, SettlementDepot depot, boolean supply) {
        if (!depot.purpose().permitsRole(HiredVillagerRole.BUILDER)) return false;
        // Unloaded settlement depots are authoritative saved inventories; live depots additionally respect locks.
        return !level.hasChunkAt(depot.position()) || SettlementStorageService.permitted(level, town, HiredVillagerRole.BUILDER,
                new SettlementStorageLinks.Link(depot.position(), supply ? SettlementStorageLinks.Purpose.SUPPLY : SettlementStorageLinks.Purpose.OUTPUT, 0));
    }

    public static BlockPos collectionTarget(Villager builder, CompoundTag state) {
        return collectionTarget(project(builder, state));
    }

    static BlockPos collectionTarget(SettlementProject project) {
        if (!reserved(project)) return null;
        for (var raw : project.materialEscrow().getList("Lots", Tag.TAG_COMPOUND)) {
            var lot = (CompoundTag) raw;
            if (lot.getInt("Remaining") > 0 && !lot.getBoolean("Collected")) return BlockPos.of(lot.getLong("Position"));
        }
        return null;
    }

    public static void collect(Villager builder, CompoundTag state, BlockPos position) {
        var town = town(builder, state);
        var project = project(builder, state);
        if (town != null && project != null) collect(town, project, builder.getUUID(), position);
    }

    static void collect(SettlementRecord town, SettlementProject project, UUID builder, BlockPos position) {
        for (var raw : project.materialEscrow().getList("Lots", Tag.TAG_COMPOUND)) {
            var lot = (CompoundTag) raw;
            if (lot.getLong("Position") == position.asLong() && !lot.getBoolean("Collected")) {
                lot.putBoolean("Collected", true);
                lot.putUUID("Collector", builder);
            }
        }
        town.changed();
    }

    /** Ceiling cumulative spending charges the first output and finishes at exactly the quoted total. */
    static int cumulative(int count, int units, int totalUnits) {
        if (count < 0 || units < 0 || totalUnits < 1) throw new IllegalArgumentException("Invalid cumulative cost");
        return (int) (((long) count * Math.min(units, totalUnits) + totalUnits - 1L) / totalUnits);
    }

    static boolean spend(ServerLevel level, SettlementRecord town, SettlementProject project,
                         BuilderWorker.PlacementGroup group, List<SettlementConstructionPlacement.Part> changes) {
        var ledger = project.materialEscrow();
        if (!reserved(project) || ledger.getBoolean("Returning") || collectionTarget(project) != null
                || ledger.getList("Debits", Tag.TAG_COMPOUND).stream().anyMatch(raw -> !((CompoundTag) raw).getBoolean("Done"))) return false;
        long position = group.materialPart().worldPos().asLong();
        var paid = ledger.getList("Paid", Tag.TAG_COMPOUND);
        if (paid.stream().anyMatch(raw -> ((CompoundTag) raw).getLong("Position") == position)) {
            ledger.putString("Blocker", "material_already_spent");
            town.changed();
            return false;
        }
        var quote = ledger.getList("Quote", Tag.TAG_COMPOUND);
        int units = Math.min(ledger.getInt("TotalUnits"), ledger.getInt("Units") + changes.size());
        var amounts = new int[quote.size()];
        if (ledger.getBoolean("Simplified")) {
            for (int row = 0; row < quote.size(); row++) {
                var line = quote.getCompound(row);
                amounts[row] = cumulative(line.getInt("Count"), units, ledger.getInt("TotalUnits")) - line.getInt("Spent");
            }
        } else {
            for (var change : changes) {
                if (BuilderStructureScanner.canTransformExisting(change.before(), change.after())) continue;
                for (var requirement : BuilderStructureScanner.exactBlockMaterials(change.after())) {
                    int row = -1;
                    for (int index = 0; index < quote.size(); index++) {
                        if (quote.getCompound(index).getString("Id").equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(requirement.getItem()).toString())) { row = index; break; }
                    }
                    if (row < 0) return false;
                    amounts[row] += requirement.getCount();
                }
            }
        }
        if (!debit(level, ledger, amounts)) return false;
        var receipt = new CompoundTag();
        receipt.putLong("Position", position);
        paid.add(receipt);
        ledger.putInt("Units", units);
        ledger.remove("Blocker");
        town.changed();
        return true;
    }

    private static boolean debit(ServerLevel level, CompoundTag ledger, int[] amounts) {
        var lots = ledger.getList("Lots", Tag.TAG_COMPOUND);
        var quote = ledger.getList("Quote", Tag.TAG_COMPOUND);
        for (int row = 0; row < amounts.length; row++) {
            int available = 0;
            for (var raw : lots) { var lot = (CompoundTag) raw; if (!lot.getBoolean("Remainder") && lot.getInt("Row") == row) available += lot.getInt("Remaining"); }
            if (amounts[row] < 0 || available < amounts[row]) return false;
        }
        var remainders = new ListTag();
        for (int row = 0; row < amounts.length; row++) {
            int remaining = amounts[row];
            for (var raw : lots) {
                var lot = (CompoundTag) raw;
                if (lot.getBoolean("Remainder") || lot.getInt("Row") != row) continue;
                int count = Math.min(remaining, lot.getInt("Remaining"));
                if (count > 0) {
                    var stack = ItemStack.parseOptional(level.registryAccess(), lot.getCompound("Stack"));
                    var remainder = stack.is(Items.POWDER_SNOW_BUCKET) ? new ItemStack(Items.BUCKET) : stack.getCraftingRemainingItem();
                    if (!remainder.isEmpty()) {
                        int produced = Math.multiplyExact(count, remainder.getCount());
                        while (produced > 0) {
                            int batch = Math.min(99, produced);
                            var returned = lot.copy();
                            returned.putBoolean("Remainder", true);
                            returned.putInt("Produced", batch);
                            returned.putInt("Refunded", 0);
                            returned.put("Stack", remainder.copyWithCount(batch).save(level.registryAccess()));
                            returned.putInt("Remaining", batch);
                            remainders.add(returned);
                            produced -= batch;
                        }
                    }
                }
                lot.putInt("Remaining", lot.getInt("Remaining") - count);
                remaining -= count;
            }
            var line = quote.getCompound(row);
            line.putInt("Spent", line.getInt("Spent") + amounts[row]);
        }
        lots.addAll(remainders);
        return true;
    }

    public static boolean place(Villager builder, CompoundTag state, BuilderWorker.PlacementGroup group) {
        var town = town(builder, state);
        var project = project(builder, state);
        if (town == null || project == null || !reserved(project) || !project.active()) return false;
        var level = (ServerLevel) builder.level();
        project.observeSite(town.workplace(project.area()));
        var site = project.site();
        if (site == null || site.conflict()) return false;
        var changes = new ArrayList<SettlementConstructionPlacement.Part>();
        for (var part : group.parts()) {
            var before = level.getBlockState(part.worldPos());
            if (BuilderStructureScanner.sameSchematicState(before, part.block().state())) continue;
            if (!site.observed(part.worldPos()) || site.getBlockState(part.worldPos()) != before || before.hasBlockEntity()) return false;
            changes.add(new SettlementConstructionPlacement.Part(part.worldPos(), before, part.block().state(), part.block().blockEntityTag()));
        }
        if (changes.isEmpty()) return true;
        var placement = new SettlementConstructionPlacement(level, changes).requireJournal();
        if (!spend(level, town, project, group, changes)) return false;
        site.queue(placement);
        SettlementConstructionService.worked(town, project, builder);
        SettlementConstructionSiteService.refresh(level, site);
        town.changed();
        return true;
    }

    public static void complete(ServerLevel level, SettlementRecord town, SettlementProject project) {
        if (reserved(project) && project.materialEscrow().getBoolean("Simplified")) {
            var ledger = project.materialEscrow();
            var quote = ledger.getList("Quote", Tag.TAG_COMPOUND);
            int[] remainder = new int[quote.size()];
            for (int row = 0; row < quote.size(); row++) remainder[row] = quote.getCompound(row).getInt("Count") - quote.getCompound(row).getInt("Spent");
            if (!debit(level, ledger, remainder)) throw new IllegalStateException("Construction quote escrow is incomplete");
            ledger.putInt("Units", ledger.getInt("TotalUnits"));
            town.changed();
        }
        refund(level, town, project);
    }

    public static boolean refundPending(SettlementProject project) {
        return reserved(project) && project.materialEscrow().getList("Lots", Tag.TAG_COMPOUND).stream()
                .anyMatch(raw -> ((CompoundTag) raw).getInt("Remaining") > 0);
    }

    public static void refund(ServerLevel level, SettlementRecord town, SettlementProject project) {
        if (!reserved(project)) return;
        var ledger = project.materialEscrow();
        ledger.putBoolean("Returning", true);
        if (!SettlementConstructionTransfers.advanceDebits(level, town, project, true)) {
            ledger.putString("Blocker", "refund_pending");
            town.changed();
            return;
        }
        SettlementConstructionTransfers.advanceRefunds(level, town, project);
        var storage = SettlementStorageLinks.load(ledger.getCompound("Storage"));
        var links = SettlementWorksiteLinks.load(ledger.getCompound("Links"));
        var lots = ledger.getList("Lots", Tag.TAG_COMPOUND);
        for (int lotIndex = 0; lotIndex < lots.size(); lotIndex++) {
            var lot = lots.getCompound(lotIndex);
            int remaining = lot.getInt("Remaining");
            if (remaining == 0 || SettlementConstructionTransfers.refundInFlight(project, lotIndex)) continue;
            var original = ItemStack.parseOptional(level.registryAccess(), lot.getCompound("Stack"));
            if (original.isEmpty()) { ledger.putString("Blocker", "refund_invalid_item"); continue; }
            var sourcePosition = BlockPos.of(lot.getLong("Position"));
            if (lot.getBoolean("Ordinary") && storage.entries().stream().anyMatch(link -> link.position().equals(sourcePosition) && link.purpose().supplies())
                    && SettlementConstructionTransfers.refund(level, town, project, lotIndex,
                            new SettlementStorageLinks.Link(sourcePosition, SettlementStorageLinks.Purpose.SUPPLY, 0), original.copyWithCount(remaining))) continue;
            var destinations = new ArrayList<SettlementDepot>();
            var source = town.depots().get(lot.getUUID("Source"));
            if (source != null && SettlementStock.depots(town, HiredVillagerRole.BUILDER).contains(source)
                    && authorized(storage, links, source, true)) destinations.add(source);
            for (var depot : SettlementStock.outputs(town, HiredVillagerRole.BUILDER)) {
                if (!destinations.contains(depot) && authorized(storage, links, depot, false)) destinations.add(depot);
            }
            for (var depot : destinations) {
                if (!depot.rulesObserved() || !accessible(level, town, depot, depot == source)) continue;
                var stack = original.copyWithCount(remaining);
                int allowance = AssignedStorageService.observedTransferAllowance(level, depot.position(), depot, depot.observedRules(),
                        stack, VillagerFilterPolicy.TransferOperation.RECEIVE);
                int offered = Math.min(remaining, allowance);
                if (offered <= 0) continue;
                var rest = depot.deposit(stack.copyWithCount(offered));
                int returned = offered - rest.getCount();
                remaining -= returned;
                recordRefund(ledger, lot, returned);
                lot.putInt("Remaining", remaining);
                town.changed();
                if (remaining == 0) break;
            }
            if (remaining > 0) for (var link : storage.entries()) {
                if (link.purpose().outputs() && town.depots().values().stream().noneMatch(depot -> depot.position().equals(link.position()))
                        && SettlementConstructionTransfers.refund(level, town, project, lotIndex, link, original.copyWithCount(remaining))) break;
            }
        }
        if (refundPending(project)) ledger.putString("Blocker", "refund_pending"); else ledger.remove("Blocker");
        town.changed();
    }

    static void recordRefund(CompoundTag ledger, CompoundTag lot, int count) {
        var line = lot.getBoolean("Remainder") ? lot : ledger.getList("Quote", Tag.TAG_COMPOUND).getCompound(lot.getInt("Row"));
        line.putInt("Refunded", line.getInt("Refunded") + count);
    }

    public static void validate(CompoundTag ledger) {
        if (ledger.isEmpty() || !ledger.getBoolean("Reserved")) return;
        if (ledger.getInt("Version") != 1 || ledger.getInt("TotalUnits") < 1 || ledger.getInt("Units") < 0
                || ledger.getInt("Units") > ledger.getInt("TotalUnits")
                || !ledger.contains("Quote", Tag.TAG_LIST) || !ledger.contains("Lots", Tag.TAG_LIST)
                || !ledger.contains("Paid", Tag.TAG_LIST)) throw new IllegalArgumentException("Invalid construction material ledger");
        var quote = ledger.getList("Quote", Tag.TAG_COMPOUND);
        var lots = ledger.getList("Lots", Tag.TAG_COMPOUND);
        if (quote.size() > BuilderConstructionCost.MAX_INGREDIENTS || lots.size() > 65536
                || ledger.getBoolean("Simplified") && quote.isEmpty()) throw new IllegalArgumentException("Construction escrow has an invalid size");
        int[] remaining = new int[quote.size()];
        for (var raw : lots) {
            var lot = (CompoundTag) raw;
            int row = lot.getInt("Row");
            if (row < 0 || row >= quote.size() || !lot.hasUUID("Source") || !lot.contains("Position", Tag.TAG_LONG)
                    || !lot.contains("Stack", Tag.TAG_COMPOUND) || lot.getInt("Remaining") < 0
                    || lot.getInt("Remaining") > 99 || lot.getInt("Slot") < 0 || lot.getInt("Slot") >= SettlementDepot.SLOT_COUNT)
                throw new IllegalArgumentException("Invalid construction escrow lot");
            if (lot.getBoolean("Remainder")) {
                if (lot.getInt("Produced") < 1 || lot.getInt("Produced") > 99 || lot.getInt("Refunded") < 0
                        || lot.getInt("Remaining") + lot.getInt("Refunded") != lot.getInt("Produced"))
                    throw new IllegalArgumentException("Invalid construction container remainder");
            } else remaining[row] = Math.addExact(remaining[row], lot.getInt("Remaining"));
        }
        for (int row = 0; row < quote.size(); row++) {
            var line = quote.getCompound(row);
            if (net.minecraft.resources.ResourceLocation.tryParse(line.getString("Id")) == null
                    || line.getInt("Count") < 1 || line.getInt("Count") > BuilderConstructionCost.MAX_COUNT
                    || line.getInt("Spent") < 0 || line.getInt("Refunded") < 0
                    || (long) line.getInt("Spent") + remaining[row] + line.getInt("Refunded") != line.getInt("Count"))
                throw new IllegalArgumentException("Invalid construction escrow accounting");
        }
        var positions = new java.util.HashSet<Long>();
        for (var raw : ledger.getList("Paid", Tag.TAG_COMPOUND)) {
            var receipt = (CompoundTag) raw;
            if (!receipt.contains("Position", Tag.TAG_LONG) || !positions.add(receipt.getLong("Position")))
                throw new IllegalArgumentException("Invalid construction material receipt");
        }
        SettlementStorageLinks.load(ledger.getCompound("Storage"));
        SettlementWorksiteLinks.load(ledger.getCompound("Links"));
        SettlementConstructionTransfers.validate(ledger);
    }

    private static SettlementRecord town(Villager builder, CompoundTag state) {
        var task = BuilderTaskState.task(state);
        return builder.level() instanceof ServerLevel level && task.hasUUID("SettlementTown")
                ? SettlementSavedData.get(level).get(new VillageAllegianceId(task.getUUID("SettlementTown"))).orElse(null) : null;
    }

    private static SettlementProject project(Villager builder, CompoundTag state) {
        var town = town(builder, state);
        return town == null ? null : BuilderTaskState.jobId(state).map(town.projects()::get).orElse(null);
    }
}
