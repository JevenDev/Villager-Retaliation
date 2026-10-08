const child = (pointer, key) => `${pointer}/${String(key).replaceAll("~", "~0").replaceAll("/", "~1")}`;
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const equal = (left, right) => {
  if (left === right) return true;
  if (Array.isArray(left) && Array.isArray(right)) return left.length === right.length && left.every((value, index) => equal(value, right[index]));
  if (object(left) && object(right)) return Object.keys(left).length === Object.keys(right).length
    && Object.keys(left).every(key => Object.hasOwn(right, key) && equal(left[key], right[key]));
  return false;
};

export function validateConversationSchema(value, schema) {
  const keywords = new Set([
    "$schema", "$id", "$defs", "$ref", "title", "description", "default", "examples",
    "type", "const", "enum", "allOf", "anyOf", "oneOf", "not", "if", "then", "else",
    "properties", "required", "additionalProperties", "items", "minItems", "maxItems",
    "uniqueItems", "minLength", "maxLength", "pattern", "minimum", "maximum"
  ]);
  const references = new Set();
  const resolve = reference => {
    if (!reference.startsWith("#/")) throw new Error(`Unsupported schema reference ${reference}`);
    const target = reference.slice(2).split("/").reduce((part, key) => part?.[key.replaceAll("~1", "/").replaceAll("~0", "~")], schema);
    if (target === undefined) throw new Error(`Unresolved schema reference ${reference}`);
    return target;
  };
  const checkSchema = rule => {
    if (typeof rule === "boolean") return;
    if (!object(rule)) throw new Error("Schema must be an object or boolean");
    for (const key of Object.keys(rule)) if (!keywords.has(key)) throw new Error(`Unsupported schema keyword ${key}`);
    if (rule.$ref) {
      const target = resolve(rule.$ref);
      if (!references.has(rule.$ref)) { references.add(rule.$ref); checkSchema(target); }
    }
    for (const key of ["$defs", "properties"]) for (const child of Object.values(rule[key] ?? {})) checkSchema(child);
    for (const key of ["allOf", "anyOf", "oneOf"]) for (const child of rule[key] ?? []) checkSchema(child);
    for (const key of ["not", "if", "then", "else", "items", "additionalProperties"]) if (Object.hasOwn(rule, key)) checkSchema(rule[key]);
  };
  checkSchema(schema);
  let remaining = 100000;
  const validate = (value, rule, pointer = "", depth = 0) => {
    if (depth > 128 || --remaining < 0) throw { pointer, message: "content exceeds the schema validation limit" };
    if (rule === true) return [];
    if (rule === false) return [{ pointer, message: "unsupported value" }];
    const issues = [];
    const issue = (message, at = pointer) => issues.push({ pointer: at, message });
    if (rule.$ref) issues.push(...validate(value, resolve(rule.$ref), pointer, depth + 1));
    const matchesType = type => type === "object" ? object(value) : type === "array" ? Array.isArray(value)
      : type === "integer" ? Number.isInteger(value) : type === "null" ? value === null : typeof value === type;
    if (rule.type && !matchesType(rule.type)) return [...issues, { pointer, message: `expected ${rule.type}` }];
    if (Object.hasOwn(rule, "const") && !equal(rule.const, value)) issue("unsupported value");
    if (rule.enum && !rule.enum.some(candidate => equal(candidate, value))) issue("unsupported value");
    for (const branch of rule.allOf ?? []) issues.push(...validate(value, branch, pointer, depth + 1));
    for (const key of ["anyOf", "oneOf"]) {
      if (!rule[key]) continue;
      const branches = rule[key].map(branch => validate(value, branch, pointer, depth + 1));
      const matches = branches.filter(branch => branch.length === 0).length;
      if (matches === 0) issues.push(...(branches.sort((left, right) => left.length - right.length)[0] ?? [{ pointer, message: "no supported alternative" }]));
      else if (key === "oneOf" && matches !== 1) issue("expected exactly one supported alternative");
    }
    if (rule.not !== undefined && validate(value, rule.not, pointer, depth + 1).length === 0) issue("unsupported combination of fields");
    if (rule.if !== undefined) {
      const branch = validate(value, rule.if, pointer, depth + 1).length === 0 ? rule.then : rule.else;
      if (branch !== undefined) issues.push(...validate(value, branch, pointer, depth + 1));
    }
    if (object(value)) {
      for (const key of rule.required ?? []) if (!Object.hasOwn(value, key)) issue("required field is missing", child(pointer, key));
      for (const [key, entry] of Object.entries(value)) {
        if (issues.length >= 128) break;
        if (Object.hasOwn(rule.properties ?? {}, key)) issues.push(...validate(entry, rule.properties[key], child(pointer, key), depth + 1));
        else if (rule.additionalProperties === false) issue("unsupported field", child(pointer, key));
        else if (object(rule.additionalProperties)) issues.push(...validate(entry, rule.additionalProperties, child(pointer, key), depth + 1));
      }
    }
    if (Array.isArray(value)) {
      if (rule.minItems !== undefined && value.length < rule.minItems) issue(`array requires at least ${rule.minItems} entries`);
      if (rule.maxItems !== undefined && value.length > rule.maxItems) return [...issues, { pointer, message: `array exceeds ${rule.maxItems} entries` }];
      for (const [index, entry] of value.entries()) {
        if (issues.length >= 128) break;
        if (rule.uniqueItems && value.slice(0, index).some(prior => equal(prior, entry))) issue("duplicate array entry", child(pointer, index));
        if (rule.items !== undefined) issues.push(...validate(entry, rule.items, child(pointer, index), depth + 1));
      }
    }
    if (typeof value === "string") {
      const length = [...value].length;
      if (rule.minLength !== undefined && length < rule.minLength || rule.maxLength !== undefined && length > rule.maxLength) issue("text length is outside the supported range");
      if (rule.pattern && !new RegExp(rule.pattern).test(value)) issue(`text does not match ${rule.pattern}`);
    }
    if (typeof value === "number" && (rule.minimum !== undefined && value < rule.minimum || rule.maximum !== undefined && value > rule.maximum)) issue("number is outside the supported range");
    return issues.slice(0, 128);
  };
  try {
    return validate(value, schema);
  } catch (error) {
    if (typeof error?.pointer === "string" && typeof error?.message === "string") return [error];
    throw error;
  }
}

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
