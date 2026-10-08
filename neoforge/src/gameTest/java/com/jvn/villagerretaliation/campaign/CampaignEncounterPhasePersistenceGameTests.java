package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.quest.QuestScopeKey;
import com.jvn.villagerretaliation.quest.VillagerQuestFacts;
import com.jvn.villagerretaliation.scene.encounter.EncounterService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignEncounterPhasePersistenceGameTests {
    private CampaignEncounterPhasePersistenceGameTests() { }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_persistence")
    public static void failedSceneSavePreventsPhaseFactsAndTransitions(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.base.block();
            fixture.defeat();
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "phase fact waits for its durable identity");
            helper.assertTrue(fixture.base.scene().stepRecords().get("wait").chosenTransition().isBlank(), "failed fact prevents its transition");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "failed phase prevents its reward");
            fixture.base.restore();
            fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "cleared", "writable storage resumes the phase");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_encounter_phase_persistence")
    public static void failedNativeFactSavePreventsLaterPhaseEffects(GameTestHelper helper) throws IOException {
        try (var fixture = new Fixture(helper)) {
            fixture.blockFacts();
            fixture.defeat();
            helper.assertFalse(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").isPresent(), "failed native save cannot publish a fact");
            helper.assertTrue(fixture.base.scene().stepRecords().get("wait").chosenTransition().isBlank(), "later transition waits for the fact save");
            helper.assertValueEqual(fixture.player().getInventory().countItem(Items.EMERALD), 0, "later reward waits for the fact save");
            fixture.restoreFacts(); fixture.base.reload(); fixture.refresh();
            helper.assertValueEqual(fixture.facts().variable(QuestScopeKey.WORLD, "phase_state").orElse(""), "cleared", "fact resumes after storage recovers");
        }
        helper.succeed();
    }

    static final class Fixture implements AutoCloseable {
        static final String FACTS = "villagerretaliation_quest_facts";
        static final String TEMPLATE = """
                    {"schema":"villagerretaliation:encounter/v1","id":"campaign_fixture:persistent_encounter",
                     "spawn_mode":"fixed","spawn_radius":0,"members":[{"entity":"minecraft:cow","count":2}],
                     "phases":[{"id":"turning_point","trigger":{"type":"remaining_percentage","percentage":50},"actions":[
                       {"id":"fact","type":"fact","scope":"world","key":"phase_state","value":"cleared"},
                       {"id":"branch","type":"transition","target":"done"}]}],
                     "rewards":{"phases":[{"id":"payment","phase":"turning_point","item":"minecraft:emerald","count":5}]}}
                    """;
        final CampaignEncounterPersistenceGameTests.Fixture base;
        final GameTestHelper helper;
        private final VillagerQuestFacts prior;
        private final Path backup;
        private boolean blocked;
        private Path blockedBackup;

        Fixture(GameTestHelper helper) throws IOException { this(helper, TEMPLATE); }

        Fixture(GameTestHelper helper, String template) throws IOException {
            this.helper = helper;
            base = new CampaignEncounterPersistenceGameTests.Fixture(helper, template);
            prior = VillagerQuestFacts.get(helper.getLevel());
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            if (Files.exists(path())) {
                backup = Files.createTempFile(base.base.base.disk.root, "phase-facts-", ".dat");
                Files.delete(backup);
                Files.move(path(), backup);
            } else backup = null;
            helper.getLevel().getDataStorage().set(FACTS, new VillagerQuestFacts());
            base.run();
        }

        VillagerQuestFacts facts() { return VillagerQuestFacts.get(helper.getLevel()); }
        Path path() { return base.path().resolveSibling(FACTS + ".dat"); }
        net.minecraft.server.level.ServerPlayer player() { return base.base.base.server.getPlayerList().getPlayer(base.encounter().participants().iterator().next()); }
        void defeat() {
            var enemy = (LivingEntity) helper.getLevel().getEntity(base.encounter().spawned().stream().filter(id -> !base.encounter().defeated().contains(id)).findFirst().orElseThrow());
            EncounterService.onDeath(enemy, base.data());
        }
        EncounterService.Result refresh() { return EncounterService.refresh(base.base.base.server, base.data(), base.encounter()); }
        void blockFacts() throws IOException {
            if (Files.exists(path())) {
                blockedBackup = Files.createTempFile(base.base.base.disk.root, "blocked-phase-facts-", ".dat");
                Files.delete(blockedBackup); Files.move(path(), blockedBackup);
            }
            Files.createDirectories(path());
            Files.writeString(path().resolve("blocked"), "phase fact storage failure");
            blocked = true;
        }
        void restoreFacts() throws IOException {
            if (!blocked) return;
            Files.delete(path().resolve("blocked")); Files.delete(path());
            if (blockedBackup != null) { Files.move(blockedBackup, path()); blockedBackup = null; }
            blocked = false;
        }
        @Override public void close() throws IOException {
            restoreFacts();
            Files.deleteIfExists(path());
            if (backup != null) Files.move(backup, path());
            helper.getLevel().getDataStorage().set(FACTS, prior);
            base.close();
        }
    }
}
