package com.viscript.npc.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MinecraftInteractionAccessor {
    @Accessor("rightClickDelay")
    int viscript_npc$getRightClickDelay();

    @Accessor("missTime")
    void viscript_npc$setMissTime(int missTime);

    @Invoker("startAttack")
    boolean viscript_npc$startAttack();

    @Invoker("continueAttack")
    void viscript_npc$continueAttack(boolean leftClick);

    @Invoker("startUseItem")
    void viscript_npc$startUseItem();

    @Invoker("pickBlock")
    void viscript_npc$pickBlock();
}
