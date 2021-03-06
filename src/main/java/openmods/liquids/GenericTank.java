package openmods.liquids;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import openmods.utils.BlockUtils;
import openmods.utils.CollectionUtils;
import openmods.utils.CompatibilityUtils;

public class GenericTank extends FluidTank {

	private List<EnumFacing> surroundingTanks = Lists.newArrayList();
	private final IFluidFilter filter;

	@FunctionalInterface
	public interface IFluidFilter {
		public boolean canAcceptFluid(FluidStack stack);
	}

	private static final IFluidFilter NO_RESTRICTIONS = stack -> true;

	private static IFluidFilter filter(final FluidStack... acceptableFluids) {
		if (acceptableFluids.length == 0) return NO_RESTRICTIONS;

		return stack -> {
			for (FluidStack acceptableFluid : acceptableFluids)
				if (acceptableFluid.isFluidEqual(stack)) return true;

			return false;
		};
	}

	public GenericTank(int capacity) {
		super(capacity);
		this.filter = NO_RESTRICTIONS;
	}

	public GenericTank(int capacity, FluidStack... acceptableFluids) {
		super(capacity);
		this.filter = filter(acceptableFluids);
	}

	public GenericTank(int capacity, Fluid... acceptableFluids) {
		super(capacity);
		this.filter = filter(CollectionUtils.transform(FluidStack.class, acceptableFluids, input -> new FluidStack(input, 0)));
	}

	private static boolean isNeighbourTank(World world, BlockPos coord, EnumFacing dir) {
		TileEntity tile = BlockUtils.getTileInDirectionSafe(world, coord, dir);
		return CompatibilityUtils.isFluidHandler(tile, dir.getOpposite());
	}

	private static Set<EnumFacing> getSurroundingTanks(World world, BlockPos coord) {
		final Set<EnumFacing> result = EnumSet.noneOf(EnumFacing.class);

		for (EnumFacing dir : EnumFacing.VALUES)
			if (isNeighbourTank(world, coord, dir)) result.add(dir);

		return result;
	}

	public int getSpace() {
		return getCapacity() - getFluidAmount();
	}

	@Override
	public boolean canFillFluidType(FluidStack fluid) {
		return fluid != null && filter.canAcceptFluid(fluid);
	}

	public void updateNeighbours(World world, BlockPos coord, Set<EnumFacing> sides) {
		this.surroundingTanks = Lists.newArrayList(Sets.difference(getSurroundingTanks(world, coord), sides));
	}

	public void updateNeighbours(World world, BlockPos coord) {
		this.surroundingTanks = Lists.newArrayList(getSurroundingTanks(world, coord));
	}

	private static int tryFillNeighbour(FluidStack drainedFluid, EnumFacing side, TileEntity otherTank) {
		final FluidStack toFill = drainedFluid.copy();
		final EnumFacing fillSide = side.getOpposite();

		final IFluidHandler fluidHandler = CompatibilityUtils.getFluidHandler(otherTank, fillSide);
		return fluidHandler != null? fluidHandler.fill(toFill, true) : 0;
	}

	public void distributeToSides(int amount, World world, BlockPos coord, Set<EnumFacing> allowedSides) {
		if (world == null) return;

		if (getFluidAmount() <= 0) return;

		if (surroundingTanks.isEmpty()) return;

		final List<EnumFacing> sides = Lists.newArrayList(surroundingTanks);

		if (allowedSides != null) {
			sides.retainAll(allowedSides);
			if (sides.isEmpty()) return;
		}

		FluidStack drainedFluid = drain(amount, false);

		if (drainedFluid != null && drainedFluid.amount > 0) {
			int startingAmount = drainedFluid.amount;
			Collections.shuffle(sides);

			for (EnumFacing side : surroundingTanks) {
				if (drainedFluid.amount <= 0) break;

				TileEntity otherTank = BlockUtils.getTileInDirection(world, coord, side);
				if (otherTank != null) drainedFluid.amount -= tryFillNeighbour(drainedFluid, side, otherTank);
			}

			// return any remainder
			int distributed = startingAmount - drainedFluid.amount;
			if (distributed > 0) drain(distributed, true);
		}
	}

	public void fillFromSides(int maxAmount, World world, BlockPos coord) {
		fillFromSides(maxAmount, world, coord, null);
	}

	public void fillFromSides(int maxAmount, World world, BlockPos coord, Set<EnumFacing> allowedSides) {
		if (world == null) return;

		int toDrain = Math.min(maxAmount, getSpace());
		if (toDrain <= 0) return;

		if (surroundingTanks.isEmpty()) return;

		final List<EnumFacing> sides = Lists.newArrayList(surroundingTanks);

		if (allowedSides != null) {
			sides.retainAll(allowedSides);
			if (sides.isEmpty()) return;
		}

		Collections.shuffle(sides);
		for (EnumFacing side : sides) {
			if (toDrain <= 0) break;
			toDrain -= fillInternal(world, coord, side, toDrain);
		}
	}

	public int fillFromSide(World world, BlockPos coord, EnumFacing side) {
		int maxDrain = getSpace();
		if (maxDrain <= 0) return 0;

		return fillInternal(world, coord, side, maxDrain);
	}

	public int fillFromSide(int maxDrain, World world, BlockPos coord, EnumFacing side) {
		maxDrain = Math.min(maxDrain, getSpace());
		if (maxDrain <= 0) return 0;

		return fillInternal(world, coord, side, maxDrain);
	}

	private int fillInternal(World world, BlockPos coord, EnumFacing side, int maxDrain) {
		int drain = 0;
		final TileEntity otherTank = BlockUtils.getTileInDirection(world, coord, side);

		final EnumFacing drainSide = side.getOpposite();
		IFluidHandler handler = CompatibilityUtils.getFluidHandler(otherTank, drainSide);

		if (handler != null) {
			final IFluidTankProperties[] infos = handler.getTankProperties();

			if (infos == null) return 0;

			for (IFluidTankProperties info : infos) {
				if (filter.canAcceptFluid(info.getContents())) {
					final FluidStack drained = handler.drain(maxDrain, true);

					if (drained != null) {
						fill(drained, true);
						drain += drained.amount;
						maxDrain -= drained.amount;
						if (maxDrain <= 0) break;
					}
				}
			}
		}

		return drain;
	}

}
