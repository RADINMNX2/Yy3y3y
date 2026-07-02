package com.example.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbMessage(
    val command: Int,
    val arg0: Int,
    val arg1: Int,
    val data: ByteArray = ByteArray(0)
) {
    companion object {
        const val CMD_CNXN = 0x4e584e43 // "CNXN"
        const val CMD_AUTH = 0x48545541 // "AUTH"
        const val CMD_OPEN = 0x4e45504f // "OPEN"
        const val CMD_OKAY = 0x59414b4f // "OKAY"
        const val CMD_CLSE = 0x45534c43 // "CLSE"
        const val CMD_WRTE = 0x45545257 // "WRTE"

        const val HEADER_LENGTH = 24

        fun parseHeader(headerBytes: ByteArray): HeaderData? {
            if (headerBytes.size < HEADER_LENGTH) return null
            val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = buffer.int
            val arg0 = buffer.int
            val arg1 = buffer.int
            val dataLength = buffer.int
            val dataChecksum = buffer.int
            val magic = buffer.int

            if ((command xor magic) != -1) {
                return null // Invalid magic checksum
            }
            return HeaderData(command, arg0, arg1, dataLength, dataChecksum)
        }
    }

    class HeaderData(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val dataLength: Int,
        val dataChecksum: Int
    )

    fun toBytes(): ByteArray {
        val payloadLen = data.size
        val totalLen = HEADER_LENGTH + payloadLen
        val bytes = ByteArray(totalLen)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(command)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payloadLen)
        buffer.putInt(calculateChecksum(data))
        buffer.putInt(command xor -1) // Magic
        buffer.put(data)

        return bytes
    }

    private fun calculateChecksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) {
            sum += b.toInt() and 0xFF
        }
        return sum
    }
}
