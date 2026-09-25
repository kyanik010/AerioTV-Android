package com.aeriotv.android

import android.app.Application
import com.aeriotv.android.core.cast.AerioCastReceiverController
import com.aeriotv.android.core.cast.CastNotificationController
import com.aeriotv.android.core.cast.companion.CompanionHostController
import com.aeriotv.android.core.debug.DebugLogger
import com.aeriotv.android.core.debug.ResourceTelemetry
import com.aeriotv.android.core.network.DispatcharrWarmupCoordinator
import com.aeriotv.android.core.preferences.AppPreferences
import com.aeriotv.android.core.timeshift.TimeshiftBufferStore
import com.aeriotv.android.feature.reminders.ReminderBannerBus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Job
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class AerioStartupCoordinator @Inject constructor(
    private val dispatcharrWarmup: Provider<DispatcharrWarmupCoordinator>,
    private val appPreferences: Provider<AppPreferences>,
    private val reminderBannerBus: Provider<ReminderBannerBus>,
    private val resourceTelemetry: Provider<ResourceTelemetry>,
    private val castReceiver: Provider<AerioCastReceiverController>,
    private val castNotificationController: Provider<CastNotificationController>,
    private val companionHost: Provider<CompanionHostController>,
    private val timeshiftStore: Provider<TimeshiftBufferStore>,
    private val debugLogger: Provider<DebugLogger>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false

    fun startAfterFirstFrame(application: Application): Job? {
        if (started) return null
        synchronized(this) {
            if (started) return null
            started = true
        }

        val prefs = appPreferences.get()
        val logger = debugLogger.get()

        return scope.launch {
            supervisorScope {
                launch { runCatching { dispatcharrWarmup.get().bind() } }
                launch { runCatching { reminderBannerBus.get().bind() } }
                launch { runCatching { castReceiver.get().bootstrap(application) } }
                launch { runCatching { castNotificationController.get().start() } }
                launch { runCatching { companionHost.get().start() } }
                launch {
                    runCatching {
                        timeshiftStore.get().pruneExpired(
                            com.aeriotv.android.core.timeshift.TimeshiftController.FIXED_RETENTION_MS,
                        )
                        val store = timeshiftStore.get()
                        store.enforceBudget(store.freeSpaceBudgetBytes())
                    }
                }
                launch { runCatching { resourceTelemetry.get().start() } }
                launch {
                    prefs.timeFormat.collect {
                        com.aeriotv.android.core.ui.ClockFormat.mode.value =
                            com.aeriotv.android.core.ui.ClockFormat.fromPref(it)
                    }
                }
                launch {
                    prefs.skipBackSeconds.distinctUntilChanged().collect {
                        com.aeriotv.android.core.ui.SkipIntervals.backSeconds.value = it
                    }
                }
                launch {
                    prefs.skipForwardSeconds.distinctUntilChanged().collect {
                        com.aeriotv.android.core.ui.SkipIntervals.forwardSeconds.value = it
                    }
                }
                launch {
                    prefs.debugLoggingEnabled.distinctUntilChanged().collectLatest { enabled ->
                        logger.setEnabled(enabled)
                    }
                }
                launch {
                    combine(
                        prefs.backgroundRefreshEnabled,
                        prefs.backgroundRefreshIntervalMins,
                    ) { enabled, mins -> enabled to mins }
                        .collectLatest { (enabled, mins) ->
                            if (enabled) {
                                com.aeriotv.android.core.data.sync.PlaylistRefreshWorker.enqueuePeriodic(
                                    application,
                                    intervalMins = mins,
                                )
                            } else {
                                com.aeriotv.android.core.data.sync.PlaylistRefreshWorker.cancel(application)
                            }
                        }
                }
                launch { runCatching { com.aeriotv.android.core.data.repository.PlaylistRepository::class } }
            }
        }
    }

    fun publishPendingCrashLog(application: Application) {
        scope.launch {
            runCatching {
                com.aeriotv.android.core.debug.CrashReporter.publishToDebugLog(
                    application,
                    debugLogger.get().logFile(),
                )
            }
        }
    }
}
