package me.settingdust.multispawn.handler

import com.google.inject.Inject
import io.github.nucleuspowered.nucleus.api.core.event.NucleusFirstJoinEvent
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn.Companion.waystoneTeleportHelperFilter
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import org.spongepowered.api.entity.Transform
import org.spongepowered.api.event.entity.living.humanoid.player.RespawnPlayerEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import org.spongepowered.api.world.TeleportHelper
import org.spongepowered.api.world.teleport.TeleportHelperFilters
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@ExperimentalStdlibApi
class SpawnHandler @Inject constructor(
    pluginContainer: PluginContainer,
    serviceManager: ServiceManager,
    teleportHelper: TeleportHelper,
    localeService: LocaleService,
    mainConfig: MainConfig
) {
    init {
        pluginContainer.registerListener<NucleusFirstJoinEvent> {
            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
            multiSpawnService.all().firstOrNull()
                ?.run { second }
                ?.apply {
                    targetEntity.location = teleportHelper.getSafeLocationWithBlacklist(
                        this,
                        3,
                        9,
                        2,
                        TeleportHelperFilters.DEFAULT,
                        waystoneTeleportHelperFilter
                    ).unwrap() ?: this
                }
        }
        pluginContainer.registerListener<RespawnPlayerEvent> {
            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
            val closest = multiSpawnService.getClosest(fromTransform.location, targetEntity.uniqueId)
            toTransform = Transform(
                closest
                    .run {
                        teleportHelper.getSafeLocationWithBlacklist(
                            second,
                            3,
                            9,
                            2,
                            TeleportHelperFilters.DEFAULT,
                            waystoneTeleportHelperFilter
                        ).unwrap() ?: second
                    }
            )
            if (mainConfig.sendMessage)
                targetEntity.sendMessage(
                    localeService.getTextUnsafe(
                        "message.respawn",
                        targetEntity,
                        "name" to { Text.of(closest.first) }
                    )
                )
        }
    }
}
