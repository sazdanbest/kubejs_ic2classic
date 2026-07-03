package com.sazdan.kubejsic2classic.net;

import com.sazdan.kubejsic2classic.KubeJSIC2Classic;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge {@link SimpleChannel} for the addon. Currently carries a single client→server
 * packet ({@link FillCraftingGridPacket}) used by the JEI count-aware recipe transfer.
 *
 * <p>Registered from the {@code @Mod} constructor on both sides so the server can receive.
 */
public final class IC2Net {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(KubeJSIC2Classic.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean initialised;

    private IC2Net() {
    }

    /** Registers all packets. Idempotent. */
    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        CHANNEL.registerMessage(
                0,
                FillCraftingGridPacket.class,
                FillCraftingGridPacket::encode,
                FillCraftingGridPacket::decode,
                FillCraftingGridPacket::handle
        );

        KubeJSIC2Classic.LOGGER.info("[{}] network channel registered", KubeJSIC2Classic.MOD_ID);
    }
}
