package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.campaign.CampaignBindingQueries;
import com.jvn.villagerretaliation.campaign.CampaignCharacterBinding;
import com.jvn.villagerretaliation.campaign.CampaignCharacterReplacements;
import com.jvn.villagerretaliation.campaign.CampaignCharacterSavedData;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import java.io.IOException;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.Villager;

final class ConversationCharacters {
    private ConversationCharacters() { }

    static ConversationSession reconcile(MinecraftServer server, ConversationSavedData data, ConversationSession current) throws IOException {
        if (current == null || !current.active()) return current;
        ConversationService.require(data.issue(current.identity.id()).isBlank(), data.issue(current.identity.id()));
        ConversationService.definition(server, current);
        var candidate = new ConversationSession(current);
        var changed = new HashSet<UUID>();
        for (var prior : current.speakers.values()) {
            var resolved = CampaignBindingQueries.character(server, current.identity.campaign(), prior.role());
            if (resolved.binding() == null || prior.entity().equals(resolved.binding().entityId())) continue;
            ConversationService.require(resolved.available(), resolved.diagnostic().isBlank()
                    ? "Load the repaired conversation character before resuming." : resolved.diagnostic());
            var target = resolved.entity();
            String authorization = CampaignCharacterReplacements.authorization(server, current.identity.campaign(), prior.role(), prior.entity(), target.getUUID());
            ConversationService.require(!authorization.isBlank(), "A saved conversation speaker changed without campaign authorization. Restore its binding.");
            ConversationService.require(!VillagerConversationService.isConversing(target), "Wait for the repaired character's current interruption to finish.");
            var next = new ConversationSession.Speaker(prior.role(), target.getUUID(), target.level().dimension().location(), target.getName().getString());
            candidate.speakers.put(prior.role(), next);
            candidate.speakerHistory.add(new ConversationSpeakerHistory.Entry(prior, next, authorization, current.revision + 1, server.overworld().getGameTime()));
            changed.add(prior.entity());
        }
        if (changed.isEmpty()) return current;
        if (candidate.confirmation != null && changed.contains(candidate.confirmation.consent().providerId())) ConversationConfirmations.discard(candidate);
        if (candidate.nestedDecision != null && candidate.nestedDecision.state() == ConversationNestedDecision.State.PENDING
                && changed.contains(candidate.nestedDecision.confirmation().consent().providerId())) {
            candidate.phase = candidate.nestedDecision.resumePhase();
            candidate.nestedDecision = null;
        }
        candidate.diagnostic = "";
        candidate.revision++;
        data.write(server.overworld(), current.revision, candidate);
        return candidate;
    }

    static UUID active(MinecraftServer server, ConversationSavedData data, UUID character) {
        UUID direct = data.activeCharacter(character);
        if (direct != null) return direct;
        var characters = CampaignCharacterSavedData.get(server.overworld());
        var binding = characters.forEntity(character);
        if (binding == null) return null;
        for (var session : data.activeSessions()) {
            if (!binding.campaign().equals(session.identity.campaign()) || !binding.instance().equals(session.identity.campaignInstance())) continue;
            for (var speaker : session.speakers.values()) {
                if (characters.authorizes(new CampaignCharacterBinding.Key(binding.campaign(), speaker.role()), speaker.entity(), character))
                    return session.identity.id();
            }
        }
        return null;
    }

    static Villager primary(net.minecraft.server.level.ServerPlayer player, ConversationSession session) {
        var speaker = session.speakers.get(session.identity.characterRole());
        return speaker != null && player.serverLevel().getEntity(speaker.entity()) instanceof Villager villager ? villager : null;
    }
}
