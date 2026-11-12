package pl.sinusoide.sinuschat.desktop.ui.icons

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

object VectorIcons {

    class Source(val viewportWidth: Float, val viewportHeight: Float, val paths: List<Pair<String, Boolean>>)

    private val sources = ConcurrentHashMap<String, Source>()
    private val vectors = ConcurrentHashMap<String, ImageVector>()

    private val pathTag = Regex("<path\\b([^>]*)>", RegexOption.DOT_MATCHES_ALL)

    private fun attr(text: String, name: String): String? =
        Regex("android:$name\\s*=\\s*\"([^\"]*)\"").find(text)?.groupValues?.get(1)

    fun source(name: String): Source = sources.getOrPut(name) {
        val xml = VectorIcons::class.java.getResourceAsStream("/icons/$name.xml")
            ?.use { it.readBytes().decodeToString() }
            ?: error("missing icon $name")
        Source(
            attr(xml, "viewportWidth")?.toFloat() ?: 24f,
            attr(xml, "viewportHeight")?.toFloat() ?: 24f,
            pathTag.findAll(xml).mapNotNull { m ->
                val tag = m.groupValues[1]
                attr(tag, "pathData")?.let { it to (attr(tag, "fillType") == "evenOdd") }
            }.toList()
        )
    }

    fun vector(name: String): ImageVector = vectors.getOrPut(name) {
        val src = source(name)
        ImageVector.Builder(name, 24.dp, 24.dp, src.viewportWidth, src.viewportHeight).apply {
            for ((data, evenOdd) in src.paths) {
                addPath(
                    PathParser().parsePathString(data).toNodes(),
                    pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                    fill = SolidColor(Color.White)
                )
            }
        }.build()
    }

    fun path(name: String): Path = Path().apply {
        for ((data, _) in source(name).paths) addPath(PathParser().parsePathString(data).toPath())
    }

    fun shapes(name: String): List<Path> =
        source(name).paths.flatMap { (data, _) ->
            data.split(Regex("(?=M)")).filter { it.isNotBlank() }
                .map { PathParser().parsePathString(it).toPath() }
        }
}

@Composable
fun SinusIcon(name: String, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Icon(rememberVectorPainter(VectorIcons.vector(name)), contentDescription, modifier, tint)
}
