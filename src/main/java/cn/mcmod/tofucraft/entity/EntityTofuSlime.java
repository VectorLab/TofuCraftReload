package cn.mcmod.tofucraft.entity;

import javax.annotation.Nullable;

import cn.mcmod.tofucraft.TofuConfig;
import cn.mcmod.tofucraft.TofuMain;
import cn.mcmod.tofucraft.util.TofuLootTables;
import cn.mcmod.tofucraft.util.TofuSlimeSpawnChunk;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.DimensionType;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootTableList;

public class EntityTofuSlime extends EntitySlime {
    public EntityTofuSlime(World worldIn) {
        super(worldIn);
    }

    protected EntityTofuSlime createInstance() {
        return new EntityTofuSlime(this.world);
    }

    @Override
    public boolean getCanSpawnHere() {

        if (this.world.getDifficulty() != EnumDifficulty.PEACEFUL) {

            if (this.dimension == TofuMain.TOFU_DIMENSION.getId() && this.rand.nextInt((int) (this.world.getLightBrightness(getPosition()) * 10 + 30)) == 0
                    && this.world.getEntitiesWithinAABBExcludingEntity(this, this.getEntityBoundingBox().expand(48.0D, 20.0D, 48.0D)).size() == 0) {

                //It does not spawn when there is a light like a torch (spawns when there is no light like a torch even if there is light in the sky)
                return this.world.getLightFor(EnumSkyBlock.BLOCK, getPosition()) < 2 + this.rand.nextInt(6) && this.baseGetCanSpawnHere();
            }

            if (TofuConfig.interactOverworld 
            		&& this.posY < 40.0D
            		&& this.dimension == DimensionType.OVERWORLD.getId()
            		&& this.rand.nextBoolean()
                    && TofuSlimeSpawnChunk.isSpawnChunk(this.world, this.posX, this.posZ)
                    && (this.world.getLightFromNeighbors(new BlockPos(MathHelper.floor(this.posX), MathHelper.floor(this.posY), MathHelper.floor(this.posZ))))
                    <= this.rand.nextInt(10))
                return this.baseGetCanSpawnHere();
        }
        return false;
    }
    
    /**
     * Must be the same as EntityLiving.getCanSpawnHere!
     */
    public boolean baseGetCanSpawnHere() {
        IBlockState iblockstate = this.world.getBlockState((new BlockPos(this)).down());
        return iblockstate.canEntitySpawn(this);
    }


    @Nullable
    @Override
    protected ResourceLocation getLootTable()
    {
        return this.getSlimeSize() == 1 ? TofuLootTables.tofuslime : LootTableList.EMPTY;
    }
    /**
     * Returns the name of a particle effect that may be randomly created by EntitySlime.onUpdate()
     */
    @Override
    protected EnumParticleTypes getParticleType() {
        return EnumParticleTypes.SNOWBALL;
    }
}
