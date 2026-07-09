package com.easytier.jni

/**
 * 数据面 JNI 调用结果数据类。
 *
 * 这些类被 [EasyTierDataPlaneJNI] 的 `*Finish` 系列函数返回。
 * 构造函数签名必须与上游 Rust 端 `data_plane_api.rs` 中通过 `JNIEnv::new_object`
 * 调用的签名严格一致，否则 JNI 反射会失败。
 *
 * 对应上游：easytier-contrib/easytier-android-jni/src/data_plane_api.rs
 */

private const val SOCKET_ADDR_CLASS = "com/easytier/jni/DataPlaneSocketAddress"

/**
 * IP 地址 + 端口的二元组。
 * 上游签名：(Ljava/lang/String;I)V
 */
class DataPlaneSocketAddress(
    val ip: String,
    val port: Int,
)

/**
 * TCP 连接结果。
 * 上游签名：(JL${SOCKET_ADDR_CLASS};)V
 */
class DataPlaneTcpConnectResult(
    val handle: Long,
    val addr: DataPlaneSocketAddress,
)

/**
 * TCP 绑定结果。
 * 上游签名：(JL${SOCKET_ADDR_CLASS};)V
 */
class DataPlaneTcpBindResult(
    val handle: Long,
    val addr: DataPlaneSocketAddress,
)

/**
 * TCP accept 结果，包含本地与对端地址。
 * 上游签名：(JL${SOCKET_ADDR_CLASS};L${SOCKET_ADDR_CLASS};)V
 */
class DataPlaneTcpAcceptResult(
    val handle: Long,
    val localAddr: DataPlaneSocketAddress,
    val peerAddr: DataPlaneSocketAddress,
)

/**
 * TCP 读取结果。
 * 上游签名：([B)V
 */
class DataPlaneTcpReadResult(
    val data: ByteArray,
)

/**
 * UDP 绑定结果。
 * 上游签名：(JL${SOCKET_ADDR_CLASS};)V
 */
class DataPlaneUdpBindResult(
    val handle: Long,
    val addr: DataPlaneSocketAddress,
)

/**
 * UDP 接收结果，包含数据与对端地址。
 * 上游签名：([BL${SOCKET_ADDR_CLASS};)V
 */
class DataPlaneUdpRecvResult(
    val data: ByteArray,
    val peerAddr: DataPlaneSocketAddress,
)
