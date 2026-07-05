package com.jvn.villagerretaliation.client.interaction;

import com.jvn.toucanlib.client.ToucanGuiText;
import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
import com.jvn.villagerretaliation.skill.VillagerSkillValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
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

        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (entry.isEmpty()) {
            context.requestProfileRefresh();
            VillagerInteractionUiUtil.drawScaledString(
                    graphics,
                    context.font(),
                    context.translate("profile.loading"),
                    left,
                    top + context.experimentalUnit(32),
                    context.infoSecondaryColor(),
                    context.experimentalTextScale());
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
        int columnWidth = (context.skillsPanelWidth() - context.profileSkillColumnGap()) / context.profileSkillColumns();
        for (int index = 0; index < highlights.size(); index++) {
            VillagerSkillValue skillValue = highlights.get(index);
            int column = index % context.profileSkillColumns();
            int row = index / context.profileSkillColumns();
            int rowLeft = left + column * (columnWidth + context.profileSkillColumnGap());
            int y = skillRowTop(context, top, row);
            int horizontalHitPadding = context.experimentalUnit(2);
            int verticalHitPadding = context.experimentalUnit(1);
            boolean rowHovered = mouseX >= rowLeft - horizontalHitPadding
                    && mouseX <= rowLeft + columnWidth + horizontalHitPadding
                    && mouseY >= y - verticalHitPadding
                    && mouseY <= y + context.profileSkillRowHeight() - verticalHitPadding;
            if (rowHovered) {
                return skillValue.skill();
            }
        }
        return null;
    }

    static int skillsInfoContentHeight(Context context) {
        float scale = context.experimentalTextScale();
        int width = VillagerInteractionUiUtil.scaledWrapWidth(context.optionWidth() - context.experimentalUnit(12), scale);
        int y = context.optionStride();
        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (context.selectedSkillDetails() != null && entry.isPresent()) {
            VillagerProfileClientCache.DisplayEntry profile = entry.get();
            y = wrappedInfoLineBottom(
                    context.font(),
                    Component.translatable(
                            GUI_KEY_PREFIX + "profile.tooltip.level",
                            context.localizedSkillRank(profile.skillRank(context.selectedSkillDetails()))),
                    y,
                    width,
                    scale);
            y = wrappedInfoLineBottom(
                    context.font(),
                    Component.translatable(GUI_KEY_PREFIX + "profile.tooltip.score", profile.skillValue(context.selectedSkillDetails())),
                    y + context.experimentalUnit(2),
                    width,
                    scale);
            return wrappedInfoLineBottom(
                    context.font(),
                    Component.literal(context.localizedExpandedSkillDescription(context.selectedSkillDetails())),
                    y + context.experimentalUnit(4),
                    width,
                    scale);
        }
        y = wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), y, width, scale);
        y = wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), y + context.experimentalUnit(4), width, scale);
        return wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), y + context.experimentalUnit(4), width, scale);
    }

    private static void renderSkillsInfo(Context context, GuiGraphics graphics) {
        int left = context.skillInfoTextLeft();
        int viewportTop = context.skillInfoViewportTop();
        int viewportBottom = context.skillInfoViewportBottom();
        int top = Mth.floor(viewportTop + context.optionTextYOffset() - context.skillScroll());
        float scale = context.experimentalTextScale();
        int width = VillagerInteractionUiUtil.scaledWrapWidth(context.optionWidth() - context.experimentalUnit(12), scale);
        int scissorOffsetY = context.guiScissorOffsetY();
        graphics.enableScissor(
                context.skillInfoScissorLeft(),
                viewportTop + scissorOffsetY,
                context.skillInfoScissorRight(),
                viewportBottom + scissorOffsetY);
        VillagerInteractionUiUtil.drawScaledString(
                graphics,
                context.font(),
                context.selectedSkillDetails() == null
                        ? context.translate("profile.skills.info.title")
                        : context.localizedSkill(context.selectedSkillDetails()),
                left,
                top,
                context.infoValueColor(),
                scale);
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
                    y + context.experimentalUnit(2),
                    width);
            renderWrappedSkillInfoLine(
                    context,
                    graphics,
                    Component.literal(context.localizedExpandedSkillDescription(context.selectedSkillDetails())),
                    left,
                    y + context.experimentalUnit(4),
                    width);
        } else {
            y = renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), left, y, width);
            y = renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), left, y + context.experimentalUnit(4), width);
            renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), left, y + context.experimentalUnit(4), width);
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
            VillagerInteractionUiUtil.drawScaledString(graphics, context.font(), line, left, y, VillagerInteractionUiUtil.withAlpha(context.infoSecondaryColor(), alpha), context.experimentalTextScale());
            y += VillagerInteractionUiUtil.scaledLineStep(context.font(), context.experimentalTextScale());
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
        int columnWidth = (context.skillsPanelWidth() - context.profileSkillColumnGap()) / context.profileSkillColumns();
        float scale = context.experimentalTextScale();
        VillagerInteractionUiUtil.drawScaledString(graphics, context.font(), context.translate("profile.skills"), left, top, context.infoValueColor(), scale);
        VillagerSkill hovered = null;
        for (int index = 0; index < highlights.size(); index++) {
            VillagerSkillValue skillValue = highlights.get(index);
            VillagerSkill skill = skillValue.skill();
            int column = index % context.profileSkillColumns();
            int row = index / context.profileSkillColumns();
            int rowLeft = left + column * (columnWidth + context.profileSkillColumnGap());
            int y = skillRowTop(context, top, row);
            int horizontalHitPadding = context.experimentalUnit(2);
            int verticalHitPadding = context.experimentalUnit(1);
            boolean rowHovered = mouseX >= rowLeft - horizontalHitPadding
                    && mouseX <= rowLeft + columnWidth + horizontalHitPadding
                    && mouseY >= y - verticalHitPadding
                    && mouseY <= y + context.profileSkillRowHeight() - verticalHitPadding;
            if (rowHovered) {
                hovered = skill;
            }

            int labelWrapWidth = VillagerInteractionUiUtil.scaledWrapWidth(columnWidth, scale);
            String label = ToucanGuiText.fitText(context.font(), context.localizedSkill(skill), labelWrapWidth);
            VillagerInteractionUiUtil.drawScaledString(graphics, context.font(), label, rowLeft, y, context.infoSecondaryColor(), scale);
            renderSkillBar(
                    context,
                    graphics,
                    rowLeft,
                    y + VillagerInteractionUiUtil.scaledLineStep(context.font(), scale) + context.experimentalUnit(1),
                    columnWidth,
                    skillValue.value(),
                    skillValue.rank(),
                    rowHovered);
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
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("Click for more info").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        VillagerInteractionUiUtil.renderScaledComponentTooltip(graphics, context.font(), tooltip, mouseX, mouseY, context.experimentalTextScale());
    }

    private static void renderSkillBar(Context context, GuiGraphics graphics, int left, int top, int width, int value, VillagerSkillRank rank, boolean hovered) {
        int rankColor = skillRankColor(rank);
        VillagerInteractionScreenShaderRenderer.renderExperimentalSkillBar(
                graphics,
                left,
                top,
                left + width,
                top + context.profileSkillBarHeight(),
                rankColor,
                Mth.clamp(value / 100.0F, 0.0F, 1.0F),
                context.experimentalChromeAlpha(),
                experimentalTicks(),
                hovered);
    }

    private static float experimentalTicks() {
        return (Util.getMillis() % 1_000_000L) / 50.0F;
    }

    private static int skillRowTop(Context context, int contentTop, int row) {
        return contentTop
                + VillagerInteractionUiUtil.scaledLineStep(context.font(), context.experimentalTextScale())
                + context.experimentalUnit(4)
                + row * (context.profileSkillRowHeight() + context.profileSkillRowGap());
    }

    private static int skillRankColor(VillagerSkillRank rank) {
        return switch (rank) {
            case NOVICE -> 0xFFE7E4D8;
            case APPRENTICE -> 0xFFE5FF35;
            case SKILLED -> 0xFF37DFFF;
            case EXPERT -> 0xFFFFD21F;
            case MASTER -> 0xFFFF3FEA;
        };
    }

    private static int wrappedInfoLineBottom(Font font, Component component, int top, int width, float scale) {
        int lines = font.split(component, width).size();
        return top + lines * VillagerInteractionUiUtil.scaledLineStep(font, scale);
    }

    interface Context {
        Font font();

        int optionsLeft();

        int skillInfoTextLeft();

        int skillInfoScissorLeft();

        int skillInfoScissorRight();

        int optionWidth();

        int skillsPanelLeft();

        int skillsPanelWidth();

        int skillsPanelTop();

        int skillsContainerHeight();

        int skillsContainerPaddingX();

        int skillsContainerPaddingY();

        int profileSkillRowHeight();

        int profileSkillRowGap();

        int profileSkillBarHeight();

        int profileSkillColumns();

        int profileSkillColumnGap();

        float experimentalChromeAlpha();

        int experimentalUnit(int value);

        int infoValueColor();

        int infoSecondaryColor();

        float skillScroll();

        int optionStride();

        float optionTextYOffset();

        float experimentalTextScale();

        int skillInfoViewportTop();

        int skillInfoViewportBottom();

        float skillInfoEdgeFadeAlpha(float lineY, int viewportTop, int viewportBottom);

        default int guiScissorOffsetY() {
            return 0;
        }

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
