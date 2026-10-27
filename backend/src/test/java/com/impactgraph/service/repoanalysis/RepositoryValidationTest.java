package com.impactgraph.service.repoanalysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryValidationTest {

    private RepositoryValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new RepositoryValidationService();
    }

    @Test
    @DisplayName("Valid GitHub URLs are parsed and canonicalized")
    void validateGitHubUrl_validUrls_succeed() {
        assertThat(validationService.validateGitHubUrl("https://github.com/spring-projects/spring-petclinic"))
                .isEqualTo("https://github.com/spring-projects/spring-petclinic");

        assertThat(validationService.validateGitHubUrl("https://github.com/spring-projects/spring-petclinic.git"))
                .isEqualTo("https://github.com/spring-projects/spring-petclinic");

        assertThat(validationService.validateGitHubUrl("https://github.com/AtharvKaner/ImpactGraph"))
                .isEqualTo("https://github.com/AtharvKaner/ImpactGraph");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://github.com/user/repo",
            "file:///etc/passwd",
            "file://C:/Windows/win.ini",
            "ssh://git@github.com/user/repo",
            "git://github.com/user/repo",
            "ftp://github.com/user/repo",
            "javascript:alert(1)"
    })
    @DisplayName("Non-HTTPS protocols are strictly rejected")
    void validateGitHubUrl_invalidProtocols_rejected(String url) {
        assertThatThrownBy(() -> validationService.validateGitHubUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/user/repo",
            "https://localhost/user/repo",
            "https://169.254.169.254/latest/meta-data",
            "https://10.0.0.1/user/repo",
            "https://192.168.1.1/user/repo",
            "https://evil-github.com/user/repo",
            "https://github.com.attacker.com/user/repo",
            "https://gitlab.com/user/repo"
    })
    @DisplayName("Private IPs, loopback, metadata services, and untrusted hosts are rejected (SSRF prevention)")
    void validateGitHubUrl_ssrfAndUntrustedHosts_rejected(String url) {
        assertThatThrownBy(() -> validationService.validateGitHubUrl(url))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/user/repo;rm -rf /",
            "https://github.com/user/repo|whoami",
            "https://github.com/user/repo&calc.exe",
            "https://github.com/user/repo`id`",
            "https://github.com/user/repo$(id)",
            "https://github.com/user/repo\nwhoami",
            "https://github.com/user/repo<input",
            "https://github.com/user/repo>output"
    })
    @DisplayName("Shell injection metacharacters in repository URLs are strictly rejected")
    void validateGitHubUrl_shellInjection_rejected(String url) {
        assertThatThrownBy(() -> validationService.validateGitHubUrl(url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden shell metacharacters");
    }

    @Test
    @DisplayName("Path traversal in local repository paths is strictly rejected")
    void validateLocalPath_pathTraversal_rejected() {
        assertThatThrownBy(() -> validationService.validateLocalPath("c:/Users/athar/ImpactGraph/../../Windows"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Path traversal");
    }

    @Test
    @DisplayName("Windows system directories are strictly forbidden")
    void validateLocalPath_systemDirectories_rejected() {
        assertThatThrownBy(() -> validationService.validateLocalPath("C:\\Windows"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Non-existent local paths are rejected")
    void validateLocalPath_nonExistent_rejected() {
        assertThatThrownBy(() -> validationService.validateLocalPath("C:\\Users\\athar\\NonExistentFolder_12345"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    @DisplayName("Current working directory is accepted as a valid local repository path")
    void validateLocalPath_currentDirectory_accepted() {
        String currentDir = System.getProperty("user.dir");
        Path path = validationService.validateLocalPath(currentDir);
        assertThat(path).isNotNull();
        assertThat(path.toFile()).exists();
    }

    @Test
    @DisplayName("Git revisions with flag prefixes are rejected (command injection prevention)")
    void validateRevision_flagInjection_rejected() {
        assertThatThrownBy(() -> validationService.validateRevision("--exec=calc.exe"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hyphen");
    }

    @Test
    @DisplayName("Valid Git revisions are approved")
    void validateRevision_validRevisions_approved() {
        assertThat(validationService.validateRevision("main")).isEqualTo("main");
        assertThat(validationService.validateRevision("HEAD~1")).isEqualTo("HEAD~1");
        assertThat(validationService.validateRevision("feat/dashboard-redesign")).isEqualTo("feat/dashboard-redesign");
        assertThat(validationService.validateRevision("v1.2.3")).isEqualTo("v1.2.3");
        assertThat(validationService.validateRevision("")).isEqualTo("HEAD");
        assertThat(validationService.validateRevision(null)).isEqualTo("HEAD");
    }
}
