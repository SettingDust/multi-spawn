package me.settingdust.multispawn.handler

import com.google.inject.Inject
import me.settingdust.laven.optional
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.task
import me.settingdust.laven.typeTokenOf
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn.Companion.pluginId
import me.settingdust.multispawn.MultiSpawn.Companion.usePermission
import me.settingdust.multispawn.PlayerStorage
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import org.spongepowered.api.Sponge
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import java.util.UUID
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
        pluginContainer.task(
            name = "$pluginId activate",
            async = true,
            interval = 50
        ) {
            serviceManager.provideUnchecked<MultiSpawnService>().all().forEach { spawn ->
                playerStorage.players.childrenMap.asSequence()
                    .map { UUID.fromString(it.key as String) to it.value.getList(typeTokenOf<String>()) }
                    .filterNot { it.second.contains(spawn.first) }
                    .map { Sponge.getServer().getPlayer(it.first).unwrap() }
                    .filterNotNull()
                    .filter { it.hasPermission(usePermission) }
                    .filter { it.location.extent == spawn.second.extent }
                    .filter { it.location.position.distance(spawn.second.position) < 3 }
                    .forEach {
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
            }
        }
    }
}
