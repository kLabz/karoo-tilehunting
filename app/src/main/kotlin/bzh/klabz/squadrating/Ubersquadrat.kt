package bzh.klabz.squadrating

data class Ubersquadrat(val x: Int, val y: Int, val size: Int){
    fun isInside(squadrat: Squadrat): Boolean {
        return squadrat.x >= x && squadrat.x < x + size && squadrat.y >= y && squadrat.y < y + size
    }

    fun getAllSquadrats(): Set<Squadrat> = buildSet {
        for (i in 0..<this@Ubersquadrat.size){
            for (j in 0..<this@Ubersquadrat.size){
                add(Squadrat(x + i, y + j))
            }
        }
    }

    fun isInside(lat: Double, lon: Double): Boolean {
        return isInside(coordsToSquadrat(lat, lon))
    }

    companion object {
        /**
         * Returns the largest ubersquadrat (represented by its top–left corner and size)
         * that can be entirely found in the specified set of squadrats.
         *
         * In this implementation, each squadrat is interpreted as the top–left corner of a candidate ubersquadrat.
         * The ubersquadrat extends to the right (increasing x) and downward (increasing y).
         */
        fun getBiggestUbersquadrat(squadrats: Set<Squadrat>): Ubersquadrat? {
            if (squadrats.isEmpty()) return null

            var bestUbersquadrat: Ubersquadrat? = null

            // For each squadrat, consider it as the top–left of a candidate ubersquadrat.
            for (squadrat in squadrats) {
                var s = 1
                // Expand the ubersquadrat until a required squadrat is missing.
                while (true) {
                    // Check all positions in the candidate ubersquadrat.
                    val ubersquadratComplete = (0 until s).all { i ->
                        (0 until s).all { j ->
                            // For a ubersquadrat with top–left corner at squadrat,
                            // the squadrats needed are those at (squadrat.x + i, squadrat.y + j)
                            Squadrat(squadrat.x + i, squadrat.y + j) in squadrats
                        }
                    }
                    if (ubersquadratComplete) {
                        // Update bestUbersquadrat if this ubersquadrat is larger than the current best.
                        if (bestUbersquadrat == null || s > bestUbersquadrat.size) {
                            bestUbersquadrat = Ubersquadrat(squadrat.x, squadrat.y, s)
                        }
                        s++
                    } else {
                        break
                    }
                }
            }
            return bestUbersquadrat
        }
    }
}
