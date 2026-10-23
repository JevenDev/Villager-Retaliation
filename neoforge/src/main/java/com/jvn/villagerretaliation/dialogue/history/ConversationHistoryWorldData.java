package com.jvn.villagerretaliation.dialogue.history;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.network.ConversationHistoryWorldPayload;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = VillagerRetaliation.MOD_ID)
public final class ConversationHistoryWorldData extends SavedData {
    private final UUID id;

    public ConversationHistoryWorldData() {
        this(UUID.randomUUID());
        setDirty();
    }

    private ConversationHistoryWorldData(UUID id) { this.id = id; }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.connection.hasChannel(ConversationHistoryWorldPayload.TYPE)) return;
        var data = player.server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ConversationHistoryWorldData::new, ConversationHistoryWorldData::load, DataFixTypes.LEVEL),
                "villagerretaliation_conversation_history_world");
        PacketDistributor.sendToPlayer(player, new ConversationHistoryWorldPayload(data.id));
    }

    private static ConversationHistoryWorldData load(CompoundTag tag, HolderLookup.Provider provider) {
        return tag.hasUUID("Id") ? new ConversationHistoryWorldData(tag.getUUID("Id")) : new ConversationHistoryWorldData();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putUUID("Id", id);
        return tag;
    }
}
