import { validateContentSchema as validateConversationSchema } from "./content-schema-validation.mjs";
export { validateConversationSchema };

const child = (pointer, key) => `${pointer}/${String(key).replaceAll("~", "~0").replaceAll("/", "~1")}`;
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);

export function localizedConversationText(reference, context) {
  const authored = typeof reference?.key === "string" ? reference.key.trim() : "";
  if (!authored || authored === "#" || authored.startsWith("#") && !context.prefix) return null;
  const key = authored.startsWith("#") ? `${context.prefix}${context.prefix.endsWith(".") ? "" : "."}${authored.slice(1)}` : authored;
  const payload = Object.hasOwn(context.messages ?? {}, key) ? context.messages[key] : undefined;
  const text = typeof payload === "string" ? payload : payload?.text;
  return typeof text === "string" && text.trim() ? text : null;
}

export function materializeConversationAction(value, context) {
  if (Array.isArray(value)) return value.map(entry => materializeConversationAction(entry, context));
  if (!object(value)) return value;
  return Object.fromEntries(Object.entries(value).map(([key, entry]) => [key,
    ["title", "label", "text"].includes(key) ? localizedConversationText(entry, context) : materializeConversationAction(entry, context)]));
}

export function validateConversation(root, schema, context) {
  const issues = validateConversationSchema(root, schema);
  if (issues.length) return issues;
  const issue = (pointer, message) => issues.push({ pointer, message });
  if (context.quest && root.quest && root.quest !== context.quest) issue("/quest", "private conversation belongs to its owning quest");
  const visitText = (value, pointer = "") => {
    if (Array.isArray(value)) value.forEach((entry, index) => visitText(entry, child(pointer, index)));
    else if (object(value)) for (const [key, entry] of Object.entries(value)) {
      if (["title", "label", "text"].includes(key)) {
        if (localizedConversationText(entry, context) === null) issue(child(pointer, key), "English localization must contain one nonblank text value");
      } else visitText(entry, child(pointer, key));
    }
  };
  visitText(root);
  const index = (entries, pointer) => {
    const values = new Map();
    for (const [position, entry] of entries.entries()) {
      if (values.has(entry.id)) issue(`${pointer}/${position}/id`, `duplicate id ${entry.id}`);
      else values.set(entry.id, { ...entry, pointer: `${pointer}/${position}` });
    }
    return values;
  };
  const topics = index(root.topics, "/topics");
  const nodes = index(root.nodes, "/nodes");
  const checks = index(root.checks ?? [], "/checks");
  const outcomes = index(root.outcomes, "/outcomes");
  const require = (values, id, pointer, kind) => { if (!values.has(id)) issue(pointer, `unknown ${kind} ${id}`); };
  const entries = new Set();
  const terminals = new Set();
  const edges = new Map();
  const reverse = new Map();
  for (const topic of topics.values()) {
    require(nodes, topic.start, `${topic.pointer}/start`, "node");
    entries.add(topic.start);
  }
  for (const check of checks.values()) {
    require(nodes, check.success, `${check.pointer}/success`, "node");
    require(nodes, check.failure, `${check.pointer}/failure`, "node");
  }
  if (context.campaign && (!Array.isArray(context.campaign.characters) || !Array.isArray(context.campaign.quest_bundles))) {
    issue("/campaign", "referenced campaign has invalid character or quest declarations");
    return issues;
  }
  const roles = new Set((context.campaign?.characters ?? []).map(role => role?.id));
  if (context.campaign) {
    if (context.campaign.id !== root.campaign) issue("/campaign", "conversation belongs to another campaign");
    if (!roles.has(root.character)) issue("/character", `unknown campaign character ${root.character}`);
    const quest = root.quest ?? context.quest;
    if (quest && !context.campaign.quest_bundles.includes(quest)) issue("/quest", "conversation quest is not declared by its campaign");
  }
  for (const node of nodes.values()) {
    const next = new Set();
    index(node.exchanges ?? [], `${node.pointer}/exchanges`);
    index(node.effects ?? [], `${node.pointer}/effects`);
    for (const [position, exchange] of (node.exchanges ?? []).entries()) {
      if (context.campaign && exchange.speaker.type === "role" && !roles.has(exchange.speaker.role)) issue(`${node.pointer}/exchanges/${position}/speaker/role`, `unknown campaign character ${exchange.speaker.role}`);
    }
    if (node.next) { require(nodes, node.next, `${node.pointer}/next`, "node"); next.add(node.next); }
    if (node.outcome) { require(outcomes, node.outcome, `${node.pointer}/outcome`, "outcome"); terminals.add(node.id); }
    for (const response of index(node.responses ?? [], `${node.pointer}/responses`).values()) {
      index(response.effects ?? [], `${response.pointer}/effects`);
      if (response.next) { require(nodes, response.next, `${response.pointer}/next`, "node"); next.add(response.next); }
      else {
        require(checks, response.check, `${response.pointer}/check`, "check");
        const check = checks.get(response.check);
        if (check) { next.add(check.success); next.add(check.failure); }
      }
    }
    edges.set(node.id, next);
    for (const target of next) {
      if (!reverse.has(target)) reverse.set(target, new Set());
      reverse.get(target).add(node.id);
    }
  }
  const reachable = (start, edges) => {
    const visited = new Set();
    const pending = [...start];
    for (let position = 0; position < pending.length; position++) {
      const id = pending[position];
      if (visited.has(id)) continue;
      visited.add(id);
      pending.push(...(edges.get(id) ?? []));
    }
    return visited;
  };
  const reached = reachable(entries, edges);
  const finishes = reachable(terminals, reverse);
  for (const node of nodes.values()) {
    if (!reached.has(node.id)) issue(`${node.pointer}/id`, "node is unreachable from every topic");
    if (!finishes.has(node.id)) issue(node.pointer, "node has no path to a named outcome");
  }
  return issues;
}
