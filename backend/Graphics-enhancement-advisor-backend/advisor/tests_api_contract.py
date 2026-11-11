"""Extra API tests focused on the contract the Android app depends on (response shapes, validation, access rules).

The main behaviour tests live in tests.py. These cover gaps around input validation and the exact fields the app reads.
"""
from datetime import timedelta

from django.contrib.auth import get_user_model
from django.core.cache import cache
from django.test import TestCase
from django.utils import timezone
from rest_framework.test import APIClient

from .models import GPU, SavedComparison

SAVED_URL = "/api/v1/saved-comparisons/"


def comparison_payload(**overrides):
    payload = {
        "gpu_name": "Test GPU", "game_name": "Test Game", "resolution": "1440p",
        "preference": "balanced", "recommendation": "DLSS", "reason": "Test reason", "note": "",
    }
    payload.update(overrides)
    return payload


class ApiContractTests(TestCase):
    def setUp(self):
        cache.clear()  # throttle counters live in the cache, so every test starts with a clean slate
        self.client = APIClient()
        self.user = get_user_model().objects.create_user(
            username="player@example.com", email="player@example.com", password="Secure!Passw0rd-2026"
        )

    # --- health and basic shapes -------------------------------------------------------------------

    def test_health_endpoint_reports_database_ok(self):
        response = self.client.get("/health/")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "database": "ok"})

    def test_catalog_lists_are_paginated_with_the_shape_the_app_reads(self):
        GPU.objects.create(name="Shape GPU", manufacturer="Example")
        response = self.client.get("/api/v1/gpus/")
        self.assertEqual(response.status_code, 200)
        self.assertTrue({"count", "next", "previous", "results"} <= set(response.data.keys()))

    def test_current_account_returns_the_fields_the_app_reads(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.get("/api/v1/auth/me/")
        self.assertEqual(response.status_code, 200)
        self.assertTrue({"id", "email", "is_staff"} <= set(response.data.keys()))
        self.assertEqual(response.data["email"], "player@example.com")

    # --- registration and login --------------------------------------------------------------------

    def test_registration_rejects_a_short_password(self):
        response = self.client.post(
            "/api/v1/auth/register/", {"email": "short@example.com", "password": "short"}, format="json"
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("password", response.data)
        self.assertFalse(get_user_model().objects.filter(email="short@example.com").exists())

    def test_registration_rejects_a_duplicate_email_ignoring_case(self):
        response = self.client.post(
            "/api/v1/auth/register/",
            {"email": "PLAYER@example.com", "password": "Another!Passw0rd-2026"},
            format="json",
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("email", response.data)

    def test_token_login_rejects_a_wrong_password(self):
        response = self.client.post(
            "/api/v1/auth/token/",
            {"username": "player@example.com", "password": "not-the-password"},
            format="json",
        )
        self.assertEqual(response.status_code, 401)

    # --- saved comparisons -------------------------------------------------------------------------

    def test_saved_comparisons_require_a_signed_in_user(self):
        response = self.client.get(SAVED_URL)
        self.assertIn(response.status_code, (401, 403))

    def test_saved_comparison_rejects_an_unknown_resolution(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.post(SAVED_URL, comparison_payload(resolution="720p"), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("resolution", response.data)
        self.assertEqual(SavedComparison.objects.count(), 0)

    def test_saved_comparison_rejects_a_note_over_200_characters(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.post(SAVED_URL, comparison_payload(note="x" * 201), format="json")
        self.assertEqual(response.status_code, 400)
        self.assertIn("note", response.data)

    def test_saved_comparisons_are_listed_newest_first(self):
        self.client.force_authenticate(user=self.user)
        older = SavedComparison.objects.create(user=self.user, **comparison_payload(note="older"))
        SavedComparison.objects.create(user=self.user, **comparison_payload(note="newer"))
        SavedComparison.objects.filter(pk=older.pk).update(created_at=timezone.now() - timedelta(days=1))

        response = self.client.get(SAVED_URL)
        self.assertEqual(response.status_code, 200)
        self.assertEqual([row["note"] for row in response.data["results"]], ["newer", "older"])

    # --- recommendations ---------------------------------------------------------------------------

    def test_recommendation_rejects_an_unknown_resolution(self):
        gpu = GPU.objects.create(name="Validation GPU", manufacturer="Example")
        response = self.client.post(
            "/api/v1/recommendations/",
            {"gpu_id": gpu.pk, "game_id": 1, "resolution": "720p", "preference": "balanced"},
            format="json",
        )
        self.assertEqual(response.status_code, 400)
        self.assertIn("resolution", response.data)
