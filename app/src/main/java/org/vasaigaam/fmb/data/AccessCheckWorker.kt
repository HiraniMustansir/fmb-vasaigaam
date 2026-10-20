package org.vasaigaam.fmb.data
import android.content.Context
import androidx.work.*
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit
/** Revalidates membership and cached reads. It never posts operational drafts automatically. */
class AccessCheckWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
 override suspend fun doWork():Result = try {
  val repo:Repository=GlobalContext.get().get()
  repo.ready()
  if(repo.user.isNotEmpty()) repo.snapshot() else repo.clearLocal()
  Result.success()
 } catch(e:Exception) { Result.retry() }
 companion object {
  fun schedule(context:Context) {
   val request=PeriodicWorkRequestBuilder<AccessCheckWorker>(15,TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
   WorkManager.getInstance(context).enqueueUniquePeriodicWork("membership-cache-check",ExistingPeriodicWorkPolicy.KEEP,request)
  }
 }
}
