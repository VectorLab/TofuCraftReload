package cn.mcmod.tofucraft.tileentity;

import java.util.List;

import cn.mcmod.tofucraft.TofuConfig;
import cn.mcmod.tofucraft.TofuMain;
import cn.mcmod.tofucraft.block.BlockLoader;
import cn.mcmod.tofucraft.block.BlockSaltFurnace;
import cn.mcmod.tofucraft.inventory.ContainerSaltFurnace;
import cn.mcmod.tofucraft.item.ItemLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.util.datafix.walkers.ItemStackDataLists;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class TileEntitySaltFurnace extends TileEntityLockable implements ITickable, ISidedInventory, IFluidHandler {
    /*
     * Comment:
     * Well this code needs a really deep reconstruction.
     * Damn...
     *
     * Program logic:
     * 1. Check if the cauldron is at proper place.
     *    - If the furnace is burning then:
     *      If there's place to place fire then place, if there's already fire then refresh, if non of these are fit then
     *      do nothing.
     * 2. Check if the cauldron has water.
     * 3. Check if there's suitable fuel.
     * 4. Consume fuel, and start a work cycle.
     * 5. Dump items to where it should be.
     * */
	
	private static boolean shouldRefresh_isValidBlock(IBlockState j) {
		Block k=j.getBlock();
		return BlockLoader.SALTFURNACE_LIT==k||BlockLoader.SALTFURNACE==k;
	}
	
    @Override
	public boolean shouldRefresh(World p_shouldRefresh_1_, BlockPos p_shouldRefresh_2_, IBlockState p_shouldRefresh_3_,
			IBlockState p_shouldRefresh_4_) {
    	if(shouldRefresh_isValidBlock(p_shouldRefresh_3_)&&shouldRefresh_isValidBlock(p_shouldRefresh_4_)) {
    		return false;
    	}
		return super.shouldRefresh(p_shouldRefresh_1_, p_shouldRefresh_2_, p_shouldRefresh_3_, p_shouldRefresh_4_);
	}

	private static final int[] SLOTS_TOP = new int[]{0, 2};
    private static final int[] SLOTS_SIDE = new int[]{0, 1, 2, 3};
    private static final int[] SLOTS_BOTTOM = new int[]{1, 3};
//    private static final FluidStack nigari = new FluidStack(BlockLoader.NIGARI_FLUID, 10);

    public FluidTank nigariTank = new FluidTank(BlockLoader.NIGARI_FLUID, 0, 120);
    net.minecraftforge.items.IItemHandler handlerTop = new net.minecraftforge.items.wrapper.SidedInvWrapper(this, net.minecraft.util.EnumFacing.UP);
    net.minecraftforge.items.IItemHandler handlerBottom = new net.minecraftforge.items.wrapper.SidedInvWrapper(this, net.minecraft.util.EnumFacing.DOWN);
    net.minecraftforge.items.IItemHandler handlerSide = new net.minecraftforge.items.wrapper.SidedInvWrapper(this, net.minecraft.util.EnumFacing.WEST);
    /**
     * The ItemStacks that hold the items currently being used in the furnace
     * 0=Fuel, 1=Salt output, 2=Glass bottle, 3=Nigari output
     */
    private NonNullList<ItemStack> furnaceItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
    /**
     * The number of ticks that the furnace will keep burning
     */
    
    private final IFurnaceBurnTime furnaceBurnTime=(TofuConfig.feToBurn>0)?(new EnergyStore(this)):(new NormalStore(this));
    
    /**
     * The number of ticks that a fresh copy of the currently-burning item would keep the furnace burning for
     */
    private int cookTime = 0;
    private int totalCookTime;
    /**
     * -1=noCauldron, 0-3=waterlevel
     */
    private int lastCauldronStatus = 0;
    private String customName;

    public static void registerFixesFurnace(DataFixer fixer) {
        fixer.registerWalker(FixTypes.BLOCK_ENTITY, new ItemStackDataLists(TileEntitySaltFurnace.class, new String[]{"Items"}));
    }

    @SideOnly(Side.CLIENT)
    public static boolean isBurning(IInventory inventory) {
        return inventory.getField(0) > 0;
    }

    /**
     * Like the old updateEntity(), except more generic.
     */
    @Override
    public void update() {

        boolean wasBurning = isBurning();
        if (isBurning()) {this.furnaceBurnTime.burnSpent();};

        int cauldron = this.getCauldronStatus();
        boolean isDirty = false;
        if (!world.isRemote) {
            if ((!this.furnaceBurnTime.burnIsRemain())&&this.canBoil(cauldron)) {
                ItemStack fuel = furnaceItemStacks.get(0);
                int a=TileEntityFurnace.getItemBurnTime(fuel);
                this.furnaceBurnTime.burnReset(a);
                if (a > 0) {
                    isDirty = true;
                    fuel.shrink(1);
                    if (fuel.getCount() == 0) {
                        furnaceItemStacks.set(0, fuel.getItem().getContainerItem(fuel));
                    }
                }
            }

            if (isBurning() && canBoil(cauldron) && cauldron >= 1) {
                cookTime++;
                if (cookTime == 200) {
                    cookTime = 0;
                    boilDown();
                    isDirty = true;
                }
            } else cookTime = 0;

            if (wasBurning != isBurning()) {
                isDirty = true;
                BlockSaltFurnace.setState(isBurning(), world, pos);
            }

            if (this.isBurning()) {
                if (cauldron == -1) {
                    this.world.setBlockState(this.pos.up(), Blocks.FIRE.getDefaultState().withProperty(BlockFire.AGE, 0));
                } else if (cauldron == -2) {
                    if (this.world.rand.nextInt(200) == 0) {
                        this.world.setBlockState(this.pos.up(2), Blocks.FIRE.getDefaultState().withProperty(BlockFire.AGE, 0));
                    }
                }
            }

            // Output nigari
            this.outputNigari();
        }

        updateCauldronStatus();
        if (isDirty) markDirty();
    }

    /**
     * Returns the number of slots in the inventory.
     */
    @Override
    public int getSizeInventory() {
        return this.furnaceItemStacks.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        return this.furnaceItemStacks.get(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        return ItemStackHelper.getAndSplit(this.furnaceItemStacks, index, count);
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        return ItemStackHelper.getAndRemove(this.furnaceItemStacks, index);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        boolean flag = stack.isItemEqual(this.furnaceItemStacks.get(index)) && ItemStack.areItemStackTagsEqual(stack, this.furnaceItemStacks.get(index));
        this.furnaceItemStacks.set(index, stack);

        if (stack.getCount() > this.getInventoryStackLimit()) {
            stack.setCount(this.getInventoryStackLimit());
        }

        if (index == 0 && !flag) {
            this.markDirty();
        }
    }

    /**
     * Returns the maximum stack size for a inventory slot. Seems to always be 64, possibly will be extended.
     */
    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        return this.world.getTileEntity(this.pos) == this && player.getDistanceSq((double) this.pos.getX() + 0.5D, (double) this.pos.getY() + 0.5D, (double) this.pos.getZ() + 0.5D) <= 64.0D;
    }

    /**
     * Don't rename this method to canInteractWith due to conflicts with Container
     */

    @Override
    public void openInventory(EntityPlayer player) {
    }

    @Override
    public void closeInventory(EntityPlayer player) {
    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack) {
        switch (i) {
            case 0:
                return TileEntityFurnace.isItemFuel(itemstack);
            case 2:
                return itemstack.isItemEqual(new ItemStack(Items.GLASS_BOTTLE, 1)) ||
                        itemstack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        }
        return false;
    }

    @Override
    public int getField(int id) {
        switch (id) {
            case 0:
                return this.furnaceBurnTime.burnGetCurrent();
            case 1:
                return this.furnaceBurnTime.burnGetMax();
            case 2:
                return this.cookTime;
            case 3:
                return this.totalCookTime;
            default:
                return 0;
        }
    }

    @Override
    public void setField(int id, int value) {
        switch (id) {
            case 0:
            	this.furnaceBurnTime.burnSetCurrent(value);
                break;
            case 1:
                this.furnaceBurnTime.burnSetMax(value);
                break;
            case 2:
                this.cookTime = value;
                break;
            case 3:
                this.totalCookTime = value;
        }
    }

    @Override
    public int getFieldCount() {
        return 4;
    }

    public void clear() {
        this.furnaceItemStacks.clear();
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.tofucraft.salt_furnace.name";
    }

    /**
     * Returns true if this thing is named
     */
    @Override
    public boolean hasCustomName() {
        return this.customName != null && !this.customName.isEmpty();
    }

    public void setCustomInventoryName(String name) {
        this.customName = name;
    }

    @Override
    public Container createContainer(InventoryPlayer playerInventory, EntityPlayer playerIn) {
        return new ContainerSaltFurnace(playerInventory, this);
    }

    @Override
    public String getGuiID() {
        return "tofucraft:saltFurnace";
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        switch (side) {
            case DOWN:
                return SLOTS_BOTTOM;
            case UP:
                return SLOTS_TOP;
            default:
                return SLOTS_SIDE;
        }
    }

    /**
     * Returns true if automation can insert the given item in the given slot from the given side.
     */
    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, EnumFacing direction) {
        return this.isItemValidForSlot(index, itemStackIn);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return index == 1 || direction == EnumFacing.DOWN;
    }

    @Override
    public void readFromNBT(NBTTagCompound par1NBTTagCompound) {
    	super.readFromNBT(par1NBTTagCompound);
        this.furnaceItemStacks = NonNullList.withSize(this.getSizeInventory(), ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(par1NBTTagCompound, this.furnaceItemStacks);
        this.cookTime = par1NBTTagCompound.getShort("CookTime");
        
        try {
        int i1=par1NBTTagCompound.getInteger("BurnTime");
        int i2=par1NBTTagCompound.getInteger("ItemBurnTime");
        this.furnaceBurnTime.nbtRead(i1, i2);
        }catch(Exception e) {
        	TofuMain.logger.catching(e);
        	TofuMain.logger.warn("TofuSaltFurnace NBT error detect, reset burn time.");
        	BlockPos a=this.getPos();
        	TofuMain.logger.warn("Pos: w={}, x={}, y={}, z={}.",this.world.provider.getDimension(),a.getX(),a.getY(),a.getZ());
        	this.furnaceBurnTime.nbtRead(0,0);
        }
        
        this.nigariTank.readFromNBT(par1NBTTagCompound.getCompoundTag("NigariTank"));

        if (par1NBTTagCompound.hasKey("CustomName")) {
            this.customName = par1NBTTagCompound.getString("CustomName");
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound par1NBTTagCompound) {
        super.writeToNBT(par1NBTTagCompound);
        par1NBTTagCompound.setInteger("BurnTime", this.furnaceBurnTime.nbtWriteCurrent());
        par1NBTTagCompound.setShort("CookTime", (short) this.cookTime);
        par1NBTTagCompound.setInteger("ItemBurnTime", this.furnaceBurnTime.nbtWriteMax());

        NBTTagCompound nigariTag = this.nigariTank.writeToNBT(new NBTTagCompound());
        par1NBTTagCompound.setTag("NigariTank", nigariTag);

        ItemStackHelper.saveAllItems(par1NBTTagCompound, this.furnaceItemStacks);

        if (this.hasCustomName()) {
            par1NBTTagCompound.setString("CustomName", this.customName);
        }

        return par1NBTTagCompound;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound nbtTagCompound = new NBTTagCompound();
        this.writeToNBT(nbtTagCompound);
        return new SPacketUpdateTileEntity(pos, this.getBlockMetadata(), nbtTagCompound);
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.getNbtCompound());
    }

    /**
     * Returns cook Progress in integer between 0 and par1
     */
    @SideOnly(Side.CLIENT)
    public int getCookProgressScaled(int par1) {
        return this.cookTime * par1 / 200;
    }

    /**
     * Returns remaining burn time in integer between 0 and par1
     */
    @SideOnly(Side.CLIENT)
    public int getBurnTimeRemainingScaled(int par1) {
        return this.furnaceBurnTime.burnGetProgressBounded(par1);
    }

    @SideOnly(Side.CLIENT)
    public FluidTank getNigariTank() {
        return this.nigariTank;
    }

    /**
     * Furnace isBurning
     */
    public boolean isBurning() {
        return this.furnaceBurnTime.burnIsRemain();
    }

    /**
     * returns true when it is possible to boil.
     */
    private boolean canBoil(int cauldronStat) {
        if (cauldronStat == 0 || cauldronStat <= -3) return false;//no water or no air for fire

        ItemStack stack = new ItemStack(ItemLoader.material, 2);
        if (this.furnaceItemStacks.get(1).isEmpty()) return true;//slot empty
        if (!this.furnaceItemStacks.get(1).isItemEqual(stack)) return false;//wrong item(s) present at salt slot.
        int result = furnaceItemStacks.get(1).getCount() + stack.getCount();
        return (result <= getInventoryStackLimit() && result <= stack.getMaxStackSize());
    }

    public void boilDown() {
        if (this.canBoil(this.getCauldronStatus())) {
            ItemStack stack = new ItemStack(ItemLoader.material, 2);

            if (this.furnaceItemStacks.get(1).isEmpty()) {
                this.furnaceItemStacks.set(1, stack.copy());
            } else if (this.furnaceItemStacks.get(1).isItemEqual(stack)) {
                //test
                int count = furnaceItemStacks.get(1).getCount() + stack.getCount();
                furnaceItemStacks.get(1).setCount(count);
            }
            this.nigariTank.fill(new FluidStack(BlockLoader.NIGARI_FLUID, 20), true);
            this.sendTankChangeToClients();

            // Decrease the water in the cauldron
            if (!world.isRemote) {
                int waterLevel = this.getCauldronWaterLevel();
                this.setCauldronWaterLevel(waterLevel - 1);
            }
        }
    }

    private int canOutputNigari() {
        ItemStack input = furnaceItemStacks.get(2);
        ItemStack output = furnaceItemStacks.get(3);

        ItemStack nigari = new ItemStack(ItemLoader.nigari);
        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);

        /*
         * If output is empty, then check if there's bottle or fluid container in the input slot.
         * Else, then check if the output is Bitter, which will restrain the input to glass bottle,
         * If none of the above is met, then check if the output is a fluid container.
         * Return value: 0->invalid, 1->bottle, 2->container(input), 3->container(output), 4->bottle(with nigari in output)
         * */

        if (nigariTank.getFluidAmount() == 0) return 0;

        if (output.isEmpty()) {
            if (input.isItemEqual(bottle)) return 1;
            if (isFluidContainer(input)) {
                IFluidHandlerItem handler = input.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                if (handler != null && handler.fill(new FluidStack(BlockLoader.NIGARI_FLUID, 1), false) > 0) return 2;
            }
        } else {
            if (output.isItemEqual(nigari) && output.getCount() < 64 && input.isItemEqual(bottle) && input.getCount() > 0)
                return 4;
            if (isFluidContainer(output)) {
                IFluidHandlerItem handler = output.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
                if (handler != null && handler.fill(new FluidStack(BlockLoader.NIGARI_FLUID, 1), false) > 0)
                    return 3;
            }
        }
        return 0;
    }

    private boolean isFluidContainer(ItemStack stack) {
        return stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    public void outputNigari() {
        ItemStack input = furnaceItemStacks.get(2);
        ItemStack output = furnaceItemStacks.get(3);

//        ItemStack nigari = new ItemStack(ItemLoader.nigari, 1);
//        ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE, 1);

        int state = canOutputNigari();
        if (state == 0) return;

        sendTankChangeToClients();

        if (state == 2) {
            output = input.copy();
            output.setCount(1);
            input.shrink(1);
            IFluidHandlerItem handler = output.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);

            int returned = handler != null ? handler.fill(nigariTank.getFluid(), true) : 0;
            nigariTank.drain(returned, true);
            furnaceItemStacks.set(3, output);
        }

        if (state == 3) {
            IFluidHandlerItem handler = output.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            int returned = handler != null ? handler.fill(nigariTank.getFluid(), true): 0;
            nigariTank.drain(returned, true);
            furnaceItemStacks.set(3, output);
        }

        if (nigariTank.getFluidAmount() < 20) return;

        if (state == 1) {
            int calculated = Math.min(nigariTank.getFluidAmount() / 20, input.getCount());
            input.shrink(calculated);
            nigariTank.drain(calculated * 20, true);
            furnaceItemStacks.set(3, new ItemStack(ItemLoader.nigari, calculated));
        }

        if (state == 4) {
            int calculated = Math.min(nigariTank.getFluidAmount() / 20, input.getCount());
            input.shrink(calculated);
            nigariTank.drain(calculated * 20, true);
            output.setCount(output.getCount() + calculated);
            furnaceItemStacks.set(3, output);
        }

    }

    @Override
	public boolean hasCapability(Capability<?> p_hasCapability_1_, EnumFacing p_hasCapability_2_) {
    	if(CapabilityEnergy.ENERGY==p_hasCapability_1_) {return true;}
		return super.hasCapability(p_hasCapability_1_, p_hasCapability_2_);
	}

	@SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Capability<T> capability, net.minecraft.util.EnumFacing facing) {
        if(capability==CapabilityEnergy.ENERGY&&TofuConfig.feToBurn>0) {
        	return (T) this.furnaceBurnTime;
        }
    	
    	if (facing != null && capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            switch(facing) {
			case DOWN:
				return (T) handlerBottom;
			case UP:
				return (T) handlerTop;
			default:
				return (T) handlerSide;
            }
        }
        return super.getCapability(capability, facing);
    }

    /*
     * === Cauldron Control ===
     */
    private void updateCauldronStatus() {
        int stat = this.getCauldronStatus();

        if (this.lastCauldronStatus != stat && this.world.getBlockState(this.pos.up()).getBlock() == Blocks.CAULDRON) {
            int metadata = this.world.getBlockState(this.pos.up()).getValue(BlockCauldron.LEVEL);
            IBlockState newMetadata = Blocks.CAULDRON.getDefaultState().withProperty(BlockCauldron.LEVEL, metadata);
            this.world.setBlockState(this.pos.up(), newMetadata, 2);
            this.lastCauldronStatus = stat;
        }
    }

    /**
     * returns water level without checking wether there is a cauldron or not.
     */
    private int getCauldronWaterLevel() {
        return world.getBlockState(this.pos.up()).getValue(BlockCauldron.LEVEL);
    }

    private void setCauldronWaterLevel(int level) {
        world.setBlockState(this.pos.up(), this.world.getBlockState(this.pos.up()).withProperty(BlockCauldron.LEVEL, level), 2);
    }

    /**
     * gets the water Level of the cauldron above this block
     *
     * @return water level. 0-3, int.
     * If there is no cauldron, returns negative value. -1, -2: must put fire one/two blck above, -3: inflammable
     */
    @SuppressWarnings("deprecation")
    public int getCauldronStatus() {
        IBlockState stateUp = this.world.getBlockState(this.pos.up());
        if (stateUp.getBlock() == Blocks.CAULDRON)
            return stateUp.getValue(BlockCauldron.LEVEL);
        else {
            Block block = stateUp.getBlock();
            if (block.isAir(stateUp, world, this.getPos().up()) || block == Blocks.FIRE) return -1;
            else if (!block.getMaterial(stateUp).getCanBurn()) return -3;
            else {
                IBlockState state2Up = this.world.getBlockState(this.pos.up(2));
                if (state2Up.getBlock().isAir(state2Up, world, this.pos.up(2))) return -2;
                else return -3;
            }
        }
    }
    /*
     * === Fluid Handler ===
     */

    @Override
    public IFluidTankProperties[] getTankProperties() {
        return this.nigariTank.getTankProperties();
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        return 0;
    }

    @Override
    public FluidStack drain(FluidStack resource, boolean doDrain) {
        if ((resource == null) || (!resource.isFluidEqual(nigariTank.getFluid()))) {
            return null;
        }

        if (!canDrain(resource.getFluid())) return null;

        return nigariTank.drain(resource.amount, doDrain);
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return nigariTank.drain(maxDrain, doDrain);
    }

    public boolean canDrain(Fluid fluid) {
        return nigariTank.getFluid().getUnlocalizedName() == fluid.getUnlocalizedName();
    }

    protected void sendTankChangeToClients() {

        if (!this.hasWorld() || this.world.isRemote) return;

        List<EntityPlayer> list = this.getWorld().playerEntities;
        for (EntityPlayer player : list) {
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).connection.sendPacket(this.getUpdatePacket());
            }
        }

    }
    
    public static interface IFurnaceBurnTime{
    	public int nbtWriteCurrent();
    	public void burnSetCurrent(int value);
		public int burnGetProgressBounded(int bound);
		public int nbtWriteMax();
    	public void nbtRead(int current,int max);
    	public void burnReset(int value);
    	public int burnGetCurrent();
    	public int burnGetMax();
    	public void burnSetMax(int value);
    	public boolean burnIsRemain();
    	public boolean burnSpent();
    }
    
    public static class NormalStore implements IFurnaceBurnTime{

		private int burnRemain=0;
		private int burnMax=0;

		public NormalStore(TileEntitySaltFurnace tileEntitySaltFurnace) {
		}
    	
		@Override
		public void burnReset(int a) {
			this.burnRemain=a;
			this.burnMax=a;
		}

		@Override
		public int burnGetCurrent() {
			return this.burnRemain;
		}

		@Override
		public boolean burnIsRemain() {
			return this.burnRemain>0;
		}

		@Override
		public boolean burnSpent() {
			if(this.burnRemain>0) {
				--this.burnRemain;
				if(this.burnRemain==0) {
					this.burnMax=0;
				}
				return true;
			}else {
				return false;
			}
		}

		@Override
		public int burnGetMax() {
			return this.burnMax;
		}

		@Override
		public int nbtWriteCurrent() {
			return this.burnRemain;
		}

		@Override
		public int burnGetProgressBounded(int bound) {
			if(this.burnRemain==0) {
				return 0;
			}
			if(this.burnMax==0) {
				return bound;
			}
			return this.burnRemain*bound/this.burnMax;
		}

		@Override
		public int nbtWriteMax() {
			return this.burnMax;
		}

		@Override
		public void nbtRead(int current, int max) {
			this.burnRemain=current;
			this.burnMax=max;
		}

		@Override
		public void burnSetMax(int value) {
			this.burnMax=value;
			if(this.burnRemain>this.burnMax) {
				this.burnRemain=this.burnMax;
			}
		}

		@Override
		public void burnSetCurrent(int value) {
			this.burnRemain=value;
		}
    	
    }
    
    public static class EnergyStore implements IEnergyStorage,IFurnaceBurnTime{
    	
  
    	private int store=0;
    	private int capacity=0;
    	
    	/* max: Short.MAX_VALUE */
    	//public static final int batteryChunk=Math.max(20000,Short.MAX_VALUE);

		public EnergyStore(TileEntitySaltFurnace tileEntitySaltFurnace) {
		}

		@Override
		public boolean canExtract() {
			return false;
		}

		@Override
		public boolean canReceive() {
			return TofuConfig.feToBurn>0;
		}

		@Override
		public int extractEnergy(int arg0, boolean arg1) {
			return 0;
		}

		@Override
		public int getEnergyStored() {
			return this.store;
		}

		@Override
		public int getMaxEnergyStored() {
			return this.capacity;
		}

		@Override
		public int receiveEnergy(final int arg0, boolean arg1) {
			int ijt=0;
			if(this.capacity>0&&this.store<this.capacity) {
				ijt=Math.min(arg0,this.capacity-this.store);
			}
			if(!arg1) {
				this.store+=ijt;
			}
			return ijt;
		}

		@Override
		public int nbtWriteCurrent() {
			return this.store;
		}

		@Override
		public int burnGetProgressBounded(int bound) {
			if(this.store==0) {
				return 0;
			}
			if(this.capacity==0) {
				return bound;
			}
			return this.store*bound/this.capacity;
		}

		@Override
		public int nbtWriteMax() {
			return this.capacity;
		}

		@Override
		public void nbtRead(int current, int max) {
			this.store=current;
			this.capacity=max;
		}

		@Override
		public void burnReset(int value) {
			this.store=this.capacity=value*TofuConfig.feToBurn;
		}

		@Override
		public int burnGetCurrent() {
			return this.store/TofuConfig.feToBurn;
		}

		@Override
		public int burnGetMax() {
			return this.capacity/TofuConfig.feToBurn;
		}

		@Override
		public void burnSetMax(int value) {
			this.capacity=value*TofuConfig.feToBurn;
		}

		@Override
		public boolean burnIsRemain() {
			return this.store>=TofuConfig.feToBurn;
		}

		@Override
		public boolean burnSpent() {
			if(this.store>=TofuConfig.feToBurn) {
				this.store-=TofuConfig.feToBurn;
				if(this.store<TofuConfig.feToBurn) {
					this.capacity=0;
				}
				return true;
			}else {
				return false;
			}
		}

		@Override
		public void burnSetCurrent(int value) {
			this.store=value*TofuConfig.feToBurn;
		}
    	
    }

}
