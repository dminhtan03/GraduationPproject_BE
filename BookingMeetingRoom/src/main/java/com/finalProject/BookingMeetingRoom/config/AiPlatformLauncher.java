package com.finalProject.BookingMeetingRoom.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * Tự động start/stop ai-platform (Python FastAPI) cùng với Spring Boot.
 *
 * Đặt ai-platform tại: BookingMeetingRoom/ai-platform/ai-platform/
 * Config trong application-local.yml:
 *   ai:
 *     platform:
 *       dir: ./ai-platform/ai-platform
 */
@Slf4j
@Component
public class AiPlatformLauncher implements ApplicationListener<ApplicationReadyEvent> {

    @Value("${ai.platform.dir:./ai-platform}")
    private String aiPlatformDir;

    @Value("${ai.platform.enabled:true}")
    private boolean enabled;

    @Value("${ai.platform.port:8001}")
    private int port;

    private Process aiProcess;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!enabled) {
            log.info("AI Platform auto-launch disabled (ai.platform.enabled=false)");
            return;
        }

        File workDir = Paths.get(aiPlatformDir).toAbsolutePath().toFile();
        if (!workDir.exists()) {
            log.warn("AI Platform directory not found: {} — skipping auto-launch", workDir);
            return;
        }

        // Check if already running
        if (isPortInUse(port)) {
            log.info("AI Platform already running on port {}", port);
            return;
        }

        try {
            // Python executable inside .venv
            String python = workDir.toPath()
                    .resolve(".venv/Scripts/python.exe")
                    .toAbsolutePath().toString();

            // Fallback to system python if .venv not found
            if (!new File(python).exists()) {
                python = "python";
                log.warn(".venv not found, falling back to system python");
            }

            ProcessBuilder pb = new ProcessBuilder(
                    python, "-m", "uvicorn", "app.main:app",
                    "--host", "0.0.0.0",
                    "--port", String.valueOf(port),
                    "--reload"
            );
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            // Redirect output to a log file next to workDir
            File logFile = workDir.toPath().resolve("../ai-platform.log").toFile();
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));

            aiProcess = pb.start();
            log.info("AI Platform started (PID={}) on port {} — logs: {}",
                    aiProcess.pid(), port, logFile.getAbsolutePath());

        } catch (IOException e) {
            log.error("Failed to start AI Platform: {}", e.getMessage());
        }
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        if (aiProcess != null && aiProcess.isAlive()) {
            log.info("Stopping AI Platform (PID={})...", aiProcess.pid());
            aiProcess.destroy();
        }
    }

    private boolean isPortInUse(int port) {
        try {
            new java.net.ServerSocket(port).close();
            return false; // port free
        } catch (IOException e) {
            return true;  // port in use
        }
    }
}
