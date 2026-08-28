import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const DEFAULT_TARGET_USER_ID = '00000001-0000-4000-8000-000000000001';
const VALID_SCENARIOS = ['smoke', 'baseline', 'average', 'high-load', 'stress', 'throughput'];
const VALID_VARIANTS = ['rdb', 'mongo'];
const VALID_USER_PICK_STRATEGIES = ['single', 'round-robin', 'random'];
const VALID_MIX_RATIOS = ['80/20', '50/50'];

const SCENARIO = stringChoiceEnv('K6_SCENARIO', 'smoke', VALID_SCENARIOS);
const VARIANT = stringChoiceEnv('K6_VARIANT', 'rdb', VALID_VARIANTS);
const MIX_RATIO = stringChoiceEnv('K6_MIX_RATIO', '80/20', VALID_MIX_RATIOS);
const BASE_URL = trimTrailingSlash(__ENV.K6_BASE_URL || 'http://localhost:8080');
const PATH_TEMPLATE = __ENV.K6_ACTIVITY_HISTORY_PATH_TEMPLATE || '/api/user-activities/{userId}';
const TARGET_USER_IDS = targetUserIdsEnv();
const PRIMARY_TARGET_USER_ID = TARGET_USER_IDS[0];
const USER_PICK_STRATEGY = stringChoiceEnv('K6_USER_PICK_STRATEGY', 'single', VALID_USER_PICK_STRATEGIES);
const USER_ID_HEADER_NAME = __ENV.K6_USER_ID_HEADER_NAME || 'Monew-Request-User-ID';
const SLEEP_SECONDS = nonNegativeNumberEnv('K6_SLEEP_SECONDS', 1);
const SUMMARY_PATH = summaryPath(PATH_TEMPLATE);
const SAMPLE_ACTIVITY_HISTORY_URL = buildUrl(PATH_TEMPLATE, PRIMARY_TARGET_USER_ID);
const HTTP_REQ_FAILED_RATE_THRESHOLD = __ENV.K6_HTTP_REQ_FAILED_RATE_THRESHOLD || '0.01';
const HTTP_REQ_DURATION_P95_THRESHOLD = __ENV.K6_HTTP_REQ_DURATION_P95_THRESHOLD || '200';
const HTTP_REQ_DURATION_P99_THRESHOLD = __ENV.K6_HTTP_REQ_DURATION_P99_THRESHOLD || '500';
const CHECK_RATE_THRESHOLD = __ENV.K6_CHECK_RATE_THRESHOLD || '0.99';
const DROPPED_ITERATIONS_COUNT_THRESHOLD = __ENV.K6_DROPPED_ITERATIONS_COUNT_THRESHOLD || '1';

const ARTICLE_IDS = idPoolEnv('K6_MIX_ARTICLE_IDS', () => buildPerfUuidPool(5, 1, 10000));
const COMMENT_IDS = idPoolEnv('K6_MIX_COMMENT_IDS', () => buildPerfUuidPool(7, 1, 10000));
const INTEREST_IDS = idPoolEnv('K6_MIX_INTEREST_IDS', () => buildPerfUuidPool(2, 40000, 10000));
const WRITE_USER_IDS = idPoolEnv('K6_MIX_WRITE_USER_IDS', () => buildActiveUserPool(1001, 5000));
const API_WEIGHTS = apiWeights(MIX_RATIO);
const API_NAMES = API_WEIGHTS.map((entry) => entry.name);
const API_METRICS = buildApiMetrics(API_NAMES);

const scenarioConfigs = {
  smoke: {
    executor: 'constant-vus',
    vus: numberEnv('K6_SMOKE_VUS', 5),
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

http.setResponseCallback(http.expectedStatuses(200, 201, 204));

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
  const actionName = pickAction();

  group(actionName, () => {
    if (actionName === 'activity-history-read') {
      readActivityHistory();
    } else if (actionName === 'comment-create') {
      createComment();
    } else if (actionName === 'comment-like-toggle') {
      toggleCommentLike();
    } else if (actionName === 'article-view') {
      viewArticle();
    } else if (actionName === 'subscription-toggle') {
      toggleSubscription();
    }
  });

  if (shouldSleepBetweenIterations() && SLEEP_SECONDS > 0) {
    sleep(SLEEP_SECONDS);
  }
}

export function handleSummary(data) {
  const summary = {
    ticket: 'MID4-206',
    scenario: SCENARIO,
    variant: VARIANT,
    workload: 'mixed',
    mixRatio: MIX_RATIO,
    apiWeights: API_WEIGHTS,
    url: SAMPLE_ACTIVITY_HISTORY_URL,
    pathTemplate: PATH_TEMPLATE,
    targetUserId: PRIMARY_TARGET_USER_ID,
    targetUserIds: TARGET_USER_IDS,
    targetUserCount: TARGET_USER_IDS.length,
    userPickStrategy: USER_PICK_STRATEGY,
    writePoolSizes: {
      users: WRITE_USER_IDS.length,
      articles: ARTICLE_IDS.length,
      comments: COMMENT_IDS.length,
      interests: INTEREST_IDS.length,
    },
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
    apiMetrics: apiMetricSummary(data),
  };

  const output = {};
  output.stdout = [
    'Activity history mixed k6 summary',
    `ticket: ${summary.ticket}`,
    `scenario: ${summary.scenario}`,
    `variant: ${summary.variant}`,
    `workload: ${summary.workload}`,
    `mixRatio: ${summary.mixRatio}`,
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
    apiMetricLines(summary.apiMetrics),
    '',
  ]
    .filter((line) => line !== null)
    .join('\n');
  output[SUMMARY_PATH] = JSON.stringify(summary, null, 2);

  return output;
}

function readActivityHistory() {
  const userId = pickTargetUserId();
  const response = apiRequest(
    'activity-history-read',
    'GET',
    buildUrl(PATH_TEMPLATE, userId),
    null,
    requestParams('activity-history-read', userId),
    [200],
    (res) => {
      const body = parseJsonBody(res);
      return (
        res.status === 200 &&
        Boolean(res.body && res.body.length > 0) &&
        body !== null &&
        hasStringField(body, 'id') &&
        hasStringField(body, 'email') &&
        hasStringField(body, 'nickname') &&
        hasStringField(body, 'createdAt') &&
        Array.isArray(body.subscriptions) &&
        Array.isArray(body.comments) &&
        Array.isArray(body.commentLikes) &&
        Array.isArray(body.articleViews)
      );
    }
  );

  return response;
}

function createComment() {
  const sequence = sequenceNumber();
  const userId = pickWriteUserId(sequence);
  const articleId = pickFrom(ARTICLE_IDS, sequence * 17);
  const body = JSON.stringify({
    articleId,
    userId,
    content: `MID4-206 mixed comment ${__VU}-${__ITER}-${Date.now()}`,
  });
  const params = requestParams('comment-create', userId, {
    'Content-Type': 'application/json',
  });

  return apiRequest('comment-create', 'POST', `${BASE_URL}/api/comments`, body, params, [201], (res) => {
    const parsedBody = parseJsonBody(res);
    return res.status === 201 && parsedBody !== null && hasStringField(parsedBody, 'id');
  });
}

function toggleCommentLike() {
  const sequence = sequenceNumber();
  const userId = pickWriteUserId(sequence);
  const commentId = pickFrom(COMMENT_IDS, sequence * 31);
  const url = `${BASE_URL}/api/comments/${encodeURIComponent(commentId)}/comment-likes`;
  const params = requestParams('comment-like-toggle', userId);

  apiRequest('comment-like-toggle', 'POST', url, null, params, [200], (res) => {
    const body = parseJsonBody(res);
    return res.status === 200 && body !== null && hasStringField(body, 'id');
  });

  return apiRequest('comment-like-toggle', 'DELETE', url, null, params, [204], (res) => res.status === 204);
}

function viewArticle() {
  const sequence = sequenceNumber();
  const userId = pickWriteUserId(sequence);
  const articleId = pickFrom(ARTICLE_IDS, sequence * 43);
  const url = `${BASE_URL}/api/articles/${encodeURIComponent(articleId)}/article-views`;

  return apiRequest('article-view', 'POST', url, null, requestParams('article-view', userId), [200], (res) => {
    const body = parseJsonBody(res);
    return res.status === 200 && body !== null && hasStringField(body, 'id');
  });
}

function toggleSubscription() {
  const sequence = sequenceNumber();
  const userId = pickWriteUserId(sequence);
  const interestId = pickFrom(INTEREST_IDS, sequence * 53);
  const url = `${BASE_URL}/api/interests/${encodeURIComponent(interestId)}/subscriptions`;
  const params = requestParams('subscription-toggle', userId);

  apiRequest('subscription-toggle', 'POST', url, null, params, [200], (res) => {
    const body = parseJsonBody(res);
    return res.status === 200 && body !== null && hasStringField(body, 'id');
  });

  return apiRequest('subscription-toggle', 'DELETE', url, null, params, [204], (res) => res.status === 204);
}

function apiRequest(apiName, method, url, body, params, expectedStatuses, checkFn) {
  const start = Date.now();
  const response = http.request(method, url, body, params);
  const duration = Date.now() - start;
  const expectedStatus = expectedStatuses.indexOf(response.status) >= 0;
  const checksPassed = check(response, {
    [`${apiName} response is valid`]: (res) => expectedStatus && checkFn(res),
  });

  recordApiMetric(apiName, duration, !expectedStatus, checksPassed, params.tags);
  return response;
}

function requestParams(apiName, userId, additionalHeaders = {}) {
  const headers = {
    Accept: 'application/json',
    ...additionalHeaders,
  };

  if (USER_ID_HEADER_NAME && userId) {
    headers[USER_ID_HEADER_NAME] = userId;
  }

  if (__ENV.K6_AUTHORIZATION) {
    headers.Authorization = __ENV.K6_AUTHORIZATION;
  }

  return {
    headers,
    tags: {
      api: apiName,
      scenario: SCENARIO,
      variant: VARIANT,
      workload: 'mixed',
      mixRatio: MIX_RATIO,
      userPickStrategy: USER_PICK_STRATEGY,
    },
  };
}

function buildApiMetrics(apiNames) {
  const metrics = {};

  for (const apiName of apiNames) {
    const key = metricKey(apiName);
    metrics[apiName] = {
      requests: new Counter(`api_${key}_requests`),
      duration: new Trend(`api_${key}_duration`, true),
      failed: new Rate(`api_${key}_failed`),
      checks: new Rate(`api_${key}_checks`),
    };
  }

  return metrics;
}

function recordApiMetric(apiName, duration, failed, checksPassed, tags) {
  API_METRICS[apiName].requests.add(1, tags);
  API_METRICS[apiName].duration.add(duration, tags);
  API_METRICS[apiName].failed.add(failed, tags);
  API_METRICS[apiName].checks.add(checksPassed, tags);
}

function apiMetricSummary(data) {
  const result = {};

  for (const apiName of API_NAMES) {
    const key = metricKey(apiName);
    result[apiName] = {
      requests: metricValue(data, `api_${key}_requests`, 'count'),
      rps: metricValue(data, `api_${key}_requests`, 'rate'),
      errorRate: metricValue(data, `api_${key}_failed`, 'rate'),
      durationAvgMs: metricValue(data, `api_${key}_duration`, 'avg'),
      durationP95Ms: metricValue(data, `api_${key}_duration`, 'p(95)'),
      durationP99Ms: metricValue(data, `api_${key}_duration`, 'p(99)'),
      checksRate: metricValue(data, `api_${key}_checks`, 'rate'),
    };
  }

  return result;
}

function apiMetricLines(apiMetrics) {
  return API_NAMES.map((apiName) => {
    const metrics = apiMetrics[apiName];
    return [
      `${apiName}:`,
      `requests=${formatNumber(metrics.requests, 0)}`,
      `p95=${formatNumber(metrics.durationP95Ms, 2)}ms`,
      `p99=${formatNumber(metrics.durationP99Ms, 2)}ms`,
      `errorRate=${formatPercent(metrics.errorRate)}`,
      `checksRate=${formatPercent(metrics.checksRate)}`,
    ].join(' ');
  }).join('\n');
}

function apiWeights(mixRatio) {
  if (mixRatio === '50/50') {
    return [
      { name: 'activity-history-read', weight: 50 },
      { name: 'comment-create', weight: 10 },
      { name: 'comment-like-toggle', weight: 15 },
      { name: 'article-view', weight: 15 },
      { name: 'subscription-toggle', weight: 10 },
    ];
  }

  return [
    { name: 'activity-history-read', weight: 80 },
    { name: 'comment-create', weight: 5 },
    { name: 'comment-like-toggle', weight: 5 },
    { name: 'article-view', weight: 5 },
    { name: 'subscription-toggle', weight: 5 },
  ];
}

function pickAction() {
  const value = Math.random() * 100;
  let lowerBound = 0;

  for (const entry of API_WEIGHTS) {
    lowerBound += entry.weight;
    if (value < lowerBound) {
      return entry.name;
    }
  }

  return API_WEIGHTS[API_WEIGHTS.length - 1].name;
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

function pickWriteUserId(sequence) {
  return pickFrom(WRITE_USER_IDS, sequence);
}

function pickFrom(pool, sequence) {
  return pool[Math.abs(sequence) % pool.length];
}

function sequenceNumber() {
  return (__ITER * 1009) + (__VU * 7919);
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

  return `/results/${summaryName(pathTemplate)}-${VARIANT}-mixed-${MIX_RATIO.replace('/', '-')}-${SCENARIO}-summary.json`;
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
  return csvEnvValues(rawValue, 'K6_TARGET_USER_IDS or K6_TARGET_USER_ID');
}

function idPoolEnv(name, fallbackFactory) {
  const rawValue = __ENV[name];
  if (!rawValue) {
    return fallbackFactory();
  }

  return csvEnvValues(rawValue, name);
}

function csvEnvValues(rawValue, name) {
  const values = rawValue
    .split(',')
    .map((value) => value.trim())
    .filter((value) => value.length > 0);

  if (values.length === 0) {
    throw new Error(`${name} must include at least one value.`);
  }

  return values;
}

function buildActiveUserPool(startSeq, count) {
  const result = [];
  let seq = startSeq;

  while (result.length < count) {
    if (seq % 100 !== 0) {
      result.push(perfUuid(1, seq));
    }
    seq += 1;
  }

  return result;
}

function buildPerfUuidPool(namespaceCode, startSeq, count) {
  const result = [];

  for (let offset = 0; offset < count; offset += 1) {
    result.push(perfUuid(namespaceCode, startSeq + offset));
  }

  return result;
}

function perfUuid(namespaceCode, seq) {
  return `${hexPad(namespaceCode, 8)}-0000-4000-8000-${hexPad(seq, 12)}`;
}

function hexPad(value, width) {
  return Number(value).toString(16).padStart(width, '0');
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

function metricKey(apiName) {
  return apiName.replace(/-/g, '_');
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
