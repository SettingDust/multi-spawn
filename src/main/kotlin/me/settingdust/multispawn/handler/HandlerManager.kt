package me.settingdust.multispawn.handler

import com.google.inject.Inject
import com.google.inject.Injector
import me.settingdust.laven.get
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.typeTokenOf
import org.spongepowered.api.event.game.state.GameLoadCompleteEvent
import org.spongepowered.api.plugin.PluginContainer
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@ExperimentalStdlibApi
class HandlerManager @Inject constructor(
    pluginContainer: PluginContainer,
    injector: Injector
) {
    init {
        pluginContainer.registerListener<GameLoadCompleteEvent> {
            injector[typeTokenOf<ActivatePointHandler>()]
            injector[typeTokenOf<SpawnHandler>()]
            injector[typeTokenOf<BlockSyncHandler>()]
            injector[typeTokenOf<WaystoneSyncHandler>()]
        }
    }
}
