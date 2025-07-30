package com.github.parkusisafk.parkusdaklient;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

@IFMLLoadingPlugin.Name("SkyblockMixinLoader")
@IFMLLoadingPlugin.MCVersion("1.8.9")
public class MixinLoader implements IFMLLoadingPlugin {

    public MixinLoader() {
        System.out.println("[SkyblockMod] INITIALISING MIXINS!!!");
        MixinBootstrap.init();
        Mixins.addConfiguration("mixins.skyblockmod.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
