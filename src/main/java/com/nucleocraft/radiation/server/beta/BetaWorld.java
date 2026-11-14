package com.nucleocraft.radiation.server.beta;
import com.nucleocraft.radiation.radiation.beta.BetaCell;
import com.nucleocraft.radiation.radiation.beta.BetaIsotope;
import com.nucleocraft.radiation.radiation.beta.BetaIsotopeLookup;
import com.nucleocraft.radiation.radiation.beta.BetaLoad;
import com.nucleocraft.radiation.radiation.beta.BetaPosition;

import com.nucleocraft.radiation.core.IsotopeDefinition;
import com.nucleocraft.radiation.core.IsotopeDefinitions;
import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.radiation.beta.*;
import com.nucleocraft.radiation.server.alpha.AlphaWorld;
import com.nucleocraft.radiation.server.data.ChunkRadiationData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.*;

/** Bridge to the one authoritative physical isotope inventory stored by Alpha's validated chunk attachment.
 * No second isotope inventory is persisted. Beta caps and synthetic Beta sources are separate metadata.
 */
public final class BetaWorld {
    private BetaWorld() {}
    public static final BetaIsotopeLookup ISOTOPES=id->{
        IsotopeDefinition d=IsotopeDefinitions.all().get(id);
        return d==null?null:new BetaIsotope(id,d.betaEmissionPerUnit(),1.0);
    };
    public static BetaPosition key(BlockPos pos){return new BetaPosition(pos.getX(),pos.getY(),pos.getZ());}
    public static BlockPos block(BetaPosition pos){return new BlockPos(pos.x(),pos.y(),pos.z());}
    public static boolean eligible(ServerLevel level,BlockPos pos) {
        if(pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||!AlphaWorld.loaded(level,pos))return false;
        var s=level.getBlockState(pos);
        return !s.isAir()&&!s.is(Blocks.BEDROCK)&&!s.getFluidState().isSource();
    }
    public static BetaCell at(ServerLevel level,BlockPos pos,long now) {
        if(!eligible(level,pos))return BetaCell.empty();
        ChunkRadiationData.Deposit record=AlphaWorld.deposit(level,pos);
        if(record==null||record.material().isEmpty())return BetaCell.empty();
        ChunkRadiationData chunk=AlphaWorld.readChunk(level,pos);
        Map<String,BetaLoad> map=new TreeMap<>();
        record.material().at(now).units().forEach((id,quantity)->{
            BetaIsotope isotope=ISOTOPES.find(id);
            double rate=isotope==null?0:isotope.betaRatePerUnit();
            map.put(id,new BetaLoad(quantity,chunk==null?quantity*rate:chunk.betaCap(pos,id,quantity*rate)));
        });
        return new BetaCell(map);
    }
    public static double synthetic(ServerLevel level,BlockPos pos){
        ChunkRadiationData c=AlphaWorld.readChunk(level,pos);return c==null?0:c.syntheticBeta(pos);
    }
    public static void synthetic(ServerLevel level,BlockPos pos,double rate){
        if(!AlphaWorld.loaded(level,pos))return;
        LevelChunk chunk=level.getChunkAt(pos);
        chunk.getData(com.nucleocraft.radiation.server.data.RadiationAttachments.CHUNK.get()).setSyntheticBeta(pos,rate);
        chunk.setUnsaved(true);
    }
    public static Map<BetaPosition,BetaCell> snapshot(ServerLevel level, Collection<ServerLevelChunk> chunks,
                                                      long now,int limit,long rotation) {
        TreeSet<BetaPosition> sources=new TreeSet<>();
        for(ServerLevelChunk place:chunks){
            if(place.level()!=level)continue;
            LevelChunk chunk=level.getChunkSource().getChunkNow(place.x(),place.z());
            if(chunk==null||!chunk.hasData(com.nucleocraft.radiation.server.data.RadiationAttachments.CHUNK.get()))continue;
            for(var entry:chunk.getData(com.nucleocraft.radiation.server.data.RadiationAttachments.CHUNK.get()).depositsSnapshot().entrySet()) {
                BlockPos p=ChunkRadiationData.unpack(place.x(),place.z(),entry.getKey());
                if(!eligible(level,p)||entry.getValue().material().betaEmissions(now)<=0)continue;
                sources.add(key(p));
            }
        }
        if(sources.isEmpty())return Map.of();
        List<BetaPosition> ordered=new ArrayList<>(sources);
        Map<BetaPosition,BetaCell> cells=new TreeMap<>();
        int take=Math.min(limit,ordered.size());
        for(int i=0;i<take;i++) {
            var src=ordered.get((int)((rotation+i)%ordered.size()));
            cells.put(src,at(level,block(src),now));
        }
        // Include the TRUE target inventory (even outside the 3x3 source scan).
        List<BetaPosition> batch=new ArrayList<>(cells.keySet());
        for(BetaPosition source:batch)for(BetaPosition neighbour:source.neighbours()) {
            if(cells.containsKey(neighbour))continue;
            BlockPos p=block(neighbour);
            if(eligible(level,p))cells.put(neighbour,at(level,p,now));
        }
        return cells;
    }
    public static void commit(ServerLevel level,Map<BetaPosition,BetaCell> before,
                              Map<BetaPosition,BetaCell> after,long now) {
        for(var entry:after.entrySet()) {
            BlockPos pos=block(entry.getKey());
            if(!eligible(level,pos))continue;
            BetaCell old=before.getOrDefault(entry.getKey(),BetaCell.empty());
            BetaCell next=entry.getValue();
            if(old.isotopes().equals(next.isotopes()))continue;
            ChunkRadiationData.Deposit record=AlphaWorld.deposit(level,pos);
            Map<String,Double> units=new TreeMap<>();
            if(record!=null)units.putAll(record.material().at(now).units());
            for(String id:old.isotopes().keySet()) {
                BetaIsotope def=ISOTOPES.find(id);
                if(def!=null&&def.betaRatePerUnit()>0)units.remove(id);
            }
            for(var iso:next.isotopes().entrySet()) {
                BetaIsotope def=ISOTOPES.find(iso.getKey());
                if(def!=null&&def.betaRatePerUnit()>0&&iso.getValue().quantity()>IsotopeInventory.EPS)
                    units.put(iso.getKey(),iso.getValue().quantity());
            }
            IsotopeInventory material=new IsotopeInventory(units,now);
            if(material.isEmpty())AlphaWorld.setDeposit(level,pos,null);
            else AlphaWorld.setDeposit(level,pos,new ChunkRadiationData.Deposit(
                record==null?(level.getBlockState(pos).is(com.nucleocraft.radiation.world.alpha.AlphaDustRegistry.DUST.get())
                    ?ChunkRadiationData.Carrier.LOOSE_DUST:ChunkRadiationData.Carrier.SURFACE_RESIDUE):record.carrier(),
                AlphaWorld.blockId(level,pos),material));
            LevelChunk chunk=level.getChunkAt(pos);
            Map<String,Double> caps=new TreeMap<>();
            next.isotopes().forEach((id,load)->{if(load.capRadPerSecond()>0)caps.put(id,load.capRadPerSecond());});
            chunk.getData(com.nucleocraft.radiation.server.data.RadiationAttachments.CHUNK.get()).setBetaCaps(pos,caps);
            chunk.setUnsaved(true);
            if(AlphaWorld.isDust(level,pos))
                com.nucleocraft.radiation.server.alpha.AlphaDustTransport.syncDust(level,pos,material);
        }
    }
    public record ServerLevelChunk(ServerLevel level,int x,int z){}
    public static List<ServerLevelChunk> playerChunks(ServerLevel level,int distance) {
        Set<Long> coords=new LinkedHashSet<>();
        for(var player:level.players()) {
            int cx=player.blockPosition().getX()>>4, cz=player.blockPosition().getZ()>>4;
            for(int x=cx-distance;x<=cx+distance;x++)for(int z=cz-distance;z<=cz+distance;z++){
                if(level.getChunkSource().hasChunk(x,z))coords.add(net.minecraft.world.level.ChunkPos.asLong(x,z));
            }
        }
        List<ServerLevelChunk> ret=new ArrayList<>();
        for(long c:coords)ret.add(new ServerLevelChunk(level,net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c)));
        return ret;
    }
    public static double betaAt(ServerLevel level,BlockPos pos,long now) {
        return at(level,pos,now).isotopes().entrySet().stream().mapToDouble(e->{
            BetaIsotope iso=ISOTOPES.find(e.getKey());return iso==null?0:iso.betaRatePerUnit()*e.getValue().quantity();
        }).sum()+synthetic(level,pos);
    }
}
