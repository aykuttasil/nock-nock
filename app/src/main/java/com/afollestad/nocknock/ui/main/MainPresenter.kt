/**
 * Designed and developed by Aidan Follestad (@afollestad)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.afollestad.nocknock.ui.main

import android.content.Intent
import com.afollestad.nocknock.data.ServerModel
import com.afollestad.nocknock.engine.db.ServerModelStore
import com.afollestad.nocknock.engine.statuscheck.CheckStatusJob.Companion.ACTION_STATUS_UPDATE
import com.afollestad.nocknock.engine.statuscheck.CheckStatusJob.Companion.KEY_UPDATE_MODEL
import com.afollestad.nocknock.engine.statuscheck.CheckStatusManager
import com.afollestad.nocknock.notifications.NockNotificationManager
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/** @author Aidan Follestad (@afollestad) */
interface MainPresenter {

  fun takeView(view: MainView)

  fun onBroadcast(intent: Intent)

  fun resume()

  fun refreshSite(site: ServerModel)

  fun removeSite(site: ServerModel)

  fun dropView()
}

/** @author Aidan Follestad (@afollestad) */
class RealMainPresenter @Inject constructor(
  private val serverModelStore: ServerModelStore,
  private val notificationManager: NockNotificationManager,
  private val checkStatusManager: CheckStatusManager
) : MainPresenter {

  private var view: MainView? = null

  override fun takeView(view: MainView) {
    this.view = view
    notificationManager.createChannels()
    ensureCheckJobs()
  }

  override fun onBroadcast(intent: Intent) {
    if (intent.action == ACTION_STATUS_UPDATE) {
      val model = intent.getSerializableExtra(KEY_UPDATE_MODEL) as? ServerModel ?: return
      view?.updateModel(model)
    }
  }

  override fun resume() {
    notificationManager.cancelStatusNotifications()
    view!!.run {
      setModels(listOf())
      scopeWhileAttached(Main) {
        launch(coroutineContext) {
          val models = async(IO) {
            serverModelStore.get()
          }.await()
          setModels(models)
        }
      }
    }
  }

  override fun refreshSite(site: ServerModel) {
    checkStatusManager.scheduleCheck(
        site = site,
        rightNow = true,
        cancelPrevious = true
    )
  }

  override fun removeSite(site: ServerModel) {
    checkStatusManager.cancelCheck(site)
    notificationManager.cancelStatusNotification(site)
    view!!.scopeWhileAttached(Main) {
      launch(coroutineContext) {
        async(IO) { serverModelStore.delete(site) }.await()
        view?.onSiteDeleted(site)
      }
    }
  }

  override fun dropView() {
    view = null
  }

  private fun ensureCheckJobs() {
    view!!.scopeWhileAttached(IO) {
      launch(coroutineContext) {
        checkStatusManager.ensureScheduledChecks()
      }
    }
  }
}
