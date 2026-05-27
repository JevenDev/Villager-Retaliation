package com.jvn.villagerretaliation.client.reputation;

import com.jvn.toucanlib.client.ToucanWorldTextIndicators;
import com.jvn.toucanlib.client.ToucanWorldTextStyle;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorKind;
import com.jvn.villagerretaliation.network.VillagerWorldTextIndicatorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class VillagerWorldTextIndicatorClient {
    private static final int TEXT_LIFETIME_MILLIS = 720;
    private static final float BASE_SCALE = 0.015F;
    private static final ToucanWorldTextIndicators WORLD_TEXT = new ToucanWorldTextIndicators();
    private static final int ALERT_COLOR = 0xFFFFD166;
    private static final int MURMUR_COLOR = 0xFFE9EEF5;
    private static final int POSITIVE_COLOR = 0xFF8DFF9E;
    private static final int NEGATIVE_COLOR = 0xFFFF7A7A;
    private static final int TRADE_COLOR = 0xFF6BFFB4;
    private static final int DIALOGUE_COLOR = 0xFFD7C7FF;
    private static final int SLEEP_COLOR = 0xFFA9E8FF;
    private static final MotionProfile ALERT_MOTION = new MotionProfile(0.08D, 0.10D, 0.20D, 0.16D, 0.10D, 0.08D, 11.0F);
    private static final MotionProfile NEGATIVE_MOTION = new MotionProfile(0.07D, 0.08D, 0.16D, 0.12D, 0.07D, 0.06D, 9.0F);
    private static final MotionProfile POSITIVE_TRADE_MOTION = new MotionProfile(0.06D, 0.06D, 0.14D, 0.12D, 0.07D, 0.06D, 7.0F);
    private static final MotionProfile DIALOGUE_MOTION = new MotionProfile(0.05D, 0.06D, 0.11D, 0.09D, 0.055D, 0.045D, 6.0F);
    private static final MotionProfile DEFAULT_MOTION = new MotionProfile(0.04D, 0.06D, 0.08D, 0.07D, 0.04D, 0.035D, 5.0F);
    private static long pausedAtMillis = -1L;
    private static long pausedDurationMillis;

    private VillagerWorldTextIndicatorClient() {
    }

    public static void accept(VillagerWorldTextIndicatorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> add(payload));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            WORLD_TEXT.clear();
            return;
        }

        WORLD_TEXT.render(
                minecraft,
                event.getPoseStack(),
                event.getCamera().getPosition(),
                event.getPartialTick().getGameTimeDeltaPartialTick(true),
                now()
        );
    }

    private static void add(VillagerWorldTextIndicatorPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Entity entity = minecraft.level.getEntity(payload.entityId());
        if (!(entity instanceof AbstractVillager villager) || !villager.isAlive()) {
            return;
        }

        ToucanWorldTextStyle style = style(payload.text(), payload.kind(), payload.textColor());
        if (payload.kind() == VillagerWorldTextIndicatorKind.SLEEP) {
            addSleepEntry(villager, payload.text(), style);
            return;
        }

        addFlowEntry(minecraft, villager, style, payload.kind(), 1.0D);
        spawnAccentParticles(minecraft.level, villager, payload.kind());

        if (payload.kind() == VillagerWorldTextIndicatorKind.ALERT && !"!".equals(payload.text())) {
            addFlowEntry(minecraft, villager, alertAccentStyle(), VillagerWorldTextIndicatorKind.ALERT, -1.0D);
        }
    }

    private static ToucanWorldTextStyle style(String text, VillagerWorldTextIndicatorKind kind, int customColor) {
        int color = customColor != Integer.MIN_VALUE ? customColor : defaultColor(kind);
        return switch (kind) {
            case ALERT -> new ToucanWorldTextStyle(text, color, false, TEXT_LIFETIME_MILLIS, BASE_SCALE * 1.05F, 0.0D, 0.0D, 0.0D, 2.15F, 0.010F, 0.024F, 8.0F);
            case POSITIVE -> new ToucanWorldTextStyle(text, color, false, TEXT_LIFETIME_MILLIS + 40, BASE_SCALE * 0.82F, 0.0D, 0.0D, 0.0D, 1.65F, 0.014F, 0.024F, 5.5F);
            case NEGATIVE -> new ToucanWorldTextStyle(text, color, true, TEXT_LIFETIME_MILLIS + 20, BASE_SCALE * 0.84F, 0.0D, 0.0D, 0.0D, 1.75F, 0.014F, 0.028F, 7.0F);
            case TRADE -> new ToucanWorldTextStyle(text, color, false, TEXT_LIFETIME_MILLIS + 35, BASE_SCALE * 0.80F, 0.0D, 0.0D, 0.0D, 1.55F, 0.012F, 0.022F, 5.0F);
            case DIALOGUE -> new ToucanWorldTextStyle(text, color, false, TEXT_LIFETIME_MILLIS - 40, BASE_SCALE * 0.76F, 0.0D, 0.0D, 0.0D, 1.45F, 0.012F, 0.020F, 4.5F);
            case SLEEP -> new ToucanWorldTextStyle(text, color, true, 1650, BASE_SCALE * 0.72F, 0.0D, 0.0D, 0.0D, 1.10F, 0.026F, 0.018F, 3.0F);
            default -> new ToucanWorldTextStyle(text, color, true, TEXT_LIFETIME_MILLIS, BASE_SCALE * 0.70F, 0.0D, 0.0D, 0.0D, 1.20F, 0.008F, 0.016F, 4.0F);
        };
    }

    private static int defaultColor(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> ALERT_COLOR;
            case POSITIVE -> POSITIVE_COLOR;
            case NEGATIVE -> NEGATIVE_COLOR;
            case TRADE -> TRADE_COLOR;
            case DIALOGUE -> DIALOGUE_COLOR;
            case SLEEP -> SLEEP_COLOR;
            default -> MURMUR_COLOR;
        };
    }

    private static ToucanWorldTextStyle alertAccentStyle() {
        return new ToucanWorldTextStyle("!", 0xFFFF8A5B, false, 360, BASE_SCALE * 0.78F, 0.0D, 0.0D, 0.0D, 2.45F, 0.0F, 0.010F, 10.0F);
    }

    private static void addSleepEntry(AbstractVillager villager, String text, ToucanWorldTextStyle style) {
        if ("ZZZ".equalsIgnoreCase(text)) {
            addSleepZSequence(villager);
            return;
        }
        if ("*snores*".equalsIgnoreCase(text)) {
            spawnSnoreBubblePop(villager);
            return;
        }
        addSleepText(villager, style, 0L, 0.0D);
    }

    private static void addSleepZSequence(AbstractVillager villager) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 center = villager.position().add(0.0D, villager.getBbHeight() * 0.78D, 0.0D);
        Vec3 horizontalToCamera = new Vec3(1.0D, 0.0D, 0.0D);
        if (minecraft.level != null) {
            Vec3 toCamera = minecraft.gameRenderer.getMainCamera().getPosition().subtract(center);
            horizontalToCamera = new Vec3(toCamera.x, 0.0D, toCamera.z);
            if (horizontalToCamera.lengthSqr() < 1.0E-4D) {
                horizontalToCamera = new Vec3(1.0D, 0.0D, 0.0D);
            }
        }

        Vec3 side = new Vec3(0.0D, 1.0D, 0.0D).cross(horizontalToCamera).normalize();
        Vec3 away = horizontalToCamera.normalize().scale(-1.0D);
        double sideSign = (villager.getId() & 1) == 0 ? 1.0D : -1.0D;
        Vec3 angled = side.scale((0.28D + Math.random() * 0.08D) * sideSign)
                .add(away.scale(0.08D + Math.random() * 0.06D))
                .add(0.0D, 0.78D + Math.random() * 0.08D, 0.0D)
                .normalize();
        Vec3 horizontalNormal = new Vec3(angled.z, 0.0D, -angled.x);
        if (horizontalNormal.lengthSqr() < 1.0E-4D) {
            horizontalNormal = side;
        } else {
            horizontalNormal = horizontalNormal.normalize();
        }

        double pathOffset = villager.getBbWidth() * 0.40D + 0.08D;
        double lateralOffset = (Math.random() - 0.5D) * 0.035D;
        Vec3 offset = angled.scale(pathOffset).add(horizontalNormal.scale(lateralOffset));
        Vec3 drift = angled.scale(0.86D + pathOffset * 0.08D).add(0.0D, 0.12D + pathOffset * 0.04D, 0.0D);
        float tilt = (float) ((5.0D + Math.random() * 2.0D) * -sideSign);
        float direction = tilt < 0.0F ? 1.0F : -1.0F;
        float phase = (float) (Math.random() * Mth.TWO_PI);
        double arcHeight = 0.18D + Math.random() * 0.04D;
        for (int index = 0; index < 3; index++) {
            ToucanWorldTextStyle zStyle = new ToucanWorldTextStyle(
                    "Z",
                    SLEEP_COLOR,
                    true,
                    3200,
                    BASE_SCALE * 0.58F,
                    0.0D,
                    0.0D,
                    0.0D,
                    1.0F,
                    0.022F,
                    0.012F,
                    Math.abs(tilt)
            );
            addSleepText(villager, zStyle, index * 720L, offset, drift, direction, phase, arcHeight);
        }
    }

    private static void spawnSnoreBubblePop(AbstractVillager villager) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        double sideSign = Math.random() < 0.5D ? -1.0D : 1.0D;
        Vec3 base = villager.position().add(
                sideSign * (villager.getBbWidth() * 0.28D + 0.08D),
                villager.getBbHeight() * 0.96D + 0.10D,
                0.0D
        );
        for (int index = 0; index < 4; index++) {
            double step = index * 0.075D;
            double x = base.x + sideSign * step + (Math.random() - 0.5D) * 0.035D;
            double y = base.y + index * 0.065D + Math.random() * 0.035D;
            double z = base.z + (Math.random() - 0.5D) * 0.06D;
            double dx = sideSign * (0.008D + index * 0.004D);
            double dy = 0.018D + index * 0.004D;
            double dz = (Math.random() - 0.5D) * 0.008D;
            minecraft.level.addParticle(ParticleTypes.BUBBLE, x, y, z, dx, dy, dz);
            if (index >= 2) {
                minecraft.level.addParticle(
                        ParticleTypes.BUBBLE_POP,
                        x + sideSign * 0.035D,
                        y + 0.045D,
                        z,
                        dx * 0.4D,
                        dy * 0.3D,
                        dz * 0.4D
                );
            }
        }
    }

    private static void addSleepText(AbstractVillager villager, ToucanWorldTextStyle style, long delayMillis, double sequenceOffset) {
        addSleepText(villager, style, delayMillis, sequenceOffset, 0.0D);
    }

    private static void addSleepText(AbstractVillager villager, ToucanWorldTextStyle style, long delayMillis, double sequenceOffset, double extraRise) {
        double sideSign = Math.random() < 0.5D ? -1.0D : 1.0D;
        double sideOffset = (0.04D + sequenceOffset + Math.random() * 0.08D) * sideSign;
        Vec3 offset = new Vec3(sideOffset, 0.16D + sequenceOffset + Math.random() * 0.06D, 0.0D);
        Vec3 drift = new Vec3(sideSign * (0.018D + Math.random() * 0.035D), 0.34D + extraRise + sequenceOffset + Math.random() * 0.10D, 0.0D);
        addSleepText(
                villager,
                style,
                delayMillis,
                offset,
                drift,
                sideSign > 0.0D ? 1.0F : -1.0F,
                (float) (Math.random() * Mth.TWO_PI),
                0.16D + Math.random() * 0.10D
        );
    }

    private static void addSleepText(
            AbstractVillager villager,
            ToucanWorldTextStyle style,
            long delayMillis,
            Vec3 offset,
            Vec3 drift,
            float direction,
            float phase,
            double arcHeight) {
        WORLD_TEXT.addAnchoredDirected(
                villager,
                offset,
                drift,
                style,
                direction,
                phase,
                arcHeight,
                0.96D,
                now() + delayMillis
        );
    }

    private static void addFlowEntry(
            Minecraft minecraft,
            AbstractVillager villager,
            ToucanWorldTextStyle style,
            VillagerWorldTextIndicatorKind kind,
            double sideBias) {
        MotionProfile motion = motionProfile(kind);
        Vec3 center = villager.position().add(0.0D, villager.getBbHeight() * 0.66D, 0.0D);
        Vec3 toCamera = minecraft.gameRenderer.getMainCamera().getPosition().subtract(center);
        Vec3 horizontalToCamera = new Vec3(toCamera.x, 0.0D, toCamera.z);
        if (horizontalToCamera.lengthSqr() < 1.0E-4D) {
            horizontalToCamera = new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 side = new Vec3(0.0D, 1.0D, 0.0D).cross(horizontalToCamera).normalize();
        Vec3 away = horizontalToCamera.normalize().scale(-1.0D);
        double randomSide = Math.random() < 0.5D ? -1.0D : 1.0D;
        double sideSign = sideBias * randomSide;
        double sideOffset = villager.getBbWidth() * 0.50D + sideOffset(style.label(), motion) + Math.random() * 0.035D;
        double awayOffset = motion.awayAmount() + Math.random() * 0.04D;
        double verticalOffset = motion.verticalOffset() + (Math.random() - 0.5D) * 0.04D;
        Vec3 offset = side.scale(sideOffset * sideSign).add(away.scale(awayOffset)).add(0.0D, verticalOffset, 0.0D);
        Vec3 drift = side.scale((motion.driftDistance() + Math.random() * 0.04D) * sideSign).add(0.0D, motion.driftRise(), 0.0D);
        double arcHeight = motion.arcHeight() + Math.random() * 0.08D;
        float tilt = (float) ((motion.tiltDegrees() + Math.random() * 3.0D) * -sideSign);

        WORLD_TEXT.addAnchoredDirected(
                villager,
                offset,
                drift,
                new ToucanWorldTextStyle(
                        style.label(),
                        style.color(),
                        style.italic(),
                        style.lifetimeMillis(),
                        style.scale(),
                        style.scatterRadius(),
                        style.driftRadius(),
                        style.riseDistance(),
                        style.popStrength(),
                        style.hoverAmplitude(),
                        style.swayAmplitude(),
                        Math.abs(tilt)
                ),
                tilt < 0.0F ? 1.0F : -1.0F,
                (float) (Math.random() * Mth.TWO_PI),
                arcHeight,
                0.90D,
                now()
        );
    }

    private static double sideOffset(String text, MotionProfile motion) {
        int length = text == null ? 1 : Math.max(1, text.strip().length());
        double textWidth = Mth.clamp(length, 1, 14) * 0.035D;
        return textWidth + motion.kindPadding();
    }

    private static MotionProfile motionProfile(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> ALERT_MOTION;
            case NEGATIVE -> NEGATIVE_MOTION;
            case POSITIVE, TRADE -> POSITIVE_TRADE_MOTION;
            case DIALOGUE -> DIALOGUE_MOTION;
            default -> DEFAULT_MOTION;
        };
    }

    private static void spawnAccentParticles(ClientLevel level, AbstractVillager villager, VillagerWorldTextIndicatorKind kind) {
        Vec3 center = villager.position().add(0.0D, villager.getBbHeight() * 0.82D, 0.0D);
        switch (kind) {
            case ALERT -> {
                spawnBurst(level, ParticleTypes.CRIT, center, 4, 0.12D, 0.08D);
                spawnBurst(level, ParticleTypes.ANGRY_VILLAGER, center.add(0.0D, 0.08D, 0.0D), 2, 0.04D, 0.04D);
            }
            case POSITIVE, TRADE -> {
                spawnBurst(level, ParticleTypes.HAPPY_VILLAGER, center, 3, 0.08D, 0.06D);
                spawnBurst(level, ParticleTypes.GLOW, center.add(0.0D, 0.04D, 0.0D), 3, 0.07D, 0.04D);
            }
            case NEGATIVE -> spawnBurst(level, ParticleTypes.ANGRY_VILLAGER, center, 3, 0.08D, 0.05D);
            case DIALOGUE -> spawnBurst(level, ParticleTypes.ENCHANT, center, 2, 0.05D, 0.04D);
            default -> {
            }
        }
    }

    private static void spawnBurst(ClientLevel level, ParticleOptions particle, Vec3 center, int count, double horizontalSpeed, double upwardSpeed) {
        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Mth.TWO_PI;
            double radius = 0.04D + Math.random() * 0.08D;
            double speed = horizontalSpeed * (0.55D + Math.random() * 0.55D);
            double dx = Math.cos(angle) * speed;
            double dz = Math.sin(angle) * speed;
            double dy = upwardSpeed * (0.55D + Math.random() * 0.75D);
            level.addParticle(
                    particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + (Math.random() - 0.5D) * 0.08D,
                    center.z + Math.sin(angle) * radius,
                    dx,
                    dy,
                    dz
            );
        }
    }

    private static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        long wallNow = System.currentTimeMillis();
        if (minecraft == null) {
            return wallNow;
        }

        if (minecraft.isPaused()) {
            if (pausedAtMillis < 0L) {
                pausedAtMillis = wallNow;
            }
        } else if (pausedAtMillis >= 0L) {
            pausedDurationMillis += wallNow - pausedAtMillis;
            pausedAtMillis = -1L;
        }

        long activePauseMillis = pausedAtMillis >= 0L ? wallNow - pausedAtMillis : 0L;
        return wallNow - pausedDurationMillis - activePauseMillis;
    }

    private record MotionProfile(
            double kindPadding,
            double awayAmount,
            double verticalOffset,
            double driftDistance,
            double driftRise,
            double arcHeight,
            float tiltDegrees) {
    }

}
