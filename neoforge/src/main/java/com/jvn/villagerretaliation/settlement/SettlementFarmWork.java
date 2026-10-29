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
        boolean farmer = false;
        for (var id : town.duties().keySet().stream().sorted().toList()) {
            if (!(level.getEntity(id) instanceof Villager villager) || !villager.isAlive() || villager.isBaby()
                    || villager.getVillagerData().getProfession() != VillagerProfession.FARMER
                    || com.jvn.villagerretaliation.combat.downed.VillagerDownedService.isDowned(villager)
                    || com.jvn.villagerretaliation.quest.tutorial.TutorialWorkService.isTrial(villager)) continue;
            var authority = VillagerWorkAuthority.resolve(level, villager);
            if (!authority.active() || !authority.civic() || authority.role() != HiredVillagerRole.FARMING
                    || SettlementWorkforceService.duty(villager).filter(duty -> duty.town() == town).isEmpty()) continue;
            var state = HiredWorkStateStore.state(villager);
            if (!state.getBoolean("Enabled") || state.contains(HiredTemporaryQueue.STATE_TAG) || state.contains(HiredTemporaryQueue.PENDING_TAG)) continue;
            farmer = true;
            var inventory = HiredJobInventory.getJobInventory(villager);
            boolean storage = state.getBoolean("UseAssignedStorageForSupplies");
            if (inventory.findTool(FarmerHoeRequirement::isHoe).isEmpty() && (!storage
                    || com.jvn.villagerretaliation.inventory.AssignedStorageService.nearestAssignedToolStoragePosContaining(
                            level, villager, FarmerHoeRequirement::isHoe) == null)) continue;
            int water = inventory.supplySlots().stream().map(inventory::getItem).filter(stack -> stack.is(Items.WATER_BUCKET))
                    .mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            if (storage) water += com.jvn.villagerretaliation.inventory.AssignedStorageService.productionSupplies(villager).stream()
                    .filter(stack -> stack.is(Items.WATER_BUCKET)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            if (water >= preview.count(SettlementFarmTerrain.Kind.WATER)) return new Assignment(villager, "");
        }
        return new Assignment(null, farmer ? preview.materials() : "Assign an available farmer to this settlement first");
    }

    public static String start(ServerPlayer player, SettlementRecord town, SettlementFarmTerrain.Preview preview) {
        if (!preview.error().isEmpty() || SettlementSavedData.get(player.serverLevel()).readOnly()
                || !town.permits(player.getUUID(), SettlementPermission.BUILD)
                || !town.permits(player.getUUID(), SettlementPermission.STAFF)) return "Farmland work is not permitted";
        var assignment = worker(player.serverLevel(), town, preview);
        if (assignment.worker() == null) return assignment.error();
        var work = new ArrayList<>(preview.cells().stream()
                .filter(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER || cell.kind() == SettlementFarmTerrain.Kind.TILL).toList());
        work.sort(Comparator.comparingInt(cell -> cell.kind() == SettlementFarmTerrain.Kind.WATER ? 0 : 1));
        if (work.isEmpty()) return "No farmland work is needed";
        var min = work.getFirst().position();
        var max = min;
        for (var cell : work) {
            min = com.jvn.villagerretaliation.interaction.HiredWorkArea.minPos(min, cell.position());
            max = com.jvn.villagerretaliation.interaction.HiredWorkArea.maxPos(max, cell.position());
        }
        var task = new HiredTemporaryTask(UUID.randomUUID(), HiredTemporaryTask.Kind.PREPARE_FARM, min, max.above(2), 1);
        String error = HiredTemporaryWorkService.submit(player, assignment.worker(), task, false);
        if (!error.isEmpty()) return error;
        var job = new CompoundTag();
        job.putUUID("Task", task.id());
        var cells = new ListTag();
        for (var cell : work) {
            var tag = new CompoundTag();
            tag.putLong("Pos", cell.position().asLong());
            tag.putBoolean("Water", cell.kind() == SettlementFarmTerrain.Kind.WATER);
            tag.put("Soil", NbtUtils.writeBlockState(cell.soil()));
            cells.add(tag);
        }
        job.put("Cells", cells);
        HiredWorkStateStore.state(assignment.worker()).put(TAG, job);
        return "";
    }

    public static Step next(ServerLevel level, CompoundTag state) {
        var queue = HiredTemporaryWorkService.queue(state);
        var job = state.getCompound(TAG);
        if (queue == null || queue.current().kind() != HiredTemporaryTask.Kind.PREPARE_FARM
                || !job.hasUUID("Task") || !job.getUUID("Task").equals(queue.current().id()))
            return new Step(null, false, "interaction.work.farmland.invalid", false);
        var cells = job.getList("Cells", Tag.TAG_COMPOUND);
        int cursor = job.getInt("Cursor");
        if (cells.isEmpty() || cells.size() > SettlementFarm.MAX_CELLS || cursor < 0 || cursor > cells.size())
            return new Step(null, false, "interaction.work.farmland.invalid", false);
        while (cursor < cells.size()) {
            var cell = cells.getCompound(cursor);
            var pos = BlockPos.of(cell.getLong("Pos"));
            boolean water = cell.getBoolean("Water");
            if (!queue.current().includes(pos)) return new Step(null, water, "interaction.work.farmland.invalid", false);
            if (!level.hasChunkAt(pos)) return new Step(pos, water, "interaction.work.temporary.unloaded", false);
            var soil = level.getBlockState(pos);
            if (water && soil.is(Blocks.WATER) && soil.getFluidState().isSource()
                    || !water && soil.is(Blocks.FARMLAND)) {
                job.putInt("Cursor", ++cursor);
                continue;
            }
            var original = NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK), cell.getCompound("Soil"));
            if (!soil.equals(original) || !SettlementFarmTerrain.soil(soil) || !SettlementFarmTerrain.clear(level, pos)) {
                if (water) return new Step(pos, true, "interaction.work.farmland.water_changed", false);
                job.putInt("Cursor", ++cursor);
                continue;
            }
            if (water && !SettlementFarmTerrain.contained(level, pos))
                return new Step(pos, true, "interaction.work.farmland.water_changed", false);
            if (!water && !SettlementFarmTerrain.hydrated(level, pos))
                return new Step(pos, false, "interaction.work.farmland.dry", false);
            return new Step(pos, water, "", false);
        }
        for (var raw : cells) {
            var cell = (CompoundTag) raw;
            var pos = BlockPos.of(cell.getLong("Pos"));
            if (!cell.getBoolean("Water") && level.hasChunkAt(pos) && level.getBlockState(pos).is(Blocks.FARMLAND)
                    && !SettlementFarmTerrain.hydrated(level, pos)) return new Step(pos, false, "interaction.work.farmland.dry", false);
        }
        return new Step(null, false, "", true);
    }

    public static void advance(CompoundTag state) {
        var job = state.getCompound(TAG);
        job.putInt("Cursor", job.getInt("Cursor") + 1);
    }
}
