#!/usr/bin/env bash
# EN: Runs the four load levels of guide §41 against the stack from `docker compose up`, and while each
#     runs, samples what k6 cannot see from outside: the backend's database connections and Redis hits.
#     Results land in docs/load-test/results/.
# VI: Chạy bốn mức tải của guide §41 trên stack của `docker compose up`, và trong lúc mỗi mức chạy thì lấy
#     mẫu những gì k6 không nhìn thấy từ bên ngoài: số kết nối database của backend và lượt trúng Redis.
#     Kết quả nằm trong docs/load-test/results/.
#
#   bash docs/load-test/run.sh                 # 10 100 500 1000
#   LEVELS="10 100" bash docs/load-test/run.sh
set -euo pipefail
cd "$(dirname "$0")/../.."

RESULTS=docs/load-test/results
mkdir -p "$RESULTS"

sql() {
  docker compose exec -T postgres psql -U nexbid -d nexbid -tAq -c "$1"
}

redis_stat() {
  docker compose exec -T redis redis-cli INFO stats | tr -d '\r' | awk -F: -v key="$1" '$1 == key { print $2 }'
}

docker compose exec -T postgres psql -U nexbid -d nexbid -v ON_ERROR_STOP=1 -q < docs/load-test/seed.sql

for vus in ${LEVELS:-10 100 500 1000}; do
  hits_before=$(redis_stat keyspace_hits)
  misses_before=$(redis_stat keyspace_misses)

  # EN: Once a second: the backend's JDBC connections, and how many are busy on a query right now.
  # VI: Mỗi giây một lần: số kết nối JDBC của backend, và bao nhiêu kết nối đang bận chạy truy vấn.
  samples=$(mktemp)
  (
    while true; do
      sql "SELECT count(*), count(*) FILTER (WHERE state = 'active')
             FROM pg_stat_activity WHERE application_name = 'PostgreSQL JDBC Driver'" >> "$samples" || true
      sleep 1
    done
  ) &
  sampler=$!

  docker compose --profile load-test run --rm -e VUS="$vus" k6 || true

  kill "$sampler" 2>/dev/null || true
  wait "$sampler" 2>/dev/null || true

  hits=$(( $(redis_stat keyspace_hits) - hits_before ))
  misses=$(( $(redis_stat keyspace_misses) - misses_before ))
  peak=$(awk -F'|' 'BEGIN { m = 0 } $1 > m { m = $1 } END { print m }' "$samples")
  busy=$(awk -F'|' 'BEGIN { m = 0 } $2 > m { m = $2 } END { print m }' "$samples")
  rm -f "$samples"

  cat > "$RESULTS/$vus-vus-infra.json" <<EOF
{
  "vus": $vus,
  "peak_db_connections": $peak,
  "peak_busy_db_connections": $busy,
  "redis_hits": $hits,
  "redis_misses": $misses,
  "redis_hit_rate": $(awk -v h="$hits" -v m="$misses" 'BEGIN { printf "%.4f", (h + m) ? h / (h + m) : 0 }')
}
EOF
  echo "  db connections: peak $peak, busy at once $busy; redis hit rate $hits/$((hits + misses))"
done
