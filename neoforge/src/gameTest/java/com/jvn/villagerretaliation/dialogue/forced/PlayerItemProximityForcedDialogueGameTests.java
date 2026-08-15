package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.combat.VillagerWeaponDrawService;
import com.jvn.villagerretaliation.inventory.VillagerInventoryAccess;
import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class PlayerItemProximityForcedDialogueGameTests {
    private static final String EMPTY_TEMPLATE = "empty";

    private PlayerItemProximityForcedDialogueGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void cooldownIsScopedAndPrunedAfterTheExistingRetentionWindow(GameTestHelper helper) {
        UUID villagerId = UUID.randomUUID();
        UUID otherVillagerId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID otherPlayerId = UUID.randomUUID();
        String definitionId = "villagerretaliation:test_player_item";

        PlayerItemProximityForcedDialogueService.clearRuntimeState();
        try {
            helper.assertTrue(
                    PlayerItemProximityForcedDialogueService.cooldownReady(
                            100L, villagerId, playerId, definitionId),
                    "a new villager/player/definition key should be ready");
            PlayerItemProximityForcedDialogueService.markCooldownUsed(
                    100L, villagerId, playerId, definitionId);
            helper.assertFalse(
                    PlayerItemProximityForcedDialogueService.cooldownReady(
                            699L, villagerId, playerId, definitionId),
                    "the same key should remain blocked for 600 ticks");
            helper.assertTrue(
                    PlayerItemProximityForcedDialogueService.cooldownReady(
                            700L, villagerId, playerId, definitionId),
                    "the same key should become ready at the existing boundary");
            helper.assertTrue(
                    PlayerItemProximityForcedDialogueService.cooldownReady(
                            699L, otherVillagerId, playerId, definitionId),
                    "cooldown should not leak to another villager");
            helper.assertTrue(
                    PlayerItemProximityForcedDialogueService.cooldownReady(
                            699L, villagerId, otherPlayerId, definitionId),
                    "cooldown should not leak to another player");
            helper.assertTrue(
                    PlayerItemProximityForcedDialogueService.cooldownReady(
                            699L, villagerId, playerId, definitionId + "_other"),
                    "cooldown should not leak to another definition");

            PlayerItemProximityForcedDialogueService.pruneCooldowns(1300L);
            helper.assertValueEqual(
                    PlayerItemProximityForcedDialogueService.cooldownEntryCount(),
                    1,
                    "cooldown should remain at the existing strict retention boundary");
            PlayerItemProximityForcedDialogueService.pruneCooldowns(1301L);
            helper.assertValueEqual(
                    PlayerItemProximityForcedDialogueService.cooldownEntryCount(),
                    0,
                    "expired cooldown should be pruned after the retention boundary");
        } finally {
            PlayerItemProximityForcedDialogueService.clearRuntimeState();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void heldTradeMatchingSkipsExhaustedOffersAndPreservesOfferIndex(GameTestHelper helper) {
        MerchantOffer exhaustedMatch = offer(Items.EMERALD, 1, Items.APPLE, 1);
        exhaustedMatch.increaseUses();
        MerchantOffer unrelated = offer(Items.COAL, 3, Items.TORCH, 12);
        MerchantOffer availableMatch = new MerchantOffer(
                new ItemCost(Items.DIAMOND, 1),
                Optional.of(new ItemCost(Items.EMERALD, 2)),
                new ItemStack(Items.BREAD),
                12,
                2,
                0.05F);
        ItemStack heldStack = new ItemStack(Items.EMERALD, 64);

        Optional<PlayerItemProximityForcedDialogueService.TradeItemMatch> match =
                PlayerItemProximityForcedDialogueService.matchingHeldTradeItem(
                        null,
                        "en_us",
                        List.of(exhaustedMatch, unrelated, availableMatch),
                        heldStack,
                        "off_hand");

        helper.assertTrue(match.isPresent(), "an available offer cost should match the held item type");
        PlayerItemProximityForcedDialogueService.TradeItemMatch value = match.orElseThrow();
        helper.assertValueEqual(value.offerIndex(), 3, "offer index should remain one-based and include skipped offers");
        helper.assertValueEqual(value.slot(), "off_hand", "held-item slot should be retained");
        helper.assertValueEqual(value.costStack().getCount(), 2, "matched trade cost count should be retained");
        helper.assertTrue(value.resultStack().is(Items.BREAD), "matched trade result should be retained");
        helper.assertTrue(
                PlayerItemProximityForcedDialogueService.matchingHeldTradeItem(
                        null,
                        "en_us",
                        List.of(availableMatch),
                        new ItemStack(Items.IRON_INGOT),
                        "main_hand").isEmpty(),
                "a held item absent from all offer costs should not match");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void chatOutputsPrecedeForcedOutputsWithoutReorderingEitherGroup(GameTestHelper helper) {
        List<String> ordered = PlayerItemProximityForcedDialogueService.chatFirst(
                List.of("forced-first", "chat-first", "forced-second", "chat-second"),
                candidate -> candidate.startsWith("chat"));

        helper.assertValueEqual(
                ordered,
                List.of("chat-first", "chat-second", "forced-first", "forced-second"),
                "selection should remain chat-first and stable within each output kind");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void samePartyWeaponAimingRuleProvidesTenNonHostileArmedVariations(GameTestHelper helper) {
        ForcedDialogueResources.clearCache();
        ForcedDialogueResources.ForcedDialogueDefinition definition =
                ForcedDialogueResources.playerItemProximityCandidates(helper.getLevel().getServer()).stream()
                        .filter(candidate -> candidate.id().equals("player_aiming_weapon_same_party"))
                        .findFirst()
                        .orElseThrow();

        helper.assertTrue(definition.requiresSameParty(),
                "the party aiming rule should only match villagers in the same party as the player");
        helper.assertTrue(definition.requiresPlayerAimingAtWitness(),
                "the party aiming rule should require the player to aim at the witness");
        helper.assertFalse(definition.aggroImmediately(),
                "arming in response to an ally should not immediately start retaliation");
        helper.assertValueEqual(definition.drawWeaponTicks(), 100,
                "the party villager should retain a five-second sheathing delay");
        helper.assertValueEqual(definition.lines().size(), 10,
                "the party aiming rule should retain all ten inline dialogue variations");
        helper.assertTrue(definition.lines().stream().allMatch(line -> line.key().isBlank()),
                "inline variations should not collapse to one translation-key fallback");
        helper.assertTrue(definition.lines().stream().noneMatch(line ->
                        line.text().contains(";") || line.text().contains("\u2014")),
                "party dialogue should not contain semicolons or em dashes");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void matchingAimRefreshesWeaponDrawWhileDialogueIsOnCooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrAimingDrawRefresh");
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Villager witness = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 1));
        witness.setNoAi(true);
        VillagerInventoryAccess.addItem(witness, new ItemStack(Items.IRON_SWORD));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, witness.getEyePosition());

        ForcedDialogueResources.clearCache();
        ForcedDialogueResources.ForcedDialogueDefinition definition =
                ForcedDialogueResources.playerItemProximityCandidates(level.getServer()).stream()
                        .filter(candidate -> candidate.id().equals("player_aiming_sword_neutral"))
                        .findFirst()
                        .orElseThrow();
        long gameTime = level.getGameTime();
        PlayerItemProximityForcedDialogueService.clearRuntimeState();
        PlayerItemProximityForcedDialogueService.markCooldownUsed(
                gameTime, witness.getUUID(), player.getUUID(), definition.id());
        boolean[] dialogueTriggered = {false};

        PlayerItemProximityForcedDialogueService.tryDefinitions(
                level,
                witness,
                player,
                List.of(definition),
                gameTime,
                new PlayerItemProximityForcedDialogueService.Delegate() {
                    @Override
                    public boolean canUseForcedInteractionSystem(ServerPlayer ignoredPlayer, Villager ignoredVillager) {
                        return true;
                    }

                    @Override
                    public boolean hasForcedSession(ServerPlayer ignoredPlayer) {
                        return false;
                    }

                    @Override
                    public boolean matchesReputation(
                            ServerLevel ignoredLevel,
                            Villager ignoredVillager,
                            ServerPlayer ignoredPlayer,
                            ForcedDialogueResources.ForcedDialogueDefinition ignoredDefinition) {
                        return true;
                    }

                    @Override
                    public boolean trigger(
                            ServerLevel ignoredLevel,
                            Villager ignoredVillager,
                            ServerPlayer ignoredPlayer,
                            ForcedDialogueResources.ForcedDialogueDefinition ignoredDefinition,
                            Optional<PlayerItemProximityForcedDialogueService.TradeItemMatch> ignoredTradeItemMatch) {
                        dialogueTriggered[0] = true;
                        return true;
                    }
                });

        helper.assertFalse(dialogueTriggered[0], "dialogue should remain blocked by its cooldown");
        helper.assertTrue(VillagerWeaponDrawService.isDrawn(witness),
                "a matching aim should refresh the weapon draw despite the dialogue cooldown");
        VillagerWeaponDrawService.sheathe(witness);
        PlayerItemProximityForcedDialogueService.clearRuntimeState();
        witness.discard();
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE)
    public static void aimRayRequiresTheWitnessToBeTheFirstVisibleLivingTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = fakePlayer(level, "VrAimingDialogue");
        BlockPos playerPos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D, 0.0F, 0.0F);
        Villager witness = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 1));
        witness.setNoAi(true);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, witness.getEyePosition());

        helper.assertTrue(
                PlayerItemProximityForcedDialogueService.isAimingAtWitness(player, witness, 8.0D),
                "an unobstructed sight ray through the witness hitbox should count as aiming");

        Villager blocker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 1));
        blocker.setNoAi(true);
        helper.assertFalse(
                PlayerItemProximityForcedDialogueService.isAimingAtWitness(player, witness, 8.0D),
                "a nearer living entity should own the sight ray");

        blocker.discard();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, witness.getEyePosition().add(0.0D, 0.0D, 3.0D));
        helper.assertFalse(
                PlayerItemProximityForcedDialogueService.isAimingAtWitness(player, witness, 8.0D),
                "a sight ray outside the witness hitbox should not count as aiming");

        witness.discard();
        helper.succeed();
    }

    private static ServerPlayer fakePlayer(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("villagerretaliation:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, name));
    }

    private static MerchantOffer offer(
            net.minecraft.world.item.Item costItem,
            int costCount,
            net.minecraft.world.item.Item resultItem,
            int maxUses) {
        return new MerchantOffer(
                new ItemCost(costItem, costCount),
                new ItemStack(resultItem),
                maxUses,
                2,
                0.05F);
    }
}
