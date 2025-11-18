package com.viscript.npc.mixin;

import com.viscript.npc.npc.data.basics.setting.ILiving;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements ILiving {
    @Unique private int viScriptNpc$skinColor = -1;

    public LivingEntityMixin(EntityType<?> entityType, Level level) {super(entityType, level);}

    @Override
    public int getSkinColor() {return viScriptNpc$skinColor;}

    @Override
    public void setSkinColor(int color) {viScriptNpc$skinColor = color;}
}
