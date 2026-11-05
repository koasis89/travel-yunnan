package kr.koasis.travelyunnan.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** 범용 표시값: 일정 완료(done:<scheduleId>), 트레킹 진행(trek:<mapId>:<i>), 준비물 체크(pack:<id>) 등 */
@Entity(tableName = "flags")
data class Flag(@PrimaryKey val key: String, val at: Long = System.currentTimeMillis())

@Entity(tableName = "phrase_fav")
data class PhraseFav(@PrimaryKey val phraseId: String, val at: Long = System.currentTimeMillis())

@Entity(tableName = "phrase_recent")
data class PhraseRecent(@PrimaryKey val phraseId: String, val at: Long = System.currentTimeMillis())

/** 경비(S4-5에서 화면 구현) */
@Entity(tableName = "expense")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: String,
    val amount: Double,
    val currency: String,
    val category: String,
    val memo: String = "",
    val method: String = "",
    val payer: String = "",
    val at: Long = System.currentTimeMillis(),
)

/** 사진 일기(S6-5): 일정 행에 붙인 사진 URI 와 메모 */
@Entity(tableName = "diary")
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: String,
    val scheduleId: String? = null,
    val uri: String? = null,
    val memo: String = "",
    val at: Long = System.currentTimeMillis(),
)

@Dao
interface UserDao {
    @Query("SELECT * FROM diary ORDER BY at DESC")
    fun diary(): Flow<List<DiaryEntry>>

    @Insert
    suspend fun addDiary(e: DiaryEntry): Long

    @Update
    suspend fun updateDiary(e: DiaryEntry)

    @Delete
    suspend fun deleteDiary(e: DiaryEntry)

    @Query("SELECT key FROM flags")
    fun flagKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFlag(flag: Flag)

    @Query("DELETE FROM flags WHERE `key` = :key")
    suspend fun clearFlag(key: String)

    @Query("SELECT phraseId FROM phrase_fav ORDER BY at")
    fun favIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFav(f: PhraseFav)

    @Query("DELETE FROM phrase_fav WHERE phraseId = :id")
    suspend fun removeFav(id: String)

    @Query("SELECT phraseId FROM phrase_recent ORDER BY at DESC LIMIT 20")
    fun recentIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun touchRecent(r: PhraseRecent)

    @Query("SELECT * FROM expense ORDER BY at DESC")
    fun expenses(): Flow<List<Expense>>

    @Insert
    suspend fun addExpense(e: Expense): Long

    @Delete
    suspend fun deleteExpense(e: Expense)
}

@Database(entities = [Flag::class, PhraseFav::class, PhraseRecent::class, Expense::class, DiaryEntry::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `diary` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayId` TEXT NOT NULL, `scheduleId` TEXT, `uri` TEXT, `memo` TEXT NOT NULL, `at` INTEGER NOT NULL)")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel_yunnan.db").addMigrations(MIGRATION_1_2).build()
    }
}
