package com.example.floatinglyricforthirdpartymusicapp

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.floatinglyricforthirdpartymusicapp.db.AppDatabase
import com.example.floatinglyricforthirdpartymusicapp.db.Lyric
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

@RequiresApi(Build.VERSION_CODES.R)
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private val TAG = "MainActivity"
    private val LYRIC_FILE_EXTENSION = ".lrc"
    private lateinit var db: AppDatabase

    private val multiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        repeat(permissions.entries.size) {
            Log.i(TAG, "permissionKey: ${permissions.keys}, permissionValue: ${permissions.values}")
        }
    }

    private val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
    private val ACTION_NOTIFICATION_LISTENER_SETTINGS =
        "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getInstance(this)

        val scanLyricButton: Button = findViewById(R.id.scan_lyric_button)
        val startFloatLyricServiceButton: Button = findViewById(R.id.start_service_button)
        val clearDatabaseButton: Button = findViewById(R.id.clear_database_button)

        scanLyricButton.setOnClickListener(this)
        startFloatLyricServiceButton.setOnClickListener(this)
        clearDatabaseButton.setOnClickListener(this)


        multiplePermissions.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.MANAGE_EXTERNAL_STORAGE,
                Manifest.permission.SYSTEM_ALERT_WINDOW,
            )
        )


        if (!isNotificationServiceEnabled())
            buildNotificationServiceAlertDialog()

        if (!overlayDisplayPermissionNotEnabled())
            requestOverlayDisplayPermission()

    }

    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.scan_lyric_button -> scanLyric()
            R.id.start_service_button -> {

                if (floatingServiceIsRunning()) {
                    stopService(Intent(this@MainActivity, FloatingLyricService::class.java))
                }

                startService(Intent(this@MainActivity, FloatingLyricService::class.java))
            }
            R.id.clear_database_button -> clearDataBase()
        }
    }

    private fun clearDataBase() {
        Thread {
            db.getLyricDAO().deleteAll()
        }.start()
    }


    /**
     * Find LRC files in folder "Music" (comes with every android phone by default)
     * TODO: 1) Allow user specify the location of LRC file.
     * TODO: 2) Scan LRC files in wider range of directories.
     */
    private fun scanLyric() {
        val path: Path = File("/storage/emulated/0/Music").toPath()

        val walk = Files.walk(path)
        val lyricPathList: List<Path> = (walk.filter(Files::isRegularFile)
            .filter { p -> p.fileName.toString().endsWith(LYRIC_FILE_EXTENSION) }
            .collect(Collectors.toList()) as ArrayList<Path>)

        Thread {
            for (pathElement in lyricPathList) {
                Log.i(TAG, "path.fileName: ${pathElement.fileName}, path: $pathElement")
                val lyric = Lyric(pathElement.toString(), pathElement.fileName.toString())
                db.getLyricDAO().insert(lyric)
            }
        }.start()
    }

    private fun floatingServiceIsRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE))
            if (FloatingLyricService::class.java.name == service.service.className)
                return true

        return false
    }

    /**
     * Is Notification Service Enabled.
     * Verifies if the notification listener service is enabled.
     * Reference: https://github.com/kpbird/NotificationListenerService-Example/blob/master/NLSExample/src/main/java/com/kpbird/nlsexample/NLService.java
     * @return True if enabled, false otherwise.
     */
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            ENABLED_NOTIFICATION_LISTENERS
        )
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (i in names.indices) {
                val cn = ComponentName.unflattenFromString(names[i])
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Build Notification Listener Alert Dialog.
     * Builds the alert dialog that pops up if the user has not turned
     * the Notification Listener Service on yet.
     * @return An alert dialog which leads to the notification enabling screen
     */
    private fun buildNotificationServiceAlertDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
        alertDialogBuilder.setTitle(R.string.notification_listener_service)
        alertDialogBuilder.setMessage(R.string.notification_listener_service_explanation)
        alertDialogBuilder.setPositiveButton(
            R.string.yes
        ) { dialog, id -> startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        alertDialogBuilder.setNegativeButton(
            R.string.no
        ) { dialog, id ->
            // If you choose to not enable the notification listener
            // the app. will not work as expected
        }
        alertDialogBuilder.create().show()
    }

    private fun requestOverlayDisplayPermission() {
        // An AlertDialog is created
        val builder = AlertDialog.Builder(this)

        // This dialog can be closed, just by
        // taping outside the dialog-box
        builder.setCancelable(true)

        // The title of the Dialog-box is set
        builder.setTitle("Screen Overlay Permission Needed")

        // The message of the Dialog-box is set
        builder.setMessage("Enable 'Display over other apps' from System Settings.")

        // The event of the Positive-Button is set
        builder.setPositiveButton(
            "Open Settings"
        ) { dialog, which -> // The app will redirect to the 'Display over other apps' in Settings.
            // This is an Implicit Intent. This is needed when any Action is needed
            // to perform, here it is
            // redirecting to an other app(Settings).
            val intent =
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))

            // This method will start the intent. It takes two parameter,
            // one is the Intent and the other is
            // an requestCode Integer. Here it is -1.
            startActivityForResult(intent, RESULT_OK)
        }
        val alertDialog = builder.create()
        // The Dialog will show in the screen
        alertDialog.show()
    }

    private fun overlayDisplayPermissionNotEnabled(): Boolean {
        // Android Version is lesser than Marshmallow
        // or the API is lesser than 23
        // doesn't need 'Display over other apps' permission enabling.
        return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            // If 'Display over other apps' is not enabled it
            // will return false or else true
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }
}