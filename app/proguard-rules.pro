# Android and dependency consumer rules supply required framework entry points.
# Keep no debug fixture or simulation classes in production.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
