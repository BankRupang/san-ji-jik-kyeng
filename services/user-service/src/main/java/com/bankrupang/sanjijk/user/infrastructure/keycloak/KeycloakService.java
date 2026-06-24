package com.bankrupang.sanjijk.user.infrastructure.keycloak;

import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.exception.KeycloakLoginFailedException;
import com.bankrupang.sanjijk.user.domain.exception.UserKeycloakCreationFailedException;
import com.bankrupang.sanjijk.user.presentation.dto.response.KeycloakTokenResponse;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloak;

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    public UUID createUser(String username, String email, String password, UserRole role) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setFirstName(username);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));
        user.setRequiredActions(Collections.emptyList());

        UsersResource usersResource = keycloak.realm(realm).users();
        user.setAttributes(Map.of("role", List.of(role.name())));
        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            String path = response.getLocation().getPath();
            String keycloakUserId = path.substring(path.lastIndexOf("/") + 1);
            return UUID.fromString(keycloakUserId);
        } else {
            log.error("Keycloak 유저 생성 실패. status={}, username={}", response.getStatus(), username);
            throw new UserKeycloakCreationFailedException();
        }
    }

    public KeycloakTokenResponse login(String username, String password) {
        try {
            Keycloak keycloakLogin = KeycloakBuilder.builder()
                    .serverUrl(serverUrl)
                    .realm(realm)
                    .grantType(OAuth2Constants.PASSWORD)
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .username(username)
                    .password(password)
                    .build();

            var tokenResponse = keycloakLogin.tokenManager().getAccessToken();
            return new KeycloakTokenResponse(
                    tokenResponse.getToken(),
                    tokenResponse.getRefreshToken()
            );
        } catch (NotAuthorizedException e) {
            throw new KeycloakLoginFailedException();
        } catch (jakarta.ws.rs.BadRequestException e) {
            log.error("Keycloak 로그인 400: {}", e.getResponse().readEntity(String.class));
            throw new KeycloakLoginFailedException();
        }

    }

    public void deleteUser(UUID keycloakUserId) {
        keycloak.realm(realm).users().get(keycloakUserId.toString()).remove();
    }
}
