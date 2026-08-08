package com.jvn.villagerretaliation.interaction;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record GiftCategoryName(String translationKey, String text) {
    public static final GiftCategoryName EMPTY = new GiftCategoryName("", "");

    public GiftCategoryName {
        translationKey = translationKey == null ? "" : translationKey;
        text = text == null ? "" : text;
    }

    public Component component(ResourceLocation categoryId) {
        if (!this.translationKey.isBlank()) {
            return Component.translatable(this.translationKey);
        }
        if (!this.text.isBlank()) {
            return Component.literal(this.text);
        }
        String path = categoryId == null ? "gift" : categoryId.getPath();
        int separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('.'));
        String value = separator >= 0 ? path.substring(separator + 1) : path;
        String[] words = value.replace('-', '_').split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.literal(name.isEmpty() ? value : name.toString());
    }
}
