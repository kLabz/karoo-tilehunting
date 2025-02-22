package bzh.klabz.squadrating

import org.junit.Test

class ClusterTest {
    @Test
    fun testCluster(){
        val centerSquadrat = Squadrat(8815, 5481)
        val squadratLoadRadius = 5
        val squadrats = setOf(Squadrat(8815, 5481))

        val viewUbersquadrat = Ubersquadrat(centerSquadrat.x - squadratLoadRadius, centerSquadrat.y - squadratLoadRadius, squadratLoadRadius * 2)
        val squadratLoadRange = centerSquadrat.x - squadratLoadRadius..centerSquadrat.x + squadratLoadRadius

        val largestUbersquadrat = Ubersquadrat.getBiggestUbersquadrat(squadrats)

        val squadratsInUbersquadrat = squadrats.filter { largestUbersquadrat?.isInside(it) == true }.toSet()
        val squadratsNotInUbersquadrat = squadrats - squadratsInUbersquadrat
        val unexploredSquadrats = viewUbersquadrat.getAllSquadrats() - squadrats

        val ubersquadratCluster = clusterSquadrats(squadratsInUbersquadrat).single()
        val clusteredExploredSquadrats = clusterSquadrats(squadratsNotInUbersquadrat)
        val clusteredUnexploredSquadrats = clusterSquadrats(unexploredSquadrats)

        val ubersquadratClusterGridLines = ubersquadratCluster.getGridPolylines()

    }
}
