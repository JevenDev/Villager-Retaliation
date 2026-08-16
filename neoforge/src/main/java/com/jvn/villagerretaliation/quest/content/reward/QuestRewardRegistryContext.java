package com.jvn.villagerretaliation.quest.content.reward;

import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;

/** Registry lookup context that exposes reloadable loot data alongside static registries. */
public final class QuestRewardRegistryContext {
    private QuestRewardRegistryContext() {
    }

    public static HolderLookup.Provider create(MinecraftServer server) {
        if (server == null) {
            throw new IllegalArgumentException("quest reward registry context requires a server");
        }
        HolderLookup.Provider reloadable = server.reloadableRegistries().get();
        HolderLookup.Provider base = server.registryAccess();
        return new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistries() {
                return Stream.concat(reloadable.listRegistries(), base.listRegistries()).distinct();
            }

            @Override
            public <T> Optional<HolderLookup.RegistryLookup<T>> lookup(
                    ResourceKey<? extends Registry<? extends T>> registryKey) {
                return reloadable.lookup(registryKey).or(() -> base.lookup(registryKey));
            }
        };
    }
}
