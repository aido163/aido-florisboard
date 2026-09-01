# Consumer ProGuard rules for the FlorisBoard IME library.
# Keep public IME / application types so a host app can reference them.
-keep class dev.patrickgold.florisboard.FlorisImeService { *; }
-keep class dev.patrickgold.florisboard.FlorisApplication { *; }
-keep class dev.patrickgold.florisboard.FlorisSpellCheckerService { *; }
