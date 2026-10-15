package id.dompet.pribadi

import android.app.Application
import id.dompet.pribadi.data.AppGraph

class DompetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.database(this)
    }
}
