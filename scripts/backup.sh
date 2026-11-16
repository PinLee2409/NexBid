#!/usr/bin/env bash
# EN: Backs up the NexBid database from the Docker stack to backups/nexbid-<time>.dump (pg_dump custom format:
#     compressed, and restorable table by table). Keeps the newest $KEEP dumps (default 14). Photos and videos live
#     in the backend's media volume, not in the database; --with-media also copies them, to a .tar.gz beside it.
# VI: Sao lưu database NexBid từ stack Docker vào backups/nexbid-<thời gian>.dump (định dạng custom của pg_dump: có
#     nén, và khôi phục được từng bảng). Giữ $KEEP bản mới nhất (mặc định 14). Ảnh và video nằm trong volume media
#     của backend, không ở trong database; --with-media chép luôn chúng, vào một file .tar.gz bên cạnh.
#
#   bash scripts/backup.sh                     # the stack from `docker compose up`
#   COMPOSE_FILE=docker/compose.yaml bash scripts/backup.sh   # the dev services
#   bash scripts/backup.sh --with-media
set -euo pipefail
cd "$(dirname "$0")/.."

OUT=${BACKUP_DIR:-backups}
KEEP=${KEEP:-14}
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p "$OUT"

FILE="$OUT/nexbid-$STAMP.dump"
# EN: Written to a temporary name first, so a failed dump never looks like a good one.
# VI: Ghi vào tên tạm trước, để bản dump hỏng không bao giờ trông như bản tốt.
docker compose exec -T postgres pg_dump -U nexbid -d nexbid --format=custom --no-owner > "$FILE.partial"
mv "$FILE.partial" "$FILE"
echo "database → $FILE ($(du -h "$FILE" | cut -f1))"

if [[ "${1:-}" == "--with-media" ]]; then
  MEDIA="$OUT/nexbid-$STAMP-media.tar.gz"
  docker compose exec -T backend sh -c 'tar -czf - -C /app var' 2>/dev/null > "$MEDIA.partial" && mv "$MEDIA.partial" "$MEDIA" \
    && echo "media    → $MEDIA ($(du -h "$MEDIA" | cut -f1))" \
    || { rm -f "$MEDIA.partial"; echo "media    → skipped (no media folder in the backend container)"; }
fi

# EN: Oldest first, all but the newest $KEEP. / VI: Cũ nhất trước, trừ $KEEP bản mới nhất.
ls -1t "$OUT"/nexbid-*.dump 2>/dev/null | tail -n +"$((KEEP + 1))" | while read -r old; do
  rm -f "$old" "${old%.dump}-media.tar.gz"
  echo "removed  $old"
done
