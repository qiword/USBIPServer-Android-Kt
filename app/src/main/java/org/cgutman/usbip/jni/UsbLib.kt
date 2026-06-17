package org.cgutman.usbip.jni

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.lang.reflect.Method
import java.nio.ByteBuffer

object UsbLib {

    private const val TAG = "UsbLib"
    private const val USBDEVFS_CONTROL = 0xc0185500L
    private const val USBDEVFS_BULK = 0xc0185502L

    // --- Access to DirectByteBuffer cleaner (sun.misc.Unsafe / jdk.internal.ref.Cleaner) ---

    /** Manually free a DirectByteBuffer. Safe to call multiple times; does nothing on heap buffers. */
    private fun freeDirectBuffer(buf: ByteBuffer) {
        try {
            // Try java.nio.DirectByteBuffer "cleaner" field (Java 9+)
            val cleanerField = buf.javaClass.getDeclaredField("cleaner")
            cleanerField.isAccessible = true
            val cleaner = cleanerField.get(buf)
            if (cleaner != null) {
                val clean = cleaner.javaClass.getDeclaredMethod("clean")
                clean.invoke(cleaner)
            }
        } catch (e: NoSuchFieldException) {
            // Java 8 fallback: sun.misc.Cleaner via sun.nio.ch.DirectBuffer
            try {
                val directBuf = buf.javaClass.getMethod("cleaner")
                val cleaner = directBuf.invoke(buf)
                if (cleaner != null) {
                    cleaner.javaClass.getMethod("clean").invoke(cleaner)
                }
            } catch (_: Exception) {
            }
        } catch (_: Exception) {
        }
    }

    @Structure.FieldOrder("bmRequestType", "bRequest", "wValue", "wIndex", "wLength", "timeout", "data")
    class CtrlTransfer : Structure() {
        @JvmField
        var bmRequestType: Byte = 0
        @JvmField
        var bRequest: Byte = 0
        @JvmField
        var wValue: Short = 0
        @JvmField
        var wIndex: Short = 0
        @JvmField
        var wLength: Short = 0
        @JvmField
        var timeout: Int = 0
        @JvmField
        var data: Pointer? = null
    }

    @Structure.FieldOrder("ep", "len", "timeout", "data")
    class BulkTransfer : Structure() {
        @JvmField
        var ep: Int = 0
        @JvmField
        var len: Int = 0
        @JvmField
        var timeout: Int = 0
        @JvmField
        var data: Pointer? = null
    }

    private interface LibC : Library {
        fun ioctl(fd: Int, request: Long, vararg args: Any?): Int
    }

    private val libc: LibC = Native.load("c", LibC::class.java)

    @JvmStatic
    fun doControlTransfer(
        fd: Int, requestType: Int, request: Int,
        value: Int, index: Int, data: ByteArray, timeout: Int
    ): Int {
        val mem = ByteBuffer.allocateDirect(data.size)
        try {
            if ((requestType and 0x80) == 0) {
                mem.put(data)
                mem.flip()
            }

            val ctrl = CtrlTransfer().apply {
                bmRequestType = requestType.toByte()
                bRequest = request.toByte()
                wValue = value.toShort()
                wIndex = index.toShort()
                wLength = data.size.toShort()
                this.timeout = timeout
                this.data = Native.getDirectBufferPointer(mem)
                write()
            }

            val ret = ctrl.usePointer { ptr ->
                libc.ioctl(fd, USBDEVFS_CONTROL, ptr)
            }

            if (ret < 0) {
                val errno = Native.getLastError()
                Log.w(TAG, "doControlTransfer: ioctl failed fd=$fd errno=$errno")
                return ret
            }

            if ((requestType and 0x80) != 0 && ret > 0) {
                mem.rewind()
                mem.get(data, 0, minOf(ret, data.size))
            }

            return ret
        } catch (e: Exception) {
            Log.e(TAG, "doControlTransfer: exception", e)
            return -1
        } finally {
            freeDirectBuffer(mem)
        }
    }

    @JvmStatic
    fun doBulkTransfer(fd: Int, endpoint: Int, data: ByteArray, timeout: Int): Int {
        val mem = ByteBuffer.allocateDirect(data.size)
        try {
            val epDir = endpoint and 0x80
            if (epDir == 0) {
                mem.put(data)
                mem.flip()
            }

            val bulk = BulkTransfer().apply {
                ep = endpoint
                len = data.size
                this.timeout = timeout
                this.data = Native.getDirectBufferPointer(mem)
                write()
            }

            val ret = bulk.usePointer { ptr ->
                libc.ioctl(fd, USBDEVFS_BULK, ptr)
            }

            if (ret < 0) {
                val errno = Native.getLastError()
                Log.w(TAG, "doBulkTransfer: ioctl failed fd=$fd ep=$endpoint errno=$errno")
                return ret
            }

            if (epDir != 0 && ret > 0) {
                mem.rewind()
                mem.get(data, 0, minOf(ret, data.size))
            }

            return ret
        } catch (e: Exception) {
            Log.e(TAG, "doBulkTransfer: exception", e)
            return -1
        } finally {
            freeDirectBuffer(mem)
        }
    }

    private inline fun <T : Structure> T.usePointer(block: (Pointer) -> Int): Int {
        return try {
            block(pointer)
        } catch (e: Exception) {
            Log.e(TAG, "usePointer: JNA error", e)
            -1
        }
    }
}
