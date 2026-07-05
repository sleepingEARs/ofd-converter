package com.ofd.converter.engine;

import com.ofd.converter.engine.converters.Ofd2Image;
import com.ofd.converter.model.ConvertFormat;
import com.ofd.converter.service.FileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers two Ofd2Image beans (PNG + JPG). Ofd2Image is not @Component because it needs
 * a per-bean ConvertFormat argument; the ConvertPipeline picks them up via source()/target().
 */
@Configuration
class ImageConverterConfig {

    @Bean
    Converter ofd2png(FileService fs) {
        return new Ofd2Image(fs, ConvertFormat.PNG);
    }

    @Bean
    Converter ofd2jpg(FileService fs) {
        return new Ofd2Image(fs, ConvertFormat.JPG);
    }
}
