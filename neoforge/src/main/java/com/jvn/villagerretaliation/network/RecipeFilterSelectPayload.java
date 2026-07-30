package com.jvn.villagerretaliation.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Selects one exact server-validated worker recipe, or clears the open Recipe Filter. */
public record RecipeFilterSelectPayload(String recipeId) implements CustomPacketPayload {
    public static final Type<RecipeFilterSelectPayload> TYPE =
            VillagerPayloads.type("recipe_filter_select");
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeFilterSelectPayload> STREAM_CODEC =
            VillagerPayloads.codec(RecipeFilterSelectPayload::encode, RecipeFilterSelectPayload::decode);

    public RecipeFilterSelectPayload {
        recipeId = recipeId == null ? "" : recipeId;
    }

    private static void encode(RegistryFriendlyByteBuf buffer, RecipeFilterSelectPayload payload) {
        buffer.writeUtf(payload.recipeId(), 256);
    }

    private static RecipeFilterSelectPayload decode(RegistryFriendlyByteBuf buffer) {
        return new RecipeFilterSelectPayload(buffer.readUtf(256));
    }

    public ResourceLocation parsedRecipeId() {
        return recipeId.isBlank() ? null : ResourceLocation.tryParse(recipeId);
    }

    public boolean valid() {
        return recipeId.isBlank() || parsedRecipeId() != null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
