import http from 'k6/http';
import { check, group, sleep } from 'k6';

const DEFAULT_TARGET_USER_ID = '00000001-0000-4000-8000-000000000001';
const VALID_SCENARIOS = ['smoke', 'baseline', 'average', 'high-load', 'stress', 'throughput'];
const VALID_VARIANTS = ['rdb', 'mongo'];
const VALID_USER_PICK_STRATEGIES = ['single', 'round-robin', 'random'];

const SCENARIO = stringChoiceEnv('K6_SCENARIO', 'smoke', VALID_SCENARIOS);
const VARIANT = stringChoiceEnv('K6_VARIANT', 'rdb', VALID_VARIANTS);
const BASE_URL = trimTrailingSlash(__ENV.K6_BASE_URL || 'http://localhost:8080');
const PATH_TEMPLATE = __ENV.K6_ACTIVITY_HISTORY_PATH_TEMPLATE || '/api/user-activities/{userId}';
const TARGET_USER_IDS = targetUserIdsEnv();
const PRIMARY_TARGET_USER_ID = TARGET_USER_IDS[0];
const USER_PICK_STRATEGY = stringChoiceEnv('K6_USER_PICK_STRATEGY', 'single', VALID_USER_PICK_STRATEGIES);
const USER_ID_HEADER_NAME = __ENV.K6_USER_ID_HEADER_NAME || 'Monew-Request-User-ID';
const EXPECTED_STATUS = httpStatusEnv('K6_EXPECTED_STATUS', 200);
const SLEEP_SECONDS = nonNegativeNumberEnv('K6_SLEEP_SECONDS', 1);
const SUMMARY_PATH = summaryPath(PATH_TEMPLATE);
const SAMPLE_ACTIVITY_HISTORY_URL = buildUrl(PATH_TEMPLATE, PRIMARY_TARGET_USER_ID);
const HTTP_REQ_FAILED_RATE_THRESHOLD = __ENV.K6_HTTP_REQ_FAILED_RATE_THRESHOLD || '0.01';
const HTTP_REQ_DURATION_P95_THRESHOLD = __ENV.K6_HTTP_REQ_DURATION_P95_THRESHOLD || '200';
const HTTP_REQ_DURATION_P99_THRESHOLD = __ENV.K6_HTTP_REQ_DURATION_P99_THRESHOLD || '500';
const CHECK_RATE_THRESHOLD = __ENV.K6_CHECK_RATE_THRESHOLD || '0.99';
const DROPPED_ITERATIONS_COUNT_THRESHOLD = __ENV.K6_DROPPED_ITERATIONS_COUNT_THRESHOLD || '1';

const scenarioConfigs = {
  smoke: {
    executor: 'constant-vus',
    vus: numberEnv('K6_SMOKE_VUS', 1),
    duration: __ENV.K6_SMOKE_DURATION || '1m',
  },
  baseline: {
    executor: 'constant-vus',
    vus: numberEnv('K6_BASELINE_VUS', 20),
    duration: __ENV.K6_BASELINE_DURATION || '5m',
  },
  average: {
    executor: 'constant-vus',
    vus: numberEnv('K6_AVERAGE_VUS', 50),
    duration: __ENV.K6_AVERAGE_DURATION || '10m',
  },
  'high-load': {
    executor: 'constant-vus',
    vus: numberEnv('K6_HIGH_LOAD_VUS', 100),
    duration: __ENV.K6_HIGH_LOAD_DURATION || '10m',
  },
  stress: {
    executor: 'ramping-vus',
    startVUs: nonNegativeIntegerEnv('K6_STRESS_START_VUS', 0),
    stages: stressStagesEnv('K6_STRESS_STAGES', '3m:50,3m:100,3m:200,3m:400'),
    gracefulRampDown: __ENV.K6_STRESS_GRACEFUL_RAMP_DOWN || '30s',
  },
  throughput: {
    executor: 'constant-arrival-rate',
    rate: numberEnv('K6_THROUGHPUT_RATE', 50),
    timeUnit: __ENV.K6_THROUGHPUT_TIME_UNIT || '1s',
    duration: __ENV.K6_THROUGHPUT_DURATION || '1m',
    preAllocatedVUs: numberEnv('K6_THROUGHPUT_PRE_ALLOCATED_VUS', 500),
    maxVUs: numberEnv('K6_THROUGHPUT_MAX_VUS', 500),
  },
};

const ACTIVE_SCENARIO_CONFIG = scenarioConfigs[SCENARIO];

http.setResponseCallback(http.expectedStatuses(EXPECTED_STATUS));

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    [SCENARIO]: ACTIVE_SCENARIO_CONFIG,
  },
  thresholds: {
    http_req_failed: [`rate<${HTTP_REQ_FAILED_RATE_THRESHOLD}`],
    http_req_duration: [
      `p(95)<${HTTP_REQ_DURATION_P95_THRESHOLD}`,
      `p(99)<${HTTP_REQ_DURATION_P99_THRESHOLD}`,
    ],
    checks: [`rate>${CHECK_RATE_THRESHOLD}`],
    dropped_iterations: [`count<${DROPPED_ITERATIONS_COUNT_THRESHOLD}`],
  },
};

export default function () {
  group('activity-history-read', () => {
    const targetUserId = pickTargetUserId();
    const response = http.get(buildUrl(PATH_TEMPLATE, targetUserId), requestParams(targetUserId));
    const body = parseJsonBody(response);

    check(response, {
      [`status is ${EXPECTED_STATUS}`]: (res) => res.status === EXPECTED_STATUS,
      'response body is not empty': (res) => Boolean(res.body && res.body.length > 0),
      'response body is json': () => body !== null,
      'activity user fields exist': () =>
        body !== null &&
        hasStringField(body, 'id') &&
        hasStringField(body, 'email') &&
        hasStringField(body, 'nickname') &&
        hasStringField(body, 'createdAt'),
      'activity array fields exist': () =>
        body !== null &&
        Array.isArray(body.subscriptions) &&
        Array.isArray(body.comments) &&
        Array.isArray(body.commentLikes) &&
        Array.isArray(body.articleViews),
    });
  });

  if (shouldSleepBetweenIterations() && SLEEP_SECONDS > 0) {
    sleep(SLEEP_SECONDS);
  }
}

export function handleSummary(data) {
  const summary = {
    ticket: __ENV.K6_TICKET || 'MID4-206',
    runIndex: Number(__ENV.K6_RUN_INDEX || '1'),
    scenario: SCENARIO,
    variant: VARIANT,
    url: SAMPLE_ACTIVITY_HISTORY_URL,
    pathTemplate: PATH_TEMPLATE,
    targetUserId: PRIMARY_TARGET_USER_ID,
    targetUserIds: TARGET_USER_IDS,
    targetUserCount: TARGET_USER_IDS.length,
    userPickStrategy: USER_PICK_STRATEGY,
    expectedStatus: EXPECTED_STATUS,
    requestedRate: scenarioInputValue('rate'),
    vus: scenarioInputValue('vus'),
    startVUs: scenarioInputValue('startVUs'),
    duration: scenarioInputValue('duration'),
    stages: scenarioInputValue('stages'),
    preAllocatedVUs: scenarioInputValue('preAllocatedVUs'),
    maxVUs: scenarioInputValue('maxVUs'),
    gracefulRampDown: scenarioInputValue('gracefulRampDown'),
    thresholds: {
      httpReqFailedRate: HTTP_REQ_FAILED_RATE_THRESHOLD,
      httpReqDurationP95Ms: HTTP_REQ_DURATION_P95_THRESHOLD,
      httpReqDurationP99Ms: HTTP_REQ_DURATION_P99_THRESHOLD,
      checksRate: CHECK_RATE_THRESHOLD,
      droppedIterationsCount: DROPPED_ITERATIONS_COUNT_THRESHOLD,
    },
    metrics: {
      requests: metricValue(data, 'http_reqs', 'count'),
      rps: metricValue(data, 'http_reqs', 'rate'),
      droppedIterations: metricValue(data, 'dropped_iterations', 'count'),
      errorRate: metricValue(data, 'http_req_failed', 'rate'),
      durationAvgMs: metricValue(data, 'http_req_duration', 'avg'),
      durationP95Ms: metricValue(data, 'http_req_duration', 'p(95)'),
      durationP99Ms: metricValue(data, 'http_req_duration', 'p(99)'),
      checksRate: metricValue(data, 'checks', 'rate'),
    },
  };

  const output = {};
  output.stdout = [
    'Activity history k6 summary',
    `ticket: ${summary.ticket}`,
    `scenario: ${summary.scenario}`,
    `variant: ${summary.variant}`,
    `url: ${summary.url}`,
    `targetUserId: ${summary.targetUserId}`,
    `targetUserCount: ${summary.targetUserCount}`,
    `userPickStrategy: ${summary.userPickStrategy}`,
    `summaryPath: ${SUMMARY_PATH}`,
    `vus: ${summary.vus === null ? 'n/a' : summary.vus}`,
    `requestedRate: ${summary.requestedRate === null ? 'n/a' : summary.requestedRate}`,
    `requests: ${formatNumber(summary.metrics.requests, 0)}`,
    `rps: ${formatNumber(summary.metrics.rps, 2)}`,
    `droppedIterations: ${formatNumber(summary.metrics.droppedIterations, 0)}`,
    `errorRate: ${formatPercent(summary.metrics.errorRate)}`,
    `duration.avg: ${formatNumber(summary.metrics.durationAvgMs, 2)} ms`,
    `duration.p95: ${formatNumber(summary.metrics.durationP95Ms, 2)} ms`,
    `duration.p99: ${formatNumber(summary.metrics.durationP99Ms, 2)} ms`,
    `checksRate: ${formatPercent(summary.metrics.checksRate)}`,
    '',
  ].join('\n');
  output[SUMMARY_PATH] = JSON.stringify(summary, null, 2);

  return output;
}

function requestParams(targetUserId) {
  const headers = {
    Accept: 'application/json',
  };

  if (USER_ID_HEADER_NAME) {
    headers[USER_ID_HEADER_NAME] = targetUserId;
  }

  if (__ENV.K6_AUTHORIZATION) {
    headers.Authorization = __ENV.K6_AUTHORIZATION;
  }

  return {
    headers,
    tags: {
      api: 'activity-history',
      scenario: SCENARIO,
      variant: VARIANT,
      userPickStrategy: USER_PICK_STRATEGY,
    },
  };
}

function pickTargetUserId() {
  if (USER_PICK_STRATEGY === 'single') {
    return PRIMARY_TARGET_USER_ID;
  }

  if (USER_PICK_STRATEGY === 'round-robin') {
    return TARGET_USER_IDS[((__VU - 1) + __ITER) % TARGET_USER_IDS.length];
  }

  return TARGET_USER_IDS[Math.floor(Math.random() * TARGET_USER_IDS.length)];
}

function shouldSleepBetweenIterations() {
  return SCENARIO !== 'throughput';
}

function parseJsonBody(response) {
  if (!response.body) {
    return null;
  }

  try {
    return response.json();
  } catch (error) {
    return null;
  }
}

function hasStringField(value, fieldName) {
  return typeof value[fieldName] === 'string' && value[fieldName].length > 0;
}

function buildUrl(pathTemplate, userId) {
  const encodedUserId = encodeURIComponent(userId);
  const path = pathTemplate.split('{userId}').join(encodedUserId);

  if (/^https?:\/\//.test(path)) {
    return path;
  }

  return `${BASE_URL}${path.startsWith('/') ? path : `/${path}`}`;
}

function summaryPath(pathTemplate) {
  if (__ENV.K6_SUMMARY_PATH) {
    return __ENV.K6_SUMMARY_PATH;
  }

  return `/results/${summaryName(pathTemplate)}-${VARIANT}-${SCENARIO}-summary.json`;
}

function summaryName(pathTemplate) {
  const path = extractPath(pathTemplate);
  const normalizedPath = normalizePath(path);

  if (normalizedPath === 'user-activities') {
    return 'activity-history';
  }

  return normalizedPath || 'k6';
}

function extractPath(pathTemplate) {
  return pathTemplate
    .replace(/^https?:\/\/[^/]+/i, '')
    .split('?')[0]
    .split('#')[0];
}

function normalizePath(path) {
  return path
    .split('/')
    .filter((segment) => segment && !isPathVariable(segment))
    .filter((segment) => segment !== 'api')
    .join('-')
    .replace(/[^A-Za-z0-9-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .toLowerCase();
}

function isPathVariable(segment) {
  return /^\{[^}]+\}$/.test(segment);
}

function trimTrailingSlash(value) {
  return value.replace(/\/+$/, '');
}

function stringChoiceEnv(name, defaultValue, allowedValues) {
  const rawValue = (__ENV[name] || defaultValue).toLowerCase();

  if (allowedValues.indexOf(rawValue) < 0) {
    throw new Error(`${name} must be one of: ${allowedValues.join(', ')}. value=${rawValue}`);
  }

  return rawValue;
}

function targetUserIdsEnv() {
  const rawValue = __ENV.K6_TARGET_USER_IDS || __ENV.K6_TARGET_USER_ID || DEFAULT_TARGET_USER_ID;
  const userIds = rawValue
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);

  if (userIds.length === 0) {
    throw new Error('K6_TARGET_USER_IDS or K6_TARGET_USER_ID must include at least one user id.');
  }

  return userIds;
}

function stressStagesEnv(name, defaultValue) {
  const rawValue = __ENV[name] || defaultValue;
  const stages = rawValue
    .split(',')
    .map((segment) => {
      const parts = segment.split(':');

      if (parts.length !== 2) {
        throw new Error(`${name} stage must use duration:target format. value=${segment}`);
      }

      const duration = parts[0].trim();
      const target = Number(parts[1]);

      if (!duration || !Number.isInteger(target) || target < 0) {
        throw new Error(`${name} stage must include a duration and a non-negative integer target. value=${segment}`);
      }

      return {
        duration,
        target,
      };
    });

  if (stages.length === 0) {
    throw new Error(`${name} must include at least one stage.`);
  }

  return stages;
}

function numberEnv(name, defaultValue) {
  const rawValue = __ENV[name];

  if (!rawValue) {
    return defaultValue;
  }

  const parsedValue = Number(rawValue);

  if (!Number.isFinite(parsedValue) || parsedValue <= 0) {
    throw new Error(`${name} must be a positive number. value=${rawValue}`);
  }

  return parsedValue;
}

function nonNegativeNumberEnv(name, defaultValue) {
  const rawValue = __ENV[name];

  if (!rawValue) {
    return defaultValue;
  }

  const parsedValue = Number(rawValue);

  if (!Number.isFinite(parsedValue) || parsedValue < 0) {
    throw new Error(`${name} must be a non-negative number. value=${rawValue}`);
  }

  return parsedValue;
}

function nonNegativeIntegerEnv(name, defaultValue) {
  const rawValue = __ENV[name];

  if (!rawValue) {
    return defaultValue;
  }

  const parsedValue = Number(rawValue);

  if (!Number.isInteger(parsedValue) || parsedValue < 0) {
    throw new Error(`${name} must be a non-negative integer. value=${rawValue}`);
  }

  return parsedValue;
}

function httpStatusEnv(name, defaultValue) {
  const rawValue = __ENV[name];

  if (!rawValue) {
    return defaultValue;
  }

  const parsedValue = Number(rawValue);

  if (!Number.isInteger(parsedValue) || parsedValue < 100 || parsedValue > 599 || isBodylessStatus(parsedValue)) {
    throw new Error(
      `${name} must be an integer HTTP status code from 100 to 599 that can include a response body. value=${rawValue}`
    );
  }

  return parsedValue;
}

function isBodylessStatus(status) {
  return (status >= 100 && status < 200) || status === 204 || status === 205 || status === 304;
}

function scenarioInputValue(name) {
  const value = ACTIVE_SCENARIO_CONFIG[name];
  return value === undefined ? null : value;
}

function metricValue(data, metricName, statName) {
  const metric = data.metrics[metricName];

  if (!metric || !metric.values || metric.values[statName] === undefined) {
    return null;
  }

  return metric.values[statName];
}

function formatNumber(value, fractionDigits) {
  if (value === null || value === undefined) {
    return 'n/a';
  }

  return Number(value).toFixed(fractionDigits);
}

function formatPercent(value) {
  if (value === null || value === undefined) {
    return 'n/a';
  }

  return `${(Number(value) * 100).toFixed(2)}%`;
}
