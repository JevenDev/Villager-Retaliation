package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Narrows one selected recipe ingredient to a server-validated exact item alternative. */
public record RecipeFilterIngredientPayload(int slot, String itemId) implements CustomPacketPayload {
    public static final Type<RecipeFilterIngredientPayload> TYPE =
            VillagerPayloads.type("recipe_filter_ingredient");
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeFilterIngredientPayload> STREAM_CODEC =
            VillagerPayloads.codec(
                    RecipeFilterIngredientPayload::encode,
                    RecipeFilterIngredientPayload::decode);

    public RecipeFilterIngredientPayload {
        itemId = itemId == null ? "" : itemId;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RecipeFilterIngredientPayload payload) {
        buffer.writeByte(payload.slot());
        buffer.writeUtf(payload.itemId(), 256);
    }

    private static RecipeFilterIngredientPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RecipeFilterIngredientPayload(buffer.readByte(), buffer.readUtf(256));
    }

    public ResourceLocation parsedItemId() {
        return itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
    }

    public boolean valid() {
        return slot >= 0
                && slot < VillagerRecipeFilterData.MAX_INGREDIENTS
                && (itemId.isBlank() || parsedItemId() != null);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
