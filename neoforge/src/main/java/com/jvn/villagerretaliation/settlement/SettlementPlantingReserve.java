package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop;
import com.jvn.villagerretaliation.inventory.HiredJobInventory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

public final class SettlementPlantingReserve {
    private SettlementPlantingReserve() { }

    static Map<Crop, Integer> targets(SettlementRecord town, SettlementArea area) {
        var targets = new EnumMap<Crop, Integer>(Crop.class);
        if (area == null || !area.enabled() || !town.owns(area) || area.crops().cycles() == 0) return targets;
        for (var plot : town.fields().values()) {
            if (!SettlementFieldService.serves(area, plot) || plot.conflict()
                    || !SettlementFieldPlot.supportedSoil(plot.soil())) continue;
            var planting = plot.planting(area.crops());
            Crop crop = planting != null ? planting.crop() : area.crops().enabled() ? area.crops().crop(area, plot.position())
                    : SettlementPlanting.crop(plot.crop());
            if (crop == Crop.ANY) {
                var plan = town.worksitePlans().get(area.id());
                if (plan != null) crop = Crop.values()[plan.settings().options().get(SettlementWorkerSetting.CROP_SELECTION)];
            }
            if (!workers(town, area, crop).isEmpty())
                targets.merge(crop, area.crops().cycles(), Integer::sum);
        }
        return targets;
    }

    private static SettlementWorkerSettings settings(SettlementRecord town, UUID worker) {
        var configured = town.effectiveWorkerSettings(worker);
        if (configured != null) return configured;
        var observed = town.workers().get(worker);
        return SettlementWorkerSettings.capture(com.jvn.villagerretaliation.interaction.HiredVillagerRole.FARMING,
                observed == null ? new net.minecraft.nbt.CompoundTag() : observed.settings());
    }

    static List<UUID> workers(SettlementRecord town, SettlementArea area, Crop crop) {
        return town.duties().entrySet().stream().filter(entry -> area.id().equals(entry.getValue()))
                .map(Map.Entry::getKey).filter(id -> {
                    var settings = settings(town, id);
                    if (settings == null || !settings.enabled() || settings.options().get(SettlementWorkerSetting.PLANT_CROPS) == 0) return false;
                    int selected = settings.options().get(SettlementWorkerSetting.CROP_SELECTION);
                    return crop == Crop.ANY || selected == 0 || selected == crop.ordinal();
                }).sorted().toList();
    }

    static int quota(SettlementRecord town, SettlementArea area, UUID worker, ItemStack sample) {
        if (area == null || area.crops().cycles() == 0) return -1;
        for (var target : targets(town, area).entrySet()) if (target.getKey() != Crop.ANY && sample.is(target.getKey().seed())) {
            var workers = workers(town, area, target.getKey());
            int index = workers.indexOf(worker);
            if (index < 0) return 0;
            return target.getValue() / workers.size() + (index < target.getValue() % workers.size() ? 1 : 0);
        }
        return 0;
    }

    private static int genericQuota(SettlementRecord town, SettlementArea area, UUID worker) {
        var workers = workers(town, area, Crop.ANY);
        int index = workers.indexOf(worker);
        if (index < 0) return 0;
        int target = targets(town, area).getOrDefault(Crop.ANY, 0);
        return target / workers.size() + (index < target % workers.size() ? 1 : 0);
    }

    static int needed(SettlementRecord town, SettlementArea area, UUID worker, List<ItemStack> supplies, ItemStack sample) {
        if (area == null || area.crops().cycles() == 0) return -1;
        var settings = settings(town, worker);
        if (settings == null || !settings.enabled() || settings.options().get(SettlementWorkerSetting.PLANT_CROPS) == 0) return 0;
        int selected = settings.options().get(SettlementWorkerSetting.CROP_SELECTION);
        if (selected != 0 && !sample.is(Crop.values()[selected].seed())) return 0;
        int carried = supplies.stream().filter(stack -> stack.is(sample.getItem())).mapToInt(ItemStack::getCount).sum();
        int extra = 0;
        for (var crop : Crop.values()) if (crop != Crop.ANY) {
            int count = supplies.stream().filter(stack -> stack.is(crop.seed())).mapToInt(ItemStack::getCount).sum();
            extra += Math.max(0, count - Math.max(0, quota(town, area, worker, new ItemStack(crop.seed()))));
        }
        return Math.max(0, quota(town, area, worker, sample) - carried)
                + Math.max(0, genericQuota(town, area, worker) - extra);
    }

    public static int needed(Villager villager, HiredJobInventory inventory, ItemStack sample) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        return duty == null ? -1 : needed(duty.town(), duty.area(), villager.getUUID(),
                inventory.supplySlots().stream().map(inventory::getItem).toList(), sample);
    }

    public static int collectionAmount(Villager villager, Crop crop) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.area().crops().cycles() == 0) return 8;
        return Math.max(1, Math.min(64, genericQuota(duty.town(), duty.area(), villager.getUUID())
                + (crop == Crop.ANY ? 0 : quota(duty.town(), duty.area(), villager.getUUID(), new ItemStack(crop.seed())))));
    }
}
