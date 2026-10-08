package com.jvn.villagerretaliation.campaign;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterActorPersistenceGameTests {
    private CampaignEncounterActorPersistenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_actors")
    public static void actorIdentitiesAreSavedBeforeJoiningTheWorld(GameTestHelper helper) throws IOException {
        try (var fixture = new CampaignEncounterPersistenceGameTests.Fixture(helper)) {
            var evidence = new ArrayList<Boolean>();
            Consumer<EntityJoinLevelEvent> listener = event -> {
                var entity = event.getEntity();
                var owner = entity.getPersistentData();
                String key = owner.hasUUID("VillagerRetaliationEncounter") ? "VillagerRetaliationEncounter"
                        : owner.hasUUID("VillagerRetaliationEncounterAlly") ? "VillagerRetaliationEncounterAlly" : "";
                if (key.isEmpty()) return;
                var encounter = fixture.disk().encounter(owner.getUUID(key)).orElseThrow();
                var receipts = encounter.save().getCompound("ActorSpawns").getList("Records", Tag.TAG_COMPOUND);
                evidence.add(receipts.stream().anyMatch(raw -> {
                    var receipt = (net.minecraft.nbt.CompoundTag) raw;
                    return receipt.hasUUID("Entity") && receipt.getUUID("Entity").equals(entity.getUUID());
                }));
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try { fixture.run(); }
            finally { NeoForge.EVENT_BUS.unregister(listener); }
            helper.assertValueEqual(evidence, List.of(true, true), "mob and ally identities must be durable before either actor joins");
        }
        helper.succeed();
    }
}
