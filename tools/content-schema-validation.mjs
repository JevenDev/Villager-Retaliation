const child = (pointer, key) => `${pointer}/${String(key).replaceAll("~", "~0").replaceAll("/", "~1")}`;
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const equal = (left, right) => {
  if (left === right) return true;
  if (Array.isArray(left) && Array.isArray(right)) return left.length === right.length && left.every((value, index) => equal(value, right[index]));
  if (object(left) && object(right)) return Object.keys(left).length === Object.keys(right).length
    && Object.keys(left).every(key => Object.hasOwn(right, key) && equal(left[key], right[key]));
  return false;
};

export function validateContentSchema(value, schema) {
  const keywords = new Set([
    "$schema", "$id", "$defs", "$ref", "title", "description", "default", "examples",
    "type", "const", "enum", "allOf", "anyOf", "oneOf", "not", "if", "then", "else",
    "properties", "propertyNames", "required", "additionalProperties", "items", "minItems", "maxItems",
    "uniqueItems", "minLength", "maxLength", "pattern", "minimum", "maximum", "minProperties", "maxProperties"
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
    for (const key of ["not", "if", "then", "else", "items", "additionalProperties", "propertyNames"]) if (Object.hasOwn(rule, key)) checkSchema(rule[key]);
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
    const matchesType = type => Array.isArray(type) ? type.some(matchesType) : type === "object" ? object(value) : type === "array" ? Array.isArray(value)
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
      if (rule.minProperties !== undefined && Object.keys(value).length < rule.minProperties) issue("object has too few properties");
      if (rule.maxProperties !== undefined && Object.keys(value).length > rule.maxProperties) issue("object has too many properties");
      for (const key of rule.required ?? []) if (!Object.hasOwn(value, key)) issue("required field is missing", child(pointer, key));
      for (const [key, entry] of Object.entries(value)) {
        if (issues.length >= 128) break;
        if (rule.propertyNames !== undefined) issues.push(...validate(key, rule.propertyNames, child(pointer, key), depth + 1));
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
