package com.viscript.npc.network;

import com.viscript.npc.ViScriptNpc;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public class ViScriptNpcNetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(ViScriptNpc.MOD_ID);
        //s2c

        //c2s

    }
}
