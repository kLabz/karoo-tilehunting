package bzh.klabz.squadrating

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

const val SQUADRAT_ZOOM = 14
const val SQUADRATINHO_ZOOM = 17

/**
 * Converts geographic coordinates to tile indices at the default zoom level.
 *
 * The formulas used are:
 *   n = 2^zoom
 *   x = floor((lon + 180) / 360 * n)
 *   y = floor((1 - ln(tan(lat_rad) + sec(lat_rad)) / π) / 2 * n)
 */
fun coordsToSquadrat(lat: Double, lon: Double): Squadrat {
    val zoom = SQUADRAT_ZOOM
    val n = 2.0.pow(zoom)
    val xSquadrat = ((lon + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(lat)
    val ySquadrat = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    return Squadrat(xSquadrat, ySquadrat)
}

/**
 * Converts geographic coordinates to tile indices at the default zoom level.
 *
 * The formulas used are:
 *   n = 2^(zoom * 8)
 *   x = floor((lon + 180) / 360 * n)
 *   y = floor((1 - ln(tan(lat_rad) + sec(lat_rad)) / π) / 2 * n)
 */
fun coordsToSquadratinho(lat: Double, lon: Double): Squadratinho {
    val zoom = SQUADRATINHO_ZOOM
    val n = 2.0.pow(zoom)
    val xSquadratinho = ((lon + 180.0) / 360.0 * n).toInt()
    val latRad = Math.toRadians(lat)
    val ySquadratinho = ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n).toInt()
    return Squadratinho(xSquadratinho, ySquadratinho)
}
