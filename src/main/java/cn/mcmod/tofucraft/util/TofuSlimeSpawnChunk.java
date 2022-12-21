package cn.mcmod.tofucraft.util;

import java.util.Random;

import cn.mcmod.tofucraft.TofuConfig;
import cn.mcmod.tofucraft.TofuMain;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

public final class TofuSlimeSpawnChunk {
	
	public static long seed=987234911L;

	public static void updateSeed(String seedString) {
		long seedParsed;
		byte seedFormat;
		if(null==seedString||seedString.isEmpty()) {
			seedParsed=(new Random()).nextLong();
			seedFormat=0;
		}else{
			try {
				if(seedString.startsWith("0x")) {
					seedParsed=Long.parseLong(seedString.substring(2),16);
					seedFormat=16;
				}else if(seedString.startsWith("0b")) {
					seedParsed=Long.parseLong(seedString.substring(2),2);
					seedFormat=2;
				}else if(seedString.startsWith("0")) {
					seedParsed=Long.parseLong(seedString.substring(1),8);
					seedFormat=8;
				}else {
					seedParsed=Long.parseLong(seedString,10);
					seedFormat=10;
				}
			}catch(NumberFormatException e) {
				seedParsed=seedString.hashCode();
				seedFormat=1;
			}
		}
		
		TofuMain.logger.info("TofuSlimeSpawnChunk seed update to value={}, radix={}, raw={}.",seedParsed,seedFormat,seedString);
		seed=seedParsed;
	}
	
	public static void loadFromSettings() {
		updateSeed(TofuConfig.globalSeed);
	}
	
    public static boolean isSpawnChunk(World world, double x, double z) {
        BlockPos blockpos = new BlockPos(MathHelper.floor(x), 0, MathHelper.floor(z));
        Chunk var1 = world.getChunkFromBlockCoords(blockpos);
        return var1.getRandomWithSeed(seed).nextInt(8) == 0;
    }

}
