package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.skill.player.PlayerSocialAward;
import com.jvn.villagerretaliation.skill.player.PlayerSocialProgressionSavedData;
import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
import com.jvn.villagerretaliation.skill.player.SocialProgressionResources;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

final class ConversationSocialProgression {
    private ConversationSocialProgression() { }

    static SocialCheckBreakdown capture(ServerPlayer player, ConversationSavedData conversations, ConversationDefinition.Check check) {
        if (check.social() == null) return null;
        ConversationService.require(!conversations.hasPendingSocialAwards(player.getUUID()), "Earlier social skill experience is waiting to save.");
        var definition = SocialProgressionResources.get(player.server, check.social().progression()).orElse(null);
        ConversationService.require(definition != null, "The authored social progression is unavailable: " + check.social().progression());
        var progress = PlayerSocialProgressionSavedData.get(player.serverLevel());
        ConversationService.require(progress.diagnostic().isBlank(), progress.diagnostic());
        return SocialCheckBreakdown.capture(definition, check.social().skill(),
                progress.experience(player.getUUID(), check.social().skill()), check.social().experience());
    }

    static void retry(MinecraftServer server, ConversationSavedData data) {
        for (var id : data.socialAwardBatch()) deliverOne(server, data, id);
    }

    static void deliverOne(MinecraftServer server, ConversationSavedData data, UUID id) {
        if (!data.issue(id).isBlank()) return;
        var session = data.session(id);
        if (session == null) return;
        var check = session.checks.values().stream().filter(value -> value.social() != null && !session.socialAwards.contains(value.id())).findFirst().orElse(null);
        if (check == null) { data.socialIssue(id, ""); return; }
        try {
            PlayerSocialProgressionSavedData.get(server.overworld()).grant(new PlayerSocialAward(id, check.id(), check.request(), check.player(), check.social()));
            session.socialAwards.add(check.id());
            data.write(server.overworld(), session.revision++, session);
            data.socialIssue(id, "");
        } catch (IOException | IllegalArgumentException exception) {
            String issue = "Social skill experience is waiting for recovery: " + exception.getMessage();
            if (data.socialIssue(id, issue)) com.mojang.logging.LogUtils.getLogger().warn("Conversation {} cannot deliver social experience", id, exception);
        }
    }
}
