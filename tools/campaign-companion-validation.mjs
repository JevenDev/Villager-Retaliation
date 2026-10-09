const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const child = (pointer, key) => pointer + "/" + String(key).replaceAll("~", "~0").replaceAll("/", "~1");

export function validateCompanionReferences(root, context) {
  const issues = [];
  const campaigns = context.campaigns ?? new Map(context.campaign ? [[context.campaign.id, context.campaign]] : []);
  const visit = (value, pointer) => {
    if (Array.isArray(value)) value.forEach((entry, index) => visit(entry, child(pointer, index)));
    else if (object(value)) {
      if (value.type === "campaign_companion") {
        const campaign = campaigns.get(value.campaign);
        if (!Array.isArray(campaign?.characters) || !campaign.characters.some(entry => entry?.id === value.role))
          issues.push({ pointer: child(pointer, "role"), message: "unknown campaign companion reference " + value.campaign + "/" + value.role });
      }
      for (const [key, entry] of Object.entries(value)) visit(entry, child(pointer, key));
    }
  };
  visit(root, "");
  return issues;
}
