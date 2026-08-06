package com.viscript.npc.npc.ai;

import com.mojang.serialization.DataResult;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.data.ai.attack.AttackEffectData;
import com.viscript.npc.npc.data.ai.attack.MeleeAttackData;
import com.viscript.npc.npc.data.ai.attack.RangedAttackData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.thexeler.api.IntentionDiagnostic;
import org.thexeler.api.IntentionTypeRegistry;
import org.thexeler.api.intention.EditorMetadata;
import org.thexeler.api.intention.IntentionContext;
import org.thexeler.api.intention.IntentionInstance;
import org.thexeler.api.intention.IntentionResult;
import org.thexeler.api.intention.IntentionStateCodec;
import org.thexeler.api.intention.IntentionType;
import org.thexeler.api.parameter.BuiltinParameterTypes;
import org.thexeler.api.parameter.ParameterDefinition;
import org.thexeler.api.parameter.ParameterSchema;
import org.thexeler.api.parameter.ParameterValues;
import org.thexeler.api.world.ActorReference;
import org.thexeler.api.world.MindActor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** VSN-owned, strongly typed attack intentions for AM assets and flow submissions. */
public final class NpcAttackIntentions {
    public static final ResourceLocation MELEE = ViScriptNpc.id("melee_attack");
    public static final ResourceLocation RANGED = ViScriptNpc.id("ranged_attack");
    private static final ResourceLocation CATEGORY = ViScriptNpc.id("combat");

    private static final ParameterDefinition<ActorReference> TARGET =
            ParameterDefinition.required("target", BuiltinParameterTypes.ACTOR_REFERENCE);
    private static final ParameterDefinition<Float> DAMAGE =
            ParameterDefinition.optional("damage", BuiltinParameterTypes.FLOAT, 2.0F);
    private static final ParameterDefinition<Double> MELEE_RANGE =
            ParameterDefinition.optional("range", BuiltinParameterTypes.DOUBLE, 2.0D);
    private static final ParameterDefinition<Double> RANGED_RANGE =
            ParameterDefinition.optional("range", BuiltinParameterTypes.DOUBLE, 16.0D);
    private static final ParameterDefinition<Integer> COOLDOWN =
            ParameterDefinition.optional("cooldown", BuiltinParameterTypes.INT, 20);
    private static final ParameterDefinition<Double> KNOCKBACK =
            ParameterDefinition.optional("knockback", BuiltinParameterTypes.DOUBLE, 0.0D);
    private static final ParameterDefinition<String> EFFECT_TYPE =
            ParameterDefinition.optional("effect_type", BuiltinParameterTypes.STRING, "none");
    private static final ParameterDefinition<ResourceLocation> EFFECT_ID = ParameterDefinition.optional(
            "effect_id", BuiltinParameterTypes.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:slowness"));
    private static final ParameterDefinition<Integer> EFFECT_SECONDS =
            ParameterDefinition.optional("effect_seconds", BuiltinParameterTypes.INT, 0);
    private static final ParameterDefinition<Integer> EFFECT_AMPLIFIER =
            ParameterDefinition.optional("effect_amplifier", BuiltinParameterTypes.INT, 0);

    private static final ParameterDefinition<Float> PROJECTILE_SPEED =
            ParameterDefinition.optional("projectile_speed", BuiltinParameterTypes.FLOAT, 1.6F);
    private static final ParameterDefinition<Float> INACCURACY =
            ParameterDefinition.optional("inaccuracy", BuiltinParameterTypes.FLOAT, 0.0F);
    private static final ParameterDefinition<Integer> PROJECTILES_PER_SHOT =
            ParameterDefinition.optional("projectiles_per_shot", BuiltinParameterTypes.INT, 1);
    private static final ParameterDefinition<Float> VISUAL_SCALE =
            ParameterDefinition.optional("visual_scale", BuiltinParameterTypes.FLOAT, 1.0F);
    private static final ParameterDefinition<Boolean> GRAVITY =
            ParameterDefinition.optional("affected_by_gravity", BuiltinParameterTypes.BOOL, true);
    private static final ParameterDefinition<ResourceLocation> PROJECTILE_ITEM = ParameterDefinition.optional(
            "projectile_item", BuiltinParameterTypes.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:arrow"));
    private static final ParameterDefinition<ResourceLocation> SHOOT_SOUND = ParameterDefinition.optional(
            "shoot_sound", BuiltinParameterTypes.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:entity.skeleton.shoot"));
    private static final ParameterDefinition<ResourceLocation> HIT_SOUND = ParameterDefinition.optional(
            "hit_sound", BuiltinParameterTypes.RESOURCE_LOCATION, ResourceLocation.parse("minecraft:entity.arrow.hit"));
    private static final ParameterDefinition<Float> EXPLOSION_POWER =
            ParameterDefinition.optional("explosion_power", BuiltinParameterTypes.FLOAT, 0.0F);
    private static final ParameterDefinition<Boolean> EXPLOSION_BREAKS_BLOCKS =
            ParameterDefinition.optional("explosion_breaks_blocks", BuiltinParameterTypes.BOOL, false);
    private static final ParameterDefinition<String> TRAIL_TYPE =
            ParameterDefinition.optional("trail_type", BuiltinParameterTypes.STRING, "none");

    private NpcAttackIntentions() {
    }

    public static void register(IntentionTypeRegistry registry) {
        register(registry, MELEE, schema(TARGET, DAMAGE, MELEE_RANGE, COOLDOWN, KNOCKBACK,
                        EFFECT_TYPE, EFFECT_ID, EFFECT_SECONDS, EFFECT_AMPLIFIER), false);
        register(registry, RANGED, schema(TARGET, DAMAGE, RANGED_RANGE, COOLDOWN, KNOCKBACK,
                        PROJECTILE_SPEED, INACCURACY, PROJECTILES_PER_SHOT, VISUAL_SCALE, GRAVITY,
                        PROJECTILE_ITEM, SHOOT_SOUND, HIT_SOUND, EXPLOSION_POWER,
                        EXPLOSION_BREAKS_BLOCKS, TRAIL_TYPE, EFFECT_TYPE, EFFECT_ID,
                        EFFECT_SECONDS, EFFECT_AMPLIFIER), true);
    }

    private static void register(IntentionTypeRegistry registry, ResourceLocation id,
                                 ParameterSchema schema, boolean ranged) {
        var factory = (org.thexeler.api.intention.IntentionFactory<AttackIntention>)
                (actor, parameters) -> new AttackIntention(actor, parameters, ranged);
        IntentionStateCodec<AttackIntention> codec = new IntentionStateCodec<>() {
            @Override
            public CompoundTag encode(AttackIntention instance) {
                CompoundTag tag = new CompoundTag();
                if (instance.readyAt >= 0) tag.putLong("ready_at", instance.readyAt);
                return tag;
            }

            @Override
            public DataResult<AttackIntention> decode(MindActor actor, ParameterValues parameters, CompoundTag state) {
                try {
                    AttackIntention instance = new AttackIntention(actor, parameters, ranged);
                    if (state.contains("ready_at")) instance.readyAt = state.getLong("ready_at");
                    return DataResult.success(instance);
                } catch (RuntimeException exception) {
                    return DataResult.error(() -> exception.getMessage() == null
                            ? exception.getClass().getSimpleName() : exception.getMessage());
                }
            }
        };
        String key = "intention." + id.getNamespace() + "." + id.getPath();
        registry.registerType(new IntentionType<>(id, schema, Set.of(), factory, codec,
                new EditorMetadata(key + ".name", key + ".description", CATEGORY), 1));
    }

    private static ParameterSchema schema(ParameterDefinition<?>... definitions) {
        return new ParameterSchema(List.of(definitions));
    }

    private static AttackEffectData effect(ParameterValues parameters) {
        return new AttackEffectData(AttackEffectData.Type.parse(parameters.get(EFFECT_TYPE)),
                parameters.get(EFFECT_ID).toString(), parameters.get(EFFECT_SECONDS),
                parameters.get(EFFECT_AMPLIFIER));
    }

    private static final class AttackIntention implements IntentionInstance {
        private final NpcMindActor actor;
        private final ActorReference target;
        private final int cooldown;
        private final MeleeAttackData melee;
        private final RangedAttackData ranged;
        private long readyAt = -1;

        private AttackIntention(MindActor actor, ParameterValues parameters, boolean isRanged) {
            if (!(actor instanceof NpcMindActor npcActor)) {
                throw new IllegalArgumentException("VSN attack intentions require a CustomNpc actor");
            }
            this.actor = npcActor;
            this.target = parameters.get(TARGET);
            this.cooldown = Math.max(1, parameters.get(COOLDOWN));
            this.melee = isRanged ? null : new MeleeAttackData(parameters.get(DAMAGE),
                    parameters.get(MELEE_RANGE), cooldown, parameters.get(KNOCKBACK), effect(parameters));
            this.ranged = !isRanged ? null : new RangedAttackData(parameters.get(RANGED_RANGE), cooldown,
                    parameters.get(DAMAGE), parameters.get(KNOCKBACK), parameters.get(PROJECTILE_SPEED),
                    parameters.get(INACCURACY), parameters.get(PROJECTILES_PER_SHOT), parameters.get(VISUAL_SCALE),
                    parameters.get(GRAVITY), parameters.get(PROJECTILE_ITEM).toString(),
                    parameters.get(SHOOT_SOUND).toString(), parameters.get(HIT_SOUND).toString(),
                    parameters.get(EXPLOSION_POWER), parameters.get(EXPLOSION_BREAKS_BLOCKS),
                    parameters.get(TRAIL_TYPE), effect(parameters));
        }

        @Override
        public IntentionResult execute(IntentionContext context) {
            if (readyAt >= 0) {
                return actor.npc().level().getGameTime() >= readyAt
                        ? IntentionResult.COMPLETED : IntentionResult.RUNNING;
            }
            var living = actor.resolveLivingEntity(target);
            if (living == null) {
                return IntentionResult.failed(new IntentionDiagnostic(
                        "INVALID_ATTACK_TARGET", "Attack target is missing or is not a living entity"));
            }
            boolean accepted = ranged == null
                    ? NpcAttackExecutor.melee(actor.npc(), living, melee)
                    : NpcAttackExecutor.ranged(actor.npc(), living, ranged);
            if (!accepted) {
                return IntentionResult.failed(new IntentionDiagnostic(
                        "ATTACK_REJECTED", "Target, range, or faction rules rejected the attack"));
            }
            readyAt = actor.npc().level().getGameTime() + cooldown;
            return IntentionResult.RUNNING;
        }

        @Override
        public void hold(IntentionContext context) {
        }
    }
}
