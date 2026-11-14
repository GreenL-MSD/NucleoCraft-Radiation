package com.nucleocraft.radiation.server.beta;
import com.nucleocraft.radiation.radiation.beta.BetaShielding;
import com.nucleocraft.radiation.radiation.beta.BetaTransferEngine;

import com.nucleocraft.radiation.radiation.beta.*;
import com.nucleocraft.radiation.radiation.profiles.beta.BetaRadiationProfile;
import com.nucleocraft.radiation.server.data.RadiationAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Budgeted real-world Beta host. Reads the SAME chunk deposits as Alpha.
 * Never forces a chunk and never catches up physical transfer while unloaded.
 */
public final class BetaSimulation {
    private final BetaTransferEngine transfer=new BetaTransferEngine();
    private final BetaRadiationProfile profile=new BetaRadiationProfile();
    private long processedEdges;
    private long ticks;
    public long processedEdges(){return processedEdges;}
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        if(++ticks%20!=0)return;
        long now=System.currentTimeMillis();
        for(ServerLevel level:event.getServer().getAllLevels()) {
            List<BetaWorld.ServerLevelChunk> active=BetaWorld.playerChunks(level,1);
            if(active.isEmpty())continue;
            var before=BetaWorld.snapshot(level,active,now,512,(ticks/20L)*512L);
            if(!before.isEmpty()) {
                var result=transfer.step(before,p->BetaWorld.eligible(level,BetaWorld.block(p)),BetaWorld.ISOTOPES,1.0);
                BetaWorld.commit(level,before,result.cells(),now);
                processedEdges+=result.statistics().appliedEdges();
            }
            // Read-only external Beta dose, including synthetic blocks, within 4 m.
            for(var player:level.players()) {
                if(!player.isAlive()||player.isSpectator())continue;
                double dose=0;
                BlockPos center=player.blockPosition();
                for(var e:before.entrySet()) {
                    BlockPos pos=BetaWorld.block(e.getKey());
                    if(pos.distSqr(center)>36)continue;
                    double rate=BetaWorld.betaAt(level,pos,now);
                    double distance=player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos));
                    dose+=profile.externalRate(rate,distance,shielding(level,pos,player.blockPosition()));
                }
                // Synthetic sources may exist without a physical Beta isotope deposit.
                for(BetaWorld.ServerLevelChunk cp:active) {
                    var c=level.getChunkSource().getChunkNow(cp.x(),cp.z());
                    if(c==null||!c.hasData(RadiationAttachments.CHUNK.get()))continue;
                    for(var e:c.getData(RadiationAttachments.CHUNK.get()).syntheticBetaSnapshot().entrySet()) {
                        BlockPos pos=com.nucleocraft.radiation.server.data.ChunkRadiationData.unpack(cp.x(),cp.z(),e.getKey());
                        if(pos.distSqr(center)>36||before.containsKey(BetaWorld.key(pos)))continue;
                        double dist=player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos));
                        dose+=profile.externalRate(e.getValue(),dist,shielding(level,pos,center));
                    }
                }
                var attached=player.getData(RadiationAttachments.PLAYER.get());
                dose+=profile.internalRate(attached.state().inhaled().at(now).units(),BetaWorld.ISOTOPES);
                dose+=profile.internalRate(attached.state().ingested().at(now).units(),BetaWorld.ISOTOPES);
                attached.state().addAbsorbedDose(dose); // one-second interval
                player.setData(RadiationAttachments.PLAYER.get(),attached);
            }
        }
    }
    private static double shielding(ServerLevel level,BlockPos from,BlockPos target) {
        int steps=Math.max(1,(int)Math.ceil(Math.sqrt(from.distSqr(target))*2.0));
        double factor=1;
        java.util.Set<BlockPos> visited=new java.util.HashSet<>();
        // Samples BETWEEN source and target. No chunk loads, skip source and endpoint.
        for(int i=1;i<steps;i++) {
            double t=(double)i/steps;
            BlockPos p=BlockPos.containing(from.getX()+.5+(target.getX()-from.getX())*t,
                from.getY()+.5+(target.getY()-from.getY())*t,
                from.getZ()+.5+(target.getZ()-from.getZ())*t);
            if(!visited.add(p)||!com.nucleocraft.radiation.server.alpha.AlphaWorld.loaded(level,p))continue;
            String id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString();
            factor*=BetaShielding.suggestedTransmission(id);
        }
        return Math.max(0,Math.min(1,factor));
    }
}
