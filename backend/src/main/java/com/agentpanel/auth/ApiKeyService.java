package com.agentpanel.auth;



import com.agentpanel.auth.entity.ApiKey;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;



import java.util.Optional;



@Service

@RequiredArgsConstructor

public class ApiKeyService {



    private final ApiKeyManagementService apiKeyManagementService;



    public Optional<ApiKey> validate(String rawKey) {

        return apiKeyManagementService.validate(rawKey);

    }

}

