package com.saurav.pixelmusic.presentation.components.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object QrCodeEncoder {
    private val EXP = IntArray(512)
    private val LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            EXP[i] = x
            EXP[i + 255] = x
            LOG[x] = i
            x = (x shl 1) xor (if ((x and 0x80) != 0) 0x11d else 0)
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return EXP[LOG[a] + LOG[b]]
    }

    fun encode(text: String): List<List<Boolean>> {
        val dataBytes = text.toByteArray(Charsets.UTF_8)
        val dataLen = dataBytes.size

        val bits = ArrayList<Int>(800)
        // Mode 4 (Byte) = 0100
        bits.add(0); bits.add(1); bits.add(0); bits.add(0)
        // 8-bit character count indicator for Version 4
        for (i in 7 downTo 0) {
            bits.add((dataLen shr i) and 1)
        }
        for (b in dataBytes) {
            val v = b.toInt() and 0xFF
            for (i in 7 downTo 0) {
                bits.add((v shr i) and 1)
            }
        }

        val maxBits = 80 * 8
        val terminatorCount = minOf(4, maxBits - bits.size)
        for (i in 0 until terminatorCount) bits.add(0)
        while (bits.size % 8 != 0) bits.add(0)

        val pad = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bits.size < maxBits) {
            val pb = pad[padIdx % 2]
            padIdx++
            for (i in 7 downTo 0) {
                bits.add((pb shr i) and 1)
            }
        }

        val dc = IntArray(80)
        for (i in 0 until 80) {
            var byteVal = 0
            val offset = i * 8
            for (j in 0 until 8) {
                byteVal = (byteVal shl 1) or bits[offset + j]
            }
            dc[i] = byteVal
        }

        // Generator polynomial for degree 20
        var g = intArrayOf(1)
        for (i in 0 until 20) {
            val ng = IntArray(g.size + 1)
            for (j in g.indices) {
                ng[j] = ng[j] xor gfMul(g[j], EXP[i])
                ng[j + 1] = ng[j + 1] xor g[j]
            }
            g = ng
        }

        val ec = IntArray(20)
        for (b in dc) {
            val factor = b xor ec[0]
            for (j in 0 until 19) {
                ec[j] = ec[j + 1] xor gfMul(factor, g[j + 1])
            }
            ec[19] = gfMul(factor, g[20])
        }

        val allCodewords = IntArray(100)
        System.arraycopy(dc, 0, allCodewords, 0, 80)
        System.arraycopy(ec, 0, allCodewords, 80, 20)

        val bitstream = ArrayList<Int>(807)
        for (b in allCodewords) {
            for (i in 7 downTo 0) {
                bitstream.add((b shr i) and 1)
            }
        }
        for (i in 0 until 7) bitstream.add(0)

        val size = 33
        val grid = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        fun placeFinder(r0: Int, c0: Int) {
            for (r in 0..6) {
                for (c in 0..6) {
                    val isBlack = (r in intArrayOf(0, 6) || c in intArrayOf(0, 6) || (r in 2..4 && c in 2..4))
                    grid[r0 + r][c0 + c] = isBlack
                    reserved[r0 + r][c0 + c] = true
                }
            }
            for (r in -1..7) {
                for (c in -1..7) {
                    if (r == -1 || r == 7 || c == -1 || c == 7) {
                        val rr = r0 + r
                        val cc = c0 + c
                        if (rr in 0 until size && cc in 0 until size) {
                            grid[rr][cc] = false
                            reserved[rr][cc] = true
                        }
                    }
                }
            }
        }

        placeFinder(0, 0)
        placeFinder(0, 26)
        placeFinder(26, 0)

        val ar = 24
        val ac = 24
        for (r in -2..2) {
            for (c in -2..2) {
                grid[ar + r][ac + c] = (kotlin.math.abs(r) == 2 || kotlin.math.abs(c) == 2 || (r == 0 && c == 0))
                reserved[ar + r][ac + c] = true
            }
        }

        for (i in 8..24) {
            if (!reserved[6][i]) {
                grid[6][i] = (i % 2 == 0)
                reserved[6][i] = true
            }
            if (!reserved[i][6]) {
                grid[i][6] = (i % 2 == 0)
                reserved[i][6] = true
            }
        }

        grid[25][8] = true
        reserved[25][8] = true

        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in 0..7) {
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }

        var bitIdx = 0
        var c = size - 1
        var goingUp = true
        while (c > 0) {
            if (c == 6) c -= 1
            val cols = intArrayOf(c, c - 1)
            val rows = if (goingUp) (size - 1 downTo 0).toList() else (0 until size).toList()
            for (r in rows) {
                for (cc in cols) {
                    if (!reserved[r][cc] && bitIdx < bitstream.size) {
                        var b = bitstream[bitIdx]
                        bitIdx++
                        if ((r + cc) % 2 == 0) {
                            b = b xor 1
                        }
                        grid[r][cc] = (b == 1)
                    }
                }
            }
            goingUp = !goingUp
            c -= 2
        }

        val formatBits = intArrayOf(1, 1, 1, 0, 1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0)
        val coordsTl = listOf(
            8 to 0, 8 to 1, 8 to 2, 8 to 3, 8 to 4, 8 to 5, 8 to 7, 8 to 8,
            7 to 8, 5 to 8, 4 to 8, 3 to 8, 2 to 8, 1 to 8, 0 to 8
        )
        for ((idx, pair) in coordsTl.withIndex()) {
            grid[pair.first][pair.second] = (formatBits[idx] == 1)
        }
        for (i in 0 until 7) {
            grid[size - 1 - i][8] = (formatBits[i] == 1)
        }
        for (i in 0 until 8) {
            grid[8][size - 8 + i] = (formatBits[7 + i] == 1)
        }

        return grid.map { it.toList() }
    }
}

@Composable
fun QrCodeCanvas(
    matrix: List<List<Boolean>>,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val size = matrix.size
        if (size == 0) return@Canvas
        val cellW = this.size.width / size
        val cellH = this.size.height / size
        drawRect(lightColor)
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (matrix[r][c]) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW, cellH)
                    )
                }
            }
        }
    }
}

@Composable
fun ListenTogetherQrDialog(
    code: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val link = remember(code) { "pixelmusic://listen_together?code=$code" }
    val matrix = remember(link) {
        try {
            QrCodeEncoder.encode(link)
        } catch (e: Exception) {
            emptyList()
        }
    }
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scan to Join",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(240.dp)
                        .padding(8.dp)
                ) {
                    if (matrix.isNotEmpty()) {
                        QrCodeCanvas(
                            matrix = matrix,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "ROOM CODE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = code,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("session link", link))
                            copied = true
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (copied) "Copied!" else "Copy Link")
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Join my Listen Together session on Pixel Music! Room Code: $code\n$link"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Listen Together Invite"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
