package com.viscript.npc.mixin;

import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldSceneRenderer.class)
public interface WorldSceneRendererAccessor {
    @Accessor("camera")
    Camera viscript_npc$getCamera();
}
