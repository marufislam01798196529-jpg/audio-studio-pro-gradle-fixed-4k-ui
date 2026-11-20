package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxString

object SyntaxHighlighter {
    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "import", "package", "return",
        "if", "else", "when", "for", "while", "suspend", "private", "public", "override",
        "function", "const", "let", "async", "await", "export", "default", "from", "def",
        "try", "catch", "throw", "new", "true", "false", "null", "void", "int", "string",
    )

    private val tokenRegex = Regex(
        """(//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_][A-Za-z0-9_]*\b)"""
    )

    fun highlight(code: String, darkTheme: Boolean = true): AnnotatedString {
        // Guard against heavy span creation on low-RAM devices for huge blobs
        val capped = if (code.length > 16_000) code.take(16_000) else code
        return buildAnnotatedString {
            append(capped)
            tokenRegex.findAll(capped).forEach { match ->
                val token = match.value
                val color = when {
                    token.startsWith("//") || token.startsWith("#") || token.startsWith("/*") -> SyntaxComment
                    token.startsWith("\"") || token.startsWith("'") -> SyntaxString
                    token.first().isDigit() -> SyntaxNumber
                    token in keywords -> SyntaxKeyword
                    else -> null
                }
                if (color != null) {
                    addStyle(
                        SpanStyle(
                            color = color,
                            fontWeight = if (token in keywords) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        start = match.range.first,
                        end = match.range.last + 1,
                    )
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    code: String,
    languageLabel: String = "code",
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val highlighted = remember(code) { SyntaxHighlighter.highlight(code) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF070B14))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF101729))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = languageLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(code)) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy code",
                    tint = Color(0xFF94A3B8),
                )
            }
        }
        Text(
            text = highlighted,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = Color(0xFFE2E8F0),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
}
