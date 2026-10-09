const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const child = (pointer, key) => pointer + "/" + String(key).replaceAll("~", "~0").replaceAll("/", "~1");

export function validateStandingReferences(root, context) {
  const issues = [];
  const issue = (pointer, message) => issues.push({ pointer, message });
  const campaigns = context.campaigns ?? new Map(context.campaign ? [[context.campaign.id, context.campaign]] : []);
  const visit = (value, pointer) => {
    if (Array.isArray(value)) value.forEach((entry, index) => visit(entry, child(pointer, index)));
    else if (object(value)) {
      if ((value.type ?? value.action) === "campaign_standing") {
        const action = Object.hasOwn(value, "amount") || Object.hasOwn(value, "operation_id");
        const questId = value.quest ?? value.quest_id ?? context.quest;
        const quest = context.quests?.get(questId);
        const campaignId = action ? quest?.metadata?.campaign ?? (questId === context.quest ? context.campaign?.id : undefined) : value.campaign;
        const campaign = campaigns.get(campaignId);
        if (!campaign || action && (!Array.isArray(campaign.quest_bundles) || !campaign.quest_bundles.includes(questId))) {
          issue(child(pointer, action ? "quest" : "campaign"), action ? "standing action requires a quest declared by its campaign" : "campaign standing content is unavailable");
        } else {
          const entries = value.scope === "character" ? campaign.relationships : campaign.factions;
          const standing = (Array.isArray(entries) ? entries : []).find(entry => entry?.id === value.subject);
          if (!standing) issue(child(pointer, "subject"), "unknown campaign standing " + value.subject);
          else if (value.reaction && !(Array.isArray(standing.reactions) ? standing.reactions : []).some(reaction => reaction?.id === value.reaction))
            issue(child(pointer, "reaction"), "unknown standing reaction " + value.reaction);
        }
      }
      for (const [key, entry] of Object.entries(value)) visit(entry, child(pointer, key));
    }
  };
  visit(root, "");
  return issues;
}
