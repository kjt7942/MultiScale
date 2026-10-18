package com.kjt7942.multiscale

import org.junit.Assert.*
import org.junit.Test

class VersionsTest {
    @Test fun newer() {
        assertTrue(Versions.isNewer("v0.1.1", "0.1.0"))
        assertTrue(Versions.isNewer("v0.2.0", "0.1.9"))
        assertTrue(Versions.isNewer("v1.0.0", "0.9.9"))
    }
    @Test fun notNewer() {
        assertFalse(Versions.isNewer("v0.1.0", "0.1.0"))
        assertFalse(Versions.isNewer("v0.0.9", "0.1.0"))
        assertFalse(Versions.isNewer("latest", "0.1.0"))
    }
}
