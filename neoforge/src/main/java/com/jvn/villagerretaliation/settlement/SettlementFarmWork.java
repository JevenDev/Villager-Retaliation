package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredTemporaryWorkService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.VillagerWorkAuthority;
import com.jvn.villagerretaliation.interaction.work.FarmerHoeRequirement;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryQueue;
import com.jvn.villagerretaliation.interaction.work.HiredTemporaryTask;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class SettlementFarmWork {
    public static final String TAG = "SettlementFarmPreparation";
    public record Assignment(Villager worker, String error) {}
    public record Step(BlockPos position, boolean water, String error, boolean finished) {}
    private SettlementFarmWork() {}

    public static Assignment worker(ServerLevel level, SettlementRecord town, SettlementFarmTerrain.Preview preview) {
        return worker(level, town, preview, false, SettlementFarmJob.AUTOMATIC);
    }

    public static Assignment worker(ServerLevel level, SettlementRecord town, SettlementFarmTerrain.Preview preview,
                                    boolean includeBusy, UUID requested) {
        boolean farmer = false;
        boolean hoe = false;
        boolean needsHoe = preview.count(SettlementFarmTerrain.Kind.TILL) > 0;
        boolean needsWater = preview.count(SettlementFarmTerrain.Kind.WATER) > 0;
        var candidates = new java.util.TreeSet<UUID>(town.duties().keySet());
        candidates.addAll(com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level)
                .residentRoster(town.id(), level.getGameTime()).stream().map(resident -> resident.id()).toList());
        var ordered = candidates.stream().sorted(Comparator.comparingInt((UUID id) -> {
            var area = town.workplace(town.duties().get(id));
            return area == null ? 1 : preview.cells().stream().allMatch(cell -> area.contains(cell.position())
                    || area.contains(cell.position().above())) ? 0 : 2;
        }).thenComparing(Comparator.naturalOrder())).toList();
        for (var id : ordered) {
            if (!requested.equals(SettlementFarmJob.AUTOMATIC) && !requested.equals(id)) continue;
            if (!(level.getEntity(id) instanceof Villager villager) || !villager.isAlive() || villager.isBaby()
                    || villager.getVillagerData().getProfession() != VillagerProfession.FARMER
                    || com.jvn.villagerretaliation.combat.downed.VillagerDownedService.isDowned(villager)
                    || com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.isTrial(villager)) continue;
            var authority = VillagerWorkAuthority.resolve(level, villager);
            if (SettlementWorkforceService.hasPersonalCommitment(villager)
                    || SettlementWorkforceService.home(level, villager).filter(home -> home == town).isEmpty()
                    || town.duties().containsKey(id) && (!authority.active() || !authority.civic()
                    || authority.role() != HiredVillagerRole.FARMING)) continue;
            var state = HiredWorkStateStore.state(villager);
            if (town.duties().containsKey(id) && !state.getBoolean("Enabled")
                    || !includeBusy && (state.contains(HiredTemporaryQueue.STATE_TAG) || state.contains(HiredTemporaryQueue.PENDING_TAG))) continue;
            farmer = true;
            var inventory = HiredJobInventory.getJobInventory(villager);
            boolean storage = com.jvn.villagerretaliation.interaction.HiredJobSetting.USE_STORAGE.read(state) != 0;
            boolean unassigned = !town.duties().containsKey(id);
            if (needsHoe && inventory.findTool(FarmerHoeRequirement::isHoe).isEmpty() && (!storage
                    || com.jvn.villagerretaliation.inventory.AssignedStorageService.nearestAssignedToolStoragePosContaining(
                            level, villager, FarmerHoeRequirement::isHoe) == null
                    && (!unassigned || !sharedSupply(level, town, villager, FarmerHoeRequirement::isHoe)))) continue;
            hoe = true;
            if (!needsWater) return new Assignment(villager, "");
            int water = inventory.supplySlots().stream().map(inventory::getItem).filter(stack -> (stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET)))
                    .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            if (storage) water += com.jvn.villagerretaliation.inventory.AssignedStorageService.productionSupplies(villager).stream()
                    .filter(stack -> (stack.is(Items.WATER_BUCKET) || stack.is(Items.BUCKET))).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            if (water >= 1 || storage && unassigned && sharedSupply(level, town, villager,
                    stack -> stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET))) return new Assignment(villager, "");
        }
        return new Assignment(null, !farmer ? "An available farmer must belong to this settlement"
                : !hoe ? "Needs a usable hoe in farmer tools or accessible supply storage"
                : "Needs a bucket in farmer supplies or accessible supply storage");
    }

    private static boolean sharedSupply(ServerLevel level, SettlementRecord town, Villager villager,
                                        java.util.function.Predicate<net.minecraft.world.item.ItemStack> matches) {
        for (var depot : SettlementStock.supplies(town, HiredVillagerRole.FARMING)) {
            if (!level.hasChunkAt(depot.position()) || SettlementSupplyStock.contents(level, town, depot.position()) == null) continue;
            for (int slot = 0; slot < depot.getContainerSize(); slot++) {
                var stack = depot.getItem(slot);
                if (stack.isEmpty() || !matches.test(stack)
                        || !com.jvn.villagerretaliation.util.VillagerEquipmentDurability.isUsable(stack)
                        || !com.jvn.villagerretaliation.inventory.VillagerItemFilterService.mayWithdraw(villager, stack)) continue;
                if (SettlementLogisticsService.framedAllowance(level, depot, stack,
                        com.jvn.villagerretaliation.item.VillagerFilterPolicy.TransferOperation.PROVIDE, false) > 0) return true;
            }
        }
        return false;
    }

    public static String start(ServerPlayer player, SettlementRecord town, SettlementFarmTerrain.Preview preview) {
        if (!preview.error().isEmpty() || SettlementSavedData.get(player.serverLevel()).readOnly()
                || !town.permits(player.getUUID(), SettlementPermission.BUILD)
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return "Farmland work is not permitted";
        var cells = preview.cells();
        if (cells.isEmpty()) return "No farmland was selected";
        var min = cells.getFirst().position();
        var max = min;
        for (var cell : cells) {
            min = com.jvn.villagerretaliation.interaction.HiredWorkArea.minPos(min, cell.position());
            max = com.jvn.villagerretaliation.interaction.HiredWorkArea.maxPos(max, cell.position());
        }
        return start(player, town, preview, new SettlementFarm(min, max));
    }

    public static String start(ServerPlayer player, SettlementRecord town, SettlementFarmTerrain.Preview preview, SettlementFarm farm) {
        return start(player, town, preview, farm, null, null);
    }

    static SettlementArea previous(SettlementRecord town, SettlementFarmTerrain.Preview preview, SettlementFarm farm, UUID areaId) {
        if (areaId != null) return town.areas().get(areaId);
        return town.areas().values().stream().filter(area -> area.type() == SettlementAreaType.FARM
                && area.min().getX() == farm.minX() && area.max().getX() == farm.maxX()
                && area.min().getZ() == farm.minZ() && area.max().getZ() == farm.maxZ()
                && preview.cells().stream().allMatch(cell -> area.contains(cell.position()) || area.contains(cell.position().above())))
                .findFirst().orElse(null);
    }

    static SettlementArea plot(SettlementFarmTerrain.Preview preview, SettlementFarm farm, SettlementArea previous, SettlementCropPlan crops) {
        int low = preview.cells().stream().mapToInt(cell -> cell.position().getY()).min().orElseThrow();
        int high = preview.cells().stream().mapToInt(cell -> cell.position().getY()).max().orElseThrow();
        return new SettlementArea(previous == null ? UUID.randomUUID() : previous.id(), SettlementAreaType.FARM,
                previous == null ? "Farm plot" : previous.name(), new BlockPos(farm.minX(), low, farm.minZ()),
                new BlockPos(farm.maxX(), high + 2, farm.maxZ()), previous == null ? 5 : previous.priority(),
                previous == null || previous.enabled(), crops == null ? previous == null ? SettlementCropPlan.NONE : previous.crops() : crops, false, farm);
    }

    public static String start(ServerPlayer player, SettlementRecord town, SettlementFarmTerrain.Preview preview, SettlementFarm farm,
                               UUID areaId, SettlementCropPlan crops) {
        var level = player.serverLevel();
        if (preview.cells().isEmpty()) return "No farmland was selected";
        if (!preview.error().isEmpty() || SettlementSavedData.get(level).readOnly()
                || !town.permits(player.getUUID(), SettlementPermission.BUILD)
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return "Farmland work is not permitted";
        var assignment = worker(level, town, preview, true, SettlementFarmJob.AUTOMATIC);
        if (assignment.worker() == null) return assignment.error();
        var work = new ArrayList<>(preview.cells().stream()
                .filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER || cell.kind() == SettlementFarmTerrain.Kind.TILL).toList());
        work.sort(Comparator.comparingInt(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER ? 0 : 1));
        var previous = previous(town, preview, farm, areaId);
        if (areaId != null && (previous == null || previous.type() != SettlementAreaType.FARM)) return "Farm plot is no longer available";
        SettlementArea area;
        try {
            area = plot(preview, farm, previous, crops);
        } catch (IllegalArgumentException exception) {
            return "Farm plot exceeds workplace size or height limits";
        }
        var min = area.min();
        var max = area.max();
        if (!town.putArea(player.getUUID(), area)) return "Farm plot overlaps another workplace or leaves settlement land";
        var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.PREPARE_FARM, min, max, 1);
        SettlementBuildingService.enqueue(level, town, area);
        var job = new CompoundTag();
        job.putUUID("Task", task.id());
        job.putBoolean("ProgressVersion", true);
        var cells = new ListTag();
        for (var cell : work) {
            var tag = new CompoundTag();
            tag.putLong("Pos", cell.position().asLong());
            tag.putBoolean("Water", cell.kind() == SettlementFarmTerrain.Kind.WATER);
            tag.put("Soil", NbtUtils.writeBlockState(cell.soil()));
            cells.add(tag);
        }
        job.put("Cells", cells);
        var project = new SettlementFarmJob(area.id(), player.getUUID(), min, max, area.priority(), job);
        if (!town.addFarmJob(project)) {
            if (previous == null) town.removeArea(player.getUUID(), area.id());
            else town.putArea(player.getUUID(), previous);
            return "Farm plot already has preparation work or the farm queue is full";
        }
        if (!SettlementFarmJobService.activate(level, town, project, assignment.worker()))
            project.blocker("Waiting for an available farmer");
        return "";
    }

    public static Step next(ServerLevel level, CompoundTag state) {
        return next(level, state, true);
    }

    public static Step next(ServerLevel level, CompoundTag state, boolean waterAvailable) {
        var queue = HiredTemporaryWorkService.queue(state);
        var job = state.getCompound(TAG);
        if (queue == null || queue.current().kind() != HiredTemporaryTask.Kind.PREPARE_FARM
                || !job.hasUUID("Task") || !job.getUUID("Task").equals(queue.current().id()))
            return new Step(null, false, "interaction.work.farmland.invalid", false);
        var cells = job.getList("Cells", Tag.TAG_COMPOUND);
        int cursor = job.getInt("Cursor");
        if (cells.size() > SettlementFarm.MAX_CELLS || cursor < 0 || cursor > cells.size())
            return new Step(null, false, "interaction.work.farmland.invalid", false);
        if (!job.getBoolean("ProgressVersion")) {
            for (int i = 0; i < cursor; i++) cells.getCompound(i).putBoolean("Done", true);
            job.putBoolean("ProgressVersion", true);
        }
        Step blocked = null;
        int waterCount = 0;
        while (waterCount < cells.size() && cells.getCompound(waterCount).getBoolean("Water")) waterCount++;
        int tillCount = cells.size() - waterCount;
        int tillCursor = tillCount == 0 ? 0 : Math.floorMod(job.getInt("TillScanCursor"), tillCount);
        int inspected = 0;
        for (int offset = 0; offset < cells.size(); offset++) {
            int i = offset < waterCount ? offset : waterCount + (tillCursor + offset - waterCount) % tillCount;
            var cell = cells.getCompound(i);
            if (cell.getBoolean("Done")) continue;
            var pos = BlockPos.of(cell.getLong("Pos"));
            boolean water = cell.getBoolean("Water");
            if (!water && inspected++ >= 64) {
                job.putInt("TillScanCursor", i - waterCount);
                return blocked == null ? new Step(null, false, "interaction.work.farming.searching_scan", false) : blocked;
            }
            if (!queue.current().includes(pos)) return new Step(null, water, "interaction.work.farmland.invalid", false);
            String error = "";
            if (!level.hasChunkAt(pos)) error = "interaction.work.temporary.unloaded";
            else {
                var soil = level.getBlockState(pos);
                if (water && soil.is(Blocks.WATER) && soil.getFluidState().isSource()
                        || !water && soil.is(Blocks.FARMLAND) && SettlementFarmTerrain.hydrated(level, pos)) {
                    cell.putBoolean("Done", true);
                    continue;
                }
                var original = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), cell.getCompound("Soil"));
                if (!soil.equals(original) || !SettlementFarmTerrain.soil(soil) || !SettlementFarmTerrain.clear(level, pos)) {
                    if (!water) {
                        cell.putBoolean("Done", true);
                        continue;
                    }
                    error = "interaction.work.farmland.water_changed";
                } else if (water && !SettlementFarmTerrain.contained(level, pos)) error = "interaction.work.farmland.water_changed";
                else if (water && !waterAvailable) error = "interaction.work.farmland.needs_water";
                else if (!water && !SettlementFarmTerrain.hydrated(level, pos)) error = "interaction.work.farmland.dry";
            }
            if (!error.isEmpty()) {
                if (blocked == null) blocked = new Step(pos, water, error, false);
                continue;
            }
            job.putInt("Cursor", i);
            if (!water) job.putInt("TillScanCursor", i - waterCount);
            return new Step(pos, water, "", false);
        }
        return blocked == null ? new Step(null, false, "", true) : blocked;
    }

    public static void advance(CompoundTag state) {
        var job = state.getCompound(TAG);
        job.getList("Cells", Tag.TAG_COMPOUND).getCompound(job.getInt("Cursor")).putBoolean("Done", true);
    }
}
