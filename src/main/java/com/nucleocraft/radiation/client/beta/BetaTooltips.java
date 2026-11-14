package com.nucleocraft.radiation.client.beta;

import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.server.alpha.AlphaItemStorage;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Beta-specific emissions only. Isotope names are rendered by shared MaterialTooltips. */
public final class BetaTooltips {
    @SubscribeEvent public void onTooltip(ItemTooltipEvent event) {
        IsotopeInventory material=AlphaItemStorage.material(event.getItemStack());
        if(material==null||material.isEmpty())return;
        double beta=material.betaEmissions(System.currentTimeMillis())*event.getItemStack().getCount();
        if(beta<=0)return;
        event.getToolTip().add(Component.literal(String.format(Locale.ROOT,"Beta Emission - %.3f RAD/s",beta))
            .withStyle(s->s.withColor(TextColor.fromRgb(0x79DAFF))));
    }
}
