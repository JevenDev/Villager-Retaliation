package com.jvn.villagerretaliation.authoring.preview;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class QuestPlaytestBaselineData extends SavedData {
    private CompoundTag entries = new CompoundTag();

    public static QuestPlaytestBaselineData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(QuestPlaytestBaselineData::new, QuestPlaytestBaselineData::load), "villagerretaliation_test_baselines");
    }

    public static QuestPlaytestBaselineData load(CompoundTag root, HolderLookup.Provider registries) {
        var data = new QuestPlaytestBaselineData();
        data.entries = root.getCompound("Baselines").copy();
        return data;
    }

    public List<String> names(UUID player, ResourceLocation quest) {
        return entries.getCompound(player.toString()).getCompound(quest.toString()).getAllKeys().stream().sorted().toList();
    }

    public CompoundTag read(UUID player, ResourceLocation quest, String name) {
        return entries.getCompound(player.toString()).getCompound(quest.toString()).getCompound(name).copy();
    }

    public void put(UUID player, ResourceLocation quest, String name, CompoundTag value) {
        var quests = entries.getCompound(player.toString());
        var saved = quests.getCompound(quest.toString());
        saved.put(name, value.copy()); quests.put(quest.toString(), saved); entries.put(player.toString(), quests);
        setDirty();
    }

    public void remove(UUID player, ResourceLocation quest, String name) {
        var quests = entries.getCompound(player.toString());
        var saved = quests.getCompound(quest.toString());
        saved.remove(name);
        if (saved.isEmpty()) quests.remove(quest.toString());
        if (quests.isEmpty()) entries.remove(player.toString());
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.put("Baselines", entries.copy());
        return root;
    }
}
