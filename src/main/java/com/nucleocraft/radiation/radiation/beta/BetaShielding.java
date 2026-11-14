package com.nucleocraft.radiation.radiation.beta;

import java.util.Locale;

/**
 * Suggested gameplay transmissions per full block/layer. World geometry raycast,
 * armour thickness and material tags are HOST integrations, not implemented here.
 */
public final class BetaShielding {
    private BetaShielding() { }
    public static double suggestedTransmission(String materialTag) {
        if (materialTag == null) return 1;
        return switch (materialTag.toLowerCase(Locale.ROOT)) {
            case "minecraft:air" -> 1.0;
            case "minecraft:glass" -> 0.55;
            case "minecraft:water" -> 0.14;
            case "minecraft:stone", "minecraft:cobblestone" -> 0.20;
            case "minecraft:iron_block", "minecraft:lead_block" -> 0.03;
            case "minecraft:oak_planks", "minecraft:wood" -> 0.65;
            default -> 1.0;
        };
    }
    public static double combine(double... layers) {
        if (layers == null) throw new IllegalArgumentException("Missing layers");
        double transmission = 1;
        for (double layer : layers) {
            if (!Double.isFinite(layer) || layer < 0 || layer > 1) throw new IllegalArgumentException("Bad layer");
            transmission *= layer;
        }
        return transmission;
    }
}
