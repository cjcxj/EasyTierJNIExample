package com.easytier.jni

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 通用 RPC 客户端，封装 [EasyTierJNI.callJsonRpc] 的 IO 切换与异常处理。 */
class RpcClient {
    companion object {
        private const val TAG = "RpcClient"
    }

    data class RpcResult(
        val success: Boolean,
        val response: String?,
        val error: String?,
        val durationMs: Long
    )

    /** 调用 EasyTier RPC。domainName 为空字符串或 null 表示不使用。 */
    suspend fun call(
        serviceName: String,
        methodName: String,
        domainName: String?,
        payloadJson: String
    ): RpcResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val resp = EasyTierJNI.callJsonRpc(serviceName, methodName, domainName, payloadJson)
            RpcResult(
                success = resp != null,
                response = resp,
                error = if (resp == null) EasyTierJNI.getLastError() else null,
                durationMs = System.currentTimeMillis() - start
            )
        } catch (e: Exception) {
            Log.e(TAG, "RPC 调用异常: $serviceName.$methodName", e)
            RpcResult(false, null, e.message, System.currentTimeMillis() - start)
        }
    }
}
