package com.viscript.npc;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.viscript.npc.command.ICommand;
import com.viscript.npc.gui.edit.npc.INPCObject;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Supplier;

public class ViScriptNpcRegistries {
    public static AutoRegistry.LDLibRegisterClient<ICommand, Supplier<ICommand>> COMMANDS;

    @OnlyIn(Dist.CLIENT)
    public static AutoRegistry.LDLibRegisterClient<INPCObject, Supplier<INPCObject>> NPC_OBJECTS;

    static {
        COMMANDS = AutoRegistry.LDLibRegisterClient
                .create(ViScriptNpc.id("command"), ICommand.class, AutoRegistry::noArgsCreator);
        if (LDLib2.isClient()) {
            Client.load();
        }
    }

    public static void init() {
    }

    @OnlyIn(Dist.CLIENT)
    private static class Client {
        public static void load() {
            NPC_OBJECTS = AutoRegistry.LDLibRegisterClient
                    .create(ViScriptNpc.id("npc_object"), INPCObject.class, AutoRegistry::noArgsCreator);
        }
    }
}
