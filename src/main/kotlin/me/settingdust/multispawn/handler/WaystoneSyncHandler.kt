package me.settingdust.multispawn.handler

import com.google.inject.Inject
import me.settingdust.laven.optional
import me.settingdust.laven.sponge.event.allOf
import me.settingdust.laven.sponge.event.containsType
import me.settingdust.laven.sponge.getChannel
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.sponge.registerMessage
import me.settingdust.laven.typeTokenOf
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn
import me.settingdust.multispawn.MultiSpawn.Companion.pluginId
import me.settingdust.multispawn.WaystoneTeleportHelperFilter.Companion.waystoneBlockId
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import me.settingdust.multispawn.network.MessageEditWaystone
import org.spongepowered.api.block.tileentity.TileEntity
import org.spongepowered.api.data.DataQuery
import org.spongepowered.api.data.key.Key
import org.spongepowered.api.data.value.BaseValue
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.block.ChangeBlockEvent
import org.spongepowered.api.network.ChannelBinding
import org.spongepowered.api.network.PlayerConnection
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import org.spongepowered.api.world.Location
import java.util.function.Function

@Suppress("UnstableApiUsage")
@ExperimentalStdlibApi
class WaystoneSyncHandler @Inject constructor(
    mainConfig: MainConfig,
    pluginContainer: PluginContainer,
    localeService: LocaleService,
    serviceManager: ServiceManager
) {
    companion object {
        val baseKey: Key<BaseValue<Boolean>> =
            Key.builder()
                .id(pluginId)
                .name("Waystone base")
                .query(DataQuery.of("base"))
                .type(typeTokenOf<BaseValue<Boolean>>())
                .build()
        val waystoneNameKey: Key<BaseValue<String>> =
            Key.builder().query(DataQuery.of("UnSafeData", "WaystoneName"))
                .id(pluginId)
                .name("Waystone name")
                .type(typeTokenOf<BaseValue<String>>())
                .build()
    }

    init {
        pluginContainer.registerListener<ChangeBlockEvent.Post> {
            if (!mainConfig.waystoneEnabled) return@registerListener
            if (!cause.containsType<ChangeBlockEvent.Place>()) return@registerListener
            if (!cause.containsType<TileEntity>()) return@registerListener
            val player = cause.root()
            if (player !is Player) return@registerListener
            if (!player.hasPermission(MultiSpawn.syncSetPermission)) return@registerListener

            cause.allOf<TileEntity>()
                .asSequence()
                .filter { it.type.id == waystoneBlockId }
                .mapNotNull { tileEntity ->
                    tileEntity.get(baseKey)
                        .filter { it }
                        .run { unwrap() }
                        ?.let { tileEntity }
                }
                .firstOrNull()
                ?.let { tileEntity ->
                    tileEntity.get(waystoneNameKey).unwrap()
                        ?.let { name ->
                            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                            multiSpawnService[name] = tileEntity.location
                            if (mainConfig.sendMessage)
                                player.sendMessage(
                                    localeService.getTextUnsafe(
                                        player,
                                        "message.set.success",
                                        "name" to Function { Text.of(name).optional() }
                                    )
                                )
                        }
                        ?: { isCancelled = true }()
                }
        }

        pluginContainer.registerListener<ChangeBlockEvent.Break> {
            if (!mainConfig.waystoneEnabled) return@registerListener
            val player = cause.root()
            if (player !is Player) return@registerListener
            if (!player.hasPermission(MultiSpawn.syncRemovePermission)) return@registerListener

            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
            transactions.asSequence()
                .map { it.original }
                .filter { it.state.type.id == waystoneBlockId }
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
                                player,
                                "message.remove.success",
                                "name" to Function { Text.of(name).optional() }
                            )
                        )
                }
        }

        pluginContainer
            .getChannel("waystones")
            ?.let { it as? ChannelBinding.IndexedMessageChannel }
            ?.registerMessage<MessageEditWaystone>(MessageEditWaystone::class.hashCode()) { message, connection, _ ->
                if (connection !is PlayerConnection) return@registerMessage
                val player = connection.player
                if (!player.hasPermission(MultiSpawn.syncSetPermission)) return@registerMessage
                val location = Location(player.world, message.pos)
                val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                multiSpawnService.all()
                    .firstOrNull { location.position.distance(it.second.position) < 1 }
                    ?.let {
                        multiSpawnService.remove(it.first)
                        multiSpawnService[message.name] = it.second

                        if (mainConfig.sendMessage)
                            player.sendMessage(
                                localeService.getTextUnsafe(
                                    player,
                                    "message.set.success",
                                    "name" to Function { Text.of(message.name).optional() }
                                )
                            )
                    }
            }
    }
}
