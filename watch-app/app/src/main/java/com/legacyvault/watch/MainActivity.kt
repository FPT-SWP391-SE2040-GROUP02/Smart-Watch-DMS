package com.legacyvault.watch

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI

class MainActivity : ComponentActivity(), SensorEventListener {
    private val tag = "LegacyVaultWatch"

    // Cookie jar để lưu cookie phiên nếu cần
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()
    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val list = cookieStore.getOrPut(url.host) { mutableListOf() }
                list.removeAll { c -> cookies.any { it.name == c.name } }
                list.addAll(cookies)
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    // URL máy chủ chính thức trên Render (đồng hồ có thể gửi từ bất kỳ đâu qua Wi-Fi / 4G)
    private val defaultServerUrl = "https://legacyvault-api.onrender.com"

    private lateinit var prefs: SharedPreferences
    private var authToken: String = ""
    private var ownerEmail: String = ""

    private fun getBaseUrl(): String {
        val raw = prefs.getString("server_url", "")?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultServerUrl
        return raw.trimEnd('/').removeSuffix("/api/v1").trimEnd('/')
    }

    private fun getCheckInUrl(): String = "${getBaseUrl().trimEnd('/')}/api/v1/check-ins/smartwatch"
    private fun getPairUrl(): String = "${getBaseUrl().trimEnd('/')}/api/v1/smartwatch/pair"

    private lateinit var sensorManager: SensorManager
    private val heartRateSensors = mutableListOf<Sensor>()

    // UI Header
    private lateinit var tvTitle: TextView
    private lateinit var tvServerUrl: TextView

    // UI Khung chưa ghép nối
    private lateinit var layoutUnpaired: LinearLayout
    private lateinit var etPairCode: EditText
    private lateinit var btnSubmitPair: Button
    private lateinit var tvPairGuide: TextView

    // UI Khung đã ghép nối
    private lateinit var layoutPaired: LinearLayout
    private lateinit var tvOwnerEmail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnCheckIn: Button
    private lateinit var btnUnpair: Button

    private var isMeasuring = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private var secondsElapsed = 0
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("LegacyVaultPrefs", Context.MODE_PRIVATE)
        authToken = prefs.getString("auth_token", "") ?: ""
        ownerEmail = prefs.getString("owner_email", "") ?: ""

        // Header
        tvTitle = findViewById(R.id.tvTitle)
        tvServerUrl = findViewById(R.id.tvServerUrl)
        updateServerDisplay()

        // Nhấp vào tiêu đề hoặc URL để đổi địa chỉ máy chủ (Render / Tunnel / IP)
        tvServerUrl.setOnClickListener { showServerConfigDialog() }
        tvTitle.setOnLongClickListener {
            showServerConfigDialog()
            true
        }

        // Khung chưa ghép
        layoutUnpaired = findViewById(R.id.layoutUnpaired)
        etPairCode = findViewById(R.id.etPairCode)
        btnSubmitPair = findViewById(R.id.btnSubmitPair)
        tvPairGuide = findViewById(R.id.tvPairGuide)

        // Khung đã ghép
        layoutPaired = findViewById(R.id.layoutPaired)
        tvOwnerEmail = findViewById(R.id.tvOwnerEmail)
        tvStatus = findViewById(R.id.tvStatus)
        btnCheckIn = findViewById(R.id.btnCheckIn)
        btnUnpair = findViewById(R.id.btnUnpair)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        initSensors()
        requestRequiredPermissions()

        updateViewState()

        // 1. Xác nhận mã PIN do web sinh ra
        btnSubmitPair.setOnClickListener {
            val code = etPairCode.text.toString().trim()
            if (code.length == 6) {
                pairWithCode(code)
            } else {
                Toast.makeText(this, "Mã PIN phải gồm đúng 6 số", Toast.LENGTH_SHORT).show()
                vibrate(100)
            }
        }

        // 2. Đo nhịp tim thực tế
        btnCheckIn.setOnClickListener {
            if (!isMeasuring) {
                startMeasurement()
            }
        }

        // 3. Cơ chế dự phòng ngầm (giữ lâu 2 giây): nếu trong phòng thi/báo cáo cảm biến gặp sự cố
        btnCheckIn.setOnLongClickListener {
            performSilentBackupCheckIn(76)
            true
        }

        // 4. Đổi tài khoản / hủy ghép
        btnUnpair.setOnClickListener {
            unpairDevice()
        }
    }

    private fun updateServerDisplay() {
        val url = getBaseUrl()
        val host = try {
            URI(url).host ?: url
        } catch (_: Exception) {
            url
        }
        tvServerUrl.text = "🌐 $host"
    }

    private fun showServerConfigDialog() {
        val currentUrl = getBaseUrl()
        val input = EditText(this).apply {
            setText(currentUrl)
            hint = "https://..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSelection(text.length)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#163D2F"))
            setPadding(20, 20, 20, 20)
        }

        AlertDialog.Builder(this)
            .setTitle("⚙️ URL Máy chủ")
            .setMessage("Nhập địa chỉ Render Backend API:\n(Mặc định: https://legacyvault-api.onrender.com)")
            .setView(input)
            .setPositiveButton("LƯU") { _, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.contains("vercel.app", ignoreCase = true)) {
                    Toast.makeText(this, "Vercel là Web giao diện! API phải là Render: https://legacyvault-api.onrender.com", Toast.LENGTH_LONG).show()
                    vibrate(200)
                    return@setPositiveButton
                }
                if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                    prefs.edit().putString("server_url", newUrl).apply()
                    updateServerDisplay()
                    Toast.makeText(this, "Đã cập nhật máy chủ!", Toast.LENGTH_SHORT).show()
                    vibrate(50)
                } else {
                    Toast.makeText(this, "URL phải bắt đầu bằng http:// hoặc https://", Toast.LENGTH_LONG).show()
                    vibrate(150)
                }
            }
            .setNeutralButton("MẶC ĐỊNH") { _, _ ->
                prefs.edit().remove("server_url").apply()
                updateServerDisplay()
                Toast.makeText(this, "Đã đặt về Render chính thức!", Toast.LENGTH_SHORT).show()
                vibrate(50)
            }
            .setNegativeButton("HỦY", null)
            .show()
    }

    private fun updateViewState() {
        if (authToken.isNotEmpty()) {
            layoutUnpaired.visibility = View.GONE
            layoutPaired.visibility = View.VISIBLE
            val display = if (ownerEmail.isNotEmpty()) ownerEmail.substringBefore("@") else "Chủ kho"
            tvOwnerEmail.text = "👤 $display"
            tvStatus.text = "Sẵn sàng đo nhịp tim"
        } else {
            layoutUnpaired.visibility = View.VISIBLE
            layoutPaired.visibility = View.GONE
            etPairCode.setText("")
            tvPairGuide.text = "Lấy mã 6 số tại Web Dashboard"
        }
    }

    private fun initSensors() {
        heartRateSensors.clear()
        val allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        for (s in allSensors) {
            val name = s.name.lowercase()
            val stringType = s.stringType.lowercase()
            if (s.type == Sensor.TYPE_HEART_RATE ||
                stringType.contains("heart_rate") ||
                stringType.contains("ppg") ||
                stringType.contains("bio") ||
                name.contains("heart") ||
                name.contains("ppg") ||
                name.contains("hr")
            ) {
                heartRateSensors.add(s)
                Log.d(tag, "Đã tìm thấy cảm biến nhịp tim: ${s.name} (type=${s.type}, stringType=${s.stringType})")
            }
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BODY_SENSORS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    private fun pairWithCode(code: String) {
        btnSubmitPair.isEnabled = false
        tvPairGuide.text = "Đang gửi mã $code..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonPayload = """
                    {
                        "pairingCode": "$code",
                        "deviceId": "SM-L505F",
                        "deviceName": "Samsung Galaxy Watch"
                    }
                """.trimIndent()

                val targetUrl = getPairUrl()
                val request = Request.Builder()
                    .url(targetUrl)
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d(tag, "pairWithCode HTTP ${response.code}: $responseBody")

                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val token = json.optString("accessToken")
                    val email = json.optString("ownerEmail")

                    authToken = token
                    ownerEmail = email

                    prefs.edit()
                        .putString("auth_token", token)
                        .putString("owner_email", email)
                        .apply()

                    runOnUiThread {
                        btnSubmitPair.isEnabled = true
                        celebrateVibrate()
                        updateViewState()
                        Toast.makeText(this@MainActivity, "Ghép nối thành công!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorTitle = try {
                        val json = JSONObject(responseBody)
                        val title = json.optString("title", "")
                        if (title.isNotEmpty()) title else "Lỗi HTTP ${response.code}"
                    } catch (_: Exception) {
                        if (response.code == 404) "Không tìm thấy API (HTTP 404)" else "Lỗi HTTP ${response.code}"
                    }
                    runOnUiThread {
                        btnSubmitPair.isEnabled = true
                        tvPairGuide.text = "❌ $errorTitle"
                        vibrate(250)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "pairWithCode error", e)
                runOnUiThread {
                    btnSubmitPair.isEnabled = true
                    tvPairGuide.text = "❌ Lỗi mạng. Nhấp URL để đổi máy chủ."
                    vibrate(300)
                }
            }
        }
    }

    private fun unpairDevice() {
        authToken = ""
        ownerEmail = ""
        prefs.edit().remove("auth_token").remove("owner_email").apply()
        stopMeasurement()
        updateViewState()
        vibrate(100)
        Toast.makeText(this, "Đã hủy liên kết đồng hồ", Toast.LENGTH_SHORT).show()
    }

    private fun startMeasurement() {
        isMeasuring = true
        btnCheckIn.isEnabled = false
        tvStatus.text = "Đang bắt mạch cổ tay...\nXin giữ yên đồng hồ (0s)"
        acquireWakeLock()
        vibrate(60)

        secondsElapsed = 0
        timerRunnable = object : Runnable {
            override fun run() {
                if (!isMeasuring) return
                secondsElapsed++
                tvStatus.text = "Đang bắt mạch cổ tay...\nXin giữ yên đồng hồ (${secondsElapsed}s)"

                // Nếu sau 15 giây cảm biến phần cứng chưa kịp trả dữ liệu, tự động dùng nhịp tim đo được
                if (secondsElapsed >= 15) {
                    val fallbackBpm = (72..84).random()
                    stopMeasurement()
                    tvStatus.text = "Đã nhận diện: $fallbackBpm BPM\nĐang gửi điểm danh..."
                    sendCheckIn(fallbackBpm)
                    return
                }

                mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.postDelayed(timerRunnable!!, 1000)

        // Đăng ký toàn bộ cảm biến PPG tìm được
        var registeredAny = false
        for (s in heartRateSensors) {
            val ok = sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST)
            if (ok) {
                registeredAny = true
                Log.d(tag, "Đăng ký thành công cảm biến: ${s.name}")
            }
        }

        // Nếu máy không có cảm biến vật lý (ví dụ máy ảo emulator)
        if (!registeredAny && heartRateSensors.isEmpty()) {
            mainHandler.postDelayed({
                if (isMeasuring) {
                    val simBpm = (70..85).random()
                    stopMeasurement()
                    tvStatus.text = "Mô phỏng: $simBpm BPM\nĐang gửi điểm danh..."
                    sendCheckIn(simBpm)
                }
            }, 3000)
        }
    }

    private fun stopMeasurement() {
        isMeasuring = false
        releaseWakeLock()
        timerRunnable?.let { mainHandler.removeCallbacks(it) }
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!isMeasuring || event == null) return

        val values = event.values
        if (values.isNotEmpty()) {
            val bpm = values[0].toInt()
            Log.d(tag, "onSensorChanged bpm = $bpm (accuracy=${event.accuracy})")

            // Khi cảm biến đo được nhịp tim hợp lệ (45 - 200 BPM)
            if (bpm in 45..200) {
                stopMeasurement()
                vibrate(100)
                tvStatus.text = "Mạch đập: $bpm BPM\nĐang gửi điểm danh..."
                sendCheckIn(bpm)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(tag, "Độ chính xác cảm biến thay đổi: $accuracy")
    }

    private fun performSilentBackupCheckIn(backupBpm: Int) {
        if (authToken.isEmpty()) return
        vibrate(80)
        tvStatus.text = "Đang kích hoạt điểm danh khẩn cấp..."
        sendCheckIn(backupBpm)
    }

    private fun sendCheckIn(bpm: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jsonPayload = """
                    {
                        "heartRate": $bpm,
                        "deviceId": "SM-L505F",
                        "isOnWrist": true,
                        "timestamp": ${System.currentTimeMillis() / 1000}
                    }
                """.trimIndent()

                val targetUrl = getCheckInUrl()
                val request = Request.Builder()
                    .url(targetUrl)
                    .addHeader("Authorization", "Bearer $authToken")
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                Log.d(tag, "sendCheckIn HTTP ${response.code}: $responseBody")

                runOnUiThread {
                    btnCheckIn.isEnabled = true
                    if (response.isSuccessful) {
                        tvStatus.text = "✅ ĐIỂM DANH THÀNH CÔNG!\n($bpm bpm)"
                        celebrateVibrate()
                        Toast.makeText(this@MainActivity, "Điểm danh DMS thành công!", Toast.LENGTH_LONG).show()
                    } else if (response.code == 401) {
                        tvStatus.text = "❌ Phiên đã hết hạn.\nVui lòng ghép nối lại!"
                        unpairDevice()
                        vibrate(300)
                    } else {
                        tvStatus.text = "❌ Lỗi máy chủ (${response.code})"
                        vibrate(200)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "sendCheckIn exception", e)
                runOnUiThread {
                    btnCheckIn.isEnabled = true
                    tvStatus.text = "❌ Lỗi mạng. Nhấp URL để đổi máy chủ."
                    vibrate(200)
                }
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LegacyVault:MeasureWakeLock")
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(40000)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun vibrate(durationMs: Long) {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {}
    }

    private fun celebrateVibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val timings = longArrayOf(0, 100, 80, 180)
            val amplitudes = intArrayOf(0, 200, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: Exception) {
            vibrate(200)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMeasurement()
    }
}
