package me.settingdust.multispawn.handler

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import me.settingdust.laven.caffeine.set
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn.Companion.syncRemovePermission
import me.settingdust.multispawn.MultiSpawn.Companion.syncSetPermission
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.block.ChangeBlockEvent
import org.spongepowered.api.event.message.MessageChannelEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@ExperimentalStdlibApi
class BlockSyncHandler @Inject constructor(
    mainConfig: MainConfig,
    pluginContainer: PluginContainer,
    localeService: LocaleService,
    serviceManager: ServiceManager
) {
    private val typingPlayerCache: Cache<UUID, Location<World>> =
        Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build()

    init {
        pluginContainer.registerListener<ChangeBlockEvent.Place> {
            val blocksToSync = mainConfig.blocksToSync
            if (blocksToSync.isEmpty()) return@registerListener
            val player = cause.root()
            if (player !is Player) return@registerListener
            if (!player.hasPermission(syncSetPermission)) return@registerListener

            transactions.asSequence()
                .map { it.final }
                .filter { blocksToSync.contains(it.state.type.id) }
                .firstOrNull()
                ?.location
                ?.ifPresent {
                    typingPlayerCache[player.uniqueId] = it
                    if (mainConfig.sendMessage)
                        player.sendMessage(localeService.getTextUnsafe("message.waitingForName", player))
                }
        }

        pluginContainer.registerListener<MessageChannelEvent.Chat> {
            val blocksToSync = mainConfig.blocksToSync
            if (blocksToSync.isEmpty()) return@registerListener
            val player = cause.root()
            if (player !is Player) return@registerListener
            val uuid = player.uniqueId
            typingPlayerCache.getIfPresent(uuid)
                ?.let {
                    serviceManager.provideUnchecked<MultiSpawnService>()[rawMessage.toPlain()] = it
                    typingPlayerCache.invalidate(uuid)
                    isCancelled = true
                    if (mainConfig.sendMessage)
                        player.sendMessage(
                            localeService.getTextUnsafe(
                                "message.set.success",
                                player,
                                "name" to { rawMessage }
                            )
                        )
                }
        }

        pluginContainer.registerListener<ChangeBlockEvent.Break> {
            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
            val blocksToSync = mainConfig.blocksToSync
            if (blocksToSync.isEmpty()) return@registerListener
            val player = cause.root()
            if (player !is Player) return@registerListener
            if (!player.hasPermission(syncRemovePermission)) return@registerListener

            transactions.asSequence()
                .map { it.original }
                .filter { blocksToSync.contains(it.state.type.id) }
                .mapNotNull { it.location.unwrap() }
                .mapNotNull { location ->
                    multiSpawnService.all()
                        .firstOrNull { location.position.distance(it.second.position) < 1 }
                        ?.first
                }
                .forEach { name ->
                    multiSpawnService.remove(name)
                    if (mainConfig.sendMessage)
                        player.sendMessage(
                            localeService.getTextUnsafe(
                                "message.remove.success",
                                player,
                                "name" to { Text.of(name) }
                            )
                        )
                }
        }
    }
}
