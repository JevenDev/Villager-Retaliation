import { createHash } from "node:crypto";
import { createRequire } from "node:module";
const { V2, migrate } = createRequire(import.meta.url)("./quest-v3-format.js");
const canonical = value => Array.isArray(value) ? value.map(canonical)
  : value && typeof value === "object" ? Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])])) : value;
const digest = value => createHash("sha256").update(JSON.stringify(canonical(value))).digest("hex");
const kinds = { scenes: "scene", conversations: "conversation", encounters: "encounter", rewards: "reward", pools: "pool", social_progression: "social_progression" };
export function migrationReceipts(files) {
  const receipts = new Map();
  for (const [file, quest] of files) {
    if (quest?.schema !== V2) continue;
    const match = file.replaceAll("\\", "/").match(/^(data\/([^/]+)\/(?:world_campaigns\/[^/]+\/)?quests\/[^/]+\/[^/]+\/)quest\.json$/);
    if (!match) continue;
    const before = { quest: { [quest.id]: quest } };
    for (const [companionPath, companion] of files) {
      if (!companionPath.startsWith(match[1]) || companionPath === file) continue;
      const relative = companionPath.slice(match[1].length);
      const kind = kinds[relative.split("/")[0]];
      if (!kind || !companion?.id) continue;
      (before[kind] ||= {})[companion.id] = companion;
    }
    const after = structuredClone(before);
    after.quest[quest.id] = migrate(quest);
    const id = createHash("sha256").update(quest.id).digest("hex");
    receipts.set(`data/${match[2]}/quest_migrations/${id}.json`, {
      schema: "villagerretaliation:quest_migration/v1", quest: quest.id,
      from_fingerprint: digest(before), to_fingerprint: digest(after)
    });
  }
  return receipts;
}
