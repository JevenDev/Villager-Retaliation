package com.jvn.villagerretaliation.client.exploration;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

final class MapSearchModel {
    static List<MapDestination> filter(List<MapDestination> destinations, String query, ResourceLocation dimension,
                                      MapDestination.Kind kind, boolean favoritesOnly, Predicate<MapDestination> favorite,
                                      boolean nearest, ResourceLocation currentDimension, double x, double z) {
        String text = query.strip().toLowerCase(Locale.ROOT);
        Comparator<MapDestination> name = Comparator.comparing(MapDestination::name, String.CASE_INSENSITIVE_ORDER).thenComparing(MapDestination::key);
        Comparator<MapDestination> order = nearest ? Comparator.comparingDouble((MapDestination destination) ->
                destination.dimension().equals(currentDimension) ? destination.distance(x, z) : Double.POSITIVE_INFINITY).thenComparing(name) : name;
        return destinations.stream().filter(destination -> dimension == null || dimension.equals(destination.dimension()))
                .filter(destination -> kind == null || kind == destination.kind())
                .filter(destination -> !favoritesOnly || favorite.test(destination))
                .filter(destination -> destination.name().toLowerCase(Locale.ROOT).contains(text)
                        || destination.label().toLowerCase(Locale.ROOT).contains(text))
                .sorted(order).toList();
    }
}
