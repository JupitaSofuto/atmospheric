package com.teamabnormals.atmospheric.common.block;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.teamabnormals.atmospheric.common.block.state.properties.DragonRootsStage;
import com.teamabnormals.atmospheric.common.block.state.properties.DragonRootsType;
import com.teamabnormals.atmospheric.common.entity.projectile.DragonFruit;
import com.teamabnormals.atmospheric.core.registry.AtmosphericEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;

import java.util.Map;

public class DragonRootsBlock extends BushBlock implements BonemealableBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<DragonRootsType> TYPE = EnumProperty.create("type", DragonRootsType.class);
	public static final EnumProperty<DragonRootsStage> STAGE = EnumProperty.create("stage", DragonRootsStage.class);

	private static final Map<Direction, VoxelShape> SHAPES = Maps.newEnumMap(ImmutableMap.of(
			Direction.NORTH, Block.box(0.0D, 6.0D, 5.0D, 16.0D, 14.0D, 16.0D),
			Direction.SOUTH, Block.box(0.0D, 6.0D, 0.0D, 16.0D, 14.0D, 11.0D),
			Direction.WEST, Block.box(5.0D, 6.0D, 0.0D, 16.0D, 14.0D, 16.0D),
			Direction.EAST, Block.box(0.0D, 6.0D, 0.0D, 11.0D, 14.0D, 16.0D)));

	public DragonRootsBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, DragonRootsType.DOUBLE).setValue(STAGE, DragonRootsStage.NONE));
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter worldIn, BlockPos pos) {
		return true;
	}

	@Override
	public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		Direction direction = state.getValue(FACING);
		BlockPos offsetPos = pos.relative(direction.getOpposite());
		return level.getBlockState(offsetPos).isFaceSturdy(level, offsetPos, direction);
	}

	public boolean hasFruit(BlockState state) {
		return state.getValue(STAGE) != DragonRootsStage.NONE;
	}

	public boolean isFlowering(BlockState state) {
		return state.getValue(STAGE) == DragonRootsStage.FLOWERING || state.getValue(STAGE) == DragonRootsStage.FLOWERING_ENDER;
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult result) {

		if (!hasFruit(state) && player.getItemInHand(hand).is(Items.BONE_MEAL)) {
			return InteractionResult.PASS;
		} else if (hasFruit(state)) {

			level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
			BlockState newState = state.setValue(STAGE, DragonRootsStage.NONE);
			level.setBlock(pos, newState, 2);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, newState));

			DragonRootsType type = state.getValue(TYPE);
			Direction dir = state.getValue(FACING);

			if (type == DragonRootsType.TOP || type == DragonRootsType.DOUBLE) {
				this.addDragonFruitWithOffset(state, level, pos, dir == Direction.NORTH || dir == Direction.EAST ? 0.25F : 0.75F, 0.75F, dir.getAxisDirection() == AxisDirection.NEGATIVE ? 0.75F : 0.25F);
			}

			if (type == DragonRootsType.BOTTOM || type == DragonRootsType.DOUBLE) {
				this.addDragonFruitWithOffset(state, level, pos, dir.getAxisDirection() == AxisDirection.NEGATIVE ? 0.75F : 0.25F, 0.25F, dir == Direction.NORTH || dir == Direction.EAST ? 0.75F : 0.25F);
			}


			return InteractionResult.sidedSuccess(level.isClientSide);
		} else {
			return super.use(state, level, pos, player, hand, result);
		}
	}

	public void addDragonFruitWithOffset(BlockState state, Level level, BlockPos pos, float x, float y, float z) {
		DragonRootsStage stage = state.getValue(STAGE);

		DragonFruit dragonFruit = AtmosphericEntityTypes.DRAGON_FRUIT.get().create(level);
		dragonFruit.setEnder(stage == DragonRootsStage.ENDER || stage == DragonRootsStage.FLOWERING_ENDER);
		dragonFruit.setFlowering(stage == DragonRootsStage.FLOWERING || stage == DragonRootsStage.FLOWERING_ENDER);
		dragonFruit.setPos(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
		dragonFruit.setRollingDirection(state.getValue(FACING));
		dragonFruit.setYRot(state.getValue(FACING).toYRot());
		level.addFreshEntity(dragonFruit);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, TYPE, STAGE);
	}

	@Override
	public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
		return !context.isSecondaryUseActive() && context.getItemInHand().getItem() == this.asItem() && state.getValue(TYPE) != DragonRootsType.DOUBLE || super.canBeReplaced(state, context);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		Level level = context.getLevel();
		Direction direction = context.getClickedFace();
		if (direction.getAxis().isVertical())
			direction = context.getHorizontalDirection().getOpposite();

		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);

		if (state.is(this) && state.getValue(TYPE) != DragonRootsType.DOUBLE) {
			return state.setValue(TYPE, DragonRootsType.DOUBLE);
		}

		return this.defaultBlockState().setValue(FACING, direction).setValue(TYPE, context.getClickLocation().y - (double) pos.getY() > 0.5D ? DragonRootsType.TOP : DragonRootsType.BOTTOM);
	}

	@Override
	public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!hasFruit(state) && level.getRawBrightness(pos, 0) >= 12 && ForgeHooks.onCropsGrowPre(level, pos, state, random.nextInt(10) == 0)) {
			BlockState newState = state.setValue(STAGE, getStageForLevel(level, false));
			level.setBlock(pos, newState, 2);
			level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(newState));
			ForgeHooks.onCropsGrowPost(level, pos, state);
		} else if (hasFruit(state)) {
			boolean floweringConditions = level.getRawBrightness(pos, 0) <= 3 || (level.getRawBrightness(pos, 15) <= 3 && level.isNight());
			if (isFlowering(state) != floweringConditions) {
				BlockState newState = state.setValue(STAGE, getStageForLevel(level, floweringConditions));
				level.setBlock(pos, newState, 2);
				level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(newState));
			}
		}
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	public boolean isValidBonemealTarget(BlockGetter worldIn, BlockPos pos, BlockState state, boolean isClient) {
		return !hasFruit(state);
	}

	@Override
	public boolean isBonemealSuccess(Level world, RandomSource random, BlockPos blockPos, BlockState blockState) {
		return world.random.nextFloat() < 0.15F;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
		level.setBlockAndUpdate(pos, state.setValue(STAGE, getStageForLevel(level, false)));
	}

	public DragonRootsStage getStageForLevel(ServerLevel level, boolean flowering) {
		return level.dimensionTypeId().equals(BuiltinDimensionTypes.END) ? (flowering ? DragonRootsStage.FLOWERING_ENDER : DragonRootsStage.ENDER) : (flowering ? DragonRootsStage.FLOWERING : DragonRootsStage.FRUIT);
	}
}
