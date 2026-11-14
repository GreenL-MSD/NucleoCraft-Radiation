package com.nucleocraft.radiation.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Ephemeral 3D text. No entities, persistent debug data, or server-side rendering. */
@EventBusSubscriber(modid="nucleocraft_radiation",value=Dist.CLIENT)
public final class RadiationLabelRenderer {
    @SubscribeEvent public static void draw(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||mc.options.hideGui)return;
        var labels=RadiationLabelClientState.labels(mc.level.dimension().location().toString());
        if(labels.isEmpty())return;
        PoseStack pose=e.getPoseStack();if(pose==null)return;
        Vec3 cam=e.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers=mc.renderBuffers().bufferSource();
        Font font=mc.font;
        for(var label:labels){
            if(!mc.level.hasChunkAt(label.pos()))continue;
            double x=label.pos().getX()+.5-cam.x;
            double y=label.pos().getY()+1.36-cam.y;
            double z=label.pos().getZ()+.5-cam.z;
            if(x*x+y*y+z*z>13*13)continue;
            pose.pushPose();pose.translate(x,y,z);
            pose.mulPose(e.getCamera().rotation());pose.scale(-.024f,-.024f,.024f);
            String[] lines={label.header(),label.material(),String.format("x%d y%d z%d",label.pos().getX(),label.pos().getY(),label.pos().getZ())};
            for(int i=0;i<lines.length;i++){
                int rgb=i==0?(label.type().equals("alpha")?0xF5C86A:0x7AD6FF):i==1?0xFFFFFF:0xAAAAAA;
                font.drawInBatch(lines[i],-font.width(lines[i])/2f,i*10f,rgb,false,
                    pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0x44000000,0x00F000F0);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){RadiationLabelClientState.clear();}
}
