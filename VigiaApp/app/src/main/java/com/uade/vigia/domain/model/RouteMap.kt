package com.uade.vigia.domain.model


/**
 * Posición de cada punto sobre el plano del recorrido, normalizada 0..1 (mismas proporciones que el plano
 * del prototipo en Figma). Las coordenadas en metros son aproximadas y solo sirven para mostrarlas.
 */
object RouteMap {
    private const val SITE_WIDTH_M = 60f
    private const val SITE_HEIGHT_M = 30f

    val points: Map<Checkpoint, Pair<Float, Float>> = mapOf(
        Checkpoint.BASE to (0.1667f to 0.8438f),
        Checkpoint.SAFE to (0.15f to 0.4062f),
        Checkpoint.P1 to (0.25f to 0.625f),
        Checkpoint.P2 to (0.45f to 0.625f),
        Checkpoint.P3 to (0.55f to 0.625f),
        Checkpoint.P4 to (0.8833f to 0.625f)
    )

    val SAFE_POSE: Pose = pose(points.getValue(Checkpoint.SAFE))

    /** Posición en el plano a [meters] de la base, siguiendo el pasillo. */
    fun poseAt(meters: Float): Pose {
        val route = Checkpoint.route
        val m = meters.coerceIn(0f, route.last().routeMeters)
        val next = route.indexOfFirst { it.routeMeters >= m }.coerceAtLeast(1)
        val a = route[next - 1]
        val b = route[next]
        val span = b.routeMeters - a.routeMeters
        val f = if (span <= 0f) 0f else (m - a.routeMeters) / span
        return lerp(pose(points.getValue(a)), pose(points.getValue(b)), f)
    }

    fun lerp(from: Pose, to: Pose, f: Float): Pose {
        val x = from.mapX + (to.mapX - from.mapX) * f
        val y = from.mapY + (to.mapY - from.mapY) * f
        return pose(x to y)
    }

    private fun pose(p: Pair<Float, Float>) = Pose(
        mapX = p.first,
        mapY = p.second,
        xM = p.first * SITE_WIDTH_M,
        yM = (1f - p.second) * SITE_HEIGHT_M + 10f
    )
}
