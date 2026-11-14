package com.nucleocraft.radiation.radiation.beta;

import com.nucleocraft.radiation.server.beta.BetaLabelPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BetaLabelsTests {
    private static int count = 0;
    private static void check(boolean condition, String message) {
        ++count; if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        BetaIsotopeLookup isotopes = id -> switch (id) {
            case "cesium_137" -> new BetaIsotope(id, 0.55, 1);
            case "strontium_90" -> new BetaIsotope(id, 0.7, 1);
            case "polonium_210" -> new BetaIsotope(id, 0, 1);
            default -> null;
        };
        BetaPosition block = new BetaPosition(4, 70, -5);
        var contaminated = new BetaCell(Map.of(
            "cesium_137", new BetaLoad(10, 8),
            "polonium_210", new BetaLoad(100, 0),
            "strontium_90", new BetaLoad(1, 1)));
        var label = BetaBlockLabel.fromCell(block, contaminated, isotopes).orElseThrow();
        check(Math.abs(label.betaRadPerSecond() - 6.2) < 1e-9, "Beta sum");
        check(Math.abs(label.capRadPerSecond() - 9) < 1e-9, "Beta cap sum");
        check(Math.abs(label.quantity() - 11) < 1e-9, "Alpha-only material excluded");
        check(label.isotope().equals("Cesium 137"), "Readable dominant isotope");
        check(label.additionalIsotopes() == 1, "Additional Beta isotope count");
        check(label.header().contains("RAD/s"), "Units");
        check(label.material().contains("+1"), "Mixture formatting");
        check(label.positionLine().equals("x4 y70 z-5"), "Coordinates");
        check(BetaBlockLabel.fromCell(block, BetaCell.single("polonium_210", 10, 0), isotopes).isEmpty(), "Pure Alpha ignored");
        check(BetaBlockLabel.fromCell(block, BetaCell.empty(), isotopes).isEmpty(), "Empty ignored");
        var synthetic = BetaBlockLabel.synthetic(new BetaPosition(5,64,5), 10);
        check(synthetic.isSynthetic() && synthetic.material().contains("configured"), "Synthetic source label");
        check(synthetic.header().contains("10.000 RAD/s"), "Synthetic rate");
        var positions = new ArrayList<BetaBlockLabel>();
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                positions.add(new BetaBlockLabel(new BetaPosition(x,70,z), "Cesium 137",0,1,1,2));
            }
        }
        BetaPosition player = new BetaPosition(0,70,0);
        var near = BetaLabelSelection.select(new BetaLabelSelection.Settings(BetaLabelSelection.Mode.NEARBY, 8),
            player, Optional.empty(), positions);
        check(near.size() == BetaLabelSelection.MAX_LABELS, "Bound max number of labels");
        check(near.getFirst().pos().equals(player), "Closest-first sorting");
        for (int i = 1; i < near.size(); i++) {
            var a = near.get(i-1).pos(); var b = near.get(i).pos();
            long da = (long)a.x()*a.x()+(long)a.z()*a.z();
            long db = (long)b.x()*b.x()+(long)b.z()*b.z();
            check(da <= db, "Sorting stable");
        }
        var look = BetaLabelSelection.select(new BetaLabelSelection.Settings(BetaLabelSelection.Mode.LOOK, 12),
            player, Optional.of(new BetaPosition(3,70,3)), positions);
        check(look.size()==1 && look.getFirst().pos().equals(new BetaPosition(3,70,3)), "Look targeting");
        check(BetaLabelSelection.select(BetaLabelSelection.Settings.off(), player, Optional.empty(), positions).isEmpty(),"Off mode");
        check(BetaLabelSelection.select(new BetaLabelSelection.Settings(BetaLabelSelection.Mode.LOOK, 12),player,Optional.empty(),positions).isEmpty(),"Look no target");
        check(BetaLabelSelection.select(new BetaLabelSelection.Settings(BetaLabelSelection.Mode.NEARBY, 1),player,Optional.empty(),positions).size() == 5,"Radius inclusive");
        var prefs = new BetaLabelPreferences(); UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        prefs.set(first,new BetaLabelSelection.Settings(BetaLabelSelection.Mode.LOOK,10));
        prefs.set(second,new BetaLabelSelection.Settings(BetaLabelSelection.Mode.NEARBY,5));
        check(prefs.activePlayerCount()==2, "Per-player preferences");
        prefs.remove(first); check(prefs.get(first).mode()==BetaLabelSelection.Mode.OFF,"Logout cleanup");
        check(prefs.get(second).mode()==BetaLabelSelection.Mode.NEARBY,"Player isolation");
        prefs.clear(); check(prefs.activePlayerCount()==0,"World cleanup");
        boolean invalid=false;
        try { new BetaLabelSelection.Settings(BetaLabelSelection.Mode.NEARBY, 99); } catch (IllegalArgumentException e) { invalid=true; }
        check(invalid,"Radius limited");
        invalid=false;
        try { new BetaBlockLabel(block,"X",0,Double.NaN,1,1); } catch (IllegalArgumentException e) {invalid=true;}
        check(invalid,"Reject NaN");
        System.out.println("Beta label tests: " + count + " assertions passed");
    }
}
