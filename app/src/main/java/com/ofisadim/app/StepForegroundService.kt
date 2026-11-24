package com.ofisadim.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.text.NumberFormat
import java.util.Locale

class StepForegroundService : Service(), SensorEventListener {

    companion object {
        const val CHANNEL_ID = "ofis_adim_step_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STEPS_UPDATED = "com.ofisadim.app.STEPS_UPDATED"
        const val EXTRA_STEPS = "extra_steps"
        const val EXTRA_SENSOR_TYPE = "extra_sensor_type"
    }

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null
    private var stepDetectorSensor: Sensor? = null
    private var activeSensorLabel: String = "HARDWARE_STEP_COUNTER"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val currentSteps = StepStore.getTodaySteps(this)
        val notification = buildNotification(currentSteps)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }

        registerStepSensor()
        broadcastSteps(currentSteps)
        return START_STICKY
    }

    private fun registerStepSensor() {
        sensorManager.unregisterListener(this)
        when {
            stepCounterSensor != null -> {
                activeSensorLabel = "HARDWARE_STEP_COUNTER"
                sensorManager.registerListener(
                    this,
                    stepCounterSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
            stepDetectorSensor != null -> {
                activeSensorLabel = "HARDWARE_STEP_DETECTOR"
                sensorManager.registerListener(
                    this,
                    stepDetectorSensor,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val updatedSteps = when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                StepStore.onHardwareCounterValue(this, event.values[0].toInt())
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                StepStore.addSingleStep(this)
            }
            else -> return
        }

        updateNotification(updatedSteps)
        broadcastSteps(updatedSteps)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun broadcastSteps(steps: Int) {
        val intent = Intent(ACTION_STEPS_UPDATED).apply {
            setPackage(packageName)
            putExtra(EXTRA_STEPS, steps)
            putExtra(EXTRA_SENSOR_TYPE, activeSensorLabel)
        }
        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ofis Adım Canlı Sayaç",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ekran kapalıyken adımlarınızı sessizce sayar"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(steps: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formatted = NumberFormat.getNumberInstance(Locale("tr", "TR")).format(steps)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ofis Adım — $formatted adım")
            .setContentText("Donanım sensörü 7/24 aktif • Ekran kapalıyken de sayıyor")
            .setSmallIcon(R.drawable.ic_step_notif)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(steps: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(steps))
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
