#!/usr/bin/env bash
# EN: Restores a dump made by scripts/backup.sh into the stack's database, replacing what is there. The backend is
#     stopped meanwhile, so nothing writes half-way through, and started again after. Asks before touching anything
#     unless YES=1. Restoring an older dump also brings back its Flyway history; the backend migrates it forward on
#     start, as after any upgrade.
# VI: Khôi phục một bản dump do scripts/backup.sh tạo vào database của stack, thay cho dữ liệu đang có. Backend được
#     dừng trong lúc đó để không ai ghi dở dang, rồi chạy lại sau. Hỏi trước khi đụng vào bất cứ gì trừ khi YES=1.
#     Khôi phục bản dump cũ cũng đem lại lịch sử Flyway của nó; backend tự migrate lên khi khởi động, như sau mọi lần
#     nâng cấp.
#
#   bash scripts/restore.sh backups/nexbid-20261117T010000Z.dump
#   YES=1 bash scripts/restore.sh backups/nexbid-…dump
set -euo pipefail
cd "$(dirname "$0")/.."

DUMP=${1:?"usage: scripts/restore.sh <file.dump>"}
[[ -f "$DUMP" ]] || { echo "No such file: $DUMP" >&2; exit 1; }

if [[ "${YES:-}" != "1" ]]; then
  read -r -p "Replace the database of $(docker compose config --format json 2>/dev/null | grep -o '"name": *"[^"]*"' | head -1) with $DUMP? [y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || { echo "Nothing changed."; exit 0; }
fi

docker compose stop backend >/dev/null 2>&1 || true
# EN: --clean drops each object before recreating it; --if-exists keeps that quiet on an empty database.
# VI: --clean xoá từng đối tượng trước khi tạo lại; --if-exists giữ cho việc đó im lặng trên database trống.
docker compose exec -T postgres pg_restore -U nexbid -d nexbid --clean --if-exists --no-owner --single-transaction < "$DUMP"
echo "restored $DUMP"
docker compose start backend >/dev/null 2>&1 || true
echo "backend started again; it may take a few seconds to answer"
