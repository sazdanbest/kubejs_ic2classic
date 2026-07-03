package com.sazdan.kubejsic2classic;

import com.mojang.logging.LogUtils;
import com.sazdan.kubejsic2classic.net.IC2Net;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Forge entry point for the KubeJS IC2Classic addon.
 *
 * <p>The actual KubeJS integration lives in {@link KubeJSIC2ClassicPlugin}, which KubeJS
 * discovers through {@code META-INF/../kubejs.plugins.txt}. This class only exists so Forge
 * recognises us as a mod and so we have a place to hang common (non-KubeJS) setup later.
 */
@Mod(KubeJSIC2Classic.MOD_ID)
public final class KubeJSIC2Classic {
    public static final String MOD_ID = "kubejs_ic2classic";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KubeJSIC2Classic() {
        LOGGER.info("[{}] Forge mod container constructed", MOD_ID);
        IC2Net.init();
    }
}
