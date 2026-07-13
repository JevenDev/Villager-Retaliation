package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.client.villager.VillagerHungerClientCache;
import com.jvn.villagerretaliation.inventory.ProtectedVillagerProperty;
import com.jvn.villagerretaliation.inventory.VillagerConfiscatedStolenItemTracker;
import com.jvn.villagerretaliation.inventory.VillagerGiftReturnTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryMenu;
import com.jvn.villagerretaliation.inventory.VillagerTradePaymentTracker;
import com.jvn.villagerretaliation.network.VillagerJobInventoryRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VillagerInventoryScreen extends AbstractContainerScreen<VillagerInventoryMenu> {
    private static final int TEXTURE_WIDTH = 182;
    private static final int TEXTURE_HEIGHT = 188;
    private static final int PLAYER_TEXTURE_WIDTH = 176;
    private static final int PLAYER_TEXTURE_HEIGHT = 90;
    private static final int PLAYER_TEXTURE_GAP = 4;
    private static final int PLAYER_TEXTURE_TOP = TEXTURE_HEIGHT + PLAYER_TEXTURE_GAP;
    private static final int PLAYER_TEXTURE_LEFT = 4;
    private static final int PLAYER_TEXTURE_BESIDE_LEFT = TEXTURE_WIDTH + PLAYER_TEXTURE_GAP;
    private static final int PLAYER_TEXTURE_BESIDE_TOP = (TEXTURE_HEIGHT - PLAYER_TEXTURE_HEIGHT) / 2;
    private static final int ENTITY_LEFT = 66;
    private static final int ENTITY_TOP = 26;
    private static final int ENTITY_RIGHT = 115;
    private static final int ENTITY_BOTTOM = 94;
    private static final int ENTITY_SCALE = 31;

    private static final int NAMEPLATE_TOP = 0;
    private static final int TAB_BUTTON_TOP = 1;
    private static final int NAMEPLATE_TEXTURE_WIDTH = 42;
    private static final int NAMEPLATE_HEIGHT = 18;
    private static final int NAMEPLATE_HORIZONTAL_PADDING = 8;
    private static final int NAMEPLATE_TEXT_Y_OFFSET = 3;
    private static final int NAMEPLATE_SLICE = 8;
    private static final int TAB_BUTTON_WIDTH = 12;
    private static final int TAB_BUTTON_HEIGHT = 14;
    private static final int TAB_BUTTON_OVERLAP = 1;
    private static final int TAB_HIGHLIGHT_INSET = 2;
    private static final int TAB_HIGHLIGHT_COLOR = 0x40FFFFFF;
    private static final int PARTY_ICON_SIZE = 20;
    private static final int PARTY_ICON_BOTTOM_INSET = 93;

    private static final int STATS_LEFT = 11;
    private static final int STATS_TOP = 27;
    private static final int STAT_ICON_SIZE = 11;
    private static final int STAT_ROW_GAP = 1;
    private static final int STAT_TEXT_GAP = 3;
    private static final int HEALTH_COLOR = 0xFFFF1313;
    private static final int ARMOR_COLOR = 0xFFB8B9C4;
    private static final int HUNGER_COLOR = 0xFFB88458;
    private static final int TEXT_OUTLINE_COLOR = 0xFF000000;
    private static final ResourceLocation HEALTH_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_health_stat.png");
    private static final ResourceLocation ARMOR_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_armor_stat.png");
    private static final ResourceLocation HUNGER_ICON =
            VillagerRetaliation.id("textures/gui/villager_stats/villager_hunger_stat.png");

    private static int renderingInventoryPreviewVillagerId = -1;
    private boolean playerInventoryBeside;

    public VillagerInventoryScreen(VillagerInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = TEXTURE_WIDTH;
        this.imageHeight = PLAYER_TEXTURE_TOP + PLAYER_TEXTURE_HEIGHT;
    }

    public static boolean isRenderingInventoryPreview(AbstractVillager villager) {
        return villager.getId() == renderingInventoryPreviewVillagerId;
    }

    @Override
    protected void init() {
        this.playerInventoryBeside = Minecraft.getInstance().options.guiScale().get() == 4;
        this.imageWidth = this.playerInventoryBeside
                ? PLAYER_TEXTURE_BESIDE_LEFT + PLAYER_TEXTURE_WIDTH
                : TEXTURE_WIDTH;
        this.imageHeight = this.playerInventoryBeside
                ? TEXTURE_HEIGHT
                : PLAYER_TEXTURE_TOP + PLAYER_TEXTURE_HEIGHT;
        this.menu.setPlayerInventoryBeside(this.playerInventoryBeside);
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderTabTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                inventoryTexture(),
                this.leftPos,
                this.topPos,
                0,
                0,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INVENTORY_TEXTURE,
                this.leftPos + playerTextureLeft(),
                this.topPos + playerTextureTop(),
                0,
                0,
                PLAYER_TEXTURE_WIDTH,
                PLAYER_TEXTURE_HEIGHT,
                PLAYER_TEXTURE_WIDTH,
                PLAYER_TEXTURE_HEIGHT
        );
        renderVillager(graphics, mouseX, mouseY);
        renderPartyIcon(graphics);
        renderTabsAndNameplate(graphics, mouseX, mouseY);
        renderVillagerStats(graphics);
    }

    private int playerTextureLeft() {
        return this.playerInventoryBeside ? PLAYER_TEXTURE_BESIDE_LEFT : PLAYER_TEXTURE_LEFT;
    }

    private int playerTextureTop() {
        return this.playerInventoryBeside ? PLAYER_TEXTURE_BESIDE_TOP : PLAYER_TEXTURE_TOP;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && availableModes().size() > 1) {
            if (contains(leftTabBounds(), mouseX, mouseY)) {
                switchMode(-1);
                return true;
            }
            if (contains(rightTabBounds(), mouseX, mouseY)) {
                switchMode(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> tooltip = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (this.hoveredSlot != null && this.menu.isVillagerSlot(this.hoveredSlot)) {
            VillagerGiftReturnTracker.giftedBy(stack)
                    .map(name -> Component.literal("gifted by " + name).withStyle(ChatFormatting.GRAY))
                    .ifPresent(tooltip::add);
            VillagerTradePaymentTracker.tradedBy(stack)
                    .map(name -> Component.literal("traded by " + name).withStyle(ChatFormatting.GRAY))
                    .ifPresent(tooltip::add);
            if (VillagerConfiscatedStolenItemTracker.stolenItemBy(stack).isPresent()) {
                tooltip.add(Component.translatable("villagerretaliation.tooltip.stolen_item").withStyle(ChatFormatting.RED));
            }
            ProtectedVillagerProperty.read(stack)
                    .map(property -> ProtectedVillagerProperty.tooltip(property).withStyle(ChatFormatting.GRAY))
                    .ifPresent(tooltip::add);
        }
        return tooltip;
    }

    private ResourceLocation inventoryTexture() {
        return this.menu.isPartyInventory()
                ? VillagerRetaliationClientAssets.VILLAGER_PARTY_INVENTORY_TEXTURE
                : this.menu.isJobInventory()
                        ? VillagerRetaliationClientAssets.VILLAGER_JOB_INVENTORY_TEXTURE
                        : VillagerRetaliationClientAssets.VILLAGER_INVENTORY_TEXTURE;
    }

    private void renderPartyIcon(GuiGraphics graphics) {
        if (!this.menu.isPartyInventory()) {
            return;
        }
        int left = this.leftPos + (TEXTURE_WIDTH - PARTY_ICON_SIZE) / 2;
        int top = this.topPos + TEXTURE_HEIGHT - PARTY_ICON_BOTTOM_INSET - PARTY_ICON_SIZE;
        graphics.blit(
                VillagerRetaliationClientAssets.VILLAGER_INVENTORY_PARTY_ICON_TEXTURE,
                left,
                top,
                0,
                0,
                PARTY_ICON_SIZE,
                PARTY_ICON_SIZE,
                PARTY_ICON_SIZE,
                PARTY_ICON_SIZE
        );
    }

    private void renderTabsAndNameplate(GuiGraphics graphics, int mouseX, int mouseY) {
        int plateWidth = nameplateWidth();
        int plateLeft = this.leftPos + (TEXTURE_WIDTH - plateWidth) / 2;
        int plateTop = this.topPos + NAMEPLATE_TOP;
        if (availableModes().size() > 1) {
            renderTabButton(graphics, leftTabBounds(), VillagerRetaliationClientAssets.VILLAGER_INVENTORY_BUTTON_LEFT_TEXTURE,
                    contains(leftTabBounds(), mouseX, mouseY));
            renderTabButton(graphics, rightTabBounds(), VillagerRetaliationClientAssets.VILLAGER_INVENTORY_BUTTON_RIGHT_TEXTURE,
                    contains(rightTabBounds(), mouseX, mouseY));
        }
        blitNineSlicedTexture(
                graphics,
                VillagerRetaliationClientAssets.VILLAGER_INVENTORY_NAMEPLATE_TEXTURE,
                plateLeft,
                plateTop,
                plateWidth,
                NAMEPLATE_HEIGHT,
                NAMEPLATE_TEXTURE_WIDTH,
                NAMEPLATE_HEIGHT,
                NAMEPLATE_SLICE
        );
        Component title = modeTitle(this.menu.viewMode());
        int textLeft = plateLeft + (plateWidth - this.font.width(title)) / 2;
        int textTop = plateTop + (NAMEPLATE_HEIGHT - this.font.lineHeight) / 2 + NAMEPLATE_TEXT_Y_OFFSET;
        drawOutlinedString(graphics, title, textLeft, textTop, 0xFFFFFFFF);
    }

    private void renderTabButton(GuiGraphics graphics, Bounds bounds, ResourceLocation texture, boolean hovered) {
        graphics.blit(texture, bounds.left(), bounds.top(), 0, 0, bounds.width(), bounds.height(), bounds.width(), bounds.height());
        if (hovered) {
            graphics.fillGradient(
                    RenderType.guiOverlay(),
                    bounds.left() + TAB_HIGHLIGHT_INSET,
                    bounds.top() + TAB_HIGHLIGHT_INSET,
                    bounds.right() - TAB_HIGHLIGHT_INSET,
                    bounds.bottom() - TAB_HIGHLIGHT_INSET,
                    TAB_HIGHLIGHT_COLOR,
                    TAB_HIGHLIGHT_COLOR,
                    0
            );
        }
    }

    private void renderTabTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        List<VillagerInventoryMenu.ViewMode> modes = availableModes();
        if (modes.size() <= 1) {
            return;
        }
        VillagerInventoryMenu.ViewMode target = contains(leftTabBounds(), mouseX, mouseY)
                ? adjacentMode(modes, -1)
                : contains(rightTabBounds(), mouseX, mouseY) ? adjacentMode(modes, 1) : null;
        if (target != null) {
            graphics.renderTooltip(
                    this.font,
                    Component.translatable("gui.villagerretaliation.inventory.switch_to", modeTitle(target)),
                    mouseX,
                    mouseY
            );
        }
    }

    private void switchMode(int direction) {
        List<VillagerInventoryMenu.ViewMode> modes = availableModes();
        VillagerInventoryMenu.ViewMode nextMode = adjacentMode(modes, direction);
        if (nextMode == null || nextMode == this.menu.viewMode()) {
            return;
        }
        this.menu.switchViewMode(nextMode);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        PacketDistributor.sendToServer(
                new VillagerJobInventoryRequestPayload(this.menu.villagerEntityId(), nextMode.isWorkInventory()));
    }

    private List<VillagerInventoryMenu.ViewMode> availableModes() {
        List<VillagerInventoryMenu.ViewMode> modes = new ArrayList<>(2);
        if (this.menu.canSwitchToPersonalInventory()) {
            modes.add(VillagerInventoryMenu.ViewMode.PERSONAL);
        }
        if (this.menu.canSwitchToJobInventory()) {
            modes.add(this.menu.workInventoryViewMode());
        }
        if (!modes.contains(this.menu.viewMode())) {
            modes.add(this.menu.viewMode());
        }
        return modes;
    }

    private VillagerInventoryMenu.ViewMode adjacentMode(List<VillagerInventoryMenu.ViewMode> modes, int direction) {
        if (modes.size() <= 1) {
            return null;
        }
        int current = modes.indexOf(this.menu.viewMode());
        return modes.get(Math.floorMod(current + direction, modes.size()));
    }

    private Component modeTitle(VillagerInventoryMenu.ViewMode mode) {
        return Component.translatable(switch (mode) {
            case PERSONAL -> "gui.villagerretaliation.inventory.personal_inventory";
            case JOB -> "gui.villagerretaliation.inventory.job_inventory";
            case PARTY -> "gui.villagerretaliation.inventory.party_inventory";
        });
    }

    private int nameplateWidth() {
        return Math.max(NAMEPLATE_TEXTURE_WIDTH, this.font.width(modeTitle(this.menu.viewMode())) + NAMEPLATE_HORIZONTAL_PADDING * 2);
    }

    private Bounds leftTabBounds() {
        int plateLeft = this.leftPos + (TEXTURE_WIDTH - nameplateWidth()) / 2;
        int top = this.topPos + TAB_BUTTON_TOP + (NAMEPLATE_HEIGHT - TAB_BUTTON_HEIGHT) / 2;
        return new Bounds(plateLeft - TAB_BUTTON_WIDTH + TAB_BUTTON_OVERLAP, top, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT);
    }

    private Bounds rightTabBounds() {
        int plateRight = this.leftPos + (TEXTURE_WIDTH + nameplateWidth()) / 2;
        int top = this.topPos + TAB_BUTTON_TOP + (NAMEPLATE_HEIGHT - TAB_BUTTON_HEIGHT) / 2;
        return new Bounds(plateRight - TAB_BUTTON_OVERLAP, top, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT);
    }

    private static boolean contains(Bounds bounds, double mouseX, double mouseY) {
        return mouseX >= bounds.left() && mouseX < bounds.right() && mouseY >= bounds.top() && mouseY < bounds.bottom();
    }

    private void renderVillagerStats(GuiGraphics graphics) {
        Entity entity = villagerEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }
        int left = this.leftPos + STATS_LEFT;
        int top = this.topPos + STATS_TOP;
        renderStat(graphics, HEALTH_ICON, formatValue(livingEntity.getHealth()), HEALTH_COLOR, left, top);
        renderStat(graphics, ARMOR_ICON, Integer.toString(livingEntity.getArmorValue()), ARMOR_COLOR,
                left, top + STAT_ICON_SIZE + STAT_ROW_GAP);
        renderStat(graphics, HUNGER_ICON, Integer.toString(VillagerHungerClientCache.hunger(entity)), HUNGER_COLOR,
                left, top + (STAT_ICON_SIZE + STAT_ROW_GAP) * 2);
    }

    private void renderStat(GuiGraphics graphics, ResourceLocation icon, String value, int color, int left, int top) {
        graphics.blit(icon, left, top, 0, 0, STAT_ICON_SIZE, STAT_ICON_SIZE, STAT_ICON_SIZE, STAT_ICON_SIZE);
        drawStatString(graphics, Component.literal(value), left + STAT_ICON_SIZE + STAT_TEXT_GAP, top + 2, color);
    }

    private void drawStatString(GuiGraphics graphics, Component text, int x, int y, int color) {
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                if (offsetX != 0 || offsetY != 0) {
                    graphics.drawString(this.font, text, x + offsetX, y + offsetY, TEXT_OUTLINE_COLOR, false);
                }
            }
        }
        graphics.drawString(this.font, text, x, y, color, false);
    }

    private void drawOutlinedString(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(this.font, text, x - 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x + 1, y, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y - 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y + 1, TEXT_OUTLINE_COLOR, false);
        graphics.drawString(this.font, text, x, y, color, false);
    }

    private static String formatValue(float value) {
        return Math.abs(value - Math.round(value)) < 0.01F
                ? Integer.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private void blitNineSlicedTexture(
            GuiGraphics graphics,
            ResourceLocation texture,
            int left,
            int top,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int slice) {
        int centerSourceWidth = textureWidth - slice * 2;
        int centerSourceHeight = textureHeight - slice * 2;
        int centerWidth = Math.max(0, width - slice * 2);
        int centerHeight = Math.max(0, height - slice * 2);
        blitPart(graphics, texture, left, top, slice, slice, 0, 0, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, left + slice, top, centerWidth, slice, slice, 0, centerSourceWidth, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, left + width - slice, top, slice, slice, textureWidth - slice, 0, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, left, top + slice, slice, centerHeight, 0, slice, slice, centerSourceHeight, textureWidth, textureHeight);
        blitPart(graphics, texture, left + slice, top + slice, centerWidth, centerHeight, slice, slice, centerSourceWidth, centerSourceHeight, textureWidth, textureHeight);
        blitPart(graphics, texture, left + width - slice, top + slice, slice, centerHeight, textureWidth - slice, slice, slice, centerSourceHeight, textureWidth, textureHeight);
        blitPart(graphics, texture, left, top + height - slice, slice, slice, 0, textureHeight - slice, slice, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, left + slice, top + height - slice, centerWidth, slice, slice, textureHeight - slice, centerSourceWidth, slice, textureWidth, textureHeight);
        blitPart(graphics, texture, left + width - slice, top + height - slice, slice, slice, textureWidth - slice, textureHeight - slice, slice, slice, textureWidth, textureHeight);
    }

    private void blitPart(
            GuiGraphics graphics,
            ResourceLocation texture,
            int destLeft,
            int destTop,
            int destWidth,
            int destHeight,
            int sourceLeft,
            int sourceTop,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight) {
        if (destWidth > 0 && destHeight > 0 && sourceWidth > 0 && sourceHeight > 0) {
            graphics.blit(texture, destLeft, destTop, destWidth, destHeight, (float) sourceLeft, (float) sourceTop,
                    sourceWidth, sourceHeight, textureWidth, textureHeight);
        }
    }

    private Entity villagerEntity() {
        return Minecraft.getInstance().level == null
                ? null
                : Minecraft.getInstance().level.getEntity(this.menu.villagerEntityId());
    }

    private void renderVillager(GuiGraphics graphics, int mouseX, int mouseY) {
        Entity entity = villagerEntity();
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        int left = this.leftPos + ENTITY_LEFT;
        int top = this.topPos + ENTITY_TOP;
        int right = this.leftPos + ENTITY_RIGHT;
        int bottom = this.topPos + ENTITY_BOTTOM;
        float centerX = (left + right) / 2.0F;
        float centerY = (top + bottom) / 2.0F;
        float mouseYaw = (float) Math.atan((centerX - mouseX) / 40.0F);
        float mousePitch = (float) Math.atan((centerY - mouseY) / 40.0F);
        Quaternionf entityRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(mousePitch * 20.0F * ((float) Math.PI / 180.0F));
        entityRotation.mul(cameraRotation);

        float previousBodyRot = livingEntity.yBodyRot;
        float previousYRot = livingEntity.getYRot();
        float previousXRot = livingEntity.getXRot();
        float previousHeadRotO = livingEntity.yHeadRotO;
        float previousHeadRot = livingEntity.yHeadRot;
        livingEntity.yBodyRot = 180.0F + mouseYaw * 20.0F;
        livingEntity.setYRot(180.0F + mouseYaw * 40.0F);
        livingEntity.setXRot(-mousePitch * 20.0F);
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();

        float scale = livingEntity.getScale();
        renderingInventoryPreviewVillagerId = livingEntity.getId();
        try {
            InventoryScreen.renderEntityInInventory(
                    graphics,
                    centerX,
                    centerY,
                    ENTITY_SCALE / scale,
                    new Vector3f(0.0F, livingEntity.getBbHeight() / 2.0F + 0.0625F * scale, 0.0F),
                    entityRotation,
                    cameraRotation,
                    livingEntity
            );
        } finally {
            renderingInventoryPreviewVillagerId = -1;
            livingEntity.yBodyRot = previousBodyRot;
            livingEntity.setYRot(previousYRot);
            livingEntity.setXRot(previousXRot);
            livingEntity.yHeadRotO = previousHeadRotO;
            livingEntity.yHeadRot = previousHeadRot;
        }
    }

    private record Bounds(int left, int top, int width, int height) {
        int right() {
            return this.left + this.width;
        }

        int bottom() {
            return this.top + this.height;
        }
    }
}
