package com.jobmate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobMateApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Test
    void 데이터베이스에_연결되면_상태_확인_요청에_UP을_반환한다() {
        // given
        String healthPath = "/actuator/health";

        // when
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(healthPath, JsonNode.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(healthEndpoint.healthForPath("db").getStatus()).isEqualTo(Status.UP);
    }
}
