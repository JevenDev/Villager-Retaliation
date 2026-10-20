import { createRequire } from "node:module";

const questFormat = createRequire(import.meta.url)("./quest-v3-format.js");
const SCHEMA_ID = questFormat.V2;

function clone(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

function isLocalizedReference(value) {
  return value && typeof value === "object" && !Array.isArray(value)
    && Object.keys(value).length === 1 && typeof value.key === "string";
}

export function editableQuest(source) {
  if (source.schema !== questFormat.V3) return clone(source);
  const result = questFormat.runtime(source);
  result.schema = SCHEMA_ID;
  if (source.world_campaign) result.world_campaign = source.world_campaign;
  return result;
}

export function materializeLocalizedDefinition(value, prefix, messages) {
  if (Array.isArray(value)) return value.map(child => materializeLocalizedDefinition(child, prefix, messages));
  if (!value || typeof value !== "object") return clone(value);
  if (isLocalizedReference(value)) {
    const key = value.key.startsWith("#") ? `${prefix}.${value.key.slice(1)}` : value.key;
    const payload = messages?.[key];
    if (!payload) return clone(value);
    if (Array.isArray(payload?.lines)) return payload.lines.length === 1 ? clone(payload.lines[0]) : clone(payload.lines);
    return clone(payload?.text ?? payload);
  }
  return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, materializeLocalizedDefinition(child, prefix, messages)]));
}
