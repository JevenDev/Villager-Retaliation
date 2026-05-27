package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.skill.VillagerSkillValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

final class VillagerInteractionSkillsPage {
    private static final String GUI_KEY_PREFIX = "villagerretaliation.gui.";

    private VillagerInteractionSkillsPage() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        renderSkillsInfo(context, graphics);

        int left = context.skillsPanelLeft();
        int top = context.skillsPanelTop();
        renderSkillsContainerBackground(context, graphics, left, top);

        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (entry.isEmpty()) {
            context.requestProfileRefresh();
            graphics.drawString(context.font(), context.translate("profile.loading"), left, top + 32, context.infoSecondaryColor(), false);
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        int contentTop = top + context.skillsContainerPaddingY();
        VillagerSkill hoveredSkill = renderProfileSkills(context, graphics, profile, left, contentTop, mouseX, mouseY);
        if (hoveredSkill != null) {
            renderProfileSkillTooltip(context, graphics, profile, hoveredSkill, mouseX, mouseY);
        }
    }

    static VillagerSkill skillAt(Context context, VillagerProfileClientCache.DisplayEntry profile, double mouseX, double mouseY) {
        int left = context.skillsPanelLeft();
        int top = context.skillsPanelTop() + context.skillsContainerPaddingY();
        List<VillagerSkillValue> highlights = profile.bestSkills(VillagerSkill.values().length);
        int columnWidth = (context.optionWidth() - 8 - context.profileSkillColumnGap()) / context.profileSkillColumns();
        for (int index = 0; index < highlights.size(); index++) {
            VillagerSkillValue skillValue = highlights.get(index);
            int column = index % context.profileSkillColumns();
            int row = index / context.profileSkillColumns();
            int rowLeft = left + column * (columnWidth + context.profileSkillColumnGap());
            int y = top + context.font().lineHeight + 4 + row * (context.profileSkillRowHeight() + context.profileSkillRowGap());
            boolean rowHovered = mouseX >= rowLeft - 2
                    && mouseX <= rowLeft + columnWidth + 2
                    && mouseY >= y - 1
                    && mouseY <= y + context.profileSkillRowHeight() - 1;
            if (rowHovered) {
                return skillValue.skill();
            }
        }
        return null;
    }

    static int skillsInfoContentHeight(Context context) {
        int width = context.optionWidth() - 12;
        int y = context.font().lineHeight + context.optionStride() - context.font().lineHeight;
        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (context.selectedSkillDetails() != null && entry.isPresent()) {
            VillagerProfileClientCache.DisplayEntry profile = entry.get();
            y = wrappedInfoLineBottom(
                    context.font(),
                    Component.translatable(
                            GUI_KEY_PREFIX + "profile.tooltip.level",
                            context.localizedSkillRank(profile.skillRank(context.selectedSkillDetails()))),
                    y,
                    width);
            y = wrappedInfoLineBottom(
                    context.font(),
                    Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(context.selectedSkillDetails())),
                    y + 2,
                    width);
            return wrappedInfoLineBottom(
                    context.font(),
                    Component.literal(context.localizedExpandedSkillDescription(context.selectedSkillDetails())),
                    y + 4,
                    width);
        }
        y = wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), y, width);
        y = wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), y + 4, width);
        return wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), y + 4, width);
    }

    private static void renderSkillsContainerBackground(Context context, GuiGraphics graphics, int left, int top) {
        int containerLeft = left - context.skillsContainerPaddingX();
        int containerTop = top;
        int containerRight = left + context.optionWidth();
        int containerBottom = top + context.skillsContainerHeight();
        graphics.fill(containerLeft, containerTop, containerRight, containerBottom, context.skillsContainerBackgroundColor());
        graphics.fill(containerLeft, containerTop, containerLeft + 2, containerBottom, context.skillsContainerStripeColor());
        graphics.fill(containerLeft, containerBottom, containerRight, containerBottom + 1, context.skillsContainerShadowColor());
    }

    private static void renderSkillsInfo(Context context, GuiGraphics graphics) {
        int left = context.optionsLeft() + 6;
        int viewportTop = context.skillInfoViewportTop();
        int viewportBottom = context.skillInfoViewportBottom();
        int top = Mth.floor(VillagerInteractionConversationPanel.optionTextTop(viewportTop) - context.skillScroll());
        int width = context.optionWidth() - 12;
        graphics.enableScissor(left - 18, viewportTop, left + context.optionWidth() + 4, viewportBottom);
        graphics.drawString(
                context.font(),
                context.selectedSkillDetails() == null
                        ? context.translate("profile.skills.info.title")
                        : context.localizedSkill(context.selectedSkillDetails()),
                left,
                top,
                context.infoValueColor(),
                false);
        int y = top + context.optionStride();
        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (context.selectedSkillDetails() != null && entry.isPresent()) {
            VillagerProfileClientCache.DisplayEntry profile = entry.get();
            y = renderWrappedSkillInfoLine(
                    context,
                    graphics,
                    Component.translatable(
                            GUI_KEY_PREFIX + "profile.tooltip.level",
                            context.localizedSkillRank(profile.skillRank(context.selectedSkillDetails()))),
                    left,
                    y,
                    width);
            y = renderWrappedSkillInfoLine(
                    context,
                    graphics,
                    Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(context.selectedSkillDetails())),
                    left,
                    y + 2,
                    width);
            renderWrappedSkillInfoLine(
                    context,
                    graphics,
                    Component.literal(context.localizedExpandedSkillDescription(context.selectedSkillDetails())),
                    left,
                    y + 4,
                    width);
        } else {
            y = renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), left, y, width);
            y = renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), left, y + 4, width);
            renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), left, y + 4, width);
        }
        graphics.disableScissor();
        context.renderSkillInfoScrollbar(graphics);
    }

    private static int renderWrappedSkillInfoLine(
            Context context,
            GuiGraphics graphics,
            Component component,
            int left,
            int top,
            int width) {
        int y = top;
        int viewportTop = context.skillInfoViewportTop();
        int viewportBottom = context.skillInfoViewportBottom();
        for (FormattedCharSequence line : context.font().split(component, width)) {
            float alpha = context.skillInfoEdgeFadeAlpha(y, viewportTop, viewportBottom);
            graphics.drawString(context.font(), line, left, y, VillagerInteractionUiUtil.withAlpha(context.infoSecondaryColor(), alpha), false);
            y += context.font().lineHeight + 2;
        }
        return y;
    }

    private static VillagerSkill renderProfileSkills(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            int left,
            int top,
            int mouseX,
            int mouseY) {
        List<VillagerSkillValue> highlights = profile.bestSkills(VillagerSkill.values().length);
        int columnWidth = (context.optionWidth() - 8 - context.profileSkillColumnGap()) / context.profileSkillColumns();
        graphics.drawString(context.font(), context.translate("profile.skills"), left, top, context.infoValueColor(), false);
        VillagerSkill hovered = null;
        for (int index = 0; index < highlights.size(); index++) {
            VillagerSkillValue skillValue = highlights.get(index);
            VillagerSkill skill = skillValue.skill();
            int column = index % context.profileSkillColumns();
            int row = index / context.profileSkillColumns();
            int rowLeft = left + column * (columnWidth + context.profileSkillColumnGap());
            int y = top + context.font().lineHeight + 4 + row * (context.profileSkillRowHeight() + context.profileSkillRowGap());
            boolean rowHovered = mouseX >= rowLeft - 2
                    && mouseX <= rowLeft + columnWidth + 2
                    && mouseY >= y - 1
                    && mouseY <= y + context.profileSkillRowHeight() - 1;
            if (rowHovered) {
                hovered = skill;
                graphics.fill(rowLeft - 2, y - 1, rowLeft + columnWidth + 2, y + context.profileSkillRowHeight() - 1, 0x22FFFFFF);
            }

            String label = fitText(context.font(), context.localizedSkill(skill), columnWidth);
            graphics.drawString(context.font(), label, rowLeft, y, context.infoSecondaryColor(), false);
            renderSkillBar(context, graphics, rowLeft, y + context.font().lineHeight + 1, columnWidth, skillValue.value(), skillValue.rank());
        }
        return hovered;
    }

    private static void renderProfileSkillTooltip(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            VillagerSkill skill,
            int mouseX,
            int mouseY) {
        VillagerSkillRank rank = profile.skillRank(skill);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.localizedSkill(skill)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.level", context.localizedSkillRank(rank)).withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(skill)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.literal(context.localizedSkillDescription(skill)).withStyle(ChatFormatting.GRAY));
        graphics.renderComponentTooltip(context.font(), tooltip, mouseX, mouseY);
    }

    private static void renderSkillBar(Context context, GuiGraphics graphics, int left, int top, int width, int value, VillagerSkillRank rank) {
        int fillWidth = Mth.clamp(Math.round(width * value / 100.0F), 1, width);
        graphics.fill(left, top, left + width, top + context.profileSkillBarHeight(), 0x55332F2A);
        graphics.fill(left, top, left + fillWidth, top + context.profileSkillBarHeight(), skillRankColor(rank));
        graphics.fill(left, top, left + width, top + 1, 0x40FFFFFF);
    }

    private static int skillRankColor(VillagerSkillRank rank) {
        return switch (rank) {
            case NOVICE -> 0xB8D5D0C6;
            case APPRENTICE -> 0xD0DDE7A4;
            case SKILLED -> 0xD0A8D8F0;
            case EXPERT -> 0xD0E9C46A;
            case MASTER -> 0xFFEFB0FF;
        };
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }

        String suffix = "...";
        int suffixWidth = font.width(suffix);
        if (maxWidth <= suffixWidth) {
            return font.plainSubstrByWidth(text, maxWidth);
        }
        return font.plainSubstrByWidth(text, maxWidth - suffixWidth) + suffix;
    }

    private static int wrappedInfoLineBottom(Font font, Component component, int top, int width) {
        int lines = font.split(component, width).size();
        return top + lines * (font.lineHeight + 2);
    }

    interface Context {
        Font font();

        int optionsLeft();

        int optionWidth();

        int skillsPanelLeft();

        int skillsPanelTop();

        int skillsContainerHeight();

        int skillsContainerPaddingX();

        int skillsContainerPaddingY();

        int skillsContainerBackgroundColor();

        int skillsContainerStripeColor();

        int skillsContainerShadowColor();

        int profileSkillRowHeight();

        int profileSkillRowGap();

        int profileSkillBarHeight();

        int profileSkillColumns();

        int profileSkillColumnGap();

        int infoValueColor();

        int infoSecondaryColor();

        float skillScroll();

        int optionStride();

        int skillInfoViewportTop();

        int skillInfoViewportBottom();

        float skillInfoEdgeFadeAlpha(float lineY, int viewportTop, int viewportBottom);

        VillagerSkill selectedSkillDetails();

        String localizedSkill(VillagerSkill skill);

        String localizedSkillRank(VillagerSkillRank rank);

        String localizedExpandedSkillDescription(VillagerSkill skill);

        String localizedSkillDescription(VillagerSkill skill);

        String translate(String key, Object... args);

        Optional<VillagerProfileClientCache.DisplayEntry> profileEntry();

        void requestProfileRefresh();

        void renderSkillInfoScrollbar(GuiGraphics graphics);
    }
}
