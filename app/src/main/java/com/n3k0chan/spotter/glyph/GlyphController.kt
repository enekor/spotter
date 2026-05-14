package com.n3k0chan.spotter.glyph

import android.content.ComponentName
import android.content.Context
import com.nothing.ketchum.GlyphException
import com.nothing.ketchum.GlyphManager

class GlyphController(private val context: Context) {

    private var glyphManager: GlyphManager? = null
    private var isSessionOpen = false

    fun init(onReady: () -> Unit) {
        try {
            val manager = GlyphManager.getInstance(context)
            glyphManager = manager

            manager.init(object : GlyphManager.Callback {
                override fun onServiceConnected(componentName: ComponentName?) {
                    try {
                        manager.register()
                        manager.openSession()
                        isSessionOpen = true
                        onReady()
                    } catch (_: GlyphException) {
                    } catch (_: Exception) {
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName?) {
                    isSessionOpen = false
                }
            })
        } catch (_: Exception) {
        }
    }

    fun showProgress(progress: Int, reverse: Boolean = true) {
        if (!isSessionOpen) return
        try {
            val manager = glyphManager ?: return
            val frame = manager.glyphFrameBuilder
                .buildChannelC()
                .build()
            manager.displayProgress(frame, progress.coerceIn(0, 100), reverse)
        } catch (_: Exception) {
        }
    }

    fun turnOff() {
        if (!isSessionOpen) return
        try {
            glyphManager?.turnOff()
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            turnOff()
            if (isSessionOpen) {
                glyphManager?.closeSession()
            }
            glyphManager?.unInit()
            isSessionOpen = false
            glyphManager = null
        } catch (_: Exception) {
        }
    }
}
