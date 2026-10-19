package com.jvn.villagerretaliation.client.campaign;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class CampaignAnimationClientChecks {
    private static Preview preview;
    private static float idleSpeed;

    private CampaignAnimationClientChecks() { }

    static Screen start(CampaignSelectScreen screen) {
        background();
        cards();
        preview = new Preview(screen);
        return preview;
    }

    static void hover() throws Exception {
        idleSpeed = speed();
        require(idleSpeed > 0.9F, "Background drift returns to its idle speed away from cards");
        preview.hovering = true;
    }

    static void verifyHover() throws Exception {
        require(speed() < idleSpeed * 0.3F, "Hovering a rendered card smoothly slows the panorama");
        Button card = card(preview.screen);
        var top = CampaignSelectScreen.class.getDeclaredField("contentTop");
        top.setAccessible(true);
        require(card.getY() < top.getInt(preview.screen) && card.getY() >= top.getInt(preview.screen) - 2,
                "Hovered cards lift subtly while updating their actual hit area");
        Button caret = preview.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Expand Campaign 1")).findFirst().orElseThrow();
        require(caret.isMouseOver(caret.getX() + 8, caret.getY() + 14), "Caret hit areas follow the tilted card");
        CampaignMotion.Card motion = motion(card);
        require(motion.tilt() > 0.2F && motion.tilt() <= 1.5F, "Collapsed cards tilt gently toward the cursor");
        require(card.getTooltip() != null, "The cover retains its campaign tooltip while tilted");
        require(preview.screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(Component.translatable("selectWorld.create").getString()))
                .noneMatch(button -> button.active), "Hover animations do not select campaigns");
    }

    static CampaignSelectScreen finish() { return preview.screen; }

    static void scrolling(CampaignSelectScreen screen, boolean settled) throws Exception {
        var field = CampaignSelectScreen.class.getDeclaredField("visibleDescriptionScroll");
        field.setAccessible(true);
        double visible = field.getDouble(screen);
        double target = CampaignCardClientChecks.scroll(screen);
        if (settled) require(visible > 0 && Math.abs(visible - target) < 1, "Description scrolling eases to the requested position");
        else require(target > visible, "Scroll input updates the target before moving the rendered content");
    }

    private static CampaignMotion.Card motion(Button card) throws ReflectiveOperationException {
        var field = card.getClass().getDeclaredField("motion");
        field.setAccessible(true);
        return (CampaignMotion.Card) field.get(card);
    }

    static void tooltips(CampaignSelectScreen screen, GuiGraphics graphics, float partialTick) {
        Button card = card(screen);
        card.render(graphics, card.getX() + 200, card.getY() + 50, partialTick);
        require(card.getTooltip() == null, "Expanded descriptions have no card tooltip");
        card.render(graphics, card.getX() + 50, card.getY() + 50, partialTick);
        require(card.getTooltip() != null, "The regular cover section retains its tooltip when expanded");
    }

    private static float speed() throws Exception {
        var background = CampaignSelectScreen.class.getDeclaredField("backgroundMotion");
        background.setAccessible(true);
        var speed = CampaignMotion.class.getDeclaredField("speed");
        speed.setAccessible(true);
        return speed.getFloat(background.get(preview.screen));
    }

    private static Button card(CampaignSelectScreen screen) {
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals("Campaign 1")).findFirst().orElseThrow();
    }

    private static void background() {
        CampaignMotion seeded = new CampaignMotion();
        seeded.start(137.25F);
        seeded.update(1000, 200, 150, 400, 300, false, 1);
        require(seeded.yaw() == -137.25F && seeded.pitch() == 10, "The first campaign frame keeps the previous menu panorama angle");
        CampaignMotion idle = new CampaignMotion();
        for (long now = 1000; now <= 2000; now += 20) idle.update(now, 200, 150, 400, 300, false, 1);
        CampaignMotion fasterFrames = new CampaignMotion();
        for (long now = 1000; now <= 2000; now += 10) fasterFrames.update(now, 200, 150, 400, 300, false, 1);
        require(Math.abs(idle.yaw() - fasterFrames.yaw()) < 0.0001F, "Background drift is independent of frame rate");
        float before = idle.yaw();
        idle.update(2020, 200, 150, 400, 300, true, 1);
        float firstStep = Math.abs(idle.yaw() - before);
        require(firstStep > 0.0035F && firstStep < 0.007F, "Card hover eases the drift speed rather than stopping abruptly");
        for (long now = 2040; now <= 3000; now += 20) idle.update(now, 200, 150, 400, 300, true, 1);
        before = idle.yaw();
        idle.update(3020, 200, 150, 400, 300, true, 1);
        require(Math.abs(idle.yaw() - before) < firstStep * 0.3F, "Sustained hover reduces background motion substantially");
        for (long now = 3040; now <= 4020; now += 20) idle.update(now, 200, 150, 400, 300, false, 1);
        before = idle.yaw();
        idle.update(4040, 200, 150, 400, 300, false, 1);
        require(Math.abs(idle.yaw() - before) > 0.006F, "Background motion eases back after leaving a card");
        CampaignMotion pointer = new CampaignMotion();
        CampaignMotion center = new CampaignMotion();
        for (long now = 1000; now <= 2000; now += 20) {
            pointer.update(now, 400, 300, 400, 300, false, 1);
            center.update(now, 200, 150, 400, 300, false, 1);
        }
        require(pointer.pitch() > 10 && pointer.pitch() <= 10.35F && pointer.yaw() - center.yaw() > 0
                && pointer.yaw() - center.yaw() <= 1.1F, "Cursor parallax stays within subtle camera bounds");
        CampaignMotion disabled = new CampaignMotion();
        for (long now = 1000; now <= 2000; now += 20) disabled.update(now, 400, 300, 400, 300, false, 0);
        require(disabled.yaw() == 0 && disabled.pitch() == 10, "A zero panorama speed disables all background motion");
        before = center.yaw();
        center.update(20_000, 200, 150, 400, 300, false, 1);
        require(Math.abs(center.yaw() - before) < 0.02F, "Returning after a stalled frame does not lurch the camera");
    }

    private static void cards() {
        var card = new CampaignMotion.Card(10, 20, 1, false, 1000);
        card.target(100, 40, 1, true, 1, 1000, 0);
        card.update(1060, 0.02F, false, false);
        require(card.width() > 128 && card.descriptionOpacity() == 0, "Descriptions wait until the panel has started opening");
        card.update(1120, 0.02F, false, false);
        require(card.x() > 10 && card.x() < 100 && card.width() > 128 && card.width() < 374
                && card.opacity() > 0 && card.opacity() < 1, "Expansion interpolates position, native reveal width, and opacity");
        require(card.descriptionOpacity() > 0 && card.descriptionOpacity() < 1, "Descriptions fade in as the opening panel settles");
        float x = card.x();
        int width = card.width();
        card.target(-10, 40, 0.75F, false, 1, 1120, 0);
        require(card.x() == x && card.width() == width, "Reversing an in-flight transition preserves its current position and reveal");
        card.update(1360, 0.02F, false, false);
        require(card.x() == -10 && card.y() == 40 && card.width() == 128 && card.scale() == 0.75F
                && card.opacity() == 1 && card.descriptionOpacity() == 0, "Reversed transitions settle exactly at the collapsed layout");
        require(card.contains(0, 50) && !card.contains(100, 50), "Hit areas use the current animated position, width, and scale");
        card.update(1380, 0.02F, true, true);
        require(card.y() < 40 && card.y() > 38.5F && card.selection() > 0 && card.selection() < 1, "Hover lift and selection feedback ease in");
        card.target(-10, 44, 0.75F, false, 0, 1380, 0);
        card.update(1520, 0.02F, false, false);
        require(card.departing() && !card.visible() && card.opacity() == 0, "Departing cards finish their fade and can be retired");
        var tilted = new CampaignMotion.Card(10, 20, 0.75F, false, 1000);
        tilted.target(10, 20, 0.75F, false, 1, 1000, 0);
        tilted.update(1240, 0.02F, false, false);
        for (int frame = 0; frame < 30; frame++) tilted.tilt(90, 80, 0.02F);
        require(tilted.tilt() > 0.5F && tilted.tilt() <= 1.5F, "Collapsed tilt is eased and bounded");
        double angle = Math.toRadians(tilted.tilt());
        double authorX = tilted.x() + (64 - 56 * Math.cos(angle) - 82 * Math.sin(angle)) * tilted.scale();
        double authorY = tilted.y() + (96 - 56 * Math.sin(angle) + 82 * Math.cos(angle)) * tilted.scale();
        require(Math.abs(tilted.localX(authorX, authorY) - 8) < 0.00001
                && Math.abs(tilted.localY(authorX, authorY) - 178) < 0.00001 && tilted.contains(authorX, authorY),
                "Tilted author hover and card hit tests undo the visual rotation and GUI scale");
        float tilt = tilted.tilt();
        tilted.target(10, 20, 0.75F, true, 1, 1240, 0);
        tilted.update(1270, 0.02F, false, false);
        require(tilted.tilt() > 0 && tilted.tilt() < tilt && tilted.width() == 128, "The cover straightens smoothly before revealing the description");
        tilted.update(1300, 0.02F, false, false);
        require(tilted.tilt() == 0 && tilted.width() == 128, "The description starts opening only once the frame is flat");
        tilted.update(1540, 0.02F, false, false);
        tilted.tilt(90, 80, 0.02F);
        require(tilted.tilt() == 0 && tilted.width() == 374, "Expanded cards stay flat and retain native dimensions");
        var delayed = new CampaignMotion.Card(10, 26, 1, false, 1000);
        delayed.target(10, 20, 1, false, 1, 1000, 25);
        delayed.update(1010, 0.01F, false, false);
        require(delayed.opacity() == 0 && delayed.y() == 26, "Staggered entrance waits before starting");
        delayed.update(1265, 0.02F, false, false);
        require(delayed.opacity() == 1 && delayed.y() == 20, "Entrance animations settle without changing native card dimensions");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static final class Preview extends Screen {
        private final CampaignSelectScreen screen;
        private boolean hovering;

        private Preview(CampaignSelectScreen screen) {
            super(Component.literal("Campaign animation verification"));
            this.screen = screen;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            Button card = card(screen);
            screen.render(graphics, hovering ? card.getX() + 96 : -1, hovering ? card.getY() + 65 : -1, partialTick);
        }
    }
}
