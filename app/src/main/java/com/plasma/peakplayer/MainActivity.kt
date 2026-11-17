package com.plasma.peakplayer

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<File> = emptyList()
    private var currentIndex = 0
    private var currentCoverBitmap: Bitmap? = null

    private var useBlurCover = true
    private var customBgColor: Int = Color.parseColor("#1E293B")

    private lateinit var lvSongs: ListView
    private lateinit var peakPlayerCard: FrameLayout
    private lateinit var ivBgBlur: ImageView
    private lateinit var ivCover: ImageView
    private lateinit var tvAppTitle: TextView
    private lateinit var tvSongName: TextView
    private lateinit var tvArtistName: TextView
    private lateinit var tvTimeDisplay: TextView
    private lateinit var btnSettings: TextView
    private lateinit var btnClosePlayer: TextView
    private lateinit var btnPrev: TextView
    private lateinit var btnPlayPause: TextView
    private lateinit var btnNext: TextView
    private lateinit var seekBar: SeekBar

    private val handler = Handler(Looper.getMainLooper())
    private var isSeeking = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) loadFixedFolder()
        else Toast.makeText(this, "Speicher-Berechtigung benötigt", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lvSongs = findViewById(R.id.lvSongs)
        peakPlayerCard = findViewById(R.id.peakPlayerCard)
        ivBgBlur = findViewById(R.id.ivBgBlur)
        ivCover = findViewById(R.id.ivCover)
        tvAppTitle = findViewById(R.id.tvAppTitle)
        tvSongName = findViewById(R.id.tvSongName)
        tvArtistName = findViewById(R.id.tvArtistName)
        tvTimeDisplay = findViewById(R.id.tvTimeDisplay)
        btnSettings = findViewById(R.id.btnSettings)
        btnClosePlayer = findViewById(R.id.btnClosePlayer)
        btnPrev = findViewById(R.id.btnPrev)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        seekBar = findViewById(R.id.seekBar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ivBgBlur.setRenderEffect(RenderEffect.createBlurEffect(45f, 45f, Shader.TileMode.CLAMP))
        }

        lvSongs.setOnItemClickListener { _, _, position, _ ->
            currentIndex = position
            peakPlayerCard.visibility = View.VISIBLE
            playSong(currentIndex)
        }

        btnClosePlayer.setOnClickListener {
            peakPlayerCard.visibility = View.GONE
        }

        btnSettings.setOnClickListener {
            openSettingsDialog()
        }

        val togglePlayPause = View.OnClickListener {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    btnPlayPause.text = "▶"
                } else {
                    it.start()
                    btnPlayPause.text = "⏸"
                }
            }
        }
        btnPlayPause.setOnClickListener(togglePlayPause)
        ivCover.setOnClickListener(togglePlayPause)

        btnNext.setOnClickListener {
            if (songList.isNotEmpty()) {
                currentIndex = (currentIndex + 1) % songList.size
                playSong(currentIndex)
            }
        }

        btnPrev.setOnClickListener {
            if (songList.isNotEmpty()) {
                currentIndex = if (currentIndex - 1 < 0) songList.size - 1 else currentIndex - 1
                playSong(currentIndex)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val total = mediaPlayer?.duration ?: 0
                    tvTimeDisplay.text = "${formatTime(progress)} / ${formatTime(total)}"
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isSeeking = false
                sb?.let { mediaPlayer?.seekTo(it.progress) }
            }
        })

        checkPermissionAndStart()
    }

    private fun openSettingsDialog() {
        val options = arrayOf(
            "Schriftart ändern",
            "Hintergrund: Blur-Cover (Standard)",
            "Hintergrund: Farbe wählen"
        )

        AlertDialog.Builder(this)
            .setTitle("Peak Player Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showFontPicker()
                    1 -> {
                        useBlurCover = true
                        updatePlayerBackground()
                    }
                    2 -> showColorPicker()
                }
            }
            .show()
    }

    private fun showFontPicker() {
        val fontNames = arrayOf("Standard (Sans-Serif)", "Modern (Sans-Serif Light)", "Schreibmaschine (Monospace)", "Klassisch (Serif)")
        val typefaces = arrayOf(Typeface.DEFAULT, Typeface.create("sans-serif-light", Typeface.NORMAL), Typeface.MONOSPACE, Typeface.SERIF)

        AlertDialog.Builder(this)
            .setTitle("Schriftart wählen")
            .setItems(fontNames) { _, index ->
                val selectedFont = typefaces[index]
                tvAppTitle.setTypeface(selectedFont, Typeface.BOLD)
                tvSongName.setTypeface(selectedFont, Typeface.BOLD)
                tvArtistName.typeface = selectedFont
                tvTimeDisplay.setTypeface(selectedFont, Typeface.BOLD)
                btnSettings.setTypeface(selectedFont, Typeface.BOLD)
            }
            .show()
    }

    private fun showColorPicker() {
        val colorNames = arrayOf("Dunkelblau", "Tiefschwarz", "Dunkelrot", "Smaragdgrün", "Violett", "Anthrazit")
        val colorValues = arrayOf("#0F172A", "#09090B", "#450A0A", "#064E3B", "#2E1065", "#27272A")

        AlertDialog.Builder(this)
            .setTitle("Hintergrundfarbe wählen")
            .setItems(colorNames) { _, index ->
                useBlurCover = false
                customBgColor = Color.parseColor(colorValues[index])
                updatePlayerBackground()
            }
            .show()
    }

    private fun updatePlayerBackground() {
        if (useBlurCover && currentCoverBitmap != null) {
            ivBgBlur.setBackgroundColor(Color.TRANSPARENT)
            ivBgBlur.setImageBitmap(currentCoverBitmap)
        } else if (useBlurCover) {
            ivBgBlur.setImageDrawable(null)
            ivBgBlur.setBackgroundColor(Color.parseColor("#1E293B"))
        } else {
            ivBgBlur.setImageDrawable(null)
            ivBgBlur.setBackgroundColor(customBgColor)
        }
    }

    private fun checkPermissionAndStart() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            loadFixedFolder()
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    private fun loadFixedFolder() {
        val targetFolder = File(Environment.getExternalStorageDirectory(), "Musik_Laufliste")

        if (targetFolder.exists() && targetFolder.isDirectory) {
            songList = targetFolder.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in listOf("mp3", "wav", "m4a", "ogg", "flac") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()

            lvSongs.adapter = SongAdapter(songList.toTypedArray())

            if (songList.isNotEmpty()) {
                currentIndex = 0
                playSong(currentIndex)
            } else {
                tvSongName.text = "Ordner ist leer"
            }
        } else {
            tvSongName.text = "Musik_Laufliste fehlt"
        }
    }

    private fun playSong(index: Int) {
        mediaPlayer?.release()
        val file = songList[index]

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val artBytes = retriever.embeddedPicture

            tvSongName.text = if (!title.isNullOrBlank()) title else file.nameWithoutExtension
tvArtistName.text = if (!artist.isNullOrBlank()) artist else "Unbekannter Künstler"
if (artBytes != null) {
currentCoverBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
ivCover.setImageBitmap(currentCoverBitmap)
} else {
currentCoverBitmap = null
ivCover.setImageResource(android.R.drawable.ic_media_play)
}
} catch (e: Exception) {
currentCoverBitmap = null
tvSongName.text = file.nameWithoutExtension
tvArtistName.text = "Musik_Laufliste"
ivCover.setImageResource(android.R.drawable.ic_media_play)
} finally {
retriever.release()
}
updatePlayerBackground()
btnPlayPause.text = "⏸"
try {
mediaPlayer = MediaPlayer().apply {
setAudioAttributes(
AudioAttributes.Builder()
.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
.setUsage(AudioAttributes.USAGE_MEDIA)
.build()
)
setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
setDataSource(file.absolutePath)
prepare()
start()
seekBar.max = duration
tvTimeDisplay.text = "0:00 / ${formatTime(duration)}"
startSeekBarUpdater()
setOnCompletionListener {
currentIndex = (currentIndex + 1) % songList.size
playSong(currentIndex)
}
}
} catch (e: Exception) {
Toast.makeText(this, "Fehler bei: ${file.name}", Toast.LENGTH_SHORT).show()
}
}
private fun startSeekBarUpdater() {
handler.removeCallbacksAndMessages(null)
handler.post(object : Runnable {
override fun run() {
mediaPlayer?.let {
if (it.isPlaying && !isSeeking) {
seekBar.progress = it.currentPosition
tvTimeDisplay.text = "${formatTime(it.currentPosition)} / ${formatTime(it.duration)}"
}
}
handler.postDelayed(this, 500)
}
})
}
private fun formatTime(ms: Int): String {
val totalSeconds = ms / 1000
val minutes = totalSeconds / 60
val seconds = totalSeconds % 60
return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
    inner class SongAdapter(private val files: Array<File>) : ArrayAdapter<File>(this, 0, files) {
        private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_song, parent, false)
            val file = files[position]

            val tvName = view.findViewById<TextView>(R.id.tvItemName)
            val tvInfo = view.findViewById<TextView>(R.id.tvItemInfo)

            tvName.text = file.name
            val sizeMb = String.format(Locale.GERMANY, "%.2f MB", file.length() / (1024.0 * 1024.0))
            val timeStr = dateFormat.format(Date(file.lastModified()))
            tvInfo.text = "$timeStr  |  $sizeMb"

            return view
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
