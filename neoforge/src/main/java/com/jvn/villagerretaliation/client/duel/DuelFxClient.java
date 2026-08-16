package com.jvn.villagerretaliation.client.duel;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.DuelFxStatePayload;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

public final class DuelFxClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ARENA_SHADER = VillagerRetaliation.id("duel_arena_boundary");
    private static final ResourceLocation EXIT_POST =
            VillagerRetaliation.id("shaders/post/duel_exit.json");
    private static final int ARENA_SEGMENTS = 96;
    private static final int RESULT_PULSE_TICKS = 28;
    private static final int LINGER_TICKS = 24;

    private static ShaderInstance arenaShader;
    private static PostChain exitPostChain;
    private static ArenaState arena;
    private static int boundaryDelayRemaining;
    private static int boundaryOutsideTicks;
    private static float previousBoundaryProgress;
    private static float boundaryProgress;
    private static float lingeringWipe;
    private static int resultPulseAge = -1;
    private static int resultKind = DuelFxStatePayload.RESULT_NONE;
    private static int postWidth = -1;
    private static int postHeight = -1;
    private static boolean loggedPostError;

    private DuelFxClient() {
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ARENA_SHADER,
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> arenaShader = shader);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to register duel visual shaders", exception);
        }
    }

    public static void accept(DuelFxStatePayload payload) {
        if (payload.active()) {
            boundaryDelayRemaining = payload.boundaryDelayTicks();
            boundaryOutsideTicks = 0;
            previousBoundaryProgress = 0.0F;
            boundaryProgress = 0.0F;
            lingeringWipe = 0.0F;
            resultPulseAge = -1;
            resultKind = DuelFxStatePayload.RESULT_NONE;
            arena = new ArenaState(
                    new Vec3(payload.centerX(), payload.centerY(), payload.centerZ()),
                    payload.radius(),
                    payload.boundaryVisible(),
                    payload.boundaryGraceTicks());
            return;
        }

        if (arena != null) {
            lingeringWipe = boundaryProgress;
        }
        if (payload.result() != DuelFxStatePayload.RESULT_NONE) {
            resultPulseAge = 0;
            resultKind = payload.result();
        }
        arena = null;
        boundaryDelayRemaining = 0;
        boundaryOutsideTicks = 0;
        previousBoundaryProgress = 0.0F;
        boundaryProgress = 0.0F;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isPaused()) {
            return;
        }

        previousBoundaryProgress = boundaryProgress;
        if (arena != null && minecraft.player != null) {
            if (boundaryDelayRemaining > 0) {
                boundaryDelayRemaining--;
                boundaryOutsideTicks = 0;
                boundaryProgress = approach(boundaryProgress, 0.0F, 0.08F);
            } else {
                boolean outside = horizontalDistanceSqr(minecraft.player.position(), arena.center())
                        > arena.radius() * arena.radius();
                boundaryOutsideTicks = outside ? boundaryOutsideTicks + 1 : 0;
                float target = smooth(boundaryOutsideTicks / (float) arena.boundaryGraceTicks());
                boundaryProgress = approach(boundaryProgress, target, outside ? 0.05F : 0.075F);
            }
        } else {
            boundaryProgress = approach(boundaryProgress, 0.0F, 0.075F);
        }

        if (resultPulseAge >= 0 && ++resultPulseAge > RESULT_PULSE_TICKS) {
            resultPulseAge = -1;
            resultKind = DuelFxStatePayload.RESULT_NONE;
        }
        if (lingeringWipe > 0.0F && resultPulseAge < 0) {
            lingeringWipe = 0.0F;
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || arena == null
                || !arena.boundaryVisible()
                || arena.radius() <= 0.0F
                || arenaShader == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        double maxDistance = arena.radius() + 64.0D;
        if (horizontalDistanceSqr(camera, arena.center()) > maxDistance * maxDistance) {
            return;
        }

        float centerX = (float) (arena.center().x - camera.x);
        float centerY = (float) (arena.center().y - camera.y);
        float centerZ = (float) (arena.center().z - camera.z);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float gameTime = minecraft.level.getGameTime() + partialTick;

        setUniform(arenaShader, "RingCenter", centerX, centerY, centerZ);
        setUniform(arenaShader, "GameTime", gameTime);
        setUniform(arenaShader, "DangerProgress", interpolatedBoundaryProgress(partialTick));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> arenaShader);

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);
        appendCylinder(buffer, centerX, centerY, centerZ, arena.radius(), 92);
        appendCylinder(buffer, centerX, centerY, centerZ, arena.radius() + 0.18F, 44);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    public static void onRenderGuiPost(RenderGuiEvent.Post event) {
        if (Minecraft.getInstance().screen == null) {
            renderScreenEffect(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
    }

    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (event.getScreen() == Minecraft.getInstance().screen) {
            renderScreenEffect(event.getPartialTick());
        }
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        arena = null;
        boundaryDelayRemaining = 0;
        boundaryOutsideTicks = 0;
        previousBoundaryProgress = 0.0F;
        boundaryProgress = 0.0F;
        lingeringWipe = 0.0F;
        resultPulseAge = -1;
        resultKind = DuelFxStatePayload.RESULT_NONE;
        closeExitChain();
    }

    private static void appendCylinder(
            BufferBuilder buffer,
            float centerX,
            float centerY,
            float centerZ,
            float radius,
            int alpha) {
        float bottom = centerY - 0.45F;
        float top = centerY + 5.25F;
        for (int segment = 0; segment < ARENA_SEGMENTS; segment++) {
            double angle0 = Math.PI * 2.0D * segment / ARENA_SEGMENTS;
            double angle1 = Math.PI * 2.0D * (segment + 1) / ARENA_SEGMENTS;
            float x0 = centerX + (float) Math.cos(angle0) * radius;
            float z0 = centerZ + (float) Math.sin(angle0) * radius;
            float x1 = centerX + (float) Math.cos(angle1) * radius;
            float z1 = centerZ + (float) Math.sin(angle1) * radius;

            buffer.addVertex(x0, bottom, z0).setColor(244, 218, 132, alpha);
            buffer.addVertex(x1, bottom, z1).setColor(244, 218, 132, alpha);
            buffer.addVertex(x1, top, z1).setColor(244, 218, 132, alpha);
            buffer.addVertex(x0, top, z0).setColor(244, 218, 132, alpha);
        }
    }

    private static void renderScreenEffect(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        float partial = Mth.clamp(partialTick, 0.0F, 1.0F);
        float wipe = interpolatedBoundaryProgress(partial);
        float pulse = 0.0F;
        if (resultPulseAge >= 0) {
            float pulseTime = Mth.clamp((resultPulseAge + partial) / RESULT_PULSE_TICKS, 0.0F, 1.0F);
            pulse = Mth.sin((float) Math.PI * pulseTime);
            float lingerFade = 1.0F - smooth(
                    Mth.clamp((resultPulseAge + partial) / LINGER_TICKS, 0.0F, 1.0F));
            wipe = Math.max(wipe, lingeringWipe * lingerFade);
        }
        if (wipe <= 0.001F && pulse <= 0.001F) {
            return;
        }

        try {
            ensureExitChain(minecraft);
            if (exitPostChain == null) {
                return;
            }
            float time = (minecraft.level.getGameTime() + partialTick) / 20.0F;
            exitPostChain.setUniform("UTime", time);
            exitPostChain.setUniform("WipeProgress", Mth.clamp(wipe, 0.0F, 1.0F));
            exitPostChain.setUniform("EffectOpacity", 1.0F);
            exitPostChain.setUniform("PulseStrength", pulse);
            exitPostChain.setUniform("WinPulse", resultKind == DuelFxStatePayload.RESULT_WIN ? pulse : 0.0F);
            exitPostChain.setUniform("LossPulse", resultKind == DuelFxStatePayload.RESULT_LOSS ? pulse : 0.0F);
            exitPostChain.process(partialTick);
            minecraft.getMainRenderTarget().bindWrite(false);
            loggedPostError = false;
        } catch (RuntimeException | IOException exception) {
            if (!loggedPostError) {
                LOGGER.error("Failed to render the duel boundary effect", exception);
                loggedPostError = true;
            }
            closeExitChain();
        }
    }

    private static void ensureExitChain(Minecraft minecraft) throws IOException {
        int width = minecraft.getMainRenderTarget().width;
        int height = minecraft.getMainRenderTarget().height;
        if (exitPostChain == null) {
            exitPostChain = new PostChain(
                    minecraft.getTextureManager(),
                    minecraft.getResourceManager(),
                    minecraft.getMainRenderTarget(),
                    EXIT_POST);
            postWidth = -1;
            postHeight = -1;
        }
        if (postWidth != width || postHeight != height) {
            exitPostChain.resize(width, height);
            postWidth = width;
            postHeight = height;
        }
    }

    private static void closeExitChain() {
        if (exitPostChain != null) {
            exitPostChain.close();
            exitPostChain = null;
        }
        postWidth = -1;
        postHeight = -1;
        loggedPostError = false;
    }

    private static float smooth(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static float approach(float value, float target, float maximumChange) {
        return value < target
                ? Math.min(value + maximumChange, target)
                : Math.max(value - maximumChange, target);
    }

    private static float interpolatedBoundaryProgress(float partialTick) {
        return Mth.lerp(Mth.clamp(partialTick, 0.0F, 1.0F), previousBoundaryProgress, boundaryProgress);
    }

    private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return x * x + z * z;
    }

    private static void setUniform(ShaderInstance shader, String name, float value) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void setUniform(
            ShaderInstance shader,
            String name,
            float x,
            float y,
            float z) {
        var uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private record ArenaState(Vec3 center, float radius, boolean boundaryVisible, int boundaryGraceTicks) {
    }
}
