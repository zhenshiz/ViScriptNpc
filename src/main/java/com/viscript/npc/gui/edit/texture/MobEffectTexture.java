package com.viscript.npc.gui.edit.texture;

import com.lowdragmc.lowdraglib2.gui.texture.TransformTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@LDLRegisterClient(name = "mob_effect_texture", registry = "ldlib2:gui_texture")
public class MobEffectTexture extends TransformTexture {
    private static final ResourceLocation DEFAULT_EFFECT = ResourceLocation.withDefaultNamespace("slowness");

    public MobEffect[] mobEffects;
    private int index;
    private int ticks;
    private long lastTick;

    public MobEffectTexture() {
        this(BuiltInRegistries.MOB_EFFECT.get(DEFAULT_EFFECT));
    }

    public MobEffectTexture(MobEffect... effects) {
        this.index = 0;
        this.ticks = 0;
        this.mobEffects = effects;
    }

    public MobEffectTexture setEffects(MobEffect... effects) {
        this.mobEffects = effects;
        this.index = 0;
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    public void updateTick() {
        if (Minecraft.getInstance().level != null) {
            long tick = Minecraft.getInstance().level.getGameTime();
            if (tick == this.lastTick) return;
            this.lastTick = tick;
            if (this.mobEffects.length > 1 && ++this.ticks % 20 == 0 && ++this.index == this.mobEffects.length) {
                this.index = 0;
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width, float height, float partialTicks) {
        if (this.mobEffects == null || this.mobEffects.length == 0) return;
        updateTick();
        if (this.index >= this.mobEffects.length) {
            this.index = 0;
        }

        MobEffect effect = this.mobEffects[this.index];
        if (effect == null) return;

        Holder<MobEffect> effectHolder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
        TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effectHolder);
        graphics.pose().pushPose();
        graphics.pose().scale(width / 18.0F, height / 18.0F, 1.0F);
        graphics.pose().translate(x * 18.0F / width, y * 18.0F / height, 0);
        graphics.blit(0, 0, 0, 18, 18, sprite);
        graphics.pose().popPose();
    }
}
