"""Sync game upscaler integrations from PCGamingWiki's maintained public list.

The upstream list documents game-level integrations, not benchmark scores or
resolution-specific performance. Sync-created records retain that limitation
and never create scores. Manually entered records are left untouched.
"""

from html.parser import HTMLParser
import re
import unicodedata

import httpx
from django.core.cache import cache
from django.db import transaction

from .models import Compatibility, Game, GPU, ResearchEvidence, Technology


SOURCE_TITLE = "PCGamingWiki — high-fidelity upscaling list (automatic sync)"
SOURCE_URL = "https://www.pcgamingwiki.com/wiki/List_of_games_that_support_high-fidelity_upscaling"
SOURCE_MARKER = "Auto-synced from PCGamingWiki:"
CATALOG_CACHE_KEY = "advisor:pcgamingwiki:upscaler-catalog:v1"
CATALOG_TTL_SECONDS = 6 * 60 * 60
FAILURE_TTL_SECONDS = 10 * 60
RESOLUTIONS = ("1080p", "1440p", "2160p")


class _TextCell:
    def __init__(self):
        self.text = []
        self.links = []
        self._link_text = None

    @property
    def value(self):
        # PCGW tables commonly append superscript citation numbers to game names.
        return " ".join(re.sub(r"\[\s*\d+\s*\]", " ", "".join(self.text)).split())


class _TableParser(HTMLParser):
    """Small stdlib parser for the first current-games table in the source page."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tables = []
        self.depth = 0
        self.rows = None
        self.row = None
        self.cell = None

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "table":
            if self.depth == 0:
                self.rows = []
                self.tables.append(self.rows)
            self.depth += 1
        elif self.depth == 1 and tag == "tr":
            self.row = []
        elif self.depth == 1 and tag in ("td", "th"):
            self.cell = _TextCell()
        elif self.depth == 1 and tag == "a" and self.cell is not None:
            href = attrs.get("href", "")
            if href:
                self.cell.links.append(href)
            self.cell._link_text = []
        elif tag == "br" and self.depth == 1 and self.cell is not None:
            self.cell.text.append(" ")

    def handle_endtag(self, tag):
        if self.depth == 1 and tag in ("td", "th") and self.cell is not None:
            self.row.append(self.cell)
            self.cell = None
        elif self.depth == 1 and tag == "tr" and self.row is not None:
            if self.row:
                self.rows.append(self.row)
            self.row = None
        elif tag == "table" and self.depth:
            self.depth -= 1

    def handle_data(self, data):
        if self.depth == 1 and self.cell is not None:
            self.cell.text.append(data)


def _normalize(value):
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode("ascii")
    return " ".join(re.sub(r"[^a-z0-9]+", " ", value.casefold()).split())


def _table_columns(rows):
    for index, row in enumerate(rows):
        headers = [_normalize(cell.value) for cell in row]
        game_col = next((i for i, value in enumerate(headers) if value == "game"), None)
        amd_col = next((i for i, value in enumerate(headers) if "amd" in value), None)
        nvidia_col = next((i for i, value in enumerate(headers) if "nvidia" in value), None)
        intel_col = next((i for i, value in enumerate(headers) if "intel" in value or "xess" in value), None)
        if None not in (game_col, amd_col, nvidia_col, intel_col):
            return index, {"fsr": amd_col, "dlss": nvidia_col, "xess": intel_col}
    return None, None


def _source_support(value):
    raw = re.sub(r"\[\s*\d+\s*\]", " ", value).strip()
    if raw in {"—", "–", "-", "➖", "✖", "❌"}:
        return False
    normalized = _normalize(value)
    if not normalized or any(term in normalized for term in ("tba", "upcoming", "planned", "unconfirmed")):
        return None
    if normalized in {"none", "n a", "na", "not available", "unknown"}:
        return None
    if any(term in normalized for term in ("unsupported", "not supported", "no support")):
        return False
    return True


def parse_upscaler_catalog(html):
    """Return game-level support data from the current-games table, or raise."""
    parser = _TableParser()
    parser.feed(html)
    for rows in parser.tables:
        header_index, tech_columns = _table_columns(rows)
        if tech_columns is None:
            continue
        results = {}
        for row in rows[header_index + 1:]:
            if not row:
                continue
            name_cell = row[0]
            game_name = name_cell.value
            if not game_name or _normalize(game_name) in {"game", "games"}:
                continue
            support = {
                key: _source_support(row[column].value) if column < len(row) else None
                for key, column in tech_columns.items()
            }
            page_path = next((link for link in name_cell.links if "/wiki/" in link), "")
            page_url = page_path if page_path.startswith("http") else "https://www.pcgamingwiki.com" + page_path
            results[_normalize(game_name)] = {
                "name": game_name,
                "support": support,
                "game_url": page_url or SOURCE_URL,
            }
        if results:
            return results
    raise ValueError("Could not find the current games/upscaler table on the source page.")


def fetch_upscaler_catalog():
    """Fetch the public MediaWiki parse API and cache successful parses."""
    cached = cache.get(CATALOG_CACHE_KEY)
    if cached is not None:
        return cached
    try:
        response = httpx.get(
            "https://www.pcgamingwiki.com/w/api.php",
            params={
                "action": "parse",
                "page": "List_of_games_that_support_high-fidelity_upscaling",
                "prop": "text",
                "format": "json",
            },
            headers={"User-Agent": "GraphicsEnhancementAdvisor/1.0 (educational compatibility lookup)"},
            timeout=httpx.Timeout(3.0, connect=1.5),
            follow_redirects=True,
        )
        response.raise_for_status()
        payload = response.json()
        html = payload.get("parse", {}).get("text", {}).get("*", "")
        catalog = parse_upscaler_catalog(html)
        result = {"catalog": catalog, "error": ""}
        cache.set(CATALOG_CACHE_KEY, result, CATALOG_TTL_SECONDS)
        return result
    except (httpx.HTTPError, ValueError, KeyError, TypeError) as exc:
        result = {"catalog": None, "error": str(exc)}
        cache.set(CATALOG_CACHE_KEY, result, FAILURE_TTL_SECONDS)
        return result


def _gpu_supports(gpu, tech_key):
    manufacturer = _normalize(gpu.manufacturer)
    name = _normalize(gpu.name)
    if tech_key == "dlss":
        if "nvidia" not in manufacturer and "geforce" not in name:
            return False, "DLSS is NVIDIA RTX technology; this GPU is from a different vendor."
        if "rtx" not in name:
            return None, "The GPU model does not identify an RTX card, so native DLSS support is unverified."
        return True, "The game lists DLSS and the selected GPU is an NVIDIA RTX model."
    if "nvidia" in manufacturer or "amd" in manufacturer or "intel" in manufacturer or any(v in name for v in ("geforce", "radeon", "arc")):
        if tech_key == "fsr":
            return True, "The game lists FSR; FSR upscaling is vendor-agnostic on supported PC hardware."
        return True, "The game lists XeSS; this GPU family can use the game's cross-vendor XeSS path where supported by its driver."
    return None, "The GPU vendor or model is not specific enough to verify hardware compatibility."


@transaction.atomic
def sync_game_compatibility(game, gpu, resolution):
    """Update one selected game/GPU/resolution while preserving manual admin rows."""
    fetched = fetch_upscaler_catalog()
    catalog = fetched["catalog"]
    if catalog is None:
        return {"synced": False, "game_found": False, "error": fetched["error"], "source_url": SOURCE_URL}

    source_game = catalog.get(_normalize(game.name))
    if source_game is None:
        return {"synced": True, "game_found": False, "updated": 0, "error": "", "source_url": SOURCE_URL}
    techs = {technology.key: technology for technology in Technology.objects.filter(is_active=True)}
    updated = 0
    for key, technology in techs.items():
        listed_support = source_game["support"].get(key) if source_game else None
        hardware_support, hardware_note = _gpu_supports(gpu, key)
        if listed_support is False or (listed_support is True and hardware_support is False):
            status = Compatibility.Status.UNSUPPORTED
        elif listed_support is True and hardware_support is True:
            status = Compatibility.Status.SUPPORTED
        else:
            status = Compatibility.Status.UNKNOWN

        if source_game:
            source_note = (
                f"{SOURCE_MARKER} the current supported-games table lists "
                f"{technology.name} as {'present' if listed_support is True else 'not listed' if listed_support is False else 'unconfirmed'} "
                f"for {source_game['name']}. {hardware_note} The source verifies game integration, not performance scores or a specific resolution."
            )
            source_game_url = source_game["game_url"]
        else:
            source_note = f"{SOURCE_MARKER} this title was not found in the current supported-games table; status remains unknown."
            source_game_url = SOURCE_URL

        row, created = Compatibility.objects.get_or_create(
            gpu=gpu, game=game, technology=technology, resolution=resolution,
            defaults={"status": status, "limitations": source_note, "is_verified": False},
        )
        if not created and row.limitations.startswith(SOURCE_MARKER):
            row.status = status
            row.limitations = source_note
            row.save(update_fields=["status", "limitations", "updated_at"])
        elif not created:
            # Human-reviewed admin data takes precedence over the automated catalog.
            continue
        updated += 1
        if row.is_rejected:
            continue

        if source_game:
            support_text = (
                f"PCGamingWiki lists {technology.name} as an integrated game upscaler for {source_game['name']}."
                if listed_support is True else
                f"PCGamingWiki does not list {technology.name} in the current supported-upscaler cell for {source_game['name']}."
                if listed_support is False else
                f"PCGamingWiki's current entry does not confirm {technology.name} support for {source_game['name']}."
            )
            evidence, evidence_created = ResearchEvidence.objects.get_or_create(
                technology=technology, game=game, gpu=None, source_title=SOURCE_TITLE,
                defaults={
                    "source_url": source_game_url or SOURCE_URL,
                    "publisher": "PCGamingWiki",
                    "findings": support_text,
                    "limitations": "Community-maintained integration listing. It does not provide benchmark scores, prove support at a specific resolution, or guarantee the selected GPU/driver/game version. See source page for references. Content: CC BY-NC-SA unless otherwise noted.",
                    "confidence": 2,
                    "is_active": True,
                },
            )
            if not evidence_created and evidence.source_title == SOURCE_TITLE:
                evidence.source_url = source_game_url or SOURCE_URL
                evidence.findings = support_text
                evidence.limitations = "Community-maintained integration listing. It does not provide benchmark scores, prove support at a specific resolution, or guarantee the selected GPU/driver/game version. See source page for references. Content: CC BY-NC-SA unless otherwise noted."
                evidence.is_active = True
                evidence.save(update_fields=["source_url", "findings", "limitations", "is_active", "updated_at"])

    return {
        "synced": True,
        "game_found": source_game is not None,
        "updated": updated,
        "error": "",
        "source_url": source_game["game_url"] if source_game else SOURCE_URL,
    }
