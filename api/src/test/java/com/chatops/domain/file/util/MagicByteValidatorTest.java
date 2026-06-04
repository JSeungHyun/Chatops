package com.chatops.domain.file.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MagicByteValidator 단위 테스트")
class MagicByteValidatorTest {

    // ========== 정상 이미지 MIME 검증 ==========

    @Nested
    @DisplayName("정상 이미지 파일 검증")
    class ValidImageFiles {

        @Test
        @DisplayName("JPEG 파일: FF D8 FF 매직넘버로 검증 통과")
        void validate_jpeg_magic_bytes() {
            byte[] jpegHeader = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
            };

            boolean result = MagicByteValidator.validate(jpegHeader, "image/jpeg");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("PNG 파일: 89 50 4E 47 매직넘버로 검증 통과")
        void validate_png_magic_bytes() {
            byte[] pngHeader = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
            };

            boolean result = MagicByteValidator.validate(pngHeader, "image/png");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("GIF 파일: GIF89a 시그니처로 검증 통과")
        void validate_gif_magic_bytes() {
            // GIF89a = 47 49 46 38 39 61
            byte[] gifHeader = new byte[]{
                0x47, 0x49, 0x46, 0x38, 0x39, 0x61,
                0x00, 0x01, 0x00, 0x01, 0x00, 0x00
            };

            boolean result = MagicByteValidator.validate(gifHeader, "image/gif");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("WebP 파일: RIFF...WEBP 시그니처로 검증 통과")
        void validate_webp_magic_bytes() {
            // RIFF(52 49 46 46) + size(4 bytes) + WEBP(57 45 42 50)
            byte[] webpHeader = new byte[]{
                0x52, 0x49, 0x46, 0x46,  // RIFF
                0x00, 0x00, 0x00, 0x00,  // file size (placeholder)
                0x57, 0x45, 0x42, 0x50   // WEBP
            };

            boolean result = MagicByteValidator.validate(webpHeader, "image/webp");

            assertThat(result).isTrue();
        }
    }

    // ========== 정상 파일 MIME 검증 ==========

    @Nested
    @DisplayName("정상 일반 파일 검증")
    class ValidFileTypes {

        @Test
        @DisplayName("PDF 파일: %PDF 시그니처로 검증 통과")
        void validate_pdf_magic_bytes() {
            // %PDF = 25 50 44 46
            byte[] pdfHeader = new byte[]{
                0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34,
                0x0A, 0x25, (byte) 0xE2, (byte) 0xE3
            };

            boolean result = MagicByteValidator.validate(pdfHeader, "application/pdf");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("ZIP 파일: PK 시그니처로 검증 통과 (docx/xlsx 포함)")
        void validate_zip_magic_bytes() {
            // PK = 50 4B 03 04
            byte[] zipHeader = new byte[]{
                0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00,
                0x08, 0x00, 0x00, 0x00
            };

            boolean result = MagicByteValidator.validate(zipHeader, "application/zip");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("DOCX 파일: ZIP 시그니처 기반으로 검증 통과")
        void validate_docx_as_zip_magic_bytes() {
            // DOCX는 ZIP 컨테이너
            byte[] docxHeader = new byte[]{
                0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00,
                0x08, 0x00, 0x00, 0x00
            };

            boolean result = MagicByteValidator.validate(docxHeader,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("text/plain: 시그니처 없는 타입은 패스스루")
        void validate_text_plain_passthrough() {
            byte[] textHeader = "Hello, world!".getBytes();

            boolean result = MagicByteValidator.validate(textHeader, "text/plain");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("MP4 비디오: ftyp 마커가 offset 4에 위치")
        void validate_mp4_ftyp_at_offset_4() {
            // MP4: size(4 bytes) + ftyp(66 74 79 70) at offset 4
            byte[] mp4Header = new byte[]{
                0x00, 0x00, 0x00, 0x18,  // size
                0x66, 0x74, 0x79, 0x70,  // ftyp
                0x69, 0x73, 0x6F, 0x6D   // isom brand
            };

            boolean result = MagicByteValidator.validate(mp4Header, "video/mp4");

            assertThat(result).isTrue();
        }
    }

    // ========== MIME Spoofing 공격 탐지 ==========

    @Nested
    @DisplayName("MIME Spoofing 공격 탐지")
    class MimeSpoofingDetection {

        @Test
        @DisplayName("EXE 파일을 JPG로 위장: MZ 시그니처 탐지 → 거부")
        void reject_exe_disguised_as_jpeg() {
            // MZ = Windows PE executable header
            byte[] exeHeader = new byte[]{
                0x4D, 0x5A, (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00
            };

            boolean result = MagicByteValidator.validate(exeHeader, "image/jpeg");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("PDF를 PNG로 위장: %PDF 시그니처와 image/png 불일치 → 거부")
        void reject_pdf_disguised_as_png() {
            byte[] pdfHeader = new byte[]{
                0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34,
                0x0A, 0x25, (byte) 0xE2, (byte) 0xE3
            };

            boolean result = MagicByteValidator.validate(pdfHeader, "image/png");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("ZIP을 이미지로 위장: PK 시그니처와 image/gif 불일치 → 거부")
        void reject_zip_disguised_as_gif() {
            byte[] zipHeader = new byte[]{
                0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00,
                0x08, 0x00, 0x00, 0x00
            };

            boolean result = MagicByteValidator.validate(zipHeader, "image/gif");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("무작위 바이트를 WebP로 위장: RIFF 시그니처 없음 → 거부")
        void reject_random_bytes_as_webp() {
            byte[] randomHeader = new byte[]{
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x09, 0x0A, 0x0B, 0x0C
            };

            boolean result = MagicByteValidator.validate(randomHeader, "image/webp");

            assertThat(result).isFalse();
        }
    }

    // ========== 엣지 케이스 ==========

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("빈 바이트 배열: 검증 실패")
        void reject_empty_bytes() {
            byte[] emptyHeader = new byte[0];

            boolean result = MagicByteValidator.validate(emptyHeader, "image/jpeg");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null 바이트 배열: 검증 실패")
        void reject_null_bytes() {
            boolean result = MagicByteValidator.validate(null, "image/jpeg");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("4바이트 미만 데이터: 검증 실패")
        void reject_insufficient_bytes() {
            byte[] shortHeader = new byte[]{(byte) 0xFF, (byte) 0xD8};

            boolean result = MagicByteValidator.validate(shortHeader, "image/jpeg");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("null Content-Type: 검증 실패")
        void reject_null_content_type() {
            byte[] jpegHeader = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0
            };

            boolean result = MagicByteValidator.validate(jpegHeader, null);

            assertThat(result).isFalse();
        }
    }
}
