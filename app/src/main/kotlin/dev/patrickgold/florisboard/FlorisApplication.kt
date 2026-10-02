/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.core.os.UserManagerCompat
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.clipboard.ClipboardManager
import dev.patrickgold.florisboard.ime.core.SubtypeManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.editor.EditorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyboardManager
import dev.patrickgold.florisboard.ime.media.emoji.FlorisEmojiCompat
import dev.patrickgold.florisboard.ime.nlp.NlpManager
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingManager
import dev.patrickgold.florisboard.ime.theme.ThemeManager
import dev.patrickgold.florisboard.lib.cache.CacheManager
import dev.patrickgold.florisboard.lib.crashutility.CrashUtility
import dev.patrickgold.florisboard.lib.devtools.Flog
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogError
import dev.patrickgold.florisboard.lib.ext.ExtensionManager
import dev.patrickgold.jetpref.datastore.runtime.initAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.florisboard.lib.kotlin.io.deleteContentsRecursively
import org.florisboard.lib.kotlin.tryOrNull
import org.florisboard.libnative.dummyAdd
import java.lang.ref.WeakReference

/**
 * Global weak reference for the [FlorisApplication] class. This is needed as in certain scenarios an application
 * reference is needed, but the Android framework hasn't finished setting up
 */
private var FlorisApplicationReference = WeakReference<FlorisApplication?>(null)

/**
 * Host paints the Suggest sheet over smartbar + keys. Must not start an
 * Activity — that would hide the keyboard and drop field focus. The sheet
 * covers the IME in place (same height; host app does not resize).
 */
fun interface HostSuggestSheetRenderer {
    @Composable
    fun Content(onChip: (String) -> Unit, onDismiss: () -> Unit)
}

@Suppress("unused")
open class FlorisApplication : Application() {
    companion object {
        init {
            try {
                System.loadLibrary("fl_native")
            } catch (_: Throwable) {
            }
        }
    }

    private val mainHandler by lazy { Handler(mainLooper) }
    private val scope = CoroutineScope(Dispatchers.Default)
    val preferenceStoreLoaded = MutableStateFlow(false)

    /**
     * Hosts that embed this library can skip JetPref / native / crash-handler
     * startup in processes that are not the IME (e.g. a React Native shell).
     */
    open fun shouldStartFlorisRuntime(): Boolean = true

    /**
     * Hook for the host to sync RN-owned keyboard_shared_config into JetPref.
     * Called after prefs init and from [FlorisImeService.onStartInputView].
     * [packageName] / [fieldId] identify the focused editor so the host can
     * drop a draft-language lock when the user switches fields.
     */
    open fun onImeStartInputView(packageName: String? = null, fieldId: Int = 0) {}

    /**
     * Called from [FlorisImeService.onFinishInputView] so the host can drop
     * pinned AI chips when the keyboard hides.
     */
    open fun onImeFinishInputView() {}

    /**
     * Host override for keypress haptics. Null = use JetPref only.
     * False must suppress vibration even if JetPref still says enabled.
     */
    open fun hostHapticFeedbackEnabled(): Boolean? = null

    /**
     * Host override for keypress sounds. Null = use JetPref only.
     */
    open fun hostAudioFeedbackEnabled(): Boolean? = null

    /**
     * Host hook for the smartbar Suggest action. Must not hide the IME —
     * chat capture needs the foreground app window to stay visible.
     */
    open fun onHostSuggestRequested() {}

    /**
     * Host hook for the keyboard mic key. Launch dictation in the shell
     * process — SpeechRecognizer inside `:keyboard` binds the stub
     * [RecognitionService] or fails with ERROR_CLIENT.
     */
    open fun onHostVoiceInputRequested() {}

    /**
     * Text captured by the host dictation activity while the IME was hidden.
     * Consuming must clear the pending value.
     */
    open fun takePendingVoiceDictation(): String? = null

    /**
     * Host hook for the smartbar Fix grammar action. Stays in the IME —
     * rewrites the current field without opening the chat overlay.
     */
    open fun onHostGrammarRequested() {}

    open fun onHostRewriteRequested() {}

    open fun onHostTranslateRequested() {}

    open fun onHostDetectAiRequested() {}

    open fun onHostHumanizeRequested() {}

    /**
     * Host hook when a nested writer-tool child chip is tapped.
     */
    open fun onHostWriterVariantRequested(id: String, label: String) {}

    /** Host hook for the add chip on a writer variant row. */
    open fun onHostWriterCatalogRequested() {}

    /**
     * Host hook when a catalog chip is chosen. Saves it onto the user's
     * writer preferences for the open mode.
     */
    open fun onHostWriterChipAdded(id: String) {}

    /**
     * Host hook for back from nested writer variants or results.
     */
    open fun onHostWriterBackRequested() {}

    /** Restore the newest writer commit through the host replace path. */
    open fun onHostWriterUndoRequested() {}

    /** Walk back toward the latest writer commit. */
    open fun onHostWriterRedoRequested() {}

    /**
     * Host hook when a writer-transform chip is tapped. Return true if the
     * host handled the chip (skip default commitCompletion).
     */
    open fun onHostGrammarChipAccepted(text: String): Boolean = false

    /**
     * Host paints the shared Suggest sheet over smartbar + keys for Suggest
     * and writer RESULTS. Null falls back to
     * [dev.patrickgold.florisboard.ime.smartbar.WriterSuggestionsPanel].
     */
    @Volatile
    var hostSuggestSheet: HostSuggestSheetRenderer? = null

    val cacheManager = lazy { CacheManager(this) }
    val clipboardManager = lazy { ClipboardManager(this) }
    val editorInstance = lazy { EditorInstance(this) }
    val extensionManager = lazy { ExtensionManager(this) }
    val glideTypingManager = lazy { GlideTypingManager(this) }
    val keyboardManager = lazy { KeyboardManager(this) }
    val nlpManager = lazy { NlpManager(this) }
    val subtypeManager = lazy { SubtypeManager(this) }
    val themeManager = lazy { ThemeManager(this) }

    override fun onCreate() {
        super.onCreate()
        FlorisApplicationReference = WeakReference(this)
        if (!shouldStartFlorisRuntime()) {
            return
        }
        try {
            Flog.install(
                context = this,
                isFloggingEnabled = BuildConfig.DEBUG,
                flogTopics = LogTopic.ALL,
                flogLevels = Flog.LEVEL_ALL,
                flogOutputs = Flog.OUTPUT_CONSOLE,
            )
            CrashUtility.install(this)
            FlorisEmojiCompat.init(this)
            flogError { "dummy result: ${dummyAdd(3,4)}" }

            if (!UserManagerCompat.isUserUnlocked(this)) {
                cacheDir?.deleteContentsRecursively()
                extensionManager.value.init()
                registerReceiver(BootComplete(), IntentFilter(Intent.ACTION_USER_UNLOCKED))
                return
            }

            init()
        } catch (e: Exception) {
            CrashUtility.stageException(e)
            return
        }
    }

    fun init() {
        cacheDir?.deleteContentsRecursively()
        scope.launch {
            val result = FlorisPreferenceStore.initAndroid(
                context = this@FlorisApplication,
                datastoreName = FlorisPreferenceModel.NAME,
            )
            Log.i("PREFS", result.toString())
            preferenceStoreLoaded.value = true
            onImeStartInputView()
        }
        extensionManager.value.init()
        clipboardManager.value.initializeForContext(this)
        DictionaryManager.init(this)
    }

    private inner class BootComplete : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (intent.action == Intent.ACTION_USER_UNLOCKED) {
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {
                    flogError { e.toString() }
                }
                mainHandler.post { init() }
            }
        }
    }
}

private tailrec fun Context.florisApplication(): FlorisApplication {
    return when (this) {
        is FlorisApplication -> this
        is ContextWrapper -> when {
            this.baseContext != null -> this.baseContext.florisApplication()
            else -> FlorisApplicationReference.get()!!
        }
        else -> tryOrNull { this.applicationContext as FlorisApplication } ?: FlorisApplicationReference.get()!!
    }
}

fun Context.appContext() = lazyOf(this.florisApplication())

fun Context.cacheManager() = this.florisApplication().cacheManager

fun Context.clipboardManager() = this.florisApplication().clipboardManager

fun Context.editorInstance() = this.florisApplication().editorInstance

fun Context.extensionManager() = this.florisApplication().extensionManager

fun Context.glideTypingManager() = this.florisApplication().glideTypingManager

fun Context.keyboardManager() = this.florisApplication().keyboardManager

fun Context.nlpManager() = this.florisApplication().nlpManager

fun Context.subtypeManager() = this.florisApplication().subtypeManager

fun Context.themeManager() = this.florisApplication().themeManager
