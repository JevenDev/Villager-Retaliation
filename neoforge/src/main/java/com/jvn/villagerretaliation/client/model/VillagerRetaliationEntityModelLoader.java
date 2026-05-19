package com.jvn.villagerretaliation.client.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.jvn.villagerretaliation.VillagerRetaliation;
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

import java.io.Reader;
import java.util.List;
import java.util.Optional;

public final class VillagerRetaliationEntityModelLoader {
    public static final ResourceLocation COMBAT_VILLAGER_MODEL =
            VillagerRetaliation.id("models/entity/villager/combat_villager.json");

    private static final String EMF_MOD_ID = "entity_model_features";
    private static final String MOD_RESOURCE_PACK_ID = "mod/" + VillagerRetaliation.MOD_ID;
    private static final Gson GSON = new Gson();
    private static final Logger LOGGER = LogUtils.getLogger();

    private VillagerRetaliationEntityModelLoader() {
    }

    public static ModelPart loadCombatVillagerModel(EntityRendererProvider.Context context) {
        ResourceManager resourceManager = context.getResourceManager();
        Optional<Resource> overrideResource = findResourcePackOverride(resourceManager);
        if (overrideResource.isPresent()) {
            LOGGER.info("Loading combat villager model from json:{}", overrideResource.get().sourcePackId());
            return loadCombatVillagerModel(overrideResource.get());
        }
        if (isEntityModelFeaturesLoaded()) {
            LOGGER.info("Loading combat villager model through EMF-compatible baked layer");
            return context.bakeLayer(VillagerRetaliationVillagerModel.LAYER_LOCATION);
        }
        LOGGER.info("Loading combat villager model from built-in JSON fallback");
        return loadCombatVillagerModel(resourceManager);
    }

    public static String combatVillagerModelSource(ResourceManager resourceManager) {
        Optional<Resource> overrideResource = findResourcePackOverride(resourceManager);
        if (overrideResource.isPresent()) {
            return "json:" + overrideResource.get().sourcePackId();
        }
        return isEntityModelFeaturesLoaded() ? "emf:" + EMF_MOD_ID : "json:" + MOD_RESOURCE_PACK_ID;
    }

    public static ModelPart loadCombatVillagerModel(ResourceManager resourceManager) {
        LayerDefinition layerDefinition = loadLayerDefinition(resourceManager)
                .orElseGet(VillagerRetaliationVillagerModel::createBodyLayer);
        ModelPart root = layerDefinition.bakeRoot();
        if (hasRequiredCombatParts(root)) {
            return root;
        }

        LOGGER.warn("Combat villager model {} is missing required parts. Falling back to the built-in model.", COMBAT_VILLAGER_MODEL);
        return VillagerRetaliationVillagerModel.createBodyLayer().bakeRoot();
    }

    private static ModelPart loadCombatVillagerModel(Resource resource) {
        LayerDefinition layerDefinition = loadLayerDefinition(resource)
                .orElseGet(VillagerRetaliationVillagerModel::createBodyLayer);
        ModelPart root = layerDefinition.bakeRoot();
        if (hasRequiredCombatParts(root)) {
            return root;
        }

        LOGGER.warn("Combat villager model from {} is missing required parts. Falling back to the built-in model.", resource.sourcePackId());
        return VillagerRetaliationVillagerModel.createBodyLayer().bakeRoot();
    }

    private static Optional<LayerDefinition> loadLayerDefinition(ResourceManager resourceManager) {
        Optional<Resource> resource = resourceManager.getResource(COMBAT_VILLAGER_MODEL);
        if (resource.isEmpty()) {
            return Optional.empty();
        }

        return loadLayerDefinition(resource.get());
    }

    private static Optional<LayerDefinition> loadLayerDefinition(Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) {
                throw new JsonParseException("Model file is empty");
            }
            return Optional.of(parseLayerDefinition(json));
        } catch (Exception exception) {
            LOGGER.warn("Failed to load combat villager model {}. Falling back to the built-in model.", COMBAT_VILLAGER_MODEL, exception);
            return Optional.empty();
        }
    }

    private static Optional<Resource> findResourcePackOverride(ResourceManager resourceManager) {
        List<Resource> resourceStack = resourceManager.getResourceStack(COMBAT_VILLAGER_MODEL);
        for (int i = resourceStack.size() - 1; i >= 0; i--) {
            Resource resource = resourceStack.get(i);
            if (!MOD_RESOURCE_PACK_ID.equals(resource.sourcePackId())) {
                return Optional.of(resource);
            }
        }
        return Optional.empty();
    }

    private static boolean isEntityModelFeaturesLoaded() {
        return ModList.get().isLoaded(EMF_MOD_ID);
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
            ModelPart body = root.getChild("body");
            ModelPart head = body.getChild("head");
            body.getChild("RightArm");
            body.getChild("LeftArm");
            body.getChild("RightLeg");
            body.getChild("LeftLeg");
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
}
