const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const child = (pointer, key) => pointer + "/" + String(key).replaceAll("~", "~0").replaceAll("/", "~1");

export function validateTransactionReferences(root, context) {
  const issues = [];
  const campaigns = context.campaigns ?? new Map(context.campaign ? [[context.campaign.id, context.campaign]] : []);
  const visit = (value, pointer) => {
    if (Array.isArray(value)) value.forEach((entry, index) => visit(entry, child(pointer, index)));
    else if (object(value)) {
      const rawType = value.type ?? value.action;
      const type = typeof rawType === "string" ? rawType.trim().toLowerCase() : "";
      if (type === "campaign_payment" || type === "campaign_transaction") {
        const payment = type === "campaign_payment";
        const questId = value.quest ?? value.quest_id ?? context.quest;
        const quest = context.quests?.get(questId);
        const campaignId = payment ? quest?.metadata?.campaign ?? (questId === context.quest ? context.campaign?.id : undefined) : value.campaign;
        const campaign = campaigns.get(campaignId);
        const issue = (field, message) => issues.push({ pointer: child(pointer, field), message });
        if (!campaign || !Array.isArray(campaign.quest_bundles) || !campaign.quest_bundles.includes(questId)
            || quest && quest.metadata?.campaign !== campaignId) {
          issue("quest", "transaction requires a quest declared by its campaign");
        } else {
          const transaction = (Array.isArray(campaign.transactions) ? campaign.transactions : []).find(entry => entry?.id === value.transaction);
          if (!transaction) issue("transaction", "unknown campaign transaction " + value.transaction);
          else if (transaction.quest !== questId) issue("transaction", "transaction belongs to another quest");
          else if (payment && root.schema === "villagerretaliation:conversation/v1" && transaction.character !== root.character)
            issue("transaction", "transaction belongs to another campaign character");
        }
      }
      for (const [key, entry] of Object.entries(value)) visit(entry, child(pointer, key));
    }
  };
  visit(root, "");
  return issues;
}
