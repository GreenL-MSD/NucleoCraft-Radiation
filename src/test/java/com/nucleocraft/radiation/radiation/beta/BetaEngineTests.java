package com.nucleocraft.radiation.radiation.beta;

import com.nucleocraft.radiation.radiation.profiles.beta.BetaRadiationProfile;
import com.nucleocraft.radiation.server.beta.BetaWorldPort;
import com.nucleocraft.radiation.server.beta.BetaWorldRunner;
import com.nucleocraft.radiation.commands.beta.BetaDiagnostics;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class BetaEngineTests {
    private static final BetaPosition ZERO = new BetaPosition(0, 64, 0);
    private static final BetaIsotopeLookup isotopes = id -> switch (id) {
        case "strontium_90" -> new BetaIsotope(id, 1.0, 1.5);
        case "cesium_137" -> new BetaIsotope(id, 0.55, 1.2);
        case "polonium_210" -> new BetaIsotope(id, 0, 0);
        default -> null;
    };
    private static long assertions;
    private static void ok(boolean b, String label) { assertions++; if (!b) throw new AssertionError(label); }
    private static void close(double a, double b, double tol, String label) {
        ok(Double.isFinite(a) && Double.isFinite(b) && Math.abs(a-b)<=tol, label+" actual="+a+" expected="+b);
    }
    private static Map<BetaPosition, BetaCell> seeded(double strength) {
        return Map.of(ZERO, BetaCell.single("strontium_90",strength,1));
    }
    private static BetaTransferEngine.Result step(Map<BetaPosition,BetaCell> s, int r, double dt) {
        return new BetaTransferEngine().step(s, p -> Math.abs(p.x())<=r && Math.abs(p.z())<=r
                && p.y()>=64-r && p.y()<=64+r, isotopes, dt);
    }
    private static void defaultsAndAllSixFaces() {
        var result=step(seeded(100), 1, 1);
        var cells=result.cells();
        ok(cells.size()==7,"six distinct neighbouring blocks plus source");
        close(cells.get(ZERO).get("strontium_90").quantity(),99.25,1e-11,"donor debit");
        for(var p:ZERO.neighbours()) {
            close(cells.get(p).get("strontium_90").quantity(),0.125,1e-11,"six-way split "+p);
            close(cells.get(p).get("strontium_90").capRadPerSecond(),90,1e-11,"90% cap");
        }
        close(BetaTransferEngine.isotopeTotals(cells).get("strontium_90"),100,1e-10,"full conservation");
        ok(result.statistics().appliedEdges()==6,"stats edges");
    }
    private static void noSameTickCascade() {
        var result=step(seeded(100),3,1).cells();
        ok(result.containsKey(new BetaPosition(1,64,0)),"one step");
        ok(!result.containsKey(new BetaPosition(2,64,0)),"no leap through clean neighbour");
        for(int i=0;i<40;i++) result=step(result,3,1).cells();
        ok(result.containsKey(new BetaPosition(2,64,0)),"slow expansion on later ticks");
        close(result.get(new BetaPosition(2,64,0)).get("strontium_90").capRadPerSecond(),81,1e-8,"successive 90% cap");
    }
    private static void thresholdAndNoBetaIsotopes() {
        var cells=step(seeded(1),3,1).cells();
        ok(cells.size()==1,"0.0075 RAD/s under 0.01 threshold");
        var alpha=new BetaCell(Map.of("polonium_210",new BetaLoad(1_000,0)));
        cells=step(Map.of(ZERO,alpha),3,1).cells();
        ok(cells.size()==1,"pure Alpha isotope does not leak via Beta");
        ok(cells.get(ZERO).get("polonium_210").quantity()==1000,"Alpha isotope unchanged");
        cells=step(Map.of(ZERO,new BetaCell(Map.of("unknown_mineral", new BetaLoad(10,0)))),2,1).cells();
        ok(cells.size()==1,"unknown isotope not turned Beta");
    }
    private static void noUnloadedTransfer() {
        var cells=new BetaTransferEngine().step(seeded(100),
            p->p.y()==64 && p.x()>=0 && p.x()<=1 && p.z()==0, isotopes, 1).cells();
        ok(cells.size()==2,"only loaded neighbour");
        close(cells.get(new BetaPosition(1,64,0)).get("strontium_90").quantity(),.75,1e-10,"budget to valid face");
        close(BetaTransferEngine.isotopeTotals(cells).get("strontium_90"),100,1e-10,"no lost chunk isotope");
    }
    private static void multipleDonorsAndCap() {
        var to=new BetaPosition(0,64,0);
        var left=new BetaPosition(-1,64,0); var right=new BetaPosition(1,64,0);
        var m=Map.of(left,BetaCell.single("strontium_90",100,1),
            right,BetaCell.single("strontium_90",100,1),
            to,BetaCell.single("strontium_90",89.9,1));
        var r=new BetaTransferEngine().step(m,p->p.z()==0 && p.y()==64 && Math.abs(p.x())<=1,isotopes,1);
        // Both donors may feed only up to target cap = 90, total 0.1 units.
        close(r.cells().get(to).get("strontium_90").quantity(),90,1e-9,"target capped");
        close(BetaTransferEngine.isotopeTotals(r.cells()).get("strontium_90"),289.9,1e-9,"dual-donor conservation");
        close(r.cells().get(to).get("strontium_90").capRadPerSecond(),90,1e-9,"90% target cap");
    }
    private static void existingCapNotReduced() {
        var to=new BetaPosition(1,64,0);
        var m=Map.of(ZERO,BetaCell.single("strontium_90",10,1),
            to,new BetaCell(Map.of("strontium_90",new BetaLoad(.5,99))));
        var r=step(m,2,1).cells();
        close(r.get(to).get("strontium_90").capRadPerSecond(),99,1e-10,"preserve larger existing cap");
    }
    private static void profileAndShielding() {
        var profile=new BetaRadiationProfile();
        close(profile.externalRate(20,0,1),20,1e-10,"near source");
        ok(profile.externalRate(20,1,1)<20,"air distance falloff");
        ok(profile.externalRate(20,4.1,1)==0,"beta finite range");
        close(profile.externalRate(20,1,0),0,0,"complete shield");
        close(profile.externalRate(20,1,0.5)*2,profile.externalRate(20,1,1),1e-10,"multiplicative shield");
        close(BetaShielding.combine(.5,.2),.1,1e-10,"multi-layer stacking");
        close(profile.internalRate(Map.of("strontium_90",2.0,"polonium_210",100.0),isotopes),3,1e-10,"only Beta internal");
        ok(profile.internalRate(Map.of("cesium_137",1.0),isotopes)>0,"inhaled Beta isotopes");
        ok(new BetaRadiationProfile().externalRate(10,1,1)>0,"configured Beta emission alone");
    }
    private static void deterministicRandomConservation() {
        Random random=new Random(192513);
        var m=new HashMap<BetaPosition,BetaCell>();
        for(int i=0;i<60;i++) {
            BetaPosition p=new BetaPosition(random.nextInt(7),64+random.nextInt(4),random.nextInt(7));
            m.put(p,new BetaCell(Map.of(
                "strontium_90", BetaLoad.initial(3+random.nextDouble()*20,1),
                "polonium_210", BetaLoad.initial(random.nextDouble()*4,0))));
        }
        Map<String,Double> initial=BetaTransferEngine.isotopeTotals(m);
        for(int tick=0;tick<80;tick++) {
            var first=step(m,7,.05);
            var second=step(new HashMap<>(m),7,.05);
            ok(first.cells().equals(second.cells()) || equivalent(first.cells(),second.cells()),"hash order deterministic step " + tick);
            m=new HashMap<>(first.cells());
            var totals=BetaTransferEngine.isotopeTotals(m);
            for(String isotope: initial.keySet())
                close(totals.get(isotope),initial.get(isotope),1e-8,"mass preserved " + tick+" "+isotope);
            for(var entry:m.entrySet()) for(var load:entry.getValue().isotopes().entrySet()){
                ok(load.getValue().quantity()>=0,"no negative quantities");
                BetaIsotope iso=isotopes.find(load.getKey());
                if(iso!=null && iso.betaRatePerUnit()>0)
                    ok(load.getValue().quantity()*iso.betaRatePerUnit()<=load.getValue().capRadPerSecond()+1e-8,"cap respected");
            }
        }
    }
    private static boolean equivalent(Map<BetaPosition,BetaCell> a,Map<BetaPosition,BetaCell> b) {
        if(!a.keySet().equals(b.keySet()))return false;
        for(var key:a.keySet())if(!a.get(key).isotopes().equals(b.get(key).isotopes()))return false;
        return true;
    }
    private static void bridgeAndDiagnostics() {
        class FakePort implements BetaWorldPort {
            Map<BetaPosition,BetaCell> stored = new HashMap<>(seeded(100));
            int commits = 0;
            @Override public Map<BetaPosition,BetaCell> snapshotPhysicalMaterial(){return stored;}
            @Override public boolean canReceive(BetaPosition p){return p.y()==64 && p.z()==0 && p.x()>=0 && p.x()<=1;}
            @Override public void commit(Map<BetaPosition,BetaCell> before,BetaTransferEngine.Result update){
                commits++; stored = update.cells();
            }
        }
        var port=new FakePort();
        var runner=new BetaWorldRunner(new BetaTransferEngine(),isotopes);
        var stats=runner.serverTick(port,1);
        ok(stats.appliedEdges()==1,"bridge transfer");
        ok(port.commits==1,"one atomic commit");
        close(BetaTransferEngine.isotopeTotals(port.stored).get("strontium_90"),100,1e-10,"bridge conservation");
        var debug=BetaDiagnostics.inspect(port.stored.get(new BetaPosition(1,64,0)),isotopes);
        ok(debug.contains("cap=") && debug.contains("Beta="),"inspect Beta dose and cap");
        ok(debug.contains("[Strontium 90]"), "human-readable Beta isotope name");
        var player=BetaDiagnostics.player(2,Map.of("strontium_90",1.0),isotopes,new BetaRadiationProfile());
        close(player.externalRadPerSecond(),2,1e-10,"separate external");
        close(player.internalRadPerSecond(),1.5,1e-10,"separate internal");
        close(player.totalRadPerSecond(),3.5,1e-10,"combined Beta");
        port.stored=Map.of(ZERO,new BetaCell(Map.of("polonium_210", new BetaLoad(10,0))));
        runner.serverTick(port,1);
        ok(port.commits==1,"no false commit on Alpha-only cells");
    }
    private static void badInputs() {
        boolean bad=false;try {new BetaTransferEngine().step(seeded(10),p->true,isotopes,10000);}catch(IllegalArgumentException ex){bad=true;}
        ok(bad,"no unbounded offline catchup");
        bad=false;try {new BetaRadiationProfile().externalRate(-3,0,1);}catch(IllegalArgumentException ex){bad=true;}
        ok(bad,"reject impossible negative source");
    }
    public static void main(String[] args) {
        defaultsAndAllSixFaces();
        noSameTickCascade();
        thresholdAndNoBetaIsotopes();
        noUnloadedTransfer();
        multipleDonorsAndCap();
        existingCapNotReduced();
        profileAndShielding();
        deterministicRandomConservation();
        bridgeAndDiagnostics();
        badInputs();
        System.out.printf("Beta engine tests: PASS — %,d assertions%n",assertions);
    }
}
