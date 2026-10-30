# Vaccine Manager release R8 rules
# Start with Android's optimized default rules.
# Add narrowly-scoped keep rules only if release testing identifies a
# reflection/serialization issue.

# Strip debug and verbose logs in release builds to eliminate diagnostic data leakage:
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
