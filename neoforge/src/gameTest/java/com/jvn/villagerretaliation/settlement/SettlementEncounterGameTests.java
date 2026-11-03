package com.jvn.villagerretaliation.settlement;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("journey_encounters")
@PrefixGameTestTemplate(false)
public final class SettlementEncounterGameTests {
    private static SettlementEncounterDefinition definition(String json) {
        return SettlementEncounterDefinition.parse(ResourceLocation.parse("test:encounter"), JsonParser.parseString(json).getAsJsonObject());
    }
    private static SettlementEncounterDefinition attack() {
        return definition("""
                {"type":"attack","chance":1,"duration_ticks":200,"delay_ticks":20,"recovery_chance":1,
                 "attackers":[{"entity":"minecraft:pillager","health":20,"damage":3}]}
                """);
    }
    @GameTest(template = "empty")
    public static void deathTransfersCargoAndPersonalItemsWithoutLooseDrops(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        var villager = SettlementJourneyEncounters.context(level, request.journey);
        var item = new ItemStack(Items.PAPER);
        var data = new CompoundTag(); data.putString(com.jvn.villagerretaliation.quest.item.QuestItems.IDENTITY, "test:protected");
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        villager.getInventory().setItem(0, item); villager.setHealth(1);
        SettlementCourierService.capture(request.journey, villager);
        var definition = definition("""
                {"type":"attack","recovery_chance":1,"attackers":[{"entity":"minecraft:pillager","health":200,"damage":128}]}
                """);
        SettlementJourneyEncounters.start(level, request, definition, List.of(request), level.getGameTime());
        SettlementJourneyEncounters.round(level, request, List.of(request));
        helper.assertValueEqual(request.journey.presence, SettlementCourierJourney.Presence.DEAD, "lethal combat records one death");
        helper.assertTrue(request.offer.empty(), "cargo leaves courier ownership");
        helper.assertTrue(request.journey.recovery.isEmpty(), "legacy loose-drop path is not also used");
        var remains = request.journey.remains.getFirst();
        helper.assertTrue(remains.items.stream().anyMatch(stack -> ItemStack.matches(stack, item)), "quest item is kept in recovery");
        helper.assertTrue(remains.items.stream().anyMatch(stack -> stack.is(Items.BREAD) && stack.getCount() == 20), "cargo kept in recovery");
        var saved = request.save(level.registryAccess());
        helper.assertValueEqual(SettlementFoodRequest.load(saved, level.registryAccess()).save(level.registryAccess()), saved, "dead journey and items reload");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void armorChangesBackgroundDamage(GameTestHelper helper) {
        var level = helper.getLevel();
        var bare = EntityType.VILLAGER.create(level); var armored = EntityType.VILLAGER.create(level);
        armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        SettlementJourneyEncounters.equipment(armored);
        var enemy = EntityType.PILLAGER.create(level);
        bare.hurt(level.damageSources().mobAttack(enemy), 6);
        armored.hurt(level.damageSources().mobAttack(enemy), 6);
        helper.assertTrue(armored.getHealth() > bare.getHealth(), "armor is applied to detached participant damage");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void loadedDamageAndRetirementKeepTheSameFight(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        var state = request.journey.encounter; state.visible = true;
        var actor = (net.minecraft.world.entity.Mob) EntityType.loadEntityRecursive(state.attackers.getFirst().copy(), level, entity -> entity);
        actor.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.assertTrue(level.addFreshEntity(actor), "current attacker is represented");
        actor.setHealth(7);
        SettlementJourneyEncounters.captureAttackers(level, state);
        helper.assertValueEqual(state.attackers.getFirst().getFloat("Health"), 7F, "player intervention is captured");
        SettlementJourneyEncounters.retiring(level, request);
        var saved = state.save(); var restored = SettlementEncounterState.load(saved);
        helper.assertValueEqual(restored.id, state.id, "encounter identity survives retirement");
        helper.assertValueEqual(restored.attackers.getFirst().getFloat("Health"), 7F, "background resumes injured attacker");
        SettlementJourneyEncounters.cleanup(level, state);
        helper.assertTrue(actor.isRemoved(), "retired attacker is removed");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void placementRejectsObstructionAndKeepsTheChosenLocation(GameTestHelper helper) {
        var level = helper.getLevel();
        var body = com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes.FALLEN_VILLAGER.get().create(level);
        var position = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlock(position.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        for (int height = 1; height < 64; height++) level.setBlock(position.above(height), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(SettlementEncounterRecovery.safe(level, position, body), "uncovered supported position is valid: ticking="
                + level.isPositionEntityTicking(position) + " entities=" + level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(position).toLong())
                + " height=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, position.getX(), position.getZ())
                + " y=" + position.getY() + " collision=" + level.noCollision(body));
        level.setBlock(position, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(SettlementEncounterRecovery.safe(level, position, body), "obstructed position is rejected");
        var remains = new SettlementEncounterRemains(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), position, new CompoundTag());
        remains.position = position; remains.items.add(new ItemStack(Items.EMERALD));
        var restored = SettlementEncounterRemains.load(remains.save(level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.position, position, "obstruction does not reroll location");
        helper.assertTrue(restored.pending(), "items survive unavailable display");
        var distant = new BlockPos(20000000, 80, 20000000);
        helper.assertTrue(SettlementEncounterRecovery.safePosition(level, distant, body, 1, 24) == null, "unloaded terrain is never forced");
        helper.assertFalse(level.hasChunkAt(distant), "search did not load distant terrain");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void claimsUsePlayerReceiptsAndOverflowIsReservedOnce(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        request.journey.position = helper.absolutePos(new BlockPos(2, 2, 2));
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        var remains = SettlementEncounterRecovery.remains(request, request.journey.encounter);
        remains.position = helper.absolutePos(new BlockPos(2, 2, 2));
        remains.items.add(new ItemStack(Items.DIAMOND, 64));
        var player = helper.makeMockServerPlayerInLevel();
        player.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(remains.position));
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        var body = com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes.FALLEN_VILLAGER.get().create(level);
        body.setUUID(remains.id); body.moveTo(player.position()); body.getPersistentData().putUUID(SettlementEncounterRecovery.BODY, request.id());
        remains.claimant = player.getUUID();
        var aid = SettlementSavedData.get(level).aid(); aid.durableRevision = aid.revision();
        SettlementEncounterRecovery.claim(player, body);
        helper.assertTrue(remains.claimed, "claim has a player receipt");
        helper.assertValueEqual(remains.overflow.size(), 1, "overflow stays reserved until native persistence");
        SettlementEncounterRecovery.claim(player, body);
        helper.assertValueEqual(remains.overflow.size(), 1, "repeated interaction cannot duplicate overflow");
        var restored = SettlementEncounterRemains.load(remains.save(level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.overflow.getFirst().id, remains.overflow.getFirst().id, "overflow identity survives reload");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void builtInExamplesLoadAndFrequencyClocksDoNotReroll(GameTestHelper helper) {
        var level = helper.getLevel(); var definitions = SettlementEncounterResources.get(level.getServer());
        helper.assertTrue(definitions.stream().anyMatch(value -> value.id().getPath().equals("wildlife")), "wildlife example loads");
        helper.assertTrue(definitions.stream().anyMatch(value -> value.id().getPath().equals("pillagers")), "pillager example loads");
        helper.assertTrue(definitions.stream().anyMatch(value -> value.id().getPath().equals("night_rest")), "rest example loads");
        var request = fixture(helper); request.journey.encounterDistance = 100;
        long now = (level.getGameTime() / 20 + 1) * 20;
        SettlementJourneyEncounters.select(level, request, now);
        long checks = request.journey.encounterChecks;
        SettlementJourneyEncounters.select(level, request, now);
        helper.assertValueEqual(request.journey.encounterChecks, checks, "same progress never rerolls");
        var restored = SettlementCourierJourney.load(request.journey.save(level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.encounterClocks, request.journey.encounterClocks, "selection and cooldown clocks survive reload");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void unloadCapturesTheFinalHitAndRemovesOwnedProjectiles(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        var state = request.journey.encounter; state.visible = true;
        var actor = (net.minecraft.world.entity.Mob) EntityType.loadEntityRecursive(state.attackers.getFirst().copy(), level, entity -> entity);
        actor.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.assertTrue(level.addFreshEntity(actor), "attacker joins before unloading");
        var arrow = EntityType.ARROW.create(level); arrow.setOwner(actor); arrow.moveTo(actor.position());
        helper.assertTrue(level.addFreshEntity(arrow), "owned projectile joins");
        actor.setHealth(5);
        actor.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertValueEqual(state.attackers.getFirst().getFloat("Health"), 5F, "last hit is captured by unload event");
        helper.assertValueEqual(state.stage, SettlementEncounterState.Stage.RETIRING, "unload retires the representation");
        SettlementJourneyEncounters.cleanup(level, state);
        helper.assertTrue(arrow.isRemoved(), "owned projectile cannot outlive the encounter representation");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void personalLossProtectsQuestItemsAndRunsOnce(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        var villager = SettlementJourneyEncounters.context(level, request.journey);
        var letter = new ItemStack(Items.PAPER, 20);
        var custom = new CompoundTag(); custom.putString(com.jvn.villagerretaliation.quest.item.QuestItems.IDENTITY, "test:letter");
        letter.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        villager.getInventory().setItem(0, new ItemStack(Items.DIRT, 20)); villager.getInventory().setItem(1, letter);
        SettlementCourierService.capture(request.journey, villager);
        var definition = definition("{\"type\":\"rest\",\"outcomes\":[{\"type\":\"lose_items\",\"fraction\":0.5}]}");
        SettlementJourneyEncounters.start(level, request, definition, List.of(request), level.getGameTime());
        SettlementJourneyEncounters.resolve(level, request, List.of(request), level.getGameTime());
        SettlementJourneyEncounters.resolve(level, request, List.of(request), level.getGameTime());
        var restored = SettlementJourneyEncounters.context(level, request.journey);
        helper.assertValueEqual(restored.getInventory().getItem(0).getCount(), 10, "ordinary items are lost once");
        helper.assertTrue(ItemStack.matches(restored.getInventory().getItem(1), letter), "protected quest stack is unchanged");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void legacyJourneyAndCargoLoadWithoutEncounterFields(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel(); var saved = request.save(level.registryAccess());
        saved.remove("LostNutrition"); var journey = saved.getCompound("Journey");
        for (String key : List.of("EncounterDistance", "EncounterChecks", "EncounterCooldown", "EncounterClocks", "Encounter", "Remains", "Downed"))
            journey.remove(key);
        var restored = SettlementFoodRequest.load(saved, level.registryAccess());
        helper.assertTrue(restored.journey.encounter == null && restored.journey.remains.isEmpty(), "legacy save has no invented encounter");
        helper.assertValueEqual(restored.offer.remaining().getFirst().item().getCount(), 20, "legacy cargo retained");
        helper.assertValueEqual(restored.journey.resident, request.journey.resident, "legacy resident identity retained");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void survivorsReturnWhenLeaderFalls(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel(); var data = SettlementSavedData.get(level);
        var villager = EntityType.VILLAGER.create(level);
        var member = new SettlementFoodRequest(UUID.randomUUID(), request.requester(), request.donor(), 1,
                request.created(), request.created(), request.expires(), request.authority());
        member.caravan = request.id(); member.purpose = SettlementFoodRequest.Purpose.GUARD; member.state = SettlementFoodRequest.State.TRAVELLING;
        var journey = new SettlementCourierJourney(member.id(), villager.getUUID(), request.journey.home, request.journey.dimension,
                request.journey.approach, request.journey.crossing, villager.saveWithoutId(new CompoundTag()), 1, ItemStack.EMPTY, level.getGameTime());
        journey.presence = SettlementCourierJourney.Presence.STORED; journey.leg = SettlementCourierJourney.Leg.OUTBOUND;
        data.aid().requests.put(member.id(), member); data.aid().journey(member, journey); request.participants.put(villager.getUUID(), true);
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request, member), level.getGameTime());
        SettlementEncounterRecovery.died(level, request, SettlementJourneyEncounters.context(level, request.journey));
        SettlementJourneyEncounters.resolve(level, request, List.of(request, member), level.getGameTime());
        helper.assertTrue(member.recalled, "survivor is recalled");
        helper.assertValueEqual(member.state, SettlementFoodRequest.State.RETURNING, "survivor has a return mission");
        helper.assertValueEqual(member.journey.leg, SettlementCourierJourney.Leg.HOMEBOUND, "survivor returns on existing journey");
        helper.assertTrue(member.journey.repair != null || member.journey.obstructed, "return path is prepared or explicitly blocked");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void taggedImportantItemsOverrideOrdinaryLoss(GameTestHelper helper) {
        var item = new ItemStack(Items.COMPASS);
        item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Survey compass"));
        helper.assertTrue(SettlementEncounterRecovery.protectedItem(item), "datapack tag marks an important item");
        helper.assertTrue(SettlementEncounterRecovery.recover(item, 0, new java.util.Random(7)), "tag overrides zero ordinary recovery");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void backgroundBiomeConditionsNeverLoadTerrain(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel(); var distant = new BlockPos(20000000, 80, 20000000);
        request.journey.position = distant;
        var biome = level.getChunkSource().getGenerator().getBiomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(distant.getX()),
                net.minecraft.core.QuartPos.fromBlock(distant.getY()), net.minecraft.core.QuartPos.fromBlock(distant.getZ()), level.getChunkSource().randomState().sampler());
        var condition = new com.jvn.villagerretaliation.dialogue.DialogueCondition.Biome(java.util.Set.of(biome.unwrapKey().orElseThrow().location()), java.util.Set.of());
        helper.assertTrue(SettlementJourneyEncounters.matches(level, request.journey, List.of(condition)), "background biome matches the dimension source");
        helper.assertFalse(level.hasChunkAt(distant), "biome eligibility does not load chunks");
        helper.succeed();
    }
    private static SettlementFoodRequest fixture(GameTestHelper helper) {
        var request = SettlementCourierGameTests.fixture(helper, false).request();
        request.journey.leg = SettlementCourierJourney.Leg.OUTBOUND;
        request.journey.position = request.journey.crossing.steps().getFirst().position();
        request.offer.custody = SettlementFoodOffer.Custody.COURIER;
        return request;
    }
    @GameTest(template = "empty")
    public static void definitionsRejectMalformedAndUnknownFields(GameTestHelper helper) {
        for (String json : List.of("{\"type\":\"unknown\"}", "{\"type\":\"rest\",\"typo\":1}",
                "{\"type\":\"rest\",\"chance\":2}", "{\"type\":\"rest\",\"duration_ticks\":20.5}",
                "{\"type\":\"attack\"}", "{\"type\":\"rest\",\"conditions\":[{\"type\":\"unknown\"}]}")) {
            boolean rejected = false;
            try { definition(json); } catch (IllegalArgumentException | IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "invalid definition rejected: " + json);
        }
        helper.assertTrue(attack().attackers().size() == 1, "valid attack parsed");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void roadDevelopmentAndGuardsReduceButNeverRemoveRisk(GameTestHelper helper) {
        var definition = attack();
        helper.assertTrue(definition.risk(1, 0) > definition.risk(.875, 0), "developing trail reduces risk");
        helper.assertTrue(definition.risk(.875, 0) > definition.risk(.75, 0), "worn trail reduces risk further");
        helper.assertTrue(definition.risk(.75, 0) > definition.risk(.5, 0), "built road reduces risk further");
        helper.assertTrue(definition.risk(.5, 0) > definition.risk(.5, 4), "guards deter attackers");
        helper.assertTrue(definition.risk(.5, 7) > 0, "roads and guards never guarantee safety");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void encounterSnapshotSurvivesDefinitionRemovalAndRejectsCorruption(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        var saved = request.journey.encounter.save();
        var restored = SettlementEncounterState.load(saved);
        helper.assertValueEqual(restored.save(), saved, "definition and attacker states are self contained");
        saved.putLong("Generation", 0);
        boolean rejected = false;
        try { SettlementEncounterState.load(saved); } catch (IllegalArgumentException expected) { rejected = true; }
        helper.assertTrue(rejected, "invalid generation fails closed");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void nightRestPausesAndResumesWithoutRepeatingAfterReload(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel(); long now = level.getGameTime();
        var rest = definition("{\"type\":\"rest\",\"duration_ticks\":60,\"delay_ticks\":0,\"conditions\":[{\"type\":\"time\",\"time\":\"night\"}]}");
        long original = level.getDayTime(); level.setDayTime(18000);
        helper.assertTrue(SettlementJourneyEncounters.matches(level, request.journey, rest.conditions()), "night condition matches");
        level.setDayTime(1000);
        helper.assertFalse(SettlementJourneyEncounters.matches(level, request.journey, rest.conditions()), "day condition fails");
        level.setDayTime(original);
        SettlementJourneyEncounters.start(level, request, rest, List.of(request), now);
        request.journey.advance(now + 100); helper.assertValueEqual(request.journey.step, 0, "rest blocks route progress");
        for (int tick = 20; tick <= 100; tick += 20) {
            var aid = SettlementSavedData.get(level).aid(); aid.durableRevision = aid.revision();
            SettlementJourneyEncounters.tick(level, request, now + tick);
        }
        helper.assertTrue(request.journey.encounter.complete(), "rest and delay finish");
        var saved = request.save(level.registryAccess());
        var restored = SettlementFoodRequest.load(saved, level.registryAccess());
        helper.assertTrue(restored.journey.encounter.complete(), "completed rest remains complete");
        helper.assertTrue(restored.journey.blocker.isEmpty(), "travel is released");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void backgroundCombatUsesHealthAndWeaponAndRetainsDamage(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        var villager = SettlementJourneyEncounters.context(level, request.journey);
        float bare = SettlementJourneyEncounters.damage(villager);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(SettlementJourneyEncounters.damage(villager) > bare, "equipped weapon affects combat");
        villager.setHealth(17); SettlementCourierService.capture(request.journey, villager);
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        SettlementJourneyEncounters.round(level, request, List.of(request));
        helper.assertTrue(request.journey.encounter.attackers.getFirst().getFloat("Health") < 20, "attacker records damage");
        helper.assertTrue(request.journey.residentContext.getFloat("Health") < 17, "participant records damage");
        var saved = request.save(level.registryAccess());
        helper.assertValueEqual(SettlementFoodRequest.load(saved, level.registryAccess()).save(level.registryAccess()), saved, "combat survives reload");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void questItemsRemainRecoverableAndKeepComponents(GameTestHelper helper) {
        var item = new ItemStack(Items.PAPER, 3); var tag = new CompoundTag();
        tag.putString(com.jvn.villagerretaliation.quest.item.QuestItems.IDENTITY, "test:document");
        tag.putString("other_mod:payload", "preserve this");
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        item.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("A letter"));
        helper.assertTrue(SettlementEncounterRecovery.recover(item, 0, new java.util.Random(1)), "quest identity overrides destruction");
        helper.assertFalse(SettlementEncounterRecovery.recover(new ItemStack(Items.DIRT), 0, new java.util.Random(1)), "ordinary items follow policy");
        var remains = new SettlementEncounterRemains(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BlockPos.ZERO, new CompoundTag());
        remains.items.add(item);
        var restored = SettlementEncounterRemains.load(remains.save(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(ItemStack.matches(restored.items.getFirst(), item), "custom components and ownership survive");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void cargoLossIsAppliedOnceAndPartialDeliveryRemainsValid(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel(); long now = level.getGameTime();
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), now);
        SettlementJourneyEncounters.resolve(level, request, List.of(request), now);
        helper.assertValueEqual(request.lostNutrition, 10L, "two bread are lost");
        helper.assertValueEqual(request.incomingNutrition(), 90L, "incoming forecast reflects loss");
        for (int tick = 20; tick <= 100; tick += 20) SettlementJourneyEncounters.tick(level, request, now + tick);
        helper.assertValueEqual(request.lostNutrition, 10L, "completed encounter never repeats loss");
        request.deliveredNutrition = 90; request.offer.replaceRemaining(List.of()); request.state = SettlementFoodRequest.State.COMPLETED;
        helper.assertValueEqual(SettlementFoodRequest.load(request.save(level.registryAccess()), level.registryAccess()).lostNutrition, 10L, "partial delivery is save compatible");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void deceasedPresentationCannotBeRevivedOrActAsResident(GameTestHelper helper) {
        var body = com.jvn.villagerretaliation.entity.VillagerRetaliationEntityTypes.FALLEN_VILLAGER.get().create(helper.getLevel());
        helper.assertFalse(((Object) body) instanceof net.minecraft.world.entity.LivingEntity, "remains are not a living entity");
        helper.assertFalse(body.hurt(helper.getLevel().damageSources().generic(), 100), "remains cannot be killed again");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void cleanupOnlyRemovesOwnedActorsAndRejectsStaleJoins(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        var state = request.journey.encounter; state.visible = true;
        var actor = EntityType.loadEntityRecursive(state.attackers.getFirst().copy(), level, entity -> entity);
        actor.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        actor.getPersistentData().putLong(SettlementJourneyEncounters.GENERATION, state.generation);
        helper.assertTrue(level.addFreshEntity(actor), "current actor joins");
        var natural = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        state.generation++; state.stage = SettlementEncounterState.Stage.RETIRING;
        SettlementJourneyEncounters.cleanup(level, state);
        helper.assertTrue(actor.isRemoved(), "encounter attacker cleaned");
        helper.assertTrue(natural.isAlive(), "unrelated animal remains");
        var stale = EntityType.loadEntityRecursive(state.attackers.getFirst().copy(), level, entity -> entity);
        helper.assertFalse(level.addFreshEntity(stale), "stale actor cannot return");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void recoveryPositionAndMultipleClaimsPersistIndependently(GameTestHelper helper) {
        var request = fixture(helper); var level = helper.getLevel();
        SettlementJourneyEncounters.start(level, request, attack(), List.of(request), level.getGameTime());
        var first = SettlementEncounterRecovery.remains(request, request.journey.encounter);
        first.items.add(new ItemStack(Items.EMERALD)); first.position = first.center.east(4);
        var second = new SettlementEncounterRemains(UUID.randomUUID(), first.encounter, UUID.randomUUID(), first.center, new CompoundTag());
        second.items.add(new ItemStack(Items.DIAMOND)); request.journey.remains.add(second);
        first.items.clear(); first.claimed = true; first.claimant = UUID.randomUUID();
        var restored = SettlementCourierJourney.load(request.journey.save(level.registryAccess()), level.registryAccess());
        helper.assertValueEqual(restored.remains.getFirst().position, first.position, "fixed position is persisted");
        helper.assertFalse(restored.remains.getFirst().pending(), "claimed record remains claimed");
        helper.assertTrue(restored.remains.getLast().pending(), "other remains still require recovery");
        helper.succeed();
    }
}
