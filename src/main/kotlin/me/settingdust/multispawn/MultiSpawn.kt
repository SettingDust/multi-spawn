package me.settingdust.multispawn

import com.google.inject.Inject
import me.settingdust.laven.typeTokenOf
import me.settingdust.multispawn.handler.HandlerManager
import me.settingdust.multispawn.integration.IntegrationManager
import ninja.leaping.configurate.reference.WatchServiceListener
import org.spongepowered.api.plugin.Dependency
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World

@ExperimentalStdlibApi
@Plugin(
    id = MultiSpawn.pluginId,
    name = MultiSpawn.pluginName,
    version = MultiSpawn.pluginVersion,
    description = MultiSpawn.pluginDescription,
    authors = ["SettingDust"],
    dependencies = [Dependency(id = "nucleus", version = "[2.0,)")]
)
class MultiSpawn
@Inject constructor(
    integrationManager: IntegrationManager,
    configManager: ConfigManager,
    handlerManager: HandlerManager,
    pluginCommandManager: PluginCommandManager
) {
    companion object {
        const val pluginId = "multi-spawn"
        const val pluginName = "Multi Spawn"
        const val pluginVersion = "@version@"
        const val pluginDescription = "Respawn at the closest spawn point"

        val watchServiceListener: WatchServiceListener = WatchServiceListener.create()

        val locationType = typeTokenOf<Location<World>>()

        const val usePermission = "spawn.use"
        const val commandSetPermission = "spawn.command.admin.set"
        const val commandRemovePermission = "spawn.command.admin.remove"
        const val commandListPermission = "spawn.command.admin.list"
        const val syncSetPermission = "spawn.sync.set"
        const val syncRemovePermission = "spawn.sync.remove"

        val waystoneTeleportHelperFilter = WaystoneTeleportHelperFilter()
    }
}
