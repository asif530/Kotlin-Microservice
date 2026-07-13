#!/bin/bash
# Creates the second Postgres-hosted logical database (order-service's `orders`).
# `identity` (identity-service) is created automatically by POSTGRES_DB.
# Per ARCHITECTURE.md §9: both logical databases share this one Postgres 17
# instance for local Compose simplicity, with separate database names.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE DATABASE orders OWNER $POSTGRES_USER;
EOSQL
