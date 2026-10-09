package com.jvn.villagerretaliation.dialogue.forced;

import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueContext;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueDefinition;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueFollowUp;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueItemDestination;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueItemPayment;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOption;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutput;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueOutputMode;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueStolenItemReturn;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.ForcedDialogueTrigger;
import com.jvn.villagerretaliation.dialogue.forced.ForcedDialogueResources.LocalizedText;
import com.jvn.villagerretaliation.dialogue.normal.SocialAttributeCondition;
import com.jvn.villagerretaliation.profile.VillagerSocialAttribute;
import com.jvn.villagerretaliation.profile.VillagerSocialAttributeBehavior;
import com.jvn.villagerretaliation.reputation.VillagerReputationManager;
import com.jvn.villagerretaliation.util.VillagerEquipmentCondition;
import com.jvn.villagerretaliation.util.VillagerInventoryItemRemoval;
import com.jvn.villagerretaliation.util.VillagerPlayerItemCondition;
import com.jvn.villagerretaliation.util.VillagerReputationCondition;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_foundation")
@PrefixGameTestTemplate(false)
public final class ForcedDialogueRestitutionGameTests {
    private ForcedDialogueRestitutionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_forced_restitution")
    public static void restitutionMenuKeepsPaymentTermsAndTheAuthoredLeaveOption(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var entry = option("offer", "Pay restitution", payment(11));
        var source = definition(entry);
        var menu = new Menu();
        player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 20));
        helper.assertTrue(ForcedDialogueRestitutionHandler.handle(player, villager, session(helper, villager, source), entry, menu),
                "restitution opens its generated choices");
        var generated = menu.definition;
        helper.assertValueEqual(generated.id(), source.id() + ".restitution_options", "menu identity stays stable");
        helper.assertValueEqual(generated.options().stream().map(ForcedDialogueOption::id).toList(),
                List.of("offer.pay", "offer.haggle", "offer.refuse", "offer.threaten", "leave"), "legacy choices retain their order and IDs");
        helper.assertValueEqual(generated.leaveOption(), source.leaveOption(), "authored leave behavior is retained");
        helper.assertValueEqual(generated.conditions(), source.conditions(), "generated menus retain source conditions");
        helper.assertValueEqual(generated.metadata(), source.metadata(), "generated menus retain source metadata");
        helper.assertValueEqual(generated.forceCameraTowardsVillager(), source.forceCameraTowardsVillager(), "camera policy remains unchanged");
        var pay = find(generated, "offer.pay");
        helper.assertValueEqual(pay.itemPayment().removal(), entry.itemPayment().removal(), "payment selectors and required count are unchanged");
        helper.assertValueEqual(pay.itemPayment().destination(), ForcedDialogueItemDestination.VILLAGER_INVENTORY, "payment keeps its destination");
        helper.assertValueEqual(pay.itemPayment().overflowDestination(), ForcedDialogueItemDestination.SOURCE_CONTAINER, "overflow destination is retained");
        helper.assertValueEqual(pay.itemPayment().successReputationDelta(), 4, "successful payment keeps its reputation effect");
        helper.assertValueEqual(pay.itemPayment().failureReputationDelta(), -2, "failed payment keeps its reputation effect");
        helper.assertFalse(pay.itemPayment().failureAggro(), "a failed payment leaves the negotiation open");
        helper.assertFalse(pay.itemPayment().failureEndConversation(), "a failed payment preserves the menu");
        helper.assertTrue(pay.itemPayment().requireSpace(), "payment still requires destination space");
        helper.assertFalse(find(generated, "offer.refuse").aggro(), "refusal retains its nonviolent option");
        helper.assertTrue(find(generated, "offer.threaten").aggro(), "threat keeps its authored consequence");
        helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 20, "opening a menu never takes payment");
        helper.assertFalse(ForcedDialogueRestitutionHandler.handle(player, villager, session(helper, villager, generated), pay, menu),
                "confirmed payment returns to the shared payment executor");
        helper.assertFalse(ForcedDialogueRestitutionHandler.handle(player, villager, session(helper, villager, generated),
                option("ordinary", "Continue", ForcedDialogueItemPayment.empty()), menu), "ordinary dialogue stays outside the restitution handler");
        helper.assertValueEqual(menu.opens, 1, "ordinary selection cannot reopen restitution");
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_restitution")
    public static void successfulHagglingReducesThePriceOnceAndKeepsItsPaymentBinding(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        for (int count : List.of(11, 1)) {
            String base = count == 11 ? "temple_restitution" : "offer";
            var haggle = option(base + ".haggle", "Can we lower that?", payment(count));
            var source = definition(haggle);
            var menu = new Menu();
            prepareRoll(helper, villager, player, true);
            helper.assertTrue(ForcedDialogueRestitutionHandler.handle(player, villager, session(helper, villager, source), haggle, menu),
                    "haggle intent is handled");
            helper.assertTrue(menu.definition != null, "successful haggle replaces the offered terms");
            var pay = find(menu.definition, base + ".pay").itemPayment();
            helper.assertValueEqual(pay.removal().count(), count == 11 ? 7 : 1, "reduced price rounds up and never drops below one");
            helper.assertValueEqual(pay.removal().selectors(), haggle.itemPayment().removal().selectors(), "discount preserves the item selectors");
            helper.assertValueEqual(pay.removal().stackPredicate(), haggle.itemPayment().removal().stackPredicate(), "discount preserves stack requirements");
            helper.assertValueEqual(pay.destination(), haggle.itemPayment().destination(), "discount preserves the payment destination");
            helper.assertTrue(menu.definition.options().stream().noneMatch(option -> option.id().endsWith(".haggle")),
                    "accepted reduction cannot be negotiated repeatedly");
            helper.assertTrue(menu.disabled.isBlank(), "accepted haggle does not emit a rejection");
        }
        villager.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_forced_restitution")
    public static void rejectedHagglingDisablesOnlyTheAttemptedOption(GameTestHelper helper) {
        var player = player(helper);
        var villager = helper.spawn(EntityType.VILLAGER, 1, 1, 1);
        var haggle = option("temple_restitution.haggle", "Can we lower that?", payment(11));
        var source = definition(haggle);
        var menu = new Menu();
        prepareRoll(helper, villager, player, false);
        helper.assertTrue(ForcedDialogueRestitutionHandler.handle(player, villager, session(helper, villager, source), haggle, menu),
                "rejected haggle is handled");
        helper.assertTrue(menu.definition == null, "rejection keeps the current menu and its price");
        helper.assertValueEqual(menu.disabled, haggle.id(), "only the attempted haggle option is disabled");
        helper.assertFalse(menu.response.isBlank(), "the rejection has readable dialogue");
        helper.assertValueEqual(haggle.itemPayment().removal().count(), 11, "rejection cannot change the price");
        villager.discard();
        helper.succeed();
    }

    private static void prepareRoll(GameTestHelper helper, Villager villager, ServerPlayer player, boolean success) {
        VillagerReputationManager.getReputationSnapshot(helper.getLevel(), villager, player.getUUID());
        VillagerSocialAttributeBehavior.value(helper.getLevel(), villager, VillagerSocialAttribute.CHARM);
        VillagerSocialAttributeBehavior.value(helper.getLevel(), villager, VillagerSocialAttribute.KINDNESS);
        var random = helper.getLevel().getRandom();
        for (long seed = 0; seed < 10000; seed++) {
            random.setSeed(seed);
            double roll = random.nextDouble();
            if (success ? roll < 0.03D : roll >= 0.95D) {
                random.setSeed(seed);
                return;
            }
        }
        throw new IllegalStateException("No bounded fixture seed produced the requested haggle result");
    }

    private static ForcedDialogueItemPayment payment(int count) {
        var removal = new VillagerInventoryItemRemoval(List.of(new VillagerInventoryItemRemoval.ItemSelector(
                Items.EMERALD, null, ResourceLocation.parse("minecraft:emerald"))), count);
        return new ForcedDialogueItemPayment(removal, List.of(LocalizedText.inline("Payment accepted.")),
                List.of(LocalizedText.inline("More payment is required.")), 4, -2, true, true,
                ForcedDialogueItemDestination.VILLAGER_INVENTORY, ForcedDialogueItemDestination.SOURCE_CONTAINER, true);
    }

    private static ForcedDialogueOption option(String id, String label, ForcedDialogueItemPayment payment) {
        return new ForcedDialogueOption(id, LocalizedText.inline(label), List.of(LocalizedText.inline("Return {payment_count} {payment_items}.")),
                3, false, 0, true, 0, ForcedDialogueStolenItemReturn.empty(), payment,
                VillagerReputationCondition.empty(), SocialAttributeCondition.EMPTY, List.of(), ForcedDialogueFollowUp.empty());
    }

    private static ForcedDialogueDefinition definition(ForcedDialogueOption entry) {
        var leave = option("leave", "Leave", ForcedDialogueItemPayment.empty());
        return new ForcedDialogueDefinition("fixture", null, ForcedDialogueTrigger.QUEST,
                new ForcedDialogueOutput(ForcedDialogueOutputMode.FORCED_DIALOGUE, 0, false), List.of(),
                true, false, true, false, 0, 1, 0, 0, 1, 0, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE,
                Set.of(), Set.of(), Set.of(), 1, 5, false, false, 0, false,
                VillagerEquipmentCondition.empty(), VillagerPlayerItemCondition.empty(), VillagerReputationCondition.empty(),
                0, List.of(entry), leave, List.of(leave), null, List.of(new DialogueCondition.AllOf(List.of())));
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "restitution"));
    }

    private static Session session(GameTestHelper helper, Villager villager, ForcedDialogueDefinition definition) {
        var context = new ForcedDialogueContext("Witness", "Player", "", "", "", "", "", 0,
                "", "", "", "", 0, 0, 0, 0, 0, helper.getLevel().getServer(), helper.getLevel().getRandom(), "en_us");
        return new Session(villager.getUUID(), definition, context, helper.getLevel().getGameTime(), List.of());
    }

    private static ForcedDialogueOption find(ForcedDialogueDefinition definition, String id) {
        return definition.options().stream().filter(option -> option.id().equals(id)).findFirst().orElseThrow();
    }

    private record Session(UUID villagerId, ForcedDialogueDefinition definition, ForcedDialogueContext context,
            long startedGameTime, List<String> disabledOptionIds) implements ForcedDialogueRuntime.Session { }

    private static final class Menu implements ForcedDialogueRuntime.GeneratedMenu {
        private ForcedDialogueDefinition definition;
        private String disabled = "";
        private String response = "";
        private int opens;

        @Override public void open(ForcedDialogueDefinition definition) { this.definition = definition; opens++; }
        @Override public void disable(String optionId, String response) { disabled = optionId; this.response = response; }
    }
}
