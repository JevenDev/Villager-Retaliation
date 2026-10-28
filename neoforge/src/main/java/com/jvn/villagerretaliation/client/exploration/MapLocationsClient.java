package com.jvn.villagerretaliation.client.exploration;

import com.jvn.villagerretaliation.exploration.MapLocation;
import com.jvn.villagerretaliation.network.MapLocationRequest;
import com.jvn.villagerretaliation.network.MapLocationsPayload;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "villagerretaliation", value = Dist.CLIENT)
public final class MapLocationsClient {
    private static List<MapLocation> locations = List.of();
    private static final EnumMap<MapLocation.Icon, ItemStack> icons = new EnumMap<>(MapLocation.Icon.class);

    public static void accept(MapLocationsPayload payload) {
        locations = payload.locations();
        if (Minecraft.getInstance().screen instanceof MapLocationEditScreen editor) editor.accept(payload.acknowledgedSave());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { locations = List.of(); icons.clear(); }
    public static List<MapLocation> all() { return locations; }
    public static void refresh() {
        PacketDistributor.sendToServer(new MapLocationRequest(MapLocationRequest.Action.VIEW, null, null, MapLocationRequest.Audience.PARTY, null));
    }

    public static Component text(String key, Object... arguments) {
        return Component.translatable("map.villagerretaliation.location." + key, arguments);
    }

    public static ItemStack icon(MapLocation.Icon icon) {
        return icons.computeIfAbsent(icon, value -> value == MapLocation.Icon.OMINOUS_BANNER
                ? Raid.getLeaderBannerInstance(Minecraft.getInstance().level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN))
                : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace(value.name().toLowerCase(Locale.ROOT)))));
    }

}
