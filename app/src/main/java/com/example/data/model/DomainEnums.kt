package com.example.data.model

enum class ProviderFormat(val label: String, val defaultBaseUrl: String) {
    OPENAI_COMPATIBLE("OpenAI-Compatible", "https://api.openai.com/v1"),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1"),
    GEMINI("Google Gemini REST", "https://generativelanguage.googleapis.com/v1beta"),
    CUSTOM("Custom / Local API", "http://localhost:11434/v1"),
}

enum class ModelCategory(val label: String) {
    CODING("Coding"),
    REASONING("Reasoning"),
    FAST("Fast"),
    GENERAL("General"),
    LIGHTWEIGHT("Lightweight"),
    VISION("Vision"),
}

enum class ReasoningLevel(val label: String, val apiValue: String, val description: String) {
    LOW("Low", "low", "Faster responses and lower token usage"),
    MEDIUM("Medium", "medium", "Balanced speed and architecture depth"),
    HIGH("High", "high", "Detailed planning and deep debugging"),
    MAX("Max", "high", "Maximum supported reasoning & verification"),
}

enum class ProjectType(
    val label: String,
    val primaryExt: String,
    val supportsLiveWebPreview: Boolean,
    val supportsCloudApkBuild: Boolean,
) {
    HTML_CSS_JS("HTML / CSS / JS", "html", true, false),
    WEBSITE("Website Project", "html", true, false),
    REACT("React Web App", "jsx", true, false),
    ANDROID_GRADLE("Android Gradle", "kt", false, true),
    KOTLIN("Kotlin", "kt", false, true),
    JAVA("Java", "java", false, true),
    FLUTTER("Flutter", "dart", false, true),
    NODE_JS("Node.js", "js", false, false),
    PYTHON("Python", "py", false, false),
    CPP("C / C++", "cpp", false, false),
    PHP("PHP", "php", false, false),
    GODOT("Godot", "gd", false, false),
}

enum class TaskStatus(val label: String) {
    PENDING("Pending"),
    PLANNING("Planning"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    NEEDS_APPROVAL("Needs Approval"),
}

enum class AgentRole(val label: String, val focusArea: String) {
    PLANNER("Planner Agent", "Architecture, file map & task breakdown"),
    FRONTEND("Frontend Agent", "UI layout, responsive CSS & Compose"),
    BACKEND("Backend Agent", "APIs, persistence & cloud queues"),
    ANDROID("Android Agent", "Kotlin, Gradle, Manifest & SDK"),
    DEBUGGING("Debugging Agent", "Stack traces, syntax & build errors"),
    TESTING("Testing Agent", "Unit tests, Robolectric & verification"),
    CODE_REVIEWER("Code Reviewer Agent", "Security, memory & code quality"),
    DOCUMENTATION("Documentation Agent", "README, BUILD-INSTRUCTIONS & specs"),
}

enum class QuickTaskAction(val label: String, val promptPrefix: String) {
    CREATE_NEW_APP("Create New App", "Design and build a complete application: "),
    BUILD_WEBSITE("Build Website", "Build a responsive HTML/CSS/JS website: "),
    CREATE_ANDROID_APP("Create Android App", "Create a Kotlin Jetpack Compose Android project: "),
    FIX_BUGS("Fix Bugs", "Analyze errors, detect bugs, and fix issues in the current project: "),
    CHANGE_EXISTING_CODE("Change Existing Code", "Modify the existing codebase carefully: "),
    ADD_NEW_FEATURES("Add New Features", "Add new features while preserving existing architecture: "),
    REBUILD_PROJECT("Rebuild Project", "Audit, refactor, and rebuild the project structure: "),
    EXPLAIN_CODE("Explain Code", "Explain the architecture, files, and logic of this project: "),
    OPTIMIZE_PROJECT("Optimize Project", "Optimize memory usage, performance, and bundle size: "),
    GENERATE_UI("Generate UI", "Generate clean, accessible, responsive UI components: "),
    REVIEW_CODE("Review Code", "Perform a security, reliability, and code quality review: "),
    CREATE_GAME("Create Game", "Build an interactive mobile/web game with smooth loop & controls: "),
    BUILD_APK("Build APK", "Validate Gradle config and prepare project for cloud APK build: "),
    EXPORT_ZIP("Export ZIP", "Prepare project documentation, config, and package for ZIP export: "),
}

data class ProposedFileOperation(
    val path: String,
    val action: String, // CREATE, MODIFY, DELETE
    val summary: String,
)

data class StructuredPlan(
    val userRequestAnalysis: String,
    val projectUnderstanding: String,
    val requirements: List<String>,
    val proposedArchitecture: String,
    val filesToCreate: List<String>,
    val filesToModify: List<String>,
    val dependencies: List<String>,
    val implementationTasks: List<String>,
    val testingStrategy: String,
    val expectedFinalOutput: String,
    val conciseReasoningSummary: String,
)

data class FileChangeProposal(
    val id: String,
    val filePath: String,
    val originalContent: String,
    val proposedContent: String,
    val rationale: String,
    val isNewFile: Boolean,
    val accepted: Boolean? = null, // null = pending
)

data class DiagnosticIssue(
    val id: String,
    val severity: String, // ERROR, WARNING
    val filePath: String,
    val line: Int,
    val message: String,
    val category: String,
)

data class ApkAnalysisReport(
    val fileName: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val applicationName: String,
    val versionCode: String,
    val versionName: String,
    val minSdk: String,
    val targetSdk: String,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val dexFiles: List<String>,
    val detectedPackagePrefixes: List<String>,
    val resourceFileCount: Int,
    val assetFileCount: Int,
    val nativeAbis: List<String>,
    val signatureFiles: List<String>,
    val manifestXmlReconstructed: String,
    val limitationsNote: String,
)

data class WorkspaceFileNode(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val depth: Int,
)

data class EditorTab(
    val filePath: String,
    val fileName: String,
    val content: String,
    val isDirty: Boolean = false,
    val isTruncatedForLowRam: Boolean = false,
    val totalFileBytes: Long = 0L,
)

data class TerminalLine(
    val id: Long,
    val text: String,
    val isError: Boolean = false,
    val isCommand: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)
