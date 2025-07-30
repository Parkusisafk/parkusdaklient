package com.github.parkusisafk.parkusdaklient.mixin;

import com.github.parkusisafk.parkusdaklient.util.GuiOpener;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityPlayerSP.class)
public class MixinEntityPlayerSP {
    static {
        System.out.println("[Mixin] MixinEntityPlayerSP loaded!");
    }

    @Inject(method = "func_71165_d", at = @At("HEAD"), cancellable = true, remap = false)
    private void onSendChatMessage(String msg, CallbackInfo ci) {
        if (msg.equalsIgnoreCase(".mymenu")) {
            System.out.println(".mymenu is called by player");
            GuiOpener.openGuiNextTick();
            System.out.println(".mymenu is called by player!!!");
             ci.cancel(); // Don't send to server */
        }
    }
}