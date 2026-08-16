package com.jvn.villagerretaliation.client.party;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import com.jvn.villagerretaliation.network.PartyQuickCommandRequestPayload;
import com.jvn.villagerretaliation.network.PartyRosterSyncPayload;
import com.jvn.villagerretaliation.party.PartyQuickCommand;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.Util;
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
import org.lwjgl.glfw.GLFW;

public final class PartyQuickCommandWheel {
    private static final int WHEEL_RADIUS = 86;
    private static final int ICON_RADIUS = 58;
    private static final int CENTER_RADIUS = 27;
    private static final int SLOT_TEXTURE_SIZE = 22;
    private static final int SLOT_ITEM_SIZE = 16;
    private static final int SLOT_FRAME_SIZE = (SLOT_TEXTURE_SIZE - SLOT_ITEM_SIZE) / 2;
    private static final float SLOT_BACKGROUND_ALPHA = 186.0F / 255.0F;
    private static final float OPEN_FADE_DURATION_MILLIS = 160.0F;
    private static final float HIGHLIGHT_CROSSFADE_DURATION_MILLIS = 120.0F;
    private static final ResourceLocation SLOT_TEXTURE =
            VillagerRetaliation.id("textures/gui/quick_command/inventory_slot.png");
    private static final ResourceLocation GUI_LAYER =
            VillagerRetaliation.id("party_quick_command_wheel");
    private static final double INNER_DEADZONE = 20.0D;
    private static final double FULL_CIRCLE = Math.PI * 2.0D;
    private static final double BLOCK_TARGET_RANGE = 48.0D;
    private static final double ATTACK_TARGET_RANGE = 32.0D;
    private static final double ENTITY_SNAP_INFLATION = 0.85D;

    private static final List<WheelEntry> BASE_ENTRIES = List.of(
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
    private static int previousHighlightedIndex = -1;
    private static long openedAtMillis = -1L;
    private static long highlightChangedAtMillis = -1L;
    private static UUID commandedVillagerId;
    private static String commandedVillagerName;
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
        if (open && event.getAction() != GLFW.GLFW_RELEASE) {
            event.setCanceled(true);
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (open) {
            int delta = event.getScrollDeltaY() > 0.0D ? -1
                    : event.getScrollDeltaY() < 0.0D ? 1 : 0;
            if (delta != 0) {
                cycleCommandTarget(delta);
            }
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
                && PartyRosterClient.hasAdminPrivileges()
                && !roster.villagers().isEmpty();
    }

    private static void open(Minecraft minecraft) {
        open = true;
        highlightedIndex = -1;
        previousHighlightedIndex = -1;
        openedAtMillis = Util.getMillis();
        highlightChangedAtMillis = -1L;
        commandedVillagerId = null;
        commandedVillagerName = null;
        capturedTarget = captureTarget(minecraft);
        wasMouseGrabbed = minecraft.mouseHandler.isMouseGrabbed();
        if (wasMouseGrabbed) {
            minecraft.mouseHandler.releaseMouse();
        }
    }

    private static void close(Minecraft minecraft, boolean applySelection) {
        List<WheelEntry> entries = entries();
        if (applySelection && highlightedIndex >= 0 && highlightedIndex < entries.size()) {
            send(entries.get(highlightedIndex).command(), capturedTarget);
        }
        open = false;
        highlightedIndex = -1;
        previousHighlightedIndex = -1;
        openedAtMillis = -1L;
        highlightChangedAtMillis = -1L;
        capturedTarget = CapturedTarget.EMPTY;
        commandedVillagerId = null;
        commandedVillagerName = null;
        if (wasMouseGrabbed && minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
        }
        wasMouseGrabbed = false;
    }

    private static void send(PartyQuickCommand command, CapturedTarget target) {
        PacketDistributor.sendToServer(new PartyQuickCommandRequestPayload(
                command,
                target.entityId(),
                target.position(),
                commandedVillagerId));
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
            setHighlightedIndex(-1);
            return;
        }
        double angle = normalizeAngle(Math.atan2(dy, dx) + Math.PI / 2.0D);
        List<WheelEntry> entries = entries();
        int index = (int) Math.floor(angle / (FULL_CIRCLE / entries.size()));
        setHighlightedIndex(Math.min(index, entries.size() - 1));
    }

    private static void setHighlightedIndex(int nextIndex) {
        if (highlightedIndex == nextIndex) {
            return;
        }
        previousHighlightedIndex = highlightedIndex;
        highlightedIndex = nextIndex;
        highlightChangedAtMillis = Util.getMillis();
    }

    private static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int centerY = minecraft.getWindow().getGuiScaledHeight() / 2;
        List<WheelEntry> entries = entries();
        long now = Util.getMillis();
        float entranceAlpha = entranceAlpha(now);
        int textColor = VillagerClientUiUtil.withAlphaRound(0xFFFFFFFF, entranceAlpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawCircle(centerX, centerY, WHEEL_RADIUS, 96,
                VillagerClientUiUtil.withAlphaRound(0xB0101010, entranceAlpha));
        double slice = FULL_CIRCLE / entries.size();
        for (int index = 0; index < entries.size(); index++) {
            double start = -Math.PI / 2.0D + index * slice;
            double end = start + slice;
            drawWedge(centerX, centerY, WHEEL_RADIUS, start, end,
                    VillagerClientUiUtil.withAlphaRound(0x22000000, entranceAlpha));
            float highlightAlpha = highlightAlpha(index, now) * entranceAlpha;
            if (highlightAlpha > 0.01F) {
                drawWedge(centerX, centerY, WHEEL_RADIUS, start, end,
                        VillagerClientUiUtil.withAlphaRound(0x885DADE2, highlightAlpha));
            }
        }
        drawCircle(centerX, centerY, CENTER_RADIUS, 48,
                VillagerClientUiUtil.withAlphaRound(0xD0181818, entranceAlpha));
        RenderSystem.disableBlend();

        for (int index = 0; index < entries.size(); index++) {
            double angle = -Math.PI / 2.0D + (index + 0.5D) * slice;
            int iconX = centerX + (int) Math.round(Math.cos(angle) * ICON_RADIUS) - 8;
            int iconY = centerY + (int) Math.round(Math.sin(angle) * ICON_RADIUS) - 8;
            renderSlotBackground(graphics, iconX - SLOT_FRAME_SIZE, iconY - SLOT_FRAME_SIZE, entranceAlpha);
            ItemStack stack = entries.get(index).icon();
            graphics.setColor(1.0F, 1.0F, 1.0F, entranceAlpha);
            graphics.renderItem(stack, iconX, iconY);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        graphics.drawCenteredString(font, truncate(font, targetTitle().getString(), 180),
                centerX, centerY - WHEEL_RADIUS - 18, textColor);
        Component label = highlightedIndex >= 0
                ? selectionLabel(entries.get(highlightedIndex))
                : Component.translatable("villagerretaliation.party.quick_command.cancel");
        graphics.drawCenteredString(font, truncate(font, label.getString(), 118),
                centerX, centerY + WHEEL_RADIUS + font.lineHeight, textColor);
    }

    private static float entranceAlpha(long now) {
        if (openedAtMillis < 0L) {
            return 1.0F;
        }
        float progress = (now - openedAtMillis) / OPEN_FADE_DURATION_MILLIS;
        return VillagerClientUiUtil.easeOutCubic(progress);
    }

    private static float highlightAlpha(int index, long now) {
        if (highlightChangedAtMillis < 0L) {
            return index == highlightedIndex ? 1.0F : 0.0F;
        }
        float progress = VillagerClientUiUtil.smoothstep(
                (now - highlightChangedAtMillis) / HIGHLIGHT_CROSSFADE_DURATION_MILLIS);
        if (index == highlightedIndex) {
            return progress;
        }
        if (index == previousHighlightedIndex) {
            return 1.0F - progress;
        }
        return 0.0F;
    }

    private static void renderSlotBackground(GuiGraphics graphics, int x, int y, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(1.0F, 1.0F, 1.0F, SLOT_BACKGROUND_ALPHA * alpha);
        graphics.blit(SLOT_TEXTURE, x, y, 0.0F, 0.0F,
                SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE, SLOT_TEXTURE_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
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
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
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

    private static List<WheelEntry> entries() {
        List<WheelEntry> entries = new ArrayList<>(BASE_ENTRIES);
        List<PartyRosterSyncPayload.VillagerEntry> targets = selectedCommandTargets();
        if (!targets.isEmpty()) {
            boolean allUnequipped = targets.stream()
                    .allMatch(PartyRosterSyncPayload.VillagerEntry::weaponsUnequipped);
            entries.add(entry(allUnequipped
                    ? PartyQuickCommand.REEQUIP_WEAPONS
                    : PartyQuickCommand.UNEQUIP_WEAPONS, Items.IRON_SWORD));
        }
        var roster = PartyRosterClient.roster();
        if (commandedVillagerId == null
                && roster.mountFeatureAvailable()
                && roster.villagers().stream().anyMatch(villager ->
                        villager.quickCommandsEnabled() && villager.assignedMount())) {
            entries.add(entry(roster.mountMode()
                    ? PartyQuickCommand.DISMOUNT_MOUNT
                    : PartyQuickCommand.RIDE_MOUNT, Items.SADDLE));
        }
        return entries;
    }

    private static List<PartyRosterSyncPayload.VillagerEntry> selectedCommandTargets() {
        return commandableVillagers().stream()
                .filter(villager -> commandedVillagerId == null
                        || villager.villagerId().equals(commandedVillagerId))
                .toList();
    }

    private static void cycleCommandTarget(int delta) {
        List<PartyRosterSyncPayload.VillagerEntry> villagers = commandableVillagers();
        int targetCount = villagers.size() + 1;
        int current = 0;
        if (commandedVillagerId != null) {
            for (int index = 0; index < villagers.size(); index++) {
                if (villagers.get(index).villagerId().equals(commandedVillagerId)) {
                    current = index + 1;
                    break;
                }
            }
        }
        int next = Math.floorMod(current + delta, targetCount);
        if (next == 0) {
            commandedVillagerId = null;
            commandedVillagerName = null;
        } else {
            PartyRosterSyncPayload.VillagerEntry villager = villagers.get(next - 1);
            commandedVillagerId = villager.villagerId();
            commandedVillagerName = villager.name();
        }
        highlightedIndex = -1;
        previousHighlightedIndex = -1;
        highlightChangedAtMillis = -1L;
    }

    private static Component targetTitle() {
        if (commandedVillagerId == null) {
            return Component.translatable("villagerretaliation.party.quick_command.target_all");
        }
        return Component.translatable(
                "villagerretaliation.party.quick_command.target_villager",
                commandedVillagerName);
    }

    private static List<PartyRosterSyncPayload.VillagerEntry> commandableVillagers() {
        return PartyRosterClient.roster().villagers().stream()
                .filter(PartyRosterSyncPayload.VillagerEntry::quickCommandsEnabled)
                .toList();
    }

    private static Component selectionLabel(WheelEntry entry) {
        if (entry.command() == PartyQuickCommand.STAND_GUARD) {
            if (commandedVillagerId != null) {
                return Component.translatable("villagerretaliation.party.quick_command.toggle_guard");
            }
            if (PartyRosterClient.roster().standGuardActive()) {
                return Component.translatable("villagerretaliation.party.quick_command.lower_shields");
            }
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
