package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.production.HiredProductionOrder;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionRecipe;
import com.jvn.villagerretaliation.interaction.work.production.HiredProductionService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ProductionRequestPayload(ProductionTarget target, UUID request, long revision, Command command) implements CustomPacketPayload {
    public static final Type<ProductionRequestPayload> TYPE = VillagerPayloads.type("production_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, ProductionRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(ProductionRequestPayload::encode, ProductionRequestPayload::decode);

    public ProductionRequestPayload {
        Objects.requireNonNull(target);
        Objects.requireNonNull(request);
        Objects.requireNonNull(command);
        if (revision < 0L) throw new IllegalArgumentException("Invalid production revision");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, ProductionRequestPayload payload) {
        payload.target().write(buffer);
        buffer.writeUUID(payload.request());
        buffer.writeVarLong(payload.revision());
        switch (payload.command()) {
            case View ignored -> buffer.writeEnum(Kind.VIEW);
            case Append append -> {
                buffer.writeEnum(Kind.APPEND);
                writeRecipe(buffer, append.recipe());
                buffer.writeEnum(append.mode());
                buffer.writeVarInt(append.amount());
                buffer.writeBoolean(append.storage() != null);
                if (append.storage() != null) buffer.writeBlockPos(append.storage());
            }
            case Edit edit -> {
                buffer.writeEnum(Kind.EDIT);
                buffer.writeUUID(edit.order());
                buffer.writeEnum(edit.edit());
                buffer.writeVarInt(edit.index());
            }
        }
    }

    private static ProductionRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        var target = ProductionTarget.read(buffer);
        var request = buffer.readUUID();
        long revision = buffer.readVarLong();
        Command command = switch (buffer.readEnum(Kind.class)) {
            case VIEW -> new View();
            case APPEND -> new Append(readRecipe(buffer), buffer.readEnum(HiredProductionOrder.Mode.class),
                    buffer.readVarInt(), buffer.readBoolean() ? buffer.readBlockPos() : null);
            case EDIT -> new Edit(buffer.readUUID(), buffer.readEnum(HiredProductionService.Edit.class), buffer.readVarInt());
        };
        return new ProductionRequestPayload(target, request, revision, command);
    }

    static void writeRecipe(RegistryFriendlyByteBuf buffer, HiredProductionRecipe recipe) {
        buffer.writeBoolean(recipe.recipe() != null);
        if (recipe.recipe() != null) {
            buffer.writeResourceLocation(recipe.recipe());
            buffer.writeVarInt(recipe.alternatives().size());
            recipe.alternatives().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                buffer.writeVarInt(entry.getKey());
                buffer.writeResourceLocation(entry.getValue());
            });
        } else {
            buffer.writeResourceLocation(recipe.brewingItem());
            buffer.writeResourceLocation(recipe.potion());
        }
    }

    static HiredProductionRecipe readRecipe(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return HiredProductionRecipe.brewing(buffer.readResourceLocation(), buffer.readResourceLocation());
        var recipe = buffer.readResourceLocation();
        int size = VillagerPayloads.readCollectionSize(buffer, 9, "Ingredient choices");
        var alternatives = new LinkedHashMap<Integer, net.minecraft.resources.ResourceLocation>();
        for (int index = 0; index < size; index++) {
            if (alternatives.putIfAbsent(buffer.readVarInt(), buffer.readResourceLocation()) != null) {
                throw new IllegalArgumentException("Duplicate ingredient choice");
            }
        }
        return HiredProductionRecipe.exact(recipe, alternatives);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public sealed interface Command permits View, Append, Edit {
    }

    public record View() implements Command {
    }

    public record Append(HiredProductionRecipe recipe, HiredProductionOrder.Mode mode, int amount, BlockPos storage) implements Command {
        public Append {
            Objects.requireNonNull(recipe);
            Objects.requireNonNull(mode);
            storage = storage == null ? null : storage.immutable();
            if (amount < 1 || amount > HiredProductionOrder.MAX_AMOUNT
                    || mode == HiredProductionOrder.Mode.MAINTAIN_STOCK && storage == null) throw new IllegalArgumentException("Invalid production amount or storage");
        }
    }

    public record Edit(UUID order, HiredProductionService.Edit edit, int index) implements Command {
        public Edit {
            Objects.requireNonNull(order);
            Objects.requireNonNull(edit);
            if (index < 0 || index >= 32) throw new IllegalArgumentException("Invalid production order position");
        }
    }

    private enum Kind {
        VIEW, APPEND, EDIT
    }
}
