package org.cgutman.usbip.server.protocol.cli

import java.io.InputStream
import java.nio.ByteBuffer
import org.cgutman.usbip.server.protocol.ProtoDefs
import org.cgutman.usbip.utils.StreamUtils

abstract class CommonPacket {
    var version: Short = 0
    var code: Short = 0
    var status: Int = 0

    constructor(header: ByteArray) {
        val bb = ByteBuffer.wrap(header)

        version = bb.short
        code = bb.short
        status = bb.int
    }

    constructor(version: Short, code: Short, status: Int) {
        this.version = version
        this.code = code
        this.status = status
    }

    protected abstract fun serializeInternal(): ByteArray?

    fun serialize(): ByteArray {
        val internalData = serializeInternal()

        val internalLen = internalData?.size ?: 0
        val bb = ByteBuffer.allocate(8 + internalLen)

        bb.putShort(version)
        bb.putShort(code)
        bb.putInt(status)

        if (internalLen != 0) {
            bb.put(internalData)
        }

        return bb.array()
    }

    companion object {
        @JvmStatic
        fun read(`in`: InputStream): CommonPacket? {
            val bb = ByteBuffer.allocate(8)
            StreamUtils.readAll(`in`, bb.array())

            // We should check the version here, but it seems they like to
            // increment it without actually changing the protocol, so I'm
            // not going to.
            bb.short

            val code = bb.short
            val pkt: CommonPacket? = when (code) {
                ProtoDefs.OP_REQ_DEVLIST -> DevListRequest(bb.array())
                ProtoDefs.OP_REQ_IMPORT -> {
                    val req = ImportDeviceRequest(bb.array())
                    req.populateInternal(`in`)
                    req
                }
                else -> {
                    System.err.println("Unsupported code: $code")
                    null
                }
            }

            return pkt
        }
    }
}
