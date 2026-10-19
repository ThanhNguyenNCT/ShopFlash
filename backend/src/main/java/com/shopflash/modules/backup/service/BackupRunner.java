package com.shopflash.modules.backup.service;

import com.shopflash.modules.backup.dto.response.BackupArtifactResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Chạy duy nhất các lệnh Docker Compose được định nghĩa trong mã nguồn. Không nhận command, tên file hoặc đường dẫn
 * từ HTTP request. Artifact luôn được copy khỏi Docker volume trước khi response được trả về.
 */
@Service
public class BackupRunner {

    private static final Duration AVAILABILITY_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration ORACLE_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration REDIS_TIMEOUT = Duration.ofMinutes(3);
    private static final int MAX_OUTPUT_BYTES = 16_384;

    public Availability availability() {
        try {
            Path workspace = workspace();
            CommandResult result = execute(workspace, AVAILABILITY_TIMEOUT, "docker", "compose", "version");
            if (result.exitCode == 0) {
                return new Availability(true, "READY", "Trình tạo bản sao lưu đã sẵn sàng tạo tệp sao lưu trên máy cục bộ.");
            }
            return new Availability(false, "DOCKER_UNAVAILABLE", "Không gọi được Docker Compose trên máy local.");
        } catch (BackupExecutionException error) {
            return new Availability(false, "NOT_CONFIGURED", "Trình tạo bản sao lưu chưa được cấu hình cho thư mục dự án này.");
        }
    }

    public Path artifactRoot() {
        return workspace().resolve("backups");
    }

    public Artifacts create(String backupId, Path backupDirectory) {
        Path workspace = workspace();
        try {
            Files.createDirectories(backupDirectory.resolve("oracle"));
            Files.createDirectories(backupDirectory.resolve("redis"));
        } catch (IOException error) {
            throw new BackupExecutionException("Không thể tạo thư mục lưu bản sao lưu", error);
        }

        BackupArtifactResponse oracle = exportOracle(workspace, backupId, backupDirectory.resolve("oracle"));
        BackupArtifactResponse redis = snapshotRedis(workspace, backupDirectory.resolve("redis"));
        return new Artifacts(oracle, redis);
    }

    private BackupArtifactResponse exportOracle(Path workspace, String backupId, Path destination) {
        String dumpFile = "shop-" + backupId + ".dmp";
        String logFile = "shop-" + backupId + "-export.log";
        String directory = dataPumpDirectory(workspace);
        requireSuccess("Oracle Data Pump export", execute(workspace, ORACLE_TIMEOUT, "docker", "compose", "exec", "-T", "oracle", "bash", "-lc", """
                set -euo pipefail
                expdp "shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1" DIRECTORY=DATA_PUMP_DIR \
                  DUMPFILE=%s LOGFILE=%s CONTENT=ALL REUSE_DUMPFILES=NO METRICS=YES
                """.formatted(dumpFile, logFile)));

        Path dump = destination.resolve(dumpFile);
        copyFromContainer(workspace, "oracle:" + directory + "/" + dumpFile, dump, ORACLE_TIMEOUT, "Oracle dump");
        copyFromContainer(workspace, "oracle:" + directory + "/" + logFile, destination.resolve(logFile), ORACLE_TIMEOUT, "Oracle export log");
        return artifact("ORACLE", "oracle/" + dumpFile, dump);
    }

    private BackupArtifactResponse snapshotRedis(Path workspace, Path destination) {
        requireSuccess("Redis RDB snapshot", execute(workspace, REDIS_TIMEOUT, "docker", "compose", "exec", "-T", "redis", "sh", "-lc", """
                set -eu
                before=$(redis-cli --raw LASTSAVE)
                redis-cli BGSAVE >/dev/null
                attempt=0
                while [ "$attempt" -lt 120 ]; do
                  after=$(redis-cli --raw LASTSAVE)
                  if [ "$after" -gt "$before" ]; then
                    exit 0
                  fi
                  sleep 1
                  attempt=$((attempt + 1))
                done
                exit 1
                """));

        Path rdb = destination.resolve("dump.rdb");
        copyFromContainer(workspace, "redis:/data/dump.rdb", rdb, REDIS_TIMEOUT, "Redis RDB snapshot");
        return artifact("REDIS", "redis/dump.rdb", rdb);
    }

    private String dataPumpDirectory(Path workspace) {
        CommandResult result = execute(workspace, AVAILABILITY_TIMEOUT, "docker", "compose", "exec", "-T", "oracle", "bash", "-lc", """
                set -e
                sqlplus -s -L /nolog <<SQL
                connect shop/${SHOP_PASSWORD}@//localhost:1521/FREEPDB1
                set heading off feedback off pages 0 verify off
                select directory_path from all_directories where directory_name = 'DATA_PUMP_DIR';
                exit
                SQL
                """);
        requireSuccess("Đọc thư mục Data Pump", result);
        return result.output.lines().map(String::strip).filter(line -> line.startsWith("/"))
                .findFirst().orElseThrow(() -> new BackupExecutionException("Không đọc được thư mục Data Pump"));
    }

    private void copyFromContainer(Path workspace, String source, Path destination, Duration timeout, String label) {
        requireSuccess(label, execute(workspace, timeout, "docker", "compose", "cp", source, destination.toString()));
        if (!Files.isRegularFile(destination)) {
            throw new BackupExecutionException(label + " không được tạo trên máy local");
        }
    }

    private static BackupArtifactResponse artifact(String source, String filename, Path file) {
        try {
            long bytes = Files.size(file);
            if (bytes <= 0) throw new BackupExecutionException("Tệp sao lưu " + source + " rỗng");
            return new BackupArtifactResponse(source, filename, bytes, sha256(file));
        } catch (IOException error) {
            throw new BackupExecutionException("Không đọc được tệp sao lưu " + source, error);
        }
    }

    private static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8_192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new BackupExecutionException("Không tạo được mã SHA-256 của tệp sao lưu", error);
        }
    }

    private static void requireSuccess(String label, CommandResult result) {
        if (result.exitCode != 0) throw new BackupExecutionException(label + " thất bại");
    }

    private static CommandResult execute(Path workspace, Duration timeout, String... command) {
        try {
            Process process = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true).start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Thread reader = Thread.ofVirtual().start(() -> copyOutput(process.getInputStream(), output));
            boolean completed = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor();
            }
            reader.join();
            return new CommandResult(completed ? process.exitValue() : -1, output.toString(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new BackupExecutionException("Không thể chạy Docker Compose", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BackupExecutionException("Trình tạo bản sao lưu bị ngắt", error);
        }
    }

    private static void copyOutput(InputStream input, ByteArrayOutputStream output) {
        try (input) {
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() < MAX_OUTPUT_BYTES) {
                    output.write(buffer, 0, Math.min(read, MAX_OUTPUT_BYTES - output.size()));
                }
            }
        } catch (IOException ignored) {
            // Lệnh chính đã trả exit code; output chỉ phục vụ parse DATA_PUMP_DIR.
        }
    }

    private static Path workspace() {
        Path current = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int level = 0; level < 4 && current != null; level++, current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("compose.yaml"))) return current;
        }
        throw new BackupExecutionException("Không tìm thấy compose.yaml của ShopFlash");
    }

    public record Availability(boolean configured, String state, String message) {
    }

    public record Artifacts(BackupArtifactResponse oracle, BackupArtifactResponse redis) {
    }

    private record CommandResult(int exitCode, String output) {
    }

    public static class BackupExecutionException extends RuntimeException {
        BackupExecutionException(String message) {
            super(message);
        }

        BackupExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
