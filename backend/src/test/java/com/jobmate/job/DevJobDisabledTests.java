package com.jobmate.job;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevJobDisabledTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 로컬_프로파일이_아니면_개발용_등록과_수정_경로가_없다() throws Exception {
        // given
        String body = "{\"companyName\":\"테스트\",\"title\":\"개발\"}";

        // when
        var create = post("/api/v1/dev/jobs").contentType(MediaType.APPLICATION_JSON).content(body);
        var update = patch("/api/v1/dev/jobs/1").contentType(MediaType.APPLICATION_JSON).content(body);

        // then
        mockMvc.perform(create).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(update).andExpect(status().isNotFound());
    }
}
