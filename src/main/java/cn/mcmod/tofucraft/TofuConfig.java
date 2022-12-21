package cn.mcmod.tofucraft;

import cn.mcmod.tofucraft.util.TofuSlimeSpawnChunk;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = TofuMain.MODID)
public class TofuConfig {
    @Config.Ignore
    private final static String config = TofuMain.MODID + ".config.";

    @Config.LangKey(config + "dimension_id")
    @Config.RequiresMcRestart
    @Config.Comment("What ID number to assign to the TofuWorld dimension. Change if you are having conflicts with another mod.")
    public static int dimensionID = 56;
    
    @Config.LangKey(config + "interact_overworld")
    @Config.Comment("Allow modify of overworld.")
    public static boolean interactOverworld = true;
    
    @Config.LangKey(config + "gen_overworld")
    @Config.Comment("Allow modify of overworld.")
    public static boolean genOverworld = true;
    
    @Config.LangKey(config + "gen_nether")
    @Config.Comment("Allow modify of nether (BlockLoader.SOYBEAN_NETHER).")
    public static boolean genNether = true;

    @Config.LangKey(config + "portal_active")
    @Config.Comment("Allow protal work.")
    public static boolean portalActive = true;

    @Config.LangKey(config + "global_seed")
    @Config.Comment("Seed for tofu slime chunk.")
    public static String globalSeed = "987234911";
    
    @Config.LangKey(config + "fe_to_burn")
    @Config.RequiresWorldRestart
    @Config.Comment("FE to Burn.")
    public static int feToBurn = 20;
    
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(TofuMain.MODID)) {
            ConfigManager.sync(TofuMain.MODID, Config.Type.INSTANCE);
        }
        TofuSlimeSpawnChunk.updateSeed(globalSeed);
    }
}
