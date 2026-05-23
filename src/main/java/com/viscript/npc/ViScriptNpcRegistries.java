package com.viscript.npc;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.viscript.npc.command.ICommand;
import com.viscript.npc.npc.data.INpcData;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class ViScriptNpcRegistries {
    public static AutoRegistry.LDLibRegister<ICommand, Supplier<ICommand>> COMMANDS;
    public static AutoRegistry.LDLibRegister<INpcData, Supplier<INpcData>> NPC_DATA;

    static {
        COMMANDS = AutoRegistry.LDLibRegister
                .create(ResourceLocation.parse(ICommand.ID), ICommand.class, AutoRegistry::noArgsCreator);
        NPC_DATA = AutoRegistry.LDLibRegister
                .create(ResourceLocation.parse(INpcData.ID), INpcData.class, AutoRegistry::noArgsCreator);
    }
}
