package com.jvn.villagerretaliation.client;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import net.minecraft.resources.ResourceLocation;

public final class VillagerRetaliationClientAssets {
    public static final ResourceLocation VANILLA_VILLAGER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    public static final ResourceLocation COMBAT_VILLAGER_SKIN =
            texture("entity/villager/villager");
    public static final ResourceLocation VANILLA_TRADER_SKIN =
            ResourceLocation.withDefaultNamespace("textures/entity/wandering_trader.png");
    public static final ResourceLocation COMBAT_TRADER_SKIN =
            texture("entity/wandering_trader/wandering_trader");

    public static final ResourceLocation DIVIDER_SELECT_TEXTURE =
            texture("gui/villager_interaction_screen/divider_select");
    public static final ResourceLocation GIFT_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/gift_inventory");
    public static final ResourceLocation GIFT_INFO_ICON_TEXTURE =
            texture("gui/villager_interaction_screen/info_icon");
    public static final ResourceLocation VILLAGER_INVENTORY_TEXTURE =
            texture("gui/villager_interaction_screen/villager_inventory");

    public static final ResourceLocation COMBAT_VILLAGER_MODEL =
            VillagerRetaliation.id("models/entity/villager/combat_villager.json");
    public static final ResourceLocation NON_COMBAT_VILLAGER_MODEL =
            VillagerRetaliation.id("models/entity/villager/non_combat_villager.json");
    public static final ResourceLocation VILLAGER_MODEL_OPTIONS =
            VillagerRetaliation.id("models/entity/villager/render_options.json");

    public static final ResourceLocation INTERACTION_VEIL_SHADER =
            VillagerRetaliation.id("interaction_veil");

    private VillagerRetaliationClientAssets() {
    }

    public static ResourceLocation reputationIcon(VillagerReputationLevel level) {
        return switch (level) {
            case ROYALTY -> texture("gui/container/icons/royalty");
            case REVERED -> texture("gui/container/icons/revered");
            case RESPECTED -> texture("gui/container/icons/respected");
            case TRUSTED -> texture("gui/container/icons/trusted");
            case NEUTRAL -> texture("gui/container/icons/neutral");
            case SUSPICIOUS -> texture("gui/container/icons/suspicious");
            case HOSTILE -> texture("gui/container/icons/hostile");
            case DESPISED -> texture("gui/container/icons/despised");
            case FEARED -> texture("gui/container/icons/feared");
        };
    }

    private static ResourceLocation texture(String path) {
        return VillagerRetaliation.id("textures/" + path + ".png");
    }
}
