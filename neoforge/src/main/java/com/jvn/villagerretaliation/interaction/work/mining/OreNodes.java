package com.jvn.villagerretaliation.interaction.work.mining;

import com.jvn.villagerretaliation.VillagerRetaliation;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class OreNodes {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, VillagerRetaliation.MOD_ID);
    private static final Supplier<AttachmentType<CompoundTag>> NODES = TYPES.register("ore_nodes",
            () -> AttachmentType.builder(() -> new CompoundTag()).serialize(CompoundTag.CODEC).build());

    private OreNodes() { }

    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static void generated(LevelChunk chunk) {
        if (chunk.getLevel().isClientSide() || chunk.hasData(NODES)) return;
        var data = chunk.getData(NODES);
        chunk.findBlocks(state -> OreNodeDefinitions.extractions(state) > 0,
                (pos, state) -> data.putInt(Long.toString(pos.asLong()), 0));
        chunk.setUnsaved(true);
    }

    public static void changed(LevelChunk chunk, BlockPos position, BlockState previous, BlockState replacement) {
        if (chunk.getLevel().isClientSide() || !chunk.hasData(NODES)
                || previous == null || previous.getBlock() == replacement.getBlock()) return;
        var data = chunk.getData(NODES);
        String key = Long.toString(position.asLong());
        if (data.contains(key)) {
            data.remove(key);
            chunk.setUnsaved(true);
        }
    }

    public static boolean silkTouch(ServerLevel level, ItemStack tool) {
        return EnchantmentHelper.getItemEnchantmentLevel(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
    }

    public static int remaining(CompoundTag nodes, BlockPos position, BlockState state) {
        String key = Long.toString(position.asLong());
        if (!nodes.contains(key)) return 0;
        int configured = OreNodeDefinitions.extractions(state);
        return configured == 0 ? 0 : nodes.getInt(key) == 0 ? configured : nodes.getInt(key);
    }

    public static boolean mine(ServerLevel level, BlockPos position, Villager villager, ItemStack tool) {
        var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null) return false;
        var nodes = chunk.hasData(NODES) ? chunk.getData(NODES) : new CompoundTag();
        int remaining = remaining(nodes, position, chunk.getBlockState(position));
        if (remaining > 1 && !silkTouch(level, tool)) {
            nodes.putInt(Long.toString(position.asLong()), remaining - 1);
            chunk.setUnsaved(true);
            return true;
        }
        return level.destroyBlock(position, false, villager);
    }

    public static CompoundTag snapshot(LevelChunk chunk, BlockPos min, BlockPos max) {
        var snapshot = new CompoundTag();
        if (!chunk.hasData(NODES)) return snapshot;
        var nodes = chunk.getData(NODES);
        for (BlockPos position : BlockPos.betweenClosed(min, max)) {
            String key = Long.toString(position.asLong());
            if (nodes.contains(key)) snapshot.putInt(key, nodes.getInt(key));
        }
        return snapshot;
    }

    public static void apply(LevelChunk chunk, CompoundTag baseline, CompoundTag updated) {
        if (baseline.equals(updated)) return;
        var nodes = chunk.getData(NODES);
        var keys = new java.util.HashSet<>(baseline.getAllKeys());
        keys.addAll(updated.getAllKeys());
        for (String key : keys) {
            if (java.util.Objects.equals(baseline.get(key), updated.get(key))) continue;
            if (updated.contains(key)) nodes.putInt(key, updated.getInt(key));
            else nodes.remove(key);
        }
        chunk.setUnsaved(true);
    }
}
