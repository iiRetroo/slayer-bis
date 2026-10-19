#!/usr/bin/env python3
"""Refresh SlayerBIS's bundled item and monster stats from the OSRS Wiki DPS calculator.

Downloads equipment.json, equipment_aliases.json and monsters.json from
https://github.com/weirdgloop/osrs-dps-calc (cdn/json) and rewrites the compact copies in
src/main/resources/com/slayerbis/. The plugin itself never downloads anything; this script is
only for maintainers, run by hand before a release.

Usage (from the repository root):
    python scripts/refresh_data.py            # latest commit on the default branch
    python scripts/refresh_data.py <commit>   # a specific calculator commit

Afterwards run ./gradlew build: the tests check the data still works, including the loadouts
verified against the wiki calculator. DATA-NOTICE.txt is updated automatically.
"""

import datetime
import json
import os
import sys
import urllib.request

REPO = "weirdgloop/osrs-dps-calc"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "com", "slayerbis")


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "slayerbis-data-refresh"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read().decode("utf-8")


def resolve_commit(ref):
    if ref is None:
        ref = json.loads(fetch("https://api.github.com/repos/%s" % REPO))["default_branch"]
    data = json.loads(fetch("https://api.github.com/repos/%s/commits/%s" % (REPO, ref)))
    return data["sha"]


def compact_items(equipment, aliases):
    items = []
    for x in equipment:
        o, b = x["offensive"], x["bonuses"]
        category = x["category"] or ""
        if category == "blunt":
            category = "Blunt"
        items.append([x["id"], x["name"], x["version"] or "", x["slot"], x["speed"] or 0, category,
                      1 if x["isTwoHanded"] else 0,
                      o["stab"] or 0, o["slash"] or 0, o["crush"] or 0, o["magic"] or 0, o["ranged"] or 0,
                      b["str"] or 0, b["ranged_str"] or 0, b["magic_str"] or 0])
    return {"items": items, "aliases": {int(k): v for k, v in aliases.items()}}


def compact_monsters(monsters):
    out = []
    for m in monsters:
        s, d, off = m["skills"], m["defensive"], m["offensive"]
        w = m.get("weakness") or {}
        out.append([m["id"], m["name"], m["version"] or "", m["size"] or 1,
                    s["hp"] or 0, s["def"] or 0, s["magic"] or 0, off.get("magic") or 0,
                    d["stab"] or 0, d["slash"] or 0, d["crush"] or 0, d["magic"] or 0,
                    d["light"] or 0, d["standard"] or 0, d["heavy"] or 0, d.get("flat_armour") or 0,
                    m["attributes"], 1 if m["is_slayer_monster"] else 0,
                    w.get("element") or "", w.get("severity") or 0])
    return out


def write_json(name, value):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(value, f, separators=(",", ":"), ensure_ascii=False)
    print("wrote %s (%d bytes)" % (path, os.path.getsize(path)))


def update_notice(commit):
    path = os.path.join(OUT_DIR, "DATA-NOTICE.txt")
    with open(path, encoding="utf-8") as f:
        lines = [l for l in f.read().splitlines() if not l.startswith("Generated from ")]
    lines.append("Generated from %s commit %s on %s." % (REPO, commit, datetime.date.today().isoformat()))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def main():
    ref = sys.argv[1] if len(sys.argv) > 1 else None
    commit = resolve_commit(ref)
    base = "https://raw.githubusercontent.com/%s/%s/cdn/json/" % (REPO, commit)
    print("using %s@%s" % (REPO, commit))
    equipment = json.loads(fetch(base + "equipment.json"))
    aliases = json.loads(fetch(base + "equipment_aliases.json"))
    monsters = json.loads(fetch(base + "monsters.json"))
    write_json("equipment.json", compact_items(equipment, aliases))
    write_json("monsters.json", compact_monsters(monsters))
    update_notice(commit)
    print("done: %d items, %d monsters. Now run ./gradlew build." % (len(equipment), len(monsters)))


if __name__ == "__main__":
    main()
