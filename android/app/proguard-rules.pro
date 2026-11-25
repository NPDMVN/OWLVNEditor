# Keep JNI bridge & native methods untouched
-keep class com.owledit.core.nativebridge.RustCoreJni { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep ViewModels & Activities constructors for reflection instantiation
-keep class com.owledit.app.ui.MainViewModel { public <init>(android.app.Application); }
-keep class * extends androidx.lifecycle.AndroidViewModel { public <init>(android.app.Application); }

# Keep kotlinx.serialization models
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.owledit.core.model.** { *; }
-keep class com.owledit.core.model.**$* { *; }
-dontwarn kotlinx.serialization.**
