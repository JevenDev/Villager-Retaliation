package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.item.BlueprintChecklistItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public record BlueprintChecklistSyncPayload(
        InteractionHand hand,
        String title,
        List<EntryView> entries,
        boolean openScreen) implements CustomPacketPayload {
    public static final Type<BlueprintChecklistSyncPayload> TYPE =
            VillagerPayloads.type("blueprint_checklist_sync");
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintChecklistSyncPayload> STREAM_CODEC =
            VillagerPayloads.codec(BlueprintChecklistSyncPayload::encode, BlueprintChecklistSyncPayload::decode);

    public BlueprintChecklistSyncPayload {
        hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        title = title == null ? "" : title;
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, BlueprintChecklistSyncPayload payload) {
        buffer.writeEnum(payload.hand());
        buffer.writeUtf(payload.title(), 256);
        int size = Math.min(payload.entries().size(), BlueprintChecklistItem.MAX_ENTRIES);
        buffer.writeVarInt(size);
        for (int index = 0; index < size; index++) {
            EntryView entry = payload.entries().get(index);
            ItemStack.STREAM_CODEC.encode(buffer, entry.item().copyWithCount(1));
            buffer.writeVarInt(Math.max(1, entry.required()));
            buffer.writeVarInt(Math.max(0, entry.observed()));
            buffer.writeBoolean(entry.checked());
        }
        buffer.writeBoolean(payload.openScreen());
    }

    private static BlueprintChecklistSyncPayload decode(RegistryFriendlyByteBuf buffer) {
        InteractionHand hand = buffer.readEnum(InteractionHand.class);
        String title = buffer.readUtf(256);
        int size = VillagerPayloads.readCollectionSize(
                buffer, BlueprintChecklistItem.MAX_ENTRIES, "blueprint checklist entries");
        List<EntryView> entries = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            entries.add(new EntryView(
                    ItemStack.STREAM_CODEC.decode(buffer),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean()));
        }
        return new BlueprintChecklistSyncPayload(hand, title, entries, buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record EntryView(ItemStack item, int required, int observed, boolean checked) {
        public EntryView {
            item = item == null ? ItemStack.EMPTY : item.copyWithCount(1);
            required = Math.max(1, required);
            observed = Math.max(0, observed);
        }
    }
}
