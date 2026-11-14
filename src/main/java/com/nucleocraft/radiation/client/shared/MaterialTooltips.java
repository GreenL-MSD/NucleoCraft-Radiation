package com.nucleocraft.radiation.client.shared;

import com.nucleocraft.radiation.core.IsotopeDefinitions;
import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.server.alpha.AlphaItemStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shared physical isotope identity, independently of the radiation profile. */
public final class MaterialTooltips {
    @SubscribeEvent public void onTooltip(ItemTooltipEvent event){
        IsotopeInventory material=AlphaItemStorage.material(event.getItemStack());
        if(material==null||material.isEmpty())return;
        event.getToolTip().add(Component.literal("[Radioactive] - Contaminated")
            .withStyle(s->s.withColor(TextColor.fromRgb(0x6EDC9B))));
        for(String id:material.units().keySet())
            event.getToolTip().add(Component.literal("[Isotope] - ["+IsotopeDefinitions.require(id).displayName()+"]")
                .withStyle(s->s.withColor(TextColor.fromRgb(0x6EDC9B))));
    }
}
