package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record TransferSnapshotPayload(ResourceLocation dimension, ProductionTarget target, UUID request, boolean applied,
        String title, boolean editable, String blocker, String feedback, CompoundTag queue, List<Storage> storages, List<Product> products, List<Stock> stocks)
        implements CustomPacketPayload {
    public static final Type<TransferSnapshotPayload> TYPE = VillagerPayloads.type("transfer_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferSnapshotPayload> STREAM_CODEC =
            VillagerPayloads.codec(TransferSnapshotPayload::encode, TransferSnapshotPayload::decode);

    public TransferSnapshotPayload {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(target);
        Objects.requireNonNull(request);
        if (title == null || title.length() > 128 || blocker == null || blocker.length() > 256 || feedback == null || feedback.length() > 256) {
            throw new IllegalArgumentException("Invalid transfer snapshot");
        }
        queue = queue == null ? null : queue.copy();
        storages = List.copyOf(storages);
        products = List.copyOf(products);
        stocks = List.copyOf(stocks);
        if (storages.size() > 128 || products.size() > 256 || stocks.size() > HiredTransferQueue.MAX_ORDERS) throw new IllegalArgumentException("Oversized transfer snapshot");
    }

    public TransferSnapshotPayload(ResourceLocation dimension, ProductionTarget target, UUID request, boolean applied,
            String title, boolean editable, String blocker, String feedback, CompoundTag queue, List<Storage> storages, List<Product> products) {
        this(dimension, target, request, applied, title, editable, blocker, feedback, queue, storages, products, List.of());
    }

    @Override
    public CompoundTag queue() { return this.queue == null ? null : this.queue.copy(); }

    private static void encode(RegistryFriendlyByteBuf buffer, TransferSnapshotPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        payload.target().write(buffer);
        buffer.writeUUID(payload.request());
        buffer.writeBoolean(payload.applied());
        buffer.writeUtf(payload.title(), 128);
        buffer.writeBoolean(payload.editable());
        buffer.writeUtf(payload.blocker(), 256);
        buffer.writeUtf(payload.feedback(), 256);
        buffer.writeNbt(payload.queue());
        buffer.writeVarInt(payload.storages().size());
        for (var storage : payload.storages()) {
            TransferRequestPayload.writeEndpoint(buffer, storage.endpoint());
            buffer.writeUtf(storage.name(), 128);
            buffer.writeBoolean(storage.source());
            buffer.writeBoolean(storage.destination());
            buffer.writeBoolean(storage.available());
        }
        buffer.writeVarInt(payload.stocks().size());
        for (var stock : payload.stocks()) {
            buffer.writeUUID(stock.order());
            buffer.writeLong(stock.stored());
            buffer.writeLong(stock.incoming());
        }
        buffer.writeVarInt(payload.products().size());
        for (var product : payload.products()) {
            TransferRequestPayload.writeEndpoint(buffer, product.source());
            ItemStack.STREAM_CODEC.encode(buffer, product.sample());
        }
    }

    private static TransferSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        var target = ProductionTarget.read(buffer);
        var request = buffer.readUUID();
        boolean applied = buffer.readBoolean();
        String title = buffer.readUtf(128);
        boolean editable = buffer.readBoolean();
        String blocker = buffer.readUtf(256);
        String feedback = buffer.readUtf(256);
        var queue = buffer.readNbt();
        if (queue != null) {
            var parsed = HiredTransferQueue.load(queue, buffer.registryAccess(), ignored -> {});
            if (parsed.scope() != (target.town() == null ? HiredTransferQueue.Scope.PERSONAL : HiredTransferQueue.Scope.WORKSITE)
                    || target.town() != null && !parsed.owner().equals(target.subject())) throw new IllegalArgumentException("Mismatched transfer snapshot");
        }
        var storages = new ArrayList<Storage>();
        int count = VillagerPayloads.readCollectionSize(buffer, 128, "Transfer storage");
        for (int index = 0; index < count; index++) storages.add(new Storage(TransferRequestPayload.readEndpoint(buffer),
                buffer.readUtf(128), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        var stocks = new ArrayList<Stock>();
        count = VillagerPayloads.readCollectionSize(buffer, HiredTransferQueue.MAX_ORDERS, "Transfer stock");
        for (int index = 0; index < count; index++) stocks.add(new Stock(buffer.readUUID(), buffer.readLong(), buffer.readLong()));
        var products = new ArrayList<Product>();
        count = VillagerPayloads.readCollectionSize(buffer, 256, "Transfer products");
        for (int index = 0; index < count; index++) products.add(new Product(TransferRequestPayload.readEndpoint(buffer), ItemStack.STREAM_CODEC.decode(buffer)));
        return new TransferSnapshotPayload(dimension, target, request, applied, title, editable, blocker, feedback, queue, storages, products, stocks);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public record Storage(HiredTransferQueue.Endpoint endpoint, String name, boolean source, boolean destination, boolean available) {
        public Storage {
            Objects.requireNonNull(endpoint);
            if (name == null || name.length() > 128 || !source && !destination) throw new IllegalArgumentException("Invalid transfer storage");
        }
    }

    public record Stock(UUID order, long stored, long incoming) {
        public Stock {
            Objects.requireNonNull(order);
            if (stored < -1 || incoming < -1) throw new IllegalArgumentException("Invalid transfer stock");
        }
    }

    public record Product(HiredTransferQueue.Endpoint source, ItemStack sample) {
        public Product {
            Objects.requireNonNull(source);
            if (sample == null || sample.isEmpty()) throw new IllegalArgumentException("Invalid transfer product");
            sample = com.jvn.villagerretaliation.interaction.work.production.HiredProductionStock.product(sample).copyWithCount(1);
        }
        @Override
        public ItemStack sample() { return this.sample.copy(); }
    }
}
