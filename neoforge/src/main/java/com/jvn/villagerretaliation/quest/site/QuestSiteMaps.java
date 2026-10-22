package com.jvn.villagerretaliation.quest.site;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Site;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

final class QuestSiteMaps {
    private QuestSiteMaps() { }

    private static void paintBiomePreview(ServerPlayer player, MapItemSavedData data) {
        var source = player.serverLevel().getChunkSource();
        int scale = 1 << data.scale;
        for (int x = 0; x < 128; x += 8) for (int z = 0; z < 128; z += 8) {
            var biome = source.getGenerator().getBiomeSource().getNoiseBiome(
                    (data.centerX + (x - 60) * scale) >> 2, 16,
                    (data.centerZ + (z - 60) * scale) >> 2, source.randomState().sampler());
            var color = biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER)
                    ? MapColor.WATER : MapColor.GRASS;
            for (int dx = 0; dx < 8; dx++) for (int dz = 0; dz < 8; dz++)
                data.setColor(x + dx, z + dz, color.getPackedId(MapColor.Brightness.NORMAL));
        }
    }

    static void give(ServerPlayer player, QuestDefinition quest, Site site, boolean replacement) {
        if (!player.serverLevel().dimension().location().equals(site.dimension)) return;
        if ((site.mapRecipients.contains(player.getUUID()) || site.mapIssued && site.player.equals(player.getUUID())) && !replacement) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            var stack = player.getInventory().getItem(slot);
            var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.hasUUID("QuestSite") && tag.getUUID("QuestSite").equals(site.id)) return;
        }
        if (player.getInventory().getFreeSlot() < 0) return;
        var position = QuestSiteService.displayPosition(player.serverLevel(), player.getUUID(), quest, site.position);
        var map = MapItem.create(player.serverLevel(), position.getX(), position.getZ(), (byte) 2, true, true);
        int radius = QuestSiteService.displayRadius(player.serverLevel(), player.getUUID(), quest);
        var original = MapItem.getSavedData(map, player.serverLevel());
        if (original == null) return;
        var centered = original.save(new CompoundTag(), player.registryAccess());
        centered.putInt("xCenter", position.getX());
        centered.putInt("zCenter", position.getZ());
        player.serverLevel().setMapData(map.get(DataComponents.MAP_ID), MapItemSavedData.load(centered, player.registryAccess()));
        paintBiomePreview(player, MapItem.getSavedData(map, player.serverLevel()));
        if (radius == 0) MapItemSavedData.addTargetDecoration(map, position, "+", MapDecorationTypes.RED_X);
        else {
            var data = MapItem.getSavedData(map, player.serverLevel());
            if (data == null) return;
            int scale = 1 << data.scale;
            double centerX = (position.getX() - data.centerX) / (double) scale + 64;
            double centerZ = (position.getZ() - data.centerZ) / (double) scale + 64;
            for (int x = 0; x < 128; x++) for (int z = 0; z < 128; z++) {
                double distance = Math.hypot(x - centerX, z - centerZ);
                if (Math.abs(distance - radius / (double) scale) < .8)
                    data.setColor(x, z, MapColor.COLOR_RED.getPackedId(MapColor.Brightness.NORMAL));
            }
            player.serverLevel().setMapData(map.get(DataComponents.MAP_ID), data.locked());
            data.setDirty();
        }
        var tag = new CompoundTag();
        tag.putUUID("QuestSite", site.id);
        map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        map.set(DataComponents.ITEM_NAME, Component.translatable("quest.villagerretaliation.site_map"));
        if (site.player.equals(player.getUUID())) site.mapIssued = true;
        if (!site.mapRecipients.contains(player.getUUID())) site.mapRecipients.add(player.getUUID());
        var saved = QuestSiteSavedData.get(player.serverLevel());
        saved.setDirty();
        player.server.overworld().getDataStorage().save();
        player.getInventory().add(map);
        player.containerMenu.broadcastChanges();
    }
}
