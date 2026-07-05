package com.jvn.villagerretaliation.client.model;

import com.jvn.villagerretaliation.client.interaction.VillagerDialogueMouthAnimation;
import com.jvn.villagerretaliation.mixin.client.ModelPartAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;

final class VillagerDialogueMouthParts {
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final List<ModelPart> defaultMouthParts;
    private final List<ModelPart> mouthTalkContainers;
    private final List<ModelPart> fallbackMouthTalkParts;
    private final List<ModelPart> talk1Parts;
    private final List<ModelPart> talk2Parts;
    private final boolean hasTalkParts;

    private VillagerDialogueMouthParts(
            List<ModelPart> defaultMouthParts,
            List<ModelPart> mouthTalkContainers,
            List<ModelPart> fallbackMouthTalkParts,
            List<ModelPart> talk1Parts,
            List<ModelPart> talk2Parts) {
        this.defaultMouthParts = defaultMouthParts;
        this.mouthTalkContainers = mouthTalkContainers;
        this.fallbackMouthTalkParts = fallbackMouthTalkParts;
        this.talk1Parts = talk1Parts;
        this.talk2Parts = talk2Parts;
        this.hasTalkParts = !talk1Parts.isEmpty() || !talk2Parts.isEmpty() || !fallbackMouthTalkParts.isEmpty();
    }

    static VillagerDialogueMouthParts find(ModelPart root) {
        List<NamedPart> parts = new ArrayList<>();
        collect(root, "", parts);

        List<ModelPart> talk1Parts = partsNamed(parts, "talk1");
        List<ModelPart> talk2Parts = partsNamed(parts, "talk2");
        List<NamedPart> mouthTalkParts = parts.stream()
                .filter(part -> part.name().equals("mouthTalk"))
                .toList();
        List<ModelPart> mouthTalkContainers = mouthTalkParts.stream()
                .filter(part -> isAncestorOfAny(part.path(), parts, "talk1") || isAncestorOfAny(part.path(), parts, "talk2"))
                .map(NamedPart::part)
                .toList();
        List<ModelPart> fallbackMouthTalkParts = mouthTalkParts.stream()
                .filter(part -> !isAncestorOfAny(part.path(), parts, "talk1") && !isAncestorOfAny(part.path(), parts, "talk2"))
                .map(NamedPart::part)
                .toList();
        List<ModelPart> defaultMouthParts = parts.stream()
                .filter(part -> part.name().equals("mouth"))
                .filter(part -> !isAncestorOfAny(part.path(), parts, "talk1"))
                .filter(part -> !isAncestorOfAny(part.path(), parts, "talk2"))
                .map(NamedPart::part)
                .toList();
        return new VillagerDialogueMouthParts(defaultMouthParts, mouthTalkContainers, fallbackMouthTalkParts, talk1Parts, talk2Parts);
    }

    void apply(AbstractVillager villager, float ageInTicks) {
        if (!this.hasTalkParts) {
            return;
        }

        boolean talking = VillagerDialogueMouthAnimation.isTalking(villager);
        boolean stretchBetweenTalkParts = !this.talk1Parts.isEmpty() && !this.talk2Parts.isEmpty();
        float phase = villager == null ? 0.0F : (villager.getId() * 0.37F) % TWO_PI;
        float talkOpen = talking ? speechOpen(ageInTicks, phase) : 0.0F;
        float talkWidth = talking ? speechWidth(ageInTicks, phase, talkOpen) : 1.0F;
        boolean roundedShape = stretchBetweenTalkParts && talkWidth < 0.58F && talkOpen < 0.55F;
        boolean showTalk1 = talking && !this.talk1Parts.isEmpty() && (!roundedShape || this.talk2Parts.isEmpty());
        boolean showTalk2 = talking && !this.talk2Parts.isEmpty() && (roundedShape || this.talk1Parts.isEmpty());

        setVisible(this.defaultMouthParts, !talking);
        setVisible(this.mouthTalkContainers, talking);
        setVisible(this.fallbackMouthTalkParts, talking && this.talk1Parts.isEmpty() && this.talk2Parts.isEmpty());
        setVisible(this.talk1Parts, showTalk1);
        setVisible(this.talk2Parts, showTalk2);
        setScale(this.talk1Parts, talking ? talkWidth : 1.0F, talking ? 0.85F + 0.35F * talkOpen : 1.0F);
        setScale(this.talk2Parts, talking ? 0.95F + 0.12F * talkOpen : 1.0F, talking ? 0.90F + 0.28F * talkOpen : 1.0F);
        setXRot(this.talk1Parts, talking ? -0.07F * talkOpen : 0.0F);
        setXRot(this.talk2Parts, talking ? -0.05F * talkOpen : 0.0F);
    }

    private static float speechOpen(float ageInTicks, float phase) {
        float phrase = Mth.clamp(0.72F + 0.28F * Mth.sin(ageInTicks * 0.18F + phase), 0.35F, 1.0F);
        float fast = wave(ageInTicks * 1.45F + phase * 1.3F);
        float mid = wave(ageInTicks * 2.85F + phase * 0.7F);
        float flicker = wave(ageInTicks * 5.10F + phase * 2.1F);
        float closure = 1.0F - 0.55F * Mth.clamp((Mth.sin(ageInTicks * 0.75F + phase * 0.5F) - 0.55F) * 2.2F, 0.0F, 1.0F);
        float open = (0.08F + 0.36F * fast * fast + 0.28F * mid * mid + 0.16F * flicker * flicker) * phrase * closure;
        return Mth.clamp(open, 0.05F, 1.0F);
    }

    private static float speechWidth(float ageInTicks, float phase, float talkOpen) {
        float width = 0.72F
                + 0.20F * Mth.sin(ageInTicks * 0.92F + phase * 1.7F)
                + 0.10F * Mth.sin(ageInTicks * 2.15F + phase * 0.9F)
                - 0.12F * talkOpen;
        return Mth.clamp(width, 0.48F, 1.0F);
    }

    private static float wave(float value) {
        return 0.5F + 0.5F * Mth.sin(value);
    }

    private static void collect(ModelPart part, String path, List<NamedPart> parts) {
        for (Map.Entry<String, ModelPart> entry : ((ModelPartAccessor) (Object) part).villagerretaliation$children().entrySet()) {
            String childPath = path + "/" + entry.getKey();
            parts.add(new NamedPart(entry.getKey(), childPath, entry.getValue()));
            collect(entry.getValue(), childPath, parts);
        }
    }

    private static List<ModelPart> partsNamed(List<NamedPart> parts, String name) {
        return parts.stream()
                .filter(part -> part.name().equals(name))
                .map(NamedPart::part)
                .toList();
    }

    private static boolean isAncestorOfAny(String path, List<NamedPart> parts, String name) {
        String childPathPrefix = path + "/";
        return parts.stream()
                .anyMatch(part -> part.name().equals(name) && part.path().startsWith(childPathPrefix));
    }

    private static void setVisible(List<ModelPart> parts, boolean visible) {
        for (ModelPart part : parts) {
            part.visible = visible;
        }
    }

    private static void setScale(List<ModelPart> parts, float xScale, float yScale) {
        for (ModelPart part : parts) {
            part.xScale = xScale;
            part.yScale = yScale;
        }
    }

    private static void setXRot(List<ModelPart> parts, float xRot) {
        for (ModelPart part : parts) {
            part.xRot = xRot;
        }
    }

    private record NamedPart(String name, String path, ModelPart part) {
    }
}
