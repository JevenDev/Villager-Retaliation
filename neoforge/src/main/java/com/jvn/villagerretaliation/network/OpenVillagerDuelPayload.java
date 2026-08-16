package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.duel.DuelAvailabilityReason;
import com.jvn.villagerretaliation.duel.DuelKit;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenVillagerDuelPayload(
        int entityId,
        String villagerName,
        boolean available,
        DuelAvailabilityReason reason,
        int villagerWins,
        int villagerLosses,
        int consecutiveLosses,
        long cooldownTicks,
        int arenaRadius,
        int boundaryGraceTicks,
        int timeoutTicks,
        int cooldownDays,
        int playerBalance,
        int villagerBalance,
        String currencyName,
        boolean bringYourOwnAllowed,
        List<DuelKit.Summary> duelKits,
        String openingDialogue,
        String loadoutDialogue,
        String wagerDialogue,
        String confirmationDialogue,
        String startingDialogue) implements CustomPacketPayload {
    private static final int MAX_TEXT = 128;
    private static final int MAX_DESCRIPTION = 512;
    private static final int MAX_DIALOGUE_TEXT = 1024;
    private static final int MAX_KITS = 128;
    public OpenVillagerDuelPayload {
        villagerName = boundedUtf(villagerName, MAX_TEXT);
        currencyName = boundedUtf(currencyName, MAX_TEXT);
        duelKits = duelKits == null
                ? List.of()
                : List.copyOf(duelKits.stream()
                        .filter(Objects::nonNull)
                        .limit(MAX_KITS)
                        .map(kit -> new DuelKit.Summary(kit.id(),
                                boundedUtf(kit.name(), MAX_TEXT),
                                boundedUtf(kit.description(), MAX_DESCRIPTION)))
                        .toList());
        openingDialogue = boundedUtf(openingDialogue, MAX_DIALOGUE_TEXT);
        loadoutDialogue = boundedUtf(loadoutDialogue, MAX_DIALOGUE_TEXT);
        wagerDialogue = boundedUtf(wagerDialogue, MAX_DIALOGUE_TEXT);
        confirmationDialogue = boundedUtf(confirmationDialogue, MAX_DIALOGUE_TEXT);
        startingDialogue = boundedUtf(startingDialogue, MAX_DIALOGUE_TEXT);
    }

    public static final Type<OpenVillagerDuelPayload> TYPE = VillagerPayloads.type("open_villager_duel");
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenVillagerDuelPayload> STREAM_CODEC =
            VillagerPayloads.codec(OpenVillagerDuelPayload::encode, OpenVillagerDuelPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, OpenVillagerDuelPayload payload) {
        buffer.writeVarInt(payload.entityId());
        buffer.writeUtf(payload.villagerName(), MAX_TEXT);
        buffer.writeBoolean(payload.available());
        buffer.writeEnum(payload.reason());
        buffer.writeVarInt(payload.villagerWins());
        buffer.writeVarInt(payload.villagerLosses());
        buffer.writeVarInt(payload.consecutiveLosses());
        buffer.writeVarLong(payload.cooldownTicks());
        buffer.writeVarInt(payload.arenaRadius());
        buffer.writeVarInt(payload.boundaryGraceTicks());
        buffer.writeVarInt(payload.timeoutTicks());
        buffer.writeVarInt(payload.cooldownDays());
        buffer.writeVarInt(payload.playerBalance());
        buffer.writeVarInt(payload.villagerBalance());
        buffer.writeUtf(payload.currencyName(), MAX_TEXT);
        buffer.writeBoolean(payload.bringYourOwnAllowed());
        buffer.writeVarInt(payload.duelKits().size());
        for (DuelKit.Summary kit : payload.duelKits()) {
            buffer.writeResourceLocation(kit.id());
            buffer.writeUtf(kit.name(), MAX_TEXT);
            buffer.writeUtf(kit.description(), MAX_DESCRIPTION);
        }
        buffer.writeUtf(payload.openingDialogue(), MAX_DIALOGUE_TEXT);
        buffer.writeUtf(payload.loadoutDialogue(), MAX_DIALOGUE_TEXT);
        buffer.writeUtf(payload.wagerDialogue(), MAX_DIALOGUE_TEXT);
        buffer.writeUtf(payload.confirmationDialogue(), MAX_DIALOGUE_TEXT);
        buffer.writeUtf(payload.startingDialogue(), MAX_DIALOGUE_TEXT);
    }

    private static OpenVillagerDuelPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenVillagerDuelPayload(buffer.readVarInt(), buffer.readUtf(MAX_TEXT), buffer.readBoolean(),
                buffer.readEnum(DuelAvailabilityReason.class), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarLong(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(MAX_TEXT), buffer.readBoolean(), readKits(buffer),
                buffer.readUtf(MAX_DIALOGUE_TEXT), buffer.readUtf(MAX_DIALOGUE_TEXT),
                buffer.readUtf(MAX_DIALOGUE_TEXT), buffer.readUtf(MAX_DIALOGUE_TEXT),
                buffer.readUtf(MAX_DIALOGUE_TEXT));
    }

    private static List<DuelKit.Summary> readKits(RegistryFriendlyByteBuf buffer) {
        int size = VillagerPayloads.readCollectionSize(buffer, MAX_KITS, "duel kits");
        java.util.ArrayList<DuelKit.Summary> kits = new java.util.ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            kits.add(new DuelKit.Summary(
                    buffer.readResourceLocation(),
                    buffer.readUtf(MAX_TEXT),
                    buffer.readUtf(MAX_DESCRIPTION)));
        }
        return List.copyOf(kits);
    }

    private static String boundedUtf(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        int end = maxLength;
        if (Character.isHighSurrogate(value.charAt(end - 1))
                && end < value.length()
                && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    public int maximumStake() {
        return Math.min(this.playerBalance, this.villagerBalance);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
