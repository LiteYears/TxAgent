package top.wkbin.taixu.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretRedactorTest {
    private val redactor = SecretRedactor()

    @Test
    fun `redacts escaped commands config prefixes and repeated secrets`() {
        val secret = "synthetic-test-credential"
        for (entry in listOf(
            """Args={"command":"password = \"$secret\"\nprint('$secret')"}""",
            """mail_smtp_pass: "$secret"""",
            "mail_smtp_pass: $secret",
            """--auth-password "$secret"""",
            """{"mail_smtp_pass":"$secret"}""",
        )) {
            assertFalse(redactor.redact(entry).contains(secret))
        }
    }

    @Test
    fun `redacts openai api key`() {
        val out = redactor.redact("config OPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwxyz123 rest")
        assertFalse(out.contains("sk-abcdefghijklmnopqrstuvwxyz123"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `leaves normal text intact`() {
        val out = redactor.redact("installed node v22.22.3")
        assertEqualsIgnoreCase("installed node v22.22.3", out)
    }

    @Test
    fun `masks configured environment secret`() {
        val out = redactor.redact("value=ghp_1234567890abcdef", listOf("ghp_1234567890abcdef"))
        assertFalse(out.contains("ghp_1234567890abcdef"))
        assertTrue(out.contains("gh") && out.contains("ef"))
    }

    @Test
    fun `privacy mode can be disabled`() {
        val secret = "local-development-secret"
        val out = redactor.redact(secret, listOf(secret), privacyMode = false)
        assertTrue(out.contains(secret))
    }

    @Test
    fun `redacts passwords in json and embedded scripts`() {
        val jsonSecret = "json-secret-value"
        val scriptSecret = "script-secret-value"
        val out = redactor.redact(
            """Args={"password":"$jsonSecret","expression":"pwd.value = '$scriptSecret'"}""",
        )

        assertFalse(out.contains(jsonSecret))
        assertFalse(out.contains(scriptSecret))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `redacts mainland phone identifiers from agent logs`() {
        val out = redactor.redact("acct.value = '13812345678'")

        assertFalse(out.contains("13812345678"))
        assertTrue(out.contains("[PHONE_REDACTED]"))
    }

    @Test
    fun `redacts custom mcp request headers and bearer credentials`() {
        val customApiKey = "mcp-custom-header-secret-998877"
        val bearerToken = "mcp-bearer-token-11223344"
        val rawLog = """MCP headers={"Authorization":"Bearer $bearerToken","X-API-Key":"$customApiKey"}"""
        val out = redactor.redact(rawLog, listOf(customApiKey, bearerToken))
        assertFalse(out.contains(customApiKey))
        assertFalse(out.contains(bearerToken))
    }

    private fun assertEqualsIgnoreCase(a: String, b: String) {
        assertTrue(a.equals(b, ignoreCase = true))
    }
}
