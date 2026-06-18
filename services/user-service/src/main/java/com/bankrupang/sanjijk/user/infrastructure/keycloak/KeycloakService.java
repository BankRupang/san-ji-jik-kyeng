package com.bankrupang.sanjijk.user.infrastructure.keycloak;

import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.exception.UserKeycloakCreationFailedException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public UUID createUser(String username, String email, String password, UserRole role) {
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);
        user.setCredentials(Collections.singletonList(credential));

        UsersResource usersResource = keycloak.realm(realm).users();
        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            String path = response.getLocation().getPath();
            String keycloakUserId = path.substring(path.lastIndexOf("/") + 1);
            assignRoleToUser(keycloakUserId, role.name());
            return UUID.fromString(keycloakUserId);
        } else {
            log.error("Keycloak 유저 생성 실패. status={}, username={}", response.getStatus(), username);
            throw new UserKeycloakCreationFailedException();
        }
    }

    private void assignRoleToUser(String userId, String roleName) {
        RoleRepresentation roleToAssign = keycloak.realm(realm)
                .roles()
                .get(roleName)
                .toRepresentation();

        UserResource userResource = keycloak.realm(realm).users().get(userId);
        userResource.roles().realmLevel().add(Collections.singletonList(roleToAssign));
    }

    public void deleteUser(UUID keycloakUserId) {
        keycloak.realm(realm).users().get(keycloakUserId.toString()).remove();
    }
}
