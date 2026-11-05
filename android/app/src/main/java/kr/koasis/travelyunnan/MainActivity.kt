package kr.koasis.travelyunnan

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.koasis.travelyunnan.ui.AppViewModel
import kr.koasis.travelyunnan.ui.nav.AppNav
import kr.koasis.travelyunnan.ui.theme.TravelTheme

class MainActivity : FragmentActivity() {
    companion object { const val EXTRA_ROUTE = "route" }
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val route = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            TravelTheme(bigFont = settings.bigFont) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                    AppNav(vm, route)
                }
            }
        }
    }
}
