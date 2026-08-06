package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeRank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class VillagerInteractionProfilePage {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";

    private static final int PROFILE_TOOLTIP_MAX_WIDTH = 220;
    private VillagerInteractionProfilePage() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int left = context.optionsLeft() + 6;
        int top = context.profileChartTopLimit();
        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (entry.isEmpty()) {
            context.requestProfileRefresh();
            VillagerInteractionUiUtil.drawScaledString(
                    graphics,
                    context.font(),
                    context.translate("profile.loading"),
                    left,
                    top + context.profileChartLoadingYOffset(),
                    context.infoSecondaryColor(),
                    context.profileChartTextScale());
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        float scale = context.profileChartTextScale();
        int chartRadius = resolvedChartRadius(context);
        int centerX = left + context.optionWidth() / 2 - context.profileChartCenterXOffset();
        int centerY = resolvedCenterY(context, chartRadius, top);
        ProfileTransform transform = new ProfileTransform(centerX, centerY, scale);
        double localMouseX = transform.localX(mouseX);
        double localMouseY = transform.localY(mouseY);
        int localMouseXi = Mth.floor(localMouseX);
        int localMouseYi = Mth.floor(localMouseY);
        int localChartRadius = resolvedLocalChartRadius(context, scale);
        VillagerSocialAttribute hoveredAttribute = profileChartPointAt(
                profile,
                localChartRadius,
                localHitRadius(context, scale),
                localMouseXi,
                localMouseYi);

        graphics.pose().pushPose();
        graphics.pose().translate(transform.centerX(), transform.centerY(), 0.0F);
        graphics.pose().scale(transform.scale(), transform.scale(), 1.0F);
        renderProfileChart(context, graphics, profile, localChartRadius, scale, hoveredAttribute);
        if (hoveredAttribute != null) {
            renderProfileAttributeTooltip(context, graphics, profile, hoveredAttribute, localMouseXi, localMouseYi, transform.scale(), transform.centerX(), transform.centerY());
        }
        graphics.pose().popPose();
    }

    static VillagerSocialAttribute attributeAt(
            Context context,
            VillagerProfileClientCache.DisplayEntry profile,
            double mouseX,
            double mouseY) {
        int left = context.optionsLeft() + 6;
        int top = context.profileChartTopLimit();
        float scale = context.profileChartTextScale();
        int chartRadius = resolvedChartRadius(context);
        int centerX = left + context.optionWidth() / 2 - context.profileChartCenterXOffset();
        int centerY = resolvedCenterY(context, chartRadius, top);
        ProfileTransform transform = new ProfileTransform(centerX, centerY, scale);
        int localMouseX = Mth.floor(transform.localX(mouseX));
        int localMouseY = Mth.floor(transform.localY(mouseY));
        return profileChartPointAt(
                profile,
                resolvedLocalChartRadius(context, scale),
                localHitRadius(context, scale),
                localMouseX,
                localMouseY);
    }

    private static void renderProfileChart(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            int chartRadius,
            float scale,
            VillagerSocialAttribute hoveredAttribute) {
        Font font = context.font();
        VillagerSocialAttribute[] attributes = VillagerSocialAttribute.values();
        ProfilePoint[] outer = new ProfilePoint[attributes.length];
        ProfilePoint[] values = new ProfilePoint[attributes.length];
        int labelXOffset = localUnit(context.profileChartLabelXOffset(), scale);
        int labelYOffset = localUnit(context.profileChartLabelYOffset(), scale);

        for (int index = 0; index < attributes.length; index++) {
            double angle = profileAttributeAngle(index, attributes.length);
            outer[index] = profilePoint(0, 0, angle, chartRadius);
            int valueRadius = Math.round(chartRadius * profile.value(attributes[index]) / 100.0F);
            values[index] = profilePoint(0, 0, angle, valueRadius);
            drawPixelLine(graphics, 0, 0, outer[index].x(), outer[index].y(), context.profileChartAxisColor());

            String label = context.localizedAttribute(attributes[index]);
            int labelX = profilePoint(0, 0, angle, chartRadius + labelXOffset).x() - font.width(label) / 2;
            int labelY = profilePoint(0, 0, angle, chartRadius + labelYOffset).y() - font.lineHeight / 2;
            graphics.drawString(font, label, labelX, labelY, context.infoSecondaryColor(), false);
        }

        for (int index = 0; index < attributes.length; index++) {
            int next = (index + 1) % attributes.length;
            drawPixelLine(graphics, outer[index].x(), outer[index].y(), outer[next].x(), outer[next].y(), context.profileChartOutlineColor());
            drawPixelLine(graphics, values[index].x(), values[index].y(), values[next].x(), values[next].y(), context.profileChartValueColor());
            boolean hovered = attributes[index] == hoveredAttribute;
            int pointRadius = hovered
                    ? localUnit(context.profileChartPointHoverRadius(), scale)
                    : localUnit(context.profileChartPointRadius(), scale);
            int pointColor = hovered ? context.profileChartPointHoverColor() : context.profileChartPointColor();
            graphics.fill(
                    values[index].x() - pointRadius,
                    values[index].y() - pointRadius,
                    values[index].x() + pointRadius + 1,
                    values[index].y() + pointRadius + 1,
                    pointColor
            );
        }
    }

    private static VillagerSocialAttribute profileChartPointAt(
            VillagerProfileClientCache.DisplayEntry profile,
            int chartRadius,
            int hitRadius,
            int mouseX,
            int mouseY) {
        VillagerSocialAttribute[] attributes = VillagerSocialAttribute.values();
        VillagerSocialAttribute closestAttribute = null;
        int closestDistance = hitRadius * hitRadius + 1;
        for (int index = 0; index < attributes.length; index++) {
            double angle = profileAttributeAngle(index, attributes.length);
            int valueRadius = Math.round(chartRadius * profile.value(attributes[index]) / 100.0F);
            ProfilePoint point = profilePoint(0, 0, angle, valueRadius);
            int dx = mouseX - point.x();
            int dy = mouseY - point.y();
            int distance = dx * dx + dy * dy;
            if (distance < closestDistance) {
                closestAttribute = attributes[index];
                closestDistance = distance;
            }
        }
        return closestAttribute;
    }

    private static int resolvedChartRadius(Context context) {
        return Math.max(4, context.profileChartRadius());
    }

    private static int resolvedLocalChartRadius(Context context, float scale) {
        return Math.max(4, localUnit(context.profileChartRadius(), scale));
    }

    private static int localHitRadius(Context context, float scale) {
        return Math.max(2, localUnit(context.profileChartPointHitRadius(), scale));
    }

    private static int localUnit(int scaledValue, float scale) {
        return Math.max(1, Math.round(scaledValue / Math.max(scale, 0.001F)));
    }

    private static int resolvedCenterY(Context context, int chartRadius, int topLimit) {
        int preferredCenterY = topLimit + chartRadius + context.profileChartCenterYOffset();
        int labelPadding = context.profileChartLabelYOffset()
                + Math.round(context.font().lineHeight * context.profileChartTextScale() * 0.5F);
        int minCenterY = topLimit + chartRadius + labelPadding;
        int maxCenterY = context.profileChartBottomLimit() - chartRadius - labelPadding;
        if (maxCenterY < minCenterY) {
            return Mth.floor((topLimit + context.profileChartBottomLimit()) * 0.5F);
        }
        return Mth.clamp(preferredCenterY, minCenterY, Math.max(minCenterY, maxCenterY));
    }

    private static void renderProfileAttributeTooltip(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            VillagerSocialAttribute attribute,
            int mouseX,
            int mouseY,
            float scale,
            int originX,
            int originY) {
        VillagerSocialAttributeRank rank = profile.rank(attribute);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.localizedAttribute(attribute)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.level", context.localizedRank(rank)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.value(attribute)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        appendWrappedTooltipText(tooltip, context.font(), context.localizedAttributeDescription(attribute));
        VillagerInteractionUiUtil.renderBoundedComponentTooltipInCurrentPose(graphics, context.font(), tooltip, mouseX, mouseY, scale, originX, originY);
    }

    private static void appendWrappedTooltipText(List<Component> tooltip, Font font, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (paragraph.isBlank()) {
                tooltip.add(Component.empty());
                continue;
            }

            String remaining = paragraph.stripLeading();
            while (!remaining.isEmpty()) {
                String line = font.plainSubstrByWidth(remaining, PROFILE_TOOLTIP_MAX_WIDTH);
                if (line.isEmpty()) {
                    int nextCodePointEnd = remaining.offsetByCodePoints(0, 1);
                    line = remaining.substring(0, nextCodePointEnd);
                    remaining = remaining.substring(nextCodePointEnd).stripLeading();
                } else if (line.length() < remaining.length()) {
                    int breakIndex = lastWhitespaceBreak(line);
                    if (breakIndex > 0) {
                        line = remaining.substring(0, breakIndex);
                        remaining = remaining.substring(breakIndex).stripLeading();
                    } else {
                        remaining = remaining.substring(line.length()).stripLeading();
                    }
                } else {
                    remaining = "";
                }
                tooltip.add(Component.literal(line.stripTrailing()).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static int lastWhitespaceBreak(String text) {
        for (int index = text.length(); index > 0; ) {
            int codePoint = text.codePointBefore(index);
            index -= Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                return index;
            }
        }
        return -1;
    }

    private static double profileAttributeAngle(int index, int attributeCount) {
        return -Math.PI / 2.0D + index * Math.PI * 2.0D / attributeCount;
    }

    private static ProfilePoint profilePoint(int centerX, int centerY, double angle, int radius) {
        int x = centerX + Mth.floor(Math.cos(angle) * radius);
        int y = centerY + Mth.floor(Math.sin(angle) * radius);
        return new ProfilePoint(x, y);
    }

    private static void drawPixelLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int error = dx - dy;
        int x = x0;
        int y = y0;

        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                return;
            }
            int doubledError = error * 2;
            if (doubledError > -dy) {
                error -= dy;
                x += sx;
            }
            if (doubledError < dx) {
                error += dx;
                y += sy;
            }
        }
    }

    interface Context {
        Font font();

        int optionsLeft();

        int conversationInfoTop();

        int optionWidth();

        int infoSecondaryColor();

        int profileChartRadius();

        int profileChartCenterXOffset();

        int profileChartCenterYOffset();

        int profileChartLabelXOffset();

        int profileChartLabelYOffset();

        int profileChartLoadingYOffset();

        int profileChartTopLimit();

        int profileChartBottomLimit();

        float profileChartTextScale();

        int profileChartAxisColor();

        int profileChartOutlineColor();

        int profileChartValueColor();

        int profileChartPointColor();

        int profileChartPointHoverColor();

        int profileChartPointRadius();

        int profileChartPointHoverRadius();

        int profileChartPointHitRadius();

        String localizedAttribute(VillagerSocialAttribute attribute);

        String localizedRank(VillagerSocialAttributeRank rank);

        String localizedAttributeDescription(VillagerSocialAttribute attribute);

        String translate(String key, Object... args);

        Optional<VillagerProfileClientCache.DisplayEntry> profileEntry();

        void requestProfileRefresh();
    }

    private record ProfilePoint(int x, int y) {
    }

    private record ProfileTransform(int centerX, int centerY, float scale) {
        double localX(double screenX) {
            return (screenX - this.centerX) / Math.max(this.scale, 0.001F);
        }

        double localY(double screenY) {
            return (screenY - this.centerY) / Math.max(this.scale, 0.001F);
        }
    }
}
