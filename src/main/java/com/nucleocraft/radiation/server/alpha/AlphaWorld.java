package com.nucleocraft.radiation.server.alpha;

import com.nucleocraft.radiation.server.data.ChunkRadiationData;
import com.nucleocraft.radiation.server.data.RadiationAttachments;
import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.world.alpha.AlphaDustRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** Server-only, chunk-local storage. All reads are loaded-only; never force chunk generation. */
public final class AlphaWorld {
    private AlphaWorld() {}
    public static boolean loaded(ServerLevel level,BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);
    }
    public static ChunkRadiationData readChunk(ServerLevel level,BlockPos pos) {
        if(!loaded(level,pos))return null;
        LevelChunk chunk=level.getChunkAt(pos);
        return chunk.hasData(RadiationAttachments.CHUNK.get())?chunk.getData(RadiationAttachments.CHUNK.get()):null;
    }
    private static ChunkRadiationData writeChunk(ServerLevel level,BlockPos pos) {
        return level.getChunkAt(pos).getData(RadiationAttachments.CHUNK.get());
    }
    private static void mark(ServerLevel level,BlockPos pos) {level.getChunkAt(pos).setUnsaved(true);}
    public static String blockId(ServerLevel level,BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
    }
    public static ChunkRadiationData.Deposit deposit(ServerLevel level,BlockPos pos) {
        ChunkRadiationData chunk=readChunk(level,pos);
        if(chunk==null)return null;
        ChunkRadiationData.Deposit record=chunk.deposit(pos);
        if(record!=null&&!record.blockId().equals(blockId(level,pos))) {
            // A world edit invalidated the block; stale material never irradiates a new block.
            // Future world-edit adapters will explicitly move this material before replacement.
            chunk.removeDeposit(pos);mark(level,pos);return null;
        }
        return record;
    }
    public static void setDeposit(ServerLevel level,BlockPos pos,ChunkRadiationData.Deposit record) {
        if(!loaded(level,pos))throw new IllegalArgumentException("Chunk not loaded");
        ChunkRadiationData data=writeChunk(level,pos);
        if(record==null||record.material().isEmpty())data.removeDeposit(pos);
        else data.putDeposit(pos,record);
        mark(level,pos);
    }
    public static ChunkRadiationData.Deposit takeDeposit(ServerLevel level,BlockPos pos) {
        ChunkRadiationData.Deposit old=deposit(level,pos);
        if(old!=null)setDeposit(level,pos,null);
        return old;
    }
    public static IsotopeInventory cloud(ServerLevel level,BlockPos pos) {
        ChunkRadiationData data=readChunk(level,pos);
        return data==null?null:data.cloud(pos);
    }
    public static void cloud(ServerLevel level,BlockPos pos,IsotopeInventory material,long now) {
        if(!loaded(level,pos))return;
        ChunkRadiationData data=writeChunk(level,pos);
        data.setCloud(pos,material==null?null:material.at(now));mark(level,pos);
    }
    public static void addCloud(ServerLevel level,BlockPos pos,IsotopeInventory material,long now) {
        if(material==null||material.isEmpty()||!loaded(level,pos))return;
        IsotopeInventory existing=cloud(level,pos);
        cloud(level,pos,existing==null?material:existing.plus(material,now),now);
    }
    public static double synthetic(ServerLevel level,BlockPos pos) {
        ChunkRadiationData data=readChunk(level,pos);
        return data==null?0:data.synthetic(pos);
    }
    public static void synthetic(ServerLevel level,BlockPos pos,double value) {
        if(!loaded(level,pos))throw new IllegalArgumentException("Chunk not loaded");
        writeChunk(level,pos).setSynthetic(pos,value);mark(level,pos);
    }
    public static int cloudCount(ServerLevel level,BlockPos pos) {
        ChunkRadiationData data=readChunk(level,pos);return data==null?0:data.cloudCount();
    }
    public static boolean isDust(ServerLevel level,BlockPos pos) {
        return level.getBlockState(pos).is(AlphaDustRegistry.DUST.get());
    }
}
