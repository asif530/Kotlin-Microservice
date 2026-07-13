#!/bin/sh
# gateway/docker-entrypoint.sh
#
# Why this script exists (see Archive/Development/Backend/Implementation/Gateway
# for the full write-up):
#
# Kong's own declarative-config loader (KONG_DECLARATIVE_CONFIG, read directly
# by `kong start`/`kong docker-start`) only accepts literal PEM text for the
# JWT plugin's `rsa_public_key` field. It has no "read this file path" field
# type — verified against Kong 3.x's declarative-config docs; the `@file`
# shorthand some blog posts show is a client-side convenience of the separate
# `decK` CLI tool, not a capability of Kong's own config loader.
#
# The shared contract from Archive/Development/Backend/ChangeRequest/Request-001
# and the parallel identity-service RS256 login work says the public key
# lives at gateway/keys/identity-dev-public-key.pem. To honour that contract
# without baking a fake key into the checked-in gateway/kong.decl.yaml, this
# script renders the checked-in template at container start: it reads the
# real PEM file and substitutes it for the __IDENTITY_DEV_PUBLIC_KEY__
# placeholder, then hands the rendered copy to Kong.
#
# If the key file is missing, Kong is intentionally NOT started. That failure
# is the correct signal that the identity-service RS256 work hasn't landed
# yet — not something to paper over with a fabricated key.
set -eu

KEY_FILE="${IDENTITY_DEV_PUBLIC_KEY_PATH:-/kong/keys/identity-dev-public-key.pem}"
TEMPLATE="/kong/declarative/kong.decl.yaml"
RENDERED="/tmp/kong.decl.rendered.yaml"

if [ ! -f "$KEY_FILE" ]; then
  echo "============================================================" >&2
  echo "FATAL: $KEY_FILE not found." >&2
  echo "Kong's JWT plugin needs identity-service's RS256 public key" >&2
  echo "to verify tokens. This file is produced by the identity-" >&2
  echo "service login (RS256 signing) work — see Request-001 and" >&2
  echo "the gateway implementation guide for the exact shared-file" >&2
  echo "contract. Refusing to start with a fabricated key. Place the" >&2
  echo "real public key at gateway/keys/identity-dev-public-key.pem" >&2
  echo "and retry." >&2
  echo "============================================================" >&2
  exit 1
fi

if [ ! -f "$TEMPLATE" ]; then
  echo "FATAL: declarative config template $TEMPLATE not found (check the" >&2
  echo "gateway/kong.decl.yaml volume mount in docker-compose.yml)." >&2
  exit 1
fi

# Substitute the placeholder line with the key file's contents, preserving
# the placeholder line's own indentation on every injected line so the
# surrounding YAML block scalar (rsa_public_key: |) stays valid.
# NOTE: the match below is anchored to a line containing *only* the
# placeholder (whitespace aside). kong.decl.yaml also mentions the literal
# token __IDENTITY_DEV_PUBLIC_KEY__ inside an explanatory comment earlier in
# the file -- an unanchored match would hit that comment line first and
# consume the single-pass getline read, leaving the real field empty.
awk -v keyfile="$KEY_FILE" '
  {
    match($0, /^[ \t]*/)
    indent = substr($0, RSTART, RLENGTH)
  }
  /^[ \t]*__IDENTITY_DEV_PUBLIC_KEY__[ \t]*$/ {
    while ((getline line < keyfile) > 0) print indent line
    next
  }
  { print }
' "$TEMPLATE" > "$RENDERED"

export KONG_DECLARATIVE_CONFIG="$RENDERED"

# Delegate to the official kong:3.x image's own entrypoint script rather
# than invoking `kong docker-start` directly: "docker-start" is not a real
# subcommand of the `kong` CLI (confirmed the hard way, see
# Archive/Issues/Gateway0) -- it's a pseudo-command that image's own
# /docker-entrypoint.sh translates into the correct prepare+start sequence.
# This script only needs to run its own check/render *before* that, then
# get out of the way.
exec /docker-entrypoint.sh kong docker-start
