package me.settingdust.multispawn.locale

import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.nucleuspowered.nucleus.api.NucleusAPI
import me.settingdust.laven.configurate.WatchingPropertyResourceBundle
import me.settingdust.laven.configurate.subscribe
import me.settingdust.laven.unwrap
import me.settingdust.multispawn.MainConfig
import me.settingdust.multispawn.MultiSpawn.Companion.watchServiceListener
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.config.ConfigDir
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.ServiceManager
import org.spongepowered.api.text.Text
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.PropertyResourceBundle
import java.util.function.Function

@Singleton
@ExperimentalStdlibApi
class LocaleServiceImpl @Inject constructor(
    @ConfigDir(sharedRoot = false) configDir: Path,
    private val serviceManager: ServiceManager,
    mainConfig: MainConfig,
    pluginContainer: PluginContainer
) : LocaleService {
    private var resourceBundle: WatchingPropertyResourceBundle

    init {
        val overridePath = configDir.resolve("messages.lang")
        if (!Files.exists(overridePath)) Files.createFile(overridePath)
        resourceBundle = overridePath.subscribe(watchServiceListener)
        val fallbackAsset = pluginContainer.getAsset(
            "lang/${mainConfig.language}.lang"
        ).unwrap() ?: pluginContainer.getAsset("lang/en_us.lang").unwrap()
            ?: throw NoSuchElementException("Can't find language file")
        resourceBundle.setParent(PropertyResourceBundle(fallbackAsset.url.openStream().bufferedReader()))
    }

    override fun get(
        source: CommandSource,
        path: String,
        vararg tokens: Pair<String, Function<CommandSource, Optional<Text>>>
    ): Text? {
        if (resourceBundle.containsKey(path)) {
            val templateFactory = NucleusAPI.getTextTemplateFactory()
            val textTemplate = templateFactory.createFromString(resourceBundle.getString(path))

            return textTemplate.getForCommandSource(
                source,
                tokens.asSequence().toMap()
            )
        }

        return null
    }
}
