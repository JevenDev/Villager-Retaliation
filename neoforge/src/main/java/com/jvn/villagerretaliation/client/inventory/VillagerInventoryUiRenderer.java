package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.villager.VillagerHungerClientCache;
import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared rendering primitives for villager inventory screens and read-only reflections. */
public final class VillagerInventoryUiRenderer {
    public static final int MODEL_SCALE = 30;
    private static final int STAT_ICON_SIZE = 11;
    private static final int STAT_ROW_GAP = 1;
    private static final int STAT_TEXT_GAP = 3;
    private static final int HEALTH_COLOR = 0xFFFF1313;
    private static final int ARMOR_COLOR = 0xFFB8B9C4;
    private static final int HUNGER_COLOR = 0xFFB88458;
    private static final int TIMER_COLOR = 0xFFFFD45C;
    private static final int TEXT_OUTLINE_COLOR = 0xFF000000;
    private static final int NAME_COLOR = 0xFF404040;
    private static final ResourceLocation HEALTH_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_health_stat.png");
    private static final ResourceLocation ARMOR_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_armor_stat.png");
    private static final ResourceLocation HUNGER_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_hunger_stat.png");
    private static final ResourceLocation TIMER_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_timer_stat.png");
    public static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    public static final ResourceLocation EMPTY_MAINHAND_SLOT =
            ResourceLocation.withDefaultNamespace("item/empty_slot_sword");

    private VillagerInventoryUiRenderer() {
    }

    /** Resolves a synchronized entity by stable UUID, using the numeric id only as a fast path. */
    public static LivingEntity resolveLivingEntity(int entityId, UUID entityUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || entityUuid == null) return null;
        var entity = minecraft.level.getEntity(entityId);
        if (entity instanceof LivingEntity livingEntity && entityUuid.equals(entity.getUUID())) {
            return livingEntity;
        }
        for (var candidate : minecraft.level.entitiesForRendering()) {
            if (candidate instanceof LivingEntity livingEntity && entityUuid.equals(candidate.getUUID())) {
                return livingEntity;
            }
        }
        return null;
    }

    public static void renderModel(
            GuiGraphics graphics,
            LivingEntity entity,
            int left,
            int top,
            int right,
            int bottom,
            float mouseX,
            float mouseY) {
        float centerX = (left + right) / 2.0F;
        float centerY = (top + bottom) / 2.0F;
        float mouseYaw = (float) Math.atan((centerX - mouseX) / 40.0F);
        float mousePitch = (float) Math.atan((centerY - mouseY) / 40.0F);
        Quaternionf entityRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation =
                new Quaternionf().rotateX(mousePitch * 20.0F * ((float) Math.PI / 180.0F));
        entityRotation.mul(cameraRotation);

        float previousBodyRot = entity.yBodyRot;
        float previousYRot = entity.getYRot();
        float previousXRot = entity.getXRot();
        float previousHeadRotO = entity.yHeadRotO;
        float previousHeadRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + mouseYaw * 20.0F;
        entity.setYRot(180.0F + mouseYaw * 40.0F);
        entity.setXRot(-mousePitch * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        graphics.enableScissor(left, top, right, bottom);
        try (VillagerModelPreviewRenderContext.Scope ignored = VillagerModelPreviewRenderContext.begin(
                entity, VillagerModelPreviewRenderContext.PreviewType.INVENTORY)) {
            float scale = Math.max(0.01F, entity.getScale());
            InventoryScreen.renderEntityInInventory(
                    graphics,
                    centerX,
                    centerY,
                    MODEL_SCALE / scale,
                    new Vector3f(0.0F, entity.getBbHeight() / 2.0F + 0.0625F * scale, 0.0F),
                    entityRotation,
                    cameraRotation,
                    entity);
        } finally {
            graphics.disableScissor();
            entity.yBodyRot = previousBodyRot;
            entity.setYRot(previousYRot);
            entity.setXRot(previousXRot);
            entity.yHeadRotO = previousHeadRotO;
            entity.yHeadRot = previousHeadRot;
        }
    }

    public static void renderReadOnlyEquipmentSlot(
            GuiGraphics graphics,
            LivingEntity entity,
            EquipmentSlot equipmentSlot,
            int left,
            int top,
            double mouseX,
            double mouseY) {
        renderReadOnlySlot(
                graphics,
                equipmentItem(entity, equipmentSlot),
                emptySlotIcon(equipmentSlot),
                left,
                top,
                mouseX,
                mouseY);
    }

    public static void renderReadOnlySlot(
            GuiGraphics graphics,
            ItemStack stack,
            ResourceLocation emptySlotIcon,
            int left,
            int top,
            double mouseX,
            double mouseY) {
        if (stack.isEmpty()) {
            graphics.blit(
                    left + 1,
                    top + 1,
                    0,
                    16,
                    16,
                    Minecraft.getInstance()
                            .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                            .apply(emptySlotIcon));
        } else {
            graphics.renderItem(stack, left + 1, top + 1);
            graphics.renderItemDecorations(Minecraft.getInstance().font, stack, left + 1, top + 1);
        }
        if (containsSlot(left, top, mouseX, mouseY)) {
            graphics.fill(left + 1, top + 1, left + 17, top + 17, 0x80FFFFFF);
        }
    }

    public static void renderCenteredName(
            GuiGraphics graphics, Component name, int centerX, int top, int maxWidth) {
        var font = Minecraft.getInstance().font;
        String text = displayedName(name, maxWidth);
        graphics.drawString(font, text, centerX - font.width(text) / 2, top, NAME_COLOR, false);
    }

    public static boolean isCenteredNameHovered(
            Component name,
            int centerX,
            int top,
            int maxWidth,
            double mouseX,
            double mouseY) {
        var font = Minecraft.getInstance().font;
        String text = displayedName(name, maxWidth);
        int width = font.width(text);
        int left = centerX - width / 2;
        return mouseX >= left
                && mouseX < left + width
                && mouseY >= top
                && mouseY < top + font.lineHeight;
    }

    private static String displayedName(Component name, int maxWidth) {
        var font = Minecraft.getInstance().font;
        String text = name == null ? "" : name.getString();
        if (text.isBlank()) text = Component.translatable("entity.minecraft.villager").getString();
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
    }

    public static void renderStats(
            GuiGraphics graphics, LivingEntity entity, long remainingContractTicks, int centerX, int top) {
        if (entity == null) return;
        renderStat(
                graphics,
                ARMOR_ICON,
                Integer.toString(entity.getArmorValue()),
                ARMOR_COLOR,
                centerX,
                top);
        renderStat(
                graphics,
                HEALTH_ICON,
                formatValue(entity.getHealth()),
                HEALTH_COLOR,
                centerX,
                top + STAT_ICON_SIZE + STAT_ROW_GAP);
        renderStat(
                graphics,
                HUNGER_ICON,
                Integer.toString(VillagerHungerClientCache.hunger(entity)),
                HUNGER_COLOR,
                centerX,
                top + (STAT_ICON_SIZE + STAT_ROW_GAP) * 2);
        renderStat(
                graphics,
                TIMER_ICON,
                remainingContractTicks < 0L
                        ? ""
                        : VillagerContractTimerFormatter.compact(remainingContractTicks),
                TIMER_COLOR,
                centerX,
                top + (STAT_ICON_SIZE + STAT_ROW_GAP) * 3);
    }

    public static boolean isTimerStatHovered(
            long remainingContractTicks, int centerX, int top, double mouseX, double mouseY) {
        if (remainingContractTicks < 0L) {
            return false;
        }
        String value = VillagerContractTimerFormatter.compact(remainingContractTicks);
        int timerTop = top + (STAT_ICON_SIZE + STAT_ROW_GAP) * 3;
        int width = statWidth(value);
        int left = centerX - width / 2;
        return mouseX >= left
                && mouseX < left + width
                && mouseY >= timerTop
                && mouseY < timerTop + STAT_ICON_SIZE;
    }

    public static void renderTimerStatTooltip(
            GuiGraphics graphics, long remainingContractTicks, int mouseX, int mouseY) {
        if (remainingContractTicks < 0L) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        graphics.renderTooltip(
                minecraft.font,
                List.of(
                        Component.translatable("gui.villagerretaliation.inventory.contract_time_remaining")
                                .withStyle(ChatFormatting.GRAY),
                        Component.literal(VillagerContractTimerFormatter.full(remainingContractTicks))
                                .withStyle(ChatFormatting.GOLD)),
                java.util.Optional.empty(),
                mouseX,
                mouseY);
    }

    private static void renderStat(
            GuiGraphics graphics, ResourceLocation icon, String value, int color, int centerX, int top) {
        int left = centerX - statWidth(value) / 2;
        graphics.blit(icon, left, top, 0, 0, STAT_ICON_SIZE, STAT_ICON_SIZE, STAT_ICON_SIZE, STAT_ICON_SIZE);
        drawOutlinedString(
                graphics,
                Component.literal(value),
                left + STAT_ICON_SIZE + STAT_TEXT_GAP,
                top + 2,
                color);
    }

    private static int statWidth(String value) {
        int textWidth = Minecraft.getInstance().font.width(value);
        return STAT_ICON_SIZE + (textWidth == 0 ? 0 : STAT_TEXT_GAP + textWidth);
    }

    private static void drawOutlinedString(
            GuiGraphics graphics, Component text, int left, int top, int color) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, text, left - 1, top, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, left + 1, top, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, left, top - 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, left, top + 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(font, text, left, top, color, false);
    }

    private static String formatValue(float value) {
        return Math.abs(value - Math.round(value)) < 0.01F
                ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    public static void renderItemTooltip(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        if (stack.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        TooltipFlag flag = minecraft.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        Item.TooltipContext context = minecraft.level == null
                ? Item.TooltipContext.EMPTY
                : Item.TooltipContext.of(minecraft.level);
        graphics.renderTooltip(
                minecraft.font,
                stack.getTooltipLines(context, minecraft.player, flag).stream()
                        .map(Component::getVisualOrderText)
                        .toList(),
                mouseX,
                mouseY);
    }

    public static boolean containsSlot(int left, int top, double mouseX, double mouseY) {
        return mouseX >= left && mouseX < left + 18 && mouseY >= top && mouseY < top + 18;
    }

    public static ItemStack equipmentItem(LivingEntity entity, EquipmentSlot equipmentSlot) {
        return entity == null ? ItemStack.EMPTY : entity.getItemBySlot(equipmentSlot);
    }

    public static ResourceLocation emptySlotIcon(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case FEET -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            case MAINHAND -> EMPTY_MAINHAND_SLOT;
            case OFFHAND -> InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD;
            default -> throw new IllegalArgumentException("Unsupported inventory equipment slot: " + equipmentSlot);
        };
    }
}
