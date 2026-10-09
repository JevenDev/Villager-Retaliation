package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignScenarioPack.require;

import com.jvn.villagerretaliation.campaign.content.CampaignScenario;
import com.jvn.villagerretaliation.interaction.VillagerConversationService;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;

final class CampaignScenarioEvidence {
    private CampaignScenarioEvidence() { }

    static void spawn(CampaignScenarioFixture fixture, CampaignScenario.SpawnEvidence step) {
        var definition = fixture.content.definition().evidence().get(step.evidence());
        require(definition != null, "Scenario evidence is unavailable: " + step.evidence());
        require(!fixture.evidenceSources.containsKey(step.id()), "Scenario evidence source already exists: " + step.id());
        var source = fixture.helper.spawn(EntityType.INTERACTION, step.position());
        source.addTag(definition.entityTag().toString());
        fixture.evidenceSources.put(step.id(), source.getUUID());
    }

    static String investigate(CampaignScenarioFixture fixture, CampaignScenario.Investigate step) {
        var id = fixture.evidenceSources.get(step.source());
        var entity = id == null ? null : fixture.helper.getLevel().getEntity(id);
        require(entity instanceof Interaction && !entity.isRemoved(), "Scenario evidence source is unavailable: " + step.source());
        var player = fixture.player(step.player());
        if (step.approach()) {
            VillagerConversationService.endForPlayer(player, false);
            player.setPos(entity.getX() + 1, entity.getY(), entity.getZ());
        }
        var result = CampaignEvidenceService.investigate(player, entity);
        require(result.matched() == step.matched(), "Expected investigation matched=" + step.matched() + ", got " + result.matched());
        require(result.applied() == step.applied(), "Expected investigation applied=" + step.applied() + ", got " + result.applied() + ": " + result.diagnostic());
        boolean unavailable = !result.diagnostic().isBlank();
        require(unavailable == step.unavailable(), "Investigation availability differs: " + result.diagnostic());
        return result.diagnostic();
    }

    static void assertDiscovery(CampaignScenarioFixture fixture, CampaignScenario.AssertEvidence step) {
        var data = CampaignEvidenceSavedData.get(fixture.helper.getLevel());
        require(data.diagnostic().isBlank(), data.diagnostic());
        var instance = fixture.instance();
        var player = fixture.playerId(step.player());
        boolean discovered = data.discovered(fixture.scenario.campaign(), instance.instanceId(), instance.fingerprint(), player, step.evidence());
        require(discovered == step.discovered(), "Expected " + step.player() + " discovery of " + step.evidence() + "=" + step.discovered() + ", got " + discovered);
        if (step.receipts() >= 0) {
            long count = data.receipts().keySet().stream().filter(key -> key.campaign().equals(fixture.scenario.campaign())
                    && key.instance().equals(instance.instanceId()) && key.fingerprint().equals(instance.fingerprint())
                    && key.player().equals(player) && key.evidence().equals(step.evidence())).count();
            require(count == step.receipts(), "Expected " + step.receipts() + " personal investigation receipts, got " + count);
        }
    }
}
