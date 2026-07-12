package com.ofd.converter;

import org.junit.jupiter.api.Test;
import org.ofdrw.reader.OFDReader;
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
