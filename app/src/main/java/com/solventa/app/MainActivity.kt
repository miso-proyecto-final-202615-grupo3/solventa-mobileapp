package com.solventa.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.solventa.app.core.designsystem.theme.SolventaTheme
import com.solventa.app.navigation.SolventaNavHost

/** FragmentActivity porque BiometricPrompt la requiere para mostrar el aviso del sistema. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SolventaApplication).container
        setContent {
            SolventaTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SolventaNavHost(
                        container = container,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
