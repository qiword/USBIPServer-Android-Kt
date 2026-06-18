package org.cgutman.usbip.server

import org.cgutman.usbip.server.protocol.ProtoDefs
import org.cgutman.usbip.server.protocol.cli.CommonPacket
import org.cgutman.usbip.server.protocol.cli.DevListReply
import org.cgutman.usbip.server.protocol.cli.ImportDeviceReply
import org.cgutman.usbip.server.protocol.cli.ImportDeviceRequest
import org.cgutman.usbip.server.protocol.dev.UsbIpDevicePacket
import org.cgutman.usbip.server.protocol.dev.UsbIpSubmitUrb
import org.cgutman.usbip.server.protocol.dev.UsbIpUnlinkUrb
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class UsbIpServer {
    private var handler: UsbRequestHandler? = null
    private var serverThread: Thread? = null
    private var serverSock: ServerSocket? = null
    private val connections = ConcurrentHashMap<Socket, Thread>()

    // Returns true if a device is now attached
    @Throws(IOException::class)
    private fun handleRequest(s: Socket): Boolean {
        val inMsg = CommonPacket.read(s.getInputStream()) ?: run {
            s.close()
            return false
        }

        var res = false
        val outMsg: CommonPacket
        println("In code: 0x%x\n".format(inMsg.code))
        if (inMsg.code == ProtoDefs.OP_REQ_DEVLIST) {
            val dlReply = DevListReply(inMsg.version)
            dlReply.devInfoList = handler?.getDevices()
            if (dlReply.devInfoList == null) {
                dlReply.status = ProtoDefs.ST_NA.toInt()
            }
            outMsg = dlReply
        } else if (inMsg.code == ProtoDefs.OP_REQ_IMPORT) {
            val imReq = inMsg as ImportDeviceRequest
            val imReply = ImportDeviceReply(inMsg.version)

            res = handler?.attachToDevice(s, imReq.busid ?: "") ?: false
            if (res) {
                imReply.devInfo = handler?.getDeviceByBusId(imReq.busid ?: "")
                if (imReply.devInfo == null) {
                    res = false
                }
            }

            imReply.status = if (res) ProtoDefs.ST_OK.toInt() else ProtoDefs.ST_NA.toInt()
            outMsg = imReply
        } else {
            return false
        }

        println("Out code: 0x%x\n".format(outMsg.code))
        s.getOutputStream().write(outMsg.serialize())
        return res
    }

    @Throws(IOException::class)
    private fun handleDevRequest(s: Socket): Boolean {
        val inMsg = UsbIpDevicePacket.read(s.getInputStream()) ?: return false

        when (inMsg.command) {
            UsbIpDevicePacket.USBIP_CMD_SUBMIT -> {
                handler?.submitUrbRequest(s, inMsg as UsbIpSubmitUrb)
            }

            UsbIpDevicePacket.USBIP_CMD_UNLINK -> {
                handler?.abortUrbRequest(s, inMsg as UsbIpUnlinkUrb)
            }

            else -> return false
        }

        return true
    }

    fun killClient(s: Socket) {
        val t = connections.remove(s)

        try {
            s.close()
        } catch (_: IOException) {
        }

        t?.interrupt()

        try {
            t?.join()
        } catch (_: InterruptedException) {
        }
    }

    private fun handleClient(s: Socket) {
        val t = Thread {
            try {
                s.tcpNoDelay = true
                s.keepAlive = true
                s.sendBufferSize = 256 * 1024
                s.receiveBufferSize = 256 * 1024

                while (!Thread.currentThread().isInterrupted) {
                    if (handleRequest(s)) {
                        while (handleDevRequest(s)) {
                            // continue processing dev requests
                        }
                    }
                }
            } catch (e: IOException) {
                println("Client disconnected")
            } finally {
                handler?.cleanupSocket(s)

                try {
                    s.close()
                } catch (_: IOException) {
                }
            }
        }

        connections[s] = t
        t.start()
    }

    fun start(handler: UsbRequestHandler) {
        this.handler = handler

        val t = Thread {
            try {
                serverSock = ServerSocket(PORT)
                println("Server listening on $PORT")
                while (!Thread.currentThread().isInterrupted) {
                    handleClient(serverSock!!.accept())
                }
            } catch (e: IOException) {
                e.printStackTrace()
                return@Thread
            }
        }

        serverThread = t
        t.start()
    }

    fun stop() {
        if (serverSock != null) {
            try {
                serverSock!!.close()
            } catch (_: IOException) {
            }

            serverSock = null
        }

        if (serverThread != null) {
            serverThread!!.interrupt()

            try {
                serverThread!!.join()
            } catch (_: InterruptedException) {
            }

            serverThread = null
        }

        for (entry in connections.entries) {
            try {
                entry.key.close()
            } catch (_: IOException) {
            }

            entry.value.interrupt()

            try {
                entry.value.join()
            } catch (_: InterruptedException) {
            }
        }
    }

    companion object {
        const val PORT = 3240
    }
}
