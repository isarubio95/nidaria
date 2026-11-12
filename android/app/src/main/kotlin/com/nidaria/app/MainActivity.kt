package com.nidaria.app

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.nidaria.app.scene.RoomView
import com.nidaria.app.ui.App

// Una sola pantalla: la habitación 3D al fondo y la interfaz encima.
class MainActivity : ComponentActivity() {
    private lateinit var room: RoomView
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        val clear = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = clear, navigationBarStyle = clear)
        super.onCreate(savedInstanceState)
        room = RoomView(this)
        game = Game(applicationContext, room, lifecycleScope)
        val ui = ComposeView(this).apply { setContent { App(game) } }
        setContentView(
            FrameLayout(this).apply {
                addView(room, MATCH_PARENT, MATCH_PARENT)
                addView(ui, MATCH_PARENT, MATCH_PARENT)
            },
        )
        game.boot()
    }

    override fun onResume() {
        super.onResume()
        room.onResume()
        game.onResume()
    }

    override fun onPause() {
        game.onPause()
        room.onPause()
        super.onPause()
    }
}
