package com.example.engine

enum class DiffLineKind { UNCHANGED, ADDED, REMOVED }

data class DiffLine(
    val kind: DiffLineKind,
    val oldLineNo: Int?,
    val newLineNo: Int?,
    val text: String,
)

object DiffEngine {

    fun computeDiff(original: String, modified: String): List<DiffLine> {
        val oldLines = if (original.isEmpty()) emptyList() else original.lines()
        val newLines = if (modified.isEmpty()) emptyList() else modified.lines()

        // Cap DP matrix for 1GB RAM safety on very large files
        if (oldLines.size * newLines.size > 40_000) {
            return buildList {
                oldLines.take(200).forEachIndexed { idx, l ->
                    add(DiffLine(DiffLineKind.REMOVED, idx + 1, null, l))
                }
                newLines.take(200).forEachIndexed { idx, l ->
                    add(DiffLine(DiffLineKind.ADDED, null, idx + 1, l))
                }
            }
        }

        val m = oldLines.size
        val n = newLines.size
        val lcs = Array(m + 1) { IntArray(n + 1) }
        for (i in m - 1 downTo 0) {
            for (j in n - 1 downTo 0) {
                lcs[i][j] = if (oldLines[i] == newLines[j]) {
                    1 + lcs[i + 1][j + 1]
                } else {
                    maxOf(lcs[i + 1][j], lcs[i][j + 1])
                }
            }
        }

        val result = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        while (i < m && j < n) {
            when {
                oldLines[i] == newLines[j] -> {
                    result.add(DiffLine(DiffLineKind.UNCHANGED, i + 1, j + 1, oldLines[i]))
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    result.add(DiffLine(DiffLineKind.REMOVED, i + 1, null, oldLines[i]))
                    i++
                }
                else -> {
                    result.add(DiffLine(DiffLineKind.ADDED, null, j + 1, newLines[j]))
                    j++
                }
            }
        }
        while (i < m) {
            result.add(DiffLine(DiffLineKind.REMOVED, i + 1, null, oldLines[i]))
            i++
        }
        while (j < n) {
            result.add(DiffLine(DiffLineKind.ADDED, null, j + 1, newLines[j]))
            j++
        }
        return result
    }
}
