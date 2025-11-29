package com.viscript.npc.mixin;

import com.viscript.npc.npc.CustomNpc;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.EntityTypePredicate;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
        if (entity instanceof CustomNpc npc && entityType.isPresent()) {
            var types = entityType.get().types();
            if (!(types instanceof HolderSet.Named<EntityType<?>>)) return;
            var attributes = npc.getNpcAttributes();
            var entityTypes = BuiltInRegistries.ENTITY_TYPE;
            //是否受亡灵杀手影响
            if (attributes.isSensitiveToSmite() && types.equals(entityTypes.getOrCreateTag(EntityTypeTags.SENSITIVE_TO_SMITE))) {
                cir.setReturnValue(true);
                return;
            }
            //是否受节肢杀手影响
            if (attributes.isSensitiveToBaneOfArthropods() && types.equals(entityTypes.getOrCreateTag(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS))) {
                cir.setReturnValue(true);
                return;
            }
            //是否受穿刺影响
            if (attributes.isSensitiveToImpaling() && types.equals(entityTypes.getOrCreateTag(EntityTypeTags.SENSITIVE_TO_IMPALING))) {
                cir.setReturnValue(true);
            }
        }
    }
}
