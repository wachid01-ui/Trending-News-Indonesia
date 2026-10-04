package id.trendingnews.indonesia.ads

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

object AdConstants {
    // Google Official Test Ad Unit IDs (Gunakan ID ini selama tahap pengujian)
    const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    // Frekuensi penayangan interstitial: Muncul setiap user membuka 3 artikel
    const val INTERSTITIAL_ARTICLE_INTERVAL = 3
}

object AdManager {
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var articleOpenCounter = 0

    fun initialize(context: Context) {
        MobileAds.initialize(context) {}
        loadInterstitial(context)
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AdConstants.INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                }
            },
        )
    }

    fun onArticleOpened(activity: Activity, onFinished: () -> Unit) {
        articleOpenCounter++
        if (articleOpenCounter >= AdConstants.INTERSTITIAL_ARTICLE_INTERVAL && interstitialAd != null) {
            articleOpenCounter = 0
            val ad = interstitialAd
            ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    interstitialAd = null
                    loadInterstitial(activity)
                    onFinished()
                }
            }
            ad?.show(activity)
        } else {
            onFinished()
        }
    }
}

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdConstants.BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary.toArgb()

    DisposableEffect(Unit) {
        val adLoader = AdLoader.Builder(context, AdConstants.NATIVE_AD_UNIT_ID)
            .forNativeAd { ad ->
                nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
        }
    }

    if (nativeAd != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                factory = { ctx ->
                    val nativeAdView = NativeAdView(ctx)
                    val container = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }

                    // Top row: Ad badge + Headline
                    val topRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                    }

                    val adBadge = TextView(ctx).apply {
                        text = "IKLAN"
                        textSize = 10f
                        setPadding(14, 4, 14, 4)
                        setTextColor(onPrimaryColor)
                        setBackgroundColor(primaryColor)
                    }

                    val headlineView = TextView(ctx).apply {
                        textSize = 15f
                        setTextColor(onSurfaceColor)
                        setPadding(16, 0, 0, 0)
                        maxLines = 1
                    }

                    topRow.addView(adBadge)
                    topRow.addView(headlineView)
                    container.addView(topRow)

                    // Body text
                    val bodyView = TextView(ctx).apply {
                        textSize = 13f
                        setTextColor(onSurfaceVariantColor)
                        setPadding(0, 10, 0, 12)
                        maxLines = 2
                    }
                    container.addView(bodyView)

                    // Call to Action button
                    val ctaButton = Button(ctx).apply {
                        textSize = 14f
                        setTextColor(onPrimaryColor)
                        setBackgroundColor(primaryColor)
                    }
                    container.addView(ctaButton)

                    nativeAdView.headlineView = headlineView
                    nativeAdView.bodyView = bodyView
                    nativeAdView.callToActionView = ctaButton
                    nativeAdView.addView(container)

                    nativeAdView
                },
                update = { nativeAdView ->
                    val ad = nativeAd ?: return@AndroidView
                    (nativeAdView.headlineView as? TextView)?.text = ad.headline
                    (nativeAdView.bodyView as? TextView)?.text = ad.body ?: ""
                    (nativeAdView.callToActionView as? Button)?.text = ad.callToAction ?: "Lihat Detail"
                    nativeAdView.setNativeAd(ad)
                },
            )
        }
    }
}
