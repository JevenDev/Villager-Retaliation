package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import com.jvn.villagerretaliation.skill.VillagerSkillRank;
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
    private static final int SKILLS_CONTAINER_WIDTH = 431;
    private static final int SKILLS_CONTAINER_HEIGHT = 139;
    private static final int SKILLS_TITLE_TOP = 6;
    private static final int SKILLS_FIRST_COLUMN_X = 10;
    private static final int SKILLS_SECOND_COLUMN_X = 219;
    private static final int SKILLS_FIRST_ROW_Y = 26;
    private static final int SKILLS_ROWS_PER_COLUMN = 9;
    private static final int SKILLS_ROW_GAP = 3;
    private static final int SKILLS_TEXT_COLOR = 0xFFFFFFFF;
    private static final int SKILLS_TEXT_OUTLINE_COLOR = 0xFF000000;
    private static final int SKILL_BAR_X_OFFSET = 101;
    private static final int SKILL_BAR_TOP_OFFSET = 1;
    private static final int SKILL_BAR_BASE_WIDTH = 102;
    private static final int SKILL_BAR_BASE_HEIGHT = 7;
    private static final int SKILL_BAR_FILL_WIDTH = 100;
    private static final int SKILL_BAR_FILL_HEIGHT = 5;

    private VillagerInteractionSkillsPage() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int left = context.skillsPanelLeft();
        int top = context.skillsPanelTop();

        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (entry.isEmpty()) {
            context.requestProfileRefresh();
            renderSkillsContainer(context, graphics, null, left, top);
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        VillagerSkill hoveredSkill = skillAt(context, profile, mouseX, mouseY);
        renderSkillsContainer(context, graphics, profile, left, top);
        if (hoveredSkill != null) {
            renderProfileSkillTooltip(context, graphics, profile, hoveredSkill, mouseX, mouseY);
        }
    }

    static VillagerSkill skillAt(Context context, VillagerProfileClientCache.DisplayEntry profile, double mouseX, double mouseY) {
        int left = context.skillsPanelLeft();
        int top = context.skillsPanelTop();
        double localMouseX = mouseX - left;
        double localMouseY = mouseY - top;
        if (localMouseX < 0.0D
                || localMouseX >= SKILLS_CONTAINER_WIDTH
                || localMouseY < 0.0D
                || localMouseY >= SKILLS_CONTAINER_HEIGHT) {
            return null;
        }

        VillagerSkill[] skills = VillagerSkill.values();
        for (int index = 0; index < skills.length; index++) {
            int column = index / SKILLS_ROWS_PER_COLUMN;
            int row = index % SKILLS_ROWS_PER_COLUMN;
            int rowLeft = skillTextX(column);
            int rowRight = skillBarLeft(rowLeft) + SKILL_BAR_BASE_WIDTH;
            int y = skillRowTop(context, row);
            boolean rowHovered = localMouseX >= rowLeft
                    && localMouseX <= rowRight
                    && localMouseY >= y
                    && localMouseY < y + skillRowStride(context);
            if (rowHovered) {
                return skills[index];
            }
        }
        return null;
    }

    static int skillsInfoContentHeight(Context context) {
        float scale = context.textScale();
        int width = VillagerInteractionUiUtil.scaledWrapWidth(context.optionWidth() - context.uiUnit(12), scale);
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
                    y + context.uiUnit(2),
                    width,
                    scale);
            return wrappedInfoLineBottom(
                    context.font(),
                    Component.literal(context.localizedExpandedSkillDescription(context.selectedSkillDetails())),
                    y + context.uiUnit(4),
                    width,
                    scale);
        }
        y = wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), y, width, scale);
        y = wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), y + context.uiUnit(4), width, scale);
        return wrappedInfoLineBottom(context.font(), Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), y + context.uiUnit(4), width, scale);
    }

    private static void renderSkillsInfo(Context context, GuiGraphics graphics) {
        int left = context.skillInfoTextLeft();
        int viewportTop = context.skillInfoViewportTop();
        int viewportBottom = context.skillInfoViewportBottom();
        int top = Mth.floor(viewportTop + context.optionTextYOffset() - context.skillScroll());
        float scale = context.textScale();
        int width = VillagerInteractionUiUtil.scaledWrapWidth(context.optionWidth() - context.uiUnit(12), scale);
        int scissorOffsetY = context.guiScissorOffsetY();
        int scissorOffsetX = context.guiScissorOffsetX();
        graphics.enableScissor(
                context.skillInfoScissorLeft() + scissorOffsetX,
                viewportTop + scissorOffsetY,
                context.skillInfoScissorRight() + scissorOffsetX,
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
                    y + context.uiUnit(2),
                    width);
            renderWrappedSkillInfoLine(
                    context,
                    graphics,
                    Component.literal(context.localizedExpandedSkillDescription(context.selectedSkillDetails())),
                    left,
                    y + context.uiUnit(4),
                    width);
        } else {
            y = renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.trade"), left, y, width);
            y = renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.specialty"), left, y + context.uiUnit(4), width);
            renderWrappedSkillInfoLine(context, graphics, Component.translatable(GUI_KEY_PREFIX + "profile.skills.info.recruit"), left, y + context.uiUnit(4), width);
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
            VillagerInteractionUiUtil.drawScaledString(graphics, context.font(), line, left, y, VillagerInteractionUiUtil.withAlpha(context.infoSecondaryColor(), alpha), context.textScale());
            y += VillagerInteractionUiUtil.scaledLineStep(context.font(), context.textScale());
        }
        return y;
    }

    private static void renderSkillsContainer(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            int left,
            int top) {
        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_CONTAINER_TEXTURE,
                0,
                0,
                0,
                0,
                SKILLS_CONTAINER_WIDTH,
                SKILLS_CONTAINER_HEIGHT,
                SKILLS_CONTAINER_WIDTH,
                SKILLS_CONTAINER_HEIGHT);

        Font font = context.font();
        String title = context.translate("skills.title");
        drawOutlinedString(graphics, font, title, (SKILLS_CONTAINER_WIDTH - font.width(title)) / 2, SKILLS_TITLE_TOP);

        if (profile == null) {
            drawOutlinedString(graphics, font, context.translate("profile.loading"), SKILLS_FIRST_COLUMN_X, SKILLS_FIRST_ROW_Y);
            graphics.pose().popPose();
            return;
        }

        VillagerSkill[] skills = VillagerSkill.values();
        for (int index = 0; index < skills.length; index++) {
            VillagerSkill skill = skills[index];
            int column = index / SKILLS_ROWS_PER_COLUMN;
            int row = index % SKILLS_ROWS_PER_COLUMN;
            int textX = skillTextX(column);
            int textY = skillRowTop(context, row);

            drawOutlinedString(graphics, font, context.localizedSkill(skill), textX, textY);
            renderSkillBar(graphics, skillBarLeft(textX), textY + SKILL_BAR_TOP_OFFSET, profile.skillValue(skill));
        }
        graphics.pose().popPose();
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
        tooltip.add(Component.translatable("villagerretaliation.gui.skills.click_for_more")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        VillagerInteractionUiUtil.renderScaledComponentTooltip(graphics, context.font(), tooltip, mouseX, mouseY, 1.0F);
    }

    private static void renderSkillBar(GuiGraphics graphics, int left, int top, int value) {
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_BAR_BASE_TEXTURE,
                left,
                top,
                0,
                0,
                SKILL_BAR_BASE_WIDTH,
                SKILL_BAR_BASE_HEIGHT,
                SKILL_BAR_BASE_WIDTH,
                SKILL_BAR_BASE_HEIGHT);
        int fillWidth = Mth.clamp(value, 0, SKILL_BAR_FILL_WIDTH);
        if (fillWidth <= 0) {
            return;
        }
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_BAR_FILL_TEXTURE,
                left + 1,
                top + 1,
                0,
                0,
                fillWidth,
                SKILL_BAR_FILL_HEIGHT,
                SKILL_BAR_FILL_WIDTH,
                SKILL_BAR_FILL_HEIGHT);
    }

    private static int skillRowTop(Context context, int row) {
        return SKILLS_FIRST_ROW_Y + row * skillRowStride(context);
    }

    private static int skillRowStride(Context context) {
        return context.font().lineHeight + SKILLS_ROW_GAP;
    }

    private static int skillTextX(int column) {
        return column == 0 ? SKILLS_FIRST_COLUMN_X : SKILLS_SECOND_COLUMN_X;
    }

    private static int skillBarLeft(int textX) {
        return textX + SKILL_BAR_X_OFFSET;
    }

    private static void drawOutlinedString(GuiGraphics graphics, Font font, String text, int x, int y) {
        graphics.drawString(font, text, x - 1, y, SKILLS_TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x + 1, y, SKILLS_TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x, y - 1, SKILLS_TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x, y + 1, SKILLS_TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x, y, SKILLS_TEXT_COLOR, false);
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

        float uiAlpha();

        int uiUnit(int value);

        int infoValueColor();

        int infoSecondaryColor();

        float skillScroll();

        int optionStride();

        float optionTextYOffset();

        float textScale();

        int skillInfoViewportTop();

        int skillInfoViewportBottom();

        float skillInfoEdgeFadeAlpha(float lineY, int viewportTop, int viewportBottom);

        default int guiScissorOffsetY() {
            return 0;
        }

        default int guiScissorOffsetX() {
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
