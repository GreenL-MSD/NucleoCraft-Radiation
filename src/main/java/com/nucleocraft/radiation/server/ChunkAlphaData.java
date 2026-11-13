package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.core.IsotopeInventory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Sparse per-chunk material and cloud attachment. A clean chunk saves NO positions.
 * Position packing supports negative build heights; repeated records are deduplicated
 * in a disk palette. Each mutable update must mark its owning chunk unsaved.
 */
public final class ChunkAlphaData implements INBTSerializable<CompoundTag> {
    public enum Carrier {LOOSE_DUST, ABSORBED, SURFACE_RESIDUE}
    public record Deposit(Carrier carrier,String blockId,IsotopeInventory material) {
        public Deposit {if(carrier==null||blockId==null||blockId.isBlank()||material==null)
            throw new IllegalArgumentException("Invalid deposit");}
    }
    private final Map<Integer,Deposit> deposits=new HashMap<>();
    private final Map<Integer,IsotopeInventory> airborne=new HashMap<>();
    private final Map<Integer,Double> syntheticAlpha=new HashMap<>();
    public static int pack(BlockPos pos) {
        return (pos.getY()<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15);
    }
    public static BlockPos unpack(int cx,int cz,int p) {
        return new BlockPos((cx<<4)|(p&15),p>>8,(cz<<4)|((p>>>4)&15));
    }
    public Deposit deposit(BlockPos pos){return deposits.get(pack(pos));}
    public Deposit removeDeposit(BlockPos pos){return deposits.remove(pack(pos));}
    public void putDeposit(BlockPos pos,Deposit record) {
        if(record.material().isEmpty())deposits.remove(pack(pos));
        else deposits.put(pack(pos),record);
    }
    public IsotopeInventory cloud(BlockPos pos){return airborne.get(pack(pos));}
    public void setCloud(BlockPos pos,IsotopeInventory value) {
        if(value==null||value.isEmpty())airborne.remove(pack(pos));
        else airborne.put(pack(pos),value);
    }
    public double synthetic(BlockPos pos){return syntheticAlpha.getOrDefault(pack(pos),0.0);}
    public void setSynthetic(BlockPos pos,double value) {
        if(value<=0)syntheticAlpha.remove(pack(pos));
        else syntheticAlpha.put(pack(pos),value);
    }
    public int depositCount(){return deposits.size();}
    public int cloudCount(){return airborne.size();}
    public int syntheticCount(){return syntheticAlpha.size();}
    public Map<Integer,IsotopeInventory> cloudsSnapshot(){return Map.copyOf(airborne);}
    public Map<Integer,Deposit> depositsSnapshot(){return Map.copyOf(deposits);}
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root=new CompoundTag();root.putInt("version",1);
        // Disk palette deduplicates equal deposit records within a chunk.
        Map<Deposit,Integer> ids=new HashMap<>();
        ListTag palette=new ListTag(),positions=new ListTag();
        for(var e:deposits.entrySet()) {
            Deposit d=e.getValue();
            Integer id=ids.get(d);
            if(id==null) {
                id=palette.size(); ids.put(d,id);
                CompoundTag p=new CompoundTag();p.putString("carrier",d.carrier().name());
                p.putString("block",d.blockId());p.put("material",AlphaNBT.write(d.material()));
                palette.add(p);
            }
            CompoundTag entry=new CompoundTag();entry.putInt("position",e.getKey());entry.putInt("palette",id);
            positions.add(entry);
        }
        root.put("palette",palette);root.put("positions",positions);
        ListTag clouds=new ListTag();
        for(var e:airborne.entrySet()) {
            CompoundTag entry=new CompoundTag();entry.putInt("position",e.getKey());
            entry.put("material",AlphaNBT.write(e.getValue()));clouds.add(entry);
        }
        root.put("clouds",clouds);
        ListTag sources=new ListTag();
        for(var e:syntheticAlpha.entrySet()) {
            CompoundTag entry=new CompoundTag();entry.putInt("position",e.getKey());
            entry.putDouble("rate",e.getValue());sources.add(entry);
        }
        root.put("synthetic",sources);
        return root;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag root) {
        deposits.clear();airborne.clear();syntheticAlpha.clear();
        if(root.getInt("version")!=1)return; // never silently reinterpret v0.7 saves
        List<Deposit> palette=new ArrayList<>();
        ListTag p=root.getList("palette",Tag.TAG_COMPOUND);
        for(int i=0;i<p.size()&&i<4096;i++) {
            CompoundTag e=p.getCompound(i);Deposit record=null;
            try {
                IsotopeInventory m=AlphaNBT.read(e.getCompound("material"));
                if(m!=null&&!m.isEmpty())record=new Deposit(Carrier.valueOf(e.getString("carrier")),
                        e.getString("block"),m);
            }catch(RuntimeException ignored) {}
            palette.add(record);
        }
        ListTag positions=root.getList("positions",Tag.TAG_COMPOUND);
        for(int i=0;i<positions.size()&&i<65536;i++) {
            CompoundTag e=positions.getCompound(i);int n=e.getInt("palette");
            if(n>=0&&n<palette.size()&&palette.get(n)!=null)deposits.put(e.getInt("position"),palette.get(n));
        }
        ListTag clouds=root.getList("clouds",Tag.TAG_COMPOUND);
        for(int i=0;i<clouds.size()&&i<256;i++) {
            CompoundTag e=clouds.getCompound(i);IsotopeInventory m=AlphaNBT.read(e.getCompound("material"));
            if(m!=null&&!m.isEmpty())airborne.put(e.getInt("position"),m);
        }
        ListTag sources=root.getList("synthetic",Tag.TAG_COMPOUND);
        for(int i=0;i<sources.size()&&i<4096;i++) {
            CompoundTag e=sources.getCompound(i);double v=e.getDouble("rate");
            if(Double.isFinite(v)&&v>0)syntheticAlpha.put(e.getInt("position"),v);
        }
    }
}
