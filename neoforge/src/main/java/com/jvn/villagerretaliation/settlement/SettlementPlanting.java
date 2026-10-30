package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.interaction.HiredWorkStateStore;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions;
import com.jvn.villagerretaliation.interaction.work.HiredFarmingOptions.Crop;
import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class SettlementPlanting {
    private SettlementPlanting() { }

    public record Decision(Crop crop, String reason) {
        CompoundTag save() {
            var tag = new CompoundTag();
            tag.putString("Crop", crop.name());
            tag.putString("Reason", reason);
            return tag;
        }

        static Decision load(CompoundTag tag) {
            String reason = tag.getString("Reason");
            if (!List.of("mix", "cooking", "feed", "food", "selected", "available").contains(reason))
                throw new IllegalArgumentException("Invalid planting reason");
            return new Decision(Crop.valueOf(tag.getString("Crop")), reason);
        }
    }

    public static String blocker(CompoundTag state, Decision decision) {
        Crop restriction = HiredFarmingOptions.crop(state);
        return decision.crop() != Crop.ANY && restriction != Crop.ANY && restriction != decision.crop() ? "restricted" : "missing";
    }

    public static void report(CompoundTag state, Decision decision, String blocker) {
        String crop = net.minecraft.network.chat.Component.translatable(
                "villagerretaliation.gui.settlement.crop." + decision.crop().name().toLowerCase(java.util.Locale.ROOT)).getString();
        String reason = blocker.isEmpty() ? decision.reason() : blocker;
        state.putString("SettlementPlantingMessage", "interaction.work.farming.planting_" + reason);
        state.putString("SettlementPlantingCrop", crop);
        state.putString("SettlementPlantingDetail", net.minecraft.network.chat.Component.translatable(
                "villagerretaliation.gui.settlement.planting_" + reason, crop).getString());
    }

    public static String message(CompoundTag state) { return state.getString("SettlementPlantingMessage"); }
    public static String description(CompoundTag state) { return state.getString("SettlementPlantingDetail"); }
    public static java.util.Map<String, String> replacements(CompoundTag state) {
        return java.util.Map.of("crop", state.getString("SettlementPlantingCrop"));
    }

    public static Decision actual(Decision decision, ItemStack seed) {
        if (decision.crop() != Crop.ANY) return decision;
        for (var crop : Crop.values()) if (crop != Crop.ANY && seed.is(crop.seed())) return new Decision(crop, decision.reason());
        return decision;
    }

    public static Crop crop(Villager villager, BlockPos soil) {
        return decision(villager, soil).crop();
    }

    public static Decision decision(Villager villager, BlockPos soil) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null || duty.area().type() != SettlementAreaType.FARM
                || !duty.area().contains(soil) && !duty.area().contains(soil.above()))
            return new Decision(Crop.ANY, "available");
        var level = (ServerLevel) villager.level();
        var plot = duty.town().fields().get(soil);
        if (plot == null && duty.area().crops().demand()) {
            var chunk = level.getChunkSource().getChunkNow(soil.getX() >> 4, soil.getZ() >> 4);
            if (chunk != null && SettlementFieldPlot.supportedSite(chunk.getBlockState(soil), chunk.getBlockState(soil.above()))) {
                plot = SettlementFieldPlot.capture(level, duty.area().id(), soil, chunk, level.getGameTime());
                duty.town().recordField(plot);
            }
        }
        return choose(level, duty.town(), duty.area(), plot, soil, HiredWorkStateStore.state(villager));
    }

    static Decision choose(ServerLevel level, SettlementRecord town, SettlementArea area, SettlementFieldPlot plot,
                           BlockPos soil, CompoundTag state) {
        var configured = area.crops();
        Crop restriction = HiredFarmingOptions.crop(state);
        var plantingCells = configured.enabled() ? town.fields().values().stream()
                .filter(cell -> SettlementFieldService.serves(area, cell) && !cell.conflict()
                        && (cell.soil().is(Blocks.FARMLAND) || SettlementFarmTerrain.soil(cell.soil()))
                        && (cell.crop().isAir() || SettlementFieldPlot.supportedCrop(cell.crop()))
                        && (area.farm() == null || cell.irrigated()))
                .map(SettlementFieldPlot::position)
                .sorted(Comparator.<BlockPos>comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getY)).toList() : List.<BlockPos>of();
        var fallback = new Decision(configured.enabled()
                ? plantingCells.isEmpty() ? configured.crop(area, soil) : configured.crop(plantingCells, soil) : restriction,
                configured.enabled() ? "mix" : restriction == Crop.ANY ? "available" : "selected");
        if (!configured.demand() || plot == null || !HiredFarmingOptions.plantCrops(state)) return fallback;
        var previous = plot.planting(configured);
        if (previous != null) return previous;
        var needs = SettlementDemandService.inspect(level, town).demands().stream()
                .filter(need -> need.missing() > 0 && (need.kind() == WorkSupplyDemand.Kind.FOOD
                        || need.kind() == WorkSupplyDemand.Kind.PRODUCTION
                        && (need.requirement().key().startsWith("animal_feed:")
                        || town.areas().get(need.area()) != null && town.areas().get(need.area()).type() == SettlementAreaType.KITCHEN)))
                .sorted(Comparator.comparingInt((SettlementDemandService.Demand need) -> need.supply().priority()).reversed()
                        .thenComparing(need -> need.supply().id())).toList();
        var credits = production(level, town, soil);
        var outputs = SettlementSupplyStock.storages(level, town, area.id(), true);
        Decision selected = fallback;
        for (var need : needs) {
            long remaining = need.missing();
            for (var credit : credits) {
                if (credit.outputs().stream().noneMatch(need.sources()::contains)) continue;
                long value = need.requirement().value(credit.stack());
                if (value <= 0) continue;
                int used = (int) Math.min(credit.stack().getCount(), (remaining + value - 1) / value);
                credit.stack().shrink(used);
                remaining = Math.max(0, remaining - used * value);
            }
            if (remaining == 0 || outputs.stream().noneMatch(need.sources()::contains)) continue;
            Crop best = Crop.ANY;
            long bestValue = 0;
            for (var crop : Crop.values()) {
                if (!configured.permits(crop) || restriction != Crop.ANY && restriction != crop) continue;
                long value = products(crop).stream().mapToLong(need.requirement()::value).sum();
                if (value > bestValue || value > 0 && value == bestValue && crop == fallback.crop()) {
                    best = crop;
                    bestValue = value;
                }
            }
            if (best != Crop.ANY) {
                selected = new Decision(best, need.kind() == WorkSupplyDemand.Kind.FOOD ? "food"
                        : need.requirement().key().startsWith("animal_feed:") ? "feed" : "cooking");
                break;
            }
        }
        if (selected.crop() != Crop.ANY) {
            plot.planting(configured, selected);
            town.changed();
        }
        return selected;
    }

    private record Production(List<BlockPos> outputs, ItemStack stack) { }

    private static List<Production> production(ServerLevel level, SettlementRecord town, BlockPos excluded) {
        var result = new ArrayList<Production>();
        for (var plot : town.fields().values()) {
            if (plot.position().equals(excluded) || plot.conflict()) continue;
            var area = town.areas().values().stream().filter(candidate -> candidate.enabled() && town.owns(candidate)
                    && candidate.type() == SettlementAreaType.FARM && SettlementFieldService.serves(candidate, plot))
                    .sorted(Comparator.comparing(SettlementArea::id)).findFirst().orElse(null);
            if (area == null) continue;
            var planned = plot.planting(area.crops());
            Crop crop = planned == null || !plot.crop().isAir() && !plot.harvestable() ? crop(plot.crop()) : planned.crop();
            var outputs = SettlementSupplyStock.storages(level, town, area.id(), true);
            for (var stack : products(crop)) result.add(new Production(outputs, stack));
        }
        return result;
    }

    static Crop crop(BlockState block) {
        for (var crop : Crop.values()) if (crop != Crop.ANY) {
            var plant = SettlementFarmPlants.plant(new ItemStack(crop.seed()));
            if (plant != null && block.is(plant.getBlock())) return crop;
        }
        return Crop.ANY;
    }

    static List<ItemStack> products(Crop crop) {
        // use conservative harvest estimates and spend each projected item only once
        return switch (crop) {
            case WHEAT -> List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS));
            case CARROT -> List.of(new ItemStack(Items.CARROT));
            case POTATO -> List.of(new ItemStack(Items.POTATO));
            case BEETROOT -> List.of(new ItemStack(Items.BEETROOT), new ItemStack(Items.BEETROOT_SEEDS));
            default -> List.of();
        };
    }

    public static void planted(Villager villager, BlockPos soil) {
        var duty = SettlementWorkforceService.duty(villager).orElse(null);
        if (duty == null) return;
        var plot = duty.town().fields().get(soil);
        if (plot == null) return;
        var chunk = ((ServerLevel) villager.level()).getChunkSource().getChunkNow(soil.getX() >> 4, soil.getZ() >> 4);
        if (chunk != null && !plot.pending()) plot.refresh(chunk);
        plot.clearPlanting();
        duty.town().changed();
    }

    public static boolean planned(Villager villager) {
        return SettlementWorkforceService.duty(villager)
                .map(duty -> duty.area().crops().enabled() || duty.area().crops().demand()).orElse(false);
    }

    public static boolean permits(CompoundTag state, Crop crop, ItemStack seed) {
        return HiredFarmingOptions.permitsSeed(state, seed) && (crop == Crop.ANY || seed.is(crop.seed()));
    }
}
