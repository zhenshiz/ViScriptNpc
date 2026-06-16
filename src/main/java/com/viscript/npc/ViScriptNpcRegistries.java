package com.viscript.npc;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.npc.data.INpcData;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorNodeHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class ViScriptNpcRegistries {
    public static AutoRegistry.LDLibRegister<INpcData, Supplier<INpcData>> NPC_DATA;
    public static AutoRegistry.LDLibRegister<NpcBehaviorNodeHandler, Supplier<NpcBehaviorNodeHandler>> NPC_BEHAVIOR_NODE_HANDLERS;
    public static AutoRegistry.LDLibRegisterClient<INpcEditorPage, Supplier<INpcEditorPage>> NPC_EDITOR_PAGE;

    static {
        NPC_DATA = AutoRegistry.LDLibRegister
                .create(ResourceLocation.parse(INpcData.ID), INpcData.class, AutoRegistry::noArgsCreator);
        NPC_BEHAVIOR_NODE_HANDLERS = AutoRegistry.LDLibRegister
                .create(ResourceLocation.parse(NpcBehaviorNodeHandler.ID), NpcBehaviorNodeHandler.class, AutoRegistry::noArgsCreator);
        if (Platform.isClient()) {
            NPC_EDITOR_PAGE = AutoRegistry.LDLibRegisterClient.create(ResourceLocation.parse(INpcEditorPage.ID), INpcEditorPage.class, AutoRegistry::noArgsCreator);
        }

    }
}
