#!/usr/bin/env python3
"""Refresh SlayerBIS's bundled item, monster and drop data.

Downloads equipment.json, equipment_aliases.json and monsters.json from
https://github.com/weirdgloop/osrs-dps-calc (cdn/json), and monster drop tables from the OSRS
Wiki's public API, and rewrites the compact copies in src/main/resources/com/slayerbis/. The
plugin itself never downloads anything; this script is only for maintainers, run by hand before
a release.

Usage (from the repository root):
    python scripts/refresh_data.py            # latest commit on the default branch
    python scripts/refresh_data.py <commit>   # a specific calculator commit
    python scripts/refresh_data.py --weights  # only refresh item weights (weights.json)
    python scripts/refresh_data.py --drops    # only refresh drop tables (drops.json)

Afterwards run ./gradlew build: the tests check the data still works, including the loadouts
verified against the wiki calculator. DATA-NOTICE.txt is updated automatically.
"""

import datetime
import json
import os
import sys
import time
import urllib.parse
import urllib.request

REPO = "weirdgloop/osrs-dps-calc"
WIKI_API = "https://oldschool.runescape.wiki/api.php"
USER_AGENT = "slayerbis-data-refresh (https://github.com/iiRetroo/slayer-bis)"

# Loot types are stored with how many kills, on average, it takes to get one item of that type.
# The plugin decides what counts as common (its "Loot tools" setting); anything rarer than this
# is dropped from the file entirely as noise.
LOOT_MAX_KILLS = 5000
# Equipment worth at least this much (GE value from the wiki) counts as worth alching.
ALCH_MIN_VALUE = 2000
# Loot types whose tool only helps with unnoted drops.
NOTED_NEEDS_NO_TOOL = {"herbs", "gems", "bones", "ashes", "logs"}
DEMONIC_ASHES = {"Fiendish ashes", "Vile ashes", "Malicious ashes", "Abyssal ashes", "Infernal ashes"}
WORD_RARITY = {"always": 1.0, "common": 1 / 8, "uncommon": 1 / 32, "rare": 1 / 128, "very rare": 1 / 1024}
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "com", "slayerbis")


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
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


def parse_rarity(text):
    """Chance per roll from wiki rarity text such as "Always", "4/128", "~1/100" or "Rare"."""
    t = (text or "").strip().lower().lstrip("~").replace(",", "")
    if t in WORD_RARITY:
        return WORD_RARITY[t]
    if "/" in t:
        num, _, den = t.partition("/")
        try:
            return float(num) / float(den)
        except ValueError:
            return 0.0
    return 0.0


def loot_type(item, value, equipment_names):
    if item.startswith("Grimy "):
        return "herbs"
    if item.endswith(" seed") or item.endswith(" seeds"):
        return "seeds"
    if item.startswith("Uncut "):
        return "gems"
    if item in DEMONIC_ASHES:
        return "ashes"
    if item == "Bones" or item.endswith(" bones"):
        return "bones"
    if item == "Logs" or item.endswith(" logs"):
        return "logs"
    if (item in equipment_names or item.endswith("battlestaff")) and value >= ALCH_MIN_VALUE:
        return "alchables"
    return None


def fetch_drops(monster_names, equipment_names):
    """For each monster, loot type -> average kills per item of that type, from the wiki's dropsline table."""
    # the wiki's page title can differ in case from the calculator's monster name
    by_lower = {n.lower(): n for n in monster_names}
    chance = {}
    offset = 0
    while True:
        query = ("bucket('dropsline').select('page_name','item_name','drop_json')"
                 ".limit(5000).offset(%d).run()" % offset)
        url = WIKI_API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
        rows = json.loads(fetch(url)).get("bucket", [])
        for row in rows:
            name = by_lower.get((row.get("page_name") or "").lower())
            if name is None:
                continue
            try:
                drop = json.loads(row.get("drop_json") or "{}")
            except ValueError:
                continue
            if drop.get("Drop type") != "combat":
                continue
            item = row.get("item_name") or ""
            quantity = max(1, (drop.get("Quantity Low") or 1))
            value = (drop.get("Drop Value") or 0) / quantity
            kind = loot_type(item, value, equipment_names)
            # noted drops stack in one slot, so they need no sack, bag, basket or crusher
            if kind in NOTED_NEEDS_NO_TOOL and "noted" in str(drop.get("Drop Quantity") or "").lower():
                kind = None
            if kind:
                per_kill = parse_rarity(drop.get("Rarity")) * (drop.get("Rolls") or 1)
                key = (name, kind)
                chance[key] = chance.get(key, 0.0) + per_kill
        print("drops: read %d rows" % (offset + len(rows)))
        if len(rows) < 5000:
            break
        offset += 5000
        time.sleep(1)  # be polite to the wiki
    drops = {}
    for (name, kind), c in chance.items():
        if c > 0 and 1.0 / c <= LOOT_MAX_KILLS:
            drops.setdefault(name, {})[kind] = max(1, int(round(1.0 / c)))
    return {name: dict(sorted(kinds.items())) for name, kinds in sorted(drops.items())}


def fetch_weights(item_ids):
    """Item id -> weight in grams (can be negative, e.g. graceful), from the wiki's infobox_item table."""
    wanted = set(item_ids)
    weights = {}
    offset = 0
    while True:
        query = ("bucket('infobox_item').select('item_id','weight')"
                 ".limit(5000).offset(%d).run()" % offset)
        url = WIKI_API + "?" + urllib.parse.urlencode({"action": "bucket", "format": "json", "query": query})
        rows = json.loads(fetch(url)).get("bucket", [])
        for row in rows:
            weight = row.get("weight")
            if not isinstance(weight, (int, float)) or weight == 0:
                continue
            for raw in row.get("item_id") or []:
                try:
                    item_id = int(raw)
                except (TypeError, ValueError):
                    continue
                if item_id in wanted:
                    weights[str(item_id)] = int(round(weight * 1000))
        print("weights: read %d rows" % (offset + len(rows)))
        if len(rows) < 5000:
            break
        offset += 5000
        time.sleep(1)  # be polite to the wiki
    return dict(sorted(weights.items(), key=lambda kv: int(kv[0])))


def write_json(name, value):
    path = os.path.join(OUT_DIR, name)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(value, f, separators=(",", ":"), ensure_ascii=False)
    print("wrote %s (%d bytes)" % (path, os.path.getsize(path)))


def update_notice(commit):
    path = os.path.join(OUT_DIR, "DATA-NOTICE.txt")
    with open(path, encoding="utf-8") as f:
        lines = [l for l in f.read().splitlines() if not l.startswith("Generated from ")]
    lines.append("Generated from %s commit %s and OSRS Wiki drop tables on %s."
                 % (REPO, commit, datetime.date.today().isoformat()))
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


def refresh_weights():
    with open(os.path.join(OUT_DIR, "equipment.json"), encoding="utf-8") as f:
        items = json.load(f)["items"]
    weights = fetch_weights(row[0] for row in items)
    write_json("weights.json", weights)
    print("done: weights for %d of %d items." % (len(weights), len(items)))


def refresh_drops():
    with open(os.path.join(OUT_DIR, "equipment.json"), encoding="utf-8") as f:
        equipment_names = {row[1] for row in json.load(f)["items"]}
    with open(os.path.join(OUT_DIR, "monsters.json"), encoding="utf-8") as f:
        monster_names = {row[1] for row in json.load(f)}
    drops = fetch_drops(monster_names, equipment_names)
    write_json("drops.json", drops)
    print("done: drop data for %d monsters." % len(drops))


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "--weights":
        refresh_weights()
        return
    if len(sys.argv) > 1 and sys.argv[1] == "--drops":
        refresh_drops()
        return
    ref = sys.argv[1] if len(sys.argv) > 1 else None
    commit = resolve_commit(ref)
    base = "https://raw.githubusercontent.com/%s/%s/cdn/json/" % (REPO, commit)
    print("using %s@%s" % (REPO, commit))
    equipment = json.loads(fetch(base + "equipment.json"))
    aliases = json.loads(fetch(base + "equipment_aliases.json"))
    monsters = json.loads(fetch(base + "monsters.json"))
    write_json("equipment.json", compact_items(equipment, aliases))
    write_json("monsters.json", compact_monsters(monsters))
    drops = fetch_drops({m["name"] for m in monsters}, {x["name"] for x in equipment})
    write_json("drops.json", drops)
    refresh_weights()
    update_notice(commit)
    print("done: %d items, %d monsters, drop data for %d monsters. Now run ./gradlew build."
          % (len(equipment), len(monsters), len(drops)))


if __name__ == "__main__":
    main()
