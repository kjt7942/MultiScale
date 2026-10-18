package com.kjt7942.grapesorter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * HC-05/HC-06 같은 클래식 블루투스(SPP) 모듈과 연결한다.
 * 끊기면 자동으로 다시 붙는다. 콜백은 백그라운드 스레드에서 불린다.
 */
class BluetoothLink(private val listener: Listener) {

    interface Listener {
        fun onState(state: LinkState, detail: String)
        fun onLine(line: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var output: OutputStream? = null

    /** 이전 연결을 끊고 이 기기로 연결을 시작한다(끊기면 자동 재시도). */
    fun connect(device: BluetoothDevice) {
        disconnect()
        job = scope.launch { runLoop(device) }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        closeSocket()
        listener.onState(LinkState.DISCONNECTED, "")
    }

    fun send(text: String) {
        val out = output ?: return
        scope.launch {
            try {
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } catch (e: IOException) {
                closeSocket() // 읽기 쪽이 예외를 받아 재연결로 넘어간다
            }
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: IOException) {
            // 이미 닫힘
        }
        socket = null
        output = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun runLoop(device: BluetoothDevice) {
        var backoff = 1000L
        val label = try {
            device.name ?: device.address
        } catch (e: SecurityException) {
            device.address
        }
        while (scope.isActive && job?.isActive == true) {
            listener.onState(LinkState.CONNECTING, label)
            try {
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = s
                s.connect()
                output = s.outputStream
                listener.onState(LinkState.CONNECTED, label)
                backoff = 1000L
                val input = s.inputStream
                val buf = ByteArray(1024)
                val assembler = LineAssembler()
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    for (line in assembler.feed(buf, n)) listener.onLine(line)
                }
            } catch (e: IOException) {
                // 연결 실패/끊김: 아래에서 재시도
            } catch (e: SecurityException) {
                listener.onState(LinkState.DISCONNECTED, "블루투스 권한이 없습니다")
                closeSocket()
                return
            }
            closeSocket()
            if (job?.isActive != true) return
            listener.onState(LinkState.DISCONNECTED, label)
            delay(backoff)
            backoff = minOf(backoff * 2, 8000L)
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
