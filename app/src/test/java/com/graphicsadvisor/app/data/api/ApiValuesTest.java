package com.graphicsadvisor.app.data.api;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ApiValuesTest {
    @Test
    public void fourKIsSentToTheServerAs2160p() {
        assertEquals("2160p", ApiValues.resolutionKey("4K"));
        assertEquals("1080p", ApiValues.resolutionKey("1080p"));
        assertEquals("1440p", ApiValues.resolutionKey("1440p"));
    }

    @Test
    public void serverResolutionIsShownToTheUserAs4K() {
        assertEquals("4K", ApiValues.resolutionLabel("2160p"));
        assertEquals("1440p", ApiValues.resolutionLabel("1440p"));
    }

    @Test
    public void preferenceLabelsMapToServerKeys() {
        assertEquals("performance", ApiValues.preferenceKey("Performance"));
        assertEquals("image_quality", ApiValues.preferenceKey("Image quality"));
        assertEquals("balanced", ApiValues.preferenceKey("Balanced"));
    }

    @Test
    public void unknownPreferenceFallsBackToBalanced() {
        assertEquals("balanced", ApiValues.preferenceKey("Something else"));
        assertEquals("Balanced", ApiValues.preferenceLabel("unknown"));
    }

    @Test
    public void serverPreferenceKeysMapBackToLabels() {
        assertEquals("Performance", ApiValues.preferenceLabel("performance"));
        assertEquals("Image quality", ApiValues.preferenceLabel("image_quality"));
    }
}
