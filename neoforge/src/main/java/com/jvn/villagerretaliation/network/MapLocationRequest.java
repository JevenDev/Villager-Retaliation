package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.exploration.MapLocation;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MapLocationRequest(Action action, MapLocation location, UUID selected, Audience audience, UUID recipient)
        implements CustomPacketPayload {
    public enum Action { VIEW, SAVE, DELETE, SHARE }
    public enum Audience { PARTY, ONLINE, PLAYER }
    public static final Type<MapLocationRequest> TYPE = VillagerPayloads.type("map_location_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, MapLocationRequest> STREAM_CODEC =
            VillagerPayloads.codec(MapLocationRequest::write, MapLocationRequest::read);

    public MapLocationRequest {
        if (action == null || audience == null || action == Action.SAVE && location == null
                || action == Action.DELETE && selected == null
                || action == Action.SHARE && audience == Audience.PLAYER && recipient == null)
            throw new IllegalArgumentException("Invalid map location request");
    }

    private static void write(RegistryFriendlyByteBuf buffer, MapLocationRequest value) {
        buffer.writeEnum(value.action); buffer.writeBoolean(value.location != null);
        if (value.location != null) value.location.write(buffer);
        VillageRelationsRequestPayload.writeId(buffer, value.selected);
        buffer.writeEnum(value.audience); VillageRelationsRequestPayload.writeId(buffer, value.recipient);
    }

    private static MapLocationRequest read(RegistryFriendlyByteBuf buffer) {
        return new MapLocationRequest(buffer.readEnum(Action.class), buffer.readBoolean() ? MapLocation.read(buffer) : null,
                VillageRelationsRequestPayload.readId(buffer), buffer.readEnum(Audience.class),
                VillageRelationsRequestPayload.readId(buffer));
    }

    @Override public Type<MapLocationRequest> type() { return TYPE; }
}
