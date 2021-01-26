package me.settingdust.multispawn.handler

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.google.inject.Inject
import me.settingdust.laven.caffeine.get
import me.settingdust.laven.caffeine.set
import me.settingdust.laven.sponge.event.containsType
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.sponge.task
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn
import me.settingdust.multispawn.PlayerStorage
import me.settingdust.multispawn.WaystoneTeleportHelperFilter.Companion.waystoneBlockId
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import org.spongepowered.api.block.trait.BlockTrait
import org.spongepowered.api.data.DataQuery
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.block.ChangeBlockEvent
import org.spongepowered.api.event.block.InteractBlockEvent
import org.spongepowered.api.event.entity.MoveEntityEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import org.spongepowered.api.util.Direction
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@Suppress("UnstableApiUsage")
@ExperimentalStdlibApi
class WaystoneSyncHandler @Inject constructor(
    mainConfig: MainConfig,
    playerStorage: PlayerStorage,
    pluginContainer: PluginContainer,
    localeService: LocaleService,
    serviceManager: ServiceManager
) {
    companion object {
        val waystoneNameQuery: DataQuery = DataQuery.of("UnsafeData", "WaystoneName")
    }

    private val typingPlayerCache: Cache<UUID, Pair<String, Location<World>>> =
        Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build()

    private var baseTrait: BlockTrait<Boolean>? = null

    init {
        pluginContainer.registerListener<ChangeBlockEvent.Post> {
            if (!mainConfig.waystoneEnabled) return@registerListener
            if (!cause.containsType<ChangeBlockEvent.Place>()) return@registerListener
            val player = cause.root() as? Player ?: return@registerListener
            if (!player.hasPermission(MultiSpawn.syncSetPermission)) return@registerListener

            pluginContainer.task {
                transactions
                    .asSequence()
                    .map { it.final }
                    .filter { it.state.type.id == waystoneBlockId }
                    .mapNotNull { blockSnapshot ->
                        val block = blockSnapshot.state
                        if (baseTrait == null) baseTrait = block.getTrait("base").unwrap() as? BlockTrait<Boolean>
                        baseTrait?.let {
                            if (block.getTraitValue(it).unwrap() == true)
                                blockSnapshot.location.unwrap()
                            else null
                        }
                    }
                    .firstOrNull()
                    ?.let { location ->
                        location.tileEntity.unwrap()
                            ?.toContainer()
                            ?.get(waystoneNameQuery)?.unwrap()
                            ?.takeIf { it is String }
                            ?.let {
                                val name = it as String
                                val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                                multiSpawnService[name] = location
                                typingPlayerCache[player.uniqueId] = name to location
                                if (mainConfig.sendMessage)
                                    player.sendMessage(
                                        localeService.getTextUnsafe(
                                            "message.set.success",
                                            player,
                                            "name" to { Text.of(name) }
                                        )
                                    )
                            }
                            ?: run { isCancelled = true }
                    }
            }
        }

        pluginContainer.registerListener<MoveEntityEvent.Position> {
            if (!mainConfig.waystoneEnabled) return@registerListener
            val player = targetEntity as? Player ?: return@registerListener
            val uuid = player.uniqueId
            val spawnData = typingPlayerCache[uuid] ?: return@registerListener
            val initName = spawnData.first
            val location = spawnData.second
            location.tileEntity.ifPresent { tileEntity ->
                val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                val name = tileEntity.toContainer()[waystoneNameQuery].unwrap()
                    ?.takeIf { it is String }
                    ?.takeIf { it != initName }
                    ?.let { it as String }
                    ?: return@ifPresent
                multiSpawnService.remove(initName)
                multiSpawnService[name] = location
                typingPlayerCache.invalidate(uuid)
                if (mainConfig.sendMessage)
                    player.sendMessage(
                        localeService.getTextUnsafe(
                            "message.set.success",
                            player,
                            "name" to { Text.of(name) }
                        )
                    )
            }
        }

        pluginContainer.registerListener<ChangeBlockEvent.Break> {
            if (!mainConfig.waystoneEnabled) return@registerListener
            val player = cause.root() as? Player ?: return@registerListener
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
                                "message.remove.success",
                                player,
                                "name" to { Text.of(name) }
                            )
                        )
                }
        }

        pluginContainer.registerListener<InteractBlockEvent.Secondary> {
            if (!mainConfig.waystoneEnabled) return@registerListener
            val player = cause.root() as? Player ?: return@registerListener
            if (!player.hasPermission(MultiSpawn.usePermission)) return@registerListener
            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
            if (mainConfig.createWhenWaystoneActivate) {
                targetBlock
                    .takeIf { it.state.type.id == waystoneBlockId }
                    ?.let { blockSnapshot ->
                        val block = blockSnapshot.state
                        if (baseTrait == null) baseTrait = block.getTrait("base").unwrap() as? BlockTrait<Boolean>
                        baseTrait!!.let {
                            val location = blockSnapshot.location.unwrap()
                            if (block.getTraitValue(it).unwrap() == true)
                                location
                            else
                                location?.getBlockRelative(Direction.DOWN)
                        }
                    }
                    ?.let { location ->
                        location.tileEntity.unwrap()
                            ?.toContainer()
                            ?.get(waystoneNameQuery)?.unwrap()
                            ?.takeIf { it is String }
                            ?.let {
                                val name = it as String
                                multiSpawnService[name] = location
                                typingPlayerCache[player.uniqueId] = name to location
                            }
                    }
            }
            if (!mainConfig.activatedEnabled) return@registerListener

            targetBlock
                .takeIf { it.state.type.id == waystoneBlockId }
                ?.let { it.location.unwrap() }
                ?.let { location ->
                    multiSpawnService.all()
                        .firstOrNull { location.position.distance(it.second.position) < 1.5 }
                        ?.first
                }
                ?.run {
                    val uuid = player.uniqueId
                    if (this in playerStorage[uuid]) return@run
                    playerStorage.add(uuid, this)
                    if (mainConfig.sendMessage)
                        player.sendMessage(
                            localeService.getTextUnsafe(
                                "message.activate",
                                player,
                                "name" to { Text.of(this) }
                            )
                        )
                }
        }

//        pluginContainer.getOrCreate("waystones")
//            ?.registerMessage<MessageEditWaystone>(MessageEditWaystone::class.hashCode()) { message, connection, _ ->
//                if (connection !is PlayerConnection) return@registerMessage
//                val player = connection.player
//                if (!player.hasPermission(MultiSpawn.syncSetPermission)) return@registerMessage
//                val location = Location(player.world, message.pos)
//                val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
//                multiSpawnService.all()
//                    .firstOrNull { location.position.distance(it.second.position) < 1 }
//                    ?.let {
//                        multiSpawnService.remove(it.first)
//                        multiSpawnService[message.name] = it.second
//
//                        if (mainConfig.sendMessage)
//                            player.sendMessage(
//                                localeService.getTextUnsafe(
//                                    player,
//                                    "message.set.success",
//                                    "name" to Function { Text.of(message.name).optional() }
//                                )
//                            )
//                    }
//            }
    }
}
