package com.abhishek.resumeanalyzer.services;

import com.abhishek.resumeanalyzer.entities.JobQueue;
import com.abhishek.resumeanalyzer.entities.ProcessTracker;
import com.abhishek.resumeanalyzer.models.JobPriority;
import com.abhishek.resumeanalyzer.models.JobStatus;
import com.abhishek.resumeanalyzer.models.JobType;
import com.abhishek.resumeanalyzer.models.ProcessStatus;
import com.abhishek.resumeanalyzer.repos.ProcessTrackerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceTest {

    @Mock
    private FileParserService fileParserService;

    @Mock
    private ProcessTrackerRepository trackerRepository;

    @Mock
    private JobQueueService jobQueueService;

    @Mock
    private TrackerEventPublisher trackerEventPublisher;

    @InjectMocks
    private FileUploadService fileUploadService;

    private UUID testTrackerId;

    @BeforeEach
    void setUp() {
        testTrackerId = UUID.randomUUID();

        ReflectionTestUtils.setField(fileUploadService, "uploadDirectory", "./uploads");
        ReflectionTestUtils.setField(fileUploadService, "allowedExtensions", ".doc,.docx,.pdf");
    }

    // ==================== Single File Upload Tests ====================

    @Test
    void testHandleFileUpload_SingleFile_CreatesJob() throws IOException {
        // Arrange
        MultipartFile file = createMockFile("resume.pdf", "PDF content");
        ProcessTracker savedTracker = createMockTracker(testTrackerId, "resume.pdf");

        JobQueue mockJobQueue = createMockJobQueue();

        when(fileParserService.isValidFileFormat("resume.pdf")).thenReturn(true);
        when(trackerRepository.save(any(ProcessTracker.class))).thenReturn(savedTracker);
        when(jobQueueService.createJob(any(), any(), any(), any(), any())).thenReturn(mockJobQueue);

        // Act
        UUID result = fileUploadService.handleFileUpload(file);

        // Assert
        assertEquals(testTrackerId, result);

        // Tracker creation: initial, correlationId update, status update after job creation
        verify(trackerRepository, times(3)).save(any(ProcessTracker.class));

        // Verify a single job was enqueued
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jobQueueService).createJob(
            eq(JobType.RESUME_PROCESSING),
            any(byte[].class),
            metadataCaptor.capture(),
            eq(JobPriority.NORMAL),
            anyString()
        );

        Map<String, Object> metadata = metadataCaptor.getValue();
        assertEquals("resume.pdf", metadata.get("filename"));
        assertEquals(testTrackerId.toString(), metadata.get("trackerId"));
    }

    @Test
    void testHandleFileUpload_ZipFile_CreatesJobPerEntry() throws IOException {
        // Arrange
        MultipartFile file = createMockFile("batch.zip", buildZip(
            Map.of("resume1.pdf", "PDF1", "resume2.docx", "DOCX", "notes.txt", "ignore")));
        ProcessTracker savedTracker = createMockTracker(testTrackerId, "batch.zip");

        JobQueue mockJobQueue = createMockJobQueue();

        when(fileParserService.isValidFileFormat("resume1.pdf")).thenReturn(true);
        when(fileParserService.isValidFileFormat("resume2.docx")).thenReturn(true);
        when(fileParserService.isValidFileFormat("notes.txt")).thenReturn(false);
        when(trackerRepository.save(any(ProcessTracker.class))).thenReturn(savedTracker);
        when(jobQueueService.createJob(any(), any(), any(), any(), any())).thenReturn(mockJobQueue);

        // Act
        UUID result = fileUploadService.handleFileUpload(file);

        // Assert
        assertEquals(testTrackerId, result);

        // Only supported entries are enqueued
        verify(jobQueueService, times(2)).createJob(
            eq(JobType.RESUME_PROCESSING),
            any(byte[].class),
            any(),
            eq(JobPriority.NORMAL),
            anyString()
        );

        // Tracker totalFiles is updated to the number of jobs created (2)
        ArgumentCaptor<ProcessTracker> trackerCaptor = ArgumentCaptor.forClass(ProcessTracker.class);
        verify(trackerRepository, times(3)).save(trackerCaptor.capture());
        List<ProcessTracker> saved = trackerCaptor.getAllValues();
        assertEquals(2, saved.get(saved.size() - 1).getTotalFiles());
    }

    // ==================== Multiple File Upload Tests ====================

    @Test
    void testHandleMultipleFileUpload_CreatesJobPerFile() throws IOException {
        // Arrange
        List<MultipartFile> files = Arrays.asList(
            createMockFile("resume1.pdf", "PDF1"),
            createMockFile("resume2.docx", "DOCX"),
            createMockFile("resume3.pdf", "PDF2")
        );

        ProcessTracker savedTracker = createMockTracker(testTrackerId, "3 files");
        JobQueue mockJobQueue = createMockJobQueue();

        when(fileParserService.isValidFileFormat(anyString())).thenReturn(true);
        when(trackerRepository.save(any(ProcessTracker.class))).thenReturn(savedTracker);
        when(jobQueueService.createJob(any(), any(), any(), any(), any())).thenReturn(mockJobQueue);

        // Act
        UUID result = fileUploadService.handleMultipleFileUpload(files);

        // Assert
        assertEquals(testTrackerId, result);

        // Verify 3 jobs created in queue
        verify(jobQueueService, times(3)).createJob(
            eq(JobType.RESUME_PROCESSING),
            any(byte[].class),
            any(),
            eq(JobPriority.NORMAL),
            anyString()
        );
    }

    @Test
    void testHandleMultipleFileUpload_SingleZip_CreatesJobPerEntry() throws IOException {
        // Arrange
        MultipartFile zip = createMockFile("batch.zip", buildZip(
            Map.of("resume1.pdf", "PDF1", "resume2.docx", "DOCX")));

        ProcessTracker savedTracker = createMockTracker(testTrackerId, "1 files");
        JobQueue mockJobQueue = createMockJobQueue();

        when(fileParserService.isValidFileFormat("resume1.pdf")).thenReturn(true);
        when(fileParserService.isValidFileFormat("resume2.docx")).thenReturn(true);
        when(trackerRepository.save(any(ProcessTracker.class))).thenReturn(savedTracker);
        when(jobQueueService.createJob(any(), any(), any(), any(), any())).thenReturn(mockJobQueue);

        // Act
        UUID result = fileUploadService.handleMultipleFileUpload(Collections.singletonList(zip));

        // Assert
        assertEquals(testTrackerId, result);
        verify(jobQueueService, times(2)).createJob(
            eq(JobType.RESUME_PROCESSING),
            any(byte[].class),
            any(),
            eq(JobPriority.NORMAL),
            anyString()
        );
    }

    @Test
    void testHandleMultipleFileUpload_EmptyList_ThrowsException() {
        // Arrange
        List<MultipartFile> emptyFiles = Collections.emptyList();

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.handleMultipleFileUpload(emptyFiles)
        );

        assertEquals("No files provided", ex.getMessage());
        verify(trackerRepository, never()).save(any());
    }

    // ==================== Validation Tests ====================

    @Test
    void testHandleFileUpload_EmptyFile_ThrowsException() {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.handleFileUpload(file)
        );

        assertEquals("File is empty", ex.getMessage());
    }

    @Test
    void testHandleFileUpload_NoFilename_ThrowsException() {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(null);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.handleFileUpload(file)
        );

        assertEquals("Filename is required", ex.getMessage());
    }

    @Test
    void testHandleFileUpload_InvalidFormat_ThrowsException() {
        // Arrange
        MultipartFile file = createMockFile("document.txt", "TXT content");
        when(fileParserService.isValidFileFormat("document.txt")).thenReturn(false);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.handleFileUpload(file)
        );

        assertTrue(ex.getMessage().contains("Unsupported file format"));
    }

    @Test
    void testHandleFileUpload_FileTooLarge_ThrowsException() {
        // Arrange
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("large.pdf");
        when(file.getSize()).thenReturn(60L * 1024 * 1024); // 60 MB
        when(fileParserService.isValidFileFormat("large.pdf")).thenReturn(true);

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.handleFileUpload(file)
        );

        assertTrue(ex.getMessage().contains("exceeds maximum allowed size"));
    }

    // ==================== Get Process Status Tests ====================

    @Test
    void testGetProcessStatus_Found() {
        // Arrange
        ProcessTracker tracker = createMockTracker(testTrackerId, "test.pdf");
        when(trackerRepository.findById(testTrackerId)).thenReturn(Optional.of(tracker));

        // Act
        ProcessTracker result = fileUploadService.getProcessStatus(testTrackerId);

        // Assert
        assertNotNull(result);
        assertEquals(testTrackerId, result.getId());
        verify(trackerRepository).findById(testTrackerId);
    }

    @Test
    void testGetProcessStatus_NotFound_ThrowsException() {
        // Arrange
        when(trackerRepository.findById(testTrackerId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> fileUploadService.getProcessStatus(testTrackerId)
        );

        assertTrue(ex.getMessage().contains("Tracker not found"));
    }

    // ==================== Helper Methods ====================

    private MultipartFile createMockFile(String filename, String content) {
        return createMockFile(filename, content.getBytes());
    }

    private MultipartFile createMockFile(String filename, byte[] content) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.isEmpty()).thenReturn(false);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        lenient().when(file.getSize()).thenReturn((long) content.length);
        try {
            lenient().when(file.getBytes()).thenReturn(content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }

    private byte[] buildZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private JobQueue createMockJobQueue() {
        return JobQueue.builder()
                .id(UUID.randomUUID())
                .jobType(JobType.RESUME_PROCESSING)
                .status(JobStatus.PENDING)
                .priority(JobPriority.NORMAL.getValue())
                .build();
    }

    private ProcessTracker createMockTracker(UUID id, String filename) {
        ProcessTracker tracker = ProcessTracker.builder()
                .id(id)
                .status(ProcessStatus.INITIATED)
                .uploadedFilename(filename)
                .totalFiles(1)
                .processedFiles(0)
                .failedFiles(0)
                .build();
        tracker.setCorrelationId("test-correlation-" + id);
        return tracker;
    }
}
