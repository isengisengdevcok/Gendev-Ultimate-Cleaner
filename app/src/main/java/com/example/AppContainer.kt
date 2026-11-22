package com.example

import android.content.Context
import com.example.automation.AutomationManager
import com.example.data.CleaningRepository
import com.example.engine.AdvancedScannerEngine
import com.example.engine.CleanerEngine
import com.example.engine.CrashAnalyzerEngine
import com.example.engine.MediaReclaimerEngine
import com.example.engine.NonRootEngine
import com.example.engine.PreLaunchEngine
import com.example.engine.SecureDeleteEngine
import com.example.engine.StorageAnalyzer
import com.example.engine.StorageForecasterEngine
import com.example.engine.VisualAndContextEngine
import com.example.engine.WhatsAppCleanerEngine
import com.example.root.RootManager
import com.example.system.BatteryManager
import com.example.system.SystemInfoManager

class AppContainer private constructor(context: Context) {
    private val appContext = context.applicationContext

    val rootManager: RootManager = RootManager()
    val repository: CleaningRepository = CleaningRepository(appContext)
    val secureDeleteEngine: SecureDeleteEngine = SecureDeleteEngine(appContext, rootManager, appContext.packageName)
    val cleanerEngine: CleanerEngine = CleanerEngine(rootManager, appContext.packageName, secureDeleteEngine)
    val nonRootEngine: NonRootEngine = NonRootEngine(appContext, rootManager, secureDeleteEngine)
    val advancedScannerEngine: AdvancedScannerEngine = AdvancedScannerEngine(appContext, rootManager, secureDeleteEngine)
    val whatsAppCleanerEngine: WhatsAppCleanerEngine = WhatsAppCleanerEngine(rootManager, secureDeleteEngine)
    val storageAnalyzer: StorageAnalyzer = StorageAnalyzer(rootManager)
    val batteryManager: BatteryManager = BatteryManager(appContext, rootManager)
    val systemInfoManager: SystemInfoManager = SystemInfoManager(
        context = appContext,
        rootManager = rootManager,
        storageAnalyzer = storageAnalyzer,
        batteryManager = batteryManager
    )
    val storageForecasterEngine: StorageForecasterEngine = StorageForecasterEngine(
        context = appContext,
        rootManager = rootManager,
        repository = repository,
        nonRootEngine = nonRootEngine
    )
    val visualAndContextEngine: VisualAndContextEngine = VisualAndContextEngine(
        context = appContext,
        rootManager = rootManager,
        nonRootEngine = nonRootEngine,
        secureDeleteEngine = secureDeleteEngine
    )
    val crashAnalyzerEngine: CrashAnalyzerEngine = CrashAnalyzerEngine(
        context = appContext,
        rootManager = rootManager
    )
    val mediaReclaimerEngine: MediaReclaimerEngine = MediaReclaimerEngine(
        context = appContext,
        rootManager = rootManager
    )
    val preLaunchEngine: PreLaunchEngine = PreLaunchEngine(
        context = appContext,
        rootManager = rootManager,
        batteryManager = batteryManager
    )
    val automationManager: AutomationManager = AutomationManager(appContext, repository)

    companion object {
        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer {
            return instance ?: synchronized(this) {
                instance ?: AppContainer(context).also { instance = it }
            }
        }
    }
}
