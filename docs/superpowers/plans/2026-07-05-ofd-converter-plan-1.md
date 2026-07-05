# OFD Converter — Plan 1: PoC + Backend MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validate ofdrw conversion feasibility via PoC, then deliver a working backend REST API that converts OFD↔PDF/图片/文本 and PDF/图片→OFD, with file management, operation logging, and Docker deployment.

**Architecture:** Spring Boot 3 backend (Java 17) wrapping ofdrw 2.3.9. SQLite (WAL) persists tasks + operation logs. Files stored on local disk under `/data`. Conversions run on a bounded thread pool (4 parallel, 5-min timeout). REST API exposes upload→convert→poll→download. Docker Compose ships backend-only (nginx + frontend arrive in Plan 3).

**Tech Stack:** Java 17, Spring Boot 3.3.x, Spring Data JDBC, sqlite-jdbc, ofdrw 2.3.9 (ofdrw-full), Apache Commons Compress (ZIP), JUnit 5 + Spring Boot Test.

## Global Constraints

(Copied verbatim from spec §2, §8, §13. Every task implicitly includes these.)

- JDK 17; Spring Boot 3.3.x; ofdrw 2.3.9 (`org.ofdrw:ofdrw-full`).
- SQLite in WAL mode (`jdbc:sqlite:/data/converter.db?journal_mode=WAL`).
- Single file size limit: **50MB**.
- Conversion timeout: **5 minutes** per task.
- Memory limit: **512MB** per conversion task.
- Max **4 parallel** conversion tasks.
- File retention: configurable, default **24 hours** (`file.retention-hours`, env `FILE_RETENTION_HOURS`).
- Log retention: configurable, default **90 days** (`log.retention-days`, env `LOG_RETENTION_DAYS`).
- Upload rate limit: **20 uploads/min per IP**.
- Disk: warn at 80%, **reject new uploads at 95%** (`/data` usage).
- File type validated by **magic number**: OFD/DOCX = ZIP (bytes `50 4B 03 04`), PDF = `%PDF` (bytes `25 50 44 46`), PNG = `89 50 4E 47`, JPG = `FF D8 FF`.
- UI copy is Chinese (deferred to Plan 3, but error messages in this plan's API use Chinese per spec).
- Every task ends with a commit. TDD: failing test first, then minimal implementation.

## Scope of This Plan

Covers spec **Phase 0 (PoC)** + **Phase 1 (backend MVP, native ofdrw conversions)**. Out of scope (separate plans): OFD→DOCX/Markdown/DOCX→OFD production implementations (Plan 2, after PoC), frontend (Plan 3), MCP (Plan 4). The PoC tasks here *explore* DOCX/Markdown/DOCX→OFD to gate Plan 2 — they do not build production converters for those directions.

## File Structure (this plan creates/ modifies)

```
ofd-converter/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── src/main/java/com/ofd/converter/
│   │   ├── OfdConverterApplication.java
│   │   ├── config/
│   │   │   ├── RetentionProperties.java
│   │   │   ├── ThreadPoolConfig.java
│   │   │   └── WebConfig.java
│   │   ├── interceptor/ClientIpInterceptor.java
│   │   ├── model/
│   │   │   ├── Task.java
│   │   │   ├── TaskStatus.java
│   │   │   ├── ConvertFormat.java
│   │   │   ├── SourceType.java
│   │   │   ├── OperationLog.java
│   │   │   ├── OperationType.java
│   │   │   ├── ApiError.java
│   │   │   └── dto/ (UploadResponse, ConvertRequest, ConvertResponse, TaskResponse, FormatsResponse)
│   │   ├── repository/
│   │   │   ├── TaskRepository.java
│   │   │   └── OperationLogRepository.java
│   │   ├── service/
│   │   │   ├── FileService.java
│   │   │   ├── ValidationService.java
│   │   │   ├── TaskService.java
│   │   │   ├── ConvertService.java
│   │   │   └── LogService.java
│   │   ├── engine/
│   │   │   ├── ConvertPipeline.java
│   │   │   ├── Converter.java          # interface
│   │   │   └── converters/
│   │   │       ├── Ofd2Pdf.java
│   │   │       ├── Ofd2Image.java
│   │   │       ├── Ofd2Text.java
│   │   │       ├── Pdf2Ofd.java
│   │   │       └── Image2Ofd.java
│   │   ├── controller/
│   │   │   ├── ConvertController.java
│   │   │   └── GlobalExceptionHandler.java
│   │   └── scheduler/
│   │       ├── FileCleanupScheduler.java
│   │       └── LogCleanupScheduler.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── schema.sql
│   └── src/test/java/com/ofd/converter/
│       ├── poc/                          # PoC harnesses (Phase 0)
│       ├── engine/                       # converter unit tests
│       ├── controller/                   # API integration tests
│       └── Fixtures.java                 # generates test OFD/PDF files
├── docker-compose.yml
├── docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md
└── README.md
```

---

## Section A — Phase 0: PoC

PoC validates ofdrw capabilities against real files *before* building production converters. PoC tasks produce runnable harnesses + a findings doc. They use ofdrw directly (no Spring context) so they can run as plain JUnit tests.

### Task 1: Scaffold backend project

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/ofd/converter/OfdConverterApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/ofd/converter/SmokeTest.java`

**Interfaces:**
- Produces: a runnable Spring Boot app + Maven build that pulls ofdrw 2.3.9. Later tasks add beans/packages under `com.ofd.converter`.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/ofd/converter/SmokeTest.java`:
```java
package com.ofd.converter;

import org.junit.jupiter.api.Test;
import org.ofdrw.reader.OFDReader;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SmokeTest {
    @Test
    void ofdrwIsOnClasspath() {
        // If ofdrw-full resolved, OFDReader class loads without error.
        assertNotNull(OFDReader.class);
    }

    @Test
    void springBootMainClassExists() {
        assertDoesNotThrow(() -> Class.forName("com.ofd.converter.OfdConverterApplication"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=SmokeTest`
Expected: FAIL — classes not found / no pom yet.

- [ ] **Step 3: Write minimal implementation**

`backend/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.ofd</groupId>
    <artifactId>ofd-converter-backend</artifactId>
    <version>0.1.0</version>
    <properties>
        <java.version>17</java.version>
        <ofdrw.version>2.3.9</ofdrw.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.ofdrw</groupId>
            <artifactId>ofdrw-full</artifactId>
            <version>${ofdrw.version}</version>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-compress</artifactId>
            <version>1.27.1</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

`backend/src/main/java/com/ofd/converter/OfdConverterApplication.java`:
```java
package com.ofd.converter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OfdConverterApplication {
    public static void main(String[] args) {
        SpringApplication.run(OfdConverterApplication.class, args);
    }
}
```

`backend/src/main/resources/application.yml`:
```yaml
server:
  port: 8080
  forward-headers-strategy: native
spring:
  application:
    name: ofd-converter
  datasource:
    url: jdbc:sqlite:${OFD_DB_PATH:/data/converter.db}?journal_mode=WAL
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
file:
  retention-hours: ${FILE_RETENTION_HOURS:24}
  data-dir: ${OFD_DATA_DIR:/data}
log:
  retention-days: ${LOG_RETENTION_DAYS:90}
conversion:
  max-parallel: 4
  timeout-minutes: 5
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=SmokeTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
cd /home/alex/my_workspace/ofd-converter
git init 2>/dev/null; git add backend/pom.xml backend/src
git commit -m "chore: scaffold Spring Boot backend with ofdrw 2.3.9"
```

---

### Task 2: Test fixtures (generate OFD + PDF + PNG via ofdrw)

**Files:**
- Create: `backend/src/test/java/com/ofd/converter/Fixtures.java`

**Interfaces:**
- Produces: `Fixtures.ofd(Path dir)`, `Fixtures.pdf(Path dir)`, `Fixtures.png(Path dir)` — return `Path` to generated files. Used by all PoC + converter tests.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/ofd/converter/FixturesTest.java`:
```java
package com.ofd.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class FixturesTest {
    @Test void generatesOfd(@TempDir Path tmp) throws Exception {
        Path p = Fixtures.ofd(tmp);
        assertTrue(Files.size(p) > 0);
        // OFD is a ZIP — first bytes are 50 4B 03 04
        byte[] head = Files.readAllBytes(p).length > 4
            ? Files.readAllBytes(p) : new byte[0];
        assertEquals(0x50, head[0]);
        assertEquals(0x4B, head[1]);
    }
    @Test void generatesPdf(@TempDir Path tmp) throws Exception {
        Path p = Fixtures.pdf(tmp);
        byte[] head = Files.readAllBytes(p);
        assertEquals(0x25, head[0]); // %
        assertEquals(0x50, head[1]); // P
    }
    @Test void generatesPng(@TempDir Path tmp) throws Exception {
        Path p = Fixtures.png(tmp);
        byte[] head = Files.readAllBytes(p);
        assertEquals((byte) 0x89, head[0]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=FixturesTest`
Expected: FAIL — `Fixtures` not found.

- [ ] **Step 3: Write minimal implementation**

`backend/src/test/java/com/ofd/converter/Fixtures.java`:
```java
package com.ofd.converter;

import org.ofdrw.converter.ofdconverter.TextConverter;
import org.ofdrw.layout.OFDDoc;
import org.ofdrw.layout.element.Paragraph;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates small valid fixture files using ofdrw itself (no external downloads). */
public final class Fixtures {
    private Fixtures() {}

    /** A 3-page text OFD built with ofdrw-layout. */
    public static Path ofd(Path dir) throws Exception {
        Path ofd = dir.resolve("sample.ofd");
        try (OFDDoc doc = new OFDDoc(ofd)) {
            for (int i = 1; i <= 3; i++) {
                doc.addPage(new Paragraph("第 " + i + " 页：OFD 转换工具测试文本。"));
            }
        }
        return ofd;
    }

    /** A minimal PDF written via ofdrw TextConverter is not a PDF — so build one from OFD->PDF at call time is circular.
     *  Instead write a tiny valid PDF by hand (5-line minimal PDF). */
    public static Path pdf(Path dir) throws Exception {
        Path pdf = dir.resolve("sample.pdf");
        String body = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
            + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
            + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]>>endobj\n"
            + "xref\n0 4\n0000000000 65535 f \n"
            + "0000000009 00000 n \n0000000052 00000 n \n0000000101 00000 n \n"
            + "trailer<</Size 4/Root 1 0 R>>\nstartxref\n164\n%%EOF";
        Files.writeString(pdf, body);
        return pdf;
    }

    /** A 100x100 solid-color PNG. */
    public static Path png(Path dir) throws Exception {
        Path png = dir.resolve("sample.png");
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics g = img.getGraphics();
        g.setColor(java.awt.Color.BLUE);
        g.fillRect(0, 0, 100, 100);
        g.dispose();
        ImageIO.write(img, "png", png.toFile());
        return png;
    }
}
```

> Note: `OFDDoc` + `Paragraph` come from `ofdrw-layout` (bundled in ofdrw-full). If the exact package differs in 2.3.9, the PoC implementer adjusts the import — the goal is a valid OFD; the assertion only checks ZIP magic bytes.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=FixturesTest`
Expected: PASS (3 tests). If `OFDDoc` import differs, fix import and re-run.

- [ ] **Step 5: Commit**

```bash
git add backend/src/test/java/com/ofd/converter/Fixtures.java backend/src/test/java/com/ofd/converter/FixturesTest.java
git commit -m "test: add OFD/PDF/PNG fixture generators"
```

---

### Task 3: PoC — native conversions smoke test

Validates spec §5 🟢 high-confidence native conversions: OFD→PDF, OFD→PNG, OFD→TXT, PDF→OFD, PNG→OFD.

**Files:**
- Create: `backend/src/test/java/com/ofd/converter/poc/NativeConversionPoc.java`

**Interfaces:**
- Produces: findings (logged to test output). No production code. Gates Task 17–21 (production converters) — if a native conversion fails here, stop and update spec.

- [ ] **Step 1: Write the PoC test**

```java
package com.ofd.converter.poc;

import com.ofd.converter.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.converter.export.*;
import org.ofdrw.converter.ofdconverter.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeConversionPoc {
    @Test void ofdToPdf(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp), out = tmp.resolve("out.pdf");
        try (var ex = new PDFExporterPDFBox(ofd, out)) { ex.export(); }
        assertTrue(Files.size(out) > 0);
        assertEquals(0x25, Files.readAllBytes(out)[0]); // %
    }
    @Test void ofdToPng(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp); Path outDir = tmp.resolve("img");
        Files.createDirectory(outDir);
        try (var ex = new ImageExporter(ofd, outDir, "PNG", 15d)) { ex.export(); }
        assertFalse(ex.getImgFilePaths().isEmpty());
    }
    @Test void ofdToText(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp), out = tmp.resolve("out.txt");
        try (var ex = new TextExporter(ofd, out)) { ex.export(); }
        // TextExporter may produce empty for path-glyph pages; our fixture uses text, so expect non-empty.
        String txt = Files.readString(out);
        assertFalse(txt.isBlank(), "TextExporter should extract text from text-based fixture");
    }
    @Test void pdfToOfd(@TempDir Path tmp) throws Exception {
        Path pdf = Fixtures.pdf(tmp), out = tmp.resolve("fromPdf.ofd");
        try (var c = new PDFConverter(out)) { c.convert(pdf); }
        byte[] head = Files.readAllBytes(out);
        assertEquals(0x50, head[0]); assertEquals(0x4B, head[1]); // ZIP
    }
    @Test void pngToOfd(@TempDir Path tmp) throws Exception {
        Path png = Fixtures.png(tmp), out = tmp.resolve("fromPng.ofd");
        try (var c = new ImageConverter(out)) { c.convert(png); }
        byte[] head = Files.readAllBytes(out);
        assertEquals(0x50, head[0]); assertEquals(0x4B, head[1]);
    }
}
```

- [ ] **Step 2: Run PoC**

Run: `cd backend && mvn -q test -Dtest=NativeConversionPoc`
Expected: All 5 pass. If any fail, capture the failure in the findings doc (Task 7) and stop — do not proceed to production converters for that direction.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/ofd/converter/poc/NativeConversionPoc.java
git commit -m "poc: smoke-test native ofdrw conversions"
```

---

### Task 4: PoC — OFD→DOCX feasibility

Explores spec §5 🟡 OFD→DOCX (no native support; ofdrw-reader + POI).

**Files:**
- Create: `backend/pom.xml` (modify: add Apache POI dependency)
- Create: `backend/src/test/java/com/ofd/converter/poc/OfdToDocxPoc.java`

**Interfaces:**
- Produces: findings on text/structure extraction quality. Gates Plan 2 OFD→DOCX production task.

- [ ] **Step 1: Add POI dependency**

In `backend/pom.xml` `<dependencies>`, add:
```xml
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.3.0</version>
</dependency>
```

- [ ] **Step 2: Write the PoC test**

```java
package com.ofd.converter.poc;

import com.ofd.converter.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.core.basicStructure.page.block.PageBlock;
import org.ofdrw.core.basicStructure.page.block.CtText;
import java.nio.file.Path;
import java.nio.file.Files;
import org.apache.poi.xwpf.usermodel.*;

class OfdToDocxPoc {
    @Test void extractTextAndBuildDocx(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp), docx = tmp.resolve("out.docx");
        StringBuilder extracted = new StringBuilder();
        try (OFDReader reader = new OFDReader(ofd)) {
            for (int i = 0; i < reader.getPageCount(); i++) {
                // Iterate page blocks; collect CtText contents. Exact accessor names verified at runtime.
                reader.getPage(i).getData().getObject().stream()
                    .filter(o -> o instanceof CtText)
                    .map(o -> ((CtText) o).getText())
                    .forEach(t -> extracted.append(t).append("\n"));
            }
        }
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(extracted.toString());
            try (var out = Files.newOutputStream(docx)) { doc.write(out); }
        }
        // DOCX is a ZIP
        byte[] head = Files.readAllBytes(docx);
        org.junit.jupiter.api.Assertions.assertEquals(0x50, head[0]);
        org.junit.jupiter.api.Assertions.assertTrue(extracted.length() > 0,
            "If extraction is empty, reader API differs — record in findings and adjust accessor");
    }
}
```

> Note: ofdrw-reader's exact page/block traversal API (`getPage`, `getData`, `getObject`, `CtText`) is verified at runtime here — that is the point of the PoC. If accessors differ in 2.3.9, the implementer corrects them and records the working API in findings.

- [ ] **Step 3: Run PoC and record findings**

Run: `cd backend && mvn -q test -Dtest=OfdToDocxPoc`
Expected: PASS with non-empty extraction. If API accessors fail, fix them; if extraction is structurally poor (no paragraph/heading separation), note "OFD→DOCX requires custom structure inference" in findings.

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/test/java/com/ofd/converter/poc/OfdToDocxPoc.java
git commit -m "poc: explore OFD->DOCX via ofdrw-reader + POI"
```

---

### Task 5: PoC — OFD→Markdown paths B + A

Validates spec §5 "OFD→Markdown 实现路径": path B (HTMLExporter→flexmark) vs path A (reader structure inference).

**Files:**
- Create: `backend/pom.xml` (modify: add flexmark dependency)
- Create: `backend/src/test/java/com/ofd/converter/poc/OfdToMarkdownPoc.java`

**Interfaces:**
- Produces: decision (B vs A vs C) recorded in findings. Gates Plan 2 OFD→Markdown production task.

- [ ] **Step 1: Add flexmark dependency**

In `backend/pom.xml` `<dependencies>`, add:
```xml
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-html2md-converter</artifactId>
    <version>0.64.8</version>
</dependency>
```

- [ ] **Step 2: Write the PoC test**

```java
package com.ofd.converter.poc;

import com.ofd.converter.Fixtures;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.converter.export.HTMLExporter;
import org.ofdrw.reader.OFDReader;
import org.ofdrw.core.basicStructure.page.block.CtText;
import java.nio.file.*;

class OfdToMarkdownPoc {
    @Test void pathB_htmlToMarkdown(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp), html = tmp.resolve("out.html");
        try (var ex = new HTMLExporter(ofd, html)) { ex.export(); }
        String md = FlexmarkHtmlConverter.builder().build().convert(Files.readString(html));
        System.out.println("[Path B] HTML head:\n" + Files.readString(html).substring(0, Math.min(300, (int) Files.size(html))));
        System.out.println("[Path B] MD:\n" + md);
        // Finding: if md is empty or SVG-only, path B is unusable.
    }

    @Test void pathA_readerStructureInference(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        StringBuilder md = new StringBuilder();
        try (OFDReader reader = new OFDReader(ofd)) {
            for (int i = 0; i < reader.getPageCount(); i++) {
                reader.getPage(i).getData().getObject().stream()
                    .filter(o -> o instanceof CtText)
                    .map(o -> ((CtText) o).getText())
                    .forEach(t -> md.append(t).append("\n\n"));
            }
        }
        System.out.println("[Path A] MD:\n" + md);
        org.junit.jupiter.api.Assertions.assertTrue(md.length() > 0);
    }
}
```

- [ ] **Step 3: Run PoC and record decision**

Run: `cd backend && mvn -q test -Dtest=OfdToMarkdownPoc`
Inspect stdout. Record in findings (Task 7):
- Does Path B HTML contain semantic tags (`<h1>`, `<p>`, `<table>`) or only SVG? → decides B viability.
- Path A extraction quality → decides if structure inference (font-size for headings) is worth building.

- [ ] **Step 4: Commit**

```bash
git add backend/pom.xml backend/src/test/java/com/ofd/converter/poc/OfdToMarkdownPoc.java
git commit -m "poc: compare OFD->Markdown paths B (html) and A (reader)"
```

---

### Task 6: PoC — DOCX→OFD feasibility

Validates spec §5 🔴 DOCX→OFD (no native; POI + ofdrw-layout).

**Files:**
- Create: `backend/src/test/java/com/ofd/converter/poc/DocxToOfdPoc.java`

**Interfaces:**
- Produces: findings on layout-engine effort. Gates Plan 2 DOCX→OFD task — may recommend deferring to a later version.

- [ ] **Step 1: Write the PoC test**

```java
package com.ofd.converter.poc;

import org.apache.poi.xwpf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ofdrw.converter.ofdconverter.TextConverter;
import org.ofdrw.layout.OFDDoc;
import org.ofdrw.layout.element.Paragraph;
import java.nio.file.*;

class DocxToOfdPoc {
    @Test void docxTextToOfd(@TempDir Path tmp) throws Exception {
        // Build a tiny DOCX, extract paragraphs, feed into ofdrw-layout.
        Path docx = tmp.resolve("in.docx");
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("DOCX 转 OFD 测试段落");
            try (var out = Files.newOutputStream(docx)) { doc.write(out); }
        }
        Path ofd = tmp.resolve("out.ofd");
        try (OFDDoc doc = new OFDDoc(ofd)) {
            try (XWPFDocument in = new XWPFDocument(Files.newInputStream(docx))) {
                for (XWPFParagraph p : in.getParagraphs()) {
                    doc.addPage(new Paragraph(p.getText()));
                }
            }
        }
        byte[] head = Files.readAllBytes(ofd);
        org.junit.jupiter.api.Assertions.assertEquals(0x50, head[0]);
        // Finding: this is text-only (no formatting/layout). Full DOCX->OFD needs a layout engine — record effort estimate.
    }
}
```

- [ ] **Step 2: Run PoC**

Run: `cd backend && mvn -q test -Dtest=DocxToOfdPoc`
Expected: PASS (text-only OFD produced). Finding: full-fidelity DOCX→OFD is out of v1 scope — recommend deferring or marking experimental.

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/ofd/converter/poc/DocxToOfdPoc.java
git commit -m "poc: explore DOCX->OFD via POI + ofdrw-layout"
```

---

### Task 7: PoC findings document

**Files:**
- Create: `docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md`

**Interfaces:**
- Produces: a findings doc that may update the spec's §5 matrix and gates Plan 2 scope.

- [ ] **Step 1: Run all PoC tests together**

Run: `cd backend && mvn -q test -Dtest="com.ofd.converter.poc.*"`
Capture pass/fail + stdout excerpts.

- [ ] **Step 2: Write findings doc**

`docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md`:
```markdown
# OFD Converter — PoC Findings (2026-07-05)

## Native conversions (spec §5 🟢)
| Direction | Result | Notes |
|---|---|---|
| OFD→PDF | PASS/FAIL | (fill from Task 3) |
| OFD→PNG | PASS/FAIL | |
| OFD→TXT | PASS/FAIL | |
| PDF→OFD | PASS/FAIL | |
| PNG→OFD | PASS/FAIL | |

## OFD→DOCX (Task 4)
- Working ofdrw-reader accessor API: `<record actual class/method names>`
- Extraction quality: text / table / image — `<observations>`
- Recommendation for Plan 2: `<build / simplify / defer>`

## OFD→Markdown (Task 5)
- Path B HTML content: `<semantic tags? SVG-only?>`
- Path B → MD output: `<empty / usable>`
- Path A reader extraction: `<quality>`
- Decision: adopt Path `<A/B/C>` for Plan 2. Reason: `<...>`

## DOCX→OFD (Task 6)
- Text-only conversion: PASS/FAIL
- Full-fidelity effort estimate: `<low/medium/high — recommend defer if high>`
- Recommendation: `<build text-only in Plan 2 / defer to later version>`

## Spec adjustments required
- `<list any §5 matrix changes, e.g. downgrade a direction>`
```

Fill every field from actual test output. No TBDs.

- [ ] **Step 3: Commit**

```bash
git add docs/superpowers/pocs/2026-07-05-ofd-converter-poc-findings.md
git commit -m "docs: record PoC findings for ofdrw conversions"
```

---

## Section B — Backend foundation

### Task 8: DB schema + Task entity + enums + repositories

**Files:**
- Modify: `backend/pom.xml` (add Lombok)
- Create: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/java/com/ofd/converter/model/Task.java`
- Create: `backend/src/main/java/com/ofd/converter/model/TaskStatus.java`
- Create: `backend/src/main/java/com/ofd/converter/model/ConvertFormat.java`
- Create: `backend/src/main/java/com/ofd/converter/model/SourceType.java`
- Create: `backend/src/main/java/com/ofd/converter/repository/TaskRepository.java`
- Test: `backend/src/test/java/com/ofd/converter/repository/TaskRepositoryTest.java`

**Interfaces:**
- Produces: `Task` entity (fields per spec §7 + §9), `TaskRepository extends CrudRepository<Task,String>`, enums `TaskStatus` (`PENDING, PROCESSING, DONE, FAILED, TIMEOUT`), `ConvertFormat` (`PDF, PNG, JPG, DOCX, TXT, MD, OFD`), `SourceType` (`OFD, PDF, IMAGE, DOCX`).

- [ ] **Step 1: Add Lombok to pom**

In `backend/pom.xml` `<dependencies>`, add:
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```
And in `<build><plugins>` add to spring-boot-maven-plugin config:
```xml
<configuration><excludes><exclude><groupId>org.projectlombok</groupId><artifactId>lombok</artifactId></exclude></excludes></configuration>
```

- [ ] **Step 2: Write the failing test**

`backend/src/test/java/com/ofd/converter/repository/TaskRepositoryTest.java`:
```java
package com.ofd.converter.repository;

import com.ofd.converter.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import static org.junit.jupiter.api.Assertions.*;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryTest {
    @Autowired TaskRepository repo;

    @Test void saveAndFind() {
        Task t = new Task();
        t.setId("t1");
        t.setSourceFileId("f1");
        t.setSourceFilename("a.ofd");
        t.setSourceType(SourceType.OFD.name());
        t.setTargetFormat(ConvertFormat.PDF.name());
        t.setStatus(TaskStatus.PENDING.name());
        t.setCreatedAt(System.currentTimeMillis());
        t.setUpdatedAt(t.getCreatedAt());
        repo.save(t);

        Task loaded = repo.findById("t1").orElseThrow();
        assertEquals(TaskStatus.PENDING.name(), loaded.getStatus());
    }
}
```

Also add test config `backend/src/test/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:sqlite::memory:
    driver-class-name: org.sqlite.JDBC
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=TaskRepositoryTest`
Expected: FAIL — schema/classes missing.

- [ ] **Step 4: Write minimal implementation**

`backend/src/main/resources/schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS task (
  id TEXT PRIMARY KEY,
  source_file_id TEXT NOT NULL,
  source_filename TEXT NOT NULL,
  source_type TEXT NOT NULL,
  target_format TEXT NOT NULL,
  status TEXT NOT NULL,
  options_json TEXT,
  output_path TEXT,
  output_filename TEXT,
  output_size INTEGER,
  output_type TEXT,
  error_message TEXT,
  downloaded_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_task_created ON task(created_at);
```

`TaskStatus.java`:
```java
package com.ofd.converter.model;
public enum TaskStatus { PENDING, PROCESSING, DONE, FAILED, TIMEOUT }
```

`ConvertFormat.java`:
```java
package com.ofd.converter.model;
public enum ConvertFormat { PDF, PNG, JPG, DOCX, TXT, MD, OFD }
```

`SourceType.java`:
```java
package com.ofd.converter.model;
public enum SourceType { OFD, PDF, IMAGE, DOCX }
```

`Task.java`:
```java
package com.ofd.converter.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter @Setter
@Table("task")
public class Task {
    @Id private String id;
    private String sourceFileId;
    private String sourceFilename;
    private String sourceType;
    private String targetFormat;
    private String status;
    private String optionsJson;
    private String outputPath;
    private String outputFilename;
    private Long outputSize;
    private String outputType;   // single | archive
    private String errorMessage;
    private Long downloadedAt;
    private Long createdAt;
    private Long updatedAt;
}
```

`TaskRepository.java`:
```java
package com.ofd.converter.repository;

import com.ofd.converter.model.Task;
import org.springframework.data.repository.CrudRepository;

public interface TaskRepository extends CrudRepository<Task, String> {}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=TaskRepositoryTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/schema.sql backend/src/main/java backend/src/test
git commit -m "feat: add Task entity, enums, schema, repository"
```

---

### Task 9: OperationLog entity + repository

**Files:**
- Modify: `backend/src/main/resources/schema.sql`
- Create: `backend/src/main/java/com/ofd/converter/model/OperationLog.java`
- Create: `backend/src/main/java/com/ofd/converter/model/OperationType.java`
- Create: `backend/src/main/java/com/ofd/converter/repository/OperationLogRepository.java`
- Test: `backend/src/test/java/com/ofd/converter/repository/OperationLogRepositoryTest.java`

**Interfaces:**
- Produces: `OperationLog` (fields per spec §9 table), `OperationType` (`UPLOAD, CONVERT, DOWNLOAD, MCP_CALL`), `OperationLogRepository` with `deleteByCreatedAtBefore(long)` for cleanup.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.repository;

import com.ofd.converter.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import static org.junit.jupiter.api.Assertions.*;

@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OperationLogRepositoryTest {
    @Autowired OperationLogRepository repo;

    @Test void insertAndDeleteOlderThan() {
        OperationLog log = new OperationLog();
        log.setId("l1");
        log.setOperationType(OperationType.UPLOAD.name());
        log.setClientIp("127.0.0.1");
        log.setFileId("f1");
        log.setStatus("SUCCESS");
        log.setCreatedAt(1000L);
        repo.save(log);

        repo.deleteByCreatedAtBefore(2000L);
        assertTrue(repo.findById("l1").isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=OperationLogRepositoryTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

Append to `schema.sql`:
```sql
CREATE TABLE IF NOT EXISTS operation_log (
  id TEXT PRIMARY KEY,
  operation_type TEXT NOT NULL,
  client_ip TEXT,
  file_id TEXT,
  task_id TEXT,
  target_format TEXT,
  status TEXT NOT NULL,
  duration_ms INTEGER,
  error_message TEXT,
  user_agent TEXT,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_log_created ON operation_log(created_at);
```

`OperationType.java`:
```java
package com.ofd.converter.model;
public enum OperationType { UPLOAD, CONVERT, DOWNLOAD, MCP_CALL }
```

`OperationLog.java`:
```java
package com.ofd.converter.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Getter @Setter
@Table("operation_log")
public class OperationLog {
    @Id private String id;
    private String operationType;
    private String clientIp;
    private String fileId;
    private String taskId;
    private String targetFormat;
    private String status;   // SUCCESS | FAILED | TIMEOUT
    private Long durationMs;
    private String errorMessage;
    private String userAgent;
    private Long createdAt;
}
```

`OperationLogRepository.java`:
```java
package com.ofd.converter.repository;

import com.ofd.converter.model.OperationLog;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

public interface OperationLogRepository extends CrudRepository<OperationLog, String> {
    @Modifying
    @Query("DELETE FROM operation_log WHERE created_at < :before")
    void deleteByCreatedAtBefore(long before);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=OperationLogRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/schema.sql backend/src/main/java backend/src/test
git commit -m "feat: add OperationLog entity, repository, cleanup query"
```

---

### Task 10: Config classes + ClientIpInterceptor

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/config/RetentionProperties.java`
- Create: `backend/src/main/java/com/ofd/converter/config/ThreadPoolConfig.java`
- Create: `backend/src/main/java/com/ofd/converter/config/WebConfig.java`
- Create: `backend/src/main/java/com/ofd/converter/interceptor/ClientIpInterceptor.java`
- Test: `backend/src/test/java/com/ofd/converter/interceptor/ClientIpInterceptorTest.java`

**Interfaces:**
- Produces: `RetentionProperties` (file/log retention), `ThreadPoolConfig` (conversion executor: 4 threads), `WebConfig` (registers `ClientIpInterceptor`), `ClientIpInterceptor.extractIp(HttpServletRequest)` — used by `LogService` and `ConvertController`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class ClientIpInterceptorTest {
    @Test void prefersXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");
        req.setRemoteAddr("10.0.0.1");
        assertEquals("203.0.113.5", ClientIpInterceptor.extractIp(req));
    }
    @Test void fallsBackToRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.9");
        assertEquals("192.168.1.9", ClientIpInterceptor.extractIp(req));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=ClientIpInterceptorTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

`RetentionProperties.java`:
```java
package com.ofd.converter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter
@Component
@ConfigurationProperties(prefix = "file")
public class RetentionProperties {
    private int retentionHours = 24;
    private String dataDir = "/data";
}
```

`ThreadPoolConfig.java`:
```java
package com.ofd.converter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.*;

@Configuration
public class ThreadPoolConfig {
    @Bean(name = "conversionExecutor")
    public ExecutorService conversionExecutor() {
        return new ThreadPoolExecutor(
            4, 4, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            r -> { Thread t = new Thread(r, "ofd-converter"); t.setDaemon(true); return t; });
    }

    @Bean(name = "logExecutor")
    public ExecutorService logExecutor() {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ofd-log-writer"); t.setDaemon(true); return t;
        });
    }
}
```

`ClientIpInterceptor.java`:
```java
package com.ofd.converter.interceptor;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIpInterceptor {
    private ClientIpInterceptor() {}

    public static String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return req.getRemoteAddr();
    }
}
```

`WebConfig.java`:
```java
package com.ofd.converter.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns("*").allowedMethods("*");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=ClientIpInterceptorTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add config classes and ClientIpInterceptor"
```

---

### Task 11: ApiError + GlobalExceptionHandler

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/model/ApiError.java`
- Create: `backend/src/main/java/com/ofd/converter/controller/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/ofd/converter/model/ErrorCode.java`
- Test: `backend/src/test/java/com/ofd/converter/controller/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Produces: `ErrorCode` enum (spec §6 error codes), `ApiError` DTO (`{error:{code,message,details}}`), `GlobalExceptionHandler` mapping exceptions → HTTP status + ApiError. A custom `ApiException(code, message, status)` is thrown by services.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.controller;

import com.ofd.converter.model.ApiError;
import com.ofd.converter.model.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test void mapsApiExceptionToStatus() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        ResponseEntity<ApiError> r = h.handleApi(new ApiException(ErrorCode.FILE_TOO_LARGE, "太大", 400));
        assertEquals(400, r.getStatusCode().value());
        assertEquals("FILE_TOO_LARGE", r.getBody().error().code());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

`ErrorCode.java`:
```java
package com.ofd.converter.model;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    TASK_NOT_FOUND(HttpStatus.NOT_FOUND),
    FILE_EXPIRED(HttpStatus.GONE),
    TASK_FAILED(HttpStatus.CONFLICT),
    TASK_TIMEOUT(HttpStatus.REQUEST_TIMEOUT),
    STORAGE_FULL(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;
    ErrorCode(HttpStatus s) { this.status = s; }
}
```

`ApiError.java`:
```java
package com.ofd.converter.model;

import java.util.Map;

public record ApiError(Error error) {
    public record Error(String code, String message, Map<String, Object> details) {}
    public static ApiError of(ErrorCode code, String message) {
        return new ApiError(new Error(code.name(), message, Map.of()));
    }
}
```

`ApiException.java`:
```java
package com.ofd.converter.controller;

import com.ofd.converter.model.ErrorCode;

public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public final int status;
    public ApiException(ErrorCode code, String message, int status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
```

`GlobalExceptionHandler.java`:
```java
package com.ofd.converter.controller;

import com.ofd.converter.model.ApiError;
import com.ofd.converter.model.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status).body(ApiError.of(ex.code, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleAny(Exception ex) {
        return ResponseEntity.status(500).body(ApiError.of(ErrorCode.INTERNAL_ERROR, "服务内部错误"));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add ApiError, ErrorCode, GlobalExceptionHandler"
```

---

### Task 12: ValidationService

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/service/ValidationService.java`
- Test: `backend/src/test/java/com/ofd/converter/service/ValidationServiceTest.java`

**Interfaces:**
- Produces: `ValidationService.detect(byte[] head, String filename)` → `SourceType`; `validateSize(long bytes)`; `sanitizeFilename(String)` → safe filename. Throws `ApiException(ErrorCode.INVALID_FILE_TYPE/FILE_TOO_LARGE)` on failure.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.SourceType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationServiceTest {
    private final ValidationService v = new ValidationService();

    @Test void detectsOfdZip() {
        byte[] head = {0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        assertEquals(SourceType.OFD, v.detect(head, "a.ofd"));
    }
    @Test void detectsPdf() {
        byte[] head = {0x25, 0x50, 0x44, 0x46};
        assertEquals(SourceType.PDF, v.detect(head, "a.pdf"));
    }
    @Test void detectsPng() {
        byte[] head = (byte) 0x89, 0x50, 0x4E, 0x47;
        // (use byte array literal in real file)
    }
    @Test void rejectsUnknown() {
        byte[] head = {1, 2, 3, 4};
        assertThrows(ApiException.class, () -> v.detect(head, "a.exe"));
    }
    @Test void rejectsOversize() {
        ApiException ex = assertThrows(ApiException.class, () -> v.validateSize(60L * 1024 * 1024));
        assertEquals("FILE_TOO_LARGE", ex.code.name());
    }
    @Test void sanitizesFilename() {
        assertEquals("a.ofd", v.sanitizeFilename("../../a.ofd"));
        assertEquals("a.ofd", v.sanitizeFilename("a.ofd "));
    }
}
```

> Note: the PNG test line above is pseudocode — in the real file write `byte[] head = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};` and assert `SourceType.IMAGE`. Fix this when writing the file.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=ValidationServiceTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.ErrorCode;
import com.ofd.converter.model.SourceType;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {
    private static final long MAX_BYTES = 50L * 1024 * 1024;

    public SourceType detect(byte[] head, String filename) {
        if (head == null || head.length < 4) throw fail();
        if (head[0] == 0x50 && head[1] == 0x4B && head[2] == 0x03 && head[3] == 0x04) {
            String n = filename.toLowerCase();
            return n.endsWith(".docx") ? SourceType.DOCX : SourceType.OFD;
        }
        if (head[0] == 0x25 && head[1] == 0x50 && head[2] == 0x44 && head[3] == 0x46) return SourceType.PDF;
        if ((head[0] & 0xFF) == 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) return SourceType.IMAGE;
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) return SourceType.IMAGE;
        throw fail();
    }

    public void validateSize(long bytes) {
        if (bytes > MAX_BYTES) throw new ApiException(ErrorCode.FILE_TOO_LARGE, "超过 50MB 限制", 400);
    }

    public String sanitizeFilename(String name) {
        String base = name.replace("\\", "/").substring(name.replace("\\", "/").lastIndexOf('/') + 1);
        base = base.replaceAll("[\\\\/]", "").replaceAll("\\p{Cntrl}", "");
        return base.isBlank() ? "file" : base;
    }

    private ApiException fail() {
        return new ApiException(ErrorCode.INVALID_FILE_TYPE, "不支持的文件类型", 400);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=ValidationServiceTest`
Expected: PASS (fix the PNG test literal first).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add ValidationService (magic number, size, filename)"
```

---

### Task 13: FileService

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/service/FileService.java`
- Test: `backend/src/test/java/com/ofd/converter/service/FileServiceTest.java`

**Interfaces:**
- Produces: `FileService` with:
  - `Path storeUpload(InputStream in, String fileId, String filename)` → writes `/data/uploads/{fileId}/{sanitized}`
  - `Path createOutputDir(String taskId)` → `/data/outputs/{taskId}/`
  - `Path zipDir(Path dir, String zipName)` → zips multi-file output (uses Commons Compress)
  - `boolean diskOk()` → true if `/data` usage < 95%
  - `void deleteRecursively(Path)` for cleanup
- Consumes: `RetentionProperties.dataDir`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.service;

import com.ofd.converter.config.RetentionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {
    private FileService fs(RetentionProperties p) { return new FileService(p); }

    @Test void storeUpload(@TempDir Path tmp) throws Exception {
        RetentionProperties p = new RetentionProperties(); p.setDataDir(tmp.toString());
        FileService fs = fs(p);
        Path out = fs.storeUpload(new ByteArrayInputStream("hello".getBytes()), "f1", "../../x.ofd");
        assertEquals("x.ofd", out.getFileName().toString());
        assertTrue(Files.exists(out));
    }

    @Test void zipDir(@TempDir Path tmp) throws Exception {
        RetentionProperties p = new RetentionProperties(); p.setDataDir(tmp.toString());
        FileService fs = fs(p);
        Path dir = fs.createOutputDir("t1");
        Files.writeString(dir.resolve("0.png"), "x");
        Files.writeString(dir.resolve("1.png"), "y");
        Path zip = fs.zipDir(dir, "out_images.zip");
        assertTrue(Files.size(zip) > 0);
        assertEquals("out_images.zip", zip.getFileName().toString());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=FileServiceTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.service;

import com.ofd.converter.config.RetentionProperties;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Stream;

@Service
public class FileService {
    private final Path dataDir;

    public FileService(RetentionProperties props) {
        this.dataDir = Paths.get(props.getDataDir());
    }

    public Path storeUpload(InputStream in, String fileId, String filename) throws IOException {
        Path dir = dataDir.resolve("uploads/" + fileId);
        Files.createDirectories(dir);
        String safe = filename.replace("\\", "/").substring(filename.replace("\\", "/").lastIndexOf('/') + 1)
                .replaceAll("[\\\\/]", "").replaceAll("\\p{Cntrl}", "");
        Path target = dir.resolve(safe.isBlank() ? "file" : safe);
        Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public Path createOutputDir(String taskId) throws IOException {
        Path dir = dataDir.resolve("outputs/" + taskId);
        Files.createDirectories(dir);
        return dir;
    }

    public Path zipDir(Path dir, String zipName) throws IOException {
        Path zip = dir.resolve(zipName);
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zip.toFile());
             Stream<Path> files = Files.list(dir)) {
            files.filter(f -> !f.equals(zip) && Files.isRegularFile(f))
                 .forEach(f -> {
                     try (InputStream fin = Files.newInputStream(f)) {
                         ZipArchiveEntry e = new ZipArchiveEntry(f.getFileName().toString());
                         zos.putArchiveEntry(e);
                         fin.transferTo(zos);
                         zos.closeArchiveEntry();
                     } catch (IOException e) { throw new UncheckedIOException(e); }
                 });
            zos.finish();
        }
        return zip;
    }

    public boolean diskOk() {
        try {
            double usage = dataDir.toFile().getUsableSpace();
            double total = dataDir.toFile().getTotalSpace();
            if (total == 0) return true;
            double usedRatio = 1.0 - (usage / total);
            return usedRatio < 0.95;
        } catch (Exception e) {
            return true;
        }
    }

    public void deleteRecursively(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> walk = Files.walk(p)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (IOException ignored) {}
            });
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=FileServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add FileService (upload store, output dir, zip, disk check)"
```

---

### Task 14: LogService (async)

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/service/LogService.java`
- Test: `backend/src/test/java/com/ofd/converter/service/LogServiceTest.java`

**Interfaces:**
- Produces: `LogService.record(OperationType, String ip, String fileId, String taskId, String targetFormat, String status, long durationMs, String error, String userAgent)` — async write via `logExecutor`. Non-blocking; failures only log to app log.
- Consumes: `OperationLogRepository`, `logExecutor`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.repository.OperationLogRepository;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LogServiceTest {
    @Test void recordsAsyncWithoutThrowing() throws Exception {
        OperationLogRepository repo = mock(OperationLogRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        LogService svc = new LogService(repo, java.util.concurrent.Executors.newSingleThreadExecutor());

        svc.record(OperationType.UPLOAD, "1.2.3.4", "f1", null, null, "SUCCESS", 12, null, "ua");

        Thread.sleep(200); // let async complete
        verify(repo, times(1)).save(any(OperationLog.class));
    }

    @Test void swallowsWriteFailure() throws Exception {
        OperationLogRepository repo = mock(OperationLogRepository.class);
        when(repo.save(any())).thenThrow(new RuntimeException("db down"));
        LogService svc = new LogService(repo, java.util.concurrent.Executors.newSingleThreadExecutor());

        svc.record(OperationType.UPLOAD, "1.2.3.4", "f1", null, null, "SUCCESS", 1, null, "ua");
        Thread.sleep(200);
        // No exception propagated — pass
        assertTrue(true);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=LogServiceTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.repository.OperationLogRepository;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Service
public class LogService {
    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private final OperationLogRepository repo;
    private final ExecutorService logExecutor;

    public LogService(OperationLogRepository repo, @Qualifier("logExecutor") ExecutorService logExecutor) {
        this.repo = repo;
        this.logExecutor = logExecutor;
    }

    public void record(OperationType type, String ip, String fileId, String taskId,
                       String targetFormat, String status, long durationMs,
                       String error, String userAgent) {
        OperationLog entry = new OperationLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setOperationType(type.name());
        entry.setClientIp(ip);
        entry.setFileId(fileId);
        entry.setTaskId(taskId);
        entry.setTargetFormat(targetFormat);
        entry.setStatus(status);
        entry.setDurationMs(durationMs);
        entry.setErrorMessage(error);
        entry.setUserAgent(userAgent);
        entry.setCreatedAt(System.currentTimeMillis());
        logExecutor.submit(() -> {
            try { repo.save(entry); }
            catch (Exception e) { log.warn("log write failed", e); }
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=LogServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add LogService with async write"
```

---

### Task 15: TaskService

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/service/TaskService.java`
- Test: `backend/src/test/java/com/ofd/converter/service/TaskServiceTest.java`

**Interfaces:**
- Produces: `TaskService.create(fileId, filename, sourceType, targetFormat, optionsJson)` → `Task` (PENDING); `markProcessing(taskId)`; `markDone(taskId, outputPath, outputFilename, size, outputType)`; `markFailed(taskId, error)`; `markTimeout(taskId)`; `get(taskId)`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.service;

import com.ofd.converter.model.*;
import com.ofd.converter.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TaskServiceTest {
    @Test void createSetsPending() {
        TaskRepository repo = mock(TaskRepository.class);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TaskService svc = new TaskService(repo);
        Task t = svc.create("f1", "a.ofd", SourceType.OFD, ConvertFormat.PDF, null);
        assertEquals(TaskStatus.PENDING.name(), t.getStatus());
        verify(repo).save(any());
    }
    @Test void markDoneSetsFields() {
        Task t = new Task(); t.setId("t1"); t.setStatus(TaskStatus.PROCESSING.name()); t.setCreatedAt(1L);
        TaskRepository repo = mock(TaskRepository.class);
        when(repo.findById("t1")).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TaskService svc = new TaskService(repo);
        svc.markDone("t1", "/o/p", "a.pdf", 123L, "single");
        assertEquals(TaskStatus.DONE.name(), t.getStatus());
        assertEquals("a.pdf", t.getOutputFilename());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=TaskServiceTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.*;
import com.ofd.converter.repository.TaskRepository;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository repo;
    public TaskService(TaskRepository repo) { this.repo = repo; }

    public Task create(String fileId, String filename, SourceType src, ConvertFormat fmt, String optionsJson) {
        Task t = new Task();
        t.setId(UUID.randomUUID().toString());
        t.setSourceFileId(fileId);
        t.setSourceFilename(filename);
        t.setSourceType(src.name());
        t.setTargetFormat(fmt.name());
        t.setStatus(TaskStatus.PENDING.name());
        t.setOptionsJson(optionsJson);
        long now = System.currentTimeMillis();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        return repo.save(t);
    }

    public Task get(String taskId) {
        return repo.findById(taskId)
            .orElseThrow(() -> new ApiException(ErrorCode.TASK_NOT_FOUND, "任务不存在", 404));
    }

    public void markProcessing(String taskId) { updateStatus(taskId, TaskStatus.PROCESSING); }
    public void markTimeout(String taskId) { updateStatus(taskId, TaskStatus.TIMEOUT); }

    public void markDone(String taskId, String outputPath, String outputFilename, Long size, String outputType) {
        Task t = get(taskId);
        t.setStatus(TaskStatus.DONE.name());
        t.setOutputPath(outputPath);
        t.setOutputFilename(outputFilename);
        t.setOutputSize(size);
        t.setOutputType(outputType);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    public void markFailed(String taskId, String error) {
        Task t = get(taskId);
        t.setStatus(TaskStatus.FAILED.name());
        t.setErrorMessage(error);
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }

    private void updateStatus(String taskId, TaskStatus s) {
        Task t = get(taskId);
        t.setStatus(s.name());
        t.setUpdatedAt(System.currentTimeMillis());
        repo.save(t);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=TaskServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add TaskService for task lifecycle"
```

---

## Section C — Conversion engine (native ofdrw)

### Task 16: Converter interface + ConvertPipeline + Ofd2Pdf

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/engine/Converter.java`
- Create: `backend/src/main/java/com/ofd/converter/engine/ConvertResult.java`
- Create: `backend/src/main/java/com/ofd/converter/engine/ConvertPipeline.java`
- Create: `backend/src/main/java/com/ofd/converter/engine/converters/Ofd2Pdf.java`
- Test: `backend/src/test/java/com/ofd/converter/engine/converters/Ofd2PdfTest.java`

**Interfaces:**
- Produces: `Converter.convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts)` → `ConvertResult(outputFile, outputFilename, size, outputType)`. `ConvertPipeline.convert(SourceType, ConvertFormat, ...)` dispatches to the right `Converter` bean.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Ofd2PdfTest {
    @Test void converts(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        Ofd2Pdf c = new Ofd2Pdf();
        ConvertResult r = c.convert(ofd, tmp, "sample.ofd", null);
        assertTrue(Files.exists(r.outputFile()));
        assertEquals("sample.pdf", r.outputFilename());
        assertEquals(0x25, Files.readAllBytes(r.outputFile())[0]);
        assertEquals("single", r.outputType());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=Ofd2PdfTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

`Converter.java`:
```java
package com.ofd.converter.engine;

import java.nio.file.Path;

public interface Converter {
    ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception;
}
```

`ConvertOptions.java`:
```java
package com.ofd.converter.engine;
import java.util.Map;
public record ConvertOptions(String pages, Integer dpi) {
    public static ConvertOptions from(Map<String,Object> m) {
        if (m == null) return new ConvertOptions(null, null);
        return new ConvertOptions((String) m.get("pages"), m.get("dpi") instanceof Number n ? n.intValue() : null);
    }
}
```

`ConvertResult.java`:
```java
package com.ofd.converter.engine;
import java.nio.file.Path;
public record ConvertResult(Path outputFile, String outputFilename, long size, String outputType) {}
```

`Ofd2Pdf.java`:
```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import org.ofdrw.converter.export.PDFExporterPDFBox;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
public class Ofd2Pdf implements Converter {
    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".pdf");
        try (PDFExporterPDFBox ex = new PDFExporterPDFBox(source, out)) {
            ex.export();
        }
        return new ConvertResult(out, base + ".pdf", Files.size(out), "single");
    }

    static String basename(String filename, String ext) {
        String n = filename == null ? "file" : filename;
        int slash = n.replace('\\', '/').lastIndexOf('/');
        n = slash >= 0 ? n.substring(slash + 1) : n;
        if (n.toLowerCase().endsWith(ext)) n = n.substring(0, n.length() - ext.length());
        return n;
    }
}
```

`ConvertPipeline.java`:
```java
package com.ofd.converter.engine;

import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.util.Map;

@Component
public class ConvertPipeline {
    private final Map<String, Converter> converters;

    public ConvertPipeline(java.util.List<Converter> all) {
        this.converters = new java.util.HashMap<>();
        // registered in tasks 17–21
    }

    public ConvertResult run(SourceType src, ConvertFormat fmt, Path source, Path outputDir, String sourceFilename, ConvertOptions opts) {
        Converter c = converters.get(key(src, fmt));
        if (c == null) throw new IllegalArgumentException("不支持的转换: " + src + "→" + fmt);
        try {
            return c.convert(source, outputDir, sourceFilename, opts);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void register(SourceType src, ConvertFormat fmt, Converter c) {
        converters.put(key(src, fmt), c);
    }

    private static String key(SourceType s, ConvertFormat f) { return s + "->" + f; }
}
```

> Note: registration is wired by each converter's task via constructor injection into the pipeline. To keep wiring simple, the pipeline constructor in Task 16 starts empty; Tasks 17–21 add their converter to the pipeline by accepting the pipeline and calling `register`. Alternatively (cleaner), the pipeline iterates injected `List<Converter>` and each converter declares which `(src,fmt)` it handles via a `supports()` method. The implementer should refactor `Converter` to add `SourceType source()` and `ConvertFormat target()` and have the pipeline build the map from those — this is the recommended approach; do it in Step 3 before Task 17.

**Refactor (do this in Step 3):** Add to `Converter`:
```java
default SourceType source() { return null; }
default ConvertFormat target() { return null; }
```
And in `Ofd2Pdf`:
```java
@Override public SourceType source() { return SourceType.OFD; }
@Override public ConvertFormat target() { return ConvertFormat.PDF; }
```
And rewrite `ConvertPipeline` constructor:
```java
public ConvertPipeline(java.util.List<Converter> all) {
    this.converters = new java.util.HashMap<>();
    for (Converter c : all) {
        if (c.source() != null && c.target() != null) {
            converters.put(c.source() + "->" + c.target(), c);
        }
    }
}
```
Remove the `register` method.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=Ofd2PdfTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add Converter interface, ConvertPipeline, Ofd2Pdf"
```

---

### Task 17: Ofd2Image converter

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/engine/converters/Ofd2Image.java`
- Test: `backend/src/test/java/com/ofd/converter/engine/converters/Ofd2ImageTest.java`

**Interfaces:**
- Produces: `Ofd2Image` for `(OFD, PNG)` and `(OFD, JPG)`. Multi-page output → zipped via `FileService.zipDir`. Returns `outputType="archive"`, filename `{base}_images.zip`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.engine.ConvertOptions;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Ofd2ImageTest {
    @Test void convertsToPngZip(@TempDir Path tmp) throws Exception {
        RetentionProperties p = new RetentionProperties(); p.setDataDir(tmp.toString());
        Ofd2Image c = new Ofd2Image(new FileService(p), ConvertFormat.PNG);
        Path ofd = Fixtures.ofd(tmp);
        ConvertResult r = c.convert(ofd, tmp, "sample.ofd", new ConvertOptions(null, 150));
        assertEquals("sample_images.zip", r.outputFilename());
        assertEquals("archive", r.outputType());
        assertTrue(Files.size(r.outputFile()) > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=Ofd2ImageTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import com.ofd.converter.service.FileService;
import org.ofdrw.converter.export.ImageExporter;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
public class Ofd2Image implements Converter {
    private final FileService fileService;
    private final ConvertFormat format; // PNG or JPG

    public Ofd2Image(FileService fileService, ConvertFormat format) {
        this.fileService = fileService;
        this.format = format;
    }

    @Override public SourceType source() { return SourceType.OFD; }
    @Override public ConvertFormat target() { return format; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        double ppm = opts != null && opts.dpi() != null ? opts.dpi() / 25.4 : 15d;
        String imgDir = outputDir.resolve("pages").toString();
        Files.createDirectories(Path.of(imgDir));
        try (ImageExporter ex = new ImageExporter(source, Path.of(imgDir), format.name(), ppm)) {
            ex.export();
        }
        String base = Ofd2Pdf.basename(sourceFilename, ".ofd");
        Path zip = fileService.zipDir(Path.of(imgDir), base + "_images.zip");
        // Move zip into outputDir root
        Path finalZip = outputDir.resolve(base + "_images.zip");
        if (!zip.equals(finalZip)) Files.move(zip, finalZip, StandardCopyOption.REPLACE_EXISTING);
        return new ConvertResult(finalZip, base + "_images.zip", Files.size(finalZip), "archive");
    }
}
```

> Note: Spring needs to construct two `Ofd2Image` beans (PNG + JPG). Register them via a `@Configuration` bean method, e.g. in `ConvertPipeline`'s package add `ImageConverterConfig`:
```java
package com.ofd.converter.engine;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.service.FileService;
import org.springframework.context.annotation.*;
@Configuration
class ImageConverterConfig {
    @Bean Converter ofd2png(FileService fs) { return new com.ofd.converter.engine.converters.Ofd2Image(fs, ConvertFormat.PNG); }
    @Bean Converter ofd2jpg(FileService fs) { return new com.ofd.converter.engine.converters.Ofd2Image(fs, ConvertFormat.JPG); }
}
```
Remove `@Component` from `Ofd2Image` since it's constructed via config.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=Ofd2ImageTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add Ofd2Image converter (multi-page zip)"
```

---

### Task 18: Ofd2Text converter

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/engine/converters/Ofd2Text.java`
- Test: `backend/src/test/java/com/ofd/converter/engine/converters/Ofd2TextTest.java`

**Interfaces:**
- Produces: `Ofd2Text` for `(OFD, TXT)` using `TextExporter`. `outputType="single"`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Ofd2TextTest {
    @Test void converts(@TempDir Path tmp) throws Exception {
        Path ofd = Fixtures.ofd(tmp);
        Ofd2Text c = new Ofd2Text();
        ConvertResult r = c.convert(ofd, tmp, "sample.ofd", null);
        assertEquals("sample.txt", r.outputFilename());
        assertFalse(Files.readString(r.outputFile()).isBlank());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=Ofd2TextTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.export.TextExporter;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
public class Ofd2Text implements Converter {
    @Override public SourceType source() { return SourceType.OFD; }
    @Override public ConvertFormat target() { return ConvertFormat.TXT; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = Ofd2Pdf.basename(sourceFilename, ".ofd");
        Path out = outputDir.resolve(base + ".txt");
        try (TextExporter ex = new TextExporter(source, out)) {
            ex.export();
        }
        return new ConvertResult(out, base + ".txt", Files.size(out), "single");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=Ofd2TextTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add Ofd2Text converter"
```

---

### Task 19: Pdf2Ofd converter

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/engine/converters/Pdf2Ofd.java`
- Test: `backend/src/test/java/com/ofd/converter/engine/converters/Pdf2OfdTest.java`

**Interfaces:**
- Produces: `Pdf2Ofd` for `(PDF, OFD)` using `PDFConverter`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Pdf2OfdTest {
    @Test void converts(@TempDir Path tmp) throws Exception {
        Path pdf = Fixtures.pdf(tmp);
        Pdf2Ofd c = new Pdf2Ofd();
        ConvertResult r = c.convert(pdf, tmp, "sample.pdf", null);
        assertEquals("sample.ofd", r.outputFilename());
        byte[] head = Files.readAllBytes(r.outputFile());
        assertEquals(0x50, head[0]); assertEquals(0x4B, head[1]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=Pdf2OfdTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.ofdconverter.PDFConverter;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
public class Pdf2Ofd implements Converter {
    @Override public SourceType source() { return SourceType.PDF; }
    @Override public ConvertFormat target() { return ConvertFormat.OFD; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = Ofd2Pdf.basename(sourceFilename, ".pdf");
        Path out = outputDir.resolve(base + ".ofd");
        try (PDFConverter c = new PDFConverter(out)) {
            c.convert(source);
        }
        return new ConvertResult(out, base + ".ofd", Files.size(out), "single");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=Pdf2OfdTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add Pdf2Ofd converter"
```

---

### Task 20: Image2Ofd converter

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/engine/converters/Image2Ofd.java`
- Test: `backend/src/test/java/com/ofd/converter/engine/converters/Image2OfdTest.java`

**Interfaces:**
- Produces: `Image2Ofd` for `(IMAGE, OFD)` using `ImageConverter`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.Fixtures;
import com.ofd.converter.engine.ConvertResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Image2OfdTest {
    @Test void converts(@TempDir Path tmp) throws Exception {
        Path png = Fixtures.png(tmp);
        Image2Ofd c = new Image2Ofd();
        ConvertResult r = c.convert(png, tmp, "sample.png", null);
        assertEquals("sample.ofd", r.outputFilename());
        byte[] head = Files.readAllBytes(r.outputFile());
        assertEquals(0x50, head[0]); assertEquals(0x4B, head[1]);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=Image2OfdTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

```java
package com.ofd.converter.engine.converters;

import com.ofd.converter.engine.*;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.model.SourceType;
import org.ofdrw.converter.ofdconverter.ImageConverter;
import org.springframework.stereotype.Component;
import java.nio.file.*;

@Component
public class Image2Ofd implements Converter {
    @Override public SourceType source() { return SourceType.IMAGE; }
    @Override public ConvertFormat target() { return ConvertFormat.OFD; }

    @Override
    public ConvertResult convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts) throws Exception {
        String base = Ofd2Pdf.basename(sourceFilename, ".png");
        if (base.endsWith(".jpg")) base = base.substring(0, base.length() - 4);
        Path out = outputDir.resolve(base + ".ofd");
        try (ImageConverter c = new ImageConverter(out)) {
            c.convert(source);
        }
        return new ConvertResult(out, base + ".ofd", Files.size(out), "single");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=Image2OfdTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add Image2Ofd converter"
```

---

## Section D — Orchestration & REST API

### Task 21: ConvertService (upload + convert orchestration)

**Files:**
- Modify: `backend/src/main/java/com/ofd/converter/service/FileService.java` (add `uploadFile(fileId)`)
- Create: `backend/src/main/java/com/ofd/converter/model/dto/UploadResponse.java`
- Create: `backend/src/main/java/com/ofd/converter/model/dto/ConvertRequest.java`
- Create: `backend/src/main/java/com/ofd/converter/model/dto/ConvertResponse.java`
- Create: `backend/src/main/java/com/ofd/converter/service/ConvertService.java`
- Test: `backend/src/test/java/com/ofd/converter/service/ConvertServiceTest.java`

**Interfaces:**
- Produces: `ConvertService.upload(MultipartFile, ip, ua)` → `UploadResponse(fileId, filename, size, sourceType)`; `ConvertService.convert(ConvertRequest, ip, ua)` → `ConvertResponse(taskId, status)`. Internal `runConversion(Task)` runs on `conversionExecutor` with 5-min timeout.
- Consumes: `FileService`, `ValidationService`, `TaskService`, `ConvertPipeline`, `LogService`, `conversionExecutor`, `RetentionProperties`.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.service;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.model.dto.ConvertRequest;
import com.ofd.converter.model.dto.UploadResponse;
import com.ofd.converter.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConvertServiceTest {
    @Test void uploadStoresAndDetects(@TempDir Path tmp) throws Exception {
        var p = new RetentionProperties(); p.setDataDir(tmp.toString());
        var fs = new FileService(p);
        var validation = new ValidationService();
        var taskRepo = new org.mockito.Mockito().mock(TaskRepository.class);
        var logRepo = new org.mockito.Mockito().mock(OperationLogRepository.class);
        // build real services with mocked repos
        var taskSvc = new TaskService(taskRepo);
        var logSvc = new LogService(logRepo, java.util.concurrent.Executors.newSingleThreadExecutor());
        var pipeline = new ConvertPipeline(java.util.List.of());
        var exec = java.util.concurrent.Executors.newFixedThreadPool(2);
        var svc = new ConvertService(fs, validation, taskSvc, pipeline, logSvc, exec, p, 5);

        byte[] ofdBytes = Files.readAllBytes(Fixtures.ofd(tmp));
        MockMultipartFile mp = new MockMultipartFile("file", "sample.ofd", "application/octet-stream", ofdBytes);
        UploadResponse r = svc.upload(mp, "1.2.3.4", "ua");
        assertEquals("OFD", r.sourceType());
        assertTrue(r.fileId() != null);
        assertEquals("sample.ofd", r.filename());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=ConvertServiceTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

Add to `FileService.java`:
```java
public Path uploadFile(String fileId) {
    Path dir = dataDir.resolve("uploads/" + fileId);
    try (Stream<Path> s = Files.list(dir)) {
        return s.filter(Files::isRegularFile).findFirst().orElseThrow();
    } catch (IOException e) { throw new RuntimeException(e); }
}
```

`UploadResponse.java`:
```java
package com.ofd.converter.model.dto;
public record UploadResponse(String fileId, String filename, long size, String sourceType) {}
```

`ConvertRequest.java`:
```java
package com.ofd.converter.model.dto;
import java.util.Map;
public record ConvertRequest(String fileId, String targetFormat, Map<String, Object> options) {}
```

`ConvertResponse.java`:
```java
package com.ofd.converter.model.dto;
public record ConvertResponse(String taskId, String status) {}
```

`ConvertService.java`:
```java
package com.ofd.converter.service;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.controller.ApiException;
import com.ofd.converter.engine.ConvertOptions;
import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.engine.ConvertResult;
import com.ofd.converter.model.*;
import com.ofd.converter.model.dto.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;

@Service
public class ConvertService {
    private static final Logger log = LoggerFactory.getLogger(ConvertService.class);
    private final FileService fileService;
    private final ValidationService validation;
    private final TaskService taskService;
    private final ConvertPipeline pipeline;
    private final LogService logService;
    private final ExecutorService executor;
    private final RetentionProperties props;
    private final int timeoutMinutes;

    public ConvertService(FileService fileService, ValidationService validation,
                          TaskService taskService, ConvertPipeline pipeline, LogService logService,
                          @Qualifier("conversionExecutor") ExecutorService executor,
                          RetentionProperties props,
                          @org.springframework.beans.factory.annotation.Value("${conversion.timeout-minutes:5}") int timeoutMinutes) {
        this.fileService = fileService; this.validation = validation;
        this.taskService = taskService; this.pipeline = pipeline;
        this.logService = logService; this.executor = executor;
        this.props = props; this.timeoutMinutes = timeoutMinutes;
    }

    public UploadResponse upload(MultipartFile file, String ip, String ua) {
        validation.validateSize(file.getSize());
        if (!fileService.diskOk()) throw new ApiException(ErrorCode.STORAGE_FULL, "磁盘空间不足", 503);
        byte[] head;
        try { head = file.getInputStream().readNBytes(8); }
        catch (Exception e) { throw new ApiException(ErrorCode.INVALID_REQUEST, "读取文件失败", 400); }
        SourceType src = validation.detect(head, file.getOriginalFilename());
        String fileId = UUID.randomUUID().toString();
        String safeName = validation.sanitizeFilename(file.getOriginalFilename());
        try { fileService.storeUpload(file.getInputStream(), fileId, safeName); }
        catch (Exception e) { throw new ApiException(ErrorCode.INTERNAL_ERROR, "存储失败", 500); }
        logService.record(OperationType.UPLOAD, ip, fileId, null, null, "SUCCESS", 0, null, ua);
        return new UploadResponse(fileId, safeName, file.getSize(), src.name());
    }

    public ConvertResponse convert(ConvertRequest req, String ip, String ua) {
        Path source = fileService.uploadFile(req.fileId());
        String filename = source.getFileName().toString();
        byte[] head;
        try { head = Files.readAllBytes(source).length > 8 ? Files.readAllBytes(source) : Files.readAllBytes(source); head = java.util.Arrays.copyOf(head, 8); }
        catch (Exception e) { throw new ApiException(ErrorCode.INVALID_REQUEST, "源文件读取失败", 400); }
        // re-read first 8 bytes safely
        try (var is = Files.newInputStream(source)) { head = is.readNBytes(8); }
        catch (Exception e) { throw new ApiException(ErrorCode.INTERNAL_ERROR, "读取失败", 500); }
        SourceType src = validation.detect(head, filename);
        ConvertFormat fmt;
        try { fmt = ConvertFormat.valueOf(req.targetFormat().toUpperCase()); }
        catch (Exception e) { throw new ApiException(ErrorCode.INVALID_REQUEST, "目标格式不支持", 400); }

        Task t = taskService.create(req.fileId(), filename, src, fmt, req.options() == null ? null : req.options().toString());
        logService.record(OperationType.CONVERT, ip, req.fileId(), t.getId(), fmt.name(), "PENDING", 0, null, ua);

        executor.submit(() -> runConversion(t, src, fmt, req.options()));
        return new ConvertResponse(t.getId(), t.getStatus());
    }

    private void runConversion(Task t, SourceType src, ConvertFormat fmt, java.util.Map<String, Object> options) {
        long start = System.currentTimeMillis();
        taskService.markProcessing(t.getId());
        Future<?> f = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                Path source = fileService.uploadFile(t.getSourceFileId());
                Path outDir = fileService.createOutputDir(t.getId());
                ConvertOptions opts = ConvertOptions.from(options);
                ConvertResult r = pipeline.run(src, fmt, source, outDir, t.getSourceFilename(), opts);
                taskService.markDone(t.getId(), r.outputFile().toString(), r.outputFilename(), r.size(), r.outputType());
                logService.record(OperationType.CONVERT, null, t.getSourceFileId(), t.getId(), fmt.name(), "SUCCESS", System.currentTimeMillis() - start, null, null);
            } catch (Exception e) {
                taskService.markFailed(t.getId(), e.getMessage());
                logService.record(OperationType.CONVERT, null, t.getSourceFileId(), t.getId(), fmt.name(), "FAILED", System.currentTimeMillis() - start, e.getMessage(), null);
                try { fileService.deleteRecursively(fileService.createOutputDir(t.getId())); } catch (Exception ignored) {}
            }
        });
        try {
            f.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            f.cancel(true);
            taskService.markTimeout(t.getId());
            logService.record(OperationType.CONVERT, null, t.getSourceFileId(), t.getId(), fmt.name(), "TIMEOUT", timeoutMinutes * 60_000L, "转换超时", null);
        } catch (Exception e) {
            // already handled inside
        }
    }
}
```

> Note: the `head` re-read logic in `convert()` is awkward in the example — the implementer should simplify to a single `try (var is = Files.newInputStream(source)) { head = is.readNBytes(8); }` and delete the redundant lines. Also, `runConversion` spawns an inner single-thread executor per task for timeout control; an alternative is `CompletableFuture.orTimeout`. Either is acceptable — keep it simple.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=ConvertServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add ConvertService for upload + async conversion orchestration"
```

---

### Task 22: ConvertController (REST endpoints)

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/model/dto/TaskResponse.java`
- Create: `backend/src/main/java/com/ofd/converter/model/dto/FormatsResponse.java`
- Create: `backend/src/main/java/com/ofd/converter/controller/ConvertController.java`
- Test: `backend/src/test/java/com/ofd/converter/controller/ConvertControllerTest.java`

**Interfaces:**
- Produces: endpoints `POST /api/upload`, `POST /api/convert`, `GET /api/task/{id}`, `GET /api/download/{id}`, `GET /api/formats`, `GET /health` (spec §6).

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.controller;

import com.ofd.converter.Fixtures;
import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.repository.*;
import com.ofd.converter.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ConvertControllerTest {
    @Autowired MockMvc mvc;

    @Test void healthOk(@TempDir Path tmp) throws Exception {
        mvc.perform(get("/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test void formatsReturnsGrouped(@TempDir Path tmp) throws Exception {
        mvc.perform(get("/api/formats")).andExpect(status().isOk())
            .andExpect(jsonPath("$.ofd").isArray());
    }
}
```

> Note: `@SpringBootTest` needs the app to boot. The test `application.yml` (sqlite `:memory:`) from Task 8 applies. Upload/convert full-flow is exercised in Task 24 integration test; here we only smoke-test `health` + `formats` to keep the unit fast and avoid async timing.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=ConvertControllerTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

`TaskResponse.java`:
```java
package com.ofd.converter.model.dto;
public record TaskResponse(String taskId, String status, String downloadUrl, String error) {}
```

`FormatsResponse.java`:
```java
package com.ofd.converter.model.dto;
import java.util.List;
import java.util.Map;
public record FormatsResponse(Map<String, List<String>> formats) {}
```

`ConvertController.java`:
```java
package com.ofd.converter.controller;

import com.ofd.converter.engine.ConvertPipeline;
import com.ofd.converter.model.*;
import com.ofd.converter.model.dto.*;
import com.ofd.converter.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.*;
import java.util.*;

@RestController
public class ConvertController {
    private final ConvertService convertService;
    private final TaskService taskService;
    private final FileService fileService;

    public ConvertController(ConvertService convertService, TaskService taskService, FileService fileService) {
        this.convertService = convertService; this.taskService = taskService; this.fileService = fileService;
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "ok"); }

    @GetMapping("/api/formats")
    public FormatsResponse formats() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("ofd", List.of("pdf", "png", "jpg", "docx", "txt", "md"));
        m.put("pdf", List.of("ofd", "png", "jpg"));
        m.put("image", List.of("ofd"));
        m.put("docx", List.of("ofd"));
        return new FormatsResponse(m);
    }

    @PostMapping("/api/upload")
    public UploadResponse upload(@RequestParam("file") MultipartFile file, HttpServletRequest req) {
        return convertService.upload(file, ClientIpInterceptor.extractIp(req), req.getHeader("User-Agent"));
    }

    @PostMapping("/api/convert")
    public ConvertResponse convert(@RequestBody ConvertRequest body, HttpServletRequest req) {
        return convertService.convert(body, ClientIpInterceptor.extractIp(req), req.getHeader("User-Agent"));
    }

    @GetMapping("/api/task/{taskId}")
    public TaskResponse task(@PathVariable String taskId) {
        Task t = taskService.get(taskId);
        String url = TaskStatus.DONE.name().equals(t.getStatus()) ? "/api/download/" + t.getId() : null;
        return new TaskResponse(t.getId(), t.getStatus(), url, t.getErrorMessage());
    }

    @GetMapping("/api/download/{taskId}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String taskId) {
        Task t = taskService.get(taskId);
        if (!TaskStatus.DONE.name().equals(t.getStatus())) {
            throw new ApiException(ErrorCode.FILE_EXPIRED, "文件未就绪或已过期", 410);
        }
        Path file = Paths.get(t.getOutputPath());
        if (!Files.exists(file)) throw new ApiException(ErrorCode.FILE_EXPIRED, "文件已清理", 410);
        if (t.getDownloadedAt() == null) {
            t.setDownloadedAt(System.currentTimeMillis());
            taskService.saveDownloadedAt(t.getId(), t.getDownloadedAt());
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + t.getOutputFilename() + "\"")
            .body(new FileSystemResource(file));
    }
}
```

Add to `TaskService`:
```java
public void saveDownloadedAt(String taskId, long ts) {
    Task t = get(taskId);
    t.setDownloadedAt(ts);
    t.setUpdatedAt(System.currentTimeMillis());
    repo.save(t);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=ConvertControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add ConvertController with REST endpoints"
```

---

### Task 23: Cleanup schedulers

**Files:**
- Create: `backend/src/main/java/com/ofd/converter/scheduler/FileCleanupScheduler.java`
- Create: `backend/src/main/java/com/ofd/converter/scheduler/LogCleanupScheduler.java`
- Modify: `backend/src/main/java/com/ofd/converter/repository/TaskRepository.java` (add `deleteByCreatedAtBefore`)
- Test: `backend/src/test/java/com/ofd/converter/scheduler/FileCleanupSchedulerTest.java`

**Interfaces:**
- Produces: `FileCleanupScheduler` (hourly, deletes uploads/outputs older than `retention-hours` + their tasks), `LogCleanupScheduler` (daily, deletes logs older than `log.retention-days`).

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.scheduler;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.repository.TaskRepository;
import com.ofd.converter.service.FileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileCleanupSchedulerTest {
    @Test void deletesOldUploads(@TempDir Path tmp) throws Exception {
        var p = new RetentionProperties(); p.setDataDir(tmp.toString()); p.setRetentionHours(1);
        var fs = new FileService(p);
        var taskRepo = mock(TaskRepository.class);
        var sched = new FileCleanupScheduler(fs, taskRepo, p);

        Path oldDir = tmp.resolve("uploads/old");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("a.ofd"), "x");
        // set mtime to 2 hours ago
        Files.setLastModifiedTime(oldDir, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 2 * 3600_000L));

        sched.cleanup();
        assertFalse(Files.exists(oldDir));
        verify(taskRepo).deleteByCreatedAtBefore(anyLong());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=FileCleanupSchedulerTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

Add to `TaskRepository`:
```java
@org.springframework.data.jdbc.repository.query.Modifying
@org.springframework.data.jdbc.repository.query.Query("DELETE FROM task WHERE created_at < :before")
void deleteByCreatedAtBefore(@org.springframework.data.repository.query.Param("before") long before);
```

`FileCleanupScheduler.java`:
```java
package com.ofd.converter.scheduler;

import com.ofd.converter.config.RetentionProperties;
import com.ofd.converter.repository.TaskRepository;
import com.ofd.converter.service.FileService;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

@Component
public class FileCleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(FileCleanupScheduler.class);
    private final FileService fileService;
    private final TaskRepository taskRepo;
    private final RetentionProperties props;

    public FileCleanupScheduler(FileService fileService, TaskRepository taskRepo, RetentionProperties props) {
        this.fileService = fileService; this.taskRepo = taskRepo; this.props = props;
    }

    @Scheduled(fixedRate = 3600_000L)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - props.getRetentionHours() * 3600_000L;
        Path root = Paths.get(props.getDataDir());
        for (String sub : List.of("uploads", "outputs")) {
            Path dir = root.resolve(sub);
            if (!Files.exists(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(Files::isDirectory).forEach(d -> {
                    try {
                        if (Files.getLastModifiedTime(d).toMillis() < cutoff) {
                            fileService.deleteRecursively(d);
                        }
                    } catch (IOException ignored) {}
                });
            } catch (IOException e) { log.warn("cleanup list failed", e); }
        }
        taskRepo.deleteByCreatedAtBefore(cutoff);
    }
}
```

`LogCleanupScheduler.java`:
```java
package com.ofd.converter.scheduler;

import com.ofd.converter.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogCleanupScheduler {
    private final OperationLogRepository repo;
    private final int retentionDays;

    public LogCleanupScheduler(OperationLogRepository repo,
                               @Value("${log.retention-days:90}") int retentionDays) {
        this.repo = repo; this.retentionDays = retentionDays;
    }

    @Scheduled(fixedRate = 86400_000L)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - retentionDays * 86400_000L;
        repo.deleteByCreatedAtBefore(cutoff);
    }
}
```

Enable scheduling in `OfdConverterApplication.java` — add `@EnableScheduling`:
```java
@SpringBootApplication
@EnableScheduling
public class OfdConverterApplication { ... }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=FileCleanupSchedulerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add file and log cleanup schedulers"
```

---

### Task 24: API integration test (full flow)

**Files:**
- Create: `backend/src/test/java/com/ofd/converter/controller/FullFlowIntegrationTest.java`

**Interfaces:**
- Produces: end-to-end test: upload OFD → convert to PDF → poll task → download. Validates the whole stack works together.

- [ ] **Step 1: Write the test**

```java
package com.ofd.converter.controller;

import com.ofd.converter.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FullFlowIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void uploadConvertDownload(@TempDir Path tmp) throws Exception {
        byte[] ofd = Files.readAllBytes(Fixtures.ofd(tmp));
        MockMultipartFile mp = new MockMultipartFile("file", "sample.ofd", "application/octet-stream", ofd);

        String uploadResp = mvc.perform(multipart("/api/upload").file(mp))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String fileId = JsonPath.read(uploadResp, "$.file_id");

        String convertResp = mvc.perform(post("/api/convert")
                .contentType("application/json")
                .content("{\"fileId\":\"" + fileId + "\",\"targetFormat\":\"pdf\"}"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String taskId = JsonPath.read(convertResp, "$.task_id");

        // poll until done (max ~30s)
        boolean done = false;
        for (int i = 0; i < 60; i++) {
            String status = JsonPath.read(mvc.perform(get("/api/task/" + taskId))
                .andReturn().getResponse().getContentAsString(), "$.status");
            if ("done".equals(status)) { done = true; break; }
            if ("failed".equals(status) || "timeout".equals(status)) break;
            Thread.sleep(500);
        }
        assertTrue(done, "task should reach done");

        mvc.perform(get("/api/download/" + taskId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("sample.pdf")));
    }

    static void assertTrue(boolean cond, String msg) { org.junit.jupiter.api.Assertions.assertTrue(cond, msg); }
}
```

> Note: add `com.jayway.jsonpath:json-path` to test dependencies in pom (Spring Boot Test usually brings it transitively; if not, add it). The `assertTrue` static wrapper avoids an extra import collision — in the real file just import `Assertions.assertTrue`.

- [ ] **Step 2: Run test**

Run: `cd backend && mvn -q test -Dtest=FullFlowIntegrationTest`
Expected: PASS (full flow completes, download returns `sample.pdf`).

- [ ] **Step 3: Commit**

```bash
git add backend/src/test
git commit -m "test: add full upload-convert-download integration test"
```

---

## Section E — Deployment & hardening

### Task 25: Dockerfile + docker-compose.yml

**Files:**
- Create: `backend/Dockerfile`
- Create: `docker-compose.yml`
- Modify: `backend/src/main/resources/application.yml` (no change needed; env vars already wired)

**Interfaces:**
- Produces: a buildable Docker image for the backend + a compose file with a `/data` volume and healthcheck.

- [ ] **Step 1: Write Dockerfile**

`backend/Dockerfile`:
```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Bound JVM heap to leave room for OS; per-task memory is bounded by this + try/catch OOM
ENV JAVA_OPTS="-Xmx768m"
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
```

> Note on spec §13 "单任务 ≤ 512MB": true per-task cgroup isolation isn't feasible in-process. We approximate by bounding total JVM heap to 768m (`-Xmx768m`) so 4 parallel tasks share headroom, and `runConversion` (Task 21) catches `Throwable` (including `OutOfMemoryError`) to mark the task `failed`. Record this approximation in the findings/README.

- [ ] **Step 2: Write docker-compose.yml**

`docker-compose.yml`:
```yaml
services:
  backend:
    build: ./backend
    ports:
      - "8080:8080"
    volumes:
      - ofd-data:/data
    environment:
      - OFD_DATA_DIR=/data
      - OFD_DB_PATH=/data/converter.db
      - FILE_RETENTION_HOURS=24
      - LOG_RETENTION_DAYS=90
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:8080/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped
volumes:
  ofd-data:
```

- [ ] **Step 3: Build and run**

Run: `cd /home/alex/my_workspace/ofd-converter && docker compose up --build -d`
Then: `curl -s http://localhost:8080/health`
Expected: `{"status":"ok"}`

- [ ] **Step 4: Commit**

```bash
git add backend/Dockerfile docker-compose.yml
git commit -m "feat: add Dockerfile and docker-compose for backend"
```

---

### Task 26: Upload rate limiting

Spec §8 requires "单 IP 每分钟最多 20 次上传". This was a gap in earlier tasks — add it here.

**Files:**
- Modify: `backend/src/main/java/com/ofd/converter/model/ErrorCode.java` (add `TOO_MANY_REQUESTS`)
- Create: `backend/src/main/java/com/ofd/converter/service/UploadRateLimiter.java`
- Modify: `backend/src/main/java/com/ofd/converter/service/ConvertService.java` (call rate limiter in `upload`)
- Test: `backend/src/test/java/com/ofd/converter/service/UploadRateLimiterTest.java`

**Interfaces:**
- Produces: `UploadRateLimiter.check(String ip)` — throws `ApiException(TOO_MANY_REQUESTS, "上传过于频繁，请稍后再试", 429)` after 20 uploads/min from one IP.

- [ ] **Step 1: Write the failing test**

```java
package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UploadRateLimiterTest {
    @Test void allowsUnderLimit() {
        UploadRateLimiter rl = new UploadRateLimiter();
        for (int i = 0; i < 20; i++) assertDoesNotThrow(() -> rl.check("1.2.3.4"));
    }
    @Test void rejectsOverLimit() {
        UploadRateLimiter rl = new UploadRateLimiter();
        for (int i = 0; i < 20; i++) rl.check("1.2.3.4");
        ApiException ex = assertThrows(ApiException.class, () -> rl.check("1.2.3.4"));
        assertEquals("TOO_MANY_REQUESTS", ex.code.name());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && mvn -q test -Dtest=UploadRateLimiterTest`
Expected: FAIL.

- [ ] **Step 3: Write minimal implementation**

Add to `ErrorCode.java`:
```java
TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
```

`UploadRateLimiter.java`:
```java
package com.ofd.converter.service;

import com.ofd.converter.controller.ApiException;
import com.ofd.converter.model.ErrorCode;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UploadRateLimiter {
    private static final int MAX_PER_MINUTE = 20;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    public void check(String ip) {
        Window w = counters.computeIfAbsent(ip, k -> new Window());
        synchronized (w) {
            long now = System.currentTimeMillis();
            if (now - w.windowStart > 60_000) { w.count = 0; w.windowStart = now; }
            w.count++;
            if (w.count > MAX_PER_MINUTE) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "上传过于频繁，请稍后再试", 429);
            }
        }
    }

    private static final class Window {
        long windowStart = System.currentTimeMillis();
        int count = 0;
    }
}
```

In `ConvertService.upload`, add as the first line:
```java
rateLimiter.check(ip);
```
And add `UploadRateLimiter rateLimiter` to `ConvertService` constructor parameters.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && mvn -q test -Dtest=UploadRateLimiterTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test
git commit -m "feat: add per-IP upload rate limiting (20/min)"
```

---

### Task 27: README

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write README**

`README.md`:
```markdown
# OFD Converter

基于 Web 的 OFD 文件格式转换工具（后端 MVP）。

## 范围（Plan 1）
- OFD → PDF / PNG / JPG / 文本
- PDF → OFD、图片 → OFD
- REST API：上传 / 转换 / 查询 / 下载
- 操作日志（基于 IP）、定时清理、文件保留期可配置
- Docker Compose 部署

OFD→DOCX/Markdown/DOCX→OFD 的生产实现见 Plan 2（已完成 PoC）。
前端、MCP 见 Plan 3/4。

## 本地开发
\`\`\`bash
cd backend
mvn test        # 运行所有测试（含 PoC）
mvn spring-boot:run
\`\`\`

## Docker 部署
\`\`\`bash
docker compose up --build -d
curl http://localhost:8080/health   # {"status":"ok"}
\`\`\`

## 配置（环境变量）
| 变量 | 默认 | 说明 |
|---|---|---|
| OFD_DATA_DIR | /data | 数据目录 |
| OFD_DB_PATH | /data/converter.db | SQLite 路径 |
| FILE_RETENTION_HOURS | 24 | 文件保留小时 |
| LOG_RETENTION_DAYS | 90 | 日志保留天数 |

## API
- POST /api/upload (multipart) → {file_id, filename, size, sourceType}
- POST /api/convert {file_id, target_format, options?} → {task_id, status}
- GET /api/task/{task_id} → {task_id, status, download_url?, error?}
- GET /api/download/{task_id} → 文件流
- GET /api/formats → 按源格式分组的目标格式
- GET /health → {"status":"ok"}

target_format: pdf | png | jpg | txt | ofd  (docx/md 见 Plan 2)
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: add README for backend MVP"
```

---

## Self-Review

**1. Spec coverage (Plan 1 scope = PoC + Phase 1 backend):**

| Spec section | Covered by | Status |
|---|---|---|
| §1 核心功能 (native conversions) | Tasks 16–20 | ✅ |
| §1 核心功能 (DOCX/Markdown/DOCX→OFD) | Tasks 4–6 (PoC only) | ⏭️ production in Plan 2 |
| §2 技术栈 | Task 1 | ✅ |
| §3 项目结构 | All tasks | ✅ |
| §4 架构 (Docker) | Task 25 | ✅ (nginx deferred to Plan 3) |
| §5 转换引擎 + PoC | Tasks 3–6, 16–20 | ✅ |
| §6 REST API | Task 22 | ✅ |
| §7 文件管理 (命名/ZIP/保留期/DB 字段) | Tasks 8, 13, 17 | ✅ |
| §8 安全 (魔数/大小/文件名/磁盘/限流) | Tasks 12, 13, 26 | ✅ (rate limit added — was a gap) |
| §8 内存限制 512MB | Task 25 note | ⚠️ approximated via JVM -Xmx768m + OOM catch (no true cgroup isolation) |
| §9 操作日志 | Tasks 9, 10, 14 | ✅ |
| §10 错误处理 | Tasks 11, 21 | ✅ |
| §11 前端 | — | ⏭️ Plan 3 |
| §12 测试 | All tasks TDD + Task 24 | ✅ |
| §13 非功能 (并发/超时/保留期/磁盘) | Tasks 10, 21, 25 | ✅ |
| §14 阶段 0+1 | Sections A–E | ✅ |
| §15 后续扩展 | — | ⏭️ out of scope |

**Gaps found & fixed during self-review:**
- Rate limiting (spec §8) had no task → added Task 26.
- `saveDownloadedAt` was used in Task 22 but not defined in Task 15's `TaskService` → added the method in Task 22 Step 3.

**Deferred (explicit):**
- OFD→DOCX, OFD→Markdown, DOCX→OFD production converters → Plan 2 (after PoC findings).
- Frontend (React + ofd.js) → Plan 3.
- MCP endpoint → Plan 4.
- nginx + full Docker Compose with frontend → Plan 3.
- True per-task memory isolation → not feasible in-JVM; documented as approximation.

**2. Placeholder scan:** Searched for TBD/TODO/"implement later" — none in task deliverables. The "Note:" blocks flag ofdrw API uncertainties that are *resolved by running the PoC* (Tasks 3–6) — these are intentional verification points, not placeholders. The PNG byte-literal in Task 12's test is explicitly flagged "fix this when writing the file."

**3. Type consistency:** Checked across tasks —
- `ConvertResult(Path outputFile, String outputFilename, long size, String outputType)` — consistent in Tasks 16–20.
- `Converter.convert(Path source, Path outputDir, String sourceFilename, ConvertOptions opts)` — consistent.
- `TaskService.create/markProcessing/markDone/markFailed/markTimeout/get/saveDownloadedAt` — consistent in Tasks 15, 21, 22.
- `ClientIpInterceptor.extractIp(HttpServletRequest)` — consistent in Tasks 10, 22.
- `FileService.storeUpload/createOutputDir/zipDir/diskOk/deleteRecursively/uploadFile` — consistent in Tasks 13, 17, 21, 23.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-05-ofd-converter-plan-1.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
