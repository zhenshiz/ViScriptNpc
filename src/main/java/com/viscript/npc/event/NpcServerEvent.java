package com.viscript.npc.event;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.command.NpcCommand;
import com.viscript.npc.util.npc.NpcHelper;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public class NpcServerEvent {

    @SubscribeEvent
    public static void reloadCommandSuggestions(ServerStartedEvent event) {
        NpcCommand.npcFilesPath.clear();
        for (String path : NpcHelper.scanNpcFiles()) {
            NpcCommand.npcFilesPath.add(ViScriptNpc.id(path));
        }
    }
}
