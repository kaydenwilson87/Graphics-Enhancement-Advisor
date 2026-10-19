from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from rest_framework.test import APIClient

from .models import Compatibility, Game, GPU, GlobalAIUsage, ResearchEvidence, Technology, UserAIUsage
from .views import DailyAIQuotaExceeded, reserve_ai_request


class AdvisorAPITests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.gpu = GPU.objects.create(name="Test GPU", manufacturer="Example")
        self.game = Game.objects.create(name="Test Game")
        self.technology = Technology.objects.get(key=Technology.Key.DLSS)

    def add_supported_evidence(self):
        Compatibility.objects.create(
            gpu=self.gpu,
            game=self.game,
            technology=self.technology,
            resolution=Compatibility.Resolution.P1440,
            status=Compatibility.Status.SUPPORTED,
        )
        ResearchEvidence.objects.create(
            technology=self.technology,
            game=self.game,
            gpu=self.gpu,
            resolution=Compatibility.Resolution.P1440,
            source_title="Test source",
            findings="The tested technology performed well.",
            performance_score=4,
            image_quality_score=4,
            latency_score=3,
        )

    def test_registration_creates_non_staff_account_and_allows_token_login(self):
        response = self.client.post(
            "/api/v1/auth/register/",
            {"email": "gamer@example.com", "password": "Secure!Passw0rd-2026"},
            format="json",
        )

        self.assertEqual(response.status_code, 201)
        user = get_user_model().objects.get(email="gamer@example.com")
        self.assertFalse(user.is_staff)
        self.assertFalse(user.is_superuser)

        token_response = self.client.post(
            "/api/v1/auth/token/",
            {"username": "gamer@example.com", "password": "Secure!Passw0rd-2026"},
            format="json",
        )
        self.assertEqual(token_response.status_code, 200)
        self.assertIn("access", token_response.data)
        self.assertIn("refresh", token_response.data)

    def test_catalog_is_public_read_only_for_anonymous_users(self):
        response = self.client.get("/api/v1/gpus/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["results"][0]["name"], "Test GPU")

        write_response = self.client.post(
            "/api/v1/gpus/",
            {"name": "Unapproved GPU", "manufacturer": "Example"},
            format="json",
        )
        self.assertEqual(write_response.status_code, 401)
        self.assertFalse(GPU.objects.filter(name="Unapproved GPU").exists())

    def test_staff_user_can_write_catalog(self):
        staff_user = get_user_model().objects.create_user(
            username="catalog-admin",
            password="Secure!Passw0rd-2026",
            is_staff=True,
        )
        self.client.force_authenticate(user=staff_user)

        response = self.client.post(
            "/api/v1/gpus/",
            {"name": "New GPU", "manufacturer": "Example"},
            format="json",
        )

        self.assertEqual(response.status_code, 201)
        self.assertTrue(GPU.objects.filter(name="New GPU").exists())

    def test_recommendation_uses_supported_compatibility_and_scored_evidence(self):
        self.add_supported_evidence()

        response = self.client.post(
            "/api/v1/recommendations/",
            {
                "gpu_id": self.gpu.pk,
                "game_id": self.game.pk,
                "resolution": "1440p",
                "preference": "balanced",
            },
            format="json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["recommendation"], "dlss")
        self.assertIsNotNone(response.data["options"][0]["score"])

    def test_recommendation_returns_no_choice_without_compatibility(self):
        response = self.client.post(
            "/api/v1/recommendations/",
            {
                "gpu_id": self.gpu.pk,
                "game_id": self.game.pk,
                "resolution": "1440p",
            },
            format="json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertIsNone(response.data["recommendation"])
        self.assertTrue(response.data["limitations"])

    @override_settings(GROQ_API_KEY="")
    def test_ai_explanation_falls_back_when_provider_is_not_configured(self):
        self.add_supported_evidence()
        user = get_user_model().objects.create_user(
            username="gamer@example.com",
            password="Secure!Passw0rd-2026",
        )
        self.client.force_authenticate(user=user)

        response = self.client.post(
            "/api/v1/recommendations/explain/",
            {
                "gpu_id": self.gpu.pk,
                "game_id": self.game.pk,
                "resolution": "1440p",
                "preference": "balanced",
            },
            format="json",
        )

        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.data["ai"]["used"])
        self.assertEqual(response.data["ai"]["status"], "not_configured")
        self.assertEqual(response.data["explanation"], response.data["reason"])

    @override_settings(GROQ_DAILY_LIMIT_PER_USER=1, GROQ_DAILY_LIMIT_GLOBAL=2)
    def test_ai_quota_enforces_user_and_global_limits(self):
        user = get_user_model().objects.create_user(username="quota-user")

        count, remaining = reserve_ai_request(user)

        self.assertEqual((count, remaining), (1, 0))
        with self.assertRaises(DailyAIQuotaExceeded):
            reserve_ai_request(user)
        self.assertEqual(UserAIUsage.objects.get(user=user).request_count, 1)

        second_user = get_user_model().objects.create_user(username="second-quota-user")
        reserve_ai_request(second_user)
        third_user = get_user_model().objects.create_user(username="third-quota-user")
        with self.assertRaises(DailyAIQuotaExceeded):
            reserve_ai_request(third_user)

        self.assertEqual(GlobalAIUsage.objects.get().request_count, 2)