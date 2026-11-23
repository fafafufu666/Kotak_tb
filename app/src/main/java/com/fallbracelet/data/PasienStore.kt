package com.fallbracelet.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** What a [KejadianPasien] row records. */
enum class JenisKejadian {
    /** The box lid was opened (real box over BLE, or the simulated box). */
    LID_OPEN,
    /** The patient pressed "Saya sudah minum obat". */
    KONFIRMASI_MANUAL,
}

/** Where a [KejadianPasien] came from. */
enum class SumberKejadian { KOTAK, SIMULASI, PASIEN }

/**
 * One patient-side record: a box opening or a manual confirmation. Day statuses are worked out
 * from these by `adherence/RingkasanHarian`, so nothing derived is stored.
 */
@Entity(tableName = "kejadian_pasien", indices = [Index(value = ["waktu"])])
data class KejadianPasien(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [JenisKejadian] name. */
    val jenis: String,
    /** When it happened, epoch ms (the virtual clock while demo mode is on). */
    val waktu: Long,
    /** When the phone stored it, epoch ms (real clock). */
    val diterimaPada: Long,
    /** [SumberKejadian] name. */
    val sumber: String,
    /** Made in demo mode; removed by "Atur ulang demo" and ignored while demo mode is off. */
    val demo: Boolean,
)

/** A `jam_minum` that applies from [berlakuMulai] (ISO date, e.g. 2026-11-23) on. */
@Entity(tableName = "jam_minum")
data class JamMinumEntity(
    @PrimaryKey val berlakuMulai: String,
    /** Minutes after midnight, e.g. 420 = 07:00. */
    val menitDalamHari: Int,
)

@Dao
interface PasienDao {
    @Insert
    suspend fun tambah(k: KejadianPasien): Long

    @Insert
    suspend fun tambahSemua(k: List<KejadianPasien>)

    @Query("SELECT * FROM kejadian_pasien ORDER BY waktu ASC, id ASC")
    fun semuaKejadian(): Flow<List<KejadianPasien>>

    @Query("DELETE FROM kejadian_pasien WHERE demo = 1")
    suspend fun hapusDemo()

    @Query("DELETE FROM kejadian_pasien")
    suspend fun hapusSemuaKejadian()

    @Query("SELECT * FROM jam_minum ORDER BY berlakuMulai ASC")
    fun jamMinum(): Flow<List<JamMinumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun simpanJamMinum(j: JamMinumEntity)

    @Query("DELETE FROM jam_minum")
    suspend fun hapusJamMinum()
}

/**
 * The patient's records, in their own database file so the bracelet's `bracelet.db` (and its
 * migrations) stays untouched.
 */
@Database(entities = [KejadianPasien::class, JamMinumEntity::class], version = 1, exportSchema = false)
abstract class PasienDatabase : RoomDatabase() {
    abstract fun pasien(): PasienDao

    companion object {
        fun create(context: Context): PasienDatabase =
            Room.databaseBuilder(context, PasienDatabase::class.java, "pasien.db").build()
    }
}
