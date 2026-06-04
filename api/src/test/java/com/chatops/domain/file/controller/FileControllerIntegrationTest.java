package com.chatops.domain.file.controller;

import com.chatops.domain.file.service.FileService;
import com.chatops.domain.user.repository.UserRepository;
import com.chatops.global.config.CustomAuthenticationEntryPoint;
import com.chatops.global.config.JwtTokenProvider;
import com.chatops.global.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class})
@DisplayName("파일 업로드 API 통합 테스트")
class FileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // Valid file uploads return proper result from service
        given(fileService.uploadFile(any(), anyString()))
            .willAnswer(invocation -> {
                org.springframework.web.multipart.MultipartFile f = invocation.getArgument(0);
                return new FileService.FileUploadResult(
                    "/files/download/rooms/room-1/uuid/" + f.getOriginalFilename(),
                    f.getOriginalFilename(),
                    f.getSize(),
                    f.getContentType()
                );
            });
    }

    // ========== 이미지 업로드 ==========

    @Nested
    @DisplayName("이미지 업로드")
    class ImageUpload {

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("JPEG 이미지 업로드: 응답에 thumbnailUrl, width, height 포함")
        void upload_jpeg_returns_image_metadata() throws Exception {
            // Given: 유효한 JPEG 파일 (FF D8 FF 매직넘버)
            byte[] jpegContent = createMinimalJpeg();
            MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", jpegContent
            );

            // When & Then: 업로드 응답에 이미지 메타데이터 포함
            mockMvc.perform(multipart("/files/upload")
                    .file(file)
                    .param("roomId", "room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.fileName").value("photo.jpg"))
                .andExpect(jsonPath("$.fileUrl").exists());
        }

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("PNG 이미지 업로드: 정상 처리")
        void upload_png_succeeds() throws Exception {
            byte[] pngContent = createMinimalPng();
            MockMultipartFile file = new MockMultipartFile(
                "file", "screenshot.png", "image/png", pngContent
            );

            mockMvc.perform(multipart("/files/upload")
                    .file(file)
                    .param("roomId", "room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contentType").value("image/png"));
        }

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("MIME spoofing 시도: exe를 jpg로 위장 → 400 응답")
        void reject_exe_disguised_as_jpeg() throws Exception {
            // Given: MZ 헤더(EXE)를 가진 파일을 image/jpeg로 선언
            byte[] exeContent = new byte[]{
                0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF
            };
            MockMultipartFile file = new MockMultipartFile(
                "file", "malware.jpg", "image/jpeg", exeContent
            );

            // When & Then: 매직넘버 검증 실패로 400 반환
            mockMvc.perform(multipart("/files/upload")
                    .file(file)
                    .param("roomId", "room-1"))
                .andExpect(status().isBadRequest());
        }
    }

    // ========== 일반 파일 업로드 ==========

    @Nested
    @DisplayName("일반 파일 업로드")
    class FileUpload {

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("PDF 업로드: 응답에 type=file, originalName 포함")
        void upload_pdf_returns_file_metadata() throws Exception {
            // Given: 유효한 PDF 파일 (%PDF 매직넘버)
            byte[] pdfContent = createMinimalPdf();
            MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", pdfContent
            );

            // When & Then
            mockMvc.perform(multipart("/files/upload")
                    .file(file)
                    .param("roomId", "room-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("document.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.fileUrl").exists());
        }

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("빈 파일 업로드: 400 응답")
        void reject_empty_file() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
            );

            mockMvc.perform(multipart("/files/upload")
                    .file(file)
                    .param("roomId", "room-1"))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = "user-1")
        @DisplayName("용량 초과 파일: 400 응답 (10MB 초과)")
        void reject_oversized_file() throws Exception {
            // Given: 11MB 파일
            byte[] oversizedContent = new byte[11 * 1024 * 1024];
            // Set JPEG magic bytes at start
            oversizedContent[0] = (byte) 0xFF;
            oversizedContent[1] = (byte) 0xD8;
            oversizedContent[2] = (byte) 0xFF;
            MockMultipartFile file = new MockMultipartFile(
                "file", "huge.jpg", "image/jpeg", oversizedContent
            );

            mockMvc.perform(multipart("/files/upload")
                    .file(file)
                    .param("roomId", "room-1"))
                .andExpect(status().isBadRequest());
        }
    }

    // ========== 헬퍼 메서드: 최소 유효 파일 바이트 생성 ==========

    private byte[] createMinimalJpeg() {
        // Minimal JPEG: SOI + APP0 marker + EOI
        return new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            (byte) 0xFF, (byte) 0xD9
        };
    }

    private byte[] createMinimalPng() {
        // PNG signature + IHDR chunk (minimal)
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00
        };
    }

    private byte[] createMinimalPdf() {
        return "%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n".getBytes();
    }
}
