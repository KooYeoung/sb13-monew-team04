#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SYNC_SCRIPT="${SCRIPT_DIR}/sync-jira-pr-status.sh"
TARGET_BASE_REFS='["develop","main"]'
PASSED=0

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to test Jira PR status synchronization." >&2
  exit 1
fi

run_case() {
  local name="$1"
  local base_ref="$2"
  local event_name="$3"
  local action="$4"
  local draft="$5"
  local merged="$6"
  local review_state="$7"
  local expected="$8"
  local output
  local exit_code

  set +e
  output="$(
    JIRA_DRY_RUN=true \
    JIRA_TARGET_BASE_REFS="$TARGET_BASE_REFS" \
    EVENT_NAME="$event_name" \
    PR_ACTION="$action" \
    PR_REVIEW_STATE="$review_state" \
    PR_NUMBER=242 \
    PR_TITLE='MID4-242 dry-run' \
    PR_BODY='- Jira: MID4-242' \
    PR_HEAD_REF='chore/MID4-242-main-pr-jira-status-sync' \
    PR_BASE_REF="$base_ref" \
    PR_IS_DRAFT="$draft" \
    PR_MERGED="$merged" \
    PR_URL='https://example.invalid/pull/242' \
    bash "$SYNC_SCRIPT" 2>&1
  )"
  exit_code=$?
  set -e

  if [ "$exit_code" -ne 0 ]; then
    echo "FAIL: ${name} exited with ${exit_code}" >&2
    echo "$output" >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* ]]; then
    echo "FAIL: ${name} did not contain '${expected}'" >&2
    echo "$output" >&2
    exit 1
  fi

  PASSED=$((PASSED + 1))
  echo "PASS: ${name}"
}

run_invalid_config_case() {
  local name="$1"
  local target_base_refs="$2"
  local output
  local exit_code

  set +e
  output="$(
    JIRA_DRY_RUN=true \
    JIRA_TARGET_BASE_REFS="$target_base_refs" \
    EVENT_NAME='pull_request_target' \
    PR_ACTION='opened' \
    PR_NUMBER=242 \
    PR_TITLE='MID4-242 dry-run' \
    PR_HEAD_REF='chore/MID4-242-main-pr-jira-status-sync' \
    PR_BASE_REF='develop' \
    PR_URL='https://example.invalid/pull/242' \
    bash "$SYNC_SCRIPT" 2>&1
  )"
  exit_code=$?
  set -e

  if [ "$exit_code" -eq 0 ]; then
    echo "FAIL: ${name} succeeded" >&2
    echo "$output" >&2
    exit 1
  fi
  if [[ "$output" != *'JIRA_TARGET_BASE_REFS must be a non-empty JSON string array'* ]]; then
    echo "FAIL: ${name} did not report the expected error" >&2
    echo "$output" >&2
    exit 1
  fi

  PASSED=$((PASSED + 1))
  echo "PASS: ${name}"
}

for base_ref in develop main; do
  run_case "${base_ref}: opened draft" "$base_ref" pull_request_target opened true false '' '-> 코드 리뷰'
  run_case "${base_ref}: ready for review" "$base_ref" pull_request_target ready_for_review false false '' '-> 검증 중'
  run_case "${base_ref}: changes requested" "$base_ref" pull_request_review submitted false false changes_requested '-> 코드 리뷰'
  run_case "${base_ref}: merged" "$base_ref" pull_request_target closed false true '' '-> 완료'
  run_case "${base_ref}: closed without merge" "$base_ref" pull_request_target closed false false '' '-> 진행 중'
done

run_case 'non-target branch' release pull_request_target opened false false '' '동기화 대상이 아니므로 건너뜁니다'
run_invalid_config_case 'invalid JSON target branch configuration' 'not-json'
run_invalid_config_case 'empty target branch array' '[]'
run_invalid_config_case 'empty target branch name' '[""]'

echo "All ${PASSED} Jira PR status synchronization tests passed."
