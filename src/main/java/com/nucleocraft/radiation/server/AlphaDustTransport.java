package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.core.AlphaDustDistribution;
import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.world.AlphaDustRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Physical transport, NOT Alpha radiation propagation. No Beta/Gamma dependencies. */
public final class AlphaDustTransport {
    private static final double VISIBLE_MINIMUM=.005;
    private static final double AEROSOL_FRACTION=.20;
    private AlphaDustTransport() {}
    public static boolean absorbs(BlockState state) {
        return state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.COARSE_DIRT)
                ||state.is(Blocks.ROOTED_DIRT)||state.is(Blocks.SAND)||state.is(Blocks.RED_SAND)
                ||state.is(Blocks.FARMLAND)||state.is(Blocks.PODZOL)||state.is(Blocks.MYCELIUM);
    }
    private static boolean canSupport(ServerLevel level,BlockPos support) {
        if(!AlphaWorld.loaded(level,support))return false;
        BlockState state=level.getBlockState(support);
        return !state.isAir() && state.isFaceSturdy(level,support,Direction.UP);
    }
    /** Command path: direct deposit onto existing dust OR above a supporting solid. */
    public static boolean direct(ServerLevel level,BlockPos block,IsotopeInventory material,long now) {
        BlockPos dust=AlphaWorld.isDust(level,block)?block:block.above();
        return addLoose(level,dust,material,now);
    }
    /** True if all the supplied material was stored. Very tiny amounts are invisible residue. */
    public static boolean addLoose(ServerLevel level,BlockPos dust,IsotopeInventory material,long now) {
        if(material==null||material.isEmpty())return true;
        BlockPos support=dust.below();
        if(!AlphaWorld.loaded(level,dust)||!canSupport(level,support))return false;
        BlockState state=level.getBlockState(dust);
        if(!state.isAir()&&!state.is(AlphaDustRegistry.DUST.get()))return false;
        if(material.totalUnits()<VISIBLE_MINIMUM&&!state.is(AlphaDustRegistry.DUST.get())) {
            return addOnBlock(level,support,ChunkAlphaData.Carrier.SURFACE_RESIDUE,material,now);
        }
        ChunkAlphaData.Deposit old=AlphaWorld.deposit(level,dust);
        IsotopeInventory total=old==null?material.at(now):old.material().plus(material,now);
        int layers=Math.max(1,Math.min(8,(int)Math.ceil(Math.sqrt(total.totalUnits())*1.9)));
        BlockState target=AlphaDustRegistry.DUST.get().defaultBlockState().setValue(SnowLayerBlock.LAYERS,layers);
        if(!level.setBlock(dust,target,3))return false;
        AlphaWorld.setDeposit(level,dust,new ChunkAlphaData.Deposit(
                ChunkAlphaData.Carrier.LOOSE_DUST,AlphaWorld.blockId(level,dust),total));
        return true;
    }
    public static boolean addOnBlock(ServerLevel level,BlockPos pos,ChunkAlphaData.Carrier carrier,
                                     IsotopeInventory material,long now) {
        if(material==null||material.isEmpty())return true;
        if(!AlphaWorld.loaded(level,pos)||level.getBlockState(pos).isAir())return false;
        ChunkAlphaData.Deposit old=AlphaWorld.deposit(level,pos);
        if(old!=null&&old.carrier()!=carrier)carrier=old.carrier();
        IsotopeInventory total=old==null?material.at(now):old.material().plus(material,now);
        AlphaWorld.setDeposit(level,pos,new ChunkAlphaData.Deposit(carrier,AlphaWorld.blockId(level,pos),total));
        return true;
    }
    /** Deterministic 3D 2-block radius, with half-distance weighting normalised
     * over ALL eligible destinations. Never invents isotope units.
     */
    public static void release(ServerLevel level,BlockPos origin,IsotopeInventory material,long now,
                               AlphaSimulation sim) {
        IsotopeInventory decayed=material.at(now);
        puff(level,origin,24);
        if(decayed.isEmpty())return;
        IsotopeInventory.Split separation=decayed.split(AEROSOL_FRACTION);
        IsotopeInventory airborne=separation.moved(),surface=separation.remaining();
        IsotopeInventory remaining=distributeSurface(level,origin,surface,now);
        airborne=airborne.plus(remaining,now);
        AlphaWorld.addCloud(level,origin,airborne,now);
        sim.scheduleCloud(level,origin);
    }
    /** Settling cloud material does not generate more airborne material. Returns unrecoverable
     * surface fraction to caller so it remains inside the existing cloud. */
    public static IsotopeInventory settle(ServerLevel level,BlockPos origin,IsotopeInventory proposed,long now) {
        return distributeSurface(level,origin,proposed,now);
    }
    private static IsotopeInventory distributeSurface(ServerLevel level,BlockPos origin,
                                                      IsotopeInventory surface,long now) {
        List<AlphaDustDistribution.Target<BlockPos>> options=new ArrayList<>();
        for(int dx=-2;dx<=2;dx++)for(int dy=-2;dy<=2;dy++)for(int dz=-2;dz<=2;dz++) {
            int distance=AlphaDustDistribution.manhattan(dx,dy,dz);
            if(distance>2)continue;
            BlockPos top=origin.offset(dx,dy,dz),support=top.below();
            if(!AlphaWorld.loaded(level,top)||!canSupport(level,support))continue;
            BlockState above=level.getBlockState(top);
            if(!above.isAir()&&!above.is(AlphaDustRegistry.DUST.get()))continue;
            options.add(new AlphaDustDistribution.Target<>(top,distance));
        }
        List<AlphaDustDistribution.Portion<BlockPos>> portions=AlphaDustDistribution.normalized(options);
        // Assign all but the last share, then hand the exact remainder to the last.
        // This avoids manufacture from repeated floating-point normalization.
        IsotopeInventory remaining=surface;
        double sent=0;
        for(int i=0;i<portions.size();i++) {
            var target=portions.get(i);
            double part=i==portions.size()-1?Math.max(0,1-sent):target.fraction();
            part=Math.min(1,Math.max(0,part));
            if(part==0)continue;
            IsotopeInventory share=surface.scaled(part);
            boolean succeeded=absorbs(level.getBlockState(target.location().below()))
                    ?addOnBlock(level,target.location().below(),ChunkAlphaData.Carrier.ABSORBED,share,now)
                    :addLoose(level,target.location(),share,now);
            if(succeeded)remaining=subtract(remaining,share);
            sent+=part;
        }
        return remaining;
    }
    /** Subtraction by isotope identity; no source multiplication from many neighbours. */
    private static IsotopeInventory subtract(IsotopeInventory left,IsotopeInventory right) {
        var m=new java.util.LinkedHashMap<String,Double>(left.units());
        right.units().forEach((key,val)->m.computeIfPresent(key,(k,old)->Math.max(0,old-val)));
        return new IsotopeInventory(m,left.epochMillis());
    }
    public static void puff(ServerLevel level,BlockPos pos,int count) {
        level.sendParticles(ParticleTypes.ASH,pos.getX()+.5,pos.getY()+.48,pos.getZ()+.5,
                count,.40,.34,.40,.011);
        level.sendParticles(ParticleTypes.CLOUD,pos.getX()+.5,pos.getY()+.40,pos.getZ()+.5,
                Math.max(2,count/5),.22,.18,.22,.006);
    }
    /** Dust can be visually removed after all of its isotope material has lifted. */
    public static void syncDust(ServerLevel level,BlockPos pos,IsotopeInventory material) {
        if(!AlphaWorld.isDust(level,pos))return;
        if(material==null||material.isEmpty()) {
            AlphaWorld.setDeposit(level,pos,null);
            level.removeBlock(pos,false);
            return;
        }
        int count=Math.max(1,Math.min(8,(int)Math.ceil(Math.sqrt(material.totalUnits())*1.9)));
        level.setBlock(pos,level.getBlockState(pos).setValue(SnowLayerBlock.LAYERS,count),3);
    }
}
