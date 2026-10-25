package com.jvn.villagerretaliation.client.interaction;

import com.google.gson.JsonParser;
import com.jvn.toucanlib.client.camera.ToucanCameraBasis;
import com.jvn.villagerretaliation.dialogue.DialogueCamera;
import com.jvn.villagerretaliation.dialogue.DialogueCamera.Focus;
import com.jvn.villagerretaliation.dialogue.DialogueCameraReference;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation.Camera;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class CameraFocusClientChecks {
    private static DialogueCameraController.Preview preview;
    private static int step;
    private static long shown;
    private static ItemEntity item;
    private static BlockPos block;
    private static net.minecraft.world.level.block.state.BlockState priorBlock;
    private static DialogueCameraTimeline.Pose beforeRemoval;
    private static boolean leftHanded;

    private CameraFocusClientChecks() { }

    static boolean tick(Villager speaker) {
        var minecraft = Minecraft.getInstance();
        if (preview == null) {
            leftHanded = speaker.isLeftHanded();
            speaker.setLeftHanded(true);
            block = speaker.blockPosition().offset(2, 0, -1);
            priorBlock = minecraft.level.getBlockState(block);
            minecraft.level.setBlock(block, Blocks.DIAMOND_BLOCK.defaultBlockState(), 3);
            item = new ItemEntity(minecraft.level, block.getX() + .5, block.getY() + 1, block.getZ() + .5, new ItemStack(Items.EMERALD));
            item.setNoGravity(true);
            minecraft.level.addEntity(item);
            preview = DialogueCameraController.beginPreview();
            show(speaker);
            return false;
        }
        if (Util.getMillis() - shown < 450) return false;
        var pose = DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
        require(pose != null, "focused camera remains active at step " + step);
        if (step < 13) {
            var focus = step == 12 ? Focus.MAIN_HAND : Focus.values()[1 + step % 6];
            Entity subject = step >= 6 ? minecraft.player : speaker;
            require(DialogueCameraFocusPoints.captured(subject, focus), "rendered model supplies " + focus + " at step " + step);
            aim(pose, DialogueCameraFocusPoints.point(subject, focus, 1), "body focus " + step);
            require(DialogueCameraController.detached(), "player body remains visible for equipment focus");
            if (step == 0) {
                Vec3 main = DialogueCameraFocusPoints.point(speaker, Focus.MAIN_HAND, 1);
                Vec3 off = DialogueCameraFocusPoints.point(speaker, Focus.OFF_HAND, 1);
                require(main.distanceTo(off) > .1, "main hand and off hand resolve distinct posed arms");
            }
        } else if (step == 13) {
            aim(pose, Vec3.atCenterOf(block), "world block");
            require(!DialogueCameraOcclusion.hides(block), "occlusion never hides the block being showcased");
        } else if (step == 14) {
            aim(pose, item.position().add(0, item.getBbHeight() * .5 + .1, 0), "dropped item");
            beforeRemoval = pose;
            minecraft.level.removeEntity(item.getId(), Entity.RemovalReason.DISCARDED);
            var next = DialogueCameraController.update(minecraft.gameRenderer.getMainCamera(), 1);
            require(Math.abs(Mth.wrapDegrees(next.yaw() - beforeRemoval.yaw())) < 1 && Math.abs(next.pitch() - beforeRemoval.pitch()) < 1,
                    "losing a bound item starts a smooth fallback");
        } else {
            aim(pose, speaker.getEyePosition(1), "missing item fallback");
            preview.close();
            preview = null;
            speaker.setLeftHanded(leftHanded);
            minecraft.level.setBlock(block, priorBlock, 3);
            return true;
        }
        step++;
        if (step != 15) show(speaker);
        else shown = Util.getMillis();
        return false;
    }

    private static void show(Villager speaker) {
        var minecraft = Minecraft.getInstance();
        String properties = step < 12 ? "\"target\":\"" + (step < 6 ? "speaker" : "player") + "\",\"focus\":\""
                + Focus.values()[1 + step % 6].name().toLowerCase(java.util.Locale.ROOT) + "\""
                : step == 12 ? "\"target\":\"player\",\"focus\":\"main_hand\""
                : "\"reference\":{\"id\":\"prop\",\"kind\":\"" + (step == 13 ? "block" : "item")
                    + "\",\"match\":\"" + (step == 13 ? "minecraft:diamond_block" : "minecraft:emerald") + "\"}";
        Camera camera = DialogueCamera.read(JsonParser.parseString("{\"camera\":{\"preset\":\""
                + (step == 12 ? "first_person" : "over_shoulder_left") + "\",\"transition\":{\"type\":\"cut\"}," + properties + "}}").getAsJsonObject());
        if (step >= 13) camera = new Camera(camera.target(), camera.zoom(), List.of(camera.shots().getFirst().withBinding(
                new DialogueCameraReference.Binding(minecraft.level.dimension().location(), step == 14 ? item.getUUID() : null, step == 13 ? block : null, ""))));
        preview.frame(new DialogueCameraController.Frame("focus-check/" + step, camera, speaker, speaker, List.of(speaker, minecraft.player), false));
        DialogueCameraController.synchronize();
        shown = Util.getMillis();
    }

    private static void aim(DialogueCameraTimeline.Pose pose, Vec3 point, String label) {
        var expected = ToucanCameraBasis.rotationToTarget(pose.position(), point, 0, 0);
        require(Math.abs(Mth.wrapDegrees(pose.yaw() - expected.yaw())) < 1.5 && Math.abs(pose.pitch() - expected.pitch()) < 1.5,
                label + " tracks its exact focus point");
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
