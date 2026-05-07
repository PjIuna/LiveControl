package com.livecontrol.client.mixin;

import com.livecontrol.client.LiveControlConfigScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void livecontrol$addConfigButton(CallbackInfo ci) {
        addDrawableChild(ButtonWidget.builder(Text.literal("LiveControl"), button ->
                        MinecraftClient.getInstance().setScreen(new LiveControlConfigScreen(this)))
                .dimensions(this.width - 108, this.height - 26, 100, 20)
                .build());
    }
}
