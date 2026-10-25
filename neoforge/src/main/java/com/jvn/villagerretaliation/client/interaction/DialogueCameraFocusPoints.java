package com.jvn.villagerretaliation.client.interaction;

import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.client.villager.VillagerModelPreviewRenderContext;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Focus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.EnumMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class DialogueCameraFocusPoints {
    private static final Map<LivingEntity, Sample> samples = new WeakHashMap<>();
    private DialogueCameraFocusPoints() { }

    private record Sample(int tick, Map<Focus, Vec3> points) { }

    public static boolean captured(Entity entity, Focus focus) {
        var sample = samples.get(entity);
        return sample != null && Math.abs(entity.tickCount - sample.tick()) <= 2 && sample.points().containsKey(focus);
    }

    public static Vec3 point(Entity entity, Focus focus, float partialTick) {
        if (focus == Focus.EYES) return com.jvn.toucanlib.client.camera.ToucanCameraTargetPoints.focusPoint(entity, partialTick);
        if (captured(entity, focus)) return entity.getPosition(partialTick).add(samples.get(entity).points().get(focus));
        if (!(entity instanceof LivingEntity living)) return entity.getEyePosition(partialTick);
        double height = entity.getBbHeight();
        double y = switch (focus) {
            case HEAD -> height * .92;
            case CHEST -> height * .65;
            case LEGS -> height * .32;
            case FEET -> height * .08;
            default -> height * .48;
        };
        double side = 0;
        if (focus == Focus.MAIN_HAND || focus == Focus.OFF_HAND) {
            var arm = focus == Focus.MAIN_HAND ? living.getMainArm() : living.getMainArm().getOpposite();
            side = entity.getBbWidth() * .55 * (arm == HumanoidArm.RIGHT ? -1 : 1);
        }
        float yaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        return entity.getPosition(partialTick).add(new Vec3(side, y, 0).yRot((float) -Math.toRadians(yaw)));
    }

    public static void capture(LivingEntity entity, EntityModel<?> model, PoseStack poses, Matrix4f entry, float partialTick) {
        if (entry == null || VillagerModelPreviewRenderContext.isRendering(entity) || !DialogueCameraController.capturesFocus(entity)) return;
        var points = new EnumMap<Focus, Vec3>(Focus.class);
        // remove the renderer's incoming view transform, retaining the animated model transform
        var transform = new Matrix4f(entry).invert().mul(poses.last().pose());
        if (model instanceof HumanoidModel<?> humanoid) {
            part(points, Focus.HEAD, transform, humanoid.head, 0, -.25f, 0, model.young);
            part(points, Focus.CHEST, transform, humanoid.body, 0, .35f, 0, model.young);
            legs(points, transform, humanoid.leftLeg, humanoid.rightLeg, model.young);
        } else if (model instanceof VillagerRetaliationVillagerModel<?> villager) {
            var stack = stack(transform);
            villager.translateRoot(stack);
            var root = villager.root();
            part(points, Focus.HEAD, stack.last().pose(), root.getChild("head"), 0, -.3f, 0, false);
            part(points, Focus.CHEST, stack.last().pose(), root.getChild("body"), 0, .35f, 0, false);
            legs(points, stack.last().pose(), root.getChild("LeftLeg"), root.getChild("RightLeg"), false);
        }
        if (model instanceof ArmedModel || model instanceof VillagerRetaliationVillagerModel<?>) {
            for (var focus : new Focus[]{Focus.MAIN_HAND, Focus.OFF_HAND}) {
                var stack = stack(transform);
                if (model.young) { stack.translate(0, .75, 0); stack.scale(.5f, .5f, .5f); }
                var arm = focus == Focus.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
                if (model instanceof ArmedModel armed) armed.translateToHand(arm, stack);
                else ((VillagerRetaliationVillagerModel<?>) model).translateToHand(arm, stack);
                stack.mulPose(Axis.XP.rotationDegrees(-90));
                stack.mulPose(Axis.YP.rotationDegrees(180));
                stack.translate((arm == HumanoidArm.LEFT ? -1 : 1) / 16.0, .125, -.625);
                points.put(focus, transform(stack, 0, 0, 0));
            }
        }
        var renderOffset = net.minecraft.client.Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity).getRenderOffset(entity, partialTick);
        points.replaceAll((focus, point) -> point.add(renderOffset));
        samples.put(entity, new Sample(entity.tickCount, points));
    }

    private static void legs(Map<Focus, Vec3> points, Matrix4f transform, ModelPart left, ModelPart right, boolean young) {
        var a = new EnumMap<Focus, Vec3>(Focus.class);
        var b = new EnumMap<Focus, Vec3>(Focus.class);
        part(a, Focus.LEGS, transform, left, 0, .3f, 0, young);
        part(b, Focus.LEGS, transform, right, 0, .3f, 0, young);
        part(a, Focus.FEET, transform, left, 0, .7f, 0, young);
        part(b, Focus.FEET, transform, right, 0, .7f, 0, young);
        points.put(Focus.LEGS, a.get(Focus.LEGS).lerp(b.get(Focus.LEGS), .5));
        points.put(Focus.FEET, a.get(Focus.FEET).lerp(b.get(Focus.FEET), .5));
    }

    private static void part(Map<Focus, Vec3> points, Focus focus, Matrix4f transform,
            ModelPart part, float x, float y, float z, boolean young) {
        var stack = stack(transform);
        if (young) {
            if (focus == Focus.HEAD) { stack.scale(.75f, .75f, .75f); stack.translate(0, 1, 0); }
            else { stack.scale(.5f, .5f, .5f); stack.translate(0, 1.5, 0); }
        }
        part.translateAndRotate(stack);
        points.put(focus, transform(stack, x, y, z));
    }

    private static PoseStack stack(Matrix4f transform) {
        var stack = new PoseStack();
        stack.mulPose(transform);
        return stack;
    }

    private static Vec3 transform(PoseStack stack, float x, float y, float z) {
        var point = stack.last().pose().transformPosition(new Vector3f(x, y, z));
        return new Vec3(point.x, point.y, point.z);
    }
}
