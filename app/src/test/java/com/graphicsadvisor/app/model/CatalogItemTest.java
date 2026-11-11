package com.graphicsadvisor.app.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CatalogItemTest {
    @Test
    public void gpuSubtitleJoinsManufacturerAndArchitecture() {
        CatalogItem gpu = new CatalogItem();
        gpu.maker = "NVIDIA";
        gpu.detail = "Ada Lovelace";
        assertEquals("NVIDIA • Ada Lovelace", gpu.subtitle());
    }

    @Test
    public void gameSubtitleIncludesTheReleaseYear() {
        CatalogItem game = new CatalogItem();
        game.maker = "CD PROJEKT RED";
        game.detail = "REDengine";
        game.releaseYear = 2020;
        assertEquals("CD PROJEKT RED • REDengine • 2020", game.subtitle());
    }

    @Test
    public void emptyFieldsAreSkipped() {
        CatalogItem game = new CatalogItem();
        game.releaseYear = 2023;
        assertEquals("2023", game.subtitle());
        assertEquals("", new CatalogItem().subtitle());
    }
}
