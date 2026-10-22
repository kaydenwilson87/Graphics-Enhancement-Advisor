from django.db import migrations, models


def mark_automatic_suggestions_unverified(apps, schema_editor):
    Compatibility = apps.get_model("advisor", "Compatibility")
    Compatibility.objects.filter(
        limitations__startswith="Auto-synced from PCGamingWiki:"
    ).update(is_verified=False)


class Migration(migrations.Migration):
    dependencies = [("advisor", "0003_savedcomparison")]

    operations = [
        migrations.AddField(
            model_name="compatibility",
            name="is_verified",
            field=models.BooleanField(default=True),
        ),
        migrations.RunPython(mark_automatic_suggestions_unverified, migrations.RunPython.noop),
    ]
