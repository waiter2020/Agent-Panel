package com.agentpanel.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class FaviconController {

    @GetMapping("/favicon.ico")
    public ResponseEntity<byte[]> favicon() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/favicon.svg");
        if (!resource.exists()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("image/svg+xml"))
                .body(resource.getInputStream().readAllBytes());
    }
}
