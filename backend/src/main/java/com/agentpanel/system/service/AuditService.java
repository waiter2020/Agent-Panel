package com.agentpanel.system.service;

import com.agentpanel.system.entity.AuditLog;
import com.agentpanel.system.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(Long userId, String username, String action, String resourceType, String resourceId,
                    Map<String, Object> detail, String ip, String result) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetail(detail);
        log.setIp(ip);
        log.setResult(result);
        auditLogRepository.save(log);
    }
}
