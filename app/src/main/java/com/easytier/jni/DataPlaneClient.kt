package com.easytier.jni

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * EasyTier 数据面操作封装。
 *
 * 将 [EasyTierDataPlaneJNI] 的 `*Start`/`*Finish` 异步配对封装为 suspend 函数，
 * 内部用 [withContext] 切到 IO 线程避免阻塞主线程，并正确管理 op handle 生命周期：
 *   - 成功路径：`Start` → `Wait` → `Finish`（Finish 消费 op）
 *   - 失败/异常路径：`Cancel` + `Free` 释放 op
 *
 * 操作失败时返回 null，错误详情可通过 [lastError] 获取。
 *
 * @param instanceName 目标网络实例名
 */
class DataPlaneClient(private val instanceName: String) {

    /** 最近一次操作的错误信息（null 表示无错误或操作未执行）。 */
    private val _lastError = AtomicReference<String?>(null)
    val lastError: String? get() = _lastError.get()

    private fun setError(msg: String) {
        _lastError.set(msg)
    }

    private fun clearError() {
        _lastError.set(null)
    }

    // ------------------------------------------------------------------
    // TCP 客户端
    // ------------------------------------------------------------------

    /** 发起 TCP 连接，返回流句柄与本地地址；失败返回 null */
    suspend fun tcpConnect(
        dstIp: String,
        dstPort: Int,
        timeoutMs: Long = 10_000
    ): DataPlaneTcpConnectResult? = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneTcpConnectStart(instanceName, dstIp, dstPort, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneTcpConnectFinish(it) }
    )

    /** 向 TCP 流写入数据，返回写入字节数；-1 表示失败 */
    suspend fun tcpWrite(
        handle: Long,
        data: ByteArray,
        timeoutMs: Long = 10_000
    ): Int = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneTcpWriteStart(handle, data, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneTcpWriteFinish(it) }
    ) ?: -1

    /** 从 TCP 流读取数据；失败返回 null */
    suspend fun tcpRead(
        handle: Long,
        maxLen: Int = 4096,
        timeoutMs: Long = 10_000
    ): DataPlaneTcpReadResult? = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneTcpReadStart(handle, maxLen, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneTcpReadFinish(it) }
    )

    /** 关闭 TCP 流（同步，无需等待） */
    fun tcpClose(handle: Long): Int = EasyTierDataPlaneJNI.dataPlaneTcpClose(handle)

    // ------------------------------------------------------------------
    // TCP 服务器
    // ------------------------------------------------------------------

    /** 绑定并监听本地端口，返回监听器句柄与本地地址 */
    suspend fun tcpBind(
        localPort: Int,
        timeoutMs: Long = 10_000
    ): DataPlaneTcpBindResult? = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneTcpBindStart(instanceName, localPort, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneTcpBindFinish(it) }
    )

    /** 在监听器上等待新连接，返回流句柄与本地/对端地址 */
    suspend fun tcpAccept(
        listenerHandle: Long,
        timeoutMs: Long = 30_000
    ): DataPlaneTcpAcceptResult? = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneTcpAcceptStart(listenerHandle, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneTcpAcceptFinish(it) }
    )

    /** 关闭 TCP 监听器 */
    fun tcpListenerClose(handle: Long): Int = EasyTierDataPlaneJNI.dataPlaneTcpListenerClose(handle)

    // ------------------------------------------------------------------
    // UDP
    // ------------------------------------------------------------------

    /** 绑定 UDP 本地端口，返回套接字句柄与本地地址 */
    suspend fun udpBind(
        localPort: Int,
        timeoutMs: Long = 10_000
    ): DataPlaneUdpBindResult? = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneUdpBindStart(instanceName, localPort, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneUdpBindFinish(it) }
    )

    /** 向目标地址发送 UDP 数据报，返回发送字节数；-1 表示失败 */
    suspend fun udpSendTo(
        handle: Long,
        dstIp: String,
        dstPort: Int,
        data: ByteArray,
        timeoutMs: Long = 10_000
    ): Int = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneUdpSendToStart(handle, dstIp, dstPort, data, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneUdpSendToFinish(it) }
    ) ?: -1

    /** 接收 UDP 数据报，返回数据与对端地址 */
    suspend fun udpRecvFrom(
        handle: Long,
        maxLen: Int = 4096,
        timeoutMs: Long = 10_000
    ): DataPlaneUdpRecvResult? = runAsyncOp(
        timeoutMs,
        startOp = { EasyTierDataPlaneJNI.dataPlaneUdpRecvFromStart(handle, maxLen, timeoutMs) },
        finishOp = { EasyTierDataPlaneJNI.dataPlaneUdpRecvFromFinish(it) }
    )

    /** 关闭 UDP 套接字 */
    fun udpClose(handle: Long): Int = EasyTierDataPlaneJNI.dataPlaneUdpClose(handle)

    // ------------------------------------------------------------------
    // 通用异步操作执行器
    // ------------------------------------------------------------------

    // 上游 FFI 异步操作状态码（data_plane_async.rs）：
    //   0  = PENDING   操作仍在进行中
    //   1  = READY     操作已完成，可调用 Finish 取结果
    //  -1  = FAILED    操作失败
    //  -2  = INVALID   句柄无效或已被消费

    private companion object {
        private const val OP_PENDING = 0
        private const val OP_READY = 1
        private const val OP_FAILED = -1
        private const val OP_INVALID = -2
    }

    /**
     * 通用异步操作执行器：Start → Wait → Finish，正确管理 op handle 生命周期。
     *
     * 失败时设置 [lastError] 并返回 null：
     *   - Start 返回 0：读取 FFI 错误信息
     *   - Wait 返回 PENDING：报告超时
     *   - Wait 返回 FAILED/INVALID：报告对应错误
     *   - Finish 返回 null：读取 FFI 错误信息
     *
     * @param timeoutMs 操作超时（毫秒），Wait 用 timeoutMs + 1000 作为等待上限
     * @param startOp 返回 op handle；0 表示启动失败
     * @param finishOp 消费 op 并返回结果
     */
    private suspend fun <T> runAsyncOp(
        timeoutMs: Long,
        startOp: () -> Long,
        finishOp: (Long) -> T?
    ): T? = withContext(Dispatchers.IO) {
        clearError()

        val op = startOp()
        if (op == 0L) {
            setError(readFfiError() ?: "操作启动失败（Start 返回 0）")
            return@withContext null
        }

        var finished = false
        try {
            val waitResult = EasyTierDataPlaneJNI.dataPlaneAsyncOpWait(op, timeoutMs + 1000)
            if (waitResult == OP_READY) {
                val result = finishOp(op)
                finished = true
                if (result == null) {
                    setError(readFfiError() ?: "Finish 返回 null")
                }
                result
            } else {
                setError(when (waitResult) {
                    OP_PENDING -> "操作超时（${timeoutMs}ms 内未完成）"
                    OP_FAILED -> "操作失败（Wait 返回 FAILED）: ${readFfiError() ?: "未知"}"
                    OP_INVALID -> "句柄无效或已被消费"
                    else -> "未知状态码: $waitResult"
                })
                null
            }
        } catch (e: Exception) {
            setError("操作异常: ${e.message}")
            null
        } finally {
            if (!finished) {
                runCatching { EasyTierDataPlaneJNI.dataPlaneAsyncOpCancel(op) }
                runCatching { EasyTierDataPlaneJNI.dataPlaneAsyncOpFree(op) }
            }
        }
    }

    private fun readFfiError(): String? {
        return runCatching { EasyTierJNI.getLastError() }.getOrNull()
    }
}
