package com.xern.xrnchips

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.xern.xrnchips.ui.ChipsRoot
import com.xern.xrnchips.ui.TableModel

class MainActivity : ComponentActivity() {
    private val table: TableModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark throughout, so the system bars keep light icons over it.
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        setContent { ChipsRoot(table) }
    }

    override fun onStop() {
        super.onStop()
        table.flush()
    }
}
