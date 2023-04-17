package yorku.ali.covidtracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

import com.google.gson.GsonBuilder
import com.robinhood.spark.SparkView

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.sql.Time
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*


private const val BASE_URL = "https://api.covidtracking.com/v1/"
private const val TAG = "MainActivity"
class MainActivity : AppCompatActivity() {
    private lateinit var  currentlyShownData: List<CovidData>
    private lateinit var adapter: CovidSparkAdapter
    private lateinit var perStateDailyData: Map<String, List<CovidData>>
    private lateinit var nationalDailyData: List<CovidData>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        val covidService = retrofit.create(CovidService::class.java)
        // attain (fetch) the national data
        covidService.getNationalData().enqueue(object: Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "OnResponse $response")
                val nationalData = response.body()
                if(nationalData == null){
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "Update graph with national data")
                updateDisplayWithData(nationalDailyData)
            }
        })
        // get states data
        covidService.getStateData().enqueue(object: Callback<List<CovidData>> {
            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure $t")
            }

            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "OnResponse $response")
                val stateData = response.body()
                if(stateData == null){
                    Log.w(TAG, "Did not receive a valid response body")
                    return
                }
                perStateDailyData = stateData.reversed().groupBy { it.state }
                Log.i(TAG, "Update spinner with state data")
            }
        })
    }

    private fun setupEventListeners() {
        //add a listener for user scrubbing on chart
        val sparkView = findViewById<SparkView>(R.id.sparkView)
        sparkView.isScrubEnabled = true
        sparkView.setScrubListener { itemData ->
            if (itemData is CovidData) {
                updateInfoForDate(itemData);
            }
        }
        // respond to radio button for selected events
        val radioGroupTimeSelection = findViewById<RadioGroup>(R.id.radioGroupTimeSelection)
        radioGroupTimeSelection.setOnCheckedChangeListener { _, checkedId ->
            adapter.daysAgo = when (checkedId) {
                R.id.radioButtonMonth -> TimeScale.MONTH
                R.id.radioButtonWeek -> TimeScale.WEEK
                else -> TimeScale.MAX
            }
            adapter.notifyDataSetChanged()
        }
        val radioGroupMetricSelection = findViewById<RadioGroup>(R.id.radioGroupMetricSelection)
        radioGroupMetricSelection.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        //update the colout of the chart
        val colourRes = when (metric) {
            Metric.NEGATIVE -> R.color.colorNegative
            Metric.POSITIVE -> R.color.colorPositive
            Metric.DEATH -> R.color.colorDeath

        }
        @ColorInt val colorInt = ContextCompat.getColor(this, colourRes)
        val sparkView = findViewById<SparkView>(R.id.sparkView)
        sparkView.lineColor = colorInt
        val tvMetricLabel = findViewById<TextView>(R.id.tvMetricLabel)
        tvMetricLabel.setTextColor(colorInt)


        //updating metric on the adapter
        adapter.metric = metric
        adapter.notifyDataSetChanged()
        //  reset number
        updateInfoForDate(currentlyShownData.last())
    }


    private fun updateDisplayWithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
        // Crete new sparkadapter with the data
        adapter = CovidSparkAdapter(dailyData)
        val sparkView = findViewById<SparkView>(R.id.sparkView)
        sparkView.adapter = adapter
        // update radio buttons to select the positive cases and max time by default
        //display metric for the most recent date
        val radiobuttonPositive = findViewById<RadioButton>(R.id.radioButtonPositive)
        val radioButtonMax = findViewById<RadioButton>(R.id.radioButtonMax)
        radiobuttonPositive.isChecked = true
        radioButtonMax.isChecked = true
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInfoForDate(covidData: CovidData) {
        val numCases = when (adapter.metric) {
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease

        }
        val tvMetricLabel = findViewById<TextView>(R.id.tvMetricLabel)
        val tvDateLabel = findViewById<TextView>(R.id.tvDateLabel)
        tvMetricLabel.text = NumberFormat.getInstance().format(covidData.positiveIncrease)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        tvDateLabel.text = outputDateFormat.format(covidData.dateChecked)
    }
}