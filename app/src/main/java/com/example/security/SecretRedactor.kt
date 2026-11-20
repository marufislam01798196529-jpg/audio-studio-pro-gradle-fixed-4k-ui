package com.example.security

/**
 * Redacts API keys, OAuth tokens, and private credentials from logs, build outputs,
 * terminal output, and exported project archives.
 */
object SecretRedactor {

    private val commonSecretPatterns = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{12,}"""),
        Regex("""sk-ant-[A-Za-z0-9_\-]{12,}"""),
        Regex("""AIza[0-9A-Za-z_\-]{20,}"""),
        Regex("""ghp_[A-Za-z0-9]{16,}"""),
        Regex("""gho_[A-Za-z0-9]{16,}"""),
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
        Regex("""(?i)(Bearer\s+)[A-Za-z0-9\-._~+/]+=*"""),
        Regex("""(?i)(x-api-key\s*[:=]\s*)([^\s"']+)"""),
    )

    fun redact(text: String, knownSecrets: List<String> = emptyList()): String {
        if (text.isEmpty()) return text
        var result = text
        for (secret in knownSecrets) {
            if (secret.length >= 6 && result.contains(secret)) {
                result = result.replace(secret, "[REDACTED_SECRET]")
            }
        }
        commonSecretPatterns.forEach { regex ->
            result = result.replace(regex) { match ->
                if (match.groupValues.size >= 2 && match.groupValues[1].isNotEmpty() &&
                    (match.value.startsWith("Bearer", ignoreCase = true) ||
                        match.value.startsWith("x-api-key", ignoreCase = true))
                ) {
                    "${match.groupValues[1]}[REDACTED]"
                } else {
                    "[REDACTED_SECRET]"
                }
            }
        }
        return result
    }

    fun isSensitiveExportFile(relativePath: String): Boolean {
        val lower = relativePath.lowercase()
        val fileName = lower.substringAfterLast('/')
        return fileName == ".env" ||
            fileName.startsWith(".env.") && fileName != ".env.example" ||
            fileName.endsWith(".jks") ||
            fileName.endsWith(".keystore") ||
            fileName.endsWith(".p12") ||
            fileName.endsWith(".pem") ||
            fileName == "local.properties" ||
            fileName == "google-services.json" ||
            fileName == "secrets.properties"
    }
}
