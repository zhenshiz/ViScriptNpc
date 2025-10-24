package com.viscript.npc.command.argument;

import com.viscript.npc.ViScriptNpc;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ArgumentRegister {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPE = DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, ViScriptNpc.MOD_ID);

    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<NpcLocationArgument>> NPC_LOCATION_ARGUMENT
            = ARGUMENT_TYPE.register("npc_location", () -> ArgumentTypeInfos.registerByClass(NpcLocationArgument.class,
            SingletonArgumentInfo.contextFree(NpcLocationArgument::new)));
}
