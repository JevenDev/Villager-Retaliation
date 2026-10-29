package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.allegiance.VillageLifecycleState;
import com.jvn.villagerretaliation.allegiance.VillageOpinion;
import com.jvn.villagerretaliation.reputation.VillagerReputationLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VillageOverviewPayload(
        BlockPos anchor,
        ResourceLocation dimension,
        boolean openScreen,
        String name,
        VillageLifecycleState lifecycle,
        int adults,
        int children,
        int present,
        int beds,
        int freeBeds,
        int workstations,
        int trusted,
        int required,
        List<Resident> residents,
        VillageOpinion opinion) implements CustomPacketPayload {
    public static final int MAX_RESIDENTS = 128;
    public static final int MAX_RESIDENT_NAME_LENGTH = 64;
    public static final Type<VillageOverviewPayload> TYPE = VillagerPayloads.type("village_overview");
    public static final StreamCodec<RegistryFriendlyByteBuf, VillageOverviewPayload> STREAM_CODEC =
            VillagerPayloads.codec(VillageOverviewPayload::encode, VillageOverviewPayload::decode);

    public VillageOverviewPayload {
        anchor = anchor.immutable();
        residents = List.copyOf(residents.stream().limit(MAX_RESIDENTS).toList());
    }

    public boolean found() {
        return !this.name.isEmpty();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, VillageOverviewPayload payload) {
        buffer.writeBlockPos(payload.anchor());
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeBoolean(payload.openScreen());
        buffer.writeUtf(payload.name(), 32);
        buffer.writeEnum(payload.lifecycle());
        buffer.writeVarInt(payload.adults());
        buffer.writeVarInt(payload.children());
        buffer.writeVarInt(payload.present());
        buffer.writeVarInt(payload.beds());
        buffer.writeVarInt(payload.freeBeds());
        buffer.writeVarInt(payload.workstations());
        buffer.writeVarInt(payload.trusted());
        buffer.writeVarInt(payload.required());
        buffer.writeDouble(payload.opinion().score());
        buffer.writeEnum(payload.opinion().level());
        buffer.writeVarInt(payload.residents().size());
        for (Resident resident : payload.residents()) {
            buffer.writeUtf(resident.name(), MAX_RESIDENT_NAME_LENGTH);
            buffer.writeResourceLocation(resident.profession());
            buffer.writeBoolean(resident.baby());
            buffer.writeEnum(resident.presence());
            buffer.writeEnum(resident.reputation());
            buffer.writeBlockPos(resident.position());
        }
    }

    private static VillageOverviewPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos anchor = buffer.readBlockPos();
        ResourceLocation dimension = buffer.readResourceLocation();
        boolean openScreen = buffer.readBoolean();
        String name = buffer.readUtf(32);
        VillageLifecycleState lifecycle = buffer.readEnum(VillageLifecycleState.class);
        int adults = buffer.readVarInt();
        int children = buffer.readVarInt();
        int present = buffer.readVarInt();
        int beds = buffer.readVarInt();
        int freeBeds = buffer.readVarInt();
        int workstations = buffer.readVarInt();
        int trusted = buffer.readVarInt();
        int required = buffer.readVarInt();
        var opinion = new VillageOpinion(
                buffer.readDouble(), buffer.readEnum(VillagerReputationLevel.class));
        int count = VillagerPayloads.readCollectionSize(buffer, MAX_RESIDENTS, "village residents");
        List<Resident> residents = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            residents.add(new Resident(buffer.readUtf(MAX_RESIDENT_NAME_LENGTH), buffer.readResourceLocation(),
                    buffer.readBoolean(), buffer.readEnum(Presence.class),
                    buffer.readEnum(VillagerReputationLevel.class), buffer.readBlockPos()));
        }
        return new VillageOverviewPayload(anchor, dimension, openScreen, name, lifecycle, adults, children,
                present, beds, freeBeds, workstations, trusted, required, residents, opinion);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Presence {
        HERE,
        AWAY,
        UNLOADED
    }

    public record Resident(String name, ResourceLocation profession, boolean baby,
                           Presence presence, VillagerReputationLevel reputation, BlockPos position) {
        public Resident {
            if (name.length() > MAX_RESIDENT_NAME_LENGTH) {
                int end = MAX_RESIDENT_NAME_LENGTH;
                if (Character.isHighSurrogate(name.charAt(end - 1))) {
                    end--;
                }
                name = name.substring(0, end);
            }
            position = position.immutable();
        }
    }
}
