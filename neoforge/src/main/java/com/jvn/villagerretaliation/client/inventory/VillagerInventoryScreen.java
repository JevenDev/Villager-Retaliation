package com.jvn.villagerretaliation.client.inventory;

import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.jvn.villagerretaliation.inventory.VillagerConfiscatedStolenItemTracker;
import com.jvn.villagerretaliation.inventory.VillagerGiftReturnTracker;
import com.jvn.villagerretaliation.inventory.VillagerInventoryMenu;
import com.jvn.villagerretaliation.inventory.VillagerTradePaymentTracker;
import com.jvn.villagerretaliation.inventory.ProtectedVillagerProperty;
import com.jvn.villagerretaliation.network.VillagerJobInventoryRequestPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class VillagerInventoryScreen extends AbstractContainerScreen<VillagerInventoryMenu> {
    private static final int VILLAGER_TEXTURE_WIDTH = 176;
    private static final int VILLAGER_TEXTURE_HEIGHT = 144;
    private static final int PLAYER_TEXTURE_WIDTH = 176;
    private static final int PLAYER_TEXTURE_HEIGHT = 90;
    private static final int PLAYER_TEXTURE_Y = 148;
    private static final int ENTITY_LEFT = 63;
    private static final int ENTITY_TOP = 8;
    private static final int ENTITY_RIGHT = 112;
    private static final int ENTITY_BOTTOM = 76;
    private static final int ENTITY_SCALE = 31;
    private static int renderingInventoryPreviewVillagerId = -1;

    public VillagerInventoryScreen(VillagerInventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = VILLAGER_TEXTURE_WIDTH;
        this.imageHeight = PLAYER_TEXTURE_Y + PLAYER_TEXTURE_HEIGHT;
    }

    public static boolean isRenderingInventoryPreview(AbstractVillager villager) {
        return villager.getId() == renderingInventoryPreviewVillagerId;
    }

    @Override
    protected void init() {
        super.init();
        Component tabLabel = Component.translatable(this.menu.isJobInventory()
                ? "gui.villagerretaliation.inventory.personal_tab"
                : "gui.villagerretaliation.inventory.job_tab");
        addRenderableWidget(Button.builder(tabLabel, button -> {
                    VillagerInventoryMenu.ViewMode nextMode = this.menu.isJobInventory()
                            ? VillagerInventoryMenu.ViewMode.PERSONAL
                            : VillagerInventoryMenu.ViewMode.JOB;
                    this.menu.switchViewMode(nextMode);
                    refreshForModeSwitch();
                    PacketDistributor.sendToServer(
                            new VillagerJobInventoryRequestPayload(this.menu.villagerEntityId(), nextMode == VillagerInventoryMenu.ViewMode.JOB));
                })
                .bounds(this.leftPos + 116, this.topPos + 8, 52, 18)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                VillagerRetaliationClientAssets.VILLAGER_INVENTORY_TEXTURE,
                this.leftPos,
                this.topPos,
                0,
                0,
                VILLAGER_TEXTURE_WIDTH,
                VILLAGER_TEXTURE_HEIGHT,
                VILLAGER_TEXTURE_WIDTH,
                VILLAGER_TEXTURE_HEIGHT
        );
        graphics.blit(
                VillagerRetaliationClientAssets.GIFT_INVENTORY_TEXTURE,
                this.leftPos,
                this.topPos + PLAYER_TEXTURE_Y,
                0,
                0,
                PLAYER_TEXTURE_WIDTH,
                PLAYER_TEXTURE_HEIGHT,
                PLAYER_TEXTURE_WIDTH,
                PLAYER_TEXTURE_HEIGHT
        );
        renderVillager(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    public void refreshForModeSwitch() {
        clearWidgets();
        init();
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

    private void renderVillager(GuiGraphics graphics, int mouseX, int mouseY) {
        Entity entity = Minecraft.getInstance().level == null
                ? null
                : Minecraft.getInstance().level.getEntity(this.menu.villagerEntityId());
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

}
