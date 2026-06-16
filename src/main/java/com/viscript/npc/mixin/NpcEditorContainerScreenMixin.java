package com.viscript.npc.mixin;

import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class NpcEditorContainerScreenMixin<T extends AbstractContainerMenu> {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void viscript_npc$skipNpcEditorBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (ViScriptNpcClientUtil.isNpcEditorScreen((Screen) (Object) this)) {
            ci.cancel();
        }
    }
}
