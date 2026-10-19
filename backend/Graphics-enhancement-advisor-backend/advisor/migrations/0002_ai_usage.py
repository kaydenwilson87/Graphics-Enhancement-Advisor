from django.conf import settings
from django.db import migrations, models
import django.db.models.deletion


class Migration(migrations.Migration):
    dependencies = [
        migrations.swappable_dependency(settings.AUTH_USER_MODEL),
        ("advisor", "0001_initial"),
    ]

    operations = [
        migrations.CreateModel(
            name="GlobalAIUsage",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("date", models.DateField(unique=True)),
                ("request_count", models.PositiveSmallIntegerField(default=0)),
            ],
        ),
        migrations.CreateModel(
            name="UserAIUsage",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("date", models.DateField()),
                ("request_count", models.PositiveSmallIntegerField(default=0)),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="ai_usage", to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.AddConstraint(
            model_name="useraiusage",
            constraint=models.UniqueConstraint(fields=("user", "date"), name="unique_ai_usage_per_user_day"),
        ),
    ]
