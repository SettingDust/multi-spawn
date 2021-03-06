package me.settingdust.multispawn

import com.google.inject.Inject
import me.settingdust.laven.sponge.commandSpec
import me.settingdust.laven.sponge.provideUnchecked
import me.settingdust.laven.sponge.registerListener
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MultiSpawn.Companion.commandListPermission
import me.settingdust.multispawn.MultiSpawn.Companion.commandRemovePermission
import me.settingdust.multispawn.MultiSpawn.Companion.commandSetPermission
import me.settingdust.multispawn.MultiSpawn.Companion.locationType
import me.settingdust.multispawn.MultiSpawn.Companion.usePermission
import me.settingdust.multispawn.api.MultiSpawnService
import me.settingdust.multispawn.locale.LocaleService
import ninja.leaping.configurate.objectmapping.ObjectMappingException
import org.spongepowered.api.command.CommandManager
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.args.GenericArguments.choices
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.game.state.GameStartingServerEvent
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.action.TextActions
import org.spongepowered.api.text.format.TextColors
import org.spongepowered.api.world.TeleportHelper
import org.spongepowered.api.world.teleport.TeleportHelperFilters
import kotlin.io.path.ExperimentalPathApi

@ExperimentalPathApi
@Suppress("UnstableApiUsage")
@ExperimentalStdlibApi
class PluginCommandManager @Inject constructor(
    commandManager: CommandManager,
    pluginContainer: PluginContainer,
    serviceManager: ServiceManager,
    teleportHelper: TeleportHelper,
    mainConfig: MainConfig,
    localeService: LocaleService
) {
    init {
        pluginContainer.registerListener<GameStartingServerEvent> {
            commandManager.register(
                pluginContainer,
                commandSpec(
                    permission = usePermission,
                    description = localeService.getTextUnsafe(path = "command.spawn.description"),
                    children = mapOf(
                        listOf("list", "ls", "l") to commandSpec(
                            permission = commandListPermission,
                            description = localeService.getTextUnsafe(path = "command.list.description")
                        ) { source, _ ->
                            val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                            val spawns = multiSpawnService.spawns.childrenMap
                            if (spawns.isNotEmpty()) {
                                source.sendMessages(
                                    spawns.asSequence()
                                        .map {
                                            it.key as String to (
                                                it.value.getValue(locationType)
                                                    ?: throw ObjectMappingException("Wrong data in ${it.key}")
                                                )
                                        }
                                        .mapNotNull { spawn ->
                                            Text.builder("${spawn.first} ")
                                                .color(TextColors.GREEN)
                                                .append(
                                                    Text.builder("${spawn.second.position} ${spawn.second.extent.name}")
                                                        .color(TextColors.YELLOW)
                                                        .build()
                                                )
                                                .onHover(
                                                    TextActions.showText(
                                                        localeService.getTextUnsafe(
                                                            path = "command.list.hover",
                                                            tokens = arrayOf(
                                                                "name" to {
                                                                    Text.of(spawn.first)
                                                                }
                                                            )
                                                        )
                                                    )
                                                )
                                                .onClick(
                                                    TextActions.executeCallback {
                                                        if (it !is Player) return@executeCallback
                                                        it.setLocationSafely(spawn.second)
                                                    }
                                                )
                                                .build()
                                        }.toList()
                                )
                                CommandResult.success()
                            } else {
                                source.sendMessage(localeService.getTextUnsafe(path = "command.list.isEmpty"))
                                CommandResult.empty()
                            }
                        },
                        listOf("set", "s") to commandSpec(
                            description = localeService.getTextUnsafe(path = "command.set.description"),
                            extendedDescription = null,
                            permission = commandSetPermission,
                            choices(
                                Text.of("name"),
                                {
                                    val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                                    multiSpawnService.all().map { it.first }.toSet()
                                },
                                { it }
                            )
                        ) { source, context ->
                            if (source is Player) {
                                context.getOne<String>("name")
                                    .ifPresent { name ->
                                        val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                                        multiSpawnService[name] = source.location
                                        source.sendMessage(
                                            localeService.getTextUnsafe(
                                                "command.set.success",
                                                source,
                                                "name" to { Text.of(name) }
                                            )
                                        )
                                    }
                            } else {
                                source.sendMessage(localeService.getTextUnsafe("command.error.onlyPlayer", source))
                            }
                            CommandResult.empty()
                        },
                        listOf("remove", "rm", "r") to commandSpec(
                            description = localeService.getTextUnsafe(path = "command.remove.description"),
                            extendedDescription = null,
                            permission = commandRemovePermission,
                            choices(
                                Text.of("name"),
                                {
                                    val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                                    multiSpawnService.all()
                                        .map { it.first }
                                        .map { if (it.contains(" ")) "'$it'" else it }
                                        .toSet()
                                },
                                { it }
                            )
                        ) { source, context ->
                            context.getOne<String>("name")
                                .ifPresent { name ->
                                    val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                                    multiSpawnService.remove(name)
                                    source.sendMessage(
                                        localeService.getTextUnsafe(
                                            "command.remove.success",
                                            source,
                                            "name" to { Text.of(name) }
                                        )
                                    )
                                }
                            CommandResult.empty()
                        }
                    )
                ) { source, _ ->
                    if (source is Player) {
                        val multiSpawnService = serviceManager.provideUnchecked<MultiSpawnService>()
                        val closest = multiSpawnService.getClosest(source)
                        source.location = closest
                            .run {
                                teleportHelper.getSafeLocationWithBlacklist(
                                    second,
                                    3,
                                    9,
                                    2,
                                    TeleportHelperFilters.DEFAULT,
                                    MultiSpawn.waystoneTeleportHelperFilter
                                ).unwrap() ?: second
                            }
                        if (mainConfig.sendMessage)
                            source.sendMessage(
                                localeService.getTextUnsafe(
                                    "message.respawn",
                                    source,
                                    "name" to { Text.of(closest.first) }
                                )
                            )
                        CommandResult.success()
                    } else
                        CommandResult.empty()
                },
                "spawn",
                "mspawn",
                "multispawn"
            )
        }
    }
}
