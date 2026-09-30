package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.courier.HiredTransferQueue;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public record TransferRequestPayload(ProductionTarget target, UUID request, long revision, Command command) implements CustomPacketPayload {
    public static final Type<TransferRequestPayload> TYPE = VillagerPayloads.type("transfer_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, TransferRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(TransferRequestPayload::encode, TransferRequestPayload::decode);

    public TransferRequestPayload {
        Objects.requireNonNull(target);
        Objects.requireNonNull(request);
        Objects.requireNonNull(command);
        if (revision < 0L) throw new IllegalArgumentException("Invalid transfer revision");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, TransferRequestPayload payload) {
        payload.target().write(buffer);
        buffer.writeUUID(payload.request());
        buffer.writeVarLong(payload.revision());
        switch (payload.command()) {
            case View ignored -> buffer.writeEnum(Kind.VIEW);
            case Append append -> {
                buffer.writeEnum(Kind.APPEND);
                writeEndpoint(buffer, append.source());
                writeEndpoint(buffer, append.destination());
                ItemStack.STREAM_CODEC.encode(buffer, append.sample());
                buffer.writeVarInt(append.amount());
                buffer.writeEnum(append.mode());
            }
            case Edit edit -> {
                buffer.writeEnum(Kind.EDIT);
                buffer.writeUUID(edit.order());
                buffer.writeEnum(edit.edit());
                buffer.writeVarInt(edit.index());
            }
        }
    }

    private static TransferRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        var target = ProductionTarget.read(buffer);
        var request = buffer.readUUID();
        long revision = buffer.readVarLong();
        Command command = switch (buffer.readEnum(Kind.class)) {
            case VIEW -> new View();
            case APPEND -> new Append(readEndpoint(buffer), readEndpoint(buffer), ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt(), buffer.readEnum(HiredTransferQueue.Mode.class));
            case EDIT -> new Edit(buffer.readUUID(), buffer.readEnum(HiredTransferQueue.Edit.class), buffer.readVarInt());
        };
        return new TransferRequestPayload(target, request, revision, command);
    }

    static void writeEndpoint(RegistryFriendlyByteBuf buffer, HiredTransferQueue.Endpoint endpoint) {
        buffer.writeBlockPos(endpoint.position());
        buffer.writeBoolean(endpoint.depot() != null);
        if (endpoint.depot() != null) buffer.writeUUID(endpoint.depot());
    }

    static HiredTransferQueue.Endpoint readEndpoint(RegistryFriendlyByteBuf buffer) {
        return new HiredTransferQueue.Endpoint(buffer.readBlockPos(), buffer.readBoolean() ? buffer.readUUID() : null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public sealed interface Command permits View, Append, Edit {}
    public record View() implements Command {}
    public record Append(HiredTransferQueue.Endpoint source, HiredTransferQueue.Endpoint destination, ItemStack sample, int amount, HiredTransferQueue.Mode mode) implements Command {
        public Append {
            var order = HiredTransferQueue.Order.create(source, destination, sample, amount, mode);
            sample = order.sample();
        }
        public Append(HiredTransferQueue.Endpoint source, HiredTransferQueue.Endpoint destination, ItemStack sample, int amount) {
            this(source, destination, sample, amount, HiredTransferQueue.Mode.FINITE);
        }
        @Override
        public ItemStack sample() { return this.sample.copy(); }
    }
    public record Edit(UUID order, HiredTransferQueue.Edit edit, int index) implements Command {
        public Edit {
            Objects.requireNonNull(order);
            Objects.requireNonNull(edit);
            if (index < 0 || index >= HiredTransferQueue.MAX_ORDERS) throw new IllegalArgumentException("Invalid transfer position");
        }
    }
    private enum Kind { VIEW, APPEND, EDIT }
}
