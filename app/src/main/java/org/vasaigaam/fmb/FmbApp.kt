package org.vasaigaam.fmb
import android.app.Application
import androidx.room.Room
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.vasaigaam.fmb.data.*
class FmbApp : Application() {
 override fun onCreate() { super.onCreate(); startKoin { androidContext(this@FmbApp); modules(module {
  single { Room.databaseBuilder(get(), DraftDatabase::class.java, "fmb-drafts.db").build() }
  single { Repository(get()) }
 }) }; AccessCheckWorker.schedule(this) }
}
