package com.meanmutton.mechgame.core;

/** How to play. */
public final class HelpScreen extends Screen {
    private static final int BACK = 1, PREV = 2, NEXT = 3;
    private int page;

    private static final String[] TITLES = {"THE BASICS", "SHOOTING", "DAMAGE", "HEAT, AMMO & EQUIPMENT", "TERRAIN & WEATHER",
            "UNITS", "CAMPAIGN", "CAMPAIGN SCENARIOS", "TIME AND MONEY", "FACTIONS", "MERCENARY"};
    private static final int FACTIONS_PAGE = 9;
    private static final String[][] PAGES = {
            {
                    "Each side fields up to 4 mechs, 6 mechs and vehicles in all (VTOLs count as vehicles), and 4 infantry, on a hex map. A full-size battle lasts up to 20 rounds; campaign scenarios may set smaller limits and shorter battles.",
                    "Before the first round you deploy: tap a unit, then a green hex. Infantry can be placed on any open hex on the map (ambushes!); everything else starts in your blue zone. Then press Start Battle.",
                    "Double-tap any unit (on the map, in HQ or in the Market) for its full detail sheet: every location's armor and structure, every weapon's numbers, and what each piece of equipment does. The Details button in the battle panel does the same.",
                    "Every round, one side wins initiative. Then the sides take turns activating one unit at a time until every unit has gone.",
                    "On your activation: tap one of your units (they pulse blue). The hexes it can reach glow green inside a bright outline, and the rest of the map dims. Tap a green hex twice to move there, tap an enemy to target it, choose weapons, FIRE, then End Turn.",
                    "You can rotate your unit (the arrow buttons) before ending. Facing matters: you shoot out of your front, and enemies who hit your sides or rear do more harm.",
                    "Undo takes back a move as long as you haven't fired yet. Drag the map to pan and pinch to zoom. Tap an empty hex for its terrain card: what it costs each kind of unit to enter, and what it does for cover and line of sight.",
                    "Battles save as you play. Leave the app or quit to the menu and Resume Battle puts you back where you were (during the computer's turn, at the start of it).",
                    "Destroy every enemy unit to win. If time runs out, the side that kept more of its value wins."
            },
            {
                    "Hit chance starts at 80% and is adjusted by range (medium -15, long -30), your own movement, how far the target moved, cover, woods in the way, heat, weather and equipment. Tap-select a weapon to see the full breakdown.",
                    "Line of sight is blocked by hills taller than the line between the two units, by towns, and by 3 or more points of woods or smoke (light woods 1, heavy woods 2, smoke 3). Each point of woods costs 10%.",
                    "Mechs stand taller than vehicles and see over low hills and light woods more easily. VTOLs fly and see almost everything, but they are also seen by everything.",
                    "Weapons fire within an arc: mech weapons cover a wide 240 degrees, vehicle hull weapons a 120-degree front arc, and turrets all around.",
                    "Artillery is the opposite of other weapons: most accurate at long range, a little worse at medium range, and very inaccurate at short range or inside its minimum range. It fires indirectly: it can hit anything any friendly unit can see. ECM stops spotting unless the spotter has a Sensor Suite. Shells that miss land in a neighboring hex, and all artillery splashes onto adjacent hexes, friends included.",
                    "Melee weapons (mechs only) strike adjacent units. Only one melee weapon swings per activation, so a mech can carry only one."
            },
            {
                    "Every unit has hit locations, each with armor and infrastructure. Damage strips armor first, then infrastructure. A location with no infrastructure left is destroyed, along with the weapons mounted there.",
                    "Hits on a location that is already destroyed (a mech's lost arm or leg, a vehicle's lost turret) carry through to the core (a vehicle's front hull) at half damage, as does any damage left over when a limb is blown off.",
                    "Where you hit depends on the angle: attacks from the front mostly hit the front (or a mech's core and limbs); side attacks hit that side; rear attacks hit the thin rear armor. A random roll picks the exact location.",
                    "Mechs die when the core or cockpit is destroyed. Losing a leg halves movement; losing both immobilizes the mech. Vehicles die when any hull side is breached, and their motive systems can be damaged by any hit.",
                    "When a unit is hit, the battle panel switches to its hit-location diagram: each hit flashes on the location it struck (yellow for armor, red for structure) with the damage, and the combat log at the bottom lists the damage by location.",
                    "Infrastructure damage can cause critical hits: destroyed weapons or equipment, damaged cooling, stunned vehicle crews, and ammunition explosions.",
                    "Missiles spread across several locations. Lasers and cannons hit one. Infantry shrug off half the damage from big guns, but chainguns, missiles and artillery hurt them fully. The exception is the Mortar Section: a glass cannon whose crew works in the open and takes full damage from everything."
            },
            {
                    "Energy weapons make heat. Ballistic weapons use ammunition. Missiles use both but are light. Each unit sheds heat every round through its heat sinks; standing in water adds cooling.",
                    "Above half your heat capacity you lose 1 movement and 10% accuracy; above three quarters, 2 movement and 20%. Reaching your capacity shuts the unit down for its next activation, damages it, and may set off its ammunition.",
                    "Targeting Computer +10% for energy and ballistic weapons. ECM jams enemy missiles and spotting near your units. Sensor Suite sees through woods, night and ECM.",
                    "Turbo Booster +3 movement for one activation. Coolant Pod dumps 15 heat. Smoke Launcher fills your hex with smoke. Field Repair Kit restores 15 armor to your most damaged location. These four have buttons in the battle panel during your activation.",
                    "Reflective and Reactive Plating cut energy or missile/artillery damage by a quarter, and Command Uplink gives allies within 3 hexes +5% to hit. These work automatically; the hit-chance breakdown under the weapon list shows each modifier."
            },
            {
                    "Open ground, light woods (cover -10%), heavy woods (cover -20%), rough ground, shallow and deep water, towns (cover -15%) and roads. Hills come in levels 0 to 3; the number in a hex's corner is its height.",
                    "Climbing costs extra movement. Wheeled vehicles fly along roads but can't enter woods or water. Hovertanks skim over water, even deep water, and weave through light woods, but not heavy woods or towns. Tracked tanks handle light woods. Mechs and infantry go almost anywhere. VTOLs ignore terrain.",
                    "Maps are generated fresh each battle: rivers run downhill, lakes sit in low ground, forests grow where it's wet, rocks cluster on high ground, and a road links the two sides.",
                    "Weather changes the fight: rain and snow cool you, heat waves don't. Fog and sandstorms limit long-range shots. Night hurts accuracy beyond 4 hexes. Magnetic storms knock out electronics."
            },
            {
                    "Light units are fast; heavy units carry more armor and weapons. Weapons come in light, medium and heavy classes; only the heaviest units (tracked tanks, heavy and assault mechs) can mount heavy weapons.",
                    "Armor: tracked tanks and assault mechs carry the most; wheeled vehicles about as much as heavy mechs; hovertanks about as much as medium mechs; light mechs and VTOLs the least.",
                    "Mechs cost much more, but they have far more infrastructure, can't suffer motive damage, cover a wider firing arc, walk through heavy woods and deep water, and can use melee weapons.",
                    "Roles: Scouts spot for artillery, Front-line units hold ground, Strikers flank, Fire Support works at long range, Artillery shells from afar, Brawlers close in, Command units boost the team."
            },
            {
                    "Your company carries over from battle to battle: money, units, damage and spare parts.",
                    "At HQ, tick the units to deploy (up to the battle's limits), repair damage, and launch the next contract. The tabs above the roster show only mechs, vehicles, or VTOLs and infantry, and Sort orders it by name, type, value, condition or what is deployed; a long roster or loadout scrolls. Win to collect the full pay plus a bonus for what you destroyed, and salvage enemy weapons into your spare parts.",
                    "Units destroyed in a battle you win (or draw) are recovered as wrecks you can repair. Lose or withdraw and they are gone for good.",
                    "The enemy is a company too. It starts with funds like yours, is paid after every battle (more when it wins), repairs what it can afford, loses units left on a field it lost, and buys reinforcements. Beat it down and it gets poorer; let it win and it grows. HQ and the briefing show its money and roster.",
                    "To customize a unit, select it at HQ and press CUSTOMIZE. Tap any weapon mount or equipment slot to swap what's in it, and use - / + to change armor, heat sinks and ammunition, all within the unit's weight limit. Removed parts go to your spares for free reuse. The Market sells new units with factory loadouts.",
                    "Quick Battle gives you a budget, lets you buy and refit a force, and fights a single battle.",
                    "Two Players (in the Quick Battle panel) is for two people on one device. Each builds a force with the same budget, then each deploys while the other looks away: a hand-over screen hides the map, and neither sees where the other set up until the battle starts. Player 1 is blue on the west edge, Player 2 red on the east; on your turn, play your units as usual. Pause to concede."
            },
            {
                    "A campaign is the fight for one planet. You choose to attack or defend when you start. The front runs from the attacker's dropships (-1) to the defender's base (6) and starts at 2, so the defender has the upper hand.",
                    "Every battle is a scenario. It limits which units may fight and how many: the Initial Drop allows only VTOLs, hovertanks, wheeled vehicles and light and medium mechs; Recon Sweeps only VTOLs, hovertanks, wheeled vehicles and light mechs; Pitched Battles anything. The enemy plays by the same limits.",
                    "The next scenario is rolled from the last result, the front and a random roll, and shown at HQ before you repair, buy and customize, so you know what you can bring. Units it doesn't allow show as Not allowed. If nothing on your roster may fight, you can only forfeit.",
                    "Winning moves the front your way, usually one step (two in a Decisive Engagement, Breakthrough or Counterattack). Recon Sweeps don't move it; the winner gets the edge instead: initiative in the first round of the next battle.",
                    "Some scenarios lean toward one side: more initiative, a time-out that only asks one side to be close, a free garrison, or a bigger force allowance. The briefing says which way each one leans.",
                    "The attacker that reaches front 6 assaults the defender's base, held by garrison infantry, an armored car, a gun emplacement and a laser turret. Win, and the planet falls; lose, and it is thrown back a step. An attacker driven past its landing zone must hold its dropships in an Evacuation, or leave the planet.",
            },
            {
                    "Time is on the defender's side. The attacker lands with " + Scenario.ATTACKER_START + " times the starting money, but its pay stays flat, while the defender's pay grows with every battle fought on the planet, up to " + Scenario.DEFENDER_MAX_INCOME + " times.",
                    "Money alone stops mattering once you can field your best units, so resources also become numbers: the side with more of them brings reserves beyond a scenario's unit limit. The attacker gets one extra unit in its first battles; the defender gets one once it has more resources than the attacker, and up to three as the campaign drags on.",
                    "The enemy company's force limit follows the two sides' resources too. As the attacker, win early. As the defender, hold on: every battle makes you stronger.",
                    "The briefing shows the battle count, both sides' income, and any reserves.",
                    "When a planet is decided, your company can move on to a new one, on either side, keeping its units and money."
            },
            {},   // the factions page is drawn specially: each faction's emblem, colors and specialty
            {
                    "In Mercenary play you fight for whoever pays. Each planet has two to four factions fighting over it; the Contracts board lists the jobs they offer: who hires you, who you fight (both with their emblems and colors), the scenario and its limits, the threat, the pay and the enemy's force.",
                    "Take a job, then buy, refit and deploy for it at HQ. Each job is one battle against the target faction's own designs, carrying its own makes of weapons and equipment and painted in its colors. Pick company colors that stand apart from theirs.",
                    "Every job moves your standing. Win and your employer and its allies like you more; the faction you fought, and its allies, like you less. Losing still costs you with the target, and disappoints your employer.",
                    "Standing runs from Hostile through Unfriendly, Neutral and Friendly to Allied. Anyone above Hostile will hire you; only factions that are Neutral or better will sell to you. The Market and refit screen only carry what this planet's traders make.",
                    "Allied factions sometimes offer something special: their one-of-a-kind prototype design or a unique weapon or piece of equipment. Uniques only exist in Mercenary play. The biggest jobs may field a faction's prototype against you.",
                    "When the board dries up or you have made too many enemies, Travel to another planet. Each destination shows who is fighting there and how they see you."
            },
    };

    public HelpScreen(Game game) {
        super(game);
    }

    protected void layout() {
        buttons.clear();
        add(new Btn(BACK, "Back").set(u * 3, h - u * 12, u * 24, u * 9));
        Btn p = add(new Btn(PREV, "<").set(w - u * 30, h - u * 12, u * 12, u * 9));
        Btn n = add(new Btn(NEXT, ">").set(w - u * 15, h - u * 12, u * 12, u * 9));
        p.enabled = page > 0;
        n.enabled = page < PAGES.length - 1;
    }

    protected void onClick(Btn b) {
        if (b.id == BACK) game.toMenu();
        else {
            page = Math.max(0, Math.min(PAGES.length - 1, page + (b.id == NEXT ? 1 : -1)));
            w = -1;
            game.setScreen(this);
        }
    }

    public boolean back() {
        game.toMenu();
        return true;
    }

    /** Every faction with its emblem, colors, bloc and specialty: the same emblem and color mark it everywhere. */
    private void renderFactions(Gfx g) {
        float top = h * 0.14f, rowH = (h - top - u * 19) / 4f, colW = (w - u * 9) / 2f;
        for (Faction f : Faction.ALL) {
            float x = u * 4 + (f.id % 2) * (colW + u), y = top + (f.id / 2) * rowH;
            Ui.panel(g, x, y, colW, rowH - u, u * 1.5f);
            float r = Math.min(rowH * 0.32f, u * 5);
            Emblem.draw(g, f.id, x + u * 2 + r, y + rowH * 0.45f, r);
            float tx = x + u * 4 + r * 2;
            g.text(f.name, tx, y + rowH * 0.2f, u * 3f, f.color, Gfx.ALIGN_LEFT, true);
            g.text(f.specialty + "  ·  " + (f.bloc >= 0 ? Faction.BLOC_NAMES[f.bloc] : "Independent"), tx, y + rowH * 0.45f, u * 2.2f,
                    Col.TEXT, Gfx.ALIGN_LEFT, false);
            // the faction's paint: main, trim, accent
            int[] cols = {f.scheme.main, f.scheme.trim, f.scheme.accent};
            for (int i = 0; i < 3; i++) g.fillRoundRect(tx + i * u * 4.5f, y + rowH * 0.62f, u * 4, u * 2.4f, u * 0.5f, cols[i]);
            Ui.wrap(g, f.desc, tx + u * 15, y + rowH * 0.66f, u * 1.8f, colW - (tx - x) - u * 16, Col.TEXT_DIM);
        }
        g.text("Factions matter in mercenary play: who hires you, whose markets you can buy from, and who you fight.",
                w / 2f, h - u * 16f, u * 2.2f, Col.TEXT_DIM, Gfx.ALIGN_CENTER, false);
    }

    public void render(Gfx g) {
        background(g);
        Ui.title(g, TITLES[page], w / 2f, h * 0.08f, u * 6.5f);
        float y = h * 0.18f;
        if (page == FACTIONS_PAGE) renderFactions(g);
        for (String s : PAGES[page]) y += Ui.wrap(g, "- " + s, u * 6, y, u * 3, w - u * 12, Col.TEXT) + u * 1.5f;
        g.text("Page " + (page + 1) + " of " + PAGES.length, w - u * 33, h - u * 7.5f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        renderButtons(g);
    }
}
