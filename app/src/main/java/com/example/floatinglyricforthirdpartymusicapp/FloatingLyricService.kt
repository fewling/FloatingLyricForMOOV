package com.example.floatinglyricforthirdpartymusicapp

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.example.floatinglyricforthirdpartymusicapp.db.AppDatabase
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.collections.ArrayList

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class FloatingLyricService : NotificationListenerService() {

    private val TAG = "FloatingLyricService"

    private var LAYOUT_TYPE: Int = 0
    private lateinit var db: AppDatabase
    private lateinit var floatView: ViewGroup
    private lateinit var floatWindowLayoutParam: WindowManager.LayoutParams
    private lateinit var windowManager: WindowManager
    private lateinit var floatingLyricTextView: TextView
    private lateinit var floatingTitleTextView: TextView
    private lateinit var floatingCloseImageButton: ImageButton
    private lateinit var floatingStartTimeTextView: TextView
    private lateinit var floatingMusicSeekBar: SeekBar
    private lateinit var floatingMaxTimeTextView: TextView

    private var currentSingerAndTitle = " "
    private var floatViewRemoved = false
    private var showLyricOnly: Boolean = false
    private val handler = Handler()
    private var durationAndLyricList: List<String> = ArrayList()
    private var controller: MediaController? = null

    override fun onCreate() {
        super.onCreate()

        db = AppDatabase.getInstance(applicationContext)

        initViews()
        showFloatingWindow()
    }

    private fun initViews() {
        val inflater = baseContext.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatView = inflater.inflate(R.layout.floating_lyric_service_layout, null) as ViewGroup


        floatingLyricTextView = floatView.findViewById(R.id.floating_lyric_text_view)
        floatingTitleTextView = floatView.findViewById(R.id.floating_title_text_view)
        floatingCloseImageButton = floatView.findViewById(R.id.floating_image_button_close)
        floatingStartTimeTextView = floatView.findViewById(R.id.floating_start_time_text_view)
        floatingMusicSeekBar = floatView.findViewById(R.id.floating_music_seekbar)
        floatingMaxTimeTextView = floatView.findViewById(R.id.floating_max_time_text_view)


        floatingMusicSeekBar.isEnabled = false
        floatView.setOnClickListener { showLyricOnly = !showLyricOnly }
        floatingCloseImageButton.setOnClickListener {
            windowManager.removeViewImmediate(floatView)
            floatViewRemoved = true
        }

    }

    private fun showFloatingWindow() {

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        LAYOUT_TYPE = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_TOAST

        // Now the Parameter of the floating-window layout is set.
        // 1) The Width of the window will be 100%  of the phone width.
        // 2) The Height of the window will be 30% of the phone height.
        // 3) Layout_Type is already set.
        // 4) Next Parameter is Window_Flag. Here FLAG_NOT_FOCUSABLE is used. But
        // problem with this flag is key inputs can't be given to the EditText.
        // This problem is solved later.
        // 5) Next parameter is Layout_Format. System chooses a format that supports
        // translucency by PixelFormat.TRANSLUCENT
        floatWindowLayoutParam = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            LAYOUT_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // The Gravity of the Floating Window is set.
        // The Window will appear in the TOP of the screen
        floatWindowLayoutParam.gravity = Gravity.TOP

        // X and Y value of the window is set
        floatWindowLayoutParam.x = 0
        floatWindowLayoutParam.y = 0

        // The ViewGroup that inflates the floating_layout.xml is
        // added to the WindowManager with all the parameters
        windowManager.addView(floatView, floatWindowLayoutParam)

        // Another feature of the floating window is, the window is movable.
        // The window can be moved at any position on the screen.
        floatView.setOnTouchListener(object : View.OnTouchListener {
            val floatWindowLayoutUpdateParam = floatWindowLayoutParam
            var x = 0.0
            var y = 0.0
            var px = 0.0
            var py = 0.0
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        x = floatWindowLayoutUpdateParam.x.toDouble()
                        y = floatWindowLayoutUpdateParam.y.toDouble()

                        // returns the original raw X
                        // coordinate of this event
                        px = event.rawX.toDouble()

                        // returns the original raw Y
                        // coordinate of this event
                        py = event.rawY.toDouble()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        floatWindowLayoutUpdateParam.x = (x + event.rawX - px).toInt()
                        floatWindowLayoutUpdateParam.y = (y + event.rawY - py).toInt()

                        // updated parameter is applied to the WindowManager
                        windowManager.updateViewLayout(floatView, floatWindowLayoutUpdateParam)
                    }
                }
                return false
            }
        })
    }

    /**
     * Listen to event occurs in Notification Section
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)

        /* Check if notification event caused by MOOV: */
        if (sbn?.packageName.equals("com.now.moov")) {


            /* Check if the MOOV notification event involves media session: */
            if (sbn?.notification?.extras?.get(Notification.EXTRA_MEDIA_SESSION) == null)
                return


            /* Prevent duplicate views */
            if (floatViewRemoved) {
                showFloatingWindow()
                floatViewRemoved = false
            }


            /* Get the MediaSession token which belongs to MOOV: */
            val token: MediaSession.Token =
                sbn.notification.extras[Notification.EXTRA_MEDIA_SESSION] as MediaSession.Token


            /* Get the MediaController from the MOOV MediaSession token: */
            controller = MediaController(applicationContext, token)


            /* Get the music info base on the displaying texts, default format: <title> - <singer> */
            currentSingerAndTitle =
                "${sbn.notification.extras[Notification.EXTRA_TEXT]} - ${sbn.notification.extras[Notification.EXTRA_TITLE]}"


            var targetPath: Path? = null
            Thread {
                /* Locate the lyric file path that matches the playing music: */
                val allLyric = db.getLyricDAO().getAll()
                for (lyric in allLyric) {
                    if (lyric.lyric_file_name.removeSuffix(".lrc") == currentSingerAndTitle) {
                        val lyricFile = File(lyric.lyric_path)
                        targetPath = lyricFile.toPath()
                        break
                    }
                }


                durationAndLyricList =
                    if (targetPath != null)
                        lyricStringToList(lyricFileToString(targetPath!!))
                    else
                        ArrayList()

                startMoovRunnable()
            }.start()
        }

    }

    /**
     * Stores the entire file content as String
     *
     * E.G. 娘子 - 周杰倫.lrc
     * [03:11.52][01:18.61]娘子我欠你太多
     * [03:18.73]一壺好酒 再來一碗熱粥 配上幾斤的牛肉
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun lyricFileToString(path: Path): String {
        val file = File(path.toUri())
        val ins = FileInputStream(file)
        val inr = InputStreamReader(ins)
        val reader = BufferedReader(inr)

        var line = reader.readLine()
        val stringBuilder = StringBuilder()

        do {
            stringBuilder.append(line + "\n")
            line = reader.readLine()
        } while (line != null)
        return stringBuilder.toString()
    }

    /**
     * Convert the file content String to List
     * Each line in the String will stored as an element in List
     *
     * E.G. 娘子 - 周杰倫.lrc
     * list[0] --> [03:11.52][01:18.61]娘子我欠你太多
     * list[1] --> [03:18.73]一壺好酒 再來一碗熱粥 配上幾斤的牛肉
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun lyricStringToList(
        lyricString: String,
    ): List<String> {
        val durationAndLyricList: MutableList<String> = ArrayList()

        for (eachLine in lyricString.split("\n")) {

            val regexPattern = "\\[(\\d{2}:\\d{2}\\.\\d{2})]"
            val pattern: Pattern = Pattern.compile(regexPattern)
            val matcher: Matcher = pattern.matcher(eachLine)

            // If there is a match, do the following
            while (matcher.find()) {

                // Get the current matches timeStr:
                val currentTimeStr = matcher.group()

                var currentContent = ""
                val content = pattern.split(eachLine)
                for (i in content.indices) {
                    if (i == content.size - 1) {
                        // set the content to the current content
                        currentContent = content[i]
                    }
                }
                durationAndLyricList.add(
                    "${
                        currentTimeStr.substring(
                            1,
                            currentTimeStr.lastIndex
                        )
                    } $currentContent"
                )
            }
        }
        durationAndLyricList.sort()
        return durationAndLyricList
    }

    /**
     * Loop the lyric update mechanism every 100ms
     */
    private fun startMoovRunnable() {
        handler.removeCallbacks(moovRunnable)
        handler.postDelayed(moovRunnable, 100)
    }

    /**
     * Tracks the music progress every 100ms and update the displaying lyric
     */
    @SuppressLint("SimpleDateFormat")
    private val moovRunnable = Runnable {

        /* Display/hide certain views in floating window: */
        if (showLyricOnly) {
            floatingTitleTextView.visibility = View.VISIBLE
            floatingCloseImageButton.visibility = View.VISIBLE
            floatingStartTimeTextView.visibility = View.VISIBLE
            floatingMusicSeekBar.visibility = View.VISIBLE
            floatingMaxTimeTextView.visibility = View.VISIBLE
        } else {
            floatingTitleTextView.visibility = View.INVISIBLE
            floatingCloseImageButton.visibility = View.GONE
            floatingStartTimeTextView.visibility = View.GONE
            floatingMusicSeekBar.visibility = View.GONE
            floatingMaxTimeTextView.visibility = View.GONE
        }

        val formatter = SimpleDateFormat("mm:ss.SS")
        formatter.timeZone = TimeZone.getTimeZone("GMT")

        val maxDuration = controller!!.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)!!
        val currentDuration = Date(controller!!.playbackState!!.position)

        floatingTitleTextView.text = currentSingerAndTitle
        floatingLyricTextView.text = ""
        floatingStartTimeTextView.text = formatter.format(currentDuration)
        floatingMaxTimeTextView.text = formatter.format(maxDuration)

        floatingMusicSeekBar.max = maxDuration.toInt()
        floatingMusicSeekBar.progress = currentDuration.time.toInt()
        if (floatingMusicSeekBar.progress == maxDuration.toInt())
            floatingMusicSeekBar.progress = 0

        if (durationAndLyricList.isNotEmpty()) {
            for (line in durationAndLyricList) {
                val timeStr = line.substring(0, 9)
                val timeDate: Date? = formatter.parse(timeStr)
                currentDuration.time = currentDuration.time + 50

                if (timeDate?.before(currentDuration)!!)
                    floatingLyricTextView.text = line.replace(timeStr, "")
            }
        } else
            floatingLyricTextView.text = "No Lyric"

        startMoovRunnable()
    }


}