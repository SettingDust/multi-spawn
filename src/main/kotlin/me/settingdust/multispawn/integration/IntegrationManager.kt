package me.settingdust.multispawn.integration

import com.google.inject.Inject
import io.github.nucleuspowered.nucleus.api.core.event.NucleusModuleEvent
import me.settingdust.laven.sponge.registerListener
import org.spongepowered.api.plugin.PluginContainer

class IntegrationManager @Inject constructor(
    pluginContainer: PluginContainer
) {
    init {
        pluginContainer.registerListener<NucleusModuleEvent.AboutToConstruct> {
            disableModule("spawn", pluginContainer)
        }
    }
}
