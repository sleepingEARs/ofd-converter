package com.ofd.converter;

import org.ofdrw.layout.OFDDoc;
import org.ofdrw.layout.PageLayout;
import org.ofdrw.layout.element.Paragraph;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Generates small valid fixture files using ofdrw itself (no external downloads). */
public final class Fixtures {
    private Fixtures() {}

    /** A text OFD built with ofdrw-layout. */
    public static Path ofd(Path dir) throws Exception {
        Path ofd = dir.resolve("sample.ofd");
        try (OFDDoc doc = new OFDDoc(ofd)) {
            doc.setDefaultPageLayout(PageLayout.A4());
            for (int i = 1; i <= 3; i++) {
                doc.add(new Paragraph("第 " + i + " 页：OFD 转换工具测试文本。"));
            }
        }
        return ofd;
    }

    /** An OFD with three paragraphs at different font sizes (simulates H1/H2/body). */
    public static Path ofdWithHeadings(Path dir) throws Exception {
        Path ofd = dir.resolve("headings.ofd");
        try (OFDDoc doc = new OFDDoc(ofd)) {
            doc.setDefaultPageLayout(PageLayout.A4());
            doc.add(new Paragraph("一级标题").setFontSize(5.0));
            doc.add(new Paragraph("二级标题").setFontSize(3.5));
            doc.add(new Paragraph("这是正文段落，字号较小。").setFontSize(2.5));
        }
        return ofd;
    }

    /** An OFD with list-like paragraphs (numbered and bulleted). */
    public static Path ofdWithList(Path dir) throws Exception {
        Path ofd = dir.resolve("list.ofd");
        try (OFDDoc doc = new OFDDoc(ofd)) {
            doc.setDefaultPageLayout(PageLayout.A4());
            doc.add(new Paragraph("1. 第一项").setFontSize(2.5));
            doc.add(new Paragraph("2. 第二项").setFontSize(2.5));
            doc.add(new Paragraph("- 无序项 A").setFontSize(2.5));
            doc.add(new Paragraph("- 无序项 B").setFontSize(2.5));
        }
        return ofd;
    }

    /** A minimal valid PDF written by hand. */
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
