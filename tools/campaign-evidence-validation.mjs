const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const child = (pointer, key) => pointer + "/" + String(key).replaceAll("~", "~0").replaceAll("/", "~1");

export function validateEvidenceReferences(root, context) {
  const issues = [];
  const campaigns = context.campaigns ?? new Map(context.campaign ? [[context.campaign.id, context.campaign]] : []);
  const visit = (value, pointer) => {
    if (Array.isArray(value)) value.forEach((entry, index) => visit(entry, child(pointer, index)));
    else if (object(value)) {
      if (value.type === "campaign_evidence") {
        const campaign = campaigns.get(value.campaign);
        if (!Array.isArray(campaign?.evidence) || !campaign.evidence.some(entry => entry?.id === value.evidence))
          issues.push({ pointer: child(pointer, "evidence"), message: "unknown campaign evidence reference " + value.campaign + "/" + value.evidence });
      }
      for (const [key, entry] of Object.entries(value)) visit(entry, child(pointer, key));
    }
  };
  visit(root, "");
  return issues;
}
