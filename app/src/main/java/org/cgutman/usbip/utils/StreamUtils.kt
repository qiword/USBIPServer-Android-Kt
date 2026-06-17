package org.cgutman.usbip.utils

import java.io.IOException
import java.io.InputStream

object StreamUtils {
    @JvmStatic
    @Throws(IOException::class)
    fun readAll(`in`: InputStream, buffer: ByteArray) {
        readAll(`in`, buffer, 0, buffer.size)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readAll(`in`: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var i = 0
        while (i < length) {
            val ret = `in`.read(buffer, offset + i, length - i)
            if (ret <= 0) {
                throw IOException("Read failed: $ret")
            }
            i += ret
        }
    }
}
