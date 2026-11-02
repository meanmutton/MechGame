package com.meanmutton.mechgame.core;

import java.util.Random;

/** Battle weather and its rule effects. */
public final class Weather {
    public static final int CLEAR = 0, OVERCAST = 1, RAIN = 2, STORM = 3, FOG = 4, NIGHT = 5, HEAT_WAVE = 6,
            SNOW = 7, SANDSTORM = 8, MAGNETIC = 9;
    public static final String[] NAMES = {"Clear", "Overcast", "Rain", "Thunderstorm", "Fog", "Night", "Heat Wave",
            "Snowfall", "Sandstorm", "Magnetic Storm"};

    public final int id;
    public int hitAll, hitMed, hitLong, missileMod, cooling, maxSight;
    public boolean darkness, snowSlow, electronicsDown;
    public String desc;

    public Weather(int id) {
        this.id = id;
        maxSight = 99;
        switch (id) {
            case RAIN: hitAll = -5; cooling = 1; desc = "-5% to hit. +1 cooling."; break;
            case STORM: hitLong = -10; missileMod = -10; cooling = 2; desc = "-10% at long range, missiles -10%. +2 cooling."; break;
            case FOG: hitMed = -10; hitLong = -25; maxSight = 9; desc = "-10% at medium range, -25% at long. Can't see beyond 9 hexes."; break;
            case NIGHT: darkness = true; desc = "-15% to hit beyond 4 hexes unless the shooter has a Sensor Suite."; break;
            case HEAT_WAVE: cooling = -3; desc = "-3 cooling for everyone. Watch your heat."; break;
            case SNOW: cooling = 2; hitLong = -5; snowSlow = true; desc = "+2 cooling, -5% at long range. Wheeled and hover units slowed off-road."; break;
            case SANDSTORM: hitMed = -10; hitLong = -15; cooling = -1; maxSight = 12; desc = "-10% medium / -15% long range, -1 cooling."; break;
            case MAGNETIC: missileMod = -10; electronicsDown = true; desc = "Targeting computers, ECM and sensors don't work. Missiles -10%."; break;
            case OVERCAST: desc = "No effect."; break;
            default: desc = "No effect."; break;
        }
    }

    public static Weather random(int biome, Random r) {
        int[] options;
        switch (biome) {
            case HexMap.BIOME_DESERT: options = new int[]{CLEAR, CLEAR, HEAT_WAVE, HEAT_WAVE, SANDSTORM, NIGHT, MAGNETIC}; break;
            case HexMap.BIOME_ARCTIC: options = new int[]{CLEAR, OVERCAST, SNOW, SNOW, FOG, NIGHT, MAGNETIC}; break;
            case HexMap.BIOME_BADLANDS: options = new int[]{CLEAR, OVERCAST, HEAT_WAVE, STORM, NIGHT, MAGNETIC, SANDSTORM}; break;
            default: options = new int[]{CLEAR, CLEAR, OVERCAST, RAIN, STORM, FOG, NIGHT}; break;
        }
        return new Weather(options[r.nextInt(options.length)]);
    }
}
