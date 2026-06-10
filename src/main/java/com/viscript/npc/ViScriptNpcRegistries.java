package com.viscript.npc;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.viscript.npc.npc.data.INpcData;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Supplier;

public class ViScriptNpcRegistries {
    public static AutoRegistry.LDLibRegister<INpcData, Supplier<INpcData>> NPC_DATA;

    static {
        NPC_DATA = AutoRegistry.LDLibRegister
                .create(ResourceLocation.parse(INpcData.ID), INpcData.class, AutoRegistry::noArgsCreator);
    }
}
