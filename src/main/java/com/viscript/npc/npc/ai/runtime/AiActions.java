package com.viscript.npc.npc.ai.runtime;

import com.mojang.brigadier.StringReader;
import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.npc.ai.NpcAttackExecutor;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.navigation.NavigationStatus;
import com.viscript.npc.npc.data.ai.attack.AttackEffectData;
import com.viscript.npc.npc.data.ai.attack.MeleeAttackData;
import com.viscript.npc.npc.data.ai.attack.RangedAttackData;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 原子动作注册与执行入口；扩展动作使用同一实例和取消约定。 */
public final class AiActions {
    public interface Executor {
        AiStatus tick(AiExecution execution, AiExecution.Frame frame);
        default void cancel(AiExecution execution, AiExecution.Frame frame) {}
    }
    private static final Map<String, Executor> EXTENSIONS = new LinkedHashMap<>();

    public static synchronized void register(AiOperations.Operation definition, Executor executor) {
        java.util.Objects.requireNonNull(executor);
        AiOperations.register(definition);
        EXTENSIONS.put(definition.id(), executor);
    }

    public static void cancel(AiExecution execution, AiExecution.Frame frame) {
        if (frame.node == null) return;
        if (frame.node.type.equals("navigate") || frame.node.type.equals("approach") || frame.node.type.equals("jump") || frame.node.type.equals("patrol"))
            execution.cancelNavigation(frame);
        Executor extension = EXTENSIONS.get(frame.node.type);
        if (extension != null) extension.cancel(execution, frame);
    }

    public static AiStatus tick(AiExecution e, AiExecution.Frame f) {
        var n = f.node;
        var npc = e.npc;
        var recipient = e.recipient(n, n.type.equals("particle") || n.type.equals("sound"));
        var target = recipient instanceof LivingEntity living ? living : null;
        ServerLevel level = (ServerLevel) npc.level();
        boolean hasTarget = target != null && target.isAlive() && target.level() == level;
        try {
            switch (n.type) {
                case "wait": return f.age >= e.ticks(n) ? AiStatus.SUCCESS : AiStatus.RUNNING;
                case "wait_ground":
                    if (f.age > 1 && npc.onGround()) {
                        boolean reached = e.expectedLanding == null || npc.position().distanceTo(e.expectedLanding) < .85;
                        e.expectedLanding = null;
                        return reached ? AiStatus.SUCCESS : failure(e, "landing_blocked");
                    }
                    return f.age > e.ticks(n) ? failure(e, "landing_timeout") : AiStatus.RUNNING;
                case "find_target": {
                    if ((npc.tickCount + npc.getId()) % 5 != 0) return AiStatus.RUNNING;
                    var volume = AiPerception.volume(e, n);
                    e.recordPerception(n.id, volume);
                    target = AiPerception.find(e, n, volume, false);
                    if (e.flag(n, "set_current_target")) npc.setTarget(target);
                    output(e, n, AiValue.entity(target));
                    return target == null ? AiStatus.FAILURE : AiStatus.SUCCESS;
                }
                case "set_target": {
                    var entity = n.inputs.containsKey("recipient") || e.text(n, "entity").isBlank() ? recipient : level.getEntity(UUID.fromString(e.text(n, "entity")));
                    if (!(entity instanceof LivingEntity living) || !NpcFactionBridge.canHurt(npc, living)) return AiStatus.FAILURE;
                    npc.setTarget(living);
                    return npc.getTarget() == living ? AiStatus.SUCCESS : AiStatus.FAILURE;
                }
                case "get_entity":
                    output(e, n, AiValue.entity(recipient));
                    return recipient == null ? AiStatus.FAILURE : AiStatus.SUCCESS;
                case "entity_info": {
                    String property = e.text(n, "property");
                    if (property.equals("alive")) { output(e, n, AiValue.bool(recipient != null)); return AiStatus.SUCCESS; }
                    if (recipient == null) { output(e, n, defaultOutput(n)); return failure(e, "invalid_target"); }
                    AiValue value = switch (property) {
                        case "position" -> AiValue.position(recipient.position());
                        case "entity_type" -> AiValue.text(BuiltInRegistries.ENTITY_TYPE.getKey(recipient.getType()).toString());
                        case "health" -> target == null ? null : AiValue.number(target.getHealth());
                        case "max_health" -> target == null ? null : AiValue.number(target.getMaxHealth());
                        case "health_ratio" -> target == null ? null : AiValue.number(target.getHealth() / target.getMaxHealth());
                        default -> null;
                    };
                    if (value == null) { output(e, n, defaultOutput(n)); return AiStatus.FAILURE; }
                    output(e, n, value); return AiStatus.SUCCESS;
                }
                case "clear_target": npc.setTarget(null); return AiStatus.SUCCESS;
                case "position": {
                    String origin = e.text(n, "origin");
                    if (origin.equals("target") && !hasTarget) return AiStatus.FAILURE;
                    var entity = origin.equals("target") ? target : npc;
                    Vec3 position = origin.equals("fixed") ? e.resolve(n, "position").position : entity.position();
                    Vec3 forward = entity.getLookAngle().multiply(1, 0, 1).normalize();
                    position = position.add(forward.scale(e.number(n, "forward")))
                            .add(-forward.z * e.number(n, "right"), e.number(n, "up"), forward.x * e.number(n, "right"));
                    e.local.put(e.text(n, "output"), AiValue.position(position));
                    return AiStatus.SUCCESS;
                }
                case "navigate", "approach": {
                    if (n.type.equals("approach") && !hasTarget) { e.cancelNavigation(f); return AiStatus.FAILURE; }
                    double range = Math.max(.55, e.number(n, "range"));
                    Vec3 goal = n.type.equals("approach") ? target.position() : e.resolve(n, "position").position;
                    if (npc.position().distanceTo(goal) <= range) { e.cancelNavigation(f); return AiStatus.SUCCESS; }
                    if (f.age > e.ticks(n)) { e.cancelNavigation(f); return failure(e, "move_timeout"); }
                    if (f.started && !e.ownsNavigation(f)) return failure(e, "navigation_superseded");
                    if (!f.started || (n.type.equals("approach") && f.age % 10 == 0
                            && npc.getNpcNavigation().status() != NavigationStatus.COMPUTING
                            && f.state instanceof Vec3 old && old.distanceToSqr(goal) > 1)) {
                        e.claimNavigation(f);
                        if (!npc.getNpcNavigation().request(goal, e.number(n, "speed"), range)) return failure(e, "navigation_rejected");
                        f.started = true;
                        f.state = goal;
                    }
                    return switch (npc.getNpcNavigation().status()) {
                        case ARRIVED -> AiStatus.SUCCESS;
                        case FAILED, CANCELLED -> failure(e, npc.getNpcNavigation().reason());
                        default -> AiStatus.RUNNING;
                    };
                }
                case "patrol": {
                    double radius = Math.clamp(e.number(n, "patrol_radius"), 0, 32);
                    if (!f.started) {
                        Vec3 anchor = e.patrolOrigin();
                        Vec3 goal = null;
                        if (npc.position().distanceToSqr(anchor) > radius * radius + 1) goal = anchor;
                        else for (int attempt = 0; attempt < 12 && goal == null; attempt++) {
                            double angle = npc.getRandom().nextDouble() * Math.PI * 2;
                            double distance = Math.sqrt(npc.getRandom().nextDouble()) * radius;
                            var column = net.minecraft.core.BlockPos.containing(anchor.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
                            for (int dy = 2; dy >= -2; dy--) {
                                var feet = column.above(dy);
                                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                                        && level.noCollision(npc, npc.getBoundingBox().move(Vec3.atBottomCenterOf(feet).subtract(npc.position())))) {
                                    goal = Vec3.atBottomCenterOf(feet); break;
                                }
                            }
                        }
                        if (goal == null) return AiStatus.FAILURE;
                        e.claimNavigation(f);
                        if (!npc.getNpcNavigation().request(goal, e.number(n, "speed"), .75)) return failure(e, "navigation_rejected");
                        f.started = true;
                    }
                    if (!e.ownsNavigation(f)) return failure(e, "navigation_superseded");
                    if (f.age > e.ticks(n)) { e.cancelNavigation(f); return failure(e, "move_timeout"); }
                    var navigation = npc.getNpcNavigation().status();
                    if (navigation == NavigationStatus.FAILED || navigation == NavigationStatus.CANCELLED) return AiStatus.FAILURE;
                    if (navigation != NavigationStatus.ARRIVED) return AiStatus.RUNNING;
                    if (!(f.state instanceof Long)) f.state = e.time();
                    return e.time() - (long) f.state >= Math.ceil(e.number(n, "pause_seconds") * 20)
                            ? AiStatus.SUCCESS : AiStatus.RUNNING;
                }
                case "stop": e.stopNavigation(); return AiStatus.SUCCESS;
                case "look":
                    if (!hasTarget) return AiStatus.FAILURE;
                    npc.getLookControl().setLookAt(target, 30, 30); return AiStatus.SUCCESS;
                case "jump": {
                    Vec3 landing = e.resolve(n, "position").position;
                    if (landing.distanceTo(npc.position()) > e.number(n, "range")) return failure(e, "jump_range");
                    e.stopNavigation(); e.claimNavigation(f);
                    if (!npc.getNpcNavigation().jump(landing)) return failure(e, "jump_blocked");
                    e.expectedLanding = landing;
                    return AiStatus.SUCCESS;
                }
                case "melee": {
                    var onHit = e.hitHandler(n);
                    boolean accepted = hasTarget && NpcAttackExecutor.melee(npc, target, new MeleeAttackData((float) e.number(n, "damage"),
                            e.number(n, "range"), 1, e.number(n, "knockback"), AttackEffectData.NONE));
                    if (accepted) onHit.accept(target);
                    return accepted ? AiStatus.SUCCESS : AiStatus.FAILURE;
                }
                case "ranged": {
                    if (!hasTarget) return AiStatus.FAILURE;
                    CompoundTag tag = new CompoundTag();
                    tag.putDouble("damage", e.number(n, "damage"));
                    tag.putDouble("range", e.number(n, "range"));
                    tag.putFloat("projectile_speed", (float) e.number(n, "speed"));
                    tag.putString("projectile_item", e.text(n, "item"));
                    return NpcAttackExecutor.ranged(npc, target, RangedAttackData.fromTag(tag), e.hitHandler(n)) ? AiStatus.SUCCESS : AiStatus.FAILURE;
                }
                case "area_damage": {
                    double range = Math.min(32, Math.max(0, e.number(n, "range")));
                    var onHit = e.hitHandler(n);
                    for (LivingEntity hit : level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                            entity -> entity != npc && entity.isAlive() && npc.distanceToSqr(entity) <= range * range
                                    && npc.hasLineOfSight(entity) && NpcFactionBridge.canHurt(npc, entity))) {
                        if (hit.hurt(npc.damageSources().mobAttack(npc), (float) e.number(n, "damage"))) {
                            hit.knockback(e.number(n, "knockback"), npc.getX() - hit.getX(), npc.getZ() - hit.getZ());
                            onHit.accept(hit);
                        }
                    }
                    return AiStatus.SUCCESS;
                }
                case "knockback":
                    if (!hasTarget || !NpcFactionBridge.canHurt(npc, target)) return AiStatus.FAILURE;
                    target.knockback(e.number(n, "strength"), npc.getX() - target.getX(), npc.getZ() - target.getZ()); return AiStatus.SUCCESS;
                case "effect": {
                    if (!hasTarget) return AiStatus.FAILURE;
                    var effect = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(e.text(n, "effect")));
                    if (effect.isEmpty()) return failure(e, "invalid_resource");
                    if (!effect.get().value().isBeneficial() && target != npc && !NpcFactionBridge.canHurt(npc, target)) return AiStatus.FAILURE;
                    target.addEffect(new MobEffectInstance(effect.get(), e.ticks(n), (int) Math.max(0, Math.min(255, e.number(n, "amplifier")))));
                    return AiStatus.SUCCESS;
                }
                case "swing": npc.swing(InteractionHand.MAIN_HAND); return AiStatus.SUCCESS;
                case "particle": {
                    var options = ParticleArgument.readParticle(new StringReader(e.text(n, "particle")),
                            level.registryAccess());
                    double spread = Math.max(0, Math.min(16, e.number(n, "spread")));
                    if (recipient == null) return AiStatus.FAILURE;
                    level.sendParticles(options, recipient.getX(), recipient.getY() + .3, recipient.getZ(),
                            (int) Math.max(0, Math.min(256, e.number(n, "count"))), spread, spread, spread, .05);
                    return AiStatus.SUCCESS;
                }
                case "sound": {
                    var sound = BuiltInRegistries.SOUND_EVENT.getOptional(ResourceLocation.parse(e.text(n, "sound")));
                    if (sound.isEmpty()) return failure(e, "invalid_resource");
                    if (recipient == null) return AiStatus.FAILURE;
                    level.playSound(null, recipient.blockPosition(), sound.get(), SoundSource.NEUTRAL,
                            (float) Math.max(0, Math.min(4, e.number(n, "volume"))), (float) Math.max(.1, Math.min(4, e.number(n, "pitch"))));
                    return AiStatus.SUCCESS;
                }
                case "set_variable":
                    (e.text(n, "scope").equals("blackboard") ? e.blackboard : e.local)
                            .put(e.text(n, "variable"), e.resolve(n, "value").copy());
                    return AiStatus.SUCCESS;
                case "success": return AiStatus.SUCCESS;
                case "failure": return AiStatus.FAILURE;
                case "finish_success": return e.finish(AiStatus.SUCCESS);
                case "finish_failure": return e.finish(AiStatus.FAILURE);
                default:
                    Executor extension = EXTENSIONS.get(n.type);
                    return extension == null ? failure(e, "unknown_node") : extension.tick(e, f);
            }
        } catch (AiExecution.InputException exception) {
            throw exception;
        } catch (Exception exception) {
            return failure(e, "invalid_action");
        }
    }

    private static void output(AiExecution e, com.viscript.npc.npc.ai.data.AiNodeData node, AiValue value) {
        String id = e.text(node, "output");
        if (!id.isBlank()) e.local.put(id, value.copy());
    }
    private static AiValue defaultOutput(com.viscript.npc.npc.ai.data.AiNodeData node) {
        return switch (AiOperations.outputType(node)) {
            case "position" -> AiValue.position(Vec3.ZERO); case "string" -> AiValue.text(""); default -> AiValue.number(0);
        };
    }
    private static AiStatus failure(AiExecution execution, String reason) {
        execution.reason = reason;
        return AiStatus.FAILURE;
    }
}
