package de.arvitus.dragonegggame;

import de.arvitus.dragonegggame.api.DragonEggAPI;
import de.arvitus.dragonegggame.config.Data;
import de.arvitus.dragonegggame.utils.ScheduledEvent;
import de.arvitus.dragonegggame.utils.Utils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import static de.arvitus.dragonegggame.DragonEggGame.CONFIG;
import static de.arvitus.dragonegggame.DragonEggGame.LOGGER;

public class Events {
    public static final LinkedHashMap<UUID, ScheduledEvent> SCHEDULED_ACTIONS = new LinkedHashMap<>();
    public static final LinkedHashMap<String, BiConsumer<Integer, MinecraftServer>> TICK_ACTIONS =
        new LinkedHashMap<>();

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            DragonEggGame.server = server;
            DragonEggAPI.init();
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
            Optional.ofNullable(DragonEggAPI.getData()).ifPresent(Data::save)
        );

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            new LinkedHashMap<>(SCHEDULED_ACTIONS).forEach((key, value) -> {
                try {
                    if (value.tryRun(server)) SCHEDULED_ACTIONS.remove(key, value);
                } catch (Exception e) {
                    LOGGER.warn("Error in scheduled callback", e);
                    SCHEDULED_ACTIONS.remove(key);
                }
            });
            int ticks = server.getTickCount();
            TICK_ACTIONS.forEach((key, value) -> {
                try {
                    value.accept(ticks, server);
                } catch (Exception e) {
                    LOGGER.warn("Error in tick callback '{}'", key, e);
                }
            });
        });

        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, lifecycledResourceManager, b) -> Commands.reload());

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, entity) -> {
            if (player.isCreative() && state.is(Blocks.DRAGON_EGG)) DragonEggAPI.clearPosition();
        });

        ServerEntityEvents.EQUIPMENT_CHANGE.register((livingEntity, equipmentSlot, previousStack, currentStack) -> {
            if (Utils.isOrHasDragonEgg(currentStack)) DragonEggAPI.updatePosition(livingEntity);
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            // item entity load cannot be here because /give is weird
            if (!(entity instanceof ItemEntity) && Utils.hasDragonEgg(entity)) DragonEggAPI.updatePosition(entity);
        });

        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (
                entity instanceof FallingBlockEntity fallingBlock &&
                entity.getRemovalReason() == Entity.RemovalReason.KILLED &&
                fallingBlock.getBlockState().is(Blocks.DRAGON_EGG)
            ) {
                fallingBlock.spawnAtLocation(world, fallingBlock.getBlockState().getBlock());
            }

            /*
            if (
                entity instanceof VehicleInventory inventory &&
                !entity.isRemoved() &&
                inventory.getLootTable() == null &&
                inventory.getInventory().stream().anyMatch(Utils::isOrHasDragonEgg)
            ) {
                int count = 0;
                for (int i = 0; i < inventory.getInventory().size(); i++) {
                    ItemStack stack = inventory.getInventoryStack(i);
                    if (!Utils.isOrHasDragonEgg(stack)) continue;
                    if (stack.isOf(Items.DRAGON_EGG)) count += stack.copyAndEmpty().getCount();
                    else count += Utils.removeDragonEgg(stack).getCount();
                }
                Utils.spawnDragonEggAtSpawn(world.getServer(), count);
            }
             */

            if (entity instanceof ItemEntity item && Utils.isOrHasDragonEgg(item.getItem())) {
                ItemStack stack = item.getItem();
                int count = stack.getCount();
                if (!item.isRemoved()) {
                    if (Utils.isNearServerSpawn(item)) return;
                    if (stack.is(Items.DRAGON_EGG)) {
                        item.makeFakeItem();
                        item.setItem(Items.STONE.getDefaultInstance());
                    }
                    item.setGlowingTag(false);
                } else if (!item.isCurrentlyGlowing()) return;

                if (!stack.is(Items.DRAGON_EGG)) count = Utils.countDragonEgg(stack);
                Utils.spawnDragonEggAtSpawn(world.getServer(), count);
            }
        });

        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof RandomizableContainerBlockEntity lootableContainerBlockEntity &&
                lootableContainerBlockEntity.getLootTable() != null) return;

            if (blockEntity instanceof BaseContainerBlockEntity containerBlockEntity) {
                MinecraftServer server = world.getServer();
                server.schedule(new TickTask(
                    server.getTickCount(), () -> {
                    if (containerBlockEntity.isRemoved() || containerBlockEntity.isEmpty()) return;
                    for (int i = 0; i < containerBlockEntity.getContainerSize(); i++) {
                        ItemStack stack = containerBlockEntity.getItem(i);
                        if (stack.isEmpty()) continue;
                        if (Utils.isOrHasDragonEgg(stack)) {
                            DragonEggAPI.updatePosition(containerBlockEntity);
                            break;
                        }
                    }
                }
                ));
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!CONFIG.rightClickPickupEnabled || (CONFIG.rightClickPickupRequiresEmptyHand && !player.getItemInHand(hand).isEmpty())) {
                return InteractionResult.PASS;
            }

            if (!world.getBlockState(hitResult.getBlockPos()).is(Blocks.DRAGON_EGG)) {
                return InteractionResult.PASS;
            }

            if (!world.isClientSide()) {
                world.removeBlock(hitResult.getBlockPos(), false);
                ItemStack egg = Items.DRAGON_EGG.getDefaultInstance();
                if (!player.getInventory().add(egg)) player.drop(egg, false);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
