package me.settingdust.multispawn.locale

import com.google.inject.ImplementedBy
import org.spongepowered.api.Sponge
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.serializer.TextSerializers
import java.util.Optional
import java.util.function.Function

@ExperimentalStdlibApi
@ImplementedBy(LocaleServiceImpl::class)
interface LocaleService {
    companion object {
        private const val keyIsMissing = "missingKey"
        private const val keyIsMissingString = "[Missing language key: {{path}}]"
        private val ketIsMissingText: Text = Text.of(keyIsMissingString)
    }

    operator fun get(
        source: CommandSource = Sponge.getServer().console,
        path: String,
        vararg tokens: Pair<String, Function<CommandSource, Optional<Text>>>
    ): Text?

    fun getString(
        source: CommandSource = Sponge.getServer().console,
        path: String,
        vararg tokens: Pair<String, Function<CommandSource, Optional<Text>>>
    ): String? =
        get(source, path, *tokens)?.let { TextSerializers.FORMATTING_CODE.serialize(it) }

    fun getTextUnsafe(
        source: CommandSource = Sponge.getServer().console,
        path: String,
        vararg tokens: Pair<String, Function<CommandSource, Optional<Text>>>
    ): Text =
        get(source, path, *tokens) ?: get(source, keyIsMissing, *tokens) ?: ketIsMissingText

    fun getStringUnsafe(
        source: CommandSource = Sponge.getServer().console,
        path: String,
        vararg tokens: Pair<String, Function<CommandSource, Optional<Text>>>
    ): String =
        getString(source, path, *tokens) ?: getString(source, keyIsMissing, *tokens)
            ?: keyIsMissingString
}
