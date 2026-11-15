package com.uade.vigia.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Íconos de la navegación, con los mismos trazos que el prototipo en Figma (24 × 24, trazo 2). */
object VigiaIcons {
    val Panel = stroke(
        "Panel",
        "M4 3H9C9.55228 3 10 3.44772 10 4V11C10 11.5523 9.55228 12 9 12H4C3.44772 12 3 11.5523 3 11V4C3 3.44772 3.44772 3 4 3Z",
        "M15 3H20C20.5523 3 21 3.44772 21 4V7C21 7.55228 20.5523 8 20 8H15C14.4477 8 14 7.55228 14 7V4C14 3.44772 14.4477 3 15 3Z",
        "M15 12H20C20.5523 12 21 12.4477 21 13V20C21 20.5523 20.5523 21 20 21H15C14.4477 21 14 20.5523 14 20V13C14 12.4477 14.4477 12 15 12Z",
        "M4 16H9C9.55228 16 10 16.4477 10 17V20C10 20.5523 9.55228 21 9 21H4C3.44772 21 3 20.5523 3 20V17C3 16.4477 3.44772 16 4 16Z"
    )
    val Route = stroke(
        "Route",
        "M9 19C9 20.6569 7.65685 22 6 22C4.34315 22 3 20.6569 3 19C3 17.3431 4.34315 16 6 16C7.65685 16 9 17.3431 9 19Z",
        "M21 5C21 6.65685 19.6569 8 18 8C16.3431 8 15 6.65685 15 5C15 3.34315 16.3431 2 18 2C19.6569 2 21 3.34315 21 5Z",
        "M9 19H17.5C18.4283 19 19.3185 18.6313 19.9749 17.9749C20.6313 17.3185 21 16.4283 21 15.5C21 14.5717 20.6313 13.6815 19.9749 13.0251C19.3185 12.3687 18.4283 12 17.5 12H6.5C5.57174 12 4.6815 11.6313 4.02513 10.9749C3.36875 10.3185 3 9.42826 3 8.5C3 7.57174 3.36875 6.6815 4.02513 6.02513C4.6815 5.36875 5.57174 5 6.5 5H15"
    )
    val Bell = stroke(
        "Bell",
        "M6 8C6 6.4087 6.63214 4.88258 7.75736 3.75736C8.88258 2.63214 10.4087 2 12 2C13.5913 2 15.1174 2.63214 16.2426 3.75736C17.3679 4.88258 18 6.4087 18 8C18 15 21 17 21 17H3C3 17 6 15 6 8Z",
        "M10.3 21C10.4674 21.3044 10.7134 21.5583 11.0125 21.7352C11.3115 21.912 11.6526 22.0053 12 22.0053C12.3474 22.0053 12.6885 21.912 12.9875 21.7352C13.2866 21.5583 13.5326 21.3044 13.7 21"
    )
    val Clock = stroke(
        "Clock",
        "M22 12C22 17.5228 17.5228 22 12 22C6.47715 22 2 17.5228 2 12C2 6.47715 6.47715 2 12 2C17.5228 2 22 6.47715 22 12Z",
        "M12 6V12L16 14"
    )
    val Sliders = stroke(
        "Sliders",
        "M4 21V14M4 14H1M4 14H7M4 10V3M12 21V12M12 8V3M12 8H9M12 8H15M20 21V16M20 16H17M20 16H23M20 12V3"
    )
    val Check = stroke("Check", "M5 12.5L10 17.5L19 7.5")

    private fun stroke(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
}
