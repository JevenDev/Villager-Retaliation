package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public record SettlementBuildingPreviewPayload(UUID town, int requestId, ResourceLocation structure, BlockPos anchor,
                                               Rotation rotation, BlockPos origin, BlockPos min, BlockPos max,
                                               int price, CurrencyAmount funds, String failure, List<PreviewBlock> blocks,
                                               List<Material> materials, List<Builder> builders) implements CustomPacketPayload {
    public static final int MAX_BLOCKS = 65_536;
    public static final int MAX_MATERIALS = 1_024;
    public static final int MAX_BUILDERS = 512;
    public static final Type<SettlementBuildingPreviewPayload> TYPE = VillagerPayloads.type("settlement_building_preview");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementBuildingPreviewPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementBuildingPreviewPayload::encode, SettlementBuildingPreviewPayload::decode);

    public SettlementBuildingPreviewPayload {
        Objects.requireNonNull(town);
        Objects.requireNonNull(structure);
        Objects.requireNonNull(anchor);
        Objects.requireNonNull(rotation);
        Objects.requireNonNull(origin);
        Objects.requireNonNull(min);
        Objects.requireNonNull(max);
        Objects.requireNonNull(funds);
        Objects.requireNonNull(failure);
        if (requestId < 0 || price < 0 || failure.length() > 64 || blocks.size() > MAX_BLOCKS
                || materials.size() > MAX_MATERIALS || builders.size() > MAX_BUILDERS) throw new IllegalArgumentException("Invalid building preview");
        blocks = List.copyOf(blocks);
        materials = List.copyOf(materials);
        builders = List.copyOf(builders);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementBuildingPreviewPayload payload) {
        buffer.writeUUID(payload.town());
        buffer.writeVarInt(payload.requestId());
        buffer.writeResourceLocation(payload.structure());
        buffer.writeBlockPos(payload.anchor());
        buffer.writeEnum(payload.rotation());
        buffer.writeBlockPos(payload.origin());
        buffer.writeBlockPos(payload.min());
        buffer.writeBlockPos(payload.max());
        buffer.writeVarInt(payload.price());
        payload.funds().write(buffer);
        buffer.writeUtf(payload.failure(), 64);
        buffer.writeVarInt(payload.blocks().size());
        for (var block : payload.blocks()) {
            buffer.writeBlockPos(block.local());
            buffer.writeVarInt(Block.getId(block.state()));
        }
        buffer.writeVarInt(payload.materials().size());
        for (var material : payload.materials()) {
            ItemStack.STREAM_CODEC.encode(buffer, material.item());
            buffer.writeVarInt(material.required());
            buffer.writeVarInt(material.available());
            buffer.writeUtf(material.label(), 256);
        }
        buffer.writeVarInt(payload.builders().size());
        for (var builder : payload.builders()) {
            buffer.writeUUID(builder.id());
            buffer.writeUtf(builder.name(), 128);
        }
    }

    private static SettlementBuildingPreviewPayload decode(RegistryFriendlyByteBuf buffer) {
        var town = buffer.readUUID();
        int request = buffer.readVarInt();
        var structure = buffer.readResourceLocation();
        var anchor = buffer.readBlockPos();
        var rotation = buffer.readEnum(Rotation.class);
        var origin = buffer.readBlockPos();
        var min = buffer.readBlockPos();
        var max = buffer.readBlockPos();
        int price = buffer.readVarInt();
        var funds = CurrencyAmount.read(buffer);
        var failure = buffer.readUtf(64);
        var blocks = new ArrayList<PreviewBlock>();
        int count = VillagerPayloads.readCollectionSize(buffer, MAX_BLOCKS, "building preview blocks");
        for (int index = 0; index < count; index++) blocks.add(new PreviewBlock(buffer.readBlockPos(), Block.stateById(buffer.readVarInt())));
        var materials = new ArrayList<Material>();
        count = VillagerPayloads.readCollectionSize(buffer, MAX_MATERIALS, "building preview materials");
        for (int index = 0; index < count; index++) materials.add(new Material(ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(256)));
        var builders = new ArrayList<Builder>();
        count = VillagerPayloads.readCollectionSize(buffer, MAX_BUILDERS, "building preview builders");
        for (int index = 0; index < count; index++) builders.add(new Builder(buffer.readUUID(), buffer.readUtf(128)));
        return new SettlementBuildingPreviewPayload(town, request, structure, anchor, rotation, origin, min, max, price, funds, failure, blocks, materials, builders);
    }

    public record PreviewBlock(BlockPos local, BlockState state) {
        public PreviewBlock {
            Objects.requireNonNull(local);
            Objects.requireNonNull(state);
            local = local.immutable();
        }
    }

    public record Material(ItemStack item, int required, int available, String label) {
        public Material(ItemStack item, int required, int available) {
            this(item, required, available, item.getHoverName().getString());
        }
        public Material {
            if (item == null || item.isEmpty() || required <= 0 || available < 0 || label == null || label.length() > 256) throw new IllegalArgumentException("Invalid building material");
            item = item.copyWithCount(1);
        }
    }

    public record Builder(UUID id, String name) {
        public Builder {
            Objects.requireNonNull(id);
            if (name == null || name.length() > 128) throw new IllegalArgumentException("Invalid building builder");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
