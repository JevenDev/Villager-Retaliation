import { readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const builtInData = fileURLToPath(new URL("../neoforge/src/main/resources/data", import.meta.url));
const reference = /^\{word:([a-z0-9_.-]+:[a-z0-9/._-]+)}$/;
const token = /\{word(?=[:}\s]|$)[^{}]*(?:}|(?=\{|$))/g;
const poolPath = /(?:^|\/)data\/([^/]+)\/(?:(world_campaigns|campaign_addons)\/([^/]+)\/)?dialogue_words\/([^/]+)\/(.+)\.json$/;
const normalizeLocale = value => value.toLowerCase().replaceAll("-", "_");

export function wordPoolIssues(value) {
  if (!value || !Array.isArray(value.values) || value.values.length === 0)
    return ["values must be a nonempty array of literal strings"];
  return value.values.flatMap((text, index) => typeof text !== "string" || !text.trim() || text.length > 512
    || /[{}<>\u00a7\u0000-\u001f\u007f-\u009f]/.test(text)
    ? [`values/${index}: expected nonblank literal text without placeholders, markup, or control characters, at most 512 characters`] : []);
}

export function wordReferences(text) {
  return [...text.matchAll(token)].map(match => ({ token: match[0], id: reference.exec(match[0])?.[1] }));
}

export function validateWordDocuments(documents) {
  const errors = [];
  const pools = new Set();
  for (const [file, value] of documents) {
    const match = poolPath.exec(file.replaceAll("\\", "/"));
    if (!match) {
      if (file.replaceAll("\\", "/").includes("/dialogue_words/")) errors.push(`${file}: expected dialogue_words/<locale>/<pool>.json`);
      continue;
    }
    if (!/^[a-z0-9_.-]+:[a-z0-9/._-]+$/.test(`${match[1]}:${match[5]}`)) {
      errors.push(`${file}: invalid word pool resource ID`);
      continue;
    }
    const issues = wordPoolIssues(value);
    errors.push(...issues.map(issue => `${file}: ${issue}`));
    if (!issues.length) pools.add(`${match[2] ? match[2] + "/" + match[3] : ""}|${normalizeLocale(match[4])}|${match[1]}:${match[5]}`);
  }
  for (const [file, value] of documents) {
    const normalized = file.replaceAll("\\", "/");
    if (poolPath.test(normalized)) continue;
    const scope = /\/(world_campaigns|campaign_addons)\/([^/]+)\//.exec(normalized);
    const scopeKey = scope ? scope[1] + "/" + scope[2] : "";
    const locale = normalizeLocale(/\/(?:dialogue|dialogue_trees|paired_dialogue|paired_greetings)\/([^/]+)\//.exec(normalized)?.[1]
      ?? /\/locales\/([^/]+)\.json$/.exec(normalized)?.[1] ?? "en_us");
    function walk(child, pointer) {
      if (typeof child === "string") {
        for (const entry of wordReferences(child)) {
          if (!entry.id) errors.push(`${file}${pointer}: malformed word pool reference ${entry.token}`);
          else if (![locale, "en_us"].some(language => [scopeKey, ""].some(owner => pools.has(`${owner}|${language}|${entry.id}`))))
            errors.push(`${file}${pointer}: missing word pool ${entry.id} for ${locale}`);
        }
      } else if (Array.isArray(child)) child.forEach((entry, index) => walk(entry, pointer + "/" + index));
      else if (child && typeof child === "object") Object.entries(child).forEach(([key, entry]) => walk(entry, pointer + "/" + key));
    }
    walk(value, "");
  }
  return errors;
}

async function jsonFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true }).catch(error => {
    if (error.code === "ENOENT") return [];
    throw error;
  });
  return (await Promise.all(entries.map(entry => entry.isDirectory() ? jsonFiles(path.join(directory, entry.name))
    : entry.name.endsWith(".json") ? [path.join(directory, entry.name)] : []))).flat();
}

export async function validateWordDirectory(dataDirectory = builtInData, selectedFiles = null) {
  const effective = new Map();
  const errors = [];
  for (const directory of dataDirectory === builtInData ? [builtInData] : [builtInData, dataDirectory]) {
    for (const file of await jsonFiles(directory)) {
      const text = (await readFile(file, "utf8")).replace(/^\uFEFF/, "");
      const isPool = file.replaceAll("\\", "/").includes("/dialogue_words/");
      if (!isPool && (directory !== dataDirectory || selectedFiles && !selectedFiles.includes(file) || !text.includes("{word"))) continue;
      try {
        effective.set(path.relative(directory, file).replaceAll("\\", "/"), [file, JSON.parse(text)]);
      } catch (error) {
        if (!(error instanceof SyntaxError)) throw error;
        errors.push(`${file}: invalid JSON: ${error.message}`);
      }
    }
  }
  return [...errors, ...validateWordDocuments([...effective.values()])];
}
