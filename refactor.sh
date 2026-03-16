#!/bin/bash
set -x
cd /home/braindead/github/watch-app/wetpet-watch-app

# 1. Add watchface-editor dependency
sed -i 's/implementation("androidx.wear.watchface:watchface-style:1.2.1")/implementation("androidx.wear.watchface:watchface-style:1.2.1")\n    implementation("androidx.wear.watchface:watchface-editor:1.2.1")/' wear/build.gradle.kts

# 2. Add WatchFaceEditorActivity in AndroidManifest.xml
sed -i '/<application/a \
        <activity\
            android:name="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"\
            android:exported="true"\
            android:theme="@android:style/Theme.Translucent.NoTitleBar"\
            android:taskAffinity=""\
            android:label="PixelFace Editor">\
            <intent-filter>\
                <action android:name="androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR" />\
                <category android:name="com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION" />\
                <category android:name="android.intent.category.DEFAULT" />\
            </intent-filter>\
        </activity>' wear/src/main/AndroidManifest.xml

# Add EDITOR_ACTIVITY meta-data to the service
awk '
/android.service.wallpaper.WallpaperService/ {
    print
    print "            </intent-filter>"
    print "            <meta-data android:name=\"androidx.wear.watchface.EDITOR_ACTIVITY\" android:value=\"androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR\" />"
    getline
    next
}
{print}' wear/src/main/AndroidManifest.xml > wear/src/main/AndroidManifest2.xml
mv wear/src/main/AndroidManifest2.xml wear/src/main/AndroidManifest.xml

# 3. Rename files
mv wear/src/main/java/com/tamagotchi/pet/PixelPetRenderer.kt wear/src/main/java/com/tamagotchi/pet/PixelFaceRenderer.kt
mv wear/src/main/java/com/tamagotchi/pet/PixelPetAnimator.kt wear/src/main/java/com/tamagotchi/pet/PixelFaceAnimator.kt

# 4. Create new directories and move files
mkdir -p wear/src/main/java/com/pixelface/watch
mv wear/src/main/java/com/tamagotchi/pet/* wear/src/main/java/com/pixelface/watch/
rmdir wear/src/main/java/com/tamagotchi/pet
rmdir wear/src/main/java/com/tamagotchi

mkdir -p mobile/src/main/java/com/pixelface/mobile
mv mobile/src/main/java/com/wetpet/mobile/* mobile/src/main/java/com/pixelface/mobile/
rmdir mobile/src/main/java/com/wetpet/mobile
rmdir mobile/src/main/java/com/wetpet

# 5. Bulk text replacements
find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.kts" -o -name "*.sh" -o -name "*.md" | grep -v "\.gradle" | grep -v "build/" | grep -v "refactor.sh" | xargs sed -i 's/com.tamagotchi.pet/com.pixelface.watch/g'
find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.kts" -o -name "*.sh" -o -name "*.md" | grep -v "\.gradle" | grep -v "build/" | grep -v "refactor.sh" | xargs sed -i 's/com.wetpet.watch/com.pixelface.watch/g'
find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.kts" -o -name "*.sh" -o -name "*.md" | grep -v "\.gradle" | grep -v "build/" | grep -v "refactor.sh" | xargs sed -i 's/com.wetpet.mobile/com.pixelface.mobile/g'

find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.kts" -o -name "*.sh" -o -name "*.md" | grep -v "\.gradle" | grep -v "build/" | grep -v "refactor.sh" | xargs sed -i 's/PixelPet/PixelFace/g'
find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.kts" -o -name "*.sh" -o -name "*.md" | grep -v "\.gradle" | grep -v "build/" | grep -v "refactor.sh" | xargs sed -i 's/WetPet/PixelFace/g'
find . -type f -name "*.kt" -o -name "*.xml" -o -name "*.kts" -o -name "*.sh" -o -name "*.md" | grep -v "\.gradle" | grep -v "build/" | grep -v "refactor.sh" | xargs sed -i 's/wetpet/pixelface/g'

# 6. Rename root directory
cd ..
mv wetpet-watch-app pixelface-watch-app
sed -i 's/wetpet/pixelface/g' setup_sdk.sh
sed -i 's/WetPet/PixelFace/g' setup_sdk.sh
