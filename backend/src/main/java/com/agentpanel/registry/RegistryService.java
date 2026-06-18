package com.agentpanel.registry;

import com.agentpanel.common.BusinessException;
import com.agentpanel.config.RegistryProperties;
import com.agentpanel.registry.dto.RegistryTagsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistryService {

    private static final Pattern AUTH_CHALLENGE = Pattern.compile("Bearer\\s+([^,]+)");
    private static final int MAX_TAGS = 100;

    private final RegistryProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public RegistryTagsDto listTags(String image, String defaultTag) {
        ImageReference ref;
        try {
            ref = ImageReference.parse(ImageReference.stripTag(image));
            validateRegistryHost(ref.registryHost());

            String cacheKey = ref.registryHost() + "/" + ref.repository();
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
                return buildResponse(cached.tags(), defaultTag, "registry", false, null);
            }

            List<String> tags = ref.dockerHub()
                    ? fetchDockerHubTags(ref)
                    : fetchRegistryV2Tags(ref);
            List<String> sorted = sortTags(tags);
            cache.put(cacheKey, new CacheEntry(sorted, Instant.now().plus(properties.getCacheTtl())));
            return buildResponse(sorted, defaultTag, "registry", false, null);
        } catch (BusinessException ex) {
            log.warn("镜像仓库请求被拒绝，镜像 {}: {}", image, ex.getMessage());
            return buildResponse(List.of(), defaultTag, "fallback", true, ex.getMessage());
        } catch (Exception ex) {
            log.warn("获取镜像标签失败，镜像 {}: {}", image, ex.getMessage());
            return buildResponse(List.of(), defaultTag, "fallback", true,
                    "无法连接镜像仓库，请手动输入标签");
        }
    }

    static List<String> sortTags(List<String> tags) {
        List<String> copy = new ArrayList<>(tags);
        copy.sort(Comparator
                .comparing((String tag) -> "latest".equalsIgnoreCase(tag))
                .thenComparing(RegistryService::versionKey, Comparator.reverseOrder())
                .thenComparing(String::compareToIgnoreCase));
        return copy;
    }

    private List<String> fetchDockerHubTags(ImageReference ref) throws Exception {
        String[] parts = ref.repository().split("/", 2);
        String namespace = parts.length > 1 ? parts[0] : "library";
        String repo = parts.length > 1 ? parts[1] : parts[0];
        String url = "https://hub.docker.com/v2/repositories/" + namespace + "/" + repo + "/tags?page_size=" + MAX_TAGS;
        WebClient client = webClientBuilder.build();
        String body = client.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(properties.getRequestTimeout())
                .retryWhen(Retry.fixedDelay(1, Duration.ofMillis(200))
                        .filter(e -> !(e instanceof BusinessException)))
                .block();
        return parseDockerHubTags(body);
    }

    private List<String> fetchRegistryV2Tags(ImageReference ref) throws Exception {
        WebClient client = webClientBuilder.build();
        String baseUrl = ref.registryBaseUrl();
        String tagsUrl = baseUrl + ref.tagsListPath() + "?n=" + MAX_TAGS;
        try {
            String body = client.get()
                    .uri(tagsUrl)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(properties.getRequestTimeout())
                    .block();
            return parseRegistryTags(body);
        } catch (WebClientResponseException.Unauthorized ex) {
            String token = resolveBearerToken(client, baseUrl, ex.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE));
            if (token == null) {
                throw ex;
            }
            String body = client.get()
                    .uri(tagsUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(properties.getRequestTimeout())
                    .block();
            return parseRegistryTags(body);
        }
    }

    private String resolveBearerToken(WebClient client, String registryBase, String wwwAuthenticate) {
        if (wwwAuthenticate == null || !wwwAuthenticate.startsWith("Bearer")) {
            return null;
        }
        Matcher matcher = AUTH_CHALLENGE.matcher(wwwAuthenticate);
        if (!matcher.find()) {
            return null;
        }
        Map<String, String> params = parseAuthParams(matcher.group(1));
        String realm = params.get("realm");
        String service = params.get("service");
        String scope = params.get("scope");
        if (realm == null) {
            return null;
        }
        String tokenUrl = realm + "?service=" + service + (scope != null ? "&scope=" + scope : "");
        try {
            String body = client.get()
                    .uri(tokenUrl)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(properties.getRequestTimeout())
                    .block();
            JsonNode node = objectMapper.readTree(body);
            if (node.hasNonNull("token")) {
                return node.get("token").asText();
            }
            if (node.hasNonNull("access_token")) {
                return node.get("access_token").asText();
            }
        } catch (Exception e) {
            log.debug("镜像仓库 Token 请求失败，{}: {}", registryBase, e.getMessage());
        }
        return null;
    }

    private Map<String, String> parseAuthParams(String paramString) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        for (String part : paramString.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                result.put(kv[0].trim(), kv[1].replace("\"", "").trim());
            }
        }
        return result;
    }

    private List<String> parseRegistryTags(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode tagsNode = root.get("tags");
        if (tagsNode == null || !tagsNode.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        tagsNode.forEach(node -> tags.add(node.asText()));
        return tags;
    }

    private List<String> parseDockerHubTags(String body) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }
        List<String> tags = new ArrayList<>();
        results.forEach(node -> {
            if (node.hasNonNull("name")) {
                tags.add(node.get("name").asText());
            }
        });
        return tags;
    }

    private void validateRegistryHost(String host) {
        if (!ImageReference.isValidRegistryHost(host)) {
            throw new BusinessException("镜像仓库地址格式无效");
        }
        String hostname = host.contains(":") ? host.substring(0, host.indexOf(':')) : host;
        if (isBlockedHost(hostname)) {
            throw new BusinessException("不允许访问该镜像仓库");
        }
        if (!isAllowedHost(hostname)) {
            throw new BusinessException("镜像仓库不在允许列表中");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(hostname)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()) {
                    throw new BusinessException("不允许访问内网镜像仓库");
                }
            }
        } catch (UnknownHostException e) {
            throw new BusinessException("无法解析镜像仓库地址");
        }
    }

    private boolean isAllowedHost(String hostname) {
        return properties.getAllowedHosts().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(hostname));
    }

    private static boolean isBlockedHost(String hostname) {
        String lower = hostname.toLowerCase();
        return lower.equals("localhost")
                || lower.equals("host.docker.internal")
                || lower.endsWith(".local")
                || lower.endsWith(".internal");
    }

    private static String versionKey(String tag) {
        if (tag == null || tag.isBlank()) {
            return "";
        }
        String normalized = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        String[] parts = normalized.split("[.-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.matches("\\d+")) {
                sb.append(String.format("%05d", Integer.parseInt(part)));
            } else {
                sb.append(part);
            }
            sb.append('.');
        }
        return sb.toString();
    }

    private RegistryTagsDto buildResponse(List<String> tags, String defaultTag, String source,
                                          boolean fallback, String message) {
        String resolvedDefault = defaultTag;
        if (resolvedDefault != null && !resolvedDefault.isBlank() && tags.contains(resolvedDefault)) {
            // keep template default when present in list
        } else if (!tags.isEmpty()) {
            resolvedDefault = tags.getFirst();
        }
        return RegistryTagsDto.builder()
                .tags(tags)
                .defaultTag(resolvedDefault)
                .source(source)
                .fallback(fallback)
                .message(message)
                .build();
    }

    private record CacheEntry(List<String> tags, Instant expiresAt) {}
}
