package com.nucleocraft.radiation.server.data;

import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.core.IsotopeDefinitions;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict, versioned NBT for physical isotope quantities. */
public final class IsotopeNBT {
    private IsotopeNBT() {}
    public static CompoundTag write(IsotopeInventory material) {
        CompoundTag out=new CompoundTag();
        out.putLong("epochMillis", material.epochMillis());
        ListTag entries=new ListTag();
        material.units().forEach((id,units)->{
            CompoundTag e=new CompoundTag();e.putString("id",id);e.putDouble("units",units);entries.add(e);
        });
        out.put("isotopes",entries);
        return out;
    }
    public static IsotopeInventory read(CompoundTag tag) {
        try {
            ListTag list=tag.getList("isotopes",Tag.TAG_COMPOUND);
            if(list.size()>32)return null;
            Map<String,Double> units=new LinkedHashMap<>();
            for(int i=0;i<list.size();i++) {
                CompoundTag e=list.getCompound(i);
                String id=e.getString("id");
                IsotopeDefinitions.require(id);
                if(units.putIfAbsent(id,e.getDouble("units"))!=null)return null;
            }
            return new IsotopeInventory(units,Math.max(0,tag.getLong("epochMillis")));
        } catch(RuntimeException bad) {return null;}
    }
}
