package com.jvn.villagerretaliation.interaction;

import java.util.Locale;
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
        String qualifier = separator > 0 ? path.substring(0, separator) : "";
        int qualifierSeparator = Math.max(qualifier.lastIndexOf('/'), qualifier.lastIndexOf('.'));
        if (qualifierSeparator >= 0) {
            qualifier = qualifier.substring(qualifierSeparator + 1);
        }
        String category = switch (value.toLowerCase(Locale.ROOT)) {
            case "exceptional" -> "Exceptional Favorites";
            case "loved" -> "Favorites";
            case "liked" -> "Useful Items";
            case "neutral" -> "Everyday Items";
            case "disliked" -> "Unwanted Items";
            case "dangerous" -> "Serious Hazards";
            case "hated" -> "Severe Hazards";
            default -> "";
        };
        if (!category.isBlank() && !qualifier.isBlank() && !"global".equalsIgnoreCase(qualifier)) {
            return Component.literal(titleCase(qualifier) + " " + category);
        }
        return Component.literal(titleCase(value));
    }

    private static String titleCase(String value) {
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
        return name.isEmpty() ? value : name.toString();
    }
}
