package org.tvheadend.tvhclient.data.worker

import android.content.Context
import android.content.Intent
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.tvheadend.tvhclient.data.service.HtspIntentService
import timber.log.Timber

class LoadChannelIconWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    override fun doWork(): ListenableWorker.Result {
        Timber.d("Loading channel icons from server")
        HtspIntentService.enqueueWork(applicationContext, Intent().setAction("loadChannelIcons"))
        return ListenableWorker.Result.success()
    }
}
