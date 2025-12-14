package com.viscript.npc;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.viscript.npc.command.ICommand;

import java.util.function.Supplier;

public class ViScriptNpcRegistries {
    public static AutoRegistry.LDLibRegister<ICommand, Supplier<ICommand>> COMMANDS;

    static {
        COMMANDS = AutoRegistry.LDLibRegister
                .create(ViScriptNpc.id("command"), ICommand.class, AutoRegistry::noArgsCreator);
    }
}
