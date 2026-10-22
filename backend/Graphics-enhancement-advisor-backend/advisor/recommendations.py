from collections import defaultdict

from django.db.models import Q

from .models import Compatibility, ResearchEvidence


PREFERENCE_WEIGHTS = {
    "performance": {"performance_score": 0.60, "image_quality_score": 0.20, "latency_score": 0.20},
    "image_quality": {"performance_score": 0.20, "image_quality_score": 0.60, "latency_score": 0.20},
    "balanced": {"performance_score": 0.40, "image_quality_score": 0.40, "latency_score": 0.20},
}


def recommend(*, gpu, game, resolution, preference):
    compatibility = list(
        Compatibility.objects.filter(
            gpu=gpu, game=game, resolution=resolution, status=Compatibility.Status.SUPPORTED,
            is_verified=True,
            technology__is_active=True,
        ).select_related("technology")
    )
    recorded_compatibility = list(
        Compatibility.objects.filter(
            gpu=gpu, game=game, resolution=resolution, technology__is_active=True,
            is_verified=True,
        ).select_related("technology")
    )
    unsupported = [row for row in recorded_compatibility if row.status == Compatibility.Status.UNSUPPORTED]
    evidence = list(
        ResearchEvidence.objects.filter(is_active=True, technology__is_active=True)
        .filter(Q(game__isnull=True) | Q(game=game))
        .filter(Q(gpu__isnull=True) | Q(gpu=gpu))
        .filter(Q(resolution="") | Q(resolution=resolution))
        .filter(preference__in=[preference, "balanced"])
        .select_related("technology")
    )

    by_technology = defaultdict(list)
    for item in evidence:
        by_technology[item.technology_id].append(item)

    weights = PREFERENCE_WEIGHTS[preference]
    candidates = []
    for row in compatibility:
        records = by_technology[row.technology_id]
        scored_records = [item for item in records if any(getattr(item, field) is not None for field in weights)]
        weighted, total_weight = 0.0, 0.0
        for item in scored_records:
            score, score_weight = 0.0, 0.0
            for field, weight in weights.items():
                value = getattr(item, field)
                if value is not None:
                    score += value * weight
                    score_weight += weight
            if score_weight:
                weighted += (score / score_weight) * item.confidence
                total_weight += item.confidence
        score = round(weighted / total_weight, 2) if total_weight else None
        candidates.append((row, records, score, len(scored_records)))

    ranked = sorted((candidate for candidate in candidates if candidate[2] is not None), key=lambda item: (-item[2], item[0].technology.key))
    chosen = ranked[0] if ranked else None
    limitations = []
    if not compatibility:
        if unsupported:
            labels = ", ".join(row.technology.name for row in unsupported)
            limitations.append(f"The available compatibility data marks {labels} unsupported for this setup.")
        elif recorded_compatibility:
            limitations.append("The available compatibility data is inconclusive for this GPU, game and resolution.")
        else:
            limitations.append("No verified compatibility record exists for this GPU, game and resolution.")
    if any(score is None for _, _, score, _ in candidates):
        limitations.append("Some compatible technologies lack scored research evidence for the selected context.")
    if chosen and len(ranked) == 1:
        limitations.append("Only one compatible technology has scored research evidence, so no evidence-based comparison was possible.")
    if len(ranked) > 1 and ranked[0][2] - ranked[1][2] < 0.35:
        limitations.append("The leading evidence scores are close; small differences in testing conditions may change the result.")
    if chosen and chosen[3] < 2:
        limitations.append("The recommendation is supported by fewer than two research records.")

    results = []
    for row, records, score, scored_count in sorted(candidates, key=lambda item: (item[2] is None, -(item[2] or 0), item[0].technology.key)):
        results.append({
            "technology": {"id": row.technology_id, "key": row.technology.key, "name": row.technology.name},
            "compatibility": {"status": row.status, "performance": row.performance_summary,
                              "image_quality": row.image_quality_summary, "latency": row.latency_summary,
                              "limitations": row.limitations},
            "score": score,
            "evidence_count": scored_count,
            "evidence": [{"id": item.id, "source_title": item.source_title, "source_url": item.source_url,
                          "findings": item.findings, "limitations": item.limitations,
                          "confidence": item.confidence} for item in records],
        })

    reason = None
    if chosen:
        row, records, score, scored_count = chosen
        reason = (f"{row.technology.name} is compatible with this GPU and game at {resolution}; "
                  f"its confidence-weighted {preference.replace('_', ' ')} evidence score is {score}/5 "
                  f"across {scored_count} scored research record(s).")
    elif compatibility:
        reason = "Compatible options exist, but none has scored research evidence for a responsible ranking."
    else:
        reason = ("The available source data does not list a supported upscaler for this GPU and game."
                  if unsupported else
                  "There is no verified compatibility data for this GPU and game, so the system cannot recommend a technology.")

    return {
        "recommendation": chosen[0].technology.key if chosen else None,
        "recommendation_name": chosen[0].technology.name if chosen else None,
        "reason": reason,
        "preference": preference,
        "context": {"gpu": gpu.name, "game": game.name, "resolution": resolution},
        "confidence": "low" if limitations else ("medium" if chosen[3] < 3 else "high"),
        "limitations": limitations,
        "options": results,
        "method": "Preference-weighted 1-5 evidence scores, confidence-weighted across matching research records. Missing scores are omitted; unsupported options are not ranked.",
    }
