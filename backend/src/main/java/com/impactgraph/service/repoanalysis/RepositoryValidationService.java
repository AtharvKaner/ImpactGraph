package com.impactgraph.service.repoanalysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates repository URLs, local filesystem paths, Git revisions, and tokens.
 * Enforces strict restrictions to prevent SSRF, command injection, path traversal,
 * and unauthorized filesystem access.
 */
@Service
public class RepositoryValidationService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryValidationService.class);

    private static final Pattern GITHUB_URL_PATTERN = Pattern.compile(
            "^https://github\\.com/([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+?)(?:\\.git)?(?:/.*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SAFE_REVISION_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_./~^-]{1,100}$"
    );

    private static final Pattern FORBIDDEN_METADATA_IPS = Pattern.compile(
            "^(127\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.|169\\.254\\.|0\\.0\\.0\\.0|::1|localhost).*"
    );

    private static final Set<String> FORBIDDEN_WINDOWS_PATHS = Set.of(
            "windows", "program files", "program files (x86)", "system volume information", "$recycle.bin"
    );

    private static final Set<String> FORBIDDEN_UNIX_PATHS = Set.of(
            "/etc", "/bin", "/sbin", "/usr", "/var", "/proc", "/sys", "/root", "/dev"
    );

    @Value("${impactgraph.allowed-local-paths:}")
    private String configuredAllowedLocalPaths;

    /**
     * Validates a public or private GitHub repository URL.
     * Enforces HTTPS protocol, github.com host, strict path characters, and SSRF avoidance.
     *
     * @param url candidate URL
     * @return validated and sanitized HTTPS URL
     * @throws IllegalArgumentException if validation fails
     */
    public String validateGitHubUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Repository URL cannot be null or blank");
        }

        String trimmed = url.trim();

        // 1. Check for forbidden shell metacharacters
        for (char c : trimmed.toCharArray()) {
            if (c == ';' || c == '&' || c == '|' || c == '`' || c == '$' ||
                c == '\n' || c == '\r' || c == '<' || c == '>' || c == '"' ||
                c == '\'' || c == '\0' || c == '\t') {
                throw new IllegalArgumentException("Repository URL contains forbidden shell metacharacters");
            }
        }

        // 2. Strict scheme and host check via URI
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid repository URI format: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equalsIgnoreCase("https")) {
            throw new IllegalArgumentException("Only secure HTTPS protocol is permitted. Received: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || !host.equalsIgnoreCase("github.com")) {
            throw new IllegalArgumentException("Repository host must be github.com. Received: " + host);
        }

        // 3. Reject loopback / private IP formats
        if (FORBIDDEN_METADATA_IPS.matcher(host.toLowerCase(Locale.ROOT)).matches()) {
            throw new IllegalArgumentException("Loopback and internal addresses are strictly prohibited (SSRF prevention)");
        }

        // 4. Pattern check for owner and repo name
        Matcher matcher = GITHUB_URL_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Invalid GitHub repository URL format. Expected: https://github.com/owner/repository"
            );
        }

        String owner = matcher.group(1);
        String repo = matcher.group(2);

        if (owner.equalsIgnoreCase("..") || repo.equalsIgnoreCase("..")) {
            throw new IllegalArgumentException("Path traversal segments are prohibited in repository URL");
        }

        // Strip trailing slash or .git for consistent canonical URL
        String cleanRepo = repo.endsWith(".git") ? repo.substring(0, repo.length() - 4) : repo;
        return String.format("https://github.com/%s/%s", owner, cleanRepo);
    }

    /**
     * Validates and normalizes a local repository filesystem path.
     * Prevents path traversal and restricts access to permitted workspace roots.
     *
     * @param pathStr candidate path string
     * @return validated normalized absolute Path
     * @throws IllegalArgumentException if validation fails
     */
    public Path validateLocalPath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            throw new IllegalArgumentException("Local repository path cannot be null or blank");
        }

        String trimmed = pathStr.trim();

        // 1. Reject path traversal symbols
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException("Path traversal ('..') is strictly prohibited in local repository path");
        }

        // 2. Normalize path
        Path normalized;
        try {
            normalized = Paths.get(trimmed).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid local filesystem path: " + e.getMessage());
        }

        // 3. Reject Windows and Unix system roots
        String normalizedStr = normalized.toString().toLowerCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_WINDOWS_PATHS) {
            if (normalizedStr.contains("\\" + forbidden + "\\") || normalizedStr.endsWith("\\" + forbidden)) {
                throw new IllegalArgumentException("Access to system directory '" + forbidden + "' is strictly forbidden");
            }
        }
        for (String forbidden : FORBIDDEN_UNIX_PATHS) {
            if (normalizedStr.equals(forbidden) || normalizedStr.startsWith(forbidden + "/")) {
                throw new IllegalArgumentException("Access to Unix system directory '" + forbidden + "' is strictly forbidden");
            }
        }

        // 4. Must exist and be an accessible directory
        if (!Files.exists(normalized)) {
            throw new IllegalArgumentException("Local directory does not exist: " + normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw new IllegalArgumentException("Local path is not a directory: " + normalized);
        }

        // 5. Must be within allowed roots
        String currentDir = System.getProperty("user.dir", ".");
        Path allowedCurrent = Paths.get(currentDir).toAbsolutePath().normalize();
        Path userHome = Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize();

        boolean isAllowed = normalized.startsWith(allowedCurrent) || normalized.startsWith(userHome);
        if (!isAllowed && configuredAllowedLocalPaths != null && !configuredAllowedLocalPaths.isBlank()) {
            for (String allowed : configuredAllowedLocalPaths.split(",")) {
                if (!allowed.isBlank() && normalized.startsWith(Paths.get(allowed.trim()).toAbsolutePath().normalize())) {
                    isAllowed = true;
                    break;
                }
            }
        }

        if (!isAllowed) {
            throw new IllegalArgumentException(
                    "Local path must reside within the authorized workspace or user directory"
            );
        }

        return normalized;
    }

    /**
     * Validates a Git revision string (branch, tag, or commit SHA).
     *
     * @param revision revision string
     * @return validated revision or "HEAD" if blank
     */
    public String validateRevision(String revision) {
        if (revision == null || revision.isBlank()) {
            return "HEAD";
        }

        String trimmed = revision.trim();

        // Prevent flag injection (e.g. --exec, -o)
        if (trimmed.startsWith("-")) {
            throw new IllegalArgumentException("Git revision cannot start with a hyphen");
        }

        if (!SAFE_REVISION_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "Invalid Git revision identifier. Allowed characters: alphanumeric, '.', '_', '/', '~', '^', '-'"
            );
        }

        return trimmed;
    }

    /**
     * Validates and sanitizes a Personal Access Token.
     * Returns null if blank, or sanitized string without logging.
     *
     * @param token candidate token
     * @return sanitized token or null
     */
    public String sanitizeToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        String trimmed = token.trim();
        // Check for basic ASCII characters without shell metacharacters
        for (char c : trimmed.toCharArray()) {
            if (c <= 32 || c >= 127 || c == ';' || c == '&' || c == '|' || c == '`' || c == '$') {
                throw new IllegalArgumentException("Personal Access Token contains invalid characters");
            }
        }

        return trimmed;
    }
}
