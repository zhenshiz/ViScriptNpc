package com.viscript.npc.npc.render;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.NpcRegister;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID, value = Dist.CLIENT)
public class RenderRegister {

    @SubscribeEvent
    public static void registerEntityRender(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcRegister.CUSTOM_NPC.get(), (context) -> new CustomNpcRender<>(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), true)));
    }
}
