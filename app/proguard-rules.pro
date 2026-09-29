# Ember proguard rules. Minification is disabled for this build, so these are
# precautionary rules for anyone who turns isMinifyEnabled on.
-keep class com.ember.companion.data.db.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
