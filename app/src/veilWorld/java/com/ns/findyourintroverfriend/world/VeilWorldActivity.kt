package com.ns.findyourintroverfriend.world

import android.app.Application
import android.content.Intent
import android.os.Process
import androidx.annotation.Keep
import com.ns.findyourintroverfriend.BuildConfig
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotActivity
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.UsedByGodot
import java.io.File

/**
 * Runs the world in its own process so shutting down Godot cannot close Explore.
 */
class VeilWorldActivity : GodotActivity() {
    private val worldIdToken by lazy {
        intent.getStringExtra("worldIdToken").orEmpty().also {
            intent.removeExtra("worldIdToken")
        }
    }

    override fun onGodotForceQuit(instance: Godot) {
        runOnUiThread { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && Application.getProcessName() == "$packageName:veil_world") {
            // Start a fresh engine next time without removing the task that hosts Explore.
            Process.killProcess(Process.myPid())
        }
    }

    override fun getCommandLine(): MutableList<String> {
        val onlineUrl = BuildConfig.VEIL_WORLD_ONLINE_URL
        val pack = File(cacheDir, "veil_world.pck")
        assets.open("veil_world.pck").use { input ->
            pack.outputStream().use { output -> input.copyTo(output) }
        }
        val alias = intent.getStringExtra("worldAlias").orEmpty()
        return super.getCommandLine().toMutableList().apply {
            addAll(listOf("--main-pack", pack.absolutePath, "--", "--alias=${alias.take(28)}"))
            add("--journal=" + intent.getStringExtra("worldJournal").orEmpty())
            if (intent.getBooleanExtra("worldLight", false)) add("--light-ui")
            if (onlineUrl.isNotEmpty()) {
                add("--connect=$onlineUrl")
            } else if (BuildConfig.DEBUG && BuildConfig.VEIL_WORLD_HOST.isNotBlank()) {
                add("--connect=${BuildConfig.VEIL_WORLD_HOST}")
            }
        }
    }

    override fun getHostPlugins(engine: Godot): Set<GodotPlugin> =
        setOf(object : GodotPlugin(engine) {
            override fun getPluginName(): String = "VeilWorldBridge"

            @Keep
            @UsedByGodot
            fun getWorldIdToken(): String = worldIdToken

            @Keep
            @UsedByGodot
            fun openWhisper() {
                this@VeilWorldActivity.runOnUiThread {
                    setResult(RESULT_OK, Intent().putExtra("openWhisper", true))
                    finish()
                }
            }

            @Keep
            @UsedByGodot
            fun closeWorld() {
                this@VeilWorldActivity.runOnUiThread { finish() }
            }
        })
}
