package com.nucleocraft.radiation.server.alpha;

import com.nucleocraft.radiation.server.data.IsotopeNBT;
import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.player.alpha.PlayerAlphaState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Dedicated persistent player data; never cached in a global singleton. */
public final class PlayerAlphaAttachment implements INBTSerializable<CompoundTag> {
    private PlayerAlphaState state=new PlayerAlphaState(System.currentTimeMillis());
    public PlayerAlphaState state(){return state;}
    public void reset(long now){state.reset(now);}
    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag=new CompoundTag();tag.putInt("version",1);
        tag.put("inhaled",IsotopeNBT.write(state.inhaled()));
        tag.put("ingested",IsotopeNBT.write(state.ingested()));
        tag.putDouble("dose",state.accumulatedRad());return tag;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider,CompoundTag tag) {
        if(tag.getInt("version")!=1)return;
        IsotopeInventory inhaled=IsotopeNBT.read(tag.getCompound("inhaled"));
        IsotopeInventory ingested=IsotopeNBT.read(tag.getCompound("ingested"));
        double dose=tag.getDouble("dose");
        if(inhaled!=null&&ingested!=null&&Double.isFinite(dose))
            state=new PlayerAlphaState(inhaled,ingested,Math.min(1e9,Math.max(0,dose)));
    }
}
