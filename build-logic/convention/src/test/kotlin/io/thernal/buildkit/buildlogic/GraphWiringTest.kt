package io.thernal.buildkit.buildlogic

import io.thernal.buildkit.buildlogic.injection.appNameOf
import io.thernal.buildkit.buildlogic.injection.graphWiring
import io.thernal.buildkit.buildlogic.injection.unknownGraphExclusions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GraphWiringTest {
    private val projects = listOf(
        ":apps:terz:shared",
        ":apps:terz:wiring",
        ":core:navigation:api",
        ":core:navigation:wiring",
        ":core:firebase:crash:wiring",
        ":core:firebase:messaging:wiring",
        ":core:firebasex:wiring",
        ":features:auth:impl",
        ":features:auth:wiring",
        ":features:client:orders:wiring",
        ":features:store:stock:wiring",
    )

    @Test
    fun `every wiring module outside apps joins the graph, sorted`() {
        assertEquals(
            listOf(
                ":core:firebase:crash:wiring",
                ":core:firebase:messaging:wiring",
                ":core:firebasex:wiring",
                ":core:navigation:wiring",
                ":features:auth:wiring",
                ":features:client:orders:wiring",
                ":features:store:stock:wiring",
            ),
            graphWiring(projects, modulesRoot = emptyList(), excluded = emptyList()),
        )
    }

    @Test
    fun `an excluded path takes everything below it out, and nothing that only starts like it`() {
        val wiring = graphWiring(projects, modulesRoot = emptyList(), excluded = listOf(":core:firebase:", ":features:store"))
        assertEquals(
            listOf(":core:firebasex:wiring", ":core:navigation:wiring", ":features:auth:wiring", ":features:client:orders:wiring"),
            wiring,
        )
    }

    @Test
    fun `the modules root is not an area`() {
        val rooted = listOf(":fixture:apps:customer:shared", ":fixture:features:greeting:wiring", ":fixture:apps:customer:wiring")
        assertEquals(listOf(":fixture:features:greeting:wiring"), graphWiring(rooted, listOf("fixture"), emptyList()))
    }

    @Test
    fun `an exclusion that names no module is reported`() {
        assertEquals(listOf(":core:firebse"), unknownGraphExclusions(projects, listOf(":core:firebase", ":core:firebse")))
    }

    @Test
    fun `the app is the directory under apps`() {
        assertEquals("terz", appNameOf(":apps:terz:shared", modulesRoot = emptyList()))
        assertEquals("kiosk", appNameOf(":fixture:apps:kiosk", modulesRoot = listOf("fixture")))
        assertNull(appNameOf(":features:auth:wiring", modulesRoot = emptyList()))
    }
}
