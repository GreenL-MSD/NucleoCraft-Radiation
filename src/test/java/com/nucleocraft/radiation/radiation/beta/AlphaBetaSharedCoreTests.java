package com.nucleocraft.radiation.radiation.beta;

import com.nucleocraft.radiation.core.*;
import com.nucleocraft.radiation.player.alpha.PlayerAlphaState;
import com.nucleocraft.radiation.radiation.profiles.alpha.AlphaRadiationProfile;
import com.nucleocraft.radiation.radiation.profiles.beta.BetaRadiationProfile;
import java.util.*;

/** Core-level bridge regression, no NeoForge dependency. */
public final class AlphaBetaSharedCoreTests {
    private static long assertions;
    private static void ok(boolean test,String message){assertions++;if(!test)throw new AssertionError(message);}
    public static void main(String[] args){
        long epoch=1_000_000;
        var alpha=IsotopeInventory.of("polonium_210",120,epoch);
        var beta=IsotopeInventory.of("strontium_90",120,epoch);
        var cesium=IsotopeInventory.of("cesium_137",10,epoch);
        ok(alpha.alphaEmissions(epoch)>0&&alpha.betaEmissions(epoch)==0,"Alpha-only identity");
        ok(beta.betaEmissions(epoch)>0&&beta.alphaEmissions(epoch)==0,"Beta-only identity");
        ok(cesium.betaEmissions(epoch)>0&&cesium.alphaEmissions(epoch)==0,"Cesium has no Alpha");
        ok(AlphaRadiationProfile.INSTANCE.externalAbsorbedRate(100)==0,"Alpha external intact skin");
        ok(new BetaRadiationProfile().externalRate(10,5,1)==0,"Beta limited range");
        var mixed=alpha.plus(beta,epoch).plus(cesium,epoch);
        ok(mixed.units().size()==3,"Physical material inventory is shared");
        ok(mixed.at(epoch+3600000).totalUnits()<mixed.totalUnits(),"Isotope decay on demand");
        Random rng=new Random(1771);
        for(int i=0;i<20000;i++){
            double fraction=rng.nextDouble();
            var split=mixed.split(fraction);
            for(String id:mixed.units().keySet()){
                double total=split.remaining().units().getOrDefault(id,0d)+split.moved().units().getOrDefault(id,0d);
                ok(Math.abs(total-mixed.units().get(id))<1e-9,"split conserves "+id);
            }
        }
        var player=new PlayerAlphaState(epoch);
        player.inhale(beta,epoch);
        ok(player.internalAlphaEmission(epoch)==0,"Beta ingestion adds zero Alpha");
        ok(new BetaRadiationProfile().internalRate(player.inhaled().units(),id->{
            var d=IsotopeDefinitions.all().get(id);
            return d==null?null:new BetaIsotope(id,d.betaEmissionPerUnit(),1.0);
        })>0,"Beta inhalation can be evaluated from shared player data");
        player.inhale(alpha,epoch);
        ok(player.internalAlphaEmission(epoch)>0,"Alpha inhalation unchanged");
        ok(player.internalDoseRate(epoch)>0,"Alpha internal dose unchanged");
        var loc=new BetaPosition(0,64,0);
        var result=new BetaTransferEngine().step(Map.of(loc,new BetaCell(Map.of(
            "polonium_210",new BetaLoad(100,0),
            "strontium_90",new BetaLoad(100,100)))),
            pos->Math.abs(pos.x())<=1&&Math.abs(pos.z())<=1&&pos.y()>=63&&pos.y()<=65,
            id->{var d=IsotopeDefinitions.all().get(id);return d==null?null:new BetaIsotope(id,d.betaEmissionPerUnit(),1);},1);
        ok(result.cells().get(loc).get("polonium_210").quantity()==100,"Beta does not move Alpha-only material");
        ok(Math.abs(BetaTransferEngine.isotopeTotals(result.cells()).get("strontium_90")-100)<1e-9,
            "Beta matter conserved independently of Alpha");
        System.out.println("Shared Alpha/Beta regression: "+assertions+" assertions passed");
    }
}
