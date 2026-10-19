from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models
from django.conf import settings


class TimestampedModel(models.Model):
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        abstract = True


class GPU(TimestampedModel):
    name = models.CharField(max_length=160, unique=True)
    manufacturer = models.CharField(max_length=80)
    architecture = models.CharField(max_length=120, blank=True)
    notes = models.TextField(blank=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["manufacturer", "name"]

    def __str__(self):
        return self.name


class Game(TimestampedModel):
    name = models.CharField(max_length=180, unique=True)
    developer = models.CharField(max_length=160, blank=True)
    engine = models.CharField(max_length=120, blank=True)
    release_year = models.PositiveSmallIntegerField(null=True, blank=True)
    notes = models.TextField(blank=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["name"]

    def __str__(self):
        return self.name


class Technology(TimestampedModel):
    class Key(models.TextChoices):
        DLSS = "dlss", "DLSS"
        FSR = "fsr", "FSR"
        XESS = "xess", "XeSS"

    key = models.CharField(max_length=8, choices=Key.choices, unique=True)
    name = models.CharField(max_length=80)
    manufacturer = models.CharField(max_length=80)
    description = models.TextField(blank=True)
    strengths = models.TextField(blank=True)
    limitations = models.TextField(blank=True)
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["key"]

    def __str__(self):
        return self.name


class Compatibility(TimestampedModel):
    class Status(models.TextChoices):
        SUPPORTED = "supported", "Supported"
        UNSUPPORTED = "unsupported", "Unsupported"
        UNKNOWN = "unknown", "Unknown"

    class Resolution(models.TextChoices):
        P1080 = "1080p", "1920 x 1080"
        P1440 = "1440p", "2560 x 1440"
        P2160 = "2160p", "3840 x 2160"

    gpu = models.ForeignKey(GPU, on_delete=models.CASCADE, related_name="compatibilities")
    game = models.ForeignKey(Game, on_delete=models.CASCADE, related_name="compatibilities")
    technology = models.ForeignKey(Technology, on_delete=models.CASCADE, related_name="compatibilities")
    resolution = models.CharField(max_length=8, choices=Resolution.choices)
    status = models.CharField(max_length=12, choices=Status.choices, default=Status.UNKNOWN)
    performance_summary = models.TextField(blank=True)
    image_quality_summary = models.TextField(blank=True)
    latency_summary = models.TextField(blank=True)
    limitations = models.TextField(blank=True)
    class Meta:
        ordering = ["technology__key", "resolution"]
        constraints = [models.UniqueConstraint(fields=["gpu", "game", "technology", "resolution"], name="unique_gpu_game_technology_resolution")]

    def __str__(self):
        return f"{self.gpu} / {self.game} / {self.technology} / {self.resolution}"


class ResearchEvidence(TimestampedModel):
    class Preference(models.TextChoices):
        PERFORMANCE = "performance", "Performance"
        IMAGE_QUALITY = "image_quality", "Image quality"
        BALANCED = "balanced", "Balanced"

    technology = models.ForeignKey(Technology, on_delete=models.CASCADE, related_name="evidence")
    game = models.ForeignKey(Game, on_delete=models.CASCADE, related_name="evidence", null=True, blank=True)
    gpu = models.ForeignKey(GPU, on_delete=models.CASCADE, related_name="evidence", null=True, blank=True)
    resolution = models.CharField(max_length=8, choices=Compatibility.Resolution.choices, blank=True)
    source_title = models.CharField(max_length=240)
    source_url = models.URLField(blank=True)
    publisher = models.CharField(max_length=160, blank=True)
    published_at = models.DateField(null=True, blank=True)
    findings = models.TextField()
    limitations = models.TextField(blank=True)
    preference = models.CharField(max_length=16, choices=Preference.choices, default=Preference.BALANCED)
    performance_score = models.PositiveSmallIntegerField(null=True, blank=True, validators=[MinValueValidator(1), MaxValueValidator(5)])
    image_quality_score = models.PositiveSmallIntegerField(null=True, blank=True, validators=[MinValueValidator(1), MaxValueValidator(5)])
    latency_score = models.PositiveSmallIntegerField(null=True, blank=True, validators=[MinValueValidator(1), MaxValueValidator(5)])
    confidence = models.PositiveSmallIntegerField(default=3, validators=[MinValueValidator(1), MaxValueValidator(5)])
    is_active = models.BooleanField(default=True)

    class Meta:
        ordering = ["-published_at", "source_title"]

    def __str__(self):
        return self.source_title


class UserAIUsage(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="ai_usage")
    date = models.DateField()
    request_count = models.PositiveSmallIntegerField(default=0)

    class Meta:
        constraints = [models.UniqueConstraint(fields=["user", "date"], name="unique_ai_usage_per_user_day")]


class GlobalAIUsage(models.Model):
    date = models.DateField(unique=True)
    request_count = models.PositiveSmallIntegerField(default=0)
