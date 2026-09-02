#!/usr/bin/env bash

set -euo pipefail

STATUS_SYNC_SKIP_CODE=10

notice() {
  echo "::notice::$1" >&2
}

warning() {
  echo "::warning::$1" >&2
}

error() {
  echo "::error::$1" >&2
  exit 1
}

require_env() {
  local name="$1"
  local value="${!name:-}"
  if [ -z "$value" ]; then
    error "${name} is required"
  fi
}

bool_value() {
  case "${1:-}" in
    true|True|TRUE|1|yes|YES)
      printf 'true'
      ;;
    *)
      printf 'false'
      ;;
  esac
}

validate_target_base_refs() {
  if ! printf '%s' "$JIRA_TARGET_BASE_REFS" \
    | jq -e 'type == "array" and length > 0 and all(.[]; type == "string" and length > 0)' >/dev/null 2>&1; then
    error 'JIRA_TARGET_BASE_REFS must be a non-empty JSON string array, for example ["develop","main"]'
  fi
}

is_target_base_ref() {
  local base_ref="$1"

  printf '%s' "$JIRA_TARGET_BASE_REFS" \
    | jq -e --arg base_ref "$base_ref" 'index($base_ref) != null' >/dev/null
}

target_base_refs_text() {
  printf '%s' "$JIRA_TARGET_BASE_REFS" | jq -r 'join(", ")'
}

keys_from_text() {
  local text="$1"
  printf '%s' "$text" \
    | grep -Eio "${JIRA_PROJECT_KEY_UPPER}-[0-9]+" \
    | tr '[:lower:]' '[:upper:]' \
    | sort -u || true
}

one_key_from_text() {
  local source="$1"
  local text="$2"
  local -a keys

  mapfile -t keys < <(keys_from_text "$text")
  if [ "${#keys[@]}" -eq 0 ]; then
    return 1
  fi
  if [ "${#keys[@]}" -gt 1 ]; then
    error "${source}에서 Jira key가 여러 개 발견됐습니다: ${keys[*]}"
  fi

  printf '%s' "${keys[0]}"
}

extract_jira_key() {
  local explicit_line

  explicit_line="$(
    printf '%s\n' "${PR_BODY:-}" \
      | grep -Eim1 '^[[:space:]]*(-[[:space:]]*)?(Jira|JIRA|Jira 티켓|관련 Jira)[[:space:]]*:' || true
  )"
  if [ -n "$explicit_line" ]; then
    if one_key_from_text "PR 본문의 Jira 필드" "$explicit_line"; then
      return
    fi
    error "PR 본문의 Jira 필드에 Jira key(${JIRA_PROJECT_KEY_UPPER}-숫자)를 입력하세요."
  fi

  if one_key_from_text "PR 제목" "${PR_TITLE:-}"; then
    return
  fi

  if one_key_from_text "PR 브랜치명" "${PR_HEAD_REF:-}"; then
    return
  fi

  if one_key_from_text "PR 본문 전체" "${PR_BODY:-}"; then
    return
  fi

  error "PR에서 Jira key(${JIRA_PROJECT_KEY_UPPER}-숫자)를 찾지 못했습니다. PR 본문에 '- Jira: ${JIRA_PROJECT_KEY_UPPER}-번호'를 추가하세요."
}

resolve_target_status() {
  local event_name="$1"
  local action="$2"
  local draft="$3"
  local merged="$4"
  local base_ref="$5"
  local review_state="$6"

  if ! is_target_base_ref "$base_ref"; then
    notice "base branch가 Jira 상태 동기화 대상이 아니므로 건너뜁니다: ${base_ref} (대상: $(target_base_refs_text))"
    return "$STATUS_SYNC_SKIP_CODE"
  fi

  case "$event_name" in
    pull_request|pull_request_target)
      case "$action" in
        closed)
          if [ "$merged" = "true" ]; then
            printf '%s' "$JIRA_STATUS_DONE"
          else
            printf '%s' "$JIRA_STATUS_IN_PROGRESS"
          fi
          ;;
        converted_to_draft)
          printf '%s' "$JIRA_STATUS_CODE_REVIEW"
          ;;
        ready_for_review|review_requested)
          printf '%s' "$JIRA_STATUS_VERIFYING"
          ;;
        opened|reopened|synchronize)
          if [ "$draft" = "true" ]; then
            printf '%s' "$JIRA_STATUS_CODE_REVIEW"
          else
            printf '%s' "$JIRA_STATUS_VERIFYING"
          fi
          ;;
        *)
          notice "Jira 상태 동기화 대상 PR action이 아닙니다: ${action}"
          return "$STATUS_SYNC_SKIP_CODE"
          ;;
      esac
      ;;
    pull_request_review)
      if [ "$action" != "submitted" ]; then
        notice "Jira 상태 동기화 대상 review action이 아닙니다: ${action}"
        return "$STATUS_SYNC_SKIP_CODE"
      fi
      if [ "$review_state" != "changes_requested" ]; then
        notice "Jira 상태 동기화 대상 review state가 아닙니다: ${review_state:-없음}"
        return "$STATUS_SYNC_SKIP_CODE"
      fi
      printf '%s' "$JIRA_STATUS_CODE_REVIEW"
      ;;
    *)
      notice "Jira 상태 동기화 대상 event가 아닙니다: ${event_name}"
      return "$STATUS_SYNC_SKIP_CODE"
      ;;
  esac
}

is_retryable_transition_status() {
  case "$1" in
    409|429|5??)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

jira_request() {
  local method="$1"
  local path="$2"
  local payload="${3:-}"
  local url="${JIRA_BASE_URL%/}/rest/api/3${path}"
  local auth
  local -a args

  auth="$(printf '%s:%s' "$JIRA_EMAIL" "$JIRA_API_TOKEN" | base64 | tr -d '\n')"
  JIRA_RESPONSE_FILE="$(mktemp)"

  args=(
    -sS
    -w '%{http_code}'
    -o "$JIRA_RESPONSE_FILE"
    -X "$method"
    "$url"
    -H "Authorization: Basic ${auth}"
    -H 'Accept: application/json'
  )

  if [ -n "$payload" ]; then
    args+=(-H 'Content-Type: application/json' --data "@${payload}")
  fi

  JIRA_HTTP_STATUS="$(curl "${args[@]}")"
}

current_jira_status() {
  local issue_key="$1"

  jira_request GET "/issue/${issue_key}?fields=status"
  if [ "$JIRA_HTTP_STATUS" != "200" ]; then
    cat "$JIRA_RESPONSE_FILE" >&2 || true
    error "Jira issue 조회 실패: ${issue_key} (HTTP ${JIRA_HTTP_STATUS})"
  fi

  jq -r '.fields.status.name' "$JIRA_RESPONSE_FILE"
}

transition_jira_issue() {
  local issue_key="$1"
  local target_status="$2"
  local current_status
  local transition_id
  local available
  local payload
  local attempt

  current_status="$(current_jira_status "$issue_key")"
  if [ "$current_status" = "$target_status" ]; then
    notice "${issue_key}는 이미 '${target_status}' 상태입니다."
    return
  fi

  jira_request GET "/issue/${issue_key}/transitions"
  if [ "$JIRA_HTTP_STATUS" != "200" ]; then
    cat "$JIRA_RESPONSE_FILE" >&2 || true
    error "Jira transition 목록 조회 실패: ${issue_key} (HTTP ${JIRA_HTTP_STATUS})"
  fi

  transition_id="$(
    jq -r --arg target "$target_status" '.transitions[] | select(.to.name == $target) | .id' "$JIRA_RESPONSE_FILE" \
      | head -n 1
  )"

  if [ -z "$transition_id" ]; then
    available="$(jq -r '.transitions[].to.name' "$JIRA_RESPONSE_FILE" | paste -sd ', ' -)"
    error "${issue_key}를 '${current_status}'에서 '${target_status}'로 전환할 수 없습니다. 가능한 상태: ${available:-없음}"
  fi

  payload="$(mktemp)"
  jq -n --arg id "$transition_id" '{ transition: { id: $id } }' > "$payload"

  for attempt in 1 2 3; do
    jira_request POST "/issue/${issue_key}/transitions" "$payload"
    if [ "$JIRA_HTTP_STATUS" = "204" ] || [ "$JIRA_HTTP_STATUS" = "200" ]; then
      notice "${issue_key}: '${current_status}' -> '${target_status}' 전환 완료"
      return
    fi
    if is_retryable_transition_status "$JIRA_HTTP_STATUS"; then
      if [ "$attempt" -lt 3 ]; then
        warning "Jira transition 일시 오류가 발생해 재시도합니다 (${attempt}/3, HTTP ${JIRA_HTTP_STATUS})."
        sleep 2
        continue
      fi

      cat "$JIRA_RESPONSE_FILE" >&2 || true
      warning "Jira transition 일시 오류가 3회 반복되어 상태 동기화를 건너뜁니다: ${issue_key} -> ${target_status} (HTTP ${JIRA_HTTP_STATUS})"
      exit 0
    fi

    cat "$JIRA_RESPONSE_FILE" >&2 || true
    error "Jira transition 실패: ${issue_key} -> ${target_status} (HTTP ${JIRA_HTTP_STATUS})"
  done
}

add_unmerged_close_comment() {
  local issue_key="$1"
  local payload
  local text

  text="GitHub Actions: PR #${PR_NUMBER}이 merge 없이 closed 되어 Jira 상태를 '${JIRA_STATUS_IN_PROGRESS}'로 유지하거나 전환했습니다. PR: ${PR_URL}"
  payload="$(mktemp)"
  jq -n --arg text "$text" '{
    body: {
      type: "doc",
      version: 1,
      content: [
        {
          type: "paragraph",
          content: [
            { type: "text", text: $text }
          ]
        }
      ]
    }
  }' > "$payload"

  jira_request POST "/issue/${issue_key}/comment" "$payload"
  if [ "$JIRA_HTTP_STATUS" = "201" ]; then
    notice "${issue_key}에 PR 미병합 close 댓글을 남겼습니다."
    return
  fi

  cat "$JIRA_RESPONSE_FILE" >&2 || true
  error "Jira 댓글 추가 실패: ${issue_key} (HTTP ${JIRA_HTTP_STATUS})"
}

main() {
  require_env PR_ACTION
  require_env PR_NUMBER
  require_env PR_TITLE
  require_env PR_HEAD_REF
  require_env PR_BASE_REF
  require_env PR_URL

  JIRA_PROJECT_KEY="${JIRA_PROJECT_KEY:-MID4}"
  JIRA_PROJECT_KEY_UPPER="$(printf '%s' "$JIRA_PROJECT_KEY" | tr '[:lower:]' '[:upper:]')"
  JIRA_TARGET_BASE_REFS="${JIRA_TARGET_BASE_REFS:-[\"develop\",\"main\"]}"
  JIRA_STATUS_IN_PROGRESS="${JIRA_STATUS_IN_PROGRESS:-진행 중}"
  JIRA_STATUS_CODE_REVIEW="${JIRA_STATUS_CODE_REVIEW:-코드 리뷰}"
  JIRA_STATUS_VERIFYING="${JIRA_STATUS_VERIFYING:-검증 중}"
  JIRA_STATUS_DONE="${JIRA_STATUS_DONE:-완료}"
  EVENT_NAME="${EVENT_NAME:-${GITHUB_EVENT_NAME:-pull_request_target}}"
  PR_IS_DRAFT="$(bool_value "${PR_IS_DRAFT:-false}")"
  PR_MERGED="$(bool_value "${PR_MERGED:-false}")"
  PR_REVIEW_STATE="$(printf '%s' "${PR_REVIEW_STATE:-}" | tr '[:upper:]' '[:lower:]')"
  JIRA_DRY_RUN="$(bool_value "${JIRA_DRY_RUN:-false}")"

  validate_target_base_refs

  local target_status
  local resolve_status
  local issue_key

  set +e
  target_status="$(resolve_target_status "$EVENT_NAME" "$PR_ACTION" "$PR_IS_DRAFT" "$PR_MERGED" "$PR_BASE_REF" "$PR_REVIEW_STATE")"
  resolve_status=$?
  set -e

  if [ "$resolve_status" -eq "$STATUS_SYNC_SKIP_CODE" ]; then
    exit 0
  fi
  if [ "$resolve_status" -ne 0 ]; then
    exit "$resolve_status"
  fi

  if [ -z "${JIRA_BASE_URL:-}" ] || [ -z "${JIRA_EMAIL:-}" ] || [ -z "${JIRA_API_TOKEN:-}" ]; then
    if [ "$JIRA_DRY_RUN" != "true" ]; then
      warning "Jira 인증 secret이 없어 상태 동기화를 건너뜁니다. JIRA_BASE_URL, JIRA_EMAIL, JIRA_API_TOKEN을 등록하세요."
      exit 0
    fi
  fi

  issue_key="$(extract_jira_key)"

  if [ "$JIRA_DRY_RUN" = "true" ]; then
    notice "dry-run: ${issue_key} -> ${target_status} (event=${EVENT_NAME}, action=${PR_ACTION}, review=${PR_REVIEW_STATE:-none}, draft=${PR_IS_DRAFT}, merged=${PR_MERGED})"
    exit 0
  fi

  transition_jira_issue "$issue_key" "$target_status"

  if [ "$PR_ACTION" = "closed" ] && [ "$PR_MERGED" = "false" ]; then
    add_unmerged_close_comment "$issue_key"
  fi
}

main "$@"
