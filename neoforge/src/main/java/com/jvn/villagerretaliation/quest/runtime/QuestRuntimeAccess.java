package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.content.QuestContentAccess;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface QuestRuntimeAccess {
    ResourceLocation id();
    QuestModelStore<? extends QuestProgressModel> progress(ServerLevel level);
    QuestContentAccess content(MinecraftServer server);
    boolean fail(ServerPlayer player, ResourceLocation quest, UUID expectedRun);

    default String executionIssue(ServerLevel level, UUID player, ResourceLocation quest, QuestProgressModel progress) {
        var definition = content(level.getServer()).quest(quest).orElse(null);
        return definition == null ? "Quest content is unavailable."
                : definition.campaign() != null ? "Campaign execution requires its campaign runtime adapter." : "";
    }

    default Optional<Entity> campaignActor(MinecraftServer server, ResourceLocation quest, String role) {
        return Optional.empty();
    }
}
