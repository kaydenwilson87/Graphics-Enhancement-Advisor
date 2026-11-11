package com.graphicsadvisor.app.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TechnologyTest {
    @Test
    public void findsATechnologyFromTheServerKeyIgnoringCase() {
        assertEquals(Technology.DLSS, Technology.fromKey("dlss"));
        assertEquals(Technology.FSR, Technology.fromKey("FSR"));
        assertEquals(Technology.XESS, Technology.fromKey("xess"));
    }

    @Test
    public void unknownKeyGivesNull() {
        assertNull(Technology.fromKey("not-a-technology"));
        assertNull(Technology.fromKey(null));
    }

    @Test
    public void colourIsLookedUpByDisplayLabel() {
        assertEquals(Technology.XESS.color, Technology.colorOf("XeSS"));
        assertEquals(Technology.DLSS.color, Technology.colorOf("DLSS"));
    }

    @Test
    public void unknownLabelIsBlack() {
        assertEquals(0xFF000000, Technology.colorOf("Unknown"));
    }
}
