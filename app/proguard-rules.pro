# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/chinmaykulkarni/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the PullRequest
# configuration in the project-level build.gradle file.

# For more details, see
# http://developer.android.com/guide/developing/tools/proguard.html

# Keep ViewModel classes
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Keep Data models used for JSON parsing
-keep class com.ludokid.data.** { *; }
