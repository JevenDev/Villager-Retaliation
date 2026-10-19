package com.jvn.villagerretaliation.client.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

final class CampaignDescription {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)(?:\\s+#+)?$");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^]\\n]*)]\\(([^\\s)]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final String[] MARKERS = {"***", "___", "**", "__", "~~", "*", "_", "`"};

    private CampaignDescription() { }

    static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            var heading = HEADING.matcher(line);
            int level = heading.matches() ? heading.group(1).length() : 0;
            String text = level > 0 ? heading.group(2) : line;
            float scale = level == 1 ? 2 : level == 2 ? 1.5F : level > 0 ? 1.25F : 1;
            Style style = level > 0 ? Style.EMPTY.withBold(true) : Style.EMPTY;
            var images = IMAGE.matcher(text);
            int start = 0;
            while (images.find()) {
                if (images.start() > start) blocks.add(new Block(inline(text.substring(start, images.start()), style), scale, null, ""));
                blocks.add(new Block(Component.literal(images.group(1)), 1, images.group(2), images.group(1)));
                start = images.end();
            }
            if (start < text.length() || text.isEmpty()) blocks.add(new Block(inline(text.substring(start), style), scale, null, ""));
        }
        return List.copyOf(blocks);
    }

    private static MutableComponent inline(String text, Style style) {
        return inline(text, style, new LegacyFormatting());
    }

    private static MutableComponent inline(String text, Style style, LegacyFormatting formatting) {
        MutableComponent result = Component.empty();
        StringBuilder plain = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '\\' && index + 1 < text.length()) {
                plain.append(text.charAt(index + 1));
                index += 2;
                continue;
            }
            if (text.charAt(index) == '§' && index + 1 < text.length()) {
                ChatFormatting code = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(index + 1)));
                if (code != null) {
                    if (!plain.isEmpty()) {
                        result.append(Component.literal(plain.toString()).setStyle(formatting.style.applyTo(style)));
                        plain.setLength(0);
                    }
                    formatting.style = switch (code) {
                        case RESET -> Style.EMPTY;
                        case BOLD -> formatting.style.withBold(true);
                        case ITALIC -> formatting.style.withItalic(true);
                        case UNDERLINE -> formatting.style.withUnderlined(true);
                        case STRIKETHROUGH -> formatting.style.withStrikethrough(true);
                        case OBFUSCATED -> formatting.style.withObfuscated(true);
                        default -> Style.EMPTY.withColor(code);
                    };
                    index += 2;
                    continue;
                }
            }
            String marker = null;
            int end = -1;
            for (String candidate : MARKERS) {
                if (!text.startsWith(candidate, index)) continue;
                int close = text.indexOf(candidate, index + candidate.length());
                if (close > index + candidate.length()) {
                    marker = candidate;
                    end = close;
                    break;
                }
            }
            if (marker == null) {
                plain.append(text.charAt(index++));
                continue;
            }
            if (!plain.isEmpty()) {
                result.append(Component.literal(plain.toString()).setStyle(formatting.style.applyTo(style)));
                plain.setLength(0);
            }
            Style nested = switch (marker) {
                case "***", "___" -> style.withBold(true).withItalic(true);
                case "**", "__" -> style.withBold(true);
                case "*", "_" -> style.withItalic(true);
                case "~~" -> style.withStrikethrough(true);
                default -> style.withColor(0xe6c78a);
            };
            String body = text.substring(index + marker.length(), end);
            result.append(marker.equals("`") ? Component.literal(body).setStyle(formatting.style.applyTo(nested)) : inline(body, nested, formatting));
            index = end + marker.length();
        }
        if (!plain.isEmpty()) result.append(Component.literal(plain.toString()).setStyle(formatting.style.applyTo(style)));
        return result;
    }

    static ResourceLocation imageLocation(ResourceLocation campaign, String destination) {
        if (destination.contains("://") || destination.startsWith("/") || destination.contains("\\") || destination.contains("..")) return null;
        if (destination.contains(":")) {
            ResourceLocation id = ResourceLocation.tryParse(destination);
            return id != null && !id.getPath().startsWith("/") ? id : null;
        }
        return ResourceLocation.tryBuild(campaign.getNamespace(), "world_campaigns/" + campaign.getPath() + "/" + destination);
    }

    private static final class LegacyFormatting {
        private Style style = Style.EMPTY;
    }

    record Block(Component text, float scale, String image, String alt) { }
}
