package com.jvn.villagerretaliation.client.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.network.SettlementManagementPayload;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementBuildingService;
import com.jvn.villagerretaliation.settlement.SettlementSavedData;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

final class SettlementWorldViewClientChecks {
    private final SettlementViewScreen view;
    private final VillageAllegianceId town;
    private final BlockPos center;
    private final Consumer<String> capture;
    private CompletableFuture<SettlementArea> preparation;
    private CompletableFuture<Boolean> deletion;
    private CompletableFuture<?> homecoming;
    private SettlementArea area;
    private SettlementPeekClientChecks.LiveFade peekFade;
    private int phase;
    private int ticks;

    SettlementWorldViewClientChecks(SettlementViewScreen view, VillageAllegianceId town, BlockPos center, Consumer<String> capture) {
        this.view = view;
        this.town = town;
        this.center = center;
        this.capture = capture;
    }

    boolean tick(Minecraft minecraft) {
        if (this.phase == 0) {
            if (this.peekFade == null) {
                SettlementPeekClientChecks.verify(minecraft, this.view, this.center);
                this.peekFade = new SettlementPeekClientChecks.LiveFade(this.center.offset(-5, 4, 1));
                return false;
            }
            if (!this.peekFade.tick()) return false;
            UUID player = minecraft.player.getUUID();
            this.preparation = minecraft.getSingleplayerServer().submit(() -> {
                var owner = minecraft.getSingleplayerServer().getPlayerList().getPlayer(player);
                var area = new SettlementArea(UUID.randomUUID(), SettlementAreaType.HOME, "Removal test",
                        this.center.offset(10, -2, 10), this.center.offset(12, 2, 12), 5, true);
                require(SettlementBuildingService.register(owner, this.town, area), "the test area registers without replacing existing work");
                return area;
            });
            this.phase = 1;
        } else if (this.phase == 1 && this.preparation.isDone()) {
            this.area = this.preparation.join();
            this.view.resumeManagement(this.view.parent());
            this.view.inspect(new SettlementManagementPayload.Area(this.area.id(), this.area.name(), this.area.type(),
                    this.area.min(), this.area.max(), 5, true, 0, 0, 0));
            this.phase = 2;
        } else if (this.phase == 2 && this.view.inspectorSelection() instanceof SettlementManagementPayload.Area selected
                && selected.id().equals(this.area.id())) {
            press(SettlementViewKeyMappings.MOVE);
            require(this.view.selectionGizmo() != null && this.view.selectionGizmo().mode() == SettlementSelectionGizmo.Mode.TRANSFORM,
                    "the move shortcut starts editing the selected area");
            press(SettlementViewKeyMappings.RESIZE);
            require(this.view.selectionGizmo().mode() == SettlementSelectionGizmo.Mode.RESIZE, "the resize shortcut changes tools");
            RenderSystem.enableDepthTest();
            SettlementSelectionGizmo.LINES.setupRenderState();
            require(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "grabbers explicitly disable inherited terrain depth testing");
            SettlementSelectionGizmo.LINES.clearRenderState();
            require(GL11.glIsEnabled(GL11.GL_DEPTH_TEST), "grabbers restore depth testing after rendering");
            this.view.focusSupply(this.area.id(), this.area.min());
            this.ticks = 0;
            this.phase = 3;
        } else if (this.phase == 3 && ++this.ticks >= 20) {
            this.capture.accept("resize-through-terrain");
            press(SettlementViewKeyMappings.SELECT);
            int x = this.area.min().getX() + 1, z = this.area.min().getZ() + 1;
            int y = minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            SettlementViewClientSmoke.clickWorld(this.view, new BlockPos(x, y, z).getCenter(), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            require(this.view.contextPopup() != null && this.view.contextPopup().target().kind() == SettlementViewPopup.Kind.AREA,
                    "right-clicking an area opens its context menu");
            require(this.view.contextPopup().buttons().stream().anyMatch(button ->
                    button.getMessage().getString().equals(SettlementManagementScreen.text("remove_area").getString())),
                    "the area context menu offers removal");
            this.ticks = 0;
            this.phase = 4;
        } else if (this.phase == 4 && ++this.ticks >= 5) {
            this.capture.accept("vanilla-area-context");
            this.view.contextPopup().buttons().stream().filter(button ->
                    button.getMessage().getString().equals(SettlementManagementScreen.text("remove_area").getString()))
                    .findFirst().orElseThrow().onPress();
            require(minecraft.screen instanceof SettlementConfirmScreen, "context removal requires confirmation");
            this.ticks = 0;
            this.phase = 5;
        } else if (this.phase == 5 && ++this.ticks >= 5) {
            this.capture.accept("area-removal-confirmation");
            minecraft.screen.onClose();
            require(minecraft.screen == this.view && !SettlementViewClient.panelsOpen(this.view),
                    "cancelling confirmation returns to interactive world controls");
            this.view.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0);
            require(minecraft.screen instanceof SettlementConfirmScreen, "Delete uses the same confirmation");
            button(minecraft.screen, "gui.yes").onPress();
            this.phase = 6;
        } else if (this.phase == 6 && !this.view.parent().submitting()) {
            if (this.deletion == null) this.deletion = minecraft.getSingleplayerServer().submit(() ->
                    !SettlementSavedData.get(minecraft.getSingleplayerServer().overworld()).get(this.town).orElseThrow()
                            .areas().containsKey(this.area.id()));
            if (!this.deletion.isDone()) return false;
            require(this.deletion.join(), "confirming removal deletes the selected saved area");
            press(SettlementViewKeyMappings.BOUNDS);
            require(SettlementBoundsClient.enabled(this.town.value()), "the bounds shortcut enables the shared overlay");
            this.ticks = 0;
            this.phase = 7;
        } else if (this.phase == 7 && ++this.ticks >= 20 && boundsReady()) {
            this.capture.accept("world-owned-bounds");
            SettlementViewClient.openPanels(this.view);
            minecraft.setScreen(this.view.parent());
            button(minecraft.screen, "villagerretaliation.gui.settlement.hide_bounds").onPress();
            require(!SettlementBoundsClient.enabled(this.town.value()), "the table toggle controls the same overlay");
            button(minecraft.screen, "villagerretaliation.gui.settlement.show_bounds").onPress();
            minecraft.screen.onClose();
            require(minecraft.screen == this.view, "closing the table resumes the view");
            this.view.onClose();
            require(minecraft.screen == null && minecraft.getCameraEntity() == minecraft.player, "normal play restores the player camera");
            UUID player = minecraft.player.getUUID();
            this.homecoming = minecraft.getSingleplayerServer().submit(() -> {
                minecraft.getSingleplayerServer().getPlayerList().getPlayer(player).teleportTo(
                        this.center.getX() + 0.5, this.center.getY(), this.center.getZ() + 0.5);
            });
            minecraft.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            this.ticks = 0;
            this.phase = 8;
        } else if (this.phase == 8 && this.homecoming.isDone()) {
            this.homecoming.join();
            minecraft.player.setXRot(45);
            minecraft.player.setYRot(45);
            if (++this.ticks < 40 || !boundsReady() || !boundsHavePlots()) return false;
            this.capture.accept("normal-play-owned-bounds");
            require(SettlementBoundsClient.enabled(this.town.value()), "owned borders remain enabled outside the world view");
            SettlementBoundsClient.toggle(this.town.value());
            return true;
        }
        return false;
    }

    private void press(net.minecraft.client.KeyMapping mapping) {
        this.view.keyPressed(mapping.getKey().getValue(), 0, 0);
    }

    private static boolean boundsReady() {
        try {
            var field = SettlementBoundsClient.class.getDeclaredField("snapshot");
            field.setAccessible(true);
            return field.get(null) != null;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean boundsHavePlots() {
        try {
            var field = SettlementBoundsClient.class.getDeclaredField("snapshot");
            field.setAccessible(true);
            var snapshot = (com.jvn.villagerretaliation.network.SettlementPlotSnapshot) field.get(null);
            return snapshot != null && !snapshot.plots().isEmpty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Button button(Screen screen, String key) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals(key)).findFirst().orElseThrow();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
