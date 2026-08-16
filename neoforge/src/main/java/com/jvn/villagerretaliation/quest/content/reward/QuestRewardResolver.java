package com.jvn.villagerretaliation.quest.content.reward;

import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Resolves and rolls quest rewards through one GLM-aware path. */
public final class QuestRewardResolver {
    private QuestRewardResolver() {
    }

    public static Resolution resolve(MinecraftServer server, ResourceLocation rewardId) {
        if (server == null || rewardId == null) {
            return Resolution.unresolved(rewardId, "quest reward resolution requires a server and stable reward ID");
        }
        BundledQuestReward bundled = QuestContentCatalogs.current(server)
                .rewards()
                .bundled(rewardId)
                .orElse(null);
        if (bundled != null) {
            return new Resolution(rewardId, Source.BUNDLED, bundled.table(), bundled, "");
        }

        boolean registered = server.reloadableRegistries()
                .getKeys(Registries.LOOT_TABLE)
                .contains(rewardId);
        if (registered) {
            ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, rewardId);
            return new Resolution(rewardId, Source.EXTERNAL, server.reloadableRegistries().getLootTable(key), null, "");
        }
        return Resolution.unresolved(rewardId, "unresolved quest reward " + rewardId
                + ": no bundled definition or external loot table exists");
    }

    public static RollResult roll(
            ServerLevel level,
            float luck,
            ResourceLocation rewardId,
            RandomSource random) {
        Resolution resolution = resolve(level == null ? null : level.getServer(), rewardId);
        if (!resolution.resolved() || level == null || random == null) {
            return new RollResult(resolution, List.of());
        }
        LootParams params = new LootParams.Builder(level)
                .withLuck(luck)
                .create(LootContextParamSets.EMPTY);
        List<ItemStack> items = resolution.table().getRandomItems(params, random).stream()
                .map(ItemStack::copy)
                .toList();
        return new RollResult(resolution, items);
    }

    /** A roll-producing preview intentionally delegates to the exact execution roll path. */
    public static RollResult rollPreview(
            ServerLevel level,
            float luck,
            ResourceLocation rewardId,
            RandomSource random) {
        return roll(level, luck, rewardId, random);
    }

    public enum Source {
        BUNDLED,
        EXTERNAL,
        UNRESOLVED
    }

    public record Resolution(
            ResourceLocation rewardId,
            Source source,
            LootTable table,
            BundledQuestReward bundled,
            String diagnostic) {
        public Resolution {
            source = source == null ? Source.UNRESOLVED : source;
            diagnostic = diagnostic == null ? "" : diagnostic;
        }

        private static Resolution unresolved(ResourceLocation id, String diagnostic) {
            return new Resolution(id, Source.UNRESOLVED, null, null, diagnostic);
        }

        public boolean resolved() {
            return this.source != Source.UNRESOLVED && this.table != null;
        }
    }

    public record RollResult(Resolution resolution, List<ItemStack> items) {
        public RollResult {
            resolution = resolution == null
                    ? Resolution.unresolved(null, "quest reward resolution was not attempted")
                    : resolution;
            items = items == null ? List.of() : items.stream().map(ItemStack::copy).toList();
        }

        public List<ItemStack> items() {
            return this.items.stream().map(ItemStack::copy).toList();
        }
    }
}
