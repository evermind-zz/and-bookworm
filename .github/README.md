## About this fork
Somehow I could not export the books via csv export on Android 10. Got
some `ClassNotFoundException` so I tried to rebuild the app with the result:
- reorganized the code for gradle build (redone all commits like they
  would have been always in this folder structure: `app/src/main/{res,java,assets}`)
- As this app is designed for an old Android version I guess around API Level 10
    (aka Gingerbread Android ~2.3.3) and for some reasons I could not figure out
    (on Android 10) how to access the settings so I added a settings button.
- csv export is working again:)
