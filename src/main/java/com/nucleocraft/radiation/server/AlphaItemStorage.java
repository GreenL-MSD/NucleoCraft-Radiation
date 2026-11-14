package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.core.IsotopeInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Synthetic source is not an isotope and cannot create physical fallout. */
public final class AlphaItemStorage {
    private static final String MAT="nucleocraft_alpha_material_v1";
    private static final String SOURCE="nucleocraft_synthetic_alpha_v1";
    private AlphaItemStorage() {}
    private static CompoundTag root(ItemStack stack) {
        CustomData data=stack.get(DataComponents.CUSTOM_DATA);
        return data==null?new CompoundTag():data.copyTag();
    }
    private static void save(ItemStack stack,CompoundTag root) {
        if(root.isEmpty())stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
    }
    public static IsotopeInventory material(ItemStack stack) {
        if(stack.isEmpty())return null;
        CompoundTag tag=root(stack);
        return tag.contains(MAT,Tag.TAG_COMPOUND)?AlphaNBT.read(tag.getCompound(MAT)):null;
    }
    /** Stored as physical quantity PER ITEM so stack splitting does not multiply matter. */
    public static void material(ItemStack stack,IsotopeInventory value) {
        if(stack.isEmpty())return;
        CompoundTag tag=root(stack);
        if(value==null||value.isEmpty())tag.remove(MAT);
        else tag.put(MAT,AlphaNBT.write(value));
        save(stack,tag);
    }
    public static double syntheticAlpha(ItemStack stack) {
        if(stack.isEmpty())return 0;
        double n=root(stack).getDouble(SOURCE);
        return Double.isFinite(n)&&n>0?n:0;
    }
    public static void syntheticAlpha(ItemStack stack,double ratePerItem) {
        if(stack.isEmpty())return;
        CompoundTag tag=root(stack);
        if(ratePerItem<=0)tag.remove(SOURCE);
        else tag.putDouble(SOURCE,ratePerItem);
        save(stack,tag);
    }
}
