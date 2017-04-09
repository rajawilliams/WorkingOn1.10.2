package buildcraft.silicon.tile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import buildcraft.api.mj.*;
import buildcraft.api.properties.BuildCraftProperties;
import buildcraft.api.tiles.IDebuggable;

import buildcraft.lib.client.render.DetatchedRenderer.IDetachedRenderer;
import buildcraft.lib.misc.LocaleUtil;
import buildcraft.lib.misc.MessageUtil;
import buildcraft.lib.misc.NBTUtilBC;
import buildcraft.lib.misc.VolumeUtil;
import buildcraft.lib.misc.data.Box;
import buildcraft.lib.mj.MjBatteryReciver;
import buildcraft.lib.net.PacketBufferBC;
import buildcraft.lib.tile.TileBC_Neptune;
import buildcraft.silicon.BCSiliconBlocks;
import buildcraft.silicon.client.render.AdvDebuggerLaser;

public class TileLaser extends TileBC_Neptune implements ITickable, IDebuggable {
    private int ticks = 0;
    private BlockPos targetPos;
    private long[] averageValues = new long[100];
    private final MjBattery battery;
    private final MjCapabilityHelper mjCapHelper;

    public Vec3d laserPos;

    public TileLaser() {
        super();
        battery = new MjBattery(1024 * MjAPI.MJ);
        mjCapHelper = new MjCapabilityHelper(new MjBatteryReciver(battery));
    }

    private void findTarget() {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != BCSiliconBlocks.laser) {
            return;
        }
        EnumFacing face = state.getValue(BuildCraftProperties.BLOCK_FACING_6);

        List<BlockPos> possible = new ArrayList<>();

        VolumeUtil.iterateCone(world, pos, face, 6, true, (w, s, p, visible) -> {
            if (!visible) {
                return;
            }
            IBlockState stateAt = world.getBlockState(p);
            if (stateAt.getBlock() instanceof ILaserTargetBlock) {
                TileEntity tileAt = world.getTileEntity(p);
                if (tileAt instanceof ILaserTarget) {
                    ILaserTarget targetAt = (ILaserTarget) tileAt;
                    if (targetAt.requiresLaserPower()) {
                        possible.add(p);
                    }
                }
            }
        });

        if (possible.isEmpty()) {
            targetPos = null;
            return;
        }

        targetPos = possible.get(world.rand.nextInt(possible.size()));
    }

    private ILaserTarget getTarget() {
        if (targetPos != null) {
            TileEntity tile = world.getTileEntity(targetPos);
            if (tile instanceof ILaserTarget) {
                ILaserTarget target = (ILaserTarget) tile;
                return !target.isInvalidTarget() && target.requiresLaserPower() ? target : null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void updateLaser() {
        if (getTarget() != null) {
            laserPos = new Vec3d(targetPos).addVector((5 + world.rand.nextInt(6) + 0.5) / 16D, 9 / 16D, (5 + world.rand.nextInt(6) + 0.5) / 16D);
        } else {
            laserPos = null;
        }
    }

    private void addAverageValue(long power) {
        System.arraycopy(averageValues, 1, averageValues, 0, averageValues.length - 1);
        averageValues[averageValues.length - 1] = power;
    }

    public long getAverage() {
        return LongStream.of(averageValues).sum() / averageValues.length;
    }

    public long getMaxPowerPerTick() {
        return 4 * MjAPI.MJ;
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }

        ticks++;

        if (getTarget() == null) {
            targetPos = null;
        }

        if (ticks % (10 + world.rand.nextInt(20)) == 0 || getTarget() == null) {
            findTarget();
        }

        if (ticks % (5 + world.rand.nextInt(10)) == 0 || getTarget() == null) {
            updateLaser();
        }

        ILaserTarget target = getTarget();
        if (target != null) {
            long max = getMaxPowerPerTick();
            max *= battery.getStored() + max;
            max /= battery.getCapacity() / 2;
            max = Math.min(max, getMaxPowerPerTick());
            long power = battery.extractPower(0, max);
            addAverageValue(power);
            target.receiveLaserPower(power);
        } else {
            averageValues = new long[averageValues.length];
        }

        sendNetworkUpdate(NET_RENDER_DATA);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("mj_battery", battery.serializeNBT());
        if (laserPos != null) {
            nbt.setTag("laser_pos", NBTUtilBC.writeVec3d(laserPos));
        }
        if (targetPos != null) {
            nbt.setTag("target_pos", NBTUtilBC.writeBlockPos(targetPos));
        }
        for (int i = 0; i < averageValues.length; i++) {
            nbt.setLong("average_value_" + i, averageValues[i]);
        }
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        battery.deserializeNBT(nbt.getCompoundTag("mj_battery"));
        targetPos = NBTUtilBC.readBlockPos(nbt.getTag("target_pos"));
        laserPos = NBTUtilBC.readVec3d(nbt.getTag("laser_pos"));
        for (int i = 0; i < averageValues.length; i++) {
            averageValues[i] = nbt.getLong("average_value_" + i);
        }
    }

    @Override
    public void writePayload(int id, PacketBufferBC buffer, Side side) {
        super.writePayload(id, buffer, side);
        if (id == NET_RENDER_DATA) {
            battery.writeToBuffer(buffer);
            buffer.writeBoolean(targetPos != null);
            if (targetPos != null) {
                buffer.writeBlockPos(targetPos);
            }
            buffer.writeBoolean(laserPos != null);
            if (laserPos != null) {
                MessageUtil.writeVec3d(buffer, laserPos);
            }
            buffer.writeLongArray(averageValues);
        }
    }

    @Override
    public void readPayload(int id, PacketBufferBC buffer, Side side, MessageContext ctx) throws IOException {
        super.readPayload(id, buffer, side, ctx);
        if (id == NET_RENDER_DATA) {
            battery.readFromBuffer(buffer);
            if (buffer.readBoolean()) {
                targetPos = buffer.readBlockPos();
            } else {
                targetPos = null;
            }
            if (buffer.readBoolean()) {
                laserPos = MessageUtil.readVec3d(buffer);
            } else {
                laserPos = null;
            }
            averageValues = buffer.readLongArray(new long[averageValues.length]);
        }
    }

    @Override
    public void getDebugInfo(List<String> left, List<String> right, EnumFacing side) {
        left.add("");
        left.add("battery = " + battery.getDebugString());
        left.add("target = " + targetPos);
        left.add("laser = " + laserPos);
        left.add("average = " + LocaleUtil.localizeMjFlow(getAverage()));
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        T cap = mjCapHelper.getCapability(capability, facing);
        if (cap != null) {
            return cap;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new Box(this).extendToEncompass(targetPos).getBoundingBox();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IDetachedRenderer getDebugRenderer() {
        return AdvDebuggerLaser.getForTile(this);
    }
}
