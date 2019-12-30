package com.vaudibert.canidrive.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.MutableLiveData
import com.vaudibert.canidrive.R
import com.vaudibert.canidrive.data.DrinkDao
import com.vaudibert.canidrive.domain.Drink
import com.vaudibert.canidrive.domain.Drinker
import com.vaudibert.canidrive.domain.DriveLaw
import com.vaudibert.canidrive.domain.DriveLaws
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.*

/**
 * Repository holding the drinker and driveLaw instances.
 *
 * It establishes the link between the drinker absolute state and the drive law limits that depend
 * on the country selection.
 *
 * It is responsible for retrieving all saved values with the given :
 *  - sharedPreferences instance for :
 *      - weight
 *      - sex
 *      - country code (= drive law)
 *      - init flag (= user configuration already validated once)
 *  - drinkDao for past consumed drinks.
 */
class DrinkerRepository {

    // Main instances to link
    private var drinker = Drinker()
    private var driveLaw : DriveLaw? = null

    // Flag for initialization, saved when set.
    var init : Boolean = false
        set(value) {
            field = value
            sharedPref.edit()
                .putBoolean(context.getString(R.string.user_initialized), value)
                .apply()
        }

    // Objects needed for DB (async) operations (Dao injected after repository instanciation).
    private var daoJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + daoJob)
    private lateinit var drinkDao : DrinkDao

    // References needed for SharedPreferences operations
    private lateinit var context: Context
    private lateinit var sharedPref: SharedPreferences

    // LiveData exposed to UI.
    val liveDrinker = MutableLiveData<Drinker>(drinker)

    /**
     * Context (main activity) reference set.
     *
     * Triggers the retrieval of all SharedPreferences-linked values.
     */
    fun setContext(context: Context) {
        this.context = context
        sharedPref = context.getSharedPreferences(context.getString(R.string.user_preferences), Context.MODE_PRIVATE)

        val weight = sharedPref.getFloat(context.getString(R.string.user_weight), 70F).toDouble()
        val sex = sharedPref.getString(context.getString(R.string.user_sex), "NONE") ?: "NONE"
        val countryCode = sharedPref.getString(context.getString(R.string.countryCode), "")
        init = sharedPref.getBoolean(context.getString(R.string.user_initialized), false)

        drinker = Drinker(weight, sex)
        driveLaw = DriveLaws.countryLaws.find { law -> law.countryCode == countryCode }
        liveDrinker.value = drinker

    }

    /**
     * DrinkDao set.
     *
     * Triggers the retrieval of all drink-DB entries.
     */
    fun setDao(drinkDao: DrinkDao) {
        this.drinkDao = drinkDao
        uiScope.launch {
            val drinks = drinkDao.getAll()
            drinks.forEach { drinker.ingest(it.toDrink())}
            liveDrinker.postValue(drinker)
        }
    }

    // Addition and removal of drinks, saved in DB

    fun ingest(drink : Drink) {
        drinker.ingest(drink)
        uiScope.launch { drinkDao.insert(drink) }
        liveDrinker.value = drinker
    }

    fun remove(drink: Drink) {
        drinker.remove(drink)
        uiScope.launch { drinkDao.remove(drink) }
        liveDrinker.value = drinker
    }

    // Set of values saved in SharedPreferences

    fun setWeight(weight: Double) {
        drinker.weight = weight
        sharedPref
            .edit()
            .putFloat(context.getString(R.string.user_weight), weight.toFloat())
            .apply()

        liveDrinker.value = drinker
    }

    fun setSex(sex: String) {
        drinker.sex = sex
        sharedPref
            .edit()
            .putString(context.getString(R.string.user_sex), sex)
            .apply()
        liveDrinker.value = drinker
    }

    fun setDriveLaw(driveLaw: DriveLaw?) {
        this.driveLaw = driveLaw
        sharedPref.edit()
            .putString(context.getString(R.string.countryCode), driveLaw?.countryCode ?: "")
            .apply()
    }

    // Getters needed for UI

    fun getDrinks() = drinker.getDrinks()

    fun getWeight() = drinker.weight

    fun getSex() = drinker.sex

    /**
     * Returns a boolean drive status.
     *
     * Makes use of the drive law selected to assess if limit is reached.
     */
    fun canDrive(): Boolean {
        return drinker.alcoholRateAt(Date()) <= driveLaw?.limit ?:0.01
    }

    /**
     * Returns the date when drinker is legally safe to drive.
     */
    fun timeToDrive(): Date {
        return drinker.timeToReachLimit(driveLaw?.limit ?: 0.01)
    }

    /**
     * Returns the position of the drive law in list of drive laws (per country).
     *
     * Used for UI (spinner current selection).
     */
    fun getCountryPosition() : Int {
        return DriveLaws.countryLaws.indexOf(driveLaw).coerceAtLeast(0)
    }
}