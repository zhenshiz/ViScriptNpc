package com.viscript.npc.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("mouseGrabbed")
    void viscript_npc$setMouseGrabbed(boolean mouseGrabbed);

    @Accessor("xpos")
    void viscript_npc$setXpos(double xpos);

    @Accessor("ypos")
    void viscript_npc$setYpos(double ypos);
}
