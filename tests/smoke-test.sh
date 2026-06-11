#!/usr/bin/env bash
# live docker compose dashboard
# Usage: ./smoke-test.sh [docker-compose-file]

COMPOSE_FILE="${1:-docker-compose.yml}"
TIMEOUT="${TIMEOUT:-120}"   # seconds to wait for healthy before giving up

# ── colours ──────────────────────────────────────────────────────────────────
R="\033[0;31m" G="\033[0;32m" Y="\033[0;33m" B="\033[0;34m"
W="\033[1;37m" DIM="\033[2m" RESET="\033[0m"
SPIN_FRAMES=("⠋" "⠙" "⠹" "⠸" "⠼" "⠴" "⠦" "⠧" "⠇" "⠏")

# ── state ─────────────────────────────────────────────────────────────────────
declare -A SVC_STATUS   # pending | starting | running | healthy | unhealthy | exited
declare -A SVC_HEALTH
declare -a SERVICES
declare -a LOG_LINES
LOG_MAX=8
START_TS=$(date +%s)
SPIN_IDX=0
DASHBOARD_LINES=0
LOG_PID=""

# ── terminal helpers ──────────────────────────────────────────────────────────
move_up()      { tput cuu "$1"  2>/dev/null || true; }
clr_eos()      { tput ed        2>/dev/null || true; }
hide_cursor()  { tput civis     2>/dev/null || true; }
show_cursor()  { tput cnorm     2>/dev/null || true; }

elapsed() {
  local s=$(( $(date +%s) - START_TS ))
  printf "%dm%02ds" $(( s/60 )) $(( s%60 ))
}

spin() {
  local c="${SPIN_FRAMES[$((SPIN_IDX % ${#SPIN_FRAMES[@]}))]}"
  (( SPIN_IDX++ )) || true
  echo -e "$c"
}

push_log() {
  # strip ANSI, truncate
  local clean
  clean=$(echo -e "$1" | sed 's/\x1b\[[0-9;]*m//g')
  LOG_LINES+=("${clean:0:100}")
  if (( ${#LOG_LINES[@]} > LOG_MAX )); then
    LOG_LINES=("${LOG_LINES[@]:1}")
  fi
}

# ── discover ──────────────────────────────────────────────────────────────────
discover_services() {
  mapfile -t SERVICES < <(
    docker compose -f "$COMPOSE_FILE" config --services 2>/dev/null | sort
  )
  for svc in "${SERVICES[@]}"; do
    SVC_STATUS[$svc]="pending"
    SVC_HEALTH[$svc]=""
  done
}

# ── poll ──────────────────────────────────────────────────────────────────────
poll_statuses() {
  local ids
  ids=$(docker compose -f "$COMPOSE_FILE" ps -q 2>/dev/null) || return 0
  [[ -z "$ids" ]] && return 0

  # single docker inspect call for all containers
  while IFS='|' read -r cname cstatus chealth; do
    cname="${cname#/}"
    cstatus=$(echo -e "$cstatus" | tr '[:upper:]' '[:lower:]' | xargs)
    chealth=$(echo -e "$chealth" | tr '[:upper:]' '[:lower:]' | xargs)

    local svc
    svc=$(docker inspect "$cname" \
      --format '{{index .Config.Labels "com.docker.compose.service"}}' 2>/dev/null) || continue
    [[ -z "$svc" ]] && continue

    SVC_HEALTH[$svc]="$chealth"

    if   [[ "$cstatus" == *exit*     ]]; then SVC_STATUS[$svc]="exited"
    elif [[ "$chealth" == "healthy"  ]]; then SVC_STATUS[$svc]="healthy"
    elif [[ "$chealth" == "unhealthy" ]]; then SVC_STATUS[$svc]="unhealthy"
    elif [[ "$cstatus" == "running"  ]]; then SVC_STATUS[$svc]="running"
    else                                      SVC_STATUS[$svc]="starting"
    fi
  done < <(
    # shellcheck disable=SC2086
    xargs docker inspect \
      --format '{{.Name}}|{{.State.Status}}|{{.State.Health.Status}}' \
      <<< "$ids" 2>/dev/null
  )
}

# ── all done? ─────────────────────────────────────────────────────────────────
all_settled() {
  for svc in "${SERVICES[@]}"; do
    case "${SVC_STATUS[$svc]}" in
      healthy|unhealthy|exited) ;;
      *) return 1 ;;
    esac
  done
  return 0
}

any_failed() {
  for svc in "${SERVICES[@]}"; do
    case "${SVC_STATUS[$svc]}" in
      unhealthy|exited) return 0 ;;
    esac
  done
  return 1
}

# ── draw ──────────────────────────────────────────────────────────────────────
draw_dashboard() {
  local cols
  cols=$(tput cols 2>/dev/null || echo -e 80)
  local sep
  sep=$(printf '─%.0s' $(seq 1 "$cols"))

  local healthy=0 failed=0 total=${#SERVICES[@]}
  for svc in "${SERVICES[@]}"; do
    case "${SVC_STATUS[$svc]}" in
      healthy)          (( healthy++ )) ;;
      unhealthy|exited) (( failed++  )) ;;
    esac
  done

  if (( DASHBOARD_LINES > 0 )); then
    move_up "$DASHBOARD_LINES"
    clr_eos
  fi

  local out="" lc=0
  p() { out+="$1"$'\n'; (( lc++ )); }

  p "${W}  docker compose up${RESET}  ${DIM}${COMPOSE_FILE}${RESET}  ${DIM}$(elapsed)${RESET}"
  p "${DIM}${sep}${RESET}"
  p ""

  for svc in "${SERVICES[@]}"; do
    local st="${SVC_STATUS[$svc]}"
    local icon lbl
    case "$st" in
      healthy)   icon="${G}✔${RESET}" ; lbl="${G}healthy${RESET}" ;;
      unhealthy) icon="${R}✖${RESET}" ; lbl="${R}unhealthy${RESET}" ;;
      exited)    icon="${R}✖${RESET}" ; lbl="${R}exited${RESET}" ;;
      running)   icon="${B}$(spin)${RESET}" ; lbl="${B}running${RESET}" ;;
      starting)  icon="${Y}$(spin)${RESET}" ; lbl="${Y}starting…${RESET}" ;;
      *)         icon="${DIM}·${RESET}"     ; lbl="${DIM}pending${RESET}" ;;
    esac

    local hc="${SVC_HEALTH[$svc]:-}"
    local hc_str=""
    # only show healthcheck label when it adds info beyond the status
    [[ -n "$hc" && "$hc" != "healthy" && "$hc" != "unhealthy" ]] \
      && hc_str="  ${DIM}(hc: ${hc})${RESET}"

    p "  ${icon}  ${W}${svc}${RESET}  ${lbl}${hc_str}"
  done

  p ""
  p "${DIM}${sep}${RESET}"

  if (( failed > 0 )); then
    p "  ${R}${failed} failed${RESET}  ·  ${healthy}/${total} healthy  ·  Ctrl-C to stop"
  elif (( healthy == total )); then
    p "  ${G}All ${total} services healthy ✔${RESET}  ·  Ctrl-C to stop"
  else
    local remaining=$(( total - healthy - failed ))
    p "  ${Y}${healthy}/${total} healthy${RESET}  ·  ${remaining} still starting  ·  Ctrl-C to stop"
  fi

  p ""
  p "  ${DIM}recent logs${RESET}"
  local shown=0
  for (( i=${#LOG_LINES[@]}-1; i>=0 && shown < LOG_MAX; i-- )); do
    p "  ${DIM}${LOG_LINES[$i]}${RESET}"
    (( shown++ ))
  done
  while (( shown < LOG_MAX )); do   # keep height stable
    p ""
    (( shown++ ))
  done

  printf "%s" "$out"
  DASHBOARD_LINES=$lc
}

# ── background log tail ───────────────────────────────────────────────────────
start_log_tail() {
  docker compose -f "$COMPOSE_FILE" logs --follow --no-color --tail=0 2>&1 \
    | while IFS= read -r line; do push_log "$line"; done &
  LOG_PID=$!
}

# ── cleanup ───────────────────────────────────────────────────────────────────
cleanup() {
  show_cursor
  [[ -n "$LOG_PID" ]] && kill "$LOG_PID" 2>/dev/null || true
  echo -e ""
}
trap cleanup EXIT INT TERM

# ── main ──────────────────────────────────────────────────────────────────────
main() {
  if [[ ! -f "$COMPOSE_FILE" ]]; then
    echo -e -e "${R}File not found: ${COMPOSE_FILE}${RESET}" >&2; exit 1
  fi

  hide_cursor
  clear

  echo -e -e "${W}Reading ${COMPOSE_FILE}…${RESET}"
  discover_services

  if (( ${#SERVICES[@]} == 0 )); then
    echo -e -e "${R}No services found.${RESET}" >&2; exit 1
  fi

  echo -e -e "${DIM}Services: ${SERVICES[*]}${RESET}"
  echo -e -e "${B}Starting (detached, no rebuild)…${RESET}"

  # --detach: compose starts containers and exits immediately — no flood
  docker compose -f "$COMPOSE_FILE" up --detach 2>&1 | while IFS= read -r l; do
    push_log "$l"
    echo -e -e "  ${DIM}${l}${RESET}"
  done

  echo -e ""
  start_log_tail

  clear
  DASHBOARD_LINES=0

  local deadline=$(( START_TS + TIMEOUT ))

  while true; do
    poll_statuses
    draw_dashboard

    if all_settled; then
      break
    fi

    if (( $(date +%s) > deadline )); then
      echo -e -e "\n${R}Timed out after ${TIMEOUT}s — some services never became healthy.${RESET}"
      break
    fi

    sleep 1
  done

  # final draw with settled state
  poll_statuses
  draw_dashboard

  echo -e ""
  if any_failed; then
    echo -e -e "${R}Some services failed. Run: docker compose -f ${COMPOSE_FILE} logs <service>${RESET}"
    exit 1
  else
    echo -e -e "${G}All services healthy.${RESET}"
  fi
}

main