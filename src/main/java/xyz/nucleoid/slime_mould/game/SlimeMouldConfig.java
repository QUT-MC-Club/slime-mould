package xyz.nucleoid.slime_mould.game;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import xyz.nucleoid.plasmid.game.config.PlayerConfig;
import xyz.nucleoid.slime_mould.game.map.SlimeMouldMapConfig;

public final class SlimeMouldConfig {
    public static final Codec<SlimeMouldConfig> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                SlimeMouldMapConfig.CODEC.fieldOf("map").forGetter(config -> config.map),
                Codec.INT.optionalFieldOf("initial_food_spawn", 6).forGetter(config -> config.initialFoodSpawn),
                Codec.LONG.optionalFieldOf("food_spawn_interval", 8 * 20L).forGetter(config -> config.foodSpawnInterval),
                Codec.INT.optionalFieldOf("initial_food_level", 20).forGetter(config -> config.initialFoodLevel),
                Codec.INT.optionalFieldOf("food_level_per_food", 10).forGetter(config -> config.foodLevelPerFood),
                Codec.INT.optionalFieldOf("grow_cooldown", 10).forGetter(config -> config.growCooldown),
                PlayerConfig.CODEC.fieldOf("players").forGetter(config -> config.players)
        ).apply(instance, SlimeMouldConfig::new);
    });

    public final SlimeMouldMapConfig map;
    public final int initialFoodSpawn;
    public final long foodSpawnInterval;
    public final int initialFoodLevel;
    public final int foodLevelPerFood;
    public final int growCooldown;
    public final PlayerConfig players;

    private SlimeMouldConfig(
            SlimeMouldMapConfig map,
            int initialFoodSpawn, long foodSpawnInterval,
            int initialFoodLevel, int foodLevelPerFood,
            int growCooldown,
            PlayerConfig players
    ) {
        this.map = map;
        this.initialFoodSpawn = initialFoodSpawn;
        this.foodSpawnInterval = foodSpawnInterval;
        this.initialFoodLevel = initialFoodLevel;
        this.foodLevelPerFood = foodLevelPerFood;
        this.growCooldown = growCooldown;
        this.players = players;
    }
}
