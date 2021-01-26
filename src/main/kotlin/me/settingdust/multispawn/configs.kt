@file:Suppress("UnstableApiUsage")

package me.settingdust.multispawn

import com.google.common.reflect.TypeToken
import com.google.inject.Inject
import com.google.inject.Injector
import com.google.inject.Singleton
import me.settingdust.laven.configurate.subscribeAsConfiguration
import me.settingdust.laven.get
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.typeTokenOf
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MultiSpawn.Companion.watchServiceListener
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.ConfigurationOptions
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import ninja.leaping.configurate.kotlin.contains
import ninja.leaping.configurate.kotlin.get
import ninja.leaping.configurate.kotlin.set
import ninja.leaping.configurate.loader.ConfigurationLoader
import ninja.leaping.configurate.objectmapping.serialize.TypeSerializer
import ninja.leaping.configurate.reference.ConfigurationReference
import org.spongepowered.api.Sponge
import org.spongepowered.api.block.BlockTypes
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.config.DefaultConfig
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.event.game.state.GamePreInitializationEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.div

@Singleton
@ExperimentalPathApi
@ExperimentalStdlibApi
class ConfigManager @Inject constructor(
    pluginContainer: PluginContainer,
    injector: Injector
) {
    init {
        pluginContainer.registerListener<GamePreInitializationEvent> {
            injector[typeTokenOf<MainConfig>()]
            injector[typeTokenOf<PlayerStorage>()]
            injector[typeTokenOf<SpawnStorage>()]
            injector[typeTokenOf<MultiSpawnServiceImpl>()]
        }
    }
}

@ExperimentalPathApi
@ExperimentalStdlibApi
class MainConfig @Inject constructor(
    pluginContainer: PluginContainer,
    @DefaultConfig(sharedRoot = false) configPath: Path
) : Config<CommentedConfigurationNode>(
    configPath,
    {
        HoconConfigurationLoader
            .builder()
            .setPath(configPath)
            .setDefaultOptions(ConfigurationOptions.defaults().withShouldCopyDefaults(true))
            .build()
    }
) {
    val language: String
        get() = reference["language"].getString(Locale.getDefault().toString().toLowerCase())

    val sendMessage
        get() = reference["sendMessage"].getBoolean(true)

    val warpEnabled
        get() = reference["warpEnabled"].getBoolean(true)

    val waystoneEnabled
        get() = reference["waystoneEnabled"].getBoolean(true)

    val activatedEnabled
        get() = reference["activatedEnabled"].getBoolean(true)

    val blocksToSync: List<String>
        get() = reference["blocksToSync"].getList(typeTokenOf<String>(), listOf(BlockTypes.BEACON.id))

    val createWhenWaystoneActivate: Boolean
        get() = reference["create spawn when waystone activating"].getBoolean(true)

    init {
        language
        sendMessage
        warpEnabled
        waystoneEnabled
        activatedEnabled
        blocksToSync

        reference["warpEnabled"].setCommentIfAbsent("Sync with Nucleus warp")
        reference["waystoneEnabled"].setCommentIfAbsent("Sync with Waystone(https://minecraft.curseforge.com/projects/waystones)")
        reference["activatedEnabled"].setCommentIfAbsent("Have to activate(Move to area nearby the spawn point) before respawn")
        reference["blocksToSync"].setCommentIfAbsent("Blocks to sync with the spawn point")
        reference["create spawn when waystone activating"].setCommentIfAbsent("Use for generated waystones. Need the player has spawn.use permission")

        pluginContainer.registerListener<GamePostInitializationEvent> { save() }
    }
}

@Singleton
@ExperimentalPathApi
@ExperimentalStdlibApi
class SpawnStorage private constructor(
    configDir: Path,
    configPath: Path
) : Config<CommentedConfigurationNode>(
    configPath,
    {
        HoconConfigurationLoader
            .builder()
            .setDefaultOptions(
                ConfigurationOptions.defaults().withSerializers {
                    it.register(typeTokenOf(), LocationSerializer())
                }
            )
            .setPath(configPath)
            .build()
    }
) {
    @ExperimentalPathApi
    @Inject
    constructor(@ConfigDir(sharedRoot = false) configDir: Path) : this(configDir, configDir / "spawns.conf")

    val spawns: ConfigurationNode = reference.node
}

@Singleton
@ExperimentalStdlibApi
@ExperimentalPathApi
class PlayerStorage private constructor(
    configDir: Path,
    configPath: Path
) : Config<CommentedConfigurationNode>(
    configPath,
    {
        HoconConfigurationLoader
            .builder()
            .setPath(configPath)
            .build()
    }
) {
    val players: CommentedConfigurationNode = reference.node

    @Inject
    constructor(@ConfigDir(sharedRoot = false) configDir: Path) : this(configDir, configDir / "players.conf")

    operator fun get(uuid: UUID): List<String> = players[uuid].getList(typeTokenOf<String>(), listOf())

    fun add(uuid: UUID, point: String) {
        players[uuid] = get(uuid).toMutableList() + point
    }
}

@ExperimentalPathApi
@ExperimentalStdlibApi
abstract class Config<N : ConfigurationNode> constructor(
    path: Path,
    loaderFunc: (Path) -> ConfigurationLoader<N>
) {
    protected var reference: ConfigurationReference<N> = path.subscribeAsConfiguration(watchServiceListener, loaderFunc)

    open fun save() = reference.save()
}

class LocationSerializer : TypeSerializer<Location<World>> {
    override fun deserialize(type: TypeToken<*>, value: ConfigurationNode): Location<World>? =
        value["worldId"].getValue(typeTokenOf<UUID>())
            ?.let { Sponge.getServer().getWorld(it).unwrap() }
            ?.let {
                if ("x" in value && "y" in value && "z" in value)
                    Location(it, value["x"].double, value["y"].double, value["z"].double)
                else null
            }

    override fun serialize(type: TypeToken<*>, obj: Location<World>?, value: ConfigurationNode) {
        obj?.let {
            value["worldId"] = it.extent.uniqueId
            value["x"] = it.x
            value["y"] = it.y
            value["z"] = it.z
        }
    }
}
