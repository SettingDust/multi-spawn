package me.settingdust.multispawn.handler

import com.google.inject.Inject
import me.settingdust.laven.optional
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.sponge.task
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn.Companion.pluginId
import me.settingdust.multispawn.MultiSpawn.Companion.usePermission
import me.settingdust.multispawn.PlayerStorage
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import ninja.leaping.configurate.kotlin.get
import org.spongepowered.api.Sponge
import org.spongepowered.api.event.game.state.GameStartingServerEvent
import org.spongepowered.api.event.game.state.GameStoppingServerEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import java.util.function.Function

@Suppress("UnstableApiUsage")
@ExperimentalStdlibApi
class ActivatePointHandler @Inject constructor(
    pluginContainer: PluginContainer,
    playerStorage: PlayerStorage,
    serviceManager: ServiceManager,
    localeService: LocaleService,
    mainConfig: MainConfig
) {
    init {

        pluginContainer.registerListener<GameStartingServerEvent> {
            val task = pluginContainer.task(
                name = "$pluginId activate",
                async = true,
                interval = 50
            ) {
                serviceManager.provideUnchecked<MultiSpawnService>().all().forEach { spawn ->
                    var shouldSave = false
                    Sponge.getServer().onlinePlayers.asSequence()
                        .filter { spawn.first !in playerStorage[it.uniqueId] }
                        .filter { it.hasPermission(usePermission) }
                        .filter { it.location.extent == spawn.second.extent }
                        .filter { it.location.position.distance(spawn.second.position) < 3 }
                        .forEach {
                            shouldSave = true
                            playerStorage.add(it.uniqueId, spawn.first)
                            if (mainConfig.sendMessage)
                                it.sendMessage(
                                    localeService.getTextUnsafe(
                                        it,
                                        "message.activate",
                                        "name" to Function { Text.of(spawn.first).optional() }
                                    )
                                )
                        }
                    if (shouldSave) playerStorage.save()
                }
            }
            pluginContainer.registerListener<GameStoppingServerEvent> { task.cancel() }
        }
    }
}
