from django.db import migrations, models


class Migration(migrations.Migration):
    dependencies = [("advisor", "0004_compatibility_is_verified")]

    operations = [
        migrations.AddField(
            model_name="compatibility",
            name="is_rejected",
            field=models.BooleanField(default=False),
        ),
    ]
