package com.sensars.eurostars.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Parses and renders Markdown text with proper formatting
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val lines = markdown.lines()
    Column(modifier = modifier) {
        lines.forEachIndexed { index, line ->
            when {
                line.startsWith("## ") -> {
                    // H2 Header
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                line.startsWith("### ") -> {
                    // H3 Header
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    // Bullet list
                    Row(
                        modifier = Modifier
                            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                            .fillMaxWidth()
                    ) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = buildAnnotatedString {
                                appendMarkdownStyledText(
                                    line.removePrefix("- ").removePrefix("* "),
                                    MaterialTheme.colorScheme
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                line.startsWith("**") && line.endsWith("**") && line.length > 4 -> {
                    // Bold text (standalone line)
                    Text(
                        text = line.removePrefix("**").removeSuffix("**"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                line.trim().isEmpty() -> {
                    // Empty line - add spacing
                    Spacer(modifier = Modifier.height(8.dp))
                }
                line.startsWith("---") -> {
                    // Horizontal rule
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                }
                else -> {
                    // Regular paragraph
                    Text(
                        text = buildAnnotatedString {
                            appendMarkdownStyledText(line, MaterialTheme.colorScheme)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/**
 * Builds annotated string with Markdown styling (bold, italic, code)
 */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendMarkdownStyledText(
    text: String,
    colorScheme: androidx.compose.material3.ColorScheme
) {
    var remaining = text
    
    while (remaining.isNotEmpty()) {
        // Check for bold **text**
        val boldStart = remaining.indexOf("**")
        if (boldStart != -1) {
            val boldEnd = remaining.indexOf("**", boldStart + 2)
            if (boldEnd != -1) {
                // Add text before bold
                if (boldStart > 0) {
                    appendMarkdownStyledText(remaining.substring(0, boldStart), colorScheme)
                }
                // Add bold text
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(remaining.substring(boldStart + 2, boldEnd))
                }
                remaining = remaining.substring(boldEnd + 2)
                continue
            }
        }
        
        // Check for italic _text_
        val italicStart = remaining.indexOf("_")
        if (italicStart != -1 && (italicStart == 0 || remaining[italicStart - 1] == ' ' || remaining[italicStart - 1] == '(')) {
            val italicEnd = remaining.indexOf("_", italicStart + 1)
            if (italicEnd != -1) {
                // Add text before italic
                if (italicStart > 0) {
                    appendMarkdownStyledText(remaining.substring(0, italicStart), colorScheme)
                }
                // Add italic text
                withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(remaining.substring(italicStart + 1, italicEnd))
                }
                remaining = remaining.substring(italicEnd + 1)
                continue
            }
        }
        
        // Check for inline code `code`
        val codeStart = remaining.indexOf("`")
        if (codeStart != -1) {
            val codeEnd = remaining.indexOf("`", codeStart + 1)
            if (codeEnd != -1) {
                // Add text before code
                if (codeStart > 0) {
                    appendMarkdownStyledText(remaining.substring(0, codeStart), colorScheme)
                }
                // Add code text with monospace style
                withStyle(
                    style = SpanStyle(
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(remaining.substring(codeStart + 1, codeEnd))
                }
                remaining = remaining.substring(codeEnd + 1)
                continue
            }
        }
        
        // No more Markdown formatting found, append remaining text
        append(remaining)
        break
    }
}

