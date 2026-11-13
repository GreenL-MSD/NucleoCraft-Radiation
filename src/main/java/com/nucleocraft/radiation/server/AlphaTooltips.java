package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.core.IsotopeDefinitions;
import com.nucleocraft.radiation.core.IsotopeInventory;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Readable isotope names, separate physical isotope vs synthetic Alpha origin. */
public final class AlphaTooltips {
    @SubscribeEvent public void onTooltip(ItemTooltipEvent event) {
        IsotopeInventory physical=AlphaItemStorage.material(event.getItemStack());
        double synthetic=AlphaItemStorage.syntheticAlpha(event.getItemStack());
        if(physical==null&&synthetic<=0)return;
        long now=System.currentTimeMillis();
        if(physical!=null&&!physical.isEmpty()) {
            event.getToolTip().add(colour("[Radioactive] - Contaminated",0x6EDC9B));
            for(String isotope:physical.units().keySet())
                event.getToolTip().add(colour("[Isotope] - ["+IsotopeDefinitions.require(isotope).displayName()+"]",0x6EDC9B));
            event.getToolTip().add(colour(String.format(Locale.ROOT,"Alpha Emission - %.3f RAD/s",
                    physical.alphaEmissions(now)*event.getItemStack().getCount()),0xE0B75A));
        }
        if(synthetic>0) {
            event.getToolTip().add(colour("[Synthetic Source] - Alpha",0xE0B75A));
            event.getToolTip().add(colour(String.format(Locale.ROOT,"Emitted - %.3f RAD/s",
                    synthetic*event.getItemStack().getCount()),0xE0B75A));
        }
        event.getToolTip().add(colour("External absorbed Alpha - 0 RAD/s",0xAAB4BD));
    }
    private static Component colour(String s,int rgb) {
        return Component.literal(s).withStyle(style->style.withColor(TextColor.fromRgb(rgb)));
    }
}
