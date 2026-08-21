package com.viscript.npc.npc.ai;

import com.mojang.authlib.GameProfile;
import com.viscript.npc.compat.team.NpcFactionBridge;
import com.viscript.npc.npc.CustomNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;
import org.thexeler.api.IntentionDiagnostic;
import org.thexeler.api.capability.ActionResult;
import org.thexeler.api.capability.AttackRequest;
import org.thexeler.api.capability.BlockInteractionCapability;
import org.thexeler.api.capability.CombatCapability;
import org.thexeler.api.capability.DamageableCapability;
import org.thexeler.api.capability.EntityInteractionCapability;
import org.thexeler.api.capability.InteractionRequest;
import org.thexeler.api.capability.ItemInteractionCapability;
import org.thexeler.api.capability.MovableCapability;
import org.thexeler.api.capability.MovementConfig;
import org.thexeler.api.capability.TargetResolverCapability;
import org.thexeler.api.navigation.NavigationRegistry;
import org.thexeler.api.navigation.NavigationStrategy;
import org.thexeler.api.world.AbstractLivingMindActor;
import org.thexeler.api.world.ActorReference;
import org.thexeler.api.world.ItemReference;
import org.thexeler.api.world.MindEntityActor;
import org.thexeler.api.world.MindMoveControl;
import org.thexeler.api.world.MindPosition;

/** Minecraft/NeoForge 能力适配器，作为 CustomNpc 唯一的 AM 执行对象。 */
public final class NpcMindActor extends AbstractLivingMindActor<CustomNpc>
        implements MovableCapability, TargetResolverCapability, CombatCapability, DamageableCapability,
        EntityInteractionCapability, BlockInteractionCapability, ItemInteractionCapability {
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    private static final double DEFAULT_INTERACTION_DISTANCE = 6.0D;

    private final MindMoveControl moveControl;

    public NpcMindActor(CustomNpc npc) {
        super(npc);
        moveControl = new MindMoveControl(this);
    }

    public CustomNpc npc() {
        return entity();
    }

    @Override
    public MovementConfig movementConfig() {
        double speed = Math.max(0.01D, npc().getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
        return new MovementConfig(NavigationRegistry.defaultStrategy().getId(), true, speed);
    }

    @Override
    public NavigationStrategy navigation() {
        return NavigationRegistry.defaultStrategy();
    }

    @Override
    public MindMoveControl moveControl() {
        return moveControl;
    }

    @Override
    public double collisionHeight() {
        return npc().getBbHeight();
    }

    @Override
    public void moveBy(MindPosition delta) {
        npc().move(MoverType.SELF, new Vec3(delta.x(), delta.y(), delta.z()));
    }

    @Override
    public void lookAt(MindPosition target) {
        npc().getLookControl().setLookAt(target.x(), target.y(), target.z(), 30.0F, 30.0F);
    }

    @Override
    public ActorResolution resolve(ActorReference reference) {
        MinecraftServer server = npc().getServer();
        if (server == null) {
            return new ActorResolution.TemporarilyUnavailable();
        }
        ServerLevel level = server.getLevel(reference.dimension());
        if (level == null) {
            return new ActorResolution.Error("Unknown dimension " + reference.dimension().location());
        }
        Entity target = level.getEntity(reference.uuid());
        if (target == null) {
            return reference.dimension().equals(npc().level().dimension())
                    ? new ActorResolution.Missing()
                    : new ActorResolution.TemporarilyUnavailable();
        }
        return new ActorResolution.Resolved(MindEntityActor.wrap(target));
    }

    @Override
    public ActionResult attack(ActorReference reference, AttackRequest request) {
        if (request.mode().isPresent()) {
            return failed("UNSUPPORTED_ATTACK_MODE", "No VSN ranged attack mode is registered: " + request.mode().orElseThrow());
        }
        LivingEntity target = resolveLiving(reference);
        if (target == null || !target.isAlive() || !target.isAttackable()) {
            return failed("INVALID_ATTACK_TARGET", "Attack target is missing or cannot be attacked");
        }
        if (!NpcFactionBridge.canHurt(npc(), target)) {
            return failed("FRIENDLY_TARGET", "Configured faction rules reject this target");
        }
        npc().getLookControl().setLookAt(target, 30.0F, 30.0F);
        npc().swing(InteractionHand.MAIN_HAND);
        if (!target.hurt(npc().damageSources().mobAttack(npc()), request.damage())) {
            return failed("ATTACK_REJECTED", "Minecraft rejected the damage operation");
        }
        npc().setLastHurtMob(target);
        return ActionResult.running("attack:" + (npc().level().getGameTime() + ATTACK_COOLDOWN_TICKS));
    }

    @Override
    public ActionResult taskStatus(String taskId) {
        if (taskId != null && taskId.startsWith("attack:")) {
            try {
                long readyAt = Long.parseLong(taskId.substring("attack:".length()));
                return npc().level().getGameTime() >= readyAt ? ActionResult.succeeded() : ActionResult.running(taskId);
            } catch (NumberFormatException ignored) {
            }
        }
        return failed("UNKNOWN_TASK", "Unknown NPC action task '" + taskId + "'");
    }

    @Override
    public void holdTask(String taskId) {
        // 攻击冷却任务不持有外部动画或异步资源。
    }

    @Override
    public boolean isAttackable() {
        return npc().isAttackable();
    }

    @Override
    public ActionResult hurt(ActorReference source, float amount) {
        LivingEntity attacker = resolveLiving(source);
        boolean accepted = attacker == null
                ? npc().hurt(npc().damageSources().generic(), amount)
                : npc().hurt(npc().damageSources().mobAttack(attacker), amount);
        return accepted ? ActionResult.succeeded() : failed("DAMAGE_REJECTED", "Minecraft rejected the damage operation");
    }

    @Override
    public ActionResult interactEntity(ActorReference reference, InteractionRequest request) {
        Entity target = resolveEntity(reference);
        if (target == null || target.isRemoved()) {
            return failed("INVALID_INTERACTION_TARGET", "Interaction target is missing");
        }
        if (npc().distanceToSqr(target) > square(maxDistance(request))) {
            return failed("INTERACTION_OUT_OF_RANGE", "Entity interaction target is out of range");
        }
        InteractionHand hand = hand(request);
        FakePlayer player = preparePlayer((ServerLevel) npc().level(), hand);
        InteractionResult result = player.interactOn(target, hand);
        syncHand(player, hand);
        if (!result.consumesAction()) {
            return failed("INTERACTION_REJECTED", "Minecraft rejected the entity interaction");
        }
        npc().swing(hand);
        return ActionResult.succeeded();
    }

    @Override
    public ActionResult interactBlock(MindPosition position, InteractionRequest request) {
        if (!(npc().level() instanceof ServerLevel level)) {
            return failed("LEVEL_UNAVAILABLE", "Block interaction requires a server level");
        }
        BlockPos blockPos = BlockPos.containing(position.x(), position.y(), position.z());
        if (!level.isLoaded(blockPos) || level.getBlockState(blockPos).isAir()) {
            return failed("INVALID_INTERACTION_TARGET", "Interaction block is missing or unloaded");
        }
        if (npc().distanceToSqr(Vec3.atCenterOf(blockPos)) > square(maxDistance(request))) {
            return failed("INTERACTION_OUT_OF_RANGE", "Block interaction target is out of range");
        }
        InteractionHand hand = hand(request);
        Direction direction = direction(request);
        FakePlayer player = preparePlayer(level, hand);
        BlockHitResult hit = new BlockHitResult(position.toVec3(), direction, blockPos, false);
        InteractionResult result = player.gameMode.useItemOn(player, level, player.getItemInHand(hand), hand, hit);
        syncHand(player, hand);
        if (!result.consumesAction()) {
            return failed("INTERACTION_REJECTED", "Minecraft rejected the block interaction");
        }
        npc().swing(hand);
        return ActionResult.succeeded();
    }

    @Override
    public ActionResult useItem(ItemReference item, InteractionRequest request) {
        if (!(npc().level() instanceof ServerLevel level)) {
            return failed("LEVEL_UNAVAILABLE", "Item interaction requires a server level");
        }
        InteractionHand hand = findHand(item, request);
        if (hand == null) {
            return failed("ITEM_NOT_HELD", "NPC is not holding item '" + item.itemId() + "'");
        }
        FakePlayer player = preparePlayer(level, hand);
        InteractionResult result = player.gameMode.useItem(player, level, player.getItemInHand(hand), hand);
        syncHand(player, hand);
        if (!result.consumesAction()) {
            return failed("INTERACTION_REJECTED", "Minecraft rejected the item interaction");
        }
        npc().swing(hand);
        return ActionResult.succeeded();
    }

    LivingEntity resolveLivingEntity(ActorReference reference) {
        MinecraftServer server = npc().getServer();
        ServerLevel level = server == null ? null : server.getLevel(reference.dimension());
        Entity entity = level == null ? null : level.getEntity(reference.uuid());
        return entity instanceof LivingEntity living ? living : null;
    }

    private Entity resolveEntity(ActorReference reference) {
        MinecraftServer server = npc().getServer();
        ServerLevel level = server == null ? null : server.getLevel(reference.dimension());
        return level == null ? null : level.getEntity(reference.uuid());
    }

    private FakePlayer preparePlayer(ServerLevel level, InteractionHand hand) {
        String name = "[VSN]" + npc().getUUID().toString().substring(0, 8);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(npc().getUUID(), name));
        player.moveTo(npc().getX(), npc().getY(), npc().getZ(), npc().getYRot(), npc().getXRot());
        player.setItemInHand(hand, npc().getItemInHand(hand).copy());
        return player;
    }

    private void syncHand(FakePlayer player, InteractionHand hand) {
        npc().setItemInHand(hand, player.getItemInHand(hand).copy());
    }

    @Nullable
    private InteractionHand findHand(ItemReference item, InteractionRequest request) {
        InteractionHand requested = hand(request);
        if (matches(npc().getItemInHand(requested), item)) return requested;
        InteractionHand other = requested == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return matches(npc().getItemInHand(other), item) ? other : null;
    }

    private static boolean matches(ItemStack stack, ItemReference reference) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(reference.itemId());
    }

    private static InteractionHand hand(InteractionRequest request) {
        Object value = request.options().get("hand");
        return value != null && "off_hand".equalsIgnoreCase(value.toString())
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static Direction direction(InteractionRequest request) {
        Object value = request.options().get("face");
        if (value != null) {
            try {
                return Direction.valueOf(value.toString().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Direction.UP;
    }

    private static double maxDistance(InteractionRequest request) {
        Object value = request.options().get("max_distance");
        return value instanceof Number number ? Math.max(0.0D, number.doubleValue()) : DEFAULT_INTERACTION_DISTANCE;
    }

    private static double square(double value) {
        return value * value;
    }

    private LivingEntity resolveLiving(ActorReference reference) {
        return resolveLivingEntity(reference);
    }

    private static ActionResult failed(String code, String detail) {
        return ActionResult.failed(new IntentionDiagnostic(code, detail));
    }
}
