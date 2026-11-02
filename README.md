# Hexfront

A turn-based hex strategy game for Android: you against the computer, with up to 6 units a side chosen from mechs, tanks, hovertanks, VTOLs, armored cars and infantry.

![Battle](docs/screenshots/battle.png)

## Install on your phone

1. Copy `release/Hexfront.apk` to your phone (or download it from this repository on the phone).
2. Tap it and allow installs from that app when Android asks. If Play Protect warns about an unknown developer, choose "Install anyway".

Android 5.0 or newer, landscape. No permissions, no network, no ads.

## How it plays

- **Modes.** *Campaign*: run a company through contract after contract; money, units, damage and spare parts carry over, and the game is saved. The enemy is a rival company with its own money and roster: it is paid every contract (more when it wins), repairs what it can afford, loses units left on a field it lost, and buys or upgrades units. Each contract caps the force it can field at a multiple of yours (1.1× Easy, 1.4× Normal, 1.8× Hard); a poor rival fields less. *Quick Battle*: spend a budget on up to 6 units and fight one battle.
- **Deployment.** Before the first round you place your units: infantry can go on any open hex on the map, everything else starts in your home zone. The computer digs its infantry into woods and towns too.
- **Details.** Double-tap any unit (on the map, in HQ or in the Market) for its full sheet: every location's armor and structure, every weapon's numbers, and what each piece of equipment does.
- **Turns.** Each round one side wins initiative, then the sides alternate activating one unit at a time. On an activation: tap your unit, tap a green hex twice to move, tap an enemy to target it, pick weapons (hit chances shown per weapon), FIRE, rotate if you like, End Turn. Undo takes back a move until you fire. Drag to pan, pinch to zoom.
- **Hit locations.** Every unit has locations with **armor** and **infrastructure**. Damage strips armor, then infrastructure; a location without infrastructure is destroyed with its weapons. Later hits on a destroyed location (and damage left over when one is blown off) carry through to the core at half strength. Which location gets hit depends on the **angle of attack** (front, side or rear arc, relative to the target's facing) plus a random roll. Mechs: cockpit, core, back, arms, legs. Vehicles: front, sides, rear, turret (rotor on VTOLs). Infantry: one platoon.
- **Heat and ammo.** Energy weapons generate heat, ballistic weapons use ammo, missiles do some of both but are light. Each unit sheds a limited amount of heat per round; running hot costs movement and accuracy, and maxing out shuts the unit down. Standing in water cools you.
- **Terrain.** Hills block sight and give high ground, woods give cover and block sight when thick, towns give cover, water cools, roads speed up wheels. Each unit type moves differently: wheels stay on roads and open ground, hovertanks skim water, tracked tanks handle light woods, mechs and infantry go almost anywhere, VTOLs fly over everything.
- **Weather.** Rain, thunderstorms, fog, night, heat waves, snow, sandstorms and magnetic storms each change accuracy, sight range, cooling or electronics.
- **Maps.** Generated fresh for every battle in four regions (temperate, desert, arctic, badlands). Elevation comes first, then lakes in the low ground, rivers that flow downhill between the two armies, forests where it is wet, rocks on high ground, a town, and a road with bridges linking both sides.

![Maps](docs/screenshots/maps.png)

## Units

9 types, 33 designs, each with its own artwork, each in a role: Scout, Front-line, Striker, Fire Support, Artillery, Brawler, Command.

![All units](docs/screenshots/units.png)

| Type | Designs | Notes |
|---|---|---|
| Infantry | Rifle Platoon, Rocket Squad, Recon Team, Mortar Section | Cheap; hard to hurt with big guns; good in woods and towns |
| Tracked Tank | Ironhide MBT, Breaker Heavy Tank, Thunder SPG, Quiver Missile Carrier | Most armor of any vehicle; heavy weapons |
| Hovertank | Dart Scout Hover, Riptide Strike Hover, Squall Missile Hover | Very fast, crosses water |
| VTOL | Mosquito Recon VTOL, Dragonfly Attack VTOL, Talon Gunship | Flies over terrain; light armor; flak deals double damage to it |
| Wheeled Vehicle | Jackal Scout Car, Rhino Armored Car, Mule Battery, Lancer AT Car | Fast on roads; armor like a heavy mech |
| Light Mech | Skitter, Needle, Lantern | Fast, light weapons only |
| Medium Mech | Halberd, Ricochet, Cinder, Sabre Dancer | |
| Heavy Mech | Bastille, Overwatch, Cleaver, Rainmaker | Heavy weapons |
| Assault Mech | Monolith, Pulverizer, Skyfall, Aegis | Most armor and firepower |

Armor follows your spec: tracked tanks and assault mechs carry the most, wheeled vehicles about as much as heavy mechs, hovertanks about as much as medium mechs, light mechs and VTOLs the least. Lighter units move further.

**Mechs** cost roughly twice as much as a comparable vehicle, and earn it: far more internal structure, no motive damage, a 240-degree firing arc, +10% fire control, any terrain, and melee weapons. In computer-vs-computer testing at equal total cost, six vehicles beat two or three mechs about 70% of the time. Vehicles are the cost-efficient choice; with only 6 slots per side, mechs are how a bigger budget becomes a stronger force.

## Weapons and equipment

31 weapons in 3 classes. Light weapons mount on anything; medium need a medium-or-heavier mount; heavy only fit tracked tanks, heavy mechs and assault mechs.

- **Energy:** Light Laser, Light Burst Laser, Laser, Burst Laser, Heavy Laser, Plasma Caster (heats the target), Ion Lance (scrambles targeting)
- **Ballistic:** Chaingun, Light Cannon, Battle Cannon, Flak Repeater (anti-air), Siege Cannon, Rail Driver (armor-piercing)
- **Missile:** Light Rocket Pod, Rocket Pod, Heavy Rocket Pod, Light / standard / Heavy Volley Missiles (long range), Seeker Rockets (lock-on)
- **Artillery** (indirect fire on anything a friendly unit can see, with splash): Field Mortar, Howitzer, Siege Howitzer
- **Melee** (mechs only): Vibro Blade, Arc Sword, Battle Axe, Great Axe, Thunder Hammer (staggers)
- **Infantry:** Assault Rifles, Satchel Charges, AT Missile Team

Equipment: Targeting Computer, ECM Suite, Sensor Suite, Turbo Booster, Coolant Pod, Smoke Launcher, Reflective Plating, Reactive Plating, Command Uplink, Field Repair Kit.

To customize a unit, select it at HQ and press **CUSTOMIZE**: you swap weapons and equipment mount by mount, set armor, heat sinks and ammo bins, all within the unit's weight capacity. Removed parts go to your spares and can be reinstalled free. Salvage from won battles adds to the spares.

| | | |
|---|---|---|
| ![HQ](docs/screenshots/hq.png) | ![Market](docs/screenshots/market.png) | ![Workshop](docs/screenshots/workshop.png) |
| ![Weapons](docs/screenshots/weapons.png) | ![Briefing](docs/screenshots/briefing.png) | ![Firing](docs/screenshots/firing.png) |

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

That also validates every design, plays 20 computer-vs-computer battles headless (checking for crashes and stalls, and printing balance stats), and renders every screen to `build/shots/`.

## Code layout

- `src/core/`: the whole game in plain Java (rules, map generation, AI, screens, procedural art and sound), drawing through a small `Gfx` interface.
- `src/android/`: activity, render loop, Canvas renderer, SoundPool audio, saved data.
- `src/desktop/`: Java2D test harness (not in the APK).
