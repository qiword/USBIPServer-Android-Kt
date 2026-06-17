package org.cgutman.usbip.server.protocol

object ProtoDefs {
    const val OP_REQUEST: Short = (0x80 shl 8).toShort()
    const val OP_REPLY: Short = (0x00 shl 8).toShort()

    const val ST_OK: Byte = 0x00
    const val ST_NA: Byte = 0x01

    const val OP_IMPORT: Byte = 0x03
    const val OP_REQ_IMPORT: Short = (OP_REQUEST.toInt() or OP_IMPORT.toInt()).toShort()
    const val OP_REP_IMPORT: Short = (OP_REPLY.toInt() or OP_IMPORT.toInt()).toShort()

    const val OP_DEVLIST: Byte = 0x05
    const val OP_REQ_DEVLIST: Short = (OP_REQUEST.toInt() or OP_DEVLIST.toInt()).toShort()
    const val OP_REP_DEVLIST: Short = (OP_REPLY.toInt() or OP_DEVLIST.toInt()).toShort()

    const val OP_EXPORT: Byte = 0x06
    const val OP_REQ_EXPORT: Short = (OP_REQUEST.toInt() or OP_EXPORT.toInt()).toShort()
    const val OP_REP_EXPORT: Short = (OP_REPLY.toInt() or OP_EXPORT.toInt()).toShort()

    const val OP_UNEXPORT: Byte = 0x07
    const val OP_REQ_UNEXPORT: Short = (OP_REQUEST.toInt() or OP_UNEXPORT.toInt()).toShort()
    const val OP_REP_UNEXPORT: Short = (OP_REPLY.toInt() or OP_UNEXPORT.toInt()).toShort()
}
