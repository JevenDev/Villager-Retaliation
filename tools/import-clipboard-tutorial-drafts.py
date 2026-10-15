"""Import reviewed teaching text. Prose checks remain design notes, never executable objectives."""
import argparse
import hashlib
import json
import re
from pathlib import Path

EXPECTED = "54954eef6850d1693d6ef07cace9d14600f62a5eec694413ff4b2804b5ee106f"
ROLES = {
    "Farmer": "farming", "Fisher": "fishing", "Miner": "mining", "Logger": "logging",
    "Hunter": "hunting", "Animal Handler": "animal_handling", "Brewer": "brewing",
    "Craftsman": "craftsman", "Cook": "cook", "Smelter": "smelter", "Courier": "courier",
    "Nitwit": "nitwit", "Guard": "combat", "Builder": "builder",
}


def clean(text):
    return text.strip().strip('"\u201c\u201d').strip()


def hints(text):
    # These handlers use physical modifier checks, not the configurable Sneak action.
    for key in ("Control", "Shift", "Alt"):
        text = re.sub(r"(?<![\w{])" + key + r"(?![\w}])", "{" + key + "Modifier}", text)
    return text


def main():
    parser = argparse.ArgumentParser(__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--destination", type=Path,
                        default=Path("neoforge/src/main/resources/data/villagerretaliation/clipboard_tutorials"))
    args = parser.parse_args()
    raw = args.source.read_bytes()
    if hashlib.sha256(raw).hexdigest() != EXPECTED:
        raise SystemExit("Refusing unreviewed draft bytes: expected authoritative Markdown version 1")
    text = raw.decode("utf-8")
    records = {}
    for section in re.split(r"^## ", text, flags=re.M)[1:]:
        title = section.splitlines()[0].strip()
        display = next((name for name in ROLES if title.startswith(name + " ")), None)
        if display is None:
            continue
        role = ROLES[display]
        steps = []
        for index, step in enumerate(re.split(r"^### ", section, flags=re.M)[1:]):
            heading = step.splitlines()[0].strip()
            sequences = []
            pattern = r"\*\*Teach and demonstrate \(spoken in order\):\*\*\s*\n(.*?)(?=\n\*\*Show / confirm:)"
            for seq_index, match in enumerate(re.finditer(pattern, step, flags=re.S)):
                cards = [hints(clean(line)) for line in re.findall(r"^\d+\.\s+(.+)$", match.group(1), flags=re.M)]
                tail = step[match.end():]
                show = re.search(r"\*\*Show / confirm:\*\*\s*(.+)", tail)
                recovery = re.search(r"\*\*If it goes wrong:\*\*\s*(.+)", tail)
                if not cards or show is None or recovery is None:
                    raise ValueError(f"Incomplete teaching sequence: {display}/{heading}")
                sequences.append({
                    "id": f"step_{index + 1}_sequence_{seq_index + 1}",
                    "cards": [{"id": f"card_{i + 1}", "text": card} for i, card in enumerate(cards)],
                    "confirmation_guidance": hints(clean(show.group(1))),
                    "recovery": hints(clean(recovery.group(1))),
                    "repeat_label": "Show me again",
                })
            def field(label):
                match = re.search(r"\*\*" + re.escape(label) + r":\*\*\s*(.+)", step)
                if match is None:
                    match = re.search(r"^" + re.escape(label) + r":\s*(.+)", step, flags=re.M)
                return match.group(1).strip() if match else ""
            steps.append({"id": f"step_{index + 1}", "title": heading, "sequences": sequences,
                          "design_objective": field("Objective") or field("Task"),
                          "design_check": field("Check") or field("Proof"),
                          "design_provisions": field("Provides") or field("Give/check"),
                          "design_hook": field("Hook"), "design_notes": step.strip()})
        records[role] = {
            "schema": "villagerretaliation:clipboard_tutorial_script/v1",
            "id": f"villagerretaliation:clipboard_{role}", "role": role, "title": title,
            "credit_policy": "personal", "setup_policy": "recognize_existing",
            "outcome_policy": "committed_since_binding", "completion_offer": "normal_contract_or_stop",
            "release_status": "requires_runtime_integration",
            "design_overview": section.split("\n### ", 1)[0].strip(),
            "source": {"library_file_id": "libfile_653e635c42288191b82fc7baeb8cbf16", "version": 1,
                       "sha256": EXPECTED, "baseline": "fd35988257b0b95685a9f12e30eece966d6210d4"},
            "steps": steps,
        }
    if set(records) != set(ROLES.values()):
        raise ValueError("Expected all fourteen personal job scripts")
    total = sum(len(step["sequences"]) for lesson in records.values() for step in lesson["steps"])
    shared = re.search(r'\*\*Supplies/deposit readiness card \(after the first chest pair\):\*\* ["\u201c](.+?)["\u201d]', text)
    if total != 81 or shared is None:
        raise ValueError(f"Expected 81 role sequences plus the shared readiness sequence, got {total}")
    args.destination.mkdir(parents=True, exist_ok=True)
    for role, record in records.items():
        (args.destination / (role + ".json")).write_text(json.dumps(record, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    common = {"schema": "villagerretaliation:clipboard_tutorial_script/v1", "id": "villagerretaliation:clipboard/shared",
              "release_status": "requires_runtime_integration", "sequence": {
                  "id": "supplies_deposit_readiness", "cards": [
                      {"id": f"card_{i + 1}", "text": hints(line)}
                      for i, line in enumerate(re.split(r'(?<=\.)\s+', shared.group(1)))],
                  "repeat_label": "Show me again",
                  "recovery": "Read the actual on/off states. If the work report cannot show them, pause the lesson."}}
    (args.destination / "shared.json").write_text(json.dumps(common, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Imported {len(records)} scripts, {total + 1} sequences; runtime release remains gated")


if __name__ == "__main__":
    main()
