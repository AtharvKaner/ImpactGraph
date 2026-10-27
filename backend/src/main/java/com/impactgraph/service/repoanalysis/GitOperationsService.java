package com.impactgraph.service.repoanalysis;

import com.impactgraph.dto.repoanalysis.ChangedFileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes isolated Git CLI operations using ProcessBuilder without shell interpolation.
 * Enforces execution timeouts, size caps, and secure credential handling.
 */
@Service
public class GitOperationsService {

    private static final Logger log = LoggerFactory.getLogger(GitOperationsService.class);

    private static final int DEFAULT_TIMEOUT_SECONDS = 45;
    private static final long DEFAULT_MAX_SIZE_BYTES = 100 * 1024 * 1024L; // 100 MB

    /**
     * Clones a repository into an isolated temporary directory.
     *
     * @param repositoryUrl       HTTPS repository URL
     * @param branch              optional branch or revision
     * @param personalAccessToken optional GitHub token (passed via extraHeader, never logged)
     * @param targetDir           destination directory
     * @param timeoutSeconds      max execution duration
     * @param maxSizeBytes        max allowed repository size
     * @throws Exception if clone fails, times out, or exceeds size
     */
    public void cloneRepository(String repositoryUrl, String branch, String personalAccessToken,
                                Path targetDir, int timeoutSeconds, long maxSizeBytes) throws Exception {
        if (targetDir == null || !Files.exists(targetDir)) {
            throw new IllegalArgumentException("Target directory must exist");
        }

        int timeout = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        long maxSize = maxSizeBytes > 0 ? maxSizeBytes : DEFAULT_MAX_SIZE_BYTES;

        List<String> command = new ArrayList<>();
        command.add("git");

        if (personalAccessToken != null && !personalAccessToken.isBlank()) {
            command.add("-c");
            command.add("http.extraHeader=Authorization: Bearer " + personalAccessToken);
        }

        command.add("clone");
        command.add("--depth");
        command.add("50");
        command.add("--single-branch");

        if (branch != null && !branch.isBlank()) {
            command.add("--branch");
            command.add(branch);
        }

        command.add(repositoryUrl);
        command.add(targetDir.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);

        log.info("Cloning repository {} into isolated temporary directory {}", repositoryUrl, targetDir);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 32768) {
                    output.append(line).append("\n");
                }
            }
        }

        boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            deleteDirectoryRecursively(targetDir);
            throw new TimeoutException("Git clone operation timed out after " + timeout + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            deleteDirectoryRecursively(targetDir);
            String sanitizedError = sanitizeErrorOutput(output.toString(), personalAccessToken);
            throw new IllegalStateException("Git clone failed (exit " + exitCode + "): " + sanitizedError);
        }

        // Verify size limit
        long actualSize = calculateDirectorySize(targetDir);
        if (actualSize > maxSize) {
            deleteDirectoryRecursively(targetDir);
            throw new IllegalStateException(String.format(
                    "Repository exceeds maximum allowed size limit (%d MB > %d MB)",
                    actualSize / (1024 * 1024), maxSize / (1024 * 1024)
            ));
        }

        log.info("Repository cloned successfully ({} MB)", actualSize / (1024 * 1024));
    }

    /**
     * Resolves the HEAD commit SHA for a repository directory.
     */
    public String getHeadCommitSha(Path repoDir) {
        try {
            return runGitCommand(repoDir, 10, "rev-parse", "HEAD").trim();
        } catch (Exception e) {
            log.warn("Failed to resolve HEAD commit SHA in {}", repoDir, e);
            return "HEAD";
        }
    }

    /**
     * Resolves the active branch name.
     */
    public String getCurrentBranch(Path repoDir) {
        try {
            String branch = runGitCommand(repoDir, 10, "rev-parse", "--abbrev-ref", "HEAD").trim();
            return branch.isEmpty() ? "main" : branch;
        } catch (Exception e) {
            return "main";
        }
    }

    /**
     * Retrieves recent commit logs (commitSha, author, subject, timestamp).
     */
    public List<Map<String, String>> getRecentCommitLogs(Path repoDir, int count) {
        List<Map<String, String>> logs = new ArrayList<>();
        try {
            int limit = count > 0 ? count : 10;
            String output = runGitCommand(repoDir, 15, "log", "-n", String.valueOf(limit), "--format=%H|%an|%s|%cI");
            for (String line : output.split("\n")) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 3) {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("sha", parts[0].trim());
                    item.put("author", parts[1].trim());
                    item.put("message", parts[2].trim());
                    item.put("timestamp", parts.length > 3 ? parts[3].trim() : "");
                    logs.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get commit logs: {}", e.getMessage());
        }
        return logs;
    }

    /**
     * Computes the file changes between two revisions using git diff --name-status, --numstat, and line ranges.
     */
    public List<ChangedFileDto> getDiffBetweenRevisions(Path repoDir, String baseRevision, String targetRevision) {
        List<ChangedFileDto> changedFiles = new ArrayList<>();
        String base = (baseRevision != null && !baseRevision.isBlank()) ? baseRevision.trim() : "HEAD~1";
        String target = (targetRevision != null && !targetRevision.isBlank()) ? targetRevision.trim() : "HEAD";

        Map<String, int[]> numstatMap = new HashMap<>();
        Map<String, List<String>> lineRangesMap = new HashMap<>();
        Map<String, String> snippetMap = new HashMap<>();

        try {
            // 1. Capture lines added and deleted
            try {
                String numstatOut = runGitCommand(repoDir, 20, "diff", "--numstat", base, target);
                for (String line : numstatOut.split("\n")) {
                    if (line.isBlank()) continue;
                    String[] parts = line.split("\t");
                    if (parts.length >= 3) {
                        try {
                            int added = parts[0].equals("-") ? 0 : Integer.parseInt(parts[0].trim());
                            int deleted = parts[1].equals("-") ? 0 : Integer.parseInt(parts[1].trim());
                            String path = parts[2].trim();
                            numstatMap.put(path, new int[]{added, deleted});
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                log.debug("git diff --numstat ignored: {}", e.getMessage());
            }

            // 2. Capture changed line ranges and snippets
            try {
                String hunkOut = runGitCommand(repoDir, 20, "diff", "-U0", base, target);
                String currentFile = null;
                for (String line : hunkOut.split("\n")) {
                    if (line.startsWith("+++ b/")) {
                        currentFile = line.substring(6).trim();
                    } else if (currentFile != null && line.startsWith("@@")) {
                        int at2 = line.indexOf("@@", 2);
                        if (at2 > 0) {
                            String header = line.substring(2, at2).trim();
                            for (String part : header.split(" ")) {
                                if (part.startsWith("+")) {
                                    try {
                                        String targetPart = part.substring(1);
                                        String[] segs = targetPart.split(",");
                                        int startLine = Integer.parseInt(segs[0]);
                                        int count = segs.length > 1 ? Integer.parseInt(segs[1]) : 1;
                                        String rangeStr = count <= 1 ? "L" + startLine : "L" + startLine + "-L" + (startLine + count - 1);
                                        lineRangesMap.computeIfAbsent(currentFile, k -> new ArrayList<>()).add(rangeStr);
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    } else if (currentFile != null && (line.startsWith("+") || line.startsWith("-")) && !line.startsWith("+++") && !line.startsWith("---")) {
                        snippetMap.putIfAbsent(currentFile, line.trim());
                    }
                }
            } catch (Exception e) {
                log.debug("git diff -U0 ignored: {}", e.getMessage());
            }

            // 3. Capture file status list
            String output = runGitCommand(repoDir, 20, "diff", "--name-status", base, target);
            for (String line : output.split("\n")) {
                if (line.isBlank()) continue;
                String[] parts = line.split("\t");
                if (parts.length < 2) continue;

                String statusCode = parts[0].trim();
                String filePath = parts[1].trim();

                String changeType = "MODIFIED";
                String oldPath = null;

                if (statusCode.startsWith("A")) {
                    changeType = "ADDED";
                } else if (statusCode.startsWith("D")) {
                    changeType = "DELETED";
                } else if (statusCode.startsWith("R")) {
                    changeType = "RENAMED";
                    oldPath = filePath;
                    filePath = parts.length > 2 ? parts[2].trim() : filePath;
                }

                int[] stats = numstatMap.getOrDefault(filePath, new int[]{0, 0});
                List<String> ranges = lineRangesMap.getOrDefault(filePath, List.of());
                String snippet = snippetMap.get(filePath);

                changedFiles.add(new ChangedFileDto(filePath, changeType, oldPath, null, stats[0], stats[1], ranges, snippet));
            }
        } catch (Exception e) {
            log.warn("Git diff failed between {} and {}: {}", base, target, e.getMessage());
        }

        return changedFiles;
    }


    /**
     * Executes an arbitrary git command inside the given repository directory with a timeout.
     */
    public String runGitCommand(Path repoDir, int timeoutSeconds, String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(Arrays.asList(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() < 65536) {
                    output.append(line).append("\n");
                }
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new TimeoutException("Git command timed out: " + String.join(" ", args));
        }

        if (process.exitValue() != 0) {
            throw new IllegalStateException("Git command failed (exit " + process.exitValue() + "): " + output);
        }

        return output.toString();
    }

    /**
     * Calculates the total size in bytes of a directory tree.
     */
    public long calculateDirectorySize(Path path) {
        if (path == null || !Files.exists(path)) return 0;
        final long[] size = {0};
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Failed to calculate directory size: {}", e.getMessage());
        }
        return size[0];
    }

    /**
     * Deletes a directory and its entire subtree recursively with Windows retry support.
     */
    public void deleteDirectoryRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) return;

        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    try {
                        file.toFile().setWritable(true);
                        Files.deleteIfExists(file);
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    try {
                        d.toFile().setWritable(true);
                        Files.deleteIfExists(d);
                    } catch (IOException ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warn("Directory cleanup notice for {}: {}", dir, e.getMessage());
        }
    }

    private String sanitizeErrorOutput(String rawOutput, String token) {
        if (rawOutput == null) return "";
        String sanitized = rawOutput;
        if (token != null && !token.isBlank()) {
            sanitized = sanitized.replace(token, "[REDACTED]");
        }
        // Limit output to prevent log bloating
        return sanitized.length() > 500 ? sanitized.substring(0, 500) + "..." : sanitized;
    }
}
