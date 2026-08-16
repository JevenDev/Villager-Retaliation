package com.jvn.villagerretaliation.client.renderer.layer;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.model.BaseVillagerModel;
import com.jvn.villagerretaliation.client.model.HumanoidCompatVillagerModel;
import com.jvn.villagerretaliation.client.model.VillagerRetaliationVillagerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

import java.io.IOException;
import java.util.Optional;

public class VillagerRetaliationProfessionLayer<T extends AbstractVillager & VillagerDataHolder> extends RenderLayer<T, BaseVillagerModel<T>> {
    private static final Int2ObjectMap<ResourceLocation> LEVEL_LOCATIONS = Util.make(new Int2ObjectOpenHashMap<>(), levels -> {
        levels.put(1, ResourceLocation.withDefaultNamespace("stone"));
        levels.put(2, ResourceLocation.withDefaultNamespace("iron"));
        levels.put(3, ResourceLocation.withDefaultNamespace("gold"));
        levels.put(4, ResourceLocation.withDefaultNamespace("emerald"));
        levels.put(5, ResourceLocation.withDefaultNamespace("diamond"));
    });

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
        VillagerProfession profession = villagerData.getProfession();
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

        if (profession == VillagerProfession.NONE || villager.isBaby()) {
            return;
        }

        renderColoredCutoutModel(
                model,
                getResourceLocation(useRetaliationTextures, "profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession)),
                poseStack,
                buffer,
                packedLight,
                villager,
                -1
        );
        if (profession == VillagerProfession.NITWIT) {
            return;
        }

        ResourceLocation levelLocation = LEVEL_LOCATIONS.get(Mth.clamp(villagerData.getLevel(), 1, LEVEL_LOCATIONS.size()));
        renderColoredCutoutModel(
                model,
                getResourceLocation(useRetaliationTextures, "profession_level", levelLocation),
                poseStack,
                buffer,
                packedLight,
                villager,
                -1
        );
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
