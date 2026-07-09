package com.easytier.jni

/**
 * EasyTier 数据面 JNI 接口。
 *
 * 提供 TCP/UDP 套接字在 EasyTier 虚拟网络上的异步 I/O 能力。
 *
 * 所有 `*Start` 函数返回一个 64 位异步操作句柄（op handle）：
 *   - 0 表示启动失败，错误通过 [EasyTierJNI.getLastError] / 抛出异常获取；
 *   - 非 0 表示已提交，需要随后调用对应的 `*Finish` 函数取得结果。
 *
 * `*Finish` 函数会消费 op handle 并释放其资源；不要重复 finish 同一 handle。
 *
 * 异步操作可通过以下函数管理：
 *   - [dataPlaneAsyncOpStatus] 查询状态
 *   - [dataPlaneAsyncOpWait] 阻塞等待完成
 *   - [dataPlaneAsyncOpCancel] 取消
 *   - [dataPlaneAsyncOpFree] 显式释放（用于未 finish 的 op）
 *
 * 对应上游：easytier-contrib/easytier-android-jni/src/lib.rs
 *           中 `export_data_plane_jni!` 宏展开的 23 个 `Java_com_easytier_jni_EasyTierDataPlaneJNI_*` 符号。
 */
object EasyTierDataPlaneJNI {

    init {
        // 与 EasyTierJNI 共享同一个本地库；loadLibrary 对已加载库幂等。
        System.loadLibrary("easytier_android_jni")
    }

    // ------------------------------------------------------------------
    // 异步操作通用控制
    // ------------------------------------------------------------------

    /**
     * 查询异步操作状态。
     * @param handle 异步操作句柄
     * @return 状态码；具体语义见上游 data_plane_api
     */
    @JvmStatic
    external fun dataPlaneAsyncOpStatus(handle: Long): Int

    /**
     * 阻塞等待异步操作完成或超时。
     * @param handle 异步操作句柄
     * @param timeoutMs 超时（毫秒），0 表示不等待
     * @return 0 完成；其他值见上游
     */
    @JvmStatic
    external fun dataPlaneAsyncOpWait(handle: Long, timeoutMs: Long): Int

    /**
     * 取消异步操作。
     * @param handle 异步操作句柄
     * @return 0 成功；非 0 失败
     */
    @JvmStatic
    external fun dataPlaneAsyncOpCancel(handle: Long): Int

    /**
     * 释放异步操作资源（用于未 finish 的 op）。
     * @param handle 异步操作句柄
     * @return 0 成功；非 0 失败
     */
    @JvmStatic
    external fun dataPlaneAsyncOpFree(handle: Long): Int

    // ------------------------------------------------------------------
    // TCP 操作
    // ------------------------------------------------------------------

    /**
     * 发起异步 TCP 连接。
     * @param instanceName 网络实例名
     * @param dstIp 目标 IP
     * @param dstPort 目标端口
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneTcpConnectStart(
        instanceName: String,
        dstIp: String,
        dstPort: Int,
        timeoutMs: Long,
    ): Long

    /**
     * 完成 TCP 连接，返回连接流句柄与本地地址。
     * @param op [dataPlaneTcpConnectStart] 返回的句柄
     * @return 连接结果；失败返回 null
     */
    @JvmStatic
    external fun dataPlaneTcpConnectFinish(op: Long): DataPlaneTcpConnectResult?

    /**
     * 发起异步 TCP 绑定（监听）。
     * @param instanceName 网络实例名
     * @param localPort 本地端口
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneTcpBindStart(
        instanceName: String,
        localPort: Int,
        timeoutMs: Long,
    ): Long

    /**
     * 完成 TCP 绑定，返回监听器句柄与本地地址。
     * @param op [dataPlaneTcpBindStart] 返回的句柄
     * @return 绑定结果；失败返回 null
     */
    @JvmStatic
    external fun dataPlaneTcpBindFinish(op: Long): DataPlaneTcpBindResult?

    /**
     * 发起异步 TCP accept。
     * @param handle [dataPlaneTcpBindFinish] 返回的监听器句柄
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneTcpAcceptStart(handle: Long, timeoutMs: Long): Long

    /**
     * 完成 TCP accept，返回连接流句柄与本地/对端地址。
     * @param op [dataPlaneTcpAcceptStart] 返回的句柄
     * @return accept 结果；失败返回 null
     */
    @JvmStatic
    external fun dataPlaneTcpAcceptFinish(op: Long): DataPlaneTcpAcceptResult?

    /**
     * 发起异步 TCP 读取。
     * @param handle TCP 流句柄（来自 connect/accept 的 finish 结果）
     * @param maxLen 最大读取字节数
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneTcpReadStart(handle: Long, maxLen: Int, timeoutMs: Long): Long

    /**
     * 完成 TCP 读取，返回读取到的字节数组。
     * @param op [dataPlaneTcpReadStart] 返回的句柄
     * @return 读取结果；失败返回 null
     */
    @JvmStatic
    external fun dataPlaneTcpReadFinish(op: Long): DataPlaneTcpReadResult?

    /**
     * 发起异步 TCP 写入。
     * @param handle TCP 流句柄
     * @param data 待写入数据
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneTcpWriteStart(handle: Long, data: ByteArray, timeoutMs: Long): Long

    /**
     * 完成 TCP 写入。
     * @param op [dataPlaneTcpWriteStart] 返回的句柄
     * @return 成功写入的字节数；-1 表示失败
     */
    @JvmStatic
    external fun dataPlaneTcpWriteFinish(op: Long): Int

    /**
     * 关闭 TCP 流。
     * @param handle TCP 流句柄
     * @return 0 成功；非 0 失败
     */
    @JvmStatic
    external fun dataPlaneTcpClose(handle: Long): Int

    /**
     * 关闭 TCP 监听器。
     * @param handle 监听器句柄
     * @return 0 成功；非 0 失败
     */
    @JvmStatic
    external fun dataPlaneTcpListenerClose(handle: Long): Int

    // ------------------------------------------------------------------
    // UDP 操作
    // ------------------------------------------------------------------

    /**
     * 发起异步 UDP 绑定。
     * @param instanceName 网络实例名
     * @param localPort 本地端口
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneUdpBindStart(
        instanceName: String,
        localPort: Int,
        timeoutMs: Long,
    ): Long

    /**
     * 完成 UDP 绑定，返回套接字句柄与本地地址。
     * @param op [dataPlaneUdpBindStart] 返回的句柄
     * @return 绑定结果；失败返回 null
     */
    @JvmStatic
    external fun dataPlaneUdpBindFinish(op: Long): DataPlaneUdpBindResult?

    /**
     * 发起异步 UDP 发送。
     * @param handle UDP 套接字句柄
     * @param dstIp 目标 IP
     * @param dstPort 目标端口
     * @param data 待发送数据
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneUdpSendToStart(
        handle: Long,
        dstIp: String,
        dstPort: Int,
        data: ByteArray,
        timeoutMs: Long,
    ): Long

    /**
     * 完成 UDP 发送。
     * @param op [dataPlaneUdpSendToStart] 返回的句柄
     * @return 成功发送的字节数；-1 表示失败
     */
    @JvmStatic
    external fun dataPlaneUdpSendToFinish(op: Long): Int

    /**
     * 发起异步 UDP 接收。
     * @param handle UDP 套接字句柄
     * @param maxLen 最大读取字节数
     * @param timeoutMs 超时（毫秒）
     * @return 异步操作句柄；0 表示失败
     */
    @JvmStatic
    external fun dataPlaneUdpRecvFromStart(handle: Long, maxLen: Int, timeoutMs: Long): Long

    /**
     * 完成 UDP 接收，返回数据与对端地址。
     * @param op [dataPlaneUdpRecvFromStart] 返回的句柄
     * @return 接收结果；失败返回 null
     */
    @JvmStatic
    external fun dataPlaneUdpRecvFromFinish(op: Long): DataPlaneUdpRecvResult?

    /**
     * 关闭 UDP 套接字。
     * @param handle UDP 套接字句柄
     * @return 0 成功；非 0 失败
     */
    @JvmStatic
    external fun dataPlaneUdpClose(handle: Long): Int
}
