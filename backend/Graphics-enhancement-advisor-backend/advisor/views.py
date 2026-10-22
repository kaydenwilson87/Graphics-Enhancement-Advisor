from django.conf import settings
from django.db import transaction
from django.db.models import F
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import generics, viewsets
from rest_framework.exceptions import APIException, ValidationError
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework import status

from .ai_explanations import AIExplanationError, explain_recommendation
from .compatibility_sync import RESOLUTIONS, sync_game_compatibility
from .models import Compatibility, Game, GPU, GlobalAIUsage, ResearchEvidence, SavedComparison, Technology, UserAIUsage
from .permissions import ReadOnlyOrStaff
from .recommendations import recommend
from .serializers import (
    CompatibilitySerializer,
    AccountRegistrationSerializer,
    GameSerializer,
    GPUSerializer,
    RecommendationRequestSerializer,
    ResearchEvidenceSerializer,
    SavedComparisonSerializer,
    TechnologySerializer,
)


class CatalogViewSet(viewsets.ModelViewSet):
    permission_classes = [ReadOnlyOrStaff]


class GPUViewSet(CatalogViewSet):
    serializer_class = GPUSerializer
    queryset = GPU.objects.filter(is_active=True)
    search_fields = ["name", "manufacturer", "architecture"]
    ordering_fields = ["name", "manufacturer"]
    filterset_fields = ["manufacturer"]

    def perform_create(self, serializer):
        gpu = serializer.save()
        _prepare_compatibility_suggestions(gpus=[gpu], games=Game.objects.filter(is_active=True))


class GameViewSet(CatalogViewSet):
    serializer_class = GameSerializer
    queryset = Game.objects.filter(is_active=True)
    search_fields = ["name", "developer", "engine"]
    ordering_fields = ["name", "release_year"]

    def perform_create(self, serializer):
        game = serializer.save()
        _prepare_compatibility_suggestions(gpus=GPU.objects.filter(is_active=True), games=[game])


def _prepare_compatibility_suggestions(*, gpus, games):
    """Create source-backed, pending proposals after a staff catalog addition."""
    for game in games:
        for gpu in gpus:
            for resolution in RESOLUTIONS:
                sync_game_compatibility(game=game, gpu=gpu, resolution=resolution)


class TechnologyViewSet(CatalogViewSet):
    serializer_class = TechnologySerializer
    queryset = Technology.objects.filter(is_active=True)


class CompatibilityViewSet(CatalogViewSet):
    serializer_class = CompatibilitySerializer
    queryset = Compatibility.objects.select_related("gpu", "game", "technology").filter(technology__is_active=True)
    filterset_fields = ["gpu", "game", "technology", "resolution", "status"]


class ResearchEvidenceViewSet(CatalogViewSet):
    serializer_class = ResearchEvidenceSerializer
    queryset = ResearchEvidence.objects.select_related("technology", "game", "gpu").filter(is_active=True, technology__is_active=True)
    filterset_fields = ["technology", "game", "gpu", "resolution", "preference"]
    search_fields = ["source_title", "publisher", "findings"]


class RecommendationView(generics.GenericAPIView):
    serializer_class = RecommendationRequestSerializer

    def post(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        values = serializer.validated_data
        gpu = get_object_or_404(GPU, pk=values["gpu_id"], is_active=True)
        game = get_object_or_404(Game, pk=values["game_id"], is_active=True)
        # Refresh the selected pair from the cached public catalog. A failed
        # lookup is non-fatal: recommend() will continue using stored data.
        sync_result = sync_game_compatibility(gpu=gpu, game=game, resolution=values["resolution"])
        try:
            result = recommend(gpu=gpu, game=game, resolution=values["resolution"], preference=values["preference"])
        except (TypeError, ValueError) as exc:
            raise ValidationError({"detail": "The recommendation could not be generated from the stored data."}) from exc
        if not sync_result["synced"]:
            result["limitations"].append(
                "Automatic compatibility lookup is temporarily unavailable; no new source claims were added."
            )
        result["compatibility_source"] = {
            "url": sync_result["source_url"],
            "synced": sync_result["synced"],
            "game_found": sync_result["game_found"],
        }
        return Response(result)


class AccountRegistrationView(generics.GenericAPIView):
    serializer_class = AccountRegistrationSerializer
    permission_classes = [AllowAny]
    throttle_scope = "registration"

    def post(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        return Response(AccountRegistrationSerializer(user).data, status=status.HTTP_201_CREATED)


class DailyAIQuotaExceeded(APIException):
    status_code = status.HTTP_429_TOO_MANY_REQUESTS
    default_detail = "The daily AI explanation limit has been reached. Try again tomorrow."
    default_code = "daily_ai_limit"


def reserve_ai_request(user):
    """Atomically reserve both per-user and app-wide daily request budget."""
    today = timezone.localdate()
    user_limit = max(0, settings.GROQ_DAILY_LIMIT_PER_USER)
    global_limit = max(0, settings.GROQ_DAILY_LIMIT_GLOBAL)
    with transaction.atomic():
        global_row, _ = GlobalAIUsage.objects.get_or_create(date=today)
        global_row = GlobalAIUsage.objects.select_for_update().get(pk=global_row.pk)
        if global_row.request_count >= global_limit:
            raise DailyAIQuotaExceeded("The app's shared daily AI limit has been reached.")

        user_row, _ = UserAIUsage.objects.get_or_create(user=user, date=today)
        user_row = UserAIUsage.objects.select_for_update().get(pk=user_row.pk)
        if user_row.request_count >= user_limit:
            raise DailyAIQuotaExceeded()

        global_row.request_count = F("request_count") + 1
        user_row.request_count = F("request_count") + 1
        global_row.save(update_fields=["request_count"])
        user_row.save(update_fields=["request_count"])
        global_row.refresh_from_db(fields=["request_count"])
        user_row.refresh_from_db(fields=["request_count"])
    return user_row.request_count, max(0, user_limit - user_row.request_count)


class RecommendationExplanationView(generics.GenericAPIView):
    serializer_class = RecommendationRequestSerializer
    permission_classes = [IsAuthenticated]
    throttle_scope = "ai_explanation"

    def post(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        values = serializer.validated_data
        gpu = get_object_or_404(GPU, pk=values["gpu_id"], is_active=True)
        game = get_object_or_404(Game, pk=values["game_id"], is_active=True)
        result = recommend(gpu=gpu, game=game, resolution=values["resolution"], preference=values["preference"])

        if not result["recommendation"]:
            result["explanation"] = result["reason"]
            result["ai"] = {"used": False, "status": "insufficient_evidence"}
            return Response(result)

        if not settings.GROQ_API_KEY:
            result["explanation"] = result["reason"]
            result["ai"] = {"used": False, "status": "not_configured"}
            return Response(result)

        _, remaining = reserve_ai_request(request.user)
        try:
            result["explanation"] = explain_recommendation(result)
            result["ai"] = {"used": True, "provider": "Groq", "model": settings.GROQ_MODEL,
                            "daily_requests_remaining": remaining}
        except AIExplanationError:
            result["explanation"] = result["reason"]
            result["ai"] = {"used": False, "status": "provider_unavailable",
                            "daily_requests_remaining": remaining}
        return Response(result)


class CurrentAccountView(generics.GenericAPIView):
    """Return the authenticated account summary used by native clients."""
    permission_classes = [IsAuthenticated]

    def get(self, request, *args, **kwargs):
        return Response({
            "id": request.user.pk,
            "email": request.user.email,
            "name": request.user.get_full_name() or request.user.email,
            "is_staff": request.user.is_staff,
        })


class SavedComparisonViewSet(viewsets.ModelViewSet):
    """Each authenticated user can only view and change their own saved comparisons."""
    serializer_class = SavedComparisonSerializer
    permission_classes = [IsAuthenticated]

    def get_queryset(self):
        return SavedComparison.objects.filter(user=self.request.user)

    def perform_create(self, serializer):
        serializer.save(user=self.request.user)
