package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.profile.VillagerProfileClientCache;
import com.jvn.villagerretaliation.combat.VillagerCombatSkillBehavior;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.skill.VillagerSkill;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

final class VillagerInteractionJobStatsPage {
    private static final int CONTAINER_WIDTH = 431;
    private static final int CONTAINER_HEIGHT = 139;
    private static final int TITLE_TOP = 6;
    private static final int FIRST_COLUMN_X = 10;
    private static final int SECOND_COLUMN_X = 219;
    private static final int FIRST_ROW_Y = 25;
    private static final int ROWS_PER_COLUMN = 7;
    private static final int ROW_STRIDE = 15;
    private static final int BAR_X_OFFSET = 103;
    private static final int BAR_TOP_OFFSET = 1;
    private static final int BAR_BASE_WIDTH = 102;
    private static final int BAR_BASE_HEIGHT = 7;
    private static final int BAR_FILL_WIDTH = 100;
    private static final int BAR_FILL_HEIGHT = 5;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_OUTLINE_COLOR = 0xFF000000;
    private static final int READY_TEXT_COLOR = 0xFF55FF55;
    private static final int LOCKED_TEXT_COLOR = 0xFFFF5555;

    private VillagerInteractionJobStatsPage() {
    }

    static void render(Context context, GuiGraphics graphics, int mouseX, int mouseY) {
        int left = context.panelLeft();
        int top = context.panelTop();
        Optional<VillagerProfileClientCache.DisplayEntry> entry = context.profileEntry();
        if (entry.isEmpty()) {
            context.requestProfileRefresh();
        }

        graphics.pose().pushPose();
        graphics.pose().translate(left, top, 0.0F);
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_CONTAINER_TEXTURE,
                0, 0, 0, 0,
                CONTAINER_WIDTH, CONTAINER_HEIGHT,
                CONTAINER_WIDTH, CONTAINER_HEIGHT);
        Font font = context.font();
        String title = context.translate("job_stats.title");
        drawOutlinedString(graphics, font, title, (CONTAINER_WIDTH - font.width(title)) / 2, TITLE_TOP);

        if (entry.isEmpty()) {
            drawOutlinedString(graphics, font, context.translate("profile.loading"), FIRST_COLUMN_X, FIRST_ROW_Y);
            graphics.pose().popPose();
            return;
        }

        VillagerProfileClientCache.DisplayEntry profile = entry.get();
        HiredVillagerRole[] roles = HiredVillagerRole.values();
        for (int index = 0; index < roles.length; index++) {
            HiredVillagerRole role = roles[index];
            int column = index / ROWS_PER_COLUMN;
            int row = index % ROWS_PER_COLUMN;
            int textX = column == 0 ? FIRST_COLUMN_X : SECOND_COLUMN_X;
            int textY = FIRST_ROW_Y + row * ROW_STRIDE;
            int aptitude = HiredVillagerRoles.roleScore(profile.skills(), role);
            boolean available = HiredVillagerRoles.isSkillUnlocked(
                    profile.professionKey(), context.baby(), profile.skills(), role);
            int fill = Mth.clamp(aptitude, 0, BAR_FILL_WIDTH);
            drawOutlinedString(
                    graphics,
                    font,
                    context.roleLabel(role),
                    textX,
                    textY,
                    available ? READY_TEXT_COLOR : LOCKED_TEXT_COLOR);
            renderBar(graphics, textX + BAR_X_OFFSET, textY + BAR_TOP_OFFSET, fill);
        }
        graphics.pose().popPose();

        HiredVillagerRole hovered = roleAt(context, mouseX, mouseY);
        if (hovered != null) {
            renderTooltip(context, graphics, profile, hovered, mouseX, mouseY);
        }
    }

    static HiredVillagerRole roleAt(Context context, double mouseX, double mouseY) {
        double localX = mouseX - context.panelLeft();
        double localY = mouseY - context.panelTop();
        if (localX < 0.0D || localX >= CONTAINER_WIDTH || localY < 0.0D || localY >= CONTAINER_HEIGHT) {
            return null;
        }
        HiredVillagerRole[] roles = HiredVillagerRole.values();
        for (int index = 0; index < roles.length; index++) {
            int column = index / ROWS_PER_COLUMN;
            int row = index % ROWS_PER_COLUMN;
            int left = column == 0 ? FIRST_COLUMN_X : SECOND_COLUMN_X;
            int top = FIRST_ROW_Y + row * ROW_STRIDE;
            if (localX >= left && localX < left + 205 && localY >= top && localY < top + ROW_STRIDE) {
                return roles[index];
            }
        }
        return null;
    }

    private static void renderTooltip(
            Context context,
            GuiGraphics graphics,
            VillagerProfileClientCache.DisplayEntry profile,
            HiredVillagerRole role,
            int mouseX,
            int mouseY) {
        int aptitude = HiredVillagerRoles.roleScore(profile.skills(), role);
        boolean available = HiredVillagerRoles.isSkillUnlocked(
                profile.professionKey(), context.baby(), profile.skills(), role);
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.literal(context.roleLabel(role)).withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable(
                "villagerretaliation.gui.job_stats.tooltip.readiness",
                context.translate(available ? "job_stats.ready" : "job_stats.locked")).withStyle(
                        available ? ChatFormatting.GREEN : ChatFormatting.RED));
        tooltip.add(Component.translatable(
                "villagerretaliation.gui.job_stats.detail.aptitude", aptitude).withStyle(ChatFormatting.GRAY));
        addPerformanceTooltip(tooltip, profile, role, aptitude);
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("villagerretaliation.gui.job_stats.tooltip.click")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        VillagerInteractionUiUtil.renderScaledComponentTooltip(
                graphics, context.font(), tooltip, mouseX, mouseY, 1.0F);
    }

    private static void addPerformanceTooltip(
            List<Component> tooltip,
            VillagerProfileClientCache.DisplayEntry profile,
            HiredVillagerRole role,
            int aptitude) {
        ChatFormatting color = ChatFormatting.GRAY;
        switch (role) {
            case COURIER -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.detail.courier_transfer",
                    HiredVillagerRoles.courierTransferLimit(aptitude)).withStyle(color));
            case CRAFTSMAN, COOK, SMELTER, BREWING -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.detail.transfer",
                    HiredVillagerRoles.transferLimit(
                            HiredVillagerRoles.baseTransferItems(role),
                            HiredVillagerRoles.transferCapacityPercent(aptitude))).withStyle(color));
            case MINING, LOGGING -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.detail.block_speed",
                    HiredVillagerRoles.blockWorkSpeedPercent(aptitude)).withStyle(color));
            case BUILDER -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.detail.build_speed",
                    HiredVillagerRoles.roleCadencePercent(role, aptitude)).withStyle(color));
            case COMBAT -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.tooltip.combat_performance",
                    VillagerCombatSkillBehavior.meleeAttackSpeedPercent(
                            profile.skillValue(VillagerSkill.GUARDING)),
                    VillagerCombatSkillBehavior.meleeDamagePercent(
                            profile.skillValue(VillagerSkill.GUARDING)),
                    VillagerCombatSkillBehavior.rangedSpreadPercent(
                            profile.skillValue(VillagerSkill.ARCHERY))).withStyle(color));
            case HUNTING -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.tooltip.hunting_performance",
                    VillagerCombatSkillBehavior.rangedAttackSpeedPercent(
                            profile.skillValue(VillagerSkill.ARCHERY)),
                    VillagerCombatSkillBehavior.rangedSpreadPercent(
                            profile.skillValue(VillagerSkill.ARCHERY)),
                    HiredVillagerRoles.roleCadencePercent(role, aptitude)).withStyle(color));
            default -> tooltip.add(Component.translatable(
                    "villagerretaliation.gui.job_stats.detail.work_speed",
                    HiredVillagerRoles.roleActionSpeedPercent(role, aptitude) != 100
                            ? HiredVillagerRoles.roleActionSpeedPercent(role, aptitude)
                            : HiredVillagerRoles.roleCadencePercent(role, aptitude)).withStyle(color));
        }
    }

    private static void renderBar(GuiGraphics graphics, int left, int top, int fillWidth) {
        graphics.blit(
                VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_BAR_BASE_TEXTURE,
                left, top, 0, 0,
                BAR_BASE_WIDTH, BAR_BASE_HEIGHT,
                BAR_BASE_WIDTH, BAR_BASE_HEIGHT);
        if (fillWidth > 0) {
            graphics.blit(
                    VillagerRetaliationClientAssets.INTERACTION_CONTAINER_SKILLS_BAR_FILL_TEXTURE,
                    left + 1, top + 1, 0, 0,
                    fillWidth, BAR_FILL_HEIGHT,
                    BAR_FILL_WIDTH, BAR_FILL_HEIGHT);
        }
    }

    private static void drawOutlinedString(GuiGraphics graphics, Font font, String text, int x, int y) {
        drawOutlinedString(graphics, font, text, x, y, TEXT_COLOR);
    }

    private static void drawOutlinedString(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        graphics.drawString(font, text, x - 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x + 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x, y - 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x, y + 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    interface Context {
        Font font();

        int panelLeft();

        int panelTop();

        boolean baby();

        String translate(String key, Object... args);

        String roleLabel(HiredVillagerRole role);

        Optional<VillagerProfileClientCache.DisplayEntry> profileEntry();

        void requestProfileRefresh();
    }
}
