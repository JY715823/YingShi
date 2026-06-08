package com.example.yingshi.feature.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaShareManagerTest {

    @Test
    fun smartShareUsesZipOnlyAtThreshold() {
        val preferences = SharePreferenceState(
            deliveryPreference = ShareDeliveryPreference.SMART,
            zipThreshold = 10,
        )

        assertFalse(preferences.shouldZip(9))
        assertTrue(preferences.shouldZip(10))
    }

    @Test
    fun directAndZipModesOverrideThreshold() {
        val directPreferences = SharePreferenceState(
            deliveryPreference = ShareDeliveryPreference.DIRECT_FILES,
            zipThreshold = 2,
        )
        val zipPreferences = SharePreferenceState(
            deliveryPreference = ShareDeliveryPreference.ZIP_PACKAGE,
            zipThreshold = 99,
        )

        assertFalse(directPreferences.shouldZip(20))
        assertTrue(zipPreferences.shouldZip(1))
    }

    @Test
    fun zippedShareNoticeIncludesSkippedCount() {
        val result = MediaShareLaunchResult.Success(
            mediaCount = 12,
            skippedCount = 2,
            zipped = true,
        )

        assertEquals(
            "已准备 12 项媒体的压缩包，系统分享已打开，另跳过 2 项暂不可分享的媒体",
            result.toNoticeMessage(),
        )
    }
}
