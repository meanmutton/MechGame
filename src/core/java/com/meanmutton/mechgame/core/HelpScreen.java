package com.meanmutton.mechgame.core;

/** How to play. */
public final class HelpScreen extends Screen {
    private static final int BACK = 1, PREV = 2, NEXT = 3;
    private int page;

    private static final String[] TITLES = {"THE BASICS", "SHOOTING", "DAMAGE", "HEAT, AMMO & EQUIPMENT", "TERRAIN & WEATHER",
            "UNITS", "CAMPAIGN"};
    private static final String[][] PAGES = {
            {
                    "Each side fields up to 6 units on a hex map. A battle lasts up to 15 rounds.",
                    "Every round, one side wins initiative. Then the sides take turns activating one unit at a time until every unit has gone.",
                    "On your activation: tap one of your units (they pulse blue), tap a green hex twice to move there, tap an enemy to target it, choose weapons, FIRE, then End Turn.",
                    "You can rotate your unit (the arrow buttons) before ending. Facing matters: you shoot out of your front, and enemies who hit your sides or rear do more harm.",
                    "Undo takes back a move as long as you haven't fired yet. Drag the map to pan and pinch to zoom.",
                    "Destroy every enemy unit to win. If time runs out, the side that kept more of its value wins."
            },
            {
                    "Hit chance starts at 80% and is adjusted by range (medium -15, long -30), your own movement, how far the target moved, cover, woods in the way, heat, weather and equipment. Tap-select a weapon to see the full breakdown.",
                    "Line of sight is blocked by hills taller than the line between the two units, by towns, and by 3 or more points of woods or smoke (light woods 1, heavy woods 2, smoke 3). Each point of woods costs 10%.",
                    "Mechs stand taller than vehicles and see over low hills and light woods more easily. VTOLs fly and see almost everything, but they are also seen by everything.",
                    "Weapons fire within an arc: mech weapons cover a wide 240 degrees, vehicle hull weapons a 120-degree front arc, and turrets all around.",
                    "Artillery fires indirectly: it can hit anything any friendly unit can see. ECM stops spotting unless the spotter has a Sensor Suite. Shells that miss land in a neighboring hex, and all artillery splashes onto adjacent hexes, friends included.",
                    "Melee weapons (mechs only) strike adjacent units. Only one melee weapon swings per activation."
            },
            {
                    "Every unit has hit locations, each with armor and infrastructure. Damage strips armor first, then infrastructure. A location with no infrastructure left is destroyed, along with the weapons mounted there.",
                    "Where you hit depends on the angle: attacks from the front mostly hit the front (or a mech's core and limbs); side attacks hit that side; rear attacks hit the thin rear armor. A random roll picks the exact location.",
                    "Mechs die when the core or cockpit is destroyed. Losing a leg halves movement; losing both immobilizes the mech. Vehicles die when any hull side is breached, and their motive systems can be damaged by any hit.",
                    "Infrastructure damage can cause critical hits: destroyed weapons or equipment, damaged cooling, stunned vehicle crews, and ammunition explosions.",
                    "Missiles spread across several locations. Lasers and cannons hit one. Infantry shrug off half the damage from big guns, but chainguns, missiles and artillery hurt them fully."
            },
            {
                    "Energy weapons make heat. Ballistic weapons use ammunition. Missiles use both but are light. Each unit sheds heat every round through its heat sinks; standing in water adds cooling.",
                    "Above half your heat capacity you lose 1 movement and 10% accuracy; above three quarters, 2 movement and 20%. Reaching your capacity shuts the unit down for its next activation, damages it, and may set off its ammunition.",
                    "Targeting Computer +10% for energy and ballistic weapons. ECM jams enemy missiles and spotting near your units. Sensor Suite sees through woods, night and ECM.",
                    "Turbo Booster +3 movement for one activation. Coolant Pod dumps heat. Smoke Launcher hides you. Reflective and Reactive Plating cut energy or missile/artillery damage. Command Uplink helps nearby allies aim. Field Repair Kit patches armor."
            },
            {
                    "Open ground, light woods (cover -10%), heavy woods (cover -20%), rough ground, shallow and deep water, towns (cover -15%) and roads. Hills come in levels 0 to 3; the number in a hex's corner is its height.",
                    "Climbing costs extra movement. Wheeled vehicles fly along roads but can't enter woods or water. Hovertanks skim over water but avoid woods. Tracked tanks handle light woods. Mechs and infantry go almost anywhere. VTOLs ignore terrain.",
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
                    "At HQ, tick up to 6 units to deploy, repair damage, and launch the next contract. Win to collect the full pay plus a bonus for what you destroyed, and salvage enemy weapons into your spare parts.",
                    "Units destroyed in a battle you win (or draw) are recovered as wrecks you can repair. Lose or withdraw and they are gone for good.",
                    "Use the Workshop (Refit) to swap weapons and equipment, change armor, heat sinks and ammunition. Removed parts go to your spares for free reuse. The Market sells new units with factory loadouts.",
                    "Quick Battle gives you a budget, lets you buy and refit a force, and fights a single battle."
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

    public void render(Gfx g) {
        background(g);
        Ui.title(g, TITLES[page], w / 2f, h * 0.08f, u * 6.5f);
        float y = h * 0.18f;
        for (String s : PAGES[page]) y += Ui.wrap(g, "- " + s, u * 6, y, u * 3, w - u * 12, Col.TEXT) + u * 1.5f;
        g.text("Page " + (page + 1) + " of " + PAGES.length, w - u * 33, h - u * 7.5f, u * 2.6f, Col.TEXT_DIM, Gfx.ALIGN_RIGHT, false);
        renderButtons(g);
    }
}
