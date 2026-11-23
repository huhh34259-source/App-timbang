package com.example.engine

import com.example.model.CellAddress
import com.example.model.CellData
import com.example.model.SheetData
import com.example.model.WorkbookData
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

sealed class EvalValue {
    data object Blank : EvalValue()
    data class Num(val value: Double) : EvalValue()
    data class Str(val value: String) : EvalValue()
    data class Bool(val value: Boolean) : EvalValue()
    data class Err(val code: String) : EvalValue()
    data class Range(val items: List<EvalValue>, val rows: Int, val cols: Int) : EvalValue()
}

object FormulaEvaluator {

    fun evaluateWorkbook(workbook: WorkbookData): WorkbookData {
        val sheets = workbook.sheets
        val cache = HashMap<Pair<Int, CellAddress>, EvalValue>()
        val visiting = HashSet<Pair<Int, CellAddress>>()

        val updatedSheets = sheets.mapIndexed { sheetIdx, sheet ->
            val newCells = LinkedHashMap<CellAddress, CellData>(sheet.cells.size)
            for ((addr, cell) in sheet.cells) {
                if (cell.hasFormula) {
                    val eval = evalCell(sheets, sheetIdx, addr, cache, visiting)
                    newCells[addr] = applyEvalToCell(cell, eval)
                } else {
                    val num = parseLiteralNumber(cell.rawInput)
                    val display = formatLiteralDisplay(cell.rawInput, num, cell.style.numberFormat)
                    newCells[addr] = cell.copy(
                        cachedDisplay = display,
                        numericValue = num,
                        isError = false
                    )
                }
            }
            sheet.copy(cells = newCells)
        }
        return workbook.copy(sheets = updatedSheets)
    }

    fun applyEvalToCell(cell: CellData, eval: EvalValue): CellData {
        return when (eval) {
            is EvalValue.Blank -> cell.copy(cachedDisplay = "0", numericValue = 0.0, isError = false)
            is EvalValue.Num -> cell.copy(
                cachedDisplay = formatNumber(eval.value, cell.style.numberFormat),
                numericValue = eval.value,
                isError = false
            )
            is EvalValue.Str -> cell.copy(
                cachedDisplay = eval.value,
                numericValue = eval.value.toDoubleOrNull(),
                isError = false
            )
            is EvalValue.Bool -> cell.copy(
                cachedDisplay = if (eval.value) "TRUE" else "FALSE",
                numericValue = if (eval.value) 1.0 else 0.0,
                isError = false
            )
            is EvalValue.Err -> cell.copy(
                cachedDisplay = eval.code,
                numericValue = null,
                isError = true
            )
            is EvalValue.Range -> {
                val first = eval.items.firstOrNull() ?: EvalValue.Blank
                applyEvalToCell(cell, first)
            }
        }
    }

    private fun evalCell(
        sheets: List<SheetData>,
        sheetIdx: Int,
        addr: CellAddress,
        cache: MutableMap<Pair<Int, CellAddress>, EvalValue>,
        visiting: MutableSet<Pair<Int, CellAddress>>
    ): EvalValue {
        val key = sheetIdx to addr
        cache[key]?.let { return it }
        if (!visiting.add(key)) return EvalValue.Err("#CIRC!")

        val result = try {
            val sheet = sheets.getOrNull(sheetIdx)
            val cell = sheet?.cells?.get(addr)
            if (cell == null) {
                EvalValue.Blank
            } else if (cell.hasFormula) {
                val expr = cell.formula!!.trim().removePrefix("=")
                evalExpression(expr, sheets, sheetIdx, cache, visiting)
            } else {
                literalToEvalValue(cell.rawInput)
            }
        } catch (_: Exception) {
            EvalValue.Err("#ERROR!")
        } finally {
            visiting.remove(key)
        }

        cache[key] = result
        return result
    }

    fun literalToEvalValue(raw: String): EvalValue {
        if (raw.isEmpty()) return EvalValue.Blank
        if (raw.equals("TRUE", ignoreCase = true)) return EvalValue.Bool(true)
        if (raw.equals("FALSE", ignoreCase = true)) return EvalValue.Bool(false)
        val num = parseLiteralNumber(raw)
        return if (num != null) EvalValue.Num(num) else EvalValue.Str(raw)
    }

    fun parseLiteralNumber(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("#") || trimmed.startsWith("=")) return null
        // Preserve leading-zero codes like "08123456" or "0012" as text, except "0" or "0.xx"
        if (trimmed.length > 1 && trimmed.startsWith("0") && !trimmed.startsWith("0.") && !trimmed.startsWith("0,")) {
            return null
        }
        return trimmed.toDoubleOrNull()
    }

    private fun formatLiteralDisplay(raw: String, num: Double?, numberFormat: String?): String {
        if (num != null && !numberFormat.isNullOrBlank()) {
            return formatNumber(num, numberFormat)
        }
        return raw
    }

    fun formatNumber(value: Double, numberFormat: String? = null): String {
        if (value.isNaN() || value.isInfinite()) return "#NUM!"
        val clean = if (abs(value) < 1e-12) 0.0 else {
            BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toDouble()
        }

        if (!numberFormat.isNullOrBlank()) {
            val fmt = numberFormat.lowercase(Locale.ROOT)
            if (fmt.contains("%")) {
                val pct = clean * 100.0
                val rounded = BigDecimal.valueOf(pct).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
                return "${rounded.toPlainString()}%"
            }
            if (fmt.contains("rp") || fmt.contains("idr")) {
                val df = DecimalFormat("#,##0.##", DecimalFormatSymbols(Locale("id", "ID")))
                return "Rp ${df.format(clean)}"
            }
            if (fmt.contains("#,##0") || fmt.contains("#,##")) {
                val decimals = if (fmt.contains(".00")) "#,##0.00" else "#,##0.##"
                val df = DecimalFormat(decimals, DecimalFormatSymbols(Locale("id", "ID")))
                return df.format(clean)
            }
        }

        val bd = BigDecimal.valueOf(clean).stripTrailingZeros()
        return if (bd.scale() <= 0) {
            bd.toBigInteger().toString()
        } else {
            bd.toPlainString()
        }
    }

    private fun evalExpression(
        expr: String,
        sheets: List<SheetData>,
        currentSheetIdx: Int,
        cache: MutableMap<Pair<Int, CellAddress>, EvalValue>,
        visiting: MutableSet<Pair<Int, CellAddress>>
    ): EvalValue {
        if (expr.isBlank()) return EvalValue.Blank
        val tokens = tokenize(expr)
        val parser = Parser(tokens, sheets, currentSheetIdx, cache, visiting)
        val value = parser.parseComparison()
        return if (value is EvalValue.Range) {
            value.items.firstOrNull() ?: EvalValue.Blank
        } else {
            value
        }
    }

    private enum class TokenType {
        NUMBER, STRING, IDENT, CELL_OR_SHEET, COLON, COMMA, LPAREN, RPAREN,
        OP, CMP, PERCENT, EOF
    }

    private data class Token(val type: TokenType, val text: String)

    private fun tokenize(input: String): List<Token> {
        val tokens = ArrayList<Token>()
        var i = 0
        val n = input.length
        while (i < n) {
            val c = input[i]
            when {
                c.isWhitespace() -> i++
                c == '"' -> {
                    i++
                    val sb = StringBuilder()
                    while (i < n) {
                        if (input[i] == '"') {
                            if (i + 1 < n && input[i + 1] == '"') {
                                sb.append('"')
                                i += 2
                            } else {
                                i++
                                break
                            }
                        } else {
                            sb.append(input[i])
                            i++
                        }
                    }
                    tokens.add(Token(TokenType.STRING, sb.toString()))
                }
                c == '\'' -> {
                    // Quoted sheet name e.g. 'Rekap Penjualan'!A1
                    i++
                    val sb = StringBuilder()
                    while (i < n && input[i] != '\'') {
                        sb.append(input[i])
                        i++
                    }
                    if (i < n && input[i] == '\'') i++
                    if (i < n && input[i] == '!') {
                        i++
                        val refSb = StringBuilder()
                        while (i < n && (input[i].isLetterOrDigit() || input[i] == '$')) {
                            refSb.append(input[i])
                            i++
                        }
                        tokens.add(Token(TokenType.CELL_OR_SHEET, "${sb}!${refSb}"))
                    } else {
                        tokens.add(Token(TokenType.STRING, sb.toString()))
                    }
                }
                c == '(' -> { tokens.add(Token(TokenType.LPAREN, "(")); i++ }
                c == ')' -> { tokens.add(Token(TokenType.RPAREN, ")")); i++ }
                c == ':' -> { tokens.add(Token(TokenType.COLON, ":")); i++ }
                c == ',' || c == ';' -> { tokens.add(Token(TokenType.COMMA, ",")); i++ }
                c == '%' -> { tokens.add(Token(TokenType.PERCENT, "%")); i++ }
                c == '+' || c == '-' || c == '*' || c == '/' || c == '^' || c == '&' -> {
                    tokens.add(Token(TokenType.OP, c.toString()))
                    i++
                }
                c == '<' || c == '>' || c == '=' -> {
                    val two = if (i + 1 < n) input.substring(i, i + 2) else ""
                    if (two == "<=" || two == ">=" || two == "<>") {
                        tokens.add(Token(TokenType.CMP, two))
                        i += 2
                    } else {
                        tokens.add(Token(TokenType.CMP, c.toString()))
                        i++
                    }
                }
                c.isDigit() || (c == '.' && i + 1 < n && input[i + 1].isDigit()) -> {
                    val start = i
                    while (i < n && (input[i].isDigit() || input[i] == '.')) i++
                    if (i < n && (input[i] == 'e' || input[i] == 'E')) {
                        i++
                        if (i < n && (input[i] == '+' || input[i] == '-')) i++
                        while (i < n && input[i].isDigit()) i++
                    }
                    tokens.add(Token(TokenType.NUMBER, input.substring(start, i)))
                }
                c.isLetter() || c == '$' || c == '_' -> {
                    val start = i
                    while (i < n && (input[i].isLetterOrDigit() || input[i] == '$' || input[i] == '_' || input[i] == '.')) {
                        i++
                    }
                    if (i < n && input[i] == '!') {
                        i++
                        while (i < n && (input[i].isLetterOrDigit() || input[i] == '$')) i++
                        tokens.add(Token(TokenType.CELL_OR_SHEET, input.substring(start, i)))
                    } else {
                        tokens.add(Token(TokenType.IDENT, input.substring(start, i)))
                    }
                }
                else -> i++
            }
        }
        tokens.add(Token(TokenType.EOF, ""))
        return tokens
    }

    private class Parser(
        private val tokens: List<Token>,
        private val sheets: List<SheetData>,
        private val currentSheetIdx: Int,
        private val cache: MutableMap<Pair<Int, CellAddress>, EvalValue>,
        private val visiting: MutableSet<Pair<Int, CellAddress>>
    ) {
        private var pos = 0

        private fun peek(): Token = tokens.getOrElse(pos) { Token(TokenType.EOF, "") }
        private fun consume(): Token = tokens.getOrElse(pos++) { Token(TokenType.EOF, "") }

        fun parseComparison(): EvalValue {
            var left = parseConcat()
            while (peek().type == TokenType.CMP) {
                val op = consume().text
                val right = parseConcat()
                left = compareValues(left, right, op)
            }
            return left
        }

        private fun parseConcat(): EvalValue {
            var left = parseAddSub()
            while (peek().type == TokenType.OP && peek().text == "&") {
                consume()
                val right = parseAddSub()
                if (left is EvalValue.Err) return left
                if (right is EvalValue.Err) return right
                left = EvalValue.Str(asString(left) + asString(right))
            }
            return left
        }

        private fun parseAddSub(): EvalValue {
            var left = parseMulDiv()
            while (peek().type == TokenType.OP && (peek().text == "+" || peek().text == "-")) {
                val op = consume().text
                val right = parseMulDiv()
                left = numericOp(left, right) { a, b -> if (op == "+") a + b else a - b }
            }
            return left
        }

        private fun parseMulDiv(): EvalValue {
            var left = parsePower()
            while (peek().type == TokenType.OP && (peek().text == "*" || peek().text == "/")) {
                val op = consume().text
                val right = parsePower()
                left = if (op == "/") {
                    val err = checkErr(left, right)
                    if (err != null) err else {
                        val b = asNumber(right)
                        val a = asNumber(left)
                        if (a == null || b == null) EvalValue.Err("#VALUE!")
                        else if (b == 0.0) EvalValue.Err("#DIV/0!")
                        else EvalValue.Num(a / b)
                    }
                } else {
                    numericOp(left, right) { a, b -> a * b }
                }
            }
            return left
        }

        private fun parsePower(): EvalValue {
            val base = parseUnary()
            if (peek().type == TokenType.OP && peek().text == "^") {
                consume()
                val exp = parsePower()
                return numericOp(base, exp) { a, b -> a.pow(b) }
            }
            return base
        }

        private fun parseUnary(): EvalValue {
            if (peek().type == TokenType.OP && (peek().text == "+" || peek().text == "-")) {
                val op = consume().text
                val inner = parseUnary()
                if (inner is EvalValue.Err) return inner
                val num = asNumber(inner) ?: return EvalValue.Err("#VALUE!")
                return EvalValue.Num(if (op == "-") -num else num)
            }
            return parsePostfix()
        }

        private fun parsePostfix(): EvalValue {
            var value = parsePrimary()
            while (peek().type == TokenType.PERCENT) {
                consume()
                if (value is EvalValue.Err) return value
                val num = asNumber(value) ?: return EvalValue.Err("#VALUE!")
                value = EvalValue.Num(num / 100.0)
            }
            return value
        }

        private fun parsePrimary(): EvalValue {
            val tok = peek()
            return when (tok.type) {
                TokenType.NUMBER -> {
                    consume()
                    EvalValue.Num(tok.text.toDoubleOrNull() ?: 0.0)
                }
                TokenType.STRING -> {
                    consume()
                    EvalValue.Str(tok.text)
                }
                TokenType.LPAREN -> {
                    consume()
                    val v = parseComparison()
                    if (peek().type == TokenType.RPAREN) consume()
                    v
                }
                TokenType.CELL_OR_SHEET, TokenType.IDENT -> {
                    consume()
                    // Function call if followed by '('
                    if (tok.type == TokenType.IDENT && peek().type == TokenType.LPAREN) {
                        consume() // '('
                        val args = ArrayList<EvalValue>()
                        if (peek().type != TokenType.RPAREN) {
                            while (true) {
                                args.add(parseComparison())
                                if (peek().type == TokenType.COMMA) {
                                    consume()
                                } else {
                                    break
                                }
                            }
                        }
                        if (peek().type == TokenType.RPAREN) consume()
                        return evalFunction(tok.text.uppercase(Locale.ROOT), args)
                    }

                    // Boolean constants
                    if (tok.text.equals("TRUE", ignoreCase = true)) return EvalValue.Bool(true)
                    if (tok.text.equals("FALSE", ignoreCase = true)) return EvalValue.Bool(false)

                    // Check range A1:B5 or Sheet2!A1:B5
                    val firstRef = resolveCellRef(tok.text)
                    if (peek().type == TokenType.COLON) {
                        consume()
                        val endTok = consume()
                        val secondRef = resolveCellRef(endTok.text, fallbackSheetIdx = firstRef?.first)
                        if (firstRef == null || secondRef == null) {
                            return EvalValue.Err("#REF!")
                        }
                        val targetSheet = firstRef.first
                        val r1 = minOf(firstRef.second.row, secondRef.second.row)
                        val r2 = maxOf(firstRef.second.row, secondRef.second.row)
                        val c1 = minOf(firstRef.second.col, secondRef.second.col)
                        val c2 = maxOf(firstRef.second.col, secondRef.second.col)
                        val items = ArrayList<EvalValue>()
                        for (r in r1..r2) {
                            for (c in c1..c2) {
                                items.add(evalCell(sheets, targetSheet, CellAddress(r, c), cache, visiting))
                            }
                        }
                        return EvalValue.Range(items, rows = r2 - r1 + 1, cols = c2 - c1 + 1)
                    }

                    if (firstRef == null) return EvalValue.Err("#NAME?")
                    evalCell(sheets, firstRef.first, firstRef.second, cache, visiting)
                }
                else -> EvalValue.Err("#ERROR!")
            }
        }

        private fun resolveCellRef(text: String, fallbackSheetIdx: Int? = null): Pair<Int, CellAddress>? {
            val bangIdx = text.indexOf('!')
            if (bangIdx > 0) {
                val sheetName = text.substring(0, bangIdx).trim('\'')
                val addrStr = text.substring(bangIdx + 1)
                val sIdx = sheets.indexOfFirst { it.name.equals(sheetName, ignoreCase = true) }
                if (sIdx < 0) return null
                val addr = CellAddress.fromA1(addrStr) ?: return null
                return sIdx to addr
            }
            val addr = CellAddress.fromA1(text) ?: return null
            return (fallbackSheetIdx ?: currentSheetIdx) to addr
        }

        private fun evalFunction(name: String, args: List<EvalValue>): EvalValue {
            fun flatten(list: List<EvalValue>): List<EvalValue> {
                val out = ArrayList<EvalValue>()
                for (item in list) {
                    if (item is EvalValue.Range) out.addAll(item.items) else out.add(item)
                }
                return out
            }

            fun numbersOf(list: List<EvalValue>, includeLiterals: Boolean = false): Pair<List<Double>, EvalValue.Err?> {
                val nums = ArrayList<Double>()
                for (item in list) {
                    when (item) {
                        is EvalValue.Range -> {
                            for (sub in item.items) {
                                if (sub is EvalValue.Err) return emptyList<Double>() to sub
                                if (sub is EvalValue.Num) nums.add(sub.value)
                            }
                        }
                        is EvalValue.Err -> return emptyList<Double>() to item
                        is EvalValue.Num -> nums.add(item.value)
                        is EvalValue.Bool -> if (includeLiterals) nums.add(if (item.value) 1.0 else 0.0)
                        is EvalValue.Str -> if (includeLiterals) item.value.toDoubleOrNull()?.let { nums.add(it) }
                        is EvalValue.Blank -> Unit
                    }
                }
                return nums to null
            }

            return when (name) {
                "SUM" -> {
                    val (nums, err) = numbersOf(args, includeLiterals = true)
                    err ?: EvalValue.Num(nums.sum())
                }
                "AVERAGE", "AVG" -> {
                    val (nums, err) = numbersOf(args, includeLiterals = true)
                    if (err != null) err
                    else if (nums.isEmpty()) EvalValue.Err("#DIV/0!")
                    else EvalValue.Num(nums.sum() / nums.size)
                }
                "MIN" -> {
                    val (nums, err) = numbersOf(args, includeLiterals = true)
                    err ?: EvalValue.Num(nums.minOrNull() ?: 0.0)
                }
                "MAX" -> {
                    val (nums, err) = numbersOf(args, includeLiterals = true)
                    err ?: EvalValue.Num(nums.maxOrNull() ?: 0.0)
                }
                "COUNT" -> {
                    val (nums, _) = numbersOf(args, includeLiterals = true)
                    EvalValue.Num(nums.size.toDouble())
                }
                "COUNTA" -> {
                    val flat = flatten(args)
                    val count = flat.count { it !is EvalValue.Blank && !(it is EvalValue.Str && it.value.isEmpty()) }
                    EvalValue.Num(count.toDouble())
                }
                "COUNTBLANK" -> {
                    val flat = flatten(args)
                    val count = flat.count { it is EvalValue.Blank || (it is EvalValue.Str && it.value.isEmpty()) }
                    EvalValue.Num(count.toDouble())
                }
                "PRODUCT" -> {
                    val (nums, err) = numbersOf(args, includeLiterals = true)
                    if (err != null) err
                    else if (nums.isEmpty()) EvalValue.Num(0.0)
                    else EvalValue.Num(nums.fold(1.0) { acc, d -> acc * d })
                }
                "ABS" -> singleNumber(args) { abs(it) }
                "SQRT" -> singleNumber(args) { if (it < 0) return EvalValue.Err("#NUM!") else sqrt(it) }
                "INT" -> singleNumber(args) { floor(it) }
                "ROUND" -> twoNumbers(args, defaultSecond = 0.0) { v, d ->
                    val places = d.toInt().coerceIn(-10, 10)
                    if (places >= 0) {
                        BigDecimal.valueOf(v).setScale(places, RoundingMode.HALF_UP).toDouble()
                    } else {
                        val factor = 10.0.pow(-places)
                        round(v / factor) * factor
                    }
                }
                "ROUNDUP" -> twoNumbers(args, defaultSecond = 0.0) { v, d ->
                    val factor = 10.0.pow(d.toInt().coerceIn(-10, 10))
                    if (v >= 0) ceil(v * factor) / factor else floor(v * factor) / factor
                }
                "ROUNDDOWN" -> twoNumbers(args, defaultSecond = 0.0) { v, d ->
                    val factor = 10.0.pow(d.toInt().coerceIn(-10, 10))
                    if (v >= 0) floor(v * factor) / factor else ceil(v * factor) / factor
                }
                "MOD" -> twoNumbers(args) { a, b ->
                    if (b == 0.0) return EvalValue.Err("#DIV/0!")
                    val r = a % b
                    if (r != 0.0 && (r < 0) != (b < 0)) r + b else r
                }
                "POWER" -> twoNumbers(args) { a, b -> a.pow(b) }
                "IF" -> {
                    if (args.size < 2) return EvalValue.Err("#VALUE!")
                    val cond = args[0]
                    if (cond is EvalValue.Err) return cond
                    val isTrue = asBoolean(cond) ?: return EvalValue.Err("#VALUE!")
                    if (isTrue) args[1] else args.getOrElse(2) { EvalValue.Bool(false) }
                }
                "IFERROR" -> {
                    if (args.size < 2) return EvalValue.Err("#VALUE!")
                    if (args[0] is EvalValue.Err) args[1] else args[0]
                }
                "AND" -> {
                    val flat = flatten(args)
                    var anyBool = false
                    for (item in flat) {
                        if (item is EvalValue.Err) return item
                        if (item is EvalValue.Blank) continue
                        val b = asBoolean(item) ?: return EvalValue.Err("#VALUE!")
                        anyBool = true
                        if (!b) return EvalValue.Bool(false)
                    }
                    if (!anyBool) EvalValue.Err("#VALUE!") else EvalValue.Bool(true)
                }
                "OR" -> {
                    val flat = flatten(args)
                    var anyBool = false
                    for (item in flat) {
                        if (item is EvalValue.Err) return item
                        if (item is EvalValue.Blank) continue
                        val b = asBoolean(item) ?: return EvalValue.Err("#VALUE!")
                        anyBool = true
                        if (b) return EvalValue.Bool(true)
                    }
                    if (!anyBool) EvalValue.Err("#VALUE!") else EvalValue.Bool(false)
                }
                "NOT" -> {
                    val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
                    if (first is EvalValue.Err) return first
                    val b = asBoolean(first) ?: return EvalValue.Err("#VALUE!")
                    EvalValue.Bool(!b)
                }
                "ISBLANK" -> {
                    val first = args.firstOrNull() ?: EvalValue.Blank
                    EvalValue.Bool(first is EvalValue.Blank)
                }
                "ISNUMBER" -> {
                    val first = args.firstOrNull() ?: EvalValue.Blank
                    EvalValue.Bool(first is EvalValue.Num)
                }
                "ISTEXT" -> {
                    val first = args.firstOrNull() ?: EvalValue.Blank
                    EvalValue.Bool(first is EvalValue.Str)
                }
                "SUMIF" -> evalSumIf(args)
                "COUNTIF" -> evalCountIf(args)
                "SUMPRODUCT" -> evalSumProduct(args)
                "VLOOKUP" -> evalLookup(args, vertical = true)
                "HLOOKUP" -> evalLookup(args, vertical = false)
                "CONCAT", "CONCATENATE" -> {
                    val flat = flatten(args)
                    val sb = StringBuilder()
                    for (item in flat) {
                        if (item is EvalValue.Err) return item
                        sb.append(asString(item))
                    }
                    EvalValue.Str(sb.toString())
                }
                "LEN" -> {
                    val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
                    if (first is EvalValue.Err) return first
                    EvalValue.Num(asString(first).length.toDouble())
                }
                "UPPER" -> {
                    val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
                    if (first is EvalValue.Err) return first
                    EvalValue.Str(asString(first).uppercase(Locale.ROOT))
                }
                "LOWER" -> {
                    val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
                    if (first is EvalValue.Err) return first
                    EvalValue.Str(asString(first).lowercase(Locale.ROOT))
                }
                "TRIM" -> {
                    val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
                    if (first is EvalValue.Err) return first
                    EvalValue.Str(asString(first).trim().replace(Regex("\\s+"), " "))
                }
                "LEFT" -> {
                    val text = asString(args.getOrNull(0) ?: return EvalValue.Err("#VALUE!"))
                    val count = asNumber(args.getOrNull(1) ?: EvalValue.Num(1.0))?.toInt() ?: 1
                    EvalValue.Str(text.take(count.coerceAtLeast(0)))
                }
                "RIGHT" -> {
                    val text = asString(args.getOrNull(0) ?: return EvalValue.Err("#VALUE!"))
                    val count = asNumber(args.getOrNull(1) ?: EvalValue.Num(1.0))?.toInt() ?: 1
                    EvalValue.Str(text.takeLast(count.coerceAtLeast(0)))
                }
                "MID" -> {
                    val text = asString(args.getOrNull(0) ?: return EvalValue.Err("#VALUE!"))
                    val start = (asNumber(args.getOrNull(1) ?: return EvalValue.Err("#VALUE!"))?.toInt() ?: 1) - 1
                    val len = asNumber(args.getOrNull(2) ?: return EvalValue.Err("#VALUE!"))?.toInt() ?: 0
                    if (start < 0 || len < 0) return EvalValue.Err("#VALUE!")
                    val safeStart = start.coerceAtMost(text.length)
                    val safeEnd = (safeStart + len).coerceAtMost(text.length)
                    EvalValue.Str(text.substring(safeStart, safeEnd))
                }
                "VALUE" -> {
                    val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
                    val num = asNumber(first) ?: return EvalValue.Err("#VALUE!")
                    EvalValue.Num(num)
                }
                "TEXT" -> {
                    val v = args.getOrNull(0) ?: return EvalValue.Err("#VALUE!")
                    val fmt = asString(args.getOrNull(1) ?: EvalValue.Str(""))
                    val num = asNumber(v)
                    if (num != null) EvalValue.Str(formatNumber(num, fmt)) else EvalValue.Str(asString(v))
                }
                "TODAY" -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
                    EvalValue.Str(sdf.format(Date()))
                }
                "NOW" -> {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)
                    EvalValue.Str(sdf.format(Date()))
                }
                "TRUE" -> EvalValue.Bool(true)
                "FALSE" -> EvalValue.Bool(false)
                else -> EvalValue.Err("#NAME?")
            }
        }

        private fun evalCountIf(args: List<EvalValue>): EvalValue {
            if (args.size < 2) return EvalValue.Err("#VALUE!")
            val range = args[0]
            val criteria = args[1]
            val items = if (range is EvalValue.Range) range.items else listOf(range)
            return EvalValue.Num(items.count { matchesCriteria(it, criteria) }.toDouble())
        }

        private fun evalSumIf(args: List<EvalValue>): EvalValue {
            if (args.size < 2) return EvalValue.Err("#VALUE!")
            val rangeItems = (args[0] as? EvalValue.Range)?.items ?: listOf(args[0])
            val criteria = args[1]
            val sumItems = if (args.size >= 3) {
                (args[2] as? EvalValue.Range)?.items ?: listOf(args[2])
            } else {
                rangeItems
            }
            var total = 0.0
            for (i in rangeItems.indices) {
                if (matchesCriteria(rangeItems[i], criteria)) {
                    val target = sumItems.getOrNull(i)
                    if (target is EvalValue.Num) total += target.value
                }
            }
            return EvalValue.Num(total)
        }

        private fun evalSumProduct(args: List<EvalValue>): EvalValue {
            if (args.isEmpty()) return EvalValue.Err("#VALUE!")
            val arrays = args.map { if (it is EvalValue.Range) it.items else listOf(it) }
            val len = arrays.first().size
            if (arrays.any { it.size != len }) return EvalValue.Err("#VALUE!")
            var sum = 0.0
            for (i in 0 until len) {
                var prod = 1.0
                for (arr in arrays) {
                    val v = arr[i]
                    if (v is EvalValue.Err) return v
                    val n = if (v is EvalValue.Num) v.value else 0.0
                    prod *= n
                }
                sum += prod
            }
            return EvalValue.Num(sum)
        }

        private fun evalLookup(args: List<EvalValue>, vertical: Boolean): EvalValue {
            if (args.size < 3) return EvalValue.Err("#VALUE!")
            val lookupVal = args[0]
            val table = args[1] as? EvalValue.Range ?: return EvalValue.Err("#VALUE!")
            val indexNum = asNumber(args[2])?.toInt() ?: return EvalValue.Err("#VALUE!")
            val exactOnly = if (args.size >= 4) (asBoolean(args[3]) == false) else false

            if (vertical) {
                if (indexNum < 1 || indexNum > table.cols) return EvalValue.Err("#REF!")
                for (r in 0 until table.rows) {
                    val key = table.items.getOrNull(r * table.cols) ?: EvalValue.Blank
                    if (valuesEqual(key, lookupVal)) {
                        return table.items.getOrNull(r * table.cols + (indexNum - 1)) ?: EvalValue.Blank
                    }
                }
                if (!exactOnly && lookupVal is EvalValue.Num) {
                    var bestRow = -1
                    var bestVal = Double.NEGATIVE_INFINITY
                    for (r in 0 until table.rows) {
                        val key = table.items.getOrNull(r * table.cols)
                        if (key is EvalValue.Num && key.value <= lookupVal.value && key.value >= bestVal) {
                            bestVal = key.value
                            bestRow = r
                        }
                    }
                    if (bestRow >= 0) {
                        return table.items.getOrNull(bestRow * table.cols + (indexNum - 1)) ?: EvalValue.Blank
                    }
                }
            } else {
                if (indexNum < 1 || indexNum > table.rows) return EvalValue.Err("#REF!")
                for (c in 0 until table.cols) {
                    val key = table.items.getOrNull(c) ?: EvalValue.Blank
                    if (valuesEqual(key, lookupVal)) {
                        return table.items.getOrNull((indexNum - 1) * table.cols + c) ?: EvalValue.Blank
                    }
                }
            }
            return EvalValue.Err("#N/A")
        }

        private fun matchesCriteria(value: EvalValue, criteria: EvalValue): Boolean {
            if (criteria is EvalValue.Str) {
                val c = criteria.value.trim()
                val ops = listOf(">=", "<=", "<>", ">", "<", "=")
                for (op in ops) {
                    if (c.startsWith(op)) {
                        val rhsStr = c.removePrefix(op).trim()
                        val rhsVal = rhsStr.toDoubleOrNull()?.let { EvalValue.Num(it) } ?: EvalValue.Str(rhsStr)
                        val res = compareValues(value, rhsVal, op)
                        return res is EvalValue.Bool && res.value
                    }
                }
            }
            return valuesEqual(value, criteria)
        }

        private fun valuesEqual(a: EvalValue, b: EvalValue): Boolean {
            if (a is EvalValue.Num && b is EvalValue.Num) return abs(a.value - b.value) < 1e-9
            if (a is EvalValue.Bool && b is EvalValue.Bool) return a.value == b.value
            if (a is EvalValue.Blank && b is EvalValue.Blank) return true
            return asString(a).equals(asString(b), ignoreCase = true)
        }

        private inline fun singleNumber(args: List<EvalValue>, op: (Double) -> Double): EvalValue {
            val first = args.firstOrNull() ?: return EvalValue.Err("#VALUE!")
            if (first is EvalValue.Err) return first
            val n = asNumber(first) ?: return EvalValue.Err("#VALUE!")
            val res = op(n)
            return if (res.isNaN() || res.isInfinite()) EvalValue.Err("#NUM!") else EvalValue.Num(res)
        }

        private inline fun twoNumbers(
            args: List<EvalValue>,
            defaultSecond: Double? = null,
            op: (Double, Double) -> Double
        ): EvalValue {
            val first = args.getOrNull(0) ?: return EvalValue.Err("#VALUE!")
            val second = args.getOrNull(1) ?: defaultSecond?.let { EvalValue.Num(it) } ?: return EvalValue.Err("#VALUE!")
            if (first is EvalValue.Err) return first
            if (second is EvalValue.Err) return second
            val a = asNumber(first) ?: return EvalValue.Err("#VALUE!")
            val b = asNumber(second) ?: return EvalValue.Err("#VALUE!")
            val res = op(a, b)
            return if (res.isNaN() || res.isInfinite()) EvalValue.Err("#NUM!") else EvalValue.Num(res)
        }

        private fun checkErr(a: EvalValue, b: EvalValue): EvalValue.Err? {
            if (a is EvalValue.Err) return a
            if (b is EvalValue.Err) return b
            return null
        }

        private inline fun numericOp(a: EvalValue, b: EvalValue, op: (Double, Double) -> Double): EvalValue {
            checkErr(a, b)?.let { return it }
            val na = asNumber(a) ?: return EvalValue.Err("#VALUE!")
            val nb = asNumber(b) ?: return EvalValue.Err("#VALUE!")
            val res = op(na, nb)
            return if (res.isNaN() || res.isInfinite()) EvalValue.Err("#NUM!") else EvalValue.Num(res)
        }

        private fun compareValues(a: EvalValue, b: EvalValue, op: String): EvalValue {
            checkErr(a, b)?.let { return it }
            val cmp = when {
                a is EvalValue.Num && b is EvalValue.Num -> a.value.compareTo(b.value)
                a is EvalValue.Num && b is EvalValue.Blank -> a.value.compareTo(0.0)
                a is EvalValue.Blank && b is EvalValue.Num -> 0.0.compareTo(b.value)
                a is EvalValue.Bool && b is EvalValue.Bool -> a.value.compareTo(b.value)
                else -> asString(a).compareTo(asString(b), ignoreCase = true)
            }
            val res = when (op) {
                "=" -> cmp == 0
                "<>" -> cmp != 0
                "<" -> cmp < 0
                "<=" -> cmp <= 0
                ">" -> cmp > 0
                ">=" -> cmp >= 0
                else -> false
            }
            return EvalValue.Bool(res)
        }

        private fun asNumber(v: EvalValue): Double? = when (v) {
            is EvalValue.Blank -> 0.0
            is EvalValue.Num -> v.value
            is EvalValue.Bool -> if (v.value) 1.0 else 0.0
            is EvalValue.Str -> if (v.value.isBlank()) 0.0 else v.value.trim().toDoubleOrNull()
            is EvalValue.Range -> v.items.firstOrNull()?.let { asNumber(it) }
            is EvalValue.Err -> null
        }

        private fun asBoolean(v: EvalValue): Boolean? = when (v) {
            is EvalValue.Blank -> false
            is EvalValue.Bool -> v.value
            is EvalValue.Num -> v.value != 0.0
            is EvalValue.Str -> when {
                v.value.equals("TRUE", ignoreCase = true) -> true
                v.value.equals("FALSE", ignoreCase = true) -> false
                else -> null
            }
            is EvalValue.Range -> v.items.firstOrNull()?.let { asBoolean(it) }
            is EvalValue.Err -> null
        }

        private fun asString(v: EvalValue): String = when (v) {
            is EvalValue.Blank -> ""
            is EvalValue.Str -> v.value
            is EvalValue.Num -> formatNumber(v.value)
            is EvalValue.Bool -> if (v.value) "TRUE" else "FALSE"
            is EvalValue.Err -> v.code
            is EvalValue.Range -> v.items.firstOrNull()?.let { asString(it) } ?: ""
        }
    }
}
