package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.config.VillagerRetaliationConfig;
import net.minecraft.network.chat.Component;

final class MapLayers {
    enum Layer {
        VILLAGE_NAMES(1, "villages"), LOCATIONS(2, "locations"), QUESTS(4, "quests"), RELATIONS(8, "relations"), COURIERS(16, "couriers");
        final int bit;
        final String key;
        Layer(int bit, String key) { this.bit = bit; this.key = key; }
        Component title() { return text("layer." + key); }
    }

    private final boolean minimap;
    MapLayers(boolean minimap) { this.minimap = minimap; }
    boolean visible(Layer layer) { return (option().get() & layer.bit) != 0; }
    void toggle(Layer layer) {
        option().set(option().get() ^ layer.bit);
        VillagerRetaliationConfig.CONFIG.save();
    }
    private VillagerRetaliationConfig.ConfigValue<Integer> option() {
        return minimap ? VillagerRetaliationConfig.MINIMAP_LAYERS : VillagerRetaliationConfig.FULL_MAP_LAYERS;
    }
    static Component text(String key, Object... args) { return Component.translatable("map.villagerretaliation." + key, args); }
}
