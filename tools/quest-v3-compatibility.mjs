import { createHash } from "node:crypto";
import { createRequire } from "node:module";
const { V2, migrate } = createRequire(import.meta.url)("./quest-v3-format.js");
class JsonNumber {
  constructor(source) { this.source = source; }
}
const quote = value => JSON.stringify(value).replaceAll("\u2028", "\\u2028").replaceAll("\u2029", "\\u2029");
function canonical(value) {
  if (value instanceof JsonNumber) return value.source;
  if (Array.isArray(value)) return "[" + value.map(canonical).join(",") + "]";
  if (value && typeof value === "object") {
    return "{" + Object.keys(value).sort().map(key => quote(key) + ":" + canonical(value[key])).join(",") + "}";
  }
  return typeof value === "string" ? quote(value) : JSON.stringify(value);
}
const digest = value => createHash("sha256").update(canonical(value)).digest("hex");
function parseSource(source) {
  // existing Java fingerprints retain the original numeric spelling
  source = source.replace(/^\uFEFF/, "");
  JSON.parse(source);
  const tokens = source.match(/"(?:\\.|[^"\\])*"|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|true|false|null|[{}\[\]:,]/g);
  let index = 0;
  function read() {
    const token = tokens[index++];
    if (token === "[") {
      const values = [];
      while (tokens[index] !== "]") {
        values.push(read());
        if (tokens[index] === ",") index++;
      }
      index++;
      return values;
    }
    if (token === "{") {
      const values = Object.create(null);
      while (tokens[index] !== "}") {
        const key = JSON.parse(tokens[index++]);
        index++;
        values[key] = read();
        if (tokens[index] === ",") index++;
      }
      index++;
      return values;
    }
    return /^-?\d/.test(token) ? new JsonNumber(token) : JSON.parse(token);
  }
  return read();
}
export function fingerprintJson(source) {
  return digest(parseSource(source));
}
const kinds = { scenes: "scene", conversations: "conversation", encounters: "encounter", rewards: "reward", pools: "pool", social_progression: "social_progression" };
export function migrationReceipts(files, sourceTexts = new Map()) {
  const original = (file, value) => sourceTexts.has(file) ? parseSource(sourceTexts.get(file)) : value;
  const receipts = new Map();
  for (const [file, quest] of files) {
    if (quest?.schema !== V2) continue;
    const match = file.replaceAll("\\", "/").match(/^(data\/([^/]+)\/(?:world_campaigns\/[^/]+\/)?quests\/[^/]+\/[^/]+\/)quest\.json$/);
    if (!match) continue;
    const before = { quest: { [quest.id]: original(file, quest) } };
    for (const [companionPath, companion] of files) {
      if (!companionPath.startsWith(match[1]) || companionPath === file) continue;
      const relative = companionPath.slice(match[1].length);
      const kind = kinds[relative.split("/")[0]];
      if (!kind || !companion?.id) continue;
      (before[kind] ||= {})[companion.id] = original(companionPath, companion);
    }
    const after = { ...before, quest: { [quest.id]: migrate(quest) } };
    const id = createHash("sha256").update(quest.id).digest("hex");
    receipts.set(`data/${match[2]}/quest_migrations/${id}.json`, {
      schema: "villagerretaliation:quest_migration/v1", quest: quest.id,
      from_fingerprint: digest(before), to_fingerprint: digest(after)
    });
  }
  return receipts;
}
