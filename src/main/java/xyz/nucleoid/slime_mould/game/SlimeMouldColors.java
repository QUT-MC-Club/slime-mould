package xyz.nucleoid.slime_mould.game;

import com.google.common.collect.Lists;
import net.minecraft.util.DyeColor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SlimeMouldColors {
    public static final DyeColor[] COLORS = Arrays.stream(DyeColor.values())
            .filter(color -> color != DyeColor.WHITE && color != DyeColor.BLACK)
            .toArray(DyeColor[]::new);

    public static List<DyeColor> shuffledColors(Random random) {
        List<DyeColor> colors = Lists.newArrayList(COLORS);
        Collections.shuffle(colors, random);
        return colors;
    }
}
