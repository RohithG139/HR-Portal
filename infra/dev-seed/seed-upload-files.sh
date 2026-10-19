#!/usr/bin/env bash
# DEV-ONLY: writes a placeholder file into hr-backend/uploads/ for every document row seeded by
# seed-dummy-data.sql (storage_key seed-doc-*), sized to match document.size_bytes.
# PDFs get a minimal valid one-page PDF naming the document; other types get plain text.
# Run from the repo root after loading seed-dummy-data.sql.
set -euo pipefail

UPLOADS="$(dirname "$0")/../../hr-backend/uploads"
mkdir -p "$UPLOADS"

docker exec hr-db-postgres psql -U postgres -d hr_app -At -F '|' \
  -c "select storage_key, file_name, content_type, size_bytes from hr.document where storage_key like 'seed-doc-%' order by storage_key" |
while IFS='|' read -r key name type size; do
  out="$UPLOADS/$key"
  if [ "$type" = "application/pdf" ]; then
    text="Placeholder document: $name"
    stream="BT /F1 14 Tf 72 720 Td ($text) Tj ET"
    {
      printf '%%PDF-1.4\n'
      printf '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n'
      printf '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n'
      printf '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n'
      printf '4 0 obj<</Length %d>>stream\n%s\nendstream endobj\n' "${#stream}" "$stream"
      printf '5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\n'
      printf 'trailer<</Root 1 0 R>>\n%%%%EOF\n'
    } > "$out"
  else
    printf 'Placeholder document: %s (%s)\n' "$name" "$type" > "$out"
  fi
  # Pad to the size recorded in the database (readers ignore trailing whitespace after %%EOF).
  current=$(wc -c < "$out")
  if [ "$current" -lt "$size" ]; then
    head -c $((size - current)) /dev/zero | tr '\0' ' ' >> "$out"
  fi
  echo "wrote $key ($name, $size bytes)"
done
