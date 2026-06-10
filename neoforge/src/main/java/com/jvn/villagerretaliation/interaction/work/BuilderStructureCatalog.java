package com.jvn.villagerretaliation.interaction.work;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class BuilderStructureCatalog {
    private static final List<Entry> ENTRIES = createEntries();
    private static final Map<ResourceLocation, Entry> BY_ID = ENTRIES.stream()
            .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.id(), entry), LinkedHashMap::putAll);

    private BuilderStructureCatalog() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Optional<Entry> byId(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    private static List<Entry> createEntries() {
        List<Entry> entries = new ArrayList<>();
        add(entries, "Plains", 10,
                "houses/plains_small_house_1",
                "houses/plains_small_house_2",
                "houses/plains_small_house_3",
                "houses/plains_small_house_4",
                "houses/plains_small_house_5",
                "houses/plains_small_house_6",
                "houses/plains_small_house_7",
                "houses/plains_medium_house_1",
                "houses/plains_big_house_1",
                "houses/plains_armorer_house_1",
                "houses/plains_butcher_shop_1",
                "houses/plains_cartographer_1",
                "houses/plains_fisher_cottage_1",
                "houses/plains_fletcher_house_1",
                "houses/plains_library_1",
                "houses/plains_masons_house_1",
                "houses/plains_shepherds_house_1",
                "houses/plains_stable_1",
                "houses/plains_tannery_1",
                "houses/plains_tool_smith_1",
                "houses/plains_weaponsmith_1");
        add(entries, "Desert", 11,
                "houses/desert_small_house_1",
                "houses/desert_small_house_2",
                "houses/desert_small_house_3",
                "houses/desert_small_house_4",
                "houses/desert_small_house_5",
                "houses/desert_small_house_6",
                "houses/desert_small_house_7",
                "houses/desert_small_house_8",
                "houses/desert_medium_house_1",
                "houses/desert_medium_house_2",
                "houses/desert_armorer_1",
                "houses/desert_butcher_shop_1",
                "houses/desert_cartographer_1",
                "houses/desert_farm_1",
                "houses/desert_farm_2",
                "houses/desert_fisher_1",
                "houses/desert_fletcher_house_1",
                "houses/desert_library_1",
                "houses/desert_mason_1",
                "houses/desert_shepherd_house_1",
                "houses/desert_tannery_1",
                "houses/desert_temple_1",
                "houses/desert_tool_smith_1",
                "houses/desert_weaponsmith_1");
        add(entries, "Savanna", 11,
                "houses/savanna_small_house_1",
                "houses/savanna_small_house_2",
                "houses/savanna_small_house_3",
                "houses/savanna_small_house_4",
                "houses/savanna_small_house_5",
                "houses/savanna_small_house_6",
                "houses/savanna_small_house_7",
                "houses/savanna_small_house_8",
                "houses/savanna_medium_house_1",
                "houses/savanna_medium_house_2",
                "houses/savanna_big_house_1",
                "houses/savanna_armorer_1",
                "houses/savanna_butchers_shop_1",
                "houses/savanna_cartographer_1",
                "houses/savanna_fisher_cottage_1",
                "houses/savanna_fletcher_house_1",
                "houses/savanna_library_1",
                "houses/savanna_mason_1",
                "houses/savanna_shepherd_1",
                "houses/savanna_tannery_1",
                "houses/savanna_temple_1",
                "houses/savanna_tool_smith_1",
                "houses/savanna_weaponsmith_1");
        add(entries, "Snowy", 12,
                "houses/snowy_small_house_1",
                "houses/snowy_small_house_2",
                "houses/snowy_small_house_3",
                "houses/snowy_small_house_4",
                "houses/snowy_small_house_5",
                "houses/snowy_small_house_6",
                "houses/snowy_small_house_7",
                "houses/snowy_small_house_8",
                "houses/snowy_medium_house_1",
                "houses/snowy_medium_house_2",
                "houses/snowy_medium_house_3",
                "houses/snowy_big_house_1",
                "houses/snowy_armorer_house_1",
                "houses/snowy_butchers_shop_1",
                "houses/snowy_cartographer_house_1",
                "houses/snowy_farm_1",
                "houses/snowy_farm_2",
                "houses/snowy_fisher_cottage_1",
                "houses/snowy_fletcher_house_1",
                "houses/snowy_library_1",
                "houses/snowy_masons_house_1",
                "houses/snowy_shepherds_house_1",
                "houses/snowy_tannery_1",
                "houses/snowy_temple_1",
                "houses/snowy_tool_smith_1",
                "houses/snowy_weaponsmith_1");
        add(entries, "Taiga", 12,
                "houses/taiga_small_house_1",
                "houses/taiga_small_house_2",
                "houses/taiga_small_house_3",
                "houses/taiga_small_house_4",
                "houses/taiga_small_house_5",
                "houses/taiga_medium_house_1",
                "houses/taiga_medium_house_2",
                "houses/taiga_medium_house_3",
                "houses/taiga_medium_house_4",
                "houses/taiga_large_farm_1",
                "houses/taiga_armorer_house_1",
                "houses/taiga_butcher_shop_1",
                "houses/taiga_cartographer_house_1",
                "houses/taiga_fisher_cottage_1",
                "houses/taiga_fletcher_house_1",
                "houses/taiga_library_1",
                "houses/taiga_masons_house_1",
                "houses/taiga_shepherds_house_1",
                "houses/taiga_tannery_1",
                "houses/taiga_temple_1",
                "houses/taiga_tool_smith_1",
                "houses/taiga_weaponsmith_1");
        entries.sort(Comparator.comparing(Entry::category).thenComparing(Entry::label));
        return List.copyOf(entries);
    }

    private static void add(List<Entry> entries, String category, int baseCost, String... paths) {
        String prefix = "village/" + category.toLowerCase(Locale.ROOT) + "/";
        for (String path : paths) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(prefix + path);
            entries.add(new Entry(id, category, labelFromPath(path, category), baseCost));
        }
    }

    private static String labelFromPath(String path, String category) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String categoryPrefix = category.toLowerCase(Locale.ROOT);
        String[] words = name.split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isBlank() || word.equals(categoryPrefix)) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            if (word.chars().allMatch(Character::isDigit)) {
                label.append(word);
            } else {
                label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return label.isEmpty() ? name : label.toString();
    }

    public record Entry(ResourceLocation id, String category, String label, int baseCost) {
        public String menuLabel() {
            return this.category + ": " + this.label;
        }
    }
}
