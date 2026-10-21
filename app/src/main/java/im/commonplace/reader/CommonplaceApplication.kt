package im.commonplace.reader

import android.app.Application

class CommonplaceApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
