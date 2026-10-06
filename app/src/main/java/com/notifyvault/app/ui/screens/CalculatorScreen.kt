package com.notifyvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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

@Composable
fun CalculatorScreen(onUnlock: () -> Unit) {
    var display by remember { mutableStateOf("0") }
    var expression by remember { mutableStateOf("") }
    var pendingOperation by remember { mutableStateOf<String?>(null) }
    var operand1 by remember { mutableStateOf<Double?>(null) }
    var isNewOperand by remember { mutableStateOf(true) }

    val secretCode = "0000+0"

    val handleInput: (String) -> Unit = { input ->
        when (input) {
            "C" -> {
                display = "0"
                expression = ""
                pendingOperation = null
                operand1 = null
                isNewOperand = true
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
                } else if (operand1 != null && pendingOperation != null) {
                    val result = calculate(operand1!!, display.toDoubleOrNull() ?: 0.0, pendingOperation!!)
                    display = formatResult(result)
                    expression = display // Reset expression to result for chain calc
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
            .background(Color(0xFF1E1E1E))
            .padding(16.dp),
        verticalArrangement = Arrangement.Bottom
    ) {
        // Display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Text(
                text = display,
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.End,
                maxLines = 1,
                lineHeight = 70.sp
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
                        onClick = { handleInput(btn) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CalculatorButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOperator = text in listOf("÷", "×", "-", "+", "=")
    val isAction = text in listOf("C", "⌫", "%")
    
    val bgColor = when {
        isOperator -> Color(0xFFFF9500) // Classic orange
        isAction -> Color(0xFFA5A5A5)   // Light gray
        else -> Color(0xFF333333)       // Dark gray
    }
    
    val textColor = if (isAction) Color.Black else Color.White

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 28.sp,
            fontWeight = if (isOperator) FontWeight.Medium else FontWeight.Normal
        )
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
