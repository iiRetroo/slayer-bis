# SlayerBIS

**Open your bank. Your best Slayer setup is already there.**

SlayerBIS is built for one moment: you've just got a Slayer task and you're standing at the bank. There's nothing to search for and no button to press. It already knows your task, so the moment your bank opens it has the answer:

- **Your best gear for this task, outlined in your bank.** It's picked from everything you own, and it already includes the gear the task needs (a mirror shield for basilisks, boots of stone for Karuulm, a nose peg or Slayer helmet for aberrant spectres).
- **What to bring.** Task items like a rock hammer or bag of salt, antifire where dragons breathe fire, a potion for your best style, prayer potions and your best food, plus tools for what the monster drops: a herb sack for herbs, a rune pouch for alchables, a bonecrusher for bones. Supplies get their own highlight colour.
- **How to get there.** Teleports to the task location, ticked off when you own the item.
- **How much better it is** than what you're wearing right now, and roughly how long the task will take.
- **A checklist while you bank.** Items turn green as they reach your inventory or equipment, until it says "Ready to go". **Show in bank** filters your bank to just the suggested items.

### Why trust the numbers

- **Real DPS maths, not a ranked list.** Every style is compared, with stab, slash and crush kept separate, using the OSRS Wiki DPS calculator's formulas.
- **Bursting counts on stacking tasks.** For dust devils, nechryael, smoke devils and abyssal demons, a Burst tab works out your best Ancient burst or barrage setup, with damage counted across the stack.
- **Every combination is checked.** Outclassed items are set aside first, then every remaining combination of your gear is evaluated, including complete armour sets and two-handed weapons.
- **Checked against the wiki.** The tests include loadouts whose max hit, accuracy and DPS match the live OSRS Wiki DPS calculator exactly.

### Small and safe

- **Read-only.** It never equips, withdraws or moves items, adds no menu options, and never suggests prayers.
- **Offline.** It makes no network requests. All data is bundled.
- **Light.** The search runs in the background at low priority, and eating or drinking mid-fight doesn't restart it.

## What the panel shows

When you open your bank, the plugin reads your banked items (plus, optionally, what you are wearing and carrying). It looks up your task from the RuneLite Slayer plugin and works out, for every combat style (stab, slash, crush, ranged and magic), which combination of your gear deals the most damage per second. The side panel shows:

- the best combat style, with the stance or spell to use, and how much better it is than what you are wearing
- the best set for each style, with its DPS, max hit, accuracy and attack speed
- the best set with each of your other strong weapons, in case you would rather use one of those
- gear the task requires, such as a mirror shield for basilisks or boots of stone for Karuulm, which is kept in every suggested set
- a suggested inventory: task items (rock hammer, bag of salt), antifire or anti-venom where needed, prayer potions or moonlight moth mixes, a boosting potion for your best style, loot tools, a teleport item, and your best food (with karambwans for combo eating on hard-hitting tasks)
- teleports to the task location, ticked off when you own the item
- where to safespot the task's monsters, as short text descriptions
- what was assumed (levels, prayers, potions, spellbook)

The panel is laid out like the game: a tab per style with its DPS (best in green), the set in the worn-equipment layout with bank items outlined, a stats box, and the inventory as item icons with stack counts. By default the inventory shows all 28 slots like the game, ticked off slot by slot as you withdraw; it's tall, so the panel may scroll. The *Inventory view* setting switches to Compact, one tile per item with a stack count. "Other weapons", "Getting there" and "Details" fold away until you need them. On the ranged set, click the ammo slot to compare every ammo you own that the weapon can fire, with its DPS; pick a cheaper one (handy for ironmen or saving money) and the set, stats and bank highlight switch to it. The chosen gear is outlined in your bank in one colour and the suggested supplies in another. While you bank, tiles turn green once an item is on you (amber means it's still in the bank), and a badge counts what's left to withdraw. **Show in bank** uses the core Bank Tags plugin's temporary tag view to show only the suggested items, including other versions of the same item (such as used Blood Moon armour, but not broken pieces); click it again for your full bank. Your saved bank tags are not changed. Use the picker at the top to choose which monster to calculate against (some tasks cover several).

## How the search works

1. **Outclassed gear is removed.** For each style, an item is dropped if another item in the same slot (or leaving the slot empty) is at least as good on every stat that matters for that style. For melee that is the style's attack bonus and melee strength; for ranged, ranged attack and ranged strength; for magic, magic attack and magic damage. Weapons are only compared with weapons of the same type, handedness, attack speed or faster, and ammo use. Items with a special effect that could apply to your target are never dropped this way. Examples are slayer helmets on task, salve amulets against undead, demonbane weapons against demons, and enchanted bolts.
2. **Armour sets count as one candidate.** Complete Void, Elite Void, Obsidian, Inquisitor's, Crystal, Verac's, Karil's, Ahrim's and Virtus sets that you own are tried as single candidates filling all their slots.
3. **Every remaining combination is evaluated.** Two-handed weapons always leave the shield slot empty. Ranged weapons are only paired with ammo they can fire. Items without special effects only affect DPS through their summed stats, and DPS never falls when those sums rise. So among those items, any combination beaten on both summed stats by another combination is skipped. The answer is the same as checking them all, but much faster.

## Task preparation

Task requirements, task items and teleports live in `src/main/resources/com/slayerbis/tasks.json`, and food and potions in `supplies.json`. Both are plain data files with a short format note at the top, so adding a task or fixing a teleport needs no code changes. Supplies are matched by item name, so every potion dose counts.

The inventory plan fills 28 slots: required task items first, then antifire or anti-venom where the task calls for it, prayer potions (5 by default), a boosting potion for your best style, loot tools, a teleport item, and your best food for the rest. The counts are settings. Moving items between bank and inventory does not change the plan, so it stays put while you withdraw.

- **Prayer:** the *Prayer restore* setting picks the order. *Prayer potions first* (the default) brings prayer potions or super restores and tops up with moonlight moth mixes. *Moth mixes first* saves your prayer potions by bringing mixes first. *Prayer potions only* never suggests mixes. Two mixes count as one potion, so 5 prayer potions become 10 mixes. If you have none at all, the panel says so, and the same goes for a missing style potion.
- **Potion storage:** potions in your bank's potion storage (from Mastering Mixology) count as banked, read the same way RuneLite's Bank Tags plugin reads them.
- **Teleports:** one teleport item you own for the task location, such as a slayer ring. Worn teleports (Rada's blessing, Ghommal's hilt) are a tip instead, since they need no inventory slot.
- **Back to your Slayer master:** set *Slayer master* and the plan adds the best teleport you own back to them, such as a necklace of passage for Mortimer or a games necklace for Turael. If an item already in the plan goes there (a slayer ring for Nieve or Mortimer), nothing extra is added. Uncharged jewellery doesn't count.
- **Combo eating:** for tasks that hit hard (metal dragons, wyverns, dark beasts, hydras, bosses and anything with 250+ HP), 4 karambwans to eat in the same tick as your main food.

Loot tools come from the monster's drop table (`drops.json`, built from the OSRS Wiki):

| If the monster drops | You're told to bring (first one you own) |
|---|---|
| Items worth alching | Divine rune pouch or rune pouch (with nature and fire runes), or nature runes |
| Herbs | Open herb sack or herb sack |
| Seeds | Open seed box or seed box |
| Gems | Open gem bag or gem bag |
| Bones | Bonecrusher |
| Demonic ashes | Ash sanctifier |

Task items that are used up on every kill (bags of salt for rockslugs, ice coolers for desert lizards, fishing explosives for mogres) are suggested once per kill left on your task, as one stack. If you own fewer, the panel says how many you're short.

For monsters with lots of HP (250 or more, such as bosses, rune dragons and Cerberus), the plan adds a Defence-lowering special attack weapon you own: Elder maul, Dragon warhammer, Bandos godsword, Arclight or Barrelchest anchor, in that order. The *Spec weapon* setting turns this off. Special attacks aren't part of the DPS figures.

Tasks that are usually cannoned (kalphites, dagannoth, trolls and others) get a tip, plus your cannon and cannonballs if you own a full cannon. Tasks where monsters stack (dust devils, nechryael, smoke devils, abyssal demons) get a bursting tip and your rune pouch. When bursting is the best style, the plan adds a stack of cheap throwing knives or darts you own, to tag monsters and gather them into a stack.

### Bursting and barraging

On tasks where monsters stack, the panel gets a **Burst** tab. It finds your best gear for casting the strongest Ancient burst or barrage your Magic level allows, cast by hand with any weapon, so Virtus, a Kodai wand or an Occult necklace come into play. Each target is worked out with the wiki calculator's formulas, then multiplied by the *Burst targets* setting (3 by default, 0 turns it off), since every monster in the stack is hit separately. The banner shows the damage on each target too. If bursting comes out ahead, it's the recommended style and its gear is highlighted. Freezes, healing and other spell effects aren't counted.

The **Loot tools** setting chooses between all drops (the default), common drops only (loot that drops at least once every 64 kills on average), or off. Tools you don't own are never suggested.

**Safespots** are short text descriptions, summarised from the OSRS Wiki, for the location the Slayer plugin reports, or for every listed location when it doesn't. They're text only: no tiles are drawn or highlighted, and bosses, Slayer bosses and demi-bosses are left out, as RuneLite's rules require.

**Location needs** apply whatever the task: boots of stone anywhere in the Karuulm Slayer Dungeon (so a Konar fire giant task there asks for them too), a light source in the Lumbridge Swamp Caves and Mos Le'Harmless Caves, and an axe plus the 875-coin entry fee for Brimhaven Dungeon. They're enforced when the Slayer plugin reports that location, or when the task is only found there; otherwise they're a tip. Some tasks also get short tips, such as insulated boots for rune dragons, and Fungicide refills are suggested for mutated zygomites (up to 4).

Teleports cover common task locations only. When the Slayer plugin reports where your task is (for example from Konar), the teleports for that location are shown; otherwise the task's usual locations are listed.

## Other conveniences

- **Remembers your bank** between sessions (setting: *Remember bank*, on by default), so suggestions are ready as soon as you get a task. It's kept in your RuneLite settings for that account, like any other plugin setting; turning the setting off deletes it.
- **Task time estimate** in the stats box: kills left x monster HP / DPS. It ignores walking, respawns and overkill, so treat it as a rough lower bound.
- **Prefer Slayer helmet** (setting, 5% by default): on task, if the best set swaps your Slayer helmet or black mask for something else (say a Salve amulet with a Serpentine helm on undead), the plugin searches again with the helmet kept and uses that set when it's within this percentage. The panel says how much DPS that costs. 0 turns it off.
- **Slayer helmet reminder** (setting, on by default): a chat message if you attack a task monster without your Slayer helmet or black mask on while you own one. Once per task, and only in your own chat box.

The Bank Tags plugin, a RuneLite core plugin that's on by default, needs to be enabled for Show in bank.

## Target monster

The monster's stats come from, in order:

1. the monster you pick in the panel
2. the **Target override** setting, if you fill it in
3. a task monster currently near you, as reported by the Slayer plugin
4. a match on your Slayer task's name, for example "Abyssal demons" becomes Abyssal demon

On a task that a boss counts for, the picker also lists that boss, marked "(boss)": Cerberus on hellhounds, the Abyssal Sire on abyssal demons, Vorkath on blue dragons or zombies, Scurrius on rats and so on. It's never picked for you. Choosing it works out your gear against the boss and uses the boss's own preparation: its location and teleports, entry items such as a spade for Barrows or a mithril grapple for Kree'arra, and one-off unlocks as tips. Boss tasks get the same data. SlayerBIS gives no fight help for bosses: no safespots, attack or prayer advice, or anything during the fight.

The Slayer plugin must be enabled. It is a RuneLite core plugin.

## Assumptions and limits

- Prayers and potion boosts are **assumptions you set** in the plugin settings, and default to none. They are inputs to the calculation, not recommendations.
- Your real (unboosted) levels are used, plus the configured boosts.
- Special attacks, wilderness and raid scaling, boss phases, and other situational effects are not modelled.
- Uncharged, empty, inactive and broken items are left out because they cannot be used as they are. If charging or repairing one would raise a style's DPS, the panel says which item and by how much.
- Blowpipes are assumed to hold the dart type chosen in the settings.
- Autocast uses the best standard spell for your Magic level, or ancients when on the Ancient spellbook with a staff that can cast them.

## Building and testing

```
./gradlew build
```

The unit tests in `src/test` run the DPS maths and the optimiser against bundled data only. They never start the game client or touch a RuneScape account. `WikiReferenceTest` holds loadouts whose max hit, accuracy and DPS were checked against the live OSRS Wiki DPS calculator; each case lists the calculator settings used, so you can re-check them yourself.

## Updating the item and monster data

New items and monsters appear in the game regularly. To refresh the bundled stats from the wiki calculator:

```
python scripts/refresh_data.py
./gradlew build
```

The script downloads the calculator's data from GitHub and the drop tables from the OSRS Wiki's public API, rewrites the three JSON files and records the source in `DATA-NOTICE.txt`. Only the script downloads anything: the plugin itself makes no network requests. If the tests still pass, commit the new files and submit an update to the Plugin Hub.

## Credits

- DPS formulas follow the [OSRS Wiki DPS calculator](https://tools.runescape.wiki/osrs-dps/) ([source](https://github.com/weirdgloop/osrs-dps-calc)). The formulas are reimplemented in Java here; no calculator code is included.
- Equipment and monster stats in `src/main/resources` come from the same calculator's data, which is sourced from the [Old School RuneScape Wiki](https://oldschool.runescape.wiki/). Those data files are under CC BY-NC-SA 3.0, not this project's BSD licence; see `src/main/resources/com/slayerbis/DATA-NOTICE.txt`.
