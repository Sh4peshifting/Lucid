package io.github.sh4peshifting.panelawake

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @Test fun launchesWithoutShizukuAndSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertNotNull(it.window.decorView) }
            scenario.recreate()
            scenario.onActivity { assertFalse(it.isFinishing) }
        }
    }
}
