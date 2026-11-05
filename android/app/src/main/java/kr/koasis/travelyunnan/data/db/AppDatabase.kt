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
    /** 영수증 사진 URI(사진 선택기 영구 권한), 영수증 인식으로 만든 항목 여부 */
    val photoUri: String? = null,
    val ocr: Boolean = false,
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

/** 문서함: 바우처·기차표·숙소 예약·영수증 등. 파일은 files/docs/{id}/ (DocStore) */
@Entity(tableName = "docs")
data class TripDoc(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val dayId: String? = null,
    val note: String = "",
    val mime: String,
    val fileName: String,
    val size: Long = 0,
    /** PDF 를 그림으로 펼친 쪽수(사진이면 0) */
    val pages: Int = 0,
    val created: Long = System.currentTimeMillis(),
)

@Dao
interface UserDao {
    @Query("SELECT * FROM docs ORDER BY created DESC")
    fun docs(): Flow<List<TripDoc>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDoc(d: TripDoc)

    @Query("DELETE FROM docs WHERE id = :id")
    suspend fun deleteDoc(id: String)

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

@Database(entities = [Flag::class, PhraseFav::class, PhraseRecent::class, Expense::class, DiaryEntry::class, TripDoc::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `diary` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayId` TEXT NOT NULL, `scheduleId` TEXT, `uri` TEXT, `memo` TEXT NOT NULL, `at` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `photoUri` TEXT")
                db.execSQL("ALTER TABLE `expense` ADD COLUMN `ocr` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `docs` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `kind` TEXT NOT NULL, `dayId` TEXT, `note` TEXT NOT NULL, `mime` TEXT NOT NULL, `fileName` TEXT NOT NULL, `size` INTEGER NOT NULL, `pages` INTEGER NOT NULL, `created` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "travel_yunnan.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
