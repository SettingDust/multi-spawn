@file:Suppress("UnstableApiUsage")

package me.settingdust.multispawn.api

import me.settingdust.multispawn.MultiSpawn.Companion.locationType
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.kotlin.contains
import ninja.leaping.configurate.kotlin.get
import ninja.leaping.configurate.kotlin.set
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.world.Location
import org.spongepowered.api.world.World
import java.util.UUID

interface MultiSpawnService {
    /**
     * All spawns.
     */
    val spawns: ConfigurationNode

    fun all(): Sequence<Pair<String, Location<World>>>

    operator fun set(name: String, location: Location<World>) {
        spawns[name] = location
    }

    operator fun contains(name: String): Boolean = name in spawns

    @ExperimentalStdlibApi
    operator fun get(name: String): Location<World>? = spawns[name].getValue(locationType)

    fun remove(name: String) {
        spawns.removeChild(name)
    }

    /**
     * @return The closest point.
     */
    fun getClosest(location: Location<World>): Pair<String, Location<World>>

    /**
     * @return The closest point to the user. The first point if nothing is activated.
     */
    fun getClosest(location: Location<World>, uuid: UUID): Pair<String, Location<World>>

    fun getClosest(player: Player) = getClosest(player.location, player.uniqueId)
}
