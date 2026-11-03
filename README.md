# Hexfront

A turn-based hex strategy game for Android: you against the computer, with up to 4 mechs, 6 mechs and vehicles in all, and 4 infantry a side, chosen from mechs, tanks, hovertanks, VTOLs, armored cars and infantry.

![Battle](docs/screenshots/battle.png)

## Install on your phone

1. Copy `release/Hexfront.apk` to your phone (or download it from this repository on the phone).
2. Tap it and allow installs from that app when Android asks. If Play Protect warns about an unknown developer, choose "Install anyway".

Android 5.0 or newer, landscape. No permissions, no network, no ads.

## How it plays

- **Modes.** *Campaign*: fight for a planet as the attacker or the defender (see below); money, units, damage and spare parts carry over, and the game is saved. The enemy is a rival company with its own money and roster: it is paid every contract (more when it wins), repairs what it can afford, loses units left on a field it lost, and buys or upgrades units. Each contract caps the force it can field at a multiple of yours (1.1× Easy, 1.4× Normal, 1.8× Hard); a poor rival fields less. It plays by each scenario's unit limits, buys with the next scenario in mind, and hires stopgap units when it has nothing allowed. *Quick Battle*: spend a budget on a force and fight one battle. Battles in progress are saved as you play: leave the app and **Resume Battle** on the menu puts you back in the same battle.
- **Crash and freeze reports.** If the game ever crashes, it saves a report on the way down (the error, the build and phone, what the game was doing, and your last few taps) and shows it the next time you open it, with a **Share report** button to email or paste it. A watchdog does the same if the game freezes for 5 seconds, recording where every thread is stuck.
- **Campaign scenarios.** A campaign is a series of battles that ends when the planet is decided. The front runs from the attacker's dropships (-1) to the defender's base (6) and starts at 2, so the defender has the upper hand. Every battle is one of 21 scenarios, each limiting which units may fight and how many: the **Initial Drop** comes first (VTOLs, hovertanks, wheeled vehicles, light and medium mechs), **Recon Sweeps** are for VTOLs, hovertanks, wheeled vehicles and light mechs (at most 2 mechs and 4 units in all, no infantry), **Pitched Battles** and **Decisive Engagements** take everything, and in between come raids, river crossings, street fights, lance clashes, armored thrusts, air assaults, storms and more. Some lean slightly toward the attacker or the defender (more initiative, a time-out that only asks one side to be close, a free garrison, a bigger force allowance), and the briefing says which. The next scenario is rolled from the last result, the front and a random roll, and is shown at HQ and in the Market *before* you repair, buy and customize. An attacker that reaches the base faces garrison infantry, an armored car, a gun emplacement and a laser turret: win and the planet falls. An attacker driven off its landing zone must hold its dropships or leave the planet. Time is on the defender's side: the attacker lands with 1.5 times the starting funds, but its pay stays flat while the defender's grows with every battle on the planet (up to 2.5 times). Resources become force: the richer side fields reserves beyond a scenario's unit limit (the attacker one extra unit in its first two battles; the defender one from about battle 6, two from 10, three from 16), and the enemy's force limit follows the two sides' resources. When a planet is decided, your company can move on to a new one, on either side.
- **Deployment.** Before the first round you place your units: infantry can go on any open hex on the map, everything else starts in your home zone. The computer digs its infantry into woods and towns too.
- **Details.** Double-tap any unit (on the map, in HQ or in the Market) for its full sheet: every location's armor and structure, every weapon's numbers, and what each piece of equipment does.
- **Hit feedback.** Every hit bursts on the part of the unit it struck. The battle panel switches to the target's hit-location diagram, flashing each struck location with the damage (yellow for armor, red for structure) and a running total. The combat log lists damage by location, e.g. "Monolith fired at Halberd (front): 5/5 hit, 46 damage: 29 to core, 11 to left arm, 2 to right arm, 4 to right leg".
- **Turns.** Each round one side wins initiative, then the sides alternate activating one unit at a time. On an activation: tap your unit, tap a green hex twice to move, tap an enemy to target it, pick weapons (hit chances shown per weapon), FIRE, rotate if you like, End Turn. Undo takes back a move until you fire. Drag to pan, pinch to zoom.
- **Hit locations.** Every unit has locations with **armor** and **infrastructure**. Damage strips armor, then infrastructure; a location without infrastructure is destroyed with its weapons. Later hits on a destroyed location (and damage left over when one is blown off) carry through to the core at half strength. Which location gets hit depends on the **angle of attack** (front, side or rear arc, relative to the target's facing) plus a random roll. Mechs: cockpit, core, back, arms, legs. Vehicles: front, sides, rear, turret (rotor on VTOLs). Infantry: one platoon.
- **Heat and ammo.** Energy weapons generate heat, ballistic weapons use ammo, missiles do some of both but are light. Each unit sheds a limited amount of heat per round; running hot costs movement and accuracy, and maxing out shuts the unit down. Standing in water cools you.
- **Terrain.** Hills block sight and give high ground, woods give cover and block sight when thick, towns give cover, water cools, roads speed up wheels. Each unit type moves differently: wheels stay on roads and open ground, hovertanks skim any water and weave through light woods, tracked tanks handle light woods, mechs and infantry go almost anywhere, VTOLs fly over everything.
- **Weather.** Rain, thunderstorms, fog, night, heat waves, snow, sandstorms and magnetic storms each change accuracy, sight range, cooling or electronics.
- **Maps.** Generated fresh for every battle in four regions (temperate, desert, arctic, badlands). Elevation comes first, then lakes in the low ground, rivers that flow downhill between the two armies, forests where it is wet, rocks on high ground, a town, and a road with bridges linking both sides.

![Maps](docs/screenshots/maps.png)

## Units

9 types, 97 designs you can buy (plus 3 fixed emplacements that defend bases), built by eight factions, each with its own artwork, each in a role: Scout, Front-line, Striker, Fire Support, Artillery, Brawler, Command.

![All units](docs/screenshots/units.png)

| Type | Designs | Notes |
|---|---|---|
| Infantry | Rifle Platoon, Rocket Squad, Recon Team, Mortar Section, Hegemony Grenadiers, Pike Squad, Sapper Team, Prospector Rifles, Ghost Cell, Accord Rangers, Pact Heavy Weapons | Cheap; hard to hurt with big guns; good in woods and towns. The Mortar Section is a glass cannon: big damage, easy to kill |
| Tracked Tank | Ironhide MBT, Breaker Heavy Tank, Thunder SPG, Quiver Missile Carrier, Lumen, Bulwark, Citadel, Salvo, Longreach, Sentinel, Hedgehog, Anvil, Hammerfall | Most armor of any vehicle; heavy weapons |
| Hovertank | Dart Scout Hover, Riptide Strike Hover, Squall Missile Hover, Glare, Gale, Cyclone, Skein, Phantom, Tern | Very fast; crosses deep water and light woods |
| VTOL | Mosquito Recon VTOL, Dragonfly Attack VTOL, Talon Gunship, Kite, Peregrine, Murk, Skylark, Gunhawk | Flies over terrain; light armor; flak deals double damage to it |
| Wheeled Vehicle | Jackal Scout Car, Rhino Armored Car, Mule Battery, Lancer AT Car, Sparkfly, Relay, Badger, Mongrel, Ox Hauler, Ranger, Aurora, Bison | Fast on roads; armor like a heavy mech |
| Light Mech | Skitter, Needle, Lantern, Sunspear, Swift, Watchman, Wraith, Vanguard | Fast, light weapons only |
| Medium Mech | Halberd, Ricochet, Cinder, Sabre Dancer, Corona, Warden, Harrier, Brushfire, Arbiter, Thorn, Homesteader, Shade, Paladin, Forge | |
| Heavy Mech | Bastille, Overwatch, Cleaver, Rainmaker, Prism, Bastion, Firestorm, Oracle, Briar, Nightfall, Templar, Crucible | Heavy weapons |
| Assault Mech | Monolith, Pulverizer, Skyfall, Aegis, Solar Flare, Juggernaut, Pyre, Bramble, Sovereign, Colossus | Most armor and firepower |
| Emplacement | Gun Emplacement, Missile Bunker, Laser Turret | Campaign bases only; can't be bought or moved |

## Factions

Every design is built by one or more of eight factions. Each faction builds around a specialty and has six designs of its own; the members of each bloc share eight more, and common designs (riflemen, the Ironhide, the Halberd) are built widely. The Market shows who builds each design and can filter by faction. Factions will matter in the mercenary mode (coming); the campaign and quick battles sell every design.

| Faction | Bloc | Specialty | Its own designs |
|---|---|---|---|
| Helion Concord | Northern Accord | Energy weapons | Sunspear, Corona, Prism, Solar Flare, Glare, Lumen |
| Vardan Hegemony | Iron Pact | Armor and cannons | Bulwark, Citadel, Warden, Bastion, Juggernaut, Hegemony Grenadiers |
| Kestrel League | Northern Accord | Speed | Swift, Harrier, Gale, Cyclone, Kite, Peregrine |
| Ashfall Collective | Iron Pact | Missiles and artillery | Brushfire, Firestorm, Pyre, Salvo, Longreach, Sparkfly (and the Skyfall) |
| Meridian Syndicate | Northern Accord | Electronics and command | Watchman, Arbiter, Oracle, Relay, Skein, Sentinel (and the Aegis) |
| Thornwall Compact | independent | Close assault and infantry | Thorn, Briar, Bramble, Pike Squad, Sapper Team, Hedgehog |
| Ironroot Freeholds | Iron Pact | Rugged vehicles | Badger, Mongrel, Ox Hauler, Ranger, Homesteader, Prospector Rifles |
| Obsidian Veil | independent | Raiders and stealth | Wraith, Shade, Nightfall, Phantom, Murk, Ghost Cell |

Shared by the Northern Accord: Vanguard, Paladin, Templar, Sovereign, Skylark, Tern, Accord Rangers, Aurora. Shared by the Iron Pact: Anvil, Bison, Hammerfall, Forge, Crucible, Colossus, Pact Heavy Weapons, Gunhawk.

**Faction-made weapons and equipment.** Factions make their own versions of the weapons and equipment in their specialty, named for the maker ("Helion Laser") and shown in its color: Helion energy weapons +5% to hit and 1 less heat; Vardan cannons +10% damage; Kestrel light weapons 25% lighter and a +4 MP turbo booster; Ashfall missiles +10% damage and artillery +5% to hit; Meridian electronics (targeting computer +15%, ECM 3 hexes, sensors through 2 points of woods, uplink +8%); Thornwall melee weapons and demolition charges hit harder and its repair kit restores 25 armor; Ironroot autocannons and rockets carry 50% more ammunition and cost less, and its repair kit works twice; Obsidian burst lasers and rockets do +25% from the side or rear and its smoke lasts longer and works three times. A faction's own designs come with its makes. In the campaign and quick battles only the standard makes are sold new; faction makes come with faction designs and from salvage, and stay in your spare parts.

**Uniques.** Each faction also has a unique prototype design carrying a unique weapon or piece of equipment (the Zenith with the Sunlance, the Dreadnought with the Hegemon Rail, the Zephyr with an Afterburner, the Cataclysm with the Hellfire Rack, the Panopticon with the Omni-Link, the Warlord with the Siegebreaker, the Mammoth with a Field Workshop, the Eclipse with the Shroud). They only turn up in mercenary play.

Armor follows your spec: tracked tanks and assault mechs carry the most, wheeled vehicles about as much as heavy mechs, hovertanks about as much as medium mechs, light mechs and VTOLs the least. Lighter units move further.

**Mechs** cost roughly twice as much as a comparable vehicle, and earn it: far more internal structure, no motive damage, a 240-degree firing arc, +10% fire control, any terrain, and melee weapons. In computer-vs-computer testing at equal total cost, six vehicles beat two or three mechs about 70% of the time. Vehicles are the cost-efficient choice; mechs are how a bigger budget becomes a stronger force, up to the 4-mech limit.

## Weapons and equipment

31 weapons in 3 classes. Light weapons mount on anything; medium need a medium-or-heavier mount; heavy only fit tracked tanks, heavy mechs and assault mechs.

- **Energy:** Light Laser, Light Burst Laser, Laser, Burst Laser, Heavy Laser, Plasma Caster (heats the target), Ion Lance (scrambles targeting)
- **Ballistic:** Chaingun, Light Cannon, Battle Cannon, Flak Repeater (anti-air), Siege Cannon, Rail Driver (armor-piercing)
- **Missile:** Light Rocket Pod, Rocket Pod, Heavy Rocket Pod, Light / standard / Heavy Volley Missiles (long range), Seeker Rockets (lock-on)
- **Artillery** (indirect fire on anything a friendly unit can see, with splash; most accurate at long range, very inaccurate up close): Field Mortar, Howitzer, Siege Howitzer
- **Melee** (mechs only): Vibro Blade, Arc Sword, Battle Axe, Great Axe, Thunder Hammer (staggers)
- **Infantry:** Assault Rifles, Satchel Charges, AT Missile Team

Equipment: Targeting Computer, ECM Suite, Sensor Suite, Turbo Booster, Coolant Pod, Smoke Launcher, Reflective Plating, Reactive Plating, Command Uplink, Field Repair Kit.

To customize a unit, select it at HQ and press **CUSTOMIZE**: you swap weapons and equipment mount by mount, set armor, heat sinks and ammo bins, all within the unit's weight capacity. Removed parts go to your spares and can be reinstalled free. Salvage from won battles adds to the spares.

| | | |
|---|---|---|
| ![HQ](docs/screenshots/hq.png) | ![Market](docs/screenshots/market.png) | ![Workshop](docs/screenshots/workshop.png) |
| ![Weapons](docs/screenshots/weapons.png) | ![Briefing](docs/screenshots/briefing.png) | ![Firing](docs/screenshots/firing.png) |
| ![Campaign HQ](docs/screenshots/campaign.png) | ![Base assault](docs/screenshots/base_assault.png) | ![Campaign won](docs/screenshots/campaign_end.png) |

## Building

```bash
sudo apt-get install default-jdk aapt dalvik-exchange apksigner zipalign android-sdk-platform-23
```

```bash
./build.sh
```

That writes `release/Hexfront.apk`, signed with `keystore/hexfront.keystore` (keep it; updates must use the same key).

```bash
./build.sh test
```

That also validates every design, plays 20 computer-vs-computer battles headless (checking for crashes and stalls, and printing balance stats), plays a few campaign missions through the scenario system, saves and resumes battles, plays a few thousand random taps through every screen looking for crashes (`monkey`; `monkey-threads` runs the computer on its own thread as the phone does), and renders every screen to `build/shots/`.

## Code layout

- `src/core/`: the whole game in plain Java (rules, map generation, AI, screens, procedural art and sound), drawing through a small `Gfx` interface.
- `src/android/`: activity, render loop, Canvas renderer, SoundPool audio, saved data.
- `src/desktop/`: Java2D test harness (not in the APK).
