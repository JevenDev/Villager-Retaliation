package com.jvn.villagerretaliation.campaign.selection;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class WorldCampaignConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.ConfigValue<String> CAMPAIGN = BUILDER
            .comment("Campaign for a new world. Existing worlds keep their permanent saved selection.")
            .define("campaign", WorldCampaign.DEFAULT.toString(), value -> value instanceof String id
                    && id.contains(":") && net.minecraft.resources.ResourceLocation.tryParse(id) != null);
    public static final ModConfigSpec.ConfigValue<java.util.List<? extends String>> ADDONS = BUILDER
            .comment("Addons for a new world, in low-to-high priority order. Existing worlds keep their saved addons.")
            .defineListAllowEmpty("addons", java.util.List.of(), () -> "example:addon", value -> value instanceof String id
                    && id.contains(":") && net.minecraft.resources.ResourceLocation.tryParse(id) != null);
    private static final ModConfigSpec SPEC = BUILDER.build();

    private WorldCampaignConfig() { }

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, "villagerretaliation-campaign-server.toml");
    }
}
