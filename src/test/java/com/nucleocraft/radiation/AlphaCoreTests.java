package com.nucleocraft.radiation;

import com.nucleocraft.radiation.core.*;
import com.nucleocraft.radiation.radiation.profiles.*;
import java.util.*;

/** Offline Java 21 regression suite. Zero NeoForge dependencies. */
public final class AlphaCoreTests {
    private static long checks;
    private static void ok(boolean valid,String reason) {checks++;if(!valid)throw new AssertionError(reason);}
    private static void near(double expected,double actual,double epsilon,String reason) {
        ok(Math.abs(expected-actual)<=epsilon,reason+" expected="+expected+" actual="+actual);
    }
    public static void main(String[] args) {
        isotopeDefinitions();lazyDecay();conservation();spatialDistribution();internalExposure();dustInteractionPolicy();validation();
        System.out.printf(Locale.ROOT,"Alpha-only core: %,d assertions PASSED%n",checks);
    }
    private static void isotopeDefinitions() {
        ok(IsotopeDefinitions.all().size()==3,"Three alpha isotopes");
        ok(IsotopeDefinitions.require("polonium_210").displayName().equals("Polonium 210"),"Human label");
        near(.5,IsotopeDefinitions.require("polonium_210").remainingFraction(3600),1e-12,"Half life");
        near(0,AlphaRadiationProfile.INSTANCE.externalAbsorbedRate(100000),0,"No external alpha absorbed");
        near(50,AlphaRadiationProfile.INSTANCE.internalAbsorbedRate(10),1e-12,"Weighted internal alpha");
    }
    private static void lazyDecay() {
        IsotopeInventory start=IsotopeInventory.of("polonium_210",100,1_000_000L);
        near(100,start.totalUnits(),1e-12,"Original material");
        near(50,start.at(4_600_000L).totalUnits(),1e-8,"3600 second half life");
        near(25,start.at(8_200_000L).totalUnits(),1e-8,"Two half lives");
        near(100,start.totalUnits(),1e-12,"Lazy never mutates stored original");
        near(180,start.alphaEmissions(1_000_000L),1e-12,"Derived alpha emission");
        near(90,start.alphaEmissions(4_600_000L),1e-8,"Derived emission decays");
        near(100,start.at(0).totalUnits(),1e-8,"System clock rollback clamped");
    }
    private static void conservation() {
        Random random=new Random(1789);
        long now=50_000;
        for(int i=0;i<10000;i++) {
            double q=1+random.nextDouble()*10000;
            var isotope=i%2==0?"uranium_238":"polonium_210";
            IsotopeInventory m=IsotopeInventory.of(isotope,q,now);
            double portion=random.nextDouble();
            IsotopeInventory.Split split=m.split(portion);
            near(q,split.remaining().totalUnits()+split.moved().totalUnits(),Math.max(1e-8,q*1e-12),"Conserved split");
            near(q,split.remaining().plus(split.moved(),now).totalUnits(),Math.max(1e-8,q*1e-12),"Conserved recombination");
            near(q,m.totalUnits(),1e-8,"Donor immutable");
            IsotopeInventory later=m.at(now+100_000);
            near(later.totalUnits(),split.remaining().at(now+100_000).plus(
                    split.moved().at(now+100_000),now+100_000).totalUnits(),Math.max(1e-8,q*1e-12),
                    "Lazy decay independent of transport");
        }
        IsotopeInventory mix=new IsotopeInventory(Map.of("uranium_238",2.0,"polonium_210",3.0),now);
        near(5,mix.totalUnits(),1e-12,"Multi-isotope composition");
        near(2+5.4,mix.alphaEmissions(now),1e-12,"Multi-isotope spectrum additive");
    }
    private static void spatialDistribution() {
        var cases=List.of(new AlphaDustDistribution.Target<>("source",0),
                new AlphaDustDistribution.Target<>("near",1),
                new AlphaDustDistribution.Target<>("far",2));
        var portions=AlphaDustDistribution.normalized(cases);
        near(4.0/7.0,portions.get(0).fraction(),1e-12,"Source weight");
        near(2.0/7.0,portions.get(1).fraction(),1e-12,"1 block 50% weight");
        near(1.0/7.0,portions.get(2).fraction(),1e-12,"2 blocks 25% weight");
        near(1,portions.stream().mapToDouble(AlphaDustDistribution.Portion::fraction).sum(),1e-12,
                "No mass amplification");
        ok(AlphaDustDistribution.manhattan(0,1,0)==1,"Upward step included");
        ok(AlphaDustDistribution.manhattan(0,-1,0)==1,"Downward step included");
        ok(AlphaDustDistribution.normalized(List.of()).isEmpty(),"No support does not invent recipients");
        for(int i=0;i<800;i++) {
            List<AlphaDustDistribution.Target<Integer>> targets=new ArrayList<>();
            for(int j=0;j<1+(i%27);j++)targets.add(new AlphaDustDistribution.Target<>(j,j%3));
            double total=AlphaDustDistribution.normalized(targets).stream()
                    .mapToDouble(AlphaDustDistribution.Portion::fraction).sum();
            near(1,total,1e-12,"Normalized release total");
        }
    }
    private static void internalExposure() {
        long now=5_000_000;
        PlayerAlphaState player=new PlayerAlphaState(now);
        near(0,player.internalDoseRate(now),0,"Clean player");
        player.inhale(IsotopeInventory.of("polonium_210",5,now),now);
        near(45,player.internalDoseRate(now),1e-12,"Internal Po210 5x weighted");
        player.tickDose(now,2);
        near(90,player.accumulatedRad(),1e-10,"Dose integrates exposure");
        player.advance(now+60_000);
        ok(player.internalDoseRate(now+60_000)<45,"Nuclear and biological decay");
        ok(player.internalDoseRate(now+60_000)>0,"Contamination persists after exposure");
        player.ingest(IsotopeInventory.of("uranium_238",2,now+60_000),now+60_000);
        ok(player.ingested().totalUnits()>1.99,"Ingestion separate compartment");
        player.reset(now+60_000);
        near(0,player.accumulatedRad(),0,"Reset historical dose");
        near(0,player.internalDoseRate(now+60_000),0,"Reset internal material");
    }
    private static void dustInteractionPolicy() {
        ok(AlphaDustInteraction.retainsOnBreak(1),"Final one-layer dust is retained");
        for (int layers=2;layers<=8;layers++)
            ok(!AlphaDustInteraction.retainsOnBreak(layers),"Thicker dust may release and spread: "+layers);
        for (int flags=0;flags<32;flags++) {
            boolean flying=(flags&1)!=0, gliding=(flags&2)!=0, riding=(flags&4)!=0;
            boolean grounded=(flags&8)!=0, fromGroundJump=(flags&16)!=0;
            boolean expected=!flying&&!gliding&&!riding&&(grounded||fromGroundJump);
            ok(expected==AlphaDustInteraction.canDisturb(flying,gliding,riding,grounded,fromGroundJump),
                    "Ground contact policy "+flags);
        }
        ok(AlphaDustInteraction.canDisturb(false,false,false,true,false),"Walking stirs dust");
        ok(AlphaDustInteraction.canDisturb(false,false,false,false,true),"Ground-launched jump stirs dust");
        ok(!AlphaDustInteraction.canDisturb(true,false,false,true,true),"Creative flight never stirs dust");
        ok(!AlphaDustInteraction.canDisturb(false,true,false,false,true),"Elytra flight never stirs dust");
        ok(!AlphaDustInteraction.canDisturb(false,false,false,false,false),"Airborne non-contact never stirs dust");
        try {AlphaDustInteraction.retainsOnBreak(0);throw new AssertionError("Must reject invalid dust layer count");}
        catch (IllegalArgumentException expected) {checks++;}
    }
    private static void validation() {
        try{IsotopeDefinitions.require("cesium_137");throw new AssertionError("Should reject Beta-only isotope in Alpha-only registry");}
        catch(IllegalArgumentException expected){checks++;}
        try{IsotopeInventory.of("polonium_210",-1,0);throw new AssertionError("negative isotope");}
        catch(IllegalArgumentException expected){checks++;}
        try{IsotopeInventory.of("polonium_210",Double.POSITIVE_INFINITY,0);throw new AssertionError("infinite isotope");}
        catch(IllegalArgumentException expected){checks++;}
        try{IsotopeInventory.of("polonium_210",1,0).scaled(1.1);throw new AssertionError("split > 100%");}
        catch(IllegalArgumentException expected){checks++;}
    }
}
