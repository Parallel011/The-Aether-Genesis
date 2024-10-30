package com.aetherteam.genesis.entity.projectile;

import com.aetherteam.aether.client.AetherSoundEvents;
import com.aetherteam.genesis.entity.GenesisEntityTypes;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public class HostEyeProjectile extends Projectile {
    private static final EntityDataAccessor<Vector3f> ID_MOTION = SynchedEntityData.defineId(HostEyeProjectile.class, EntityDataSerializers.VECTOR3);
    private Mob projectileOwner;
    private Vec3 targetPoint;
    private float velocity;
    private Direction moveDirection = null;
    private int moveDelay = this.calculateMoveDelay();

    public HostEyeProjectile(EntityType<? extends HostEyeProjectile> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public HostEyeProjectile(Level level, Mob owner) {
        this(GenesisEntityTypes.HOST_EYE.get(), level);
        this.projectileOwner = owner;
        this.setOwner(owner);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ID_MOTION, new Vector3f(0, 0, 0));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.moveDelay <= 0) {
                this.targetPoint = this.findTargetPoint();
                if (this.targetPoint != null) {
                    Direction moveDir = this.getMoveDirection(this.targetPoint);
                    if (!(this.axisDistance(this.targetPoint, this.position(), moveDir) <= 0.0)) {
                        if (this.velocity < this.getMaxVelocity()) {
                            this.velocity = Math.min(this.getMaxVelocity(), this.velocity + this.getVelocityIncrease());
                        }
                        this.setMotionVector(new Vector3f((float) moveDir.getStepX() * this.velocity, (float) moveDir.getStepY() * this.velocity, (float) moveDir.getStepZ() * this.velocity));
                        this.hasImpulse = true;
                    } else {
                        this.stop();
                    }
                }
            } else {
                --this.moveDelay;
            }
            if (this.moveDirection == null) {
                this.setMotionVector(this.getMotionVector().mul(new Vector3f(0, 0.05F, 0)));
            }
            HitResult hitresult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hitresult.getType() != HitResult.Type.MISS && !net.neoforged.neoforge.event.EventHooks.onProjectileImpact(this, hitresult)) {
                this.onHit(hitresult);
            }
        }
        this.checkInsideBlocks();
        this.move(MoverType.SELF, new Vec3(this.getMotionVector().x(), this.getMotionVector().y(), this.getMotionVector().z()));
    }

    @Nullable
    public Vec3 findTargetPoint() {
        Vec3 pos = this.targetPoint;
        if (pos != null) {
            return pos;
        } else {
            if (this.projectileOwner != null) {
                LivingEntity target = this.projectileOwner.getTarget();
                return target == null ? null : target.position();
            }
            return null;
        }
    }

    private Direction getMoveDirection(Vec3 targetPoint) {
        Direction moveDir = this.moveDirection;
        if (moveDir == null) {
            double x = targetPoint.x - this.getX();
            double y = targetPoint.y - this.getY();
            double z = targetPoint.z - this.getZ();
            moveDir = this.calculateDirection(x, y, z);
            this.moveDirection = moveDir;
        }

        return moveDir;
    }

    public Direction calculateDirection(double x, double y, double z) {
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);
        if (absY > absX && absY > absZ) {
            return y > 0.0 ? Direction.UP : Direction.DOWN;
        } else if (absX > absZ) {
            return x > 0.0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0.0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private double axisDistance(Vec3 target, Vec3 start, Direction direction) {
        double x = target.x() - start.x();
        double y = target.y() - start.y();
        double z = target.z() - start.z();
        return x * (double) direction.getStepX() + y * (double) direction.getStepY() + z * (double) direction.getStepZ();
    }

    public int calculateMoveDelay() {
        return 2 + this.random.nextInt(14);
    }

    public float getVelocityIncrease() {
        return 0.035F - 400.0F / 30000.0F;
    }

    public float getMaxVelocity() {
        return 2.5F;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
            this.discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !entity.equals(this.projectileOwner);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity entity = result.getEntity();
        Mob owner = this.projectileOwner;
        if (entity.hurt(this.damageSources().mobAttack(owner), 4)) { //todo
            this.playSound(AetherSoundEvents.ENTITY_SLIDER_COLLIDE.get(), 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.doEnchantDamageEffects(owner, entity);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult pResult) {
        super.onHitBlock(pResult);
        this.stop();
    }

    private void stop() {
        this.moveDirection = null;
        this.moveDelay = this.calculateMoveDelay();
        this.targetPoint = null;
        this.velocity = 0.0F;
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        if (!this.level().isClientSide()) {
            this.destroy(); //todo
        }
        return true;
    }

    private void destroy() {
        this.discard();
        this.level().gameEvent(GameEvent.ENTITY_DAMAGE, this.position(), GameEvent.Context.of(this));
    }

    public Vector3f getMotionVector() {
        return this.entityData.get(ID_MOTION);
    }

    public void setMotionVector(Vector3f vector) {
        this.entityData.set(ID_MOTION, vector);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("xMotion", this.getMotionVector().x());
        tag.putFloat("yMotion", this.getMotionVector().y());
        tag.putFloat("zMotion", this.getMotionVector().z());
        if (this.projectileOwner != null) {
            tag.putInt("ProjectileOwner", this.projectileOwner.getId());
        }
        if (this.targetPoint != null) {
            tag.putDouble("xTarget", this.targetPoint.x());
            tag.putDouble("yTarget", this.targetPoint.y());
            tag.putDouble("zTarget", this.targetPoint.z());
        }
        tag.putFloat("Velocity", this.velocity);
        if (this.moveDirection != null) {
            tag.putInt("Direction", this.moveDirection.get3DDataValue());
        }
        tag.putInt("MoveDelay", this.moveDelay);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        float x = 0.0F;
        float y = 0.0F;
        float z = 0.0F;
        if (tag.contains("xMotion")) {
            x = tag.getFloat("xMotion");
        }
        if (tag.contains("yMotion")) {
            y = tag.getFloat("yMotion");
        }
        if (tag.contains("zMotion")) {
            z = tag.getFloat("zMotion");
        }
        this.setMotionVector(new Vector3f(x, y, z));
        if (tag.contains("ProjectileOwner")) {
            if (this.level().getEntity(tag.getInt("ProjectileOwner")) instanceof Mob mob) {
                this.projectileOwner = mob;
            }
        }
        double targetX = 0.0;
        double targetY = 0.0;
        double targetZ = 0.0;
        if (tag.contains("xTarget")) {
            targetX = tag.getDouble("xTarget");
        }
        if (tag.contains("yTarget")) {
            targetY = tag.getDouble("yTarget");
        }
        if (tag.contains("zTarget")) {
            targetZ = tag.getDouble("zTarget");
        }
        this.targetPoint = new Vec3(targetX, targetY, targetZ);
        if (tag.contains("Direction")) {
            this.moveDirection = Direction.from3DDataValue(tag.getInt("Direction"));
        }
        if (tag.contains("MoveDelay")) {
            this.moveDelay = tag.getInt("MoveDelay");
        }
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket pPacket) {
        super.recreateFromPacket(pPacket);
        double d0 = pPacket.getXa();
        double d1 = pPacket.getYa();
        double d2 = pPacket.getZa();
        this.setDeltaMovement(d0, d1, d2);
    }
}