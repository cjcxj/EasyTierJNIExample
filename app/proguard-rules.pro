# ===================================================================
# 通用 Android 和 Kotlin 规则
# ===================================================================

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class kotlin.Metadata { *; }
-keep class kotlin.coroutines.jvm.internal.DebugMetadataKt { *; }
-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===================================================================
# JNI (Native Interface) 规则
# ===================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}
# 显式保留 JNI 接口类，确保其 init 块和所有方法不被任何优化影响
-keep class com.easytier.jni.EasyTierJNI { *; }


# ===================================================================
# 第三方库规则
# ===================================================================

# --- Jetpack Compose ---
-keepclassmembers class * { @androidx.compose.runtime.Composable <methods>; }
-keep class androidx.compose.runtime.internal.ComposableLambda { *; }
-keepclassmembers class **.R$* { public static <fields>; }

# --- Moshi & Wire ---
-keep class com.squareup.moshi.** { *; }
-keep @interface com.squareup.moshi.**
-keep class com.squareup.wire.** { *; }

# --- Kotlinx Serialization & Ktoml ---
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <fields>; }
-keep class **$$serializer { *; }
-keep class * implements kotlinx.serialization.KSerializer { *; }


# ===================================================================
# 为所有生成的代码（模型、RPC、枚举）添加强保留规则
# ===================================================================
# 这是解决 JNI 初始化失败和 Moshi/Wire 反射问题的核心。
# 保留所有由 Wire 生成的包，确保它们的结构不被 R8 的优化或缩减破坏。

# 1. 保留所有生成的 Enum 类
-keep public enum acl.** { *; }
-keep public enum cli.** { *; }
-keep public enum common.** { *; }
-keep public enum error.** { *; }
-keep public enum magic_dns.** { *; }
-keep public enum peer_rpc.** { *; }
-keep public enum tests.** { *; }
-keep public enum web.** { *; }

# 2. 保留所有生成的非 Enum 数据模型类
-keep class acl.** { *; }
-keep class cli.** { *; }
-keep class common.** { *; }
-keep class error.** { *; }
-keep class magic_dns.** { *; }
-keep class peer_rpc.** { *; }
-keep class tests.** { *; }
-keep class web.** { *; }


# ===================================================================
# 优化：在 release 版本中移除所有日志调用
# ===================================================================
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}