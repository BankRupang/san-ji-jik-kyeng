package com.bankrupang.sanjijk.user.infrastructure.keycloak;

import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KeycloakService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public UUID createUser(String username, String email, String password) {
        // 1. Keycloak 규격에 맞는 유저 객체 생성
        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setEnabled(true); // 💡 아까 수동으로 켰던 Enabled 설정을 코드로 자동화!

        // 2. 비밀번호 세팅
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false); // 💡 아까 삽질 유발했던 Temporary도 false로 깔끔하게 처리!
        user.setCredentials(Collections.singletonList(credential));

        // 3. Keycloak 서버의 Users API 호출
        UsersResource usersResource = keycloak.realm(realm).users();
        Response response = usersResource.create(user);

        if (response.getStatus() == 201) {
            // 성공하면 Keycloak 생성 URL에서 생성된 유저의 고유 UUID를 파싱해서 반환합니다.
            String path = response.getLocation().getPath();
            String keycloakUserId = path.substring(path.lastIndexOf("/") + 1);
            return UUID.fromString(keycloakUserId);
        } else {
            throw new RuntimeException("Keycloak 회원가입 실패 상태코드: " + response.getStatus());
        }
    }

    public void deleteUser(UUID keycloakUserId) {
            keycloak.realm(realm).users().get(keycloakUserId.toString()).remove();
    }
}
