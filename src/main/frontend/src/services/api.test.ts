import { describe, it, expect, beforeAll, afterEach, afterAll } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { uploadResumes, getProcessStatus } from './api';
import { mockUploadResponse, mockProcessTracker } from '../test/mockData';

// MSW server setup for REST API
const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('API Service', () => {
  describe('File Upload', () => {
    it('should upload resume files as multipart form data', async () => {
      let requestContentType: string | null = null;
      server.use(
        http.post('http://localhost:8080/api/upload/resume', ({ request }) => {
          requestContentType = request.headers.get('content-type');
          return HttpResponse.json(mockUploadResponse);
        })
      );

      const files = [new File(['resume content'], 'resume.pdf', { type: 'application/pdf' })];
      const response = await uploadResumes(files);
      
      // Guard against axios JSON-serializing FormData (which breaks the
      // backend's multipart parsing). jsdom sends text/plain; a real browser
      // sends multipart/form-data; boundary=..., so assert it is NOT JSON.
      expect(requestContentType).not.toMatch(/application\/json/);
      expect(response.trackerId).toBe(mockUploadResponse.trackerId);
      expect(response.message).toBe(mockUploadResponse.message);
    });

    it('should handle upload errors', async () => {
      server.use(
        http.post('http://localhost:8080/api/upload/resume', () => {
          return HttpResponse.json(
            { error: 'File too large' },
            { status: 400 }
          );
        })
      );

      const files = [new File(['content'], 'large.pdf', { type: 'application/pdf' })];

      await expect(uploadResumes(files)).rejects.toThrow();
    });

    it('should handle multiple files upload', async () => {
      server.use(
        http.post('http://localhost:8080/api/upload/resume', () => {
          return HttpResponse.json(mockUploadResponse);
        })
      );

      const files = [
        new File(['content1'], 'resume1.pdf', { type: 'application/pdf' }),
        new File(['content2'], 'resume2.pdf', { type: 'application/pdf' }),
      ];
      
      const response = await uploadResumes(files);
      expect(response.trackerId).toBeDefined();
    });
  });

  describe('Processing Status', () => {
    it('should get processing status', async () => {
      const trackerId = 'track-123';
      const statusResponse = {
        id: mockProcessTracker.id,
        status: mockProcessTracker.status,
        totalFiles: mockProcessTracker.totalFiles,
        processedFiles: mockProcessTracker.processedFiles,
        failedFiles: mockProcessTracker.failedFiles,
        startTime: mockProcessTracker.createdAt,
        message: mockProcessTracker.message,
      };
      
      server.use(
        http.get(`http://localhost:8080/api/upload/status/${trackerId}`, () => {
          return HttpResponse.json(statusResponse);
        })
      );

      const response = await getProcessStatus(trackerId);
      
      expect(response.id).toBe(mockProcessTracker.id);
      expect(response.status).toBe('COMPLETED');
    });

    it('should handle status check errors', async () => {
      server.use(
        http.get('http://localhost:8080/api/upload/status/invalid', () => {
          return HttpResponse.json(
            { error: 'Tracker not found' },
            { status: 404 }
          );
        })
      );

      await expect(getProcessStatus('invalid')).rejects.toThrow();
    });
  });

  describe('Error Handling', () => {
    it('should handle network errors', async () => {
      server.use(
        http.post('http://localhost:8080/api/upload/resume', () => {
          return HttpResponse.error();
        })
      );

      const files = [new File(['content'], 'resume.pdf', { type: 'application/pdf' })];

      await expect(uploadResumes(files)).rejects.toThrow();
    });

    it('should handle timeout', async () => {
      server.use(
        http.get('http://localhost:8080/api/upload/status/slow', async () => {
          await new Promise((resolve) => setTimeout(resolve, 100));
          const statusResponse = {
            id: 'slow',
            status: 'PROCESSING',
            totalFiles: 1,
            processedFiles: 0,
            failedFiles: 0,
            startTime: new Date().toISOString(),
          };
          return HttpResponse.json(statusResponse);
        })
      );

      // With proper timeout configuration, this would succeed
      const response = await getProcessStatus('slow');
      expect(response.id).toBe('slow');
    });
  });
});
