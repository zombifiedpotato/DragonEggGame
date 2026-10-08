package de.arvitus.dragonegggame.mixin;

import de.arvitus.dragonegggame.api.DragonEggAPI;
import de.arvitus.dragonegggame.utils.Utils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
    @Shadow
    private int age;

    public ItemEntityMixin(EntityType<?> type, Level world) {
        super(type, world);
    }

    @Shadow
    public abstract ItemStack getItem();

    /*
    // Replaced with PlayerInventory.insertStack(ItemStack) to also detect things like /give
    @Inject(
        method = "onPlayerCollision",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;increaseStat(Lnet/minecraft/stat/Stat;I)V"
        )
    )
    private void onPlayerCollision(PlayerEntity player, CallbackInfo ci, @Local ItemStack itemStack) {
        if (itemStack.isOf(Items.DRAGON_EGG)) DragonEggAPI.updatePosition(player);
    }
    */

    @Shadow
    public abstract void setUnlimitedLifetime();

    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo ci) {
        if (this.level().isClientSide()) return;

        ItemStack stack = this.getItem();
        if (!this.isRemoved() && this.age == 0 && Utils.isOrHasDragonEgg(stack)) {
            this.setGlowingTag(true);

            if (stack.is(Items.DRAGON_EGG) && Utils.isNearServerSpawn(this)) {
                this.setUnlimitedLifetime();
                this.setInvulnerable(true);
            }

            DragonEggAPI.updatePosition(this);
        }
    }
}
