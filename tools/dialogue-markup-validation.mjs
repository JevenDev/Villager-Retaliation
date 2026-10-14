const groups = [
  ["i", "italic", "italics"], ["b", "bold"], ["u", "underline", "underlined"],
  ["s", "strike", "strikethrough"], ["obfuscated", "obfuscate", "magic"],
  ["wave", "wavy"], ["shake", "shaky"], ["pulse", "pulsing"],
  ["jump", "jumping", "bounce", "bouncy"], ["rainbow"], ["color"], ["gradient"],
  ...["black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
    "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "purple", "magenta", "grey", "dark_grey"].map(value => [value])
];
const tags = new Map(groups.flatMap(group => group.map(tag => [tag, group[0]])));

export function markupWarnings(text) {
  const warnings = [];
  const stack = [];
  for (const match of text.matchAll(/<(\/?)([a-zA-Z_][^<>]*)>/g)) {
    const tag = match[2].trim().split(":")[0].toLowerCase();
    const canonical = tags.get(tag);
    if (!canonical) {
      warnings.push("unknown text tag: " + match[0]);
    } else if (!match[1]) {
      stack.push(canonical);
    } else if (stack.at(-1) !== canonical) {
      warnings.push("unmatched closing text tag: " + match[0]);
    } else stack.pop();
  }
  for (const tag of stack) warnings.push("unclosed text tag: " + tag);
  return warnings;
}

export function inspectDialogueMarkup(value, pointer = "", text = false) {
  if (typeof value === "string") return text ? markupWarnings(value).map(warning => pointer + ": " + warning) : [];
  if (Array.isArray(value)) return value.flatMap((child, index) => inspectDialogueMarkup(child, pointer + "/" + index, text));
  if (value && typeof value === "object") return Object.entries(value).flatMap(([key, child]) =>
    inspectDialogueMarkup(child, pointer + "/" + key, ["text", "lines", "label", "unavailable", "response", "responses"].includes(key)));
  return [];
}
