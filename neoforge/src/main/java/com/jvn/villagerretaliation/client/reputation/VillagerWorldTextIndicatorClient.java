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

        ToucanWorldTextStyle style = style(payload.text(), payload.kind());
        addFlowEntry(minecraft, villager, style, payload.kind(), 1.0D);
        spawnAccentParticles(minecraft.level, villager, payload.kind());

        if (payload.kind() == VillagerWorldTextIndicatorKind.ALERT && !"!".equals(payload.text())) {
            addFlowEntry(minecraft, villager, alertAccentStyle(), VillagerWorldTextIndicatorKind.ALERT, -1.0D);
        }
    }

    private static ToucanWorldTextStyle style(String text, VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> new ToucanWorldTextStyle(text, ALERT_COLOR, false, TEXT_LIFETIME_MILLIS, BASE_SCALE * 1.05F, 0.0D, 0.0D, 0.0D, 2.15F, 0.010F, 0.024F, 8.0F);
            case POSITIVE -> new ToucanWorldTextStyle(text, POSITIVE_COLOR, false, TEXT_LIFETIME_MILLIS + 40, BASE_SCALE * 0.82F, 0.0D, 0.0D, 0.0D, 1.65F, 0.014F, 0.024F, 5.5F);
            case NEGATIVE -> new ToucanWorldTextStyle(text, NEGATIVE_COLOR, true, TEXT_LIFETIME_MILLIS + 20, BASE_SCALE * 0.84F, 0.0D, 0.0D, 0.0D, 1.75F, 0.014F, 0.028F, 7.0F);
            case TRADE -> new ToucanWorldTextStyle(text, TRADE_COLOR, false, TEXT_LIFETIME_MILLIS + 35, BASE_SCALE * 0.80F, 0.0D, 0.0D, 0.0D, 1.55F, 0.012F, 0.022F, 5.0F);
            case DIALOGUE -> new ToucanWorldTextStyle(text, DIALOGUE_COLOR, false, TEXT_LIFETIME_MILLIS - 40, BASE_SCALE * 0.76F, 0.0D, 0.0D, 0.0D, 1.45F, 0.012F, 0.020F, 4.5F);
            default -> new ToucanWorldTextStyle(text, MURMUR_COLOR, true, TEXT_LIFETIME_MILLIS, BASE_SCALE * 0.70F, 0.0D, 0.0D, 0.0D, 1.20F, 0.008F, 0.016F, 4.0F);
        };
    }

    private static ToucanWorldTextStyle alertAccentStyle() {
        return new ToucanWorldTextStyle("!", 0xFFFF8A5B, false, 360, BASE_SCALE * 0.78F, 0.0D, 0.0D, 0.0D, 2.45F, 0.0F, 0.010F, 10.0F);
    }

    private static void addFlowEntry(
            Minecraft minecraft,
            AbstractVillager villager,
            ToucanWorldTextStyle style,
            VillagerWorldTextIndicatorKind kind,
            double sideBias) {
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
        double sideOffset = villager.getBbWidth() * 0.50D + sideOffset(style.label(), kind) + Math.random() * 0.035D;
        double awayOffset = awayAmount(kind) + Math.random() * 0.04D;
        double verticalOffset = verticalOffset(kind) + (Math.random() - 0.5D) * 0.04D;
        Vec3 offset = side.scale(sideOffset * sideSign).add(away.scale(awayOffset)).add(0.0D, verticalOffset, 0.0D);
        Vec3 drift = side.scale((driftDistance(kind) + Math.random() * 0.04D) * sideSign).add(0.0D, driftRise(kind), 0.0D);
        double arcHeight = arcHeight(kind) + Math.random() * 0.08D;
        float tilt = (float) ((tiltDegrees(kind) + Math.random() * 3.0D) * -sideSign);

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

    private static double sideOffset(String text, VillagerWorldTextIndicatorKind kind) {
        int length = text == null ? 1 : Math.max(1, text.strip().length());
        double textWidth = Mth.clamp(length, 1, 14) * 0.035D;
        double kindPadding = switch (kind) {
            case ALERT -> 0.08D;
            case NEGATIVE -> 0.07D;
            case POSITIVE, TRADE -> 0.06D;
            case DIALOGUE -> 0.05D;
            default -> 0.04D;
        };
        return textWidth + kindPadding;
    }

    private static double awayAmount(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> 0.10D;
            case NEGATIVE -> 0.08D;
            default -> 0.06D;
        };
    }

    private static double verticalOffset(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> 0.20D;
            case POSITIVE, TRADE -> 0.14D;
            case NEGATIVE -> 0.16D;
            case DIALOGUE -> 0.11D;
            default -> 0.08D;
        };
    }

    private static double driftDistance(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> 0.16D;
            case POSITIVE, NEGATIVE, TRADE -> 0.12D;
            case DIALOGUE -> 0.09D;
            default -> 0.07D;
        };
    }

    private static double driftRise(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> 0.10D;
            case POSITIVE, NEGATIVE, TRADE -> 0.07D;
            case DIALOGUE -> 0.055D;
            default -> 0.04D;
        };
    }

    private static double arcHeight(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> 0.08D;
            case POSITIVE, NEGATIVE, TRADE -> 0.06D;
            case DIALOGUE -> 0.045D;
            default -> 0.035D;
        };
    }

    private static float tiltDegrees(VillagerWorldTextIndicatorKind kind) {
        return switch (kind) {
            case ALERT -> 11.0F;
            case NEGATIVE -> 9.0F;
            case POSITIVE, TRADE -> 7.0F;
            case DIALOGUE -> 6.0F;
            default -> 5.0F;
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

}
