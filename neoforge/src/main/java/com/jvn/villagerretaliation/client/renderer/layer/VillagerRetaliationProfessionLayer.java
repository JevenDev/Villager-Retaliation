package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.HumanoidCompatVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.jvn.villagerretaliation.interaction.VillagerWorkAppearance;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

import java.io.IOException;
import java.util.Optional;

public class VillagerRetaliationProfessionLayer<T extends AbstractVillager & VillagerDataHolder> extends RenderLayer<T, BaseVillagerModel<T>> {
    private final Object2ObjectMap<VillagerType, VillagerMetaDataSection.Hat> vanillaTypeHatCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<VillagerType, VillagerMetaDataSection.Hat> retaliationTypeHatCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<VillagerProfession, VillagerMetaDataSection.Hat> vanillaProfessionHatCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<VillagerProfession, VillagerMetaDataSection.Hat> retaliationProfessionHatCache = new Object2ObjectOpenHashMap<>();
    private final ResourceManager resourceManager;
    private final String path;

    public VillagerRetaliationProfessionLayer(
            RenderLayerParent<T, BaseVillagerModel<T>> renderer,
            ResourceManager resourceManager,
            String path
    ) {
        super(renderer);
        this.resourceManager = resourceManager;
        this.path = path;
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (villager.isInvisible()) {
            return;
        }

        boolean useRetaliationTextures = this.getParentModel() instanceof VillagerRetaliationVillagerModel<?>
                && !(this.getParentModel() instanceof HumanoidCompatVillagerModel<?>);
        VillagerData villagerData = villager.getVillagerData();
        VillagerType type = villagerData.getType();
        VillagerProfession profession = VillagerWorkAppearance.profession(villager);
        VillagerMetaDataSection.Hat typeHat = getHatData(
                useRetaliationTextures ? this.retaliationTypeHatCache : this.vanillaTypeHatCache,
                useRetaliationTextures,
                "type",
                BuiltInRegistries.VILLAGER_TYPE,
                type
        );
        VillagerMetaDataSection.Hat professionHat = getHatData(
                useRetaliationTextures ? this.retaliationProfessionHatCache : this.vanillaProfessionHatCache,
                useRetaliationTextures,
                "profession",
                BuiltInRegistries.VILLAGER_PROFESSION,
                profession
        );

        BaseVillagerModel<T> model = this.getParentModel();
        var headParts = model.getHead().getAllParts().toList();
        boolean[] visible = new boolean[headParts.size()];
        for (int index = 0; index < visible.length; index++) visible[index] = headParts.get(index).visible;
        try {
            model.hatVisible(professionHat == VillagerMetaDataSection.Hat.NONE
                    || professionHat == VillagerMetaDataSection.Hat.PARTIAL && typeHat != VillagerMetaDataSection.Hat.FULL);
            renderColoredCutoutModel(
                    model,
                    getResourceLocation(useRetaliationTextures, "type", BuiltInRegistries.VILLAGER_TYPE.getKey(type)),
                    poseStack,
                    buffer,
                    packedLight,
                    villager,
                    -1
            );
            model.hatVisible(true);

            if (profession == VillagerProfession.NONE) return;
            renderColoredCutoutModel(
                    model,
                    getResourceLocation(useRetaliationTextures, "profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession)),
                    poseStack,
                    buffer,
                    packedLight,
                    villager,
                    -1
            );
        } finally {
            for (int index = 0; index < visible.length; index++) headParts.get(index).visible = visible[index];
        }
    }

    private <K> VillagerMetaDataSection.Hat getHatData(
            Object2ObjectMap<K, VillagerMetaDataSection.Hat> cache,
            boolean useRetaliationTextures,
            String category,
            DefaultedRegistry<K> registry,
            K value
    ) {
        return cache.computeIfAbsent(value, ignored -> this.resourceManager
                .getResource(getResourceLocation(useRetaliationTextures, category, registry.getKey(value)))
                .flatMap(VillagerRetaliationProfessionLayer::readHatData)
                .orElse(VillagerMetaDataSection.Hat.NONE));
    }

    private ResourceLocation getResourceLocation(boolean useRetaliationTextures, String category, ResourceLocation id) {
        if (useRetaliationTextures && ResourceLocation.DEFAULT_NAMESPACE.equals(id.getNamespace())) {
            return VillagerRetaliation.id("textures/entity/" + this.path + "/" + category + "/" + id.getPath() + ".png");
        }
        return id.withPath(texturePath -> "textures/entity/" + this.path + "/" + category + "/" + texturePath + ".png");
    }

    private static Optional<VillagerMetaDataSection.Hat> readHatData(Resource resource) {
        try {
            return resource.metadata()
                    .getSection(VillagerMetaDataSection.SERIALIZER)
                    .map(VillagerMetaDataSection::getHat);
        } catch (IOException exception) {
            return Optional.empty();
        }
    }
}
