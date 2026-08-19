package com.easytier.jni

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VpnConfigParsersTest {

    // --- parseIpv4Address ---

    @Test
    fun `parseIpv4Address default prefix 24 when no slash`() {
        val (ip, prefix) = VpnConfigParsers.parseIpv4Address("10.0.0.1")
        assertEquals("10.0.0.1", ip)
        assertEquals(24, prefix)
    }

    @Test
    fun `parseIpv4Address with explicit prefix`() {
        val (ip, prefix) = VpnConfigParsers.parseIpv4Address("10.0.0.1/16")
        assertEquals("10.0.0.1", ip)
        assertEquals(16, prefix)
    }

    @Test
    fun `parseIpv4Address falls back to 24 when prefix non numeric`() {
        val (ip, prefix) = VpnConfigParsers.parseIpv4Address("10.0.0.1/abc")
        assertEquals("10.0.0.1", ip)
        assertEquals(24, prefix)
    }

    // --- parseIpv6Address ---

    @Test
    fun `parseIpv6Address default prefix 64 when no slash`() {
        val (ip, prefix) = VpnConfigParsers.parseIpv6Address("fd00::1")
        assertEquals("fd00::1", ip)
        assertEquals(64, prefix)
    }

    @Test
    fun `parseIpv6Address with explicit prefix`() {
        val (ip, prefix) = VpnConfigParsers.parseIpv6Address("fd00::1/128")
        assertEquals("fd00::1", ip)
        assertEquals(128, prefix)
    }

    // --- parseCidr ---

    @Test
    fun `parseCidr valid input`() {
        val (ip, prefix) = VpnConfigParsers.parseCidr("192.168.1.0/24")
        assertEquals("192.168.1.0", ip)
        assertEquals(24, prefix)
    }

    @Test
    fun `parseCidr throws on missing prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            VpnConfigParsers.parseCidr("192.168.1.0")
        }
    }

    @Test
    fun `parseCidr throws on multiple slashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            VpnConfigParsers.parseCidr("10.0.0.1/24/extra")
        }
    }
}
