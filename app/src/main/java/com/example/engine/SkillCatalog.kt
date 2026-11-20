package com.example.engine

import com.example.data.local.SkillEntity

object SkillCatalog {

    val defaultSkills: List<SkillEntity> = listOf(
        SkillEntity(
            id = "skill_android_kotlin",
            name = "Android & Kotlin Expert",
            description = "Modern Jetpack Compose, Material 3, Gradle Kotlin DSL, and ViewModel/Flow architecture.",
            category = "Android",
            markdownContent = """
                # Android & Kotlin Developer Skill
                - Use Kotlin, Jetpack Compose, Material Design 3, and StateFlow MVVM.
                - Apply WindowInsets.safeDrawing and minimum 48dp touch targets.
                - Keep files modular under 500 lines and avoid blocking the main thread.
                - Use Storage Access Framework instead of broad legacy storage permissions.
            """.trimIndent(),
            isEnabled = true,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
        SkillEntity(
            id = "skill_web_stack",
            name = "HTML / CSS / JS Expert",
            description = "Responsive mobile-first web apps, clean semantic HTML5, CSS Grid/Flexbox, and vanilla ES6+.",
            category = "Frontend",
            markdownContent = """
                # HTML, CSS & JavaScript Expert Skill
                - Build semantic HTML5 with accessible contrast and mobile viewport support.
                - Keep CSS modular using custom properties and responsive breakpoints.
                - Avoid inline unsafe eval(); handle DOM events cleanly with defensive null checks.
            """.trimIndent(),
            isEnabled = true,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
        SkillEntity(
            id = "skill_debugging",
            name = "Debugging & Error Fixer",
            description = "Root-cause stack trace analysis, minimal surgical patches, and post-fix verification.",
            category = "Debugging",
            markdownContent = """
                # Debugging Expert Skill
                - Inspect exact compiler/runtime error lines before proposing edits.
                - Never rewrite unrelated files when fixing a single bug.
                - Verify syntax, imports, and resource links after every patch.
            """.trimIndent(),
            isEnabled = true,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
        SkillEntity(
            id = "skill_apk_analysis",
            name = "APK Reconstruction Assistant",
            description = "Interprets AXML manifests, DEX package namespaces, and reconstructs clean Gradle skeletons.",
            category = "Android",
            markdownContent = """
                # APK Analysis Skill
                - Preserve recovered resources, permissions, and component declarations accurately.
                - Clearly label missing/decompiled stubs as [RECONSTRUCTED].
                - Never claim original signing keys or full original source were recovered.
            """.trimIndent(),
            isEnabled = true,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
        SkillEntity(
            id = "skill_ui_designer",
            name = "Mobile UI Designer",
            description = "High-contrast developer typography, spacious 8dp grids, and adaptive Tablet/Portrait layouts.",
            category = "Design",
            markdownContent = """
                # UI Designer Skill
                - Enforce 8dp grid spacing, crisp visual hierarchy, and clear empty states.
                - Support both dark and light color tokens without hardcoded hex overrides in components.
            """.trimIndent(),
            isEnabled = false,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
        SkillEntity(
            id = "skill_python_backend",
            name = "Python & API Integration",
            description = "Clean Python 3 modules, FastAPI/Flask endpoints, type hints, and safe error handling.",
            category = "Backend",
            markdownContent = """
                # Python & API Integration Skill
                - Use type annotations, explicit timeouts on network calls, and environment variables for secrets.
                - Never hardcode credentials or API tokens inside source files.
            """.trimIndent(),
            isEnabled = false,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
        SkillEntity(
            id = "skill_game_dev",
            name = "Game Development",
            description = "RequestAnimationFrame / Canvas game loops, touch/keyboard input, and state machines.",
            category = "Games",
            markdownContent = """
                # Game Development Skill
                - Separate update(dt) physics from render(ctx) drawing using delta-time clamping.
                - Provide responsive touch controls for mobile screens and pause/restart state.
            """.trimIndent(),
            isEnabled = false,
            isTrusted = true,
            sourceType = "BUILTIN",
        ),
    )

    fun inspectExternalSkillRisk(markdown: String): List<String> {
        val warnings = mutableListOf<String>()
        val lower = markdown.lowercase()
        if (lower.contains("curl ") || lower.contains("wget ") || lower.contains("rm -rf") ||
            lower.contains("chmod ") || lower.contains("eval(") || lower.contains("sudo ")
        ) {
            warnings.add("Contains shell/command instructions — require explicit user approval before executing any commands.")
        }
        if (lower.contains("api_key") || lower.contains("token") || lower.contains("password")) {
            warnings.add("Mentions credentials — verify no instructions ask to log or exfiltrate secrets.")
        }
        return warnings
    }
}
