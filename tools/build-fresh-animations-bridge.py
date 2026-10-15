import argparse
import json
from pathlib import Path
import zipfile


VERSION = "1.1.3"
ANIMATIONS = "assets/villagerretaliation/optifine/cem/villager_animations.jpm"
BASE_ANIMATIONS = "assets/minecraft/optifine/cem/villager_animations.jpm"


def encode(data):
    return (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8")


def update_animations(data, base):
    groups = data["animations"]
    original = groups[0]
    state = {"varb.pfc": original["varb.pfc"]}
    state.update({
        "varb.vr_downed": "equals(body.ty,10,0.01)||equals(body.ty,12.5,0.01)||root.ty>17",
        "varb.vr_studying": "equals(root.tx,0.01,0.001)",
        "varb.vr_body_swing": "equals(root.tx,0.02,0.001)",
        "varb.vr_item_use": "equals(root.tx,0.03,0.001)",
        "varb.vr_repair": "equals(root.tx,0.04,0.001)",
        "varb.vr_sleeping": "equals(root.tx,0.05,0.001)",
        "varb.vr_locked_pose": "varb.vr_downed||varb.vr_studying||varb.vr_repair||varb.vr_sleeping||is_riding",
    })
    for part in ("head", "body", "RightArm", "LeftArm", "RightLeg", "LeftLeg", "arms"):
        for axis in ("rx", "ry", "rz", "tx", "ty", "tz"):
            variable = f"var.vr_{part.lower()}_{axis}"
            state[variable] = f"if(varb.pfc,{variable},{part}.{axis})"
    state.update({key: value for key, value in original.items()
                  if key != "varb.pfc" and not key.startswith(("var.vr_", "varb.vr_"))})
    groups[0] = state
    groups[0]["var.nope0"] = f"if(varb.vr_locked_pose,0,({base['animations'][0]['var.nope0']}))"

    arms = base["animations"][2]
    groups[2] = {key: value for key, value in arms.items() if key.startswith("arms.")}
    groups[2]["arms.ty"] = "2+body.ty"
    groups[2]["arms.tz"] = "body.tz"
    groups[2]["arms.rx"] = f"({arms['arms.rx']})-pi/16"
    groups[4]["nose.rx"] = base["animations"][4]["nose2.rx"]
    groups[4]["nose.rz"] = base["animations"][4]["nose2.rz"]
    groups[5]["var.blink"] = f"if(varb.vr_sleeping,1,({base['animations'][5]['var.blink']}))"

    for group in groups[1:]:
        for key, expression in list(group.items()):
            if key.count(".") != 1:
                continue
            part, axis = key.split(".")
            if part not in ("head", "body", "RightArm", "LeftArm", "RightLeg", "LeftLeg", "arms"):
                continue
            if axis not in ("rx", "ry", "rz", "tx", "ty", "tz"):
                continue
            variable = f"var.vr_{part.lower()}_{axis}"
            condition = "varb.vr_locked_pose"
            if part == "head" and axis.startswith("r"):
                condition += "||varb.vr_item_use"
            elif part == "body" and axis == "ry":
                condition += "||varb.vr_body_swing"
            elif part in ("RightArm", "LeftArm"):
                if axis == "tx":
                    condition += "||varb.vr_body_swing"
                elif axis == "ty":
                    expression = "2+body.ty"
                elif axis == "tz":
                    expression = f"if(varb.vr_body_swing,{variable}+body.tz,body.tz)"
            group[key] = f"if({condition},{variable},({expression}))"

    for group in groups:
        for key, expression in group.items():
            group[key] = expression.replace("is_in_water", "(is_in_water||is_in_lava)")
    return data


def build(bridge, fresh_animations, output, version=VERSION):
    if output.resolve() in (bridge.resolve(), fresh_animations.resolve()):
        raise ValueError("Output must be a new ZIP, separate from the input packs")
    if output.exists():
        raise FileExistsError(output)
    with zipfile.ZipFile(bridge) as source, zipfile.ZipFile(fresh_animations) as fresh:
        animation = update_animations(json.loads(source.read(ANIMATIONS)),
                                      json.loads(fresh.read(BASE_ANIMATIONS)))
        metadata = json.loads(source.read("pack.mcmeta"))
        metadata["pack"]["description"] = f"v{version} Villager Retaliation x Fresh Animations"
        entries = {entry.filename: source.read(entry) for entry in source.infolist() if not entry.is_dir()}
    for name in list(entries):
        if name.endswith("/villager_animations.jpm"):
            entries[name] = encode(animation)
    entries["pack.mcmeta"] = encode(metadata)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "x", compression=zipfile.ZIP_DEFLATED) as target:
        for name, content in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, date_time=(2026, 10, 15, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            target.writestr(entry, content)
    return output


def main():
    parser = argparse.ArgumentParser(description="Build the VR Fresh Animations bridge from existing bridge and base packs.")
    parser.add_argument("bridge", type=Path)
    parser.add_argument("fresh_animations", type=Path)
    parser.add_argument("--version", default=VERSION)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    output = args.output or Path("build/resourcepacks") / f"VillagerRetaliationXFreshAnimations_v{args.version}.zip"
    print(build(args.bridge, args.fresh_animations, output, args.version))


if __name__ == "__main__":
    main()
