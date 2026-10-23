FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

COPY requirements.txt ./
RUN pip install --upgrade pip && pip install -r requirements.txt

RUN addgroup --system app && adduser --system --ingroup app app
COPY --chown=app:app . .
# Files saved on Windows can have CRLF line endings, which break shell scripts. Convert them.
RUN sed -i 's/\r$//' start.sh
RUN SECRET_KEY=build-only-placeholder-000000000000000000000000000000000000000000000000 DATABASE_URL=postgresql://build:build@localhost:5432/build ALLOWED_HOSTS=build.invalid DEBUG=False python manage.py collectstatic --noinput

USER app
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD ["python", "-c", "import urllib.request, os; urllib.request.urlopen('http://127.0.0.1:%s/health/' % os.environ.get('PORT', '8000'), timeout=3)"]
CMD ["sh", "start.sh"]
