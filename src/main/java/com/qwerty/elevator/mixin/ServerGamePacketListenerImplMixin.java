package com.qwerty.elevator.mixin;

import com.qwerty.elevator.event.RiderTracker;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players standing in a moving car are not "floating": stop the anti-fly kick. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true)
    private void qwertysElevator$riding(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (RiderTracker.isRiding(entity)) cir.setReturnValue(false);
    }
}
