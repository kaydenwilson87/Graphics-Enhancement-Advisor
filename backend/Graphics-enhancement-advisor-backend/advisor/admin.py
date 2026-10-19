from django.contrib import admin

from .models import Compatibility, Game, GPU, ResearchEvidence, Technology


@admin.register(GPU)
class GPUAdmin(admin.ModelAdmin):
    list_display = ("name", "manufacturer", "architecture", "is_active")
    list_filter = ("manufacturer", "is_active")
    search_fields = ("name", "architecture")


@admin.register(Game)
class GameAdmin(admin.ModelAdmin):
    list_display = ("name", "developer", "release_year", "is_active")
    list_filter = ("is_active", "release_year")
    search_fields = ("name", "developer", "engine")


@admin.register(Technology)
class TechnologyAdmin(admin.ModelAdmin):
    list_display = ("name", "key", "manufacturer", "is_active")
    list_filter = ("is_active",)
    search_fields = ("name", "manufacturer")


@admin.register(Compatibility)
class CompatibilityAdmin(admin.ModelAdmin):
    list_display = ("gpu", "game", "technology", "resolution", "status")
    list_filter = ("technology", "resolution", "status")
    search_fields = ("gpu__name", "game__name", "technology__name")


@admin.register(ResearchEvidence)
class ResearchEvidenceAdmin(admin.ModelAdmin):
    list_display = ("source_title", "technology", "game", "gpu", "resolution", "confidence", "is_active")
    list_filter = ("technology", "preference", "confidence", "is_active")
    search_fields = ("source_title", "publisher", "findings")
