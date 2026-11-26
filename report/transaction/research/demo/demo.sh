#!/usr/bin/env bash
# Transaction Demo Kit — entrypoint duy nhất. Chạy "./demo.sh help" để xem lệnh và danh sách scene.
# Mọi thao tác ghi đều xác minh container/volume/port của project shopflashdemo trước khi chạy.
set -euo pipefail

DEMO_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$DEMO_DIR/../../../.." && pwd)
RESEARCH=report/transaction/research
RT=$DEMO_DIR/runtime
ENV_FILE=$RT/demo.env
MANIFEST=$RT/manifest.env
KEYS_FILE=$RT/redis-keys.txt
PROJECT=shopflashdemo
MAIN_PROJECT=shopflash
RUN_ID=demo
ORA_PORT=1522
REDIS_PORT=6381
DEMO_REAL_DOCKER=$(command -v docker)
export DEMO_REAL_DOCKER DEMO_DIR

die()  { printf '\033[31mLỖI:\033[0m %b\n' "$*" >&2; exit 1; }
info() { printf '\033[36m==>\033[0m %s\n' "$*"; }
play() { python3 "$DEMO_DIR/lib/play.py" "$@"; }

[[ -f "$ROOT/compose.yaml" && -d "$ROOT/$RESEARCH" ]] || die "Không tìm thấy repo ShopFlash từ $DEMO_DIR"
cd "$ROOT"  # script nghiên cứu dùng đường dẫn tương đối từ root repo

# ---------- Môi trường Docker riêng ----------

new_env_file() {
  [[ -f "$ENV_FILE" ]] && return
  mkdir -p "$RT"
  (umask 077; python3 - "$ENV_FILE" <<'EOF'
import secrets, string, sys
a = string.ascii_letters + string.digits
pw = lambda: 'D' + ''.join(secrets.choice(a) for _ in range(23))
open(sys.argv[1], 'w').write(
    "# Sinh bởi demo.sh init; chỉ dành cho môi trường shopflashdemo. Không commit.\n"
    "COMPOSE_PROJECT_NAME=shopflashdemo\nORACLE_PORT=1522\nREDIS_PORT=6381\n"
    f"ORACLE_ADMIN_PASSWORD={pw()}\nORACLE_APP_PASSWORD={pw()}\nREDIS_PASSWORD={pw()}\n")
EOF
  )
  info "Đã sinh mật khẩu ngẫu nhiên vào $ENV_FILE (chmod 600)"
}

compose() {
  [[ -f "$ENV_FILE" ]] || die "Chưa có $ENV_FILE; chạy ./demo.sh init"
  # env -i: biến export trong shell và file .env của project chính không thể ghi đè cấu hình demo.
  env -i PATH="$PATH" HOME="$HOME" ${DOCKER_HOST:+"DOCKER_HOST=$DOCKER_HOST"} \
    ${DOCKER_CONTEXT:+"DOCKER_CONTEXT=$DOCKER_CONTEXT"} ${DOCKER_CONFIG:+"DOCKER_CONFIG=$DOCKER_CONFIG"} \
    "$DEMO_REAL_DOCKER" compose -p "$PROJECT" --project-directory "$ROOT" -f "$ROOT/compose.yaml" \
    --env-file "$ENV_FILE" "$@"
}

find_ctr() {
  local ids
  ids=$("$DEMO_REAL_DOCKER" ps -q --no-trunc --filter "label=com.docker.compose.project=$PROJECT" \
        --filter "label=com.docker.compose.service=$1")
  [[ -n "$ids" && $(wc -l <<<"$ids") == 1 ]] || die "Không thấy đúng 1 container $PROJECT/$1 đang chạy. Chạy ./demo.sh up"
  echo "$ids"
}

# project|volume:đích ...|port|network|health — đọc từ docker inspect, không tin tên biến môi trường.
check_ctr() {
  local id=$1 volume=$2 dest=$3 port=$4 got vol_project
  got=$("$DEMO_REAL_DOCKER" inspect -f '{{index .Config.Labels "com.docker.compose.project"}}|{{range .Mounts}}{{if eq .Type "volume"}}{{.Name}}:{{.Destination}} {{end}}{{end}}|{{range $p, $b := .NetworkSettings.Ports}}{{range $b}}{{$p}}>{{.HostIp}}:{{.HostPort}} {{end}}{{end}}|{{range $n, $v := .NetworkSettings.Networks}}{{$n}} {{end}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}' "$id")
  IFS='|' read -r g_project g_vols g_ports g_nets g_health <<<"$got"
  [[ "$g_project" == "$PROJECT" ]] || die "Container $id không thuộc project $PROJECT: $g_project"
  [[ " $g_vols" == *" $volume:$dest "* ]] || die "Container $id không mount $volume:$dest (thấy: $g_vols)"
  [[ " $g_vols" != *" ${MAIN_PROJECT}_"* ]] || die "Container $id mount volume của project chính: $g_vols"
  [[ "$g_ports" == "$port " ]] || die "Container $id publish port khác cấu hình demo: '$g_ports' (cần '$port')"
  [[ "$g_nets" == "${PROJECT}_default " ]] || die "Container $id nằm ngoài network ${PROJECT}_default: $g_nets"
  [[ "$g_health" == healthy ]] || die "Container $id chưa healthy ($g_health)"
  vol_project=$("$DEMO_REAL_DOCKER" volume inspect -f '{{index .Labels "com.docker.compose.project"}}' "$volume")
  [[ "$vol_project" == "$PROJECT" ]] || die "Volume $volume không thuộc project $PROJECT"
}

verify_target() {
  local ora red c
  ora=$(find_ctr oracle); red=$(find_ctr redis)
  check_ctr "$ora" "${PROJECT}_oracle_data" /opt/oracle/oradata "1521/tcp>127.0.0.1:$ORA_PORT"
  check_ctr "$red" "${PROJECT}_redis_data" /data "6379/tcp>127.0.0.1:$REDIS_PORT"
  for c in $("$DEMO_REAL_DOCKER" ps -aq --no-trunc --filter "label=com.docker.compose.project=$MAIN_PROJECT"); do
    [[ "$c" != "$ora" && "$c" != "$red" ]] || die "Container demo trùng container của project chính"
  done
  export DEMO_ORA_CTR=$ora DEMO_REDIS_CTR=$red
}

with_shim() { PATH="$DEMO_DIR/lib/bin:$PATH" "$@"; }
orasql()    { with_shim bash "$RESEARCH/oracle/run-sql.sh" "$RUN_ID" "$@"; }
rcli()      { "$DEMO_REAL_DOCKER" exec -i "$DEMO_REDIS_CTR" redis-cli "$@" </dev/null; }

cmd_up()   { new_env_file; info "Khởi động project $PROJECT (Oracle 127.0.0.1:$ORA_PORT, Redis 127.0.0.1:$REDIS_PORT)"; compose up -d --wait; verify_target; info "Target đã xác minh."; }
cmd_down() { info "Dừng project $PROJECT, giữ volume demo"; compose stop; }

cmd_destroy() {
  [[ "$PROJECT" == shopflashdemo ]] || die "Từ chối: project không phải shopflashdemo"
  printf 'Xóa container, network và volume CHỈ của project %s. Gõ "%s" để xác nhận: ' "$PROJECT" "$PROJECT"
  read -r answer; [[ "$answer" == "$PROJECT" ]] || die "Đã hủy"
  compose down --volumes
  rm -f "$MANIFEST" "$KEYS_FILE"; rm -rf "$RT/oracle"
}

# ---------- Flyway qua backend, chỉ trỏ vào DB demo ----------

flyway_current() { orasql "$DEMO_DIR/oracle/lib/flyway.sql" >/dev/null 2>&1; }

cmd_migrate() {
  verify_target
  local log=$RT/migrate.log pid i
  info "Khởi tạo RediSearch index trên Redis demo"
  "$DEMO_REAL_DOCKER" exec -i "$DEMO_REDIS_CTR" redis-cli -e --eval /opt/shopflash/redis/init.lua </dev/null
  info "Chạy backend Spring Boot chỉ để Flyway migrate, trỏ tới Oracle :$ORA_PORT và Redis :$REDIS_PORT (demo)"
  (
    set -a; . "$ENV_FILE"; set +a
    unset ORACLE_ADMIN_PASSWORD COMPOSE_PROJECT_NAME
    # Biến môi trường thắng ../.env mà application.properties import: đặt đủ mọi biến nó đọc.
    export ORACLE_HOST=localhost ORACLE_PORT=$ORA_PORT ORACLE_DATABASE=FREEPDB1 ORACLE_SCHEMA=SHOP \
           REDIS_HOST=localhost REDIS_PORT=$REDIS_PORT REDIS_DATABASE=0 SERVER_ADDRESS=127.0.0.1
    JWT_SECRET_BASE64=$(head -c 32 /dev/urandom | base64); export JWT_SECRET_BASE64
    # Máy có thể có java (JRE) mới hơn javac: Maven cần JDK chứa javac.
    if [[ -z "${JAVA_HOME:-}" ]] && command -v javac >/dev/null; then
      JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")"); export JAVA_HOME
    fi
    cd backend
    exec setsid ./mvnw -q spring-boot:run "-Dspring-boot.run.arguments=--server.port=0 --spring.datasource.url=jdbc:oracle:thin:@//localhost:$ORA_PORT/FREEPDB1 --spring.data.redis.host=localhost --spring.data.redis.port=$REDIS_PORT"
  ) >"$log" 2>&1 &
  pid=$!
  for ((i = 0; i < 600; i++)); do
    grep -q 'Started .* in ' "$log" && break
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  kill -- "-$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  grep -E 'Database: jdbc|Successfully applied|Schema .* is up to date|Started ' "$log" || true
  grep -q 'Started .* in ' "$log" || die "Backend không khởi động xong; xem $log:\n$(grep -m5 -E 'ERROR|Exception' "$log")"
  grep -q "jdbc:oracle:thin:@//localhost:$ORA_PORT/FREEPDB1" "$log" || die "Log không xác nhận Flyway dùng DB demo; xem $log"
  flyway_current || die "Flyway history trên DB demo chưa hợp lệ"
  info "Đã dừng backend. Flyway history (DB demo):"
  orasql "$DEMO_DIR/oracle/lib/flyway.sql"
}

# ---------- Fixture: tái sử dụng setup/reset/cleanup của research ----------

declare -A FX=()

load_lookup() {
  local out line
  out=$(orasql "$DEMO_DIR/oracle/lib/lookup.sql" | tr -d '\r') || die "Không đọc được fixture Oracle:\n$out"
  FX=()
  while IFS= read -r line; do
    [[ "$line" =~ ^([A-Z0-9_]+)=(.*)$ ]] || continue
    if [[ ${BASH_REMATCH[1]} == OPEN_TX ]]; then FX[OPEN_TX]+="${BASH_REMATCH[2]}"$'\n'
    else FX[${BASH_REMATCH[1]}]=${BASH_REMATCH[2]}; fi
  done <<<"$out"
}

require_no_open_tx() {
  [[ -z "${FX[OPEN_TX]:-}" ]] && return
  die "Có session SHOP khác còn transaction mở (giữ khóa), reset sẽ bị chặn:\n${FX[OPEN_TX]}Xử lý: ROLLBACK (hoặc COMMIT) trong DBeaver editor đó, rồi chạy lại.\nNếu không tìm được editor: ./demo.sh unlock (kill session trong container demo)."
}

write_manifest() {
  local p1=${FX[P1]}
  for v in USER_ID CATEGORY_ID CART_ID P1 P2 P3; do [[ "${FX[$v]:-}" =~ ^[0-9]+$ ]] || die "ID fixture không hợp lệ: $v"; done
  if [[ -f "$MANIFEST" ]]; then
    local old; old=$(grep -E '^(USER_ID|CATEGORY_ID|CART_ID|P1|P2|P3)=' "$MANIFEST" | tr '\n' ' ')
    local new="USER_ID=${FX[USER_ID]} CATEGORY_ID=${FX[CATEGORY_ID]} CART_ID=${FX[CART_ID]} P1=${FX[P1]} P2=${FX[P2]} P3=${FX[P3]} "
    if [[ "$old" != "$new" ]]; then
      info "ID Oracle khác manifest cũ ($old) — dọn key Redis của manifest cũ trước"
      [[ -f "$KEYS_FILE" ]] && with_shim bash "$RESEARCH/redis/cleanup.sh" "$RUN_ID" "$KEYS_FILE"
    fi
  fi
  cat >"$MANIFEST" <<EOF
# Manifest fixture demo (không chứa mật khẩu). Ghi bởi demo.sh $(date -Is)
RUN_ID=$RUN_ID
TAG=TXRESEARCH_${RUN_ID^^}
USER_ID=${FX[USER_ID]}
CATEGORY_ID=${FX[CATEGORY_ID]}
CART_ID=${FX[CART_ID]}
P1=${FX[P1]}
P2=${FX[P2]}
P3=${FX[P3]}
ORACLE_CONTAINER=$DEMO_ORA_CTR
REDIS_CONTAINER=$DEMO_REDIS_CTR
REDIS_PREFIX=txresearch:$RUN_ID:
EOF
  # Bảy key chính xác như manifest revision-20261109 (thêm product:<p1> cho C2 và order).
  local pre="txresearch:$RUN_ID:"
  printf '%s\n' "${pre}cart" "${pre}inventory:${FX[P1]}" "${pre}inventory:${FX[P2]}" "${pre}inventory:${FX[P3]}" \
    "${pre}quota" "${pre}order" "${pre}product:$p1" >"$KEYS_FILE"
}

ids6() { echo "${FX[USER_ID]} ${FX[CATEGORY_ID]} ${FX[CART_ID]} ${FX[P1]} ${FX[P2]} ${FX[P3]}"; }

reset_oracle() {
  verify_target; load_lookup; require_no_open_tx
  if [[ "${FX[FIXTURE]:-}" == ABSENT ]]; then
    info "Oracle: chưa có fixture run '$RUN_ID' → setup.sql"
    orasql "$RESEARCH/oracle/setup.sql" | grep -E '^(RESULT|[A-Z_0-9]+_ID)='
    load_lookup
  else
    info "Oracle: Order đã COMMIT của user ${FX[USER_ID]}: ${FX[ORDER_IDS]} (reset.sql đối chiếu user + mã TXRESEARCH_${RUN_ID^^})"
    # shellcheck disable=SC2046
    orasql "$RESEARCH/oracle/reset.sql" $(ids6) "${FX[ORDER_IDS]}" | grep -E '^RESET_' || die "reset.sql thất bại"
  fi
  write_manifest
  play render >/dev/null
}

reset_redis() {
  verify_target
  [[ -f "$MANIFEST" ]] || reset_oracle
  load_lookup
  [[ "${FX[FIXTURE]:-}" == PRESENT ]] || die "Chưa có fixture Oracle; chạy ./demo.sh reset oracle"
  write_manifest
  info "Redis: reset.sh (DEL đúng 7 key manifest rồi setup.sh) + Product Hash cho Lua C2"
  with_shim bash "$RESEARCH/redis/reset.sh" "$RUN_ID" "$KEYS_FILE" "${FX[P1]}" "${FX[P2]}" "${FX[P3]}" | grep -E '^(RESULT|CLEANUP_DONE)'
  [[ $(rcli HSET "txresearch:$RUN_ID:product:${FX[P1]}" product_id "${FX[P1]}" status ACTIVE) == 2 ]] \
    || die "Không tạo được Product Hash"
}

cmd_reset() {
  case "${1:-all}" in
    oracle) reset_oracle ;;
    redis)  reset_redis ;;
    all)    reset_oracle; reset_redis ;;
    *) die "reset oracle|redis|all" ;;
  esac
  info "Baseline: P1/P2/P3 tồn 100, Cart 3 dòng × 1, chưa có Order."
}

cmd_unlock() {
  verify_target
  info "Session SHOP còn transaction mở trong container demo (sẽ bị kill):"
  # ALTER SYSTEM không nhận bind variable; sid/serial# là số đọc từ v\$session, không phải input người dùng.
  "$DEMO_REAL_DOCKER" exec -i "$DEMO_ORA_CTR" bash -c 'sqlplus -s / as sysdba' <<'SQL'
ALTER SESSION SET CONTAINER = FREEPDB1;
SET SERVEROUTPUT ON FEEDBACK OFF
BEGIN
  FOR r IN (SELECT s.sid, s.serial#, s.program FROM v$session s JOIN v$transaction t ON t.addr = s.taddr
            WHERE s.username = 'SHOP') LOOP
    DBMS_OUTPUT.PUT_LINE('KILL sid ' || r.sid || ',' || r.serial# || ' program=' || r.program);
    EXECUTE IMMEDIATE 'ALTER SYSTEM KILL SESSION ''' || r.sid || ',' || r.serial# || ''' IMMEDIATE';
  END LOOP;
END;
/
EXIT
SQL
  info "Xong. Editor DBeaver bị kill sẽ báo mất kết nối: bấm reconnect (Oracle đã rollback transaction đó)."
}

cmd_cleanup() {
  verify_target; load_lookup; require_no_open_tx
  if [[ "${FX[FIXTURE]:-}" == PRESENT ]]; then
    write_manifest
    info "Oracle cleanup-preview → cleanup → verify-absent (Order: ${FX[ORDER_IDS]})"
    # shellcheck disable=SC2046
    orasql "$RESEARCH/oracle/cleanup-preview.sql" $(ids6) "${FX[ORDER_IDS]}" | grep -vE '^$|PL/SQL'
    # shellcheck disable=SC2046
    orasql "$RESEARCH/oracle/cleanup.sql" $(ids6) "${FX[ORDER_IDS]}" | grep -E '^CLEANUP_' || die "cleanup.sql thất bại"
    # shellcheck disable=SC2046
    orasql "$RESEARCH/oracle/verify-absent.sql" $(ids6) "${FX[ORDER_IDS]}" | grep -E '^VERIFY_' || die "verify-absent thất bại"
  else
    info "Oracle: không còn fixture run '$RUN_ID'"
  fi
  if [[ -f "$KEYS_FILE" ]]; then
    info "Redis cleanup.sh (DEL đúng key manifest) → preview.sh"
    with_shim bash "$RESEARCH/redis/cleanup.sh" "$RUN_ID" "$KEYS_FILE"
    with_shim bash "$RESEARCH/redis/preview.sh" "$RUN_ID" "$KEYS_FILE"
  fi
  rm -f "$MANIFEST" "$KEYS_FILE"; rm -rf "$RT/oracle"
  info "Cleanup xong. Container/volume demo vẫn giữ; ./demo.sh down để dừng, ./demo.sh destroy để xóa hẳn."
}

# ---------- Chạy lại script nghiên cứu gốc (đối chiếu) ----------

cmd_reference() {
  verify_target
  local out; out=$RT/reference/$(date +%Y%m%d-%H%M%S); mkdir -p "$out"
  local exp=$RESEARCH/oracle/experiments rexp=$RESEARCH/redis/experiments
  run_ref() { local name=$1; shift; info "reference $name"; "$@" >"$out/$name.txt" 2>&1 || { cat "$out/$name.txt"; die "$name thất bại"; }; }
  reset_oracle >/dev/null
  # shellcheck disable=SC2046
  run_ref A1-commit orasql "$exp/A1-commit.sql" $(ids6) '[]'
  run_ref A1-after-commit orasql "$RESEARCH/oracle/snapshot.sql"
  # shellcheck disable=SC2046
  run_ref A1-rollback orasql "$exp/A1-rollback.sql" $(ids6) '[]'
  run_ref A1-after-rollback orasql "$RESEARCH/oracle/snapshot.sql"
  reset_oracle >/dev/null
  # shellcheck disable=SC2046
  run_ref A3 orasql "$exp/A3.sql" $(ids6) '[]'
  # shellcheck disable=SC2046
  run_ref D1 orasql "$exp/D1.sql" $(ids6) '[]'
  run_ref B1 with_shim python3 "$exp/two-session.py" B1 "${FX[P1]}" "${FX[P2]}"
  run_ref oracle-after orasql "$RESEARCH/oracle/snapshot.sql"
  reset_redis >/dev/null
  run_ref A4-exec with_shim bash "$rexp/A4.sh" exec "$RUN_ID" "${FX[USER_ID]}" "${FX[P1]}"
  run_ref A4-exec-after with_shim bash "$RESEARCH/redis/snapshot.sh" "$RUN_ID" "${FX[P1]}" "${FX[P2]}" "${FX[P3]}"
  reset_redis >/dev/null
  run_ref A4-discard with_shim bash "$rexp/A4.sh" discard "$RUN_ID" "${FX[USER_ID]}" "${FX[P1]}"
  run_ref A4-discard-after with_shim bash "$RESEARCH/redis/snapshot.sh" "$RUN_ID" "${FX[P1]}" "${FX[P2]}" "${FX[P3]}"
  reset_redis >/dev/null
  run_ref A5-runtime with_shim bash "$rexp/A5.sh" runtime "$RUN_ID" "${FX[P1]}"
  run_ref A5-runtime-after with_shim bash "$RESEARCH/redis/snapshot.sh" "$RUN_ID" "${FX[P1]}" "${FX[P2]}" "${FX[P3]}"
  reset_redis >/dev/null
  run_ref B4 with_shim python3 "$rexp/B4.py" "$RUN_ID" "${FX[P1]}"
  reset_redis >/dev/null
  run_ref C2 with_shim bash "$rexp/C2.sh" "$RUN_ID" "${FX[P1]}" "${FX[P2]}" "${FX[P3]}"
  reset_redis >/dev/null
  info "Transcript script gốc: $out"
}

# ---------- Thông tin ----------

ora_host() { "$DEMO_REAL_DOCKER" inspect -f '{{.Config.Hostname}}' "$(find_ctr oracle)"; }

cmd_dbeaver() {
  cat <<EOF
DBeaver → New Connection → Oracle:
  Host: localhost   Port: $ORA_PORT   Database: FREEPDB1 (chọn "Service Name")   User: SHOP
  Password: dòng ORACLE_APP_PASSWORD trong
            $ENV_FILE
  Đặt tên kết nối: "ShopFlash DEMO :$ORA_PORT" để không nhầm với ShopFlash chính (:1521).
  Connection settings → Initialization: bỏ chọn Auto-commit (Manual commit).
  Kiểm tra đúng DB: STEP 0/1 của mỗi file in SERVER_HOST = $(ora_host) (container Oracle demo).
Redis (tùy chọn GUI): RedisInsight → Add database → 127.0.0.1:$REDIS_PORT, password: dòng REDIS_PASSWORD trong file trên.
Redis CLI (miễn phí, luôn dùng được): ./demo.sh redis-cli
EOF
}

cmd_status() {
  verify_target
  info "Target đã xác minh: project $PROJECT"
  "$DEMO_REAL_DOCKER" ps --filter "label=com.docker.compose.project=$PROJECT" --format '  {{.Names}}  {{.Status}}  {{.Ports}}'
  printf '  volumes: %s_oracle_data, %s_redis_data | network: %s_default\n' "$PROJECT" "$PROJECT" "$PROJECT"
  printf '  Oracle demo SERVER_HOST (so với cột SERVER_HOST trong DBeaver): %s\n' "$(ora_host)"
  if flyway_current; then info "Flyway (DB demo):"; orasql "$DEMO_DIR/oracle/lib/flyway.sql" | sed 's/^/  /'
  else info "Flyway chưa migrate trên DB demo: ./demo.sh migrate"; return; fi
  load_lookup
  [[ -n "${FX[OPEN_TX]:-}" ]] && printf '\033[33mCảnh báo — session còn transaction mở:\033[0m\n%s' "${FX[OPEN_TX]}"
  if [[ "${FX[FIXTURE]:-}" != PRESENT ]]; then info "Chưa có fixture: ./demo.sh reset all"; return; fi
  [[ -f "$MANIFEST" ]] && { info "Manifest $MANIFEST"; grep -vE '^#|CONTAINER' "$MANIFEST" | sed 's/^/  /'; }
  play show oracle
  play show redis
}

cmd_help() {
  cat <<EOF
Transaction Demo Kit — ShopFlash (Oracle :$ORA_PORT, Redis :$REDIS_PORT, project $PROJECT)

Chuẩn bị (một lần):
  ./demo.sh init              tạo env demo, khởi động DB riêng, Flyway migrate, tạo fixture
  ./demo.sh status            xác minh target + trạng thái fixture Oracle/Redis
  ./demo.sh dbeaver           thông số kết nối DBeaver / RedisInsight

Mỗi scene:
  ./demo.sh prepare <scene>   reset baseline của DB liên quan, render SQL, in hướng dẫn
  ./demo.sh play <scene>      chạy từng bước (Enter = bước tiếp); Oracle: phương án CLI dự phòng
  ./demo.sh lua <quantity>    EVAL C2-validate.lua với quantity, in BEFORE/COMMAND/RESPONSE/AFTER
  ./demo.sh show oracle|redis trạng thái hiện tại (đọc bằng session/client độc lập)
  ./demo.sh reset oracle|redis|all

Scene:
$(play list)

Phục hồi / dọn dẹp:
  ./demo.sh unlock            kill session SHOP còn transaction mở (chỉ container demo)
  ./demo.sh redis-cli         redis-cli tương tác trên Redis demo
  ./demo.sh cleanup           xóa fixture theo manifest (preview → cleanup → verify)
  ./demo.sh down | up         dừng / khởi động lại DB demo (giữ volume)
  ./demo.sh destroy           xóa container + volume CHỈ của project $PROJECT
  ./demo.sh reference         chạy lại script nghiên cứu gốc trên DB demo (đối chiếu)
  ./demo.sh selftest          kiểm thử toàn bộ kit (8 scene × 2 lượt + tình huống bất thường)

Runbook: $DEMO_DIR/DEMO_RUNBOOK.md
EOF
}

cmd_init() {
  cmd_up
  if flyway_current; then info "Flyway đã migrate trên DB demo"; else cmd_migrate; fi
  cmd_reset all
  play show oracle
  play show redis
  info "Sẵn sàng. Tiếp theo: ./demo.sh dbeaver, rồi ./demo.sh prepare 1"
}

cmd_prepare() {
  local scene=${1:?prepare <scene>} db
  db=$(play db "$scene") || exit 1
  case "$db" in
    oracle) reset_oracle ;;
    redis)  reset_redis ;;
    both)   reset_oracle; reset_redis ;;
  esac
  play info "$scene"
}

main() {
  local cmd=${1:-help}; shift || true
  case "$cmd" in
    help|-h|--help) cmd_help ;;
    init)      cmd_init ;;
    up)        cmd_up ;;
    down)      cmd_down ;;
    destroy)   cmd_destroy ;;
    migrate)   cmd_migrate ;;
    status)    cmd_status ;;
    dbeaver)   cmd_dbeaver ;;
    prepare)   cmd_prepare "$@" ;;
    reset)     cmd_reset "$@" ;;
    unlock)    cmd_unlock ;;
    cleanup)   cmd_cleanup ;;
    reference) cmd_reference ;;
    show|play|lua|selftest|info)
      verify_target
      [[ -f "$MANIFEST" ]] || die "Chưa có fixture: ./demo.sh reset all"
      play "$cmd" "$@" ;;
    redis-cli) verify_target; exec "$DEMO_REAL_DOCKER" exec -it "$DEMO_REDIS_CTR" redis-cli ;;
    *) cmd_help; exit 2 ;;
  esac
}
main "$@"
