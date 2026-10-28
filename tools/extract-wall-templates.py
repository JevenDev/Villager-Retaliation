import argparse
import gzip
import io
import pathlib
import struct
import zlib

PARTS = {
    "stone_tower_low": ((104, -60, 108), (108, -53, 112)),
    "stone_tower_tall": ((147, -60, 108), (151, -50, 112)),
    "stone_span": ((152, -60, 108), (165, -54, 112)),
    "timber_span": ((172, -60, 106), (185, -52, 113)),
}


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.stream.read(struct.calcsize(fmt)))[0]

    def string(self):
        return self.stream.read(self.number("H")).decode("utf-8")

    def payload(self, kind):
        if 1 <= kind <= 6:
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[kind])
        if kind == 8:
            return self.string()
        if kind == 9:
            child, count = self.number("B"), self.number("i")
            return [self.payload(child) for _ in range(count)]
        if kind == 10:
            result = {}
            while (child := self.number("B")) != 0:
                name = self.string()
                result[name] = self.payload(child)
            return result
        if kind in (7, 11, 12):
            fmt = {7: "b", 11: "i", 12: "q"}[kind]
            return [self.number(fmt) for _ in range(self.number("i"))]
        raise ValueError(f"Unsupported NBT type {kind}")

    def root(self):
        kind = self.number("B")
        self.string()
        return self.payload(kind)


def string(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def payload(kind, value):
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 8:
        return string(value)
    if kind == 9:
        child, values = value
        return bytes([child]) + struct.pack(">i", len(values)) + b"".join(payload(child, item) for item in values)
    if kind == 10:
        return b"".join(bytes([child]) + string(name) + payload(child, item)
                        for name, (child, item) in value.items()) + b"\0"
    raise ValueError(f"Unsupported output type {kind}")


def chunk(world, x, z, regions):
    region = (x // 32, z // 32)
    if region not in regions:
        regions[region] = (world / "region" / f"r.{region[0]}.{region[1]}.mca").read_bytes()
    data = regions[region]
    slot = (x % 32 + z % 32 * 32) * 4
    offset = int.from_bytes(data[slot:slot + 3], "big") * 4096
    if not offset:
        raise ValueError(f"Source chunk {x}, {z} is missing")
    size = int.from_bytes(data[offset:offset + 4], "big")
    codec = data[offset + 4]
    raw = data[offset + 5:offset + 4 + size]
    if codec == 1:
        raw = gzip.decompress(raw)
    elif codec == 2:
        raw = zlib.decompress(raw)
    elif codec != 3:
        raise ValueError(f"Unsupported region compression {codec}")
    return NbtReader(raw).root()


def extract(world, output):
    regions, chunks = {}, {}
    version = NbtReader(gzip.decompress((world / "level.dat").read_bytes())).root()["Data"]["DataVersion"]
    output.mkdir(parents=True, exist_ok=True)
    for name, (lower, upper) in PARTS.items():
        palette, lookup, blocks = [], {}, []
        for y in range(lower[1], upper[1] + 1):
            for z in range(lower[2], upper[2] + 1):
                for x in range(lower[0], upper[0] + 1):
                    key = (x // 16, z // 16)
                    if key not in chunks:
                        root = chunk(world, *key, regions)
                        if root.get("block_entities"):
                            for entity in root["block_entities"]:
                                if all(lower[i] <= entity[axis] <= upper[i] for i, axis in enumerate(("x", "y", "z"))):
                                    raise ValueError("Wall extraction does not support block entities")
                        chunks[key] = {section["Y"]: section.get("block_states", {}) for section in root["sections"]}
                    states = chunks[key].get(y // 16, {})
                    source_palette = states.get("palette", [{"Name": "minecraft:air"}])
                    index = (y % 16) * 256 + (z % 16) * 16 + x % 16
                    bits = max(4, (len(source_palette) - 1).bit_length())
                    per = 64 // bits
                    state = source_palette[0 if len(source_palette) == 1 else
                                           (states["data"][index // per] >> (index % per * bits)) & ((1 << bits) - 1)]
                    signature = (state["Name"], tuple(sorted(state.get("Properties", {}).items())))
                    if signature not in lookup:
                        lookup[signature] = len(palette)
                        entry = {"Name": (8, state["Name"])}
                        if "Properties" in state:
                            entry["Properties"] = (10, {k: (8, v) for k, v in state["Properties"].items()})
                        palette.append(entry)
                    blocks.append({"pos": (9, (3, [x - lower[0], y - lower[1], z - lower[2]])),
                                   "state": (3, lookup[signature])})
        root = {
            "DataVersion": (3, version),
            "size": (9, (3, [upper[i] - lower[i] + 1 for i in range(3)])),
            "palette": (9, (10, palette)),
            "blocks": (9, (10, blocks)),
            "entities": (9, (10, [])),
            "wall_source": (10, {"world": (8, "VR Structure Design"),
                                "min": (9, (3, list(lower))), "max": (9, (3, list(upper)))}),
        }
        raw = b"\x0a\0\0" + payload(10, root)
        destination = output / (name + ".nbt")
        destination.write_bytes(gzip.compress(raw, mtime=0))
        print(f"{name}: {len(blocks)} cells, {len(palette)} states, {lower} to {upper}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("world", type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path(
        "neoforge/src/main/resources/data/villagerretaliation/structure/walls"))
    args = parser.parse_args()
    extract(args.world, args.output)
