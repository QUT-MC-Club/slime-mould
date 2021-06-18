package xyz.nucleoid.slime_mould.game.map;

import net.minecraft.block.BlockState;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xyz.nucleoid.plasmid.game.GameOpenException;
import xyz.nucleoid.plasmid.map.template.MapTemplate;
import xyz.nucleoid.plasmid.map.template.MapTemplateMetadata;
import xyz.nucleoid.plasmid.map.template.MapTemplateSerializer;
import xyz.nucleoid.plasmid.map.template.TemplateRegion;
import xyz.nucleoid.plasmid.util.BlockBounds;

import java.io.IOException;

public final class SlimeMouldMapBuilder {
    private final SlimeMouldMapConfig config;

    public SlimeMouldMapBuilder(SlimeMouldMapConfig config) {
        this.config = config;
    }

    public SlimeMouldMap build() {
        MapTemplate template;
        try {
            template = MapTemplateSerializer.INSTANCE.loadFromResource(this.config.template);
        } catch (IOException e) {
            throw new GameOpenException(new LiteralText("Failed to load map template"), e);
        }

        MapTemplateMetadata metadata = template.getMetadata();

        SlimeMouldPlate plate = this.buildPlate(template, metadata);
        return new SlimeMouldMap(template, plate);
    }

    private SlimeMouldPlate buildPlate(MapTemplate template, MapTemplateMetadata metadata) {
        TemplateRegion plate = metadata.getFirstRegion("plate");
        if (plate == null) {
            throw new GameOpenException(new LiteralText("Missing plate region!"));
        }

        int radius = this.computePlateRadius(template, plate.getBounds());
        return new SlimeMouldPlate(plate.getBounds(), radius);
    }

    private int computePlateRadius(MapTemplate template, BlockBounds plateBounds) {
        BlockPos plateCenter = new BlockPos(plateBounds.getCenter());
        BlockPos plateSize = plateBounds.getSize();
        int plateRadius = Math.min(plateSize.getX(), plateSize.getZ()) / 2;

        BlockPos.Mutable surfacePos = new BlockPos.Mutable();
        BlockPos.Mutable abovePos = new BlockPos.Mutable();

        for (int i = 0; i < 4; i++) {
            Direction direction = Direction.fromHorizontal(i);
            surfacePos.set(plateCenter);

            for (int x = 0; x <= plateRadius; x++) {
                BlockState surface = template.getBlockState(surfacePos);
                BlockState above = template.getBlockState(abovePos.set(surfacePos, Direction.UP));

                if (SlimeMouldPlate.testSurface(surface.getBlock()).isOnPlate() || !above.isAir()) {
                    plateRadius = x;
                    break;
                }

                surfacePos.move(direction);
            }
        }

        return plateRadius;
    }
}
