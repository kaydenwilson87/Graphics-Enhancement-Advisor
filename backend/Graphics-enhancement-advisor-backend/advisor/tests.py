from django.contrib.auth import get_user_model
from django.test import TestCase, override_settings
from rest_framework.test import APIClient
from unittest.mock import patch

from .compatibility_sync import parse_upscaler_catalog, sync_game_compatibility
from .models import Compatibility, Game, GPU, GlobalAIUsage, ResearchEvidence, SavedComparison, Technology, UserAIUsage
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

    @patch("advisor.views.sync_game_compatibility", return_value={
        "synced": True, "game_found": True, "updated": 3, "error": "", "source_url": "https://example.test"
    })
    def test_staff_user_can_write_catalog(self, sync):
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
        self.assertEqual(sync.call_count, 3)

    @patch("advisor.views.sync_game_compatibility", return_value={
        "synced": True, "game_found": True, "updated": 3, "error": "", "source_url": "https://example.test"
    })
    def test_adding_game_prepares_suggestions_for_existing_gpus(self, sync):
        gpu = GPU.objects.create(name="Existing GPU", manufacturer="NVIDIA")
        staff_user = get_user_model().objects.create_user(username="catalog-admin", is_staff=True)
        self.client.force_authenticate(user=staff_user)

        response = self.client.post("/api/v1/games/", {"name": "New Game"}, format="json")

        self.assertEqual(response.status_code, 201)
        self.assertEqual(sync.call_count, 6)
        self.assertEqual({call.kwargs["gpu"].pk for call in sync.call_args_list}, {self.gpu.pk, gpu.pk})

    def test_authenticated_non_staff_cannot_write_catalog(self):
        user = get_user_model().objects.create_user(username="regular-gamer")
        self.client.force_authenticate(user=user)

        create_response = self.client.post(
            "/api/v1/gpus/", {"name": "Blocked GPU", "manufacturer": "Example"}, format="json"
        )
        update_response = self.client.patch(
            f"/api/v1/gpus/{self.gpu.pk}/", {"name": "Changed GPU"}, format="json"
        )
        delete_response = self.client.delete(f"/api/v1/gpus/{self.gpu.pk}/")

        self.assertEqual(create_response.status_code, 403)
        self.assertEqual(update_response.status_code, 403)
        self.assertEqual(delete_response.status_code, 403)
        self.assertTrue(GPU.objects.filter(pk=self.gpu.pk, name="Test GPU").exists())
        self.assertFalse(GPU.objects.filter(name="Blocked GPU").exists())

    def test_current_account_reports_staff_status(self):
        user = get_user_model().objects.create_user(username="regular-gamer", email="gamer@example.com")
        self.client.force_authenticate(user=user)
        response = self.client.get("/api/v1/auth/me/")
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.data["is_staff"])

        staff_user = get_user_model().objects.create_user(username="staff-gamer", is_staff=True)
        self.client.force_authenticate(user=staff_user)
        response = self.client.get("/api/v1/auth/me/")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.data["is_staff"])

    def test_saved_comparisons_are_private_and_update_uses_full_put(self):
        User = get_user_model()
        owner = User.objects.create_user(username="comparison-owner")
        other = User.objects.create_user(username="comparison-other")
        endpoint = "/api/v1/saved-comparisons/"
        payload = {
            "gpu_name": "Test GPU", "game_name": "Test Game", "resolution": "1440p",
            "preference": "balanced", "recommendation": "dlss", "reason": "Test reason", "note": "Initial",
        }

        self.client.force_authenticate(user=owner)
        created = self.client.post(endpoint, payload, format="json")
        self.assertEqual(created.status_code, 201)
        comparison_id = created.data["id"]
        row = SavedComparison.objects.get(pk=comparison_id)
        self.assertEqual(row.user, owner)

        updated_payload = {**payload, "note": "Updated"}
        updated = self.client.put(f"{endpoint}{comparison_id}/", updated_payload, format="json")
        self.assertEqual(updated.status_code, 200)
        self.assertEqual(updated.data["note"], "Updated")

        self.client.force_authenticate(user=other)
        self.assertEqual(self.client.get(endpoint).data["results"], [])
        self.assertEqual(self.client.get(f"{endpoint}{comparison_id}/").status_code, 404)
        self.assertEqual(self.client.put(f"{endpoint}{comparison_id}/", updated_payload, format="json").status_code, 404)
        self.assertEqual(self.client.delete(f"{endpoint}{comparison_id}/").status_code, 404)

        self.client.force_authenticate(user=owner)
        self.assertEqual(self.client.delete(f"{endpoint}{comparison_id}/").status_code, 204)

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

    @patch("advisor.views.sync_game_compatibility")
    def test_recommendation_reports_compatible_options_without_scores(self, sync):
        self.gpu.name = "GeForce RTX 4070"
        self.gpu.manufacturer = "NVIDIA"
        self.gpu.save()
        sync.return_value = {
            "synced": True, "game_found": True, "updated": 1, "error": "", "source_url": "https://example.test"
        }
        Compatibility.objects.create(
            gpu=self.gpu, game=self.game, technology=self.technology,
            resolution="1440p", status=Compatibility.Status.SUPPORTED,
        )

        response = self.client.post("/api/v1/recommendations/", {
            "gpu_id": self.gpu.pk, "game_id": self.game.pk, "resolution": "1440p", "preference": "balanced",
        }, format="json")

        self.assertEqual(response.status_code, 200)
        self.assertIsNone(response.data["recommendation"])
        self.assertEqual(response.data["options"][0]["technology"]["key"], "dlss")
        self.assertIsNone(response.data["options"][0]["score"])
        self.assertIn("compatible options exist", response.data["reason"].lower())

    def test_parse_upscaler_catalog_reads_support_columns_and_ignores_citation_suffix(self):
        html = """<table><tr><th>Game</th><th>AMD</th><th>Nvidia</th><th>Intel</th></tr>
          <tr><td><a href="/wiki/Test_Game">Test Game</a><sup>[1]</sup></td>
          <td>FSR 3.1</td><td>DLSS 3</td><td>—</td></tr></table>"""

        parsed = parse_upscaler_catalog(html)

        self.assertEqual(parsed["test game"]["support"], {"fsr": True, "dlss": True, "xess": False})
        self.assertTrue(parsed["test game"]["game_url"].endswith("/wiki/Test_Game"))

    @patch("advisor.compatibility_sync.fetch_upscaler_catalog")
    def test_auto_sync_respects_gpu_vendor_and_records_source_evidence_without_scores(self, fetch):
        self.gpu.name = "GeForce RTX 4070"
        self.gpu.manufacturer = "NVIDIA"
        self.gpu.save()
        fetch.return_value = {"catalog": {"test game": {
            "name": "Test Game", "game_url": "https://example.test/test-game",
            "support": {"dlss": True, "fsr": True, "xess": False},
        }}, "error": ""}

        result = sync_game_compatibility(game=self.game, gpu=self.gpu, resolution="1440p")

        self.assertTrue(result["synced"])
        self.assertEqual(Compatibility.objects.get(technology__key="dlss").status, Compatibility.Status.SUPPORTED)
        self.assertEqual(Compatibility.objects.get(technology__key="fsr").status, Compatibility.Status.SUPPORTED)
        self.assertEqual(Compatibility.objects.get(technology__key="xess").status, Compatibility.Status.UNSUPPORTED)
        self.assertTrue(all(not row.is_verified for row in Compatibility.objects.all()))
        source_evidence = ResearchEvidence.objects.filter(source_title__contains="automatic sync")
        self.assertEqual(source_evidence.count(), 3)
        self.assertTrue(all(item.performance_score is None for item in source_evidence))

    @patch("advisor.compatibility_sync.fetch_upscaler_catalog")
    def test_auto_sync_does_not_create_a_suggestion_when_title_is_not_listed(self, fetch):
        fetch.return_value = {"catalog": {}, "error": ""}

        sync_game_compatibility(game=self.game, gpu=self.gpu, resolution="1440p")

        self.assertEqual(Compatibility.objects.count(), 0)

    @patch("advisor.compatibility_sync.fetch_upscaler_catalog")
    def test_auto_sync_does_not_overwrite_admin_reviewed_compatibility(self, fetch):
        row = Compatibility.objects.create(
            gpu=self.gpu, game=self.game, technology=self.technology,
            resolution="1440p", status=Compatibility.Status.UNKNOWN, limitations="Reviewed by administrator",
        )
        fetch.return_value = {"catalog": {"test game": {
            "name": "Test Game", "game_url": "https://example.test/test-game",
            "support": {"dlss": True, "fsr": True, "xess": False},
        }}, "error": ""}

        sync_game_compatibility(game=self.game, gpu=self.gpu, resolution="1440p")

        row.refresh_from_db()
        self.assertEqual(row.status, Compatibility.Status.UNKNOWN)
        self.assertEqual(row.limitations, "Reviewed by administrator")

    @patch("advisor.compatibility_sync.fetch_upscaler_catalog")
    def test_dismissed_source_suggestion_stays_dismissed_after_refresh(self, fetch):
        row = Compatibility.objects.create(
            gpu=self.gpu, game=self.game, technology=self.technology,
            resolution="1440p", status=Compatibility.Status.SUPPORTED,
            limitations="Auto-synced from PCGamingWiki: prior suggestion", is_verified=False, is_rejected=True,
        )
        fetch.return_value = {"catalog": {"test game": {
            "name": "Test Game", "game_url": "https://example.test/test-game",
            "support": {"dlss": True, "fsr": True, "xess": False},
        }}, "error": ""}

        sync_game_compatibility(game=self.game, gpu=self.gpu, resolution="1440p")

        row.refresh_from_db()
        self.assertFalse(row.is_verified)
        self.assertTrue(row.is_rejected)

    @patch("advisor.views.sync_game_compatibility", return_value={
        "synced": True, "game_found": True, "updated": 0, "error": "", "source_url": "https://example.test"
    })
    def test_unverified_automatic_compatibility_does_not_enter_recommendations(self, sync):
        row = Compatibility.objects.create(
            gpu=self.gpu, game=self.game, technology=self.technology,
            resolution="1440p", status=Compatibility.Status.SUPPORTED, is_verified=False,
        )

        response = self.client.post("/api/v1/recommendations/", {
            "gpu_id": self.gpu.pk, "game_id": self.game.pk, "resolution": "1440p", "preference": "balanced",
        }, format="json")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data["options"], [])
        row.is_verified = True
        row.save(update_fields=["is_verified"])
        response = self.client.post("/api/v1/recommendations/", {
            "gpu_id": self.gpu.pk, "game_id": self.game.pk, "resolution": "1440p", "preference": "balanced",
        }, format="json")
        self.assertEqual(len(response.data["options"]), 1)

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
