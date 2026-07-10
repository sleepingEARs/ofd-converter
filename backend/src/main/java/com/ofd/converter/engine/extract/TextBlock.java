package com.ofd.converter.engine.extract;

/**
 * A text block extracted from an OFD, with geometry (mm) and font metadata.
 * Shared input to both DOCX and Markdown structure inference.
 */
public record TextBlock(
    int pageIndex,      // 0-based page index
    double x,           // top-left X, mm
    double y,           // top-left Y, mm
    double width,       // mm
    double height,      // mm
    Double fontSize,    // mm, null if unset
    String fontRefId,   // font reference ID, null if unset
    String text         // concatenated TextCode content
) {}
