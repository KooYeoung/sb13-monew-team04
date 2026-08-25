import http from 'k6/http';
import { check, group, sleep } from 'k6';

const DEFAULT_TARGET_USER_ID = '00000001-0000-4000-8000-000000000001';

const SCENARIO = (__ENV.K6_SCENARIO || 'smoke').toLowerCase();
const BASE_URL = trimTrailingSlash(__ENV.K6_BASE_URL || 'http://localhost:8080');
const PATH_TEMPLATE = __ENV.K6_ACTIVITY_HISTORY_PATH_TEMPLATE || '/api/user-activities/{userId}';
const TARGET_USER_ID = __ENV.K6_TARGET_USER_ID || DEFAULT_TARGET_USER_ID;
const USER_ID_HEADER_NAME = __ENV.K6_USER_ID_HEADER_NAME || 'Monew-Request-User-ID';
const EXPECTED_STATUS = httpStatusEnv('K6_EXPECTED_STATUS', 200);
const SLEEP_SECONDS = nonNegativeNumberEnv('K6_SLEEP_SECONDS', 1);
const SUMMARY_PATH = summaryPath(PATH_TEMPLATE);
const ACTIVITY_HISTORY_URL = buildUrl(PATH_TEMPLATE, TARGET_USER_ID);

const scenarioConfigs = {
  smoke: {
    executor: 'shared-iterations',
    vus: numberEnv('K6_SMOKE_VUS', 1),
    iterations: numberEnv('K6_SMOKE_ITERATIONS', 1),
    maxDuration: __ENV.K6_SMOKE_MAX_DURATION || '30s',
  },
  baseline: {
    executor: 'constant-arrival-rate',
    rate: numberEnv('K6_BASELINE_RATE', 20),
    timeUnit: __ENV.K6_BASELINE_TIME_UNIT || '1s',
    duration: __ENV.K6_BASELINE_DURATION || '1m',
    preAllocatedVUs: numberEnv('K6_BASELINE_PRE_ALLOCATED_VUS', 20),
    maxVUs: numberEnv('K6_BASELINE_MAX_VUS', 100),
  },
};

if (!scenarioConfigs[SCENARIO]) {
  throw new Error(`K6_SCENARIO must be one of: smoke, baseline. value=${SCENARIO}`);
}

http.setResponseCallback(http.expectedStatuses(EXPECTED_STATUS));

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    [SCENARIO]: scenarioConfigs[SCENARIO],
  },
  thresholds: {
    http_req_failed: [`rate<${__ENV.K6_HTTP_REQ_FAILED_RATE_THRESHOLD || '0.01'}`],
    http_req_duration: [
      `p(95)<${__ENV.K6_HTTP_REQ_DURATION_P95_THRESHOLD || '1000'}`,
      `p(99)<${__ENV.K6_HTTP_REQ_DURATION_P99_THRESHOLD || '2000'}`,
    ],
    checks: [`rate>${__ENV.K6_CHECK_RATE_THRESHOLD || '0.99'}`],
    dropped_iterations: [`count<${__ENV.K6_DROPPED_ITERATIONS_COUNT_THRESHOLD || '1'}`],
  },
};

export default function () {
  group('activity-history-read', () => {
    const response = http.get(ACTIVITY_HISTORY_URL, requestParams());

    check(response, {
      [`status is ${EXPECTED_STATUS}`]: (res) => res.status === EXPECTED_STATUS,
      'response body is not empty': (res) => Boolean(res.body && res.body.length > 0),
    });
  });

  if (SCENARIO === 'smoke' && SLEEP_SECONDS > 0) {
    sleep(SLEEP_SECONDS);
  }
}

export function handleSummary(data) {
  const summary = {
    scenario: SCENARIO,
    url: ACTIVITY_HISTORY_URL,
    targetUserId: TARGET_USER_ID,
    expectedStatus: EXPECTED_STATUS,
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
    `scenario: ${summary.scenario}`,
    `url: ${summary.url}`,
    `targetUserId: ${summary.targetUserId}`,
    `summaryPath: ${SUMMARY_PATH}`,
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

function requestParams() {
  const headers = {
    Accept: 'application/json',
  };

  if (USER_ID_HEADER_NAME) {
    headers[USER_ID_HEADER_NAME] = TARGET_USER_ID;
  }

  if (__ENV.K6_AUTHORIZATION) {
    headers.Authorization = __ENV.K6_AUTHORIZATION;
  }

  return {
    headers,
    tags: {
      api: 'activity-history',
      scenario: SCENARIO,
    },
  };
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

  return `/results/${summaryName(pathTemplate)}-summary.json`;
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
