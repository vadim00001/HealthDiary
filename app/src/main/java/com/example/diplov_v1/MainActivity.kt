package com.example.diplov_v1

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.diplov_v1.databinding.MainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

fun setWorkRequestForNextDay(context: Context) {
    val current = Calendar.getInstance()
    val nextDay = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 0)
    }
    val delay = nextDay.timeInMillis - current.timeInMillis

    val workRequest = PeriodicWorkRequestBuilder<MyWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context).enqueue(workRequest)
    Log.d("MyLog", "WorkerExist")
}

class MyWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams),
    SensorEventListener {
    private var stepCountAtStartOfDay: Int = 0
    private var currentStepCount: Int = 0
    private var stepsNorm: Int = 5000

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null


    private var date: String = ""
    private lateinit var database: Db

    override fun doWork(): Result {
        val calendar: Calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val time = calendar.time
        date = formatter.format(time)

        val dateBackCalendar = Calendar.getInstance()
        dateBackCalendar.add(Calendar.DAY_OF_YEAR, -1)
        val dateBack = formatter.format(dateBackCalendar.time)

        database = Db.getDb(applicationContext)


        //updateCurrentSteps()
        //Log.d("MyLog", currentStepCount.toString())

        /*
                Thread {
                    try {
                        stepsNorm =
                            database.stepsCounterDao().getDayData(dateBack, dateBack).last().stepsNorm
                        currentSteps =
                            database.stepsCounterDao().getDayData(dateBack, dateBack).last().stepCountEnd

                        stepCountAtStartOfDay = currentSteps
                        val data = StepsCounterEntity(
                            null,
                            date,
                            0,
                            stepsNorm,
                            stepCountAtStartOfDay,
                            currentSteps
                        )

                        database.stepsCounterDao().insert(data)

                        Log.d("MyLog", "Воркер сработал")

                    } catch (_: Exception) {
                    }
                }.start()
        */


        return Result.success()
    }

    private fun updateCurrentSteps() {
        Thread {
            database.stepsCounterDao().updateCurrentSteps(currentStepCount, date, date)
        }.start()
    }

    private fun saveToDb() {
    }

    override fun onSensorChanged(event: SensorEvent?) {
        currentStepCount = event!!.values[0].toInt()
        updateCurrentSteps()
        Log.d("MyLog", currentStepCount.toString())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        TODO("Not yet implemented")
    }
}

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var bg: MainBinding

    private lateinit var database: Db

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private var launcher: ActivityResultLauncher<Intent>? = null

    private var date: String = ""
    private var dateBack: String = ""
    private var name: String = ""
    private var weight: Double = 0.0
    private var caloriesNow: Double = 0.0
    private var caloriesMax: Double = 2500.0

    private var stepsNow: Int = 0
    private var stepCountAtStartOfDay: Int = 0
    private var currentStepCount: Int = 0
    private var stepsNorm: Int = 5000

    private var waterNorm: Int = 2000
    private var waterNow: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bg = MainBinding.inflate(layoutInflater)
        setContentView(bg.root)

        supportActionBar?.title = getString(R.string.name_app)
        name = getString(R.string.fillProfile)
        bg.txtHello.text = name

        database = Db.getDb(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        getPermission()

        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val time = calendar.time
        date = formatter.format(time)

            //setWorkRequestForNextDay(this)

        bg.cvProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            launcher?.launch(intent)
        }

        bg.cvNutrition.setOnClickListener {
            val intent = Intent(this, NutritionActivity::class.java)
            launcher?.launch(intent)
        }

        bg.cvSteps.setOnClickListener {
            val intent = Intent(this, StepCounter::class.java)
            launcher?.launch(intent)
        }

        bg.cvWater.setOnClickListener {
            val intent = Intent(this, WaterActivity::class.java)
            launcher?.launch(intent)
        }

        bg.cvStat.setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            launcher?.launch(intent)
        }

        bg.cvHealth.setOnClickListener {
            val intent = Intent(this, HealthIndicators::class.java)
            launcher?.launch(intent)
        }

        launcher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == RESULT_OK) {
                    loadFromDb()
                }
                if (result.resultCode == RESULT_CANCELED) {
                    loadFromDb()
                }
            }

        loadFromDb()
    }

    private fun loadFromDb() {
        lifecycleScope.launch(Dispatchers.IO) {
            val nutrData = database.listNutrDao().getDayData(date, date)
            val profileData = database.profileDao().getProfileData()
            val stepsData = database.stepsCounterDao().getDayData(date, date)
            val waterData = database.waterCounterDao().getDayData(date, date)
            withContext(Dispatchers.Main) {
                caloriesNow = 0.0
                waterNow = 0

                if (profileData.isNotEmpty()) {
                    name = profileData.last().name
                    weight = profileData.last().weight
                    caloriesMax = profileData.last().kcal
                    bg.txtHello.text = "${getString(R.string.Hello)}, $name!"

                    waterNorm = (weight * 30).roundToInt()
                }
                bg.txtMainWeight.text = "$weight кг"

                if (nutrData.isNotEmpty()) {
                    nutrData.forEach { caloriesNow += it.kcal }
                }
                bg.progressBarMainCalories.max = caloriesMax.roundToInt()
                bg.progressBarMainCalories.progress = caloriesNow.roundToInt()
                bg.txtMainCalories.text =
                    "${caloriesNow.roundToInt()} / ${caloriesMax.roundToInt()}"

                if (stepsData.isNotEmpty()) {
                    stepCountAtStartOfDay = stepsData.last().stepCountStart
                    stepsNorm = stepsData.last().stepsNorm
                }
                stepsNow = currentStepCount - stepCountAtStartOfDay

                if (stepsNow < 0) {
                    var stepCountStart: Int = 0
                    Thread {
                        database.stepsCounterDao()
                            .updateStepCountStart(currentStepCount, date, date)
                        stepCountStart =
                            database.stepsCounterDao().getDayData(date, date).last().stepCountStart
                    }.start()

                    stepsNow = currentStepCount - stepCountStart
                }
                bg.progressBarMainSteps.max = stepsNorm
                bg.progressBarMainSteps.progress = stepsNow
                bg.txtMainSteps.text = "$stepsNow / $stepsNorm"

                if (waterData.isNotEmpty()) {
                    waterNorm = waterData.last().waterNorm
                    waterData.forEach { waterNow += it.glassVolume }
                }
                bg.progressBarMainWater.max = waterNorm
                bg.progressBarMainWater.progress = waterNow
                bg.txtMainWater.text = "$waterNow / $waterNorm"
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            currentStepCount = event.values[0].toInt()

            val calendar = Calendar.getInstance()
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val time = calendar.time
            date = formatter.format(time)

            val calendarBack = calendar.clone() as Calendar
            calendarBack.add(Calendar.DAY_OF_YEAR, -1)
            dateBack = formatter.format(calendarBack.time)

            updateCurrentDate()
            updateCurrentSteps()
            loadFromDb()
        }
    }

    private fun updateCurrentSteps() {
        Thread {
            database.stepsCounterDao().updateCurrentSteps(currentStepCount, date, date)
        }.start()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun getPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                0
            )
        }
    }

    override fun onResume() {
        super.onResume()

        val calendar = Calendar.getInstance()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val time = calendar.time
        date = formatter.format(time)

        stepCounterSensor?.also { stepCounter ->
            sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }

    private fun updateCurrentDate() {
        lifecycleScope.launch(Dispatchers.IO) {
            val data = database.stepsCounterDao().getDayData(date, date)
            val dataBack = database.stepsCounterDao().getDayData(dateBack, dateBack)
            withContext(Dispatchers.Main) {
                if (data.isEmpty()) {
                    stepCountAtStartOfDay = if (dataBack.isNotEmpty()) {
                        dataBack.last().stepCountEnd
                    } else {
                        currentStepCount
                    }
                    saveToDb()
                }
            }
        }
    }

    private fun saveToDb() {
        val data = StepsCounterEntity(
            null,
            date,
            0,
            stepsNorm,
            stepCountAtStartOfDay,
            currentStepCount
        )
        Thread {
            database.stepsCounterDao().insert(data)
        }.start()
    }
}