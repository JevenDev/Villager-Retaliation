package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload;
import com.jvn.villagerretaliation.party.PartyQuickCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PartyQuickCommandWheel {
    private static final int WHEEL_RADIUS = 86;
    private static final int ICON_RADIUS = 58;
    private static final int CENTER_RADIUS = 27;
    private static final int SLOT_TEXTURE_SIZE = 22;
    private static final int SLOT_ITEM_SIZE = 16;
    private static final int SLOT_FRAME_SIZE = (SLOT_TEXTURE_SIZE - SLOT_ITEM_SIZE) / 2;
    private static final float SLOT_BACKGROUND_ALPHA = 186.0F / 255.0F;
    private static final ResourceLocation SLOT_TEXTURE =
            VillagerRetaliation.id("textures/gui/quick_command/inventory_slot.png");
    private static final ResourceLocation GUI_LAYER =
            VillagerRetaliation.id("party_quick_command_wheel");
    private static final double INNER_DEADZONE = 20.0D;
    private static final double FULL_CIRCLE = Math.PI * 2.0D;
    private static final double BLOCK_TARGET_RANGE = 48.0D;
    private static final double ATTACK_TARGET_RANGE = 96.0D;
    private static final double ENTITY_SNAP_INFLATION = 0.0D;

    private static final List<WheelEntry> ENTRIES = List.of(
            entry(PartyQuickCommand.ATTACK, Items.IRON_SWORD),
            entry(PartyQuickCommand.MOVE_TO, Items.COMPASS),
            entry(PartyQuickCommand.STAY_HERE, Items.OAK_FENCE),
            entry(PartyQuickCommand.REGROUP, Items.LEAD),
            entry(PartyQuickCommand.STAND_GUARD, Items.SHIELD),
            entry(PartyQuickCommand.RANGE, Items.CROSSBOW),
            entry(PartyQuickCommand.MELEE, Items.IRON_AXE),
            entry(PartyQuickCommand.HEAL, Items.GOLDEN_APPLE),
            entry(PartyQuickCommand.PICK_UP_DROPS, Items.HOPPER),
            entry(PartyQuickCommand.LOOT_CONTAINERS, Items.CHEST)
    );

    private static boolean open;
    private static boolean wasMouseGrabbed;
    private static int highlightedIndex = -1;
    private static CapturedTarget capturedTarget = CapturedTarget.EMPTY;

    private PartyQuickCommandWheel() {
    }

    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!open && PartyQuickCommandKeyMappings.QUICK_COMMAND.isDown() && canUse(minecraft)) {
            open(minecraft);
        }
        if (open && !canUse(minecraft)) {
            close(minecraft, false);
        }
    }

    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!open) {
            return;
        }
        if (!PartyQuickCommandKeyMappings.QUICK_COMMAND.isDown()) {
            close(minecraft, true);
            return;
        }
        if (!canUse(minecraft)) {
            close(minecraft, false);
            return;
        }
        updateHighlighted(minecraft);
    }

    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (open) {
            event.setCanceled(true);
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (open) {
            event.setCanceled(true);
        }
    }

    public static void registerGuiLayer(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, GUI_LAYER, (graphics, partialTick) -> {
            if (open) {
                render(graphics);
            }
        });
    }

    private static boolean canUse(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        var roster = PartyRosterClient.roster();
        return player != null
                && minecraft.level != null
                && minecraft.screen == null
                && !player.isSpectator()
                && roster.active()
                && roster.recipientLeader()
                && !roster.villagers().isEmpty();
    }

    private static void open(Minecraft minecraft) {
        open = true;
        highlightedIndex = -1;
        capturedTarget = captureTarget(minecraft);
        wasMouseGrabbed = minecraft.mouseHandler.isMouseGrabbed();
        if (wasMouseGrabbed) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    private static void close(Minecraft minecraft, boolean applySelection) {
        if (applySelection && highlightedIndex >= 0 && highlightedIndex < ENTRIES.size()) {
            send(ENTRIES.get(highlightedIndex).command(), capturedTarget);
        }
        open = false;
        highlightedIndex = -1;
        capturedTarget = CapturedTarget.EMPTY;
        if (wasMouseGrabbed && minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
        }
        wasMouseGrabbed = false;
    }

    private static void send(PartyQuickCommand command, CapturedTarget target) {
        PacketDistributor.sendToServer(new PartyQuickCommandRequestPayload(
                command,
                target.entityId(),
                target.position()));
    }

    private static CapturedTarget captureTarget(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return CapturedTarget.EMPTY;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        Vec3 attackEnd = eye.add(view.scale(ATTACK_TARGET_RANGE));
        Vec3 blockEnd = eye.add(view.scale(BLOCK_TARGET_RANGE));
        HitResult blockOcclusion = minecraft.level.clip(new ClipContext(
                eye,
                attackEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        double visibleAttackDistanceSqr = blockOcclusion.getType() == HitResult.Type.BLOCK
                ? eye.distanceToSqr(blockOcclusion.getLocation())
                : ATTACK_TARGET_RANGE * ATTACK_TARGET_RANGE;
        int entityId = findSnappedEntity(player, eye, attackEnd, visibleAttackDistanceSqr)
                .map(Entity::getId)
                .orElse(PartyQuickCommandRequestPayload.NO_ENTITY);
        HitResult hit = minecraft.level.clip(new ClipContext(
                eye,
                blockEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player));
        BlockPos position = hit instanceof BlockHitResult blockHit
                && hit.getType() == HitResult.Type.BLOCK
                ? blockHit.getBlockPos().relative(blockHit.getDirection())
                : null;
        return new CapturedTarget(entityId, position);
    }

    private static Optional<LivingEntity> findSnappedEntity(
            LocalPlayer player,
            Vec3 eye,
            Vec3 end,
            double maximumDistanceSqr) {
        Vec3 travel = end.subtract(eye);
        AABB search = player.getBoundingBox().expandTowards(travel).inflate(ENTITY_SNAP_INFLATION + 1.0D);
        LivingEntity nearest = null;
        double nearestDistanceSqr = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player, search, candidate ->
                candidate instanceof LivingEntity living && living.isAlive() && candidate.isPickable())) {
            LivingEntity living = (LivingEntity) entity;
            AABB snappedBounds = living.getBoundingBox().inflate(ENTITY_SNAP_INFLATION);
            Optional<Vec3> intersection = snappedBounds.contains(eye)
                    ? Optional.of(eye)
                    : snappedBounds.clip(eye, end);
            if (intersection.isEmpty()) {
                continue;
            }
            double distanceSqr = eye.distanceToSqr(intersection.get());
            if (distanceSqr <= maximumDistanceSqr && distanceSqr < nearestDistanceSqr) {
                nearest = living;
                nearestDistanceSqr = distanceSqr;
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static void updateHighlighted(Minecraft minecraft) {
        double mouseX = minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth()
                / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight()
                / minecraft.getWindow().getScreenHeight();
        double centerX = minecraft.getWindow().getGuiScaledWidth() / 2.0D;
        double centerY = minecraft.getWindow().getGuiScaledHeight() / 2.0D;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        if (Math.hypot(dx, dy) < INNER_DEADZONE) {
            highlightedIndex = -1;
            return;
        }
        double angle = normalizeAngle(Math.atan2(dy, dx) + Math.PI / 2.0D);
        int index = (int) Math.floor(angle / (FULL_CIRCLE / ENTRIES.size()));
        highlightedIndex = Math.min(index, ENTRIES.size() - 1);
    }

    private static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int centerY = minecraft.getWindow().getGuiScaledHeight() / 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawCircle(centerX, centerY, WHEEL_RADIUS, 96, 0xB0101010);
        double slice = FULL_CIRCLE / ENTRIES.size();
        for (int index = 0; index < ENTRIES.size(); index++) {
            double start = -Math.PI / 2.0D + index * slice;
            double end = start + slice;
            drawWedge(centerX, centerY, WHEEL_RADIUS, start, end,
                    index == highlightedIndex ? 0x995DADE2 : 0x22000000);
        }
        drawCircle(centerX, centerY, CENTER_RADIUS, 48, 0xD0181818);
        RenderSystem.disableBlend();

        for (int index = 0; index < ENTRIES.size(); index++) {
            double angle = -Math.PI / 2.0D + (index + 0.5D) * slice;
            int iconX = centerX + (int) Math.round(Math.cos(angle) * ICON_RADIUS) - 8;
            int iconY = centerY + (int) Math.round(Math.sin(angle) * ICON_RADIUS) - 8;
            renderSlotBackground(graphics, iconX - SLOT_FRAME_SIZE, iconY - SLOT_FRAME_SIZE);
            ItemStack stack = ENTRIES.get(index).icon();
            graphics.renderItem(stack, iconX, iconY);
        }

        graphics.drawCenteredString(font,
                Component.translatable("villagerretaliation.party.quick_command.title"),
                centerX, centerY - WHEEL_RADIUS - 18, 0xFFFFFF);
        Component label = highlightedIndex >= 0
                ? selectionLabel(ENTRIES.get(highlightedIndex))
                : Component.translatable("villagerretaliation.party.quick_command.cancel");
        graphics.drawCenteredString(font, truncate(font, label.getString(), 118),
                centerX, centerY + WHEEL_RADIUS + font.lineHeight, 0xFFFFFF);
    }

    private static void renderSlotBackground(GuiGraphics graphics, int x, int y) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, SLOT_BACKGROUND_ALPHA);
        graphics.blit(SLOT_TEXTURE, x, y, 0.0F, 0.0F,
                SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(SLOT_TEXTURE, x, y, SLOT_TEXTURE_SIZE, SLOT_FRAME_SIZE,
                0.0F, 0.0F, SLOT_TEXTURE_SIZE, SLOT_FRAME_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
        graphics.blit(SLOT_TEXTURE, x, y + SLOT_TEXTURE_SIZE - SLOT_FRAME_SIZE,
                SLOT_TEXTURE_SIZE, SLOT_FRAME_SIZE, 0.0F, SLOT_TEXTURE_SIZE - SLOT_FRAME_SIZE,
                SLOT_TEXTURE_SIZE, SLOT_FRAME_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
        graphics.blit(SLOT_TEXTURE, x, y + SLOT_FRAME_SIZE, SLOT_FRAME_SIZE, SLOT_ITEM_SIZE,
                0.0F, SLOT_FRAME_SIZE, SLOT_FRAME_SIZE, SLOT_ITEM_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
        graphics.blit(SLOT_TEXTURE, x + SLOT_TEXTURE_SIZE - SLOT_FRAME_SIZE, y + SLOT_FRAME_SIZE,
                SLOT_FRAME_SIZE, SLOT_ITEM_SIZE, SLOT_TEXTURE_SIZE - SLOT_FRAME_SIZE, SLOT_FRAME_SIZE,
                SLOT_FRAME_SIZE, SLOT_ITEM_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
        RenderSystem.disableBlend();
    }

    private static void drawCircle(double centerX, double centerY, double radius, int segments, int color) {
        drawWedge(centerX, centerY, radius, 0.0D, FULL_CIRCLE, color, segments);
    }

    private static void drawWedge(
            double centerX,
            double centerY,
            double radius,
            double startAngle,
            double endAngle,
            int color) {
        int segments = Math.max(4, (int) Math.ceil((endAngle - startAngle) / FULL_CIRCLE * 96.0D));
        drawWedge(centerX, centerY, radius, startAngle, endAngle, color, segments);
    }

    private static void drawWedge(
            double centerX,
            double centerY,
            double radius,
            double startAngle,
            double endAngle,
            int color,
            int segments) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex((float) centerX, (float) centerY, 0.0F).setColor(color);
        for (int step = 0; step <= segments; step++) {
            double progress = (double) step / segments;
            double angle = startAngle + (endAngle - startAngle) * progress;
            buffer.addVertex(
                    (float) (centerX + Math.cos(angle) * radius),
                    (float) (centerY + Math.sin(angle) * radius),
                    0.0F).setColor(color);
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static WheelEntry entry(PartyQuickCommand command, net.minecraft.world.item.Item item) {
        return new WheelEntry(
                command,
                new ItemStack(item),
                Component.translatable("villagerretaliation.party.quick_command."
                        + command.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static Component selectionLabel(WheelEntry entry) {
        if (entry.command() == PartyQuickCommand.STAND_GUARD
                && PartyRosterClient.roster().standGuardActive()) {
            return Component.translatable("villagerretaliation.party.quick_command.lower_shields");
        }
        return entry.label();
    }

    private static String truncate(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(suffix))) + suffix;
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % FULL_CIRCLE;
        return normalized < 0.0D ? normalized + FULL_CIRCLE : normalized;
    }

    private record WheelEntry(PartyQuickCommand command, ItemStack icon, Component label) {
    }

    private record CapturedTarget(int entityId, BlockPos position) {
        private static final CapturedTarget EMPTY =
                new CapturedTarget(PartyQuickCommandRequestPayload.NO_ENTITY, null);
    }
}
