package pl.sinusoide.sinuschat.desktop

import pl.sinusoide.sinuschat.desktop.ui.icons.VectorIcons
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class AppIconsTest {

    @Test
    fun everyAppIconLoads() {
        val dir = File("src/jvmMain/resources/icons")
        val names = dir.listFiles { f -> f.name.endsWith(".xml") }.orEmpty().map { it.name.removeSuffix(".xml") }
        assertTrue(names.size > 100, "za mało ikon: ${names.size}")
        for (name in names) {
            val src = VectorIcons.source(name)
            assertTrue(src.paths.isNotEmpty(), "pusta ikona $name")
            VectorIcons.vector(name)
        }
    }

    @Test
    fun sameAsPhoneApp() {
        val app = File("../../AndroidStudioProjects/SC/app/src/main/res/drawable")
        if (!app.isDirectory) return
        val dir = File("src/jvmMain/resources/icons")
        for (f in dir.listFiles { x -> x.name.endsWith(".xml") }.orEmpty()) {
            val twin = File(app, f.name)
            if (!twin.exists()) continue
            assertTrue(f.readBytes().contentEquals(twin.readBytes()), "ikona ${f.name} różni się od aplikacji")
        }
    }
}
