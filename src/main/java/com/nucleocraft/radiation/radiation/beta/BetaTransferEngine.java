package com.nucleocraft.radiation.radiation.beta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Deterministic, six-face, mass-conserving Beta isotope transfer.
 *
 * IMPORTANT: This updates physical isotope quantities, NOT synthetic Beta
 * radiation sources. The caller provides an already-decayed snapshot at ONE
 * simulation instant, then applies the returned amounts and caps atomically
 * to its real, sparse per-chunk isotope storage.
 *
 * All proposals use start-of-step material (two-phase snapshot). A new recipient
 * cannot feed another recipient until the next update. The total feeding budget
 * across all six neighbours is 0.75%/second by default (not 0.75% EACH).
 * Offline transfers are NOT caught up in one enormous tick; decay is separate.
 */
public final class BetaTransferEngine {
    public static final double DEFAULT_TRANSFER_FRACTION_PER_SECOND = 0.0075;
    public static final double DEFAULT_NEIGHBOUR_CAP_RATIO = 0.90;
    public static final double DEFAULT_MIN_RATE_PER_SECOND = 0.01;
    private static final double EPS = 1.0e-14;

    private final double fractionPerSecond;
    private final double capRatio;
    private final double minTransferRatePerSecond;

    public BetaTransferEngine() {
        this(DEFAULT_TRANSFER_FRACTION_PER_SECOND, DEFAULT_NEIGHBOUR_CAP_RATIO,
            DEFAULT_MIN_RATE_PER_SECOND);
    }
    public BetaTransferEngine(double fractionPerSecond, double capRatio, double minTransferRatePerSecond) {
        if (!Double.isFinite(fractionPerSecond) || fractionPerSecond < 0 || fractionPerSecond > 0.01)
            throw new IllegalArgumentException("Beta transfer fraction must be in [0,0.01]");
        if (!Double.isFinite(capRatio) || capRatio <= 0 || capRatio >= 1)
            throw new IllegalArgumentException("Cap ratio must be in (0,1)");
        if (!Double.isFinite(minTransferRatePerSecond) || minTransferRatePerSecond < 0)
            throw new IllegalArgumentException("Invalid rate threshold");
        this.fractionPerSecond = fractionPerSecond;
        this.capRatio = capRatio;
        this.minTransferRatePerSecond = minTransferRatePerSecond;
    }

    public Result step(Map<BetaPosition, BetaCell> source, Predicate<BetaPosition> eligible,
                       BetaIsotopeLookup isotopes, double deltaSeconds) {
        if (source == null || eligible == null || isotopes == null)
            throw new IllegalArgumentException("Missing Beta simulation input");
        // No world-copying, no catch-up over huge offline intervals.
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0 || deltaSeconds > 1.0)
            throw new IllegalArgumentException("Simulated transfer step must be (0,1] second");

        // Stable position ordering even if host chunk map is a HashMap.
        TreeMap<BetaPosition, BetaCell> snapshot = new TreeMap<>(source);
        List<Proposal> proposals = new ArrayList<>();
        Map<Target, Double> targetOfferedCaps = new HashMap<>();
        int feedingPositions = 0;

        for (var e : snapshot.entrySet()) {
            BetaPosition from = e.getKey();
            BetaCell cell = e.getValue();
            if (!eligible.test(from) || cell == null || cell.isEmpty()) continue;
            boolean fed = false;
            // TreeMap ensures source isotope ordering is deterministic.
            for (var entry : new TreeMap<>(cell.isotopes()).entrySet()) {
                String id = entry.getKey();
                BetaIsotope iso = isotopes.find(id);
                if (iso == null || iso.betaRatePerUnit() <= 0) continue;
                BetaLoad donor = entry.getValue();
                double strength = donor.quantity() * iso.betaRatePerUnit();
                if (strength <= 0 || strength * fractionPerSecond < minTransferRatePerSecond) continue;

                List<BetaPosition> recipients = new ArrayList<>(6);
                for (BetaPosition to : from.neighbours()) {
                    if (!eligible.test(to)) continue; // includes unloaded-chunk, air and void guards
                    BetaCell dest = snapshot.getOrDefault(to, BetaCell.empty());
                    double destStrength = dest.get(id).quantity() * iso.betaRatePerUnit();
                    if (strength <= destStrength + EPS) continue; // no reverse leakage uphill
                    recipients.add(to);
                }
                if (recipients.isEmpty()) continue;
                double each = donor.quantity() * fractionPerSecond * deltaSeconds / recipients.size();
                double offeredCap = Math.max(donor.capRadPerSecond(), strength) * capRatio;
                for (BetaPosition to : recipients) {
                    Target t = new Target(to, id);
                    targetOfferedCaps.merge(t, offeredCap, Math::max);
                    proposals.add(new Proposal(from, to, id, each));
                }
                fed = true;
            }
            if (fed) feedingPositions++;
        }

        // All inflows competing for the same target/cap receive proportional shares.
        Map<Target, Double> requestedByTarget = new HashMap<>();
        for (Proposal p : proposals)
            requestedByTarget.merge(new Target(p.to(), p.isotope()), p.quantity(), Double::sum);

        Map<Target, Double> allocationScale = new HashMap<>();
        for (var entry : requestedByTarget.entrySet()) {
            Target target = entry.getKey();
            BetaIsotope iso = isotopes.find(target.isotope());
            if (iso == null || iso.betaRatePerUnit() <= 0) continue;
            BetaLoad before = snapshot.getOrDefault(target.position(), BetaCell.empty()).get(target.isotope());
            double cap = Math.max(before.capRadPerSecond(),
                targetOfferedCaps.getOrDefault(target, 0.0));
            double room = Math.max(0, cap / iso.betaRatePerUnit() - before.quantity());
            allocationScale.put(target, Math.min(1, room / entry.getValue()));
        }

        // Only after all proposals and cap scales are fixed do we apply changes.
        Map<Target, Double> quantitiesDelta = new HashMap<>();
        Map<Target, Double> actualCapByTarget = new HashMap<>();
        TreeMap<String, Double> movedByIsotope = new TreeMap<>();
        int applied = 0;
        for (Proposal p : proposals) {
            Target t = new Target(p.to(), p.isotope());
            double moved = p.quantity() * allocationScale.getOrDefault(t, 0.0);
            if (moved <= EPS) continue;
            quantitiesDelta.merge(new Target(p.from(), p.isotope()), -moved, Double::sum);
            quantitiesDelta.merge(t, moved, Double::sum);
            actualCapByTarget.merge(t, targetOfferedCaps.get(t), Math::max);
            movedByIsotope.merge(p.isotope(), moved, Double::sum);
            applied++;
        }

        TreeSet<BetaPosition> affected = new TreeSet<>(snapshot.keySet());
        for (Target target : quantitiesDelta.keySet()) affected.add(target.position());
        TreeMap<BetaPosition, BetaCell> updated = new TreeMap<>();
        for (BetaPosition position : affected) {
            TreeMap<String, BetaLoad> isotopesAtPosition = new TreeMap<>(
                snapshot.getOrDefault(position, BetaCell.empty()).isotopes());
            TreeSet<String> isotopeKeys = new TreeSet<>(isotopesAtPosition.keySet());
            for (Target t : quantitiesDelta.keySet())
                if (t.position().equals(position)) isotopeKeys.add(t.isotope());
            for (String id : isotopeKeys) {
                Target t = new Target(position, id);
                BetaLoad old = isotopesAtPosition.getOrDefault(id, new BetaLoad(0, 0));
                double quantity = old.quantity() + quantitiesDelta.getOrDefault(t, 0.0);
                if (quantity <= EPS) { isotopesAtPosition.remove(id); continue; }
                BetaIsotope iso = isotopes.find(id);
                double currentActivity = iso == null ? 0 : quantity * iso.betaRatePerUnit();
                double cap = Math.max(Math.max(old.capRadPerSecond(),
                    actualCapByTarget.getOrDefault(t, 0.0)), currentActivity);
                isotopesAtPosition.put(id, new BetaLoad(quantity, cap));
            }
            BetaCell cell = new BetaCell(isotopesAtPosition);
            if (!cell.isEmpty()) updated.put(position, cell);
        }
        return new Result(Map.copyOf(updated),
            new Statistics(feedingPositions, proposals.size(), applied, Map.copyOf(movedByIsotope)));
    }

    public static Map<String, Double> isotopeTotals(Map<BetaPosition, BetaCell> cells) {
        TreeMap<String, Double> totals = new TreeMap<>();
        cells.values().forEach(cell -> cell.isotopes().forEach((id, load) ->
            totals.merge(id, load.quantity(), Double::sum)));
        return totals;
    }

    private record Target(BetaPosition position, String isotope) { }
    private record Proposal(BetaPosition from, BetaPosition to, String isotope, double quantity) { }
    public record Statistics(int feedingPositions, int proposedEdges,
                             int appliedEdges, Map<String, Double> movedByIsotope) { }
    public record Result(Map<BetaPosition, BetaCell> cells, Statistics statistics) { }
}
