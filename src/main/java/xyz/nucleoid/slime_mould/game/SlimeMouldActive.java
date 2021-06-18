package xyz.nucleoid.slime_mould.game;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.plasmid.game.GameCloseReason;
import xyz.nucleoid.plasmid.game.GameLogic;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.event.GameOpenListener;
import xyz.nucleoid.plasmid.game.event.GameTickListener;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.UseBlockListener;
import xyz.nucleoid.plasmid.game.player.GameTeam;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.plasmid.game.player.PlayerSet;
import xyz.nucleoid.plasmid.game.rule.GameRule;
import xyz.nucleoid.plasmid.game.rule.RuleResult;
import xyz.nucleoid.plasmid.util.ItemStackBuilder;
import xyz.nucleoid.plasmid.widget.GlobalWidgets;
import xyz.nucleoid.plasmid.widget.SidebarWidget;
import xyz.nucleoid.slime_mould.game.map.SlimeMouldMap;
import xyz.nucleoid.slime_mould.game.map.SlimeMouldPlate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SlimeMouldActive {
    private static final long CLOSE_TICKS = 20 * 5;

    private static final Item GROWTH_ITEM = Items.WOODEN_HOE;

    private static final ItemStackBuilder GROWTH_STACK = ItemStackBuilder.of(GROWTH_ITEM)
            .setName(new LiteralText("Grow your mould!").formatted(Formatting.GREEN, Formatting.BOLD))
            .addLore(new LiteralText("Right click on blocks adjacent to your own to grow"));

    private static final EntityAttributeModifier STRETCHED_THIN_MODIFIER = new EntityAttributeModifier(
            "slime_mould_stretched_thin",
            -0.5,
            EntityAttributeModifier.Operation.MULTIPLY_BASE
    );

    private final GameSpace gameSpace;
    private final SlimeMouldMap map;
    private final SlimeMouldConfig config;

    private final SidebarWidget sidebar;

    private final Map<GameProfile, Mould> playerToMould = new Object2ObjectOpenHashMap<>();

    private final SlimeMouldFood food;

    private boolean opened;
    private boolean singlePlayer;

    private long lastFoodSpawnTime;

    private long closeTime = -1;

    private SlimeMouldActive(GameLogic gameLogic, SlimeMouldMap map, SlimeMouldConfig config, GlobalWidgets widgets) {
        this.gameSpace = gameLogic.getSpace();
        this.map = map;
        this.config = config;

        this.food = new SlimeMouldFood(gameLogic);

        this.sidebar = widgets.addSidebar(new LiteralText("Slime Mould!").formatted(Formatting.RED, Formatting.BOLD));
    }

    public static void open(GameSpace gameSpace, SlimeMouldMap map, SlimeMouldConfig config) {
        gameSpace.openGame(game -> {
            GlobalWidgets widgets = new GlobalWidgets(game);

            SlimeMouldActive active = new SlimeMouldActive(game, map, config, widgets);

            game.setRule(GameRule.CRAFTING, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.DENY);
            game.setRule(GameRule.BLOCK_DROPS, RuleResult.DENY);
            game.setRule(GameRule.FALL_DAMAGE, RuleResult.DENY);
            game.setRule(GameRule.HUNGER, RuleResult.DENY);
            game.setRule(GameRule.THROW_ITEMS, RuleResult.DENY);
            game.setRule(GameRule.PVP, RuleResult.DENY);

            game.on(GameOpenListener.EVENT, active::onOpen);

            game.on(OfferPlayerListener.EVENT, player -> JoinResult.ok());
            game.on(PlayerAddListener.EVENT, active::addPlayer);

            game.on(GameTickListener.EVENT, active::tick);
            game.on(UseBlockListener.EVENT, active::onUseBlock);

            game.on(PlayerDamageListener.EVENT, (player, source, amount) -> ActionResult.FAIL);
        });
    }

    private void onOpen() {
        int plateRadius = this.map.getPlate().radius;
        double spawnRadius = plateRadius * (3.0 / 4.0);

        ServerWorld world = this.gameSpace.getWorld();
        PlayerSet players = this.gameSpace.getPlayers();

        List<DyeColor> colors = SlimeMouldColors.shuffledColors(world.random);

        int i = 0;

        for (ServerPlayerEntity player : players) {
            Mould mould = new Mould(player, colors.get(i), this.config.initialFoodLevel);
            this.playerToMould.put(player.getGameProfile(), mould);

            double theta = ((double) i / players.size()) * Math.PI * 2.0;
            this.spawnPlayer(player, mould, theta, spawnRadius);

            this.updateFoodBar(player, mould);

            i++;
        }

        this.spawnInitialFood();

        this.opened = true;
        this.singlePlayer = players.size() == 1;
        this.lastFoodSpawnTime = world.getTime();

        this.updateSidebar();
    }

    private void spawnPlayer(ServerPlayerEntity player, Mould mould, double theta, double radius) {
        ServerWorld world = this.gameSpace.getWorld();

        float yaw = (float) Math.toDegrees(theta);

        BlockPos spawnPos = this.map.getPlate().getSpawnPos(theta, radius);
        player.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, yaw, 0.0F);

        world.setBlockState(spawnPos, mould.block);

        player.inventory.insertStack(GROWTH_STACK.build());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, Integer.MAX_VALUE, 200, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, Integer.MAX_VALUE, 0, false, false));
    }

    private void spawnInitialFood() {
        int foodCount = this.gameSpace.getPlayerCount() * this.config.initialFoodSpawn;

        int i = 0;
        while (i < foodCount) {
            if (this.trySpawnFood()) {
                i++;
            }
        }
    }

    private void addPlayer(ServerPlayerEntity player) {
        if (this.opened) {
            this.spawnSpectator(player);
        }
    }

    private void tick() {
        ServerWorld world = this.gameSpace.getWorld();
        long time = world.getTime();

        if (this.closeTime > 0) {
            this.tickClosing(time);
            return;
        }

        this.tickFood(time);
        this.tickMoulds();

        if (!this.singlePlayer && this.playerToMould.size() <= 1) {
            this.closeTime = time + CLOSE_TICKS;
        }
    }

    private void tickFood(long time) {
        long interval = this.config.foodSpawnInterval / this.playerToMould.size();
        if (time - this.lastFoodSpawnTime >= interval) {
            if (this.trySpawnFood()) {
                this.lastFoodSpawnTime = time;
            }
        }
    }

    private boolean trySpawnFood() {
        ServerWorld world = this.gameSpace.getWorld();
        BlockPos foodSpawnPos = this.findFoodSpawnPos(world, world.random);
        if (foodSpawnPos == null) {
            return false;
        }

        return this.food.addFood(foodSpawnPos);
    }

    @Nullable
    private BlockPos findFoodSpawnPos(ServerWorld world, Random random) {
        SlimeMouldPlate plate = this.map.getPlate();
        BlockPos spawnPos = plate.getRandomSurfacePos(random);

        if (plate.testSurface(world, spawnPos).isSterile()) {
            return spawnPos.up();
        } else {
            return null;
        }
    }

    private void tickMoulds() {
        for (ServerPlayerEntity player : this.gameSpace.getPlayers()) {
            Mould mould = this.playerToMould.get(player.getGameProfile());
            if (mould != null) {
                this.tickMould(player, mould);
            }
        }
    }

    private void tickMould(ServerPlayerEntity player, Mould mould) {
        BlockPos pos = player.getBlockPos();
        if (mould.moveTo(pos)) {
            this.onMouldMove(player, mould, pos);
        }
    }

    private void tickClosing(long time) {
        if (time >= this.closeTime) {
            this.gameSpace.close(GameCloseReason.FINISHED);
        }
    }

    private void onMouldMove(ServerPlayerEntity player, Mould mould, BlockPos pos) {
        BlockState surface = player.world.getBlockState(pos.down());

        boolean slowed = surface != mould.block && !this.hasAdjacentMould(pos, mould);

        if (mould.updateSlowed(slowed)) {
            EntityAttributeInstance attribute = player.getAttributes().getCustomInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (attribute != null) {
                if (slowed) {
                    attribute.addTemporaryModifier(STRETCHED_THIN_MODIFIER);
                } else {
                    attribute.removeModifier(STRETCHED_THIN_MODIFIER);
                }
            }
        }
    }

    private ActionResult onUseBlock(ServerPlayerEntity player, Hand hand, BlockHitResult result) {
        if (player.getStackInHand(hand).getItem() == GROWTH_ITEM) {
            Mould mould = this.playerToMould.get(player.getGameProfile());
            if (mould == null) {
                return ActionResult.PASS;
            }

            BlockPos pos = result.getBlockPos();
            if (this.tryGrowInto(player, mould, pos)) {
                return ActionResult.SUCCESS;
            }
        }

        return ActionResult.PASS;
    }

    private boolean tryGrowInto(ServerPlayerEntity player, Mould mould, BlockPos pos) {
        ServerWorld world = this.gameSpace.getWorld();
        if (world.getBlockState(pos) == mould.block || player.getItemCooldownManager().isCoolingDown(GROWTH_ITEM)) {
            return false;
        }

        SlimeMouldPlate.Surface surface = this.map.getPlate().testSurface(world, pos);
        if (!surface.isOnPlate() || !this.hasAdjacentMould(pos, mould)) {
            return false;
        }

        if (this.takeFoodFrom(mould)) {
            this.growInto(player, mould, pos);
            return true;
        } else {
            return false;
        }
    }

    private void growInto(ServerPlayerEntity player, Mould mould, BlockPos pos) {
        player.getItemCooldownManager().set(GROWTH_ITEM, this.config.growCooldown);

        if (this.food.removeFoodAt(pos.up())) {
            player.playSound(SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 1.0F, 1.0F);
            mould.food += this.config.foodLevelPerFood;
        }

        Mould existingMould = this.getMouldFor(player.world.getBlockState(pos));
        if (existingMould != null && --existingMould.score <= 0) {
            this.eliminate(existingMould);
        }

        player.world.setBlockState(pos, mould.block);
        mould.score++;

        this.updateSidebar();
    }

    private boolean hasAdjacentMould(BlockPos pos, Mould mould) {
        ServerWorld world = this.gameSpace.getWorld();

        BlockPos.Mutable mutablePos = new BlockPos.Mutable();
        for (int i = 0; i < 4; i++) {
            mutablePos.set(pos, Direction.fromHorizontal(i));
            if (world.getBlockState(mutablePos) == mould.block) {
                return true;
            }
        }

        return false;
    }

    @Nullable
    private Mould getMouldFor(BlockState block) {
        if (!SlimeMouldPlate.testSurface(block.getBlock()).isMould()) {
            return null;
        }

        for (Mould mould : this.playerToMould.values()) {
            if (mould.block == block) {
                return mould;
            }
        }

        return null;
    }

    private void spawnSpectator(ServerPlayerEntity player) {
        player.setGameMode(GameMode.SPECTATOR);

        Vec3d spawn = this.map.getWaitingSpawn();
        player.teleport(this.gameSpace.getWorld(), spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
    }

    private void updateFoodBar(ServerPlayerEntity player, Mould mould) {
        player.experienceProgress = 1.0F;
        player.setExperienceLevel(mould.food);
    }

    private void updateSidebar() {
        this.sidebar.set(content -> {
            content.writeLine(Formatting.GREEN + "Expand your slime mould!");
            content.writeLine("");

            this.playerToMould.values().stream()
                    .sorted(Comparator.comparingInt(mould -> -mould.score))
                    .limit(8)
                    .forEach(mould -> {
                        String name = mould.team.getFormatting() + mould.team.getDisplay();
                        String score = Formatting.GOLD.toString() + mould.score;
                        content.writeLine(name + ": " + score);
                    });
        });
    }

    private boolean takeFoodFrom(Mould mould) {
        if (mould.food > 0) {
            mould.food--;

            PlayerSet players = this.gameSpace.getPlayers();
            ServerPlayerEntity player = players.getEntity(mould.player.getId());
            if (player != null) {
                this.updateFoodBar(player, mould);
            }

            return true;
        } else {
            this.eliminate(mould);
            return false;
        }
    }

    private void eliminate(Mould mould) {
        if (this.playerToMould.remove(mould.player, mould)) {
            this.gameSpace.getPlayers().sendMessage(
                    new LiteralText(mould.player.getName())
                            .append(" has been eliminated!")
                            .formatted(Formatting.RED)
            );
        }
    }

    static final class Mould {
        final GameProfile player;

        final DyeColor dyeColor;
        final GameTeam team;
        final BlockState block;

        BlockPos lastPos;
        boolean slowed;

        int food;
        int score = 1;

        Mould(ServerPlayerEntity player, DyeColor dyeColor, int initialFood) {
            GameProfile profile = player.getGameProfile();
            this.player = profile;

            this.dyeColor = dyeColor;
            this.team = new GameTeam(dyeColor.getName(), profile.getName(), dyeColor);
            this.block = SlimeMouldPlate.getMouldBlock(dyeColor);

            this.food = initialFood;
        }

        boolean moveTo(BlockPos pos) {
            boolean moved = !pos.equals(this.lastPos);
            this.lastPos = pos;
            return moved;
        }

        boolean updateSlowed(boolean slowed) {
            boolean changed = this.slowed != slowed;
            this.slowed = slowed;
            return changed;
        }
    }
}
