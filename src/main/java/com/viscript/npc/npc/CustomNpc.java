package com.viscript.npc.npc;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.math.Range;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.event.NpcEvent;
import com.viscript.npc.npc.ai.mind.MindMachine;
import com.viscript.npc.npc.data.attributes.MeleeConfig;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.attributes.ResistanceConfig;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.data.inventory.LootTableConfig;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod.integrations.ChatBoxConfig;
import com.viscript.npc.npc.data.mod.integrations.NpcModIntegrations;
import com.viscript.npc.util.common.StrUtil;
import com.zhenshiz.chatbox.utils.chatbox.ChatBoxCommandUtil;
import lombok.Getter;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomNpc extends PathfinderMob implements RangedAttackMob {
    private static final AttachmentType<NpcBasicsSetting> NPC_BASICS_SETTING = NpcAttachmentType.NPC_BASICS_SETTING.get();
    private static final AttachmentType<NpcDynamicModel> NPC_DYNAMIC_MODEL = NpcAttachmentType.NPC_DYNAMIC_MODEL.get();
    private static final AttachmentType<NpcAttributes> NPC_ATTRIBUTES = NpcAttachmentType.NPC_ATTRIBUTES.get();
    private static final AttachmentType<NpcInventory> NPC_INVENTORY = NpcAttachmentType.NPC_INVENTORY.get();
    private static final AttachmentType<NpcModIntegrations> NPC_MOD_INTEGRATIONS = NpcAttachmentType.NPC_MOD_INTEGRATIONS.get();

    //披风用参数
    public double xCloakO;
    public double yCloakO;
    public double zCloakO;
    public double xCloak;
    public double yCloak;
    public double zCloak;
    public float oBob;
    public float bob;

    // MindMachine
    @Getter
    MindMachine mind;

    public CustomNpc(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        mind = new MindMachine(this);
    }

    @Override
    public void performRangedAttack(LivingEntity livingEntity, float v) {

    }

    @Override
    public void tick() {
        super.tick();
        this.moveCloak();
        this.updateBoundingBox();
        if (this.level() instanceof ServerLevel) {
            NeoForge.EVENT_BUS.post(new NpcEvent.Tick(this));
        }

        // MindMachine
        mind.tick();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // 用于披风渲染
        this.oBob = this.bob;
        float f;
        if (this.onGround() && !this.isDeadOrDying() && !this.isSwimming()) {
            f = Math.min(0.1F, (float) this.getDeltaMovement().horizontalDistance());
        } else {
            f = 0.0F;
        }

        this.bob += (f - this.bob) * 0.4F;

        //是否在白天点燃
        if (this.isAlive()) {
            boolean flag = this.getNpcAttributes().isBurnInDay() && this.isSunBurnTick();
            if (flag) {
                ItemStack itemstack = this.getItemBySlot(EquipmentSlot.HEAD);
                if (!itemstack.isEmpty()) {
                    if (itemstack.isDamageableItem()) {
                        Item item = itemstack.getItem();
                        itemstack.setDamageValue(itemstack.getDamageValue() + this.random.nextInt(2));
                        if (itemstack.getDamageValue() >= itemstack.getMaxDamage()) {
                            this.onEquippedItemBroken(item, EquipmentSlot.HEAD);
                            this.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        }
                    }

                    flag = false;
                }

                if (flag) {
                    this.igniteForSeconds(8);
                }
            }
        }

        if (!this.level().isClientSide) {
            //回血
            if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
                if (this.getTarget() != null) {
                    this.heal(this.getNpcAttributes().getCombatRegenRate());
                } else {
                    this.heal(this.getNpcAttributes().getOutOfCombatRegenRate());
                }
            }
        }
    }

    public void updateBoundingBox() {
        NpcDynamicModel dynamicModel = this.getNpcDynamicModel();
        AABB aabb = dynamicModel.getAabb();
        this.bb = aabb.move(this.position().subtract(0, aabb.minY, 0));
        this.dimensions = EntityDimensions.scalable((float) aabb.getXsize(), (float) aabb.getYsize());
    }

    @Override
    protected AABB getAttackBoundingBox() {
        Entity entity = this.getVehicle();
        AABB aabb;
        if (entity != null) {
            AABB aabb1 = entity.getBoundingBox();
            AABB aabb2 = this.getBoundingBox();
            aabb = new AABB(Math.min(aabb2.minX, aabb1.minX), aabb2.minY, Math.min(aabb2.minZ, aabb1.minZ), Math.max(aabb2.maxX, aabb1.maxX), aabb2.maxY, Math.max(aabb2.maxZ, aabb1.maxZ));
        } else {
            aabb = this.getBoundingBox();
        }
        double attackRange = this.getNpcAttributes().getMeleeConfig().getAttackRange();

        return aabb.inflate(attackRange, 0.0F, attackRange);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        if (!state.is(Blocks.COBWEB) || !this.getNpcAttributes().isIgnoreCobweb()) {
            super.makeStuckInBlock(state, motionMultiplier);
        }
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (this.getNpcAttributes().isPotionImmune()) {
            return false;
        } else {
            return super.canBeAffected(effectInstance);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        super.addAdditionalSaveData(compoundTag);
        compoundTag.put(StrUtil.toCamelCase(NpcBasicsSetting.class.getSimpleName()), getNpcBasicsSetting().serializeNBT(Platform.getFrozenRegistry()));
        compoundTag.put(StrUtil.toCamelCase(NpcDynamicModel.class.getSimpleName()), getNpcDynamicModel().serializeNBT(Platform.getFrozenRegistry()));
        compoundTag.put(StrUtil.toCamelCase(NpcAttributes.class.getSimpleName()), getNpcAttributes().serializeNBT(Platform.getFrozenRegistry()));
        compoundTag.put(StrUtil.toCamelCase(NpcInventory.class.getSimpleName()), getNpcInventory().serializeNBT(Platform.getFrozenRegistry()));
        compoundTag.put(StrUtil.toCamelCase(NpcModIntegrations.class.getSimpleName()), getNpcModIntegrations().serializeNBT(Platform.getFrozenRegistry()));
        updateNpcState();
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        super.readAdditionalSaveData(compoundTag);
        getNpcBasicsSetting().deserializeNBT(Platform.getFrozenRegistry(), compoundTag.getCompound(StrUtil.toCamelCase(NpcBasicsSetting.class.getSimpleName())));
        getNpcDynamicModel().deserializeNBT(Platform.getFrozenRegistry(), compoundTag.getCompound(StrUtil.toCamelCase(NpcDynamicModel.class.getSimpleName())));
        getNpcAttributes().deserializeNBT(Platform.getFrozenRegistry(), compoundTag.getCompound(StrUtil.toCamelCase(NpcAttributes.class.getSimpleName())));
        getNpcInventory().deserializeNBT(Platform.getFrozenRegistry(), compoundTag.getCompound(StrUtil.toCamelCase(NpcInventory.class.getSimpleName())));
        getNpcModIntegrations().deserializeNBT(Platform.getFrozenRegistry(), compoundTag.getCompound(StrUtil.toCamelCase(NpcModIntegrations.class.getSimpleName())));
        updateNpcState();
    }

    public void updateNpcState() {
        //基础信息
        NpcBasicsSetting npcBasicsSetting = getNpcBasicsSetting();
        this.setData(NPC_BASICS_SETTING, npcBasicsSetting);
        this.setAttributeBaseValue(Attributes.SCALE, npcBasicsSetting.getModeSize());
        this.setInvulnerable(npcBasicsSetting.isInvulnerable());
        this.setNoAi(npcBasicsSetting.isNoAI());
        this.setNoGravity(npcBasicsSetting.isNoGravity());

        //动态模型
        NpcDynamicModel npcDynamicModel = getNpcDynamicModel();
        this.setData(NPC_DYNAMIC_MODEL, npcDynamicModel);

        //NPC属性
        NpcAttributes npcAttributes = getNpcAttributes();
        this.setData(NPC_ATTRIBUTES, npcAttributes);
        this.setAttributeBaseValue(Attributes.MAX_HEALTH, npcAttributes.getMaxHealth());
        this.setAttributeBaseValue(Attributes.MOVEMENT_SPEED, npcAttributes.getMovementSpeed());
        this.setAttributeBaseValue(Attributes.FOLLOW_RANGE, npcAttributes.getFollowRange());
        //近战属性
        this.setAttributeBaseValue(Attributes.ATTACK_DAMAGE, npcAttributes.getMeleeConfig().getAttackDamage());
        this.setAttributeBaseValue(Attributes.ATTACK_KNOCKBACK, npcAttributes.getMeleeConfig().getKnockback());
        this.setAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE, npcAttributes.getResistanceConfig().getKnockback());
        //防御
        this.setAttributeBaseValue(Attributes.ARMOR, npcAttributes.getDefenseConfig().getArmor());
        this.setAttributeBaseValue(Attributes.ARMOR_TOUGHNESS, npcAttributes.getDefenseConfig().getArmorToughness());

        //AI


        //联动模组
        NpcModIntegrations npcModIntegrations = getNpcModIntegrations();
        this.setData(NPC_MOD_INTEGRATIONS, npcModIntegrations);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            ChatBoxConfig chatBoxConfig = getNpcModIntegrations().getChatBoxConfig();
            if (ViScriptNpc.isChatBoxLoaded() && chatBoxConfig.isEnabled()) {
                ChatBoxCommandUtil.serverSkipDialogues(serverPlayer, ResourceLocation.parse(chatBoxConfig.getDialogResourceLocation()), chatBoxConfig.getGroup(), (Integer) chatBoxConfig.getIndex());
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        NpcInventory npcInventory = this.getNpcInventory();
        switch (npcInventory.getLootTableType()) {
            case DATAPACK -> {
                ResourceLocation lootTableRes = ResourceLocation.parse(npcInventory.getLootTable());
                ResourceKey<LootTable> resourcekey = ResourceKey.create(Registries.LOOT_TABLE, lootTableRes);
                LootTable loottable = this.level().getServer().reloadableRegistries().getLootTable(resourcekey);
                LootParams.Builder lootparams$builder = new LootParams.Builder((ServerLevel) this.level())
                        .withParameter(LootContextParams.THIS_ENTITY, this)
                        .withParameter(LootContextParams.ORIGIN, this.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                        .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
                        .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());
                if (recentlyHit && this.lastHurtByPlayer != null) {
                    lootparams$builder = lootparams$builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer)
                            .withLuck(this.lastHurtByPlayer.getLuck());
                }

                LootParams lootparams = lootparams$builder.create(LootContextParamSets.ENTITY);
                loottable.getRandomItems(lootparams, this.getLootTableSeed(), this::spawnAtLocation);
            }
            case CUSTOM -> {
                for (LootTableConfig lootTableConfig : npcInventory.getLootTables()) {
                    ResourceLocation key = ResourceLocation.parse(lootTableConfig.getItem());
                    Item item = BuiltInRegistries.ITEM.get(key);

                    if (item == Items.AIR) continue;

                    float chance = lootTableConfig.getChance();
                    int count = lootTableConfig.getCount();

                    // 随机概率判断是否掉落
                    float v = random.nextFloat();
                    System.out.println(v);
                    System.out.println(chance);
                    if (v <= chance) {
                        ItemStack stack = new ItemStack(item, count);
                        System.out.println(stack);

                        if (!lootTableConfig.getName().isEmpty()) {
                            stack.set(DataComponents.ITEM_NAME, Component.translatable(lootTableConfig.getName()));
                        }

                        // 将物品作为掉落物生成出来
                        this.spawnAtLocation(stack);
                    }
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        NpcAttributes npcAttributes = getNpcAttributes();
        if (npcAttributes.isImmuneToFire() && source.is(DamageTypeTags.IS_FIRE)) {
            return false;
        } else if (npcAttributes.isCanDrown() && source.is(DamageTypeTags.IS_DROWNING)) {
            return false;
        } else if (npcAttributes.isFallDamage() && source.is(DamageTypeTags.IS_FALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    public NpcBasicsSetting getNpcBasicsSetting() {
        return this.getData(NPC_BASICS_SETTING);
    }

    public NpcDynamicModel getNpcDynamicModel() {
        return this.getData(NPC_DYNAMIC_MODEL);
    }

    public NpcAttributes getNpcAttributes() {
        return this.getData(NPC_ATTRIBUTES);
    }

    public NpcModIntegrations getNpcModIntegrations() {
        return this.getData(NPC_MOD_INTEGRATIONS);
    }

    public NpcInventory getNpcInventory() {
        return this.getData(NPC_INVENTORY);
    }

    private void setAttributeBaseValue(Holder<Attribute> attribute, double value) {
        AttributeInstance attributeInstance = this.getAttribute(attribute);
        if (attributeInstance != null) attributeInstance.setBaseValue(value);
    }

    @Override
    public void die(DamageSource damageSource) {
        if (this.level() instanceof ServerLevel) {
            if (NeoForge.EVENT_BUS.post(new NpcEvent.Death(this, damageSource)).isCanceled()) return;
        }
        super.die(damageSource);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oBob = this.bob;
        this.bob = 0.0F;
    }

    private void moveCloak() {
        this.xCloakO = this.xCloak;
        this.yCloakO = this.yCloak;
        this.zCloakO = this.zCloak;
        double d0 = this.getX() - this.xCloak;
        double d1 = this.getY() - this.yCloak;
        double d2 = this.getZ() - this.zCloak;
        double d3 = 10.0;
        if (d0 > 10.0) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 > 10.0) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 > 10.0) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        if (d0 < -10.0) {
            this.xCloak = this.getX();
            this.xCloakO = this.xCloak;
        }

        if (d2 < -10.0) {
            this.zCloak = this.getZ();
            this.zCloakO = this.zCloak;
        }

        if (d1 < -10.0) {
            this.yCloak = this.getY();
            this.yCloakO = this.yCloak;
        }

        this.xCloak += d0 * 0.25;
        this.zCloak += d2 * 0.25;
        this.yCloak += d1 * 0.25;
    }

    protected void dropFromLootTable(DamageSource damageSourceIn, boolean attackedRecently) {
        //取消npc自带的战利品系统
    }

    private void attack(CustomNpc npc, LivingEntity entity) {
        MeleeConfig.executeAdditionalEffects(npc, entity);
    }

    @EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
    public static class EventHandler {

        @SubscribeEvent
        public static void hurtAndAttack(LivingIncomingDamageEvent event) {
            if (event.getEntity().level().isClientSide) return;
            DamageSource source = event.getSource();
            //npc受伤
            if (event.getEntity() instanceof CustomNpc npc) {
                DamageContainer container = event.getContainer();
                float currentDamage = container.getNewDamage();
                ResistanceConfig resistanceConfig = npc.getNpcAttributes().getResistanceConfig();
                if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                    currentDamage *= 1 - resistanceConfig.getProjectile();
                } else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
                    currentDamage *= 1 - resistanceConfig.getExplosion();
                } else if (source.getDirectEntity() instanceof LivingEntity) {
                    currentDamage *= 1 - resistanceConfig.getMelee();
                }
                container.setNewDamage(currentDamage);
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Hurt(event)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
            //npc攻击
            if (source.getEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Attack(npc, event)).isCanceled()) {
                    event.setCanceled(true);
                }
                npc.attack(npc, event.getEntity());
            } else if (source.getDirectEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Attack(npc, event)).isCanceled()) {
                    event.setCanceled(true);
                }
                npc.attack(npc, event.getEntity());
            }
        }

        @SubscribeEvent
        public static void hurt(LivingDamageEvent.Post event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                //反弹伤害
                float reboundDamage = npc.getNpcAttributes().getDefenseConfig().getReboundDamage();
                DamageSource source = event.getSource();
                if (reboundDamage > 0) {
                    if (source.getDirectEntity() instanceof LivingEntity directEntity) {
                        directEntity.hurt(npc.damageSources().source(DamageTypes.MOB_ATTACK), reboundDamage);
                    }
                    if (source.getEntity() instanceof LivingEntity entity) {
                        entity.hurt(npc.damageSources().source(DamageTypes.MOB_ATTACK), reboundDamage);
                    }
                }
            }
        }

        @SubscribeEvent
        public static void npcInteract(PlayerInteractEvent.EntityInteract event) {
            if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer serverPlayer && event.getTarget() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Interact(npc, serverPlayer)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void spawn(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof CustomNpc npc && event.getLevel() instanceof ServerLevel level) {
                NeoForge.EVENT_BUS.post(new NpcEvent.Spawn(npc));
            }
        }

        @SubscribeEvent
        public static void killed(LivingDeathEvent event) {
            if (event.getEntity().level().isClientSide) return;
            DamageSource source = event.getSource();
            if (source.getEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Killed(npc, event.getEntity(), source)).isCanceled()) {
                    event.setCanceled(true);
                }
            } else if (source.getDirectEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.Killed(npc, event.getEntity(), source)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
        }


        @SubscribeEvent
        public static void changeTarget(LivingChangeTargetEvent event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                if (NeoForge.EVENT_BUS.post(new NpcEvent.TargetEvent(npc, event)).isCanceled()) {
                    event.setCanceled(true);
                }
            }
        }

        @SubscribeEvent
        public static void onXpDrop(LivingExperienceDropEvent event) {
            if (event.getEntity() instanceof CustomNpc npc) {
                Range levelConfig = npc.getNpcInventory().getLevelRange();
                int random = Mth.randomBetweenInclusive(RandomSource.create(), levelConfig.getMin().intValue(), levelConfig.getMax().intValue());
                event.setDroppedExperience(random);
            }
        }
    }
}
