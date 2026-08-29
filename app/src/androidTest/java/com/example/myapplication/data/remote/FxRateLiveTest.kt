package com.example.myapplication.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hits the real endpoints. Unit tests prove we parse a saved response; only this
 * proves the URL, the OkHttp path and the live response shape still agree — which
 * is what actually breaks when a provider changes its API.
 */
@RunWith(AndroidJUnit4::class)
class FxRateLiveTest {

    @Test
    fun fetchesAPlausibleLiveRate() = runBlocking {
        val rate = FxRateService.fetch()
        assertNotNull("both FX sources failed", rate)
        rate!!
        println("FX: ¥1 = A$${rate.rate} from ${rate.source}")
        // Wide band on purpose: this is a smoke test for the pipe, not an
        // assertion about the currency market.
        assertTrue("implausible rate ${rate.rate}", rate.rate in 0.15..0.30)
        assertTrue(rate.source.isNotEmpty())
        assertTrue(!rate.isFallback)
    }
}
