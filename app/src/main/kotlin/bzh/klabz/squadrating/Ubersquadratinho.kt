package bzh.klabz.squadrating

data class Ubersquadratinho(val x: Int, val y: Int, val size: Int){
    fun isInside(squadratinho: Squadratinho): Boolean {
        return squadratinho.x >= x && squadratinho.x < x + size && squadratinho.y >= y && squadratinho.y < y + size
    }

    fun getAllSquadratinhos(): Set<Squadratinho> = buildSet {
        for (i in 0..<this@Ubersquadratinho.size){
            for (j in 0..<this@Ubersquadratinho.size){
                add(Squadratinho(x + i, y + j))
            }
        }
    }

    fun isInside(lat: Double, lon: Double): Boolean {
        return isInside(coordsToSquadratinho(lat, lon))
    }

    companion object {
        /**
         * Returns the largest ubersquadratinho (represented by its top–left corner and size)
         * that can be entirely found in the specified set of squadratinhos.
         *
         * In this implementation, each squadratinho is interpreted as the top–left corner of a candidate ubersquadratinho.
         * The ubersquadratinho extends to the right (increasing x) and downward (increasing y).
         */
        fun getBiggestUbersquadratinho(squadratinhos: Set<Squadratinho>): Ubersquadratinho? {
            if (squadratinhos.isEmpty()) return null

            var bestUbersquadratinho: Ubersquadratinho? = null

            // For each squadratinho, consider it as the top–left of a candidate ubersquadratinho.
            for (squadratinho in squadratinhos) {
                var s = 1
                // Expand the ubersquadratinho until a required squadratinho is missing.
                while (true) {
                    // Check all positions in the candidate ubersquadratinho.
                    val ubersquadratinhoComplete = (0 until s).all { i ->
                        (0 until s).all { j ->
                            // For a ubersquadratinho with top–left corner at squadratinho,
                            // the squadratinhos needed are those at (squadratinho.x + i, squadratinho.y + j)
                            Squadratinho(squadratinho.x + i, squadratinho.y + j) in squadratinhos
                        }
                    }
                    if (ubersquadratinhoComplete) {
                        // Update bestUbersquadratinho if this ubersquadratinho is larger than the current best.
                        if (bestUbersquadratinho == null || s > bestUbersquadratinho.size) {
                            bestUbersquadratinho = Ubersquadratinho(squadratinho.x, squadratinho.y, s)
                        }
                        s++
                    } else {
                        break
                    }
                }
            }
            return bestUbersquadratinho
        }
    }
}
