package com.viscript.npc.mixin;

import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class NpcAiWorldCameraMixin {
    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot, float roll);

    @Inject(method = "setup", at = @At("RETURN"))
    private void viscript_npc$applyNpcAiWorldCamera(BlockGetter level, Entity entity, boolean detached,
                                                    boolean thirdPersonReverse, float partialTick, CallbackInfo ci) {
        if (!ViScriptNpcClientUtil.shouldOverrideNpcAiWorldCamera()) {
            return;
        }
        setRotation(ViScriptNpcClientUtil.getNpcAiWorldFixedCameraYaw(),
                ViScriptNpcClientUtil.getNpcAiWorldFixedCameraPitch(), 0.0F);
        setPosition(ViScriptNpcClientUtil.getNpcAiWorldFixedCameraPosition());
    }
}
