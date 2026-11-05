package kr.koasis.travelyunnan

import android.app.Application
import kr.koasis.travelyunnan.data.SettingsStore
import kr.koasis.travelyunnan.data.TripData
import kr.koasis.travelyunnan.data.db.AppDatabase
import kr.koasis.travelyunnan.secure.SecureStore
import kr.koasis.travelyunnan.tts.TtsPlayer

/** 수동 의존성 컨테이너(규모가 작아 Hilt 대신 사용) */
class AppContainer(app: Application) {
    val tripData: TripData by lazy { TripData.load(app) }
    val db: AppDatabase by lazy { AppDatabase.create(app) }
    val settings = SettingsStore(app)
    val tts: TtsPlayer by lazy { TtsPlayer(app) }
    val secure: SecureStore by lazy { SecureStore(app) }
}

class TravelApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
