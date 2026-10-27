package com.shopflash.modules.transaction.study;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Bảng chứng cứ chỉ chứa trạng thái fixture, không chứa mật khẩu hoặc token. */
final class SemanticsEvidence {
    private SemanticsEvidence() {}

    static synchronized void record(String system, String scenario, String steps, String before,
                                    String expected, String actual, String verification, boolean passed) {
        Path file = Path.of("target", "transaction-evidence", system + ".csv");
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) Files.writeString(file,
                    "scenario,steps,before,expected,actual,verification,assertion\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            Files.writeString(file, String.join(",", cell(scenario), cell(steps), cell(before),
                    cell(expected), cell(actual), cell(verification), cell(passed ? "PASS" : "FAIL")) + "\n",
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException error) {
            throw new IllegalStateException("Không ghi được chứng cứ Transaction", error);
        }
    }

    private static String cell(String value) {
        return "\"" + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + "\"";
    }
}
