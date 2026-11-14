package com.nucleocraft.radiation.commands.beta;

import com.nucleocraft.radiation.radiation.profiles.beta.BetaRadiationProfile;
import com.nucleocraft.radiation.radiation.beta.BetaCell;
import com.nucleocraft.radiation.radiation.beta.BetaIsotope;
import com.nucleocraft.radiation.radiation.beta.BetaIsotopeLookup;
import com.nucleocraft.radiation.radiation.beta.BetaLoad;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Ready-to-use string diagnostics; actual /radiation commands are a NeoForge bridge responsibility. */
public final class BetaDiagnostics {
    private BetaDiagnostics() { }
    public static String inspect(BetaCell cell, BetaIsotopeLookup lookup) {
        if (cell == null || lookup == null) throw new IllegalArgumentException("Missing diagnostic input");
        StringBuilder s=new StringBuilder("Beta material: ");
        if (cell.isEmpty()) return s.append("none").toString();
        for (var e:new TreeMap<>(cell.isotopes()).entrySet()) {
            BetaIsotope isotope=lookup.find(e.getKey());
            if(isotope==null || isotope.betaRatePerUnit()==0)continue;
            BetaLoad q=e.getValue();
            double activity=q.quantity()*isotope.betaRatePerUnit();
            s.append(String.format(Locale.ROOT,"[%s] quantity=%.6f, Beta=%.5f RAD/s, cap=%.5f RAD/s; ",
                displayName(e.getKey()),q.quantity(),activity,q.capRadPerSecond()));
        }
        return s.toString().equals("Beta material: ") ? "Beta material: none" : s.toString();
    }
    private static String displayName(String identifier) {
        String[] words = identifier.replace('_', ' ').trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
    public static PlayerRates player(double externalRadPerSecond, Map<String,Double> internalIsotopes,
                                     BetaIsotopeLookup lookup, BetaRadiationProfile profile) {
        if(!Double.isFinite(externalRadPerSecond)||externalRadPerSecond<0)
            throw new IllegalArgumentException("Invalid external Beta rate");
        double internal=profile.internalRate(internalIsotopes,lookup);
        return new PlayerRates(externalRadPerSecond,internal,externalRadPerSecond+internal);
    }
    public record PlayerRates(double externalRadPerSecond,double internalRadPerSecond,double totalRadPerSecond) { }
}
