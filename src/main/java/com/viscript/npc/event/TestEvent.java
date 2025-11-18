package com.viscript.npc.event;

import com.viscript.npc.ViScriptNpc;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public class TestEvent {

    @SubscribeEvent
    public static void testEvent(LivingIncomingDamageEvent event) {
    }
}
