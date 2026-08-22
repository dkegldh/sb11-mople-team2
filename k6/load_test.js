import http from 'k6/http';
import {check, sleep} from 'k6';
import {Rate, Trend} from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.2/index.js';

// 커스텀 Metrics 추가

// 에러율
const errorRate = new Rate('error_rate');

// 로그인
const loginTrend = new Trend('login_duration');

// 조회
const contentDetailTrend = new Trend('content_detail_duration');
const contentListTrend = new Trend('content_list_duration');

// 검색
const contentSearchTrend = new Trend('content_search_duration');
const playlistSearchTrend = new Trend('playlist_search_duration');
const userSearchTrend = new Trend('user_search_duration');

// 쓰기
const contentCreateTrend = new Trend('content_create_duration');
const playlistCreateTrend = new Trend('playlist_create_duration');

// Test Options
export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],

  scenarios: {

    // 조회 부하
    read_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        {duration: '1m', target: 50}, // 1분간 0VU -> 50VU로 서서히 증가
        {duration: '1m', target: 100}, // 1분간 50VU -> 100VU로 서서히 증가
        {duration: '2m', target: 200}, // 2분간 100VU -> 200VU로 서서히 증가
        {duration: '3m', target: 500}, // 3분간 200VU -> 500VU로 서서히 증가
        {duration: '3m', target: 200}, // 3분간 500VU -> 200VU로 서서히 감소
        {duration: '2m', target: 100}, // 2분간 200VU -> 100VU로 서서히 감소
        {duration: '1m', target: 50}, // 1분간 100VU -> 50VU로 서서히 감소
        {duration: '1m', target: 0}, // 1분간 50VU -> 0VU로 감소(종료)
      ],
      exec: 'readLoad',
    },

    // 쓰기 부하
    write_load: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 1,
      maxDuration: '20m',
      gracefulStop: '2m',
      exec: 'writeLoad',
    },
  },

  thresholds: {
    http_req_duration: ['p(95)<200', 'p(99)<500'],
    error_rate: ['rate<0.01'],
  },
};

// 환경 구성
const BASE_URL = 'http://localhost:8080/api';

const PASSWORD = '12345678';

const ADMIN_EMAIL = 'admin@mople.com';
const ADMIN_PASSWORD = 'Admin1234!';

// 시드 데이터의 실제 Content UUID
const SAMPLE_CONTENT_IDS = [
  'b3ac7de9-e94f-4faf-b14f-8e0f2b296c4c',
  'eb4262d4-a42c-4d10-a1ff-f3ba87ece3b3',
  'cb0222bf-80a7-4081-80f8-87e45f7c6cce',
];

// 검색 키워드
const CONTENT_KEYWORDS = [
  '1',
  '10',
  '100',
  '1000',
  '9999',
];

const PLAYLIST_KEYWORDS = [
  '1',
  '10',
  '100',
  '1000',
  '10000',
  '19999',
];

const USER_KEYWORDS = [
  '1',
  '10',
  '100',
  '1000',
  '9999',
];

// 부하 테스트에서 수정할 기존 사용자
const LOAD_TEST_USER_EMAIL = 'test1@test.com';
const LOAD_TEST_USER_ORIGINAL_NAME = 'test1';
const LOAD_TEST_USER_MODIFIED_NAME = '부하테스트 사용자 수정';

// 부하 테스트에서 생성한 Content ID
let loadTestContentIds = [];

// 부하 테스트에서 생성한 Playlist ID
let loadTestPlaylistIds = [];

// 수정한 사용자 ID
let loadTestUserId = null;

// Utility

function randomItem(array) {
  return array[Math.floor(Math.random() * array.length)];
}

function randomUser() {
  const userNumber = Math.floor(Math.random() * 10000) + 1;

  return {
    email: `test${userNumber}@test.com`,
    password: PASSWORD,
  };
}

// ADMIN 인증 + CSRF 토큰 발급
export function setup() {

  const adminLoginRes = http.post(
      `${BASE_URL}/auth/sign-in`,
      {
        username: ADMIN_EMAIL,
        password: ADMIN_PASSWORD,
      }
  );

  const adminLoginSuccess = check(adminLoginRes, {
    'admin login status is 200': (r) => r.status === 200,
  });

  if (!adminLoginSuccess) {
    throw new Error(
        `Admin login failed: status=${adminLoginRes.status}, body=${adminLoginRes.body}`
    );
  }

  const adminAccessToken = adminLoginRes.json('accessToken');

  /*
   * CookieCsrfTokenRepository가 XSRF-TOKEN 쿠키를 발급하도록
   * 인증된 GET 요청을 한 번 호출한다.
   */
  const csrfRes = http.get(
      `${BASE_URL}/contents?limit=1&sortDirection=DESCENDING&sortBy=createdAt`,
      {
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
      }
  );

  const csrfCookie = csrfRes.cookies['XSRF-TOKEN']
  ?.find((cookie) => cookie.value)?.value;

  if (!csrfCookie) {
    throw new Error(
        `CSRF token not found: status=${csrfRes.status}, cookies=${JSON.stringify(csrfRes.cookies)}`
    );
  }

  const csrfToken = decodeURIComponent(csrfCookie);

  return {
    adminAccessToken,
    csrfToken,
  };
}

// 테스트 시나리오
export function readLoad(data) {

  // 1. 사용자 로그인
  const user = randomUser();

  const loginRes = http.post(
      `${BASE_URL}/auth/sign-in`,
      {
        username: user.email,
        password: user.password,
      }
  );

  const loginSuccess = check(loginRes, {
    'login status is 200': (r) => r.status === 200,
  });

  errorRate.add(!loginSuccess);
  loginTrend.add(loginRes.timings.duration);

  if (!loginSuccess) {
    return;
  }

  const accessToken = loginRes.json('accessToken');

  const authHeaders = {
    Authorization: `Bearer ${accessToken}`,
  };

  sleep(0.1);

  // 2. 콘텐츠 상세 조회
  const contentId = randomItem(SAMPLE_CONTENT_IDS);

  const detailRes = http.get(
      `${BASE_URL}/contents/${contentId}`,
      {
        headers: authHeaders,
      }
  );

  const detailSuccess = check(detailRes, {
    'content detail status is 200': (r) => r.status === 200,
  });

  errorRate.add(!detailSuccess);
  contentDetailTrend.add(detailRes.timings.duration);

  sleep(0.05);

  // 3. 콘텐츠 목록 조회
  const contentListRes = http.get(
      `${BASE_URL}/contents?limit=20&sortDirection=DESCENDING&sortBy=createdAt`,
      {
        headers: authHeaders,
      }
  );

  const contentListSuccess = check(contentListRes, {
    'content list status is 200': (r) => r.status === 200,
  });

  errorRate.add(!contentListSuccess);
  contentListTrend.add(contentListRes.timings.duration);

  sleep(0.1);

  // 4. 콘텐츠 키워드 검색
  const contentKeyword = randomItem(CONTENT_KEYWORDS);

  const contentSearchRes = http.get(
      `${BASE_URL}/contents?keywordLike=${encodeURIComponent(
          contentKeyword)}&limit=20&sortDirection=DESCENDING&sortBy=createdAt`,
      {
        headers: authHeaders,
      }
  );

  const contentSearchSuccess = check(contentSearchRes, {
    'content search status is 200': (r) => r.status === 200,
  });

  errorRate.add(!contentSearchSuccess);
  contentSearchTrend.add(contentSearchRes.timings.duration);

  sleep(0.1);

  // 5. 플레이리스트 키워드 검색
  const playlistKeyword = randomItem(PLAYLIST_KEYWORDS);

  const playlistSearchRes = http.get(
      `${BASE_URL}/playlists?keywordLike=${encodeURIComponent(
          playlistKeyword)}&limit=20&sortDirection=DESCENDING&sortBy=updatedAt`,
      {
        headers: authHeaders,
      }
  );

  const playlistSearchSuccess = check(playlistSearchRes, {
    'playlist search status is 200': (r) => r.status === 200,
  });

  errorRate.add(!playlistSearchSuccess);
  playlistSearchTrend.add(playlistSearchRes.timings.duration);

  sleep(0.1);

  // 6. 사용자 키워드 검색
  const userKeyword = randomItem(USER_KEYWORDS);

  const adminAuthHeaders = {
    Authorization: `Bearer ${data.adminAccessToken}`,
  };

  const userSearchRes = http.get(
      `${BASE_URL}/users?emailLike=${encodeURIComponent(
          userKeyword)}&limit=20&sortDirection=DESCENDING&sortBy=email`,
      {
        headers: adminAuthHeaders,
      }
  );

  const userSearchSuccess = check(userSearchRes, {
    'user search status is 200': (r) => r.status === 200,
  });

  errorRate.add(!userSearchSuccess);
  userSearchTrend.add(userSearchRes.timings.duration);

  sleep(0.2);
}

export function writeLoad(data) {

  // 5분 시점
  sleep(5 * 60);

  console.log('[WRITE 1 START]');

  executeWriteScenario(
      data.adminAccessToken,
      data.csrfToken,
      'write1',
      2,
      12
  );

  console.log(
      `[WRITE 1 END] contentIds=${JSON.stringify(loadTestContentIds)}, ` +
      `playlistIds=${JSON.stringify(loadTestPlaylistIds)}`
  );

  // 9분 시점
  sleep(4 * 60);

  console.log('[WRITE 2 START]');

  executeWriteScenario(
      data.adminAccessToken,
      data.csrfToken,
      'write2',
      0,
      8
  );

  updateLoadTestContent(
      data.adminAccessToken,
      data.csrfToken
  );

  updateLoadTestUser(
      data.adminAccessToken,
      data.csrfToken
  );

  console.log('[WRITE 2 END]');

  // 13분 시점
  sleep(4 * 60);

  console.log('[WRITE 3 START]');

  const cleanupErrors = [];

  for (const cleanup of [
    deleteLoadTestContents,
    deleteLoadTestPlaylists,
    restoreLoadTestUser,
  ]) {
    try {
      cleanup(data.adminAccessToken, data.csrfToken);
    } catch (e) {
      cleanupErrors.push(e.message);
    }
  }

  console.log('[WRITE 3 END]');

  if (cleanupErrors.length > 0) {
    throw new Error(`정리 실패: ${JSON.stringify(cleanupErrors)}`);
  }
}

// 콘텐츠, 플레이리스트 추가
function executeWriteScenario(
    adminAccessToken,
    csrfToken,
    prefix,
    contentCount,
    playlistCount
) {

  // 1. 관리자 콘텐츠 추가
  for (let i = 1; i <= contentCount; i++) {

    const uniqueId = `${prefix}_${Date.now()}_${i}`;

    const contentRes = http.post(
        `${BASE_URL}/contents`,
        {
          request: http.file(
              JSON.stringify({
                type: 'MOVIE',
                title: `부하테스트 콘텐츠 ${uniqueId}`,
                description: `부하테스트용 콘텐츠 ${uniqueId}`,
                tags: ['테스트'],
              }),
              'request.json',
              'application/json'
          ),
        },
        {
          headers: {
            Authorization: `Bearer ${adminAccessToken}`,
            'X-XSRF-TOKEN': csrfToken,
            Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
          },
        }
    );

    const contentCreateSuccess = check(contentRes, {
      'content create status is 201': (r) => r.status === 201,
    });

    errorRate.add(!contentCreateSuccess);
    contentCreateTrend.add(contentRes.timings.duration);

    if (!contentCreateSuccess) {
      console.log(
          `[CONTENT CREATE FAIL] status=${contentRes.status}, body=${contentRes.body}`
      );
    }

    if (contentCreateSuccess) {
      const content = contentRes.json();

      if (content.id) {
        loadTestContentIds.push(content.id);
      }
    }
  }

  // 2. 관리자 플레이리스트 추가
  for (let i = 1; i <= playlistCount; i++) {

    const uniqueId = `${prefix}_${Date.now()}_${i}`;

    const playlistRes = http.post(
        `${BASE_URL}/playlists`,
        JSON.stringify({
          title: `부하테스트 플레이리스트 ${uniqueId}`,
          description: `부하테스트용 플레이리스트 ${uniqueId}`,
        }),
        {
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${adminAccessToken}`,
            'X-XSRF-TOKEN': csrfToken,
            Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
          },
        }
    );

    const playlistCreateSuccess = check(playlistRes, {
      'playlist create status is 201': (r) => r.status === 201,
    });

    errorRate.add(!playlistCreateSuccess);
    playlistCreateTrend.add(playlistRes.timings.duration);

    if (!playlistCreateSuccess) {
      console.log(
          `[PLAYLIST CREATE FAIL] status=${playlistRes.status}, body=${playlistRes.body}`
      );
    }

    if (playlistCreateSuccess) {
      const playlist = playlistRes.json();

      if (playlist.id) {
        loadTestPlaylistIds.push(playlist.id);
      }
    }
  }
}

// 1번째 쓰기 작업에서 생성된 콘텐츠 1개 수정
function updateLoadTestContent(adminAccessToken, csrfToken) {

  if (loadTestContentIds.length === 0) {
    console.log('[CONTENT UPDATE SKIP] 수정할 콘텐츠가 없습니다.');
    return;
  }

  const contentId = loadTestContentIds[0];

  const contentDetailRes = http.get(
      `${BASE_URL}/contents/${contentId}`,
      {
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
      }
  );

  const detailSuccess = check(contentDetailRes, {
    'load test content detail status is 200': (r) => r.status === 200,
  });

  errorRate.add(!detailSuccess);

  if (!detailSuccess) {
    console.log(
        `[CONTENT UPDATE DETAIL FAIL] id=${contentId}, ` +
        `status=${contentDetailRes.status}, body=${contentDetailRes.body}`
    );
    return;
  }

  const updateRes = http.patch(
      `${BASE_URL}/contents/${contentId}`,
      {
        request: http.file(
            JSON.stringify({
              description: '부하테스트 수정 요청',
            }),
            'request.json',
            'application/json'
        ),
      },
      {
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
          'X-XSRF-TOKEN': csrfToken,
          Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
        },
      }
  );

  const updateSuccess = check(updateRes, {
    'content update status is 200': (r) => r.status === 200,
  });

  errorRate.add(!updateSuccess);

  if (!updateSuccess) {
    console.log(
        `[CONTENT UPDATE FAIL] id=${contentId}, ` +
        `status=${updateRes.status}, body=${updateRes.body}`
    );
  } else {
    console.log(
        `[CONTENT UPDATE SUCCESS] id=${contentId}, status=${updateRes.status}`
    );
  }
}

// 기존 사용자 1명 수정
function updateLoadTestUser(adminAccessToken, csrfToken) {

  const userSearchRes = http.get(
      `${BASE_URL}/users?emailLike=${encodeURIComponent(
          LOAD_TEST_USER_EMAIL)}&limit=20&sortDirection=DESCENDING&sortBy=email`,
      {
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
        },
      }
  );

  const searchSuccess = check(userSearchRes, {
    'load test user search status is 200': (r) => r.status === 200,
  });

  errorRate.add(!searchSuccess);

  if (!searchSuccess) {
    return;
  }

  const users = extractItems(userSearchRes);

  const user = users.find(
      (item) => item.email === LOAD_TEST_USER_EMAIL
  );

  if (!user) {
    console.log(
        `부하 테스트 사용자 조회 실패: email=${LOAD_TEST_USER_EMAIL}`
    );
    return;
  }

  loadTestUserId = user.id;

  const updateRes = http.patch(
      `${BASE_URL}/users/${loadTestUserId}`,
      {
        request: http.file(
            JSON.stringify({
              name: LOAD_TEST_USER_MODIFIED_NAME,
            }),
            'request.json',
            'application/json'
        ),
        image: http.file('', 'empty'),
      },
      {
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
          'X-XSRF-TOKEN': csrfToken,
          Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
        },
      }
  );

  const updateSuccess = check(updateRes, {
    'load test user update status is 200': (r) => r.status === 200,
  });

  errorRate.add(!updateSuccess);

  if (!updateSuccess) {
    console.log(
        `[USER UPDATE FAIL] status=${updateRes.status}, body=${updateRes.body}`
    );
  }
}

// 생성된 콘텐츠 삭제
function deleteLoadTestContents(adminAccessToken, csrfToken) {

  const failedContentIds = [];

  for (const contentId of loadTestContentIds) {

    const deleteRes = http.del(
        `${BASE_URL}/contents/${contentId}`,
        null,
        {
          headers: {
            Authorization: `Bearer ${adminAccessToken}`,
            'X-XSRF-TOKEN': csrfToken,
            Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
          },
        }
    );

    const deleteSuccess = check(deleteRes, {
      'content delete status is 204': (r) => r.status === 204,
    });

    errorRate.add(!deleteSuccess);

    if (!deleteSuccess) {
      failedContentIds.push(contentId);

      console.log(
          `[CONTENT DELETE FAIL] id=${contentId}, ` +
          `status=${deleteRes.status}, body=${deleteRes.body}`
      );
    }
  }

  if (failedContentIds.length > 0) {
    loadTestContentIds = failedContentIds;

    throw new Error(
        `콘텐츠 정리 실패: ${JSON.stringify(failedContentIds)}`
    );
  }

  loadTestContentIds = [];
}

// 생성된 플레이리스트 삭제
function deleteLoadTestPlaylists(adminAccessToken, csrfToken) {

  const failedPlaylistIds = [];

  for (const playlistId of loadTestPlaylistIds) {

    const deleteRes = http.del(
        `${BASE_URL}/playlists/${playlistId}`,
        null,
        {
          headers: {
            Authorization: `Bearer ${adminAccessToken}`,
            'X-XSRF-TOKEN': csrfToken,
            Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
          },
        }
    );

    const deleteSuccess = check(deleteRes, {
      'playlist delete status is 204': (r) => r.status === 204,
    });

    errorRate.add(!deleteSuccess);

    if (!deleteSuccess) {
      failedPlaylistIds.push(playlistId);

      console.log(
          `[PLAYLIST DELETE FAIL] id=${playlistId}, ` +
          `status=${deleteRes.status}, body=${deleteRes.body}`
      );
    }
  }

  if (failedPlaylistIds.length > 0) {
    loadTestPlaylistIds = failedPlaylistIds;

    throw new Error(
        `플레이리스트 정리 실패: ${JSON.stringify(failedPlaylistIds)}`
    );
  }

  loadTestPlaylistIds = [];
}

// 수정했던 사용자 원복
function restoreLoadTestUser(adminAccessToken, csrfToken) {

  if (!loadTestUserId) {
    return;
  }

  const updateRes = http.patch(
      `${BASE_URL}/users/${loadTestUserId}`,
      {
        request: http.file(
            JSON.stringify({
              name: LOAD_TEST_USER_ORIGINAL_NAME,
            }),
            'request.json',
            'application/json'
        ),
        image: http.file('', 'empty'),
      },
      {
        headers: {
          Authorization: `Bearer ${adminAccessToken}`,
          'X-XSRF-TOKEN': csrfToken,
          Cookie: `XSRF-TOKEN=${encodeURIComponent(csrfToken)}`,
        },
      }
  );

  const restoreSuccess = check(updateRes, {
    'load test user restore status is 200': (r) => r.status === 200,
  });

  errorRate.add(!restoreSuccess);

  if (!restoreSuccess) {
    console.log(
        `[USER RESTORE FAIL] status=${updateRes.status}, body=${updateRes.body}`
    );

    throw new Error(
        `사용자 원복 실패: userId=${loadTestUserId}, status=${updateRes.status}`
    );
  }

// 복원 성공한 경우에만 상태 초기화
  loadTestUserId = null;
}

// 목록 응답에서 실제 데이터 배열 추출
function extractItems(response) {

  const body = response.json();

  if (Array.isArray(body)) {
    return body;
  }

  if (Array.isArray(body.content)) {
    return body.content;
  }

  if (Array.isArray(body.data)) {
    return body.data;
  }

  if (Array.isArray(body.items)) {
    return body.items;
  }

  return [];
}

export function handleSummary(data) {
  const metrics = [
    'login_duration',
    'content_detail_duration',
    'content_list_duration',
    'content_search_duration',
    'playlist_search_duration',
    'user_search_duration',
    'content_create_duration',
    'playlist_create_duration',
  ];

  let output = '\n===== LOAD TEST RESULT =====\n';

  for (const name of metrics) {
    const metric = data.metrics[name];

    if (!metric) {
      continue;
    }

    output += `
${name}
  avg : ${metric.values.avg.toFixed(2)} ms
  min : ${metric.values.min.toFixed(2)} ms
  med : ${metric.values.med.toFixed(2)} ms
  max : ${metric.values.max.toFixed(2)} ms
  p95 : ${metric.values['p(95)'].toFixed(2)} ms
  p99 : ${metric.values['p(99)'].toFixed(2)} ms
`;
  }

  return {
    stdout:
        textSummary(data, {indent: ' ', enableColors: true}) +
        output,
  };
}