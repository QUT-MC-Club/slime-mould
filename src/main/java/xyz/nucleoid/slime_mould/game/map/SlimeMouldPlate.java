package xyz.nucleoid.slime_mould.game.map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.StainedGlassBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.nucleoid.plasmid.util.BlockBounds;
import xyz.nucleoid.plasmid.util.ColoredBlocks;

import java.util.Random;

public final class SlimeMouldPlate {
    public final BlockBounds bounds;
    public final int radius;

    public SlimeMouldPlate(BlockBounds bounds, int radius) {
        this.bounds = bounds;
        this.radius = radius;
    }

    public static BlockState getMouldBlock(DyeColor color) {
        return ColoredBlocks.glass(color).getDefaultState();
    }

    public BlockPos getRandomSurfacePos(Random random) {
        BlockPos min = this.bounds.getMin();
        BlockPos max = this.bounds.getMax();

        return new BlockPos(
                min.getX() + random.nextInt(max.getX() - min.getX() + 1),
                max.getY(),
                min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1)
        );
    }

    public Surface testSurface(ServerWorld world, BlockPos pos) {
        if (this.bounds.contains(pos) && world.isAir(pos.up())) {
            BlockState state = world.getBlockState(pos);
            return testSurface(state.getBlock());
        }
        return Surface.NONE;
    }

    public BlockPos getSpawnPos(double theta, double distance) {
        Vec3d plateCenter = this.bounds.getCenterTop();
        int plateY = this.bounds.getMax().getY();

        double spawnX = plateCenter.x + Math.cos(theta) * distance;
        double spawnZ = plateCenter.z - Math.sin(theta) * distance;

        return new BlockPos(spawnX, plateY, spawnZ);
    }

    public static Surface testSurface(Block block) {
        if (block == Blocks.WHITE_STAINED_GLASS) {
            return Surface.STERILE;
        } else if (block instanceof StainedGlassBlock) {
            return Surface.MOULD;
        } else {
            return Surface.NONE;
        }
    }

    public enum Surface {
        NONE,
        STERILE,
        MOULD;

        public boolean isOnPlate() {
            return this != NONE;
        }

        public boolean isSterile() {
            return this == STERILE;
        }

        public boolean isMould() {
            return this == MOULD;
        }
    }
}
