package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.campaign.content.CampaignTransactionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.VillagerQuestResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.conditions.QuestAvailabilityService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class CampaignTransactions {
    private CampaignTransactions() { }

    public static Reading read(DialogueContext context, ResourceLocation questId, String transactionId) {
        if (context == null || !context.player().server.isSameThread() || questId == null)
            return Reading.unavailable("Campaign transactions require the server thread and an owning quest.");
        var quest = VillagerQuestResources.quest(context.player().server, questId).orElse(null);
        if (quest == null || quest.campaign() == null) return Reading.unavailable("Campaign transactions must belong to a campaign quest.");
        String issue = CampaignQuestAccess.executionIssue(context, quest);
        if (!issue.isBlank()) return Reading.unavailable(issue);
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), quest.id());
        var instance = CampaignSavedData.get(context.level()).instances().get(quest.campaign());
        if (progress == null || progress.questRunId() == null || instance == null)
            return Reading.unavailable("Start this campaign quest before arranging its transaction.");
        if (!QuestAvailabilityService.matchesProviderLock(context, quest, progress))
            return Reading.unavailable("Return to this quest's assigned character to arrange its transaction.");
        var content = CampaignResources.current(context.player().server).campaigns().get(quest.campaign());
        var definition = content.definition().transactions().get(transactionId);
        if (definition == null || !definition.quest().equals(quest.id())) return Reading.unavailable("Unknown transaction for this campaign quest.");
        var seller = CampaignCharacterService.resolve(context.player().server, quest.campaign(), definition.character());
        if (!seller.available()) return Reading.unavailable(seller.diagnostic());
        if (!seller.entity().getUUID().equals(context.villager().getUUID()) || context.player().serverLevel() != seller.entity().level()
                || !context.player().isAlive() || context.player().distanceToSqr(seller.entity()) > 256.0)
            return Reading.unavailable("Return to the transaction's bound seller to continue.");
        var data = CampaignTransactionSavedData.get(context.level());
        if (!data.diagnostic().isBlank()) return Reading.unavailable(data.diagnostic());
        if (!BuiltInRegistries.ITEM.containsKey(definition.item())) return Reading.unavailable("The transaction's payment item is unavailable: " + definition.item());
        var unit = new ItemStack(BuiltInRegistries.ITEM.get(definition.item()));
        if (unit.isEmpty()) return Reading.unavailable("The transaction's payment item is empty.");
        var key = new CampaignTransaction.Key(quest.campaign(), instance.instanceId(), context.player().getUUID(), quest.id(), progress.questRunId(), transactionId);
        var terms = new CampaignTransaction.Terms(key, content.definition().revision(), content.definition().fingerprint(), definition.titleKey(),
                content.localization().plainText(context.player().clientInformation().language(), definition.titleKey()).orElse(definition.titleKey()),
                definition.character(), seller.entity().getUUID(), seller.entity().level().dimension().location(), definition.item(),
                (CompoundTag) unit.save(context.level().registryAccess()), unit.getMaxStackSize(), definition.count(), definition.bargainedCount(), context.level().getGameTime());
        var receipt = data.receipts().get(key.operation());
        if (receipt == null && definition.bargainedCount() != null) {
            var conversations = com.jvn.villagerretaliation.dialogue.conversation.ConversationSavedData.get(context.level());
            if (!conversations.diagnostic().isBlank()) return Reading.unavailable(conversations.diagnostic());
            if (conversations.hasTransaction(key.operation()))
                return Reading.unavailable("A saved conversation already resolved this bargain. Restore its missing transaction data before continuing.");
        }
        if (receipt == null && progress.state() != VillagerQuestSavedData.QuestState.ACTIVE)
            return Reading.unavailable("This campaign quest is no longer active. Its existing transactions remain recorded.");
        if (receipt != null && !receipt.terms().compatible(terms))
            return Reading.unavailable("Saved transaction terms or seller identity changed. Restore matching campaign content and bindings.");
        return new Reading(definition, terms, receipt, "");
    }

    public record Reading(CampaignTransactionDefinition definition, CampaignTransaction.Terms terms, CampaignTransaction.Receipt receipt, String diagnostic) {
        static Reading unavailable(String issue) { return new Reading(null, null, null, issue); }
        public boolean available() { return diagnostic.isBlank(); }
    }
}
