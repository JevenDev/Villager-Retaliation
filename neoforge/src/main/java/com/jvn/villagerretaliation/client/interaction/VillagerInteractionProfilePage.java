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

    private VillagerInteractionProfilePage() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int left = context.optionsLeft() + 6;
        int top = context.profileChartTopLimit();
        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (entry.isEmpty()) {
            context.requestProfileRefresh();
            graphics.drawString(context.font(), context.translate("profile.loading"), left, top + 32, context.infoSecondaryColor(), false);
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        int chartRadius = resolvedChartRadius(context);
        int centerX = left + context.optionWidth() / 2 - context.profileChartCenterXOffset();
        int centerY = resolvedCenterY(context, chartRadius, top);
        VillagerSocialAttribute hoveredAttribute = profileChartPointAt(context, profile, centerX, centerY, chartRadius, mouseX, mouseY);
        renderProfileChart(context, graphics, profile, centerX, centerY, chartRadius, hoveredAttribute);
        if (hoveredAttribute != null) {
            renderProfileAttributeTooltip(context, graphics, profile, hoveredAttribute, mouseX, mouseY);
        }
    }

    private static void renderProfileChart(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            int centerX,
            int centerY,
            int chartRadius,
            VillagerSocialAttribute hoveredAttribute) {
        Font font = context.font();
        VillagerSocialAttribute[] attributes = VillagerSocialAttribute.values();
        ProfilePoint[] outer = new ProfilePoint[attributes.length];
        ProfilePoint[] values = new ProfilePoint[attributes.length];

        for (int index = 0; index < attributes.length; index++) {
            double angle = profileAttributeAngle(index, attributes.length);
            outer[index] = profilePoint(centerX, centerY, angle, chartRadius);
            int valueRadius = Math.round(chartRadius * profile.value(attributes[index]) / 100.0F);
            values[index] = profilePoint(centerX, centerY, angle, valueRadius);
            drawPixelLine(graphics, centerX, centerY, outer[index].x(), outer[index].y(), context.profileChartAxisColor());

            String label = context.localizedAttribute(attributes[index]);
            int labelX = profilePoint(centerX, centerY, angle, chartRadius + context.profileChartLabelXOffset()).x() - font.width(label) / 2;
            int labelY = profilePoint(centerX, centerY, angle, chartRadius + context.profileChartLabelYOffset()).y() - font.lineHeight / 2;
            graphics.drawString(font, label, labelX, labelY, context.infoSecondaryColor(), false);
        }

        for (int index = 0; index < attributes.length; index++) {
            int next = (index + 1) % attributes.length;
            drawPixelLine(graphics, outer[index].x(), outer[index].y(), outer[next].x(), outer[next].y(), context.profileChartOutlineColor());
            drawPixelLine(graphics, values[index].x(), values[index].y(), values[next].x(), values[next].y(), context.profileChartValueColor());
            boolean hovered = attributes[index] == hoveredAttribute;
            int pointRadius = hovered ? context.profileChartPointHoverRadius() : context.profileChartPointRadius();
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
            Context context,
            VillagerProfileClientCache.DisplayEntry profile,
            int centerX,
            int centerY,
            int chartRadius,
            int mouseX,
            int mouseY) {
        VillagerSocialAttribute[] attributes = VillagerSocialAttribute.values();
        VillagerSocialAttribute closestAttribute = null;
        int closestDistance = context.profileChartPointHitRadius() * context.profileChartPointHitRadius() + 1;
        for (int index = 0; index < attributes.length; index++) {
            double angle = profileAttributeAngle(index, attributes.length);
            int valueRadius = Math.round(chartRadius * profile.value(attributes[index]) / 100.0F);
            ProfilePoint point = profilePoint(centerX, centerY, angle, valueRadius);
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

    private static int resolvedCenterY(Context context, int chartRadius, int topLimit) {
        int preferredCenterY = topLimit + chartRadius + context.profileChartCenterYOffset();
        int labelPadding = context.profileChartLabelYOffset() + context.font().lineHeight / 2;
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
            int mouseY) {
        VillagerSocialAttributeRank rank = profile.rank(attribute);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.localizedAttribute(attribute)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.level", context.localizedRank(rank)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.value(attribute)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(context.localizedAttributeDescription(attribute)).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(context.font(), tooltip, mouseX, mouseY);
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

        int profileChartTopLimit();

        int profileChartBottomLimit();

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
}
