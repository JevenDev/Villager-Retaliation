const groups = [
  ["i", "italic", "italics"], ["b", "bold"], ["u", "underline", "underlined"],
  ["s", "strike", "strikethrough"], ["obfuscated", "obfuscate", "magic"],
  ["wave", "wavy"], ["shake", "shaky"], ["pulse", "pulsing"],
  ["jump", "jumping", "bounce", "bouncy"], ["rainbow"], ["color"], ["gradient"],
  ...["black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
    "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "purple", "magenta", "grey", "dark_grey"].map(value => [value])
];
const tags = new Map(groups.flatMap(group => group.map(tag => [tag, group[0]])));

const cueTags = new Set(["pause", "speed", "look", "agree", "disagree", "reset", "particle", "sound"]);

function validCue(raw) {
  const normalized = raw.trim().toLowerCase();
  if (normalized.split(/[=:]/)[0].trim() === "sound") {
    const [resource, ...arguments_] = normalized.split(/\s+/);
    if (!/^sound[=:](?:[a-z0-9_.-]+:)?[a-z0-9/._-]+$/.test(resource)) return false;
    const seen = new Set();
    return arguments_.every(argument => {
      const match = /^(volume|pitch)[=:]([0-9]+(?:\.[0-9]+)?)$/.exec(argument);
      if (!match || seen.has(match[1])) return false;
      seen.add(match[1]);
      const value = Number(match[2]);
      return Number.isFinite(value) && (match[1] === "volume" ? value >= 0 && value <= 1 : value >= 0.5 && value <= 2);
    });
  }
  if (normalized.split(/[=:]/)[0].trim() === "particle") {
    const match = /^particle[=:]([a-z0-9_.-]+(?::[a-z0-9/._-]+)?)(?:\s+amount[=:]([0-9]+))?$/.exec(normalized);
    if (!match) return false;
    const amount = match[2] === undefined ? 1 : Number(match[2]);
    return Number.isInteger(amount) && amount >= 1 && amount <= 64;
  }
  if (/^look[=:](?:nearest_participant|role:[a-z0-9][a-z0-9_./-]{0,255})$/.test(normalized)) return true;
  const parts = normalized.split(/[=:]/).map(part => part.trim());
  const [name, argument] = parts;
  if (["agree", "disagree", "reset"].includes(name)) return parts.length === 1;
  if (parts.length !== 2) return false;
  if (name === "look") return ["left", "right", "up", "down", "player"].includes(argument);
  if (!/^[0-9]+(?:\.[0-9]+)?$/.test(argument)) return false;
  const value = Number(argument);
  return Number.isFinite(value) && (name === "pause"
    ? Number.isInteger(value) && value >= 0 && value <= 60000 : value >= 0.1 && value <= 10);
}

export function reservedDialogueTag(tag) {
  return tags.has(tag) || cueTags.has(tag) || /^[a-f0-9]{6}$/.test(tag);
}

export function markupWarnings(text, styles = new Map()) {
  const warnings = [];
  const stack = [];
  const priorSpeeds = [];
  let speed = 1;
  let cueCount = 0;
  for (const match of text.matchAll(/<(\/?)([a-zA-Z_][^<>]*)>/g)) {
    const raw = match[2].trim();
    const cueName = raw.toLowerCase().split(/[=:]/)[0].trim();
    if (cueTags.has(cueName)) {
      if (match[1] || !validCue(raw)) warnings.push("invalid dialogue cue: " + match[0]);
      else {
        if (cueName === "speed") speed = Number(raw.split(/[=:]/)[1].trim());
        if (++cueCount > 64) warnings.push("too many dialogue cues");
      }
      continue;
    }
    const tag = raw.split(":")[0].toLowerCase();
    const canonical = tags.get(tag) ?? (styles.has(tag) ? tag : undefined);
    if (!canonical) {
      warnings.push("unknown text tag: " + match[0]);
    } else if (!match[1]) {
      const style = styles.get(tag);
      priorSpeeds.push(style ? speed : null);
      if (style?.speed !== undefined && speed !== style.speed) {
        speed = style.speed;
        if (++cueCount > 64) warnings.push("too many dialogue cues");
      }
      stack.push(canonical);
    } else if (stack.at(-1) !== canonical) {
      warnings.push("unmatched closing text tag: " + match[0]);
    } else {
      stack.pop();
      const previous = priorSpeeds.pop();
      if (previous !== null && speed !== previous) {
        speed = previous;
        if (++cueCount > 64) warnings.push("too many dialogue cues");
      }
    }
  }
  for (const tag of stack) warnings.push("unclosed text tag: " + tag);
  return warnings;
}

export function inspectDialogueMarkup(value, pointer = "", text = false, styles = new Map()) {
  if (typeof value === "string") return text ? markupWarnings(value, styles).map(warning => pointer + ": " + warning) : [];
  if (Array.isArray(value)) return value.flatMap((child, index) => inspectDialogueMarkup(child, pointer + "/" + index, text, styles));
  if (value && typeof value === "object") return Object.entries(value).flatMap(([key, child]) =>
    inspectDialogueMarkup(child, pointer + "/" + key, ["text", "lines", "label", "unavailable", "response", "responses"].includes(key), styles));
  return [];
}
