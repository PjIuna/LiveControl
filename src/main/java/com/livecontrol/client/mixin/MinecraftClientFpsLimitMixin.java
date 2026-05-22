package com.livecontrol.client.mixin;

import com.livecontrol.client.LiveControlClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientFpsLimitMixin {
    @Shadow
    private boolean windowFocused;

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void livecontrol$keepFullFpsWhenUnfocusedOrAfk(CallbackInfoReturnable<Integer> cir) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (!windowFocused || LiveControlClient.shouldKeepFullFps()) {
            cir.setReturnValue(client.options.getMaxFps().getValue());
        }
    }
}
