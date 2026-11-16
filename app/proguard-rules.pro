# Proto DataStore (schedules.pb): protobuf-javalite finds each generated message's
# fields by NAME through reflection at runtime (GeneratedMessageLite's schema lookup
# for "id_", "label_", ...). Without this rule R8 renames those fields in a release
# build and the first read or write of a schedule throws
# "Field id_ for ...ScheduleProto not found" — a crash that never shows up in debug
# builds, which aren't minified. protobuf-javalite ships no consumer rule of its own
# for app-defined messages (only the shaded copies inside DataStore/Glance do).
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
