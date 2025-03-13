1. Add permisson manually
   adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS -d  package:com.example.cameratest
2. Modify camera ID

startCamera("0", textureView1)
startCamera("102", textureView2)
