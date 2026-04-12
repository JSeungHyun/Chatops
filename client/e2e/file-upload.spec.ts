import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const FIXTURES_DIR = path.join(__dirname, 'fixtures');
const TEST_IMAGE_PATH = path.join(FIXTURES_DIR, 'test-image.png');
const TEST_FILE_PATH = path.join(FIXTURES_DIR, 'test-doc.txt');
const LARGE_FILE_PATH = path.join(FIXTURES_DIR, 'large-file.bin');

const BASE_URL = 'http://localhost';
const API_URL = `${BASE_URL}/api`;

function ensureFixtures() {
  if (!fs.existsSync(FIXTURES_DIR)) {
    fs.mkdirSync(FIXTURES_DIR, { recursive: true });
  }
  if (!fs.existsSync(TEST_IMAGE_PATH)) {
    fs.writeFileSync(TEST_IMAGE_PATH, Buffer.from([
      0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
      0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52,
      0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
      0x08, 0x02, 0x00, 0x00, 0x00, 0x90, 0x77, 0x53, 0xde,
      0x00, 0x00, 0x00, 0x0c, 0x49, 0x44, 0x41, 0x54,
      0x08, 0xd7, 0x63, 0xf8, 0xcf, 0xc0, 0x00, 0x00,
      0x00, 0x02, 0x00, 0x01, 0xe2, 0x21, 0xbc, 0x33,
      0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4e, 0x44,
      0xae, 0x42, 0x60, 0x82,
    ]));
  }
  if (!fs.existsSync(TEST_FILE_PATH)) {
    fs.writeFileSync(TEST_FILE_PATH, 'This is a test document for ChatOps E2E testing.');
  }
  if (!fs.existsSync(LARGE_FILE_PATH)) {
    fs.writeFileSync(LARGE_FILE_PATH, Buffer.alloc(11 * 1024 * 1024, 'x'));
  }
}

async function loginViaAPI(request: any, email: string) {
  const res = await request.post(`${API_URL}/auth/login`, {
    data: { email, password: 'test1234' },
  });
  return res.json();
}

test.describe('File Upload API', () => {
  let token: string;

  test.beforeAll(async ({ request }) => {
    ensureFixtures();
    try {
      await request.post(`${API_URL}/auth/register`, {
        data: { email: 'admin@admin.com', nickname: 'Admin', password: 'test1234' },
      });
    } catch { /* may exist */ }
    const loginData = await loginViaAPI(request, 'admin@admin.com');
    token = loginData.accessToken;
  });

  test('should upload an image file successfully', async ({ request }) => {
    const res = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'test-image.png',
          mimeType: 'image/png',
          buffer: fs.readFileSync(TEST_IMAGE_PATH),
        },
      },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.fileUrl).toContain('/files/download/');
    expect(body.fileName).toBe('test-image.png');
    expect(body.contentType).toBe('image/png');
    expect(body.fileSize).toBeGreaterThan(0);
  });

  test('should upload a text file successfully', async ({ request }) => {
    const res = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'test-doc.txt',
          mimeType: 'text/plain',
          buffer: fs.readFileSync(TEST_FILE_PATH),
        },
      },
    });

    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.fileUrl).toContain('/files/download/');
    expect(body.fileName).toBe('test-doc.txt');
    expect(body.contentType).toBe('text/plain');
  });

  test('should reject file exceeding 10MB', async ({ request }) => {
    const res = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'large-file.bin',
          mimeType: 'application/octet-stream',
          buffer: fs.readFileSync(LARGE_FILE_PATH),
        },
      },
    });

    expect([400, 413]).toContain(res.status());
  });

  test('should reject disallowed MIME type', async ({ request }) => {
    const res = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'script.sh',
          mimeType: 'application/x-sh',
          buffer: Buffer.from('#!/bin/bash\necho "hello"'),
        },
      },
    });

    expect(res.status()).toBe(400);
  });

  test('should reject unauthenticated upload', async ({ request }) => {
    const res = await request.post(`${API_URL}/files/upload`, {
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'test.txt',
          mimeType: 'text/plain',
          buffer: Buffer.from('test'),
        },
      },
    });

    expect(res.status()).toBe(401);
  });

  test('should download file via presigned URL redirect', async ({ request }) => {
    const uploadRes = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'download-test.txt',
          mimeType: 'text/plain',
          buffer: fs.readFileSync(TEST_FILE_PATH),
        },
      },
    });
    const { fileUrl } = await uploadRes.json();

    // Presigned URL now goes through Nginx /minio/ proxy, so the redirect
    // points to a relative /minio/ path that Nginx can resolve
    const downloadRes = await request.fetch(`${API_URL}${fileUrl}`, {
      headers: { Authorization: `Bearer ${token}` },
      maxRedirects: 0,
    });

    expect(downloadRes.status()).toBe(302);
    const location = downloadRes.headers()['location'];
    expect(location).toBeTruthy();
    // URL should now be /minio/... instead of http://minio:9000/...
    expect(location).toContain('/minio/');
    expect(location).toContain('X-Amz-Signature');
  });

  test('should block path traversal in download', async ({ request }) => {
    const res = await request.get(`${API_URL}/files/download/..%2F..%2Fetc%2Fpasswd`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    // Rejected or not found — must NOT return real file content
    expect([400, 404, 500]).toContain(res.status());
  });
});

test.describe('File Message Flow (API)', () => {
  let token: string;
  let roomId: string;

  test.beforeAll(async ({ request }) => {
    ensureFixtures();
    const loginData = await loginViaAPI(request, 'admin@admin.com');
    token = loginData.accessToken;

    const roomsRes = await request.get(`${API_URL}/chats`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    const rooms = await roomsRes.json();
    if (rooms.length > 0) {
      roomId = rooms[0].id;
    }
  });

  test('should send IMAGE message with fileUrl', async ({ request }) => {
    test.skip(!roomId, 'No chat room available');

    const uploadRes = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'chat-image.png',
          mimeType: 'image/png',
          buffer: fs.readFileSync(TEST_IMAGE_PATH),
        },
      },
    });
    const { fileUrl } = await uploadRes.json();

    const msgRes = await request.post(`${API_URL}/chats/${roomId}/messages`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { content: 'chat-image.png', type: 'IMAGE', fileUrl },
    });

    expect(msgRes.status()).toBe(200);
    const msg = await msgRes.json();
    expect(msg.type).toBe('IMAGE');
    expect(msg.fileUrl).toBe(fileUrl);
    expect(msg.content).toBe('chat-image.png');
  });

  test('should send FILE message with fileUrl', async ({ request }) => {
    test.skip(!roomId, 'No chat room available');

    const uploadRes = await request.post(`${API_URL}/files/upload`, {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        roomId: 'test-room',
        file: {
          name: 'report.txt',
          mimeType: 'text/plain',
          buffer: fs.readFileSync(TEST_FILE_PATH),
        },
      },
    });
    const { fileUrl } = await uploadRes.json();

    const msgRes = await request.post(`${API_URL}/chats/${roomId}/messages`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { content: 'report.txt', type: 'FILE', fileUrl },
    });

    expect(msgRes.status()).toBe(200);
    const msg = await msgRes.json();
    expect(msg.type).toBe('FILE');
    expect(msg.fileUrl).toBe(fileUrl);
  });

  test('should include file messages in room message history', async ({ request }) => {
    test.skip(!roomId, 'No chat room available');

    const res = await request.get(`${API_URL}/chats/${roomId}/messages?page=1&limit=10`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    const fileMessages = body.data.filter(
      (m: any) => m.type === 'IMAGE' || m.type === 'FILE',
    );
    expect(fileMessages.length).toBeGreaterThan(0);
    // Verify at least one file message has a valid fileUrl
    const withUrl = fileMessages.filter((m: any) => m.fileUrl);
    expect(withUrl.length).toBeGreaterThan(0);
    for (const msg of withUrl) {
      expect(msg.fileUrl).toContain('/files/download/');
    }
  });

  test('should handle TEXT message without fileUrl', async ({ request }) => {
    test.skip(!roomId, 'No chat room available');

    const msgRes = await request.post(`${API_URL}/chats/${roomId}/messages`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { content: 'Hello, text message test', type: 'TEXT' },
    });

    expect(msgRes.status()).toBe(200);
    const msg = await msgRes.json();
    expect(msg.type).toBe('TEXT');
    // Jackson non_null setting omits null fields from JSON
    expect(msg.fileUrl).toBeUndefined();
  });
});
