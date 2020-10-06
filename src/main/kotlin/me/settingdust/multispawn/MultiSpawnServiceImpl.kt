@file:Suppress("UnstableApiUsage")

package me.settingdust.multispawn

import com.flowpowered.math.vector.Vector3d
import com.google.inject.Inject
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.sponge.setProvider
import me.settingdust.multispawn.MultiSpawn.Companion.locationType
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import org.spongepowered.api.event.game.state.GamePostInitializationEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.Comparator
import java.util.UUID

@ExperimentalStdlibApi
class MultiSpawnServiceImpl @Inject constructor(
    private val mainConfig: MainConfig,
    private val spawnStorage: SpawnStorage,
    private val playerStorage: PlayerStorage,
    private val localeService: LocaleService,
    pluginContainer: PluginContainer
) : MultiSpawnService {

    init {
        pluginContainer.registerListener<GamePostInitializationEvent> {
            pluginContainer.setProvider<MultiSpawnService>(this@MultiSpawnServiceImpl)
        }
    }

    override val spawns = spawnStorage.spawns
    override fun all(): Sequence<Pair<String, Location<World>>> =
        spawns.childrenMap.asSequence().map {
            it.key as String to (
                it.value.getValue(locationType)
                    ?: throw ObjectMappingException("${it.key} with wrong location")
                )
        }

    override fun set(name: String, location: Location<World>) {
        super.set(name, location)
        if (mainConfig.warpEnabled) {
            NucleusAPI.getWarpService().ifPresent {
                it.removeWarp(name)
                it.setWarp(name, location, Vector3d.FORWARD)
                it.setWarpCategory(name, "spawns")
                localeService.get(path = "warp.category")
                    ?.let { category -> it.setWarpCategoryDisplayName("spawns", category) }
            }
        }
        spawnStorage.save()
    }

    override fun remove(name: String) {
        super.remove(name)
        if (mainConfig.warpEnabled) {
            NucleusAPI.getWarpService().ifPresent { it.removeWarp(name) }
        }
        spawnStorage.save()
    }

    override fun getClosest(location: Location<World>): Pair<String, Location<World>> {
        val map = spawns.childrenMap
        return all()
            .filter { it.second == location.extent }
            .minWithOrNull(Comparator.comparingDouble { it.second.position.distance(location.position) })
            ?: map.entries.firstOrNull()?.let {
                it.key as String to (
                    it.value.getValue(locationType)
                        ?: throw ObjectMappingException("${it.key} with wrong location")
                    )
            } ?: "" to location.extent.spawnLocation
    }

    override fun getClosest(location: Location<World>, uuid: UUID): Pair<String, Location<World>> {
        val map = spawns.childrenMap
        return all()
            .filter { it.second == location.extent }
            .also { sequence -> if (!mainConfig.activatedEnabled) sequence.filter { it.first in playerStorage[uuid] } }
            .minWithOrNull(Comparator.comparingDouble { it.second.position.distance(location.position) })
            ?: map.entries.firstOrNull()?.let {
                it.key as String to (
                    it.value.getValue(locationType)
                        ?: throw ObjectMappingException("${it.key} with wrong location")
                    )
            } ?: "" to location.extent.spawnLocation
    }
}
