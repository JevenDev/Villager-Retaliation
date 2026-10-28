package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.exploration.MapLocation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MapLocationsPayload(List<MapLocation> locations, java.util.UUID acknowledgedSave) implements CustomPacketPayload {
    public static final Type<MapLocationsPayload> TYPE = VillagerPayloads.type("map_locations");
    public static final StreamCodec<RegistryFriendlyByteBuf, MapLocationsPayload> STREAM_CODEC =
            VillagerPayloads.codec(MapLocationsPayload::write, MapLocationsPayload::read);

    public MapLocationsPayload(List<MapLocation> locations) { this(locations, null); }

    public MapLocationsPayload {
        locations = List.copyOf(locations);
        if (locations.size() > MapLocation.MAX_LOCATIONS) throw new IllegalArgumentException("Too many map locations");
    }

    private static void write(RegistryFriendlyByteBuf buffer, MapLocationsPayload value) {
        buffer.writeVarInt(value.locations.size());
        value.locations.forEach(location -> location.write(buffer));
        VillageRelationsRequestPayload.writeId(buffer, value.acknowledgedSave);
    }

    private static MapLocationsPayload read(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MapLocation.MAX_LOCATIONS, "map locations");
        var locations = new ArrayList<MapLocation>(size);
        for (int i = 0; i < size; i++) locations.add(MapLocation.read(buffer));
        return new MapLocationsPayload(locations, VillageRelationsRequestPayload.readId(buffer));
    }

    @Override public Type<MapLocationsPayload> type() { return TYPE; }
}
