package de.arvitus.dragonegggame;

import com.mojang.serialization.MapCodec;
import de.arvitus.dragonegggame.api.DragonEggAPI;
import de.arvitus.dragonegggame.config.Data;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.NonNull;

import static de.arvitus.dragonegggame.DragonEggGame.CONFIG;
import static de.arvitus.dragonegggame.DragonEggGame.MOD_ID;

public class LootConditions {
    public static void register() {
        register("is_bearer", IsBearer.CODEC);
        register("is_nearby", IsNearby.CODEC);
    }

    private static void register(String id, MapCodec<? extends LootItemCondition> codec) {
        Registry.register(
            BuiltInRegistries.LOOT_CONDITION_TYPE,
            Identifier.fromNamespaceAndPath(MOD_ID, id),
            codec
        );
    }

    public static class IsBearer implements LootItemCondition {
        public static final IsBearer INSTANCE = new IsBearer();
        public static final MapCodec<IsBearer> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public @NonNull MapCodec<IsBearer> codec() {
            return CODEC;
        }

        @Override
        public boolean test(LootContext context) {
            Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
            Data data = DragonEggAPI.getData();
            if (entity instanceof ServerPlayer player && data != null)
                return player.getUUID().equals(data.playerUUID);
            return false;
        }
    }

    public static class IsNearby implements LootItemCondition {
        public static final IsNearby INSTANCE = new IsNearby();
        public static final MapCodec<IsNearby> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public @NonNull MapCodec<IsNearby> codec() {
            return CODEC;
        }

        @Override
        public boolean test(LootContext context) {
            Entity entity = context.getOptionalParameter(LootContextParams.THIS_ENTITY);
            Data data = DragonEggAPI.getData();
            if (entity != null && data != null && data.world != null)
                return entity.position().closerThan(data.getPosition(), CONFIG.nearbyRange);
            return false;
        }
    }
}


