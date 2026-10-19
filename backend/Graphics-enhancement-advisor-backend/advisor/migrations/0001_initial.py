import django.core.validators
from django.db import migrations, models
import django.db.models.deletion


def add_initial_technologies(apps, schema_editor):
    Technology = apps.get_model("advisor", "Technology")
    Technology.objects.bulk_create([
        Technology(key="dlss", name="NVIDIA DLSS", manufacturer="NVIDIA"),
        Technology(key="fsr", name="AMD FSR", manufacturer="AMD"),
        Technology(key="xess", name="Intel XeSS", manufacturer="Intel"),
    ])


class Migration(migrations.Migration):
    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name="Game",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("name", models.CharField(max_length=180, unique=True)),
                ("developer", models.CharField(blank=True, max_length=160)),
                ("engine", models.CharField(blank=True, max_length=120)),
                ("release_year", models.PositiveSmallIntegerField(blank=True, null=True)),
                ("notes", models.TextField(blank=True)),
                ("is_active", models.BooleanField(default=True)),
            ],
            options={"ordering": ["name"]},
        ),
        migrations.CreateModel(
            name="GPU",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("name", models.CharField(max_length=160, unique=True)),
                ("manufacturer", models.CharField(max_length=80)),
                ("architecture", models.CharField(blank=True, max_length=120)),
                ("notes", models.TextField(blank=True)),
                ("is_active", models.BooleanField(default=True)),
            ],
            options={"ordering": ["manufacturer", "name"]},
        ),
        migrations.CreateModel(
            name="Technology",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("key", models.CharField(choices=[("dlss", "DLSS"), ("fsr", "FSR"), ("xess", "XeSS")], max_length=8, unique=True)),
                ("name", models.CharField(max_length=80)),
                ("manufacturer", models.CharField(max_length=80)),
                ("description", models.TextField(blank=True)),
                ("strengths", models.TextField(blank=True)),
                ("limitations", models.TextField(blank=True)),
                ("is_active", models.BooleanField(default=True)),
            ],
            options={"ordering": ["key"]},
        ),
        migrations.CreateModel(
            name="Compatibility",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("resolution", models.CharField(choices=[("1080p", "1920 x 1080"), ("1440p", "2560 x 1440"), ("2160p", "3840 x 2160")], max_length=8)),
                ("status", models.CharField(choices=[("supported", "Supported"), ("unsupported", "Unsupported"), ("unknown", "Unknown")], default="unknown", max_length=12)),
                ("performance_summary", models.TextField(blank=True)),
                ("image_quality_summary", models.TextField(blank=True)),
                ("latency_summary", models.TextField(blank=True)),
                ("limitations", models.TextField(blank=True)),
                ("game", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="compatibilities", to="advisor.game")),
                ("gpu", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="compatibilities", to="advisor.gpu")),
                ("technology", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="compatibilities", to="advisor.technology")),
            ],
            options={"ordering": ["technology__key", "resolution"]},
        ),
        migrations.CreateModel(
            name="ResearchEvidence",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
                ("resolution", models.CharField(blank=True, choices=[("1080p", "1920 x 1080"), ("1440p", "2560 x 1440"), ("2160p", "3840 x 2160")], max_length=8)),
                ("source_title", models.CharField(max_length=240)),
                ("source_url", models.URLField(blank=True)),
                ("publisher", models.CharField(blank=True, max_length=160)),
                ("published_at", models.DateField(blank=True, null=True)),
                ("findings", models.TextField()),
                ("limitations", models.TextField(blank=True)),
                ("preference", models.CharField(choices=[("performance", "Performance"), ("image_quality", "Image quality"), ("balanced", "Balanced")], default="balanced", max_length=16)),
                ("performance_score", models.PositiveSmallIntegerField(blank=True, null=True, validators=[django.core.validators.MinValueValidator(1), django.core.validators.MaxValueValidator(5)])),
                ("image_quality_score", models.PositiveSmallIntegerField(blank=True, null=True, validators=[django.core.validators.MinValueValidator(1), django.core.validators.MaxValueValidator(5)])),
                ("latency_score", models.PositiveSmallIntegerField(blank=True, null=True, validators=[django.core.validators.MinValueValidator(1), django.core.validators.MaxValueValidator(5)])),
                ("confidence", models.PositiveSmallIntegerField(default=3, validators=[django.core.validators.MinValueValidator(1), django.core.validators.MaxValueValidator(5)])),
                ("is_active", models.BooleanField(default=True)),
                ("game", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.CASCADE, related_name="evidence", to="advisor.game")),
                ("gpu", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.CASCADE, related_name="evidence", to="advisor.gpu")),
                ("technology", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="evidence", to="advisor.technology")),
            ],
            options={"ordering": ["-published_at", "source_title"]},
        ),
        migrations.AddConstraint(
            model_name="compatibility",
            constraint=models.UniqueConstraint(fields=("gpu", "game", "technology", "resolution"), name="unique_gpu_game_technology_resolution"),
        ),
        migrations.RunPython(add_initial_technologies, migrations.RunPython.noop),
    ]
