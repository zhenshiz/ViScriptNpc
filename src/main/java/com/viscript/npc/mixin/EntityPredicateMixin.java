package com.viscript.npc.mixin;

import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(EntityPredicate.class)
public abstract class EntityPredicateMixin {

    @Shadow @Final private Optional<EntityTypePredicate> entityType;

    @Inject(method = "matches(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    public void is(ServerLevel level, Vec3 position, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        // 配置项还没有相关的字段，先放在这里
/*        if (entity instanceof CustomNpc npc && npc.getMainHandItem().isEmpty() && entityType.isPresent()) {
            var types = entityType.get().types();
            var holders = BuiltInRegistries.ENTITY_TYPE.getOrCreateTag(EntityTypeTags.SENSITIVE_TO_SMITE);
            if (types.equals(holders)) cir.setReturnValue(true);
        }*/
    }
}
