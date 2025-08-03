package com.github.parkusisafk.parkusdaklient;

import com.github.parkusisafk.parkusdaklient.command.CommandMonitorWhitelist;
import com.github.parkusisafk.parkusdaklient.command.CommandOpenMenu;
import com.github.parkusisafk.parkusdaklient.command.CommandRemoveMonitorWhitelist;
import com.github.parkusisafk.parkusdaklient.handlers.BlockBreakingHandler;
import com.github.parkusisafk.parkusdaklient.handlers.ClientTickDispatcher;
import com.github.parkusisafk.parkusdaklient.handlers.HighlightRenderHandler;
import com.github.parkusisafk.parkusdaklient.handlers.MoveForwardHandler;
import com.github.parkusisafk.parkusdaklient.macro.MacroCheckDetector;
import com.github.parkusisafk.parkusdaklient.render.QuicksandFontRenderer;
import com.github.parkusisafk.parkusdaklient.tasks.TaskManager;
import com.github.parkusisafk.parkusdaklient.util.GuiOpener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = SkyblockMod.MODID, name = "ParkusDaKlient", version = SkyblockMod.VERSION)
public class SkyblockMod {
    public static MoveForwardHandler moveForwardHandler;
    public static final String MODID = "parkusdaklient";
    public static final String VERSION = SkyblockModVersion.VERSION;
    public static HighlightRenderHandler highlightRenderHandler;
    public static BlockBreakingHandler blockBreakingHandler;
    public static TaskManager taskManager;
    public static ClientTickDispatcher clientTickDispatcher;    // ✅ THIS is how you use @Mod.Instance:
    public static QuicksandFontRenderer QUICKSAND_14;

    @Mod.Instance
    public static SkyblockMod instance;

    @EventHandler
    public void init(FMLInitializationEvent event) {

        // Register GUI handler
        NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());
        MinecraftForge.EVENT_BUS.register(new GuiOpener());
        MinecraftForge.EVENT_BUS.register(BlockBreakingHandler.INSTANCE);
        moveForwardHandler = new MoveForwardHandler(); // constructor handles registration
        blockBreakingHandler = new BlockBreakingHandler(); // self-registers
        taskManager          = new TaskManager();
        clientTickDispatcher = new ClientTickDispatcher(taskManager, blockBreakingHandler);
        highlightRenderHandler = new HighlightRenderHandler(taskManager);
        MinecraftForge.EVENT_BUS.register(MacroCheckDetector.INSTANCE);

        try {
            // load from assets: assets/parkusdaklient/fonts/Quicksand-Regular.ttf
            QUICKSAND_14 = QuicksandFontRenderer.loadFromAssets(
                    "parkusdaklient", "fonts/Quicksand-Regular.ttf", 14f, true);
        } catch (Exception ex) {
            // Fallback: will remain null; GUI should handle it
            ex.printStackTrace();
        }
    }

    @EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        // Register slash command
        event.registerServerCommand(new CommandOpenMenu());
        event.registerServerCommand(new CommandMonitorWhitelist());
        event.registerServerCommand(new CommandRemoveMonitorWhitelist());

    }
}
