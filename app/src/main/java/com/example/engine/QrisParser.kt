package com.example.engine

data class ParsedQrisResult(
    val rawPayload: String,
    val isEmvQris: Boolean,
    val crcValid: Boolean,
    val isDynamic: Boolean,
    val merchantName: String? = null,
    val merchantCity: String? = null,
    val amount: String? = null,
    val numericAmount: Double? = null,
    val currencyCode: String? = null,
    val countryCode: String? = null,
    val nmid: String? = null,
    val merchantId: String? = null,
    val merchantCriteria: String? = null,
    val mcc: String? = null,
    val billReference: String? = null,
    val acquirerDomain: String? = null
) {
    fun formatSummary(): String {
        if (!isEmvQris) return rawPayload
        val parts = mutableListOf<String>()
        if (!merchantName.isNullOrBlank()) parts.add(merchantName)
        if (!amount.isNullOrBlank()) parts.add("Rp $amount")
        if (!nmid.isNullOrBlank()) parts.add("NMID:$nmid")
        else if (!merchantId.isNullOrBlank()) parts.add("ID:$merchantId")
        if (!merchantCity.isNullOrBlank() && parts.size < 3) parts.add(merchantCity)
        return if (parts.isNotEmpty()) parts.joinToString(" | ") else rawPayload
    }
}

object QrisParser {

    fun parse(raw: String): ParsedQrisResult {
        val trimmed = raw.trim()
        if (trimmed.length < 12 || !trimmed.startsWith("00")) {
            return ParsedQrisResult(
                rawPayload = trimmed,
                isEmvQris = false,
                crcValid = false,
                isDynamic = false
            )
        }

        val rootTags = parseTlv(trimmed)
        if (rootTags.isEmpty() || rootTags["00"] == null) {
            return ParsedQrisResult(
                rawPayload = trimmed,
                isEmvQris = false,
                crcValid = false,
                isDynamic = false
            )
        }

        val payloadFormat = rootTags["00"]
        val hasQrisMarkers = payloadFormat == "01" &&
            (rootTags.containsKey("58") || rootTags.containsKey("59") || rootTags.containsKey("51"))

        if (!hasQrisMarkers) {
            return ParsedQrisResult(
                rawPayload = trimmed,
                isEmvQris = false,
                crcValid = false,
                isDynamic = false
            )
        }

        val crcValid = verifyCrc16Ccitt(trimmed)
        val pointOfInit = rootTags["01"]
        val isDynamic = pointOfInit == "12"
        val merchantName = rootTags["59"]?.trim()
        val merchantCity = rootTags["60"]?.trim()
        val rawAmount = rootTags["54"]?.trim()
        val numericAmount = rawAmount?.toDoubleOrNull()
        val currencyCode = rootTags["53"]?.trim()
        val countryCode = rootTags["58"]?.trim()
        val mcc = rootTags["52"]?.trim()

        var nmid: String? = null
        var merchantId: String? = null
        var merchantCriteria: String? = null
        var acquirerDomain: String? = null

        // Tag 51 is typically National Merchant ID (NMID) in QRIS
        rootTags["51"]?.let { tag51 ->
            val sub = parseTlv(tag51)
            acquirerDomain = sub["00"] ?: acquirerDomain
            nmid = sub["02"] ?: sub["01"]
            merchantCriteria = sub["03"] ?: merchantCriteria
        }

        // Tags 26..45 hold Acquirer Merchant Account Information
        for (tagNum in 26..45) {
            val tagKey = tagNum.toString().padStart(2, '0')
            rootTags[tagKey]?.let { mai ->
                val sub = parseTlv(mai)
                if (acquirerDomain == null) acquirerDomain = sub["00"]
                if (merchantId == null) merchantId = sub["01"] ?: sub["02"]
                if (merchantCriteria == null) merchantCriteria = sub["03"]
            }
        }

        var billRef: String? = null
        rootTags["62"]?.let { tag62 ->
            val sub = parseTlv(tag62)
            billRef = sub["01"] ?: sub["05"] ?: sub["07"]
        }

        return ParsedQrisResult(
            rawPayload = trimmed,
            isEmvQris = true,
            crcValid = crcValid,
            isDynamic = isDynamic,
            merchantName = merchantName,
            merchantCity = merchantCity,
            amount = rawAmount,
            numericAmount = numericAmount,
            currencyCode = currencyCode,
            countryCode = countryCode,
            nmid = nmid,
            merchantId = merchantId,
            merchantCriteria = merchantCriteria,
            mcc = mcc,
            billReference = billRef,
            acquirerDomain = acquirerDomain
        )
    }

    private fun parseTlv(data: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var idx = 0
        while (idx + 4 <= data.length) {
            val tag = data.substring(idx, idx + 2)
            val lenStr = data.substring(idx + 2, idx + 4)
            val len = lenStr.toIntOrNull() ?: break
            if (len < 0 || idx + 4 + len > data.length) break
            val value = data.substring(idx + 4, idx + 4 + len)
            result[tag] = value
            idx += 4 + len
        }
        return result
    }

    fun verifyCrc16Ccitt(payload: String): Boolean {
        val crcTagIdx = payload.lastIndexOf("6304")
        if (crcTagIdx < 0 || crcTagIdx + 8 != payload.length) return false
        val bodyToHash = payload.substring(0, crcTagIdx + 4)
        val expectedHex = payload.substring(crcTagIdx + 4).uppercase()
        val computed = computeCrc16Ccitt(bodyToHash)
        return computed == expectedHex
    }

    fun computeCrc16Ccitt(input: String): String {
        var crc = 0xFFFF
        for (ch in input) {
            crc = crc xor (ch.code shl 8)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }
}
