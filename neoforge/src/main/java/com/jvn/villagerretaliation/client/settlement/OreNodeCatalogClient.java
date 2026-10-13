package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.network.OreNodeCatalogPayload;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class OreNodeCatalogClient {
    private static Set<ResourceLocation> blocks = Set.of();

    private OreNodeCatalogClient() { }

    public static void accept(OreNodeCatalogPayload payload) { blocks = Set.copyOf(payload.blocks()); }

    public static boolean isOre(BlockState state) { return blocks.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock())); }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { blocks = Set.of(); }
}
