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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
@Entity(
    tableName = "kejadian_pasien",
    indices = [Index(value = ["waktu"]), Index(value = ["perangkat", "seqKotak"], unique = true)],
)
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
    /**
     * For a box event: the box's name and its event `seq`. Unique together, so an event the box
     * sends again (its ACK was lost) is stored once. Null for manual confirmations.
     */
    val perangkat: String? = null,
    val seqKotak: Long? = null,
)

/** A `jam_minum` that applies from [berlakuMulai] (ISO date, e.g. 2026-11-23) on. */
@Entity(tableName = "jam_minum")
data class JamMinumEntity(
    @PrimaryKey val berlakuMulai: String,
    /** Minutes after midnight, e.g. 420 = 07:00. */
    val menitDalamHari: Int,
)

/**
 * One reminder moment (P5 and the dose notifications): when it was handled, what it did, and
 * for the special reminder which button the patient pressed. A pending "Ingatkan x menit lagi"
 * is a TUNDA row with [ditanganiPada] still null.
 */
@Entity(tableName = "log_pengingat")
data class LogPengingat(
    /** `adherence.Pemicu.kunci`, with "demo/" in front for rows made in demo mode. */
    @PrimaryKey val kunci: String,
    /** The dose date (ISO). */
    val tanggal: String,
    /** `adherence.JenisPemicu` name. */
    val jenis: String,
    val urutan: Int,
    /** When the moment was due, epoch ms (virtual clock in demo mode). */
    val waktuPemicu: Long,
    /** When it was handled (shown or skipped), epoch ms, virtual clock; null = still pending. */
    val ditanganiPada: Long?,
    /** `adherence.Tindakan` name, or [DILEWATI]. */
    val hasil: String?,
    /** The message id shown (special reminder only). */
    val pesanId: String?,
    /** Button pressed on the special reminder: [AKSI_SUDAH_MINUM], [AKSI_TUNDA] or [AKSI_DITUTUP]. */
    val aksi: String?,
    val aksiPada: Long?,
    val demo: Boolean,
) {
    companion object {
        const val DILEWATI = "DILEWATI"
        const val AKSI_SUDAH_MINUM = "SUDAH_MINUM"
        const val AKSI_TUNDA = "TUNDA"
        const val AKSI_DITUTUP = "DITUTUP"
    }
}

/**
 * One missed day that qualified for an ESKALASI to the PMO (PRD 7.4), sent or only recorded, and
 * what the relay said about it ("Dilihat oleh …").
 */
@Entity(tableName = "eskalasi")
data class EskalasiEntity(
    /** [perangkat] + "/" + [tanggal]: one per missed day and per demo session. */
    @PrimaryKey val kunci: String,
    /** The missed dose's date (ISO). */
    val tanggal: String,
    /** The relay's device address: [PERANGKAT_NYATA], or "demo-" + a session id in demo mode. */
    val perangkat: String,
    val demo: Boolean,
    /** When it was decided, epoch ms (virtual clock in demo mode). */
    val dibuatPada: Long,
    val hariKe: Int,
    val terlewatBerturut: Int,
    /** The last box opening before it, epoch ms; null = never opened. */
    val terakhirDibuka: Long?,
    val hariRawan: Boolean,
    /** False for an older day recorded together with a newer one: never sent. */
    val kirim: Boolean,
    /** When the relay accepted it (real clock), and its id there; null while waiting. */
    val terkirimPada: Long?,
    val idRelay: Long?,
    val dilihatOleh: String?,
    val dilihatPada: Long?,
) {
    companion object {
        const val PERANGKAT_NYATA = "kotakobat"
    }
}

@Dao
interface PasienDao {
    @Insert
    suspend fun tambah(k: KejadianPasien): Long

    /** Returns -1 when this box event (perangkat, seqKotak) is already stored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tambahDariKotak(k: KejadianPasien): Long

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

    @Query("SELECT * FROM log_pengingat ORDER BY waktuPemicu ASC")
    fun logPengingat(): Flow<List<LogPengingat>>

    @Query("SELECT * FROM log_pengingat")
    suspend fun logPengingatSekarang(): List<LogPengingat>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun simpanLog(l: LogPengingat)

    @Query("UPDATE log_pengingat SET aksi = :aksi, aksiPada = :pada WHERE kunci = :kunci AND aksi IS NULL")
    suspend fun catatAksi(kunci: String, aksi: String, pada: Long)

    @Query("DELETE FROM log_pengingat WHERE demo = 1")
    suspend fun hapusLogDemo()

    @Query("DELETE FROM log_pengingat")
    suspend fun hapusSemuaLog()

    @Query("SELECT * FROM eskalasi ORDER BY tanggal ASC")
    fun eskalasi(): Flow<List<EskalasiEntity>>

    @Query("SELECT * FROM eskalasi WHERE kirim = 1 AND terkirimPada IS NULL ORDER BY tanggal ASC")
    suspend fun eskalasiMenunggu(): List<EskalasiEntity>

    /** Sent since [sejak] (real clock) and not yet seen by a PMO. */
    @Query("SELECT COUNT(*) FROM eskalasi WHERE kirim = 1 AND terkirimPada > :sejak AND dilihatPada IS NULL")
    suspend fun jumlahBelumDilihat(sejak: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun tambahEskalasi(e: EskalasiEntity)

    @Query("UPDATE eskalasi SET terkirimPada = :pada, idRelay = :id WHERE kunci = :kunci")
    suspend fun eskalasiTerkirim(kunci: String, pada: Long, id: Long)

    @Query(
        "UPDATE eskalasi SET dilihatOleh = :oleh, dilihatPada = :pada " +
            "WHERE perangkat = :perangkat AND tanggal = :tanggal AND dilihatPada IS NULL"
    )
    suspend fun eskalasiDilihat(perangkat: String, tanggal: String, oleh: String, pada: Long)

    @Query("DELETE FROM eskalasi WHERE demo = 1")
    suspend fun hapusEskalasiDemo()

    @Query("DELETE FROM eskalasi")
    suspend fun hapusSemuaEskalasi()
}

/**
 * The patient's records, in their own database file so the bracelet's `bracelet.db` (and its
 * migrations) stays untouched.
 */
@Database(
    entities = [KejadianPasien::class, JamMinumEntity::class, LogPengingat::class, EskalasiEntity::class],
    version = 4, exportSchema = false,
)
abstract class PasienDatabase : RoomDatabase() {
    abstract fun pasien(): PasienDao

    companion object {
        fun create(context: Context): PasienDatabase =
            Room.databaseBuilder(context, PasienDatabase::class.java, "pasien.db").addMigrations(MIGRASI_1_2, MIGRASI_2_3, MIGRASI_3_4).build()

        /** Stage 2b: adds the reminder log; earlier records stay. */
        val MIGRASI_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `log_pengingat` (`kunci` TEXT NOT NULL, `tanggal` TEXT NOT NULL, " +
                        "`jenis` TEXT NOT NULL, `urutan` INTEGER NOT NULL, `waktuPemicu` INTEGER NOT NULL, " +
                        "`ditanganiPada` INTEGER, `hasil` TEXT, `pesanId` TEXT, `aksi` TEXT, `aksiPada` INTEGER, " +
                        "`demo` INTEGER NOT NULL, PRIMARY KEY(`kunci`))"
                )
            }
        }

        /** Stage 4: box events carry the box name and seq, unique together. */
        val MIGRASI_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `kejadian_pasien` ADD COLUMN `perangkat` TEXT")
                db.execSQL("ALTER TABLE `kejadian_pasien` ADD COLUMN `seqKotak` INTEGER")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_kejadian_pasien_perangkat_seqKotak` " +
                        "ON `kejadian_pasien` (`perangkat`, `seqKotak`)"
                )
            }
        }

        /** Stage 3: adds the ESKALASI record; earlier records stay. */
        val MIGRASI_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `eskalasi` (`kunci` TEXT NOT NULL, `tanggal` TEXT NOT NULL, " +
                        "`perangkat` TEXT NOT NULL, `demo` INTEGER NOT NULL, `dibuatPada` INTEGER NOT NULL, " +
                        "`hariKe` INTEGER NOT NULL, `terlewatBerturut` INTEGER NOT NULL, `terakhirDibuka` INTEGER, " +
                        "`hariRawan` INTEGER NOT NULL, `kirim` INTEGER NOT NULL, `terkirimPada` INTEGER, " +
                        "`idRelay` INTEGER, `dilihatOleh` TEXT, `dilihatPada` INTEGER, PRIMARY KEY(`kunci`))"
                )
            }
        }
    }
}
