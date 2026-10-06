package com.notifyvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(onUnlock: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var pendingOperation by remember { mutableStateOf<String?>(null) }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var isNewOperand by remember { mutableStateOf(true) }
    var historyList by remember { mutableStateOf(listOf<String>()) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val secretCode = "0000+0"

    val handleInput: (String) -> Unit = { input ->
        when (input) {
            "C" -> {
                display = "0"
                expression = ""
                pendingOperation = null
                operand1 = null
                isNewOperand = true
                historyList = emptyList()
            }
            "⌫" -> {
                if (!isNewOperand && display.length > 1) {
                    display = display.dropLast(1)
                    expression = expression.dropLast(1)
                } else if (!isNewOperand) {
                    display = "0"
                    expression = expression.dropLast(1)
                    isNewOperand = true
                }
            }
            "÷", "×", "-", "+" -> {
                if (operand1 == null) {
                    operand1 = display.toDoubleOrNull()
                } else if (!isNewOperand && pendingOperation != null) {
                    val result = calculate(operand1!!, display.toDoubleOrNull() ?: 0.0, pendingOperation!!)
                    display = formatResult(result)
                    operand1 = result
                }
                pendingOperation = input
                isNewOperand = true
                expression += input
            }
            "=" -> {
                if (expression == secretCode) {
                    onUnlock()
                    // Reset so if they lock it again, it's fresh
                    display = "0"
                    expression = ""
                    pendingOperation = null
                    operand1 = null
                    isNewOperand = true
                    historyList = emptyList()
                } else if (operand1 != null && pendingOperation != null) {
                    val op1Str = formatResult(operand1!!)
                    val op2Str = display
                    val result = calculate(operand1!!, display.toDoubleOrNull() ?: 0.0, pendingOperation!!)
                    val resStr = formatResult(result)
                    historyList = historyList + "$op1Str $pendingOperation $op2Str = $resStr"
                    
                    display = resStr
                    expression = resStr // Reset expression to result for chain calc
                    operand1 = result
                    pendingOperation = null
                    isNewOperand = true
                }
            }
            "." -> {
                if (isNewOperand) {
                    display = "0."
                    expression += "0."
                    isNewOperand = false
                } else if (!display.contains(".")) {
                    display += "."
                    expression += "."
                }
            }
            else -> { // Numbers
                if (isNewOperand) {
                    display = input
                    isNewOperand = false
                } else {
                    display += input
                }
                expression += input
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, end = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = { showHistorySheet = true }) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = Color.Gray
                )
            }
        }

        // Display
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = expression,
                color = Color.Gray.copy(alpha = 0.9f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = display,
                color = Color.White,
                fontSize = 72.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                maxLines = 1,
                lineHeight = 76.sp
            )
        }

        // Keypad
        val buttons = listOf(
            listOf("C", "⌫", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("0", "00", ".", "=")
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { btn ->
                    CalculatorButton(
                        text = btn,
                        modifier = Modifier.weight(1f),
                        onClick = { handleInput(btn) },
                        isActive = btn == pendingOperation
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = Color(0xFF1E1E1E)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Calculation History", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (historyList.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            color = Color(0xFFFF9500),
                            modifier = Modifier.clickable { historyList = emptyList() }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                if (historyList.isEmpty()) {
                    Text("No history yet.", color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        reverseLayout = false
                    ) {
                        items(historyList) { pastCalc ->
                            Text(
                                text = pastCalc,
                                color = Color.Gray.copy(alpha = 0.9f),
                                fontSize = 24.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val isOperator = text in listOf("÷", "×", "-", "+", "=")
    val isAction = text in listOf("C", "⌫", "%")
    
    val bgColor = when {
        isActive -> Color.White
        isOperator -> Color(0xFFFF9500) // Classic orange
        isAction -> Color(0xFFA5A5A5)   // Light gray
        else -> Color(0xFF333333)       // Dark gray
    }
    
    val textColor = when {
        isActive -> Color(0xFFFF9500)
        isAction -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text == "⌫") {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Backspace",
                tint = textColor,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Text(
                text = text,
                color = textColor,
                fontSize = 32.sp,
                fontWeight = if (isOperator || isActive) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

private fun calculate(op1: Double, op2: Double, operator: String): Double {
    return when (operator) {
        "+" -> op1 + op2
        "-" -> op1 - op2
        "×" -> op1 * op2
        "÷" -> if (op2 != 0.0) op1 / op2 else Double.NaN
        else -> op2
    }
}

private fun formatResult(result: Double): String {
    if (result.isNaN()) return "Error"
    val format = DecimalFormat("0.######")
    return format.format(result)
}
