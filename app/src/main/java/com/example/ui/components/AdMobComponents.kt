package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceHighlight
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ViralAmber
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

object AdMobConfig {
    const val APP_ID = "ca-app-pub-1374968715711516~1495134749"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-1374968715711516/7584980500"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1374968715711516/1826307230"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-1374968715711516/5276782879"

    // Google Official Test IDs for debug / preview
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

    val activeAppId: String
        get() = if (BuildConfig.DEBUG) TEST_APP_ID else BuildConfig.ADMOB_APP_ID

    val activeBannerUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else BuildConfig.ADMOB_BANNER_AD_UNIT_ID

    val activeInterstitialUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_AD_UNIT_ID else BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID

    val activeAppOpenUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_APP_OPEN_AD_UNIT_ID else BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID
}

enum class FullScreenAdType {
    APP_OPEN,
    INTERSTITIAL
}

data class FullScreenAdDialogState(
    val type: FullScreenAdType,
    val adUnitId: String,
    val onClosed: () -> Unit
)

object AdMobManager {
    private const val TAG = "AdMobManager"
    private const val PREFS_NAME = "clipforge_admob_prefs"
    private const val KEY_HAS_COMPLETED_FIRST_SESSION = "has_completed_first_session"
    const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L // 3 minutes minimum between interstitials

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAppOpen = false
    private var isShowingAppOpen = false
    private var appOpenLoadTimeMs: Long = 0L
    private var hasShownInitialAppOpen = false

    private val isMobileAdsInitialized = AtomicBoolean(false)

    @Volatile
    private var isExportActive: Boolean = false
    @Volatile
    private var isProcessingActive: Boolean = false
    @Volatile
    private var isFirstSessionLaunch: Boolean = false
    @Volatile
    private var lastInterstitialShownMs: Long = 0L

    private val _isConsentGranted = MutableStateFlow(false)
    val isConsentGranted: StateFlow<Boolean> = _isConsentGranted.asStateFlow()

    private val _activeFullScreenAd = MutableStateFlow<FullScreenAdDialogState?>(null)
    val activeFullScreenAd: StateFlow<FullScreenAdDialogState?> = _activeFullScreenAd.asStateFlow()

    fun setExportActive(active: Boolean) {
        isExportActive = active
        if (active) {
            _activeFullScreenAd.value = null
            isShowingAppOpen = false
        }
    }

    fun isExportCurrentlyActive(): Boolean = isExportActive

    fun setProcessingActive(active: Boolean) {
        isProcessingActive = active
        if (active) {
            _activeFullScreenAd.value = null
            isShowingAppOpen = false
        }
    }

    fun isProcessingCurrentlyActive(): Boolean = isProcessingActive

    fun isAnyJobActive(): Boolean = isExportActive || isProcessingActive

    fun isFirstLaunchSession(): Boolean = isFirstSessionLaunch

    fun canShowInterstitialNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!canRequestAds() || isAnyJobActive() || isFirstSessionLaunch) return false
        if (lastInterstitialShownMs == 0L) return true
        return (nowMs - lastInterstitialShownMs) >= INTERSTITIAL_COOLDOWN_MS
    }

    fun canRequestAds(): Boolean = _isConsentGranted.value

    fun isVirtualEmulatorEnvironment(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        val hardware = Build.HARDWARE.lowercase()
        val hasRenderNode = try {
            File("/dev/dri/renderD128").exists()
        } catch (_: Exception) {
            false
        }
        return !hasRenderNode ||
            fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            product.contains("sdk") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu") ||
            hardware.contains("cuttlefish")
    }

    fun dismissFullScreenAdDialog() {
        val current = _activeFullScreenAd.value
        _activeFullScreenAd.value = null
        isShowingAppOpen = false
        current?.onClosed?.invoke()
    }

    fun ensureWebViewCacheDirectories(context: Context) {
        try {
            val baseCache = context.cacheDir
            listOf(
                "WebView/Default/HTTP Cache/Code Cache/js",
                "WebView/Default/HTTP Cache/Code Cache/wasm"
            ).forEach { relPath ->
                File(baseCache, relPath).mkdirs()
            }
        } catch (_: Exception) {}
    }

    fun initialize(context: Context, initialActivity: Activity? = null) {
        ensureWebViewCacheDirectories(context)
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val completedBefore = prefs.getBoolean(KEY_HAS_COMPLETED_FIRST_SESSION, false)
            if (!completedBefore) {
                isFirstSessionLaunch = true
                prefs.edit().putBoolean(KEY_HAS_COMPLETED_FIRST_SESSION, true).apply()
            } else {
                isFirstSessionLaunch = false
            }
        } catch (_: Exception) {}

        if (initialActivity == null) return

        if (isVirtualEmulatorEnvironment()) {
            onUmpConsentResolved(context, initialActivity, granted = true)
            return
        }

        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        val consentInformation = try {
            UserMessagingPlatform.getConsentInformation(context)
        } catch (e: Exception) {
            Log.w(TAG, "UMP consent init: ${e.message}")
            null
        }

        if (consentInformation == null) {
            onUmpConsentResolved(context, initialActivity, granted = true)
            return
        }

        consentInformation.requestConsentInfoUpdate(
            initialActivity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(initialActivity) { _ ->
                    val allowed = try {
                        consentInformation.canRequestAds()
                    } catch (_: Exception) {
                        true
                    }
                    onUmpConsentResolved(context, initialActivity, granted = allowed)
                }
            },
            { _ ->
                val allowed = try {
                    consentInformation.canRequestAds()
                } catch (_: Exception) {
                    true
                }
                onUmpConsentResolved(context, initialActivity, granted = allowed)
            }
        )

        if (consentInformation.canRequestAds()) {
            onUmpConsentResolved(context, initialActivity, granted = true)
        }
    }

    private fun onUmpConsentResolved(context: Context, initialActivity: Activity?, granted: Boolean) {
        _isConsentGranted.value = granted
        if (!granted) return

        if (isMobileAdsInitialized.getAndSet(true)) {
            if (initialActivity != null && !hasShownInitialAppOpen && !isAnyJobActive() && !isFirstSessionLaunch) {
                hasShownInitialAppOpen = true
                showAppOpenAdIfAvailable(initialActivity)
            }
            return
        }

        if (isVirtualEmulatorEnvironment()) {
            return
        }

        try {
            val requestConfig = RequestConfiguration.Builder()
                .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                .build()
            MobileAds.setRequestConfiguration(requestConfig)
        } catch (e: Exception) {
            Log.w(TAG, "RequestConfiguration: ${e.message}")
        }

        try {
            MobileAds.initialize(context) { _ ->
                loadAppOpenAd(context, AdMobConfig.activeAppOpenUnitId, autoShowActivity = initialActivity)
                loadInterstitial(context, AdMobConfig.activeInterstitialUnitId)
            }
        } catch (_: Exception) {}
    }

    private fun isAppOpenAdAvailable(): Boolean {
        if (!canRequestAds() || isAnyJobActive() || isFirstSessionLaunch) return false
        if (isVirtualEmulatorEnvironment()) return true
        if (appOpenAd == null) return false
        val dateDifference = Date().time - appOpenLoadTimeMs
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < (numMilliSecondsPerHour * 4L)
    }

    fun loadAppOpenAd(
        context: Context,
        adUnitId: String = AdMobConfig.activeAppOpenUnitId,
        forceReload: Boolean = false,
        autoShowActivity: Activity? = null
    ) {
        if (!canRequestAds() || isAnyJobActive()) return
        val unitToUse = if (BuildConfig.DEBUG) AdMobConfig.TEST_APP_OPEN_AD_UNIT_ID else adUnitId
        if (isVirtualEmulatorEnvironment()) return

        if (forceReload) {
            appOpenAd = null
            isLoadingAppOpen = false
        }
        if (isLoadingAppOpen || isAppOpenAdAvailable()) return
        isLoadingAppOpen = true

        val request = AdRequest.Builder().build()
        try {
            AppOpenAd.load(
                context.applicationContext,
                unitToUse,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd = ad
                        isLoadingAppOpen = false
                        appOpenLoadTimeMs = Date().time
                        if (autoShowActivity != null && !hasShownInitialAppOpen && !isAnyJobActive() && !isFirstSessionLaunch) {
                            hasShownInitialAppOpen = true
                            showAppOpenAdIfAvailable(autoShowActivity)
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        appOpenAd = null
                        isLoadingAppOpen = false
                    }
                }
            )
        } catch (_: Exception) {
            isLoadingAppOpen = false
        }
    }

    fun showAppOpenAdIfAvailable(
        activity: Activity?,
        onAdComplete: () -> Unit = {}
    ) {
        if (activity == null || !canRequestAds() || isAnyJobActive() || isFirstSessionLaunch || isShowingAppOpen || _activeFullScreenAd.value != null) {
            onAdComplete()
            return
        }
        if (isVirtualEmulatorEnvironment()) {
            onAdComplete()
            return
        }
        if (!isAppOpenAdAvailable()) {
            loadAppOpenAd(activity)
            onAdComplete()
            return
        }
        val ad = appOpenAd ?: run {
            onAdComplete()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAppOpen = false
                loadAppOpenAd(activity)
                onAdComplete()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAppOpen = false
                loadAppOpenAd(activity)
                onAdComplete()
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAppOpen = true
            }
        }
        isShowingAppOpen = true
        ad.show(activity)
    }

    fun loadInterstitial(
        context: Context,
        adUnitId: String = AdMobConfig.activeInterstitialUnitId,
        forceReload: Boolean = false
    ) {
        if (!canRequestAds() || isAnyJobActive()) return
        val unitToUse = if (BuildConfig.DEBUG) AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID else adUnitId
        if (isVirtualEmulatorEnvironment()) return

        if (forceReload) {
            interstitialAd = null
            isLoadingInterstitial = false
        }
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true

        val adRequest = AdRequest.Builder().build()
        try {
            InterstitialAd.load(
                context.applicationContext,
                unitToUse,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                    }
                }
            )
        } catch (_: Exception) {
            isLoadingInterstitial = false
        }
    }

    fun showInterstitial(
        activity: Activity?,
        showFeedbackToast: Boolean = false,
        onAdClosed: () -> Unit
    ) {
        val nowMs = System.currentTimeMillis()
        if (!canShowInterstitialNow(nowMs)) {
            onAdClosed()
            return
        }
        if (isVirtualEmulatorEnvironment()) {
            lastInterstitialShownMs = nowMs
            _activeFullScreenAd.value = FullScreenAdDialogState(
                type = FullScreenAdType.INTERSTITIAL,
                adUnitId = AdMobConfig.activeInterstitialUnitId,
                onClosed = onAdClosed
            )
            return
        }
        val ad = interstitialAd
        if (activity != null && ad != null) {
            lastInterstitialShownMs = nowMs
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onAdClosed()
                }

                override fun onAdShowedFullScreenContent() {}
            }
            ad.show(activity)
        } else {
            if (activity != null) {
                loadInterstitial(activity, AdMobConfig.activeInterstitialUnitId, forceReload = true)
            }
            onAdClosed()
        }
    }

    fun isAdLoaded(): Boolean = canRequestAds() && !isAnyJobActive() && (interstitialAd != null || isVirtualEmulatorEnvironment())
}

@Composable
fun AdMobFullScreenOverlayHost() {
    val activeAd by AdMobManager.activeFullScreenAd.collectAsStateWithLifecycle()
    val adState = activeAd ?: return
    if (AdMobManager.isAnyJobActive() || !AdMobManager.canRequestAds()) return

    val isAppOpen = adState.type == FullScreenAdType.APP_OPEN

    Dialog(
        onDismissRequest = {
            SoundEffectManager.playButtonTap()
            AdMobManager.dismissFullScreenAdDialog()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .padding(20.dp)
                .testTag(if (isAppOpen) "admob_app_open_dialog" else "admob_interstitial_dialog"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.5.dp,
                        if (isAppOpen) NeonCyan else ElectricViolet,
                        RoundedCornerShape(22.dp)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAppOpen) NeonCyan.copy(alpha = 0.2f) else ElectricViolet.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "SPONSORED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isAppOpen) NeonCyan else ViralAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            onClick = {
                                SoundEffectManager.playButtonTap()
                                AdMobManager.dismissFullScreenAdDialog()
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = DarkSurfaceElevated,
                            modifier = Modifier
                                .border(1.dp, DarkSurfaceHighlight, RoundedCornerShape(20.dp))
                                .testTag("btn_dismiss_fullscreen_ad")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Close",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF3B0764),
                                        Color(0xFF1E1B4B),
                                        Color(0xFF0F172A)
                                    )
                                )
                            )
                            .border(1.dp, ElectricViolet.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(ElectricViolet, NeonCyan))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAppOpen) Icons.Default.AutoAwesome else Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = "ClipForge Studio",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "High-quality 1080p rendering, animated captions, and smart viral moment detection.",
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    Button(
                        onClick = {
                            SoundEffectManager.playButtonTap()
                            AdMobManager.dismissFullScreenAdDialog()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    adUnitId: String = AdMobConfig.activeBannerUnitId
) {
    val isConsentGranted by AdMobManager.isConsentGranted.collectAsStateWithLifecycle()
    if (!isConsentGranted || AdMobManager.isAnyJobActive()) {
        return
    }

    val resolvedUnitId = if (BuildConfig.DEBUG) AdMobConfig.TEST_BANNER_AD_UNIT_ID else adUnitId
    val isEmulator = remember { AdMobManager.isVirtualEmulatorEnvironment() }
    var isAdLoaded by remember { mutableStateOf(false) }
    var activeAdView by remember { mutableStateOf<AdView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                activeAdView?.destroy()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceElevated)
            .border(
                1.dp,
                if (isAdLoaded || isEmulator) NeonCyan.copy(alpha = 0.35f) else DarkSurfaceHighlight,
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .testTag("admob_banner_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            ElectricViolet.copy(alpha = 0.2f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "AD",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!isEmulator) {
                    key(resolvedUnitId) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            factory = { ctx ->
                                AdView(ctx).apply {
                                    activeAdView = this
                                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                    setAdSize(AdSize.BANNER)
                                    this.adUnitId = resolvedUnitId
                                    adListener = object : AdListener() {
                                        override fun onAdLoaded() {
                                            isAdLoaded = true
                                        }
                                        override fun onAdFailedToLoad(error: LoadAdError) {
                                            isAdLoaded = false
                                        }
                                    }
                                    try {
                                        loadAd(AdRequest.Builder().build())
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                }

                if (!isAdLoaded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF1E1B4B), Color(0xFF0F172A), Color(0xFF164E63))
                                    )
                                )
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ClipForge Shorts Studio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Sponsored",
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
