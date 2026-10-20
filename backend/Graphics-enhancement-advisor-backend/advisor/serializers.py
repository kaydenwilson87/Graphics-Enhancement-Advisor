from rest_framework import serializers
from django.contrib.auth import get_user_model
from django.contrib.auth.password_validation import validate_password

from .models import Compatibility, Game, GPU, ResearchEvidence, SavedComparison, Technology


class GPUSerializer(serializers.ModelSerializer):
    supported_technologies = serializers.SerializerMethodField()

    class Meta:
        model = GPU
        fields = "__all__"

    def get_supported_technologies(self, obj):
        return list(obj.compatibilities.filter(status="supported", technology__is_active=True)
                    .values_list("technology__key", flat=True).distinct())


class GameSerializer(serializers.ModelSerializer):
    supported_technologies = serializers.SerializerMethodField()

    class Meta:
        model = Game
        fields = "__all__"

    def get_supported_technologies(self, obj):
        return list(obj.compatibilities.filter(status="supported", technology__is_active=True)
                    .values_list("technology__key", flat=True).distinct())


class TechnologySerializer(serializers.ModelSerializer):
    class Meta:
        model = Technology
        fields = "__all__"


class CompatibilitySerializer(serializers.ModelSerializer):
    gpu_name = serializers.CharField(source="gpu.name", read_only=True)
    game_name = serializers.CharField(source="game.name", read_only=True)
    technology_name = serializers.CharField(source="technology.name", read_only=True)
    technology_key = serializers.CharField(source="technology.key", read_only=True)

    class Meta:
        model = Compatibility
        fields = "__all__"


class ResearchEvidenceSerializer(serializers.ModelSerializer):
    technology_name = serializers.CharField(source="technology.name", read_only=True)
    game_name = serializers.CharField(source="game.name", read_only=True)
    gpu_name = serializers.CharField(source="gpu.name", read_only=True)

    class Meta:
        model = ResearchEvidence
        fields = "__all__"


class RecommendationRequestSerializer(serializers.Serializer):
    gpu_id = serializers.IntegerField()
    game_id = serializers.IntegerField()
    resolution = serializers.ChoiceField(choices=Compatibility.Resolution.choices)
    preference = serializers.ChoiceField(choices=ResearchEvidence.Preference.choices, default="balanced")


class AccountRegistrationSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(write_only=True, min_length=10, max_length=128, trim_whitespace=False)

    def validate_email(self, value):
        value = value.strip().lower()
        User = get_user_model()
        if User.objects.filter(email__iexact=value).exists() or User.objects.filter(username__iexact=value).exists():
            raise serializers.ValidationError("An account with this email already exists.")
        return value

    def validate(self, attrs):
        User = get_user_model()
        validate_password(attrs["password"], user=User(username=attrs["email"], email=attrs["email"]))
        return attrs

    def create(self, validated_data):
        User = get_user_model()
        return User.objects.create_user(
            username=validated_data["email"],
            email=validated_data["email"],
            password=validated_data["password"],
        )

    def to_representation(self, instance):
        return {"id": instance.pk, "email": instance.email}


class SavedComparisonSerializer(serializers.ModelSerializer):
    class Meta:
        model = SavedComparison
        fields = ["id", "gpu_name", "game_name", "resolution", "preference",
                  "recommendation", "reason", "note", "created_at"]
        read_only_fields = ["id", "created_at"]
