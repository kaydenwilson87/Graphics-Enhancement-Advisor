from rest_framework.permissions import BasePermission, SAFE_METHODS


class ReadOnlyOrStaff(BasePermission):
    """Public catalog reads; authenticated staff manage research data."""

    def has_permission(self, request, view):
        return request.method in SAFE_METHODS or bool(request.user and request.user.is_staff)
