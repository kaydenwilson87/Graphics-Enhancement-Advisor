from django.contrib import admin
from django.db import connection
from django.http import JsonResponse
from django.urls import include, path
from rest_framework.routers import DefaultRouter
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

from advisor.views import (
    AccountRegistrationView,
    CompatibilityViewSet,
    GameViewSet,
    GPUViewSet,
    RecommendationView,
    RecommendationExplanationView,
    ResearchEvidenceViewSet,
    TechnologyViewSet,
)

router = DefaultRouter()
router.register("gpus", GPUViewSet)
router.register("games", GameViewSet)
router.register("technologies", TechnologyViewSet)
router.register("compatibility", CompatibilityViewSet)
router.register("evidence", ResearchEvidenceViewSet)


def health_check(request):
    try:
        connection.ensure_connection()
    except Exception:
        return JsonResponse({"status": "unavailable", "database": "unavailable"}, status=503)
    return JsonResponse({"status": "ok", "database": "ok"})


urlpatterns = [
    path("health/", health_check, name="health-check"),
    path("admin/", admin.site.urls),
    path("api/v1/auth/token/", TokenObtainPairView.as_view(), name="token_obtain_pair"),
    path("api/v1/auth/token/refresh/", TokenRefreshView.as_view(), name="token_refresh"),
    path("api/v1/auth/register/", AccountRegistrationView.as_view(), name="register"),
    path("api/v1/recommendations/", RecommendationView.as_view(), name="recommendation"),
    path("api/v1/recommendations/explain/", RecommendationExplanationView.as_view(), name="recommendation-explanation"),
    path("api/v1/", include(router.urls)),
]
