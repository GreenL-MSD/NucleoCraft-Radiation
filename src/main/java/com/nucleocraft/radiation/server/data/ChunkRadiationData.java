package com.nucleocraft.radiation.server.data;

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
public final class ChunkRadiationData implements INBTSerializable<CompoundTag> {
    public enum Carrier {LOOSE_DUST, ABSORBED, SURFACE_RESIDUE}
    public record Deposit(Carrier carrier,String blockId,IsotopeInventory material) {
        public Deposit {if(carrier==null||blockId==null||blockId.isBlank()||material==null)
            throw new IllegalArgumentException("Invalid deposit");}
    }
    private final Map<Integer,Deposit> deposits=new HashMap<>();
    private final Map<Integer,IsotopeInventory> airborne=new HashMap<>();
    private final Map<Integer,Double> syntheticAlpha=new HashMap<>();
    /** Only Beta cap metadata is stored here. All isotope quantities remain in deposits. */
    private final Map<Integer,Map<String,Double>> betaCaps=new HashMap<>();
    private final Map<Integer,Double> syntheticBeta=new HashMap<>();
    public static int pack(BlockPos pos) {
        return (pos.getY()<<8)|((pos.getZ()&15)<<4)|(pos.getX()&15);
    }
    public static BlockPos unpack(int cx,int cz,int p) {
        return new BlockPos((cx<<4)|(p&15),p>>8,(cz<<4)|((p>>>4)&15));
    }
    public Deposit deposit(BlockPos pos){return deposits.get(pack(pos));}
    public Deposit removeDeposit(BlockPos pos){betaCaps.remove(pack(pos));return deposits.remove(pack(pos));}
    public void putDeposit(BlockPos pos,Deposit record) {
        if(record.material().isEmpty()){deposits.remove(pack(pos));betaCaps.remove(pack(pos));}
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
    public double betaCap(BlockPos pos,String isotope,double derivedFallback) {
        return betaCaps.getOrDefault(pack(pos),Map.of()).getOrDefault(isotope,derivedFallback);
    }
    public void setBetaCaps(BlockPos pos,Map<String,Double> caps) {
        if(caps==null||caps.isEmpty())betaCaps.remove(pack(pos));
        else betaCaps.put(pack(pos),Map.copyOf(caps));
    }
    public void clearBetaCaps(BlockPos pos){betaCaps.remove(pack(pos));}
    public double syntheticBeta(BlockPos pos){return syntheticBeta.getOrDefault(pack(pos),0.0);}
    public void setSyntheticBeta(BlockPos pos,double rate){
        if(rate<=0)syntheticBeta.remove(pack(pos));else syntheticBeta.put(pack(pos),rate);
    }
    public Map<Integer,Double> syntheticAlphaSnapshot(){return Map.copyOf(syntheticAlpha);}
    public Map<Integer,Double> syntheticBetaSnapshot(){return Map.copyOf(syntheticBeta);}
    public int depositCount(){return deposits.size();}
    public int cloudCount(){return airborne.size();}
    public int syntheticCount(){return syntheticAlpha.size();}
    public Map<Integer,IsotopeInventory> cloudsSnapshot(){return Map.copyOf(airborne);}
    public Map<Integer,Deposit> depositsSnapshot(){return Map.copyOf(deposits);}
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag root=new CompoundTag();root.putInt("version",2);
        // Disk palette deduplicates equal deposit records within a chunk.
        Map<Deposit,Integer> ids=new HashMap<>();
        ListTag palette=new ListTag(),positions=new ListTag();
        for(var e:deposits.entrySet()) {
            Deposit d=e.getValue();
            Integer id=ids.get(d);
            if(id==null) {
                id=palette.size(); ids.put(d,id);
                CompoundTag p=new CompoundTag();p.putString("carrier",d.carrier().name());
                p.putString("block",d.blockId());p.put("material",IsotopeNBT.write(d.material()));
                palette.add(p);
            }
            CompoundTag entry=new CompoundTag();entry.putInt("position",e.getKey());entry.putInt("palette",id);
            positions.add(entry);
        }
        root.put("palette",palette);root.put("positions",positions);
        ListTag clouds=new ListTag();
        for(var e:airborne.entrySet()) {
            CompoundTag entry=new CompoundTag();entry.putInt("position",e.getKey());
            entry.put("material",IsotopeNBT.write(e.getValue()));clouds.add(entry);
        }
        root.put("clouds",clouds);
        ListTag sources=new ListTag();
        for(var e:syntheticAlpha.entrySet()) {
            CompoundTag entry=new CompoundTag();entry.putInt("position",e.getKey());
            entry.putDouble("rate",e.getValue());sources.add(entry);
        }
        root.put("synthetic",sources);
        ListTag caps=new ListTag();
        for(var e:betaCaps.entrySet())for(var cap:e.getValue().entrySet()) {
            CompoundTag a=new CompoundTag();a.putInt("position",e.getKey());
            a.putString("id",cap.getKey());a.putDouble("cap",cap.getValue());caps.add(a);
        }
        root.put("betaCaps",caps);
        ListTag emitters=new ListTag();
        for(var e:syntheticBeta.entrySet()) {
            CompoundTag a=new CompoundTag();a.putInt("position",e.getKey());a.putDouble("rate",e.getValue());emitters.add(a);
        }
        root.put("syntheticBeta",emitters);
        return root;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag root) {
        deposits.clear();airborne.clear();syntheticAlpha.clear();betaCaps.clear();syntheticBeta.clear();
        if(root.getInt("version")!=1&&root.getInt("version")!=2)return; // never silently reinterpret v0.7 saves
        List<Deposit> palette=new ArrayList<>();
        ListTag p=root.getList("palette",Tag.TAG_COMPOUND);
        for(int i=0;i<p.size()&&i<4096;i++) {
            CompoundTag e=p.getCompound(i);Deposit record=null;
            try {
                IsotopeInventory m=IsotopeNBT.read(e.getCompound("material"));
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
            CompoundTag e=clouds.getCompound(i);IsotopeInventory m=IsotopeNBT.read(e.getCompound("material"));
            if(m!=null&&!m.isEmpty())airborne.put(e.getInt("position"),m);
        }
        ListTag sources=root.getList("synthetic",Tag.TAG_COMPOUND);
        for(int i=0;i<sources.size()&&i<4096;i++) {
            CompoundTag e=sources.getCompound(i);double v=e.getDouble("rate");
            if(Double.isFinite(v)&&v>0)syntheticAlpha.put(e.getInt("position"),v);
        }
        if(root.getInt("version")>=2) {
            ListTag caps=root.getList("betaCaps",Tag.TAG_COMPOUND);
            for(int i=0;i<caps.size()&&i<65536;i++) {
                CompoundTag e=caps.getCompound(i);
                try {
                    String id=e.getString("id");
                    com.nucleocraft.radiation.core.IsotopeDefinitions.require(id);
                    double value=e.getDouble("cap");
                    if(!Double.isFinite(value)||value<0)continue;
                    betaCaps.computeIfAbsent(e.getInt("position"),k->new HashMap<>()).put(id,value);
                }catch(RuntimeException ignored){}
            }
            ListTag beta=root.getList("syntheticBeta",Tag.TAG_COMPOUND);
            for(int i=0;i<beta.size()&&i<4096;i++) {
                CompoundTag e=beta.getCompound(i);double rate=e.getDouble("rate");
                if(Double.isFinite(rate)&&rate>0)syntheticBeta.put(e.getInt("position"),rate);
            }
        }
    }
}
