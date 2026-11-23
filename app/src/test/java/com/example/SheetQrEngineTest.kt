package com.example

import com.example.engine.FormulaEvaluator
import com.example.engine.QrisParser
import com.example.model.CellAddress
import com.example.model.CellData
import com.example.model.MergeRange
import com.example.model.SheetData
import com.example.model.WorkbookData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetQrEngineTest {

    @Test
    fun `formula evaluator computes SUM AVERAGE IF COUNTIF and cross sheet`() {
        val sheet1 = SheetData(
            name = "Data",
            cells = mapOf(
                CellAddress(0, 0) to CellData("Item"),
                CellAddress(0, 1) to CellData("Harga"),
                CellAddress(1, 0) to CellData("Kopi"),
                CellAddress(1, 1) to CellData("25000"),
                CellAddress(2, 0) to CellData("Teh"),
                CellAddress(2, 1) to CellData("15000"),
                CellAddress(3, 1) to CellData("=SUM(B2:B3)", formula = "SUM(B2:B3)"),
                CellAddress(4, 1) to CellData("=AVERAGE(B2:B3)", formula = "AVERAGE(B2:B3)"),
                CellAddress(5, 1) to CellData("=IF(B4>=30000,\"Target\",\"Kurang\")", formula = "IF(B4>=30000,\"Target\",\"Kurang\")"),
                CellAddress(6, 1) to CellData("=COUNTIF(B2:B3,\">20000\")", formula = "COUNTIF(B2:B3,\">20000\")")
            )
        )
        val sheet2 = SheetData(
            name = "Rekap",
            cells = mapOf(
                CellAddress(0, 0) to CellData("=Data!B4*2", formula = "Data!B4*2")
            )
        )
        val evaluated = FormulaEvaluator.evaluateWorkbook(
            WorkbookData(sheets = listOf(sheet1, sheet2))
        )

        assertEquals("40000", evaluated.sheets[0].getCell(CellAddress(3, 1)).cachedDisplay)
        assertEquals("20000", evaluated.sheets[0].getCell(CellAddress(4, 1)).cachedDisplay)
        assertEquals("Target", evaluated.sheets[0].getCell(CellAddress(5, 1)).cachedDisplay)
        assertEquals("1", evaluated.sheets[0].getCell(CellAddress(6, 1)).cachedDisplay)
        assertEquals("80000", evaluated.sheets[1].getCell(CellAddress(0, 0)).cachedDisplay)
    }

    @Test
    fun `formula evaluator catches circular reference`() {
        val sheet = SheetData(
            name = "Sheet1",
            cells = mapOf(
                CellAddress(0, 0) to CellData("=B1", formula = "B1"),
                CellAddress(0, 1) to CellData("=A1", formula = "A1")
            )
        )
        val evaluated = FormulaEvaluator.evaluateWorkbook(WorkbookData(sheets = listOf(sheet)))
        val cellA1 = evaluated.sheets[0].getCell(CellAddress(0, 0))
        assertTrue(cellA1.isError)
        assertEquals("#CIRC!", cellA1.cachedDisplay)
    }

    @Test
    fun `cell address and merge range conversions work accurately`() {
        val addr = CellAddress.fromA1("AA12")
        assertEquals(CellAddress(11, 26), addr)
        assertEquals("AA12", addr!!.toA1())

        val mr = MergeRange.fromA1Range("B2:D4")
        assertEquals(MergeRange(1, 1, 3, 3), mr)
        assertEquals("B2:D4", mr!!.toA1Range())
    }

    @Test
    fun `qris parser extracts merchant city amount nmid and validates crc16`() {
        val bodyWithoutCrc =
            "000201010212" +
                "26380015ID.CO.GOPAY.WWW0115936009143000123" +
                "51320014ID.CO.QRIS.WWW0210ID10203040" +
                "52045499" +
                "5303360" +
                "540550000" +
                "5802ID" +
                "5914TOKO NUSANTARA" +
                "6007JAKARTA" +
                "6304"
        val crc = QrisParser.computeCrc16Ccitt(bodyWithoutCrc)
        val fullPayload = bodyWithoutCrc + crc

        val parsed = QrisParser.parse(fullPayload)
        assertTrue(parsed.isEmvQris)
        assertTrue(parsed.crcValid)
        assertTrue(parsed.isDynamic)
        assertEquals("TOKO NUSANTARA", parsed.merchantName)
        assertEquals("JAKARTA", parsed.merchantCity)
        assertEquals("50000", parsed.amount)
        assertEquals(50000.0, parsed.numericAmount!!, 0.001)
        assertEquals("ID10203040", parsed.nmid)

        val plainText = QrisParser.parse("https://example.com/menu")
        assertFalse(plainText.isEmvQris)
    }
}
