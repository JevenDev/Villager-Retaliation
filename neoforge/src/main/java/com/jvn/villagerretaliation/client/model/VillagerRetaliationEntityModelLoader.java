package com.jvn.villagerretaliation.client.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.client.VillagerRetaliationClientAssets;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;
import java.util.Optional;

public final class VillagerRetaliationEntityModelLoader {
    private static final String EMF_MOD_ID = "entity_model_features";
    private static final String MOD_RESOURCE_PACK_ID = "mod/" + VillagerRetaliation.MOD_ID;
    private static final List<ResourceLocation> COMBAT_VILLAGER_CEM_MODELS = List.of(
            VillagerRetaliationClientAssets.COMBAT_VILLAGER_CEM_MODEL,
            VillagerRetaliationClientAssets.COMBAT_VILLAGER_CEM_MODEL_DEPRECATED,
            VillagerRetaliationClientAssets.COMBAT_VILLAGER_CEM_MODEL_LEGACY_FOLDER
    );
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();
    private VillagerRetaliationEntityModelLoader() {
    }

    public static ModelPart loadCombatVillagerModel(EntityRendererProvider.Context context) {
        ResourceManager resourceManager = context.getResourceManager();
        Optional<Resource> overrideResource = findResourcePackOverride(resourceManager, VillagerRetaliationClientAssets.COMBAT_VILLAGER_MODEL);
        if (overrideResource.isPresent()) {
            LOGGER.info("Loading combat villager model from json:{}", overrideResource.get().sourcePackId());
            return loadCombatVillagerModel(overrideResource.get());
        }
        Optional<Resource> cemOverrideResource = findFirstResourcePackOverride(resourceManager, COMBAT_VILLAGER_CEM_MODELS);
        if (isEntityModelFeaturesLoaded() && cemOverrideResource.isPresent()) {
            LOGGER.info("Loading combat villager model through EMF-compatible baked layer:{}", cemOverrideResource.get().sourcePackId());
            ModelPart root = context.bakeLayer(VillagerRetaliationVillagerModel.LAYER_LOCATION);
            if (hasRequiredCombatParts(root)) {
                return root;
            }
            LOGGER.warn(
                    "Combat villager CEM model from {} is missing required parts. Falling back to the built-in JSON model.",
                    cemOverrideResource.get().sourcePackId()
            );
        }
        LOGGER.info("Loading combat villager model from built-in JSON fallback");
        return loadCombatVillagerModel(resourceManager);
    }

    public static Optional<ModelPart> loadNonCombatVillagerModel(ResourceManager resourceManager) {
        if (!shouldUseCustomNonCombatModel(resourceManager)) {
            return Optional.empty();
        }

        Optional<Resource> overrideResource = findResourcePackOverride(resourceManager, VillagerRetaliationClientAssets.NON_COMBAT_VILLAGER_MODEL);
        if (overrideResource.isEmpty()) {
            LOGGER.warn(
                    "Villager model options requested a custom non-combat model, but {} was not found. Falling back to vanilla crossed arms.",
                    VillagerRetaliationClientAssets.NON_COMBAT_VILLAGER_MODEL
            );
            return Optional.empty();
        }

        Resource resource = overrideResource.get();
        LOGGER.info("Loading non-combat villager model from json:{}", resource.sourcePackId());
        Optional<LayerDefinition> layerDefinition = loadLayerDefinition(resource, VillagerRetaliationClientAssets.NON_COMBAT_VILLAGER_MODEL);
        if (layerDefinition.isEmpty()) {
            return Optional.empty();
        }

        ModelPart root = layerDefinition.get().bakeRoot();
        if (hasRequiredCombatParts(root)) {
            return Optional.of(root);
        }

        LOGGER.warn(
                "Non-combat villager model from {} is missing required parts. Falling back to vanilla crossed arms.",
                resource.sourcePackId()
        );
        return Optional.empty();
    }

    public static boolean hasVanillaVillagerCemModel(ResourceManager resourceManager) {
        List<Resource> cemStack = resourceManager.getResourceStack(VillagerRetaliationClientAssets.VANILLA_VILLAGER_CEM_MODEL);
        List<Resource> textureStack = resourceManager.getResourceStack(VillagerRetaliationClientAssets.VANILLA_VILLAGER_SKIN);
        return isEntityModelFeaturesLoaded()
                && findResourcePackOverride(cemStack).isPresent()
                && hasTopTextureSize(textureStack, 64, 64);
    }

    public static ModelPart loadCombatVillagerModel(ResourceManager resourceManager) {
        LayerDefinition layerDefinition = loadLayerDefinition(resourceManager)
                .orElseGet(VillagerRetaliationVillagerModel::createBodyLayer);
        ModelPart root = layerDefinition.bakeRoot();
        if (hasRequiredCombatParts(root)) {
            return root;
        }

        LOGGER.warn("Combat villager model {} is missing required parts. Falling back to the built-in model.", VillagerRetaliationClientAssets.COMBAT_VILLAGER_MODEL);
        return VillagerRetaliationVillagerModel.createBodyLayer().bakeRoot();
    }

    private static ModelPart loadCombatVillagerModel(Resource resource) {
        LayerDefinition layerDefinition = loadLayerDefinition(resource, VillagerRetaliationClientAssets.COMBAT_VILLAGER_MODEL)
                .orElseGet(VillagerRetaliationVillagerModel::createBodyLayer);
        ModelPart root = layerDefinition.bakeRoot();
        if (hasRequiredCombatParts(root)) {
            return root;
        }

        LOGGER.warn("Combat villager model from {} is missing required parts. Falling back to the built-in model.", resource.sourcePackId());
        return VillagerRetaliationVillagerModel.createBodyLayer().bakeRoot();
    }

    private static Optional<LayerDefinition> loadLayerDefinition(ResourceManager resourceManager) {
        Optional<Resource> resource = resourceManager.getResource(VillagerRetaliationClientAssets.COMBAT_VILLAGER_MODEL);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        return loadLayerDefinition(resource.get());
    }

    private static Optional<LayerDefinition> loadLayerDefinition(Resource resource) {
        return loadLayerDefinition(resource, VillagerRetaliationClientAssets.COMBAT_VILLAGER_MODEL);
    }

    private static Optional<LayerDefinition> loadLayerDefinition(Resource resource, ResourceLocation modelLocation) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                throw new JsonParseException("Model file is empty");
            }
            return Optional.of(parseLayerDefinition(json));
        } catch (Exception exception) {
            LOGGER.warn("Failed to load villager model {}.", modelLocation, exception);
            return Optional.empty();
        }
    }

    private static Optional<Resource> findResourcePackOverride(ResourceManager resourceManager, ResourceLocation location) {
        return findResourcePackOverride(resourceManager.getResourceStack(location));
    }

    private static Optional<Resource> findFirstResourcePackOverride(ResourceManager resourceManager, List<ResourceLocation> locations) {
        for (ResourceLocation location : locations) {
            Optional<Resource> resource = findResourcePackOverride(resourceManager, location);
            if (resource.isPresent()) {
                return resource;
            }
        }
        return Optional.empty();
    }

    private static Optional<Resource> findResourcePackOverride(List<Resource> resourceStack) {
        for (int i = resourceStack.size() - 1; i >= 0; i--) {
            Resource resource = resourceStack.get(i);
            if (!MOD_RESOURCE_PACK_ID.equals(resource.sourcePackId())) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    private static boolean hasTopTextureSize(List<Resource> resourceStack, int width, int height) {
        if (resourceStack.isEmpty()) {
            return false;
        }

        Resource resource = resourceStack.getLast();
        try (InputStream inputStream = resource.open(); NativeImage image = NativeImage.read(inputStream)) {
            return image.getWidth() == width && image.getHeight() == height;
        } catch (IOException exception) {
            LOGGER.warn("Failed to read villager texture dimensions from {}.", resource.sourcePackId(), exception);
            return false;
        }
    }

    private static boolean isEntityModelFeaturesLoaded() {
        return ModList.get().isLoaded(EMF_MOD_ID);
    }

    private static boolean shouldUseCustomNonCombatModel(ResourceManager resourceManager) {
        return getNonCombatModelMode(resourceManager) == NonCombatModelMode.CUSTOM;
    }

    private static NonCombatModelMode getNonCombatModelMode(ResourceManager resourceManager) {
        Optional<Resource> optionsResource = findResourcePackOverride(resourceManager, VillagerRetaliationClientAssets.VILLAGER_MODEL_OPTIONS);
        if (optionsResource.isEmpty()) {
            return NonCombatModelMode.VANILLA;
        }

        Resource resource = optionsResource.get();
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                throw new JsonParseException("Options file is empty");
            }

            String value = getString(json, "non_combat_model");
            return NonCombatModelMode.fromSerializedName(value);
        } catch (Exception exception) {
            LOGGER.warn(
                    "Failed to load villager model options {} from {}. Falling back to vanilla crossed arms.",
                    VillagerRetaliationClientAssets.VILLAGER_MODEL_OPTIONS,
                    resource.sourcePackId(),
                    exception
            );
            return NonCombatModelMode.VANILLA;
        }
    }

    private static LayerDefinition parseLayerDefinition(JsonObject json) {
        int textureWidth = getInt(json, "texture_width", getInt(json, "textureWidth", 64));
        int textureHeight = getInt(json, "texture_height", getInt(json, "textureHeight", 64));
        JsonArray parts = getArray(json, "parts");

        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        for (JsonElement partElement : parts) {
            parsePart(root, asObject(partElement, "part"));
        }

        return LayerDefinition.create(meshDefinition, textureWidth, textureHeight);
    }

    private static void parsePart(PartDefinition parent, JsonObject json) {
        String name = getString(json, "name");
        PartDefinition part = parent.addOrReplaceChild(name, parseCubes(json), parsePose(json));

        JsonArray children = getOptionalArray(json, "children");
        if (children == null) {
            return;
        }

        for (JsonElement childElement : children) {
            parsePart(part, asObject(childElement, "child part"));
        }
    }

    private static CubeListBuilder parseCubes(JsonObject partJson) {
        CubeListBuilder cubes = CubeListBuilder.create();
        JsonArray cubeArray = getOptionalArray(partJson, "cubes");
        if (cubeArray == null) {
            return cubes;
        }

        for (JsonElement cubeElement : cubeArray) {
            JsonObject cubeJson = asObject(cubeElement, "cube");
            int[] uv = getIntPair(cubeJson, "uv");
            float[] origin = getFloatTriplet(cubeJson, "origin");
            float[] size = getFloatTriplet(cubeJson, "size");
            float inflate = getFloat(cubeJson, "inflate", getFloat(cubeJson, "deformation", 0.0F));
            boolean mirror = getBoolean(cubeJson, "mirror", false);

            cubes.texOffs(uv[0], uv[1]);
            if (mirror) {
                cubes.mirror();
            }
            cubes.addBox(origin[0], origin[1], origin[2], size[0], size[1], size[2], new CubeDeformation(inflate));
            if (mirror) {
                cubes.mirror(false);
            }
        }

        return cubes;
    }

    private static PartPose parsePose(JsonObject json) {
        float[] pivot = getFloatTriplet(json, "pivot", new float[]{0.0F, 0.0F, 0.0F});
        float[] rotation = getFloatTriplet(json, "rotation", new float[]{0.0F, 0.0F, 0.0F});
        float xRot = degreesToRadians(rotation[0]);
        float yRot = degreesToRadians(rotation[1]);
        float zRot = degreesToRadians(rotation[2]);
        return PartPose.offsetAndRotation(pivot[0], pivot[1], pivot[2], xRot, yRot, zRot);
    }

    private static boolean hasRequiredCombatParts(ModelPart root) {
        try {
            root.getChild("body");
            root.getChild("arms");
            root.getChild("RightArm");
            root.getChild("LeftArm");
            root.getChild("RightLeg");
            root.getChild("LeftLeg");
            ModelPart head = root.getChild("head");
            return head != null;
        } catch (Exception exception) {
            return false;
        }
    }

    private static JsonObject asObject(JsonElement element, String description) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException("Expected " + description + " to be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray getArray(JsonObject json, String key) {
        JsonArray array = getOptionalArray(json, key);
        if (array == null) {
            throw new JsonParseException("Missing required array '" + key + "'");
        }
        return array;
    }

    private static JsonArray getOptionalArray(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null) {
            return null;
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("Expected '" + key + "' to be an array");
        }
        return element.getAsJsonArray();
    }

    private static String getString(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException("Missing required string '" + key + "'");
        }
        return element.getAsString();
    }

    private static int getInt(JsonObject json, String key, int defaultValue) {
        JsonElement element = json.get(key);
        return element == null ? defaultValue : element.getAsInt();
    }

    private static float getFloat(JsonObject json, String key, float defaultValue) {
        JsonElement element = json.get(key);
        return element == null ? defaultValue : element.getAsFloat();
    }

    private static boolean getBoolean(JsonObject json, String key, boolean defaultValue) {
        JsonElement element = json.get(key);
        return element == null ? defaultValue : element.getAsBoolean();
    }

    private static int[] getIntPair(JsonObject json, String key) {
        JsonArray array = getArray(json, key);
        if (array.size() != 2) {
            throw new JsonParseException("Expected '" + key + "' to contain exactly 2 numbers");
        }
        return new int[]{array.get(0).getAsInt(), array.get(1).getAsInt()};
    }

    private static float[] getFloatTriplet(JsonObject json, String key) {
        return getFloatTriplet(json, key, null);
    }

    private static float[] getFloatTriplet(JsonObject json, String key, float[] defaultValue) {
        JsonArray array = getOptionalArray(json, key);
        if (array == null) {
            if (defaultValue == null) {
                throw new JsonParseException("Missing required vector '" + key + "'");
            }
            return defaultValue;
        }
        if (array.size() != 3) {
            throw new JsonParseException("Expected '" + key + "' to contain exactly 3 numbers");
        }
        return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float degreesToRadians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }

    private enum NonCombatModelMode {
        VANILLA("vanilla"),
        CUSTOM("custom");

        private final String serializedName;

        NonCombatModelMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static NonCombatModelMode fromSerializedName(String name) {
            for (NonCombatModelMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            throw new JsonParseException("Unknown non_combat_model value '" + name + "'");
        }
    }
}
