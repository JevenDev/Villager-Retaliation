import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { reservedDialogueTag } from "./dialogue-markup-validation.mjs";

export const builtInStyleData = fileURLToPath(new URL("../neoforge/src/main/resources/data", import.meta.url));
const flags = new Set(["italic", "bold", "underlined", "strikethrough", "obfuscated", "wavy", "shake", "pulse", "jump", "rainbow"]);
const animated = new Set(["wavy", "shake", "pulse", "jump"]);
export function animationIssues(value) {
  if (!object(value)) return ["animation must be an object"];
  const errors = [];
  if (Object.keys(value).some(key => !["strength", "speed"].includes(key))) errors.push("unknown animation field");
  for (const [field, maximum] of [["strength", 4], ["speed", 10]])
    if (field in value && (typeof value[field] !== "number" || !Number.isFinite(value[field]) || value[field] < 0 || value[field] > maximum))
      errors.push(field + " must be a number from 0 to " + maximum);
  return errors;
}
const colors = new Set(["black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
  "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "purple", "magenta", "grey", "dark_grey"]);
const colorFields = new Set(["color", "gradient_start", "gradient_end"]);
const object = value => value !== null && typeof value === "object" && !Array.isArray(value);
const validColor = value => value === null || Number.isInteger(value) && value >= 0 && value <= 0xffffff
  || typeof value === "string" && value.length <= 32 && (colors.has(value.trim().toLowerCase()) || /^(?:#|0x)?[a-f0-9]{6}$/i.test(value.trim()));

export function styleIssues(value) {
  if (!object(value)) return ["expected a dialogue style object"];
  const errors = [];
  if (Object.keys(value).some(key => !["tag", "text_effects", "speed"].includes(key))) errors.push("unknown dialogue style field");
  if (typeof value.tag !== "string" || !/^[a-z][a-z0-9_]{0,63}$/.test(value.tag) || reservedDialogueTag(value.tag))
    errors.push("style tag is reserved or invalid");
  if ("speed" in value && (typeof value.speed !== "number" || !Number.isFinite(value.speed) || value.speed < 0.1 || value.speed > 10))
    errors.push("speed must be a number from 0.1 to 10");
  if ("text_effects" in value) {
    if (!object(value.text_effects)) errors.push("text_effects must be an object");
    else {
      for (const [field, setting] of Object.entries(value.text_effects)) {
        if (animated.has(field) && object(setting)) errors.push(...animationIssues(setting).map(issue => field + ": " + issue));
        else if (flags.has(field)) { if (typeof setting !== "boolean") errors.push(field + " must be a boolean or an animation object"); }
        else if (colorFields.has(field)) { if (!validColor(setting)) errors.push(field + " must be a color or null"); }
        else errors.push("unknown text effect: " + field);
      }
      const effects = value.text_effects;
      if (("gradient_start" in effects) !== ("gradient_end" in effects)
          || "gradient_start" in effects && (effects.gradient_start === null) !== (effects.gradient_end === null))
        errors.push("set or clear both gradient colors together");
    }
  }
  return errors;
}

export function compileStyleDocuments(documents) {
  const styles = new Map(), errors = [];
  for (const [file, value] of documents) {
    const issues = styleIssues(value);
    errors.push(...issues.map(issue => file + ": " + issue));
    if (issues.length) continue;
    if (styles.has(value.tag)) errors.push(file + ": duplicate dialogue style tag " + value.tag);
    else styles.set(value.tag, value);
  }
  if (styles.size > 256) errors.push("too many dialogue styles");
  return { styles, errors };
}

async function entries(directory) {
  try { return await readdir(directory, { withFileTypes: true }); }
  catch (error) { if (error.code === "ENOENT") return []; throw error; }
}

async function jsonFiles(directory) {
  const files = [];
  for (const entry of await entries(directory)) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await jsonFiles(file));
    else if (entry.name.endsWith(".json")) files.push(file);
  }
  return files.sort();
}

export async function loadDialogueStyles(dataDirectory = builtInStyleData) {
  const effective = new Map(), errors = [];
  for (const directory of dataDirectory === builtInStyleData ? [builtInStyleData] : [builtInStyleData, dataDirectory]) {
    for (const namespace of await entries(directory)) {
      if (!namespace.isDirectory()) continue;
      for (const file of await jsonFiles(path.join(directory, namespace.name, "dialogue_styles"))) {
        try {
          effective.set(path.relative(directory, file).replaceAll("\\", "/"), [file, JSON.parse((await readFile(file, "utf8")).replace(/^\uFEFF/, ""))]);
        } catch (error) {
          if (!(error instanceof SyntaxError)) throw error;
          errors.push(file + ": invalid JSON: " + error.message);
        }
      }
    }
  }
  const compiled = compileStyleDocuments([...effective.values()]);
  return { styles: compiled.styles, errors: [...errors, ...compiled.errors] };
}
