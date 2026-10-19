package com.jvn.villagerretaliation.client.campaign;

import com.jvn.villagerretaliation.client.ui.VillagerClientUiUtil;
import net.minecraft.util.Mth;

final class CampaignMotion {
    private long previousFrame;
    private float delta;
    private float speed = 1;
    private float yaw;
    private float pointerX;
    private float pointerY;

    void update(long now, int mouseX, int mouseY, int width, int height, boolean hovering, double panoramaSpeed) {
        delta = previousFrame == 0 ? 0 : Math.clamp((now - previousFrame) / 1000F, 0, 0.05F);
        previousFrame = now;
        speed = approach(speed, hovering ? 0.15F : 1, delta, 4);
        if (panoramaSpeed == 0) {
            pointerX = 0;
            pointerY = 0;
            return;
        }
        yaw = (yaw + delta * 0.35F * speed * (float) panoramaSpeed) % 360;
        float horizontal = Mth.clamp(mouseX / (float) Math.max(1, width) * 2 - 1, -1, 1);
        float vertical = Mth.clamp(mouseY / (float) Math.max(1, height) * 2 - 1, -1, 1);
        pointerX = approach(pointerX, horizontal * speed, delta, 5);
        pointerY = approach(pointerY, vertical * speed, delta, 5);
    }

    void start(float spin) { yaw = spin; }

    float delta() { return delta; }
    float pitch() { return 10 + pointerY * 0.35F; }
    float yaw() { return -yaw + pointerX * 1.1F; }

    private static float approach(float value, float target, float delta, float response) {
        return Mth.lerp(1 - (float) Math.exp(-delta * response), value, target);
    }

    static final class Card {
        private float x;
        private float y;
        private float scale;
        private float expansion;
        private float opacity;
        private float descriptionOpacity;
        private float hover;
        private float selection;
        private float tilt;
        private float fromTilt;
        private int expansionDelay;
        private float fromX;
        private float fromY;
        private float fromScale;
        private float fromExpansion;
        private float fromOpacity;
        private float fromDescriptionOpacity;
        private float targetX;
        private float targetY;
        private float targetScale;
        private float targetExpansion;
        private float targetOpacity;
        private long started;
        private int delay;

        Card(float x, float y, float scale, boolean expanded, long now) {
            this.x = fromX = targetX = x;
            this.y = fromY = targetY = y;
            this.scale = fromScale = targetScale = scale;
            expansion = fromExpansion = targetExpansion = expanded ? 1 : 0;
            descriptionOpacity = fromDescriptionOpacity = expanded ? 1 : 0;
            started = now;
        }

        void target(float x, float y, float scale, boolean expanded, float opacity, long now, int delay) {
            sample(now);
            fromX = this.x;
            fromY = this.y;
            fromScale = this.scale;
            fromExpansion = expansion;
            fromOpacity = this.opacity;
            fromDescriptionOpacity = descriptionOpacity;
            targetX = x;
            targetY = y;
            targetScale = scale;
            fromTilt = tilt;
            expansionDelay = expanded && Math.abs(tilt) > 0.01F ? 60 : 0;
            targetExpansion = expanded ? 1 : 0;
            targetOpacity = opacity;
            started = now;
            this.delay = delay;
        }

        void update(long now, float delta, boolean hovering, boolean selected) {
            sample(now);
            hover = approach(hover, hovering ? 1 : 0, delta, 12);
            selection = approach(selection, selected ? 1 : 0, delta, 12);
        }

        void tilt(double mouseX, double mouseY, float delta) {
            if (expansion != 0 || targetExpansion != 0) return;
            float horizontal = (float) Math.clamp((localX(mouseX, mouseY) - 64) / 64, -1, 1);
            tilt = approach(tilt, contains(mouseX, mouseY) ? horizontal * 1.5F : 0, delta, 10);
        }

        private void sample(long now) {
            if (targetExpansion == 1) tilt = fromTilt * (1 - VillagerClientUiUtil.easeOutCubic((now - started - delay) / 60F));
            float progress = VillagerClientUiUtil.easeOutCubic((now - started - delay - expansionDelay) / (targetOpacity == 0 ? 140F : 240F));
            x = Mth.lerp(progress, fromX, targetX);
            y = Mth.lerp(progress, fromY, targetY);
            scale = Mth.lerp(progress, fromScale, targetScale);
            expansion = Mth.lerp(progress, fromExpansion, targetExpansion);
            opacity = Mth.lerp(progress, fromOpacity, targetOpacity);
            float descriptionProgress = VillagerClientUiUtil.easeOutCubic((now - started - delay - expansionDelay - (targetExpansion == 1 ? 100 : 0))
                    / (targetExpansion == 1 ? 140F : 90F));
            descriptionOpacity = Mth.lerp(descriptionProgress, fromDescriptionOpacity, targetExpansion);
        }

        float x() { return x; }
        float y() { return y - hover * 2 * scale; }
        float scale() { return scale; }
        int width() { return Math.round(128 + 246 * expansion); }
        float opacity() { return opacity; }
        float descriptionOpacity() { return descriptionOpacity; }
        float hover() { return hover; }
        float selection() { return selection; }
        float tilt() { return tilt; }
        double localX(double mouseX, double mouseY) {
            double angle = Math.toRadians(tilt);
            double dx = (mouseX - x()) / scale - 64;
            double dy = (mouseY - y()) / scale - 96;
            return 64 + dx * Math.cos(angle) + dy * Math.sin(angle);
        }
        double localY(double mouseX, double mouseY) {
            double angle = Math.toRadians(tilt);
            double dx = (mouseX - x()) / scale - 64;
            double dy = (mouseY - y()) / scale - 96;
            return 96 - dx * Math.sin(angle) + dy * Math.cos(angle);
        }
        boolean overCaret(double mouseX, double mouseY) {
            double localX = localX(mouseX, mouseY);
            double localY = localY(mouseX, mouseY);
            return localX >= 109 && localX < 130 && localY >= 80 && localY < 112;
        }
        boolean departing() { return targetOpacity == 0; }
        boolean visible() { return opacity > 0.005F; }
        boolean contains(double mouseX, double mouseY) {
            double localX = localX(mouseX, mouseY);
            double localY = localY(mouseX, mouseY);
            return localX >= 0 && localX < width() && localY >= 0 && localY < 192;
        }
    }
}
