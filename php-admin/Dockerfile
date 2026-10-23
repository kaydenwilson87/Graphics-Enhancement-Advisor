FROM php:8.3-apache

RUN apt-get update \
    && apt-get install -y --no-install-recommends libcurl4-openssl-dev \
    && docker-php-ext-install curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /var/www/html
COPY index.php health.php ./
COPY docker-entrypoint.sh /usr/local/bin/portal-entrypoint
RUN chmod 755 /usr/local/bin/portal-entrypoint

ENV PORT=10000
EXPOSE 10000
ENTRYPOINT ["/usr/local/bin/portal-entrypoint"]
