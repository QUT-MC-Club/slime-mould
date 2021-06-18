package xyz.nucleoid.slime_mould.game;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.nucleoid.fantasy.BubbleWorldConfig;
import xyz.nucleoid.plasmid.game.GameOpenContext;
import xyz.nucleoid.plasmid.game.GameOpenProcedure;
import xyz.nucleoid.plasmid.game.GameSpace;
import xyz.nucleoid.plasmid.game.GameWaitingLobby;
import xyz.nucleoid.plasmid.game.StartResult;
import xyz.nucleoid.plasmid.game.event.OfferPlayerListener;
import xyz.nucleoid.plasmid.game.event.PlayerAddListener;
import xyz.nucleoid.plasmid.game.event.PlayerDamageListener;
import xyz.nucleoid.plasmid.game.event.PlayerDeathListener;
import xyz.nucleoid.plasmid.game.event.RequestStartListener;
import xyz.nucleoid.plasmid.game.player.JoinResult;
import xyz.nucleoid.slime_mould.game.map.SlimeMouldMap;
import xyz.nucleoid.slime_mould.game.map.SlimeMouldMapBuilder;

public final class SlimeMouldWaiting {
    private final GameSpace gameSpace;
    private final SlimeMouldMap map;

    private SlimeMouldWaiting(GameSpace gameSpace, SlimeMouldMap map) {
        this.gameSpace = gameSpace;
        this.map = map;
    }

    public static GameOpenProcedure open(GameOpenContext<SlimeMouldConfig> context) {
        SlimeMouldConfig config = context.getConfig();
        SlimeMouldMapBuilder mapBuilder = new SlimeMouldMapBuilder(config.map);
        SlimeMouldMap map = mapBuilder.build();

        BubbleWorldConfig worldConfig = new BubbleWorldConfig()
                .setGenerator(map.asGenerator(context.getServer()))
                .setDefaultGameMode(GameMode.ADVENTURE);

        return context.createOpenProcedure(worldConfig, game -> {
            GameWaitingLobby.applyTo(game, config.players);

            SlimeMouldWaiting waiting = new SlimeMouldWaiting(game.getSpace(), map);

            game.on(RequestStartListener.EVENT, () -> {
                SlimeMouldActive.open(game.getSpace(), map, config);
                return StartResult.OK;
            });

            game.on(OfferPlayerListener.EVENT, player -> {
                if (game.getSpace().getPlayerCount() >= SlimeMouldColors.COLORS.length) {
                    return JoinResult.gameFull();
                }
                return JoinResult.ok();
            });

            game.on(PlayerAddListener.EVENT, waiting::spawnPlayer);
            game.on(PlayerDeathListener.EVENT, (player, source) -> {
                waiting.spawnPlayer(player);
                return ActionResult.FAIL;
            });

            game.on(PlayerDamageListener.EVENT, (player, source, amount) -> ActionResult.FAIL);
        });
    }

    private void spawnPlayer(ServerPlayerEntity player) {
        player.setGameMode(GameMode.ADVENTURE);

        Vec3d spawn = this.map.getWaitingSpawn();
        player.teleport(this.gameSpace.getWorld(), spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
    }
}
