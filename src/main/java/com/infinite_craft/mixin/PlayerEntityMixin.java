package com.infinite_craft.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.infinite_craft.ability.DashAbility;
import com.infinite_craft.ability.ThrustAbility;
import com.infinite_craft.element.DiscoveringPlayerData;
import com.infinite_craft.element.PlayerEntityExt;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements PlayerEntityExt, DashAbility.Dasher {
    @Unique
    private DiscoveringPlayerData infiniteCraft$discoveringData;

    @Unique
    private final DashAbility.State infiniteCraft$dashState = new DashAbility.State();

    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(CallbackInfo ci) {
        this.infiniteCraft$discoveringData = new DiscoveringPlayerData();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void infiniteCraft$tickAbilities(CallbackInfo ci) {
        ThrustAbility.tick((PlayerEntity) (Object) this);
        DashAbility.tick((PlayerEntity) (Object) this);
    }

    @Inject(method = "writeCustomData", at = @At("HEAD"))
    private void writeDiscoveringData(WriteView view, CallbackInfo ci) {
        infiniteCraft$discoveringData.writeTo(view);
    }

    @Inject(method = "readCustomData", at = @At("HEAD"))
    private void readDiscoveringData(ReadView view, CallbackInfo ci) {
        infiniteCraft$discoveringData.readFrom(view);
    }


    @Override
    public DiscoveringPlayerData getDiscoveringData() {
        return infiniteCraft$discoveringData;
    }

    @Override
    public DashAbility.State infiniteCraft$dashState() {
        return infiniteCraft$dashState;
    }
}
