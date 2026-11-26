package com.sift.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.sift.config.SiftConfig;
import com.sift.core.ServiceRegistry;
import com.sift.dimension.DimensionService;
import com.sift.resourcepack.ResourcePackService;
import com.sift.tides.Tide;
import com.sift.tides.TideService;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Phase 1 command set. Permissions: sift.admin, sift.enter, sift.exit, sift.info, sift.debug.
 * Commands from the spec that need later systems (locate, give, structures, weather, spawn,
 * reload) are intentionally not registered yet rather than registered as no-ops.
 */
public final class SiftCommand {

    private final ServiceRegistry services;
    private final SiftConfig config;

    public SiftCommand(ServiceRegistry services, SiftConfig config) {
        this.services = services;
        this.config = config;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("sift");

        root.executes(ctx -> help(ctx.getSource().getSender()));
        root.then(Commands.literal("help").executes(ctx -> help(ctx.getSource().getSender())));

        root.then(Commands.literal("info")
                .requires(src -> src.getSender().hasPermission("sift.info"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    World world = services.get(DimensionService.class).world();
                    TideService tides = services.get(TideService.class);
                    sender.sendMessage(Component.text("TheSift — world: "
                            + (world == null ? "NOT LOADED" : world.getKey().toString())
                            + ", tide: " + tides.current() + " (" + tides.secondsRemaining() + "s left)"));
                    return Command.SINGLE_SUCCESS;
                }));

        // enter/exit: placeholder travel until the portal and rift systems replace it.
        root.then(Commands.literal("enter")
                .requires(src -> src.getSender().hasPermission("sift.enter"))
                .executes(ctx -> travel(ctx.getSource().getSender(), true)));
        root.then(Commands.literal("exit")
                .requires(src -> src.getSender().hasPermission("sift.exit"))
                .executes(ctx -> travel(ctx.getSource().getSender(), false)));

        root.then(Commands.literal("resourcepack")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text("Players only."));
                        return Command.SINGLE_SUCCESS;
                    }
                    ResourcePackService packs = services.get(ResourcePackService.class);
                    if (!packs.send(player)) {
                        player.sendMessage(Component.text(
                                "No resource pack is configured on this server.", NamedTextColor.YELLOW));
                    }
                    return Command.SINGLE_SUCCESS;
                }));

        root.then(Commands.literal("tide")
                .executes(ctx -> {
                    TideService tides = services.get(TideService.class);
                    ctx.getSource().getSender().sendMessage(Component.text(
                            "Current tide: " + tides.current() + " (" + tides.secondsRemaining() + "s left)"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission("sift.admin"))
                        .then(Commands.argument("tide", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (Tide t : Tide.values()) {
                                        builder.suggest(t.name().toLowerCase());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String raw = StringArgumentType.getString(ctx, "tide").toUpperCase();
                                    try {
                                        services.get(TideService.class).setTide(Tide.valueOf(raw));
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().getSender().sendMessage(
                                                Component.text("Unknown tide.", NamedTextColor.RED));
                                    }
                                    return Command.SINGLE_SUCCESS;
                                }))));

        return root.build();
    }

    private int help(CommandSender sender) {
        sender.sendMessage(Component.text(
                "/sift info | enter | exit | resourcepack | tide [set <flow|thrive|endure>]"));
        return Command.SINGLE_SUCCESS;
    }

    private int travel(CommandSender sender, boolean toSift) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only."));
            return Command.SINGLE_SUCCESS;
        }
        World sift = services.get(DimensionService.class).world();
        if (sift == null) {
            player.sendMessage(Component.text(
                    "The Sift is not loaded. An admin may need to restart the server.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        World target = toSift ? sift : player.getServer().getWorlds().get(0);
        player.teleportAsync(target.getSpawnLocation()).thenAccept(ok -> {
            if (!ok) {
                player.sendMessage(Component.text("Teleport failed.", NamedTextColor.RED));
            }
        });
        return Command.SINGLE_SUCCESS;
    }
}
